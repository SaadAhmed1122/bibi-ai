package io.github.saadahmed1122.bibi.spike.s4.coretest

// Runs every suite. Started by tools/core-test/run.sh.
fun main() {
    shortcutParserTests()
    toolCallParserTests()
    permissionEngineTests()
    appResolverTests()
    summaryTests()
    println("$passed checks")
    println("ALL PASS")
}
