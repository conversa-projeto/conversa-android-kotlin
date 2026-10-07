# TODO — Android Conversa (nova base)

> **Como usar:** cada linha é **uma ação concreta**, pequena (de 5 min a 2 h), que dá para marcar como feita.
> **Ao concluir:** marcar `[x]` e, quando houver, anotar o commit ao lado (`✔ abc1234`).
> As linhas **Teste:** provam que a seção terminou. Só passe para a seção seguinte com elas marcadas.
> **Referências:**
> - `FC-xxx` = item da fila (`docs/auditoria-2026-10/06-fila-de-correcoes.md`)
> - `#n` = problema da auditoria do app (`04`)
> - `§x` = contrato do servidor (`01`)
> - `AUT-01` etc. = funcionalidade do web (`03`/`05`)
> - `Sn` = pendência do servidor (`08`)
>
> **Ordem:** seguir as etapas na ordem. Dentro de cada etapa, a ordem das linhas é a sugerida.

---

## Etapa 0 — Preparação do repositório

> Executada em 2026-10-06. Detalhe em `docs/historico/2026-10-06-05-etapa-0-repositorio.md`.

### 0.1 Salvar tudo que existe (FC-002)
- [x] Conferir em que branch estou: `git branch --show-current` (era `novo`)
- [x] Ver o que está pendente: `git status` → o trabalho de abril já estava commitado pelo usuário em `bb22aa2` ("atualizacao"); não precisou de commit "wip"
- [x] Criar a branch de preservação em `bb22aa2`: `git branch legado/abril-2026 HEAD`
- [x] Criar a pasta: `mkdir -p docs/legado/stashes`
- [x] Listar os stashes: `git stash list` (11)
- [x] Guardar cada stash sem perda numa tag: `git tag legado/stash-NN stash@{N}` (00…10)
- [x] Exportar cada stash como patch de texto, sem `.gradle/`, `.idea/` e `build/`: `docs/legado/stashes/NN.patch`
- [x] Ler o `commit.md` do `stash@{0}` (rascunhos de mensagens de commit de abril) e salvar em `docs/legado/commit-md-stash00.md`
- [x] Conferir os patches: sem senhas nem chaves
- [x] **Teste:** 11 patches na pasta e 11 tags `legado/stash-*`

### 0.2 Publicar (FC-003)
- [x] Criar a tag anotada: `git tag -a legado-v1-final legado/abril-2026`
- [ ] Enviar `novo`, `legado/abril-2026`, `legado-v1-final` e as tags dos stashes ⛔ o push foi bloqueado pela permissão automática do Claude Code; o usuário precisa rodar:
  `git push origin novo legado/abril-2026 legado-v1-final` e `git push origin "refs/tags/legado/stash-*"`
- [ ] **Teste:** branch e tags aparecem no GitHub

### 0.3 Gradle wrapper e limpeza (FC-004, FC-005)
- [x] No `.gitignore`, logo abaixo de `*.jar`, adicionar `!gradle/wrapper/gradle-wrapper.jar` ✔ 855e72d
- [x] Versionar o jar ✔ 855e72d
- [x] Remover os temporários (`tmpclaude-*-cwd`, `temp_function.kt`) ✔ 855e72d
- [x] Remover os `.OLD`/`.REMOVIDO` de `ui/chamada/` ✔ 855e72d
- [x] Remover o relatório de build versionado (`build/reports`) ✔ 855e72d
- [x] Parar de versionar `local.properties` e `.claude/settings.local.json` (os arquivos locais continuam) ✔ 855e72d
- [x] Conferir o `.gitignore` (`local.properties`, `.claude/settings.local.json`, `tmpclaude-*`) ✔ 855e72d
- [ ] Apagar do disco `conversa-android-kotlin (2).rar` (36 MB, não versionado) ⛔ é um backup local; apagar é irreversível, fica a critério do usuário
- [x] **Teste:** `./gradlew --version` roda com o JBR do Android Studio (Gradle 9.1.0, JVM 21)
- [ ] **Teste:** clone limpo + `gradlew assembleDebug` (fazer depois do push)

### 0.4 Documentação (FC-006, FC-007, FC-001)
- [x] Mover `legado/*.md` para `docs/legado/` (`git mv`)
- [x] Mover `documentacao-oficial/*.md` para `docs/legado/documentacao-oficial/`
- [x] Mover `legado/chamada-tcp/` para `docs/legado/chamada-tcp/`
- [x] Apagar `legado/erros.md` (vazio)
- [x] Criar `docs/legado/LEIA.md` (aviso de desatualizado + mapa dos stashes e de onde está o código legado)
- [x] Reescrever o `README.md`: o que é o app, "em reescrita", links para os docs
- [x] README → "Como compilar" (JDK 17+, `JAVA_HOME` do Android Studio, Git Bash e PowerShell)
- [x] README → "Servidor de desenvolvimento" (porta 443, `/api/` `/ws/` `/webrtc/` `/storage/`, IP da rede, CA do mkcert)
- [x] Apagar o `build_completo.bat` e o `rebuild_project.bat`
- [x] Escrever `docs/adr/0001-nova-base.md`
- [x] Criar o `CLAUDE.md` (onde está cada coisa, regra do histórico, TODO, build, convenções)
- [x] Criar `docs/historico/` (`indice.md` por fluxo, `_modelo.md`, entradas 01–05 do dia)
- [x] Commitar: `docs: organiza documentação, registra a nova base e cria o histórico`

### 0.5 Spike de mídia, para provar a chamada antes de investir (doc 07 §9) — ⛔ precisa do servidor de dev rodando e de um aparelho/emulador
- [ ] Criar o projeto descartável `spike-midia` (fora do app, Compose vazio)
- [ ] Adicionar OkHttp e `io.github.webrtc-sdk:android` (versão mais nova)
- [ ] Exportar a CA do mkcert do servidor de dev (`mkcert -CAROOT` → `rootCA.pem`)
- [ ] Copiar a CA para `res/raw/mkcert_ca.pem`
- [ ] Criar `res/xml/network_security_config.xml` com `<debug-overrides>` confiando em `@raw/mkcert_ca`
- [ ] Botão "Login": `POST /api/login {login, senha}`; logar o `id` e **só o tamanho** do token
- [ ] Abrir o WS em `wss://<host>/ws/` (com a barra final) e enviar `{"tipo":1,"token":"…"}`
- [ ] Mandar uma mensagem pelo web para esse usuário; conferir no log o evento `tipo:2`
- [ ] Chamar `GET /api/ice`; logar quantos servidores e as `urls` (a resposta é em camelCase)
- [ ] No web, ligar (áudio) para o usuário do spike; pegar o `chamada_id` do evento 51
- [ ] Spike: `POST /api/chamada/entrar {id}`
- [ ] Criar a PeerConnection com os `iceServers` do `/ice` e uma trilha de microfone (`SEND_ONLY`)
- [ ] Esperar o ICE gathering (`GATHER_ONCE`, até 5 s) e mandar o SDP completo
- [ ] Publicar: `POST /webrtc/call-<chamada>-u-<eu>/whip` com `Content-Type: application/sdp`
- [ ] Assistir: `POST /webrtc/call-<chamada>-u-<id do web>/whep` (`RECV_ONLY`), com retry em 404
- [ ] **Teste:** áudio nos dois sentidos, celular ↔ web, **em 4G** (Wi-Fi desligado)
- [ ] Sair: `POST /api/chamada/sair {id}`
- [ ] Repetir com vídeo e `setCodecPreferences` H264 → VP9 → VP8
- [ ] **Teste:** a gravação aparece no servidor (pasta de gravações do MediaMTX)
- [ ] Anotar: tempo até conectar, latência, falhas
- [ ] Escrever `docs/adr/0002-midia-webrtc.md` com o resultado

### 0.6 Pedidos ao servidor (abrir no repo `conversa`; detalhes no doc 08) — ⛔ abrir issues no GitHub depende de aprovação do usuário
- [ ] S1 — push de chamada (data-only, prioridade alta, TTL ~30 s) + push de "chamada encerrada"
- [ ] S2 — `android.priority: high` no push de mensagem; push por aparelho, não por usuário; vários dispositivos ativos
- [ ] S3 — `conversa_id`, `mensagem_id` e `remetente_id` no evento WS 2 e no push
- [ ] S4 — conferir a participação em `GET /anexo`; autenticação no MediaMTX; checar o acesso em `mensagem_referencia`
- [ ] S13 — refresh token
- [ ] S7, S8, S9, S11 — erros 409/400 amigáveis, criação de conversa atômica, visualizar em lote, `tipo` da chamada
- [ ] S5, S6, S10, S12, S14 — figurinhas no build, `DELETE /conversa`, `/recursos`, esquemas de resposta no OpenAPI, rate limit

---

## Etapa 1 — Fundação do app novo

