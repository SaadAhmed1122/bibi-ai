# Spike S4 — results (issue #8)

Device: vivo Y17s (V2310), Android 15, Helio G85, 6 GB. 2026-10-30, charging over USB, airplane mode on, app driven over adb by Claude. Build: branch `feature/8-s4-llm-tools`. Raw log: `raw/2026-10-30-vivo-y17s/s4_20261030-004906_V2310.csv` (phone numbers are masked by the app).

## 1. Verdicts

| Check | Verdict | Evidence |
|---|---|---|
| **S4 gate: shortcut path p95 < 300 ms** (PRD 0.7, SPEC §2) | **PASS** | 20 dry runs per action, 0 failures. p95: dial 9.2 ms, flashlight 19.4 ms (torch callback), open_app 13.8 ms, set_alarm 6.7 ms, start_timer 6.2 ms. |
| Decision Maker as intent router (Decision 2026-10-30) | **Works** (info only) | Router accuracy 100 % dial, flashlight, set_alarm; 95 % open_app; 85 % start_timer; 70 % none. ~0.5 s per request. |
| Gemma 3 1B int4 tool calling via LiteRT-LM 0.16.1 (Decision 2026-10-30, provisional) | **Does not work** (info only) | 0 of 100 action prompts produced a valid tool call that reached the Permission Engine. No native `toolCalls` at all; every answer came back as text. |

The gate verdict is shortcut-only and unaffected by the LLM path (SPEC §2).

## 2. Shortcut and fake-LLM benchmark (dry run, torch real)

| action | path | n | fail | p50 ms | p95 ms | max ms |
|---|---|---|---|---|---|---|
| dial | shortcut | 20 | 0 | 7.7 | 9.2 | 11.3 |
| flashlight | shortcut | 20 | 0 | 16.3 | 19.4 | 33.4 |
| open_app | shortcut | 20 | 0 | 12.6 | 13.8 | 14.9 |
| set_alarm | shortcut | 20 | 0 | 5.7 | 6.7 | 11.7 |
| start_timer | shortcut | 20 | 0 | 5.6 | 6.2 | 16.0 |
| dial / flashlight / open_app / set_alarm / start_timer | fake_llm | 6 each | 0 | 6.3–16.5 | 8.9–20.4 | — |

## 3. LLM benchmark (110 prompts, dry run, stops at the Permission Engine decision)

Models loaded together: Decision Maker 11.6 s, Gemma 3 1B 11.2 s, PSS 1,212 MB after load; **peak PSS 1,760 MB** during the run.

| expected | n | DM label right | routed (after 0.5 threshold) | valid JSON | valid call of the expected tool | e2e p50 / p95 | DM p50 |
|---|---|---|---|---|---|---|---|
| dial | 20 | 100 % | 100 % | 5 % | 0 % | 7.1 / 8.1 s | 533 ms |
| flashlight | 20 | 100 % | 90 % | 0 % | 0 % | 4.4 / 6.5 s | 536 ms |
| open_app | 20 | 95 % | 70 % | 30 % | 0 % | 6.0 / 11.6 s | 491 ms |
| set_alarm | 20 | 100 % | 100 % | 80 % | 0 % | 13.6 / 15.3 s | 525 ms |
| start_timer | 20 | 85 % | 85 % | 30 % | 0 % | 10.1 / 11.0 s | 532 ms |
| none | 10 | 70 % | 80 % (no action) | — | — | 0.5 / 5.6 s | 535 ms |

Decision Maker confusions: start_timer → set_alarm (3), none → set_alarm (2), none → start_timer (1), open_app → dial (1).

What Gemma 3 1B wrote instead of a tool call (examples): `set_alarm {"hour": 07; "minute": 0}` (semicolons, leading zero: not JSON), ```` ```tool open_app {"app": "chrome"} ``` ````, `Tool: flashlight {"on": true|false}`, "Okay.", "2 minutes left!", or the phone number repeated back. Even where the shape was close, values were often wrong (`"hour": 0` for "seven thirty"). Every such answer was rejected by ToolCallParser/ToolValidator and fell through to ask-the-user; **no action was dispatched outside the Permission Engine path**.

