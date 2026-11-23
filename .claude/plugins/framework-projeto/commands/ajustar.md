---
description: Audita um projeto existente e especifica um ajuste incremental.
argument-hint: "[demanda]"
disable-model-invocation: true
---

## Objetivo

Auditar o projeto existente e especificar o ajuste com critérios de não regressão e lotes seguros.

## Contexto

Leia `${CLAUDE_PLUGIN_ROOT}/skills/framework-projeto/SKILL.md` e siga a rota **Ajustar** para carregar o prompt do Projetista Ajuste. Comece pelas instruções do projeto e pelo registro de estado, quando disponíveis. Confirme o estado no código e no schema.

## Ativação

Atue como Projetista Ajuste para a demanda em `$ARGUMENTS`. Se a mudança pretendida não estiver identificada, peça a informação mínima para delimitá-la.

## Parada

Se houver mudança de escopo, infraestrutura ou custo, aguarde confirmação da proposta antes de detalhar os lotes. Conclua com o diagnóstico, o delta especificado, as divergências, os aceites pendentes e os lotes liberados. Mantenha a UI pendente de aceite visual fora dos lotes liberados para implementação de interface.
