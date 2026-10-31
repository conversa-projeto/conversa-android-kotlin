# 2026-10-31 · 04 · Registro do app: 7 dias de diagnóstico guardados no aparelho

- **Fluxo:** Qualidade, segurança e publicação. Toca todos os outros: rede, sessão, envio, sincronização, notificações, chamadas e Configurações.
- **Tipo:** código + decisão
- **Itens:** `TODO.md` "Qualidade" (Timber, Crashlytics) e linhas novas; problema #4 do legado (log com segredos); módulo novo `:core:registro`
- **Branch:** `reescrita`
- **Commits:** `edadc75`

## Contexto
De manhã, no servidor do trabalho, a pessoa teve problemas no celular. À tarde, o logcat só guardava os últimos ~20 minutos, e a causa não pôde ser vista (histórico `2026-10-31-02`, pendências). Ela pediu um registro interno do app, guardado por até 7 dias, com "todo tipo de informação importante" ("não seja mesquinho").

Antes, o app quase não registrava nada: eram 48 chamadas soltas do Timber, quase todas só com o nome da classe do erro, e só no debug (no release, nada).

## O que foi feito
### Módulo `:core:registro`
- **`ArquivosDoRegistro`** (JVM pura, com testes):
  - um arquivo por dia em `files/registro/AAAA-MM-DD.log`;
  - um dia que passa de 5 MB continua em `AAAA-MM-DD.2.log`;
  - guarda 7 dias contando hoje e um teto de 60 MB no total (sai o mais antigo, nunca o aberto);
  - lê as últimas linhas, compacta tudo num zip e apaga tudo.
- **`Registro`**:
  - fila de 20 mil linhas e uma thread só gravando (quem chama o Timber não espera o disco); com a fila cheia, avisa quantas linhas descartou;
  - a árvore do Timber grava do DEBUG para cima. Ela herda da `DebugTree` só para descobrir a tag pela classe quando não há uma explícita;
  - a falha fatal (exceção não tratada) grava a pilha inteira na hora, da thread que caiu, antes de passar ao tratador do Android;
  - é um só por processo (`Registro.de`) e liga antes do Hilt, no `Application.onCreate`, antes do `super`. Assim pega o que os singletons registram ao nascer (antes, "Sessão guardada" se perdia). O Hilt entrega o mesmo (`RegistroModulo`).
- **`linhaDoRegistro`:** `2026-10-31 16:41:04.199 -04:00 I/Http [OkHttp] …`. O fuso fica em cada linha, e a thread do OkHttp perde o endereço no nome. `agruparEntradas` junta a pilha de um erro à entrada dela, para a tela.
- **`Sigilo`:** a segunda barreira, aplicada a toda linha.
  - Os segredos em uso viram `***` onde aparecerem: o token da sessão (posto pelo `SessaoRepositorio`) e as credenciais do TURN (pela mídia).
  - O mesmo vale para `Bearer …` e para os valores de `token=`, `"senha":`, `X-Amz-Signature=` etc.
  - "Token não informado" passa inteiro.
- **`resumoDoErro()`:** `Classe: mensagem ← Causa: mensagem`, até 4 níveis. O `ErroApi` se descreve com o status (`Servidor 403: Sem permissão`, `SemConexao ← SocketTimeoutException: timeout`), pela interface `DescricaoParaRegistro`.

### O que vai para o registro
- **HTTP (`RegistroHttp`, um `EventListener` do OkHttp):** substitui o `HttpLoggingInterceptor` (a dependência `okhttp-logging` saiu). É uma linha por requisição: REST, anexos, imagens do Coil, WHIP/WHEP.
  - Traz método, endereço (`resumoDaUrl`), status, tempo e bytes enviados e recebidos.
  - Numa conexão nova ou numa falha, traz também DNS, TCP, TLS (versão), 1º byte, protocolo, o endereço IP:porta e cada tentativa de conexão que falhou.
  - No endereço, os parâmetros só levam o valor quando é número ou sim/não: o texto pesquisado e a assinatura das URLs de anexo viram "…".
  - Dos mapas, só o nome do serviço.
  - O endereço vem da requisição de rede: o da chamada ainda é a base fictícia.
- **Tempo real (`RealtimeClient`):**
  - o que pediu para ligar ou desligar, a abertura (endereço, conexão, tentativa) e o "conectado em N ms (HTTP 101)";
  - cada evento recebido, resumido por `resumoParaRegistro()` (tipo e números). Nunca entram o título nem o texto da mensagem nova, o emoji da reação, os dados do sinal (só a ação) nem o JSON de um tipo desconhecido;
  - o que o app manda (presença, sinal), inclusive o "não enviado (socket fechado)";
  - fechamento e falha, com código, motivo e quanto tempo ficou conectado, e a espera até a próxima tentativa.
