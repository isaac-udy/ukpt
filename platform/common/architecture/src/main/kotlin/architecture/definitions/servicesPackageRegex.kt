package architecture.definitions

/**
 * The services package, `feature.x.server.services.**`. Group 1 is the feature name — lazy, so it
 * spans a grouped feature's whole name and stops at the side segment — and group 2 the dotted
 * sub-path after `services` (absent for the services package itself).
 */
internal val servicesPackageRegex = Regex("""^feature\.(.+?)\.server\.services(?:\.(.+))?$""")
