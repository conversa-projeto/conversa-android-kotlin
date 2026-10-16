# 2026-10-15 · 26 · Sincronização: bots de IA, permissões de uso, cadastro e atividades pendentes

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** linhas 🆕 no `TODO.md`: 2.4, 2.9, 4.6, 8.5 e a seção nova 8.9; FC-220, FC-221, FC-421, FC-813, FC-814 e FC-815, e nota na FC-800; S26–S30
- **Branch:** `reescrita`
- **Commits:** (preencher)
- **Commits de origem** (pull `--ff-only` em 2026-10-15, à noite; um commit em cada repositório):
  - **Servidor (`e25c4db` → `ef13c22`):** "Bots de IA com ferramentas e base de conhecimento, permissões de uso e cadastro pelo administrador" (migrações 044–052).
  - **Web (`8b43965` → `9c6e8a6`):** o mesmo título, com as telas disso, o visualizador do Office (`@silurus/ooxml`) e o player de vídeo na bolha.

## Contexto
O pedido foi "veja se tem pull para fazer nos repositórios base; se tiver, pode fazer e atualizar o projeto". Mais cedo no mesmo dia não havia nada; à noite havia um commit em cada repositório.

O ambiente de dev:
- **API:** o contêiner `api` lê o `src` e as `migracoes` da pasta e recarrega sozinho. Depois do pull, aplicou as migrações até a 52, e as contas de teste continuaram entrando: as migrações dão todas as permissões de uso a quem já tinha conta.
- **Web:** a dependência nova (`@silurus/ooxml`) pediu o `bun install` no contêiner `dev`, como o `iniciar-desenvolvimento.bat` faz, e o Vite foi reiniciado.
- **Docker:** o `docker-compose.yml` passou a chamar os volumes de `conversa-postgres` e `conversa-minio` (eram `pgdata` e `minio`) e ganhou o Qdrant. Os contêineres que estão rodando continuam nos volumes antigos.
  - Um `docker compose up` com o arquivo novo sobe o banco vazio, a não ser que antes se rode o `bin\renomear-volumes.bat`, que copia os dados, confere e **apaga** os volumes antigos.
  - Não foi rodado: fica para a pessoa decidir.

## O que mudou (resumo)
- **Servidor: 82 → 109 rotas.**
  - **Cadastro:** `GET /cadastro` (público, `{aberto}`) e `PUT /usuario/novo` (administrador).
  - **Bots de IA:** 7 rotas. O bot é um usuário comum, que responde na direta, quando mencionado e quando respondem a ele, com as ferramentas do chat e as permissões dele.
  - **Ferramentas externas:** 6 rotas.
  - **Base de conhecimento:** 10 rotas, com embeddings no servidor de IA e busca no Qdrant.
  - **Atividades:** `POST /atividade/concluir` e `POST /atividades/reacoes-vistas`. A atividade fica **pendente até concluir**: `nova` vira `pendente`, o contador passa a ser o de pendentes e `POST /atividades/vistas` não zera mais. Entra o tipo 5 (pedido de confirmação).
  - **Permissões de uso por pessoa:** 26 códigos, conferidos em cada rota. Sem a permissão, o servidor responde 403 "Você não tem permissão para: …".
  - **Áudio:** `duracao` e `intensidade` no `PUT /anexo` e nos conteúdos (a tabela `anexo_transcricao` virou `anexo_audio`).
  - **WebSocket:** nenhum tipo novo. O WS 4 também vem dos bots, e o WS 61 sai em mais situações.
- **Web: 153 → 163 funcionalidades.**
  - **Valem para o celular:** CON-16 (conversa com bot), ATV-04 (concluir à mão), ATV-05 (reação vista conclui) e GER-09 (permissões de uso).
  - **Administração:** CFG-11 a CFG-14 (Usuários, Bots, Ferramentas, Conhecimento).
  - **➖ no Android:** ANX-16 (visualizador do Office; o celular abre com outro app) e GER-10 (ocultar a barra no computador).

