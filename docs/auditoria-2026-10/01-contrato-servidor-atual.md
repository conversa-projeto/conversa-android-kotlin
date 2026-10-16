# Contrato atual do servidor Conversa (referência para o cliente Android Kotlin)

> Auditoria de 2026-10-06. Fonte: repositório `conversa` (servidor Bun + Elysia), último commit lido 🆕 **`ef13c22`** ("Bots de IA com ferramentas e base de conhecimento, permissões de uso e cadastro pelo administrador", 2026-10-15 22:42), atualizado a partir de `7f670c3`, em 2026-10-13 de `5cad911`/`779c8ed` (este só mexe no `bin/iniciar-desenvolvimento.bat`), em 2026-10-15 de `12cf0cf` (commits `6d20ff7` e `e25c4db`) e, 🆕 em 2026-10-15 (noite), de `e25c4db` (commit `ef13c22`). Migrações aplicadas até a versão 🆕 **52** (`migracoes/000.sql` … `052.sql`). Mudanças desde `7f670c3` estão marcadas com 🆕 (quando não é o `8031fa5`, o commit vem junto) e resumidas na §20.
>
> 🆕 Números de linha: as citações `arquivo:linha` sem 🆕 foram lidas em `7f670c3`/`5cad911`. Os commits de outubro (`b8ea0c2` … `e25c4db`) deslocaram as linhas de `rotas.ts`, `mensagens.ts`, `websocket.ts`, `chamadas.ts`, `conversas.ts`, `usuarios.ts`, `parametros.ts` e 🆕 `atividades.ts`; 🆕 o `ef13c22` deslocou de novo `rotas.ts`, `mensagens.ts`, `atividades.ts`, `usuarios.ts`, `permissoes.ts`, `chamadas.ts`, `conversas.ts`, `parametros.ts`, `esquemas.ts` e `docker-compose.yml`. Nas partes novas, a referência é pelo nome da função.
>
> Todas as referências `arquivo:linha` são relativas à raiz do repositório do servidor (`C:\Users\danie\Desktop\GIT\conversa-projeto\conversa`). Quando algo vem do cliente web de referência (`conversa-web`), isso é dito explicitamente: o servidor não define, a página é que usa assim.
>
> Este documento é a fonte da verdade do contrato. Onde houver dúvida, está escrito **"não confirmado"**.

---

## Sumário

