# CLAUDE.md — conversa-android-kotlin

Instruções para quem trabalha neste repositório, pessoas ou agentes de IA. Leia antes de qualquer alteração.

## O projeto

- Cliente **Android (Kotlin)** do Conversa. Os repositórios irmãos:
  - `../conversa`: servidor Bun + Elysia + Postgres + MinIO + MediaMTX + coturn;
  - `../conversa-web`: cliente web Vue, que é a **referência de comportamento**.
- **Situação (2026-10-06):** o app atual é **legado** (escrito para o servidor Delphi). Foi decidido **recomeçar numa nova base** (`docs/adr/0001-nova-base.md`).
- O código legado fica na branch `legado/abril-2026` / tag `legado-v1-final`.

## Onde está cada coisa

| O quê | Onde |
|---|---|
| Visão geral, decisão e índice da auditoria | `docs/auditoria-2026-10/00-LEIAME.md` |
| **Contrato do servidor (fonte da verdade)**: rotas, WebSocket, push, anexos, chamadas, pegadinhas | `docs/auditoria-2026-10/01-contrato-servidor-atual.md` |
| Funcionalidades do web (IDs AUT/CON/MSG/ENV/ANX/PES/CHA/SIP/NOT/PRE/ATV/CFG/GER) | `docs/auditoria-2026-10/03-inventario-web.md` |
| Problemas do app legado (#1–#57) | `docs/auditoria-2026-10/04-auditoria-android.md` |
| Paridade web × Android | `docs/auditoria-2026-10/05-matriz-paridade.md` |
| Fila de correções (FC-xxx) | `docs/auditoria-2026-10/06-fila-de-correcoes.md` |
| Arquitetura da nova base | `docs/auditoria-2026-10/07-plano-nova-base.md` |
| Pendências do servidor (S1–S14) | `docs/auditoria-2026-10/08-pendencias-servidor.md` |
| **Passo a passo do trabalho** | `TODO.md` |
| **Histórico de alterações** | `docs/historico/indice.md` + `docs/historico/*.md` |
| Decisões de arquitetura | `docs/adr/NNNN-*.md` |
| Testar no emulador contra o servidor de dev (e as contas de teste) | `docs/desenvolvimento/emulador.md` |
| Matriz de testes manuais de chamada (emulador e aparelho) | `docs/testes/chamadas.md` |
| Contrato publicado pelo servidor (OpenAPI) | `docs/contrato/openapi.json` |
| Documentação antiga (não usar como referência) | `docs/legado/` |

## Regra obrigatória: histórico de alterações

**Toda alteração** gera um registro em `docs/historico/`, **no mesmo commit da alteração**. Vale para código, documentação, configuração, build, dependências e decisões.

1. **Arquivo de detalhe:** `docs/historico/AAAA-MM-DD-NN-assunto-curto.md`.
   - `NN` é a sequência do dia (01, 02, …); o assunto vai em kebab-case, sem acentos.
   - Use o modelo `docs/historico/_modelo.md`: fluxo, tipo, itens (FC/TODO/IDs), commits, contexto, o que foi feito (com arquivos), como foi verificado, decisões e pendências.
2. **Índice:** acrescente **uma linha** em `docs/historico/indice.md`, **na seção do fluxo** correspondente, em ordem cronológica (mais recente embaixo). Formato:
   `- AAAA-MM-DD · [Título](AAAA-MM-DD-NN-assunto.md) — resumo de uma linha`
   - Se a alteração toca mais de um fluxo, registre no fluxo principal e cite os outros no detalhe.
   - Se surgir um fluxo novo, crie a seção no índice.
3. **Commit:** depois de commitar, preencha o hash no campo **Commits** do detalhe (pode ir no commit seguinte).
4. **Mudou o servidor ou o web?** Registre no fluxo "Sincronização com servidor e web" e atualize os docs afetados (01, 03, 05, 06, `TODO.md`), marcando o que mudou com 🆕.

## Como trabalhar com o `TODO.md`

- Siga a ordem das etapas. Cada linha é uma ação pequena; ao concluir, marque `[x]` e anote o commit (`✔ abc1234`).
- Só passe de seção depois de marcar as linhas **Teste:**.
- Ao fazer algo que não está no TODO, inclua a linha no lugar certo (e registre no histórico).
- Itens bloqueados: deixe `[ ]` e acrescente `⛔ motivo` na mesma linha.
- Toda semana: atualize a matriz de paridade (doc 05) e confira se o servidor/web mudaram:
  - `git -C ../conversa fetch && git -C ../conversa log HEAD..origin/main --oneline`
  - o mesmo para `../conversa-web`.

## Build

- **JDK 17+** (o `java` do PATH desta máquina é 1.8; use o do Android Studio):
  - Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`
  - PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
- `./gradlew assembleDebug` (Git Bash) ou `.\gradlew.bat assembleDebug` (PowerShell).
- O `local.properties` não é versionado; o `gradle-wrapper.jar` é.
- Testes: `./gradlew testDebugUnitTest :core:model:test :core:testing:test`. No emulador (Android de verdade): `./gradlew :app:connectedDebugAndroidTest` — **desinstala o app no fim** (servidor e sessão somem); guia em `docs/desenvolvimento/emulador.md`. Estilo: `./gradlew ktlintCheck` (`ktlintFormat` corrige). Lint: `./gradlew :app:lintDebug`.
- Toolchain: Gradle 9.8, AGP 9.4 (Kotlin embutido), Kotlin 2.4, compileSdk 37, targetSdk 36, minSdk 28. Versões só no `gradle/libs.versions.toml`.

## Estrutura dos módulos (nova base)

- `:app` — Application, MainActivity, navegação.
- `:core:model` — modelos de domínio (Kotlin puro).
- `:core:network` — `ServerConfig`, Retrofit (`ConversaApi`, 67 rotas), DTOs + mapeadores, erros (`ErroApi`), `RealtimeClient` (WebSocket).
- `:core:datastore` — sessão cifrada (Tink/Keystore) e preferências.
- `:core:database` — Room (cache local).
- `:core:data` — repositórios, `ConexaoTempoReal`, `SyncManager`.
- `:core:ui` — tema (cores do FMX: `docs/design/cores.md`), componentes, `UiState`.
- `:core:media` — mídia: `PlayerAudio` (Media3, um áudio por vez) e `GravadorAudio` (microfone em AAC/M4A, trechos juntados por `juntarTrechos`).
- `:core:webrtc` — mídia da chamada: `MidiaChamada` e a implementação `MidiaWebRtc` (libwebrtc; publica por WHIP e assina por WHEP no MediaMTX; `ClienteWhipWhep` no OkHttp do app).
- `:core:testing` — regras de teste e fixtures JSON do contrato.
- `:feature:*` — uma por área (hoje: `auth` — servidor, login, cadastro; `conversas` — lista, nova conversa, grupos, membros; `chat` — a conversa aberta; `chamada` — o `GerenciadorChamadas`, dono da chamada, a `ChamadaActivity`, os botões de ligar e a integração com o sistema: toque, notificações, serviço e Core-Telecom; `atividades` — a aba Atividades; `pesquisa` — a pesquisa em todos os chats; `config` — o perfil e, no 8.5, as configurações).
- Convention plugins em `build-logic/`. O app antigo está em `app-legado/`, fora do build.
- **Cores:** só as de `docs/design/cores.md` (vêm do `conversa-windows-fmx`), nunca as do web.

## Convenções

- **Idioma:** português do Brasil em código de domínio, textos de UI, commits e documentação, como nos repositórios irmãos.
- **Commits:** mensagem no imperativo/descritiva em português (ex.: "Corrige …", "Adiciona …"); prefixos `feat:`, `fix:`, `chore:`, `docs:` são aceitos.
- **Contrato:** nunca deduza formatos do código legado. Consulte o doc 01 e, em caso de dúvida, o código do servidor (`../conversa/src`) e o cliente web (`../conversa-web/src`).
- **Segurança:**
  - nada de `TrustManager` que aceita tudo;
  - nunca registrar em log token, senha ou conteúdo de mensagem;
  - nunca guardar a senha do usuário.
- **Textos de UI:** sempre em `strings.xml`. Quando existir no web, use o mesmo texto (doc 03 §6). Os nomes são globais no APK: um nome repetido em dois módulos com textos diferentes faz um módulo mostrar o texto do outro. Confira com `node ferramentas/textos-repetidos.mjs`.
- **Testes:** parsers, mapeadores e a máquina de estados de chamada sempre com teste unitário.
- **Regex:** o Android usa o regex do **ICU**, não o da JVM dos testes unitários. Um padrão aceito nos testes pode fechar o app (aconteceu com `(?U)`). Evite flags embutidas e classes de propriedade Unicode; não use `\w` (no ICU aceita acentos; use `[A-Za-z0-9_]`). Toda função nova com regex entra no `app/src/androidTest/.../RegexNoAndroidTest.kt`.
- **Campos de texto:** o valor do `TextField` fica em estado local (síncrono). Passar pelo `combine`/`stateIn` do ViewModel atrasa um quadro e o cursor pula.
