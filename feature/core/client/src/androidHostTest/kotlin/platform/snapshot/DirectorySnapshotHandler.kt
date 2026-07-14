package platform.snapshot

import app.cash.paparazzi.Snapshot
import app.cash.paparazzi.SnapshotHandler
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_ARGB
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max

/**
 * A [SnapshotHandler] that writes/verifies golden PNGs at a **directory-grouped** path derived from
 * the preview's declaring package and function name, instead of the stock flat
 * `<testPkg>_<testClass>_<testMethod>_<name>.png` scheme.
 *
 * The relative golden path (POSIX, `/`-separated, no extension) is passed straight through as the
 * Paparazzi snapshot `name` (see [PreviewSnapshotTest]); e.g.
 * `feature/campaigns/ui/CampaignManagementEmptyRolesPreview`. This handler resolves that against the
 * module's `snapshots/images/` directory and appends `.png`.
 *
 * Why a custom handler: the stock `HtmlReportWriter` (report/record) and `SnapshotVerifier` (verify) both
 * compute the golden path via `Snapshot.toFileName`, which only joins with `_` (no directories) and
 * rejects `/`. So neither can produce a nested layout. This mirrors their behaviour otherwise:
 *
 * - **report** (a plain test run): writes the rendered frame under `build/paparazzi/` without
 *   changing the committed golden.
 * - **record** (`paparazzi.test.record=true`): writes the rendered frame to the golden path,
 *   creating parent directories as needed.
 * - **verify** (`paparazzi.test.verify=true`): compares the rendered frame against the committed
 *   golden using the same `OffByTwo` pixel diff, `maxPercentDifference` threshold and width/height
 *   tolerance the stock `SnapshotVerifier`/`ImageUtils` use in this Paparazzi version. A missing
 *   golden, an over-threshold difference, or a size mismatch fails the test; on failure a delta and
 *   the actual image are written under `build/paparazzi/failures/…` and the assertion names both
 *   paths.
 *
 * The system properties consumed here are exactly the ones the Paparazzi gradle plugin sets for the
 * test task (`paparazzi.snapshot.dir`, `paparazzi.report.dir`, `paparazzi.failures.dir`,
 * `paparazzi.test.record`, `paparazzi.test.verify`,
 * `app.cash.paparazzi.maxPercentDifferenceDefault`).
 */
internal class DirectorySnapshotHandler : SnapshotHandler {

    private val isRecording: Boolean =
        System.getProperty("paparazzi.test.record")?.toBoolean() == true

    private val isVerifying: Boolean =
        System.getProperty("paparazzi.test.verify")?.toBoolean() == true

    private val maxPercentDifference: Double =
        System.getProperty("app.cash.paparazzi.maxPercentDifferenceDefault")?.toDoubleOrNull() ?: 0.01

    /** `<sourceSetRoot>/snapshots` — goldens live in its `images/` subdirectory. */
    private val imagesDir: File =
        File(System.getProperty("paparazzi.snapshot.dir") ?: error("paparazzi.snapshot.dir not set"), "images")

    private val failuresDir: File?
        get() = System.getProperty("paparazzi.failures.dir")?.let { File(it).apply { mkdirs() } }

    private val reportImagesDir: File?
        get() = System.getProperty("paparazzi.report.dir")
            ?.let { File(it, "directory-snapshots").apply { mkdirs() } }

    override fun newFrameHandler(
        snapshot: Snapshot,
        frameCount: Int,
        fps: Int,
    ): SnapshotHandler.FrameHandler {
        val relativePath = snapshot.name
            ?: error("DirectorySnapshotHandler requires a snapshot name (the relative golden path)")
        val golden = File(imagesDir, "$relativePath.png")

        return object : SnapshotHandler.FrameHandler {
            override fun handle(image: BufferedImage) {
                if (isRecording) {
                    record(golden, image)
                } else if (isVerifying) {
                    verify(golden, image)
                } else {
                    // Match Paparazzi's default mode: render a report artifact, but never mutate
                    // source-controlled goldens unless the explicit record task is running.
                    report(relativePath, image)
                }
            }

            override fun close() = Unit
        }
    }

    override fun close() = Unit

    private fun record(golden: File, image: BufferedImage) {
        golden.parentFile?.mkdirs()
        ImageIO.write(image, "PNG", golden)
    }

    private fun report(relativePath: String, image: BufferedImage) {
        val output = reportImagesDir?.resolve("$relativePath.png") ?: return
        output.parentFile?.mkdirs()
        ImageIO.write(image, "PNG", output)
    }

    private fun verify(golden: File, image: BufferedImage) {
        val expectedPath = golden.absolutePath
        if (!golden.exists()) {
            writeFailureArtifacts(golden, actual = image, delta = null)
            throw AssertionError(
                "Missing golden image: no snapshot recorded at $expectedPath\n" +
                    "Record it with the module's recordPaparazzi task and --no-configuration-cache.",
            )
        }

        val goldenImage = ImageIO.read(golden)
            ?: throw AssertionError(
                "Failed to read golden image at $expectedPath (corrupt file, or a git-LFS pointer instead of the PNG).",
            )

        val (deltaImage, percentDifference, differentPixels) = compare(goldenImage, image)

        val goldenW = goldenImage.width
        val goldenH = goldenImage.height
        val actualW = image.width
        val actualH = image.height

        val error: String? = when {
            percentDifference > maxPercentDifference ->
                "Images differ (by %.4f%%, tolerance %.4f%%)".format(percentDifference, maxPercentDifference)
            abs(goldenW - actualW) >= 2 ->
                "Widths differ too much: ${goldenW}x$goldenH (golden) vs ${actualW}x$actualH (actual)"
            abs(goldenH - actualH) >= 2 ->
                "Heights differ too much: ${goldenW}x$goldenH (golden) vs ${actualW}x$actualH (actual)"
            else -> null
        }

        if (error != null) {
            val (deltaFile, actualFile) = writeFailureArtifacts(golden, actual = image, delta = deltaImage)
            val message = buildString {
                append(error)
                append(" ($differentPixels differing pixel(s))\n")
                append("  expected golden: $expectedPath\n")
                if (deltaFile != null) append("  delta image:     ${deltaFile.absolutePath}\n")
                if (actualFile != null) {
                    append("  actual render:   ${actualFile.absolutePath}\n")
                    append("  accept with:     mv ${actualFile.absolutePath} $expectedPath")
                }
            }
            println(message)
            throw AssertionError(message)
        }
    }

