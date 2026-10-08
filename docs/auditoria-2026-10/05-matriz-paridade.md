# 05 — Matriz de paridade Web × Android

**Base:** inventário do web (doc 03, 132 funcionalidades) × auditoria do Android (doc 04) × contrato atual (docs 01 e 02).
**Data:** 2026-10-06.

## Legenda

| Símbolo | Significado no Android **atual** |
|---|---|
| ✅ | Existe e funciona contra o servidor atual |
| 🟡 | Existe parcialmente ou com defeitos relevantes |
| 🔴 | Existe, mas **quebra contra o servidor atual** ou tem defeito crítico |
| ⬜ | Não existe |
| ➖ | Não se aplica literalmente ao Android; precisa de **adaptação** (veja a coluna "Adaptação") |

A coluna **Fase** indica em que fase do plano da nova base (doc 07) a funcionalidade entra:

| Fase | Escopo |
|---|---|
| F0 | Fundação |
| F1 | Sessão e conversas |
| F2 | Mensagens: núcleo |
| F3 | Anexos e mídia |
| F4 | Ações sobre mensagens |
| F5 | Notificações e push |
| F6 | Chamadas |
| F7 | Atividades, pesquisa e configurações |
| F8 | SIP e extras |

**Prio:** P0 = obrigatório para substituir o app atual · P1 = paridade essencial · P2 = paridade completa · P3 = opcional/avaliar.

---

## Placar

| Grupo | Total | ✅ | 🟡 | 🔴 | ⬜ | ➖ |
|---|---|---|---|---|---|---|
| AUT | 10 | 0 | 2 | 1 | 7 | 0 |
| CON | 13 | 1 | 4 | 1 | 6 | 1 |
| MSG | 20 | 0 | 3 | 2 | 14 | 1 |
| ENV | 22 | 0 | 2 | 0 | 15 | 5 |
| ANX | 15 | 0 | 3 | 3 | 8 | 1 |
| PES | 2 | 0 | 0 | 0 | 2 | 0 |
| CHA | 24 | 0 | 6 | 6 | 11 | 1 |
| SIP | 5 | 0 | 0 | 0 | 5 | 0 |
| NOT | 6 | 1 | 2 | 2 | 1 | 0 |
| PRE | 1 | 0 | 0 | 1 | 0 | 0 |
| ATV | 3 | 0 | 0 | 0 | 3 | 0 |
| CFG | 8 | 0 | 2 | 0 | 4 | 2 |
| GER | 7 | 0 | 2 | 0 | 2 | 3 |
| **Total** | **136** | **2** | **26** | **16** | **78** | **14** |

> A linha extra "Gravação" (codec), na tabela CHA, não entra na contagem.
>
> 🆕 **Atualizado em 2026-10-06 (noite)** com os commits `8031fa5` (servidor) e `39d06f9` (web): +4 funcionalidades (ENV-21, ENV-22, MSG-20, CHA-24), todas ⬜ no Android.
>
> 🆕 **Atualizado em 2026-10-08** com os commits `d4435db` e `5cad911` (servidor) e `eaa8bac` e `785bdef` (web): nenhuma funcionalidade nova; mudam CON-08, MSG-16, MSG-20, ENV-17, ENV-22, ANX-13, CHA-11, CHA-18 e CHA-24 (coluna "Adaptação"). A coluna "Android" continua sendo a do app **legado**; o andamento da nova base está no `TODO.md`.
>
> **Leitura:** só 2 de 136 funcionalidades estão íntegras. 42 existem de alguma forma (🟡+🔴), mas 16 delas quebram contra o servidor atual. 78 nunca existiram no Android.

---

