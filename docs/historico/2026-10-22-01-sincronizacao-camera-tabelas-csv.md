# 2026-10-22 · 01 · Sincronização: câmera com foto ou vídeo e tabelas CSV/TSV

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** linhas 🆕 no `TODO.md` (4.2, 7.9 e 7.11); FC-426 e FC-529; ANX-20 e MSG-26; o OpenAPI pendente de 2026-10-19
- **Branch:** `reescrita`
- **Commits:** (preencher)
- **Commits de origem** (pull `--ff-only` em 2026-10-22; o servidor não mudou desde `b104db8`):
  - **Web (`57bd9f0` → `3689155`), os dois de 2026-10-19:**
    - `77c7821` "Câmera no "+": foto ou vídeo direto da câmera, com prévia e legenda";
    - `3689155` "Tabelas CSV e TSV nas mensagens e tabela colada vira bloco de tabela".

## Contexto
- **O pedido** foi "faça pull e atualize a documentação com as pendências dos commits que chegaram".
- **Só o web tinha commits novos.** O repositório do Android tinha os 2 commits do sincronismo anterior (`540e79b`, `62a371b`) ainda sem push.
- **O Docker voltou:**
  - a API já tinha aplicado a migração 057 ("Migrações aplicadas até a versão 57"; a permissão `sip` existe no `conversa_dev`). Na subida desta vez, ela tentou antes de o Postgres ficar pronto, caiu e subiu de novo sozinha;
  - deu para fechar a pendência do OpenAPI do sincronismo de 2026-10-19;
  - o coturn continua publicando só a 3478/TCP: o contêiner não foi recriado (o UDP do `383eaaa` ainda não vale no Docker local).
- **O `yarn.lock` não versionado do `conversa-web`** continua lá; o pull não mexeu nele.

## O que mudou (resumo)
- **Web: 176 → 178 funcionalidades.**
  - **ANX-20, câmera no "+":**
    - no celular, abre a câmera do sistema; no computador, só aparece com webcam e abre a tela dela (clique tira a foto, segurar grava, arrastar para fora trava);
    - a prévia tem "Adicione uma legenda...", "Refazer" e "Enviar", que manda na hora como [arquivo, legenda], o arquivo antes do texto;
    - vale a permissão de uso `imagem` ou `video`;
    - não há limite de duração. A foto sai em JPEG; o vídeo, em WebM ou MP4.
  - **MSG-26, tabelas CSV e TSV:**
    - blocos ```csv e ```tsv aparecem como tabela, com "Visualizar" (padrão), "Código" e "Copiar" dos dados originais;
    - a primeira linha é o cabeçalho, e as colunas numéricas ficam à direita;
    - o separador do CSV (`;` ou `,`) é escolhido pelo número de colunas.
  - **Mudaram:**
    - ENV-09: tabela colada do Excel, do Sheets ou em CSV vira direto o bloco `tsv`/`csv`, sem janela;
    - ENV-10: `csv` e `tsv` na lista de linguagens;
    - também ENV-21, ENV-28, MSG-15, ANX-08 e GER-09.
- **Servidor:** nada mudou. O conteúdo continua o mesmo bloco de código (texto).

## Achados no Android
- **Nada quebra:** as tabelas chegam como bloco de código com a linguagem `csv`/`tsv` e aparecem como código comum, com o texto cru; a foto e o vídeo chegam como imagem e vídeo comuns.
- **Câmera:**
  - o "+" do app já tem "Câmera", mas só para foto, que vai para a fila de anexos e sai com o Enviar;
  - falta o vídeo, a prévia com legenda e o envio na hora;
  - a ordem do web é o arquivo antes da legenda, e o `EnvioMensagens.enviar` de hoje põe o texto antes dos anexos.
- **Tabelas:** o app não tem `csv`/`tsv` em `LINGUAGENS_CODIGO`, não lê tabela e, ao colar mais de 10 linhas, abre o "Inserir código" sem detectar tabela.

## Documentos atualizados
- **`03`** (por um agente, conferindo os textos no web):
  - os IDs novos ANX-20 e MSG-26;
  - os alterados ENV-09, ENV-10, ENV-21, ENV-28, MSG-15, ANX-08 e GER-09;
  - os textos da câmera em §6 e as regras 32 e 33;
  - a **§18 nova**, com as regras do `tabelaCsv.ts` e o que muda para o Android;
  - o total de 178.
- **`05`:**
  - as 2 linhas novas: MSG-26 ⬜ e ANX-20 🟡 (o app já tira foto);
  - notas em ENV-09, ENV-10 e MSG-15;
  - o placar da nova base: **147 ✅, 4 🟡, 3 ⛔, 18 ⬜, 6 ➖ de 178**, recontado das linhas por script.
- **`06`:** FC-426 (câmera com foto ou vídeo, prévia e legenda) e FC-529 (tabelas CSV/TSV, colar tabela e as linguagens).
- **`00`:** o aviso, o HEAD do web e os totais.
- **`TODO.md`:** linhas 🆕 em 4.2 (câmera), 7.9 (tabelas no `BlocoCodigo`) e 7.11 (colar tabela e as linguagens).
- **`docs/contrato/openapi.json`:**
  - baixado de `https://localhost/api/docs/json` (servidor `b104db8`): de 120 para 123 entradas;
  - entraram `POST /api/pastas/sugerir`, `GET /api/pastas/sugestao` e `GET`/`PUT`/`DELETE /api/sip/usuario`; saíram `PUT` e `PATCH /api/sip`; nenhuma outra mudou;
  - gravado no mesmo formato (indentado com 2 espaços).

## Como foi verificado
- `git log --stat` dos dois commits; pull `--ff-only`.
- O OpenAPI novo comparado com o salvo por script: entradas novas, removidas e alteradas. Bate com o doc 01 (rotas 120–124 e as 63–64 riscadas).
- O placar do doc 05 recontado das linhas da tabela por script (178 linhas).
- No banco de dev, a permissão `sip` (migração 057) existe.

## Decisões
- **ANX-20 fica 🟡, não ⬜:** a câmera do app já tira e envia foto. Falta o vídeo e a prévia com legenda.
- **A tela da webcam do computador não vale para o celular** (segurar para gravar, arrastar para travar, trocar de câmera).

## Pendências
- **As FC novas:** FC-426 (câmera) e FC-529 (tabelas).
- **No ambiente de dev:** recriar o coturn para o TURN por UDP valer (`env -u CONVERSA_DATABASE -u CONVERSA_PORTA docker compose up -d coturn`, em `../conversa`).
