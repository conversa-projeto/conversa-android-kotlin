# 2026-10-08 · 19 · Etapa 7 — menu da mensagem, reações, ocultar e copiar

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.1, 7.2, 7.4 e a linha "Copiar" do 7.6; FC-500, FC-501, FC-503, FC-507; ENV-17, ENV-18, ENV-20
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
Primeiro bloco da etapa 7. O toque longo na bolha abre o menu da mensagem, como o web (`MensagemAcoes.vue`): reações rápidas, "mais emojis", Copiar e Ocultar. As outras ações do menu (Responder, Encaminhar, Responder no privado) entram com 7.3 e 7.6.

## O que foi feito
- **`core:model` — `Reacoes.kt`:**
  - `REACOES_RAPIDAS` (as 7 do web);
  - `emojiAceito` (no máximo 10 code points, contrato §10.12);
  - `alternarReacao`: a mesma regra do `PUT /mensagem/reacao` (põe ou tira a minha), para mostrar na hora.
- **`core:database`:** `reacoesDe` e `trocarReacoes` no `MensagemDao`; a agendada que o servidor apaga de vez usa o `remover(id)` que já existia.
- **`core:data`:**
  - `MensagensRepositorio`:
    - `reagir`: grava a reação otimista no Room, manda o `PUT` e relê a mensagem do servidor (nomes e horas), mesmo se falhar;
    - `ocultar`: `DELETE /mensagem`; com `excluida_em`, marca como oculta; sem, apaga (era agendada);
    - `recarregar`: relê uma mensagem pelo `mensagemreferencia`.
  - O repositório passou a receber um `Clock` (hora da reação otimista).
  - `MapeadoresEntidade`: `Reacao.paraEntidade`.
  - `SyncManager`: o WS 7 relê a mensagem (o evento não traz o nome de quem reagiu), só se ela está no aparelho.
- **`feature:chat`:**
  - `AcoesMensagem.kt` (novo):
    - `podeAbrirMenu`: só mensagem com id do servidor, que não é chamada nem oculta;
    - `Modifier.toqueLongo`: olha os eventos antes dos filhos (links, imagem, áudio) e consome o resto do gesto quando vira toque longo;
    - `MenuMensagem` (folha de baixo): as 7 reações, com a minha destacada, "Mais emojis", Copiar e Ocultar (só na minha);
    - `ChipsDeReacao`: emoji e contagem, destacado se eu reagi; toque alterna, toque longo mostra quem reagiu;
    - `QuemReagiu`: foto, nome e hora (só a hora se foi hoje; senão `dd/MM HH:mm`, como o web);
    - `SeletorDeEmoji`: o `EmojiPickerView` do AndroidX;
    - `ConfirmarOcultar`: os textos do web; para agendada futura, "Cancelar mensagem agendada" / "Cancelar envio" / "Voltar";
    - `AcoesAbertas` + `AcoesDaMensagem`: o que está aberto. A mensagem é relida da lista, porque as reações mudam com a folha aberta.
  - `Bolhas.kt`: o toque longo na bolha e os chips embaixo; `AcoesBolha` ganhou `aoMenu`, `aoReagir` e `aoVerReacoes`.
  - `ChatViewModel`:
    - `reagir`, `ocultar` (atualiza a lista de conversas, por causa da prévia) e `copiar`;
    - `copiar` funciona como no web: imagem vai como imagem (baixada para o cache, `ClipData` com URI do `FileProvider`); senão, os textos, um por linha;
    - eventos novos `CopiarTexto`, `CopiarImagem`, `OcultarFalhou`.
  - `ChatTela`:
    - área de transferência;
    - "Copiado!" só abaixo do Android 13, porque dali em diante o sistema mostra a confirmação;
    - "Não foi possível ocultar".
  - Textos novos no `strings.xml`.
- **Dependência:** `androidx.emoji2:emoji2-emojipicker` 1.7.0.

## Como foi verificado
- **Testes novos:**
  - `ReacoesTest` (model);
  - `MensagensTest`: reagir otimista e depois igual ao servidor, reagir sem rede, ocultar e agendada;
  - `ChatViewModelTest`: copiar texto, copiar imagem, reagir com o meu nome, ocultar que falha e que dá certo;
  - `AcoesMensagemTest`: `podeAbrirMenu` e a hora da reação.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A) contra o servidor de dev,** com o B por script na API:
  - o menu abre com as reações e Copiar; Ocultar só aparece na minha mensagem;
  - ❤️ aparece na hora e fica no servidor;
  - a folha "Reações" mostra nome e hora;
  - o toque no chip tira a reação;
  - "Mais emojis" abre o seletor e grava o 🤩;
  - a reação do B (WS 7) chega sozinha ("😂, 2");
  - "Copiar", depois colar no campo, deu o texto da mensagem;
  - "Ocultar" (confirmação com o texto do web): a bolha vira "Mensagem oculta" e a prévia da lista também;
  - depois de rolar a lista para cima e para baixo, o toque longo reage na mensagem certa.

## Decisões
- **Reação otimista, depois a mensagem relida:** a resposta do `PUT` só diz `add`/`remove`, sem nomes nem horas. Reler a mensagem deixa os chips e "quem reagiu" iguais ao servidor.
- **WS 7 relê a mensagem:** pelo mesmo motivo; só quando a mensagem já está no aparelho.
- **Seletor de emoji do AndroidX:** é o mesmo do teclado do sistema. Os rótulos seguem o idioma do aparelho; o emulador está em inglês, então aparecem em inglês.
- **`toqueLongo` com a função como chave do `pointerInput`:** no primeiro teste apareceu uma reação numa mensagem que não foi tocada, e não se repetiu. Com `pointerInput(Unit)`, quando a LazyColumn reaproveita a composição de um item para outra mensagem, o gesto chama a função antiga, da mensagem antiga. A chave agora é a própria função, e o teste depois de rolar passou.

## Pendências
- Responder, Encaminhar e Responder no privado no menu (7.3, 7.6).
- A variante "agendada" do ocultar só foi testada no unitário: agendar pelo app chega no 7.10.
- Ficaram reações de teste no "Grupo criado pelo B" (servidor de dev).
