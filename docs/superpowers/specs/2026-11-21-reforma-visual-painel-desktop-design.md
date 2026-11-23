# Design — Reforma visual profissional do painel (desktop-first)

## Intenção

Transformar o painel do responsável em uma central profissional de cuidado
parental: rápida para operar, clara sobre o estado do aparelho e confiável em
ações sensíveis. A reforma visual será feita antes da expansão funcional,
preservando os contratos atuais do Android, Supabase, autenticação, comandos e
WebRTC.

## Usuário e sucesso

O usuário principal é o responsável que administra um ou mais aparelhos de
crianças pequenas, normalmente em computador. O sucesso será medido por:

- localizar rapidamente o aparelho e seu estado de conexão;
- executar comandos importantes com confirmação e feedback inequívocos;
- entender tempo de uso, localização, histórico e permissões sem investigar a
  página;
- operar com teclado, contraste adequado e sem perder contexto entre abas;
- manter todos os fluxos atuais funcionando.

## Abordagens consideradas

1. **Reforma visual desktop-first — escolhida:** manter `painel/index.html`
   vanilla, reorganizar hierarquia, navegação e estados usando o design system
   existente. Entrega maior percepção de produto com baixo risco técnico.
2. **Mobile-first:** priorizaria ações grandes e navegação compacta. Não foi
   escolhida porque o uso principal será no computador e exigiria sacrificar
   densidade operacional.
3. **Reescrita em framework:** permitiria componentes mais estruturados, mas
   ampliaria o risco, introduziria dependências e não é necessária para esta
   etapa.

## Direção visual

Direção: **central de cuidado**. Base em azul-marinho e grafite para confiança,
verde-petróleo para proteção e ações positivas, âmbar para atenção e vermelho
somente para ações destrutivas. Superfícies claras, bordas discretas, tipografia
sans-serif legível e uma assinatura visual sutil de proteção; sem estética
infantilizada ou aparência de vigilância.

O desktop terá sidebar persistente, cabeçalho contextual, aparelho selecionado
sempre visível e conteúdo em grade. A interface deverá privilegiar leitura em
varredura: status, última comunicação, ações principais e alertas aparecem antes
dos detalhes.

## Estrutura proposta

```text
┌ marca ─────────────────────────────── conta / sair ┐
├ navegação ───────┬─────────────────────────────────┤
│ Visão geral      │ aparelho ativo · conexão        │
│ Comandos         │ status + última comunicação     │
│ Aplicativos      │ ações rápidas                   │
│ Tempo de uso     │ métricas / alertas / conteúdo   │
│ Localização      │                                 │
│ Histórico        │                                 │
│ Pessoas          │                                 │
└──────────────────┴─────────────────────────────────┘
```

### Visão geral

Adicionar uma visão resumida sem remover as abas existentes: conexão, último
contato, proteção, tempo usado, localização recente e alertas. As ações mais
importantes continuam disponíveis em comandos, com atalhos contextuais apenas
quando não duplicarem comportamento.

### Comandos

Agrupar comandos por intenção: proteção, dispositivo e comunicação. Ações de
risco alto devem exibir contexto, estado de execução e resultado. O modal de
vídeo permanece flutuante e independente do layout.

### Aplicativos e tempo de uso

Dar prioridade a busca, resumo de configuração e estados de sincronização. Listas
longas devem ter hierarquia, categorias e vazio útil, sem esconder ações.

### Localização, histórico e pessoas

Apresentar o dado principal primeiro, com data/hora, fonte e permissões visíveis.
Viewers devem receber uma interface de leitura, sem controles que pareçam ativos.

## Estados obrigatórios

Cada tela alterada deve mapear `Padrão`, `Carregando`, `Vazio`, `Erro` e
`Sem permissão`. Mensagens devem explicar o que aconteceu e o próximo passo;
nenhum estado pode depender apenas de cor. Ações destrutivas exigem confirmação
explícita e feedback de sucesso, falha ou expiração.

## Restrições e não objetivos

- Preservar IDs, handlers, textos funcionais, RPCs, autenticação, RLS, WebRTC e
  contratos de `device-actions.js`.
- Não alterar schema, permissões, retenção de dados ou comportamento de captura
  nesta reforma visual.
- Não adicionar framework ou dependência externa.
- Não remover funcionalidades existentes nem esconder controles de viewers.
- Corrigir problemas de semântica, acessibilidade e renderização segura somente
  quando forem necessários para a reforma; hardening completo de XSS permanece
  lote separado.

## Critérios de aceite

1. Desktop apresenta hierarquia clara de dashboard e mantém densidade útil.
2. Todos os IDs e eventos atuais continuam presentes e funcionais.
3. Login, seleção de aparelho, abas, comandos, listas, histórico, pessoas e
   viewer continuam operacionais.
4. Estados padrão, carregando, vazio, erro e sem permissão são legíveis e
   consistentes.
5. Teclado, foco visível, contraste e ordem semântica atendem ao mínimo AA.
6. Mobile e tablet não produzem overflow horizontal involuntário.
7. Testes estruturais e funcionais do painel passam; build Android permanece
   verde.
8. A inspeção visual em navegador registra desktop e viewport estreita.

## Decisões

- **DEC-04 (PADRÃO ADOTADO):** desktop-first por ser o contexto principal de uso.
- **DEC-05 (PADRÃO ADOTADO):** visão geral resumida complementa, mas não substitui,
  as abas funcionais existentes.
- **DEC-06 (PADRÃO ADOTADO):** reforma em HTML/CSS/JS vanilla para reduzir risco e
  preservar a infraestrutura atual.

## Pendências para o construtor

- Definir a composição final dos cards a partir dos dados já disponíveis, sem
  inventar novas métricas de backend.
- Identificar e preservar todos os seletores funcionais antes da edição.
- Registrar qualquer divergência entre o wireframe e o HTML atual em
  `docs/divergencias.md`.

## Decisões de revisão

- Viewers permanecem somente leitura. Os únicos controles visíveis para eles são
  os controles do próprio visualizador WebRTC (minimizar, maximizar e encerrar a
  visualização); comandos, convites e configurações continuam indisponíveis.
- A implementação será dividida em duas etapas: (1) shell, visão geral e
  comandos; (2) aplicativos, tempo, localização, histórico e pessoas.

**STATUS: APROVADO PELO USUÁRIO EM 2026-11-21 — PRONTO PARA PLANEJAMENTO.**
