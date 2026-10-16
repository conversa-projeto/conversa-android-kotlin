# 2026-10-16 · 04 · Cadastro fechado pelo administrador

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2); toca também as configurações (etapa 8, Sistema)
- **Tipo:** código
- **Itens:** `TODO.md` 2.4 (linha 🆕 `ef13c22`); FC-220; AUT-02; CFG-07
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`ef13c22`) tem o parâmetro `cadastro_aberto` (padrão ligado). Fechado, o `PUT /usuario` responde 403 "O cadastro está fechado: peça a um administrador para criar sua conta."; a rota pública `GET /cadastro` → `{aberto}` diz se a tela de login oferece o cadastro (contrato §2.6).
- **O web** (`9c6e8a6`) consulta ao montar o login e esconde "Não tem conta? Criar conta"; sem resposta, mostra. No Sistema, a seção "Cadastro" (a primeira) tem a caixa "Permitir que as pessoas se cadastrem".
- **No app,** o link aparecia sempre e levava a um 403; o Sistema não tinha a chave.

## O que foi feito
- **`core:network`:** `ConversaApi.cadastro()` (`GET /cadastro`, pública) e `CadastroDto`; `cadastro_aberto` no `ParametrosDto` e no `AlterarParametrosRequisicao`.
- **`core:model`:** `ParametrosSistema.cadastroAberto`, `AlteracaoParametros.cadastroAberto` e o `alteracaoDosParametros` com ela.
- **`core:data`:** `AutenticacaoRepositorio.cadastroAberto()`: sem resposta (erro, servidor antigo), `true`, como o web (o servidor recusa com o motivo); o mapeamento no `SistemaRepositorio`.
- **Login:** `LoginUiState.cadastroAberto`, lido ao abrir e a cada troca de servidor; fechado, o "Não tem conta? Criar conta" some.
- **Sistema:** a seção "Cadastro", a primeira depois do aviso, com "Permitir que as pessoas se cadastrem" e o texto do web; a linha da chave virou `LinhaInterruptor` (a do TURN usa a mesma).
- **Testes:** `AutenticacaoRepositorioTest` (fechado; sem resposta, aberto), `LoginViewModelTest` (fechado esconde), `SistemaTest` (só a chave vai no PATCH).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev:**
  - com o cadastro aberto, o login mostrou "Não tem conta? Criar conta";
  - em Configurações › Sistema, a seção "Cadastro" apareceu primeiro, ligada; desligada e salva, "Configurações salvas." e `GET /cadastro` → `{"aberto":false}`;
  - depois de sair, o login ficou sem o "Criar conta";
  - religado (`PATCH /parametros {cadastro_aberto: true}` pela API), o link voltou ao reabrir o app. O servidor ficou como estava.

## Decisões
- **Lido a cada troca de servidor,** não só ao abrir: um servidor pode ter o cadastro aberto e outro fechado.
- **A tela de cadastro não muda:** se alguém chegar lá com o cadastro fechado (ex.: o administrador fechou depois), o 403 já mostra o motivo do servidor.

## Pendências
- Nenhuma do AUT-02. A aba Usuários (cadastro pelo administrador, CFG-11) continua na avaliação da FC-815.
