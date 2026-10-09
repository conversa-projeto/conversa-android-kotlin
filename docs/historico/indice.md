# Histórico de alterações — índice por fluxo

> Uma linha por alteração, na seção do fluxo, em ordem cronológica (mais recente embaixo). O detalhe de cada uma está no arquivo do link.
> Regras de registro: `CLAUDE.md`, seção "Regra obrigatória: histórico de alterações". Modelo: [`_modelo.md`](_modelo.md).
>
> Formato: `- AAAA-MM-DD · [Título](arquivo.md) — resumo`

---

## Planejamento e documentação
- 2026-10-06 · [Auditoria inicial do Android contra o servidor e o web](2026-10-06-01-auditoria-inicial.md) — docs 00–08; decisão de recomeçar; 132 funcionalidades, 57 problemas, Q1–Q12, S1–S14
- 2026-10-06 · [TODO literal, cronograma removido e correção sobre o OpenAPI](2026-10-06-02-todo-detalhado.md) — `TODO.md` com 586 passos; sem datas; o OpenAPI já existe (faltam as respostas)
- 2026-10-06 · [Criação do histórico de alterações e do CLAUDE.md](2026-10-06-04-historico-e-claude-md.md) — `docs/historico/` por fluxo + regra obrigatória no `CLAUDE.md`
- 2026-10-07 · [Cores do app vêm do conversa-windows-fmx](2026-10-07-01-cores-do-fmx.md) — `docs/design/cores.md`; primária `#007DFF`; escuro é proposta e fica desligado

## Sincronização com servidor e web
- 2026-10-06 · [Commits da noite: votação, campo rico, chat completo na chamada](2026-10-06-03-sincronizacao-votacao-campo-rico.md) — servidor `8031fa5` (enquete, WS 62, tipo 8) e web `39d06f9`; 136 funcionalidades; novos FC-315/415/416/516–518/723
- 2026-10-08 · [Sincronização: limite de reações, votação com data final e painel do grupo](2026-10-08-20-sincronizacao-reacoes-votacao-prazo.md) — servidor `d4435db`, `5cad911` (69 rotas, migração 036) e web `eaa8bac`, `785bdef`; docs 00–08, OpenAPI e linhas 🆕 no TODO (2.10, 6.13, 7.2, 7.12, 8.6)
- 2026-10-08 · [Sincronização: agendadas fora do chat, rascunho e encaminhada de encaminhada](2026-10-08-31-sincronizacao-agendadas-rascunho.md) — web `7322e83`, `e8d82cb` e servidor `779c8ed` (só script); docs 00, 03 (ENV-23, total 137), 05, 06 (FC-519, FC-520) e linhas 🆕 no TODO (7.5, 7.10, 7.11)

## Repositório e build
- 2026-10-06 · [Etapa 0 — preservação, limpeza e organização do repositório](2026-10-06-05-etapa-0-repositorio.md) — branch/tag legado, 11 stashes em tags e patches, limpeza, wrapper versionado, docs em `docs/legado/`, ADR 0001, README; push pendente

## Fundação (etapa 1)
- 2026-10-07 · [Etapa 1 — fundação do app novo](2026-10-07-02-etapa-1-fundacao.md) — 8 módulos, rede (67 rotas), WebSocket, Room, sessão cifrada, sync, design system, tela Servidor; 65 testes passando

## Sessão, conversas, contatos e presença (etapa 2)
- 2026-10-07 · [Etapa 2 — camada de dados e OpenAPI](2026-10-07-03-etapa-2-camada-de-dados.md) — login/cadastro/dispositivo/sair, conversas (fixar, arquivar, direta, grupo), contatos, presença, início resiliente, limpeza; OpenAPI = 67 rotas
- 2026-10-07 · [Etapa 2 — login, cadastro, navegação e saída](2026-10-07-04-etapa-2-login-cadastro-navegacao.md) — telas de login e cadastro, rotas tipadas, barra inferior, links `conversa://chat`, sair com limpeza; 124 testes
- 2026-10-07 · [Etapa 2 — conversas, grupos e teste de ponta a ponta no emulador](2026-10-07-05-etapa-2-conversas-e-teste-no-emulador.md) — `:feature:conversas` (lista, nova conversa, grupos, membros), 6 correções achadas no emulador, S15/S16; 137 testes

