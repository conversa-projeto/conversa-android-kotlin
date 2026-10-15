# 2026-10-15 · 04 · Sincronização: confirmação com lista, chat novo por ligação, temas do web

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** linhas 🆕 no `TODO.md`: 2.6, 2.10, 3.5, 3.8, 4.4, 4.11, 7.1, 7.13, 8.1, 8.2 e 8.7; FC-219, FC-420, FC-525, FC-526 e notas em FC-217, 317, 521, 524, 725 e 727; S11, S20 e S25
- **Branch:** `reescrita`
- **Commits:** (preencher)
- **Commits de origem** (pull `--ff-only` em 2026-10-15; 2 commits atrás em cada repositório):
  - **Servidor (`12cf0cf` → `e25c4db`):**
    - `6d20ff7` "Confirmação de leitura com lista e prazo, chat novo por ligação e pesquisa nos chats das ligações" (migração 043);
    - `e25c4db` "Atividade de resposta ou menção já lida no chat deixa de contar como nova".
  - **Web (`d202c06` → `8b43965`):**
    - `e33ecba` "Confirmação de leitura com lista, código no campo, postagem na largura do chat e ajustes";
    - `8b43965` "Temas prontos, balões com cores próprias, seletor de emoji rápido e ajustes".
  - **Pastas:** o `conversa-android-fmx` e o `conversa-operador` não existem mais na pasta do projeto.

## Contexto
O pedido foi de novo "faça os pulls e atualize o projeto", junto com a construção das pendências.

O ambiente de dev foi religado:
- **Docker:** a pessoa iniciou o Docker Desktop.
- **Servidor:** a API aplicou as migrações até a 43.
- **Web:** o container `dev` e o Vite voltaram.
- **App:** o proxy e o emulador voltaram.

## O que mudou (resumo)
- **Servidor: 81 → 82 rotas.**
  - `POST /mensagem/pedir-confirmacao`: só quem enviou, até 24 h depois do envio, com uma lista opcional de quem confirma; manda o WS 65 (`ConfirmacaoPedida`).
  - `PUT /mensagem` aceita `confirmacao_usuarios`.
  - O objeto `confirmacao` ganha `pode_confirmar` e `destinatarios`.
  - `POST /mensagem/confirmar` responde 403 para quem está fora da lista.
  - **O chat da ligação voltou a ser um grupo novo**, com o nome dos participantes e sem o prefixo "Chamada: ". Só reaproveita a conversa dentro do chat de outra ligação. Isso desfaz o `a40fd47`.
  - Áudio que ganha vídeo passa a contar como chamada de vídeo.
  - A pesquisa na conversa traz também os chats das ligações feitas nela.
  - Uma resposta ou menção já lida deixa de contar como atividade nova (WS 61 ao ler).
- **Web: 150 → 153 funcionalidades.**
  - ENV-27: pedir a confirmação de leitura depois do envio;
  - ENV-28: código como peça no campo;
  - CFG-10: temas prontos e cores dos balões (➖ no Android, que usa as cores do FMX).
  - Mais a confirmação com lista, a postagem na largura do chat (respondida pelo campo), o grupo com imagem ou emoji na criação e o ícone "Conversa em grupo".

## Documentos atualizados
- **`01`, `02` e `08`** (por um agente, conferindo no código):
  - rota 82, WS 65, migração 043;
  - §9.12 reescrita ("revisto em `6d20ff7`"), `tipo` 2 ao ligar o vídeo;
  - pesquisa nos chats das ligações; atividades;
  - pegadinhas 66–71;
  - S20 quase toda resolvida; nota na S11;
  - **S25 nova**, com as arestas da confirmação com lista: o WS 65 de uma agendada sai antes dela; `pode_confirmar` vem `true` em mensagem oculta; quem sai da conversa continua na lista.
- **`03`** (por um agente, conferindo os textos no web):
  - ENV-27, ENV-28 e CFG-10;
  - os IDs alterados;
  - §2.4, §4.1, §4.2, §6 e §7;
  - a **§14 nova**, com o que muda para o Android.
- **`05`:**
  - 3 linhas novas e as notas de PES-01, CON-01, CON-07, MSG-21, ENV-24, MSG-22, CHA-18, CHA-26, ANX-04 e ATV-01;
  - ENV-04 volta a ✅ (Noto, `a7a6c8a`);
  - CHA-18 volta a ✅, porque o servidor desfez o reaproveitamento;
  - placares recalculados: nova base com **114 ✅, 9 🟡, 3 ⛔, 23 ⬜, 4 ➖** de 153.
- **`06`:**
  - FC-219 (ícone de grupo), FC-420 (zoom na pinça), FC-525 (pedir confirmação depois) e FC-526 (código como peça, que depende da FC-416);
  - notas em FC-217, 317, 521, 524, 725 e 727.
- **`00`:** nota, HEADs e totais.
- **`docs/contrato/openapi.json`:** baixado de novo (`e25c4db`), 82 → 83 entradas.
  - Entrou o `POST /api/mensagem/pedir-confirmacao`.
  - Os corpos de `/login`, `/dispositivo`, `/mensagem/visualizar` e `/mensagem/reproduzir` passaram a sair com `additionalProperties: false`. Isso vem do gerador do OpenAPI, e não de mudança no `esquemas.ts`.
  - O app não manda campos a mais, e login, dispositivo e lidas funcionaram hoje no emulador.
- **`TODO.md`:** linhas 🆕 nas seções listadas no cabeçalho.

## Achados no Android (registrados no TODO)
- **Pesquisa:** `MensagensRepositorio.pesquisar` filtra os resultados por `conversaId`, então jogaria fora os dos chats das ligações. Além disso, `abrirResultado` usa a conversa aberta, e não a do resultado (8.2, FC-524).
- **Teste do chat da chamada:** a fixture do `ChatViewModelTest` ainda usa o nome antigo "Chamada: Ana, Bruno" (8.7).

## Como foi verificado
- `git log`/`git diff` dos dois commits de cada repositório; pull `--ff-only`.
- OpenAPI novo comparado com o salvo por script.
- Os placares do doc 05 foram recalculados a partir das tabelas.

## Decisões
- **CFG-10 (temas) fica ➖:** o Android segue as cores do FMX.
- **ENV-28 fica ⛔ junto com a FC-416:** a peça de código depende da decisão do campo rico.

## Pendências
- As do TODO. O 8.7 continua primeiro.
