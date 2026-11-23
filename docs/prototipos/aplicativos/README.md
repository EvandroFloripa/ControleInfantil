# Protótipo — Seção Aplicativos do painel

**Status:** PROPOSTA VISUAL — AGUARDANDO ACEITE. Não aprovado. Não implementado em `painel/index.html`.

## Como abrir

Abra `index.html` no navegador, ou sirva a pasta localmente (`python -m http.server 8766` dentro desta pasta) e acesse http://127.0.0.1:8766/index.html.

Os dados são fictícios (`com.exemplo.*`). A barra no topo alterna os estados: Padrão, Carregando, Vazio, Erro e Sem permissão.

## O que muda em relação ao painel atual

- Cabeçalho com resumo (liberados, total e progresso).
- Busca e filtro por categoria em chips.
- Categorias como cartões recolhíveis (abrem por padrão), com contador "X de Y liberados" e ação "Liberar todos / Bloquear todos" (o botão não recolhe a seção).
- Cada app com ícone de iniciais, nome e pacote em hierarquia clara.
- Interruptor (`role="switch"`, alvo de 44 px) no lugar da caixa de seleção.
- Barra fixa "N alterações não salvas" com Descartar e Salvar, só quando há mudança pendente.
- Instalação em cartão próprio e recolhível, com explicação dos dois modos em "Como funciona cada opção".
- Estados Carregando (esqueleto), Vazio, Erro com nova tentativa e Sem permissão com aviso.

## Validação feita

- Renderização no Chrome em desktop (1512 px).
- Recolher e reabrir categorias e o cartão de instalação, com clique real; "Liberar todos" não recolhe a seção.
- Alternância de interruptor, barra de salvar e contadores.
- Estados Erro e Sem permissão acionados pela barra.
- Celular (390 px, iframe): sem rolagem horizontal (`scrollWidth` 375 em 390).

## Pendências

- Aceite visual humano. Só o responsável muda o status para APROVADO.
- Teclado e contraste sistemáticos não foram auditados.
- Não há `FIDELIDADE.md` nem `docs/inventario-de-telas.md` no projeto; os tokens usados são os de `painel/index.html`.
- Implementação em `painel/index.html` depende do aceite e segue como lote separado, com testes estruturais.

## Regras preservadas

Nenhuma ação, payload ou RPC muda. Viewer continua somente leitura. Os comandos `set_allowed_apps` e `install_app` seguem os mesmos contratos.
