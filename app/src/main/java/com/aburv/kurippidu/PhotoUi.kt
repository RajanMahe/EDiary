@file:OptIn(ExperimentalMaterial3Api::class)

package com.aburv.kurippidu

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aburv.kurippidu.data.AttachmentEntity
import com.aburv.kurippidu.data.ImageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Row of thumbnails for the day's photos. */
@Composable
fun PhotoStrip(
    photos: List<AttachmentEntity>,
    fileOf: (AttachmentEntity) -> File,
    onOpen: (AttachmentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            Thumbnail(
                file = fileOf(photo),
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOpen(photo) }
            )
        }
    }
}

@Composable
private fun Thumbnail(file: File, modifier: Modifier) {
    val sizePx = with(LocalDensity.current) { 88.dp.roundToPx() }
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ImageImporter.thumbnail(file, sizePx) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Full-screen photo with pinch-to-zoom; delete asks first. */
@Composable
fun PhotoViewer(file: File, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { ImageImporter.thumbnail(file, 2048) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val zoom = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else offset + panChange
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoom)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .systemBarsPadding()
                    .padding(8.dp)
            ) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete photo", tint = Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close photo", tint = Color.White)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo?") },
            text = { Text("It will be removed from this day.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

/** Gallery or camera. */
@Composable
fun PhotoSourceSheet(onGallery: () -> Unit, onCamera: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text("Choose from gallery") },
            leadingContent = { Text("🖼️") },
            modifier = Modifier.clickable(onClick = onGallery)
        )
        ListItem(
            headlineContent = { Text("Take a photo") },
            leadingContent = { Text("📷") },
            modifier = Modifier.clickable(onClick = onCamera)
        )
        Spacer(Modifier.height(24.dp))
    }
}
