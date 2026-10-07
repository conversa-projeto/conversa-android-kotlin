# 2026-10-06 · 05 · Etapa 0 — preservação, limpeza e organização do repositório

- **Fluxo:** Repositório e build
- **Tipo:** configuração / documentação
- **Itens:** `TODO.md` etapas 0.1–0.4 (FC-001…FC-007)
- **Commits:**
  - `855e72d` — limpeza e gradle wrapper;
  - `87ba4b5` — docs, histórico, `CLAUDE.md`, ADR, README;
  - o commit seguinte só preenche estes hashes.

## Contexto
Primeira etapa do `TODO.md`: guardar tudo o que existia antes de começar a nova base e deixar o repositório limpo.

## O que foi feito
- **0.1 Preservação:**
  - O trabalho que estava staged/unstaged em abril já tinha sido commitado pelo usuário em `bb22aa2` ("atualizacao", 2026-10-06 23:18). Não foi preciso um commit "wip".
  - Criada a branch local `legado/abril-2026` em `bb22aa2`.
  - Os 11 stashes foram preservados de dois jeitos:
    - tags locais `legado/stash-00` … `legado/stash-10`, sem perda de nada;
    - patches de texto em `docs/legado/stashes/NN.patch`, sem `.gradle/`, `.idea/` e `build/`. Nenhum stash tinha arquivos não rastreados.
  - O `commit.md` do `stash@{0}` (rascunhos de mensagens de commit de abril) foi salvo em `docs/legado/commit-md-stash00.md`.
  - Patches verificados: sem senhas nem chaves (só exemplos como `"senha": "hash_bcrypt"`).
- **0.2 Tag:** criada a tag anotada local `legado-v1-final` → `legado/abril-2026`.
  - ⛔ **O push para o GitHub não foi feito:** a ação foi bloqueada pela permissão automática do Claude Code. Pendente para o usuário: `novo`, `legado/abril-2026`, `legado-v1-final` e as tags `legado/stash-*`.
- **0.3 Limpeza** (commit `855e72d`):
  - `.gitignore`: `!gradle/wrapper/gradle-wrapper.jar`, `.claude/settings.local.json`, `tmpclaude-*`.
  - `gradle-wrapper.jar` versionado (o `gradlew --version` rodou: Gradle 9.1.0, JBR 21).
  - Removidos do Git: `tmpclaude-*-cwd` (3), `temp_function.kt`, `ChamadaIncomingActivity.kt.OLD` e `.REMOVIDO`, `build/reports/problems/problems-report.html`.
  - Deixaram de ser versionados (o arquivo local continua): `local.properties`, `.claude/settings.local.json`.
  - O `conversa-android-kotlin (2).rar` (36 MB, não versionado) **não foi apagado**: é um backup local, e apagar é irreversível; fica a critério do usuário.
- **0.4 Documentação:**
  - `legado/*.md` → `docs/legado/`; `documentacao-oficial/*.md` → `docs/legado/documentacao-oficial/`; `legado/chamada-tcp/` → `docs/legado/chamada-tcp/` (via `git mv`, o histórico se mantém).
  - `legado/erros.md` (vazio) removido. `build_completo.bat` e `rebuild_project.bat` removidos (caminho absoluto, sem checagem de erro).
  - Criados: `docs/legado/LEIA.md` (aviso de desatualizado + mapa dos stashes), `docs/adr/0001-nova-base.md` e o `CLAUDE.md`.
  - `README.md` reescrito: estado "em reescrita", links, como compilar com o JBR, servidor de dev na porta 443.

## Como foi verificado
- `git status` limpo depois dos commits; `git tag -l 'legado*'` lista as 12 tags; `./gradlew --version` OK com `JAVA_HOME` do Android Studio.

## Pendências
- **Push** (usuário): `git push origin novo legado/abril-2026 legado-v1-final` e `git push origin "refs/tags/legado/stash-*"`.
- **0.5 Spike de mídia:** precisa do servidor de dev rodando e de um aparelho ou emulador com a CA do mkcert. Não executado.
- **0.6 Pedidos ao servidor:** o texto está pronto em `docs/auditoria-2026-10/08-pendencias-servidor.md`. Abrir issues no GitHub do `conversa` depende de aprovação do usuário.
- Opcional: apagar o `conversa-android-kotlin (2).rar`.
