# 02 — Mudanças no servidor desde abril/2026 e impacto no cliente Android

Auditoria feita em 2026-10-06, só leitura (nenhum código foi alterado).

## 0. Referências e método

| Item | Valor |
|---|---|
| Repositório do servidor | `C:\Users\danie\Desktop\GIT\conversa-projeto\conversa` |
| HEAD do servidor em 2026-04-26 (`git rev-list -1 --before=2026-04-27 HEAD`) | **`56e7cc0`** (2026-04-19, "Adiciona agendamento de mensagens"). Nenhum commit em nenhum branch entre 2026-04-19 e 2026-09-12. |
| HEAD atual do servidor | **`7f670c3`** (2026-10-06, "Permissões do sistema e parâmetros alterados pela API") |
| Commits entre os dois | 22 (`aa61b78` … `7f670c3`) |
| Cliente Android | `conversa-android-kotlin`, HEAD `e3e2a65` (2026-04-26). **A árvore de trabalho tem alterações não commitadas** (MainActivity, SocketManager, ChamadaService, ChatActivity, SocketService, MensagemExtras etc.). As citações `arquivo:linha` do Android referem-se à árvore de trabalho atual. |
| Fontes do servidor em abril (Delphi/Horse) | `conversa.rest.dpr` (rotas), `src/conversa/conversa.api.pas` (handlers), `src/conversa/WebSocket.pas`, `src/conversa/FCMNotification.pas`, `src/conversa/conversa.comum.pas`, `src/conversa/conversa.autorizacao.pas`, `bin/nginx/conf/nginx.conf`, `bin/mediamtx/mediamtx.yml` — todas lidas com `git show 56e7cc0:<caminho>`. |
| Fontes do servidor hoje (Bun/Elysia) | `src/rotas.ts`, `src/esquemas.ts`, `src/app.ts`, `src/autenticacao.ts`, `src/websocket.ts`, `src/mensagens.ts`, `src/conversas.ts`, `src/chamadas.ts`, `src/anexos.ts`, `src/minio.ts`, `src/usuarios.ts`, `src/notificacoes.ts`, `src/fcm.ts`, `src/atividades.ts`, `src/sip.ts`, `bin/nginx/nginx.conf`, `bin/mediamtx/mediamtx.docker.yml`, `bin/coturn/turnserver.conf`, `docker-compose.yml`, `migracoes/*.sql`. |

Convenções deste documento:

- `dpr@56e7cc0:N` = linha N de `conversa.rest.dpr` no commit `56e7cc0`; `api.pas@56e7cc0:N` = `src/conversa/conversa.api.pas` no mesmo commit; `ws.pas@56e7cc0:N` = `src/conversa/WebSocket.pas`.
- Sem prefixo de commit = HEAD atual do servidor (`7f670c3`) ou árvore de trabalho do Android.
- Caminhos Android são relativos a `app/src/main/java/com/conversa/conversa/`.
- **"Já quebrava em abril"** = o Android já era incompatível com o servidor de 56e7cc0; a quebra não foi introduzida pela reescrita, mas continua.

Achado transversal importante para priorizar: **vários repositórios do Android nunca são instanciados**. `MensagemRepository`, `UsuarioRepository`, `SipRepository` e `ContatosRepository` não têm nenhum `XxxRepository(` fora do próprio arquivo (busca `grep -rn "MensagemRepository\|UsuarioRepository\|SipRepository\|ContatosRepository"`). `DispositivoRepository` só é instanciado em `service/ConversaFcmService.kt:36`. Por isso, na tabela da seção 2 há uma coluna "chamado em runtime?": endpoints só usados por esses repositórios são código morto hoje — a quebra é latente.

---

## 1. Linha do tempo dos commits do servidor desde 2026-04-26

> Atualizado em 2026-10-06 (noite) com o commit `8031fa5` (linha 23) e em 2026-10-08 com `d4435db` e `5cad911` (linhas 24 e 25), marcados com 🆕.

Base: **`56e7cc0`** (2026-04-19) — Delphi + Horse na porta 8080, WebSocket próprio (Bird Socket) na porta **9090** (`dpr@56e7cc0:571`), nginx nativo no Windows escutando **4430** em DEV (`bin/nginx/conf/nginx.conf@56e7cc0:28`) e repassando `/ws/` → 9090, `/webrtc/` → MediaMTX nativo em 127.0.0.1:8889, `/storage/` → MinIO. Push FCM com bloco `notification` (sem `data`).

| # | Commit | Data | Título | O que mudou para clientes |
|---|---|---|---|---|
| 1 | `aa61b78` | 2026-09-12 | Ambiente | Ainda Delphi. **Novo `GET /api/ice`** (`TConversa.IceServers`) devolvendo `{iceServers:[{urls,username,credential}], iceTransportPolicy:'relay'|'all'}` com credencial TURN HMAC-SHA1 (formato `use-auth-secret` do coturn); parâmetros `turn_url`, `turn_secret`, `turn_forcar_relay` (padrão `'1'`, migração 23). Entra coturn (`bin/coturn/turnserver.conf`) e MediaMTX em Docker (`bin/mediamtx/mediamtx.docker.yml`), `docker-compose.yml`, nginx para Docker. Cria o bucket do MinIO na partida. |
| 2 | `97f9341` | 2026-09-13 | Converte a API para Node e padroniza o ambiente em Docker | **Reescrita total** em TypeScript (Fastify + postgres.js). Mesmo conjunto de rotas (`/api/...`) e mesmos números de evento WS. **WebSocket passa a ser servido pela própria API em `/ws/` (porta 8080)**, não mais 9090; nginx em Docker repassa `/ws/` para a API. **nginx de DEV passa a escutar 443** (antes 4430). Validação de esquema Fastify (400 antes do handler). `mensagemreferencia` limitado a 0..1000 (corrigido em `cb7ef68`). `PUT /conversa/usuario` de membro existente devolve o registro em vez de erro de chave duplicada. Segredos (pepper, MinIO, TURN) gerados no volume `conversa-dados`; `turn_url`/`turn_secret`/`s3_endpoint` saem dos parâmetros (migrações 024–026): URLs de anexos e TURN passam a ser montadas a partir do `Host`/`X-Forwarded-Proto` da requisição. MediaMTX/nginx nativos de Windows removidos. FCM ainda com `notification:{title,body}`. |
| 3 | `6c1ee03` | 2026-09-15 | Altera porta padrão do postgres | Só infraestrutura (Postgres publicado em 5433). Sem efeito no contrato. |
| 4 | `2299a5c` | 2026-09-17 | Corrige notificações e mensagem de chamada; adiciona transcrição de áudio | `GET /mensagens` ganha `conteudos[].transcricao_status` e `conteudos[].transcricao`. Novos `GET` e `PUT /api/anexo/transcricao`. `GET /mensagens/novas` ignora mensagens já visualizadas e trunca `ate` em milissegundos. Mensagem-resumo de chamada (conteúdo tipo 6) já nasce lida para quem participou, e o evento WS 3 dela vai para **todos** os membros (inclusive quem ligou). Migração 027. |
| 5 | `6fd4733` | 2026-09-22 | Ajusta notificações push | **Push FCM passa a ser só `data`** = `{titulo, mensagem, conversa}` + `webpush.headers.Urgency=high`; o bloco `notification` some. Texto do push/WS com menção `@[Nome](id)`→`@Nome` e blocos de código resumidos; espaços deixam de virar `" | "`. |
| 6 | `5159c5b` | 2026-09-23 | Adiciona script para subir o desenvolvimento | Só script. |
| 7 | `cb7ef68` | 2026-09-25 | Corrige pesquisa e contexto de mensagens | `mensagemreferencia` aceita ids > 1000 (no Node entre `97f9341` e aqui, id > 1000 dava 400). Pesquisa com `unaccent` + `ilike` (sem diferenciar maiúsculas/acentos). **Novo `GET /api/mensagem/status/detalhe?id=`**. Migração 028. |
| 8 | `95430ba` | 2026-09-26 | Migra a API para Bun e Elysia | Fastify→**Elysia** (validação TypeBox; erro `{error}`; mensagens de validação em PT). OpenAPI em `/api/docs` (JSON em `/api/docs/json`). Datas do histórico de chamadas (`GET /chamadas`) passam de texto sem fuso (`to_char`) para ISO com `Z`; `reagido_em` das reações com fuso. **Senha atual errada em `/alterar-senha` → 400** (antes 401). **Novos `PATCH /conversa/fixadas` e `PATCH /conversa/arquivada`** (migração 029); `GET /conversas` ganha `fixada_ordem` e `arquivada_em`; conversa arquivada não gera push. **`POST /chamada/entrar` e `/chamada/recusar` passam a notificar também o próprio usuário** (eventos 54/53 com `usuario_id` = ele mesmo) para as outras abas/aparelhos pararem de tocar. |
| 9 | `abfd3d5` | 2026-09-27 | Sobe a API de novo quando ela cai no desenvolvimento | Só script de desenvolvimento. |
| 10 | `f1ffb14` | 2026-09-28 | Isola anexos abertos direto pelo endereço de /storage/ | nginx manda `Content-Security-Policy: sandbox` em `/storage/` (exceto PDF). Não afeta download por app nativo. |
| 11 | `09c2c8d` | 2026-09-28 | Ajusta a transcrição para a versão Windows do transcritor-api | Interno (não manda `num_speakers`). |
| 12 | `0b67aca` | 2026-09-29 | Adiciona testes unitários | `GET /chamada/dados` e `POST /chamada/video` validam participação: chamada inexistente/alheia → **404** (antes erro genérico 500). `digitando`/`gravando` passam a exigir participação na conversa (**403**). |
| 13 | `8e81360` | 2026-09-30 | Corrige acesso a dispositivos, exclusão de conta e erros 500 da API | `PATCH /dispositivo` só altera dispositivo do próprio usuário (**403**). Login só reaproveita `dispositivo_id` que pertence à pessoa (senão cria outro). `DELETE /usuario` remove dispositivos; conta com histórico → **409**. Anexo inexistente → **404**; arquivo > 1 GiB → **400**; pesquisa vazia → **400** (antes 500). |
| 14 | `5272c9f` | 2026-09-30 | Confere o dono ao vincular dispositivo; acelera os testes | `PUT /dispositivo/usuario` só vincula dispositivo do próprio usuário (**403**). |
| 15 | `4bbfb0a` | 2026-10-02 | Grava o áudio e o vídeo das chamadas para auditoria | MediaMTX grava os caminhos `call-*` em fMP4 (`mediamtx.docker.yml:32-37`). **Vídeo só é gravado em H264 ou VP9; VP8 não é gravado** (`mediamtx.docker.yml:31`). Parâmetro `gravacao_dias` (migração 030). |
| 16 | `b94b504` | 2026-10-02 | Excluir marca a mensagem em vez de apagar | **`DELETE /mensagem` vira exclusão lógica**: grava `excluida_em`/`excluida_por` (migração 031); conteúdo continua guardado e a mensagem continua em `GET /mensagens` com `excluida_em` preenchido; só o autor exclui, sem prazo (antes: 409 se alguém já tinha recebido); agendada não enviada é apagada de vez. A exclusão é avisada pelo evento WS 3 e aparece em `GET /mensagem/status` (`excluida_em`). Pesquisa ignora excluídas; prévia e citações não mostram o texto. |
| 17 | `315464b` | 2026-10-03 | Sinal da chamada, chat da chamada e figurinhas | **Novo evento WS 57 (`SinalChamada`)**, bidirecional, só entre participantes da chamada. **Novo `PUT /api/chamada/chat`** (cria grupo da chamada, migração 032; `GET /chamada/dados` ganha `conversa_chat_id`). **Conteúdo tipo 7 = figurinha** (`conteudo` = `pacote/nome`, regex `^[a-z0-9-]{1,40}\/[a-z0-9-]{1,40}$`, outro formato → 400). |
| 18 | `a82d373` | 2026-10-03 | Pasta das gravações nasce com o dono certo, sem container auxiliar | Só infraestrutura. |
| 19 | `184904b` | 2026-10-05 | Dev Container instala as dependências ao conectar | Só ambiente. |
| 20 | `f095bf1` | 2026-10-05 | Prévia da conversa diz "Mensagem oculta" | `GET /conversas`: `ultima_mensagem_texto` = `"Mensagem oculta"` quando a última mensagem foi excluída (`conversas.ts:127`). |
| 21 | `8283616` | 2026-10-05 | Atividades: reações, respostas, menções e chamadas perdidas | **Novos `GET /atividades`, `GET /atividades/novas`, `POST /atividades/vistas`**; **novo evento WS 61 (`NovaAtividade`)**; `POST /chamada/recusar` aceita `nao_atendeu: boolean` (evento de chamada 8). Migração 033. |
| 22 | `7f670c3` | 2026-10-06 | Permissões do sistema e parâmetros alterados pela API | **Novos `GET /usuario/permissoes`, `GET /permissoes`, `PUT`/`DELETE /permissao/usuario`, `GET`/`PATCH /parametros`** (403 sem permissão). Migração 034. Nenhuma rota usada pelo Android passou a exigir permissão (só `parametros.ts:12,58` e `permissoes.ts:57,82,98` chamam `validarPermissao`). |
| 23 🆕 | `8031fa5` | 2026-10-06 (noite) | Votação em grupo: enquete com escolha única ou múltipla | **Novos `PUT /enquete`, `GET /enquete?id=`, `POST /enquete/votar`**; **novo evento WS 62 (`EnqueteAtualizada`)** `{enquete_id, conversa_id}`; **conteúdo tipo 8** = id da enquete, que só o servidor grava (`PUT /mensagem` com tipo 8 → 400, então enquete não pode ser encaminhada); prévia/push `enquete`. Migração 035. Não altera nenhuma rota que o Android antigo usa; o app antigo mostra a bolha vazia (tipo desconhecido) e ignora o WS 62. Detalhes: doc 01 §10.13 |
| 24 🆕 | `d4435db` | 2026-10-07 (noite) | Reações: no máximo 5 emojis diferentes por pessoa na mesma mensagem | `PUT /mensagem/reacao` recusa o 6º emoji diferente da mesma pessoa na mesma mensagem: 400 `Você já reagiu com 5 emojis nesta mensagem.` Tirar continua livre. O app antigo não reage (não afeta). Detalhes: doc 01 §10.12 |
| 25 🆕 | `5cad911` | 2026-10-07 (noite) | Votação com data final e encerramento antes do prazo | **Novos `POST /enquete/encerrar` e `PATCH /enquete`**; `PUT /enquete` aceita `encerra_em`; o `Enquete` ganha `encerra_em`, `encerrada_em`, `encerrada`, `pode_encerrar`, `pode_alterar_prazo`; votar encerrada → 400; WS 62 também ao encerrar e ao mudar a data. Migração 036. O app antigo não tem votação (não afeta). Detalhes: doc 01 §10.13 |

