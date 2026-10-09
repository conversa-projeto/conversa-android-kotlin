# 2026-10-09 · 05 · Etapa 8 — Configurações: notificações e permissões

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 8.5 (Notificações, Permissões); FC-806; CFG-04, NOT-06
- **Branch:** `reescrita`
- **Commits:** `ee1237c`

## Contexto
O web tem a aba "Permissões": Notificações, Microfone e Câmera, com o estado ("Concedida", "Negada"…) e o botão "Solicitar".

No Android, o TODO pede mais duas coisas:
- a tela cheia das chamadas (Android 14+) e a bateria;
- uma seção de notificações com atalhos para cada canal. Som, vibração e importância de um canal só se mudam no sistema.

## O que foi feito
- **`:feature:config` — Permissões** (`PermissoesTela`). Cada linha tem o nome, para que serve e o estado ("Concedida" em verde, "Não concedida" em vermelho):
  - **Notificações:** `areNotificationsEnabled`, que cobre o Android 12 (sem permissão de execução) e o 13+;
  - **Microfone e Câmera;**
  - **Chamadas em tela cheia:** só aparece no Android 14+ (`canUseFullScreenIntent`);
  - **Uso de bateria:** "Sem restrição" / "Com restrição" (`isIgnoringBatteryOptimizations`).
  - **O botão:**
    - com diálogo do sistema, "Permitir" pede;
    - se a resposta voltar negada sem poder perguntar de novo (`shouldShowRequestPermissionRationale` falso depois do pedido), o app abre a tela do sistema;
    - tela cheia e bateria têm "Abrir configurações": a tela de tela cheia do app e a lista de otimização de bateria. O app não pede a isenção direto (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), que a Play restringe.
  - O estado é relido a cada volta ao app (`LifecycleResumeEffect`).
- **`:feature:config` — Notificações** (`NotificacoesTela`):
  - "Notificações do Conversa", ativadas ou não, abre as notificações do app no sistema;
  - "Tipos de aviso" lista os canais (Mensagens, Chamadas recebidas, Chamada em andamento, Sistema), com o nome e a descrição que o sistema guarda. Desativado = importância `NONE`, ou as do app desligadas. Cada linha abre a tela do canal.
- **`SubTela`:** o esqueleto das seções (barra com voltar, superfície branca). O Sobre passou a usar.
- **Na lista de Configurações:** os itens "Notificações" e "Permissões". As rotas `RotaNotificacoes` e `RotaPermissoes` ficam no `app`.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (conta A).** Só abri as telas do sistema e voltei, sem mudar nada no aparelho.
  - **Permissões:** notificações, microfone, câmera e tela cheia "Concedida"; bateria "Com restrição". "Abrir configurações" abriu a lista de otimização de bateria.
  - **Notificações:** as do app e os quatro canais "Ativadas". O toque em "Mensagens" abriu a tela do canal.
- **Não testado:** o caminho "Permitir" → negar. Para ver isso, seria preciso revogar uma permissão no emulador, e as permissões do aparelho não devem ser mexidas.

## Pendências
- **O resto do 8.5:** Chamadas, Sistema e Acessos.