- **Sessão:** a sessão guardada (usuário e dispositivo, nunca o token). O login registra o usuário **como foi digitado** (teria mostrado o "teste. Android. b" do SwiftKey) e, na recusa, o motivo. Também entram cadastro, sair, o 401 que encerra a sessão, a carga da sessão (tentativas e tempo), o registro do dispositivo e a limpeza.
- **Sincronização:** a completa (tempo), quantas conversas, as conversas com mensagens novas e a atualização periódica com o socket fora. As falhas vêm com o motivo.
- **Envio:**
  - cada mensagem que entra na fila (id local, conversa, anexos com tipo e tamanho, resposta, encaminhamento, programada…; nunca o texto);
  - cada tentativa (número, desde quando está na fila), cada anexo subindo (tempo), o id real no servidor, a falha e a decisão (tentar depois, falhou de vez, recusado pelo servidor);
  - reenviar e apagar pela pessoa;
  - o `EnvioWorker`: execução, resultado e parada pelo sistema (com o motivo, no Android 12+). O estado do trabalho no WorkManager também entra: na fila, rodando, a próxima em N s.
- **Notificações:**
  - a decisão de cada mensagem nova (notificar, só som ou nada, e por quê: arquivada, app na frente, conversa na tela);
  - a notificação mostrada (linhas, não lidas), a que não pôde ser mostrada (sem permissão) e a tirada.
- **Chamadas:**
  - os comandos (ligar, atender, recusar, desligar);
  - cada mudança do estado (`EstadoChamada.resumoParaRegistro()`): fase, chamada, tipo, quem ligou, mídia, microfone, câmera, participantes com o status, tela compartilhada, etapa do vídeo;
  - **mídia:** o `/ice` (as URLs, nunca as credenciais), a publicação e cada assinatura (WHIP/WHEP com tempos e tentativas);
  - por conexão: a coleta ICE, os candidatos por tipo ("1 relay udp", sem endereços), o estado do ICE e da conexão com o tempo.
- **Conexão em segundo plano:** ligada ou desligada, o serviço iniciando, recriado pelo sistema, parando, encerrado ou recusado, e o "celular ligado" ou "app atualizado".
- **App e aparelho (`DiarioDoApp`, `RetratoDoAparelho`, `HistoricoDoProcesso`, no `:app`):**
  - **abertura:** versão e build, aparelho, Android e patch de segurança, ABI, idioma, fuso, memória, espaço livre, banco e cache;
  - **permissões e notificações:** as permissões, as notificações e a importância de cada canal, e a tela cheia nas chamadas;
  - **energia:** bateria, economia de energia, Doze, isenção da otimização de bateria, restrição em segundo plano, balde de espera e economia de dados. Ao voltar para a frente, só o que mudou;
  - **por que o processo anterior terminou** (Android 11+): falta de memória, congelado, parado pela pessoa, atualizado, ANR (com as pilhas, até 400 linhas) ou crash. Só os novos desde a última abertura;
  - **por que este começou** (Android 15+): ícone, notificação, trabalho agendado, celular ligado…, e se tinha sido forçado a parar;
  - **ciclo:** primeiro e segundo plano, cada Activity (criada com o quê: ação, endereço `conversa://chat/8` e o **nome** dos extras, nunca os valores), memória pedida pelo sistema e mudanças de configuração;
  - **telas:** cada tela navegada (`RotaChat (conversaId=3…)`, só os argumentos que são números);
  - **rede:** a padrão, com tipo, **internet confirmada** e se é medida (num Wi-Fi da empresa sem internet, o Android prefere os dados móveis), interface, DNS privado, proxy, queda e bloqueio para o app;
  - **tela e energia:** tela acesa ou apagada, Doze e economia de energia;
  - **estado:** servidor, tempo real (deveria estar ligado? está?) e a faixa "sem tempo real";
  - **travamentos:** com o app na frente, a thread principal parada por mais de 2 s, com a pilha dela, e quanto durou.
- **Erros que já existiam:** os `Timber` que só davam o nome da classe passaram a dar o `resumoDoErro()` (rede, dados, notificações, chamada, mídia).

### Tela "Registro do app"
- **Onde fica:** em Configurações (ícone de inseto). Também na barra da tela **Servidor**, para abrir antes de entrar (o login leva a ela).
- **Conteúdo:**
  - o que é e o que não guarda, desde quando e o tamanho;
  - o filtro "Só avisos e erros";
  - as últimas 1.500 entradas, a mais nova em cima, com a faixa da cor do nível (cores do `cores.md`), em fonte fixa e selecionáveis.
- **Botões:**
  - Atualizar;
  - **Compartilhar:** um zip em `cache/registro/`, pelo FileProvider do app (`caminhos_arquivos.xml`), com o assunto "Registro do Conversa (data)";
  - **Apagar,** com confirmação (o registro novo começa dizendo que a pessoa apagou).
- Os textos ficam em `strings.xml` (`registro_*`, `servidor_registro`). O web não tem esta tela.