### 1.1 Projeto (FC-100)
- [ ] Criar a branch `reescrita` a partir de `novo`
- [ ] Renomear o `app/` atual para `app-legado/` e tirá-lo do `settings.gradle.kts`
- [ ] Criar o módulo `app/` novo (Empty Compose Activity)
- [ ] `applicationId = "com.conversa.conversa"` (atualiza por cima do instalado) e `namespace = "com.conversa.app"`
- [ ] `compileSdk 36`, `targetSdk 36`, `minSdk 28`
- [ ] `jvmToolchain(17)`
- [ ] Limpar o `gradle.properties`: remover `android.builtInKotlin=false`, `android.newDsl=false` e as demais flags depreciadas
- [ ] Pôr **todas** as dependências e plugins no `gradle/libs.versions.toml`
- [ ] Atualizar o Kotlin, o Compose BOM, o AndroidX e as coroutines para as versões estáveis mais novas
- [ ] **Teste:** `gradlew assembleDebug` passa

### 1.2 Módulos (FC-101)
- [ ] Criar `:core:model` (Kotlin puro)
- [ ] Criar `:core:network`
- [ ] Criar `:core:database`
- [ ] Criar `:core:datastore`
- [ ] Criar `:core:data`
- [ ] Criar `:core:ui`
- [ ] Criar `:core:media`
- [ ] Criar `:core:webrtc`
- [ ] Criar `:core:testing`
- [ ] Criar `:feature:auth`, `:feature:conversas`, `:feature:chat`, `:feature:chamada`, `:feature:atividades`, `:feature:pesquisa`, `:feature:config`
- [ ] Criar convention plugins em `build-logic/` (android-library, compose, hilt, feature)
- [ ] **Teste:** o build passa e não há dependência circular

### 1.3 CI e qualidade (FC-102)
- [ ] Adicionar ktlint (ou spotless) e detekt com configuração inicial
- [ ] Ativar o Android Lint com `warningsAsErrors` só para as categorias de segurança
- [ ] Criar `.github/workflows/android.yml`: checkout → JDK 17 → cache do Gradle → `assembleDebug lint detekt test`
- [ ] Publicar o APK de debug como artefato do workflow
- [ ] **Teste:** abrir um PR e ver o check verde

### 1.4 Hilt e padrão de telas (FC-103)
- [ ] Adicionar o Hilt e criar a `ConversaApplication` com `@HiltAndroidApp`
- [ ] Criar a `MainActivity` (`@AndroidEntryPoint`) com `enableEdgeToEdge()`
- [ ] Criar `UiState<T>` (`Carregando` / `Conteudo` / `Vazio` / `Erro`) em `:core:ui`
- [ ] Criar a base de ViewModel com `StateFlow<UiState>` + `Channel` de eventos únicos
- [ ] Criar uma tela de exemplo + teste de ViewModel com `kotlinx-coroutines-test` e Turbine
- [ ] **Teste:** `gradlew test` passa

### 1.5 Design system (FC-104)
- [ ] Tema Material 3: cores claras/escuras (partir das cores do web), tipografia, formas
- [ ] Componente `Avatar(url, nome)` com fallback para a inicial — primeiro grafema, que funciona com emoji (GER-07)
- [ ] Componente `ConfirmDialog` com variante "perigo" (foco em Cancelar) (GER-03)
- [ ] `SnackbarHost` global para erros (GER-01)
- [ ] Componentes `EstadoVazio`, `EstadoErro` (com "Tentar de novo") e `Carregando`
- [ ] Ícones: adicionar `material-icons-extended`, ou vetores próprios
- [ ] Back preditivo: `android:enableOnBackInvokedCallback="true"` e `BackHandler` onde precisar
- [ ] `strings.xml` como regra: nenhum texto literal na UI
- [ ] Previews de todos os componentes (claro e escuro)

### 1.6 Configuração do servidor (FC-105)
- [ ] `ServerConfig(base)` com `api`, `ws` (`wss://host[:porta]/ws/`) e `webrtc` derivados
- [ ] Teste unitário: `https://x` → `wss://x/ws/`; `http://x:8080` → `ws://x:8080/ws/`; barra final sempre presente
- [ ] Guardar a `base` no DataStore e expor um `StateFlow<ServerConfig?>`
- [ ] Interceptor do OkHttp que troca o host pela configuração atual (não recriar o Retrofit)
- [ ] Tela "Servidor": campo URL, botão "Testar conexão" (espera 401 numa rota protegida = servidor OK), "Salvar"
- [ ] Mostrar o erro amigável: certificado inválido, host inacessível, não é um servidor Conversa
- [ ] **Teste:** trocar a URL em tempo de execução muda REST, WS e WebRTC sem reiniciar o app

### 1.7 TLS (FC-106)
- [ ] `res/xml/network_security_config.xml`: `cleartextTrafficPermitted="false"`
- [ ] `<debug-overrides>` com `@raw/mkcert_ca` (só no debug)
- [ ] Referenciar no manifest (`android:networkSecurityConfig`)
- [ ] **Teste:** debug conecta ao servidor de dev com mkcert; release recusa certificado inválido
- [ ] Garantir que não existe `TrustManager` "aceita tudo" em lugar nenhum (busca no código)

### 1.8 Rede (FC-107, FC-109)
- [ ] Prover um **único** `OkHttpClient` via Hilt (timeouts, `pingInterval(20s)`)
- [ ] `HttpLoggingInterceptor`: `BODY` só em `BuildConfig.DEBUG`, com `redactHeader("Authorization")`
- [ ] `AuthInterceptor`: adiciona `Authorization: Bearer <token>` quando há sessão
- [ ] Retrofit + converter kotlinx.serialization (`ignoreUnknownKeys = true`, `explicitNulls = false`, `coerceInputValues = true`)
- [ ] Ler o erro padrão do servidor: corpo `{error: string}` (§3)
- [ ] Mapear 401 → evento global `SessaoExpirada`
- [ ] Mapear resposta HTML (502/504 do nginx) → "Servidor indisponível"
- [ ] Mapear 400/403/404/429/500 → mensagens amigáveis (manter a original no log de debug)
- [ ] Testes com MockWebServer: sucesso, 401, `{error}`, HTML, timeout

### 1.9 Modelos do contrato (FC-108)
- [ ] Baixar o OpenAPI do servidor (`/api/docs/json`) para `docs/contrato/openapi.json`, como referência das entradas de cada rota
- [ ] Copiar JSONs reais de `conversa/tests/*.test.ts` para `:core:testing/fixtures/`
- [ ] Serializer de data: ISO-8601 com `Z` → `Instant`, tolerante (aceita sem `Z`, aceita espaço)
- [ ] Serializer de id tolerante (número ou string → `Long`)
- [ ] DTO login: `{id, nome, email, telefone, avatar_identificador, dispositivo{…}, token}`
- [ ] DTO conversa (§9 do doc 01 / CON-01)
- [ ] DTO mensagem (§10.2): `excluida_em`, `visivel_em`, `mensagem_referencia` (recursivo, opcional), `reacoes` (opcional), conteúdos com `transcricao_status`/`transcricao`
- [ ] DTO conteúdo de chamada (tipo 6), com datas fora do padrão (§9.8)
- [ ] DTOs de status (`/mensagem/status` = booleanos; `/mensagem/status/detalhe` = datas)
- [ ] DTOs de anexo (`PUT /anexo` nos dois formatos de resposta; `GET /anexo` → `{url}`; `/anexos`)
- [ ] DTOs de chamada (`dados da chamada`, `pendentes` **sem `usuarios`**, histórico `ChamadaHistoricoItem`)
- [ ] DTO ICE (camelCase: `urls`, `username`, `credential`)
- [ ] DTOs de atividade, permissões, parâmetros, SIP, contatos, membros
- [ ] DTO `Enquete` (`opcoes[].votantes[]`, `total_votantes`, `meus_votos`) e resposta do `PUT /enquete` (mensagem + `enquete_id`)
- [ ] Mapeadores DTO → modelo de domínio em `:core:model`
- [ ] Um teste de desserialização por DTO, usando as fixtures

### 1.10 Banco local (FC-110)
- [ ] Entidades Room: `ConversaEntity`, `MensagemEntity`, `ConteudoEntity`, `ReacaoEntity`, `ContatoEntity`, `AtividadeEntity`, `ChamadaHistoricoEntity`, `EnvioPendenteEntity`, `SyncEstadoEntity` (cursor `ate`)
- [ ] DAOs com `Flow` para a UI
- [ ] Exportar o schema (`room.schemaLocation`) e criar o teste de migração
- [ ] **Teste:** inserir e ler cada entidade

### 1.11 Sessão segura (FC-111)
- [ ] `SessaoStore`: token, `usuario_id`, nome e `dispositivo_id`, cifrados (Tink/Keystore)
- [ ] **Nunca** salvar a senha
- [ ] `allowBackup="false"` (ou `data_extraction_rules` excluindo a sessão)
- [ ] `SessaoRepository` com `StateFlow<Sessao?>`
- [ ] **Teste:** matar o app, reabrir e a sessão continua; um backup não contém o token

