# Auditoria do Android — outubro/2026 (LEIA PRIMEIRO)

**Data:** 2026-10-06
> 🆕 **Atualização 2026-10-06 (noite):** incorporados os commits `8031fa5` (servidor: votação em grupo — 3 rotas, WS 62, conteúdo tipo 8, migração 035) e `39d06f9` (web: campo de mensagem rico, votação, chat completo na chamada, cores dos botões da chamada). Agora são **67 rotas** e **136 funcionalidades** no web. Os itens alterados nos docs estão marcados com 🆕. Observação: o web mostra "Encaminhar" em mensagem de votação, mas o servidor recusa (400) — no Android, esconder (FC-518).
>
> 🆕 **Atualização 2026-10-08:** incorporados os commits do servidor `d4435db` (no máximo 5 emojis diferentes por pessoa numa mensagem) e `5cad911` (votação com data final e encerramento: `POST /enquete/encerrar`, `PATCH /enquete`, migração 036), e os do web `eaa8bac` (painel "Dados do grupo" com anexos, "+N" nas reações, resumo da votação oculta, chave nos botões da chamada, grupo do chat da chamada criado no primeiro toque) e `785bdef` (data final e encerrar na votação). Agora são **69 rotas**; as funcionalidades do web seguem **136**. Marcações nos docs: 🆕 com o commit.

**Escopo:** `conversa-android-kotlin` (branch `novo`, último commit `e3e2a65` em 2026-04-26) comparado a
`conversa` (servidor, HEAD `7f670c3` → 🆕 `8031fa5` → 🆕 `5cad911`) e `conversa-web` (HEAD `bfb79d8` → 🆕 `39d06f9` → 🆕 `785bdef`), de 2026-10-06 e 2026-10-07.
**Método:** leitura integral do código dos três repositórios, do histórico Git do servidor desde abril (Delphi → Node → Bun/Elysia) e build real do Android (`assembleDebug`, sucesso, JDK 21 do Android Studio). Nenhum código foi alterado; só esta pasta foi criada.

---

## Documentos desta pasta

