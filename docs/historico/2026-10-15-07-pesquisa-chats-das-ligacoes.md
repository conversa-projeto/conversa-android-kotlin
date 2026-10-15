# 2026-10-15 · 07 · Pesquisa na conversa com os chats das ligações, painel novo e destaque em faixa

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** correção
- **Itens:** `TODO.md` 8.2 (as duas linhas 🆕) e 7.5 (destaque); FC-524; PES-01, MSG-06
- **Branch:** `reescrita`
- **Commits:** `923297f`

## Contexto
- **No servidor `6d20ff7`:** `GET /pesquisar?conversa=X` passou a trazer também as mensagens dos chats das ligações feitas em X. Cada ligação com chat cria um grupo novo.
- **No web `e33ecba`/`d202c06`:** o painel de resultados foi refeito:
  - a contagem;
  - a ordem do mais antigo ao mais novo;
  - "Você" nas minhas mensagens;
  - o selo "no chat da ligação";
  - o destaque em faixa ao chegar na mensagem.
- **No Android:** a análise da sincronização achou dois defeitos:
  - `MensagensRepositorio.pesquisar` filtrava os resultados por `conversaId` e descartaria os das ligações;
  - `abrirResultado` abria a conversa aberta, e não a do resultado.

## O que foi feito
- **`MensagensRepositorio.pesquisar`:** sem o filtro por conversa (o servidor já filtra). Continua das mais recentes para as mais antigas, para a pesquisa global.
- **`ChatViewModel`:**
  - os resultados da conversa vão do mais antigo ao mais novo, como no web;
  - `abrirResultado` usa a conversa da mensagem. O `irParaMensagem`, que já sabia abrir outra conversa, abre o chat da ligação na mensagem.
- **Painel (`ResultadosPesquisa`):**
  - cabeçalho "1 resultado para “termo”" / "N resultados para “termo”" (plural);
  - sem resultado: "Nenhuma mensagem com “termo”";
  - "Você" nas minhas mensagens;
  - selo "no chat da ligação" quando o resultado é de outra conversa.
  - O `LinhaResultadoPesquisa` (`core:ui`) ganhou os parâmetros `autor` e `selo`.
- **Destaque (`Bolhas.kt`):** a faixa já era da largura toda. Agora:
  - aparece na hora;
  - fica 1,5 s (eram 1,2 s);
  - some em 0,7 s;
  - a cor passou para `onSurface` a 15% (cinza, como o "surface-800 a 15%" do web), em vez do azul.
- **Textos novos:** `pesquisa_resultados_para` (plural), `pesquisa_nenhuma_com`, `no_chat_da_ligacao` e `voce` (o mesmo "Você" da chamada).
- **Testes:**
  - o repositório não filtra mais (a mensagem de outra conversa vem);
  - o ViewModel ordena do mais antigo ao mais novo e abre a conversa do resultado;
  - o destaque dura 1,5 s.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A):**
  - o B mandou "Combinado o abacaxi para sexta" no grupo da ligação de há pouco (o 15, criado da direta A–B);
  - na direta, "Pesquisar na conversa" → "abacaxi" mostrou "1 resultado para “abacaxi”", com "no chat da ligação" e o termo destacado;
  - o toque abriu o grupo "Teste Android A, Teste Android B" na mensagem.

## Pendências
- O formato da data no painel continua `dd/MM/aa HH:mm`. O web usa `HH:MM` hoje, `dd/MM HH:MM` no ano e `dd/MM/aa HH:MM` fora dele. Ficou como está, para não divergir da pesquisa global.
