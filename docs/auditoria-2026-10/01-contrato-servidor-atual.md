# Contrato atual do servidor Conversa (referência para o cliente Android Kotlin)

> Auditoria de 2026-10-06. Fonte: repositório `conversa` (servidor Bun + Elysia), último commit lido **`8031fa5`** ("Votação em grupo: enquete com escolha única ou múltipla", 2026-10-06 21:22), atualizado a partir de `7f670c3`. Migrações aplicadas até a versão **35** (`migracoes/000.sql` … `035.sql`). Mudanças do commit `8031fa5` estão marcadas com 🆕 e resumidas na §20.
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
| `api` | Bun + Elysia | 8080 (HTTP + WebSocket) | `127.0.0.1:8080` | API REST em `/api` e WebSocket em `/ws/` | `docker-compose.yml:134-160`, `src/servidor.ts:31` |
| `nginx` | nginx:1.28-alpine | 443 (dev) / 80 (prod) | `${CONVERSA_PORTA:-443}` | Ponto de entrada único | `docker-compose.yml:165-183`, `bin/nginx/nginx.conf` |

Volumes: `pgdata` (banco), `minio` (anexos), `conversa-dados` (`/dados`: pepper das senhas, credenciais do MinIO, segredo do TURN), `conversa-gravacoes` (`/gravacoes`) — `docker-compose.yml:193-201`.

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
- Variáveis de ambiente da API: `CONVERSA_SERVER`, `CONVERSA_PORT`, `CONVERSA_DATABASE`, `CONVERSA_USERNAME`, `CONVERSA_PASSWORD`, `CONVERSA_PORTA_HTTP` (8080), `CONVERSA_S3_INTERNO`, `CONVERSA_TURN_PORTA`, `CONVERSA_DADOS` (`/dados`), `CONVERSA_GRAVACOES` (`/gravacoes`) — `src/configuracao.ts:57-86`.

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

Cadastro é público e qualquer um pode se cadastrar (não há convite nem aprovação).

---

## 3. Formato de erro e validação

Tratador global: `src/app.ts:57-77`. **Todo erro é JSON `{ "error": "<mensagem em português>" }`**.

| Origem | Status | Mensagem |
|---|---|---|
| `ErroHttp` lançado pelo código | 400/401/403/404/409 | mensagem específica (`src/erros.ts:13-19`) |
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

Prefixo `/api` em todas (`src/rotas.ts:28`). "Token" = exige `Authorization: Bearer`. Total: **67 rotas** em `src/rotas.ts` (2 públicas + 65 autenticadas; as 3 de enquete 🆕 estão no fim da tabela como 65–67), mais a documentação `/api/docs` e `/api/docs/json` (públicas, do plugin OpenAPI) e o WebSocket `/ws/`.

Legenda de efeitos: **WS n** = evento WebSocket tipo n (§6.4); **FCM** = push.

