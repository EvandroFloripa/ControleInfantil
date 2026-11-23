# PACOTE DE PROMPTS — PROJETO, PROTOTIPAÇÃO, COORDENAÇÃO E EXECUÇÃO v5.3

## Visão Geral
Framework operacional enxuto para conduzir projetos de software assistidos por IA com:
- Estudo estratégico de mercado, tecnologia e infraestrutura antes de arquitetar;
- Prototipação funcional (Design System + Wireframes) incluída no planejamento;
- Zero lacunas de negócio delegadas ao agente executor;
- Economia extrema de tokens e roteamento rigoroso de modelos por complexidade e risco.

## Arquivos do Pacote
1. `projetista-novo.md` — Para iniciar projetos do zero (Estudo -> Aprovação -> Especificação Fechada + Prototipação + Lotes).
2. `projetista-ajuste.md` — Para projetos já iniciados/legado (Auditoria Forense -> Baseline -> Estudo de Delta + Prototipação -> Lotes Seguros).
3. `coordenador.md` — Para gerenciar a fila de execução, rotear agentes/modelos com custo mínimo, auditar riscos críticos e executar lotes continuamente.
4. `executor.md` — Para implementar os lotes aprovados com zero invenção, testes rigorosos, auto-revisão de diff e commits atômicos.

## Fluxo de Trabalho

### Cenário A: Projeto Novo
1. Abra uma sessão com modelo de raciocínio forte e cole `projetista-novo.md` + sua ideia/briefing.
2. **Fase 1 (Estudo):** O Projetista analisa mercado, benchmarks, stack e custos de infra. Você revisa e aprova.
3. **Fase 2 (Projeto e Protótipo):** O Projetista gera a documentação (`docs/`), `CLAUDE.md`/`AGENTS.md`, `FIDELIDADE.md` e os wireframes das telas. Você valida a proposta visual.
4. **Execução:** Abra uma sessão limpa com `coordenador.md` (ou diretamente com `executor.md` se estiver usando um único agente). Ele assume a fila e executa os lotes liberados até o checkpoint ou conclusão.

### Cenário B: Projeto Já Iniciado
1. Abra o agente no repositório existente com `projetista-ajuste.md` + a mudança desejada.
2. O Projetista audita o código/banco real, sincroniza o `estado-atual.md`, estuda o impacto, especifica o delta + wireframes e libera lotes seguros.
3. Acione `coordenador.md` / `executor.md` para executar os lotes sem quebrar o que já funciona.

## Instalação no Repositório
Salve os arquivos em `docs/ia/prompts/` e adicione no `CLAUDE.md` ou `AGENTS.md` da raiz apenas os ponteiros para cada papel, evitando duplicar o conteúdo integral dos prompts.

## Permissões de comandos e limites

Os prompts v5.3 autorizam operações locais rotineiras sem confirmações repetidas, preservando a aprovação humana para operações destrutivas, remotas ou sensíveis. As ferramentas (Codex CLI, Claude Code, IDE) podem exigir configurações próprias de sandbox/approval; instruções no prompt não substituem essas configurações.


## POLÍTICA OPERACIONAL — AUTONOMIA DE COMANDOS (v5.3)

**Princípio:** não pedir confirmação humana para comandos locais, previsíveis, reversíveis e necessários ao escopo aprovado. Esta política orienta decisões do agente; **não substitui permissões, sandbox ou aprovações obrigatórias da ferramenta/IDE**. Nunca contorne bloqueios de segurança.

### Execução automática, quando dentro do repositório e escopo aprovado
- Ler, pesquisar, criar e editar arquivos; criar diretórios; formatar código e documentação; aplicar patches pontuais sem sobrescrever trabalho preexistente de terceiros.
- Instalar dependências previstas no projeto, executar comandos de desenvolvimento, geração de código, lint, typecheck, build e testes locais; verificar scripts antes de executá-los quando forem desconhecidos.
- Git somente leitura: `git status`, `git diff`, `git log`, `git show`, `git branch --list`, `git fetch`.
- Git local de baixo risco: `git switch -c <branch-nova>`, `git add <arquivos-do-lote>`, `git commit -m <mensagem>` na branch de trabalho. Nunca usar `git add -A` sem conferir o status e a propriedade das alterações.
- `git pull --ff-only` somente após verificar branch, árvore de trabalho e ausência de conflitos previsíveis; se falhar, não forçar.
- Remover apenas arquivos temporários **criados pelo próprio agente nesta tarefa**, com identificação inequívoca e sem alterações de terceiros.

### Exigem autorização humana específica
- `git reset --hard`, `git clean -fd`/`-fdx`, `git push --force`/`--force-with-lease`, reescrita de histórico publicado, `git branch -D`, exclusão de branches remotas, descarte de alterações preexistentes, substituição de arquivos não pertencentes ao lote.
- `git push` comum, abertura de PR, merge em branch compartilhada, deploy ou publicação externa, salvo se já houver autorização explícita e específica no projeto para aquela ação e destino.
- Migração destrutiva de banco, alterações em produção, exclusão de dados persistentes, rotação de credenciais, mudanças de permissões sensíveis, despesas ou serviços externos não aprovados.
- Comandos desconhecidos, scripts de procedência duvidosa ou operações cujo efeito não possa ser avaliado com segurança.

### Protocolo de decisão sem interrupções
1. Antes de operações Git de escrita, conferir `git status --short --branch` e preservar alterações preexistentes.
2. Se a operação estiver na lista automática, **executar sem perguntar**; não criar checkpoints artificiais nem pedir aceite a cada edição, teste ou commit.
3. Se uma ação depender de permissão do ambiente, solicitar apenas a aprovação mínima exigida pela ferramenta, sem alegar que o prompt remove essa exigência.
4. Se uma ação de risco elevado for necessária, explicar comando, alvo, impacto e alternativa segura, solicitando aprovação específica; continuar lotes independentes enquanto aguarda.
5. Não usar `--yes`, `--force`, bypass de sandbox ou mecanismos equivalentes para transformar operação arriscada em automática.
6. A autorização de comandos **não elimina** os portões humanos de produto, escopo, UI, negócio e segurança definidos neste documento.
