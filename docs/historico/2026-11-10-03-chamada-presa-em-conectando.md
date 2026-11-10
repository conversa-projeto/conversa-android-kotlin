# 2026-11-10 · 03 · Conexão da chamada presa em "conectando": refeita em 3 s, e mais registro da mídia

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** correção
- **Itens:** `TODO.md` 6.1 (linha nova, depois do "ICE `FAILED` → recriar só o PC"); FC-703, FC-704, CHA-10
- **Commits:** (preencher)

## Contexto
No registro do celular de 2026-11-10 (histórico `2026-11-10-02`; Galaxy A25, `v2.0.0-teste.2`, servidor do trabalho), a pessoa notou um atraso numa das ligações. Foi a chamada 171, às 14:40:
- **O outro atendeu às 14:40:32** e ouviu a pessoa só às 14:40:52, **~20 s depois**:
  - o WHIP do celular respondeu 201 às 14:40:32, e a conexão com o MediaMTX ficou em "conectando" e nunca fechou;
  - o WebRTC só a deu por perdida (`DISCONNECTED`) depois de 17,8 s. O app esperou mais 2 s e publicou de novo, e a nova fechou em 0,4 s;
  - o `DELETE` da sessão antiga voltou 404: o MediaMTX já a tinha descartado.
- **A pessoa ouviu o outro ~5 s depois:** o WHEP voltou 404 seis vezes até a transmissão do outro aparecer no servidor (às 14:40:38). Essa parte é do outro aparelho, e o app já tenta de novo a cada 0,8 s.
- **A outra ligação (170, às 14:37)** fechou os dois lados em menos de 0,5 s depois do atendimento.

A causa de a conexão travar não aparece no registro: ele só tinha os estados da conexão, sem dizer em que passo ela estava. O web faz igual ao app (só refaz quando o WebRTC diz que caiu).

## O que foi feito
- **`core/webrtc/.../EstatisticasConexao.kt` (novo):**
  - `prazoParaConectar(refeitas)`: 3 s, depois 6 s, e 10 s daí em diante. Na rede interna, a conexão fecha em 0,2–0,4 s depois da resposta. Cada refeita seguida ganha mais tempo, para uma rede lenta não ficar refazendo para sempre;
  - `resumoDaConexao(estatisticas)`: em que passo a conexão está, sem os endereços:
    - o transporte: estado do ICE e do DTLS, e os bytes que passaram;
    - os pares de candidatos, o escolhido primeiro: estado, pedidos e respostas de conexão dos dois lados, RTT;
    - os pacotes de áudio;
  - `resumoDaQualidade(estatisticas)`: o RTT e, por mídia:
    - o que foi e veio;
    - a perda, aqui e do lado do MediaMTX (RTCP);
    - o jitter e o atraso do buffer de quem recebe;
    - o áudio ocultado;
    - no vídeo, a resolução, o fps e o motivo do limite;
    - "nenhum pacote de mídia ainda" quando a conexão está de pé e nada chegou;
  - `candidatosDaResposta(sdp)`: os candidatos que o MediaMTX mandou na resposta ("4 host udp").
- **`MidiaWebRtc.kt`:**
  - **o vigia:** depois da resposta do WHIP (`vigiarPublicacao`) ou do WHEP (`vigiarAssinatura`), sem `CONNECTED` no prazo, a conexão é refeita na hora. O contador zera ao conectar, ao sair o participante e ao encerrar;
  - **antes de refazer** (vigia, `FAILED` ou `DISCONNECTED`), o registro guarda o `resumoDaConexao` ("Estava: …");
  - **a cada 15 s**, a qualidade de cada conexão de pé (`registrarQualidade`, na mesma vida do medidor de fala);
  - a resposta de cada WHIP/WHEP registra os candidatos dela;
  - `estatisticas(pc)` junta as leituras do `getStats` (o medidor de fala também usa);
  - **`assinar(trocar = true)`:** com a assinatura atual ainda sem conectar, a anterior conectada (troca sem intervalo, FC-728) continua tocando. O vigia refaz a assinatura nova sem cortar a que toca enquanto o vídeo liga.
