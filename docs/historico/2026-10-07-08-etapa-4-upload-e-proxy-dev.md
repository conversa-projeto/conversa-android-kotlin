# 2026-10-07 · 08 · Etapa 4 — envio de anexos, URLs assinadas, token só na API e proxy de desenvolvimento

- **Fluxo:** Anexos e mídia (etapa 4); também Fundação (1.8, segurança) e Repositório e build (ferramenta de dev)
- **Tipo:** código / testes / segurança / ferramenta / documentação
- **Itens:** `TODO.md` 4.1, 4.3 (dados) e 1.8 (🆕); ANX-02, ANX-14
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
Primeiro bloco da etapa 4: o caminho dos arquivos até o servidor e de volta, antes das telas (bolhas de imagem/arquivo/áudio e seletor).

## O que foi feito

### `AnexosRepositorio` (`core/data/anexos/`)
- **Envio:**
  - SHA-256 em fluxo (blocos de 64 KB), que vira o `identificador`;
  - `PUT /anexo`; se já existe, não sobe nada (deduplicação);
  - senão, `PUT` dos bytes na URL assinada, em fluxo e com progresso;
  - `POST /anexo/confirmar?identificador=`;
  - URL vencida (403 do MinIO) → pede outra uma vez;
  - acima de 1 GiB nem começa.
- **Leitura:** `url(identificador)` com cache até 1 min antes dos 600 s; `esquecerUrl` para quando a imagem falhar; o cache é limpo no fim da sessão.
- `FonteArquivo`: arquivo lido em fluxo, nunca inteiro na memória. A versão com `ContentResolver` entra com o seletor.

### Segurança: o token só vai para a API
- O app tem um OkHttp só, e o `AutenticacaoInterceptor` colocava `Authorization: Bearer` em **todo** pedido: URLs assinadas do MinIO, imagens do Coil e qualquer outro endereço.
  - O MinIO recusa pedido com duas formas de autenticação, então o upload e as imagens falhariam.
  - Além disso, o token sairia do servidor.
- Agora o token só vai para `<base>/api/` do servidor configurado (mesmo esquema, host, porta e caminho). Teste novo no `RedeTest`. Item 🆕 no 1.8 do TODO.

### Ferramenta: `ferramentas/proxy-dev.mjs`
- Proxy HTTP (Node, sem dependências) que faz o papel do nginx sem TLS em `127.0.0.1:8081`:
  - `/storage/` vai para o MinIO, mantendo o `Host` da assinatura;
  - o resto, inclusive o WebSocket, vai para a API, com `X-Forwarded-Proto: http`.
- Permite testar anexos no emulador sem instalar a CA do mkcert no aparelho.
- Testado de ponta a ponta com um script: `PUT /anexo` → upload pela URL assinada → confirmar → `GET /anexo` → download igual aos bytes enviados. Repetir o mesmo arquivo devolve `existe=true` com o `id` em texto, como diz o contrato.
- `docs/desenvolvimento/emulador.md` reescrito para usar o proxy (porta 8081).

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
- **171 testes, 0 falhas.** Lint: 0 apontamentos.
- Novos: `AnexosRepositorioTest` (6, com MockWebServer fazendo o papel do MinIO):
  - bytes iguais e sem `Authorization`;
  - "já existe" sem upload;
  - URL vencida;
  - limite de 1 GiB;
  - cache da URL de leitura.
- Novo no `RedeTest`: token só para a API.

## Decisões
- **Proxy de desenvolvimento em vez de mexer no aparelho:** mesma regra do bloco do emulador (não alterar a segurança do dispositivo). O proxy só escuta em `127.0.0.1` e só serve para desenvolvimento.
- **Cache de URL por identificador:** o conteúdo de um identificador nunca muda; a URL muda a cada 600 s.

## Pendências
- `UploadWorker`, seletor (galeria, câmera, documento), fila acima do campo e envio da mensagem com anexos (4.2).
- Bolhas de imagem (Coil com chave = identificador), arquivo e áudio (4.4–4.7).
- Teste do vídeo de 200 MB no emulador.
