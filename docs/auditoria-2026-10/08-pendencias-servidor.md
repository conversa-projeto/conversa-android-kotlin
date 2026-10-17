# 08 — Pendências no servidor (`conversa`) que afetam o Android

**Data:** 2026-10-06 (🆕 S17–S22 em 2026-10-13, servidor em `12cf0cf`; 🆕 S11 e S20 revistas e S25 em 2026-10-15, servidor em `e25c4db`; 🆕 S26–S30 e notas em S4, S7, S10, S11, S12, S19 e S25 em 2026-10-15 (noite), servidor em `ef13c22`; 🆕 S31–S39 e notas em S4, S7, S10, S12, S16, S27 e S28 em 2026-10-16, servidor em `ac710eb`) · **Origem:** leitura de `conversa/src/*.ts` durante a auditoria (detalhes e linhas no doc 01).

> **Nenhuma alteração foi feita no servidor.** Esta lista serve para planejar o trabalho no repositório `conversa`. Itens marcados como **bloqueante** impedem uma funcionalidade P0 do Android.

---

### S1 · **Bloqueante** · Não existe push para chamada

- **Hoje:** `chamadas.ts` notifica só por WebSocket (evento 51). O FCM (`fcm.ts`) só é usado em `notificarNovaMensagemConversa` (`notificacoes.ts`).
- **Efeito:** com o app Android fechado, em segundo plano sem WS ou com o aparelho em Doze, **o telefone não toca**. O web contorna isso com a aba aberta; o Android não pode manter um socket permanente (política do Play, bateria).
- **Proposta:** ao iniciar a chamada (e ao adicionar um usuário), enviar um push **data-only, `android.priority: "high"`, TTL curto (~30 s)** para cada participante sem WS ativo. Payload sugerido:

  ```json
  {"tipo":"chamada","chamada_id":"123","usuario_id":"7","usuario_nome":"Ana","video":"1","criada_em":"<ISO>"}
  ```

  Também é preciso enviar um push de "chamada encerrada/cancelada", para parar o toque em quem recebeu só por push.
- **Compatibilidade com o web:** o service worker hoje lê `{titulo, mensagem, conversa}`. Incluir o campo `tipo` e fazer o SW ignorar o que não reconhece.

### S2 · **Bloqueante** · Entrega do push de mensagem

1. **Sem prioridade Android:** `enviarPush` só define `webpush.headers.Urgency`. É preciso `android: { priority: 'high' }`, porque sem isso o FCM pode atrasar a entrega em Doze.
2. **"Conectado" é por usuário, não por aparelho:** `usuarioConectado(usuario_id)` faz com que, se o usuário tiver **qualquer** WS aberto (por exemplo, o web no PC), o celular **não receba push**.
   - Proposta: enviar push para os dispositivos que não estão com socket aberto. Isso exige associar a conexão WS ao `dispositivo_id`, enviado no login do WS (`{tipo:1, token, dispositivo_id}`).
   - Alternativa: sempre enviar push e deixar o cliente decidir se exibe.
3. **Um só dispositivo por usuário?** A consulta faz `left join dispositivo … d.ativo = true`. Confirmar que o modelo suporta vários aparelhos ativos por usuário (web + celular) e que o login não desativa os outros.

### S3 · Eventos e push sem contexto suficiente

- **WS 2 (nova mensagem):** envia só `{titulo, mensagem}` (`notificarNovaMensagem(usuario_id, titulo, texto)`), **sem `conversa_id` nem `mensagem_id`**. O cliente precisa fazer `GET /mensagens/novas` para descobrir de onde veio.
- **Push:** `{titulo, mensagem, conversa}`, sem `mensagem_id`, `remetente_id`, tipo de conversa ou nome do grupo.
- **Proposta:** incluir `conversa_id`, `mensagem_id`, `remetente_id`, `conversa_tipo` e `conversa_descricao` nos dois. Isso permite MessagingStyle com o nome do grupo, deduplicação e deep link direto. O web pode continuar ignorando os campos extras.
- **Duplicidade:** o doc 01 registra que o WS 2 pode chegar duplicado. Incluir `mensagem_id` permite deduplicar.

### S4 · Segurança

1. **`GET /api/anexo?identificador=`** devolve a URL assinada **sem conferir se o usuário participa de alguma conversa que contém o anexo**. Quem souber ou adivinhar o SHA-256 baixa o arquivo.
2. **MediaMTX (`/webrtc/call-<id>-u-<uid>/whep`)** não exige autenticação nem participação. Os caminhos são previsíveis (ids sequenciais), então é possível **ouvir chamadas alheias**.
   - Proposta: autenticação HTTP do MediaMTX (`authMethod: http` apontando para a API, ou JWT) com um token de chamada emitido em `/chamada/entrar`/`/chamada/iniciar`. Os clientes mandam `Authorization: Bearer` no WHIP/WHEP.
3. **`mensagem_referencia`** não checa o acesso à mensagem referenciada (doc 01 §10.3). Isso permite citar ou encaminhar o conteúdo de conversas de que o usuário não participa.
   - 🆕 2026-10-15 (`ef13c22`): agora isso também **dribla as permissões de uso** — ver S28, item 1.
4. 🆕 2026-10-15 (`ef13c22`): **ferramentas externas dos bots** fazem o servidor chamar qualquer endereço http(s), inclusive a rede interna — ver S29.
5. 🆕 2026-10-16 (`ac710eb`): os testes de IA e de embeddings mandam o **token salvo** para o endereço que veio na tela — ver S39. E a localização do usuário vai do aparelho para serviços públicos de terceiros (mapa, endereço, rota) — ver S36.

### S5 · Figurinhas não são servidas pelo servidor de produção

- As animações ficam em `conversa-web/public/figurinhas/**`, mas o `bin/web` (build publicado) não contém a pasta. Ou seja, nem o web de produção as teria se o build não fosse refeito.
- **Para o Android:** embutir no APK (FC-509).
- **Para o servidor:** garantir que o build do web publique `/figurinhas/`. Se o catálogo for crescer, considerar servi-lo pela API.
- 🆕 2026-10-13: o web (`d202c06`) trocou o catálogo pelo pacote `noto` (55 figurinhas, CC BY 4.0) e apagou `basico/`, `rostos/` e `coisas/`. Mensagens antigas com esses ids passam a mostrar só "Figurinha" no web (doc 01 §10.6).

