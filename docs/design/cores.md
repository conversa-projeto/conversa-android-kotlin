# Cores do Conversa (referência para o Android)

> **Regra principal:** toda cor usada no app Android vem **deste documento**. A fonte de verdade
> visual é o app desktop **Delphi FireMonkey** (`conversa-windows-fmx`). **Não usar as cores do
> cliente web.** Cor nova = primeiro entra aqui (com origem e papel), depois no código.

## 1. Fonte

| Item | Valor |
|---|---|
| Repositório | `C:\Users\danie\Desktop\GIT\conversa-projeto\conversa-windows-fmx` |
| HEAD analisado | `4759ba54547b7a6a73a4bf693663fd752439da2d` (`4759ba5` — "feat: Integra chamadas com WebRTC (audio + video) via MediaMTX", 2026-04-26) |
| Working tree | `Conversa.Dados.pas` e `src/chamada/Conversa.Chamada.WebRTC.pas` modificados sem commit — **nenhuma alteração de cor** no diff |
| Data da análise | 2026-10-06 |
| Ignorado | `conversa-windows-fmx - Copia`, `__history`, `__recovery`, `Win32/Win64/Android64`, `*.dcu`, `bin/` (exceto `bin/tema`), `src/chat/chat/skia` (biblioteca Skia4Delphi), `lib/AES`, `lib/audio`, `lib/bird-socket-client`, `conversa-webrtc/` (app de teste separado), `teste/` |

### Mudanças recentes de cor (git log)

Últimos 15 commits: `4759ba5`, `ebe5cb9`, `784c989`, `3d57153`, `50724b7`, `bb6c031`, `e7aed82`, `3b73c9d`, `f2442c7`, `a084ef2`, `5936215`, `34ae54b`, `2ceca9f`, `bfd9d9a`, `1baab7d`.

- **`ebe5cb9` (2026-02-03) "Padroniza design visual do item de chamada"** — única mudança de cor recente: item da lista de chamadas passou de `claWhite` para `claWhitesmoke`, e o hover de `White→#E6E6E6` para `Whitesmoke→White`, "alinhando com o padrão de Contatos e Novo Grupo". Isso indica que o **padrão atual para itens de listagem secundária** é fundo `#F5F5F5` com hover `#FFFFFF`.
- `3d57153` (2026-02-02) criou as telas de histórico/detalhe de chamada (cores `#333333/#666666/#999999`, `#00C853`, `#FF5252`).
- `bin/tema/*.pss` e `src/pss/PascalStyleScript.pas` não mudam de cor desde 2024 (último commit relevante: `7327be8` "PSS - Correção de Warning").

## 2. Método

1. Listagem de todos os `.fmx` e `.pas` do app (fora das pastas ignoradas).
2. Extração por script (awk) de toda linha `Fill.Color`, `Stroke.Color`, `TextSettings.FontColor`, `StartValue/StopValue` de `TColorAnimation` e `ShadowColor`, com o nome/classe do componente dono e `arquivo:linha`.
3. Busca em `.pas` por `$FFxxxxxx`, `TAlphaColors.*`, `TAlphaColorF.Create`, `MakeColor`, `claXxx`.
4. Busca de **shapes sem `Fill.Color` gravado** — no FMX o valor padrão não é salvo no `.fmx`. Padrão de `TShape` (`TRectangle`, `TCircle`, `TPath`): **Fill `$FFE0E0E0`**, Stroke `$FF000000`. Padrão de `TText.TextSettings.FontColor`: **`claBlack`**. Esses defaults foram contados como cor efetiva (marcados "default FMX").
5. Leitura do sistema de tema `PascalStyleScript` (`src/pss/PascalStyleScript.pas`) e dos arquivos `bin/tema/{default,claro,escuro}.pss`.
6. Nomes `claXxx` convertidos com os valores de `System.UITypes` (`TAlphaColorRec`).
7. Conversão: FMX usa `AARRGGBB` (`xFF007DFF` no `.fmx`, `$FF007DFF` no `.pas`) → `#RRGGBB`; alfa ≠ `FF` é anotado. No Compose o formato é o mesmo: `Color(0xAARRGGBB)`.

Não existem `.style`, `.fsf`, `TStyleBook` nem estilos embutidos com cor. `res/` só contém `nova_mensagem.wav`. README/docs não descrevem cores.

### Achado importante: o tema PSS está DESLIGADO

- `TPascalStyleScript.RegisterObject` começa com `Result := Self; Exit;` (`src/pss/PascalStyleScript.pas:521-522`) — nenhum objeto é registrado, nada é aplicado.
- `LoadFromFile` (que carregaria `claro.pss`/`escuro.pss`) **não é chamado em lugar nenhum**.
- Logo, **as cores reais são as dos `.fmx` + `.pas`**. Os `.pss` são histórico (nov/2024), mas úteis: `default.pss` é um *snapshot* das cores de runtime da época (confirma, p.ex., `#E0E0E0` nos campos de login) e `escuro.pss` é a **única pista de um modo escuro** pretendido.

| Token PSS | `claro.pss` | `escuro.pss` | `default.pss` |
|---|---|---|---|
| `fundo` | `#F0F0F0` | `#323232` | `#F0F0F0` |
| `fundo_claro_1` | `#FAFAFA` | `#3C3C3C` | `#FAFAFA` |
| `fundo_escuro` | `#F0F0F0` | `#282828` | `#C8C8C8` |
| `primaria` | `#007FFF` | `#007FFF` | `#007FFF` |
| `primaria_clara` | `#007FFF` α `0x50` (31%) | idem | idem |

**Conclusão: o FMX não tem modo escuro ativo.** O esquema escuro abaixo é **PROPOSTA** derivada de `escuro.pss`.

## 3. Valores dos nomes `claXxx` usados

