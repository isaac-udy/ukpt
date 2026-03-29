package architecture

import com.lemonappdev.konsist.api.Konsist

val projectScope = Konsist
    .scopeFromProject()
    .slice {
        !it.path.contains("embedded-enro") &&
                !it.path.contains("embedded-udytils") &&
                !it.path.contains("/src/test/") &&
                !it.path.contains("/src/screenshotTest/")
    }
