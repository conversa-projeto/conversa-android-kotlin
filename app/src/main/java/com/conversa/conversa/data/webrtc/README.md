# data/webrtc — WebRTC (Kotlin)

Substitui `data/chamada/ChamadaManager.kt` (TCP raw PCM, movido para `legado/chamada-tcp/`) por implementação WebRTC nativa.

## Arquivos

- **`WebRTCManager.kt`** — núcleo: PeerConnectionFactory, txPc (publish WHIP), rxPcs (subscribe WHEP por peer), captura áudio/vídeo, controles mute/camera/speaker
- **`WhipWhepClient.kt`** — HTTP client que lida com `Content-Type: application/sdp`, retry em 404 (stream ainda não publicado), cert self-signed em dev

## Wiring

`ChamadaRepository` (em `data/repository/`) é o orquestrador. Ele é quem:

1. Instancia `WebRTCManager` no construtor
2. Registra handlers `SocketManager` (eventos 51-56)
3. Chama `api.iniciarChamada`/`entrarChamada`/`sairChamada`
4. Delega publicação/assinatura WebRTC ao `WebRTCManager`

### Construção típica (em `ChamadaService` ou `ConversaApplication`)

```kotlin
val webRTCManager = WebRTCManager(
    context = this,
    mediaMtxBase = BuildConfig.MEDIAMTX_URL,  // "http://host:8889"
    stunUrl = BuildConfig.STUN_URL,           // "stun:stun.l.google.com:19302"
)

val repository = ChamadaRepository(
    context = this,
    api = RetrofitClient.api,
    webRTCManager = webRTCManager,
    socketManager = socketManager,
    userPreferences = UserPreferences(this),
)
```

### Ciclo de vida

- **Criação:** `WebRTCManager.inicializar()` é chamado no `init` do `ChamadaRepository`
- **Chamada iniciada:** `adquirirMidiaLocal(comVideo)` → `publicarLocalNaSala(chamadaId, meuUid)`
- **Peer entra (evento WS 54):** `assinarDePeer(chamadaId, peerId)` automaticamente
- **Peer sai (evento WS 55):** `desconectarPeer(peerId)`
- **Finalizar:** `desligar()` fecha todas PeerConnections e libera mídia
- **Destroy:** `cleanup()` encerra factory e libera EGL

## TODOs

1. **UI de vídeo remoto:** adicionar `SurfaceViewRenderer` em `ui/chamada/` para cada `peer.videoTrack` de `webRTCManager.peers`. Usar `eglBaseContext` do manager.
2. **UI de vídeo local:** `SurfaceViewRenderer` para `webRTCManager.localVideoTrackFlow`.
3. **Integrar ao ChamadaService:** hoje o `ChamadaService.kt` chama a API REST diretamente sem repository. Refatorar para usar `ChamadaRepository` + `WebRTCManager`.
4. **STUN/TURN via API:** backend poderia expor `/api/ice-servers` com TURN em produção (atualmente só STUN do Google).
5. **Reconexão por peer:** se `PeerConnection.connectionState` virar FAILED por >2s, fechar e re-assinar aquele peer (`desconectarPeer` + `assinarDePeer`).

## Referências

- [`docs/webrtc.md`](../../../../../../../../../docs/webrtc.md) — protocolo geral
- [`docs/websocket.md`](../../../../../../../../../docs/websocket.md) — eventos 51-56
- [`docs/superpowers/specs/2026-04-21-kotlin-webrtc-design.md`](../../../../../../../../../docs/superpowers/specs/2026-04-21-kotlin-webrtc-design.md) — spec completo
- [`docs/delphi-webrtc-exemplo.md`](../../../../../../../../../docs/delphi-webrtc-exemplo.md) — exemplo Delphi (base para cliente Windows FMX)
