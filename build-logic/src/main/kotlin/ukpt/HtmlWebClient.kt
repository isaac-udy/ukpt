package ukpt

import org.gradle.api.Project

/** True when `ukpt.htmlWebClient` is set, which builds the Compose HTML web client. */
val Project.isHtmlWebClientEnabled: Boolean
    get() = providers.gradleProperty("ukpt.htmlWebClient").orNull.toBoolean()
