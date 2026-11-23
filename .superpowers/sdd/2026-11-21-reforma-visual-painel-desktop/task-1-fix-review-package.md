# Fix review package — Task 1 round 1

The review-package helper was unavailable because the Windows bash service was
denied. Review the exact fix range below read-only.

- Fix base: `5780fef2303e09f23b3be4d29418b18299ffda4e`
- Head: `8005f54`
- Changed files: `painel/index.html`, `painel/ui-structure.test.mjs`
- Report: `task-1-report.md` (Fix round 1 section appended)

The fix removes the uncommitted module import and out-of-scope controls/commands,
and adds structural/runtime harness assertions. Do not review unrelated code.
