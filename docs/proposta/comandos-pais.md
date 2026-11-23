# Comandos que um responsável gostaria de ter — análise e proposta

**Status:** PROPOSTA — AGUARDANDO DECISÃO HUMANA. Nenhum comando novo foi implementado.

## 1. Referências de mercado

- Os recursos mais comuns em 2026 são limite de tempo, bloqueio de apps, localização e bloqueio remoto. Os gratuitos cobrem o básico, e os pagos acrescentam relatórios e filtros. Fonte: [Tom's Guide — melhores apps de controle parental 2026](https://www.tomsguide.com/us/best-parental-control-apps,review-2258.html) e [NordVPN — apps de controle parental 2026](https://nordvpn.com/blog/best-parental-control-apps/). Os dois são sites de review com interesse comercial; use como referência de mercado, não como especificação.
- Google Family Link cobre limites por aparelho e por app, horários de descanso e de escola, aprovação de instalações, bloqueio de sites no Chrome, localização e toque para encontrar o aparelho. Limitação: só funciona em Android com conta Google supervisionada. Fonte: [Google — gerenciar com Family Link](https://support.google.com/families/answer/7103262?hl=en) e [Google — novidades do Family Link (fev/2025)](https://blog.google/technology/families/family-link-updates-february-2025/).
- Qustodio tem **pausar a internet** do aparelho, bloqueio total do aparelho, limite diário e relatórios; monitoramento de ligações e SMS é só Android. Fonte: [Qustodio — recursos](https://www.qustodio.com:443/en/features) e [análise TechRadar](https://www.techradar.com/uk/reviews/qustodio-parental-control-software). A página exata de cada plano pode mudar; confirme antes de citar.
- Android (Device Owner): o modo Lock Task permite travar o aparelho em uma lista de apps a partir do Android 9 para Device Owner. Não há API nativa para limite de tempo por app. Fonte: [Android — Lock task mode](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode?hl=en) e [Android 9 para empresas](https://developer.android.com/work/versions/android-9.0).

## 2. O que o app já faz

| Comando | Já existe | Como |
|---|---|---|
| Bloquear a tela agora | Sim | `lock_screen` |
| Reiniciar o aparelho | Sim | `reboot` |
| Ligar GPS e pedir localização | Sim | `enable_location`, `request_location` |
| Ver e ouvir (check-in) e ver a tela | Sim | `start_checkin`, `start_screen_view` |
| Liberar apps | Sim | `set_allowed_apps` |
| Instalar e desinstalar app | Sim | `install_app`, `uninstall_app` |
| Horário e limite diário | Sim | `set_time_rules` |
| Tempo extra | Sim | `grant_time` |
| Liberar por 15 min / desativar controle | Sim | Pelo app, com PIN |
| Histórico de apps abertos | Sim | Registro local de uso no app |
| Última localização | Parcial | Só a última posição; sem trajeto |

## 3. Comandos propostos

Viabilidade: **alta** (já cabe no modelo atual), **média** (exige código no aparelho e nova permissão), **baixa** (exige API que o Android não dá ao app ou decisão de produto forte).

| # | Comando | Para que serve | Viabilidade | Risco | Observação |
|---|---|---|---|---|---|
| 1 | **Tocar o aparelho** | Achar o celular perdido na casa | Alta | Baixo | Toca som em volume alto por alguns segundos. Família Link faz o mesmo. |
| 2 | **Mensagem na tela da criança** | Avisar "hora de jantar" sem ligar | Alta | Baixo | Notificação ou aviso sobre a tela, com texto curto do pai. |
| 3 | **Bateria e status no painel** | Saber se o aparelho está sem carga | Alta | Baixo | Campo novo no status enviado ao painel. |
| 4 | **Alerta de aparelho offline** | Saber quando o celular ficou sem internet | Média | Baixo | Depende de o painel ver o último contato. |
| 5 | **Alerta de tentativa de desinstalar ou desativar** | Saber se a criança mexeu na proteção | Média | Baixo | Depende de registrar eventos no aparelho. |
| 6 | **Modo escola** | Só apps liberados no horário escolar | Média | Baixo | Usa horários e a lista de apps já existentes. |
| 7 | **Modo descanso agendado por dia** | Dias diferentes (ex.: fim de semana) | Média | Baixo | Estende o horário atual por dia da semana. |
| 8 | **Limite de tempo por app** | Ex.: 30 min de jogos por dia | Média | Médio | Precisa contar o tempo por app; o app só conta o que ele observa. |
| 9 | **Geofencing (entrar/sair de um lugar)** | Aviso quando chega na escola | Média | Alto | Dado sensível de localização; exige decisão de retenção e aviso à criança. |
| 10 | **Pausar a internet** | Desligar a rede do aparelho | Baixa | Alto | Sem API para isso no app comum; exigiria VPN ou administração de rede. |
| 11 | **Bloqueio de sites** | Filtrar navegação | Baixa | Alto | Exige VPN local ou DNS; complexo de manter e fácil de burlar. |
| 12 | **Contatos permitidos (ligações e SMS)** | Só falar com contatos aprovados | Baixa | Alto | Exige substituir o discador e o app de mensagens. |
| 13 | **Relatório semanal** | Resumo de uso e bloqueios | Alta | Baixo | Feito no servidor com o histórico que já existe. |
| 14 | **Impedir alterar data e hora** | Evitar burlar o horário | Média | Médio | Exige restrição de Device Owner; testar no aparelho. |

## 4. Recomendação de ordem

1. **Primeiro (viabilidade alta, risco baixo):** tocar o aparelho, mensagem na tela, bateria no painel, relatório semanal.
2. **Depois (média, decisão de produto pequena):** modo escola, descanso por dia da semana, alertas de offline e de tentativa de desativar.
3. **Por último, com decisão humana antes:** limite por app, geofencing, impedir mudar data e hora.
4. **Não recomendado agora:** pausar a internet, bloqueio de sites e contatos permitidos. Exigem VPN ou substituição de apps do sistema, têm alto risco de quebrar o aparelho e são fáceis de burlar.

## 5. Decisões humanas necessárias

- Aceitar ou não o geofencing, com a criança sabendo que o aparelho registra o lugar.
- Definir a retenção do trajeto de localização (hoje só a última posição).
- Aceitar que alguns recursos (pausar internet, contatos permitidos, bloqueio de sites) ficam fora desta fase.
- Confirmar que a proteção por Device Owner continua sendo o modelo do produto.

## 6. Princípios que valem para todos

- Comandos que afetam a criança têm aviso visível no aparelho, como já acontece com o check-in e a tela.
- Nenhum comando novo mexe em permissões de forma oculta.
- Cada comando novo passa pelo PIN quando for de configuração, e não é liberado só pelo painel.