### S6 · `DELETE /api/conversa` não funciona

- Ele faz `delete from conversa` sem cascade, e `conversa_usuario`/`mensagem` têm FK, o que gera **500**.
- **Proposta:** remover a rota, ou implementar exclusão lógica ou "sair da conversa" (que já existe via `DELETE /conversa/usuario`).

### S7 · Erros 500 com mensagem crua do Postgres

- E-mail duplicado no cadastro, segundo `PUT /sip`, strings acima do limite da coluna (nome 100, login 50, dispositivo 50/15, extensão 10, emoji 10…) e FKs inválidas viram **500** com o texto do Postgres (doc 01 §3, pegadinha 42).
- **Proposta:** validar o tamanho no `esquemas.ts` (`maxLength`) e mapear `unique_violation` → **409** e `foreign_key_violation` → **400/404**, com mensagens em português. O Android passa a mostrar mensagens corretas sem precisar adivinhar.
- 🆕 2026-10-15 (`ef13c22`): o cadastro pelo administrador (`PUT /usuario/novo`) usa a mesma rotina do cadastro (`criarConta`): e-mail repetido continua dando **500**. As rotas novas de bots, ferramentas e base de conhecimento já validam tamanho e duplicidade com 400 em português.
- 🆕 2026-10-16 (`ac710eb`): as rotas de pastas validam o nome (até 60) e a cor (até 20) com 400. Já o `PATCH /parametros` não limita o tamanho dos campos: um endereço ou token com mais de **5000** caracteres (a coluna `parametros.valor`, migração 005) dá **500** com a mensagem do Postgres. Vale também para `ia_embeddings_url` e `ia_embeddings_token`, novos. Na prática, raro (só administração).

### S8 · Criação de conversa não atômica

- Criar uma conversa direta ou um grupo exige `PUT /conversa` + N× `PUT /conversa/usuario`. Uma falha no meio deixa uma conversa órfã ou sem participantes (o web e o Android sofrem igual).
- **Proposta:** `PUT /conversa {descricao, tipo, usuarios:[ids]}` transacional; para a direta, `PUT /conversa/direta {usuario_id}`, que devolve a existente ou cria.

### S9 · Marcar como visualizada é uma chamada por mensagem

- `POST /mensagem/visualizar {conversa, mensagem}`. Abrir uma conversa com 200 não lidas gera 200 requisições (no web e no Android).
- **Proposta:** `POST /mensagem/visualizar {conversa, ate_mensagem}` (marca tudo até o id) ou `{conversa, mensagens:[ids]}`.

### S10 · Descobrir recursos do servidor

- O cliente não sabe se a transcrição está configurada (`transcritor_url`), e só administradores leem `/parametros`.
- **Proposta:** `GET /api/recursos` (para qualquer usuário logado) → `{transcricao:boolean, sip:boolean, gravacao:boolean, versao_api:"…"}`.
- 🆕 2026-10-13: **parcialmente atendida** em `b8ea0c2`: `GET /api/recursos` existe, para qualquer usuário logado, e devolve `{transcricao, ia}` (doc 01 §13.3). Ainda faltam `versao_api` (e `sip`/`gravacao`, se fizerem sentido).
- 🆕 2026-10-15 (`ef13c22`): `GET /recursos` ganhou `conhecimento` (base de conhecimento dos bots), e surgiu `GET /api/cadastro` **público** (`{aberto}`), que a tela de login pode usar antes do login. `versao_api` continua faltando; com a mudança incompatível de `GET /atividades` (S26), ela fica mais necessária.
- 🆕 2026-10-16 (`ac710eb`): o critério de `conhecimento` mudou: agora basta haver endereço de embeddings (o próprio, `ia_embeddings_url`, ou o `ia_url`) e o modelo de embeddings. Pode vir **`ia: false, conhecimento: true`**, e um cliente que lia `conhecimento` como "IA ligada" passa a errar. Não há recurso para os mapas da localização (S36), e o código de permissão `usuarios` sumiu sem versão (S35): `versao_api` continua faltando.

### S11 · Ambiguidade do `tipo` da chamada

- Para o cliente, `tipo` é áudio/vídeo; na regra automática de status do servidor (`chamadas.ts`, doc 01 §9.4), ele é tratado como 1:1/grupo.
- Além disso, quem liga precisa se incluir em `usuarios`, e iniciar uma chamada só consigo mesmo dá **500 "Chamada não encontrada!"** (`notificacoes.ts:33-35`).
- Ao terminar porque o último participante saiu, **não sai o evento 52**.
- **Proposta:** separar `midia` (1 áudio / 2 vídeo) de `modo` (direta/grupo), incluir o criador automaticamente e emitir o 52 em todo encerramento.
- 🆕 2026-10-15 (`6d20ff7`): `POST /chamada/video` passou a gravar `tipo = 2` numa chamada `tipo = 1` (`chamadas.ts`, `ativarVideo`), para o resumo e o histórico dizerem "vídeo". Como o mesmo campo decide a regra de status, a chamada **troca de regra no meio**: com 3+ pessoas, a primeira saída ou recusa deixa de encerrá-la (doc 01 §9.4). A rota só confere a participação, então também muda o `tipo` de uma chamada já encerrada (o histórico muda; o resumo já gravado não). Reforça a proposta de separar `midia` de `modo`.
- 🆕 2026-10-15 (`ef13c22`): as permissões de chamada também seguem esse `tipo` (`chamadas.ts`, `iniciarChamada`): 1 pede `chamada_audio` e 2 pede `chamada_video`. Sem `tipo`, 3+ pessoas em `usuarios` valem `tipo = 2`, então quem só tem `chamada_audio` leva 403 numa chamada de áudio em grupo se o cliente não mandar o `tipo`. Mais um motivo para separar `midia` de `modo`.

### S12 · Contrato publicado

- **Já existe** OpenAPI: `@elysiajs/openapi` publica a documentação em `/api/docs` e o JSON em `/api/docs/json` (`src/app.ts:49-55`). O Android pode usar esse JSON para validar ou gerar os DTOs.
- **Lacunas:**
  - As **respostas** não têm esquema declarado (só corpo e query têm), então o OpenAPI não descreve o que volta.
  - O WebSocket e o push não estão documentados.
  - Não há `CHANGELOG-API.md` nem versão da API.
- **Proposta:**
  - Declarar `response` nos esquemas das rotas.
  - Um `CHANGELOG-API.md` no servidor.
  - Versão da API no login ou em `/recursos`.
