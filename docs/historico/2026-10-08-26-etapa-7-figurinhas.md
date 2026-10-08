# 2026-10-08 · 26 · Etapa 7 — figurinhas animadas

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.8; FC-509; ENV-04
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
O web tem 24 figurinhas animadas (Lottie) em três pacotes (`utils/figurinhas.ts`, `FigurinhaLottie.vue`, `BolhaFigurinha.vue`). A mensagem leva só o identificador "pacote/nome" (conteúdo tipo 7). No Android elas apareciam como o marcador "🏷 Figurinha".

## O que foi feito
- **Assets:** as 24 animações do `conversa-web/public/figurinhas` (176 KB) foram para `feature/chat/src/main/assets/figurinhas/`. Só o chat usa; elas entram no app do mesmo jeito.
- **Dependência:** `com.airbnb.android:lottie-compose` 6.7.1.
- **`core:model` — `Figurinhas.kt`:**
  - o catálogo do web (`PACOTES_FIGURINHAS`, os nomes em português);
  - `nomeFigurinha`;
  - `caminhoFigurinha`, que só aceita identificadores do catálogo: o conteúdo vem de outra pessoa e não pode apontar para fora da pasta.
- **`core:data`:** `EnvioMensagens.enviar(figurinha =)`. A figurinha vai depois do texto, como no web, e pode ir sozinha.
- **`feature:chat` — `Figurinhas.kt`:**
  - **`FigurinhaAnimada`:** a animação dos assets, em loop.
    - Só é desenhada enquanto está na lista (a LazyColumn descarta o que sai da tela).
    - Com "Remover animações" (escala de animação zero), fica parada no meio, como o `prefers-reduced-motion` do web.
    - Desconhecida ou com falha: o quadro "Figurinha".
    - O TalkBack lê o nome.
  - **`BolhaFigurinha`:** 160 dp, sem fundo de bolha, com a hora embaixo. Dentro de outra bolha (citação, encaminhada) a figurinha tem 120 dp.
  - **`SeletorDeFigurinhas`:** "Figurinha" no "+" do campo; uma aba por pacote e a grade animada. O toque envia na hora, como resposta se houver uma pendente.
  - `ChatViewModel.enviarFigurinha`.

## Como foi verificado
- **Testes novos:**
  - `FigurinhasTest`: pacotes 8 + 7 + 9, nome e caminho, recusa de `../`, e toda figurinha com a animação nos assets;
  - `MensagensTest`: figurinha sozinha e depois do texto;
  - `ChatViewModelTest`: a figurinha leva a resposta pendente.
- **Verificação completa:** `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou, com 344 testes unitários; o lint ficou sem avisos.
  - No caminho, o `RealtimeClientTest` (`core:network`, fora deste bloco) falhou uma vez ao fechar o `MockWebServer` e passou em três repetições: é instável. Ficou a sugestão de tarefa para estabilizá-lo.
  - O Gradle também corrompeu os resultados de teste depois das repetições (`EOFException`, "Index out of bounds"); limpar `build/test-results` resolveu.
- **Emulador (A), no "Grupo criado pelo B":**
  - "+" → "Figurinha" abriu as abas Básico/Rostos/Coisas com as animações;
  - o coração saiu com 160 dp e a hora embaixo (servidor: `7:basico/coracao`);
  - do B, "Legal" animou e um identificador desconhecido mostrou "Figurinha".
  - "Remover animações" não foi ligado: é configuração do sistema do emulador.

## Decisões
- **A figurinha vai sozinha, na hora.** No web, com algo escrito no campo, ela entra como peça do campo rico; no Android ainda não há campo rico (decisão FC-416). O envio já aceita texto + figurinha para quando houver.

## Pendências
- Testar "Remover animações" em aparelho.