---

## 2. Endpoints chamados pelo Android (todos os métodos de `data/api/ConversaApi.kt`)

### 2.1 Regras gerais que mudaram (valem para todas as rotas)

| Aspecto | Abril (`56e7cc0`, Delphi/Horse) | Hoje (`7f670c3`, Bun/Elysia) |
|---|---|---|
| Prefixo | `/api/...` | `/api/...` (`rotas.ts:28`) — igual |
| Autenticação | JWT HS256 via middleware Horse.JWT, rotas livres `/api/login` e `/api/cadastro` (`dpr@56e7cc0:81-84`; atenção: `PUT /api/usuario` **não** estava na lista livre) | `Authorization: Bearer <jwt>` (`autenticacao.ts:29`), validade 12 h (`autenticacao.ts:6`). Livres: `POST /login` e `PUT /usuario` (`rotas.ts:33-38`); o resto passa pelo `resolve` em `rotas.ts:42`. Sem token → 401 `{"error":"Token não informado"}`; inválido/expirado → 401 `{"error":"Token inválido ou expirado"}`. |
| Formato de erro | `{"error": "<mensagem>"}` (Horse.HandleException) | `{"error": "<mensagem>"}` (`app.ts:57-77`) — mesmo formato |
| Validação | Manual (`CamposObrigatorios` → 400 `Campo "x" é obrigatório e não foi informado!`); erros não previstos viravam 500 | Esquema TypeBox antes do handler (`esquemas.ts`) → **400** com `Campo "x" é obrigatório e não foi informado!` / `O valor de "x" deve ser do tipo integer!` / `Valor inválido em "x": ...` (`app.ts:25-39`). Corpo ilegível → 400 `Corpo da requisição inválido!` (`app.ts:70-72`). Campos extras no corpo **são ignorados** (não geram 400). |
| Rota inexistente | Resposta padrão do Horse | **404** `{"error":"Rota GET:/api/xxx não encontrada"}` (`app.ts:66-68`) |
| Erros que viraram código HTTP correto | Chamada/anexo não encontrado, arquivo grande, pesquisa vazia → `raise Exception` → **500** (`api.pas@56e7cc0:1930,1947,1974`; `1423`) | 404/400 (`chamadas.ts:80`, `anexos.ts:44,60,86`, `mensagens.ts:375`) |
| Datas | `DateToISO8601(...)` → `"2026-04-19T15:40:30.000Z"` (sessão PG em UTC, `Postgres.pas@56e7cc0:146`). Exceção: `GET /chamadas` com `to_char` sem fuso `"2026-04-19T15:40:30"` | `JSON.stringify(Date)` → `"2026-10-06T12:34:56.789Z"`; sessão PG em UTC (`banco.ts:18`), `timestamp` sem fuso lido como UTC (`banco.ts:26`). `GET /chamadas` agora também com `Z`. |
| Tamanho do corpo em `/api/` | `client_max_body_size 1024m` global (`nginx.conf@56e7cc0:12`) | **`client_max_body_size 1m`** (`bin/nginx/nginx.conf:24`); só `/storage/` aceita 1024m (`:128`) |
| Login com limite de taxa | Não havia | `location = /api/login` com `limit_req` 10/min, burst 5 (`bin/nginx/nginx.conf:74,97-100`) → excedente recebe 503 do nginx |
| Porta DEV do nginx | **4430** (`nginx.conf@56e7cc0:28`) | **443** (`bin/nginx/desenvolvimento/servidor.conf:2`); produção 80 atrás da borda |

### 2.2 Tabela por método

Legenda de status: **igual** = contrato idêntico ao de abril e compatível com o Android; **igual (já quebrava)** = servidor não mudou o contrato, mas o Android já era incompatível em abril; **mudou** = contrato diferente de abril; **removido** = rota não existe mais.

