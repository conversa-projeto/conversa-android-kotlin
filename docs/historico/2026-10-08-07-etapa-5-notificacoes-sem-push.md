# 2026-10-08 · 07 · Etapa 5 — notificação de mensagem, canais, permissão e atalhos (sem o push)

- **Fluxo:** Notificações e push (etapa 5); também Anexos (4.7, notificação de download)
- **Tipo:** código / testes / manifesto (receiver)
- **Itens:** `TODO.md` 5.3, 5.4, 5.5, 5.6, 5.7 (em parte), 5.8, e o último da 4.7; FC-602…608; NOT-01, NOT-02, NOT-03, NOT-06; problema #42 do legado
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A etapa 5 começa pelo Firebase (5.1 e 5.2). Isso depende do projeto Firebase do usuário (criar o app Android e baixar o `google-services.json`), e o servidor de dev está sem FCM. Esses itens ficaram ⛔.

Todo o resto não depende do push e foi feito agora. Quando o FCM chegar, o push só precisa sincronizar; a notificação já existe.

## O que foi feito
- **Canais (5.3):** `CanaisNotificacao` (`core/data/notificacoes`) cria `mensagens_v1`, `chamadas_recebidas_v1`, `chamada_ativa_v1` e `sistema_v1` no `Application`.
  - O canal "downloads" da 4.7 foi apagado; o aviso de download passou para o "Sistema".
- **Permissão (5.3, NOT-06):** depois do login, a tela principal explica e pede `POST_NOTIFICATIONS` (Android 13+), uma vez.
  - "Agora não" também conta como pedido, para não insistir a cada abertura. Fica guardado em `PreferenciasStore.pediuNotificacoes`.
- **Quem dispara:** `SyncManager.novasDeOutros` avisa as conversas que receberam mensagem nova de outra pessoa ainda não lida. O aviso vem depois de atualizar a lista, para o contador já estar certo.
- **`NotificadorMensagens`** (`app/notificacoes`):
  - **`decidirAviso`** (regra do web):
    - conversa arquivada → nada;
    - app na frente com a conversa na tela → nada;
    - app na frente em outra tela → só o som (no máximo um a cada 1,5 s);
    - app em segundo plano → notificação.
  - **Notificação:**
    - `MessagingStyle` por conversa (tag "conversa", id = id da conversa), montada a partir das não lidas do Room (`MensagemDao.naoLidasDeOutros`, até 7);
    - grupo com o nome no título;
    - corpo com `resumoDaMensagem`, a mesma regra da citação e do web: texto resumido ou "Imagem", "Gravação de áudio"…;
    - toque abre `conversa://chat/{id}`.
  - **Ações:** "Responder" (RemoteInput) e "Marcar como lida".
  - **Some (NOT-03)** quando o contador da conversa zera, quando a conversa é arquivada ou aberta (`ConversaEmTela`, marcada pelo `ON_RESUME`/`ON_PAUSE` do chat).
  - **Atalhos (5.8):** cada conversa notificada e as 4 recentes viram atalhos de longa duração. Isso exige que a notificação seja uma "conversa" no Android 11+.
  - **Logout:** apaga as notificações e os atalhos.
- **`AcoesNotificacaoReceiver`** usa `goAsync()` (#42):
  - **Responder:** a resposta entra na fila de envio (WorkManager) e a conversa fica lida (`MensagensRepositorio.marcarConversaLida`, que espera cada `POST`).
  - **Achado no emulador:** no Android 15+, só cancelar não basta. O sistema segura a notificação respondida (`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`) até o app publicar uma atualização.
    - Agora a notificação é atualizada com "Você: …", sem som, e sai em 2 s (`setTimeoutAfter`).
  - **Marcar como lida:** marca no servidor e cancela.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **214 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **Testes novos:**
  - `DecidirAvisoTest`;
  - `SyncManagerTest`: avisa só a conversa com mensagem nova de outra pessoa, não as minhas nem as já lidas;
  - `MensagensTest`: marcar a conversa como lida pela notificação marca todas as de outros e espera o servidor.
- **No emulador.** A permissão foi concedida pelo diálogo do próprio app, e o app foi para segundo plano; o B mandou mensagens dentro dos 10 s em que o WebSocket ainda fica ligado.
  - **Explicação e permissão:** o diálogo apareceu e a permissão ficou concedida; os 4 canais foram criados com as importâncias certas.
  - **Notificação:** apareceu como "conversa" no topo da gaveta, com título do grupo, remetente, texto, "Responder" e "Marcar como lida".
  - **Responder:** a mensagem chegou ao servidor, a do B ficou lida e a notificação saiu (depois da correção do Android 15).
  - **Marcar como lida:** a mensagem ficou lida no servidor e a notificação saiu.
  - **Toque:** abriu a conversa e a notificação saiu.
  - **App na frente (lista):** nenhuma notificação.
  - **Download:** "Baixar" com a permissão concedida mostrou a notificação no canal "Sistema".

## Pendências
- **5.1 e 5.2 (Firebase/FCM):** precisam do usuário (`google-services.json` do projeto Firebase) e do FCM configurado no servidor.
- **Avatar** como ícone da pessoa na notificação.
- **Tela cheia e bateria:** `canUseFullScreenIntent` e a isenção de bateria ficam para a etapa 6 (chamadas).
