# Spec: onboarding (P1)

Shared rules: [SPEC.md](SPEC.md). PRD 0.9: ask for the overlay permission only when the user turns on the floating character.

## Objective
A short first run that gets a new user to a working chat. Success: on a clean install, a user who has the model file reaches a working chat in four steps or fewer and is never asked for a permission before they need it.

## Scope
- `feature:onboarding`, shown while onboarding isn't complete (a flag in DataStore or SharedPreferences).
  1. **Welcome:** what Bibi is. Says that it runs offline, sends nothing, and stores chats only on this phone.
  2. **Model:** explains the side-load step and starts the import (SPEC-model-import.md). It can be skipped; the chat then shows "Import the model".
  3. **Notifications:** asks for `POST_NOTIFICATIONS`, explaining that it is needed for the floating Bibi's notification. Denying is fine.
- **Floating Bibi:** a switch on Home and in settings. Turning it on, when the overlay permission isn't granted, shows an explainer and opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` for this package. On return, if granted, the overlay service starts; if not, the switch stays off and chat still works in the app.
- The Gemma terms link is shown on the Model step (app-shell).

## Acceptance criteria
1. An `OnboardingViewModel` test covers the step sequence, skip model, notifications denied, and the completed flag being saved.
2. A `FloatingToggle` logic test: permission missing → request flow; granted → start the service; denied → the switch stays off.
3. On the vivo, clean install: no permission dialog before the Notifications step; the overlay permission is asked only after the switch is turned on; chat works with both denied.

## Out of scope
Persona setup, voice setup, memory consent (P2), and a tutorial.