## 4. Conclusions

- The S4 gate passes: the shortcut path is far under 300 ms on this phone.
- **Decision Maker is a good router** (PRD §13.4/§13.5 pass 1): ~0.5 s, ≥ 95 % on four of five actions. Its 0.5 confidence threshold [OPEN] and the timer/alarm confusion need tuning.
- **Gemma 3 1B int4 is not usable for tool-call arguments** on LiteRT-LM 0.16.1: it is not tool-trained (as the LiteRT-LM guide warns), and it is slow (4–14 s). A more lenient text parser would not fix wrong values.
- Options for pass 2 (argument filling), each needing an owner decision before work:
  1. **Deterministic slot extraction** after the Decision Maker label (time, minutes, phone number, app name, on/off): no second model, fast, fully testable; the LLM stays for chat only.
  2. **A tool-trained small model** (e.g. FunctionGemma 270M on LiteRT-LM), measured with the same 110-prompt benchmark.
  3. Grammar-constrained decoding, if the runtime offers it [VERIFY].

## 5. Not measured / caveats [VERIFY]

- One run, one device, charging; Galaxy A23 not tested.
- Real launches (n = 3 per action) and the owner's on-screen checks (README steps 2–8, 13–14) were not done in this session.
- Runtime check that MediaPipe's removed datatransport logging is never reached: logcat after the run had no `NoClassDefFoundError`, no `datatransport` line and no crash, but the buffer may not cover the whole run.
- Strix security scan not run (Strix CLI not installed on this machine).

## 6. Pass 2 = slot extraction (PR #25), vivo Y17s, 2026-10-30 19:17

Same 110 prompts, dry run, charging. Decision Maker only (Gemma not loaded): load 11.6 s, **PSS 599 MB** (was 1,212 MB with Gemma). Raw: `raw/2026-10-30-vivo-y17s/s4_20261030-191713_V2310.csv`. App summary:

| expected | n | DM acc | tool ok | slot ok | args = | p50 ms | p95 ms | max PSS |
|---|---|---|---|---|---|---|---|---|
| open_app | 20 | 70 % | 65 % | 65 % | 60 % | 491 | 572 | 596 MB |
| set_alarm | 20 | 100 % | 100 % | 100 % | 100 % | 488 | 498 | 595 MB |
| start_timer | 20 | 85 % | 85 % | 85 % | 85 % | 488 | 498 | 596 MB |
| flashlight | 20 | 90 % | 90 % | 90 % | 90 % | 486 | 493 | 597 MB |
| dial | 20 | 100 % | 100 % | 100 % | 100 % | 488 | 498 | 599 MB |
| none | 10 | 80 % | 100 % (no call) | n/a | n/a | 485 | 491 | 600 MB |

**87 of 100 action prompts produced the right call with the right values** (Gemma tool calling: 0 of 100), end to end in about 0.5 s. None of the 10 "none" prompts produced a call.

Misses (13), all but one ended with no action or a question to the user:
- **Confidence threshold (7):** the router's label was right but below the 0.5 threshold [OPEN]: "I'd like to look at my photos" 0.40, "let me see the calculator" 0.11, "switch over to spotify" 0.27, "show my gallery" 0.35, "bring the weather app up" 0.38, "it's too dark to see anything" 0.47, "give me some light" 0.43; plus "jump into whatsapp" labelled dial 0.44.
- **Timer heard as alarm (3):** the extractor saw a duration in an alarm request and asked instead of guessing. Candidate fix: re-route set_alarm → start_timer when the text has a duration and no clock time [OPEN].
- **App not installed (1):** Instagram is not on this phone; the app asked.
- **Wrong app (1):** "get me into google drive" → `open_app {"app":"google"}` (Google, not Drive). This call reached the Permission Engine (dry run). Fix: prefer the longest matching app name [OPEN].

The 100/100 offline score in the JVM tests is an upper bound (extractor written with the prompts in view; assumed app list).

## 7. Tuning after §6 (#8), JVM tests (run on the vivo in §8)

