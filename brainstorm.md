# Brainstorm (scratchpad)

Raw ideas. Not binding. The binding plan is [PRD Part 0](reference/Pip_PRD_Final.md); the clean version is [project_spec.md](project_spec.md).

## Goals
From PRD Part 0.3:
- **Phase 0 (spikes):** answer "does this work on a mid-range phone?" with numbers. S1 and S2 pass on the Galaxy A23, or an honest minimum-hardware decision gets written down.
- **MVP:** closed-test release on Google Play. 10–12 real testers complete the core loop, with no crash in the core loop.
- **Portfolio:** public write-up with benchmarks, an architecture diagram, a short demo video, and source or sanitized excerpts.
- **Funding decision:** look at tester retention, performance measured on 3+ device classes, and whether privacy plus character is a clear differentiator.

## Non-goals
From PRD Part 0.3 (MVP): monetization, multi-language, multi-user profiles, cloud sync, wearables, Live2D or 3D avatars, enterprise features, Accessibility-service automation.

## User flows
### First run
- Install → AI disclosure + age notice (FR-188) → download the Core Pack (resume, checksum) → optional overlay permission with rationale → Pip appears.
- Without overlay permission → in-app chat only.
- Onboarding isn't designed yet. These are notes only.

### Daily chat
- Tap the floating character → compact card → type → streamed short answer → stop button.
- "Open in chat" goes to the full chat screen.

### Voice
- Ships only if S3 passes. Long-press to talk (push-to-talk), no background listening.
- If S3 fails on the A23, show "Voice not supported on this device" (Part 0.5).

### Tool action
- "Set a timer for 5 minutes" → shortcut path (no LLM) → runs → Pip confirms.
- LLM path → structured tool call → validated → Safe runs; Sensitive shows a confirmation card.

### Delete my data
- Privacy dashboard → see what's stored and for how long → export → "Delete all".
- Single memory: long-press → forget.

## Name candidates (working name: Pip)
The name should be cute, short, and easy to say aloud. From PRD Appendix F: Pip, Mochi, Sprout, Nova, Wisp, Orbit-friend, Lumi, Tama, Bit, Kiko.
Extra ideas, each with a character hook:
- **Pip:** a tiny seed-sprite with a leaf on its head.
- **Mochi:** a soft round rice-cake blob that squishes when dragged.
- **Wisp:** a floating flame-spirit that glows when thinking.
- **Kiko:** a small fox-like critter with a big tail that wraps around it to sleep.
- **Nibbo:** a round little robot with one antenna that blinks.
- **Puffin / Puff:** a cloud-puff that rains a little when confused.
- **Tama:** an egg-shaped pet (nod to the classic, but check trademark conflicts).

None of these have been checked for trademarks or Play Store name availability yet [VERIFY]. That's PRD 0.12 open item 5.

### Name check — 2026-10-28 (web search only, not legal clearance)
Taken in the same space (AI companions, pets, assistants), so drop these:
| Name | Conflict found |
|---|---|
| Pip | "Pip", a safe AI chat companion for kids (guidedaiforkids.com) |
| Mochi | Several AI companion apps: "Mochi: AI Companion Chat" (Play), himochichat.com, get-mochi.com, Mochi Crew |
| Nibbo | "Nibbo: Pet on Your Screen" on Play, a screen pet (same concept); also a family app and a recipe app |
| Nova | Several AI chatbot apps named Nova (Play/App Store), plus Nova Launcher's AI |
| Lumi | "Lumi AI" chat app on Play |
| Wisp | Wisp voice notes (AI) and Wisp AI news, both on Play |
| Kiko | "KiKO" AI video companion on Play |
| Sprout | Sprout AI knowledge companion, Sprout.ai, Little Sprout parenting AI |
| Puffin | Puffin Browser (an established brand) |
| Pipling | Pipling kids app on Play, a baby tracker, and a Stream Deck pet |
| Tama | Too close to Tamagotchi (Bandai) |
| Mibbit | A known IRC client brand |
| Bimble | Too close to Bumble (big brand with its own AI features) |

No app or trademark found with the exact spelling (these are the shortlist):
| Name | Say it | Character hook | Nearest thing found |
|---|---|---|---|
| **Pomki** | POM-kee | A fluffy pom-pom ball with little feet that puffs up when happy | POMIKU (US mark, hand tools); the "POM" juice brand family (different goods) |
| **Wobbi** | WOB-ee | A jelly blob that wobbles when dragged and squashes when dropped (fits PRD §20.13 spring physics) | A WOBBI US filing for dish drying mats (suspended, different goods); WOBLI wellness software (pending) |
| **Zibbo** | ZIB-oh | A small round robot with an antenna light | "Zibbo", developer name of a 2013 puzzle game (looks dormant) |
| Tumbi | TUM-bee | A round tumbling critter | "Tummi" baby-food app (similar sound) |

Still to verify for the shortlist [VERIFY]:
- Exact search in the Play Store app.
- WIPO Global Brand Database, classes 9 and 42.
- The trademark office for each launch market.
- `.app` / `.com` domain availability.
- No bad meaning in Arabic, Urdu, Hindi, or Spanish.

## Open questions
- Part 0.8 lists "dial with prefilled number" as one of five *Safe* actions, but §16.3 rates `call_contact` (dialer intent) as SENSITIVE. Which one wins?
- Does MVP memory search include semantic (embedding) search, or only keyword (FTS)? Part 0.8 only says "search".
- Avatar tech: sprite sheet or Rive (OD-03)?
- Which four emotion states make the MVP cut? (Part 0.8 allows "beyond four states" only if a gate passes.)

## Voice transcription inbox
Paste each voice-note transcript below as `### YYYY-MM-DD – topic`, then the raw text. Don't edit it here; move decisions to docs/project_status.md.
