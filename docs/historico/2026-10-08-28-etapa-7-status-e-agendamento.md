# 2026-10-08 · 28 · Etapa 7 — detalhe do status e agendar mensagem

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.10; FC-513, FC-514
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O web tem:
- o detalhe do status (`DetalheStatusMensagem.vue`) ao tocar no ✓ de uma mensagem minha;
- o agendamento (`AgendarMensagemModal.vue`, selo em `MessageBubble.vue`).

No Android, a rota `GET /mensagem/status/detalhe` e o campo `visivel_em` do envio já existiam no `:core:network`, mas não eram usados. O cancelamento de uma agendada ("Cancelar mensagem agendada") já vinha do 7.2.

## O que foi feito
- **`core:model` — `Agendamento.kt`:**
  - `sugestaoAgendamento` (amanhã 08:00);
  - `validarAgendamento` (≥ 5 min, ≤ 1 ano);
  - `agendadaFutura`, `quandoAgendada` (hoje / amanhã / dd/MM);
  - `StatusDestinatario`, `etapasDaDireta` ("Ouvida" só em áudio, "Oculta" só se ocultada), `secoesDoGrupo` (sem seções vazias), `horaDoStatus`.
- **`core:data`:**
  - `MensagensRepositorio.statusDetalhe`;
  - `EnvioMensagens.enviar(..., visivelEm)`: manda `visivel_em` em ISO UTC; a otimista já ordena pela data agendada.
- **`feature:chat` — `StatusEAgendamento.kt`:**
  - **`DetalheStatus`:** folha com "Carregando…" e o erro do web. Na direta, uma linha por etapa ("Aguardando" sem data); no grupo, "Visualizada por (N)", "Recebida por (N)" e "Aguardando (N)".
  - **`AgendarMensagem`:** diálogo "Agendar mensagem" com Data e Hora (DatePicker e TimePicker de 24 h), os erros do web e "Agendar" desabilitado com erro.
  - **`SeloAgendada`:** "Agendada para hoje HH:MM / amanhã HH:MM / dd/MM HH:MM".
  - **`agendadaNaTela`:** faz o selo e o esmaecido sumirem na hora exata, sem nova lista.
- **`Bolhas.kt` e `Anexos.kt`:**
  - o rodapé (hora + status) das minhas é tocável ("Ver status"), via `LocalAoVerStatus`;
  - a bolha agendada fica com 70% de opacidade e o selo em cima.
- **`CampoMensagem.kt`:** o Enviar aceita toque longo → menu "Agendar mensagem" → diálogo. O ViewModel ganhou `agendar` e `statusDetalhe`.
- **Testes:**
  - `AgendamentoTest` (6 casos);
  - `MensagensTest`: "agendada vai com visivel_em";
  - `ChatViewModelTest`: "agendar manda o mesmo envio com visivel_em".

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou. O lint só aponta versões novas de dependências.
- Emulador: ver a seção abaixo, preenchida no teste.

## Decisões
- **Cores do selo:** o FMX não tem cor de aviso; o selo usa `campoEntrada` (fundo) e `iconeAcao` (texto e ícone), sem cor nova.
- **Agendar pelo toque longo no Enviar**, como o TODO pede. O web tem um botão de relógio separado.

## Pendências
- Nenhuma do 7.10.
