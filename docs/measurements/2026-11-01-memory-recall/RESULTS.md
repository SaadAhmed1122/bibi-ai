# P2 Task 9 (#80): memories in answers, vivo Y17s, 2026-11-01

**Setup:** Gemma 3 1B int4, Friendly preset. Every run starts from a cleared conversation (Clear chat), because the model copies earlier answers that are still in context. One saved memory per test.

## Wording of the turn note (memory "My sister is Sara", not pinned)

| Note format | "what is my sister called" | "who is Sara" | "do I have a sister" |
|---|---|---|---|
| None (no memory: baseline) | "Your sister was called **Lily**." (invented) | "Sara was Sara-Marie-Jones-Smith-Jones-…" (loop until the 256-token cap, #107) | — |
| `(You know this about me: "My sister is Sara.")` | "You're called Sara!" (who's who wrong) | "Sara was your sister!" ✓ | "Yes you do!" ✓ |
| `(Remember: Your sister is Sara.)` (kept: second person, fewer tokens) | "You're called Sara!" (wrong) | "Sara was your sister!" ✓ | "Yes you do!" ✓ |

The same note, other phrasings: "what is my sister name" → the note was found and sent, but Gemma brushed it off ("isn't really important right now"). "Tell me about my sister" → "Sara's an artist! …": it used the name, then invented details.

## Pinned memory ("My favourite colour is green", pinned)

| Where the note goes | "what is my favourite colour" | "what colour do I like" |
|---|---|---|
| System prompt only | "You can be green!" (garbled) | "You can be blue!" (wrong) |
| System prompt **and** the matching turn note (kept) | "Green! It's lovely – really calming and refreshing." ✓ | "You like blue!" (wrong) |

## Cost

- First word: 1.25–1.35 s with a note, the same as without; notes are capped at about 80 estimated tokens per turn.
- A conversation reopen with a pinned block: 8.3 s (≈ 7 s without).

## Verdict

- Memory works for direct questions.
- The 1B model sometimes mixes up who's who or adds made-up details.
- Notes next to the question work far better than notes in the system prompt.
- Without memory it invents names, and can loop (#107).
- Few prompts, so these are indications, not rates [VERIFY with a larger set].
