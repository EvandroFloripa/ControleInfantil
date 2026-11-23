# Reforma visual profissional do painel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar o painel do responsável em uma central de cuidado parental profissional, desktop-first, sem alterar os contratos funcionais existentes.

**Architecture:** Manter `painel/index.html` como aplicação vanilla única. A reforma reorganiza o shell, adiciona uma visão geral derivada dos dados já carregados e consolida componentes visuais/estados em CSS e JavaScript local; Supabase, RPCs, autenticação, WebRTC e IDs existentes permanecem compatíveis.

**Tech Stack:** HTML5 semântico, CSS vanilla, JavaScript vanilla, Node.js `node:test`, Gradle/Kotlin Android para regressão do projeto.

**Spec:** `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md`

## Global Constraints

- Desktop é o contexto principal; não introduzir rolagem horizontal involuntária em tablet/mobile.
- Preservar IDs, handlers, textos funcionais, RPCs, autenticação, RLS, WebRTC e contratos de `device-actions.js`.
- Não alterar schema, permissões, retenção de dados ou comportamento de captura nesta reforma.
- Não adicionar framework ou dependência externa.
- Viewers permanecem somente leitura; controles funcionais de guardian não podem aparecer como ativos para viewers.
- A implementação será dividida em duas etapas: (1) shell, visão geral e comandos; (2) aplicativos, tempo, localização, histórico e pessoas.

## Review Focus

- Viewer em aparelho selecionado como `viewer`: ações de comando, convite e configuração permanecem indisponíveis; testar no Task 1.
- Ausência de aparelhos ou aparelho selecionado sem dados opcionais: visão geral mostra estado vazio útil; testar no Task 1.
- Dados longos de apps, labels, comandos e localização: cards não quebram a grade nem criam overflow; testar no Task 2.
- Carregamento, erro e sem permissão em cada aba: feedback textual e ação de recuperação permanecem visíveis; testar no Task 2.
- Modal WebRTC minimizado/maximizado/encerrado sobre o novo shell: regras de posicionamento e eventos continuam operacionais; testar no Task 2.

## Mapa de arquivos

- `painel/index.html`: markup, estilos, navegação, visão geral e renderização existente; único arquivo de produto a modificar.
- `painel/ui-structure.test.mjs`: contratos estruturais do shell, visão geral, estados e responsividade.
- `painel/device-actions.test.mjs`: contratos funcionais de ações e permissões já existentes; ampliar apenas se necessário.
- `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md`: design aprovado e decisões fechadas.
- `docs/execucao/estado-atual.md`: registrar o resultado real da reforma após validação.
- `docs/divergencias.md`: registrar qualquer divergência encontrada, sem apagar histórico.

### Task 1: Shell profissional, visão geral e comandos

**Files:**
- Modify: `painel/index.html` — estrutura do shell, CSS tokens, visão geral e comandos.
- Test: `painel/ui-structure.test.mjs`.
- Test: `painel/device-actions.test.mjs` se uma regra de permissão precisar de cobertura adicional.

**Interfaces:**
- Consumes: `state.devices`, `state.roles`, `state.selected`, `showTab`, `selectDevice`, `sendCommand` e os IDs atuais.
- Produces: shell desktop-first; novo conjunto de IDs estáveis `overviewCard`, `overviewConnection`, `overviewLastSeen`, `overviewProtection`, `overviewUsage`, `overviewLocation` e `overviewAlerts`; visão geral derivada sem nova chamada de backend.

- [ ] **Step 1: Escrever testes estruturais que falhem para o novo contrato**

  Em `painel/ui-structure.test.mjs`, exigir os IDs da visão geral, um único `h1`, skip link, navegação semântica e tokens para sucesso, atenção, erro e foco.

- [ ] **Step 2: Executar os testes estruturais para confirmar a falha inicial**

  Run: `node --test painel/ui-structure.test.mjs`

  Expected: FAIL apenas nas asserções dos novos elementos/contratos.

- [ ] **Step 3: Implementar o shell desktop-first em `painel/index.html`**

  Organizar header, sidebar persistente e área principal sem remover `devicesCard`, `tabsBar`, `commandsCard` ou os demais IDs existentes. Preservar a navegação funcional atual e manter uma área de contexto para aparelho selecionado.

- [ ] **Step 4: Implementar a visão geral derivada dos dados existentes**

  Criar uma função local `renderOverview(device, role)` que atualize somente os IDs `overview*`, trate `null`/ausência como estado vazio e não invente métricas. Chamar após `selectDevice` e após `loadDevices`; `role` controla apenas a apresentação de ações autorizadas.