| # | Método Android (`ConversaApi.kt`) | Verbo e caminho do Android | Runtime? | Abril (rota) | Hoje (rota / esquema) | Status | Compatível hoje? |
|---|---|---|---|---|---|---|---|
| 1 | `login` (:11-12) | `POST login` body `LoginRequest` | sim (`LoginActivity.kt:105`) | `dpr:104` / `api.pas:96` | `rotas.ts:33`, `esquemas.ts:29`, `usuarios.ts:19-66` | mudou (só regra de `dispositivo_id`) | sim |
| 2 | `cadastrarUsuario` (:17-18) | `PUT usuario` | não (UsuarioRepository morto) | `dpr:156` / `api.pas:266` (exigia JWT!) | `rotas.ts:38` (pública), `esquemas.ts:36` | mudou (agora público) | sim |
| 3 | `atualizarUsuario` (:21-25) | `PATCH usuario` sem `id` | não | `dpr:164` / `api.pas:298` (UpdateJSON exige `id`) | `rotas.ts:56`, `esquemas.ts:37` (`id` obrigatório), `usuarios.ts:110-116` (id ≠ token → 403) | mudou (403 se id alheio) | **não** (400: falta `id`) — já quebrava |
| 4 | `alterarSenha` (:28-32) | `POST alterar-senha` `{senha_atual, senha_nova}` | não | `dpr:130` / `api.pas:190` exige `senha_atual`,`senha` | `rotas.ts:46`, `esquemas.ts:30` `{senha_atual, senha}`; senha atual errada → 400 | mudou (401→400) | **não** (400: falta `senha`) — já quebrava |
| 5 | `deletarUsuario` (:35-39) | `DELETE usuario?id=` | não | `dpr:172` / `api.pas:304` (apagava qualquer id) | `rotas.ts:58`, `usuarios.ts:118-131` | mudou | sim se id = próprio e sem histórico; senão 403/409 |
| 6 | `atualizarDispositivo` (:44-48) | `PATCH dispositivo` body sem `id` (`DispositivoRepository.kt:39-47,62`) | sim (`ConversaFcmService.kt:36-37`) | `dpr:139` / `api.pas:214` exige `id` | `rotas.ts:51`, `esquemas.ts:31-33` (`id` obrigatório), `usuarios.ts:76-90` (403 se dispositivo alheio) | mudou (403) | **não** (400: falta `id`) — já quebrava |
| 7 | `vincularDispositivo` (:51-55) | `PUT dispositivo/usuario?dispositivo_id=` | não | `dpr:147` | `rotas.ts:53`, `usuarios.ts:92-95` (403 se alheio) | mudou (403) | sim para dispositivo próprio |
| 8 | `adicionarContato` (:60-64) | `PUT usuario/contato?relacionamento_id=` | não | `dpr:181` | `rotas.ts:60` | igual | sim |
| 9 | `removerContato` (:67-71) | `DELETE usuario/contato?id=` | não | `dpr:190` | `rotas.ts:63` | igual | sim |
| 10 | `listarContatosOnline` (:74-77) | `GET contatos/online` | não | `dpr:207` | `rotas.ts:67`, `usuarios.ts:147-155` | igual | sim |
| 11 | `listarConversas` (:79-82) | `GET conversas` | sim | `dpr:240` / `api.pas:452` | `rotas.ts:77`, `conversas.ts:56-178` | mudou (campos novos, texto "Mensagem oculta"/"figurinha") | sim (campos extras ignorados) |
| 12 | `obterDadosConversa` (:87-91) | `GET conversa/dados?id=` | sim (`ChatActivity.kt:966`) | **não existia** | **não existe** | igual (já quebrava) | **não** (404) |
| 13 | `listarContatos` (:96-99) | `GET usuario/contatos` | sim | `dpr:199` | `rotas.ts:65`, `usuarios.ts:142-144` | igual | sim |
| 14 | `criarConversa` (:104-108) | `PUT conversa` `{descricao, tipo, inserida}` | sim | `dpr:215` / `api.pas:416` (gravava `inserida`) | `rotas.ts:71`, `esquemas.ts:43` (`inserida` ignorado) | mudou | sim |
| 15 | `adicionarUsuarioConversa` (:113-117) | `PUT conversa/usuario` | sim | `dpr:257` (duplicado → erro) | `rotas.ts:88`, `conversas.ts:222-234` (idempotente) | mudou (melhor) | sim |
| 16 | `removerUsuarioConversa` (:120-124) | `DELETE conversa/usuario?id=` | não | `dpr:265` | `rotas.ts:90` | igual | sim |
| 17 | `atualizarConversa` (:127-131) | `PATCH conversa` Map | não | `dpr:223` | `rotas.ts:73`, `esquemas.ts:44` `{id, descricao}` obrigatórios | igual | depende do Map |
| 18 | `deletarConversa` (:134-138) | `DELETE conversa?id=` | não | `dpr:231` | `rotas.ts:75` | igual | sim |
| 19 | `listarUsuariosConversa` (:141-145) | `GET conversa/usuarios?conversa=` → `List<Contato>` | não | `dpr:248` / `api.pas:591` | `rotas.ts:85`, `conversas.ts:203-220` | igual (já quebrava: resposta é `{id,usuario_id,nome,avatar_url}`) | forma incompatível |
| 20 | `broadcastDigitando` (:148-152) | `POST conversa/digitando` `{id}` | sim (`ChatActivity.kt:264`) | `dpr:274` | `rotas.ts:92`, `conversas.ts:237-240` (403 se não membro) | mudou (403) | sim |
| 21 | `broadcastGravando` (:155-159) | `POST conversa/gravando` `{id}` | não | `dpr:285` | `rotas.ts:97` | mudou (403) | sim |
| 22 | `obterMensagens` (:169-176) | `GET mensagens?conversa&mensagemreferencia&mensagensprevias&mensagensseguintes` | sim | `dpr:356` / `api.pas:1330,1456` | `rotas.ts:108`, `esquemas.ts:58-65`, `mensagens.ts:323-371,501-616` | **mudou** (`excluida_em`, tipos 7, transcrição, `alterada` null) | parse OK; **semântica de exclusão quebrada** |
| 23 | `enviarMensagem` (:181-185) | `PUT mensagem` | sim | `dpr:296` / `api.pas:735` | `rotas.ts:104`, `esquemas.ts:50-57`, `mensagens.ts:146-227` | mudou (validação tipo 7; resposta com `excluida_em`/`excluida_por`) | sim |
| 24 | `visualizarMensagem` (:190-195) | **`GET`** `mensagem/visualizar?conversa&mensagem` | sim (`ChatActivity.kt:673,1209`) | só **`POST`** com corpo (`dpr:372`) | só **`POST`** `{conversa, mensagem}` (`rotas.ts:111`, `esquemas.ts:66`) | igual (já quebrava) | **não** (404) |
| 25 | `reproduzirMensagem` (:198-202) | `POST mensagem/reproduzir` `{mensagem_id, conversa_id}` | não | `dpr:380` exige `conversa`,`mensagem` | `rotas.ts:114`, `esquemas.ts:66` | igual (já quebrava) | **não** (400) |
| 26 | `deletarMensagem` (:205-209) | `DELETE mensagem?id=` | não | `dpr:304` / `api.pas:941` (apagava; 409 se recebida) | `rotas.ts:106`, `mensagens.ts:288-321` (marca `excluida_em`) | **mudou** (exclusão lógica = "ocultar") | HTTP OK, semântica diferente |
| 27 | `enviarMensagemComReferencia` (:212-216) | `PUT mensagem` com `referencia:{tipo, origem_mensagem_id?, destino_mensagem_id}` | não | campo esperado `mensagem_referencia:{tipo, origem_mensagem_id}` (`api.pas:797`); `referencia` virava 500 | `esquemas.ts:55`; `referencia` é ignorado | mudou (500 → 200 sem referência) | **não** (referência perdida silenciosamente) |
| 28 | `obterMensagensNovas` (:219-223) | `GET mensagens/novas?desde=` | não | `dpr:404` / `api.pas:1891` | `rotas.ts:123`, `mensagens.ts:689-720` | mudou (filtra visualizadas; `ate` em ms) | sim |
| 29 | `statusMensagem` (:226-231) | `GET mensagem/status?conversa&mensagem` → `List<MensagemStatusResponse>` | não | `dpr:388` / `api.pas:1848` (booleans por mensagem) | `rotas.ts:117`, `mensagens.ts:631-663` (+`excluida_em`) | mudou (+`excluida_em`); forma já incompatível | forma incompatível |
| 30 | `reagirMensagem` (:234-238) | `PUT mensagem/reacao` → `ReacaoResponse{id,mensagem_id,usuario_id,emoji,acao}` | não | `dpr:563` / `api.pas:2870-2961` (resposta `{mensagem_id,emoji,acao}`) | `rotas.ts:130`, `mensagens.ts:722-755` (mesma resposta; 404/403) | mudou (403) | parcial (`id`,`usuario_id` = 0) |
| 31 | `pesquisar` (:241-247) | `GET pesquisar?texto&conversa&usuario` → `List<PesquisaResultado>` | não | `dpr:525-537` (**`usuario` obrigatório**, `:529`) | `rotas.ts:127`, `esquemas.ts:69`, `mensagens.ts:373-399` (`usuario` ignorado) | mudou | forma incompatível (retorna mensagens) |
| 32 | `downloadAnexo` (:252-257) | `GET anexo?identificador=` esperando **bytes** | sim (`DownloadHelper.kt:27`, `MensagensAdapter.kt:239,288`, `ImageViewerActivity.kt:60`) | `dpr:322` / `api.pas:1042` devolve JSON `{url}` | `rotas.ts:136`, `anexos.ts:41-56` devolve JSON `{url}` | igual (já quebrava) | **não** |
| 33 | `verificarAnexoExiste` (:262-266) | `GET anexo/existe?identificador=` | sim | `dpr:313` | `rotas.ts:134`, `anexos.ts:33-39` | igual | sim |
| 34 | `uploadAnexo` (:271-278) | `PUT anexo?tipo&nome&extensao` + corpo `application/octet-stream` | sim | `dpr:331-345`: corpo **JSON** `{identificador,tipo,nome,extensao,tamanho}` → `{existe,id,upload_url,upload_status}` | `rotas.ts:138`, `esquemas.ts:74`, `anexos.ts:58-81` (mesmo contrato JSON) | igual (já quebrava) + nginx 1 MiB | **não** (400 ou 413) |
| 35 | `confirmarAnexo` (:281-285) | `POST anexo/confirmar?identificador=` | não | `dpr:347` | `rotas.ts:140`, `anexos.ts:83-95` | igual (404 em vez de 500) | sim |
| 36 | `listarAnexos` (:288-297) | `GET anexos?...` → `AnexoListItem` | não | `dpr:412` / `api.pas:1169` | `rotas.ts:142`, `anexos.ts:97-169` | igual (já quebrava: campos `anexo_id`, `autor_id`, `url`) | forma incompatível |
| 37 | `iniciarChamada` (:304-308) | `PUT chamada/iniciar` `{tipo, usuarios[{id}]}` | sim | `dpr:430` / `api.pas:1989` | `rotas.ts:154`, `esquemas.ts:86`, `chamadas.ts:88-110` | mudou (+`conversa_chat_id`, `usuarios[].saiu_em/recusou_em`) | sim |
| 38 | `entrarChamada` (:313-317) | `POST chamada/entrar` `{id}` | sim | `dpr:446` | `rotas.ts:158`, `chamadas.ts:199-212` | mudou (WS 54 também para si; 404) | sim |
| 39 | `recusarChamada` (:322-326) | `POST chamada/recusar` `{id}` | sim | `dpr:454` | `rotas.ts:160`, `esquemas.ts:88` (`nao_atendeu?`), `chamadas.ts:164-197` | mudou (WS 53 também para si; `nao_atendeu`) | sim |
| 40 | `sairChamada` (:331-335) | `POST chamada/sair` `{id}` | sim | `dpr:462` | `rotas.ts:163`, `chamadas.ts:214-225` | mudou (404) | sim |
| 41 | `cancelarChamada` (:340-344) | `POST chamada/cancelar` `{id}` | não | `dpr:438` | `rotas.ts:156` | mudou (404) | sim |
| 42 | `finalizarChamada` (:349-353) | `POST chamada/finalizar` `{id}` | não (o app usa `sair`) | `dpr:478` | `rotas.ts:168` | mudou (404) | sim |
| 43 | `obterDadosChamada` (:358-362) | `GET chamada/dados?id=` | sim | `dpr:486` / `api.pas:2306,2369` | `rotas.ts:192`, `chamadas.ts:307-389` | mudou (+`conversa_chat_id`; 404) | sim |
| 44 | `listarHistoricoChamadas` (:367-370) | `GET chamadas` → `List<HistoricoChamada>` | sim (`HistoricoChamadasActivity.kt:91`) | `dpr:502` / `api.pas:2445` | `rotas.ts:196`, `chamadas.ts:429-450` | mudou (datas com `Z`); forma já incompatível | forma incompatível |
| 45 | `adicionarUsuarioChamada` (:373-377) | `PUT chamada/usuario` `{chamada_id, usuario_id}` | não | `dpr:470` | `rotas.ts:165`, `esquemas.ts:87` | igual | sim |
| 46 | `ativarVideoChamada` (:380-384) | `POST chamada/video` `{id}` | sim | `dpr:515` | `rotas.ts:198-201` (404 se não participa) | mudou (404) | sim |
| 47 | `listarChamadasPendentes` (:387-390) | `GET chamadas/pendentes` → `List<ChamadaResponse>` (com `usuarios`) | sim (`SocketService.kt:653`) | `dpr:494` / `api.pas:2312-2366` (sem `usuarios`) | `rotas.ts:194`, `chamadas.ts:312-330` (sem `usuarios`) | igual (já quebrava) | **não** (NPE capturada) |
| 48 | `obterSip` (:394-397) | `GET sip` | não | `dpr:539` | `rotas.ts:207`, `sip.ts:10-26` (`{}` se não houver) | igual | sim |
| 49 | `criarSip` (:399-403) | `PUT sip` | não | `dpr:547` | `rotas.ts:209`, `esquemas.ts:108` | igual | sim |
| 50 | `atualizarSip` (:405-409) | `PATCH sip` sem `id` | não | `dpr:555` exige `id` | `rotas.ts:211`, `esquemas.ts:109-120` (`id` obrigatório) | igual (já quebrava) | **não** (400) |

### 2.3 Notas detalhadas por endpoint

**1. `POST /api/login`** — Corpo `{login, senha, dispositivo_id?}` (`esquemas.ts:29`). Resposta igual à de abril: `{id, nome, email, telefone, avatar_identificador, dispositivo:{id,nome,modelo,versao_so,plataforma,ativo}, token}` (`usuarios.ts:20-30,65`; `rotas.ts:35`). Mudanças: (a) `dispositivo_id` só é reaproveitado se `dispositivo.usuario_id` = quem loga (`usuarios.ts:51`, commit `8e81360`); (b) usuário inexistente/senha errada → 401 `{"error":"Usuário não encontrado!"}`/`"Senha incorreta!"` (igual); dispositivo inativo → 401 `"Seção Encerrada!"` (igual); (c) limite de 10 req/min por IP no nginx (`nginx.conf:97-100`). O Android sempre manda `dispositivo_id = null` (`LoginActivity.kt:102`), então cada login cria um dispositivo `desconhecido` novo — comportamento já existente em abril (`api.pas@56e7cc0:165-179`). `LoginResponse` (`data/model/LoginResponse.kt`) é compatível.

