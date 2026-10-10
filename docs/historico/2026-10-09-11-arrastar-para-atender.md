# 2026-10-09 · 11 · Atender e recusar arrastando

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.5 (nova linha depois de "Botões tocáveis"); CHA-03; problema #25 (acessibilidade da tela de chamada)
- **Branch:** `reescrita`
- **Commits:** `34e1c8d`

## Contexto
O primeiro teste num aparelho de verdade foi um Galaxy A25 (Android 16), ligado pela depuração por Wi-Fi. Depois dele, veio o pedido de trocar os botões de toque da chamada recebida por botões de **arrastar para qualquer lado**, com o efeito de "crescer" do discador da Samsung.

## O que foi feito
- **`feature/chamada/.../BotaoDeArrastar.kt` (novo):**
  - **Ao tocar:** o botão cresce 15% e aparece um anel claro, que mostra até onde arrastar.
  - **Ao arrastar:** um círculo na cor do botão cresce com o dedo, para qualquer lado, até encher o anel. Aí o celular vibra (`HapticFeedbackType.Confirm`) e o botão age, uma vez só.
  - **Ao soltar antes:** o círculo volta e nada acontece. Só tocar não faz nada, e assim o app não atende no bolso.
  - **Distâncias:** o dedo precisa andar 140 dp (`LIMIAR_ARRASTO`), e o anel fica a 115 dp da borda (`ALCANCE_DO_ANEL`). Como o círculo cresce a cerca de 80% do dedo, o arrasto fica "pesado".
  - **Depois de agir:** o círculo fica cheio por 1,5 s enquanto a tela muda. Se a tela não mudar, o botão volta a valer.
  - **TalkBack:** o botão leva `contentDescription`, `Role.Button` e a ação de clique da acessibilidade, e o toque duplo atende ou recusa. Isso mantém a correção do #25: o legado era só de arrasto e o TalkBack não atendia.
- **`TelaChamada.kt` (`TelaRecebendo`):**
  - Recusar e Atender viram `BotaoDeArrastarComRotulo`, com o nome embaixo. O nome fica fora do TalkBack, que já lê o botão.
  - O botão arrastado fica por cima (`zIndex`), e o outro some.
  - Acima dos botões aparece a dica "Arraste um botão para qualquer lado" (texto novo, só do Android).
  - "Atender só assistindo" continua de toque.
- **`feature/chamada/build.gradle.kts`:** Robolectric e `ui-test-junit4` nos testes do módulo.
- **`BotaoDeArrastarTest` (novo, Robolectric):** tocar não age; arrastar 40 dp não age; arrastar além do limiar age (para cima, para o lado e na diagonal, e volta a valer depois); um arrasto longo age uma vez só; a ação de clique da acessibilidade age.
- **`docs/desenvolvimento/emulador.md`:** seção "Aparelho de verdade pela depuração por Wi-Fi", com o pareamento feito pela pessoa e o `adb reverse` pelo Wi-Fi.
- **`docs/testes/chamadas.md`:** linha do teste no aparelho.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug`, `ktlintCheck` e `ferramentas/textos-repetidos.mjs`. Tudo passou.
- **No aparelho:** Galaxy A25, conta `teste.android.b`, contra o web na conta C.
  - A chamada tocou e foi atendida arrastando o botão.
  - Depois do primeiro teste, a área do anel aumentou e a sensibilidade diminuiu (de 90 dp para 140 dp de arrasto), como pedido.
  - A pessoa aprovou o resultado.

## Decisões
- **Agir quando o círculo enche o anel**, sem esperar soltar o dedo, como no discador da Samsung. O retorno vem na hora, pela vibração.
- **O anel menor que a distância do dedo** é o que deixa o gesto menos sensível sem um anel enorme na tela.
- **"Atender só assistindo" ficou de toque:** é uma opção secundária, abaixo dos botões.

## Pendências
- Testar com o TalkBack num aparelho (a linha "atender e desligar só com o TalkBack" do 6.9 continua aberta).
