# 2026-10-07 · 04 · Etapa 2 — login, cadastro, navegação e saída

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 2.1–2.5 (FC-200…FC-203); AUT-01…05, GER-02
- **Branch:** `reescrita`
- **Commits:** `7978455`

## Contexto
Segundo bloco da etapa 2: as telas de entrada e a estrutura de navegação, em cima da camada de dados do bloco anterior (`2026-10-07-03`). A lista de conversas ainda é um espaço reservado; ela entra no próximo bloco.

## O que foi feito

### `:feature:auth`
- **Login** (`login/LoginTela.kt`, `LoginViewModel.kt`):
  - textos do web ("Usuário", "Senha", "Entrar"/"Entrando…", "Não tem conta? Criar conta");
  - mostrar/esconder a senha e autofill (`ContentType.Username`/`Password`) para gerenciadores de senha;
  - campo preenchido com o último login digitado, ou com o usuário que acabou de se cadastrar;
  - mostra o servidor atual, com o atalho "Trocar";
  - erros: mensagem do servidor como veio ("Senha incorreta!"), "Muitas tentativas…", "Resposta de login inválida: token ausente.";
  - avisos ao abrir: "Sua sessão expirou. Entre novamente." e "Conta criada com sucesso!".
  - A senha some do estado depois do login.
- **Cadastro** (`cadastro/CadastroTela.kt`, `CadastroViewModel.kt`): "Crie sua conta" com Nome, Usuário, E-mail e Senha.
  - Campos param de aceitar texto nos limites das colunas (100/50/100).
  - Validações: todos preenchidos, e-mail válido, senha ≥ 4.
  - "E-mail já cadastrado." para o 500 de e-mail repetido; "Login já cadastrado!" vem do servidor.
  - Sucesso → login com o usuário preenchido.

### `:core:ui`
- `TextoUi` (texto de `strings.xml` ou texto do servidor).
- `Avatar(online = true)` com a bolinha verde.
- `IndicadorDigitando` (três pontinhos), `FaixaSemConexao` (texto do web + "Tentar agora").
- `textoDaPrevia`, `textoDoRotulo`.

### `:app`
- `navegacao/Rotas.kt`: rotas tipadas (Servidor, Login, Cadastro, Principal, Chat, NovaConversa, CriarGrupo, Membros).
- `MainViewModel`:
  - primeira tela: servidor → login → principal;
  - leva ao login quando a sessão acaba (com o aviso certo);
  - lê links `conversa://chat/{id}?mensagem={id}`; sem sessão, guarda e abre depois do login.
- `MainActivity`: `singleTask` e `onNewIntent` para os links.
- Manifest: `intent-filter` do esquema `conversa://chat`.
- `principal/PrincipalTela.kt`:
  - barra inferior Conversas / Chamadas / Atividades (badge de novas) / Configurações;
  - faixa de sem conexão no topo;
  - Snackbar "<erro> (tentando de novo…)" enquanto a sessão não carrega.
- `principal/ConfiguracoesProvisorias.kt`: nome e e-mail, servidor (abre a tela Servidor) e "Sair" com confirmação de perigo.
- `ConversaApplication`:
  - inicia presença, início de sessão e limpeza;
  - no fim da sessão cancela todas as notificações e limpa os caches de imagem (memória e disco).
- Saiu a tela "Início" provisória da etapa 1.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
- **124 testes, 0 falhas.** Novos neste bloco:
  - `LoginViewModelTest` (6);
  - `CadastroViewModelTest` (4);
  - `LinkConversaTest` (3, Robolectric).
- Lint: 0 apontamentos.
  - Corrigido: `@Keep` no enum `AvisoLogin`, que vai na rota tipada; sem ele o R8 pode quebrar o serializer.

## Decisões
- **Links tratados pelo `MainViewModel`, não pelo `deepLinks` do NavHost:** assim um link nunca abre uma conversa sem sessão, e o link que chega antes do login não se perde.
- **Configurações e as abas Chamadas/Atividades provisórias:** a estrutura de navegação fica pronta agora; o conteúdo é das etapas 6 e 8.
- **Sem a espera de 1,5 s depois do cadastro** (o web espera): volta direto ao login, com o aviso no Snackbar.

## Pendências
- Testes em aparelho/emulador:
  - logar, matar o app e reabrir;
  - conferir o dispositivo no banco;
  - sem notificação depois do logout.
- Encerrar a chamada no logout (etapa 6); cache de áudio (etapa 4).
