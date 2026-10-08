# TODO — Android Conversa (nova base)

> **Como usar:** cada linha é **uma ação concreta**, pequena (de 5 min a 2 h), que dá para marcar como feita.
> **Ao concluir:** marcar `[x]` e, quando houver, anotar o commit ao lado (`✔ abc1234`).
> As linhas **Teste:** provam que a seção terminou. Só passe para a seção seguinte com elas marcadas.
> **Referências:**
> - `FC-xxx` = item da fila (`docs/auditoria-2026-10/06-fila-de-correcoes.md`)
> - `#n` = problema da auditoria do app (`04`)
> - `§x` = contrato do servidor (`01`)
> - `AUT-01` etc. = funcionalidade do web (`03`/`05`)
> - `Sn` = pendência do servidor (`08`)
>
> **Ordem:** seguir as etapas na ordem. Dentro de cada etapa, a ordem das linhas é a sugerida.

---

## Etapa 0 — Preparação do repositório

> Executada em 2026-10-06. Detalhe em `docs/historico/2026-10-06-05-etapa-0-repositorio.md`.

### 0.1 Salvar tudo que existe (FC-002)
- [x] Conferir em que branch estou: `git branch --show-current` (era `novo`)
- [x] Ver o que está pendente: `git status` → o trabalho de abril já estava commitado pelo usuário em `bb22aa2` ("atualizacao"); não precisou de commit "wip"
- [x] Criar a branch de preservação em `bb22aa2`: `git branch legado/abril-2026 HEAD`
- [x] Criar a pasta: `mkdir -p docs/legado/stashes`
- [x] Listar os stashes: `git stash list` (11)
- [x] Guardar cada stash sem perda numa tag: `git tag legado/stash-NN stash@{N}` (00…10)
- [x] Exportar cada stash como patch de texto, sem `.gradle/`, `.idea/` e `build/`: `docs/legado/stashes/NN.patch`
- [x] Ler o `commit.md` do `stash@{0}` (rascunhos de mensagens de commit de abril) e salvar em `docs/legado/commit-md-stash00.md`
- [x] Conferir os patches: sem senhas nem chaves
- [x] **Teste:** 11 patches na pasta e 11 tags `legado/stash-*`

### 0.2 Publicar (FC-003)
- [x] Criar a tag anotada: `git tag -a legado-v1-final legado/abril-2026`
- [ ] Enviar `novo`, `legado/abril-2026`, `legado-v1-final` e as tags dos stashes ⛔ o push foi bloqueado pela permissão automática do Claude Code; o usuário precisa rodar:
  `git push origin novo legado/abril-2026 legado-v1-final` e `git push origin "refs/tags/legado/stash-*"`
- [ ] **Teste:** branch e tags aparecem no GitHub

### 0.3 Gradle wrapper e limpeza (FC-004, FC-005)
- [x] No `.gitignore`, logo abaixo de `*.jar`, adicionar `!gradle/wrapper/gradle-wrapper.jar` ✔ 855e72d
- [x] Versionar o jar ✔ 855e72d
- [x] Remover os temporários (`tmpclaude-*-cwd`, `temp_function.kt`) ✔ 855e72d
- [x] Remover os `.OLD`/`.REMOVIDO` de `ui/chamada/` ✔ 855e72d
- [x] Remover o relatório de build versionado (`build/reports`) ✔ 855e72d
- [x] Parar de versionar `local.properties` e `.claude/settings.local.json` (os arquivos locais continuam) ✔ 855e72d
- [x] Conferir o `.gitignore` (`local.properties`, `.claude/settings.local.json`, `tmpclaude-*`) ✔ 855e72d
- [ ] Apagar do disco `conversa-android-kotlin (2).rar` (36 MB, não versionado) ⛔ é um backup local; apagar é irreversível, fica a critério do usuário
- [x] **Teste:** `./gradlew --version` roda com o JBR do Android Studio (Gradle 9.1.0, JVM 21)
- [ ] **Teste:** clone limpo + `gradlew assembleDebug` (fazer depois do push)

### 0.4 Documentação (FC-006, FC-007, FC-001)
- [x] Mover `legado/*.md` para `docs/legado/` (`git mv`)
- [x] Mover `documentacao-oficial/*.md` para `docs/legado/documentacao-oficial/`
- [x] Mover `legado/chamada-tcp/` para `docs/legado/chamada-tcp/`
- [x] Apagar `legado/erros.md` (vazio)
- [x] Criar `docs/legado/LEIA.md` (aviso de desatualizado + mapa dos stashes e de onde está o código legado)
- [x] Reescrever o `README.md`: o que é o app, "em reescrita", links para os docs
- [x] README → "Como compilar" (JDK 17+, `JAVA_HOME` do Android Studio, Git Bash e PowerShell)
- [x] README → "Servidor de desenvolvimento" (porta 443, `/api/` `/ws/` `/webrtc/` `/storage/`, IP da rede, CA do mkcert)
- [x] Apagar o `build_completo.bat` e o `rebuild_project.bat`
- [x] Escrever `docs/adr/0001-nova-base.md`
- [x] Criar o `CLAUDE.md` (onde está cada coisa, regra do histórico, TODO, build, convenções)
- [x] Criar `docs/historico/` (`indice.md` por fluxo, `_modelo.md`, entradas 01–05 do dia)
- [x] Commitar: `docs: organiza documentação, registra a nova base e cria o histórico` ✔ 87ba4b5

### 0.5 Spike de mídia, para provar a chamada antes de investir (doc 07 §9) — ⛔ precisa do servidor de dev rodando e de um aparelho/emulador
- [ ] Criar o projeto descartável `spike-midia` (fora do app, Compose vazio)
- [ ] Adicionar OkHttp e `io.github.webrtc-sdk:android` (versão mais nova)
- [ ] Exportar a CA do mkcert do servidor de dev (`mkcert -CAROOT` → `rootCA.pem`)
- [ ] Copiar a CA para `res/raw/mkcert_ca.pem`
- [ ] Criar `res/xml/network_security_config.xml` com `<debug-overrides>` confiando em `@raw/mkcert_ca`
- [ ] Botão "Login": `POST /api/login {login, senha}`; logar o `id` e **só o tamanho** do token
- [ ] Abrir o WS em `wss://<host>/ws/` (com a barra final) e enviar `{"tipo":1,"token":"…"}`
- [ ] Mandar uma mensagem pelo web para esse usuário; conferir no log o evento `tipo:2`
- [ ] Chamar `GET /api/ice`; logar quantos servidores e as `urls` (a resposta é em camelCase)
- [ ] No web, ligar (áudio) para o usuário do spike; pegar o `chamada_id` do evento 51
- [ ] Spike: `POST /api/chamada/entrar {id}`
- [ ] Criar a PeerConnection com os `iceServers` do `/ice` e uma trilha de microfone (`SEND_ONLY`)
- [ ] Esperar o ICE gathering (`GATHER_ONCE`, até 5 s) e mandar o SDP completo
- [ ] Publicar: `POST /webrtc/call-<chamada>-u-<eu>/whip` com `Content-Type: application/sdp`
- [ ] Assistir: `POST /webrtc/call-<chamada>-u-<id do web>/whep` (`RECV_ONLY`), com retry em 404
- [ ] **Teste:** áudio nos dois sentidos, celular ↔ web, **em 4G** (Wi-Fi desligado)
- [ ] Sair: `POST /api/chamada/sair {id}`
- [ ] Repetir com vídeo e `setCodecPreferences` H264 → VP9 → VP8
- [ ] **Teste:** a gravação aparece no servidor (pasta de gravações do MediaMTX)
- [ ] Anotar: tempo até conectar, latência, falhas
- [ ] Escrever `docs/adr/0002-midia-webrtc.md` com o resultado

### 0.6 Pedidos ao servidor (abrir no repo `conversa`; detalhes no doc 08) — ⛔ abrir issues no GitHub depende de aprovação do usuário
- [ ] S1 — push de chamada (data-only, prioridade alta, TTL ~30 s) + push de "chamada encerrada"
- [ ] S2 — `android.priority: high` no push de mensagem; push por aparelho, não por usuário; vários dispositivos ativos
- [ ] S3 — `conversa_id`, `mensagem_id` e `remetente_id` no evento WS 2 e no push
- [ ] S4 — conferir a participação em `GET /anexo`; autenticação no MediaMTX; checar o acesso em `mensagem_referencia`
- [ ] S13 — refresh token
- [ ] S7, S8, S9, S11 — erros 409/400 amigáveis, criação de conversa atômica, visualizar em lote, `tipo` da chamada
- [ ] S5, S6, S10, S12, S14 — figurinhas no build, `DELETE /conversa`, `/recursos`, esquemas de resposta no OpenAPI, rate limit

---

## Etapa 1 — Fundação do app novo

> Executada em 2026-10-07 na branch `reescrita` (commit `2127356`). Detalhe em `docs/historico/2026-10-07-02-etapa-1-fundacao.md`. Itens com ⛔ dependem de aparelho, do servidor rodando ou do push.

### 1.1 Projeto (FC-100)
- [x] Criar a branch `reescrita` a partir de `novo`
- [x] Renomear o `app/` atual para `app-legado/` e tirá-lo do `settings.gradle.kts`
- [x] Criar o módulo `app/` novo (Empty Compose Activity) — `MainActivity` Compose + splash + navegação
- [x] `applicationId = "com.conversa.conversa"` (atualiza por cima do instalado) e `namespace = "com.conversa.app"`
- [x] `compileSdk 36`, `targetSdk 36`, `minSdk 28` — **compileSdk 37**: as bibliotecas estáveis atuais (Compose 1.12, core 1.19, OkHttp 5.5, Coil 3.6) exigem; o AGP instalou a plataforma 37.0 no SDK
- [x] `jvmToolchain(17)` — feito com `compileOptions`/`jvmTarget` 17 (sem toolchain: a máquina só tem o JDK 21)
- [x] Limpar o `gradle.properties`: remover `android.builtInKotlin=false`, `android.newDsl=false` e as demais flags depreciadas
- [x] Pôr **todas** as dependências e plugins no `gradle/libs.versions.toml` — AGP 9.4.1, Kotlin 2.4.20, KSP 2.3.12, Hilt 2.60.1, Gradle 9.8.0
- [x] Atualizar o Kotlin, o Compose BOM, o AndroidX e as coroutines para as versões estáveis mais novas — Compose BOM 2026.09.00, coroutines 1.11.0
- [x] **Teste:** `gradlew assembleDebug` passa — `BUILD SUCCESSFUL`, APK debug gerado (2026-10-07)

### 1.2 Módulos (FC-101)
- [x] Criar `:core:model` (Kotlin puro)
- [x] Criar `:core:network`
- [x] Criar `:core:database`
- [x] Criar `:core:datastore`
- [x] Criar `:core:data`
- [x] Criar `:core:ui`
- [x] Criar `:core:media` — criado na 4.5 (ver etapa 4) ✔ 1ceaad0
- [ ] Criar `:core:webrtc` — ⏭ adiado: será criado quando a etapa 6 começar
- [x] Criar `:core:testing`
- [ ] Criar `:feature:auth`, `:feature:conversas`, `:feature:chat`, `:feature:chamada`, `:feature:atividades`, `:feature:pesquisa`, `:feature:config` — ⏭ `:feature:auth` criado; os demais têm uma linha no início da etapa onde são usados
- [x] Criar convention plugins em `build-logic/` (android-library, compose, hilt, feature) — `Plugins.kt`: application, library, compose, hilt, feature, jvm
- [x] **Teste:** o build passa e não há dependência circular — o build com todos os módulos passou

### 1.3 CI e qualidade (FC-102)
- [x] Adicionar ktlint (ou spotless) e detekt com configuração inicial — ktlint (estilo `android_studio`, `.editorconfig`) passando em todos os módulos; **detekt adiado**: a versão estável (1.23) não roda com Kotlin 2.4
- [x] Ativar o Android Lint com `warningsAsErrors` só para as categorias de segurança — `app/lint.xml`: problemas de segurança viram erro; resultado "No issues found"
- [x] Criar `.github/workflows/android.yml`: checkout → JDK 17 → cache do Gradle → `assembleDebug lint detekt test` — `.github/workflows/android.yml` (instala a plataforma 37; ktlint → testes → lint → APK)
- [x] Publicar o APK de debug como artefato do workflow
- [ ] **Teste:** abrir um PR e ver o check verde — ⛔ depende do push para o GitHub (bloqueado aqui; ver etapa 0.2)

### 1.4 Hilt e padrão de telas (FC-103)
- [x] Adicionar o Hilt e criar a `ConversaApplication` com `@HiltAndroidApp` — `ConversaApplication` (Timber só no debug; Coil com o OkHttp do app)
- [x] Criar a `MainActivity` (`@AndroidEntryPoint`) com `enableEdgeToEdge()` — com splash (`core-splashscreen`)
- [x] Criar `UiState<T>` (`Carregando` / `Conteudo` / `Vazio` / `Erro`) em `:core:ui` — `core/ui/estado/UiState.kt`
- [x] Criar a base de ViewModel com `StateFlow<UiState>` + `Channel` de eventos únicos — `EventosUnicos` (Channel) + `ColetarEventos`
- [x] Criar uma tela de exemplo + teste de ViewModel com `kotlinx-coroutines-test` e Turbine — tela "Servidor" + `ServidorViewModelTest` (6 testes, mockk + Turbine)
- [x] **Teste:** `gradlew test` passa — 65 testes, 0 falhas (rodado 3× seguidas)

