# 06 — Fila de correções e implementação (FC)

**Data:** 2026-10-06 (🆕 atualizado à noite com os commits `8031fa5` e `39d06f9`: FC-315, 415–416, 516–518, 723 e ajustes em FC-108, 112, 303, 714; 🆕 de novo em 2026-10-08 com `d4435db`, `5cad911`, `eaa8bac` e `785bdef`: ajustes em FC-212, 501, 516, 517, 719 e 723) · **Premissa:** decisão do doc 00. Construir **nova base**; o app atual só recebe higiene e, opcionalmente, hotfixes de segurança.

## Como ler

- Cada item tem um ID `FC-nnn`, prioridade (**P0** bloqueia substituir o app atual · **P1** paridade essencial · **P2** paridade completa · **P3** opcional), dependências e um **critério de pronto (CP)** verificável.
- Referências cruzadas:
  - `#n` = problema da auditoria Android (doc 04).
  - `Qn` = quebra contra o servidor (doc 00 §2 / doc 02).
  - `AUT-01` etc. = funcionalidade do web (docs 03/05).
  - `Sn` = pendência do servidor (doc 08).
  - `§x` = seção do contrato (doc 01).
- A ordem dentro de cada bloco é a ordem sugerida de execução.
- Marque `[x]` quando concluir e anote o commit.

---

## Bloco A — Decisão, preservação e higiene do repositório (fazer primeiro)

- [x] **FC-001 · P0 · Registrar a decisão de recomeçar (ADR).**
  - Criar `docs/adr/0001-nova-base.md` com o contexto (doc 00), a decisão, as alternativas descartadas (corrigir incrementalmente) e as consequências.
  - **CP:** ADR commitado.
- [x] **FC-002 · P0 · Preservar o trabalho não commitado.**
  - A branch `novo` tem 9 arquivos staged e 3 unstaged (doc 04 §1.2), além de 11 stashes.
  - Commitar tudo como "wip: estado final do app legado (abril/2026)" numa branch `legado/abril-2026`.
  - Exportar cada stash com `git stash show -p stash@{n} > docs/legado/stashes/n.patch`, ou criar branches com `git stash branch`.
  - **Não descartar nada antes disso.**
  - **CP:** `git status` limpo; stashes salvos.
- [ ] ⛔ (push pendente; tag local criada) **FC-003 · P0 · Publicar os 3 commits locais não enviados** (`a8b4027`, `8696b65`, `e3e2a65`) e a branch do FC-002. Criar a tag `legado-v1-final`.
  - **CP:** tag e branch no `origin`.
