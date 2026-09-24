# Provisionamento (Device Owner) e instalação

Este guia explica como instalar o app no celular da criança com os poderes
completos (reiniciar, forçar GPS, modo quiosque de verdade). Sem isto, o app ainda
funciona como **Device Admin** (bloquear tela, launcher básico), mas alguns recursos
ficam indisponíveis.

## Por que "Device Owner"?

O Android reserva as ações mais fortes para um app que é o "dono" do aparelho. Isso
**não** pode ser ativado só tocando na tela — por segurança, exige um comando via
**ADB** em um aparelho **recém-resetado e sem nenhuma conta Google configurada**.

## Passo a passo

### 1. Prepare o aparelho da criança
- Faça um **reset de fábrica** (ou use um aparelho novo).
- Ao ligar, **não adicione nenhuma conta Google** (pule essa etapa). Se houver conta,
  o comando de Device Owner falha.
- Ative as **Opções do desenvolvedor** (toque 7x em *Configurações > Sobre o telefone
  > Número da versão*) e ligue a **Depuração USB**.

### 2. Instale o app
Com o Android Studio (ou o APK gerado) e o aparelho conectado por USB:

```bash
# Compila e instala o app
./gradlew installDebug

# ou instala o APK da última Release (recomendado)
adb install ControleInfantil-0.1.7.apk
```

### 3. Torne o app Device Owner

```bash
adb shell dpm set-device-owner com.controleinfantil.kids/.admin.DeviceAdmin
```

Se aparecer `Success: Device owner set...`, deu certo. Se der erro dizendo que já
existe conta no aparelho, refaça o passo 1.

### 4. Verifique
Abra o app > área do responsável (toque e segure no relógio, digite o PIN). O status
deve mostrar:

```
Device Admin: ✅ ativo
Device Owner: ✅ ativo
```

## Se você NÃO quiser usar ADB

Dá para usar só como **Device Admin**:
- Abra o app, vá na área do responsável e toque em **"Ativar proteção (Device
  Admin)"**.
- Funciona: **bloquear tela** e o **launcher** (lista de apps liberados).
- Com **Acessibilidade + sobreposição** ligadas (botões no setup), também funcionam
  o **bloqueio de apps**, o **bloqueio de tela com PIN** e o bloqueio da instalação.
- Só com Device Owner: **reiniciar**, **forçar o GPS**, **quiosque inquebrável**,
  **impedir desinstalar/desligar o app** e **instalação silenciosa**. Sem ele, um
  adulto ainda consegue desligar a Acessibilidade nas Configurações.

## Depois de instalar

1. Anote o **ID do aparelho** mostrado na área do responsável.
2. Cadastre esse aparelho no **painel do controlador** (`/painel`) e faça o
   pareamento. A partir daí, você (e outros controladores autorizados) enviam
   comandos remotamente.
