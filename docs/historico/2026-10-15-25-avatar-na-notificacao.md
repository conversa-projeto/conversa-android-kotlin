# 2026-10-15 · 25 · Avatar na notificação de mensagem

- **Fluxo:** Notificações e push (etapa 5)
- **Tipo:** código
- **Itens:** `TODO.md` 5.4 (linha do título, avatar e corpo); NOT-02
- **Branch:** `reescrita`
- **Commits:** `ef281e4`

## Contexto
- **A notificação de mensagem** já tinha título e corpo resumido, mas o avatar grande era o ícone do app. No Android 11+, o avatar de uma notificação de conversa vem do atalho da conversa, e o de cada mensagem vem do `Person`.
- **No app,** o atalho usava o ícone do app e o `Person` ia sem ícone (o sistema desenhava a inicial).

## O que foi feito
- **`IconesDeAvatar`** (`app/notificacoes`, novo):
  - **A foto:** vem da conversa (direta, ou grupo com imagem) ou de quem mandou, pelo Coil, recortada em círculo (256 px, espera de até 5 s);
  - **Sem foto:** o emoji do grupo ou a inicial, desenhados nas cores do `Avatar` do app (`avatarFundo`, `avatarLetra`, claro ou escuro como o sistema);
  - **A última foto de cada um fica guardada,** porque as URLs vencem em 10 min e o atalho é republicado sem rede. Ao sair da conta, as fotos são apagadas.
- **`NotificadorMensagens`:**
  - **O atalho da conversa** vai com o avatar dela; os atalhos das conversas recentes usam o guardado ou o desenho;
  - **O `Person` de cada linha** vai com a foto de quem mandou: na direta, a da conversa; no grupo, a dos membros (`GET /conversa/usuarios`, relidos a cada 5 min, espera de até 3 s);
  - **No Android 9 e 10,** a foto também vai como ícone grande.
- **`coresDoTema(escuro)`** (`core:ui`): as cores do tema fora do Compose.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (519 testes), `:app:lintDebug` (só os 3 avisos de sempre) e `ktlintCheck`. Passou.
- **No emulador (A),** com o app em segundo plano e o B pela API:
  - **"Aniversario" (grupo com emoji):** a notificação mostrou o 🎂 como avatar; antes era o ícone do app.
  - **"Grupo de teste 2" com imagem:** pela tela do grupo, pus a imagem de teste do emulador. A notificação mostrou a foto do grupo como avatar. Depois a imagem foi removida (o grupo voltou como estava).
- **Sem foto de pessoa:** nenhuma conta de teste tem foto. O `Person` com foto não foi visto; quem mandou aparece com a inicial desenhada pelo sistema, como antes.

## Decisões
- **Sem foto, o `Person` fica sem ícone.** O sistema já desenha a inicial de quem mandou. O desenho próprio (inicial ou emoji) fica só para a conversa, porque o atalho sem ícone mostraria o do app.