- 🆕 2026-10-15 (`ef13c22`): o commit trocou uma chave de resposta (`nova` → `pendente` em `GET /atividades`) sem versão nem aviso, e o OpenAPI não pega isso porque as respostas não têm esquema (S26). É o caso que esta pendência queria evitar.
- 🆕 2026-10-16 (`ac710eb`): de novo, sem versão: o código `usuarios` deixou de existir (S35), entraram chaves em respostas antigas (`pastas` nas mensagens; `criado_em` nos contatos; `criado_em`, `bot` e `espaco` em `GET /permissoes`) e dois tipos de WebSocket (66 e 67). O OpenAPI mostra as rotas novas, mas não as respostas, e o WebSocket continua fora dele.

### S13 · JWT sem renovação

- O token vale 12 h e não há refresh nem logout no servidor (doc 01 §2.2). No celular, isso obriga a pedir a senha a cada 12 h, ou a guardá-la (o que o app atual faz, de forma insegura).
- **Proposta:** refresh token (rotativo, por dispositivo, revogável no logout) via `POST /api/token/renovar`. Até lá, o Android só pede login de novo ao receber 401, e **não guarda a senha**.

### S14 · Rate limit do login

- O nginx limita o login a 10 por minuto. Uma rede com NAT compartilhado (empresa) pode bloquear vários usuários juntos.
- Avaliar o limite por login, e não só por IP.

### S15 · Remover participante do grupo 🆕 (2026-10-07)

- `DELETE /api/conversa/usuario?id=` só aceita o **próprio** vínculo (`validarRemocaoConversaUsuario`, `src/autorizacao.ts`: "Regra atual: só auto-remoção. Admin de grupo via conversa.criado_por fica para depois"). O web mostra "Remover" para os outros membros, e o clique sempre dá 403.
- No Android a opção não é oferecida; a tela de membros explica que cada pessoa só pode sair por conta própria.
- **Proposta:** administrador de grupo (ao menos `conversa.criado_por`) pode remover; ou o web esconde o botão até lá.
- 🆕 2026-10-08: o painel novo do web ("Dados do grupo", `eaa8bac`) continua mostrando "Remover", e o servidor continua recusando. O servidor já usa "quem criou o grupo" para encerrar votação (`5cad911`); a mesma regra serviria aqui.

### S16 · Prévia da conversa sem o tipo do conteúdo 🆕 (2026-10-07)

- Em `GET /conversas`, `ultima_mensagem_texto` vem `""` para arquivo, áudio, gravação e chamada (doc 01 §11.2). O cliente não sabe o que foi: o web mostra "Sem mensagens"; o Android mostra "Anexo ou chamada".
- **Proposta:** incluir `ultima_mensagem_tipo` (tipo do primeiro conteúdo) na lista, para a prévia dizer "Áudio", "Arquivo", "Chamada perdida"…
- 🆕 2026-10-16 (`ac710eb`): a localização (tipo 9) ganhou texto na prévia (`"localização"`), mas arquivo, áudio, gravação e chamada continuam `""`. A proposta segue valendo.

### S17 · Respostas de postagem contam como mensagens do fluxo em `/conversas` e `/mensagens/novas` 🆕 (2026-10-13, `12cf0cf`)

- **Hoje:** as respostas de postagem (`mensagem.postagem_id` não nulo) ficam fora de `GET /mensagens` e `GET /pesquisar` (`mensagens.ts`, filtro `m.postagem_id is null`), mas **não** são filtradas em:
  - `GET /conversas` (`conversas.ts`, `conversas`): `mensagem_id`, `ultima_mensagem` e `ultima_mensagem_texto` podem ser de uma resposta, e `mensagens_sem_visualizar` conta as respostas não vistas;
  - `GET /mensagens/novas` (`mensagens.ts`, `novasMensagens`): uma resposta faz a conversa aparecer, com `mensagem_id` = id da resposta;
  - também WS 3 (ids de respostas), `GET /anexos` e o resumo por IA.
- **Efeito:** a lista mostra como "última mensagem" e como "não lida" algo que o cliente não encontra no fluxo da conversa. O contador só zera se o cliente chamar `POST /postagem/visualizar` (ou visualizar cada resposta). Um cliente que usa `mensagem_id` de `/mensagens/novas` para buscar a mensagem (`mensagemreferencia=<id>`) recebe `[]`.
- **Proposta:** em `/conversas` e `/mensagens/novas`, ou filtrar `postagem_id is null` e somar à parte as respostas não lidas (ex.: `respostas_sem_visualizar`), ou devolver também `postagem_id` da última mensagem/da linha, para o cliente levar até a postagem.

### S18 · Push continua bloqueado por qualquer socket aberto, mesmo "ausente" 🆕 (2026-10-13, `b8ea0c2`)

- **Hoje:** a presença ganhou estados (ativo, ausente, offline) e "aparecer offline", mas o push de mensagem continua usando `usuarioConectado(usuario_id)` (`notificacoes.ts:71`), que só olha se existe **alguma** conexão (`websocket.ts`, `usuarioConectado`).
- **Efeito:** o Android em segundo plano com o socket aberto (mesmo depois de mandar `{tipo:63, ativo:false}`) **não recebe push**; idem se o usuário tiver uma aba do web escondida no PC. Complementa o item 2 da S2.
- **Proposta:** enviar push quando o estado real do usuário não é `ativo` (ou, melhor, por dispositivo: push para os aparelhos cuja conexão não está ativa — exige ligar a conexão ao `dispositivo_id`, ver S2).

### S19 · `PATCH /conversa` não valida `avatar_anexo_id` 🆕 (2026-10-13, `b8ea0c2`)

- **Hoje:** `alterarConversa` (`conversas.ts`) grava `avatar_anexo_id` direto. Id inexistente → **500** com a mensagem de FK do Postgres; o tipo do anexo (imagem) e o `upload_status` também não são conferidos.
- **Proposta:** conferir que o anexo existe, é tipo 2 e está concluído; responder 400/404 em português. Mesmo caso da S7.
- 🆕 2026-10-15 (`ef13c22`): o cadastro pelo administrador (`PUT /usuario/novo`, `cadastrarUsuario`) já confere o avatar (existe e é tipo 2 → senão 400 `Avatar inválido: envie uma imagem.`). `PATCH /conversa` e `PATCH /usuario` continuam sem conferir; a mesma checagem serviria nos dois.

