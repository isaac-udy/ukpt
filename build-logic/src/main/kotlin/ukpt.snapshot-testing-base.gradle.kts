import app.cash.paparazzi.gradle.PrepareResourcesTask
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension
import com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType
import com.android.build.gradle.internal.res.GenerateLibraryRFileTask
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import java.time.ZoneId
import ukpt.runAndroidHostTestsOnJUnitPlatform

/**
 * Leaf convention for Paparazzi snapshot-test wiring, independent of the module's target set.
 *
 * Applies: KotlinMultiplatform, the AGP 9 Android KMP library plugin, Paparazzi
 *
 * Most modules should apply `ukpt.snapshot-testing`, which composes this with the template's
 * standard target set via `ukpt.compose-library`. This leaf exists for shell modules that
 * hand-wire their own targets (a named `jvm("desktop")` target, an iOS framework binary, …) and
 * therefore can't apply `ukpt.compose-library` without gaining a second, unwanted `jvm()` target.
 * The plugins applied here are the ones such a module already has — applying them again is a
 * no-op — and none of them declares a non-Android target.
 *
 * An Android target is required: Paparazzi runs as an Android host test. A module applying this
 * still declares its own `androidHostTest` dependency on `dev.isaacudy.udytils:snapshot` (the
 * harness) and its own `PreviewSnapshotTest` naming the package tree to scan — those are the
 * module's facts, not boilerplate.
 *
 * The snapshot test JVM runs in locale `en-US` and time zone `UTC`. A project sets the Gradle
 * properties `ukpt.snapshotLocale` (`language-COUNTRY`) and `ukpt.snapshotTimeZone` (a time zone
 * ID) in `gradle.properties` to record in another locale or time zone.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("app.cash.paparazzi")
}

kotlin {
    @Suppress("UnstableApiUsage")
    androidLibrary {
        // Host (JVM) unit-test component — Paparazzi attaches its record/verify tasks here and
        // reads the KMP `androidHostTest` source set.
        withHostTestBuilder {
        }.configure {
        }

        // The AGP KMP library plugin disables Android resource processing by default, and with it
        // the R class Paparazzi resolves reflectively at runtime — without this flag the host
        // tests die with ClassNotFoundException: <module>.R. ukpt.compose-library also enables it
        // (for its own reason: packaging composeResources as Android assets); setting it twice is
        // harmless, and a hand-wired module applying only this leaf needs it from here.
        androidResources {
            enable = true
        }
    }
}

afterEvaluate { runAndroidHostTestsOnJUnitPlatform() }

extensions.configure<KotlinMultiplatformAndroidComponentsExtension> {
    onVariants { variant ->
        val hostTest = variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE] ?: return@onVariants
        val hostName = hostTest.name.replaceFirstChar(Char::uppercaseChar)
        hostTest.configureTestTask { test ->
            // Paparazzi loads each library's R class from the host test's aggregated stub jar.
            val stubRJar = tasks.named<GenerateLibraryRFileTask>("generate${hostName}StubRFile").flatMap { it.rClassOutputJar }
            test.classpath += files(stubRJar)
        }
        val localAssets = variant.sources.assets?.all
        val moduleAssets = variant.runtimeConfiguration.incoming.artifactView {
            attributes.attribute(ARTIFACT_TYPE_ATTRIBUTE, ArtifactType.ASSETS.type)
            componentFilter { it is ProjectComponentIdentifier }
        }.files
        val moduleDir = layout.projectDirectory.asFile
        val variantName = variant.name.replaceFirstChar(Char::uppercaseChar)
        tasks.named<PrepareResourcesTask>("preparePaparazzi${variantName}Resources") {
            // Paparazzi's zipped provider queries generated Compose assets during configuration-cache storage (#2345).
            projectAssetDirs.empty()
            localAssets?.let { layers ->
                projectAssetDirs.addAll(layers.map { directories ->
                    directories.flatten().asReversed().map { it.asFile.relativeTo(moduleDir).invariantSeparatorsPath }
                })
            }
            projectAssetDirs.addAll(moduleAssets.elements.map { dirs ->
                dirs.map { it.asFile.relativeTo(moduleDir).invariantSeparatorsPath }
            })
        }
    }
}

// The Snapshot test category (see ukpt.test-categories): `./gradlew snapshotTest`.
tasks.register("snapshotTest") {
    group = "verification"
    description = "Verifies the Paparazzi snapshot goldens."
    dependsOn("verifyPaparazzi")
}

tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        // A golden renders dates, weekday order and times from the JVM's default locale and time
        // zone, so every machine that records or verifies goldens must run with the same pair.
        val snapshotLocale = providers.gradleProperty("ukpt.snapshotLocale").getOrElse("en-US")
        require(Regex("[a-z]{2,3}-[A-Z]{2}").matches(snapshotLocale)) {
            "ukpt.snapshotLocale must be language-COUNTRY, for example en-US. Found: '$snapshotLocale'."
        }
        val snapshotTimeZone = providers.gradleProperty("ukpt.snapshotTimeZone").getOrElse("UTC")
        require(runCatching { ZoneId.of(snapshotTimeZone) }.isSuccess) {
            "ukpt.snapshotTimeZone must be a time zone ID, for example UTC or Pacific/Auckland. Found: '$snapshotTimeZone'."
        }
        systemProperty("user.language", snapshotLocale.substringBefore("-"))
        systemProperty("user.country", snapshotLocale.substringAfter("-"))
        systemProperty("user.timezone", snapshotTimeZone)
    }
}