| # | Método | Caminho | Auth | Query | Corpo | Resposta (resumo) | Permissão / regra | Efeitos | Ref. rota |
|---|---|---|---|---|---|---|---|---|---|
| 1 | POST | `/login` | pública | – | `login`, `senha`, `dispositivo_id?` | usuário + `dispositivo` + `token` | – | cria dispositivo | `rotas.ts:33` |
| 2 | PUT | `/usuario` | pública | – | `nome`, `login`, `email`, `telefone?`, `senha` | linha usuário sem senha | – | – | `rotas.ts:38` |
| 3 | POST | `/alterar-senha` | token | – | `senha_atual`, `senha` | `{}` | senha atual correta (400 se errada) | – | `rotas.ts:46` |
| 4 | PATCH | `/dispositivo` | token | – | `id`, `nome?`, `modelo?`, `versao_so?`, `plataforma?`, `token_fcm?` | linha dispositivo | dono do dispositivo (403) | – | `rotas.ts:51` |
| 5 | PUT | `/dispositivo/usuario` | token | `dispositivo_id` | – | linha `dispositivo_usuario` | dono (403) | – | `rotas.ts:53` |
| 6 | PATCH | `/usuario` | token | – | `id`, `nome?`, `email?`, `telefone?`, `avatar_anexo_id?` | linha usuário sem senha | só o próprio (`id` = token, 403) | – | `rotas.ts:56` |
| 7 | DELETE | `/usuario` | token | `id` | – | linha usuário excluída sem senha | só o próprio (403); com histórico 409 | apaga dispositivos | `rotas.ts:58` |
| 8 | PUT | `/usuario/contato` | token | `relacionamento_id` | – | linha `usuario_contato` | – | – | `rotas.ts:60` |
| 9 | DELETE | `/usuario/contato` | token | `id` | – | linha excluída | dono do contato (403) | – | `rotas.ts:63` |
| 10 | GET | `/usuario/contatos` | token | – | – | `[{id,nome,login,email,telefone}]` de **todos** os usuários | – | – | `rotas.ts:65` |
| 11 | GET | `/contatos/online` | token | – | – | `number[]` (ids online) | – | – | `rotas.ts:67` |
| 12 | PUT | `/conversa` | token | – | `descricao?`, `tipo?` | linha conversa | – | criador vira membro | `rotas.ts:71` |
| 13 | PATCH | `/conversa` | token | – | `id`, `descricao` | linha conversa | membro (403) | – | `rotas.ts:73` |
| 14 | DELETE | `/conversa` | token | `id` | – | linha excluída | membro (403) | ver §11.6 (na prática 500) | `rotas.ts:75` |
| 15 | GET | `/conversas` | token | – | – | lista de conversas (§11.2) | – | – | `rotas.ts:77` |
| 16 | PATCH | `/conversa/fixadas` | token | – | `conversas: int[]` | `{conversas}` | – | – | `rotas.ts:79` |
| 17 | PATCH | `/conversa/arquivada` | token | – | `conversa`, `arquivada: bool` | `{id, arquivada}` | membro (403) | desfixa ao arquivar | `rotas.ts:82` |
| 18 | GET | `/conversa/usuarios` | token | `conversa` | – | `[{id,usuario_id,nome,avatar_url}]` | não membro recebe `[]` | – | `rotas.ts:85` |
| 19 | PUT | `/conversa/usuario` | token | – | `usuario_id`, `conversa_id` | linha `conversa_usuario` | membro (403) | **WS 40** aos membros (exceto quem adicionou) | `rotas.ts:88` |
| 20 | DELETE | `/conversa/usuario` | token | `id` (= `conversa_usuario.id`) | – | linha excluída | só a própria participação (403) | – | `rotas.ts:90` |
| 21 | POST | `/conversa/digitando` | token | – | `id` (conversa) | `{}` | membro (403) | **WS 4** aos outros membros | `rotas.ts:92` |
| 22 | POST | `/conversa/gravando` | token | – | `id` (conversa) | `{}` | membro (403) | **WS 5** aos outros membros | `rotas.ts:97` |
| 23 | PUT | `/mensagem` | token | – | `conversa_id`, `visivel_em?`, `conteudos[]`, `mensagem_referencia?` | linha mensagem (+`mensagem_referencia`) | membro (403) | **WS 2**, **FCM**, **WS 61** (resposta/menção); agendada só ao sair | `rotas.ts:104` |
| 24 | DELETE | `/mensagem` | token | `id` | – | `{id, conversa_id, excluida_em}` ou linha apagada | só o autor (403) | **WS 3** aos outros, **WS 61** | `rotas.ts:106` |
| 25 | GET | `/mensagens` | token | `conversa`, `mensagemreferencia=0`, `mensagensprevias=0`, `mensagensseguintes=0` | – | `MensagemResposta[]` (§10.2) | membro (403) | marca **recebida** + **WS 3** aos outros | `rotas.ts:108` |
| 26 | POST | `/mensagem/visualizar` | token | – | `conversa`, `mensagem` | `{sucesso:true}` | membro (403) | **WS 3** aos outros | `rotas.ts:111` |
| 27 | POST | `/mensagem/reproduzir` | token | – | `conversa`, `mensagem` | `{sucesso:true}` | membro (403) | **WS 3** aos outros | `rotas.ts:114` |
| 28 | GET | `/mensagem/status` | token | `conversa`, `mensagem` (CSV de ids) | – | `[{conversa_id,mensagem_id,recebida,visualizada,reproduzida,excluida_em}]` | membro (403) | – | `rotas.ts:117` |
| 29 | GET | `/mensagem/status/detalhe` | token | `id` | – | `[{usuario_id,nome,recebida,visualizada,reproduzida}]` (datas) | só o autor (403); 404 | – | `rotas.ts:120` |
| 30 | GET | `/mensagens/novas` | token | `desde=''` (ISO) | – | `[{conversa_id,mensagem_id,ate}]` | – | – | `rotas.ts:123` |
| 31 | GET | `/pesquisar` | token | `conversa=0`, `texto=''` | – | `MensagemResposta[]` | só conversas do usuário | – | `rotas.ts:127` |
| 32 | PUT | `/mensagem/reacao` | token | – | `mensagem_id`, `emoji` | `{mensagem_id, emoji, acao}` | membro (403); 404 | **WS 7** aos outros, **WS 61** ao autor | `rotas.ts:130` |
| 33 | GET | `/anexo/existe` | token | `identificador` | – | `{existe:false}` ou `{existe:true,id,identificador,tipo,tamanho,upload_status}` | **nenhuma** | – | `rotas.ts:134` |
| 34 | GET | `/anexo` | token | `identificador` | – | `{url}` (GET assinado, 600 s) | **nenhuma** | pode marcar upload concluído | `rotas.ts:136` |
| 35 | PUT | `/anexo` | token | – | `identificador`, `tipo`, `nome?`, `extensao?`, `tamanho` | §8.2 | – | cria registro | `rotas.ts:138` |
| 36 | POST | `/anexo/confirmar` | token | `identificador` | – | `{confirmado:true, upload_status:1}` | – | – | `rotas.ts:140` |
| 37 | GET | `/anexos` | token | `conversa=0`, `autor=0`, `direcao=''`, `tipos=''`, `antes=0`, `limite=0` | – | lista de anexos (§8.5) | conversa: membro (403) | – | `rotas.ts:142` |
| 38 | GET | `/anexo/transcricao` | token | `identificador` | – | `{status,texto,erro}` | áudio (4/5) em conversa do usuário (403) | – | `rotas.ts:146` |
| 39 | PUT | `/anexo/transcricao` | token | – | `identificador` | `{status,texto,erro}` | idem; 400 se transcritor não configurado | inicia job em 2º plano | `rotas.ts:149` |
| 40 | PUT | `/chamada/iniciar` | token | – | `tipo?`, `conversa_id?`, `usuarios:[{id}]` | dados da chamada (§9.3) | – | **WS 51** aos convidados | `rotas.ts:154` |
| 41 | POST | `/chamada/cancelar` | token | – | `id` | `{id}` | participante (404) | status 6, resumo, chamada perdida, **WS 52** | `rotas.ts:156` |
| 42 | POST | `/chamada/entrar` | token | – | `id` | `{id}` | participante (404) | **WS 54** (outros + próprio), entra no chat (WS 40 a si) | `rotas.ts:158` |
| 43 | POST | `/chamada/recusar` | token | – | `id`, `nao_atendeu?` | `{id}` | participante (404) | **WS 53** (outros + próprio), resumo, chamada perdida | `rotas.ts:160` |
| 44 | POST | `/chamada/sair` | token | – | `id` | `{id}` | participante (404) | **WS 55**, resumo | `rotas.ts:163` |
| 45 | PUT | `/chamada/usuario` | token | – | `chamada_id`, `usuario_id` | `{id}` | participante (404) | **WS 51** a todos menos quem adicionou | `rotas.ts:165` |
| 46 | POST | `/chamada/finalizar` | token | – | `id` | `{id}` | participante (404) | status 4, resumo, chamada perdida, **WS 52** | `rotas.ts:168` |
| 47 | GET | `/usuario/permissoes` | token | – | – | `string[]` | – | – | `rotas.ts:170` |
| 48 | GET | `/permissoes` | token | – | – | `{permissoes, usuarios, modo_aberto}` | `permissoes` (403) | – | `rotas.ts:172` |
| 49 | PUT | `/permissao/usuario` | token | – | `usuario_id`, `codigo` | `{usuario_id, codigo}` | `permissoes` (403); 404 | – | `rotas.ts:174` |
| 50 | DELETE | `/permissao/usuario` | token | `usuario_id`, `codigo` | – | `{usuario_id, codigo}` | `permissoes` (403); 400 último | – | `rotas.ts:177` |
| 51 | GET | `/parametros` | token | – | – | §13.2 | `parametros` (403) | – | `rotas.ts:180` |
| 52 | PATCH | `/parametros` | token | – | §13.2 | §13.2 | `parametros` (403) | recarrega config | `rotas.ts:182` |
| 53 | GET | `/atividades` | token | `antes=0`, `limite=30` (1..100) | – | `Atividade[]` (§12) | – | – | `rotas.ts:184` |
| 54 | GET | `/atividades/novas` | token | – | – | `{quantidade}` | – | – | `rotas.ts:186` |
| 55 | POST | `/atividades/vistas` | token | – | – | `{vistas_em}` | – | – | `rotas.ts:188` |
| 56 | PUT | `/chamada/chat` | token | – | `id` (chamada) | `{conversa_id}` | participante (404) | se criou: **WS 40** + **WS 57** `{acao:'chat'}` | `rotas.ts:190` |
| 57 | GET | `/chamada/dados` | token | `id` | – | dados da chamada (§9.3) | participante (404) | – | `rotas.ts:192` |
| 58 | GET | `/chamadas/pendentes` | token | – | – | chamadas tocando para o usuário (§9.6) | – | – | `rotas.ts:194` |
| 59 | GET | `/chamadas` | token | `participante=0`, `de=''`, `ate=''` | – | histórico (§9.7) | – | – | `rotas.ts:196` |
| 60 | POST | `/chamada/video` | token | – | `id` | `{}` | participante (404) | **WS 56** aos outros participantes | `rotas.ts:198` |
| 61 | GET | `/ice` | token | – | – | `{iceServers, iceTransportPolicy}` | – | – | `rotas.ts:203` |
| 62 | GET | `/sip` | token | – | – | linha `sip` ou `{}` | – | – | `rotas.ts:207` |
| 63 | PUT | `/sip` | token | – | §14 | linha `sip` | – | – | `rotas.ts:209` |
| 64 | PATCH | `/sip` | token | – | `id` + campos | linha `sip` | dono (403) | – | `rotas.ts:211` |
| 65 🆕 | PUT | `/enquete` | token | – | `conversa_id`, `pergunta` (≤ 300), `opcoes: string[]` (2–12, cada ≤ 200), `multipla: bool` | mensagem criada + `enquete_id` | membro; **só grupo** (400) | cria a mensagem tipo 8 (WS 2, push, WS 3 como uma mensagem normal) | `rotas.ts` (`8031fa5`), `enquetes.ts:12-45` |
| 66 🆕 | GET | `/enquete` | token | `id` | – | `Enquete` (§10.13) | membro da conversa (403); 404 | – | `enquetes.ts:54-78` |
| 67 🆕 | POST | `/enquete/votar` | token | – | `enquete_id`, `opcoes: int[]` (≤ 12; vazio tira o voto) | `Enquete` (§10.13) | membro; escolha única aceita 1 (400) | **WS 62** a **todos** os membros (inclusive quem votou) | `enquetes.ts:82-116` |

Todas as rotas de `src/rotas.ts` estão na tabela acima; nenhuma foi omitida.

---

## 5. Detalhe das rotas por área

### 5.1 Usuário

**`POST /api/alterar-senha`** — `src/usuarios.ts:68-74`. Corpo `{ "senha_atual": string, "senha": string }`. 400 `Senha atual incorreta!` (`src/autorizacao.ts:61-68`); 400 `Senha inválida!` se `senha` > 72. **Não** confere o mínimo de 4 caracteres aqui. Resposta `{}`. O token continua válido.

**`PATCH /api/usuario`** — `src/usuarios.ts:110-116`. Corpo `{ "id": int, "nome"?: string, "email"?: string, "telefone"?: string|null, "avatar_anexo_id"?: int|null }` (`src/esquemas.ts:37`). `id` precisa ser o do token (403 `Acesso negado!`). Só grava os campos enviados; sem nenhum, devolve a linha atual. Resposta: linha `usuario` sem `senha`. Sem evento WebSocket (os outros só veem a mudança ao recarregar listas).
- Avatar: enviar o anexo (tipo 2, §8) e depois `avatar_anexo_id` = **id numérico** do anexo.

**`DELETE /api/usuario?id=`** — `src/usuarios.ts:118-131`. Só a própria conta. Apaga `dispositivo_usuario` e `dispositivo` do usuário; se houver histórico (mensagens, conversas, chamadas...), **409** `A conta tem histórico e não pode ser excluída!`.

**`GET /api/usuario/contatos`** — `src/usuarios.ts:142-144`. Devolve **todos os usuários do sistema** (inclusive o próprio), `[{ id, nome, login, email, telefone }]`, ordenado por `id`. **Sem avatar.** Não usa a tabela `usuario_contato`.

**`PUT /api/usuario/contato?relacionamento_id=`** / **`DELETE /api/usuario/contato?id=`** — `src/usuarios.ts:133-140`. Mantêm a tabela `usuario_contato` (contatos salvos). **Não há rota para listar** os contatos salvos. O PUT não valida duplicidade nem existência (FK inválida = 500).