### S20 · Chat da chamada reaproveitado deixa de fora quem não é membro da conversa 🆕 (2026-10-13, `a40fd47`) — 🆕 quase toda resolvida em `6d20ff7`

- **Hoje** (🆕 valia de `a40fd47` a `6d20ff7`; situação atual na nota de 2026-10-15 abaixo): `PUT /chamada/chat` (`chamadas.ts`, `chatChamada`) reaproveita a conversa da ligação quando todos os participantes com status 3/4/5 e quem pediu já são membros dela. Mas o WS 57 `{acao:"chat", conversa_id}` vai a **todos** os participantes (qualquer status), inclusive quem foi adicionado à chamada e não é membro da origem (ex.: ainda tocando). E `incluirNoChatChamada` não inclui ninguém quando `conversa_chat_id = conversa_id`, então quem entra depois **não ganha acesso**: abrir o chat dá **403**. `GET /chamada/dados` também devolve esse `conversa_chat_id` sem filtro.
- **Efeito:** no web e no Android, o painel da chamada aponta para um chat que essa pessoa não consegue abrir nem usar.
- **Proposta:** ao reaproveitar, mandar o `{acao:"chat"}` só aos membros da origem; para os outros, ou criar o grupo "Chamada: …" (regra antiga) ou incluí-los na origem só se for grupo (nunca numa conversa direta).
- 🆕 2026-10-15 — **quase toda resolvida em `6d20ff7`**: o chat voltou a ser um **grupo novo** (agora com o nome só com os nomes dos participantes, sem "Chamada: "), também em ligação feita numa conversa direta ou num grupo comum; quem entra depois é incluído e recebe WS 40 (doc 01 §9.12).
  - **Ainda acontece** num caso só: ligação feita **dentro do chat de outra ligação**, com todos os participantes com status 3/4/5 e quem pediu já membros desse chat. Aí o chat é reaproveitado, e quem foi adicionado à chamada e não é membro (ex.: ainda tocando) continua recebendo o `{acao:"chat"}`, não é incluído ao entrar e leva 403; `GET /chamada/dados` continua sem filtro.
  - Ligações cujo chat foi resolvido entre `a40fd47` e `6d20ff7` continuam apontando para a direta/grupo de origem (dado já gravado).
  - Proposta restante: neste caso, incluir no chat (é sempre um grupo criado para chamada) quem entra depois, como já se faz com o grupo novo, ou mandar o `{acao:"chat"}` só aos membros.

### S21 · Resumo por IA só na memória 🆕 (2026-10-13, `b8ea0c2`)

- **Hoje:** os resumos ficam num `Map` em `resumos.ts` (30 min de validade). Reiniciar a API (ou ter mais de um processo) perde o resumo pronto ou em andamento: o `GET /conversa/resumo?id=` passa a dar **404** e o cliente precisa pedir de novo (e esperar a IA, que pode levar minutos). Não há evento ao concluir (só polling).
- **Proposta:** gravar os resumos numa tabela (com o período e as mensagens usadas) e avisar a conclusão por WebSocket; ou, no mínimo, documentar o 404 como "peça de novo" (é o que a mensagem já diz).

### S22 · Tipo 63 aceita `conversa_id` de conversa alheia 🆕 (2026-10-13, `b8ea0c2`)

- **Hoje:** ao receber `{tipo:63, ativo, conversa_id}`, o servidor guarda a conversa sem conferir se o usuário é membro (`websocket.ts`, `criarWebSocket`), e `notificarNaConversa` manda WS 63 `{conversa_id, usuario_id, aberta:true}` a **todos os membros** dessa conversa.
- **Efeito:** qualquer usuário logado pode aparecer como "com a conversa aberta" para os membros de uma conversa da qual não participa. `GET /conversa/presentes` não é afetado (só lista membros).
- **Proposta:** conferir a participação (`conversa_usuario`) antes de aceitar o `conversa_id`, como as rotas HTTP fazem com `validarAcessoConversa`. Até lá, o cliente ignora WS 63 de quem não é membro.

---


### S23 · Configuração do cliente Firebase por servidor 🆕 (2026-10-15, ADR 0002) — ⏸ depende da decisão
- **Contexto:** o app precisa funcionar também sem FCM; com FCM, o melhor caminho é um APK só que liga o Firebase **em tempo de execução** com o projeto do servidor (a conta de serviço já está nos `parametros`).
- **Pedido:** parâmetros públicos do cliente Android (por exemplo `fcm_android_app_id`, `fcm_api_key`, `fcm_sender_id`; o `fcm_project_id` já existe) expostos depois do login, por exemplo em `GET /recursos` (`{…, fcm: {projeto, app_id, chave, sender_id} | null}`).
- **Sem isso:** o app teria de levar um `google-services.json` fixo, de um projeto só, e não serviria a instalações com outro projeto (ou sem nenhum).

### S24 · UnifiedPush (push sem Google) 🆕 (2026-10-15, ADR 0002, opção B) — ⏸ só se for escolhida
- **Pedido:** registrar por dispositivo o endereço UnifiedPush (`PATCH /dispositivo {push_endpoint}`) e, nas mensagens e nas chamadas, fazer o POST HTTP nesse endereço (como o `enviarPush` faz no FCM); opcionalmente um ntfy próprio no `docker-compose`.
- **Observação:** o push de chamada (S1) vale para os dois canais.

### S25 · Confirmação de leitura com lista: arestas 🆕 (2026-10-15, `6d20ff7`)

