# 2026-10-17 · 02 · Ajustes do sincronismo `ac710eb`

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8); toca também a sessão e os contatos (etapa 2) e o áudio (etapa 4)
- **Tipo:** código
- **Itens:** `TODO.md` 2.6, 2.9, 4.5, 8.3, 8.4 e 8.5 (linhas 🆕 `ac710eb`/`f351190`); FC-222, FC-224, FC-422, FC-423, FC-816; AUT-11, CON-01, CON-11, ANX-17, ANX-18, CFG-08, GER-09
- **Branch:** `reescrita`
- **Commits:** `08e2e77`

## Contexto
O sincronismo com o servidor `ac710eb` e o web `f351190` (histórico 2026-10-17-01) trouxe, além das pastas e da localização, ajustes menores que o app já podia fazer sem decisão: os contatos pelo WS 66, a permissão `usuarios` juntada a `permissoes`, a `localizacao` nas permissões de uso, o espaço usado, o "Usuário desde", a velocidade do áudio e o "Gravando áudio…" na lista.

## O que foi feito
- **Permissões (GER-09, CFG-08, FC-816):**
  - `CodigoPermissao.USUARIOS` saiu; `LOCALIZACAO` entrou e fecha o grupo Enviar, como no web;
  - na tela de Acessos, `permissoes` se chama "Pessoas" e a `localizacao` "Localização e trajeto"; os textos do modo aberto e da explicação dizem "Pessoas";
  - cada pessoa mostra o espaço dos arquivos que enviou, o "Usuário desde" e o selo "Bot" (`UsuarioAcessos` com `criadoEm`, `bot` e `espaco`).
- **Contatos (CON-11, AUT-11, FC-222):**
  - `EventoSocket.ContatosAtualizados` (66) e o `SyncManager` relê os contatos no evento e em cada ressincronização (reconexão, volta à frente), como o web;
  - `criado_em` no `ContatoDto`, no `Contato` e no Room (**v12**, coluna nova `criadoEm`);
  - `ContatosRepositorio.observarEu()`.
- **"Usuário desde" (AUT-11):** na folha do perfil de outra pessoa (só com a data) e no Perfil, debaixo de "Nome e e-mail visíveis na sua conta.".
- **Espaço usado (ANX-17, FC-423):** `GET /usuario/espaco`; a seção "Armazenamento" no Perfil, entre a foto e os dados, com os textos do web ("Calculando…", "Não foi possível calcular o espaço usado", "**<tamanho>** usados pelos arquivos que você enviou (…)"). Fica num `ArmazenamentoViewModel`, sem mexer no ViewModel do perfil.
- **Velocidade do áudio (ANX-18, FC-422):**
  - `PlayerAudio.velocidade` e `mudarVelocidade`, guardada em `SharedPreferences` (vale para todos os áudios, como o `localStorage` do web; padrão na interface para os falsos dos testes);
  - o `core:media` passa a depender do `core:model` (`velocidadeValida`);
  - botão "1x" ao lado da barra, 1x → 1,5x → 2x → 3x → 1x; o leitor de tela diz "Velocidade: 1,5x".
- **"Gravando áudio…" na lista (CON-01, FC-224):** `presenca.gravando` no item; pontinhos e texto na cor de gravando, com prioridade sobre o "digitando".
- **Testes:**
  - `DadosAudioTest` (velocidades);
  - `SistemaTest` (`localizacao` no Enviar);
  - `PerfilTest` ("Usuário desde" na ficha);
  - `EventoSocketParserTest` (66);
  - `SyncManagerTest` (relê os contatos na reconexão e no 66; a construção virou `criar()`);
  - `SistemaRepositorioTest` (`criado_em` e `espaco`);
  - `MigracoesTest` (versão 12).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev (`ac710eb`):**
  - **Perfil:** "Armazenamento — 200.9 MB usados pelos arquivos que você enviou (…)" (o vídeo de teste de 200 MB) e "Usuário desde 07/10/2026";
  - **Acessos:** "Ninguém tem a permissão Pessoas ainda", "200.9 MB · Usuário desde 07/10/2026" no A, a administração sem "Usuários" e "Localização e trajeto" no fim do Enviar;
  - **folha do perfil do C:** "Usuário desde 08/10/2026";
  - **áudio:** o botão foi de 1x a 3x e voltou a 1x, sem tocar o áudio;
  - **WS 66:** a conta C mudou o nome pela API; com a "Nova conversa" aberta, a lista mudou sozinha quando o nome voltou. A conta ficou como antes;
  - **gravando:** a conta C avisou `POST /conversa/gravando` na direta, e a lista do A mostrou "Gravando áudio…" em vermelho.

## Decisões
- **Reler os contatos em toda ressincronização,** não só na reconexão do socket: é um `GET` pequeno, e cobre a volta à frente e o push.
- **O espaço e o "Usuário desde" na mesma linha** de cada pessoa nos Acessos ("200.9 MB · Usuário desde …"): no celular não cabem as três colunas do web.

## Pendências
- As pastas (FC-223, FC-318, FC-527), a localização (FC-319, FC-528) e os anexos de todas as conversas (FC-424).
