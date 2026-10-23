# 2026-10-22 · 07 · Espera do ICE com o TURN por UDP: medida e encurtada

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.1 (a linha 🆕 `383eaaa` "Medir o tempo da publicação…"); FC-702; S41 (doc 08)
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
Desde o servidor `383eaaa`, o `GET /ice` manda o TURN por UDP primeiro e o TCP de reserva. Sem trickle ICE, o app espera a coleta terminar (até 5 s) antes de mandar a oferta ao MediaMTX. Onde o UDP não tem caminho (emulador, Tailscale Funnel, firewall), a coleta só termina quando a alocação UDP desiste. Faltava medir quanto isso custa a cada WHIP/WHEP.

## Medição (emulador, servidor de dev)
- **Como:** um proxy de medição local, na frente do proxy de dev, anotou o instante do `GET /api/ice` e do `POST /webrtc/…/whip` seguinte. Chamadas de voz de teste do A para o B e para o C, sem atender, desligadas depois de alguns segundos; o servidor e o coturn não mudaram.
- **`/ice` como o servidor manda (UDP primeiro):** 5.319 ms entre o `/ice` e o WHIP, o limite inteiro.
- **`/ice` só com o TCP (reescrito pelo proxy):** 109 ms.
- A assinatura (WHEP) espera a mesma coleta, então cada pessoa que entra custava mais 5 s.

## O que foi feito
- **`EsperaColeta` (`core/webrtc/.../FabricaWebRtc.kt`):** a oferta sai com a coleta completa **ou** 1 s (`FOLGA_DEPOIS_DO_RELAY_MS`) depois do primeiro candidato do TURN (`typ relay`), sempre até os 5 s (`ESPERA_ICE_MS`).
  - Com o UDP funcionando, o candidato UDP chega antes do TCP, e a coleta costuma completar dentro da folga.
  - Sem o UDP, o candidato TCP chega em ~0,1 s e a oferta sai ~1 s depois, com ele no SDP.
- **O `Observador`** passa a avisar a `EsperaColeta` em cada candidato (`onIceCandidate`) e na mudança da coleta.
- **Teste:** `EsperaColetaTest` (5 testes, com o tempo virtual): coleta completa, só o TCP (1 s depois do TURN), UDP completando dentro da folga, nada chegando (5 s) e o tipo do candidato.

## Como foi verificado
- `:core:webrtc:testDebugUnitTest`.
- **No emulador, com o `/ice` como o servidor manda:** 1.336 ms entre o `/ice` e o WHIP (era 5.319 ms). O log do MediaMTX mostrou a publicação conectada pelo relay ("peer connection established… remote candidate: relay/udp/…", "is publishing to path 'call-72-u-1'").
- **A primeira chamada de teste (70) ficou aberta** porque o proxy de medição parou antes do "Sair da chamada". O B recusou pela API (`nao_atendeu`), como numa chamada não atendida. As outras (71, 72) terminaram pelo app (status 6, cancelada).

## Decisões
- **Encurtar a espera só no app, sem trickle ICE:** o MediaMTX aceita `PATCH` com candidatos, mas o web não usa (doc 07). A folga de 1 s depois do primeiro relay mantém a oferta completa onde o UDP passa.
- **O web continua esperando os 5 s.** A proposta do servidor (anunciar o UDP só quando houver caminho, S41) continua valendo para os dois clientes.

## Pendências
- **CP do FC-702:** chamada 4G ↔ Wi-Fi com áudio nos dois lados, em aparelho.
