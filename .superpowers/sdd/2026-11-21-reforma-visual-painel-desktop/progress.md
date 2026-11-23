# SDD ledger — plan: docs/superpowers/plans/2026-11-21-reforma-visual-painel-desktop.md

Workspace script: blocked by Windows `E_ACCESSDENIED`; workspace created manually. No product code was changed by this fallback.

Pre-flight scan:

| Item | Relationship checked | Finding | Ruling |
|---|---|---|---|
| Task 1 ↔ Task 2 | `painel/index.html`; Task 1 produces shell/tokens consumed by Task 2 states/responsiveness | Compatible; Task 2 must preserve Task 1 IDs and selectors | Task 2 consumes Task 1 contracts and may refine only within the approved visual scope |
| Task 1 ↔ Task 3 | `painel/ui-structure.test.mjs`; Task 1 adds contracts consumed by final validation | Compatible; Task 3 runs the complete suite after both implementation tasks | Keep structural tests deterministic and local |
| Task 2 ↔ Task 3 | `painel/index.html`, tests and docs | Compatible; Task 3 records evidence only after focused tests and visual inspection | Do not mark visual acceptance from static tests alone |
| Task 1 self-consistency | New `overview*` IDs, `renderOverview(device, role)`, tests and files | Consistent; function is local to `painel/index.html` and uses existing state | Implement exactly as specified; no backend additions |
| Task 2 self-consistency | Five states, viewer controls, media queries and tests | Consistent; all targets exist in `painel/index.html` | Preserve viewer behavior and guardian/viewer permission boundaries |
| Task 3 self-consistency | Docs, tests, build and validation | Consistent; no product behavior is introduced | Record only commands actually executed |

Rulings: no plan conflicts found. The existing uncommitted changes are preexisting and must not be discarded or reformatted outside the task write set.

Task 1 review round 1 — Deferred minor: `overflow-x: hidden` can mask clipping; visual viewport validation remains required in Task 3.

## Todos

- [ ] Task 1 — shell, visão geral e comandos
- [ ] Task 2 — estados, abas secundárias, acessibilidade e responsividade
- [ ] Task 3 — validação visual e regressão

Task 1: fix round 1 (3 addressed, 1 deferred minor — overflow-x:hidden can mask clipping; visual viewport validation remains Task 3; commits 5780fef..8005f54).

