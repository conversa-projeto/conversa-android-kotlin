# 2026-10-08 · 13 · Etapa 6 — modos de exibição da chamada (grade, destaque, tela única)

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes / documentação (conta de teste nova)
- **Itens:** `TODO.md` 6.11 (modos, toque para destacar, serviço com câmera) e 6.2 (estado completo); FC-716; CHA-12, CHA-16
- **Branch:** `reescrita`
- **Commits:** `482037d`

## Contexto
Numa chamada com várias pessoas, o web deixa escolher como elas aparecem:
- **Grade:** todos lado a lado;
- **Destaque:** um grande e os demais na lateral;
- **Tela única:** só um, trocado por setas.

Quem escolhe "só assistir" abre em tela única em quem transmite. No app havia só a grade.

## O que foi feito
- **Estado (`EstadoChamada`):**
  - `Exibicao(modo, destaque)` com `ModoExibicao` `GRADE`/`DESTAQUE`/`UNICA`;
  - `GerenciadorChamadas.exibir(modo, destaque)`;
  - "Apenas assistir" (resposta ao 56) e "Atender só assistindo" (ou atender vídeo sem câmera) → tela única em quem ligou ou ativou o vídeo, como o web;
  - a chamada seguinte começa na grade.
- **Tela (`TelaChamada.kt`):**
  - **Seletor** "Grade / Destaque / Tela única" (rótulos do web), com 2 ou mais participantes.
    - Trocar de modo mantém quem está em destaque; sem destaque, começa pelo primeiro.
  - **Grade:** como antes, até 2 colunas no celular (o web também fica em 2 em tela estreita). Tocar num participante o destaca.
  - **Destaque:** um grande (tocar volta à grade) e os outros numa faixa embaixo (tocar troca o destaque).
  - **Tela única:** um só, com as setas "Participante anterior" / "Próximo participante".
  - **Quem saiu:** se quem estava em destaque saiu ou ainda está conectando, mostra a grade até ele voltar (o modo fica guardado).
  - **Toque sobre o vídeo:** uma camada por cima, porque o `SurfaceView` não repassa o toque.
    - Ela leva o nome do participante para o TalkBack: sem isso, o Compose escondia o nome, que ficava coberto.
- **Conta de teste C** (`teste.android.c`, no servidor local; `docs/desenvolvimento/emulador.md`):
  - criada pelo cadastro da API, com a mesma senha das contas A e B;
  - serve para chamada em grupo, com um segundo Chrome de teste na porta 9334.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **283 testes, 0 falhas**;
  - 5 testes novos no `GerenciadorChamadasTest`: apenas assistir, transmitir também, atender só assistindo, atender com câmera, exibir/trocar/voltar/próxima chamada;
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, chamada de vídeo em grupo** (o B ligou para A, B e C; o C atendeu no web e o app pela tela):
  - **Grade:** "3 pessoas", com o vídeo do B e do C, a miniatura "Você" e o seletor.
  - **Toque no C:** destaque no C, o B na faixa.
  - **"Tela única":**
    - mostrou o C;
    - **achado:** na primeira versão mostrava o primeiro participante, e não quem estava em destaque — corrigido.
  - **Setas:** C → B → C.
  - **Toque no único:** voltou à grade. "Destaque" sem ninguém destacado começou pelo primeiro.
  - **Serviço:** com a câmera ligada, `phoneCall|microphone|camera`.
  - Os nomes dos participantes aparecem para a acessibilidade (conferido pela árvore do uiautomator).

## Pendências / próximos passos
- **6.12:** minimizar (PiP e o banner "Toque para voltar à chamada").
- **6.9:** o teste só com o TalkBack (atender e desligar).
