# 2026-10-22 · 08 · Lint do debug com HTTP sem TLS

- **Fluxo:** Qualidade, segurança e publicação
- **Tipo:** configuração
- **Itens:** FC-102 (lint de segurança como erro); a decisão do histórico 2026-10-17-20
- **Branch:** `reescrita`
- **Commits:** `ffbb812`

## Contexto
O `app/lint.xml` trata `InsecureBaseConfiguration` como erro (FC-102). Em `f4a9be3` (a apresentação, juntada em `ca13388`), a configuração de rede **só do debug** passou a aceitar HTTP sem TLS em qualquer endereço, para o servidor interno sem a borda TLS. A decisão está no histórico 2026-10-17-20. Desde então, o `:app:lintDebug` parava com 1 erro nesse arquivo.

## O que foi feito
- `app/src/debug/res/xml/network_security_config.xml`: `tools:ignore="InsecureBaseConfiguration"` no `<base-config>`, com o motivo no comentário.
- O arquivo do release (`src/main`, só HTTPS e só as CAs do sistema) continua sob a regra.

## Como foi verificado
- `:app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck :app:compileDebugAndroidTestKotlin`: tudo passou (621 testes, 0 falhas).
- O lint ficou só com avisos: 9 `NewerVersionAvailable` e 2 `GradleDependency` (versões novas de bibliotecas), `AndroidGradlePluginVersion` e `UseKtx`.

## Decisões
- **Ignorar só no arquivo do debug, em vez de baixar a severidade no `lint.xml`:** a regra continua valendo para o release e para qualquer outra configuração.

## Pendências
- **Versões novas de bibliotecas,** para avaliar à parte:
  - Kotlin 2.4.20 → 2.4.21;
  - OkHttp 5.5.0 → 5.5.1;
  - DataStore 1.2.1 → 1.3.0;
  - Tink 1.23.0 → 1.24.0;
  - kotlinx-serialization 1.11.0 → mais nova;
  - markdown-renderer 0.45.0 → mais nova.