Three changes, each with tests written first (`tools/core-test`, 559 checks pass):
1. **Router threshold 0.5 → 0.25** (`ToolRouting.MIN_CONFIDENCE`). Of the §6 threshold misses, this lets through photos 0.40, spotify 0.27, gallery 0.35, weather 0.38, dark 0.47, light 0.43 and "tea" 0.27; calculator 0.11 stays out. "what time is it in Tokyo" (set_alarm 0.15) stays out. A wrong label that gets through still meets slot extraction, which asks when a value is missing. The value was picked on the same 110 prompts [VERIFY on a fresh prompt set].
2. **set_alarm → start_timer re-route:** under the set_alarm label, a duration with no clock time becomes a timer ("let me know when 3 minutes are up" → 180 s). A clock time plus a duration still asks. This is the only cross-tool route, and it still goes through ToolCallParser, ToolValidator and the Permission Engine. Offline: 20/20 timer prompts labelled set_alarm start the right timer; none set an alarm.
3. **Vendor word + unknown app asks:** "google drive" with no Drive app now asks "Which app should I open?" instead of opening Google. With Drive installed it still opens Drive. Whether Drive is on this vivo is [VERIFY]; the §6 result (Google opened) fits it being absent, because with Drive installed the extractor picks Drive.

Expected on the vivo, if the router labels come out the same: about 95 of 100 [VERIFY by re-running the slot benchmark]. Strix not run (CLI not installed); these files are tool-router code (CLAUDE.md §9).

## 8. Tuned slot extraction on the vivo Y17s, 2026-10-31 15:44

The §7 tuning, built from `main` (PR #26), same 110 prompts, dry run, charging, Gemma not loaded. Decision Maker load 11.6 s, **PSS 595 MB**. Raw: `raw/2026-10-31-vivo-y17s/s4_20261031-154401_V2310.csv`. App summary:

| expected | n | DM acc | tool ok | slot ok | args = | p50 ms | p95 ms | max PSS |
|---|---|---|---|---|---|---|---|---|
| open_app | 20 | 90 % | 75 % | 75 % | 75 % | 493 | 548 | 597 MB |
| set_alarm | 20 | 100 % | 100 % | 100 % | 100 % | 491 | 497 | 599 MB |
| start_timer | 20 | 85 % | 100 % | 100 % | 100 % | 487 | 535 | 601 MB |
| flashlight | 20 | 100 % | 100 % | 100 % | 100 % | 485 | 502 | 601 MB |
| dial | 20 | 100 % | 100 % | 100 % | 100 % | 488 | 497 | 592 MB |
| none | 10 | 80 % | 100 % (no call) | n/a | n/a | 487 | 503 | 593 MB |

**95 of 100 action prompts produced the right call with the right values** (§6: 87), about 0.5 s end to end, as §7 expected. **No wrong call reached the Permission Engine** (§6: 1, "google drive" → Google). None of the 10 "none" prompts produced a call.

- **Timer re-route works:** the 3 timer prompts the router labelled set_alarm all started the right timer (start_timer 85 % → 100 %).
- **Threshold 0.25 works:** flashlight 90 % → 100 %; open_app 60 % → 75 %.
- **The 5 misses, all open_app, all safe** (no action or a question):
  - "let me see the calculator": router confidence 0.11 < 0.25, no action;
  - "jump into whatsapp": labelled dial 0.44, no number, so it asks;
  - "switch over to spotify", "I want to browse instagram": not installed on this phone, so it asks (the right behaviour);
  - "get me into google drive": Drive not installed, so it now asks instead of opening Google (§7 fix 3, confirmed).
- **Two "none" prompts led to a question, not an action:** "don't set an alarm tonight" (labelled set_alarm 0.97, asks for a time) and "how long should I boil an egg" (labelled start_timer, asks for a length). The negation in the first is not understood [OPEN for P2 tools work].

Caveat: the threshold and the re-route were tuned on these same 110 prompts, so 95/100 is likely optimistic for new phrasings [VERIFY on a fresh prompt set before P2].

