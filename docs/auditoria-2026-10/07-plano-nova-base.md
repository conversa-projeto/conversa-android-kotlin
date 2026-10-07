# 07 — Plano da nova base do app Android

**Data:** 2026-10-06 · **Base:** docs 01–06.
**Objetivo:** um app Android moderno, testável e publicável no Play, em paridade com o `conversa-web`. Ele deve tolerar o servidor Bun/Elysia atual e reagir bem a mudanças futuras do contrato.

---

## 1. Decisões estruturais

| # | Decisão | Escolha | Motivo |
|---|---|---|---|
| D1 | Recomeçar ou evoluir | **Recomeçar** (nova base) e transplantar peças | Doc 00 §4 e doc 04 §8 |
| D2 | Onde | **Mesmo repositório**, em branch `reescrita` → `main`. O app antigo fica na tag `legado-v1-final` e na branch `legado/abril-2026` | Histórico preservado; um só lugar para issues e docs |
| D3 | `applicationId` | **Manter `com.conversa.conversa`** para atualizar por cima do app instalado. Pacote de código novo: `com.conversa.app` | Testadores não precisam desinstalar; o namespace do código pode diferir do applicationId |
| D4 | UI | 100% Jetpack Compose, Material 3, Single-Activity, Navigation Compose (rotas tipadas) | Fim da divisão XML/Compose; edge-to-edge nativo |
| D5 | Arquitetura | Camadas `data` / `domain` (opcional, usar só onde houver regra) / `ui`; UDF com `ViewModel` + `StateFlow` | Testabilidade |
| D6 | DI | Hilt | Padrão de mercado; integra com WorkManager e ViewModel |
| D7 | Serialização | kotlinx.serialization (não Gson) | Respeita a nulidade do Kotlin (#34) e é amigável ao R8 |
| D8 | Rede | Retrofit + OkHttp único. Modelos escritos à mão, conferidos contra o OpenAPI do servidor (`/api/docs/json`) e testados com os JSON reais dos testes do servidor | O OpenAPI já existe, mas descreve só as entradas, não as respostas (ver S12) |
| D9 | Persistência | Room (cache e offline) + DataStore (preferências) + Keystore/Tink (token) | AND-11, #6 |
| D10 | Tempo real | `RealtimeClient` (OkHttp WebSocket) só em primeiro plano + FCM em segundo plano | AND-07, política do Play |
| D11 | Chamadas | Core-Telecom + WebRTC (`io.github.webrtc-sdk:android`, versão mais nova) + WHIP/WHEP | AND-03 |
| D12 | Mídia | Media3 (ExoPlayer) para áudio e vídeo; Coil 3 para imagens (com o OkHttp compartilhado); Lottie-compose para figurinhas | — |
| D13 | Trabalho em segundo plano | WorkManager (envio de mensagens, uploads, downloads) | Sobrevive a matar o app |
| D14 | SDK | `compileSdk/targetSdk 36`, `minSdk 28` (Android 9) | O app atual já usa 28; Core-Telecom exige API 26+ |
| D15 | Qualidade | ktlint + detekt + Android Lint no CI; testes unitários obrigatórios no `:core` e no `CallManager` | #55 |

---

## 2. Estrutura de módulos

```
conversa-android-kotlin/
├── app/                          # Application, MainActivity, NavHost, DI raiz, FCM service, Telecom glue
├── core/
│   ├── model/                    # Tipos de domínio puros (Conversa, Mensagem, Conteudo, Chamada, Atividade…)
│   ├── network/                  # Retrofit API, DTOs (@Serializable), mapeadores DTO→modelo, OkHttp, auth interceptor,
│   │                             # RealtimeClient (WS) + parser de eventos, ServerConfig (base → api/ws/webrtc)
│   ├── database/                 # Room (entidades, DAOs, migrações)
│   ├── datastore/                # Preferências + SessaoStore (Keystore)
│   ├── data/                     # Repositórios (SessaoRepo, ConversaRepo, MensagemRepo, AnexoRepo, ChamadaRepo,
│   │                             # AtividadeRepo, PresencaRepo, ConfigRepo) + SyncManager
│   ├── ui/                       # Design system: tema, Avatar, diálogos, estados, ícones, strings comuns
│   ├── media/                    # Player (Media3) único, gravador, cache de anexos, URLs assinadas
│   ├── webrtc/                   # PeerConnectionFactory/EGL de processo, WhipWhepClient, PublicadorWhip, AssinanteWhep
│   └── testing/                  # Fakes, JSONs reais de fixture, regras de coroutine
└── feature/
    ├── auth/                     # Servidor, Login, Cadastro
    ├── conversas/                # Lista, filtro, fixar/arquivar, contatos, novo grupo, membros
    ├── chat/                     # Chat, bolhas, composição, anexos, ações, pesquisa na conversa
    ├── chamada/                  # CallManager, UI (recebida, efetuada, ativa, PiP), histórico
    ├── atividades/
    ├── pesquisa/
    └── config/                   # Perfil, aparência, notificações, chamadas, permissões, sistema, acessos, SIP
```

> **Dica:** dá para começar com `:app`, `:core` (um módulo só) e `:feature:*` mínimos, e só dividir o `:core` quando o build ficar lento. O que importa é a **separação de pacotes**, que torna a divisão futura trivial.

---

## 3. Componentes centrais (desenho)

### 3.1 `ServerConfig`

```kotlin
data class ServerConfig(val base: HttpUrl) {           // ex.: https://conversa.empresa.com  (sem /api)
    val api: HttpUrl  = base.newBuilder().addPathSegment("api").addPathSegment("").build()
    val ws: String    = base.toString().replaceFirst("http", "ws").trimEnd('/') + "/ws/"   // barra final obrigatória
    val webrtc: HttpUrl = base.newBuilder().addPathSegment("webrtc").build()
}
```

- Vem de um `StateFlow<ServerConfig>` (DataStore). O Retrofit usa um interceptor que reescreve o host, para não precisar recriar o cliente quando a configuração muda (#13).

### 3.2 `RealtimeClient`

```
estado: Desconectado → Conectando → Autenticando → Conectado → (falha) → Aguardando(backoff) → Conectando …
- conecta só quando: sessão válida && app em foreground (ProcessLifecycleOwner) [ou chamada ativa]
- onOpen → send {"tipo":1,"token":…}
- onMessage → parser → SharedFlow<EventoSocket>(extraBufferCapacity = 64)
- cada conexão tem um id de geração; callbacks de gerações antigas são ignorados
- pingInterval 20 s (OkHttp); backoff 1 s → 30 s com jitter
- ao entrar em Conectado: SyncManager.ressincronizar()
```

`EventoSocket` é uma sealed class:
- `NovaMensagem(titulo, texto)`, que **não tem `conversa_id`** (S3);
- `StatusMensagem(conversaId, ids)`;
- `Digitando(conversaId, usuarioId)` e `Gravando(…)`;
- `Reacao(…)`;
- `ConversaAtualizada(conversaId)`;
- `ChamadaRecebida(chamadaId, usuarioId)`, `ChamadaFinalizada`, `UsuarioRecusou`, `UsuarioEntrou`, `UsuarioSaiu`, `VideoAtivado`;
- `SinalChamada(chamadaId, usuarioId, dados: JsonObject)`;
- `StatusUsuario(usuarioId, online)`;
- `NovaAtividade`;
- 🆕 `EnqueteAtualizada(enqueteId, conversaId)` (tipo 62);
- `Erro(msg)`;
- `Desconhecido(tipo, json)`.

### 3.3 `SyncManager`

Na (re)conexão, no retorno ao foreground e ao receber push, executa **em paralelo e de forma idempotente**:
1. `GET /mensagens/novas?desde=<cursor>` → para cada `{conversa_id, mensagem_id}`, buscar as mensagens que faltam (`/mensagens` com referência) → Room → salvar `ate` como o novo cursor.
2. `GET /conversas` → Room.
3. `GET /contatos/online` → `PresencaRepo`.
4. `GET /atividades/novas` → badge.
5. `GET /chamadas/pendentes` → `CallManager.avaliarPendentes()`.

### 3.4 `CallManager` (escopo de aplicação, singleton Hilt)

```
            ┌────────── iniciar() ──────────┐
 Inativo ───┤                               ▼
            │  WS51/push ──► Recebendo ──atender()──► Conectando ──WHIP ok──► Ativa
            │                  │  │ 30s/ocupado → recusar(nao_atendeu)   │        │
            │                  │  └ 53/54 com meu id (outro aparelho) → Inativo  │
            │                  └ recusar() ─────────────────────────────► Encerrando ─► Inativo
            └─► Chamando ──54 de outro──► Ativa ── 52 | sair() | ninguém "Entrou" após 55 | 53 em 1:1 ──► Encerrando
                  └ cancelar() ──────────────────────────────────────────► Encerrando
```

- **Única** fonte de verdade: `StateFlow<EstadoChamada>` (participantes, tracks, mute, rota de áudio, duração, modo de exibição).
- Faz a ponte com Core-Telecom: `CallsManager.addCall(...)` com callbacks de answer, disconnect, setActive e setInactive, e o endpoint de áudio.
- O FGS da chamada é o próprio `CallsManager`/serviço de chamada, com tipos `phoneCall|microphone` (+ `camera` quando houver vídeo).
- `encerrar()` é idempotente e faz:
  1. `DELETE` dos recursos WHIP/WHEP;
  2. `dispose()` de tracks e PCs;
  3. liberação do áudio;
  4. cancelamento das notificações;
  5. `Telecom disconnect`;
  6. fim do serviço.

### 3.5 Mídia de chamada (`:core:webrtc`)

- Uma `PeerConnectionFactory` e um `EglBase` **por processo**, criados sob demanda.
- `PublicadorWhip`: um PC `SEND_ONLY` (áudio + vídeo opcional) com `setCodecPreferences(H264, VP9, VP8)`.
  - Opus em 32/64/128 kbps, conforme a configuração.
  - Câmera 360p/720p a 15/24 fps (padrão do celular: 360p a 15 fps, como no web).
- `AssinanteWhep` por participante: PC `RECV_ONLY`; o peer é registrado **antes** do `setRemoteDescription`.
- ICE: `GET /api/ice` a cada PC; `GATHER_ONCE`; espera até 5 s; sem trickle (o MediaMTX aceita PATCH, mas o web não usa; manter igual).
- Retentativas: WHEP 404 → vídeo 40×1 s, áudio 12×0,8 s; ICE `FAILED` → refazer o PC daquele peer.

### 3.6 Anexos (`:core:media`)

- `UrlAssinadaCache`: identificador → (url, expira_em), com renovação ao falhar.
- `UploadWorker`: implementa o fluxo do FC-400, com progresso via `setProgress`. Retentativa com backoff; URL vencida (> 300 s) → pedir de novo.
- `PlayerUnico`: Media3, com estado `StateFlow<Map<mensagemId, EstadoAudio>>`.

---

## 4. Navegação (proposta)

```
Splash(decide) ─► Servidor ─► Login ─► Cadastro
                         └──────────► Principal (NavigationBar):
                                       ├── Conversas ──► Chat(conversaId, mensagemId?) ──► Membros / Perfil do outro / Anexos
                                       │                    └──► Visualizador (imagens/vídeo/PDF)
                                       ├── Chamadas (histórico) ──► Chat
                                       ├── Atividades (badge) ──► Chat(mensagem)
                                       └── Configurações ──► Perfil / Aparência / Notificações / Chamadas /
                                                             Permissões / Servidor / Sistema* / Acessos* / Ramal SIP
Chamada: Activity própria com showWhenLocked/turnScreenOn + PiP (exceção à Single-Activity, padrão para VoIP)
Deep links: conversa://chat/{id}?mensagem={id}, notificações e compartilhamento recebido
```

---

## 5. O que transplantar do app atual

| Peça (app atual) | Destino | Ajustes obrigatórios |
|---|---|---|
| `data/webrtc/WhipWhepClient.kt` | `:core:webrtc` | OkHttp compartilhado, ler o `Location` + `DELETE`, sem trust-all |
| Núcleo de `data/webrtc/WebRTCManager.kt` (criação de PC, transceivers, câmera) | `:core:webrtc` (`PublicadorWhip`/`AssinanteWhep`) | #10, #18, #36, #37, #38; codec; ICE via `/ice` |
| `ui/chat/AudioRecorderHelper.kt` | `:core:media` | #48 (`catch Exception`), saída tipo 5 |
| `service/ChamadaRingtoneManager.kt` | `:feature:chamada` | Timeout de 30 s, `AudioAttributes.USAGE_NOTIFICATION_RINGTONE`, menos logs (com Telecom, avaliar se continua necessário) |
| Padrão de `notification/MensagemNotificationManager.kt` + `MensagemActionReceiver.kt` | `app/notificacoes` | Ids estáveis, `conversa_id` real, `goAsync`, atalhos de conversa |
| Trechos CallStyle de `service/ChamadaService.kt:451-579` | `:feature:chamada` | Cronômetro nativo, `PendingIntent.getActivity` para atender |
| Visual de `ui/chamada/*` (Incoming, Outgoing, Active, CallBanner, ParticipantAvatar, CallControls) | `:feature:chamada` | Acessibilidade (#25), ícones, efeitos fora da composição (#40), renderer com `key` (#39) |
| Lógica de `HistoricoChamadasAdapter` (ícones por status, "Hoje/Ontem") | `:feature:chamada` | Novo modelo (`ChamadaHistoricoItem`) |
| `SocketManager.processarMensagem` (só como referência dos formatos legados) | — | O servidor atual tem **um** formato por evento; não carregar os formatos do Delphi |

**Não transplantar:** `SocketService`, a orquestração de `ChamadaService`, `ChamadaRepository`, `ChamadaActionReceiver`, `ChamadaServiceObserver`, `AppLifecycleManager`, `NotificationConstants`, todas as Activities/Adapters XML, `AudioPlayerHelper`, `DownloadHelper`, `UploadHelper` (fluxo antigo), `UtcToLocalDateTimeDeserializer`, os modelos Gson e o código morto (#49/#50).

---

## 6. Fases (resumo; detalhe por item no doc 06)

| Fase | Entrega demonstrável | Itens |
|---|---|---|
| **F0 Fundação** | App vazio com CI, tema, configuração de servidor, TLS correto, rede, Room e WS conectando e logando no servidor real | FC-100…113 |
| **F1 Sessão e conversas** | Login persistente; lista de conversas viva (fixar, arquivar, presença); contatos; grupos | FC-200…214 |
| **F2 Mensagens núcleo** | Chat com paginação, envio otimista, status, oculta, chamada (bolha), digitando, deep link | FC-300…315 |
| **F3 Anexos** | Enviar e receber imagens, áudios (gravação), vídeos e arquivos; transcrição; compartilhamento recebido | FC-400…416 |
| **F4 Ações** | Menu, reações, responder, encaminhar, ocultar, menções, figurinhas, código/markdown, 🆕 votação | FC-500…518 |
| **F5 Push** | Notificações confiáveis com o app fechado, resposta direta | FC-600…609 (+ S1–S3 no servidor) |
| **F6 Chamadas** | Chamadas 1:1 e em grupo, áudio e vídeo, Telecom, PiP, gravação OK, histórico | FC-700…723 |
| **F7 Restante** | Atividades, pesquisa, perfil, configurações, telas administrativas | FC-800…809 |
| **F8 Extras** | SIP, compartilhar tela, bubbles | FC-900…904 |

**Corte para trocar o app atual pelos testadores:** fim da F6 com os P0.

---

## 7. Critérios de aceite globais

1. **Contrato:** todos os DTOs têm teste de desserialização com JSON real (tirado de `conversa/tests/*.test.ts` ou capturado do servidor de dev).
2. **Sem regressão de segurança:** sem trust-all, sem senha persistida, sem token em log, R8 ligado no release.
3. **Plataforma:** `targetSdk 36`, edge-to-edge, back preditivo, FGS com o tipo correto, sem FGS permanente, Lint sem erros.
4. **Chamadas:** a matriz manual (FC-955) passa, e a gravação aparece no servidor.
5. **Acessibilidade:** fluxo de login → conversa → mensagem → atender chamada inteiramente operável pelo TalkBack.
6. **Paridade:** a matriz do doc 05 atualizada a cada fase, com evidência (print/vídeo) por item marcado ✅.

---

## 8. Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Sem push de chamada no servidor (S1) | O celular não toca com o app fechado | Priorizar S1 em paralelo à F0–F2; enquanto isso, documentar a limitação |
| O contrato do servidor muda de novo | Retrabalho | DTOs isolados em `:core:network` + testes de contrato; pedir esquemas de resposta no OpenAPI (S12) e um CHANGELOG de API no servidor |
| WebRTC Android + MediaMTX atrás de TURN TCP | Latência e qualidade | Testar cedo (spike na F0: publicar e assistir um stream com `/ice`) |
| Linphone/PJSIP (licença, tamanho) | SIP atrasado | SIP é P3; spike isolado |
| Volume (132 funcionalidades) | Prazo | Seguir P0 → P1 → P2; não perseguir P3 antes do corte |
| Certificado mkcert em aparelhos de teste | "Não conecta" | `network_security_config` com debug-overrides + instruções no README |

---

## 9. Spike recomendado (antes de tudo, 1–2 dias)

Um app descartável (ou teste instrumentado) que:
1. Faz login (`POST /api/login`).
2. Abre o WS e envia `{tipo:1, token}`.
3. Busca `GET /api/ice`.
4. Publica o microfone em `/webrtc/call-<id>-u-<eu>/whip` via TURN, numa chamada criada pelo web, e assiste o web por WHEP.
5. Confere a gravação no MediaMTX.

O spike **reduz o maior risco técnico** (mídia via TURN/MediaMTX no Android) antes do investimento grande e valida o `network_security_config` com mkcert.
