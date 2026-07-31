/**
 * Convention plugin for Compose modules that snapshot-test their UI with Paparazzi.
 *
 * Applies: ukpt.compose-library, ukpt.snapshot-testing-base
 *
 * This is pure composition: compose-library declares the template's standard KMP target set, and
 * the base plugin carries the Paparazzi host-test wiring (see its KDoc for what a module still
 * declares itself, and for the `--no-configuration-cache` requirement). A shell module that
 * hand-wires its own targets applies `ukpt.snapshot-testing-base` directly instead of this.
 */
plugins {
    id("ukpt.compose-library")
    id("ukpt.snapshot-testing-base")
}
