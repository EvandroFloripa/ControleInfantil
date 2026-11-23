# Estado atual — reforma visual do painel (para o Coordenador)

Data da atualização: 2026-11-23
Último registro de diagnóstico: 2026-11-20 (Projetista Ajuste, v5.3)
Branch: `claude/parental-control-app-5qy1gk`

## 1. Lotes do plano de entrega

Fonte: `docs/proposta/plano-de-entrega.md`. Status conforme o plano e a evidência abaixo.

| Lote | Status | Evidência |
| --- | --- | --- |
| LOTE-01 — Hardening de renderização do painel | AGUARDANDO APROVAÇÃO | não iniciado |
| LOTE-02 — Migrações versionadas e schema | AGUARDANDO DECISÃO DE INFRA | não iniciado |
| LOTE-03 — Testes de segurança e contrato | AGUARDANDO APROVAÇÃO | não iniciado |
| LOTE-04 — Validação física | BLOQUEADO POR RECURSO EXTERNO | depende de Supabase de teste e aparelho físico |
| LOTE-05 — Shell, visão geral e comandos | IMPLEMENTADO E REVISADO; ACEITE VISUAL PENDENTE | commits `5780fef` e `8005f54`; `node --test painel/*.test.mjs` 11/11 em 2026-11-21 |
| LOTE-06 — Estados, abas secundárias e acessibilidade | PARCIAL: Task 2 commitada em `1af4aeb`; Task 3 e aceite visual pendentes | ver seção 2 |
| LOTE-07 — Validação e documentação visual | BLOQUEADO por LOTE-06 e pelo aceite visual | não iniciado |

Regra: LOTE-06 não pode ser marcado como concluído nem liberado para implementação de interface sem o aceite visual humano do LOTE-05.

## 2. Trabalho da Task 2 (LOTE-06) — estado real

Plano: `docs/superpowers/plans/2026-11-21-reforma-visual-painel-desktop.md`. Spec aprovada: `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md`.

Implementada e commitada em `1af4aeb` (`painel/index.html` e `painel/ui-structure.test.mjs`):

- Testes estruturais e funcionais novos (`ui-structure.test.mjs`): `aria-live`/`role="status"` no toast e nos conteúdos das abas; estados `loading`, `empty`, `error`, `denied`; `prefers-reduced-motion`; aviso de somente leitura para observador em Aplicativos e Tempo de uso; estado de carregamento de Aplicativos.
- Estilos: `[data-state]` para estados de alerta com ícone e texto; `.permission-note`; bloco `prefers-reduced-motion`.
- Marcado: `#appsPermission`, `#timePermission`; `aria-live="polite"` em `appsList`, `timeUsed`, `locationInfo`, `peopleList`, `usageList`, `historyList`.
- Lógica: helper `setPanelState(el, kind, message)`; estados de carregamento, vazio e erro em Aplicativos, Tempo de uso, Localização, Pessoas, Histórico de apps e Comandos recentes; `selectDevice` alterna os avisos de observador.

Validação registrada:

- `node --test painel/*.test.mjs` — 15/15 passam (2026-11-23).
- `git diff --check` — sem erros de espaço em branco (apenas avisos LF→CRLF).
- **Não executado:** inspeção visual em navegador (desktop e viewport estreita), teste de teclado, e teste com sessão real do Supabase (sem credenciais nesta sessão).

Status: Task 2 **commitada (`1af4aeb`), testada estruturalmente; aceite visual pendente**. Não há revisão independente registrada para esta etapa.

## 3. Task 3 — pendente

- Inspeção visual em desktop e viewport estreita, com estados vazio, erro e sem permissão.
- Validação de teclado e foco visível.
- Regressão Android: `./gradlew.bat test assembleDebug` (não executado nesta etapa).
- Atualizar `docs/divergencias.md` apenas com evidência observada.

## 4. Decisões pendentes do usuário

1. **Commit da Task 2:** realizado em `1af4aeb`.
2. **Aceite visual** de LOTE-05 e da Task 2: depende de inspeção humana em navegador com conta real.
3. **Alterações Android e Supabase não relacionadas à reforma** (ver seção 5): precisam de dono e lote próprio antes de qualquer commit.

