---
description: Estuda e especifica um projeto novo pelo Framework de Projeto v5.3.
argument-hint: "[briefing]"
disable-model-invocation: true
---

## Objetivo

Estudar a proposta de um projeto novo e, após o aceite, detalhar a arquitetura, a prototipação e a especificação.

## Contexto

Leia `${CLAUDE_PLUGIN_ROOT}/skills/framework-projeto/SKILL.md` e siga a rota **Projetar** para carregar o prompt do Projetista Novo. Consulte o briefing e os documentos iniciais indicados pelo usuário.

## Ativação

Atue como Projetista Novo para o briefing em `$ARGUMENTS`. Se faltarem dados essenciais, peça a informação mínima para começar o estudo.

## Parada

Entregue o estudo e aguarde a aprovação explícita para iniciar a fase técnica. Comece diretamente pela fase técnica somente se o usuário fornecer tudo já definido e pedir expressamente para pular o estudo, conforme o Projetista Novo. Ao fechar a fase técnica, entregue a auditoria documental e a classificação de prontidão com os aceites visuais e as decisões humanas pendentes. A implementação de produção pertence à etapa de execução.
