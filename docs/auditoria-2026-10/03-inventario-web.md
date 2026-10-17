# 03 — Inventário de funcionalidades e comportamento do cliente web (conversa-web)

> Auditoria de 2026-10-06. Fonte: `C:\Users\danie\Desktop\GIT\conversa-projeto\conversa-web` no commit `bfb79d8` (2026-10-06, "Abas Sistema e Acessos nas configurações; Equipes sai da barra"), **atualizado com `39d06f9`** (2026-10-06 21:22, "Campo de mensagem rico, votação em grupo e chat completo na chamada"). Itens novos ou alterados por esse commit estão marcados com 🆕; o resumo está na §10. **Atualizado de novo em 2026-10-08** com `eaa8bac` e `785bdef` (2026-10-07 20:30 e 20:41): as marcações citam o commit (🆕 `eaa8bac`) e o resumo está na §11. Depois vieram `7322e83` e `e8d82cb` (§12, total 137). **Atualizado de novo em 2026-10-13** até o commit **`d202c06`** (2026-10-11 18:00, "Postagens em grupo, todos os emojis, figurinhas Noto e pesquisa nova"), com `769bd7d`, `05507a0`, `f798e9c`, `964ed72` e `d202c06` (servidor até `12cf0cf`): 13 IDs novos, total **150**; as marcações citam o commit (🆕 `769bd7d`) e o resumo está na §13. **Atualizado de novo em 2026-10-15** até o commit **`8b43965`** (2026-10-14 20:39, "Temas prontos, balões com cores próprias, seletor de emoji rápido e ajustes"), com `e33ecba` e `8b43965` (servidor até `e25c4db`, com `6d20ff7`): 3 IDs novos, total **153**; as marcações citam o commit (🆕 `e33ecba`) e o resumo está na §14. **Atualizado de novo em 2026-10-15 (noite)** com o commit **`9c6e8a6`** (2026-10-15 22:42, "Bots de IA com ferramentas e base de conhecimento, permissões de uso e cadastro pelo administrador"; servidor até `ef13c22`): 10 IDs novos, total **163**; as marcações citam o commit (🆕 `9c6e8a6`) e o resumo está na §15. **Atualizado de novo em 2026-10-16 (noite)** com o commit **`f351190`** (2026-10-16 22:49, "Pastas de mensagens, localização e trajeto, espaço usado e menu das Configurações"; servidor até `ac710eb`): 9 IDs novos, total **172**; as marcações citam o commit (🆕 `f351190`) e o resumo está na §16. Foram lidos `CLAUDE.md`, `README.md`, todo o `src/`, `chat-popup.html`, `public/firebase-messaging-sw.js`, `package.json`, `vite.config.ts`, os nomes dos testes em `tests/unit/` (589 casos) e o `git show --stat` de todos os commits desde 2026-09-12.
>
> Objetivo: servir de **checklist de paridade** para o app Android (`conversa-android-kotlin`). Cada funcionalidade tem um ID (ex.: `MSG-07`) para ser marcada como "feito / parcial / falta" na comparação com o Android.
>
> Caminhos de arquivo são relativos a `conversa-web/` salvo indicação. Rotas REST são relativas a `/api` (o cliente Eden monta `https://<host>/api/<rota>`).

---

## Sumário

1. [Arquitetura do cliente web](#1-arquitetura-do-cliente-web)
2. [Mapa de telas, rotas, painéis e navegação](#2-mapa-de-telas-rotas-painéis-e-navegação)
3. [Lista de funcionalidades (checklist de paridade)](#3-lista-de-funcionalidades-checklist-de-paridade)
4. [Todos os endpoints REST e eventos WebSocket](#4-todos-os-endpoints-rest-e-eventos-websocket)
5. [WebRTC / MediaMTX / WHIP / WHEP](#5-webrtc--mediamtx--whip--whep)
6. [Regras de UX e textos exatos para replicar](#6-regras-de-ux-e-textos-exatos-para-replicar)
7. [Itens que não fazem sentido no Android ou precisam de adaptação](#7-itens-que-não-fazem-sentido-no-android-ou-precisam-de-adaptação)
8. [Anexo A — Commits desde 2026-09-12 e o que tocaram](#8-anexo-a--commits-desde-2026-09-12-e-o-que-tocaram)
9. [Anexo B — Código morto / legado encontrado](#9-anexo-b--código-morto--legado-encontrado)
10. [🆕 Atualização de 2026-10-06 (noite) — commit `39d06f9`](#10-atualização-de-2026-10-06-noite--commit-39d06f9)
11. [🆕 Atualização de 2026-10-08 — commits `eaa8bac` e `785bdef`](#11-atualização-de-2026-10-08--commits-eaa8bac-e-785bdef)
12. [🆕 Atualização de 2026-10-08 (noite) — commits `7322e83` e `e8d82cb`](#12-atualização-de-2026-10-08-noite--commits-7322e83-e-e8d82cb)
13. [🆕 Atualização de 2026-10-13 — commits `769bd7d` a `d202c06`](#13-atualização-de-2026-10-13--commits-769bd7d-a-d202c06)
14. [🆕 Atualização de 2026-10-15 — commits `e33ecba` e `8b43965`](#14-atualização-de-2026-10-15--commits-e33ecba-e-8b43965)
15. [🆕 Atualização de 2026-10-15 (noite) — commit `9c6e8a6`](#15-atualização-de-2026-10-15-noite--commit-9c6e8a6)
16. [🆕 Atualização de 2026-10-16 (noite) — commit `f351190`](#16-atualização-de-2026-10-16-noite--commit-f351190)

---

## 1. Arquitetura do cliente web

### 1.1 Stack

| Camada | Tecnologia | Onde |
|---|---|---|
| Framework | Vue 3 `<script setup lang="ts">` | `src/**/*.vue` |
| Estado | Pinia 3 (Composition API) | `src/stores/{auth,chat,call,sip,atividades}.ts` |
| Estilo | Tailwind 3 + CSS variables, tema pela classe `dark` no `<html>` | `src/style.css`, `tailwind.config.js` |
| Runtime/build | Bun ≥ 1.4, Vite 7, vue-tsc (via `scripts/vue-tsc.ts`) | `package.json`, `vite.config.ts` |
| HTTP | Eden (`@elysiajs/eden` 1.4.9), tipado pelas rotas do backend (`../conversa/src/app.ts`) | `src/services/eden.ts`, `src/services/conversaApi.ts` |
| Tempo real | WebSocket nativo, reconexão com backoff, polling de 8 s como fallback | `src/stores/chat.ts` |
| Push | Firebase Cloud Messaging (web, VAPID) + service worker | `src/services/firebase.ts`, `public/firebase-messaging-sw.js` |
| Upload | MinIO por URL pré-assinada, `XMLHttpRequest` (progresso) | `conversaApi.ts#uploadMinio` |
| Hash | `hash-wasm` SHA-256 em blocos de 2 MB (deduplicação) | `conversaApi.ts#sha256File` |
| Código | highlight.js (sob demanda) + CodeMirror 6 (janela de código) | `composables/useCodeHighlight.ts`, `components/CodigoModal.vue` |
| Markdown / diagramas | `marked` + `DOMPurify`, `mermaid` (securityLevel strict) | `composables/useMarkdown.ts`, `composables/useMermaid.ts` |
| PDF | `pdfjs-dist` 6 | `components/VisualizadorPdf.vue` |
| 🆕 `9c6e8a6` Arquivos do Office | `@silurus/ooxml` 0.88.0 (leitor em Rust/WASM, desenho em canvas); fontes substitutas em `public/fontes/office` (sem Google Fonts) | `components/VisualizadorOffice.vue`, `utils/fontesOffice.ts` |
| 🆕 `f351190` Mapas | Leaflet 1.9.4 (chunk `leaflet`, só carrega quando aparece uma localização); ruas do OpenStreetMap e satélite da Esri; busca de endereço no Nominatim e rota no OSRM, chamados direto do navegador (ENV-30, MSG-25) | `components/MapaLeaflet.vue`, `services/mapas.ts`, `utils/localizacao.ts` |
| Figurinhas | `lottie-web` (build `lottie_light`, só SVG) | `utils/figurinhas.ts`, `components/FigurinhaLottie.vue` |
| VoIP | `sip.js` 0.21 | `src/stores/sip.ts` |
| Chamadas | WebRTC nativo contra MediaMTX (WHIP publica / WHEP assina) | `src/stores/call.ts` |

Duas entradas HTML (Vite multi-page): `index.html` → `src/main.ts` → `App.vue`; `chat-popup.html` → `src/chat-popup.ts` → `ChatPopup.vue` (janela separada de uma conversa).

Topologia (nginx do backend, mesmo host em dev e prod — `README.md`):

```
https://HOST
 ├── /          → página (Vite em dev, bin/web em prod)
 ├── /api/      → API REST
 ├── /ws/       → WebSocket da API
 ├── /storage/  → anexos (MinIO)
 └── /webrtc/   → MediaMTX (WHIP/WHEP)
```

### 1.2 Estado

| Store | Responsabilidade | Persistência |
|---|---|---|
| `auth` (`stores/auth.ts`) | token, usuário, `dispositivoId`, URL do avatar resolvida, permissões do sistema (`permissoes`, `temPermissao`); 🆕 `9c6e8a6` `pode(codigo)` para as permissões de uso (verdadeiro até a lista chegar, GER-09) | `localStorage`: `conversa.token`, `conversa.user` (JSON), `conversa.deviceId`, `conversa.apiBase` |
| `chat` (`stores/chat.ts`) | contatos, conversas, mensagens por conversa, conversa ativa, resposta pendente, digitando/gravando, online, WebSocket, polling, cursor de sincronização, buscas; 🆕 `769bd7d` presença (`presencas`: estado e visto por último; `presentesPorConversa`), confirmação pendente (`pedirConfirmacao`); 🆕 `d202c06` postagem pendente (`postagemPendente`) e respostas por postagem; 🆕 `e33ecba` quem precisa confirmar (`confirmacaoUsuarios`, `null` = todos) e a postagem que está sendo respondida pelo campo (`postagemRespondendo`) | memória (não persiste mensagens) |
| `call` (`stores/call.ts`) | estado da chamada (`inativo`/`chamando`/`recebendo`/`ativa`/`encerrando`), peers WHIP/WHEP, mídia local, tela, ponteiros, chat da chamada | memória |
| `sip` (`stores/sip.ts`) | config do ramal, UserAgent/Registerer sip.js, sessão, tons | memória |
| `atividades` (`stores/atividades.ts`) | lista paginada, contador de novas, tela aberta; 🆕 `9c6e8a6` o contador (`novas`) passa a contar as **pendentes**, `marcarVistas` saiu e entrou `concluir(id)` (ATV-02, ATV-04) | memória |
| 🆕 `f351190` `pastas` (`stores/pastas.ts`) | pastas da conversa direta aberta (`lista`, `porId`), painel aberto, mensagens selecionadas no chat, arraste em andamento, pasta aberta e as mensagens dela, com a paginação (CON-17, ENV-29, MSG-24) | memória |

Estados globais fora do Pinia (composables singleton): tema (`useTheme`, chave `theme` = `dark`/`light`), cores personalizadas (`useCoresPersonalizadas`, chave própria no localStorage, por tema; 🆕 `8b43965` por cima do tema pronto escolhido, chave `conversa.temaCores`, CFG-10), qualidade das chamadas (`useConfigChamada`, chave `conversa.chamada`), uploads ativos (`useUploadProgress`), áudio tocando (`useAudioManager`), relógio compartilhado (`useAgora`), conexão lenta (`useConexao`). 🆕 `769bd7d`…`d202c06`: recursos do servidor (`useRecursos`, GER-08), sugestão da IA (`useSugestaoIa`, chave `conversa.sugestoesIa`), catálogo e recentes de emojis (`useEmojis`, chave `conversa.emojisRecentes`), aba própria da chamada (`useAbaChamada`). 🆕 `9c6e8a6`: `useRecursos` ganha `conhecimento`; barra de navegação oculta no computador (chave `conversa.menuOculto`, GER-10). 🆕 `f351190`: velocidade dos áudios, a mesma para todos (`useAudioPlayer`, chave `conversa.velocidadeAudio`, ANX-18). IndexedDB: rascunhos (ENV-23) e o último resumo de cada conversa (banco `conversa-resumos`, CON-14).

### 1.3 Como fala com a API (Eden)

- `api()` (`services/eden.ts`) devolve o cliente das rotas `/api` no endereço `getApiBase()` = `localStorage['conversa.apiBase']` ou `window.location.origin`. O cliente é recriado se a base muda. (Não há mais campo na tela de login para trocar a base, só `auth.setApiBase`, sem uso na UI.)
- Header `authorization: Bearer <token>` lido do `localStorage` a cada requisição.
- `dados(chamada)` devolve `data` ou lança:
  - **401** → `limparToken()` (remove `conversa.token` do localStorage) e lança `ErroNaoAutenticado`.
  - Corpo HTML (página de erro do nginx 502/504) → mensagem `"Servidor indisponível"`.
  - JSON `{ error }` ou `{ message }` → a string; senão `Erro HTTP <status>`.
- **Datas**: o JSON é lido com reviver que só converte para `Date` as chaves em `CAMPOS_DATA` (`inserida, alterada, visivel_em, encerra_em, encerrada_em, confirmada_em, visto_em, criado_em, atualizado_em, ultima_mensagem, arquivada_em, iniciada, finalizada, adicionado_em, entrou_em, saiu_em, recusou_em, recebida, visualizada, reproduzida, excluida_em, reagido_em, online_em, ate, vistas_em`; 🆕 `encerra_em`/`encerrada_em` vêm da votação de `785bdef` e `confirmada_em`/`visto_em` de `769bd7d`) e só se o valor for ISO-8601 com fuso. Texto de mensagem nunca vira data. **Atenção Android**: `recebida`/`visualizada`/`reproduzida` são booleanos na lista de mensagens, mas datas (ou null) no detalhe de status — o mesmo nome de campo tem tipos diferentes por rota.
- Verbos: `GET` consulta, `PUT` cria, `PATCH` altera, `POST` ações, `DELETE` remove.

### 1.4 WebSocket

Arquivo: `src/stores/chat.ts` (`conectarWebSocket`, `tratarEventoSocket`).

- URL: `${wss|ws}://${window.location.host}${VITE_WS_PATH || '/ws/'}` — **usa o host da página, não `conversa.apiBase`**. No Android: `wss://<host-da-api>/ws/`.
- `onopen`:
  1. `conectadoTempoReal = true`, zera tentativas.
  2. Envia `{"tipo":1,"token":"<token>"}` (autenticação).
  3. Recarrega quem está online (`GET /contatos/online`) — quem saiu durante a queda não fica "online". 🆕 `769bd7d`: agora `GET /contatos/presenca` (PRE-01); em seguida envia a própria presença (WS 63, PRE-02) e, com uma conversa aberta, busca quem está nela (`GET /conversa/presentes`, PRE-03).
  4. Atualiza o contador de atividades (`GET /atividades/novas`).
  5. Se o handler de chamada já está registrado, verifica chamadas pendentes (`GET /chamadas/pendentes`).
  6. 🆕 `f351190` Na **reconexão** (não na primeira conexão, em que a inicialização acabou de carregá-los), relê os contatos (`GET /usuario/contatos`): uma conta criada ou renomeada com o socket fora não gerou o aviso 66 (CON-11).
- `onclose` → agenda reconexão com espera `min(30000, 1000 * 2^tentativas)` ms (1 s, 2 s, 4 s, … até 30 s).
- **Polling de fallback**: a cada 8 s, **só se o socket estiver desconectado**, executa o mesmo fluxo de "nova mensagem" (`GET /mensagens/novas`) e verifica chamadas pendentes.
- **Aviso visual**: se o socket fica desconectado por mais de 5 s com o usuário logado, aparece a faixa vermelha no topo: `Conexao em tempo real indisponivel — usando atualizacao periodica` (`App.vue`).
- Envio: `{tipo:1, token}` (login) e `{tipo:57, chamada_id, dados}` (sinal de chamada; descartado se o socket não estiver `OPEN`); 🆕 `769bd7d` `{tipo:63, ativo, conversa_id}` (presença, PRE-02).
- 🆕 `769bd7d` `onclose` também apaga o **próprio** estado de presença (sem conexão ele fica desconhecido: o avatar da barra mostra offline).
- Recebimento: ver tabela completa em §4.2.

**Sincronização incremental (evento 2):** o evento `NovaMensagem` (tipo 2) não traz a mensagem. O cliente chama `GET /mensagens/novas?desde=<cursorSync ISO>` (devolve `[{conversa_id, mensagem_id, ate}]`), avança o cursor para o maior `ate` (e para `coalesce(visivel_em, inserida)` de cada mensagem recebida, **nunca para o futuro** — agendadas futuras do autor não avançam o cursor), busca `GET /mensagens?conversa=X&mensagemreferencia=0&mensagensprevias=10` de cada conversa afetada para ter o conteúdo (notificação), recarrega `GET /conversas` e, se a conversa ativa foi afetada, recarrega as 80 últimas mensagens dela. Também limpa o "digitando" das conversas que receberam mensagem. O cursor começa em `new Date()` no carregamento da página (não é persistido).

### 1.5 Sessão, token expirado e "login volta sem F5 depois que o servidor reinicia"

Arquivos: `App.vue#iniciarSessao`, `services/eden.ts`, `stores/auth.ts` (commit `c32209a`).

- `iniciarSessao()` roda ao montar o app já logado **e** quando `auth.isAuthenticated` vira `true` (login pelo formulário). Sequência: `chat.inicializar()` (contatos + conversas em paralelo, WebSocket, polling, pede permissão de notificação) → `sip.inicializarSessao(false)` → resolve URL do avatar → carrega permissões (`GET /usuario/permissoes`, falha silenciosa) → registra o handler de eventos de chamada → `call.verificarChamadasPendentes()` → aplica deep link da URL.
- **Erro `ErroNaoAutenticado` (401)** → `sair()` (logout local, sem mensagem de erro).
- **Qualquer outro erro** (API ainda subindo após reiniciar, 502 do nginx) → **mantém o login**, mostra o toast `"<mensagem> (tentando de novo...)"` e tenta de novo a cada **5 s**. Quando dá certo, o toast some (se ainda for o mesmo).
- Logout (`sair()`): cancela retentativa, para toque/notificação de chamada, encerra chamada, remove handler, fecha WS + polling, limpa conversa ativa, limpa atividades, encerra SIP, `auth.logout()` (remove `conversa.token` e `conversa.user`; **mantém** `conversa.deviceId`, que é reenviado no próximo login para reaproveitar o dispositivo). **Não há chamada de logout na API.**
- Lacuna conhecida do web: um 401 em qualquer chamada **depois** do início só apaga o token do localStorage; o `auth.token` em memória continua e a tela não volta ao login até o próximo `iniciarSessao` (F5 ou falha seguinte). No Android, recomenda-se tratar 401 global → logout imediato.

### 1.6 FCM web e notificações

- `services/firebase.ts`: projeto `conversa-23858`, `getToken(messaging, { vapidKey })` após `Notification.requestPermission()`; sem permissão → `null`.
- Após login: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma:'Web'}` (detecção pelo user agent — iPhone/iPad antes de macOS) e depois `PATCH /dispositivo {id, token_fcm}`.
- **Payload do push** (o servidor manda só `data`): `{ titulo, mensagem, conversa }`. O service worker (`public/firebase-messaging-sw.js`) mostra `showNotification(titulo, {body: mensagem, icon:'/logo.png', silent:true, tag:'conversa-<id>', data:{conversa}})`.
- Clique na notificação (SW): procura a janela principal (exclui `/chat-popup`), prioriza a focada, depois a visível; foca e envia `postMessage({tipo:'conversa-abrir', conversaId})`; o `App.vue` abre a conversa. Sem janela aberta → `clients.openWindow('/chat/<id>')` (ou `/`).
- Com o app aberto, as notificações de mensagem e de chamada são geradas pelo próprio cliente (ver `NOT-*` em §3). `ouvirMensagensForeground` existe em `firebase.ts` mas **não é usado**.

### 1.7 Upload de anexos (deduplicação)

`conversaApi.ts#uploadAnexo(tipo, nome, extensao, blob, onProgress)`:
1. `identificador = sha256(blob)` (hex, blocos de 2 MB).
2. `GET /anexo/existe?identificador=` → se a resposta tem `id`, o arquivo já existe: progresso 100%, devolve `{id, identificador}` sem subir nada.
3. `PUT /anexo {identificador, tipo, nome, extensao, tamanho}` → `{id, upload_url}`. 🆕 `9c6e8a6` (servidor `ef13c22`) Áudio (tipos 4 e 5) leva também **`duracao`** (segundos, até 86400) e **`intensidade`** (48 picos de 0 a 100, até 200 itens), medidos no navegador antes do envio (`utils/dadosAudio.ts#medirAudio`, que decodifica o arquivo; acima de 30 MB, ou se a decodificação falha, vai sem). O servidor grava na `anexo_audio` (migração 045, uma linha por identificador; o mesmo áudio enviado de novo só completa o que faltava). Ver ANX-10.
4. `PUT upload_url` (XHR, corpo = arquivo) com progresso.
5. A mensagem leva no conteúdo o **identificador** (não o id).

Para exibir: `GET /anexo?identificador=` → `{url}` (pré-assinada; validade lida de `X-Amz-Date` + `X-Amz-Expires`; renovada 60 s antes de vencer; URL sem assinatura vale até falhar; erro de carga força renovação, no máximo 1 vez por minuto por anexo) — `composables/useAttachments.ts`.

### 1.8 Carregamento sob demanda

Chunks manuais no Vite: `sipjs`, `framework`, `highlightjs`, `markdown`, `lottie`, `vendor-utils` (hash-wasm), `codemirror`. Componentes assíncronos: `SipDialerModal`, `SipIncomingCallModal`, `CodigoModal`, `AgendarMensagemModal`, `VisualizadorPdf`, `VisualizadorHtml`; 🆕 `9c6e8a6` `VisualizadorOffice` (um chunk por formato, cada um com o seu `.wasm`, sem CDN); 🆕 `f351190` chunk `leaflet`, com `LocalizacaoModal` e `ConteudoLocalizacao` assíncronos (o mapa só carrega quando aparece uma localização). Irrelevante para o Android, mas indica o que é "pesado" e opcional.

---

## 2. Mapa de telas, rotas, painéis e navegação

### 2.1 Rotas (URL) — `composables/useHistoryNavigation.ts`

Não há vue-router. A URL é sincronizada manualmente com `history.pushState/replaceState` e o `popstate` restaura o estado.

| URL | Seção | Observação |
|---|---|---|
| `/` ou `/chat` | Chat sem conversa aberta | painel lateral aberto |
| `/chat/:conversaId` | Chat com conversa aberta | deep link aplicado após `iniciarSessao` |
| `/anexos`, `/anexos/:conversaId` | Página de anexos | |
| `/config/:aba` | Configurações | `aba` ∈ `usuario, privacidade, dispositivos, permissoes, voip, cores, chamadas, sistema, acessos` (inválida → `usuario`; 🆕 `769bd7d` `privacidade`; 🆕 `9c6e8a6` `bots, usuarios, ferramentas, conhecimento`; 🆕 `f351190` `ia` — IA › Servidor —, e `acessos` passa a levar a `usuarios`, CFG-01) |
| `/chamadas` | Histórico de chamadas | |
| `/atividades` | Atividades | |
| `/ramal` | (válida no parser; o item da barra abre o discador em vez de navegar) | |
| `/chat-popup.html?conversa=:id` | Janela separada de uma conversa | entrada própria |
| 🆕 `964ed72` `/chamada.html` | Aba própria da chamada de vídeo (computador); 🆕 `e33ecba` agora numa **janela popup** | título "Chamada · Conversa"; desenhada pela aba do app (CHA-13); aberta sozinha mostra "A chamada acontece na aba do Conversa que a abriu. Volte para ela." |

- O `history.state` guarda também a **âncora de rolagem** `{mensagemId, offset}` (atualizada por `replaceState` 200 ms após parar de rolar). Voltar/avançar restaura seção, conversa, aba e posição. Em **reload** a âncora é descartada (abre no fim ou na primeira não lida).
- Mudar só a âncora usa `replaceState`; mudar seção/conversa/aba usa `pushState`.

### 2.2 Barra de navegação (`components/NavBar.vue`)

Desktop: barra vertical à esquerda. Celular (< 768 px): barra horizontal no rodapé (50 px).

| Ordem | Item | Ação | Indicadores |
|---|---|---|---|
| 1 | Avatar do usuário (ou inicial) | seção `config` | tooltip "Configurações"; se a imagem falha, re-resolve a URL; 🆕 `769bd7d` bolinha do **próprio** status (verde/amarela/cinza), tooltip "Para os outros você aparece ativo/ausente/offline" (PRE-02) |
| 2 | Chat | seção `chat` | — |
| 3 | Atividades | seção `atividades` | badge com `atividades.novas` (`99+` acima de 99); 🆕 `9c6e8a6` conta as **pendentes** (ATV-02) |
| 4 | Chamadas | seção `chamadas` | — |
| 5 | Anexos | seção `anexos` | — |
| 6 | Ramal | **abre o discador SIP** (não muda de seção) | só aparece se `sip.sipDisponivel` (config ativa); bolinha verde = registrado, amarela = conectando, vermelha = erro; 🆕 `9c6e8a6` sem a permissão `telefonia` o `GET /sip` volta `{}` e o item some (GER-09) |

"Equipes" foi **removido** da barra em `bfb79d8`. Qualquer outra seção cai na tela "Em desenvolvimento".

🆕 `9c6e8a6` **Barra oculta (só computador, GER-10):** no pé da barra vertical, o botão "Ocultar menu" a esconde; oculta, fica uma aba fina no pé da borda esquerda, "Mostrar menu", que a traz de volta (com um ponto vermelho se há atividades pendentes). A escolha fica no navegador (`conversa.menuOculto`). A barra do rodapé do celular não muda.

### 2.3 Layout da seção Chat (`App.vue`)

```
[NavBar] [ChatSidebar (lista)] [main: CallBar? · ChatHeader · UploadIndicador · MessageList · MessageInput]
```

- No celular só uma coluna aparece: lista **ou** conversa (`sidebarAberta`); botão de voltar no cabeçalho reabre a lista; tela vazia mostra "Selecione uma conversa." e (celular) botão "Ver conversas".
- Chamada de **vídeo** ativa e não minimizada cobre o app inteiro (`CallWindow` em `fixed inset-0`). Minimizada no desktop → janela flutuante arrastável/redimensionável; no celular → só a `CallBar` no topo, app utilizável.
- Chamada de **áudio** → só a `CallBar` (com controles) no topo da área do chat.
- 🆕 `964ed72` **Computador:** a chamada de vídeo abre numa **aba própria** (`/chamada.html`; 🆕 `e33ecba` uma **janela popup**, sem a barra do navegador, CHA-13) e o app fica só com a `CallBar` ("Abrir chamada" traz a aba de volta); se o navegador bloquear a aba, fica dentro do app como acima. **Celular** (tela < 768 px ou só toque): a chamada fica dentro do app; em tela estreita, fora da tela dela, a `CallBar` vai para o **topo do app, em qualquer seção** (CHA-13).
- 🆕 `f351190` **Conversa direta:** à direita do chat, o painel estreito **"Pastas"** (`PainelPastas`, 208 px; em tela com menos de 1024 px, por cima, fixo à direita), aberto pelo botão "Pastas de mensagens" do cabeçalho (CON-17). A **pasta aberta** (`ChatDaPasta`) cobre a área das mensagens e o campo de mensagem fica escondido, com o rascunho guardado (MSG-24).

### 2.4 Painéis, modais e sobreposições

| Componente | Abre por | Conteúdo |
|---|---|---|
| `PesquisaAvancada` | lupa "Pesquisar em todos os chats" na lista | substitui a lista; pesquisa global |
| Menu da conversa (em `ChatSidebar`) | clique direito na conversa ou botão "Mais opções" | Fixar / Desafixar / Arquivar / Desarquivar |
| `UserInfoModal` | avatar na lista ou no cabeçalho (só conversa direta) | foto, nome, Email, Telefone ("Nao informado"), botão "Ver anexos"; 🆕 `f351190` "Usuário desde" (AUT-11) |
| `CreateGroupModal` | botão "Novo grupo" na lista | nome + seleção de contatos; 🆕 `e33ecba` imagem ou emoji do grupo no topo (CON-07) |
| `GroupMembersModal` | botão "Gerenciar membros" no cabeçalho (grupo) | renomear, remover, adicionar |
| `ForwardMessageModal` | ação Encaminhar | destinos (conversas e contatos) |
| `DetalheStatusMensagem` | hover 300 ms / clique no ✓ da própria mensagem | etapas de entrega |
| `MensagemAcoes` (menu + `EmojiPicker`) | botão ⋯ ao passar o mouse ou clique direito na bolha | reações rápidas + ações |
| `DialogoConfirmacao` | Ocultar / cancelar agendada / erros de download | diálogo do app |
| `AnexoPopup` | botão "Anexar" | Arquivo / Código (hoje: Arquivo, Código, 🆕 `f351190` **Localização**, Votação e Postagem em grupo, Confirmar leitura) |
| 🆕 `f351190` `LocalizacaoModal` | "+" › "Localização", ou "Editar localização" na peça do campo | "Enviar localização" / "Editar localização": Local ou Trajeto, busca, posição, mapa; "Inserir na mensagem" ou "Enviar" (ENV-30) |
| 🆕 `f351190` `VisualizadorLocalizacao` | clique no mapa da bolha | mapa grande com arrastar, zoom e satélite; "Abrir no Google Maps" (MSG-25) |
| 🆕 `f351190` `PainelPastas` | botão "Pastas de mensagens" do cabeçalho (direta), ou ao selecionar mensagens | pastas da conversa: criar, abrir, opções, soltar mensagens (CON-17) |
| 🆕 `f351190` `ChatDaPasta` | clique numa pasta do painel ou na marca da bolha recebida | só as mensagens da pasta, sem campo; "Voltar ao chat" (MSG-24) |
| `EmojiPicker` (com abas Emojis/Figurinhas) | botão "Emoji" | |
| `CodigoModal` | Anexar → Código, ou colar texto > 10 linhas; 🆕 `e33ecba` também "Editar código" na peça do campo | editor CodeMirror; 🆕 `e33ecba` "Inserir na mensagem" (vira a peça `codigo`, ENV-28) ou "Enviar"; editando, só "Salvar" (ENV-10) |
| `AgendarMensagemModal` | botão relógio "Agendar mensagem" (aparece com conteúdo) | data + hora |
| `MencaoDropdown` | digitar `@` | até 6 contatos |
| `BarraGravacao` | microfone | gravação de áudio |
| `ImageViewerModal` | clique em imagem/vídeo (chat, fila, anexos) | visualizador com galeria; 🆕 `9c6e8a6` o vídeo toca no `PlayerVideo` completo (ANX-04) |
| 🆕 `9c6e8a6` `PlayerVideo` | vídeo na bolha e no visualizador | player próprio: play/pausa, barra, tempo, baixar, tela cheia; no visualizador também volume até 200% e velocidade (ANX-05, ANX-04) |
| `VisualizadorPdf`, `VisualizadorHtml` | botão "Abrir" em anexo PDF/HTML | 🆕 `8b43965` o de HTML tem "Ver fonte" / "Ver página" (ANX-07) |
| 🆕 `9c6e8a6` `VisualizadorOffice` | botão "Abrir" em `.docx`, `.xlsx`, `.pptx` (chat) ou clique na página de Anexos | documento desenhado em canvas, com páginas/slides e zoom (ANX-16) |
| `IncomingCallModal` | evento 51 | atender / recusar / atender só assistindo |
| `VideoUpgradeModal` | evento 56 | Apenas assistir / Transmitir também |
| `AddUserToCallModal` (App) e modal interno do `CallWindow` | botão "Adicionar usuário" | contatos fora da chamada |
| `ChatChamada` | botão "Chat da chamada" no `CallWindow` | painel lateral (celular: por cima) |
| 🆕 `769bd7d` `ResumoConversaModal` | botão "Resumir conversa" no cabeçalho (só com IA) | "Resumo da conversa" por período e assunto (CON-14) |
| 🆕 `964ed72` `ChatDaChamada` | "Ver chat da chamada" na bolha da ligação ou no histórico | chat da ligação só leitura, "Abrir o chat completo" (CHA-26); 🆕 `e33ecba` com os conteúdos como no chat |
| 🆕 `d202c06` `BolhaPostagem` | postagem de grupo no chat | respostas embaixo e campo "Responder à postagem" (MSG-22); 🆕 `e33ecba` o campo saiu: o botão "Responder à postagem" leva a resposta ao campo de mensagem; cartão na largura do chat |
| 🆕 `e33ecba` `ConfirmacaoLeituraModal` | em grupo: "+" › "Confirmar leitura" (e "Escolher" na faixa), ou "Pedir confirmação de leitura" no menu da mensagem | "Quem precisa confirmar": "Filtrar por nome", "Todos (N)", os participantes (menos eu) com caixa; "Cancelar" / "Pronto" (antes do envio) ou "Pedir confirmação" (depois) — ENV-24, ENV-27 |
| `SipDialerModal` | item Ramal da barra, ou chamada SIP recebida sendo conectada | discador flutuante arrastável |
| `SipIncomingCallModal` | INVITE SIP | atender / recusar |
| Overlay de arrastar arquivo | arrastar arquivos sobre a conversa | "Solte para enviar o arquivo" |
| Toast de erro | qualquer erro tratado no `App.vue` | topo, botão × |

### 2.5 Configurações (`components/ProfileSettingsModal.vue`, modo `inline`)

Título "Configuracoes" / "Preferencias da conta". Lista de abas à esquerda (celular: menu → sub-tela com botão voltar). 🆕 `f351190`: a lista virou **menus com sub-abas** (tabela nova no fim desta seção); a tabela abaixo é a de até `9c6e8a6`, com os ids que continuam valendo.

| Aba (id) | Título | Descrição exibida | Visibilidade |
|---|---|---|---|
| `usuario` | Usuario | Nome, email, avatar e senha | todos |
| 🆕 `769bd7d` `privacidade` | Privacidade | Status, visto por último e aparecer offline | todos (CFG-09) |
| `dispositivos` | Dispositivos | Sessao atual e perifericos locais | todos |
| `permissoes` | Permissoes | Notificacoes, microfone e camera | todos |
| `voip` | Voip | Configuracao SIP do usuario | todos; 🆕 `9c6e8a6` só com a permissão de uso `telefonia` |
| `cores` | Cores | Cores do sistema, salvas neste navegador | todos; 🆕 `9c6e8a6` só com a permissão de uso `cores` |
| `chamadas` | Chamadas | Qualidade do áudio, vídeo e tela | todos; 🆕 `9c6e8a6` só com a permissão de uso `config_chamadas` |
| `sistema` | Sistema | Configurações que valem para todos | só com permissão `parametros` |
| `acessos` | Acessos | Quem pode mexer no sistema e nas permissões | só com permissão `permissoes` |
| 🆕 `9c6e8a6` `usuarios` | Usuários | Cadastrar pessoas e bots | só com permissão `usuarios` (CFG-11) |
| 🆕 `9c6e8a6` `ferramentas` | Ferramentas | APIs externas que os bots podem chamar | só com permissão `ferramentas` (CFG-13) |
| 🆕 `9c6e8a6` `conhecimento` | Conhecimento | Documentos que os bots consultam | só com permissão `conhecimento` (CFG-14) |
| 🆕 `9c6e8a6` `bots` | Bots | Bots de IA: instruções e o que cada um pode fazer | com permissão `bots` **ou** `permissoes` (CFG-12) |

🆕 `9c6e8a6` A ordem é: as abas de todos (filtradas pelas permissões de uso), depois Sistema, Acessos, Usuários, Ferramentas, Conhecimento e Bots. As de uso (`telefonia`, `cores`, `config_chamadas`) usam `auth.pode` (aparecem até a lista de permissões chegar); as de administração usam `auth.temPermissao`.

Regra: link direto para aba restrita sem a permissão volta para `usuario` **depois** de as permissões carregarem. Fechar as configurações volta ao chat com a lista aberta e força reconexão do SIP (`sip.inicializarSessao(true)`).

🆕 `f351190` **Menus com sub-abas** (`ProfileSettingsModal.vue`, lista `GRUPOS`; CFG-01). A lista da esquerda tem **menus**, em duas seções; cada menu tem as suas **sub-abas**, numa barra no topo do conteúdo. O id de cada sub-aba é o da aba antiga (o endereço continua `/config/<id>`).

| Seção | Menu (descrição na lista) | Sub-abas: id — título · descrição | Visível com |
|---|---|---|---|
| Pessoal | **Conta** ("Perfil, senha e privacidade") | `usuario` — **Perfil** · "Nome, email, avatar e senha"; `privacidade` — **Privacidade** · "Status, visto por último e aparecer offline" | todos |
| Pessoal | **Aparência** ("Tema claro ou escuro e cores") | `cores` — **Tema** · "Tema claro ou escuro e cores do sistema, salvos neste navegador" | uso `cores` |
| Pessoal | **Dispositivos** ("Sessão, periféricos e permissões do navegador") | `dispositivos` — **Sessão e periféricos** · "Sessao atual e perifericos locais"; `permissoes` — **Permissões do navegador** · "Notificacoes, microfone e camera" | todos |
| Pessoal | **Chamadas** ("Qualidade das chamadas e telefonia") | `chamadas` — **Qualidade** · "Qualidade do áudio, vídeo e tela" (uso `config_chamadas`); `voip` — **Voip** · "Configuracao SIP do usuario" (uso `telefonia`) | cada sub-aba com a sua |
| Administração | **Sistema** ("Configurações que valem para todos") | `sistema` — **Sistema** · "Configurações que valem para todos" (a parte geral, CFG-07) | `parametros` |
| Administração | **Pessoas** ("Usuários e o que cada um pode fazer") | `usuarios` — **Usuários** · "Cadastrar pessoas e bots e o que cada um pode fazer" (CFG-11, com o CFG-08 dentro) | `permissoes` |
| Administração | **IA** ("Bots, conhecimento, ferramentas e servidor") | `bots` — **Bots** · "Bots de IA: instruções e o que cada um pode fazer" (`bots` **ou** `permissoes`); `conhecimento` — **Conhecimento** · "Documentos que os bots consultam" (`conhecimento`); `ferramentas` — **Ferramentas** · "APIs externas que os bots podem chamar" (`ferramentas`); `ia` — **Servidor** · "Servidor de IA e de embeddings" (`parametros`, a parte de IA do CFG-07) | cada sub-aba com a sua |

- Cada sub-aba some sem a sua permissão; o menu sem nenhuma sub-aba visível some. As de uso usam `auth.pode` (aparecem até a lista chegar); as de administração, `auth.temPermissao`.
- Os títulos de seção **"Pessoal"** e **"Administração"** (maiúsculas, pequenos) só aparecem quando existem as duas seções.
- O clique num menu abre a **primeira sub-aba visível** dele (ex.: IA abre Bots). A barra de sub-abas só aparece com **mais de uma** visível (Pessoas, Sistema e Aparência entram direto na tela).
- O cabeçalho do conteúdo mostra o **título do menu** e, embaixo, a **descrição da sub-aba**.
- `/config/acessos` (a aba antiga) leva a `usuarios`; aba sem permissão, depois de as permissões carregarem, volta a `usuario`.
- Exemplos dos testes: sem nenhuma permissão (lista já carregada), os menus são Conta e Dispositivos, sem "Administração"; com `parametros`, Conta, Dispositivos, Sistema e IA; com `permissoes`, Conta, Dispositivos, Pessoas e IA (IA por causa de Bots); com `permissoes` e `cores`, também Aparência.

---

## 3. Lista de funcionalidades (checklist de paridade)

Legenda dos campos: **Comportamento** (o que o usuário vê/faz), **API/WS** (rotas e eventos com campos), **Arquivos**, **Regras e casos de borda**.

### 3.1 AUT — Autenticação, sessão, perfil

**AUT-01 Login**
- Comportamento: tela com logo "Conversa", campos "Usuário" e "Senha", botão "Entrar" ("Entrando..."), link "Não tem conta? Criar conta". Foco inicial no usuário. Após sucesso pede permissão de notificação se ainda `default`.
- API: `POST /login {login, senha, dispositivo_id?}` → `{id, nome, email, telefone, avatar_url, avatar_identificador, token, dispositivo:{id,...}}` (sem o campo `login`; o cliente guarda o que foi digitado, `trim()`).
- Arquivos: `components/LoginForm.vue`, `stores/auth.ts#login`.
- Regras: `dispositivo_id` salvo é reenviado para reaproveitar o dispositivo; resposta sem token → erro "Resposta de login invalida: token ausente".
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Cadastro fechado:** ao montar, a tela chama `GET /cadastro` (público, sem token) → `{aberto}`; com `aberto: false` o link "Não tem conta? Criar conta" **some**. Sem resposta (erro, servidor antigo), o link fica (o servidor recusa o cadastro se estiver fechado). Ver AUT-02 e CFG-07.
- 🆕 `9c6e8a6` (servidor) Conta de **bot** não entra pelo login: 401 "Bot não entra pelo login." (CON-16).

**AUT-02 Cadastro**
- Comportamento: "Crie sua conta" com Nome, Usuário, E-mail, Senha (`minlength=4` no HTML), botão "Cadastrar"/"Cadastrando..."; sucesso mostra "Conta criada com sucesso!" e volta ao login após 1,5 s. Link "Já tem conta? Entrar".
- API: `PUT /usuario {nome, login, email, senha}`.
- Arquivos: `components/RegisterForm.vue`.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **O cadastro pode estar fechado** (parâmetro `cadastro_aberto`, CFG-07):
  - fechado, o login não oferece "Criar conta" (AUT-01) e o `PUT /usuario` responde **403** "O cadastro está fechado: peça a um administrador para criar sua conta.";
  - quem tem a permissão `usuarios` cria contas pela aba Usuários (CFG-11), mesmo com o cadastro fechado (🆕 `f351190`: a permissão agora é `permissoes`, "Pessoas", e a tela é Pessoas › Usuários);
  - conta nova (pelo login ou pelo administrador) **começa com todas as permissões de uso** (GER-09).

**AUT-03 Início de sessão resiliente** — ver §1.5 (401 → login; outro erro → mantém login e tenta a cada 5 s com toast "(tentando de novo...)"). Testes: `tests/unit/App.test.ts`.

**AUT-04 Registro de dispositivo e token de push**
- API: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma}` e `PATCH /dispositivo {id, token_fcm}`.
- Arquivos: `stores/auth.ts#detectarNavegador/registrarTokenFCM`.

**AUT-05 Logout** — botão "Sair" (ícone) na aba Usuário (🆕 `f351190` Conta › Perfil). Local apenas (§1.5).

**AUT-06 Editar dados do perfil**
- Comportamento: seção "Dados do usuario" — Nome, Email, botão "Salvar dados"; mensagens "Preencha nome e email." / "Dados atualizados com sucesso.".
- API: `PATCH /usuario {id, nome, email}` (o tipo aceita também `telefone`, mas a tela não edita telefone).
- Arquivos: `ProfileSettingsModal.vue#salvarPerfil`.
- 🆕 `9c6e8a6` **Permissões de uso `nome` e `email`** (GER-09): sem a permissão, o campo fica desabilitado, com o `title` "Você não tem permissão para mudar o nome" / "Você não tem permissão para mudar o e-mail"; sem as duas, o botão "Salvar dados" some. O `PATCH /usuario` leva **só os campos que a pessoa pode mudar**. O servidor confere cada campo (`email` vale também para `telefone`) e responde 403 "Você não tem permissão para: alterar o próprio nome." / "… alterar o próprio e-mail e telefone.".
- 🆕 `f351190` A aba virou **Conta › Perfil** (CFG-01). Entre o cartão da foto e "Dados do usuario" entra a seção **"Armazenamento"** com o espaço usado (ANX-17); embaixo de "Nome e email visiveis na sua conta." aparece **"Usuário desde dd/mm/aaaa"** (AUT-11). O botão de tema claro/escuro do cartão da foto **saiu** (foi para Aparência › Tema, CFG-02).

**AUT-07 Alterar senha**
- Comportamento: "Senha atual", "Nova senha", "Confirmar nova senha", botão "Salvar senha".
- Validações no cliente: todos preenchidos ("Preencha todos os campos de senha."), nova ≥ 6 ("A nova senha deve ter pelo menos 6 caracteres."), confirmação igual ("A confirmacao da senha nao confere."). Sucesso: "Senha alterada com sucesso." e limpa os campos.
- API: `POST /alterar-senha {senha_atual, senha}` (o servidor confere a atual).
- 🆕 `9c6e8a6` Sem a permissão de uso `senha` (GER-09), a seção "Senha" inteira some; o servidor responde 403 "Você não tem permissão para: trocar a própria senha.".

**AUT-08 Avatar (foto de perfil)**
- Comportamento: "Foto de perfil" — "Alterar foto" ("Enviando...") / "Remover"; prévia local + % de progresso sobre o avatar.
- Processamento: recorte quadrado central, 256×256, JPEG qualidade 0,85 (`utils/imageResize.ts`), nome `avatar.jpg`.
- API: upload (§1.7, tipo 2) → `PATCH /usuario {id, avatar_anexo_id}` → `GET /anexo?identificador=` para a URL. Remover: `PATCH /usuario {id, avatar_anexo_id: null}`.
- Regra: URL do avatar expira; ao falhar a `<img>` chama `auth.renovarAvatarExpirado(url)` (renova no máximo a cada 30 s, só se a URL que falhou for a atual).
- 🆕 `9c6e8a6` Sem a permissão de uso `avatar` (GER-09), no lugar de "Alterar foto" / "Remover" aparece "Você não tem permissão para mudar o avatar."; o servidor responde 403 "Você não tem permissão para: alterar o próprio avatar." ao `PATCH /usuario` com `avatar_anexo_id`. Vale só para o avatar da pessoa (a imagem do grupo, CON-15, não pede).

**AUT-09 Permissões do sistema do usuário logado**
- API: `GET /usuario/permissoes` → `string[]` (códigos `parametros`, `permissoes`). Só controla visibilidade das abas Sistema/Acessos; o servidor confere em cada rota.
- 🆕 `9c6e8a6` A lista passa a ter **32 códigos**: 6 de administração (`parametros`, `permissoes`, `bots`, `usuarios`, `ferramentas`, `conhecimento`) e 26 de uso do app (GER-09). No **modo aberto** (ninguém tem `permissoes`) o servidor devolve todos. As de administração continuam em `auth.temPermissao`; as de uso vão por `auth.pode`, que é verdadeiro enquanto a lista não chegou.
- 🆕 `f351190` (servidor `ac710eb`, migrações 054 e 055) Continua com **32 códigos**, mas outros: **5 de administração** (`usuarios` deixou de existir, juntada a `permissoes`) e **27 de uso** (entrou `localizacao`). Ver GER-09.

**AUT-10 Ver perfil de outro usuário** (`UserInfoModal.vue`)
- Abre ao clicar no avatar da conversa **direta** (lista ou cabeçalho). Mostra foto/inicial, nome, Email, Telefone ("Nao informado" se vazio) e botão **"Ver anexos"** que leva a `/anexos/:conversaId`.
- Dados vêm de `GET /usuario/contatos` (fallback: dados da própria conversa).
- 🆕 `f351190` Depois de "Telefone", o campo **"Usuário desde"** com a data do cadastro (`dd/mm/aaaa`); sem a data, o campo não aparece (AUT-11).

**AUT-11 🆕 `f351190` "Usuário desde" (data do cadastro)** (`utils/formatters.ts#usuarioDesde`, `ProfileSettingsModal.vue`, `UserInfoModal.vue`, `ConfiguracaoAcessos.vue`; servidor `ac710eb`)
- **De onde vem:** `criado_em` de cada contato em `GET /usuario/contatos` (CON-11) e de cada usuário em `GET /permissoes` (CFG-08). Conta antiga, sem a data (`null`), não mostra nada.
- **Formato:** `usuarioDesde(data)` = "Usuário desde " + `toLocaleDateString('pt-BR', {day: '2-digit', month: '2-digit', year: 'numeric'})` → **"Usuário desde 05/10/2026"**; sem data, texto vazio.
- **Onde aparece:**
  - **Conta › Perfil**, na seção "Dados do usuario", logo abaixo de "Nome e email visiveis na sua conta.": **"Usuário desde dd/mm/aaaa"**, com a data do próprio usuário achada em `chat.contatos`;
  - **perfil de outra pessoa** (`UserInfoModal`, AUT-10): rótulo **"Usuário desde"** (pequeno, em maiúsculas, como "Email" e "Telefone") e a data `dd/mm/aaaa` embaixo;
  - **lista de Pessoas** (CFG-08/CFG-11): só a data, numa coluna à direita, com o `title` **"Usuário desde"**.
- Testes: `usuarioDesde(new Date(2026, 9, 5, 14, 30))` → "Usuário desde 05/10/2026"; `null` e `undefined` → `''`; o perfil sem a data não mostra "Usuário desde".

### 3.2 CON — Conversas

**CON-01 Lista de conversas** (`components/ChatSidebar.vue`) — 🆕 `769bd7d`: seção recolhível "Chamadas (N)", emoji do grupo e bolinha amarela de ausente
- API: `GET /conversas` → `Conversa[]` (`id, descricao, tipo, nome, destinatario_id, mensagem_id, ultima_mensagem, ultima_mensagem_texto, mensagens_sem_visualizar, avatar_url, fixada_ordem, arquivada_em`; 🆕 `769bd7d` `chamada` — grupo criado como chat de uma ligação — e `emoji` — grupo com emoji no lugar da inicial; a imagem do grupo vem em `avatar_url`).
- Item: avatar (ou inicial — primeira letra, `Array.from` para não quebrar emoji), bolinha verde se o destinatário da direta está online, título (`descricao || nome || "Conversa #id"`), etiqueta "Grupo" (🆕 `e33ecba`: virou um **ícone de pessoas** cinza depois do nome, `role="img"` com `aria-label`/`title` **"Conversa em grupo"**), ícone de alfinete se fixada, contador de não lidas (oculto em arquivadas), prévia `resumirTexto(ultima_mensagem_texto)` ou "Sem mensagens", três pontinhos animados se alguém digita, botões "Mais opções" e "Abrir em nova janela" (hover).
- Ordem: fixadas primeiro por `fixada_ordem`; depois por `mensagem_id` desc. Arquivadas numa seção recolhível no fim: botão "Arquivadas (N)".
- Prévia: menção `@[Nome](id)` vira `@Nome`; cada bloco de código vira `Código (linguagem)`; mensagem oculta aparece como o servidor mandar em `ultima_mensagem_texto` (o web recarrega a lista após ocultar para mostrar "Mensagem oculta").
- Recarregada em: nova mensagem, status de mensagem (WS 3), conversa atualizada (WS 40), envio de mensagem, leitura.
- 🆕 `769bd7d` **Seção "Chamadas":** a conversa com `chamada: true` que **não** está fixada nem arquivada sai da lista principal e vai para a seção recolhível **"Chamadas (N)"** no fim, **antes** de "Arquivadas (N)" (a primeira das duas seções encosta no fim do painel; cada uma abre e fecha independente da outra; começam fechadas). Arquivada vai para "Arquivadas"; fixada fica nas fixadas. Com termo no filtro, as duas seções somem e tudo aparece junto. O servidor só marca `chamada` no grupo **criado** para a ligação, não na conversa de origem reaproveitada (CHA-18).
- 🆕 `6d20ff7` (servidor, junto de `e33ecba`) Como toda ligação nova volta a criar o **seu** grupo (CHA-18), o chat de cada ligação com mensagens entra em "Chamadas (N)", com o nome dos participantes (ex.: "Ana, Bruno"), sem o prefixo "Chamada: " (os grupos antigos mantêm o nome que tinham).
- 🆕 `769bd7d` Grupo com `emoji` mostra o emoji (maior) no lugar da inicial (CON-15). A bolinha de presença da direta fica **verde** (ativo) ou **amarela** (ausente), com tooltip "Ativo"/"Ausente"; offline não mostra bolinha (PRE-01).
- 🆕 `f351190` **Gravando áudio na lista:** quando alguém (que não sou eu) grava áudio na conversa, no lugar dos três pontinhos de digitando aparecem **três pontinhos vermelhos** (cor `danger-500`), com o `title` **"Gravando áudio..."**; gravando tem prioridade sobre digitando (ENV-16).

**CON-02 Filtro da lista e "Nova conversa"**
- Campo "Pesquisar..." filtra localmente por título e pela prévia. Com termo, as arquivadas aparecem junto com a etiqueta "Arquivada" e não há arraste.
- Abaixo, seção "Nova conversa" com contatos que **não** têm conversa direta, filtrados por nome/login/email; clicar cria/abre a conversa direta.
- 🆕 `769bd7d` Cada contato de "Nova conversa" tem a bolinha de presença (verde/amarela, PRE-01).

**CON-03 Fixar, desafixar e reordenar fixadas**
- Menu: "Fixar" (entra no fim das fixadas) / "Desafixar". Arrastar uma fixada sobre outra (metade de cima = antes, de baixo = depois) reordena; linha azul indica a posição. Sem mudança → não chama a API.
- API: `PATCH /conversa/fixadas {conversas: number[]}` — a lista completa na ordem; as que ficam de fora deixam de ser fixadas. Atualização otimista; erro → recarrega `GET /conversas`.
- Arquivos: `stores/chat.ts#fixarConversa/moverFixada/salvarFixadas`.

**CON-04 Arquivar / desarquivar**
- Menu: "Arquivar" / "Desarquivar". Arquivar tira das fixadas e fecha a notificação da conversa.
- Regra: arquivada **não toca som nem notifica**, mas as mensagens continuam chegando; o contador de não lidas não aparece nela.
- API: `PATCH /conversa/arquivada {conversa, arquivada}` → `{id, arquivada}`. Otimista; erro → recarrega.

**CON-05 Menu de contexto da conversa** — clique direito ou botão ⋯; posicionado no clique e ajustado para não sair da janela; fecha com clique fora, Esc ou redimensionar. Itens conforme CON-03/04.

**CON-06 Conversa direta (obter ou criar)**
- Reaproveita a direta existente (`tipo=1 && destinatario_id`). Senão: `PUT /conversa {descricao:'', tipo:1}` → `PUT /conversa/usuario {conversa_id, usuario_id}` para o próprio usuário e para o contato → `GET /conversas`.
- Usada por: "Nova conversa", chip de membro no cabeçalho do grupo, clique numa menção, encaminhar para contato, responder no privado.

**CON-07 Criar grupo** (`CreateGroupModal.vue`)
- "Criar grupo": "Nome do grupo" (placeholder "Ex: Projeto Alpha"), "Selecionar usuários" com filtro "Filtrar contatos" (nome/login/email) e checkboxes; "Cancelar" / "Criar"; Esc cancela.
- Validações: "Informe o nome do grupo." / "Selecione ao menos um usuário.".
- API: `PUT /conversa {descricao, tipo:2}` → `PUT /conversa/usuario` para cada membro **incluindo o criador** (em paralelo, sem duplicar) → recarrega e abre o grupo.
- 🆕 `e33ecba` **Imagem ou emoji ao criar** (`CreateGroupModal.vue`, `chat.criarGrupo`):
  - no topo do modal, o avatar de 56 px (prévia da imagem, o emoji ou a inicial do nome digitado, "G" sem nome) e os botões **"Imagem"** (seletor `image/*`), **"Emoji"** (abre/fecha o `EmojiPicker` na largura do modal) e **"Remover"** (só com imagem ou emoji). **Um tira o outro**, como no CON-15;
  - a imagem **só sobe ao criar** (até lá, só a prévia local); "Cancelar" descarta;
  - botão **"Criar"** → **"Criando..."** (desabilitado enquanto cria); erro na caixa do modal (mensagem do servidor ou "Erro ao criar grupo").
  - Ordem das chamadas: (com imagem) recorte/redução `redimensionarImagem` e upload tipo 2 `avatar.jpg` (§1.7) → `PUT /conversa {descricao, tipo:2}` → `PUT /conversa/usuario` de cada membro (em paralelo) → `PATCH /conversa {id, avatar_anexo_id, emoji: null}` (imagem) ou `{id, avatar_anexo_id: null, emoji}` (emoji) → `GET /conversas` → abre o grupo. Sem imagem nem emoji, não há o `PATCH`.
- 🆕 `9c6e8a6` Sem a permissão de uso `grupo` (GER-09), o botão "Novo grupo" da lista some; o servidor responde 403 "Você não tem permissão para: criar grupos." ao `PUT /conversa` com `tipo: 2` (a conversa direta não pede).

**CON-08 Gerenciar membros do grupo** (`GroupMembersModal.vue`) — 🆕 `eaa8bac`: o modal virou o painel **"Dados do grupo"** (`PainelGrupo.vue`), aberto pelo avatar do grupo no cabeçalho ou pelo botão "Participantes e anexos", com os anexos da conversa embaixo (§11)
- "Membros do grupo": campo "Nome do grupo" + "Renomear" (habilitado só se mudou; "Salvando..."); lista de membros com "Remover" (não aparece para si mesmo; "Removendo..."); "Adicionar participante" com select "Selecionar usuario" (contatos fora do grupo) + "Adicionar" ("Adicionando..."). Mensagens: "Grupo renomeado com sucesso.", "Participante adicionado com sucesso.", "Participante removido com sucesso.".
- API: `GET /conversa/usuarios?conversa=` → `[{id (conversa_usuario_id), usuario_id, nome, avatar_url}]`; `PATCH /conversa {id, descricao}`; `PUT /conversa/usuario {conversa_id, usuario_id}`; `DELETE /conversa/usuario?id=<conversa_usuario_id>`.
- Não há regra de administrador no cliente: qualquer membro pode renomear/adicionar/remover (o servidor pode recusar).
- 🆕 `769bd7d` No topo do painel, o avatar do grupo (imagem, emoji ou inicial) com os botões "Imagem", "Emoji" e "Remover" (CON-15). Cada participante tem a bolinha verde/amarela (PRE-01) e, quem está com o grupo aberto agora, " · nesta conversa" depois do nome (PRE-03).

**CON-09 Cabeçalho da conversa** (`ChatHeader.vue`)
- Avatar (clicável na direta → AUT-10), bolinha online, título; subtítulo: em grupo, nomes dos membros separados por vírgula (some quando alguém digita/grava) e uma faixa de "chips" de membros (foto + nome) que, clicados, abrem a conversa direta com o membro.
- Indicador animado de digitando/gravando (ver MSG-15).
- Botões (escondidos durante chamada ou chamada recebida): "Chamada de voz", "Chamada de video", "Compartilhar tela"; em grupo "Gerenciar membros"; pesquisa na conversa (desktop: campo inline; celular: botão que abre painel "Pesquisar nesta conversa").
- 🆕 `769bd7d` **Direta:** subtítulo de presença abaixo do título — "nesta conversa" (cor primária), "ativo agora", "ausente" ou "visto por último …" (PRE-03); some enquanto a pessoa digita/grava. Bolinha verde/amarela no avatar (PRE-01). **Grupo:** emoji no lugar da inicial (CON-15).
- 🆕 `769bd7d` Botão **"Resumir conversa"** (ícone de brilhos), depois de "Compartilhar tela" e antes do de participantes, **só com a IA ligada** (`recursos.ia`, GER-08); aparece também durante chamada. Abre o diálogo do resumo (CON-14), que fecha ao trocar de conversa.
- 🆕 `9c6e8a6` **Marca "Bot"** ao lado do título, numa pílula, com o `title` "Responde com a IA": só na conversa **direta** com um contato que tem `bot: true` (CON-11, CON-16). O título passou a ficar num `<span>` que corta com "…", para a marca caber.
- 🆕 `9c6e8a6` **Botões por permissão** (GER-09): "Chamada de voz" só com `chamada_audio`; "Chamada de video" só com `chamada_video`; "Compartilhar tela" só com `chamada_video` **e** `compartilhar_tela`.
- 🆕 `f351190` Botão **"Pastas de mensagens"** (ícone de pasta), depois de "Resumir conversa" e antes de "Pesquisar na conversa": só na conversa **direta** e fora da janela popup; aparece também durante chamada. Abre e fecha o painel de pastas (fundo destacado quando aberto, CON-17).

**CON-10 Abrir conversa em nova janela (popup)**
- Botão "Abrir em nova janela" em cada item da lista → `window.open('/chat-popup.html?conversa=<id>', 'chat-popup-<id>', 900×700 centralizada)`.
- O popup (`ChatPopup.vue`) monta Pinia própria, inicializa o chat inteiro (WS próprio), mostra cabeçalho (sem botão de voltar), lista, campo, visualizador de imagens e encaminhamento; título da janela = nome da conversa. Sem sessão: "Sessao expirada. Faca login novamente."; id inválido: "Conversa invalida.".

**CON-11 Contatos**
- API: `GET /usuario/contatos` → `Contato[]` (`id, nome, login, email, telefone, avatar_url?`). Regra de avatar usada em várias telas: a lista de contatos pode não trazer foto → usa o `avatar_url` da conversa direta com o contato; o próprio usuário usa o avatar do perfil.
- 🆕 `9c6e8a6` (servidor `ef13c22`) Cada contato ganha **`bot: boolean`** (é um bot de IA, CON-16). Bot **excluído** que já conversou (`bot.excluido_em`) some dos contatos (e da tela Acessos); as conversas diretas com ele ficam, com o nome dele.
- 🆕 `f351190` (servidor `ac710eb`) Cada contato ganha **`criado_em`** (data do cadastro, `Date | null`; "Usuário desde", AUT-11).
- 🆕 `f351190` **Contatos recarregados:** a busca da lista e as sugestões do @ procuram em `chat.contatos`, que agora é relido:
  - no **WS 66** (`ContatosAtualizados`, sem campos): `GET /usuario/contatos` e troca a lista. O servidor manda o 66 a **todas as conexões abertas** quando uma conta é criada (cadastro ou administrador), quando muda o nome ou o e-mail de alguém, quando uma conta é excluída e quando um bot é criado, renomeado ou excluído;
  - a cada **reconexão** do WebSocket (§1.4), porque o aviso perdido com o socket fora não chega depois.

**CON-12 Deep link, histórico e âncora** — §2.1. Clique em notificação abre a conversa e **recarrega as mensagens** antes (as em memória não têm as que chegaram fechada).

**CON-13 Conversa atualizada em tempo real** — WS tipo 40 → `GET /conversas`.

**CON-14 🆕 `769bd7d` Resumo da conversa por IA** (`ResumoConversaModal.vue`; cache em `services/resumosSalvos.ts` desde `05507a0`)
- Entrada: botão "Resumir conversa" do cabeçalho, só com a IA ligada (GER-08).
- Diálogo **"Resumo da conversa"** (botão "Fechar"; clique fora fecha). Períodos em pílulas: **"Últimas 24 horas"** (padrão, `24h`), **"7 dias"** (`7d`), **"30 dias"** (`30d`), **"Últimas 400 mensagens"** (`recentes`; em `769bd7d` era "Últimas mensagens", renomeado em `05507a0`). Botão **"Resumir"** ("Resumir de novo" quando já há resumo); períodos e botão ficam desabilitados enquanto processa.
- Estados:
  - antes do primeiro pedido: "Escolha o período e toque em Resumir. As mensagens são separadas por assunto.";
  - processando: "Resumindo N mensagens..." e "Pode levar alguns minutos, dependendo do servidor de IA.";
  - erro: a mensagem do servidor numa caixa vermelha ("Erro ao pedir o resumo" / "Erro ao consultar o resumo" como fallback; consulta que falha, por exemplo porque o servidor reiniciou, vira erro);
  - período sem mensagens: "Nenhuma mensagem nesse período.".
- Resultado: linha "Resumo de dd/MM às HH:MM · <período> · N mensagem(ns)" (quando foi pedido; 🆕 `05507a0`); um cartão por **assunto** com título, resumo, **pendências** (lista com bolinha amarela) e o botão **"Ver na conversa (N mensagem/mensagens)"**, que fecha o diálogo e vai à **primeira** mensagem do assunto (MSG-06); rodapé "Gerado por IA a partir de N mensagens. Confira na conversa antes de agir.".
- API: `POST /conversa/resumo {conversa_id, periodo}` → `ResumoConversa {id (texto), conversa_id, periodo, status: 'processando'|'concluido'|'erro', mensagens, assuntos:[{titulo, resumo, pendencias: string[], mensagens: number[]}], erro}`; enquanto `processando`, `GET /conversa/resumo?id=` **a cada 2 s**.
- 🆕 `05507a0` **Cache:** o último resumo de cada conversa fica no IndexedDB (banco `conversa-resumos`, loja `resumos`, chave `<usuarioId>:<conversaId>`, valor `{resumo, geradoEm}`), gravado a cada mudança. Ao abrir o diálogo, ele volta com o período usado; se estava em andamento, volta a consultar o mesmo pedido. Ir até a mensagem e voltar, ou fechar o navegador, não perde o resumo. Sem IndexedDB o resumo só não fica guardado.

**CON-15 🆕 `769bd7d` Imagem ou emoji do grupo** (`PainelGrupo.vue`, `chat.alterarAvatarGrupo`)
- No topo do painel "Dados do grupo" (CON-08): avatar de 56 px (imagem, emoji ou inicial) e os botões **"Imagem"** (seletor de imagem), **"Emoji"** (abre o `EmojiPicker` na largura do painel) e **"Remover"** (só se há imagem ou emoji); "Salvando..." enquanto altera.
- Imagem: recorte e redução como no avatar do usuário (`redimensionarImagem`), upload tipo 2 com o nome `avatar.jpg` (§1.7) e `PATCH /conversa {id, avatar_anexo_id, emoji: null}`. Emoji: `PATCH /conversa {id, avatar_anexo_id: null, emoji}`. Remover: os dois `null`. **Um tira o outro.** Depois recarrega `GET /conversas`.
- Erros na caixa do painel: "Erro ao alterar a imagem do grupo" / "Erro ao enviar a imagem do grupo" (ou a mensagem do servidor).
- Onde aparece: o emoji entra no lugar da inicial na lista (CON-01), no cabeçalho (CON-09), no painel e na página de Anexos (GER-07); a imagem vem em `avatar_url` da conversa.

**CON-16 🆕 `9c6e8a6` Conversa com um bot de IA** (`ChatHeader.vue`; servidor `ef13c22`, `src/bots.ts`, migrações 047 e 050)
- **O que é:** um bot é um **usuário comum** (tabela `bot`), criado pelo administrador (CFG-11, CFG-12), com instruções de personalidade e do que faz. Aparece nos contatos com `bot: true` (CON-11) e conversa por mensagens comuns: no cliente não há rota nem evento novo.
- **Na tela:** a única diferença visível no chat é a pílula **"Bot"** (`title` "Responde com a IA") ao lado do nome, no cabeçalho da conversa **direta** com ele (CON-09). Lista, "Nova conversa" e grupos não mostram a marca.
- **Quando ele responde** (servidor, em segundo plano, só com a IA ligada, GER-08):
  - na conversa **direta**, a toda mensagem;
  - num **grupo**, quando é **mencionado** (`@[Nome](id)`) ou quando **respondem a uma mensagem dele**;
  - nunca a outro bot; bot desligado ou excluído não responde;
  - mensagens que chegam enquanto ele pensa viram **uma resposta só**, à mais nova.
- **Como ele responde:**
  - marca a mensagem como **visualizada** e manda o **"digitando"** (WS 4) a cada 3 s enquanto pensa (o cliente mostra como de qualquer pessoa, ENV-15);
  - a resposta é uma mensagem comum: na direta sem citação; no grupo **citando** a mensagem que o chamou;
  - pode também reagir, responder citando, mandar figurinha Noto, encaminhar, criar votação e buscar mensagens (ferramentas do servidor), cada uma só com a **permissão de uso** dele (GER-09; nasce com `emoji`, `responder` e `reagir`, se quem criou as tem);
  - se falha (servidor de IA fora do ar, demora), manda na conversa, citando a mensagem: **"Não consegui responder a esta mensagem: <motivo>"** (ou "…mensagem." sem motivo).
- **Login:** conta de bot não entra pelo login (401 "Bot não entra pelo login.", AUT-01).
- **Excluído:** sai dos grupos, dos contatos e das telas e não responde mais; as conversas diretas com ele e o que ele escreveu ficam, com o nome dele (bot sem histórico é apagado de vez).

**CON-17 🆕 `f351190` Pastas de mensagens na conversa direta** (`PainelPastas.vue`, `stores/pastas.ts`, `utils/pastas.ts`, `ChatHeader.vue`, `App.vue`; servidor `ac710eb`, `src/pastas.ts`, migração 056)
- **O que é:** cada pessoa organiza mensagens da conversa **direta** em pastas por assunto. A pasta **só marca**: a mensagem continua no chat e pode estar em várias pastas. A pasta é de quem a criou; **compartilhada**, a outra pessoa da conversa também a vê e põe ou tira mensagens dela, mas só o dono renomeia, muda a cor, deixa de compartilhar e exclui. Grupo não tem pastas. Não há permissão de uso própria.
- **Entrada:** botão **"Pastas de mensagens"** do cabeçalho, só na direta e fora da janela popup (CON-09); abre e fecha o painel. **Selecionar uma mensagem abre o painel sozinho** (ENV-29).
- **Carga:** a cada troca de conversa (`pastas.usarConversa`): limpa a seleção, fecha a pasta aberta e esvazia a lista; na direta, `GET /pastas?conversa=<id>`; em outra conversa, fecha o painel. Entre duas diretas o painel continua aberto.
- **Painel** (208 px, à direita do chat; em tela com menos de 1024 px, por cima, fixo à direita; só na seção Chat e fora da chamada em tela cheia):
  - cabeçalho **"Pastas"**, botões **"Nova pasta"** (+) e **"Fechar"** (×);
  - vazio: **"Crie pastas para guardar mensagens por assunto. Arraste ao lado das mensagens para selecioná-las e solte-as numa pasta."**;
  - cada pasta: ícone de pasta cheio na cor dela, o nome, o ícone de pessoas se compartilhada (`title` **"Compartilhada com <nome>"** para o dono, **"Compartilhada por <nome>"** para o outro) e o **número de mensagens**. `title` da linha: o nome (dono) ou **"<nome da pasta> (compartilhada por <nome>)"**. `<nome>` é o nome da conversa (o da outra pessoa) ou, sem ele, "a outra pessoa";
  - clique abre a pasta (MSG-24); a aberta fica destacada;
  - ordem: por nome (pt-BR, sem diferenciar maiúscula nem acento) e depois id;
  - durante um arraste de mensagens (ENV-29): **"Solte numa pasta"** no topo, bordas tracejadas e a pasta sob o cursor destacada.
- **Criar:** o "+" mostra um campo no topo (placeholder **"Nome da pasta"**, até 60 caracteres). Enter ou sair do campo cria; nome vazio não cria; Esc cancela. `PUT /pasta {conversa_id, nome, cor: '', compartilhada: false}` → `PastaMensagens`.
- **Opções (só o dono):** botão **"Opções da pasta"** (⋯), que aparece no lugar do número ao passar o mouse. Menu:
  - **"Renomear"**: o nome vira um campo (já selecionado, até 60). Enter ou sair do campo salva; Esc cancela; vazio ou igual não faz nada. → `PATCH /pasta {id, nome}`;
  - **"Compartilhar com <nome>"** / **"Deixar de compartilhar"** → `PATCH /pasta {id, compartilhada}`;
  - **cores:** 8 bolinhas de 16 px, a atual com anel; `title` **"Cor do tema"** (a primeira) e **"Cor"** (as outras) → `PATCH /pasta {id, cor}`;
  - **"Excluir"** (vermelho): confirmação de perigo, título **"Excluir pasta"**, texto **"A pasta "<nome>" sai daqui. As mensagens continuam na conversa."** (compartilhada: **"A pasta "<nome>" sai daqui e de <nome da outra pessoa>. As mensagens continuam na conversa."**), botões "Cancelar" / **"Excluir"** → `DELETE /pasta?id=` → `{id}`.
- **Paleta** (`CORES_PASTA`, nesta ordem): `''` (cor do tema: `var(--color-primary-500)`), `#ef4444`, `#f59e0b`, `#22c55e`, `#06b6d4`, `#3b82f6`, `#a855f7`, `#ec4899`. A cor vai como texto; o servidor aceita até 20 caracteres, sem conferir o formato.
- **Erros** (diálogo com a mensagem do servidor): **"Não foi possível criar a pasta"**, **"Não foi possível renomear a pasta"**, **"Não foi possível mudar a pasta"** (compartilhar), **"Não foi possível mudar a cor"**, **"Não foi possível excluir a pasta"**, **"Não foi possível pôr na pasta"**.
- **API:** `GET /pastas?conversa=` → `PastaMensagens[] {id, conversa_id, dono_id, nome, cor, compartilhada, mensagens}` (as minhas e as compartilhadas pela outra pessoa; `mensagens` = quantas; ordem `lower(nome), id`); `PUT /pasta`; `PATCH /pasta {id, nome?, cor?, compartilhada?}` → `PastaMensagens`; `DELETE /pasta?id=`. Pôr e tirar mensagens: ENV-29 e MSG-24.
- **Na mensagem:** `Mensagem.pastas?: number[]` — os ids (em ordem crescente) das pastas em que ela está **e que eu enxergo**; o servidor **omite a chave** quando não há nenhuma. Vira as marcas da bolha (MSG-23). O web atualiza `mensagem.pastas` na memória ao pôr, tirar e excluir, sem reler as mensagens.
- **Tempo real — WS 67** (`PastasAtualizadas`) `{conversa_id, pasta_id, mensagens?: number[], acao?: 'incluir' | 'tirar'}`:
  - o servidor manda ao dono e, se a pasta é (ou era, antes da mudança) compartilhada, aos dois da conversa — inclusive a quem fez, para as outras abas e aparelhos;
  - criar, alterar e excluir vêm **sem** `mensagens` e `acao`; pôr e tirar vêm **com** eles;
  - o web ignora o aviso de outra conversa; com `mensagens` e `acao`, marca ou desmarca as mensagens em memória; sempre relê `GET /pastas`; se é a pasta aberta e veio `acao`, relê as mensagens dela;
  - ao reler a lista, a pasta que sumiu (excluída, ou que deixou de ser compartilhada) sai das bolhas e, se estava aberta, fecha.
- **Limites e erros do servidor:** só em conversa direta (400 "Pastas só existem nas conversas diretas."); nome de 1 a 60 caracteres depois do `trim` (400 "Informe o nome da pasta." / "O nome da pasta pode ter até 60 caracteres."); até **50 pastas por pessoa por conversa** (400 "Cada pessoa pode ter até 50 pastas por conversa."); mudar ou excluir a de outro: 403 "Só quem criou a pasta pode mudá-la."; pasta que eu não enxergo: 404 "Pasta não encontrada!".

### 3.3 MSG — Mensagens: exibição e leitura

**MSG-01 Carregamento e paginação bidirecional** (`stores/chat.ts`, `composables/useScrollManager.ts`)
- Abrir conversa: `GET /mensagens?conversa=X&mensagemreferencia=0&mensagensprevias=80&mensagensseguintes=0`. Mensagens otimistas ainda enviando são preservadas.
- Rolar para cima: prefetch quando chega a 1,5× a altura visível do topo; injeta ao chegar no topo, compensando a rolagem. `GET /mensagens?conversa=X&mensagemreferencia=<primeira salva>&mensagensprevias=60&mensagensseguintes=0`. Para quando volta vazio.
- Rolar para baixo (só depois de pular para uma mensagem antiga): `mensagensprevias=0&mensagensseguintes=60` a partir da última salva.
- Ordem: `coalesce(visivel_em, inserida)` e depois `id`; ids negativos (enviando) no fim (`utils/ordemMensagens.ts`). Referência de paginação sempre uma mensagem com id > 0.
- Indicadores: "Carregando mensagens", "Carregando mensagens anteriores", "Carregando mensagens seguintes".

**MSG-02 Separadores e agrupamento**
- Separador de dia com `toLocaleDateString('pt-BR', {weekday:'short', day, month, year})` (ex.: "seg., 05/10/2026").
- Em grupo, o nome do remetente aparece acima das bolhas recebidas; `mudouRemetente` controla o espaçamento.

**MSG-03 Indicador "Últimas" (não lidas)** (`MessageList.vue`)
- Linha "Últimas" inserida antes da primeira não lida. Regras:
  1. A posição é fixada na primeira aparição; novas mensagens não a movem.
  2. Fica enquanto a conversa está aberta (não some após ler); sai ao trocar de conversa.
  3. Se a janela perdeu o foco e chega mensagem nova, a linha passa para cima dessa mensagem.
  4. Mensagem nova com a janela focada e a rolagem a ≤ 500 px do fim não gera a linha.
  5. O auto-scroll não empurra a linha para fora da tela enquanto houver não lidas. 🆕 `8b43965`: isso só vale com a linha **à vista**; se ela já saiu da tela (está acima), a mensagem que chega **rola até ela** normalmente (`useScrollManager.ts`).
- Ao abrir conversa com não lidas: posiciona a primeira não lida no topo (margem 40 px); sem não lidas: rola ao fim e "gruda" no fim enquanto o conteúdo cresce (imagens, áudio, código).

**MSG-04 Marcar como visualizada**
- Só com a janela focada. A cada rolagem/foco, cada mensagem de outro, ainda não visualizada, **totalmente visível** (ou com o fim visível) é marcada.
- API: `POST /mensagem/visualizar {conversa, mensagem}` — **uma chamada por mensagem**. Sucesso marca `visualizada=recebida=true`, desconta do contador da conversa (otimista); ao chegar a 0 fecha a notificação dessa conversa; recarrega `GET /conversas`. Falha não desconta.

**MSG-05 Botões de rolagem**
- Botão "Ir para o final" quando a > 1000 px do fim (se houve salto para mensagem antiga, recarrega as 80 últimas e vai ao fim sem animação).
- Botão flutuante "Há novas mensagens" quando chega mensagem e o usuário está longe do fim.

**MSG-06 Ir para uma mensagem**
- Se não está carregada: `GET /mensagens?conversa&mensagemreferencia=<id>&mensagensprevias=30&mensagensseguintes=30` (se não vier, tenta 120/120); **substitui** a lista e ativa paginação nos dois sentidos. Centraliza a mensagem e a destaca com anel amarelo por 1,2 s (🆕 `d202c06`: virou faixa, ver abaixo).
- Usado por: resultado de pesquisa, citação de resposta, atividade, "Abrir mensagem" nos anexos. Encaminhada de outra conversa abre a conversa de origem **se o usuário participa dela**. 🆕 `769bd7d` Também "Ver na conversa" do resumo (CON-14).
- Erro: "Nao foi possivel localizar esta mensagem no contexto da conversa.".
- 🆕 `d202c06` **Destaque novo** (`useScrollManager.ts#destacarMensagem`): em vez do anel amarelo, uma **faixa da largura toda da lista** e da altura da mensagem, na cor `surface-800` a 15% (escura no tema claro, clara no escuro). A faixa **aparece de uma vez**, fica **1,5 s** e some aos poucos em **0,7 s**. Destacar outra mensagem antes disso limpa a anterior.

**MSG-07 Classificação das bolhas** (`utils/classificarMensagem.ts`, por prioridade)

| Tipo | Componente | Condição |
|---|---|---|
| Excluida | `BolhaExcluida` | `excluida_em` preenchido (antes de tudo) |
| Chamada | `BolhaChamada` | conteúdo único tipo 6 |
| Postagem 🆕 `d202c06` | `BolhaPostagem` | `mensagem.postagem` preenchido (depois de Excluida e Chamada, antes de Imagem) — MSG-22 |
| Imagem | `BolhaImagem` | conteúdo único tipo 2, sem referência |
| Enquete 🆕 | `BolhaEnquete` | conteúdo único tipo 8 (**com ou sem** referência; vem antes de Figurinha) |
| Figurinha | `BolhaFigurinha` | conteúdo único tipo 7, sem referência |
| Localizacao 🆕 `f351190` | `BolhaLocalizacao` | conteúdo único tipo 9, sem referência (depois de Figurinha, antes de Codigo) — MSG-25 |
| Codigo | `BolhaCodigo` | texto único só com blocos de código, sem referência |
| Emoji | `BolhaEmoji` | texto único só com emojis, sem referência (fonte grande) |
| ComReferencia | `BolhaReferencia` | tem `mensagem_referencia.mensagem` |
| TextoCurto | `BolhaTextoCurto` | texto único, uma linha, ≤ 60 caracteres |
| Padrao | `BolhaPadrao` | demais |

Hora (`HH:MM`) dentro da bolha (`MensagemStatus.vue`), posição conforme variante (sobre a imagem com sombra radial, etc.).

**MSG-08 Ícone de status de entrega** (só nas próprias mensagens, fora da bolha; não em mensagem de chamada)
- `enviando` (relógio) → enviada (✓) → `recebida` (✓✓ cinza) → `visualizada` (✓✓ cor primária).
- 🆕 `f351190` Na conversa direta, as pastas da mensagem aparecem como bolinhas **atrás** desse ícone, sem mudar a altura (MSG-23).

**MSG-09 Detalhe do status** (`DetalheStatusMensagem.vue`)
- Abre com o mouse parado 300 ms sobre o ✓ (ou clique/toque).
- API: `GET /mensagem/status/detalhe?id=<mensagemId>` → `[{usuario_id, nome, recebida, visualizada, reproduzida}]` (datas ou null).
- Direta: etapas "Enviada" (visivel_em ou inserida), "Recebida", "Visualizada", "Ouvida" (só áudio tipo 4/5), "Oculta" (se oculta); sem data → "Aguardando".
- Grupo: seções "Visualizada por (N)", "Recebida por (N)", "Aguardando (N)" com horário; linha "Oculta" no topo se oculta.
- Formato: hoje só `HH:MM`; outros dias `dd/MM HH:MM`. Erro: "Não foi possível carregar o status.".

**MSG-10 Atualização de status em tempo real**
- WS tipo 3 `{grupo: conversaId, mensagens: "12,13,14"}` → `GET /mensagem/status?conversa=X&mensagem=12,13,14` → `[{mensagem_id, recebida, visualizada, reproduzida, excluida_em}]` → atualiza na tela. Se algum id não está carregado e é a conversa ativa → recarrega as mensagens. Sempre recarrega `GET /conversas`.
- **Ocultar feito pelo autor em outro aparelho chega por aqui** (`excluida_em`).

**MSG-11 Links clicáveis** (`utils/formatters.ts#parseLinks`)
- Regex `https?://…` ou `www.…`; pontuação final `.,;:!?'` sai do link; `)` só sai se não fechar um `(` do próprio link. `www.` ganha `https://`. Abre em nova aba (`noopener`).

**MSG-12 Menções na mensagem**
- Formato no texto: `@[Nome](usuarioId)`. Renderizada como botão `@Nome` destacado; hover mostra cartão com foto e nome; clique abre a conversa direta com a pessoa (`MencaoLink.vue`).

**MSG-13 Mensagem de chamada** (`BolhaChamada.vue`)
- Conteúdo tipo 6 com JSON `{chamada_id, tipo, status, iniciada, finalizada, duracao, participantes:[{usuario_id, nome, status, duracao}]}`.
- Título "Chamada de audio|video" (+ " em grupo" se > 2 participantes). Direta: "<remetente> · mm:ss" ou status ("Recusada"=2, "Perdida"=5, "Cancelada"=6). Grupo: lista de participantes com duração ou status ("Perdida"=1, "Recusou"=2, "Desconectou"=5). Cores: verde encerrada (4), vermelho recusada/perdida. Sem menu de ações e sem status de entrega.
- 🆕 `964ed72` Quando o conteúdo traz `chat_chamada_id` (o chat da ligação; o servidor só manda para quem é membro dele e só quando o chat **não** é a própria conversa da ligação), a bolha ganha embaixo o botão **"Ver chat da chamada"** / **"Esconder o chat da chamada"**, que abre o chat da ligação só para leitura dentro da bolha (CHA-26); aberta, a bolha passa de 320 px para até 480 px de largura.
- 🆕 `6d20ff7` (servidor) Como o chat de cada ligação nova volta a ser um grupo próprio (CHA-18), o botão aparece em toda ligação cujo chat foi aberto (para quem é membro dele); a regra "só quando o chat não é a conversa da ligação" continua no servidor, para as ligações antigas.
- 🆕 `6d20ff7` (servidor) Chamada de **áudio que ganha vídeo** (CHA-16) passa a ser **de vídeo**: o `POST /chamada/video` troca o tipo da chamada, e a mensagem da ligação (gravada no fim) sai como **"Chamada de video"**.

**MSG-14 Mensagem só de emojis** — fonte grande, sem fundo de bolha (`BolhaEmoji`).

**MSG-15 Blocos de código** (`MessageContent.vue`, `utils/codeBlocks.ts`)
- Detecção: abertura com 3+ crases + linguagem + quebra de linha; fechamento numa linha só de crases com pelo menos o mesmo tamanho; blocos internos com linguagem aninham; para `md/markdown` fecha na última linha de crases; crases coladas no fim também fecham.
- Cabeçalho com a linguagem (ou "code"), botão "Copiar" ("Copiado!" por 2 s).
- Destaque de sintaxe (highlight.js; aliases js/ts/py/sh/cs/delphi/md/html/xml/texto…).
- Bloco longo: recolhido em altura máxima (~240 px) com degradê e botão "Expandir código"/"Recolher código", re-medido quando muda de tamanho.
- Blocos ` ```md ` / ` ```markdown ` aparecem **formatados** (marked GFM + breaks, sanitizado, links em nova aba) e ` ```mermaid ` como **diagrama** (também dentro de Markdown); alternância "Visualizar" / "Código". O diagrama usa as cores do tema atual.

**MSG-16 Mensagem oculta** (antigo "excluída") — `BolhaExcluida.vue`
- Mostra "Mensagem oculta" + hora/status. Clique alterna revelar/ocultar o conteúdo original ("Mensagem oculta" continua no topo); clicar em controles do conteúdo (botões, links, áudio, vídeo, imagem) não alterna. Tooltip "Clique para ver o conteúdo" / "Clique para ocultar o conteúdo".
- Sem menu de ações e sem reações. Não entra na galeria do visualizador. Citações de mensagem oculta mostram "Mensagem oculta" sem o conteúdo.
- 🆕 `eaa8bac` Votação oculta: ao revelar, mostra o resumo só de leitura (`EnqueteResumo.vue`): "📊 <pergunta>" e cada opção com a contagem (tooltip com os nomes ou "Ninguém votou"); "Carregando votação..." / erro.

**MSG-17 Selo de mensagem agendada** (só o autor vê antes da hora) — 🆕 `7322e83`: **substituído** pela lista do relógio (ver §12); a agendada não aparece mais no chat antes da hora. A descrição abaixo é histórica.
- Acima da bolha: "Agendada para hoje HH:MM" / "amanhã HH:MM" / "dd/MM HH:MM". Some exatamente na hora (timer agendado, não espera o ciclo de 30 s — `useAgora.ts`).

**MSG-18 Modo conexão lenta**
- Com `navigator.connection.effectiveType` em `slow-2g`/`2g`: imagens mostram "Toque para abrir" e vídeos "Toque para carregar" antes de baixar (`useConexao.ts`).

**MSG-19 Citação (resposta/encaminhada) na bolha** (`BolhaReferencia.vue`, `ReferenciaRecursiva.vue`) — 🆕 `7322e83`: dentro da citação, a encaminhada também não repete o que já aparece na citação de baixo (§12)
- Bloco com título: remetente (resposta) ou "Encaminhado de <remetente>" / "Encaminhado"; hora; conteúdos da original (imagens, áudio etc.), citação aninhada recursiva até profundidade 5. Clique no título vai para a original (MSG-06).
- Encaminhada: conteúdos próprios iguais aos da referência não são repetidos — só aparece o que foi acrescentado.

(MSG-20, a bolha de votação, está na §10.)

**MSG-21 🆕 `769bd7d` Confirmação de leitura na bolha** (`MessageBubble.vue`, `chat.confirmarLeitura`) — 🆕 `e33ecba`: só quem está na lista tem a caixa (`pode_confirmar`, `destinatarios`), dica com "Falta confirmar", WS 65 (ver o fim do item)
- Mensagem com `confirmacao` (`{total, confirmou, usuarios:[{usuario_id, nome, avatar_url, confirmada_em}]}`; só vem nas que pediram, ENV-24): um chip na linha das reações, **antes** delas, abaixo da bolha. Não aparece em mensagem oculta.
- **Quem recebe:** caixa de marcar com o texto **"Confirmar leitura"**; marcada (desabilitada) vira **"Leitura confirmada"**. A caixa só fica marcada depois da resposta do servidor. 🆕 `964ed72`: a caixa e o ✓ são desenhados (a nativa sumia com a cor principal escura).
- **Quem enviou:** ✓ num círculo e **"Aguardando confirmação"** (ninguém confirmou), **"Confirmada por N de T"** (com o total de destinatários) ou **"Confirmada por N"** (sem total, como na mensagem recém-enviada).
- Passar o mouse no chip lista quem confirmou: foto (ou inicial), nome e horário (hoje `HH:MM`, senão `dd/MM HH:MM`).
- API: `POST /mensagem/confirmar {mensagem_id}`; sucesso marca localmente (`confirmou = true` e me põe na lista). Erro: diálogo "Não foi possível confirmar a leitura".
- Tempo real: WS 8 (`ConfirmacaoLeitura`) `{conversa_id, mensagem_id, usuario_id, nome, confirmada_em}` → acrescenta a pessoa (se a mensagem está em memória e ela ainda não está na lista).
- 🆕 `e33ecba` (servidor `6d20ff7`) **Confirmação com lista** (só quem está nela confirma):
  - `confirmacao` ganha **`pode_confirmar`** (estou na lista e ainda não confirmei) e **`destinatarios`** `[{usuario_id, nome}]` (quem precisa confirmar, por nome); `total` passa a ser o número de destinatários. Mensagens que já pediam antes viram lista com todos os destinatários de então (migração 043).
  - **Caixa "Confirmar leitura"** só para quem está na lista: aparece com `pode_confirmar` **ou** `confirmou`. **Os demais** — quem enviou **e** membros fora da lista — veem o chip de contagem ("Aguardando confirmação" / "Confirmada por N de T").
  - **Passar o mouse** no chip (caixa ou contagem) lista quem confirmou (foto, nome, horário) e, embaixo, **"Falta confirmar"** com o nome de cada destinatário que ainda não confirmou.
  - Confirmar sem estar na lista: o servidor responde **403** "Você não está entre quem precisa confirmar esta mensagem.". Ao confirmar, `pode_confirmar` vira `false` localmente.
  - **WS 65** (`ConfirmacaoPedida`) `{conversa_id, mensagem_id, usuario_id, confirmacao}`: a confirmação foi pedida depois do envio (ENV-27); cada membro recebe a **sua** visão (`pode_confirmar` próprio) e o web troca o `confirmacao` da mensagem em memória. Sem som nem notificação.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Atividade do pedido:** cada pessoa da lista ganha uma atividade **"pediu que você confirme a leitura"** (tipo 5, ATV-01), pendente até confirmar. Confirmar (`POST /mensagem/confirmar`) a conclui e o servidor manda o **WS 61** a quem confirmou, para o contador cair (ATV-02).

**MSG-22 🆕 `d202c06` Bolha de postagem com respostas embaixo** (`BolhaPostagem.vue`, store `chat`) — 🆕 `e33ecba`: cartão na largura do chat; a resposta é escrita no **campo de mensagem**, com qualquer conteúdo; foto de quem respondeu (ver o fim do item)
- Classificação: `mensagem.postagem` (`{titulo, respostas, nao_lidas, ultimas: Mensagem[]}`) → `Postagem` (MSG-07). As respostas **não entram no fluxo** da conversa nem na pesquisa (servidor): ficam só no cartão.
- Cartão (até 32rem; 🆕 `e33ecba` largura toda do chat): cabeçalho **"Postagem · Você"** ou **"Postagem · <remetente>"** e o título (se tem); o conteúdo (texto e anexos, como numa mensagem) com hora/status; a postagem continua com o menu de ações e as reações de uma mensagem comum.
- Rodapé das respostas:
  - contagem **"Nenhuma resposta ainda"** / **"1 resposta"** / **"N respostas"**;
  - selo **"1 nova"** / **"N novas"** (`nao_lidas`);
  - **"Ver todas as N"** (só quando há mais respostas que as 2 últimas que vêm junto) / **"Mostrar menos"**; abrir busca todas com `GET /postagem/respostas?id=<postagem>` → `Mensagem[]` e refaz a contagem;
  - cada resposta: inicial, **"Você"** ou o nome, hora `HH:MM` e o conteúdo; resposta apagada mostra **"Mensagem apagada"**;
  - (até `d202c06`; 🆕 `e33ecba` **saiu**, ver abaixo) campo **"Responder à postagem"** (Enter envia, Shift+Enter quebra a linha; botão "Responder"): `PUT /mensagem {conversa_id, conteudos:[texto], postagem_id}` → relê as respostas e as conversas; erro "Erro ao responder" (ou a mensagem do servidor) embaixo.
- **Vistas:** com a postagem na tela, a conversa aberta e a janela em foco, havendo `nao_lidas`, zera o selo e chama `POST /postagem/visualizar {postagem_id}` (e recarrega `GET /conversas`).
- **Tempo real:** WS 64 (`PostagemAtualizada`) `{conversa_id, postagem_id, usuario_id}` → recarrega as conversas; se a postagem está em memória, relê as respostas; se não estou vendo a conversa (aberta e em foco), ela não está arquivada e não fui eu → som e notificação "<nome> respondeu a uma postagem" (NOT-02).
- **Atividade:** a resposta vira atividade "respondeu sua mensagem" para quem postou e para quem já respondeu, apontando para a **postagem** (o servidor grava; ATV-01 abre a postagem).
- 🆕 `e33ecba` **Mudanças:**
  - **Largura:** o cartão ocupa a **largura toda do chat** (era até 32rem), seja de quem for; o botão ⋯ do menu de ações fica **dentro do cartão**, no canto de cima à direita, e o menu abre alinhado pela direita (ENV-20).
  - **Responder:** o campo de dentro do cartão (e o botão "Responder", o erro "Erro ao responder") **saiu**. No lugar, o botão **"Responder à postagem"** (vira **"Respondendo no campo de mensagem"**, em destaque, enquanto vale para este cartão) leva a resposta ao **campo de mensagem** (`chat.responderPostagem`): tira o "respondendo a..." e a postagem pendente, põe acima do campo a faixa **"Respondendo à postagem de <remetente>"** com o título da postagem (ou o resumo dela) e o × **"Não responder à postagem"**, e foca o campo. Trocar de conversa desliga.
  - **Envio:** a próxima mensagem do campo, **com qualquer conteúdo** (texto, anexos, áudio, figurinha, código), vai com `postagem_id`; não entra no fluxo (sem mensagem otimista no chat) e, gravada, o web relê as respostas da postagem. Erro aparece acima do campo, como qualquer envio.
  - **Foto de quem respondeu:** cada resposta mostra a foto (dos membros do grupo, `GET /conversa/usuarios`, carregados quando o cartão aparece; senão dos contatos ou a minha) ou a inicial.

**MSG-23 🆕 `f351190` Marcas das pastas na bolha** (`MessageBubble.vue`, `MessageList.vue#pastasDaMensagem`)
- Só na conversa direta com as pastas carregadas (CON-17). Até **3** marcas por mensagem: as pastas de `mensagem.pastas` que estão na lista, na cor de cada uma (sem cor, a principal do tema).
- **Mensagem enviada** (salva, não chamada): bolinhas de **12 px**, opacidade 0,7, **sobrepostas** (−4 px entre elas) e centralizadas **atrás do ✓** do status (que tem 14 px), sem mudar a altura da linha; não são clicáveis (o clique continua abrindo o detalhe do status). O status ganha o `title` **"Na pasta <nome1, nome2>"**.
- **Mensagem recebida** (e a bolha de chamada, que não tem status): bolinhas de **10 px**, opacidade 0,8, lado a lado (2 px), logo depois da bolha, na altura em que fica o status; `title` **"Pasta <nome>"**; o clique **abre a pasta** (MSG-24).
- Dentro da pasta aberta (MSG-24) não há marcas.

**MSG-24 🆕 `f351190` Pasta aberta por cima do chat** (`ChatDaPasta.vue`, `stores/pastas.ts`, `App.vue`; servidor `ac710eb`)
- **Abre** pelo clique na pasta do painel (CON-17) ou na marca da bolha recebida (MSG-23). Cobre a **área das mensagens**: o chat fica como estava por baixo e o **campo de mensagem some** (escondido, com o rascunho guardado). Aqui não se envia nada.
- **Barra do topo:** **"Voltar ao chat"** (seta para a esquerda; `title` **"Voltar ao chat (Esc)"**), o ícone na cor da pasta, o nome e **"· 1 mensagem"** / **"· N mensagens"** (o número da lista), mais **" · compartilhada"** quando for.
- **Lista:** só as mensagens da pasta, com o separador de dia (MSG-02) e o espaçamento por remetente, como numa conversa direta. Carregando: **"Carregando mensagens"**; vazia: **"Nenhuma mensagem nesta pasta. No chat, arraste ao lado das mensagens para selecioná-las e solte-as aqui no painel."**.
- **Paginação:** `GET /pasta/mensagens?id=<pasta>&antes=0` → `Mensagem[]`, as **50** mais recentes, **da mais antiga para a mais nova**; abre rolada no fim. A **80 px do topo**, `antes=<id da mais antiga carregada>` traz as 50 anteriores (sem repetir), mantendo a posição; acabou quando vierem menos de 50. O servidor ordena por `coalesce(visivel_em, inserida)` e id, e não mostra a agendada de outra pessoa que ainda não saiu.
- **Menu da mensagem** (clique direito ou ⋯, ENV-20): as reações em cima, como no chat; depois **"Ver na conversa"** (o primeiro item), "Responder", "Encaminhar" e "Copiar"; por último **"Tirar da pasta"**. **Sem** "Pedir confirmação de leitura" e **sem** "Ocultar".
  - **"Ver na conversa"** fecha a pasta e vai à mensagem no chat (MSG-06). Clicar numa citação dentro da bolha faz o mesmo com a mensagem citada.
  - **"Responder"** fecha a pasta e deixa a resposta pendente no campo do chat (ENV-06). **"Encaminhar"** abre o mesmo encaminhamento do chat (ENV-08).
  - **Reagir:** como no chat (`PUT /mensagem/reacao`); como a cópia da pasta é outra, o web relê só essa mensagem (`GET /mensagens?conversa=<id>&mensagemreferencia=<id>&mensagensprevias=0&mensagensseguintes=0`). Erro: "Não foi possível reagir".
  - **"Tirar da pasta"** → `DELETE /pasta/mensagens` **com corpo JSON** `{pasta_id, mensagens: [id]}` → `{pasta_id, tiradas}`; a mensagem sai da lista, as marcas do chat são atualizadas e a contagem é relida (`GET /pastas`). Erro: **"Não foi possível tirar da pasta"** (fallback "Erro ao tirar da pasta"). Download que falha: "Não foi possível baixar".
- **Tirar várias:** a mesma seleção do chat (ENV-29: arrastar ao lado das bolhas, Ctrl + clique), só nesta tela e sem arrastar para outra pasta. A barra (abaixo da do topo) mostra **"1 selecionada"** / **"N selecionadas"**, **"Tirar da pasta"** (vermelho; tira todas numa chamada) e × **"Limpar seleção (Esc)"**. Trocar de pasta limpa a seleção.
- **Esc:** com seleção, limpa a seleção; sem, **volta ao chat** (ignorado com um diálogo aberto).
- Também fecha quando a pasta some da lista (WS 67, CON-17) e ao trocar de conversa.

**MSG-25 🆕 `f351190` Localização na conversa: bolha, mapa e visualizador** (`BolhaLocalizacao.vue`, `ConteudoLocalizacao.vue`, `MapaLeaflet.vue`, `VisualizadorLocalizacao.vue`, `utils/localizacao.ts`; servidor `ac710eb`)
- **Classificação** (MSG-07): conteúdo único tipo 9 sem referência → **`Localizacao`** (`BolhaLocalizacao`): cartão com uma moldura de 2 px na cor do balão (enviada ou recebida), o nome do remetente em cima (grupo, recebida), o mapa e a hora embaixo. Com referência, vai para a bolha de referência; com texto junto, para a padrão, e o mapa aparece no meio dos outros conteúdos. O formato do conteúdo está no ENV-30.
- **Conteúdo** (`ConteudoLocalizacao`, carregado sob demanda junto com o Leaflet; 288 px de largura):
  - **mapa** de 160 px de altura, **não interativo** (só imagem, para não brigar com a rolagem), sempre o de ruas; o clique (`title` **"Ampliar mapa"**) abre o visualizador;
  - **Local:** o nome do ponto (até 2 linhas; sem nome, as coordenadas);
  - **Trajeto:** uma linha por ponto, com o selo **"A"** (verde) / **"B"** (vermelho) e o nome (cortado; inteiro no `title`), e embaixo a descrição (ex.: **"Trajeto · 12,3 km · 18 min de carro"**);
  - à direita, o link **"Abrir no Google Maps"** (nova aba; no celular abre o app);
  - conteúdo que não é uma localização válida: só o texto **"Localização"**.
- **Mapa** (`MapaLeaflet`):
  - **ruas:** `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (até o nível 19), atribuição **"© OpenStreetMap"** (link para `https://www.openstreetmap.org/copyright`), sem o prefixo do Leaflet;
  - **satélite** (só no mapa interativo): `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}` (níveis nativos até 18, ampliado até 19), atribuição **"Imagens © Esri, Maxar, Earthstar Geographics"**, com as camadas `…/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}` e `…/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}` por cima (ruas e nomes), nativas **só até o nível 16** e ampliadas daí em diante, porque os níveis maiores da Esri vêm deslocados da imagem. Botão no canto de cima à direita: **"Satélite"** (`title` "Ver a imagem de satélite") / **"Mapa"** (`title` "Ver o mapa de ruas");
  - **tema escuro:** o mapa de ruas recebe o filtro `invert(1) hue-rotate(180deg) brightness(0.95) contrast(0.9)`; o satélite não;
  - **marcadores:** alfinete de 28×38 px (a ponta embaixo), contorno branco; Local na cor principal, com um círculo branco; Trajeto **"A"** verde e **"B"** vermelho, com a letra branca; `title` = o nome;
  - **linha:** a rota decodificada (espessura 5, opacidade 0,85, cor principal) ou, sem rota, uma **reta tracejada** entre os pontos (espessura 3, traço `6 8`);
  - **enquadramento:** sem pontos, Brasília (`-15.78, -47.93`) com zoom 4; um ponto, zoom 16; mais, enquadra os pontos e a linha (margem de 28 px, zoom máximo 17).
- **Visualizador** (`VisualizadorLocalizacao`; quase a tela toda, até 1024 px de largura e 90% da altura; fecha com ×, Esc ou clique fora):
  - título: o nome do local, ou os nomes unidos por **" → "**;
  - subtítulo: as coordenadas (local) ou a descrição do trajeto;
  - **"Abrir no Google Maps"**; mapa interativo (arrastar, zoom, satélite);
  - no trajeto, embaixo, "A" e "B" com o nome inteiro.
- **Fórmulas** (`utils/localizacao.ts`):
  - coordenadas: `lat.toFixed(5) + ", " + lng.toFixed(5)` → "-23.50000, -46.60000" (ponto decimal);
  - distância: abaixo de 1000 m, `round(m) + " m"` ("850 m"); senão quilômetros com `toLocaleString('pt-BR')`, no máximo **1 casa abaixo de 100 km** e nenhuma a partir daí ("12,3 km", "150 km");
  - tempo: `min = max(1, round(s / 60))`; abaixo de 60, "N min"; senão "H h M min", ou "H h" com hora cheia ("1 min" para 30 s; "1 h 30 min" para 5400 s);
  - nome do ponto: `nome.trim()` ou, sem ele, as coordenadas;
  - resumo (`resumoLocalizacao`, usado na peça do campo): local = o nome; trajeto = os nomes unidos por " → " e, com distância, " · <distância>" (ex.: "Casa → Trabalho · 12,3 km");
  - descrição do trajeto (`descricaoTrajeto`): sem distância, **"Trajeto"**; sem duração, **"Trajeto · <distância> em linha reta"**; com as duas, **"Trajeto · <distância> · <tempo> de carro"**;
  - linha reta: haversine, raio 6.371.000 m;
  - rota: polyline do Google/OSRM, precisão 1e5, pares `[lat, lng]`.
- **"Abrir no Google Maps"** (`linkMapa`):
  - local: `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>`;
  - trajeto: `https://www.google.com/maps/dir/?api=1&origin=<lat>%2C<lng>&destination=<lat>%2C<lng>&travelmode=driving`.
- **Resumos:** **"Localização"** na notificação (NOT-02), no resumo da citação (`messageReferences.ts`, MSG-19), na prévia das atividades (ATV-01) e **"[Localização]"** no painel do chat da chamada (CHA-18). A barra "respondendo a" e o encaminhar (`resumoMensagem`, ENV-06/ENV-08) não mudaram e mostram "Arquivo". No servidor, a prévia da conversa (`ultima_mensagem_texto`) e o push dizem "localização".

### 3.4 ENV — Mensagens: composição e ações

**ENV-01 Enviar texto** — 🆕 o campo agora é rico (ENV-21); só texto continua indo como antes (`MessageInput.vue`, `stores/chat.ts#enviarMensagemComConteudos`)
- Campo "Digite uma mensagem", cresce até 120 px. **Enter envia**; Shift+Enter quebra linha. Botão "Enviar" aparece quando há conteúdo (texto, fila ou resposta); sem conteúdo aparece o microfone.
- Envio otimista: mensagem com `id = -Date.now()`, `enviando:true` aparece na hora; após `PUT /mensagem` troca pelo id do servidor; erro remove a otimista e mostra o erro acima do campo.
- API: `PUT /mensagem {conversa_id, conteudos:[{ordem, tipo, conteudo}], mensagem_referencia?:{tipo, origem_mensagem_id}, visivel_em?: ISO}` → `{id, conversa_id, usuario_id}`.
- 🆕 Campos opcionais novos no mesmo `PUT /mensagem`: `pede_confirmacao: true` (`769bd7d`, ENV-24), `postagem: {titulo: string | null}` (`d202c06`, cria uma postagem, ENV-26) e `postagem_id` (`d202c06`, resposta a uma postagem, MSG-22). Só vão quando usados. 🆕 `e33ecba` (servidor `6d20ff7`) **`confirmacao_usuarios?: number[]`** junto de `pede_confirmacao`: quem precisa confirmar (ENV-24); sem a lista, todos os outros membros. Lista vazia ou com quem não é membro → 400 "Escolha quem precisa confirmar entre os membros da conversa.".
- Regras: texto `trim()`; pode enviar só a resposta (sem texto) — ver ENV-07. Após enviar, rola ao fim e recarrega a lista de conversas.
- 🆕 `9c6e8a6` **Enviar com um trecho antigo carregado** (`useScrollManager.ts#rolarParaFinalAoEnviar`; no app, no popup e no chat completo da chamada): depois de ir até uma mensagem antiga (MSG-06), a lista não tem as mais recentes e a enviada ficaria no fim desse trecho. Ao enviar, o web **recarrega as 80 recentes** (a que está sendo enviada fica) e só então rola ao fim.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Permissões de uso no `PUT /mensagem`** (GER-09): o servidor confere o que a mensagem pede e responde 403 "Você não tem permissão para: <descrição>." (por exemplo "… enviar imagens."):
  - texto com emoji (`\p{Extended_Pictographic}`) → `emoji`; imagem (2) → `imagem`; áudio (4 e 5) → `audio`; figurinha (7) → `figurinha`; arquivo (3) → `video` ou `arquivo`, pela extensão do anexo (`mp4, webm, ogg, mov, m4v, mkv` = vídeo);
  - resposta (`mensagem_referencia.tipo` 1) → `responder`; `postagem` → `postagem`; `pede_confirmacao` → `confirmacao`; `visivel_em` → `agendar`;
  - **encaminhada** (`mensagem_referencia.tipo` 2, inclusive o "Responder no privado") pede **só** `encaminhar`: os conteúdos são de outra mensagem.

**ENV-02 Atalhos de emoji no texto** (`utils/emojiAtalhos.ts`)
- `:)`/`:-)`→🙂, `:D`/`:-D`→😃, `xD`/`XD`→😆, `;)`/`;-)`→😉, `:(`/`:-(`→🙁, `:'(`→😢, `:P`/`:p`→😛, `:O`/`:o`→😮, `:|`→😐, `:/`→😕, `:*`→😘, `<3`→❤️, `\o/`→🙌.
- Só como palavra solta (início/espaço antes e espaço/quebra depois, ou no fim ao enviar); nunca dentro de código (crases).

**ENV-03 Seletor de emoji** (`EmojiPicker.vue`) — 🆕 `d202c06`: **todos os emojis**, por categoria e com pesquisa; as listas fixas antigas saíram
- (Até `e8d82cb`) Categorias "Populares", "Rostos", "Gestos", "Objetos" (listas fixas); tooltip com nome em português (`utils/emojiNomes.ts`). Insere no fim do texto e devolve o foco. **Histórico: essas listas não existem mais.**
- 🆕 `d202c06` **Catálogo:** `public/emojis/pt.json` (estático da página, baixado na primeira vez que o seletor abre; `composables/useEmojis.ts`), gerado por `scripts/gerar-emojis.ts` a partir do `emojibase-data` (pt). **1644 emojis**, cada um `[emoji, nome, palavras-chave, versão do Unicode]`. **Sem tons de pele** (só o amarelo padrão), **sem bandeiras** (grupo 9) e **sem componentes** (grupo 2). Sem o **FE0F que sobra**: o seletor de variação só fica quando o caractere sozinho seria texto (ex.: "❤️"); "👍" vai sem ele, igual às reações rápidas e à digitação, para as reações não se dividirem.
- **Layout** (320 px): campo **"Pesquisar emoji"**; barra de categorias com ícone e tooltip (a atual marcada ao rolar; clicar leva até ela); grade de 8 colunas, 260 px de altura com rolagem. Categorias, nesta ordem: **"Recentes"** 🕒, **"Carinhas e emoções"** 😀, **"Pessoas e corpo"** 👋, **"Animais e natureza"** 🐶, **"Comida e bebida"** 🍔, **"Viagens e lugares"** ✈️, **"Atividades"** ⚽, **"Objetos"** 💡, **"Símbolos"** ❤️. Tooltip de cada emoji = nome do catálogo (fallback `emojiNomes`).
- **Recentes:** até **32**, o último usado primeiro, salvos no navegador (`conversa.emojisRecentes`); sem nada usado, começam por 16 populares (😂 ❤️ 😍 🤣 😊 🙏 😭 😘 👍 😅 🔥 🥰 😎 💕 🎉 ✨).
- **Pesquisa:** sem acento e sem diferenciar maiúsculas; cada palavra digitada precisa começar uma palavra do nome, das palavras-chave ou do nome do app; até 200 resultados. Textos "Nenhum emoji encontrado" e "Carregando os emojis...". Sem o catálogo (sem rede), ficam os Recentes e tenta de novo na próxima abertura.
- **Suporte do sistema** (`utils/suporteEmoji.ts`): desenha num canvas um emoji representante de cada versão do Unicode (16, 15.1, 15, 14, 13.1, 13, 12, 11) e esconde os emojis de versão mais nova que a primeira que aparece colorida; se o teste falha, mostra tudo.
- O mesmo componente serve ao campo de mensagem (com a aba "Figurinhas", ENV-04), ao "Mais emojis" das reações (ENV-17) e ao emoji do grupo (CON-15). 🆕 `e33ecba` Também ao emoji do grupo novo (CON-07).
- 🆕 `8b43965` **Desempenho:** os botões de cada categoria só são criados quando ela chega perto da vista (`IntersectionObserver`, margem de 300 px) e, depois de aberto, as demais uma por vez nos intervalos livres (`requestIdleCallback`); até lá a categoria ocupa a mesma altura (linhas de 8, 38 px cada). A primeira abertura não trava mais. Sem mudança visível.
- 🆕 `9c6e8a6` **Permissões de uso** (GER-09) no botão "Emoji" do campo: sem `emoji` **e** sem `figurinha`, o botão some; sem `emoji` e com `figurinha`, o seletor abre **só na aba "Figurinhas"**, sem as abas em cima (prop `soFigurinhas`). Os atalhos de texto (ENV-02) continuam virando emoji; o servidor recusa o envio com 403 "Você não tem permissão para: enviar emojis no texto das mensagens." (ENV-01).

**ENV-04 Figurinhas (Lottie)** — 🆕 com o campo **vazio** a figurinha é enviada na hora, sozinha; com algo escrito ela **entra no campo** no ponto do cursor e vai junto, na ordem (ENV-21)
- Aba "Figurinhas" no seletor do campo de mensagem (abre sempre em "Emojis"). Pacotes: **Básico** (coracao, sorriso, risada, estrela, feito, festa, sono, pontinhos), **Rostos** (piscadinha, apaixonado, beijo, legal, surpreso, triste, bravo), **Coisas** (joinha, fogo, coracao-partido, presente, bolo, balao, cafe, sol, chuva).
- Clicar **envia na hora**, sozinha (o texto digitado fica no campo; pode ir como resposta se houver resposta pendente).
- Conteúdo tipo 7 com o identificador `pacote/nome`; animação em `/figurinhas/<pacote>/<nome>.json` (servida pela página, não pela API). Bolha 160 px; no picker 64 px; na resposta 120 px. Toca só quando visível; com "reduzir movimento" fica parada no quadro do meio. Falha → texto "Figurinha". Nome amigável em `nomeFigurinha()`.
- Arquivos: `utils/figurinhas.ts`, `FigurinhaLottie.vue`, `BolhaFigurinha.vue`, `public/figurinhas/**`, `scripts/gerar-figurinhas.ts`.
- 🆕 `d202c06` **Só o pacote "Noto"**: os pacotes Básico, Rostos e Coisas (e `scripts/gerar-figurinhas.ts`) foram **apagados**. O pacote `noto` (emojis animados do Google em Lottie, `utils/figurinhasNoto.ts`, baixado por `scripts/baixar-figurinhas-noto.ts`) tem **55 figurinhas** `noto/<nome>`: chorando-de-rir, rolando-de-rir, sorriso, ufa, piscadinha, apaixonado, cheio-de-amor, beijinho, legal, deslumbrado, festejando, abraco, ops, pensando, desconfiado, revirando-os-olhos, de-cabeca-para-baixo, envergonhado, por-favorzinho, triste, chorando, bravo, assustado, cabeca-explodindo, bocejando, dormindo, doente, morri, palhaco, fantasma, joinha, negativo, palmas, aleluia, por-favor, tchau, forca, combinado, paz, de-olho, coracao, coracao-partido, fogo, cem, brilhos, festa, confete, presente, bolo, foguete, ideia, raio, arco-iris, cafe, brinde (nomes amigáveis no catálogo, ex.: "Chorando de rir", "Abraço", "Arco-íris").
- 🆕 `d202c06` Na aba "Figurinhas", o título do pacote "Noto" vem com o crédito que a licença pede, como link para a CC BY 4.0: **"Animated Noto Emoji © Google, CC BY 4.0"**. Grade de 4 colunas, 64 px.
- 🆕 `d202c06` Figurinha de pacote apagado (`basico/…`, `rostos/…`, `coisas/…`) não carrega mais e aparece como o texto **"Figurinha"**; `nomeFigurinha()` também devolve "Figurinha" para id desconhecido.
- 🆕 `9c6e8a6` A aba "Figurinhas" passa a ter **a mesma altura** da de emojis (342 px: pesquisa 45 + categorias 37 + lista 260), para o seletor não mudar de tamanho ao trocar de aba. Ela só aparece com a permissão de uso `figurinha`; sem `emoji`, é a única (ENV-03, GER-09).

**ENV-05 Menções no campo**
- Digitar `@` + texto abre lista com até 6 contatos (nome/login); ↑/↓ navegam, Enter/Tab confirmam, Esc fecha; clique também. Insere `@Nome` destacado no campo (camada de realce atrás do textarea); no envio vira `@[Nome](id)`. Texto colado com `@[Nome](id)` é convertido para `@Nome` com o id guardado. "@Ana" não casa com "@Anabela".
- Arquivos: `utils/mencoesTexto.ts`, `MencaoDropdown.vue`.

**ENV-06 Responder**
- Ação "Responder" → barra acima do campo com nome e resumo (`resumoMensagem`) e botão ×; foco no campo.
- Envio leva `mensagem_referencia {tipo:1, origem_mensagem_id}`. Mensagem otimista já mostra a citação.

**ENV-07 Responder no privado** (só em grupo, em mensagem de outro)
- Abre/cria a conversa direta com o remetente e deixa a mensagem como **encaminhada** pendente (barra "Encaminhando de <remetente>").
- No envio: `mensagem_referencia {tipo:2}` e os conteúdos da original vão **antes** do texto digitado (ordens 1..n, depois o texto).

**ENV-08 Encaminhar** (`ForwardMessageModal.vue`)
- "Encaminhar mensagem" / "Escolha uma conversa ou contato para receber esta mensagem." / "Mensagem selecionada" (resumo) / campo "Buscar destino" ("Nome da conversa ou contato").
- Destinos: todas as conversas exceto a de origem (rótulos "Grupo existente" / "Conversa direta existente", etiqueta "Conversa") + contatos sem conversa direta ("Novo chat direto"/email, etiqueta "Contato"). "Nenhum destino encontrado.".
- API: `PUT /mensagem` no destino com os conteúdos copiados (reordenados 1..n) e `mensagem_referencia {tipo:2, origem_mensagem_id}`; contato sem conversa → cria a direta antes. Depois abre o destino.

**ENV-09 Colar código e colar texto longo** (`MessageInput.vue#aoColarNoChat`, `utils/codeBlocks.ts`)
- Texto colado com **mais de 10 linhas** → abre a janela "Inserir código" já preenchida, com linguagem detectada (`markdown` se contém ` ``` `; `texto` se é desenho ASCII; senão heurística). **Cancelar cola o texto como estava** (a janela é só sugestão). 🆕 `e33ecba`: **"Inserir na mensagem"** põe o código colado no campo como **peça** (ENV-28), no ponto do cursor; "Enviar" manda só o código, na hora (ENV-10).
- Texto com cara de código (≥ 2 linhas, metade com cara de código + sinal forte; ou ≥ 50% desenho ASCII; ou ≥ 50% comandos de terminal) → entra como texto e logo vira bloco cercado com a linguagem detectada (Ctrl+Z volta ao texto cru).
- Cerca = uma crase a mais que a maior sequência de crases do código (mín. 3).
- Detecção de linguagem: JSON válido; regras para html, sql, pascal, csharp, typescript, python, javascript, css, bash; senão highlight.js (relevância ≥ 3) ou `texto`.

**ENV-10 Janela "Inserir código"** (`CodigoModal.vue`)
- Seletor de linguagem (`texto, javascript, typescript, python, sql, json, html, css, bash, csharp, pascal, markdown, mermaid` + a detectada, se fora da lista), editor CodeMirror (tema claro/escuro), placeholder "Cole ou escreva seu código aqui...", "Cancelar"/"Enviar", **Ctrl/Cmd+Enter envia**. Enviar **envia imediatamente** a mensagem com o bloco cercado (substitui o texto do campo).
- 🆕 `e33ecba` **Inserir ou enviar:**
  - título **"Inserir código"** (ou **"Editar código"**, aberto pela peça do campo, ENV-28);
  - botões **"Cancelar"**, **"Inserir na mensagem"** (contorno; não aparece editando) e **"Enviar"** (cheio; editando vira **"Salvar"**); os dois desabilitados sem código;
  - **"Inserir na mensagem"** vira a peça `codigo` no campo (ENV-28), que vai junto com o resto; editando, **"Salvar"** troca a peça;
  - **"Enviar"** manda **só o bloco cercado, na hora**, numa mensagem só dele, e **não limpa mais o campo**: o que já estava escrito fica lá (o que estiver pendente acima do campo — resposta, confirmação, postagem — vai com ele, porque o envio passa pelo mesmo `chat.enviarMensagemComConteudos`);
  - **Ctrl/Cmd+Enter agora insere** (ou salva, editando), em vez de enviar.

**ENV-11 Colar imagem** — imagem na área de transferência entra na fila de anexos como `print-<timestamp>.<ext>` (ext pelo mime).

**ENV-12 Teclado do campo**
- Tab insere 4 espaços (Shift+Tab sai do campo).
- Qualquer letra digitada fora de um campo, sem modal por cima, foca o campo de mensagem no fim do texto.
- Campo recebe foco ao trocar de conversa e ao iniciar resposta.
- 🆕 `769bd7d` **Ctrl/Cmd+Enter volta a enviar** (tinha parado com o editor Tiptap de `e8d82cb`); Enter continua enviando.
- 🆕 `f798e9c` Com uma sugestão da IA à mostra (ENV-25), **Tab aceita** a sugestão (no lugar dos 4 espaços) e **Esc** a descarta.
- 🆕 `f351190` A letra digitada fora do campo põe o foco nele **no mesmo `keydown`** (seleção no fim e `view.focus()` direto no ProseMirror), e a própria letra entra no campo; antes o foco esperava um quadro e a primeira letra se perdia.

**ENV-13 Agendar mensagem** (`AgendarMensagemModal.vue`) — 🆕 `7322e83`: depois de agendar, a mensagem vai para o relógio ao lado do microfone, não para o chat (§12)
- Botão "Agendar mensagem" (aparece com conteúdo). Modal "Agendar mensagem": "Data" (calendário próprio `DateInput`) e "Hora" (`HH:MM`); padrão **amanhã 08:00**.
- Validações: "Data/hora inválida", "O envio deve ser no mínimo 5 minutos no futuro", "O envio não pode ser mais de 1 ano no futuro". Botões "Cancelar" / "Agendar".
- Envia o conteúdo atual (texto + fila) com `visivel_em` em ISO (UTC). O autor vê a mensagem na hora com o selo (MSG-17); destinatários só depois.
- 🆕 `9c6e8a6` O botão "Agendar mensagem" só aparece com a permissão de uso `agendar` (GER-09); o servidor recusa `visivel_em` sem ela (403).

**ENV-14 Cancelar mensagem agendada** — 🆕 `7322e83`: agora pela lista "Mensagens agendadas" do relógio (§12), com a mesma confirmação
- Na própria mensagem ainda futura, a ação "Ocultar" vira confirmação "Cancelar mensagem agendada" / "Ela não será enviada." / "Cancelar envio" / "Voltar".
- API: `DELETE /mensagem?id=` — para agendada não enviada a resposta **não** traz `excluida_em` e a mensagem é removida da lista.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Pegadinha:** o `DELETE /mensagem` confere a permissão de uso `ocultar` **antes** de ver se a mensagem é agendada; sem ela, cancelar a agendada também dá 403 "Você não tem permissão para: ocultar as próprias mensagens.". O web não esconde o "Cancelar" da lista de agendadas.

**ENV-15 Digitando** (`stores/chat.ts`)
- Envio: ao digitar texto não vazio, `POST /conversa/digitando {id: conversaId}`, no máximo 1 a cada 2,5 s (o último é adiado, não perdido). Ao enviar texto, reseta o throttle.
- Recepção: WS tipo 4 `{conversa_id, usuario_id}`; ignora o próprio; expira 4 s sem novo aviso; some quando chega mensagem daquela conversa.
- Exibição: lista (três pontinhos), cabeçalho e chip acima do campo (só se estiver no fim do chat; não durante gravação) + linha luminosa na borda do campo. Textos: direta "Digitando..."; grupo "Ana está digitando...", "Ana e Beto estão digitando...", "Ana, Beto e Caio estão digitando...", "Ana, Beto, Caio e outras N pessoas estão digitando...". Nome de quem não é contato: "Usuário #id".

**ENV-16 Gravando áudio (indicador)**
- Envio: ao começar a gravar e a cada 2,5 s durante a gravação, `POST /conversa/gravando {id}` (mesma regra de throttle).
- Recepção: WS tipo 5 `{conversa_id, usuario_id}`, expira 4 s. Cor vermelha; textos "Gravando áudio..." / "Ana está gravando áudio..." / "… estão gravando áudio...". Gravando tem prioridade sobre digitando na exibição.
- 🆕 `f351190` Agora também **na lista de conversas** (antes só digitando aparecia lá): três pontinhos vermelhos com o `title` "Gravando áudio...", no lugar dos de digitando (CON-01). Não conta quando só eu estou gravando.

**ENV-17 Reações**
- Menu de ações: linha 1 👍 ❤️ 😂 😮, linha 2 😢 👏 🔥 + "Mais emojis" (abre o seletor completo). 🆕 `d202c06`: o "Mais emojis" abre o seletor novo, com todos os emojis, Recentes e pesquisa (ENV-03).
- Toggle: reagir de novo com o mesmo emoji remove. Atualização otimista; erro recarrega a conversa.
- Abaixo da bolha: chips `emoji contagem` (destacado se eu reagi; clicar alterna); tooltip lista quem reagiu com foto e hora (hoje `HH:MM`, senão `dd/MM HH:MM`).
- API: `PUT /mensagem/reacao {mensagem_id, emoji}` → `{mensagem_id, emoji, acao}`.
- 🆕 `eaa8bac` **Limite:** até **5 emojis diferentes por pessoa** na mesma mensagem. O web confere antes da reação otimista e mostra o diálogo "Não foi possível reagir" / "Você já reagiu com 5 emojis nesta mensagem." (o servidor confere de novo). Na bolha, **5 chips à mostra** e o resto num chip **"+N"** (destacado se eu reagi em algum); passar o mouse lista os demais (emoji, contagem, nomes) e clicar num deles alterna.
- WS tipo 7 `{conversa_id, mensagem_id, emoji, acao, usuario_id}` — `acao === 'remove'` remove; outro valor adiciona.
- Mensagem oculta não mostra reações.
- 🆕 `9c6e8a6` Sem a permissão de uso `reagir` (GER-09), o menu de ações perde as duas linhas de reações rápidas (e o "Mais emojis") e o Ctrl + clique direito não abre nada. Os chips embaixo da bolha continuam clicáveis: o servidor responde 403 "Você não tem permissão para: reagir às mensagens com emoji." e o web mostra o diálogo "Não foi possível reagir".
- 🆕 `9c6e8a6` (servidor) A reação vira atividade de quem recebeu, pendente até ele **ver a própria mensagem na tela** (ATV-05).

**ENV-18 Ocultar mensagem** (ex-"Excluir", commit `51a0cd7`)
- Ação "Ocultar" só nas **próprias** mensagens não ocultas, **sem prazo**. Confirmação: título "Ocultar mensagem", texto "Ela continua na conversa, marcada como oculta.", botão "Ocultar" (perigo; foco inicial em "Cancelar").
- API: `DELETE /mensagem?id=` → com `excluida_em` (mensagem fica marcada em todas as listas em memória; recarrega `GET /conversas` para a prévia) ou sem (agendada → removida).
- Erro: aviso "Não foi possível ocultar".
- Os demais participantes recebem via WS 3 (MSG-10).
- 🆕 `9c6e8a6` "Ocultar" só aparece com a permissão de uso `ocultar` (GER-09); o servidor responde 403 "Você não tem permissão para: ocultar as próprias mensagens." (vale também para cancelar a agendada, ENV-14).

**ENV-19 Copiar**
- Ação "Copiar": se o menu foi aberto por clique direito **numa imagem**, copia aquela imagem; **no texto**, copia o texto; pelo botão (sem alvo): se a mensagem tem imagem copia a primeira imagem, senão todos os textos unidos por `\n`.
- Imagem é copiada como PNG (converte via canvas se não for PNG) — `utils/copiarImagem.ts`.

**ENV-20 Menu de ações da mensagem** (`MensagemAcoes.vue`) — 🆕 `7322e83`/`e8d82cb`: Ctrl + clique direito abre só as reações rápidas (com "mais emojis"); toque longo no celular abre o menu completo (§12)
- Abre pelo botão ⋯ ao passar o mouse na bolha (acompanha a rolagem em bolhas altas) ou clique direito na bolha. Só para mensagens salvas (id > 0), não para chamada nem oculta. Abre abaixo do botão ou acima se não couber; um menu por vez; fecha ao rolar ou clicar fora.
- Itens, nesta ordem: reações rápidas; **Responder**; **Responder no privado** (grupo e mensagem de outro); **Encaminhar**; **Copiar**; **Ocultar** (própria e não oculta).
- 🆕 `e33ecba` Item novo **"Pedir confirmação de leitura"** (ícone de ✓ num círculo) **depois de "Copiar" e antes de "Ocultar"**: só na **minha** mensagem salva (id > 0), não oculta, que **ainda não pede** confirmação, já enviada (`visivel_em ?? inserida` não futura) e há **menos de 24 h** (ENV-27).
- 🆕 `e33ecba` Na **postagem** (MSG-22), que ocupa a largura do chat, o botão ⋯ fica **dentro do cartão**, no canto de cima à direita, e o menu abre alinhado pela direita.
- 🆕 `9c6e8a6` **Itens por permissão de uso** (GER-09):
  - reações rápidas e "Mais emojis": `reagir`;
  - "Responder": `responder`;
  - "Responder no privado": `responder` **e** `encaminhar`;
  - "Encaminhar": `encaminhar`;
  - "Pedir confirmação de leitura": `confirmacao` (além das regras de ENV-27);
  - "Ocultar": `ocultar`;
  - "Copiar" não pede permissão.
- 🆕 `f351190` **Na pasta aberta** (MSG-24, prop `naPasta`): item novo **"Ver na conversa"** (ícone de balão) **antes de "Responder"**, e **"Tirar da pasta"** (ícone de pasta com "−", com uma linha em cima) **no fim**; lá somem "Pedir confirmação de leitura" e "Ocultar". No chat normal, os dois itens novos não aparecem.

(ENV-21 e ENV-22 estão na §10; ENV-23 na §12.)

**ENV-24 🆕 `769bd7d` Pedir confirmação de leitura** (`AnexoPopup.vue`, `MessageInput.vue`, `chat.pedirConfirmacao`) — 🆕 `e33ecba`: em grupo, escolher quem precisa confirmar (ver o fim do item); pedir depois do envio é o ENV-27
- Entrada: botão "+" → **"Confirmar leitura"** (em qualquer conversa, direta ou grupo; último item do menu, depois de Arquivo, Código e, em grupo, Votação e Postagem).
- Marca a **próxima mensagem enviada**: aparece acima do campo a faixa **"A mensagem vai pedir confirmação de leitura"** com o × "Não pedir confirmação". Trocar de conversa desliga.
- No envio: `PUT /mensagem {…, pede_confirmacao: true}` e a faixa some; a mensagem otimista já nasce com `confirmacao {total: 0, confirmou: false, usuarios: []}`, então quem enviou vê "Aguardando confirmação" na hora (MSG-21).
- 🆕 `e33ecba` (servidor `6d20ff7`) **Em grupo, escolher quem precisa confirmar** (`ConfirmacaoLeituraModal.vue`, `chat.confirmacaoUsuarios`):
  - no grupo, "+" › "Confirmar leitura" **abre antes** o modal **"Quem precisa confirmar"** (na conversa direta, marca direto, como antes): × "Fechar", busca **"Filtrar por nome"** (sem acento; nada encontrado: **"Ninguém com “<termo>”"**), caixa **"Todos (N)"** (N = participantes menos eu), a lista com foto/inicial e nome ("Carregando participantes" enquanto lê `GET /conversa/usuarios`), **"Cancelar"** e **"Pronto"** (desabilitado sem ninguém marcado). Começa com todos marcados; cancelar não marca a mensagem;
  - a faixa vira **"A mensagem vai pedir confirmação de leitura de todos"** / **"… de 1 pessoa"** / **"… de N pessoas"** com o link **"Escolher"**, que reabre o modal com a escolha atual (só em grupo; na direta a faixa fica como antes);
  - no envio: com todos marcados vai só `pede_confirmacao: true` (quem entrar no grupo antes do envio também entra na lista); com parte, também **`confirmacao_usuarios: [ids]`**. Trocar de conversa limpa a escolha;
  - a mensagem otimista nasce com `confirmacao {total: N, confirmou: false, pode_confirmar: false, destinatarios: […], usuarios: []}`, montada com os participantes já carregados.
- 🆕 `9c6e8a6` O item "Confirmar leitura" do "+" só aparece com a permissão de uso `confirmacao` (GER-09). Cada pessoa da lista ganha a atividade **"pediu que você confirme a leitura"** (ATV-01); a de mensagem agendada nasce com o horário de envio e só aparece quando ela sai.

**ENV-25 🆕 `f798e9c` Sugestão de texto da IA** (`composables/useSugestaoIa.ts`, `editor/sugestao.ts`)
- Liga quando: a IA do servidor está ligada (`recursos.ia`, GER-08) **e** a preferência local está ligada (`localStorage['conversa.sugestoesIa'] !== '0'`; padrão ligada; muda em Configurações › Privacidade, CFG-09).
- Pedido: a cada mudança no texto recomeça a contar; depois de **600 ms parado**, com o campo em foco, **sem a lista de menção aberta**, **sem seleção** e com o **cursor no fim** (depois dele só espaço ou peças) e **pelo menos 3 caracteres** antes do cursor (sem contar espaços nas pontas) → `POST /ia/sugestao {conversa_id, texto}` → `{sugestao}` (o texto vai até o cursor). Voltar a digitar cancela o pedido em andamento (`AbortController`); se o campo ou o cursor mudou enquanto a IA pensava, a resposta é jogada fora.
- Exibição: o texto sugerido aparece **em cinza logo depois do cursor**, sem ser parte do campo (não seleciona, não recebe clique).
- **Tab aceita** (insere o texto no cursor); **Esc descarta**. Qualquer mudança no campo, mover o cursor ou o campo perder o foco apaga a sugestão. Erro do servidor: só não aparece sugestão.

**ENV-26 🆕 `d202c06` Criar postagem em grupo** (`AnexoPopup.vue`, `MessageInput.vue`, `chat.postagemPendente`)
- Entrada: botão "+" → **"Postagem"** (só em grupo, depois de "Votação").
- Marca a **próxima mensagem enviada** como postagem: acima do campo, a faixa **"Postagem: as respostas ficam embaixo dela"** com o campo de título (placeholder **"Título (opcional)"**, máx. **150** caracteres) e o × "Não postar". Trocar de conversa desliga.
- No envio: `PUT /mensagem {…, postagem: {titulo}}` com o título `trim()` ou `null`; a mensagem otimista já é um cartão de postagem sem respostas (MSG-22). O servidor só aceita postagem em grupo.
- 🆕 `9c6e8a6` "Postagem" só aparece com a permissão de uso `postagem` (GER-09); o servidor responde 403 "Você não tem permissão para: fazer postagens em grupos.". Responder a uma postagem não pede essa permissão.

**ENV-27 🆕 `e33ecba` Pedir confirmação de leitura depois do envio** (`MensagemAcoes.vue`, `MessageList.vue`, `ConfirmacaoLeituraModal.vue`, `chat.pedirConfirmacaoDepois`; servidor `6d20ff7`)
- Entrada: menu de ações da mensagem → **"Pedir confirmação de leitura"** (ENV-20), só na **minha** mensagem **salva** (id > 0), **não oculta**, que **ainda não pede** confirmação, já enviada e há **menos de 24 h** (`visivel_em ?? inserida`).
- **Conversa direta:** pede na hora, para a outra pessoa. **Grupo:** abre o modal **"Quem precisa confirmar"** (o mesmo do ENV-24) com o botão **"Pedir confirmação"** (no lugar de "Pronto"); com todos marcados, vai sem lista.
- API: **`POST /mensagem/pedir-confirmacao {mensagem_id, usuarios?: number[]}`** → `ConfirmacaoLeitura` como quem enviou a vê (`{total, confirmou, pode_confirmar, destinatarios, usuarios}`); o web põe na mensagem, que passa a mostrar o chip "Aguardando confirmação" (MSG-21).
- Erros do servidor: 404 "Mensagem não encontrada!"; 403 "Só quem enviou pede confirmação de leitura."; 400 "Mensagem apagada.", "Esta mensagem já pede confirmação de leitura.", "A confirmação de leitura só pode ser pedida até 24 horas depois do envio." e "Escolha quem precisa confirmar entre os membros da conversa.". O web mostra o diálogo **"Não foi possível pedir a confirmação"** com a mensagem do servidor (fallback "Erro ao pedir a confirmação").
- Tempo real: o servidor manda o **WS 65** (`ConfirmacaoPedida`) a **todos os membros** da conversa (inclusive às outras abas de quem pediu), cada um com a sua visão de `confirmacao` (MSG-21).
- 🆕 `9c6e8a6` (servidor `ef13c22`) O item do menu só aparece com a permissão de uso `confirmacao` (o servidor responde 403 "Você não tem permissão para: pedir confirmação de leitura."). Pedir depois também cria a atividade "pediu que você confirme a leitura" para cada pessoa da lista (ATV-01).

**ENV-28 🆕 `e33ecba` Código como peça no campo** (`editor/PecaCodigo.vue`, `editor/pecas.ts#Codigo/blocoDeCodigo`, `CodigoModal.vue`, `MessageInput.vue`)
- Entrada: "+" › "Código" ou texto colado com mais de 10 linhas (ENV-09) abre a janela **"Inserir código"**; **"Inserir na mensagem"** (ou **Ctrl/Cmd+Enter**) põe o código no campo como uma **peça** no ponto do cursor, numa linha própria (como as de anexo, ENV-21). "Enviar" continua mandando o código sozinho, na hora (ENV-10).
- **Peça** (até `max-w-md`; selecionada fica azul, com anel): cabeçalho com o ícone de código, **"<linguagem> · 1 linha"** / **"<linguagem> · N linhas"** (sem linguagem: **"texto"**), e os botões **"Editar código"** (lápis) e **"Remover código"** (×) — `title`/`aria-label` com esses textos; embaixo, a **prévia das 4 primeiras linhas** em fonte monoespaçada, com **"…"** quando há mais.
- **Não se edita dentro do campo** (peça "átomo"): "Editar código" abre a mesma janela com o título **"Editar código"** e só **"Cancelar"** / **"Salvar"**; salvar troca a peça. Como as outras peças, apaga-se como um caractere, seleciona-se e arrasta-se para mudar a ordem; o rascunho (ENV-23) guarda a peça.
- **No envio** (`blocosDoDocumento`), **cada peça vira um conteúdo próprio tipo 1** com o bloco cercado: `<cerca><linguagem>\n<código>\n<cerca>` (cerca = uma crase a mais que a maior sequência de crases do código, mínimo 3); o texto antes e depois vira conteúdos tipo 1 separados, na ordem do campo. Peça com código vazio é descartada.

**ENV-29 🆕 `f351190` Selecionar mensagens e pôr na pasta** (`composables/useSelecaoMensagens.ts`, `MessageList.vue`, `utils/pastas.ts`, `PainelPastas.vue`; servidor `ac710eb`)
- Só na conversa **direta** com as pastas carregadas (CON-17); no grupo não há seleção. Mensagem ainda enviando (id ≤ 0) não se seleciona.
- **Selecionar com o mouse:**
  - **arrastar na área vazia ao lado das bolhas** desenha um retângulo (borda e fundo leve na cor principal, recortado à área do chat) e seleciona as mensagens cuja **linha** (a largura toda) ele cruza na vertical. O arraste só conta depois de **4 px**; perto da borda de cima ou de baixo (**40 px**), o chat rola sozinho, `ceil(distância dentro da faixa / 3)` px por quadro;
  - o arraste **não começa** sobre a bolha (nem numa faixa de 32 px ao lado dela: o texto da bolha continua selecionável), em botões, links e campos, numa mensagem já selecionada (dali ela é arrastada para a pasta), na barra de rolagem, com outro botão que não o esquerdo e **com toque** (`pointerType === 'touch'`): **no celular o web não seleciona**;
  - **Ctrl (ou Cmd)** pressionado ao começar o arraste **soma** à seleção que já existe;
  - **Ctrl + clique** numa mensagem (fora de links e botões) soma ou tira só ela;
  - clique no vazio **sem arrastar e sem Ctrl** limpa a seleção; **Esc** limpa (e fecha o menu "Pôr na pasta").
- **Selecionada:** a linha da mensagem fica com fundo e anel na cor principal (25% e 60%). Selecionar abre o painel de pastas (CON-17).
- **Barra da seleção** (no topo do chat, centralizada): **"1 selecionada"** / **"N selecionadas"**, **"· arraste até uma pasta"** (some em tela estreita), o botão **"Pôr na pasta"** e o × **"Limpar seleção (Esc)"**. A própria barra se arrasta (`title` **"Arraste até uma pasta"**).
- **Pôr na pasta**, de dois jeitos:
  - **arrastar** a barra, ou qualquer mensagem selecionada, até uma pasta do painel (que mostra "Solte numa pasta" e as bordas tracejadas). A imagem do arraste é uma etiqueta **"1 mensagem"** / **"N mensagens"**; o arraste leva o tipo próprio `application/x-conversa-mensagens` com o JSON dos ids (assim o chat não confunde com arquivo vindo de fora); ao soltar, só valem inteiros > 0;
  - o botão **"Pôr na pasta"**, que abre a lista das pastas (as minhas e as compartilhadas, cada uma com a bolinha da cor); sem pastas: **"Nenhuma pasta ainda. Crie no painel de pastas."**. O menu fecha com clique fora ou quando a seleção esvazia.
- **API:** `PUT /pasta/mensagens {pasta_id, mensagens: number[]}` → `{pasta_id, incluidas}` (só ids > 0; sem nenhum, não chama). Depois: marca `mensagem.pastas` nas bolhas (MSG-23), relê `GET /pastas` (contagem), relê a pasta se ela está aberta e limpa a seleção. Erro: diálogo **"Não foi possível pôr na pasta"** (fallback "Erro ao pôr na pasta").
- **Servidor:** de **1 a 500** ids; todos da conversa da pasta e visíveis para mim (a agendada de outra pessoa que ainda não saiu não vale), senão 400 "Alguma das mensagens não é desta conversa."; a que já está na pasta é ignorada sem erro (`incluidas` conta só as novas). Na pasta compartilhada, os dois põem.

**ENV-30 🆕 `f351190` Enviar localização ou trajeto** (`AnexoPopup.vue`, `LocalizacaoModal.vue`, `services/mapas.ts`, `utils/localizacao.ts`, `editor/PecaLocalizacao.vue`, `editor/pecas.ts`, `MessageInput.vue`; servidor `ac710eb`, migração 055)
- **Entrada:** "+" → **"Localização"** (ícone de alfinete, vermelho), depois de "Código" e antes de "Votação"; em conversa direta e em grupo. Só com a permissão de uso **`localizacao`** (GER-09).
- **Janela** **"Enviar localização"** (editando a peça: **"Editar localização"**); fecha pelo ×, pelo clique fora ou pelo Esc (com a lista de resultados aberta, o Esc só fecha a lista):
  - modos **"Local"** (um ponto) e **"Trajeto"** (origem e destino); os pontos marcados ficam ao trocar de modo;
  - campos: Local com o selo **"•"**; Trajeto com **"A"** (verde, origem) e **"B"** (vermelho, destino). Placeholders **"Endereço, lugar ou coordenadas"**, **"Origem: endereço, lugar ou coordenadas"** e **"Destino: endereço, lugar ou coordenadas"**. O campo com foco é o **ativo** (borda na cor principal) e recebe o clique no mapa. Cada campo tem **"Buscar"** (lupa; o Enter também busca) e **"Usar minha posição"** (mira, girando enquanto localiza);
  - mapa de 288 px de altura, interativo (MSG-25), com os marcadores **arrastáveis**.
- **Como marcar o ponto:**
  - **posição do aparelho:** ao abrir do zero, a janela já tenta, em silêncio. Pelo botão, o erro aparece: **"Este dispositivo não informa a posição."**, **"O acesso à localização foi negado."** ou **"Não foi possível obter a posição deste dispositivo."**, seguido de **" Busque o endereço, digite as coordenadas ou clique no mapa."**. `getCurrentPosition` com alta precisão, limite de 15 s e posição de até 60 s; o mapa vai ao ponto com zoom 16;
  - **coordenadas digitadas** (conferidas antes de buscar): `-23.55, -46.63`, `-23,55; -46,63` ou `-23.55 -46.63` — regex `^(-?\d{1,2}(?:[.,]\d+)?)\s*[,;\s]\s*(-?\d{1,3}(?:[.,]\d+)?)$`, ponto ou vírgula decimal, |lat| ≤ 90 e |lng| ≤ 180 (`"95, 10"` não vale);
  - **busca de endereço** (Nominatim), **sem debounce**: só sai no Enter ou na lupa, e a busca anterior é cancelada. `https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=pt-BR&q=<texto codificado>` → até 6 resultados `{lat, lon, display_name}` (texto) numa lista sob o campo. Sem resultado: **"Nada encontrado com esse endereço."**; resposta não OK: **"A busca de endereço não respondeu."** (fallback "A busca de endereço falhou.");
  - **clique no mapa** ou **arrastar o marcador**.
  - **Nome do ponto:** o `display_name` do resultado escolhido; nos outros casos, a **busca reversa** `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=pt-BR&lat=<lat>&lon=<lng>` → `display_name` (sem resposta, o ponto fica sem nome, sem erro). Até **300** caracteres. Marcar a origem com o destino vazio passa o campo ativo ao destino.
- **Rota do trajeto:** com os dois pontos, **300 ms** depois da última mudança, `https://router.project-osrm.org/route/v1/driving/<lngA>,<latA>;<lngB>,<latB>?overview=full&geometries=polyline` (OSRM público, perfil `driving`, só de carro; o pedido anterior é cancelado). Vale `code: 'Ok'` com `routes[0]`: `rota` = `geometry` (polyline, precisão 5), `distancia` = `round(distance)` em metros, `duracao` = `round(duration)` em segundos. Sem rota ou sem resposta, fica a **linha reta**. Abrindo para editar um trajeto que já tem rota, não recalcula; um trajeto em linha reta é recalculado ao abrir.
- **Linha embaixo do mapa**, por prioridade: o aviso (vermelho); **"Buscando..."**; **"Calculando a rota..."**; trajeto com rota: **"<distância> · <tempo> de carro"**; trajeto sem rota: **"Sem rota pelas ruas: <distância> em linha reta"**; senão a dica — Local **"Sem GPS? Busque o endereço, digite as coordenadas ou clique no mapa."**, Trajeto **"Clique no mapa para marcar a origem, ou busque o endereço."** (ou "o destino", conforme o campo ativo). Fórmulas em MSG-25.
- **Botões:** **"Cancelar"**, **"Inserir na mensagem"** (contorno; não aparece editando) e **"Enviar"** (editando, **"Salvar"**); os dois últimos ficam desabilitados sem os pontos do modo ou enquanto a rota é calculada.
  - **"Enviar"** manda **na hora**, numa mensagem só da localização; o que estava no campo fica lá. Como o "Enviar" do código (ENV-10), leva o que estiver pendente acima do campo (resposta, confirmação, postagem).
  - **"Inserir na mensagem"** vira a **peça `localizacao`** no campo (ENV-21), numa linha própria: ícone de alfinete, título **"Localização"** ou **"Trajeto"**, o resumo (ex.: **"Casa → Trabalho · 12,3 km"**), **"Editar localização"** (lápis; reabre a janela em modo edição, e "Salvar" troca a peça) e **"Remover localização"** (×). Não se edita dentro do campo; apaga-se, seleciona-se e arrasta-se como as outras peças. No envio (`blocosDoDocumento`), cada peça vira **um conteúdo tipo 9**, na ordem do campo.
- **Formato do conteúdo tipo 9** (`conteudo` = o JSON em texto; `PUT /mensagem {conteudos: [{ordem, tipo: 9, conteudo}]}`, com `ordem` a partir de 1):
  - Local: `{"pontos":[{"lat":-23.5,"lng":-46.6,"nome":"Praça da Sé, …"}]}`;
  - Trajeto com rota: `{"pontos":[A,B],"rota":"<polyline>","distancia":12345,"duracao":1080}` (metros e segundos, inteiros);
  - Trajeto sem rota: `{"pontos":[A,B],"distancia":10000}` (linha reta arredondada; sem `duracao` nem `rota`).
  - `nome` é opcional em cada ponto. Ordem das chaves: `pontos`, `rota`, `distancia`, `duracao`; no ponto, `lat`, `lng`, `nome`. Na leitura, vale como localização o JSON com `pontos` (lista com 1 item ou mais); é trajeto quando tem 2.
- **Servidor** (`localizacaoValida`; senão 400 **"Localização inválida!"**): texto até 200.000 caracteres; 1 ou 2 pontos; `lat` e `lng` numéricos, com |lat| ≤ 90 e |lng| ≤ 180; `nome` texto de até 300; `rota` só com 2 pontos; `distancia` e `duracao` números finitos ≥ 0. Sem a permissão: 403 "Você não tem permissão para: enviar localizações e trajetos.". Os bots e o resumo da conversa leem `[localização: nome (lat, lng)]` ou `[trajeto: origem → destino, 12,3 km]`.

### 3.5 ANX — Anexos e mídia

**ANX-01 Fila de anexos antes de enviar** — 🆕 **SUBSTITUÍDA pelo campo de mensagem rico (ENV-21)**: `useFilaArquivos.ts` e `FilaArquivosPreview.vue` foram removidos em `39d06f9`. A descrição abaixo é histórica. (`useFilaArquivos.ts`, `FilaArquivosPreview.vue`)
- Entradas: "Anexar" → "Arquivo" (seletor múltiplo, qualquer tipo), arrastar e soltar sobre a conversa (overlay "Solte para enviar o arquivo" / "Imagens mostrarao uma previa antes de enviar"), colar imagem.
- Imagens: miniaturas com nome e "Remover"; clicar abre no visualizador com galeria da fila. Outros: ícone, nome, tamanho (`B/KB/MB/GB`), duração (áudio; via metadata ou decodificação), botão Ouvir/Pausar (um por vez), "Remover".
- Enviar manda o texto + todos os arquivos numa só mensagem (ordem: [conteúdos encaminhados] → texto → figurinha → arquivos).

**ANX-02 Upload com deduplicação e progresso** — §1.7. Indicador (`UploadIndicador.vue`) com nome, barra e %, aparece só se após 2 s algum upload ainda está abaixo de 50%. Sair da página com upload ativo pede confirmação do navegador (`beforeunload`). Erro em qualquer upload cancela a mensagem inteira.

**ANX-03 Tipo de conteúdo por arquivo**
- `image/*` → 2 (Imagem); `audio/*` → 4 (Audio); gravação → 5 (GravacaoAudio); demais → 3 (Arquivo). Vídeo é **Arquivo (3)** com extensão `mp4, webm, ogg, mov, m4v, mkv`. Extensão = parte após o último ponto, máx. 10 caracteres.

**ANX-04 Visualizador de imagens e vídeos** (`ImageViewerModal.vue`, `useImageViewer.ts`)
- Galeria = imagens e vídeos da conversa em ordem (exceto mensagens ocultas), com **legenda** = textos da mesma mensagem; miniaturas embaixo (setas de rolagem se > 10), item atual com anel.
- Imagem: zoom 10%–3000% (passo 0,1 abaixo de 1×, 0,2 até 3×, 0,5 até 8×, 1 acima), roda do mouse, arrastar com zoom, duplo clique volta a 100% animado, botões −/%/+, "Copiar imagem (Ctrl+C)", menu de clique direito "Copiar imagem", "Fechar".
- Vídeo: toca com controles nativos e autoplay; sem zoom/copiar.
- Teclas: Esc fecha, ←/→ navegam (exceto com o player em foco), +/=/− zoom, Ctrl+C copia.
- Overlay "Carregando imagem"/"Carregando vídeo".
- 🆕 `e33ecba` **Zoom no ponteiro:** a roda do mouse amplia/reduz mantendo parado o ponto que está **sob o ponteiro** (antes, o deslocamento só era escalado em torno do centro); os botões −/+ e as teclas continuam usando o centro do que está à vista (`useImageViewer.ts#ajustarZoomImagem`, com o foco).
- 🆕 `9c6e8a6` **Vídeo no player próprio** (`PlayerVideo.vue`, modo completo), no lugar dos controles nativos: começa tocando; barra de progresso com arraste, "Pausar" / "Reproduzir", tempo **"mm:ss / mm:ss"** ("--:--" enquanto não sabe a duração); **volume de 0 a 200%** ("Silenciar" / "Ativar som", barra "Volume" com uma marca no meio; acima de 100% a barra e o número ficam vermelhos, com ganho do Web Audio); **"Velocidade"** com 0,25x, 0,5x, 0,75x, 1x, 1,25x, 1,5x, 1,75x, 2x, 2,5x e 3x; **"Baixar vídeo"**; "Tela cheia" / "Sair da tela cheia". Um clique toca ou pausa; dois cliques entram ou saem da tela cheia. A barra aparece ao mexer o mouse e some 2,5 s depois.

**ANX-05 Vídeo na conversa** — prévia com o primeiro quadro (`url#t=0.1`) e ícone de play; clique abre no visualizador; botão "Baixar video". 🆕 `9c6e8a6`: o vídeo **toca na própria bolha** (fim do item).
- 🆕 `9c6e8a6` **Player na bolha** (`PlayerVideo.vue`, `MessageContent.vue`), na mesma caixa de 420×236 px:
  - parado, o botão central "Reproduzir vídeo"; um clique toca ou pausa ali mesmo; dois cliques, tela cheia;
  - barra embaixo (aparece ao mexer o mouse): progresso com arraste, "Pausar" / "Reproduzir", **"mm:ss / mm:ss"**, **"Baixar vídeo"**, **"Abrir no visualizador"** (pausa e abre o ANX-04) e "Tela cheia";
  - o link "Baixar video" embaixo da prévia **saiu** (virou o botão da barra);
  - enviando (arquivo local), sem "Baixar vídeo" nem "Abrir no visualizador";
  - volume e velocidade só no visualizador.

**ANX-06 Visualizador de PDF** (`VisualizadorPdf.vue`)
- Botão "Abrir" em anexos `.pdf`. Páginas em canvas renderizadas sob demanda (perto da área visível), 100% = largura da tela (240–900 px); zoom 50%–300% passo 25%; botão "%" volta a ajustar à largura; indicador "página / total"; "Baixar"; "Fechar (Esc)". Mensagens "Carregando PDF..." / "Não foi possível abrir o PDF. Tente baixar o arquivo.".

**ANX-07 Visualizador de HTML isolado** (`VisualizadorHtml.vue`)
- Botão "Abrir" em `.html/.htm` (chat e página de Anexos). Baixa o HTML e mostra em `iframe srcdoc` com `sandbox="allow-scripts"` **sem** `allow-same-origin` e `referrerpolicy=no-referrer` (sem acesso ao token/cookies). "Carregando HTML..." / "Não foi possível abrir o HTML. Tente baixar o arquivo.".
- 🆕 `eaa8bac` Links `#...` rolam dentro do próprio documento (um script injetado logo depois do `<head>`); antes, no `srcdoc`, eles abriam o Conversa dentro do quadro.
- 🆕 `8b43965` **Ver o fonte:** com o HTML carregado, um botão na barra (ícone `</>`, antes de "Baixar") alterna entre a página e o **fonte** como veio no arquivo, com destaque de sintaxe (highlight.js `html`, quebra de linha); `title` **"Ver fonte"** / **"Ver página"** (destacado enquanto mostra o fonte).

**ANX-08 Markdown e Mermaid** — dentro de blocos de código (MSG-15). Não há visualizador de **arquivo** `.md` anexado (só para blocos no texto).

**ANX-09 Download** — "Download" em arquivos: busca a URL assinada, baixa como blob e dispara `<a download=nome>`; falha → abre a URL em nova aba; erro → aviso "Não foi possível baixar".

**ANX-10 Player de áudio** (`AudioPlayerArquivo.vue`, `AudioPlayerGravacao.vue`, `useAudioPlayer.ts`)
- Play/pause, barra de progresso com arraste (seek), duração/tempo `mm:ss`; arquivo mostra o nome; gravação mostra só o player. URL buscada só no primeiro play.
- Apenas **um áudio toca por vez** (`useAudioManager`).
- Áudio recebido **não ouvido** tem o botão verde; o primeiro play chama `POST /mensagem/reproduzir {conversa, mensagem}` (uma vez).
- Em bolha só de áudio, a hora/status fica dentro do player.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Onda e duração sem baixar o áudio** (`OndaAudio.vue`, `useAudioPlayer.ts`):
  - o conteúdo de áudio (tipos 4 e 5) traz **`duracao`** (segundos, `number | null`) e **`intensidade`** (picos de 0 a 100, `number[] | null`), gravados no envio (§1.7); a mensagem otimista já nasce com eles;
  - com `intensidade`, a barra vira a **onda**: uma barra por pico (altura mínima de 12%), com 2 px entre elas e 28 px de altura; as já tocadas ficam na cor de destaque; o arraste (seek) continua;
  - a **duração aparece antes de tocar**; tocando ou parado no meio, o texto vira **"atual / total"** (ex.: "00:05 / 00:12"; sem o total, só o atual);
  - áudio antigo, sem esses dados: barra simples; quando o player aparece na tela (com 200 px de folga), o web **busca só a duração**, sem tocar nem mostrar carregando;
  - gravação WebM do navegador não traz a duração no arquivo: o web pula para o fim para descobrir e volta ao começo (desiste em 10 s). A duração gravada no envio prevalece sobre a do elemento.
- 🆕 `f351190` À direita da barra (ou da onda), o botão de **velocidade** "1x" / "1,5x" / "2x" / "3x", a mesma para todos os áudios (ANX-18).

**ANX-11 Gravação de áudio** — 🆕 com o campo **vazio** a gravação é enviada na hora; com algo escrito ela **entra no campo** como peça e vai junto (ENV-21) (`useAudioRecording.ts`, `BarraGravacao.vue`, `MessageInput.vue`)
- Exige contexto seguro ("Para gravar áudio por navegador, use HTTPS (ou localhost).") e `MediaRecorder` ("Gravação de áudio não suportada neste navegador.").
- **Pressionar e segurar** o microfone ≥ 300 ms e soltar sobre o botão → para e **envia direto**. **Clique curto** (ou arrastar para fora do botão) → modo travado com a barra de gravação.
- Barra: "Descartar", ponto vermelho + tempo `m:ss`, forma de onda animada; "Pausar gravação" / "Continuar gravação"; pausado: "Ouvir gravação"/"Parar preview" com barra de progresso clicável (seek); "Enviar áudio".
- Arquivo: `audio-<timestamp>.webm|ogg` (mime do `MediaRecorder`), conteúdo **tipo 5**.
- 🆕 `9c6e8a6` O botão "Gravar áudio" só aparece com a permissão de uso `audio` (GER-09). A gravação (e qualquer áudio anexado) vai com `duracao` e `intensidade` medidas no navegador (§1.7, ANX-10).

**ANX-12 Transcrição de áudio** (`TranscricaoAudio.vue`)
- Abaixo de áudios (tipo 4 e 5) já enviados. Estados (`StatusTranscricao`): 0 Nenhuma → botão "Transcrever"; 1 Processando → "Transcrevendo..." com consulta a cada 3 s; 2 Concluída → texto (ou "(nenhuma fala reconhecida)"); 3 Erro → botão "Não foi possível transcrever. Tentar de novo" + mensagem de erro.
- Estado inicial vem nos próprios conteúdos da mensagem (`transcricao_status`, `transcricao`).
- API: `PUT /anexo/transcricao {identificador}` e `GET /anexo/transcricao?identificador=` → `{status, texto, erro}`. Se o servidor não tem transcritor configurado (parâmetro `transcritor_url` vazio), o botão não funciona (ver CFG-07).
- 🆕 `769bd7d` O botão **"Transcrever"** (e o "Tentar de novo" do erro) **só aparece com `recursos.transcricao`** (GER-08). Sem transcritor, continuam aparecendo a transcrição **concluída** e a **em andamento** ("Transcrevendo..."); as outras situações não mostram nada.

**ANX-13 Página de Anexos** (`AnexosPage.vue`, `AnexosLista.vue`) — 🆕 `eaa8bac`: a mesma `AnexosLista` aparece no painel "Dados do grupo" (CON-08), já com a conversa do grupo
- Sem conversa selecionada: campo "Buscar contato ou grupo..." e "Selecione um contato ou grupo para ver os anexos"; resultados com avatar, etiqueta "Grupo", prévia; "Nenhum resultado".
- Com conversa: chip da conversa com "Limpar selecao"; filtros de direção **Todos / Enviados / Recebidos** e de tipo **Todos / Imagens / Arquivos / Audios / Gravacoes**.
- Imagens em grade (lazy, "Erro" se falhar) com ações "Abrir mensagem" e "Baixar"; demais em lista: ícone/miniatura, nome ("Sem nome"), tamanho · data (hoje `HH:MM`, senão `dd/MM/aa`) · (sem conversa: nome da conversa), autor; ações "Abrir mensagem", "Baixar" e player nativo para áudios.
- Clique: imagem/vídeo → visualizador com a galeria dos itens carregados; HTML → visualizador isolado; outro arquivo → abre a URL. 🆕 `9c6e8a6` `.docx`, `.xlsx` e `.pptx` → visualizador do Office (ANX-16).
- Paginação infinita: carrega mais quando faltam 80% da altura visível.
- API: `GET /anexos?conversa=&autor=&direcao=(enviados|recebidos|'')&tipos=2,3,4,5&antes=<anexo_id>&limite=60` → `AnexoItem[]` (`anexo_id, identificador, nome, extensao, tamanho, criado_em, tipo, mensagem_id, conversa_id, conversa_descricao, autor_id, autor_nome, url` — URL assinada com validade de 600 s).
- Entrada também pelo botão "Ver anexos" do perfil (AUT-10).
- 🆕 `f351190` **Anexos de todas as conversas:** sem conversa escolhida e sem nada no campo, a página **abre com os anexos mais recentes de todas as conversas** (`AnexosLista` com `conversaId = 0` → `GET /anexos?conversa=0&…`; o servidor busca em todas as conversas de que a pessoa participa). O texto ~~"Selecione um contato ou grupo para ver os anexos"~~ saiu.
  - Digitar em "Buscar contato ou grupo..." troca a lista pelas conversas encontradas ("Nenhum resultado"); escolher uma mostra o chip e os anexos dela; "Limpar selecao" volta aos de todas.
  - Na lista de todas, cada item mostra, depois de "tamanho · data", **"· <nome da conversa>"**: `conversa_descricao` do item, ou, na direta (que não tem descrição), o nome da conversa na lista (o da outra pessoa); sem nenhum, nem o "·".
  - Os filtros (direção e tipo) e a paginação (60 por vez, `antes` = `anexo_id` do último) não mudaram.

**ANX-14 URLs assinadas e renovação** — §1.7 (`useAttachments.ts`). Imagens com erro chamam `renovarAnexoUrl` (injeção `provide/inject`).

**ANX-15 Pré-visualização de imagem antes de enviar** (`ImagePreviewModal.vue`, `useImagePreview.ts`) — "Pré-visualização da imagem", "Cancelar"/"Enviar imagem", Enter confirma, Esc fecha. **Sem gatilho atual** (o evento `open-image-preview` não é emitido; imagens vão para a fila). Ver Anexo B.

**ANX-16 🆕 `9c6e8a6` Visualizador de arquivos do Office** (`VisualizadorOffice.vue`, `utils/fontesOffice.ts`, `utils/formatters.ts#formatoOffice`)
- Entrada: botão **"Abrir"** nos anexos **`.docx`, `.xlsx` e `.pptx`** do chat (como PDF e HTML, ANX-06/ANX-07) e o clique no item na página de Anexos (ANX-13). Os formatos antigos (`.doc`, `.xls`, `.ppt`) continuam só com "Download".
- Desenho: a biblioteca `@silurus/ooxml` lê o arquivo (Rust/WASM, num worker) e **desenha em canvas**: nada do arquivo (scripts, links, formulários) roda na página. Word e PowerPoint rolam página a página (ou slide a slide), mostrando as primeiras enquanto pagina o resto; o Excel mostra as abas, com os controles da biblioteca.
- Fontes: as substitutas (Carlito no lugar da Calibri, Caladea no da Cambria e as do Google que a biblioteca conhece) ficam em `public/fontes/office`; antes de desenhar, o web lê no ZIP as fontes que o documento usa e carrega só essas. Sem Google Fonts nem CDN.
- Barra de cima: o nome do arquivo; no Word e no PowerPoint, **"Página anterior"** / **"Próxima página"** (**"Slide anterior"** / **"Próximo slide"**), o campo **"Ir para a página"** / **"Ir para o slide"** com "/ N" ("…" e o `title` "Ainda contando as páginas" enquanto pagina), e, em tela larga, **"Diminuir zoom"**, o %, **"Aumentar zoom"**, **"Página inteira"** (padrão) e **"Ajustar à largura"**; sempre **"Baixar"** e **"Fechar (Esc)"**. Ctrl + roda (ou pinça) também amplia.
- Estados: **"Baixando arquivo..."**, **"Preparando o documento..."**; erro **"Não foi possível abrir o arquivo. Tente baixar."**.

**ANX-17 🆕 `f351190` Espaço usado pelos arquivos enviados** (`EspacoUsado.vue`, `ConfiguracaoAcessos.vue`, `utils/formatters.ts#formatarTamanho`; servidor `ac710eb`, `anexos.ts#espacoPorUsuario`)
- **Conta › Perfil**, seção **"Armazenamento"** (entre o cartão da foto e "Dados do usuario"): o tamanho em destaque e **" usados pelos arquivos que você enviou (imagens, arquivos e áudios; cada arquivo conta uma vez, mesmo mandado em várias conversas)."**. Enquanto lê: **"Calculando..."**; erro: a mensagem do servidor (fallback **"Não foi possível calcular o espaço usado"**).
- **Lista de Pessoas** (CFG-08/CFG-11): uma coluna à direita com o espaço de cada pessoa, `title` **"Espaço dos arquivos que enviou"**.
- **Formato** (`formatarTamanho`): abaixo de 1024, "N B"; depois KB, MB e GB na base 1024, com **uma casa e ponto decimal** ("0 B", "2.0 KB", "1.5 MB").
- **API:** **`GET /usuario/espaco`** → `{bytes}` (da própria pessoa); `GET /permissoes` traz `espaco` de cada usuário. O servidor soma o `tamanho` de cada anexo **distinto** (pelo identificador) das mensagens que a pessoa enviou, nos tipos 2, 3, 4 e 5: o mesmo arquivo mandado em várias conversas conta uma vez.

**ANX-18 🆕 `f351190` Velocidade do áudio** (`composables/useAudioPlayer.ts`, `AudioPlayerArquivo.vue`, `AudioPlayerGravacao.vue`)
- Botão redondo **à direita da barra** (ou da onda) de cada player de áudio (arquivo e gravação), `title` **"Velocidade"**, com o rótulo **"1x"**, **"1,5x"**, **"2x"** ou **"3x"** (vírgula decimal: `String(v).replace('.', ',') + 'x'`).
- Cada clique passa para a próxima: 1 → 1,5 → 2 → 3 → 1.
- A velocidade é **uma só para todos os áudios**: vale na hora para o áudio do botão clicado e no próximo play de qualquer outro. Fica no navegador (`localStorage['conversa.velocidadeAudio']`; valor fora da lista volta a 1).

### 3.6 PES — Pesquisa

**PES-01 Pesquisa na conversa**
- Campo "Pesquisar..." no cabeçalho (desktop) ou painel "Pesquisar nesta conversa" (celular); Enter pesquisa; × limpa.
- API: `GET /pesquisar?texto=<termo>&conversa=<id>` → `Mensagem[]` (o cliente ainda filtra por `conversa_id`; 🆕 `e33ecba` não filtra mais, ver abaixo).
- (Até `e8d82cb`) Resultados numa lista sob o cabeçalho: "#<id> - <resumo>"; clique → MSG-06. **Correção desta revisão:** a ordem é **do mais antigo ao mais novo**, como na conversa (o cliente inverte o que a API devolve); a versão anterior deste doc dizia "mais recentes primeiro", o que estava errado.
- 🆕 `d202c06` **Painel de resultados refeito** (`ChatHeader.vue`), aberto depois de uma pesquisa feita (some ao limpar ou trocar de conversa):
  - cabeçalho **"N resultado para “termo”"** / **"N resultados para “termo”"**, ou **"Nenhuma mensagem com “termo”"** (aspas curvas; o termo é o da última pesquisa feita, não o que está no campo);
  - cada resultado: **autor** ("Você" nas minhas) e, à direita, **quando** (hoje `HH:MM`; outro dia `dd/MM HH:MM`, com `/aa` se for de outro ano); embaixo, **uma linha** com o trecho do texto começando **até 30 caracteres antes** do termo ("…" quando corta o começo), menções como `@Nome`, e o termo destacado com `<mark>` (sem diferenciar maiúsculas) — sem texto, o resumo da mensagem;
  - lista com altura máxima de **16rem** e rolagem; clique → MSG-06 (com a faixa de destaque nova).
- 🆕 `e33ecba` (servidor `6d20ff7`) **Também nos chats das ligações:** o `GET /pesquisar?conversa=<id>` passa a devolver, além das mensagens da conversa, as dos **chats das ligações feitas nela** (só os de que a pessoa é membro, e só se ela também é membro da conversa; o chat que é a própria conversa não conta duas vezes). O web **parou de filtrar** por `conversa_id` (`chat.buscarNaConversa`).
  - Resultado de outra conversa ganha, depois do autor, o selo **"no chat da ligação"**;
  - clicar nele **abre aquela conversa** (o chat da ligação) já na mensagem, pelo mesmo caminho da pesquisa global (`abrirMensagemPesquisaGlobal`), em vez de procurar na conversa aberta.

**PES-02 Pesquisa em todos os chats** (`PesquisaAvancada.vue`)
- Botão "Pesquisar em todos os chats" na lista (leva o termo do filtro). Campo "Pesquisar em todos os chats...", Enter pesquisa, "Cancelar".
- API: `GET /pesquisar?texto=<termo>&conversa=0`.
- Resultados agrupados por conversa (título + contagem), cada um com remetente, data `dd/MM/aa HH:mm` e resumo com o termo destacado (`<mark>`, sem diferenciar maiúsculas). "Pesquisando..." / "Nenhum resultado encontrado.". Clique abre a conversa e vai à mensagem.

### 3.7 CHA — Chamadas (WebRTC)

Arquivos centrais: `stores/call.ts`, `CallWindow.vue`, `components/CallBar.vue`, `IncomingCallModal.vue`, `VideoUpgradeModal.vue`, `composables/useCallPopup.ts`, `composables/useFalaChamada.ts`, `components/PonteiroTela.vue`, `components/ChatChamada.vue`. Detalhes de mídia em §5.

**CHA-01 Iniciar chamada de voz ou vídeo**
- Botões do cabeçalho. Direta: participantes `[eu, destinatário]`. Grupo: **todos os membros** da conversa (eu incluído) — não há escolha de participantes (o modal de escolha existe mas não é aberto; Anexo B).
- Pega mídia (vídeo sem câmera → só áudio; áudio sem microfone → erro "Nao foi possivel acessar o microfone"), estado `chamando`, `PUT /chamada/iniciar {tipo, usuarios:[{id}], conversa_id?}` (conversa ativa) → `Chamada`, e **já publica** a mídia no MediaMTX (WHIP) para quem atender assinar logo.
- Não inicia se já existe chamada ("Ja existe uma chamada em andamento").
- Som de chamando (`/chamando.mp3`, loop) até alguém atender. Se o navegador bloquear autoplay, toca no primeiro clique/tecla.
- 🆕 `8b43965` Começando **com a tela** (CHA-02): se a pessoa **fecha a escolha da tela sem escolher** (`NotAllowedError`), a chamada simplesmente não começa (o estado volta ao inicial), **sem mensagem de erro**.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Permissões de uso** (GER-09): "Chamada de voz" só com `chamada_audio`; "Chamada de video" só com `chamada_video` (CON-09). O `PUT /chamada/iniciar` confere pelo tipo (sem `tipo`, 2 participantes = áudio) e responde 403 "Você não tem permissão para: fazer chamadas de áudio." / "… fazer chamadas de vídeo e ligar o vídeo na chamada.". Atender não pede permissão.

**CHA-02 Iniciar compartilhando a tela** — botão "Compartilhar tela" no cabeçalho: chamada de **vídeo** com a tela no lugar da câmera + microfone (+ som do sistema misturado, CHA-15).
- 🆕 `964ed72` No **celular** (tela < 768 px ou só toque) a janela da chamada **não tem** "Compartilhar tela" (os navegadores de celular não compartilham tela; CHA-11). O botão "Compartilhar tela" do **cabeçalho** da conversa, porém, continua aparecendo no celular (lacuna do web; o `ChatHeader` não confere o aparelho).
- 🆕 `9c6e8a6` Pede as permissões de uso `chamada_video` **e** `compartilhar_tela` (GER-09): sem elas, o botão do cabeçalho e o "Compartilhar tela" da janela da chamada somem. **Só o cliente confere** `compartilhar_tela` (a tela não passa pela API).

**CHA-03 Chamada recebida**
- WS 51 `{chamada_id, usuario_id}` → `GET /chamada/dados?id=` → estado `recebendo`, toca `/toque.mp3` em loop, notificação do sistema "Chamada recebida" / "<nome> está ligando (Vídeo|Áudio)" (`tag: conversa-chamada`, `requireInteraction`), modal:
  - "Chamada recebida", "<nome> está ligando...", "Vídeo + Áudio" ou "Somente Áudio";
  - botões **Recusar** (vermelho) e **Atender** (verde); em vídeo, **"Atender só assistindo"** (entra com a câmera desligada e em tela única em quem ligou).
- **Sem resposta em 30 s** → recusa automática com `nao_atendeu` (vira chamada perdida nas atividades).
- Aviso 51 da própria chamada que eu fiz é ignorado. Aviso 51 de uma chamada em que já estou → só sincroniza peers.

**CHA-04 Atender**
- Mídia: vídeo+áudio → só áudio → **sem mídia (somente recepção)**. `POST /chamada/entrar {id}` → `ativa`, cronômetro, publica (WHIP) se tem mídia, `GET /chamada/dados`, sincroniza peers (3 tentativas com espera crescente).
- Chamada de vídeo sem enviar vídeo (só assistindo, sem câmera, sem mídia) → pede **tela única** em quem ligou, já antes de conectar (aplicado quando o primeiro participante chega).
- Falha → desfaz tudo e mostra o erro.

**CHA-05 Recusar** — `POST /chamada/recusar {id, nao_atendeu?}`; para o toque.

**CHA-06 Ocupado** — chega 51 enquanto estou em chamada ou tocando → recusa automática com `nao_atendeu: true` sem tocar.

**CHA-07 Atendida/recusada em outro aparelho ou aba** — WS 54 (`UsuarioEntrou`) ou 53 (`UsuarioRecusou`) com o **meu** `usuario_id` enquanto estou `recebendo` (e não fui eu que atendi aqui) → para de tocar e volta a `inativo`.

**CHA-08 Chamadas pendentes** — ao conectar o WS (e no polling sem WS): `GET /chamadas/pendentes` → `[{id, tipo, status, criado_em, criado_por, conversa_id, ...}]`; a primeira: se criada há **> 25 s** → recusa com `nao_atendeu`; senão passa a tocar como CHA-03.

**CHA-09 Cancelar enquanto chama** — botão sair durante `chamando` → `POST /chamada/cancelar {id}`.

**CHA-10 Entrada dos outros e fim da chamada**
- WS 54 de outro: se eu estava `chamando` → `ativa` + cronômetro; se `ativa` → reanuncia minha tela (se compartilhando) e sincroniza peers.
- WS 55 (`UsuarioSaiu`) → desconecta o peer, `GET /chamada/dados`; **se não sobrou nenhum outro com status "Entrou"** → sai sozinho (`POST /chamada/sair`) e encerra.
- WS 53 de outro numa chamada de 2 pessoas em que o outro está "Recusou" → encerra.
- WS 52 (`ChamadaFinalizada`) → encerra tudo.
- Sair: `POST /chamada/sair {id}`. Existe também `POST /chamada/finalizar {id}` (encerra para todos) na store, **sem botão na UI**.
- A cada 4 s em chamada ativa, sincroniza peers com `GET /chamada/dados` (reconecta quem caiu ou está sem trilhas).

**CHA-11 Controles da chamada** — 🆕 `eaa8bac`: os liga/desliga (microfone, câmera, som, tela, chat, ponteiro) viraram **pílulas com ícone + chave** (verde ligada, cinza desligada; `role="switch"`, `aria-checked`), e a cor do botão não muda mais com o estado. Antes, 🆕 cores: microfone, câmera e som ficam **vermelhos quando desligados**; tela, chat e ponteiro ficam **azuis quando ligados**; os demais ficam neutros (`CallWindow.vue`, `CallControlButton.vue`)
- Microfone (liga/desliga a trilha), Câmera (só vídeo), Compartilhar tela (só vídeo), Chat da chamada (com contador de não lidas), Ponteiro (só quando alguém compartilha a tela), Áudio de saída (silencia os outros), Adicionar usuário (só `ativa`), Voltar ao grid (em destaque), Minimizar/Expandir, Sair (vermelho).
- Cabeçalho: indicador, "Chamando..." / "Em chamada" / "Encerrando...", duração `mm:ss` ou `hh:mm:ss`, "Vídeo"/"Áudio", "N pessoa(s)".
- Tiles: nome ("<eu> (você)"); sem vídeo → iniciais; erro de conexão em faixa vermelha (`erroMsg`, ex.: "Erro ao conectar com <nome>: …"; 404 de WHEP não vira erro, tenta de novo).
- 🆕 `964ed72` **Celular** (tela < 768 px ou só toque): sem o botão "Compartilhar tela"; o "Minimizar" chama-se **"Voltar ao chat"** (deixa a chamada na barra do topo, CHA-13). Na **aba própria** do computador (CHA-13) não há "Minimizar".
- 🆕 `769bd7d` A grade também tem os tiles dos convidados fora da chamada (CHA-25), que contam no número de colunas.
- 🆕 `9c6e8a6` "Compartilhar tela" só com a permissão de uso `compartilhar_tela` (GER-09, CHA-02).

**CHA-12 Modos de exibição** (só vídeo, janela não flutuante) — **Grade** ("Todos os participantes lado a lado"), **Destaque** ("Um participante grande e os demais na lateral"), **Tela única** ("Só um participante, ocupando toda a área", setas "Participante anterior"/"Proximo participante"). Clique num tile destaca; clique no destaque volta à grade. Grade: 1 col (1), 2 col (2–4), 3 col (≤6), 4 col (mais). Se o destacado sai em tela única, o pedido fica pendente e volta quando ele reconectar.

**CHA-13 Janela flutuante e barra** — 🆕 `e33ecba`: no computador, a chamada de vídeo abre numa **janela popup** (fim do item). Vídeo minimizado no desktop: janela flutuante arrastável pelo cabeçalho e redimensionável pelas bordas/cantos. `CallBar` (áudio sempre; vídeo minimizado): status, duração, tipo, nº de pessoas, avatares (anel verde em quem fala); em áudio: microfone, saída, adicionar, botão "Vídeo" (upgrade) e sair; em vídeo: "Abrir chamada" e sair. No celular a chamada minimizada fica só na barra.
- 🆕 `769bd7d` Enquanto o vídeo é ligado numa chamada de áudio, a barra mostra a etapa no lugar de "Vídeo"/"Áudio" (CHA-16).
- 🆕 `964ed72` **Computador** (tela ≥ 768 px e não só toque): a chamada de **vídeo** abre numa **aba própria do navegador** (`/chamada.html`, título **"Chamada · Conversa"**, `composables/useAbaChamada.ts`). A conexão e os dados continuam na aba do app, que desenha a janela da chamada dentro da outra aba (mesmo Vue e mesmo Pinia; estilos e tema copiados e acompanhados). No app fica só a `CallBar`; **"Abrir chamada"** traz a aba de volta (ou a reabre). **Fechar a aba não desliga**: a chamada continua só na barra. No fim da chamada a aba fecha sozinha. Se o navegador **bloquear** a aba nova, a chamada fica dentro do app, como antes (tela cheia / janela flutuante); se a aba abrir mas não carregar em 10 s, ela é fechada e a chamada fica só na barra. Na aba própria não há "Minimizar" e o chat da chamada é o painel simples, só de texto (CHA-18), não o completo (CHA-24). A página aberta direto, sem o app, mostra "A chamada acontece na aba do Conversa que a abriu. Volte para ela.".
- 🆕 `e33ecba` **Janela popup em vez de aba:** `window.open('/chamada.html', 'conversa-chamada', 'popup,width=…,height=…,left=…,top=…')` — **80%** da área livre da tela, no máximo **1280×800**, **centralizada**, sem a barra de ferramentas do navegador (`useAbaChamada.ts#recursosDaJanela`). O resto vale como acima: "Abrir chamada" traz a janela de volta, fechá-la não desliga, bloqueada → a chamada fica dentro do app. A escolha da tela a compartilhar aparece **nessa janela** (CHA-15).
- 🆕 `964ed72` **Celular** (tela < 768 px ou só toque): a chamada fica dentro do app e o "Minimizar" vira **"Voltar ao chat"**. Em tela estreita (< 768 px), a `CallBar` vai para o **topo do app, em qualquer seção** (lista, configurações, atividades…), e não só na área da conversa; em tela larga só de toque (tablet) ela fica no lugar de sempre.

**CHA-14 Adicionar participante** — modal "Adicionar à chamada" com contatos que não estão na chamada ("Nenhum contato disponível para adicionar."), checkboxes, "Adicionar". API: `PUT /chamada/usuario {chamada_id, usuario_id}` (um por vez) → `GET /chamada/dados`.

**CHA-15 Compartilhar tela com áudio do sistema**
- `getDisplayMedia({video:true, audio:{restrictOwnAudio:true, suppressLocalAudioPlayback:false}, systemAudio:'include'})`.
- A trilha de vídeo da tela **substitui** a da câmera na publicação (`replaceTrack`); sem câmera (publicação só com áudio) republica e avisa com `POST /chamada/video`.
- Som do computador + microfone misturados num `AudioContext` numa trilha única que substitui a do microfone; mutar o microfone silencia só a voz. Parar (botão ou "parar compartilhamento" do navegador) restaura câmera (ou abre uma nova) e o microfone puro.
- Anuncia aos outros com sinal `{acao:'tela', ativa}` (CHA-17) e reanuncia para quem entra depois.
- `contentHint` e limites de envio pela prioridade (Nitidez: 15 fps, `maintain-resolution`; Fluidez: 30 fps, `maintain-framerate`).
- 🆕 `769bd7d` O **tile local mostra a própria tela compartilhada** (antes continuava mostrando a câmera): a stream local é trocada por uma nova (áudio + tela), e não só a trilha dentro da mesma; ao parar, outra stream nova com a câmera.
- 🆕 `8b43965` **Escolha da tela na janela da chamada:** com a chamada na janela própria (CHA-13), o `getDisplayMedia` é chamado pelo `navigator` **daquela janela**, então a escolha do navegador aparece nela (e não na aba do app, escondida atrás). **Fechar a escolha sem escolher** (`NotAllowedError`, conferido pelo nome do erro) **não é erro**: nada acontece (`call.escolherTela` devolve `null`); outros erros seguem como antes (ao iniciar a chamada viram erro; durante a chamada, só vão para o console).

**CHA-16 Troca de tipo áudio → vídeo**
- Botão "Vídeo" na barra de chamada de áudio: abre a câmera (sem câmera continua só recebendo vídeo), muda para vídeo, reconecta todos os peers e `POST /chamada/video {id}` (proteção contra clique duplo).
- Os outros recebem WS 56 (`VideoAtivado`): se já estão em vídeo → só reassinam quem republicou; senão modal "Vídeo ativado" / "<nome> ativou o vídeo" / "Deseja transmitir seu vídeo também?" com **"Apenas assistir"** (câmera desligada, tela única em quem ativou) e **"Transmitir também"**; sem resposta em **15 s** → "Apenas assistir".
- 🆕 `769bd7d` **Ligar o vídeo não derruba mais as conexões:**
  - etapas mostradas na janela (faixa no topo do vídeo) e na `CallBar`, com um giro: **"Abrindo a câmera..."** → **"Enviando o vídeo..."** → **"Recebendo o vídeo dos participantes..."**;
  - **republica sem interromper**: publica a transmissão nova (com vídeo) no mesmo caminho WHIP **antes** de fechar a antiga (o MediaMTX troca uma pela outra); se ele recusar a troca, fecha a antiga e publica de novo;
  - cada assinatura (WHEP) é **trocada só quando a nova está pronta**; o áudio segue pela antiga enquanto isso;
  - "Apenas assistir" já abre a câmera **desligada** (dá para ligar depois);
  - quem recebe o WS 56 **sempre reassina na hora** quem republicou (para voltar a ouvi-lo, e ver se já está em vídeo); só então pergunta, se ainda está em áudio.
- 🆕 `9c6e8a6` (servidor `ef13c22`) O botão "Vídeo" da `CallBar` (`title` "Ativar vídeo") só aparece com a permissão de uso `chamada_video` (GER-09); o `POST /chamada/video` responde 403 sem ela. Aceitar com "Transmitir também" no modal não chama essa rota.
- 🆕 `6d20ff7` (servidor) **A chamada passa a ser de vídeo:** o `POST /chamada/video` também troca o tipo da chamada de áudio (1) para vídeo (2). A mensagem da ligação (MSG-13, gravada no fim) e o histórico (CHA-22) mostram "vídeo"; os dados da chamada (`GET /chamada/dados`) também.

**CHA-17 Ponteiro remoto** (commit `20d5b73`)
- Quem compartilha a tela avisa por WS 57 `{acao:'tela', ativa:true|false}`. Quem assiste vê o botão "Apontar na tela compartilhada"/"Desligar ponteiro" (só com tela remota; desliga sozinho quando não há mais telas).
- Ligado, o movimento do mouse sobre a imagem da tela envia `{acao:'ponteiro', alvo:<id de quem compartilha>, x, y}` com x/y de 0 a 1 **relativos à imagem do vídeo** (descontando faixas de `object-contain`), no máximo a cada 40 ms (o último sempre vai); sair da imagem envia `x:null, y:null`.
- Todos que veem aquela tela (inclusive quem compartilha, sobre o próprio vídeo local) desenham o ponteiro com o nome e uma cor por usuário (`id % 5`). Ponteiro parado some após 5 s; some quando a pessoa sai ou a tela para. Sinal de outra chamada é ignorado.
- 🆕 `769bd7d` Novo sinal WS 57 **`{acao:'participantes'}`**: o servidor manda aos demais quando alguém que não atendeu é chamado de novo (CHA-25); o cliente relê `GET /chamada/dados` para atualizar a grade.

**CHA-18 Chat da chamada** — 🆕 na janela principal o painel virou o **chat completo** (CHA-24) (commit `20d5b73`); 🆕 `6d20ff7` (servidor): cada ligação nova volta a ter o **seu grupo**, nomeado com os participantes (fim do item)
- Painel "Chat da chamada" ao lado do vídeo (celular: por cima). Vazio: "As mensagens enviadas aqui ficam num grupo com quem está na chamada.". Só texto: anexos aparecem como "[Imagem]", "[Áudio]", "[Figurinha]", "[Arquivo]"; mensagens ocultas não aparecem. Campo "Mensagem", Enter envia. 🆕 `f351190` A localização aparece como **"[Localização]"** (ENV-30).
- 🆕 `eaa8bac` Agora o grupo é criado no **primeiro clique no campo** (não mais na primeira mensagem): sem grupo, o painel mostra o aviso e um campo-botão "Digite uma mensagem" ("Abrindo o chat..."); o clique chama `PUT /chamada/chat`, abre o chat completo (CHA-24) e põe o cursor no campo. Erro aparece numa faixa vermelha.
- (Antes de `eaa8bac`) O grupo só é criado na **primeira mensagem**: `PUT /chamada/chat {id}` → `{conversa_id}` (idempotente; guardado em `chamada.conversa_chat_id`). Se outro criou, chega WS 57 `{acao:'chat', conversa_id}`. Quem entra depois passa a fazer parte (servidor).
- Envio: `PUT /mensagem` no grupo; recarrega `GET /mensagens?conversa=<grupo>&…80`. Painel aberto marca as recebidas como visualizadas. Botão do chat mostra o número de não lidas do grupo quando fechado.
- 🆕 `964ed72` (servidor `a40fd47`) **O chat da chamada reaproveita a conversa de origem:** no primeiro `PUT /chamada/chat`, se a ligação começou numa conversa (`conversa_id` do `PUT /chamada/iniciar`) e **todos** os que estão na ligação já são membros dela, o chat **é essa conversa** — na chamada **direta**, o chat da chamada **é a própria conversa direta**; num grupo com todos, o próprio grupo (ou o chat de outra ligação). Só quando falta alguém é criado um grupo novo "Chamada: <nomes>". Na conversa reaproveitada os membros não mudam (quem entra depois não é incluído) e ela não ganha `chamada: true` (CON-01). A resposta continua `{conversa_id}` e o WS 57 `{acao:'chat', conversa_id}` avisa os demais. A bolha da ligação e o histórico mostram o chat (CHA-26) só quando ele **não** é a conversa da ligação. **🆕 `6d20ff7`: deixou de valer — ver o item seguinte.**
- 🆕 `6d20ff7` (servidor, junto de `e33ecba`) **O chat da ligação volta a ser um grupo novo:** no primeiro `PUT /chamada/chat`, a ligação **cria um grupo próprio**, mesmo na conversa direta ou num grupo com todos — o histórico deles não é o da ligação. O nome do grupo é a lista dos participantes em ordem alfabética, separados por vírgula (ex.: "Ana, Bruno"; acima de 80 caracteres corta em 77 + "..."), **sem o prefixo "Chamada: "**. Membros: quem esteve na ligação (entrou, saiu ou caiu) e quem abriu o chat; quem entra na ligação depois também é incluído. O grupo tem `chamada: true` (CON-01, seção "Chamadas (N)").
  - **Só reaproveita** quando a ligação foi feita **dentro do chat de outra ligação** e todos os da ligação já são membros dele: aí o chat é esse mesmo (sem mexer nos membros).
  - As ligações antigas cujo chat era a própria conversa continuam assim (sem "Ver chat da chamada").

**CHA-19 Indicador de fala** (`useFalaChamada.ts`)
- A cada 100 ms mede o RMS do microfone local e do áudio de cada participante (`AnalyserNode`, 1024 amostras); acima de **0,02** conta como fala; mantém marcado por **400 ms** depois de parar. Microfone mutado não conta.
- Exibição: anel verde nos avatares da `CallBar` e tooltip "Você está falando" / "<nome> está falando".

**CHA-20 Qualidade da chamada** (aba Configurações → Chamadas; `useConfigChamada.ts`) — ver CFG-06. Mudanças durante a chamada valem na hora (reabre o microfone para ruído/eco/ganho/qualidade; `applyConstraints` para resolução/fps; refaz parâmetros de envio).

**CHA-21 Somente recepção** — sem microfone nem câmera o tile local mostra "Somente recepção" e os botões "Ativar microfone" / "Ativar câmera" (abre a mídia e reconecta todos os peers para publicar).

**CHA-22 Histórico de chamadas** (`ChamadaHistorico.vue`)
- Título "Chamadas", abas **Todas** / **Perdidas** (status 5), filtro "Buscar contato" (local, pelo nome do outro participante), datas "De" e "Até" (calendário; formato `YYYY-MM-DD`; mudar recarrega).
- API: `GET /chamadas?participante=0&de=YYYY-MM-DD&ate=YYYY-MM-DD` → `ChamadaHistoricoItem[]` (`id, tipo, status, criado_em, criado_por, conversa_id, iniciada, finalizada, duracao, participantes:[{usuario_id, nome, status, duracao, avatar_url}]`).
- Agrupado por "Hoje"/"Ontem"/`dd/MM/aaaa`. Item: avatar do outro (grupo: "Grupo (N)"), seta efetuada/recebida (efetuada = `criado_por` sou eu), hora, "Video"/"Audio" · duração ou status ("Recusada", "Perdida", "Cancelada"), cor (vermelho perdida/recusada, cinza cancelada, verde demais).
- Clique abre a conversa da chamada; botão "Ligar novamente" inicia nova chamada do mesmo tipo com os mesmos participantes. Vazio: "Nenhuma chamada" / "Nenhuma chamada perdida".
- 🆕 `964ed72` O item ganha `conversa_chat_id` (o chat da ligação, só para quem é membro dele e só quando não é a conversa da ligação). Com ele, aparece um botão de balão com o título **"Ver chat da chamada"** / **"Esconder o chat da chamada"**, que abre **embaixo do item** o chat da ligação só para leitura (CHA-26); "Abrir o chat completo" abre a conversa.
- 🆕 `e33ecba` As imagens do chat da ligação aberto aqui abrem no visualizador do app (CHA-26). 🆕 `6d20ff7` (servidor) Chamada de áudio que ganhou vídeo aparece como **"Video"** (CHA-16).
- 🆕 `9c6e8a6` "Ligar novamente" só aparece com a permissão de uso do tipo da chamada (`chamada_video` ou `chamada_audio`, GER-09).

**CHA-23 Áudio remoto estável** — o áudio de cada participante toca num `HTMLAudioElement` criado pela store (vive a chamada inteira); os `<video>` são sempre mudos. Evita perder áudio ao alternar janela flutuante/tela cheia. "Áudio de saída" muta esses elementos.

(CHA-24, o chat completo na chamada, está na §10.)

**CHA-25 🆕 `769bd7d` Convidados fora da chamada na grade** (`CallWindow.vue`, `call.participantesAguardando`)
- Na grade aparecem, além de quem está conectado, os convidados que **não estão** na chamada (menos eu e quem já tem peer): status **Pendente** (o telefone deles está tocando) ou **Recusou sem nunca ter entrado** (`entrou_em` vazio). Quem entrou e saiu não aparece aqui.
- Tile apagado com as iniciais e o nome embaixo; Pendente mostra **"Chamando..."** (piscando); quem não atendeu mostra **"Não atendeu"** e o botão verde **"Chamar de novo"** (desabilitado até a resposta do servidor).
- API: `POST /chamada/chamar-novamente {chamada_id, usuario_id}` → `Chamada` (a lista nova substitui a atual). O servidor volta o convidado a Pendente, **faz tocar de novo** no aparelho dele (WS 51) e manda aos demais o sinal WS 57 `{acao:'participantes'}`, que faz cada um reler `GET /chamada/dados` (CHA-17). Só vale para quem recusou/não atendeu e nunca entrou, com a chamada em andamento; senão o servidor recusa com "Só dá para chamar de novo quem não atendeu, com a chamada em andamento.". Erro aparece na faixa vermelha (`erroMsg`, fallback "Erro ao chamar de novo").

**CHA-26 🆕 `964ed72` Chat da ligação só leitura na bolha e no histórico** (`ChatDaChamada.vue`) — 🆕 `e33ecba`: conteúdos como no chat (imagem com visualizador, áudio, arquivo, figurinha), 24rem
- Onde: dentro da bolha da ligação (MSG-13, botão "Ver chat da chamada" / "Esconder o chat da chamada") e embaixo do item do histórico (CHA-22). Só aparece quando a mensagem traz `chat_chamada_id` (conteúdo tipo 6) ou o item traz `conversa_chat_id` — o servidor manda só para quem é membro do chat e só quando o chat não é a própria conversa da ligação (CHA-18).
- Carrega `GET /mensagens?conversa=<chat>&mensagemreferencia=0&mensagensprevias=100` ao abrir. Textos: **"Carregando o chat..."**; erro (a mensagem do servidor ou **"Não foi possível carregar o chat"**); vazio **"Ninguém escreveu no chat da chamada."**.
- Linhas como uma citação com vários autores (barra à esquerda, altura máxima de 16rem com rolagem): **autor**, hora `HH:MM` e o texto. Mensagens ocultas não aparecem. Conteúdos: texto (menções como `@Nome`), **"📷 Imagem"**, **"📎 <nome do arquivo>"** (ou "📎 Arquivo"), áudio **"🎤 "<transcrição>""** ou **"🎤 Áudio"**, **"Figurinha"**, **"📊 Votação"**.
- Botão **"Abrir o chat completo"** abre a conversa do chat (na bolha, `chat.selecionarConversa`; no histórico, a mesma navegação do clique no item). Não há campo para responder.
- 🆕 `e33ecba` **Conteúdos como no chat:** cada mensagem mostra autor e hora e, embaixo, os conteúdos com o mesmo componente do chat (`MessageContent`): texto (com menções e código), **imagem** (sem moldura; clique abre o **visualizador** do app, na bolha e no histórico), **áudio** com o player, **arquivo** com o download, **figurinha** etc. Os resumos em texto ("📷 Imagem", "📎 …", "🎤 …", "📊 Votação") **saíram**. Ficam de fora as ocultas e as sem conteúdo. Altura máxima **24rem** (era 16rem), com rolagem.
- 🆕 `6d20ff7` (servidor) Como cada ligação nova tem o seu grupo (CHA-18), o chat aparece aqui sempre que foi aberto na ligação e a pessoa é membro dele.

### 3.8 SIP — Telefonia SIP (ramal)

**SIP-01 Configuração do ramal** (aba Voip)
- "Configuracao de ramal" (etiqueta "Asterisk / VueSIP"): "Ramal SIP", "Usuario de autenticacao", "Senha SIP", "Nome de exibicao", "Dominio SIP", "Servidor WebSocket" (ex. `wss://pbx.exemplo.com:8089/ws`), checkbox "Configuracao SIP ativa para este usuario".
- Validação: "Preencha ao menos ramal, senha SIP, dominio e servidor WebSocket.". Botão "Criar configuracao SIP" / "Salvar configuracao SIP"; "Nenhuma alteracao para salvar."; mensagens de sucesso.
- API: `GET /sip` → `SipConfig` ou `{}` (sem ramal → null); `PUT /sip {sip_user, auth_user, sip_password, display_name, domain, ws_server, ativo}`; `PATCH /sip {id, ...só o que mudou}`.
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Permissão de uso `telefonia`** (GER-09): sem ela, a aba Voip some (§2.5), o `GET /sip` devolve `{}` (o ramal não registra e o item "Ramal" da barra some, SIP-02) e `PUT`/`PATCH /sip` respondem 403 "Você não tem permissão para: usar a telefonia (sip).".

**SIP-02 Registro automático**
- No início da sessão, ao montar a lista e ao fechar as Configurações (forçando reconexão): carrega `GET /sip`; se `ativo` → pede permissão de microfone, cria `UserAgent` (`sip:<sip_user>@<domain>`, transporte `ws_server`, auth `auth_user||sip_user` + senha, display name), `Registerer.register()`, espera até 10 s ("O ramal SIP nao concluiu o registro a tempo."). Inativo → encerra.
- Status: barra (bolinha), discador ("Sem configuracao", "Ramal inativo", "Registrado", "Conectando", "Conectado sem registro", "Falha na conexao", "Desconectado"). Erros: "Conexao WebSocket perdida.", "Falha na conexao WebSocket: …".

**SIP-03 Discador** (`SipDialerModal.vue`)
- Janela flutuante arrastável: "Ramal" + número do ramal, status, campo "Digite o numero" (teclado de telefone), apagar último dígito, teclado 1–9/*/0/# com letras (ABC… e "+" no 0) e tom DTMF local (duas senoides 100 ms).
- "Ligar" ("Conectando..." enquanto registra) → `INVITE sip:<numero>@<domain>` só áudio; "Discando..." + destino + tom de discagem sintetizado (3 bipes a cada 2 s); "Cancelar".
- Em chamada: "Chamada em andamento" + destino, mutar/desmutar, "Encerrar chamada"; teclas enviam **DTMF in-band** (`RTCDTMFSender.insertDTMF`, 100 ms, gap 70 ms).
- Falha antes de estabelecer: "Chamada não completada — destino indisponível ou ocupado."; fim de chamada estabelecida toca tom de ocupado (4 bipes 425 Hz).

**SIP-04 Chamada SIP recebida** (`SipIncomingCallModal.vue`)
- Modal "Chamada recebida" com nome (display name ou usuário da URI, "Desconhecido") e número; toque sintetizado (2 bipes 440 Hz a cada 3 s); "Recusar" / "Atender". Atender abre o discador mostrando "Conectando..." e depois a chamada.

**SIP-05 ICE do SIP** — antes de cada chamada SIP o cliente busca `GET /ice` e passa como `peerConnectionConfiguration` (commit `bc11b32`, removeu TURN fixo).

### 3.9 NOT — Notificações

**NOT-01 Som de mensagem** — `/notification.mp3` (fallback: bipe 880 Hz 0,5 s). Toca para mensagens de outros **exceto**: conversa ativa com a janela focada, conversas arquivadas. Toca mesmo com a janela focada (em outra conversa).

**NOT-02 Notificação do sistema por conversa** (`utils/sound.ts`)
- Uma por conversa (agrupamento): título = nome do remetente da última mensagem; corpo = texto resumido ou "Imagem", "Gravacao de audio", "Audio", "Figurinha", "Arquivo" (🆕 também "Votação" e, 🆕 `f351190`, **"Localização"**); ícone = avatar do contato/conversa ou `/logo.png`; `tag: conversa-<id>`, `renotify`, `requireInteraction` (fica até interagir), `silent` (o som é o do app).
- Criada pelo service worker quando registrado (clique foca a aba e abre a conversa, mesmo após recarregar); senão pela página (`new Notification`, com cuidado do `onclose` assíncrono).
- 🆕 `d202c06` **Resposta a uma postagem** (WS 64, MSG-22): som de mensagem e notificação com título = nome da conversa (ou "Grupo"), corpo **"<nome> respondeu a uma postagem"**, `tag: conversa-<id>` (substitui a da conversa). Mesmas exceções do NOT-01 (conversa aberta e em foco, arquivada) e não avisa a minha própria resposta.

**NOT-03 Fechar notificação** — quando o contador de não lidas da conversa chega a 0 (leitura) e ao arquivar a conversa.

**NOT-04 Push em segundo plano (FCM)** — §1.6.

**NOT-05 Notificação de chamada** — CHA-03 (tag `conversa-chamada`, fechada quando para de tocar).

**NOT-06 Pedido de permissão** — no início da sessão e após o login; aba Permissões (CFG-04).

### 3.10 PRE — Presença

**PRE-01 Online/offline** — 🆕 `769bd7d`: virou **ativo / ausente / offline**, com visto por último
- (Até `e8d82cb`) `GET /contatos/online` → `number[]` a cada conexão do WS; WS 60 `{usuario_id, online:boolean}`; só bolinha verde. **Histórico.**
- 🆕 `769bd7d` **Três estados** (`EstadoPresenca`): **ativo** (o Conversa visível e em uso em alguma aba/aparelho), **ausente** (conectado, mas escondido ou parado há 5 min) e **offline**. A API junta as abas e aparelhos de cada pessoa a partir do que cada um envia (PRE-02).
- 🆕 `769bd7d` Carga: **`GET /contatos/presenca`** (substitui `/contatos/online`) → `[{usuario_id, estado, visto_em}]` a cada conexão do WS; quem não está na lista fica offline. O servidor só informa a presença de quem tem **conversa direta** comigo (lista e WS 60); membro de grupo sem direta fica sem bolinha. A lista **não traz o próprio usuário**: o próprio estado chega só pelo WS e é mantido se chegar antes da lista (`964ed72`).
- 🆕 `769bd7d` Tempo real: WS 60 `{usuario_id, online, estado, visto_em}` (sem `estado`, usa `online` → ativo/offline). `visto_em` só vem quando muda; sem ele, fica o que já se sabia. O servidor manda o WS 60 **também do próprio usuário** (PRE-02).
- 🆕 `769bd7d` **Bolinha** (`IndicadorPresenca.vue`): **verde** = ativo, **amarela** = ausente; offline **não mostra** bolinha (só no próprio avatar da barra, em cinza). Tooltip "Ativo" / "Ausente" / "Offline". Onde: lista de conversas (direta), cabeçalho (direta), "Nova conversa", participantes do painel do grupo, avatar da barra (PRE-02).
- O "visto por último" e o "nesta conversa" estão em PRE-03; o que cada um mostra aos outros, em CFG-09.

**PRE-02 🆕 `769bd7d` Presença enviada pelo cliente e o próprio status** (`stores/chat.ts#enviarPresenca`, `NavBar.vue`)
- Cada aba envia pelo WS **`{tipo:63, ativo, conversa_id}`**: `ativo` = página **visível** (`document.visibilityState === 'visible'`) **e** sem **5 min parada** (sem `pointerdown`, `pointermove`, `keydown`, `wheel` ou `touchstart`); `conversa_id` = a conversa aberta **só quando ativo** (senão `null`).
- O servidor trata toda conexão nova como **ativa e sem conversa** até chegar o primeiro 63.
- Envia ao conectar o WS (depois do login), quando a visibilidade muda, quando fica parada 5 min, no primeiro uso depois de parada e ao trocar de conversa. Não repete um aviso igual ao anterior (reconectar zera essa memória).
- **Próprio status:** o avatar da barra (NavBar) tem a bolinha de como os outros me veem (verde, amarela ou **cinza** para offline — por exemplo com "Aparecer offline", CFG-09), com o tooltip **"Para os outros você aparece ativo"** / **"… ausente"** / **"… offline"**. Vem do WS 60 com o meu id; com o WS caído, o próprio estado é apagado e aparece offline.

**PRE-03 🆕 `769bd7d` "Visto por último" e "nesta conversa"** (`ChatHeader.vue`, `PainelGrupo.vue`)
- Quem está **com a conversa aberta agora**: `GET /conversa/presentes?conversa=<id>` → `number[]` ao abrir uma conversa (e ao reconectar o WS, para a aberta); WS 63 recebido `{conversa_id, usuario_id, aberta}` acrescenta (`aberta: true`) ou tira a pessoa.
- **Subtítulo do cabeçalho da direta**, nesta prioridade:
  1. **"nesta conversa"** (cor primária) — a pessoa está com esta conversa aberta;
  2. **"ativo agora"**;
  3. **"ausente"**;
  4. offline com `visto_em`: **"visto por último hoje às HH:MM"**, **"visto por último ontem às HH:MM"** ou **"visto por último em dd/MM às HH:MM"** (com `/aaaa` se for de outro ano);
  5. offline sem `visto_em` (ou escondido pela privacidade): nada.
  O subtítulo some enquanto a pessoa digita/grava (o indicador de digitando ocupa o lugar).
- **Grupo:** no painel "Dados do grupo", **" · nesta conversa"** (cor primária) depois do nome de cada participante que está com o grupo aberto (não em mim). O cabeçalho do grupo não mostra presença.
- O que aparece depende da privacidade de cada um (CFG-09), aplicada pelo servidor: sem "Mostrar o visto por último" o `visto_em` vem nulo; sem "Mostrar quando estou na conversa" a pessoa nunca aparece "nesta conversa"; com "Aparecer offline" ela fica offline para os outros e fora das conversas (o visto por último para de andar).

### 3.11 ATV — Atividades (commit `39d5af1`)

**ATV-01 Tela de atividades** (`AtividadesPage.vue`, `stores/atividades.ts`)
- Título "Atividades". Vazio: "Nenhuma atividade ainda." / ~~"Reações, respostas, menções e chamadas perdidas aparecem aqui."~~ (🆕 `9c6e8a6` texto novo no fim do item).
- Agrupadas por "Hoje"/"Ontem"/`dd/MM/aaaa`. Item: avatar de quem fez com selo no canto (emoji da reação; "↩" resposta; "@" menção; "✆" chamada perdida; 🆕 `9c6e8a6` "✓" pedido de confirmação), "**<autor>** <descrição>", "em <grupo>" (só em grupo), prévia entre aspas (texto ou "Imagem"/"Arquivo"/"Áudio"/"Figurinha"; 🆕 também "Votação" e, 🆕 `f351190`, "Localização"; não para chamada), hora `HH:MM`, ~~bolinha "Nova"~~ 🆕 `9c6e8a6` bolinha "Pendente".
- Descrições: Reação "reagiu <emoji> à sua mensagem"; Resposta "respondeu sua mensagem"; Menção "mencionou você"; Chamada perdida "ligou (chamada perdida)" ou "ligou (chamada de vídeo perdida)"; 🆕 `9c6e8a6` Confirmação (tipo 5) "pediu que você confirme a leitura".
- Clique: com `mensagem_id` → abre a conversa na mensagem (MSG-06); sem (chamada perdida) → abre a conversa.
- Paginação: ao chegar a 200 px do fim, `GET /atividades?antes=<id da última>&limite=30`; fim quando volta < 30.
- API: `GET /atividades?antes=0&limite=30` → `Atividade[]` (`id, tipo (1 Reacao, 2 Resposta, 3 Mencao, 4 ChamadaPerdida; 🆕 `9c6e8a6` 5 Confirmacao), criado_em, ~~nova~~ 🆕 `9c6e8a6` pendente, autor_id, autor_nome, autor_avatar_url, conversa_id, conversa_tipo, conversa_descricao, mensagem_id, conteudo_tipo, texto, chamada_id, chamada_tipo, emoji`).
- 🆕 `e25c4db` (servidor; o web não mudou) **`nova` desconta o que já foi lido no chat:** resposta e menção cuja mensagem eu **já visualizei** voltam com `nova: false` (perdem a bolinha "Nova"), mesmo depois da última abertura da tela. Resposta de **postagem** (que aponta para a postagem, MSG-22) fica lida quando não há resposta daquela pessoa **sem ler** na postagem. Reação e chamada perdida seguem só pela abertura da tela (`atividades_vistas_em`). **🆕 `9c6e8a6`: `nova` deixou de existir — ver o item seguinte.**
- 🆕 `9c6e8a6` (servidor `ef13c22`, migração 044) **Pendente até concluir**, no lugar de "nova até abrir a tela":
  - ~~`nova` (chegou depois da última abertura da tela)~~ → **`pendente: boolean`**: a atividade fica pendente até ser **concluída**. A abertura da tela não conclui nada;
  - cada tipo conclui sozinho: **reação** ao ver a própria mensagem na tela (ATV-05); **resposta e menção** ao ler a mensagem (a regra do `e25c4db`); **pedido de confirmação** ao confirmar a leitura (MSG-21); **chamada perdida** ao abri-la na tela (ATV-04); e qualquer uma **à mão**, pelo ✓ (ATV-04);
  - na migração, as que a pessoa já tinha visto na tela entram concluídas;
  - o item pendente fica destacado (fundo na cor principal) e com a bolinha de `title` **"Pendente"** (era "Nova").
- 🆕 `9c6e8a6` **Tipo novo 5, `Confirmacao`:** alguém pediu que eu confirme a leitura de uma mensagem (ENV-24, ENV-27). Descrição **"pediu que você confirme a leitura"**, selo **"✓"** (verde), prévia como as outras; o clique abre a conversa na mensagem.
- 🆕 `9c6e8a6` Texto do vazio: ~~"Reações, respostas, menções e chamadas perdidas aparecem aqui."~~ → **"Reações, respostas, menções, pedidos de confirmação de leitura e chamadas perdidas aparecem aqui."**
- 🆕 `9c6e8a6` (servidor) O **contador** (ATV-02) passa a contar só atividades de conversas de que a pessoa **ainda participa**, como a lista já fazia (a chamada perdida sem conversa também conta).

**ATV-02 Contador de novas**
- Badge no item Atividades da barra (`99+`). Fonte: `GET /atividades/novas` → `{quantidade}` ao conectar o WS e a cada WS 61.
- ~~Abrir a tela: carrega a lista e `POST /atividades/vistas` → `{vistas_em}`, zera o contador; os itens continuam destacados como "nova" enquanto a tela está aberta. Com a tela aberta, WS 61 recarrega e marca como visto; fechada, só atualiza o contador.~~ 🆕 `9c6e8a6`: abrir não zera mais; com a tela aberta, o WS 61 recarrega a lista e o contador; fechada, só o contador (fim do item).
- 🆕 `e25c4db` (servidor) **O contador desconta o que já foi lido:** `GET /atividades/novas` não conta resposta/menção já visualizada (mesma regra do ATV-01). E o servidor manda o **WS 61 também quando a pessoa lê** (`POST /mensagem/visualizar` ou `POST /postagem/visualizar`) uma mensagem ou resposta de postagem que tem atividade de resposta/menção ligada: o contador cai em todas as abas e aparelhos, sem abrir a tela. O web não mudou (o WS 61 já relê o contador).
- 🆕 `9c6e8a6` (servidor `ef13c22`) **O contador é o de pendentes:**
  - `GET /atividades/novas` → `{quantidade}` conta as **pendentes** (ATV-01), não mais as chegadas depois da última abertura da tela;
  - abrir a tela **não zera**: o web carrega a lista e relê o contador (`Promise.all([carregar(), atualizarNovas()])`); ~~`POST /atividades/vistas`~~ **não é mais chamado** pelo web (a rota continua no servidor, mas não muda mais o contador);
  - o servidor manda o **WS 61** também ao concluir à mão (ATV-04), ao ver reações (ATV-05) e ao confirmar uma leitura pedida (MSG-21), para o número acompanhar em todas as abas e aparelhos.

**ATV-03 Geração de "chamada perdida" pelo cliente** — toda recusa automática (30 s sem resposta, ocupado, pendente antiga > 25 s) envia `nao_atendeu: true` em `POST /chamada/recusar`, o que o servidor transforma em atividade de chamada perdida. **O Android precisa fazer o mesmo.**

**ATV-04 🆕 `9c6e8a6` Concluir a atividade** (`AtividadesPage.vue`, `stores/atividades.ts#concluir`; servidor `ef13c22`)
- **À mão:** cada item **pendente** tem, à direita, um botão redondo com ✓, `aria-label` **"Marcar como concluída"** e `title` **"Marcar como concluída"** (no pedido de confirmação: **"Marcar como concluída (a leitura continua sem confirmar)"**). Item já concluído fica com o espaço vazio no lugar do botão.
- **Chamada perdida:** concluída **ao ser aberta** (o clique no item conclui e abre a conversa).
- API: **`POST /atividade/concluir {id}`** → `{sucesso: true}`. Só conclui a do próprio usuário que ainda não foi concluída; quando conclui, o servidor manda o WS 61 (as outras abas e aparelhos releem o contador).
- No web: só chama se o item está pendente na lista; depois marca `pendente = false` e desconta 1 do contador.
- Concluir o pedido de confirmação **não confirma** a leitura (a caixa "Confirmar leitura" da mensagem continua, MSG-21).

**ATV-05 🆕 `9c6e8a6` Reação vista na tela conclui a atividade** (`useScrollManager.ts#validarMensagensCompletamenteVisiveis`; servidor `ef13c22`)
- No mesmo laço que marca as visualizadas (MSG-04), **só com a janela focada**: cada **mensagem minha** salva (id > 0) com reação **de outra pessoa** (soma das contagens menos a minha) que tem **qualquer parte à vista** entra na lista.
- API: **`POST /atividades/reacoes-vistas {mensagens: number[]}`** (até 200) → `{concluidas}`: conclui as atividades de reação dessas mensagens criadas até agora; se concluiu alguma, o servidor manda o WS 61.
- Cada mensagem é avisada **uma vez por quantidade** de reações de outros (chave `id:quantidade`): reação nova na mesma mensagem avisa de novo. Se o pedido falha, avisa de novo na próxima vez que ela aparecer.

### 3.12 CFG — Configurações, personalização e administração

**CFG-01 Abas e navegação** — §2.5 (celular: lista de abas → sub-tela com botão voltar). 🆕 `769bd7d` Aba nova **Privacidade** (`/config/privacidade`), a segunda da lista, logo depois de Usuario (CFG-09). 🆕 `9c6e8a6` Abas novas de administração **Usuários**, **Ferramentas**, **Conhecimento** e **Bots** (CFG-11 a CFG-14), no fim; Voip, Cores e Chamadas passam a depender das permissões de uso `telefonia`, `cores` e `config_chamadas` (GER-09).
- 🆕 `f351190` **Menus com sub-abas** (tabela e regras no fim da §2.5): a lista da esquerda tem menus em duas seções, "Pessoal" e "Administração", e cada menu tem as suas sub-abas numa barra no topo do conteúdo. O endereço continua o da sub-aba; o clique no menu abre a primeira visível; `/config/acessos` leva a `usuarios`. Onde ficou cada aba antiga:
  - Usuario → **Conta › Perfil**; Privacidade → **Conta › Privacidade** (CFG-09);
  - Cores → **Aparência › Tema** (CFG-05, CFG-10, e o claro/escuro do CFG-02);
  - Dispositivos → **Dispositivos › Sessão e periféricos** (CFG-03); Permissoes → **Dispositivos › Permissões do navegador** (CFG-04);
  - Chamadas → **Chamadas › Qualidade** (CFG-06); Voip → **Chamadas › Voip** (SIP-01);
  - Sistema → **Sistema** (só a parte geral) e **IA › Servidor** (a parte de IA, `/config/ia`, CFG-07);
  - Acessos e Usuários → **Pessoas › Usuários**, uma tela só (CFG-08, CFG-11);
  - Bots, Conhecimento e Ferramentas → **IA › Bots**, **IA › Conhecimento**, **IA › Ferramentas** (CFG-12 a CFG-14).

**CFG-02 Tema escuro** — botão na aba Usuário ("Tema claro"/"Tema escuro"); padrão segue `prefers-color-scheme`; persistido em `theme`. Também aplicado no popup. 🆕 `9c6e8a6` O botão só aparece com a permissão de uso `cores` (GER-09; só o cliente confere). 🆕 `f351190` O botão **saiu da aba do perfil**: virou o par **"Claro"** (ícone de sol) / **"Escuro"** (ícone de lua) no topo de **Aparência › Tema**, à direita do título "Tema" (CFG-10), com `aria-pressed`; o ativo fica cheio na cor principal e clicar nele não faz nada. Sem `cores`, o menu Aparência inteiro some.

**CFG-03 Dispositivos** — "Sessao atual" (etiqueta "Ativo"): Dispositivo, Modelo, Sistema, Plataforma (detectados localmente); "Dispositivos de midia" com "Atualizar lista" (enumera microfones, câmeras, saídas; etiquetas "Padrao"/"Comunicacao"). Sem API (não lista os outros dispositivos do usuário no servidor).

**CFG-04 Permissões do navegador** — "Permissoes do navegador": Notificacoes, Microfone, Camera com status "Concedida"/"Negada"/"Nao solicitada"/"Indisponivel" e botão "Solicitar"; dica do cadeado na barra de endereço.

**CFG-05 Cores personalizadas** (`ConfiguracaoCores.vue`, `useCoresPersonalizadas.ts`)
- Edita as cores do **tema atual** (claro e escuro separados), valem na hora, salvas no navegador. Grupos: Principal (primary 50–900), Superfícies (surface base, 50–900; escala invertida no escuro), Sucesso, Perigo, Informação, Alerta, Chamada (300–900 — janela de chamada escura nos dois temas). Cada tom com descrição de uso. Busca por variável ou uso; "Restaurar todas", "Restaurar grupo", "Voltar ao padrão" por cor.
- 🆕 `8b43965` **Por cima do tema pronto** (CFG-10): os ajustes feitos à mão valem por cima das cores do tema escolhido (`{...tema, ...ajustes}`, por claro/escuro); o valor mostrado em cada cor é o ajuste, senão o do tema, senão o padrão; restaurar uma cor volta à do tema. **Escolher um tema apaga todos os ajustes** (claro e escuro). Mudar o tema ou as cores numa janela (popup de conversa, por exemplo) vale nas outras (evento `storage` das duas chaves).
- 🆕 `f351190` Texto da edição: ~~"Para editar o outro tema, troque o tema na aba Usuário."~~ → **"Para editar o outro tema, troque entre claro e escuro acima."** (os botões "Claro" / "Escuro" do CFG-02).

**CFG-06 Qualidade das chamadas** (`ConfiguracaoChamadas.vue`) — salvo em `conversa.chamada`; "Restaurar padrão".
- Áudio: "Redução de ruído", "Cancelamento de eco", "Ganho automático do microfone" (padrão ligados); "Qualidade do áudio": Normal 32 kbps (padrão) / Alta 64 kbps / Música estéreo 128 kbps (estéreo vale na próxima chamada).
- Vídeo: Resolução 360p/720p/1080p (padrão 720p desktop, 360p celular); FPS 15/24/30 (padrão 24 desktop, 15 celular); Limite de banda Automático / Econômico 0,5 Mbps / Alto 3 Mbps.
- Tela: Prioridade Nitidez (padrão; até 15 fps) / Fluidez (até 30 fps).

**CFG-07 Sistema — parâmetros do servidor** (`ConfiguracaoSistema.vue`, permissão `parametros`)
- Aviso "Valem para todos os usuários e entram em vigor ao salvar, sem reiniciar o servidor.".
- Notificações (Firebase): "ID do projeto", "E-mail da conta de serviço", "Chave privada" ("(configurada)"/"(não configurada)"; nunca exibida; vazio mantém).
- Chamadas: "Forçar relay pelo TURN"; "Guardar as gravações por (dias)" (0 = para sempre, 0–36500).
- Transcrição: "Endereço do transcritor" (vazio desliga o Transcrever), "Idioma".
- Armazenamento: bucket só leitura.
- Salva **só o que mudou** (chave só se digitada); "Configurações salvas.".
- API: `GET /parametros` → `{fcm_project_id, fcm_client_email, fcm_private_key_configurada, turn_forcar_relay, transcritor_url, transcritor_idioma, gravacao_dias, s3_bucket}`; `PATCH /parametros {campos alterados, fcm_private_key?}` → mesmos campos.
- 🆕 `769bd7d` Seção **"Inteligência artificial"** (entre Transcrição e Armazenamento):
  - aviso "Servidor no padrão da OpenAI (vLLM, Ollama ou OpenAI), usado no resumo das conversas. As mensagens resumidas são enviadas a ele: prefira um servidor da sua rede.";
  - **"Endereço"** (placeholder "http://ollama:11434/v1"; dica "Com ou sem o /v1. Em branco desliga os recursos de IA.");
  - **"Modelo"** (placeholder "llama3.1, qwen2.5:14b, gpt-4o-mini...");
  - **"Token"** com a situação "(configurado)" / "(será removido)" / "(sem token)"; campo de senha com placeholder "Deixe em branco para manter o atual" (com token) ou "Só se o servidor pedir (Ollama não pede)" (sem); dica "O token salvo nunca é mostrado." e o link "Remover o token" / "Manter o token";
  - botão **"Testar conexão"** ("Testando..."; habilitado com endereço e modelo preenchidos) — testa o que está na tela, antes de salvar (token em branco usa o salvo); resultado em verde `Funcionando (1.2 s): "<resposta>"` (segundos com uma casa e ponto decimal) ou o erro em vermelho.
  - API: `GET /parametros` ganha `ia_url, ia_modelo, ia_token_configurado` (o token nunca volta); `PATCH /parametros` aceita `ia_url, ia_modelo, ia_token` (`''` remove o token); `POST /parametros/ia/testar {url, modelo, token?}` → `{ok, resposta, erro, milissegundos}`. Depois de salvar, o web relê `GET /recursos` (GER-08).
- 🆕 `9c6e8a6` (servidor `ef13c22`) Seção nova **"Cadastro"**, a primeira depois do aviso: caixa **"Permitir que as pessoas se cadastrem"** — "Desligado, a tela de entrada não oferece o cadastro: só quem tem a permissão Usuários cria contas, na aba Usuários." Campo `cadastro_aberto` (padrão ligado; parâmetro `'1'`/`'0'`, migração 048). Ver AUT-01, AUT-02 e CFG-11.
- 🆕 `9c6e8a6` Na seção "Inteligência artificial", depois de "Modelo": **"Modelo de embeddings"** (placeholder "nomic-embed-text, text-embedding-3-small..."; dica "Para a base de conhecimento dos bots, no mesmo servidor. Vazio desliga a base. Trocou de modelo: use "Reindexar tudo" em Conhecimento."). Campo `ia_modelo_embeddings` (CFG-14).
- 🆕 `9c6e8a6` API: `GET /parametros` e `PATCH /parametros` ganham `cadastro_aberto` (boolean) e `ia_modelo_embeddings` (texto).
- 🆕 `f351190` **Dividida em duas telas** (prop `parte` do `ConfiguracaoSistema`), com a mesma permissão `parametros`, o mesmo aviso no topo e o mesmo "Salvar" (cada uma manda só o que mudou):
  - **Sistema** (parte geral): Cadastro, Notificações (Firebase), Chamadas, Transcrição de áudio e Armazenamento;
  - **IA › Servidor** (`/config/ia`): só a seção "Inteligência artificial".
- 🆕 `f351190` Texto do Cadastro: ~~"… só quem tem a permissão Usuários cria contas, na aba Usuários."~~ → **"Desligado, a tela de entrada não oferece o cadastro: só quem tem a permissão Usuários cria contas, em Pessoas › Usuários."** (o texto ainda diz "permissão Usuários", embora ela agora seja `permissoes`, "Pessoas", GER-09).
- 🆕 `f351190` (servidor `ac710eb`, migração 053) **Servidor de embeddings próprio.** O "Modelo de embeddings" saiu do meio da seção e foi para a subseção nova **"Embeddings (base de conhecimento dos bots)"**, no fim de "Inteligência artificial":
  - **"Endereço do servidor de embeddings"** — placeholder **"Vazio: o mesmo servidor da IA"**; dica **"Só se os embeddings rodarem em outro host ou porta, ex.: http://192.168.0.20:1234."** (`ia_embeddings_url`);
  - **"Token do servidor de embeddings"**, **só com o endereço preenchido** — situação "(configurado)" / "(será removido)" / "(sem token)"; placeholder "Deixe em branco para manter o atual" (com token) ou **"Só se o servidor pedir"** (sem); dica **"O token da IA não vai para este servidor; o salvo aqui nunca é mostrado."** e o link "Remover o token" / "Manter o token". Vai como `ia_embeddings_token`: só se digitado, ou `''` para remover;
  - **"Modelo de embeddings"** — placeholder "nomic-embed-text, text-embedding-3-small..."; dica nova **"Vazio desliga a base. Trocou de modelo: use "Reindexar tudo" em Conhecimento."**;
  - botão **"Testar embeddings"** ("Testando..."; habilitado com o endereço próprio **ou** o da IA, e com o modelo). Testa o que está na tela, antes de salvar: `POST /parametros/ia/testar-embeddings {url: <endereço próprio, ou ''>, url_ia: <endereço da IA>, modelo, token?}` → `{ok, dimensoes, erro, milissegundos}`. O token enviado é o dos embeddings quando há endereço próprio, ou o da IA quando não há; em branco, o servidor usa o salvo do servidor usado. Resultado em verde **"Funcionando (0.1 s): vetores de 768 dimensões"** (segundos com uma casa e ponto) ou o erro em vermelho.
  - API: `GET /parametros` ganha `ia_embeddings_url` e `ia_embeddings_token_configurado` (o token nunca volta); `PATCH /parametros` aceita `ia_embeddings_url` e `ia_embeddings_token`. Servidor: endereço que não começa com `http://` ou `https://` → 400 "Endereço do servidor de embeddings inválido (use http:// ou https://)."; teste sem endereço nenhum → 400 "Defina o endereço do servidor de IA ou o de embeddings.". A base de conhecimento fica ligada (`recursos.conhecimento`, GER-08) com um servidor de embeddings (o próprio ou o da IA) e o modelo.

**CFG-08 Acessos — permissões** (`ConfiguracaoAcessos.vue`, permissão `permissoes`)
- "Modo aberto" (ninguém tem Acessos): aviso de que todos podem mexer; marcar Acessos para alguém encerra o modo.
- ~~"O que cada permissão libera" (nomes: `parametros` → "Sistema", `permissoes` → "Acessos"), busca "Buscar usuário" (nome/login), tabela usuário × permissão com checkboxes ("(você)").~~ (🆕 `9c6e8a6` refeita: ver o fim do item.)
- API: `GET /permissoes` → `{permissoes:[{codigo, descricao}], usuarios:[{id, nome, login, permissoes:[]}], modo_aberto}`; `PUT /permissao/usuario {usuario_id, codigo}`; `DELETE /permissao/usuario?usuario_id=&codigo=`. Recusa do servidor mostra o motivo e a caixa volta. Após cada mudança recarrega as próprias permissões (abas visíveis acompanham).
- 🆕 `9c6e8a6` (servidor `ef13c22`) **Tela refeita para as permissões de uso** (GER-09):
  - ~~a seção "O que cada permissão libera" e a tabela usuário × permissão~~ saíram. No lugar, o texto "Abra uma pessoa para ver e mudar o que ela pode fazer: a administração do sistema e o uso do chat. Conta nova começa com todas as de uso. Ninguém retira as próprias permissões: outra pessoa com Acessos precisa fazer isso.";
  - cada pessoa é uma linha que **abre e fecha** (`aria-expanded`): nome ("(você)"), login, a pílula **"Administração"** (com os nomes no `title`) se tem alguma de administração, e **"N de T"** permissões;
  - aberta, as caixas em grupos: **"Administração"**, **"Enviar"**, **"Mensagens"**, **"Grupos"**, **"Chamadas"**, **"Configurações"** (e "Outras", para código de uso que o web não conhece), com os nomes curtos de `utils/permissoesUso.ts` (§6.2) e a descrição do servidor no `title`;
  - as **próprias** marcadas ficam **travadas**, com o `title` "Você não pode retirar as próprias permissões"; o servidor responde 400 "Você não pode retirar as próprias permissões." ao `DELETE /permissao/usuario` com o próprio id;
  - `GET /permissoes` traz **`uso: boolean`** em cada permissão (separa as de uso das de administração) e não traz os bots excluídos; a busca "Buscar usuário" continua (nome ou login).
- 🆕 `f351190` (servidor `ac710eb`, migração 054) **Não é mais uma aba:** a lista entrou em **Pessoas › Usuários** (CFG-11), embaixo do "Novo usuário", e `/config/acessos` leva para lá. A permissão `permissoes` aparece como **"Pessoas"** (era "Acessos"). Mudanças na tela:
  - modo aberto: **"Ninguém tem a permissão Pessoas ainda:"** (em negrito na tela) seguido de **"todos os usuários podem mexer nas configurações e nas permissões. Marque Sistema e Pessoas para quem deve cuidar disso; ao marcar Pessoas para alguém, só passa a valer o que estiver marcado."**;
  - o parágrafo "Abra uma pessoa para ver…" saiu; ficou **"Ninguém retira as próprias permissões: outra pessoa com a permissão Pessoas precisa fazer isso."**;
  - cada linha: nome, " (você)", a pílula **"Bot"** (nova), login, a pílula "Administração" e, à direita, **três colunas de largura fixa** (uma não empurra a outra): o **espaço** usado (ANX-17, `title` "Espaço dos arquivos que enviou"), a **data do cadastro** (`dd/mm/aaaa`, `title` "Usuário desde", vazia sem data, AUT-11) e **"N de T"**;
  - API: `GET /permissoes` → cada item de `usuarios` ganha **`criado_em`** (`Date | null`), **`bot`** e **`espaco`** (bytes); a descrição de `permissoes` passou a ser "Cadastrar pessoas e bots e conceder e retirar permissões".

**CFG-09 🆕 `769bd7d` Aba Configurações › Privacidade** (`ConfiguracaoPrivacidade.vue`; seção de IA desde `f798e9c`)
- Rota `/config/privacidade`; aba "Privacidade" / "Status, visto por último e aparecer offline"; para todos. "Carregando..." enquanto lê.
- Seção **"Status"**, texto "Os contatos veem você ativo com o Conversa aberto e em uso, ausente quando ele está escondido ou parado há 5 minutos. Valem para todos os seus aparelhos e mudam na hora." e três caixas:
  - **"Aparecer offline"** — "Para os outros você fica offline, sem visto por último nem a conversa aberta." (padrão desligada);
  - **"Mostrar o visto por último"** — "Quando você esteve ativo pela última vez, para quem tem conversa com você." (padrão ligada);
  - **"Mostrar quando estou na conversa"** — "Quem está na conversa vê que você está com ela aberta." (padrão ligada).
  Com "Aparecer offline" marcada, as outras duas ficam desabilitadas.
- API: `GET /usuario/privacidade` → `{mostrar_visto_em, mostrar_na_conversa, aparecer_offline}`; cada clique salva na hora com `PATCH /usuario {id, <chave>: valor}` (otimista; erro volta a caixa e mostra a mensagem, fallback "Erro ao salvar a privacidade"; erro ao ler: "Erro ao carregar a privacidade"). O servidor aplica na hora a todos os aparelhos.
- 🆕 `f798e9c` Seção **"Inteligência artificial"**, só com a IA ligada no servidor (GER-08): caixa **"Sugerir a continuação ao digitar"** — "Depois de uma pausa, a IA sugere em cinza como continuar a mensagem: Tab aceita, Esc descarta. Para isso, o que você digita e as últimas mensagens da conversa vão para o servidor de IA do sistema. Vale só neste navegador." Salva no `localStorage` (`conversa.sugestoesIa` = `1`/`0`; padrão ligada) — ENV-25.
- 🆕 `9c6e8a6` Sem a permissão de uso `privacidade` (GER-09), aparece no topo "Você não tem permissão para mudar a privacidade." e **todas as caixas** ficam desabilitadas (inclusive a da sugestão da IA, que é só do navegador). O servidor responde 403 "Você não tem permissão para: alterar as opções de privacidade." ao `PATCH /usuario` com as três chaves.

**CFG-10 🆕 `8b43965` Temas prontos** (`ConfiguracaoCores.vue`, `utils/temasPredefinidos.ts`, `useCoresPersonalizadas.ts`, `style.css`)
- Em Configurações › Cores, uma seção nova no topo, **"Tema"**, com o texto **"Cores prontas para os temas claro e escuro. Escolher um tema apaga as cores ajustadas abaixo."** e quatro botões: **"Padrão"**, **"Teams"**, **"WhatsApp"**, **"Messenger"** (grade de 2 colunas; 5 em tela larga). Cada um tem uma amostra nas cores do tema, no claro ou escuro atual (fundo `surface-100` com duas pílulas, `surface-200` e `primary-600`); o escolhido fica com a borda na cor principal (`aria-pressed`).
- O que cada tema troca: a **cor principal** (`--color-primary-50…900`) e, em alguns, os **fundos** (`--color-surface-base`, `-50…-400`) e os **balões**. Padrão: nada (o `style.css`). Teams: principal; no escuro, também fundos e balão recebido. WhatsApp: principal e fundos; no claro, **enviada verde-clara com texto escuro** e recebida branca. Messenger: principal e o **degradê** nos balões enviados (`--gradiente-enviada`, preso à tela, muda de cor conforme a mensagem sobe); no escuro, também fundos e recebida.
- **Balões com variáveis próprias** (`style.css`): `--bolha-enviada` (padrão = `primary-600`), `--bolha-enviada-texto` (branco), `-suave` (ícones e controles; `primary-100`), `-destaque` (links e progresso; `primary-100`), `-sucesso` (`success-300`), `-perigo` (`danger-300`) e `--bolha-recebida` (`surface-300`; no escuro `surface-200`). Sem tema, o visual é o de antes. No escuro, textos e bordas com `primary-600/700` ganharam um tom claro (`dark:text-primary-400`…), para não sumirem quando o tema escurece esses tons.
- Guardado **por navegador** no `localStorage`, chave **`conversa.temaCores`** (id `padrao`, `teams`, `whatsapp`, `messenger`; inválido ou ausente → `padrao`); vale para claro e escuro. Os ajustes à mão (CFG-05) ficam por cima; escolher um tema os apaga. Sem API.
- 🆕 `9c6e8a6` A aba Cores inteira (CFG-05 e CFG-10) só aparece com a permissão de uso `cores` (GER-09; só o cliente confere).
- 🆕 `f351190` A aba virou **Aparência › Tema**. Na seção "Tema", à direita do título e do texto, os botões **"Claro"** e **"Escuro"** (CFG-02) vêm antes dos temas prontos (nos testes, os botões com `aria-pressed` são "Claro", "Escuro", "Padrão", "Teams", "WhatsApp", "Messenger"; com Teams no escuro, os marcados são "Escuro" e "Teams").

**CFG-11 🆕 `9c6e8a6` Aba Configurações › Usuários** (`ConfiguracaoUsuarios.vue`; permissão de administração `usuarios`; servidor `ef13c22`)
- Texto: "Crie contas para pessoas ou bots, mesmo com o cadastro pela tela de entrada fechado (em Sistema). Conta nova começa com todas as permissões de uso; o bot, só com o básico. Ajuste depois em Acessos (e o bot também em Bots)."
- Botão **"Novo usuário"** abre o formulário **"Novo usuário"**:
  - foto: avatar de 64 px (prévia ou inicial, "?" sem nome), **"Escolher foto"** / **"Trocar foto"** e **"Remover"**; a foto passa por `redimensionarImagem` e **só sobe ao criar** (upload tipo 2 `avatar.jpg`);
  - caixa **"É um bot de IA"** — "Responde com a IA; não entra pelo login, então não tem login, e-mail nem senha."; com a IA desligada, o aviso "A IA está desligada: configure o servidor de IA em Sistema para criar bots.";
  - **"Nome"** (até 60); pessoa: **"Login"**, **"E-mail"** e **"Senha"** ("Entre 4 e 72 caracteres. A pessoa pode trocar depois, se tiver a permissão."); bot: **"Instruções (personalidade e o que ele faz)"** (até 8000);
  - **"Cancelar"** e **"Criar"** ("Criando..."), habilitado com tudo preenchido (senha ≥ 4; bot só com a IA ligada).
- Sucesso: **"Conta de <nome> criada (login <login>)."** ou **"Bot <nome> criado."**; erro na caixa vermelha (fallback "Erro ao criar o usuário").
- Lista embaixo: campo **"Buscar usuário"** (nome ou login) e cada usuário com nome, login e a pílula **"Bot"**; "Nenhum usuário encontrado.". A lista vem de `GET /usuario/contatos`.
- API: **`PUT /usuario/novo {nome, login?, email?, senha?, telefone?, avatar_anexo_id?, bot?, instrucoes?}`** → `{id, nome, login, bot}`. Pessoa: 400 "Preencha nome, login, e-mail e senha." / "Login já cadastrado!"; bot: 400 "IA não configurada: defina o endereço e o modelo nas configurações do sistema."; avatar que não é imagem: 400 "Avatar inválido: envie uma imagem.". O login do bot é gerado (`bot-<nome>-<6 caracteres>`).
- 🆕 `f351190` (servidor `ac710eb`, migração 054) **Virou Pessoas › Usuários, uma tela só**, com a permissão de administração **`permissoes`** ("Pessoas"); a `usuarios` foi juntada a ela (quem a tinha ganhou `permissoes`) e deixou de existir:
  - texto: ~~"Crie contas para pessoas ou bots, mesmo com o cadastro … Ajuste depois em Acessos (e o bot também em Bots)."~~ → **"Crie contas para pessoas ou bots, já escolhendo o que cada uma pode fazer, mesmo com o cadastro pela tela de entrada fechado (em Sistema). Abra uma pessoa na lista para ver e mudar os acessos dela; o bot também se ajusta em Bots."**;
  - a lista de baixo (antes de `GET /usuario/contatos`, com "Buscar usuário", nome, login e "Bot") **foi trocada pela de Acessos** (CFG-08, com o selo "Bot", o espaço, a data do cadastro e o painel de cada um); depois de criar uma conta, ela volta a carregar;
  - o formulário "Novo usuário" ganha a seção **"Acessos"**, antes dos botões: dica **"Vem com todas as de uso marcadas e nenhuma de administração."** (pessoa) ou **"O bot só recebe permissões de uso; vem com o básico marcado."** (bot); **"Carregando as permissões..."** enquanto lê `GET /permissoes`; caixas em grupos — **"Administração"** (só para pessoa) e os grupos de uso de §6.2 —, com o nome curto e a descrição do servidor no `title`;
  - padrão marcado: pessoa com **todas as de uso**; bot com **`emoji`, `responder` e `reagir`**. Marcar ou desmarcar "É um bot de IA" volta ao padrão do tipo. "Criar" só fica habilitado com as permissões carregadas;
  - API: `PUT /usuario/novo` leva **`permissoes: string[]`**, na ordem em que ficaram marcadas (ex.: `['emoji', 'responder', 'reagir', 'parametros']`); sem a lista, o servidor usa o padrão. Servidor: código desconhecido → 400 "Permissão inválida: <código>"; bot com permissão de administração → 400 "Bot só recebe permissões de uso."; a lista **substitui** o padrão.

**CFG-12 🆕 `9c6e8a6` Aba Configurações › Bots** (`ConfiguracaoBots.vue`; permissão de administração `bots`, ou `permissoes`, que vê e edita todos; servidor `src/bots.ts`, migrações 047 e 050)
- Texto: "Cada bot é um usuário do chat que responde com a IA. Ele aparece nos contatos: na conversa direta responde a toda mensagem; num grupo, quando é mencionado (@Nome) ou quando respondem a uma mensagem dele. O que ele pode fazer segue as permissões de uso dele." Com a IA desligada: "A IA está desligada: os bots não respondem e não dá para criar novos. Configure o servidor de IA em Sistema."
- Lista: inicial, nome, pílula **"Ligado"** / **"Desligado"**, login e " · criado por <nome>" (quando foi outra pessoa), botão **"Editar"**; vazio "Nenhum bot ainda.". Botão **"Novo bot"** (só com `bots`; desabilitado sem IA).
- Formulário (**"Novo bot"** ou **"Editar <nome>"**): **"Nome"** (até 60, placeholder "Ex.: Assistente de Compras"), **"Instruções (personalidade e o que ele faz)"** (até 8000, contador "N / 8000"). Editando, também:
  - **"Ligado (desligado, não responde a ninguém)"**;
  - **"O que ele pode fazer"** — as permissões de uso nos grupos de §6.2, que **mudam na hora** (`PUT /bot/permissoes`); quem tem `permissoes` libera qualquer uma, os outros só as que eles mesmos têm ("Você não tem esta permissão" no `title`; o servidor responde 403 "Você só libera para o bot o que você mesmo pode fazer.");
  - **"Bases de conhecimento"** (CFG-14) e **"Ferramentas externas"** (CFG-13), caixas que mudam na hora; vazias: "Nenhuma base cadastrada." / "Nenhuma ferramenta cadastrada.";
  - **"Excluir bot"**, com a confirmação de perigo "Excluir <nome>?" / "Ele sai de todas as conversas, some dos contatos e não responde mais. O que ele já escreveu continua nas conversas. Não dá para desfazer." / "Excluir".
- Botões **"Cancelar"** e **"Criar bot"** / **"Salvar"** ("Salvando...").
- API: `GET /bots`, `PUT /bot {nome, instrucoes}`, `PATCH /bot {id, nome?, instrucoes?, ativo?}`, `DELETE /bot?id=` → `{id, apagado}`, `PUT /bot/permissoes {id, permissoes}`, `PUT /bot/ferramentas {id, ferramentas}`, `PUT /bot/conhecimento {id, bases}`, `GET /ferramentas/disponiveis` e `GET /conhecimento/disponiveis` (§4.1). O bot nasce com `emoji`, `responder` e `reagir` (as que quem criou tem). Como ele age no chat: CON-16.
- 🆕 `f351190` Fica em **IA › Bots**, a primeira sub-aba do menu IA (com `bots` ou `permissoes`, CFG-01). Na tela, só o estilo do "Cancelar" mudou (contorno na cor principal).

**CFG-13 🆕 `9c6e8a6` Aba Configurações › Ferramentas** (`ConfiguracaoFerramentas.vue`; permissão de administração `ferramentas`; servidor `src/ferramentas.ts`, migração 051)
- APIs HTTP com JSON que os bots podem chamar. Texto: "APIs que os bots podem chamar para buscar informação de fora do chat (uma base de conhecimento, um sistema da empresa). A IA decide quando usar pela descrição. Ligue cada ferramenta nos bots que devem usá-la, em Bots. Os valores dos cabeçalhos (tokens) ficam só no servidor: nunca voltam para esta tela nem vão para a IA."
- Formulário (**"Nova ferramenta"** / **"Editar <nome>"**): **"Nome"** (minúsculas, números e `_`), **"Descrição"**, **"Método"** (GET/POST), **"Endereço"** (`{parametro}` vira o valor), **"Cabeçalhos"** (nome e valor; salvo: "Salvo: deixe vazio para manter"; "+ Cabeçalho"), **"Parâmetros"** (nome, tipo "texto"/"número"/"sim/não", descrição, "obrigatório"; "+ Parâmetro"), **"Ligada (desligada, nenhum bot a usa)"**; "Excluir" (confirmação "Excluir <nome>?" / "Os bots que usam esta ferramenta deixam de usar. Não dá para desfazer."), "Cancelar", "Criar" / "Salvar".
- **"Testar"** (só editando): campos com os parâmetros salvos e o botão "Testar" ("Chamando..."); resultado "Respondeu <status> · N ms" (ou o erro) e a resposta.
- Lista com o nome, **"Ligada"** / **"Desligada"**, "<método> <endereço>" e "Editar"; vazio "Nenhuma ferramenta ainda."; botão "Nova ferramenta". No servidor, 20 s por chamada e resposta cortada em 8 mil caracteres.
- 🆕 `f351190` Fica em **IA › Ferramentas** (CFG-01). Lista vazia: ~~"Nenhuma ferramenta ainda."~~ → **"Nenhuma API externa cadastrada ainda: use Nova ferramenta para cadastrar a primeira."**.
- 🆕 `f351190` (servidor `ac710eb`) **Ferramenta embutida no topo da lista**, de exemplo de configuração: `GET /ferramentas/embutidas` (permissão `ferramentas`; se falhar, só não aparece) → `[{nome, descricao, origem, parametros, definicao}]`, hoje só `consultar_conhecimento` (a mesma que o bot com base de conhecimento ganha, CFG-14):
  - na lista: o nome (fonte monoespaçada), a pílula **"Embutida"**, **"Interna, sem endereço externo"** e o botão **"Ver"**;
  - "Ver" abre **o mesmo formulário, travado** (`aria-label` "Ferramenta embutida"): o nome com "Embutida"; o aviso **"Vem com o Conversa e não se altera nem se remove: serve de exemplo de como fica uma ferramenta configurada. Os bots a usam quando têm uma base ligada, em Bots."**; "Método" **"Interna"**; "Endereço" "Qdrant, nas bases ligadas ao bot" e embaixo a `origem` que o servidor manda; em Cabeçalhos, **"Nenhum: ela não chama endereço externo."**; os parâmetros (`pergunta`, obrigatório; `base`, opcional), sem "Remover" nem "+ Parâmetro"; o bloco que abre **"Como o modelo recebe (com as bases cadastradas)"**, com o JSON da `definicao` (a que o modelo recebe, montada com as bases que existem ou, sem nenhuma, duas de exemplo); e só o botão **"Fechar"** (sem salvar, excluir nem testar).

**CFG-14 🆕 `9c6e8a6` Aba Configurações › Conhecimento** (`ConfiguracaoConhecimento.vue`, `utils/textoDocumento.ts`; permissão de administração `conhecimento`; servidor `src/conhecimento.ts`, migração 052, Qdrant)
- Bases de documentos que os bots consultam (RAG). Texto: "Documentos que os bots consultam para responder (políticas, manuais, perguntas frequentes). Cada documento é quebrado em trechos e indexado com a IA; o bot recebe os trechos mais parecidos com a pergunta e diz de qual documento vieram. Ligue as bases em cada bot, em Bots. Formatos: …". Sem o modelo de embeddings (CFG-07): "A base de conhecimento está desligada: defina o servidor de IA e o modelo de embeddings em Sistema para enviar documentos." (`recursos.conhecimento`, GER-08).
- Bases: lista com nome, descrição ("Sem descrição") e "N documentos · N trechos"; **"Nova base"** ("Nome", "Descrição", "Criar base"); a base aberta tem "Nome", "Descrição", "Salvar" e **"Excluir base"**.
- **"Documentos"**: **"Enviar documentos"** (`.txt`, `.md`, `.markdown`, `.csv`, `.pdf`, `.docx`; o **texto sai no navegador** — pdf.js e o XML do Word — e vai para `PUT /conhecimento/documento`; "Lendo N..."); cada documento com "N caracteres · N trechos" (ou o erro), a situação **"Indexando"** / **"Pronto"** / **"Erro"** e "Excluir"; vazio "Nenhum documento nesta base.".
- **"Testar busca"**: "Os trechos que o bot receberia para esta pergunta.", campo e "Buscar" ("Buscando..."); cada trecho com "<documento> · semelhança 0.NN" (duas casas, com ponto) e o texto; "Nada encontrado.".
- **"Trocou o modelo de embeddings?"** — "Os trechos precisam ser indexados de novo com o modelo novo, em todas as bases." e **"Reindexar tudo"**.
- No servidor: trechos de ~1000 caracteres, embeddings no servidor de IA, busca no Qdrant; o bot com a base ganha a ferramenta `consultar_conhecimento` (5 trechos).
- 🆕 `f351190` Fica em **IA › Conhecimento** (CFG-01). Os embeddings podem ir para um servidor próprio (CFG-07).
- 🆕 `f351190` **Destaque em "Testar busca"** (`utils/destacarTermos.ts#partesDestacadas`): as palavras da pergunta aparecem **marcadas** (`<mark>` na cor principal) em cada trecho. Regras:
  - valem as palavras com **3 letras ou números ou mais** da pergunta (de, a, o ficam de fora), sem repetir, as mais longas primeiro;
  - **sem diferenciar maiúscula nem acento**, nos dois sentidos ("agua" acha "Água"; "água" acha "Agua"), letra a letra, para o destaque cair no lugar certo do texto original (emoji e o que não cabe numa posição viram um marcador);
  - o destaque usa a pergunta **da busca feita**, não o que está sendo digitado;
  - o texto continua inteiro, só dividido em partes; sem palavra que conte ou sem achar, vai sem destaque.

### 3.13 GER — Comportamentos gerais de UX

**GER-01 Toast de erro global** — faixa vermelha no topo com ×.
**GER-02 Faixa de conexão em tempo real** — §1.4.
**GER-03 Diálogo de confirmação próprio** (`DialogoConfirmacao.vue`, `useDialogo.ts`) — título, mensagem, "Cancelar"/"OK" (customizáveis), variante perigo (botão vermelho, foco em Cancelar), Esc cancela, clique fora cancela; modo aviso (só OK). Novo diálogo responde "não" ao anterior.
**GER-04 Arrastar e soltar arquivos** — ANX-01.
**GER-05 Menu de contexto nativo bloqueado** — exceto em campos de texto/conteúdo editável (clique direito abre os menus do app).
**GER-06 Seções não implementadas** — tela "Em desenvolvimento".
**GER-07 Avatares com fallback** — inicial do nome (primeira "letra" real, inclusive emoji); imagens que falham somem e fica a inicial. 🆕 `769bd7d`: num grupo com `emoji` (CON-15), o fallback é o **emoji do grupo** (em fonte maior) no lugar da inicial — lista, cabeçalho, painel do grupo e página de Anexos.
**GER-08 🆕 `769bd7d` Recursos do servidor** (`composables/useRecursos.ts`) — `GET /recursos` → `{transcricao, ia}` diz o que está ligado no servidor (transcritor e servidor de IA configurados). Lido uma vez por janela, no primeiro uso (falha → tenta de novo no próximo uso; até lá, os dois `false`), e de novo depois de salvar a aba Sistema (CFG-07). Controla: botão "Transcrever" (ANX-12), botão "Resumir conversa" (CON-09/CON-14), a sugestão ao digitar (ENV-25) e a seção de IA da aba Privacidade (CFG-09). 🆕 `9c6e8a6` (servidor `ef13c22`) Ganha **`conhecimento`** (IA configurada **e** modelo de embeddings definido): liga o envio de documentos e a busca da aba Conhecimento (CFG-14). A `ia` também diz se os bots respondem e se dá para criar bots (CFG-11, CFG-12, CON-16). 🆕 `f351190` (servidor `ac710eb`) `conhecimento` passa a depender só de haver um servidor de embeddings (o próprio ou o da IA, CFG-07) e o modelo de embeddings.

**GER-09 🆕 `9c6e8a6` Permissões de uso** (`utils/permissoesUso.ts`, `stores/auth.ts#pode`; servidor `ef13c22`, `src/permissoes.ts`, migrações 046, 048 e 049)
- **O que é:** além das de administração, cada pessoa tem permissões do **uso do app**, marcadas por quem tem Acessos (CFG-08) ou, para bots, em Bots (CFG-12). Conta nova (cadastro ou administrador) começa com **todas**; quem já tinha conta recebeu todas na migração; o bot começa só com `emoji`, `responder` e `reagir`.
- **No cliente:** `auth.pode(codigo)` esconde ou trava o que a pessoa não pode. Enquanto `GET /usuario/permissoes` não chegou, `pode` é **verdadeiro** (mostra tudo; o servidor recusa o que não pode). No modo aberto o servidor devolve todas.
- **No servidor:** cada rota confere (fora do modo aberto) e responde **403 "Você não tem permissão para: <descrição em minúsculas>."** (as de administração continuam com "Acesso negado!"). Três **só o cliente confere**: `compartilhar_tela`, `cores` e `config_chamadas` (não passam pela API).
- 🆕 `f351190` (servidor `ac710eb`, migrações 054 e 055):
  - **`usuarios` deixou de existir**: quem a tinha ganhou `permissoes`, que aparece como **"Pessoas"** (descrição "Cadastrar pessoas e bots e conceder e retirar permissões") e passa a cuidar também do cadastro de contas (CFG-11). As de administração ficam 5: `parametros` "Sistema", `permissoes` "Pessoas", `bots` "Bots", `ferramentas` "Ferramentas", `conhecimento` "Conhecimento". As permissões de uso de cada pessoa ficam em **Pessoas › Usuários** (CFG-08 dentro do CFG-11);
  - **código de uso novo `localizacao`** ("Localização e trajeto" / "Enviar localizações e trajetos"), no grupo **Enviar**, depois de "Arquivos": esconde "Localização" do "+" (ENV-30); o servidor confere o conteúdo tipo 9 no `PUT /mensagem`. Quem já tinha conta recebeu (menos os bots); conta nova de pessoa começa com ela; o bot, não;
  - passam a ser **27 códigos de uso** (a tabela abaixo ganhou a linha `localizacao`).
- **Os 26 códigos** (🆕 `f351190` 27), nos grupos da tela (nome curto do web → descrição do servidor → o que muda):

| Grupo | Código | Nome no web | Descrição (servidor) | Web esconde ou trava | Servidor confere |
|---|---|---|---|---|---|
| Enviar | `emoji` | Emojis no texto | Enviar emojis no texto das mensagens | aba "Emojis" do seletor (ENV-03) | `PUT /mensagem` com texto que tem emoji |
| Enviar | `figurinha` | Figurinhas | Enviar figurinhas | aba "Figurinhas" (ENV-04) | `PUT /mensagem` tipo 7 |
| Enviar | `imagem` | Imagens | Enviar imagens | arquivo de imagem no campo (ENV-21) | tipo 2 |
| Enviar | `video` | Vídeos | Enviar vídeos | arquivo de vídeo no campo | tipo 3 com extensão de vídeo |
| Enviar | `audio` | Áudios e gravações | Enviar áudios e gravações de voz | microfone (ANX-11), arquivo de áudio | tipos 4 e 5 |
| Enviar | `arquivo` | Arquivos | Enviar arquivos (documentos e outros) | outros arquivos; "Arquivo" do "+" some sem nenhuma das quatro | tipo 3 sem extensão de vídeo |
| Enviar 🆕 `f351190` | `localizacao` | Localização e trajeto | Enviar localizações e trajetos | "Localização" do "+" (ENV-30) | tipo 9 |
| Mensagens | `responder` | Responder | Responder mensagens | "Responder" e "Responder no privado" (ENV-20) | `mensagem_referencia.tipo` 1 |
| Mensagens | `encaminhar` | Encaminhar | Encaminhar mensagens | "Encaminhar" e "Responder no privado" | `mensagem_referencia.tipo` 2 (só ela) |
| Mensagens | `reagir` | Reagir | Reagir às mensagens com emoji | reações rápidas e "Mais emojis" (ENV-17) | `PUT /mensagem/reacao` |
| Mensagens | `agendar` | Agendar mensagens | Agendar mensagens | "Agendar mensagem" (ENV-13) | `visivel_em` no `PUT /mensagem` |
| Mensagens | `confirmacao` | Pedir confirmação de leitura | Pedir confirmação de leitura | "Confirmar leitura" do "+" e do menu (ENV-24, ENV-27) | `pede_confirmacao`; `POST /mensagem/pedir-confirmacao` |
| Mensagens | `ocultar` | Ocultar mensagens | Ocultar as próprias mensagens | "Ocultar" (ENV-18) | `DELETE /mensagem` (também a agendada, ENV-14) |
| Grupos | `grupo` | Criar grupos | Criar grupos | "Novo grupo" (CON-07) | `PUT /conversa` tipo 2 |
| Grupos | `postagem` | Postagens | Fazer postagens em grupos | "Postagem" do "+" (ENV-26) | `postagem` no `PUT /mensagem` (responder a uma não pede) |
| Grupos | `votacao` | Votações | Criar votações em grupos | "Votação" do "+" (ENV-22) | `PUT /enquete` |
| Chamadas | `chamada_audio` | Chamadas de áudio | Fazer chamadas de áudio | "Chamada de voz", "Ligar novamente" (CHA-01, CHA-22) | `PUT /chamada/iniciar` tipo 1 |
| Chamadas | `chamada_video` | Chamadas de vídeo | Fazer chamadas de vídeo e ligar o vídeo na chamada | "Chamada de video", "Compartilhar tela" do cabeçalho, "Vídeo" da `CallBar` (CHA-16) | `PUT /chamada/iniciar` tipo 2; `POST /chamada/video` |
| Chamadas | `compartilhar_tela` | Compartilhar a tela | Compartilhar a tela nas chamadas | "Compartilhar tela" do cabeçalho e da janela (CHA-02) | — (só o cliente) |
| Chamadas | `telefonia` | Telefonia (SIP) | Usar a telefonia (SIP) | aba Voip; o ramal (SIP-01) | `GET /sip` → `{}`; `PUT`/`PATCH /sip` |
| Configurações | `nome` | Mudar o nome | Alterar o próprio nome | campo "Nome" (AUT-06) | `nome` no `PATCH /usuario` |
| Configurações | `email` | Mudar o e-mail e telefone | Alterar o próprio e-mail e telefone | campo "Email" | `email` ou `telefone` no `PATCH /usuario` |
| Configurações | `avatar` | Mudar o avatar | Alterar o próprio avatar | "Alterar foto" / "Remover" (AUT-08) | `avatar_anexo_id` no `PATCH /usuario` |
| Configurações | `senha` | Trocar a senha | Trocar a própria senha | seção "Senha" (AUT-07) | `POST /alterar-senha` |
| Configurações | `privacidade` | Mudar a privacidade | Alterar as opções de privacidade | aba Privacidade travada (CFG-09) | chaves de privacidade no `PATCH /usuario` |
| Configurações | `cores` | Mudar as cores | Mudar as cores do app | aba Cores; botão de tema (CFG-02) | — (só o cliente) |
| Configurações | `config_chamadas` | Configurações de chamada | Mudar as configurações de chamada (áudio, vídeo e tela) | aba Chamadas (CFG-06) | — (só o cliente) |

- **Arquivos no campo** (`MessageInput.vue#inserirArquivos`): cada arquivo pede a sua (imagem pelo mime `image/`, áudio por `audio/`, vídeo por `video/` ou extensão, o resto `arquivo`). Os barrados não entram e aparece acima do campo "Você não tem permissão para enviar <tipos>." (ex.: "Você não tem permissão para enviar imagens, vídeos.").
- O que **não** pede permissão: texto sem emoji, "Copiar", atender chamada, adicionar à chamada, a imagem ou o emoji do grupo (CON-15), responder a uma postagem.

**GER-10 🆕 `9c6e8a6` Ocultar a barra de navegação** (`NavBar.vue`; só computador) — §2.2: botão "Ocultar menu" no pé da barra; oculta, a aba "Mostrar menu" na borda esquerda, com um ponto vermelho se há atividades pendentes; guardado em `conversa.menuOculto` (`'1'`/`'0'`). No celular a barra do rodapé não muda.

### Contagem

| Grupo | IDs | Qtde |
|---|---|---|
| AUT — Autenticação, sessão, perfil | AUT-01…10 | 10 |
| CON — Conversas | CON-01…13 | 13 |
| MSG — Exibição e leitura de mensagens | MSG-01…19 | 19 |
| ENV — Composição e ações sobre mensagens | ENV-01…20 | 20 |
| ANX — Anexos e mídia | ANX-01…15 | 15 |
| PES — Pesquisa | PES-01…02 | 2 |
| CHA — Chamadas WebRTC | CHA-01…23 | 23 |
| SIP — Telefonia SIP | SIP-01…05 | 5 |
| NOT — Notificações | NOT-01…06 | 6 |
| PRE — Presença | PRE-01 | 1 |
| ATV — Atividades | ATV-01…03 | 3 |
| CFG — Configurações e administração | CFG-01…08 | 8 |
| GER — UX geral | GER-01…07 | 7 |
| 🆕 Novos em `39d06f9` | ENV-21, ENV-22, MSG-20, CHA-24 | 4 |
| 🆕 Novo em `7322e83` | ENV-23 | 1 |
| 🆕 Novos em `769bd7d`…`d202c06` | PRE-02, PRE-03, CFG-09, ENV-24, MSG-21, CON-14, ENV-25, GER-08, CON-15, CHA-25, CHA-26, ENV-26, MSG-22 | 13 |
| 🆕 Novos em `e33ecba`…`8b43965` | ENV-27, ENV-28, CFG-10 | 3 |
| 🆕 Novos em `9c6e8a6` | CON-16, ANX-16, ATV-04, ATV-05, CFG-11, CFG-12, CFG-13, CFG-14, GER-09, GER-10 | 10 |
| 🆕 Novos em `f351190` | AUT-11, CON-17, MSG-23, MSG-24, MSG-25, ENV-29, ENV-30, ANX-17, ANX-18 | 9 |
| **Total** | | **172** |

---

## 4. Todos os endpoints REST e eventos WebSocket

### 4.1 Endpoints REST (todos sob `/api`, Bearer token salvo onde indicado)

Fonte única: `src/services/conversaApi.ts` (+ `eden.ts`). "Params" = query string; "Body" = JSON.

| # | Método | Rota | Params / Body | Resposta (campos usados) | Usado em |
|---|---|---|---|---|---|
| 1 | POST | `/login` | body `{login, senha, dispositivo_id?}` (sem token) | `{id, nome, email, telefone, avatar_url, avatar_identificador, token, dispositivo:{id, nome, modelo, versao_so, plataforma, ativo}}` | AUT-01 |
| 2 | PUT | `/usuario` | body `{nome, login, email, senha}` (sem token) | `{id, nome, login, email}` (🆕 `9c6e8a6` **403** "O cadastro está fechado: peça a um administrador para criar sua conta." com `cadastro_aberto` desligado) | AUT-02 |
| 3 | PATCH | `/dispositivo` | body `{id, nome?, modelo?, versao_so?, plataforma?, token_fcm?}` | — | AUT-04 |
| 4 | POST | `/alterar-senha` | body `{senha_atual, senha}` | — (🆕 `9c6e8a6` 403 sem a permissão de uso `senha`) | AUT-07 |
| 5 | PATCH | `/usuario` | body `{id, nome?, email?, telefone?, avatar_anexo_id?: number\|null}`; 🆕 `769bd7d` também `mostrar_visto_em?, mostrar_na_conversa?, aparecer_offline?` | — (🆕 `9c6e8a6` cada campo pede a sua permissão de uso: `nome`, `email` (também `telefone`), `avatar`, `privacidade`; 403 sem ela) | AUT-06, AUT-08, CFG-09 |
| 6 | GET | `/usuario/contatos` | — | `Contato[]` (🆕 `9c6e8a6` + `bot: boolean`; sem os bots excluídos; 🆕 `f351190` + `criado_em`; relido no WS 66 e ao reconectar o WS) | CON-11, CON-16, AUT-11 |
| 7 | GET | ~~`/contatos/online`~~ → 🆕 `769bd7d` `/contatos/presenca` | — | ~~`number[]`~~ → `[{usuario_id, estado: 'ativo'\|'ausente'\|'offline', visto_em}]` | PRE-01 |
| 8 | GET | `/usuario/permissoes` | — | `string[]` (🆕 `9c6e8a6` até 32 códigos: 6 de administração e 26 de uso; 🆕 `f351190` 5 e 27: sai `usuarios`, entra `localizacao`) | AUT-09, GER-09 |
| 9 | GET | `/conversas` | — | `Conversa[]` | CON-01 |
| 10 | GET | `/conversa/usuarios` | `conversa` | `[{id, usuario_id, nome, avatar_url}]` | CON-08, CHA-01; 🆕 `e33ecba` ENV-24/ENV-27 (modal "Quem precisa confirmar"), MSG-22 (foto nas respostas) |
| 11 | PUT | `/conversa` | body `{descricao, tipo}` (1 direta, 2 grupo) | `{id, ...}` (🆕 `9c6e8a6` grupo pede a permissão de uso `grupo`) | CON-06, CON-07 |
| 12 | PATCH | `/conversa` | body `{id, descricao}`; 🆕 `769bd7d` ou `{id, avatar_anexo_id: number\|null, emoji: string\|null}` | — | CON-08, CON-15; 🆕 `e33ecba` CON-07 (imagem/emoji ao criar, depois dos `PUT /conversa/usuario`) |
| 13 | PUT | `/conversa/usuario` | body `{conversa_id, usuario_id}` | `{id, conversa_id, usuario_id}` | CON-06/07/08 |
| 14 | DELETE | `/conversa/usuario` | `id` (= id do vínculo conversa_usuario) | — | CON-08 |
| 15 | PATCH | `/conversa/fixadas` | body `{conversas: number[]}` | `{conversas}` | CON-03 |
| 16 | PATCH | `/conversa/arquivada` | body `{conversa, arquivada}` | `{id, arquivada}` | CON-04 |
| 17 | POST | `/conversa/digitando` | body `{id}` | — | ENV-15 |
| 18 | POST | `/conversa/gravando` | body `{id}` | — | ENV-16 |
| 19 | GET | `/mensagens` | `conversa, mensagemreferencia, mensagensprevias, mensagensseguintes` | `Mensagem[]` (🆕 `9c6e8a6` conteúdos de áudio com `duracao` e `intensidade`, ANX-10; 🆕 `f351190` `pastas?: number[]` só quando há, MSG-23; conteúdo tipo 9, MSG-25) | MSG-01, MSG-06, CHA-18; 🆕 `f351190` MSG-24 (reler uma mensagem da pasta com `mensagensprevias=0&mensagensseguintes=0`) |
| 20 | GET | `/mensagens/novas` | `desde` (ISO ou '') | `[{conversa_id, mensagem_id, ate}]` | §1.4 |
| 21 | PUT | `/mensagem` | body `{conversa_id, conteudos:[{ordem, tipo, conteudo}], mensagem_referencia?:{tipo, origem_mensagem_id}, visivel_em?}`; 🆕 `pede_confirmacao?: true` (`769bd7d`), `postagem?: {titulo}` e `postagem_id?` (`d202c06`), `confirmacao_usuarios?: number[]` (`e33ecba`, servidor `6d20ff7`) | `{id, conversa_id, usuario_id}` (🆕 `9c6e8a6` 403 sem as permissões de uso que a mensagem pede, ENV-01; 🆕 `f351190` conteúdo tipo 9 com o JSON da localização, conferido: 400 "Localização inválida!"; pede `localizacao`) | ENV-01/04/06/07/08/13, CHA-18, ENV-24, ENV-26, ENV-28, MSG-22, GER-09, ENV-30 |
| 22 | DELETE | `/mensagem` | `id` | `{..., excluida_em?}` (🆕 `9c6e8a6` pede `ocultar`, também para cancelar a agendada) | ENV-14, ENV-18 |
| 23 | PUT | `/mensagem/reacao` | body `{mensagem_id, emoji}` | `{mensagem_id, emoji, acao}` (🆕 `9c6e8a6` pede `reagir`) | ENV-17 |
| 24 | POST | `/mensagem/visualizar` | body `{conversa, mensagem}` | `{sucesso}` | MSG-04 |
| 25 | POST | `/mensagem/reproduzir` | body `{conversa, mensagem}` | `{sucesso}` | ANX-10 |
| 26 | GET | `/mensagem/status` | `conversa, mensagem` (ids separados por vírgula) | `[{conversa_id, mensagem_id, recebida, visualizada, reproduzida, excluida_em}]` | MSG-10 |
| 27 | GET | `/mensagem/status/detalhe` | `id` | `[{usuario_id, nome, recebida, visualizada, reproduzida}]` (datas) | MSG-09 |
| 28 | GET | `/pesquisar` | `texto, conversa` (0 = todas) | `Mensagem[]`; 🆕 `6d20ff7` com `conversa > 0`, também as mensagens dos **chats das ligações feitas nela** (de que a pessoa é membro): `conversa_id` diferente do pedido | PES-01/02 |
| 29 | GET | `/anexo/existe` | `identificador` | `{id}` ou objeto sem `id` | §1.7 |
| 30 | PUT | `/anexo` | body `{identificador, tipo, nome, extensao, tamanho}`; 🆕 `9c6e8a6` áudio também `duracao?, intensidade?` | `{id, upload_url}` | §1.7, ANX-10 |
| — | PUT | `<upload_url>` (MinIO, fora da API, sem Bearer) | corpo binário | 2xx | §1.7 |
| 31 | GET | `/anexo` | `identificador` | `{url}` | ANX-*, AUT-08 |
| 32 | GET | `/anexo/transcricao` | `identificador` | `{status, texto, erro}` | ANX-12 |
| 33 | PUT | `/anexo/transcricao` | body `{identificador}` | `{status, texto, erro}` | ANX-12 |
| 34 | GET | `/anexos` | `conversa, autor, direcao, tipos (csv), antes, limite` (0/'' = sem filtro) | `AnexoItem[]` (🆕 `f351190` a página de Anexos abre com `conversa=0`: os de todas as conversas) | ANX-13 |
| 35 | PUT | `/chamada/iniciar` | body `{tipo, usuarios:[{id}], conversa_id?}` | `Chamada` (🆕 `9c6e8a6` pede `chamada_audio` ou `chamada_video`, pelo tipo) | CHA-01 |
| 36 | POST | `/chamada/cancelar` | body `{id}` | `{id}` | CHA-09 |
| 37 | POST | `/chamada/entrar` | body `{id}` | `{id}` | CHA-04 |
| 38 | POST | `/chamada/recusar` | body `{id, nao_atendeu?: true}` | `{id}` | CHA-05/06/08, ATV-03 |
| 39 | POST | `/chamada/sair` | body `{id}` | `{id}` | CHA-10 |
| 40 | POST | `/chamada/finalizar` | body `{id}` | `{id}` | (store, sem botão) |
| 41 | GET | `/chamada/dados` | `id` | `Chamada {id, iniciada, finalizada, tipo, status, criado_em, criado_por, conversa_chat_id, usuarios:[{usuario_id, usuario_nome, status, adicionado_por, adicionado_por_nome, adicionado_em, entrou_em, saiu_em, recusou_em}]}` | CHA-* |
| 42 | PUT | `/chamada/chat` | body `{id}` | `{conversa_id}` (🆕 `6d20ff7` grupo novo com o nome dos participantes; só reaproveita o chat de outra ligação) | CHA-18 |
| 43 | PUT | `/chamada/usuario` | body `{chamada_id, usuario_id}` | `{id}` | CHA-14 |
| 44 | POST | `/chamada/video` | body `{id}` | — (🆕 `6d20ff7` também troca o tipo da chamada para vídeo; 🆕 `9c6e8a6` pede `chamada_video`) | CHA-15/16 |
| 45 | GET | `/chamadas/pendentes` | — | `ChamadaPendente[]` | CHA-08 |
| 46 | GET | `/chamadas` | `participante, de, ate` | `ChamadaHistoricoItem[]` (🆕 `964ed72` com `conversa_chat_id`) | CHA-22, CHA-26 |
| 47 | GET | `/ice` | — | `{iceServers: RTCIceServer[], iceTransportPolicy}` | §5, SIP-05 |
| 48 | GET | `/sip` | — | `SipConfig` ou `{}` (🆕 `9c6e8a6` `{}` também sem a permissão `telefonia`) | SIP-01/02 |
| 49 | PUT | `/sip` | body `{sip_user, auth_user, sip_password, display_name, domain, ws_server, ativo}` | `SipConfig` (🆕 `9c6e8a6` pede `telefonia`) | SIP-01 |
| 50 | PATCH | `/sip` | body `{id, ...campos}` | — (🆕 `9c6e8a6` pede `telefonia`) | SIP-01 |
| 51 | GET | `/atividades` | `antes, limite` | `Atividade[]` (🆕 `9c6e8a6` ~~`nova`~~ → `pendente`; tipo 5) | ATV-01 |
| 52 | GET | `/atividades/novas` | — | `{quantidade}` (🆕 `9c6e8a6` as pendentes, só de conversas de que participa) | ATV-02 |
| 53 | POST | `/atividades/vistas` | — | `{vistas_em}` (🆕 `9c6e8a6` o web não chama mais; não muda o contador) | ATV-02 |
| 54 | GET | `/permissoes` | — | `PermissoesSistema` (🆕 `9c6e8a6` cada permissão com `uso: boolean`; sem os bots excluídos; 🆕 `f351190` cada usuário com `criado_em`, `bot` e `espaco`) | CFG-08; 🆕 `f351190` CFG-11 (caixas do "Novo usuário"), ANX-17, AUT-11 |
| 55 | PUT | `/permissao/usuario` | body `{usuario_id, codigo}` | `{usuario_id, codigo}` | CFG-08 |
| 56 | DELETE | `/permissao/usuario` | `usuario_id, codigo` | `{usuario_id, codigo}` (🆕 `9c6e8a6` 400 "Você não pode retirar as próprias permissões." com o próprio id) | CFG-08 |
| 57 | GET | `/parametros` | — | `ParametrosSistema` (🆕 `769bd7d` + `ia_url, ia_modelo, ia_token_configurado`; 🆕 `9c6e8a6` + `cadastro_aberto, ia_modelo_embeddings`; 🆕 `f351190` + `ia_embeddings_url, ia_embeddings_token_configurado`) | CFG-07 |
| 58 | PATCH | `/parametros` | body `AlteracaoParametros` (🆕 `769bd7d` + `ia_url?, ia_modelo?, ia_token?`; 🆕 `9c6e8a6` + `cadastro_aberto?, ia_modelo_embeddings?`; 🆕 `f351190` + `ia_embeddings_url?, ia_embeddings_token?` (`''` remove o token)) | `ParametrosSistema` | CFG-07 |
| 59 🆕 | GET | `/conversa/presentes` | `conversa` | `number[]` (quem está com a conversa aberta) | PRE-03 (`769bd7d`) |
| 60 🆕 | GET | `/usuario/privacidade` | — | `{mostrar_visto_em, mostrar_na_conversa, aparecer_offline}` | CFG-09 (`769bd7d`) |
| 61 🆕 | POST | `/mensagem/confirmar` | body `{mensagem_id}` | — (🆕 `6d20ff7` **403** "Você não está entre quem precisa confirmar esta mensagem." para quem está fora da lista) | MSG-21 (`769bd7d`) |
| 62 🆕 | GET | `/recursos` | — | `{transcricao, ia}` (🆕 `9c6e8a6` + `conhecimento`; 🆕 `f351190` `conhecimento` = servidor de embeddings, o próprio ou o da IA, com modelo) | GER-08 (`769bd7d`) |
| 63 🆕 | POST | `/parametros/ia/testar` | body `{url, modelo, token?}` | `{ok, resposta, erro, milissegundos}` | CFG-07 (`769bd7d`) |
| 64 🆕 | POST | `/conversa/resumo` | body `{conversa_id, periodo: '24h'\|'7d'\|'30d'\|'recentes'}` | `ResumoConversa` | CON-14 (`769bd7d`) |
| 65 🆕 | GET | `/conversa/resumo` | `id` | `ResumoConversa {id, conversa_id, periodo, status, mensagens, assuntos:[{titulo, resumo, pendencias, mensagens}], erro}` | CON-14 (`769bd7d`) |
| 66 🆕 | POST | `/chamada/chamar-novamente` | body `{chamada_id, usuario_id}` | `Chamada` | CHA-25 (`769bd7d`) |
| 67 🆕 | POST | `/ia/sugestao` | body `{conversa_id, texto}` (cancelável) | `{sugestao}` | ENV-25 (`f798e9c`) |
| 68 🆕 | GET | `/postagem/respostas` | `id` (da postagem) | `Mensagem[]` | MSG-22 (`d202c06`) |
| 69 🆕 | POST | `/postagem/visualizar` | body `{postagem_id}` | — | MSG-22 (`d202c06`) |
| 70 🆕 | POST | `/mensagem/pedir-confirmacao` | body `{mensagem_id, usuarios?: number[]}` (sem `usuarios`: todos os outros membros) | `ConfirmacaoLeitura {total, confirmou, pode_confirmar, destinatarios, usuarios}` (como quem enviou a vê); erros 404/403/400 em ENV-27; 🆕 `9c6e8a6` pede `confirmacao` | ENV-27 (`e33ecba`, servidor `6d20ff7`) |
| 71 🆕 | GET | `/cadastro` | — (**público**, sem token) | `{aberto}` | AUT-01, AUT-02 (`9c6e8a6`) |
| 72 🆕 | PUT | `/usuario/novo` | body `{nome, login?, email?, senha?, telefone?, avatar_anexo_id?, bot?, instrucoes?}` (permissão `usuarios`; 🆕 `f351190` permissão `permissoes` e `permissoes?: string[]`, que substitui o padrão) | `{id, nome, login, bot}` | CFG-11 (`9c6e8a6`) |
| 73 🆕 | POST | `/atividade/concluir` | body `{id}` | `{sucesso}` (WS 61 se concluiu) | ATV-04 (`9c6e8a6`) |
| 74 🆕 | POST | `/atividades/reacoes-vistas` | body `{mensagens: number[]}` (até 200) | `{concluidas}` (WS 61 se concluiu alguma) | ATV-05 (`9c6e8a6`) |
| 75 🆕 | GET | `/bots` | — (`bots`: os que criou; `permissoes`: todos) | `Bot[] {id, usuario_id, nome, login, instrucoes, ativo, criado_por, criado_por_nome, permissoes, ferramentas, bases}` | CFG-12 (`9c6e8a6`) |
| 76 🆕 | PUT | `/bot` | body `{nome, instrucoes}` (`bots`; IA ligada) | `Bot` | CFG-12 (`9c6e8a6`) |
| 77 🆕 | PATCH | `/bot` | body `{id, nome?, instrucoes?, ativo?}` | `Bot` | CFG-12 (`9c6e8a6`) |
| 78 🆕 | DELETE | `/bot` | `id` | `{id, apagado}` (`apagado: false` = ficou marcado como excluído, por ter histórico) | CFG-12 (`9c6e8a6`) |
| 79 🆕 | PUT | `/bot/permissoes` | body `{id, permissoes: string[]}` (só as de uso) | `Bot` | CFG-12 (`9c6e8a6`) |
| 80 🆕 | PUT | `/bot/ferramentas` | body `{id, ferramentas: number[]}` | `Bot` | CFG-12 (`9c6e8a6`) |
| 81 🆕 | PUT | `/bot/conhecimento` | body `{id, bases: number[]}` | `Bot` | CFG-12 (`9c6e8a6`) |
| 82 🆕 | GET | `/ferramentas` | — (`ferramentas`) | `FerramentaExterna[] {id, nome, descricao, metodo, url, cabecalhos: string[] (só os nomes), parametros, ativo}` | CFG-13 (`9c6e8a6`) |
| 83 🆕 | GET | `/ferramentas/disponiveis` | — (`bots`, `permissoes` ou `ferramentas`) | `[{id, nome, descricao}]` (só as ligadas) | CFG-12 (`9c6e8a6`) |
| 84 🆕 | PUT | `/ferramenta` | body `{nome, descricao, metodo, url, cabecalhos:[{nome, valor}], parametros:[{nome, tipo, descricao, obrigatorio}], ativo?}` | `FerramentaExterna` | CFG-13 (`9c6e8a6`) |
| 85 🆕 | PATCH | `/ferramenta` | body `{id, …}` (cabeçalho com `valor: null` mantém o salvo) | `FerramentaExterna` | CFG-13 (`9c6e8a6`) |
| 86 🆕 | DELETE | `/ferramenta` | `id` | `{id}` | CFG-13 (`9c6e8a6`) |
| 87 🆕 | POST | `/ferramenta/testar` | body `{id, argumentos}` | `{ok, status, resposta, erro, milissegundos}` | CFG-13 (`9c6e8a6`) |
| 88 🆕 | GET | `/conhecimento/bases` | — (`conhecimento`) | `[{id, nome, descricao, documentos, trechos}]` | CFG-14 (`9c6e8a6`) |
| 89 🆕 | GET | `/conhecimento/disponiveis` | — (`bots`, `permissoes` ou `conhecimento`) | `[{id, nome, descricao}]` | CFG-12 (`9c6e8a6`) |
| 90 🆕 | PUT | `/conhecimento/base` | body `{nome, descricao?}` | base | CFG-14 (`9c6e8a6`) |
| 91 🆕 | PATCH | `/conhecimento/base` | body `{id, nome, descricao?}` | base | CFG-14 (`9c6e8a6`) |
| 92 🆕 | DELETE | `/conhecimento/base` | `id` | `{id}` | CFG-14 (`9c6e8a6`) |
| 93 🆕 | GET | `/conhecimento/documentos` | `base` | `[{id, base_id, nome, caracteres, trechos, status (1 Processando, 2 Pronto, 3 Erro), erro, criado_em}]` | CFG-14 (`9c6e8a6`) |
| 94 🆕 | PUT | `/conhecimento/documento` | body `{base_id, nome, texto}` (o texto já extraído no navegador) | documento | CFG-14 (`9c6e8a6`) |
| 95 🆕 | DELETE | `/conhecimento/documento` | `id` | `{id}` | CFG-14 (`9c6e8a6`) |
| 96 🆕 | POST | `/conhecimento/buscar` | body `{bases: number[], pergunta, limite?}` | `[{documento, texto, nota}]` | CFG-14 (`9c6e8a6`) |
| 97 🆕 | POST | `/conhecimento/reindexar` | — | `{documentos}` | CFG-14 (`9c6e8a6`) |
| 98 🆕 | GET | `/usuario/espaco` | — | `{bytes}` (arquivos que a própria pessoa enviou, cada um uma vez) | ANX-17 (`f351190`) |
| 99 🆕 | POST | `/parametros/ia/testar-embeddings` | body `{url, url_ia, modelo, token?}` (`url` vazio = testa no servidor da IA) | `{ok, dimensoes, erro, milissegundos}` | CFG-07 (`f351190`) |
| 100 🆕 | GET | `/ferramentas/embutidas` | — (`ferramentas`) | `[{nome, descricao, origem, parametros, definicao}]` | CFG-13 (`f351190`) |
| 101 🆕 | GET | `/pastas` | `conversa` | `PastaMensagens[] {id, conversa_id, dono_id, nome, cor, compartilhada, mensagens}` (as minhas e as compartilhadas comigo) | CON-17 (`f351190`) |
| 102 🆕 | PUT | `/pasta` | body `{conversa_id, nome, cor?, compartilhada?}` (só conversa direta; até 50 por pessoa e conversa) | `PastaMensagens` (WS 67) | CON-17 (`f351190`) |
| 103 🆕 | PATCH | `/pasta` | body `{id, nome?, cor?, compartilhada?}` (só o dono) | `PastaMensagens` (WS 67) | CON-17 (`f351190`) |
| 104 🆕 | DELETE | `/pasta` | `id` (só o dono) | `{id}` (WS 67) | CON-17 (`f351190`) |
| 105 🆕 | GET | `/pasta/mensagens` | `id, antes` (0 = as mais recentes; senão o id da mais antiga carregada) | `Mensagem[]` (até 50, da mais antiga para a mais nova) | MSG-24 (`f351190`) |
| 106 🆕 | PUT | `/pasta/mensagens` | body `{pasta_id, mensagens: number[]}` (1 a 500) | `{pasta_id, incluidas}` (WS 67 com `acao: 'incluir'`) | ENV-29 (`f351190`) |
| 107 🆕 | DELETE | `/pasta/mensagens` | **corpo JSON** `{pasta_id, mensagens: number[]}` (1 a 500; não vai na consulta) | `{pasta_id, tiradas}` (WS 67 com `acao: 'tirar'`) | MSG-24 (`f351190`) |

As rotas da votação (`GET/PUT/PATCH /enquete`, `POST /enquete/votar`, `POST /enquete/encerrar`) estão em ENV-22 e MSG-20 (§10), fora desta tabela. 🆕 `9c6e8a6` O `PUT /enquete` pede a permissão de uso `votacao` (GER-09). As rotas 72 e 75 a 97 são de administração (cadastro de usuários e bots, CFG-11 a CFG-14); no celular ficam fora do escopo (§7). 🆕 `f351190` As rotas 99 e 100 também são de administração; as das pastas (101 a 107) e a 98 valem para o celular. O `DELETE /pasta/mensagens` (107) é o único `DELETE` com corpo JSON.

Fora da API: `POST /webrtc/<caminho>/whip` e `POST /webrtc/<caminho>/whep` (MediaMTX, `Content-Type: application/sdp`, sem Bearer) — §5; `GET /figurinhas/<pacote>/<nome>.json` (estático da página; 🆕 `d202c06` só `noto/<nome>`); 🆕 `d202c06` `GET /emojis/pt.json` (catálogo do seletor, ENV-03); 🆕 `964ed72` `/chamada.html` (aba da chamada, CHA-13; 🆕 `e33ecba` janela popup); `/notification.mp3`, `/toque.mp3`, `/chamando.mp3`, `/logo.png` (estáticos); 🆕 `9c6e8a6` `/fontes/office/*.woff2` e `fontes.css` (fontes do visualizador do Office, ANX-16).

🆕 `f351190` **Serviços externos chamados direto do navegador** (sem passar pela API, sem Bearer; localização, ENV-30 e MSG-25):
- busca de endereço: `GET https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&accept-language=pt-BR&q=<texto>` → `[{lat, lon, display_name}]` (lat e lon em texto);
- nome do lugar num ponto: `GET https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=pt-BR&lat=<lat>&lon=<lng>` → `{display_name}`;
- rota de carro: `GET https://router.project-osrm.org/route/v1/driving/<lngA>,<latA>;<lngB>,<latB>?overview=full&geometries=polyline` → `{code, routes: [{geometry, distance, duration}]}`;
- mapa: `https://tile.openstreetmap.org/{z}/{x}/{y}.png`; satélite `https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}`, com `…/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}` e `…/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}` por cima;
- links "Abrir no Google Maps": `https://www.google.com/maps/search/?api=1&query=<lat>%2C<lng>` e `https://www.google.com/maps/dir/?api=1&origin=<lat>%2C<lng>&destination=<lat>%2C<lng>&travelmode=driving`.

### 4.2 Eventos WebSocket

Enum: `TipoEventoSocket` em `src/types/api.ts`. Mensagens são JSON.

**Enviados pelo cliente**

| tipo | Nome | Payload | Quando |
|---|---|---|---|
| 1 | (login) | `{tipo:1, token}` | `onopen` |
| 57 | SinalChamada | `{tipo:57, chamada_id, dados: SinalChamada}` | tela compartilhada, ponteiro (CHA-15/17) |
| 63 🆕 `769bd7d` | Presenca | `{tipo:63, ativo, conversa_id}` (`conversa_id` só quando ativo, senão `null`) | ao conectar, visibilidade, 5 min parada, uso depois de parada, troca de conversa; sem repetir o mesmo aviso (PRE-02) |

**Recebidos**

| tipo | Nome | Campos lidos | Ação no cliente |
|---|---|---|---|
| 2 | NovaMensagem | (nenhum) | `GET /mensagens/novas?desde=cursor` → notificações, lista, conversa ativa (§1.4) |
| 3 | StatusMensagem | `grupo` (conversa), `mensagens` ("1,2,3") | `GET /mensagem/status` → atualiza recebida/visualizada/reproduzida/excluida_em; recarrega conversas |
| 4 | Digitando | `conversa_id, usuario_id` | indicador 4 s; 🆕 `9c6e8a6` (servidor) o bot de IA manda a cada 3 s enquanto pensa (CON-16) |
| 5 | GravandoAudio | `conversa_id, usuario_id` | indicador 4 s |
| 7 | ReacaoMensagem | `conversa_id, mensagem_id, emoji, acao ('remove' ou outro), usuario_id` | atualiza a reação localmente (só se a mensagem está em memória) |
| 8 🆕 `769bd7d` | ConfirmacaoLeitura | `conversa_id, mensagem_id, usuario_id, nome, confirmada_em` | acrescenta quem confirmou (só se a mensagem está em memória) — MSG-21 |
| 40 | ConversaAtualizada | — | `GET /conversas` |
| 51 | ChamadaRecebida | `chamada_id, usuario_id` | CHA-03/06 |
| 52 | ChamadaFinalizada | `chamada_id` | encerra |
| 53 | UsuarioRecusou | `chamada_id, usuario_id` | CHA-07/10 |
| 54 | UsuarioEntrou | `chamada_id, usuario_id` | CHA-07/10 |
| 55 | UsuarioSaiu | `chamada_id, usuario_id` | CHA-10 |
| 56 | VideoAtivado | `chamada_id, usuario_id` | CHA-16 |
| 57 | SinalChamada | `chamada_id, usuario_id, dados` | `dados.acao`: `'tela'` `{ativa}` · `'ponteiro'` `{alvo, x, y}` (x/y null = saiu) · `'chat'` `{conversa_id}` · 🆕 `769bd7d` `'participantes'` (relê `GET /chamada/dados`, CHA-25); ignora o próprio e de outra chamada |
| 60 | StatusUsuario | `usuario_id, online`; 🆕 `769bd7d` + `estado` (`ativo`/`ausente`/`offline`), `visto_em` (só quando muda) | presença (PRE-01); também chega com o **meu** id (PRE-02) |
| 61 | NovaAtividade | — | ATV-02; 🆕 `e25c4db` (servidor) chega também quando **eu leio** uma mensagem ou resposta de postagem com atividade de resposta/menção ligada (`POST /mensagem/visualizar`, `POST /postagem/visualizar`), para o contador cair em todas as abas e aparelhos; 🆕 `9c6e8a6` (servidor `ef13c22`) e também ao concluir à mão (`POST /atividade/concluir`), ao ver reações (`POST /atividades/reacoes-vistas`), e ao confirmar uma leitura pedida (`POST /mensagem/confirmar`); como toda atividade nova, a do pedido de confirmação (tipo 5) também avisa. O contador agora é o de **pendentes** (ATV-02) |
| 62 | EnqueteAtualizada | `enquete_id` | relê `GET /enquete` se a enquete está na tela — MSG-20 (§10; faltava nesta tabela) |
| 63 🆕 `769bd7d` | Presenca | `conversa_id, usuario_id, aberta` | põe/tira a pessoa de quem está com a conversa aberta (PRE-03) |
| 64 🆕 `d202c06` | PostagemAtualizada | `conversa_id, postagem_id, usuario_id` | recarrega conversas, relê as respostas da postagem em memória, som e notificação (MSG-22, NOT-02) |
| 65 🆕 `e33ecba` | ConfirmacaoPedida | `conversa_id, mensagem_id, usuario_id` (quem pediu), `confirmacao` (`{total, confirmou, pode_confirmar, destinatarios, usuarios}`, a visão de quem recebe) | troca o `confirmacao` da mensagem, se ela está em memória (ENV-27, MSG-21); sem som nem notificação. O servidor (`6d20ff7`) manda a todos os membros, inclusive a quem pediu |
| 66 🆕 `f351190` | ContatosAtualizados | (nenhum) | `GET /usuario/contatos` e troca a lista (busca da lista e sugestões do @). O servidor manda a **todas as conexões abertas** quando uma conta é criada, renomeada (nome ou e-mail) ou excluída, e quando um bot é criado, renomeado ou excluído (CON-11) |
| 67 🆕 `f351190` | PastasAtualizadas | `conversa_id, pasta_id`; em pôr e tirar também `mensagens` (**lista de ids**, não o texto "1,2,3" do evento 3) e `acao` (`'incluir'` / `'tirar'`) | ignora o de outra conversa; marca ou desmarca `mensagem.pastas`; relê `GET /pastas`; relê a pasta aberta se é ela e veio `acao` (CON-17). Vai ao dono e, com a pasta compartilhada (antes ou depois da mudança), aos dois da conversa, inclusive a quem fez |

Eventos 51–57 só são tratados depois que o handler de chamada é registrado em `iniciarSessao` (o popup de chat não registra — chamadas não tocam no popup). Tipo 6 não existe no enum do cliente.

---

## 5. WebRTC / MediaMTX / WHIP / WHEP

Arquivo: `src/stores/call.ts`. Não há malha P2P: cada participante **publica uma única transmissão** no MediaMTX (WHIP) e **assina** a transmissão de cada outro participante (WHEP).

### 5.1 Endereços e nomes das transmissões

- Base: `${window.location.origin}${VITE_WEBRTC_PATH || '/webrtc'}` → `https://HOST/webrtc`.
- Sala: `call-<chamadaId>`; transmissão do usuário: `call-<chamadaId>-u-<usuarioId>` (caracteres fora de `[a-zA-Z0-9_-]` removidos).
- Publicar: `POST https://HOST/webrtc/call-<chamadaId>-u-<meuId>/whip` (corpo = SDP offer, `Content-Type: application/sdp`; resposta = SDP answer).
- Assinar: `POST https://HOST/webrtc/call-<chamadaId>-u-<outroId>/whep`.
- Sem trickle ICE: espera a coleta de candidatos terminar (até **5 s**, por causa da alocação TURN/TLS) antes de enviar o offer. Não usa `PATCH`/`DELETE` do WHIP/WHEP (o encerramento é só `pc.close()`).

### 5.2 Servidores ICE

- `GET /ice` a **cada nova RTCPeerConnection** (credenciais TURN temporárias) → `{iceServers, iceTransportPolicy}` (`relay` quando o parâmetro "Forçar relay pelo TURN" está ligado).
- Falha ou lista vazia → `VITE_STUN_URL` se configurado, senão nenhum.
- O mesmo `GET /ice` alimenta o SIP.

### 5.3 Publicação (WHIP)

- Uma `RTCPeerConnection` de envio (`pcPublicacaoLocal`) compartilhada por todos os peers.
- **No máximo uma trilha de áudio e uma de vídeo** (o MediaMTX recusa com 406 mais de uma de cada tipo) — usa a primeira trilha `live`.
- **Codec de vídeo (commit `dfeedbf`)**: `setCodecPreferences` no transceiver de vídeo ordenando `video/H264` primeiro, depois `video/VP9`, demais (VP8 etc.) por último — porque o MediaMTX **grava** as chamadas e não grava VP8. A chamada funciona mesmo sem H264/VP9 (só não grava).
- **Opus estéreo**: com a qualidade "Música", o SDP do offer recebe `stereo=1;sprop-stereo=1` no `a=fmtp` do Opus. Os offers de WHEP sempre pedem estéreo (para aceitar quem envia música).
- **Parâmetros de envio** (`sender.setParameters`): áudio `maxBitrate` 32k/64k/128k; vídeo `maxBitrate` conforme banda (Automático = sem limite, 500k, 3M) e `maxFramerate` = fps escolhido; com tela: 15 fps + `degradationPreference: maintain-resolution` (Nitidez) ou 30 fps + `maintain-framerate` (Fluidez).
- Captura: áudio `{echoCancellation, noiseSuppression, autoGainControl, channelCount: 2 se música senão 1}`; vídeo `{width/height ideal pela resolução (640×360, 1280×720, 1920×1080), frameRate {ideal, max}, facingMode:'user' em celular}`.
- **Quando publica**: logo após `PUT /chamada/iniciar` (quem liga), logo após `POST /chamada/entrar` (quem atende, se tem mídia), ao ativar microfone/câmera em "somente recepção", ao fazer upgrade para vídeo (republica), ao compartilhar tela sem câmera (republica + `POST /chamada/video`).
- **Recuperação**: `connectionState` `failed`/`disconnected` da publicação → fecha e republica, depois sincroniza peers (2 tentativas).
- 🆕 `769bd7d` **Republicar sem interromper** (ligar o vídeo, CHA-16): a publicação nova vai ao **mesmo caminho** WHIP antes de fechar a antiga (o MediaMTX troca uma pela outra; a antiga perde o monitor de queda para não ser "recuperada"); se o MediaMTX recusar, fecha a antiga e publica de novo.

### 5.4 Assinatura (WHEP)

- Uma `RTCPeerConnection` de recepção por participante, transceivers `recvonly`: áudio sempre; vídeo só se a chamada é de vídeo.
- Tentativas enquanto o outro ainda não publicou: vídeo **40 × 1 s**, áudio **12 × 0,8 s**; esgotado → erro `WHEP erro: <status|timeout>`. 404 não aparece como erro para o usuário: tenta de novo na próxima sincronização (1,5 s depois).
- Trilhas recebidas vão para um `MediaStream` por peer; áudio toca num `HTMLAudioElement` dedicado (CHA-23); vídeos sempre mudos.
- Reconexão: `failed`/`disconnected` → fecha e reassina após 2 s; chamada de vídeo sem trilha de vídeo 5 s depois de conectar → reassina; sincronização periódica (4 s) com `GET /chamada/dados` reassina peers com conexão `failed/disconnected/closed`, sem áudio vivo ou (vídeo) sem vídeo vivo, e desconecta quem não está mais com status "Entrou" (3).
- Reassinar de propósito: ao receber `VideoAtivado` (56) já em vídeo. 🆕 `769bd7d`: agora **sempre** no 56 (também em áudio) e **sem cortar** (`trocarAssinatura`): a assinatura nova entra no lugar da antiga, que só fecha depois; o áudio segue pela antiga enquanto isso. Se a troca falha, reassina do jeito antigo.

### 5.5 Compartilhamento de tela

§3 CHA-15: a tela entra **no lugar da câmera** na mesma transmissão (não há segunda transmissão); por isso quem assiste não sabe que é tela — quem compartilha avisa por WS 57 `{acao:'tela', ativa}`. Áudio do sistema é mixado com o microfone (Web Audio) numa única trilha de áudio.

### 5.6 Canais de dados

**Não há `RTCDataChannel`.** Ponteiro remoto, aviso de tela compartilhada e aviso de criação do chat da chamada passam pelo **WebSocket da API** (tipo 57, repassado pelo servidor sem gravar). O chat da chamada é uma conversa de grupo comum (REST + eventos de mensagem).

### 5.7 Indicador de fala

Medido no cliente sobre as trilhas de áudio (local e recebidas) com `AnalyserNode` (CHA-19). Não usa `getStats`/`audioLevel` nem evento do servidor.

### 5.8 Gravação

O cliente não grava nada nem tem botão de gravar: o **MediaMTX grava** as transmissões (por isso a preferência de codec). A retenção é o parâmetro do sistema "Guardar as gravações por (dias)" (CFG-07). O web não lista nem reproduz gravações.

### 5.9 Estados e máquina da chamada (resumo)

```
inativo --(iniciar)--> chamando --(54 de outro)--> ativa
inativo --(51)--> recebendo --(atender)--> ativa
recebendo --(recusar | 30 s | 53/54 meus de outro aparelho)--> inativo
chamando --(cancelar)--> inativo
ativa --(sair | 52 | último outro saiu | recusa em direta)--> encerrando --> inativo
```

---

## 6. Regras de UX e textos exatos para replicar

### 6.1 Mensagens

| Situação | Texto exato |
|---|---|
| Campo de mensagem | "Digite uma mensagem" |
| Bolha oculta | "Mensagem oculta" (tooltip "Clique para ver o conteúdo" / "Clique para ocultar o conteúdo") |
| Citação de mensagem oculta | "Mensagem oculta" |
| Confirmação de ocultar | título "Ocultar mensagem", texto "Ela continua na conversa, marcada como oculta.", botões "Cancelar" / "Ocultar" (vermelho) |
| Confirmação de cancelar agendada | título "Cancelar mensagem agendada", texto "Ela não será enviada.", botões "Voltar" / "Cancelar envio" |
| Erro ao ocultar | título "Não foi possível ocultar" |
| Erro ao baixar | título "Não foi possível baixar" |
| Menu de ações | "Responder", "Responder no privado", "Encaminhar", "Copiar", 🆕 `e33ecba` "Pedir confirmação de leitura", "Ocultar"; reações 👍 ❤️ 😂 😮 / 😢 👏 🔥 + "Mais emojis" (🆕 `9c6e8a6` cada item some sem a sua permissão de uso, ENV-20); 🆕 `f351190` na pasta aberta, "Ver na conversa" (primeiro item) e "Tirar da pasta" (último), sem "Pedir confirmação de leitura" nem "Ocultar" |
| Selo de agendada | "Agendada para hoje 14:30" / "Agendada para amanhã 08:00" / "Agendada para 12/10 08:00" |
| Indicador não lidas | "Últimas" |
| Botão flutuante | "Há novas mensagens"; botão "Ir para o final" |
| Lista vazia | "Selecione uma conversa." / "Ver conversas" (celular) / "Sem mensagens" (prévia) |
| Citação encaminhada | "Encaminhado de <nome>" ou "Encaminhado" |
| Barra de resposta | "<nome>" (resposta) / "Encaminhando de <nome>" (responder no privado) |
| Detalhe status (direta) | "Enviada", "Recebida", "Visualizada", "Ouvida", "Oculta", "Aguardando" |
| Detalhe status (grupo) | "Visualizada por (N)", "Recebida por (N)", "Aguardando (N)" |
| Digitando | "Digitando..." / "<nome> está digitando..." / "<a> e <b> estão digitando..." / "<a>, <b> e <c> estão digitando..." / "<a>, <b>, <c> e outras N pessoas estão digitando..." (idem "gravando áudio") |
| Código | "Copiar"/"Copiado!", "Expandir código"/"Recolher código", "Visualizar"/"Código" |
| 🆕 Janela e peça de código (`e33ecba`) | janela "Inserir código" / "Editar código", placeholder "Cole ou escreva seu código aqui...", botões "Cancelar", "Inserir na mensagem", "Enviar" / "Salvar"; peça "<linguagem> · 1 linha" / "<linguagem> · N linhas" (sem linguagem "texto"), "Editar código", "Remover código", prévia com "…" depois da 4ª linha |
| Transcrição | "Transcrever", "Transcrevendo...", "(nenhuma fala reconhecida)", "Não foi possível transcrever. Tentar de novo" |
| Arquivo | "Abrir" (PDF/HTML; 🆕 `9c6e8a6` também Office), "Download", ~~"Baixar video"~~ (🆕 `9c6e8a6` virou o botão "Baixar vídeo" do player), "Toque para abrir" / "Toque para carregar"; 🆕 `8b43965` HTML "Ver fonte" / "Ver página" |
| Agendar | "Agendar mensagem", "Data", "Hora", "Cancelar", "Agendar"; erros "Data/hora inválida", "O envio deve ser no mínimo 5 minutos no futuro", "O envio não pode ser mais de 1 ano no futuro" |
| Resumos de conteúdo | notificação: "Imagem", "Gravacao de audio", "Audio", "Figurinha", "Arquivo"; resposta/encaminhar (`resumoMensagem`): texto, "Imagem", "Áudio", "Arquivo"; código: "Código (linguagem)"; 🆕 `f351190` localização: "Localização" (notificação, resumo da citação, atividades) e "[Localização]" (chat da chamada); a barra de resposta e o encaminhar continuam com "Arquivo" |
| 🆕 Menu "+" | "Arquivo", "Código", 🆕 `f351190` "Localização", "Votação" (grupo), "Postagem" (grupo), "Confirmar leitura" |
| 🆕 Confirmação de leitura | faixa "A mensagem vai pedir confirmação de leitura" (× "Não pedir confirmação"); quem recebe "Confirmar leitura" / "Leitura confirmada"; quem enviou "Aguardando confirmação" / "Confirmada por N de T" / "Confirmada por N"; erro "Não foi possível confirmar a leitura" |
| 🆕 Confirmação com lista (`e33ecba`) | faixa em grupo "A mensagem vai pedir confirmação de leitura de todos" / "… de 1 pessoa" / "… de N pessoas" + "Escolher"; modal "Quem precisa confirmar", "Fechar", "Filtrar por nome", "Todos (N)", "Carregando participantes", "Ninguém com “<termo>”", "Cancelar", "Pronto" / "Pedir confirmação"; dica do chip "Falta confirmar"; menu "Pedir confirmação de leitura"; erro "Não foi possível pedir a confirmação" (fallback "Erro ao pedir a confirmação") |
| 🆕 Postagem | faixa "Postagem: as respostas ficam embaixo dela", "Título (opcional)" (× "Não postar"); cartão "Postagem · Você" / "Postagem · <nome>", "Nenhuma resposta ainda" / "1 resposta" / "N respostas", "1 nova" / "N novas", "Ver todas as N" / "Mostrar menos", "Mensagem apagada", "Responder à postagem", botão "Responder" (🆕 `e33ecba` saiu com o campo do cartão); notificação "<nome> respondeu a uma postagem" |
| 🆕 Responder à postagem (`e33ecba`) | botão do cartão "Responder à postagem" / "Respondendo no campo de mensagem"; faixa acima do campo "Respondendo à postagem de <nome>" (× "Não responder à postagem") |
| 🆕 Resumo (IA) | "Resumir conversa", "Resumo da conversa", "Últimas 24 horas", "7 dias", "30 dias", "Últimas 400 mensagens", "Resumir" / "Resumir de novo", "Resumindo N mensagens...", "Pode levar alguns minutos, dependendo do servidor de IA.", "Escolha o período e toque em Resumir. As mensagens são separadas por assunto.", "Nenhuma mensagem nesse período.", "Resumo de dd/MM às HH:MM · <período> · N mensagens", "Ver na conversa (N mensagens)", "Gerado por IA a partir de N mensagens. Confira na conversa antes de agir." |
| 🆕 Emojis | "Pesquisar emoji", "Recentes", "Carinhas e emoções", "Pessoas e corpo", "Animais e natureza", "Comida e bebida", "Viagens e lugares", "Atividades", "Objetos", "Símbolos", "Nenhum emoji encontrado", "Carregando os emojis..."; figurinhas: pacote "Noto", crédito "Animated Noto Emoji © Google, CC BY 4.0" |
| 🆕 Pesquisa na conversa | "N resultado para “termo”" / "N resultados para “termo”" / "Nenhuma mensagem com “termo”"; autor "Você" nas minhas; 🆕 `e33ecba` selo "no chat da ligação" |
| 🆕 Transcrição | "Transcrever" só com o transcritor configurado (GER-08) |
| 🆕 Áudio (`9c6e8a6`) | duração antes de tocar; tocando ou parado no meio "00:05 / 00:12" (`mm:ss`; "--:--" sem duração) |
| 🆕 Vídeo (`9c6e8a6`) | "Reproduzir vídeo", "Pausar" / "Reproduzir", "mm:ss / mm:ss", "Baixar vídeo", "Abrir no visualizador", "Tela cheia" / "Sair da tela cheia"; no visualizador também "Silenciar" / "Ativar som", "Volume" (0% a 200%), "Velocidade" (0,25x … 3x) |
| 🆕 Office (`9c6e8a6`) | "Abrir" em `.docx/.xlsx/.pptx`; "Página anterior" / "Próxima página" ("Slide anterior" / "Próximo slide"), "Ir para a página" / "Ir para o slide", "Ainda contando as páginas", "Diminuir zoom", "Aumentar zoom", "Página inteira", "Ajustar à largura", "Baixar", "Fechar (Esc)"; "Baixando arquivo...", "Preparando o documento...", "Não foi possível abrir o arquivo. Tente baixar." |
| 🆕 Permissões de uso no campo (`9c6e8a6`) | "Você não tem permissão para enviar <tipos>." (tipos: "imagens", "vídeos", "áudios", "arquivos", separados por vírgula) |
| 🆕 Recusa do servidor (`9c6e8a6`) | "Você não tem permissão para: <descrição em minúsculas>." (descrições em GER-09; ex.: "Você não tem permissão para: enviar imagens.") |
| 🆕 Bot (`9c6e8a6`) | pílula "Bot" (`title` "Responde com a IA") no cabeçalho da direta; falha do bot na conversa "Não consegui responder a esta mensagem: <motivo>" |
| 🆕 Pastas (`f351190`) | cabeçalho "Pastas de mensagens"; painel "Pastas", "Nova pasta", "Fechar", "Nome da pasta", vazio "Crie pastas para guardar mensagens por assunto. Arraste ao lado das mensagens para selecioná-las e solte-as numa pasta.", "Solte numa pasta", "Opções da pasta", "Renomear", "Compartilhar com <nome>" / "Deixar de compartilhar", cores "Cor do tema" / "Cor", "Excluir"; `title` "Compartilhada com <nome>" / "Compartilhada por <nome>" / "<pasta> (compartilhada por <nome>)" ("a outra pessoa" sem nome); confirmação "Excluir pasta" / "A pasta "<nome>" sai daqui[ e de <nome>]. As mensagens continuam na conversa." / "Excluir"; erros "Não foi possível criar a pasta", "Não foi possível renomear a pasta", "Não foi possível mudar a pasta", "Não foi possível mudar a cor", "Não foi possível excluir a pasta", "Não foi possível pôr na pasta" |
| 🆕 Seleção (`f351190`) | "1 selecionada" / "N selecionadas", "· arraste até uma pasta", "Pôr na pasta", "Limpar seleção (Esc)", "Arraste até uma pasta", "Nenhuma pasta ainda. Crie no painel de pastas.", "Erro ao pôr na pasta"; etiqueta do arraste "1 mensagem" / "N mensagens"; marcas na bolha: `title` "Pasta <nome>" (recebida) / "Na pasta <nome1, nome2>" (enviada) |
| 🆕 Pasta aberta (`f351190`) | "Voltar ao chat" (`title` "Voltar ao chat (Esc)"), "· 1 mensagem" / "· N mensagens", " · compartilhada", "Carregando mensagens", "Nenhuma mensagem nesta pasta. No chat, arraste ao lado das mensagens para selecioná-las e solte-as aqui no painel.", menu "Ver na conversa" / "Tirar da pasta", barra "Tirar da pasta"; erros "Não foi possível tirar da pasta" (fallback "Erro ao tirar da pasta"), "Não foi possível reagir" |
| 🆕 Localização — janela (`f351190`) | "+" › "Localização"; "Enviar localização" / "Editar localização", "Local" / "Trajeto", selos "•", "A", "B"; placeholders "Endereço, lugar ou coordenadas", "Origem: endereço, lugar ou coordenadas", "Destino: endereço, lugar ou coordenadas"; "Buscar", "Usar minha posição"; "Buscando...", "Calculando a rota...", "<d> · <t> de carro", "Sem rota pelas ruas: <d> em linha reta"; dicas "Sem GPS? Busque o endereço, digite as coordenadas ou clique no mapa." / "Clique no mapa para marcar a origem, ou busque o endereço." ("o destino"); avisos "Nada encontrado com esse endereço.", "A busca de endereço não respondeu.", "A busca de endereço falhou.", "Este dispositivo não informa a posição.", "O acesso à localização foi negado.", "Não foi possível obter a posição deste dispositivo.", "Sem acesso à posição." (+ " Busque o endereço, digite as coordenadas ou clique no mapa."); botões "Cancelar", "Inserir na mensagem", "Enviar" / "Salvar"; peça "Localização" / "Trajeto", "Editar localização", "Remover localização" |
| 🆕 Localização — bolha (`f351190`) | "Ampliar mapa", "Abrir no Google Maps", "Satélite" / "Mapa" (`title` "Ver a imagem de satélite" / "Ver o mapa de ruas"), "Trajeto · 12,3 km · 18 min de carro" / "Trajeto · 12,3 km em linha reta" / "Trajeto", selos "A" / "B", "Localização" (conteúdo inválido); atribuições "© OpenStreetMap", "Imagens © Esri, Maxar, Earthstar Geographics"; distância "850 m" / "12,3 km" / "150 km", tempo "1 min" / "1 h 30 min" / "2 h"; visualizador "Fechar" |
| 🆕 Velocidade do áudio (`f351190`) | botão "Velocidade": "1x", "1,5x", "2x", "3x" |

### 6.2 Conversas, chamadas, atividades

- Lista: "Pesquisar...", "Nova conversa", "Arquivadas (N)", etiquetas "Grupo" e "Arquivada", menu "Fixar"/"Desafixar"/"Arquivar"/"Desarquivar". 🆕 `e33ecba`: a etiqueta "Grupo" da lista virou um ícone com o rótulo acessível "Conversa em grupo" ("Arquivada" continua; a página de Anexos mantém a etiqueta "Grupo").
- Chamada recebida: "Chamada recebida", "<nome> está ligando...", "Vídeo + Áudio" / "Somente Áudio", "Atender só assistindo"; notificação "<nome> está ligando (Vídeo|Áudio)".
- Upgrade: "Vídeo ativado", "<nome> ativou o vídeo", "Deseja transmitir seu vídeo também?", "Apenas assistir", "Transmitir também".
- Janela da chamada: "Chamando...", "Em chamada", "Encerrando...", "N pessoa(s)", modos "Grade"/"Destaque"/"Tela única", "Somente recepção", "Ativar microfone", "Ativar câmera", "Chat da chamada", "Apontar na tela compartilhada"/"Desligar ponteiro", "Adicionar à chamada", "Abrir chamada", "Vídeo".
- Histórico: "Chamadas", "Todas", "Perdidas", "Buscar contato", "De", "Até", "Hoje", "Ontem", "Ligar novamente", "Nenhuma chamada", "Nenhuma chamada perdida".
- Bolha de chamada: "Chamada de audio|video[ em grupo]", status "Recusada", "Perdida", "Cancelada"; participantes "Perdida", "Recusou", "Desconectou".
- Atividades: ver ATV-01 (descrições exatas).
- 🆕 Lista: seção "Chamadas (N)" antes de "Arquivadas (N)".
- 🆕 Presença: tooltip da bolinha "Ativo" / "Ausente" / "Offline"; próprio avatar "Para os outros você aparece ativo" / "… ausente" / "… offline"; cabeçalho "nesta conversa", "ativo agora", "ausente", "visto por último hoje às HH:MM" / "visto por último ontem às HH:MM" / "visto por último em dd/MM às HH:MM"; painel do grupo " · nesta conversa".
- 🆕 Grupo: "Imagem", "Emoji", "Remover", "Salvando...". 🆕 `e33ecba` Criar grupo: "Imagem", "Emoji", "Remover", "Criar" / "Criando...", erro "Erro ao criar grupo".
- 🆕 `8b43965` Cores: seção "Tema", "Cores prontas para os temas claro e escuro. Escolher um tema apaga as cores ajustadas abaixo.", "Padrão", "Teams", "WhatsApp", "Messenger".
- 🆕 Chamada: "Chamando...", "Não atendeu", "Chamar de novo"; etapas "Abrindo a câmera...", "Enviando o vídeo...", "Recebendo o vídeo dos participantes..."; "Voltar ao chat" (celular); chat da ligação "Ver chat da chamada" / "Esconder o chat da chamada", "Carregando o chat...", "Ninguém escreveu no chat da chamada.", "Abrir o chat completo". 🆕 `6d20ff7` O grupo do chat da ligação se chama com os nomes dos participantes ("Ana, Bruno"), sem "Chamada: ".
- 🆕 Privacidade: "Status", "Aparecer offline", "Mostrar o visto por último", "Mostrar quando estou na conversa", "Inteligência artificial", "Sugerir a continuação ao digitar" (descrições em CFG-09). Sistema › IA: "Endereço", "Modelo", "Token", "Testar conexão" / "Testando..." (CFG-07).
- 🆕 `9c6e8a6` Atividades: vazio "Reações, respostas, menções, pedidos de confirmação de leitura e chamadas perdidas aparecem aqui."; tipo 5 "pediu que você confirme a leitura" (selo "✓"); bolinha "Pendente"; botão "Marcar como concluída" (no pedido de confirmação, `title` "Marcar como concluída (a leitura continua sem confirmar)").
- 🆕 `9c6e8a6` Login: "Não tem conta? Criar conta" só com o cadastro aberto; cadastro fechado (servidor) "O cadastro está fechado: peça a um administrador para criar sua conta."; bot no login "Bot não entra pelo login.".
- 🆕 `9c6e8a6` Perfil sem permissão: "Você não tem permissão para mudar o nome" / "Você não tem permissão para mudar o e-mail" (`title` do campo), "Você não tem permissão para mudar o avatar."; Privacidade "Você não tem permissão para mudar a privacidade.".
- 🆕 `9c6e8a6` Nomes curtos das permissões (`utils/permissoesUso.ts`), por grupo:
  - **Administração:** "Sistema", "Acessos" (🆕 `f351190` → **"Pessoas"**), "Bots", ~~"Usuários"~~ (🆕 `f351190` a permissão saiu), "Ferramentas", "Conhecimento";
  - **Enviar:** "Emojis no texto", "Figurinhas", "Imagens", "Vídeos", "Áudios e gravações", "Arquivos", 🆕 `f351190` **"Localização e trajeto"**;
  - **Mensagens:** "Responder", "Encaminhar", "Reagir", "Agendar mensagens", "Pedir confirmação de leitura", "Ocultar mensagens";
  - **Grupos:** "Criar grupos", "Postagens", "Votações";
  - **Chamadas:** "Chamadas de áudio", "Chamadas de vídeo", "Compartilhar a tela", "Telefonia (SIP)";
  - **Configurações:** "Mudar o nome", "Mudar o e-mail e telefone", "Mudar o avatar", "Trocar a senha", "Mudar a privacidade", "Mudar as cores", "Configurações de chamada".
- 🆕 `9c6e8a6` Acessos: "Abra uma pessoa para ver e mudar o que ela pode fazer: a administração do sistema e o uso do chat. Conta nova começa com todas as de uso. Ninguém retira as próprias permissões: outra pessoa com Acessos precisa fazer isso.", pílula "Administração", "N de T", `title` "Você não pode retirar as próprias permissões"; servidor "Você não pode retirar as próprias permissões.". (🆕 `f351190` textos novos no bullet de Pessoas, abaixo.)
- 🆕 `9c6e8a6` Sistema: seção "Cadastro", "Permitir que as pessoas se cadastrem"; IA "Modelo de embeddings" (textos em CFG-07). Abas de administração "Usuários", "Ferramentas", "Conhecimento", "Bots" (textos em CFG-11 a CFG-14).
- 🆕 `9c6e8a6` Barra do computador: "Ocultar menu" / "Mostrar menu" (GER-10).
- 🆕 `f351190` Configurações: seções "Pessoal" e "Administração"; menus "Conta", "Aparência", "Dispositivos", "Chamadas", "Sistema", "Pessoas", "IA"; sub-abas "Perfil", "Privacidade", "Tema", "Sessão e periféricos", "Permissões do navegador", "Qualidade", "Voip", "Sistema", "Usuários", "Bots", "Conhecimento", "Ferramentas", "Servidor" (descrições na §2.5).
- 🆕 `f351190` Perfil: seção "Armazenamento", "Calculando...", "<tamanho> usados pelos arquivos que você enviou (imagens, arquivos e áudios; cada arquivo conta uma vez, mesmo mandado em várias conversas).", erro "Não foi possível calcular o espaço usado"; "Usuário desde dd/mm/aaaa". Perfil de outra pessoa: campo "Usuário desde".
- 🆕 `f351190` Tema: botões "Claro" / "Escuro"; "Para editar o outro tema, troque entre claro e escuro acima.".
- 🆕 `f351190` Pessoas › Usuários: "Crie contas para pessoas ou bots, já escolhendo o que cada uma pode fazer, mesmo com o cadastro pela tela de entrada fechado (em Sistema). Abra uma pessoa na lista para ver e mudar os acessos dela; o bot também se ajusta em Bots."; seção "Acessos", "Vem com todas as de uso marcadas e nenhuma de administração." / "O bot só recebe permissões de uso; vem com o básico marcado.", "Carregando as permissões..."; lista: "Ninguém tem a permissão Pessoas ainda:" + "todos os usuários podem mexer nas configurações e nas permissões. Marque Sistema e Pessoas para quem deve cuidar disso; ao marcar Pessoas para alguém, só passa a valer o que estiver marcado.", "Ninguém retira as próprias permissões: outra pessoa com a permissão Pessoas precisa fazer isso.", pílula "Bot", colunas com `title` "Espaço dos arquivos que enviou" e "Usuário desde".
- 🆕 `f351190` Sistema: Cadastro "Desligado, a tela de entrada não oferece o cadastro: só quem tem a permissão Usuários cria contas, em Pessoas › Usuários."; IA › Servidor: "Embeddings (base de conhecimento dos bots)", "Endereço do servidor de embeddings" ("Vazio: o mesmo servidor da IA"; "Só se os embeddings rodarem em outro host ou porta, ex.: http://192.168.0.20:1234."), "Token do servidor de embeddings" ("Só se o servidor pedir"; "O token da IA não vai para este servidor; o salvo aqui nunca é mostrado."), "Modelo de embeddings" ("Vazio desliga a base. Trocou de modelo: use "Reindexar tudo" em Conhecimento."), "Testar embeddings" / "Testando...", "Funcionando (N s): vetores de N dimensões".
- 🆕 `f351190` Ferramentas: pílula "Embutida", "Interna, sem endereço externo", "Ver", "Vem com o Conversa e não se altera nem se remove: serve de exemplo de como fica uma ferramenta configurada. Os bots a usam quando têm uma base ligada, em Bots.", método "Interna", "Nenhum: ela não chama endereço externo.", "Como o modelo recebe (com as bases cadastradas)", "Fechar"; lista vazia "Nenhuma API externa cadastrada ainda: use Nova ferramenta para cadastrar a primeira.".
- 🆕 `f351190` Lista de conversas: "Gravando áudio..." (três pontinhos vermelhos, no lugar de "Digitando...").
- 🆕 `f351190` Anexos: o texto "Selecione um contato ou grupo para ver os anexos" saiu (a página abre com os anexos de todas as conversas).

### 6.3 Regras de comportamento críticas

1. **Ocultar ≠ apagar**: a mensagem permanece, com `excluida_em`; só a agendada ainda não enviada some de vez. Só o autor oculta, sem prazo.
2. **Arquivada não notifica nem toca**, mas recebe mensagens; desarquivar não restaura a posição de fixada.
3. **Visualizar só o que está visível com o app em primeiro plano** (equivalente Android: Activity resumida e item totalmente visível).
4. **Recusa automática sempre com `nao_atendeu: true`** (30 s tocando, ocupado, pendente > 25 s) — alimenta "chamada perdida".
5. **Chamada atendida/recusada em outro aparelho** (53/54 com meu id) deve parar de tocar.
6. **Um áudio por vez**; primeiro play de áudio recebido marca `reproduzir`.
7. **Throttle de digitando/gravando 2,5 s**, expiração 4 s.
8. **Ordenação** por `coalesce(visivel_em, inserida)`, id como desempate; otimistas no fim.
9. **Menções** enviadas como `@[Nome](id)`.
10. **Figurinha** é enviada sozinha (conteúdo único tipo 7).
11. **Responder no privado** = encaminhada (tipo 2) com conteúdos da original antes do texto.
12. **Fixadas**: enviar a lista completa na ordem; ordem de exibição fixadas → recentes → arquivadas (seção recolhível).
13. **Sessão**: 401 → login; outros erros → manter login e tentar de novo a cada 5 s.
14. 🆕 **Presença**: cada aparelho avisa pelo WS 63 se está em uso e qual conversa está aberta. O servidor considera **ativa** toda conexão nova até ela dizer o contrário: um cliente que nunca manda o 63 aparece "ativo agora" o tempo todo em que o WS está aberto (inclusive em segundo plano) e nunca "nesta conversa" (PRE-02).
15. 🆕 **Emoji igual ao do web**: sem o FE0F que sobra (só fica onde o caractere sozinho seria texto, como "❤️"); senão a mesma reação vira dois chips (ENV-03).
16. 🆕 **Recursos do servidor**: Transcrever, Resumir e a sugestão só aparecem com `GET /recursos` dizendo que estão ligados (GER-08).
17. 🆕 `e33ecba` **Confirmação de leitura com lista**: a caixa "Confirmar leitura" só aparece com `confirmacao.pode_confirmar` (o servidor recusa com 403 quem está fora da lista); os demais, inclusive quem enviou, veem a contagem. Pedir depois do envio só na própria mensagem, até 24 h (ENV-27, MSG-21).
18. 🆕 `6d20ff7` **Chat da ligação é um grupo próprio** (salvo a ligação feita dentro do chat de outra ligação): não tratar a conversa da ligação como o chat dela (CHA-18).
19. 🆕 `9c6e8a6` **Permissões de uso:** esconder ou travar o que a pessoa não pode (GER-09), com a lista de `GET /usuario/permissoes`; até ela chegar, mostrar tudo. O servidor confere de qualquer jeito e responde 403 "Você não tem permissão para: …" — mostrar essa mensagem. `compartilhar_tela`, `cores` e `config_chamadas` **só o cliente confere**.
20. 🆕 `9c6e8a6` **Atividade pendente até concluir:** abrir a tela não zera o contador; a reação conclui ao ver a própria mensagem na tela (`POST /atividades/reacoes-vistas`), a chamada perdida ao abri-la, e qualquer uma pelo ✓ (`POST /atividade/concluir`). O campo é `pendente`, não mais `nova` (ATV-01, ATV-04, ATV-05).
21. 🆕 `9c6e8a6` **Cadastro pode estar fechado:** perguntar `GET /cadastro` (público) antes de oferecer "Criar conta" (AUT-01).
22. 🆕 `9c6e8a6` **Áudio com duração e onda:** mandar `duracao` e `intensidade` (48 picos de 0 a 100) no `PUT /anexo` dos tipos 4 e 5, e mostrar a duração e a onda vindas do conteúdo sem baixar o áudio (ANX-10).
23. 🆕 `f351190` **A pasta só marca** (CON-17): só na conversa direta; a mensagem fica no chat e pode estar em várias pastas. `mensagem.pastas` só vem quando há alguma (sem a chave, nenhuma). Atualizar as marcas pela resposta de pôr/tirar/excluir e pelo WS 67. Na compartilhada, os dois põem e tiram; só o dono renomeia, muda a cor, deixa de compartilhar e exclui.
24. 🆕 `f351190` **Localização é um conteúdo tipo 9 com JSON** (ENV-30): um ponto ou dois. A rota pelas ruas é calculada **uma vez, no envio**, e vai na mensagem (quem recebe não chama o OSRM); sem rota, vai a distância em linha reta, sem duração. O servidor recusa o formato errado ("Localização inválida!").
25. 🆕 `f351190` **A permissão `usuarios` não existe mais:** cadastrar contas pede `permissoes` ("Pessoas"); o nome da permissão `permissoes` na tela é "Pessoas".
26. 🆕 `f351190` **Contatos sempre frescos:** reler `GET /usuario/contatos` no WS 66 e a cada reconexão do WebSocket (a busca e as menções procuram neles).
27. 🆕 `f351190` **`DELETE /pasta/mensagens` leva corpo JSON** (`{pasta_id, mensagens}`), ao contrário dos outros `DELETE`, que usam a consulta.

---

## 7. Itens que não fazem sentido no Android ou precisam de adaptação

| Item web | Situação no Android | Sugestão |
|---|---|---|
| Janela popup de conversa (CON-10, `chat-popup.html`) | Não existe "nova janela" | Ignorar; opcionalmente suporte a multi-janela/Bubbles (Android 11+) ou atalho dinâmico para a conversa |
| Janela flutuante da chamada arrastável (CHA-13) | — | **Picture-in-Picture** da Activity da chamada + notificação de chamada em andamento (foreground service `phoneCall`/`camera`/`microphone`) |
| Copiar imagem para a área de transferência (ENV-19, visualizador) | Possível, mas diferente | `ClipData` com URI de `FileProvider` (imagem) ou oferecer "Compartilhar"/"Salvar na galeria"; texto via `ClipboardManager` |
| Clique direito / hover (menu de ações, detalhe de status ao parar o mouse, tooltip de reações, cartão da menção, botão ⋯) | Sem hover | Toque longo abre o menu de ações; toque no ✓ abre o detalhe; toque longo na reação lista quem reagiu |
| Arrastar para reordenar fixadas | — | Modo de edição com alças de arraste (`ItemTouchHelper`/Compose reorder) ou "Mover para cima/baixo" |
| Drag & drop de arquivos, colar imagem | — | Intent de compartilhamento (`ACTION_SEND`/`SEND_MULTIPLE`) para o app, seletor do sistema (Photo Picker), colar imagem do teclado (`OnReceiveContentListener`) |
| Teclas (Enter envia, Tab = 4 espaços, letra fora do campo foca, Esc, setas, Ctrl+C, Ctrl+Enter) | Teclado virtual | Botão de enviar; Enter = nova linha (opção "Enter envia" para teclado físico); demais atalhos dispensáveis |
| Colar texto longo → janela de código; detecção heurística de código | Útil | Manter a regra (> 10 linhas abre o editor) com um editor simples monoespaçado e seletor de linguagem; o highlight pode ser via biblioteca (ex.: Highlight.js em WebView ou Prism4j) |
| CodeMirror | — | `EditText` monoespaçado com seletor de linguagem |
| Visualizador PDF (pdf.js) | — | `PdfRenderer` nativo ou abrir com app externo via Intent |
| Visualizador HTML isolado (iframe sandbox) | Risco de segurança | `WebView` com JavaScript permitido mas **sem acesso a arquivos/conteúdo**, sem cookies nem ponte JS, carregando por `loadDataWithBaseURL(null, …)`; ou abrir no navegador externo |
| Markdown/Mermaid em blocos de código | — | Markwon para Markdown; Mermaid via WebView isolado ou mostrar só o código ("Visualizar" opcional) |
| Figurinhas Lottie | Suportado | `lottie-android`/`lottie-compose`; baixar os JSON de `https://HOST/figurinhas/<pacote>/<nome>.json` (ou embarcar no APK). 🆕 `d202c06`: só o pacote `noto` (55), com o crédito CC BY 4.0 no seletor |
| Download com `<a download>` | — | `DownloadManager` ou salvar via MediaStore/SAF |
| Faixa "Conexao em tempo real indisponivel" | Útil | Manter (Snackbar/banner) |
| Polling de 8 s sem WebSocket | Gasto de bateria em segundo plano | Só com a tela aberta; em segundo plano confiar no FCM |
| Notificações do navegador e service worker | — | Notificações nativas com canal por tipo (mensagens, chamadas), agrupamento por conversa (`setGroup`/`MessagingStyle`), ação "Responder" inline e "Marcar como lida"; chamada recebida com **full-screen intent** + `ConnectionService`/`CallStyle` |
| FCM web (`token_fcm` + VAPID) | FCM nativo | Registrar o token nativo no `PATCH /dispositivo` com `plataforma: "Android"`, `modelo`, `versao_so`; tratar payload `data` `{titulo, mensagem, conversa}` |
| `beforeunload` com upload | — | Upload em `WorkManager` (sobrevive a sair da tela) |
| Aba "Dispositivos" (lista de periféricos do navegador) | Pouco útil | Mostrar dispositivo atual; seleção de saída de áudio (alto-falante/fone/Bluetooth) é mais útil na chamada |
| Aba "Permissoes" do navegador | — | Tela de permissões do app (notificações, microfone, câmera, full-screen intent, sobreposição) com atalho para as configurações do sistema |
| Aba "Cores" (editar CSS variables) | Possível, baixo valor | Opcional; no mínimo tema claro/escuro/sistema e cor principal (ou Material You) |
| Áudio do sistema no compartilhamento de tela | Limitado | `MediaProjection` + `AudioPlaybackCaptureConfiguration` (Android 10+, só apps que permitem captura) |
| Ponteiro remoto (enviar posição do mouse) | Sem mouse | Receber e desenhar os ponteiros é obrigatório; enviar pode ser por toque e arraste sobre a tela compartilhada |
| Indicador de fala por `AnalyserNode` | — | No Android, medir o nível do áudio local pelo `AudioRecord`/`JavaAudioDeviceModule` e o remoto por `getStats()` (`audioLevel` de `inbound-rtp`) |
| `setCodecPreferences` H264/VP9 | Necessário | Usar `RtpTransceiver.setCodecPreferences` (libwebrtc) com H264 primeiro (hardware), VP9 em seguida — **obrigatório** para as chamadas do Android serem gravadas |
| Conexão lenta (`navigator.connection`) | — | `ConnectivityManager` (rede medida/lenta) para "Toque para abrir" |
| Discador SIP flutuante arrastável | — | Tela/BottomSheet do discador; integrar com `ConnectionService` |
| Detecção do navegador para o dispositivo | — | `Build.MANUFACTURER/MODEL`, `Build.VERSION.RELEASE` |
| `conversa.apiBase` | — | Base configurável no login/ambiente; WebSocket e MediaMTX devem derivar da **mesma base** (`/ws/`, `/webrtc/`) |
| Seleção de texto bloqueada nas configurações, menu de contexto nativo bloqueado | — | Não se aplica |
| Banner "Em desenvolvimento" / Equipes | — | Não implementar Equipes (removido do web) |
| 🆕 Chamada de vídeo em aba própria do navegador (CHA-13, `964ed72`; 🆕 `e33ecba` janela popup de 80% da tela, até 1280×800) | Não existe aba nem popup | Não se aplica: continua a Activity da chamada + PiP; o equivalente ao "Voltar ao chat" do web celular é a barra/notificação de chamada em andamento em qualquer tela |
| 🆕 Presença pela visibilidade da aba e 5 min sem mexer (PRE-02, `769bd7d`) | Ciclo de vida | `ProcessLifecycleOwner` (primeiro plano = ativo, segundo plano = `ativo:false`) + a conversa aberta na tela; ociosidade de 5 min pela última interação (opcional) |
| 🆕 Sugestão da IA: Tab aceita, Esc descarta (ENV-25, `f798e9c`) | Teclado virtual sem Tab | Texto cinza depois do cursor (ou chip acima do teclado) que se aceita com um toque; qualquer digitação descarta; Tab/Esc só com teclado físico |
| 🆕 Hover em "quem confirmou" (MSG-21) | Sem hover | Toque no chip abre a lista de quem confirmou; 🆕 `e33ecba` com a seção "Falta confirmar" (destinatários que ainda não confirmaram). Para quem tem a caixa, o toque marca: a lista pode ir no toque longo |
| 🆕 Seletor de emoji próprio (ENV-03, `d202c06`) | `EmojiPickerView` (androidx) tem tons de pele, bandeiras e FE0F | Normalizar o emoji escolhido (tirar o FE0F que sobra; sem tom de pele) ou montar o seletor com o catálogo `/emojis/pt.json` |
| 🆕 IndexedDB (rascunho ENV-23, resumo CON-14) | — | Room ou DataStore, por usuário e conversa |
| 🆕 Temas prontos e variáveis dos balões (CFG-10, `8b43965`) | O Android usa as cores do FMX (`docs/design/cores.md`) | **Não se aplica**: sem "Tema", sem "Padrão/Teams/WhatsApp/Messenger" e sem `--bolha-*`; o mesmo vale para os ajustes por cima do tema (CFG-05) |
| 🆕 "Ver fonte" / "Ver página" do HTML (ANX-07, `8b43965`) | O Android abre o HTML com outro app (§11) | **Não se aplica** |
| 🆕 Escolha da tela na janela da chamada (CHA-15, `8b43965`) | A escolha é do sistema (`MediaProjection`) | **Não se aplica** a parte da janela; a outra parte vale: **fechar a escolha sem escolher não é erro** (sem aviso; ao iniciar compartilhando, a chamada não começa) |
| 🆕 Zoom pela roda no ponto do ponteiro (ANX-04, `e33ecba`) | Sem roda | Equivalente: a **pinça amplia no ponto entre os dedos** (centroide do gesto) e o duplo toque no ponto tocado |
| 🆕 Modal "Quem precisa confirmar" (ENV-24/ENV-27, `e33ecba`) | — | Folha (bottom sheet) ou diálogo com busca, "Todos (N)" e caixas; mesmos textos |
| 🆕 Peça de código no campo (ENV-28, `e33ecba`) | Campo simplificado (FC-416) | Um "chip"/cartão de código na faixa de anexos acima do campo, com a prévia, editar e remover; no envio, cada código vira um conteúdo tipo 1 com a cerca, na ordem decidida pelo FC-416 |
| 🆕 Ícone de grupo com `aria-label` (CON-01, `e33ecba`) | — | Ícone de pessoas com `contentDescription` "Conversa em grupo" no lugar da etiqueta |
| 🆕 Visualizador do Office (ANX-16, `9c6e8a6`; `@silurus/ooxml` em WASM) | O Android abre os arquivos com outro app (`abrirComOutroApp`) | **Não se aplica**: `.docx`, `.xlsx` e `.pptx` continuam abrindo com outro app (ou baixando), como o HTML |
| 🆕 Player de vídeo próprio (ANX-04/ANX-05, `9c6e8a6`) | O Media3 (`PlayerView`) já tem play/pausa, barra e tempo | No visualizador, acrescentar a **velocidade** (`setPlaybackSpeed`, as mesmas opções) e o **"Baixar vídeo"**; volume acima de 100% é opcional (`LoudnessEnhancer`). Tocar dentro da bolha é opcional: no celular o toque já abre o visualizador |
| 🆕 Onda do áudio medida no navegador (ANX-10, `9c6e8a6`; `AudioContext.decodeAudioData`) | Sem `AudioContext` | Gravação: ler o pico durante a gravação (`MediaRecorder.getMaxAmplitude()`; o `GravadorMediaRecorder` do `:core:media` usa `MediaRecorder`) e reduzir a **48 picos de 0 a 100**, relativos ao maior; duração pelo próprio gravador. Áudio escolhido: duração por `MediaMetadataRetriever`; a onda pode ir sem (`intensidade` é opcional) ou por decodificação (`MediaExtractor`/`MediaCodec`). Desenho: barras no `Canvas` do Compose |
| 🆕 Botão ✓ "Marcar como concluída" (ATV-04, `9c6e8a6`) | — | Botão à direita de cada item pendente (como no web); deslizar o item para concluir é opcional |
| 🆕 Barra de navegação oculta (GER-10, `9c6e8a6`) | Navegação inferior | **Não se aplica** |
| 🆕 Abas Usuários, Bots, Ferramentas e Conhecimento (CFG-11 a CFG-14, `9c6e8a6`) | O Android já tem Sistema e Acessos (8.5) | **Fora do escopo do celular** (administração pelo computador); avaliar só se for pedido. O que vale no celular é a conversa com o bot (CON-16) |
| 🆕 Seleção de mensagens por arraste, Ctrl + clique e Esc (ENV-29, `f351190`) | **O web não seleciona por toque** (ignora `pointerType === 'touch'`) | O Android precisa de um gesto próprio. Como o toque longo já abre o menu de ações (ENV-20), acrescentar nele **"Selecionar"** (ou "Pôr na pasta"), que liga o modo de seleção: os toques seguintes marcam ou desmarcam, e a barra de cima mostra "N selecionadas", "Pôr na pasta" e o ×; voltar limpa. Mesmos textos da §6.1 |
| 🆕 Arrastar as selecionadas até a pasta do painel (ENV-29, CON-17) | Sem arrastar e soltar entre painéis | "Pôr na pasta" na barra de seleção abre a lista das pastas (folha ou menu); o arraste não é necessário |
| 🆕 Painel estreito de pastas à direita (CON-17) | Tela estreita | Item **"Pastas"** no menu do cabeçalho da conversa **direta**, abrindo uma folha (bottom sheet) ou tela com a lista, "Nova pasta" e as opções de cada uma (toque longo ou ⋯): "Renomear", "Compartilhar com <nome>" / "Deixar de compartilhar", a mesma paleta e "Excluir" |
| 🆕 Pasta aberta por cima do chat (MSG-24) | — | Tela própria (ou o chat em "modo pasta") **sem o campo de mensagem**; "Voltar ao chat" = o voltar da barra e do sistema; no toque longo, "Ver na conversa" e "Tirar da pasta" |
| 🆕 Mapa Leaflet com os tiles do OSM e da Esri (MSG-25, ENV-30) | Sem Leaflet | Biblioteca de mapa nativa com os mesmos tiles (por exemplo osmdroid ou MapLibre; o Google Maps SDK pede chave) — dependência nova, a decidir. **O servidor de tiles do OSM exige um User-Agent que identifique o app** (e não aceita uso pesado: guardar em cache). Na bolha, mapa sem gestos (ou imagem); satélite da Esri com a atribuição |
| 🆕 Busca no Nominatim e rota no OSRM chamadas do navegador (ENV-30) | App nativo | Mesmas URLs, com **User-Agent próprio** (a política do Nominatim pede identificar o app; o navegador já manda o dele e o Referer); busca só quando a pessoa pede (sem autocompletar), cancelando a anterior; OSRM uma vez por trajeto |
| 🆕 Posição pelo `navigator.geolocation` (ENV-30) | Permissão de localização | `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` pedidas na hora do uso; `FusedLocationProviderClient.getCurrentLocation` (ou `LocationManager`, sem Play Services), com limite de 15 s. Negada: a mesma mensagem do web, e as outras formas (busca, coordenadas, toque no mapa) continuam |
| 🆕 "Abrir no Google Maps" (MSG-25) | Intent | O mesmo link `https://www.google.com/maps/...` por `ACTION_VIEW` (abre o app do Maps, se houver) |
| 🆕 Mapa escuro por filtro de cor nos tiles (MSG-25) | — | Estilo escuro da biblioteca, ou um `ColorMatrix` invertido só no mapa de ruas |
| 🆕 Velocidade do áudio guardada no navegador (ANX-18) | Media3 | `setPlaybackSpeed` no `PlayerAudio` (1x, 1,5x, 2x, 3x), a mesma para todos os áudios, guardada no DataStore |
| 🆕 Menus com sub-abas nas Configurações (CFG-01, `f351190`) | O Android tem a sua lista (Perfil, Aparência, Notificações, Chamadas, Permissões, Servidor, Sistema, Acessos, Sobre) | **Não copiar a estrutura.** Só o nome: "Acessos" passa a ser **"Pessoas"** (a permissão `permissoes`), e cada item segue a sua permissão |
| 🆕 Pessoas › Usuários numa tela só, embeddings próprios, ferramenta embutida, destaque na busca do conhecimento (CFG-07, CFG-11, CFG-13, CFG-14, `f351190`) | Administração | **Fora do escopo do celular.** Na tela Acessos do Android, só acompanhar os nomes ("Pessoas", sem "Usuários", com "Localização e trajeto") e, se quiser, o selo "Bot" e as colunas de espaço e de data |
| 🆕 Página de Anexos de todas as conversas (ANX-13, `f351190`) | O Android só tem os anexos de uma conversa | Opcional; `GET /anexos?conversa=0` já devolve os de todas, com `conversa_id` e `conversa_descricao` (vazia na direta: usar o nome da conversa) |

---

## 8. Anexo A — Commits desde 2026-09-12 e o que tocaram

| Data | Commit | Assunto | Principais arquivos |
|---|---|---|---|
| 2026-10-06 | `bfb79d8` | Abas Sistema e Acessos nas configurações; Equipes sai da barra | `ConfiguracaoSistema.vue`, `ConfiguracaoAcessos.vue`, `ProfileSettingsModal.vue`, `NavBar.vue`, `auth.ts` (permissões), `conversaApi.ts`, `types/api.ts`, `useHistoryNavigation.ts` |
| 2026-10-05 | `39d5af1` | Tela de atividades, com contador de novas na barra | `AtividadesPage.vue`, `stores/atividades.ts`, `NavBar.vue`, `App.vue`, `call.ts` (`nao_atendeu`), `chat.ts` (WS 61), `eden.ts` (`vistas_em`) |
| 2026-10-05 | `51a0cd7` | Excluir mensagem vira "Ocultar" | `BolhaExcluida.vue`, `MensagemAcoes.vue`, `MessageList.vue`, `DetalheStatusMensagem.vue`, `BolhaReferencia.vue`, `ReferenciaRecursiva.vue`, `messageReferences.ts` |
| 2026-10-03 | `20d5b73` | Ponteiro remoto e chat na chamada; figurinhas Lottie | `call.ts`, `PonteiroTela.vue`, `ChatChamada.vue`, `CallWindow.vue`, `chat.ts` (`enviarSinalChamada`), `figurinhas.ts`, `FigurinhaLottie.vue`, `BolhaFigurinha.vue`, `EmojiPicker.vue`, `public/figurinhas/**` |
| 2026-10-03 | `c32209a` | Login volta sem F5 depois que o servidor reinicia; copiar imagem pelo menu | `App.vue` (`iniciarSessao`), `LoginForm.vue`, `eden.ts` ("Servidor indisponível"), `ImageViewerModal.vue` |
| 2026-10-02 | `466e79a` | Adiciona os vídeos ao visualizador | `useImageViewer.ts`, `ImageViewerModal.vue`, `MessageContent.vue`, `AnexosLista.vue`, `formatters.ts` |
| 2026-10-02 | `f83ecd1` | Mensagem excluída continua no chat; confirmação e menu de ações próprios | `BolhaExcluida.vue`, `DialogoConfirmacao.vue`, `useDialogo.ts`, `MensagemAcoes.vue`, `classificarMensagem.ts`, `chat.ts` |
| 2026-10-02 | `dfeedbf` | Publica o vídeo das chamadas em codec que o MediaMTX grava | `call.ts` (`preferirCodecsGravaveis`) |
| 2026-10-02 | `6b121f2` | Texto longo colado abre a janela de código; botão de expandir sempre aparece | `MessageInput.vue`, `CodigoModal.vue`, `MessageContent.vue`, `codeBlocks.ts` |
| 2026-09-30 | `fa6b4f7` | Corrige anexos sem assinatura, erros soltos de anexo e agendadas atrasadas | `useAttachments.ts`, `useAgora.ts`, `MessageList.vue` |
| 2026-09-30 | `48ae9d3` | Testes de componentes, composables e utilitários | `tests/unit/**` (549 testes na época) |
| 2026-09-30 | `bc11b32` | Sugere código ao colar texto longo; tira TURN fixo do SIP; detecta iPhone | `sip.ts` (ICE do backend), `auth.ts`, `codeBlocks.ts` |
| 2026-09-29 | `77c49d2` | Adiciona testes unitários | `tests/unit/**`, ajustes em stores |
| 2026-09-28 | `0c404fd` | Corrige status online, notificações e digitando; abre anexos HTML isolados | `chat.ts`, `VisualizadorHtml.vue`, `AnexosLista.vue`, `ImageViewerModal.vue` (legenda), `call.ts`/`CallWindow.vue` (tela única ao só assistir) |
| 2026-09-27 | `d9f9a97` | Move as conversas arquivadas para o fim do painel lateral | `ChatSidebar.vue` |
| 2026-09-26 | `4007600` | Migra para Bun e Eden; fixar, arquivar e indicador de fala | `eden.ts`, `conversaApi.ts`, `ChatSidebar.vue`, `CallBar.vue`, `useFalaChamada.ts`, `MessageInput.vue` (menção destacada, código colado), `MensagemAcoes.vue` (responder no privado, copiar conforme clique), `sound.ts` (notificação de chamada pelo SW) |
| 2026-09-25 | `1861d37` | Ajusta notificações, status e layout do chat | `DetalheStatusMensagem.vue`, `MessageBubble.vue`, `sound.ts`, `conversaApi.ts` |
| 2026-09-23 | `217ac04` | Adiciona áudio da máquina na transmissão de tela | `call.ts`, `ConfiguracaoChamadas.vue`, `useConfigChamada.ts`, `SeletorOpcoes.vue`, `ordemMensagens.ts` |
| 2026-09-22 | `cc18872` | Visualizadores de Markdown, Mermaid e PDF; ajustes no chat | `useMarkdown.ts`, `useMermaid.ts`, `VisualizadorPdf.vue`, `emojiAtalhos.ts`, `firebase-messaging-sw.js`, `toque.mp3`, `chamando.mp3`, `MessageList.vue` (indicador fixo) |
| 2026-09-18 | `3d764d1` | Ajusta chat, chamadas e adiciona personalização de cores | `ConfiguracaoCores.vue`, `useCoresPersonalizadas.ts`, `MessageInput.vue`, `CallBar.vue`, `call.ts` (tela sem webcam) |
| 2026-09-17 | `977fa55` | Corrige largura da mensagem de áudio transcrita | `TranscricaoAudio.vue` |
| 2026-09-17 | `05f12c2` | Anexos, avatares e chamadas; modos de exibição e transcrição | `CallWindow.vue` (modos), `TranscricaoAudio.vue`, `useAttachments.ts`, `call.ts` |
| 2026-09-15 | `24806ef` | Muda porta padrão do postgres | README |
| 2026-09-13 | `b203faf` | Ambiente de desenvolvimento e chamadas no celular | `App.vue`, `CallBar.vue`, `vite.config.ts` |
| 2026-09-12 | `75c011f` | Ambiente | `call.ts` (ICE via `/ice`), `conversaApi.ts`, `types/api.ts` |

Antes de 2026-09-12 (março–abril de 2026): base do chat, chamadas, encaminhamento, tema escuro, FCM, navbar, nova janela, SIP, status do usuário, pesquisa global, menções, histórico de chamadas, página de anexos e agendamento (`1b5be8d`, 2026-04-19).

---

## 9. Anexo B — Código morto / legado encontrado

Não replicar sem confirmar com o produto:

- `CallParticipantsModal.vue`: montado no `App.vue`, mas `modalParticipantesChamada` nunca vira `true` (chamada em grupo liga para todos os membros).
- `ImagePreviewModal.vue` / `useImagePreview.ts`: o `MessageInput` declara o evento `open-image-preview` mas não o emite; imagens vão para a fila (ANX-01).
- `ouvirMensagensForeground` (`services/firebase.ts`): sem uso.
- `chamadaFinalizar` / `POST /chamada/finalizar`: na store, sem botão.
- `auth.setApiBase`: sem UI.
- Evento `open-chat-with` do `ChatHeader`: não escutado (a conversa é aberta pela store mesmo assim).
- Dois modais de "Adicionar à chamada" (`AddUserToCallModal.vue` no App e o interno do `CallWindow.vue`) — mesmo comportamento.
- Rota `/ramal` reconhecida pelo parser de URL, mas o item da barra abre o discador.

---

## 10. Atualização de 2026-10-06 (noite) — commit `39d06f9`

Commit **`39d06f9`** — "Campo de mensagem rico, votação em grupo e chat completo na chamada" (35 arquivos, +1774/−684). Acompanha o commit do servidor `8031fa5` (doc 01 §10.13).

### ENV-21 🆕 Campo de mensagem rico (substitui a fila de anexos ANX-01)

- O `MessageInput` virou um `contenteditable` (`utils/editorRico.ts`, `utils/blocosEditor.ts`).
- **O que entra no ponto do cursor:**
  - imagem, vídeo, áudio, arquivo e figurinha entram como **peças** ("átomos"), cada uma **numa linha própria**: o texto depois do cursor desce para a linha de baixo, e o cursor vai para baixo da peça;
  - a menção também é uma peça, mas fica **dentro** da linha de texto.
- **Edição:** o **Backspace apaga uma peça como se fosse um caractere**. Imagens mostram miniatura com "×" no canto.
- **Como os arquivos entram:** seletor de arquivo, colar e **arrastar e soltar dentro do campo**.
- **No envio,** os conteúdos vão **na ordem em que aparecem no campo** (`extrairBlocos`):
  - cada trecho de texto entre peças vira um conteúdo tipo 1;
  - cada peça vira o seu tipo (2/3/4/5/7);
  - linhas vazias entre peças são descartadas;
  - menções viram `@[Nome](id)` dentro do texto.
  - Exemplo: [texto, imagem, texto, figurinha] → conteúdos ordem 1..4 nessa sequência.
- Mensagem só de texto: igual ao antes (ENV-01).
- 🆕 `769bd7d`/`f798e9c`/`d202c06`: o `PUT /mensagem` pode levar `pede_confirmacao` (ENV-24), `postagem` (ENV-26) e `postagem_id` (MSG-22); acima do campo podem aparecer as faixas de confirmação de leitura e de postagem; Ctrl/Cmd+Enter volta a enviar (ENV-12); a sugestão da IA aparece em cinza depois do cursor (ENV-25).
- 🆕 `e33ecba`: nova peça **`codigo`** (`PecaCodigo`): o código inserido pela janela fica no campo como bloco, com prévia, "Editar código" e "Remover código", e vai como um conteúdo tipo 1 próprio, com a cerca (ENV-28). Nova faixa acima do campo **"Respondendo à postagem de <remetente>"** (× "Não responder à postagem"): a próxima mensagem, com qualquer conteúdo, vai com `postagem_id` (MSG-22). A faixa da confirmação, em grupo, ganha " de todos" / " de N pessoas" e "Escolher", e o envio pode levar `confirmacao_usuarios` (ENV-24).
- 🆕 `9c6e8a6`: cada arquivo que entra no campo (seletor, colar, arrastar) pede a sua **permissão de uso** (`imagem`, `video`, `audio` ou `arquivo`, GER-09); os barrados não entram e aparece acima do campo "Você não tem permissão para enviar <tipos>.". No "+", "Arquivo" some sem nenhuma das quatro; "Votação", "Postagem" e "Confirmar leitura" somem sem `votacao`, `postagem` e `confirmacao`. Áudio entra no envio com `duracao` e `intensidade` (§1.7).
- 🆕 `f351190`: nova peça **`localizacao`** (`PecaLocalizacao`): a localização inserida pela janela fica no campo como bloco, com "Localização" / "Trajeto", o resumo, "Editar localização" e "Remover localização", e vai como um conteúdo **tipo 9** próprio, na ordem do campo (ENV-30). O "+" ganha **"Localização"** depois de "Código" (some sem `localizacao`). Com uma pasta aberta (MSG-24), o campo fica **escondido**, com o rascunho guardado.
- **Figurinha** (ENV-04) e **gravação** (ANX-11) com o campo **vazio** são enviadas na hora, sozinhas; com algo escrito, entram como peça.
- **Emoji** entra no ponto do cursor (o cursor é guardado quando o campo perde o foco).
- **Placeholder:** "Digite uma mensagem". Altura máxima de 40% da tela, com rolagem.
- **Botão "+" (Anexar)** abre o `AnexoPopup` com: **Arquivo**, **Código** e (só em grupo) **Votação** (hoje: Arquivo, Código, 🆕 `f351190` Localização, Votação e Postagem em grupo, Confirmar leitura).
- **Agendar** e **Enviar** aparecem quando há conteúdo; sem conteúdo aparece o microfone.
- **Android:** é o equivalente a um campo de composição com "chips" de anexos intercalados. Sugestão de implementação:
  - um `LazyColumn` de blocos editáveis (texto / anexo / figurinha) acima do teclado, com a ordem preservada;
  - ou uma versão simplificada: anexos numa faixa + texto, enviados como [anexos…, texto] (perde a intercalação).
  - **Decisão de produto:** ver FC-416 no doc 06.

### ENV-22 🆕 Criar votação (enquete) — só em grupo

- **Entrada:** "+" → "Votação" (`EnqueteModal.vue`).
- **Textos da tela:** título "Nova votação"; campo "Pergunta" (placeholder "Ex.: Onde vamos almoçar?", máx. 300); "Opções" com 2 campos iniciais ("Opção 1", "Opção 2", máx. 200 cada), "+ Adicionar opção" até **12** e "×" para remover (só com mais de 2); checkbox "Permitir várias escolhas" / "Cada pessoa pode marcar mais de uma opção."
- **Botões:** "Cancelar" / "Criar votação" ("Criando..."). Fica habilitado com pergunta preenchida e ≥ 2 opções preenchidas.
- **API:** `PUT /enquete {conversa_id, pergunta, opcoes (só as preenchidas, trim), multipla}`.
- 🆕 `785bdef` **Data final (opcional):** checkbox "Definir data final" / "Depois dela ninguém vota mais. Dá para mudar ou encerrar antes pela votação." → campo de data e hora (sugestão: amanhã, na próxima hora cheia). Erros: "Informe a data e a hora", "A data final precisa estar no futuro", "A data final não pode passar de 1 ano"; com erro, "Criar votação" fica desabilitado. Vai como `encerra_em` (ISO).
  - Depois de criar, o web recarrega as mensagens e as conversas.
  - Erros do servidor aparecem na caixa vermelha: só grupo, opções repetidas etc.
- 🆕 `9c6e8a6` "Votação" só aparece no "+" com a permissão de uso `votacao` (GER-09); o `PUT /enquete` responde 403 "Você não tem permissão para: criar votações em grupos.".

### MSG-20 🆕 Bolha de votação (`BolhaEnquete.vue`, store `enquetes.ts`)

- **Classificação:** conteúdo único tipo 8 → `Enquete` (antes de Figurinha; vale **mesmo com referência**).
- **Carregamento:**
  - ao montar: `GET /enquete?id=<conteudo>`, com cache por id e leituras simultâneas deduplicadas;
  - enquanto carrega: "Carregando votação..."; em erro, a mensagem do erro.
- **Layout:**
  - nome do remetente (em grupo, se não é minha);
  - "📊 <pergunta>";
  - subtítulo "Escolha uma opção" / "Escolha uma ou mais opções".
- **Cada opção:**
  - marcador **círculo** (única) ou **quadrado** (múltipla) com ✓ quando marcada;
  - texto; contagem;
  - barra com `round(votos / total_votantes × 100)%` (na múltipla as barras podem somar mais de 100%, "como no WhatsApp");
  - nomes de quem votou abaixo (e no tooltip; "Ninguém votou ainda").
- **Rodapé:** "1 pessoa votou" / "N pessoas votaram" + hora/status da mensagem.
- **Votar:**
  - **única:** tocar em outra opção troca o voto; tocar na marcada tira o voto;
  - **múltipla:** marca e desmarca.
  - Envia sempre a lista completa: `POST /enquete/votar {enquete_id, opcoes}` → substitui a enquete no cache.
  - Os botões ficam desabilitados enquanto vota; em erro, mostra "Não foi possível votar".
- **Tempo real:** WS 62 `{enquete_id}` → relê `GET /enquete` **só se a enquete está no cache**, ou seja, na tela.
- 🆕 `785bdef` **Data final e encerramento:**
  - aberta com data: o subtítulo ganha "· encerra hoje 18:00" / "amanhã 08:30" / "12/10 18:00" (com o ano, se for outro);
  - quem criou vê "Definir data final" / "Alterar data final": um editor na bolha com "Data final", "Tirar data" (se tem), "Cancelar" e "Salvar" (`PATCH /enquete`; erro "Não foi possível mudar a data final");
  - quem criou a votação ou o grupo vê "Encerrar votação": confirmação de perigo "Encerrar votação" / "Depois de encerrada, ninguém vota mais e o resultado fica como está." / "Encerrar" (`POST /enquete/encerrar`; erro "Não foi possível encerrar a votação");
  - encerrada (pelo servidor, ou o prazo passou com a bolha aberta, na hora exata): "🔒 Votação encerrada <quando>", opções desabilitadas, a mais votada (ou as empatadas, com votos) em negrito com 🏆; os botões somem;
  - o resumo (`EnqueteResumo`) mostra "Votação encerrada".
- **Resumos:** "Votação" em prévias, notificações, atividades (`AtividadesPage`), citações (`messageReferences.ts`) e chat da chamada ("[Votação]").
- **Encaminhar:** o servidor recusa tipo 8 em `PUT /mensagem` (400). O web não esconde "Encaminhar" para enquete, então encaminhar dá erro. **No Android: esconder "Encaminhar" para enquete.**

### CHA-24 🆕 Chat completo na chamada

- **Onde:** só na janela principal, não na popup da chamada.
- **Quando:** com o grupo da chamada já criado, o painel "Chat da chamada" usa o **mesmo `MessageList` e `MessageInput` do chat**: anexos, respostas, reações, figurinhas, encaminhar, visualizador de imagens. O painel fica mais largo (`sm:w-96 lg:w-[28rem]`).
- **Conversa ativa:** enquanto o painel está aberto, a conversa do grupo da chamada vira a **conversa ativa**; ao fechar, volta a conversa anterior.
- **Antes da primeira mensagem** (o grupo ainda não existe), continua o painel simples, só de texto (CHA-18). A primeira mensagem cria o grupo (`PUT /chamada/chat`) e abre o modo completo.
- **Android:** o painel de chat da chamada pode reaproveitar a própria tela de chat (`ChatScreen`) num bottom sheet/painel, com a conversa do grupo da chamada.
- 🆕 `964ed72`: com a chamada de vídeo na **aba própria** do computador (CHA-13), o painel volta a ser o simples, só de texto (CHA-18); o completo fica para a chamada dentro do app (celular, aba bloqueada). E o "grupo da chamada" pode ser a **própria conversa de origem** — na direta, a conversa direta (CHA-18). **🆕 `6d20ff7`: deixou de valer** — cada ligação nova volta a ter o seu grupo; só a ligação feita dentro do chat de outra ligação reaproveita esse chat (CHA-18). 🆕 `e33ecba`: a aba própria virou janela popup (CHA-13).

### Outros ajustes do commit
- **Bolhas com vários conteúdos:** saiu o divisor entre conteúdos, e as imagens dentro da bolha padrão ou de referência ficam **sem moldura** (`imagemSemMoldura`).
- **Modais:** o encaminhamento passou a ficar por cima da janela da chamada (`z-50`).
- **Testes:** `tests/unit/enquetes.test.ts`, `blocosEditor.test.ts`, `entradaEAvatar.test.ts` (reescrito), `CallWindow.test.ts`.

## 11. Atualização de 2026-10-08 — commits `eaa8bac` e `785bdef`

Os dois commits são de 2026-10-07 (noite) e acompanham os do servidor `d4435db` (limite de reações) e `5cad911` (votação com data final), descritos no doc 01 §10.12, §10.13 e §20. Não há IDs novos: são mudanças em funcionalidades que já existiam (o total segue 136).

### `eaa8bac` — "Painel do grupo, limite de reações, votação oculta e chave na chamada"
- **CON-08 / ANX-13 — painel "Dados do grupo"** (`PainelGrupo.vue`, substitui o `GroupMembersModal.vue`):
  - abre à direita do chat (por cima, em tela pequena) pelo **avatar do grupo** no cabeçalho ("Dados do grupo") ou pelo botão "Participantes e anexos";
  - em cima: "Nome do grupo" + "Renomear" ("Salvando..."); "N participantes"; "Adicionar pessoas" abre uma busca ("Buscar contato...") entre os contatos fora do grupo ("Adicionando...", "Nenhum contato encontrado", "Todos os contatos já estão no grupo");
  - cada participante com foto, bolinha de online, "(você)" e "Remover" ("Removendo..."; o servidor só aceita remover o próprio vínculo, pendência S15);
  - embaixo: "Anexos" com a mesma lista e filtros da página de Anexos (ANX-13), abrindo a galeria ou a mensagem.
- **ENV-17 — limite de reações:** 5 emojis diferentes por pessoa (conferido antes da reação otimista); erro em diálogo "Não foi possível reagir"; 5 chips à mostra e o resto num "+N".
- **MSG-16 — votação oculta:** revelar mostra o resumo só de leitura (pergunta + contagem por opção).
- **ANX-07 — HTML:** links `#...` rolam dentro do documento.
- **CHA-11 — chave nos liga/desliga** da chamada (pílula com ícone + chave).
- **CHA-18 / CHA-24 — chat da chamada:** o grupo é criado no primeiro clique no campo, que já abre o chat completo.

### `785bdef` — "Votação com data final e opção de encerrar"
- **ENV-22:** data final opcional ao criar.
- **MSG-20:** "encerra …" no subtítulo; "Definir/Alterar data final" (quem criou); "Encerrar votação" (quem criou a votação ou o grupo), com confirmação; encerrada mostra "🔒 Votação encerrada …", destaca a mais votada com 🏆 e não aceita votos; o prazo que vence com a bolha aberta encerra na hora.

### Para o Android
- Reações (7.2, já feita): falta o limite de 5 antes da otimista e o chip "+N" (no celular, um toque abre a lista dos demais).
- Votação (7.12): data final na criação, editar/tirar a data, encerrar, estado encerrado e prazo vencendo na tela; resumo da votação oculta.
- Grupo (2.10 / 8.6): abrir os dados do grupo pelo avatar do cabeçalho do chat e mostrar os anexos do grupo na mesma tela.
- Chat da chamada (6.x): criar o grupo ao tocar no campo, não na primeira mensagem.
- Chave nos controles da chamada: adaptação visual; no Android os botões de liga/desliga já mostram o estado pelo ícone e pela cor.
- HTML (ANX-07): o Android abre o HTML com outro app (4.x), então não se aplica.

## 12. Atualização de 2026-10-08 (noite) — commits `7322e83` e `e8d82cb`

Dois commits do web de 2026-10-08 (noite). No servidor só entrou `779c8ed` (o `iniciar-desenvolvimento.bat` também atualiza as dependências da API), sem mudança de contrato. Há **um ID novo**, ENV-23, e o total passa a **137**.

### `7322e83` — "Campo de mensagem: seleção, desfazer e rascunho; reações rápidas e agendadas"
- **MSG-17 / ENV-13 / ENV-14 — agendadas fora do chat:**
  - a mensagem agendada **não aparece no chat** antes da hora (`chat.ts#agendadasAtivas`, `MessageList.vue`);
  - com o campo **vazio**, um **relógio** ao lado do microfone mostra quantas há, com o título "1 mensagem agendada" ou "N mensagens agendadas"; com algo no campo, o relógio some;
  - o relógio abre "Mensagens agendadas" (`MensagensAgendadasModal.vue`), só da conversa aberta e em ordem de horário. Cada linha tem:
    - o horário (`formatarPrazo`: "hoje 18:00", "amanhã 08:30", "12/10 18:00", com o ano se for outro);
    - o resumo do conteúdo, até 3 linhas;
    - "Cancelar" ("Cancelando..."), com a confirmação "Cancelar mensagem agendada" / "Ela não será enviada." / "Cancelar envio" / "Voltar"; em erro, "Não foi possível cancelar";
  - a lista fecha sozinha quando fica vazia;
  - na hora marcada, a mensagem sai da lista e entra no chat, sem esperar a próxima leitura (`useAgora` na próxima agendada).
- **MSG-19 — encaminhada de encaminhada:** dentro da citação, os conteúdos que a encaminhada copiou da citação de baixo (já à mostra) não se repetem (`semCopiasDaReferencia`: tira uma cópia por igual `tipo:conteudo`, na mesma quantidade; resposta não copia nada).
- **ENV-23 🆕 — rascunho por conversa** (`services/rascunhos.ts`, IndexedDB):
  - um rascunho por usuário e conversa, com o campo (texto, menções, peças), os arquivos e o "respondendo a..." ou encaminhando;
  - gravado com um pequeno atraso a cada mudança; vazio apaga;
  - restaurado ao voltar à conversa, mesmo depois de fechar o navegador;
  - apagado ao enviar.
- **ENV-20 — menu:**
  - Ctrl + clique direito abre só a barra de reações rápidas, junto do clique;
  - toque longo no celular (500 ms, tolerância de 10 px) abre o menu completo e engole o clique ao soltar.
- Campo rico (ENV-21): a peça selecionada fica azul; Ctrl+Z/Ctrl+Y próprios. Copiar GIF leva a imagem inteira.

### `e8d82cb` — "Campo de mensagem no Tiptap; 'mais emojis' na barra de reações rápidas"
- **ENV-21:** o campo passa a ser um editor Tiptap (ProseMirror) no lugar do `contenteditable` feito à mão. Anexos e figurinhas são peças do editor; a peça pode ser arrastada para mudar a ordem, e copiar e colar uma peça a duplica. O rascunho guarda o documento do editor.
- **ENV-20:** a barra de reações rápidas ganha o botão da lista completa de emojis.

### Para o Android
- **Agendadas (7.10, já feito com o selo no chat):** passar para o modelo novo:
  - a agendada fica fora da lista do chat até a hora;
  - relógio com o número ao lado do microfone, com o campo vazio;
  - folha "Mensagens agendadas" com horário, resumo e "Cancelar".
- **Citação (7.5):** aplicar `semCopiasDaReferencia` nos níveis aninhados.
- **Rascunho (ENV-23):** guardar no aparelho, por conversa, o texto com as menções, a fila de anexos e a resposta ou encaminhamento pendente; restaurar ao abrir a conversa.
- **Menu (ENV-20):** o Android já abre o menu completo no toque longo, com "Mais emojis". Ctrl + clique direito não se aplica.
- **Tiptap (ENV-21):** detalhe interno do web; a decisão FC-416 do Android continua valendo.

## 13. Atualização de 2026-10-13 — commits `769bd7d` a `d202c06`

Cinco commits do web, de 2026-10-09 a 2026-10-11, que acompanham os do servidor `b8ea0c2` (presença, confirmação de leitura, resumo por IA e chamar de novo), `ea7e974` (resumo pede JSON por `json_schema`), `ea02f57` (sugestão de texto), `a40fd47` (chat da chamada reaproveita a conversa) e `12cf0cf` (postagens). Há **13 IDs novos** e o total passa de 137 para **150**. Os textos foram conferidos no código do web em `d202c06`.

### Commits
- **`769bd7d`** (2026-10-09 21:24) — "Presença, confirmação de leitura, resumo por IA e ajustes nas chamadas": presença ativo/ausente/offline com visto por último e "nesta conversa"; aba Privacidade; confirmação de leitura; IA na aba Sistema e "Resumir conversa"; imagem ou emoji do grupo; seção "Chamadas" na lista; "Chamar de novo" na grade; ligar o vídeo sem derrubar; "Transcrever" só com transcritor; Ctrl+Enter volta a enviar; tile local mostra a tela compartilhada.
- **`05507a0`** (2026-10-09 22:07) — "Resumo da conversa guardado no IndexedDB": último resumo por usuário e conversa; "Últimas 400 mensagens"; a linha "Resumo de …".
- **`f798e9c`** (2026-10-09 22:21) — "Adiciona sugestão de texto": continuação sugerida pela IA em cinza; preferência na aba Privacidade.
- **`964ed72`** (2026-10-10 13:26) — "Chamada de vídeo em aba própria, chat da ligação expandido e testes e2e": aba `chamada.html` no computador; "Voltar ao chat" e barra no topo no celular; chat da ligação só leitura na bolha e no histórico; testes Playwright (`e2e/`, fora do escopo do Android).
- **`d202c06`** (2026-10-11 18:00) — "Postagens em grupo, todos os emojis, figurinhas Noto e pesquisa nova": postagem com respostas embaixo; seletor com todos os emojis; só figurinhas Noto; painel de resultados da pesquisa; faixa de destaque na mensagem encontrada.

### IDs novos (13)
- **PRE-02** — presença enviada pelo cliente (WS 63) e o próprio status no avatar da barra.
- **PRE-03** — "visto por último" e "nesta conversa" (`GET /conversa/presentes`, WS 63 recebido).
- **CFG-09** — aba Configurações › Privacidade, com a seção de IA.
- **ENV-24** — pedir confirmação de leitura ("+" › "Confirmar leitura").
- **MSG-21** — confirmação de leitura na bolha (`POST /mensagem/confirmar`, WS 8).
- **CON-14** — resumo da conversa por IA, com cache no IndexedDB.
- **ENV-25** — sugestão de texto da IA (`POST /ia/sugestao`).
- **GER-08** — recursos do servidor (`GET /recursos`).
- **CON-15** — imagem ou emoji do grupo.
- **CHA-25** — convidados fora da chamada na grade, "Chamar de novo".
- **CHA-26** — chat da ligação só leitura na bolha e no histórico.
- **ENV-26** — criar postagem em grupo.
- **MSG-22** — bolha de postagem com respostas embaixo (WS 64).

### IDs alterados
- **PRE-01** — ativo/ausente/offline, `/contatos/presenca`, WS 60 com `estado`/`visto_em`, bolinha amarela.
- **CON-01** — seção "Chamadas (N)"; emoji do grupo; bolinha amarela. **CON-02** — bolinha em "Nova conversa". **CON-08** — imagem/emoji e " · nesta conversa". **CON-09** — subtítulo de presença e "Resumir conversa".
- **MSG-06** — destaque em faixa da largura toda (1,5 s + 0,7 s). **MSG-07** — classe Postagem. **MSG-13** — "Ver chat da chamada".
- **ENV-01 / ENV-21** — campos novos no `PUT /mensagem`. **ENV-03** — seletor com todos os emojis. **ENV-04** — só o pacote Noto. **ENV-12** — Ctrl/Cmd+Enter; Tab/Esc da sugestão. **ENV-17** — "Mais emojis" com o seletor novo.
- **ANX-12** — "Transcrever" só com transcritor.
- **PES-01** — painel de resultados novo; **corrigida** a ordem (do mais antigo ao mais novo).
- **CHA-02 / CHA-11** — sem "Compartilhar tela" no celular. **CHA-13** — aba própria no computador; barra no topo no celular. **CHA-15** — tile local com a tela. **CHA-16** — etapas e troca sem derrubar. **CHA-17** — sinal `participantes`. **CHA-18 / CHA-24** — o chat reaproveita a conversa de origem (🆕 **revisto em 2026-10-15**: o servidor `6d20ff7` desfez isso; ver §14); na aba própria o chat é o simples. **CHA-22** — "Ver chat da chamada" no histórico.
- **NOT-02** — notificação de resposta a postagem.
- **CFG-01** — aba Privacidade. **CFG-07** — seção "Inteligência artificial" na aba Sistema.
- **GER-07** — fallback com o emoji do grupo.
- §1.2 (estado), §1.3 (datas), §1.4 (WebSocket), §2 (rotas, barra, layout, painéis, abas), §4.1 (rotas 59–69 e alterações), §4.2 (eventos 8, 62, 63, 64 e campos novos do 60 e do 57), §5.3/§5.4 (republicar e reassinar sem cortar), §6 (textos e regras 14–16), §7 (adaptações novas) e as notas em ENV-21 e CHA-24 da §10.

### O que muda para o Android
**IDs novos**
- **PRE-02:** mandar `{tipo:63, ativo, conversa_id}` a partir do ciclo de vida (`ProcessLifecycleOwner`, que o `ConexaoTempoReal` já usa): ativo em primeiro plano com a conversa aberta, `ativo:false` e `conversa_id:null` em segundo plano, ao conectar o WS e ao trocar de conversa; sem isso o servidor mostra o Android "ativo agora" o tempo todo. Mostrar o próprio status no avatar do perfil.
- **PRE-03:** `GET /conversa/presentes` ao abrir a conversa e ao reconectar; tratar o WS 63 recebido; subtítulo do cabeçalho da direta com os textos exatos e " · nesta conversa" nos membros do grupo.
- **CFG-09:** tela Privacidade com as três chaves (`GET /usuario/privacidade`, `PATCH /usuario`), salvando na hora; a caixa da sugestão da IA (preferência local) só com `recursos.ia`.
- **ENV-24:** item "Confirmar leitura" no "+", faixa acima do campo com ×, `pede_confirmacao: true` na próxima mensagem.
- **MSG-21:** chip de confirmação antes das reações (caixa para quem recebe; contagem para quem enviou); toque lista quem confirmou; `POST /mensagem/confirmar`; WS 8; DTO `confirmacao`.
- **CON-14:** "Resumir conversa" no menu do cabeçalho só com `recursos.ia`; tela/folha do resumo com os períodos, consulta a cada 2 s, "Ver na conversa" e o último resumo guardado por usuário e conversa (Room/DataStore).
- **ENV-25:** sugestão da IA depois de 600 ms parado (≥ 3 caracteres, cursor no fim), com aceite por toque (sem Tab no teclado virtual) e preferência desligável; avaliar custo de rede/bateria antes.
- **GER-08:** `GET /recursos` no início da sessão (e ao voltar da aba Sistema); esconder Transcrever, Resumir e a sugestão quando desligados.
- **CON-15:** "Imagem" / "Emoji" / "Remover" nos dados do grupo (`PATCH /conversa {avatar_anexo_id, emoji}`); usar `emoji` no lugar da inicial em todos os avatares de grupo.
- **CHA-25:** tiles "Chamando..." / "Não atendeu" + "Chamar de novo" (`POST /chamada/chamar-novamente`); tratar o sinal `{acao:'participantes'}` relendo os dados da chamada.
- **CHA-26:** "Ver chat da chamada" na bolha da ligação e no histórico, só leitura, com "Abrir o chat completo"; ler `chat_chamada_id` e `conversa_chat_id` nos DTOs.
- **ENV-26:** item "Postagem" no "+" (só em grupo), faixa com título opcional ≤ 150, `postagem: {titulo}` no envio.
- **MSG-22:** classificar a postagem antes de Imagem; cartão com as 2 últimas respostas, "Ver todas", campo de resposta (`postagem_id`), `GET /postagem/respostas`, `POST /postagem/visualizar`, WS 64 e a notificação "<nome> respondeu a uma postagem"; DTO `postagem`.

**IDs alterados**
- **PRE-01:** trocar `GET /contatos/online` (hoje em `ConversaApi` e `PresencaRepositorio`) por `/contatos/presenca`; ler `estado` e `visto_em` do WS 60 (inclusive o do próprio usuário); bolinha amarela para ausente.
- **CON-01:** seção recolhível "Chamadas (N)" (conversas com `chamada: true`, não fixadas nem arquivadas) antes de "Arquivadas (N)"; emoji do grupo; bolinha amarela.
- **CON-02 / CON-08 / CON-09:** bolinhas com três estados; imagem/emoji e " · nesta conversa" nos dados do grupo; subtítulo de presença e "Resumir conversa" no cabeçalho.
- **MSG-06:** trocar o destaque da mensagem encontrada por uma faixa da largura toda que aparece de uma vez, fica 1,5 s e some em 0,7 s.
- **MSG-07:** nova classe Postagem (depois de Excluida/Chamada, antes de Imagem).
- **MSG-13:** "Ver chat da chamada" na bolha da ligação (CHA-26).
- **ENV-01 / ENV-21:** acrescentar `pede_confirmacao`, `postagem` e `postagem_id` (opcionais) no DTO do `PUT /mensagem`.
- **ENV-03:** o Android usa `EmojiPickerView` (androidx.emoji2) no "Mais emojis", com tons de pele, bandeiras e FE0F: as reações vão divergir do web (👍 e 👍️ viram chips diferentes). Normalizar antes de reagir/enviar (tirar o FE0F que sobra, sem tom de pele) ou montar o seletor com `/emojis/pt.json` (8 categorias, Recentes até 32, pesquisa sem acento).
- **ENV-04:** o Android embarca `basico/rostos/coisas` (`feature/chat/src/main/assets/figurinhas`, `core/model/.../Figurinhas.kt`): trocar pelo pacote `noto` (55 figurinhas, nomes e crédito "Animated Noto Emoji © Google, CC BY 4.0"); figurinha desconhecida mostra "Figurinha".
- **ENV-12:** com teclado físico, Ctrl+Enter envia (se houver a opção "Enter envia").
- **ENV-17:** "Mais emojis" com o mesmo seletor/normalização do ENV-03.
- **ANX-12:** esconder "Transcrever" sem `recursos.transcricao` (continua mostrando a transcrição pronta ou em andamento).
- **PES-01:** a pesquisa na conversa do Android segue "mais recentes primeiro" (o erro antigo deste doc): inverter para do mais antigo ao mais novo; cabeçalho "N resultados para “termo”" / "Nenhuma mensagem com “termo”"; "Você" nas minhas; trecho a partir de 30 caracteres antes do termo.
- **CHA-02 / CHA-11:** sem mudança no Android (compartilhar tela por `MediaProjection` continua); quem perdeu o botão foi o navegador de celular.
- **CHA-13:** aba própria não se aplica; manter a Activity da chamada com PiP e a barra/notificação de chamada em andamento em qualquer tela (equivale ao "Voltar ao chat").
- **CHA-15:** o tile local deve mostrar a tela compartilhada enquanto ela está no ar.
- **CHA-16:** mostrar as três etapas; publicar o WHIP novo antes de fechar o antigo e trocar cada assinatura só quando a nova estiver pronta; no WS 56, reassinar sempre quem republicou.
- **CHA-17:** tratar `{acao:'participantes'}` no sinal 57.
- **CHA-18 / CHA-24:** o `PUT /chamada/chat` pode devolver a **conversa de origem** (na direta, a própria conversa direta): não supor grupo novo nem mexer nos membros; abrir o chat da chamada nessa conversa. **🆕 Revisto em 2026-10-15 (servidor `6d20ff7`): deixou de valer.** A ligação nova volta a criar um grupo próprio, nomeado com os participantes; a conversa de origem só é devolvida quando a ligação foi feita dentro do chat de outra ligação. Continua valendo não supor nada: abrir o chat no `conversa_id` devolvido (§14).
- **CHA-22:** botão "Ver chat da chamada" no histórico quando vier `conversa_chat_id`.
- **NOT-02:** com o app aberto, notificação "<nome> respondeu a uma postagem" pelo WS 64, agrupada na conversa; em segundo plano chega o push comum de mensagem (o servidor trata a resposta como mensagem nova: título = quem respondeu, corpo = o texto).
- **CFG-01 / CFG-07:** aba Privacidade; na aba Sistema do Android (`SistemaRepositorio`), a seção "Inteligência artificial" com Endereço, Modelo, Token e "Testar conexão".
- **GER-07:** emoji do grupo como fallback do avatar.
- **§1.3:** o parser de datas precisa aceitar `confirmada_em`, `visto_em`, `encerra_em` e `encerrada_em`.

## 14. Atualização de 2026-10-15 — commits `e33ecba` e `8b43965`

Dois commits do web, de 2026-10-13 e 2026-10-14, que acompanham os do servidor **`6d20ff7`** (confirmação de leitura com lista e prazo — migração 043, `POST /mensagem/pedir-confirmacao`, WS 65 —, chat novo por ligação, pesquisa nos chats das ligações, áudio que ganha vídeo vira vídeo) e **`e25c4db`** (atividade de resposta ou menção já lida deixa de contar como nova; WS 61 ao ler). Há **3 IDs novos** e o total passa de 150 para **153**. Os textos foram conferidos no código do web (`git show e33ecba`, `git show 8b43965`) e as regras no código do servidor (`6d20ff7`, `e25c4db`). Os testes e2e novos (`e2e/codigo.e2e.ts`, `e2e/grupo.e2e.ts`) ficam fora do escopo do Android.

### Commits
- **`e33ecba`** (2026-10-13 19:27) — "Confirmação de leitura com lista, código no campo, postagem na largura do chat e ajustes": quem precisa confirmar (modal com filtro), pedir depois pelo menu até 24 h, caixa só para quem está na lista e "Falta confirmar"; código "Enviar" ou "Inserir na mensagem" como peça no campo; postagem na largura do chat, resposta pelo campo de mensagem, foto de quem respondeu; chat da ligação com os conteúdos; pesquisa nos chats das ligações; chamada de vídeo em janela popup; zoom no ponteiro; ícone no lugar da etiqueta "Grupo"; imagem ou emoji ao criar o grupo.
- **`8b43965`** (2026-10-14 20:39) — "Temas prontos, balões com cores próprias, seletor de emoji rápido e ajustes": temas Padrão, Teams, WhatsApp e Messenger; variáveis `--bolha-*`; tons claros no escuro; categorias do seletor de emoji criadas sob demanda; "Ver fonte" no visualizador de HTML; escolha da tela na janela da chamada e cancelar sem erro; chegada de mensagem com a "Últimas" fora da tela.
- Servidor **`6d20ff7`** (2026-10-13) e **`e25c4db`** (2026-10-14): descritos acima; sem mudança no push.

### IDs novos (3)
- **ENV-27** — pedir confirmação de leitura depois do envio (menu da mensagem, até 24 h; `POST /mensagem/pedir-confirmacao`; WS 65).
- **ENV-28** — código como peça no campo ("Inserir na mensagem", "Editar código", "Remover código"; cada peça vira um conteúdo tipo 1 com a cerca).
- **CFG-10** — temas prontos em Configurações › Cores (`conversa.temaCores`, variáveis dos balões).

### IDs alterados
- **ENV-24** — em grupo, modal "Quem precisa confirmar" ("Filtrar por nome", "Todos (N)", "Pronto"); faixa com " de todos" / " de 1 pessoa" / " de N pessoas" e "Escolher"; `confirmacao_usuarios`.
- **MSG-21** — `pode_confirmar` e `destinatarios`; caixa só para quem está na lista; dica com "Falta confirmar"; WS 65; 403 no `POST /mensagem/confirmar` para quem está fora.
- **ENV-20** — item "Pedir confirmação de leitura" depois de "Copiar" e antes de "Ocultar"; na postagem, o ⋯ fica dentro do cartão.
- **ENV-01** — `confirmacao_usuarios`. **ENV-21** (§10) — peça `codigo`; faixa "Respondendo à postagem de …".
- **ENV-09 / ENV-10** — "Inserir na mensagem"; "Enviar" não limpa mais o campo; Ctrl/Cmd+Enter insere.
- **MSG-22** — cartão na largura toda; "Responder à postagem" / "Respondendo no campo de mensagem" leva ao campo, faixa "Respondendo à postagem de <remetente>" com × "Não responder à postagem"; resposta com qualquer conteúdo via `postagem_id`; foto nas respostas; saiu o campo de dentro do cartão.
- **CHA-26** — conteúdos como no chat (imagem com visualizador, áudio, arquivo, figurinha); 24rem.
- **PES-01** — resultados dos chats das ligações com o selo "no chat da ligação", que abrem aquela conversa na mensagem; o web parou de filtrar por conversa.
- **CHA-18 / CHA-24 / CON-01 / MSG-13 / CHA-22** — (servidor `6d20ff7`) o chat da ligação volta a ser um **grupo novo**, nomeado com os participantes, sem "Chamada: "; só reaproveita dentro do chat de outra ligação. **Revistas** as notas de 2026-10-13 que diziam que, na direta, o chat era a própria conversa (CHA-18, nota de CHA-24 na §10, §13).
- **CON-01** — ícone de pessoas com o rótulo "Conversa em grupo" no lugar da etiqueta "Grupo". **CON-07** — imagem ou emoji ao criar o grupo, "Criando..."; ordem `PUT /conversa` → `PUT /conversa/usuario` → `PATCH /conversa`.
- **CHA-13** — janela popup. **ANX-04** — zoom da roda no ponteiro. **ANX-07** — "Ver fonte" / "Ver página".
- **CHA-15 / CHA-01** — a escolha da tela aparece na janela da chamada; fechar a escolha sem escolher não é erro.
- **CHA-16 / MSG-13 / CHA-22** — (servidor `6d20ff7`) chamada de áudio que ganha vídeo passa a ser de vídeo (mensagem e histórico).
- **MSG-03** — regra 5: com a "Últimas" já fora da tela (acima), a mensagem que chega rola até ela.
- **ENV-03** — desempenho: categorias criadas sob demanda. **CFG-05** — ajustes por cima do tema pronto; escolher um tema os apaga.
- **ATV-01 / ATV-02** — (servidor `e25c4db`) `nova` e a contagem descontam resposta/menção já lida; WS 61 também ao ler.
- §1.2 (estado), §2.1/§2.3 (popup), §2.4 (modal novo e linhas alteradas), §4.1 (rotas 10, 12, 21, 28, 42, 44, 61 e a nova 70), §4.2 (evento 65; o 61 também ao ler), §6 (textos e regras 17–18), §7 (adaptações novas, com "não se aplica" para temas, popup, "Ver fonte" e a escolha da tela na janela) e a contagem.

### O que muda para o Android
**IDs novos**
- **ENV-27:** depende de ENV-24 e MSG-21, que o Android **ainda não tem** (não há `pede_confirmacao` nem DTO `confirmacao` no código). Ao fazê-los: item "Pedir confirmação de leitura" no menu do toque longo, só na própria mensagem salva, não oculta, que ainda não pede, enviada há menos de 24 h (conferir ao abrir o menu); na direta pede direto, no grupo abre a folha "Quem precisa confirmar" com o botão "Pedir confirmação"; `POST /mensagem/pedir-confirmacao {mensagem_id, usuarios?}` e gravar no Room o `confirmacao` devolvido; erro no diálogo "Não foi possível pedir a confirmação" com a mensagem do servidor; WS 65 como evento novo no `EventoSocket` (hoje cairia em `Desconhecido`), trocando o `confirmacao` da mensagem que estiver no Room.
- **ENV-28:** o "Inserir código" do Android (`feature/chat/.../InserirCodigo.kt`) só tem "Cancelar" e "Enviar". Acrescentar **"Inserir na mensagem"** e a peça de código no campo (cartão com "<linguagem> · N linhas", 4 linhas de prévia, "Editar código" reabrindo a janela como "Editar código" com "Salvar", e "Remover código"), na faixa de anexos da decisão FC-416; no envio, cada peça vira um conteúdo tipo 1 com a cerca, na ordem. **Achado:** o "Enviar" do Android **já manda sem limpar o campo** — `CampoMensagem.kt` chama `acoes.aoEnviar(bloco) {}`, sem o `aoGravar` que limpa o texto no envio normal —, igual ao web novo; manter assim.
- **CFG-10:** **não se aplica** (o Android usa as cores do FMX, `docs/design/cores.md`; §7).

**IDs alterados**
- **ENV-24 / MSG-21:** quando forem feitos, já no modelo novo: DTO `confirmacao {total, confirmou, pode_confirmar, destinatarios:[{usuario_id, nome}], usuarios}`; caixa "Confirmar leitura" só com `pode_confirmar || confirmou`, e a contagem para os demais (inclusive quem enviou); "Falta confirmar" na lista aberta pelo toque; em grupo, a folha "Quem precisa confirmar" antes de marcar e "Escolher" na faixa; `confirmacao_usuarios` no `PUT /mensagem` só quando não forem todos; o 403 do `/mensagem/confirmar` vira o diálogo de erro.
- **ENV-20:** item novo no menu (ENV-27), entre "Copiar" e "Ocultar". O ⋯ dentro do cartão da postagem não se aplica (no Android o menu abre pelo toque longo).
- **ENV-01 / ENV-21:** acrescentar `confirmacao_usuarios` (e o `postagem_id` da resposta pelo campo) ao DTO do `PUT /mensagem`.
- **ENV-09 / ENV-10:** colar > 10 linhas abre a janela, agora com "Inserir na mensagem"; "Enviar" continua sem limpar o campo (já é assim). Ctrl+Enter, com teclado físico, insere.
- **MSG-22:** o Android ainda não tem postagem: fazer já no modelo novo e esquecer o campo dentro do cartão descrito na §13. Cartão na largura toda; botão "Responder à postagem" (e "Respondendo no campo de mensagem") que liga a faixa "Respondendo à postagem de <remetente>" acima do campo, com × "Não responder à postagem"; a próxima mensagem do campo, com qualquer conteúdo, vai com `postagem_id` e não entra na lista do chat; foto de quem respondeu (membros do grupo, contatos ou a inicial).
- **CHA-26:** (ainda não feito) mostrar os conteúdos com os mesmos componentes da bolha (imagem abrindo o visualizador, player de áudio, arquivo com download, figurinha), não os resumos em texto; altura máxima maior, com rolagem.
- **PES-01:** **Achado:** `core/data/.../mensagens/MensagensRepositorio.kt#pesquisar` filtra `it.conversaId == conversaId` (comentário "Como o web: se o servidor não filtrar pela conversa, filtra aqui") e **descartaria os resultados dos chats das ligações**: tirar esse filtro. E `ChatViewModel.abrirResultado` chama `irParaMensagem(mensagem.id, conversaId)` com a conversa aberta: passar `mensagem.conversaId`, porque o `irParaMensagem` já abre outra conversa (`EventoChat.AbrirConversa`). Mostrar o selo "no chat da ligação" quando o `conversaId` do resultado for outro. (A ordem do mais antigo ao mais novo, §13, continua pendente.)
- **CHA-18 / CHA-24:** o `PUT /chamada/chat` volta a devolver, quase sempre, um grupo novo com o nome dos participantes (sem "Chamada: "). O Android já abre o `conversa_id` devolvido, então o fluxo não muda; vale a nota revista da §13 (não supor que é a conversa da ligação, nem o contrário).
- **CON-01:** a etiqueta "Grupo" da lista (`feature/conversas/.../lista/ConversasTela.kt`, string `etiqueta_grupo`) vira o ícone de pessoas com `contentDescription` "Conversa em grupo". Com um grupo novo por ligação, a seção "Chamadas (N)" (`chamada: true`, que o Android ainda não lê) fica mais importante: sem ela, cada ligação com chat vira mais um grupo na lista principal.
- **CON-07:** na criação do grupo (`feature/conversas/.../grupo/CriarGrupo.kt`), "Imagem" / "Emoji" / "Remover" com prévia; ao criar: upload da imagem (se houver) → `PUT /conversa` → `PUT /conversa/usuario` de cada membro → `PATCH /conversa {id, avatar_anexo_id, emoji}`; botão "Criando..." enquanto cria.
- **CHA-13:** não se aplica (janela popup).
- **ANX-04:** a pinça do visualizador (`feature/chat/.../Anexos.kt#ImagemComZoom`, com `rememberTransformableState`) amplia em torno do centro; para acompanhar o web, ampliar no ponto entre os dedos (centroide, `detectTransformGestures`) e o duplo toque no ponto tocado.
- **ANX-07:** não se aplica (o Android abre o HTML com outro app).
- **CHA-15 / CHA-01:** a escolha na janela não se aplica; vale o resto: **cancelar a escolha da tela (`MediaProjection`) não é erro** — no meio da chamada nada acontece; ao começar compartilhando, a chamada não começa, sem aviso.
- **CHA-16 / MSG-13 / CHA-22:** nada a fazer: o servidor já grava a mensagem e o histórico como vídeo.
- **MSG-03:** o Android não tem a regra 5 (perto do fim, sempre rola até a nova, `ChatTela.kt`), o que já bate com o comportamento novo quando a "Últimas" está fora da tela; se a regra for feita, só com a linha à vista.
- **ENV-03:** sem mudança (o seletor do Android é o `EmojiPickerView`; continua valendo a normalização da §13).
- **CFG-05:** não se aplica (cores do FMX).
- **ATV-01 / ATV-02:** nada a mudar no código: o Android relê `GET /atividades/novas` a cada WS 61 (`AtividadesRepositorio.aoReceberAviso`), então o badge passa a cair sozinho quando a resposta ou menção é lida no chat, e o `nova` da lista vem do servidor. Conferir no teste manual (ler uma menção no chat e ver o badge cair). (🆕 `9c6e8a6`: `nova` virou `pendente`, §15.)

## 15. Atualização de 2026-10-15 (noite) — commit `9c6e8a6`

Um commit do web, de 2026-10-15 22:42, que acompanha o do servidor **`ef13c22`** (mesmo título; migrações 044 a 052; o Qdrant entra no `docker-compose` para a base de conhecimento). Há **10 IDs novos** e o total passa de 153 para **163**. Os textos foram conferidos no código do web (`git diff 8b43965..9c6e8a6`, `CLAUDE.md` §8) e as regras no servidor (`git diff e25c4db..ef13c22`).
- Não há evento WebSocket novo nem mudança no push.
- Os testes novos do web (12 arquivos em `tests/unit/`) ficam fora do escopo do Android.
- O estado do Android citado abaixo é o do `5c14417` (2026-10-15), que já traz as atividades pendentes (`2219208`).

### Commits
- **`9c6e8a6`** (2026-10-15 22:42) — "Bots de IA com ferramentas e base de conhecimento, permissões de uso e cadastro pelo administrador":
  - permissões de uso por pessoa, conferidas no cliente (`auth.pode`) e no servidor;
  - cadastro que o administrador fecha, e a aba Usuários para criar pessoas e bots;
  - bots de IA (aba Bots), ferramentas externas (aba Ferramentas) e base de conhecimento (aba Conhecimento);
  - atividades pendentes até concluir, com o tipo novo "pedido de confirmação";
  - onda e duração do áudio gravadas no envio; player de vídeo próprio; visualizador de Word, Excel e PowerPoint;
  - enviar com um trecho antigo na tela recarrega as recentes; barra de navegação que se oculta no computador.
- Servidor **`ef13c22`**: migrações 044 (`atividade.concluida_em`), 045 (`anexo_transcricao` vira `anexo_audio`, com `duracao` e `intensidade`), 046, 048 e 049 (permissões de uso e `cadastro_aberto`), 047 e 050 (bots), 051 (ferramentas) e 052 (conhecimento). Volumes do Docker com o prefixo `conversa-` (`bin\renomear-volumes.bat`).

### IDs novos (10)
- **CON-16** — conversa com um bot de IA (pílula "Bot" no cabeçalho da direta; o bot responde como um usuário). **Vale para o celular.**
- **ANX-16** — visualizador de arquivos do Office (`.docx`, `.xlsx`, `.pptx`). Só web (não se aplica: o Android abre com outro app).
- **ATV-04** — concluir a atividade à mão ("Marcar como concluída") e a chamada perdida ao abrir. **Vale para o celular.**
- **ATV-05** — reação vista na tela conclui a atividade (`POST /atividades/reacoes-vistas`). **Vale para o celular.**
- **CFG-11** — aba Usuários: cadastrar pessoas e bots. Administração (web).
- **CFG-12** — aba Bots. Administração (web).
- **CFG-13** — aba Ferramentas. Administração (web).
- **CFG-14** — aba Conhecimento. Administração (web).
- **GER-09** — permissões de uso (esconder ou travar o que a pessoa não pode; 403 do servidor). **Vale para o celular.**
- **GER-10** — ocultar a barra de navegação no computador. Só web.

### IDs alterados
- **ATV-01** — ~~`nova`~~ → `pendente`; tipo 5 "pediu que você confirme a leitura" (selo "✓"); texto do vazio novo; bolinha "Pendente".
- **ATV-02** — o contador é o de pendentes (só de conversas de que a pessoa participa); abrir a tela não zera; ~~`POST /atividades/vistas`~~ fora de uso; WS 61 também ao concluir, ao ver reações e ao confirmar.
- **MSG-21 / ENV-24 / ENV-27** — o pedido de confirmação cria a atividade tipo 5 para cada pessoa da lista; confirmar a conclui; "Confirmar leitura" e "Pedir confirmação de leitura" pedem `confirmacao`.
- **AUT-01 / AUT-02** — "Não tem conta? Criar conta" só com o cadastro aberto (`GET /cadastro`); `PUT /usuario` com 403 quando fechado; conta nova com todas as permissões de uso; bot não entra pelo login.
- **AUT-06 / AUT-07 / AUT-08 / AUT-09** — nome, e-mail, senha e avatar por permissão de uso; 32 códigos em `GET /usuario/permissoes`.
- **CON-07 / CON-09 / CON-11** — "Novo grupo" pede `grupo`; pílula "Bot" e botões de chamada por permissão no cabeçalho; `bot` nos contatos.
- **ENV-01 / ENV-21** — permissões no `PUT /mensagem`; arquivos barrados no campo; enviar com trecho antigo recarrega as recentes.
- **ENV-03 / ENV-04** — emoji e figurinha por permissão (seletor só com figurinhas); aba Figurinhas com a mesma altura.
- **ENV-13 / ENV-14 / ENV-17 / ENV-18 / ENV-20 / ENV-22 / ENV-26** — agendar, ocultar (também cancelar a agendada), reagir, responder, encaminhar, votação e postagem por permissão; a reação vira atividade pendente até ser vista.
- **ANX-04 / ANX-05** — `PlayerVideo`: o vídeo toca na bolha; no visualizador, volume até 200% e velocidade; "Baixar video" virou botão do player.
- **ANX-10 / ANX-11 / §1.7** — `duracao` e `intensidade` no `PUT /anexo` e nos conteúdos; onda, duração antes de tocar e "atual / total"; microfone pede `audio`.
- **ANX-13** — `.docx`, `.xlsx` e `.pptx` abrem no visualizador do Office.
- **CHA-01 / CHA-02 / CHA-11 / CHA-16 / CHA-22** — ligar, compartilhar a tela, ligar o vídeo e "Ligar novamente" por permissão; 403 no `PUT /chamada/iniciar` e no `POST /chamada/video`.
- **SIP-01** — aba Voip e ramal pedem `telefonia`; `GET /sip` → `{}` sem ela.
- **CFG-01 / CFG-02 / CFG-07 / CFG-08 / CFG-09 / CFG-10** — abas novas e abas de todos por permissão; tema pede `cores`; Sistema com "Cadastro" e "Modelo de embeddings"; Acessos refeita (linha por pessoa, grupos, próprias travadas); Privacidade travada sem `privacidade`; Cores pede `cores`.
- **GER-08** — `GET /recursos` com `conhecimento`.
- §1.1 (Office), §1.2 (estado), §1.7 (upload de áudio), §1.8, §2.1, §2.2 (barra oculta, Ramal), §2.4 (`PlayerVideo`, `VisualizadorOffice`), §2.5 (abas), §4.1 (rotas 2, 4, 5, 6, 8, 11, 19, 21, 22, 23, 30, 35, 44, 48–54, 56–58, 62, 70 e as novas 71–97), §4.2 (eventos 4 e 61), §6 (textos e regras 19–22), §7 (adaptações novas) e a contagem.

### O que muda para o Android
**IDs novos**
- **CON-16:** ler `bot` no `ContatoDto` (hoje sem o campo) e mostrar a pílula "Bot" (com a descrição "Responde com a IA") ao lado do nome no cabeçalho da conversa direta. O resto já funciona: as respostas do bot, o "digitando" (WS 4 a cada 3 s) e o aviso de falha chegam como mensagens e eventos comuns.
- **ATV-04 / ATV-05:** **já feitos** no `2219208` ("Marcar como concluída", a chamada perdida conclui ao tocar, `POST /atividades/reacoes-vistas` uma vez por quantidade). Conferir no teste manual: reagir pelo web a uma mensagem do Android, abrir a conversa no Android e ver o badge cair.
- **GER-09:** o Android já carrega `GET /usuario/permissoes` (`SistemaRepositorio.minhasPermissoes`), mas só para as telas Sistema e Acessos. Falta:
  - uma função como o `pode` do web (verdadeira até a lista chegar) e esconder pelo código: o menu do toque longo (reações, Responder, Responder no privado, Encaminhar, Pedir confirmação, Ocultar), os itens do "+", o microfone, o botão de emoji e a aba de figurinhas, "Agendar", os botões de ligar e de ligar o vídeo, "Compartilhar tela", "Ligar novamente", "Novo grupo", os campos do perfil, a senha, a privacidade e a qualidade das chamadas;
  - barrar o anexo sem permissão ao entrar na fila, com "Você não tem permissão para enviar <tipos>.";
  - mostrar a mensagem do 403 ("Você não tem permissão para: …") onde o servidor recusar (envio, reação, ocultar, chamada);
  - **pegadinha:** sem `ocultar`, cancelar a agendada também dá 403.
- **ANX-16, CFG-11 a CFG-14, GER-10:** não se aplicam ao celular (§7).

**IDs alterados**
- **ATV-01 / ATV-02 / MSG-21 / ENV-24 / ENV-27:** **já feitos** no `2219208` (`pendente`, tipo 5 com o selo "✓", contador de pendentes sem `POST /atividades/vistas`). O WS 61 que chega ao confirmar já é tratado (o Android relê o contador a cada 61).
- **AUT-01 / AUT-02:** chamar `GET /cadastro` (rota pública, como o login) na tela de login e esconder "Não tem conta? Criar conta" (`LoginTela.kt`) com `aberto: false`; sem resposta, mostrar. No cadastro, o 403 vira a mensagem do servidor.
- **AUT-06 / AUT-07 / AUT-08 / CFG-09:** no perfil e na privacidade, travar ou esconder pelas permissões `nome`, `email`, `avatar`, `senha` e `privacidade` (textos de §6.2) e mandar no `PATCH /usuario` só o que pode mudar.
- **CFG-08:** **Achado:** `feature/config/.../AcessosTela.kt` põe, em cada usuário, **uma caixa por permissão numa `Row` sem rolagem**, e lista todas em "O que cada permissão libera"; o `nomeDaPermissao` só conhece `parametros` e `permissoes`. Com 32 permissões a linha estoura a largura e as de uso aparecem com o código cru (`chamada_audio`). Refazer como o web: uma linha por pessoa que abre, a pílula "Administração", "N de T", os grupos e os nomes curtos de §6.2, as próprias travadas (o servidor recusa com 400). Ler `uso` no `PermissaoDto` (hoje `{codigo, descricao}`).
- **CFG-07:** na tela Sistema, a caixa "Permitir que as pessoas se cadastrem" (`cadastro_aberto`). "Modelo de embeddings" só se a seção de IA for feita (FC-812).
- **CFG-01 / CFG-02 / CFG-06:** esconder "Qualidade das chamadas" sem `config_chamadas` e a escolha do tema sem `cores` (só o cliente confere essas duas).
- **CON-07 / CON-09 / CON-11:** "Novo grupo" só com `grupo`; botões de chamada por permissão; `bot` no DTO (CON-16).
- **ENV-01:** o servidor passa a recusar pelo conteúdo (emoji no texto, imagem, áudio etc.): mostrar o 403 na mensagem que falhou. Sobre rolar ao enviar: no Android a lista vem do Room, onde as recentes continuam depois de ir a uma mensagem antiga; conferir no teste manual que, depois de ir a uma mensagem antiga, enviar leva ao fim com a enviada.
- **ENV-03 / ENV-04:** sem `emoji`, o seletor abre só nas figurinhas; sem `figurinha`, sem a aba; sem os dois, sem o botão.
- **ANX-10 / ANX-11 / §1.7:** ler `duracao` e `intensidade` no `ConteudoDto` (hoje sem os campos) e mostrar a duração antes de tocar (hoje ela só aparece depois que o player carrega o áudio), "atual / total" e a onda; no envio, mandar `duracao` e `intensidade` no `IncluirAnexoRequisicao` (hoje `{identificador, tipo, nome, extensao, tamanho}`) para a gravação e o áudio anexado (§7). Sem isso, o áudio do Android aparece no web com a barra simples.
- **ANX-04 / ANX-05:** opcional: velocidade e "Baixar vídeo" no visualizador (§7). Tocar na bolha não é necessário no celular.
- **ANX-13:** não se aplica (o Android abre o Office com outro app).
- **CHA-01 / CHA-02 / CHA-11 / CHA-16 / CHA-22:** esconder pelos códigos `chamada_audio`, `chamada_video` e `compartilhar_tela`.
  - Ligar: o `GerenciadorChamadas.ligar` já encerra e avisa (`AvisoChamada.Falhou`) quando o `PUT /chamada/iniciar` falha; conferir que o 403 aparece com o texto do servidor.
  - Ligar o vídeo: o `ativarVideo` abre a câmera e só depois anuncia (`tentar { remoto.anunciarVideo(id) }`, falha ignorada, como no web). Sem `chamada_video`, o servidor recusa o anúncio e os outros não ficam sabendo: **o botão precisa sumir** antes.
- **SIP-01:** na etapa 9, `GET /sip` → `{}` sem `telefonia` (o ramal não aparece).
- **GER-08:** `RecursosDto` pode ganhar `conhecimento`, mas o celular não usa; `ia` passa a dizer também se os bots respondem.
- **§4.2:** WS 61 em mais situações e WS 4 do bot: nada a mudar (o Android já trata os dois).

## 16. Atualização de 2026-10-16 (noite) — commit `f351190`

Um commit do web, de 2026-10-16 22:49, que acompanha o do servidor **`ac710eb`** ("Pastas de mensagens, localização e trajeto, espaço usado e ajustes de IA e acessos"; migrações 053 a 056). Há **9 IDs novos** e o total passa de 163 para **172**. Os textos foram conferidos no código do web (`git show f351190` e os testes novos e alterados de `tests/unit/`) e as regras no servidor (`git show ac710eb`: `src/pastas.ts`, `mensagens.ts#localizacaoValida`, `websocket.ts`, `permissoes.ts`, as migrações).
- Eventos WebSocket novos: **66** (`ContatosAtualizados`) e **67** (`PastasAtualizadas`). O push não mudou de formato; a mensagem com localização chega com o texto "localização".
- Serviços de fora chamados direto pelo cliente: Nominatim, OSRM, tiles do OSM e da Esri, links do Google Maps (§4.1).
- Os testes novos do web (`pastas.test.ts`, `localizacao.test.ts` e casos em outros arquivos) ficam fora do escopo do Android; as regras que eles fixam estão nos IDs.
- O estado do Android citado abaixo é o do último commit, **`39f236d`** (2026-10-16). Mudanças ainda sem commit na árvore de trabalho não foram consideradas.

### Commits
- **`f351190`** (2026-10-16 22:49) — "Pastas de mensagens, localização e trajeto, espaço usado e menu das Configurações":
  - pastas de mensagens na conversa direta: painel estreito à direita, seleção arrastando ao lado das mensagens (Ctrl soma, Ctrl + clique marca uma, Esc limpa), "Pôr na pasta", bolinhas atrás do ✓, pasta aberta por cima do chat sem campo;
  - localização e trajeto: "+" › Localização, ponto ou rota de carro (OSRM), busca de endereço, mapa Leaflet na bolha e visualizador com satélite; vai na hora ou como peça no campo;
  - anexos de todas as conversas na página de Anexos; espaço usado no Perfil e na lista de Pessoas; "Usuário desde";
  - Configurações em menus com sub-abas (Conta, Aparência, Dispositivos, Chamadas; Sistema, Pessoas, IA); Pessoas numa tela só; IA › Servidor com servidor de embeddings próprio; ferramenta embutida; destaque na busca do conhecimento;
  - velocidade do áudio; "Gravando áudio..." na lista; contatos recarregados (WS 66 e reconexão); a letra digitada fora do campo não se perde.
- Servidor **`ac710eb`**: migrações 053 (servidor de embeddings próprio: `ia_embeddings_url`, `ia_embeddings_token`), 054 (`usuarios` juntada a `permissoes`), 055 (permissão de uso `localizacao`; conteúdo tipo 9 conferido) e 056 (tabelas `pasta` e `pasta_mensagem`); `GET /usuario/espaco`; `criado_em`, `bot` e `espaco` em `GET /permissoes`; `criado_em` nos contatos; `pastas` em cada mensagem.

### IDs novos (9)
- **AUT-11** — "Usuário desde" (data do cadastro) no Perfil, no perfil de outra pessoa e na lista de Pessoas. **Vale para o celular.**
- **CON-17** — pastas de mensagens na conversa direta: painel, criar, renomear, cor (paleta), compartilhar, excluir; WS 67. **Vale para o celular.**
- **MSG-23** — marcas das pastas na bolha (atrás do ✓ na enviada; ao lado, clicáveis, na recebida). **Vale para o celular.**
- **MSG-24** — pasta aberta por cima do chat, sem campo ("Ver na conversa", "Tirar da pasta", paginação de 50, Esc). **Vale para o celular.**
- **MSG-25** — localização na conversa: bolha com mapa, visualizador com satélite, "Abrir no Google Maps", fórmulas de distância e tempo. **Vale para o celular.**
- **ENV-29** — selecionar mensagens e pôr na pasta (arraste, Ctrl, Esc, barra, "Pôr na pasta"). **Vale para o celular**, com outro gesto: o web não seleciona por toque (§7).
- **ENV-30** — enviar localização ou trajeto ("+" › Localização; Nominatim, posição do aparelho, OSRM; "Enviar" ou "Inserir na mensagem"; formato do tipo 9). **Vale para o celular.**
- **ANX-17** — espaço usado pelos arquivos enviados (`GET /usuario/espaco`). **Vale para o celular** (no Perfil; a coluna da lista de Pessoas é administração).
- **ANX-18** — velocidade do áudio (1x, 1,5x, 2x, 3x, a mesma para todos). **Vale para o celular.**

### IDs alterados
- **AUT-02** — cadastrar contas pede `permissoes` (Pessoas › Usuários). **AUT-05 / AUT-06** — Conta › Perfil; seção "Armazenamento" e "Usuário desde"; o botão de tema saiu. **AUT-09** — 32 códigos: 5 de administração (sai `usuarios`) e 27 de uso (entra `localizacao`). **AUT-10** — campo "Usuário desde".
- **CON-01 / ENV-16** — "Gravando áudio..." (pontinhos vermelhos) também na lista. **CON-09** — botão "Pastas de mensagens" (só na direta). **CON-11** — `criado_em` nos contatos; relidos no WS 66 e a cada reconexão.
- **MSG-07** — classe nova `Localizacao`. **MSG-08** — marcas das pastas atrás do ✓.
- **ENV-12** — a letra digitada fora do campo entra nele no mesmo `keydown`. **ENV-20** — na pasta aberta, "Ver na conversa" e "Tirar da pasta", sem "Pedir confirmação" nem "Ocultar". **ENV-21** (§10) — peça `localizacao`; "Localização" no "+"; campo escondido com a pasta aberta.
- **ANX-10** — botão de velocidade (ANX-18). **ANX-13** — abre com os anexos de todas as conversas, com o nome da conversa em cada item.
- **CHA-18** — "[Localização]" no painel do chat da chamada. **NOT-02** — corpo "Localização". **ATV-01** — prévia "Localização".
- **CFG-01** — menus com sub-abas em duas seções; `/config/acessos` → `usuarios`; `/config/ia`. **CFG-02** — "Claro" / "Escuro" em Aparência › Tema. **CFG-05** — texto "troque entre claro e escuro acima". **CFG-07** — Sistema (geral) e IA › Servidor; servidor de embeddings próprio, com token e "Testar embeddings"; texto do Cadastro. **CFG-08** — dentro de Pessoas › Usuários; "Pessoas" no lugar de "Acessos"; selo "Bot", colunas de espaço, data e "N de T". **CFG-10** — fica em Aparência › Tema, com os botões claro/escuro. **CFG-11** — Pessoas › Usuários, uma tela só, com a permissão `permissoes`; seção "Acessos" no "Novo usuário"; `permissoes` no `PUT /usuario/novo`. **CFG-12** — IA › Bots. **CFG-13** — IA › Ferramentas; a embutida `consultar_conhecimento` só para ver; texto da lista vazia. **CFG-14** — IA › Conhecimento; palavras da pergunta destacadas na busca.
- **GER-08** — `conhecimento` com o servidor de embeddings próprio ou o da IA. **GER-09** — `usuarios` saiu; `permissoes` = "Pessoas"; código de uso novo `localizacao`.
- §1.1 (mapas), §1.2 (store `pastas`, velocidade), §1.4 (contatos na reconexão), §1.8 (chunk `leaflet`), §2.1 (`ia`, `acessos`), §2.3 (painel e pasta aberta), §2.4 (componentes novos), §2.5 (tabela dos menus), §4.1 (rotas 6, 8, 19, 21, 34, 54, 57, 58, 62 e 72, as novas 98 a 107 e os serviços externos), §4.2 (eventos 66 e 67), §6 (textos e regras 23 a 27), §7 (adaptações novas) e a contagem.

### O que muda para o Android
**IDs novos**
- **CON-17 / ENV-29 / MSG-23 / MSG-24 (pastas):** nada disso existe no Android. Fazer:
  - as rotas 101 a 107 no `ConversaApi`; o `DELETE /pasta/mensagens` vai **com corpo** (no Retrofit, `@HTTP(method = "DELETE", path = "pasta/mensagens", hasBody = true)`);
  - `pastas` (lista de ids, vazia quando a chave não vem) no `MensagemDto` — hoje o `ignoreUnknownKeys` a descarta — e no Room, para as marcas;
  - os eventos 66 e 67 no `TipoSocket` / `EventoSocketParser` (hoje caem em `Desconhecido`); no 67, `mensagens` é lista de números;
  - a entrada "Pastas" no menu do cabeçalho da conversa **direta** (onde já fica "Resumir conversa"), com a folha da lista, "Nova pasta", as opções e a paleta exata do CON-17;
  - **seleção por toque:** o web não tem gesto de toque (§7). Proposta: "Selecionar" no menu do toque longo; os toques seguintes marcam ou desmarcam; barra com "N selecionadas", "Pôr na pasta" e ×;
  - as marcas atrás do ✓ (enviada) e ao lado da bolha (recebida, o toque abre a pasta);
  - a pasta aberta numa tela sem campo, paginada de 50 em 50 (`antes` = id da mais antiga), com "Ver na conversa" e "Tirar da pasta" no toque longo, e a seleção para tirar várias.
- **ENV-30 / MSG-25 (localização):** o `TipoConteudo` do Android não tem o 9, e hoje a mensagem de localização aparece como **"Conteúdo que esta versão ainda não mostra"** (`Bolhas.kt`, `DESCONHECIDO`). Fazer:
  - `LOCALIZACAO(9)` e o leitor do JSON (`pontos`, `rota`, `distancia`, `duracao`), com as fórmulas de MSG-25 (distância, tempo, descrição, link) e o decodificador da polyline, todos com teste unitário (os casos de `localizacao.test.ts` servem de exemplo); a regex das coordenadas entra também no `RegexNoAndroidTest`;
  - bolha: mapa sem gestos com os marcadores A/B e a linha, o nome (ou A e B com a descrição) e "Abrir no Google Maps" por Intent; o toque abre o mapa grande, com satélite;
  - envio: "Localização" no "+" (só com `localizacao`), janela Local/Trajeto, posição do aparelho (permissão de localização), busca no Nominatim e rota no OSRM com **User-Agent do app**, coordenadas digitadas, toque no mapa; "Enviar" na hora e "Inserir na mensagem" como peça na faixa de anexos (FC-416);
  - biblioteca de mapa: dependência nova, a decidir (osmdroid ou MapLibre, §7); os tiles do OSM também pedem User-Agent;
  - "Localização" nas prévias das atividades (`Atividades.kt#TIPOS_COM_NOME` não tem o 9) e no resumo da citação.
- **ANX-17:** `GET /usuario/espaco` (rota nova) e a seção "Armazenamento" no Perfil, com os textos e o formato de `formatarTamanho` (ponto decimal).
- **ANX-18:** o `PlayerAudio` (Media3) não tem velocidade: `setPlaybackSpeed` com 1x, 1,5x, 2x e 3x, a mesma para todos os áudios, guardada no DataStore, e o botão "Velocidade" no player.
- **AUT-11:** `criado_em` no `ContatoDto` (hoje sem o campo) e no Room; "Usuário desde dd/mm/aaaa" no Perfil. O perfil de outra pessoa (AUT-10) o Android ainda não tem; quando tiver, com o campo "Usuário desde".

**IDs alterados**
- **CON-11 / §1.4 / §4.2:** tratar o WS 66 relendo `GET /usuario/contatos` (`ContatosRepositorio.atualizar`) e reler também a cada **reconexão** do WebSocket: o `SyncManager` relê mensagens, conversas, presença, atividades e chamadas, mas não os contatos.
- **CON-01 / ENV-16:** o `ConversasViewModel` combina só `presenca.digitando`, e o `PresencaRepositorio` já tem `gravando`. Mostrar na lista os três pontinhos vermelhos ("Gravando áudio...", como `contentDescription`), com prioridade sobre o digitando.
- **GER-09 / AUT-09 / CFG-08:**
  - tirar `CodigoPermissao.USUARIOS` (`core/model/.../Sistema.kt`) e o nome "Usuários" (`acessos_permissao_usuarios`, em `AcessosTela.kt#nomeDaPermissao`);
  - `permissoes` passa a se chamar **"Pessoas"** (a string `config_acessos`, "Acessos", vira "Pessoas");
  - acrescentar `LOCALIZACAO = "localizacao"` ("Localização e trajeto", grupo Enviar, depois de "Arquivos");
  - na tela Acessos, opcional: o selo "Bot", o espaço e a data (`UsuarioAcessos` só tem `id, nome, login, permissoes`) e o texto novo do modo aberto ("Ninguém tem a permissão Pessoas ainda: …").
- **MSG-07 / ENV-21 / ENV-20:** classe nova de bolha para a localização (depois de Figurinha); peça de localização no campo; o menu do toque longo da pasta aberta (MSG-24).
- **ANX-13:** opcional. O Android só tem os anexos de uma conversa (`AnexosDaConversaTela`); com `conversa=0`, a mesma rota devolve os de todas.
- **NOT-02 / ATV-01 / CHA-18:** "Localização" onde o Android escreve o nome do tipo de conteúdo. A notificação em segundo plano já vem do servidor com "localização".
- **CFG-01 / CFG-02 / CFG-05 / CFG-10:** não se aplicam (o Android tem a sua lista de configurações e o tema em Aparência, com as cores do FMX).
- **CFG-07:** o servidor de embeddings faz parte da seção de IA do Sistema (FC-812), só se ela for feita; o texto do Cadastro, se o Android o mostrar, segue o do web.
- **CFG-11 / CFG-12 / CFG-13 / CFG-14 / GER-08:** fora do escopo do celular (administração); o `recursos.conhecimento` o celular não usa.
- **ENV-12:** não se aplica (teclado virtual).
- **AUT-02 / AUT-05 / AUT-06 / AUT-10 / MSG-08:** o que vale no Android está em AUT-11, ANX-17 e MSG-23.

**Pontos de atenção**
- **Seleção por toque não existe no web:** o Android precisa do seu gesto (toque longo › "Selecionar"), sem tirar o menu de ações do toque longo (ENV-20).
- **`DELETE` com corpo:** o `DELETE /pasta/mensagens` leva `{pasta_id, mensagens}` no corpo; os outros `DELETE` usam a consulta.
- **User-Agent num app nativo:** o navegador manda o dele (e o Referer) para o Nominatim, o OSRM e os tiles do OSM; o app precisa mandar um próprio, que identifique o Conversa, ou corre o risco de ser bloqueado. Uso leve: busca só quando a pessoa pede, rota uma vez por trajeto, tiles em cache.
- **`pastas` é opcional:** sem a chave, a mensagem não está em nenhuma pasta; a lista vem só com as pastas que a pessoa enxerga, e a pasta que some (WS 67) precisa sair das marcas.
- **Texto inconsistente no web:** em Sistema › Cadastro, o web ainda diz "só quem tem a permissão Usuários cria contas, em Pessoas › Usuários", mas a permissão agora é `permissoes` ("Pessoas"). Se o Android mostrar esse texto, usar "a permissão Pessoas".
