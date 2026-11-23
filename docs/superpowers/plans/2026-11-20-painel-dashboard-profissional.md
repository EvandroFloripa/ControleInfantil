# Painel Dashboard Profissional Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transformar o painel estático em um dashboard profissional e responsivo sem alterar sua lógica funcional.

**Architecture:** Manter `painel/index.html` como página vanilla única. A implementação será visual, usando tokens CSS, layout responsivo e pequenas melhorias semânticas; IDs, handlers, Supabase e WebRTC permanecem compatíveis.

**Tech Stack:** HTML5, CSS vanilla, JavaScript vanilla, Node.js para o teste existente do painel.

**Spec:** `docs/superpowers/specs/2026-11-20-painel-dashboard-profissional-design.md`

## Global Constraints

- Preservar Supabase, autenticação, RPCs, WebRTC, textos funcionais e contratos do `device-actions.js`.
- Não introduzir dependências externas ou framework novo.
- Não remover funcionalidades existentes do painel.
- A janela flutuante de vídeo deve continuar funcionando sobre o painel.
- Desktop deve apresentar hierarquia de dashboard; celular não deve exigir rolagem horizontal involuntária.

## Review Focus

- IDs usados pelo JavaScript: validação estrutural deve confirmar que os elementos funcionais continuam presentes.
- Navegação em viewport estreita: validação visual deve confirmar que sidebar vira navegação compacta/rolável sem overflow da página.
- Estados de foco e desabilitado: inspeção CSS deve confirmar contraste e foco visível.
- Modal de vídeo: validar que as regras de posicionamento e maximização continuam isoladas do novo layout.
- Conteúdo longo e listas vazias: confirmar que cards e listas não quebram a grade nem escondem mensagens existentes.

### Task 1: Tokens e shell do dashboard

**Files:**
- Modify: `painel/index.html` — bloco `<style>` e estrutura visual do `body`/`main`.

**Interfaces:**
- Consumes: IDs e classes existentes no HTML e seletores usados pelo JavaScript.
- Produces: tokens CSS, shell de dashboard, sidebar/navegação e área de conteúdo compatíveis com os cards atuais.

- [ ] **Step 1: Mapear os seletores funcionais existentes**

  Confirmar no HTML que cada ID referenciado por `painel/index.html` e `device-actions.js` será preservado durante a edição.

- [ ] **Step 2: Implementar os tokens visuais**

  Substituir a paleta compacta atual por variáveis para fundo, superfície, texto, texto auxiliar, borda, primária, sucesso, alerta, erro, raios, sombras e espaçamento.

- [ ] **Step 3: Implementar o shell profissional**

  Aplicar sidebar em desktop, cabeçalho contextual, área principal mais larga e navegação visualmente consistente com os botões de aba já existentes.

- [ ] **Step 4: Refinar cards e controles existentes**

  Melhorar hierarquia de títulos, agrupamentos, botões, badges, dispositivos e formulários sem mudar seus IDs, valores ou eventos.

### Task 2: Responsividade, acessibilidade e estados

**Files:**
- Modify: `painel/index.html` — media queries, estados de foco e regras de componentes.

**Interfaces:**
- Consumes: shell e tokens produzidos pela Task 1.
- Produces: layout adaptável, foco visível, estados de hover/active/disabled e tratamento visual dos estados existentes.

- [ ] **Step 1: Adicionar o breakpoint de tablet/celular**

  Em viewport estreita, converter a sidebar em navegação horizontal/compacta, reduzir espaçamentos e manter ações primárias acessíveis sem overflow horizontal involuntário.

- [ ] **Step 2: Adicionar estados de interação acessíveis**

  Definir `:focus-visible`, hover, active e disabled para botões, inputs, tabs e dispositivos, mantendo contraste suficiente.

- [ ] **Step 3: Proteger o viewer modal**

  Ajustar apenas o necessário para o modal flutuante continuar sobrepondo o dashboard, incluindo maximizado, minimizado e mobile.

- [ ] **Step 4: Revisar estados de conteúdo**

  Garantir legibilidade para vazio, erro, carregamento textual, sem permissão e mensagens de toast já produzidas pelo JavaScript.

### Task 3: Validação do lote visual

**Files:**
- Test: `painel/device-actions.test.mjs`
- Modify: `docs/superpowers/specs/2026-11-20-painel-dashboard-profissional-design.md` — registrar aceite e validação.

**Interfaces:**
- Consumes: painel final das Tasks 1 e 2.
- Produces: evidência de que os contratos funcionais e o design aprovado permanecem íntegros.

- [ ] **Step 1: Executar os testes existentes do painel**

  Run: `node --test painel/device-actions.test.mjs`

  Expected: todos os testes PASS.

- [ ] **Step 2: Fazer validação estrutural**

  Confirmar que os IDs usados pelo código permanecem presentes e que não há referências a classes removidas nos estilos do viewer.

- [ ] **Step 3: Fazer revisão visual responsiva**

  Inspecionar desktop e viewport estreita, verificando shell, abas, cards, foco, overflow e viewer modal.

- [ ] **Step 4: Registrar a conclusão**

  Atualizar o design doc para remover o marcador de aguardando aceite e registrar a validação executada.