**2. `PUT /api/usuario`** — Corpo `{nome, login, email, senha, telefone?}` (`esquemas.ts:36`). Agora é **pública** (`rotas.ts:38`, antes do `resolve` de `:42`); em abril a lista de rotas livres era `/api/login` e `/api/cadastro` (`dpr@56e7cc0:81-84`), portanto `PUT /api/usuario` exigia JWT. Resposta: linha de `usuario` sem `senha` (`usuarios.ts:105-106`) — não tem `avatar_url` nem `avatar_identificador` (igual a abril). Login duplicado → 400 `"Login já cadastrado!"`; senha fora de 4..72 → 400.

**3. `PATCH /api/usuario`** — Esquema `{id (obrigatório), nome?, email?, telefone?, avatar_anexo_id?}` (`esquemas.ts:37`); `id` ≠ usuário do token → **403** `"Acesso negado!"` (`usuarios.ts:111-113`, novo). Android manda `AtualizarUsuarioRequest` **sem `id`** (`data/model/UsuarioRequest.kt:15-20`; `data/repository/UsuarioRepository.kt:23`) → 400 `Campo "id" é obrigatório e não foi informado!`. Em abril: `UpdateJSON` → 400 `Campo "id" obrigatório para alteração!` (`conversa.comum.pas@56e7cc0:176-177`). Resposta: linha de `usuario` sem `senha`.

**4. `POST /api/alterar-senha`** — Esquema `{senha_atual, senha}` (`esquemas.ts:30`); Android manda `{senha_atual, senha_nova}` (`data/model/UsuarioRequest.kt:23-26`) → 400 `Campo "senha" é obrigatório...`. Em abril também exigia `senha` (`api.pas@56e7cc0:190`). Senha atual errada: abril **401** (`conversa.autorizacao.pas@56e7cc0:152,161`), hoje **400** `"Senha atual incorreta!"` (`autorizacao.ts:236-243`, commit `95430ba`). Resposta `{}`.

**5. `DELETE /api/usuario?id=`** — Hoje: `id` ≠ token → 403; apaga `dispositivo_usuario` e `dispositivo` do usuário; FK violada (mensagens, conversas, chamadas) → **409** `"A conta tem histórico e não pode ser excluída!"` (`usuarios.ts:118-131`). Abril: `Delete('usuario', id)` sem conferir dono (`api.pas@56e7cc0:304-308`). Android passa o próprio `userId` (`UsuarioRepository.kt:34`).

**6. `PATCH /api/dispositivo`** — Esquema `{id (obrigatório), nome?, modelo?, versao_so?, plataforma?, token_fcm?}` (`esquemas.ts:31-33`). Se nenhum campo alterável vier, devolve o corpo como está; senão confere dono (403) e devolve a linha (`usuarios.ts:76-90`). Android: `registrarOuAtualizar` envia `id = null` (`DispositivoRepository.kt:39-47`) e `atualizarTokenFcm` envia só `token_fcm` (`:62`); como o Gson omite nulos, `id` não vai → **400**. Em abril: `CamposObrigatorios(oDispositivo, ['id'])` → 400 (`api.pas@56e7cc0:214`). Consequência: **o token FCM nunca é gravado no servidor** (ver seção 5). O comentário "backend cria baseado em fingerprint" (`DispositivoRequest.kt:7`) nunca foi verdade em nenhuma versão.

**7. `PUT /api/dispositivo/usuario?dispositivo_id=`** — Igual, mas dispositivo de outro usuário → 403 (`usuarios.ts:92-95`, commit `5272c9f`). Resposta: linha de `dispositivo_usuario`.

**8–10. Contatos** — `PUT /usuario/contato?relacionamento_id=` (`usuarios.ts:133-135`), `DELETE /usuario/contato?id=` (403 se não for seu, `autorizacao.ts:216-224`), `GET /contatos/online` → `number[]` (`usuarios.ts:147-155`). Iguais a abril.

**11. `GET /api/conversas`** — Mesmos campos de abril (`id, descricao, tipo, inserida, nome, destinatario_id, mensagem_id, ultima_mensagem, ultima_mensagem_texto, mensagens_sem_visualizar, avatar_url`) **mais** `fixada_ordem` (int|null) e `arquivada_em` (data|null) (`conversas.ts:16-30,88-89`, commit `95430ba`). `ultima_mensagem_texto`: `"Mensagem oculta"` se a última foi excluída (`:127`, commits `b94b504`/`f095bf1`), `"figurinha"` para tipo 7 (`:130`), `"imagem"` tipo 2, texto tipo 1, `""` demais. Conversas arquivadas **continuam** na resposta (o filtro é do cliente web). Android `Conversa` (`data/model/Conversa.kt:5-16`) ignora os campos novos — parse OK, mas o app mostra conversas arquivadas e não ordena fixadas.

**12. `GET /api/conversa/dados?id=`** — **Não existe** nem em `56e7cc0` (lista de rotas `dpr@56e7cc0:104-569`) nem hoje (`rotas.ts`). Hoje responde 404 `{"error":"Rota GET:/api/conversa/dados não encontrada"}`. Uso: `ChatActivity.kt:966` para montar participantes de chamada em grupo. Substituto: `GET /api/conversa/usuarios?conversa=` → `[{id (conversa_usuario.id), usuario_id, nome, avatar_url}]` (`conversas.ts:203-220`).

**13. `GET /api/usuario/contatos`** — `[{id, nome, login, email, telefone}]` de **todos** os usuários (`usuarios.ts:142-144`). Igual.

**14. `PUT /api/conversa`** — Esquema `{descricao?: string|null, tipo?: int}` (`esquemas.ts:43`). `inserida` enviado pelo Android (`GrupoRequest.kt:9`, `ChatActivity.kt:609-616`) é **ignorado** (abril gravava o valor do cliente via `InsertJSON`). O criador é inserido como membro (igual a abril). Resposta: linha de `conversa` (`id, descricao, tipo, inserida, criado_por, ...`). `CriarConversaResponse` OK.

**15. `PUT /api/conversa/usuario`** — `{usuario_id, conversa_id}` (`esquemas.ts:48`). Membro existente → devolve o registro existente, sem erro (`conversas.ts:224-230`, commit `97f9341`). Em abril, como o criador já entrava ao criar (`api.pas@56e7cc0:429-436`), o Android (`ChatActivity.kt:632-638`) recebia erro de chave duplicada ao incluir a si mesmo; hoje recebe 200. Novo membro gera WS 40 para os outros.

**16–18.** `DELETE /conversa/usuario?id=` (só auto-remoção, 403), `PATCH /conversa` `{id, descricao}` (403 se não membro), `DELETE /conversa?id=` (403 se não membro). Iguais.

**19. `GET /api/conversa/usuarios?conversa=`** — `[{id, usuario_id, nome, avatar_url}]`; Android espera `List<Contato>{id,nome,login,email,telefone}` — `id` viria como `conversa_usuario.id`, não id do usuário. Igual a abril (`api.pas@56e7cc0:591-636`).

**20–21. `POST /api/conversa/digitando` e `/gravando`** — Corpo `{id}` (`esquemas.ts:41`) — Android `DigitandoRequest` serializa `id` (`MensagemExtras.kt:68-71`), OK. Novo: **403** se não for membro (`conversas.ts:237-240`, commit `0b67aca`). Resposta `{}`.

**22. `GET /api/mensagens`** — Query: `conversa` (obrigatório), `mensagemreferencia` (int ≥ 0, padrão 0), `mensagensprevias`/`mensagensseguintes` (int 0..1000, padrão 0) (`esquemas.ts:58-65`). Máx. 100 mensagens. Formato de cada item hoje (`mensagens.ts:64-79,597-613`):

```
{ id, remetente_id, remetente, conversa_id, inserida, alterada (null), visivel_em (null|data),
  excluida_em (null|data)            <- NOVO (b94b504)
  mensagem_referencia?: {tipo, mensagem?: {id, conversa_id, remetente, inserida,
                         excluida_em <- NOVO, conteudos[], mensagem_referencia?}},
  recebida, visualizada, reproduzida (bool),
  conteudos: [{id, ordem, tipo, conteudo, nome, extensao,
               transcricao_status (0..3) <- NOVO (2299a5c), transcricao <- NOVO}],
  reacoes?: [{emoji, quantidade, reagiu, usuarios:[{usuario_id, nome, reagido_em, avatar_url}]}] }
```

Diferenças com abril (`api.pas@56e7cc0:1683-1810`): `excluida_em` novo; `transcricao_status`/`transcricao` novos; `alterada` era sempre string (com data zero `1899-12-30T00:00:00.000Z` quando nula — `DateToISO8601` de campo nulo, `api.pas@56e7cc0:1690`) e agora é `null`; conteúdo **tipo 7 (figurinha)** novo, devolvido junto com 1 e 6 (`mensagens.ts:415`). **Mensagens excluídas continuam no array, com `conteudos` completos** (`carregarConteudos` não filtra, `mensagens.ts:401-452`). Android `Mensagem` (`data/model/Mensagem.kt:9-22`) não tem `excluida_em`, e `MensagensAdapter` só trata tipos 1–4 (`ui/chat/MensagensAdapter.kt:84-126`). Efeito colateral igual a abril: marca como recebidas e dispara WS 3.

**23. `PUT /api/mensagem`** — Esquema (`esquemas.ts:50-57`): `{conversa_id, visivel_em?: string|null, conteudos: [{ordem, tipo, conteudo: string|null}], mensagem_referencia?: {tipo, origem_mensagem_id}|null}`. `nome`/`extensao` dos conteúdos (enviados pelo Android, `Mensagem.kt:52-58`) são ignorados — já eram em abril (`api.pas@56e7cc0:840-846`). Novos 400: figurinha fora do formato (`mensagens.ts:176-178`), tipo de referência ≠ 1/2. 403 se não membro. Resposta: linha de `mensagem` = `{id, usuario_id, conversa_id, inserida, alterada: null, visivel_em, excluida_em: null, excluida_por: null}` (+`mensagem_referencia` se houver). `EnviarMensagemResponse.alterada: String` (não-nulo em Kotlin) recebe `null` — igual a abril.

**24. `GET /api/mensagem/visualizar`** — Android usa **GET com query** (`ConversaApi.kt:190-195`); o servidor sempre teve só **`POST /api/mensagem/visualizar` com corpo `{conversa, mensagem}`** (`dpr@56e7cc0:372-378`; hoje `rotas.ts:111-112`, `esquemas.ts:66`). Hoje: 404 `Rota GET:/api/mensagem/visualizar não encontrada`. Resposta correta: `{sucesso: true}` + WS 3 aos outros membros (`mensagens.ts:618-629`).

**25. `POST /api/mensagem/reproduzir`** — Esquema `{conversa, mensagem}`; Android manda `{mensagem_id, conversa_id}` (`MensagemRepository.kt:143-146`) → 400. Já era assim em abril (`api.pas@56e7cc0:1833`).

**26. `DELETE /api/mensagem?id=`** — ver seção 8. Abril: só autor (403) e só se ninguém recebeu (409), apagava fisicamente e devolvia a linha apagada + `conteudo` (`api.pas@56e7cc0:941-969`; `conversa.autorizacao.pas@56e7cc0:95-112`). Hoje: só autor (403), qualquer momento; mensagem normal → `UPDATE ... set excluida_em = now(), excluida_por` e resposta `{id, conversa_id, excluida_em}`; repetir devolve a mesma coisa; agendada ainda no futuro → apagada de fato, resposta = linha + `conteudo` (`mensagens.ts:288-321`; `autorizacao.ts:205-214`). Gera WS 3 aos outros membros.