| Nome FMX | ARGB | Hex |
|---|---|---|
| `claWhite` | `$FFFFFFFF` | `#FFFFFF` |
| `claWhitesmoke` | `$FFF5F5F5` | `#F5F5F5` |
| `claSnow` | `$FFFFFAFA` | `#FFFAFA` |
| `claGainsboro` | `$FFDCDCDC` | `#DCDCDC` |
| `claLightgray` | `$FFD3D3D3` | `#D3D3D3` |
| `claSilver` | `$FFC0C0C0` | `#C0C0C0` |
| `claGray` | `$FF808080` | `#808080` |
| `claDimgray` | `$FF696969` | `#696969` |
| `claBlack` | `$FF000000` | `#000000` |
| `claNull` | `$00000000` | transparente |
| `claRed` | `$FFFF0000` | `#FF0000` |
| `claLightcoral` | `$FFF08080` | `#F08080` |
| `claGreen` | `$FF008000` | `#008000` |
| `claLime` | `$FF00FF00` | `#00FF00` |
| `claBlue` | `$FF0000FF` | `#0000FF` |
| `claLightblue` | `$FFADD8E6` | `#ADD8E6` |
| `claLightsteelblue` | `$FFB0C4DE` | `#B0C4DE` |

## 4. Paleta completa

Ocorrências = declarações explícitas em `.fmx` (incl. `TColorAnimation` e `ShadowColor`) + atribuições em `.pas`. "+N default" = shapes/textos sem cor gravada que herdam o default FMX. Caminhos relativos a `conversa-windows-fmx/`.

### 4.1 Paleta principal (uso recorrente)

| Hex | ARGB original | Papel | Onde (evidência) | Ocorr. |
|---|---|---|---|---|
| `#007DFF` | `xFF007DFF` / `$FF007DFF` | **Primária / marca** — ícones de ação, badge de não lidas, letra do avatar, "visualizada", botão primário | `chat.editor.fmx:53` pthEnviar, `:98` pthMicrofone; `chat.editor.texto.fmx:59` pthEmoji, `:106` pthAnexo; `chat.editor.audio.fmx:74,139,164`; `Conversa.Chat.Listagem.Item.fmx:92` rctCount (badge), `:45` txtAbreviatura; `Conversa.Principal.fmx:322` txtUserLetra; `Conversa.Chamada.Detalhe.fmx:209` rctBtnChamar, `:31` crclIcone; `chat.mensagem.pas:165` (check azul "visualizada"); `chat.editor.audio.pas:206`; `Conversa.Chamada.Detalhe.pas:130`; avatares em Contatos/Novo Grupo/Chamadas | 22 |
| `#007FFF` | `xFF007FFF` | **Primária (variante)** — praticamente idêntica a `#007DFF` (diferença de 2 no canal G, imperceptível). Usada em telas mais antigas e no PSS (`primaria`) | `Conversa.Login.fmx:84` rctBotaoEntrar; `Conversa.Notificacao.Item*.fmx` rctFundo (×3); `Conversa.Conexao.AvisoInicioSistema.fmx:106`, `Conversa.Configurar.Conexao.fmx:142`, `Novo.Grupo.fmx:113` (texto de botão "Salvar"/"Configurações") | 7 (+PSS) |
| `#FFFFFF` | `claWhite` | Superfície: lista de conversas, item de conversa, cards/diálogos, texto/ícone sobre primária | `Conversa.Chat.Listagem.fmx:22` rctListaConversas; `Conversa.Chat.Listagem.Item.fmx:6` rctFundo; `Conversa.Conteudo.fmx:13` rctTitulo; `Conversa.Configurar.Conexao.fmx:13` rctCenter; `Conversa.Conexao.AvisoInicioSistema.fmx:13`; `Conversa.Chamada.view.fmx:33,176` botões redondos; textos sobre azul (`txtBotaoEntrar`, `txtCount`, `txtBtnChamar`, notificações) | 48 |
| `#F5F5F5` | `claWhitesmoke` | **Fundo do app** (forms/telas), fundo do editor de mensagem, fundo do avatar, botões secundários, campos | `src/base/Conversa.FormularioBase.fmx:7,16` (form base); `Conversa.Login.fmx:6`; `Conversa.Chat.fmx:6`, `Conversa.Chat.Listagem.fmx:13`; `chat.editor.fmx:8` rtgFundo; `crclFoto` em todos os itens; `rctBotaoCancelar/Salvar`; itens de Contatos/Novo Grupo/Chamadas (`ebe5cb9`) | 41 |
| `#000000` | `claBlack` (+ default FMX de `TText`) | Texto da bolha, ícones neutros (menu lateral, mic/vídeo na chamada), loading | `Conversa.Principal.fmx:61,135,203,277` ícones do menu; `Conversa.Chamada.view.fmx:71,111,204,232`; `Conversa.Loading.Pontos.frame.fmx:16,35,56`; `chat.conteudo.mensagem.audio.fmx:15` crclAction; `PopupMenu.fmx:32` sombra | 15 (+todos os TText sem cor) |
| `#E0E0E0` | `xFFE0E0E0` + default `TShape` | Campo de entrada (login, editor), barra inferior da chamada, separador de data, "ir para última", botão secundário cinza, botões do menu lateral | `Conversa.Chamada.Detalhe.fmx:186` rctBtnVoltar (explícito); default: `Conversa.Login.fmx:18,45` rctUsuario/rctSenha, `chat.editor.fmx:13` rtgEditor, `Conversa.Chamada.view.fmx:16` rtgBarraInferior, `chat.separador.data.fmx:5`, `chat.ultima.fmx:5`, `Conversa.Principal.fmx:18,69,144,212,304`; `default.pss` confirma `#FFE0E0E0` | 1 (+~14 default) |
| `#C8C8C8` | `xFFC8C8C8` | Barra de título da janela e **menu lateral** (rail de navegação) | `src/base/Conversa.FormularioBase.fmx:39` rctTitleBar, `:136`; `Conversa.Principal.fmx:13` rctMenuLateral; `Conversa.Visualizador.Midia.fmx:173` | 4 |
| `#CFE7FF` | `$FFCFE7FF` | **Bolha da própria mensagem** (lado direito) | `src/chat/chat/frames/chat.mensagem.pas:101` (`CorFundo`, `TLado.Direito`) | 1 |
| `#EDEDED` | `$FFEDEDED` | **Bolha da mensagem do outro** (lado esquerdo) | `src/chat/chat/frames/chat.mensagem.pas:102` (`TLado.Esquerdo`) | 1 |
| `#E3F1FF` | `$FFE3F1FF` | **Conversa selecionada** na lista | `src/chat/Conversa.Chat.Listagem.Item.pas:126` (`Selecionado`) | 1 |
| `#E6E6E6` | `xFFE6E6E6` | Hover/pressionado de item (conversa, participante, usuário) | `Conversa.Chat.Listagem.Item.fmx:131`; `Conversa.Chamada.Participante.Item.fmx:87`; `Conversa.Chamada.Usuarios.Listagem.Item.fmx:87` | 3 |
| `#141414` | `xFF141414` | **Texto primário** (nome na lista) | `Conversa.Chat.Listagem.Item.fmx:66` lblNome; `Conversa.Contatos.Listagem.Item.fmx:65`; `Novo.Grupo.Usuario.Item.fmx:66`; `Conversa.Chamada.Listagem.Item.fmx:244` | 4 |
| `#646464` | `xFF646464` | **Texto secundário** (prévia da última mensagem, status) | `Conversa.Chat.Listagem.Item.fmx:85` txtMensagem; `Conversa.Chamada.Listagem.Item.fmx:261`; `Conversa.Chamada.Participante.Item.fmx:66`; `Conversa.Chamada.Usuarios.Listagem.Item.fmx:66` | 4 |
| `#808080` | `claGray` / `TAlphaColors.Gray` | Ícones/texto discretos: status pendente/recebida (✓/✓✓ cinza), "digitando…", ícone iniciar chamada | `chat.mensagem.fmx:66`, `chat.mensagem.pas:147,156`; `Conversa.Chat.fmx:170` pthIniciarChamada, `:212` txtNomeDigitando; `Conversa.Conteudo.fmx:149`; `Conversa.Notificacao.Item.Chamada.fmx:66,154` | 8 |
| `#FF0000` | `claRed` / `TAlphaColors.Red` | Perigo genérico: hover do botão fechar janela, gravando áudio, alertas de mic/vídeo, ícone de erro de conexão, chamada recusada | `Conversa.FormularioBase.pas:347`; `chat.editor.audio.fmx:190`, `.pas:173`; `Conversa.Chamada.view.fmx:164,283`; `Conversa.Conexao.AvisoInicioSistema.fmx:154,160`; `Conversa.Chamada.BarraTitulo.fmx:59,228`; `Conversa.Chamada.Listagem.Item.fmx:218` | 11 |
| `#333333` | `xFF333333` | Texto de título/valor (telas novas: Detalhe de chamada, Sobre) | `Conversa.Chamada.Detalhe.fmx:63,103,126,149,172,203`; `Conversa.Sobre.fmx:27` | 7 |

