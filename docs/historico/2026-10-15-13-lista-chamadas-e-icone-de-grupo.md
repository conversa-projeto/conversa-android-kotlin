# 2026-10-15 · 13 · Lista: seção "Chamadas (N)", ícone de grupo e emoji do grupo nos avatares

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código
- **Itens:** `TODO.md` 2.x (as linhas 🆕 `769bd7d` "Chamadas (N)", `b8ea0c2` emoji ou imagem do grupo e `e33ecba` ícone de grupo); FC-218, FC-219, FC-217 (a parte de mostrar); CON-01, CON-15, GER-07
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O que o servidor manda na lista** (`GET /conversas`): `chamada` (é o chat de uma ligação, `769bd7d`) e `emoji` (do grupo, `b8ea0c2`). A imagem do grupo vem em `avatar_url`, que o Android já lia.
- **O que o web faz com isso:**
  - põe os chats de ligação não fixados numa seção recolhível "Chamadas (N)", antes de "Arquivadas";
  - trocou a etiqueta "Grupo" por um ícone de pessoas, com a descrição "Conversa em grupo" (`e33ecba`);
  - mostra o emoji no lugar da inicial.

## O que foi feito
- **Dados:**
  - `emoji` e `chamada` no `ConversaDto`, na `ConversaEntidade` e no `Conversa`;
  - **Room v6:** migração automática 5→6; `chamada` com `defaultValue = "0"`. O esquema `6.json` foi exportado.
- **`Avatar`:** o parâmetro `emoji`, que fica no lugar da inicial quando não há imagem.
- **Onde o emoji aparece:** na lista, no cabeçalho do chat e no "Enviar para".
- **Lista** (`ConversasViewModel`, `ConversasTela`):
  - a seção "Chamadas (N)", recolhida no começo, com o ícone de telefone, antes de "Arquivadas (N)";
  - nela ficam os chats de ligação não fixados e não arquivados, do mais recente ao mais antigo. A fixada fica em cima; a arquivada, em Arquivadas. Com termo de busca, tudo fica junto, como o web;
  - as duas seções usam o mesmo cabeçalho (`CabecalhoSecao`);
  - o grupo mostra o ícone `Group` com "Conversa em grupo", e a etiqueta "Grupo" (`etiqueta_grupo`) saiu;
  - o estado ganhou `vazia`, porque a condição com as quatro listas passou do limite de linha.
- **Testes:**
  - a seção (fixada fora, arquivada em Arquivadas, a ordem, abrir e fechar, e tudo junto com termo) e o emoji no item;
  - o JSON com `emoji` e `chamada`.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (495 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A):**
  - a versão nova migrou o banco para a v6;
  - no fim da lista, "Chamadas (4)" e "Arquivadas (1)". Aberta, a seção mostrou o grupo da ligação de hoje ("Teste Android A, Teste Android B") e os três "Chamada: …" antigos, com os contadores;
  - os grupos com o ícone de pessoas.

## Pendências
- Escolher a imagem ou o emoji do grupo, na tela do grupo e ao criar (FC-217): o próximo bloco, que também testa o emoji na lista.