**27. `PUT /api/mensagem` com referência** — Android serializa `referencia: {tipo, destino_mensagem_id, origem_mensagem_id?}` (`MensagemExtras.kt:20-38`; `MensagemRepository.kt:114,125`). O servidor espera `mensagem_referencia: {tipo, origem_mensagem_id}`, onde **`origem_mensagem_id` é o id da mensagem citada** (`mensagens.ts:193-201`; abril `api.pas@56e7cc0:797,815-833`). Abril: o par `referencia` ficava no objeto e `InsertJSON` lançava "Tipo do campo ... não esperado" → **500** (`conversa.comum.pas@56e7cc0:144`). Hoje: `referencia` é descartado e a mensagem é gravada **sem** referência → **200 silencioso**.

**28. `GET /api/mensagens/novas?desde=`** — Resposta `[{conversa_id, mensagem_id, ate}]` igual. `desde` sem fuso é tratado como UTC (`comum.ts:53-58`). Mudanças (commit `2299a5c`): exclui mensagens que o usuário já visualizou; `ate` truncado em ms e comparação `>` truncada (`mensagens.ts:702-719`).

**29. `GET /api/mensagem/status?conversa=&mensagem=`** — `mensagem` é lista CSV de ids (texto; não-inteiro → 400). Resposta `[{conversa_id, mensagem_id, recebida: bool, visualizada: bool, reproduzida: bool, excluida_em}]` (`mensagens.ts:655-662`); `excluida_em` é novo (`b94b504`). Android espera `{mensagem_id, usuario_id, recebida: String?, visualizada: String?, reproduzida: String?}` (`MensagemExtras.kt:48-54`) — incompatível desde abril. O que o Android modela existe hoje em **`GET /api/mensagem/status/detalhe?id=`** → `[{usuario_id, nome, recebida, visualizada, reproduzida}]` com datas, só para o autor (403 para os outros) (`mensagens.ts:667-686`, commit `cb7ef68`).

**30. `PUT /api/mensagem/reacao`** — Corpo `{mensagem_id, emoji}`; resposta `{mensagem_id, emoji, acao: 'add'|'remove'}` (`mensagens.ts:754`) — sem `id`/`usuario_id` (igual a abril, `api.pas@56e7cc0:2957-2960`). Novos: 403 se não membro; registra atividade (tipo 1) para o autor da mensagem e manda WS 61 a ele.

**31. `GET /api/pesquisar`** — Query `{conversa: int=0, texto: string=''}` (`esquemas.ts:69`); `usuario` é **ignorado** (sempre o do token, `rotas.ts:126`). Em abril `usuario` era **obrigatório** (`dpr@56e7cc0:529`) e o Android o omite (default `null`, `ConversaApi.kt:246`) → 400 em abril. Hoje 200, mas a resposta é **array de mensagens no mesmo formato de `GET /mensagens`** (`mensagens.ts:398`), não `PesquisaResultado{mensagem_id, conversa_descricao, usuario_nome, trecho}` (`MensagemExtras.kt:57-65`). Texto vazio → 400 (antes 500). Agora `unaccent` + `ilike` e ignora excluídas.

**32. `GET /api/anexo?identificador=`** — Sempre devolveu **JSON `{url}`** com URL pré-assinada do MinIO, válida 600 s (abril `api.pas@56e7cc0:1040-1043`; hoje `anexos.ts:55`). O Android trata a resposta como o próprio arquivo: `DownloadHelper.kt:27-42` grava o JSON como arquivo; `MensagensAdapter.kt:239-245` e `ImageViewerActivity.kt:60` passam a URL da API ao Glide; `MensagensAdapter.kt:288` idem para áudio. Mudanças de hoje: anexo inexistente 404 (antes 500); upload pendente/sem objeto → 400 `"Upload ainda não foi concluído"`; URL montada com o `Host` e `X-Forwarded-Proto` da requisição + prefixo `/storage` (`minio.ts:202-215`) — antes vinha do parâmetro `s3_endpoint` (removido na migração 025).

**33. `GET /api/anexo/existe?identificador=`** — `{existe:false}` ou `{existe:true, id, identificador, tipo, tamanho, upload_status}` (`anexos.ts:33-39`). Igual.

**34. `PUT /api/anexo`** — Ver seção 7. Contrato (abril e hoje): corpo JSON `{identificador (sha256), tipo, nome?, extensao?, tamanho}` (`esquemas.ts:74`) → `{existe:false, id:number, upload_url (PUT pré-assinado 300 s), upload_status:0}` ou `{existe:true, id:string, upload_url (GET!)}` (`anexos.ts:58-81`). Android manda query + bytes `application/octet-stream` (`UploadHelper.kt:61-71,124-134`) e espera `{id, identificador, tipo, tamanho}` (`ConversaApi.kt:416-421`). Hoje: corpo ≤ 1 MiB → **400** de validação (`identificador`/`tipo`/`tamanho` ausentes); corpo > 1 MiB → **413** do nginx (HTML, não JSON) por `client_max_body_size 1m` (`nginx.conf:24`). Abril: `Req.Body<TJSONObject>` sem JSON → erro 500.

**35. `POST /api/anexo/confirmar?identificador=`** — `{confirmado:true, upload_status:1}`; 404/400 (`anexos.ts:83-95`). Igual (404 em vez de 500).

**36. `GET /api/anexos`** — Itens `{anexo_id, identificador, nome, extensao, tamanho, criado_em, tipo, mensagem_id, conversa_id, conversa_descricao, autor_id, autor_nome, url}` (`anexos.ts:130-168`) — Android espera `id, criado_por, criado_por_nome, url_download` (`AnexoListagem.kt:6-19`). Igual a abril.

**37. `PUT /api/chamada/iniciar`** — `{tipo?, conversa_id?, usuarios:[{id}]}` (`esquemas.ts:86`). Android inclui a si mesmo em `usuarios` (`ChamadaRepository.kt:85-89`) — correto (quem não está em `chamada_usuario` recebe 404 depois). Android **não** manda `conversa_id`, então não há mensagem-resumo na conversa nem chat da chamada vinculado. Resposta = `dadosChamada` (`chamadas.ts:332-389`): `{id, iniciada, finalizada, tipo, status, criado_em, criado_por, conversa_chat_id <- NOVO, usuarios:[{usuario_id, usuario_nome, status, adicionado_por, adicionado_por_nome, adicionado_em, entrou_em, saiu_em, recusou_em}]}`. `recusou_em` agora também considera evento 8 (não atendeu). Notifica WS 51 aos outros.

**38–42. entrar/recusar/sair/cancelar/finalizar** — Corpo `{id}`; resposta `{id}`. Não participante → 404 `"Chamada não encontrada!"` (antes 500). `entrar` e `recusar` agora mandam WS 54/53 **também ao próprio usuário** (`chamadas.ts:195,210`). `recusar` aceita `nao_atendeu: true` (registra evento 8 e atividade "chamada perdida"; `chamadas.ts:164-197`). `entrar` inclui o usuário no chat da chamada se ele já existir (`chamadas.ts:269-281`).

**43. `GET /api/chamada/dados?id=`** — Mesmo formato da resposta de `iniciar` (com `conversa_chat_id`). 404 se não participa (`chamadas.ts:307-310`).

**44. `GET /api/chamadas`** — Query `participante`, `de`, `ate` (`esquemas.ts:106`); 25 itens (250 com filtro). Itens `{id, tipo, status, criado_em, criado_por, conversa_id, iniciada, finalizada, duracao, participantes:[{usuario_id, nome, status, duracao, avatar_url}]}` (`chamadas.ts:416-450`). Mudança: datas antes eram texto sem fuso (`api.pas@56e7cc0:2493-2497`), agora ISO com `Z` (`95430ba`). Android `HistoricoChamada` (`data/model/HistoricoChamada.kt:11-31`) espera `chamada_id, tipo_chamada, status_chamada, criado_por_id, criado_por (String), usuario_exibido_id, ...` — nenhum desses existe em nenhuma versão.

**45. `PUT /api/chamada/usuario`** — `{chamada_id, usuario_id}`; resposta `{id}`; WS 51 a todos os outros membros. Igual.

**46. `POST /api/chamada/video`** — `{id}` → `{}`; WS 56 aos outros. 404 se não participa (`0b67aca`).

**47. `GET /api/chamadas/pendentes`** — Itens `{id, tipo, status, iniciada, finalizada, conversa_id (0 se nulo), criado_em, criado_por}` (`chamadas.ts:312-330`) — **sem `usuarios`**, igual a abril (`api.pas@56e7cc0:2312-2366`). Android: `SocketService.kt:660` faz `chamada.usuarios.firstOrNull` em lista que veio `null` do Gson → `NullPointerException` capturada em `:668` → chamadas pendentes nunca são recuperadas.

**48–50. SIP** — `GET /sip` → linha ou `{}`; `PUT /sip` `{sip_user, auth_user?, sip_password, display_name?, domain, ws_server, ativo?}`; `PATCH /sip` exige `id` (`esquemas.ts:109-120`) — `SipRequest` não tem `id` (`data/model/Sip.kt:19-27`). Iguais a abril.

---

## 3. Endpoints novos que o Android não conhece

