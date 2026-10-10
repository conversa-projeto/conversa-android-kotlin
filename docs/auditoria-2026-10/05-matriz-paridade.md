# 05 — Matriz de paridade Web × Android

**Base:** inventário do web (doc 03, 132 funcionalidades) × auditoria do Android (doc 04) × contrato atual (docs 01 e 02).
**Data:** 2026-10-06 (coluna "Legado") · 🆕 coluna "Nova base" em 2026-10-09.

## Legenda

### Coluna "Legado": o app antigo, como estava na auditoria (2026-10-06)

| Símbolo | Significado no Android **legado** |
|---|---|
| ✅ | Existe e funciona contra o servidor atual |
| 🟡 | Existe parcialmente ou com defeitos relevantes |
| 🔴 | Existe, mas **quebra contra o servidor atual** ou tem defeito crítico |
| ⬜ | Não existe |
| ➖ | Não se aplica literalmente ao Android; precisa de **adaptação** (veja a coluna "Adaptação") |

### 🆕 Coluna "Nova base": o app novo (`TODO.md`, branch `reescrita`)

| Símbolo | Significado na **nova base** |
|---|---|
| ✅ | Pronto: as linhas do `TODO.md` estão marcadas. A célula traz a seção do TODO e, quando há, o commit. Uma nota "falta…" indica um teste que o emulador não permite |
| 🟡 | Pronto em parte; a célula diz o que falta |
| ⛔ | Bloqueado por algo fora do app: Firebase, servidor ou uma decisão de produto |
| ⬜ | Ainda não feito (etapa futura ou fora do TODO) |
| ➖ | Não se aplica ou foi decidido não fazer |

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

### Legado (2026-10-06)

| Grupo | Total | ✅ | 🟡 | 🔴 | ⬜ | ➖ |
|---|---|---|---|---|---|---|
| AUT | 10 | 0 | 2 | 1 | 7 | 0 |
| CON | 13 | 1 | 4 | 1 | 6 | 1 |
| MSG | 20 | 0 | 3 | 2 | 14 | 1 |
| ENV | 23 | 0 | 2 | 0 | 16 | 5 |
| ANX | 15 | 0 | 3 | 3 | 8 | 1 |
| PES | 2 | 0 | 0 | 0 | 2 | 0 |
| CHA | 24 | 0 | 6 | 6 | 11 | 1 |
| SIP | 5 | 0 | 0 | 0 | 5 | 0 |
| NOT | 6 | 1 | 2 | 2 | 1 | 0 |
| PRE | 1 | 0 | 0 | 1 | 0 | 0 |
| ATV | 3 | 0 | 0 | 0 | 3 | 0 |
| CFG | 8 | 0 | 2 | 0 | 4 | 2 |
| GER | 7 | 0 | 2 | 0 | 2 | 3 |
| **Total** | **137** | **2** | **26** | **16** | **79** | **14** |

> A linha extra "Gravação" (codec), na tabela CHA, não entra na contagem.
>
> 🆕 **Atualizado em 2026-10-06 (noite)** com os commits `8031fa5` (servidor) e `39d06f9` (web): +4 funcionalidades (ENV-21, ENV-22, MSG-20, CHA-24), todas ⬜ no Android.
>
> 🆕 **Atualizado em 2026-10-08** com os commits `d4435db` e `5cad911` (servidor) e `eaa8bac` e `785bdef` (web): nenhuma funcionalidade nova; mudam CON-08, MSG-16, MSG-20, ENV-17, ENV-22, ANX-13, CHA-11, CHA-18 e CHA-24 (coluna "Adaptação"). A coluna "Android" continua sendo a do app **legado**; o andamento da nova base está no `TODO.md`.

> 🆕 **Atualizado em 2026-10-08 (noite)** com os commits do web `7322e83` e `e8d82cb` (o do servidor, `779c8ed`, só mexe no script de desenvolvimento): +1 funcionalidade (ENV-23, rascunho por conversa); mudam MSG-17, MSG-19, ENV-13, ENV-14 e ENV-20 (coluna "Adaptação").
>
> 🆕 **Atualizado em 2026-10-09:** nova coluna "Nova base" com o andamento do app novo (etapas 1–8) e o placar dela, logo abaixo. A coluna "Android" virou "Legado".
>
> **Leitura (legado):** só 2 de 137 funcionalidades estão íntegras. 42 existem de alguma forma (🟡+🔴), mas 16 delas quebram contra o servidor atual. 79 nunca existiram no Android. (🆕 2026-10-09: o placar ainda não contava o ENV-23, ⬜ no legado.)

### Nova base (2026-10-09, etapas 1–8 do `TODO.md`)

| Grupo | Total | ✅ | 🟡 | ⛔ | ⬜ | ➖ |
|---|---|---|---|---|---|---|
| AUT | 10 | 10 | 0 | 0 | 0 | 0 |
| CON | 13 | 11 | 1 | 0 | 1 | 0 |
| MSG | 20 | 20 | 0 | 0 | 0 | 0 |
| ENV | 23 | 21 | 0 | 1 | 0 | 1 |
| ANX | 15 | 15 | 0 | 0 | 0 | 0 |
| PES | 2 | 2 | 0 | 0 | 0 | 0 |
| CHA | 24 | 22 | 0 | 0 | 2 | 0 |
| SIP | 5 | 0 | 0 | 0 | 5 | 0 |
| NOT | 6 | 4 | 1 | 1 | 0 | 0 |
| PRE | 1 | 1 | 0 | 0 | 0 | 0 |
| ATV | 3 | 3 | 0 | 0 | 0 | 0 |
| CFG | 8 | 6 | 0 | 0 | 1 | 1 |
| GER | 7 | 6 | 0 | 0 | 0 | 1 |
| **Total** | **137** | **121** | **2** | **2** | **9** | **3** |

