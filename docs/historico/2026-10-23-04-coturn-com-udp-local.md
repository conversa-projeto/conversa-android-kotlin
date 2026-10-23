# 2026-10-23 · 04 · Coturn local com a porta UDP

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** ambiente de desenvolvimento e documentação
- **Itens:** FC-702 (TURN por UDP do servidor `383eaaa`); `docs/desenvolvimento/emulador.md`
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
Desde o servidor `383eaaa` (2026-10-19), o `docker-compose.yml` publica a 3478 em TCP e em UDP, e o `GET /ice` manda o UDP primeiro. O contêiner `coturn` do Docker local não tinha sido recriado e publicava só a 3478/TCP. A pessoa liberou as pendências opcionais em 2026-10-23.

## O que foi feito
- Em `../conversa`: `env -u CONVERSA_DATABASE -u CONVERSA_PORTA docker compose up -d --no-deps coturn`. Só o coturn foi recriado; os outros contêineres não foram tocados.
- **Resultado:** `docker port coturn` mostra a 3478 em TCP e em UDP (IPv4 e IPv6).
- **`emulador.md`:** a nota "precisa ser recriado" virou o estado atual.

## Como foi verificado
- O log do coturn subiu sem erro.
- **No emulador**, uma ligação de teste do A para o C (chamada 73, contas de teste, sem atender e desligada em seguida): o MediaMTX mostrou a publicação conectada pelo relay ("peer connection established… remote candidate: relay/udp/…", "is publishing to path 'call-73-u-1'").
- No emulador, o UDP continua sem caminho (o `adb reverse` só leva TCP). A espera de 1 s depois do primeiro relay (2026-10-22-07) é o que mantém a oferta rápida ali. Em aparelho na mesma rede, o UDP agora responde.

## Pendências
- Nenhuma.
