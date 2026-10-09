# 2026-10-09 · 03 · Etapa 8 — perfil de outro usuário

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 8.4; FC-803; AUT-10
- **Branch:** `reescrita`
- **Commits:** `4d53ba8`

## Contexto
No web, o `UserInfoModal.vue` abre ao clicar no avatar de uma conversa **direta**, tanto na lista (botão "Ver perfil") quanto no cabeçalho. Ele mostra:
- a foto ou a inicial, e o nome;
- "Email" e "Telefone", com "Nao informado" quando vazios;
- o botão "Ver anexos".

Os dados vêm do contato (`GET /usuario/contatos`, que não traz foto) e a foto vem da conversa (`resolverUsuarioDaConversa`). Sem o contato, tudo vem da própria conversa.

O TODO pede também um "Ligar", que o web não tem nesse painel.

## O que foi feito
- **`core:model` — `Perfil.kt`:** `FichaUsuario` e `fichaDaConversa`, como o `resolverUsuarioDaConversa` do web:
  - só na direta;
  - dados do contato e a foto da conversa;
  - sem o contato, nome e foto da conversa;
  - e-mail e telefone em branco viram nulo.
- **`core:ui` — `FolhaPerfilUsuario`:**
  - bottom sheet com a foto ou a inicial (120 dp), o nome e um quadro com "E-mail" e "Telefone" ("Não informado", com acento, quando vazios);
  - "Ligar" e "Ver anexos" só aparecem quando a tela passa a ação;
  - a folha usa a superfície branca, como a lista: no fundo padrão (#F5F5F5) o círculo da inicial sumia.
- **Lista de conversas:**
  - `ItemConversa.ficha`: o avatar da direta abre a folha (ripple redondo sem recorte, para não cortar a bolinha de online);
  - para o TalkBack, há a ação "Ver perfil" na linha;
  - o "Ligar" vem do `app`: `rememberLigarParaUsuario`, novo em `feature:chamada`. Ele pede o microfone como os botões da conversa e liga com voz para a pessoa e para mim (`LigarViewModel.ligarParaUsuario`).
- **Cabeçalho do chat:**
  - na direta, avatar e nome abrem a folha ("Ver perfil"); no grupo, continuam abrindo os membros;
  - o "Ligar" usa a mesma ação do botão de voz.
- **Perfil (8.3):** o avatar grande ganhou uma borda fina, porque o círculo da inicial também sumia no fundo da tela.
- **Testes:**
  - `PerfilTest`: perfil vindo do contato com a foto da conversa; vindo da conversa sem o contato; grupo sem perfil;
  - `LigarViewModelTest`: voz para a pessoa e para mim, na direta.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (conta A):**
  - **Lista:** o toque no avatar de "Teste Android C" abriu a folha com o e-mail, "Não informado" no telefone e "Ligar". O toque no resto da linha continua abrindo a conversa.
  - **Cabeçalho:** o toque no nome de C abriu a mesma folha.
  - **Ligar:** foi testado pelos dois caminhos, com uma chamada do próprio app entre as contas de teste. A tela "Chamando…" abriu com C e eu desliguei; a conversa registrou "Chamada de áudio · Cancelada".

## Pendências
- **"Ver anexos":** entra com a tela de anexos da conversa (8.6). A folha já aceita a ação.
