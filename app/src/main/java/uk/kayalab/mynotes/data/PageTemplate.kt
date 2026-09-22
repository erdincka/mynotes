package uk.kayalab.mynotes.data

/** Background pattern of a note, stored by name so a new build can add patterns safely. */
enum class PageTemplate(val label: String) {
    PLAIN("Plain"),
    GRID("Grid"),
    RULED("Ruled"),
    DOTTED("Dotted");

    companion object {
        val DEFAULT = GRID
        fun fromName(name: String?): PageTemplate = entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}
