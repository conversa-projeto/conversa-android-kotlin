# 2026-10-08 · 31 · Sincronização: agendadas fora do chat, rascunho e encaminhada de encaminhada

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** `TODO.md` 7.5, 7.10, 7.11 (linhas 🆕); FC-514, FC-519 (novo), FC-520 (novo); ENV-23 (novo)
- **Branch:** `reescrita`
- **Commits:** (preencher)
- **Commits de origem** (pull feito em 2026-10-08, à noite):
  - servidor `779c8ed`: "iniciar-desenvolvimento.bat atualiza também as dependências da API";
  - web `7322e83` (2026-10-08 19:50): "Campo de mensagem: seleção, desfazer e rascunho; reações rápidas e agendadas";
  - web `e8d82cb`: "Campo de mensagem no Tiptap; 'mais emojis' na barra de reações rápidas".

## Contexto
Antes de seguir o 7.12, conferi os repositórios irmãos (CLAUDE.md, "Como trabalhar com o TODO"). Os dois tinham commits novos. O pull foi `git pull --ff-only`, sem alterações locais; no servidor só o `yarn.lock` está fora do Git e não foi tocado.

## O que mudou no servidor
- **`779c8ed`:** o script de desenvolvimento passa a rodar o `bun install` também na API. Não muda o contrato, que segue com 69 rotas.

## O que mudou no web
- **`7322e83`:**
  - **Agendadas fora do chat:** a agendada não aparece no chat antes da hora. Com o campo vazio, um relógio ao lado do microfone mostra quantas há e abre "Mensagens agendadas", com horário, resumo e "Cancelar". Na hora, a mensagem sai da lista e entra no chat.
  - **Rascunho por usuário e conversa** (IndexedDB), com o campo, os arquivos e a resposta pendente; restaurado ao voltar à conversa.
  - **Encaminhada de encaminhada:** dentro da citação, os conteúdos que vieram da citação de baixo não se repetem.
  - **Menu:**
    - Ctrl + clique direito abre só as reações rápidas;
    - toque longo no celular abre o menu completo.
  - **Campo rico:** seleção de peça, desfazer próprio.
  - **Copiar GIF** leva a imagem inteira.
- **`e8d82cb`:**
  - o campo de mensagem vira um editor Tiptap;
  - "mais emojis" na barra de reações rápidas.

## Documentos atualizados (marcados com 🆕 e o commit)
- **`03-inventario-web.md`:**
  - marcações em MSG-17 (substituído pela lista do relógio), ENV-13, ENV-14, MSG-19 e ENV-20;
  - novo ENV-23 (total: 137);
  - nova §12 com o resumo e o que muda para o Android;
  - itens 11 e 12 no índice.
- **`05-matriz-paridade.md`:**
  - nota de atualização;
  - coluna "Adaptação" de MSG-17, MSG-19, ENV-13, ENV-14 e ENV-20;
  - linha nova ENV-23.
- **`06-fila-de-correcoes.md`:**
  - nota no FC-514;
  - novos FC-519 (rascunho por conversa) e FC-520 (encaminhada de encaminhada).
- **`00-LEIAME.md`:** nota de atualização e HEADs.
- **`TODO.md`** (linhas 🆕):
  - 7.5: encaminhada de encaminhada;
  - 7.10: agendadas fora do chat, no lugar do selo feito em `f3cab38`;
  - 7.11: rascunho por conversa.
- O doc 01 (contrato) e o OpenAPI não mudam.

## Decisões
- **O 7.10 já estava marcado como feito** com o selo dentro do chat (`f3cab38`). A linha 🆕 troca esse modelo pelo novo do web; as linhas antigas ficam marcadas, como registro do que foi feito.
- **O Tiptap é um detalhe interno do web.** A decisão FC-416 do Android (fila de anexos separada do texto) continua valendo.

## Pendências
- Implementar as três linhas 🆕 (próximos commits).
