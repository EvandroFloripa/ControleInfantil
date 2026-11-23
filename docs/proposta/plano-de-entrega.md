# Plano de entrega — ajuste pós-auditoria

Os lotes abaixo são propostas do Projetista Ajuste. Nenhum está `LIVRE` ainda:
dependem de aprovação humana da proposta e, no lote de segurança, de revisão do
critério de aceite antes da execução.

## LOTE-01 — Hardening de renderização do painel

- **Objetivo:** eliminar XSS e montar dados remotos com saída contextual segura.
- **Arquivos/módulos:** `painel/index.html`; testes de navegador a adicionar.
- **Dependências:** nenhuma de banco; preservar a API atual.
- **Complexidade:** média. **Risco:** alto, por atingir todas as abas do painel.
- **Rollback/segurança:** alteração reversível por commit; validar CSP/headers e
  não alterar permissões para contornar o problema.
- **Aceite:** labels, categorias, nomes, detalhes de comando e localização são
  exibidos como texto; payloads com HTML/atributos não executam script; links de
  mapa aceitam apenas coordenadas numéricas.
- **Regressão:** login, seleção de aparelho, abas, comandos, pessoas, apps,
  histórico e localização continuam operacionais.
- **Status:** AGUARDANDO APROVAÇÃO; não `LIVRE`.

## LOTE-02 — Migrações versionadas e verificação de schema

- **Objetivo:** tornar a aplicação do backend reproduzível e detectar ordem/versão
  incorreta dos scripts atuais.
- **Arquivos/módulos:** `supabase/`; documentação de provisionamento; workflow/CLI
  a definir após escolher a ferramenta de migração.
- **Dependências:** decisão sobre ferramenta e acesso a um projeto Supabase de teste.
- **Complexidade:** média. **Risco:** alto, por envolver schema e políticas.
- **Rollback/segurança:** somente migrações aditivas e transacionais quando possível;
  backup e ensaio em projeto descartável antes de produção.
- **Aceite:** um ambiente novo e um ambiente existente podem ser atualizados com a
  mesma sequência; constraints, funções, grants e RLS ficam verificáveis.
- **Regressão:** todos os RPCs atuais e o pareamento continuam compatíveis.
- **Status:** AGUARDANDO DECISÃO DE INFRA; não `LIVRE`.

## LOTE-03 — Testes de segurança e contrato

- **Objetivo:** cobrir autorização, pareamento, expiração, comandos, RLS e painel.
- **Arquivos/módulos:** testes de Supabase/RPC, `painel/` e CI.
- **Dependências:** ambiente Supabase de teste; pode começar com fixtures locais
  para o painel.
- **Complexidade:** média. **Risco:** médio.
- **Rollback/segurança:** somente adição de testes e jobs; não altera produção.
- **Aceite:** CI executa testes reais e falha em violações de isolamento, XSS ou
  transições inválidas de comando; o relatório deixa de ser `NO-SOURCE`.
- **Regressão:** build Android e workflow do painel permanecem verdes.
- **Status:** AGUARDANDO APROVAÇÃO; não `LIVRE`.

## LOTE-04 — Validação física dos recursos sensíveis

- **Objetivo:** testar Device Owner, Acessibilidade, localização, reboot, WebRTC,
  captura de tela, áudio/vídeo, TURN e recuperação após reboot/rede ausente.
- **Arquivos/módulos:** protocolo de teste e atualizações de `README.md`/docs;
  código somente se uma falha reproduzível for encontrada.
- **Dependências:** aparelho resetável, conta Supabase de teste e TURN.
- **Complexidade:** alta. **Risco:** alto operacionalmente.
- **Rollback/segurança:** usar aparelho de teste; não testar em dispositivo com
  dados pessoais; nenhum deploy automático.
- **Aceite:** cada recurso recebe `VALIDADO`, `PARCIAL` ou `BLOQUEADO` com evidência
  de versão Android e condição de provisionamento.
- **Regressão:** não alterar a política explícita contra gravação oculta.
- **Status:** BLOQUEADO POR RECURSO EXTERNO; não `LIVRE`.

