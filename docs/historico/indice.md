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

## Ações sobre mensagens (etapa 7)
_(nada ainda)_

## Atividades, pesquisa, perfil e configurações (etapa 8)
_(nada ainda)_

## Extras — SIP, compartilhar tela (etapa 9)
_(nada ainda)_

## Qualidade, segurança e publicação
_(nada ainda)_

## App legado (hotfixes opcionais)
_(nada ainda)_
