# 03 — Inventário de funcionalidades e comportamento do cliente web (conversa-web)

> Auditoria de 2026-10-06. Fonte: `C:\Users\danie\Desktop\GIT\conversa-projeto\conversa-web` no commit `bfb79d8` (2026-10-06, "Abas Sistema e Acessos nas configurações; Equipes sai da barra"), **atualizado com `39d06f9`** (2026-10-06 21:22, "Campo de mensagem rico, votação em grupo e chat completo na chamada"). Itens novos ou alterados por esse commit estão marcados com 🆕; o resumo está na §10. **Atualizado de novo em 2026-10-08** com `eaa8bac` e `785bdef` (2026-10-07 20:30 e 20:41): as marcações citam o commit (🆕 `eaa8bac`) e o resumo está na §11. Foram lidos `CLAUDE.md`, `README.md`, todo o `src/`, `chat-popup.html`, `public/firebase-messaging-sw.js`, `package.json`, `vite.config.ts`, os nomes dos testes em `tests/unit/` (589 casos) e o `git show --stat` de todos os commits desde 2026-09-12.
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
| `auth` (`stores/auth.ts`) | token, usuário, `dispositivoId`, URL do avatar resolvida, permissões do sistema (`permissoes`, `temPermissao`) | `localStorage`: `conversa.token`, `conversa.user` (JSON), `conversa.deviceId`, `conversa.apiBase` |
| `chat` (`stores/chat.ts`) | contatos, conversas, mensagens por conversa, conversa ativa, resposta pendente, digitando/gravando, online, WebSocket, polling, cursor de sincronização, buscas | memória (não persiste mensagens) |
| `call` (`stores/call.ts`) | estado da chamada (`inativo`/`chamando`/`recebendo`/`ativa`/`encerrando`), peers WHIP/WHEP, mídia local, tela, ponteiros, chat da chamada | memória |
| `sip` (`stores/sip.ts`) | config do ramal, UserAgent/Registerer sip.js, sessão, tons | memória |
| `atividades` (`stores/atividades.ts`) | lista paginada, contador de novas, tela aberta | memória |

Estados globais fora do Pinia (composables singleton): tema (`useTheme`, chave `theme` = `dark`/`light`), cores personalizadas (`useCoresPersonalizadas`, chave própria no localStorage, por tema), qualidade das chamadas (`useConfigChamada`, chave `conversa.chamada`), uploads ativos (`useUploadProgress`), áudio tocando (`useAudioManager`), relógio compartilhado (`useAgora`), conexão lenta (`useConexao`).

### 1.3 Como fala com a API (Eden)

- `api()` (`services/eden.ts`) devolve o cliente das rotas `/api` no endereço `getApiBase()` = `localStorage['conversa.apiBase']` ou `window.location.origin`. O cliente é recriado se a base muda. (Não há mais campo na tela de login para trocar a base, só `auth.setApiBase`, sem uso na UI.)
- Header `authorization: Bearer <token>` lido do `localStorage` a cada requisição.
- `dados(chamada)` devolve `data` ou lança:
  - **401** → `limparToken()` (remove `conversa.token` do localStorage) e lança `ErroNaoAutenticado`.
  - Corpo HTML (página de erro do nginx 502/504) → mensagem `"Servidor indisponível"`.
  - JSON `{ error }` ou `{ message }` → a string; senão `Erro HTTP <status>`.
- **Datas**: o JSON é lido com reviver que só converte para `Date` as chaves em `CAMPOS_DATA` (`inserida, alterada, visivel_em, criado_em, atualizado_em, ultima_mensagem, arquivada_em, iniciada, finalizada, adicionado_em, entrou_em, saiu_em, recusou_em, recebida, visualizada, reproduzida, excluida_em, reagido_em, online_em, ate, vistas_em`) e só se o valor for ISO-8601 com fuso. Texto de mensagem nunca vira data. **Atenção Android**: `recebida`/`visualizada`/`reproduzida` são booleanos na lista de mensagens, mas datas (ou null) no detalhe de status — o mesmo nome de campo tem tipos diferentes por rota.
- Verbos: `GET` consulta, `PUT` cria, `PATCH` altera, `POST` ações, `DELETE` remove.

### 1.4 WebSocket

Arquivo: `src/stores/chat.ts` (`conectarWebSocket`, `tratarEventoSocket`).

- URL: `${wss|ws}://${window.location.host}${VITE_WS_PATH || '/ws/'}` — **usa o host da página, não `conversa.apiBase`**. No Android: `wss://<host-da-api>/ws/`.
- `onopen`:
  1. `conectadoTempoReal = true`, zera tentativas.
  2. Envia `{"tipo":1,"token":"<token>"}` (autenticação).
  3. Recarrega quem está online (`GET /contatos/online`) — quem saiu durante a queda não fica "online".
  4. Atualiza o contador de atividades (`GET /atividades/novas`).
  5. Se o handler de chamada já está registrado, verifica chamadas pendentes (`GET /chamadas/pendentes`).
- `onclose` → agenda reconexão com espera `min(30000, 1000 * 2^tentativas)` ms (1 s, 2 s, 4 s, … até 30 s).
- **Polling de fallback**: a cada 8 s, **só se o socket estiver desconectado**, executa o mesmo fluxo de "nova mensagem" (`GET /mensagens/novas`) e verifica chamadas pendentes.
- **Aviso visual**: se o socket fica desconectado por mais de 5 s com o usuário logado, aparece a faixa vermelha no topo: `Conexao em tempo real indisponivel — usando atualizacao periodica` (`App.vue`).
- Envio: `{tipo:1, token}` (login) e `{tipo:57, chamada_id, dados}` (sinal de chamada; descartado se o socket não estiver `OPEN`).
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
3. `PUT /anexo {identificador, tipo, nome, extensao, tamanho}` → `{id, upload_url}`.
4. `PUT upload_url` (XHR, corpo = arquivo) com progresso.
5. A mensagem leva no conteúdo o **identificador** (não o id).

Para exibir: `GET /anexo?identificador=` → `{url}` (pré-assinada; validade lida de `X-Amz-Date` + `X-Amz-Expires`; renovada 60 s antes de vencer; URL sem assinatura vale até falhar; erro de carga força renovação, no máximo 1 vez por minuto por anexo) — `composables/useAttachments.ts`.

### 1.8 Carregamento sob demanda

Chunks manuais no Vite: `sipjs`, `framework`, `highlightjs`, `markdown`, `lottie`, `vendor-utils` (hash-wasm), `codemirror`. Componentes assíncronos: `SipDialerModal`, `SipIncomingCallModal`, `CodigoModal`, `AgendarMensagemModal`, `VisualizadorPdf`, `VisualizadorHtml`. Irrelevante para o Android, mas indica o que é "pesado" e opcional.

---

## 2. Mapa de telas, rotas, painéis e navegação

### 2.1 Rotas (URL) — `composables/useHistoryNavigation.ts`

Não há vue-router. A URL é sincronizada manualmente com `history.pushState/replaceState` e o `popstate` restaura o estado.

| URL | Seção | Observação |
|---|---|---|
| `/` ou `/chat` | Chat sem conversa aberta | painel lateral aberto |
| `/chat/:conversaId` | Chat com conversa aberta | deep link aplicado após `iniciarSessao` |
| `/anexos`, `/anexos/:conversaId` | Página de anexos | |
| `/config/:aba` | Configurações | `aba` ∈ `usuario, dispositivos, permissoes, voip, cores, chamadas, sistema, acessos` (inválida → `usuario`) |
| `/chamadas` | Histórico de chamadas | |
| `/atividades` | Atividades | |
| `/ramal` | (válida no parser; o item da barra abre o discador em vez de navegar) | |
| `/chat-popup.html?conversa=:id` | Janela separada de uma conversa | entrada própria |

- O `history.state` guarda também a **âncora de rolagem** `{mensagemId, offset}` (atualizada por `replaceState` 200 ms após parar de rolar). Voltar/avançar restaura seção, conversa, aba e posição. Em **reload** a âncora é descartada (abre no fim ou na primeira não lida).
- Mudar só a âncora usa `replaceState`; mudar seção/conversa/aba usa `pushState`.

### 2.2 Barra de navegação (`components/NavBar.vue`)

Desktop: barra vertical à esquerda. Celular (< 768 px): barra horizontal no rodapé (50 px).

| Ordem | Item | Ação | Indicadores |
|---|---|---|---|
| 1 | Avatar do usuário (ou inicial) | seção `config` | tooltip "Configurações"; se a imagem falha, re-resolve a URL |
| 2 | Chat | seção `chat` | — |
| 3 | Atividades | seção `atividades` | badge com `atividades.novas` (`99+` acima de 99) |
| 4 | Chamadas | seção `chamadas` | — |
| 5 | Anexos | seção `anexos` | — |
| 6 | Ramal | **abre o discador SIP** (não muda de seção) | só aparece se `sip.sipDisponivel` (config ativa); bolinha verde = registrado, amarela = conectando, vermelha = erro |

"Equipes" foi **removido** da barra em `bfb79d8`. Qualquer outra seção cai na tela "Em desenvolvimento".

### 2.3 Layout da seção Chat (`App.vue`)

```
[NavBar] [ChatSidebar (lista)] [main: CallBar? · ChatHeader · UploadIndicador · MessageList · MessageInput]
```

