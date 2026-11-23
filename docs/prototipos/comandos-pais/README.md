# Protótipo — Comandos para pais

**Status:** PROPOSTA VISUAL — AGUARDANDO ACEITE. Não implementado. Base da análise em `docs/proposta/comandos-pais.md`.

## Como abrir

Abra `index.html` no navegador, ou sirva a pasta (`python -m http.server 8769` nesta pasta) e acesse http://127.0.0.1:8769/index.html.

Cada cartão mostra a tela do responsável e o que a criança vê. Os filtros da barra no topo separam os **próximos passos** dos itens **fora desta fase**.

## Itens

**Próximos passos** (viabilidade alta ou média, risco baixo ou médio)
- Tocar o aparelho
- Mensagem na tela da criança
- Bateria e status no painel
- Relatório semanal
- Alerta de aparelho offline
- Alerta de tentativa de desativar
- Modo escola
- Descanso por dia da semana
- Limite de tempo por app (risco médio)

**Fora desta fase** (desenho para discussão)
- Aviso ao chegar ou sair de um lugar (risco alto: dado sensível)
- Pausar a internet (exige VPN ou administração de rede)
- Bloqueio de sites (exige filtro próprio)
- Contatos permitidos (exige substituir discador e mensagens)
- Impedir mudar data e hora (exige Device Owner e teste no aparelho; risco médio)

## Validação feita

- Os 14 cartões carregam e o filtro "Fora desta fase" mostra 5.
- Sem erros no console.
- Celular (390 px, iframe): sem rolagem horizontal.

## Pendências

- Aceite humano. Só o responsável muda o status para APROVADO.
- Decisão sobre geofencing e retenção de localização.
- Os botões são demonstrativos: nenhum comando é enviado.
- Textos e avisos para a criança precisam de revisão de linguagem.
