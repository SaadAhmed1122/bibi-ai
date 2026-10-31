package io.github.saadahmed1122.bibi.core.tools

import org.junit.Test

/** Runs the S4 suites (ported with their `check` helpers) under JUnit, one test per suite. */
class CoreToolsTest {
    @Test fun shortcutParser() = shortcutParserTests()

    @Test fun toolCallParser() = toolCallParserTests()

    @Test fun permissionEngine() = permissionEngineTests()

    @Test fun appResolver() = appResolverTests()

    @Test fun llmToolPath() = llmToolPathTests()

    @Test fun slotExtractor() = slotExtractorTests()

    @Test fun slotWrongLabel() = slotWrongLabelTests()
}
