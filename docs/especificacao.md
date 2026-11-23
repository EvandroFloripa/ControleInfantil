# Especificação do delta de ajuste

Este documento registra apenas o delta identificado na auditoria de 2026-11-20.
Não há uma nova demanda funcional fornecida pelo solicitante; portanto, não foi
inventado um comportamento de produto.

## Objetivos

1. Eliminar a possibilidade de XSS no painel ao renderizar dados vindos do aparelho
   ou do backend.
2. Tornar a evolução do schema reproduzível por migrações versionadas.
3. Criar uma base mínima de testes para autorização, comandos e renderização.
4. Validar e documentar o comportamento dos recursos que dependem de aparelho físico.

## Critérios de não-regressão

- O responsável só visualiza aparelhos e dados permitidos pelo vínculo atual.
- Viewer não envia comandos, não convida pessoas e não altera configurações.
- Token do aparelho continua ausente de leituras diretas e armazenado somente como hash
  no backend.
- Pareamento continua com código de uso único, expiração e limite de tentativas.
- Comandos existentes mantêm seus status `pending`, `done`, `error` e `expired`.
- A renderização preserva rótulos, categorias, localização e histórico sem executar
  conteúdo fornecido como dado.
- O app Android continua compilando com os parâmetros atuais de SDK e assinatura.

## Decisões

- **DEC-01 (PADRÃO ADOTADO):** manter Kotlin/Android nativo, painel estático e
  Supabase enquanto o ajuste for de hardening e evolução incremental.
- **DEC-02 (PADRÃO ADOTADO):** tratar os SQLs atuais como fonte histórica de bootstrap
  e criar migrações aditivas; não apagar tabelas nem reescrever o schema em produção.
- **DEC-03:** qualquer mudança de permissão, retenção de localização/histórico ou
  comportamento de captura exige decisão de negócio separada; não foi assumida.

## UI

Não há delta visual de produto especificado nesta solicitação. Portanto, nenhum
wireframe é liberado e não há aceite visual pendente para implementação.