### 4.2 Cores de apoio e pontuais

| Hex | ARGB original | Papel | Onde | Ocorr. |
|---|---|---|---|---|
| `#F0F0F0` | `xFFF0F0F0` | Cabeçalho (toolbar) da conversa aberta | `src/chat/Conversa.Chat.fmx:13` rctTitulo | 1 (+PSS `fundo`) |
| `#FAFAFA` | só PSS | `fundo_claro_1` (lista de conversas em 2024) — **não usado hoje** (lista é `#FFFFFF`) | `bin/tema/claro.pss:10` | 0 |
| `#DCDCDC` | `claGainsboro` | Borda do popup menu, borda do avatar no painel de conteúdo | `lib/popupmenu/PopupMenu.fmx:17`; `Conversa.Conteudo.fmx:41` | 2 |
| `#DBDBDB` | `xFFDBDBDB` | Divisor (linhas) | `src/novo/grupo/Novo.Grupo.fmx:191,202` | 2 |
| `#000000` α `0x32` (20%) | `x32000000` | Divisor vertical lista/conversa | `src/chat/Conversa.Chat.Listagem.fmx:2527` lnSeparador | 1 |
| `#999999` | `xFF999999` | Rótulos (label) na tela de detalhe | `Conversa.Chamada.Detalhe.fmx:92,115,138,161` | 4 |
| `#9E9E9E` | `xFF9E9E9E` | Duração / data-hora (terciário) no item de chamada | `Conversa.Chamada.Listagem.Item.fmx:276,291` | 2 |
| `#666666` | `xFF666666` | Texto secundário (Detalhe, Sobre) | `Conversa.Chamada.Detalhe.fmx:74`; `Conversa.Sobre.fmx:37,47` | 3 |
| `#545454` | `xFF545454` | Texto digitado no editor de mensagem | `chat.editor.texto.fmx:161` txtMensagem | 1 |
| `#4B4B4B` | `xFF4B4B4B` | Ícone "remover anexo" | `chat.anexo.item.fmx:111`; `chat.editor.anexo.item.fmx:110` | 2 |
| `#696969` | `claDimgray` | Nome no painel de conteúdo | `Conversa.Conteudo.fmx:137` | 1 |
| `#C0C0C0` | `claSilver` | Ícone "ir para última mensagem" | `chat.ultima.fmx:24` | 1 |
| `#B0C4DE` | `claLightsteelblue` | Separador "mensagens não lidas"; fundo dos botões de gravação de áudio | `chat.separador.lidas.fmx:7`; `chat.editor.audio.fmx:31,95` | 3 |
| `#FFFAFA` | `claSnow` | Item do popup menu (hover anima `#FFFFFF→#F5F5F5`) | `lib/popupmenu/PopupMenu.Item.fmx:5,18-19` | 1 |
| `#E6F2FF` | `MakeColor(230,242,255)` | Hover do botão "Salvar" (config. de conexão) | `src/configuracoes/Conversa.Configurar.Conexao.pas:85` | 1 |
| `#0000EE` | `TAlphaColorF(0,0,238/255)` | **Link** em mensagem | `chat.conteudo.texto.pas:172` | 1 |
| `#551A8B` | `TAlphaColorF(85,26,139)/255` | Link após clique (visitado) | `chat.conteudo.texto.pas:106` | 1 |
| `#000000` α 50% | `txtHora.Opacity = 0.5` | **Hora dentro da bolha** (texto preto com opacidade 0.5) | `chat.mensagem.fmx` txtHora (Opacity 0.5, fonte 10) | 1 |
| `#FFFFFF` α ~10% | `TAlphaColorF.Create(255,255,255,0.1)` | Hover maximizar/minimizar da barra de título (provável clamp de RGB para 1.0 → `#1AFFFFFF`; incerto) | `Conversa.FormularioBase.pas:364,379`; `Conversa.Visualizador.Midia.pas:156,172` | 4 |
| `#000000` α `0x64` (39%) | `x64000000` | Scrim de modal | `src/principal/Conversa.ModalView.fmx:7` | 1 |
| `#000000` α `0xC8` (78%) | `xC8000000` | Fundo do visualizador de mídia | `src/visualizadormidia/Conversa.Visualizador.Midia.fmx:5` | 1 |
| `#F08080` | `claLightcoral` | Faixa de aviso "sem conexão" (texto branco) | `Conversa.Tela.Inicial.view.fmx:2471` rctAvisoConexao | 1 |
| `#00FF00` | `claLime` | Check de seleção de usuário (Novo Grupo) | `Novo.Grupo.Usuario.Item.fmx:97` | 1 |
| `#0000FF` | `claBlue` | Ícone "chamada realizada" (lista/barra); cor inicial da animação de loading | `Conversa.Chamada.Listagem.Item.fmx:150`; `Conversa.Chamada.BarraTitulo.fmx:160`; `Conversa.Loading.Pontos.frame.fmx:29,50,71` | 5 |

