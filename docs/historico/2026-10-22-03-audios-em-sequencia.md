# 2026-10-22 · 03 · Áudios em sequência

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.5 (a linha 🆕 `d244fc8`); FC-425; ANX-19
- **Branch:** `reescrita`
- **Commits:** `8935111`

## Contexto
No web (`d244fc8`), o áudio que termina toca o próximo: o seguinte na mesma mensagem ou, na mensagem logo abaixo (de quem for, inclusive as próprias), o primeiro dela. Se essa mensagem não tem áudio, a sequência para. No app, cada áudio parava no fim.

## O que foi feito
- **`PlayerAudio.terminados`:** a chave do áudio que tocou até o fim. Parar ou trocar de áudio não conta, porque só o `STATE_ENDED` do Media3 avisa. O padrão da interface é um fluxo vazio, então os players falsos dos testes não precisaram mudar.
- **`proximoAudio`** (`core:model`, `AudiosEmSequencia.kt`): a regra do web (`tocarProximo`) sobre as mensagens da lista do chat, na ordem; mensagem oculta não tem áudio à mostra.
- **`ChatViewModel`:**
  - ao ouvir um áudio desta conversa terminar, acha o próximo nas mensagens do estado;
  - toca pelo mesmo `alternarAudio` da bolha, que baixa o arquivo na primeira vez e marca como ouvido o áudio de outra pessoa.
- **Testes:**
  - `AudiosEmSequenciaTest`: mesma mensagem antes da de baixo, mensagem minha, fim da conversa, mensagem sem áudio, oculta, mensagem fora da lista;
  - `ChatViewModelTest`: o 1 termina e toca o 2; o 2 termina e para na mensagem de texto (o 4 não toca); áudio de outra conversa não mexe nesta.

## Como foi verificado
- `:core:model:test`, `:feature:chat:testDebugUnitTest` e `assembleDebug`.
- **No emulador (A, na direta com o C):**
  - dois áudios de 2 s gravados seguidos (segurando o microfone);
  - tocado o primeiro, o botão dele passou a "Pausar"; no fim, o do segundo passou a "Pausar" sozinho;
  - no fim do segundo, os dois voltaram a "Ouvir" (não havia mensagem abaixo).

## Pendências
- Nenhuma.
