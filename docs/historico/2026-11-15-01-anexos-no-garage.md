# 2026-11-15 · 01 · Sincronização: anexos no Garage, URL vencida com 400 e o "já existe" do anexo pendente

- **Fluxo:** Sincronização com servidor e web (cita também Anexos e mídia)
- **Tipo:** correção | documentação
- **Itens:**
  - ANX-02, ANX-09, ANX-14;
  - `TODO.md` 4.1 e 4.3: as notas nas linhas antigas e as linhas 🆕;
  - pendência S43 (nova) e S29/S30 no doc 08; pegadinha 38 do doc 01.
- **Branch:** `reescrita`
- **Commits:** `23238bf`
- **Commits de origem** (pull `--ff-only` em 2026-11-15):
  - **Servidor (`331083b` → `db5616d`):**
    - `d9b2646` (2026-11-14) "Anexos no Garage no lugar do MinIO, com script de migração";
    - `db5616d` (2026-11-14) "Migração do MinIO apaga o volume antigo no fim, depois do Enter".
  - **Web (`1dab7dd` → `361716d`):** `361716d` (2026-11-14) "Anexos no Garage: nomes e documentação sem o MinIO".

## Contexto
- **O pedido:** baixar o servidor e o web, atualizar o Kotlin e a documentação da reescrita, fazer commit e push e avisar a conversa "Conversa - Desktop (Iron)".
- **O que mudou no servidor:**
  - o serviço `garage` (Garage v2.5.0, imagem do `bin/garage/Dockerfile`) substitui o `minio`: S3 em `garage:3900`, publicado em `127.0.0.1:3900`, região `us-east-1`;
  - as chaves vêm de `/dados/garage-chave`, `/dados/garage-segredo` e `/dados/garage-rpc`; o volume é o `conversa-garage`;
  - `src/minio.ts` virou `src/armazenamento.ts`; o nginx manda `/storage/` para o `garage_upstream`;
  - `bin\migrar-minio-garage.bat` copia os anexos do MinIO e, depois do Enter (`db5616d`), apaga o volume antigo.
- **O que mudou no web:** só nomes (`uploadMinio` → `uploadArmazenamento`), comentários e documentação. O fluxo de anexo é o mesmo.
- **As rotas não mudaram.** As URLs assinadas continuam em `/storage/<bucket>/<chave>`, com `X-Amz-*`.

## O que o Garage responde (testado em 2026-11-14/15)
| Pedido | Resposta |
|---|---|
| URL vencida (GET e PUT) | **400** `InvalidRequest` "Bad request: Date is too old" (o MinIO dava 403) |
| Assinatura errada | 403 `AccessDenied` "Forbidden: Invalid signature" |
| Sem assinatura | 400 |
| PUT em partes (chunked) | 200 |
| GET com `Range` | 206 |
| Objeto que não existe | 404 `NoSuchKey` |
| `Authorization: Bearer` junto da URL assinada | ignorado (200); o MinIO recusava |
| HEAD numa URL assinada para GET | 403 |

## O achado: o "pedir outra URL" do envio nunca funcionou
- `incluirAnexo` (`PUT /anexo`) responde `{existe: true, id, upload_url: <GET 600 s>}` para **qualquer** registro com o identificador, também o pendente (`upload_status` 0), cujo arquivo nunca chegou ao armazenamento.
- Com a URL recusada, o app pedia outra (a linha da 4.1 "URL vencida no meio → pedir de novo"). A resposta era "já existe", e o app pulava o envio e mandava a mensagem **com o anexo quebrado**: `GET /anexo` → 400 "Upload ainda não foi concluído".
- Isso já acontecia com o MinIO. Só não apareceu antes porque o teste unitário simulava um servidor que dava outra URL de envio.
- O identificador fica preso: o mesmo arquivo dá o mesmo SHA-256, e nenhuma rota dá outra URL de envio. Proposta ao servidor: pendência **S43** do doc 08.
- O web confia no "já existe" do mesmo jeito (doc 03 §21).

## O que foi feito
- **Código:**
  - `core/data/.../anexos/AnexosRepositorio.kt`:
    - `urlAssinadaRecusada(codigo)`, pública: 400 ou 403;
    - o envio pede outra URL uma vez com qualquer um dos dois;
    - o ramo "já existe" chama `POST /anexo/confirmar`, que confere o arquivo no armazenamento. Com o arquivo, segue (e marca o pendente que já tinha subido). Sem o arquivo, a falha volta com o motivo do servidor: 400 "Arquivo não encontrado no armazenamento". O `EnvioMensagens` trata o 4xx como falha definitiva, e a bolha fica "Não enviada";
    - o KDoc da classe explica o "já existe" e a S43.
  - `core/data/.../anexos/ArquivosLocais.kt`: renova a URL com `urlAssinadaRecusada` (antes, só com 403).
  - `app/.../imagens/FetcherAnexo.kt`: o mesmo; a constante própria do 403 saiu.
  - Só comentários, sem o MinIO:
    - `core/network/.../http/Interceptadores.kt`: o token nunca vai para `/storage/`; o Garage ignora o cabeçalho e o MinIO recusava duas autenticações;
    - `core/network/.../dto/AnexoDtos.kt`;
    - `core/network/src/test/.../RedeTest.kt`.
- **Testes:**
  - `AnexosRepositorioTest`, com 14 testes:
    - "já existe" com o `confirmar`;
    - "já existe" sem o arquivo: falha com o motivo do servidor;
    - URL vencida com o servidor de hoje: "já existe", o `confirmar` recusa e nada vai quebrado;
    - URL vencida com um servidor que corrigiu a S43: sobe de novo, com o 400 do Garage;
    - assinatura recusada (403): pede outra uma vez;
    - recusada de novo: para;
    - 500 não pede outra URL;
    - a tabela de `urlAssinadaRecusada`.
  - `ArquivosLocaisTest` (4): "URL vencida (400 do Garage) - pede outra e baixa".