    /** Writes the actual render (and delta, when available) to the failures directory. */
    private fun writeFailureArtifacts(
        golden: File,
        actual: BufferedImage,
        delta: BufferedImage?,
    ): Pair<File?, File?> {
        val dir = failuresDir ?: return null to null
        // Flatten the nested golden path so failure files stay in one directory but remain traceable.
        val flatName = golden.toRelativeString(imagesDir).replace(File.separatorChar, '_').removeSuffix(".png")
        val deltaFile = delta?.let {
            File(dir, "delta-$flatName.png").also { f -> ImageIO.write(it, "PNG", f) }
        }
        val actualFile = File(dir, "$flatName.png").also { f -> ImageIO.write(actual, "PNG", f) }
        return deltaFile to actualFile
    }

    private data class Comparison(
        val delta: BufferedImage,
        val percentDifference: Double,
        val differentPixels: Long,
    )

    /**
     * Pixel diff mirroring Paparazzi's default `OffByTwo` differ in this version: pixels whose ARGB
     * channels are all within ±2 are treated as "similar" (not counted as different); fully
     * transparent pixels on both sides are ignored; the percentage is the summed absolute channel
     * delta over the total possible (w·h·3·256), with a fallback to the fraction of differing pixels
     * when that sum rounds to zero. The delta is the classic side-by-side expected|diff|actual strip.
     */
    private fun compare(golden: BufferedImage, actual: BufferedImage): Comparison {
        val expected = golden.toArgb()
        val expectedWidth = expected.width
        val expectedHeight = expected.height
        val actualWidth = actual.width
        val actualHeight = actual.height

        val maxWidth = max(expectedWidth, actualWidth)
        val maxHeight = max(expectedHeight, actualHeight)

        val deltaImage = BufferedImage(expectedWidth + maxWidth + actualWidth, maxHeight, TYPE_INT_ARGB)
        val g = deltaImage.graphics

        var delta = 0L
        var differentPixels = 0L
        val gray = 0x00808080

        for (y in 0 until maxHeight) {
            for (x in 0 until maxWidth) {
                val expectedRgb = if (x >= expectedWidth || y >= expectedHeight) gray else expected.getRGB(x, y)
                val actualRgb = if (x >= actualWidth || y >= actualHeight) gray else actual.getRGB(x, y)

                if (expectedRgb == actualRgb) {
                    deltaImage.setRGB(expectedWidth + x, y, gray)
                    continue
                }
                // Fully transparent on both sides: colours don't matter.
                if (expectedRgb and -0x1000000 == 0 && actualRgb and -0x1000000 == 0) {
                    deltaImage.setRGB(expectedWidth + x, y, gray)
                    continue
                }

                val deltaA = (actualRgb and -0x1000000).ushr(24) - (expectedRgb and -0x1000000).ushr(24)
                val deltaR = (actualRgb and 0xFF0000).ushr(16) - (expectedRgb and 0xFF0000).ushr(16)
                val deltaG = (actualRgb and 0x00FF00).ushr(8) - (expectedRgb and 0x00FF00).ushr(8)
                val deltaB = (actualRgb and 0x0000FF) - (expectedRgb and 0x0000FF)

                val newR = 128 + deltaR and 0xFF
                val newG = 128 + deltaG and 0xFF
                val newB = 128 + deltaB and 0xFF
                val avgAlpha =
                    ((expectedRgb and -0x1000000).ushr(24) + (actualRgb and -0x1000000).ushr(24)) / 2 shl 24
                val newRGB = avgAlpha or (newR shl 16) or (newG shl 8) or newB

                if (abs(deltaR) <= 2 && abs(deltaG) <= 2 && abs(deltaB) <= 2 && abs(deltaA) <= 2) {
                    // "Similar" — within OffByTwo tolerance, not counted as different.
                    deltaImage.setRGB(expectedWidth + x, y, 0xFF0000FF.toInt())
                    continue
                }

                differentPixels++
                deltaImage.setRGB(expectedWidth + x, y, newRGB)
                delta += abs(deltaR).toLong()
                delta += abs(deltaG).toLong()
                delta += abs(deltaB).toLong()
            }
        }

        g.drawImage(golden, 0, 0, null)
        g.drawImage(actual, expectedWidth + maxWidth, 0, null)
        g.dispose()

        val total = actualHeight.toLong() * actualWidth.toLong() * 3L * 256L
        var percentDifference = delta * 100.0 / total.toDouble()
        if (differentPixels > 0 && percentDifference == 0.0) {
            percentDifference = differentPixels * 100.0 / (actualWidth * actualHeight.toDouble())
        }

        return Comparison(deltaImage, percentDifference, differentPixels)
    }

    private fun BufferedImage.toArgb(): BufferedImage {
        if (type == TYPE_INT_ARGB) return this
        val converted = BufferedImage(width, height, TYPE_INT_ARGB)
        converted.graphics.apply {
            drawImage(this@toArgb, 0, 0, null)
            dispose()
        }
        return converted
    }
}
