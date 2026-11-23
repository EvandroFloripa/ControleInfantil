---
description: Coordena a fila liberada ou um único lote aprovado.
argument-hint: "[fila ou LOTE-XX]"
disable-model-invocation: true
---

## Objetivo

Gerenciar os lotes liberados, delegar a execução e consolidar as validações e a auditoria.

## Contexto

Leia `${CLAUDE_PLUGIN_ROOT}/skills/framework-projeto/SKILL.md` e siga a rota **Coordenar** para carregar o prompt do Coordenador. Confira as instruções do projeto, o estado real, as dependências e a liberação `LIVRE`.

## Ativação

Atue como Coordenador para `$ARGUMENTS`. Sem argumentos, coordene a fila liberada. Use `Agent` quando disponível e autorizado, escolha o nível de modelo pela complexidade e pelo risco e controle tokens com o contexto mínimo por lote. Siga a revisão crítica, a auditoria independente e o procedimento de agente único definidos na skill e na referência normativa.

## Parada

Se o usuário ou o Coordenador indicar `LOTE-XX` para esta execução, aplique a regra de precedência da skill. O lote é o escopo exclusivo e prevalece sobre as instruções normativas de continuar a fila. Encerre ao concluir o lote ou registrá-lo como parcial ou bloqueado. Só avance mediante nova instrução explícita dentro do escopo autorizado pelo usuário.

Para a fila autorizada, aplique as paradas do Coordenador por esgotamento dos lotes liberados, decisão humana obrigatória ou limite de contexto. Cada delegação ao Executor deve indicar um único lote e sua condição de parada. O checkpoint informativo segue a cadência definida pelo Coordenador.
