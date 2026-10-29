package io.github.saadahmed1122.bibi.spike.s4.coretest

// Minimal assertion helpers for the JVM-only tests (no JUnit: no external libraries in this spike).

var passed = 0

fun check(name: String, ok: Boolean, detail: () -> String = { "" }) {
    if (!ok) throw AssertionError("FAIL $name: ${detail()}")
    passed++
    println("PASS $name")
}

fun <T> checkEq(name: String, expected: T, actual: T) =
    check(name, expected == actual) { "expected <$expected> but was <$actual>" }
