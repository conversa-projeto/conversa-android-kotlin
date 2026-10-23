# 2026-10-23 · 03 · Atualiza Kotlin, OkHttp, DataStore, Tink, serialização e Markdown

- **Fluxo:** Repositório e build
- **Tipo:** dependências
- **Itens:** a pendência do histórico 2026-10-22-08 (os avisos `NewerVersionAvailable` e `GradleDependency` do lint)
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
O lint de 2026-10-22 apontou 11 versões novas (9 `NewerVersionAvailable`, 2 `GradleDependency`). Ficaram anotadas como pendência para avaliar à parte.

## O que foi feito
`gradle/libs.versions.toml`:

| Biblioteca | De | Para |
|---|---|---|
| Kotlin (plugins `jvm`, `compose`, `serialization`) | 2.4.20 | 2.4.21 |
| OkHttp (`okhttp`, `logging-interceptor`, `mockwebserver3`) | 5.5.0 | 5.5.1 |
| DataStore (`datastore`, `datastore-preferences`) | 1.2.1 | 1.3.0 |
| Tink (`tink-android`) | 1.23.0 | 1.24.0 |
| kotlinx-serialization-json | 1.11.0 | 1.12.0 |
| multiplatform-markdown-renderer-m3 | 0.45.0 | 0.46.0 |

- O KSP (2.3.12) não depende mais da versão do Kotlin e ficou como estava.
- Nenhum código mudou.

## Como foi verificado
- `:app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck :app:compileDebugAndroidTestKotlin`: tudo passou (623 testes, 0 falhas).
- O lint ficou só com `AndroidGradlePluginVersion` e `UseKtx`; os avisos de versão nova sumiram.
- Os avisos de API obsoleta da compilação já existiam: são das corrotinas e do Compose, que não mudaram.
- **No emulador, por cima da versão anterior:**
  - o app abriu com a sessão de antes, o que confirma que o Tink e o DataStore leram o que já estava gravado;
  - um bloco ` ```md ` mandado pelo C apareceu formatado na 0.46.0: título, negrito, itálico, código e lista.

## Decisões
- **Atualizar tudo de uma vez:** são correções e passos pequenos, e a verificação completa cobre o conjunto. A biblioteca de Markdown, que é 0.x, foi conferida na tela.

## Pendências
- Nenhuma.
