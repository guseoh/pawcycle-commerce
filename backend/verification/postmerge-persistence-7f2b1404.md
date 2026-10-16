# Temporary post-merge Backend/MySQL CI evidence

- Baseline `main`: `7f2b1404b64f3e0aa1b532dfe4ce7d686bf024e6`
- Purpose: trigger existing pull-request Backend/MySQL validation on the exact combined main production and test code after PRs #387, #388 and #389.
- This markdown-only file changes no Java source, build configuration, schema, migration, runtime behavior, or test definitions.
- Validation-only Draft PR. **Do not merge.** Close after collecting CI evidence.
- Repository validation classifier recognizes `backend/**` as Backend, invoking the complete disposable MySQL suite and mandatory T09/T10 physical-lock gates.
