package platform.design.html

/** The type roles of `UkptTypography`, as `ukpt-design.css` classes. */
object UkptHtmlTypography {
    const val display = "ukpt-display"
    const val title = "ukpt-title"
    const val body = "ukpt-body"
    const val label = "ukpt-label"
    const val caption = "ukpt-caption"
}

/** The content colour roles of `UkptColors`, as `ukpt-design.css` classes that set `color`. */
object UkptHtmlColors {
    const val onSurface = "ukpt-on-surface"
    const val onSurfaceVariant = "ukpt-on-surface-variant"
    const val accent = "ukpt-accent"
    const val error = "ukpt-error"
}

/** Layout classes from `ukpt-design.css`. */
object UkptHtmlLayout {
    /** A full-viewport page that centres a column of content, `UkptSpacing.md` apart. */
    const val screen = "ukpt-screen"

    /** The column of content inside a dialog destination. */
    const val dialogContent = "ukpt-dialog-content"

    /** The row of buttons that ends a dialog, aligned to the end. */
    const val dialogActions = "ukpt-dialog-actions"
}
