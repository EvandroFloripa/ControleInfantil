# Roteamento no Claude Code

Use o objetivo do pedido para escolher a rota. Os comandos abaixo selecionam um papel explicitamente. Os caminhos de documentação na última coluna pertencem ao projeto do usuário.

| Intenção | Comando explícito | Referência normativa | Primeira fonte de contexto |
| --- | --- | --- | --- |
| Projetar | `/framework-projeto:projetar [briefing]` | [Projetista Novo](projetista-novo.md) | Briefing ou ideia do usuário e documentação inicial indicada. |
| Ajustar | `/framework-projeto:ajustar [demanda]` | [Projetista Ajuste](projetista-ajuste.md) | `CLAUDE.md` e `AGENTS.md`, se presentes, e `docs/execucao/estado-atual.md`, se existir. Confira a documentação contra o código e o schema. |
| Prototipar | `/framework-projeto:prototipar [escopo ou TEL-XX]` | [Designer UI/UX e Prototipador](designer-ui-ux-prototipador.md) | `FIDELIDADE.md`, `docs/inventario-de-telas.md` e as seções pertinentes da especificação e do plano de entrega. |
| Coordenar | `/framework-projeto:coordenar [fila ou LOTE-XX]` | [Coordenador](coordenador.md) | `CLAUDE.md` e `AGENTS.md`, se presentes, e `docs/execucao/estado-atual.md`. Confira a fila, a liberação e as dependências dos lotes. |
| Executar | `/framework-projeto:executar LOTE-XX` | [Executor](executor.md) | `CLAUDE.md` e `AGENTS.md`, se presentes, e `docs/execucao/estado-atual.md`. Confirme o status `LIVRE` e leia as seções citadas no contrato do lote. |

Se o usuário ou o Coordenador indicar `LOTE-XX`, aplique a regra de precedência da skill. O lote é o escopo exclusivo de quem recebe a instrução. As instruções normativas de continuar a fila ficam subordinadas a essa regra. Encerre ao concluir o lote ou registrá-lo como parcial ou bloqueado. Só avance mediante nova instrução explícita dentro do escopo autorizado pelo usuário.

Na coordenação, consulte também a [referência do Executor](executor.md) ao preparar a delegação ou assumir a execução em modo de agente único. Use o [manual operacional](manual-operacional.md) para consultar as transições entre papéis.
