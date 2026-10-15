# 2026-10-15 · 01 · Investigação: o app funcionar sem FCM (push opcional)

- **Fluxo:** Planejamento e documentação
- **Tipo:** decisão (proposta, aguardando a pessoa)
- **Itens:** `docs/adr/0002-push-opcional-sem-fcm.md` (novo); `TODO.md` 5.1 (nota), 5.7 (nota) e 5.9 (nova); doc 08 S23 e S24
- **Branch:** `reescrita`
- **Commits:** `7f5954c`

## Contexto
A pessoa pediu: "o projeto também precisa funcionar sem FCM, seja opcional; investigue, anote as pendências e à noite decido".

## O que foi encontrado
- **No app:**
  - não tem Firebase nenhum, então compila e roda sem `google-services.json`;
  - na frente, tudo funciona pelo WebSocket;
  - o `ConexaoTempoReal` desliga o WebSocket 10 s depois que o app sai da frente, de propósito, para não ter serviço permanente. **Com isso, hoje, sem FCM, nada chega em segundo plano: nem mensagem, nem chamada.**
- **No servidor (`e25c4db`):**
  - o único canal de push é o FCM (`fcm.ts`, só dados);
  - ele só é mandado a quem não tem nenhum socket aberto (`usuarioConectado`, S18);
  - não há push de chamada (S1);
  - com os `fcm_*` vazios, só avisa no log.
- **No web:** usa o Firebase do navegador (`services/firebase.ts`). O projeto Firebase é um por servidor.

## O que foi feito
- **ADR 0002 (proposto):**
  - a situação atual;
  - o que "funcionar sem FCM" precisa cobrir;
  - quatro opções:
    - **A**, conexão própria em segundo plano, com notificação fixa e isenção de bateria;
    - **B**, UnifiedPush;
    - **C**, sincronização periódica;
    - **D**, Firebase ligado em tempo de execução com a configuração vinda do servidor;
  - a recomendação: **D + A + C**;
  - seis perguntas para a decisão;
  - as pendências técnicas que valem para qualquer escolha.
- **`TODO.md`:**
  - nota no 5.1 e no 5.7;
  - **5.9 nova**, com a decisão, o teste do CI sem Google, o `CanalDePush`, a tela que mostra como o aparelho recebe os avisos, os itens de cada opção e o teste no Galaxy A25. Todos marcados ⏸.
- **Doc 08:**
  - S23: configuração do cliente Firebase pelo servidor, para um APK só servir qualquer instalação;
  - S24: UnifiedPush, só se for escolhida.

## Como foi verificado
- Busca por Firebase/FCM no app (só a tela Sistema e o DTO dos parâmetros).
- Leitura do `ConexaoTempoReal` (tolerância de 10 s e o motivo) e do manifesto: o único serviço é o da chamada (`phoneCall|microphone|camera`).
- Leitura de `notificacoes.ts`, `fcm.ts` e `configuracao.ts` no servidor, e do `firebase.ts` no web.

## Decisões
- **Nenhuma ainda.** A pessoa decide à noite. Nada foi implementado nesta frente.

## Pendências
- Responder às perguntas do ADR 0002 e mudar o status dele.
- Converter a 5.9 em trabalho de verdade conforme a escolha.