### 4.3 Chamadas e notificações

| Hex | ARGB original | Papel | Onde | Ocorr. |
|---|---|---|---|---|
| `#F5F5F5` + `#E0E0E0` | herdado / default | Tela de chamada é **clara**: fundo do form base `#F5F5F5`, barra inferior `#E0E0E0`, avatar do participante `#E0E0E0` com ícone preto | `Conversa.Chamada.view.fmx:7,16`; `Conversa.Chamada.Usuario.view.fmx:46,76` | — |
| `#FFFFFF` / `#000000` | `claWhite` / `claBlack` | Botões redondos mic/vídeo: fundo branco, ícone preto | `Conversa.Chamada.view.fmx:33,71,111,176,204,232` | 6 |
| `#D44242` | `xFFD44242` | **Encerrar chamada** (círculo), ícone branco | `Conversa.Chamada.view.fmx:295` | 1 |
| `#008000` | `claGreen` | **Atender chamada** (círculo, tela de chamada); ícones atender/recebida | `Conversa.Chamada.view.fmx:348`; `Conversa.Chamada.BarraTitulo.fmx:130,176`; `Conversa.Chamada.Listagem.Item.fmx:166` | 4 |
| `#85FF85` | `xFF85FF85` | Barra de título "em chamada" (faixa verde) | `src/chamada/Conversa.Chamada.BarraTitulo.fmx:6` | 1 |
| `#00D26A` | `$FF00D26A` | Waveform de áudio | `src/chamada/Conversa.Chamada.Waveform.pas:45` | 1 |
| `#00C853` | `$FF00C853` | Ícone "recebida" na tela de **detalhe** | `Conversa.Chamada.Detalhe.pas:135` | 1 |
| `#FF5252` | `$FFFF5252` | Ícone "perdida/recusada" na tela de **detalhe** | `Conversa.Chamada.Detalhe.pas:139` | 1 |
| `#007FFF` | `xFF007FFF` | Fundo do toast de notificação (mensagem e chamada) | `Conversa.Notificacao.Item.fmx:8`, `.Mensagem.fmx:4`, `.Chamada.fmx:4` | 3 |
| `#1B8DFF` | `xFF1B8DFF` | Avatar no toast de mensagem | `Conversa.Notificacao.Item.fmx:100`; `.Mensagem.fmx:96` | 2 |
| `#ADD8E6` | `claLightblue` | Hora no toast | `Conversa.Notificacao.Item.fmx:155`; `.Mensagem.fmx:151` | 2 |
| `#D3D3D3` | `claLightgray` | Tipo de chamada no toast | `Conversa.Notificacao.Item.Chamada.fmx:142` | 1 |
| `#4CAF50` | `xFF4CAF50` | Avatar no toast de chamada | `Conversa.Notificacao.Item.Chamada.fmx:93` | 1 |
| `#E53935` | `xFFE53935` | Botão **Recusar** (toast de chamada) | `Conversa.Notificacao.Item.Chamada.fmx:170` | 1 |
| `#43A047` | `xFF43A047` | Botão **Atender** (toast de chamada) | `Conversa.Notificacao.Item.Chamada.fmx:193` | 1 |
| `#FF0000` / `#FFFFFF` | `TAlphaColors` | Ícone da bandeja (overlay de contador) | `lib/windows/Conversa.Windows.Overlay.pas:111,120` | 2 |

### 4.4 Papéis pedidos sem equivalente no FMX

- **Indicador online:** não existe cor de presença no FMX (nenhum componente "online"). → pergunta em aberto.
- **Modo escuro:** só `bin/tema/escuro.pss`, inativo.
- **Botão danger com texto:** não existe botão vermelho com texto além de "Recusar" (`#E53935`).

## 5. Contraste (WCAG) — atenção

| Combinação | Razão aprox. | Observação |
|---|---|---|
| `#FFFFFF` sobre `#007DFF` | ~3,9:1 | Passa só para texto grande/ícones (≥3:1). O FMX usa assim (badge, botões). Manter, mas evitar texto pequeno branco em `#007DFF`. |
| `#000000` sobre `#CFE7FF` | ~17:1 | OK |
| `#646464` sobre `#FFFFFF` | ~5,9:1 | OK |
| `#FFFFFF` sobre `#F08080` | ~2,6:1 | **Reprova.** No Android usar `onErrorContainer` escuro (proposta abaixo). |

## 6. Mapeamento Material 3

### 6.1 Esquema claro (fiel ao FMX)

