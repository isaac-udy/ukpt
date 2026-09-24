package architecture.definitions

/**
 * The services package, `feature.x.server.services.**`. Group 1 is the feature name, group 2 the
 * dotted sub-path after `services` (absent for the services package itself).
 */
internal val servicesPackageRegex = Regex("""^feature\.([^.]+)\.server\.services(?:\.(.+))?$""")
