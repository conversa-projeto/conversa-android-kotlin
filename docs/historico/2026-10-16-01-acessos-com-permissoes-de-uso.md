# 2026-10-16 · 01 · Acessos com as permissões de uso

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.5 (linha 🆕 `ef13c22`/`9c6e8a6` dos Acessos); FC-813; CFG-08
- **Branch:** `reescrita`
- **Commits:** `91d5681`

## Contexto
- **O servidor** (`ef13c22`) passou de 2 para **32 permissões**: 6 de administração e 26 de uso, marcadas com `uso` no `GET /permissoes`.
- **O web** (`9c6e8a6`) refez a tela de Acessos: uma lista de pessoas que se abre, com as caixas em grupos e nomes curtos (`utils/permissoesUso.ts`). As próprias permissões marcadas ficam travadas.
- **No app,** cada pessoa tinha uma `Row` sem rolagem com uma caixa por permissão. Com 32 a linha passava da tela, e as de uso apareciam com o código cru (ex.: `chamada_audio`). Achado na sincronização (histórico 2026-10-15-26).

## O que foi feito
- **`core:model` (`Sistema.kt`):**
  - `PermissaoSistema.uso` e os 30 códigos novos em `CodigoPermissao`;
  - `GrupoPermissao` e `gruposDePermissoes`, na ordem do web: Administração (as que não são de uso), Enviar, Mensagens, Grupos, Chamadas, Configurações e, para uma de uso que o app não conhece, Outras;
  - `quantasDe` ("N de M"), `temAdministracao` (o selo) e `propriaMarcada` (a caixa travada).
- **`PermissaoDto.uso`** e o mapeamento no `SistemaRepositorio`.
- **Tela de Acessos:**
  - **No topo:** o aviso do modo aberto, o texto do web ("Abra uma pessoa para ver e mudar o que ela pode fazer…") no lugar da lista "O que cada permissão libera", e a busca;
  - **Cada pessoa:** uma linha que se abre (uma de cada vez), com o nome, o login, o selo "Administração", "N de M" e a seta;
  - **Aberta:** os grupos, com os nomes curtos do web e caixas de 48 dp de altura. As próprias, marcadas, ficam travadas, e o leitor de tela diz "Você não pode retirar as próprias permissões".
- **Testes:**
  - os grupos e a ordem (com Outras para a desconhecida);
  - "N de M", o selo e as caixas travadas.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (524 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor `ef13c22`:**
  - Configurações › Acessos mostrou as três contas com "26 de 32", no modo aberto;
  - aberta a linha do A, apareceram "ADMINISTRAÇÃO" (Sistema, Acessos, Bots, Usuários, Ferramentas, Conhecimento, livres) e "ENVIAR" (Emojis no texto, Figurinhas…), com as próprias marcadas travadas.
  - Nenhuma permissão foi alterada no teste, porque mudaria os dados do servidor. O caminho de marcar e desmarcar não mudou e está no `AcessosViewModelTest`.

## Decisões
- **Sem o fundo próprio da área aberta** (o web usa `surface-50`): o app não tem esse token, e os títulos dos grupos bastam para separar.
