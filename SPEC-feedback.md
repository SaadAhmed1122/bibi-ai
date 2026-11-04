# Spec: feedback (P3)

Shared rules: [SPEC-P3.md](SPEC-P3.md). Owner answers 2026-11-04: email for feedback, and more testers are needed.

## Objective
Testers can send feedback in two taps, and there are enough testers to meet Play's closed-test rule.

## Scope
- **Send feedback:** Settings > "Send feedback" opens an email app (`ACTION_SENDTO`, `mailto:`) with a subject "Bibi AI feedback <version>". The body is prefilled with the app version, phone model and Android version, plus three prompts: what you tried, what happened, what you expected. The address lives in one string resource (`feedback_address`): swdeveloper.sa@gmail.com (owner, 2026-11-04). If no email app is installed, a message says so and shows the address.
- **Tester invite (`docs/tester-invite.md`):** a short message with the opt-in link slot, a pointer to the install steps in docs/tester-guide.md and a weekly "what to try" list (week 1: chat, memory, a phone action; week 2: voice and floating Bibi). It asks testers to send the usage numbers on day 7 and day 14.
- **Recruiting plan (in the same doc):** the target is 12 testers opted in for 14 continuous days [VERIFY current Play rule]. Sources: people the owner knows, a LinkedIn call for testers (draft added to docs/linkedin/posts.md), and two spare testers in case some drop out. The doc also says what testers need: an Android 14+ phone with 4 GB RAM or more, and about 2 GB of free space.

## Acceptance criteria
1. A JVM test checks that the mailto URI and body hold the version, model and Android version, and no chat or memory text.
2. On the vivo: the email app opens prefilled; without an email app the fallback message shows.
3. The owner approves the tester invite and the LinkedIn call text.

## Out of scope
Google Forms, in-app bug upload, screenshots attached automatically.
