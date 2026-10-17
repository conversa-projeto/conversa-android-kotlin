# 2026-10-17 · 20 · Apresentação: receber com o app fechado (sem FCM), minimizar a chamada e servidor interno

- **Fluxo:** Notificações e push (etapa 5) — também Chamadas (etapa 6) e Fundação (tela Servidor, TLS)
- **Tipo:** código | decisão
- **Itens:** ADR 0002 (opção A); TODO 5.3 (bateria), 5.7 (teste com o app fechado), 5.9 (A), 6.12 (minimizar), 1.x (TLS do debug); NOT-01, CHA-03, CHA-13, AND-01, AND-06
- **Commits:** `HASH_APRESENTACAO` (branch `apresentacao`, a partir de `dd799d0` da `reescrita`)

## Contexto
A pessoa vai apresentar o app em 2026-10-17, num celular real, contra um servidor que já roda o web na rede local de lá (endereço e certificado desconhecidos). Ela listou o que precisa funcionar:
1. Configurar tudo na primeira inicialização: servidor, usuário etc.
2. Usar o toque padrão do aparelho nas notificações de mensagem e de chamada.
3. Receber notificação sem FCM.
4. Receber notificação com o app fechado.
5. Atender uma chamada com o app fechado e a tela bloqueada.
6. Continuar na chamada depois de tirar o app dos recentes.
7. Ao reabrir o app, a chamada fica em segundo plano e dá para navegar entre os chats.
8. Sair da tela da chamada sem encerrar e ir a outro chat.

Ela respondeu às perguntas do ADR 0002: a conexão própria fica, com a notificação fixa e o pedido de bateria. Para o servidor, pediu que o HTTPS do debug fosse resolvido sem custo, ou seja, sem domínio nem certificado comprados.

Outra sessão estava mexendo na `reescrita` (Localização). Por isso o trabalho foi feito num worktree, na branch `apresentacao`, e testado num AVD separado (`Apresentacao`, porta 5556), sem tocar no emulador da outra sessão.

## O que foi feito
**Receber com o app fechado sem FCM (ADR 0002, opção A).** Itens 3, 4 e 5.
- `app/.../conexao/ConexaoEmSegundoPlano.kt`:
  - `ConexaoEmSegundoPlano`: com sessão e a chave ligada, liga `ConexaoTempoReal.manterEmSegundoPlano`. Inicia o serviço ao ativar e cada vez que o app volta para a frente, porque o Android não deixa iniciar em segundo plano.
  - `ServicoConexao`: serviço em primeiro plano `specialUse` com a notificação fixa "Conectado ao Conversa / Recebendo mensagens e chamadas com o app fechado". `START_STICKY`; para sozinho ao sair da conta ou desligar a chave.
  - `InicioDoAparelhoReceiver`: `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED`.
- `core/data/.../ConexaoTempoReal.kt`: o WebSocket fica ligado com o app na frente, numa chamada **ou** com `manterEmSegundoPlano`.
- `core/data/.../CanaisNotificacao.kt`: canal `conexao_v1`, mínimo e sem som.
- `core/datastore/.../PreferenciasStore.kt` e `PreferenciasRepositorio`: `conexaoSegundoPlano`, ligada por padrão, e `pediuBateria`.
- `app/.../principal/PrincipalTela.kt`: pedido único "Receber com o app fechado", que abre o `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Vem depois dos pedidos de notificações e de tela cheia.
- `feature/config/.../NotificacoesTela.kt`:
  - a chave "Receber com o app fechado";
  - a linha "Uso de bateria", com o estado (sem restrição ou restrito) e o atalho;
  - o canal novo na lista.
- Manifesto: `FOREGROUND_SERVICE_SPECIAL_USE` (com a propriedade de subtipo), `RECEIVE_BOOT_COMPLETED` e `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

**Chamada.** Item 8.
- `TelaChamada.kt`: botão "Voltar ao app sem encerrar" (ícone `CloseFullscreen`) no cabeçalho.
- `ChamadaActivity.minimizar()`: o botão e o Voltar trazem o app para a frente pelo intent do lançador. Antes, com o app fechado, caía na tela inicial do Android. A `MainActivity` não muda de tela com esse intent.

**Servidor interno no debug, sem custo.** Item 1.
- `core/network/.../tls/ConfiancaServidor.kt`:
  - `X509ExtendedTrustManager` que aceita o que o Android aceita (o `network_security_config`) **ou** o certificado exato aceito pela pessoa (SHA-256 do certificado);
  - `HostnameVerifier` que aceita esse certificado mesmo sem o IP no SAN;
  - `impressaoSha256`;
  - só com `BuildConfig.DEBUG`.
- `RedeModulo.okHttp` usa a `ConfiancaServidor`. O `HttpsURLConnection` padrão também, para o vídeo do Media3.
- `ServidorRepositorio`:
  - guarda a impressão junto do endereço e é o `CertificadoConfiadoProvider`;
  - `testar(config, certificado)` devolve `CertificadoInvalido(impressao)` com o certificado recebido.
- `ServidorViewModel` e `ServidorTela`:
  - diálogo "Certificado não reconhecido", com a impressão (selecionável) e "Confiar e salvar";
  - endereço digitado sem esquema: se o HTTPS não responde, ou se responde sem TLS, tenta `http://` e mostra o endereço usado.
