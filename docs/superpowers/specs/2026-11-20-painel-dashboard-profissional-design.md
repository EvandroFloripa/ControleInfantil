# Design — Painel parental profissional

## Intenção

Transformar o painel web atual em um dashboard profissional para responsáveis,
com leitura rápida do estado do aparelho e navegação mais clara entre controle,
aplicativos, tempo de uso, localização, acesso e histórico.

## Abordagens consideradas

- **Dashboard profissional:** sidebar, cabeçalho contextual, cards de status e
  layout responsivo. Escolhido por melhorar a percepção de produto sem alterar
  os fluxos existentes.
- **Minimalista premium:** mais espaço e menos informação visível; descartado
  porque esconderia ações importantes do controle parental.
- **Visual familiar/infantil:** cores e elementos mais lúdicos; descartado para
  manter o painel orientado aos responsáveis, não às crianças.

## Escopo

- Atualizar a camada visual de `painel/index.html`.
- Criar tokens de cor, tipografia, espaçamento, bordas, sombras e estados.
- Reorganizar visualmente o cabeçalho, seleção de aparelho, abas e cartões já
  existentes, sem remover IDs usados pelo JavaScript.
- Melhorar responsividade para desktop, tablet e celular.
- Adicionar estados visuais coerentes para seleção, foco, carregamento, vazio,
  erro e sem permissão quando já representados pela interface atual.
- Preservar Supabase, autenticação, RPCs, WebRTC, textos funcionais e contratos
  do `device-actions.js`.

## Direção visual proposta

- Base clara em azul-marinho/grafite, com ciano/verde como sinal de segurança e
  ações primárias.
- Sidebar escura em telas largas; navegação continua horizontal e rolável em
  telas pequenas.
- Cabeçalho com saudação, aparelho ativo, indicador de conexão e ação principal.
- Cards com títulos curtos, valor/estado em destaque e apoio contextual.
- Uma textura de gradiente discreta no fundo e sombras suaves, sem aparência de
  template genérico.
- Tipografia sans-serif moderna com fallback local, alto contraste e foco visível.

## Wireframe estrutural

```text
┌─────────────────────────────────────────────────────────────────┐
│ marca Controle Infantil                 conta · sair            │
├───────────────┬─────────────────────────────────────────────────┤
│ Visão geral   │ Olá, responsável                [aparelho ativo]│
│ Comandos      │ proteção ativa · último contato                 │
│ Aplicativos   │ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐ │
│ Tempo de uso  │ │status  │ │tempo   │ │apps    │ │localização │ │
│ Localização   │ └────────┘ └────────┘ └────────┘ └────────────┘ │
│ Histórico     │                                                 │
│ Acesso        │ conteúdo da aba atual em cards existentes        │
└───────────────┴─────────────────────────────────────────────────┘
```

## Não objetivos

- Não alterar banco, autenticação, regras de permissão ou chamadas de API.
- Não redesenhar as telas Android.
- Não introduzir dependências externas ou framework novo.
- Não remover funcionalidades existentes do painel.

## Critérios de aceite

1. O painel mantém todos os IDs e eventos JavaScript atuais.
2. Login, seleção de aparelho e todas as abas continuam operacionais.
3. Desktop apresenta hierarquia de dashboard; celular não exige rolagem
   horizontal involuntária.
4. Foco de teclado, contraste e estados desabilitados permanecem legíveis.
5. A janela flutuante de vídeo continua funcionando sobre o painel.
6. O arquivo passa por validação estrutural e os testes existentes do painel
   continuam passando.

**ACEITE VISUAL:** aprovado pelo usuário em 2026-11-20.

## Validação do lote

- Testes do painel: `node --test painel/*.test.mjs` — 6 testes aprovados.
- `git diff --check` — sem erros de whitespace.
- Validação estrutural confirmou tokens, shell, responsividade, foco visível e
  preservação dos IDs funcionais.
- Inspeção visual em navegador: indisponível nesta sessão porque o host de
  browsers não estava disponível; revisão estática realizada.
