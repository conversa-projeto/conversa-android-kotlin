# 2026-10-06 · 03 · Sincronização com os commits da noite (votação, campo rico, chat completo na chamada)

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Commits de origem:**
  - servidor `8031fa5` (2026-10-06 21:22): "Votação em grupo: enquete com escolha única ou múltipla";
  - web `39d06f9` (2026-10-06 21:22): "Campo de mensagem rico, votação em grupo e chat completo na chamada".
- **Observação:** os dois commits estavam só no GitHub (`origin/main`). As cópias locais de `conversa` e `conversa-web` seguem 1 commit atrás, e nada foi alterado nelas. A leitura foi feita com `git fetch` + `git show origin/main`.

## O que mudou no servidor (`8031fa5`)
- Migração `035.sql`: tabelas `enquete`, `enquete_opcao`, `enquete_voto`.
- Rotas novas (o total passou de 64 para **67**):
  - `PUT /enquete {conversa_id, pergunta ≤ 300, opcoes 2–12 (≤ 200), multipla}`, só em grupo;
  - `GET /enquete?id=`;
  - `POST /enquete/votar {enquete_id, opcoes[]}`, que **substitui** o voto (vazio tira).
- **WS 62 `EnqueteAtualizada`** `{enquete_id, conversa_id}`, enviado a todos os membros a cada voto.
- **Conteúdo tipo 8** = id da enquete. Só o servidor grava; `PUT /mensagem` com tipo 8 → 400, ou seja, enquete **não pode ser encaminhada**.
- Prévia e push: `enquete`.

## O que mudou no web (`39d06f9`)
- **ENV-21 — campo de mensagem rico:** `contenteditable`. Anexos, figurinhas e menções ficam no meio do texto, e os conteúdos são enviados na ordem do campo. A fila de anexos (ANX-01) foi removida.
- **ENV-22 — criar votação:** pelo "+", só em grupo.
- **MSG-20 — bolha de votação:** barras, votantes, votar/trocar/tirar, tempo real pelo WS 62.
- **CHA-24 — chat completo na chamada:** usa o mesmo `MessageList`/`MessageInput`.
- **Cores dos botões da chamada:** desligado = vermelho; ligado = azul.

## Documentos atualizados (marcados com 🆕)
- `01-contrato-servidor-atual.md`:
  - cabeçalho, total de rotas, rotas 65–67;
  - enum e eventos do WS (62), push, tipo 8, tabelas;
  - nova §10.13 Enquetes;
  - checklist item 17, pegadinhas 47–48;
  - nova §20 Histórico.
- `02-mudancas-servidor-desde-abril.md`: commit 23 na linha do tempo, endpoints novos, WS 62.
- `03-inventario-web.md`:
  - marcações em ANX-01, ENV-01, ENV-04, ANX-11, CHA-11, CHA-18;
  - MSG-07 com Enquete;
  - contagem 136;
  - nova §10.
- `05-matriz-paridade.md`: linhas ENV-21, ENV-22, MSG-20, CHA-24; placar 136 (⬜ 78).
- `06-fila-de-correcoes.md`:
  - novos FC-315, FC-415, FC-416, FC-516, FC-517, FC-518, FC-723;
  - ajustes em FC-108, FC-112, FC-303, FC-714.
- `07-plano-nova-base.md`: `EnqueteAtualizada` no `EventoSocket`; faixas de FC por fase.
- `00-LEIAME.md`: nota de atualização e totais.
- `TODO.md`:
  - DTO `Enquete`; WS 62; classificação; placeholder da votação;
  - nova 4.11 (campo rico); nova 7.12 (votação);
  - cores dos controles; chat completo da chamada.

## Achado
- O web mostra "Encaminhar" em mensagem de votação, mas o servidor recusa (400). No Android: esconder (FC-518).

## Decisões
- Campo rico: recomendação de começar **simplificado** na F3 e evoluir depois do Marco 1. A decisão final fica para o FC-416, registrada em ADR.
