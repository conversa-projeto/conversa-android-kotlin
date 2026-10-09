# 2026-10-09 · 07 · Etapa 8 — Configurações: sistema e acessos

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 8.5 (tela com seções, permissões do usuário, Sistema, Acessos): o 8.5 fecha; FC-804, FC-808; CFG-07, CFG-08, AUT-09
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O web mostra duas abas administrativas só para quem tem a permissão. As permissões vêm de `GET /usuario/permissoes`; no modo aberto, todos têm todas.
- **Sistema** (`ConfiguracaoSistema.vue`, permissão `parametros`):
  - Firebase: ID do projeto, e-mail e a chave privada, que nunca é mostrada (em branco mantém a atual);
  - forçar relay pelo TURN; dias de gravação (0–36500);
  - transcritor (endereço e idioma);
  - o bucket só para leitura.
  - Salva **só o que mudou**: "Configurações salvas.".
- **Acessos** (`ConfiguracaoAcessos.vue`, permissão `permissoes`):
  - aviso de "modo aberto" e "O que cada permissão libera";
  - "Buscar usuário" e uma tabela usuário × permissão com "(você)";
  - a caixa só muda se o servidor aceitar; a recusa mostra o motivo;
  - depois de cada mudança, relê as próprias permissões.

As rotas e os DTOs já existiam na `ConversaApi`; nada os usava.

## O que foi feito
- **`core:model` — `Sistema.kt`:**
  - `CodigoPermissao`, `ParametrosSistema`, `AlteracaoParametros`, `Acessos`;
  - `alteracaoDosParametros`: só o que mudou, e a chave só se foi digitada; nulo = nada mudou;
  - `diasDeGravacao` (0–36500), `filtrarUsuarios`;
  - `comPermissao`: marcar "Acessos" para alguém encerra o modo aberto.
- **`core:data` — `SistemaRepositorio`:**
  - `minhasPermissoes`, presas ao usuário da sessão para outra conta não herdar as telas;
  - `parametros` / `alterarParametros`, `acessos`, `conceder` / `retirar`. Depois de conceder ou retirar, relê as minhas;
  - os mapeamentos de DTO.
  - No `PATCH`, o `ConversaJson` não manda nulos: vai só o que mudou. Uma URL vazia do transcritor vai como `""` e desliga o botão.
- **`:feature:config`:**
  - **Configurações:** a aba relê as minhas permissões a cada abertura. "Sistema" e "Acessos" só aparecem com `parametros` / `permissoes`.
  - **Sistema** (`SistemaTela`): os blocos e os textos do web.
    - Os campos ficam em estado local e recomeçam do que o servidor devolveu depois de salvar.
    - A chave privada só existe no campo, como senha: não vai para o estado salvo nem volta do servidor.
    - "Salvar" só acende com mudança, e com os dias válidos ("Use um número de 0 a 36500.").
  - **Acessos** (`AcessosTela`):
    - aviso do modo aberto, o que cada permissão libera e a busca;
    - no celular, a tabela vira uma linha por usuário, com uma caixa por permissão ("Sistema", "Acessos"). Para o TalkBack, cada caixa diz "Sistema para Teste Android C";
    - recusa do servidor: o motivo aparece e a caixa fica como estava.
  - **`Avisos.kt`:** o `Aviso` e o "erro do servidor como veio", que o Perfil já usava, agora servem às três telas.
- **`core:ui` — `Interruptor`:** com o switch desligado, o Material pinta o polegar e a borda com `outline` sobre o trilho `surfaceContainerHighest`. Nas cores do FMX os dois são `#C8C8C8`, e o polegar sumia. Agora eles usam `onSurfaceVariant`. As telas Chamadas e Sistema passaram a usar o `Interruptor`.
- **Testes:**
  - `SistemaTest`: só o que mudou, dias, busca, modo aberto;
  - `SistemaRepositorioTest`: as minhas presas ao usuário; o `PATCH` só com o que mudou, conferido também no JSON; acessos e reler depois de conceder;
  - `AcessosViewModelTest`: aceito marca; recusado mostra o motivo e não marca;
  - `ConfiguracoesViewModelTest`: só "Sistema" para quem só tem `parametros`.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (conta A).** O servidor de dev está em modo aberto. **Nada foi salvo nem marcado**: os parâmetros e as permissões do servidor de dev não foram alterados.
  - **Configurações:** "Sistema" e "Acessos" aparecem.
  - **Sistema:** os valores do servidor carregaram. "Salvar" fica apagado sem mudança e acende ao mexer no TURN; voltei o interruptor e saí sem salvar.
  - **Interruptor:** desligado, agora aparece.
  - **Acessos:** o aviso do modo aberto, as três contas com "(você)" na A, e a busca "c" deixou só a C.
- **Coberto só pelos testes unitários:** salvar e conceder ou retirar de verdade.

## Pendências
- **Ramal SIP:** a seção vem com o SIP (etapa 9).
- **Próximo:** o 8.6, anexos da conversa.