### 1.5 Design system (FC-104)
- [x] Tema Material 3: cores claras/escuras (partir das cores do web), tipografia, formas — **cores do conversa-windows-fmx** (pedido do usuário; `docs/design/cores.md`), não do web; escuro é proposta e fica desligado
- [x] Componente `Avatar(url, nome)` com fallback para a inicial — primeiro grafema, que funciona com emoji (GER-07) — `Avatar` + `inicialDoNome` (BreakIterator)
- [x] Componente `ConfirmDialog` com variante "perigo" (foco em Cancelar) (GER-03) — `DialogoConfirmacao` e `DialogoAviso`
- [x] `SnackbarHost` global para erros (GER-01) — `AreaDeAvisos` + `LocalAvisos` na raiz da `MainActivity`
- [x] Componentes `EstadoVazio`, `EstadoErro` (com "Tentar de novo") e `Carregando` — `Carregando`, `EstadoVazio`, `EstadoErro`
- [x] Ícones: adicionar `material-icons-extended`, ou vetores próprios — `material-icons-extended`
- [x] Back preditivo: `android:enableOnBackInvokedCallback="true"` e `BackHandler` onde precisar — `enableOnBackInvokedCallback` no manifest; `BackHandler` entra quando alguma tela precisar
- [x] `strings.xml` como regra: nenhum texto literal na UI — regra no `CLAUDE.md`; todo texto de tela está em `strings.xml`
- [x] Previews de todos os componentes (claro e escuro) — previews em todos os componentes e na tela Servidor; escuro no Avatar (o escuro é proposta)

### 1.6 Configuração do servidor (FC-105)
- [x] `ServerConfig(base)` com `api`, `ws` (`wss://host[:porta]/ws/`) e `webrtc` derivados
- [x] Teste unitário: `https://x` → `wss://x/ws/`; `http://x:8080` → `ws://x:8080/ws/`; barra final sempre presente — `ServerConfigTest` (8 testes)
- [x] Guardar a `base` no DataStore e expor um `StateFlow<ServerConfig?>` — `PreferenciasStore` + `ServidorRepositorio.atual`
- [x] Interceptor do OkHttp que troca o host pela configuração atual (não recriar o Retrofit) — `EnderecoInterceptor` (base fictícia `conversa.invalid`)
- [x] Tela "Servidor": campo URL, botão "Testar conexão" (espera 401 numa rota protegida = servidor OK), "Salvar" — `ServidorTela` + `ServidorViewModel`: testar espera 401 com `{error}` em `/api/usuario/permissoes`; salvar sempre testa antes
- [x] Mostrar o erro amigável: certificado inválido, host inacessível, não é um servidor Conversa — certificado, host não encontrado, indisponível, não é Conversa, sem conexão
- [x] **Teste:** trocar a URL em tempo de execução muda REST, WS e WebRTC sem reiniciar o app — REST: teste `trocar o servidor vale na proxima requisicao`; WS: `ConexaoTempoReal` reabre ao mudar o servidor; WebRTC lê a mesma config (etapa 6)

### 1.7 TLS (FC-106)
- [x] `res/xml/network_security_config.xml`: `cleartextTrafficPermitted="false"`
- [x] `<debug-overrides>` com `@raw/mkcert_ca` (só no debug) — **mudou**: em vez de embutir `@raw/mkcert_ca`, o debug confia nas CAs instaladas pelo usuário (`certificates src=user`) — nada de certificado no repositório; instruções no XML e no README
- [x] Referenciar no manifest (`android:networkSecurityConfig`)
- [ ] **Teste:** debug conecta ao servidor de dev com mkcert; release recusa certificado inválido — ⛔ instalar a CA do mkcert altera a segurança do aparelho: fica para a pessoa fazer num aparelho de teste. Enquanto isso, o emulador usa a API sem TLS só em `localhost` (debug; `docs/desenvolvimento/emulador.md`)
- [x] Garantir que não existe `TrustManager` "aceita tudo" em lugar nenhum (busca no código) — busca por `TrustManager`/`hostnameVerifier`/`sslSocketFactory` no código novo: nada; lint de segurança como erro

### 1.8 Rede (FC-107, FC-109)
- [x] Prover um **único** `OkHttpClient` via Hilt (timeouts, `pingInterval(20s)`)
- [x] `HttpLoggingInterceptor`: `BODY` só em `BuildConfig.DEBUG`, com `redactHeader("Authorization")` — nível `BASIC` no debug (`BODY` exporia conteúdo de mensagens), `NONE` no release
- [x] `AuthInterceptor`: adiciona `Authorization: Bearer <token>` quando há sessão — `AutenticacaoInterceptor`; rotas públicas marcadas com cabeçalho e não disparam sessão expirada
- [x] 🆕 O token só vai para a API do servidor configurado (`<base>/api/`): nunca para as URLs assinadas do MinIO (que recusam duas autenticações), imagens ou outros endereços — achado na etapa 4 (+ teste no `RedeTest`)
- [x] Retrofit + converter kotlinx.serialization (`ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`)
- [x] Ler o erro padrão do servidor: corpo `{error: string}` (§3)
- [x] Mapear 401 → evento global `SessaoExpirada` — `EventosSessao.sessaoExpirada`
- [x] Mapear resposta HTML (502/504 do nginx) → "Servidor indisponível"
- [x] Mapear 400/403/404/429/500 → mensagens amigáveis (manter a original no log de debug) — `ErroApi` + `mensagemAmigavel()`
- [x] Testes com MockWebServer: sucesso, 401, `{error}`, HTML, timeout — `RedeTest` (12 testes): sucesso, query, troca de servidor, 401 de sessão, 401 do login, `{error}`, 500, HTML 502, 503 de limite, sem servidor, tempo esgotado, JSON inválido

### 1.9 Modelos do contrato (FC-108)
- [x] Baixar o OpenAPI do servidor (`/api/docs/json`) para `docs/contrato/openapi.json`, como referência das entradas de cada rota — baixado em 2026-10-07 do servidor de dev (servidor `8031fa5`): 68 operações = as 67 rotas REST da `ConversaApi` (método e caminho conferidos um a um) + `/ws/`
- [x] Copiar JSONs reais de `conversa/tests/*.test.ts` para `:core:testing/fixtures/` — fixtures montadas a partir dos exemplos do doc 01 (os testes do servidor são TypeScript, não JSON)
- [x] Serializer de data: ISO-8601 com `Z` → `Instant`, tolerante (aceita sem `Z`, aceita espaço)
- [x] Serializer de id tolerante (número ou string → `Long`)
- [x] DTO login: `{id, nome, email, telefone, avatar_identificador, dispositivo{…}, token}`
- [x] DTO conversa (§9 do doc 01 / CON-01)
- [x] DTO mensagem (§10.2): `excluida_em`, `visivel_em`, `mensagem_referencia` (recursivo, opcional), `reacoes` (opcional), conteúdos com `transcricao_status`/`transcricao`
- [x] DTO conteúdo de chamada (tipo 6), com datas fora do padrão (§9.8)
- [x] DTOs de status (`/mensagem/status` = booleanos; `/mensagem/status/detalhe` = datas)
- [x] DTOs de anexo (`PUT /anexo` nos dois formatos de resposta; `GET /anexo` → `{url}`; `/anexos`)
- [x] DTOs de chamada (`dados da chamada`, `pendentes` **sem `usuarios`**, histórico `ChamadaHistoricoItem`)
- [x] DTO ICE (camelCase: `urls`, `username`, `credential`)
- [x] DTOs de atividade, permissões, parâmetros, SIP, contatos, membros
- [x] DTO `Enquete` (`opcoes[].votantes[]`, `total_votantes`, `meus_votos`) e resposta do `PUT /enquete` (mensagem + `enquete_id`)
- [x] Mapeadores DTO → modelo de domínio em `:core:model` — ficaram em `:core:network` (`dto/Mapeadores.kt`): o `:core:model` não conhece o servidor
- [x] Um teste de desserialização por DTO, usando as fixtures — `DesserializacaoTest` (12) + `ConversaJsonTest`

### 1.10 Banco local (FC-110)
- [x] Entidades Room: `ConversaEntity`, `MensagemEntity`, `ConteudoEntity`, `ReacaoEntity`, `ContatoEntity`, `AtividadeEntity`, `ChamadaHistoricoEntity`, `EnvioPendenteEntity`, `SyncEstadoEntity` (cursor `ate`) — nomes em português: `ConversaEntidade` etc.
- [x] DAOs com `Flow` para a UI
- [ ] Exportar o schema (`room.schemaLocation`) e criar o teste de migração — ⏭ schema da versão 1 exportado em `core/database/schemas`; o teste de migração nasce junto com a versão 2 (não há o que migrar ainda)
- [x] **Teste:** inserir e ler cada entidade — `BancoTest` (Robolectric, Room em memória): todas as entidades

### 1.11 Sessão segura (FC-111)
- [x] `SessaoStore`: token, `usuario_id`, nome e `dispositivo_id`, cifrados (Tink/Keystore)
- [x] **Nunca** salvar a senha
- [x] `allowBackup="false"` (ou `data_extraction_rules` excluindo a sessão) — `allowBackup=false`, `fullBackupContent=false` e `regras_extracao.xml` excluindo tudo
- [x] `SessaoRepository` com `StateFlow<Sessao?>` — `SessaoRepositorio` (também encerra a sessão em qualquer 401)
- [x] **Teste:** matar o app, reabrir e a sessão continua; um backup não contém o token — `SessaoStoreTest` + `allowBackup=false`/regras de extração vazias; no emulador (2026-10-07): `am force-stop` e reabrir → entra direto na lista

### 1.12 WebSocket — `RealtimeClient` (FC-112)
- [x] Sealed class `EventoSocket` com todos os tipos: 0, 2, 3, 4, 5, 7, 9, 40, 51–57, 60, 61, 62 (`EnqueteAtualizada {enquete_id, conversa_id}`) e `Desconhecido`
- [x] Parser JSON → `EventoSocket` (testes com um payload real de cada tipo, §6.6) — 🔄 parser feito, testes a seguir — `EventoSocketParserTest` (10 testes, todos os tipos)
- [x] Máquina de estados: `Desconectado` / `Conectando` / `Autenticando` / `Conectado` / `Aguardando` — estados `DESCONECTADO`, `CONECTANDO`, `CONECTADO`, `AGUARDANDO` (sem "autenticando": o servidor não confirma o login)
- [x] No `onOpen`: enviar `{"tipo":1,"token":…}` (sem resposta de sucesso; erro vem como 0/9)
- [x] `SharedFlow<EventoSocket>` multi-assinante (`extraBufferCapacity = 64`) — buffer de 256
- [x] Id de geração por conexão; ignorar callbacks de sockets antigos
- [x] Backoff com jitter: 1 s → 2 → 4 → … → 30 s
- [x] Conectar só com sessão válida **e** app em primeiro plano (`ProcessLifecycleOwner`) **ou** chamada ativa — `ConexaoTempoReal` + `MonitorPrimeiroPlano`; a flag `chamadaAtiva` será ligada na etapa 6
- [x] Desconectar ao ir para segundo plano (com uma pequena tolerância, ex.: 10 s) — tolerância de 10 s
- [x] `enviarSinal(chamadaId, dados)` → `{tipo:57, chamada_id, dados}`
- [x] Nenhum log de frame com token ou conteúdo
- [x] **Teste:** derrubar o Wi-Fi → reconecta sozinho; nunca há 2 sockets abertos — `RealtimeClientTest`; no emulador (2026-10-07) derrubando a **API** (`docker stop api`, sem mexer na rede do aparelho): tentativas em 2/4/8 s, faixa após 5 s, reconectou sozinho ao voltar e a faixa sumiu

### 1.13 Sincronização (FC-113)
- [x] `SyncManager.ressincronizar()` disparado ao conectar, ao voltar ao primeiro plano e ao receber push — `SyncManager.ressincronizar()` ao conectar (cobre a volta ao primeiro plano); a chamada pelo push entra na etapa 5
- [x] `GET /mensagens/novas?desde=<cursor>` → buscar as mensagens que faltam por conversa → Room → salvar `ate` — busca a partir da última mensagem salva (100) ou as 80 últimas
- [x] `GET /conversas` → Room
- [x] `GET /contatos/online` → presença — `SyncManager.online` + WS 60
- [x] `GET /atividades/novas` → badge — `SyncManager.atividadesNovas` + WS 61
- [x] `GET /chamadas/pendentes` → `CallManager` — `SyncManager.chamadasPendentes` (o `CallManager` consome na etapa 6)
- [x] Tratar o WS 2 (sem `conversa_id`, pode chegar duplicado) como gatilho de sincronização incremental, com debounce — debounce de 300 ms
- [ ] **Teste:** desligar a rede por 2 min, mandar mensagens pelo web e religar → tudo aparece, sem duplicar — ⛔ precisa do servidor e de aparelho; a lógica tem `SyncManagerTest` (4 testes: cursor, falha parcial, eventos, debounce)