## Mensagens (etapa 3)
- 2026-10-07 · [Etapa 3 — regras do chat, carregar, ler e enviar](2026-10-07-06-etapa-3-regras-e-dados.md) — classificação e links do web, paginação, fila de leitura, envio com WorkManager; status do WS 3 só nas minhas; 159 testes
- 2026-10-07 · [Etapa 3 — tela de chat, testada no emulador](2026-10-07-07-etapa-3-tela-de-chat.md) — `:feature:chat` (bolhas, Últimas, FABs, links, digitando, status, reenviar); app fechava por regex do ICU (corrigido + teste instrumentado); 164 testes

## Anexos e mídia (etapa 4)
- 2026-10-07 · [Etapa 4 — envio de anexos, URLs assinadas, token só na API e proxy de dev](2026-10-07-08-etapa-4-upload-e-proxy-dev.md) — `AnexosRepositorio` (SHA-256 em fluxo, dedup, URL vencida), `AutenticacaoInterceptor` só para `/api/`, `ferramentas/proxy-dev.mjs`; 171 testes
- 2026-10-07 · [Etapa 4 — envio com anexos, bolhas de imagem e arquivo (parcial)](2026-10-07-09-etapa-4-envio-com-anexos-e-bolhas.md) — worker sobe os anexos e manda a mensagem; bolhas de imagem/arquivo, abrir com FileProvider; seletor no campo e teste no emulador ficaram para a volta; 174 testes
- 2026-10-07 · [Etapa 4 — seletor de anexos, visualizador e teste no emulador](2026-10-07-10-etapa-4-seletor-visualizador-e-teste-no-emulador.md) — galeria/câmera/documento no campo, visualizador, `FetcherAnexo` (URL vencida renovada no Coil), lista acompanha mensagem nova; testado nos dois sentidos e com 200 MB; 177 testes
- 2026-10-07 · [Etapa 4 — player de áudio (Media3) e `:core:media`](2026-10-07-11-etapa-4-player-de-audio.md) — um áudio por vez, barra com seek, verde até ouvir + `POST /mensagem/reproduzir`, para ao sair da conversa; download com URL vencida tenta de novo; 185 testes
- 2026-10-07 · [Etapa 4 — gravação de áudio no campo](2026-10-07-12-etapa-4-gravacao-de-audio.md) — microfone (segurar envia, toque trava, arrastar cancela), barra com pausa/ouvir, AAC/M4A em trechos juntados, tipo 5, "gravando" a cada 2,5 s; `nomeSeguro` sem `..`; 193 testes
- 2026-10-08 · [Etapa 4 — vídeo na bolha e no visualizador, tira de miniaturas](2026-10-08-01-etapa-4-video.md) — primeiro quadro pela URL assinada (sem baixar tudo), Media3 com controles no visualizador, miniaturas; 193 testes
- 2026-10-08 · [Etapa 4 — baixar para Downloads e compartilhar](2026-10-08-02-etapa-4-baixar-e-compartilhar.md) — Downloads/Conversa pelo MediaStore (Android 9: "Salvar como"), aviso com "Abrir", compartilhar, notificação quando permitida; 196 testes
- 2026-10-08 · [Etapa 4 — transcrição de áudio](2026-10-08-03-etapa-4-transcricao.md) — "Transcrever" embaixo dos áudios, consulta a cada 3 s, resultado em todas as mensagens com o anexo, botões somem sem transcritor; 199 testes
- 2026-10-08 · [Etapa 4 — receber compartilhamento de outros apps](2026-10-08-04-etapa-4-receber-compartilhamento.md) — "Conversa" no compartilhar do Android, "Enviar para…", itens copiados para o cache, só `content://` de outro app; 205 testes
- 2026-10-08 · [Etapa 4 — visualizador de PDF](2026-10-08-05-etapa-4-visualizador-pdf.md) — `PdfRenderer` sob demanda, zoom, "Página X de Y", Baixar/Abrir com…; 205 testes
- 2026-10-08 · [Etapa 4 — colar imagem, HTML e economia de dados](2026-10-08-06-etapa-4-colar-html-economia.md) — campo em `TextFieldState` com `contentReceiver`, HTML com outro app, "Toque para carregar" com conexão lenta/economia; 211 testes