1. [Arquitetura, serviços, portas e URLs](#1-arquitetura-serviços-portas-e-urls)
2. [Autenticação](#2-autenticação)
3. [Formato de erro e validação](#3-formato-de-erro-e-validação)
4. [Tabela de TODAS as rotas REST](#4-tabela-de-todas-as-rotas-rest)
5. [Detalhe das rotas por área](#5-detalhe-das-rotas-por-área)
6. [WebSocket](#6-websocket)
7. [Push FCM e dispositivos](#7-push-fcm-e-dispositivos)
8. [Anexos / MinIO / transcrição](#8-anexos--minio--transcrição)
9. [Chamadas (ciclo de vida, ICE/TURN, MediaMTX WHIP/WHEP, gravação, sinal, chat)](#9-chamadas)
10. [Mensagens](#10-mensagens)
11. [Conversas](#11-conversas)
12. [Atividades](#12-atividades)
13. [Permissões e parâmetros do sistema](#13-permissões-e-parâmetros-do-sistema)
14. [SIP](#14-sip)
15. [Presença, digitando, gravando](#15-presença-digitando-gravando)
16. [Modelo de dados (tabelas)](#16-modelo-de-dados-tabelas)
17. [Tarefas periódicas do servidor](#17-tarefas-periódicas-do-servidor)
18. [O que o cliente precisa implementar (checklist)](#18-o-que-o-cliente-precisa-implementar-checklist)
19. [Pegadinhas para clientes](#19-pegadinhas-para-clientes)
20. [Histórico de atualizações deste documento](#20-histórico-de-atualizações-deste-documento)

---

## 1. Arquitetura, serviços, portas e URLs

### 1.1 Serviços (docker-compose)

| Serviço | Imagem | Porta interna | Publicada | Função | Referência |
|---|---|---|---|---|---|
| `postgres` | postgres:15-alpine | 5432 | `127.0.0.1:5433` | Banco. `TZ: UTC` | `docker-compose.yml:31-46` |
| `minio` | quay.io/minio/minio | 9000 (S3), 9001 (console) | `127.0.0.1:9000`, `127.0.0.1:9001` | Armazenamento dos anexos. Credenciais geradas em `/dados/minio-usuario` e `/dados/minio-senha` | `docker-compose.yml:52-79` |
| `coturn` | coturn/coturn | 3478/tcp | `3478:3478/tcp` (pública) | TURN só TCP, sem TLS (TLS na borda em produção). IP fixo `172.30.0.10` na rede `conversa-rtc` | `docker-compose.yml:86-110`, `bin/coturn/turnserver.conf` |
| `mediamtx` | `bin/mediamtx/Dockerfile` (bluenviron/mediamtx:1.16.2) | 8889 (WebRTC HTTP), 8189/udp (mídia, interna) | `127.0.0.1:8889` | Servidor WebRTC (WHIP/WHEP) e gravação das chamadas. IP fixo `172.30.0.20` | `docker-compose.yml:116-130`, `bin/mediamtx/mediamtx.docker.yml` |
| 🆕 `ef13c22` `qdrant` | qdrant/qdrant:v1.13.6 | 6333 (HTTP) | `127.0.0.1:6333` | Banco de vetores da **base de conhecimento dos bots** (§13.9): os trechos dos documentos com os embeddings. A API grava e busca por HTTP (`CONVERSA_QDRANT`); o painel fica em `/dashboard`. O cliente não fala com ele | `docker-compose.yml:136-146` (em `ef13c22`) |
| `api` | Bun + Elysia | 8080 (HTTP + WebSocket) | `127.0.0.1:8080` | API REST em `/api` e WebSocket em `/ws/`. 🆕 `ef13c22`: depende também do `qdrant` (`service_started`) | `docker-compose.yml:134-160`, `src/servidor.ts:31` |
| `nginx` | nginx:1.28-alpine | 443 (dev) / 80 (prod) | `${CONVERSA_PORTA:-443}` | Ponto de entrada único | `docker-compose.yml:165-183`, `bin/nginx/nginx.conf` |

Volumes: ~~`pgdata` (banco), `minio` (anexos)~~ 🆕 `ef13c22`: `conversa-postgres` (banco), `conversa-minio` (anexos), `conversa-qdrant` (trechos da base de conhecimento; dá para refazer com "Reindexar tudo"), `conversa-dados` (`/dados`: pepper das senhas, credenciais do MinIO, segredo do TURN), `conversa-gravacoes` (`/gravacoes`) — 🆕 `docker-compose.yml:212-222` (em `ef13c22`).
- 🆕 `ef13c22` **Instalação antiga** (volumes `pgdata` e `minio`): o `bin\renomear-volumes.bat` (rodar uma vez, **antes** de subir a versão nova) derruba os containers, copia `pgdata` → `conversa-postgres` e `minio` → `conversa-minio`, confere a cópia (arquivos e bytes) e só então apaga os antigos. Se o volume novo já tem dados, não copia e mantém o antigo (`SETUP.md`, passo 4).
- 🆕 Consequência para o desenvolvimento: subir o `docker-compose.yml` novo **sem** o script cria um banco e um MinIO **vazios** (as contas de teste do emulador somem até rodar o script).

### 1.2 URLs públicas (tudo na mesma origem, via nginx)

Todas as URLs são relativas à origem que o cliente usou (ex.: `https://192.168.0.10` em desenvolvimento ou `https://SEU_DOMINIO` em produção). O servidor monta URLs de MinIO e TURN a partir do **cabeçalho `Host`** e do `X-Forwarded-Proto` de cada requisição (`src/contexto.ts:9-12`), então elas sempre apontam para o mesmo host que o cliente chamou.

| Caminho público | Destino | Observações | Referência |
|---|---|---|---|
| `POST /api/login` | `api:8080` | Limite de taxa: 10 req/min por IP, `burst=5 nodelay` (excedente recebe resposta do nginx, por padrão **503**, não JSON) | `bin/nginx/nginx.conf:74`, `:97-100` |
| `/api/*` | `api:8080` | timeouts 300 s. **`client_max_body_size 1m`** (corpo JSON máximo de 1 MB) | `bin/nginx/nginx.conf:24`, `:102-106` |
| `/api/docs` | api | Documentação Scalar; JSON OpenAPI em `/api/docs/json` (útil para gerar o cliente) | `src/app.ts:49-55` |
| `/ws/` (com barra final) | `api:8080` | WebSocket; timeouts 3600 s. `/ws` sem barra **não casa** com a `location` e cai na página | `bin/nginx/nginx.conf:109-114`, `src/websocket.ts:55` |
| `/storage/*` | `minio:9000/` (prefixo removido) | URLs pré-assinadas do MinIO; upload até 1024 MB; `Content-Security-Policy: sandbox` (exceto PDF) | `bin/nginx/nginx.conf:121-133`, `src/minio.ts:7`, `:33-46` |
| `/webrtc/*` | `mediamtx:8889/` (prefixo removido) | WHIP/WHEP do MediaMTX. Sem autenticação | `bin/nginx/nginx.conf:135-140` |
| `/` | Vite (dev) ou `bin/web` (prod) | Página web (SPA). Figurinhas Lottie em `/figurinhas/<pacote>/<nome>.json` (servidas pela página, ver §10.6) | `bin/nginx/desenvolvimento/pagina.conf`, `bin/nginx/producao/pagina.conf` |
| TURN | `turn:<host>:3478?transport=tcp` (dev) ou `turns:<host>:<CONVERSA_TURN_PORTA>?transport=tcp` (prod) | Fora do nginx: dev vai direto ao coturn; prod passa pela borda com TLS | `src/chamadas.ts:576-594`, `SETUP.md:91-95,130` |
| Gravações | **não expostas** | Ficam no volume `conversa-gravacoes`; nenhum endpoint as serve | `README`/`SETUP.md:197-210` |

### 1.3 Desenvolvimento x produção, TLS

- **Desenvolvimento**: o nginx escuta em **443 com TLS** usando `bin/cert/cert.pem`/`key.pem`, gerados pelo `bin/setup-cert.bat` com **mkcert** (`bin/nginx/desenvolvimento/servidor.conf:1-7`, `SETUP.md:52`). HTTP na 443 recebe redirect 301 para HTTPS. O Android precisa confiar na CA raiz do mkcert (ex.: `network_security_config` com CA do usuário/da app em build de debug). A página vem do Vite.
- **Produção**: o nginx escuta em **80 sem TLS**; uma borda (nginx externo) termina o TLS e repassa com `X-Forwarded-Proto: https` (`bin/nginx/producao/servidor.conf`, `SETUP.md:101-130`). Para chamadas, a borda também precisa de uma porta TCP pública com TLS repassada para `:3478` (`CONVERSA_TURN_PORTA`, ex.: 8443) — `SETUP.md:91-95`.
- O protocolo usado nas URLs geradas vem de `X-Forwarded-Proto` (padrão `https` se ausente) — `src/contexto.ts:10`. O nginx passa `$proto_original` (`bin/nginx/nginx.conf:35-38`, `:93`).
- Variáveis de ambiente da API: `CONVERSA_SERVER`, `CONVERSA_PORT`, `CONVERSA_DATABASE`, `CONVERSA_USERNAME`, `CONVERSA_PASSWORD`, `CONVERSA_PORTA_HTTP` (8080), `CONVERSA_S3_INTERNO`, `CONVERSA_TURN_PORTA`, `CONVERSA_DADOS` (`/dados`), `CONVERSA_GRAVACOES` (`/gravacoes`) — `src/configuracao.ts:57-86`. 🆕 `ef13c22`: + `CONVERSA_QDRANT` (padrão `http://127.0.0.1:6333`; no compose, `http://qdrant:6333`) — `configuracao.ts` (`definirAmbiente`).

### 1.4 Como a API trata cada requisição

- Toda rota autenticada roda numa conexão reservada do Postgres com `app.usuario_id` = id do token (`src/rotas.ts:20-23`, `src/banco.ts:69-77`). Isso alimenta as colunas `criado_por` (default) e os gatilhos de auditoria (`migracoes/013.sql`). **Não há RLS** (row level security); toda autorização é feita no código (`src/autorizacao.ts`, `src/permissoes.ts`).
- A sessão do Postgres roda em UTC (`src/banco.ts:18`).

---

## 2. Autenticação

### 2.1 Login — `POST /api/login` (pública)

Rota: `src/rotas.ts:33-36`; lógica: `src/usuarios.ts:19-66`; esquema: `src/esquemas.ts:29`.

Corpo:
```json
{ "login": "ana", "senha": "segredo", "dispositivo_id": 12 }
```
| Campo | Tipo | Obrigatório | Observação |
|---|---|---|---|
| `login` | string | sim | Comparado sem diferenciar maiúsculas (`lower(login)`) — `src/usuarios.ts:30` |
| `senha` | string | sim | bcrypt com pepper; senha legada em texto puro é aceita e convertida — `src/usuarios.ts:35-45` |
| `dispositivo_id` | integer \| null | não | Id do dispositivo salvo no aparelho. Só é reaproveitado se for do próprio usuário; senão um novo é criado — `src/usuarios.ts:47-60` |

Resposta 200 (ordem real das chaves):
```json
{
  "id": 7,
  "nome": "Ana Souza",
  "email": "ana@x.com",
  "telefone": null,
  "avatar_identificador": "e3b0c442...(sha256 hex)" ,
  "dispositivo": { "id": 12, "nome": "desconhecido", "modelo": "desconhecido", "versao_so": "desconhecido", "plataforma": "desconhecido", "ativo": true },
  "token": "eyJhbGciOi..."
}
```
- **Não** vem `login` nem `avatar_url`. `avatar_identificador` é o `anexo.identificador` do avatar (ou `null`); para obter a URL chame `GET /api/anexo?identificador=...` (§8).
- `dispositivo` **não** traz `token_fcm`. Em login novo, o dispositivo é criado com `"desconhecido"` em todos os campos (`src/usuarios.ts:53-60`) — o cliente deve depois chamar `PATCH /api/dispositivo` (§7.3).

Erros (todos `{ "error": "..." }`):
| Status | `error` | Quando |
|---|---|---|
| 401 | `Usuário não encontrado!` | login inexistente — `src/usuarios.ts:31-33` |
| 401 🆕 `ef13c22` | `Bot não entra pelo login.` | o login é de um **bot** (§13.7); conferido antes da senha — `usuarios.ts` (`login`, `ehBot`) |
| 401 | `Senha incorreta!` | `src/usuarios.ts:38-40` |
| 401 | `Seção Encerrada!` (sic) | dispositivo com `ativo=false` — `src/usuarios.ts:62-64` (nenhuma rota desativa dispositivo hoje) |
| 400 | mensagem de validação | campo faltando/tipo errado (§3) |
| 503 (nginx) | página HTML do nginx | mais de 10 logins/min por IP — `bin/nginx/nginx.conf:74,98` |

### 2.2 JWT

- Biblioteca `@elysiajs/jwt` (jose), segredo = parâmetro `jwt_token` da tabela `parametros` (gerado aleatório se vazio ou igual ao valor público da migração 015) — `src/autenticacao.ts:6`, `src/configuracao.ts:151-158`. Algoritmo: padrão do plugin (HS256) — **não fixado explicitamente no código**.
- Claims (`src/rotas.ts:35`): `sub` = id do usuário **como string** (ex.: `"7"`), `iss` = `"conversa.login"`, `iat` (numérico), `exp` = iat + **12 horas** (`src/autenticacao.ts:6`).
- Validação: precisa ter `exp`, `iat` e `sub` inteiro > 0 — `src/autenticacao.ts:14-24`. `iss` **não** é conferido.
- **Não existe rota de refresh nem de logout.** Após 12 h, qualquer rota responde 401 e o cliente deve logar de novo (guardar credenciais de forma segura ou pedir ao usuário).
- Trocar o parâmetro `jwt_token` invalida todos os tokens.

### 2.3 Como enviar o token

- HTTP: cabeçalho `Authorization: Bearer <token>` (a palavra `Bearer` é case-insensitive; separador é um espaço) — `src/autenticacao.ts:27-30`, `src/rotas.ts:42`.
- WebSocket: **não** usa cabeçalho. A primeira mensagem do cliente deve ser `{"tipo":1,"token":"<jwt>"}` (§6.2).

### 2.4 Token inválido

| Situação | Status | Corpo |
|---|---|---|
| Sem cabeçalho / sem `Bearer` | 401 | `{"error":"Token não informado"}` — `src/autenticacao.ts:15-17` |
| Assinatura inválida, expirado, sem `exp`/`iat`, `sub` inválido | 401 | `{"error":"Token inválido ou expirado"}` — `src/autenticacao.ts:20-22` |

Observação: rotas que validam "senha atual" respondem **400** (não 401) justamente para o cliente não interpretar como sessão expirada (`src/autorizacao.ts:61-68`). Regra prática para o Android: **401 = deslogar**.

### 2.5 Cadastro — `PUT /api/usuario` (pública)

Rota `src/rotas.ts:38`; lógica `src/usuarios.ts:97-107`; esquema `src/esquemas.ts:36`.

Corpo: `{ "nome": string, "login": string, "email": string, "telefone"?: string|null, "senha": string }`.

Regras:
- `login` único sem diferenciar maiúsculas → 400 `Login já cadastrado!`.
- `senha` com 4 a 72 caracteres → 400 `A senha deve ter entre 4 e 72 caracteres!`.
- `email` tem constraint `unique` no banco (`migracoes/000.sql`) mas **não é tratado**: e-mail repetido gera **500** com a mensagem do Postgres.
- Limites de coluna: `nome` 100, `login` 50, `email` 100, `telefone` 50 (`migracoes/000.sql`). Excedente = 500.

Resposta: a linha `usuario` inteira **sem** `senha`: `{ id, nome, login, email, telefone, avatar_anexo_id, criado_em, criado_por, atividades_vistas_em }` (`src/usuarios.ts:105-106`, `src/comum.ts:47-50`). **Não** devolve token: é preciso chamar `/api/login` depois.

~~Cadastro é público e qualquer um pode se cadastrar (não há convite nem aprovação).~~ 🆕 `ef13c22` (`usuarios.ts`, `incluirUsuario`, `criarConta`):
- O cadastro pela tela de login só funciona com o parâmetro `cadastro_aberto` ligado (padrão: ligado, migração 048). Fechado → **403** `O cadastro está fechado: peça a um administrador para criar sua conta.` (depois da validação do esquema e antes das regras de login e senha).
- Antes de mostrar "Cadastre-se", consulte `GET /api/cadastro` (§2.6).
- A conta nova ganha **todas as permissões de uso** (§13.1), mesmo fora do modo aberto (`concederPermissoesUso`).

### 2.6 🆕 Cadastro aberto ou fechado e cadastro pelo administrador (`ef13c22`)

**`GET /api/cadastro`** (pública, sem token) — `parametros.ts` (`cadastroSistema`). → `{ "aberto": true|false }` = parâmetro `cadastro_aberto` (`'0'` fecha; qualquer outro valor abre). O web só mostra "Cadastre-se" com `aberto: true`. Não há evento quando muda: consulte ao abrir a tela de login.

**`PUT /api/usuario/novo`** (token; permissão de administração `usuarios`, senão 403 `Acesso negado!`) — `usuarios.ts` (`cadastrarUsuario`). Funciona **mesmo com o cadastro fechado**.
- Corpo: `{ "nome": string, "login"?: string, "email"?: string, "telefone"?: string|null, "senha"?: string, "avatar_anexo_id"?: int|null, "bot"?: bool, "instrucoes"?: string }`.
- **Pessoa** (`bot` ausente ou `false`): `nome`, `login`, `email` e `senha` obrigatórios (vazios → 400 `Preencha nome, login, e-mail e senha.`); mesmas regras do cadastro (`Login já cadastrado!`, senha de 4 a 72; e-mail repetido continua dando 500). Ganha todas as permissões de uso.
- **Bot** (`bot: true`): só `nome` e `instrucoes`; cria o bot como `PUT /bot` (§13.7) — exige a IA configurada (400) e instruções de 1 a 8000 caracteres (400).
- `avatar_anexo_id`: id numérico de um anexo **tipo 2** (imagem) já enviado (§8.2); outro tipo ou inexistente → 400 `Avatar inválido: envie uma imagem.` (diferente do `PATCH /usuario`, que não confere).
- Resposta: `{ "id": int, "nome": string, "login": string, "bot": bool }` (não é a linha inteira; sem token).

---

## 3. Formato de erro e validação

Tratador global: `src/app.ts:57-77`. **Todo erro é JSON `{ "error": "<mensagem em português>" }`**.

| Origem | Status | Mensagem |
|---|---|---|
| `ErroHttp` lançado pelo código | 400/401/403/404/409 | mensagem específica (`src/erros.ts:13-19`) |
| 🆕 `ef13c22` Falta de permissão | 403 | permissão **de uso**: `Você não tem permissão para: <descrição da permissão em minúsculas>.` (ex.: `Você não tem permissão para: enviar imagens.`); permissão de administração: `Acesso negado!` — `permissoes.ts` (`validarPermissoes`). Com várias faltando, vem só a primeira (ordem do `permissao.id`). Lista em §13.1 |
| Validação de esquema (TypeBox) | 400 | `Campo "x" é obrigatório e não foi informado!` / `O valor de "x" deve ser do tipo integer!` / `Valor inválido em "x": ...` / `Dados inválidos em "body"` — `src/app.ts:25-39` |
| Rota inexistente | 404 | `Rota GET:/api/xyz não encontrada` |
| JSON malformado | 400 | `Corpo da requisição inválido!` (o teste aceita qualquer string) |
| Qualquer outro erro (inclusive violação de constraint do Postgres) | 500 | `error.message` cru (ex.: `duplicate key value violates unique constraint ...`) |

Validação (`src/esquemas.ts:1-14`):
- Corpo e query são validados **antes** do handler. Campos extras no corpo são ignorados (não geram erro).
- Query: números chegam como texto e são convertidos (`?conversa=5` → 5). Valores com `default` podem ser omitidos.
- `ouNulo(x)` = campo opcional que aceita `null`; `opcional(x)` = opcional mas **não aceita `null`**.

---

## 4. Tabela de TODAS as rotas REST

Prefixo `/api` em todas (`src/rotas.ts:28`). "Token" = exige `Authorization: Bearer`. Total: ~~82 rotas (2 públicas + 80 autenticadas)~~ 🆕 `ef13c22`: **109 rotas** em `src/rotas.ts` (**3 públicas + 106 autenticadas**; as 5 de enquete 🆕 estão como 65–69, as 13 de outubro 🆕 `b8ea0c2`/`ea02f57`/`12cf0cf`/`6d20ff7` como 70–82, na ordem dos commits, e as 27 do 🆕 `ef13c22` como 83–109, na ordem de `rotas.ts`), mais a documentação `/api/docs` e `/api/docs/json` (públicas, do plugin OpenAPI) e o WebSocket `/ws/`. Linhas que mudaram de comportamento em outubro têm 🆕 e o commit no `#`.

🆕 `ef13c22` **Permissões de uso:** a partir deste commit, várias rotas do chat respondem **403** quando falta ao usuário a permissão de uso correspondente (coluna "Permissão / regra"; lista completa em §13.1). Fora do modo aberto, toda conta tem todas as de uso até um administrador retirar; bots começam só com `emoji`, `responder` e `reagir`.

Legenda de efeitos: **WS n** = evento WebSocket tipo n (§6.4); **FCM** = push.

| # | Método | Caminho | Auth | Query | Corpo | Resposta (resumo) | Permissão / regra | Efeitos | Ref. rota |
|---|---|---|---|---|---|---|---|---|---|
| 1 🆕 `ef13c22` | POST | `/login` | pública | – | `login`, `senha`, `dispositivo_id?` | usuário + `dispositivo` + `token` | 🆕 bot → 401 `Bot não entra pelo login.` | cria dispositivo | `rotas.ts:33` |
| 2 🆕 `ef13c22` | PUT | `/usuario` | pública | – | `nome`, `login`, `email`, `telefone?`, `senha` | linha usuário sem senha | 🆕 cadastro fechado → 403 (§2.5) | 🆕 concede todas as permissões de uso | `rotas.ts:38` |
| 3 🆕 `ef13c22` | POST | `/alterar-senha` | token | – | `senha_atual`, `senha` | `{}` | 🆕 permissão `senha` (403); senha atual correta (400 se errada) | – | `rotas.ts:46` |
| 4 | PATCH | `/dispositivo` | token | – | `id`, `nome?`, `modelo?`, `versao_so?`, `plataforma?`, `token_fcm?` | linha dispositivo | dono do dispositivo (403) | – | `rotas.ts:51` |
| 5 | PUT | `/dispositivo/usuario` | token | `dispositivo_id` | – | linha `dispositivo_usuario` | dono (403) | – | `rotas.ts:53` |
| 6 🆕 `b8ea0c2` `ef13c22` | PATCH | `/usuario` | token | – | `id`, `nome?`, `email?`, `telefone?`, `avatar_anexo_id?`, 🆕 `mostrar_visto_em?`, `mostrar_na_conversa?`, `aparecer_offline?` (bool) | linha usuário sem senha (🆕 com `visto_em` e as 3 preferências) | só o próprio (`id` = token, 403); 🆕 `ef13c22` uma permissão por chave **enviada**: `nome` → `nome`; `email` ou `telefone` → `email`; `avatar_anexo_id` → `avatar`; as 3 de presença → `privacidade` (403, §5.1) | 🆕 preferência de presença vale na hora (WS 60/63, §15) | `rotas.ts:56` |
| 7 🆕 `ef13c22` | DELETE | `/usuario` | token | `id` | – | linha usuário excluída sem senha | só o próprio (403); com histórico 409 | apaga dispositivos (🆕 e permissões, numa transação: com 409 nada sai) | `rotas.ts:58` |
| 8 | PUT | `/usuario/contato` | token | `relacionamento_id` | – | linha `usuario_contato` | – | – | `rotas.ts:60` |
| 9 | DELETE | `/usuario/contato` | token | `id` | – | linha excluída | dono do contato (403) | – | `rotas.ts:63` |
| 10 🆕 `ef13c22` | GET | `/usuario/contatos` | token | – | – | `[{id,nome,login,email,telefone}]` de **todos** os usuários — 🆕 `+ bot: bool` no fim; bots excluídos ficam de fora | – | – | `rotas.ts:65` |
| 11 🆕 `b8ea0c2` | GET | `/contatos/online` | token | – | – | `number[]` (ids online) — 🆕 pela presença **visível** (ativo ou ausente; quem aparece offline fica de fora) | – | – | `rotas.ts:67` |
| 12 🆕 `ef13c22` | PUT | `/conversa` | token | – | `descricao?`, `tipo?` | linha conversa | 🆕 `tipo: 2` pede `grupo` (403); direta não pede nada | criador vira membro | `rotas.ts:71` |
| 13 🆕 `b8ea0c2` | PATCH | `/conversa` | token | – | `id`, `descricao?` (🆕 opcional), 🆕 `avatar_anexo_id?: int\|null`, 🆕 `emoji?: string\|null` | linha conversa (🆕 com `avatar_anexo_id`, `emoji`) | membro (403); 🆕 imagem/emoji só em grupo (400) | 🆕 **WS 40** aos outros membros | `rotas.ts:73` |
| 14 | DELETE | `/conversa` | token | `id` | – | linha excluída | membro (403) | ver §11.6 (na prática 500) | `rotas.ts:75` |
| 15 🆕 `b8ea0c2` | GET | `/conversas` | token | – | – | lista de conversas (§11.2) — 🆕 com `emoji`, `chamada` e imagem do grupo em `avatar_url` | – | – | `rotas.ts:77` |
| 16 | PATCH | `/conversa/fixadas` | token | – | `conversas: int[]` | `{conversas}` | – | – | `rotas.ts:79` |
| 17 | PATCH | `/conversa/arquivada` | token | – | `conversa`, `arquivada: bool` | `{id, arquivada}` | membro (403) | desfixa ao arquivar | `rotas.ts:82` |
| 18 | GET | `/conversa/usuarios` | token | `conversa` | – | `[{id,usuario_id,nome,avatar_url}]` | não membro recebe `[]` | – | `rotas.ts:85` |
| 19 | PUT | `/conversa/usuario` | token | – | `usuario_id`, `conversa_id` | linha `conversa_usuario` | membro (403) | **WS 40** aos membros (exceto quem adicionou) | `rotas.ts:88` |
| 20 | DELETE | `/conversa/usuario` | token | `id` (= `conversa_usuario.id`) | – | linha excluída | só a própria participação (403) | – | `rotas.ts:90` |
| 21 | POST | `/conversa/digitando` | token | – | `id` (conversa) | `{}` | membro (403) | **WS 4** aos outros membros | `rotas.ts:92` |
| 22 | POST | `/conversa/gravando` | token | – | `id` (conversa) | `{}` | membro (403) | **WS 5** aos outros membros | `rotas.ts:97` |
| 23 🆕 `b8ea0c2` `12cf0cf` `6d20ff7` | PUT | `/mensagem` | token | – | `conversa_id`, `visivel_em?`, `conteudos[]`, `mensagem_referencia?`, 🆕 `pede_confirmacao?: bool`, 🆕 `6d20ff7` `confirmacao_usuarios?: int[]` (quem precisa confirmar; sem a lista, todos os outros membros), 🆕 `postagem?: {titulo?: string\|null}` (título ≤ 150), 🆕 `postagem_id?: int\|null` | linha mensagem (+`mensagem_referencia`) — 🆕 com `pede_confirmacao`, `postagem`, `postagem_titulo`, `postagem_id` | membro (403); 🆕 postagem só em grupo e regras da resposta (400, §10.15); 🆕 `ef13c22` permissões de uso pelo conteúdo e pelas opções (403, §10.3); 🆕 `6d20ff7` lista de confirmação fora dos membros ou vazia (400, §10.14) | **WS 2**, **FCM**, **WS 61** (resposta/menção); agendada só ao sair; 🆕 resposta de postagem: **WS 64** aos outros membros + atividade; 🆕 `ef13c22` atividade **tipo 5** (+ WS 61) a quem precisa confirmar; chama os **bots** da conversa (§13.7) | `rotas.ts:104` |
| 24 🆕 `ef13c22` | DELETE | `/mensagem` | token | `id` | – | `{id, conversa_id, excluida_em}` ou linha apagada | só o autor (403); 🆕 permissão `ocultar` (403) | **WS 3** aos outros, **WS 61** | `rotas.ts:106` |
| 25 🆕 `12cf0cf` `ef13c22` | GET | `/mensagens` | token | `conversa`, `mensagemreferencia=0`, `mensagensprevias=0`, `mensagensseguintes=0` | – | `MensagemResposta[]` (§10.2) — 🆕 **sem** as respostas de postagem; 🆕 `ef13c22` `conteudos[]` com `duracao` e `intensidade` | membro (403) | marca **recebida** (🆕 inclusive respostas de postagem) + **WS 3** aos outros | `rotas.ts:108` |
| 26 🆕 `e25c4db` | POST | `/mensagem/visualizar` | token | – | `conversa`, `mensagem` | `{sucesso:true}` | membro (403) | **WS 3** aos outros; 🆕 **WS 61** ao próprio usuário se marcou agora uma mensagem com atividade de resposta/menção ligada a ela (§12) | `rotas.ts:111` |
| 27 | POST | `/mensagem/reproduzir` | token | – | `conversa`, `mensagem` | `{sucesso:true}` | membro (403) | **WS 3** aos outros | `rotas.ts:114` |
| 28 | GET | `/mensagem/status` | token | `conversa`, `mensagem` (CSV de ids) | – | `[{conversa_id,mensagem_id,recebida,visualizada,reproduzida,excluida_em}]` | membro (403) | – | `rotas.ts:117` |
| 29 | GET | `/mensagem/status/detalhe` | token | `id` | – | `[{usuario_id,nome,recebida,visualizada,reproduzida}]` (datas) | só o autor (403); 404 | – | `rotas.ts:120` |
| 30 | GET | `/mensagens/novas` | token | `desde=''` (ISO) | – | `[{conversa_id,mensagem_id,ate}]` | – | – | `rotas.ts:123` |
| 31 🆕 `12cf0cf` `6d20ff7` | GET | `/pesquisar` | token | `conversa=0`, `texto=''` | – | `MensagemResposta[]` — 🆕 sem respostas de postagem; 🆕 `6d20ff7` com `conversa` > 0, também mensagens dos **chats das ligações** feitas nela (outro `conversa_id`, §10.11) | só conversas do usuário | – | `rotas.ts:127` |
| 32 🆕 `ef13c22` | PUT | `/mensagem/reacao` | token | – | `mensagem_id`, `emoji` | `{mensagem_id, emoji, acao}` | membro (403); 404; 🆕 permissão `reagir` (403), **também para tirar** a própria reação | **WS 7** aos outros, **WS 61** ao autor | `rotas.ts:130` |
| 33 | GET | `/anexo/existe` | token | `identificador` | – | `{existe:false}` ou `{existe:true,id,identificador,tipo,tamanho,upload_status}` | **nenhuma** | – | `rotas.ts:134` |
| 34 | GET | `/anexo` | token | `identificador` | – | `{url}` (GET assinado, 600 s) | **nenhuma** | pode marcar upload concluído | `rotas.ts:136` |
| 35 🆕 `ef13c22` | PUT | `/anexo` | token | – | `identificador`, `tipo`, `nome?`, `extensao?`, `tamanho`, 🆕 `duracao?: number\|null` (0..86400 s), `intensidade?: int[]\|null` (até 200 valores de 0 a 100) | §8.2 | – | cria registro; 🆕 áudio (4/5): grava duração e intensidade em `anexo_audio` (§8.4) | `rotas.ts:138` |
| 36 | POST | `/anexo/confirmar` | token | `identificador` | – | `{confirmado:true, upload_status:1}` | – | – | `rotas.ts:140` |
| 37 | GET | `/anexos` | token | `conversa=0`, `autor=0`, `direcao=''`, `tipos=''`, `antes=0`, `limite=0` | – | lista de anexos (§8.5) | conversa: membro (403) | – | `rotas.ts:142` |
| 38 | GET | `/anexo/transcricao` | token | `identificador` | – | `{status,texto,erro}` | áudio (4/5) em conversa do usuário (403) | – | `rotas.ts:146` |
| 39 | PUT | `/anexo/transcricao` | token | – | `identificador` | `{status,texto,erro}` | idem; 400 se transcritor não configurado | inicia job em 2º plano | `rotas.ts:149` |
| 40 🆕 `ef13c22` | PUT | `/chamada/iniciar` | token | – | `tipo?`, `conversa_id?`, `usuarios:[{id}]` | dados da chamada (§9.3) | 🆕 `tipo` 1 pede `chamada_audio`, `tipo` 2 pede `chamada_video` (403); sem `tipo`, com 3+ em `usuarios` (contando quem liga) vale 2 | **WS 51** aos convidados | `rotas.ts:154` |
| 41 | POST | `/chamada/cancelar` | token | – | `id` | `{id}` | participante (404) | status 6, resumo, chamada perdida, **WS 52** | `rotas.ts:156` |
| 42 🆕 `a40fd47` | POST | `/chamada/entrar` | token | – | `id` | `{id}` | participante (404) | **WS 54** (outros + próprio), entra no chat (WS 40 a si) — 🆕 só quando o chat é um grupo criado para a chamada; na conversa reaproveitada não inclui ninguém (§9.12; 🆕 `6d20ff7`: reaproveitamento só acontece em ligação feita dentro do chat de outra ligação) | `rotas.ts:158` |
| 43 | POST | `/chamada/recusar` | token | – | `id`, `nao_atendeu?` | `{id}` | participante (404) | **WS 53** (outros + próprio), resumo, chamada perdida | `rotas.ts:160` |
| 44 | POST | `/chamada/sair` | token | – | `id` | `{id}` | participante (404) | **WS 55**, resumo | `rotas.ts:163` |
| 45 | PUT | `/chamada/usuario` | token | – | `chamada_id`, `usuario_id` | `{id}` | participante (404) | **WS 51** a todos menos quem adicionou | `rotas.ts:165` |
| 46 | POST | `/chamada/finalizar` | token | – | `id` | `{id}` | participante (404) | status 4, resumo, chamada perdida, **WS 52** | `rotas.ts:168` |
| 47 🆕 `ef13c22` | GET | `/usuario/permissoes` | token | – | – | `string[]` — 🆕 inclui os códigos **de uso** (§13.1) | – | – | `rotas.ts:170` |
| 48 🆕 `ef13c22` | GET | `/permissoes` | token | – | – | `{permissoes, usuarios, modo_aberto}` — 🆕 `permissoes[]` com `uso: bool`; bots excluídos fora de `usuarios` | `permissoes` (403) | – | `rotas.ts:172` |
| 49 | PUT | `/permissao/usuario` | token | – | `usuario_id`, `codigo` | `{usuario_id, codigo}` | `permissoes` (403); 404 | – | `rotas.ts:174` |
| 50 🆕 `ef13c22` | DELETE | `/permissao/usuario` | token | `usuario_id`, `codigo` | – | `{usuario_id, codigo}` | `permissoes` (403); 400 último; 🆕 400 `Você não pode retirar as próprias permissões.` | – | `rotas.ts:177` |
| 51 🆕 `b8ea0c2` `ef13c22` | GET | `/parametros` | token | – | – | §13.2 (🆕 `ia_url`, `ia_modelo`, `ia_token_configurado`; 🆕 `ef13c22` `cadastro_aberto`, `ia_modelo_embeddings`) | `parametros` (403) | – | `rotas.ts:180` |
| 52 🆕 `b8ea0c2` `ef13c22` | PATCH | `/parametros` | token | – | §13.2 (🆕 `ia_url?`, `ia_modelo?`, `ia_token?` — o token só é gravado, nunca volta; 🆕 `ef13c22` `cadastro_aberto?: bool`, `ia_modelo_embeddings?`) | §13.2 | `parametros` (403) | recarrega config | `rotas.ts:182` |
| 53 🆕 `e25c4db` `ef13c22` | GET | `/atividades` | token | `antes=0`, `limite=30` (1..100) | – | `Atividade[]` (§12) — ~~🆕 `nova: false` também para resposta/menção já lida no chat~~ 🆕 `ef13c22`: a chave `nova` **saiu**; no lugar dela vem **`pendente`** (§12); tipo **5** (confirmação pedida) | – | – | `rotas.ts:184` |
| 54 🆕 `e25c4db` `ef13c22` | GET | `/atividades/novas` | token | – | – | `{quantidade}` — ~~🆕 sem contar resposta/menção já lida no chat (§12)~~ 🆕 `ef13c22`: quantas estão **pendentes**, só de conversas em que o usuário ainda é membro (§12) | – | – | `rotas.ts:186` |
| 55 🆕 `ef13c22` | POST | `/atividades/vistas` | token | – | – | `{vistas_em}` | – | ~~zera o contador~~ 🆕 só grava `atividades_vistas_em`: **não mexe mais** no contador nem em `pendente` (§12) | `rotas.ts:188` |
| 56 🆕 `a40fd47` `6d20ff7` | PUT | `/chamada/chat` | token | – | `id` (chamada) | `{conversa_id}` — ~~🆕 pode ser a própria conversa da ligação (até uma direta)~~ 🆕 `6d20ff7`: quase sempre um **grupo novo** (nome = nomes dos participantes, sem `Chamada: `); só reaproveita a origem quando a ligação foi feita dentro do chat de outra ligação | participante (404) | se criou o grupo: **WS 40** + **WS 57** `{acao:'chat'}`; 🆕 se reaproveitou a conversa: só **WS 57** `{acao:'chat'}` (§9.12) | `rotas.ts:190` |
| 57 | GET | `/chamada/dados` | token | `id` | – | dados da chamada (§9.3) | participante (404) | – | `rotas.ts:192` |
| 58 | GET | `/chamadas/pendentes` | token | – | – | chamadas tocando para o usuário (§9.6) | – | – | `rotas.ts:194` |
| 59 🆕 `a40fd47` | GET | `/chamadas` | token | `participante=0`, `de=''`, `ate=''` | – | histórico (§9.7) — 🆕 com `conversa_chat_id` | – | – | `rotas.ts:196` |
| 60 🆕 `6d20ff7` `ef13c22` | POST | `/chamada/video` | token | – | `id` | `{}` | participante (404); 🆕 `ef13c22` permissão `chamada_video` (403, depois do 404) | **WS 56** aos outros participantes; 🆕 grava `chamada.tipo = 2` se era 1 (áudio que ganhou vídeo vira vídeo na mensagem e no histórico, §9.2) | `rotas.ts:198` |
| 61 | GET | `/ice` | token | – | – | `{iceServers, iceTransportPolicy}` | – | – | `rotas.ts:203` |
| 62 🆕 `ef13c22` | GET | `/sip` | token | – | – | linha `sip` ou `{}` — 🆕 `{}` também sem a permissão `telefonia` | – | – | `rotas.ts:207` |
| 63 🆕 `ef13c22` | PUT | `/sip` | token | – | §14 | linha `sip` | 🆕 `telefonia` (403) | – | `rotas.ts:209` |
| 64 🆕 `ef13c22` | PATCH | `/sip` | token | – | `id` + campos | linha `sip` | dono (403); 🆕 `telefonia` (403) | – | `rotas.ts:211` |
| 65 🆕 (`8031fa5`) 🆕 `ef13c22` | PUT | `/enquete` | token | – | `conversa_id`, `pergunta` (≤ 300), `opcoes: string[]` (2–12, cada ≤ 200), `multipla: bool` | mensagem criada + `enquete_id` | membro; 🆕 `ef13c22` `votacao` (403); **só grupo** (400) | cria a mensagem tipo 8 (WS 2, push, WS 3 como uma mensagem normal) | `rotas.ts` (`8031fa5`), `enquetes.ts:12-45` |
| 66 🆕 | GET | `/enquete` | token | `id` | – | `Enquete` (§10.13) | membro da conversa (403); 404 | – | `enquetes.ts:54-78` |
| 67 🆕 | POST | `/enquete/votar` | token | – | `enquete_id`, `opcoes: int[]` (≤ 12; vazio tira o voto) | `Enquete` (§10.13) | membro; escolha única aceita 1 (400) | **WS 62** a **todos** os membros (inclusive quem votou) | `enquetes.ts:82-116` |
| 68 🆕 `5cad911` | POST | `/enquete/encerrar` | token | – | `enquete_id` | `Enquete` (§10.13) | quem criou a votação **ou** quem criou o grupo (403); já encerrada (400) | **WS 62** a todos os membros | `enquetes.ts` (`encerrarEnquete`) |
| 69 🆕 `5cad911` | PATCH | `/enquete` | token | – | `enquete_id`, `encerra_em: string \| null` | `Enquete` (§10.13) | só quem criou a votação (403); já encerrada (400); data inválida (400) | **WS 62** a todos os membros | `enquetes.ts` (`alterarPrazoEnquete`) |
| 70 🆕 `b8ea0c2` | GET | `/contatos/presenca` | token | – | – | `[{usuario_id, estado: 'ativo'\|'ausente'\|'offline', visto_em}]` (§15.1) | contatos de conversa direta; `visto_em` só de quem mostra | – | `usuarios.ts` (`contatosPresenca`) |
| 71 🆕 `b8ea0c2` | GET | `/usuario/privacidade` | token | – | – | `{mostrar_visto_em, mostrar_na_conversa, aparecer_offline}` | o próprio usuário | – | `usuarios.ts` (`privacidade`) |
| 72 🆕 `b8ea0c2` | GET | `/conversa/presentes` | token | `conversa` | – | `number[]`: outros membros com a conversa aberta agora (§15.2) | membro (403) | – | `conversas.ts` (`presentesConversa`) |
| 73 🆕 `b8ea0c2` `6d20ff7` `ef13c22` | POST | `/mensagem/confirmar` | token | – | `mensagem_id` | `{mensagem_id}` | 404; membro (403); mensagem sem pedido ou do próprio autor (400); 🆕 `6d20ff7` fora da lista de quem precisa confirmar (403) | **WS 8** aos outros membros, só na 1ª confirmação (§10.14); 🆕 `ef13c22` na 1ª confirmação, **WS 61** ao próprio usuário se ele tinha a atividade tipo 5 dessa mensagem (ela deixa de ser pendente) | `mensagens.ts` (`confirmarLeitura`) |
| 74 🆕 `b8ea0c2` | POST | `/chamada/chamar-novamente` | token | – | `chamada_id`, `usuario_id` | dados da chamada (§9.3) | participante (404); alvo recusou/não atendeu e chamada 1 ou 3 (400) | alvo volta a 1; **WS 51** ao alvo; **WS 57** `{acao:'participantes'}` aos outros (§9.14) | `chamadas.ts` (`chamarNovamente`) |
| 75 🆕 `b8ea0c2` `ef13c22` | GET | `/recursos` | token | – | – | `{transcricao: bool, ia: bool}` (§13.3) — 🆕 `ef13c22` `+ conhecimento: bool` | qualquer usuário logado | – | `parametros.ts` (`recursosSistema`) |
| 76 🆕 `b8ea0c2` | POST | `/parametros/ia/testar` | token | – | `url`, `modelo`, `token?` | `{ok, resposta, erro, milissegundos}` (§13.4) | `parametros` (403); URL inválida (400) | chama o servidor de IA (até 60 s) | `parametros.ts` (`testarIa`) |
| 77 🆕 `b8ea0c2` | POST | `/conversa/resumo` | token | – | `conversa_id`, `periodo: '24h'\|'7d'\|'30d'\|'recentes'` | `Resumo` (§13.5) | membro (403); IA desligada (400) | gera em segundo plano (memória, 30 min) | `resumos.ts` (`pedirResumo`) |
| 78 🆕 `b8ea0c2` | GET | `/conversa/resumo` | token | `id` (uuid) | – | `Resumo` (§13.5) | só quem pediu; vencido/inexistente (404) | – | `resumos.ts` (`consultarResumo`) |
| 79 🆕 `ea02f57` | POST | `/ia/sugestao` | token | – | `conversa_id`, `texto` (≤ 4000) | `{sugestao}` (§13.6) | membro (403); IA desligada (400) | – | `sugestoes.ts` (`sugerirTexto`) |
| 80 🆕 `12cf0cf` | GET | `/postagem/respostas` | token | `id` (da postagem) | – | `MensagemResposta[]`: até 100 respostas, mais antiga primeiro (§10.15) | membro (403); não é postagem (404) | – (não marca recebida) | `mensagens.ts` (`respostasPostagem`) |
| 81 🆕 `12cf0cf` | POST | `/postagem/visualizar` | token | – | `postagem_id` | `{vistas: int}` (§10.15) | membro (403); não é postagem (404) | marca as respostas como recebidas/visualizadas; **WS 3** aos outros se alguma mudou; 🆕 `e25c4db` **WS 61** ao próprio usuário se alguma tinha atividade de resposta/menção ligada (§12) | `mensagens.ts` (`visualizarPostagem`) |
| 82 🆕 `6d20ff7` `ef13c22` | POST | `/mensagem/pedir-confirmacao` | token | – | `mensagem_id`, `usuarios?: int[]` | objeto `confirmacao` (§10.2) de quem pediu | 404; membro (403); só quem enviou (403); 🆕 `ef13c22` permissão `confirmacao` (403); oculta, já pede, fora do prazo de **24 h** ou lista inválida (400) — §10.14 | **WS 65** a **todos** os membros (inclusive quem pediu), cada um com a `confirmacao` calculada para si; 🆕 `ef13c22` atividade **tipo 5** (+ WS 61) a quem precisa confirmar | `mensagens.ts` (`pedirConfirmacao`) |
| 83 🆕 `ef13c22` | GET | `/cadastro` | **pública** | – | – | `{aberto: bool}` (§2.6) | – | – | `parametros.ts` (`cadastroSistema`) |
| 84 🆕 `ef13c22` | PUT | `/usuario/novo` | token | – | `nome`, `login?`, `email?`, `telefone?`, `senha?`, `avatar_anexo_id?`, `bot?: bool`, `instrucoes?` | `{id, nome, login, bot}` (§2.6) | `usuarios` (403); campos faltando ou avatar que não é imagem (400); bot sem IA ou sem instruções (400) | pessoa: todas as permissões de uso; bot: §13.7 | `usuarios.ts` (`cadastrarUsuario`) |
| 85 🆕 `ef13c22` | GET | `/bots` | token | – | – | `Bot[]` (§13.7): todos com `permissoes`; só os próprios com `bots` | `permissoes` ou `bots` (403) | – | `bots.ts` (`listarBots`) |
| 86 🆕 `ef13c22` | PUT | `/bot` | token | – | `nome`, `instrucoes` | `Bot` | `bots` (403); IA desligada, nome fora de 1..60 ou instruções fora de 1..8000 (400) | cria o usuário bot | `bots.ts` (`criarBot`, `incluirBot`) |
| 87 🆕 `ef13c22` | PATCH | `/bot` | token | – | `id`, `nome?`, `instrucoes?`, `ativo?: bool` | `Bot` | quem criou (com `bots`) ou quem tem `permissoes` (403 `Só quem criou o bot o edita.`); 404 | – | `bots.ts` (`alterarBot`) |
| 88 🆕 `ef13c22` | DELETE | `/bot` | token | `id` | – | `{id, apagado: bool}` | idem | sai dos grupos (sem WS 40); sem histórico apaga de vez, com histórico marca `excluido_em` (§13.7) | `bots.ts` (`excluirBot`) |
| 89 🆕 `ef13c22` | PUT | `/bot/ferramentas` | token | – | `id`, `ferramentas: int[]` (≤ 100) | `Bot` | idem; ferramenta inexistente (400) | troca a lista | `bots.ts` (`definirFerramentasBot`) |
| 90 🆕 `ef13c22` | PUT | `/bot/conhecimento` | token | – | `id`, `bases: int[]` (≤ 100) | `Bot` | idem; base inexistente (400) | troca a lista | `bots.ts` (`definirConhecimentoBot`) |
| 91 🆕 `ef13c22` | GET | `/conhecimento/bases` | token | – | – | `[{id, nome, descricao, documentos, trechos}]` (§13.9) | `conhecimento` (403) | – | `conhecimento.ts` (`listarBases`) |
| 92 🆕 `ef13c22` | GET | `/conhecimento/disponiveis` | token | – | – | `[{id, nome, descricao}]` | `bots`, `permissoes` ou `conhecimento` (403) | – | `conhecimento.ts` (`basesDisponiveis`) |
| 93 🆕 `ef13c22` | PUT | `/conhecimento/base` | token | – | `nome` (1..100), `descricao?` | base (`documentos: 0`, `trechos: 0`) | `conhecimento` (403); nome repetido (400) | – | `conhecimento.ts` (`incluirBase`) |
| 94 🆕 `ef13c22` | PATCH | `/conhecimento/base` | token | – | `id`, `nome`, `descricao?` | base | `conhecimento` (403); 404; nome repetido (400) | – | `conhecimento.ts` (`alterarBase`) |
| 95 🆕 `ef13c22` | DELETE | `/conhecimento/base` | token | `id` | – | `{id}` | `conhecimento` (403); 404 | apaga os documentos e os trechos no Qdrant | `conhecimento.ts` (`excluirBase`) |
| 96 🆕 `ef13c22` | GET | `/conhecimento/documentos` | token | `base` | – | `[{id, base_id, nome, caracteres, trechos, status, erro, criado_em}]` | `conhecimento` (403) | – | `conhecimento.ts` (`listarDocumentos`) |
| 97 🆕 `ef13c22` | PUT | `/conhecimento/documento` | token | – | `base_id`, `nome`, `texto` | documento (`status: 1`) | `conhecimento` (403); base desligada, sem texto ou grande demais (400); 404 | indexa em segundo plano (sem evento) | `conhecimento.ts` (`incluirDocumento`) |
| 98 🆕 `ef13c22` | DELETE | `/conhecimento/documento` | token | `id` | – | `{id}` | `conhecimento` (403); 404 | apaga os trechos no Qdrant | `conhecimento.ts` (`excluirDocumento`) |
| 99 🆕 `ef13c22` | POST | `/conhecimento/buscar` | token | – | `bases: int[]` (1..100), `pergunta`, `limite?` (1..20, padrão 5) | `[{documento, texto, nota}]` | `conhecimento` (403) | chama o servidor de IA (embeddings) | `conhecimento.ts` (`testarBusca`) |
| 100 🆕 `ef13c22` | POST | `/conhecimento/reindexar` | token | – | – | `{documentos: int}` | `conhecimento` (403); base desligada (400) | apaga a coleção e indexa tudo de novo | `conhecimento.ts` (`reindexarTudo`) |
| 101 🆕 `ef13c22` | GET | `/ferramentas` | token | – | – | `Ferramenta[]` (§13.8; dos cabeçalhos, só os nomes) | `ferramentas` (403) | – | `ferramentas.ts` (`listarFerramentas`) |
| 102 🆕 `ef13c22` | GET | `/ferramentas/disponiveis` | token | – | – | `[{id, nome, descricao}]` das ativas | `bots`, `permissoes` ou `ferramentas` (403) | – | `ferramentas.ts` (`ferramentasDisponiveis`) |
| 103 🆕 `ef13c22` | PUT | `/ferramenta` | token | – | §13.8 | `Ferramenta` | `ferramentas` (403); nome, URL ou parâmetros inválidos (400) | – | `ferramentas.ts` (`incluirFerramenta`) |
| 104 🆕 `ef13c22` | PATCH | `/ferramenta` | token | – | `id` + §13.8 | `Ferramenta` | `ferramentas` (403); 404; 400 | – | `ferramentas.ts` (`alterarFerramenta`) |
| 105 🆕 `ef13c22` | DELETE | `/ferramenta` | token | `id` | – | `{id}` | `ferramentas` (403); 404 | desliga dos bots (cascade) | `ferramentas.ts` (`excluirFerramenta`) |
| 106 🆕 `ef13c22` | POST | `/ferramenta/testar` | token | – | `id`, `argumentos: {}` | `{ok, status, resposta, erro, milissegundos}` | `ferramentas` (403); 404 | chama a API externa (até 20 s) | `ferramentas.ts` (`testarFerramenta`) |
| 107 🆕 `ef13c22` | PUT | `/bot/permissoes` | token | – | `id`, `permissoes: string[]` (≤ 50, só de uso) | `Bot` | como `PATCH /bot`; código que não é de uso (400); quem não tem `permissoes` só libera o que ele mesmo tem (403) | troca as permissões de uso do bot | `bots.ts` (`definirPermissoesBot`) |
| 108 🆕 `ef13c22` | POST | `/atividade/concluir` | token | – | `id` (atividade) | `{sucesso: true}` (sempre; id alheio ou inexistente não dá erro) | só a própria | conclui à mão; **WS 61** a si se mudou (§12) | `atividades.ts` (`concluirAtividade`) |
| 109 🆕 `ef13c22` | POST | `/atividades/reacoes-vistas` | token | – | `mensagens: int[]` (≤ 200) | `{concluidas: int}` | só as próprias | conclui as atividades de **reação** dessas mensagens; **WS 61** a si se concluiu alguma (§12) | `atividades.ts` (`concluirReacoesVistas`) |

Todas as rotas de `src/rotas.ts` estão na tabela acima; nenhuma foi omitida (~~🆕 conferido em `e25c4db`: 82 chamadas `.get/.post/.put/.patch/.delete`~~ 🆕 conferido em `ef13c22`: **109** chamadas `.get/.post/.put/.patch/.delete`, 27 a mais que as 82 de `e25c4db`).

---

## 5. Detalhe das rotas por área

### 5.1 Usuário

**`POST /api/alterar-senha`** — `src/usuarios.ts:68-74`. Corpo `{ "senha_atual": string, "senha": string }`. 400 `Senha atual incorreta!` (`src/autorizacao.ts:61-68`); 400 `Senha inválida!` se `senha` > 72. **Não** confere o mínimo de 4 caracteres aqui. Resposta `{}`. O token continua válido. 🆕 `ef13c22`: a rota chama `trocarSenha`, que antes confere a permissão de uso **`senha`** → 403 `Você não tem permissão para: trocar a própria senha.` (`usuarios.ts`, `trocarSenha`).

**`PATCH /api/usuario`** — `src/usuarios.ts:110-116`. Corpo `{ "id": int, "nome"?: string, "email"?: string, "telefone"?: string|null, "avatar_anexo_id"?: int|null }` (`src/esquemas.ts:37`). `id` precisa ser o do token (403 `Acesso negado!`). Só grava os campos enviados; sem nenhum, devolve a linha atual. Resposta: linha `usuario` sem `senha`. Sem evento WebSocket (os outros só veem a mudança ao recarregar listas).
- Avatar: enviar o anexo (tipo 2, §8) e depois `avatar_anexo_id` = **id numérico** do anexo.
- 🆕 `b8ea0c2` Privacidade da presença: o corpo aceita também `mostrar_visto_em?`, `mostrar_na_conversa?` e `aparecer_offline?` (booleanos; `null` não é aceito). A resposta passa a trazer `visto_em`, `mostrar_visto_em`, `mostrar_na_conversa` e `aparecer_offline`. Se o usuário tem socket aberto, a preferência vale na hora: os contatos e o próprio usuário recebem WS 60 se o estado visível mudou, e os membros das conversas que deixam de aparecer recebem WS 63 `aberta:false` (§15).
- 🆕 `ef13c22` **Uma permissão de uso por campo** (`usuarios.ts`, `alterarUsuario`), conferida pela **presença da chave** no corpo, não por mudança de valor:
  - `nome` → `nome`;
  - `email` ou `telefone` → `email` (o telefone vai com o e-mail);
  - `avatar_anexo_id` → `avatar`;
  - `mostrar_visto_em`, `mostrar_na_conversa` ou `aparecer_offline` → `privacidade`.
  - Falta qualquer uma → 403 `Você não tem permissão para: …` e **nada** é gravado. Mande só as chaves que mudaram: um "Salvar" com nome e e-mail juntos precisa das duas permissões.

🆕 `b8ea0c2` **`GET /api/usuario/privacidade`** — `usuarios.ts` (`privacidade`). → `{ "mostrar_visto_em": true, "mostrar_na_conversa": true, "aparecer_offline": false }` (padrões da migração 040). Para a tela de configurações.

🆕 `b8ea0c2` **`GET /api/contatos/presenca`** — `usuarios.ts` (`contatosPresenca`). Uma linha por pessoa com quem o usuário tem **conversa direta (tipo 1)**: `[{ "usuario_id": 8, "estado": "ativo"|"ausente"|"offline", "visto_em": "<ISO>"|null }]`. `estado` é o visível (quem escolheu aparecer offline vem `offline`); `visto_em` vem do banco e é `null` para quem não mostra o visto por último. Não traz o próprio usuário. Ver §15.1.

**`DELETE /api/usuario?id=`** — `src/usuarios.ts:118-131`. Só a própria conta. Apaga `dispositivo_usuario` e `dispositivo` do usuário; se houver histórico (mensagens, conversas, chamadas...), **409** `A conta tem histórico e não pode ser excluída!`. 🆕 `ef13c22`: apaga também as permissões (`permissao_usuario`), tudo numa transação: com 409, nada sai (antes os dispositivos saíam mesmo com 409).

**`GET /api/usuario/contatos`** — `src/usuarios.ts:142-144`. Devolve **todos os usuários do sistema** (inclusive o próprio), `[{ id, nome, login, email, telefone }]`, ordenado por `id`. **Sem avatar.** Não usa a tabela `usuario_contato`. 🆕 `ef13c22` (`usuarios.ts`, `contatos`): cada item ganha **`bot: bool`** no fim (`[{ id, nome, login, email, telefone, bot }]`); os bots aparecem como contatos comuns (login `bot-…`, e-mail `…@bot.conversa`), e os bots **excluídos** com histórico ficam de fora. É a única rota que marca quem é bot (§13.7).

🆕 `ef13c22` **`PUT /api/usuario/novo`** — cadastro pelo administrador (pessoa ou bot). Ver §2.6.

**`PUT /api/usuario/contato?relacionamento_id=`** / **`DELETE /api/usuario/contato?id=`** — `src/usuarios.ts:133-140`. Mantêm a tabela `usuario_contato` (contatos salvos). **Não há rota para listar** os contatos salvos. O PUT não valida duplicidade nem existência (FK inválida = 500).

**`GET /api/contatos/online`** — `src/usuarios.ts:147-155`. `number[]` com os ids dos usuários que têm **conversa direta (tipo 1)** com o usuário e estão com WebSocket conectado agora. 🆕 `b8ea0c2`: o filtro passou a ser a presença **visível** (`estado != 'offline'`): entra quem está ativo ou ausente; quem escolheu aparecer offline fica de fora mesmo conectado. O web passou a usar `GET /contatos/presenca`.

### 5.2 Conversas
Ver §11.

### 5.3 Mensagens
Ver §10.

### 5.4 Anexos e transcrição
Ver §8.

### 5.5 Chamadas e ICE
Ver §9.

### 5.6 Permissões, parâmetros, atividades, SIP
Ver §13, §12, §14.

### 5.7 🆕 IA (resumo e sugestão), recursos
Ver §13.3–§13.6.

### 5.8 🆕 Bots de IA, ferramentas externas e base de conhecimento (`ef13c22`)
Ver §13.7–§13.9. Cadastro pelo administrador: §2.6.

---

## 6. WebSocket

Código: `src/websocket.ts`. Testes: `tests/websocket.test.ts`.

### 6.1 URL

`wss://<host>/ws/` (com a barra final) — mesma porta da API (`src/websocket.ts:52-55`, `bin/nginx/nginx.conf:109-114`). Em desenvolvimento local sem nginx: `ws://127.0.0.1:8080/ws/`.

### 6.2 Handshake / login

1. Abrir a conexão (sem cabeçalho de autenticação).
2. Enviar como primeira mensagem (texto JSON):
   ```json
   { "tipo": 1, "token": "<jwt>" }
   ```
3. ~~**Não há resposta de sucesso.**~~ 🆕 `b8ea0c2`: **agora há uma confirmação indireta.** Depois do login, o servidor manda **a esta conexão** um WS 60 sobre o próprio usuário: `{"tipo":60,"usuario_id":<eu>,"online":bool,"estado":"ativo"|"ausente"|"offline","visto_em":null}` (como os outros o veem; `offline` se ele escolheu aparecer offline). Se o login mudou o estado visível (primeira conexão, ou o usuário estava `ausente`), antes chega também o WS 60 enviado aos contatos **e ao próprio usuário** (todas as conexões dele) — ou seja, na primeira conexão **o WS 60 do próprio usuário chega duas vezes**. Use o primeiro WS 60 com `usuario_id` = eu como "login aceito". Toda conexão nova começa como **ativa** (§15.1). (Antes: nenhum retorno; o teste esperava 100 ms — `tests/api.ts:173-177`.)
4. Token inválido/expirado: o servidor envia `{"tipo":0,"message":"Token inválido ou expirado"}` (ou `Token não informado`) e **não fecha** a conexão; ela simplesmente não recebe nada (`src/websocket.ts:83-88`). O cliente deve fechar, renovar o login HTTP e reconectar.
5. Um segundo `tipo:1` na mesma conexão já autenticada é ignorado (`src/websocket.ts:78-80`).
6. Expiração do token **depois** do login não derruba a conexão: ela continua recebendo eventos até cair. Na reconexão, porém, o token velho é recusado.

### 6.3 Envelope

Todas as mensagens (nos dois sentidos) são objetos JSON com o campo numérico `tipo`. Os demais campos variam por tipo e ficam **no nível raiz** (não há `dados` genérico, exceto no tipo 57). 🆕 O tipo **63** tem formatos diferentes em cada sentido (§6.5 e §6.6).

Erros de leitura (`src/websocket.ts:60-68`):
- Texto que não é JSON: `{"tipo":9,"message":"Erro ao ler os dados do WebSocket: JSON inválido!"}`
- JSON sem `tipo`: `{"tipo":9,"message":"Erro ao ler os dados do WebSocket: Par \"tipo\" não encontrado!"}`

> Atenção: o `tipo: 9` **não existe** no enum `TipoMensagemSocket`.

### 6.4 Enum `TipoMensagemSocket` (`src/websocket.ts:4-22`; 🆕 em `12cf0cf`, `:4-29`; 🆕 em `e25c4db`, `:4-31`)

> 🆕 `ef13c22`: **nenhum tipo novo**; o `websocket.ts` não mudou. Mudaram só os gatilhos de tipos que já existiam: o **61** sai em mais situações (atividades pendentes, §12) e o **4** ("digitando") também vem de **bots** enquanto pensam (§13.7). Mensagens de bots geram WS 2, 3 e 61 como as de qualquer pessoa.

| Nome | Valor | Direção |
|---|---|---|
| `Erro` | 0 | servidor → cliente |
| `Login` | 1 | cliente → servidor |
| `NovaMensagem` | 2 | servidor → cliente |
| `AtualizacaoStatusMensagem` | 3 | servidor → cliente |
| `Digitando` | 4 | servidor → cliente |
| `GravandoAudio` | 5 | servidor → cliente |
| *(6 não usado)* | – | – |
| `ReacaoMensagem` | 7 | servidor → cliente |
| `ConfirmacaoLeitura` 🆕 `b8ea0c2` | 8 | servidor → cliente |
| `ConversaNova` | 40 | servidor → cliente (o web chama de `ConversaAtualizada`) |
| `ChamadaRecebida` | 51 | servidor → cliente |
| `ChamadaFinalizada` | 52 | servidor → cliente |
| `UsuarioRecusou` | 53 | servidor → cliente |
| `UsuarioEntrou` | 54 | servidor → cliente |
| `UsuarioSaiu` | 55 | servidor → cliente |
| `VideoAtivado` | 56 | servidor → cliente |
| `SinalChamada` | 57 | **bidirecional** |
| `StatusUsuario` | 60 | servidor → cliente |
| `NovaAtividade` | 61 | servidor → cliente |
| `EnqueteAtualizada` 🆕 | 62 | servidor → cliente |
| `Presenca` 🆕 `b8ea0c2` | 63 | **bidirecional** (formatos diferentes) |
| `PostagemAtualizada` 🆕 `12cf0cf` | 64 | servidor → cliente |
| `ConfirmacaoPedida` 🆕 `6d20ff7` | 65 | servidor → cliente |
| *(9: erro de leitura, fora do enum)* | 9 | servidor → cliente |

### 6.5 Mensagens cliente → servidor aceitas

Somente ~~duas~~ 🆕 três (`src/websocket.ts`, `criarWebSocket`):

| tipo | Formato | Regra |
|---|---|---|
| 1 | `{"tipo":1,"token":"<jwt>"}` | login (§6.2) |
| 63 🆕 `b8ea0c2` | `{"tipo":63,"ativo":true\|false,"conversa_id":<int>\|null}` | Presença **desta conexão** (§15.1). Só vale depois do login (antes é ignorado). `ativo` conta só se for **literalmente `true`** (JSON booleano; `1` ou `"true"` = inativo). `conversa_id` precisa ser inteiro > 0, senão vira `null`; só conta enquanto a conexão está ativa. Sem resposta. Quem nunca manda o 63 fica **ativo** enquanto o socket estiver aberto. O web manda ao conectar e a cada mudança (aba visível e usada nos últimos 5 min; conversa aberta), sem repetir o mesmo conteúdo — `conversa-web/src/stores/chat.ts:1430-1465` |
| 57 | `{"tipo":57,"chamada_id":<int>,"dados":{...}}` | Só vale se a conexão já fez login, `chamada_id` inteiro > 0, `dados` é objeto com `JSON.stringify(dados).length <= 2000`, e o remetente está **dentro** da chamada (`chamada_usuario.status = 3` e `chamada.status in (1,3)`). Repassado aos outros participantes com status 3. Sem resposta, sem gravação. Participantes ficam em cache por 5 s (`src/websocket.ts:164-221`) |

Qualquer outro `tipo` é ignorado silenciosamente. Não existe ping de aplicação. Ações como "digitando" são **HTTP** (`POST /api/conversa/digitando`), não WebSocket.

### 6.6 Eventos servidor → cliente (payload exato e quando disparam)

Todos são enviados a **todas as conexões abertas** do usuário destinatário (várias abas/aparelhos) — `src/websocket.ts:119-128`.

| tipo | Payload | Quando / para quem | Ref. |
|---|---|---|---|
| 0 | `{"tipo":0,"message":string}` | Falha no login do socket | `websocket.ts:86` |
| 2 | `{"tipo":2,"titulo":string,"mensagem":string}` | Nova mensagem (imediata em `PUT /mensagem`, ou agendada quando amadurece) para **cada outro membro** da conversa. `titulo` = nome completo do remetente; `mensagem` = texto resumido (§7.2). **Não traz `conversa_id` nem `mensagem_id`**: o cliente deve chamar `GET /api/mensagens/novas` (como o web faz). Pode chegar **duplicado** (§19) | `websocket.ts:134-136`, `notificacoes.ts:52-76` |
| 3 | `{"tipo":3,"grupo":<conversa_id>,"mensagens":"12,13,14"}` | Mudança de status de mensagens (`mensagens` é **string CSV**). Dispara: ao listar `/mensagens` (recebidas → aos outros membros), `visualizar`/`reproduzir` (aos outros), exclusão de mensagem (aos outros), agendada amadurecendo (aos outros), resumo de chamada inserido (a **todos** os membros, inclusive o autor). Cliente: chamar `GET /api/mensagem/status`; se algum id é desconhecido, recarregar mensagens. 🆕 `12cf0cf`: os ids podem ser de **respostas de postagem**, que não estão no fluxo (vêm ao listar `/mensagens`, que marca recebidas também as respostas, e em `POST /postagem/visualizar`): id desconhecido **não** quer dizer que falta mensagem no fluxo (§10.15) | `websocket.ts:138-140`, `notificacoes.ts:43-49` |
| 4 | `{"tipo":4,"conversa_id":int,"usuario_id":int}` | Alguém chamou `POST /conversa/digitando`; vai aos outros membros. Não há evento de "parou". 🆕 `ef13c22`: também um **bot** que está montando a resposta (`usuario_id` = o bot), na hora e a cada **3 s** até responder ou falhar (pode levar minutos) | `websocket.ts:142-144`, `conversas.ts:237-240`, 🆕 `bots.ts` (`responder`) |
| 5 | `{"tipo":5,"conversa_id":int,"usuario_id":int}` | `POST /conversa/gravando` | idem |
| 7 | `{"tipo":7,"conversa_id":int,"mensagem_id":int,"usuario_id":int,"emoji":string,"acao":"add"\|"remove"}` | Reação alternada; aos outros membros da conversa | `websocket.ts:146-148`, `mensagens.ts:748-752` |
| 8 🆕 `b8ea0c2` | `{"tipo":8,"conversa_id":int,"mensagem_id":int,"usuario_id":int,"nome":string,"confirmada_em":"<ISO>"}` | Alguém confirmou a leitura de uma mensagem que pede confirmação (`POST /mensagem/confirmar`). `usuario_id`/`nome` (completo) = quem confirmou. Vai a **todos os outros membros** (inclusive o autor), **só na primeira** confirmação daquela pessoa (repetir não reenvia) | `websocket.ts` (`notificarConfirmacao`), `mensagens.ts` (`confirmarLeitura`) |
| 40 | `{"tipo":40,"conversa_id":int,"usuario_id":int}` | (a) `PUT /conversa/usuario` adicionou membro novo → a todos os membros exceto quem adicionou (inclui o novo); (b) chat da chamada criado → membros do grupo exceto quem pediu (🆕 `a40fd47`: **não** dispara quando o chat reaproveita a conversa da ligação — 🆕 `6d20ff7`: o que agora só acontece com ligação feita dentro do chat de outra ligação); (c) ao `POST /chamada/entrar` com chat já existente → ao próprio usuário (🆕 só quando o chat é um grupo criado); (d) 🆕 `b8ea0c2` `PATCH /conversa` (nome, imagem ou emoji; sempre, mesmo sem mudança) → aos outros membros, `usuario_id` = quem alterou. **Não** dispara na criação da conversa nem na saída de membro. Cliente: recarregar `/conversas` | `conversas.ts:232`, `chamadas.ts:258,280`, 🆕 `conversas.ts` (`alterarConversa`) |
| 51 | `{"tipo":51,"chamada_id":int,"usuario_id":int}` | `usuario_id` = quem ligou/adicionou. Em `iniciar`: a todos os participantes menos o criador. Em `PUT /chamada/usuario`: a todos menos quem adicionou (inclusive quem já está na chamada). 🆕 `b8ea0c2` Em `POST /chamada/chamar-novamente`: **só ao alvo**, `usuario_id` = quem chamou de novo — pode chegar para um `chamada_id` que esse aparelho já recusou (§9.14) | `chamadas.ts:108,289`, 🆕 `chamadas.ts` (`chamarNovamente`) |
| 52 | `{"tipo":52,"chamada_id":int,"usuario_id":int}` | `cancelar` ou `finalizar`, aos outros participantes (qualquer status). **Não** dispara quando a chamada termina por saída (`sair`) | `chamadas.ts:158,298` |
| 53 | `{"tipo":53,"chamada_id":int,"usuario_id":int}` | `recusar`: aos outros participantes **e ao próprio** (outras abas param de tocar) | `chamadas.ts:193-195` |
| 54 | `{"tipo":54,"chamada_id":int,"usuario_id":int}` | `entrar`: aos outros e **ao próprio** | `chamadas.ts:208-210` |
| 55 | `{"tipo":55,"chamada_id":int,"usuario_id":int}` | `sair`: aos outros participantes | `chamadas.ts:223` |
| 56 | `{"tipo":56,"chamada_id":int,"usuario_id":int}` | `POST /chamada/video`: aos outros participantes. 🆕 `6d20ff7`: a mesma rota grava `chamada.tipo = 2` (§9.2); o evento não traz o tipo — quem precisar relê `GET /chamada/dados` | `chamadas.ts:302-305`, 🆕 `chamadas.ts` (`ativarVideo`) |
| 57 | `{"tipo":57,"chamada_id":int,"usuario_id":int,"dados":{...}}` | Repasse de sinal (§9.11) ou aviso do servidor: `{acao:"chat",conversa_id}` (🆕 `a40fd47`: também quando o chat é a própria conversa da ligação — 🆕 `6d20ff7`: só se ela é o chat de outra ligação) e 🆕 `b8ea0c2` `{acao:"participantes"}` (alguém foi chamado de novo: reler `GET /chamada/dados`), este a todos os participantes menos quem chamou e o alvo, em qualquer status | `websocket.ts:156-158,213-215`, 🆕 `chamadas.ts` (`chamarNovamente`) |
| 60 | ~~`{"tipo":60,"usuario_id":int,"online":bool}`~~ 🆕 `b8ea0c2` `{"tipo":60,"usuario_id":int,"online":bool,"estado":"ativo"\|"ausente"\|"offline","visto_em":"<ISO>"\|null}` | 🆕 Dispara a **cada mudança do estado visível** (ativo ↔ ausente ↔ offline), não só na primeira/última conexão. Vai a quem tem conversa direta (tipo 1) com ele **e ao próprio usuário** (todas as conexões dele). `online` = `estado != 'offline'`. `visto_em` só vem preenchido quando a mudança é **saída do ativo** e a pessoa mostra o visto por último; nos outros casos vem `null` (que **não** apaga o valor conhecido). Também é mandado à conexão que acabou de fazer login (§6.2). Detalhes em §15.1 | 🆕 `websocket.ts` (`atualizarPresenca`, `notificarContatosStatus`) |
| 61 | `{"tipo":61}` | Atividade nova ou removida para o usuário; recarregar `/atividades/novas`. 🆕 `e25c4db`: também quando o **próprio usuário** lê no chat uma mensagem com atividade de resposta/menção ligada a ela (`POST /mensagem/visualizar` que marcou agora, ou `POST /postagem/visualizar` que marcou alguma resposta) — o contador diminui sem `POST /atividades/vistas` (§12). `GET /mensagens` não dispara (só marca recebida). 🆕 `ef13c22` — também ao próprio usuário quando uma atividade **deixa de estar pendente**: `POST /atividade/concluir` que concluiu; `POST /atividades/reacoes-vistas` que concluiu alguma; `POST /mensagem/confirmar` (1ª vez) com atividade tipo 5 daquela mensagem. E a quem **precisa confirmar**, quando ganha a atividade tipo 5 (`PUT /mensagem` com `pede_confirmacao`, que numa agendada só avisa quando ela sai, ou `POST /mensagem/pedir-confirmacao`, que avisa na hora) | `websocket.ts:151-153`, `atividades.ts`, 🆕 `atividades.ts` (`avisarAtividadesLidas`; 🆕 `ef13c22` `concluirAtividade`, `concluirReacoesVistas`, `avisarConfirmacaoFeita`) |
| 62 🆕 | `{"tipo":62,"enquete_id":int,"conversa_id":int}` | Alguém votou (ou tirou o voto) numa enquete — ou, 🆕 `5cad911`, a votação foi encerrada ou teve a data final mudada; vai a todos os membros da conversa, inclusive outras abas/aparelhos de quem votou. O cliente relê `GET /enquete?id=` só se a bolha está carregada | `websocket.ts` (`notificarEnquete`), `enquetes.ts:110-114` |
| 63 🆕 `b8ea0c2` | `{"tipo":63,"conversa_id":int,"usuario_id":int,"aberta":bool}` | `usuario_id` abriu (`true`) ou saiu (`false`) da conversa — inclusive porque a conexão ficou inativa, fechou ou a pessoa passou a esconder a conversa aberta. Vai aos **outros membros** daquela conversa. Não vem de quem desligou `mostrar_na_conversa` ou aparece offline. Estado inicial: `GET /conversa/presentes` (§15.2) | `websocket.ts` (`notificarNaConversa`) |
| 64 🆕 `12cf0cf` | `{"tipo":64,"conversa_id":int,"postagem_id":int,"usuario_id":int}` | Resposta nova numa postagem (`usuario_id` = quem respondeu). Vai a **todos os outros membros** da conversa. Cliente: reler a postagem (o resumo `postagem` vem em `GET /mensagens?mensagemreferencia=<postagem_id>`; as respostas em `GET /postagem/respostas`). A resposta também gera WS 2 e push como mensagem comum (§10.15) | `websocket.ts` (`notificarPostagem`), `mensagens.ts` (`registrarRespostaPostagem`) |
| 65 🆕 `6d20ff7` | `{"tipo":65,"conversa_id":int,"mensagem_id":int,"usuario_id":int,"confirmacao":{total,confirmou,pode_confirmar,destinatarios,usuarios}}` | Quem enviou passou a pedir confirmação de leitura numa mensagem **já enviada** (`POST /mensagem/pedir-confirmacao`; `usuario_id` = quem pediu). Vai a **todos os membros**, inclusive quem pediu (outros aparelhos), cada um com o objeto `confirmacao` (§10.2) **calculado para si** (`confirmou`, `pode_confirmar`). Cliente: pôr a `confirmacao` na mensagem `mensagem_id`, se ela está carregada; id desconhecido (ex.: agendada que ainda não saiu, §10.14) → ignorar. Pedido feito **no envio** (`PUT /mensagem`) não gera WS 65 | `websocket.ts` (`notificarConfirmacaoPedida`), `mensagens.ts` (`pedirConfirmacao`) |
| 9 | `{"tipo":9,"message":string}` | Mensagem do cliente ilegível | `websocket.ts:61-66` |

### 6.7 Keepalive e reconexão

- **Não há ping/pong de aplicação** no código. O WebSocket é o do Bun via Elysia sem opções de `idleTimeout`/`sendPings` (`src/websocket.ts:55`), ou seja, valem os padrões do Bun (pelo que se sabe do Bun: `idleTimeout` 120 s e envio automático de pings de protocolo — **não confirmado no código do servidor**). O nginx mantém a conexão por até 3600 s sem tráfego (`bin/nginx/nginx.conf:111-112`); a borda de produção também usa 3600 s (`SETUP.md:124-125`).
- Recomendação para OkHttp: `pingInterval(30, SECONDS)` (pings de protocolo são respondidos automaticamente pelo servidor).
- O servidor não guarda estado de sessão nem fila: eventos ocorridos com o socket fora **se perdem**. Na reconexão, o cliente deve (como o web faz — `conversa-web/src/stores/chat.ts:693-735`):
  1. mandar `{tipo:1, token}`;
  2. `GET /api/contatos/online` — 🆕 `b8ea0c2`: o web usa `GET /api/contatos/presenca` (estado + visto por último);
  3. 🆕 `b8ea0c2` mandar `{tipo:63, ativo, conversa_id}` com o estado atual e, se há conversa aberta, `GET /api/conversa/presentes?conversa=` (o web faz os dois no `onopen`, `conversa-web/src/stores/chat.ts:779-800`);
  4. `GET /api/atividades/novas`;
  5. `GET /api/chamadas/pendentes`;
  6. `GET /api/mensagens/novas?desde=<cursor>` e `GET /api/conversas`.
- Backoff do web: `min(30 s, 1 s × 2^tentativas)`; com o socket fora, polling a cada 8 s de `/mensagens/novas` e `/chamadas/pendentes`.

### 6.8 Presença online

Ver §15. 🆕 `b8ea0c2`: a presença passou a ter três estados (ativo, ausente, offline), visto por último e "quem está com a conversa aberta", alimentados pelo tipo 63 que o cliente manda (§6.5) e avisados pelos tipos 60 e 63 (§6.6).

---

## 7. Push FCM e dispositivos

### 7.1 Quando é enviado

**Apenas para mensagens novas** (`src/notificacoes.ts:52-76`), chamado por:
- `PUT /api/mensagem` não agendada (`src/mensagens.ts:222-225`);
- mensagem agendada quando amadurece (tarefa a cada 60 s, `src/mensagens.ts:264-283`, `src/tarefas.ts:16-44`).

Para cada outro membro da conversa, para cada dispositivo com `ativo = true` e `token_fcm` não nulo, envia push **se e somente se**:
- o usuário **não** arquivou a conversa (`conversa_usuario.arquivada_em is null`), e
- o usuário **não tem nenhuma conexão WebSocket aberta** naquele momento (`usuarioConectado`) — em qualquer aparelho/aba.
  - 🆕 `b8ea0c2`: a presença nova **não mudou** essa regra (`notificacoes.ts:71` continua usando `usuarioConectado`). Uma conexão `ausente` (aba escondida, app em segundo plano que mandou `ativo:false`) ou de quem escolheu aparecer offline **ainda bloqueia o push** (S18 do doc 08).

🆕 `12cf0cf`: resposta de postagem é mensagem comum para o push e o WS 2 (mesmo título e texto), embora não apareça no fluxo da conversa (§10.15).

🆕 `ef13c22`: mensagem de **bot** é mensagem comum para o push e o WS 2 (título = nome do bot). O bot não tem dispositivo nem socket, então nunca recebe push.

**Não há push para**: chamadas recebidas, chamadas perdidas, reações, menções/atividades (🆕 inclusive o pedido de confirmação, atividade tipo 5), inclusão em conversa, status. Chamada só chega por WebSocket (tipo 51) — ver §19.

Falhas de push são ignoradas silenciosamente (`.catch(() => {})`), inclusive token inválido; o servidor **não limpa** tokens inválidos.

### 7.2 Payload exato (`src/fcm.ts:28-36`)

Mensagem **somente de dados** (sem bloco `notification`):
```json
{
  "token": "<token_fcm>",
  "data": { "titulo": "Ana Souza", "mensagem": "oi @Bruno Código (ts)", "conversa": "42" },
  "webpush": { "headers": { "Urgency": "high" } }
}
```
- Todas as chaves de `data` são **strings** (`conversa` é o id em texto).
- **Não há configuração `android`** (nem `priority: high`): no Android o FCM entrega com a prioridade padrão de mensagem de dados (normal), sujeita a atraso em Doze. Não confirmado em campo; consequência da ausência do bloco `android` no código.
- `titulo` = `usuario.nome` do remetente (nome completo).
- `mensagem` (`src/mensagens.ts:123-145`, `:212-225`):
  - conteúdos unidos por `" | "`, vazios descartados;
  - tipo 1 (texto): menção `@[Nome](id)` vira `@Nome`; bloco de código com cerca ```` ```lang ```` vira ` Código (lang) `; espaços colapsados;
  - tipo 2 → `imagem`; tipo 3 → `arquivo`; tipo 7 → `figurinha`; tipo 8 → `enquete` 🆕;
  - tipos 4, 5 e 6 → **string vazia** (em mensagem imediata só de áudio, `mensagem` chega `""`). Exceção: agendada que amadurece com primeiro conteúdo 4/5 → `áudio` (`src/mensagens.ts:279`), e nesse caso só o **primeiro** conteúdo (ordem 1) é usado.
- O mesmo `titulo`/`mensagem` vai no evento WebSocket tipo 2.

Credenciais do Firebase vêm dos parâmetros `fcm_project_id`, `fcm_client_email`, `fcm_private_key` (com `\n` literais trocados por quebras — `src/fcm.ts:13`).

### 7.3 Registro do dispositivo

Modelo `dispositivo` (`src/tabelas.ts:24-35`, `migracoes/002.sql`, `004.sql`, `007.sql`, `013.sql`):

| Coluna | Tipo | Limite |
|---|---|---|
| `id` | int | – |
| `nome` | varchar | **50** |
| `modelo` | varchar | **50** |
| `versao_so` | varchar | **15** |
| `plataforma` | varchar | **15** |
| `token_fcm` | varchar | 255, nulo |
| `usuario_id` | int | dono |
| `ativo` | bool | default true |
| `criado_em`, `criado_por` | | |

Fluxo:
1. `POST /api/login` com `dispositivo_id` salvo localmente (ou sem, na primeira vez). A resposta traz `dispositivo.id` → **persistir**.
2. `PATCH /api/dispositivo` com `{ "id": <dispositivo.id>, "nome": "...", "modelo": "...", "versao_so": "...", "plataforma": "android", "token_fcm": "<token>" }` (`src/usuarios.ts:76-90`). Só o dono altera (403). Resposta: linha completa do dispositivo (inclui `token_fcm`). Sem nenhum campo além de `id`, devolve o próprio corpo sem tocar no banco (`{ "id": 123 }`).
3. Atualizar o `token_fcm` sempre que o Firebase renovar o token.
4. "Logout": **não há rota**. Para parar os pushes, enviar `PATCH /api/dispositivo` com `"token_fcm": null`.
- Strings acima do limite da coluna geram **500**.
- `PUT /api/dispositivo/usuario?dispositivo_id=` cria uma linha em `dispositivo_usuario` (`src/usuarios.ts:92-95`). É legado: `online_em` nunca é usado; não é necessário para push.

---

## 8. Anexos / MinIO / transcrição

Código: `src/anexos.ts`, `src/minio.ts`, `src/transcricoes.ts`. Testes: `tests/anexos.test.ts`, `tests/transcricoes.test.ts`.

### 8.1 Tipos de anexo

`TIPOS_ANEXO = [2, 3, 4, 5]` — 2 imagem, 3 arquivo, 4 áudio (arquivo de áudio), 5 gravação de áudio (gravada no app) — `src/anexos.ts:31`. O mesmo número é o `tipo` do conteúdo da mensagem (§10.1). Vídeo é enviado como **arquivo (3)** — não há tipo de vídeo.

Tabela `anexo`: `id`, `identificador` varchar(64), `tipo`, `tamanho` int8, `nome` varchar(255), `extensao` varchar(**10**), `objeto` varchar(255), `upload_status` (0 pendente, 1 concluído, 2 falhou), `criado_em`, `criado_por` (`migracoes/000,001,010,013,019.sql`).

### 8.2 Fluxo de upload completo

1. Calcular o `identificador`: o cliente web usa **SHA-256 hex (64 caracteres) do conteúdo** (`tests/api.ts:93`). O servidor **não confere** o hash; aceita qualquer string até 64 caracteres. Usar SHA-256 é o que dá deduplicação.
2. (Opcional, recomendado) `GET /api/anexo/existe?identificador=<id>` → `{"existe":false}` ou `{"existe":true,"id":5,"identificador":"...","tipo":3,"tamanho":123,"upload_status":1}` (`src/anexos.ts:33-39`).
3. `PUT /api/anexo` com corpo (`src/esquemas.ts:74`):
   ```json
   { "identificador": "<sha256>", "tipo": 2, "nome": "foto.jpg", "extensao": "jpg", "tamanho": 123456 }
   ```
   `nome` e `extensao` são `string|null` opcionais (vazio vira `null`). `tamanho` em bytes, máximo **1 GiB** (`1024*1024*1024`) → senão 400 `Arquivo muito grande!` (`src/anexos.ts:30,59-61`).
   🆕 `ef13c22` — **dados do áudio** (`anexos.ts`, `gravarDadosAudio`; esquema `incluirAnexo`): o corpo aceita também `"duracao": number|null` (segundos, 0 a 86400; gravado com 2 casas) e `"intensidade": int[]|null` (picos de 0 a 100 ao longo do áudio, para desenhar a onda; no máximo 200 valores). Fora disso → 400 de validação.
   - Só vale para `tipo` 4 ou 5 e com pelo menos um dos dois preenchido; nos outros tipos é ignorado.
   - Grava em `anexo_audio` (§8.6) **antes** de ver se o anexo existe. O primeiro valor fica: um envio seguinte do mesmo `identificador` só preenche o que faltava.
   - O servidor **não mede** nada: quem envia calcula. A resposta não muda.
   Respostas (`src/anexos.ts:58-81`):
   - Novo: `{"existe":false,"id":<number>,"upload_url":"https://<host>/storage/<bucket>/conversa/AAAA/MM/DD/<identificador>?X-Amz-...","upload_status":0}` — `upload_url` é **PUT pré-assinado válido por 300 s**.
   - Já existente: `{"existe":true,"id":"<string!>","upload_url":"<URL GET pré-assinada, 600 s>"}` — **`id` vem como string** e **não** vem `upload_status`; a URL é de **download**, não de upload. Se o anexo existente estiver pendente/falho, o cliente não tem como reenviar (ver §19).
4. Enviar os bytes: `PUT <upload_url>` com o corpo cru do arquivo. O `Content-Type` não faz parte da assinatura (qualquer um é aceito). **Não alterar a URL** (host/caminho/query fazem parte da assinatura; o `/storage` é removido pelo nginx antes de chegar ao MinIO — `src/minio.ts:29-46`). Limite no nginx: 1024 MB.
5. `POST /api/anexo/confirmar?identificador=<id>` — **identificador na query, não no corpo**, sem corpo. → `{"confirmado":true,"upload_status":1}`; 404 `Anexo não encontrado`; 400 `Arquivo não encontrado no armazenamento` se o objeto não está no MinIO (`src/anexos.ts:83-95`).
   - Se o cliente não confirmar, a tarefa `VerificacaoAnexos` (a cada 120 s) confirma sozinha os que já estão no MinIO e marca como **falho (2)** os pendentes há mais de 15 min (`src/tarefas.ts:46-67`).
6. Enviar a mensagem com o conteúdo `{ "ordem": n, "tipo": 2|3|4|5, "conteudo": "<identificador>" }` (§10.3).
7. Avatar: após o upload (tipo 2), `PATCH /api/usuario` com `avatar_anexo_id` = id numérico.

Caminho do objeto no bucket: `conversa/AAAA/MM/DD/<identificador>` com a data do servidor (`src/anexos.ts:68-70`).

### 8.3 Download

`GET /api/anexo?identificador=<id>` → `{"url":"https://<host>/storage/...?X-Amz-..."}` (GET pré-assinado, **600 s**) — `src/anexos.ts:41-56`.
- 404 `Anexo não encontrado`; 400 `Upload ainda não foi concluído` (pendente e sem objeto no MinIO; se o objeto existe, marca como concluído e entrega a URL); 400 `Upload falhou`.
- **Não há controle de acesso** por conversa nesta rota nem em `/anexo/existe`: qualquer usuário logado que conheça o identificador obtém a URL (`src/rotas.ts:134-136`).
- A URL usa o host da requisição; baixe pelo mesmo host. Range requests passam ao MinIO (`proxy_buffering off`). Cachear localmente pelo `identificador` (conteúdo imutável), nunca pela URL (expira).
- `/storage/` responde com `Content-Security-Policy: sandbox` (exceto PDF) — irrelevante para o app nativo.

### 8.4 Miniaturas e metadados

**Não existem.** O servidor não gera thumbnail, não guarda largura/altura/~~duração~~/mime. O cliente baixa o arquivo inteiro (ou gera miniatura localmente). O tipo de mídia deve ser inferido por `tipo` + `extensao` do conteúdo da mensagem.

🆕 `ef13c22` — **exceção: áudio.** Para conteúdos tipo 4 e 5, o servidor guarda a **duração** e a **intensidade** (onda) que o cliente mandou no `PUT /anexo` (§8.2) e as devolve em cada conteúdo das mensagens (`duracao`, `intensidade`, §10.2). Se ninguém mandou, vêm `null`: o cliente mede ao tocar, como antes. Imagem e vídeo continuam sem metadados.

### 8.5 Galeria — `GET /api/anexos`

Query (`src/esquemas.ts:75-84`): `conversa` (0 = todas as do usuário), `autor` (0 = todos), `direcao` (`''`, `enviados`, `recebidos`), `tipos` (CSV de 2..5, vazio = todos), `antes` (anexo_id; paginação), `limite` (0 → 50; máx. 200). Ordem `anexo.id desc` (`src/anexos.ts:97-169`).

Resposta: `[{ "anexo_id": int, "identificador": str, "nome": str|null, "extensao": str|null, "tamanho": int, "criado_em": date, "tipo": int, "mensagem_id": int, "conversa_id": int, "conversa_descricao": str|null, "autor_id": int, "autor_nome": str, "url": str|null }]`.
- Só anexos com `upload_status = 1`, de conversas em que o usuário é membro, e mensagens visíveis (agendadas alheias ficam de fora). **Mensagens excluídas (ocultas) continuam aparecendo** (não há filtro de `excluida_em`).
- Um mesmo anexo enviado em várias mensagens aparece várias vezes (uma por conteúdo).
- Erros 400: `Direção inválida (use enviados, recebidos ou vazio).`, `Tipo inválido: x`, `Tipo não permitido: 6`, `Filtro de autor incompatível com direção "recebidos".`; 403 se `conversa` alheia.

### 8.6 Transcrição de áudio

- `GET /api/anexo/transcricao?identificador=` → `{"status":0|1|2|3,"texto":string,"erro":string}` (0 nenhuma, 1 processando, 2 concluída, 3 erro) — `src/transcricoes.ts:59-69`.
- `PUT /api/anexo/transcricao` com corpo `{"identificador":"..."}` → inicia (ou devolve a existente). Resposta imediata `{"status":1,"texto":"","erro":""}`; se já concluída devolve status 2 sem reenviar; se processando há menos de 30 min devolve status 1 sem duplicar; status 3 ou processando há ≥ 30 min reinicia (`src/transcricoes.ts:71-101`).
- Regras: o identificador precisa ser conteúdo **tipo 4 ou 5** numa conversa da qual o usuário participa, senão **403** `Acesso negado!` (`src/transcricoes.ts:39-57`). Sem `transcritor_url` configurado: 400 `Transcrição não configurada: defina o parâmetro transcritor_url.`
- **Não há evento WebSocket** ao concluir: o cliente faz polling do GET (o teste usa 250 ms; sugerido 2–3 s).
- A transcrição é por anexo (`identificador`), compartilhada por todas as conversas onde o áudio aparece, e vem embutida nas mensagens (`transcricao_status`, `transcricao` — §10.2).
- Internamente a API envia o áudio ao "transcritor-api" (`POST {url}/jobs` multipart com `file`, `language`, `diarization=false`, `alignment=false`; polling `GET /jobs/{id}` a cada 2 s; download `GET /jobs/{id}/download?formato=txt`) — `src/transcricoes.ts:103-157`. O cliente não fala com o transcritor.
- 🆕 `ef13c22` (migração 045): a tabela `anexo_transcricao` virou **`anexo_audio`**, uma linha por áudio, com a transcrição e os dados do áudio (`duracao`, `intensidade`, §8.2). As colunas da transcrição ganharam o prefixo `transcricao_`, e o status ficou **nulo** enquanto ninguém pede a transcrição (a linha pode existir só com a duração). **Para o cliente nada muda**: `GET`/`PUT /anexo/transcricao` continuam com `{status, texto, erro}`, e status nulo sai como **0** (`transcricoes.ts`, `resposta`); nas mensagens, `transcricao_status` também sai 0.

---

## 9. Chamadas

Código: `src/chamadas.ts`. Testes: `tests/chamadas.test.ts`, `tests/atividades.test.ts`, `tests/websocket.test.ts`. Cliente de referência: `conversa-web/src/stores/call.ts`.

### 9.1 Enums (`src/chamadas.ts:14-17`, `src/tabelas.ts:7-9`)

- **Tipo da chamada** (`chamada.tipo`): `1` e `2`. **Atenção: o significado é duplo.**
  - Para o cliente web e `tabelas.ts`: 1 = áudio, 2 = vídeo.
  - Para a migração 008 e para a lógica de status no servidor (`atualizarStatusChamada`, `src/chamadas.ts:472-479`): 1 = "Simples" (1:1), 2 = "Grupo".
  - Padrão quando `tipo` não é enviado: `usuarios.length === 2 ? 1 : 2` (`src/chamadas.ts:90`).
  - Consequência: uma chamada de **vídeo 1:1** segue a regra de **grupo**, e uma chamada de **áudio em grupo** segue a regra de **1:1** (§9.4).
  - 🆕 `6d20ff7` O `tipo` **pode mudar** no meio da chamada: `POST /chamada/video` grava `tipo = 2` numa chamada `tipo = 1` (`chamadas.ts`, `ativarVideo`). Para o web, "áudio que ganhou vídeo vira chamada de vídeo"; para a regra de status, a chamada passa da regra de 1:1 para a de **grupo** daí em diante (§9.4, S11 do doc 08).
- **Status da chamada** (`chamada.status`): 1 Pendente, 2 Recusada, 3 Em andamento, 4 Encerrada, 5 Desconectada, 6 Cancelada. (5 nunca é gravado pelo código atual.)
- **Status do participante** (`chamada_usuario.status`): 1 Pendente (tocando), 2 Recusou, 3 Entrou (está na chamada), 4 Saiu, 5 Desconectou (nunca gravado pelo código atual). O comentário da migração 008 tem outra ordem — vale o código.
- **Eventos** (`chamada_evento.tipo`): 1 Iniciada, 2 Cancelada, 3 Convidado, 4 Recusou, 5 Entrou, 6 Saiu, 7 Finalizada, 8 Não atendeu.

### 9.2 Ciclo de vida

| Ação | Rota / corpo | Efeito no banco | Eventos | Ref. |
|---|---|---|---|---|
| Iniciar | `PUT /api/chamada/iniciar` `{ "tipo"?: 1\|2, "conversa_id"?: int\|null, "usuarios": [{"id": int}, ...] }` | Cria `chamada` (status 1). Para cada item de `usuarios`: se for o próprio → status 3 (entrou), senão status 1. **O chamador deve incluir a si mesmo em `usuarios`** (senão não é participante e todas as ações respondem 404). `conversa_id` ≤ 0 vira `null`. 🆕 `ef13c22`: antes de criar, confere a permissão pelo `tipo` (o enviado ou o padrão): 1 → `chamada_audio`, 2 → `chamada_video` (403 `Você não tem permissão para: fazer chamadas de áudio.` / `… de vídeo e ligar o vídeo na chamada.`) | WS 51 a todos os participantes menos o criador | `chamadas.ts:88-110`, 🆕 `chamadas.ts` (`iniciarChamada`) |
| Atender | `POST /api/chamada/entrar` `{ "id" }` | participante → 3 e `entrou_em` (só se ainda não entrou); recalcula status (§9.4) | WS 54 aos outros **e a si mesmo**; se o chat da chamada existe, entra no grupo e recebe WS 40 | `chamadas.ts:199-212` |
| Recusar | `POST /api/chamada/recusar` `{ "id", "nao_atendeu"?: bool }` | participante → 2 (só se ainda não entrou). Chamada → 2 se todos menos um recusaram. `nao_atendeu: true` (tocou até o fim, recusado automaticamente pelo app) registra evento 8 e **atividade de chamada perdida** | WS 53 aos outros **e a si mesmo** | `chamadas.ts:164-197` |
| Sair | `POST /api/chamada/sair` `{ "id" }` | participante → 4 e `saiu_em`; recalcula status | WS 55 aos outros. **Não** envia 52 mesmo que a chamada termine | `chamadas.ts:214-225` |
| Cancelar | `POST /api/chamada/cancelar` `{ "id" }` | chamada → 6 (qualquer participante pode; não confere se é o criador) | WS 52 aos outros | `chamadas.ts:153-160` |
| Finalizar | `POST /api/chamada/finalizar` `{ "id" }` | chamada → 4 + `finalizada` (qualquer participante) | WS 52 aos outros | `chamadas.ts:293-300` |
| Adicionar pessoa | `PUT /api/chamada/usuario` `{ "chamada_id", "usuario_id" }` | novo participante status 1 (não confere duplicidade: repetir = linha duplicada) | WS 51 a todos menos quem adicionou | `chamadas.ts:283-291` |
| 🆕 Chamar de novo | `POST /api/chamada/chamar-novamente` `{ "chamada_id", "usuario_id" }` | quem recusou/não atendeu volta a status 1 (evento 3) | WS 51 só ao alvo; WS 57 `{acao:'participantes'}` aos outros (§9.14) | `chamadas.ts` (`chamarNovamente`, `b8ea0c2`) |
| Anunciar vídeo | `POST /api/chamada/video` `{ "id" }` | 🆕 `ef13c22`: pede a permissão `chamada_video` (403, conferida depois da participação). ~~–~~ 🆕 `6d20ff7`: `chamada.tipo` 1 → **2** (só se era 1; repetir não muda nada). O resumo (§9.8), o histórico (§9.7) e `/chamada/dados` passam a dizer vídeo. Só confere a participação, não o status: chamar depois de encerrada também muda o `tipo` no histórico, mas o resumo já gravado fica como estava | WS 56 aos outros (o web oferece "Apenas assistir") | `chamadas.ts:302-305`, 🆕 `chamadas.ts` (`ativarVideo`) |
| Chat da chamada | `PUT /api/chamada/chat` `{ "id" }` | §9.12 | §9.12 | `chamadas.ts:230-266` |

Todas as ações exigem que o usuário seja participante (qualquer status) — senão **404 `Chamada não encontrada!`** (não 403) — `src/chamadas.ts:70-82`. Todas respondem `{ "id": <chamada> }` (exceto iniciar, chat, video e 🆕 chamar-novamente, que devolve os dados da chamada).

🆕 `ef13c22` — **permissões nas chamadas:** só **iniciar** (`chamada_audio`/`chamada_video`, pelo `tipo`) e **ligar o vídeo** (`chamada_video`) são conferidos. Atender, recusar, sair, adicionar pessoa, chamar de novo e o chat da chamada não pedem permissão. `compartilhar_tela` existe, mas o servidor não confere (a tela vai pelo MediaMTX e pelo sinal 57; só o cliente esconde o botão). Um **bot** pode ser convidado (é um usuário comum), mas nunca atende: toca até o cliente desistir e vira chamada perdida para ele (§13.7).

`notificarMembrosChamada` lança erro (→ **500 `Chamada não encontrada!`**) se não há nenhum outro participante (ex.: iniciar só consigo mesmo) — `src/notificacoes.ts:33-35`.

Ao chegar a status final (2, 4, 5, 6), após `cancelar`, `recusar`, `sair` e `finalizar`:
- **Mensagem de resumo** (tipo 6) na conversa da chamada (se `conversa_id` não é nulo), uma só por chamada (§9.8).
- **Chamada perdida** (atividade tipo 4) para quem ainda estava com status 1 (tocando) — `src/chamadas.ts:112-151`.

### 9.3 Formato "dados da chamada" (`PUT /chamada/iniciar` e `GET /chamada/dados?id=`)

`src/chamadas.ts:332-389`:
```json
{
  "id": 10,
  "iniciada": null,
  "finalizada": null,
  "tipo": 2,
  "status": 1,
  "criado_em": "2026-10-06T12:00:00.000Z",
  "criado_por": 7,
  "conversa_chat_id": null,
  "usuarios": [
    { "usuario_id": 8, "usuario_nome": "Bruno Lima", "status": 1, "adicionado_por": 7, "adicionado_por_nome": "Ana Souza",
      "adicionado_em": "2026-10-06T12:00:00.000Z", "entrou_em": null, "saiu_em": null, "recusou_em": null }
  ]
}
```
- **Não traz `conversa_id`** (a conversa de origem); só `conversa_chat_id`. 🆕 `a40fd47`: `conversa_chat_id` pode ser **igual à conversa de origem** (chat reaproveitado, §9.12) e aqui vem sem filtro, mesmo para quem não é membro dela. 🆕 `6d20ff7`: em ligação nova isso só acontece quando ela foi feita dentro do chat de outra ligação; ligações resolvidas entre `a40fd47` e `6d20ff7` podem continuar apontando para uma direta ou um grupo comum.
- 🆕 `6d20ff7` `tipo` passa a 2 depois de `POST /chamada/video` numa chamada de áudio (§9.2).
- 🆕 `b8ea0c2`: depois de um "chamar de novo", o alvo volta a `status: 1`, mas `recusou_em` continua preenchido (é o último evento 4/8); `adicionado_por`/`adicionado_em` passam a refletir o evento 3 novo.
- `iniciada` é gravada quando a chamada passa a 3; `finalizada` quando passa a 4. Cancelada/recusada ficam com `finalizada = null`.
- As datas de `usuarios` vêm dos **eventos** (último de cada tipo), não das colunas de `chamada_usuario`. `recusou_em` cobre eventos 4 e 8.
- Ordem de `usuarios`: não definida (sem `order by`).

### 9.4 Regras automáticas de status (`src/chamadas.ts:452-505`)

Recalculadas após entrar/recusar/sair, só enquanto a chamada está 1 ou 3:
- `tipo = 1`: alguém recusou → 2; alguém saiu → 4; **todos** com status 3 → 3.
- `tipo = 2`: recusaram = total−1 → 2; saíram = total−1 → 4; algum entrou → 3.
- Em `recusar`, antes disso: se recusaram = total−1 → 2, qualquer tipo.

Implicações para o cliente:
- Chamada `tipo 1` com 3+ pessoas: a primeira que sair encerra (status 4) para todos no banco, mas os outros recebem só WS 55.
- Chamada termina por saída sem WS 52: ao receber 55 o cliente deve consultar `GET /chamada/dados` e encerrar se `status` for 4 (ou se, em 1:1, o outro saiu).
- 🆕 `6d20ff7` Chamada `tipo = 1` em que alguém ligou o vídeo passa a `tipo = 2` e segue a regra de grupo daí em diante: com 3+ pessoas, a primeira saída **deixa de** encerrar a chamada e a primeira recusa deixa de recusá-la. Em 1:1 (2 participantes) com a chamada já em andamento, as duas regras dão o mesmo resultado.

### 9.5 ICE / TURN — `GET /api/ice`

`src/chamadas.ts:562-594`, `tests/configuracao.test.ts:172-197`.

```json
{
  "iceServers": [ { "urls": "turns:meu.dominio:8443?transport=tcp", "username": "1791234567:7", "credential": "base64(HMAC-SHA1)" } ],
  "iceTransportPolicy": "relay"
}
```
- `urls` é **string** (não array). Dev: `turn:<host>:3478?transport=tcp`; prod: `turns:<host>:<CONVERSA_TURN_PORTA>?transport=tcp`. `<host>` = hostname do `Host` da requisição.
- Credencial temporária do coturn (`use-auth-secret`): `username = "<expira_unix>:<usuario_id>"` com validade **1 hora**; `credential = base64(HMAC-SHA1(segredo, username))`. Pedir de novo a cada chamada.
- `iceTransportPolicy`: `"relay"` se o parâmetro `turn_forcar_relay = '1'` (padrão da migração 023), senão `"all"`.
- Sem segredo do TURN (fora do Docker): `{"iceServers":[],"iceTransportPolicy":"all"}`.
- **Na prática a mídia só funciona por relay**: o MediaMTX anuncia apenas o IP do container (172.30.0.20), alcançável somente pelo coturn (`bin/mediamtx/mediamtx.docker.yml:20-24`); o coturn só aceita TCP do cliente e só retransmite (UDP) para o MediaMTX (`bin/coturn/turnserver.conf:15-17,36-41`). Use `iceTransportPolicy = RELAY` sempre, e TURN sobre TCP/TLS.

### 9.6 Chamadas pendentes — `GET /api/chamadas/pendentes`

`src/chamadas.ts:312-330`. Chamadas em que o usuário está com status 1 (tocando) e a chamada está 1 ou 3. Ordenadas por `criado_em desc`.
```json
[ { "id": 10, "tipo": 2, "status": 1, "iniciada": null, "finalizada": null, "conversa_id": 42, "criado_em": "...", "criado_por": 7 } ]
```
`conversa_id` nulo vira **0**. Usar ao abrir o app / reconectar o socket (o web faz isso no `onopen`).

### 9.7 Histórico — `GET /api/chamadas`

`src/chamadas.ts:429-450`. Query: `participante` (id de usuário, 0 = todos), `de` (texto data/hora, comparado com `criado_em` em UTC, `>=`), `ate` (data `AAAA-MM-DD`, inclusiva). Só chamadas **finalizadas** (status 2, 4, 5, 6) das quais o usuário é participante. Limite **25** sem filtros, **250** com algum filtro. Sem paginação por cursor.
```json
[ {
  "id": 10, "tipo": 1, "status": 4, "criado_em": "...", "criado_por": 7, "conversa_id": 42,
  "iniciada": "...", "finalizada": "...", "duracao": 125,
  "participantes": [ { "usuario_id": 7, "nome": "Ana Souza", "status": 3, "duracao": null, "avatar_url": null } ]
} ]
```
`duracao` em segundos (nulo se faltou início/fim). A `duracao` do participante usa `entrou_em`/`saiu_em` da tabela; o **criador nunca tem `entrou_em`** gravado na coluna (`src/chamadas.ts:101`), então a dele é sempre `null`.

🆕 `a40fd47` — cada item ganha **`conversa_chat_id`** (depois de `duracao`, antes de `participantes`): o id do chat da chamada **só quando** ele é um grupo criado para a chamada (diferente de `conversa_id`) **e** o usuário do token é membro dele; senão `null`. Ordem das chaves: `id, tipo, status, criado_em, criado_por, conversa_id, iniciada, finalizada, duracao, conversa_chat_id, participantes` (`chamadas.ts`, `historicoChamadas`).

🆕 `6d20ff7` — a regra do `conversa_chat_id` não mudou, mas como o chat agora é **quase sempre um grupo novo** (§9.12), ele passa a vir preenchido para quem é membro do chat em praticamente toda ligação que teve chat. E `tipo` = 2 quando a chamada de áudio ganhou vídeo (§9.2).

### 9.8 Mensagem de resumo da chamada (conteúdo tipo 6)

`src/chamadas.ts:507-560`. Inserida quando a chamada chega a 2, 4, 5 ou 6 e tem `conversa_id`. Remetente = criador da chamada. `conteudo` (string JSON):
```json
{
  "chamada_id": 10, "tipo": 1, "status": 4,
  "iniciada": "2026-10-06T12:00:05", "finalizada": "2026-10-06T12:02:10", "duracao": 125,
  "participantes": [ { "usuario_id": 7, "nome": "Ana Souza", "status": 3, "duracao": null } ]
}
```
- Datas **sem fuso e sem milissegundos** (UTC), formato `YYYY-MM-DDTHH24:MI:SS` — diferente do resto da API.
- Quem **entrou** na chamada recebe o resumo já como recebido+visualizado; quem não entrou fica com ele não lido (aviso de chamada perdida na conversa).
- Não gera WS 2 nem push; gera WS 3 para **todos** os membros (inclusive o autor) com o id da nova mensagem.
- 🆕 `a40fd47` **`chat_chamada_id`**: ao montar a mensagem (`GET /mensagens`, `/pesquisar`, `/postagem/respostas`), o item de `conteudos[]` com `tipo: 6` ganha a chave `chat_chamada_id` (no fim do objeto, **fora** do JSON de `conteudo`) com o id do chat da chamada — **só quando** o chat é uma conversa **diferente** da conversa onde está o resumo **e** o usuário do token é membro dele. Senão a chave **não vem** (chat reaproveitado, sem chat, ou quem não é membro). Não é gravada no conteúdo; não aparece nas citações (`mensagem_referencia.mensagem.conteudos`). O web usa a chave para mostrar o chat da ligação dentro da bolha, com opção de abrir a conversa (`conversa-web/src/components/BolhaChamada.vue:58-70,107`). Servidor: `mensagens.ts` (`chatDaChamada`).
- 🆕 `6d20ff7` Com o chat quase sempre num grupo novo (§9.12), `chat_chamada_id` passa a vir na maioria dos resumos de ligações que tiveram chat (falta só no reaproveitamento dentro do chat de outra ligação e para quem não é membro do chat). O `tipo` do JSON é 2 se o vídeo foi ligado antes de o resumo ser gravado (§9.2).

### 9.9 MediaMTX — WHIP/WHEP

O servidor **não** cria nem controla salas: o MediaMTX aceita qualquer caminho, **sem autenticação** (`bin/mediamtx/mediamtx.docker.yml:26-38`). A convenção de nomes vem do cliente web (`conversa-web/src/stores/call.ts:209-231`) e da gravação:

- Base: `https://<host>/webrtc` (nginx → `mediamtx:8889/`).
- Nome do stream de cada participante: **`call-<chamada_id>-u-<usuario_id>`** (ids só com `[a-zA-Z0-9_-]`).
- **Publicar (WHIP)**: cada participante publica a própria mídia em `POST https://<host>/webrtc/call-<chamada>-u-<eu>/whip`, `Content-Type: application/sdp`, corpo = SDP offer; resposta = SDP answer (texto). O web espera a coleta de ICE completar (até 5 s) antes de enviar o offer — **sem trickle ICE** (`call.ts:233-251`, `:369-412`).
- **Assistir (WHEP)**: para cada outro participante com status 3, `POST https://<host>/webrtc/call-<chamada>-u-<outro>/whep` com SDP offer (recvonly) → answer. 404 = o outro ainda não publicou; o web tenta de novo (`call.ts:459-560`).
- Uma única trilha de áudio e uma de vídeo por publicação (o MediaMTX responde **406** com mais de uma de cada tipo — `call.ts:378-381`).
- Quem chama publica logo após `iniciar`; quem atende publica após `entrar` (`call.ts:890-965`). Ao receber 54 (alguém entrou) ou 51 durante a chamada, (re)assinar os streams dos participantes. Reconectar WHIP/WHEP se a `PeerConnection` cair (`call.ts:440-457`, `:594-621`).
- Compartilhamento de tela: vai **no lugar da câmera** (mesmo stream) e é anunciado por sinal `{acao:'tela'}` (§9.11).

### 9.10 Gravação das chamadas (requisito de codec)

`bin/mediamtx/mediamtx.docker.yml:27-37`, `SETUP.md:197-210`:
- Todo caminho `call-*` é gravado em `/gravacoes/call-<chamada>-u-<usuario>/<AAAA-MM-DD_HH-MM-SS-ffffff>.mp4`, formato **fMP4**, segmentos de 1 h. O app não mostra e não avisa os participantes.
- **Vídeo deve ser H264 (preferido) ou VP9. VP8 — o padrão do WebRTC — NÃO é gravado pelo MediaMTX.** O web reordena os codecs com `setCodecPreferences` para `video/H264`, depois `video/VP9` (`conversa-web/src/stores/call.ts:350-367`). No Android (libwebrtc), ordenar os codecs do transceiver de vídeo com H264 primeiro (e garantir encoder H264 por hardware/software disponível).
- Áudio: Opus (padrão). Estéreo opcional via `stereo=1;sprop-stereo=1` no fmtp (só no modo "música" do web).
- Retenção: parâmetro `gravacao_dias` (padrão 90, 0 = para sempre), limpeza a cada hora pela API (`src/tarefas.ts:69-73`, `src/gravacoes.ts`).

### 9.11 Sinal da chamada (tipo 57): tela compartilhada, ponteiro remoto, chat

Transporte no §6.5. Formato de `dados` usado pelo web (`conversa-web/src/types/api.ts:374-383`, `stores/call.ts:1345-1450`) — o servidor só exige que seja um objeto ≤ 2000 caracteres:

| `dados` | Quem envia | Significado |
|---|---|---|
| `{"acao":"tela","ativa":true\|false}` | quem começou/parou de compartilhar a tela; reenviado quando alguém entra (WS 54) | a imagem do stream daquele usuário é uma tela |
| `{"acao":"ponteiro","alvo":<usuario_id dono da tela>,"x":0..1\|null,"y":0..1\|null}` | quem assiste a tela, no máximo a cada 40 ms; `x/y` nulos = ponteiro saiu | desenhar o ponteiro de `usuario_id` sobre a tela de `alvo`; some após 5 s sem atualização |
| `{"acao":"chat","conversa_id":<int>}` | **o servidor**, ao criar o chat da chamada (🆕 `a40fd47`: ou ao reaproveitar a conversa da ligação — 🆕 `6d20ff7`: só quando ela é o chat de outra ligação) | todos passam a usar essa conversa como chat da chamada. 🆕 Quem não é membro dela recebe o aviso do mesmo jeito (§9.12) |
| 🆕 `b8ea0c2` `{"acao":"participantes"}` | **o servidor**, em `POST /chamada/chamar-novamente` | alguém voltou a tocar: reler `GET /chamada/dados` para atualizar a lista (§9.14) |

### 9.12 Chat da chamada — `PUT /api/chamada/chat`

`src/chamadas.ts:227-281` (🆕 `a40fd47`: `chatChamada`, `incluirNoChatChamada`; 🆕 revisto em `6d20ff7` — em `e25c4db`, `:226-301`). Corpo `{"id": <chamada>}` → `{"conversa_id": int}`.
- Na primeira vez escolhe o chat; chamadas seguintes devolvem o mesmo (`chamada.conversa_chat_id`). "Membros do chat" = participantes com status 3, 4 ou 5 + quem pediu (convidado que não atendeu fica de fora), em ordem de nome.
- 🆕 revisto em `6d20ff7` — **regra atual: grupo novo**. Cria uma conversa **grupo (tipo 2)** com esses membros e descrição = **nomes dos membros** separados por `", "` (ex.: `Ana, Bruno`); se passar de 80 caracteres, os 77 primeiros + `...`. ~~Descrição `Chamada: <nomes>`~~: o prefixo `Chamada: ` saiu — para reconhecer o chat de chamada, use `chamada: true` em `GET /conversas` (§11.2). Ao criar: WS 40 aos membros (exceto quem pediu) e WS 57 `{acao:"chat",conversa_id}` a **todos** os outros participantes da chamada (qualquer status). Quem entra depois (`/chamada/entrar`) é adicionado ao grupo e recebe WS 40. Vale também para ligação feita numa **conversa direta** ou num **grupo comum**, mesmo com todo mundo já nela (o código explica: "o histórico deles não é o desta ligação").
- 🆕 revisto em `6d20ff7` — **único reaproveitamento**: a ligação foi feita **dentro do chat de outra ligação** (a `conversa_id` da chamada é o `conversa_chat_id` de alguma outra chamada, com origem diferente dela) **e** todos os membros acima já estão nessa conversa. Aí:
  - devolve o próprio `conversa_id` da origem (o chat da ligação anterior);
  - não cria conversa, não muda os membros e **não manda WS 40**;
  - manda WS 57 `{acao:"chat",conversa_id}` a **todos** os outros participantes da chamada (qualquer status), **inclusive quem não é membro da origem** (ex.: alguém adicionado à chamada que ainda está tocando);
  - `POST /chamada/entrar` **não inclui ninguém** nessa conversa: quem não é membro da origem fica sem acesso (403 em `GET /mensagens` e `PUT /mensagem`) — S20 do doc 08, agora restrita a este caso;
  - `GET /chamada/dados` traz `conversa_chat_id` = a origem; `GET /chamadas` e o `chat_chamada_id` do resumo **não** apontam para ela (§9.7, §9.8); em `GET /conversas` ela já era `chamada: true` (é o chat da outra ligação).
- ~~🆕 `a40fd47` **Reaproveita a conversa da ligação** quando a chamada tem `conversa_id` e todos esses membros já estão nela — que pode ser uma **conversa direta**, um grupo ou o chat de outra ligação; em `GET /conversas` ela fica com `chamada: false`.~~ 🆕 revisto em `6d20ff7`: direta e grupo comum **não** são mais reaproveitados. Ligações cujo chat foi resolvido entre `a40fd47` e `6d20ff7` continuam com `conversa_chat_id = conversa_id` (o dado gravado não muda); essas conversas não contam como "chat de outra ligação" para o reaproveitamento.
- 🆕 `6d20ff7` Efeitos para o cliente: `chamada: true` em praticamente todo chat de chamada (§11.2); `chat_chamada_id` (§9.8) e `conversa_chat_id` do histórico (§9.7) passam a vir com mais frequência; e a pesquisa numa conversa também acha mensagens dos chats das ligações feitas nela (§10.11).
- Mensagens do chat são mensagens normais (`PUT /mensagem` na `conversa_id` devolvida).

### 9.13 Chamada perdida

Atividade tipo 4 (§12) para: (a) quem estava tocando (status 1) quando a chamada chegou a status final; (b) quem recusou com `nao_atendeu: true`. Uma por pessoa por chamada (índice único `ux_atividade_chamada_perdida`, `migracoes/033.sql:21`). O web recusa automaticamente após **30 s** tocando (`conversa-web/src/stores/call.ts:1527-1532`) e também recusa com `nao_atendeu: true` quando já está em outra chamada.

### 9.14 🆕 Chamar de novo — `POST /api/chamada/chamar-novamente` (`b8ea0c2`)

`chamadas.ts` (`chamarNovamente`). Corpo `{ "chamada_id": int, "usuario_id": int }` (mesmo esquema de `PUT /chamada/usuario`) → **dados da chamada** (§9.3), não `{id}`.
- Quem pede precisa ser participante (qualquer status), senão 404 `Chamada não encontrada!`.
- Só funciona se, ao mesmo tempo: a chamada está com status **1 ou 3**; o alvo é participante com status **2** (recusou, ou recusou com `nao_atendeu`) e **`entrou_em` nulo** (nunca entrou). Senão → **400** `Só dá para chamar de novo quem não atendeu, com a chamada em andamento.` (inclusive alvo ainda tocando, alvo que já entrou e saiu, ou chamada encerrada).
- Efeitos: o alvo volta a **status 1** (tocando) e ganha um evento 3 (Convidado) com `criado_por` = quem pediu; **WS 51** só ao alvo (`usuario_id` = quem pediu); **WS 57** `{acao:"participantes"}` a todos os outros participantes, menos quem pediu e o alvo (qualquer status). O alvo volta a aparecer em `GET /chamadas/pendentes`.
- Na prática é para chamadas **`tipo = 2`**: numa `tipo = 1` a primeira recusa já leva a chamada a status 2 (§9.4), e aí o servidor responde 400. Em `tipo = 2`, se todos menos um recusaram, a chamada também já foi a status 2 ("recusaram = total−1") e não dá mais para chamar de novo.
- A chamada perdida já registrada (atividade tipo 4) não é retirada; se o alvo deixar tocar de novo, o índice único impede outra (§9.13).

---

## 10. Mensagens

Código: `src/mensagens.ts`. Testes: `tests/mensagens.test.ts`, `tests/mensagensStatus.test.ts`.

### 10.1 Modelo

Tabelas: `mensagem` (`id`, `usuario_id`, `conversa_id`, `inserida` timestamp, `alterada` timestamp, `visivel_em` **timestamptz**, `excluida_em`, `excluida_por`; 🆕 `pede_confirmacao` bool — migração 039; 🆕 `postagem` bool, `postagem_titulo` text, `postagem_id` → mensagem — migração 042), 🆕 `mensagem_confirmacao` (`mensagem_id`, `usuario_id`, `confirmada_em`; única por mensagem+usuário — migração 039), 🆕 `6d20ff7` `mensagem_confirmacao_destinatario` (`mensagem_id`, `usuario_id`; PK pelos dois — quem precisa confirmar, migração 043), `mensagem_conteudo` (`id`, `mensagem_id`, `ordem`, `tipo`, `conteudo` **bytea** UTF-8), `mensagem_referencia` (`tipo` 1 resposta / 2 encaminhada, `origem_mensagem_id` = a mensagem nova, `destino_mensagem_id` = a referenciada), `mensagem_status` (PK `conversa_id, usuario_id, mensagem_id`; `recebida`, `visualizada`, `reproduzida` timestamps), `reacao` (`mensagem_id`, `usuario_id`, `emoji` varchar(10), unique pelos três).

Tipos de conteúdo (`src/tabelas.ts:6`):

| tipo | Nome | `conteudo` enviado/recebido |
|---|---|---|
| 1 | Texto | texto UTF-8 (menções `@[Nome](id)`, blocos de código com cercas de crases) |
| 2 | Imagem | identificador do anexo |
| 3 | Arquivo | identificador do anexo (inclui vídeos e documentos) |
| 4 | Áudio (arquivo) | identificador do anexo |
| 5 | Gravação de áudio | identificador do anexo |
| 6 | Chamada | JSON do resumo (§9.8). Normalmente só o servidor cria; o servidor **aceita** tipo 6 vindo do cliente em mensagem não agendada |
| 7 | Figurinha | `pacote/nome` (§10.6) |
| 8 🆕 | Enquete (votação) | id da enquete em texto (ex.: `"42"`). **Só o servidor grava** (via `PUT /enquete`): `PUT /mensagem` com tipo 8 → 400 "Votação não pode ser enviada nem encaminhada como mensagem: crie uma nova." — ou seja, **não dá para encaminhar** uma enquete (§10.13) |

Uma mensagem tem **vários conteúdos** (lista ordenada por `ordem`), ex.: imagem + legenda.

🆕 `12cf0cf` **Postagem não é tipo de conteúdo**: é uma marca na mensagem (`postagem = true`, título opcional), e as respostas são mensagens comuns com `postagem_id` (§10.15). 🆕 `b8ea0c2` Confirmação de leitura também é uma marca (`pede_confirmacao`, §10.14).

### 10.2 Formato de mensagem devolvido (`MensagemResposta`)

`src/mensagens.ts:64-79`, montado em `:597-613` (ordem real das chaves):
```json
{
  "id": 101,
  "remetente_id": 7,
  "remetente": "Ana",
  "conversa_id": 42,
  "inserida": "2026-10-06T12:00:00.123Z",
  "alterada": null,
  "visivel_em": null,
  "excluida_em": null,
  "mensagem_referencia": { "tipo": 1, "mensagem": { "id": 99, "conversa_id": 42, "remetente": "Bruno", "inserida": "...", "excluida_em": null, "conteudos": [ ... ], "mensagem_referencia": { ... } } },
  "recebida": true,
  "visualizada": false,
  "reproduzida": false,
  "conteudos": [
    { "id": 555, "ordem": 1, "tipo": 1, "conteudo": "oi", "nome": "", "extensao": "", "transcricao_status": 0, "transcricao": "" }
  ],
  "reacoes": [
    { "emoji": "👍", "quantidade": 2, "reagiu": true,
      "usuarios": [ { "usuario_id": 8, "nome": "Bruno Lima", "reagido_em": "2026-10-06T12:01:00.000Z", "avatar_url": null } ] }
  ]
}
```
- `remetente` = **só o primeiro nome** (`substring(trim(nome) from '^([^ ]+)')`). Para nome completo, cruzar `remetente_id` com contatos/membros.
- `mensagem_referencia` e `reacoes` **só aparecem quando existem** (chaves ausentes, não `null`).
- `mensagem_referencia.mensagem` é uma `MensagemResumida` (`id`, `conversa_id`, `remetente`, `inserida`, `excluida_em`, `conteudos`, `mensagem_referencia?`) — cadeia de até **5 níveis** (`src/mensagens.ts:478-499`). Pode faltar `mensagem` se a referenciada não existir mais. A referenciada pode ser de **outra conversa** (encaminhamento) e aparece mesmo que o usuário não participe dela.
- `conteudos[]`: sempre `id`, `ordem`, `tipo`, `conteudo`, `nome`, `extensao`, `transcricao_status`, `transcricao`; para tipos 1/6/7/8, `nome`/`extensao` = `""` e `transcricao_status` = 0. Para 2–5 `nome`/`extensao` vêm do anexo (vazio se nulo) e transcrição de ~~`anexo_transcricao`~~ 🆕 `anexo_audio` (§8.6).
  - 🆕 `ef13c22` — mais duas chaves **sempre presentes**, depois de `transcricao` (e antes de `chat_chamada_id`, quando houver): **`duracao`** (`number|null`, segundos com até 2 casas, ex.: `44.95`) e **`intensidade`** (`int[]|null`, picos de 0 a 100 para a onda). Vêm preenchidas só em áudio (tipos 4/5) cujo envio mandou esses dados (§8.2); em todo o resto, `null` (`mensagens.ts`, `carregarConteudos`). Valem também nas citações (`mensagem_referencia.mensagem.conteudos`), em `/pesquisar` e em `/postagem/respostas`. Exemplo: `{ "id": 556, "ordem": 1, "tipo": 5, "conteudo": "<sha256>", "nome": "audio.webm", "extensao": "webm", "transcricao_status": 0, "transcricao": "", "duracao": 44.95, "intensidade": [0, 12, 100, 37] }`.
  - **Conteúdo de anexo cujo `identificador` não existe na tabela `anexo` é omitido** (inner join — `src/mensagens.ts:436-437`).
- Status (`src/mensagens.ts:560-610`): para o **remetente**, `recebida/visualizada/reproduzida` = **todos** os destinatários; para os demais, só o **próprio** status. Se não há linhas de status (conversa só com o autor, ou mensagem anterior à entrada do usuário na conversa), os três vêm **`true`** (0 === 0).
- `reacoes[].usuarios[].nome` = nome completo; `avatar_url` assinado por 600 s ou `null`. Ordem das reações: primeira reação de cada emoji.
- `alterada`: **sempre `null`** — não existe rota de edição de mensagem.
- 🆕 Chaves novas, **só quando existem** (ausentes, não `null`), depois de `reacoes` e nesta ordem (`mensagens.ts`, `carregarMensagens`):
  - 🆕 `b8ea0c2` **`confirmacao`** (mensagem com `pede_confirmacao`): ~~`{ "total": 2, "confirmou": false, "usuarios": [...] }`~~ 🆕 `6d20ff7`: `{ "total": 2, "confirmou": false, "pode_confirmar": true, "destinatarios": [ { "usuario_id": 8, "nome": "Bruno Lima" }, { "usuario_id": 9, "nome": "Carla Dias" } ], "usuarios": [ { "usuario_id": 8, "nome": "Bruno Lima", "confirmada_em": "<ISO>", "avatar_url": null } ] }` (chaves nessa ordem; `mensagens.ts`, `confirmacaoDaMensagem`).
    - `destinatarios` 🆕 = **quem precisa confirmar** (a lista gravada em `mensagem_confirmacao_destinatario`), com nome completo, **em ordem de nome**;
    - `total` = ~~número de linhas de `mensagem_status`~~ 🆕 `6d20ff7`: **tamanho de `destinatarios`**;
    - `confirmou` = se o **usuário do token** confirmou;
    - `pode_confirmar` 🆕 = o usuário do token está em `destinatarios` **e** ainda não confirmou (para o autor, sempre `false`). Use para mostrar o botão "Confirmar leitura". Atenção: não olha `excluida_em` — numa mensagem oculta pode vir `true`, mas confirmar dá 400 (§10.14);
    - `usuarios` = quem confirmou, na ordem da confirmação, com nome completo e avatar assinado (600 s).
    - Todos os membros veem, não só o autor (§10.14). Mesmo objeto devolvido por `POST /mensagem/pedir-confirmacao` e mandado no WS 65.
  - 🆕 `12cf0cf` **`postagem`** (mensagem marcada como postagem): `{ "titulo": "Aviso"|null, "respostas": 3, "nao_lidas": 1, "ultimas": [MensagemResposta, MensagemResposta] }`. `respostas` conta as respostas **não ocultas**; `nao_lidas` = respostas não ocultas com `visualizada` nula para o usuário do token (as dele mesmo não contam); `ultimas` = até **2** respostas, da mais antiga para a mais nova, no formato `MensagemResposta` completo (podem incluir resposta oculta, com `excluida_em`). Ver §10.15.
- 🆕 `a40fd47` `conteudos[]` de tipo 6 podem trazer `chat_chamada_id` (§9.8).

### 10.3 Enviar — `PUT /api/mensagem`

Corpo (`src/esquemas.ts:50-57`):
```json
{
  "conversa_id": 42,
  "visivel_em": "2026-10-07T09:00:00-03:00",
  "conteudos": [ { "ordem": 1, "tipo": 1, "conteudo": "texto" } ],
  "mensagem_referencia": { "tipo": 1, "origem_mensagem_id": 99 }
}
```
- `conteudos[].conteudo` é `string|null` (obrigatório como chave). `ordem` e `tipo` inteiros. Não há validação de lista vazia.
- `mensagem_referencia`: `tipo` 1 = resposta, 2 = encaminhamento (outro valor → 400 `Tipo de referência inválido!`); **`origem_mensagem_id` = id da mensagem respondida/encaminhada** (o nome é invertido em relação ao banco). Id ≤ 0 é ignorado; id inexistente → 500 (FK). Não há checagem de acesso à mensagem referenciada.
- Figurinha: `conteudo` precisa casar `^[a-z0-9-]{1,40}/[a-z0-9-]{1,40}$`, senão 400 `Figurinha inválida!` (`src/mensagens.ts:134,174-178`).
- `visivel_em` (agendamento) — §10.8.
- Corpo JSON até **1 MB** (nginx).
- 🆕 `b8ea0c2` `pede_confirmacao?: bool` (padrão `false`): pede confirmação de leitura (§10.14). Vale em qualquer conversa, sem outra validação.
- 🆕 `6d20ff7` `confirmacao_usuarios?: int[]`: **quem precisa confirmar** (só vale com `pede_confirmacao: true`; sozinha é ignorada). Sem a lista: todos os outros membros da conversa no momento do envio. Com a lista: ids repetidos contam uma vez; precisa ter ao menos um id e todos precisam ser **outros** membros (o próprio autor não entra).
- 🆕 `12cf0cf` `postagem?: { "titulo"?: string|null }`: marca a mensagem como **postagem** (só em grupo). O título passa por `trim()`, é **cortado em 150 caracteres** sem erro e vazio vira `null`. `postagem: {}` também marca.
- 🆕 `12cf0cf` `postagem_id?: int|null`: a mensagem é **resposta** dessa postagem (§10.15). Mande `null` ou omita quando não for resposta (`0` não é tratado como "sem postagem": vai para o banco e dá 500 de FK).
- 🆕 Erros 400 novos, na ordem em que são conferidos (depois de 403, tipo 8, `visivel_em`, tipo 6 agendado, figurinha e tipo de referência):
  1. `Postagem só existe em grupo.` — `postagem` ou `postagem_id` em conversa que não é grupo;
  2. `Resposta de postagem não pode ser outra postagem nem agendada.` — `postagem_id` junto com `postagem` ou `visivel_em`;
  3. `Postagem não encontrada.` — `postagem_id` não é uma postagem **desta conversa**, visível e não oculta;
  4. 🆕 `ef13c22` **403 por falta de permissão de uso** (ver abaixo);
  5. 🆕 `6d20ff7` (era o 4) `Escolha quem precisa confirmar entre os membros da conversa.` — `pede_confirmacao: true` com `confirmacao_usuarios` vazia ou com alguém que não é outro membro.
- 🆕 `ef13c22` **Permissões de uso do `PUT /mensagem`** (`mensagens.ts`, `permissoesMensagem`). O servidor junta as permissões que a mensagem pede e responde 403 com a primeira que falta (§3):
  - `mensagem_referencia` com `origem_mensagem_id` > 0: `tipo` 1 → `responder`; `tipo` 2 → `encaminhar` e **só ela** (encaminhar não confere mais nada: nem os conteúdos, nem agendar, postagem ou confirmação);
  - `postagem` (criar postagem) → `postagem` (responder a uma postagem com `postagem_id` não pede nada);
  - `pede_confirmacao: true` → `confirmacao`;
  - `visivel_em` preenchido → `agendar`;
  - por conteúdo: texto (1) com qualquer caractere `\p{Extended_Pictographic}` → `emoji` (pega também `©`, `®`, `™` e algumas setas); imagem (2) → `imagem`; áudio (4/5) → `audio`; figurinha (7) → `figurinha`; arquivo (3) → `video` se a extensão do anexo (ou do nome) for `mp4`, `webm`, `ogg`, `mov`, `m4v` ou `mkv`, senão `arquivo` (identificador que não está na tabela `anexo` também conta como `arquivo`);
  - chamada (6) não pede nada.

Resposta (`src/mensagens.ts:185-227`): a linha `mensagem` crua + referência:
```json
{ "id": 101, "usuario_id": 7, "conversa_id": 42, "inserida": "...", "alterada": null, "visivel_em": null, "excluida_em": null, "excluida_por": null,
  "mensagem_referencia": { "tipo": 1, "origem_mensagem_id": 99 } }
```
Formato **diferente** do `MensagemResposta` (usa `usuario_id`, não tem `conteudos` nem status). Para exibir, o cliente usa o que enviou ou recarrega. 🆕 A linha crua agora termina com `pede_confirmacao`, `postagem`, `postagem_titulo`, `postagem_id` (colunas novas, nessa ordem).

Efeitos: cria `mensagem_status` para cada outro membro; registra atividades (resposta → autor da original, se na mesma conversa; menções → membros mencionados; quem é respondido e mencionado recebe só a resposta) com WS 61; WS 2 + push (§7) se não agendada. 🆕 `12cf0cf` Resposta de postagem: além disso, atividade "resposta" para quem postou e quem já respondeu, e WS 64 aos outros membros (§10.15). 🆕 `ef13c22`: com `pede_confirmacao`, cada um que precisa confirmar ganha a **atividade tipo 5** (§12); e, se não é agendada (ou quando a agendada sai), os **bots** da conversa que foram chamados respondem em segundo plano (§13.7).

### 10.4 Listar / paginar — `GET /api/mensagens`

Query (`src/esquemas.ts:58-65`): `conversa` (obrigatório), `mensagemreferencia` (≥ 0, padrão 0), `mensagensprevias` (0..1000, padrão 0), `mensagensseguintes` (0..1000, padrão 0). Lógica `src/mensagens.ts:323-371`.

Ordenação por "data efetiva" `coalesce(visivel_em, inserida)` e desempate por `id`. Resposta **sempre em ordem crescente** (mais antiga primeiro), **no máximo 100 mensagens** por chamada, mesmo pedindo mais (`limit 100` em `src/mensagens.ts:531`).

| `mensagemreferencia` | `previas` | `seguintes` | Resultado |
|---|---|---|---|
| 0 | 0 | 0 | só a última mensagem |
| id | 0 | 0 | só a mensagem `id` |
| 0 | N | 0 | as N mais recentes |
| id | N | 0 | N mensagens até `id`, **incluindo `id`** |
| id | 0 | M | M mensagens a partir de `id`, **incluindo `id`** |
| id | N | M | união das duas (a referência aparece uma vez) |
| 0 | 0 | M | as M **mais antigas** da conversa |

Paginação para trás: `mensagemreferencia = id da mais antiga carregada`, `mensagensprevias = 50` e descartar a repetida. Exemplo do teste: ids m1..m5, `ref=m3, previas=2` → `[m2, m3]` (`tests/mensagensStatus.test.ts:304-312`).

Visibilidade: agendadas futuras de outros usuários não aparecem; as do próprio autor sim. **Mensagens excluídas aparecem** (com `excluida_em`).

🆕 `12cf0cf` **Respostas de postagem ficam fora**, em todos os modos da tabela: o filtro `postagem_id is null` vale para as prévias, as seguintes e a referência. `mensagemreferencia=<id de uma resposta>` com 0/0 devolve **`[]`**; com prévias/seguintes, a data da resposta serve só de cursor. A postagem em si aparece normalmente, com a chave `postagem` (§10.2).

Efeito colateral: marca como **recebidas** todas as mensagens pendentes do usuário naquela conversa (não só as devolvidas — 🆕 inclusive as respostas de postagem) e envia WS 3 aos outros membros com os ids (`src/mensagens.ts:535-546`). Membro inexistente → 403.

### 10.5 Status

- `POST /api/mensagem/visualizar` e `POST /api/mensagem/reproduzir` com `{ "conversa": int, "mensagem": int }` → `{"sucesso":true}` (`src/mensagens.ts:618-629`). Marca **uma única mensagem** (não "até aqui"); para ler várias, chamar para cada uma. `reproduzir` **não** marca visualizada. Sempre envia WS 3 aos outros, mesmo sem mudança. 🆕 `e25c4db`: `visualizar` que **marcou agora** (a mensagem ainda não estava vista) uma mensagem com atividade de resposta/menção do usuário ligada a ela (a própria mensagem, ou a postagem dela, se for resposta de postagem) manda também **WS 61** ao próprio usuário — essa atividade deixa de ser `nova` (§12). `reproduzir` não manda.
- `GET /api/mensagem/status?conversa=42&mensagem=101,102` → `[{ "conversa_id", "mensagem_id", "recebida": bool, "visualizada": bool, "reproduzida": bool, "excluida_em": date|null }]` ordenado por `mensagem_id` (`src/mensagens.ts:631-663`). Aqui os booleanos são **sempre o agregado de todos os destinatários** (mesmo para quem não é o autor). Ids sem linhas de status não aparecem. Lista vazia → `[]`; id não inteiro → 400.
- `GET /api/mensagem/status/detalhe?id=101` → `[{ "usuario_id", "nome", "recebida": date|null, "visualizada": date|null, "reproduzida": date|null }]`, ordenado por visualizada, recebida (nulos por último), nome. Só o autor (403 `Só quem enviou a mensagem vê quem recebeu e visualizou.`); 404 `Mensagem não encontrada!` (`src/mensagens.ts:665-686`).

### 10.6 Figurinhas (Lottie)

- Conteúdo tipo 7 = `"<pacote>/<nome>"`. A animação **não vem do servidor da API**: o cliente web a carrega de `/figurinhas/<pacote>/<nome>.json` servido pela página (`conversa-web/src/utils/figurinhas.ts:60-62`). Atenção: o `bin/web` atual do repositório do servidor **não contém** a pasta `figurinhas` (build de produção desatualizado); no Android, embutir as animações no app.
- ~~Catálogo atual (`conversa-web/src/utils/figurinhas.ts:15-58`): `basico/{coracao,sorriso,risada,estrela,feito,festa,sono,pontinhos}`, `rostos/{piscadinha,apaixonado,beijo,legal,surpreso,triste,bravo}`, `coisas/{joinha,fogo,coracao-partido,presente,bolo,balao,cafe,sol,chuva}`.~~ Identificador desconhecido → mostrar "Figurinha".
- 🆕 Web `d202c06` (2026-10-11, "Postagens em grupo, todos os emojis, figurinhas Noto e pesquisa nova"): o catálogo passou a ter **um só pacote, `noto`** — **55 figurinhas** (Animated Noto Emoji do Google), ids `noto/<nome>` (ex.: `noto/chorando-de-rir`, `noto/joinha`, `noto/coracao`), servidas em `/figurinhas/noto/<nome>.json` (`conversa-web/src/utils/figurinhasNoto.ts`, gerado por `scripts/baixar-figurinhas-noto.ts`; lista e nomes em português ali). Licença **CC BY 4.0**: o seletor mostra o crédito `Animated Noto Emoji © Google, CC BY 4.0` com link para `https://creativecommons.org/licenses/by/4.0/` — o Android precisa mostrar o mesmo crédito.
  - Os arquivos de `basico/`, `rostos/` e `coisas/` foram **apagados** (só `coisas/fogo.json` virou `noto/fogo.json`). Mensagens antigas com esses ids continuam no banco; no web a animação não carrega e a bolha mostra o texto **"Figurinha"** (`FigurinhaLottie.vue`). O servidor não mudou (o formato `pacote/nome` continua o mesmo).

### 10.7 Menções e código

- Menção: no texto, `@[Nome](<usuario_id>)` (`src/atividades.ts:79-82`). Só gera atividade para membros da conversa.
- Código: texto tipo 1 com cerca de 3+ crases e linguagem opcional: ```` ```kotlin\n...\n``` ````. O web usa cerca maior que as crases internas. Não há tipo de conteúdo específico para código.

### 10.8 Agendadas

- `visivel_em`: ISO-8601 (sem fuso = UTC); cortado no minuto; precisa estar entre agora+5 min e agora+1 ano (400 `visivel_em deve estar a pelo menos 5 minutos no futuro.` / `visivel_em não pode estar mais de 1 ano no futuro.` / `visivel_em inválido (use ISO-8601 com offset).`). Não pode ter conteúdo tipo 6. — `src/mensagens.ts:149-172`
- Até a hora, só o autor vê (lista, conversa, pesquisa). A tarefa a cada 60 s envia WS 2, push, WS 3 e WS 61 quando amadurece (só olha os últimos 10 min — `src/tarefas.ts:7-44`).
- Excluir antes de sair: apagada de vez (§10.9).
- Não há rota para listar "minhas agendadas" nem para reagendar.

### 10.9 Excluir / ocultar — `DELETE /api/mensagem?id=`

`src/mensagens.ts:285-321`, `migracoes/031.sql`. Só o autor (403), a qualquer momento. 🆕 `ef13c22`: depois da regra do autor, pede a permissão de uso **`ocultar`** (403 `Você não tem permissão para: ocultar as próprias mensagens.`), inclusive para apagar uma agendada que ainda não saiu.
- Mensagem já visível: **exclusão lógica** — grava `excluida_em = now()` e `excluida_por`; resposta `{ "id", "conversa_id", "excluida_em" }`. Repetir não muda a data. Apaga atividades ligadas (WS 61) e envia WS 3 aos outros.
- Agendada que ainda não saiu: **apagada fisicamente** (com referências, status, reações; 🆕 `6d20ff7`: e a lista de quem precisa confirmar). Resposta: linha da mensagem apagada + `"conteudo"` (linha de `mensagem_conteudo`).
- **Representação de mensagem oculta hoje**: a mensagem continua em `/mensagens` com `excluida_em` preenchido **e com `conteudos` completos (o texto original)** e reações. Cabe ao cliente esconder o conteúdo e mostrar "Mensagem oculta". Também: `/conversas` mostra `ultima_mensagem_texto = "Mensagem oculta"`; `/pesquisar` não acha; referências (`mensagem_referencia.mensagem.excluida_em`) trazem a marca; `/mensagem/status` traz `excluida_em`; `/anexos` continua listando os anexos dela.

### 10.10 Novas mensagens — `GET /api/mensagens/novas?desde=`

`src/mensagens.ts:688-720`. `desde` ISO-8601 (vazio = desde sempre; inválido = 400 `Parâmetro "desde" inválido (use ISO-8601 com offset).`).

Resposta: uma linha por conversa que tenha mensagem **de outra pessoa**, já visível, **não visualizada pelo usuário**, com data efetiva **maior que `desde`** (comparação truncada em milissegundos):
```json
[ { "conversa_id": 42, "mensagem_id": 105, "ate": "2026-10-06T12:00:00.123Z" } ]
```
`mensagem_id` = maior id da conversa entre as que casaram; `ate` = maior data efetiva (ms). Usar `ate` como próximo `desde` (cursor). Inclui conversas arquivadas e mensagens excluídas.

🆕 `12cf0cf` **Não filtra respostas de postagem**: uma resposta nova faz a conversa aparecer aqui, e `mensagem_id` pode ser o id de uma resposta (que não está em `GET /mensagens`). Use a linha como "há novidade na conversa", não como "a mensagem X está no fluxo" (S17 do doc 08).

### 10.11 Pesquisa — `GET /api/pesquisar?conversa=0&texto=`

`src/mensagens.ts:373-399`. `texto` obrigatório não vazio (400 `Texto inválido!`). Espaços viram `%` (palavras em sequência, qualquer coisa entre elas); sem acento e sem maiúsculas (`unaccent ilike`). Só conteúdo tipo 1, só conversas do usuário (`conversa=0` = todas), exclui excluídas e agendadas alheias. Resposta no formato `MensagemResposta`, **no máximo 100 (as mais recentes)**, em ordem crescente. Não marca recebida. 🆕 `12cf0cf` Exclui também as **respostas de postagem** (não há lugar no fluxo para onde levar).

🆕 `6d20ff7` **Na conversa, também os chats das ligações feitas nela** (`mensagens.ts`, `pesquisar`): com `conversa = X` (> 0), entram ainda as mensagens das conversas que são `conversa_chat_id` de alguma chamada com `conversa_id = X`, desde que (a) o chat seja **outra** conversa (o reaproveitamento, §9.12, fica de fora) e (b) o usuário seja membro de X **e** desse chat. Os resultados podem vir com **`conversa_id` diferente de X**: ao tocar num resultado, abrir a conversa do resultado (o chat da ligação), não X. Só um nível: chats de ligações feitas dentro desses chats não entram. O limite de 100 (as mais recentes) vale para o conjunto. Com `conversa = 0` nada muda (já eram todas as conversas do usuário).

### 10.12 Reações — `PUT /api/mensagem/reacao`

`src/mensagens.ts:722-755`. Corpo `{ "mensagem_id": int, "emoji": string }`. Alterna: se o usuário já reagiu com esse emoji, remove; senão adiciona. Um usuário pode ter vários emojis na mesma mensagem — 🆕 `d4435db`: **no máximo 5 emojis diferentes por pessoa** na mesma mensagem; o 6º → 400 `Você já reagiu com 5 emojis nesta mensagem.` (tirar um emoji continua livre). Resposta `{ "mensagem_id", "emoji", "acao": "add"|"remove" }`. 404 `Mensagem não encontrada!`; 403 se não é membro. `emoji` > 10 caracteres (code points) → 500. Efeitos: WS 7 aos outros membros; atividade de reação para o autor da mensagem (se não for ele mesmo) com WS 61. 🆕 `ef13c22`: depois do 404 e do 403 de membro, pede a permissão **`reagir`** (403) — **também para tirar** uma reação própria. A atividade de reação fica pendente até o autor ver a mensagem (`POST /atividades/reacoes-vistas`, §12).

---

### 10.13 Enquetes (votação em grupo) 🆕 — commit `8031fa5`, migração `035.sql`

- **Só em conversas de grupo** (`tipo = 2`); em direta → 400 `Votação só pode ser criada em grupos.`
- 🆕 `ef13c22` Criar pede a permissão de uso **`votacao`** (403 `Você não tem permissão para: criar votações em grupos.`), conferida depois do 403 de membro e antes do "só grupo". Votar, encerrar e mudar o prazo não pedem permissão.
- **Criar** — `PUT /api/enquete`:

  ```json
  { "conversa_id": 10, "pergunta": "Onde vamos almoçar?", "opcoes": ["Centro", "Shopping"], "multipla": false, "encerra_em": "2026-10-09T18:00:00.000Z" }
  ```

  - Validações (`src/enquetes.ts:12-30`, `esquemas.ts`):
    - `pergunta` ≤ 300 caracteres e não vazia após `trim()` (400 `Informe a pergunta da votação.`);
    - `opcoes` com 2 a 12 itens, cada ≤ 200; as vazias são descartadas e precisam sobrar ≥ 2 (400 `A votação precisa de pelo menos duas opções.`);
    - opções repetidas, sem diferenciar maiúsculas → 400 `As opções da votação não podem se repetir.`
    - 🆕 `5cad911` `encerra_em` (opcional, ISO ou `null`): a data final. O servidor corta os segundos e exige **pelo menos 1 minuto no futuro** e **no máximo 1 ano**: 400 `Data final inválida.` / `A data final precisa estar no futuro.` / `A data final não pode passar de 1 ano.`
  - O servidor cria a enquete e as opções numa transação e depois **cria a mensagem** com um único conteúdo `{ordem:1, tipo:8, conteudo:"<id da enquete>"}`. Essa mensagem passa pelo mesmo caminho de `PUT /mensagem` (WS 2, push com texto `enquete`, WS 3, atividades). O `mensagem_id` é gravado na enquete.
  - **Resposta:** os campos da mensagem incluída (`id`, `conversa_id`, `usuario_id`, …) + `enquete_id`. O web, depois de criar, recarrega as mensagens da conversa e a lista de conversas.
- **Ler** — `GET /api/enquete?id=<enquete_id>` → objeto `Enquete`:

  ```json
  {
    "id": 42, "conversa_id": 10, "mensagem_id": 900, "pergunta": "Onde vamos almoçar?",
    "multipla": false, "criado_por": 7,
    "encerra_em": "2026-10-09T18:00:00.000Z", "encerrada_em": null, "encerrada": false,
    "pode_encerrar": true, "pode_alterar_prazo": true,
    "opcoes": [ { "id": 1, "texto": "Centro", "votantes": [ { "id": 7, "nome": "Ana Souza" } ] },
                { "id": 2, "texto": "Shopping", "votantes": [] } ],
    "total_votantes": 1,
    "meus_votos": [1]
  }
  ```

  - As `opcoes` vêm em `ordem`; os `votantes` vêm na ordem do voto e com o **nome completo**.
  - `total_votantes` conta pessoas distintas: na múltipla escolha, cada pessoa conta uma vez, então as porcentagens podem somar mais de 100% (o web calcula `votos_da_opcao / total_votantes`).
  - `meus_votos` são os ids das opções em que **o usuário do token** votou.
  - 🆕 `5cad911`:
    - `encerra_em`: a data final (ou `null`);
    - `encerrada_em`: quando foi encerrada **à mão**, antes do prazo (ou `null`);
    - `encerrada`: `encerrada_em` preenchido **ou** `encerra_em` já passou, calculado na hora da leitura;
    - `pode_encerrar`: aberta e o usuário do token criou a votação **ou o grupo**;
    - `pode_alterar_prazo`: aberta e o usuário do token criou a votação.
  - 404 `Votação não encontrada!`; 403 se não é membro.
- **Votar** — `POST /api/enquete/votar {enquete_id, opcoes:[ids]}`:
  - **Substitui** o voto do usuário pelas opções enviadas: lista vazia **tira** o voto.
  - Escolha única com mais de uma opção → 400 `Esta votação aceita uma opção só.`; opção de outra enquete → 400 `Opção que não é desta votação.`
  - Resposta: o `Enquete` atualizado. Efeito: **WS 62** `{enquete_id, conversa_id}` a todos os membros, inclusive quem votou.
  - 🆕 `5cad911` Votação encerrada → 400 `Esta votação já foi encerrada.`
- 🆕 `5cad911` **Encerrar antes do prazo** — `POST /api/enquete/encerrar {enquete_id}`:
  - Só quem criou a votação ou quem criou o grupo: 403 `Só quem criou a votação ou o grupo pode encerrá-la.`; já encerrada → 400.
  - Grava `encerrada_em` e `encerrada_por`; resposta: o `Enquete`; WS 62 a todos os membros.
- 🆕 `5cad911` **Definir, adiar ou tirar a data final** — `PATCH /api/enquete {enquete_id, encerra_em}` (`null` tira):
  - Só quem criou a votação: 403 `Só quem criou a votação pode mudar a data final.`; encerrada → 400; mesmas validações de data da criação.
  - Resposta: o `Enquete`; WS 62 a todos os membros.
- 🆕 `5cad911` **Prazo vencido:** o servidor **não avisa** quando a data final passa (não há WS nem tarefa). O cliente trata `encerra_em <= agora` como encerrada, inclusive com a bolha aberta (o web encerra na hora).
- **Comportamento do cliente web** (`conversa-web` `39d06f9`):
  - Escolha única: tocar em outra opção troca o voto; tocar na marcada tira.
  - Múltipla: marca e desmarca.
  - Bolha com "📊 pergunta", "Escolha uma opção"/"Escolha uma ou mais opções", barra de porcentagem, nomes de quem votou e "N pessoas votaram"/"1 pessoa votou".
  - Prévia/resumo: "Votação".
  - 🆕 `785bdef`:
    - ao criar, "Definir data final" (sugestão: amanhã, na próxima hora cheia);
    - na bolha, "· encerra hoje 18:00" / "amanhã 08:30" / "12/10 18:00";
    - quem pode vê "Definir/Alterar data final" (com "Tirar data") e "Encerrar votação" (confirmação "Depois de encerrada, ninguém vota mais e o resultado fica como está.");
    - encerrada: "🔒 Votação encerrada <quando>", opções desabilitadas e a mais votada com 🏆.
  - 🆕 `eaa8bac` Mensagem oculta de votação: ao revelar, mostra a pergunta e a contagem por opção (só leitura).
- **Prévias e push:** `ultima_mensagem_texto` em `/conversas` e o texto do push/WS 2 dizem `enquete` (minúsculo); o web exibe "Votação".
- **Limitações:**
  - Não há rota para editar a pergunta ou as opções, listar enquetes ou ver quando cada um votou. (🆕 `5cad911`: encerrar e mudar a data final agora existem.)
  - Mensagem com enquete pode ser ocultada (`DELETE /mensagem`), mas os votos continuam acessíveis por `GET /enquete`.
  - A enquete não pode ser encaminhada (400).
  - Responder a uma mensagem de enquete é possível (a referência aponta para a mensagem).

### 10.14 Confirmação de leitura 🆕 — commit `b8ea0c2`, migração `039.sql` (🆕 lista e prazo: `6d20ff7`, migração `043.sql`)

- **Pedir no envio:** `PUT /mensagem` com `pede_confirmacao: true` (qualquer conversa) e, 🆕 `6d20ff7`, `confirmacao_usuarios?: int[]` com **quem precisa confirmar** (sem a lista: todos os outros membros no momento do envio; regras e erro 400 em §10.3). A mensagem passa a trazer `confirmacao` em `MensagemResposta` (§10.2), para **todos** os membros. Não gera WS 65 (a mensagem nova já chega com o pedido).
- 🆕 `6d20ff7` **Quem precisa confirmar** fica gravado em `mensagem_confirmacao_destinatario` (migração 043). As mensagens que já pediam confirmação antes da 043 ganharam como lista os destinatários de então (as linhas de `mensagem_status`).
- 🆕 `6d20ff7` **Pedir depois** — `POST /api/mensagem/pedir-confirmacao {mensagem_id, usuarios?: int[]}` → o objeto **`confirmacao`** (§10.2) calculado para quem pediu (`mensagens.ts`, `pedirConfirmacao`). Erros, nesta ordem:
  1. 404 `Mensagem não encontrada!`;
  2. 403 `Acesso negado!` — não é membro da conversa;
  3. 403 `Só quem enviou pede confirmação de leitura.`;
  - 🆕 `ef13c22` 403 `Você não tem permissão para: pedir confirmação de leitura.` — falta a permissão de uso `confirmacao` (entre o 3 e o 4);
  4. 400 `Mensagem apagada.` — mensagem oculta (`excluida_em`);
  5. 400 `Esta mensagem já pede confirmação de leitura.` — o pedido é um só: não dá para pedir de novo nem mudar a lista;
  6. 400 `A confirmação de leitura só pode ser pedida até 24 horas depois do envio.` — prazo: `coalesce(visivel_em, inserida)` + **24 h** (numa agendada, conta a partir da hora marcada para sair; antes de sair, está sempre no prazo);
  7. 400 `Escolha quem precisa confirmar entre os membros da conversa.` — mesma regra de `confirmacao_usuarios` (§10.3); sem `usuarios`, a lista é de todos os outros membros **no momento do pedido** (não do envio).
  - Efeitos: grava `pede_confirmacao = true` e a lista (numa transação) e manda **WS 65** `{conversa_id, mensagem_id, usuario_id, confirmacao}` a **todos** os membros da conversa, **inclusive quem pediu**, cada um com a `confirmacao` calculada para si (§6.6). Sem push, ~~sem atividade,~~ sem WS 2/3. Numa agendada que ainda não saiu, os outros recebem o WS 65 de uma mensagem que ainda não veem (S25 do doc 08).
  - 🆕 `ef13c22` Cria a **atividade tipo 5** para cada um da lista, com a data **de agora** (mesmo numa agendada que ainda não saiu), e manda WS 61 a eles (§12, S25 do doc 08).
- **Confirmar** — `POST /api/mensagem/confirmar {mensagem_id}` → `{ "mensagem_id": int }` (`mensagens.ts`, `confirmarLeitura`). Erros, nesta ordem:
  1. 404 `Mensagem não encontrada!`;
  2. 403 `Acesso negado!` — não é membro da conversa;
  3. 400 `Esta mensagem não pede confirmação de leitura.` — sem o pedido **ou** mensagem oculta;
  4. 400 `Quem enviou não confirma a própria mensagem.`;
  5. 🆕 `6d20ff7` 403 `Você não está entre quem precisa confirmar esta mensagem.` — o usuário não está na lista (`destinatarios`).
- **Idempotente:** confirmar de novo devolve 200 e não muda nada (`on conflict do nothing`); não há como desfazer.
- **WS 8** `{conversa_id, mensagem_id, usuario_id, nome, confirmada_em}` só na **primeira** confirmação daquela pessoa, a **todos os outros membros** (inclusive o autor). Quem confirmou não recebe, nem nos outros aparelhos dele (eles só veem ao recarregar).
- **Não mexe** em `recebida`/`visualizada` (não substitui `POST /mensagem/visualizar`) e não gera atividade nem push. 🆕 `ef13c22`: a atividade tipo 5 de quem confirmou deixa de ser pendente (e ele recebe WS 61, na 1ª confirmação).
- 🆕 `ef13c22` **Atividade "pediu confirmação" (tipo 5):** cada um que precisa confirmar ganha uma atividade (`autor_id` = quem enviou, `mensagem_id` = a mensagem), no envio (`PUT /mensagem`; numa agendada, com `criado_em = visivel_em`, só aparece quando ela sai) ou no pedido depois (`pedir-confirmacao`, com a data de agora). Fica **pendente até confirmar**; concluir à mão (`POST /atividade/concluir`) tira do contador, mas **não** confirma a leitura (`pode_confirmar` continua `true`). Ocultar a mensagem apaga essas atividades.
- 🆕 `ef13c22` **Bots na lista:** sem `confirmacao_usuarios`, a lista é de todos os outros membros, **inclusive bots** — e um bot nunca confirma (o "x de y" não fecha; S27 do doc 08). Para evitar, mande a lista sem os bots (`bot: true` em `/usuario/contatos`).
- ~~Quem entrou no grupo depois do envio não tem linha de status, não conta em `total`, mas pode confirmar (o servidor não confere).~~ 🆕 `6d20ff7`: **só quem está na lista confirma** (403 para os outros). Quem entrou no grupo depois do envio não está nela — salvo se o pedido foi feito depois da entrada, por `pedir-confirmacao` (sem `usuarios`, ou com ela na lista). Confirmações dadas antes da 043 por quem não estava na lista continuam em `usuarios` (aparecem "a mais", fora de `destinatarios`).
- 🆕 `6d20ff7` Quem **sai** da conversa continua na lista (conta em `total` e aparece em `destinatarios`), mas não consegue mais confirmar (403 `Acesso negado!`): o "x de y" dessa mensagem não se completa (S25 do doc 08).
- Excluir (ocultar) a mensagem mantém as confirmações (🆕 e a lista); apagar uma agendada apaga as confirmações (🆕 e a lista) junto.

### 10.15 Postagens em grupo 🆕 — commit `12cf0cf`, migração `042.sql`

- **Não é tipo de conteúdo:** a postagem é uma mensagem comum (qualquer conteúdo) com `postagem = true` e título opcional (`postagem_titulo`). As respostas são mensagens comuns com `postagem_id` = id da postagem.
- **Criar a postagem:** `PUT /mensagem` com `postagem: {titulo?}` — só em grupo (400 `Postagem só existe em grupo.`). Pode ser agendada, citar outra mensagem, pedir confirmação. Gera WS 2, push e atividades como uma mensagem comum. 🆕 `ef13c22`: pede a permissão de uso `postagem` (403); **responder** a uma postagem (`postagem_id`) não pede permissão (§10.3).
- **Responder:** `PUT /mensagem` com `postagem_id` (§10.3). Regras: só em grupo; não pode ser postagem nem agendada; a postagem precisa ser **desta conversa**, estar visível e **não oculta** (postagem oculta não recebe resposta nova → 400 `Postagem não encontrada.`). O servidor não impede `mensagem_referencia` nem `pede_confirmacao` numa resposta.
- **Efeitos da resposta** (`mensagens.ts`, `registrarRespostaPostagem`):
  - WS 2 e push como qualquer mensagem (§7.1);
  - **atividade tipo 2 (Resposta)** para quem postou e para quem já respondeu (respostas não ocultas), menos quem respondeu agora — com `mensagem_id` = **id da postagem** (não da resposta); uma atividade por resposta (a mesma pessoa pode acumular várias); WS 61;
  - menção (`@[Nome](id)`) ou citação dentro da resposta seguem a regra normal (§12): a atividade aponta para a **resposta**, que está fora do fluxo;
  - **WS 64** `{conversa_id, postagem_id, usuario_id}` a todos os outros membros.
- **Fora do fluxo:** respostas **não** aparecem em `GET /mensagens` (nenhum modo; `mensagemreferencia=<resposta>` 0/0 → `[]`) nem em `GET /pesquisar`. **Mas** contam como mensagens comuns em:
  - `GET /conversas`: `mensagem_id`, `ultima_mensagem` e `ultima_mensagem_texto` podem ser de uma resposta; `mensagens_sem_visualizar` conta respostas não vistas (§11.2);
  - `GET /mensagens/novas` (§10.10);
  - WS 3 (ids de respostas), `GET /anexos` (anexos das respostas, com `mensagem_id` da resposta) e o resumo por IA (§13.5).
  - Para zerar o contador, as respostas precisam ser marcadas: `POST /postagem/visualizar` (todas da postagem) ou `POST /mensagem/visualizar` (uma por uma).
- **Ler as respostas** — `GET /api/postagem/respostas?id=<postagem>` → `MensagemResposta[]`, as **100 mais recentes**, da mais antiga para a mais nova; inclui respostas ocultas (com `excluida_em`). 404 `Postagem não encontrada.` se o id não é postagem; 403 se não é membro. **Não** marca recebida. O resumo `postagem` na própria mensagem traz só as 2 últimas (§10.2).
- **Marcar como vistas** — `POST /api/postagem/visualizar {postagem_id}` → `{ "vistas": <quantas mudaram> }`. Marca `visualizada` (e `recebida`, se faltava) em **todas** as respostas da postagem ainda não vistas pelo usuário; se mudou alguma, WS 3 aos outros membros com os ids (se não mudou nada, não manda nada — diferente de `/mensagem/visualizar`); 🆕 `e25c4db` e, se alguma das respostas marcadas (ou a própria postagem) tem atividade de resposta/menção do usuário ligada, **WS 61** ao próprio usuário — a atividade "respondeu" deixa de ser `nova` (§12). Mesmos 404/403. O web chama quando a bolha da postagem aparece com `nao_lidas > 0`, a conversa aberta e a janela em foco, e depois recarrega `/conversas` (`conversa-web/src/components/BolhaPostagem.vue:152-160`, `stores/chat.ts:1337-1342`).
- **Ocultar:** ocultar a postagem apaga as atividades que apontam para ela (inclusive as "respondeu"); as respostas continuam. Ocultar uma resposta não apaga as atividades "respondeu" (elas apontam para a postagem).
- **Contagens:** `postagem.respostas` e `nao_lidas` ignoram respostas ocultas; `GET /postagem/respostas` e `ultimas` não.

## 11. Conversas

Código: `src/conversas.ts`. Testes: `tests/conversas.test.ts`.

### 11.1 Modelo

`conversa`: `id`, `descricao` varchar(100) nulo, `tipo` (1 direta/"chat", 2 grupo), `inserida`, `criado_por`; 🆕 `b8ea0c2` (migração 038) `avatar_anexo_id` → anexo (imagem do grupo) e `emoji` text (emoji no lugar da inicial). `conversa_usuario`: `id`, `usuario_id`, `conversa_id`, `fixada_ordem` int nulo, `arquivada_em` timestamp nulo, `criado_em`, `criado_por` (único `conversa_id+usuario_id`). Fixar e arquivar são **por usuário**.

Não existe conceito de **administrador** de grupo: qualquer membro renomeia, adiciona membros e tenta excluir; cada um só remove a si mesmo (`src/autorizacao.ts:17-26`).

### 11.2 Lista — `GET /api/conversas`

`src/conversas.ts:56-178`. Cada item:
```json
{
  "id": 42,
  "descricao": "Bruno Lima",
  "tipo": 1,
  "inserida": "2026-10-01T10:00:00.000Z",
  "nome": "Bruno Lima",
  "destinatario_id": 8,
  "mensagem_id": 105,
  "ultima_mensagem": "2026-10-06T12:00:00.123Z",
  "ultima_mensagem_texto": "oi @[Ana](7)",
  "mensagens_sem_visualizar": 3,
  "emoji": null,
  "fixada_ordem": null,
  "arquivada_em": null,
  "chamada": false,
  "avatar_url": "https://<host>/storage/...|null"
}
```
- 🆕 `b8ea0c2` Ordem real das chaves: `id, descricao, tipo, inserida, nome, destinatario_id, mensagem_id, ultima_mensagem, ultima_mensagem_texto, mensagens_sem_visualizar, emoji, fixada_ordem, arquivada_em, chamada, avatar_url` (`conversas.ts`, `conversas`; o `avatar_url` é posto no fim por `comAvatarUrl`).
- `descricao`: descrição da conversa (trim); se vazia/nula, o nome do destinatário (só para tipo 1).
- `nome`, `destinatario_id`: só em conversas **tipo 1** (o outro membro; se o usuário está sozinho, ele mesmo). Em grupos vêm `null`. 🆕 `ef13c22`: o outro membro pode ser um **bot** (§13.7); a lista não diz — cruze `destinatario_id` com `bot: true` de `/usuario/contatos`. Bot excluído com histórico continua na conversa direta (com o nome), mas some dos contatos.
- `avatar_url`: em conversa **tipo 1**, o avatar do outro membro; ~~**Grupos não têm avatar.**~~ 🆕 `b8ea0c2`: em **grupo**, a imagem do grupo (`conversa.avatar_anexo_id`), ou `null`.
- 🆕 `b8ea0c2` `emoji`: emoji do grupo (texto com um único grafema) ou `null`. Com imagem e emoji, o web mostra a imagem; sem nenhum, a inicial do nome.
- 🆕 `b8ea0c2` `chamada`: `true` quando a conversa é o **grupo criado como chat de uma chamada** (`chamada.conversa_chat_id` aponta para ela). 🆕 `a40fd47`: `false` quando o chat reaproveitou a conversa da ligação (`conversa_chat_id = conversa_id`) — a conversa continua uma conversa comum. 🆕 `6d20ff7`: como o chat passou a ser **sempre um grupo novo** (salvo ligação feita dentro do chat de outra ligação, que já é `chamada: true`), praticamente todo chat de chamada vem com `chamada: true`; só as diretas/grupos reaproveitados entre `a40fd47` e `6d20ff7` ficam `false`. O nome do grupo novo é só a lista de nomes (`Ana, Bruno`), sem o prefixo `Chamada: ` — o `chamada` é a única marca confiável (§9.12). O web põe essas conversas numa seção "Chamadas" da lista, salvo se estiverem fixadas ou arquivadas (`conversa-web/src/components/ChatSidebar.vue:286-292`).
- `mensagem_id`: id da última mensagem (0 se não há). `ultima_mensagem`: data efetiva da última (nulo se não há). 🆕 `12cf0cf`: **podem ser de uma resposta de postagem** (fora do fluxo, §10.15).
- `ultima_mensagem_texto` (do **primeiro conteúdo** da última mensagem): `"Mensagem oculta"` se excluída; texto **cru** se tipo 1 (com a marcação de menção e de código — não resumido); `"imagem"` (2); `"figurinha"` (7); `"enquete"` (8) 🆕; `""` para arquivo, áudio, gravação e chamada. 🆕 `12cf0cf`: pode ser o texto de uma resposta de postagem.
- `mensagens_sem_visualizar`: quantidade de mensagens visíveis com `recebida` **ou** `visualizada` nula para o usuário. 🆕 `12cf0cf`: **inclui respostas de postagem** não vistas; elas só saem do contador com `POST /postagem/visualizar` ou `POST /mensagem/visualizar` com o id de cada resposta (S17 do doc 08).
- **Arquivadas vêm na lista** (com `arquivada_em` preenchido): o cliente filtra.
- Ordenação: `ultima_mensagem desc` — no Postgres, `DESC` põe **nulos primeiro**: conversas sem mensagens aparecem no topo. Fixadas não são ordenadas pelo servidor: ordenar no cliente por `fixada_ordem` (1 = primeira).
- `avatar_url` expira em 600 s.

### 11.3 Criar — `PUT /api/conversa`

`src/conversas.ts:39-44`. Corpo `{ "descricao"?: string|null, "tipo"?: int }` (tipo padrão 1 no banco; não validado). 🆕 `ef13c22`: `tipo: 2` (grupo) pede a permissão de uso **`grupo`** (403 `Você não tem permissão para: criar grupos.`); conversa direta (ou sem `tipo`) não pede nada. O chat da chamada (§9.12) é criado pelo servidor e não passa por essa regra. O criador entra como membro. Resposta: `{ "id", "descricao", "tipo", "inserida", "criado_por" }` (🆕 `b8ea0c2`: + `avatar_anexo_id`, `emoji`, nulos; imagem e emoji só por `PATCH /conversa`). **Nenhum evento** é enviado na criação.

Fluxo (como o web/teste): criar e depois `PUT /api/conversa/usuario` para cada membro. O servidor **não deduplica conversas diretas**: antes de criar uma tipo 1, procurar em `/conversas` uma com `tipo=1` e `destinatario_id` = contato.

### 11.4 Membros

- `GET /api/conversa/usuarios?conversa=` → `[{ "id": <conversa_usuario.id>, "usuario_id", "nome", "avatar_url" }]`. Quem não é membro recebe `[]` (não 403). Sem ordem definida (`src/conversas.ts:203-220`).
- `PUT /api/conversa/usuario` `{ "usuario_id", "conversa_id" }` → linha `conversa_usuario`. Se já é membro, devolve a linha existente (sem erro e sem evento). Novo membro: WS 40 a todos os membros exceto quem adicionou (`src/conversas.ts:222-234`).
- `DELETE /api/conversa/usuario?id=<conversa_usuario.id>` — **id do vínculo, não do usuário**. Só o próprio vínculo (sair do grupo); 403 caso contrário. Sem evento WebSocket. Depois de sair, o usuário perde acesso e deixa de ver atividades da conversa.

### 11.5 Renomear — `PATCH /api/conversa`

~~`{ "id", "descricao" }` → linha da conversa. Qualquer membro. Sem evento WebSocket.~~

🆕 `b8ea0c2` — `conversas.ts` (`alterarConversa`). Corpo `{ "id": int, "descricao"?: string, "avatar_anexo_id"?: int|null, "emoji"?: string|null }` → linha da conversa (`id, descricao, tipo, inserida, criado_por, avatar_anexo_id, emoji`). Qualquer membro (403).
- Só grava os campos enviados; `descricao` passou a ser opcional (sem nenhum campo, devolve a linha atual).
- `avatar_anexo_id` (imagem do grupo): **id numérico** de um anexo já enviado (§8.2, tipo 2); `null` tira a imagem.
- `emoji`: `trim()`; vazio ou `null` tira o emoji.
- Erros 400:
  - `Só o grupo tem imagem ou emoji.` — `avatar_anexo_id` ou `emoji` não nulos numa conversa que não é grupo (`emoji: ""` também conta como "não nulo" aqui);
  - `Use um único emoji.` — mais de um grafema (emoji composto, como 👨‍👩‍👧, conta como um). O servidor não confere se é mesmo um emoji: uma letra passa.
- `avatar_anexo_id` **não é validado**: id inexistente → **500** com a mensagem de FK do Postgres; o tipo do anexo também não é conferido (S19 do doc 08).
- Efeito: **WS 40** `{conversa_id, usuario_id: quem alterou}` a todos os outros membros, sempre (mesmo sem mudança). Os outros recarregam `/conversas` para ver nome, imagem e emoji novos.

### 11.6 Excluir — `DELETE /api/conversa?id=`

`src/conversas.ts:51-54`. Valida membro e faz `delete from conversa`. Como `conversa_usuario` (e mensagens) referenciam `conversa` sem `on delete cascade`, **a exclusão de uma conversa com membros falha com 500** (violação de FK). Na prática a rota não funciona — não usar; para "apagar" use arquivar ou sair.

### 11.7 Fixar e arquivar

- `PATCH /api/conversa/fixadas` `{ "conversas": [idA, idB, ...] }` → `{ "conversas": [...] }`. Define a ordem completa das fixadas do usuário: posição = índice 1-based no array; conversas fixadas que não vierem no array deixam de ser fixadas (`src/conversas.ts:180-189`). Para desafixar tudo: `[]`.
- `PATCH /api/conversa/arquivada` `{ "conversa": int, "arquivada": bool }` → `{ "id", "arquivada" }`. Arquivar também desfixa. Arquivada não recebe push (mas recebe WS 2). **Não desarquiva sozinha** com mensagem nova (`src/conversas.ts:191-201`).

### 11.8 Contadores

- Por conversa: `mensagens_sem_visualizar` em `/conversas` (🆕 `12cf0cf`: conta também respostas de postagem não vistas).
- Total de não lidas: somar no cliente (decidir se conta arquivadas).

### 11.9 🆕 Quem está com a conversa aberta — `GET /api/conversa/presentes?conversa=` (`b8ea0c2`)

`conversas.ts` (`presentesConversa`). → `number[]` com os **outros** membros que estão com essa conversa aberta numa conexão **ativa** e que mostram isso (§15.2). Membro (403). É o estado inicial; depois chegam os WS 63.

---

## 12. Atividades

Código: `src/atividades.ts`, `migracoes/033.sql` (🆕 e `044.sql`). Testes: `tests/atividades.test.ts`.

> 🆕 `ef13c22` — **a atividade passou a ser uma pendência**, não mais "nova até abrir a tela". Resumo do que mudou (detalhes nos itens com 🆕 abaixo):
> - `GET /atividades` não traz mais `nova`; traz **`pendente`**.
> - `GET /atividades/novas` conta as **pendentes**.
> - `POST /atividades/vistas` **não zera mais** o contador.
> - Tipo novo **5** (pediram a confirmação de leitura).
> - Rotas novas para concluir: `POST /atividade/concluir` e `POST /atividades/reacoes-vistas`.

Tipos (`src/atividades.ts:8-13`): 1 Reação, 2 Resposta, 3 Menção, 4 Chamada perdida, 🆕 `ef13c22` **5 Confirmação** (pediram a ele a confirmação de leitura de uma mensagem; §10.14).

Quando são criadas (nunca para o próprio autor):
- Reação: para o autor da mensagem reagida; some quando a reação é retirada.
- Resposta: para o autor da mensagem respondida (mesma conversa).
- Menção: para cada membro mencionado (exceto quem já recebeu a de resposta).
- Chamada perdida: §9.13.
- 🆕 `12cf0cf` Resposta de **postagem**: atividade **tipo 2** para quem postou e para quem já respondeu (menos o autor da resposta), com `mensagem_id` = **id da postagem** — então `texto` e `conteudo_tipo` são os da **postagem**, não os da resposta. Uma atividade por resposta. Menção ou citação **dentro** da resposta geram as atividades normais (3 ou 2) apontando para a **resposta**, que está fora do fluxo: para levar até ela, abrir a postagem e as respostas (§10.15), não `GET /mensagens?mensagemreferencia=`.
- 🆕 `ef13c22` Confirmação (5): para cada um que precisa confirmar a leitura, no envio com `pede_confirmacao` ou no `POST /mensagem/pedir-confirmacao`; `autor_id` = quem enviou, `mensagem_id` = a mensagem.
- Mensagem excluída: todas as atividades dela são apagadas.
- Agendada: a atividade nasce com `criado_em = visivel_em` e só aparece/conta depois disso. 🆕 Exceção: a tipo 5 criada por `pedir-confirmacao` numa agendada nasce com a data de agora (S25 do doc 08).

`GET /api/atividades?antes=<id>&limite=30` (limite 1..100; `antes` = `id` da última atividade da página anterior) — `src/atividades.ts:103-153`. Mais recentes primeiro. Só de conversas em que o usuário ainda é membro (ou sem conversa):
```json
[ {
  "id": 33, "tipo": 1, "criado_em": "...", "pendente": true,
  "autor_id": 8, "autor_nome": "Bruno Lima",
  "conversa_id": 42, "conversa_tipo": 1, "conversa_descricao": null,
  "mensagem_id": 101, "conteudo_tipo": 1, "texto": "bom dia",
  "chamada_id": null, "chamada_tipo": null, "emoji": "😂",
  "autor_avatar_url": null
} ]
```
- 🆕 `ef13c22`: no exemplo, `"pendente": true` substituiu ~~`"nova": true`~~ (ordem das chaves igual: `id, tipo, criado_em, pendente, autor_id, autor_nome, conversa_id, conversa_tipo, conversa_descricao, mensagem_id, conteudo_tipo, texto, chamada_id, chamada_tipo, emoji, autor_avatar_url`).
- `mensagem_id`: na reação, a mensagem **do usuário** que recebeu a reação; na resposta/menção, a mensagem **nova do autor** (🆕 exceto na resposta de postagem: a postagem).
- `texto`: primeiro conteúdo tipo 1 da mensagem, numa linha, menções viram `@Nome`, cortado em 120 caracteres com `…`; `null` se não há texto. `conteudo_tipo` = tipo do primeiro conteúdo.
- `conversa_descricao`: descrição trimada ou `null` (em conversa direta sem descrição, montar com `autor_nome`).
- ~~`nova`: criada depois de `usuario.atividades_vistas_em` — 🆕 `e25c4db` **e não "lida no chat"** (`atividades.ts`, `atividadeLida`).~~ 🆕 `ef13c22`: a chave **`nova` saiu** da resposta. A regra de "lida no chat" do `e25c4db`, abaixo, continua valendo, agora dentro de `pendente`. Só vale para **resposta (2) e menção (3)** com `mensagem_id`:
  - em geral, a atividade fica lida quando o usuário já **visualizou** a mensagem `mensagem_id` (`mensagem_status.visualizada` não nula — ou seja, `POST /mensagem/visualizar`, ou `POST /postagem/visualizar` para menção dentro de uma resposta de postagem; `GET /mensagens` só marca recebida);
  - resposta de **postagem** (`mensagem_id` = postagem e o autor da atividade tem resposta nela): lida quando **não há** resposta desse autor na postagem, não oculta, ainda não visualizada pelo usuário (`POST /postagem/visualizar` resolve todas de uma vez);
  - ~~reação (1) e chamada perdida (4) continuam só pela data.~~
  - ~~A atividade lida **continua na lista**, só com `nova: false`. `atividades_vistas_em` não muda.~~
- 🆕 `ef13c22` **`pendente`** (bool, no lugar de `nova`, mesma posição: depois de `criado_em`) — `atividades.ts` (`atividadePendente`). Pendente = **não concluída** (`atividade.concluida_em` nula, migração 044) **e** não resolvida pelo próprio evento:
  - **reação (1):** só sai concluindo — `POST /atividades/reacoes-vistas` com a mensagem reagida (quando o usuário vê a própria mensagem com a reação) ou `POST /atividade/concluir`;
  - **resposta (2) e menção (3):** sai quando lida no chat (regra acima) ou concluindo à mão;
  - **chamada perdida (4):** só sai concluindo à mão (o comentário do servidor prevê concluir "ao abrir a chamada perdida");
  - **confirmação (5):** sai quando o usuário **confirma** a leitura daquela mensagem (`mensagem_confirmacao`), ou concluindo à mão (que não confirma);
  - a data (`atividades_vistas_em`) **não conta mais**. Na migração 044, as que já tinham sido vistas (criadas até `atividades_vistas_em`) entraram concluídas.
  - Concluída ou lida, a atividade **continua na lista**, com `pendente: false`.
- `id` é bigint convertido para número.

`GET /api/atividades/novas` → `{ "quantidade": int }` — ~~🆕 `e25c4db`: mesma regra (não conta resposta/menção lida no chat). Então o contador **pode diminuir sozinho**, sem `POST /atividades/vistas`.~~ 🆕 `ef13c22`: quantas atividades estão **pendentes**, já visíveis (`criado_em` ≤ agora) e de conversas em que o usuário **ainda é membro** (antes o contador contava também as de conversas que ele deixou). O contador diminui sozinho quando o evento resolve a pendência e só zera quando todas forem resolvidas ou concluídas.
`POST /api/atividades/vistas` (sem corpo) → `{ "vistas_em": date }`; ~~zera o contador~~ 🆕 `ef13c22`: só grava `usuario.atividades_vistas_em`. **Não mexe** no contador nem em `pendente`: abrir a tela de atividades não conclui nada (o teste do servidor diz "abrir a tela não conclui").

🆕 `ef13c22` **`POST /api/atividade/concluir`** `{ "id": <atividade> }` → `{ "sucesso": true }` (`atividades.ts`, `concluirAtividade`). Conclui à mão uma atividade **do próprio usuário**. Responde 200 mesmo se o id não existe, é de outra pessoa ou já estava concluída (nesses casos não muda nada). Se concluiu agora, WS 61 ao próprio usuário. Não confirma leitura nem marca mensagem como vista.

🆕 `ef13c22` **`POST /api/atividades/reacoes-vistas`** `{ "mensagens": [int, …] }` (até 200) → `{ "concluidas": int }` (`atividades.ts`, `concluirReacoesVistas`). Conclui as atividades de **reação** (tipo 1) do usuário cujas `mensagem_id` estão na lista — ou seja, as reações que **as mensagens dele** receberam até agora. Uso: quando o chat mostra na tela mensagens próprias com reações. Lista vazia → `{concluidas: 0}`. Se concluiu alguma, WS 61 ao próprio usuário. Mensagem de outra pessoa não conclui nada.

WS 61 `{tipo:61}` avisa mudança (nova ou removida). 🆕 `e25c4db`: também quando o usuário lê no chat uma mensagem com atividade de resposta/menção dele ligada a ela (`avisarAtividadesLidas`): `POST /mensagem/visualizar` que marcou agora, ou `POST /postagem/visualizar` que marcou alguma resposta (vale a mensagem ou a postagem dela). Vai a todas as conexões do próprio usuário (inclusive a que leu) e sai mesmo que a atividade já não fosse nova; o cliente só relê `/atividades/novas` (e a lista, se aberta). 🆕 `ef13c22`: também ao concluir (as duas rotas acima), ao confirmar a leitura com atividade tipo 5 ligada e a quem ganha uma tipo 5 (§6.6).

---

## 13. Permissões e parâmetros do sistema

### 13.1 Permissões

Tabelas `permissao` (`codigo`, `descricao`) e `permissao_usuario` — `migracoes/034.sql`. Códigos (`src/permissoes.ts:6-9`):

| Código | Descrição (banco) | Libera |
|---|---|---|
| `parametros` | Ver e alterar as configurações do sistema | `GET/PATCH /api/parametros` |
| `permissoes` | Conceder e retirar permissões dos usuários | `GET /api/permissoes`, `PUT/DELETE /api/permissao/usuario` |

🆕 `ef13c22` — **mais 4 de administração** (coluna `uso = false`):

| Código | Descrição (banco) | Libera | Migração |
|---|---|---|---|
| `bots` | Criar bots de IA e editar os próprios | `GET /bots` (só os seus), `PUT /bot`, editar/excluir os que criou; listas de ferramentas e bases disponíveis | 047 |
| `usuarios` | Cadastrar usuários e bots | `PUT /usuario/novo` (§2.6) | 048 |
| `ferramentas` | Cadastrar as ferramentas externas dos bots | rotas `/ferramenta*` (§13.8) | 051 |
| `conhecimento` | Cuidar das bases de conhecimento dos bots | rotas `/conhecimento/*` (§13.9) | 052 |

Quem tem `permissoes` também cuida de **todos** os bots (listar, editar, excluir, liberar qualquer permissão de uso).

🆕 `ef13c22` — **permissões de uso** (migrações 046, 048 e 049; coluna nova `permissao.uso = true`): o que cada pessoa pode fazer no chat. O servidor confere em cada rota (403 com `Você não tem permissão para: <descrição>.`, §3):

| Código | Descrição (banco) | Onde o servidor confere |
|---|---|---|
| `emoji` | Enviar emojis no texto das mensagens | `PUT /mensagem`: texto com `\p{Extended_Pictographic}` (§10.3) |
| `figurinha` | Enviar figurinhas | `PUT /mensagem`: conteúdo 7 |
| `imagem` | Enviar imagens | `PUT /mensagem`: conteúdo 2 |
| `video` | Enviar vídeos | `PUT /mensagem`: conteúdo 3 com extensão de vídeo |
| `audio` | Enviar áudios e gravações de voz | `PUT /mensagem`: conteúdo 4 ou 5 |
| `arquivo` | Enviar arquivos (documentos e outros) | `PUT /mensagem`: conteúdo 3 que não é vídeo |
| `responder` | Responder mensagens | `PUT /mensagem` com referência tipo 1 |
| `encaminhar` | Encaminhar mensagens | `PUT /mensagem` com referência tipo 2 (dispensa as outras) |
| `reagir` | Reagir às mensagens com emoji | `PUT /mensagem/reacao` (pôr e tirar) |
| `postagem` | Fazer postagens em grupos | `PUT /mensagem` com `postagem` |
| `votacao` | Criar votações em grupos | `PUT /enquete` |
| `confirmacao` | Pedir confirmação de leitura | `PUT /mensagem` com `pede_confirmacao`; `POST /mensagem/pedir-confirmacao` |
| `agendar` | Agendar mensagens | `PUT /mensagem` com `visivel_em` |
| `grupo` | Criar grupos | `PUT /conversa` com `tipo: 2` |
| `chamada_audio` | Fazer chamadas de áudio | `PUT /chamada/iniciar` com `tipo` 1 |
| `chamada_video` | Fazer chamadas de vídeo e ligar o vídeo na chamada | `PUT /chamada/iniciar` com `tipo` 2; `POST /chamada/video` |
| `compartilhar_tela` | Compartilhar a tela nas chamadas | **não conferida** no servidor (só o cliente) |
| `telefonia` | Usar a telefonia (SIP) | `GET /sip` (devolve `{}`), `PUT`/`PATCH /sip` (§14) |
| `ocultar` | Ocultar as próprias mensagens | `DELETE /mensagem` |
| `nome` | Alterar o próprio nome (era `perfil`, renomeada na 049) | `PATCH /usuario` com `nome` |
| `email` | Alterar o próprio e-mail e telefone | `PATCH /usuario` com `email` ou `telefone` |
| `avatar` | Alterar o próprio avatar | `PATCH /usuario` com `avatar_anexo_id` |
| `senha` | Trocar a própria senha | `POST /alterar-senha` |
| `privacidade` | Alterar as opções de privacidade | `PATCH /usuario` com as preferências de presença |
| `cores` | Mudar as cores do app | **não conferida** (fica no navegador; só o cliente) |
| `config_chamadas` | Mudar as configurações de chamada (áudio, vídeo e tela) | **não conferida** (só o cliente) |

- **Quem tem o quê:** as contas que já existiam ganharam todas as de uso nas migrações 046, 048 e 049. Conta nova (cadastro ou `PUT /usuario/novo`) ganha todas (`concederPermissoesUso`). Bot novo começa só com `emoji`, `responder` e `reagir` — e só as que quem o criou tem (§13.7).
- **Modo aberto** continua igual: enquanto ninguém tem `permissoes`, todos podem tudo (inclusive as de uso e as de administração novas).
- **Ninguém retira as próprias permissões:** `DELETE /permissao/usuario` com `usuario_id` = o próprio → 400 `Você não pode retirar as próprias permissões.` (`permissoes.ts`, `retirarPermissao`). Outra pessoa com `permissoes` precisa fazer.
- **Para o cliente:** leia `GET /usuario/permissoes` depois do login (e ao voltar ao app) para esconder o que a pessoa não pode fazer. Não há evento quando um administrador muda as permissões: trate o 403 com a mensagem do servidor.

**Modo aberto** (`src/permissoes.ts:12-23`): enquanto **ninguém** tem `permissoes`, todos os usuários têm todas as permissões. A primeira concessão de `permissoes` fecha o modo. Não é possível retirar `permissoes` do último que a tem (400 `Ao menos uma pessoa precisa poder gerenciar as permissões.`).

Rotas:
- `GET /api/usuario/permissoes` → `["parametros","permissoes"]` (os códigos do próprio usuário; todos no modo aberto). Use para mostrar/ocultar telas. 🆕 `ef13c22`: inclui os códigos de uso e os de administração novos, em ordem do `permissao.id` (ex.: `["emoji","figurinha",…,"avatar"]` para uma conta comum).
- `GET /api/permissoes` → `{ "permissoes": [{codigo, descricao}], "usuarios": [{ id, nome, login, permissoes: string[] }], "modo_aberto": bool }` (usuários ordenados por nome). 🆕 `ef13c22`: cada item de `permissoes` ganha **`uso: bool`** (`{codigo, descricao, uso}`), para a tela separar uso de administração; `usuarios` inclui os bots (ligados ou desligados) e deixa de fora os bots excluídos.
- `PUT /api/permissao/usuario` corpo `{ "usuario_id", "codigo" }` → `{ usuario_id, codigo }` (idempotente). 404 `Permissão não encontrada!` / `Usuário não encontrado!`.
- `DELETE /api/permissao/usuario?usuario_id=&codigo=` → `{ usuario_id, codigo }`. 🆕 `ef13c22`: 400 para as próprias (acima).
- Sem permissão: 403 `Acesso negado!` (🆕 para as de administração; as de uso têm a mensagem com a descrição, §3).

### 13.2 Parâmetros (tabela `parametros`: `nome`, `valor` texto)

| Nome | Significado | Exposto em `GET /parametros` | Alterável por `PATCH` | Origem |
|---|---|---|---|---|
| `versao` | versão das migrações aplicadas (~~🆕 hoje `43`~~ 🆕 `ef13c22`: hoje `52`) | não | não | `src/migracoes.ts` |
| `jwt_token` | segredo do JWT (gerado se vazio/padrão) | **nunca** | não | `015.sql`, `configuracao.ts:151-158` |
| `fcm_project_id` | Firebase project id | sim | sim | `015.sql` |
| `fcm_client_email` | e-mail da conta de serviço (precisa ter `@`) | sim | sim | `015.sql` |
| `fcm_private_key` | chave privada (precisa conter `PRIVATE KEY`) | só `fcm_private_key_configurada: bool` | sim | `015.sql` |
| `s3_bucket` | bucket do MinIO (padrão `chat`) | sim | não | `011.sql` |
| `turn_forcar_relay` | `'1'` = `iceTransportPolicy: relay` (padrão `'1'`) | como bool | sim (bool) | `023.sql` |
| `transcritor_url` | URL do transcritor-api (vazio = desligado; http/https) | sim | sim | `027.sql` |
| `transcritor_idioma` | idioma (`pt`, `en`, `pt-BR`...; regex `^[a-z]{2,3}(-[A-Za-z]{2})?$`) | sim | sim | `027.sql` |
| `gravacao_dias` | dias de retenção das gravações (padrão 90; 0 = sempre) | como número | sim (0..36500) | `030.sql` |
| `ia_url` 🆕 | endereço do servidor de IA no padrão OpenAI, com ou sem `/v1` (vazio = desligado; http/https) | sim | sim | `041.sql` (`b8ea0c2`) |
| `ia_modelo` 🆕 | nome do modelo (vazio = desligado) | sim | sim | `041.sql` |
| `ia_token` 🆕 | token `Bearer` do servidor de IA (opcional) | só `ia_token_configurado: bool` | sim (só gravação) | `041.sql` |
| `cadastro_aberto` 🆕 `ef13c22` | `'1'` (padrão) = cadastro pela tela de login aberto; `'0'` = só o administrador cadastra (§2.6) | como bool | sim (bool) | `048.sql` |
| `ia_modelo_embeddings` 🆕 `ef13c22` | modelo de embeddings no mesmo servidor de IA (`POST <ia_url>/v1/embeddings`); vazio desliga a base de conhecimento (§13.9) | sim | sim | `052.sql` |

Removidos por migrações: `s3_endpoint`, `s3_accesskey`, `s3_secretkey`, `turn_url`, `turn_secret` (`024–026.sql`).

`GET /api/parametros` → `{ "fcm_project_id", "fcm_client_email", "fcm_private_key_configurada": bool, "turn_forcar_relay": bool, "transcritor_url", "transcritor_idioma", "gravacao_dias": number, "s3_bucket" }` (`src/parametros.ts:11-25`) — 🆕 `b8ea0c2`: + `"ia_url", "ia_modelo", "ia_token_configurado": bool` no fim. O `ia_token` **nunca** volta. 🆕 `ef13c22`: + `"cadastro_aberto": bool, "ia_modelo_embeddings"` no fim.
`PATCH /api/parametros` com qualquer subconjunto de `{ fcm_project_id?, fcm_client_email?, fcm_private_key?, turn_forcar_relay?: bool, transcritor_url?, transcritor_idioma?, gravacao_dias?: int }` (🆕 + `ia_url?`, `ia_token?`, `ia_modelo?`; todos com `trim()`, padrão `''`; 🆕 `ef13c22` + `cadastro_aberto?: bool`, `ia_modelo_embeddings?`) → mesmo formato do GET. Aplica na hora, sem reiniciar (`src/parametros.ts:57-77`). Erros 400 com explicação (ver `src/parametros.ts:29-54`; 🆕 `Endereço do servidor de IA inválido (use http:// ou https://).`).

### 13.3 🆕 Recursos ligados — `GET /api/recursos` (`b8ea0c2`)

`parametros.ts` (`recursosSistema`). Para **qualquer usuário logado** (sem permissão): `{ "transcricao": bool, "ia": bool }` — 🆕 `ef13c22`: `{ "transcricao": bool, "ia": bool, "conhecimento": bool }`.
- `transcricao` = `transcritor_url` preenchido.
- `ia` = `ia_url` **e** `ia_modelo` preenchidos (o token é opcional).
- 🆕 `ef13c22` `conhecimento` = `ia` **e** `ia_modelo_embeddings` preenchido (base de conhecimento dos bots ligada, §13.9). Só interessa a telas de administração.
- Não diz se o servidor de IA ou o transcritor respondem; só se estão configurados. Muda na hora com `PATCH /parametros` (não há evento: reler ao abrir telas que dependem disso). Cobre parte da S10 do doc 08.

### 13.4 🆕 Servidor de IA e teste — `POST /api/parametros/ia/testar` (`b8ea0c2`, `ea7e974`)

- O servidor fala com um servidor de IA **compatível com a OpenAI** (`POST <ia_url>/v1/chat/completions`; se `ia_url` já termina em `/v1`, não repete — `ia.ts`, `enderecoCompletions`). Exemplos citados no código: vLLM, Ollama, LM Studio, OpenAI. `temperature: 0.2`, `stream: false`, `Authorization: Bearer <ia_token>` se houver.
- `ea7e974`: o resumo pede `response_format: {type:"json_schema", …}`; a sugestão pede `reasoning_effort: "none"`. Se o servidor de IA responder **400**, o pedido é refeito **sem** esses extras. Interno: o cliente não fala com a IA.
- Tempos-limite: **5 min** no resumo, **60 s** no teste, **10 s** na sugestão.
- **Testar** — `POST /api/parametros/ia/testar {url, modelo, token?}`: permissão `parametros` (403); `url` sem http/https → 400 `Endereço do servidor de IA inválido (use http:// ou https://).`. `token` vazio usa o salvo. Manda "Responda apenas: ok" e responde **sempre 200**: `{ "ok": bool, "resposta": "<até 200 caracteres>", "erro": "<motivo>", "milissegundos": int }`. Falhas (sem conexão, tempo esgotado, status ≠ 2xx, modelo vazio) vêm em `ok:false` + `erro`.
- Sem `ia_url`/`ia_modelo` salvos, resumo e sugestão respondem 400 `IA não configurada: defina o endereço e o modelo nas configurações do sistema.` (o teste usa o que vem no corpo). Confira antes com `GET /recursos`.

### 13.5 🆕 Resumo da conversa por IA — `POST` e `GET /api/conversa/resumo` (`b8ea0c2`, `ea7e974`)

`resumos.ts`. O resumo é **assíncrono**: o POST devolve um id e o cliente consulta até ficar pronto (polling).
- **Pedir** — `POST /api/conversa/resumo {conversa_id, periodo}`, `periodo` ∈ `'24h' | '7d' | '30d' | 'recentes'` (outro valor → 400 de validação). Membro (403); IA desligada (400). Se o mesmo usuário já tem um pedido **em andamento** para a mesma conversa e período, devolve esse (não começa outro).
- **Consultar** — `GET /api/conversa/resumo?id=<uuid>`. Só quem pediu; outro usuário, id inexistente ou vencido → 404 `Resumo não encontrado (pode ter expirado). Peça de novo.`
- **Resposta (as duas rotas):**

  ```json
  { "id": "<uuid>", "conversa_id": 42, "periodo": "7d", "status": "processando"|"concluido"|"erro",
    "mensagens": 37,
    "assuntos": [ { "titulo": "Orçamento", "resumo": "Bruno fecha até quinta.", "pendencias": ["Fechar orçamento"], "mensagens": [101, 105] } ],
    "erro": "" }
  ```

  - `mensagens` (número) = quantas mensagens foram mandadas à IA; `assuntos[].mensagens` = ids de mensagens da conversa (só ids que foram enviados), em ordem crescente, para "levar até elas".
  - `erro` preenchido quando `status: "erro"` (sem conexão, tempo esgotado, resposta fora do formato, ou `A IA não encontrou assuntos para resumir.`).
- **O que vai para a IA:** as mensagens não ocultas do período (`'recentes'` = sem limite de data), visíveis para o usuário (agendadas dele entram), até **400 mensagens**; cada uma vira uma linha `[id] dd/mm hh:mm Nome: texto`, cortada em **1.500 caracteres**; o total fica em **48.000 caracteres** (saem as mais antigas). Texto com menção vira `@Nome`; imagem `[imagem]`; arquivo `[arquivo: nome]`; áudio com transcrição pronta `[áudio: "…"]`; votação `[votação: pergunta]`; figurinha e chamada são ignoradas. **Respostas de postagem entram** (não há filtro), então `assuntos[].mensagens` pode ter ids que não estão em `GET /mensagens`.
- **Período vazio** (nenhuma mensagem com texto aproveitável): volta na hora com `status: "concluido"`, `mensagens: 0`, `assuntos: []`, sem chamar a IA.
- **Só na memória** do processo da API: some ao reiniciar a API e vale por **30 min** (a limpeza dos vencidos roda a cada novo pedido). Não há evento WebSocket ao concluir (S21 do doc 08). O web guarda o resultado no IndexedDB (`conversa-web` `05507a0`).

### 13.6 🆕 Sugestão de texto — `POST /api/ia/sugestao` (`ea02f57`)

`sugestoes.ts` (`sugerirTexto`). Completa o que a pessoa está digitando, como o autocompletar de um editor de código.
- Corpo `{ "conversa_id": int, "texto": string }` (`texto` ≤ **4000** caracteres, senão 400 de validação). Membro (403); IA desligada (400). Texto só com espaços → `{ "sugestao": "" }` sem chamar a IA.
- Resposta: `{ "sugestao": "<continuação>" }` — o trecho a **acrescentar** depois do que foi digitado, numa linha só, sem aspas, até ~**120 caracteres** (cortado numa palavra). Pode **começar com espaço** (ex.: digitado `Ok, então vamos` → `" aguardar até segunda."`); se o digitado já termina com espaço, a sugestão vem sem espaço inicial; palavra pela metade é continuada (`vou avisar o pes` → `"soal que vai atrasar."`).
- **Falha não é erro:** IA fora do ar, tempo esgotado (10 s) ou resposta que não encaixa → **200** com `sugestao: ""` (o motivo só vai para o log do servidor).
- **Cancelamento:** se o cliente aborta a requisição HTTP, o servidor cancela o pedido à IA. Na prática, cancelar a chamada anterior a cada tecla.
- Contexto enviado à IA: as últimas 20 partes de texto (tipo 1, não ocultas) da conversa, até 300 caracteres cada, com o primeiro nome do autor; respostas de postagem também entram.

### 13.7 🆕 Bots de IA (`ef13c22`, migrações 047 e 050)

`bots.ts`. Um bot é um **usuário comum** (linha em `usuario` + linha em `bot` com as instruções). Ele conversa pelas mesmas rotinas e com as mesmas permissões de uso de uma pessoa. Não entra pelo login.

**Para o cliente do chat (o que aparece):**
- **Contatos:** aparece em `GET /usuario/contatos` com `bot: true`, login `bot-<nome>-<6 hex>` e e-mail `<login>@bot.conversa`. Nenhuma outra rota marca bot (`/conversas`, `/conversa/usuarios`, `MensagemResposta`, atividades): cruze pelo id.
- **Quando responde** (`aoNovaMensagem`, `botsChamados`), depois de cada mensagem que sai (na hora, ou agendada quando sai), se a IA está configurada e o bot está ligado e é membro:
  - na **conversa direta** com ele: a toda mensagem;
  - em **grupo**: quando é **mencionado** (`@[Nome](id)`) ou quando **respondem** (citação tipo 1) a uma mensagem dele;
  - nunca a mensagem de outro bot.
- **Enquanto pensa:** manda "digitando" (**WS 4** com `usuario_id` = bot) na hora e a cada 3 s. Cada pedido à IA tem limite de 2 min e até 5 rodadas de ferramentas: a resposta pode levar minutos.
- **Marca como vista** a mensagem que o chamou (`visualizada`, com WS 3 aos outros membros). Só essa: ele **nunca marca `recebida`** e não lê as outras (em grupo, as mensagens que não o chamam ficam sem recebida/vista por ele; S27 do doc 08).
- **A resposta** é uma mensagem comum do bot (WS 2, push, WS 3, atividades): na direta, sem citar; em grupo, citando a mensagem que o chamou (se o bot tem `responder`). Se quem chamou estava respondendo a uma **postagem**, o bot responde na postagem (`postagem_id`). Mensagens que chegam enquanto ele pensa viram **uma** resposta só, à mais nova.
- **Ferramentas do chat** (function calling), cada uma só com a permissão de uso do bot: `reagir` (só acrescenta), `responder_mensagem`, `enviar_figurinha` (pacote `noto`), `encaminhar_mensagem`, `criar_votacao` (só em grupo), `buscar_mensagens` (sempre), `consultar_conhecimento` (com base ligada, §13.9) e as **externas** ligadas a ele (§13.8). Os efeitos chegam ao cliente como de qualquer pessoa (WS 7, mensagens, votação).
- **Sem a permissão `emoji`**, o texto vai **sem os emojis** (em vez de dar 403).
- **Falha** (IA fora do ar, tempo esgotado…): o bot manda na conversa `Não consegui responder a esta mensagem: <motivo>` (até 300 caracteres), citando a mensagem que ficou sem resposta. Sem IA configurada, ele simplesmente não responde.
- **Presença:** nunca conecta o WebSocket — sempre `offline`. **Chamadas:** pode ser convidado, mas nunca atende (§9.2).

**Cadastro e administração** (telas de administração; o celular provavelmente não usa):
- Objeto `Bot` (`linhasBots`): `{ "id", "usuario_id", "nome", "login", "instrucoes", "ativo": bool, "criado_por", "criado_por_nome", "permissoes": string[] (só as de uso), "ferramentas": int[], "bases": int[] }`, em ordem de nome. `id` é o id do **bot**; `usuario_id`, o do usuário.
- `GET /bots`: quem tem `permissoes` vê todos; quem tem `bots`, só os que criou; os outros, 403.
- `PUT /bot {nome, instrucoes}` (permissão `bots`): exige a IA configurada (400 `IA não configurada: …`); `nome` de 1 a 60 caracteres e `instrucoes` de 1 a 8000 (400). Cria o usuário (senha aleatória que ninguém sabe) e libera `emoji`, `responder` e `reagir` **que quem criou tiver**. Também por `PUT /usuario/novo` com `bot: true` (§2.6).
- `PATCH /bot {id, nome?, instrucoes?, ativo?}`, `PUT /bot/permissoes {id, permissoes}`, `PUT /bot/ferramentas {id, ferramentas}`, `PUT /bot/conhecimento {id, bases}`: só quem criou (com `bots`) ou quem tem `permissoes` (404 `Bot não encontrado!`; 403 `Só quem criou o bot o edita.`). As listas **substituem** as anteriores. Em `/bot/permissoes`, só códigos de uso (400 `Permissão de uso inválida: x`), e quem não tem `permissoes` só libera o que ele mesmo tem (403 `Você só libera para o bot o que você mesmo pode fazer.`).
- `DELETE /bot?id=` → `{ "id", "apagado": bool }`. Apaga os status de entrega e as atividades do bot, tira-o dos **grupos** (sem WS 40) e das permissões. Sem histórico: apaga o bot e o usuário de vez (`apagado: true`). Com histórico (mensagens, reações…): o usuário fica, para o que ele escreveu continuar com o nome; o bot é marcado `excluido_em` e desligado (`apagado: false`), **continua nas conversas diretas** e some dos contatos, de `/permissoes` e de `/bots`.

### 13.8 🆕 Ferramentas externas dos bots (`ef13c22`, migração 051)

`ferramentas.ts`. APIs HTTP com JSON que o administrador (permissão `ferramentas`) cadastra e liga bot a bot. O modelo chama pelo nome; o **servidor** faz o pedido. Só administração; o cliente do chat não usa.
- Objeto `Ferramenta`: `{ "id", "nome", "descricao", "metodo": "GET"|"POST", "url", "parametros": [{nome, tipo: "string"|"number"|"boolean", descricao, obrigatorio}], "ativo", "cabecalhos": string[] }` — dos cabeçalhos voltam **só os nomes**; os valores (tokens) nunca saem do servidor.
- Corpo de `PUT`/`PATCH /ferramenta`: `{ nome, descricao, metodo, url, cabecalhos: [{nome, valor: string|null}] (≤ 30), parametros: [...] (≤ 30), ativo? }` (+ `id` no PATCH). Cabeçalho com valor vazio/nulo mantém o valor salvo; cabeçalho que não vem sai.
- Regras (400): `nome` em `^[a-z][a-z0-9_]{1,59}$`, único e diferente das ferramentas do chat (`reagir`, `responder_mensagem`, …); `descricao` obrigatória; `url` http/https; parâmetros com nome válido e sem repetir; todo `{marcador}` da URL precisa ser um parâmetro.
- Chamada: parâmetros marcados na URL vão nela; o resto vai na consulta (GET) ou no corpo JSON (POST). Tempo-limite 20 s; resposta cortada em 8000 caracteres.
- `POST /ferramenta/testar {id, argumentos}` → `{ "ok": bool, "status": int, "resposta": string, "erro": string, "milissegundos": int }` (sempre 200, como o teste da IA).
- `GET /ferramentas/disponiveis` → `[{id, nome, descricao}]` das ativas, para quem tem `bots`, `permissoes` ou `ferramentas` (escolher na tela do bot).

### 13.9 🆕 Base de conhecimento dos bots (`ef13c22`, migração 052)

`conhecimento.ts`, `ia.ts` (`gerarEmbeddings`). Documentos de texto que o bot consulta pela ferramenta `consultar_conhecimento` (RAG). Só administração (permissão `conhecimento`); o cliente do chat não usa.
- Liga com `ia_url` + `ia_modelo_embeddings` (`GET /recursos` → `conhecimento`). Embeddings em `POST <ia_url>/v1/embeddings` (com prefixos próprios para modelos `nomic-embed` e `e5`); os trechos e vetores ficam no **Qdrant** (§1.1), numa coleção só; o texto inteiro fica no Postgres, para reindexar.
- **Bases:** `{ "id", "nome", "descricao", "documentos": int, "trechos": int }`; `nome` de 1 a 100, único sem diferenciar maiúsculas (400). Excluir a base apaga os documentos e os trechos.
- **Documentos:** `PUT /conhecimento/documento {base_id, nome, texto}` — o **texto** já extraído (o web extrai de `.txt`, `.md`, `.pdf` e `.docx`), até 2 milhões de caracteres no servidor. Atenção: o nginx corta o corpo JSON em **1 MB** (§1.2), então documentos maiores levam 413 do nginx antes de chegar à API. Resposta: `{ "id", "base_id", "nome", "caracteres", "trechos", "status": 1|2|3, "erro", "criado_em" }` (1 processando, 2 pronto, 3 erro).
- **Indexação** em segundo plano, um documento por vez: trechos de ~1000 caracteres com 150 de sobra do anterior, embeddings em lotes de 32. **Sem evento** ao terminar: a tela relê `GET /conhecimento/documentos?base=`.
- `POST /conhecimento/buscar {bases, pergunta, limite?}` → `[{ "documento": nome, "texto": trecho, "nota": 0..1 }]` (os trechos que o bot receberia). `POST /conhecimento/reindexar` → `{ "documentos": n }` (apaga a coleção e indexa tudo; necessário ao trocar o modelo de embeddings).

---

## 14. SIP

Código: `src/sip.ts`, `migracoes/016.sql`. Um ramal por usuário (índice único `ux_sip_usuario`).

Campos da tabela `sip`: `id`, `usuario_id`, `sip_user` varchar(100), `auth_user` varchar(100) nulo, `sip_password` texto, `display_name` varchar(100) nulo, `domain` varchar(150), `ws_server` varchar(255), `ativo` bool (padrão true), `criado_em`, `criado_por`.

- `GET /api/sip` → linha completa (inclui **`sip_password` em texto puro**) ou `{}` se não há ramal. 🆕 `ef13c22`: também `{}` para quem **não tem a permissão de uso `telefonia`**, mesmo com ramal cadastrado (não dá para distinguir de "sem ramal"; confira `GET /usuario/permissoes`).
- `PUT /api/sip` corpo `{ "sip_user", "auth_user"?: str|null, "sip_password", "display_name"?: str|null, "domain", "ws_server", "ativo"?: bool }` → linha criada; `usuario_id` sempre do token (campo enviado é ignorado). Segundo PUT do mesmo usuário → **500** (unique). 🆕 `ef13c22`: sem `telefonia` → 403 `Você não tem permissão para: usar a telefonia (sip).`
- `PATCH /api/sip` corpo `{ "id", ...campos opcionais }` → linha alterada; só o dono (403). 🆕 `ef13c22`: e `telefonia` (403).
- O servidor só guarda a configuração; o registro SIP (ex.: SIP sobre WebSocket em `ws_server`) é feito pelo cliente direto no PBX.

---

## 15. Presença, digitando, gravando

### 15.1 Online, ausente, offline e visto por último 🆕 (`b8ea0c2`, migração `040.sql`)

> Antes de `b8ea0c2`: estado = "tem ao menos um socket aberto", WS 60 `{usuario_id, online}` só na primeira conexão e no fechamento da última, sem "ausente" nem "visto por último". O texto abaixo substitui isso.

Código: `src/websocket.ts` (`presencaReal`, `presencaVisivel`, `atualizarPresenca`, `notificarContatosStatus`), `usuarios.ts` (`contatosPresenca`, `contatosOnline`).

- **Estado por conexão:** cada conexão autenticada tem `{ativo, conversa}`. Ao fazer login ela nasce **ativa** e sem conversa; o cliente atualiza com o tipo 63 (`{tipo:63, ativo, conversa_id}`, §6.5). `ativo` só é verdadeiro se vier **literalmente `true`**.
- **Estado real do usuário** (juntando todas as conexões dele, de todas as abas e aparelhos, **naquele processo da API**):
  - `ativo` — alguma conexão ativa;
  - `ausente` — conectado, mas nenhuma conexão ativa;
  - `offline` — nenhuma conexão.
- **Estado visível** (o que os outros veem): o real, a não ser que o usuário tenha `aparecer_offline = true` — aí é sempre `offline` (e a conversa aberta não aparece).
- **Preferências** (colunas de `usuario`, migração 040; lidas e trocadas por `GET /usuario/privacidade` e `PATCH /usuario`):

  | Preferência | Padrão | Efeito |
  |---|---|---|
  | `mostrar_visto_em` | `true` | `false` → `visto_em` vai `null` no WS 60 e em `GET /contatos/presenca` (o servidor continua gravando) |
  | `mostrar_na_conversa` | `true` | `false` → ninguém vê que ele está com uma conversa aberta (sem WS 63, fora de `/conversa/presentes`) |
  | `aparecer_offline` | `false` | `true` → aparece `offline` para todos, mesmo conectado; o "visto por último" fica parado (no momento em que ligou a opção, se estava ativo, ou na última saída do ativo) |

  As preferências ficam em memória enquanto o usuário tem socket (lidas no login) e são trocadas na hora pelo `PATCH /usuario`.
- **`visto_em`** (`usuario.visto_em`, UTC): gravado **só quando o estado visível sai de `ativo`** (para `ausente` ou `offline`, inclusive ao ligar `aparecer_offline`). Enquanto a pessoa está ativa, o `visto_em` do banco é o da última saída — mostre "online"/"ativo", não o `visto_em`.
- **WS 60** `{usuario_id, online, estado, visto_em}` (§6.6) a cada mudança do estado visível, para quem tem conversa direta com ele **e para ele mesmo** (para ver como os outros o veem). `visto_em` só vem preenchido na saída do `ativo` e se ele mostra; nos outros casos `null` — o web mantém o último valor conhecido quando chega `null` (`conversa-web/src/stores/chat.ts:1397`). Também vai à conexão que acabou de logar (§6.2).
- **Estado inicial:** `GET /api/contatos/presenca` → `[{usuario_id, estado, visto_em}]` (contatos de conversa direta; não inclui o próprio usuário — o próprio estado chega pelo WS 60 do login). `GET /api/contatos/online` continua existindo e devolve os ids com estado visível ≠ `offline`.
- **Em memória, por processo:** estados, preferências carregadas e "o que já foi avisado" ficam em `Map`s do processo da API; não há tabela de presença nem sincronização entre processos. Reiniciar a API zera tudo (os clientes reconectam e aparecem ativos de novo).
- **Push:** a presença **não** mudou a regra do push — continua bloqueado se houver **qualquer** socket aberto, ausente ou "aparecendo offline" (§7.1, S18 do doc 08).
- 🆕 `ef13c22` **Bots** nunca conectam o WebSocket: aparecem sempre `offline`, sem `visto_em` (§13.7).
- **Para o Android:** quem nunca manda o 63 fica **ativo enquanto o socket estiver aberto**, inclusive em segundo plano. Mandar `{tipo:63, ativo:false, conversa_id:null}` ao ir para segundo plano (ou após inatividade, como os 5 min do web) e `{tipo:63, ativo:true, conversa_id:<aberta>}` ao voltar. Fechar o socket ao ir para segundo plano continua sendo o único jeito de voltar a receber push.

### 15.2 🆕 Quem está com a conversa aberta (`b8ea0c2`)

- Uma conversa conta como "aberta" pelo usuário se alguma conexão **ativa** dele mandou `conversa_id` no tipo 63 (conexões inativas não contam).
- Os **outros membros** da conversa recebem **WS 63** `{conversa_id, usuario_id, aberta}`: `true` ao abrir; `false` ao sair, ao a conexão ficar inativa ou fechar, ou ao ligar `mostrar_na_conversa = false`/`aparecer_offline = true`.
- Estado inicial ao abrir uma conversa: `GET /api/conversa/presentes?conversa=` → ids dos outros membros com ela aberta (§11.9).
- O servidor **não confere** se o `conversa_id` mandado no 63 é de uma conversa da qual o usuário é membro: os membros dessa conversa recebem o WS 63 do mesmo jeito (S22 do doc 08). O cliente deve ignorar WS 63 de quem não é membro.
- O web, no cabeçalho da conversa direta, mostra "nesta conversa", "ativo agora", "ausente" ou "visto por último …", nessa prioridade (`conversa-web/src/components/ChatHeader.vue:278-288`).

### 15.3 Digitando / gravando áudio

- `POST /api/conversa/digitando` e `POST /api/conversa/gravando` com `{ "id": <conversa_id> }` → `{}`; membro (403). Repassam WS 4/5 `{tipo, conversa_id, usuario_id}` aos outros membros.
- Não há "parou de digitar": o receptor usa temporizador (o web expira o indicador por tempo) e limpa ao chegar mensagem nova. Enviar no máximo a cada poucos segundos enquanto digita (sugestão: a cada 3 s).

---

## 16. Modelo de dados (tabelas)

Resumo das tabelas do schema `public` (migrações 000–~~🆕043~~ 🆕 `ef13c22`: 052):

| Tabela | Colunas principais | Observações |
|---|---|---|
| `usuario` | `id`, `nome`(100), `login`(50, único lower), `email`(100, único), `telefone`(50), `senha`(60 bcrypt), `avatar_anexo_id`→anexo, `criado_em`, `criado_por`, `atividades_vistas_em`; 🆕 `040.sql`: `visto_em` timestamp nulo, `mostrar_visto_em` bool (true), `mostrar_na_conversa` bool (true), `aparecer_offline` bool (false) | 🆕 presença §15.1 |
| `dispositivo` | §7.3 | |
| `dispositivo_usuario` | `id`, `dispositivo_id`, `usuario_id`, `online_em` | legado |
| `usuario_contato` | `id`, `usuario_id`, `relacionamento_id` | sem rota de listagem |
| `conversa` | `id`, `descricao`(100), `tipo`, `inserida`, `criado_por`; 🆕 `038.sql`: `avatar_anexo_id`→anexo, `emoji` text | 🆕 imagem/emoji de grupo §11.5 |
| `conversa_usuario` | `id`, `usuario_id`, `conversa_id`, `fixada_ordem`, `arquivada_em` | |
| `mensagem` | `id`, `usuario_id`, `conversa_id`, `inserida`, `alterada`, `visivel_em`(timestamptz), `excluida_em`, `excluida_por`; 🆕 `039.sql`: `pede_confirmacao` bool (false); 🆕 `042.sql`: `postagem` bool (false), `postagem_titulo` text, `postagem_id`→mensagem (índice `ix_mensagem_postagem`) | 🆕 §10.14, §10.15 |
| `mensagem_confirmacao` 🆕 | `id`, `mensagem_id`→mensagem, `usuario_id`→usuario, `confirmada_em` timestamp (default agora); único (`mensagem_id`,`usuario_id`) | `039.sql` (`b8ea0c2`) |
| `mensagem_confirmacao_destinatario` 🆕 | `mensagem_id`→mensagem, `usuario_id`→usuario; PK (`mensagem_id`,`usuario_id`) | `043.sql` (`6d20ff7`): quem precisa confirmar (§10.14). A migração preencheu as mensagens que já pediam confirmação com as linhas de `mensagem_status` delas. Sai junto só quando uma agendada é apagada de vez |
| `mensagem_conteudo` | `id`, `mensagem_id`, `ordem`, `tipo`, `conteudo`(bytea) | |
| `mensagem_referencia` | `id`, `tipo`, `origem_mensagem_id`(nova), `destino_mensagem_id`(referenciada) | |
| `mensagem_status` | PK(`conversa_id`,`usuario_id`,`mensagem_id`), `recebida`, `visualizada`, `reproduzida` | uma linha por destinatário |
| `reacao` | `id`, `mensagem_id`, `usuario_id`, `emoji`(10) | único (msg, usuário, emoji) |
| `anexo` | §8.1 | |
| ~~`anexo_transcricao`~~ 🆕 `anexo_audio` | `identificador`(único), ~~`status`, `texto`, `idioma`, `job_id`, `erro`(500)~~ 🆕 `transcricao_status` (**nulo** enquanto ninguém pede), `transcricao`, `transcricao_idioma`, `transcricao_job_id`, `transcricao_erro`(500), `atualizado_em`; 🆕 `duracao` numeric(10,2), `intensidade` int2[] | 🆕 `045.sql` (`ef13c22`): renomeada; uma linha por áudio, com a transcrição e os dados do envio (§8.2, §8.6) |
| `chamada` | `id`, `tipo`, `status`, `iniciada`, `finalizada`, `conversa_id`, `criado_em`, `criado_por`, `conversa_chat_id` | 🆕 `037.sql`: índice `chamada_conversa_chat_id_idx` (para `chamada` em `/conversas`); 🆕 `a40fd47`: `conversa_chat_id` pode ser igual a `conversa_id` (🆕 `6d20ff7`: em ligação nova, só quando a origem é o chat de outra ligação); 🆕 `6d20ff7`: `tipo` passa de 1 a 2 em `POST /chamada/video` |
| `chamada_usuario` | `id`, `chamada_id`, `usuario_id`, `status`, `adicionado_por`, `adicionado_em`, `entrou_em`, `saiu_em`, `recusou_em` | `recusou_em` nunca é gravado (vem do evento) |
| `chamada_evento` | `id`, `chamada_id`, `usuario_id`, `tipo`, `criado_em`, `criado_por` | |
| `atividade` | `id` bigserial, `usuario_id`, `tipo` int2, `autor_id`, `conversa_id`, `mensagem_id`, `chamada_id`, `emoji`, `criado_em`; 🆕 `044.sql`: `concluida_em` timestamp nulo | 🆕 `ef13c22`: tipo 5 (confirmação); pendente até concluir (§12). A 044 concluiu as já vistas |
| `permissao`, `permissao_usuario` | §13.1; 🆕 `046.sql`: `permissao.uso` bool (false) | 🆕 `ef13c22`: 26 de uso + 6 de administração (§13.1) |
| `bot` 🆕 | `id`, `usuario_id`→usuario (único), `instrucoes` text, `ativo` bool (true), `criado_em`, `criado_por`; 🆕 `050.sql`: `excluido_em` timestamp nulo | `047.sql` (`ef13c22`), §13.7 |
| `ferramenta` 🆕 | `id`, `nome`(60, único), `descricao`, `metodo`(`GET`/`POST`), `url`, `cabecalhos` jsonb, `parametros` jsonb, `ativo`, `criado_em`, `criado_por` | `051.sql` (`ef13c22`), §13.8 |
| `bot_ferramenta` 🆕 | `id`, `bot_id`, `ferramenta_id` (único pelos dois; cascade) | `051.sql` |
| `conhecimento_base` 🆕 | `id`, `nome`(100, único), `descricao`, `criado_em`, `criado_por` | `052.sql` (`ef13c22`), §13.9 |
| `conhecimento_documento` 🆕 | `id`, `base_id` (cascade), `nome`(255), `texto`, `trechos`, `status` int2 (1 processando, 2 pronto, 3 erro), `erro`, `criado_em`, `criado_por` | `052.sql` |
| `bot_conhecimento` 🆕 | `id`, `bot_id`, `base_id` (único pelos dois; cascade) | `052.sql` |
| `sip` | §14 | |
| `parametros` | `id`, `nome`(50, único), `valor`(5000) | 🆕 `041.sql`: `ia_url`, `ia_token`, `ia_modelo` (vazios); 🆕 `048.sql`: `cadastro_aberto` (`'1'`); 🆕 `052.sql`: `ia_modelo_embeddings` (vazio) |
| `enquete` 🆕 | `id`, `conversa_id`, `mensagem_id`, `pergunta`(300), `multipla`, `criado_em`, `criado_por`; 🆕 `036.sql`: `encerra_em`, `encerrada_em`, `encerrada_por` | `035.sql`, `036.sql` |
| `enquete_opcao` 🆕 | `id`, `enquete_id`, `ordem`, `texto`(200) | |
| `enquete_voto` 🆕 | `id`, `enquete_id`, `opcao_id`, `usuario_id`, `criado_em`; único (`opcao_id`,`usuario_id`) | |
| `auditoria.alteracao`, `auditoria.exclusao` | log de alterações/exclusões via gatilhos | interno |

Todas as colunas `timestamp` (sem fuso) guardam UTC; `visivel_em` é `timestamptz`.

🆕 Fora do banco (só na memória do processo da API): o estado de presença e as conversas abertas (§15) e os resumos por IA (§13.5). 🆕 `ef13c22`: também a fila dos bots (o que cada bot está respondendo, §13.7) e a fila de indexação dos documentos (§13.9) — reiniciar a API perde o que estava em andamento. Os trechos com os vetores ficam no **Qdrant** (volume `conversa-qdrant`).

---

## 17. Tarefas periódicas do servidor

`src/tarefas.ts:97-104`:
| Tarefa | Intervalo | Faz |
|---|---|---|
| `AgendadorMensagens` | 60 s | notifica agendadas que amadureceram nos últimos 10 min (WS 2, push, WS 3, WS 61) |
| `VerificacaoAnexos` | 120 s | confirma uploads pendentes já no MinIO; marca falho após 15 min |
| `LimpezaGravacoes` | 3600 s | apaga gravações mais antigas que `gravacao_dias` |

Atraso máximo para uma agendada "sair": ~60 s após `visivel_em`. 🆕 `ef13c22`: quando ela sai, o `AgendadorMensagens` também chama os bots da conversa (§13.7). Nenhuma tarefa periódica nova; as respostas dos bots e a indexação da base de conhecimento rodam em segundo plano, disparadas pelas rotas.

---

## 18. O que o cliente precisa implementar (checklist)

1. **Login** (`/api/login`) guardando `token`, `id`, `dispositivo.id`; reenviar `dispositivo_id` nos próximos logins. Relogar ao receber 401 (token dura 12 h, sem refresh).
2. **Dispositivo/FCM**: `PATCH /api/dispositivo` com nome/modelo/versão/plataforma e `token_fcm`; atualizar no `onNewToken`; `token_fcm: null` ao sair da conta.
3. **Push de dados**: montar a notificação localmente com `data.titulo`, `data.mensagem`, `data.conversa` (string). Tratar `mensagem` vazia (áudio/arquivo/chamada). Ao tocar, abrir a conversa.
4. **WebSocket** em `wss://host/ws/` com login por mensagem, ping de protocolo, reconexão com backoff, ressincronização ao reconectar (§6.7) e polling de reserva.
5. **Sincronização de mensagens**: ao receber WS 2 (ou push), `GET /mensagens/novas?desde=<cursor>` → recarregar conversas e a conversa aberta; manter cursor `ate`.
6. **Status**: chamar `/mensagem/visualizar` por mensagem vista; `/mensagem/reproduzir` ao tocar áudio; processar WS 3 via `/mensagem/status`.
7. **Lista de conversas**: ordenar (fixadas por `fixada_ordem`, depois `ultima_mensagem` com nulos no fim), filtrar arquivadas, mostrar prévia (tratar `@[Nome](id)`, código, `""` para anexos).
8. **Mensagens**: render dos 8 tipos (o 8, enquete, é 🆕); resposta/encaminhamento com cadeia; reações; ocultas (`excluida_em`); menções; código; figurinhas Lottie embutidas; agendamento; pesquisa (🆕 `6d20ff7`: na pesquisa de uma conversa, resultados dos chats das ligações vêm com outro `conversa_id` — abrir a conversa do resultado); galeria de anexos.
9. **Anexos**: SHA-256, `PUT /anexo` → PUT na URL → `POST /anexo/confirmar?identificador=`; download via `GET /anexo` com cache por identificador; miniaturas locais.
10. **Transcrição**: `PUT` + polling do `GET`.
11. **Chamadas**: tocar ao receber WS 51 (e consultar `/chamadas/pendentes` ao abrir o app); timeout de 30 s com `nao_atendeu:true`; WHIP/WHEP com TURN relay TCP/TLS e H264; reagir a 52/53/54/55/56/57; resumo tipo 6; histórico; chat da chamada; tela/ponteiro.
12. **Atividades**: lista paginada, contador, ~~marcar vistas~~, WS 61. 🆕 `e25c4db`: usar o ~~`nova`~~ e o `/atividades/novas` do servidor (resposta/menção lida no chat deixa de ser nova e o contador cai sozinho); reler o contador no WS 61 que chega depois de visualizar mensagens. 🆕 `ef13c22`: ler **`pendente`** (não existe mais `nova`); abrir a aba **não** zera o contador (`/atividades/vistas` não conta mais); concluir à mão (`POST /atividade/concluir`, inclusive ao abrir uma chamada perdida); mandar `POST /atividades/reacoes-vistas` com as próprias mensagens reagidas que aparecem no chat; tratar o tipo **5** ("pediu confirmação de leitura", resolvido ao confirmar).
13. **Presença**: `/contatos/online` + WS 60 (🆕 ampliada no item 18); digitando/gravando (HTTP + WS 4/5 com expiração local).
14. **Perfil**: alterar nome/e-mail/telefone/avatar, senha; exclusão de conta (409).
15. **Configurações administrativas** (opcional no Android): permissões e parâmetros conforme `/usuario/permissoes`.
16. **SIP** (se usado): ler `/sip` e registrar no PBX.
17. 🆕 **Enquetes**: criar em grupos (`PUT /enquete`, com data final opcional), bolha tipo 8 lendo `GET /enquete?id=`, votar (`POST /enquete/votar`, a lista substitui o voto), reler ao receber WS 62; não permitir encaminhar enquete; 🆕 `5cad911` encerrar (`POST /enquete/encerrar`), mudar a data final (`PATCH /enquete`) e tratar o prazo vencido como encerrada.
18. 🆕 `b8ea0c2` **Presença**: mandar o tipo 63 (`ativo:false` em segundo plano/ocioso, `ativo:true` + conversa aberta ao voltar); estado inicial por `GET /contatos/presenca` e `GET /conversa/presentes`; tratar WS 60 novo (`estado`, `visto_em` que pode vir `null` sem apagar o conhecido), WS 63 e o WS 60 do próprio usuário logo após o login; tela de privacidade (`GET /usuario/privacidade` + `PATCH /usuario`).
19. 🆕 `b8ea0c2` **Confirmação de leitura**: opção "pedir confirmação" no envio (`pede_confirmacao`), botão "Confirmar leitura" para os destinatários (`POST /mensagem/confirmar`), lista de quem confirmou (`confirmacao`), WS 8. 🆕 `6d20ff7`: escolher quem confirma (`confirmacao_usuarios`); pedir depois, até 24 h após o envio, só nas próprias mensagens (`POST /mensagem/pedir-confirmacao`); botão só com `pode_confirmar` (e nunca em mensagem oculta); mostrar `destinatarios` × `usuarios` ("x de `total`"); aplicar o WS 65.
20. 🆕 `b8ea0c2` **Grupo com imagem ou emoji** (`PATCH /conversa`, `avatar_url`/`emoji` em `/conversas`, recarregar com WS 40); chats de chamada marcados por `chamada`.
21. 🆕 `b8ea0c2` **Chamadas**: "Chamar de novo" para quem recusou/não atendeu (`POST /chamada/chamar-novamente`), reler `/chamada/dados` no WS 57 `{acao:'participantes'}`; tocar de novo num WS 51 de chamada já recusada. 🆕 `a40fd47`: ~~o chat da chamada pode ser a própria conversa (inclusive direta)~~ 🆕 revisto em `6d20ff7`: o chat da chamada é um grupo novo (nome = nomes dos participantes; reconhecer por `chamada: true`), salvo ligação feita dentro do chat de outra ligação, que reaproveita esse chat; usar `chat_chamada_id` (tipo 6) e `conversa_chat_id` (histórico). 🆕 `6d20ff7`: depois de `POST /chamada/video`, a chamada passa a `tipo = 2` (ícone de vídeo no resumo e no histórico).
22. 🆕 `12cf0cf` **Postagens em grupo**: criar (`postagem:{titulo}`), bolha com título, contagem, não lidas e 2 últimas respostas; tela das respostas (`GET /postagem/respostas`), responder (`postagem_id`), marcar vistas (`POST /postagem/visualizar`), WS 64; contar e mostrar respostas que chegam por `/conversas`, `/mensagens/novas`, WS 2/push e atividades sem esperar vê-las no fluxo.
23. 🆕 **IA** (opcional, se `GET /recursos` diz `ia: true`): resumo da conversa (POST + polling do GET) e sugestão de texto com cancelamento; configuração `ia_*` e teste para administradores.
24. 🆕 **Figurinhas**: trocar o catálogo pelo pacote `noto` (55, com o crédito CC BY 4.0) e mostrar "Figurinha" para ids antigos (`basico/`, `rostos/`, `coisas/`).
25. 🆕 `ef13c22` **Permissões de uso**: ler `GET /usuario/permissoes` no login e ao voltar ao app; esconder ou desabilitar o que falta (emoji, figurinha, anexos por tipo, responder, encaminhar, reagir, postagem, votação, confirmação, agendar, criar grupo, chamadas de áudio/vídeo, compartilhar tela, ocultar, nome, e-mail/telefone, avatar, senha, privacidade); mostrar a mensagem do 403 quando o servidor recusar; no perfil, mandar só as chaves que mudaram.
26. 🆕 `ef13c22` **Cadastro**: consultar `GET /cadastro` antes de oferecer "Cadastre-se"; tratar 403 de cadastro fechado.
27. 🆕 `ef13c22` **Áudio**: ler `duracao` e `intensidade` dos conteúdos (duração e onda sem baixar o arquivo); opcional: mandar os dois no `PUT /anexo` ao enviar gravações e áudios.
28. 🆕 `ef13c22` **Bots**: marcar bots na lista de contatos/conversas (por `bot: true` de `/usuario/contatos`); "digitando" demorado do bot; não oferecer ligação para bot; tirar bots da lista padrão de confirmação de leitura.
29. 🆕 `ef13c22` (opcional, administração) cadastro de pessoas e bots (`PUT /usuario/novo`), bots, ferramentas e base de conhecimento — telas do web.

---

## 19. Pegadinhas para clientes

### Nomes, tipos e formatos
1. **snake_case em tudo** (`conversa_id`, `mensagens_sem_visualizar`, `token_fcm`...). Exceções em camelCase: só a resposta de `/api/ice` (`iceServers`, `iceTransportPolicy`, `urls`, `username`, `credential`).
2. Nomes de parâmetros de query **sem separador**: `mensagemreferencia`, `mensagensprevias`, `mensagensseguintes`.
3. Mesmo conceito com nomes diferentes por rota: `conversa` (query/corpo de status, membros, arquivada) × `conversa_id` (mensagem, membro) × `id` (digitando, gravando) × `grupo` (WS 3). Mensagem: `mensagem` (status) × `mensagem_id` (reação) × `id` (excluir/detalhe).
4. `PUT /mensagem` devolve a linha crua (`usuario_id`), mas `GET /mensagens` usa `remetente_id`/`remetente`. `remetente` é **só o primeiro nome**.
5. `mensagem_referencia.origem_mensagem_id` no envio = id da mensagem **referenciada** (o banco usa o nome ao contrário).
6. **Ids são números**, exceto: `PUT /anexo` com `existe:true` devolve `id` **string**; JWT `sub` string; push `data.conversa` string; WS 3 `mensagens` é CSV string. Use desserialização tolerante (ex.: `JsonPrimitive` → Long).
7. `atividade.id` é bigint no banco, mas chega como número JSON (cabe em Long).
8. Booleanos JSON verdadeiros (`true/false`) nas respostas; parâmetro `turn_forcar_relay` vira bool na API mas é `'1'/'0'` no banco.

### Datas
9. Datas da API saem como ISO-8601 **UTC com milissegundos e `Z`** (`2026-10-06T12:00:00.123Z`) — serialização de `Date` do JS. Parse com `Instant.parse`.
10. Exceção: JSON do resumo de chamada (conteúdo tipo 6) usa `YYYY-MM-DDTHH:MM:SS` **sem fuso e sem ms** (UTC).
11. Entradas: `visivel_em` e `desde` em ISO-8601 (sem fuso = UTC; prefira mandar com `Z` ou offset). `de` do histórico é data/hora sem fuso (comparado em UTC) e `ate` é data `AAAA-MM-DD`.
12. `visivel_em` é cortado no minuto e precisa estar ≥ agora+5 min (relógio do servidor).

### Campos nulos e ausentes
13. `mensagem_referencia` e `reacoes` **ausentes** (não `null`) quando não existem. 🆕 O mesmo vale para `confirmacao`, `postagem` e `conteudos[].chat_chamada_id`.
14. `conteudos[].nome/extensao/transcricao` vêm `""` (não `null`) em texto; `conteudo` nunca `null` na resposta (vira `""`). 🆕 `ef13c22`: já `duracao` e `intensidade` vêm sempre, como **`null`** fora de áudio (ou em áudio sem esses dados).
15. `/sip` sem ramal → `{}` (objeto vazio). `PATCH /dispositivo` sem campos → eco do corpo.
16. `/chamadas/pendentes` converte `conversa_id` nulo em **0**; `/chamadas` e `/chamada/dados` não (e `/chamada/dados` nem tem `conversa_id`).
17. `alterada` sempre `null` (sem edição). Status 5 de chamada/participante nunca ocorre.

### Comportamentos que surpreendem
18. **Mensagem oculta traz o conteúdo original** em `/mensagens` — o cliente é quem esconde.
19. Conteúdo de anexo sem registro em `anexo` some de `conteudos`.
20. Status `true` quando não há destinatários (inclui mensagens anteriores à entrada no grupo).
21. `GET /mensagens` marca **todas** as pendentes da conversa como recebidas (efeito colateral de leitura) e no máximo 100 mensagens vêm por chamada.
22. `/mensagem/visualizar` marca **uma** mensagem por vez.
23. `/conversas` inclui arquivadas e põe conversas sem mensagem **no topo** (`NULLS FIRST`).
24. `ultima_mensagem_texto` cru: contém `@[Nome](id)` e cercas de código; `""` para áudio/arquivo/chamada.
25. `/usuario/contatos` lista **todos os usuários**, sem avatar.
26. Não há deduplicação de conversa direta; criar duas vezes cria duas conversas.
27. `DELETE /conversa` quebra com 500 (FK).
28. Sair do grupo exige `conversa_usuario.id` (pegar em `/conversa/usuarios`), não o id do usuário.
29. WS 2 **não identifica a conversa**; e pode chegar **duplicado** quando o destinatário tem mais de um dispositivo com `token_fcm` (a consulta faz um envio por par usuário×dispositivo — `src/notificacoes.ts:56-75`).
30. **Push não é enviado se o usuário tiver qualquer WebSocket aberto** (ex.: aba do web aberta no PC) e nunca para conversa arquivada. 🆕 `b8ea0c2`: continua assim com a presença nova — socket `ausente` ou de quem aparece offline também bloqueia o push.
31. **Chamadas não geram push**: com o app morto/sem socket, o aparelho não toca. Ao abrir o app, consultar `/chamadas/pendentes`. (Melhoria de servidor necessária para tocar em segundo plano.)
32. Push sem `android.priority` (data message de prioridade normal).
33. `tipo` da chamada é ambíguo (áudio/vídeo para o cliente, simples/grupo para a regra de status). O chamador **precisa se incluir** em `usuarios`.
34. Chamada que termina por saída não envia WS 52 — tratar WS 55.
35. Ações de chamada para quem não participa → **404**, não 403.
36. `/ice` devolve `urls` string única e credencial de 1 h; mídia só funciona via relay (TURN TCP/TLS). VP8 não é gravado — preferir **H264**.
37. MediaMTX sem autenticação e `GET /anexo` sem checagem de conversa (qualquer usuário logado com o identificador/caminho acessa) — não confiar nisso como privacidade.
38. `PUT /anexo` com `existe:true` devolve URL de **GET** (não de upload) e não informa `upload_status`; se o anexo existente falhou (status 2), não há como reenviar pelo mesmo identificador. Verifique com `/anexo/existe` antes.
39. `POST /anexo/confirmar` recebe `identificador` na **query**.
40. URLs do MinIO dependem do `Host` da requisição e expiram (upload 300 s, download/avatares 600 s). Não reescrever host; não cachear URL.
41. Corpo JSON máximo **1 MB** em `/api/*`; login limitado a 10/min por IP (resposta 503 do nginx, não JSON).
42. Strings maiores que as colunas (dispositivo 50/15, extensão 10, emoji 10, descrição 100...) e duplicidades não tratadas (e-mail, SIP, permissão via FK) viram **500** com mensagem do Postgres.
43. Erros sempre `{ "error": "..." }`; mensagens em português e com acentos — não usar o texto como chave lógica (exceto quando documentado, ex.: `Upload ainda não foi concluído`).
44. WebSocket: ~~login sem confirmação~~ 🆕 `b8ea0c2`: o login é confirmado indiretamente por um WS 60 sobre o próprio usuário (na primeira conexão chegam dois, §6.2); `tipo 9` fora do enum; token expirado não derruba a conexão; `/ws` sem barra final não funciona.
45. Certificado de desenvolvimento é mkcert — configurar confiança no build de debug.
46. Não existe: refresh de token, logout, edição de mensagem, listagem de agendadas, administradores de grupo, miniaturas, evento de saída de membro, evento de fim de transcrição. 🆕 `b8ea0c2`: **passaram a existir** imagem/emoji de grupo (§11.5), estado "ausente" e "visto por último" (§15.1) — os itens "avatar de grupo" e "status ausente/visto por último" saíram desta lista. Também não há evento de fim do resumo por IA (polling, §13.5). 🆕 `ef13c22`: nem evento quando o administrador muda as permissões de alguém (§13.1) ou quando termina a indexação de um documento (§13.9).
47. 🆕 Enquete (tipo 8) não pode ser enviada por `PUT /mensagem` nem encaminhada (400); a criação é só por `PUT /enquete`, e só em grupo.
48. 🆕 `POST /enquete/votar` **substitui** o voto (não é toggle de uma opção): mande sempre a lista completa das opções marcadas.
49. 🆕 `d4435db` Reações: no máximo **5 emojis diferentes por pessoa** na mesma mensagem (o 6º → 400). Confira antes da reação otimista, como o web.
50. 🆕 `5cad911` Data final da votação: o servidor não manda nada quando ela passa. `encerrada` só muda na próxima leitura; o cliente compara `encerra_em` com a hora atual.

### 🆕 Outubro (`b8ea0c2` … `e25c4db`)
51. 🆕 `12cf0cf` **Respostas de postagem ficam fora do fluxo, mas não do resto**: não vêm em `GET /mensagens` nem em `/pesquisar`, porém contam em `mensagens_sem_visualizar`, podem ser a "última mensagem" de `/conversas` (`mensagem_id`, `ultima_mensagem`, `ultima_mensagem_texto`), geram WS 2 e push, aparecem em `/anexos` e no resumo por IA. O contador só zera com `POST /postagem/visualizar` (ou visualizar cada resposta).
52. 🆕 `12cf0cf` `GET /mensagens/novas` pode devolver `mensagem_id` de uma **resposta de postagem**; e WS 3 pode trazer ids de respostas. Um id desconhecido não quer dizer "falta mensagem no fluxo"; `mensagemreferencia=<resposta>` com 0/0 devolve `[]`.
53. 🆕 `12cf0cf` Atividade "respondeu" de postagem aponta para a **postagem** (texto da postagem); menção dentro de uma resposta aponta para a **resposta** (fora do fluxo).
54. 🆕 `12cf0cf` `postagem_id: 0` em `PUT /mensagem` não é "sem postagem": dá 500 (FK). Mande `null` ou omita. O título da postagem é cortado em 150 caracteres sem aviso.
55. 🆕 `b8ea0c2` `visto_em` só é gravado quando a pessoa **sai do ativo**; enquanto está ativa, o valor do banco é antigo. No WS 60, `visto_em: null` também aparece em mudanças que não saem do ativo (ex.: ausente → offline) — não apague o valor que já tinha.
56. 🆕 `b8ea0c2` No tipo 63 enviado pelo cliente, `ativo` precisa ser o booleano `true`; qualquer outra coisa conta como inativo. Cliente que nunca manda o 63 fica **ativo** enquanto o socket estiver aberto, mesmo em segundo plano.
57. 🆕 `b8ea0c2` A presença não mexeu no push: qualquer socket aberto (ativo, ausente ou "aparecer offline") bloqueia o push de mensagem.
58. 🆕 `b8ea0c2` O WS 60 agora também chega **sobre o próprio usuário** (todas as conexões dele, a cada mudança, e duas vezes na primeira conexão). Não trate como "contato ficou online".
59. 🆕 `b8ea0c2` WS 51 pode chegar de novo para um `chamada_id` que o usuário **já recusou** (alguém usou "chamar de novo"). Não descartar 51 só porque a chamada já foi vista/recusada; conferir em `/chamada/dados` ou `/chamadas/pendentes` (status 1 = tocando).
60. 🆕 `b8ea0c2` `POST /chamada/chamar-novamente` devolve os **dados da chamada**, não `{id}`; só vale com o alvo em status 2 sem ter entrado e chamada 1/3 — na prática, só em chamadas `tipo = 2`.
61. 🆕 `a40fd47` ~~`PUT /chamada/chat` pode devolver a **conversa de origem**, inclusive uma **conversa direta**: não presumir que o chat da chamada é um grupo novo "Chamada: …".~~ 🆕 revisto em `6d20ff7`: o chat volta a ser um **grupo novo**, agora com o nome só com os nomes dos participantes (`Ana, Bruno`, sem `Chamada: `) — não reconhecer chat de chamada pelo nome, e sim por `chamada: true`. A conversa de origem só é devolvida quando a ligação foi feita **dentro do chat de outra ligação** com todo mundo nele; nesse caso, quem não é membro recebe o WS 57 `{acao:'chat'}` e leva 403 ao abrir. Ligações antigas (entre `a40fd47` e `6d20ff7`) podem ter o chat numa direta.
62. 🆕 `b8ea0c2` `PATCH /conversa` dispara WS 40 a cada chamada (mesmo sem mudança); `avatar_anexo_id` inexistente dá 500; `emoji` aceita qualquer grafema único (não só emoji).
63. 🆕 `b8ea0c2` ~~`confirmacao.total` conta só quem tinha linha de status (membros no momento do envio); quem entrou depois pode confirmar e ficar "a mais" em `usuarios`.~~ 🆕 revisto em `6d20ff7`: `total` = tamanho de `destinatarios` (a lista de quem precisa confirmar, que pode ser só parte dos membros); quem está fora dela leva **403** ao confirmar. Mostre o botão por `pode_confirmar`, não por "não sou o autor". Só confirmações antigas (antes da 043) podem aparecer em `usuarios` sem estar em `destinatarios`.
64. 🆕 IA: a sugestão nunca dá erro por falha da IA (vem `""`), mas dá 400 se a IA não está configurada — confira `GET /recursos`. O resumo some ao reiniciar a API (404 no GET: pedir de novo).
65. 🆕 Web `d202c06`: figurinhas antigas (`basico/`, `rostos/`, `coisas/`) não existem mais no web; só `noto/<nome>` (55). Mostrar "Figurinha" para ids desconhecidos e dar o crédito CC BY 4.0.
66. 🆕 `6d20ff7` `POST /mensagem/pedir-confirmacao` é **uma vez só** e até **24 h** depois de `coalesce(visivel_em, inserida)`; depois de pedir, não dá para mudar a lista. Sem `usuarios`, a lista é de quem é membro **no momento do pedido**. A resposta é o objeto `confirmacao`, não a mensagem.
67. 🆕 `6d20ff7` O **WS 65** chega a todos os membros, **inclusive a quem pediu** (em todos os aparelhos), cada um com a sua `confirmacao`. Pode chegar para uma **agendada que ainda não saiu** (os outros não a têm): id desconhecido → ignorar. Pedido no envio não gera WS 65.
68. 🆕 `6d20ff7` `pode_confirmar` não olha se a mensagem foi ocultada: pode vir `true` numa mensagem oculta, e confirmar dá 400. Quem saiu da conversa continua em `destinatarios`/`total` e nunca mais confirma.
69. 🆕 `6d20ff7` `GET /pesquisar?conversa=X` pode trazer resultados com **`conversa_id` ≠ X** (chats das ligações feitas em X). Agrupar/abrir pelo `conversa_id` de cada resultado.
70. 🆕 `6d20ff7` `POST /chamada/video` muda `tipo` de 1 para 2 **de vez** (não volta a 1) e, com ele, a regra de status (§9.4). Numa chamada já encerrada também muda: o histórico passa a dizer vídeo, o resumo já gravado não. Não guarde o `tipo` lido no início da chamada como verdade; releia `/chamada/dados` se precisar.
71. 🆕 `e25c4db` Atividade de resposta/menção pode passar de `nova: true` a `false` **sem** `POST /atividades/vistas` (o usuário leu no chat, inclusive em outro aparelho). Não mantenha o "nova" localmente: releia `/atividades/novas` (e a lista) a cada WS 61 — que agora também chega logo depois de o próprio app chamar `POST /mensagem/visualizar` ou `POST /postagem/visualizar`. (🆕 `ef13c22`: leia `pendente` no lugar de `nova`; a regra de reler a cada WS 61 continua — pegadinha 72.)

### 🆕 Bots, permissões de uso e atividades pendentes (`ef13c22`)
72. 🆕 `ef13c22` **`GET /atividades` não tem mais `nova`**: a chave virou **`pendente`**, com outro sentido (pendência até concluir, §12). Um DTO que lê `nova` com valor padrão recebe sempre o padrão, sem erro. E `POST /atividades/vistas` **não zera mais** o contador: o número só cai quando cada atividade é resolvida (lida no chat, confirmada) ou concluída (`/atividade/concluir`, `/atividades/reacoes-vistas`).
73. 🆕 `ef13c22` Tipo de atividade **5** (pediram a confirmação de leitura) é novo. Trate tipos desconhecidos sem quebrar. A tipo 5 só deixa de ser pendente com a **confirmação** (ou concluída à mão, que não confirma).
74. 🆕 `ef13c22` Reação (1) e chamada perdida (4) **nunca** deixam de ser pendentes sozinhas: o cliente precisa chamar `POST /atividades/reacoes-vistas` (com as próprias mensagens reagidas que mostrou) ou `POST /atividade/concluir`. Sem isso, o contador só cresce.
75. 🆕 `ef13c22` **403 novos, por falta de permissão de uso**, também para quem é membro, autor ou dono: `PUT /mensagem`, `DELETE /mensagem`, `PUT /mensagem/reacao`, `PUT /conversa` (grupo), `PUT /enquete`, `POST /mensagem/pedir-confirmacao`, `PUT /chamada/iniciar`, `POST /chamada/video`, `PATCH /usuario`, `POST /alterar-senha`, `PUT`/`PATCH /sip`. A mensagem (`Você não tem permissão para: …`) é para mostrar ao usuário; o 403 **não** é sessão expirada. Envio otimista precisa desfazer a bolha.
76. 🆕 `ef13c22` `PATCH /usuario` confere a permissão de cada **chave enviada**, mesmo que o valor não mude: mandar nome e e-mail juntos exige `nome` **e** `email`; `telefone` conta como `email`. Mande só o que mudou.
77. 🆕 `ef13c22` A permissão `emoji` vale para **qualquer** caractere `\p{Extended_Pictographic}` no texto — inclusive `©`, `®`, `™` e algumas setas. Quem não tem `emoji` leva 403 por um "©". Não vale para reações, título de postagem, pergunta/opções de votação nem emoji de grupo.
78. 🆕 `ef13c22` Tirar a própria reação também pede `reagir`; ocultar (e apagar agendada) pede `ocultar`. Quem perde a permissão fica sem desfazer.
79. 🆕 `ef13c22` Encaminhar (`mensagem_referencia.tipo = 2`) pede **só** `encaminhar`: não confere os conteúdos (que vêm do cliente), nem agendar, postagem ou confirmação (S28 do doc 08).
80. 🆕 `ef13c22` Vídeo × arquivo é decidido pela **extensão do anexo** (`mp4`, `webm`, `ogg`, `mov`, `m4v`, `mkv` → `video`; o resto → `arquivo`). Um `.ogg` de áudio enviado como arquivo (tipo 3) pede `video`.
81. 🆕 `ef13c22` `PUT /chamada/iniciar` **sem `tipo`** com 3+ pessoas em `usuarios` vale `tipo = 2` e pede `chamada_video`, mesmo que a ideia fosse áudio. Mande sempre o `tipo`.
82. 🆕 `ef13c22` `GET /sip` devolve `{}` tanto "sem ramal" quanto "sem permissão `telefonia`".
83. 🆕 `ef13c22` **Bots são usuários comuns**: estão em `/usuario/contatos` (só ali com `bot: true`), podem ser o `destinatario_id` de uma conversa direta, mandam mensagens, reações, figurinhas e votações, e "digitam" (WS 4) por minutos. Nunca ficam online, nunca atendem chamada, nunca marcam `recebida` e só marcam `visualizada` a mensagem que os chamou — então, em grupo com bot, o status agregado do remetente (`recebida`/`visualizada`) **não fecha** (S27 do doc 08).
84. 🆕 `ef13c22` Pedir confirmação de leitura **sem lista** num grupo com bot põe o bot em `destinatarios`, e ele nunca confirma ("x de y" não fecha). Mande `confirmacao_usuarios` sem os bots.
85. 🆕 `ef13c22` Login de bot → 401 `Bot não entra pelo login.` (como login errado, não sessão expirada).
86. 🆕 `ef13c22` Cadastro fechado: `PUT /usuario` → **403** (não 400). Consulte `GET /cadastro` (pública) antes de mostrar a tela.
87. 🆕 `ef13c22` `conteudos[].duracao` é decimal (ex.: `44.95`), em segundos; `intensidade` tem até 200 valores de 0 a 100. Os dois são do **primeiro** envio daquele arquivo (o servidor não mede e não corrige); podem vir `null` num áudio enviado por um cliente que não os manda.

---

## 20. Histórico de atualizações deste documento

| Data | Commit do servidor | O que mudou no contrato |
|---|---|---|
| 2026-10-06 (manhã) | `7f670c3` | Versão inicial da auditoria |
| 2026-10-06 (noite) | `8031fa5` — "Votação em grupo: enquete com escolha única ou múltipla" | **3 rotas novas** (`PUT /enquete`, `GET /enquete`, `POST /enquete/votar`), **evento WS 62** `EnqueteAtualizada`, **conteúdo tipo 8** (só o servidor grava; não pode ser encaminhado), prévia/push `enquete`, migração `035.sql` (tabelas `enquete`, `enquete_opcao`, `enquete_voto`). Detalhes em §10.13 |
| 2026-10-07 (noite) | `d4435db` — "Reações: no máximo 5 emojis diferentes por pessoa na mesma mensagem" | `PUT /mensagem/reacao` recusa o 6º emoji da mesma pessoa (400). Detalhes em §10.12 |
| 2026-10-07 (noite) | `5cad911` — "Votação com data final e encerramento antes do prazo" | **2 rotas novas** (`POST /enquete/encerrar`, `PATCH /enquete`; total 69); `encerra_em` na criação; campos `encerra_em`, `encerrada_em`, `encerrada`, `pode_encerrar`, `pode_alterar_prazo` no `Enquete`; votar encerrada → 400; WS 62 também ao encerrar e ao mudar a data; migração `036.sql`. Detalhes em §10.13 |
| 2026-10-13 🆕 | `779c8ed` — "iniciar-desenvolvimento.bat atualiza também as dependências da API" | Só script de desenvolvimento; sem efeito no contrato |
| 2026-10-13 🆕 | `b8ea0c2` — "Presença, confirmação de leitura, resumo por IA e chamar de novo" (2026-10-09) | **9 rotas novas** (70–78: `GET /contatos/presenca`, `GET /usuario/privacidade`, `GET /conversa/presentes`, `POST /mensagem/confirmar`, `POST /chamada/chamar-novamente`, `GET /recursos`, `POST /parametros/ia/testar`, `POST`/`GET /conversa/resumo`); **WS 8** `ConfirmacaoLeitura`, **WS 63** `Presenca` (bidirecional), WS 60 com `estado`/`visto_em` a cada mudança e também ao próprio usuário e após o login; WS 57 `{acao:'participantes'}`; `PATCH /usuario` com preferências de privacidade; `PATCH /conversa` com imagem/emoji de grupo, `descricao` opcional e WS 40; `GET /conversas` com `emoji`, `chamada` e imagem do grupo; `pede_confirmacao` e `confirmacao` nas mensagens; parâmetros `ia_*`. Migrações `037`–`041`. Detalhes em §5.1, §6, §9.14, §10.14, §11, §13.2–§13.5, §15 |
| 2026-10-13 🆕 | `ea7e974` — "IA: resumo pede JSON por json_schema (LM Studio recusa json_object)" (2026-10-09) | Interno (formato do pedido à IA; repete sem os extras se o servidor de IA der 400). §13.4 |
| 2026-10-13 🆕 | `ea02f57` — "Adiciona sugestão de texto" (2026-10-09) | **1 rota nova** (79: `POST /ia/sugestao`). §13.6 |
| 2026-10-13 🆕 | `a40fd47` — "Chat da chamada reaproveita a conversa e vai junto da mensagem da ligação" (2026-10-10) | `PUT /chamada/chat` reaproveita a conversa da ligação quando todos já estão nela (sem WS 40; `/chamada/entrar` não inclui ninguém); `chat_chamada_id` no conteúdo tipo 6; `conversa_chat_id` no histórico; `chamada:false` na conversa reaproveitada. §9.3, §9.7, §9.8, §9.12, §11.2 |
| 2026-10-13 🆕 | `12cf0cf` — "Postagens em grupo com respostas embaixo da postagem" (2026-10-11) | **2 rotas novas** (80–81: `GET /postagem/respostas`, `POST /postagem/visualizar`; total **81**); `postagem`/`postagem_id` em `PUT /mensagem` e 3 erros 400 novos; chave `postagem` nas mensagens; respostas fora de `/mensagens` e `/pesquisar`, mas dentro de `/conversas`, `/mensagens/novas`, WS 2/3 e push; atividade "respondeu" apontando para a postagem; **WS 64** `PostagemAtualizada`. Migração `042`. §10.15, pegadinhas 51–54 |
| 2026-10-13 🆕 | Web `d202c06` (não é do servidor) | Figurinhas: só o pacote `noto` (55), pacotes antigos apagados. §10.6 |
| 2026-10-15 🆕 | `6d20ff7` — "Confirmação de leitura com lista e prazo, chat novo por ligação e pesquisa nos chats das ligações" (2026-10-13) | **1 rota nova** (82: `POST /mensagem/pedir-confirmacao`, até 24 h, uma vez; total **82**); `confirmacao_usuarios` em `PUT /mensagem` (+ erro 400); `confirmacao` com `pode_confirmar` e `destinatarios`, `total` = tamanho da lista; `POST /mensagem/confirmar` → 403 fora da lista; **WS 65** `ConfirmacaoPedida`. `PUT /chamada/chat` **volta a criar grupo novo** (nome = nomes dos participantes, sem `Chamada: `) e só reaproveita a origem quando a ligação foi feita dentro do chat de outra ligação (revê `a40fd47`). `POST /chamada/video` grava `tipo = 2`. `GET /pesquisar?conversa=X` inclui os chats das ligações feitas em X. Migração `043` (`mensagem_confirmacao_destinatario`). §4, §6, §9.1–§9.4, §9.7, §9.8, §9.12, §10.2, §10.3, §10.11, §10.14, §11.2, §16, pegadinhas 61, 63, 66–70 |
| 2026-10-15 🆕 | `e25c4db` — "Atividade de resposta ou menção já lida no chat deixa de contar como nova" (2026-10-14) | `nova` em `GET /atividades` e a contagem de `GET /atividades/novas` deixam de fora resposta/menção cuja mensagem o usuário já visualizou (resposta de postagem: sem resposta do autor por ler); **WS 61** também depois de `POST /mensagem/visualizar` e `POST /postagem/visualizar` que marcaram mensagem com atividade ligada. Sem rota nova. §4, §6.6, §10.5, §10.15, §12, pegadinha 71 |
| 2026-10-15 (noite) 🆕 | `ef13c22` — "Bots de IA com ferramentas e base de conhecimento, permissões de uso e cadastro pelo administrador" (2026-10-15) | **27 rotas novas** (83–109; total **109**: 3 públicas + 106 autenticadas): `GET /cadastro` (pública), `PUT /usuario/novo`, 7 de bots, 10 de base de conhecimento, 6 de ferramentas externas, `POST /atividade/concluir` e `POST /atividades/reacoes-vistas`. **Permissões de uso** (26 códigos; 403 com a descrição em `PUT /mensagem`, `DELETE /mensagem`, reação, grupo, votação, confirmação, chamadas, perfil, senha e SIP) e 4 de administração novas (`bots`, `usuarios`, `ferramentas`, `conhecimento`); ninguém retira as próprias (400). **Atividades**: `nova` → **`pendente`** (pendência até concluir), tipo **5** (confirmação pedida), `POST /atividades/vistas` não zera mais, contador só de conversas em que ainda é membro. **Áudio**: `PUT /anexo` aceita `duracao`/`intensidade`; `conteudos[]` ganham as duas chaves. **Cadastro**: `cadastro_aberto` (403 no `PUT /usuario` fechado). **Bots de IA**: usuários comuns que respondem na direta, quando mencionados ou respondidos; `bot` em `/usuario/contatos`; login de bot → 401. Parâmetros `cadastro_aberto` e `ia_modelo_embeddings`; `GET /recursos` com `conhecimento`. Sem tipo de WebSocket novo (WS 61 e 4 com gatilhos novos). Docker: serviço `qdrant` e volumes `conversa-postgres`/`conversa-minio`/`conversa-qdrant` (`bin\renomear-volumes.bat`). Migrações `044`–`052`. §1, §2.1, §2.5–§2.6, §3, §4, §5.1, §6, §7.1, §8, §9.2, §10.2–§10.3, §10.9, §10.12–§10.15, §11, §12, §13.1–§13.3, §13.7–§13.9, §14, §15.1, §16–§18, pegadinhas 14, 46, 72–87 |