---

## Etapa 2 — Sessão, conversas, contatos e presença

> Executada em 2026-10-07 na branch `reescrita`. Detalhe em `docs/historico/2026-10-07-03`, `-04` e `-05`. Testada no emulador contra o servidor de dev (`docs/desenvolvimento/emulador.md`). Itens ⛔ dependem das notificações (etapa 5), do web rodando ou do servidor (S15).

- [x] Criar o módulo `:feature:conversas` (adiado da 1.2) — `feature/conversas` (lista, nova conversa, criar grupo, membros)
### 2.1 Login e entrada (FC-200, AUT-01)
- [x] Tela Splash/decisão: sem servidor → Servidor; sem sessão → Login; com sessão → Principal — `MainViewModel` (splash fica até decidir)
- [x] Tela Login: logo, "Usuário", "Senha", botão "Entrar"/"Entrando…", link "Não tem conta? Criar conta" — `LoginTela` (ícone + "Conversa", mostrar/esconder senha, autofill de usuário/senha, último login preenchido, servidor atual com "Trocar")
- [x] `POST /api/login {login, senha, dispositivo_id?}` (reenviar o `dispositivo_id` salvo) — `AutenticacaoRepositorio.entrar`
- [x] Salvar o token e `dispositivo.id`; `trim()` no login — token na sessão cifrada; `dispositivo.id` nas preferências (sobrevive ao logout)
- [x] Tratar 429/503 (limite de 10/min do nginx) → "Muitas tentativas, aguarde" — `ErroApi.MuitasTentativas` → "Muitas tentativas. Aguarde um minuto e tente de novo."
- [x] Tratar resposta sem token → "Resposta de login inválida" — "Resposta de login inválida: token ausente."
- [x] **Teste:** logar, matar o app, abrir → entra direto — no emulador: `am force-stop` e reabrir → entra direto

### 2.2 Dispositivo (FC-201, AUT-04)
- [x] Após o login: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma:"android"}` — feito a cada início de sessão (`IniciadorSessao`), não só no login
- [x] Cortar os textos nos limites (nome/modelo 50, versão 15) para não dar 500 — `AutenticacaoRepositorio.registrarDispositivo` (+ teste)
- [x] **Teste:** conferir a linha do dispositivo no banco — no emulador (2026-10-07): linha `dispositivo` com "Google sdk_gphone64_x86_64" / "Android 16" / "android"

### 2.3 Sessão expirada e logout (FC-202, AUT-03, AUT-05)
- [x] Observar `SessaoExpirada` (401) em qualquer lugar → limpar a sessão → Login com o aviso "Sessão expirada" — `SessaoRepositorio` encerra → `MainViewModel` leva ao login com "Sua sessão expirou. Entre novamente."
- [x] Outros erros na inicialização mantêm a sessão e tentam de novo (como no web) — `IniciadorSessao`: Snackbar "<erro> (tentando de novo…)" e nova tentativa a cada 5 s
- [x] Logout: `PATCH /dispositivo {id, token_fcm:null}` — `AutenticacaoRepositorio.sair` (3 s de limite; sai mesmo sem rede)
- [ ] Logout: fechar o WS e encerrar a chamada ativa — 🔄 o WS fecha na hora sem sessão (`ConexaoTempoReal`); encerrar a chamada ativa entra com o `CallManager` (etapa 6)
- [ ] Logout: limpar o Room, o cache de imagens e de áudio e cancelar todas as notificações — 🔄 Room (`LimpezaSessao`), imagens do Coil e notificações (`ConversaApplication`); cache de áudio entra na etapa 4
- [ ] **Teste:** depois do logout, mensagens do web não geram notificação — ⛔ não há notificações antes da etapa 5; o logout já cancela todas e limpa o `token_fcm` (conferido no banco: nulo)

### 2.4 Cadastro (FC-203, AUT-02)
- [x] Tela "Crie sua conta": Nome, Usuário, E-mail, Senha — `CadastroTela`
- [x] `PUT /api/usuario {nome, login, email, senha}` (rota pública) — `AutenticacaoRepositorio.cadastrar`
- [x] Respeitar os limites (nome 100, login 50, e-mail 100) — o campo para de aceitar texto no limite; validação de e-mail e senha ≥ 4 (como o web)
- [x] E-mail duplicado (500 do Postgres hoje) → "E-mail já cadastrado" — `EmailJaCadastradoException` → "E-mail já cadastrado."
- [x] Sucesso → "Conta criada com sucesso!" → voltar ao Login com o usuário preenchido — volta ao login com o usuário preenchido e o aviso no Snackbar (sem a espera de 1,5 s do web)

### 2.5 Navegação principal
- [ ] NavHost com rotas tipadas: Servidor, Login, Cadastro, Principal, Chat(conversaId, mensagemId?), Membros, Perfil, Visualizador, Configurações… — 🔄 `navegacao/Rotas.kt`: Servidor, Login, Cadastro, Principal, Chat, NovaConversa, CriarGrupo, Membros; Perfil e Visualizador entram nas etapas 4 e 8
- [x] Barra inferior: Conversas, Chamadas, Atividades (com badge), Configurações — `PrincipalTela`; Chamadas e Atividades são provisórias (etapas 6 e 8); badge de atividades novas já funciona; Configurações provisória com Servidor e Sair
- [x] Deep link `conversa://chat/{id}?mensagem={id}` — intent-filter + `MainViewModel.receberLink` (sem sessão, abre depois do login); `LinkConversaTest`

### 2.6 Lista de conversas (FC-204, FC-205, CON-01, CON-13)
- [x] Repositório: Room como fonte; `GET /conversas` para atualizar — `ConversasRepositorio` + `ConversasViewModel`
- [x] Ordem: fixadas por `fixada_ordem`, depois as demais por `mensagem_id` desc — `ordenarConversas` (+ teste)
- [x] Título: `descricao || nome || "Conversa #id"` — `Conversa.titulo`
- [x] Item: avatar, bolinha online (direta), título, etiqueta "Grupo", alfinete se fixada — `LinhaConversa`: avatar com bolinha, título (negrito com não lidas), "Grupo", alfinete
- [x] Prévia: menção `@[Nome](id)` → `@Nome`; bloco de código → `Código (linguagem)`; vazio → "Sem mensagens" — porte de `resumirTexto` do web (`TextoTest`); vazio com mensagem → "Anexo ou chamada" (pendência S16)
- [x] Badge de não lidas (`mensagens_sem_visualizar`), escondido nas arquivadas — contador azul (99+), zerado nas arquivadas
- [x] Hora: hoje `HH:mm`, ontem "Ontem", senão `dd/MM/aa` — `rotuloData` (+ teste)
- [x] Três pontinhos animados quando alguém digita naquela conversa — `IndicadorDigitando` (WS 4, expira em 4 s); testado no emulador com um 2º cliente
- [ ] Atualizar (sem spinner por cima da lista) em WS 2, 3 e 40, ao enviar e ao ler — 🔄 WS 2, 3 e 40 atualizam o Room e a lista acompanha (WS 40 testado no emulador: grupo criado por outro usuário apareceu sozinho); "ao enviar e ao ler" entram na etapa 3
- [x] Pull-to-refresh — `PullToRefreshBox` (conversas + contatos)
- [x] Estados vazio e de erro — carregando (até a 1ª carga), vazio, nada encontrado, erro em tela cheia com "Tentar de novo" (só com cache vazio)
- [ ] **Teste:** comparar lado a lado com o web (8 casos do CON-01) — ⛔ o web não está rodando nesta máquina; as regras do CON-01 têm teste unitário (`ListaConversasTest`, `ConversasViewModelTest`) e foram vistas no emulador

### 2.7 Filtro e "Nova conversa" (FC-206, CON-02)
- [x] Campo de busca: filtra por título e prévia — sem acento e sem maiúscula (`conversaCombina`)
- [x] Com termo, mostrar também as arquivadas (com a etiqueta "Arquivada") — com a etiqueta "Arquivada"
- [x] Seção "Nova conversa" com contatos sem conversa direta (nome/login/e-mail) — também na tela "Nova conversa" (botão lápis)

### 2.8 Menu da conversa, fixar e arquivar (FC-207, FC-208, FC-209)
- [x] Toque longo no item → bottom sheet: Fixar/Desafixar, Arquivar/Desarquivar — `MenuConversa` (+ "Mover para cima/baixo" e "Membros do grupo"); ação de acessibilidade "Mais opções"
- [x] Fixar: adicionar ao fim das fixadas → `PATCH /conversa/fixadas {conversas:[ids na ordem]}` — conferido no banco pelo emulador
- [x] Reordenar fixadas: arrastar (ou "mover para cima/baixo") → mesmo PATCH com a lista inteira — "Mover para cima/baixo" no menu (arrastar fica para depois); conferido no banco
- [x] Otimista; erro → recarregar `GET /conversas` e mostrar o aviso — `ConversasRepositorioTest`: erro → recarrega; aviso no Snackbar
- [ ] Arquivar: `PATCH /conversa/arquivada {conversa, arquivada:true}` → tirar das fixadas, cancelar a notificação — 🔄 arquivar desfixa e reordena as outras (conferido no banco); cancelar a notificação entra na etapa 5
- [x] Seção recolhível "Arquivadas (N)" no fim da lista
- [ ] Arquivada: sem som e sem notificação — ⛔ notificações são da etapa 5

### 2.9 Contatos e conversa direta (FC-210, CON-06, CON-11)
- [x] Tela/aba Contatos: `GET /usuario/contatos`, busca local — tela "Nova conversa" (lápis na lista): contatos, filtro e "Novo grupo"; atualiza ao abrir
- [x] Avatar: usar o `avatar_url` do contato ou, na falta, o da conversa direta — `avatarDoContato`
- [x] "Obter ou criar direta": procurar `tipo=1 && destinatario_id=contato` — `diretaCom`
- [x] Se não existe: `PUT /conversa {descricao:"", tipo:1}`
- [x] → `PUT /conversa/usuario {conversa_id, usuario_id:eu}`
- [x] → `PUT /conversa/usuario {conversa_id, usuario_id:contato}` — ordem conferida em `ConversasRepositorioTest`; no emulador a conversa nasceu com os 2 membros
- [x] → `GET /conversas` → abrir o chat — abre o chat (provisório até a etapa 3)
- [x] Falha no meio → mostrar o erro (e registrar a conversa órfã no log); depende de S8 para ficar atômico — erro no Snackbar; `Timber.w` com o id da conversa incompleta

### 2.10 Grupos (FC-211, FC-212, CON-07, CON-08)
- [x] Tela "Criar grupo": "Nome do grupo", busca, checkboxes de contatos — `CriarGrupo.kt`
- [x] Validações: "Informe o nome do grupo." / "Selecione ao menos um usuário." — `CriarGrupoViewModelTest` + emulador
- [x] `PUT /conversa {descricao, tipo:2}` → `PUT /conversa/usuario` para cada membro **e para mim** → abrir o grupo — em paralelo, sem duplicar; testado no emulador
- [x] Tela "Membros do grupo": `GET /conversa/usuarios?conversa=` (id = `conversa_usuario_id`) — `Membros.kt`; lista vazia = "Você não faz parte deste grupo."
- [x] Renomear: `PATCH /conversa {id, descricao}` → "Grupo renomeado com sucesso." — testado no emulador (conferido no banco)
- [x] Adicionar: seletor de contatos fora do grupo → `PUT /conversa/usuario` — seletor em bottom sheet com quem está fora do grupo
- [ ] Remover (não para mim mesmo): `DELETE /conversa/usuario?id=<conversa_usuario_id>` — ⛔ o servidor só aceita remover o próprio vínculo (403 para os outros; o botão do web sempre falha). Não oferecido; a tela explica. Pendência S15
- [x] Sair do grupo: `DELETE /conversa/usuario?id=<meu conversa_usuario_id>` — com confirmação de perigo; volta à lista

### 2.11 Presença e conexão (FC-213, FC-214, PRE-01, GER-02)
- [x] `PresencaRepository`: conjunto de ids online a partir de `GET /contatos/online` + WS 60 `{usuario_id, online}` — `PresencaRepositorio` (+ teste)
- [x] Bolinha verde na lista, no cabeçalho do chat (direta) e nos contatos — lista, cabeçalho do chat e contatos ("Nova conversa")
- [x] Banner "Sem conexão em tempo real", depois de 5 s desconectado, com "Tentar agora" — `FaixaSemConexao` + `ConexaoTempoReal.semTempoReal` (+ `ConexaoTempoRealTest`); texto do web
- [x] Atualização periódica de 8 s (mensagens novas + chamadas pendentes) só enquanto o socket está fora, como o web (§1.4 do doc 03) — `ConexaoTempoReal` → `SyncManager.atualizacaoPeriodica` (+ teste)
- [x] **Teste:** abrir e fechar o web com outro usuário → a bolinha acende e apaga no celular — no emulador com um 2º cliente (Node, conta B): bolinha e "digitando" acendem e apagam