### 1.12 WebSocket — `RealtimeClient` (FC-112)
- [ ] Sealed class `EventoSocket` com todos os tipos: 0, 2, 3, 4, 5, 7, 9, 40, 51–57, 60, 61, 62 (`EnqueteAtualizada {enquete_id, conversa_id}`) e `Desconhecido`
- [ ] Parser JSON → `EventoSocket` (testes com um payload real de cada tipo, §6.6)
- [ ] Máquina de estados: `Desconectado` / `Conectando` / `Autenticando` / `Conectado` / `Aguardando`
- [ ] No `onOpen`: enviar `{"tipo":1,"token":…}` (sem resposta de sucesso; erro vem como 0/9)
- [ ] `SharedFlow<EventoSocket>` multi-assinante (`extraBufferCapacity = 64`)
- [ ] Id de geração por conexão; ignorar callbacks de sockets antigos
- [ ] Backoff com jitter: 1 s → 2 → 4 → … → 30 s
- [ ] Conectar só com sessão válida **e** app em primeiro plano (`ProcessLifecycleOwner`) **ou** chamada ativa
- [ ] Desconectar ao ir para segundo plano (com uma pequena tolerância, ex.: 10 s)
- [ ] `enviarSinal(chamadaId, dados)` → `{tipo:57, chamada_id, dados}`
- [ ] Nenhum log de frame com token ou conteúdo
- [ ] **Teste:** derrubar o Wi-Fi → reconecta sozinho; nunca há 2 sockets abertos

### 1.13 Sincronização (FC-113)
- [ ] `SyncManager.ressincronizar()` disparado ao conectar, ao voltar ao primeiro plano e ao receber push
- [ ] `GET /mensagens/novas?desde=<cursor>` → buscar as mensagens que faltam por conversa → Room → salvar `ate`
- [ ] `GET /conversas` → Room
- [ ] `GET /contatos/online` → presença
- [ ] `GET /atividades/novas` → badge
- [ ] `GET /chamadas/pendentes` → `CallManager`
- [ ] Tratar o WS 2 (sem `conversa_id`, pode chegar duplicado) como gatilho de sincronização incremental, com debounce
- [ ] **Teste:** desligar a rede por 2 min, mandar mensagens pelo web e religar → tudo aparece, sem duplicar

---

## Etapa 2 — Sessão, conversas, contatos e presença

### 2.1 Login e entrada (FC-200, AUT-01)
- [ ] Tela Splash/decisão: sem servidor → Servidor; sem sessão → Login; com sessão → Principal
- [ ] Tela Login: logo, "Usuário", "Senha", botão "Entrar"/"Entrando…", link "Não tem conta? Criar conta"
- [ ] `POST /api/login {login, senha, dispositivo_id?}` (reenviar o `dispositivo_id` salvo)
- [ ] Salvar o token e `dispositivo.id`; `trim()` no login
- [ ] Tratar 429/503 (limite de 10/min do nginx) → "Muitas tentativas, aguarde"
- [ ] Tratar resposta sem token → "Resposta de login inválida"
- [ ] **Teste:** logar, matar o app, abrir → entra direto

### 2.2 Dispositivo (FC-201, AUT-04)
- [ ] Após o login: `PATCH /dispositivo {id, nome, modelo, versao_so, plataforma:"android"}`
- [ ] Cortar os textos nos limites (nome/modelo 50, versão 15) para não dar 500
- [ ] **Teste:** conferir a linha do dispositivo no banco

### 2.3 Sessão expirada e logout (FC-202, AUT-03, AUT-05)
- [ ] Observar `SessaoExpirada` (401) em qualquer lugar → limpar a sessão → Login com o aviso "Sessão expirada"
- [ ] Outros erros na inicialização mantêm a sessão e tentam de novo (como no web)
- [ ] Logout: `PATCH /dispositivo {id, token_fcm:null}`
- [ ] Logout: fechar o WS e encerrar a chamada ativa
- [ ] Logout: limpar o Room, o cache de imagens e de áudio e cancelar todas as notificações
- [ ] **Teste:** depois do logout, mensagens do web não geram notificação

### 2.4 Cadastro (FC-203, AUT-02)
- [ ] Tela "Crie sua conta": Nome, Usuário, E-mail, Senha
- [ ] `PUT /api/usuario {nome, login, email, senha}` (rota pública)
- [ ] Respeitar os limites (nome 100, login 50, e-mail 100)
- [ ] E-mail duplicado (500 do Postgres hoje) → "E-mail já cadastrado"
- [ ] Sucesso → "Conta criada com sucesso!" → voltar ao Login com o usuário preenchido

### 2.5 Navegação principal
- [ ] NavHost com rotas tipadas: Servidor, Login, Cadastro, Principal, Chat(conversaId, mensagemId?), Membros, Perfil, Visualizador, Configurações…
- [ ] Barra inferior: Conversas, Chamadas, Atividades (com badge), Configurações
- [ ] Deep link `conversa://chat/{id}?mensagem={id}`

### 2.6 Lista de conversas (FC-204, FC-205, CON-01, CON-13)
- [ ] Repositório: Room como fonte; `GET /conversas` para atualizar
- [ ] Ordem: fixadas por `fixada_ordem`, depois as demais por `mensagem_id` desc
- [ ] Título: `descricao || nome || "Conversa #id"`
- [ ] Item: avatar, bolinha online (direta), título, etiqueta "Grupo", alfinete se fixada
- [ ] Prévia: menção `@[Nome](id)` → `@Nome`; bloco de código → `Código (linguagem)`; vazio → "Sem mensagens"
- [ ] Badge de não lidas (`mensagens_sem_visualizar`), escondido nas arquivadas
- [ ] Hora: hoje `HH:mm`, ontem "Ontem", senão `dd/MM/aa`
- [ ] Três pontinhos animados quando alguém digita naquela conversa
- [ ] Atualizar (sem spinner por cima da lista) em WS 2, 3 e 40, ao enviar e ao ler
- [ ] Pull-to-refresh
- [ ] Estados vazio e de erro
- [ ] **Teste:** comparar lado a lado com o web (8 casos do CON-01)

### 2.7 Filtro e "Nova conversa" (FC-206, CON-02)
- [ ] Campo de busca: filtra por título e prévia
- [ ] Com termo, mostrar também as arquivadas (com a etiqueta "Arquivada")
- [ ] Seção "Nova conversa" com contatos sem conversa direta (nome/login/e-mail)

### 2.8 Menu da conversa, fixar e arquivar (FC-207, FC-208, FC-209)
- [ ] Toque longo no item → bottom sheet: Fixar/Desafixar, Arquivar/Desarquivar
- [ ] Fixar: adicionar ao fim das fixadas → `PATCH /conversa/fixadas {conversas:[ids na ordem]}`
- [ ] Reordenar fixadas: arrastar (ou "mover para cima/baixo") → mesmo PATCH com a lista inteira
- [ ] Otimista; erro → recarregar `GET /conversas` e mostrar o aviso
- [ ] Arquivar: `PATCH /conversa/arquivada {conversa, arquivada:true}` → tirar das fixadas, cancelar a notificação
- [ ] Seção recolhível "Arquivadas (N)" no fim da lista
- [ ] Arquivada: sem som e sem notificação

### 2.9 Contatos e conversa direta (FC-210, CON-06, CON-11)
- [ ] Tela/aba Contatos: `GET /usuario/contatos`, busca local
- [ ] Avatar: usar o `avatar_url` do contato ou, na falta, o da conversa direta
- [ ] "Obter ou criar direta": procurar `tipo=1 && destinatario_id=contato`
- [ ] Se não existe: `PUT /conversa {descricao:"", tipo:1}`
- [ ] → `PUT /conversa/usuario {conversa_id, usuario_id:eu}`
- [ ] → `PUT /conversa/usuario {conversa_id, usuario_id:contato}`
- [ ] → `GET /conversas` → abrir o chat
- [ ] Falha no meio → mostrar o erro (e registrar a conversa órfã no log); depende de S8 para ficar atômico

### 2.10 Grupos (FC-211, FC-212, CON-07, CON-08)
- [ ] Tela "Criar grupo": "Nome do grupo", busca, checkboxes de contatos
- [ ] Validações: "Informe o nome do grupo." / "Selecione ao menos um usuário."
- [ ] `PUT /conversa {descricao, tipo:2}` → `PUT /conversa/usuario` para cada membro **e para mim** → abrir o grupo
- [ ] Tela "Membros do grupo": `GET /conversa/usuarios?conversa=` (id = `conversa_usuario_id`)
- [ ] Renomear: `PATCH /conversa {id, descricao}` → "Grupo renomeado com sucesso."
- [ ] Adicionar: seletor de contatos fora do grupo → `PUT /conversa/usuario`
- [ ] Remover (não para mim mesmo): `DELETE /conversa/usuario?id=<conversa_usuario_id>`
- [ ] Sair do grupo: `DELETE /conversa/usuario?id=<meu conversa_usuario_id>`

### 2.11 Presença e conexão (FC-213, FC-214, PRE-01, GER-02)
- [ ] `PresencaRepository`: conjunto de ids online a partir de `GET /contatos/online` + WS 60 `{usuario_id, online}`
- [ ] Bolinha verde na lista, no cabeçalho do chat (direta) e nos contatos
- [ ] Banner "Sem conexão em tempo real", depois de 5 s desconectado, com "Tentar agora"
- [ ] **Teste:** abrir e fechar o web com outro usuário → a bolinha acende e apaga no celular

