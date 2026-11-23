# Protótipo — App do celular: launcher e área do responsável

**Status:** PROPOSTA VISUAL — AGUARDANDO ACEITE. Não aprovado. Não implementado no app Android.

## Como abrir

Abra `index.html` no navegador, ou sirva a pasta (`python -m http.server 8768` nesta pasta) e acesse http://127.0.0.1:8768/index.html.

A barra no topo alterna as telas: launcher da criança, tempo esgotado, área do responsável e área com o controle desativado. Os dados são fictícios.

## Análise do estado atual

**Launcher** (`launcher/LauncherActivity.kt`, `res/layout/activity_launcher.xml`)
- Relógio e data, grade de 4 colunas com os apps liberados e avisos de texto.
- A criança não vê quanto tempo ainda tem nem até que horas pode usar. Ela só descobre ao ser bloqueada.
- A entrada do responsável é o toque longo no relógio, sem nenhuma pista visual. É intencional, para a criança não encontrar, mas a intenção não aparece em lugar nenhum.
- A tela de bloqueio usa a mesma linguagem dos avisos (caixa de texto com fundo), sem explicar quando volta.

**Área do responsável** (`setup/SetupActivity.kt`, `res/layout/activity_setup.xml`)
- Uma única tela rolável, com cerca de 15 botões de peso igual, sem agrupamento.
- Ações de risco (liberar o aparelho, desativar o controle) ficam misturadas com as comuns.
- O estado das permissões (Acessibilidade, sobreposição, administrador) aparece só em texto, e a ação fica num botão separado.
- O código de pareamento aparece direto na tela, sem contexto.
- Horário e limite já têm tela própria (`ScheduleActivity`), mas a área principal não mostra o resumo.

## O que o protótipo propõe

1. **Launcher**: indicador "Restam X min · até HH:MM", só com dados que a criança já pode ver. Nada de entrada visível para o responsável.
2. **Tela de bloqueio**: motivo em linguagem simples, quando volta e botão discreto para o responsável.
3. **Área do responsável**: resumo do aparelho no topo, depois grupos Uso, Aparelho, Manutenção e Pareamento.
4. **Permissões com status e ação direta**: Acessibilidade, sobreposição e administrador com indicação OK ou pendente.
5. **Zona de risco**: "Liberar este aparelho" isolado, com borda tracejada e confirmação.

## Pendências e trade-offs

- **Launcher**: o indicador de tempo expõe o limite à criança. É uma decisão de produto; se o responsável preferir não mostrar, a tela fica sem o indicador.
- **Acesso do responsável**: o protótipo mantém o toque longo e o PIN. Não há botão visível no launcher. Mudar isso exige decisão humana e pode reduzir a proteção.
- **Textos**: os textos de bloqueio são propostos; a revisão deve ser feita com o responsável pelo produto.
- Teclado, contraste e tamanho de toque no aparelho real não foram verificados.
- Aceite visual humano pendente. Só o responsável muda o status para APROVADO.

## Regras preservadas

Nenhuma regra de bloqueio, PIN, horário, comando ou permissão muda. A área do responsável continua exigindo o PIN a cada entrada, e o controle desativado continua gravado.