- **`FabricaWebRtc.kt` (`Observador`):**
  - os tempos de cada mudança de estado contam também desde a resposta ("em 2312 ms (252 ms desde a resposta)");
  - o ICE pela regra do padrão (`onStandardizedIceConnectionChange`): com ele `CONNECTED` e a conexão em "conectando", o caminho existe e é o DTLS que não fecha;
  - "chegando dados / nada chegando pela rede", o par de candidatos escolhido ("relay udp → host udp") e os erros da coleta ICE (código e texto do TURN).
- **Testes:** `EstatisticasConexaoTest` (9):
  - os prazos;
  - os candidatos da resposta, sem endereço;
  - a conexão presa (ICE de pé, DTLS sem fechar);
  - sem par e sem transporte;
  - mais de 3 pares;
  - a qualidade da assinatura e a da publicação (com vídeo);
  - a conexão de pé sem mídia.
- **Docs:**
  - `docs/desenvolvimento/emulador.md`: como travar a conexão de propósito (pausar o coturn);
  - `docs/testes/chamadas.md`: a linha do cenário.

## Como foi verificado
- `:app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck :app:compileDebugAndroidTestKotlin`: 666 testes verdes.
  - O `RedeTest` (`:core:network`, que esta mudança não toca) falhou uma vez por "Read timed out", com a máquina carregada (emulador, Chrome e Docker); sozinho, os 79 do módulo passaram.
- **No emulador, contra o servidor de desenvolvimento, com o coturn pausado** (`docker pause coturn`; a conexão não fecha, como no celular):
  - **A publicação:** o A ligou para o C com o coturn pausado e o coturn voltou 10 s depois:
    - a oferta saiu sem candidatos e o WHIP respondeu;
    - **3000 ms depois da resposta**: "Publicação não conectou em 3000 ms desde a resposta; … Estava: ICE new, DTLS new, … nenhum par de candidatos …";
    - a nova publicação conectou **252 ms depois da resposta**;
    - 15 s depois: "Qualidade da publicação: RTT 4 ms; áudio: enviou 484 pacotes, perdidos lá 0, jitter lá 9 ms".
  - **A assinatura:** o C atendeu pelo web (Chrome de teste com microfone falso) com o coturn pausado:
    - a assinatura do app ao C não fechou, e **aos 3 s** foi refeita ("Estava: ICE new, DTLS new, … nenhum par");
    - a nova conectou **256 ms depois da resposta**.
  - **A queda:** com o coturn pausado, a publicação já de pé caiu e foi refeita, com "Estava: ICE disconnected, DTLS connected, … pedidos 91, respostas 9 …".
    - O web do C também republicou, e a assinatura do app caiu e foi refeita ("DTLS closed").
    - Depois: "Qualidade da assinatura de 35: RTT 4 ms; áudio: recebeu 389 pacotes, perdidos 2, jitter 21 ms, buffer 80 ms, ocultado 1,2 %".
  - Sem falha do app no logcat (`-b crash`).
- **O microfone:** o emulador pediu a permissão de novo (o `uninstall -k` tira as permissões), e a resposta foi "Only this time", que vale só para aquela vez.

## Decisões
- **3 s e não esperar o WebRTC:**
  - na rede interna e no emulador, a conexão fecha em 0,25–0,4 s depois da resposta;
  - presa, o WebRTC levou ~18 s para dar por perdida;
  - o prazo cresce a cada refeita seguida (6 s, 10 s), para não brigar com uma rede lenta (4G, TURN por TLS).
- **O vigia conta da resposta do servidor, não do começo:** o WHEP 404 (o outro ainda não publicou) e a coleta ICE ficam fora do prazo.
- **A assinatura refeita pelo vigia não ganha a faixa vermelha** ("falhou"): aos 3 s ainda é cedo para avisar na tela.
- **Nunca endereços no registro:** só tipos de candidato e contadores, como já era com os candidatos da oferta.

## Pendências / próximos passos
- Sair numa versão de teste (`v2.0.0-teste.3`) e repetir as ligações no celular, no servidor do trabalho. Se travar de novo, o "Estava: …" diz em que passo foi.
- O registro do MediaMTX do servidor do trabalho às 14:40:20–14:41:00 (UTC 18:40) diria por que a sessão de 2026-11-10 não fechou (por exemplo, o tempo-limite do aperto de mão do WebRTC dele).