- `app/src/debug/res/xml/network_security_config.xml`: HTTP sem TLS em qualquer endereço, só no debug. É para servidor interno em modo produção sem a borda TLS (`bin/producao.bat` escuta só na porta 80). O release continua só HTTPS e só com as CAs do sistema.

**Toque padrão.** Item 2: nada mudou, já estava certo.
- Mensagens: o canal `mensagens_v1` usa o som padrão de notificação do aparelho, e o "só som" com o app na frente também.
- Chamadas: o canal é silencioso, e o `TomDeChamada` toca o `DEFAULT_RINGTONE_URI` em repetição, respeitando o modo silencioso e o de vibração.

**Itens 6 e 7:** nada mudou, já funcionavam. O serviço da chamada segura o processo, e a faixa "Toque para voltar à chamada" envolve todas as telas.

## Como foi verificado
- `assembleDebug` e os testes unitários de `core:network` (65), `core:data` (119), `feature:auth` (22), `feature:config` (10) e `feature:chamada` (84): todos passando. `ktlintCheck` limpo.
- Testes novos:
  - `ConfiancaServidorTest` (4): impressão no formato do `openssl`, recusa sem certificado aceito, aceita só o exato, o que o Android aceita continua valendo;
  - `ServidorViewModelTest` (+5): confiar e salvar, não confiar, HTTP sem resposta no HTTPS, porta só HTTP, com `https://` digitado não tenta HTTP.
- No emulador separado (AVD `Apresentacao`, Android 16), contra o servidor de dev, com o B comandado pela API (script no scratchpad):
  1. **Primeira inicialização com `10.0.2.2`** (fora do certificado do mkcert, sem CA instalada):
     - "Certificado não reconhecido" com `E5:06:CA:…:0E:F8`, igual ao `openssl x509 -fingerprint -sha256` do `conversa/bin/cert/cert.pem`;
     - "Confiar e salvar" → login como C → pedidos de notificações → bateria ("Allow") → lista.
  2. **App fechado** (removido dos recentes, processo e serviço vivos), tela apagada e `deviceidle force-idle`: a mensagem do B virou notificação no canal `mensagens_v1`, com o som `content://settings/system/notification_sound`.
  3. **Chamada do B com a tela apagada:**
     - a tela acendeu, a `ChamadaActivity` apareceu por cima e o toque tocou (`USAGE_NOTIFICATION_RINGTONE`);
     - atendi arrastando o botão e o microfone foi pedido;
     - o servidor ficou com os dois em "entrou"; o serviço da chamada ficou com `phoneCall|microphone`.
  4. **Minimizar → lista de conversas** com a faixa; o chat do B abriu durante a chamada.
  5. **App tirado dos recentes durante a chamada:** "No recent items", a chamada seguiu (status 3, cronômetro na barra).
  6. **Fim da chamada pelo B:** só o serviço de conexão continuou.
  7. **Dados apagados, `10.0.2.2:8081`** (o proxy HTTP de dev): o app passou sozinho para `http://` → login → mensagem do B apareceu em tempo real.
  8. **Chave "Receber com o app fechado":** desligada, o serviço parou e a linha da bateria sumiu; religada, o serviço voltou.
- **Não testado:**
  - num aparelho real, nem com o servidor de lá;
  - mídia de chamada com o outro lado transmitindo (o B da API não publica; a mídia já foi testada na etapa 6);
  - tela bloqueada **com PIN** (o AVD não tem PIN, e pôr um seria mudar a segurança do aparelho). O `showWhenLocked` da `ChamadaActivity` é o mesmo da etapa 6.

## Decisões
- **Opção A sem esperar o resto do ADR.** É o único jeito de a chamada tocar com o app fechado sem FCM. A chave fica ligada por padrão, porque ainda não existe FCM no app. Quando o FCM (D) chegar, ela pode passar a valer só sem FCM.
- **Aceitar certificado próprio** é confiança no primeiro uso, com a impressão digital à vista, e só no debug. A regra "nada de TrustManager que aceita tudo" continua valendo: só aquele certificado passa. O release não muda (FC-106).
- **HTTP sem TLS em qualquer endereço só no debug.** O servidor instalado em modo produção sem a borda escuta só HTTP.
- **Registros do histórico com número 20.** A outra sessão já usa 01–02 neste dia e deve continuar a sequência; assim os nomes não colidem na junção das branches.

## Pendências / próximos passos
- Juntar a branch `apresentacao` na `reescrita` depois que a sessão da Localização terminar. Pode dar conflito pequeno no `RedeModulo.kt` e nos `strings.xml`.
- No aparelho real: conferir o Doze com a tela apagada por muito tempo. Em Samsung, também "Apps em suspensão profunda" e o uso de bateria "Sem restrições".
- `turns:` (TURN com TLS) com certificado próprio não passa pela `ConfiancaServidor`, porque a libwebrtc valida sozinha. Só importa se o servidor de lá usar `CONVERSA_TURN_PORTA`; na mesma rede, a mídia costuma ir direto.
- A miniatura do vídeo (`MediaMetadataRetriever`) não usa a confiança do app; com certificado próprio, a bolha do vídeo pode ficar sem quadro (o vídeo abre).
- ADR 0002: D (FCM pelo servidor), C (sincronização periódica) e a medição de bateria.
