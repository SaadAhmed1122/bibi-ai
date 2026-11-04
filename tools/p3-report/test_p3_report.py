"""Tests for p3_report.py (SPEC-stability.md criterion 1). Run: python3 -I tools/p3-report/test_p3_report.py"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import p3_report  # noqa: E402


def report(sessions=10, crash_free=10, active=3, first14=3, day7="no", model="V2310", version="0.3.0-p3",
           messages=5, talks=1, memories=0, actions="none", crashes=None, anrs=0, low=0):
    crashes = sessions - crash_free if crashes is None else crashes
    return "\n".join([
        "Bibi AI usage numbers",
        f"App version: {version}",
        f"Phone: {model}, Android 15",
        "First use: 2026-11-10",
        f"Active days: {active} ({first14} in the first 14 days)",
        f"Used on day 7 or later: {day7}",
        f"Sessions: {sessions}",
        f"Crash-free sessions: {crash_free}",
        f"Crashes: {crashes}",
        f"ANRs: {anrs}",
        f"Low-memory kills: {low}",
        f"Messages: {messages}",
        f"Talks: {talks}",
        f"Memories saved: {memories}",
        f"Actions: {actions}",
        "Only these numbers are in this report: no messages, voice or memories.",
    ])


class ReportToolTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def put(self, name, text):
        with open(os.path.join(self.dir, name), "w") as f:
            f.write(text)

    def test_parses_a_report(self):
        r = p3_report.parse(report(sessions=12, crash_free=11, actions="SET_TIMER 1, TOGGLE_FLASHLIGHT 2", day7="yes"))
        self.assertEqual(12, r["sessions"])
        self.assertEqual(11, r["crash_free"])
        self.assertEqual("V2310", r["model"])
        self.assertTrue(r["day7"])
        self.assertEqual({"SET_TIMER": 1, "TOGGLE_FLASHLIGHT": 2}, r["actions"])

    def test_text_pasted_with_mail_quoting_still_parses(self):
        quoted = "\n".join("> " + line for line in report(sessions=4, crash_free=4).splitlines())
        self.assertEqual(4, p3_report.parse("Hi, here you go!\n\n" + quoted + "\n\nThanks")["sessions"])

    def test_day14_report_replaces_day7_and_duplicates_count_once(self):
        self.put("T1-day7.txt", report(sessions=5, crash_free=5))
        self.put("T1-day14.txt", report(sessions=20, crash_free=19, day7="yes"))
        self.put("T1-day14-again.txt", report(sessions=20, crash_free=19, day7="yes"))
        self.put("T2-day7.txt", report(sessions=8, crash_free=8))
        latest, skipped = p3_report.load(self.dir)
        self.assertEqual([], skipped)
        self.assertEqual(["T1", "T2"], sorted(latest))
        self.assertEqual(20, latest["T1"]["sessions"])

    def test_malformed_report_is_skipped_and_named(self):
        self.put("T3-day7.txt", "Sessions: lots\nhello")
        self.put("notes.txt", report())  # no tester id in the name
        latest, skipped = p3_report.load(self.dir)
        self.assertEqual({}, latest)
        self.assertEqual(["T3-day7.txt", "notes.txt"], sorted(n for n, _ in skipped))

    def test_summary_gives_the_gate_and_retention(self):
        latest = {
            "T1": p3_report.parse(report(sessions=60, crash_free=60, first14=9, day7="yes")),
            "T2": p3_report.parse(report(sessions=40, crash_free=39, first14=2, day7="no", model="SM-A235F")),
        }
        s = p3_report.summarize(latest)
        self.assertEqual(100, s["sessions"])
        self.assertEqual(99, s["crash_free"])
        self.assertAlmostEqual(99.0, s["crash_free_pct"])
        self.assertEqual(1, s["day7_testers"])
        self.assertEqual({"V2310": (60, 60), "SM-A235F": (40, 39)}, s["by_model"])

    def test_markdown_states_verdict_and_caveats(self):
        latest = {"T1": p3_report.parse(report(sessions=100, crash_free=99))}
        md = p3_report.markdown(p3_report.summarize(latest), skipped=[("bad.txt", "no tester id")], days=10)
        self.assertIn("99.0 %", md)
        self.assertIn("1 tester", md)
        self.assertIn("fewer than 10 testers", md)
        self.assertIn("under 14 days", md)
        self.assertIn("bad.txt", md)
        gate_to_usage = md.split("## Gate")[1].split("## Per tester")[0]
        self.assertNotIn("T1", gate_to_usage)  # only the per-tester table names testers

    def test_below_the_gate_is_a_fail(self):
        latest = {"T1": p3_report.parse(report(sessions=50, crash_free=48))}
        md = p3_report.markdown(p3_report.summarize(latest), skipped=[], days=14)
        self.assertIn("**FAIL**", md)

    def test_no_reports(self):
        md = p3_report.markdown(p3_report.summarize({}), skipped=[], days=None)
        self.assertIn("No reports yet", md)


if __name__ == "__main__":
    unittest.main()