---

## Etapa 3 — Mensagens (núcleo)

> Executada em 2026-10-07 na branch `reescrita` (`docs/historico/2026-10-07-06` e `-07`), testada no emulador contra o servidor de dev. Os itens abertos dependem das etapas 4 (anexos, microfone), 6 (chamadas), 7 (menu, salto para mensagem) e 8 (perfil, pesquisa).

- [x] Criar o módulo `:feature:chat` (adiado da 1.2) — `feature/chat` (`ChatViewModel`, `ChatTela`, `Bolhas`)
### 3.1 Tela de chat (FC-300, CON-09)
- [ ] Cabeçalho: voltar, avatar (toque → perfil do outro), título, bolinha online — 🔄 voltar, avatar com bolinha, título; o toque no avatar abre o perfil na etapa 8 (AUT-10)
- [x] Subtítulo: direta → "online"/nada; grupo → nomes dos membros; digitando/gravando substitui — digitando/gravando com os textos do web; direta "online"; grupo com os nomes dos membros
- [ ] Ações do topo: chamada de voz, chamada de vídeo, pesquisa, membros (grupo) — 🔄 voz e vídeo (avisam "As chamadas chegam na etapa 6") e membros no grupo; a pesquisa entra na etapa 8
- [x] Lista: `LazyColumn` com `reverseLayout` — itens ao contrário, a mais nova é o item 0
- [ ] Campo de mensagem + botão Enviar/Microfone + botão de anexo — 🔄 campo + Enviar; microfone e anexo entram na etapa 4

### 3.2 Carregar e paginar (FC-301, MSG-01)
- [x] Abrir: `GET /mensagens?conversa=X&mensagemreferencia=0&mensagensprevias=80&mensagensseguintes=0` (máximo de 100 por chamada) — `MensagensRepositorio.carregarRecentes` (+ `MensagensTest`)
- [x] Mostrar o que está no Room antes da rede — o Room aparece na hora; a rede só acrescenta
- [x] Rolar para cima: `mensagemreferencia=<mais antiga>&mensagensprevias=60`; parar quando vier vazio — `carregarAnteriores`: descarta a de referência (o servidor a inclui); 0 = começo (+ teste)
- [x] Rolar para baixo (depois de um salto): `mensagemreferencia=<mais nova>&mensagensseguintes=60` — `carregarSeguintes` (mesma regra)
- [x] Ordem: `coalesce(visivel_em, inserida)`, depois `id`; ids negativos (enviando) no fim — `ordenarMensagens` em `core/model/Chat.kt` (+ `ChatTest`)
- [ ] Indicadores "Carregando mensagens anteriores/seguintes" — 🔄 "Carregando mensagens anteriores…" no topo; o de "seguintes" entra com o salto para uma mensagem (etapa 7)
- [x] **Teste:** rolar até o início de uma conversa com 1000+ mensagens sem travar — no emulador: conversa com 1050 mensagens, 70 gestos até a "Mensagem número 1" (≈17 páginas de 60), sem fechar

### 3.3 Classificação e bolhas (FC-302, FC-303, MSG-07)
- [x] Portar `classificarMensagem.ts` com a prioridade: oculta > chamada > imagem > enquete (tipo 8, mesmo com referência) > figurinha > código > emoji > com referência > texto curto > padrão — `classificarMensagem` em `core/model/Chat.kt`; "só emojis" por faixas de código (o regex de propriedades Unicode difere entre Android/ICU e JVM)
- [x] Testes unitários copiando os casos do web — `ChatTest` (14 casos, inclusive todos os do `classificarMensagem.test.ts`)
- [x] Bolha padrão (texto multi-linha) e bolha curta (≤ 60 caracteres, uma linha, hora ao lado) — `TextoCurto` (hora ao lado) e `CorpoPadrao` (hora embaixo)
- [x] Bolha só de emojis (fonte grande, sem fundo) (MSG-14) — `BolhaEmoji` (40 sp, sem fundo)
- [x] Placeholders para imagem, figurinha, código e citação (preenchidos nas etapas seguintes) — `Marcador` para imagem/arquivo/áudio/figurinha; código em bloco monoespaçado com a linguagem; citação com o resumo do web
- [x] Placeholder da votação (tipo 8): "📊 Votação" + "Abra no computador para votar" (a bolha nunca fica vazia; a bolha real vem na 7.12) — "📊 Votação" + "Abra no computador para votar"

### 3.4 Mensagem oculta (FC-304, MSG-16)
- [x] `excluida_em != null` → bolha "Mensagem oculta" + hora/status — `BolhaOculta`
- [x] Toque alterna revelar/ocultar o conteúdo original
- [ ] Sem menu e sem reações; fora da galeria de imagens — 🔄 ainda não há menu nem reações (etapa 7 respeita); galeria na etapa 4
- [x] Citação de mensagem oculta → "Mensagem oculta" sem conteúdo — `resumoCitacao` → "Mensagem oculta"

### 3.5 Bolha de chamada (FC-305, MSG-13)
- [x] Ler o JSON do conteúdo tipo 6: `{chamada_id, tipo, status, iniciada, finalizada, duracao, participantes[]}` — `ChamadaConteudoDto` + `lerChamadaDaMensagem` (datas sem fuso; JSON inválido → nulo) + teste com a fixture
- [x] Título: "Chamada de áudio"/"Chamada de vídeo" (+ " em grupo" se > 2) — `BolhaChamada`
- [x] Direta: "<remetente> · mm:ss" ou o status ("Recusada", "Perdida", "Cancelada")
- [x] Grupo: lista de participantes com a duração ou o status
- [x] Cores: verde (encerrada), vermelho (recusada/perdida); sem status de entrega — ícone verde (`chamadaRecebida`) / vermelho (`chamadaPerdida`), cores do FMX; sem status de entrega
- [ ] Toque → ligar de novo (mesmo tipo) — 🔄 toque chama `aoLigar` (hoje só o aviso; liga de verdade na etapa 6)

