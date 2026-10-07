# 04 — Auditoria do cliente Android (Kotlin)

**Repositório:** `conversa-android-kotlin` · **Data da auditoria:** 2026-10-06 · **Branch auditada:** `novo` (working tree com alterações staged + unstaged)
**Escopo:** todos os `.kt` de `app/src` (83 em `main`, 2 de teste), `AndroidManifest.xml`, `res/layout`, `res/xml`, `res/values*`, `res/menu`, `res/navigation`, scripts Gradle, `libs.versions.toml`, `gradle.properties`, `proguard-rules.pro`, `.bat`, `temp_function.kt`, `README.md`, `documentacao-oficial/*.md` e `legado/*.md` (lidos por amostragem).
**Restrição respeitada:** nenhum arquivo do projeto foi modificado. A única escrita foi este relatório e as saídas de build em `app/build/` (que é ignorado pelo Git).

> Convenção de caminhos: `P/` = `app/src/main/java/com/conversa/conversa/`. `file:linha` sempre se refere ao arquivo **no estado atual da working tree** (staged + unstaged).

---

## Sumário executivo

- **Build:** `gradlew assembleDebug --offline` com o JBR do Android Studio (OpenJDK 21.0.8) → **BUILD SUCCESSFUL em 33s**. Gerou um APK debug de 81 MB, inflado pelas libs nativas do WebRTC nas 4 ABIs. Houve só 3 warnings Kotlin (nomes de parâmetro divergentes em `ChatActivity.kt:1088` e `:1137`) e 7 avisos de opções do AGP depreciadas. As `.so` do WebRTC estão alinhadas a 16 KB (verificado no ELF), o que é bom.
- **O app funciona no "caminho feliz"** de login, lista de conversas, chat de texto, envio de imagem e de áudio, notificações de mensagem e chamada 1:1 de áudio via WHIP/WHEP com o app em primeiro plano. **Fora desse caminho a plumbing de chamadas é frágil e tem defeitos críticos de ciclo de vida:**
  1. Dois "donos" disputam os callbacks do `SocketManager` (o `SocketService` e o `ChamadaRepository`). A flag que o `SocketService` consulta para repassar os eventos 52–55 **nunca recebe `true`**. Minimizar uma chamada, ou voltar a uma Activity durante ela, faz o app parar de enxergar o "entrou", o "saiu" e o "finalizou".
  2. Depois que uma chamada termina, os handlers do repositório morto continuam registrados no `SocketManager` global. **A próxima chamada recebida é descartada em silêncio** até alguma Activity se registrar de novo.
  3. **"Atender" pela notificação não funciona no Android 12+**, porque o app usa um *trampolim* de notificação (BroadcastReceiver → `startActivity`).
  4. **O full-screen intent cala o toque**: `ChamadaActivity.onCreate` para o ringtone incondicionalmente.
  5. **O FCM está totalmente inoperante**: falta o plugin, falta o `google-services.json`, nada chama `getToken` nem registra o dispositivo, e a ação do push não é tratada.
- **Segurança:** o `HttpLoggingInterceptor` roda em `BODY` **também em release**, o que registra no logcat a senha do login, o JWT e o conteúdo das mensagens. A senha fica **em texto puro** no DataStore, e o `allowBackup=true` não tem regras de exclusão. O cleartext está liberado globalmente e não existe `network_security_config`. O WHIP/WHEP não tem autenticação e usa paths previsíveis.
- **Dívida:** não há camada de arquitetura (Activities chamam o Retrofit direto), não há ViewModel, DI nem testes reais. Cerca de 1,5 mil linhas são código morto (4 dos 6 repositórios nunca são instanciados e 28 dos 50 endpoints não são usados). O `targetSdk` é 34, abaixo do que o Play exige. O `gradle-wrapper.jar` não é versionado, então um clone limpo não builda com `gradlew`.
- **Recomendação (seção 8):** **recomeçar a estrutura do app e transplantar componentes**, em vez de evoluir incrementalmente. Dá para aproveitar `WhipWhepClient`, o núcleo do `WebRTCManager` (com correções), `AudioRecorderHelper`, `ChamadaRingtoneManager`, o padrão MessagingStyle+RemoteInput do `MensagemNotificationManager`, as telas Compose de chamada e o `ConversaApi`/modelos como referência de contrato. O resto deve ser reescrito.

---

## 1. Estado do repositório

### 1.1 Branch, remoto e histórico

| Item | Valor |
|---|---|
| Remoto | `origin https://github.com/conversa-projeto/conversa-android-kotlin.git` |
| Branch atual | `novo`, **3 commits à frente de `origin/novo`** e não publicados: `a8b4027`, `8696b65`, `e3e2a65` |
| Outras branches | `main`, `flamboyant-ritchie`, `magical-curran` (locais); `origin/main`, `origin/magical-curran`, `origin/novo` |
| Total de commits (novo) | 72 |
| Primeiro commit | `9c2b616` 2025-05-22 (Initial commit) |
| Último commit | `e3e2a65` 2026-04-26 |
| Stashes | **11** (`stash@{0}` "20260421" … `stash@{10}` "20251022_2139"). O `stash@{0}` contém um `commit.md` de 502 linhas e alterações em `.claude/settings.local.json`. Vale revisar antes de descartar. |

**`git log --stat -10` (resumo):**