| # | Arquivo | Para que serve |
|---|---|---|
| 00 | `00-LEIAME.md` | Este índice, o resumo executivo e a **decisão recomendada** |
| 01 | `01-contrato-servidor-atual.md` | **Fonte da verdade** do contrato atual do servidor: 67 rotas, WebSocket, FCM, anexos/MinIO, chamadas/MediaMTX, gravação, permissões, parâmetros e 46 "pegadinhas" para clientes |
| 02 | `02-mudancas-servidor-desde-abril.md` | O que mudou no servidor desde a última versão do Android, endpoint por endpoint, com as **quebras confirmadas** |
| 03 | `03-inventario-web.md` | As **136 funcionalidades** do cliente web (IDs AUT/CON/MSG/ENV/ANX/PES/CHA/SIP/NOT/PRE/ATV/CFG/GER), textos de UI e detalhes de WebRTC: é o checklist de paridade |
| 04 | `04-auditoria-android.md` | Auditoria interna do app Android: estado do repo, build, arquitetura, **57 problemas** (#1–#57) com severidade e arquivo:linha, e avaliação "evoluir x recomeçar" |
| 05 | `05-matriz-paridade.md` | Cruzamento das 136 funcionalidades do web com o estado no Android (o que existe, o que está quebrado, o que falta) |
| 06 | `06-fila-de-correcoes.md` | **A fila de trabalho**, priorizada e numerada (FC-xxx), com dependências e critério de pronto |
| 07 | `07-plano-nova-base.md` | Arquitetura proposta para o app novo, estrutura de módulos, bibliotecas, fases e o que transplantar do código atual |
| 08 | `08-pendencias-servidor.md` | Problemas e lacunas **no servidor** encontrados durante a auditoria e que bloqueiam ou afetam o Android |

Leitura sugerida: 00 → 06 → 07, consultando 01/03 como referência durante a implementação.

---

## Resumo executivo

### 1. O tamanho da defasagem

- O Android parou em **abril/2026**. Desde então, o servidor foi **reescrito duas vezes** (Delphi/Horse → Node em 13/09 → Bun + Elysia em 26/09) e recebeu 22 commits. O web recebeu 25 commits com dezenas de funcionalidades novas.
- Os **paths** REST foram preservados na reescrita, o que dá a falsa impressão de compatibilidade. Mas **formatos, verbos, fluxos e a infraestrutura mudaram** (veja o doc 02).
- O web tem **136 funcionalidades** catalogadas (132 + 4 do commit da noite). O Android tem algo em torno de **25 funcionando ou parciais**, e várias delas estão quebradas contra o servidor atual (doc 05).

### 2. O que está quebrado hoje contra o servidor atual (confirmado no código)

| # | Quebra | Efeito para o usuário |
|---|---|---|
| Q1 | Chamadas usam só STUN; o MediaMTX agora roda em Docker e só é alcançável por **TURN** com credenciais de `GET /api/ice` | **Chamadas sem áudio/vídeo** |
| Q2 | Upload: o app manda os bytes em `PUT /anexo`; o servidor espera JSON → URL assinada do MinIO → `POST /anexo/confirmar` | **Não envia imagem nem áudio** (400, ou 413 acima de 1 MiB) |
| Q3 | Download: `GET /anexo` devolve `{url}` (JSON), e o app trata a resposta como o arquivo | **Não exibe imagens, não toca áudios, não baixa arquivos** |
| Q4 | `GET /mensagem/visualizar` não existe; o servidor só tem `POST` com corpo | Mensagens nunca ficam "visualizadas" (404) |
| Q5 | `GET /conversa/dados` não existe | Ligar a partir do chat falha |
| Q6 | Mensagem "excluída" agora é **ocultada** (`excluida_em`) e continua vindo com o conteúdo original | O app **mostra o texto de mensagens ocultadas** |
| Q7 | Push FCM agora é só `data {titulo, mensagem, conversa}`; o app descarta quem não tem `data.tipo` (e o FCM nem está configurado) | **Nenhum push** |
| Q8 | `PATCH /dispositivo` exige `id`; o app não manda | O token FCM nunca é gravado |
| Q9 | `GET /chamadas/pendentes` não traz `usuarios`; o app faz NPE | Toque perdido não é recuperado |
| Q10 | Modelo do histórico (`GET /chamadas`) não bate com a resposta | Histórico de chamadas vazio ou errado |
| Q11 | A porta de DEV mudou (4430 → 443) e o `build.gradle.kts` ainda aponta 4430 | O app não conecta "de fábrica" |
| Q12 | VP8 não é gravado pelo MediaMTX; o web força H264/VP9 | Chamadas do Android **não são gravadas** (auditoria) |

### 3. O que está quebrado no próprio app (independente do servidor)

Os 7 críticos do doc 04:
- **#1/#2:** dois "donos" disputam os callbacks do socket. Eventos de chamada se perdem ao minimizar, e a chamada seguinte **não toca**.
- **#3:** "Atender" pela notificação não funciona no Android 12+.
- **#4:** senha e JWT vão para o logcat, inclusive em release.
- **#5:** FCM inoperante (sem plugin, sem `google-services.json`, sem registro de token).
- **#6:** senha salva em texto puro com backup liberado.
- **#7:** o full-screen intent cala o toque da chamada.

Além disso:
- `targetSdk 34` (o Play exige 35+).
- Zero ViewModel, DI, Room ou testes.
- ~11% de código morto.
- `ChatActivity` com 1.237 linhas.
- `gradle-wrapper.jar` fora do Git: um clone limpo não builda.

### 4. Decisão recomendada: **recomeçar o app (nova base), transplantando peças**

**Recomendação firme: começar um projeto novo** (novo módulo/app no mesmo repositório ou repositório novo), em vez de corrigir o atual. Motivos:

1. **As quebras de contrato atingem o núcleo inteiro:** anexos (upload e download), mensagens (ocultar, referência, status), chamadas (TURN/ICE, codec, eventos), push e sessão. Corrigir tudo no app atual equivale a reescrever quase todas as classes com estado.
2. **Os defeitos internos críticos estão no núcleo** (socket com callback único, dois serviços de chamada, sessão com senha). Não estão nas bordas.
3. **Faltam ~105 funcionalidades** do web. Construí-las sobre Activities de 1.200 linhas sem ViewModel, sem testes e com estado global multiplica o custo e o risco.
4. **O `targetSdk 35/36` obriga a refazer os layouts XML** (edge-to-edge) e a estratégia de serviços em primeiro plano. Fazer isso em XML para depois migrar para Compose é trabalho dobrado.
5. **O código aproveitável é pequeno e bem delimitado:** WhipWhepClient, parte do WebRTCManager, AudioRecorder, Ringtone, padrões de notificação (MessagingStyle/CallStyle), telas Compose de chamada (visual) e a lógica do histórico. Isso cabe num transplante.

**O que NÃO fazer:**
- Não tentar "atualizar o app atual endpoint por endpoint".
- Não publicar o app atual no Play.

**Enquanto a nova base não substitui o app atual,** a fila (doc 06, Fase 0) traz um **pacote mínimo de higiene e segurança** para o app atual. Ele é opcional e só vale se alguém depende do APK atual no dia a dia.

### 5. Arquitetura-alvo (detalhe no doc 07)

- **App:** Kotlin, Single-Activity, Jetpack Compose + Material 3, Navigation Compose, ViewModel + StateFlow (UDF), Hilt, Room (cache e offline), DataStore + Keystore (token), OkHttp único (REST, WS, WHIP/WHEP, imagens via Coil), kotlinx.serialization, WorkManager (envio e upload), Media3 (áudio e vídeo), Lottie (figurinhas), Firebase Messaging, `androidx.core:core-telecom` (chamadas), `io.github.webrtc-sdk:android`.
- **Toolchain:** `targetSdk/compileSdk 36`, `minSdk 28` (ou 29), JDK 17, R8 ligado, CI com build, lint e testes.

### 6. Dependências do servidor (detalhe no doc 08)

São 14 itens (S1–S14). Os principais, que precisam ser decididos ou corrigidos no servidor para o Android ficar bom:

- **S1 — Push de chamada.** Hoje não existe push para chamada; com o app fechado, o aparelho não toca. Essencial para Android.
- **S2 — Push de mensagem.** Falta `priority: high` para Android, e o push não vai quando há qualquer WebSocket aberto, inclusive o de outro aparelho.
- **S3 — Eventos com contexto.** Os eventos WS 2 (mensagem nova) e o push não trazem `mensagem_id` nem dados suficientes para notificação rica.
- **S4 — Segurança.** `GET /anexo` e MediaMTX sem checagem de participação.
- **S5 — Figurinhas.** A pasta não é servida pelo servidor (o app deve embutir).
- **S6 — Exclusão de conversa.** `DELETE /conversa` quebra (FK).

---

## Glossário rápido

- **WHIP/WHEP:** protocolos HTTP para publicar e assistir mídia WebRTC no MediaMTX. Os caminhos são `/webrtc/call-<chamada>-u-<usuario>/whip|whep`.
- **"Ocultar":** o antigo "excluir mensagem". A mensagem fica com `excluida_em`, aparece como "Mensagem oculta" e o conteúdo continua disponível para revelar.
- **Atividades:** reações, respostas, menções e chamadas perdidas dirigidas ao usuário, com contador de novas (WS 61).
- **Sinal da chamada (WS 57):** canal de mensagens efêmeras entre os participantes. Leva tela compartilhada, ponteiro remoto e criação do chat da chamada.
- **FC-xxx:** item da fila de correções (doc 06). **#n:** problema da auditoria Android (doc 04). **AUT-01 etc.:** funcionalidade do web (doc 03).
