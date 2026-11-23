# Relatório da Task 1 — LOTE-05

## Status

**CONCLUÍDO.** O LOTE-05 foi implementado e encerrado no papel de Executor do Framework de Projeto v5.3. Nenhum outro lote foi iniciado.

## Implementado

- Shell desktop-first preservado e completado com skip link, navegação semântica, foco visível e tokens de sucesso, atenção, erro e foco.
- Visão geral adicionada com os IDs contratuais `overviewCard`, `overviewConnection`, `overviewLastSeen`, `overviewProtection`, `overviewUsage`, `overviewLocation` e `overviewAlerts`.
- Função local `renderOverview(device, role)` implementada somente com dados já presentes em `state.devices`, `state.roles` e `state.selected`; nenhuma consulta, tabela, RPC, dependência ou payload foi adicionado.
- Visão geral atualizada depois de `loadDevices` e de `selectDevice`, incluindo estado vazio quando não há seleção ou dados opcionais.
- Comandos existentes agrupados visualmente em proteção, dispositivo e comunicação, preservando tipos, IDs, handlers e payloads.
- Permissões de viewer preservadas: comandos e cópia de configuração ficam desabilitados; controles dinâmicos de configuração de apps ficam desabilitados ou ocultos; controles próprios do viewer WebRTC permanecem intactos.
- Layout responsivo mantido sem overflow horizontal involuntário no documento.

## Arquivos alterados

- `painel/index.html`
- `painel/ui-structure.test.mjs`

Nenhum arquivo Android, Supabase, workflow ou migração foi alterado durante o lote.

## Validação

- RED: `node --test painel/ui-structure.test.mjs` — falha esperada, 2 passaram e 4 falharam pelos contratos ainda ausentes.
- GREEN focado: `node --test painel/ui-structure.test.mjs` — 6/6 passaram.
- Regressão final exigida: `node --test painel/ui-structure.test.mjs painel/device-actions.test.mjs` — 9/9 passaram, 0 falhas.
- Sintaxe do módulo inline: extração do `<script type="module">` seguida de `node --input-type=module --check` — OK.
- `git diff --check` — OK, código de saída 0.
- `git diff --cached --check` — OK, código de saída 0.
- Auto-revisão crítica: IDs e tipos de comando aparecem uma única vez; arquivos staged limitados aos dois arquivos do lote; nenhuma alteração nova de RPC ou payload foi introduzida pelo LOTE-05.
- Auditoria independente: DISPENSADA — lote classificado como risco médio/alto, não crítico, e o usuário proibiu acionar subagentes.

## Commit

- `5780fef` — `feat: profissionaliza painel desktop`

## Preocupações

- A inspeção visual em navegador, viewport estreita e navegação real por teclado pertence à Task 3 e não foi executada neste lote; os contratos atuais são estruturais e funcionais em Node.
- O hardening completo de XSS permanece fora do escopo, conforme restrição expressa do brief.
- `painel/index.html` já continha alterações preexistentes dentro do próprio arquivo permitido. Elas foram preservadas e integram o snapshot do commit; nenhuma alteração preexistente fora dos arquivos do lote foi staged ou commitada.
- A árvore continua com alterações preexistentes em Android, Supabase e outros arquivos não rastreados; elas permanecem intactas e fora do commit.

---

## Fix round 1 — revisão do LOTE-05

### Status

**CONCLUÍDO.** Os achados CRITICAL e IMPORTANT da primeira revisão foram corrigidos sem ampliar o escopo visual do lote.

### Correções aplicadas

- Removidos `type="module"`, o import de `./device-actions.js` e todas as referências dependentes; o painel voltou a inicializar como script clássico autocontido.
- Removidos o botão e o fluxo `copyConfiguration`, incluindo consultas adicionais e comandos/payloads de cópia.
- Removidos o botão e o fluxo `uninstallApp`, incluindo o comando/payload novo de desinstalação.
- Removido o controle `data-unpair` adicionado aos cards de aparelhos; o card de `viewer` não expõe desvinculação e não usa incorretamente `state.selected` para um card clicado.
- Preservados somente os seis tipos de comando já existentes: `lock_screen`, `reboot`, `enable_location`, `request_location`, `start_checkin` e `start_screen_view`.
- `painel/ui-structure.test.mjs` passou a executar o script inline em um harness mínimo com `node:vm`, confirmar a instalação real dos handlers, validar o payload original de `lock_screen` e renderizar um card de viewer sem controle de desvinculação.

### Arquivos do commit

- `painel/index.html`
- `painel/ui-structure.test.mjs`

### Comandos e resultados

1. RED após fortalecer os testes:

   `node --test painel/ui-structure.test.mjs`

   Saída resumida: `tests 8 | pass 6 | fail 2`; ambas as falhas ocorreram porque o painel ainda declarava `type="module"`.

2. GREEN focado após as remoções:

   `node --test painel/ui-structure.test.mjs`

   Saída resumida: `tests 8 | pass 8 | fail 0`.

3. Suíte de cobertura solicitada, executada novamente após o commit:

   `node --test painel/*.test.mjs`

   Saída resumida: `tests 11 | pass 11 | fail 0 | duration_ms 179.6494`.

4. Verificação de whitespace, executada novamente após o commit:

   `git diff --check`

   Saída: nenhum erro de whitespace; código de saída `0`. O Git exibiu somente avisos LF→CRLF sobre alterações preexistentes.

5. Conferência do escopo do commit:

   `git show --name-only --format='%H%n%s' 8005f54`

   Saída: somente `painel/index.html` e `painel/ui-structure.test.mjs`.

### Commit

- `8005f54d8896cf78a4e34a6587dd09babc1a54b3` — `fix: restringe reforma visual aos contratos existentes`

### Preocupações

- Concern MINOR registrado no ledger: `overflow-x: hidden` pode mascarar clipping; a validação visual em viewport continua obrigatória na Task 3.
- Os arquivos não rastreados preexistentes `painel/device-actions.js` e `painel/device-actions.test.mjs` permanecem intactos, mas o HTML e o commit do LOTE-05 não dependem deles.
- Hardening completo de XSS e inspeção visual em navegador continuam fora deste fix, conforme o brief.
