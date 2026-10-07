# 2026-10-07 · 05 · Etapa 2 — conversas, grupos e teste de ponta a ponta no emulador

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2); também Fundação (testes 1.11 e 1.12) e Planejamento (pendências S15, S16)
- **Tipo:** código / testes / documentação / configuração de debug
- **Itens:** `TODO.md` 2.6–2.11 (FC-204…FC-214); CON-01…08, CON-11, CON-13, PRE-01, GER-02
- **Branch:** `reescrita`
- **Commits:** `0d595af`

## Contexto
Último bloco da etapa 2. O usuário ligou o servidor de desenvolvimento e pediu para seguir sozinho. Por isso, além do código, o app foi testado de ponta a ponta no emulador contra o servidor real.

## O que foi feito

### `:feature:conversas` (módulo novo)
- **Lista** (`lista/ConversasTela.kt`, `ConversasViewModel.kt`):
  - itens: avatar com bolinha de online, título, etiquetas "Grupo"/"Arquivada", alfinete, hora, prévia, contador de não lidas e três pontinhos de "digitando";
  - busca sem acento (com termo, entram as arquivadas); seção recolhível "Arquivadas (N)" e seção "Nova conversa";
  - pull-to-refresh; carregando só até a primeira carga da sessão (`IniciadorSessao.carregada`), vazio e erro em tela cheia só com cache vazio;
  - toque longo abre o menu: Fixar/Desafixar, Mover para cima/baixo, Arquivar/Desarquivar, Membros do grupo. Há também uma ação de acessibilidade "Mais opções".
- **Nova conversa** (`novaconversa/NovaConversa.kt`): contatos com filtro e "Novo grupo"; tocar abre (ou cria) a conversa direta.
- **Criar grupo** (`grupo/CriarGrupo.kt`): textos e validações do web; os marcados continuam visíveis fora do filtro.
- **Membros** (`membros/Membros.kt`): renomear, adicionar (bottom sheet) e sair (confirmação). Não há "remover outro" (S15).

### `:app`
- Chat provisório (`chat/ChatProvisorio.kt`): cabeçalho com avatar, online, digitando e "Membros do grupo". As mensagens são da etapa 3.
- Navegação: Nova conversa → Chat (substitui a tela), Criar grupo → Chat, Sair do grupo → lista.
- **Debug:** `app/src/debug/res/xml/network_security_config.xml` aceita HTTP **só** para `localhost`/`127.0.0.1`, para o emulador usar a API sem TLS via `adb reverse`. O release não muda.

### Correções achadas no teste no emulador
- **Faixa "sem conexão" ficava ligada com o socket conectado.**
  - Causa: o `launch` do aviso de 5 s estava dentro de um `collectLatest`, mas pertencia ao escopo de fora e não era cancelado.
  - Correção: `coroutineScope { … }`.
  - Teste novo `ConexaoTempoRealTest`. Ele falhou sem a correção e passou com ela.
  - Ficou estável depois de injetar o dispatcher de E/S (`@DespachanteEs`); antes dependia de sorte.
- **Cursor pulando ao digitar** ("Grupo de teste" virava "Grupde testeo").
  - Causa: o texto do campo passava pelo `combine`/`stateIn` do ViewModel e chegava um quadro atrasado.
  - Correção: o texto do campo fica em estado local nos 4 campos (busca, filtros, nome do grupo, renomear).
- **Teclado aberto sobre a lista depois do login:** o login agora limpa o foco e esconde o teclado.
- **Foco inicial do login:** vai para a senha quando o usuário já vem preenchido, decidido uma vez só. A primeira versão jogava o foco embora na primeira letra digitada.
- **Avatar sumia na lista** (fundo `#F5F5F5` sobre fundo `#F5F5F5`): a lista fica sobre a superfície branca, como no FMX.
- **Faixa cinza acima da barra inferior:** o `Scaffold` interno não repete o espaço do sistema.
- **Lint:**
  - `<debug-overrides>` duplicado entre os dois XMLs;
  - textos do menu marcados como "não usados" por causa de uma função local; reescrito como `@Composable` comum.

### Documentação
- `docs/desenvolvimento/emulador.md`: como testar no emulador sem mexer na segurança do aparelho, e as contas de teste do banco local (`teste.android.a` / `teste.android.b`).
- Doc 08: **S15** (só dá para sair do grupo, não remover outra pessoa) e **S16** (prévia sem o tipo do conteúdo).
- `CLAUDE.md`: módulos e onde está o guia do emulador e o OpenAPI.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
  - **137 testes, 0 falhas.** Novos: `ConexaoTempoRealTest` (3), `ConversasViewModelTest` (7), `CriarGrupoViewModelTest` (3).
  - `:core:data` rodado 3 vezes seguidas sem falha.
  - Lint: 0 apontamentos.
- **Emulador** (`Medium_Phone_API_36.1`) contra o servidor de dev (`http://localhost:8080` via `adb reverse`):
  - **Servidor:** "Testar conexão" → "Conexão OK".
  - **Cadastro e login:** cadastro das contas A e B, com "Conta criada com sucesso!" e o usuário preenchido. "Usuário não encontrado!" aparece como veio do servidor; o login funciona.
  - **Dispositivo:** a linha `dispositivo` no banco tem modelo, versão e plataforma certos.
  - **Conversa direta:** criada com os 2 membros (conferido no banco); a seção "Nova conversa" esvazia.
  - **Grupo:**
    - validações;
    - criação;
    - renomear (conferido no banco);
    - lista de membros com "(Você)".
  - **Fixar, mover e arquivar:** conferidos no banco (`fixada_ordem`, `arquivada_em`); arquivar desfixa e reordena.
  - **Online e "digitando":** testados com um 2º cliente (Node, conta B); acendem e apagam.
  - **WS 40:** grupo criado pela conta B apareceu sozinho na lista de A.
  - **Sair e entrar de novo:** sair → login com o usuário preenchido, `token_fcm` nulo. Ao entrar de novo, a lista volta do servidor.
  - **Sessão:** `am force-stop` e reabrir → entra direto.
  - **Queda da API** (`docker stop api`): tentativas em 2, 4 e 8 s; faixa depois de 5 s; reconectou sozinho e a faixa sumiu.

## Decisões
- **Não instalar a CA do mkcert no emulador:** altera a segurança do aparelho. No debug, HTTP só para `localhost`; o teste com mkcert fica para a pessoa (1.7 ⛔).
- **"Remover participante" não é oferecido** (S15): o servidor sempre recusa.
- **Reordenar fixadas por "Mover para cima/baixo"**; arrastar fica para depois.
- **Prévia vazia com mensagem = "Anexo ou chamada"** até o servidor mandar o tipo (S16).

## Pendências
- Itens ⛔ da etapa 2:
  - notificações (etapa 5): logout sem notificação, arquivada sem som, cancelar a notificação ao arquivar;
  - comparação lado a lado com o web, que não está rodando aqui;
  - "remover participante" (S15).
- "Ao enviar e ao ler" a lista atualiza: entra com as mensagens (etapa 3).
- Teste com mkcert num aparelho (1.7) e Wi-Fi caindo de verdade num aparelho (o teste foi derrubando a API).
