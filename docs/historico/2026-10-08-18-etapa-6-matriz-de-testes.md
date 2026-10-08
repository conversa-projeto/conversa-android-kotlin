# 2026-10-08 · 18 · Etapa 6 — matriz de testes manuais de chamada

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** documentação / teste manual
- **Itens:** `TODO.md` 6.14; FC-955
- **Branch:** `reescrita`
- **Commits:** `610bbe1`

## Contexto
O TODO 6.14 pede uma matriz de testes manuais de chamada:
- rede;
- estado do app;
- Bluetooth, fone e ligação de celular;
- tipos de chamada.

Boa parte já foi testada no emulador, nos blocos anteriores. O resto depende de aparelho de verdade ou de bloqueios conhecidos.

## O que foi feito
- **`docs/testes/chamadas.md`:** a matriz em quatro grupos (rede, estado do app, áudio e sistema, tipos de chamada).
  - Cada cenário diz onde se testa (emulador ou aparelho), a situação (passou, falta, bloqueado) e observações.
  - Ficou preenchida com o que passou no emulador em 2026-10-08.
- **Teste novo no emulador:** "atender no web com o celular tocando".
  - O A ficou logado também num terceiro Chrome de teste.
  - O B ligou para o A; o celular e o web tocaram; o A atendeu no web.
  - O celular parou de tocar: a tela fechou e a chamada saiu do Telecom.
- **`CLAUDE.md`:** a matriz entrou no "Onde está cada coisa".

## Como foi verificado
- No emulador, como descrito acima; os demais itens marcados como passados vêm dos blocos 2026-10-08 · 09 a 17.

## Pendências / próximos passos
- **Em aparelho de verdade:**
  - Wi-Fi ↔ 4G e 4G ↔ 4G;
  - tela bloqueada com PIN;
  - fone Bluetooth e com fio;
  - fone do aparelho ↔ alto-falante e o sensor de proximidade;
  - TalkBack;
  - somente recepção;
  - grupo de 4 (com mais uma conta de teste).
- **App fechado:** ⛔ depende do FCM (5.1) e do S1 no servidor.
