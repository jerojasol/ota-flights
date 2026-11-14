---
description: Append an entry to docs/ai-log.md for the AI-assisted work just done
---

Append a new entry to `docs/ai-log.md` (repository root) documenting the AI-assisted work just completed in this session.

Today's date in the heading, then the fields `Intent`, `Prompt (summary)`, `Generated`, `Human review / corrections`, `Artifacts`, following the format defined at the top of the file.

Rules:
- Append at the end; never rewrite earlier entries.
- Be specific about what the human corrected or decided. "Accepted" is fine when true, but if the AI got something wrong first (wrong artifact name, wrong assumption) record it, because that is the useful part for `SOLUTION.md`.
- Keep `Artifacts` as relative paths from the repository root.
- Write in English.

Context from the user, if any: $ARGUMENTS
