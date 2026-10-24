package com.aburv.kurippidu.data

/** #tags written in diary text, e.g. "#work" or "#family". */
object Tags {
    // A # not preceded by a letter/digit/#, then letters, digits, _ or combining
    // marks (\\p{M}: vowel signs in Tamil and other Indic scripts are marks, not letters)
    private val TAG = Regex("(?<![\\p{L}\\p{M}\\p{N}_#])#([\\p{L}\\p{M}\\p{N}_]+)")

    /** Distinct tags in [text], lower-case, without the #. */
    fun extract(text: String): Set<String> =
        TAG.findAll(text).map { it.groupValues[1].lowercase() }.filter { it.any(Char::isLetter) }.toSet()

    /** Character ranges of each #tag (including the #), for highlighting. */
    fun ranges(text: String): List<IntRange> =
        TAG.findAll(text).filter { it.groupValues[1].any(Char::isLetter) }.map { it.range }.toList()
}
