package com.aburv.kurippidu

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.aburv.kurippidu.data.ImageImporter
import java.io.File
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One day for the PDF. */
data class PdfDay(
    val date: LocalDate,
    val moodLine: String?,
    val text: String,   // markdown; markers are removed when printed
    val photos: List<File>
)

/**
 * Writes a diary as an A4 "printed diary": a title page, then each day with
 * its date, mood, text and up to three photos, flowing across pages.
 */
object DiaryPdf {

    private const val PAGE_WIDTH = 595  // A4 in points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
    private const val PHOTO_HEIGHT = 140f
    private const val INK = 0xFF3B2414.toInt()

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

    /** @return number of pages written */
    fun write(out: OutputStream, diaryTitle: String, days: List<PdfDay>): Int {
        val pdf = PdfDocument()
        val writer = PageWriter(pdf)

        writer.titlePage(diaryTitle, days)
        if (days.isNotEmpty()) writer.startEntries() // entries begin on a fresh page
        days.forEach { day -> writer.day(day) }
        writer.finish()

        pdf.writeTo(out)
        val pages = pdf.pages.size
        pdf.close()
        return pages
    }

    /** Markdown to plain printed text (keeps ☐/☑ checklist marks). */
    fun printable(markdown: String): String = renderMarkdown(markdown).text

    private class PageWriter(private val pdf: PdfDocument) {
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f
        private val bottom = PAGE_HEIGHT - MARGIN - 16f // room for the page number

        private val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f; color = INK }
        private val heading = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f; color = INK; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = Color.GRAY }
        private val rule = Paint().apply { color = 0x336B3E26; strokeWidth = 0.8f }

        private fun newPage() {
            finishPage()
            pageNumber++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page!!.canvas
            y = MARGIN
        }

        private fun finishPage() {
            val current = page ?: return
            canvas!!.drawText("$pageNumber", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN / 2, small)
            pdf.finishPage(current)
            page = null
        }

        fun finish() = finishPage()

        fun startEntries() = newPage()

        /** Starts a new page if [height] doesn't fit. */
        private fun ensure(height: Float) {
            if (page == null || y + height > bottom) newPage()
        }

        private fun layout(text: String, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, CONTENT_WIDTH.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()

        /** Draws text line by line so long entries flow onto the next page. */
        private fun paragraph(text: String, paint: TextPaint) {
            val layout = layout(text, paint)
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line).toFloat()
                val height = layout.getLineBottom(line) - top
                ensure(height)
                canvas!!.save()
                canvas!!.translate(MARGIN, y - top)
                canvas!!.clipRect(0f, top, CONTENT_WIDTH, top + height)
                layout.draw(canvas!!)
                canvas!!.restore()
                y += height
            }
        }

        fun titlePage(title: String, days: List<PdfDay>) {
            newPage()
            y = PAGE_HEIGHT / 3f
            paragraph(title, TextPaint(heading).apply { textSize = 28f })
            y += 12f
            val range = if (days.isEmpty()) "No entries yet"
            else "${days.first().date.format(dayFormat)} – ${days.last().date.format(dayFormat)}"
            paragraph(range, body)
            paragraph("${days.size} days", small)
        }

        fun day(day: PdfDay) {
            // Keep the date with at least a couple of lines of its text
            ensure(70f)
            if (y > MARGIN) {
                y += 10f
                canvas!!.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
                y += 14f
            }
            paragraph(day.date.format(dayFormat), heading)
            day.moodLine?.let { paragraph(it, small) }
            y += 6f
            val text = printable(day.text).trim()
            if (text.isNotEmpty()) paragraph(text, body)
            photos(day.photos.take(3))
        }

        private fun photos(files: List<File>) {
            val bitmaps = files.mapNotNull { ImageImporter.thumbnail(it, 600) }
            if (bitmaps.isEmpty()) return
            y += 8f
            ensure(PHOTO_HEIGHT)
            var x = MARGIN
            val gap = 8f
            bitmaps.forEach { bitmap ->
                val width = PHOTO_HEIGHT * bitmap.width / bitmap.height
                if (x + width > PAGE_WIDTH - MARGIN) return@forEach // no room on this row
                canvas!!.drawBitmap(bitmap, null, RectF(x, y, x + width, y + PHOTO_HEIGHT), null)
                x += width + gap
                bitmap.recycleIfPossible()
            }
            y += PHOTO_HEIGHT
        }

        private fun Bitmap.recycleIfPossible() = runCatching { recycle() }
    }
}
