#!/usr/bin/env bash
# Compiles the pure-Kotlin core of the S4 spike (no Android imports) with the JVM tests in this
# folder on a plain JVM and runs them. Compiling without android.jar on the classpath also proves
# the core has no Android dependency.
#   spikes/s4-tools/tools/core-test/run.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PKGDIR="$HERE/../../app/src/main/java/io/github/saadahmed1122/bibi/spike/s4"
# The pure-Kotlin files. Everything else in PKGDIR uses android.* and is checked by tools/typecheck.sh.
CORE="ToolCall.kt ToolValidator.kt ShortcutParser.kt MiniJson.kt ToolCallParser.kt PermissionEngine.kt AppResolver.kt FakeLlm.kt Summary.kt LlmToolPath.kt SlotExtractor.kt LlmBenchPrompts.kt LlmSummary.kt"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SRCS=()
for f in $CORE; do SRCS+=("$PKGDIR/$f"); done

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$STDLIB" -d "$WORK/out" "${SRCS[@]}" "$HERE"/*.kt 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -f "$WORK/out/io/github/saadahmed1122/bibi/spike/s4/coretest/TestMainKt.class" || { echo "COMPILE FAILED"; exit 1; }

java -cp "$WORK/out:$STDLIB" io.github.saadahmed1122.bibi.spike.s4.coretest.TestMainKt
