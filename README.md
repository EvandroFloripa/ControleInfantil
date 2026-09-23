# Controle Infantil

App de **controle parental** para um celular Android que o responsável (avô/avó, pai/mãe)
compra, configura e entrega para uma criança pequena. Foi pensado para um cenário
específico e legítimo:

> "Tenho um neto de 5 anos, vou dar um celular para ele usar e quero acompanhar e
> controlar o aparelho remotamente."

## O que este app faz (e o que não faz)

Tudo aqui é feito de forma **transparente** — do jeito que o Android permite. Este
projeto **não** implementa gravação oculta de câmera/microfone nem espionagem
escondida; quando a câmera ou o microfone ligam, o Android mostra o indicador na
tela, e isso é proposital.

| Recurso | Situação | Como |
|---|---|---|
| 🔒 Bloquear a tela remotamente | ✅ Funciona | Device Admin (`lockNow`) |
| 🧩 Fechar / bloquear apps | ✅ Funciona | Launcher em modo quiosque (só apps liberados) |
| ⏰ Limites de horário | ✅ Funciona | Regras no launcher + política |
| 📍 Localização | ✅ Funciona | FusedLocation, reporta ao painel |
| 🛰️ Ligar o GPS se estiver desligado | ✅ Funciona | Device Owner (`setLocationEnabled`) |
| 🔁 Reiniciar o aparelho | ✅ Funciona | Device Owner (`reboot`) |
| 🖥️ Ver a tela em uso | ⚙️ Estruturado | MediaProjection (mostra ícone de transmissão) |
| 📷🎙️ Check-in de vídeo/áudio | ⚙️ Estruturado | Chamada transparente (mostra indicador do Android) |
| ⛔ Desligar (shutdown) total | ❌ Impossível para apps | Use "bloquear tela" ou "reiniciar" no lugar |

> **Por que "desligar" não existe?** O Android não expõe *shutdown* completo para
> nenhum app de terceiros, nem mesmo para um app Device Owner. O substituto real é
> bloquear a tela ou reiniciar.

## Arquitetura

```
┌──────────────────────────┐         ┌──────────────────┐         ┌───────────────────────┐
│  App no celular da        │  polls  │     Supabase      │  envia  │  Você (painel/celular) │
│  criança (este projeto)   │ ───────▶│  (fila de         │◀─────── │  manda comandos         │
│  - launcher quiosque      │         │   comandos +      │         │                        │
│  - executa comandos       │         │   localização)    │         │                        │
└──────────────────────────┘         └──────────────────┘         └───────────────────────┘
```

- **App da criança** (`/app`): app Android nativo em Kotlin. É o *launcher* do
  aparelho e roda um serviço em segundo plano que consulta a fila de comandos.
- **Backend** (`/supabase`): tabelas `devices`, `commands` e `locations`. O SQL
  para criar tudo está em [`supabase/schema.sql`](supabase/schema.sql).
- **Painel do responsável**: por enquanto os comandos são inseridos direto na
  tabela `commands` (dá para fazer pelo próprio Supabase). Um painel web é o
  próximo passo natural.

## Comece por aqui

1. **Provisionamento (importante):** para os recursos fortes (reiniciar, ligar GPS,
   quiosque completo) o app precisa ser **Device Owner**, o que exige instalar via
   ADB em um aparelho recém-resetado. O passo a passo está em
   [`docs/PROVISIONAMENTO.md`](docs/PROVISIONAMENTO.md).
2. **Backend:** rode o [`supabase/schema.sql`](supabase/schema.sql) no seu projeto Supabase.
   > ⚠️ **Em andamento:** o schema já tem a versão segura (login dos controladores,
   > token por aparelho, funções no lugar de acesso direto às tabelas), mas o app
   > Android e o painel **ainda usam o acesso antigo** e param de funcionar com ele.
   > Não aplique em produção até essa adaptação ser concluída e testada.
3. **Configuração do app:** preencha a URL e a chave do Supabase (veja
   `app/src/main/java/com/controleinfantil/kids/remote/SupabaseConfig.kt`).
4. **Build:** abra a pasta no Android Studio e rode no aparelho.

## Aviso legal e ético

Use apenas em um aparelho **que você possui** e entrega a **uma criança sob sua
responsabilidade**. Monitorar um adulto sem consentimento é ilegal na maioria dos
lugares. Este projeto é feito para controle parental transparente de uma criança
pequena — não para vigilância escondida de ninguém.
