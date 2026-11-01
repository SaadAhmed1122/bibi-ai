# Spec: design (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). Owner request, 2026-10-31: a design system, navigation and a screen-by-screen restyle, using every UI/UX tool available, for a modern look that doesn't read as AI-made.

## Objective
Bibi looks like one designed product, not Material defaults: warm, calm and personal, built around the character (orange tabby, cream, violet collar; [docs/design/bibi-character-gemini.md](docs/design/bibi-character-gemini.md)). Closed testers (P3) see this version.

"Not AI-looking" means, in testable terms: no purple-to-blue gradients, no glassmorphism, no sparkle/✨ or robot icons, no generic chatbot bubbles with avatars on every line, no centred-everything hero layouts, no stock "AI assistant" copy. Bibi's personality comes from the character art, the palette and the writing.

## Scope
- **Direction first (Task 20 starts with it):** two or three mood directions with palette, type pair and a mock of Home and Chat, shown to the owner as an artifact before any code. The owner picks one.
- **Design system (`core/designsystem`):** colour tokens light and dark (from the character palette, contrast checked to WCAG AA), a type scale (one display face plus one text face, bundled; licences recorded), spacing and shape scales, elevation, motion durations, and shared components: buttons, list rows, section headers, switches row, cards, chat bubbles, input bar, banners, empty and error states, the confirmation card, dialogs.
- **Navigation:** a clear structure (for example Chat as the main surface; Memory, Settings and Privacy one tap away), predictive Back, edge-to-edge, state kept on rotation. Debug screens (Crash log, LLM check) only in debug builds.
- **Restyle (Task 21):** every screen, in order: onboarding, Home/Chat, chat card over other apps, model and voice pack import, confirmation card, Memory, Settings, Privacy dashboard, Licenses and policy. Each with previews in light, dark and large font.
- **Tools, each used where it fits (CLAUDE.md §9, docs/tooling.md):**
  - **ui-ux-pro-max** and **impeccable** plugins: palettes, type pairs, patterns, then an audit/polish pass of each screen. They are web-oriented: ideas and critique only, built in Compose.
  - **21st MCP:** inspiration search for components (needs `API_KEY_21ST`, not set on 2026-10-29 [VERIFY]); never paste web code.
  - **agent-skills `frontend-ui-engineering`**, `code-review-and-quality` and `/review` on each PR.
  - **Context7:** Compose BOM 2026.06.01 and Material 3 APIs before code.
  - **Claude artifacts:** the direction mock-ups and a before/after page for the owner.
  - If a tool is unavailable, the session summary says so and the work continues with official Material 3 and Android design guidance.

## Acceptance criteria
1. The owner picks a direction from the mock-ups (recorded in Decisions).
2. No hard-coded colours, sizes or text styles outside `core/designsystem` (a lint-style check or grep in the PR).
3. Every screen has light, dark and 200 % font previews; contrast AA on text; touch targets ≥ 48 dp; TalkBack labels unchanged or better.
4. On the vivo, portrait and landscape: before/after screenshots of every screen in the PR and in the LinkedIn log.
5. The "not AI-looking" list above is checked in review.

## Known layout issues to fix in the restyle
- Memory screen in landscape: fixed in Task 21 (one scrolling list; the header scrolls away).
- Floating keyboard in landscape covers the chat card's Send button (the keyboard's own send key works). Not changed: the keyboard is the vivo's floating mode [OPEN].

## Out of scope
A new character design or new art (the four states are fixed), animations beyond Material motion and Bibi's frames, tablet layouts, localisation.
