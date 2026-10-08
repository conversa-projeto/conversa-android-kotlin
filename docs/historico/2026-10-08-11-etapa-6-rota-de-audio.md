# 2026-10-08 · 11 · Etapa 6 — rota de áudio da chamada e sensor de proximidade

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / manifesto
- **Itens:** `TODO.md` 6.8 (seletor de rota, sensor de proximidade), 6.3 (endpoints de áudio), 6.9 (controles); FC-713; AND-04, AND-05
- **Branch:** `reescrita`
- **Commits:** `cc52aa4`

## Contexto
Com o Core-Telecom no lugar (2026-10-08 · 10), as rotas de áudio já vinham do sistema, mas não havia como escolher.

O web tem o botão "Áudio saída". No celular, o natural é escolher por onde sai o som: fone do aparelho, alto-falante, Bluetooth ou fone com fio.

## O que foi feito
- **Botão "Áudio saída"** na barra da chamada (`TelaChamada.kt`):
  - o ícone mostra a rota atual (fone do aparelho, alto-falante, Bluetooth, fone com fio);
  - com duas rotas alterna direto; com mais, abre a lista (Bluetooth aparece com o nome do aparelho);
  - `stateDescription` com a rota, para o TalkBack;
  - sem Telecom não há rotas e o botão não aparece.
- **Alto-falante no vídeo** (`TelecomChamadas`): a chamada de vídeo começa no alto-falante, e passar de áudio para vídeo sai do fone do aparelho. Bluetooth e fone com fio ficam como estão.
- **Sensor de proximidade:**
  - `PROXIMITY_SCREEN_OFF_WAKE_LOCK` enquanto a chamada está em curso com o áudio no fone do aparelho;
  - solta ao mudar de rota ou encerrar, esperando o celular sair do rosto;
  - permissão `WAKE_LOCK` no manifesto.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **274 testes, 0 falhas**;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, contra o web:**
  - o botão "Áudio saída" aparece na chamada, com a rota "Alto-falante";
  - o emulador só oferece essa rota (o Telecom listou só "Speaker"), então trocar de rota e o sensor de proximidade não deram para testar aqui;
  - o sensor corretamente não ligou, porque a rota não é o fone do aparelho.

## Pendências / próximos passos
- **Testar num aparelho de verdade:** alternar fone do aparelho ↔ alto-falante, escolher um fone Bluetooth, e o sensor de proximidade apagando a tela.