- No celular só uma coluna aparece: lista **ou** conversa (`sidebarAberta`); botão de voltar no cabeçalho reabre a lista; tela vazia mostra "Selecione uma conversa." e (celular) botão "Ver conversas".
- Chamada de **vídeo** ativa e não minimizada cobre o app inteiro (`CallWindow` em `fixed inset-0`). Minimizada no desktop → janela flutuante arrastável/redimensionável; no celular → só a `CallBar` no topo, app utilizável.
- Chamada de **áudio** → só a `CallBar` (com controles) no topo da área do chat.

### 2.4 Painéis, modais e sobreposições

| Componente | Abre por | Conteúdo |
|---|---|---|
| `PesquisaAvancada` | lupa "Pesquisar em todos os chats" na lista | substitui a lista; pesquisa global |
| Menu da conversa (em `ChatSidebar`) | clique direito na conversa ou botão "Mais opções" | Fixar / Desafixar / Arquivar / Desarquivar |
| `UserInfoModal` | avatar na lista ou no cabeçalho (só conversa direta) | foto, nome, Email, Telefone ("Nao informado"), botão "Ver anexos" |
| `CreateGroupModal` | botão "Novo grupo" na lista | nome + seleção de contatos |
| `GroupMembersModal` | botão "Gerenciar membros" no cabeçalho (grupo) | renomear, remover, adicionar |
| `ForwardMessageModal` | ação Encaminhar | destinos (conversas e contatos) |
| `DetalheStatusMensagem` | hover 300 ms / clique no ✓ da própria mensagem | etapas de entrega |
| `MensagemAcoes` (menu + `EmojiPicker`) | botão ⋯ ao passar o mouse ou clique direito na bolha | reações rápidas + ações |
| `DialogoConfirmacao` | Ocultar / cancelar agendada / erros de download | diálogo do app |
| `AnexoPopup` | botão "Anexar" | Arquivo / Código |
| `EmojiPicker` (com abas Emojis/Figurinhas) | botão "Emoji" | |
| `CodigoModal` | Anexar → Código, ou colar texto > 10 linhas | editor CodeMirror |
| `AgendarMensagemModal` | botão relógio "Agendar mensagem" (aparece com conteúdo) | data + hora |
| `MencaoDropdown` | digitar `@` | até 6 contatos |
| `BarraGravacao` | microfone | gravação de áudio |
| `ImageViewerModal` | clique em imagem/vídeo (chat, fila, anexos) | visualizador com galeria |
| `VisualizadorPdf`, `VisualizadorHtml` | botão "Abrir" em anexo PDF/HTML | |
| `IncomingCallModal` | evento 51 | atender / recusar / atender só assistindo |
| `VideoUpgradeModal` | evento 56 | Apenas assistir / Transmitir também |
| `AddUserToCallModal` (App) e modal interno do `CallWindow` | botão "Adicionar usuário" | contatos fora da chamada |
| `ChatChamada` | botão "Chat da chamada" no `CallWindow` | painel lateral (celular: por cima) |
| `SipDialerModal` | item Ramal da barra, ou chamada SIP recebida sendo conectada | discador flutuante arrastável |
| `SipIncomingCallModal` | INVITE SIP | atender / recusar |
| Overlay de arrastar arquivo | arrastar arquivos sobre a conversa | "Solte para enviar o arquivo" |
| Toast de erro | qualquer erro tratado no `App.vue` | topo, botão × |

### 2.5 Configurações (`components/ProfileSettingsModal.vue`, modo `inline`)

Título "Configuracoes" / "Preferencias da conta". Lista de abas à esquerda (celular: menu → sub-tela com botão voltar).

| Aba (id) | Título | Descrição exibida | Visibilidade |
|---|---|---|---|
| `usuario` | Usuario | Nome, email, avatar e senha | todos |
| `dispositivos` | Dispositivos | Sessao atual e perifericos locais | todos |
| `permissoes` | Permissoes | Notificacoes, microfone e camera | todos |
| `voip` | Voip | Configuracao SIP do usuario | todos |
| `cores` | Cores | Cores do sistema, salvas neste navegador | todos |
| `chamadas` | Chamadas | Qualidade do áudio, vídeo e tela | todos |
| `sistema` | Sistema | Configurações que valem para todos | só com permissão `parametros` |
| `acessos` | Acessos | Quem pode mexer no sistema e nas permissões | só com permissão `permissoes` |

Regra: link direto para aba restrita sem a permissão volta para `usuario` **depois** de as permissões carregarem. Fechar as configurações volta ao chat com a lista aberta e força reconexão do SIP (`sip.inicializarSessao(true)`).

---

## 3. Lista de funcionalidades (checklist de paridade)

Legenda dos campos: **Comportamento** (o que o usuário vê/faz), **API/WS** (rotas e eventos com campos), **Arquivos**, **Regras e casos de borda**.

### 3.1 AUT — Autenticação, sessão, perfil

**AUT-01 Login**
- Comportamento: tela com logo "Conversa", campos "Usuário" e "Senha", botão "Entrar" ("Entrando..."), link "Não tem conta? Criar conta". Foco inicial no usuário. Após sucesso pede permissão de notificação se ainda `default`.
- API: `POST /login {login, senha, dispositivo_id?}` → `{id, nome, email, telefone, avatar_url, avatar_identificador, token, dispositivo:{id,...}}` (sem o campo `login`; o cliente guarda o que foi digitado, `trim()`).
- Arquivos: `components/LoginForm.vue`, `stores/auth.ts#login`.
- Regras: `dispositivo_id` salvo é reenviado para reaproveitar o dispositivo; resposta sem token → erro "Resposta de login invalida: token ausente".

**AUT-02 Cadastro**
- Comportamento: "Crie sua conta" com Nome, Usuário, E-mail, Senha (`minlength=4` no HTML), botão "Cadastrar"/"Cadastrando..."; sucesso mostra "Conta criada com sucesso!" e volta ao login após 1,5 s. Link "Já tem conta? Entrar".
- API: `PUT /usuario {nome, login, email, senha}`.
- Arquivos: `components/RegisterForm.vue`.

**AUT-03 Início de sessão resiliente** — ver §1.5 (401 → login; outro erro → mantém login e tenta a cada 5 s com toast "(tentando de novo...)"). Testes: `tests/unit/App.test.ts`.

**AUT-04 Registro de dispositivo e token de push**
- API: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma}` e `PATCH /dispositivo {id, token_fcm}`.
- Arquivos: `stores/auth.ts#detectarNavegador/registrarTokenFCM`.

**AUT-05 Logout** — botão "Sair" (ícone) na aba Usuário. Local apenas (§1.5).

**AUT-06 Editar dados do perfil**
- Comportamento: seção "Dados do usuario" — Nome, Email, botão "Salvar dados"; mensagens "Preencha nome e email." / "Dados atualizados com sucesso.".
- API: `PATCH /usuario {id, nome, email}` (o tipo aceita também `telefone`, mas a tela não edita telefone).
- Arquivos: `ProfileSettingsModal.vue#salvarPerfil`.

**AUT-07 Alterar senha**
- Comportamento: "Senha atual", "Nova senha", "Confirmar nova senha", botão "Salvar senha".
- Validações no cliente: todos preenchidos ("Preencha todos os campos de senha."), nova ≥ 6 ("A nova senha deve ter pelo menos 6 caracteres."), confirmação igual ("A confirmacao da senha nao confere."). Sucesso: "Senha alterada com sucesso." e limpa os campos.
- API: `POST /alterar-senha {senha_atual, senha}` (o servidor confere a atual).

**AUT-08 Avatar (foto de perfil)**
- Comportamento: "Foto de perfil" — "Alterar foto" ("Enviando...") / "Remover"; prévia local + % de progresso sobre o avatar.
- Processamento: recorte quadrado central, 256×256, JPEG qualidade 0,85 (`utils/imageResize.ts`), nome `avatar.jpg`.
- API: upload (§1.7, tipo 2) → `PATCH /usuario {id, avatar_anexo_id}` → `GET /anexo?identificador=` para a URL. Remover: `PATCH /usuario {id, avatar_anexo_id: null}`.
- Regra: URL do avatar expira; ao falhar a `<img>` chama `auth.renovarAvatarExpirado(url)` (renova no máximo a cada 30 s, só se a URL que falhou for a atual).

**AUT-09 Permissões do sistema do usuário logado**
- API: `GET /usuario/permissoes` → `string[]` (códigos `parametros`, `permissoes`). Só controla visibilidade das abas Sistema/Acessos; o servidor confere em cada rota.

**AUT-10 Ver perfil de outro usuário** (`UserInfoModal.vue`)
- Abre ao clicar no avatar da conversa **direta** (lista ou cabeçalho). Mostra foto/inicial, nome, Email, Telefone ("Nao informado" se vazio) e botão **"Ver anexos"** que leva a `/anexos/:conversaId`.
- Dados vêm de `GET /usuario/contatos` (fallback: dados da própria conversa).

### 3.2 CON — Conversas

