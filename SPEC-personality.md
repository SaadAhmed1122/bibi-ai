# Spec: personality (P2)

Shared rules: [SPEC-P2.md](SPEC-P2.md). PRD 0.8: two presets that change tone and the system prompt only.

## Objective
The user picks how Bibi talks: **Friendly** (warm, a little playful; the P1 prompt) or **Concise** (short, plain answers). The choice is kept across restarts.

## Scope
- Two system prompts as assets in `:inference` (`system_prompt_friendly.txt`, `system_prompt_concise.txt`); Friendly is the current `system_prompt.txt`.
- A new Messenger message `SET_PERSONA(id)`. The service stores the id and, if it differs, rebuilds the conversation with the new prompt and the carried turns (the P1 reset path; about 7 s on the vivo, the banner shows "Loading").
- The choice is saved in SharedPreferences "app" and sent each time the client connects.
- A Settings screen (Home → Settings) with the two options and one line describing each. The Privacy dashboard and Safe mode will live here later.

## Acceptance criteria
1. Protocol round-trip test for `SET_PERSONA`; service logic test: same id → no reopen, new id → one reopen.
2. ViewModel test: the setting is saved and sent on connect.
3. On the vivo: switching to Concise gives visibly shorter answers to the same three questions (recorded in the PR); the choice survives a force-stop. Both orientations.

## Out of scope
More than two presets, custom prompts, a voice per preset.
