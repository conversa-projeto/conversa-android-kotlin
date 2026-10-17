# 2026-10-17 · 01 · Sincronização: pastas de mensagens, localização, espaço usado e contatos pelo WebSocket

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** linhas 🆕 no `TODO.md` (2.6, 2.9, 4.5, 8.3, 8.4, 8.5, 8.6) e as seções novas 4.12 (localização) e 7.14 (pastas); FC-222, FC-223, FC-224, FC-318, FC-319, FC-422, FC-423, FC-424, FC-527, FC-528 e FC-816, e nota na FC-812; S31–S39
- **Branch:** `reescrita`
- **Commits:** `1e7ab54`
- **Commits de origem** (pull `--ff-only` em 2026-10-17, de madrugada; um commit em cada repositório):
  - **Servidor (`ef13c22` → `ac710eb`):** "Pastas de mensagens, localização e trajeto, espaço usado e ajustes de IA e acessos" (migrações 053–056).
  - **Web (`9c6e8a6` → `f351190`):** "Pastas de mensagens, localização e trajeto, espaço usado e menu das Configurações".

## Contexto
O pedido foi "faça um pull e atualize a documentação e o desenvolvimento". Havia um commit em cada repositório.

O ambiente de dev:
- **API:** o contêiner `api` recarregou sozinho com o código novo e aplicou as migrações até a 56 ("Migrações aplicadas até a versão 56"). As contas de teste continuaram entrando, e a migração 055 deu `localizacao` a todas as pessoas.
- **Web:** ganhou o Leaflet (`leaflet@1.9.4`). O contêiner `dev` não estava rodando e não foi preciso para o sincronismo.
- **O `conversa-web` tem um `yarn.lock` não versionado** que não é deste trabalho. O pull não mexeu nele.

## O que mudou (resumo)
- **Servidor: 109 → 119 rotas.**
  - **Pastas de mensagens** (só na conversa direta, migração 056): `GET /pastas`, `PUT`/`PATCH`/`DELETE /pasta`, `GET`/`PUT`/`DELETE /pasta/mensagens`. O `DELETE /pasta/mensagens` leva **corpo JSON**. Cada mensagem traz `pastas` (ids), só quando não é vazia. Aviso pelo WS 67.
  - **Localização e trajeto:** conteúdo **tipo 9**, um JSON dentro do `conteudo` (`pontos` 1 ou 2, `rota`, `distancia`, `duracao`), conferido pelo servidor ("Localização inválida!"). Permissão de uso nova `localizacao` (migração 055).
  - **Espaço usado:** `GET /usuario/espaco` → `{bytes}`; em `GET /permissoes`, cada pessoa ganha `criado_em`, `bot` e `espaco`.
  - **Contatos:** `criado_em` em `GET /usuario/contatos` e o **WS 66** quando uma conta é criada, renomeada ou excluída.
  - **Permissões:** `usuarios` foi juntada a `permissoes` (migração 054); continuam 32 códigos (5 de administração e 27 de uso).
  - **IA:** servidor de embeddings próprio (migração 053) e `POST /parametros/ia/testar-embeddings`; `GET /ferramentas/embutidas`. O `conhecimento` do `/recursos` passou a depender do servidor de embeddings.
- **Web: 163 → 172 funcionalidades.**
  - **Valem para o celular:** AUT-11 ("Usuário desde"), CON-17 (pastas), MSG-23 (marcas das pastas na bolha), MSG-24 (pasta aberta), MSG-25 (localização na conversa), ENV-29 (selecionar e pôr na pasta), ENV-30 (enviar localização), ANX-17 (espaço usado) e ANX-18 (velocidade do áudio).
  - **Mudaram:** as configurações viraram menus com sub-abas (CFG-01), Pessoas › Usuários numa tela só (CFG-08, CFG-11), a página de Anexos abre com os de todas as conversas (ANX-13), "Gravando áudio…" na lista (CON-01) e os contatos relidos pelo WS 66 e na reconexão (CON-11).

## Documentos atualizados
- **`01`, `02` e `08`** (por um agente, conferindo no código do servidor):
  - **No `01`:**
    - 119 rotas, conferidas por script contra o `rotas.ts` (110–119 novas);
    - as seções novas §8.7 (espaço usado), §10.16 (localização), §10.17 (pastas), §13.10 (servidor de embeddings) e §13.11 (ferramentas embutidas);
    - §13.1 (permissões depois da junção) e §13.3 (recursos) reescritas;
    - WS 66 e 67 em §6;
    - as migrações 053–056;
    - as pegadinhas 88–105.
  - **No `02`:** a linha do tempo (linha 35) e as rotas.
  - **No `08`:** notas em S4, S7, S10, S12, S16, S27 e S28, e as **pendências novas S31–S39**:
    - **S31:** a contagem da pasta inclui ocultas e agendadas alheias, e ocultar não avisa as pastas;
    - **S32:** WS 67 sem dizer o que mudou;
    - **S33:** `DELETE` com corpo JSON;
    - **S34:** WS 66 dentro da transação;
    - **S35:** a migração 054 amplia acessos sem aviso e pode encerrar o modo aberto;
    - **S36:** a localização depende do OSM, do Nominatim e do OSRM públicos;
    - **S37:** validação da localização (`null` recusado, erro sem o campo, rota não conferida);
    - **S38:** espaço usado conta uploads não concluídos;
    - **S39:** os testes de IA e de embeddings mandam o token salvo para o endereço digitado.