## Notificações e push (etapa 5)
- 2026-10-08 · [Etapa 5 — notificação de mensagem, canais, permissão e atalhos (sem o push)](2026-10-08-07-etapa-5-notificacoes-sem-push.md) — MessagingStyle por conversa com Responder/Marcar como lida, regra do web (som na frente, notificação em segundo plano), some ao ler/arquivar/abrir, atalhos; Firebase ⛔ (precisa do usuário); 214 testes

## Chamadas (etapa 6)
- 2026-10-08 · [Etapa 6 — módulos de chamada e a máquina de estados](2026-10-08-08-etapa-6-maquina-de-estados-chamada.md) — `:core:webrtc` (interface `MidiaChamada`) e `:feature:chamada` (`GerenciadorChamadas`): fases, eventos 51–57, pendentes, regras do web (ocupado, 30 s, 25 s, 55 sem 52…), monitor de 4 s; 51 testes com eventos duplicados e fora de ordem
- 2026-10-08 · [Etapa 6 — mídia WebRTC (WHIP/WHEP), tela da chamada e ligar pela conversa](2026-10-08-09-etapa-6-midia-webrtc-e-tela.md) — `MidiaWebRtc` (publicação, assinaturas, ICE só relay, VP9/H264, quadros pretos com a câmera desligada), `ChamadaActivity` (recebida e em chamada), botões de ligar; testado contra o web: gravação no MediaMTX, atender, recusar, sair dos dois lados, upgrade para vídeo; 274 testes
- 2026-10-08 · [Etapa 6 — chamada no sistema: toque, notificação, tela cheia, serviço e Core-Telecom](2026-10-08-10-etapa-6-integracao-sistema.md) — toque próprio, CallStyle recebida (tela cheia) e em andamento, serviço `phoneCall|microphone`, Core-Telecom (só registra a chamada do app; nada de rede de telefonia), "em espera" com "Retomar"; testado no emulador; 274 testes
- 2026-10-08 · [Etapa 6 — rota de áudio da chamada e sensor de proximidade](2026-10-08-11-etapa-6-rota-de-audio.md) — botão "Áudio saída" com as rotas do Telecom (fone do aparelho, alto-falante, Bluetooth, fone com fio), vídeo no alto-falante, proximidade só no fone do aparelho; no emulador só há alto-falante; 274 testes
- 2026-10-08 · [Etapa 6 — histórico de chamadas](2026-10-08-12-etapa-6-historico-de-chamadas.md) — aba "Chamadas": Todas/Perdidas, busca, período, Hoje/Ontem/data, seta e cores, abrir a conversa, "Ligar novamente"; "perdida" corrigida em relação ao web (status 5 nunca é gravado); 278 testes
- 2026-10-08 · [Etapa 6 — modos de exibição da chamada (grade, destaque, tela única)](2026-10-08-13-etapa-6-modos-de-exibicao.md) — modo no estado do gerenciador (+ testes), seletor com os rótulos do web, toque para destacar, setas na tela única, "só assistir" em tela única; testado numa chamada em grupo com a nova conta C; 283 testes
- 2026-10-08 · [Etapa 6 — minimizar a chamada: picture-in-picture e a faixa "voltar à chamada"](2026-10-08-14-etapa-6-minimizar.md) — PiP automático em vídeo (tarefa própria, só o vídeo principal), "voltar" minimiza, faixa verde "Toque para voltar à chamada" no topo do app; testado no emulador; 283 testes
- 2026-10-08 · [Etapa 6 — adicionar à chamada e chat da chamada](2026-10-08-15-etapa-6-adicionar-e-chat.md) — "Adicionar à chamada" (um `PUT` por pessoa) e "Chat da chamada" (a primeira mensagem cria o chat e abre a conversa; a chamada vai para o PiP); testado com B e C no web; 287 testes
- 2026-10-08 · [Etapa 6 — indicador de fala e somente recepção](2026-10-08-16-etapa-6-fala-e-recepcao.md) — anel verde de quem fala (nível do WebRTC, 0,02 e 400 ms como o web) e "Ativar microfone/câmera" para quem entrou só recebendo; 292 testes
- 2026-10-08 · [Etapa 6 — tela e ponteiro remotos](2026-10-08-17-etapa-6-tela-e-ponteiro.md) — a tela compartilhada entra em destaque, inteira; ponteiros dos outros com nome e cor; "Sair da chamada" fixo na barra; testado com o B compartilhando a tela e o C apontando; 295 testes
- 2026-10-08 · [Etapa 6 — matriz de testes manuais de chamada](2026-10-08-18-etapa-6-matriz-de-testes.md) — `docs/testes/chamadas.md` com o que passou no emulador e o que falta em aparelho; "atender no web com o celular tocando" testado

