# 2026-10-15 · 15 · Imagem ou emoji do grupo: na tela do grupo e ao criar

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código
- **Itens:** `TODO.md` 2.x (as linhas 🆕 `769bd7d` da tela do grupo e `e33ecba` de criar); FC-217; CON-07, CON-15
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O bloco 13 passou a mostrar a imagem e o emoji do grupo. Faltava escolher, como o web faz:
- no painel do grupo (`PainelGrupo.vue`): "Imagem", "Emoji" e "Remover", e um tira o outro;
- ao criar (`CreateGroupModal.vue`): a imagem sobe antes, e a imagem ou o emoji vão no grupo depois dos membros.

## O que foi feito
- **Preparo da imagem:**
  - o `PreparadorFotoDePerfil` do `feature:config` virou o `PreparadorAvatar` no `core:data` (`anexos/`): o recorte quadrado de 256×256 em JPEG 85%, agora também para o grupo;
  - a imagem em memória para o envio virou `ImagemEmMemoria`, compartilhada com o perfil;
  - o perfil (`PerfilViewModel`, `PerfilRepositorio`) só trocou o nome.
- **Rede:** `AlterarConversaRequisicao` com `descricao` opcional, `avatar_anexo_id` e `emoji`; os dois campos podem ir com nulo explícito. O renomear continua só com a descrição.
- **`AvatarDoGrupo`** (`core:data`, novo):
  - `enviarImagem`: prepara e sobe, e devolve o id do anexo; a imagem que não abre vira `ImagemIlegivelException`;
  - `alterar(conversa, Imagem | Emoji | nulo)`: `PATCH /conversa` com os dois campos (um nulo; os dois nulos é "Remover") e relê a lista.
- **`EscolhaAvatarGrupo`** (`feature:conversas`, novo):
  - **Conteúdo:** o avatar de 64 dp e "Imagem" (o seletor de fotos do sistema), "Emoji" (o `SeletorEmojis` do bloco 14) e "Remover" (quando há um dos dois).
  - **Salvando:** os botões ficam desabilitados e aparece "Salvando…".
- **Telas:**
  - **Membros do grupo:** a escolha no topo. Lê a imagem e o emoji da conversa; a falha vira aviso: "Não foi possível ler a imagem." ou o motivo do servidor.
  - **Criar grupo:** a escolha acima do nome; a imagem é só a prévia até "Criar". Ao criar, a imagem sobe antes; a imagem que não abre não cria o grupo e avisa. O avatar vai depois dos membros; se só ele falhar, o grupo abre assim mesmo.
- **Textos** (os do web): "Imagem", "Emoji", "Remover", "Salvando…" e "Não foi possível ler a imagem.".
- **Testes:**
  - JSON: imagem com o emoji nulo; o renomear só com a descrição;
  - criar com emoji: vai depois de criar, e a imagem tira o emoji;
  - criar com uma imagem que não abre: não cria e avisa.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (501 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A):**
  - **No "Grupo de teste 2":**
    - "Emoji" › "festa" › 🎉: o avatar mudou, e no servidor ficou `emoji: 🎉`;
    - "Imagem" › a imagem de teste do emulador (`avatar-teste.png`, gerada para os testes): o servidor ficou com a imagem e sem emoji;
    - "Remover": sem os dois.
  - **Criar grupo:** "Aniversario" com 🎂 e o B. O servidor criou o grupo 16 com `emoji: 🎂`, e a lista mostrou o 🎂 no lugar da inicial.

## Pendências
- Nenhuma.