> Requisitos exclusivos do Android (AND, fora da contagem): 16 — 13 ✅, 2 🟡, 1 ⛔, 0 ⬜, 0 ➖.
>
> **Leitura:** 121 de 137 funcionalidades estão prontas na nova base, e 2 estão em parte.
> - **Bloqueadas (⛔):** dependem de algo fora do app: o push (Firebase, NOT-04) ou a decisão do campo rico (FC-416).
> - **Por fazer (⬜):** a etapa 9 (SIP, compartilhar tela, Bubbles) e um item P3 fora do TODO.
> - **Falta conferir:** as células com "falta conferir" ou "em aparelho" estão prontas, mas esperam um teste que o emulador não permite.

---

## AUT — Autenticação, sessão, perfil

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| AUT-01 | Login | 🟡 | ✅ 2.1 | Funciona, mas salva **senha em texto puro** e refaz o login a cada abertura (#6, #44); `dispositivo_id = null` (`LoginActivity.kt:102`) | Guardar só o token (Keystore); reenviar o `dispositivo_id` salvo | F1 | P0 |
| AUT-02 | Cadastro | ⬜ | ✅ 2.4 | Endpoint declarado, sem UI | Tela "Criar conta" | F1 | P1 |
| AUT-03 | Sessão resiliente (401 → login; outro erro → tenta de novo) | ⬜ | ✅ 1.8, 2.3 · falta conferir que o web não gera notificação depois do logout | Splash ignora o token e sempre manda para o Login; não há interceptor de 401 (#44) | `Authenticator`/interceptor + estado global de sessão | F1 | P0 |
| AUT-04 | Registro de dispositivo e token push | 🔴 | ✅ 2.2 | `PATCH /dispositivo` sem `id` → 400; FCM não configurado (#5; doc 02 Q8) | `PATCH /dispositivo {id,…}` após o login + `onNewToken` | F1/F5 | P0 |
| AUT-05 | Logout | 🟡 | ✅ 2.3 · falta conferir que o web não gera notificação depois do logout | Não para a chamada, não limpa notificações, cache nem token push (#45) | Logout central: limpa Room, DataStore, notificações e `token_fcm: null` | F1 | P0 |
| AUT-06 | Editar perfil (nome, e-mail) | ⬜ | ✅ 8.3 · `143d8d6` | Repositório morto | Tela de perfil | F7 | P1 |
| AUT-07 | Alterar senha | ⬜ | ✅ 8.3 · `143d8d6` | Código morto com campo errado (`senha_nova` em vez de `senha`) | Tela + validações iguais ao web (mínimo 6, confirmação) | F7 | P1 |
| AUT-08 | Avatar (enviar/remover) | ⬜ | ✅ 8.3 · `143d8d6` | — | Photo Picker + recorte quadrado 256×256 JPEG 85% + upload + `PATCH /usuario {avatar_anexo_id}` | F7 | P1 |
| AUT-09 | Permissões do sistema do usuário | ⬜ | ✅ 8.5 · `afd3269` | — | `GET /usuario/permissoes` → mostra/oculta as telas Sistema/Acessos | F7 | P2 |
| AUT-10 | Ver perfil de outro usuário | ⬜ | ✅ 8.4 · `4d53ba8`; "Ver anexos" no 8.6 (`95b9bb5`) | — | Bottom sheet com foto, nome, e-mail, telefone e "Ver anexos" | F7 | P1 |

## CON — Conversas

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| CON-01 | Lista de conversas (avatar, online, prévia, não lidas, fixadas, arquivadas, digitando) | 🟡 | ✅ 2.6 · a comparação lado a lado com o web não foi feita (o web não roda nesta máquina); as regras têm teste | Lista existe; **sem badge** (o layout tem, o adapter não preenche), sem avatar, sem fixadas/arquivadas, hora sempre `HH:mm`, recarrega tudo com spinner (#26, #47) | Compose `LazyColumn` alimentada pelo Room; atualização incremental | F1 | P0 |
| CON-02 | Filtro local + "Nova conversa" com contatos sem conversa | ⬜ | ✅ 2.7 | Só há a tela separada de Contatos | Busca na barra superior + seção "Nova conversa" | F1 | P1 |
| CON-03 | Fixar / desafixar / reordenar | ⬜ | ✅ 2.8 | — | Menu de toque longo + arrastar (ou "mover para cima/baixo"); `PATCH /conversa/fixadas` com a lista inteira | F1 | P1 |
| CON-04 | Arquivar / desarquivar | ⬜ | ✅ 2.8 · arquivada sem notificação na 5.4 (`199ce1b`) | — | Menu + gesto de deslizar; arquivadas sem som nem notificação; seção "Arquivadas (N)" | F1 | P1 |
| CON-05 | Menu de contexto da conversa | ⬜ | ✅ 2.8 | — | Toque longo → bottom sheet | F1 | P1 |
| CON-06 | Obter/criar conversa direta | 🟡 | ✅ 2.9 | Existe, mas não é atômico e não checa retornos (#46) | Caso de uso único; tratar falha parcial | F1 | P0 |
| CON-07 | Criar grupo | 🟡 | ✅ 2.10 | Existe em 2 telas; **descarta a descrição** (`detalhes_grupo_activity.kt:64`); não inclui o criador de forma garantida | Tela única: nome + seleção | F1 | P1 |
| CON-08 | Gerenciar membros (renomear, adicionar, remover) | ⬜ | 🟡 2.10 · remover outro membro: ⛔ o servidor só aceita a própria saída (403); os anexos do grupo vêm no 8.6 (`95b9bb5`) | Endpoints mortos | Tela "Membros do grupo" — 🆕 `eaa8bac`: o web juntou nome, participantes e anexos num painel "Dados do grupo" aberto pelo avatar do grupo; no Android, abrir pelo cabeçalho do chat e mostrar os anexos do grupo na mesma tela | F1 | P1 |
| CON-09 | Cabeçalho (avatar, online real, membros, digitando, botões) | 🟡 | ✅ 3.1 | Mostra **"online" fixo, que é falso** (`ChatActivity.kt:137,1144`) | TopAppBar com presença real e subtítulo de membros/digitando | F2 | P0 |
| CON-10 | Abrir em nova janela (popup) | ➖ | ⬜ etapa 9 (Bubbles, avaliar) | — | Avaliar *Bubbles* (Android 11+) e suporte a multi-janela | F8 | P3 |
| CON-11 | Contatos | ✅ | ✅ 2.9 | `GET /usuario/contatos` funciona | Reescrever em Compose; usar `avatar_url` | F1 | P0 |
| CON-12 | Deep link (abrir conversa pela notificação/âncora) | 🔴 | ✅ 3.11 · `2a4cc59` | A notificação manda `conversa_id`; a `MainActivity` ignora (#14) | Navigation Compose com deep link `conversa/{id}?mensagem={id}` | F2/F5 | P0 |
| CON-13 | Conversa atualizada em tempo real (WS 40) | ⬜ | ✅ 2.6 | WS 40 não tem consumidor | Evento → refresh da lista | F1 | P0 |

## MSG — Exibição e leitura

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| MSG-01 | Carregamento + paginação bidirecional | 🟡 | ✅ 3.2 · ir para uma mensagem traz o caminho todo (7.5, `2a4cc59`) | Só as 50 últimas, sem "carregar anteriores" (#35) | Paging/manual com `mensagemreferencia`/`mensagensprevias`/`mensagensseguintes`; máximo 100 por chamada (doc 01 §10) | F2 | P0 |
| MSG-02 | Separadores de dia e nome do remetente em grupo | ⬜ | ✅ 3.6 | Hora some quando os segundos são `:00` (#29) | Cabeçalhos de dia; nome acima das bolhas no grupo | F2 | P0 |
| MSG-03 | Linha "Últimas" (não lidas) | ⬜ | ✅ 3.9 | — | Mesmas 5 regras do web (doc 03, MSG-03) | F2 | P1 |
| MSG-04 | Marcar como visualizada | 🔴 | ✅ 3.8 | `GET mensagem/visualizar` → **404** (doc 02 Q4); uma chamada por mensagem | `POST /mensagem/visualizar {conversa, mensagem}` só para as visíveis, com o app em primeiro plano | F2 | P0 |
| MSG-05 | "Ir para o final" / "Há novas mensagens" | ⬜ | ✅ 3.9 | — | FAB com contador | F2 | P1 |
| MSG-06 | Ir para uma mensagem (contexto) | ⬜ | ✅ 7.5 · `2a4cc59` | — | Carregar contexto 30/30 (fallback 120/120) + destaque | F4 | P1 |
| MSG-07 | Classificação das bolhas (oculta, chamada, imagem, figurinha, código, emoji, referência, curta, padrão) | 🟡 | ✅ 3.3 | Só texto, imagem, áudio e arquivo; **tipos 5, 6 e 7 aparecem vazios** | Mesma prioridade de classificação (`classificarMensagem.ts`) | F2–F4 | P0 |
| MSG-08 | Ícone de status (enviando → ✓ → ✓✓ → ✓✓ azul) | 🟡 | ✅ 3.8 | Calculado só no carregamento; WS 3 ignorado | Atualização via WS 3 | F2 | P0 |
| MSG-09 | Detalhe do status (quem recebeu/viu/ouviu) | ⬜ | ✅ 7.10 · `f3cab38` | — | Bottom sheet; **atenção:** campos são datas aqui e booleanos em `/mensagens` | F4 | P2 |
| MSG-10 | Status em tempo real (WS 3 → `GET /mensagem/status`) | ⬜ | ✅ 3.8 | `onStatusMensagemAtualizado` nunca é atribuído | Inclui o `excluida_em` de mensagem ocultada em outro aparelho | F2 | P0 |
| MSG-11 | Links clicáveis | ⬜ | ✅ 3.9 | Não há tratamento explícito (a confirmar no layout) | `LinkAnnotation` no Compose com as mesmas regras de pontuação | F2 | P1 |
| MSG-12 | Menções `@[Nome](id)` | ⬜ | ✅ 7.7 · `76a7357` | Aparece o texto cru | Renderizar `@Nome` clicável → conversa direta | F4 | P1 |
| MSG-13 | Mensagem de chamada (tipo 6, JSON) | ⬜ | ✅ 3.5 | Aparece vazia | Bolha "Chamada de áudio/vídeo" com duração e status | F2 | P0 |
| MSG-14 | Mensagem só de emojis (fonte grande) | ⬜ | ✅ 3.3 | — | Detectar com `BreakIterator`/regex de emoji | F2 | P2 |
| MSG-15 | Blocos de código (destaque, copiar, recolher, Markdown, Mermaid) | ⬜ | ✅ 7.9 · código `74ce225`, Markdown `26f4ea2`, Mermaid `cba9d66` | Aparecem os ``` crus | Parser idêntico ao web (`codeBlocks.ts`). Destaque: lib leve ou WebView com highlight.js. Markdown: Markwon/compose-markdown. Mermaid: WebView offline | F4 | P1 (código/markdown), P2 (mermaid) |
| MSG-16 | Mensagem oculta ("Mensagem oculta", toque para revelar) | 🔴 | ✅ 3.4 | **Mostra o conteúdo original** (doc 02 Q6) | Ler `excluida_em`; bolha própria — 🆕 `eaa8bac`: votação oculta revelada mostra o resumo (pergunta + votos) | F2 | **P0** |
| MSG-17 | Selo de mensagem agendada | ⬜ | ✅ 7.10 · `f3cab38` | — | "Agendada para hoje HH:MM / amanhã / dd/MM" — 🆕 `7322e83`: a agendada sai do chat; relógio com o número ao lado do microfone (campo vazio) e folha "Mensagens agendadas" (horário, resumo, "Cancelar") | F4 | P2 |
| MSG-18 | Modo conexão lenta | ➖ | ✅ 4.10 · `d9d03f1` | — | Usar `ConnectivityManager` (rede limitada/economia de dados) → "Toque para carregar" | F3 | P3 |
| MSG-19 | Citação de resposta/encaminhada (recursiva até 5) | ⬜ | ✅ 7.5 · `2a4cc59` | — | Componente recursivo — 🆕 `7322e83`: encaminhada de encaminhada não repete, na citação, o que a citação de baixo já mostra | F4 | P1 |
| MSG-20 🆕 | Bolha de votação (tipo 8): pergunta, opções com barra, votantes, votar/trocar/tirar voto, tempo real (WS 62) | ⬜ | ✅ 7.12 · `b6d8631` | O app antigo mostra a bolha **vazia** | `GET /enquete?id=` com cache por id; `POST /enquete/votar` com a lista completa; reler no WS 62 se estiver na tela. **No F2, pelo menos um placeholder "📊 Votação"** — 🆕 `5cad911`/`785bdef`: data final, encerrar (`POST /enquete/encerrar`), mudar a data (`PATCH /enquete`), estado encerrado com 🏆 e prazo vencendo na tela | F4 (placeholder F2) | P1 (placeholder P0) |

## ENV — Composição e ações

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| ENV-01 | Enviar texto (otimista) | 🟡 | ✅ 3.7 | Apaga o texto antes de enviar e perde se falhar (#35) | Envio otimista com id negativo + fila (WorkManager) e reenvio | F2 | P0 |
| ENV-02 | Atalhos de emoji (`:)` → 🙂) | ⬜ | ✅ 7.11 · `c2a31d6` | — | Mesma tabela (`emojiAtalhos.ts`) | F4 | P3 |
| ENV-03 | Seletor de emoji | ➖ | ✅ teclado do sistema; nas reações, o seletor do AndroidX (7.2) | — | O teclado do sistema já cobre; opcional `EmojiPicker` do androidx | F4 | P3 |
| ENV-04 | Figurinhas Lottie (tipo 7, `pacote/nome`) | ⬜ | ✅ 7.8 · `61d6a2c` | — | **Embutir** os JSON em `assets/figurinhas/` (o servidor não serve; doc 08 S5); `lottie-compose` | F4 | P1 |
| ENV-05 | Menções no campo (`@` → lista → `@[Nome](id)`) | ⬜ | ✅ 7.7 · `76a7357` | — | Popup de sugestões acima do teclado | F4 | P1 |
| ENV-06 | Responder | ⬜ | ✅ 7.3 · `cdfb8cf` | Código morto envia `referencia` (errado; o servidor espera `mensagem_referencia`) | Gesto de deslizar para responder + barra acima do campo | F4 | P0 |
| ENV-07 | Responder no privado | ⬜ | ✅ 7.6 · `19add2b` | — | Igual ao web (encaminhada com `tipo:2`) | F4 | P2 |
| ENV-08 | Encaminhar | ⬜ | ✅ 7.6 · `19add2b` | — | Tela de destino (conversas + contatos) | F4 | P1 |
| ENV-09 | Colar texto longo → janela de código | ➖ | ✅ 7.11 · `c2a31d6` | — | Detectar colagem grande (`onPaste` via `ContentReceiver`) e sugerir "enviar como código" | F4 | P3 |
| ENV-10 | Janela "Inserir código" | ➖ | ✅ 7.11 · `c2a31d6` | — | Tela simples com seletor de linguagem e monoespaçado (sem CodeMirror) | F4 | P3 |
| ENV-11 | Colar imagem | ➖ | ✅ 4.10 · `d9d03f1` | — | `contentReceiver` (Compose) para imagens do teclado e da área de transferência | F3 | P2 |
| ENV-12 | Atalhos de teclado | ➖ | ➖ não previsto (teclado físico) | — | Só suporte básico a teclado físico (Enter envia é opcional) | — | P3 |
| ENV-13 | Agendar mensagem (`visivel_em`) | ⬜ | ✅ 7.10 · `f3cab38` | Modelo morto tem o campo | Toque longo no "Enviar" → seletor de data e hora; mesmas validações (≥ 5 min, ≤ 1 ano) — 🆕 `7322e83`: depois de agendar, a mensagem vai para o relógio, não para o chat | F4 | P2 |
| ENV-14 | Cancelar agendada | ⬜ | ✅ 7.4, 7.10 · `f3cab38` | — | "Ocultar" vira "Cancelar envio" — 🆕 `7322e83`: pela folha "Mensagens agendadas" | F4 | P2 |
| ENV-15 | Digitando (enviar e receber) | 🟡 | ✅ 3.10 | **Não commitado**; throttle de 2,5 s; recebimento sem expirar corretamente; textos de grupo ausentes | Throttle de 2,5 s; expira em 4 s; textos exatos do web | F2 | P1 |
| ENV-16 | Gravando áudio (indicador) | ⬜ | ✅ 4.6 · `399bae5` | `broadcastGravando` não é usado; WS 5 sem efeito | `POST /conversa/gravando` a cada 2,5 s durante a gravação | F3 | P1 |
| ENV-17 | Reações (toggle, chips, quem reagiu) | ⬜ | ✅ 7.2 · `52d6d03` | Só no repositório morto | Barra de reações no menu de toque longo; WS 7 — 🆕 `d4435db`/`eaa8bac`: até 5 emojis diferentes por pessoa (conferir antes da otimista); 5 chips à mostra e "+N" | F4 | P0 |
| ENV-18 | Ocultar mensagem (com confirmação) | ⬜ | ✅ 7.4 · `52d6d03` | `DELETE /mensagem` está só no repositório morto | Ação "Ocultar" com o texto exato do web | F4 | P0 |
| ENV-19 | Copiar (texto/imagem) | ⬜ | ✅ 7.6 · `52d6d03` | — | `ClipboardManager` (texto) e `ClipData` com URI de conteúdo (imagem) | F4 | P1 |
| ENV-20 | Menu de ações da mensagem | ⬜ | ✅ 7.1 · `52d6d03` | — | Toque longo → reações + Responder, Responder no privado, Encaminhar, Copiar, Ocultar — 🆕 `7322e83`/`e8d82cb`: no celular o web também abre o menu completo no toque longo; Ctrl + clique direito (só reações) não se aplica | F4 | P0 |
| ENV-21 🆕 | Campo de mensagem rico (texto e anexos/figurinhas/menções intercalados, enviados na ordem) | ⬜ | ⛔ 4.11 · falta a decisão FC-416 (campo simplificado × completo); hoje: fila de anexos + texto, figurinha sozinha | — | Editor por blocos (texto ↔ peça) acima do teclado; figurinha/gravação com o campo vazio vão na hora. Ver a decisão FC-416 | F3 | P1 |
| ENV-22 🆕 | Criar votação (só grupo): pergunta ≤ 300, 2–12 opções ≤ 200, "Permitir várias escolhas" | ⬜ | ✅ 7.12 · `b6d8631` | — | Bottom sheet "Nova votação" a partir do "+"; `PUT /enquete` — 🆕 `785bdef`: "Definir data final" opcional (`encerra_em`) | F4 | P1 |
| ENV-23 🆕 | Rascunho por conversa (texto, menções, anexos e resposta pendente), restaurado ao voltar | ⬜ | ✅ 7.11 · `217be62` | — | Guardar no aparelho por conversa (Room) e restaurar ao abrir o chat; apagar ao enviar | F4 | P2 |

## ANX — Anexos e mídia

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| ANX-01 | Fila de anexos (vários, prévia, remover) — 🆕 no web foi **substituída pelo ENV-21** | ⬜ | ✅ 4.2 · `389e66d` | Só uma imagem por vez | Photo Picker múltiplo + SAF (`OpenMultipleDocuments`) + fila com prévia | F3 | P0 |
| ANX-02 | Upload com dedupe SHA-256 e URL assinada + progresso | 🔴 | ✅ 4.1 | Manda bytes em `PUT /anexo` (doc 02 Q2); lê tudo em memória (#27) | `PUT /anexo` (JSON) → `PUT` na URL do MinIO (em até 300 s) → `POST /anexo/confirmar?identificador=`; hash em streaming; WorkManager com progresso | F3 | **P0** |
| ANX-03 | Tipo de conteúdo por arquivo | 🟡 | ✅ 4.2 · `389e66d` | Imagem = 2 e áudio = 4. **A gravação do microfone deveria ser 5** (o web usa 5) | Mapear igual ao web; vídeo = 3 com extensão | F3 | P0 |
| ANX-04 | Visualizador de imagens/vídeos (galeria, zoom, legenda) | 🟡 | ✅ 4.4 · `0168bd6` | `ImageViewerActivity` existe, mas o download está quebrado (Q3) e não há galeria/vídeo | Pager com zoom (Telephoto/Zoomable) + Media3 para vídeo | F3 | P0 |
| ANX-05 | Vídeo na conversa (prévia + play) | ⬜ | ✅ 4.4 · `0168bd6` | — | Miniatura do primeiro quadro (Coil video frame) + player | F3 | P1 |
| ANX-06 | Visualizador de PDF | ⬜ | ✅ 4.10 · `d9d03f1` | — | `PdfRenderer` nativo (ou `androidx.pdf`) | F3 | P2 |
| ANX-07 | HTML isolado | ⬜ | ✅ 4.10 · `d9d03f1` | — | WebView com JavaScript **sem** acesso a arquivos/cookies, sem token; ou abrir externamente | F3 | P3 |
| ANX-08 | Markdown/Mermaid em blocos | ⬜ | ✅ = MSG-15 (7.9: Markdown `26f4ea2`, Mermaid `cba9d66`) | — | Ver MSG-15 | F4 | P2 |
| ANX-09 | Download | 🔴 | ✅ 4.7 | Trata `{url}` como arquivo (Q3); scoped storage/path traversal (#33) | `GET /anexo?identificador=` → `{url}` → baixar a URL assinada → `MediaStore.Downloads`; nome sanitizado | F3 | P0 |
| ANX-10 | Player de áudio (um por vez, seek, "não ouvido" verde, `POST /mensagem/reproduzir`) | 🔴 | ✅ 4.5 · `1ceaad0` | Download quebrado (Q3); vazamentos (#31); não chama `reproduzir` | Media3 único + estado por mensagem | F3 | P0 |
| ANX-11 | Gravação de áudio (segurar para enviar, travar, pausar, ouvir antes) | 🟡 | ✅ 4.6 · `399bae5` | Grava M4A e funciona localmente; o envio quebra (Q2); tipo errado (4) | Reaproveitar `AudioRecorderHelper` (#48); UI de segurar/travar | F3 | P0 |
| ANX-12 | Transcrição de áudio | ⬜ | ✅ 4.8 · `f5d1501` | — | `PUT/GET /anexo/transcricao`; polling de 3 s enquanto "Processando" | F3 | P1 |
| ANX-13 | Página de Anexos (filtros, grade, paginação) | ⬜ | ✅ 8.6 · `95b9bb5` | — | Tela "Anexos" por conversa (acessada pelo perfil/grupo) — 🆕 `eaa8bac`: no web, a lista também fica no painel do grupo | F7 | P2 |
| ANX-14 | URLs assinadas e renovação | ⬜ | ✅ 4.3 | — | URL expira (600 s em `/anexos`, 300 s no upload): renovar ao falhar; Coil com chave = identificador | F3 | P0 |
| ANX-15 | Pré-visualização de imagem antes de enviar | ➖ | ✅ a fila de anexos mostra a prévia (4.2) | No web é código sem gatilho | A fila (ANX-01) já mostra a prévia | — | P3 |

## PES — Pesquisa

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| PES-01 | Pesquisa na conversa | ⬜ | ✅ 8.2 · `28cef66` | — | Ícone de busca no topo do chat → resultados → MSG-06 | F7 | P1 |
| PES-02 | Pesquisa em todos os chats | ⬜ | ✅ 8.2 · `28cef66` | — | Busca global com resultados agrupados e termo destacado | F7 | P1 |

## CHA — Chamadas

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| CHA-01 | Iniciar chamada de voz/vídeo | 🔴 | ✅ 6.4 · `cf43b1f` | Sem TURN → **sem mídia** (Q1); usa `/conversa/dados`, que não existe (Q5); não manda `conversa_id` | `GET /ice` antes de cada PeerConnection; usar `/conversa/usuarios`; incluir-se em `usuarios` | F6 | P0 |
| CHA-02 | Iniciar compartilhando a tela | ⬜ | ⬜ etapa 9 (compartilhar a tela) | — | `MediaProjection` + FGS `mediaProjection` | F8 | P3 |
| CHA-03 | Chamada recebida (toque, notificação, modal, 30 s) | 🟡 | ✅ 6.5 | Toque é calado pelo full-screen intent (#7); sem timeout de 30 s; sem `nao_atendeu` | Core-Telecom + CallStyle; timeout de 30 s → `recusar {nao_atendeu:true}` | F6 | P0 |
| CHA-04 | Atender (com fallback sem mídia; "Atender só assistindo") | 🔴 | ✅ 6.5 | Atender pela notificação não funciona no Android 12+ (#3); eventos perdidos (#1) | `PendingIntent.getActivity` / Telecom `answer()` | F6 | P0 |
| CHA-05 | Recusar | 🟡 | ✅ 6.5 | Recusar pela notificação dispara `recusar` **e** `sair` (#8) | Um único caminho | F6 | P0 |
| CHA-06 | Ocupado → recusa automática com `nao_atendeu` | ⬜ | ✅ 6.6 · `ceeec72` | — | No `CallManager` | F6 | P0 |
| CHA-07 | Atendida/recusada em outro aparelho (WS 53/54 com o meu id) | ⬜ | ✅ 6.6 · `ceeec72` | — | Parar toque e fechar a UI | F6 | P0 |
| CHA-08 | Chamadas pendentes ao conectar (> 25 s → perdida) | 🔴 | ✅ 6.6 · `ceeec72` | NPE porque `usuarios` não vem (Q9) | Modelo correto + regra dos 25 s | F6 | P0 |
| CHA-09 | Cancelar enquanto chama | 🔴 | ✅ 6.2 (`GerenciadorChamadas`) · `ceeec72` | Usa `/chamada/sair` em vez de `/chamada/cancelar` | `POST /chamada/cancelar` | F6 | P0 |
| CHA-10 | Entrada/saída dos outros, fim, sincronização a cada 4 s | 🔴 | ✅ 6.7 · `ceeec72` | #1/#2: eventos se perdem; a próxima chamada não toca | `SharedFlow` multi-assinante + `CallManager` de escopo de app + sync periódico | F6 | P0 |
| CHA-11 | Controles (mic, câmera, alto-falante, sair…) | 🟡 | ✅ 6.9 · falta o teste com TalkBack em aparelho | Ícones semanticamente errados; mute não reativo (#25, #40) | Controles Compose acessíveis + seletor de rota de áudio (Bluetooth/fone/alto-falante) — 🆕 cores como no web: mic/câmera/som **vermelhos quando desligados**; tela/chat/ponteiro **azuis quando ligados**; 🆕 `eaa8bac`: o web trocou as cores por uma chave liga/desliga em cada botão (adaptação: no Android, ícone + cor + estado lido pelo TalkBack) | F6 | P0 |
| CHA-12 | Modos de exibição (grade, destaque, tela única) | 🟡 | ✅ 6.11 · `cf43b1f` | Grade/PiP só no código **staged**; vídeo remoto nunca chega (#10) | Grade adaptativa + destaque | F6 | P1 |
| CHA-13 | Janela flutuante / barra de chamada | 🟡 | ✅ 6.12 · `add5196` | `CallBanner` existe | **Picture-in-Picture** + banner "voltar para a chamada" | F6 | P1 |
| CHA-14 | Adicionar participante | ⬜ | ✅ 6.13 | Botão sempre desabilitado | `PUT /chamada/usuario` | F6 | P1 |
| CHA-15 | Compartilhar tela com áudio do sistema | ⬜ | ⬜ etapa 9 (compartilhar a tela) | — | `MediaProjection` + `AudioPlaybackCapture` (API 29+) | F8 | P3 |
| CHA-16 | Troca áudio → vídeo (WS 56 + modal "Apenas assistir / Transmitir também", 15 s) | 🟡 | ✅ 6.11 · `cf43b1f` | Envia o upgrade; não trata o 56 recebido (flow sem coletor) | Igual ao web | F6 | P1 |
| CHA-17 | Ponteiro remoto (WS 57 `ponteiro`) | ⬜ | ✅ 6.13 | — | Desenhar o ponteiro sobre a tela compartilhada recebida (enviar é opcional no celular) | F8 | P2 |
| CHA-18 | Chat da chamada (`PUT /chamada/chat`, WS 57 `chat`) | ⬜ | ✅ 6.13 | — | Painel lateral/inferior — 🆕 `eaa8bac`: o grupo é criado no primeiro toque no campo (não mais na primeira mensagem) | F6 | P2 |
| CHA-19 | Indicador de fala | ⬜ | ✅ 6.13 | — | `AudioTrackSink` (RMS > 0,02; segura 400 ms) ou estatísticas `audioLevel` do WebRTC | F6 | P2 |
| CHA-20 | Qualidade da chamada (ruído, eco, ganho, bitrate, resolução, fps) | ⬜ | ✅ 8.5 · `705ee2e` · vale a partir da próxima chamada | — | Tela de configurações de chamada | F7 | P2 |
| CHA-21 | Somente recepção | ⬜ | ✅ 6.13 | — | Permitir entrar sem permissão de mic/câmera | F6 | P2 |
| CHA-22 | Histórico de chamadas (abas, filtros, "ligar de novo") | 🔴 | ✅ 6.10 · `b952923` | Modelo não bate com `GET /chamadas` (Q10) | Reescrever com o modelo `ChamadaHistoricoItem` (doc 01 §9.7); portar a lógica "Hoje/Ontem" | F6 | P0 |
| CHA-23 | Áudio remoto estável | ➖ | ✅ 6.12 · `add5196` | WebRTC nativo toca os tracks remotos por padrão | Garantir que o áudio sobreviva a PiP/troca de tela | F6 | P0 |
| CHA-24 🆕 | Chat **completo** da chamada (o mesmo chat, com anexos, respostas, reações), conversa do grupo da chamada como ativa | ⬜ | ✅ 6.13 · `3a9f39d` | — | Reusar a `ChatScreen` num painel/bottom sheet durante a chamada — 🆕 `eaa8bac`: abre já no primeiro toque no campo | F6 | P2 |
| **Gravação** | Publicar vídeo em **H264/VP9** (VP8 não é gravado) | 🔴 | ✅ 6.1 · `cf43b1f` | Preferência de codec não é configurada (Q12) | `setCodecPreferences` H264 → VP9 → VP8 | F6 | P0 |

## SIP — Telefonia (ramal)

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| SIP-01 | Configuração do ramal | ⬜ | ⬜ etapa 9 (configuração do ramal) | Repositório morto | Tela igual à do web | F8 | P2 |
| SIP-02 | Registro automático | ⬜ | ⬜ etapa 9 · depende do spike Linphone × PJSIP (decisão pendente) | — | **Decisão técnica pendente:** biblioteca SIP nativa (Linphone SDK ou PJSIP) sobre WebSocket/WSS, ou adiar | F8 | P3 |
| SIP-03 | Discador com DTMF | ⬜ | ⬜ etapa 9 · depende do SIP-02 | — | Depende de SIP-02 | F8 | P3 |
| SIP-04 | Chamada SIP recebida | ⬜ | ⬜ etapa 9 · depende do SIP-02 | — | Depende de SIP-02; integra com Core-Telecom | F8 | P3 |
| SIP-05 | ICE do SIP via `/ice` | ⬜ | ⬜ etapa 9 · depende do SIP-02 | — | Depende de SIP-02 | F8 | P3 |

## NOT — Notificações

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| NOT-01 | Som de mensagem (exceto conversa aberta e arquivadas) | 🟡 | ✅ 5.4 · com o app aberto ou até 10 s em segundo plano; fechado, depende do push (NOT-04) | Toca; não respeita as arquivadas (não existem) | Canal de notificação; supressão na conversa aberta | F5 | P0 |
| NOT-02 | Notificação por conversa (agrupada, avatar, resposta direta) | 🔴 | 🟡 5.4 · falta o avatar como ícone; em segundo plano, depende do push (NOT-04) | Com o payload real, tudo cai na "conversa 0" (#14) | MessagingStyle + RemoteInput + atalhos de conversa (Conversation shortcuts) | F5 | P0 |
| NOT-03 | Fechar a notificação ao ler ou arquivar | ⬜ | ✅ 5.6, 2.8, 3.8 · `199ce1b` | — | Cancelar por `conversa_id` | F5 | P1 |
| NOT-04 | Push em segundo plano (FCM) | 🔴 | ⛔ 5.1 · precisa do projeto Firebase (`google-services.json`); para chamadas, também do S1 no servidor | Inoperante (#5) e formato divergente (Q7) | `google-services.json`, plugin, `data {titulo, mensagem, conversa}` | F5 | P0 |
| NOT-05 | Notificação de chamada | 🟡 | ✅ 6.5 · `ceeec72` · com o app fechado, depende do push (NOT-04) | CallStyle existe; atender quebrado (#3); toque calado (#7) | Core-Telecom + CallStyle | F6 | P0 |
| NOT-06 | Pedido de permissão | ✅ | ✅ 5.3 · e a tela Permissões (8.5, `ee1237c`) | Splash pede `POST_NOTIFICATIONS` etc. | Pedir no contexto (ao entrar, ao ligar), não tudo de uma vez | F1 | P0 |

## PRE — Presença

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| PRE-01 | Online/offline (`GET /contatos/online` + WS 60) | 🔴 | ✅ 2.11 | O app **mostra "online" fixo** (informação falsa) | Bolinha verde real na lista, no cabeçalho e nos contatos. Só existe online/offline (não há "visto por último") | F1 | P0 |

## ATV — Atividades

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| ATV-01 | Tela de atividades | ⬜ | ✅ 8.1 · `84a6ec3` | — | Aba "Atividades" com paginação | F7 | P1 |
| ATV-02 | Contador de novas (WS 61) | ⬜ | ✅ 8.1 · `84a6ec3` | — | Badge na navegação inferior | F7 | P1 |
| ATV-03 | Gerar chamada perdida (`nao_atendeu: true`) | ⬜ | ✅ 6.5, 6.6 · `ceeec72` | — | **Obrigatório** no `CallManager` (timeout, ocupado, pendente > 25 s) | F6 | P0 |

## CFG — Configurações e administração

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| CFG-01 | Abas de configurações | ⬜ | ✅ 8.5 · `afd3269` · o Ramal SIP fica para a etapa 9 | Só existe "ConfigApi" (URL do servidor) | Tela de Configurações com seções | F7 | P1 |
| CFG-02 | Tema escuro | 🟡 | ✅ 8.5 · `5214cce` · padrão Claro até o escuro ser aprovado (`cores.md` pergunta 9) | Segue o sistema (DayNight); sem escolha manual | Sistema / Claro / Escuro (+ Material You opcional) | F7 | P2 |
| CFG-03 | Dispositivos (sessão atual, dispositivos de mídia) | ➖ | ⬜ fora do TODO (P3) | — | Mostrar modelo e versão; rotas de áudio disponíveis | F7 | P3 |
| CFG-04 | Permissões | 🟡 | ✅ 8.5 · `ee1237c` | Pedidas na Splash | Tela com o estado de cada permissão + atalho para as Configurações do sistema (inclui full-screen intent e otimização de bateria) | F7 | P1 |
| CFG-05 | Cores personalizadas | ➖ | ➖ só as cores do `cores.md`; a etapa 9 lista "cor de destaque" para avaliar | — | Avaliar: cor primária/Material You. Não replicar o editor completo | F8 | P3 |
| CFG-06 | Qualidade das chamadas | ⬜ | ✅ 8.5 · `705ee2e` · vale a partir da próxima chamada | — | = CHA-20 | F7 | P2 |
| CFG-07 | Sistema — parâmetros do servidor (permissão `parametros`) | ⬜ | ✅ 8.5 · `afd3269` | — | Tela administrativa (FCM, TURN, gravações, transcritor) | F7 | P2 |
| CFG-08 | Acessos — permissões (permissão `permissoes`) | ⬜ | ✅ 8.5 · `afd3269` | — | Tela administrativa usuário × permissão | F7 | P2 |

## GER — UX geral

| ID | Funcionalidade | Legado | Nova base | Situação / evidência (legado) | Adaptação Android | Fase | Prio |
|---|---|---|---|---|---|---|---|
| GER-01 | Erro global | 🟡 | ✅ 1.5 | ~65 `Toast`s com mensagem técnica (#52) | Snackbar com mensagens amigáveis | F0 | P0 |
| GER-02 | Faixa "sem conexão em tempo real" | ⬜ | ✅ 2.11 | Só no texto da notificação do serviço | Banner com o estado do WS | F1 | P1 |
| GER-03 | Diálogo de confirmação próprio | 🟡 | ✅ 1.5 | `AlertDialog` ad hoc | Componente padrão (variante perigo) | F0 | P1 |
| GER-04 | Arrastar e soltar arquivos | ➖ | ✅ 4.9 · `1fbfc38` | — | **Receber compartilhamento** (`ACTION_SEND`/`SEND_MULTIPLE`) de outros apps | F3 | P1 |
| GER-05 | Menu de contexto | ➖ | ✅ toque longo (2.8, 7.1) | — | Toque longo | — | — |
| GER-06 | Tela "Em desenvolvimento" | ➖ | ➖ não replicar | — | Não replicar | — | — |
| GER-07 | Avatares com fallback (inicial, inclusive emoji) | ⬜ | ✅ 1.5 | Ícone genérico | Componente `Avatar` com inicial (grafema) e cor | F0 | P0 |

---

## Requisitos exclusivos do Android (sem equivalente no web)

| ID | Requisito | Nova base | Por quê | Fase | Prio |
|---|---|---|---|---|---|
| AND-01 | Configurar URL do servidor (com teste de conexão) | ✅ 1.6 | O app não tem "origem" como o web; WS e WebRTC derivam da mesma base (`/api`, `/ws/`, `/webrtc`) | F0 | P0 |
| AND-02 | Confiar na CA do mkcert em debug via `network_security_config` (sem trust-all) | ✅ 1.7 · falta o teste com a CA do mkcert (⛔ não se altera a segurança do aparelho de teste) | Desenvolvimento local com HTTPS | F0 | P0 |
| AND-03 | Core-Telecom (chamadas no sistema: Bluetooth, carro, relógio, GSM concorrente) | ✅ 6.3 · falta o fone Bluetooth em aparelho | Padrão do Android para VoIP | F6 | P0 |
| AND-04 | Rotas de áudio (fone, alto-falante, Bluetooth SCO/LE, fone com fio) + foco de áudio + restauração do modo | ✅ 6.8 · `5837aec` | #21 | F6 | P0 |
| AND-05 | Sensor de proximidade só com o fone do aparelho | ✅ 6.8 · `5837aec` | #21 | F6 | P1 |
| AND-06 | Picture-in-Picture na chamada de vídeo | ✅ 6.12 · `add5196` | Equivalente à janela flutuante | F6 | P1 |
| AND-07 | WebSocket só em primeiro plano + FCM como canal em segundo plano (sem FGS permanente) | 🟡 1.12, 5.7 · WebSocket só em primeiro plano; em segundo plano, depende do push (⛔ 5.1) | Política do Play e bateria (#15) | F5 | P0 |
| AND-08 | Resposta direta e "marcar como lida" pela notificação | ✅ 5.5 · `199ce1b` | Padrão do Android | F5 | P0 |
| AND-09 | Atalhos de conversa (Conversation shortcuts) e notificações de conversa | ✅ 5.8 · `199ce1b` | Seção "Conversas" do Android 11+ | F5 | P2 |
| AND-10 | Receber compartilhamento de outros apps | ✅ 4.9 · `1fbfc38` | Equivale a arrastar e soltar | F3 | P1 |
| AND-11 | Cache offline (Room) e envio em fila (WorkManager) | ✅ 1.10, 3.7 · falta o teste de migração do Room | Rede móvel instável | F2 | P0 |
| AND-12 | Edge-to-edge e back preditivo (`targetSdk 35+`) | ✅ 1.5 | Exigência da plataforma | F0 | P0 |
| AND-13 | Pedido de full-screen intent (Android 14+) e isenção de otimização de bateria (explicado) | ✅ 5.3, 6.5, 8.5 | Chamadas com a tela bloqueada | F6 | P0 |
| AND-14 | Retomada após reiniciar o aparelho | ⛔ depende do push (5.1) | Depende do FCM; sem FGS permanente | F5 | P1 |
| AND-15 | Acessibilidade (TalkBack, alvos de 48 dp, contraste) | 🟡 rótulos e alvos nas telas; falta TalkBack em aparelho (seção Qualidade) | #25 | todas | P0 |
| AND-16 | Strings em `strings.xml` (i18n) | ✅ 1.5 | #52 | todas | P1 |
