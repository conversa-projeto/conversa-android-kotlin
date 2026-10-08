# 2026-10-08 · 17 · Etapa 6 — tela e ponteiro remotos

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 6.13 (tela remota compartilhada, ponteiro remoto); FC-722
- **Branch:** `reescrita`
- **Commits:** `3a9f39d`

## Contexto
No web, um participante pode compartilhar a tela, e os outros podem apontar sobre ela. Os dois chegam pelo sinal da chamada (WS 57, contrato §9.11):
- `{acao:"tela", ativa}`;
- `{acao:"ponteiro", alvo, x, y}`.

O app ainda ignorava esses sinais.

## O que foi feito
- **`GerenciadorChamadas`:**
  - **`{acao:"tela"}`:** quem compartilha entra em `EstadoChamada.telas` e vai para o destaque (o TODO pede destacar a tela). Ao parar, volta à grade e somem os ponteiros sobre aquela tela.
  - **`{acao:"ponteiro"}`:**
    - `EstadoChamada.ponteiros`, com o nome do participante ("Participante" quando não se sabe, como o web);
    - `x`/`y` nulos tiram o ponteiro;
    - sem atualizar por 5 s, some.
- **Tela (`TelaChamada.kt`):**
  - a tela compartilhada aparece inteira (encaixada, sem cortar);
  - os ponteiros são desenhados sobre a área real da imagem: o renderizador informa a resolução do vídeo e `areaDaImagem` calcula onde ela fica dentro do quadro;
  - cada ponteiro é uma bolinha com o nome, na cor da pessoa (`id % 5`, como o web), com 5 cores do tema no lugar das do web.
- **Barra de controles:**
  - **achado no teste:** com 7 botões, "Sair da chamada" ficava escondido na rolagem;
  - agora fica fixo à direita, e só os outros controles rolam.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **295 testes, 0 falhas**;
  - novos: 2 no `GerenciadorChamadasTest` (tela em destaque e ao parar; ponteiro com x nulo e os 5 s) e 1 no `TelaChamadaTest` (`areaDaImagem`);
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, chamada de vídeo em grupo** (o B e o C no web, o app pela tela):
  - **Compartilhar:** o B compartilhou a tela de verdade (`compartilharTela` no Chrome de teste), e o app passou sozinho ao destaque com a tela do B.
  - **Apontar:** o C ligou o ponteiro e apontou no meio (0,5; 0,5). O ponteiro "Teste Android C" apareceu no meio da tela do B e sumiu 5 s depois.
  - **Barra:** "Sair da chamada" à vista, com os outros controles rolando.

## Pendências / próximos passos
- **Compartilhar a tela e apontar a partir do app:** fica para a etapa 9 (extras), como no TODO.
- **6.14:** a matriz de testes manuais (`docs/testes/chamadas.md`).