- **Hoje** (`mensagens.ts`, `pedirConfirmacao`, `confirmacaoDaMensagem`; doc 01 §10.14):
  1. **WS 65 de agendada antes de sair:** `POST /mensagem/pedir-confirmacao` aceita mensagem agendada (o prazo conta de `visivel_em`) e manda o WS 65 a **todos** os membros na hora, com a lista de nomes, embora os outros ainda não vejam a mensagem. Os outros avisos de agendada (WS 2, WS 3, WS 61) esperam a mensagem sair.
  2. **`pode_confirmar` em mensagem oculta:** o objeto `confirmacao` continua sendo montado para mensagem com `excluida_em`, e `pode_confirmar` vem `true` para quem está na lista e não confirmou; mas `POST /mensagem/confirmar` responde 400 `Esta mensagem não pede confirmação de leitura.`
  3. **Quem sai da conversa fica na lista:** `mensagem_confirmacao_destinatario` não é mexida quando alguém sai (`DELETE /conversa/usuario`). A pessoa continua em `destinatarios` e em `total`, mas não pode mais confirmar (403 `Acesso negado!`): o "x de y" daquela mensagem nunca fecha.
  4. 🆕 2026-10-15 (`ef13c22`) **Atividade tipo 5 de agendada antes de sair:** `pedirConfirmacao` cria a atividade "pediu confirmação" de cada destinatário com a data **de agora** (`atividadesConfirmacao(..., null)`), mesmo numa agendada que ainda não saiu (no envio com `pede_confirmacao`, ela nasce com `visivel_em` e só aparece depois). Como `GET /atividades` monta `texto` e `conteudo_tipo` com o conteúdo da mensagem, o destinatário **lê o texto da agendada antes da hora** na tela de atividades, e o contador sobe por uma mensagem que ele não acha no chat.
- **Efeito:** clientes mostram um botão que dá erro (item 2) e uma contagem que não se completa (item 3); o item 1 avisa sobre uma mensagem que o cliente ainda não tem (o Android ignora id desconhecido); 🆕 o item 4 vaza o texto da agendada e cria uma pendência que o destinatário não resolve até a mensagem sair.
- **Proposta:** (1) numa agendada, mandar o WS 65 só ao autor e deixar os outros receberem a `confirmacao` quando a mensagem sair; (2) `pode_confirmar = false` com `excluida_em`; (3) ao sair da conversa, tirar a pessoa das listas de mensagens ainda não confirmadas por ela (ou não contá-la em `total`); 🆕 (4) em `pedirConfirmacao`, criar a atividade com `criado_em = coalesce(visivel_em, agora)`, como já faz o envio.

### S26 · Atividades: `nova` virou `pendente` sem compatibilidade 🆕 (2026-10-15, `ef13c22`)

- **Hoje** (`atividades.ts`, `atividades`, `atividadesNovas`, `marcarAtividadesVistas`; doc 01 §12):
  1. `GET /atividades` deixou de mandar a chave `nova` e passou a mandar `pendente`, com outro sentido (pendência até concluir, não "nova desde a última visita").
  2. `POST /atividades/vistas` continua respondendo 200 e gravando `atividades_vistas_em`, mas **não tem mais efeito** no contador nem na lista.
  3. Reação (1) e chamada perdida (4) só deixam de ser pendentes por rotas novas (`/atividades/reacoes-vistas`, `/atividade/concluir`).
  4. `POST /atividade/concluir` responde `{sucesso: true}` mesmo para id inexistente ou de outra pessoa.
- **Efeito:** um cliente feito para o contrato anterior (o Android de hoje) **não quebra no parse**, mas: não mostra nenhuma atividade como nova (a chave sumiu); abrir a aba não zera mais o número; e reações e chamadas perdidas acumulam no contador para sempre. O usuário vê um número que não consegue zerar.
- **Proposta:** durante a transição, mandar `nova` junto com `pendente` (ex.: `nova = pendente`) e documentar a mudança num `CHANGELOG-API.md` (S12); avaliar se `POST /atividades/vistas` deve concluir reações e chamadas perdidas vistas (o comportamento antigo do "abrir a aba"); responder 404 em `/atividade/concluir` com id alheio, para o cliente perceber o erro.

### S27 · Bots no status de entrega, na confirmação e nas chamadas 🆕 (2026-10-15, `ef13c22`)

- **Hoje** (`bots.ts`, `responder`, `botsChamados`; `mensagens.ts`, `destinatariosConfirmacao`):
  1. **Status de entrega:** o bot é membro e ganha linha em `mensagem_status` como qualquer um, mas nunca chama `GET /mensagens`: **nunca marca `recebida`**, e só marca `visualizada` a mensagem que o chamou. Para o remetente, `recebida`/`visualizada` é o agregado de todos os destinatários (doc 01 §10.2), então **em grupo com bot nenhuma mensagem chega a "recebida por todos"**; na direta com o bot, a mensagem fica "vista" sem ter sido "recebida". `GET /mensagem/status/detalhe` mostra o bot sem datas.
  2. **Confirmação de leitura:** sem `confirmacao_usuarios`, a lista é de todos os outros membros, **inclusive bots**. O bot ganha a atividade tipo 5 e nunca confirma: o "x de y" não fecha.
  3. **Chamadas:** dá para ligar para um bot ou convidá-lo. Ele nunca atende: a chamada toca até o cliente desistir e o bot ganha uma "chamada perdida" que ninguém vê.
  4. **Marcação:** só `GET /usuario/contatos` diz quem é bot (`bot: true`). `GET /conversas`, `GET /conversa/usuarios`, `MensagemResposta` e as atividades não dizem; o cliente cruza ids.
  5. **Excluir bot** tira o bot dos grupos sem WS 40: os outros membros só veem a saída ao recarregar.
- **Efeito:** tiques de entrega errados em todo grupo com bot; confirmações que não fecham; ligação que não serve para nada.
- **No app (2026-10-16, FC-221):** os itens 2 e 3 estão contornados: a confirmação vai com a lista explícita sem os bots, e o app não oferece ligar para bot nem convidá-lo. O item 1 (tiques) depende do servidor.
- 🆕 2026-10-16 (`ac710eb`):
  - item 4: `GET /permissoes` (administração) passou a trazer `bot` em `usuarios[]`; no chat, continua só `GET /usuario/contatos`;
  - item 5: criar, renomear e excluir bot agora gera **WS 66** (contatos), mas a saída dos grupos continua sem WS 40;
  - **localização:** nenhum bot tem a permissão `localizacao` (a migração 055 deixou todos de fora e o bot novo nasce com `emoji`, `responder` e `reagir`), e não há ferramenta para enviar uma. O bot lê a localização no histórico (`[localização: …]`/`[trajeto: …]`), mas não consegue mandar uma de volta. Se fizer falta, uma ferramenta `enviar_localizacao` seguiria a mesma regra das outras (só com a permissão).
- **Proposta:** (1) o bot marcar `recebida` (e `visualizada`) de toda mensagem da conversa ao responder, ou o agregado ignorar bots; (2) deixar bots fora da lista padrão de confirmação (e recusar bot em `confirmacao_usuarios`); (3) recusar bot em `PUT /chamada/iniciar` e `PUT /chamada/usuario` (400); (4) mandar `bot` (ou `destinatario_bot`) em `/conversas` e `/conversa/usuarios`; (5) WS 40 ao tirar o bot dos grupos.

