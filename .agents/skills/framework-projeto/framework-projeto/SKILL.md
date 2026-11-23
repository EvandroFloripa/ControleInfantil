---
name: framework-projeto
description: Use quando o usuário pedir o framework de projeto v5.4 para projetar, ajustar, prototipar, coordenar ou executar um projeto de software.
---

# Framework de Projeto v5.4

Identifique o papel pelo objetivo do pedido. Use [o roteamento](references/roteamento.md) para localizar a referência do papel e a primeira fonte de contexto do projeto. Carregue apenas a referência do papel escolhido e os trechos do projeto necessários ao trabalho. Consulte o [manual operacional](references/manual-operacional.md) se o usuário pedir uma visão do método ou se precisar esclarecer uma transição entre papéis. A seção de instalação do manual descreve a distribuição dos prompts avulsos.

- **Projetar** inicia pelo estudo de um projeto novo e segue os portões do Projetista Novo.
- **Ajustar** audita o projeto existente e especifica o que muda, com os portões do Projetista Ajuste.
- **Prototipar** transforma a especificação aprovada em protótipos visuais verificáveis, documenta estados e prepara o aceite visual antes dos lotes frontend.
- **Coordenar** gerencia a fila de lotes liberados e os subagentes quando disponíveis e autorizados. Selecione o nível de modelo pela complexidade e pelo risco, conforme a matriz do Coordenador. Controle tokens e contexto com leituras pontuais, pacotes mínimos por lote e o checkpoint de contexto. Quando faltar suporte para delegar ou trocar o modelo, siga o procedimento do Coordenador para essa limitação e registre o que a sessão permitiu.
- **Executar** implementa um lote aprovado e liberado (`LIVRE`), conforme seu contrato e os critérios de aceite.

Se a intenção estiver ambígua, peça a informação mínima para escolher o papel ou apresente os quatro modos e aguarde a escolha antes de implementar.

Preserve os portões humanos do papel escolhido. Confirme a aprovação aplicável da proposta, da documentação e da UI antes de avançar para as etapas que dependem dela. Coordenar e executar exigem conferir o estado real do projeto e a liberação do lote antes de iniciar a implementação. Encaminhe decisões pendentes de negócio, segurança ou UI pelo fluxo de divergências do papel.

Nos modos **Coordenar** e **Executar**, um lote de risco crítico exige auditoria independente antes do fechamento. No modo de agente único, se a auditoria independente estiver indisponível, faça a auto-revisão crítica do diff em etapa separada. Registre a auditoria como indisponível nesse caso. Reserve `DISPENSADA` para lotes em que a auditoria independente é opcional.

- **Coordenação** — Após a auto-revisão por indisponibilidade, substitua o campo de auditoria do relatório pela frase `AUDITORIA INDEPENDENTE: INDISPONÍVEL — AUTO-REVISÃO REALIZADA`.
- **Execução direta** — Na linha `Validação` do relatório do Executor, preserve os resultados dos testes, do lint e do typecheck e acrescente o resultado da auditoria independente. Após a auto-revisão por indisponibilidade, acrescente nessa mesma linha exatamente `AUDITORIA INDEPENDENTE: INDISPONÍVEL — AUTO-REVISÃO REALIZADA`.

Quando o usuário indicar um lote específico para execução pelo ID (`LOTE-XX`), trate esse lote como o escopo exclusivo da execução. Encerre ao concluir ou fechar o lote, inclusive quando ficar parcial ou bloqueado. Só inicie outro lote mediante instrução explícita do usuário para continuar. Esse limite prevalece sobre as instruções de continuidade do Coordenador e do Executor, inclusive as de avançar após um bloqueio. No relatório, registre o encerramento do lote indicado.

Siga as travas de segurança, as permissões do Codex e a política de comandos do papel. Inspecione e preserve o trabalho preexistente antes de editar. Encerre com o papel usado, os arquivos alterados, as validações executadas e os bloqueios humanos pendentes, no formato do papel.