## Ações sobre mensagens (etapa 7)
- 2026-10-08 · [Etapa 7 — menu da mensagem, reações, ocultar e copiar](2026-10-08-19-etapa-7-menu-reacoes-ocultar.md) — toque longo com reações rápidas, seletor de emoji, chips e "quem reagiu", WS 7, Ocultar com a confirmação do web e Copiar (texto ou imagem)
- 2026-10-08 · [Itens 🆕 da sincronização: limite e "+N" nas reações, chat da chamada e cabeçalho do grupo](2026-10-08-21-pendencias-da-sincronizacao.md) — 5 emojis por pessoa, chips na ordem do servidor (banco v2), "+N"; grupo do chat da chamada criado no primeiro toque, com foco no campo; cabeçalho do grupo abre os membros; placeholder do web
- 2026-10-08 · [Etapa 7 — responder](2026-10-08-22-etapa-7-responder.md) — deslizar a bolha ou "Responder" no menu; barra acima do campo; `mensagem_referencia` tipo 1 com a citação já na otimista; gravação também responde; ações de acessibilidade na bolha
- 2026-10-08 · [Etapa 7 — citação completa e "ir para a mensagem"](2026-10-08-23-etapa-7-citacao-e-ir-para-a-mensagem.md) — citação com hora, conteúdos e aninhada (5 níveis); tocar leva à original sem deixar buraco no Room, com destaque; encaminhada abre a conversa de origem; link/notificação troca o chat aberto
- 2026-10-08 · [Etapa 7 — encaminhar e responder no privado](2026-10-08-24-etapa-7-encaminhar-e-responder-no-privado.md) — folha de destinos com busca (conversas e contatos sem direta), encaminhada pela fila com os conteúdos da original; "Responder no privado" abre a direta com a mensagem pendente; "Encaminhar" escondido em votação
- 2026-10-08 · [Etapa 7 — menções no campo](2026-10-08-25-etapa-7-mencoes.md) — "@" abre até 6 contatos; "@Nome" destacado no campo vira `@[Nome](id)` no envio; porte do `mencoesTexto.ts` com testes
- 2026-10-08 · [Etapa 7 — figurinhas animadas](2026-10-08-26-etapa-7-figurinhas.md) — as 24 animações Lottie do web nos assets; seletor por pacote no "+"; bolha de 160 dp que para com "remover animações"; envio tipo 7 pela fila
- 2026-10-08 · [Etapa 7 — blocos de código](2026-10-08-27-etapa-7-blocos-de-codigo.md) — cabeçalho com "Copiar"/"Copiado!", destaque de sintaxe próprio (linguagens do web, sem regex), recolher acima de 240 dp; cores do destaque reaproveitam tokens (cores.md §6.4)
- 2026-10-08 · [Etapa 7 — detalhe do status e agendar](2026-10-08-28-etapa-7-status-e-agendamento.md) — toque no ✓ abre o detalhe (etapas na direta, seções no grupo); toque longo no Enviar agenda (≥ 5 min, ≤ 1 ano), selo "Agendada para …" que some na hora exata
- 2026-10-08 · [Etapa 7 — atalhos de emoji e código](2026-10-08-29-etapa-7-atalhos-e-codigo.md) — ":)" vira 🙂 ao digitar o espaço (e no envio), fora de código; "+" → "Código" abre "Inserir código"; colar mais de 10 linhas sugere a mesma janela (Cancelar cola como estava)
- 2026-10-08 · [Etapa 7 — votação: base de dados](2026-10-08-30-etapa-7-enquetes-base.md) — DTO com data final e encerramento (servidor `5cad911`), rotas encerrar/PATCH, `EnquetesRepositorio` com cache e leituras deduplicadas, WS 62 relê só o que está em cache
- 2026-10-08 · [Agendadas no relógio e encaminhada de encaminhada](2026-10-08-32-agendadas-no-relogio-e-encaminhada-dupla.md) — como o web `7322e83`: agendadas fora do chat, relógio com o número ao lado do microfone e folha "Mensagens agendadas" com "Cancelar"; a citação de uma encaminhada não repete o que a de baixo já mostra
- 2026-10-08 · [Rascunho por conversa](2026-10-08-33-rascunho-por-conversa.md) — como o web `7322e83`: texto com menções, anexos e resposta pendente guardados no Room (versão 3, migração automática) e restaurados ao abrir a conversa; apagado ao esvaziar ou enviar
- 2026-10-08 · [Etapa 7 — votação](2026-10-08-34-etapa-7-votacao.md) — bolha com votar, barras e nomes ao vivo (WS 62), encerrada com 🏆 (inclusive pelo prazo, na hora), data final e encerrar para quem pode; "Nova votação" pelo "+" em grupo; resumo na citação, na oculta e na prévia
- 2026-10-08 · [Etapa 7 — Markdown formatado](2026-10-08-35-etapa-7-markdown.md) — blocos ```md formatados com "Visualizar"/"Código" (`multiplatform-markdown-renderer-m3` 0.45.0), recolhidos acima de 240 dp, cores só de tokens; mermaid (P2) aguarda decisão
- 2026-10-08 · [Etapa 7 — diagramas Mermaid](2026-10-08-36-etapa-7-mermaid.md) — mermaid 12.0.0 embutido (o mesmo do web, 1,87 MB no APK) num WebView isolado: ```mermaid e os de dentro do ```md viram diagrama; inválido fica como código

## Atividades, pesquisa, perfil e configurações (etapa 8)
- 2026-10-08 · [Etapa 8 — aba Atividades](2026-10-08-37-etapa-8-atividades.md) — módulo `:feature:atividades`: lista por dia com selos, prévias e "Nova", paginação, abrir marca como vistas e o WS 61 relê com a aba aberta; corrigido o POST de vistas (o OkHttp recusava POST sem corpo)
- 2026-10-09 · [Etapa 8 — pesquisa](2026-10-09-01-etapa-8-pesquisa.md) — na conversa ("⋮" → campo no cabeçalho, resultados levam à mensagem) e em todos os chats (módulo `:feature:pesquisa`, agrupado por conversa); corrigidas colisões de nomes de texto entre módulos e criado `ferramentas/textos-repetidos.mjs`
- 2026-10-09 · [Etapa 8 — perfil](2026-10-09-02-etapa-8-perfil.md) — módulo `:feature:config` com a tela Perfil: foto (Photo Picker, quadrado 256×256 em JPEG, remover, URL renovada a cada 30 s no máximo), nome e e-mail, troca de senha com as conferências e mensagens do web
- 2026-10-09 · [Etapa 8 — perfil de outro usuário](2026-10-09-03-etapa-8-perfil-de-outro-usuario.md) — folha com foto, nome, e-mail, telefone ("Não informado") e "Ligar", aberta pelo avatar da direta na lista e pelo cabeçalho do chat
- 2026-10-09 · [Etapa 8 — Configurações: tela, aparência e sobre](2026-10-09-04-etapa-8-configuracoes-aparencia-sobre.md) — aba Configurações no `:feature:config` (usuário → perfil, Aparência, Servidor, Sobre com licenças, Sair); tema Sistema/Claro/Escuro com padrão Claro enquanto o escuro for proposta; `surfaceContainer*` do esquema escuro

## Extras — SIP, compartilhar tela (etapa 9)
_(nada ainda)_

## Qualidade, segurança e publicação
_(nada ainda)_

## App legado (hotfixes opcionais)
_(nada ainda)_