## AUT — Autenticação, sessão, perfil

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| AUT-01 | Login | 🟡 | Funciona, mas salva **senha em texto puro** e refaz o login a cada abertura (#6, #44); `dispositivo_id = null` (`LoginActivity.kt:102`) | Guardar só o token (Keystore); reenviar o `dispositivo_id` salvo | F1 | P0 |
| AUT-02 | Cadastro | ⬜ | Endpoint declarado, sem UI | Tela "Criar conta" | F1 | P1 |
| AUT-03 | Sessão resiliente (401 → login; outro erro → tenta de novo) | ⬜ | Splash ignora o token e sempre manda para o Login; não há interceptor de 401 (#44) | `Authenticator`/interceptor + estado global de sessão | F1 | P0 |
| AUT-04 | Registro de dispositivo e token push | 🔴 | `PATCH /dispositivo` sem `id` → 400; FCM não configurado (#5; doc 02 Q8) | `PATCH /dispositivo {id,…}` após o login + `onNewToken` | F1/F5 | P0 |
| AUT-05 | Logout | 🟡 | Não para a chamada, não limpa notificações, cache nem token push (#45) | Logout central: limpa Room, DataStore, notificações e `token_fcm: null` | F1 | P0 |
| AUT-06 | Editar perfil (nome, e-mail) | ⬜ | Repositório morto | Tela de perfil | F7 | P1 |
| AUT-07 | Alterar senha | ⬜ | Código morto com campo errado (`senha_nova` em vez de `senha`) | Tela + validações iguais ao web (mínimo 6, confirmação) | F7 | P1 |
| AUT-08 | Avatar (enviar/remover) | ⬜ | — | Photo Picker + recorte quadrado 256×256 JPEG 85% + upload + `PATCH /usuario {avatar_anexo_id}` | F7 | P1 |
| AUT-09 | Permissões do sistema do usuário | ⬜ | — | `GET /usuario/permissoes` → mostra/oculta as telas Sistema/Acessos | F7 | P2 |
| AUT-10 | Ver perfil de outro usuário | ⬜ | — | Bottom sheet com foto, nome, e-mail, telefone e "Ver anexos" | F7 | P1 |

## CON — Conversas

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| CON-01 | Lista de conversas (avatar, online, prévia, não lidas, fixadas, arquivadas, digitando) | 🟡 | Lista existe; **sem badge** (o layout tem, o adapter não preenche), sem avatar, sem fixadas/arquivadas, hora sempre `HH:mm`, recarrega tudo com spinner (#26, #47) | Compose `LazyColumn` alimentada pelo Room; atualização incremental | F1 | P0 |
| CON-02 | Filtro local + "Nova conversa" com contatos sem conversa | ⬜ | Só há a tela separada de Contatos | Busca na barra superior + seção "Nova conversa" | F1 | P1 |
| CON-03 | Fixar / desafixar / reordenar | ⬜ | — | Menu de toque longo + arrastar (ou "mover para cima/baixo"); `PATCH /conversa/fixadas` com a lista inteira | F1 | P1 |
| CON-04 | Arquivar / desarquivar | ⬜ | — | Menu + gesto de deslizar; arquivadas sem som nem notificação; seção "Arquivadas (N)" | F1 | P1 |
| CON-05 | Menu de contexto da conversa | ⬜ | — | Toque longo → bottom sheet | F1 | P1 |
| CON-06 | Obter/criar conversa direta | 🟡 | Existe, mas não é atômico e não checa retornos (#46) | Caso de uso único; tratar falha parcial | F1 | P0 |
| CON-07 | Criar grupo | 🟡 | Existe em 2 telas; **descarta a descrição** (`detalhes_grupo_activity.kt:64`); não inclui o criador de forma garantida | Tela única: nome + seleção | F1 | P1 |
| CON-08 | Gerenciar membros (renomear, adicionar, remover) | ⬜ | Endpoints mortos | Tela "Membros do grupo" — 🆕 `eaa8bac`: o web juntou nome, participantes e anexos num painel "Dados do grupo" aberto pelo avatar do grupo; no Android, abrir pelo cabeçalho do chat e mostrar os anexos do grupo na mesma tela | F1 | P1 |
| CON-09 | Cabeçalho (avatar, online real, membros, digitando, botões) | 🟡 | Mostra **"online" fixo, que é falso** (`ChatActivity.kt:137,1144`) | TopAppBar com presença real e subtítulo de membros/digitando | F2 | P0 |
| CON-10 | Abrir em nova janela (popup) | ➖ | — | Avaliar *Bubbles* (Android 11+) e suporte a multi-janela | F8 | P3 |
| CON-11 | Contatos | ✅ | `GET /usuario/contatos` funciona | Reescrever em Compose; usar `avatar_url` | F1 | P0 |
| CON-12 | Deep link (abrir conversa pela notificação/âncora) | 🔴 | A notificação manda `conversa_id`; a `MainActivity` ignora (#14) | Navigation Compose com deep link `conversa/{id}?mensagem={id}` | F2/F5 | P0 |
| CON-13 | Conversa atualizada em tempo real (WS 40) | ⬜ | WS 40 não tem consumidor | Evento → refresh da lista | F1 | P0 |

## MSG — Exibição e leitura

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| MSG-01 | Carregamento + paginação bidirecional | 🟡 | Só as 50 últimas, sem "carregar anteriores" (#35) | Paging/manual com `mensagemreferencia`/`mensagensprevias`/`mensagensseguintes`; máximo 100 por chamada (doc 01 §10) | F2 | P0 |
| MSG-02 | Separadores de dia e nome do remetente em grupo | ⬜ | Hora some quando os segundos são `:00` (#29) | Cabeçalhos de dia; nome acima das bolhas no grupo | F2 | P0 |
| MSG-03 | Linha "Últimas" (não lidas) | ⬜ | — | Mesmas 5 regras do web (doc 03, MSG-03) | F2 | P1 |
| MSG-04 | Marcar como visualizada | 🔴 | `GET mensagem/visualizar` → **404** (doc 02 Q4); uma chamada por mensagem | `POST /mensagem/visualizar {conversa, mensagem}` só para as visíveis, com o app em primeiro plano | F2 | P0 |
| MSG-05 | "Ir para o final" / "Há novas mensagens" | ⬜ | — | FAB com contador | F2 | P1 |
| MSG-06 | Ir para uma mensagem (contexto) | ⬜ | — | Carregar contexto 30/30 (fallback 120/120) + destaque | F4 | P1 |
| MSG-07 | Classificação das bolhas (oculta, chamada, imagem, figurinha, código, emoji, referência, curta, padrão) | 🟡 | Só texto, imagem, áudio e arquivo; **tipos 5, 6 e 7 aparecem vazios** | Mesma prioridade de classificação (`classificarMensagem.ts`) | F2–F4 | P0 |
| MSG-08 | Ícone de status (enviando → ✓ → ✓✓ → ✓✓ azul) | 🟡 | Calculado só no carregamento; WS 3 ignorado | Atualização via WS 3 | F2 | P0 |
| MSG-09 | Detalhe do status (quem recebeu/viu/ouviu) | ⬜ | — | Bottom sheet; **atenção:** campos são datas aqui e booleanos em `/mensagens` | F4 | P2 |
| MSG-10 | Status em tempo real (WS 3 → `GET /mensagem/status`) | ⬜ | `onStatusMensagemAtualizado` nunca é atribuído | Inclui o `excluida_em` de mensagem ocultada em outro aparelho | F2 | P0 |
| MSG-11 | Links clicáveis | ⬜ | Não há tratamento explícito (a confirmar no layout) | `LinkAnnotation` no Compose com as mesmas regras de pontuação | F2 | P1 |
| MSG-12 | Menções `@[Nome](id)` | ⬜ | Aparece o texto cru | Renderizar `@Nome` clicável → conversa direta | F4 | P1 |
| MSG-13 | Mensagem de chamada (tipo 6, JSON) | ⬜ | Aparece vazia | Bolha "Chamada de áudio/vídeo" com duração e status | F2 | P0 |
| MSG-14 | Mensagem só de emojis (fonte grande) | ⬜ | — | Detectar com `BreakIterator`/regex de emoji | F2 | P2 |
| MSG-15 | Blocos de código (destaque, copiar, recolher, Markdown, Mermaid) | ⬜ | Aparecem os ``` crus | Parser idêntico ao web (`codeBlocks.ts`). Destaque: lib leve ou WebView com highlight.js. Markdown: Markwon/compose-markdown. Mermaid: WebView offline | F4 | P1 (código/markdown), P2 (mermaid) |
| MSG-16 | Mensagem oculta ("Mensagem oculta", toque para revelar) | 🔴 | **Mostra o conteúdo original** (doc 02 Q6) | Ler `excluida_em`; bolha própria — 🆕 `eaa8bac`: votação oculta revelada mostra o resumo (pergunta + votos) | F2 | **P0** |
| MSG-17 | Selo de mensagem agendada | ⬜ | — | "Agendada para hoje HH:MM / amanhã / dd/MM" | F4 | P2 |
| MSG-18 | Modo conexão lenta | ➖ | — | Usar `ConnectivityManager` (rede limitada/economia de dados) → "Toque para carregar" | F3 | P3 |
| MSG-19 | Citação de resposta/encaminhada (recursiva até 5) | ⬜ | — | Componente recursivo | F4 | P1 |
| MSG-20 🆕 | Bolha de votação (tipo 8): pergunta, opções com barra, votantes, votar/trocar/tirar voto, tempo real (WS 62) | ⬜ | O app antigo mostra a bolha **vazia** | `GET /enquete?id=` com cache por id; `POST /enquete/votar` com a lista completa; reler no WS 62 se estiver na tela. **No F2, pelo menos um placeholder "📊 Votação"** — 🆕 `5cad911`/`785bdef`: data final, encerrar (`POST /enquete/encerrar`), mudar a data (`PATCH /enquete`), estado encerrado com 🏆 e prazo vencendo na tela | F4 (placeholder F2) | P1 (placeholder P0) |

## ENV — Composição e ações

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| ENV-01 | Enviar texto (otimista) | 🟡 | Apaga o texto antes de enviar e perde se falhar (#35) | Envio otimista com id negativo + fila (WorkManager) e reenvio | F2 | P0 |
| ENV-02 | Atalhos de emoji (`:)` → 🙂) | ⬜ | — | Mesma tabela (`emojiAtalhos.ts`) | F4 | P3 |
| ENV-03 | Seletor de emoji | ➖ | — | O teclado do sistema já cobre; opcional `EmojiPicker` do androidx | F4 | P3 |
| ENV-04 | Figurinhas Lottie (tipo 7, `pacote/nome`) | ⬜ | — | **Embutir** os JSON em `assets/figurinhas/` (o servidor não serve; doc 08 S5); `lottie-compose` | F4 | P1 |
| ENV-05 | Menções no campo (`@` → lista → `@[Nome](id)`) | ⬜ | — | Popup de sugestões acima do teclado | F4 | P1 |
| ENV-06 | Responder | ⬜ | Código morto envia `referencia` (errado; o servidor espera `mensagem_referencia`) | Gesto de deslizar para responder + barra acima do campo | F4 | P0 |
| ENV-07 | Responder no privado | ⬜ | — | Igual ao web (encaminhada com `tipo:2`) | F4 | P2 |
| ENV-08 | Encaminhar | ⬜ | — | Tela de destino (conversas + contatos) | F4 | P1 |
| ENV-09 | Colar texto longo → janela de código | ➖ | — | Detectar colagem grande (`onPaste` via `ContentReceiver`) e sugerir "enviar como código" | F4 | P3 |
| ENV-10 | Janela "Inserir código" | ➖ | — | Tela simples com seletor de linguagem e monoespaçado (sem CodeMirror) | F4 | P3 |
| ENV-11 | Colar imagem | ➖ | — | `contentReceiver` (Compose) para imagens do teclado e da área de transferência | F3 | P2 |
| ENV-12 | Atalhos de teclado | ➖ | — | Só suporte básico a teclado físico (Enter envia é opcional) | — | P3 |
| ENV-13 | Agendar mensagem (`visivel_em`) | ⬜ | Modelo morto tem o campo | Toque longo no "Enviar" → seletor de data e hora; mesmas validações (≥ 5 min, ≤ 1 ano) | F4 | P2 |
| ENV-14 | Cancelar agendada | ⬜ | — | "Ocultar" vira "Cancelar envio" | F4 | P2 |
| ENV-15 | Digitando (enviar e receber) | 🟡 | **Não commitado**; throttle de 2,5 s; recebimento sem expirar corretamente; textos de grupo ausentes | Throttle de 2,5 s; expira em 4 s; textos exatos do web | F2 | P1 |
| ENV-16 | Gravando áudio (indicador) | ⬜ | `broadcastGravando` não é usado; WS 5 sem efeito | `POST /conversa/gravando` a cada 2,5 s durante a gravação | F3 | P1 |
| ENV-17 | Reações (toggle, chips, quem reagiu) | ⬜ | Só no repositório morto | Barra de reações no menu de toque longo; WS 7 — 🆕 `d4435db`/`eaa8bac`: até 5 emojis diferentes por pessoa (conferir antes da otimista); 5 chips à mostra e "+N" | F4 | P0 |
| ENV-18 | Ocultar mensagem (com confirmação) | ⬜ | `DELETE /mensagem` está só no repositório morto | Ação "Ocultar" com o texto exato do web | F4 | P0 |
| ENV-19 | Copiar (texto/imagem) | ⬜ | — | `ClipboardManager` (texto) e `ClipData` com URI de conteúdo (imagem) | F4 | P1 |
| ENV-20 | Menu de ações da mensagem | ⬜ | — | Toque longo → reações + Responder, Responder no privado, Encaminhar, Copiar, Ocultar | F4 | P0 |
| ENV-21 🆕 | Campo de mensagem rico (texto e anexos/figurinhas/menções intercalados, enviados na ordem) | ⬜ | — | Editor por blocos (texto ↔ peça) acima do teclado; figurinha/gravação com o campo vazio vão na hora. Ver a decisão FC-416 | F3 | P1 |
| ENV-22 🆕 | Criar votação (só grupo): pergunta ≤ 300, 2–12 opções ≤ 200, "Permitir várias escolhas" | ⬜ | — | Bottom sheet "Nova votação" a partir do "+"; `PUT /enquete` — 🆕 `785bdef`: "Definir data final" opcional (`encerra_em`) | F4 | P1 |

## ANX — Anexos e mídia

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| ANX-01 | Fila de anexos (vários, prévia, remover) — 🆕 no web foi **substituída pelo ENV-21** | ⬜ | Só uma imagem por vez | Photo Picker múltiplo + SAF (`OpenMultipleDocuments`) + fila com prévia | F3 | P0 |
| ANX-02 | Upload com dedupe SHA-256 e URL assinada + progresso | 🔴 | Manda bytes em `PUT /anexo` (doc 02 Q2); lê tudo em memória (#27) | `PUT /anexo` (JSON) → `PUT` na URL do MinIO (em até 300 s) → `POST /anexo/confirmar?identificador=`; hash em streaming; WorkManager com progresso | F3 | **P0** |
| ANX-03 | Tipo de conteúdo por arquivo | 🟡 | Imagem = 2 e áudio = 4. **A gravação do microfone deveria ser 5** (o web usa 5) | Mapear igual ao web; vídeo = 3 com extensão | F3 | P0 |
| ANX-04 | Visualizador de imagens/vídeos (galeria, zoom, legenda) | 🟡 | `ImageViewerActivity` existe, mas o download está quebrado (Q3) e não há galeria/vídeo | Pager com zoom (Telephoto/Zoomable) + Media3 para vídeo | F3 | P0 |
| ANX-05 | Vídeo na conversa (prévia + play) | ⬜ | — | Miniatura do primeiro quadro (Coil video frame) + player | F3 | P1 |
| ANX-06 | Visualizador de PDF | ⬜ | — | `PdfRenderer` nativo (ou `androidx.pdf`) | F3 | P2 |
| ANX-07 | HTML isolado | ⬜ | — | WebView com JavaScript **sem** acesso a arquivos/cookies, sem token; ou abrir externamente | F3 | P3 |
| ANX-08 | Markdown/Mermaid em blocos | ⬜ | — | Ver MSG-15 | F4 | P2 |
| ANX-09 | Download | 🔴 | Trata `{url}` como arquivo (Q3); scoped storage/path traversal (#33) | `GET /anexo?identificador=` → `{url}` → baixar a URL assinada → `MediaStore.Downloads`; nome sanitizado | F3 | P0 |
| ANX-10 | Player de áudio (um por vez, seek, "não ouvido" verde, `POST /mensagem/reproduzir`) | 🔴 | Download quebrado (Q3); vazamentos (#31); não chama `reproduzir` | Media3 único + estado por mensagem | F3 | P0 |
| ANX-11 | Gravação de áudio (segurar para enviar, travar, pausar, ouvir antes) | 🟡 | Grava M4A e funciona localmente; o envio quebra (Q2); tipo errado (4) | Reaproveitar `AudioRecorderHelper` (#48); UI de segurar/travar | F3 | P0 |
| ANX-12 | Transcrição de áudio | ⬜ | — | `PUT/GET /anexo/transcricao`; polling de 3 s enquanto "Processando" | F3 | P1 |
| ANX-13 | Página de Anexos (filtros, grade, paginação) | ⬜ | — | Tela "Anexos" por conversa (acessada pelo perfil/grupo) — 🆕 `eaa8bac`: no web, a lista também fica no painel do grupo | F7 | P2 |
| ANX-14 | URLs assinadas e renovação | ⬜ | — | URL expira (600 s em `/anexos`, 300 s no upload): renovar ao falhar; Coil com chave = identificador | F3 | P0 |
| ANX-15 | Pré-visualização de imagem antes de enviar | ➖ | No web é código sem gatilho | A fila (ANX-01) já mostra a prévia | — | P3 |

## PES — Pesquisa

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| PES-01 | Pesquisa na conversa | ⬜ | — | Ícone de busca no topo do chat → resultados → MSG-06 | F7 | P1 |
| PES-02 | Pesquisa em todos os chats | ⬜ | — | Busca global com resultados agrupados e termo destacado | F7 | P1 |

## CHA — Chamadas

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| CHA-01 | Iniciar chamada de voz/vídeo | 🔴 | Sem TURN → **sem mídia** (Q1); usa `/conversa/dados`, que não existe (Q5); não manda `conversa_id` | `GET /ice` antes de cada PeerConnection; usar `/conversa/usuarios`; incluir-se em `usuarios` | F6 | P0 |
| CHA-02 | Iniciar compartilhando a tela | ⬜ | — | `MediaProjection` + FGS `mediaProjection` | F8 | P3 |
| CHA-03 | Chamada recebida (toque, notificação, modal, 30 s) | 🟡 | Toque é calado pelo full-screen intent (#7); sem timeout de 30 s; sem `nao_atendeu` | Core-Telecom + CallStyle; timeout de 30 s → `recusar {nao_atendeu:true}` | F6 | P0 |
| CHA-04 | Atender (com fallback sem mídia; "Atender só assistindo") | 🔴 | Atender pela notificação não funciona no Android 12+ (#3); eventos perdidos (#1) | `PendingIntent.getActivity` / Telecom `answer()` | F6 | P0 |
| CHA-05 | Recusar | 🟡 | Recusar pela notificação dispara `recusar` **e** `sair` (#8) | Um único caminho | F6 | P0 |
| CHA-06 | Ocupado → recusa automática com `nao_atendeu` | ⬜ | — | No `CallManager` | F6 | P0 |
| CHA-07 | Atendida/recusada em outro aparelho (WS 53/54 com o meu id) | ⬜ | — | Parar toque e fechar a UI | F6 | P0 |
| CHA-08 | Chamadas pendentes ao conectar (> 25 s → perdida) | 🔴 | NPE porque `usuarios` não vem (Q9) | Modelo correto + regra dos 25 s | F6 | P0 |
| CHA-09 | Cancelar enquanto chama | 🔴 | Usa `/chamada/sair` em vez de `/chamada/cancelar` | `POST /chamada/cancelar` | F6 | P0 |
| CHA-10 | Entrada/saída dos outros, fim, sincronização a cada 4 s | 🔴 | #1/#2: eventos se perdem; a próxima chamada não toca | `SharedFlow` multi-assinante + `CallManager` de escopo de app + sync periódico | F6 | P0 |
| CHA-11 | Controles (mic, câmera, alto-falante, sair…) | 🟡 | Ícones semanticamente errados; mute não reativo (#25, #40) | Controles Compose acessíveis + seletor de rota de áudio (Bluetooth/fone/alto-falante) — 🆕 cores como no web: mic/câmera/som **vermelhos quando desligados**; tela/chat/ponteiro **azuis quando ligados**; 🆕 `eaa8bac`: o web trocou as cores por uma chave liga/desliga em cada botão (adaptação: no Android, ícone + cor + estado lido pelo TalkBack) | F6 | P0 |
| CHA-12 | Modos de exibição (grade, destaque, tela única) | 🟡 | Grade/PiP só no código **staged**; vídeo remoto nunca chega (#10) | Grade adaptativa + destaque | F6 | P1 |
| CHA-13 | Janela flutuante / barra de chamada | 🟡 | `CallBanner` existe | **Picture-in-Picture** + banner "voltar para a chamada" | F6 | P1 |
| CHA-14 | Adicionar participante | ⬜ | Botão sempre desabilitado | `PUT /chamada/usuario` | F6 | P1 |
| CHA-15 | Compartilhar tela com áudio do sistema | ⬜ | — | `MediaProjection` + `AudioPlaybackCapture` (API 29+) | F8 | P3 |
| CHA-16 | Troca áudio → vídeo (WS 56 + modal "Apenas assistir / Transmitir também", 15 s) | 🟡 | Envia o upgrade; não trata o 56 recebido (flow sem coletor) | Igual ao web | F6 | P1 |
| CHA-17 | Ponteiro remoto (WS 57 `ponteiro`) | ⬜ | — | Desenhar o ponteiro sobre a tela compartilhada recebida (enviar é opcional no celular) | F8 | P2 |
| CHA-18 | Chat da chamada (`PUT /chamada/chat`, WS 57 `chat`) | ⬜ | — | Painel lateral/inferior — 🆕 `eaa8bac`: o grupo é criado no primeiro toque no campo (não mais na primeira mensagem) | F6 | P2 |
| CHA-19 | Indicador de fala | ⬜ | — | `AudioTrackSink` (RMS > 0,02; segura 400 ms) ou estatísticas `audioLevel` do WebRTC | F6 | P2 |
| CHA-20 | Qualidade da chamada (ruído, eco, ganho, bitrate, resolução, fps) | ⬜ | — | Tela de configurações de chamada | F7 | P2 |
| CHA-21 | Somente recepção | ⬜ | — | Permitir entrar sem permissão de mic/câmera | F6 | P2 |
| CHA-22 | Histórico de chamadas (abas, filtros, "ligar de novo") | 🔴 | Modelo não bate com `GET /chamadas` (Q10) | Reescrever com o modelo `ChamadaHistoricoItem` (doc 01 §9.7); portar a lógica "Hoje/Ontem" | F6 | P0 |
| CHA-23 | Áudio remoto estável | ➖ | WebRTC nativo toca os tracks remotos por padrão | Garantir que o áudio sobreviva a PiP/troca de tela | F6 | P0 |
| CHA-24 🆕 | Chat **completo** da chamada (o mesmo chat, com anexos, respostas, reações), conversa do grupo da chamada como ativa | ⬜ | — | Reusar a `ChatScreen` num painel/bottom sheet durante a chamada — 🆕 `eaa8bac`: abre já no primeiro toque no campo | F6 | P2 |
| **Gravação** | Publicar vídeo em **H264/VP9** (VP8 não é gravado) | 🔴 | Preferência de codec não é configurada (Q12) | `setCodecPreferences` H264 → VP9 → VP8 | F6 | P0 |

## SIP — Telefonia (ramal)

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| SIP-01 | Configuração do ramal | ⬜ | Repositório morto | Tela igual à do web | F8 | P2 |
| SIP-02 | Registro automático | ⬜ | — | **Decisão técnica pendente:** biblioteca SIP nativa (Linphone SDK ou PJSIP) sobre WebSocket/WSS, ou adiar | F8 | P3 |
| SIP-03 | Discador com DTMF | ⬜ | — | Depende de SIP-02 | F8 | P3 |
| SIP-04 | Chamada SIP recebida | ⬜ | — | Depende de SIP-02; integra com Core-Telecom | F8 | P3 |
| SIP-05 | ICE do SIP via `/ice` | ⬜ | — | Depende de SIP-02 | F8 | P3 |

## NOT — Notificações

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| NOT-01 | Som de mensagem (exceto conversa aberta e arquivadas) | 🟡 | Toca; não respeita as arquivadas (não existem) | Canal de notificação; supressão na conversa aberta | F5 | P0 |
| NOT-02 | Notificação por conversa (agrupada, avatar, resposta direta) | 🔴 | Com o payload real, tudo cai na "conversa 0" (#14) | MessagingStyle + RemoteInput + atalhos de conversa (Conversation shortcuts) | F5 | P0 |
| NOT-03 | Fechar a notificação ao ler ou arquivar | ⬜ | — | Cancelar por `conversa_id` | F5 | P1 |
| NOT-04 | Push em segundo plano (FCM) | 🔴 | Inoperante (#5) e formato divergente (Q7) | `google-services.json`, plugin, `data {titulo, mensagem, conversa}` | F5 | P0 |
| NOT-05 | Notificação de chamada | 🟡 | CallStyle existe; atender quebrado (#3); toque calado (#7) | Core-Telecom + CallStyle | F6 | P0 |
| NOT-06 | Pedido de permissão | ✅ | Splash pede `POST_NOTIFICATIONS` etc. | Pedir no contexto (ao entrar, ao ligar), não tudo de uma vez | F1 | P0 |

## PRE — Presença

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| PRE-01 | Online/offline (`GET /contatos/online` + WS 60) | 🔴 | O app **mostra "online" fixo** (informação falsa) | Bolinha verde real na lista, no cabeçalho e nos contatos. Só existe online/offline (não há "visto por último") | F1 | P0 |

## ATV — Atividades

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| ATV-01 | Tela de atividades | ⬜ | — | Aba "Atividades" com paginação | F7 | P1 |
| ATV-02 | Contador de novas (WS 61) | ⬜ | — | Badge na navegação inferior | F7 | P1 |
| ATV-03 | Gerar chamada perdida (`nao_atendeu: true`) | ⬜ | — | **Obrigatório** no `CallManager` (timeout, ocupado, pendente > 25 s) | F6 | P0 |

## CFG — Configurações e administração

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| CFG-01 | Abas de configurações | ⬜ | Só existe "ConfigApi" (URL do servidor) | Tela de Configurações com seções | F7 | P1 |
| CFG-02 | Tema escuro | 🟡 | Segue o sistema (DayNight); sem escolha manual | Sistema / Claro / Escuro (+ Material You opcional) | F7 | P2 |
| CFG-03 | Dispositivos (sessão atual, dispositivos de mídia) | ➖ | — | Mostrar modelo e versão; rotas de áudio disponíveis | F7 | P3 |
| CFG-04 | Permissões | 🟡 | Pedidas na Splash | Tela com o estado de cada permissão + atalho para as Configurações do sistema (inclui full-screen intent e otimização de bateria) | F7 | P1 |
| CFG-05 | Cores personalizadas | ➖ | — | Avaliar: cor primária/Material You. Não replicar o editor completo | F8 | P3 |
| CFG-06 | Qualidade das chamadas | ⬜ | — | = CHA-20 | F7 | P2 |
| CFG-07 | Sistema — parâmetros do servidor (permissão `parametros`) | ⬜ | — | Tela administrativa (FCM, TURN, gravações, transcritor) | F7 | P2 |
| CFG-08 | Acessos — permissões (permissão `permissoes`) | ⬜ | — | Tela administrativa usuário × permissão | F7 | P2 |

## GER — UX geral

| ID | Funcionalidade | Android | Situação / evidência | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|
| GER-01 | Erro global | 🟡 | ~65 `Toast`s com mensagem técnica (#52) | Snackbar com mensagens amigáveis | F0 | P0 |
| GER-02 | Faixa "sem conexão em tempo real" | ⬜ | Só no texto da notificação do serviço | Banner com o estado do WS | F1 | P1 |
| GER-03 | Diálogo de confirmação próprio | 🟡 | `AlertDialog` ad hoc | Componente padrão (variante perigo) | F0 | P1 |
| GER-04 | Arrastar e soltar arquivos | ➖ | — | **Receber compartilhamento** (`ACTION_SEND`/`SEND_MULTIPLE`) de outros apps | F3 | P1 |
| GER-05 | Menu de contexto | ➖ | — | Toque longo | — | — |
| GER-06 | Tela "Em desenvolvimento" | ➖ | — | Não replicar | — | — |
| GER-07 | Avatares com fallback (inicial, inclusive emoji) | ⬜ | Ícone genérico | Componente `Avatar` com inicial (grafema) e cor | F0 | P0 |

---

## Requisitos exclusivos do Android (sem equivalente no web)

| ID | Requisito | Por quê | Fase | Prio |
|---|---|---|---|---|
| AND-01 | Configurar URL do servidor (com teste de conexão) | O app não tem "origem" como o web; WS e WebRTC derivam da mesma base (`/api`, `/ws/`, `/webrtc`) | F0 | P0 |
| AND-02 | Confiar na CA do mkcert em debug via `network_security_config` (sem trust-all) | Desenvolvimento local com HTTPS | F0 | P0 |
| AND-03 | Core-Telecom (chamadas no sistema: Bluetooth, carro, relógio, GSM concorrente) | Padrão do Android para VoIP | F6 | P0 |
| AND-04 | Rotas de áudio (fone, alto-falante, Bluetooth SCO/LE, fone com fio) + foco de áudio + restauração do modo | #21 | F6 | P0 |
| AND-05 | Sensor de proximidade só com o fone do aparelho | #21 | F6 | P1 |
| AND-06 | Picture-in-Picture na chamada de vídeo | Equivalente à janela flutuante | F6 | P1 |
| AND-07 | WebSocket só em primeiro plano + FCM como canal em segundo plano (sem FGS permanente) | Política do Play e bateria (#15) | F5 | P0 |
| AND-08 | Resposta direta e "marcar como lida" pela notificação | Padrão do Android | F5 | P0 |
| AND-09 | Atalhos de conversa (Conversation shortcuts) e notificações de conversa | Seção "Conversas" do Android 11+ | F5 | P2 |
| AND-10 | Receber compartilhamento de outros apps | Equivale a arrastar e soltar | F3 | P1 |
| AND-11 | Cache offline (Room) e envio em fila (WorkManager) | Rede móvel instável | F2 | P0 |
| AND-12 | Edge-to-edge e back preditivo (`targetSdk 35+`) | Exigência da plataforma | F0 | P0 |
| AND-13 | Pedido de full-screen intent (Android 14+) e isenção de otimização de bateria (explicado) | Chamadas com a tela bloqueada | F6 | P0 |
| AND-14 | Retomada após reiniciar o aparelho | Depende do FCM; sem FGS permanente | F5 | P1 |
| AND-15 | Acessibilidade (TalkBack, alvos de 48 dp, contraste) | #25 | todas | P0 |
| AND-16 | Strings em `strings.xml` (i18n) | #52 | todas | P1 |
