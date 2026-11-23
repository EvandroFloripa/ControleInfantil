# Task 1 brief — Shell, visão geral e comandos

Leia primeiro:
- `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md`
- `docs/superpowers/plans/2026-11-21-reforma-visual-painel-desktop.md`
- `F:/Projetos/ControleInfantil/.superpowers/sdd/2026-11-21-reforma-visual-painel-desktop/progress.md`

## Escopo

Implementar somente o LOTE-05: shell profissional desktop-first, visão geral derivada dos dados já carregados e agrupamento visual dos comandos.

## Arquivos permitidos

- `painel/index.html`
- `painel/ui-structure.test.mjs`
- `painel/device-actions.test.mjs` somente se uma regra nova de permissão precisar de teste

## Contratos obrigatórios

- Preservar todos os IDs e handlers existentes, especialmente `devicesCard`, `tabsBar`, `commandsCard`, `viewerModal` e os IDs usados por `device-actions.js`.
- Criar os IDs `overviewCard`, `overviewConnection`, `overviewLastSeen`, `overviewProtection`, `overviewUsage`, `overviewLocation`, `overviewAlerts`.
- Criar função local `renderOverview(device, role)` em `painel/index.html`, usando somente dados já disponíveis em `state.devices`, `state.roles` e `state.selected`.
- Chamar `renderOverview` após seleção do aparelho e após atualização da lista de aparelhos.
- Não adicionar chamadas ao backend, tabelas, RPCs, dependências ou payloads novos.
- Viewers permanecem somente leitura: não enviar comandos, não convidar pessoas e não alterar configurações. Os controles próprios do viewer WebRTC continuam permitidos.
- Comandos existentes devem manter tipos, IDs, handlers e payloads.
- Desktop deve ser o layout principal e telas estreitas não podem criar overflow horizontal involuntário.

## Validação

Executar:

`node --test painel/ui-structure.test.mjs painel/device-actions.test.mjs`

O resultado esperado é PASS. Também executar `git diff --check` antes do commit.

## Restrições

- Não tocar em Android, Supabase, workflows, migrações ou arquivos fora da lista.
- Não descartar nem reformatar alterações preexistentes.
- Não implementar hardening completo de XSS neste lote; registrar qualquer observação sem expandir o escopo.

## Relatório

Escrever o relatório completo em:
`F:/Projetos/ControleInfantil/.superpowers/sdd/2026-11-21-reforma-visual-painel-desktop/task-1-report.md`

O relatório deve conter status, arquivos alterados, testes executados, commit e preocupações. Retornar na conversa apenas status, commit, resumo de testes e concerns.