**`GET /api/contatos/online`** — `src/usuarios.ts:147-155`. `number[]` com os ids dos usuários que têm **conversa direta (tipo 1)** com o usuário e estão com WebSocket conectado agora.

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
3. **Não há resposta de sucesso.** A conexão passa a valer quando o servidor processa o login (o teste espera 100 ms — `tests/api.ts:173-177`).
4. Token inválido/expirado: o servidor envia `{"tipo":0,"message":"Token inválido ou expirado"}` (ou `Token não informado`) e **não fecha** a conexão; ela simplesmente não recebe nada (`src/websocket.ts:83-88`). O cliente deve fechar, renovar o login HTTP e reconectar.
5. Um segundo `tipo:1` na mesma conexão já autenticada é ignorado (`src/websocket.ts:78-80`).
6. Expiração do token **depois** do login não derruba a conexão: ela continua recebendo eventos até cair. Na reconexão, porém, o token velho é recusado.

### 6.3 Envelope

Todas as mensagens (nos dois sentidos) são objetos JSON com o campo numérico `tipo`. Os demais campos variam por tipo e ficam **no nível raiz** (não há `dados` genérico, exceto no tipo 57).

Erros de leitura (`src/websocket.ts:60-68`):
- Texto que não é JSON: `{"tipo":9,"message":"Erro ao ler os dados do WebSocket: JSON inválido!"}`
- JSON sem `tipo`: `{"tipo":9,"message":"Erro ao ler os dados do WebSocket: Par \"tipo\" não encontrado!"}`

> Atenção: o `tipo: 9` **não existe** no enum `TipoMensagemSocket`.

### 6.4 Enum `TipoMensagemSocket` (`src/websocket.ts:4-22`)

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
| *(9: erro de leitura, fora do enum)* | 9 | servidor → cliente |

### 6.5 Mensagens cliente → servidor aceitas

Somente duas (`src/websocket.ts:56-100`):

| tipo | Formato | Regra |
|---|---|---|
| 1 | `{"tipo":1,"token":"<jwt>"}` | login (§6.2) |
| 57 | `{"tipo":57,"chamada_id":<int>,"dados":{...}}` | Só vale se a conexão já fez login, `chamada_id` inteiro > 0, `dados` é objeto com `JSON.stringify(dados).length <= 2000`, e o remetente está **dentro** da chamada (`chamada_usuario.status = 3` e `chamada.status in (1,3)`). Repassado aos outros participantes com status 3. Sem resposta, sem gravação. Participantes ficam em cache por 5 s (`src/websocket.ts:164-221`) |

Qualquer outro `tipo` é ignorado silenciosamente. Não existe ping de aplicação. Ações como "digitando" são **HTTP** (`POST /api/conversa/digitando`), não WebSocket.

### 6.6 Eventos servidor → cliente (payload exato e quando disparam)

Todos são enviados a **todas as conexões abertas** do usuário destinatário (várias abas/aparelhos) — `src/websocket.ts:119-128`.

| tipo | Payload | Quando / para quem | Ref. |
|---|---|---|---|
| 0 | `{"tipo":0,"message":string}` | Falha no login do socket | `websocket.ts:86` |
| 2 | `{"tipo":2,"titulo":string,"mensagem":string}` | Nova mensagem (imediata em `PUT /mensagem`, ou agendada quando amadurece) para **cada outro membro** da conversa. `titulo` = nome completo do remetente; `mensagem` = texto resumido (§7.2). **Não traz `conversa_id` nem `mensagem_id`**: o cliente deve chamar `GET /api/mensagens/novas` (como o web faz). Pode chegar **duplicado** (§19) | `websocket.ts:134-136`, `notificacoes.ts:52-76` |
| 3 | `{"tipo":3,"grupo":<conversa_id>,"mensagens":"12,13,14"}` | Mudança de status de mensagens (`mensagens` é **string CSV**). Dispara: ao listar `/mensagens` (recebidas → aos outros membros), `visualizar`/`reproduzir` (aos outros), exclusão de mensagem (aos outros), agendada amadurecendo (aos outros), resumo de chamada inserido (a **todos** os membros, inclusive o autor). Cliente: chamar `GET /api/mensagem/status`; se algum id é desconhecido, recarregar mensagens | `websocket.ts:138-140`, `notificacoes.ts:43-49` |
| 4 | `{"tipo":4,"conversa_id":int,"usuario_id":int}` | Alguém chamou `POST /conversa/digitando`; vai aos outros membros. Não há evento de "parou" | `websocket.ts:142-144`, `conversas.ts:237-240` |
| 5 | `{"tipo":5,"conversa_id":int,"usuario_id":int}` | `POST /conversa/gravando` | idem |
| 7 | `{"tipo":7,"conversa_id":int,"mensagem_id":int,"usuario_id":int,"emoji":string,"acao":"add"\|"remove"}` | Reação alternada; aos outros membros da conversa | `websocket.ts:146-148`, `mensagens.ts:748-752` |
| 40 | `{"tipo":40,"conversa_id":int,"usuario_id":int}` | (a) `PUT /conversa/usuario` adicionou membro novo → a todos os membros exceto quem adicionou (inclui o novo); (b) chat da chamada criado → membros do grupo exceto quem pediu; (c) ao `POST /chamada/entrar` com chat já existente → ao próprio usuário. **Não** dispara na criação da conversa, renomeação, nem saída de membro. Cliente: recarregar `/conversas` | `conversas.ts:232`, `chamadas.ts:258,280` |
| 51 | `{"tipo":51,"chamada_id":int,"usuario_id":int}` | `usuario_id` = quem ligou/adicionou. Em `iniciar`: a todos os participantes menos o criador. Em `PUT /chamada/usuario`: a todos menos quem adicionou (inclusive quem já está na chamada) | `chamadas.ts:108,289` |
| 52 | `{"tipo":52,"chamada_id":int,"usuario_id":int}` | `cancelar` ou `finalizar`, aos outros participantes (qualquer status). **Não** dispara quando a chamada termina por saída (`sair`) | `chamadas.ts:158,298` |
| 53 | `{"tipo":53,"chamada_id":int,"usuario_id":int}` | `recusar`: aos outros participantes **e ao próprio** (outras abas param de tocar) | `chamadas.ts:193-195` |
| 54 | `{"tipo":54,"chamada_id":int,"usuario_id":int}` | `entrar`: aos outros e **ao próprio** | `chamadas.ts:208-210` |
| 55 | `{"tipo":55,"chamada_id":int,"usuario_id":int}` | `sair`: aos outros participantes | `chamadas.ts:223` |
| 56 | `{"tipo":56,"chamada_id":int,"usuario_id":int}` | `POST /chamada/video`: aos outros participantes | `chamadas.ts:302-305` |
| 57 | `{"tipo":57,"chamada_id":int,"usuario_id":int,"dados":{...}}` | Repasse de sinal (§9.11) ou aviso do servidor `{acao:"chat",conversa_id}` | `websocket.ts:156-158,213-215` |
| 60 | `{"tipo":60,"usuario_id":int,"online":bool}` | Usuário abriu a **primeira** conexão / fechou a **última**; vai a quem tem conversa direta (tipo 1) com ele | `websocket.ts:224-237` |
| 61 | `{"tipo":61}` | Atividade nova ou removida para o usuário; recarregar `/atividades/novas` | `websocket.ts:151-153`, `atividades.ts` |
| 62 🆕 | `{"tipo":62,"enquete_id":int,"conversa_id":int}` | Alguém votou (ou tirou o voto) numa enquete; vai a todos os membros da conversa, inclusive outras abas/aparelhos de quem votou. O cliente relê `GET /enquete?id=` só se a bolha está carregada | `websocket.ts` (`notificarEnquete`), `enquetes.ts:110-114` |
| 9 | `{"tipo":9,"message":string}` | Mensagem do cliente ilegível | `websocket.ts:61-66` |

### 6.7 Keepalive e reconexão

- **Não há ping/pong de aplicação** no código. O WebSocket é o do Bun via Elysia sem opções de `idleTimeout`/`sendPings` (`src/websocket.ts:55`), ou seja, valem os padrões do Bun (pelo que se sabe do Bun: `idleTimeout` 120 s e envio automático de pings de protocolo — **não confirmado no código do servidor**). O nginx mantém a conexão por até 3600 s sem tráfego (`bin/nginx/nginx.conf:111-112`); a borda de produção também usa 3600 s (`SETUP.md:124-125`).
- Recomendação para OkHttp: `pingInterval(30, SECONDS)` (pings de protocolo são respondidos automaticamente pelo servidor).
- O servidor não guarda estado de sessão nem fila: eventos ocorridos com o socket fora **se perdem**. Na reconexão, o cliente deve (como o web faz — `conversa-web/src/stores/chat.ts:693-735`):
  1. mandar `{tipo:1, token}`;
  2. `GET /api/contatos/online`;
  3. `GET /api/atividades/novas`;
  4. `GET /api/chamadas/pendentes`;
  5. `GET /api/mensagens/novas?desde=<cursor>` e `GET /api/conversas`.