**CON-01 Lista de conversas** (`components/ChatSidebar.vue`)
- API: `GET /conversas` → `Conversa[]` (`id, descricao, tipo, nome, destinatario_id, mensagem_id, ultima_mensagem, ultima_mensagem_texto, mensagens_sem_visualizar, avatar_url, fixada_ordem, arquivada_em`).
- Item: avatar (ou inicial — primeira letra, `Array.from` para não quebrar emoji), bolinha verde se o destinatário da direta está online, título (`descricao || nome || "Conversa #id"`), etiqueta "Grupo", ícone de alfinete se fixada, contador de não lidas (oculto em arquivadas), prévia `resumirTexto(ultima_mensagem_texto)` ou "Sem mensagens", três pontinhos animados se alguém digita, botões "Mais opções" e "Abrir em nova janela" (hover).
- Ordem: fixadas primeiro por `fixada_ordem`; depois por `mensagem_id` desc. Arquivadas numa seção recolhível no fim: botão "Arquivadas (N)".
- Prévia: menção `@[Nome](id)` vira `@Nome`; cada bloco de código vira `Código (linguagem)`; mensagem oculta aparece como o servidor mandar em `ultima_mensagem_texto` (o web recarrega a lista após ocultar para mostrar "Mensagem oculta").
- Recarregada em: nova mensagem, status de mensagem (WS 3), conversa atualizada (WS 40), envio de mensagem, leitura.

**CON-02 Filtro da lista e "Nova conversa"**
- Campo "Pesquisar..." filtra localmente por título e pela prévia. Com termo, as arquivadas aparecem junto com a etiqueta "Arquivada" e não há arraste.
- Abaixo, seção "Nova conversa" com contatos que **não** têm conversa direta, filtrados por nome/login/email; clicar cria/abre a conversa direta.

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

**CON-08 Gerenciar membros do grupo** (`GroupMembersModal.vue`) — 🆕 `eaa8bac`: o modal virou o painel **"Dados do grupo"** (`PainelGrupo.vue`), aberto pelo avatar do grupo no cabeçalho ou pelo botão "Participantes e anexos", com os anexos da conversa embaixo (§11)
- "Membros do grupo": campo "Nome do grupo" + "Renomear" (habilitado só se mudou; "Salvando..."); lista de membros com "Remover" (não aparece para si mesmo; "Removendo..."); "Adicionar participante" com select "Selecionar usuario" (contatos fora do grupo) + "Adicionar" ("Adicionando..."). Mensagens: "Grupo renomeado com sucesso.", "Participante adicionado com sucesso.", "Participante removido com sucesso.".
- API: `GET /conversa/usuarios?conversa=` → `[{id (conversa_usuario_id), usuario_id, nome, avatar_url}]`; `PATCH /conversa {id, descricao}`; `PUT /conversa/usuario {conversa_id, usuario_id}`; `DELETE /conversa/usuario?id=<conversa_usuario_id>`.
- Não há regra de administrador no cliente: qualquer membro pode renomear/adicionar/remover (o servidor pode recusar).

**CON-09 Cabeçalho da conversa** (`ChatHeader.vue`)
- Avatar (clicável na direta → AUT-10), bolinha online, título; subtítulo: em grupo, nomes dos membros separados por vírgula (some quando alguém digita/grava) e uma faixa de "chips" de membros (foto + nome) que, clicados, abrem a conversa direta com o membro.
- Indicador animado de digitando/gravando (ver MSG-15).
- Botões (escondidos durante chamada ou chamada recebida): "Chamada de voz", "Chamada de video", "Compartilhar tela"; em grupo "Gerenciar membros"; pesquisa na conversa (desktop: campo inline; celular: botão que abre painel "Pesquisar nesta conversa").

**CON-10 Abrir conversa em nova janela (popup)**
- Botão "Abrir em nova janela" em cada item da lista → `window.open('/chat-popup.html?conversa=<id>', 'chat-popup-<id>', 900×700 centralizada)`.
- O popup (`ChatPopup.vue`) monta Pinia própria, inicializa o chat inteiro (WS próprio), mostra cabeçalho (sem botão de voltar), lista, campo, visualizador de imagens e encaminhamento; título da janela = nome da conversa. Sem sessão: "Sessao expirada. Faca login novamente."; id inválido: "Conversa invalida.".

**CON-11 Contatos**
- API: `GET /usuario/contatos` → `Contato[]` (`id, nome, login, email, telefone, avatar_url?`). Regra de avatar usada em várias telas: a lista de contatos pode não trazer foto → usa o `avatar_url` da conversa direta com o contato; o próprio usuário usa o avatar do perfil.

**CON-12 Deep link, histórico e âncora** — §2.1. Clique em notificação abre a conversa e **recarrega as mensagens** antes (as em memória não têm as que chegaram fechada).

**CON-13 Conversa atualizada em tempo real** — WS tipo 40 → `GET /conversas`.

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
  5. O auto-scroll não empurra a linha para fora da tela enquanto houver não lidas.
- Ao abrir conversa com não lidas: posiciona a primeira não lida no topo (margem 40 px); sem não lidas: rola ao fim e "gruda" no fim enquanto o conteúdo cresce (imagens, áudio, código).

**MSG-04 Marcar como visualizada**
- Só com a janela focada. A cada rolagem/foco, cada mensagem de outro, ainda não visualizada, **totalmente visível** (ou com o fim visível) é marcada.
- API: `POST /mensagem/visualizar {conversa, mensagem}` — **uma chamada por mensagem**. Sucesso marca `visualizada=recebida=true`, desconta do contador da conversa (otimista); ao chegar a 0 fecha a notificação dessa conversa; recarrega `GET /conversas`. Falha não desconta.

**MSG-05 Botões de rolagem**
- Botão "Ir para o final" quando a > 1000 px do fim (se houve salto para mensagem antiga, recarrega as 80 últimas e vai ao fim sem animação).
- Botão flutuante "Há novas mensagens" quando chega mensagem e o usuário está longe do fim.

**MSG-06 Ir para uma mensagem**
- Se não está carregada: `GET /mensagens?conversa&mensagemreferencia=<id>&mensagensprevias=30&mensagensseguintes=30` (se não vier, tenta 120/120); **substitui** a lista e ativa paginação nos dois sentidos. Centraliza a mensagem e a destaca com anel amarelo por 1,2 s.
- Usado por: resultado de pesquisa, citação de resposta, atividade, "Abrir mensagem" nos anexos. Encaminhada de outra conversa abre a conversa de origem **se o usuário participa dela**.
- Erro: "Nao foi possivel localizar esta mensagem no contexto da conversa.".

**MSG-07 Classificação das bolhas** (`utils/classificarMensagem.ts`, por prioridade)

| Tipo | Componente | Condição |
|---|---|---|
| Excluida | `BolhaExcluida` | `excluida_em` preenchido (antes de tudo) |
| Chamada | `BolhaChamada` | conteúdo único tipo 6 |
| Imagem | `BolhaImagem` | conteúdo único tipo 2, sem referência |
| Enquete 🆕 | `BolhaEnquete` | conteúdo único tipo 8 (**com ou sem** referência; vem antes de Figurinha) |
| Figurinha | `BolhaFigurinha` | conteúdo único tipo 7, sem referência |
| Codigo | `BolhaCodigo` | texto único só com blocos de código, sem referência |
| Emoji | `BolhaEmoji` | texto único só com emojis, sem referência (fonte grande) |
| ComReferencia | `BolhaReferencia` | tem `mensagem_referencia.mensagem` |
| TextoCurto | `BolhaTextoCurto` | texto único, uma linha, ≤ 60 caracteres |
| Padrao | `BolhaPadrao` | demais |

Hora (`HH:MM`) dentro da bolha (`MensagemStatus.vue`), posição conforme variante (sobre a imagem com sombra radial, etc.).

**MSG-08 Ícone de status de entrega** (só nas próprias mensagens, fora da bolha; não em mensagem de chamada)
- `enviando` (relógio) → enviada (✓) → `recebida` (✓✓ cinza) → `visualizada` (✓✓ cor primária).

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

**MSG-17 Selo de mensagem agendada** (só o autor vê antes da hora)
- Acima da bolha: "Agendada para hoje HH:MM" / "amanhã HH:MM" / "dd/MM HH:MM". Some exatamente na hora (timer agendado, não espera o ciclo de 30 s — `useAgora.ts`).

**MSG-18 Modo conexão lenta**
- Com `navigator.connection.effectiveType` em `slow-2g`/`2g`: imagens mostram "Toque para abrir" e vídeos "Toque para carregar" antes de baixar (`useConexao.ts`).

**MSG-19 Citação (resposta/encaminhada) na bolha** (`BolhaReferencia.vue`, `ReferenciaRecursiva.vue`)
- Bloco com título: remetente (resposta) ou "Encaminhado de <remetente>" / "Encaminhado"; hora; conteúdos da original (imagens, áudio etc.), citação aninhada recursiva até profundidade 5. Clique no título vai para a original (MSG-06).
- Encaminhada: conteúdos próprios iguais aos da referência não são repetidos — só aparece o que foi acrescentado.

### 3.4 ENV — Mensagens: composição e ações

