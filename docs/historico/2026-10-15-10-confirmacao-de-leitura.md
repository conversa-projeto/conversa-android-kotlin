# 2026-10-15 · 10 · Confirmação de leitura: pedir, confirmar e pedir depois

- **Fluxo:** Ações sobre mensagens (etapa 7); toca também em Mensagens (etapa 3)
- **Tipo:** código
- **Itens:** `TODO.md` 3.8 (as duas linhas 🆕 `769bd7d`) e 7.1 (linha 🆕 `6d20ff7`/`e33ecba`); FC-521, FC-525; MSG-21, ENV-24, ENV-27
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **Servidor e web:** o servidor (`b8ea0c2`, `6d20ff7`) e o web (`769bd7d`, `e33ecba`) têm a confirmação de leitura:
  - a mensagem pede confirmação, de todos ou de uma lista;
  - quem está na lista marca "Confirmar leitura";
  - quem enviou vê quantos confirmaram e quem falta;
  - até 24 h depois do envio, quem enviou ainda pode passar a pedir.
- **O que o Android já tinha** (bloco 05 deste dia): os dados (`confirmacao` na mensagem, Room v4) e os WS 8 e 65, que releem a mensagem.
- **O que faltava:** a interface e as rotas.

## O que foi feito
- **Regras** (`core/model/.../Confirmacao.kt`, novo):
  - `resumoConfirmacao`: "Aguardando confirmação" / "Confirmada por N de T" / "Confirmada por N";
  - `faltaConfirmar`;
  - `podePedirConfirmacao`: minha, salva e visível, não oculta, sem pedido, até 24 h de `coalesce(visivel_em, inserida)`;
  - `filtrarMembros` (sem acento nem maiúsculas).
- **Rede:**
  - `pede_confirmacao` e `confirmacao_usuarios` em `EnviarMensagemRequisicao`;
  - `POST /mensagem/confirmar` (`MensagemIdDto`);
  - `POST /mensagem/pedir-confirmacao` (`PedirConfirmacaoRequisicao`, sem a lista = todos).
- **Dados:**
  - `EnvioMensagens.enviar(confirmacao = PedidoConfirmacao)`: a otimista já mostra "Aguardando confirmação" com os destinatários;
  - `MensagensRepositorio.confirmarLeitura` e `pedirConfirmacao` chamam o servidor e releem a mensagem. O WS 8 da minha confirmação não volta para mim.
- **Bolha** (`Confirmacao.kt` no `feature:chat`, novo; `Bolhas.kt`), um chip antes das reações, como no web:
  - **quem está na lista:** a caixa "Confirmar leitura", que confirma no toque, e depois "Leitura confirmada";
  - **quem enviou, e quem não está na lista:** "Aguardando confirmação" / "Confirmada por N de T";
  - **o detalhe:** o toque (ou o toque longo, na caixa) abre "Confirmação de leitura", com quem confirmou (foto, nome e hora) e "Falta confirmar". No web isso aparece ao passar o mouse.
- **Campo** (`CampoMensagem.kt`):
  - **"+" › "Confirmar leitura":** na direta, vale na hora; no grupo, abre antes "Quem precisa confirmar".
  - **"Quem precisa confirmar":** "Filtrar por nome", "Todos (N)", um a um, "Cancelar" / "Pronto". Com todos marcados, a lista vai vazia, como no web: quem entrar no grupo antes do envio também precisa confirmar.
  - **A faixa:** "A mensagem vai pedir confirmação de leitura" (no grupo, "de todos" / "de N pessoas" e "Escolher"), com o "×" "Não pedir confirmação".
  - Vale só para a próxima mensagem: texto, figurinha, código ou gravação.
- **Menu da mensagem** (`AcoesMensagem.kt`):
  - "Pedir confirmação de leitura" nas minhas, até 24 h; no grupo, com o "Quem precisa confirmar" e o botão "Pedir confirmação";
  - a falha aparece como "Não foi possível pedir a confirmação: <motivo>".
- **ViewModel:** `confirmacaoNoCampo`, `escolherConfirmacao`, `cancelarConfirmacao`, `pegarConfirmacao` (na direta, o outro participante; no grupo, os membros escolhidos), `confirmarLeitura`, `pedirConfirmacaoDepois` e `membrosDoGrupo`.
- **Textos:** os do web (`MessageBubble.vue`, `MessageInput.vue`, `ConfirmacaoLeituraModal.vue`, `MensagemAcoes.vue`), mais "Confirmação de leitura" e "Ver quem confirmou" (o título da folha e o rótulo de acessibilidade, que o web não tem).
- **Testes (+10):**
  - as regras (resumo, falta confirmar, o prazo de 24 h e o filtro);
  - o JSON do envio e do pedir depois;
  - o envio com a lista e a otimista aguardando;
  - confirmar, o 403 e pedir depois, no repositório;
  - no ViewModel: a confirmação do campo só na próxima mensagem, a caixa só para quem pode e o aviso de falha.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (484 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), no grupo 3, com o B pela API (as rotas do web):**
  - **Pedir no envio:** "+" › "Confirmar leitura" abriu o "Quem precisa confirmar" com "Todos (1)" e o B; depois de "Pronto", a faixa "…de todos" com "Escolher". "Leia o aviso" saiu com "Aguardando confirmação".
  - **A confirmação do B:** o B confirmou; o chip virou "Confirmada por 1 de 1" sozinho (WS 8). O toque abriu o detalhe com o B e a hora.
  - **Confirmar como destinatário:** o B mandou uma mensagem pedindo confirmação. O A viu "Confirmar leitura", tocou e ficou "Leitura confirmada"; no servidor, o A aparece entre quem confirmou.
  - **Pedir depois:** toque longo na postagem do A (de hoje) › "Pedir confirmação de leitura" › "Pedir confirmação". No servidor, a 1182 passou a pedir, com o B na lista; no app, "Aguardando confirmação".

## Decisões
- **Os detalhes ficam numa folha** (toque no chip), porque no celular não há o "passar o mouse" do web. Na caixa de quem precisa confirmar, o toque confirma, e os detalhes ficam no toque longo.
- **O app relê a mensagem depois de confirmar ou pedir**, em vez de montar a confirmação no aparelho: fica igual ao servidor, com nomes e horas.

## Pendências
- Ao fechar uma folha ou um diálogo, o teclado volta a abrir se o campo tinha o foco antes (acontece com qualquer folha do chat, não só aqui). Ver depois se vale tirar o foco do campo ao abrir o menu.