## Portões humanos pendentes

- Aprovar o escopo de hardening do painel.
- Escolher ferramenta e ambiente para migrações Supabase.
- Disponibilizar ambiente Supabase de teste e aparelho físico para LOTE-04.
- Fazer a inspeção visual e registrar o aceite ou as divergências de LOTE-05 e da Task 2 do LOTE-06 (desktop e viewport estreita).
- Aprovar qualquer mudança futura de retenção de localização/histórico ou permissões.

## Reforma visual profissional aprovada — fila coordenada

Os lotes abaixo pertencem ao plano aprovado em
`docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md` e
`docs/superpowers/plans/2026-11-21-reforma-visual-painel-desktop.md`. Eles não
alteram o status dos lotes de hardening, banco, testes de segurança ou validação
física acima.

### LOTE-05 — Shell, visão geral e comandos

- **Objetivo:** elevar a experiência desktop do responsável com shell profissional, visão geral derivada e comandos agrupados.
- **Arquivos/Módulos:** `painel/index.html`, `painel/ui-structure.test.mjs`, `painel/device-actions.test.mjs` se necessário.
- **Dependências:** design visual aprovado; nenhum backend novo.
- **Complexidade:** média. **Risco:** médio/alto por atingir a navegação principal.
- **Rollback/Segurança:** commit atômico e reversível; preservar alterações preexistentes; não alterar RPCs, RLS ou payloads.
- **Aceite:** shell desktop-first, visão geral com dados existentes, viewer read-only para viewers, comandos atuais operacionais e testes estruturais passando.
- **Regressão:** login, aparelhos, seleção, abas, comandos e contratos de `device-actions.js`.
- **Status:** CONCLUÍDO E REVISADO em 2026-11-21 (`5780fef`, `8005f54`); testes estruturais do painel passando. **ACEITE VISUAL PENDENTE** (inspeção humana em desktop e viewport estreita). Não libera trabalho de interface novo enquanto pendente.

### LOTE-06 — Estados, abas secundárias e acessibilidade

- **Objetivo:** uniformizar estados das abas, responsividade, foco, semântica e proteção do modal WebRTC.
- **Arquivos/Módulos:** `painel/index.html`, `painel/ui-structure.test.mjs`, `docs/divergencias.md` somente se necessário.
- **Dependências:** LOTE-05 concluído e revisado (cumprido), com aceite visual pendente.
- **Complexidade:** média. **Risco:** médio.
- **Rollback/Segurança:** commit atômico e reversível; não alterar chamadas de API ou permissões.
- **Aceite:** cinco estados legíveis, sem overflow horizontal, foco visível, reduced motion, viewer preservado e suíte do painel passando.
- **Regressão:** aplicativos, tempo, localização, histórico, pessoas e viewer.
- **Status:** PARCIAL. Task 2 commitada em `1af4aeb` (estados loading/empty/error/denied, avisos de observador, `aria-live`, `prefers-reduced-motion`); `node --test painel/*.test.mjs` 15/15. **Pendentes:** Task 3 (inspeção visual em desktop e viewport estreita, teclado, build Android) e aceite visual humano. Não concluído.

### LOTE-07 — Validação e documentação da reforma visual

- **Objetivo:** validar desktop, viewport estreita, teclado, estados, painel e build Android; registrar evidências.
- **Arquivos/Módulos:** `docs/superpowers/specs/2026-11-21-reforma-visual-painel-desktop-design.md`, `docs/execucao/estado-atual.md`, `docs/divergencias.md`.
- **Dependências:** LOTE-06 concluído.
- **Complexidade:** baixa/média. **Risco:** médio.
- **Rollback/Segurança:** somente documentação baseada em evidências; nenhuma alteração externa.
- **Aceite:** testes reais registrados, inspeção visual registrada e build Android sem regressão.
- **Regressão:** manter DV-01–DV-05 abertas quando não houver evidência nova.
- **Status:** BLOQUEADO. Depende de LOTE-06 concluído (hoje PARCIAL) e do aceite visual humano de LOTE-05 e LOTE-06. Não `LIVRE`.
