package architecture.definitions

/**
 * The coordinates of a side-first feature package — `feature.[feature].[side].[layer]` plus the
 * dotted [subsystem] path after the layer segment, `""` at the layer root.
 */
data class FeatureLayerPath(
    val feature: String,
    val side: String,
    val layer: String,
    val subsystem: String,
) {
    fun sameLayerAs(other: FeatureLayerPath): Boolean =
        feature == other.feature && side == other.side && layer == other.layer

    fun sameSideAs(other: FeatureLayerPath): Boolean =
        feature == other.feature && side == other.side
}

private val sideFirstPackageRegex = Regex("""^feature\.([^.]+)\.(client|server)\.([^.]+)(?:\.(.+))?$""")

/**
 * The coordinates of [packageName], or null when it names no side-first feature layer: a feature
 * root, a `platform.**` package, or a pre-migration package with no `client`/`server` segment.
 */
fun featureLayerPath(packageName: String): FeatureLayerPath? =
    sideFirstPackageRegex.matchEntire(packageName)?.let {
        FeatureLayerPath(
            feature = it.groupValues[1],
            side = it.groupValues[2],
            layer = it.groupValues[3],
            subsystem = it.groupValues[4],
        )
    }

/**
 * Subsystem-path relations, compared over package *segments* so `audiox` never reads as a child of
 * `audio`. The layer root is `""` — the ancestor of every subsystem and a child of none.
 */
fun isAncestorSubsystem(ancestor: String, of: String): Boolean {
    val ancestorSegments = ancestor.subsystemSegments()
    val ofSegments = of.subsystemSegments()
    return ancestorSegments.size < ofSegments.size &&
        ofSegments.subList(0, ancestorSegments.size) == ancestorSegments
}

fun isDirectChildSubsystem(child: String, of: String): Boolean {
    val childSegments = child.subsystemSegments()
    val ofSegments = of.subsystemSegments()
    return childSegments.size == ofSegments.size + 1 &&
        childSegments.subList(0, ofSegments.size) == ofSegments
}

private fun String.subsystemSegments(): List<String> = if (isEmpty()) emptyList() else split('.')
