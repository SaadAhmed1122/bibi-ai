package io.github.saadahmed1122.bibi.core.tools

// Assertion helpers the S4 suites were written with; CoreToolsTest runs each suite under JUnit.

var passed = 0

fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
    if (!ok) throw AssertionError("FAIL $name: ${detail()}")
    passed++
    println("PASS $name")
}

fun <T> checkEq(name: String, expected: T, actual: T) =
    check(name, expected == actual) { "expected <$expected> but was <$actual>" }