- Backoff do web: `min(30 s, 1 s × 2^tentativas)`; com o socket fora, polling a cada 8 s de `/mensagens/novas` e `/chamadas/pendentes`.

### 6.8 Presença online

Ver §15.

---

## 7. Push FCM e dispositivos

### 7.1 Quando é enviado

**Apenas para mensagens novas** (`src/notificacoes.ts:52-76`), chamado por:
- `PUT /api/mensagem` não agendada (`src/mensagens.ts:222-225`);
- mensagem agendada quando amadurece (tarefa a cada 60 s, `src/mensagens.ts:264-283`, `src/tarefas.ts:16-44`).

Para cada outro membro da conversa, para cada dispositivo com `ativo = true` e `token_fcm` não nulo, envia push **se e somente se**:
- o usuário **não** arquivou a conversa (`conversa_usuario.arquivada_em is null`), e
- o usuário **não tem nenhuma conexão WebSocket aberta** naquele momento (`usuarioConectado`) — em qualquer aparelho/aba.

**Não há push para**: chamadas recebidas, chamadas perdidas, reações, menções/atividades, inclusão em conversa, status. Chamada só chega por WebSocket (tipo 51) — ver §19.

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

**Não existem.** O servidor não gera thumbnail, não guarda largura/altura/duração/mime. O cliente baixa o arquivo inteiro (ou gera miniatura localmente). O tipo de mídia deve ser inferido por `tipo` + `extensao` do conteúdo da mensagem.

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

---

## 9. Chamadas

Código: `src/chamadas.ts`. Testes: `tests/chamadas.test.ts`, `tests/atividades.test.ts`, `tests/websocket.test.ts`. Cliente de referência: `conversa-web/src/stores/call.ts`.

### 9.1 Enums (`src/chamadas.ts:14-17`, `src/tabelas.ts:7-9`)

- **Tipo da chamada** (`chamada.tipo`): `1` e `2`. **Atenção: o significado é duplo.**
  - Para o cliente web e `tabelas.ts`: 1 = áudio, 2 = vídeo.
  - Para a migração 008 e para a lógica de status no servidor (`atualizarStatusChamada`, `src/chamadas.ts:472-479`): 1 = "Simples" (1:1), 2 = "Grupo".
  - Padrão quando `tipo` não é enviado: `usuarios.length === 2 ? 1 : 2` (`src/chamadas.ts:90`).
  - Consequência: uma chamada de **vídeo 1:1** segue a regra de **grupo**, e uma chamada de **áudio em grupo** segue a regra de **1:1** (§9.4).
- **Status da chamada** (`chamada.status`): 1 Pendente, 2 Recusada, 3 Em andamento, 4 Encerrada, 5 Desconectada, 6 Cancelada. (5 nunca é gravado pelo código atual.)
- **Status do participante** (`chamada_usuario.status`): 1 Pendente (tocando), 2 Recusou, 3 Entrou (está na chamada), 4 Saiu, 5 Desconectou (nunca gravado pelo código atual). O comentário da migração 008 tem outra ordem — vale o código.
- **Eventos** (`chamada_evento.tipo`): 1 Iniciada, 2 Cancelada, 3 Convidado, 4 Recusou, 5 Entrou, 6 Saiu, 7 Finalizada, 8 Não atendeu.

### 9.2 Ciclo de vida

| Ação | Rota / corpo | Efeito no banco | Eventos | Ref. |
|---|---|---|---|---|
| Iniciar | `PUT /api/chamada/iniciar` `{ "tipo"?: 1\|2, "conversa_id"?: int\|null, "usuarios": [{"id": int}, ...] }` | Cria `chamada` (status 1). Para cada item de `usuarios`: se for o próprio → status 3 (entrou), senão status 1. **O chamador deve incluir a si mesmo em `usuarios`** (senão não é participante e todas as ações respondem 404). `conversa_id` ≤ 0 vira `null` | WS 51 a todos os participantes menos o criador | `chamadas.ts:88-110` |
| Atender | `POST /api/chamada/entrar` `{ "id" }` | participante → 3 e `entrou_em` (só se ainda não entrou); recalcula status (§9.4) | WS 54 aos outros **e a si mesmo**; se o chat da chamada existe, entra no grupo e recebe WS 40 | `chamadas.ts:199-212` |
| Recusar | `POST /api/chamada/recusar` `{ "id", "nao_atendeu"?: bool }` | participante → 2 (só se ainda não entrou). Chamada → 2 se todos menos um recusaram. `nao_atendeu: true` (tocou até o fim, recusado automaticamente pelo app) registra evento 8 e **atividade de chamada perdida** | WS 53 aos outros **e a si mesmo** | `chamadas.ts:164-197` |
| Sair | `POST /api/chamada/sair` `{ "id" }` | participante → 4 e `saiu_em`; recalcula status | WS 55 aos outros. **Não** envia 52 mesmo que a chamada termine | `chamadas.ts:214-225` |
| Cancelar | `POST /api/chamada/cancelar` `{ "id" }` | chamada → 6 (qualquer participante pode; não confere se é o criador) | WS 52 aos outros | `chamadas.ts:153-160` |
| Finalizar | `POST /api/chamada/finalizar` `{ "id" }` | chamada → 4 + `finalizada` (qualquer participante) | WS 52 aos outros | `chamadas.ts:293-300` |
| Adicionar pessoa | `PUT /api/chamada/usuario` `{ "chamada_id", "usuario_id" }` | novo participante status 1 (não confere duplicidade: repetir = linha duplicada) | WS 51 a todos menos quem adicionou | `chamadas.ts:283-291` |
| Anunciar vídeo | `POST /api/chamada/video` `{ "id" }` | – | WS 56 aos outros (o web oferece "Apenas assistir") | `chamadas.ts:302-305` |
| Chat da chamada | `PUT /api/chamada/chat` `{ "id" }` | §9.12 | §9.12 | `chamadas.ts:230-266` |

Todas as ações exigem que o usuário seja participante (qualquer status) — senão **404 `Chamada não encontrada!`** (não 403) — `src/chamadas.ts:70-82`. Todas respondem `{ "id": <chamada> }` (exceto iniciar, chat e video).

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
- **Não traz `conversa_id`** (a conversa de origem); só `conversa_chat_id`.
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
| `{"acao":"chat","conversa_id":<int>}` | **o servidor**, ao criar o chat da chamada | todos passam a usar essa conversa como chat da chamada |

### 9.12 Chat da chamada — `PUT /api/chamada/chat`

`src/chamadas.ts:227-281`. Corpo `{"id": <chamada>}` → `{"conversa_id": int}`.
- Na primeira vez cria uma conversa **grupo (tipo 2)** com descrição `Chamada: <nomes>` (até 80 caracteres) e membros = participantes com status 3, 4 ou 5 + quem pediu. Grava `chamada.conversa_chat_id`. Chamadas seguintes devolvem a mesma.
- Ao criar: WS 40 aos membros (exceto quem pediu) e WS 57 `{acao:"chat",conversa_id}` a **todos** os outros participantes da chamada (qualquer status).
- Quem entra depois (`/chamada/entrar`) é adicionado ao grupo e recebe WS 40.
- Mensagens do chat são mensagens normais (`PUT /mensagem` na `conversa_id` devolvida).

### 9.13 Chamada perdida

Atividade tipo 4 (§12) para: (a) quem estava tocando (status 1) quando a chamada chegou a status final; (b) quem recusou com `nao_atendeu: true`. Uma por pessoa por chamada (índice único `ux_atividade_chamada_perdida`, `migracoes/033.sql:21`). O web recusa automaticamente após **30 s** tocando (`conversa-web/src/stores/call.ts:1527-1532`) e também recusa com `nao_atendeu: true` quando já está em outra chamada.

---

## 10. Mensagens

Código: `src/mensagens.ts`. Testes: `tests/mensagens.test.ts`, `tests/mensagensStatus.test.ts`.

### 10.1 Modelo

Tabelas: `mensagem` (`id`, `usuario_id`, `conversa_id`, `inserida` timestamp, `alterada` timestamp, `visivel_em` **timestamptz**, `excluida_em`, `excluida_por`), `mensagem_conteudo` (`id`, `mensagem_id`, `ordem`, `tipo`, `conteudo` **bytea** UTF-8), `mensagem_referencia` (`tipo` 1 resposta / 2 encaminhada, `origem_mensagem_id` = a mensagem nova, `destino_mensagem_id` = a referenciada), `mensagem_status` (PK `conversa_id, usuario_id, mensagem_id`; `recebida`, `visualizada`, `reproduzida` timestamps), `reacao` (`mensagem_id`, `usuario_id`, `emoji` varchar(10), unique pelos três).

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
- `conteudos[]`: sempre `id`, `ordem`, `tipo`, `conteudo`, `nome`, `extensao`, `transcricao_status`, `transcricao`; para tipos 1/6/7/8, `nome`/`extensao` = `""` e `transcricao_status` = 0. Para 2–5 `nome`/`extensao` vêm do anexo (vazio se nulo) e transcrição de `anexo_transcricao`.
  - **Conteúdo de anexo cujo `identificador` não existe na tabela `anexo` é omitido** (inner join — `src/mensagens.ts:436-437`).
