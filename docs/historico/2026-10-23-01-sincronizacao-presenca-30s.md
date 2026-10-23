# 2026-10-23 · 01 · Sincronização: presença parada em 30 s e pré-conexão muda

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** FC-226 (novo); notas em FC-216 e FC-729; PRE-01, PRE-02, PRE-03, CFG-09 e CHA-27; linhas 🆕 no `TODO.md` (2.11, 6.x e 8.5)
- **Branch:** `reescrita`
- **Commits:** `4191d1a`
- **Commits de origem** (pull `--ff-only` em 2026-10-23):
  - **Servidor (`b104db8` → `331083b`):** `331083b` "Presença: a aba parada, mas visível, continua com a conversa aberta" (2026-10-22; só `src/websocket.ts` e o teste).
  - **Web (`3689155` → `17c49a4`), os dois de 2026-10-22:**
    - `30035bb` "Pré-conexão da chamada toca muda enquanto toca, sem atraso ao atender";
    - `17c49a4` "Presença: ausente após 30 s parado, com visto por último, e som de aguardar conexão menor".

## Contexto
- **O pedido** foi "faça pull e atualize a documentação com os commits novos".
- O repositório do Android tinha 21 commits sem push; o remoto da `reescrita` não tinha nada novo.
- O `yarn.lock` não versionado do `conversa-web` continua lá; o pull não mexeu nele.

## O que mudou (resumo)
- **Servidor, presença (WS 63):** antes, só uma conexão **ativa** contava como "com a conversa aberta". Agora conta também a conexão **parada** (`ativo:false`). A pessoa só sai da conversa com `conversa_id: null` ou ao fechar a conexão (`presencaReal`). Isso vale para o `GET /conversa/presentes` e para o WS 63 `aberta`. Nenhuma rota mudou, então o OpenAPI continua o mesmo.
- **Web, presença:**
  - a aba fica parada depois de **30 s** sem uso (antes, 5 min);
  - parada e visível, manda `{ativo:false, conversa_id:<aberta>}`: fica ausente e continua "nesta conversa"; escondida, manda `conversa_id: null`;
  - no cabeçalho da direta: **"ausente · visto por último …"** e, com a pessoa ausente, **"nesta conversa · visto por último …"**;
  - Privacidade › Status: "…ausente quando ele está escondido ou parado há **30 segundos**…".
- **Web, chamada (CHA-27):** a pré-assinatura toca o áudio **mudo** enquanto toca. Sem ninguém consumindo, os pacotes se acumulavam no buffer do WebRTC e a voz chegava ~1 s atrasada ao atender. O `aguardando.mp3` ficou menor (~270 KB).
- **Sem funcionalidade nova:** continuam 178.

## Achados no Android
- **Nada quebra:** o app manda `ativo` = app em primeiro plano e o `conversa_id` só com ele na frente (`PresencaRepositorio`). Em segundo plano já manda `{ativo:false, conversa_id:null}`, então ninguém fica preso na conversa.
- **PRE-03:** `subtituloPresenca` devolve "nesta conversa" e "ausente" sem o visto por último.
- **CFG-09:** `privacidade_status_texto` ainda diz "5 minutos".
- **PRE-02:** o app não tem o "parado" (na frente = em uso). Seguir o web é opcional: no celular, a tela costuma apagar sozinha e o app vai para segundo plano.
- **CHA-27:** o app não assina durante o toque (FC-729, opcional); se passar a assinar, precisa consumir esse áudio com o volume zero.

## Documentos atualizados
- **`01`:**
  - o último commit lido (`331083b`);
  - o WS 63 enviado (a conversa conta com a conexão parada; o web em 30 s) e o recebido (ficar parada não tira mais da conversa);
  - o `GET /conversa/presentes` (§11.9);
  - "Para o Android" em §15.1 e as regras de §15.2.
- **`03`:**
  - o cabeçalho e o sumário;
  - PRE-01, PRE-02 e PRE-03 (o subtítulo com " · visto por último");
  - o cabeçalho da direta em §2;
  - o texto do Status (CFG-09);
  - CHA-27 (a pré-assinatura muda e o `aguardando.mp3` menor);
  - a **§19 nova**, com os IDs alterados e o que muda para o Android.
- **`05`:**
  - o aviso da atualização;
  - PRE-03 e CFG-09 voltam a 🟡 (FC-226);
  - notas em PRE-02 e CHA-27;
  - o placar da nova base: **149 ✅, 5 🟡, 3 ⛔, 15 ⬜, 6 ➖ de 178**, recontado das linhas por script;
  - a leitura.
- **`06`:** FC-226 (subtítulo, texto do Status e o parado opcional); notas em FC-216 e FC-729.
- **`00`:** o aviso da atualização.
- **`TODO.md`:** linhas 🆕 em 2.11 (subtítulo; parado opcional), 6.x (áudio mudo na pré-assinatura, se houver) e 8.5 (texto do Status).
- **`08`:** nada novo. A S22 (WS 63 sem conferir a participação) continua igual.

## Como foi verificado
- `git show` dos três commits; pull `--ff-only` nos dois repositórios.
- O código do app conferido: `PresencaRepositorio` (o que manda), `subtituloPresenca` e `textoDoSubtitulo` (o cabeçalho), `privacidade_status_texto`.
- O placar do doc 05 recontado das 178 linhas da tabela por script.

## Pendências
- Implementar a FC-226 (subtítulo e texto; o parado é opcional).
