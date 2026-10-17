# 2026-10-17 · 21 · Branch `apresentacao` juntada na `reescrita`

- **Fluxo:** Repositório e build
- **Tipo:** build
- **Itens:** histórico `2026-10-17-20` (ADR 0002 opção A, minimizar a chamada, servidor interno no debug)
- **Commits:** `ca13388` (merge de `e69a64e` na `reescrita` em `3450d49`)

## Contexto
O trabalho da apresentação (`f4a9be3`, `e69a64e`) foi feito num worktree, na branch `apresentacao`, enquanto outra sessão fazia a Localização e as pastas de mensagens na `reescrita` (`eaa4f42` … `3450d49`). Com a outra sessão parada e o checkout limpo, a pessoa pediu para juntar.

## O que foi feito
- `git merge --no-ff apresentacao` na `reescrita`.
- **Um conflito, em `core/network/.../di/RedeModulo.kt`.**
  - A Localização trocou o `addInterceptor(log)` por um interceptador que deixa os serviços de mapa fora do log (`ehServicoDeMapa`).
  - A apresentação acrescentou, logo depois, o bloco da `ConfiancaServidor`.
  - Ficaram os dois, nessa ordem.
  - Os mapas (OSM, Nominatim, OSRM) usam certificados públicos. A `ConfiancaServidor` só aceita um certificado fora das CAs depois que a validação do Android falha, e só o certificado exato que a pessoa aceitou, então os mapas não são afetados.
- `TODO.md`, `docs/historico/indice.md` e os `strings.xml` de `app` e `feature/chamada` juntaram sozinhos.

## Como foi verificado
- `./gradlew assembleDebug testDebugUnitTest :core:model:test :core:testing:test ktlintCheck` na `reescrita` depois da junção. Resultado no commit: build OK, 597 testes unitários sem falha, ktlint limpo.
- `node ferramentas/textos-repetidos.mjs`: nenhum nome de texto repetido com valores diferentes.
- O comportamento da apresentação já tinha sido testado no emulador (histórico `2026-10-17-20`). A junção não mudou nenhuma linha desse código além da ordem no `RedeModulo`.

## Decisões
- Merge com `--no-ff`, para a branch da apresentação continuar visível no histórico.

## Pendências / próximos passos
- ~~O worktree `../conversa-android-apresentacao` e a branch `apresentacao` podem ser apagados quando a pessoa quiser.~~ Apagados em 2026-10-17, a pedido da pessoa (os commits seguem na `reescrita` pelo merge), junto com o AVD `Apresentacao`. O APK da apresentação (`conversa-projeto/Conversa-apresentacao-debug.apk`) foi gerado antes da junção, **sem** a Localização.
- ~~O AVD `Apresentacao` (4,7 GB), criado só para os testes, pode ser apagado.~~ Apagado (ver acima).
