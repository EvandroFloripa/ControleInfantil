---
name: framework-projeto
description: Use quando o usuário pedir o Framework de Projeto v5.3 para projetar software novo, planejar ajustes em um projeto existente, coordenar lotes aprovados ou executar um lote liberado.
---

# Framework de Projeto v5.3

Identifique a intenção pelo pedido. Se um comando do plugin já selecionou o papel, mantenha essa seleção. Use o [roteamento](references/roteamento.md) para localizar o prompt normativo e a primeira fonte de contexto. Resolva os links do pacote a partir do arquivo que os contém e os caminhos do projeto a partir da raiz do projeto do usuário. Carregue a referência do papel selecionado e os trechos necessários ao trabalho. Consulte o [manual operacional](references/manual-operacional.md) para esclarecer o fluxo entre papéis. A seção de instalação do manual descreve a distribuição dos prompts avulsos.

Se a intenção for ambígua, peça a informação mínima para escolher o papel ou apresente os quatro modos e aguarde a escolha antes de implementar.

## Precedência do lote indicado

Se o usuário ou o Coordenador indicar um `LOTE-XX` para execução, esse lote é o escopo exclusivo de quem recebe a instrução. Essa regra prevalece sobre as instruções normativas do Coordenador e do Executor de continuar a fila, inclusive as de avançar para um lote independente após execução parcial ou bloqueio. Encerre após concluir o lote ou registrá-lo como parcial ou bloqueado. Só inicie outro lote mediante nova instrução explícita, dentro do escopo autorizado pelo usuário.

Ao delegar, inclua essa condição de parada no pacote do Executor. Se o usuário autorizou a fila, o Coordenador pode selecionar o próximo lote e emitir uma nova instrução ao Executor. Se o usuário indicou um único lote, o Coordenador também encerra ao fechá-lo e aguarda nova instrução explícita do usuário.

## Aprovações e segurança

Confirme as aprovações humanas aplicáveis à proposta, à documentação e à UI antes de avançar para as etapas que dependem delas. Em coordenação ou execução, confira o estado real, as dependências e o status `LIVRE` antes de implementar. Registre decisões pendentes de negócio, segurança ou UI pelo fluxo de divergências do papel.

Use as ferramentas de leitura e busca do Claude Code para inspecionar o contexto e o trabalho preexistente antes de editar. Preserve essas alterações. Aplique as travas e a política de comandos do papel dentro das permissões e do sandbox da sessão. Ações que exigem autorização específica continuam sujeitas a essa aprovação.

## Coordenação no Claude Code

Gerencie a fila e delegue a implementação com a ferramenta `Agent` quando disponível e autorizada. Selecione o nível de modelo por complexidade e risco conforme a matriz do Coordenador. Quando disponíveis, Haiku atende ao nível econômico, Sonnet ao equilibrado e Opus ao avançado ou de máxima capacidade, conforme o modelo disponível e o rigor exigido. Se a seleção estiver indisponível ou a troca não for confirmada, registre o nível recomendado e o modelo efetivo quando conhecido. Só prossiga se a capacidade disponível for compatível com o risco.

Controle tokens e contexto com leituras pontuais, um pacote mínimo por lote e os checkpoints do Coordenador. Envie ao Executor o ID, o objetivo, os critérios de aceite, os trechos normativos aplicáveis, os arquivos-alvo e as restrições. Dê ao auditor o objetivo, os critérios, o diff e os resultados dos testes. Ao se aproximar do limite de contexto, estabilize o trabalho e registre o ponto de retomada antes de encerrar.

## Auditoria e encerramento

Nas rotas de coordenação e execução, revise criticamente o diff antes de fechar o lote. Exija auditoria independente para risco crítico e aplique a auditoria proporcional prevista pelo Coordenador. Se a auditoria independente estiver indisponível no modo de agente único, faça a auto-revisão crítica em etapa separada e registre `AUDITORIA INDEPENDENTE: INDISPONÍVEL — AUTO-REVISÃO REALIZADA` no campo de auditoria do Coordenador ou na linha `Validação` do Executor, junto dos resultados dos testes. Use `DISPENSADA` apenas quando a auditoria independente for opcional.

Ao encerrar, registre o papel usado, o estado do lote quando aplicável, os arquivos alterados, as validações realizadas e os bloqueios humanos pendentes no formato do papel.