| Papel M3 | Hex | Origem |
|---|---|---|
| `primary` | `#007DFF` | cor de marca mais usada (§4.1) |
| `onPrimary` | `#FFFFFF` | texto/ícones sobre azul |
| `primaryContainer` | `#CFE7FF` | bolha própria |
| `onPrimaryContainer` | `#000000` | texto da bolha (TText default) |
| `inversePrimary` | `#9CCAFF` | **proposta** (não há no FMX) |
| `secondary` | `#646464` | **proposta** — FMX não tem secundária cromática; usa cinza de texto secundário |
| `onSecondary` | `#FFFFFF` | proposta |
| `secondaryContainer` | `#E3F1FF` | item selecionado (bom para indicador do NavigationBar/Rail) |
| `onSecondaryContainer` | `#141414` | texto primário |
| `tertiary` | `#43A047` | **proposta** — verde de "atender" |
| `onTertiary` | `#FFFFFF` | |
| `background` | `#F5F5F5` | fundo das telas (`claWhitesmoke`) |
| `onBackground` | `#141414` | texto primário |
| `surface` | `#FFFFFF` | lista de conversas, cards, diálogos |
| `onSurface` | `#141414` | |
| `surfaceVariant` | `#F0F0F0` | cabeçalho da conversa |
| `onSurfaceVariant` | `#646464` | texto secundário |
| `surfaceContainerLowest` | `#FFFFFF` | |
| `surfaceContainerLow` | `#F5F5F5` | |
| `surfaceContainer` | `#F0F0F0` | top bar |
| `surfaceContainerHigh` | `#E0E0E0` | campos de entrada, barra inferior da chamada |
| `surfaceContainerHighest` | `#C8C8C8` | menu lateral / barra de título |
| `outline` | `#C8C8C8` | **proposta** (FMX quase não usa bordas) |
| `outlineVariant` | `#DBDBDB` | divisores (`Novo.Grupo.fmx`) |
| `error` | `#E53935` | botão Recusar (vermelho "Material" já presente no FMX; preferido ao `#FF0000` puro) |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#F08080` | faixa "sem conexão" |
| `onErrorContainer` | `#410002` | **proposta** (FMX usa branco, contraste insuficiente) |
| `scrim` | `#000000` (aplicar α 0x64) | `ModalView` |
| `inverseSurface` / `inverseOnSurface` | `#333333` / `#F5F5F5` | proposta (snackbar) |

### 6.2 Esquema escuro — **PROPOSTA** (o FMX não tem modo escuro ativo)

Derivado de `bin/tema/escuro.pss` (`fundo #323232`, `fundo_claro_1 #3C3C3C`, `fundo_escuro #282828`, `primaria #007FFF`). Os demais valores são propostas e precisam de aprovação.

| Papel M3 | Hex | Origem |
|---|---|---|
| `primary` | `#007DFF` | mesma marca (PSS mantém a primária no escuro) |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#16406B` | proposta (bolha própria escura) |
| `onPrimaryContainer` | `#E3F1FF` | proposta |
| `secondary` | `#B4B4B4` | proposta |
| `onSecondary` | `#1E1E1E` | proposta |
| `secondaryContainer` | `#2B3A4A` | proposta (item selecionado) |
| `onSecondaryContainer` | `#F0F0F0` | proposta |
| `tertiary` | `#66BB6A` | proposta |
| `background` | `#323232` | `escuro.pss` `fundo` |
| `onBackground` | `#F0F0F0` | proposta |
| `surface` | `#3C3C3C` | `escuro.pss` `fundo_claro_1` |
| `onSurface` | `#F0F0F0` | proposta |
| `surfaceVariant` | `#282828` | `escuro.pss` `fundo_escuro` |
| `onSurfaceVariant` | `#B4B4B4` | proposta |
| `surfaceContainerLowest` | `#3C3C3C` | 🆕 8.5: espelha o claro (= `surface`) |
| `surfaceContainerLow` | `#323232` | 🆕 8.5: espelha o claro (= `background`) |
| `surfaceContainer` | `#282828` | 🆕 8.5: espelha o claro (= `surfaceVariant`; barra inferior). Sem ele, o Material usava um cinza arroxeado |
| `surfaceContainerHigh` | `#464646` | proposta (campos) |
| `surfaceContainerHighest` | `#282828` | `fundo_escuro` (menu lateral/barra) |
| `outline` | `#5A5A5A` | proposta |
| `outlineVariant` | `#464646` | proposta |
| `error` | `#EF5350` | proposta |
| `onError` | `#FFFFFF` | |
| `errorContainer` | `#8C1D18` | proposta |
| `onErrorContainer` | `#FFDAD6` | proposta |

### 6.3 Tokens próprios do app (`ConversaCores`)

