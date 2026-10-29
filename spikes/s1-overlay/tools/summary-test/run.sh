#!/usr/bin/env bash
# Compiles Summary.kt with SummaryTest.kt, and FrameSchedule.kt with FrameScheduleTest.kt, on a
# plain JVM (no Android) and runs the tests.
#   spikes/s1-overlay/tools/summary-test/run.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PKGDIR="$HERE/../../app/src/main/java/io/github/saadahmed1122/bibi/spike/s1"
SRC="$PKGDIR/Summary.kt"
KLIB="$(ls -d /opt/gradle-*/lib | head -1)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

CP="$KLIB/$(cd "$KLIB" && ls kotlin-compiler-embeddable-*.jar)"
for j in kotlin-stdlib kotlin-script-runtime kotlin-reflect kotlin-daemon-embeddable trove4j annotations kotlinx-coroutines-core-jvm; do
  CP="$CP:$(ls "$KLIB"/$j-*.jar | head -1)"
done
STDLIB="$(ls "$KLIB"/kotlin-stdlib-*.jar | head -1)"

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$STDLIB" -d "$WORK/out" "$SRC" "$HERE/SummaryTest.kt" 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -f "$WORK/out/io/github/saadahmed1122/bibi/spike/s1/SummaryTestKt.class" || { echo "COMPILE FAILED"; exit 1; }

java -cp "$WORK/out:$STDLIB" io.github.saadahmed1122.bibi.spike.s1.SummaryTestKt

java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-reflect -jvm-target 17 \
  -classpath "$STDLIB" -d "$WORK/frame" "$PKGDIR/FrameSchedule.kt" "$HERE/FrameScheduleTest.kt" 2>&1 \
  | grep -v -e 'JAVA_TOOL_OPTIONS' -e 'Kotlin home directory' || true
test -f "$WORK/frame/io/github/saadahmed1122/bibi/spike/s1/frametest/FrameScheduleTestKt.class" \
  || { echo "COMPILE FAILED (FrameSchedule)"; exit 1; }

java -cp "$WORK/frame:$STDLIB" io.github.saadahmed1122.bibi.spike.s1.frametest.FrameScheduleTestKt