---

## Etapa 3 — Mensagens (núcleo)

### 3.1 Tela de chat (FC-300, CON-09)
- [ ] Cabeçalho: voltar, avatar (toque → perfil do outro), título, bolinha online
- [ ] Subtítulo: direta → "online"/nada; grupo → nomes dos membros; digitando/gravando substitui
- [ ] Ações do topo: chamada de voz, chamada de vídeo, pesquisa, membros (grupo)
- [ ] Lista: `LazyColumn` com `reverseLayout`
- [ ] Campo de mensagem + botão Enviar/Microfone + botão de anexo

### 3.2 Carregar e paginar (FC-301, MSG-01)
- [ ] Abrir: `GET /mensagens?conversa=X&mensagemreferencia=0&mensagensprevias=80&mensagensseguintes=0` (máximo de 100 por chamada)
- [ ] Mostrar o que está no Room antes da rede
- [ ] Rolar para cima: `mensagemreferencia=<mais antiga>&mensagensprevias=60`; parar quando vier vazio
- [ ] Rolar para baixo (depois de um salto): `mensagemreferencia=<mais nova>&mensagensseguintes=60`
- [ ] Ordem: `coalesce(visivel_em, inserida)`, depois `id`; ids negativos (enviando) no fim
- [ ] Indicadores "Carregando mensagens anteriores/seguintes"
- [ ] **Teste:** rolar até o início de uma conversa com 1000+ mensagens sem travar

### 3.3 Classificação e bolhas (FC-302, FC-303, MSG-07)
- [ ] Portar `classificarMensagem.ts` com a prioridade: oculta > chamada > imagem > enquete (tipo 8, mesmo com referência) > figurinha > código > emoji > com referência > texto curto > padrão
- [ ] Testes unitários copiando os casos do web
- [ ] Bolha padrão (texto multi-linha) e bolha curta (≤ 60 caracteres, uma linha, hora ao lado)
- [ ] Bolha só de emojis (fonte grande, sem fundo) (MSG-14)
- [ ] Placeholders para imagem, figurinha, código e citação (preenchidos nas etapas seguintes)
- [ ] Placeholder da votação (tipo 8): "📊 Votação" + "Abra no computador para votar" (a bolha nunca fica vazia; a bolha real vem na 7.12)

### 3.4 Mensagem oculta (FC-304, MSG-16)
- [ ] `excluida_em != null` → bolha "Mensagem oculta" + hora/status
- [ ] Toque alterna revelar/ocultar o conteúdo original
- [ ] Sem menu e sem reações; fora da galeria de imagens
- [ ] Citação de mensagem oculta → "Mensagem oculta" sem conteúdo

### 3.5 Bolha de chamada (FC-305, MSG-13)
- [ ] Ler o JSON do conteúdo tipo 6: `{chamada_id, tipo, status, iniciada, finalizada, duracao, participantes[]}`
- [ ] Título: "Chamada de áudio"/"Chamada de vídeo" (+ " em grupo" se > 2)
- [ ] Direta: "<remetente> · mm:ss" ou o status ("Recusada", "Perdida", "Cancelada")
- [ ] Grupo: lista de participantes com a duração ou o status
- [ ] Cores: verde (encerrada), vermelho (recusada/perdida); sem status de entrega
- [ ] Toque → ligar de novo (mesmo tipo)