| Endpoint | Commit | Para que serve / formato |
|---|---|---|
| `GET /api/ice` | `aa61b78` (Delphi) / `97f9341` | **Necessário para chamadas hoje.** `{iceServers:[{urls:"turn:<host>:3478?transport=tcp"` (ou `"turns:<host>:<CONVERSA_TURN_PORTA>?transport=tcp"` em produção)`, username:"<expira_unix>:<usuario>", credential:base64(HMAC-SHA1(segredo, username))}], iceTransportPolicy:"relay"|"all"}`; sem segredo TURN → `{iceServers:[], iceTransportPolicy:"all"}` (`chamadas.ts:576-594`; rota `rotas.ts:203`). Credencial vale 1 h. |
| `GET /api/anexo/transcricao?identificador=` | `2299a5c` | `{status: 0 nenhuma | 1 processando | 2 concluída | 3 erro, texto, erro}` (`transcricoes.ts:59-68`) |
| `PUT /api/anexo/transcricao` `{identificador}` | `2299a5c` | Pede transcrição de áudio (tipos 4/5) — mesma resposta (`transcricoes.ts:71-100`) |
| `GET /api/mensagem/status/detalhe?id=` | `cb7ef68` | Status por destinatário com datas; só o autor (`mensagens.ts:667-686`) |
| `PATCH /api/conversa/fixadas` `{conversas:int[]}` | `95430ba` | Ordem das fixadas; resposta `{conversas}` (`conversas.ts:182-189`) |
| `PATCH /api/conversa/arquivada` `{conversa, arquivada}` | `95430ba` | Arquiva/desarquiva; resposta `{id, arquivada}` (`conversas.ts:192-201`) |
| `PUT /api/chamada/chat` `{id}` | `315464b` | Cria/obtém o grupo da chamada → `{conversa_id}`; avisa WS 40 e WS 57 `{acao:'chat', conversa_id}` (`chamadas.ts:230-266`) |
| `GET /api/atividades?antes=&limite=` | `8283616` | Lista paginada `{id, tipo (1 reação, 2 resposta, 3 menção, 4 chamada perdida), criado_em, nova, autor_id, autor_nome, conversa_id, conversa_tipo, conversa_descricao, mensagem_id, conteudo_tipo, texto, chamada_id, chamada_tipo, emoji, autor_avatar_url}` (`atividades.ts:105-153`) |
| `GET /api/atividades/novas` | `8283616` | `{quantidade}` |
| `POST /api/atividades/vistas` | `8283616` | `{vistas_em}` |
| `GET /api/usuario/permissoes` | `7f670c3` | `string[]` de códigos |
| `GET /api/permissoes` | `7f670c3` | `{permissoes, usuarios, modo_aberto}` (403 sem permissão) |
| `PUT /api/permissao/usuario` `{usuario_id, codigo}` / `DELETE /api/permissao/usuario?usuario_id=&codigo=` | `7f670c3` | Concede/retira permissão |
| `GET /api/parametros` / `PATCH /api/parametros` | `7f670c3` | Parâmetros do sistema (`fcm_*`, `turn_forcar_relay`, `transcritor_*`, `gravacao_dias`) — 403 sem permissão `parametros` |
| `GET /api/docs`, `GET /api/docs/json` | `95430ba` | Documentação OpenAPI gerada a partir dos esquemas — útil como contrato de referência para o Android |
| 🆕 `PUT /api/enquete` `{conversa_id, pergunta, opcoes[], multipla}` | `8031fa5` | Cria enquete + a mensagem tipo 8 (só em grupo) → mensagem + `enquete_id` |
| 🆕 `GET /api/enquete?id=` | `8031fa5` | `{id, conversa_id, mensagem_id, pergunta, multipla, criado_por, opcoes:[{id, texto, votantes:[{id,nome}]}], total_votantes, meus_votos}` |
| 🆕 `POST /api/enquete/encerrar` `{enquete_id}` | `5cad911` | Encerra antes do prazo (quem criou a votação ou o grupo); devolve a enquete; WS 62 |
| 🆕 `PATCH /api/enquete` `{enquete_id, encerra_em\|null}` | `5cad911` | Define, adia ou tira a data final (quem criou); devolve a enquete; WS 62 |
| 🆕 `POST /api/enquete/votar` `{enquete_id, opcoes:int[]}` | `8031fa5` | Substitui o voto (vazio tira); devolve a enquete; WS 62 a todos os membros |

---

## 4. WebSocket

### 4.1 Transporte e autenticação

| | Abril (`56e7cc0`) | Hoje (`7f670c3`) | Android |
|---|---|---|---|
| Servidor | Bird Socket próprio na porta **9090** (`dpr@56e7cc0:571`; `ws.pas@56e7cc0:217-224`) | Elysia `.ws('/ws/')` na **mesma porta da API (8080)** (`websocket.ts:54-55`; `docker-compose.yml:132-133`) | — |
| URL pública | `wss://<host>:4430/ws/` (nginx `location /ws/` → `ws_upstream/` 127.0.0.1:9090, `nginx.conf@56e7cc0:48-57`) | `wss://<host>[:443]/ws/` (nginx `location /ws/` → `api:8080` com o caminho `/ws/`, `nginx.conf:109-114`) | `wss://$host:$port/ws/` (`data/socket/SocketManager.kt:133`), host/porta tirados da `apiUrl` (`MainActivity.kt:462-477`) |
| Autenticação | 1ª mensagem `{tipo:1, token}`; JWT exige `exp`, `iat`, `sub` (`ws.pas@56e7cc0:139-150`) | Igual: `{tipo:1, token}`; segunda tentativa de login na mesma conexão é ignorada (`websocket.ts:78-89`) | `{"tipo":1,"token":...}` 100 ms após abrir (`SocketManager.kt:218-227`) — **compatível** |
| Erro de token | `{tipo:0, message:<erro da lib>}` | `{tipo:0, message:"Token inválido ou expirado"}` (`websocket.ts:85-87`) | Lê `message` em tipo 0 (`SocketManager.kt:249-253`) — compatível |
| JSON inválido / sem `tipo` | `{tipo:9, message:...}` (`ws.pas@56e7cc0:126-130`) | Igual (`websocket.ts:60-67`) | tipo 9 cai no `else` (log) |
| Mensagens cliente→servidor | só `1` | `1` e **`57`** (sinal da chamada) | só `1` |
| Ping/timeout | — | Bun envia pings; nginx `proxy_read_timeout 3600s` | OkHttp sem `pingInterval` (`SocketManager.kt:61`), mas responde pong automaticamente |

### 4.2 Envelope e números de evento

Envelope sempre JSON plano com `tipo` numérico, sem campo `dados` (exceto o 57). **Nenhum número existente mudou de valor**: a enumeração de hoje (`websocket.ts:4-22`) é a de abril (`ws.pas@56e7cc0:20-36`) mais 57 e 61.

| tipo | Nome no servidor | Abril (payload) | Hoje (payload) | Constante Android | Android lê | Situação |
|---|---|---|---|---|---|---|
| 0 | Erro | `{tipo, message}` | igual | `TYPE_ERRO=0` (`SocketManager.kt:32`) | `message` | ok |
| 1 | Login | (só cliente→servidor) | igual | `TYPE_LOGIN=1` | — | ok |
| 2 | NovaMensagem | `{tipo, titulo, mensagem}` (`ws.pas@56e7cc0:290-303`) | igual (`websocket.ts:134-136`); `titulo` = nome do remetente; `mensagem` = texto resumido (menção `@Nome`, código resumido, partes unidas por `" | "`, `"imagem"`, `"arquivo"`, `"figurinha"`) | `TYPE_NOVA_MENSAGEM=2` | 3º ramo "gatilho" (`SocketManager.kt:280-289`), `conversaId=0` | igual; sem `conversa_id` em nenhuma versão |
| 3 | AtualizacaoStatusMensagem | `{tipo, grupo:<conversa_id int>, mensagens:"1,2,3"}` | igual (`websocket.ts:138-140`). **Agora também emitido** para exclusão de mensagem (`mensagens.ts:308`), para mensagem-resumo de chamada a todos (`chamadas.ts:559`) e quando agendada sai | `TYPE_STATUS_MENSAGEM=3` | ramo "legacy" CSV (`SocketManager.kt:303-308`) | parse ok; **nenhum consumidor** de `onStatusMensagemAtualizado` no app (grep) |
| 4 | Digitando | `{tipo, conversa_id, usuario_id}` | igual | `TYPE_DIGITANDO=4` | ok | ok (só membros enviam, 403) |
| 5 | GravandoAudio | `{tipo, conversa_id, usuario_id}` | igual | `TYPE_GRAVANDO_AUDIO=5` | ok | ok |
| 7 | ReacaoMensagem | `{tipo, conversa_id, mensagem_id, usuario_id, emoji, acao}` | igual (`websocket.ts:146-148`) | `TYPE_REACAO_MENSAGEM=7` | ok | ok |
| 40 | ConversaNova | `{tipo, conversa_id, usuario_id}` | igual; também emitido ao criar chat da chamada e ao entrar em chamada com chat (`chamadas.ts:258,279`) | `TYPE_CONVERSA_ATUALIZADA=40` | ok | sem consumidor no app |
| 51 | ChamadaRecebida | `{tipo, chamada_id, usuario_id}` | igual | `TYPE_CHAMADA_RECEBIDA=51` | lê `usuario_nome` (opcional, nunca enviado) | ok |
| 52 | ChamadaFinalizada | `{tipo, chamada_id, usuario_id}` | igual | 52 | ok | ok |
| 53 | UsuarioRecusou | idem; só aos outros | idem; **também ao próprio usuário** (`chamadas.ts:195`, `95430ba`) | 53 | ok | ver 9.2 |
| 54 | UsuarioEntrou | idem; só aos outros | idem; **também ao próprio usuário** (`chamadas.ts:210`) | 54 | ok (ignora `peerId == meuUid` para estado, `ChamadaRepository.kt:248`) | ok |
| 55 | UsuarioSaiu | idem | igual | 55 | ok | ok |
| 56 | VideoAtivado | idem | igual | 56 | ok | ok |
| **57** | **SinalChamada** (novo, `315464b`) | — | Bidirecional. Cliente→servidor `{tipo:57, chamada_id, dados:{...}}` (≤ 2000 caracteres, só participantes com status 3); servidor→cliente `{tipo:57, chamada_id, usuario_id, dados}` — `dados` ex.: `{acao:'chat', conversa_id}` ou ponteiro de tela compartilhada (`websocket.ts:155-221`) | — | cai no `else` (log) | ignorado, sem crash |
| 60 | StatusUsuario | `{tipo, usuario_id, online:bool}` | igual | `TYPE_STATUS_USUARIO=60` | ok | ok |
| **61** | **NovaAtividade** (novo, `8283616`) | — | `{tipo:61}` sem outros campos (`websocket.ts:151-153`) | — | `else` (log) | ignorado |
| **62** 🆕 | **EnqueteAtualizada** (novo, `8031fa5`) | — | `{tipo:62, enquete_id, conversa_id}` a todos os membros a cada voto | — | `else` (log) | ignorado |

Observação: o comentário do Android "Novo payload (alinhado com backend): { tipo:2, mensagem: {...} }" (`SocketManager.kt:256`) e o formato `mensagens` como array de objetos (`:293-301`) **não correspondem a nenhuma versão do servidor** — só o ramo CSV/gatilho é exercitado.

---

## 5. FCM (push)

| | Abril (`56e7cc0`) | Hoje (`7f670c3`) | Android (`service/ConversaFcmService.kt`) |
|---|---|---|---|
| Quando envia | Nova mensagem, para cada destinatário com `dispositivo.ativo` e `token_fcm` não nulo **que não esteja com WebSocket conectado** (`api.pas@56e7cc0:669-683,687-733`) | Igual, e **não envia se a conversa estiver arquivada** pelo destinatário (`notificacoes.ts:297-321`) | — |
| Chamadas | **Nunca** houve push de chamada | **Nunca** há push de chamada | Espera `tipo="chamada_recebida"` (`:59-64`) |
| Formato | HTTP v1 com `message.notification = {title: <nome do remetente>, body: <texto>}`, **sem `data`** (`FCMNotification.pas@56e7cc0:183-186`; `EnviaNotificacoes` chamado sem `ADadosExtras`) | `97f9341`: `notification:{title, body}`. **Desde `6fd4733`: só `data` = `{titulo, mensagem, conversa:"<id>"}` + `webpush.headers.Urgency: high`**; sem `notification`, sem bloco `android` (sem `priority: high`) (`fcm.ts:30-36`) | Lê `data["tipo"]`; **se ausente, retorna sem fazer nada** (`:49`). Para `nova_mensagem` lê `conversa_id`, `titulo`/`remetente_nome`, `mensagem`, `remetente_id` (`:52-57`) e mesmo assim só loga (`:69-73`). |

Consequências:

- Abril: com o app em segundo plano o SDK do Firebase exibia sozinho a notificação do bloco `notification`; em primeiro plano `onMessageReceived` recebia `data` vazio e saía na linha 49.
- Hoje: mensagem só-`data` sempre chega em `onMessageReceived` → sem `tipo` → **nada é exibido em nenhum estado do app**. O nome da chave do id também mudou de inexistente para `conversa` (o Android procura `conversa_id`).
- Na prática o push já não chegava ao Android em nenhuma versão, porque o token nunca é gravado: `PATCH /dispositivo` sem `id` → 400 (seção 2.3, item 6), e `registrarOuAtualizar` nunca é chamado (só `atualizarTokenFcm` em `ConversaFcmService.kt:37`). A troca de formato é, portanto, uma quebra de contrato **mascarada** por outra quebra.
- Detalhe extra: a action `"CHAMADA_PUSH_RECEBIDA"` enviada por `ConversaFcmService.kt:78` não é tratada em `ChamadaService.onStartCommand` (`service/ChamadaService.kt:207-233`) — problema interno do Android, independente do servidor.

---

## 6. Chamadas / WebRTC

### 6.1 Sinalização

Continua sendo **REST + eventos WS 51–56** (seções 2 e 4) + **WHIP/WHEP** direto no MediaMTX. Não há troca de SDP pelo WebSocket. Novidades: WS 57 (sinal entre participantes; o Android não precisa dele para áudio/vídeo), `PUT /chamada/chat`, `nao_atendeu` em `recusar`, auto-notificação 53/54.

### 6.2 MediaMTX, caminhos WHIP/WHEP

| | Abril | Hoje | Android |
|---|---|---|---|
| URL base | `https://<host>:4430/webrtc/` → `127.0.0.1:8889` (MediaMTX nativo no Windows; `nginx.conf@56e7cc0:72-80`; `mediamtx.yml@56e7cc0:13-15`) | `https://<host>/webrtc/` → `mediamtx:8889` (container; `nginx.conf:135-140`; `mediamtx.docker.yml:16-18`) | `apiUrl` sem `/api` + `/webrtc` (`service/ChamadaService.kt:353-360`); padrão `https://192.168.2.5:4430/webrtc` (`app/build.gradle.kts:27`) |
| Caminho | `call-<chamada>-u-<usuario>` | igual (gravado por `"~^call-"`, `mediamtx.docker.yml:32`) | `call-$chamadaId-u-$userId` (`data/webrtc/WebRTCManager.kt:366-367`) — igual |
| Publicar | `POST .../call-X-u-Y/whip` (`application/sdp`) | igual | `WebRTCManager.kt:174-177`, `WhipWhepClient.kt:58-71` |
| Assinar | `POST .../whep`, 404 enquanto não publicado | igual | retry em 404, 40×1 s (`WhipWhepClient.kt:78-114`) |
| Mídia (ICE) | MediaMTX no host; UDP local padrão 8189 nas interfaces do host → **alcançável pela LAN só com STUN** | MediaMTX dentro da rede Docker `conversa-rtc` em **172.30.0.20**; `webrtcLocalUDPAddress: :8189`, `webrtcIPsFromInterfaces: yes`, `webrtcAdditionalHosts: []` (`mediamtx.docker.yml:20-24`); compose publica **só `127.0.0.1:8889/tcp`** (`docker-compose.yml:124`). O comentário do próprio arquivo: "O candidato anunciado e o IP do container, 172.30.0.20, que so o coturn alcanca. Por isso o cliente usa relay-only." | Só STUN `stun:stun.l.google.com:19302` (`build.gradle.kts:28`; `WebRTCManager.kt:356-364`); nunca chama `GET /api/ice` |

### 6.3 TURN

- Hoje existe **coturn** (`docker-compose.yml:86-110`): escuta **somente TCP** 3478 (`turnserver.conf:15-17`, `no-udp`, `no-tls`), relay só por UDP (`no-tcp-relay`, `:41`) e só até o MediaMTX (`denied-peer-ip`, `:36-37`). Credencial temporária via `GET /api/ice` (HMAC-SHA1, `use-auth-secret`, `realm=conversa`). `turn_forcar_relay` padrão `'1'` (migração 23) → `iceTransportPolicy: "relay"`. Em produção a URL é `turns:<host>:<CONVERSA_TURN_PORTA>?transport=tcp` (TLS terminado na borda).
- O cliente web usa exatamente isso: `conversa-web/src/stores/call.ts:41-49` lê `GET /ice` e aplica `iceServers` + `iceTransportPolicy`.
- O Android não tem nada disso (`WebRTCManager.kt:356-357`).

### 6.4 Codecs e gravação

- Desde `4bbfb0a`, o MediaMTX grava cada `call-*` em fMP4; **VP8 não é gravado** (`mediamtx.docker.yml:31`). O cliente web força H264/VP9 com `setCodecPreferences` (`conversa-web/src/stores/call.ts:352-365`).
- O Android usa `DefaultVideoEncoderFactory(eglBase, true, true)` (`WebRTCManager.kt:108`) sem `setCodecPreferences`; a oferta do libwebrtc costuma listar VP8 primeiro, então o MediaMTX tende a negociar VP8 → **vídeo do Android provavelmente não fica gravado** (áudio Opus é gravado). Não verificável sem executar; ver 9.2.

---

## 7. Anexos — upload e download

**Fluxo do servidor (abril e hoje, mesmo contrato):**

1. Cliente calcula SHA-256 do arquivo → `identificador`.
2. `GET /api/anexo/existe?identificador=` → `{existe, ...}`.
3. `PUT /api/anexo` com **JSON** `{identificador, tipo (2 imagem, 3 arquivo, 4 áudio, 5 gravação de áudio), nome?, extensao?, tamanho}` → `{existe:false, id, upload_url, upload_status:0}`; `upload_url` é **PUT pré-assinado no MinIO, 300 s** (`anexos.ts:58-81`; abril `api.pas@56e7cc0:1048-1114`).
4. Cliente faz `PUT <upload_url>` com os bytes (direto no MinIO via `/storage/`, que aceita até 1024 MiB, `nginx.conf:121-133`).
5. `POST /api/anexo/confirmar?identificador=` → `{confirmado:true, upload_status:1}` (o servidor confere com `statObject`).
6. `PUT /api/mensagem` com conteúdo `{tipo:2|3|4|5, ordem, conteudo:<identificador>}`.
7. Download: `GET /api/anexo?identificador=` → `{url}` (GET pré-assinado, 600 s) → cliente baixa da URL (sem `Authorization`).

**O que mudou no servidor:** a URL pré-assinada passou a usar o `Host` e o `X-Forwarded-Proto` da requisição + `/storage` (`minio.ts:198-215`; `contexto.ts`) em vez do parâmetro `s3_endpoint`; códigos 404/400 em vez de 500 (`8e81360`); `client_max_body_size 1m` em `/api/`; `Content-Security-Policy: sandbox` em `/storage/` (`f1ffb14`); para `existe:true`, `id` vem como **string** e `upload_url` é uma URL **GET** (`anexos.ts:65`) — igual a abril.

**O que o Android faz (`data/api/UploadHelper.kt`, `data/api/DownloadHelper.kt`):** passos 1–2 corretos (`UploadHelper.kt:39-55`); passo 3 manda os bytes no corpo do `PUT /api/anexo` com `tipo/nome/extensao` em query (`:61-71`, `:124-134`) e espera `{id, identificador, tipo, tamanho}`; não faz 4 nem 5 (`confirmarAnexo` não é chamado). Download trata a resposta JSON de `GET /api/anexo` como o arquivo (`DownloadHelper.kt:27-42`), e as imagens/áudios do chat apontam o Glide/player para `GET /api/anexo` (`MensagensAdapter.kt:239-245,288`; `ImageViewerActivity.kt:60`). **Nada disso funcionava em abril e continua não funcionando.** A única diferença observável hoje: upload > 1 MiB recebe 413 do nginx em vez de erro da API.

---

## 8. Semântica de exclusão ("ocultar"), datas/fuso, ids e outros

**Exclusão de mensagem** (`b94b504`, `f095bf1`):
- `DELETE /api/mensagem?id=` não apaga mais: grava `mensagem.excluida_em = now()` e `excluida_por` (migração `031.sql`). O app web chama a ação de "Ocultar"; a prévia da conversa mostra `"Mensagem oculta"` (`conversas.ts:127`).
- Só o autor (403 para os outros), **sem prazo** e mesmo que já tenha sido recebida (antes: 409 se alguém recebeu).
- A mensagem **continua** em `GET /mensagens` com `excluida_em` preenchido e **com o conteúdo original** em `conteudos` (o cliente é quem deve esconder). Citações (`mensagem_referencia.mensagem`) também trazem `excluida_em`.
- Some da pesquisa (`mensagens.ts:394`). Apaga as atividades ligadas e avisa WS 61.
- Outros membros recebem WS 3 com o id; `GET /mensagem/status` traz `excluida_em`.
- Agendada ainda não enviada é apagada fisicamente (resposta com `conteudo`).
- Android: não tem `excluida_em` no modelo (`Mensagem.kt:9-22`), não consome WS 3 (nenhum handler de `onStatusMensagemAtualizado`) e exibe o conteúdo normalmente.

**Datas / fuso:**
- Abril e hoje: sessão do PostgreSQL em UTC (`Postgres.pas@56e7cc0:146` / `banco.ts:18`) e colunas `timestamp` sem fuso gravadas em UTC; saída ISO-8601 com `Z`. O instante é o mesmo.
- Formato: abril `yyyy-mm-ddThh:nn:ss.zzzZ` (Delphi `DateToISO8601`); hoje `toISOString()` → mesmo formato com milissegundos. Exceções em abril que agora têm `Z`: `GET /chamadas` (`criado_em`, `iniciada`, `finalizada`) e `reagido_em`.
- O conteúdo da mensagem tipo 6 (resumo de chamada) continua com datas **sem fuso** `YYYY-MM-DDTHH:MM:SS` (`chamadas.ts:415-427`).
- `alterada` nula: abril `"1899-12-30T00:00:00.000Z"`, hoje `null`.
- Entrada (`visivel_em`, `desde`): sem fuso é tratado como UTC (`comum.ts:53-58`).
- O desserializador do Android (`data/api/UtcToLocalDateTimeDeserializer.kt:21-29`) aceita os dois formatos (ISO_DATE_TIME com zona UTC padrão) e converte para o fuso local — compatível.

**Ids e tipos numéricos:** todos `int4` → números JSON; `atividade.id` é `bigserial` mas sai como número (`atividades.ts:151`). `PUT /anexo` devolve `id` string quando já existe e número quando cria (igual a abril). `int8`/`numeric` saem como número (`banco.ts:23-24`); `count()` e somas, idem.

**Tipos de conteúdo de mensagem:** 1 texto, 2 imagem, 3 arquivo, 4 áudio, 5 gravação de áudio, 6 chamada (JSON), **7 figurinha (novo)**. Android só conhece 1–4 (`Mensagem.kt:35-40`).

**Status (sem mudança de valores):** chamada 1 Pendente, 2 Recusada, 3 Em andamento, 4 Encerrada, 5 Desconectada, 6 Cancelada; usuário na chamada 1 Pendente, 2 Recusou, 3 Entrou, 4 Saiu, 5 Desconectou (`chamadas.ts:14-17`). Eventos de chamada (tabela `chamada_evento`) ganharam o **8 = Não atendeu**. Android `ChamadaStatus` (`Chamada.kt:47-58`) nomeia 5 como `NAO_ATENDIDA` — o servidor chama 5 de "Desconectada" (só nome).

---

## 9. Conclusões

### 9.1 QUEBRAS CONFIRMADAS

Cada item traz a evidência dos dois lados. **[NOVA]** = introduzida entre `56e7cc0` e `7f670c3`; **[ABRIL]** = já existia em abril e continua.