- **Ferramenta:** `ferramentas/proxy-dev.mjs`:
  - `/storage/` vai para o Garage (`GARAGE_PORTA`, padrão 3900);
  - `URL_VENCIDA=1` recusa o primeiro GET **e** o primeiro PUT de cada arquivo com 400 `InvalidRequest`, como o Garage; `URL_VENCIDA=403` recusa com 403 `AccessDenied`, como o MinIO;
  - o corpo do PUT recusado é lido inteiro antes da resposta.
- **Documentação:**
  - 🆕 nas partes que mudaram;
  - `00-LEIAME.md`: o parágrafo da atualização e a linha do doc 01;
  - `01-contrato-servidor-atual.md`:
    - o serviço `garage`, os volumes, §1.2, a rota `/storage`;
    - §8 "Anexos / armazenamento / transcrição", com as respostas do Garage no passo 4 do §8.2;
    - pegadinhas 38 e 40;
    - a linha no registro de atualizações;
  - `02-mudancas-servidor-desde-abril.md`: linhas 39 e 40 e uma nota no §7;
  - `03-inventario-web.md`: a linha do envio, o diagrama e o §21 "Atualização de 2026-11-15";
  - `05-matriz-paridade.md`: a nota de 2026-11-15, ANX-02 e ANX-14;
  - `08-pendencias-servidor.md`: S29 e S30 com o Garage, a **S43** nova e a prioridade 4;
  - `docs/desenvolvimento/emulador.md`:
    - o proxy vai para o Garage;
    - a lista do Docker;
    - a migração feita nesta máquina;
    - a pegadinha do `api` que se recarrega no pull;
  - `CLAUDE.md`: o servidor usa o Garage;
  - `TODO.md`: notas nas linhas antigas da 4.1 e da 4.3, mais as linhas 🆕 (o `confirmar` no "já existe", o 400 do Garage, o teste no emulador e a leitura).

## Servidor de dev desta máquina (migração)
- A migração seguiu os passos do `bin\migrar-minio-garage.bat`, feitos à mão:
  - `docker compose up -d --build --wait postgres garage`;
  - o `mc mirror` do bucket `chat`: 24 objetos, 206 MiB;
  - o `mc diff` não mostrou diferença;
  - `docker compose up -d api nginx`, `docker rm -f minio` e `nginx -s reload`. O `nginx` não foi recriado e ainda apontava para o `minio`.
- **O volume `conversa-minio` foi mantido:** o passo do Enter, que o apaga, ficou de fora.
- **Pegadinha:** o `api` de desenvolvimento lê o `src` da pasta e se recarrega sozinho. O `git pull` derrubou a API até o `garage` subir.

## Como foi verificado
- **Testes unitários:**
  - `AnexosRepositorioTest` 14/14, `ArquivosLocaisTest` 4/4 e `RedeTest` 14/14;
  - a verificação completa antes do commit: build, testes, lint, ktlint, `compileDebugAndroidTestKotlin` e `textos-repetidos`.
- **Emulador:** `Medium_Phone_API_36.1` contra o servidor de dev, já no Garage, pelo `proxy-dev`.
- **Leitura com `URL_VENCIDA=1`:**
  - imagem: 400 → URL nova → 200;
  - áudio: baixou na segunda URL e tocou;
  - o quadro do vídeo tentou de novo.
- **Envio com `URL_VENCIDA=1`:**
  - imagem nova: `PUT /anexo` → PUT no Garage 400 → `PUT /anexo` "já existe" → `confirmar` 400 "Arquivo não encontrado no armazenamento";
  - a bolha fica "Não enviada", com Reenviar e Apagar;
  - no banco, o anexo ficou pendente (status 0) e não foi criada mensagem.
- **Envio sem as recusas:**
  - imagem nova: PUT 200 e `confirmar` 200, enviada; no banco, status 1;
  - a mesma imagem de novo: só `PUT /anexo` "já existe" e `confirmar` 200, enviada.
- **Antes da correção:** o mesmo teste mandou a mensagem com o anexo quebrado (conversa 5, com Teste Android C).

## Decisões
- **Renovar com 400 e com 403:**
  - o 400 do Garage é a URL vencida;
  - o 403 é a assinatura errada, e também a vencida de um armazenamento como o MinIO.
  - Outros códigos (404, 413, 500…) não pedem outra URL.
- **O `confirmar` também no "já existe":** custa uma chamada a mais por anexo repetido, mas é a única rota que confere o arquivo no armazenamento. O "já existe" sozinho não garante nada (pegadinha 38).
- **O app não tenta contornar a S43:** o envio falha com o motivo do servidor. O laço que pede outra URL fica como está, pronto para o dia em que o servidor devolver uma URL de envio ao pendente.

## Pendências / próximos passos
- **S43 no servidor:** com o registro pendente e sem o objeto, `incluirAnexo` deve devolver uma URL de envio. Até lá, o anexo cujo envio parou no meio não sobe mais, no app e no web.
- **No dev**, ficam como lixo de teste:
  - a mensagem quebrada da conversa 5: anexo `772106dc…`, pendente;
  - o anexo `cfc906b1…`, pendente e sem mensagem;
  - as imagens `teste-garage-*.png` do emulador.
- **O volume `conversa-minio`** continua no Docker; apagar só quando não for mais preciso.
- **O desktop (Aylis)** foi avisado: a URL vencida dá 400, e o "já existe" precisa do `confirmar`.
