# Spec: chat (P1)

Shared rules: [SPEC.md](SPEC.md). Owner decision 2026-10-31: the in-app chat screen and Stop are in P1.

## Objective
The core loop: type a message, see Bibi's answer stream in, stop it if needed, and find it again later. It works as a normal app screen, with no overlay permission needed, and the same state powers the overlay chat card. Success: the success criteria 1, 2 and 4 in SPEC.md.

## Scope
- **`SendMessage`** (in `feature:chat`, because `InferenceClient` is in an Android module; Decision 2026-10-31):
  1. saves the user message;
  2. streams `InferenceClient.send`;
  3. on `Done`, `Stopped` or `Error`, saves Bibi's message with that status.

  Only one generation at a time: Send is disabled while generating. Empty or blank input is ignored, and input is capped at 2,000 characters.
- **`feature:chat`:**
  - `ChatViewModel` (MVI, `StateFlow<ChatUiState>`, intents Edit / Send / Stop / Clear).
  - `ChatScreen`: message list, input field, Send button that turns into Stop while generating, and the model state banner (Missing → "Import the model", Loading, Restarting, Error).
  - `ChatCard`: the compact version of the same content, used by the overlay. It shares the ViewModel logic through the same use cases.
- Short answers are handled by the system prompt and the token cap (SPEC-inference.md). The UI does not cut text.
- Accessibility: TalkBack labels on Send, Stop and messages. The list is announced politely while streaming.
- Strings live in resources.

## Acceptance criteria
1. `SendMessage` tests with `FakeInferenceClient` and a fake repository:
   - Done saves both messages;
   - Stop saves the partial answer as STOPPED;
   - Error saves the user message and an ERROR entry with a friendly text;
   - a second Send while generating is ignored.
2. `ChatViewModel` tests (Turbine): state sequence for send → tokens → done; Stop; Clear (clears history and resets the conversation); model Missing shows the import prompt.
3. Compose previews for: empty, conversation, generating, stopped, model missing, and restarting.
4. On the vivo, with the overlay permission denied: chat works end to end from the app.

## Out of scope
Voice input or output, markdown rendering beyond plain text, message editing, multiple conversations.

## Open questions
- None. The input cap is 2,000 characters (tasks/plan.md).