| Token | Claro | Escuro (proposta) | Origem FMX |
|---|---|---|---|
| `bolhaPropria` | `#CFE7FF` | `#16406B` | `chat.mensagem.pas:101` |
| `bolhaOutro` | `#EDEDED` | `#3C3C3C` | `chat.mensagem.pas:102` |
| `textoBolhaPropria` | `#000000` | `#F0F0F0` | default TText |
| `textoBolhaOutro` | `#000000` | `#F0F0F0` | default TText |
| `horaBolha` | `#80000000` (preto 50%) | `#99FFFFFF` | `txtHora Opacity 0.5` |
| `statusEnviando` / `statusEntregue` | `#808080` | `#9E9E9E` | `chat.mensagem.pas:147,156` |
| `statusLida` | `#007DFF` | `#4DA3FF` | `chat.mensagem.pas:165` |
| `link` | `#0000EE` | `#8AB4F8` | `chat.conteudo.texto.pas:172` |
| `linkVisitado` | `#551A8B` | `#C58AF9` | `chat.conteudo.texto.pas:106` |
| `badgeNaoLidas` | `#007DFF` | `#007DFF` | `Conversa.Chat.Listagem.Item.fmx:92` |
| `textoBadge` | `#FFFFFF` | `#FFFFFF` | `:109` |
| `itemSelecionado` | `#E3F1FF` | `#2B3A4A` | `Conversa.Chat.Listagem.Item.pas:126` |
| `itemPressionado` | `#E6E6E6` | `#464646` | `ColorAnimation1` (`#E6E6E6`) |
| `divisorLista` | `#33000000` (FMX: α `0x32`) | `#33FFFFFF` | `Conversa.Chat.Listagem.fmx:2527` |
| `divisor` | `#DBDBDB` | `#464646` | `Novo.Grupo.fmx:191` |
| `separadorNaoLidas` | `#B0C4DE` | `#3A4A5E` | `chat.separador.lidas.fmx:7` |
| `separadorData` | `#E0E0E0` | `#464646` | `chat.separador.data.fmx` (default) |
| `campoEntrada` | `#E0E0E0` | `#464646` | `chat.editor.fmx:13`, login (default) |
| `textoCampo` | `#545454` | `#DCDCDC` | `chat.editor.texto.fmx:161` |
| `iconeAcao` | `#007DFF` | `#007DFF` | editor (enviar/mic/emoji/anexo) |
| `iconeNeutro` | `#000000` | `#F0F0F0` | menu lateral, chamada |
| `iconeDiscreto` | `#808080` | `#9E9E9E` | iniciar chamada, digitando |
| `avatarFundo` | `#F5F5F5` | `#464646` | `crclFoto` |
| `avatarLetra` | `#007DFF` | `#4DA3FF` | `txtAbreviatura` |
| `textoPrimario` | `#141414` | `#F0F0F0` | `lblNome` |
| `textoTitulo` | `#333333` | `#F0F0F0` | Detalhe/Sobre |
| `textoSecundario` | `#646464` | `#B4B4B4` | `txtMensagem` |
| `textoTerciario` | `#9E9E9E` | `#8C8C8C` | data/duração |
| `digitando` | `#808080` | `#9E9E9E` | `Conversa.Chat.fmx:212` |
| `gravandoAudio` | `#FF0000` | `#FF5252` | `chat.editor.audio.pas:173` |
| `fundoBotaoGravacao` | `#B0C4DE` | `#3A4A5E` | `chat.editor.audio.fmx:31,95` |
| `chamadaFundo` | `#F5F5F5` | `#282828` | form base (tela de chamada clara) |
| `chamadaBarraInferior` | `#E0E0E0` | `#3C3C3C` | `Conversa.Chamada.view.fmx:16` |
| `chamadaBotao` / `chamadaIconeBotao` | `#FFFFFF` / `#000000` | `#464646` / `#F0F0F0` | `crclAudio/crclVideo` |
| `chamadaEncerrar` | `#D44242` | `#D44242` | `crclFinalizarChamada` |
| `chamadaAtender` | `#008000` | `#43A047` | `crclAtenderChamada` (ver pergunta 3) |
| `chamadaAlerta` | `#FF0000` | `#FF5252` | `pthMicrofoneAlerta` |
| `chamadaEmAndamento` | `#85FF85` | `#2E7D32` | `Conversa.Chamada.BarraTitulo.fmx:6` |
| `waveform` | `#00D26A` | `#00D26A` | `Conversa.Chamada.Waveform.pas:45` |
| `chamadaRealizada` | `#007DFF` | `#4DA3FF` | `Conversa.Chamada.Detalhe.pas:130` |
| `chamadaRecebida` | `#00C853` | `#00C853` | `:135` |
| `chamadaPerdida` | `#FF5252` | `#FF5252` | `:139` |
| `notificacaoFundo` | `#007FFF` | `#007FFF` | toasts |
| `notificacaoAvatar` | `#1B8DFF` | `#1B8DFF` | toast mensagem |
| `notificacaoHora` | `#ADD8E6` | `#ADD8E6` | toast |
| `notificacaoAvatarChamada` | `#4CAF50` | `#4CAF50` | toast chamada |
| `botaoRecusar` | `#E53935` | `#E53935` | toast chamada |
| `botaoAtender` | `#43A047` | `#43A047` | toast chamada |
| `avisoConexao` | `#F08080` | `#8C1D18` | `rctAvisoConexao` |
| `menuLateral` | `#C8C8C8` | `#282828` | `rctMenuLateral` |
| `selecaoCheck` | `#00FF00` → sugerido `#43A047` | `#66BB6A` | `Novo.Grupo.Usuario.Item.fmx:97` (ver pergunta 6) |
| `overlayModal` | `#64000000` | `#64000000` | `ModalView` |
| `fundoVisualizadorMidia` | `#C8000000` | `#C8000000` | `Visualizador.Midia.fmx:5` |
| `online` | **indefinido** | **indefinido** | não existe no FMX (pergunta 1) |

### 6.4 Destaque de código (papel novo, sem cor nova) — 2026-10-08

O FMX não tem destaque de sintaxe. Para os blocos de código (TODO 7.9), os papéis novos **reaproveitam tokens desta paleta**; nenhum hexadecimal novo entrou:

| Papel | Token reaproveitado | Claro | Escuro (proposta) |
|---|---|---|---|
| Palavra-chave, tag de XML/HTML | `primary` (`AzulConversa`) | `#007DFF` | `#4DA3FF` |
| Texto entre aspas | `chamadaAtender` | `#008000` | `#43A047` |
| Comentário (itálico) | `textoTerciario` | `#9E9E9E` | `#8C8C8C` |
| Número | `chamadaEncerrar` | `#D44242` | `#D44242` |
| Atributo de XML/HTML | `link` | `#0000EE` | `#8AB4F8` |
| Cabeçalho do bloco ("js", "Copiar") | `campoEntrada` (fundo), `textoTerciario`, `iconeAcao` | — | — |

**Markdown formatado (```` ```md ````, 2026-10-08)**, também sem cor nova:

| Papel | Token reaproveitado |
|---|---|
| Texto | `onSurface` |
| Fundo do código e da tabela | `campoEntrada` |
| Divisórias; fundo do "Visualizar"/"Código" | `divisorLista` |
| Link | `primary` |
| Alertas do GitHub (`> [!NOTE]` etc.) | nota `primary`; dica `chamadaAtender`; importante `link`; aviso `avisoConexao`; cuidado `chamadaEncerrar` |

Os alertas do GitHub têm cores próprias na biblioteca; aqui elas são trocadas pelos tokens acima.

Ver a pergunta 12 da §9.

## 7. Snippet Kotlin (Compose)

```kotlin
// ui/theme/Color.kt
package com.conversa.app.core.ui.theme // sugestão (namespace atual: com.conversa.app); ajustar ao módulo real

import androidx.compose.ui.graphics.Color

// --- Marca / base (FMX) ---
val AzulConversa        = Color(0xFF007DFF) // primária (xFF007DFF)
val AzulConversaAlt     = Color(0xFF007FFF) // variante legada (login/notificações/PSS)
val BrancoFmx           = Color(0xFFFFFFFF) // claWhite
val WhitesmokeFmx       = Color(0xFFF5F5F5) // claWhitesmoke — fundo
val CinzaTopo           = Color(0xFFF0F0F0) // header da conversa
val CinzaCampo          = Color(0xFFE0E0E0) // default TShape — campos
val CinzaRail           = Color(0xFFC8C8C8) // menu lateral / barra de título
val Divisor             = Color(0xFFDBDBDB)
val TextoPrimario       = Color(0xFF141414)
val TextoSecundario     = Color(0xFF646464)
val VermelhoErro        = Color(0xFFE53935)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = AzulConversa,
    onPrimary = BrancoFmx,
    primaryContainer = Color(0xFFCFE7FF),
    onPrimaryContainer = Color(0xFF000000),
    inversePrimary = Color(0xFF9CCAFF),            // proposta
    secondary = TextoSecundario,                   // proposta
    onSecondary = BrancoFmx,
    secondaryContainer = Color(0xFFE3F1FF),
    onSecondaryContainer = TextoPrimario,
    tertiary = Color(0xFF43A047),                  // proposta
    onTertiary = BrancoFmx,
    background = WhitesmokeFmx,
    onBackground = TextoPrimario,
    surface = BrancoFmx,
    onSurface = TextoPrimario,
    surfaceVariant = CinzaTopo,
    onSurfaceVariant = TextoSecundario,
    surfaceContainerLowest = BrancoFmx,
    surfaceContainerLow = WhitesmokeFmx,
    surfaceContainer = CinzaTopo,
    surfaceContainerHigh = CinzaCampo,
    surfaceContainerHighest = CinzaRail,
    outline = CinzaRail,                           // proposta
    outlineVariant = Divisor,
    error = VermelhoErro,
    onError = BrancoFmx,
    errorContainer = Color(0xFFF08080),
    onErrorContainer = Color(0xFF410002),          // proposta (contraste)
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF333333),            // proposta
    inverseOnSurface = WhitesmokeFmx,
)