1. **[NOVA] Chamadas sem mídia: o Android não usa TURN, e o MediaMTX agora só é alcançável por TURN.**
   - Servidor: `bin/mediamtx/mediamtx.docker.yml:20-24` (candidato só 172.30.0.20, "o cliente usa relay-only"); `docker-compose.yml:124` (só 8889/tcp publicado, UDP 8189 não); `bin/coturn/turnserver.conf:15-17,36-41` (TURN só TCP); credenciais em `GET /api/ice` (`rotas.ts:203`, `chamadas.ts:576-594`).
   - Abril: MediaMTX nativo, `bin/mediamtx/mediamtx.yml@56e7cc0:13-15`, sem `GET /api/ice`.
   - Android: `data/webrtc/WebRTCManager.kt:356-364` (só `stunUrl`); `app/build.gradle.kts:28`. O WHIP/WHEP responde (HTTP via nginx), mas o ICE não conecta.

2. **[NOVA] Push FCM sem `tipo` → o Android descarta todo push.**
   - Servidor: `src/fcm.ts:30-36` (`data: {titulo, mensagem, conversa}`, sem `notification`, desde `6fd4733`).
   - Abril: `FCMNotification.pas@56e7cc0:183-186` (`notification`, sem `data`).
   - Android: `service/ConversaFcmService.kt:49` (`message.data["tipo"] ?: return`), `:53` espera `conversa_id`.
   - Hoje mascarada pelo item 6, mas é quebra de contrato.

3. **[NOVA] Mensagens ocultadas aparecem normalmente, com o texto original.**
   - Servidor: `src/mensagens.ts:288-321` (exclusão lógica), `:606` (`excluida_em` na resposta), `:401-452` (conteúdo não filtrado); `migracoes/031.sql`.
   - Abril: `api.pas@56e7cc0:941-969` (apagava fisicamente).
   - Android: `data/model/Mensagem.kt:9-22` (sem `excluida_em`); `ui/chat/MensagensAdapter.kt:84-126` renderiza `conteudos`; nenhum handler de `onStatusMensagemAtualizado` (WS 3) no app.

4. **[NOVA] Responder/encaminhar grava a mensagem sem a referência (200 silencioso).**
   - Android: `data/model/MensagemExtras.kt:20-38` (chave `referencia`, campo `destino_mensagem_id`).
   - Servidor: `src/esquemas.ts:55` (`mensagem_referencia: {tipo, origem_mensagem_id}`), `src/mensagens.ts:180-201`.
   - Abril: o mesmo request dava 500 (`conversa.comum.pas@56e7cc0:144`). Hoje só no código morto `MensagemRepository.kt:106-126`.

5. **[ABRIL] `GET /mensagem/visualizar` não existe** (servidor só tem POST com corpo).
   - Android: `data/api/ConversaApi.kt:190-195`; usado em `ui/chat/ChatActivity.kt:673,1209`.
   - Servidor: `src/rotas.ts:111-112`, `src/esquemas.ts:66`; abril `dpr@56e7cc0:372-378`.
   - Hoje 404. Nenhuma mensagem é marcada como visualizada pelo Android.

6. **[ABRIL] `PATCH /dispositivo` sem `id` → 400; o token FCM nunca é registrado.**
   - Android: `data/repository/DispositivoRepository.kt:39-47,62`; `data/model/DispositivoRequest.kt:7`.
   - Servidor: `src/esquemas.ts:31-33`, `src/usuarios.ts:76-90`; abril `api.pas@56e7cc0:214`.

7. **[ABRIL] `GET /conversa/dados` não existe** (404). Chamada em grupo a partir do chat falha.
   - Android: `ConversaApi.kt:87-91`, `ui/chat/ChatActivity.kt:966`.
   - Servidor: ausente em `src/rotas.ts` e em `dpr@56e7cc0`.
   - Substituto: `GET /conversa/usuarios?conversa=` (`rotas.ts:85`).

8. **[ABRIL] Download/exibição de anexos: `GET /anexo` devolve JSON `{url}`, não bytes.**
   - Android: `data/api/DownloadHelper.kt:27-42`, `ui/chat/MensagensAdapter.kt:239-245,288`, `ui/chat/ImageViewerActivity.kt:60`.
   - Servidor: `src/anexos.ts:41-56`; abril `api.pas@56e7cc0:1040-1043`.

9. **[ABRIL, piorada] Upload de anexos com contrato errado.**
   - Android: `data/api/UploadHelper.kt:61-71,124-134`, `ConversaApi.kt:271-278,416-421`.
   - Servidor: `src/esquemas.ts:74`, `src/anexos.ts:58-81`.
   - Hoje 400 (≤ 1 MiB) ou 413 do nginx (> 1 MiB, `bin/nginx/nginx.conf:24`); abril 500.
   - Falta também o PUT no MinIO e o `POST /anexo/confirmar`.

10. **[ABRIL] `GET /chamadas/pendentes` sem `usuarios` → NPE capturada; toques perdidos não são recuperados.**
    - Android: `service/SocketService.kt:653-668`, `data/model/Chamada.kt:28`.
    - Servidor: `src/chamadas.ts:312-330`; abril `api.pas@56e7cc0:2312-2366`.

11. **[ABRIL] Histórico de chamadas com campos que não existem.**
    - Android: `data/model/HistoricoChamada.kt:11-31` (`chamada_id`, `tipo_chamada`, `status_chamada`, `criado_por_id`, `usuario_exibido_*`, `tipo_acao`...).
    - Servidor: `src/chamadas.ts:416-450` (`id`, `tipo`, `status`, `criado_por`, `participantes[]`); abril `api.pas@56e7cc0:2445-2591`.
    - Tela `HistoricoChamadasActivity.kt:91` recebe ids/tipos zerados.

12. **[ABRIL] Código morto com contrato errado (quebra latente se for ligado):**
    - `POST /alterar-senha` usa `senha_nova` em vez de `senha` (`UsuarioRequest.kt:23-26` × `esquemas.ts:30`).
    - `PATCH /usuario` sem `id` (`UsuarioRequest.kt:15-20` × `esquemas.ts:37`).
    - `POST /mensagem/reproduzir` com `{mensagem_id, conversa_id}` (`MensagemRepository.kt:143-146` × `esquemas.ts:66`).
    - `PATCH /sip` sem `id` (`Sip.kt:19-27` × `esquemas.ts:109-120`).
    - `GET /mensagem/status` espera status por usuário (`MensagemExtras.kt:48-54` × `mensagens.ts:655-662`).
    - `GET /pesquisar` espera `PesquisaResultado` e recebe mensagens (`MensagemExtras.kt:57-65` × `mensagens.ts:398`).
    - `GET /anexos` (`AnexoListagem.kt` × `anexos.ts:130-168`).
    - `GET /conversa/usuarios` → `List<Contato>` (`ConversaApi.kt:141-145` × `conversas.ts:203-220`).

### 9.2 QUEBRAS PROVÁVEIS

1. **Porta/URL padrão errada (4430 → 443).**
   - O nginx de desenvolvimento passou a escutar 443 (`bin/nginx/desenvolvimento/servidor.conf:2`; antes 4430, `nginx.conf@56e7cc0:28`, mudança em `97f9341`).
   - O Android tem `https://192.168.2.5:4430/api/` e `.../webrtc` fixos (`app/build.gradle.kts:26-27`); REST, WS e WHIP/WHEP derivam dessa URL (`MainActivity.kt:462-477`, `ChamadaService.kt:353-360`).
   - Quebra com a configuração padrão; funciona se a URL for corrigida em `ConfigApiActivity`. Em produção a porta é 80 atrás da borda TLS.

2. **Vídeo das chamadas do Android não gravado (VP8).**
   - O MediaMTX não grava VP8 (`mediamtx.docker.yml:31`); o web força H264/VP9 (`conversa-web/src/stores/call.ts:352-365`).
   - O Android não define preferência (`WebRTCManager.kt:108`).
   - Depende da ordem de codecs da oferta do libwebrtc no aparelho; só se confirma executando. Também depende de o item 9.1-1 ser resolvido antes.

3. **Eventos 53/54 para o próprio usuário.**
   - Desde `95430ba` (`chamadas.ts:195,210`), quem recusa/entra recebe o próprio 53/54.
   - `ChamadaRepository.kt:268-272` emite `CHAMADA_RECUSADA` para a UI sem checar se `peerId` é o próprio usuário → possível toast/estado "recusada" indevido na tela de quem recusou ou em outro aparelho da mesma conta.
   - Em compensação, um segundo aparelho do mesmo usuário poderia usar 54/53 próprios para parar de tocar, e o Android não faz isso.
   - No caminho do `SocketService` (`SocketService.kt:335-349`) a intent `ACTION_USUARIO_RECUSOU` não é tratada por `ChamadaService.onStartCommand` (`:207-233`), então o efeito depende de qual callback está registrado por último.

4. **403 novos em `digitando`/`gravando`/`reacao`/`dispositivo`.**
   - Agora exigem participação/posse (`conversas.ts:237-240`, `mensagens.ts:727`, `usuarios.ts:85-95`).
   - Em fluxos normais não deve ocorrer; erros são silenciados no Android (`ChatActivity.kt:268`).

5. **`criarConversa` + `adicionarUsuarioConversa`.**
   - Agora 200 ao incluir a si mesmo (antes erro); o Android ignorava a resposta, então só melhora.
   - A conversa 1:1 é criada com `descricao: ""` (`ChatActivity.kt:614`); o servidor usa `nullif(trim(descricao),'')` e cai no nome do destinatário — sem problema.
   - `inserida` enviado é ignorado (o servidor usa `current_timestamp` UTC).

6. **Limite de login no nginx (10/min, burst 5).**
   - `nginx.conf:97-100`; respostas excedentes vêm com status 503 e corpo HTML.
   - Login automático repetido (`LoginActivity.kt:57`) em loop de reconexão pode bater no limite; o Android trata qualquer não-2xx como falha de login.

7. **Push sem prioridade Android.**
   - Mesmo corrigindo os itens 9.1-2 e 9.1-6, `fcm.ts:31-35` não define `android.priority = "high"`.
   - Mensagens só-`data` com prioridade normal podem ser adiadas em Doze. Não há push de chamada em nenhuma versão.

8. **Mensagens com conteúdo tipo 5, 6 ou 7 aparecem vazias no Android.**
   - Tipo 7 (figurinha) é novo (`315464b`); 5 e 6 já existiam.
   - `MensagensAdapter.kt:84-126` só procura tipos 1–4, então a mensagem-resumo de chamada (tipo 6, gerada quando há `conversa_id` — o Android não envia) e figurinhas mandadas pelo web aparecem como balões vazios.

9. **Conversas arquivadas e fixadas.**
   - `GET /conversas` devolve arquivadas com `arquivada_em` (`conversas.ts:89`), e o filtro é do cliente.
   - O Android as mostra na lista e não respeita `fixada_ordem`. Não quebra o parse.

10. **Não é possível determinar sem executar:**
    - o efeito da troca do WebSocket Bird → Bun nos fechamentos com código ≠ 1000 e na reconexão (`SocketManager.kt:188-200`);
    - se o certificado mkcert de DEV continua aceito em build release (o `trustAll` só existe em DEBUG).

---

Arquivo gerado por auditoria somente leitura; nenhum arquivo do servidor ou do Android foi modificado.