**ENV-01 Enviar texto** — 🆕 o campo agora é rico (ENV-21); só texto continua indo como antes (`MessageInput.vue`, `stores/chat.ts#enviarMensagemComConteudos`)
- Campo "Digite uma mensagem", cresce até 120 px. **Enter envia**; Shift+Enter quebra linha. Botão "Enviar" aparece quando há conteúdo (texto, fila ou resposta); sem conteúdo aparece o microfone.
- Envio otimista: mensagem com `id = -Date.now()`, `enviando:true` aparece na hora; após `PUT /mensagem` troca pelo id do servidor; erro remove a otimista e mostra o erro acima do campo.
- API: `PUT /mensagem {conversa_id, conteudos:[{ordem, tipo, conteudo}], mensagem_referencia?:{tipo, origem_mensagem_id}, visivel_em?: ISO}` → `{id, conversa_id, usuario_id}`.
- Regras: texto `trim()`; pode enviar só a resposta (sem texto) — ver ENV-07. Após enviar, rola ao fim e recarrega a lista de conversas.

**ENV-02 Atalhos de emoji no texto** (`utils/emojiAtalhos.ts`)
- `:)`/`:-)`→🙂, `:D`/`:-D`→😃, `xD`/`XD`→😆, `;)`/`;-)`→😉, `:(`/`:-(`→🙁, `:'(`→😢, `:P`/`:p`→😛, `:O`/`:o`→😮, `:|`→😐, `:/`→😕, `:*`→😘, `<3`→❤️, `\o/`→🙌.
- Só como palavra solta (início/espaço antes e espaço/quebra depois, ou no fim ao enviar); nunca dentro de código (crases).

**ENV-03 Seletor de emoji** (`EmojiPicker.vue`)
- Categorias "Populares", "Rostos", "Gestos", "Objetos" (listas fixas); tooltip com nome em português (`utils/emojiNomes.ts`). Insere no fim do texto e devolve o foco.

