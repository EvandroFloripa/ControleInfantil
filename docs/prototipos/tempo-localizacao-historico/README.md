# Protótipo — Tempo de uso, Localização e Histórico

**Status:** PROPOSTA VISUAL — AGUARDANDO ACEITE. Não aprovado. Não implementado em `painel/index.html`.

## Como abrir

Abra `index.html` no navegador, ou sirva a pasta localmente (`python -m http.server 8767` dentro desta pasta) e acesse http://127.0.0.1:8767/index.html.

Dados fictícios. A barra no topo alterna os estados (Padrão, Carregando, Vazio, Erro, Observador) e o observador vê os controles desabilitados com aviso de somente leitura.

## O que muda em relação ao painel atual

**Tempo de uso**
- Resumo com minutos usados, limite do dia, barra de progresso e aviso visual a partir de 80%.
- Regras do dia em cartão próprio, com interruptor acessível (`role="switch"`) e campos de horário e limite.
- Tempo extra em botões claros; a liberação é imediata e não passa pela barra de salvar.
- Barra fixa "Alterações não salvas" com Descartar e Salvar, só para as regras.

**Localização**
- Mapa ilustrativo (SVG, sem serviço externo), com coordenadas, precisão, horário e fonte.
- Ações "Pedir nova posição" e "Abrir no mapa". O observador vê a posição, mas não pede outra.

**Histórico**
- Apps usados agrupados por dia, com etiqueta de bloqueado ou liberado e filtro "Só bloqueados".
- Comandos recentes com resultado (concluído ou pendente).

## Validação feita

- Carregamento sem erros no console.
- Observador com controles desabilitados.
- Barra de salvar após alterar o horário.
- Filtro de bloqueados e troca de abas.
- Celular (390 px, iframe): sem rolagem horizontal (`scrollWidth` 372).

## Pendências

- Aceite visual humano. Só o responsável muda o status para APROVADO.
- Teclado e contraste sistemáticos não foram auditados.
- Não há `FIDELIDADE.md` nem `docs/inventario-de-telas.md` no projeto; os tokens são os de `painel/index.html`.
- Implementação em `painel/index.html` depende do aceite e segue como lote separado, com testes estruturais.

## Regras preservadas

Nenhuma ação, payload ou RPC muda. Os comandos `grant_time` e `set_time_rules` seguem os contratos atuais. O observador continua somente leitura.
