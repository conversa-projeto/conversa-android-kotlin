# 2026-10-15 · 23 · Teste das migrações do Room

- **Fluxo:** Fundação (etapa 1)
- **Tipo:** teste / build
- **Itens:** `TODO.md` 1.x (linha do esquema e do teste de migração); FC-110
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O banco** está na versão 7, com seis migrações automáticas (1→7), cada uma com o esquema exportado em `core/database/schemas`.
- **O TODO** previa o teste de migração para quando surgisse a versão 2, e ele nunca foi escrito. Nenhuma migração tinha sido conferida contra os esquemas.

## O que foi feito
- **`MigracoesTest`** (`core:database`, Robolectric):
  - cada versão migra para a seguinte e o `MigrationTestHelper` valida o esquema contra o JSON exportado;
  - uma conversa, uma mensagem e um conteúdo gravados na versão 1 chegam à 7 com os dados e as colunas novas no padrão (`emoji` nulo, `chamada` 0, `postagemId` e `chatChamadaId` nulos).
- **`core/database/build.gradle.kts`:** os esquemas como assets dos testes unitários, pela API de variantes do AGP 9 (`hostTests[UNIT_TEST_TYPE].sources.assets`). O `sourceSets` antigo não funciona mais no AGP 9.
- **O driver:** o teste usa o `MigrationTestHelper` com o `AndroidSQLiteDriver`. O construtor antigo (com o `SupportSQLite`) falha no Windows: compara o nome do banco cortando o caminho na `/`.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (519 testes), `:app:lintDebug` (só os 3 avisos de sempre) e `ktlintCheck`. Passou.

## Decisões
- **Teste no Robolectric, não instrumentado.** Roda com os outros testes unitários e não desinstala o app do emulador, como o `connectedDebugAndroidTest` faria.
