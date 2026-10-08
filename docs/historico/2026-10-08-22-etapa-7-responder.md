# 2026-10-08 · 22 · Etapa 7 — responder (deslizar, menu, barra e citação)

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.3 e o "Responder" do 7.1; FC-502; ENV-06, ENV-20
- **Branch:** `reescrita`
- **Commits:** `cdfb8cf`

## Contexto
Responder a uma mensagem, como o web (`chat.responderMensagem`, a barra do `MessageInput.vue`) e o WhatsApp no celular:
- deslizar a bolha para a direita, ou "Responder" no menu;
- a barra aparece acima do campo;
- o envio leva `mensagem_referencia {tipo: 1, origem_mensagem_id}`.

## O que foi feito
- **`core:data`:**
  - `ReferenciaPendente(tipo, mensagem)`. O `EnvioMensagens.enviar` ganhou o parâmetro `referencia`:
    - o corpo do `PUT /mensagem` leva `ReferenciaEnvioDto`;
    - a otimista grava a citação no `referenciaJson` (a respondida vira `MensagemResumidaDto`, com a cadeia que ela tinha), e a bolha já mostra a citação antes do servidor;
    - a referência fica no pacote da fila, então "Reenviar" mantém.
  - Mapeador `Mensagem.paraResumidaDto` (+ conteúdos e referências aninhadas).
- **`feature:chat`:**
  - **`ChatViewModel`:**
    - `respondendo` no estado; `responder(mensagem)` (só mensagem com id do servidor, não oculta nem chamada) liga a barra e pede o foco do campo; `cancelarResposta`;
    - o `enviar` manda a referência e limpa a barra;
    - o campo, o foco e a resposta foram juntados em `ExtrasDoCampo` para caber no `combine`.
  - **Gravação:** o `ControleGravacao` recebe `pegarResposta`. A gravação enviada durante uma resposta vai como resposta, como no web (que usa a mesma resposta pendente para todo envio).
  - **`DeslizarParaResponder`:** arrastar a bolha para a direita.
    - A bolha acompanha o dedo; o ícone de resposta aparece atrás (transparência e escala) e o aparelho vibra ao passar de 64 dp.
    - Soltando além do limite, responde; a bolha volta animada.
  - **Acessibilidade:** a bolha ganhou `onLongClick` ("Ações da mensagem") e a ação "Responder". O toque longo e o deslizar são gestos, e o TalkBack não os alcança sozinho.
  - **Menu:** item "Responder" (primeiro da lista, como no web).
  - **`BarraResposta` no campo:** borda azul, o primeiro nome (como o servidor manda e o web mostra), o resumo (`textoDoResumo`, extraído da `Citacao` para ser reaproveitado) e o "×" ("Cancelar resposta").
  - Textos novos: "Responder", "Cancelar resposta", "Ações da mensagem".

## Como foi verificado
- **Testes novos:**
  - `MensagensTest`: a resposta manda a referência e a otimista já mostra a citação;
  - `ChatViewModelTest`: responder ignora a otimista, liga a barra e o foco, vai no envio e limpa; cancelar não manda referência.
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou; o lint ficou sem avisos.
- **Emulador (A), no "Grupo criado pelo B":**
  - deslizar a 1049 mostrou a barra "Teste / Mensagem número 1049" e o campo com o cursor;
  - enviar "Respondendo a 1049" mostrou a citação na hora, e o servidor gravou a referência tipo 1;
  - "Responder" do menu na 1050 mostrou a barra, e o "×" tirou;
  - uma gravação feita respondendo à 1048 saiu com a referência (tipo 5 + referência tipo 1);
  - no dump de acessibilidade, a bolha aparece como `long-clickable`.

## Decisões
- **Mensagem só com a citação, sem texto, não sai.** O web habilita o "Enviar" com a resposta pendente, mas manda os conteúdos vazios; no Android o botão só aparece com texto ou anexo.
- **Deslizar só para a direita**, nas minhas e nas dos outros, como o WhatsApp.

## Pendências
- **Confirmar com o TalkBack, em aparelho,** que a bolha anuncia "Ações da mensagem" e "Responder" (não se liga o TalkBack no emulador de teste).
- **7.5:** citação aninhada e "ir para a mensagem" ao tocar na citação.
