# 2026-10-16 · 03 · Permissões de uso

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.9; FC-814; GER-09
- **Branch:** `reescrita`
- **Commits:** `946c410`

## Contexto
- **O servidor** (`ef13c22`) tem 26 permissões de uso (enviar emoji, figurinha, imagem, vídeo, áudio, arquivo; responder, encaminhar, reagir, postagem, votação, confirmação, agendar; grupo; chamada de áudio e de vídeo; nome, e-mail, avatar, senha, privacidade, cores, config. de chamadas…).
  - Confere cada uma e responde 403 "Você não tem permissão para: <descrição>.".
  - No **modo aberto** (ninguém com "permissoes"), todos podem tudo, e o `GET /usuario/permissoes` devolve as 32.
  - Conta nova ganha todas as de uso; o administrador restringe depois.
- **O web** (`9c6e8a6`) lê as permissões ao abrir (`auth.pode`, que diz "pode" até carregar) e esconde ou trava cada ação (`utils/permissoesUso.ts`).
- **No app,** nada disso existia: os botões apareciam, o envio só marcava "falhou" sem dizer por quê, e o "Salvar" do perfil mandava nome e e-mail juntos, exigindo as duas permissões.

## O que foi feito
- **`core:model`:**
  - `PermissoesDeUso`: `codigos` nulo enquanto não carregou (tudo pode); `pode(codigo)`; `algumAnexo`;
  - `codigoDoAnexo`: a permissão que cada anexo pede, como o servidor confere (vídeo pela extensão ou pelo mime);
  - `anexosBarrados`: os códigos que faltam, sem repetir, na ordem do web;
  - `ehNomeDeVideo` (`Chat.kt`);
  - `dadosDoPerfilValidos` aceita campo nulo (travado).
- **`core:data`:**
  - `SistemaRepositorio.permissoesDeUso`, presas ao usuário logado; `iniciar()` lê ao entrar e cada vez que o app volta à frente (o servidor não avisa mudança pelo WS). Chamado no `ConversaApplication`;
  - `EnvioMensagens.recusas`: um 403 no envio emite `RecusaEnvio(conversaId, motivo)` (a bolha continua "falhou");
  - `PerfilRepositorio.alterarDados(nome?, email?)`: só vai o que não é nulo.
- **`core:ui`:** `LocalPermissoesDeUso`, dado na raiz da `MainActivity` e da `ChamadaActivity`.
- **Telas:**
  - **Campo da mensagem:** no "+", Galeria (imagem ou vídeo), Câmera (imagem), Documento (algum anexo), Figurinha, Votação, Postagem e Confirmar leitura; o microfone (áudio) e o agendar no toque longo do enviar;
  - **Menu da mensagem:** a linha de reações, Responder, Responder no privado (responder e encaminhar, como o web), Encaminhar, Pedir confirmação e Ocultar; os chips de reação só alternam com "reagir"; o deslizar para responder só com "responder";
  - **Chamadas:** voz e vídeo no cabeçalho do chat, "Ligar" na folha do perfil, "Ligar novamente" no histórico (pelo tipo) e "Ativar vídeo" na chamada de voz;
  - **Nova conversa:** "Novo grupo" só com "grupo";
  - **Configurações:** Aparência ("cores") e Chamadas ("config_chamadas");
  - **Perfil:** a foto mostra "Você não tem permissão para mudar o avatar." no lugar dos botões; nome e e-mail travados com o texto do web embaixo; o "Salvar dados" manda só o que pode; a senha some;
  - **Privacidade:** "Você não tem permissão para mudar a privacidade." e as chaves desabilitadas.
- **`ChatViewModel`:**
  - anexos escolhidos ou colados sem a permissão ficam de fora, e a tela diz quais: "Você não tem permissão para enviar imagens, vídeos." (texto do web);
  - as recusas do envio desta conversa viram aviso com o motivo do servidor.
- **Testes:** `PermissoesDeUsoTest` (3), `SistemaRepositorioTest` (lê ao voltar à frente, outra conta não herda), `MensagensTest` (403 → recusa), `PerfilRepositorioTest` e `PerfilViewModelTest` (campo travado não vai), `PerfilTest`, `ChatViewModelTest` (anexo barrado, recusa de outra conversa ignorada, texto dos barrados).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (533 testes), `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), fora do modo aberto:**
  - **Preparação:** cópia da tabela `permissao_usuario` (78 linhas: as 26 de uso das 3 contas). Pela API, o B ganhou "permissoes" (fim do modo aberto) e tirou do A vídeo, arquivo, reagir, encaminhar, chamada de vídeo, grupo, avatar, e-mail, privacidade, cores, confirmação e emoji.
  - **Chat (grupo):** só o botão de voz no cabeçalho; o "+" sem "Confirmar leitura"; o menu da mensagem sem reações, Encaminhar e Responder no privado; o microfone continuou (áudio permitido).
  - **403:** com uma resposta já começada, o B tirou "responder" do A; ao enviar, a bolha ficou "falhou" e apareceu "Você não tem permissão para: responder mensagens.". A mensagem local foi descartada. Depois de o app ir ao fundo e voltar, o menu ficou só com "Copiar".
  - **Anexo:** um PDF pelo "Documento" não entrou na fila, e apareceu "Você não tem permissão para enviar arquivos.".
  - **Telas:** Nova conversa sem "Novo grupo"; Configurações sem "Aparência"; Perfil com o aviso do avatar e o e-mail travado com o texto do web, e "Salvar dados" respondeu "Dados atualizados com sucesso." (só o nome foi); Privacidade com o aviso e as chaves desabilitadas.
  - **Restauração:** a tabela voltou da cópia (idêntica, conferida com `diff`), e o servidor voltou ao modo aberto (`modo_aberto: true`, 32 permissões para o A). A última "permissoes" não sai pela API ("Ao menos uma pessoa precisa poder gerenciar as permissões."), por isso a restauração foi direto na base de dev.

## Decisões
- **As permissões vêm por `CompositionLocal`,** não por cada ViewModel: são muitas telas que só escondem botões, e o servidor confere de qualquer jeito.
- **Antes de carregar, tudo pode,** como o `auth.pode` do web: sem rede, o app não esconde nada, e o 403 explica.
- **Lê também ao voltar à frente** (o web só ao abrir): uma mudança do administrador vale sem sair da conta.
- **Emoji:** o app não tem botão de emoji no campo (vem do teclado). Sem a permissão, o servidor recusa e a conversa mostra o motivo.
- **"Enviar para…"** (arquivos vindos de outro app) não passa pelo filtro do chat: o servidor recusa o que não pode, com o motivo na conversa.

## Pendências
- Nenhuma do GER-09.
