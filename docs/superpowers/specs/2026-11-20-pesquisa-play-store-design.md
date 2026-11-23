# Pesquisa de aplicativos na Play Store — desenho curto

## Intenção

Permitir que o responsável pesquise um aplicativo na Play Store a partir da aba
Aplicativos do painel, sem precisar descobrir previamente o ID do pacote.

## Abordagem escolhida

Adicionar um botão `Pesquisar na Play Store` ao lado do campo de instalação. O
botão abre uma nova aba para a busca oficial:

`https://play.google.com/store/search?q=<texto>&c=apps`

Após escolher o app, o responsável cola no campo o link da página ou o ID do
pacote e usa `Instalar`. O fluxo atual de comandos permanece inalterado.

## Alternativas consideradas

- Integrar Managed Google Play: oferece seleção embutida, mas exige Android
  Enterprise, políticas gerenciadas e backend adicional.
- Fazer scraping do catálogo da Play Store: frágil, dependente de HTML externo
  e inadequado para um painel confiável.

## Escopo

- Alterar somente `painel/index.html`.
- Validar texto vazio e codificar a consulta com `encodeURIComponent`.
- Atualizar a orientação visível do campo.

## Fora de escopo

- Seleção automática do app entre domínios diferentes.
- Login Google, API privada da Play Store ou instalação silenciosa pela Play.
- Alteração de RPCs, schema Supabase ou aplicativo Android.

## Critérios de aceite

- Clicar no botão abre a busca de apps da Play Store em nova aba.
- A consulta digitada é preservada corretamente, inclusive com espaços e acentos.
- Campo vazio mostra orientação sem abrir uma busca vazia.
- O botão `Instalar` continua aceitando ID de pacote e URL da Play.
