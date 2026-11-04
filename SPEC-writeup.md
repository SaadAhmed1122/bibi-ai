# Spec: writeup (P4)

Shared rules: [SPEC-P3.md](SPEC-P3.md). PRD 0.6 P4: write-up published. Owner answer 2026-11-04: a LinkedIn article, and the repository made public.

## Objective
A public account of what was built, what was measured and what didn't work, plus a public repository that is safe to show.

## Scope
- **Repository audit before going public:**
  - secrets in the working tree and the full git history: `.env`, keystores, `local.properties`, tokens and keys;
  - personal data: tester data, phone logs with personal content, the owner's chat history used in bug reports;
  - large binaries and model files;
  - licenses of the bundled assets.
  Findings are fixed by removing them in a commit. If history has to be rewritten, that is an owner decision first (CLAUDE.md §10). The owner flips the visibility.
- **Public README:** what Bibi is, a screenshot, the architecture diagram, how to build, how to get the model, and the license (license choice [OPEN], owner).
- **LinkedIn article:** problem, approach, measured results (S1–S4, P2 gate, P3 numbers), what didn't work (for example Qwen, and Gemma's 0/100 tool calls), the privacy design and the next steps. Built from docs/linkedin/posts.md and the RESULTS files. It uses real numbers only; unmeasured ones are left out.
- **Portfolio package:** a case-study page, a demo video script and storyboard, screenshots with no personal data, and the architecture diagram.

## Acceptance criteria
1. Audit report in `docs/public-release-audit.md` with every finding resolved or accepted by the owner.
2. Every number in the article traces to a file in the repository.
3. The owner edits and publishes; the dated publish goes in Decisions.

## Out of scope
Paid promotion, a website build, store graphics beyond the Play listing.
