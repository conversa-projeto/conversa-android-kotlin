# 2026-11-10 · 02 · A `v2.0.0-teste.2` no celular, no servidor do trabalho

- **Fluxo:** Mensagens (etapa 3)
- **Tipo:** documentação
- **Itens:** `TODO.md` 3.7 (a linha **Teste:** da rede sem internet confirmada e uma linha nova, a dos dados móveis); ENV-01, FC-307
- **Commits:** (preencher)

## Contexto
A pessoa instalou a `v2.0.0-teste.2` (histórico `2026-11-10-01`) e mandou o registro do app do celular, sem texto. O registro vai das 13:30 às 14:41.
- **Aparelho:** Samsung SM-A256E (Galaxy A25), Android 16.
- **Servidor:** `https://10.0.5.78`, o do trabalho.

## O que o registro mostra
- **13:29–13:30, ainda na `teste.1`, no Wi-Fi do trabalho:** duas mensagens presas na fila (locais -1 e -2), o problema do histórico `2026-11-10-01`.
- **13:32:15, o Wi-Fi cai:** a rede padrão (117) caiu, e o celular passou para os **dados móveis** (118, com a internet confirmada).
  - O tempo real caiu junto ("Software caused connection abort").
  - O registro só guarda a rede padrão, então não diz se o Wi-Fi saiu de vez (fora de alcance, desligado) ou se ficou ligado por trás. Uma possível causa é o Samsung trocar sozinho o Wi-Fi "sem internet" pelos dados móveis ("Wi-Fi inteligente › Mudar para dados móveis").
- **13:32–14:24, nos dados móveis:** nada chegou ao servidor.
  - O `10.0.5.78` é endereço interno e não se alcança pela operadora.
  - Toda chamada levava 15 s e caía em `SocketTimeoutException: failed to connect to /10.0.5.78 (port 443) from /10.198.180.152`, o IP dos dados móveis.
  - Ainda na `teste.1`, o `EnvioWorker` enfim rodou (os dados móveis têm a internet confirmada) e foi tentando com espera crescente, até a execução 8.
  - A `teste.2` foi aberta às 14:03:47 nos dados móveis e ficou igual: a faixa "fora do ar", o tempo real tentando a cada ~30 s e a carga da sessão a cada 20 s.
  - O trabalho de nome antigo (`envio_mensagens`) ainda rodou uma vez às 14:17:25 (execução 9). O cancelamento dele só acontece no primeiro `agendar()`, e ele não duplica nada, porque só esvazia a fila do Room.
- **14:24–14:33:** o processo parou. Não há "app fechado", então foi o sistema ou a pessoa tirando o app da lista.
- **14:33:17, o app reaberto:** às 14:33:19 a rede padrão virou o **Wi-Fi** (120), com "internet confirmada: não", a rede do histórico `2026-11-10-01`.
  - As primeiras chamadas, ainda abertas pelos dados móveis, falharam (`ConnectException`). São os 8 avisos depois do Wi-Fi voltar, e mais nenhum.
  - **14:33:26:** "Tempo real conectado com 2 mensagem(ns) na fila: enviando já". As duas presas desde as 13:29/13:30 saíram, 8630 em 52 ms e 8631 em 36 ms. O worker veio junto e achou a fila vazia, sem duplicar.
  - **Daí em diante:** cada mensagem nova saiu em 18–80 ms (8632 a 8656), também depois de voltar do segundo plano.
  - **Chamadas:** uma recebida (169) e duas feitas, de áudio (170 e 171), com WHIP/WHEP e as conexões CONNECTED.

## O que foi feito
- `TODO.md` 3.7: marcada a linha **Teste:** do celular no Wi-Fi do trabalho, com este registro.
- `TODO.md` 3.7: linha nova para o servidor interno nos dados móveis (abaixo).
- Este histórico e a linha no `indice.md`.

## Como foi verificado
- A leitura do registro `0c638738-2026-11-10.txt` (1830 linhas):
  - as linhas de `Rede`, `Envio`, `TempoReal`, `Sessao`, `Http`, `Chamada` e `Midia`;
  - nenhum `E/`;
  - depois do Wi-Fi voltar, só os 8 avisos das chamadas que estavam nos dados móveis.

## Decisões
- **A correção da `teste.2` está confirmada:** no Wi-Fi sem internet confirmada, a fila saiu assim que o tempo real conectou, e as mensagens novas saem na hora.
- **Os dados móveis com um servidor de endereço interno são outro assunto:** nenhum app alcança o `10.0.5.78` pela operadora (sem VPN). O que dá para melhorar fica na pendência abaixo, para decidir com a pessoa.

## Pendências / próximos passos
- **Servidor interno nos dados móveis (linha nova no 3.7):**
  - registrar todas as redes, e não só a padrão, para saber se o Wi-Fi ficou ligado por trás;
  - se o Wi-Fi estiver ligado e o servidor for de endereço interno, usar o Wi-Fi nas conexões do app (`requestNetwork` sem `VALIDATED` e o `socketFactory` da rede no OkHttp e no WebSocket);
  - um aviso claro na faixa de "fora do ar": "o servidor é interno e não responde pelos dados móveis".