### 3.6 Separadores e hora (FC-306, MSG-02)
- [x] Separador de dia ("seg., 05/10/2026" ou "Hoje"/"Ontem") — "Hoje"/"Ontem"/"qua., 07/10/2026"; dia pela data efetiva
- [x] Grupo: nome do remetente acima das bolhas recebidas quando muda o remetente — `ItemChat.Bolha.mostrarRemetente` (só o primeiro nome, como o servidor manda)
- [x] Hora `HH:mm` formatada direto do `Instant` (nada de `toString()`, ver #29) — `horaDa` com `DateTimeFormatter` no fuso do aparelho

### 3.7 Enviar texto (FC-307, ENV-01)
- [x] Ao enviar: criar a mensagem otimista com id negativo e `enviando=true` no Room — `EnvioMensagens.enviarTexto` (+ teste)
- [x] Guardar em `EnvioPendente` e disparar o `EnvioWorker` (WorkManager, com rede) — `envio_pendente` + `EnvioWorker` (`@HiltWorker`, rede obrigatória, `APPEND_OR_REPLACE`); WorkManager iniciado pelo app com o Hilt
- [x] Worker: `PUT /mensagem {conversa_id, conteudos:[{ordem:1, tipo:1, conteudo}]}` → trocar o id negativo pelo real — `processarPendentes`: em ordem; troca pela real (`mensagemreferencia=id`) ou, se não vier, grava a otimista com o id real (+ teste)
- [x] Erro definitivo → marcar a mensagem como "falhou" com o botão "Reenviar"/"Apagar" — regra (4xx ou 5 erros do servidor → `falhou`; sem rede não conta) + botões "Reenviar"/"Apagar" embaixo da bolha
- [x] O texto do campo só é limpo depois de salvo no Room (nunca se perde) — o campo só limpa no retorno do `enviar` (depois de gravar no Room), e só se o texto não mudou nesse meio-tempo
- [x] Após enviar: rolar ao fim e atualizar a lista de conversas — `EventoChat.RolarAoFim`; a lista de conversas atualiza ao terminar o envio (`processarPendentes`)
- [x] **Teste:** enviar em modo avião → religar → a mensagem sai sozinha — no emulador, derrubando a API (`docker stop api`): ficou "Enviando" (relógio), saiu sozinha quando a API voltou, virou "Enviada", sem duplicar

### 3.8 Lida e status (FC-308, FC-309, MSG-04, MSG-08, MSG-10)
- [x] Detectar as mensagens visíveis na tela (de outros, não visualizadas, app em primeiro plano) — `snapshotFlow` dos itens visíveis, só com a tela `RESUMED`; o ViewModel filtra (de outros, não lidas, id > 0) (+ teste)
- [x] Para cada uma: `POST /mensagem/visualizar {conversa, mensagem}` (fila serial; trocar por lote quando S9 existir) — `MensagensRepositorio.marcarLida`: fila serial, uma vez por mensagem, tenta de novo se falhar (+ teste)
- [ ] Descontar o contador da conversa (otimista); ao chegar a 0, cancelar a notificação da conversa — 🔄 desconta o contador e marca lida no Room na hora (+ teste); cancelar a notificação entra na etapa 5
- [x] Ícone de status nas minhas mensagens: relógio → ✓ → ✓✓ cinza → ✓✓ cor primária — relógio → ✓ → ✓✓ cinza → ✓✓ azul (`statusLida`); ⚠ vermelho se falhou
- [x] WS 3 `{grupo:conversaId, mensagens:"12,13"}` → `GET /mensagem/status?conversa=&mensagem=12,13` → atualizar `recebida`/`visualizada`/`reproduzida`/`excluida_em` — `SyncManager`; **corrigido**: o status dessa rota é o agregado de todos os destinatários, então só é aplicado nas minhas mensagens (`atualizarStatusDaMinha`); ocultar vale para todas (+ teste no `SyncManagerTest` e no `BancoTest`)
- [x] Se algum id do WS 3 não está carregado e a conversa está aberta → recarregar — `SyncManager`: id do WS 3 fora do cache → busca as seguintes da conversa (sempre, não só com a conversa aberta). Necessário para o resumo de chamada, que só chega por WS 3 (+ teste)
- [x] **Teste:** ler no web → o ✓✓ do celular fica azul sem recarregar — no emulador com um 2º cliente (Node, conta B) lendo as mensagens: ✓✓ ficou azul sozinho

### 3.9 Linha "Últimas", FABs e links (FC-310, FC-311, MSG-03, MSG-05, MSG-11)
- [x] Linha "Últimas" antes da primeira não lida (posição fixada ao abrir; fica até sair da conversa) — decidida uma vez só depois da 1ª carga; não pula enquanto lê (+ teste). **Corrigido no emulador**: a tela se posicionava antes da decisão quando o Room já tinha mensagens
- [x] Ao abrir com não lidas: posicionar a primeira não lida no topo; sem não lidas: ir ao fim — primeira não lida no topo; sem não lidas, o fim
- [x] FAB "ir para o final" quando estiver longe do fim — `SmallFloatingActionButton` quando o 1º visível passa do 6º item
- [x] FAB "Há novas mensagens" quando chegar mensagem com o usuário longe do fim — aparece quando chega mensagem de outra pessoa com a lista longe do fim
- [x] 🆕 Perto do fim, a lista acompanha a mensagem que chega — o `LazyColumn` mantinha na tela o item visível (pela chave) e a nova ficava escondida embaixo, sem aviso; agora rola até ela (achado no emulador com uma mensagem de imagem do B) ✔ 389e66d
- [x] Links clicáveis (`https?://` e `www.`) com as regras de pontuação do web; abrir no navegador — `separarTexto` + `LinkAnnotation.Url` (abre no navegador); menção abre a conversa direta (`LinkAnnotation.Clickable`); testado no emulador (o "." final fica fora do link)

### 3.10 Digitando (FC-312, ENV-15)
- [x] Ao digitar texto não vazio: `POST /conversa/digitando {id: conversaId}`, no máximo 1 a cada 2,5 s (o último é adiado, não descartado) — `ChatViewModel.aoDigitar` (+ teste com tempo virtual)
- [x] Resetar o throttle ao enviar — (+ teste)
- [x] Receber o WS 4 `{conversa_id, usuario_id}`; ignorar o meu; expirar em 4 s; sumir quando chegar mensagem — `PresencaRepositorio` (etapa 2) + `atividadeDaConversa` ignora o meu
- [x] Textos: "Digitando…", "Ana está digitando…", "Ana e Beto estão digitando…", "Ana, Beto e Caio…", "… e outras N pessoas…" — `textoAtividade` com os textos do web (+ plural "outra/outras")
- [x] Quem não é contato: "Usuário #id" — `atividadeDaConversa`

### 3.11 Deep link (FC-314, CON-12)
- [x] Abrir `chat/{id}` pela notificação → **recarregar as mensagens** antes de mostrar — o chat sempre recarrega as recentes ao abrir (`carregarRecentes`), venha de onde vier
- [ ] Com `mensagem={id}` → ir para a mensagem (usa a 6.3) — ⛔ o salto para uma mensagem é da etapa 7 (ações sobre mensagens); hoje abre no lugar padrão

---

## Etapa 4 — Anexos e mídia

- [x] Criar o módulo `:core:media` (adiado da 1.2) — Media3 1.11.1 (`media3-exoplayer`); por enquanto só o player de áudio ✔ 1ceaad0
### 4.1 Upload (FC-400, ANX-02)
- [x] Calcular o SHA-256 em streaming (`DigestInputStream`, sem carregar o arquivo inteiro) — `calcularSha256` (blocos de 64 KB) em `core/data/anexos/AnexosRepositorio.kt` (+ teste)
- [x] `PUT /anexo {identificador, tipo, nome, extensao (≤ 10), tamanho}` — nome ≤ 255, extensão minúscula ≤ 10; acima de 1 GiB nem começa (+ teste)
- [x] Resposta "não existe" → URL assinada de upload (vale 300 s)
- [x] Resposta "já existe" → `id` (string) + URL de download → pular o upload — não sobe nada e guarda a URL devolvida como a de leitura (+ teste)
- [x] `PUT` do arquivo na URL assinada (streaming, `RequestBody` a partir do `ContentResolver`) com progresso — `RequestBody` em fluxo com progresso, sem token (+ teste com MockWebServer conferindo os bytes); no emulador, 2 imagens da galeria subiram pelo proxy com o tamanho exato no MinIO ✔ 389e66d
- [x] `POST /anexo/confirmar?identificador=` (na **query**) — (+ teste)
- [x] URL vencida no meio → pedir de novo — 403 do MinIO → pede outra URL uma vez (+ teste)
- [x] Tudo dentro de um `UploadWorker` (sobrevive a fechar o app) — o mesmo `EnvioWorker` da 3.7 sobe os anexos antes de mandar a mensagem; o identificador de cada anexo é gravado na fila assim que sobe (nova tentativa não sobe de novo) (+ teste)
- [x] **Teste:** um vídeo de 200 MB sobe sem estourar a memória; o mesmo arquivo de novo não sobe — no emulador, 200 MB (`dd`) pelo seletor de documento: subiu inteiro (209.715.200 bytes no servidor) com a memória do app estável (PSS ~116–125 MB durante todo o envio); de novo: só `PUT /anexo` (já existe) e `PUT /mensagem`, nada para o `/storage` ✔ 389e66d

### 4.2 Fila e envio de anexos (FC-401, FC-402, ANX-01, ANX-03)
- [x] Botão de anexo → Galeria (Photo Picker múltiplo), Câmera, Documento (`OpenMultipleDocuments`) — `BotaoAnexar` no campo; `takePersistableUriPermission` (o envio pode ser depois, pelo WorkManager); câmera grava em `cache/camera` pelo `FileProvider` (sem permissão de câmera). No emulador: 2 fotos da galeria, PDF por documento e foto da câmera enviados ✔ 389e66d
- [x] Fila acima do campo: miniaturas (imagem), ícone + nome + tamanho (outros), "Remover" — `FilaAnexos`; Enviar habilitado só com anexos (testado no emulador) ✔ 389e66d
- [x] 🆕 Devolver o acesso aos arquivos ao sair da fila (enviada, descartada ou removida do campo) e apagar a foto da câmera; o Android limita as permissões guardadas por app — `FontesArquivo.liberar` + `EnvioMensagens.desistirDoArquivo`; arquivo ainda usado por outra mensagem da fila não é liberado (+ teste; no emulador a foto some de `cache/camera` ao enviar e ao remover) ✔ 389e66d
- [x] Tipo: `image/*` → 2; `audio/*` → 4; gravação do microfone → **5**; resto → 3 (vídeo = 3 com extensão) — `tipoPorMime` + `ehVideo` (extensão, como o web) em `core/model/Chat.kt` (+ teste); a gravação é sempre 5
- [ ] Mensagem: [encaminhados] → texto → figurinha → arquivos, com `ordem` 1..n — 🔄 texto (ordem 1) → arquivos na ordem escolhida (+ teste; no emulador o banco gravou texto=1, imagens=2 e 3); encaminhados e figurinha entram nas etapas 7
- [x] Erro em qualquer upload cancela a mensagem inteira (como no web) — arquivo inacessível, grande demais ou recusado → a mensagem inteira vira "falhou"; sem rede espera (+ teste)
- [x] Indicador de upload (nome, barra, %) na bolha otimista — `EnvioMensagens.progresso` (fração por mensagem) → barra na bolha (`LinhaMensagem(progresso)`), com o nome do arquivo e o relógio de "enviando"; visto no emulador com o arquivo de 200 MB (sem o número em %, a barra basta) ✔ 389e66d

### 4.3 URLs assinadas (FC-403, ANX-14)
- [x] `GET /anexo?identificador=` → `{url}` — `AnexosRepositorio.url`
- [x] Cache identificador → (url, expira_em) — vale até 1 min antes dos 600 s (+ teste)
- [x] Coil 3 com o OkHttp compartilhado e chave de cache = identificador (não a URL) — modelo `AnexoRemoto(identificador)` + `FetcherAnexo` no `:app`: cache de memória (`Keyer`) e de disco pelo identificador; já no disco, nem pede URL ao servidor (no emulador: reabrir a conversa = 0 pedidos); o OkHttp do app já não manda o token para o MinIO ✔ 389e66d
- [x] Imagem falhou (403/expirada) → renovar a URL e tentar uma vez — dentro do `FetcherAnexo` (a primeira versão, na tela, trocava o modelo do `AsyncImage` e o Coil não refazia o pedido). Testado com `URL_VENCIDA=1 node ferramentas/proxy-dev.mjs` (403 na primeira leitura de cada arquivo): as 3 imagens renovaram a URL e apareceram ✔ 389e66d
- [x] 🆕 Download de anexo (abrir arquivo, áudio) com URL vencida → pedir outra e tentar uma vez — `ArquivosLocais.baixar` só esquecia a URL e falhava ("Falha ao baixar (403)") (+ teste) ✔ 1ceaad0

### 4.4 Imagens e vídeos (FC-404, FC-408, ANX-04, ANX-05)
- [x] Bolha de imagem (proporção preservada, hora sobre a imagem) — `BolhaImagem` (até 260×320 dp, hora e status por cima, barra de envio); imagem com texto vai na bolha padrão. Testado no emulador (galeria e câmera; a imagem enviada volta pelo servidor) ✔ 389e66d
- [x] Visualizador em tela cheia: pager com todas as imagens e vídeos da conversa (exceto ocultas) — `VisualizadorImagens` aberto pelo toque na imagem ou no vídeo; `imagensDaConversa` inclui vídeos (tipo 3 com extensão de vídeo) (+ teste); legenda e "Abrir com…" embaixo, fora da área da mídia. Testado no emulador ✔ 0168bd6
- [x] Zoom com pinça e duplo toque; legenda = textos da mesma mensagem; tira de miniaturas — pinça e duplo toque (sem zoom o arrasto troca de imagem); legenda = remetente, hora e textos; `TiraMiniaturas` (a atual em destaque, toque vai até ela; vídeo com o play). Testado no emulador ✔ 0168bd6
- [x] Vídeo na bolha: primeiro quadro + ícone de play — `VideoNaBolha` + `QuadroVideo`/`FetcherQuadroVideo` (`MediaMetadataRetriever` pela URL assinada, lê por partes; o quadro fica no cache de disco do Coil; URL vencida tenta de novo; arquivo local enquanto envia). No emulador: vídeo gravado da tela, enviado pela galeria e recebido do B ✔ 0168bd6
- [x] Vídeo no visualizador: Media3 com controles — `ReprodutorVideo` (`:core:media`, `PlayerView` do `media3-ui`): toca só na página visível, solta o player ao sair, pega o foco de áudio (o áudio da conversa pausa). No emulador: o vídeo de 6 s do B tocou pela URL assinada ✔ 0168bd6
- [x] Ações do visualizador: compartilhar, baixar — "Abrir com…", "Compartilhar" (baixa para o cache e abre o compartilhar do Android) e "Baixar" (Downloads/Conversa). Testado no emulador ✔ 45f269f

### 4.5 Áudio (FC-405, ANX-10)
- [x] `PlayerUnico` (Media3): só um áudio toca por vez — `PlayerAudio`/`PlayerMedia3` (singleton, foco de áudio, pausa ao desconectar o fone, posição a cada 200 ms); a chave é conversa:mensagem:ordem. No emulador: tocar o segundo para o primeiro ✔ 1ceaad0
- [x] Bolha de áudio: play/pause, barra com seek, `mm:ss`; nome do arquivo (tipo 4) ou só o player (tipo 5) — `PlayerNaBolha` (barra fina própria: tocar ou arrastar; o `Slider` do M3 é grande demais para a bolha); o estado chega por `LocalAudio` (fluxo) para a posição não recompor a lista. No emulador: WAV de 7 s e 4 s do B, pausa, pular para 75% (00:05), fim volta ao início ✔ 1ceaad0
- [x] Download sob demanda para o cache: arquivo `.part` → rename ao terminar — `ArquivosLocais.baixar` (o mesmo do "Abrir"), só no primeiro play; agora com URL vencida → pede outra e tenta de novo (+ teste `ArquivosLocaisTest`; achado no emulador com `URL_VENCIDA=1`) ✔ 1ceaad0
- [x] Áudio de outro não ouvido: botão verde; o primeiro play chama `POST /mensagem/reproduzir {conversa, mensagem}` — verde = `waveform` do FMX (o FMX não tem cor de "não ouvido"); `MensagensRepositorio.marcarReproduzida` (Room otimista + uma chamada; não marca lida) (+ testes). No emulador: `mensagem_status.reproduzida` gravado só no áudio tocado ✔ 1ceaad0
- [ ] Parar o áudio ao sair da conversa ou ao começar uma chamada — 🔄 sair da conversa: `ChatViewModel.onCleared` para o áudio dela (+ teste; no emulador, 0 players ativos depois de voltar); a chamada (etapa 6) deve chamar `PlayerAudio.parar()`

### 4.6 Gravação de áudio (FC-406, ANX-11, ENV-16)
- [x] Transplantar o `AudioRecorderHelper` (pegar `Exception` em `start()`, #48) — `GravadorMediaRecorder` (`:core:media`): AAC/M4A mono 64 kbps (toca no web, no Windows e no Android), qualquer `Exception` ao abrir o microfone vira "Não foi possível iniciar a gravação"; pausa = fecha o trecho e `juntarTrechos` (MediaMuxer, sem recodificar) monta o arquivo. No emulador: 2 trechos juntados = 42,42 s contínuos (1827 quadros AAC) ✔ 399bae5
- [x] Segurar o microfone ≥ 300 ms e soltar → envia direto — o microfone aparece com o campo vazio (como o web); menos de 1 s é descartado com aviso. No emulador: segurar 3,5 s enviou ✔ 399bae5
- [x] Toque curto (ou arrastar para cima) → modo travado com barra: Descartar, tempo, Pausar/Continuar, Ouvir, Enviar — `BarraGravacao` (textos do web, nível do microfone, ponto piscando); pausada: ouvir com barra de progresso; app em segundo plano pausa a gravação. Testado no emulador ✔ 399bae5
- [x] Arrastar para o lado → cancelar — nada enviado e os arquivos apagados (testado no emulador) ✔ 399bae5
- [x] Enviar como conteúdo **tipo 5** — `audio-<hora>.m4a`, `audio/mp4`, pela mesma fila de envio (o arquivo e a pasta somem do cache depois de enviado) (+ testes `GravacaoTest`) ✔ 399bae5
- [x] Durante a gravação: `POST /conversa/gravando {id}` a cada 2,5 s — também pausada (a gravação continua aberta, como no web) (+ teste); no emulador o B recebeu o WS 5 a cada 2,5 s ✔ 399bae5
- [x] Receber o WS 5 → "Gravando áudio…" em vermelho (prioridade sobre o "digitando") — no cabeçalho (texto e pontos em `gravandoAudio`); a lista de conversas não mostra, igual ao web ✔ 399bae5
- [x] Permissão de microfone pedida na hora, com um launcher só para isso (#32) — `RECORD_AUDIO` no manifesto, pedida ao apertar o microfone; negada: "Sem permissão para usar o microfone…" ✔ 399bae5

### 4.7 Arquivos e download (FC-407, ANX-09)
- [x] Bolha de arquivo: ícone pela extensão, nome, tamanho, "Baixar"/"Abrir" — `LinhaArquivo`: ícone, nome, "Abrir" e o botão "Baixar"; vídeo com "Baixar vídeo" embaixo (como o web). O tamanho não vem na mensagem (só no anexo): fica sem, para não fazer uma chamada por arquivo ✔ 45f269f
- [x] Baixar: URL assinada → `MediaStore.Downloads` (sanitizar o nome: nada de `../`) — `DownloadsRepositorio`: baixa para o cache (URL renovada, `.part`) e copia para Downloads/Conversa pelo MediaStore (pendente até terminar; erro apaga); roda no escopo do app (sair da conversa não interrompe); Android 9 usa "Salvar como" (sem permissão de armazenamento — não testado, o emulador é Android 16); aviso "salvo em Downloads/Conversa" com "Abrir" (+ testes). No emulador: PDF e foto salvos com o tamanho exato e o PDF abriu pelo aviso ✔ 45f269f
- [x] 🆕 `nomeSeguro` nunca devolve `.` nem `..` (antes `..` passava e o arquivo baixado ou apagado podia sair da pasta) (+ teste) ✔ 399bae5
- [x] Abrir: `ACTION_VIEW` com `FileProvider` — `ArquivosLocais.baixar` (cache, `.part` → renomeia) + `FileProvider` (`caminhos_arquivos.xml`) + `ACTION_VIEW`; no emulador o PDF enviado baixou pelo proxy e abriu no leitor de PDF do sistema ✔ 389e66d
- [ ] Notificação de download concluído — 🔄 `AvisoDownloadNotificacao` (canal "Downloads", toque abre o arquivo) só publica se notificações estiverem permitidas; a permissão `POST_NOTIFICATIONS` é pedida na etapa 5 — testar lá

### 4.8 Transcrição (FC-409, ANX-12)
- [x] Abaixo dos áudios (tipos 4 e 5): estado inicial a partir de `transcricao_status`/`transcricao` — `TranscricaoNaBolha` lê da própria mensagem (Room); o resultado vale para todas as mensagens com o mesmo anexo (`MensagemDao.atualizarTranscricao`) ✔ f5d1501
- [x] Botão "Transcrever" → `PUT /anexo/transcricao {identificador}` — `TranscricoesRepositorio.transcrever` (desliga o botão enquanto pede) ✔ f5d1501
- [x] "Transcrevendo…" → `GET /anexo/transcricao?identificador=` a cada 3 s — também quando a mensagem já chega "processando" (pedida por outra pessoa); no escopo do app, até 1 h (+ testes `TranscricoesRepositorioTest`). ⚠ No emulador só deu para testar o servidor sem transcritor (o de dev está com `transcritor_url` vazio); pedir, acompanhar e mostrar o texto ficaram testados por unidade ✔ f5d1501
- [x] Concluída → texto (ou "(nenhuma fala reconhecida)"); erro → "Não foi possível transcrever. Tentar de novo" — e o motivo do transcritor embaixo, como o web ✔ f5d1501
- [x] Esconder o botão se o servidor não tem transcritor (por enquanto: esconder após o primeiro erro de configuração; depois, via S10) — 400 → `desligada` até o fim da sessão; o motivo do servidor aparece no aviso. Testado no emulador ("Transcrição não configurada: defina o parâmetro transcritor_url." e os 4 botões sumiram) ✔ f5d1501

### 4.9 Receber compartilhamento (FC-410, AND-10)
- [x] `intent-filter` `ACTION_SEND`/`ACTION_SEND_MULTIPLE` para `text/*`, `image/*`, `video/*`, `*/*` — na `MainActivity` (`*/*` cobre todos); `lerCompartilhamento` (+ teste); sem sessão, o "Enviar para…" abre depois do login ✔ 1fbfc38
- [x] Tela "Enviar para…" com conversas e contatos — `EnviarParaRotaTela` (`feature/conversas`): conversas na ordem da lista e contatos sem conversa direta (cria ao escolher), busca, resumo do que vai ("1 arquivo", o texto, os ignorados); voltar descarta. No emulador: compartilhado pelo app Arquivos do Android ("Conversa" aparece no compartilhar) ✔ 1fbfc38
- [x] Abrir o chat escolhido com os itens já na fila — `RotaChat(comCompartilhamento = true)`: os arquivos entram na fila e o texto no campo (para revisar antes de enviar). No emulador: texto (via `am start`) e PDF (via app Arquivos) chegaram; o PDF foi enviado e a cópia saiu do cache ✔ 1fbfc38
- [x] 🆕 Segurança do compartilhamento: só `content://` de outro app; `file://` e URIs do próprio app são recusados (senão outro app poderia fazer o Conversa mandar os próprios arquivos privados, como a sessão, para uma conversa); os arquivos são copiados para `cache/compartilhados` na hora (a permissão do compartilhar é temporária e o envio pode ser depois); acima de 1 GiB não entra (+ testes `CompartilhamentosTest`) ✔ 1fbfc38

### 4.10 Extras de mídia (FC-411…414)
- [x] PDF: visualizador com `PdfRenderer` (páginas sob demanda, zoom, "página X de Y") — `VisualizadorPdf` (como o `VisualizadorPdf.vue` do web): "Abrir" num PDF baixa para o cache e abre no app; páginas desenhadas ao rolar (uma por vez no `PdfRenderer`, cache de 48 MB), pinça até 4× e duplo toque 1×↔2×, "Página X de Y", Baixar, Abrir com… e Fechar; PDF protegido/inválido: "Não foi possível abrir o PDF. Tente baixar o arquivo." + Abrir com…. No emulador: PDF de 5 páginas do B, rolagem até a 4 e zoom ✔ 9724037
- [x] Colar imagem do teclado/área de transferência (`contentReceiver`) — o campo passou para `TextFieldState` (o `contentReceiver` só funciona nele; o estado continua local e síncrono); imagem colada é copiada para o cache (mesmas regras do compartilhar) e entra na fila (+ teste). No emulador: digitar, apagar, enviar e o "digitando" continuaram funcionando. ⚠ Colar de verdade não foi testado: o teclado do emulador não tem imagens sem internet, e copiar imagem pelo Chrome pediria aceitar os termos dele ✔ d9d03f1
- [x] HTML anexado: abrir externamente (ou WebView sem acesso a arquivos/cookies) — "Abrir" abre com outro app pelo `FileProvider` (sem WebView no Conversa). No emulador: o Android ofereceu Chrome e HTML Viewer, e a página abriu ✔ d9d03f1
- [x] Economia de dados: com rede limitada, "Toque para carregar" em imagens e vídeos — `EconomiaDados` (`core/data/rede`): conexão lenta (< 150 kbps, como o web bloqueia em 2g) ou Economia de dados do Android numa rede medida; `CarregarSobToque` nas bolhas de imagem e vídeo (o visualizador não bloqueia) (+ testes da regra). ⚠ Não testado no emulador: exigiria mudar a rede ou a Economia de dados do aparelho ✔ d9d03f1

### 4.11 Campo de mensagem rico (FC-415, FC-416, ENV-21)
- [ ] Escrever o ADR com a decisão: campo **simplificado** (faixa de anexos + texto, enviado como [anexos…, texto]) agora × **completo** (blocos intercalados, igual ao web)
- [ ] Simplificado: faixa horizontal de peças acima do campo (miniatura, nome, "×"), na ordem de inserção
- [ ] Simplificado: envio = peças na ordem + o texto por último
- [ ] Completo: modelo `List<BlocoComposicao>` (`Texto(String)` / `Peca(anexo ou figurinha)`) no ViewModel
- [ ] Completo: UI com um `TextField` por bloco de texto e um chip/miniatura por peça, numa coluna rolável (máx. ~40% da tela)
- [ ] Completo: inserir peça no ponto do cursor (divide o bloco de texto em dois)
- [ ] Completo: Backspace no início de um bloco de texto remove a peça anterior e junta os textos
- [ ] Completo: envio na ordem; cada texto vira tipo 1; linhas vazias entre peças descartadas; menção `@[Nome](id)` dentro do texto
- [ ] Figurinha e gravação com o campo vazio vão na hora; com algo escrito, entram como peça
- [ ] **Teste:** enviar [texto, imagem, texto, figurinha] e conferir a mesma ordem no web

---

## Etapa 5 — Notificações e push

### 5.1 Firebase (FC-600)
- [ ] Criar o app Android no projeto Firebase (o mesmo do servidor)
- [ ] Baixar o `google-services.json` para `app/` (fora do Git ou por variante)
- [ ] Aplicar o plugin `com.google.gms.google-services`
- [ ] Dependência `firebase-messaging` (BOM 34+, sem `-ktx`)
- [ ] `ConversaFcmService` com `onNewToken` e `onMessageReceived`
- [ ] **Teste:** `onNewToken` dispara na primeira abertura

### 5.2 Token (FC-601)
- [ ] No login e no `onNewToken`: `PATCH /dispositivo {id, token_fcm}`
- [ ] No logout: `PATCH /dispositivo {id, token_fcm:null}`

### 5.3 Canais e permissões (FC-606, FC-607)
- [ ] Criar os canais uma vez no `Application`: `mensagens_v1`, `chamadas_recebidas_v1`, `chamada_ativa_v1`, `sistema_v1`
- [ ] Pedir `POST_NOTIFICATIONS` depois do login, com explicação
- [ ] Android 14+: verificar `canUseFullScreenIntent()`; se negado, explicar e levar às configurações
- [ ] Explicar e pedir a isenção de otimização de bateria (opcional, para chamadas)

### 5.4 Notificação de mensagem (FC-602, FC-603, NOT-01, NOT-02)
- [ ] Push `data {titulo, mensagem, conversa}` → sincronizar (`SyncManager`) → montar a notificação
- [ ] MessagingStyle por conversa, com id de notificação = id da conversa (sem colisão)
- [ ] Título = nome do remetente/grupo; avatar como ícone grande; corpo resumido ("Imagem", "Gravação de áudio", "Figurinha", "Arquivo"…)
- [ ] Com o app aberto (via WS): não notificar se a conversa está aberta na tela
- [ ] Nunca notificar conversas arquivadas
- [ ] Toque → deep link para a conversa

### 5.5 Ações da notificação (FC-604, AND-08)
- [ ] Resposta direta (RemoteInput) → `PUT /mensagem` no `conversa_id` real → atualizar a notificação
- [ ] "Marcar como lida" → `POST /mensagem/visualizar` das mensagens da conversa
- [ ] Receivers com `goAsync()` ou WorkManager expedited (#42)

### 5.6 Fechar notificações (FC-605, NOT-03)
- [ ] Cancelar ao ler tudo, ao arquivar e ao abrir a conversa

### 5.7 Sem serviço permanente (FC-609, AND-07)
- [ ] Confirmar que não existe nenhum foreground service fora de chamada
- [ ] Em segundo plano, depender só do FCM (as chamadas precisam de S1)
- [ ] **Teste:** app fechado + mensagem pelo web → notificação chega (com o aparelho em Doze: `adb shell dumpsys deviceidle force-idle`)

### 5.8 Atalhos de conversa (FC-608)
- [ ] Publicar `ShortcutInfo` de longa duração para as conversas recentes (aparecem na seção "Conversas" do Android)

---

## Etapa 6 — Chamadas

- [ ] Criar os módulos `:core:webrtc` e `:feature:chamada` (adiados da 1.2)
### 6.1 Infra de mídia (FC-703, FC-704)
- [ ] Transplantar o `WhipWhepClient` para `:core:webrtc`, usando o OkHttp compartilhado
- [ ] WHIP/WHEP: ler o header `Location` e fazer `DELETE` ao encerrar
- [ ] `PeerConnectionFactory` e `EglBase` únicos por processo
- [ ] `PublicadorWhip`: PC `SEND_ONLY`, áudio Opus (32/64/128 kbps conforme a configuração), vídeo opcional
- [ ] `setCodecPreferences`: H264 → VP9 → VP8
- [ ] Câmera: padrão 360p a 15 fps (como no web para celular); trocar frontal/traseira
- [ ] `AssinanteWhep` por participante: PC `RECV_ONLY`; registrar o peer **antes** de `setRemoteDescription` (#10)
- [ ] ICE: `GET /api/ice` a cada PC; `GATHER_ONCE`; esperar até 5 s; "forçar relay" → `iceTransportPolicy = RELAY`
- [ ] Retentativas WHEP 404: vídeo 40×1 s; áudio 12×0,8 s
- [ ] ICE `FAILED` → recriar só o PC daquele peer
- [ ] Lock só para mudar o estado, nunca durante a rede (#18)
- [ ] `dispose()` em tracks e PCs ao encerrar (#37)
- [ ] **Teste:** o arquivo gravado aparece no MediaMTX

### 6.2 `CallManager` (FC-700)
- [ ] Estados: `Inativo`, `Chamando`, `Recebendo`, `Conectando`, `Ativa`, `Encerrando`
- [ ] `StateFlow<EstadoChamada>`: chamada, participantes, tracks, mute, câmera, rota de áudio, duração, modo de exibição
- [ ] Assinar o `SharedFlow` do WS para os eventos 51–57 (único consumidor de chamada)
- [ ] `encerrar()` idempotente: DELETE WHIP/WHEP → `dispose` → liberar o áudio → cancelar as notificações → Telecom disconnect → parar o serviço
- [ ] Testes unitários de **todas** as transições (incluindo eventos fora de ordem e duplicados)

### 6.3 Core-Telecom (FC-701, AND-03)
- [ ] Adicionar `androidx.core:core-telecom`
- [ ] Registrar o `CallsManager` com as capacidades (vídeo)
- [ ] Recebida e efetuada via `addCall(...)` com os callbacks answer/disconnect/setActive/setInactive
- [ ] Usar os endpoints de áudio do Telecom para rotear (fone, alto-falante, Bluetooth, fone com fio)
- [ ] Manifest: `FOREGROUND_SERVICE_PHONE_CALL`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CAMERA`, `MANAGE_OWN_CALLS`
- [ ] **Teste:** atender pelo botão do fone Bluetooth; receber uma ligação GSM durante a chamada → coloca em espera

### 6.4 Iniciar chamada (FC-705, CHA-01)
- [ ] Botões de voz e vídeo no cabeçalho do chat
- [ ] Participantes: direta = [eu, outro]; grupo = `GET /conversa/usuarios?conversa=` (todos, inclusive eu)
- [ ] Pedir as permissões (microfone; câmera se vídeo) na hora
- [ ] `PUT /chamada/iniciar {tipo, usuarios:[{id}], conversa_id}` → já publicar via WHIP
- [ ] Tela "Chamando…" + som de chamando em loop
- [ ] Bloquear se já existe chamada ("Já existe uma chamada em andamento")
- [ ] Cancelar enquanto chama: `POST /chamada/cancelar {id}` (FC-711)
- [ ] Timeout local (ex.: 45 s sem ninguém atender) → cancelar

### 6.5 Chamada recebida (FC-706, FC-707, CHA-03, CHA-04, CHA-05)
- [ ] WS 51 (ou push S1) → `GET /chamada/dados?id=` → `CallManager` em `Recebendo`
- [ ] Telecom `addCall` (entrada) + notificação CallStyle `forIncomingCall` + full-screen intent
- [ ] Tela de chamada recebida (`showWhenLocked`, `turnScreenOn`): nome, avatar, "Vídeo + Áudio"/"Somente áudio"
- [ ] Botões **tocáveis**: Recusar, Atender e, em vídeo, "Atender só assistindo"
- [ ] O toque **só para** ao atender, recusar, encerrar remoto ou no timeout (#7)
- [ ] 30 s sem resposta → `POST /chamada/recusar {id, nao_atendeu:true}`
- [ ] Atender: mídia vídeo+áudio → só áudio → só recepção (fallback) → `POST /chamada/entrar {id}` → publicar → assinar os outros
- [ ] "Atender" pela notificação usa `PendingIntent.getActivity` ou Telecom (nunca broadcast → activity, #3)
- [ ] Recusar: **um** `POST /chamada/recusar {id}`

### 6.6 Regras automáticas (FC-708, FC-709, FC-710, ATV-03)
- [ ] 51 durante outra chamada ou toque → `recusar {nao_atendeu:true}` sem tocar
- [ ] 51 da minha própria chamada → ignorar
- [ ] 53/54 com o **meu** `usuario_id` enquanto toca (atendi/recusei em outro aparelho) → parar o toque, voltar a `Inativo`
- [ ] Ao conectar o WS / abrir pelo push: `GET /chamadas/pendentes` (**sem `usuarios`**)
- [ ] Pendente criada há mais de 25 s → `recusar {nao_atendeu:true}`; senão, tocar

### 6.7 Durante a chamada (FC-712, CHA-10)
- [ ] 54 de outro: se eu estava `Chamando` → `Ativa` + cronômetro; assinar quem entrou
- [ ] 55: remover o peer → `GET /chamada/dados` → se ninguém mais com status "Entrou", sair (`POST /chamada/sair`) — o servidor **não manda o 52** nesse caso
- [ ] 53 de outro em chamada de 2 pessoas → encerrar
- [ ] 52 → encerrar
- [ ] A cada 4 s em `Ativa`: `GET /chamada/dados` e reconectar quem caiu ou está sem trilha
- [ ] Botão sair: `POST /chamada/sair {id}`

### 6.8 Áudio (FC-713, AND-04, AND-05)
- [ ] Foco de áudio durante a chamada (pausa a música)
- [ ] Seletor de rota: Fone do aparelho / Alto-falante / Bluetooth / Fone com fio (via Telecom)
- [ ] Sensor de proximidade só quando a rota é o fone do aparelho
- [ ] Ao encerrar: devolver o modo normal e limpar o dispositivo de comunicação
- [ ] Parar qualquer áudio de mensagem ao entrar em chamada

### 6.9 Tela da chamada ativa (FC-714, CHA-11)
- [ ] Cabeçalho: "Chamando…"/"Em chamada"/"Encerrando…", duração, tipo, nº de pessoas
- [ ] Controles: microfone, câmera (vídeo), trocar câmera, rota de áudio, adicionar pessoa, chat, sair
- [ ] Estado de mute e câmera reativo (StateFlow), ícones corretos, `contentDescription` em tudo
- [ ] Cores: microfone, câmera e som **vermelhos quando desligados**; tela, chat e ponteiro **azuis quando ligados**; demais neutros
- [ ] `BackHandler`: voltar = minimizar (não encerra)
- [ ] Tiles: nome ("Você"), iniciais sem vídeo, faixa vermelha de erro de conexão
- [ ] **Teste:** atender e desligar só com o TalkBack

### 6.10 Histórico de chamadas (FC-715, CHA-22)
- [ ] `GET /chamadas?participante=0&de=YYYY-MM-DD&ate=YYYY-MM-DD` → `ChamadaHistoricoItem`
- [ ] Abas Todas / Perdidas (status 5); busca por contato; filtro de período
- [ ] Agrupar: Hoje / Ontem / `dd/MM/aaaa` (portar a lógica do adapter antigo)
- [ ] Item: avatar (grupo: "Grupo (N)"), seta efetuada/recebida (`criado_por == eu`), hora, "Vídeo"/"Áudio" · duração ou status, cor
- [ ] Toque → abrir a conversa; botão "Ligar novamente" (mesmo tipo e participantes)

### 6.11 Vídeo (FC-716, CHA-12, CHA-16)
- [ ] Renderers com `key(track)` e `onRelease` (#39); PiP local com `setZOrderMediaOverlay(true)`
- [ ] Modos: Grade (1/2/3/4 colunas conforme o número), Destaque, Tela única com setas
- [ ] Toque no tile → destacar; toque no destaque → voltar à grade
- [ ] Upgrade áudio→vídeo: abrir a câmera → republicar → `POST /chamada/video {id}` (proteger contra toque duplo)
- [ ] Receber o WS 56: já em vídeo → reassinar quem republicou; senão, modal "<nome> ativou o vídeo" com "Apenas assistir" / "Transmitir também" (15 s → "Apenas assistir")
- [ ] FGS com o tipo `camera` adicionado quando o vídeo liga

### 6.12 Minimizar (FC-717, CHA-13, AND-06)
- [ ] Picture-in-Picture em chamada de vídeo (`setAutoEnterEnabled` no Android 12+)
- [ ] Banner "Toque para voltar à chamada" no topo das outras telas
- [ ] Notificação em andamento CallStyle `forOngoingCall` com cronômetro nativo e "Desligar"

### 6.13 Recursos extras de chamada (FC-718…722)
- [ ] Adicionar participante: contatos fora da chamada → `PUT /chamada/usuario {chamada_id, usuario_id}` (um por vez) → `GET /chamada/dados`
- [ ] Chat da chamada: `PUT /chamada/chat {id}` → `{conversa_id}` na primeira mensagem; WS 57 `{acao:"chat", conversa_id}`; painel com só texto
- [ ] Chat **completo** da chamada: quando o grupo existe, abrir a própria tela de chat (lista + campo completos) num painel/bottom sheet com a conversa do grupo; antes da primeira mensagem, o painel simples
- [ ] Indicador de fala: nível de áudio local e remoto (> 0,02, segura 400 ms) → anel verde no avatar
- [ ] Somente recepção: entrar sem microfone/câmera; botões "Ativar microfone"/"Ativar câmera"
- [ ] Tela remota compartilhada: WS 57 `{acao:"tela", ativa}` → destacar a tela
- [ ] Ponteiro remoto: WS 57 `{acao:"ponteiro", alvo, x, y}` → desenhar com nome e cor (`id % 5`); some em 5 s

### 6.14 Matriz de testes manuais (FC-955)
- [ ] Criar `docs/testes/chamadas.md` com a matriz
- [ ] Wi-Fi ↔ 4G; 4G ↔ 4G
- [ ] App aberto / em segundo plano / fechado (precisa de S1) / tela bloqueada
- [ ] Bluetooth; fone com fio; chamada GSM concorrente
- [ ] 1:1 áudio; 1:1 vídeo; grupo de 4; upgrade para vídeo; adicionar participante
- [ ] Atender no web com o celular tocando (o celular para de tocar)
- [ ] Gravação conferida no servidor

---

## Etapa 7 — Ações sobre mensagens

### 7.1 Menu de ações (FC-500, ENV-20)
- [ ] Toque longo na bolha → barra de reações 👍 ❤️ 😂 😮 😢 👏 🔥 + "mais"
- [ ] Itens: Responder; Responder no privado (grupo e mensagem de outro); Encaminhar; Copiar; Ocultar (minha e não oculta)
- [ ] Não abrir para chamada, oculta ou mensagem ainda sem id real

### 7.2 Reações (FC-501, ENV-17)
- [ ] `PUT /mensagem/reacao {mensagem_id, emoji}` → `{mensagem_id, emoji, acao:"add"|"remove"}` (toggle), otimista
- [ ] Chips abaixo da bolha: emoji + contagem; destacado se eu reagi; toque alterna
- [ ] Toque longo no chip → quem reagiu (foto, nome, hora)
- [ ] Receber o WS 7 `{conversa_id, mensagem_id, emoji, acao, usuario_id}`
- [ ] "Mais" → seletor de emoji do sistema (emoji com no máximo 10 code points)

### 7.3 Responder (FC-502, ENV-06)
- [ ] Deslizar a bolha para a direita → responder
- [ ] Barra acima do campo: nome + resumo + ×
- [ ] Envio: `mensagem_referencia:{tipo:1, origem_mensagem_id:<id respondida>}`
- [ ] A mensagem otimista já mostra a citação

### 7.4 Ocultar (FC-503, ENV-18, ENV-14)
- [ ] Confirmação: "Ocultar mensagem" / "Ela continua na conversa, marcada como oculta." / "Ocultar" (perigo) / "Cancelar"
- [ ] `DELETE /mensagem?id=` → com `excluida_em`: marcar como oculta; sem: remover (era agendada)
- [ ] Atualizar a prévia da lista ("Mensagem oculta")
- [ ] Agendada futura: o texto vira "Cancelar mensagem agendada" / "Ela não será enviada." / "Cancelar envio" / "Voltar"
- [ ] Erro → "Não foi possível ocultar"

### 7.5 Citação e ir para a mensagem (FC-504, FC-505, MSG-19, MSG-06)
- [ ] Bloco de citação na bolha: remetente (resposta) ou "Encaminhado de <remetente>"; conteúdos da original
- [ ] Citação aninhada recursiva até 5 níveis
- [ ] Encaminhada: não repetir os conteúdos iguais aos da referência
- [ ] Toque na citação → ir para a original
- [ ] Ir para a mensagem: se não carregada, `GET /mensagens?…&mensagemreferencia=<id>&mensagensprevias=30&mensagensseguintes=30` (se não vier, 120/120) → substituir a lista → centralizar + destacar por 1,2 s
- [ ] Original em outra conversa → abrir aquela conversa (se participo)
- [ ] Erro → "Não foi possível localizar esta mensagem no contexto da conversa."

### 7.6 Encaminhar, copiar e responder no privado (FC-506, FC-507, FC-512)
- [ ] Encaminhar: tela de destino (conversas exceto a de origem + contatos sem direta), com busca
- [ ] `PUT /mensagem` no destino com os conteúdos copiados (ordem 1..n) e `mensagem_referencia:{tipo:2, origem_mensagem_id}`
- [ ] Contato sem conversa → criar a direta antes; depois abrir o destino
- [ ] Copiar: texto → área de transferência; imagem → `ClipData` com URI
- [ ] Responder no privado: abrir/criar a direta com o remetente e deixar a mensagem pendente como encaminhada (`tipo:2`, conteúdos antes do texto)

### 7.7 Menções (FC-508, ENV-05, MSG-12)
- [ ] Ao digitar `@` + texto → popup com até 6 contatos/membros (nome/login)
- [ ] Escolher → inserir `@Nome` destacado no campo; guardar o id
- [ ] No envio: converter para `@[Nome](id)`; "@Ana" não casa com "@Anabela"
- [ ] Renderizar `@[Nome](id)` como `@Nome` clicável → abrir a conversa direta
- [ ] Testes do parser (portar `mencoesTexto.ts`)

### 7.8 Figurinhas (FC-509, ENV-04)
- [ ] Copiar `conversa-web/public/figurinhas/**` (basico 8, rostos 7, coisas 9) para `app/src/main/assets/figurinhas/`
- [ ] Adicionar `lottie-compose`
- [ ] Aba "Figurinhas" no seletor do campo, por pacote
- [ ] Toque → enviar na hora como tipo 7 `pacote/nome` (como resposta, se houver uma pendente)
- [ ] Bolha de 160 dp; toca só quando visível; com "remover animações" fica parada; falha → "Figurinha"

### 7.9 Código e Markdown (FC-510, FC-511, MSG-15)
- [ ] Portar `codeBlocks.ts` (abertura/fechamento, aninhamento, markdown) com testes
- [ ] Bloco de código: cabeçalho com a linguagem, "Copiar" → "Copiado!"
- [ ] Destaque de sintaxe (lib leve) para as linguagens do web
- [ ] Recolher acima de ~240 dp: "Expandir código"/"Recolher código"
- [ ] ` ```md `/` ```markdown ` → Markdown renderizado (Markwon ou compose-markdown), alternância "Visualizar"/"Código"
- [ ] ` ```mermaid ` → WebView offline com mermaid.js embutido (P2)

### 7.10 Detalhe de status e agendamento (FC-513, FC-514)
- [ ] Toque no ✓ → bottom sheet: `GET /mensagem/status/detalhe?id=` (aqui os campos são **datas**)
- [ ] Direta: Enviada / Recebida / Visualizada / Ouvida (áudio) / Oculta; sem data → "Aguardando"
- [ ] Grupo: "Visualizada por (N)", "Recebida por (N)", "Aguardando (N)"
- [ ] Toque longo no Enviar → "Agendar": data e hora (padrão amanhã 08:00)
- [ ] Validações: ≥ 5 min no futuro; ≤ 1 ano
- [ ] `visivel_em` em ISO UTC; selo "Agendada para hoje HH:MM / amanhã / dd/MM HH:MM"; some na hora exata

### 7.11 Pequenos extras (FC-515)
- [ ] Atalhos `:)` → 🙂 etc. (tabela do `emojiAtalhos.ts`), só como palavra solta e fora de código
- [ ] Colar texto com mais de 10 linhas → sugerir "Enviar como código"
- [ ] Tela "Inserir código" simples (linguagem + texto monoespaçado)

### 7.12 Votação em grupo (FC-516, FC-517, FC-518, MSG-20, ENV-22)
- [ ] Store/repositório de enquetes: cache por id; leituras simultâneas deduplicadas
- [ ] Bolha: `GET /enquete?id=<conteudo>`; "Carregando votação..." / erro
- [ ] Bolha: nome do remetente (grupo, mensagem de outro), "📊 <pergunta>", "Escolha uma opção" / "Escolha uma ou mais opções"
- [ ] Bolha: cada opção com círculo (única) ou quadrado (múltipla) + ✓, texto, contagem, barra `round(votos / total_votantes × 100)%`, nomes dos votantes
- [ ] Bolha: rodapé "1 pessoa votou" / "N pessoas votaram" + hora/status
- [ ] Votar: única → troca o voto (tocar na marcada tira); múltipla → marca/desmarca
- [ ] `POST /enquete/votar {enquete_id, opcoes:[lista completa]}` → substituir o cache; desabilitar durante o voto; erro → "Não foi possível votar"
- [ ] WS 62 → reler `GET /enquete` só se estiver em cache/na tela
- [ ] Resumo "Votação" em prévia, notificação, citação, atividade e chat da chamada
- [ ] Criar: "+" → "Votação" (só em grupo) → bottom sheet "Nova votação"
- [ ] Criar: "Pergunta" (≤ 300, placeholder "Ex.: Onde vamos almoçar?"), "Opções" 2–12 (≤ 200), "+ Adicionar opção", remover acima de 2
- [ ] Criar: "Permitir várias escolhas" / "Cada pessoa pode marcar mais de uma opção."
- [ ] Criar: "Cancelar" / "Criar votação" ("Criando..."), habilitado com pergunta e ≥ 2 opções
- [ ] `PUT /enquete {conversa_id, pergunta, opcoes (preenchidas, trim), multipla}` → atualizar a conversa; mostrar os erros do servidor
- [ ] Esconder "Encaminhar" no menu de uma mensagem de votação (o servidor recusa com 400)
- [ ] **Teste:** criar no celular, votar no web → a barra atualiza no celular sem recarregar

---

## Etapa 8 — Atividades, pesquisa, perfil e configurações

- [ ] Criar os módulos `:feature:atividades`, `:feature:pesquisa` e `:feature:config` (adiados da 1.2)
### 8.1 Atividades (FC-800, ATV-01, ATV-02)
- [ ] Aba Atividades: `GET /atividades?antes=0&limite=30`
- [ ] Paginação: perto do fim → `antes=<id da última>`; fim quando vierem < 30
- [ ] Agrupar Hoje / Ontem / `dd/MM/aaaa`
- [ ] Item: avatar com selo (emoji, "↩", "@", "✆"), "**autor** descrição", "em <grupo>", prévia, hora, "Nova"
- [ ] Descrições: "reagiu <emoji> à sua mensagem", "respondeu sua mensagem", "mencionou você", "ligou (chamada perdida)" / "ligou (chamada de vídeo perdida)"
- [ ] Toque: com `mensagem_id` → ir para a mensagem; sem → abrir a conversa
- [ ] Badge: `GET /atividades/novas` → `{quantidade}` ao conectar e a cada WS 61 (`99+`)
- [ ] Abrir a aba → `POST /atividades/vistas` → zerar o badge (itens seguem marcados como "Nova" enquanto aberta)
- [ ] Vazio: "Nenhuma atividade ainda." / "Reações, respostas, menções e chamadas perdidas aparecem aqui."

### 8.2 Pesquisa (FC-801, PES-01, PES-02)
- [ ] Na conversa: ícone de busca → campo → `GET /pesquisar?texto=&conversa=<id>` → resultados (mais recentes primeiro) → ir para a mensagem
- [ ] Global: "Pesquisar em todos os chats" → `GET /pesquisar?texto=&conversa=0`
- [ ] Resultados agrupados por conversa: remetente, data `dd/MM/aa HH:mm`, trecho com o termo destacado
- [ ] Estados "Pesquisando…" / "Nenhum resultado encontrado."

### 8.3 Perfil (FC-802, AUT-06, AUT-07, AUT-08)
- [ ] Editar nome e e-mail → `PATCH /usuario {id, nome, email}` → "Dados atualizados com sucesso."
- [ ] Alterar senha: atual, nova (≥ 6), confirmação → `POST /alterar-senha {senha_atual, senha}`
- [ ] Mensagens: "Preencha todos os campos de senha.", "A nova senha deve ter pelo menos 6 caracteres.", "A confirmação da senha não confere.", "Senha alterada com sucesso."
- [ ] Avatar: Photo Picker → recorte quadrado central 256×256 → JPEG 85% → upload (4.1) → `PATCH /usuario {id, avatar_anexo_id}`
- [ ] Remover avatar: `PATCH /usuario {id, avatar_anexo_id:null}`
- [ ] Avatar com URL expirada → renovar (no máximo a cada 30 s)

### 8.4 Perfil de outro usuário (FC-803, AUT-10)
- [ ] Toque no avatar da direta → bottom sheet: foto, nome, e-mail, telefone ("Não informado"), "Ver anexos", "Ligar"

### 8.5 Configurações (FC-804…808)
- [ ] Tela com seções: Perfil, Aparência, Notificações, Chamadas, Permissões, Servidor, Sistema*, Acessos*, Ramal SIP, Sobre
- [ ] Aparência: Sistema / Claro / Escuro (+ Material You opcional)
- [ ] Notificações: atalhos para as configurações de cada canal
- [ ] Permissões: estado de notificações, microfone, câmera, full-screen intent e bateria, com botão para o sistema
- [ ] Chamadas: redução de ruído, cancelamento de eco, ganho automático, qualidade de áudio (32/64/128), resolução (360/720/1080), fps (15/24/30), banda; "Restaurar padrão"
- [ ] Sobre: versão do app, servidor conectado, licenças
- [ ] `GET /usuario/permissoes` → mostrar Sistema (`parametros`) e Acessos (`permissoes`) só para quem tem
- [ ] Sistema: `GET /parametros`; editar Firebase (ID do projeto, e-mail, chave — nunca exibida), forçar relay TURN, dias de gravação (0–36500), transcritor (URL, idioma); `PATCH /parametros` só com o que mudou
- [ ] Acessos: `GET /permissoes` → tabela usuário × permissão com busca; `PUT /permissao/usuario {usuario_id, codigo}`; `DELETE /permissao/usuario?usuario_id=&codigo=`; aviso de "modo aberto"

### 8.6 Anexos da conversa (FC-809, ANX-13)
- [ ] A partir do perfil ou do grupo: "Ver anexos"
- [ ] `GET /anexos?conversa=&direcao=(enviados|recebidos|)&tipos=2,3,4,5&antes=<anexo_id>&limite=60`
- [ ] Filtros: Todos / Enviados / Recebidos e Todos / Imagens / Arquivos / Áudios / Gravações
- [ ] Imagens em grade; demais em lista (nome, tamanho, data, autor); ações "Abrir mensagem" e "Baixar"
- [ ] Paginação infinita

---

## Etapa 9 — Extras (avaliar antes de fazer)

- [ ] Ramal SIP — configuração: `GET /sip`; `PUT /sip` (se não existe) ou `PATCH /sip {id, …}` (se existe)
- [ ] Ramal SIP — spike: Linphone SDK (licença GPL/comercial) vs PJSIP; transporte WSS + ICE via `/ice`
- [ ] Ramal SIP — registro, discador com DTMF, chamada recebida, integração com o Telecom
- [ ] Compartilhar a tela do celular (`MediaProjection` + FGS `mediaProjection` + captura de áudio)
- [ ] Bubbles para conversas
- [ ] Cor de destaque personalizada

---

## Qualidade (fazer junto, ao longo de tudo)

- [ ] Testes unitários: parsers (WS, datas, código, menções, classificação), `CallManager`, repositórios (MockWebServer)
- [ ] Testes instrumentados: login, abrir conversa e enviar, chamada (smoke)
- [ ] Meta de cobertura: ≥ 70% no `:core` e no `CallManager`
- [ ] Acessibilidade: TalkBack nas telas críticas, alvos de 48 dp, `contentDescription`, contraste
- [ ] R8 ligado no release + regras para kotlinx.serialization e WebRTC
- [ ] `signingConfig` de release com a keystore fora do Git (variáveis de ambiente/secrets do CI)
- [ ] Timber: árvore de debug; nada de log em release
- [ ] (Opcional) Crashlytics sem dados de mensagem
- [ ] Manifest com as permissões mínimas: nada de `READ_MEDIA_*`, `SYSTEM_ALERT_WINDOW` ou `DISABLE_KEYGUARD`
- [ ] Play Console: declarações de FGS (`phoneCall`, `microphone`, `camera`) e de full-screen intent
- [ ] Ícone adaptativo, splash screen API, nome do app, prints para a loja

---

## Opcional — Hotfixes no app ANTIGO (só se alguém depende dele até o novo ficar pronto)

- [ ] Logs: `BODY` só em debug + `redactHeader("Authorization")` (`RetrofitClient.kt:23-29`)
- [ ] Remover os logs de frame/token (`SocketManager.kt:176,417`; `ChatActivity.kt:1151`; `ConversaFcmService.kt:32,48`)
- [ ] Parar de salvar a senha (`UserPreferences.kt`) + `allowBackup="false"`
- [ ] Porta de dev 4430 → 443 (`app/build.gradle.kts:26-27`)
- [ ] Não parar o toque no `onCreate` da `ChamadaActivity` (`:150-151`)
- [ ] Atender pela notificação com `PendingIntent.getActivity`
- [ ] `@POST("mensagem/visualizar")` com corpo `{conversa, mensagem}`
- [ ] `excluida_em` em `Mensagem` → mostrar "Mensagem oculta"
- [ ] `GET /anexo` → ler `{url}` e usar a URL assinada (Glide, áudio, download)
- [ ] Upload pelo fluxo novo (JSON → PUT na URL → confirmar)
- [ ] `GET /api/ice` antes de cada PeerConnection (TURN)
- [ ] Trocar `GET /conversa/dados` por `GET /conversa/usuarios`
- [ ] **Teste:** login, texto, imagem e áudio nos dois sentidos, chamada 1:1 em 4G, atender pela notificação com a tela bloqueada

---

## Revisão periódica
- [ ] Marcar o que foi feito neste TODO (com o commit)
- [ ] Atualizar `docs/auditoria-2026-10/05-matriz-paridade.md`
- [ ] Ver se o servidor mudou: `git -C ../conversa log --since="1 week ago" --oneline`; se mudou o contrato → atualizar o doc 01 e as fixtures
- [ ] Ver se o web ganhou funcionalidade nova: `git -C ../conversa-web log --since="1 week ago" --oneline` → acrescentar aqui
