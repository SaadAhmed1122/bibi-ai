# Demo video: script and storyboard

P4 Task 11 (#136), SPEC-writeup portfolio package. A 75–90 s screen recording of the vivo Y17s for LinkedIn and the portfolio. The owner records it; the existing Play FGS video (`docs/play-store/bibi-fgs-demo.mp4`) shows the recording setup works.

## Before recording
- Airplane mode **on**, and keep it visible in the status bar: it proves the "offline" claim on camera.
- Do Not Disturb on, so no notifications appear. Use a clean home screen with no personal apps or widgets in view.
- Use only made-up content: "my cat is called Milo", "I love jasmine tea". No real names, numbers or places.
- Model, action router and voice pack imported. Floating Bibi on, Calm mode off, Read aloud on.
- Record with `adb shell screenrecord --time-limit 180 /sdcard/demo.mp4` and the phone's own audio playing near a mic, or speak the questions yourself.

## Storyboard

| # | Time | Screen | Action | Caption on screen |
|---|---|---|---|---|
| 1 | 0–5 s | Home screen, Bibi floating at the edge, airplane icon visible | Hold still | "An AI companion with no internet." |
| 2 | 5–15 s | Home screen | Drag Bibi across; it snaps to the edge | "Bibi floats over any app." |
| 3 | 15–30 s | Home screen | Tap Bibi, say "Give me one tip for better sleep". The listening bar shows, then the reply bubble, read aloud | "Tap and talk. Speech, answer and voice all run on the phone." |
| 4 | 30–40 s | Home screen | Tap Bibi, say "Set a timer for five minutes". The Clock app's timer starts | "Phone actions, through one permission gate." |
| 5 | 40–50 s | Bibi app, Chat | Type "call 0123 456 789". The confirmation card appears; tap Cancel | "Sensitive actions always ask first." |
| 6 | 50–60 s | Chat | Type "remember that my cat is called Milo", then "remember that my password is hunter2" (refused) | "It remembers what you ask, and refuses secrets." |
| 7 | 60–70 s | Memory tab | Scroll the list; show Keep for and Export | "You can see, edit and export every memory." |
| 8 | 70–80 s | Privacy screen | Show "What Bibi keeps" and the Delete everything button (don't press it) | "Nothing leaves the phone unless you share it." |
| 9 | 80–88 s | Feature graphic `docs/play-store/feature-1024x500.png` | End card | "Bibi AI · Gemma 3 1B on a 6 GB phone · github.com/SaadAhmed1122/bibi-ai" |

## Voice-over (optional, about 70 words)
> This is Bibi, an AI companion that runs entirely on a mid-range phone. Airplane mode is on. I tap and ask a question: the speech recognition, the language model and the voice all run right here. It can set a timer, and it always asks before anything sensitive, like a call. It remembers what I tell it, refuses to store a password, and shows me everything it keeps.

## After recording
- Watch the whole video for notifications, account names or personal content before posting. Same rule as the screenshots: [public-release-audit.md](../public-release-audit.md).
- Keep the raw file outside the repo. Commit only the final cut, under 5 MB, to `docs/linkedin/media/` if it's needed there.
