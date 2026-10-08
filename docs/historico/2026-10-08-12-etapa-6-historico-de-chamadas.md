# 2026-10-08 · 12 · Etapa 6 — histórico de chamadas

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 6.10; FC-715; CHA-22
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A aba "Chamadas" da tela principal só dizia "em breve". A referência é o `ChamadaHistorico.vue` do web.

## O que foi feito
- **`core:model` (`HistoricoChamadas.kt`):**
  - `ChamadaHistorico` e `ParticipanteHistorico`;
  - quem é "o outro", "Grupo (N)", efetuada ou recebida;
  - **perdida:** recebida em que eu não entrei (fiquei tocando ou fui recusado sozinho, o "não atendeu" do app);
  - `filtrarHistorico` ("Perdidas" e busca pelo nome de qualquer outro participante);
  - `agruparHistorico` (Hoje / Ontem / data, pelo dia local).
- **`ChamadasRepositorio.historico(de, ate)`** e o mapeador do `ChamadaHistoricoDto`.
- **`feature:chamada` (`HistoricoChamadas.kt`):**
  - **Lista:**
    - "Chamadas" com "Todas"/"Perdidas", "Buscar contato" e "Período" (seletor de datas, com "limpar");
    - cabeçalhos fixos por dia.
  - **Linha:**
    - avatar, seta efetuada/recebida/perdida com a cor do FMX, nome em vermelho na perdida, hora;
    - "Áudio · 00:18" ou "Vídeo · Perdida"/"Recusada"/"Cancelada";
    - "Ligar novamente" no mesmo tipo e com os mesmos participantes.
  - **Toque:** a linha abre a conversa; a chamada que não veio de uma conversa não abre nada, como no web.
  - **Recarga:** recarrega ao abrir a aba e quando uma chamada termina.
- **`Ligar.kt`:** o pedido de permissões (microfone; câmera no vídeo) virou uma peça comum, usada pela conversa e pelo "Ligar novamente".
- **`PrincipalTela`/`ConversaNavHost`:** a aba "Chamadas" mostra o histórico.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck`:
  - **278 testes, 0 falhas** (4 novos em `HistoricoChamadasTest`: efetuada/outro/grupo, perdida, filtros, agrupamento com virada de dia pelo fuso);
  - 0 apontamentos de lint;
  - ktlint OK.
- **No emulador, com as chamadas de teste do dia:**
  - a lista apareceu em "Hoje", com setas verdes (recebidas), azul (a que o app fez) e vermelha ("Vídeo · Perdida");
  - "Perdidas" mostrou 5;
  - "xyz" na busca deu "Nenhuma chamada";
  - a linha da chamada feita pela conversa abriu a conversa com o B;
  - "Ligar novamente" ligou e o web tocou.

## Decisões
- **"Perdida" diferente do web.**
  - O web filtra pelo status 5, que o servidor nunca grava (contrato §9.1): a aba "Perdidas" dele fica sempre vazia.
  - Aqui vale o que aconteceu comigo: recebida e não entrei.
  - Recusar de propósito também conta, porque o histórico não distingue um do outro.
  - Vale avisar o web: a correção lá é a mesma regra.
- **A busca procura em todos os outros participantes,** não só no "outro" da linha. No web, procurar alguém numa chamada em grupo não acha.

## Pendências / próximos passos
- **Sugestão para o web:** a mesma regra de "perdida". Não abri issue; fica para o usuário decidir.
