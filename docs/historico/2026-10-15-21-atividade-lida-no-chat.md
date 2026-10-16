# 2026-10-15 · 21 · Resposta ou menção lida no chat deixa de contar como nova

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.1 (linha 🆕 `e25c4db`); ATV-01
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`e25c4db`): resposta ou menção cuja mensagem o usuário já leu no chat deixa de contar em `GET /atividades/novas` e vem com `nova: false` em `GET /atividades`. Ao ler uma dessas mensagens, ele manda o WS 61 ao próprio usuário.
- **O app:** o WS 61 já atualizava o contador. Com a aba fechada, a lista guardada no Room continuava com o `nova` antigo e, ao abrir a aba, mostrava por um instante a marca de nova de algo já lido.

## O que foi feito
- **`AtividadesRepositorio.aoReceberAviso`:** com a aba fechada, além do contador, relê a primeira página se já há lista guardada. Com a aba aberta, segue como antes (relê e marca como vistas).
- **`AtividadeDao.quantas()`.**
- **Teste:** com a aba fechada, o aviso relê a lista e a resposta lida perde o "Nova", sem marcar como vistas.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (516 testes), `:app:lintDebug` (só os 3 avisos de sempre) e `ktlintCheck`. Passou.
- **No emulador (A),** com o B pela API:
  - na lista de conversas, o contador da aba Atividades mostrava 1 (o servidor também);
  - o B respondeu a uma mensagem do A no "Grupo criado pelo B" e o contador foi a 2;
  - o A abriu o grupo (a resposta foi lida) e, ao voltar, o contador estava em 1, igual ao servidor;
  - na aba, "Teste Android B respondeu sua mensagem · Resposta para a ATV-01" sem a marca de nova. Só a chamada perdida, não vista, continuou marcada.

## Decisões
- **Com a aba fechada, relê só se já há lista guardada.** Sem ela, a aba carrega ao abrir, como antes.
