# 2026-10-08 · 25 · Etapa 7 — menções no campo

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.7; FC-508; ENV-05, MSG-12
- **Branch:** `reescrita`
- **Commits:** `76a7357`

## Contexto
No web, digitar `@` no campo abre uma lista de até 6 contatos (`MencaoDropdown.vue`). Escolher insere "@Nome" e guarda o id; no envio, o texto vira `@[Nome](id)` (`utils/mencoesTexto.ts`). A bolha já mostrava `@[Nome](id)` como "@Nome" clicável desde a etapa 3.

## O que foi feito
- **`core:model` — `Mencoes.kt` (porte do `mencoesTexto.ts`):**
  - `dividirMencoes`: o nome precisa terminar ali, e "@Ana" não casa com "@Anabela" nem com "@Ana_1"; os nomes maiores são tentados antes;
  - `textoParaEnvio`;
  - `extrairMencoesCruas`: texto com "@[Nome](id)" volta a "@Nome", com as menções;
  - `mencaoDigitada`: o "@termo" antes do cursor, com as mesmas classes do regex do web (letra ASCII, número, "_" e espaço). É uma varredura, sem regex, o que evita a diferença entre o ICU do Android e a JVM dos testes;
  - `sugestoesDeMencao`: nome ou login, no máximo 6.
- **`feature:chat`:**
  - `SugestoesDeMencao` acima do campo, com foto e nome. Escolher troca o "@termo" por "@Nome " e guarda a menção.
  - As menções aparecem destacadas no campo, em azul e seminegrito, por um `OutputTransformation`. O texto do campo continua "@Nome".
  - O "Enviar" manda `textoParaEnvio` e, depois de gravado, limpa o texto e as menções.
  - Texto compartilhado por outro app com "@[Nome](id)" entra como "@Nome", com as menções.
  - `ChatViewModel.contatosMencao`: todos os contatos, como o web.

## Como foi verificado
- `MencoesTest`, 7 testes: envio, "@Anabela"/"@Ana_1"/"@Anaé", nome maior primeiro, trechos, cruas, a menção digitada (inclusive e-mail e acento, que não abrem a lista) e as sugestões.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador (A), no "Grupo criado pelo B":**
  - "@Te" mostrou Teste Android B e C;
  - escolher o B deixou "@Teste Android B" destacado no campo;
  - o servidor recebeu `O @[Teste Android B](2) td`;
  - a bolha mostrou "@Teste Android B", e o toque abriu a direta com o B.
  - Observação: o `adb shell input text` do emulador perde letras perto do "@". O "@" foi digitado com `KEYCODE_AT`.

## Decisões
- **A lista usa todos os contatos,** como o web, e não só os membros do grupo. Mencionar quem não está na conversa também funciona no web.