### S28 · Permissões de uso: arestas 🆕 (2026-10-15, `ef13c22`)

- **Hoje** (`mensagens.ts`, `permissoesMensagem`; `usuarios.ts`, `alterarUsuario`; `permissoes.ts`):
  1. **Encaminhar dribla as outras permissões:** com `mensagem_referencia.tipo = 2`, o servidor só confere `encaminhar` e volta. Mas os conteúdos vêm **do cliente**, não da mensagem original, e a referência não é conferida (S4, item 3). Quem não tem `imagem`, `arquivo`, `agendar`, `postagem` ou `confirmacao` consegue tudo isso marcando a mensagem como "encaminhada" de qualquer id.
  2. **`emoji` pega mais que emoji:** a checagem é `\p{Extended_Pictographic}`, que inclui `©`, `®`, `™` e algumas setas. E deixa de fora o título da postagem, a pergunta e as opções da votação e o emoji do grupo.
  3. **Desfazer pede a permissão:** tirar a própria reação pede `reagir`; ocultar a própria mensagem (e apagar a própria agendada) pede `ocultar`.
  4. **`PATCH /usuario` confere pela chave enviada**, não pela mudança: um cliente que manda nome e e-mail juntos precisa das duas, mesmo mudando só um.
  5. **Não conferidas no servidor:** `compartilhar_tela`, `cores` e `config_chamadas` (só o cliente esconde). Para `compartilhar_tela` isso permite compartilhar a tela mesmo sem a permissão.
  6. **Vídeo pela extensão:** `ogg` conta como vídeo (um áudio `.ogg` enviado como arquivo pede `video`); anexo desconhecido conta como `arquivo`.
  7. **Sem aviso:** não há evento quando um administrador muda as permissões de alguém; o cliente só descobre no 403 ou ao reler `GET /usuario/permissoes`.
  8. 🆕 2026-10-16 (`ac710eb`) **A nova `localizacao` também é driblada pelo encaminhamento** (item 1): uma mensagem marcada como encaminhada com conteúdo tipo 9 só pede `encaminhar`. O servidor ainda confere o **formato** da localização (400), mas não a permissão. E a ordem é "formato antes da permissão": quem não tem `localizacao` e manda um JSON errado leva 400, não 403.
  9. 🆕 2026-10-16 (`ac710eb`) **Pastas não têm permissão de uso:** qualquer pessoa cria e compartilha pastas na conversa direta; o administrador não tem como desligar.
- **Efeito:** o item 1 anula as permissões de conteúdo para quem conhece a API; os itens 2–4 dão 403 em situações que o usuário não entende; o item 7 deixa botões aparecendo até o 403.
- **Proposta:** (1) no encaminhamento, copiar os conteúdos da mensagem original no servidor (e conferir o acesso a ela) em vez de aceitar os do cliente, e conferir `agendar`/`postagem`/`confirmacao` também no encaminhamento; (2) usar uma checagem de emoji de verdade (`\p{Emoji_Presentation}` ou `\p{Extended_Pictographic}` sem os símbolos de texto) e conferir também título, votação e emoji do grupo; (3) liberar o "desfazer" sem a permissão; (4) conferir só os campos que mudam; (5) conferir `compartilhar_tela` no sinal `{acao:'tela'}` (WS 57); (6) tirar `ogg` da lista de vídeo; (7) um WS (por exemplo 61 ou um tipo novo) ao mudar as permissões da pessoa; 🆕 (8) a proposta (1) resolve também o tipo 9 (copiar o conteúdo da original); (9) se a organização quiser controlar, uma permissão de uso `pastas`.

### S29 · Ferramentas externas: o servidor chama qualquer endereço 🆕 (2026-10-15, `ef13c22`)

- **Hoje** (`ferramentas.ts`, `conferir`, `chamarFerramenta`): a URL só precisa ser http ou https. O servidor faz o pedido de dentro da rede do Docker, então alcança `http://postgres:5432`, `http://minio:9000`, `http://qdrant:6333` (sem autenticação), `http://api:8080` e `127.0.0.1` — e devolve até 8000 caracteres da resposta em `POST /ferramenta/testar`. Exige a permissão `ferramentas`, mas, no **modo aberto** (doc 01 §13.1), qualquer conta tem todas as permissões.
- **Efeito:** quem cadastra ferramentas lê e altera os serviços internos (o Qdrant, por exemplo, aceita apagar coleções por HTTP). Um bot com a ferramenta ligada também pode ser induzido a chamá-la.
- **Proposta:** recusar endereços internos e privados (nomes do compose, `localhost`, 10/8, 172.16/12, 192.168/16, 169.254/16) depois de resolver o DNS, ou ter uma lista de domínios permitidos nos parâmetros.

### S30 · Base de conhecimento: limite do documento e indexação sem aviso 🆕 (2026-10-15, `ef13c22`)

- **Hoje** (`conhecimento.ts`, `incluirDocumento`, `indexar`):
  1. O servidor aceita documento de até **2 milhões de caracteres**, mas o nginx corta o corpo JSON em **1 MB** (`client_max_body_size 1m` em `/api/`). Documentos acima de ~1 MB levam **413 do nginx** (HTML, não `{error}`).
  2. A indexação roda em segundo plano, numa fila só da memória, sem evento ao terminar (a tela relê a lista). Reiniciar a API no meio deixa o documento em "processando" para sempre, até "Reindexar tudo".
- **Efeito:** só administração (o celular não usa): mensagem de erro ruim para documentos grandes e documentos presos em "processando".
- **Proposta:** (1) aumentar o `client_max_body_size` só para `/api/conhecimento/documento`, ou baixar o limite do servidor para o que passa no nginx (com 400 em português); (2) ao subir a API, recolocar na fila os documentos com status 1; avisar o fim por WebSocket.