// PROPOSTA — o FMX não tem modo escuro ativo (base: bin/tema/escuro.pss)
val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = AzulConversa,
    onPrimary = BrancoFmx,
    primaryContainer = Color(0xFF16406B),
    onPrimaryContainer = Color(0xFFE3F1FF),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF2B3A4A),
    onSecondaryContainer = Color(0xFFF0F0F0),
    tertiary = Color(0xFF66BB6A),
    background = Color(0xFF323232),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF3C3C3C),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF282828),
    onSurfaceVariant = Color(0xFFB4B4B4),
    surfaceContainerHigh = Color(0xFF464646),
    surfaceContainerHighest = Color(0xFF282828),
    outline = Color(0xFF5A5A5A),
    outlineVariant = Color(0xFF464646),
    error = Color(0xFFEF5350),
    onError = BrancoFmx,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6),
)
```

```kotlin
// ui/theme/ConversaCores.kt
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ConversaCores(
    val bolhaPropria: Color,
    val bolhaOutro: Color,
    val textoBolhaPropria: Color,
    val textoBolhaOutro: Color,
    val horaBolha: Color,
    val statusEntregue: Color,
    val statusLida: Color,
    val link: Color,
    val linkVisitado: Color,
    val badgeNaoLidas: Color,
    val textoBadge: Color,
    val itemSelecionado: Color,
    val itemPressionado: Color,
    val divisorLista: Color,
    val separadorNaoLidas: Color,
    val separadorData: Color,
    val campoEntrada: Color,
    val textoCampo: Color,
    val iconeAcao: Color,
    val iconeDiscreto: Color,
    val avatarFundo: Color,
    val avatarLetra: Color,
    val textoTerciario: Color,
    val gravandoAudio: Color,
    val chamadaFundo: Color,
    val chamadaBarraInferior: Color,
    val chamadaBotao: Color,
    val chamadaIconeBotao: Color,
    val chamadaEncerrar: Color,
    val chamadaAtender: Color,
    val chamadaEmAndamento: Color,
    val waveform: Color,
    val chamadaRealizada: Color,
    val chamadaRecebida: Color,
    val chamadaPerdida: Color,
    val notificacaoFundo: Color,
    val botaoRecusar: Color,
    val botaoAtender: Color,
    val avisoConexao: Color,
    val menuLateral: Color,
    val overlayModal: Color,
    val fundoVisualizadorMidia: Color,
    val online: Color, // INDEFINIDO no FMX — ver perguntas em aberto
)

val ConversaCoresClaro = ConversaCores(
    bolhaPropria = Color(0xFFCFE7FF),
    bolhaOutro = Color(0xFFEDEDED),
    textoBolhaPropria = Color(0xFF000000),
    textoBolhaOutro = Color(0xFF000000),
    horaBolha = Color(0x80000000),
    statusEntregue = Color(0xFF808080),
    statusLida = Color(0xFF007DFF),
    link = Color(0xFF0000EE),
    linkVisitado = Color(0xFF551A8B),
    badgeNaoLidas = Color(0xFF007DFF),
    textoBadge = Color(0xFFFFFFFF),
    itemSelecionado = Color(0xFFE3F1FF),
    itemPressionado = Color(0xFFE6E6E6),
    divisorLista = Color(0x32000000),
    separadorNaoLidas = Color(0xFFB0C4DE),
    separadorData = Color(0xFFE0E0E0),
    campoEntrada = Color(0xFFE0E0E0),
    textoCampo = Color(0xFF545454),
    iconeAcao = Color(0xFF007DFF),
    iconeDiscreto = Color(0xFF808080),
    avatarFundo = Color(0xFFF5F5F5),
    avatarLetra = Color(0xFF007DFF),
    textoTerciario = Color(0xFF9E9E9E),
    gravandoAudio = Color(0xFFFF0000),
    chamadaFundo = Color(0xFFF5F5F5),
    chamadaBarraInferior = Color(0xFFE0E0E0),
    chamadaBotao = Color(0xFFFFFFFF),
    chamadaIconeBotao = Color(0xFF000000),
    chamadaEncerrar = Color(0xFFD44242),
    chamadaAtender = Color(0xFF008000),
    chamadaEmAndamento = Color(0xFF85FF85),
    waveform = Color(0xFF00D26A),
    chamadaRealizada = Color(0xFF007DFF),
    chamadaRecebida = Color(0xFF00C853),
    chamadaPerdida = Color(0xFFFF5252),
    notificacaoFundo = Color(0xFF007FFF),
    botaoRecusar = Color(0xFFE53935),
    botaoAtender = Color(0xFF43A047),
    avisoConexao = Color(0xFFF08080),
    menuLateral = Color(0xFFC8C8C8),
    overlayModal = Color(0x64000000),
    fundoVisualizadorMidia = Color(0xC8000000),
    online = Color(0xFF43A047), // PROPOSTA provisória — não existe no FMX
)

