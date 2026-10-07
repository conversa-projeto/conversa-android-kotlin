# 2026-10-07 · 02 · Etapa 1 — fundação do app novo

- **Fluxo:** Fundação (etapa 1)
- **Tipo:** código / build / configuração
- **Itens:** `TODO.md` 1.1–1.13 (FC-100…FC-113)
- **Branch:** `reescrita` (criada a partir de `novo`)
- **Commits:** ver `git log reescrita` (primeiro commit da etapa 1)

## Contexto
Começo da nova base (ADR 0001). O objetivo da etapa é a fundação: projeto, módulos, rede, banco, sessão, tempo real e sincronização. Telas de verdade começam na etapa 2.

## O que foi feito

### Projeto e build
- `app/` antigo → `app-legado/` (fora do build). Novo `:app` com `applicationId com.conversa.conversa` (atualiza por cima do legado) e `namespace com.conversa.app`.
- Gradle 9.8.0 (wrapper regenerado).
- Versões: AGP 9.4.1 com Kotlin embutido, Kotlin 2.4.20, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00, Room 2.8.5, OkHttp 5.5, Retrofit 3, kotlinx.serialization 1.11, Coil 3.6.3. Todas no `gradle/libs.versions.toml`.
- **compileSdk 37** (as bibliotecas atuais exigem; o AGP instalou `platforms/android-37.0` no SDK). targetSdk 36, minSdk 28.
- `gradle.properties` limpo: sem as flags de compatibilidade do AGP 9.
- Convention plugins em `build-logic/convention/` (`Plugins.kt`, `ConfiguracaoComum.kt`): application, library, compose, hilt, feature, jvm.

### Módulos
`:core:model`, `:core:network`, `:core:datastore`, `:core:database`, `:core:data`, `:core:ui`, `:core:testing`, `:feature:auth`. Os demais (`:core:media`, `:core:webrtc`, outros `:feature:*`) foram movidos para o início das etapas onde serão usados.

### Rede (`:core:network`)
- `ServerConfig`: uma URL base → `api/`, `wss://…/ws/` e `webrtc/`.
- `EnderecoInterceptor` (troca de servidor em tempo de execução, sem recriar o Retrofit) e `AutenticacaoInterceptor` (Bearer; 401 de token vira `EventosSessao.sessaoExpirada`; rotas públicas marcadas).
- `ErroApi` + `mensagemAmigavel()` + `chamarApi {}`: `{error}`, HTML do nginx, 429/503, sem conexão, resposta inválida.
- `ConversaApi` com as **67 rotas**; DTOs de todas as áreas; serializers tolerantes de data e id; mapeadores DTO → modelo.
- `RealtimeClient`:
  - socket único com número de geração, para ignorar callbacks de conexões antigas;
  - eventos para vários assinantes;
  - reconexão de 1 s a 30 s, com variação;
  - login recusado não insiste;
  - parser de todos os tipos, de 0 a 62.
- Um único `OkHttpClient` no Hilt, com log `BASIC` só no debug e o `Authorization` redigido.

### Dados
- **Sessão (`:core:datastore`):** `SessaoStore` cifrado com Tink + Keystore, sem senha; `PreferenciasStore` com o servidor e o id do dispositivo.
- **Banco (`:core:database`):** Room com 9 tabelas, DAOs com `Flow` e schema v1 exportado.
- **Camada de dados (`:core:data`):**
  - `ServidorRepositorio`: implementa `ServerConfigProvider` e faz o teste de conexão (401 com `{error}` = é Conversa);
  - `SessaoRepositorio`: implementa `TokenProvider` e encerra a sessão em qualquer 401;
  - `MonitorPrimeiroPlano` e `ConexaoTempoReal`: WebSocket só com sessão e app visível (ou chamada), 10 s de tolerância;
  - `SyncManager`: ressincronização completa ao conectar e WS 2 tratado como gatilho com debounce.

### Interface
- **Design system (`:core:ui`):** cores do FMX (`docs/design/cores.md`), `UiState`, `EventosUnicos`, `Avatar` (grafema), `DialogoConfirmacao` e `DialogoAviso`, `Carregando`, `EstadoVazio`, `EstadoErro`, `AreaDeAvisos` (Snackbar global) e previews.
- **`:feature:auth`:** tela "Servidor" (testar e salvar, avisos amigáveis).
- **`:app`:**
  - `ConversaApplication` (Timber só no debug, Coil com o OkHttp do app) e `MainActivity` com splash e edge-to-edge;
  - navegação tipada: Servidor → Início provisório, que mostra o servidor e o estado do tempo real;
  - segurança: `allowBackup=false`, regras de extração vazias e `network_security_config` (sem texto claro; no debug confia em CAs do usuário, para o mkcert).

### Qualidade
- ktlint em todos os módulos (estilo `android_studio`); lint com problemas de segurança como erro (`app/lint.xml`).
- CI em `.github/workflows/android.yml`.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → **BUILD SUCCESSFUL**.
- **65 testes, 0 falhas**, rodados 3 vezes seguidas:
  - `ServerConfigTest`, `EventoSocketParserTest`, `DesserializacaoTest`, `ConversaJsonTest`;
  - `RedeTest` (MockWebServer), `RealtimeClientTest` (WebSocket real contra o MockWebServer);
  - `SessaoStoreTest`, `BancoTest` (Robolectric);
  - `SyncManagerTest`, `ServidorViewModelTest`.
- Lint: "No issues found". Busca por "aceita qualquer certificado" no código novo: nada.

## Decisões
- **compileSdk 37 / targetSdk 36.** Subir o target é uma decisão à parte, por causa das mudanças de comportamento.
- **CA do mkcert:** em vez de embutir o certificado no app (`@raw`), o build de debug confia nas CAs instaladas pelo usuário. Não há certificado no repositório.
- **detekt adiado:** a versão estável (1.23) não roda com Kotlin 2.4.
- **Estilo ktlint `android_studio`:** sem vírgula final, imports `java`/`javax`/`kotlin` no fim. O `ktlintFormat` aplicou em todo o código.
- **Teste do SessaoStore com DataStore em memória:** o DataStore de arquivo não consegue renomear no Windows durante os testes; no Android isso não acontece.
- **Mapeadores DTO → modelo em `:core:network`:** o `:core:model` não conhece o servidor.

## Pendências (marcadas com ⛔ no TODO)
- **Push para o GitHub e CI verde:** o push foi bloqueado aqui.
- **Testes no aparelho:**
  - conectar ao servidor de dev com mkcert;
  - matar o app e reabrir, conferindo que a sessão continua;
  - derrubar o Wi-Fi e conferir a reconexão;
  - ficar 2 min sem rede e conferir que nada falta nem duplica.
- **Baixar o OpenAPI:** precisa do servidor rodando.
