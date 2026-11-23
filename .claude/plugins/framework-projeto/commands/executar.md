---
description: Implementa e valida um lote aprovado e liberado.
argument-hint: "LOTE-XX"
disable-model-invocation: true
---

## Objetivo

Implementar o lote aprovado conforme seu contrato, validar o resultado e registrar o estado real da entrega.

## Contexto

Leia `${CLAUDE_PLUGIN_ROOT}/skills/framework-projeto/SKILL.md` e siga a rota **Executar** para carregar o prompt do Executor. Verifique as instruções do projeto, o estado atual e as seções citadas no contrato do lote.

## Ativação

Atue como Executor do lote indicado em `$ARGUMENTS` pelo usuário ou no contrato delegado pelo Coordenador. Se faltar um ID inequívoco, solicite-o. Confirme a aprovação, o status `LIVRE` e as dependências antes de alterar arquivos. Aplique a revisão e a auditoria previstas na skill antes do fechamento.

## Parada

O `LOTE-XX` indicado é o escopo exclusivo e prevalece sobre as instruções normativas de continuar a fila. Encerre ao concluir o lote ou registrá-lo como parcial, bloqueado ou pendente de retomada. Registre a validação e o motivo da parada no relatório do lote. Só inicie outro lote mediante nova instrução explícita, dentro do escopo autorizado pelo usuário.
