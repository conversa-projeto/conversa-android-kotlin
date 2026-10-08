# 2026-10-08 · 20 · Sincronização: limite de reações, votação com data final e painel do grupo

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** `TODO.md` 2.10, 6.13, 7.2, 7.12, 8.6 (linhas 🆕); FC-212, FC-501, FC-516, FC-517, FC-719, FC-723
- **Branch:** `reescrita`
- **Commits:** _(preencher)_
- **Commits de origem** (pull feito em 2026-10-08; as cópias locais estavam 2 commits atrás):
  - servidor `d4435db` (2026-10-07 20:30): "Reações: no máximo 5 emojis diferentes por pessoa na mesma mensagem";
  - servidor `5cad911` (2026-10-07 20:41): "Votação com data final e encerramento antes do prazo";
  - web `eaa8bac` (2026-10-07 20:30): "Painel do grupo, limite de reações, votação oculta e chave na chamada";
  - web `785bdef` (2026-10-07 20:41): "Votação com data final e opção de encerrar".

## Contexto
Pedido do usuário: depois do bloco 7.1/7.2/7.4, fazer pull em `conversa` e `conversa-web`, atualizar a documentação e o TODO com os commits novos e então seguir o TODO.

O pull foi `git pull --ff-only` nos dois repositórios, sem alterações locais. No web, só o `yarn.lock` está fora do Git e não foi tocado. O servidor de dev (Docker) recarregou sozinho e aplicou a migração 036 ("Migrações aplicadas até a versão 36" no log da API).

## O que mudou no servidor
- **`d4435db`:** `PUT /mensagem/reacao` recusa o 6º emoji diferente da mesma pessoa na mesma mensagem: 400 "Você já reagiu com 5 emojis nesta mensagem.". Tirar continua livre.
- **`5cad911`:**
  - migração `036.sql`: `encerra_em`, `encerrada_em` e `encerrada_por` na `enquete`;
  - `PUT /enquete` aceita `encerra_em` (pelo menos 1 minuto no futuro, no máximo 1 ano, cortado no minuto);
  - `GET /enquete` ganha `encerra_em`, `encerrada_em`, `encerrada` (à mão ou prazo vencido), `pode_encerrar` (quem criou a votação ou o grupo) e `pode_alterar_prazo` (quem criou);
  - rotas novas `POST /enquete/encerrar` e `PATCH /enquete` (total: **69**);
  - votar numa votação encerrada → 400;
  - WS 62 também ao encerrar e ao mudar a data. Quando o prazo vence, o servidor não avisa nada.

## O que mudou no web
- **`eaa8bac`:**
  - painel "Dados do grupo" (`PainelGrupo.vue`, no lugar do `GroupMembersModal.vue`): nome, participantes (adicionar com busca, remover) e anexos com filtro por tipo; abre pelo avatar do grupo no cabeçalho;
  - reações: limite de 5 conferido antes da otimista; 5 chips à mostra e o resto num "+N";
  - votação oculta revelada mostra o resumo (pergunta + votos);
  - visualizador de HTML: links `#...` rolam dentro do documento;
  - botões liga/desliga da chamada com chave;
  - o grupo do chat da chamada é criado no primeiro clique no campo, que já abre o chat completo.
- **`785bdef`:** data final opcional ao criar a votação; na bolha, "encerra …", "Definir/Alterar data final", "Encerrar votação" (com confirmação), estado encerrado com 🏆; o prazo que vence com a bolha aberta encerra na hora.

## Documentos atualizados (marcados com 🆕 e o commit)
- `01-contrato-servidor-atual.md`:
  - cabeçalho (`5cad911`, migração 36);
  - total de rotas (69) e as rotas 68–69 na tabela;
  - WS 62;
  - §10.12 (limite de reações), §10.13 (data final, encerrar, campos novos, prazo vencido, comportamento do web, limitações);
  - tabela `enquete`, checklist 17, pegadinhas 49–50 e §20.
- `02-mudancas-servidor-desde-abril.md`: linhas 24 e 25 na linha do tempo; as rotas novas na §3.
- `03-inventario-web.md`:
  - marcações em CON-08, MSG-16, ENV-17, ANX-07, ANX-13, CHA-11, CHA-18, ENV-22 e MSG-20;
  - nova §11 com o resumo e o que muda para o Android.
- `05-matriz-paridade.md`: nota de atualização; coluna "Adaptação" de CON-08, MSG-16, MSG-20, ENV-17, ENV-22, ANX-13, CHA-11, CHA-18 e CHA-24.
- `06-fila-de-correcoes.md`: ajustes em FC-212, FC-501, FC-516, FC-517, FC-719 e FC-723.
- `08-pendencias-servidor.md`: nota na S15 (o painel novo do web ainda mostra "Remover"; a regra "quem criou o grupo" já existe para encerrar votação).
- `00-LEIAME.md`: nota de atualização e HEADs.
- `docs/contrato/openapi.json`: baixado de novo do servidor de dev (`5cad911`); só entraram `PATCH /api/enquete` e `POST /api/enquete/encerrar`.
- `TODO.md` (linhas 🆕):
  - 2.10: o avatar do grupo no cabeçalho do chat abre os dados do grupo;
  - 6.13: grupo do chat da chamada criado no primeiro toque no campo;
  - 7.2: limite de 5 emojis e o chip "+N";
  - 7.12: DTO e rotas novas, data final na criação, "encerra …", alterar/tirar a data, encerrar, estado encerrado e resumo da votação oculta;
  - 8.6: anexos dentro da tela do grupo.

## Como foi verificado
- `git log` antes e depois do pull nos dois repositórios; leitura dos diffs dos quatro commits (servidor: `mensagens.ts`, `enquetes.ts`, `esquemas.ts`, `rotas.ts`, `036.sql`; web: os componentes, as stores e os tipos alterados).
- Rotas conferidas no OpenAPI do servidor de dev: 68 → 70 entradas (contando as 2 públicas), só as duas de enquete novas.

## Decisões
- **Chave nos botões da chamada:** não muda no Android. O ícone, a cor do desligado e o estado lido pelo TalkBack já mostram ligado/desligado.
- **Âncoras no HTML:** não se aplica. O Android abre o HTML com outro app (4.x).
- **Ordem:** os itens 🆕 das seções já fechadas (2.10, 6.13, 7.2) são pequenos e vêm antes do 7.3, seguindo a ordem do TODO.

## Pendências
- S15 continua: remover outro participante segue recusado pelo servidor.
