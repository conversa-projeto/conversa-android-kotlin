# 2026-10-15 · 24 · Revisão: matriz de paridade e TODO

- **Fluxo:** Planejamento e documentação
- **Tipo:** documentação
- **Itens:** `TODO.md` › Revisão periódica; doc 05
- **Branch:** `reescrita`
- **Commits:** `941f14f`

## Contexto
- **A revisão periódica** do `TODO.md` pede marcar o que foi feito, atualizar a matriz de paridade (doc 05) e ver se o servidor e o web mudaram.
- **A matriz** tinha sido revista em 2026-10-13 e no começo de 2026-10-15, antes do trabalho do dia.

## O que foi feito
- **Servidor e web:** `git fetch` nos dois. Nada depois de `e25c4db` (servidor) e `8b43965` (web), os já sincronizados hoje.
- **Doc 05, coluna "Nova base":**
  - **Passaram a ✅, com o commit:** CON-01, CON-15, MSG-06, MSG-21, MSG-22, ENV-17, ENV-24, ENV-26, ENV-27, ANX-12, PES-01, CHA-25, CHA-26, PRE-01, PRE-02, PRE-03 e CFG-09.
  - **GER-08:** foi a 🟡, porque o "Transcrever" já some, mas "Resumir" e a sugestão ainda não existem.
  - **Ganharam o commit novo:** ANX-04 (pinça), CHA-16 (etapas do vídeo), CHA-18 (conferido no 8.7) e ATV-01 (lida no chat).
  - **O placar** foi recontado da tabela, por script: 131 ✅, 4 🟡, 3 ⛔, 11 ⬜ e 4 ➖, de 153. A leitura e a nota da atualização foram reescritas.
- **`TODO.md`:**
  - fechadas as linhas do `:core:webrtc` e dos módulos de feature, criados nas etapas 6 a 8, com os commits de criação;
  - a revisão periódica ficou com a data de hoje.

## Como foi verificado
- **O placar** sai da contagem das linhas da tabela, sem os AND, e o total bate com os 153 de antes.
- **Os commits** citados foram conferidos no `TODO.md` e no `git log`.

## Pendências
- **O que segue aberto na matriz:**
  - **🟡:** CON-08 (o servidor não remove outro membro), NOT-02 (avatar como ícone da notificação), CFG-07 (seção de IA, FC-812) e GER-08;
  - **⛔:** NOT-04 (Firebase), ENV-21 e ENV-28 (decisão do campo rico, FC-416);
  - **⬜:** a etapa 9, CON-14 e ENV-25 (IA) e o CFG-03.