### S31 · Pastas: contagem, mensagens ocultas e pasta sem dono 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`pastas.ts`, `listarPastas`, `alterarPasta`, `mensagensDaPasta`; `mensagens.ts`, `excluirMensagem`; doc 01 §10.17):
  1. **Contagem sem filtro:** `mensagens` em `PastaResposta` é o `count` de `pasta_mensagem`, sem olhar `excluida_em` nem a visibilidade. Conta mensagens ocultas e, numa pasta compartilhada, a agendada que o dono pôs e que a outra pessoa ainda não vê (ela conta para a outra pessoa, mas não aparece no `GET /pasta/mensagens` dela).
  2. **Mensagem oculta fica na pasta:** `DELETE /mensagem` só marca `excluida_em`; a mensagem continua nas pastas e em `GET /pasta/mensagens`, com o conteúdo original (como no chat). `PUT /pasta/mensagens` também aceita pôr uma mensagem oculta.
  3. **Sem aviso:** ocultar a mensagem e apagar uma agendada (que sai das pastas pelo cascade) não geram WS 67; a contagem muda sem evento.
  4. **Quem sai da conversa:** as pastas da pessoa continuam. Uma pasta compartilhada dela fica visível e editável para quem ficou, mas ninguém mais consegue renomeá-la nem excluí-la (o dono leva 403 por não ser mais membro).
- **Efeito:** o número da pasta não bate com o que a tela mostra; a pasta guarda "Mensagem oculta" sem o usuário saber; uma pasta compartilhada pode ficar sem ninguém que a apague.
- **Proposta:** (1) contar só as mensagens que o usuário vê (`excluida_em is null` e a regra de `visivel_em`), ou devolver as duas contagens; (2) decidir se a oculta sai das pastas ao ocultar (ou se a lista da pasta a esconde); (3) WS 67 a quem vê a pasta quando uma mensagem dela é ocultada ou apagada; (4) ao sair da conversa, apagar as pastas da pessoa (ou passar a compartilhada para a outra).

### S32 · Pastas: WS 67 sem o que mudou e com ids não conferidos 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`pastas.ts:69-78`, `avisar`; `incluirMensagensPasta`, `tirarMensagensPasta`):
  1. Ao criar, alterar e excluir, o evento é só `{conversa_id, pasta_id}`: o cliente não sabe se a pasta nasceu, mudou ou sumiu.
  2. Em `acao: "tirar"`, `mensagens` é o array **do corpo**, como veio (com repetidos e com ids que nunca estiveram na pasta, até de outra conversa); em `"incluir"`, todos os ids válidos, inclusive os que já estavam. O evento sai mesmo com `incluidas`/`tiradas` = 0.
  3. Vai também a quem fez a ação (todas as conexões dele).
- **Efeito:** um cliente que aplica o delta pode marcar ou desmarcar bolhas erradas; o seguro é recarregar `GET /pastas` (e as mensagens visíveis) a cada evento, o que custa mais requisições.
- **Proposta:** mandar `acao: "criada" | "alterada" | "excluida"` nos três casos (ou a própria `PastaResposta`); em `incluir`/`tirar`, só os ids que de fato entraram ou saíram (o `returning` já existe no código) e nenhum evento quando nada mudou.

### S33 · `DELETE /pasta/mensagens` com corpo JSON 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`rotas.ts:223`, esquema `mensagensPasta`): tirar mensagens da pasta é um `DELETE` com corpo `{pasta_id, mensagens}`. É a única rota da API com corpo em `DELETE`; as outras exclusões usam `?id=`.
- **Efeito:** o HTTP não define sentido para corpo em `DELETE`, e há bibliotecas, proxies e caches que descartam o corpo ou recusam a requisição. No Android, o Retrofit não aceita `@DELETE` com `@Body` (é preciso `@HTTP(method = "DELETE", hasBody = true)`); o OkHttp aceita. Se um proxy no caminho tirar o corpo, o servidor responde 400 de validação.
- **Proposta:** trocar por `POST /pasta/mensagens/tirar` (mesmo corpo) ou `DELETE /pasta/mensagens?pasta_id=&mensagens=1,2,3`, mantendo a rota atual por um tempo.

### S34 · WS 66 sai antes de a conta estar gravada 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`usuarios.ts:111-123`, `criarConta`; `:129-158`, `cadastrarUsuario`; `bots.ts:122-147`, `incluirBot`; `websocket.ts:219-224`):
  1. `criarConta` manda o WS 66 logo depois do `insert`. No `PUT /usuario/novo` de pessoa, isso acontece **dentro da transação**: o aviso sai antes do commit, do avatar e da lista de permissões. No bot, sai depois da transação dele, mas antes do avatar e da lista.
  2. O evento vai a todas as conexões de todos os usuários e não traz nada (nem o id).
  3. Não sai quando muda só o avatar ou o telefone, que também aparecem nos contatos.
- **Efeito:** quem recarrega `GET /usuario/contatos` assim que recebe o 66 pode não ver a conta nova (o commit ainda não aconteceu) ou vê-la sem avatar; como não vem outro aviso, a lista fica desatualizada até a próxima mudança ou recarga. Com muitas conexões, cada mudança faz todo mundo recarregar a lista inteira.
- **Proposta:** mandar o 66 depois do commit e de todas as gravações (no fim de `cadastrarUsuario`, `incluirUsuario` e das rotas de bot); incluir `usuario_id` e o que aconteceu (`criado`, `alterado`, `excluido`); avisar também troca de avatar e de telefone.

### S35 · Migração 054: `usuarios` virou `permissoes` sem aviso 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`migracoes/054.sql`, `permissoes.ts`):
  1. Quem tinha `usuarios` ("Cadastrar usuários e bots") ganhou `permissoes`, que também concede e retira **qualquer** permissão e cuida de **todos** os bots. É uma ampliação de acesso feita pela migração, sem ninguém escolher.
  2. **Modo aberto:** ele dura enquanto ninguém tem `permissoes`. Se uma instalação estava assim e alguém tinha recebido `usuarios`, a migração dá `permissoes` a essa pessoa e o modo aberto **acaba** ao subir a versão nova: as outras contas perdem, sem aviso, o que tinham só pelo modo aberto (as permissões de administração e as de uso que não estavam gravadas).
  3. O código `usuarios` sumiu das listas: `PUT`/`DELETE /permissao/usuario` com ele dão 404, e um cliente com o código fixo (o Android tem `CodigoPermissao.USUARIOS`) fica com código morto. Não há versão nem changelog (S12).
- **Efeito:** um administrador pode descobrir só depois que alguém passou a mexer nas permissões de todos; "cadastrar pessoas" e "mudar acessos" deixaram de ser coisas separadas.
- **Proposta:** registrar a mudança num `CHANGELOG-API.md` e nas notas da versão, com o aviso de revisar quem tem `permissoes` depois de atualizar; se a separação fizer falta, voltar a ter uma permissão só de cadastro.