## Documentos atualizados
- **`01`, `02` e `08`** (por um agente, conferindo no código):
  - **No `01`:**
    - 109 rotas, conferidas por script contra o `rotas.ts`;
    - as seções novas §2.6, §5.8, §13.7 (bots), §13.8 (ferramentas) e §13.9 (base de conhecimento);
    - a §12 (atividades) e a §13.1 (as 26 permissões de uso) reescritas;
    - as migrações 044–052;
    - as pegadinhas 72–87.
  - **No `02`:** a linha do tempo e as rotas.
  - **No `08`:** notas em S4, S7, S10, S11, S12, S19 e S25, e as **pendências novas S26–S30**:
    - **S26:** `nova` → `pendente` sem compatibilidade;
    - **S27:** os bots não marcam `recebida`, entram na lista de confirmação e podem ser chamados numa ligação;
    - **S28:** arestas das permissões; encaminhar dribla as outras;
    - **S29:** pela URL de uma ferramenta, o servidor pode chamar a rede interna;
    - **S30:** o nginx corta o corpo em 1 MB, e a indexação não avisa quando termina.
- **`03`** (por um agente, conferindo os textos no web): os 10 IDs novos, os alterados (ATV-01/02, AUT-01/02/06–09, CON-07/09/11, ENV-*, ANX-04/05/10/11/13, CHA-*, SIP-01, CFG-*, GER-08), §6, §7 e a **§15 nova**, com o que muda para o Android.
- **`05`:**
  - as 10 linhas novas;
  - **ATV-04 e ATV-05 ✅** (`2219208`);
  - **voltam a 🟡:** AUT-02 (falta o `GET /cadastro`), ANX-10 e ANX-11 (duração e onda) e CFG-08 (a tela de Acessos com 32 permissões);
  - o placar recontado das tabelas: **130 ✅, 7 🟡, 3 ⛔, 17 ⬜, 6 ➖** de 163.
- **`06`:** FC-220 (cadastro fechado), FC-221 (bots), FC-421 (duração e onda), FC-813 (Acessos), FC-814 (permissões de uso), FC-815 (administração, a avaliar) e uma nota na FC-800.
- **`00`:** nota, HEADs e totais.
- **`docs/contrato/openapi.json`:** baixado de `https://localhost/api/docs/json` (`ef13c22`), 83 → 110 entradas. Entraram as 27 rotas novas.
  - O `PUT /api/anexo` ganhou `duracao` e `intensidade`.
  - Os corpos de `/dispositivo`, `/mensagem`, `/mensagem/visualizar` e `/mensagem/reproduzir` perderam o `additionalProperties: false`, efeito do gerador e não do `esquemas.ts`.
- **`TODO.md`:** linhas 🆕 nas seções do cabeçalho e a seção 8.9.

## Achados no Android
- **Atividades:** o app lia `nova`, que não vem mais, e zerava o contador ao abrir a aba, mas o contador voltava no aviso seguinte. Corrigido em seguida, no `2219208` (histórico 2026-10-15-27).
- **Acessos:** com 32 permissões, a linha de caixas de cada pessoa passa da tela, e as permissões de uso aparecem com o código cru (FC-813).
- **Permissões de uso:** sem elas, o envio marca "falhou" sem dizer o motivo, e "Salvar" no perfil manda nome e e-mail juntos (FC-814).

## Como foi verificado
- `git log`/`git diff` dos dois commits; pull `--ff-only`.
- **A API depois do pull:** "Migrações aplicadas até a versão 52", login das contas de teste e `GET /cadastro` → `{"aberto":true}`.
- O OpenAPI novo comparado com o salvo por script.
- Os placares do doc 05 recontados das tabelas.

## Decisões
- **ANX-16 (Office) e GER-10 (barra do computador) ficam ➖.**
- **A administração de bots, ferramentas, conhecimento e usuários (CFG-11…14) fica para avaliar junto da FC-812**, que é a mesma dúvida: se a administração vai para o celular.

## Pendências
- **Os volumes do Docker:** rodar ou não o `renomear-volumes.bat` é decisão da pessoa.
- **A FC-813** (a tela de Acessos) vem em seguida; as outras ficam no TODO.