- Status (`src/mensagens.ts:560-610`): para o **remetente**, `recebida/visualizada/reproduzida` = **todos** os destinatários; para os demais, só o **próprio** status. Se não há linhas de status (conversa só com o autor, ou mensagem anterior à entrada do usuário na conversa), os três vêm **`true`** (0 === 0).
- `reacoes[].usuarios[].nome` = nome completo; `avatar_url` assinado por 600 s ou `null`. Ordem das reações: primeira reação de cada emoji.
- `alterada`: **sempre `null`** — não existe rota de edição de mensagem.

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

Resposta (`src/mensagens.ts:185-227`): a linha `mensagem` crua + referência:
```json
{ "id": 101, "usuario_id": 7, "conversa_id": 42, "inserida": "...", "alterada": null, "visivel_em": null, "excluida_em": null, "excluida_por": null,
  "mensagem_referencia": { "tipo": 1, "origem_mensagem_id": 99 } }
```
Formato **diferente** do `MensagemResposta` (usa `usuario_id`, não tem `conteudos` nem status). Para exibir, o cliente usa o que enviou ou recarrega.

Efeitos: cria `mensagem_status` para cada outro membro; registra atividades (resposta → autor da original, se na mesma conversa; menções → membros mencionados; quem é respondido e mencionado recebe só a resposta) com WS 61; WS 2 + push (§7) se não agendada.

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

Efeito colateral: marca como **recebidas** todas as mensagens pendentes do usuário naquela conversa (não só as devolvidas) e envia WS 3 aos outros membros com os ids (`src/mensagens.ts:535-546`). Membro inexistente → 403.

### 10.5 Status

- `POST /api/mensagem/visualizar` e `POST /api/mensagem/reproduzir` com `{ "conversa": int, "mensagem": int }` → `{"sucesso":true}` (`src/mensagens.ts:618-629`). Marca **uma única mensagem** (não "até aqui"); para ler várias, chamar para cada uma. `reproduzir` **não** marca visualizada. Sempre envia WS 3 aos outros, mesmo sem mudança.
- `GET /api/mensagem/status?conversa=42&mensagem=101,102` → `[{ "conversa_id", "mensagem_id", "recebida": bool, "visualizada": bool, "reproduzida": bool, "excluida_em": date|null }]` ordenado por `mensagem_id` (`src/mensagens.ts:631-663`). Aqui os booleanos são **sempre o agregado de todos os destinatários** (mesmo para quem não é o autor). Ids sem linhas de status não aparecem. Lista vazia → `[]`; id não inteiro → 400.
- `GET /api/mensagem/status/detalhe?id=101` → `[{ "usuario_id", "nome", "recebida": date|null, "visualizada": date|null, "reproduzida": date|null }]`, ordenado por visualizada, recebida (nulos por último), nome. Só o autor (403 `Só quem enviou a mensagem vê quem recebeu e visualizou.`); 404 `Mensagem não encontrada!` (`src/mensagens.ts:665-686`).

### 10.6 Figurinhas (Lottie)

- Conteúdo tipo 7 = `"<pacote>/<nome>"`. A animação **não vem do servidor da API**: o cliente web a carrega de `/figurinhas/<pacote>/<nome>.json` servido pela página (`conversa-web/src/utils/figurinhas.ts:60-62`). Atenção: o `bin/web` atual do repositório do servidor **não contém** a pasta `figurinhas` (build de produção desatualizado); no Android, embutir as animações no app.
- Catálogo atual (`conversa-web/src/utils/figurinhas.ts:15-58`): `basico/{coracao,sorriso,risada,estrela,feito,festa,sono,pontinhos}`, `rostos/{piscadinha,apaixonado,beijo,legal,surpreso,triste,bravo}`, `coisas/{joinha,fogo,coracao-partido,presente,bolo,balao,cafe,sol,chuva}`. Identificador desconhecido → mostrar "Figurinha".

### 10.7 Menções e código

- Menção: no texto, `@[Nome](<usuario_id>)` (`src/atividades.ts:79-82`). Só gera atividade para membros da conversa.
- Código: texto tipo 1 com cerca de 3+ crases e linguagem opcional: ```` ```kotlin\n...\n``` ````. O web usa cerca maior que as crases internas. Não há tipo de conteúdo específico para código.

### 10.8 Agendadas

- `visivel_em`: ISO-8601 (sem fuso = UTC); cortado no minuto; precisa estar entre agora+5 min e agora+1 ano (400 `visivel_em deve estar a pelo menos 5 minutos no futuro.` / `visivel_em não pode estar mais de 1 ano no futuro.` / `visivel_em inválido (use ISO-8601 com offset).`). Não pode ter conteúdo tipo 6. — `src/mensagens.ts:149-172`
- Até a hora, só o autor vê (lista, conversa, pesquisa). A tarefa a cada 60 s envia WS 2, push, WS 3 e WS 61 quando amadurece (só olha os últimos 10 min — `src/tarefas.ts:7-44`).
- Excluir antes de sair: apagada de vez (§10.9).
- Não há rota para listar "minhas agendadas" nem para reagendar.

### 10.9 Excluir / ocultar — `DELETE /api/mensagem?id=`

`src/mensagens.ts:285-321`, `migracoes/031.sql`. Só o autor (403), a qualquer momento.
- Mensagem já visível: **exclusão lógica** — grava `excluida_em = now()` e `excluida_por`; resposta `{ "id", "conversa_id", "excluida_em" }`. Repetir não muda a data. Apaga atividades ligadas (WS 61) e envia WS 3 aos outros.
- Agendada que ainda não saiu: **apagada fisicamente** (com referências, status, reações). Resposta: linha da mensagem apagada + `"conteudo"` (linha de `mensagem_conteudo`).
- **Representação de mensagem oculta hoje**: a mensagem continua em `/mensagens` com `excluida_em` preenchido **e com `conteudos` completos (o texto original)** e reações. Cabe ao cliente esconder o conteúdo e mostrar "Mensagem oculta". Também: `/conversas` mostra `ultima_mensagem_texto = "Mensagem oculta"`; `/pesquisar` não acha; referências (`mensagem_referencia.mensagem.excluida_em`) trazem a marca; `/mensagem/status` traz `excluida_em`; `/anexos` continua listando os anexos dela.

### 10.10 Novas mensagens — `GET /api/mensagens/novas?desde=`

`src/mensagens.ts:688-720`. `desde` ISO-8601 (vazio = desde sempre; inválido = 400 `Parâmetro "desde" inválido (use ISO-8601 com offset).`).

Resposta: uma linha por conversa que tenha mensagem **de outra pessoa**, já visível, **não visualizada pelo usuário**, com data efetiva **maior que `desde`** (comparação truncada em milissegundos):
```json
[ { "conversa_id": 42, "mensagem_id": 105, "ate": "2026-10-06T12:00:00.123Z" } ]
```
`mensagem_id` = maior id da conversa entre as que casaram; `ate` = maior data efetiva (ms). Usar `ate` como próximo `desde` (cursor). Inclui conversas arquivadas e mensagens excluídas.

### 10.11 Pesquisa — `GET /api/pesquisar?conversa=0&texto=`

`src/mensagens.ts:373-399`. `texto` obrigatório não vazio (400 `Texto inválido!`). Espaços viram `%` (palavras em sequência, qualquer coisa entre elas); sem acento e sem maiúsculas (`unaccent ilike`). Só conteúdo tipo 1, só conversas do usuário (`conversa=0` = todas), exclui excluídas e agendadas alheias. Resposta no formato `MensagemResposta`, **no máximo 100 (as mais recentes)**, em ordem crescente. Não marca recebida.

### 10.12 Reações — `PUT /api/mensagem/reacao`

`src/mensagens.ts:722-755`. Corpo `{ "mensagem_id": int, "emoji": string }`. Alterna: se o usuário já reagiu com esse emoji, remove; senão adiciona. Um usuário pode ter vários emojis na mesma mensagem. Resposta `{ "mensagem_id", "emoji", "acao": "add"|"remove" }`. 404 `Mensagem não encontrada!`; 403 se não é membro. `emoji` > 10 caracteres (code points) → 500. Efeitos: WS 7 aos outros membros; atividade de reação para o autor da mensagem (se não for ele mesmo) com WS 61.

---

### 10.13 Enquetes (votação em grupo) 🆕 — commit `8031fa5`, migração `035.sql`

