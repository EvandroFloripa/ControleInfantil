# Pesquisa de aplicativos na Play Store Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Adicionar ao painel um atalho para pesquisar aplicativos na Play Store sem alterar o fluxo atual de instalação.

**Architecture:** O navegador abrirá a busca oficial da Play Store em nova aba usando o texto do campo. O responsável continuará colando o link da página ou o ID do pacote no campo existente; nenhum dado novo será enviado ao Supabase.

**Tech Stack:** HTML, CSS inline existente e JavaScript vanilla em `painel/index.html`.

**Spec:** `docs/superpowers/specs/2026-11-20-pesquisa-play-store-design.md`

## Global Constraints

- Alterar somente `painel/index.html`.
- Usar `encodeURIComponent` na consulta.
- Não fazer scraping, login Google ou alteração de RPC/schema.
- Preservar a aceitação existente de ID de pacote e URL da Play Store.

## Review Focus

- Texto vazio: mostrar orientação e não abrir aba.
- Espaços e acentos: preservar a consulta via URL codificada.
- Bloqueador de pop-up: abrir diretamente no clique do usuário.
- Campo de instalação: continuar aceitando package ID e link da Play.
- Segurança: não interpolar texto bruto em HTML; construir a URL com `URLSearchParams` ou codificação equivalente.

### Task 1: Atalho de pesquisa da Play Store

**Files:**
- Modify: `painel/index.html` — seção Aplicativos e handlers de instalação.

**Interfaces:**
- Consumes: valor textual de `#installInput`.
- Produces: abertura de `https://play.google.com/store/search?q=<query>&c=apps` em nova aba.

- [ ] **Step 1: Add the search button and helper copy**

Adicionar o botão `#btnSearchPlay` ao lado do campo de instalação e explicar que
o responsável deve selecionar o app na nova aba e colar o link ou ID no campo.

- [ ] **Step 2: Implement the click handler**

No handler de `#btnSearchPlay`, rejeitar texto vazio com `toast`, construir a URL
com `URLSearchParams({ q: raw, c: 'apps' })` e chamar `window.open(url, '_blank', 'noopener')`.

- [ ] **Step 3: Verify the static behavior**

Confirmar por inspeção que o botão usa o valor atual do campo, que a URL não
interpola texto sem codificação e que o handler existente de `#btnInstallApp` não
foi alterado.

- [ ] **Step 4: Run validation**

Executar `git diff --check` e revisar o diff de `painel/index.html`. Como o
repositório não possui suíte JavaScript configurada, registrar a ausência de teste
automatizado; validar o build Android somente se a edição afetar arquivos Android
(não afeta).

- [ ] **Step 5: Commit**

```bash
git add painel/index.html docs/superpowers/specs/2026-11-20-pesquisa-play-store-design.md docs/superpowers/plans/2026-11-20-pesquisa-play-store.md
git commit -m "feat: adiciona busca da Play Store ao painel"
```
