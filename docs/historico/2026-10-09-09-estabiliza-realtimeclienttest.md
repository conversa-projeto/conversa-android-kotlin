# 2026-10-09 · 09 · Estabiliza o RealtimeClientTest

- **Fluxo:** Qualidade, segurança e publicação. Toca o teste da fundação (etapa 1).
- **Tipo:** teste / correção de instabilidade
- **Itens:** a pendência anotada em `2026-10-08-26-etapa-7-figurinhas.md` (o `RealtimeClientTest` falhou uma vez ao fechar o `MockWebServer`)
- **Branch:** `reescrita`
- **Commits:** `762bf2c`

## Contexto
O `RealtimeClientTest` (`core:network`) abre WebSockets de verdade contra o `MockWebServer`. Na verificação completa da 7.x, ele falhou uma vez no `@After`, com `AssertionError: Gave up waiting for queue to shut down` vindo do `MockWebServer.close()`, e passou quando repetido.

O `close()` do `mockwebserver3` 5.5.0 fecha o socket de escuta e espera até 5 s cada fila de atendimento ficar ociosa. A fila de uma conexão WebSocket só fica ociosa quando o fechamento termina dos dois lados.

## Diagnóstico
- **Isolado:** a classe passou em 12 rodadas seguidas.
- **Sob carga:** um teste temporário (não commitado) repetiu os 5 cenários com WebSocket 30 vezes na mesma JVM, com 18 dos 20 núcleos ocupados. Deu **2 falhas em 150**, as duas no cenário "desconectar fecha e para de tentar", com o `@After` preso por 5 s.
- **Causa:**
  1. Nesse cenário, o cliente se desconecta logo depois de conectar.
  2. Sob carga, o `onOpen` do lado do servidor (que registra o socket na lista do teste) ainda não tinha rodado quando o `@After` fechou os sockets registrados.
  3. O servidor de teste recebia o "close" do cliente, mas não respondia, porque o ouvinte dele não tratava `onClosing`.
  4. A conexão ficava esperando o "close" do servidor; o OkHttp só corta à força depois de 60 s.
- **Não era defeito do `RealtimeClient`:** era do servidor falso do teste. O servidor de verdade responde ao "close".

## O que foi feito
- `RealtimeClientTest`:
  - o ouvinte do servidor de teste responde ao "close" do cliente (`onClosing` → `close(1000)`), como o servidor de verdade. Nenhum socket depende mais de estar na lista;
  - no `@After`, o `MockWebServer` fecha **antes** de derrubar as threads do cliente, que ainda precisam ler o "close" do servidor e responder.
- Nenhuma mudança no código do app.

## Como foi verificado
- **Depois da correção, com a mesma carga:** 3 rodadas de 150, **0 falhas**, e nenhum `@After` passou de 1 s. Antes eram 2 falhas a cada 150.
- **Verificação completa:** `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- O teste temporário foi apagado.