- **Só em conversas de grupo** (`tipo = 2`); em direta → 400 `Votação só pode ser criada em grupos.`
- **Criar** — `PUT /api/enquete`:

  ```json
  { "conversa_id": 10, "pergunta": "Onde vamos almoçar?", "opcoes": ["Centro", "Shopping"], "multipla": false }
  ```

  - Validações (`src/enquetes.ts:12-30`, `esquemas.ts`):
    - `pergunta` ≤ 300 caracteres e não vazia após `trim()` (400 `Informe a pergunta da votação.`);
    - `opcoes` com 2 a 12 itens, cada ≤ 200; as vazias são descartadas e precisam sobrar ≥ 2 (400 `A votação precisa de pelo menos duas opções.`);
    - opções repetidas, sem diferenciar maiúsculas → 400 `As opções da votação não podem se repetir.`
  - O servidor cria a enquete e as opções numa transação e depois **cria a mensagem** com um único conteúdo `{ordem:1, tipo:8, conteudo:"<id da enquete>"}`. Essa mensagem passa pelo mesmo caminho de `PUT /mensagem` (WS 2, push com texto `enquete`, WS 3, atividades). O `mensagem_id` é gravado na enquete.
  - **Resposta:** os campos da mensagem incluída (`id`, `conversa_id`, `usuario_id`, …) + `enquete_id`. O web, depois de criar, recarrega as mensagens da conversa e a lista de conversas.
- **Ler** — `GET /api/enquete?id=<enquete_id>` → objeto `Enquete`:

  ```json
  {
    "id": 42, "conversa_id": 10, "mensagem_id": 900, "pergunta": "Onde vamos almoçar?",
    "multipla": false, "criado_por": 7,
    "opcoes": [ { "id": 1, "texto": "Centro", "votantes": [ { "id": 7, "nome": "Ana Souza" } ] },
                { "id": 2, "texto": "Shopping", "votantes": [] } ],
    "total_votantes": 1,
    "meus_votos": [1]
  }
  ```

  - As `opcoes` vêm em `ordem`; os `votantes` vêm na ordem do voto e com o **nome completo**.
  - `total_votantes` conta pessoas distintas: na múltipla escolha, cada pessoa conta uma vez, então as porcentagens podem somar mais de 100% (o web calcula `votos_da_opcao / total_votantes`).
  - `meus_votos` são os ids das opções em que **o usuário do token** votou.
  - 404 `Votação não encontrada!`; 403 se não é membro.
- **Votar** — `POST /api/enquete/votar {enquete_id, opcoes:[ids]}`:
  - **Substitui** o voto do usuário pelas opções enviadas: lista vazia **tira** o voto.
  - Escolha única com mais de uma opção → 400 `Esta votação aceita uma opção só.`; opção de outra enquete → 400 `Opção que não é desta votação.`
  - Resposta: o `Enquete` atualizado. Efeito: **WS 62** `{enquete_id, conversa_id}` a todos os membros, inclusive quem votou.
- **Comportamento do cliente web** (`conversa-web` `39d06f9`):
  - Escolha única: tocar em outra opção troca o voto; tocar na marcada tira.
  - Múltipla: marca e desmarca.
  - Bolha com "📊 pergunta", "Escolha uma opção"/"Escolha uma ou mais opções", barra de porcentagem, nomes de quem votou e "N pessoas votaram"/"1 pessoa votou".
  - Prévia/resumo: "Votação".
- **Prévias e push:** `ultima_mensagem_texto` em `/conversas` e o texto do push/WS 2 dizem `enquete` (minúsculo); o web exibe "Votação".
- **Limitações:**
  - Não há rota para encerrar a enquete, editar, listar enquetes ou ver quando cada um votou.
  - Mensagem com enquete pode ser ocultada (`DELETE /mensagem`), mas os votos continuam acessíveis por `GET /enquete`.
  - A enquete não pode ser encaminhada (400).
  - Responder a uma mensagem de enquete é possível (a referência aponta para a mensagem).

## 11. Conversas

Código: `src/conversas.ts`. Testes: `tests/conversas.test.ts`.

### 11.1 Modelo