### S36 · Localização: mapa, endereço e rota dependem de serviços públicos 🆕 (2026-10-16, `ac710eb`)

- **Hoje:** o servidor só guarda e confere o JSON da localização (doc 01 §10.16). O mapa, a busca de endereço, o nome do lugar e a rota vêm de serviços públicos chamados **direto pelo cliente**: no web, `tile.openstreetmap.org`, `nominatim.openstreetmap.org` e `router.project-osrm.org` (`conversa-web/src/services/mapas.ts`). Não há parâmetro para trocar esses endereços nem proxy, e `GET /recursos` não diz nada sobre mapas.
- **Efeito:**
  - **Privacidade:** as coordenadas, o que a pessoa digita na busca e o IP do aparelho vão para terceiros, fora do controle da instalação. Pode não servir a uma empresa que hospeda o próprio servidor.
  - **Regras de uso:** os servidores públicos do OpenStreetMap limitam o uso (o Nominatim pede no máximo 1 requisição por segundo e a identificação do aplicativo; os tiles pedem `User-Agent` identificável e proíbem uso pesado), e o servidor de demonstração do OSRM não tem garantia de funcionamento. Com muitas pessoas, o app pode ser bloqueado, e o Android teria de mandar os cabeçalhos certos.
  - **Rede fechada:** numa instalação sem internet, a localização fica sem mapa e sem busca de endereço.
- **Proposta:** parâmetros no servidor com os endereços de tiles, geocodificador e roteador (padrão: os públicos), expostos em `GET /recursos`; opcionalmente, um proxy com cache no servidor (por exemplo `/api/mapa/busca` e `/api/mapa/rota`), que também cuide do `User-Agent` e do limite de taxa.

### S37 · Localização: validação recusa `null` e não diz o campo 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`mensagens.ts:241-261`, `localizacaoValida`):
  1. Os campos opcionais (`nome`, `rota`, `distancia`, `duracao`) aceitam "ausente", mas **não `null`**: um cliente que serializa os nulos leva 400.
  2. Qualquer desvio dá a mesma mensagem, `Localização inválida!`, sem dizer o campo.
  3. A `rota` não é conferida (qualquer string, até os 200.000 caracteres do conteúdo); `distancia` e `duracao` são aceitas com um ponto só; chaves a mais são guardadas e repassadas a todos.
- **Efeito:** erro difícil de diagnosticar no cliente; dados sem sentido (rota que não decodifica, distância num ponto só) chegam aos outros clientes, que precisam se defender.
- **Proposta:** tratar `null` como ausente; dizer o campo na mensagem (ex.: `Localização inválida: a rota só vale com dois pontos.`); conferir que a `rota` é uma polyline válida (só os caracteres do formato, tamanho razoável); recusar ou descartar chaves desconhecidas.

### S38 · Espaço usado: conta uploads não concluídos e recalcula tudo a cada chamada 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`anexos.ts:33-58`, `espacoPorUsuario`; `permissoes.ts`, `listarPermissoes`):
  1. A soma junta os conteúdos de anexo com a tabela `anexo` sem olhar `upload_status`: anexo pendente ou que falhou (o arquivo não está no MinIO) conta como espaço usado.
  2. Em `GET /permissoes` (sem filtro de usuário), a consulta percorre **todos** os conteúdos de anexo de todas as mensagens a cada chamada.
  3. Cada pessoa só consulta o próprio (`GET /usuario/espaco`); o dos outros sai só em `GET /permissoes`, junto da lista inteira.
- **Efeito:** um número um pouco maior que o real quando há uploads que falharam; a tela "Pessoas" fica mais lenta conforme o histórico cresce.
- **Proposta:** filtrar `upload_status = 1`; guardar o total por usuário (atualizado ao confirmar o upload) ou calcular sob demanda, por pessoa.

### S39 · Testes de IA e de embeddings mandam o token salvo ao endereço digitado 🆕 (2026-10-16, `ac710eb`)

- **Hoje** (`parametros.ts:117-132`, `testarEmbeddings`; `:134-148`, `testarIa`): com o token em branco, o teste usa o token **salvo** (o `ia_embeddings_token` se veio `url`, senão o `ia_token`) e o manda para o endereço que veio **no corpo**, mesmo que seja outro host. O `testarIa` (`b8ea0c2`) já fazia o mesmo com o `ia_token`.
- **Efeito:** os tokens nunca saem pela API (`GET /parametros` só diz se estão preenchidos), mas quem tem a permissão `parametros` — ou qualquer conta no modo aberto — consegue lê-los apontando o teste para um servidor seu. Contraria a própria regra do commit de que o token da IA não vai para outro host.
- **Proposta:** só usar o token salvo quando o endereço do teste for igual ao salvo; com outro endereço, exigir o token digitado.

## Prioridade sugerida no servidor

| Ordem | Itens | Por quê |
|---|---|---|
| 1 | S1, S2, 🆕 S18 | Sem eles, o Android não toca nem notifica de forma confiável |
| 2 | S4, 🆕 S22, 🆕 `ef13c22` S29, S28 (item 1; 🆕 `ac710eb` e item 8), 🆕 `ac710eb` S39, S36 | Segurança (ouvir chamadas e baixar anexos alheios; presença em conversa alheia; 🆕 servidor chamando a rede interna; encaminhamento driblando as permissões; 🆕 token salvo indo a endereço digitado; coordenadas indo a serviços de terceiros) |
| 3 | S3, S13, 🆕 S17, 🆕 `ef13c22` S26, 🆕 `ac710eb` S35 | Qualidade das notificações, do contador de não lidas (🆕 e do de atividades) e da sessão no celular; 🆕 mudança de acesso feita pela migração, sem aviso |
| 4 | S7, S8, S9, S11, 🆕 S19, S20 (🆕 2026-10-15: só o caso do chat de outra ligação), 🆕 `ef13c22` S27, S28 (itens 2–7 e 🆕 9), 🆕 `ac710eb` S31, S32, S33, S34, S37 | Robustez para todos os clientes |
| 5 | S5, S6, S10, S12, S14, S15, S16, 🆕 S21, 🆕 S25, 🆕 `ef13c22` S30, 🆕 `ac710eb` S38 | Higiene e evolução |
