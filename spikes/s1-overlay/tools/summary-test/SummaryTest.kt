package io.github.saadahmed1122.bibi.spike.s1

import kotlin.math.abs

// JVM-only tests for Summary (no Android). Run with tools/summary-test/run.sh.

/** Copied from SpikeLog.HEADER; update both together. */
const val HEADER =
    "time,epoch_ms,run_id,kind,detail,battery_pct,charge_uah,current_ua,charging,screen_on,thermal," +
        "frames,fps,pss_kb,calm,mode," +
        "voltage_mv,batt_temp_dc,brightness,brightness_mode,power_save,refresh_hz"

const val T0 = 1_760_000_000_000L

fun row(
    tMs: Long,
    kind: String,
    currentUa: Long = -900_000,
    charging: Boolean = false,
    screenOn: Boolean = true,
    mode: String = "bibi12",
    pct: Int = 80,
): String {
    val sample = kind == "sample"
    return listOf(
        "2026-10-29 10:00:00", (T0 + tMs).toString(), T0.toString(), kind, "", pct.toString(), "",
        currentUa.toString(), charging.toString(), screenOn.toString(), "0",
        if (sample) "120" else "", if (sample) "12.00" else "", if (sample) "51200" else "",
        "false", mode, "3950", "312", "128", "0", "false", "60.0",
    ).joinToString(",")
}

/** start, a sample every 10 s up to [minutes], stop; [tweak] may alter rows by index. */
fun cleanRun(minutes: Int, tweak: (Int, Long) -> String? = { _, _ -> null }): String {
    val rows = mutableListOf(HEADER, row(0, "start"))
    val n = minutes * 6
    for (i in 1..n) {
        val t = i * 10_000L
        rows += tweak(i, t) ?: row(t, "sample", pct = 80 - (i * 18 / n))
    }
    rows += row(n * 10_000L, "stop")
    return rows.joinToString("\n") + "\n"
}

fun check(name: String, ok: Boolean, detail: String) {
    if (!ok) throw AssertionError("FAIL $name: $detail")
    println("PASS $name")
}

fun main() {
    val a = Summary.from(cleanRun(60))
    check("a) 60-min clean run is valid", a.valid, a.validityLine())
    check("a) mean ~ 900 mA", abs(a.meanCurrentMa!! - 900.0) <= 1.0, "mean=${a.meanCurrentMa}")
    check("a) %/h ~ 18.0", abs(a.pctPerHourFromCurrent!! - 18.0) < 0.05, "pct/h=${a.pctPerHourFromCurrent}")

    val b = Summary.from(cleanRun(50))
    check("b) 50-min run invalid, shorter than 60 min",
        !b.valid && b.validityLine().contains("shorter than 60 min"), b.validityLine())

    val c = Summary.from(cleanRun(60) { i, t -> if (i == 100) row(t, "screen_off") else null })
    check("c) screen_off event invalid, names screen off",
        !c.valid && c.validityLine().contains("screen off"), c.validityLine())

    val d = Summary.from(cleanRun(60) { i, t -> if (i == 200) row(t, "sample", charging = true) else null })
    check("d) charging sample invalid, names charging",
        !d.valid && d.validityLine().contains("charging"), d.validityLine())

    // Samples at 0, 10, 40, 50 s with 1000, 500, 2000, 7000 mA (signs mixed):
    // (1000*10 + 500*30 + 2000*10) / 50 = 900 mA; the last sample has no interval.
    val e = Summary.from(
        listOf(
            HEADER,
            row(0, "sample", currentUa = -1_000_000),
            row(10_000, "sample", currentUa = 500_000),
            row(40_000, "event_ignored", currentUa = -9_000_000),
            row(40_000, "sample", currentUa = -2_000_000),
            row(50_000, "sample", currentUa = -7_000_000),
        ).joinToString("\n"),
    )
    check("e) time-weighted mean with uneven spacing = 900 mA",
        abs(e.meanCurrentMa!! - 900.0) < 1e-6, "mean=${e.meanCurrentMa}")

    println(a.describe())
    println("ALL PASS")
}
