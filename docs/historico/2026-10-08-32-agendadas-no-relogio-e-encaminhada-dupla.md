# 2026-10-08 · 32 · Agendadas fora do chat (relógio) e encaminhada de encaminhada

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade (acompanha o web)
- **Itens:** `TODO.md` 7.10 e 7.5 (linhas 🆕 `7322e83`); FC-514, FC-520; MSG-17, ENV-14, MSG-19
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O web `7322e83` (ver a sincronização em [31](2026-10-08-31-sincronizacao-agendadas-rascunho.md)) mudou dois comportamentos que o Android já tinha:
- **Agendadas:** saem do chat e vão para um relógio ao lado do microfone. O Android fazia isso com o selo na bolha (`f3cab38`).
- **Citação:** a encaminhada de uma encaminhada repetia o conteúdo dentro da citação.

## O que foi feito
- **`core:model`:**
  - **`Agendamento.kt`:**
    - `separarAgendadas` (chat sem as agendadas; agendadas por horário);
    - `quandoPrazo`/`DiaPrazo`, no lugar de `quandoAgendada`: o `formatarPrazo` do web, agora com o ano quando é outro. Serve também ao "encerra …" da votação.
  - **`Chat.kt`:** `semCopiasDaReferencia`. O `separarConteudosDaCitacao` passa a usá-la.
- **`feature:chat`:**
  - **`ChatViewModel`:**
    - a lista vem de um fluxo que separa as agendadas e se refaz na hora da próxima, sem esperar o Room;
    - `ChatUiState.agendadas`;
    - `cancelarAgendada` (`DELETE /mensagem`).
  - **`StatusEAgendamento.kt`:**
    - saem o selo e o esmaecimento;
    - entram o `RelogioAgendadas` (ícone com o número; "1 mensagem agendada" / "N mensagens agendadas" para o TalkBack) e a folha `MensagensAgendadas`. A folha tem horário, resumo em até 3 linhas e "Cancelar" ("Cancelando…"), com a confirmação de sempre e o erro "Não foi possível cancelar"; fecha sozinha quando esvazia.
  - **`CampoMensagem.kt`:** o relógio fica entre o campo e o microfone, só com o campo vazio.
  - **`Bolhas.kt`:** na citação aninhada, os conteúdos copiados da citação de baixo não se repetem.
- **Testes:**
  - `AgendamentoTest`: prazo com o ano, e agendadas separadas e voltando na hora;
  - `ChatTest`: encaminhada de encaminhada;
  - `ChatViewModelTest`: "agendada fica fora do chat, no relógio, e entra na hora exata".

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A no "Grupo criado pelo B"):**
  - **Encaminhada de encaminhada:** pela API, o B mandou uma original, uma encaminhada dela com um comentário e uma encaminhada da encaminhada. A terceira mostra a citação aninhada ("Original…") e, no nível do meio, só "Comentário acrescentado".
  - **Agendada:** ao agendar para amanhã 08:00, a mensagem não entrou no chat; o relógio com "1" apareceu ao lado do microfone ("1 mensagem agendada").
  - **Folha:** mostrou "amanhã 08:00" e o texto. "Cancelar" abriu "Cancelar mensagem agendada" / "Ela não será enviada."; "Cancelar envio" fechou a folha, o relógio sumiu e a mensagem saiu do servidor.

## Decisões
- **A prévia da lista de conversas mostra a agendada:** o servidor devolve a agendada como `ultima_mensagem_texto` para o autor. Isso fica como está; não é mudança deste commit.

## Pendências
- Rascunho por conversa (FC-519), no próximo bloco.
