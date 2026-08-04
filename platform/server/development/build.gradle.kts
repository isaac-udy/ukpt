plugins {
    id("ukpt.jvm-library")
}

kotlin {
    compilerOptions {
        // Scenarios write through the generated Exposed tables, whose UUID id columns are
        // kotlin.uuid.Uuid (still experimental under this Kotlin toolchain).
        freeCompilerArgs.add("-opt-in=kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    api(projects.platform.server.postgres)

    // Zonky's embedded Postgres and its ~150 MB of native binaries. They reach the runtime
    // classpath of anything that depends on this module, so ONLY `:app:server` may depend on
    // it, and only to serve its dev-database path — never a feature or a production module.
    // `api`, because the app names DevServer/DevServerConfig/DevServerHandle directly.
    api(libs.udytils.postgres.embedded)
}