// PROPOSTA (FMX sem modo escuro)
val ConversaCoresEscuro = ConversaCoresClaro.copy(
    bolhaPropria = Color(0xFF16406B),
    bolhaOutro = Color(0xFF3C3C3C),
    textoBolhaPropria = Color(0xFFF0F0F0),
    textoBolhaOutro = Color(0xFFF0F0F0),
    horaBolha = Color(0x99FFFFFF),
    statusEntregue = Color(0xFF9E9E9E),
    statusLida = Color(0xFF4DA3FF),
    link = Color(0xFF8AB4F8),
    linkVisitado = Color(0xFFC58AF9),
    itemSelecionado = Color(0xFF2B3A4A),
    itemPressionado = Color(0xFF464646),
    divisorLista = Color(0x33FFFFFF),
    separadorNaoLidas = Color(0xFF3A4A5E),
    separadorData = Color(0xFF464646),
    campoEntrada = Color(0xFF464646),
    textoCampo = Color(0xFFDCDCDC),
    iconeDiscreto = Color(0xFF9E9E9E),
    avatarFundo = Color(0xFF464646),
    avatarLetra = Color(0xFF4DA3FF),
    textoTerciario = Color(0xFF8C8C8C),
    gravandoAudio = Color(0xFFFF5252),
    chamadaFundo = Color(0xFF282828),
    chamadaBarraInferior = Color(0xFF3C3C3C),
    chamadaBotao = Color(0xFF464646),
    chamadaIconeBotao = Color(0xFFF0F0F0),
    chamadaAtender = Color(0xFF43A047),
    chamadaEmAndamento = Color(0xFF2E7D32),
    chamadaRealizada = Color(0xFF4DA3FF),
    avisoConexao = Color(0xFF8C1D18),
    menuLateral = Color(0xFF282828),
)

val LocalConversaCores = staticCompositionLocalOf { ConversaCoresClaro }
```

```kotlin
// ui/theme/Theme.kt
@Composable
fun ConversaTheme(
    darkTheme: Boolean = false, // FMX é só claro; escuro é proposta — ligar só após aprovação
    content: @Composable () -> Unit,
) {
    // Sem dynamicColor: a identidade vem do FMX, não do papel de parede.
    val scheme = if (darkTheme) DarkColors else LightColors
    val cores = if (darkTheme) ConversaCoresEscuro else ConversaCoresClaro
    CompositionLocalProvider(LocalConversaCores provides cores) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
// Uso: LocalConversaCores.current.bolhaPropria
```

## 8. Regras

1. **Toda cor nova deve vir deste documento.** Se faltar um papel, primeiro procurar no FMX, registrar aqui (hex, ARGB, arquivo:linha) e só então usar.
2. **Não usar as cores do web** (nem paleta do cliente web, nem cores default de bibliotecas web).
3. Nada de `Color(0x…)` solto em telas: usar `MaterialTheme.colorScheme.*` ou `LocalConversaCores.current.*`.
4. Não usar `dynamicColor` (Material You) — quebraria a identidade do FMX.
5. Primária única no Android: **`#007DFF`**. `#007FFF` só onde se quiser reproduzir literalmente a notificação/login legados (diferença imperceptível).
6. Cores puras de "nome HTML" do FMX (`claRed #FF0000`, `claGreen #008000`, `claBlue #0000FF`, `claLime #00FF00`) são tratadas como **legado**: o Android usa os equivalentes já presentes no FMX mais novo (`#E53935`/`#FF5252`, `#43A047`/`#00C853`, `#007DFF`) salvo decisão contrária.
7. Alterou cor no FMX → atualizar este documento (com o novo hash de commit).

## 9. Perguntas em aberto / incertezas

1. **Indicador online:** o FMX não tem. Definir cor (proposta provisória `#43A047`) ou omitir.
2. **`#007DFF` vs `#007FFF`:** duas primárias quase idênticas. Proposta: unificar em `#007DFF` (mais usada e mais recente). Confirmar.
3. **Verde de atender:** `claGreen #008000` (tela de chamada) vs `#43A047` (toast) vs `#00C853` (detalhe). Proposta Android: `#43A047` para botões e `#00C853` para ícone "recebida"; manter `#008000` só se quiser fidelidade literal.
4. **Vermelhos:** `#FF0000` (vários), `#D44242` (encerrar), `#E53935` (recusar), `#FF5252` (perdida). Proposta: `error = #E53935`, encerrar chamada `#D44242`, perdida `#FF5252`.
5. **Ícones do histórico de chamadas inconsistentes:** a lista usa `claBlue/claGreen/claRed` (`Conversa.Chamada.Listagem.Item.fmx:150,166,218`), o detalhe usa `#007DFF/#00C853/#FF5252` (`Conversa.Chamada.Detalhe.pas`). Proposta: seguir o detalhe (mais recente).
6. **`claLime #00FF00`** no check de seleção do Novo Grupo é berrante/legado — confirmar se deve ser trocado por `#43A047`.
7. **Defaults FMX:** as cores `#E0E0E0` (fill de `TShape`) e `#000000` (`TText`) foram inferidas pelo default do framework, não lidas do arquivo; `default.pss` (snapshot de runtime de 2024) confirma `#E0E0E0` nos campos de login. O estilo nativo da plataforma (Windows) pode alterar cor de `TLabel`/`TEdit` sem `FontColor` explícito — não verificado em execução.
8. **Hover da barra de título:** `TAlphaColorF.Create(255,255,255,0.1)` passa 255 onde se espera 0..1; o resultado provável (com clamp) é branco 10% (`#1AFFFFFF`). Irrelevante no Android (sem barra de título).
9. **Modo escuro:** tudo em §6.2 e a coluna "Escuro" de §6.3 é proposta; só `#323232/#3C3C3C/#282828/#007FFF` vêm de `escuro.pss` (inativo).
10. **Tela de chamada no Android:** o FMX é claro; apps móveis costumam usar chamada escura. Se desejado, `chamadaFundo` escuro é decisão nova (não está no FMX).
11. **Cabeçalho:** `Conversa.Chat.fmx` usa `#F0F0F0`, mas `Conversa.Conteudo.fmx:13` usa `#FFFFFF`. Proposta: top bar = `#F0F0F0` (`surfaceContainer`).
12. **Destaque de código (§6.4):** como o FMX não tem, o Android reaproveita tokens da paleta (azul, verde de atender, cinza terciário, vermelho de encerrar, link). Confirmar se está bom ou se prefere outro esquema.
