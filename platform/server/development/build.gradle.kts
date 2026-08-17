plugins {
    id("ukpt.jvm-library")
}

kotlin {
    compilerOptions {
        // Scenarios write through the generated Exposed tables, whose UUID id columns are
        // kotlin.uuid.Uuid.
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    api(projects.platform.server.postgres)

    // Zonky's ~150 MB of native Postgres binaries reach the runtime classpath of anything that
    // depends on this module, so ONLY `:app:server` may, and only for its dev-database path.
    // `api`, because the app names DevServer/DevServerConfig directly.
    api(libs.udytils.postgres.embedded)
}