- [x] **FC-004 · P0 · Versionar o `gradle-wrapper.jar`** (#23).
  - Adicionar `!gradle/wrapper/gradle-wrapper.jar` ao `.gitignore` e commitar o jar.
  - **CP:** um clone limpo roda `gradlew assembleDebug`.
- [x] **FC-005 · P1 · Limpar o lixo versionado** (#57, doc 04 §1.3).
  - Remover `tmpclaude-*-cwd`, `temp_function.kt`, `build/reports/**`, `.claude/settings.local.json` e os `*.kt.OLD`/`*.REMOVIDO`.
  - Tirar `local.properties` do tracking (`git rm --cached`).
  - Apagar localmente `conversa-android-kotlin (2).rar` (36 MB, não versionado).
  - **CP:** `git ls-files` sem esses itens.
- [x] **FC-006 · P1 · Arquivar a documentação antiga.**
  - Mover `legado/*.md`, `documentacao-oficial/*.md` e `legado/chamada-tcp/` para `docs/legado/` com um aviso no topo: "desatualizado: descreve o servidor Delphi e o áudio TCP 9090".
  - Reescrever o `README.md` apontando para `docs/auditoria-2026-10/00-LEIAME.md`.
  - **CP:** README atualizado; nenhum doc antigo na raiz.
- [x] **FC-007 · P1 · Documentar como compilar.**
  - JDK 17+ (o `java` do PATH é 1.8; usar o JBR do Android Studio).
  - Corrigir os `.bat`: tirar o caminho absoluto e checar o `errorlevel`. Ou removê-los.
  - **CP:** seção "Como compilar" no README.

## Bloco B — Hotfixes no app ATUAL (OPCIONAL)

> **Só faça este bloco se alguém usa o APK atual no dia a dia até a nova base ficar pronta.** Cada item é pequeno e isolado. Mesmo corrigido, o app atual **não deve ir para o Play**.

- [ ] **FC-010 · P0\* · Logs sensíveis** (#4).
  - `HttpLoggingInterceptor`: `BODY` só em `DEBUG`, mais `redactHeader("Authorization")`.
  - Remover os logs de frame do WS (`SocketManager.kt:176,417`), do prefixo do token (`ChatActivity.kt:1151`) e do token/payload FCM (`ConversaFcmService.kt:32,48`).
- [ ] **FC-011 · P0\* · Senha salva** (#6).
  - Parar de gravar `saved_password` (`UserPreferences.kt:22-23,64-69`) e o auto-login por senha.
  - Usar `allowBackup="false"`, ou regras de exclusão do DataStore.
- [ ] **FC-012 · P0\* · URL de desenvolvimento** (Q11).
  - `build.gradle.kts:26-27`: porta `4430` → `443` (ou sem porta).
- [ ] **FC-013 · P0\* · Toque calado pelo full-screen intent** (#7).
  - Remover `ChamadaRingtoneManager.parar()` incondicional de `ChamadaActivity.kt:150-151`.
- [ ] **FC-014 · P0\* · Atender pela notificação** (#3).
  - `answerPI` = `PendingIntent.getActivity(ChamadaActivity, extra auto_answer)`.
- [ ] **FC-015 · P0\* · Marcar como visualizada** (Q4).
  - `@POST("mensagem/visualizar")` com corpo `{conversa, mensagem}`.
- [ ] **FC-016 · P0\* · Mensagem oculta** (Q6).
  - Adicionar `excluida_em: String?` em `Mensagem` e exibir "Mensagem oculta" quando preenchido.
- [ ] **FC-017 · P0\* · Download de anexos** (Q3).
  - `GET /anexo?identificador=` devolve `{url}`. Buscar o JSON e usar a URL assinada no Glide, no áudio e no download (sem header `Authorization`, porque a URL já é assinada).
- [ ] **FC-018 · P0\* · Upload de anexos** (Q2).
  - Novo fluxo: `PUT /anexo {identificador, tipo, nome, extensao, tamanho}`, depois `PUT` na URL, depois `POST /anexo/confirmar?identificador=`.
  - Ver doc 01 §8.2.
- [ ] **FC-019 · P0\* · Chamadas com TURN** (Q1).
  - Buscar `GET /api/ice` antes de criar cada `PeerConnection` e usar os `iceServers` devolvidos.
  - **Atenção:** `/ice` é a única rota em camelCase (`urls`, `username`, `credential`).
- [ ] **FC-020 · P0\* · `GET /conversa/dados` não existe** (Q5).
  - Trocar por `GET /conversa/usuarios?conversa=` para montar os participantes da chamada.
- [ ] **FC-021 · P1\* · Callbacks de chamada** (#1/#2, mitigação).
  - Só o `ChamadaRepository` trata 51–56 enquanto houver chamada; desregistrar no `cleanup()`. O `SocketService` volta a tratar 51 quando não há chamada.

**CP do bloco:** checklist manual — login, conversas, texto, imagem enviada e recebida, áudio enviado e tocado, chamada 1:1 em Wi-Fi e em 4G, atender pela notificação com a tela bloqueada.

---

## Bloco C — Nova base: Fundação (F0)

> Detalhes de arquitetura no doc 07. Cada item abaixo produz código **com testes**.

- [ ] **FC-100 · P0 · Criar o projeto novo.**
  - Pacote `com.conversa.app` (ou manter `com.conversa.conversa` para atualizar sobre o instalado; ver a decisão em doc 07 §1).
  - `compileSdk/targetSdk 36`, `minSdk 28`, JDK 17, Kotlin 2.2+, AGP 9, version catalog **completo**, sem as flags depreciadas do `gradle.properties` (#2.1).
  - **CP:** app vazio compila; `./gradlew lint test` passa.
- [ ] **FC-101 · P0 · Módulos.**
  - `:app`, `:core:model`, `:core:network`, `:core:database`, `:core:datastore`, `:core:ui` (design system), `:core:testing`.
  - Features: `:feature:auth`, `:feature:conversas`, `:feature:chat`, `:feature:chamada`, `:feature:atividades`, `:feature:config`.
  - Pode começar com menos módulos e dividir depois.
  - **CP:** grafo de dependências sem ciclos.
- [ ] **FC-102 · P0 · CI.**
  - GitHub Actions: build, lint, testes unitários, ktlint/detekt, APK de debug como artefato.
  - **CP:** PR mostra o status.
- [ ] **FC-103 · P0 · Hilt + estrutura UDF.**
  - `ViewModel` + `StateFlow<UiState>` + eventos. Um `UiState` com Loading/Content/Empty/Error (evita o #26).
  - **CP:** tela exemplo com teste de ViewModel.
- [ ] **FC-104 · P0 · Design system em `:core:ui`.**
  - Tema Material 3 (claro/escuro, cor de marca), tipografia.
  - Componentes: `Avatar` com fallback de inicial por grafema (GER-07), `ConfirmDialog` (GER-03), Snackbar de erro (GER-01), `EmptyState`, `ErrorState`.
  - Edge-to-edge e back preditivo (AND-12).
  - Strings em `strings.xml` (AND-16).
  - **CP:** previews Compose de cada componente.
- [ ] **FC-105 · P0 · Configuração do servidor** (AND-01).
  - Uma `ServerConfig` no DataStore com `baseUrl`. Derivar `api = base + "/api/"`, `ws = (wss|ws)://host[:porta]/ws/` (**barra final obrigatória**, §6.1) e `webrtc = base + "/webrtc"`.
  - Tela com "Testar conexão" (ex.: `GET /api/ice` sem token deve dar 401, ou ver doc 01 §1).
  - **CP:** trocar a URL reconfigura REST, WS e WebRTC sem reiniciar o app (#13, #45).
- [ ] **FC-106 · P0 · TLS correto** (AND-02, #11).
  - `network_security_config.xml` com cleartext desligado e `<debug-overrides>` confiando na CA do mkcert (`res/raw/mkcert_ca.pem`).
  - **Proibido trust-all.**
  - **CP:** debug conecta ao mkcert; release recusa certificado inválido.
- [ ] **FC-107 · P0 · Rede.**
  - **Um único `OkHttpClient`** (REST, WS, WHIP/WHEP, Coil, download), com `pingInterval(20s)` para o WS.
  - `HttpLoggingInterceptor` só em debug, com o header redigido (#4).
  - Retrofit + kotlinx.serialization (`ignoreUnknownKeys`, `explicitNulls=false`, `coerceInputValues`).
  - **CP:** teste com MockWebServer.
- [ ] **FC-108 · P0 · Modelos do contrato em `:core:model`/`:core:network`, fiéis ao doc 01.**
  - snake_case via `@SerialName`.
  - Datas ISO-8601 UTC com `Z` → `Instant`, com parser tolerante (#30). Exceção: o JSON de resumo da chamada (§9.8).
  - Ids que às vezes vêm como string (ex.: `id` do anexo existente, §8.2) → serializer tolerante.
  - Campos que **somem** quando vazios (`mensagem_referencia`, `reacoes`) → defaults.
  - 🆕 DTO `Enquete` `{id, conversa_id, mensagem_id, pergunta, multipla, criado_por, opcoes:[{id, texto, votantes:[{id, nome}]}], total_votantes, meus_votos}` (doc 01 §10.13).
  - **CP:** testes de desserialização com os JSON reais dos `tests/*.test.ts` do servidor.
- [ ] **FC-109 · P0 · Tratamento de erro padrão.**
  - Erro do servidor é `{error}` (§3).
  - 401 → evento global "sessão expirada" (AUT-03).
  - Erro HTML do nginx (502/504) → "Servidor indisponível".
  - 400/403/404/500 → mensagem amigável + log.
  - **CP:** testes de mapeamento.
- [ ] **FC-110 · P0 · Room** (AND-11).
  - Tabelas `conversa`, `mensagem`, `conteudo`, `reacao`, `usuario/contato`, `atividade`, `chamada_historico`, `envio_pendente`, mais um cursor `sync_estado` (`ate` de `/mensagens/novas`).
  - **CP:** migrações testadas.
- [ ] **FC-111 · P0 · Sessão segura** (#6, #44).
  - Token JWT (válido por 12 h, sem refresh, §2.2) cifrado com Keystore (Tink/EncryptedFile).
  - `dispositivo_id` persistido. **Nunca a senha.**
  - `allowBackup=false`, ou regras que excluem a sessão.
  - **CP:** um backup não contém o token.
- [ ] **FC-112 · P0 · `RealtimeClient` (WebSocket).**
  - Conecta em `/ws/` e envia `{tipo:1, token}` (§6.2). Não há resposta em caso de sucesso: considerar "logado" se não vier o tipo 0/9.
  - Expõe `SharedFlow<EventoSocket>` **multi-assinante** (corrige #1/#2) e `StateFlow<EstadoConexao>`.
  - Um único socket ativo, identificado por geração (#16).
  - Reconexão com backoff de 1 s a 30 s.
  - Parser tolerante para os tipos 0, 2, 3, 4, 5, 7, 9, 40, 51–57, 60, 61 e 🆕 62 `{enquete_id, conversa_id}` (§6.4–6.6).
  - Envio só dos tipos 1 e 57.
  - Conectado **só com o app em primeiro plano** (ProcessLifecycleOwner, AND-07).
  - **CP:** testes do parser com payloads reais; teste de reconexão.
- [ ] **FC-113 · P0 · Ressincronização** (§6.7).
  - Eventos que chegam com o socket fora se perdem. A cada (re)conexão:
    1. `GET /mensagens/novas?desde=<ate salvo>` e atualizar o cursor `ate`.
    2. `GET /conversas`.
    3. `GET /contatos/online`.
    4. `GET /atividades/novas`.
    5. `GET /chamadas/pendentes`.
  - O WS 2 **não traz `conversa_id`** e pode chegar duplicado: tratar como gatilho de sincronização incremental.
  - **CP:** derrubar a rede por 2 min, mandar mensagens do web e voltar → tudo aparece sem duplicar.

## Bloco D — Sessão, conversas, contatos, presença (F1)

- [ ] **FC-200 · P0 · Login** (AUT-01).
  - `POST /api/login {login, senha, dispositivo_id?}` → `{id, nome, email, telefone, avatar_identificador, dispositivo{…}, token}`.
  - Guardar o token e `dispositivo.id`.
  - Atenção ao limite de 10 logins/min no nginx: tratar 429/503 com "aguarde".
  - **CP:** login, kill do app e reabertura → entra direto, sem pedir senha.
- [ ] **FC-201 · P0 · Registro do dispositivo** (AUT-04, Q8).
  - Após o login: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma:"android"}`.
  - Respeitar os limites de coluna (nome/modelo 50, versão 15; §7.3), senão dá 500.
  - **CP:** linha atualizada no banco.
- [ ] **FC-202 · P0 · Sessão resiliente e logout** (AUT-03, AUT-05, #45).
  - 401 → limpar e ir ao login.
  - Logout:
    - `PATCH /dispositivo {id, token_fcm:null}`;
    - fechar o WS;
    - encerrar a chamada;
    - limpar Room, notificações e caches (Coil/áudio).
  - **CP:** após o logout, não chega push nem notificação.
- [ ] **FC-203 · P1 · Cadastro** (AUT-02).
  - `PUT /api/usuario {nome, login, email, senha}` (pública).
  - Tratar e-mail duplicado (o servidor devolve 500 com mensagem do Postgres; mostrar "E-mail já cadastrado" — ver S7).
- [ ] **FC-204 · P0 · Lista de conversas** (CON-01).
  - Do Room; `GET /conversas` → `id, descricao, tipo, nome, destinatario_id, mensagem_id, ultima_mensagem, ultima_mensagem_texto, mensagens_sem_visualizar, avatar_url, fixada_ordem, arquivada_em`.
  - Ordem: fixadas por `fixada_ordem`, depois `mensagem_id` desc.
  - Título: `descricao || nome || "Conversa #id"`.
  - Prévia: menção → `@Nome`; código → `Código (linguagem)`; "Mensagem oculta" vem do servidor.
  - Badge de não lidas, exceto nas arquivadas. Hora: hoje `HH:mm`, ontem "Ontem", senão `dd/MM/aa`.
  - **CP:** paridade visual com o web nos 8 casos do doc 03 CON-01.
- [ ] **FC-205 · P0 · Atualização em tempo real da lista.**
  - WS 2, 3 e 40 e o envio/leitura → refresh incremental, sem spinner que esconde a lista (#47).
- [ ] **FC-206 · P1 · Filtro + "Nova conversa"** (CON-02).
- [ ] **FC-207 · P1 · Fixar, desafixar e reordenar** (CON-03).
  - `PATCH /conversa/fixadas {conversas:[ids na ordem]}`, otimista, com rollback via `GET /conversas`.
- [ ] **FC-208 · P1 · Arquivar** (CON-04).
  - `PATCH /conversa/arquivada {conversa, arquivada}`.
  - Arquivada não notifica nem toca som, e fecha a notificação existente.
  - Seção "Arquivadas (N)".
- [ ] **FC-209 · P1 · Menu de toque longo da conversa** (CON-05).
- [ ] **FC-210 · P0 · Contatos + obter/criar conversa direta** (CON-06, CON-11).
  - Reusar a direta existente (`tipo=1 && destinatario_id`).
  - Senão: `PUT /conversa {descricao:"", tipo:1}`, depois `PUT /conversa/usuario` para **mim e para o contato**, depois refresh.
  - Tratar falha parcial (#46; ver S8).
- [ ] **FC-211 · P1 · Criar grupo** (CON-07).
  - `PUT /conversa {descricao, tipo:2}`, depois `PUT /conversa/usuario` para cada membro, **incluindo o criador**.
  - Validações com os textos do web.
- [ ] **FC-212 · P1 · Membros do grupo** (CON-08).
  - `GET /conversa/usuarios?conversa=` → `[{id: conversa_usuario_id, usuario_id, nome, avatar_url}]`.
  - Renomear com `PATCH /conversa {id, descricao}`.
  - 🆕 `eaa8bac`: o web abre os "Dados do grupo" pelo avatar do grupo no cabeçalho do chat, com os anexos do grupo (FC-809) na mesma tela.
  - Adicionar e remover; o remover usa `DELETE /conversa/usuario?id=<conversa_usuario_id>`, **não** o `usuario_id`.
- [ ] **FC-213 · P0 · Presença** (PRE-01).
  - `GET /contatos/online` ao conectar + WS 60 `{usuario_id, online}`.
  - Só contatos de conversa direta; só online/offline.
  - Remover qualquer "online" fixo.
- [ ] **FC-214 · P1 · Banner "sem conexão em tempo real"** (GER-02), após 5 s desconectado.

## Bloco E — Mensagens: núcleo (F2)

- [ ] **FC-300 · P0 · Tela de chat** (Compose `LazyColumn` invertida) com cabeçalho (CON-09): avatar, título, presença real, membros do grupo, digitando/gravando.
- [ ] **FC-301 · P0 · Carregamento e paginação** (MSG-01).
  - `GET /mensagens?conversa&mensagemreferencia&mensagensprevias&mensagensseguintes`. Abrir com 80 prévias; paginar 60 para cima; 60 para baixo após um salto. No máximo 100 por chamada.
  - Ordem: `coalesce(visivel_em, inserida)`, depois `id`.
  - **CP:** rolar até o início de uma conversa com mais de 1000 mensagens.
- [ ] **FC-302 · P0 · Modelo de mensagem completo** (§10.2).
  - `remetente` é **só o primeiro nome**.
  - Status `recebida/visualizada/reproduzida` são **booleanos** aqui.
  - Outros campos: `excluida_em`, `visivel_em`, `mensagem_referencia{tipo, mensagem{…}}` (recursivo), `reacoes[]`.
  - Conteúdos com `tipo` 1 (texto), 2 (imagem), 3 (arquivo), 4 (áudio), 5 (gravação), 6 (chamada, JSON) e 7 (figurinha), mais `transcricao_status`/`transcricao`.
- [ ] **FC-303 · P0 · Classificação das bolhas** (MSG-07), na mesma prioridade do web: oculta > chamada > imagem > 🆕 enquete (tipo 8, mesmo com referência) > figurinha > código > emoji > com referência > texto curto > padrão.
  - **CP:** testes unitários portados de `classificarMensagem.ts`.
- [ ] **FC-304 · P0 · Mensagem oculta** (MSG-16, Q6).
  - "Mensagem oculta" + toque para revelar.
  - Sem menu e sem reações. Fora da galeria.
  - Citação de mensagem oculta aparece como "Mensagem oculta".
- [ ] **FC-305 · P0 · Bolha de chamada** (MSG-13).
  - JSON `{chamada_id, tipo, status, iniciada, finalizada, duracao, participantes[]}`.
  - Textos e cores do web. **Atenção:** as datas desse JSON não seguem o padrão ISO-Z (§9.8).
- [ ] **FC-306 · P0 · Separadores de dia, nome do remetente em grupo, hora correta** (MSG-02, #29).
- [ ] **FC-307 · P0 · Enviar texto otimista** (ENV-01).
  - Id negativo → `PUT /mensagem {conversa_id, conteudos:[{ordem, tipo:1, conteudo}]}` → `{id, conversa_id, usuario_id}`.
  - Fila persistente (WorkManager) com reenvio. O texto nunca se perde.
- [ ] **FC-308 · P0 · Marcar como visualizada** (MSG-04, Q4).
  - `POST /mensagem/visualizar {conversa, mensagem}` para cada mensagem de outro, não visualizada e visível, com o app em primeiro plano.
  - Desconta o contador da conversa (otimista) e fecha a notificação ao chegar a 0.
  - Ver S9 (pedir um endpoint em lote).
- [ ] **FC-309 · P0 · Status de entrega** (MSG-08, MSG-10).
  - Ícones enviando/✓/✓✓/✓✓ azul.
  - WS 3 `{grupo: conversaId, mensagens:"12,13"}` → `GET /mensagem/status?conversa=&mensagem=12,13` → `[{mensagem_id, recebida, visualizada, reproduzida, excluida_em}]`.
- [ ] **FC-310 · P1 · Linha "Últimas"** (MSG-03) e FAB "ir para o final"/"novas mensagens" (MSG-05).
- [ ] **FC-311 · P1 · Links clicáveis** (MSG-11) com as regras de pontuação do web.
- [ ] **FC-312 · P1 · Digitando** (ENV-15).
  - `POST /conversa/digitando {id}` com throttle de 2,5 s (o último é adiado, não perdido).
  - Recebimento pelo WS 4. Expira em 4 s e some quando chega mensagem.
  - Textos de grupo exatos do web.
- [ ] **FC-313 · P2 · Mensagem só de emojis** (MSG-14).
- [ ] **FC-314 · P0 · Deep link** `conversa/{id}?mensagem={id}` (CON-12): abre a conversa e **recarrega as mensagens**.
- [ ] **FC-315 · P0 · 🆕 Placeholder da votação** (MSG-20): até a F4, a bolha tipo 8 mostra "📊 Votação" + "Abra no computador para votar". Nunca deixar a bolha vazia.

## Bloco F — Anexos e mídia (F3)

- [ ] **FC-400 · P0 · Upload** (ANX-02, Q2, #27). Ver §8.2.
  1. SHA-256 em streaming (`DigestInputStream`).
  2. `PUT /anexo {identificador, tipo, nome, extensao (≤ 10), tamanho}` → URL assinada (válida por 300 s) **ou**, se já existe, `id` (string) + URL de download.
  3. `PUT` do arquivo na URL, via streaming com progresso.
  4. `POST /anexo/confirmar?identificador=` (na **query**).
  - Tudo via WorkManager (sobrevive a matar o app).
  - **CP:** enviar um vídeo de 200 MB sem OOM; reenviar o mesmo arquivo não sobe de novo.
- [ ] **FC-401 · P0 · Fila de anexos + seletor** (ANX-01).
  - Photo Picker múltiplo, SAF para documentos e câmera.
  - Ordem dos conteúdos: [encaminhados] → texto → figurinha → arquivos.
  - Erro em qualquer upload cancela a mensagem inteira (como no web).
- [ ] **FC-402 · P0 · Tipos de conteúdo** (ANX-03).
  - `image/*` → 2, `audio/*` → 4, **gravação → 5**, resto → 3 (vídeo = 3 com extensão).
- [ ] **FC-403 · P0 · URLs assinadas** (ANX-14, Q3).
  - `GET /anexo?identificador=` → `{url}`.
  - Cache em memória com expiração; renovar ao falhar (403/expirada).
  - Coil com chave estável = identificador.
- [ ] **FC-404 · P0 · Imagens** (bolha, visualizador com zoom, galeria com vídeos e legenda; ANX-04).
- [ ] **FC-405 · P0 · Áudio** (ANX-10).
  - Media3, um por vez, seek.
  - Áudio "não ouvido" fica verde. O 1º play chama `POST /mensagem/reproduzir {conversa, mensagem}` uma vez.
  - Download sob demanda para cache com `.part` + rename atômico (#31).
- [ ] **FC-406 · P0 · Gravação de áudio** (ANX-11).
  - Reaproveitar `AudioRecorderHelper` (corrigir #48).
  - Segurar ≥ 300 ms e soltar envia; toque curto trava a gravação; pausar, ouvir antes, descartar.
  - Conteúdo **tipo 5**.
  - `POST /conversa/gravando {id}` a cada 2,5 s (ENV-16).
- [ ] **FC-407 · P0 · Download de arquivo** (ANX-09, #33): `MediaStore.Downloads`, nome sanitizado.
- [ ] **FC-408 · P1 · Vídeo na conversa** (ANX-05): miniatura + player Media3.
- [ ] **FC-409 · P1 · Transcrição** (ANX-12).
  - `PUT /anexo/transcricao {identificador}`, depois `GET /anexo/transcricao?identificador=` a cada 3 s → `{status 0..3, texto, erro}`.
  - Sem transcritor configurado: esconder o botão (ver S10).
- [ ] **FC-410 · P1 · Receber compartilhamento** (AND-10, GER-04).
  - `ACTION_SEND`/`SEND_MULTIPLE` de texto, imagem e arquivo → escolher a conversa.
- [ ] **FC-411 · P2 · PDF** (ANX-06) com `PdfRenderer`.
- [ ] **FC-412 · P2 · Colar imagem** via `contentReceiver` (ENV-11).
- [ ] **FC-413 · P3 · HTML isolado** (ANX-07): WebView sandbox ou abrir fora.
- [ ] **FC-414 · P3 · Modo conexão lenta** (MSG-18).
- [ ] **FC-415 · P1 · 🆕 Campo de mensagem rico** (ENV-21; no web, substituiu a fila de anexos).
  - Composição por **blocos** na ordem: texto ↔ peça (imagem, vídeo, áudio, arquivo, figurinha). A menção fica dentro do texto.
  - Envio na ordem do campo: cada trecho de texto vira tipo 1; cada peça, o seu tipo; linhas vazias entre peças são descartadas.
  - Figurinha e gravação com o campo vazio vão na hora; com algo escrito, entram como peça.
  - Backspace no início de um bloco de texto remove a peça anterior; peça de imagem com "×".
  - **CP:** enviar [texto, imagem, texto, figurinha] e conferir no web a mesma ordem.
- [ ] **FC-416 · P1 · 🆕 Decisão de produto: campo rico completo × simplificado.**
  - Completo: igual ao web (FC-415).
  - Simplificado: faixa de anexos + texto, enviado como [anexos…, texto]; perde a intercalação, mas é bem mais simples no Android.
  - Registrar a escolha em `docs/adr/`. Recomendação: começar simplificado na F3 e evoluir para o completo depois do Marco 1, porque as mensagens intercaladas que chegam do web são exibidas corretamente de qualquer forma.

## Bloco G — Ações sobre mensagens (F4)

- [ ] **FC-500 · P0 · Menu de toque longo** (ENV-20).
  - Reações rápidas 👍 ❤️ 😂 😮 😢 👏 🔥 + "mais".
  - Itens: Responder, Responder no privado (grupo), Encaminhar, Copiar, Ocultar (própria).
- [ ] **FC-501 · P0 · Reações** (ENV-17).
  - `PUT /mensagem/reacao {mensagem_id, emoji}` → `{mensagem_id, emoji, acao:"add"|"remove"}`.
  - WS 7. Chips com contagem e "quem reagiu". Emoji com no máximo 10 code points (senão dá 500).
  - 🆕 `d4435db`/`eaa8bac`: no máximo **5 emojis diferentes por pessoa** na mesma mensagem (o 6º → 400); conferir antes da reação otimista e avisar "Você já reagiu com 5 emojis nesta mensagem.". Na bolha, 5 chips à mostra e o resto num "+N".
- [ ] **FC-502 · P0 · Responder** (ENV-06).
  - `mensagem_referencia:{tipo:1, origem_mensagem_id}`. **O nome é invertido em relação ao banco**: `origem_mensagem_id` = a mensagem respondida (§10.3).
  - Deslizar para responder.
- [ ] **FC-503 · P0 · Ocultar** (ENV-18).
  - `DELETE /mensagem?id=` → com `excluida_em` (marca como oculta) ou sem (agendada, então remove).
  - Confirmação com os textos exatos do web.
- [ ] **FC-504 · P1 · Citação recursiva** (MSG-19), até 5 níveis; encaminhada não repete conteúdo.
- [ ] **FC-505 · P1 · Ir para mensagem** (MSG-06): contexto 30/30, depois 120/120, mais destaque.
- [ ] **FC-506 · P1 · Encaminhar** (ENV-08): conversas + contatos; `tipo:2`.
- [ ] **FC-507 · P1 · Copiar** (ENV-19).
- [ ] **FC-508 · P1 · Menções** (ENV-05, MSG-12).
  - Sugestões ao digitar `@`. Enviar `@[Nome](id)`; renderizar `@Nome` clicável.
- [ ] **FC-509 · P1 · Figurinhas Lottie** (ENV-04).
  - Embutir os 3 pacotes (24 animações) do `conversa-web/public/figurinhas/` em `assets/`.
  - Conteúdo tipo 7 `pacote/nome`. Tocar só quando visível; respeitar "remover animações".
- [ ] **FC-510 · P1 · Blocos de código e Markdown** (MSG-15).
  - Portar `codeBlocks.ts` com testes.
  - Copiar, recolher/expandir, destaque de sintaxe, Markdown renderizado.
- [ ] **FC-511 · P2 · Mermaid** (WebView offline com mermaid.js embutido).
- [ ] **FC-512 · P2 · Responder no privado** (ENV-07).
- [ ] **FC-513 · P2 · Detalhe de status** (MSG-09).
  - `GET /mensagem/status/detalhe?id=` → datas (aqui **não** são booleanos).
- [ ] **FC-514 · P2 · Agendar + cancelar agendada** (ENV-13, ENV-14, MSG-17).
  - `visivel_em` em ISO UTC; entre 5 min e 1 ano no futuro.
  - 🆕 `7322e83`: a agendada fica fora do chat até a hora; relógio com o número ao lado do microfone (campo vazio) abre "Mensagens agendadas" (horário, resumo, "Cancelar" com confirmação).
- [ ] **FC-515 · P3 · Atalhos de emoji, "inserir código", colar texto longo** (ENV-02, ENV-09, ENV-10).
- [ ] **FC-516 · P1 · 🆕 Bolha de votação** (MSG-20).
  - `GET /enquete?id=<conteudo>`, com cache por id (Room ou memória) e leituras simultâneas deduplicadas.
  - "📊 pergunta"; "Escolha uma opção" / "Escolha uma ou mais opções"; círculo (única) ou quadrado (múltipla) com ✓.
  - Contagem; barra `round(votos / total_votantes × 100)%`; nomes dos votantes; "1 pessoa votou" / "N pessoas votaram".
  - Votar: única troca ou tira; múltipla marca ou desmarca. `POST /enquete/votar {enquete_id, opcoes:[lista completa]}` → substituir o cache.
  - Desabilitar durante o voto; em erro, "Não foi possível votar".
  - WS 62 → reler só se a enquete estiver em cache/visível.
  - Resumo "Votação" em prévias, notificações, citações e atividades.
  - 🆕 `5cad911`/`785bdef` **Data final e encerramento:**
    - "· encerra hoje 18:00" no subtítulo;
    - "Definir/Alterar data final" e "Tirar data" para quem criou (`PATCH /enquete {enquete_id, encerra_em|null}`, `pode_alterar_prazo`);
    - "Encerrar votação" com confirmação para quem criou a votação ou o grupo (`POST /enquete/encerrar`, `pode_encerrar`);
    - encerrada (`encerrada` ou `encerra_em` vencido, inclusive com a bolha na tela): "🔒 Votação encerrada <quando>", sem votar, 🏆 na mais votada.
  - 🆕 `eaa8bac` Votação oculta revelada: resumo só de leitura (pergunta + votos por opção).
- [ ] **FC-517 · P1 · 🆕 Criar votação** (ENV-22).
  - "+" → "Votação" (só em grupo).
  - Bottom sheet "Nova votação": Pergunta (≤ 300); Opções 2–12 (≤ 200), "+ Adicionar opção", remover acima de 2; "Permitir várias escolhas".
  - `PUT /enquete {conversa_id, pergunta, opcoes (preenchidas, trim), multipla}`; mostrar o erro do servidor (repetidas, só grupo).
  - 🆕 `785bdef`: "Definir data final" opcional (`encerra_em`; no futuro, até 1 ano; sugestão amanhã na próxima hora cheia).
  - Depois, atualizar a conversa.
- [ ] **FC-519 · P2 · 🆕 `7322e83` Rascunho por conversa** (ENV-23): texto com menções, fila de anexos e resposta/encaminhamento pendente guardados no aparelho por conversa; restaurar ao abrir o chat; apagar ao enviar.
- [ ] **FC-520 · P2 · 🆕 `7322e83` Encaminhada de encaminhada** (MSG-19): na citação aninhada, não repetir os conteúdos que a citação de baixo já mostra.
- [ ] **FC-518 · P1 · 🆕 Esconder "Encaminhar" para mensagem de votação.** O servidor recusa tipo 8 em `PUT /mensagem` (400); no web o botão aparece e falha.

## Bloco H — Notificações e push (F5)

- [ ] **FC-600 · P0 · Firebase.**
  - Projeto e `google-services.json` (fora do Git ou por variante).
  - Plugin `com.google.gms.google-services`; `firebase-messaging` (sem `-ktx`, BOM 34+).
  - **CP:** `onNewToken` dispara.
- [ ] **FC-601 · P0 · Registrar o token.**
  - `PATCH /dispositivo {id, token_fcm}` no login e no `onNewToken`.
  - Logout → `token_fcm: null`.
- [ ] **FC-602 · P0 · Tratar o push atual** (Q7): `data {titulo, mensagem, conversa}`.
  - Notificação MessagingStyle por `conversa`, com id de notificação estável derivado do id da conversa (sem colisões; #14).
  - Se possível, sincronizar (`/mensagens/novas`) antes de exibir.
- [ ] **FC-603 · P0 · Notificação de mensagem com o app aberto (via WS).**
  - Suprimir na conversa aberta e nas arquivadas (NOT-01).
  - Som pelo canal.
- [ ] **FC-604 · P0 · Resposta direta e "Marcar como lida"** (AND-08) com `goAsync`/WorkManager (#42) e o `conversa_id` real.
- [ ] **FC-605 · P1 · Fechar a notificação** ao ler, ao arquivar e ao abrir a conversa (NOT-03).
- [ ] **FC-606 · P0 · Canais criados uma vez**, com ids versionados: mensagens, chamadas recebidas, chamada em andamento, sistema (#41).
- [ ] **FC-607 · P0 · Permissões pedidas em contexto** (NOT-06, AND-13): `POST_NOTIFICATIONS`, full-screen intent (Android 14+) e otimização de bateria, com explicação.
- [ ] **FC-608 · P2 · Atalhos de conversa** (AND-09).
- [ ] **FC-609 · P0 · Sem serviço permanente** (AND-07, #15).
  - WS só em primeiro plano; em segundo plano, FCM.
  - **Depende de S1/S2** para as chamadas tocarem com o app fechado.

## Bloco I — Chamadas (F6)

- [ ] **FC-700 · P0 · `CallManager` de escopo de aplicação** (corrige #1, #2, #8, #9).
  - Máquina de estados explícita:
    - estados: `Inativo`, `Chamando`, `Recebendo`, `Conectando`, `Ativa`, `Encerrando`;
    - toda transição terminal libera mídia, áudio, notificação e serviço.
  - Fonte única dos eventos 51–57.
  - **CP:** testes unitários de todas as transições (tabela do doc 03 §5.9 e doc 01 §9.2).
- [ ] **FC-701 · P0 · Core-Telecom** (AND-03, #20).
  - `CallsManager.addCall` para recebida e efetuada.
  - Atender e recusar pelo sistema, Bluetooth e carro; FGS `phoneCall`/`microphone`/`camera` com o tipo certo.
- [ ] **FC-702 · P0 · ICE/TURN** (Q1).
  - `GET /api/ice` (camelCase) **antes de cada** PeerConnection. Respeitar "Forçar relay" (`iceTransportPolicy`).
  - **CP:** chamada em 4G ↔ Wi-Fi com áudio nos dois lados.
- [ ] **FC-703 · P0 · WHIP/WHEP** (transplantar `WhipWhepClient`).
  - Publicar em `/webrtc/call-<chamada>-u-<eu>/whip`; assistir `/webrtc/call-<chamada>-u-<outro>/whep`.
  - Sem trickle: esperar o gathering com `GATHER_ONCE`, até 5 s.
  - Retentativas de WHEP: vídeo 40×1 s, áudio 12×0,8 s.
  - Ler o `Location` e fazer `DELETE` ao sair (#12).
  - Lock só para estado, nunca durante a rede (#18).
  - Inserir o peer **antes** de `setRemoteDescription` para não perder o `onAddTrack` (#10).
  - Uma factory e um EGL por processo; `dispose()` dos tracks e PCs (#37).
- [ ] **FC-704 · P0 · Codec gravável** (Q12): `setCodecPreferences` H264 → VP9 → VP8; Opus para o áudio.
  - **CP:** a gravação aparece no MediaMTX (`bin/mediamtx`).
- [ ] **FC-705 · P0 · Iniciar** (CHA-01).
  - `PUT /chamada/iniciar {tipo, usuarios:[{id}] (incluindo eu), conversa_id?}`; já publicar.
  - Som "chamando".
  - Participantes do grupo via `/conversa/usuarios` (Q5).
  - O `tipo` da chamada é ambíguo no servidor (§9.1, ver S11): enviar como o web envia.
- [ ] **FC-706 · P0 · Recebida** (CHA-03).
  - WS 51 → `GET /chamada/dados?id=` → Telecom + CallStyle + full-screen.
  - Toque que **só para** ao atender, recusar ou encerrar (#7).
  - Timeout de 30 s → `POST /chamada/recusar {id, nao_atendeu:true}` (ATV-03).
- [ ] **FC-707 · P0 · Atender e recusar** (CHA-04, CHA-05).
  - `POST /chamada/entrar {id}`; fallback de mídia (vídeo+áudio → só áudio → só recepção).
  - Recusar = **um** `POST /chamada/recusar`.
- [ ] **FC-708 · P0 · Ocupado** (CHA-06): 51 durante outra chamada → recusar com `nao_atendeu:true`.
- [ ] **FC-709 · P0 · Atendida ou recusada em outro aparelho** (CHA-07): 53/54 com o meu id → parar o toque.
- [ ] **FC-710 · P0 · Pendentes** (CHA-08, Q9).
  - `GET /chamadas/pendentes` (sem `usuarios`!) ao conectar e ao abrir pelo push.
  - Mais de 25 s → recusar com `nao_atendeu:true`; senão, tocar.
- [ ] **FC-711 · P0 · Cancelar** (CHA-09): `POST /chamada/cancelar {id}`.
- [ ] **FC-712 · P0 · Eventos da chamada ativa** (CHA-10).
  - 54 → assinar.
  - 55 → remover o peer e `GET /chamada/dados`; **se não sobrou ninguém com status "Entrou", sair**. Atenção: o servidor **não manda o 52** nesse caso (§9.2).
  - 53 em chamada 1:1 → encerrar. 52 → encerrar.
  - Sincronizar a cada 4 s.
- [ ] **FC-713 · P0 · Áudio** (AND-04, AND-05, #21).
  - Foco de áudio; rotas fone/alto-falante/Bluetooth/fone com fio (via Telecom).
  - Proximidade só no fone do aparelho; restaurar o modo ao sair.
- [ ] **FC-714 · P0 · UI de chamada acessível** (CHA-11, #25, #40).
  - Atender e recusar por toque (o gesto de arrastar é opcional).
  - Ícones corretos, estado reativo, `BackHandler`.
  - 🆕 Cores iguais ao web: microfone, câmera e som **vermelhos quando desligados**; tela, chat e ponteiro **azuis quando ligados**; os demais neutros.
- [ ] **FC-715 · P0 · Histórico** (CHA-22, Q10).
  - `GET /chamadas?participante=0&de=&ate=` → `ChamadaHistoricoItem` (§9.7).
  - Abas Todas/Perdidas, "Ligar novamente". Portar a lógica de "Hoje/Ontem" do `HistoricoChamadasAdapter`.
- [ ] **FC-716 · P1 · Vídeo** (CHA-12, CHA-16).
  - Grade, destaque e tela única.
  - Upgrade de áudio para vídeo: `POST /chamada/video`. Ao receber o 56, mostrar o modal "Apenas assistir / Transmitir também" (15 s).
  - Trocar câmera.
- [ ] **FC-717 · P1 · PiP + banner "voltar para a chamada"** (CHA-13, AND-06).
- [ ] **FC-718 · P1 · Adicionar participante** (CHA-14): `PUT /chamada/usuario {chamada_id, usuario_id}`.
- [ ] **FC-719 · P2 · Chat da chamada** (CHA-18): `PUT /chamada/chat {id}` → `{conversa_id}`; WS 57 `{acao:"chat"}`. 🆕 `eaa8bac`: o web cria o grupo no **primeiro toque no campo** e já abre o chat completo (antes: na primeira mensagem).
- [ ] **FC-720 · P2 · Indicador de fala** (CHA-19).
- [ ] **FC-721 · P2 · Somente recepção** (CHA-21).
- [ ] **FC-722 · P2 · Receber tela compartilhada + ponteiro remoto** (CHA-17).
  - Exibir o ponteiro (WS 57 `{acao:"ponteiro", alvo, x, y}`).
  - Enviar ponteiro tocando na tela compartilhada.
- [ ] **FC-723 · P2 · 🆕 Chat completo da chamada** (CHA-24): quando o grupo da chamada existe, o painel usa a própria tela de chat (lista + campo completos) com a conversa do grupo; antes da primeira mensagem, o painel simples (FC-719). 🆕 `eaa8bac`: sem grupo, o toque no campo cria o grupo e abre o chat completo.

## Bloco J — Atividades, pesquisa, perfil, configurações (F7)

- [ ] **FC-800 · P1 · Atividades** (ATV-01, ATV-02).
  - `GET /atividades?antes=&limite=30`; `GET /atividades/novas` → `{quantidade}`; `POST /atividades/vistas`.
  - WS 61 atualiza o badge. Textos e selos do web.
- [ ] **FC-801 · P1 · Pesquisa** (PES-01, PES-02).
  - `GET /pesquisar?texto=&conversa=<id|0>`; resultado → ir para mensagem.
- [ ] **FC-802 · P1 · Perfil** (AUT-06, AUT-07, AUT-08).
  - Editar nome/e-mail: `PATCH /usuario {id, …}`.
  - Senha: `POST /alterar-senha {senha_atual, senha}`.
  - Avatar: 256×256 JPEG 85%, upload, `PATCH /usuario {id, avatar_anexo_id}`.
- [ ] **FC-803 · P1 · Ver perfil de outro usuário** (AUT-10) + "Ver anexos".
- [ ] **FC-804 · P1 · Tela de Configurações** (CFG-01): Perfil, Aparência, Notificações, Chamadas, Permissões, Servidor, Sistema*, Acessos*, Sobre.
- [ ] **FC-805 · P2 · Tema** (CFG-02): sistema, claro ou escuro.
- [ ] **FC-806 · P1 · Permissões** (CFG-04) com atalhos para as configurações do sistema.
- [ ] **FC-807 · P2 · Qualidade das chamadas** (CFG-06/CHA-20): ruído, eco, ganho, bitrate de áudio (32/64/128), resolução, fps, banda.
- [ ] **FC-808 · P2 · Sistema e Acessos** (CFG-07, CFG-08, AUT-09).
  - Visíveis conforme `GET /usuario/permissoes`.
  - `GET/PATCH /parametros`; `GET /permissoes`, `PUT/DELETE /permissao/usuario`.
- [ ] **FC-809 · P2 · Página de anexos por conversa** (ANX-13): `GET /anexos?conversa=&direcao=&tipos=&antes=&limite=60`.

## Bloco K — SIP e extras (F8)

- [ ] **FC-900 · P2 · Configuração SIP** (SIP-01).
  - `GET/PUT/PATCH /sip`. O segundo PUT dá 500 (unique): usar PATCH se já existe.
- [ ] **FC-901 · P3 · Telefonia SIP nativa** (SIP-02..05).
  - **Spike técnico primeiro:** Linphone SDK (licença GPL/comercial — avaliar) ou PJSIP.
  - Precisa de transporte WSS e ICE via `/ice`; integrar com Core-Telecom.
- [ ] **FC-902 · P3 · Compartilhar a tela do celular** (CHA-02, CHA-15): `MediaProjection` + captura de áudio.
- [ ] **FC-903 · P3 · Bubbles / multi-janela** (CON-10).
- [ ] **FC-904 · P3 · Cor de destaque personalizada** (CFG-05).

## Bloco L — Qualidade transversal (contínuo)

- [ ] **FC-950 · P0 · Testes.**
  - Unitários: parsers (WS, datas, código, menções, classificação), máquina de estados da chamada, repositórios (MockWebServer).
  - Instrumentados: login, chat, chamada (smoke).
  - Meta: ≥ 70% no `:core` e no `CallManager`.
- [ ] **FC-951 · P0 · Acessibilidade** (AND-15): TalkBack nas telas críticas, alvos de 48 dp, `contentDescription`.
- [ ] **FC-952 · P0 · R8 ligado** com regras para kotlinx.serialization e WebRTC (#54); `signingConfig` de release fora do Git.
- [ ] **FC-953 · P1 · Observabilidade.**
  - Timber, com árvore silenciosa em release.
  - Crashlytics (opcional) **sem** dados de mensagem.
- [ ] **FC-954 · P1 · Política do Play.**
  - Declarações de FGS (`phoneCall`, `microphone`, `camera`) e de full-screen intent.
  - Permissões mínimas: sem `READ_MEDIA_*` (usar o Photo Picker), sem `SYSTEM_ALERT_WINDOW`, sem `DISABLE_KEYGUARD` (#43).
- [ ] **FC-955 · P1 · Matriz de testes manuais de chamada.**
  - Rede: Wi-Fi ↔ 4G.
  - Estado do aparelho: tela bloqueada, app morto, Bluetooth, chamada GSM concorrente.
  - Participantes: 1:1 e grupo de 4.
  - Mídia: upgrade de vídeo; gravação conferida no servidor.
  - Documentar em `docs/testes/chamadas.md`.

## Bloco M — Itens que dependem do servidor (ver doc 08)

| FC | Depende de | Por quê |
|---|---|---|
| FC-609, FC-706 | **S1** push de chamada | Sem isso, o celular só toca com o app aberto |
| FC-602 | **S2** prioridade alta e envio mesmo com WS de outro aparelho; **S3** `mensagem_id`/remetente no push | Entrega confiável e notificação rica |
| FC-403 | **S4** checar participação em `GET /anexo` | Segurança |
| FC-703 | **S4** autenticação do MediaMTX | Segurança |
| FC-509 | **S5** figurinhas | O app embute enquanto isso |
| FC-210/211 | **S8** criação de conversa atômica | Evitar conversa órfã |
| FC-308 | **S9** visualizar em lote | Hoje é uma chamada por mensagem |

---

## Ordem macro e estimativa grosseira (1 dev Android experiente)

| Etapa | Blocos | Estimativa |
|---|---|---|
| 1 | A (+ B se necessário) | 1–3 dias (+ 3–5 dias com o B) |
| 2 | C (fundação) | 2 semanas |
| 3 | D + E (conversas + mensagens núcleo) | 2–3 semanas |
| 4 | F (anexos/mídia) | 2 semanas |
| 5 | H (notificações/push) | 1 semana (+ servidor S1–S3) |
| 6 | I (chamadas, P0/P1) | 3–4 semanas |
| 7 | G (ações) | 2 semanas |
| 8 | J (atividades, pesquisa, config) | 1,5–2 semanas |
| 9 | K + P2/P3 restantes | sob demanda |

**Marco "substitui o app atual":** todos os P0 dos blocos C–I. Estimativa: ~10–12 semanas.

**Marco "paridade essencial com o web":** + todos os P1. Estimativa: ~16–18 semanas.

> As estimativas são ordens de grandeza para planejamento, não compromisso. Elas assumem que S1–S3 são feitos no servidor em paralelo.