### Build
- **`settings.gradle.kts`:** o módulo `:core:registro`. O `:core:network` o expõe como `api` (o `ErroApi` implementa a interface dele), e o `:app` depende dele.
- **`proguard-rules.pro`:** `-keepattributes SourceFile, LineNumberTable` e `-keepnames class com.conversa.app.** { *; }`. No release, as pilhas e as tags descobertas pela classe ficam legíveis sem o mapping (o R8 ainda tira o que não é usado).

## Como foi verificado
- **Testes:**
  - `ArquivosDoRegistroTest` (8): dia, virada do dia, partes, 7 dias, teto total, últimas linhas, zip, apagar, nomes;
  - `LinhaDoRegistroTest` (5): formato, agrupar a pilha, sigilo, texto comum intacto, resumo do erro;
  - `RegistroHttpTest` (6): endereço, URL assinada, mapas, tamanhos, eventos sem conteúdo, `ErroApi`;
  - `ResumoDaChamadaTest` (2);
  - o tipo do candidato no `EsperaColetaTest`;
  - o `Sigilo` no `RegexNoAndroidTest` (regex do ICU): no commit, só compilado (o emulador estava desligado); no mesmo dia, rodado no emulador (Android 16, API 36), `OK (1 test)` (histórico `2026-10-31-06`).
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `lintDebug` (só os avisos de antes: versões do AGP/Gradle, `UseKtx`, `CustomX509TrustManager`), `ktlintCheck`, `compileDebugAndroidTestKotlin` e `textos-repetidos`.
- **No celular** (Galaxy A25, Android 16, depuração por Wi-Fi, conta B no servidor de dev):
  - **o arquivo:** `adb shell run-as com.conversa.conversa.dev cat files/registro/2026-10-31.log` mostrou a abertura completa;
    - o histórico do processo trouxe as paradas forçadas da manhã (07:45–07:51) e as atualizações;
    - a rede apareceu como "Wi-Fi; internet confirmada: sim", e as requisições com DNS/TCP/1º byte;
    - também vieram o WebSocket (conectado em 145 ms), a sessão, a sincronização e as telas;
  - **sem segredos:** nenhuma ocorrência de "token", "senha" nem "Bearer" no arquivo;
  - **a tela:** aberta pelas Configurações. O Compartilhar abriu o menu do Android com `conversa-registro-2026-10-31-1645.zip` (fechado sem enviar).
  - **o filtro e o Apagar,** no mesmo dia, a pedido:
    - **avisos de propósito:** o registro ainda não tinha avisos. O proxy de dev parou por ~20 s, e o app registrou as falhas com a causa (`W/TempoReal Falhou depois de 694 s conectado: EOFException`, `W/Http GET …/api/mensagens/novas?desde=… ✗ IOException…`, `W/Sync Falha em mensagens/novas: SemConexao ← …`, a faixa "fora do ar há mais de 5 s"). Com o proxy de volta, reconectou em 57 ms;
    - **o filtro:** "Só avisos e erros" ligado deixou só as entradas W, com a faixa coral; desligado, voltaram as D e I;
    - **apagar:** a confirmação "Apagar todo o registro deste aparelho? Não dá para desfazer." apareceu. "Cancelar" não apagou (o arquivo seguiu com 55 kB). "Apagar" deixou o arquivo só com `I/Registro … Registro apagado pela pessoa, na tela do registro`, e a tela com "0 kB" e essa entrada. O registro seguiu gravando depois (uma cópia do anterior ficou no PC).

## Decisões
- **O registro vale também no release** e substitui o item "Timber: nada de log em release". O problema #4 do legado era log com senha, token e mensagens **no logcat**, que outros apps e o `adb` leem. Agora:
  - o logcat continua só no debug;
  - o arquivo fica na pasta privada do app, sem nada disso, passa pelo `Sigilo` e só sai do aparelho pelo Compartilhar da pessoa.
  Sem ele, o próximo problema num celular de apresentação seria tão invisível quanto o de hoje.
- **Fica ao sair da conta** (a limpeza da sessão não apaga). É diagnóstico do aparelho, sem conteúdo; apagar junto teria perdido o que se queria ver hoje. Quem quiser apaga na tela.
- **Ids sim, textos não:** ids de conversa, mensagem, usuário e chamada entram (são o que liga uma linha à outra). Nomes de pessoas e grupos, textos e conteúdos não entram. A exceção é o **usuário digitado no login**, o que a pessoa escreveu para entrar, que explica o "Usuário não encontrado".
- **Uma linha por requisição, e não um log por etapa:** o resumo (com as etapas quando a conexão é nova ou falhou) diz o mesmo e cabe nos 7 dias com folga. Um dia de uso normal fica bem abaixo dos 5 MB.

## Pendências
- Usar no servidor do trabalho e, no próximo problema, mandar o zip (Configurações › Registro do app › Compartilhar).