- **`03`** (por um agente, conferindo os textos no web): os 9 IDs novos, os alterados (AUT-02/05/06/09/10, CON-01/09/11, MSG-07/08, ENV-12/20/21, ANX-10/13, CHA-18, NOT-02, ATV-01, CFG-01/02/05/07/08/10–14, GER-08/09), §2.5 (menus com sub-abas), §4.1 (rotas e os serviços externos com as URLs exatas), §4.2, §6, §7 e a **§16 nova**, com o que muda para o Android.
- **`05`:**
  - as 9 linhas novas, todas ⬜;
  - **voltam a 🟡:** CON-01 ("Gravando áudio…" na lista), CON-11 (WS 66 e reconexão), ANX-13 (anexos de todas as conversas) e CFG-08 (Pessoas, `localizacao`, espaço, "Usuário desde", selo "Bot");
  - notas em CFG-01, CFG-07, GER-09, ANX-10 e AUT-10;
  - o placar recontado das tabelas: **135 ✅, 6 🟡, 3 ⛔, 22 ⬜, 6 ➖** de 172.
- **`06`:** FC-222 (contatos pelo WS 66), FC-223 (pastas), FC-224 (gravando na lista), FC-318 (pasta aberta e marcas), FC-319 (localização na conversa), FC-422 (velocidade), FC-423 (espaço usado), FC-424 (anexos de todas as conversas), FC-527 (selecionar e pôr na pasta), FC-528 (enviar localização), FC-816 (permissões e Acessos) e uma nota na FC-812 (servidor de embeddings).
- **`00`:** nota, HEADs e totais.
- **`docs/contrato/openapi.json`:** baixado de `https://localhost/api/docs/json` (`ac710eb`), 110 → 120 entradas. Entraram as 10 rotas novas.
  - O `PUT /api/usuario/novo` ganhou `permissoes` (lista).
  - O `PATCH /api/parametros` ganhou `ia_embeddings_url` e `ia_embeddings_token`.
  - O corpo do `POST /api/login` perdeu o `additionalProperties: false`, efeito do gerador e não do `esquemas.ts`.
- **`TODO.md`:** linhas 🆕 nas seções 2.6, 2.9, 4.5, 8.3, 8.4, 8.5 e 8.6, e as seções novas 4.12 (localização) e 7.14 (pastas).

## Achados no Android (antes deste sincronismo)
- **Nada quebrou:** o app ignora as chaves novas (`ignoreUnknownKeys`) e os WS 66 e 67 caem em `Desconhecido`.
- **A localização aparece como conteúdo desconhecido** ("•" e o aviso).
- **`CodigoPermissao.USUARIOS`** ficou morto, e a `localizacao` cairia no grupo "Outras" dos Acessos.
- **Os contatos não são relidos** quando outra conta muda, nem na reconexão.

## Como foi verificado
- `git log`/`git show` dos dois commits; pull `--ff-only`.
- **A API depois do pull:** "Migrações aplicadas até a versão 56", base `conversa_dev`, `GET /cadastro` → `{"aberto":true}`; no banco, `usuarios` sumiu e `localizacao` entrou como permissão de uso.
- O OpenAPI novo comparado com o salvo por script (entradas novas, removidas e alteradas).
- Os placares do doc 05 recontados das tabelas por script.

## Decisões
- **Selecionar mensagens no celular:** o web só seleciona com o mouse. No app, "Selecionar" vai no menu do toque longo, sem tirar o menu de ações (FC-527).
- **"Inserir na mensagem" da localização** depende da decisão do campo rico (FC-416); até lá, só "Enviar".
- **Anexos de todas as conversas (FC-424)** fica para avaliar onde entra: o web tem a aba Anexos na barra, e o app não tem essa aba.
- **Configurações em menus com sub-abas (CFG-01):** o app mantém as seções dele; só os nomes e o que cada uma mostra seguem o web.

## Pendências
- As FC novas, a começar pelas que o app já pode fazer sem decisão: FC-222, FC-224, FC-422, FC-423, FC-816, depois FC-319/FC-528 (localização) e FC-223/FC-318/FC-527 (pastas).