**ENV-04 Figurinhas (Lottie)** — 🆕 com o campo **vazio** a figurinha é enviada na hora, sozinha; com algo escrito ela **entra no campo** no ponto do cursor e vai junto, na ordem (ENV-21)
- Aba "Figurinhas" no seletor do campo de mensagem (abre sempre em "Emojis"). Pacotes: **Básico** (coracao, sorriso, risada, estrela, feito, festa, sono, pontinhos), **Rostos** (piscadinha, apaixonado, beijo, legal, surpreso, triste, bravo), **Coisas** (joinha, fogo, coracao-partido, presente, bolo, balao, cafe, sol, chuva).
- Clicar **envia na hora**, sozinha (o texto digitado fica no campo; pode ir como resposta se houver resposta pendente).
- Conteúdo tipo 7 com o identificador `pacote/nome`; animação em `/figurinhas/<pacote>/<nome>.json` (servida pela página, não pela API). Bolha 160 px; no picker 64 px; na resposta 120 px. Toca só quando visível; com "reduzir movimento" fica parada no quadro do meio. Falha → texto "Figurinha". Nome amigável em `nomeFigurinha()`.
- Arquivos: `utils/figurinhas.ts`, `FigurinhaLottie.vue`, `BolhaFigurinha.vue`, `public/figurinhas/**`, `scripts/gerar-figurinhas.ts`.

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
- Texto colado com **mais de 10 linhas** → abre a janela "Inserir código" já preenchida, com linguagem detectada (`markdown` se contém ` ``` `; `texto` se é desenho ASCII; senão heurística). **Cancelar cola o texto como estava** (a janela é só sugestão).
- Texto com cara de código (≥ 2 linhas, metade com cara de código + sinal forte; ou ≥ 50% desenho ASCII; ou ≥ 50% comandos de terminal) → entra como texto e logo vira bloco cercado com a linguagem detectada (Ctrl+Z volta ao texto cru).
- Cerca = uma crase a mais que a maior sequência de crases do código (mín. 3).
- Detecção de linguagem: JSON válido; regras para html, sql, pascal, csharp, typescript, python, javascript, css, bash; senão highlight.js (relevância ≥ 3) ou `texto`.

**ENV-10 Janela "Inserir código"** (`CodigoModal.vue`)
- Seletor de linguagem (`texto, javascript, typescript, python, sql, json, html, css, bash, csharp, pascal, markdown, mermaid` + a detectada, se fora da lista), editor CodeMirror (tema claro/escuro), placeholder "Cole ou escreva seu código aqui...", "Cancelar"/"Enviar", **Ctrl/Cmd+Enter envia**. Enviar **envia imediatamente** a mensagem com o bloco cercado (substitui o texto do campo).

**ENV-11 Colar imagem** — imagem na área de transferência entra na fila de anexos como `print-<timestamp>.<ext>` (ext pelo mime).

**ENV-12 Teclado do campo**
- Tab insere 4 espaços (Shift+Tab sai do campo).
- Qualquer letra digitada fora de um campo, sem modal por cima, foca o campo de mensagem no fim do texto.
- Campo recebe foco ao trocar de conversa e ao iniciar resposta.

**ENV-13 Agendar mensagem** (`AgendarMensagemModal.vue`)
- Botão "Agendar mensagem" (aparece com conteúdo). Modal "Agendar mensagem": "Data" (calendário próprio `DateInput`) e "Hora" (`HH:MM`); padrão **amanhã 08:00**.
- Validações: "Data/hora inválida", "O envio deve ser no mínimo 5 minutos no futuro", "O envio não pode ser mais de 1 ano no futuro". Botões "Cancelar" / "Agendar".
- Envia o conteúdo atual (texto + fila) com `visivel_em` em ISO (UTC). O autor vê a mensagem na hora com o selo (MSG-17); destinatários só depois.

**ENV-14 Cancelar mensagem agendada**
- Na própria mensagem ainda futura, a ação "Ocultar" vira confirmação "Cancelar mensagem agendada" / "Ela não será enviada." / "Cancelar envio" / "Voltar".
- API: `DELETE /mensagem?id=` — para agendada não enviada a resposta **não** traz `excluida_em` e a mensagem é removida da lista.

**ENV-15 Digitando** (`stores/chat.ts`)
- Envio: ao digitar texto não vazio, `POST /conversa/digitando {id: conversaId}`, no máximo 1 a cada 2,5 s (o último é adiado, não perdido). Ao enviar texto, reseta o throttle.
- Recepção: WS tipo 4 `{conversa_id, usuario_id}`; ignora o próprio; expira 4 s sem novo aviso; some quando chega mensagem daquela conversa.
- Exibição: lista (três pontinhos), cabeçalho e chip acima do campo (só se estiver no fim do chat; não durante gravação) + linha luminosa na borda do campo. Textos: direta "Digitando..."; grupo "Ana está digitando...", "Ana e Beto estão digitando...", "Ana, Beto e Caio estão digitando...", "Ana, Beto, Caio e outras N pessoas estão digitando...". Nome de quem não é contato: "Usuário #id".

**ENV-16 Gravando áudio (indicador)**
- Envio: ao começar a gravar e a cada 2,5 s durante a gravação, `POST /conversa/gravando {id}` (mesma regra de throttle).
- Recepção: WS tipo 5 `{conversa_id, usuario_id}`, expira 4 s. Cor vermelha; textos "Gravando áudio..." / "Ana está gravando áudio..." / "… estão gravando áudio...". Gravando tem prioridade sobre digitando na exibição.

**ENV-17 Reações**
- Menu de ações: linha 1 👍 ❤️ 😂 😮, linha 2 😢 👏 🔥 + "Mais emojis" (abre o seletor completo).
- Toggle: reagir de novo com o mesmo emoji remove. Atualização otimista; erro recarrega a conversa.
- Abaixo da bolha: chips `emoji contagem` (destacado se eu reagi; clicar alterna); tooltip lista quem reagiu com foto e hora (hoje `HH:MM`, senão `dd/MM HH:MM`).
- API: `PUT /mensagem/reacao {mensagem_id, emoji}` → `{mensagem_id, emoji, acao}`.
- 🆕 `eaa8bac` **Limite:** até **5 emojis diferentes por pessoa** na mesma mensagem. O web confere antes da reação otimista e mostra o diálogo "Não foi possível reagir" / "Você já reagiu com 5 emojis nesta mensagem." (o servidor confere de novo). Na bolha, **5 chips à mostra** e o resto num chip **"+N"** (destacado se eu reagi em algum); passar o mouse lista os demais (emoji, contagem, nomes) e clicar num deles alterna.
- WS tipo 7 `{conversa_id, mensagem_id, emoji, acao, usuario_id}` — `acao === 'remove'` remove; outro valor adiciona.
- Mensagem oculta não mostra reações.

**ENV-18 Ocultar mensagem** (ex-"Excluir", commit `51a0cd7`)
- Ação "Ocultar" só nas **próprias** mensagens não ocultas, **sem prazo**. Confirmação: título "Ocultar mensagem", texto "Ela continua na conversa, marcada como oculta.", botão "Ocultar" (perigo; foco inicial em "Cancelar").
- API: `DELETE /mensagem?id=` → com `excluida_em` (mensagem fica marcada em todas as listas em memória; recarrega `GET /conversas` para a prévia) ou sem (agendada → removida).
- Erro: aviso "Não foi possível ocultar".
- Os demais participantes recebem via WS 3 (MSG-10).

**ENV-19 Copiar**
- Ação "Copiar": se o menu foi aberto por clique direito **numa imagem**, copia aquela imagem; **no texto**, copia o texto; pelo botão (sem alvo): se a mensagem tem imagem copia a primeira imagem, senão todos os textos unidos por `\n`.
- Imagem é copiada como PNG (converte via canvas se não for PNG) — `utils/copiarImagem.ts`.

**ENV-20 Menu de ações da mensagem** (`MensagemAcoes.vue`)
- Abre pelo botão ⋯ ao passar o mouse na bolha (acompanha a rolagem em bolhas altas) ou clique direito na bolha. Só para mensagens salvas (id > 0), não para chamada nem oculta. Abre abaixo do botão ou acima se não couber; um menu por vez; fecha ao rolar ou clicar fora.
- Itens, nesta ordem: reações rápidas; **Responder**; **Responder no privado** (grupo e mensagem de outro); **Encaminhar**; **Copiar**; **Ocultar** (própria e não oculta).

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

**ANX-05 Vídeo na conversa** — prévia com o primeiro quadro (`url#t=0.1`) e ícone de play; clique abre no visualizador; botão "Baixar video".

**ANX-06 Visualizador de PDF** (`VisualizadorPdf.vue`)
- Botão "Abrir" em anexos `.pdf`. Páginas em canvas renderizadas sob demanda (perto da área visível), 100% = largura da tela (240–900 px); zoom 50%–300% passo 25%; botão "%" volta a ajustar à largura; indicador "página / total"; "Baixar"; "Fechar (Esc)". Mensagens "Carregando PDF..." / "Não foi possível abrir o PDF. Tente baixar o arquivo.".

**ANX-07 Visualizador de HTML isolado** (`VisualizadorHtml.vue`)
- Botão "Abrir" em `.html/.htm` (chat e página de Anexos). Baixa o HTML e mostra em `iframe srcdoc` com `sandbox="allow-scripts"` **sem** `allow-same-origin` e `referrerpolicy=no-referrer` (sem acesso ao token/cookies). "Carregando HTML..." / "Não foi possível abrir o HTML. Tente baixar o arquivo.".
- 🆕 `eaa8bac` Links `#...` rolam dentro do próprio documento (um script injetado logo depois do `<head>`); antes, no `srcdoc`, eles abriam o Conversa dentro do quadro.

**ANX-08 Markdown e Mermaid** — dentro de blocos de código (MSG-15). Não há visualizador de **arquivo** `.md` anexado (só para blocos no texto).

**ANX-09 Download** — "Download" em arquivos: busca a URL assinada, baixa como blob e dispara `<a download=nome>`; falha → abre a URL em nova aba; erro → aviso "Não foi possível baixar".

**ANX-10 Player de áudio** (`AudioPlayerArquivo.vue`, `AudioPlayerGravacao.vue`, `useAudioPlayer.ts`)
- Play/pause, barra de progresso com arraste (seek), duração/tempo `mm:ss`; arquivo mostra o nome; gravação mostra só o player. URL buscada só no primeiro play.
- Apenas **um áudio toca por vez** (`useAudioManager`).
- Áudio recebido **não ouvido** tem o botão verde; o primeiro play chama `POST /mensagem/reproduzir {conversa, mensagem}` (uma vez).
- Em bolha só de áudio, a hora/status fica dentro do player.

**ANX-11 Gravação de áudio** — 🆕 com o campo **vazio** a gravação é enviada na hora; com algo escrito ela **entra no campo** como peça e vai junto (ENV-21) (`useAudioRecording.ts`, `BarraGravacao.vue`, `MessageInput.vue`)
- Exige contexto seguro ("Para gravar áudio por navegador, use HTTPS (ou localhost).") e `MediaRecorder` ("Gravação de áudio não suportada neste navegador.").
- **Pressionar e segurar** o microfone ≥ 300 ms e soltar sobre o botão → para e **envia direto**. **Clique curto** (ou arrastar para fora do botão) → modo travado com a barra de gravação.
- Barra: "Descartar", ponto vermelho + tempo `m:ss`, forma de onda animada; "Pausar gravação" / "Continuar gravação"; pausado: "Ouvir gravação"/"Parar preview" com barra de progresso clicável (seek); "Enviar áudio".
- Arquivo: `audio-<timestamp>.webm|ogg` (mime do `MediaRecorder`), conteúdo **tipo 5**.

**ANX-12 Transcrição de áudio** (`TranscricaoAudio.vue`)
- Abaixo de áudios (tipo 4 e 5) já enviados. Estados (`StatusTranscricao`): 0 Nenhuma → botão "Transcrever"; 1 Processando → "Transcrevendo..." com consulta a cada 3 s; 2 Concluída → texto (ou "(nenhuma fala reconhecida)"); 3 Erro → botão "Não foi possível transcrever. Tentar de novo" + mensagem de erro.
- Estado inicial vem nos próprios conteúdos da mensagem (`transcricao_status`, `transcricao`).
- API: `PUT /anexo/transcricao {identificador}` e `GET /anexo/transcricao?identificador=` → `{status, texto, erro}`. Se o servidor não tem transcritor configurado (parâmetro `transcritor_url` vazio), o botão não funciona (ver CFG-07).

**ANX-13 Página de Anexos** (`AnexosPage.vue`, `AnexosLista.vue`) — 🆕 `eaa8bac`: a mesma `AnexosLista` aparece no painel "Dados do grupo" (CON-08), já com a conversa do grupo
- Sem conversa selecionada: campo "Buscar contato ou grupo..." e "Selecione um contato ou grupo para ver os anexos"; resultados com avatar, etiqueta "Grupo", prévia; "Nenhum resultado".
- Com conversa: chip da conversa com "Limpar selecao"; filtros de direção **Todos / Enviados / Recebidos** e de tipo **Todos / Imagens / Arquivos / Audios / Gravacoes**.
- Imagens em grade (lazy, "Erro" se falhar) com ações "Abrir mensagem" e "Baixar"; demais em lista: ícone/miniatura, nome ("Sem nome"), tamanho · data (hoje `HH:MM`, senão `dd/MM/aa`) · (sem conversa: nome da conversa), autor; ações "Abrir mensagem", "Baixar" e player nativo para áudios.
- Clique: imagem/vídeo → visualizador com a galeria dos itens carregados; HTML → visualizador isolado; outro arquivo → abre a URL.
- Paginação infinita: carrega mais quando faltam 80% da altura visível.
- API: `GET /anexos?conversa=&autor=&direcao=(enviados|recebidos|'')&tipos=2,3,4,5&antes=<anexo_id>&limite=60` → `AnexoItem[]` (`anexo_id, identificador, nome, extensao, tamanho, criado_em, tipo, mensagem_id, conversa_id, conversa_descricao, autor_id, autor_nome, url` — URL assinada com validade de 600 s).
- Entrada também pelo botão "Ver anexos" do perfil (AUT-10).

**ANX-14 URLs assinadas e renovação** — §1.7 (`useAttachments.ts`). Imagens com erro chamam `renovarAnexoUrl` (injeção `provide/inject`).

**ANX-15 Pré-visualização de imagem antes de enviar** (`ImagePreviewModal.vue`, `useImagePreview.ts`) — "Pré-visualização da imagem", "Cancelar"/"Enviar imagem", Enter confirma, Esc fecha. **Sem gatilho atual** (o evento `open-image-preview` não é emitido; imagens vão para a fila). Ver Anexo B.

### 3.6 PES — Pesquisa

**PES-01 Pesquisa na conversa**
- Campo "Pesquisar..." no cabeçalho (desktop) ou painel "Pesquisar nesta conversa" (celular); Enter pesquisa; × limpa.
- API: `GET /pesquisar?texto=<termo>&conversa=<id>` → `Mensagem[]` (o cliente ainda filtra por `conversa_id`).
- Resultados numa lista sob o cabeçalho (ordem invertida, mais recentes primeiro): "#<id> - <resumo>"; clique → MSG-06.

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

**CHA-02 Iniciar compartilhando a tela** — botão "Compartilhar tela" no cabeçalho: chamada de **vídeo** com a tela no lugar da câmera + microfone (+ som do sistema misturado, CHA-15).

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

**CHA-12 Modos de exibição** (só vídeo, janela não flutuante) — **Grade** ("Todos os participantes lado a lado"), **Destaque** ("Um participante grande e os demais na lateral"), **Tela única** ("Só um participante, ocupando toda a área", setas "Participante anterior"/"Proximo participante"). Clique num tile destaca; clique no destaque volta à grade. Grade: 1 col (1), 2 col (2–4), 3 col (≤6), 4 col (mais). Se o destacado sai em tela única, o pedido fica pendente e volta quando ele reconectar.

**CHA-13 Janela flutuante e barra** — vídeo minimizado no desktop: janela flutuante arrastável pelo cabeçalho e redimensionável pelas bordas/cantos. `CallBar` (áudio sempre; vídeo minimizado): status, duração, tipo, nº de pessoas, avatares (anel verde em quem fala); em áudio: microfone, saída, adicionar, botão "Vídeo" (upgrade) e sair; em vídeo: "Abrir chamada" e sair. No celular a chamada minimizada fica só na barra.

**CHA-14 Adicionar participante** — modal "Adicionar à chamada" com contatos que não estão na chamada ("Nenhum contato disponível para adicionar."), checkboxes, "Adicionar". API: `PUT /chamada/usuario {chamada_id, usuario_id}` (um por vez) → `GET /chamada/dados`.

**CHA-15 Compartilhar tela com áudio do sistema**
- `getDisplayMedia({video:true, audio:{restrictOwnAudio:true, suppressLocalAudioPlayback:false}, systemAudio:'include'})`.
- A trilha de vídeo da tela **substitui** a da câmera na publicação (`replaceTrack`); sem câmera (publicação só com áudio) republica e avisa com `POST /chamada/video`.
- Som do computador + microfone misturados num `AudioContext` numa trilha única que substitui a do microfone; mutar o microfone silencia só a voz. Parar (botão ou "parar compartilhamento" do navegador) restaura câmera (ou abre uma nova) e o microfone puro.
- Anuncia aos outros com sinal `{acao:'tela', ativa}` (CHA-17) e reanuncia para quem entra depois.
- `contentHint` e limites de envio pela prioridade (Nitidez: 15 fps, `maintain-resolution`; Fluidez: 30 fps, `maintain-framerate`).

**CHA-16 Troca de tipo áudio → vídeo**
- Botão "Vídeo" na barra de chamada de áudio: abre a câmera (sem câmera continua só recebendo vídeo), muda para vídeo, reconecta todos os peers e `POST /chamada/video {id}` (proteção contra clique duplo).
- Os outros recebem WS 56 (`VideoAtivado`): se já estão em vídeo → só reassinam quem republicou; senão modal "Vídeo ativado" / "<nome> ativou o vídeo" / "Deseja transmitir seu vídeo também?" com **"Apenas assistir"** (câmera desligada, tela única em quem ativou) e **"Transmitir também"**; sem resposta em **15 s** → "Apenas assistir".

**CHA-17 Ponteiro remoto** (commit `20d5b73`)
- Quem compartilha a tela avisa por WS 57 `{acao:'tela', ativa:true|false}`. Quem assiste vê o botão "Apontar na tela compartilhada"/"Desligar ponteiro" (só com tela remota; desliga sozinho quando não há mais telas).
- Ligado, o movimento do mouse sobre a imagem da tela envia `{acao:'ponteiro', alvo:<id de quem compartilha>, x, y}` com x/y de 0 a 1 **relativos à imagem do vídeo** (descontando faixas de `object-contain`), no máximo a cada 40 ms (o último sempre vai); sair da imagem envia `x:null, y:null`.
- Todos que veem aquela tela (inclusive quem compartilha, sobre o próprio vídeo local) desenham o ponteiro com o nome e uma cor por usuário (`id % 5`). Ponteiro parado some após 5 s; some quando a pessoa sai ou a tela para. Sinal de outra chamada é ignorado.

**CHA-18 Chat da chamada** — 🆕 na janela principal o painel virou o **chat completo** (CHA-24) (commit `20d5b73`)
- Painel "Chat da chamada" ao lado do vídeo (celular: por cima). Vazio: "As mensagens enviadas aqui ficam num grupo com quem está na chamada.". Só texto: anexos aparecem como "[Imagem]", "[Áudio]", "[Figurinha]", "[Arquivo]"; mensagens ocultas não aparecem. Campo "Mensagem", Enter envia.
- 🆕 `eaa8bac` Agora o grupo é criado no **primeiro clique no campo** (não mais na primeira mensagem): sem grupo, o painel mostra o aviso e um campo-botão "Digite uma mensagem" ("Abrindo o chat..."); o clique chama `PUT /chamada/chat`, abre o chat completo (CHA-24) e põe o cursor no campo. Erro aparece numa faixa vermelha.
- (Antes de `eaa8bac`) O grupo só é criado na **primeira mensagem**: `PUT /chamada/chat {id}` → `{conversa_id}` (idempotente; guardado em `chamada.conversa_chat_id`). Se outro criou, chega WS 57 `{acao:'chat', conversa_id}`. Quem entra depois passa a fazer parte (servidor).
- Envio: `PUT /mensagem` no grupo; recarrega `GET /mensagens?conversa=<grupo>&…80`. Painel aberto marca as recebidas como visualizadas. Botão do chat mostra o número de não lidas do grupo quando fechado.

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

**CHA-23 Áudio remoto estável** — o áudio de cada participante toca num `HTMLAudioElement` criado pela store (vive a chamada inteira); os `<video>` são sempre mudos. Evita perder áudio ao alternar janela flutuante/tela cheia. "Áudio de saída" muta esses elementos.

### 3.8 SIP — Telefonia SIP (ramal)

**SIP-01 Configuração do ramal** (aba Voip)
- "Configuracao de ramal" (etiqueta "Asterisk / VueSIP"): "Ramal SIP", "Usuario de autenticacao", "Senha SIP", "Nome de exibicao", "Dominio SIP", "Servidor WebSocket" (ex. `wss://pbx.exemplo.com:8089/ws`), checkbox "Configuracao SIP ativa para este usuario".
- Validação: "Preencha ao menos ramal, senha SIP, dominio e servidor WebSocket.". Botão "Criar configuracao SIP" / "Salvar configuracao SIP"; "Nenhuma alteracao para salvar."; mensagens de sucesso.
- API: `GET /sip` → `SipConfig` ou `{}` (sem ramal → null); `PUT /sip {sip_user, auth_user, sip_password, display_name, domain, ws_server, ativo}`; `PATCH /sip {id, ...só o que mudou}`.

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
- Uma por conversa (agrupamento): título = nome do remetente da última mensagem; corpo = texto resumido ou "Imagem", "Gravacao de audio", "Audio", "Figurinha", "Arquivo"; ícone = avatar do contato/conversa ou `/logo.png`; `tag: conversa-<id>`, `renotify`, `requireInteraction` (fica até interagir), `silent` (o som é o do app).
- Criada pelo service worker quando registrado (clique foca a aba e abre a conversa, mesmo após recarregar); senão pela página (`new Notification`, com cuidado do `onclose` assíncrono).

**NOT-03 Fechar notificação** — quando o contador de não lidas da conversa chega a 0 (leitura) e ao arquivar a conversa.

**NOT-04 Push em segundo plano (FCM)** — §1.6.

**NOT-05 Notificação de chamada** — CHA-03 (tag `conversa-chamada`, fechada quando para de tocar).

**NOT-06 Pedido de permissão** — no início da sessão e após o login; aba Permissões (CFG-04).

### 3.10 PRE — Presença

**PRE-01 Online/offline**
- `GET /contatos/online` → `number[]` a cada conexão do WS; WS 60 `{usuario_id, online:boolean}`.
- Bolinha verde no avatar da lista, do cabeçalho (direta) e em "Nova conversa". Não há "visto por último" na UI (o campo `online_em` existe na lista de datas, sem uso visível).

### 3.11 ATV — Atividades (commit `39d5af1`)

**ATV-01 Tela de atividades** (`AtividadesPage.vue`, `stores/atividades.ts`)
- Título "Atividades". Vazio: "Nenhuma atividade ainda." / "Reações, respostas, menções e chamadas perdidas aparecem aqui.".
- Agrupadas por "Hoje"/"Ontem"/`dd/MM/aaaa`. Item: avatar de quem fez com selo no canto (emoji da reação; "↩" resposta; "@" menção; "✆" chamada perdida), "**<autor>** <descrição>", "em <grupo>" (só em grupo), prévia entre aspas (texto ou "Imagem"/"Arquivo"/"Áudio"/"Figurinha"; não para chamada), hora `HH:MM`, bolinha "Nova".
- Descrições: Reação "reagiu <emoji> à sua mensagem"; Resposta "respondeu sua mensagem"; Menção "mencionou você"; Chamada perdida "ligou (chamada perdida)" ou "ligou (chamada de vídeo perdida)".
- Clique: com `mensagem_id` → abre a conversa na mensagem (MSG-06); sem (chamada perdida) → abre a conversa.
- Paginação: ao chegar a 200 px do fim, `GET /atividades?antes=<id da última>&limite=30`; fim quando volta < 30.
- API: `GET /atividades?antes=0&limite=30` → `Atividade[]` (`id, tipo (1 Reacao, 2 Resposta, 3 Mencao, 4 ChamadaPerdida), criado_em, nova, autor_id, autor_nome, autor_avatar_url, conversa_id, conversa_tipo, conversa_descricao, mensagem_id, conteudo_tipo, texto, chamada_id, chamada_tipo, emoji`).

**ATV-02 Contador de novas**
- Badge no item Atividades da barra (`99+`). Fonte: `GET /atividades/novas` → `{quantidade}` ao conectar o WS e a cada WS 61.
- Abrir a tela: carrega a lista e `POST /atividades/vistas` → `{vistas_em}`, zera o contador; os itens continuam destacados como "nova" enquanto a tela está aberta. Com a tela aberta, WS 61 recarrega e marca como visto; fechada, só atualiza o contador.

**ATV-03 Geração de "chamada perdida" pelo cliente** — toda recusa automática (30 s sem resposta, ocupado, pendente antiga > 25 s) envia `nao_atendeu: true` em `POST /chamada/recusar`, o que o servidor transforma em atividade de chamada perdida. **O Android precisa fazer o mesmo.**

### 3.12 CFG — Configurações, personalização e administração

**CFG-01 Abas e navegação** — §2.5 (celular: lista de abas → sub-tela com botão voltar).

**CFG-02 Tema escuro** — botão na aba Usuário ("Tema claro"/"Tema escuro"); padrão segue `prefers-color-scheme`; persistido em `theme`. Também aplicado no popup.

**CFG-03 Dispositivos** — "Sessao atual" (etiqueta "Ativo"): Dispositivo, Modelo, Sistema, Plataforma (detectados localmente); "Dispositivos de midia" com "Atualizar lista" (enumera microfones, câmeras, saídas; etiquetas "Padrao"/"Comunicacao"). Sem API (não lista os outros dispositivos do usuário no servidor).

**CFG-04 Permissões do navegador** — "Permissoes do navegador": Notificacoes, Microfone, Camera com status "Concedida"/"Negada"/"Nao solicitada"/"Indisponivel" e botão "Solicitar"; dica do cadeado na barra de endereço.

**CFG-05 Cores personalizadas** (`ConfiguracaoCores.vue`, `useCoresPersonalizadas.ts`)
- Edita as cores do **tema atual** (claro e escuro separados), valem na hora, salvas no navegador. Grupos: Principal (primary 50–900), Superfícies (surface base, 50–900; escala invertida no escuro), Sucesso, Perigo, Informação, Alerta, Chamada (300–900 — janela de chamada escura nos dois temas). Cada tom com descrição de uso. Busca por variável ou uso; "Restaurar todas", "Restaurar grupo", "Voltar ao padrão" por cor.

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

**CFG-08 Acessos — permissões** (`ConfiguracaoAcessos.vue`, permissão `permissoes`)
- "Modo aberto" (ninguém tem Acessos): aviso de que todos podem mexer; marcar Acessos para alguém encerra o modo.
- "O que cada permissão libera" (nomes: `parametros` → "Sistema", `permissoes` → "Acessos"), busca "Buscar usuário" (nome/login), tabela usuário × permissão com checkboxes ("(você)").
- API: `GET /permissoes` → `{permissoes:[{codigo, descricao}], usuarios:[{id, nome, login, permissoes:[]}], modo_aberto}`; `PUT /permissao/usuario {usuario_id, codigo}`; `DELETE /permissao/usuario?usuario_id=&codigo=`. Recusa do servidor mostra o motivo e a caixa volta. Após cada mudança recarrega as próprias permissões (abas visíveis acompanham).

### 3.13 GER — Comportamentos gerais de UX

**GER-01 Toast de erro global** — faixa vermelha no topo com ×.
**GER-02 Faixa de conexão em tempo real** — §1.4.
**GER-03 Diálogo de confirmação próprio** (`DialogoConfirmacao.vue`, `useDialogo.ts`) — título, mensagem, "Cancelar"/"OK" (customizáveis), variante perigo (botão vermelho, foco em Cancelar), Esc cancela, clique fora cancela; modo aviso (só OK). Novo diálogo responde "não" ao anterior.
**GER-04 Arrastar e soltar arquivos** — ANX-01.
**GER-05 Menu de contexto nativo bloqueado** — exceto em campos de texto/conteúdo editável (clique direito abre os menus do app).
**GER-06 Seções não implementadas** — tela "Em desenvolvimento".
**GER-07 Avatares com fallback** — inicial do nome (primeira "letra" real, inclusive emoji); imagens que falham somem e fica a inicial.

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
| **Total** | | **136** |

---

## 4. Todos os endpoints REST e eventos WebSocket

### 4.1 Endpoints REST (todos sob `/api`, Bearer token salvo onde indicado)

Fonte única: `src/services/conversaApi.ts` (+ `eden.ts`). "Params" = query string; "Body" = JSON.

| # | Método | Rota | Params / Body | Resposta (campos usados) | Usado em |
|---|---|---|---|---|---|
| 1 | POST | `/login` | body `{login, senha, dispositivo_id?}` (sem token) | `{id, nome, email, telefone, avatar_url, avatar_identificador, token, dispositivo:{id, nome, modelo, versao_so, plataforma, ativo}}` | AUT-01 |
| 2 | PUT | `/usuario` | body `{nome, login, email, senha}` (sem token) | `{id, nome, login, email}` | AUT-02 |
| 3 | PATCH | `/dispositivo` | body `{id, nome?, modelo?, versao_so?, plataforma?, token_fcm?}` | — | AUT-04 |
| 4 | POST | `/alterar-senha` | body `{senha_atual, senha}` | — | AUT-07 |
| 5 | PATCH | `/usuario` | body `{id, nome?, email?, telefone?, avatar_anexo_id?: number\|null}` | — | AUT-06, AUT-08 |
| 6 | GET | `/usuario/contatos` | — | `Contato[]` | CON-11 |
| 7 | GET | `/contatos/online` | — | `number[]` | PRE-01 |
| 8 | GET | `/usuario/permissoes` | — | `string[]` | AUT-09 |
| 9 | GET | `/conversas` | — | `Conversa[]` | CON-01 |
| 10 | GET | `/conversa/usuarios` | `conversa` | `[{id, usuario_id, nome, avatar_url}]` | CON-08, CHA-01 |
| 11 | PUT | `/conversa` | body `{descricao, tipo}` (1 direta, 2 grupo) | `{id, ...}` | CON-06, CON-07 |
| 12 | PATCH | `/conversa` | body `{id, descricao}` | — | CON-08 |
| 13 | PUT | `/conversa/usuario` | body `{conversa_id, usuario_id}` | `{id, conversa_id, usuario_id}` | CON-06/07/08 |
| 14 | DELETE | `/conversa/usuario` | `id` (= id do vínculo conversa_usuario) | — | CON-08 |
| 15 | PATCH | `/conversa/fixadas` | body `{conversas: number[]}` | `{conversas}` | CON-03 |
| 16 | PATCH | `/conversa/arquivada` | body `{conversa, arquivada}` | `{id, arquivada}` | CON-04 |
| 17 | POST | `/conversa/digitando` | body `{id}` | — | ENV-15 |
| 18 | POST | `/conversa/gravando` | body `{id}` | — | ENV-16 |
| 19 | GET | `/mensagens` | `conversa, mensagemreferencia, mensagensprevias, mensagensseguintes` | `Mensagem[]` | MSG-01, MSG-06, CHA-18 |
| 20 | GET | `/mensagens/novas` | `desde` (ISO ou '') | `[{conversa_id, mensagem_id, ate}]` | §1.4 |
| 21 | PUT | `/mensagem` | body `{conversa_id, conteudos:[{ordem, tipo, conteudo}], mensagem_referencia?:{tipo, origem_mensagem_id}, visivel_em?}` | `{id, conversa_id, usuario_id}` | ENV-01/04/06/07/08/13, CHA-18 |
| 22 | DELETE | `/mensagem` | `id` | `{..., excluida_em?}` | ENV-14, ENV-18 |
| 23 | PUT | `/mensagem/reacao` | body `{mensagem_id, emoji}` | `{mensagem_id, emoji, acao}` | ENV-17 |
| 24 | POST | `/mensagem/visualizar` | body `{conversa, mensagem}` | `{sucesso}` | MSG-04 |
| 25 | POST | `/mensagem/reproduzir` | body `{conversa, mensagem}` | `{sucesso}` | ANX-10 |
| 26 | GET | `/mensagem/status` | `conversa, mensagem` (ids separados por vírgula) | `[{conversa_id, mensagem_id, recebida, visualizada, reproduzida, excluida_em}]` | MSG-10 |
| 27 | GET | `/mensagem/status/detalhe` | `id` | `[{usuario_id, nome, recebida, visualizada, reproduzida}]` (datas) | MSG-09 |
| 28 | GET | `/pesquisar` | `texto, conversa` (0 = todas) | `Mensagem[]` | PES-01/02 |
| 29 | GET | `/anexo/existe` | `identificador` | `{id}` ou objeto sem `id` | §1.7 |
| 30 | PUT | `/anexo` | body `{identificador, tipo, nome, extensao, tamanho}` | `{id, upload_url}` | §1.7 |
| — | PUT | `<upload_url>` (MinIO, fora da API, sem Bearer) | corpo binário | 2xx | §1.7 |
| 31 | GET | `/anexo` | `identificador` | `{url}` | ANX-*, AUT-08 |
| 32 | GET | `/anexo/transcricao` | `identificador` | `{status, texto, erro}` | ANX-12 |
| 33 | PUT | `/anexo/transcricao` | body `{identificador}` | `{status, texto, erro}` | ANX-12 |
| 34 | GET | `/anexos` | `conversa, autor, direcao, tipos (csv), antes, limite` (0/'' = sem filtro) | `AnexoItem[]` | ANX-13 |
| 35 | PUT | `/chamada/iniciar` | body `{tipo, usuarios:[{id}], conversa_id?}` | `Chamada` | CHA-01 |
| 36 | POST | `/chamada/cancelar` | body `{id}` | `{id}` | CHA-09 |
| 37 | POST | `/chamada/entrar` | body `{id}` | `{id}` | CHA-04 |
| 38 | POST | `/chamada/recusar` | body `{id, nao_atendeu?: true}` | `{id}` | CHA-05/06/08, ATV-03 |
| 39 | POST | `/chamada/sair` | body `{id}` | `{id}` | CHA-10 |
| 40 | POST | `/chamada/finalizar` | body `{id}` | `{id}` | (store, sem botão) |
| 41 | GET | `/chamada/dados` | `id` | `Chamada {id, iniciada, finalizada, tipo, status, criado_em, criado_por, conversa_chat_id, usuarios:[{usuario_id, usuario_nome, status, adicionado_por, adicionado_por_nome, adicionado_em, entrou_em, saiu_em, recusou_em}]}` | CHA-* |
| 42 | PUT | `/chamada/chat` | body `{id}` | `{conversa_id}` | CHA-18 |
| 43 | PUT | `/chamada/usuario` | body `{chamada_id, usuario_id}` | `{id}` | CHA-14 |
| 44 | POST | `/chamada/video` | body `{id}` | — | CHA-15/16 |
| 45 | GET | `/chamadas/pendentes` | — | `ChamadaPendente[]` | CHA-08 |
| 46 | GET | `/chamadas` | `participante, de, ate` | `ChamadaHistoricoItem[]` | CHA-22 |
| 47 | GET | `/ice` | — | `{iceServers: RTCIceServer[], iceTransportPolicy}` | §5, SIP-05 |
| 48 | GET | `/sip` | — | `SipConfig` ou `{}` | SIP-01/02 |
| 49 | PUT | `/sip` | body `{sip_user, auth_user, sip_password, display_name, domain, ws_server, ativo}` | `SipConfig` | SIP-01 |
| 50 | PATCH | `/sip` | body `{id, ...campos}` | — | SIP-01 |
| 51 | GET | `/atividades` | `antes, limite` | `Atividade[]` | ATV-01 |
| 52 | GET | `/atividades/novas` | — | `{quantidade}` | ATV-02 |
| 53 | POST | `/atividades/vistas` | — | `{vistas_em}` | ATV-02 |
| 54 | GET | `/permissoes` | — | `PermissoesSistema` | CFG-08 |
| 55 | PUT | `/permissao/usuario` | body `{usuario_id, codigo}` | `{usuario_id, codigo}` | CFG-08 |
| 56 | DELETE | `/permissao/usuario` | `usuario_id, codigo` | `{usuario_id, codigo}` | CFG-08 |
| 57 | GET | `/parametros` | — | `ParametrosSistema` | CFG-07 |
| 58 | PATCH | `/parametros` | body `AlteracaoParametros` | `ParametrosSistema` | CFG-07 |

Fora da API: `POST /webrtc/<caminho>/whip` e `POST /webrtc/<caminho>/whep` (MediaMTX, `Content-Type: application/sdp`, sem Bearer) — §5; `GET /figurinhas/<pacote>/<nome>.json` (estático da página); `/notification.mp3`, `/toque.mp3`, `/chamando.mp3`, `/logo.png` (estáticos).

### 4.2 Eventos WebSocket

Enum: `TipoEventoSocket` em `src/types/api.ts`. Mensagens são JSON.

**Enviados pelo cliente**

| tipo | Nome | Payload | Quando |
|---|---|---|---|
| 1 | (login) | `{tipo:1, token}` | `onopen` |
| 57 | SinalChamada | `{tipo:57, chamada_id, dados: SinalChamada}` | tela compartilhada, ponteiro (CHA-15/17) |

**Recebidos**

| tipo | Nome | Campos lidos | Ação no cliente |
|---|---|---|---|
| 2 | NovaMensagem | (nenhum) | `GET /mensagens/novas?desde=cursor` → notificações, lista, conversa ativa (§1.4) |
| 3 | StatusMensagem | `grupo` (conversa), `mensagens` ("1,2,3") | `GET /mensagem/status` → atualiza recebida/visualizada/reproduzida/excluida_em; recarrega conversas |
| 4 | Digitando | `conversa_id, usuario_id` | indicador 4 s |
| 5 | GravandoAudio | `conversa_id, usuario_id` | indicador 4 s |
| 7 | ReacaoMensagem | `conversa_id, mensagem_id, emoji, acao ('remove' ou outro), usuario_id` | atualiza a reação localmente (só se a mensagem está em memória) |
| 40 | ConversaAtualizada | — | `GET /conversas` |
| 51 | ChamadaRecebida | `chamada_id, usuario_id` | CHA-03/06 |
| 52 | ChamadaFinalizada | `chamada_id` | encerra |
| 53 | UsuarioRecusou | `chamada_id, usuario_id` | CHA-07/10 |
| 54 | UsuarioEntrou | `chamada_id, usuario_id` | CHA-07/10 |
| 55 | UsuarioSaiu | `chamada_id, usuario_id` | CHA-10 |
| 56 | VideoAtivado | `chamada_id, usuario_id` | CHA-16 |
| 57 | SinalChamada | `chamada_id, usuario_id, dados` | `dados.acao`: `'tela'` `{ativa}` · `'ponteiro'` `{alvo, x, y}` (x/y null = saiu) · `'chat'` `{conversa_id}`; ignora o próprio e de outra chamada |
| 60 | StatusUsuario | `usuario_id, online` | presença |
| 61 | NovaAtividade | — | ATV-02 |

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

### 5.4 Assinatura (WHEP)

- Uma `RTCPeerConnection` de recepção por participante, transceivers `recvonly`: áudio sempre; vídeo só se a chamada é de vídeo.
- Tentativas enquanto o outro ainda não publicou: vídeo **40 × 1 s**, áudio **12 × 0,8 s**; esgotado → erro `WHEP erro: <status|timeout>`. 404 não aparece como erro para o usuário: tenta de novo na próxima sincronização (1,5 s depois).
- Trilhas recebidas vão para um `MediaStream` por peer; áudio toca num `HTMLAudioElement` dedicado (CHA-23); vídeos sempre mudos.
- Reconexão: `failed`/`disconnected` → fecha e reassina após 2 s; chamada de vídeo sem trilha de vídeo 5 s depois de conectar → reassina; sincronização periódica (4 s) com `GET /chamada/dados` reassina peers com conexão `failed/disconnected/closed`, sem áudio vivo ou (vídeo) sem vídeo vivo, e desconecta quem não está mais com status "Entrou" (3).
- Reassinar de propósito: ao receber `VideoAtivado` (56) já em vídeo.

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
| Menu de ações | "Responder", "Responder no privado", "Encaminhar", "Copiar", "Ocultar"; reações 👍 ❤️ 😂 😮 / 😢 👏 🔥 + "Mais emojis" |
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
| Transcrição | "Transcrever", "Transcrevendo...", "(nenhuma fala reconhecida)", "Não foi possível transcrever. Tentar de novo" |
| Arquivo | "Abrir" (PDF/HTML), "Download", "Baixar video", "Toque para abrir" / "Toque para carregar" |
| Agendar | "Agendar mensagem", "Data", "Hora", "Cancelar", "Agendar"; erros "Data/hora inválida", "O envio deve ser no mínimo 5 minutos no futuro", "O envio não pode ser mais de 1 ano no futuro" |
| Resumos de conteúdo | notificação: "Imagem", "Gravacao de audio", "Audio", "Figurinha", "Arquivo"; resposta/encaminhar (`resumoMensagem`): texto, "Imagem", "Áudio", "Arquivo"; código: "Código (linguagem)" |

### 6.2 Conversas, chamadas, atividades

- Lista: "Pesquisar...", "Nova conversa", "Arquivadas (N)", etiquetas "Grupo" e "Arquivada", menu "Fixar"/"Desafixar"/"Arquivar"/"Desarquivar".
- Chamada recebida: "Chamada recebida", "<nome> está ligando...", "Vídeo + Áudio" / "Somente Áudio", "Atender só assistindo"; notificação "<nome> está ligando (Vídeo|Áudio)".
- Upgrade: "Vídeo ativado", "<nome> ativou o vídeo", "Deseja transmitir seu vídeo também?", "Apenas assistir", "Transmitir também".
- Janela da chamada: "Chamando...", "Em chamada", "Encerrando...", "N pessoa(s)", modos "Grade"/"Destaque"/"Tela única", "Somente recepção", "Ativar microfone", "Ativar câmera", "Chat da chamada", "Apontar na tela compartilhada"/"Desligar ponteiro", "Adicionar à chamada", "Abrir chamada", "Vídeo".
- Histórico: "Chamadas", "Todas", "Perdidas", "Buscar contato", "De", "Até", "Hoje", "Ontem", "Ligar novamente", "Nenhuma chamada", "Nenhuma chamada perdida".
- Bolha de chamada: "Chamada de audio|video[ em grupo]", status "Recusada", "Perdida", "Cancelada"; participantes "Perdida", "Recusou", "Desconectou".
- Atividades: ver ATV-01 (descrições exatas).

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
| Figurinhas Lottie | Suportado | `lottie-android`/`lottie-compose`; baixar os JSON de `https://HOST/figurinhas/<pacote>/<nome>.json` (ou embarcar no APK) |
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
- **Figurinha** (ENV-04) e **gravação** (ANX-11) com o campo **vazio** são enviadas na hora, sozinhas; com algo escrito, entram como peça.
- **Emoji** entra no ponto do cursor (o cursor é guardado quando o campo perde o foco).
- **Placeholder:** "Digite uma mensagem". Altura máxima de 40% da tela, com rolagem.
- **Botão "+" (Anexar)** abre o `AnexoPopup` com: **Arquivo**, **Código** e (só em grupo) **Votação**.
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
