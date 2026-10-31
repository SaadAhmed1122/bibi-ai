# Spec: chat-history (P1)

Shared rules: [SPEC.md](SPEC.md).

## Objective
Store the single conversation on the device with Room so it survives app and process restarts. Success: every sent message and every finished, stopped or failed answer is saved and shown again in order after a restart.

## Scope
- `core:database`: `BibiDatabase` (Room, schema version 1, schema JSON exported to `core/database/schemas/`).
- Entity `MessageEntity(id: Long autoGenerate, role: USER|BIBI, text: String, createdAt: Long epoch ms, status: DONE|STOPPED|ERROR)`. There is one conversation in P1, so there is no conversation table.
- `MessageDao`: `observeAll(): Flow<List<MessageEntity>>` ordered by `createdAt, id`; `insert`; `update(id, text, status)`; `deleteAll()`.
- `ChatHistoryRepository` (domain-facing) maps entities to `domain:model.Message`.
- A partial Bibi answer is **not** written token by token. It is inserted once when the answer finishes, is stopped, or fails, which keeps write traffic low. While streaming, the text lives in UI state only.
- "Clear chat" deletes all messages. In the plan it also resets the model conversation (SPEC-inference.md).
- The database is plain, not encrypted (P1 assumption approved 2026-10-31). Encryption is a P2 decision together with memory.

## Acceptance criteria
1. DAO tests (in-memory Room): insert/observe ordering, update of status, deleteAll, and Flow emissions on change.
2. A repository test maps every role and status correctly.
3. The schema JSON for version 1 is committed. Later changes need a migration and an "Ask first" (SPEC.md Boundaries).
4. On the vivo: send two messages, force-stop the app, reopen it, and both turns are there in order.

## Out of scope
Memory extraction, search, export/delete UI beyond "Clear chat", and retention rules (P2, docs/features/memory.md).
