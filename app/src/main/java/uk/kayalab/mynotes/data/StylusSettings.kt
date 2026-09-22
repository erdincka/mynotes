package uk.kayalab.mynotes.data

/** What a stylus barrel button does while it is held down at the start of a stroke. */
enum class StylusButtonAction(val label: String) {
    NONE("Nothing"),
    ERASER("Erase while held"),
    LASSO("Lasso select while held"),
    HIGHLIGHTER("Highlight while held"),
    UNDO("Undo on press");

    companion object {
        fun fromName(name: String?, default: StylusButtonAction): StylusButtonAction =
            entries.firstOrNull { it.name == name } ?: default
    }
}

data class StylusConfig(
    val primaryButton: StylusButtonAction = StylusButtonAction.ERASER,
    val secondaryButton: StylusButtonAction = StylusButtonAction.LASSO,
    /** When true, only a stylus draws; a single finger pans the canvas. */
    val stylusOnly: Boolean = false
)