- [ ] **Step 5: Reorganizar visualmente os comandos sem alterar payloads**

  Agrupar os comandos existentes em proteção, dispositivo e comunicação; manter os handlers e os tipos enviados por `sendCommand`. Para viewer, esconder/desabilitar ações funcionais conforme `state.roles[state.selected] !== 'guardian'`, mantendo leitura do estado.

- [ ] **Step 6: Executar os testes da Task 1**

  Run: `node --test painel/ui-structure.test.mjs painel/device-actions.test.mjs`

  Expected: PASS, com todos os IDs existentes e novos contratos presentes.

### Task 2: Estados, abas secundárias, acessibilidade e responsividade

**Files:**
- Modify: `painel/index.html` — estados visuais, cards das abas, listas, modal e media queries.
- Modify: `painel/ui-structure.test.mjs` — contratos de estados e overflow.
- Modify: `docs/divergencias.md` apenas se a inspeção confirmar divergência nova.

**Interfaces:**
- Consumes: shell e tokens da Task 1; funções atuais `loadApps`, `loadStatus`, `loadLocation`, `loadPeople`, `loadUsage`, `loadHistory` e viewer.
- Produces: apresentação consistente dos cinco estados sem alterar as respostas dos serviços nem as permissões.

- [ ] **Step 1: Escrever testes estruturais para os estados e o viewer**

  Exigir classes/atributos ou regiões estáveis para carregando, vazio, erro e sem permissão; confirmar `viewerModal`, `viewerMin`, `viewerMax`, `viewerClose`, media queries e `prefers-reduced-motion`.

- [ ] **Step 2: Executar os testes para confirmar a falha inicial**

  Run: `node --test painel/ui-structure.test.mjs`

  Expected: FAIL somente nas novas asserções de estados/acessibilidade.

- [ ] **Step 3: Padronizar os estados das abas secundárias**

  Aplicar uma estrutura visual comum para aplicativos, tempo, localização, histórico e pessoas: título contextual, resumo, conteúdo, estado vazio/erro e ação de recuperação já suportada. Não alterar URLs, RPCs, payloads ou regras RLS.

- [ ] **Step 4: Refinar acessibilidade e responsividade**

  Garantir skip link, hierarquia de headings, labels associados, `aria-live` para toast/status, foco visível, contraste e navegação compacta em viewport estreita. Adicionar `prefers-reduced-motion` e limitar sombras/movimentos em telas pequenas.

- [ ] **Step 5: Proteger o modal WebRTC no novo layout**

  Preservar `viewerModal` sobre o dashboard, estados minimizado/maximizado e controles próprios do viewer. Não conceder ações de guardian a viewers.

- [ ] **Step 6: Executar os testes da Task 2**

  Run: `node --test painel/*.test.mjs`

  Expected: todos os testes PASS.

### Task 3: Validação visual e regressão do projeto

**Files:**
- Modify: `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md` — registrar validação real.
- Modify: `docs/execucao/estado-atual.md` — atualizar apenas evidências confirmadas.
- Modify: `docs/divergencias.md` — atualizar estados das divergências somente com evidência.

**Interfaces:**
- Consumes: painel concluído nas Tasks 1 e 2 e testes automatizados.
- Produces: evidência de aceitação desktop, viewport estreita, teclado, estados e regressão Android.

- [ ] **Step 1: Executar testes do painel**

  Run: `node --test painel/*.test.mjs`

  Expected: todos os testes PASS.

- [ ] **Step 2: Executar verificação de whitespace e contratos**

  Run: `git diff --check`

  Expected: nenhum erro de whitespace; IDs e handlers preservados conforme os testes.

- [ ] **Step 3: Fazer inspeção visual em desktop e viewport estreita**

  Verificar shell, densidade, hierarquia, seleção de aparelho, visão geral, viewer, estados vazios/erro, foco, teclado e ausência de overflow horizontal. Registrar o resultado no design doc.

- [ ] **Step 4: Executar regressão Android**

  Run: `./gradlew.bat test assembleDebug`

  Expected: build concluído; registrar `NO-SOURCE` se continuar sem testes unitários Android.

- [ ] **Step 5: Atualizar documentação com fatos observados**

  Marcar somente critérios realmente validados como concluídos; manter recursos físicos e divergências não testadas como parciais/abertas.

## Self-review do plano

- Todos os critérios do design doc têm uma tarefa correspondente.
- A visão geral usa somente dados já carregados e não exige alteração de backend.
- Os contratos de viewer, guardian e viewer foram explicitados nos testes e tarefas.
- Nenhuma tarefa descarta ou sobrescreve alterações preexistentes.
- A validação visual continua obrigatória; testes estruturais não substituem inspeção no navegador.

**STATUS: PLANO PRONTO PARA EXECUÇÃO APÓS ESCOLHA DO MÉTODO.**
