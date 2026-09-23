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
| 🧩 Bloquear apps | ✅ Funciona | Tela para escolher os apps liberados + launcher em quiosque |
| ⏰ Limites de horário | ❌ Ainda não | Nada implementado |
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
3. **Configuração do app:** preencha a URL e a chave do Supabase (veja
   `app/src/main/java/com/controleinfantil/kids/remote/SupabaseConfig.kt`).
4. **Build:** abra a pasta no Android Studio e rode no aparelho.
5. **Painel:** abra `painel/index.html` no navegador, informe a mesma URL e chave,
   crie sua conta e pareie (passo abaixo).

## Escolher os apps da criança

Na área do responsável (toque longo em "Meus aplicativos"), use **"Escolher os apps
liberados"**. Marque os aplicativos e toque em Salvar — só os marcados aparecem na
tela inicial da criança. Enquanto nada estiver marcado, a tela dela fica vazia com
um aviso explicando onde configurar.

## Como parear

1. No celular da criança, abra a área do responsável (toque longo em "Meus
   aplicativos") e toque em **"Gerar código de pareamento"**.
2. No painel, entre com sua conta e use **"Parear com um código"**. O código tem 8
   caracteres, vale 15 minutos e serve uma única vez.
3. Quem pareia primeiro vira **guardião**. Para dar acesso a outra pessoa da
   família, o guardião gera um **convite** no painel, escolhendo:
   - **Guardião** — envia comandos, convida e remove pessoas;
   - **Observador** — só vê aparelho, localização e histórico.

## Segurança

- Os responsáveis entram com **login** (Supabase Auth); cada um só enxerga os
  aparelhos aos quais foi vinculado.
- O celular da criança **não acessa as tabelas**: ele recebe um **token secreto** no
  registro (o banco guarda apenas o hash SHA-256) e age só por funções que conferem
  esse token.
- Comandos parados há mais de **10 minutos expiram**, para que um "reiniciar"
  esquecido não dispare horas depois. Há limite de 30 comandos por 10 minutos.
- Códigos de pareamento errados são limitados a **10 tentativas por hora** por conta.

> **Risco conhecido:** a função de registro de aparelho é aberta (como precisa ser,
> já que o celular ainda não tem identidade). Quem tiver a chave `anon` pode criar
> registros vazios de aparelho. Eles não dão acesso a nada — ficam sem responsável
> vinculado — mas ocupam espaço. Se isso virar problema, o próximo passo é registrar
> o aparelho por uma Edge Function com verificação extra.

## Aviso legal e ético

Use apenas em um aparelho **que você possui** e entrega a **uma criança sob sua
responsabilidade**. Monitorar um adulto sem consentimento é ilegal na maioria dos
lugares. Este projeto é feito para controle parental transparente de uma criança
pequena — não para vigilância escondida de ninguém.
