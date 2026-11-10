# 2026-11-10 · 01 · Mensagem presa na fila no Wi-Fi do trabalho (rede "sem internet")

- **Fluxo:** Mensagens (etapa 3); também Qualidade, segurança e publicação (a `v2.0.0-teste.2`)
- **Tipo:** correção
- **Itens:** `TODO.md` 3.7 (linha nova, depois do envio pelo `EnvioWorker`); ENV-01, FC-307
- **Branch:** `reescrita`
- **Commits:** `4f091a2`
- **Release:** [`v2.0.0-teste.2`](https://github.com/conversa-projeto/conversa-android-kotlin/releases/tag/v2.0.0-teste.2), do `4f091a2`

## Contexto
A pessoa mandou o registro do app do celular, no servidor do trabalho (`https://10.0.5.78`, `v2.0.0-teste.1`), perguntando por que estava travando.

O que o registro mostra:
- **13:30:11:** "Na fila: mensagem local -2" e "Envio agendado no WorkManager (espera haver rede)";
- **logo depois:** "WorkManager: enqueued, execuções 0, próxima em 0 s, blocked, execuções 0". São dois trabalhos: um de antes, pronto e esperando a rede, e o da mensagem nova, bloqueado atrás dele (`APPEND_OR_REPLACE`);
- **nos 80 s seguintes:** nenhum "EnvioWorker: execução" e nenhum envio para o servidor. Mesmo assim, as outras chamadas ao `10.0.5.78` respondiam em 15–120 ms, e o tempo real recebia tudo.

**A causa:** o envio só rodava pelo WorkManager com `NetworkType.CONNECTED`.
- Para o Android, essa exigência é **rede com internet confirmada** (`NET_CAPABILITY_VALIDATED`). No `JobInfo`, todo tipo de rede pede a internet validada. O próprio WorkManager 2.12 converte o `CONNECTED` no `setRequiredNetworkType` do JobScheduler.
- Num Wi-Fi interno sem saída para a internet, ou com bloqueio ao teste de conexão do Google, o Android marca a rede como "conectada, sem internet", e o trabalho nunca roda.
- O resto do app usa a rede direto e não liga para essa marca.

No emulador e em casa a rede tem internet confirmada, por isso o problema não tinha aparecido.

## O que foi feito
- **`core/data/.../mensagens/EnvioMensagens.kt` (`AgendadorEnvioWorkManager`):**
  - o pedido de rede agora é próprio (`setRequiredNetworkRequest`, `redeConectada()`): `NET_CAPABILITY_INTERNET`, sem o `NET_CAPABILITY_VALIDATED`, e aceita VPN;
  - o trabalho único mudou de nome (`envio_mensagens` → `envio_mensagens_rede`). Na primeira vez que o app agenda, ele cancela o trabalho de nome antigo. No celular que já tem a `teste.1`, o trabalho preso tem a regra antiga gravada, e as mensagens novas ficariam atrás dele. Cancelar é seguro: o worker não carrega a mensagem, só esvazia a fila do Room;
  - o `cancelar()` (logout) cancela os dois nomes.
- **`EnvioMensagens.enviarAoConectar()`:** manda a fila na hora, sem passar pelo WorkManager.
  - Se sobrar alguma coisa (servidor com erro ou rede caiu no meio), agenda o WorkManager.
  - A `trava` impede que o worker e esta chamada mandem a mesma mensagem.
- **`EnvioMensagens.quantidadeNaFila`** e o `EnvioPendenteDao.observarQuantidade()` (um `Flow`, sem migração).
- **`core/data/.../sessao/CicloSessao.kt` (`IniciadorSessao`):** depois da carga da sessão, com o tempo real **conectado** e a fila não vazia, chama o `enviarAoConectar()`.
  - Dispara a cada reconexão e a cada mensagem nova.
  - O tempo real conectado é o sinal de que o servidor responde, seja qual for a opinião do Android sobre a internet.
  - O WorkManager continua como reserva: app fechado, ou tempo real fora.
- **Testes:**
  - `AgendadorEnvioTest` (novo): o pedido tem `INTERNET` e não tem `VALIDATED` nem `NOT_VPN`;
  - `MensagensTest`: a fila vazia não chama a API; sem rede, a mensagem fica e o WorkManager é agendado; com o servidor de volta, ela sai;
  - `IniciadorSessaoTest`: conectado com a fila vazia não dispara; uma mensagem nova dispara; uma reconexão dispara de novo; sem tempo real não dispara.

## Como foi verificado
- `:core:data:testDebugUnitTest`, `:core:database:testDebugUnitTest`, `:app:assembleDebug` (o grafo do Hilt) e `ktlintCheck`.
- **A publicação:** `publicar.ps1` → `v2.0.0-teste.2`, Pre-release, 657 testes verdes; os dois APKs (64 e 32 bits) chegaram com o tamanho dos daqui.
- **No emulador, numa rede sem internet confirmada:**
  - **A rede:** o emulador foi ligado a frio (`-no-snapshot`) com `-dns-server 127.0.0.2`, um DNS que não responde. O `dumpsys connectivity` mostrou o Wi-Fi e os dados móveis sem o `IS_VALIDATED`, como o Wi-Fi do trabalho. O `adb reverse` para o proxy de dev funciona igual.
  - **O problema reproduzido**, com o app de 2026-11-03 (a regra antiga): a mensagem ficou em "WorkManager: enqueued, execuções 0" por 45 s, sem execução.
  - **A atualização por cima**, com a mensagem ainda na fila. Foi um `uninstall -k` (mantém os dados) e o `install` do APK só x86_64, porque o `install -r` falta espaço. Ao abrir:
    - "Tempo real conectado com 1 mensagem(ns) na fila: enviando já";
    - "Mensagem local -1 enviada: é a 1254 no servidor (37 ms)", presa desde 18:00:52;
    - o `EnvioWorker` também rodou nessa rede, o que antes nunca acontecia, e achou a fila vazia.
  - **Mensagem nova com o tempo real conectado:** enviada em 40 ms (1255); o worker veio depois e não duplicou.
  - **Espaço no emulador:**
    - saiu o APK de teste instrumentado (`com.conversa.conversa.dev.test`);
    - saiu de novo o `video-teste-200mb.mp4` da pasta Download, copiado para o scratchpad do PC. Ele tinha voltado porque o boot a frio carrega a imagem de dados sem o que só ficou no snapshot (histórico `2026-10-27-03`).

## Decisões
- **As duas vias, e não só a troca do pedido de rede:**
  - o tempo real conectado prova que o servidor responde, e não depende de como cada versão do Android trata rede sem internet;
  - o WorkManager cobre o app fechado.
- **O nome novo do trabalho, e não `REPLACE`:** o `REPLACE` cancelaria um envio em andamento no meio, e o `APPEND_OR_REPLACE` continua valendo para as mensagens novas.
- **Sem rede de verdade, nada muda:** a falha `SemConexao` não conta tentativa, como antes.

## Pendências / próximos passos
- A pessoa confirmar no celular, no Wi-Fi do trabalho, com a `v2.0.0-teste.2`. Feito pelo registro do celular (histórico `2026-11-10-02`).
- O guia do emulador (`emulador.md`) ganhou o jeito de simular a rede sem internet confirmada.