### 3.6 Separadores e hora (FC-306, MSG-02)
- [ ] Separador de dia ("seg., 05/10/2026" ou "Hoje"/"Ontem")
- [ ] Grupo: nome do remetente acima das bolhas recebidas quando muda o remetente
- [ ] Hora `HH:mm` formatada direto do `Instant` (nada de `toString()`, ver #29)

### 3.7 Enviar texto (FC-307, ENV-01)
- [ ] Ao enviar: criar a mensagem otimista com id negativo e `enviando=true` no Room
- [ ] Guardar em `EnvioPendente` e disparar o `EnvioWorker` (WorkManager, com rede)
- [ ] Worker: `PUT /mensagem {conversa_id, conteudos:[{ordem:1, tipo:1, conteudo}]}` → trocar o id negativo pelo real
- [ ] Erro definitivo → marcar a mensagem como "falhou" com o botão "Reenviar"/"Apagar"
- [ ] O texto do campo só é limpo depois de salvo no Room (nunca se perde)
- [ ] Após enviar: rolar ao fim e atualizar a lista de conversas
- [ ] **Teste:** enviar em modo avião → religar → a mensagem sai sozinha

### 3.8 Lida e status (FC-308, FC-309, MSG-04, MSG-08, MSG-10)
- [ ] Detectar as mensagens visíveis na tela (de outros, não visualizadas, app em primeiro plano)
- [ ] Para cada uma: `POST /mensagem/visualizar {conversa, mensagem}` (fila serial; trocar por lote quando S9 existir)
- [ ] Descontar o contador da conversa (otimista); ao chegar a 0, cancelar a notificação da conversa
- [ ] Ícone de status nas minhas mensagens: relógio → ✓ → ✓✓ cinza → ✓✓ cor primária
- [ ] WS 3 `{grupo:conversaId, mensagens:"12,13"}` → `GET /mensagem/status?conversa=&mensagem=12,13` → atualizar `recebida`/`visualizada`/`reproduzida`/`excluida_em`
- [ ] Se algum id do WS 3 não está carregado e a conversa está aberta → recarregar
- [ ] **Teste:** ler no web → o ✓✓ do celular fica azul sem recarregar

### 3.9 Linha "Últimas", FABs e links (FC-310, FC-311, MSG-03, MSG-05, MSG-11)
- [ ] Linha "Últimas" antes da primeira não lida (posição fixada ao abrir; fica até sair da conversa)
- [ ] Ao abrir com não lidas: posicionar a primeira não lida no topo; sem não lidas: ir ao fim
- [ ] FAB "ir para o final" quando estiver longe do fim
- [ ] FAB "Há novas mensagens" quando chegar mensagem com o usuário longe do fim
- [ ] Links clicáveis (`https?://` e `www.`) com as regras de pontuação do web; abrir no navegador

### 3.10 Digitando (FC-312, ENV-15)
- [ ] Ao digitar texto não vazio: `POST /conversa/digitando {id: conversaId}`, no máximo 1 a cada 2,5 s (o último é adiado, não descartado)
- [ ] Resetar o throttle ao enviar
- [ ] Receber o WS 4 `{conversa_id, usuario_id}`; ignorar o meu; expirar em 4 s; sumir quando chegar mensagem
- [ ] Textos: "Digitando…", "Ana está digitando…", "Ana e Beto estão digitando…", "Ana, Beto e Caio…", "… e outras N pessoas…"
- [ ] Quem não é contato: "Usuário #id"

### 3.11 Deep link (FC-314, CON-12)
- [ ] Abrir `chat/{id}` pela notificação → **recarregar as mensagens** antes de mostrar
- [ ] Com `mensagem={id}` → ir para a mensagem (usa a 6.3)

---

## Etapa 4 — Anexos e mídia

### 4.1 Upload (FC-400, ANX-02)
- [ ] Calcular o SHA-256 em streaming (`DigestInputStream`, sem carregar o arquivo inteiro)
- [ ] `PUT /anexo {identificador, tipo, nome, extensao (≤ 10), tamanho}`
- [ ] Resposta "não existe" → URL assinada de upload (vale 300 s)
- [ ] Resposta "já existe" → `id` (string) + URL de download → pular o upload
- [ ] `PUT` do arquivo na URL assinada (streaming, `RequestBody` a partir do `ContentResolver`) com progresso
- [ ] `POST /anexo/confirmar?identificador=` (na **query**)
- [ ] URL vencida no meio → pedir de novo
- [ ] Tudo dentro de um `UploadWorker` (sobrevive a fechar o app)
- [ ] **Teste:** um vídeo de 200 MB sobe sem estourar a memória; o mesmo arquivo de novo não sobe

### 4.2 Fila e envio de anexos (FC-401, FC-402, ANX-01, ANX-03)
- [ ] Botão de anexo → Galeria (Photo Picker múltiplo), Câmera, Documento (`OpenMultipleDocuments`)
- [ ] Fila acima do campo: miniaturas (imagem), ícone + nome + tamanho (outros), "Remover"
- [ ] Tipo: `image/*` → 2; `audio/*` → 4; gravação do microfone → **5**; resto → 3 (vídeo = 3 com extensão)
- [ ] Mensagem: [encaminhados] → texto → figurinha → arquivos, com `ordem` 1..n
- [ ] Erro em qualquer upload cancela a mensagem inteira (como no web)
- [ ] Indicador de upload (nome, barra, %) na bolha otimista

### 4.3 URLs assinadas (FC-403, ANX-14)
- [ ] `GET /anexo?identificador=` → `{url}`
- [ ] Cache identificador → (url, expira_em)
- [ ] Coil 3 com o OkHttp compartilhado e chave de cache = identificador (não a URL)
- [ ] Imagem falhou (403/expirada) → renovar a URL e tentar uma vez

### 4.4 Imagens e vídeos (FC-404, FC-408, ANX-04, ANX-05)
- [ ] Bolha de imagem (proporção preservada, hora sobre a imagem)
- [ ] Visualizador em tela cheia: pager com todas as imagens e vídeos da conversa (exceto ocultas)
- [ ] Zoom com pinça e duplo toque; legenda = textos da mesma mensagem; tira de miniaturas
- [ ] Vídeo na bolha: primeiro quadro + ícone de play
- [ ] Vídeo no visualizador: Media3 com controles
- [ ] Ações do visualizador: compartilhar, baixar

### 4.5 Áudio (FC-405, ANX-10)
- [ ] `PlayerUnico` (Media3): só um áudio toca por vez
- [ ] Bolha de áudio: play/pause, barra com seek, `mm:ss`; nome do arquivo (tipo 4) ou só o player (tipo 5)
- [ ] Download sob demanda para o cache: arquivo `.part` → rename ao terminar
- [ ] Áudio de outro não ouvido: botão verde; o primeiro play chama `POST /mensagem/reproduzir {conversa, mensagem}`
- [ ] Parar o áudio ao sair da conversa ou ao começar uma chamada

### 4.6 Gravação de áudio (FC-406, ANX-11, ENV-16)
- [ ] Transplantar o `AudioRecorderHelper` (pegar `Exception` em `start()`, #48)
- [ ] Segurar o microfone ≥ 300 ms e soltar → envia direto
- [ ] Toque curto (ou arrastar para cima) → modo travado com barra: Descartar, tempo, Pausar/Continuar, Ouvir, Enviar
- [ ] Arrastar para o lado → cancelar
- [ ] Enviar como conteúdo **tipo 5**
- [ ] Durante a gravação: `POST /conversa/gravando {id}` a cada 2,5 s
- [ ] Receber o WS 5 → "Gravando áudio…" em vermelho (prioridade sobre o "digitando")
- [ ] Permissão de microfone pedida na hora, com um launcher só para isso (#32)

### 4.7 Arquivos e download (FC-407, ANX-09)
- [ ] Bolha de arquivo: ícone pela extensão, nome, tamanho, "Baixar"/"Abrir"
- [ ] Baixar: URL assinada → `MediaStore.Downloads` (sanitizar o nome: nada de `../`)
- [ ] Abrir: `ACTION_VIEW` com `FileProvider`
- [ ] Notificação de download concluído

### 4.8 Transcrição (FC-409, ANX-12)
- [ ] Abaixo dos áudios (tipos 4 e 5): estado inicial a partir de `transcricao_status`/`transcricao`
- [ ] Botão "Transcrever" → `PUT /anexo/transcricao {identificador}`
- [ ] "Transcrevendo…" → `GET /anexo/transcricao?identificador=` a cada 3 s
- [ ] Concluída → texto (ou "(nenhuma fala reconhecida)"); erro → "Não foi possível transcrever. Tentar de novo"
- [ ] Esconder o botão se o servidor não tem transcritor (por enquanto: esconder após o primeiro erro de configuração; depois, via S10)

### 4.9 Receber compartilhamento (FC-410, AND-10)
- [ ] `intent-filter` `ACTION_SEND`/`ACTION_SEND_MULTIPLE` para `text/*`, `image/*`, `video/*`, `*/*`
- [ ] Tela "Enviar para…" com conversas e contatos
- [ ] Abrir o chat escolhido com os itens já na fila

### 4.10 Extras de mídia (FC-411…414)
- [ ] PDF: visualizador com `PdfRenderer` (páginas sob demanda, zoom, "página X de Y")
- [ ] Colar imagem do teclado/área de transferência (`contentReceiver`)
- [ ] HTML anexado: abrir externamente (ou WebView sem acesso a arquivos/cookies)
- [ ] Economia de dados: com rede limitada, "Toque para carregar" em imagens e vídeos

### 4.11 Campo de mensagem rico (FC-415, FC-416, ENV-21)
- [ ] Escrever o ADR com a decisão: campo **simplificado** (faixa de anexos + texto, enviado como [anexos…, texto]) agora × **completo** (blocos intercalados, igual ao web)
- [ ] Simplificado: faixa horizontal de peças acima do campo (miniatura, nome, "×"), na ordem de inserção
- [ ] Simplificado: envio = peças na ordem + o texto por último
- [ ] Completo: modelo `List<BlocoComposicao>` (`Texto(String)` / `Peca(anexo ou figurinha)`) no ViewModel
- [ ] Completo: UI com um `TextField` por bloco de texto e um chip/miniatura por peça, numa coluna rolável (máx. ~40% da tela)
- [ ] Completo: inserir peça no ponto do cursor (divide o bloco de texto em dois)
- [ ] Completo: Backspace no início de um bloco de texto remove a peça anterior e junta os textos
- [ ] Completo: envio na ordem; cada texto vira tipo 1; linhas vazias entre peças descartadas; menção `@[Nome](id)` dentro do texto
- [ ] Figurinha e gravação com o campo vazio vão na hora; com algo escrito, entram como peça
- [ ] **Teste:** enviar [texto, imagem, texto, figurinha] e conferir a mesma ordem no web

---

## Etapa 5 — Notificações e push

### 5.1 Firebase (FC-600)
- [ ] Criar o app Android no projeto Firebase (o mesmo do servidor)
- [ ] Baixar o `google-services.json` para `app/` (fora do Git ou por variante)
- [ ] Aplicar o plugin `com.google.gms.google-services`
- [ ] Dependência `firebase-messaging` (BOM 34+, sem `-ktx`)
- [ ] `ConversaFcmService` com `onNewToken` e `onMessageReceived`
- [ ] **Teste:** `onNewToken` dispara na primeira abertura

### 5.2 Token (FC-601)
- [ ] No login e no `onNewToken`: `PATCH /dispositivo {id, token_fcm}`
- [ ] No logout: `PATCH /dispositivo {id, token_fcm:null}`

### 5.3 Canais e permissões (FC-606, FC-607)
- [ ] Criar os canais uma vez no `Application`: `mensagens_v1`, `chamadas_recebidas_v1`, `chamada_ativa_v1`, `sistema_v1`
- [ ] Pedir `POST_NOTIFICATIONS` depois do login, com explicação
- [ ] Android 14+: verificar `canUseFullScreenIntent()`; se negado, explicar e levar às configurações
- [ ] Explicar e pedir a isenção de otimização de bateria (opcional, para chamadas)

### 5.4 Notificação de mensagem (FC-602, FC-603, NOT-01, NOT-02)
- [ ] Push `data {titulo, mensagem, conversa}` → sincronizar (`SyncManager`) → montar a notificação
- [ ] MessagingStyle por conversa, com id de notificação = id da conversa (sem colisão)
- [ ] Título = nome do remetente/grupo; avatar como ícone grande; corpo resumido ("Imagem", "Gravação de áudio", "Figurinha", "Arquivo"…)
- [ ] Com o app aberto (via WS): não notificar se a conversa está aberta na tela
- [ ] Nunca notificar conversas arquivadas
- [ ] Toque → deep link para a conversa

### 5.5 Ações da notificação (FC-604, AND-08)
- [ ] Resposta direta (RemoteInput) → `PUT /mensagem` no `conversa_id` real → atualizar a notificação
- [ ] "Marcar como lida" → `POST /mensagem/visualizar` das mensagens da conversa
- [ ] Receivers com `goAsync()` ou WorkManager expedited (#42)

### 5.6 Fechar notificações (FC-605, NOT-03)
- [ ] Cancelar ao ler tudo, ao arquivar e ao abrir a conversa

### 5.7 Sem serviço permanente (FC-609, AND-07)
- [ ] Confirmar que não existe nenhum foreground service fora de chamada
- [ ] Em segundo plano, depender só do FCM (as chamadas precisam de S1)
- [ ] **Teste:** app fechado + mensagem pelo web → notificação chega (com o aparelho em Doze: `adb shell dumpsys deviceidle force-idle`)

### 5.8 Atalhos de conversa (FC-608)
- [ ] Publicar `ShortcutInfo` de longa duração para as conversas recentes (aparecem na seção "Conversas" do Android)

---

## Etapa 6 — Chamadas

### 6.1 Infra de mídia (FC-703, FC-704)
- [ ] Transplantar o `WhipWhepClient` para `:core:webrtc`, usando o OkHttp compartilhado
- [ ] WHIP/WHEP: ler o header `Location` e fazer `DELETE` ao encerrar
- [ ] `PeerConnectionFactory` e `EglBase` únicos por processo
- [ ] `PublicadorWhip`: PC `SEND_ONLY`, áudio Opus (32/64/128 kbps conforme a configuração), vídeo opcional
- [ ] `setCodecPreferences`: H264 → VP9 → VP8
- [ ] Câmera: padrão 360p a 15 fps (como no web para celular); trocar frontal/traseira
- [ ] `AssinanteWhep` por participante: PC `RECV_ONLY`; registrar o peer **antes** de `setRemoteDescription` (#10)
- [ ] ICE: `GET /api/ice` a cada PC; `GATHER_ONCE`; esperar até 5 s; "forçar relay" → `iceTransportPolicy = RELAY`
- [ ] Retentativas WHEP 404: vídeo 40×1 s; áudio 12×0,8 s
- [ ] ICE `FAILED` → recriar só o PC daquele peer
- [ ] Lock só para mudar o estado, nunca durante a rede (#18)
- [ ] `dispose()` em tracks e PCs ao encerrar (#37)
- [ ] **Teste:** o arquivo gravado aparece no MediaMTX

### 6.2 `CallManager` (FC-700)
- [ ] Estados: `Inativo`, `Chamando`, `Recebendo`, `Conectando`, `Ativa`, `Encerrando`
- [ ] `StateFlow<EstadoChamada>`: chamada, participantes, tracks, mute, câmera, rota de áudio, duração, modo de exibição
- [ ] Assinar o `SharedFlow` do WS para os eventos 51–57 (único consumidor de chamada)
- [ ] `encerrar()` idempotente: DELETE WHIP/WHEP → `dispose` → liberar o áudio → cancelar as notificações → Telecom disconnect → parar o serviço
- [ ] Testes unitários de **todas** as transições (incluindo eventos fora de ordem e duplicados)

### 6.3 Core-Telecom (FC-701, AND-03)
- [ ] Adicionar `androidx.core:core-telecom`
- [ ] Registrar o `CallsManager` com as capacidades (vídeo)
- [ ] Recebida e efetuada via `addCall(...)` com os callbacks answer/disconnect/setActive/setInactive
- [ ] Usar os endpoints de áudio do Telecom para rotear (fone, alto-falante, Bluetooth, fone com fio)
- [ ] Manifest: `FOREGROUND_SERVICE_PHONE_CALL`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CAMERA`, `MANAGE_OWN_CALLS`
- [ ] **Teste:** atender pelo botão do fone Bluetooth; receber uma ligação GSM durante a chamada → coloca em espera

### 6.4 Iniciar chamada (FC-705, CHA-01)
- [ ] Botões de voz e vídeo no cabeçalho do chat
- [ ] Participantes: direta = [eu, outro]; grupo = `GET /conversa/usuarios?conversa=` (todos, inclusive eu)
- [ ] Pedir as permissões (microfone; câmera se vídeo) na hora
- [ ] `PUT /chamada/iniciar {tipo, usuarios:[{id}], conversa_id}` → já publicar via WHIP
- [ ] Tela "Chamando…" + som de chamando em loop
- [ ] Bloquear se já existe chamada ("Já existe uma chamada em andamento")
- [ ] Cancelar enquanto chama: `POST /chamada/cancelar {id}` (FC-711)
- [ ] Timeout local (ex.: 45 s sem ninguém atender) → cancelar

### 6.5 Chamada recebida (FC-706, FC-707, CHA-03, CHA-04, CHA-05)
- [ ] WS 51 (ou push S1) → `GET /chamada/dados?id=` → `CallManager` em `Recebendo`
- [ ] Telecom `addCall` (entrada) + notificação CallStyle `forIncomingCall` + full-screen intent
- [ ] Tela de chamada recebida (`showWhenLocked`, `turnScreenOn`): nome, avatar, "Vídeo + Áudio"/"Somente áudio"
- [ ] Botões **tocáveis**: Recusar, Atender e, em vídeo, "Atender só assistindo"
- [ ] O toque **só para** ao atender, recusar, encerrar remoto ou no timeout (#7)
- [ ] 30 s sem resposta → `POST /chamada/recusar {id, nao_atendeu:true}`
- [ ] Atender: mídia vídeo+áudio → só áudio → só recepção (fallback) → `POST /chamada/entrar {id}` → publicar → assinar os outros
- [ ] "Atender" pela notificação usa `PendingIntent.getActivity` ou Telecom (nunca broadcast → activity, #3)
- [ ] Recusar: **um** `POST /chamada/recusar {id}`

### 6.6 Regras automáticas (FC-708, FC-709, FC-710, ATV-03)
- [ ] 51 durante outra chamada ou toque → `recusar {nao_atendeu:true}` sem tocar
- [ ] 51 da minha própria chamada → ignorar
- [ ] 53/54 com o **meu** `usuario_id` enquanto toca (atendi/recusei em outro aparelho) → parar o toque, voltar a `Inativo`
- [ ] Ao conectar o WS / abrir pelo push: `GET /chamadas/pendentes` (**sem `usuarios`**)
- [ ] Pendente criada há mais de 25 s → `recusar {nao_atendeu:true}`; senão, tocar

### 6.7 Durante a chamada (FC-712, CHA-10)
- [ ] 54 de outro: se eu estava `Chamando` → `Ativa` + cronômetro; assinar quem entrou
- [ ] 55: remover o peer → `GET /chamada/dados` → se ninguém mais com status "Entrou", sair (`POST /chamada/sair`) — o servidor **não manda o 52** nesse caso
- [ ] 53 de outro em chamada de 2 pessoas → encerrar
- [ ] 52 → encerrar
- [ ] A cada 4 s em `Ativa`: `GET /chamada/dados` e reconectar quem caiu ou está sem trilha
- [ ] Botão sair: `POST /chamada/sair {id}`

### 6.8 Áudio (FC-713, AND-04, AND-05)
- [ ] Foco de áudio durante a chamada (pausa a música)
- [ ] Seletor de rota: Fone do aparelho / Alto-falante / Bluetooth / Fone com fio (via Telecom)
- [ ] Sensor de proximidade só quando a rota é o fone do aparelho
- [ ] Ao encerrar: devolver o modo normal e limpar o dispositivo de comunicação
- [ ] Parar qualquer áudio de mensagem ao entrar em chamada

### 6.9 Tela da chamada ativa (FC-714, CHA-11)
- [ ] Cabeçalho: "Chamando…"/"Em chamada"/"Encerrando…", duração, tipo, nº de pessoas
- [ ] Controles: microfone, câmera (vídeo), trocar câmera, rota de áudio, adicionar pessoa, chat, sair
- [ ] Estado de mute e câmera reativo (StateFlow), ícones corretos, `contentDescription` em tudo
- [ ] Cores: microfone, câmera e som **vermelhos quando desligados**; tela, chat e ponteiro **azuis quando ligados**; demais neutros
- [ ] `BackHandler`: voltar = minimizar (não encerra)
- [ ] Tiles: nome ("Você"), iniciais sem vídeo, faixa vermelha de erro de conexão
- [ ] **Teste:** atender e desligar só com o TalkBack

### 6.10 Histórico de chamadas (FC-715, CHA-22)
- [ ] `GET /chamadas?participante=0&de=YYYY-MM-DD&ate=YYYY-MM-DD` → `ChamadaHistoricoItem`
- [ ] Abas Todas / Perdidas (status 5); busca por contato; filtro de período
- [ ] Agrupar: Hoje / Ontem / `dd/MM/aaaa` (portar a lógica do adapter antigo)
- [ ] Item: avatar (grupo: "Grupo (N)"), seta efetuada/recebida (`criado_por == eu`), hora, "Vídeo"/"Áudio" · duração ou status, cor
- [ ] Toque → abrir a conversa; botão "Ligar novamente" (mesmo tipo e participantes)

### 6.11 Vídeo (FC-716, CHA-12, CHA-16)
- [ ] Renderers com `key(track)` e `onRelease` (#39); PiP local com `setZOrderMediaOverlay(true)`
- [ ] Modos: Grade (1/2/3/4 colunas conforme o número), Destaque, Tela única com setas
- [ ] Toque no tile → destacar; toque no destaque → voltar à grade
- [ ] Upgrade áudio→vídeo: abrir a câmera → republicar → `POST /chamada/video {id}` (proteger contra toque duplo)
- [ ] Receber o WS 56: já em vídeo → reassinar quem republicou; senão, modal "<nome> ativou o vídeo" com "Apenas assistir" / "Transmitir também" (15 s → "Apenas assistir")
- [ ] FGS com o tipo `camera` adicionado quando o vídeo liga

### 6.12 Minimizar (FC-717, CHA-13, AND-06)
- [ ] Picture-in-Picture em chamada de vídeo (`setAutoEnterEnabled` no Android 12+)
- [ ] Banner "Toque para voltar à chamada" no topo das outras telas
- [ ] Notificação em andamento CallStyle `forOngoingCall` com cronômetro nativo e "Desligar"

### 6.13 Recursos extras de chamada (FC-718…722)
- [ ] Adicionar participante: contatos fora da chamada → `PUT /chamada/usuario {chamada_id, usuario_id}` (um por vez) → `GET /chamada/dados`
- [ ] Chat da chamada: `PUT /chamada/chat {id}` → `{conversa_id}` na primeira mensagem; WS 57 `{acao:"chat", conversa_id}`; painel com só texto
- [ ] Chat **completo** da chamada: quando o grupo existe, abrir a própria tela de chat (lista + campo completos) num painel/bottom sheet com a conversa do grupo; antes da primeira mensagem, o painel simples
- [ ] Indicador de fala: nível de áudio local e remoto (> 0,02, segura 400 ms) → anel verde no avatar
- [ ] Somente recepção: entrar sem microfone/câmera; botões "Ativar microfone"/"Ativar câmera"
- [ ] Tela remota compartilhada: WS 57 `{acao:"tela", ativa}` → destacar a tela
- [ ] Ponteiro remoto: WS 57 `{acao:"ponteiro", alvo, x, y}` → desenhar com nome e cor (`id % 5`); some em 5 s

### 6.14 Matriz de testes manuais (FC-955)
- [ ] Criar `docs/testes/chamadas.md` com a matriz
- [ ] Wi-Fi ↔ 4G; 4G ↔ 4G
- [ ] App aberto / em segundo plano / fechado (precisa de S1) / tela bloqueada
- [ ] Bluetooth; fone com fio; chamada GSM concorrente
- [ ] 1:1 áudio; 1:1 vídeo; grupo de 4; upgrade para vídeo; adicionar participante
- [ ] Atender no web com o celular tocando (o celular para de tocar)
- [ ] Gravação conferida no servidor

---

## Etapa 7 — Ações sobre mensagens

### 7.1 Menu de ações (FC-500, ENV-20)
- [ ] Toque longo na bolha → barra de reações 👍 ❤️ 😂 😮 😢 👏 🔥 + "mais"
- [ ] Itens: Responder; Responder no privado (grupo e mensagem de outro); Encaminhar; Copiar; Ocultar (minha e não oculta)
- [ ] Não abrir para chamada, oculta ou mensagem ainda sem id real

### 7.2 Reações (FC-501, ENV-17)
- [ ] `PUT /mensagem/reacao {mensagem_id, emoji}` → `{mensagem_id, emoji, acao:"add"|"remove"}` (toggle), otimista
- [ ] Chips abaixo da bolha: emoji + contagem; destacado se eu reagi; toque alterna
- [ ] Toque longo no chip → quem reagiu (foto, nome, hora)
- [ ] Receber o WS 7 `{conversa_id, mensagem_id, emoji, acao, usuario_id}`
- [ ] "Mais" → seletor de emoji do sistema (emoji com no máximo 10 code points)

### 7.3 Responder (FC-502, ENV-06)
- [ ] Deslizar a bolha para a direita → responder
- [ ] Barra acima do campo: nome + resumo + ×
- [ ] Envio: `mensagem_referencia:{tipo:1, origem_mensagem_id:<id respondida>}`
- [ ] A mensagem otimista já mostra a citação

### 7.4 Ocultar (FC-503, ENV-18, ENV-14)
- [ ] Confirmação: "Ocultar mensagem" / "Ela continua na conversa, marcada como oculta." / "Ocultar" (perigo) / "Cancelar"
- [ ] `DELETE /mensagem?id=` → com `excluida_em`: marcar como oculta; sem: remover (era agendada)
- [ ] Atualizar a prévia da lista ("Mensagem oculta")
- [ ] Agendada futura: o texto vira "Cancelar mensagem agendada" / "Ela não será enviada." / "Cancelar envio" / "Voltar"
- [ ] Erro → "Não foi possível ocultar"

### 7.5 Citação e ir para a mensagem (FC-504, FC-505, MSG-19, MSG-06)
- [ ] Bloco de citação na bolha: remetente (resposta) ou "Encaminhado de <remetente>"; conteúdos da original
- [ ] Citação aninhada recursiva até 5 níveis
- [ ] Encaminhada: não repetir os conteúdos iguais aos da referência
- [ ] Toque na citação → ir para a original
- [ ] Ir para a mensagem: se não carregada, `GET /mensagens?…&mensagemreferencia=<id>&mensagensprevias=30&mensagensseguintes=30` (se não vier, 120/120) → substituir a lista → centralizar + destacar por 1,2 s
- [ ] Original em outra conversa → abrir aquela conversa (se participo)
- [ ] Erro → "Não foi possível localizar esta mensagem no contexto da conversa."

### 7.6 Encaminhar, copiar e responder no privado (FC-506, FC-507, FC-512)
- [ ] Encaminhar: tela de destino (conversas exceto a de origem + contatos sem direta), com busca
- [ ] `PUT /mensagem` no destino com os conteúdos copiados (ordem 1..n) e `mensagem_referencia:{tipo:2, origem_mensagem_id}`
- [ ] Contato sem conversa → criar a direta antes; depois abrir o destino
- [ ] Copiar: texto → área de transferência; imagem → `ClipData` com URI
- [ ] Responder no privado: abrir/criar a direta com o remetente e deixar a mensagem pendente como encaminhada (`tipo:2`, conteúdos antes do texto)

### 7.7 Menções (FC-508, ENV-05, MSG-12)
- [ ] Ao digitar `@` + texto → popup com até 6 contatos/membros (nome/login)
- [ ] Escolher → inserir `@Nome` destacado no campo; guardar o id
- [ ] No envio: converter para `@[Nome](id)`; "@Ana" não casa com "@Anabela"
- [ ] Renderizar `@[Nome](id)` como `@Nome` clicável → abrir a conversa direta
- [ ] Testes do parser (portar `mencoesTexto.ts`)

### 7.8 Figurinhas (FC-509, ENV-04)
- [ ] Copiar `conversa-web/public/figurinhas/**` (basico 8, rostos 7, coisas 9) para `app/src/main/assets/figurinhas/`
- [ ] Adicionar `lottie-compose`
- [ ] Aba "Figurinhas" no seletor do campo, por pacote
- [ ] Toque → enviar na hora como tipo 7 `pacote/nome` (como resposta, se houver uma pendente)
- [ ] Bolha de 160 dp; toca só quando visível; com "remover animações" fica parada; falha → "Figurinha"

### 7.9 Código e Markdown (FC-510, FC-511, MSG-15)
- [ ] Portar `codeBlocks.ts` (abertura/fechamento, aninhamento, markdown) com testes
- [ ] Bloco de código: cabeçalho com a linguagem, "Copiar" → "Copiado!"
- [ ] Destaque de sintaxe (lib leve) para as linguagens do web
- [ ] Recolher acima de ~240 dp: "Expandir código"/"Recolher código"
- [ ] ` ```md `/` ```markdown ` → Markdown renderizado (Markwon ou compose-markdown), alternância "Visualizar"/"Código"
- [ ] ` ```mermaid ` → WebView offline com mermaid.js embutido (P2)

### 7.10 Detalhe de status e agendamento (FC-513, FC-514)
- [ ] Toque no ✓ → bottom sheet: `GET /mensagem/status/detalhe?id=` (aqui os campos são **datas**)
- [ ] Direta: Enviada / Recebida / Visualizada / Ouvida (áudio) / Oculta; sem data → "Aguardando"
- [ ] Grupo: "Visualizada por (N)", "Recebida por (N)", "Aguardando (N)"
- [ ] Toque longo no Enviar → "Agendar": data e hora (padrão amanhã 08:00)
- [ ] Validações: ≥ 5 min no futuro; ≤ 1 ano
- [ ] `visivel_em` em ISO UTC; selo "Agendada para hoje HH:MM / amanhã / dd/MM HH:MM"; some na hora exata

### 7.11 Pequenos extras (FC-515)
- [ ] Atalhos `:)` → 🙂 etc. (tabela do `emojiAtalhos.ts`), só como palavra solta e fora de código
- [ ] Colar texto com mais de 10 linhas → sugerir "Enviar como código"
- [ ] Tela "Inserir código" simples (linguagem + texto monoespaçado)

### 7.12 Votação em grupo (FC-516, FC-517, FC-518, MSG-20, ENV-22)
- [ ] Store/repositório de enquetes: cache por id; leituras simultâneas deduplicadas
- [ ] Bolha: `GET /enquete?id=<conteudo>`; "Carregando votação..." / erro
- [ ] Bolha: nome do remetente (grupo, mensagem de outro), "📊 <pergunta>", "Escolha uma opção" / "Escolha uma ou mais opções"
- [ ] Bolha: cada opção com círculo (única) ou quadrado (múltipla) + ✓, texto, contagem, barra `round(votos / total_votantes × 100)%`, nomes dos votantes
- [ ] Bolha: rodapé "1 pessoa votou" / "N pessoas votaram" + hora/status
- [ ] Votar: única → troca o voto (tocar na marcada tira); múltipla → marca/desmarca
- [ ] `POST /enquete/votar {enquete_id, opcoes:[lista completa]}` → substituir o cache; desabilitar durante o voto; erro → "Não foi possível votar"
- [ ] WS 62 → reler `GET /enquete` só se estiver em cache/na tela
- [ ] Resumo "Votação" em prévia, notificação, citação, atividade e chat da chamada
- [ ] Criar: "+" → "Votação" (só em grupo) → bottom sheet "Nova votação"
- [ ] Criar: "Pergunta" (≤ 300, placeholder "Ex.: Onde vamos almoçar?"), "Opções" 2–12 (≤ 200), "+ Adicionar opção", remover acima de 2
- [ ] Criar: "Permitir várias escolhas" / "Cada pessoa pode marcar mais de uma opção."
- [ ] Criar: "Cancelar" / "Criar votação" ("Criando..."), habilitado com pergunta e ≥ 2 opções
- [ ] `PUT /enquete {conversa_id, pergunta, opcoes (preenchidas, trim), multipla}` → atualizar a conversa; mostrar os erros do servidor
- [ ] Esconder "Encaminhar" no menu de uma mensagem de votação (o servidor recusa com 400)
- [ ] **Teste:** criar no celular, votar no web → a barra atualiza no celular sem recarregar

---

## Etapa 8 — Atividades, pesquisa, perfil e configurações

### 8.1 Atividades (FC-800, ATV-01, ATV-02)
- [ ] Aba Atividades: `GET /atividades?antes=0&limite=30`
- [ ] Paginação: perto do fim → `antes=<id da última>`; fim quando vierem < 30
- [ ] Agrupar Hoje / Ontem / `dd/MM/aaaa`
- [ ] Item: avatar com selo (emoji, "↩", "@", "✆"), "**autor** descrição", "em <grupo>", prévia, hora, "Nova"
- [ ] Descrições: "reagiu <emoji> à sua mensagem", "respondeu sua mensagem", "mencionou você", "ligou (chamada perdida)" / "ligou (chamada de vídeo perdida)"
- [ ] Toque: com `mensagem_id` → ir para a mensagem; sem → abrir a conversa
- [ ] Badge: `GET /atividades/novas` → `{quantidade}` ao conectar e a cada WS 61 (`99+`)
- [ ] Abrir a aba → `POST /atividades/vistas` → zerar o badge (itens seguem marcados como "Nova" enquanto aberta)
- [ ] Vazio: "Nenhuma atividade ainda." / "Reações, respostas, menções e chamadas perdidas aparecem aqui."

### 8.2 Pesquisa (FC-801, PES-01, PES-02)
- [ ] Na conversa: ícone de busca → campo → `GET /pesquisar?texto=&conversa=<id>` → resultados (mais recentes primeiro) → ir para a mensagem
- [ ] Global: "Pesquisar em todos os chats" → `GET /pesquisar?texto=&conversa=0`
- [ ] Resultados agrupados por conversa: remetente, data `dd/MM/aa HH:mm`, trecho com o termo destacado
- [ ] Estados "Pesquisando…" / "Nenhum resultado encontrado."

### 8.3 Perfil (FC-802, AUT-06, AUT-07, AUT-08)
- [ ] Editar nome e e-mail → `PATCH /usuario {id, nome, email}` → "Dados atualizados com sucesso."
- [ ] Alterar senha: atual, nova (≥ 6), confirmação → `POST /alterar-senha {senha_atual, senha}`
- [ ] Mensagens: "Preencha todos os campos de senha.", "A nova senha deve ter pelo menos 6 caracteres.", "A confirmação da senha não confere.", "Senha alterada com sucesso."
- [ ] Avatar: Photo Picker → recorte quadrado central 256×256 → JPEG 85% → upload (4.1) → `PATCH /usuario {id, avatar_anexo_id}`
- [ ] Remover avatar: `PATCH /usuario {id, avatar_anexo_id:null}`
- [ ] Avatar com URL expirada → renovar (no máximo a cada 30 s)

### 8.4 Perfil de outro usuário (FC-803, AUT-10)
- [ ] Toque no avatar da direta → bottom sheet: foto, nome, e-mail, telefone ("Não informado"), "Ver anexos", "Ligar"

### 8.5 Configurações (FC-804…808)
- [ ] Tela com seções: Perfil, Aparência, Notificações, Chamadas, Permissões, Servidor, Sistema*, Acessos*, Ramal SIP, Sobre
- [ ] Aparência: Sistema / Claro / Escuro (+ Material You opcional)
- [ ] Notificações: atalhos para as configurações de cada canal
- [ ] Permissões: estado de notificações, microfone, câmera, full-screen intent e bateria, com botão para o sistema
- [ ] Chamadas: redução de ruído, cancelamento de eco, ganho automático, qualidade de áudio (32/64/128), resolução (360/720/1080), fps (15/24/30), banda; "Restaurar padrão"
- [ ] Sobre: versão do app, servidor conectado, licenças
- [ ] `GET /usuario/permissoes` → mostrar Sistema (`parametros`) e Acessos (`permissoes`) só para quem tem
- [ ] Sistema: `GET /parametros`; editar Firebase (ID do projeto, e-mail, chave — nunca exibida), forçar relay TURN, dias de gravação (0–36500), transcritor (URL, idioma); `PATCH /parametros` só com o que mudou
- [ ] Acessos: `GET /permissoes` → tabela usuário × permissão com busca; `PUT /permissao/usuario {usuario_id, codigo}`; `DELETE /permissao/usuario?usuario_id=&codigo=`; aviso de "modo aberto"

### 8.6 Anexos da conversa (FC-809, ANX-13)
- [ ] A partir do perfil ou do grupo: "Ver anexos"
- [ ] `GET /anexos?conversa=&direcao=(enviados|recebidos|)&tipos=2,3,4,5&antes=<anexo_id>&limite=60`
- [ ] Filtros: Todos / Enviados / Recebidos e Todos / Imagens / Arquivos / Áudios / Gravações
- [ ] Imagens em grade; demais em lista (nome, tamanho, data, autor); ações "Abrir mensagem" e "Baixar"
- [ ] Paginação infinita

---

## Etapa 9 — Extras (avaliar antes de fazer)

- [ ] Ramal SIP — configuração: `GET /sip`; `PUT /sip` (se não existe) ou `PATCH /sip {id, …}` (se existe)
- [ ] Ramal SIP — spike: Linphone SDK (licença GPL/comercial) vs PJSIP; transporte WSS + ICE via `/ice`
- [ ] Ramal SIP — registro, discador com DTMF, chamada recebida, integração com o Telecom
- [ ] Compartilhar a tela do celular (`MediaProjection` + FGS `mediaProjection` + captura de áudio)
- [ ] Bubbles para conversas
- [ ] Cor de destaque personalizada

---

## Qualidade (fazer junto, ao longo de tudo)

- [ ] Testes unitários: parsers (WS, datas, código, menções, classificação), `CallManager`, repositórios (MockWebServer)
- [ ] Testes instrumentados: login, abrir conversa e enviar, chamada (smoke)
- [ ] Meta de cobertura: ≥ 70% no `:core` e no `CallManager`
- [ ] Acessibilidade: TalkBack nas telas críticas, alvos de 48 dp, `contentDescription`, contraste
- [ ] R8 ligado no release + regras para kotlinx.serialization e WebRTC
- [ ] `signingConfig` de release com a keystore fora do Git (variáveis de ambiente/secrets do CI)
- [ ] Timber: árvore de debug; nada de log em release
- [ ] (Opcional) Crashlytics sem dados de mensagem
- [ ] Manifest com as permissões mínimas: nada de `READ_MEDIA_*`, `SYSTEM_ALERT_WINDOW` ou `DISABLE_KEYGUARD`
- [ ] Play Console: declarações de FGS (`phoneCall`, `microphone`, `camera`) e de full-screen intent
- [ ] Ícone adaptativo, splash screen API, nome do app, prints para a loja

---

## Opcional — Hotfixes no app ANTIGO (só se alguém depende dele até o novo ficar pronto)

- [ ] Logs: `BODY` só em debug + `redactHeader("Authorization")` (`RetrofitClient.kt:23-29`)
- [ ] Remover os logs de frame/token (`SocketManager.kt:176,417`; `ChatActivity.kt:1151`; `ConversaFcmService.kt:32,48`)
- [ ] Parar de salvar a senha (`UserPreferences.kt`) + `allowBackup="false"`
- [ ] Porta de dev 4430 → 443 (`app/build.gradle.kts:26-27`)
- [ ] Não parar o toque no `onCreate` da `ChamadaActivity` (`:150-151`)
- [ ] Atender pela notificação com `PendingIntent.getActivity`
- [ ] `@POST("mensagem/visualizar")` com corpo `{conversa, mensagem}`
- [ ] `excluida_em` em `Mensagem` → mostrar "Mensagem oculta"
- [ ] `GET /anexo` → ler `{url}` e usar a URL assinada (Glide, áudio, download)
- [ ] Upload pelo fluxo novo (JSON → PUT na URL → confirmar)
- [ ] `GET /api/ice` antes de cada PeerConnection (TURN)
- [ ] Trocar `GET /conversa/dados` por `GET /conversa/usuarios`
- [ ] **Teste:** login, texto, imagem e áudio nos dois sentidos, chamada 1:1 em 4G, atender pela notificação com a tela bloqueada

---

## Revisão periódica
- [ ] Marcar o que foi feito neste TODO (com o commit)
- [ ] Atualizar `docs/auditoria-2026-10/05-matriz-paridade.md`
- [ ] Ver se o servidor mudou: `git -C ../conversa log --since="1 week ago" --oneline`; se mudou o contrato → atualizar o doc 01 e as fixtures
- [ ] Ver se o web ganhou funcionalidade nova: `git -C ../conversa-web log --since="1 week ago" --oneline` → acrescentar aqui
