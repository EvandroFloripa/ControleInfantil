# Roteamento no Codex

Para selecionar um modo explicitamente, invoque a skill `framework-projeto` no Codex e acrescente ao pedido a frase sugerida. Os caminhos de documentação na última coluna se referem ao projeto em que a skill foi acionada.

| Intenção | Pedido explícito sugerido | Referência do papel | Primeira fonte de contexto |
| --- | --- | --- | --- |
| `projetar` | `projetar um projeto novo com este briefing` | [Projetista Novo](projetista-novo.md) | Briefing ou ideia do usuário e documentação inicial fornecida. |
| `ajustar` | `ajustar o projeto existente para esta demanda` | [Projetista Ajuste](projetista-ajuste.md) | `AGENTS.md` ou `CLAUDE.md`, se presentes, e depois `docs/execucao/estado-atual.md`, se existir. Confronte a documentação com o código e o schema atuais. |
| `prototipar` | `prototipar as telas aprovadas deste escopo` | [Designer UI/UX e Prototipador](designer-ui-ux-prototipador.md) | `FIDELIDADE.md`, `docs/inventario-de-telas.md` e as seções pertinentes da especificação e do plano de entrega. |
| `coordenar` | `coordenar a fila de lotes liberados` | [Coordenador](coordenador.md) | `AGENTS.md` ou `CLAUDE.md` e `docs/execucao/estado-atual.md`. Confira a fila e as dependências dos lotes. |
| `executar` | `executar o lote LOTE-XX` | [Executor](executor.md) | `AGENTS.md` ou `CLAUDE.md` e `docs/execucao/estado-atual.md`. Confira o status `LIVRE` e consulte as seções citadas no contrato do lote. |

Para um pedido como `executar o lote LOTE-XX`, execute apenas o lote indicado, tanto em execução direta quanto via Coordenador, e encerre ao fechá-lo. Avance para outro lote somente com instrução explícita para continuar. O pedido `coordenar a fila de lotes liberados` aciona a continuidade da fila.

Em `coordenar`, carregue também a [referência do Executor](executor.md) ao preparar a delegação de um lote ou assumir sua execução no modo de agente único. Use o [manual operacional](manual-operacional.md) quando precisar consultar o fluxo entre papéis.