`conversa`: `id`, `descricao` varchar(100) nulo, `tipo` (1 direta/"chat", 2 grupo), `inserida`, `criado_por`. `conversa_usuario`: `id`, `usuario_id`, `conversa_id`, `fixada_ordem` int nulo, `arquivada_em` timestamp nulo, `criado_em`, `criado_por` (único `conversa_id+usuario_id`). Fixar e arquivar são **por usuário**.

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
  "fixada_ordem": null,
  "arquivada_em": null,
  "avatar_url": "https://<host>/storage/...|null"
}
```
- `descricao`: descrição da conversa (trim); se vazia/nula, o nome do destinatário (só para tipo 1).
- `nome`, `destinatario_id`, `avatar_url`: só em conversas **tipo 1** (o outro membro; se o usuário está sozinho, ele mesmo). Em grupos vêm `null`. **Grupos não têm avatar.**
- `mensagem_id`: id da última mensagem (0 se não há). `ultima_mensagem`: data efetiva da última (nulo se não há).
- `ultima_mensagem_texto` (do **primeiro conteúdo** da última mensagem): `"Mensagem oculta"` se excluída; texto **cru** se tipo 1 (com a marcação de menção e de código — não resumido); `"imagem"` (2); `"figurinha"` (7); `""` para arquivo, áudio, gravação e chamada.
- `mensagens_sem_visualizar`: quantidade de mensagens visíveis com `recebida` **ou** `visualizada` nula para o usuário.
- **Arquivadas vêm na lista** (com `arquivada_em` preenchido): o cliente filtra.
- Ordenação: `ultima_mensagem desc` — no Postgres, `DESC` põe **nulos primeiro**: conversas sem mensagens aparecem no topo. Fixadas não são ordenadas pelo servidor: ordenar no cliente por `fixada_ordem` (1 = primeira).
- `avatar_url` expira em 600 s.

### 11.3 Criar — `PUT /api/conversa`

`src/conversas.ts:39-44`. Corpo `{ "descricao"?: string|null, "tipo"?: int }` (tipo padrão 1 no banco; não validado). O criador entra como membro. Resposta: `{ "id", "descricao", "tipo", "inserida", "criado_por" }`. **Nenhum evento** é enviado na criação.

Fluxo (como o web/teste): criar e depois `PUT /api/conversa/usuario` para cada membro. O servidor **não deduplica conversas diretas**: antes de criar uma tipo 1, procurar em `/conversas` uma com `tipo=1` e `destinatario_id` = contato.

### 11.4 Membros

- `GET /api/conversa/usuarios?conversa=` → `[{ "id": <conversa_usuario.id>, "usuario_id", "nome", "avatar_url" }]`. Quem não é membro recebe `[]` (não 403). Sem ordem definida (`src/conversas.ts:203-220`).
- `PUT /api/conversa/usuario` `{ "usuario_id", "conversa_id" }` → linha `conversa_usuario`. Se já é membro, devolve a linha existente (sem erro e sem evento). Novo membro: WS 40 a todos os membros exceto quem adicionou (`src/conversas.ts:222-234`).
- `DELETE /api/conversa/usuario?id=<conversa_usuario.id>` — **id do vínculo, não do usuário**. Só o próprio vínculo (sair do grupo); 403 caso contrário. Sem evento WebSocket. Depois de sair, o usuário perde acesso e deixa de ver atividades da conversa.

### 11.5 Renomear — `PATCH /api/conversa`

`{ "id", "descricao" }` → linha da conversa. Qualquer membro. Sem evento WebSocket.

### 11.6 Excluir — `DELETE /api/conversa?id=`

`src/conversas.ts:51-54`. Valida membro e faz `delete from conversa`. Como `conversa_usuario` (e mensagens) referenciam `conversa` sem `on delete cascade`, **a exclusão de uma conversa com membros falha com 500** (violação de FK). Na prática a rota não funciona — não usar; para "apagar" use arquivar ou sair.

### 11.7 Fixar e arquivar

- `PATCH /api/conversa/fixadas` `{ "conversas": [idA, idB, ...] }` → `{ "conversas": [...] }`. Define a ordem completa das fixadas do usuário: posição = índice 1-based no array; conversas fixadas que não vierem no array deixam de ser fixadas (`src/conversas.ts:180-189`). Para desafixar tudo: `[]`.
- `PATCH /api/conversa/arquivada` `{ "conversa": int, "arquivada": bool }` → `{ "id", "arquivada" }`. Arquivar também desfixa. Arquivada não recebe push (mas recebe WS 2). **Não desarquiva sozinha** com mensagem nova (`src/conversas.ts:191-201`).

### 11.8 Contadores

- Por conversa: `mensagens_sem_visualizar` em `/conversas`.
- Total de não lidas: somar no cliente (decidir se conta arquivadas).

---

## 12. Atividades

Código: `src/atividades.ts`, `migracoes/033.sql`. Testes: `tests/atividades.test.ts`.

Tipos (`src/atividades.ts:8-13`): 1 Reação, 2 Resposta, 3 Menção, 4 Chamada perdida.

Quando são criadas (nunca para o próprio autor):
- Reação: para o autor da mensagem reagida; some quando a reação é retirada.
- Resposta: para o autor da mensagem respondida (mesma conversa).
- Menção: para cada membro mencionado (exceto quem já recebeu a de resposta).
- Chamada perdida: §9.13.
- Mensagem excluída: todas as atividades dela são apagadas.
- Agendada: a atividade nasce com `criado_em = visivel_em` e só aparece/conta depois disso.

`GET /api/atividades?antes=<id>&limite=30` (limite 1..100; `antes` = `id` da última atividade da página anterior) — `src/atividades.ts:103-153`. Mais recentes primeiro. Só de conversas em que o usuário ainda é membro (ou sem conversa):
```json
[ {
  "id": 33, "tipo": 1, "criado_em": "...", "nova": true,
  "autor_id": 8, "autor_nome": "Bruno Lima",
  "conversa_id": 42, "conversa_tipo": 1, "conversa_descricao": null,
  "mensagem_id": 101, "conteudo_tipo": 1, "texto": "bom dia",
  "chamada_id": null, "chamada_tipo": null, "emoji": "😂",
  "autor_avatar_url": null
} ]
```
- `mensagem_id`: na reação, a mensagem **do usuário** que recebeu a reação; na resposta/menção, a mensagem **nova do autor**.
- `texto`: primeiro conteúdo tipo 1 da mensagem, numa linha, menções viram `@Nome`, cortado em 120 caracteres com `…`; `null` se não há texto. `conteudo_tipo` = tipo do primeiro conteúdo.
- `conversa_descricao`: descrição trimada ou `null` (em conversa direta sem descrição, montar com `autor_nome`).
- `nova`: criada depois de `usuario.atividades_vistas_em`.
- `id` é bigint convertido para número.

`GET /api/atividades/novas` → `{ "quantidade": int }`.
`POST /api/atividades/vistas` (sem corpo) → `{ "vistas_em": date }`; zera o contador.
WS 61 `{tipo:61}` avisa mudança (nova ou removida).

---

## 13. Permissões e parâmetros do sistema

### 13.1 Permissões

Tabelas `permissao` (`codigo`, `descricao`) e `permissao_usuario` — `migracoes/034.sql`. Códigos (`src/permissoes.ts:6-9`):

| Código | Descrição (banco) | Libera |
|---|---|---|
| `parametros` | Ver e alterar as configurações do sistema | `GET/PATCH /api/parametros` |
| `permissoes` | Conceder e retirar permissões dos usuários | `GET /api/permissoes`, `PUT/DELETE /api/permissao/usuario` |

**Modo aberto** (`src/permissoes.ts:12-23`): enquanto **ninguém** tem `permissoes`, todos os usuários têm todas as permissões. A primeira concessão de `permissoes` fecha o modo. Não é possível retirar `permissoes` do último que a tem (400 `Ao menos uma pessoa precisa poder gerenciar as permissões.`).

Rotas:
- `GET /api/usuario/permissoes` → `["parametros","permissoes"]` (os códigos do próprio usuário; todos no modo aberto). Use para mostrar/ocultar telas.
- `GET /api/permissoes` → `{ "permissoes": [{codigo, descricao}], "usuarios": [{ id, nome, login, permissoes: string[] }], "modo_aberto": bool }` (usuários ordenados por nome).
- `PUT /api/permissao/usuario` corpo `{ "usuario_id", "codigo" }` → `{ usuario_id, codigo }` (idempotente). 404 `Permissão não encontrada!` / `Usuário não encontrado!`.
- `DELETE /api/permissao/usuario?usuario_id=&codigo=` → `{ usuario_id, codigo }`.
- Sem permissão: 403 `Acesso negado!`.

### 13.2 Parâmetros (tabela `parametros`: `nome`, `valor` texto)

| Nome | Significado | Exposto em `GET /parametros` | Alterável por `PATCH` | Origem |
|---|---|---|---|---|
| `versao` | versão das migrações aplicadas (hoje `34`) | não | não | `src/migracoes.ts` |
| `jwt_token` | segredo do JWT (gerado se vazio/padrão) | **nunca** | não | `015.sql`, `configuracao.ts:151-158` |
| `fcm_project_id` | Firebase project id | sim | sim | `015.sql` |
| `fcm_client_email` | e-mail da conta de serviço (precisa ter `@`) | sim | sim | `015.sql` |
| `fcm_private_key` | chave privada (precisa conter `PRIVATE KEY`) | só `fcm_private_key_configurada: bool` | sim | `015.sql` |
| `s3_bucket` | bucket do MinIO (padrão `chat`) | sim | não | `011.sql` |
| `turn_forcar_relay` | `'1'` = `iceTransportPolicy: relay` (padrão `'1'`) | como bool | sim (bool) | `023.sql` |
| `transcritor_url` | URL do transcritor-api (vazio = desligado; http/https) | sim | sim | `027.sql` |
| `transcritor_idioma` | idioma (`pt`, `en`, `pt-BR`...; regex `^[a-z]{2,3}(-[A-Za-z]{2})?$`) | sim | sim | `027.sql` |
| `gravacao_dias` | dias de retenção das gravações (padrão 90; 0 = sempre) | como número | sim (0..36500) | `030.sql` |

Removidos por migrações: `s3_endpoint`, `s3_accesskey`, `s3_secretkey`, `turn_url`, `turn_secret` (`024–026.sql`).

`GET /api/parametros` → `{ "fcm_project_id", "fcm_client_email", "fcm_private_key_configurada": bool, "turn_forcar_relay": bool, "transcritor_url", "transcritor_idioma", "gravacao_dias": number, "s3_bucket" }` (`src/parametros.ts:11-25`).
`PATCH /api/parametros` com qualquer subconjunto de `{ fcm_project_id?, fcm_client_email?, fcm_private_key?, turn_forcar_relay?: bool, transcritor_url?, transcritor_idioma?, gravacao_dias?: int }` → mesmo formato do GET. Aplica na hora, sem reiniciar (`src/parametros.ts:57-77`). Erros 400 com explicação (ver `src/parametros.ts:29-54`).

---

## 14. SIP

Código: `src/sip.ts`, `migracoes/016.sql`. Um ramal por usuário (índice único `ux_sip_usuario`).

Campos da tabela `sip`: `id`, `usuario_id`, `sip_user` varchar(100), `auth_user` varchar(100) nulo, `sip_password` texto, `display_name` varchar(100) nulo, `domain` varchar(150), `ws_server` varchar(255), `ativo` bool (padrão true), `criado_em`, `criado_por`.

- `GET /api/sip` → linha completa (inclui **`sip_password` em texto puro**) ou `{}` se não há ramal.
- `PUT /api/sip` corpo `{ "sip_user", "auth_user"?: str|null, "sip_password", "display_name"?: str|null, "domain", "ws_server", "ativo"?: bool }` → linha criada; `usuario_id` sempre do token (campo enviado é ignorado). Segundo PUT do mesmo usuário → **500** (unique).
- `PATCH /api/sip` corpo `{ "id", ...campos opcionais }` → linha alterada; só o dono (403).
- O servidor só guarda a configuração; o registro SIP (ex.: SIP sobre WebSocket em `ws_server`) é feito pelo cliente direto no PBX.

---

## 15. Presença, digitando, gravando

### 15.1 Online/offline

- Estado = **existe pelo menos uma conexão WebSocket autenticada** do usuário naquele processo da API (`src/websocket.ts:30-33`, `:130-132`). Não há "ausente", "ocupado", "visto por último" nem persistência (`dispositivo_usuario.online_em` não é usado).
- Avisos `{tipo:60, usuario_id, online}` vão **apenas a quem tem conversa direta (tipo 1)** com o usuário; enviados na primeira conexão e no fechamento da última (`src/websocket.ts:96-115`, `:223-237`).
- Estado inicial: `GET /api/contatos/online` (mesma regra de conversa direta).
- Consequência para o Android: enquanto o app mantiver o socket aberto (inclusive em segundo plano), o usuário aparece online e **não recebe push**. Fechar o socket ao ir para segundo plano faz aparecer offline e reativa o push.

### 15.2 Digitando / gravando áudio

- `POST /api/conversa/digitando` e `POST /api/conversa/gravando` com `{ "id": <conversa_id> }` → `{}`; membro (403). Repassam WS 4/5 `{tipo, conversa_id, usuario_id}` aos outros membros.
- Não há "parou de digitar": o receptor usa temporizador (o web expira o indicador por tempo) e limpa ao chegar mensagem nova. Enviar no máximo a cada poucos segundos enquanto digita (sugestão: a cada 3 s).

---

## 16. Modelo de dados (tabelas)

Resumo das tabelas do schema `public` (migrações 000–035):

| Tabela | Colunas principais | Observações |
|---|---|---|
| `usuario` | `id`, `nome`(100), `login`(50, único lower), `email`(100, único), `telefone`(50), `senha`(60 bcrypt), `avatar_anexo_id`→anexo, `criado_em`, `criado_por`, `atividades_vistas_em` | |
| `dispositivo` | §7.3 | |
| `dispositivo_usuario` | `id`, `dispositivo_id`, `usuario_id`, `online_em` | legado |
| `usuario_contato` | `id`, `usuario_id`, `relacionamento_id` | sem rota de listagem |
| `conversa` | `id`, `descricao`(100), `tipo`, `inserida`, `criado_por` | |
| `conversa_usuario` | `id`, `usuario_id`, `conversa_id`, `fixada_ordem`, `arquivada_em` | |
| `mensagem` | `id`, `usuario_id`, `conversa_id`, `inserida`, `alterada`, `visivel_em`(timestamptz), `excluida_em`, `excluida_por` | |
| `mensagem_conteudo` | `id`, `mensagem_id`, `ordem`, `tipo`, `conteudo`(bytea) | |
| `mensagem_referencia` | `id`, `tipo`, `origem_mensagem_id`(nova), `destino_mensagem_id`(referenciada) | |
| `mensagem_status` | PK(`conversa_id`,`usuario_id`,`mensagem_id`), `recebida`, `visualizada`, `reproduzida` | uma linha por destinatário |
| `reacao` | `id`, `mensagem_id`, `usuario_id`, `emoji`(10) | único (msg, usuário, emoji) |
| `anexo` | §8.1 | |
| `anexo_transcricao` | `identificador`(único), `status`, `texto`, `idioma`, `job_id`, `erro`(500), `atualizado_em` | |
| `chamada` | `id`, `tipo`, `status`, `iniciada`, `finalizada`, `conversa_id`, `criado_em`, `criado_por`, `conversa_chat_id` | |
| `chamada_usuario` | `id`, `chamada_id`, `usuario_id`, `status`, `adicionado_por`, `adicionado_em`, `entrou_em`, `saiu_em`, `recusou_em` | `recusou_em` nunca é gravado (vem do evento) |
| `chamada_evento` | `id`, `chamada_id`, `usuario_id`, `tipo`, `criado_em`, `criado_por` | |
| `atividade` | `id` bigserial, `usuario_id`, `tipo` int2, `autor_id`, `conversa_id`, `mensagem_id`, `chamada_id`, `emoji`, `criado_em` | |
| `permissao`, `permissao_usuario` | §13.1 | |
| `sip` | §14 | |
| `parametros` | `id`, `nome`(50, único), `valor`(5000) | |
| `enquete` 🆕 | `id`, `conversa_id`, `mensagem_id`, `pergunta`(300), `multipla`, `criado_em`, `criado_por` | `035.sql` |
| `enquete_opcao` 🆕 | `id`, `enquete_id`, `ordem`, `texto`(200) | |
| `enquete_voto` 🆕 | `id`, `enquete_id`, `opcao_id`, `usuario_id`, `criado_em`; único (`opcao_id`,`usuario_id`) | |
| `auditoria.alteracao`, `auditoria.exclusao` | log de alterações/exclusões via gatilhos | interno |

Todas as colunas `timestamp` (sem fuso) guardam UTC; `visivel_em` é `timestamptz`.

---

## 17. Tarefas periódicas do servidor

`src/tarefas.ts:97-104`:
| Tarefa | Intervalo | Faz |
|---|---|---|
| `AgendadorMensagens` | 60 s | notifica agendadas que amadureceram nos últimos 10 min (WS 2, push, WS 3, WS 61) |
| `VerificacaoAnexos` | 120 s | confirma uploads pendentes já no MinIO; marca falho após 15 min |
| `LimpezaGravacoes` | 3600 s | apaga gravações mais antigas que `gravacao_dias` |

Atraso máximo para uma agendada "sair": ~60 s após `visivel_em`.

---

## 18. O que o cliente precisa implementar (checklist)

1. **Login** (`/api/login`) guardando `token`, `id`, `dispositivo.id`; reenviar `dispositivo_id` nos próximos logins. Relogar ao receber 401 (token dura 12 h, sem refresh).
2. **Dispositivo/FCM**: `PATCH /api/dispositivo` com nome/modelo/versão/plataforma e `token_fcm`; atualizar no `onNewToken`; `token_fcm: null` ao sair da conta.
3. **Push de dados**: montar a notificação localmente com `data.titulo`, `data.mensagem`, `data.conversa` (string). Tratar `mensagem` vazia (áudio/arquivo/chamada). Ao tocar, abrir a conversa.
4. **WebSocket** em `wss://host/ws/` com login por mensagem, ping de protocolo, reconexão com backoff, ressincronização ao reconectar (§6.7) e polling de reserva.
5. **Sincronização de mensagens**: ao receber WS 2 (ou push), `GET /mensagens/novas?desde=<cursor>` → recarregar conversas e a conversa aberta; manter cursor `ate`.
6. **Status**: chamar `/mensagem/visualizar` por mensagem vista; `/mensagem/reproduzir` ao tocar áudio; processar WS 3 via `/mensagem/status`.
7. **Lista de conversas**: ordenar (fixadas por `fixada_ordem`, depois `ultima_mensagem` com nulos no fim), filtrar arquivadas, mostrar prévia (tratar `@[Nome](id)`, código, `""` para anexos).
8. **Mensagens**: render dos 8 tipos (o 8, enquete, é 🆕); resposta/encaminhamento com cadeia; reações; ocultas (`excluida_em`); menções; código; figurinhas Lottie embutidas; agendamento; pesquisa; galeria de anexos.
9. **Anexos**: SHA-256, `PUT /anexo` → PUT na URL → `POST /anexo/confirmar?identificador=`; download via `GET /anexo` com cache por identificador; miniaturas locais.
10. **Transcrição**: `PUT` + polling do `GET`.
11. **Chamadas**: tocar ao receber WS 51 (e consultar `/chamadas/pendentes` ao abrir o app); timeout de 30 s com `nao_atendeu:true`; WHIP/WHEP com TURN relay TCP/TLS e H264; reagir a 52/53/54/55/56/57; resumo tipo 6; histórico; chat da chamada; tela/ponteiro.
12. **Atividades**: lista paginada, contador, marcar vistas, WS 61.
13. **Presença**: `/contatos/online` + WS 60; digitando/gravando (HTTP + WS 4/5 com expiração local).
14. **Perfil**: alterar nome/e-mail/telefone/avatar, senha; exclusão de conta (409).
15. **Configurações administrativas** (opcional no Android): permissões e parâmetros conforme `/usuario/permissoes`.
16. **SIP** (se usado): ler `/sip` e registrar no PBX.
17. 🆕 **Enquetes**: criar em grupos (`PUT /enquete`), bolha tipo 8 lendo `GET /enquete?id=`, votar (`POST /enquete/votar`, a lista substitui o voto), reler ao receber WS 62; não permitir encaminhar enquete.

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
13. `mensagem_referencia` e `reacoes` **ausentes** (não `null`) quando não existem.
14. `conteudos[].nome/extensao/transcricao` vêm `""` (não `null`) em texto; `conteudo` nunca `null` na resposta (vira `""`).
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
30. **Push não é enviado se o usuário tiver qualquer WebSocket aberto** (ex.: aba do web aberta no PC) e nunca para conversa arquivada.
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
44. WebSocket: login sem confirmação; `tipo 9` fora do enum; token expirado não derruba a conexão; `/ws` sem barra final não funciona.
45. Certificado de desenvolvimento é mkcert — configurar confiança no build de debug.
46. Não existe: refresh de token, logout, edição de mensagem, listagem de agendadas, avatar de grupo, administradores de grupo, miniaturas, status "ausente"/"visto por último", evento de saída de membro, evento de fim de transcrição.
47. 🆕 Enquete (tipo 8) não pode ser enviada por `PUT /mensagem` nem encaminhada (400); a criação é só por `PUT /enquete`, e só em grupo.
48. 🆕 `POST /enquete/votar` **substitui** o voto (não é toggle de uma opção): mande sempre a lista completa das opções marcadas.

---

## 20. Histórico de atualizações deste documento

| Data | Commit do servidor | O que mudou no contrato |
|---|---|---|
| 2026-10-06 (manhã) | `7f670c3` | Versão inicial da auditoria |
| 2026-10-06 (noite) | `8031fa5` — "Votação em grupo: enquete com escolha única ou múltipla" | **3 rotas novas** (`PUT /enquete`, `GET /enquete`, `POST /enquete/votar`), **evento WS 62** `EnqueteAtualizada`, **conteúdo tipo 8** (só o servidor grava; não pode ser encaminhado), prévia/push `enquete`, migração `035.sql` (tabelas `enquete`, `enquete_opcao`, `enquete_voto`). Detalhes em §10.13 |
