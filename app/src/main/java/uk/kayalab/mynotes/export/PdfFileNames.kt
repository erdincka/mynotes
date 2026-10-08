package uk.kayalab.mynotes.export

private val unsafeCharacters = Regex("[/\\\\:*?\"<>|]")
private val leadingDate = Regex("^\\d{8}(\\D|$)")

/**
 * The PDF file name for a note. The export date is prefixed unless the note name already starts
 * with one ("20261008 - Title"), so the new default note names are not dated twice.
 */
fun pdfFileNameFor(noteName: String, exportDate: String): String {
    val safe = noteName.replace(unsafeCharacters, "_").trim().ifEmpty { "note" }
    return if (leadingDate.containsMatchIn(safe)) "$safe.pdf" else "$exportDate-$safe.pdf"
}

/** Keeps every name distinct (case-insensitively, as cloud drives are) by numbering repeats: "x (2).pdf". */
fun uniquePdfFileNames(names: List<String>): List<String> {
    val used = HashSet<String>()
    return names.map { name ->
        var candidate = name
        var n = 1
        while (!used.add(candidate.lowercase())) {
            n++
            candidate = name.removeSuffix(".pdf") + " ($n).pdf"
        }
        candidate
    }
}
