# Divergências e riscos — auditoria de ajuste

## DV-01 — HTML construído com dados remotos sem escape consistente

- **Classificação:** segurança / bloqueadora para exposição ampla do painel.
- **Evidência:** `painel/index.html` usa `innerHTML` em renderizações de apps,
  categorias e localização; alguns campos usam `esc`, mas `data-cat`, categorias
  e partes de atributos não têm a mesma proteção. Os dados de apps são reportados
  pelo aparelho e podem vir de metadados de aplicativos instalados.
- **Impacto:** um valor malicioso armazenado no backend pode virar XSS no painel
  autenticado de um responsável.
- **Recomendação:** substituir interpolação por `textContent`/criação de nós ou
  aplicar escape contextual completo para texto, atributo e URL; adicionar teste
  com labels/categorias hostis.
- **Estado:** ABERTA — requer lote de segurança e validação.

## DV-02 — Scripts SQL são incrementais, mas não são migrações versionadas

- **Classificação:** operação / consistência de ambiente.
- **Evidência:** `schema.sql`, `signaling.sql`, `apps.sql`, `timerules.sql` e
  `usagelog.sql` precisam ser executados em ordem; o README instrui execução manual
  e o código trata tabelas opcionais com respostas 404.
- **Impacto:** ambientes podem ficar com tabelas, constraints, funções ou permissões
  em versões diferentes; rollback e auditoria de schema ficam manuais.
- **Recomendação:** adotar migrações versionadas e um comando de aplicação/validação,
  mantendo scripts de bootstrap apenas como compatibilidade documentada.
- **Estado:** ABERTA — não bloqueia o uso local, bloqueia evolução segura do backend.

## DV-03 — Cobertura automatizada insuficiente para controles críticos

- **Classificação:** qualidade / risco de regressão.
- **Evidência:** `test` passa sem executar testes (`NO-SOURCE`). Não há evidência
  automatizada para RLS, RPCs, regras de horário, fila de comandos ou XSS do painel.
- **Impacto:** mudanças em bloqueio, autorização e comandos remotos podem regressar
  sem detecção no CI.
- **Recomendação:** criar testes de contrato/RPC/RLS e testes de navegador para o
  painel; priorizar autorização, pareamento, expiração e comandos destrutivos.
- **Estado:** ABERTA — lote de testes recomendado antes de novas funcionalidades.

## DV-04 — Recursos sensíveis ainda sem validação em dispositivo físico

- **Classificação:** produto / operação.
- **Evidência:** README declara WebRTC, check-in e captura de tela como não testados
  em aparelho; o fluxo depende de Device Owner, permissões Android e TURN.
- **Impacto:** a situação real de produção desses recursos é incerta, apesar de o
  restante do README apresentar o fluxo como disponível.
- **Recomendação:** executar matriz de validação em aparelho resetado, com e sem
  Device Owner, e atualizar o status por recurso.
- **Estado:** ABERTA — depende de dispositivo e infraestrutura de teste.

## DV-05 — Correções de áudio, limite e Play Store aguardam validação física

- **Classificação:** validação de integração.
- **Evidência:** foram corrigidos os limites identificados no código: reprodução
  explícita/composição de tracks WebRTC, eventos adicionais do AccessibilityService
  e allowlist temporária da Play Store no Lock Task.
- **Impacto:** o build confirma compilação, mas não confirma permissão de microfone,
  negociação WebRTC, comportamento real do quiosque ou instalação no Android alvo.
- **Recomendação:** testar os três fluxos em aparelho Device Owner e, para áudio,
  também com navegador que aplique política de autoplay.
- **Estado:** EM VALIDAÇÃO — não bloquear o build, mas bloquear o fechamento do
  ajuste como resolvido sem evidência em aparelho.
