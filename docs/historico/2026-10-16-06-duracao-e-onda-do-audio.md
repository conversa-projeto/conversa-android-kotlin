# 2026-10-16 · 06 · Duração e onda do áudio

- **Fluxo:** Anexos e mídia (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 4.5/4.6 (linha 🆕 `ef13c22`); FC-421; ANX-10; ANX-11
- **Branch:** `reescrita`
- **Commits:** `2f0b29d`

## Contexto
- **O servidor** (`ef13c22`) guarda a `duracao` (segundos, 0 a 86400) e a `intensidade` (até 200 picos de 0 a 100) que o cliente manda no `PUT /anexo` de um áudio (tipos 4 e 5) e as devolve em cada conteúdo (contrato §8.2, §10.2). Valem as do primeiro envio daquele arquivo.
- **O web** (`9c6e8a6`) mede no navegador (`utils/dadosAudio.ts`: decodifica, 48 picos, até 30 MB), mostra a duração antes de tocar, "atual / total" e a onda (`OndaAudio.vue`).
- **No app,** a duração só aparecia depois de tocar, e o áudio enviado pelo Android aparecia no web só com a barra simples.

## O que foi feito
- **`core:model` (`DadosAudio.kt`):** `DadosAudio`, `BARRAS_ONDA` (48), `picos` (a mesma conta do web: o maior de cada trecho, relativo ao mais alto), `arredondarDuracao` (2 casas), a intensidade como texto para o Room e `textoDoTempoDoAudio` ("atual / total"); `Conteudo.duracao`/`intensidade`; `TipoConteudo.ehAudio`.
- **Rede:** `duracao` e `intensidade` no `ConteudoDto` e no `IncluirAnexoRequisicao`.
- **Room v10:** `duracao` e `intensidade` em `conteudo` (AutoMigration 9→10, nulas); `MensagemDao.gravarDadosAudio`.
- **`MedidorAudio`** (`core:data`): decodifica com MediaExtractor + MediaCodec (PCM 16 bits ou float), guarda o pico de cada janela de 10 ms e faz as 48 barras; a duração sai das amostras decodificadas. Acima de 30 MB, ou se não der para decodificar, o áudio vai sem os dados, como antes.
- **Envio:** `subirAnexos` mede o áudio uma vez (o resultado fica no pacote da fila; numa nova tentativa não mede de novo), grava na otimista e manda no `PUT /anexo`. O áudio encaminhado leva os dados da original. Se não der para buscar a mensagem real, a otimista gravada com o id real fica com eles.
- **Player na bolha:**
  - a duração gravada vale antes de tocar e prevalece sobre a do player, como no web;
  - o texto vira "atual / total" tocando ou parado no meio;
  - com a intensidade, a barra vira a onda: uma barra por pico, 2 dp entre elas, 28 dp de altura, cada uma com pelo menos 12%, as tocadas na cor cheia. O arraste e o leitor de tela continuam iguais.
- **Testes:** `DadosAudioTest` (picos, silêncio, menos amostras que barras, texto, tempo), `MensagensTest` (mede uma vez, a otimista ganha a onda, o `PUT` leva os dois), `AnexosRepositorioTest` (o corpo do `PUT /anexo`) e `MigracoesTest` (versão 10).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev:**
  - um WAV de teste gerado com Node (6 s, tom de 440 Hz com o volume subindo e descendo duas vezes) foi copiado para o `Download` do emulador e enviado pelo "Documento" na conversa de teste com o C;
  - a bolha mostrou "00:06" antes de tocar e a onda com os dois "morros" (o segundo mais baixo);
  - no servidor, `anexo_audio` ficou com `duracao = 6.00` e 48 picos.
  - O áudio não foi tocado no teste (sairia som nas caixas do PC); "atual / total" e a cor das barras tocadas estão nos testes e no código.

## Decisões
- **Medir no upload, não ao tocar "Enviar":** decodificar um áudio grande pode levar segundos, e a bolha aparece na hora. A gravação, curta, é medida em instantes, e a otimista ganha a onda logo em seguida.
- **Janelas de 10 ms:** guardam só um valor por janela (10 min de áudio = 60 mil números), sem segurar o PCM inteiro na memória; o resultado é o mesmo do web na prática.
- **Áudio antigo, sem os dados:** continua com a barra simples e a duração depois de tocar. O web busca só a duração quando o player aparece; aqui ficou como antes (baixaria o arquivo).

## Pendências
- Nenhuma do FC-421.