## 5. Alterações fora da reforma visual (preservadas, não tocadas)

Não pertencem a nenhum lote do plano de entrega e não foram revisadas nesta etapa:

- Modificados: `AndroidManifest.xml`, `AppBlockerService.kt`, `InstallAllow.kt`, `KioskManager.kt`, `remote/Command.kt`, `remote/CommandService.kt`, `accessibility_service_config.xml`, `supabase/apps.sql`, `supabase/signaling.sql`, `supabase/timerules.sql`.
- Não rastreados: `block/RemoteUninstaller.kt`, `block/UninstallResultReceiver.kt`, `painel/device-actions.js`, `painel/device-actions.test.mjs`, `docs/divergencias.md`, `docs/especificacao.md`, `dist/`, `.agents/`, `.superpowers/`, `.claude/`, `*.zip` na raiz.

`painel/device-actions.js` não é carregado pelo `index.html` atual (removido no fix do LOTE-05). Decidir se deve ser removido, versionado ou mantido fora.

## 6. Checkpoint do Coordenador — 2026-11-23

- Sincronização: estado acima conferido contra Git (`8005f54` é o último commit; `painel/` tem apenas alterações da Task 2, não commitadas).
- Fila: nenhum lote `LIVRE` elegível. LOTE-05 está concluído e revisado, mas com aceite visual pendente. LOTE-06 depende dele e o plano não o libera para implementação de interface antes desse aceite. LOTE-01, 02, 03, 04 e 07 seguem bloqueados ou aguardando decisão humana.
- Classificação de LOTE-06: **PARCIAL** (implementação e testes estruturais feitos; Task 3 e aceite visual pendentes). Não fechado.
- Parada: backlog liberado esgotado; decisão humana obrigatória (aceite visual, decisão de infra, aprovações de LOTE-01 e LOTE-03). Nenhum lote novo iniciado.
- Commit da Task 2: realizado em `1af4aeb`, após autorização do usuário.

## 7. Próximo passo recomendado para o Coordenador

1. Task 2 commitada (`1af4aeb`, testes 15/15). Próximo: inspeção visual e Task 3.
2. Realizar a inspeção visual da Task 3 com conta real e registrar o resultado no próprio documento, sem marcar aceite por conta própria.
3. Só depois do aceite visual de LOTE-05 e LOTE-06, liberar LOTE-07.
4. Tratar as alterações da seção 5 em lote separado, com dono definido.

## 8. Inspeção visual — 2026-11-23 (parcial, sem aceite)

Método: `painel/` servido em `127.0.0.1` e aberto no Chrome; servidor e aba encerrados ao final.

- **Login:** tela de entrada renderiza corretamente em desktop. Sem credenciais de responsável, as abas não foram vistas com dados reais.
- **Desktop (simulação de DOM com dados de exemplo):** `h1` único; toast com `role="status"`; visão geral e grade de comandos sem rolagem horizontal.
- **Observador (`selectDevice` com papel `viewer`):** comandos desabilitados; avisos "Somente leitura" em Aplicativos e Tempo de uso visíveis.
- **Estados de erro reais:** sem API no servidor local, Aplicativos e Tempo de uso exibiram "Não foi possível carregar …: HTTP 404" com o texto do erro.
- **Celular (iframe 390 px):** sem rolagem horizontal na página (`scrollWidth` 374 em viewport de 389).
- **Ponto de atenção para avaliação humana:** no celular, a barra de abas rola horizontalmente dentro da lateral; "Localização", "Histórico" e "Pessoas" ficam fora da tela sem indicação clara.
- **Não verificado:** teclado, contraste sistemático, dados reais de aparelho, localização, histórico e pessoas.

Conclusão: evidência parcial. **Aceite visual de LOTE-05 e da Task 2 do LOTE-06 continua pendente** de inspeção humana com conta real. Regressão Android (Task 3) não executada.

## 9. Limitações da evidência

- Sem execução contra Supabase real nesta sessão.
- Sem teste em aparelho Android físico (Device Owner, Acessibilidade, localização, WebRTC, TURN, reboot).
- Deploy externo do painel não validado.