| Commit | Data | Conteúdo |
|---|---|---|
| `e3e2a65` | 2026-04-26 | chore: remove `.gradle/` e `.idea/` do tracking (43 arquivos, −1934 linhas) e atualiza o `.gitignore` |
| `8696b65` | 2026-04-26 | **Grande refactor de chamadas para WebRTC (WHIP/WHEP via nginx)**: novos `WebRTCManager` (466), `WhipWhepClient` (115) e `WebRTCVideoRenderer`; `ChamadaService` reescrito (−2119/+…); `ChamadaRepository` reescrito; `ConversaApi` com +219 linhas (endpoints novos); repositórios novos (`Mensagem`, `Contatos`, `Dispositivo`, `Sip`, `Usuario`); `ConversaFcmService`; trust-all em DEBUG no Retrofit; docs `.md` movidos para `legado/`; o antigo `ChamadaManager` TCP foi para `legado/chamada-tcp/`. Ainda inclui binários `.gradle/` e `.idea/` (removidos no commit seguinte). |
| `a8b4027` | 2026-04-21 | "atualizacao": só `.gradle`, `.idea` e a versão do AGP no `libs.versions.toml` |
| `a68b452` | 2026-02-04 | Notificações agrupadas por conversa com nome do remetente |
| `8eeb73f` | 2026-02-03 | MessagingStyle + RemoteInput, `MensagemActionReceiver`, estado `CHAMANDO`, permissões na Splash, layouts |
| `243abf0` | 2026-01 | Remoção de código após a migração para Compose |
| `84e6c5c` | — | Impede que eventos de socket iniciem o `ChamadaService` sem necessidade (introduziu a flag `chamadaServiceAtivo`; ver o problema #1) |
| `ff38684`, `0adb945`, `82bec35` | — | Contatos/histórico/nome na lista de chamada |

As mensagens de commit são boas nos commits recentes e genéricas ("atualizacao") em alguns. Vários commits foram co-assinados por agentes de IA.

### 1.2 Alterações não commitadas (detalhe)

**Staged (9 arquivos, +277/−83):**

| Arquivo | ± | O que muda |
|---|---|---|
| `P/MainActivity.kt` | +8/−4 | `inicializarSocket()` deixa de usar a porta fixa `9090` e passa a derivar `host:porta` da `apiUrl` (proxy nginx, ex.: 4430), com padrão 443/80 conforme o esquema (`MainActivity.kt:466-471`). |
| `P/data/socket/SocketManager.kt` | +34/−6 | (a) **Trust-all TLS em DEBUG** no OkHttp do WebSocket (`:64-75`). (b) A URL muda de `ws://host:port` para **`wss://host:port/ws/` fixo** (`:133`), mesmo quando a `apiUrl` é `http://` (ver o problema #19). (c) Passa a tratar o payload real do backend Delphi `{tipo:2,titulo,mensagem}` **sem `conversa_id`** como "gatilho", repassando `conversaId=0` (`:280-289`). |
| `P/service/ChamadaService.kt` | +8 | Expõe `peersFlow`, `localVideoTrackFlow` e `eglBaseContext` para a UI (`:338-344`). |
| `P/ui/chamada/ChamadaScreen.kt` | +22/−2 | O estado `EM_CHAMADA` passa a montar a lista de participantes de forma reativa a partir de `peersFlow` (com `videoTrack`) e repassa o track local e o toggle de vídeo. |
| `P/ui/chamada/ParticipanteUI.kt` | +5/−1 | Novo campo `videoTrack: VideoTrack?`. |
| `P/ui/chamada/components/CallControls.kt` | +16/−1 | Botão de vídeo, com ícone *placeholder* `PlayArrow`. |
| `P/ui/chamada/components/WebRTCVideoRenderer.kt` | +13/−7 | "BUGFIX": `videoTrack.addSink(this)` na factory e `removeSink`+`release` no dispose. |
| `P/ui/chamada/screens/ActiveCallScreen.kt` | +167/−60 | Vídeo remoto em tela cheia (1:1), PiP da câmera local e grade de tiles no grupo (substitui `ParticipantsList`, que vira código morto). |
| `P/ui/chat/ChatActivity.kt` (parte staged) | +4/−2 | `onNovaMensagem` aceita `conversaIdRecebida == 0` (gatilho) e recarrega a conversa aberta. |

**Unstaged (3 arquivos, +48/−1):**

| Arquivo | ± | O que muda |
|---|---|---|
| `P/data/model/MensagemExtras.kt` | +2/−1 | `DigitandoRequest` passa a serializar como `id` em vez de `conversa_id` (alinhado ao `GetValue('id')` do backend). O Git avisa sobre conversão LF→CRLF. |
| `P/service/SocketService.kt` | +17 | `CallListener` ganha `onDigitando`/`onGravandoAudio` com corpo default. Os handlers são registrados **nos dois blocos duplicados** (`:375-380` e `:581-586`). Isso sobrescreve os de `MensagemRepository`, que é código morto. |
| `P/ui/chat/ChatActivity.kt` (parte unstaged) | +29 | Broadcast de "digitando" com throttle de 2,5 s (`:257-271`, chamando o Retrofit direto) e exibição de "digitando..." por 4 s, que depois força `"online"` fixo (`:1137-1146`). O KDoc de `buscarEAdicionarNovaMensagem` ficou deslocado para cima de `onDigitando` (`:1134-1137`). `gravando` não é tratado na UI. |

`ChatActivity.kt` está **parcialmente staged** (uma parte no índice e outra só na working tree). O build desta auditoria compilou a working tree completa, e ela compila.

### 1.3 Lixo e artefatos

| Item | Tracked? | Observação |
|---|---|---|
| `conversa-android-kotlin (2).rar` (36 MB) | Não (ignorado por `*.rar`) | Backup manual na raiz. Remover. |
| `tmpclaude-c8c5-cwd`, `tmpclaude-c90a-cwd`, `tmpclaude-dc7f-cwd` | **Sim** | Arquivos temporários de ferramenta (contêm só o path). Remover do Git. |
| `temp_function.kt` (119 linhas) | **Sim** | Trecho órfão de uma versão antiga de notificação de chamada (`ChamadaBroadcast`, que não existe mais). Não compila nem é usado. |
| `app/src/main/java/.../ui/chamada/ChamadaIncomingActivity.kt.OLD` / `.REMOVIDO` | **Sim** | Código morto dentro de `src`. |
| `build/reports/problems/problems-report.html` | **Sim** | Artefato de build versionado (o `build/` só entrou no `.gitignore` depois). |
| `local.properties` | **Sim** | Contém `sdk.dir` da máquina local. Está no `.gitignore`, mas continua tracked. |
| `.claude/settings.local.json` | **Sim** | Configuração local de ferramenta. |
| `build/`, `app/build/` (235 MB), `.kotlin/`, `.gradle/`, `.idea/` | Não | Ignorados corretamente após `e3e2a65`. |
| `gradle/wrapper/gradle-wrapper.jar` | **Não, por engano** | Ignorado pela regra `*.jar` (`.gitignore:15`). **Um clone limpo não roda `./gradlew`.** |
| `legado/chamada-tcp/ChamadaManager.kt` (1261 linhas) | Sim | Fora de `src`, não compila. Serve como referência histórica do áudio PCM/TCP. |
| `build_completo.bat`, `rebuild_project.bat` | Sim | Scripts de conveniência. O `rebuild_project.bat` tem path absoluto da máquina (`cd /d "C:\Users\danie\..."`) e roda `rmdir /s /q .gradle`. O `build_completo.bat` chama `installDebug` e imprime "BUILD CONCLUIDO COM SUCESSO" sem checar `errorlevel`. |
| `legado/erros.md` | Sim | **Vazio (0 bytes).** |

### 1.4 Segredos e IPs hardcoded

- `app/build.gradle.kts:26-27`: `API_URL = "https://192.168.2.5:4430/api/"` e `MEDIAMTX_URL = "https://192.168.2.5:4430/webrtc"`. É IP de LAN e funciona como **fallback silencioso** sempre que a URL configurada no DataStore não foi carregada no processo (ver o problema #13).
- `app/build.gradle.kts:28`: STUN público do Google (`stun:stun.l.google.com:19302`). **Não há TURN.**
- `res/layout/activity_config_api.xml:91`: exemplo `http://192.168.1.100:90/`.
- Não há chaves de API, senhas nem tokens versionados. Não existe `google-services.json`.

---

## 2. Build e toolchain

### 2.1 Versões

| Componente | Versão no projeto | Observação |
|---|---|---|
| Gradle wrapper | 9.1.0 (`gradle-wrapper.properties`) | Recente. O jar **não é versionado**. |
| AGP | 9.0.1 (`libs.versions.toml:2`) | Recente. O `gradle.properties` desliga vários padrões novos (`android.builtInKotlin=false`, `android.newDsl=false`, `enableAppCompileTimeRClass=false`, `usesSdkInManifest.disallowed=false`, `r8.optimizedResourceShrinking=false`…), **todos depreciados e com remoção prevista no AGP 10** (o próprio build avisa). |
| Kotlin | 2.2.10 + plugin Compose | OK, mas há versões 2.3.x. |
| compileSdk / targetSdk / minSdk | **34 / 34 / 28** (`app/build.gradle.kts:14,19,18`) | **O Google Play exige `targetSdk 35` para apps novos e atualizações desde 31/08/2025.** Pelo ciclo anual, a exigência deve subir para 36 em ago/2026 (confirmar a política vigente). Hoje o app **não pode ser publicado/atualizado**. |
| Java | `VERSION_11` / `jvmTarget "11"` (`:41-46`) | Funciona, mas o padrão atual é 17 e o AGP 9 roda com JDK 17+. |
| Compose BOM | `2024.02.00` (`:91`) | Desatualizado (~2,5 anos). |
| activity-compose | 1.8.2 | Desatualizado. |
| lifecycle | `viewmodel-ktx 2.6.2`, `runtime-ktx 2.6.2`, `viewmodel-compose 2.7.0` (`:65-66,98`) | **Versões misturadas** e antigas. O ViewModel nem é usado. |
| coroutines | 1.7.3 | Desatualizado (há 1.10.x). |
| core-ktx / appcompat / material / constraintlayout | 1.10.1 / 1.6.1 / 1.10.0 / 2.1.4 | Todos desatualizados. |
| navigation-fragment/ui | 2.6.0 | **Não é usado** (o `nav_graph` é template). |
| Retrofit / converter-gson | 2.9.0 | Desatualizado. O Gson ignora a nulidade do Kotlin (ver o problema #34). |
| OkHttp / logging | 4.12.0, declarado **duas vezes** (`:70,74`) | Há o 5.x. |
| WebRTC | `io.github.webrtc-sdk:android:125.6422.07` | Fork mantido, mas há builds bem mais novos. As libs estão alinhadas a 16 KB (verificado). |
| Firebase BOM | 33.5.1 + `firebase-messaging-ktx` | Os módulos `-ktx` foram descontinuados a partir do BOM 34, o que bloqueia o upgrade. **O plugin `google-services` não está aplicado** (`:5-9`, só em comentário). |
| DataStore | 1.0.0 | Desatualizado. |
| Glide | 4.16.0 | OK. |
| Dependências hardcoded fora do version catalog | quase todas (`:63-104`) | O catálogo só cobre os templates. Mistura de estilos. |

### 2.2 Configuração de release

- `isMinifyEnabled = false` (`app/build.gradle.kts:33`). O `proguard-rules.pro` é só o template, sem regras para os modelos do Gson nem para WebRTC/Retrofit. **Ligar o R8 hoje quebraria a desserialização.** Também não existe `signingConfig` de release.
- Não há *build flavors* nem configuração por ambiente. A URL vem de `buildConfigField` + DataStore.
- Não há lint/ktlint/detekt nem CI.

### 2.3 FCM

- O comentário em `app/build.gradle.kts:5-9` reconhece: *"Sem isso as chamadas a FirebaseApp.initializeApp falham em runtime."*
- Confirmado: **não existe `app/google-services.json`**, o plugin `com.google.gms.google-services` não está no catálogo nem aplicado, e **nenhum código chama `FirebaseMessaging.getInstance().token`**. O `FirebaseInitProvider` (merged manifest) apenas registra a falha de inicialização, então não há crash, mas também não há push. Detalhes no problema #5.

### 2.4 Resultado do build

```
JAVA_HOME = C:\Program Files\Android\Android Studio\jbr  (OpenJDK 21.0.8)
./gradlew.bat assembleDebug --offline --console=plain
BUILD SUCCESSFUL in 33s — 39 actionable tasks: 4 executed, 35 up-to-date
APK: app/build/outputs/apk/debug/app-debug.apk (81.034.046 bytes)
```

- **Warnings Kotlin:** `ChatActivity.kt:1088:9`, `:1137:30`, `:1137:55` (nome de parâmetro diferente do supertipo `CallListener`; risco com argumentos nomeados).
- **Warnings AGP:** 7 opções depreciadas em `gradle.properties`, mais 4 avisos de `excludeLibraryComponentsFromConstraints`.
- O `java` do PATH é 1.8 e não serve para o AGP 9. É preciso o JBR do Android Studio. Os `.bat` não setam `JAVA_HOME`.
- Não foram executados testes: só existem os templates `ExampleUnitTest` e `ExampleInstrumentedTest`.

---

## 3. Arquitetura atual

### 3.1 Pacotes (linhas de Kotlin por pacote, só o nível direto)

```
com.conversa.conversa                 1655  Activities "raiz": Splash, ConfigApi, Login, Main, Contatos,
                                            HistoricoChamadas, Application, First/SecondFragment (template)
├── adapter                            477  RecyclerView adapters (Conversas, Contatos, ContatosSelecionaveis,
│                                            HistoricoChamadas, ParticipantesAdapter [morto])
├── data
│   ├── api                            882  ConversaApi (50 endpoints), RetrofitClient (object),
│   │                                        Upload/DownloadHelper, UtcToLocalDateTimeDeserializer (sem package!)
│   ├── model                          557  DTOs (Gson)
│   ├── chamada/model                   42  EventoChamada/EventoChamadaUI (duplicata parcial de data/model)
│   ├── preferences                     91  UserPreferences (DataStore)
│   ├── repository                     738  ChamadaRepository (usado), Dispositivo (só pelo FCM),
│   │                                        Mensagem/Contatos/Sip/Usuario (NUNCA instanciados)
│   ├── socket                         500  SocketManager (OkHttp WebSocket + parser de eventos)
│   └── webrtc                         581  WebRTCManager (PeerConnectionFactory, WHIP/WHEP), WhipWhepClient
├── service                           2091  SocketService (FGS permanente), ChamadaService (FGS de chamada),
│                                            ChamadaActionReceiver, ConversaFcmService, ChamadaRingtoneManager,
│                                            NotificationConstants, SocketServiceHelper [morto]
├── notification                       447  MensagemNotificationManager (object), MensagemActionReceiver
├── ui
│   ├── chat                          2522  ChatActivity (1237!), MensagensAdapter (635), AudioPlayerHelper,
│   │                                        AudioRecorderHelper, ImageViewerActivity
│   ├── chamada (+components, screens) ~1570+ ChamadaActivity (Compose), ChamadaScreen, IncomingCallScreen,
│   │                                        screens/Active+Outgoing, components/* (Compose), SwipeButton (View,
│   │                                        morto), ParticipantesAdapter (morto), ChamadaNavigator
│   └── grupo                          316  criar_grupo_activity.kt / detalhes_grupo_activity.kt (package raiz!)
└── utils                              265  AppLifecycleManager (object), ChamadaServiceObserver
```

### 3.2 Componentes Android (manifest)

| Tipo | Componente | Exported | Notas |
|---|---|---|---|
| Activity | `SplashActivity` | true (LAUNCHER) | Pede permissões depois de `delay(2000)` |
| Activity | `ConfigApiActivity`, `LoginActivity`, `MainActivity`, `ChatActivity`, `ImageViewerActivity`, `ContatosActivity`, `HistoricoChamadasActivity`, `CriarGrupoActivity`, `DetalhesGrupoActivity` | false | XML + ViewBinding |
| Activity | `ui.chamada.ChamadaActivity` | false | `ComponentActivity` + Compose, `singleTop`, `showWhenLocked`, `turnScreenOn`, `portrait` |
| Service | `SocketService` | false | FGS `dataSync\|phoneCall\|microphone`, permanente, `START_STICKY` |
| Service | `ChamadaService` | false | FGS `microphone\|phoneCall\|camera`, `START_NOT_STICKY` |
| Service | `ConversaFcmService` | false | `MESSAGING_EVENT` (inerte sem Firebase configurado) |
| Receiver | `ChamadaActionReceiver` | false | Atender/recusar da notificação |
| Receiver | `MensagemActionReceiver` | false | Resposta direta e marcar como lida |
| Biblioteca | `FirebaseInstanceIdReceiver`, `ProfileInstallReceiver`, `PreviewActivity` (debug) | true | Vêm de bibliotecas, nada anormal |

### 3.3 Divisão Views x Compose

- **Compose:** apenas o fluxo de chamada (`ChamadaActivity` → `ChamadaScreen` → `IncomingCallScreen`/`OutgoingCallScreen`/`ActiveCallScreen` + `components/*`) e o `CallBanner`, embutido via `ComposeView` em `activity_main.xml` e `activity_chat.xml` (`CallBannerIntegration.kt`).
- **XML + ViewBinding:** todo o resto (11 Activities, 31 layouts, 3.437 linhas de XML). **Cerca de 10 layouts estão mortos** (`activity_chamada.xml`, `activity_chamada_incoming.xml`, `fragment_first/second/group_call/incoming_call/simple_call.xml`, `dialog_criar_grupo.xml`, `dialog_detalhes_grupo.xml`, `item_participante*.xml`; `dialog_contatos.xml` só aparece num import não usado).
- Tema: `Theme.Material3.DayNight` em XML; no Compose, `MaterialTheme {}` padrão com cores hardcoded (`0xFF0F172A`…). Não há design system comum.

### 3.4 Gerência de estado, DI e threading

- **Estado:** fica em campos `var` das Activities (`ChatActivity.conversaId`, `authToken`, `usuarioId`…), em `StateFlow`s dentro dos Services (`ChamadaService.estadoFlow`, `chamadaAtualFlow`, `timerFlow`), em singletons `object` (`RetrofitClient`, `MensagemNotificationManager`, `NotificationConstants`, `AppLifecycleManager`), em estáticos de companion (`SocketService.socketManagerGlobal`, `SocketService.lastValidToken`, `chamadaServiceAtivo` ×2) e em **callbacks `var` atribuíveis** no `SocketManager` (um consumidor por evento; o último a atribuir vence).
- **ViewModel:** **nenhum** (`grep ViewModel` não acha nada). O README fala em "MVVM parcialmente", o que não é verdade.
- **DI:** **nenhuma.** Tudo é instanciado na mão (`UserPreferences(this)` em cada Activity/Receiver, `ChamadaRepository(...)` dentro do `ChamadaService.onCreate`, `RetrofitClient.api` global).
- **Threading:** `lifecycleScope` nas Activities; `CoroutineScope(Dispatchers.IO + SupervisorJob())` próprio em `SocketManager`, `SocketService`, `ChamadaService`, `ChamadaRepository`, `MensagemRepository` e `WebRTCManager`; escopos **sem dono** em `AudioPlayerHelper` (`:92,179,294`), `ChamadaActionReceiver` (`:94`), `MensagemActionReceiver` (`:37`) e `ChamadaServiceObserver` (`:57`); **`runBlocking` na main thread** em `ChamadaService.kt:354` e `WebRTCManager.kt:303`. O parser do WS roda em `Dispatchers.Main` (`SocketManager.kt:178`).
- **Navegação:** Activities + Intents com extras em string (`"conversa_id"`, `"conversa_nome"`…) duplicados em vários lugares. O Navigation Component está declarado, mas só o `nav_graph` de template o usa.

### 3.5 Interação SocketService / ChamadaService / FCM (estado real)

```
                       ┌──────────────────────── processo do app ───────────────────────────────┐
 Splash ─► Login ─► MainActivity ──start FGS──► SocketService (FGS permanente, wakelock infinito)
                         │  bind/unbind            │  cria SocketManager  ──► wss://host:porta/ws/
                         │  setCallListener(this)  │  socketManagerGlobal (static)   (OkHttp WS)
                         │                         │
                         │      registrarListenersSocket() ◄── a cada bind / volta ao foreground
                         │         (SOBRESCREVE onChamadaRecebida, onUsuarioEntrou/Saiu/Recusou,
                         │          onChamadaFinalizada, onNovaMensagem, onDigitando…)
                         │
 ChatActivity ───────────┤  evento 51 (SocketService) ─► startForegroundService(ChamadaService,
   iniciarChamada()──────┼──────────────────────────────────── ACTION_CHAMADA_RECEBIDA)
   startFGS(ChamadaService, ACTION_INICIAR_CHAMADA) + startActivity(ChamadaActivity)
                         │
                         ▼
                 ChamadaService.onCreate
                   ├─ WebRTCManager(mediaMtx = apiUrl/api → /webrtc)  [runBlocking na main]
                   └─ ChamadaRepository(init) ── registrarHandlersSocket()
                          (SOBRESCREVE onChamadaRecebida/Entrou/Saiu/Recusou/Finalizada/VideoAtivado
                           do MESMO socketManagerGlobal; nunca desregistra)
                 eventos 52..55 chegam por QUEM estiver registrado por último:
                   • ChamadaRepository → trata (sincroniza peers, finaliza)          ✔
                   • SocketService     → só repassa se SocketService.chamadaServiceAtivo
                                         (NUNCA true) → DESCARTA                     ✘
                 ChamadaActivity (Compose) ── bind(flags=0) ── coleta StateFlows do service
                 ChamadaActionReceiver (notificação) ─► startActivity()  ✘ bloqueado Android 12+

 FCM: ConversaFcmService ──(nunca recebe nada: Firebase não inicializado)── startFGS(ChamadaService,
      action="CHAMADA_PUSH_RECEBIDA")  ✘ ação não tratada → FGS órfão
                       └────────────────────────────────────────────────────────────────────────┘
 Externo:  REST  https://host:4430/api/*    WS wss://host:4430/ws/    WHIP/WHEP https://host:4430/webrtc/call-{id}-u-{uid}/whip|whep
```

---

## 4. Inventário de features (por tela)

Legenda: **F** = funcional · **P** = parcial · **Q** = quebrado · **S** = stub/não implementado

| Tela / área | Feature | Como é implementada | Status |
|---|---|---|---|
| **Splash** (`SplashActivity`) | Pedido de permissões | `RequestMultiplePermissions` para `RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`(33+) e `BLUETOOTH_CONNECT`(31+), depois de `delay(2000)` (`:59-62`) | F |
| | Roteamento | Sem `apiUrl` → ConfigApi; caso contrário, **sempre vai para Login** (`:105-126`), mesmo com token salvo. O README diz que "verifica token salvo", o que não acontece. | P |
| **ConfigApi** | Salvar URL do servidor | Valida o prefixo http(s) e a `/` final e salva no DataStore. Não pré-preenche o valor atual nem testa a conexão. | F |
| **Login** | Login | `POST login` com `dispositivo_id = null` (`LoginActivity.kt:102`) e salva o token, o id e o nome | F |
| | "Lembrar" + auto-login | Salva **login e senha em texto puro** e refaz o login a cada abertura | F (inseguro) |
| | Cadastro | Endpoint `PUT usuario` declarado, sem UI | S |
| **Main** (lista de conversas) | Listar conversas | `GET conversas`. Recarrega a lista inteira em todo `onResume` e a cada evento WS 2 | P (pisca e não mostra erro/vazio; ver o problema #26) |
| | Badge de não lidas | `tvBadgeNaoLidas` existe em `item_conversa.xml:104-118`, mas o adapter nunca o preenche | S |
| | Hora da última mensagem | Sempre `HH:mm`, mesmo para dias anteriores (`ConversasAdapter.kt:47`) | P |
| | Drawer | Contatos, Histórico, Criar grupo, Configurações, **"Testar Vibração/Toque"** (debug exposto ao usuário) e Sair | F |
| | CallBanner | `ChamadaServiceObserver` + ComposeView, visível em `CHAMANDO`/`EM_CHAMADA` | F |
| | Deep link da notificação | A notificação manda `conversa_id`, mas a `MainActivity` nunca lê o extra | Q |
| | Logout | Para o SocketService e limpa o DataStore. Não para o ChamadaService, não desvincula o dispositivo e não limpa as notificações | P |
| **Contatos** | Listar e filtrar | `GET usuario/contatos`, com filtro local | F |
| | Abrir chat | Procura conversa 1:1 existente via `GET conversas`; se não houver, abre um chat "temporário" (`criar_conversa=true`) | F |
| | Ligar | `startForegroundService(ChamadaService, ACTION_INICIAR_CHAMADA)` + `ChamadaActivity` | P (ver chamadas) |
| | Adicionar/remover contato, presença online | Endpoints declarados no `ContatosRepository`, que está morto | S |
| **Chat** | Carregar mensagens | `GET mensagens?conversa&mensagemreferencia=0&mensagensprevias=50`. **Sem paginação** (só as 50 últimas). | P |
| | Enviar texto | `PUT mensagem` (tipo 1) e depois recarrega as 50. O texto é apagado antes de enviar e se perde se falhar. | P |
| | Criar conversa 1:1 no 1º envio | `PUT conversa` + 2× `PUT conversa/usuario`, sem transação nem checagem de retorno | P |
| | Enviar imagem | SHA-256 → `GET anexo/existe` → `PUT anexo?tipo=2` → `PUT mensagem` (tipo 2 + texto opcional) | F (lê tudo em memória) |
| | Enviar áudio | `MediaRecorder` AAC/M4A 128 kbps, mínimo 1 s e máximo 5 min, depois o mesmo fluxo com `tipo=4` | F |
| | Enviar arquivo genérico | Não existe | S |
| | Exibir imagem | Glide com header `Authorization`. Tela cheia em `ImageViewerActivity` (o token vai no extra). | P (falha com o cert mkcert do Android; ver o problema #11) |
| | Tocar áudio | Download automático para `cacheDir/audios`, `MediaPlayer` e SeekBar | P (falha com mkcert; leaks; ver o problema #31) |
| | Baixar arquivo | `GET anexo` e grava em `Downloads/Conversa` | P (scoped storage; ver o problema #33) |
| | Status (✓, ✓✓, azul) | Calculado no carregamento. **O WS tipo 3 não está ligado** (`onStatusMensagemAtualizado` nunca é atribuído). | P |
| | Marcar como visualizada | Um `GET mensagem/visualizar` **por mensagem**, em sequência | P |
| | Tempo real | WS 2 → `GET mensagens?…previas=5`, com deduplicação por id | F |
| | Digitando (enviar/receber) | **Não commitado.** `POST conversa/digitando` com throttle de 2,5 s; recebimento via WS 4 mostra "digitando..." | P |
| | Gravando (5) | Não é exibido. `broadcastGravando` não é usado. | S |
| | Online / visto por último | `"online"` hardcoded (`ChatActivity.kt:137,1144`) | Q (informação falsa) |
| | Responder, encaminhar, reagir, deletar, buscar | Estão no `MensagemRepository` (morto), sem UI | S |
| **Criar grupo** (2 telas) | Seleção e nome | `GET usuario/contatos` → `PUT conversa` (tipo 2) + N× `PUT conversa/usuario`. **O campo "descrição" é descartado** (`detalhes_grupo_activity.kt:64`). | P |
| | Gerenciar grupo (renomear, remover membro, sair) | Endpoints declarados, sem UI | S |
| **Histórico de chamadas** | Lista e detalhe | `GET chamadas`, ícones e duração. O diálogo mostra `criadoPor` como "contato" (`HistoricoChamadasActivity.kt:128`). Os estados de erro/vazio nunca aparecem. | P |
| | Religar | `GET chamada/dados` → `ACTION_INICIAR_CHAMADA` com os mesmos participantes | P |
| **Chamada recebida** | Toque e vibração | `ChamadaRingtoneManager` (Ringtone em loop + Vibrator) | P (**sem timeout** e parado pelo full-screen intent; ver o problema #7) |
| | Notificação CallStyle | `forIncomingCall` + full-screen intent + `timeoutAfter(60s)` | P |
| | Atender/Recusar pela notificação | Receiver → `startActivity` / `startService` | **Q** (Android 12+) / P |
| | Atender/Recusar na tela | Gestos de *arrastar* (Compose), sem alternativa por toque | P (acessibilidade) |
| | Chamadas perdidas | `NotificationConstants.moverParaPerdida`/`getNotificationIdMissed` existem, mas nunca são usados | S |
| | Recuperar toque perdido | No reconectar do WS: `GET chamadas/pendentes` → simula o evento 51 | F |
| **Chamada efetuada** | Iniciar | `PUT chamada/iniciar` (tipo 1 para 2 usuários, 2 para mais) → WHIP publish | F |
| | Cancelar antes de atender | Chama `POST chamada/sair`. **`chamada/cancelar` nunca é usado.** | P |
| | Destinatário recusa | O evento 53 vira apenas `eventosUIFlow`, que **ninguém coleta**. A tela fica em "Chamando..." | Q (validar se o backend manda 52) |
| | Timeout de chamada sem resposta | Não existe no cliente | S |
| **Chamada ativa** | Áudio 1:1 / grupo (mesh WHIP/WHEP) | Publica `call-{id}-u-{me}`, assina `call-{id}-u-{peer}` para cada `status=3` | F (foreground) / **Q** depois de minimizar (ver o problema #1) |
| | Mute | `AudioTrack.setEnabled` | F (o ícone só atualiza no próximo tick do timer) |
| | Alto-falante | `setCommunicationDevice` (31+) ou `isSpeakerphoneOn` | P (sem Bluetooth/fone com fio) |
| | Vídeo (upgrade) | `POST chamada/video` + captura 640×480@24 + re-POST WHIP | P / provável **Q** no lado remoto (ver o problema #12) |
| | Trocar câmera | Existe no repositório, sem UI | S |
| | Adicionar participante | O botão fica sempre desabilitado. `PUT chamada/usuario` não é usado. | S |
| | Minimizar + CallBanner | `finish()`; o banner volta para a tela | P (quebra os eventos; ver o problema #1) |
| | Sensor de proximidade | `PROXIMITY_SCREEN_OFF_WAKE_LOCK` | P (age mesmo com alto-falante ligado) |
| | Notificação em andamento | CallStyle `forOngoingCall`, atualizada todo segundo | P |
| **Notificações de mensagem** | MessagingStyle + resposta direta | `MensagemNotificationManager` + `MensagemActionReceiver` (`PUT mensagem`) | P (com o payload real do backend, tudo cai na "conversa 0"; ver o problema #14) |
| **Push (FCM)** | Token, mensagens e chamadas | Ver o problema #5 | **Q** |
| **SIP / perfil / alterar senha / deletar conta / galeria de anexos / busca** | — | Só endpoints em repositórios mortos | S |

---

## 5. Inventário técnico do contrato usado

### 5.1 Endpoints Retrofit (`P/data/api/ConversaApi.kt`)

A base é `RetrofitClient.currentBaseUrl` (padrão `BuildConfig.API_URL`, sobrescrita pelo DataStore em Splash/Login/Main). O header `Authorization: Bearer <jwt>` é passado **manualmente** em cada método. Não há interceptor nem authenticator.

| # | Método | Path | Parâmetros | Request → Response | Usado por (código vivo) |
|---|---|---|---|---|---|
| 1 | POST | `login` | body | `LoginRequest` → `LoginResponse` | LoginActivity |
| 2 | PUT | `usuario` | body (sem auth) | `CadastrarUsuarioRequest` → `UsuarioPerfil` | — (morto) |
| 3 | PATCH | `usuario` | body | `AtualizarUsuarioRequest` → `UsuarioPerfil` | — |
| 4 | POST | `alterar-senha` | body | `AlterarSenhaRequest` → Unit | — |
| 5 | DELETE | `usuario` | `id` | → Unit | — |
| 6 | PATCH | `dispositivo` | body | `AtualizarDispositivoRequest` → `DispositivoResponse` | DispositivoRepository (só pelo FCM `onNewToken`, que nunca dispara) |
| 7 | PUT | `dispositivo/usuario` | `dispositivo_id` | → Unit | — (o repositório tem o método, mas ninguém chama) |
| 8 | PUT | `usuario/contato` | `relacionamento_id` | → Unit | — |
| 9 | DELETE | `usuario/contato` | `id` | → Unit | — |
| 10 | GET | `contatos/online` | — | → `List<Int>` | — |
| 11 | GET | `conversas` | — | → `List<Conversa>` | Main, Contatos |
| 12 | GET | `conversa/dados` | `id` | → `ConversaCompleta` | ChatActivity (para montar a chamada) |
| 13 | GET | `usuario/contatos` | — | → `List<Contato>` | Contatos, CriarGrupo, DetalhesGrupo |
| 14 | PUT | `conversa` | body | `CriarConversaRequest` → `CriarConversaResponse` | Chat, DetalhesGrupo |
| 15 | PUT | `conversa/usuario` | body | `AdicionarUsuarioRequest` → Unit | Chat, DetalhesGrupo |
| 16 | DELETE | `conversa/usuario` | `id` | → Unit | — |
| 17 | PATCH | `conversa` | body `Map<String,Any?>` | → Unit | — |
| 18 | DELETE | `conversa` | `id` | → Unit | — |
| 19 | GET | `conversa/usuarios` | `conversa` | → `List<Contato>` | — |
| 20 | POST | `conversa/digitando` | body `DigitandoRequest{id}` | → Unit | ChatActivity (unstaged) |
| 21 | POST | `conversa/gravando` | body `DigitandoRequest{id}` | → Unit | — |
| 22 | GET | `mensagens` | `conversa`, `mensagemreferencia=0`, `mensagensprevias=50`, `mensagensseguintes=0` | → `List<Mensagem>` | ChatActivity |
| 23 | PUT | `mensagem` | body | `EnviarMensagemRequest` → `EnviarMensagemResponse` | Chat, MensagemActionReceiver |
| 24 | GET | `mensagem/visualizar` | `conversa`, `mensagem` | → Unit (**GET com efeito colateral**) | ChatActivity |
| 25 | POST | `mensagem/reproduzir` | body `{mensagem_id, conversa_id}` | → Unit | — |
| 26 | DELETE | `mensagem` | `id` | → Unit | — |
| 27 | PUT | `mensagem` | body | `EnviarMensagemComReferenciaRequest` → `EnviarMensagemResponse` | — |
| 28 | GET | `mensagens/novas` | `desde` (ISO) | → `List<MensagemNovaItem>` | — |
| 29 | GET | `mensagem/status` | `conversa`, `mensagem` | → `List<MensagemStatusResponse>` | — |
| 30 | PUT | `mensagem/reacao` | body | `ReacaoRequest` → `ReacaoResponse` | — |
| 31 | GET | `pesquisar` | `texto`, `conversa?`, `usuario?` | → `List<PesquisaResultado>` | — |
| 32 | GET `@Streaming` | `anexo` | `identificador` | → `ResponseBody` | DownloadHelper. Imagens/áudios usam a mesma URL montada à mão (`${apiUrl}anexo?identificador=`) via Glide/OkHttp |
| 33 | GET | `anexo/existe` | `identificador` | → `AnexoExisteResponse{existe}` | UploadHelper |
| 34 | PUT | `anexo` | `tipo`, `nome`, `extensao`, body octet-stream | → `AnexoUploadResponse{id,identificador,tipo,tamanho}` | UploadHelper |
| 35 | POST | `anexo/confirmar` | `identificador` | → Unit | — (o comentário cita "PUT presigned MinIO", mas **o app não usa presigned**; verificar o contrato atual do backend) |
| 36 | GET | `anexos` | `conversa?`, `autor?`, `direcao?`, `tipos?`, `antes?`, `limite?` | → `List<AnexoListItem>` | — |
| 37 | PUT | `chamada/iniciar` | body | `IniciarChamadaRequest` → `ChamadaResponse` | ChamadaRepository |
| 38 | POST | `chamada/entrar` | body `{id}` | → Unit | ChamadaRepository |
| 39 | POST | `chamada/recusar` | body `{id}` | → Unit | ChamadaRepository, ChamadaActionReceiver |
| 40 | POST | `chamada/sair` | body `{id}` | → Unit | ChamadaRepository (usado inclusive para "cancelar" e para "recusar" pela notificação) |
| 41 | POST | `chamada/cancelar` | body `{id}` | → Unit | — |
| 42 | POST | `chamada/finalizar` | body `{id}` | → Unit | — |
| 43 | GET | `chamada/dados` | `id` | → `ChamadaResponse` | ChamadaRepository, Historico, SocketServiceHelper (morto) |
| 44 | GET | `chamadas` | — | → `List<HistoricoChamada>` | Historico |
| 45 | PUT | `chamada/usuario` | body `{chamada_id, usuario_id}` | → Unit | — |
| 46 | POST | `chamada/video` | body `{id}` | → Unit | ChamadaRepository |
| 47 | GET | `chamadas/pendentes` | — | → `List<ChamadaResponse>` | SocketService |
| 48 | GET | `sip` | — | → `SipConfig` | — |
| 49 | PUT | `sip` | body | `SipRequest` → `SipConfig` | — |
| 50 | PATCH | `sip` | body | `SipRequest` → `SipConfig` | — |

**22 de 50 endpoints têm uso em código vivo.**

### 5.2 Modelos (Gson)

| Classe (arquivo) | Campos (JSON ← Kotlin) |
|---|---|
| `LoginRequest` (`data/model/LoginRequest.kt`) | `login`, `senha`, `dispositivo_id: Int?` |
| `LoginResponse` | `id`, `nome`, `email?`, `telefone?`, `token`, `dispositivo: Dispositivo?` |
| `Dispositivo` | `id`, `nome`, `modelo?`, `versao_so?`, `plataforma?`, `ativo` |
| `Contato` | `id`, `nome`, `login`, `email?`, `telefone?` |
| `Conversa` (`Conversa.kt`) | `id`, `descricao`, `tipo` (1=individual, 2=grupo), `inserida: LocalDateTime?`, `nome?`, `destinatario_id?`, `mensagem_id`, `ultima_mensagem: LocalDateTime?`, `ultima_mensagem_texto?`, `mensagens_sem_visualizar` (propriedades em snake_case, sem `@SerializedName`) |
| `ConversaCompleta` | campos de `Conversa` + `usuarios: List<UsuarioConversa>` |
| `UsuarioConversa` | `id`, `nome`, `login`, `email` |
| `Remetente` | `id`, `nome` (não usado) |
| `ConversasResponse` | `conversas` (não usado) |
| `Mensagem` | `id`, `conversa_id`→`conversaId`, **`remetente_id`→`usuarioId`**, `remetente`, `inserida: LocalDateTime` (não nulo!), `alterada?`, `recebida`, `visualizada`, `reproduzida`, `conteudos` |
| `Conteudo` | `id`, `tipo` (1 texto, 2 imagem, 3 arquivo, 4 áudio), `ordem`, `conteudo` (texto ou SHA-256 do anexo), `nome?`, `extensao?` |
| `EnviarMensagemRequest` | `conversa_id`, `conteudos: List<ConteudoRequest{tipo,ordem,conteudo,nome?,extensao?}>` |
| `EnviarMensagemResponse` | `id`, `usuario_id`, `conversa_id`, `inserida: String`, `alterada: String` |
| `CriarConversaRequest` | `descricao`, `tipo`, `inserida: String` (ISO **sem offset**) |
| `AdicionarUsuarioRequest` | `conversa_id`, `usuario_id` |
| `CriarConversaResponse` | `id`, `descricao`, `tipo`, `inserida?` |
| `IniciarChamadaRequest` | `tipo`, `usuarios: [{id}]` (inclui o próprio usuário) |
| `ChamadaResponse` | `id`, `iniciada?`, `finalizada?` (String), `tipo`, `status` (1 Pendente … 6 Cancelada), `criado_em`, `criado_por`, `usuarios` |
| `UsuarioChamada` | `usuario_id`, `usuario_nome`, `status` (1 Pendente, 2 Recusado, 3 Entrou, 4 Saiu), `adicionado_por`, `adicionado_por_nome`, `adicionado_em`, `entrou_em?` |
| `ChamadaIdRequest` | `id` |
| `HistoricoChamada` | `chamada_id`, `iniciada?`, `finalizada?`, `conversa_id?`, `tipo_chamada`, `status_chamada`, `criado_em?`, `criado_por_id`, `criado_por`, `usuario_exibido_id?`, `usuario_exibido_nome?`, `recusou_em?`, `entrou_em?`, `saiu_em?`, `status_usuario` (1–5), `adicionado_em?`, `adicionado_por_id?`, `adicionado_por?`, `tipo_acao` (1 realizada, 2 recebida, 3 desconhecido). Datas em `LocalDateTime`. |
| `AnexoExisteResponse` / `AnexoUploadResponse` (dentro de `ConversaApi.kt:412-421`) | `existe` / `id`, `identificador`, `tipo`, `tamanho` |
| `AnexoListItem` | `id`, `identificador`, `tipo`, `tamanho`, `nome?`, `extensao?`, `criado_em`, `criado_por`, `criado_por_nome?`, `conversa_id`, `mensagem_id?`, `url_download?` |
| `AtualizarDispositivoRequest` / `DispositivoResponse` | `id?`, `nome?`, `modelo?`, `versao_so?`, `plataforma?`, `token_fcm?` / + `usuario_id?`, `ativo` |
| `VincularDispositivoRequest` | `dispositivo_id` (não usado: o endpoint usa `@Query`) |
| `ReacaoRequest` / `ReacaoResponse` | `mensagem_id`, `emoji` / + `id`, `usuario_id`, `acao` ("add"/"remove") |
| `MensagemReferencia` | `id?`, `tipo` (1 responder, 2 encaminhar), `origem_mensagem_id?`, `destino_mensagem_id` |
| `EnviarMensagemComReferenciaRequest` | `conversa_id`, `conteudos`, `referencia?`, `visivel_em?` |
| `MensagemNovaItem` | `conversa_id`, `mensagem_id`, `ate` |
| `MensagemStatusResponse` | `mensagem_id`, `usuario_id`, `recebida?`, `visualizada?`, `reproduzida?` |
| `PesquisaResultado` | `mensagem_id`, `conversa_id`, `conversa_descricao?`, `usuario_id`, `usuario_nome`, `inserida`, `trecho` |
| `DigitandoRequest` | **`id`** (unstaged; antes era `conversa_id`) |
| `AdicionarContatoRequest` | `relacionamento_id` (não usado: o endpoint usa `@Query`) |
| `CadastrarUsuarioRequest`, `AtualizarUsuarioRequest`, `AlterarSenhaRequest`, `UsuarioPerfil` | `nome`, `login`, `email`, `senha`, `telefone?` / `avatar_anexo_id?` / `senha_atual`, `senha_nova` / + `avatar_url?`, `avatar_identificador?` |
| `SipConfig` / `SipRequest` | `id`, `usuario_id`, `sip_user`, `auth_user?`, `sip_password`, `display_name?`, `domain`, `ws_server`, `ativo` |

As datas do tipo `LocalDateTime` passam por `UtcToLocalDateTimeDeserializer` (`P/data/api/UtcToLocalDateTimeDeserializer.kt:21-29`), que trata a string como UTC e converte para o fuso do aparelho. Os campos de data `String` de `ChamadaResponse` ficam como estão.

### 5.3 WebSocket

- **URL:** `wss://{host}:{porta}/ws/` (`SocketManager.kt:133`). O host e a porta vêm da `apiUrl` (`MainActivity.kt:466-471`). Não há subprotocolo nem header de auth. **Não há ping** (`:59-61`). A reconexão usa backoff de 3/6/12/24/30 s, sem fim (`:433-455`).
- **Enviado:** só `{"tipo":1,"token":"<jwt>"}`, 100 ms depois do `onOpen` (`:218-238`).

| tipo | Constante | Campos lidos | Consumidor efetivo |
|---|---|---|---|
| 0 | `TYPE_ERRO` | `message` | `onErro` → SocketService (atualiza o texto da notificação) |
| 2 | `TYPE_NOVA_MENSAGEM` | (a) `mensagem{conversa_id, usuario_id\|remetente_id, texto}` + raiz `titulo`, `subtitulo`, `tipo_conversa`, `destinatario_id`; (b) legado: `conversa_id`, `remetente_id`, `destinatario_id`, `titulo`, `subtitulo`, `mensagem`, `tipo_conversa`; (c) **formato real do Delphi `{titulo, mensagem}`**, tratado como gatilho com `conversaId=0` | SocketService → notificação + Main/Chat |
| 3 | `TYPE_STATUS_MENSAGEM` | `mensagens` (array `[{conversa_id, mensagem_id}]` ou CSV) + `grupo` | **Ninguém** (`onStatusMensagemAtualizado` não é atribuído) |
| 4 | `TYPE_DIGITANDO` | `conversa_id`, `usuario_id` | SocketService → ChatActivity (unstaged) |
| 5 | `TYPE_GRAVANDO_AUDIO` | `conversa_id`, `usuario_id` | SocketService → `CallListener.onGravandoAudio`, sem efeito (default vazio) |
| 7 | `TYPE_REACAO_MENSAGEM` | `conversa_id`, `mensagem_id`, `usuario_id`, `emoji`, `acao` | Só `MensagemRepository` (morto) |
| 40 | `TYPE_CONVERSA_ATUALIZADA` | `conversa_id` ou `conversa.id` | **Ninguém** |
| 51 | `TYPE_CHAMADA_RECEBIDA` | `chamada_id`, `usuario_id`, `usuario_nome` | SocketService **ou** ChamadaRepository (o último que registrou) |
| 52 | `TYPE_CHAMADA_FINALIZADA` | `chamada_id`, `usuario_id` | idem; no SocketService é **descartado** |
| 53 | `TYPE_CHAMADA_USUARIO_RECUSOU` | `chamada_id`, `usuario_id` | idem; o Repository só emite um flow que ninguém coleta |
| 54 | `TYPE_CHAMADA_USUARIO_ENTROU` | `chamada_id`, `usuario_id` | idem |
| 55 | `TYPE_CHAMADA_USUARIO_SAIU` | `chamada_id`, `usuario_id` | idem |
| 56 | `TYPE_CHAMADA_VIDEO_ATIVADO` | `chamada_id`, `usuario_id` | ChamadaRepository → `videoRemotoAtivadoFlow` (ninguém coleta) |
| 60 | `TYPE_STATUS_USUARIO` | `usuario_id`, `online` | Só `ContatosRepository` (morto) |

`documentacao-oficial/DOCUMENTACAO_TECNICA.md:790-860` ainda descreve `ws://servidor:porta/`, só os tipos 0–3 e 51–55, e o áudio por **TCP 9090/PCM**. Está desatualizada em relação ao código (wss via nginx, WebRTC).

### 5.4 Payload FCM esperado (`ConversaFcmService.kt:47-67`)

- `data.tipo = "nova_mensagem"`: `conversa_id`, `titulo` \| `remetente_nome`, `mensagem`, `remetente_id`. **É ignorado** (`:69-73`, "já tratado via WS").
- `data.tipo = "chamada_recebida"`: `chamada_id`, `usuario_id`, `usuario_nome`. Inicia o `ChamadaService` com `action="CHAMADA_PUSH_RECEBIDA"` e extras `chamada_id`/`usuario_id`/`usuario_nome` (`:75-87`), **ação que o `ChamadaService.onStartCommand` não trata**.
- `onNewToken` → `PATCH dispositivo {token_fcm}` (sem `id`).

### 5.5 WebRTC / WHIP / WHEP

- **Base:** `apiUrl` sem `/api` + `/webrtc` (`ChamadaService.kt:353-360`), com fallback para `BuildConfig.MEDIAMTX_URL`.
- **Publicação:** `POST {base}/call-{chamadaId}-u-{meuId}/whip`, `Content-Type: application/sdp`, offer completa (sem trickle; espera até 3 s pelo gathering), sem retry (`WebRTCManager.kt:158-181`; `WhipWhepClient.kt:58-71`).
- **Assinatura:** `POST {base}/call-{chamadaId}-u-{peerId}/whep` com `addTransceiver` audio+video `RECV_ONLY`. Faz retry em **404** até **40× a cada 1 s**; outro status aborta (`WebRTCManager.kt:187-216`; `WhipWhepClient.kt:78-115`).
- **Sem** header de autenticação, **sem** leitura do `Location` e **sem** `DELETE` do recurso WHIP/WHEP ao desligar (as sessões ficam abertas no MediaMTX até expirar).
- **ICE:** só o STUN do Google, `UNIFIED_PLAN`, `MAXBUNDLE`, `rtcpMux REQUIRE`, `GATHER_CONTINUALLY` (`:356-364`). Sem TURN.
- **Codecs:** os padrões das factories (`DefaultVideoEncoderFactory(eglCtx, enableIntelVp8=true, enableH264HighProfile=true)` e `DefaultVideoDecoderFactory`), sem preferência de codec. O áudio fica no padrão (Opus). Constraints `googEchoCancellation`, `googNoiseSuppression` e `googAutoGainControl` (`:125-129`).
- **Câmera:** `Camera2Enumerator`, frontal, `startCapture(640, 480, 24)` (`:134-146`).
- **Reconexão:** ICE `FAILED`/`DISCONNECTED` → depois de 2 s, refaz o publish/subscribe do peer (`:311-352`).
- **Upgrade de vídeo:** `POST chamada/video` → adiciona o track no mesmo `txPc` → nova offer → **re-POST WHIP no mesmo path**, sem `DELETE` da sessão anterior (`:234-259`).

### 5.6 Fluxo de upload e download

- **Upload** (`UploadHelper.kt`): copia o `Uri` para `cacheDir/temp_image_*.jpg` (`:33`) → `readBytes()` + SHA-256 (`:160-165`) → `GET anexo/existe` → se não existir, `readBytes()` de novo e `PUT anexo?tipo=2|4&nome&extensao` com `application/octet-stream` (`:61-71`, `:124-134`) → devolve o `identificador` → `PUT mensagem` com `conteudo = identificador`.
- **Download de arquivo** (`DownloadHelper.kt`): `GET anexo` streaming → `Downloads/Conversa/{nome}.{ext}` via `File` (`:58-79`) → `DownloadManager.addCompletedDownload` (`:84-101`, depreciado).
- **Imagem:** Glide com `GlideUrl` + `LazyHeaders(Authorization)` (`MensagensAdapter.kt:236-263`). **Áudio:** OkHttp próprio para `cacheDir/audios/audio_{sha}.mp3`, com progresso (`AudioPlayerHelper.kt:302-367`).

---

## 6. Problemas encontrados

Formato: **#n [SEVERIDADE] Título.** Local, descrição e correção sugerida.

### CRÍTICO

**#1 [CRÍTICO] Dois donos disputam os callbacks do `SocketManager`, e a flag que libera os eventos 52–55 nunca fica `true`.**
- **Local:** `P/service/SocketService.kt:90-91` (`chamadaServiceAtivo` é declarada e nunca atribuída a `true` em todo o código), `:249-259` (re-registro a cada `setCallListener`), `:156-185` (re-registro a cada volta ao foreground), `:287-349`; `P/data/repository/ChamadaRepository.kt:233-296`; `P/service/ChamadaService.kt:80-81` (**outra** flag com o mesmo nome, que é a única setada).
- **O que acontece:** os callbacks são `var` únicos, então quem atribui por último vence. Durante uma chamada, basta voltar à Chat/Main (minimizar, botão voltar, banner) para o `SocketService` sobrescrever os handlers do repositório. Daí em diante, "finalizou", "entrou", "saiu" e "recusou" são **descartados**, porque a flag é sempre falsa. E mesmo que passassem, o `ChamadaService.onStartCommand` (`:204-236`) não trata `USUARIO_ENTROU/SAIU/RECUSOU`.
- **Efeitos:** o chamador nunca assina a mídia de quem atendeu (fica sem áudio). O hang-up remoto não encerra a chamada local. A chamada fica "fantasma" com o FGS ligado.
- **Correção:** trocar os callbacks por um `SharedFlow<SocketEvent>` com vários assinantes no `SocketManager` (ou um event bus) e eliminar a flag e o re-registro. Ter uma única fonte de verdade para o estado da chamada (um `CallManager` de escopo de aplicação).

**#2 [CRÍTICO] Os handlers do repositório morto continuam registrados depois da chamada, e a próxima chamada recebida é perdida.**
- **Local:** `ChamadaRepository.kt:218-223` (`cleanup()` cancela o `scope` mas não desregistra nada); `:234-243` (`onChamadaRecebida` faz `scope.launch` num escopo cancelado, ou seja, no-op); `ChamadaService.kt:238-248`; `SocketService.kt:93-96` (o `socketManagerGlobal` sobrevive).
- **O que acontece:** depois de qualquer chamada, se nenhuma Activity se re-vincular ao `SocketService` (por exemplo, o app ficou em segundo plano ou a chamada foi encerrada pela notificação), o evento 51 cai num repositório morto. **O telefone não toca.** De quebra, o repositório, o `WebRTCManager` (EGL, factory) e o `Service` ficam vazando pelas closures.
- **Correção:** a mesma do #1, mais desregistro explícito no `cleanup()`. O roteamento de "chamada recebida" deve morar num componente de escopo de aplicação, nunca num objeto da vida da chamada.

**#3 [CRÍTICO] "Atender" pela notificação não funciona no Android 12+ (trampolim de notificação).**
- **Local:** `ChamadaService.kt:454-461` (o `answerPI` é `PendingIntent.getBroadcast`); `P/service/ChamadaActionReceiver.kt:56-76` (`context.startActivity(...)` dentro do receiver).
- **O que acontece:** com `targetSdk ≥ 31`, o sistema bloqueia o `startActivity` vindo de um receiver disparado por ação de notificação. O receiver só para o toque e cancela a notificação, e a chamada continua tocando do outro lado.
- **Correção:** fazer o `answerPI` ser `PendingIntent.getActivity(ChamadaActivity, auto_answer=true)`, ou usar o `Core-Telecom`/`ConnectionService` (ver #20).

**#4 [CRÍTICO] Log em nível `BODY` também em release: senha, JWT e conteúdo das mensagens vão para o logcat.**
- **Local:** `P/data/api/RetrofitClient.kt:23-29` (sem checagem de `BuildConfig.DEBUG`, sem `redactHeader("Authorization")`).
- **Locais adicionais:**
  - `SocketManager.kt:176` registra **todo** frame recebido (mensagens, nomes).
  - `SocketManager.kt:417` registra os 100 primeiros caracteres de cada envio, o que **inclui ~80 caracteres do JWT** no frame de login.
  - `ChatActivity.kt:1151` registra o prefixo do token.
  - `ConversaFcmService.kt:32` registra o token FCM e `:48` o payload inteiro.
- **Correção:** `level = if (DEBUG) BODY else NONE` mais `redactHeader("Authorization")`. Remover os logs de payload e token. Usar Timber com uma árvore de release silenciosa.

**#5 [CRÍTICO] FCM inoperante, e o app depende 100% de um socket em FGS.**
- **Local:**
  - `app/build.gradle.kts:5-9` (plugin ausente); não existe `app/google-services.json`.
  - Nenhum `FirebaseMessaging.getInstance().token`.
  - `LoginActivity.kt:102` (`dispositivo_id = null`); `DispositivoRepository.registrarOuAtualizar`/`vincularAoUsuario` nunca são chamados.
  - `ConversaFcmService.kt:78` usa a ação `"CHAMADA_PUSH_RECEBIDA"`, que não é tratada em `ChamadaService.kt:207-233`. Se um push chegasse, o serviço subiria em foreground (`:151-159`) e **nunca pararia**.
  - Não há `BOOT_COMPLETED`: depois de reiniciar o aparelho, o app fica mudo até o usuário abri-lo.
- **Correção:** configurar o Firebase, registrar o token no login e no `onNewToken` (`PATCH dispositivo` + `PUT dispositivo/usuario`), tratar o push de chamada como *high priority* abrindo a UI de chamada (via Telecom) e tornar o socket um complemento de primeiro plano.

**#6 [CRÍTICO] Senha em texto puro e backup irrestrito.**
- **Local:** `P/data/preferences/UserPreferences.kt:22-23,64-69` (salva `saved_password`); `LoginActivity.kt:118-120` (auto-login reenvia a senha); `AndroidManifest.xml:41-43` (`allowBackup="true"`); `res/xml/backup_rules.xml` e `data_extraction_rules.xml` são só templates sem `<exclude>`.
- **O que acontece:** o DataStore com senha e JWT entra no backup em nuvem e na transferência entre aparelhos.
- **Correção:** não guardar a senha. Manter só o token (idealmente com refresh) em armazenamento cifrado (Keystore/Tink) e excluir o DataStore do backup, ou usar `allowBackup=false`.

**#7 [CRÍTICO] O full-screen intent cala o toque da chamada recebida.**
- **Local:** `P/ui/chamada/ChamadaActivity.kt:150-151` (`ChamadaRingtoneManager.parar()` incondicional no `onCreate`), combinado com `ChamadaService.kt:472-488` (`setFullScreenIntent(fsPI, true)`).
- **O que acontece:** com a tela bloqueada ou apagada, o sistema abre a `ChamadaActivity` na hora e o toque para em milissegundos. O usuário só vê a tela, sem som.
- **Correção:** parar o toque apenas ao atender ou recusar, quando o estado sair de `RECEBENDO_CHAMADA`, ou quando a chamada for encerrada remotamente.

### ALTO

**#8 [ALTO] O `ChamadaService` não encerra em vários caminhos (FGS e notificação órfãos, wakelock de 30 min).**
- **Local:**
  - `ChamadaService.kt:217-222`: um `FINALIZADA` remoto chama só `repository.finalizarChamada()`, sem `stopSelf`, sem parar o toque e sem cancelar a notificação de entrada.
  - `:252-258`: falha em `iniciarChamada` só zera a flag.
  - FCM (#5).
  - `ChamadaActionReceiver.kt:88-92`: "recusar" manda `ACTION_CHAMADA_FINALIZADA`, que chama **`/chamada/sair`**, em paralelo com o `/chamada/recusar` disparado pelo próprio receiver (`:95-117`), e não para o serviço.
- **Correção:** uma máquina de estados explícita, em que todo estado terminal executa `stopForeground(REMOVE)` + `stopSelf` + limpeza do toque e das notificações.

**#9 [ALTO] O ciclo de vida da chamada no chamador está incompleto.**
- **Local:**
  - `ChamadaRepository.kt:268-273`: o evento 53 (recusou) não encerra uma chamada 1:1.
  - `:132-145`: "cancelar antes de atender" usa `sairChamada`; `ConversaApi.kt:340-344` (`cancelarChamada`) nunca é usado, assim como `finalizarChamada` (`:349-353`).
  - `:65-68`: `onErro`, `onChamadaIniciada`, `onChamadaConectada` e `onChamadaFinalizada` nunca são atribuídos, então falhas de WHIP ficam invisíveis.
  - Não existe timeout de "Chamando..." no cliente.
- **Correção:** mapear 1:1 o contrato do backend (cancelar, recusar, sair, finalizar), tratar o evento 53 e criar um timeout local. **Validar no backend** se ele emite 52 depois que todos recusam.

**#10 [ALTO] O vídeo remoto nunca chega à UI (corrida no `onAddTrack`).**
- **Local:** `P/data/webrtc/WebRTCManager.kt:383-396` (`val peer = rxPcs[peerId] ?: return`) versus `:210-215`, onde o peer só é inserido em `rxPcs` **depois** de `setRemoteDescriptionAwait`.
- **O que acontece:** o `onAddTrack` dispara durante o `setRemoteDescription`, então o `VideoTrack` é descartado e o "BUGFIX" staged do renderer nunca recebe track. O áudio só funciona porque o WebRTC toca os tracks remotos por padrão.
- **Correção:** inserir o `PeerRemoto` antes da negociação, ou capturar os tracks via `pc.transceivers` depois do `setRemote`. Proteger `rxPcs` com o mutex ou com um `ConcurrentHashMap` (também está exposto a corrida de threads, ver #36).

**#11 [ALTO] TLS e cleartext inconsistentes e inseguros.**
- **Local:** `AndroidManifest.xml:49` (`usesCleartextTraffic="true"` global) e **nenhum `res/xml/network_security_config.xml`**.
- **Trust-all + `hostnameVerifier { true }` em DEBUG**, em três clientes diferentes: `RetrofitClient.kt:34-45`, `SocketManager.kt:66-75`, `WhipWhepClient.kt:37-50`. Qualquer build debug distribuído a testadores é vulnerável a MITM.
- **Efeito colateral funcional:** Glide (`MensagensAdapter.kt:242-252`, `ImageViewerActivity.kt:63-75`) e o OkHttp do `AudioPlayerHelper.kt:322-326` **não** usam o trust-all. Com o certificado mkcert do servidor, **imagens e áudios falham em debug**, e o Android 7+ não confia em CA de usuário sem `network_security_config`.
- **Correção:** um `network_security_config` com `<debug-overrides>` confiando na CA do mkcert (`@raw/mkcert_ca`), cleartext desligado e um único `OkHttpClient` compartilhado (Retrofit, WS, WHIP, Glide via `OkHttpUrlLoader`, áudio).

**#12 [ALTO] O WHIP/WHEP não tem autenticação, não tem TURN e não encerra as sessões.**
- **Local:** `WebRTCManager.kt:366-367` (paths previsíveis `call-{id}-u-{uid}`, com ids sequenciais); `WhipWhepClient.kt:58-71,78-115` (sem `Authorization`, sem `Location`/`DELETE`); `WebRTCManager.kt:357` (só STUN).
- **Riscos:**
  - Se o nginx/MediaMTX não validar o JWT, qualquer um pode assinar o áudio/vídeo de qualquer chamada (**validar no backend**).
  - Sem TURN, chamadas em 4G/CGNAT/NAT simétrico falham.
  - O re-POST de WHIP no upgrade de vídeo (`:247-258`) depende do `overridePublisher` do MediaMTX e derruba os assinantes (provavelmente "funciona" via reconexão de ICE, com glitch).
- **Correção:** token de curta duração por chamada no header (o MediaMTX aceita auth via HTTP/JWT), TURN (coturn) via endpoint `/ice-servers`, `DELETE` no `Location` ao desligar, e renegociação via PATCH ou nova sessão explícita.

**#13 [ALTO] A URL base é global e pode apontar para o host errado em processo frio.**
- **Local:** `RetrofitClient.kt:20` (padrão `BuildConfig.API_URL` = 192.168.2.5). O `setBaseUrl` só é chamado em `SplashActivity.kt:91`, `LoginActivity.kt:41` e `MainActivity.kt:296`.
- **O que acontece:** receivers e serviços iniciados pelo sistema em processo novo (resposta direta da notificação, recusar pela notificação, `ChamadaService` reiniciado, FCM) usam o IP fixo de build.
- **Correção:** resolver a URL num ponto único e assíncrono (`Application`/DI) ou num interceptor que lê a configuração.

**#14 [ALTO] As notificações de mensagem quebram com o payload real do backend (`conversaId=0`).**
- **Local:** `SocketManager.kt:280-289` (staged); `SocketService.kt:557-573`; `MensagemNotificationManager.kt:86-114` (agrupa por `conversaId`); `NotificationConstants.kt:59-61` (id `3000 + conversaId%1000`, com colisões entre conversas); `MensagemActionReceiver.kt:45-49` (aceita `0`) → `PUT mensagem` com `conversa_id=0`; `MensagemNotificationManager.kt:138-141` (extra `conversa_id`, **ignorado** pela `MainActivity`).
- **O que acontece:** todas as conversas viram uma única notificação "conversa 0", a resposta direta falha e o toque abre só a lista.
- **Correção:** o backend precisa enviar `conversa_id` e `mensagem_id` no evento 2 (e no push). Como alternativa, ao receber o gatilho, buscar `GET mensagens/novas` para descobrir a conversa. Implementar o deep link na `MainActivity`/`ChatActivity`.

**#15 [ALTO] `SocketService`: FGS permanente que vai contra a política e gasta bateria.**
- **Local:** `AndroidManifest.xml:51-55` (tipos `dataSync|phoneCall|microphone` num serviço que só mantém socket).
- `SocketService.kt:214`: `startForeground` **sem tipo**, então o sistema assume todos os tipos do manifest. O tipo `microphone` exige *while-in-use*, o que faz o restart via `START_STICKY` (`:221`) em segundo plano ser rejeitado no Android 14 (`SecurityException`).
- `:140-146`: `PARTIAL_WAKE_LOCK` **sem timeout**, segurado enquanto o serviço vive.
- `:156-185`: loop `while(isActive){ delay(500) }` permanente.
- Com `targetSdk 35`, o tipo `dataSync` fica limitado a 6 h por dia (`onTimeout`). O Play não aceita "manter socket" como justificativa de `dataSync`.
- **Correção:** FCM de alta prioridade como canal primário (#5), WS só em primeiro plano (ou FGS `specialUse`/`remoteMessaging` justificado), nada de wakelock, e trocar o polling por `ProcessLifecycleOwner`.

**#16 [ALTO] `SocketManager` não é robusto.**
- **Local:**
  - `:59-61`: sem `pingInterval`. Com o nginx na frente, a conexão ociosa cai pelo `proxy_read_timeout` (60 s por padrão) e conexões *half-open* não são detectadas.
  - `:123-155`: `conectar()` cria um novo `WebSocket` **sem fechar o anterior**. `onFailure` e `onClosed` (`:188-212`) e o "forçar reconexão" do `SocketService` (`:170-178`) podem gerar **dois sockets autenticados** e duplicar eventos (notificações e intents de chamada duplicados).
  - `:82-83`: `isConnected` e `reconnectAttempts` não são `@Volatile`, mas são escritos em várias threads.
  - `:465`: `shouldReconnect` é declarado depois do uso.
- **Correção:** `pingInterval(20s)`, uma máquina de estados com um único socket ativo (fechar ou ignorar listeners obsoletos por id de geração) e `StateFlow` de conexão.

**#17 [ALTO] `runBlocking` na main thread (risco de ANR).**
- **Local:** `ChamadaService.kt:353-360` (lê o DataStore no `onCreate`); `WebRTCManager.kt:302-307` (`cleanup()` → `runBlocking(Dispatchers.IO){ desligar() }`, chamado em `ChamadaService.onDestroy`, na main). O comentário diz que "não bloqueia a main", o que **é falso**.
- **Agravante:** `desligar()` espera o `mutex`, que pode estar preso por um WHEP em retry de até 40 s (#18).
- **Correção:** inicialização assíncrona e encerramento em escopo de aplicação (`ProcessLifecycleOwner`/`GlobalScope` controlado), sem bloquear.

**#18 [ALTO] O mutex do `WebRTCManager` fica preso durante a rede.**
- **Local:** `WebRTCManager.kt:183-209` (`assinarDePeer` segura o mutex durante até 40 tentativas de 1 s, mais 3 s de ICE); `:154-156`; `:270`.
- **O que acontece:** as assinaturas de peers ficam serializadas (num grupo de 4, o último pode esperar minutos), e `desligar()` e o upgrade de vídeo ficam bloqueados.
- **Correção:** segurar o lock só para mutar o estado e fazer a rede fora dele, com cancelamento por peer (`Job` por peer).

**#19 [ALTO] `wss://` fixo versus porta derivada de `http://` (mudança staged).**
- **Local:** `SocketManager.kt:133` (sempre `wss`) versus `MainActivity.kt:466-471` (`http` → porta 80).
- **O que acontece:** com a `apiUrl` `http://…` (que a `ConfigApiActivity` aceita e cujo exemplo de tela mostra), o WS tenta TLS na porta 80 e falha para sempre.
- **Correção:** derivar o esquema (`ws`/`wss`) da `apiUrl`, ou proibir `http`.

**#20 [ALTO] Sem integração com o sistema de telefonia (`ConnectionService`/Core-Telecom).**
- **Local:** `MANAGE_OWN_CALLS` declarado (`AndroidManifest.xml:28`), mas nenhum `ConnectionService`.
- **Consequências:** chamadas GSM concorrentes não colocam a chamada VoIP em espera; botões de fone Bluetooth, carro e relógio não funcionam; o roteamento de áudio é manual; o atendimento por notificação depende de hacks (#3).
- **Correção:** `androidx.core:core-telecom` (CallsManager), que também resolve o roteamento de áudio (#21).

**#21 [ALTO] Áudio de chamada: modo nunca restaurado, sem foco e sem roteamento.**
- **Local:** `WebRTCManager.kt:148-151,263-267` e `ChamadaService.kt:302-308` (`MODE_IN_COMMUNICATION`, nunca volta para `MODE_NORMAL` nem chama `clearCommunicationDevice`). Não há `requestAudioFocus`, nem tratamento de Bluetooth SCO/LE ou de fone com fio (`BLUETOOTH_CONNECT` é pedido e não usado). O sensor de proximidade age mesmo com o alto-falante ligado (`ChamadaActivity.kt:226-252`).
- **Efeitos:** depois de uma chamada, outros apps podem tocar pelo fone de ouvido do aparelho ou com volume de chamada, e músicas não pausam durante a chamada.
- **Correção:** um `AudioRouteManager` (ou o Core-Telecom) com foco `AUDIOFOCUS_GAIN_TRANSIENT`, `setCommunicationDevice` por rota e restauração no fim.

**#22 [ALTO] Vazamento de Activity e de objetos de chamada.**
- **Local:** `SocketService.kt:60,230-235` (o `callListener` é mantido depois do unbind) com `MainActivity.kt:509-521` (nunca chama `setCallListener(null)`), o que retém a `MainActivity` destruída. Há também o #2 (repositório e WebRTC retidos) e o `AppLifecycleManager.kt:21` (Activity estática).
- **Correção:** nada de listeners com referência forte em serviços (use Flows coletados com `repeatOnLifecycle`).

**#23 [ALTO] O `gradle-wrapper.jar` não é versionado.**
- **Local:** `.gitignore:15` (`*.jar`); `git ls-files gradle/` mostra só o `.properties` e o catálogo.
- **O que acontece:** um clone novo (ou a CI) não executa `./gradlew`.
- **Correção:** `!gradle/wrapper/gradle-wrapper.jar` no `.gitignore` e commitar o jar.

**#24 [ALTO] `targetSdk`/`compileSdk` 34.**
- **Local:** `app/build.gradle.kts:14,19`.
- **O que acontece:** o app não é aceito no Play (exige 35 desde ago/2025). A migração traz edge-to-edge obrigatório (os layouts XML atuais vão sobrepor as barras), o limite de `dataSync` (#15), mudanças de FGS e o comportamento de back preditivo (#40).
- **Correção:** planejar junto com a reescrita (seção 8).

**#25 [ALTO] Acessibilidade das telas de chamada.**
- **Local:** `IncomingCallScreen.kt:105-180,373-394` (atender/recusar **só por arrasto**, sem `onClick`/`semantics`/`customActions`; TalkBack não consegue atender); `:229,398` (emojis 👤/📞 como ícone); `CallControls.kt:46-61` (mute usa `Close`/`Phone`, alto-falante usa `Settings`, ícones semanticamente errados); `ParticipantAvatar` sem `contentDescription`.
- **Correção:** botões tocáveis com rótulos, `Modifier.semantics { onClick(...) }`, ícones corretos (`material-icons-extended` ou vetores próprios) e alvos de 48 dp.

**#26 [ALTO] Estados de erro e vazio nunca aparecem (Main e Histórico).**
- **Local:** `MainActivity.kt:342-344` (`finally { mostrarLoading(false) }`) com `:347-354` (`mostrarLoading(false)` esconde `layoutErro`/`layoutVazio` e mostra a lista) depois de `mostrarErro`/`mostrarVazio`; o mesmo em `HistoricoChamadasActivity.kt:114-115` / `:302-309`.
- **Correção:** um `UiState` único (Loading/Content/Empty/Error) renderizado num só lugar.

**#27 [ALTO] Upload carrega o arquivo inteiro em memória, 2 a 3 vezes.**
- **Local:** `UploadHelper.kt:61-63,124-126,160-165` (`readBytes()` para o hash e de novo para o corpo).
- **O que acontece:** vídeos e arquivos grandes causam OOM. Não existe envio de arquivo genérico nem de vídeo.
- **Correção:** hash em streaming (`DigestInputStream`) e `RequestBody` baseado em `File`/`ContentResolver` (`asRequestBody`). Avaliar upload presigned se o backend suportar (`confirmarAnexo`).

### MÉDIO

**#28 [MÉDIO] Corrida ao atender ou recusar logo que a chamada chega.**
- **Local:** `ChamadaService.kt:261-281`. Se `_chamadaAtualFlow` ainda é `null` (o `GET chamada/dados` está em andamento), `return@launch` **antes** de parar o toque. O toque do usuário é ignorado.
- **Correção:** usar o `chamadaId` do intent como fonte e enfileirar a ação.

**#29 [MÉDIO] Hora da mensagem some com frequência.**
- **Local:** `MensagensAdapter.kt:138,229` + `:567-577`. `LocalDateTime.toString()` omite os segundos quando são `:00` e inclui frações quando há milissegundos. Nesses casos o parse com `"yyyy-MM-dd'T'HH:mm:ss"` falha e devolve `""`.
- **Correção:** formatar direto o `LocalDateTime` (`format(ofPattern("HH:mm"))`), mais separadores de data.

**#30 [MÉDIO] Datas: suposições frágeis.**
- **Local:** `UtcToLocalDateTimeDeserializer.kt:18-24`. Assume UTC quando não há offset e lança exceção em formatos fora de ISO (por exemplo, com espaço), o que derruba o parse da lista inteira. O arquivo **não tem `package`** (`:1`).
- O cliente envia `LocalDateTime.now()` sem offset (`ChatActivity.kt:609-610`, `detalhes_grupo_activity.kt:107`).
- **Correção:** combinar com o backend o formato ISO-8601 com offset ou `Z`, usar `Instant`/`OffsetDateTime` e um parser tolerante que registra e ignora em vez de lançar.

**#31 [MÉDIO] `AudioPlayerHelper` e o adapter vazam e têm corridas.**
- **Local:** `AudioPlayerHelper.kt:92,179,294` (`CoroutineScope` sem dono, que sobrevive à Activity); `:44,47` (`mutableMapOf` acessado por IO e Main); `:334` (`Response` não fechado); `:316-319` (arquivo parcial de download interrompido é reaproveitado como "pronto").
- `MensagensAdapter.kt:582-608`: `onViewRecycled` usa `adapterPosition`, que já é `NO_POSITION`, então os callbacks nunca são removidos e um holder reciclado recebe atualizações de outro áudio. `:424-450`: um `Handler` novo a cada play. `:335`: download antecipado de **todos** os áudios visíveis.
- **Correção:** player único com `StateFlow` por id coletado no `ViewHolder` com `repeatOnLifecycle`, Media3/ExoPlayer, download sob demanda em arquivo `.part` com rename atômico e cache LRU.

**#32 [MÉDIO] O launcher de permissão de microfone é compartilhado.**
- **Local:** `ChatActivity.kt:204-216` (callback chama `iniciarGravacaoAudio`) e `:921-934` (reusado pelo botão de ligar). O comentário em `:930` diz o contrário.
- **O que acontece:** ao conceder a permissão para ligar, o app começa a **gravar um áudio**.
- **Correção:** launchers separados ou guardar a ação pendente.

**#33 [MÉDIO] `DownloadHelper` não respeita o scoped storage e permite path traversal.**
- **Local:** `DownloadHelper.kt:58-79` (`getExternalStoragePublicDirectory` + `File`). No Android 10 (API 29, dentro do `minSdk 28+`), isso falha sem `requestLegacyExternalStorage`. `:89-97` usa `addCompletedDownload`, depreciado. `:69` monta `File(conversaDir, "$nome.$ext")` com `nome`/`ext` vindos do servidor e sem sanitização (por exemplo, `../`).
- **Correção:** `MediaStore.Downloads` (29+) ou SAF, com o nome sanitizado.

**#34 [MÉDIO] Nulidade do Kotlin furada pelo Gson.**
- **Local:** `Mensagem.kt:15-16` (`remetente: String`, `inserida: LocalDateTime` não nulos); `Conteudo.conteudo`; `Conversa.descricao`; `UsuarioConversa.email`. O Gson injeta `null` via reflexão, o que gera NPE em pontos distantes.
- **Correção:** Moshi/kotlinx-serialization com validação, ou tipos anuláveis com mapeamento para o domínio.

**#35 [MÉDIO] Chat: perda de mensagem, ausência de paginação e N+1.**
- **Local:**
  - `ChatActivity.kt:521`: o texto é apagado antes do envio e não volta se der erro.
  - `:463-469`: só as 50 últimas mensagens, sem "carregar anteriores".
  - `:665-683`: um `GET mensagem/visualizar` por mensagem, em sequência.
  - Não há cache local (Room) nem modo offline.
  - `:397,840`: depois de enviar imagem ou áudio, `carregarMensagens()` com spinner esconde a lista.
- **Correção:** envio otimista com fila (WorkManager), paginação (Paging 3), marcar como lida em lote (até a última id) e Room.

**#36 [MÉDIO] Concorrência no `WebRTCManager`.**
- **Local:** `:82` (`rxPcs` é `mutableMapOf`, alterado sob mutex em coroutines mas lido no `onAddTrack` da thread de sinalização, `:385`); `:89` (`reconnectJobs` sem sincronização).
- **Correção:** confinar o estado a um único dispatcher (`limitedParallelism(1)`) ou a um actor.

**#37 [MÉDIO] Mídia nativa não liberada.**
- **Local:** `WebRTCManager.kt:276-292`. Os tracks locais (`localVideoTrack`/`localAudioTrack`) e as `PeerConnection` não recebem `dispose()` (só `close()`), e a `PeerConnectionFactory` é recriada a cada `ChamadaService` (`ChamadaRepository.kt:73`, `WebRTCManager.kt:101-115`).
- **Correção:** chamar `dispose()` em tracks e PCs e manter uma factory e um EGL por processo.

**#38 [MÉDIO] ICE sem trickle: sempre 3 s de latência por PeerConnection.**
- **Local:** `WebRTCManager.kt:172,198,253` + `:426-432`. Com `GATHER_CONTINUALLY` (`:362`), o estado raramente chega a `COMPLETE`, então cada publish ou subscribe espera o timeout inteiro.
- **Correção:** `GATHER_ONCE`, ou trickle via `PATCH` WHIP/WHEP (o MediaMTX suporta).

**#39 [MÉDIO] Problemas no renderer de vídeo (Compose).**
- **Local:** `WebRTCVideoRenderer.kt:33-63`. Se o `videoTrack` mudar na mesma posição, o `DisposableEffect` libera o renderer, mas a factory do `AndroidView` não roda de novo, e o resultado é superfície preta. O `remember` sem chave piora isso.
- `ActiveCallScreen.kt:107-125` sobrepõe dois `SurfaceView` sem `setZOrderMediaOverlay(true)`, então o PiP pode ficar atrás do vídeo remoto.
- `:72-111`: `WebRTCVideoRendererSafe` é uma duplicata.
- **Correção:** `key(videoTrack){ … }`, `onRelease` do `AndroidView` e `TextureViewRenderer` ou ajuste de z-order.

**#40 [MÉDIO] Compose: efeitos colaterais na composição e estado não reativo.**
- **Local:** `ChamadaScreen.kt:21-25,121-125` (`onFinish()`, ou seja, `finish()`, chamado **durante a composição**, possivelmente várias vezes); `:95-96` (`isMuted()`/`isSpeakerOn()` são getters não observáveis, então o ícone só atualiza no próximo tick do timer).
- `ChamadaActivity.kt:372-402`: o `onBackPressed` depreciado **não é chamado** no API 33+ com `enableOnBackInvokedCallback="true"` (`AndroidManifest.xml:50`), então o bloqueio do "voltar" durante o toque não funciona.
- **Correção:** `LaunchedEffect(estado)`, estado de mute/alto-falante em `StateFlow` e `BackHandler`/`OnBackPressedCallback`.

**#41 [MÉDIO] Canais e notificações.**
- **Local:**
  - `SocketService.kt:409-416`: **apaga e recria os canais** em todo `onCreate`, o que descarta as preferências do usuário (som, importância).
  - `ChamadaService.kt:436`: `setBypassDnd(true)` é ignorado sem `ACCESS_NOTIFICATION_POLICY`.
  - `:390-397`: `notify()` da notificação de chamada a cada 1 s, em vez de `setUsesChronometer(true)`.
  - `MainActivity.kt:141-148,159-175`: abre as configurações de full-screen intent em **todo** `onCreate` sem permissão, e o Play exige uma declaração de uso de FSI.
  - `ChamadaRingtoneManager.kt` não tem timeout, então o toque continua até alguém chamar `parar()`.
- **Correção:** criar os canais uma vez (no `Application`) com ids versionados, cronômetro nativo, pedir FSI uma vez com explicação e timeout de toque igual ao do servidor.

**#42 [MÉDIO] Receivers fazem trabalho assíncrono sem `goAsync()`.**
- **Local:** `ChamadaActionReceiver.kt:94-117` e `MensagemActionReceiver.kt:37,73-131` disparam rede numa `CoroutineScope` solta depois que `onReceive` retorna, e o processo pode ser morto.
- **Correção:** `goAsync()` + `finish()`, ou WorkManager expedited.

**#43 [MÉDIO] Permissões em excesso.**
- **Local:** `AndroidManifest.xml:19-21` (`READ_MEDIA_IMAGES/VIDEO/AUDIO`, desnecessárias com `GetContent`/Photo Picker; a política do Play de 2025 exige justificar fotos/vídeos); `:36-37` (`SYSTEM_ALERT_WINDOW`, `DISABLE_KEYGUARD`, não usadas); `:10` (`FOREGROUND_SERVICE_CAMERA`, mas o tipo `camera` nunca é passado ao `startForeground`, `ChamadaService.kt:566-571`, então a câmera é cortada em segundo plano no Android 14); `:22/:29` (`POST_NOTIFICATIONS` duplicada).
- **Correção:** Photo Picker, remover as não usadas e incluir `CAMERA` no `startForeground` quando o vídeo estiver ativo.

**#44 [MÉDIO] Sessão e autenticação.**
- **Local:**
  - `SplashActivity.kt:105-126`: ignora o token salvo e sempre manda para o Login.
  - `LoginActivity.kt:36-62`: refaz o login com a senha a cada abertura.
  - Não há interceptor de 401. Só a `MainActivity.kt:326-330` trata 401; nas demais telas, aparece um erro genérico.
  - `UserPreferences.kt:84-90`: `getToken()` está **quebrado** (usa `map` sem coletar e sempre retorna `null`). Felizmente não é usado.
- **Correção:** `AuthRepository` com `Authenticator`/interceptor, sessão persistida e logout centralizado.

**#45 [MÉDIO] Logout e troca de servidor incompletos.**
- **Local:** `MainActivity.kt:442-453`. Não para o `ChamadaService`, não desvincula o dispositivo no backend, não limpa notificações (`MensagemNotificationManager`), o cache de áudio nem o do Glide. Trocar a URL em Configurações (`ConfigApiActivity`) não reinicia o `SocketService`, que continua no host antigo.

**#46 [MÉDIO] Criação de conversa e grupo não atômica.**
- **Local:** `ChatActivity.kt:607-663` e `detalhes_grupo_activity.kt:98-158`. Três ou mais chamadas sem checar o resultado das adições deixam conversas órfãs ou sem participante. A descrição do grupo é descartada (`:64`).
- **Correção:** um endpoint transacional no backend (`PUT conversa` com a lista de usuários).

**#47 [MÉDIO] Lista de conversas recarregada inteira.**
- **Local:** `MainActivity.kt:491-507,532-538`. Cada mensagem recebida e cada `onResume` refazem `GET conversas` com spinner que esconde a lista (`:347-351`). O badge de não lidas e a presença não são usados.
- **Correção:** atualização incremental via evento, com Room como fonte.

**#48 [MÉDIO] `AudioRecorderHelper` só trata `IOException`.**
- **Local:** `AudioRecorderHelper.kt:23-61`. `MediaRecorder.start()` lança `IllegalStateException`/`RuntimeException` (por exemplo, microfone ocupado por uma chamada), o que derruba o app.
- **Correção:** `catch (e: Exception)`, liberar os recursos e informar o usuário.

### BAIXO

**#49 [BAIXO] Código morto (≈1,5 mil linhas, ~11%).**
- **Templates:** `FirstFragment.kt`, `SecondFragment.kt`, `res/navigation/nav_graph.xml`, `fragment_first/second.xml`, `strings.xml` (lorem ipsum, "First Fragment", "Next/Previous", "Settings" em inglês), dependências do Navigation.
- **Repositórios nunca instanciados:** `MensagemRepository` (216), `ContatosRepository` (66), `SipRepository` (42), `UsuarioRepository` (39).
- **Classes sem uso:** `SocketServiceHelper.kt`; `ChamadaNavigator.abrirChamadaRecebida/iniciarChamada/iniciarChamadaGrupo`; `MainActivity.abrirChamada` (`:558-566`); `ChatActivity.chamadaRepository` (sempre `null`, `:70,704,1041`); `ChamadaService.getParticipantes` (`:320-336`, sem uso depois do staged); `ParticipantsList.kt`; `WebRTCVideoRendererSafe`; `SwipeButton.kt` (298 linhas, usado só pelo layout morto `fragment_incoming_call.xml`).
- **Métodos sem uso:** `ConversasAdapter.formatarHora` (`:72-94`); `UploadHelper.formatarTamanhoArquivo`; perdidas e "missed" em `NotificationConstants`; `eventosChamadaFlow`, `videoRemotoAtivadoFlow` e `ChamadaService.eventosFlow` (emitidos e nunca coletados).
- **Arquivos soltos:** `.kt.OLD`/`.REMOVIDO` em `src`; cerca de 10 layouts mortos (seção 3.3); 28 endpoints sem uso.

**#50 [BAIXO] Duplicações.**
- `adapter/ParticipantesAdapter.kt` e `ui/chamada/ParticipantesAdapter.kt` (ambos mortos), `data/model/ParticipanteItem.kt` e `ui/chamada/ParticipanteItem.kt` (ambos mortos), `data/model/EventoChamada.kt` (sealed) e `data/chamada/model/EventoChamada.kt` (data class morta).
- `SocketService.registrarListenersSocket()` (`:264-383`) e `inicializarSocket()` (`:471-586`) têm o mesmo código, cerca de 110 linhas duplicadas.
- `ChamadaServiceActions` (`SocketService.kt:32-38`) repete as constantes de `ChamadaService.kt:61-65`.
- Os extras de Intent estão definidos em `ChamadaActivity`, `ChamadaNavigator`, `ChamadaActionReceiver` e `ChamadaService`.
- Dois `chamadaServiceAtivo` (#1).
- Três implementações de trust-all.

**#51 [BAIXO] Nomenclatura e organização.**
- `ui/grupo/criar_grupo_activity.kt` e `detalhes_grupo_activity.kt` estão em snake_case e declaram `package com.conversa.conversa` (`:1`), divergindo do diretório (o manifest os referencia como `.CriarGrupoActivity`).
- `UtcToLocalDateTimeDeserializer.kt` não tem package.
- `Conversa` usa propriedades em snake_case.
- `Mensagem.usuarioId` é mapeado de `remetente_id`.
- `MensagemRepository.encaminhar` passa `origemMensagemId` como `destinoMensagemId` (`:117-126`).
- 258 nomes totalmente qualificados inline (`com.conversa.conversa.data.model.X(...)`), 32 `!!`, 256 chamadas de `Log`, 30 `printStackTrace` e emojis nos logs.

**#52 [BAIXO] i18n e textos.**
- 109 textos hardcoded em layouts e menus, cerca de 65 `Toast` com string literal e todo o Compose com strings literais.
- Acentuação inconsistente ("Sessao", "historico", "Nao atendida").
- Mensagens técnicas expostas ao usuário ("Erro de conexão: ${e.message}").

**#53 [BAIXO] Pequenas falhas de UI.**
- O histórico mostra `criadoPor` no diálogo (`HistoricoChamadasActivity.kt:128`).
- `ConversasAdapter.kt:47` mostra só `HH:mm`.
- `CriarGrupoActivity`/`DetalhesGrupoActivity` usam um tema com ActionBar e uma Toolbar própria (`AndroidManifest.xml:140-149`).
- "Testar Vibração/Toque" fica exposto no menu (`nav_drawer_menu.xml`).
- Os ícones do drawer são todos `ic_person`.

**#54 [BAIXO] R8 e regras de ofuscação.**
- `isMinifyEnabled=false` e `proguard-rules.pro` vazio. Quando o R8 for ligado, serão necessárias regras para Gson, Retrofit e WebRTC.

**#55 [BAIXO] Testes.**
- Só os templates `ExampleUnitTest` (2+2) e `ExampleInstrumentedTest`. A cobertura real é zero.

**#56 [BAIXO] Documentação divergente.**
- **README:** a Splash "verifica token"; fases 2–6 aparecem como "[ ] pendentes" (a maioria existe); a porta indicada é 9000; "MVVM parcial" (não há MVVM).
- **`documentacao-oficial/*`:** descreve o áudio por TCP 9090/PCM e `ws://`.
- **`legado/CORRECOES-PENDENTES.md`:** lista como "não commitadas" correções que já estão em commits.
- **`legado/erros.md`:** vazio.
- **`legado/pendencias-voip.md`:** o checklist de testes continua todo desmarcado. O item "Atender pela notificação → abre ChamadaActivity" segue de fato **quebrado** (#3). Binder e RingtoneManager foram implementados; a "camada de pré-notificação" foi parcialmente.
- Vários `legado/IMPLEMENTACAO_*`/`RESUMO_AUDIO.md` declaram "COMPLETO E FUNCIONAL" funcionalidades que têm defeitos (#29, #31, #11).

**#57 [BAIXO] Higiene do repositório.**
- Seção 1.3 (rar, tmpclaude, `temp_function.kt`, `local.properties`, `build/reports`, `.claude`), 11 stashes e 3 commits não publicados.
- `.bat` com path absoluto e um `build_completo.bat` que anuncia sucesso sem checar erros.

---

## 7. Qualidade geral e dívida técnica

### 7.1 Métricas

| Métrica | Valor |
|---|---|
| Arquivos `.kt` em `main` / testes | 83 / 2 (templates) |
| Linhas Kotlin (main + test) | 13.984 |
| Layouts XML | 31 (3.437 linhas), ~10 mortos |
| Drawables | 40 |
| Endpoints declarados / usados | 50 / 22 |
| Repositórios / vivos | 6 / 2 (`Chamada`; `Dispositivo` só pelo FCM inerte) |
| ViewModels / DI / Room / testes reais | 0 / 0 / 0 / 0 |
| Maiores classes | `ChatActivity` **1.237** · `SocketService` 687 · `MensagensAdapter` 635 · `ChamadaService` 604 · `MainActivity` 573 · `SocketManager` 500 · `WebRTCManager` 466 · `IncomingCallScreen` 464 · `ConversaApi` 421 · `AudioPlayerHelper` 414 · `ChamadaActivity` 413 |
| Código morto estimado | ≈1,5 mil linhas (~11%) |
| Logs / `printStackTrace` / `!!` | 256 / 30 / 32 |
| Escopos de coroutine criados à mão | 18 ocorrências de `CoroutineScope(`/`runBlocking` |

### 7.2 Avaliação franca

- **Arquitetura:** não existe uma camada de apresentação nem de domínio. As Activities fazem I/O, parsing, regras de negócio e navegação ao mesmo tempo. A `ChatActivity` (1.237 linhas) concentra lista, envio, upload, gravação, chamada, digitando e socket. O estado de chamada está espalhado por 2 serviços, 1 repositório, 2 flags estáticas, callbacks sobrescrevíveis e 1 observer. O desenho de "callback `var` único" no `SocketManager` é a raiz dos bugs mais graves (#1, #2).
- **Evolução por camadas:** é visível que o código cresceu em "sessões" sucessivas (muitas com assistentes de IA, segundo os commits e os `.md`). Cada sessão adicionou uma camada sem remover a anterior: repositórios novos não foram conectados, há classes duplicadas, documentos de "implementação concluída" e um commit que cria repositórios "alinhados ao contrato" sem UI. O resultado é um **contrato rico declarado (50 endpoints) e uma UI que usa menos da metade**.
- **Testabilidade:** praticamente nula. Não há interfaces nem injeção, e todas as dependências são globais (`RetrofitClient.api`, `SocketService.socketManagerGlobal`, `object`s). A lógica fica presa a `Context`/`Activity`, e não há nenhum teste.
- **UI:** há dois toolkits sem design system comum. As telas Compose de chamada são visualmente boas, mas inacessíveis. As telas XML são funcionais e simples, e não estão prontas para edge-to-edge (necessário no `targetSdk 35+`).
- **Plataforma:** o tratamento de Android 12–15 é superficial (trampolins, tipos de FGS, FSI, dataSync, back preditivo, edge-to-edge), há riscos de política do Play (permissões, FGS, FSI, `targetSdk`) e não há `ConnectionService`.
- **Pontos positivos reais:**
  - Compila limpo no AGP 9/Gradle 9.
  - `PendingIntent`s usam `FLAG_IMMUTABLE` corretamente (exceto o de resposta, que precisa ser `MUTABLE` e tem intent explícita).
  - Nenhum componente próprio exportado além do launcher.
  - `.so` do WebRTC alinhadas a 16 KB.
  - Cliente WHIP/WHEP enxuto e correto no essencial.
  - Notificações com MessagingStyle, RemoteInput e CallStyle.
  - Recuperação de chamadas pendentes ao reconectar.
  - Deduplicação de anexos por SHA-256.
  - DiffUtil (`ListAdapter`) em todas as listas.

---

## 8. Avaliação: evoluir ou recomeçar?

### 8.1 Argumentos para evoluir o código atual

1. **Ele funciona no caminho principal:** login, conversas, chat de texto, imagem e áudio, notificações e chamada 1:1 de áudio em primeiro plano. Há valor de produto já entregue.
2. **O conhecimento do contrato está codificado:** 50 endpoints, os modelos, os tipos de evento WS (incluindo as variações de payload do Delphi) e os paths WHIP/WHEP. Isso tem valor como referência.
3. **A parte WebRTC (WHIP/WHEP) é recente** (abr/2026), segue o cliente web e tem um núcleo razoável.
4. **O build está saudável** em toolchain moderna (AGP 9 / Gradle 9 / Kotlin 2.2).

### 8.2 Argumentos para recomeçar (a estrutura)

1. **Os defeitos críticos estão no núcleo**, não nas bordas: o roteamento de eventos do socket, o ciclo de vida dos serviços de chamada, o FCM e o modelo de sessão. Corrigir #1, #2, #3, #5, #8, #15 e #20 significa reescrever `SocketService`, `ChamadaService`, `ChamadaRepository`, `ChamadaActionReceiver`, `ChamadaActivity` e o login. Isso é praticamente tudo que tem estado.
2. **A integração correta com o Android moderno muda o desenho:** Core-Telecom/`ConnectionService` para chamadas, FCM high-priority como gatilho, WS apenas em primeiro plano e um gerenciador de rota de áudio. Não é um patch incremental.
3. **A migração para `targetSdk 35/36` força refazer os layouts XML** (edge-to-edge) e a estratégia de FGS. Fazer isso em XML para depois migrar para Compose é trabalho dobrado.
4. **Não há base para refatoração segura:** zero testes, zero DI e estado global. Cada mudança no núcleo exigiria teste manual em vários cenários de chamada sem rede de proteção.
5. **A Chat (1.237 linhas) e a Main** precisam de ViewModel, Paging, Room e envio otimista. Isso é reescrita de qualquer forma.
6. **O código morto e as duplicações (~11%)** e a documentação divergente aumentam o custo cognitivo de manter.

### 8.3 Recomendação

**Recomeçar a estrutura do app ("novo esqueleto") e transplantar componentes selecionados.** Não descartar tudo, nem continuar remendando.

Esqueleto sugerido:
- Single-Activity + Jetpack Compose + Navigation Compose.
- ViewModel + `StateFlow`/UDF.
- Hilt.
- Camadas `data` (Retrofit/OkHttp único + Room + DataStore cifrado) / `domain` (casos de uso) / `ui`.
- `SocketClient` com `SharedFlow<Evento>` multi-assinante e ping.
- `CallManager` de escopo de aplicação + Core-Telecom.
- FCM high-priority.
- WorkManager para envio e upload.
- `targetSdk 36`, R8 ligado e testes (unitários dos parsers e da máquina de estados de chamada; instrumentados das telas críticas).

**Aproveitáveis (com os ajustes indicados):**

| Peça | Arquivo | Ajustes |
|---|---|---|
| Cliente WHIP/WHEP | `P/data/webrtc/WhipWhepClient.kt` | Usar o OkHttp compartilhado, header de auth, `Location` + `DELETE`, trickle/PATCH |
| Núcleo WebRTC | `P/data/webrtc/WebRTCManager.kt` | Corrigir #10, #18, #36, #37, #38; factory/EGL por processo; separar o roteamento de áudio |
| Gravação de áudio | `P/ui/chat/AudioRecorderHelper.kt` | Quase como está (#48) |
| Toque de chamada | `P/service/ChamadaRingtoneManager.kt` | Timeout, `AudioAttributes` de ringtone, reduzir os logs. Com o Core-Telecom, avaliar se continua necessário. |
| Notificação de mensagem | `P/notification/MensagemNotificationManager.kt` + `MensagemActionReceiver.kt` | Padrão MessagingStyle + RemoteInput; acrescentar conversation shortcuts, ids reais (#14) e `goAsync` |
| Notificação de chamada | trechos CallStyle de `ChamadaService.kt:451-579` | Como referência de notificação incoming/ongoing |
| Telas Compose de chamada | `IncomingCallScreen`, `OutgoingCallScreen`, `ActiveCallScreen`, `CallBanner`, `ParticipantAvatar`, `CallControls` | Visual aproveitável; corrigir a11y (#25), ícones, efeitos (#40) e renderer (#39) |
| Contrato | `P/data/api/ConversaApi.kt` + `data/model/*` | Ponto de partida do cliente gerado/tipado; revisar nulidade (#34), datas (#30) e campos `@Query` x body |
| Parser de eventos WS | `SocketManager.processarMensagem` (`:243-402`) | Lógica de tolerância a formatos legados; mover para um parser testável que emite eventos tipados |
| Upload com dedupe | `UploadHelper.kt` (fluxo `existe` → `PUT`) | Reescrever em streaming (#27) |
| Formatação do histórico | `HistoricoChamadasAdapter.kt` / `HistoricoChamadasActivity.kt` (ícone por status, "Hoje/Ontem") | Lógica pura, fácil de portar e testar |

**Descartar:**
- `SocketService` (desenho de callbacks, polling, wakelock), a orquestração de `ChamadaService` e `ChamadaRepository`, `ChamadaActionReceiver`, `ChamadaServiceObserver`, `AppLifecycleManager`, `NotificationConstants` (alocação de ids superdimensionada; usar ids derivados do `chamadaId`).
- `ChatActivity`, `MainActivity` e as demais Activities XML, `MensagensAdapter` (reimplementar em Compose), `AudioPlayerHelper` (trocar por Media3), `DownloadHelper` (trocar por MediaStore).
- Todos os itens de código morto do #49, `temp_function.kt`, `legado/chamada-tcp`, os `.OLD`/`.REMOVIDO`, os templates e os `.bat`.
- `legado/*.md`, que deve ir para um arquivo histórico fora do repo do app ou ser apagado. A documentação viva deve ser gerada a partir do contrato real.

**Ações imediatas, se o app atual precisar continuar em uso enquanto a nova base nasce:**
1. Corrigir #4: logging só em debug, com redação do header de autorização.
2. Corrigir #7: não parar o toque no `onCreate`.
3. Corrigir #3: `PendingIntent.getActivity` para atender.
4. Mitigar #1 e #2: deixar só o `ChamadaRepository` tratar 51–56 e o `SocketService` só tratar mensagens, sem re-registro.
5. Corrigir #23: versionar o wrapper jar.
6. Corrigir #6: parar de salvar a senha e excluir o DataStore do backup.
7. Commitar ou descartar o trabalho staged/unstaged e publicar os 3 commits pendentes.

Mesmo com essas correções, o app atual **não deve ser publicado no Play** (#24, #15, #43, FSI).

---

*Fim do relatório.*
