# ADR 0002 — O app precisa funcionar sem FCM (push opcional)

- **Status:** proposto, aguardando decisão (pedido de 2026-10-15: "o projeto também precisa funcionar sem FCM, seja opcional").
- **Data:** 2026-10-15
- **Base:**
  - código do app (`ConexaoTempoReal`, manifestos, `TODO.md` etapa 5);
  - servidor `e25c4db` (`src/notificacoes.ts`, `src/fcm.ts`, `src/configuracao.ts`);
  - web `8b43965` (`src/services/firebase.ts`);
  - contrato, doc 01 §7 e §15.

## Onde estamos hoje

### No app (nova base)
- **Sem Firebase:** não há `firebase-messaging`, `google-services.json` nem o plugin `com.google.gms.google-services`. O app **já compila e roda sem FCM.** A etapa 5.1 ficou ⛔ esperando o projeto Firebase.
- **Primeiro plano:** tudo funciona sem FCM (mensagens, chamadas, presença), porque vem pelo WebSocket.
- **Segundo plano:** o `ConexaoTempoReal` desliga o WebSocket 10 s depois que o app sai da frente. A exceção é uma chamada em andamento. Isso foi decidido assim para não ter serviço permanente (#15 do legado, FC-609) e contar com o push.
- **Consequência hoje, sem FCM:** com o app em segundo plano ou fechado, **nada chega**. Nenhuma notificação de mensagem, e **nenhuma chamada toca**. Só ao voltar para o app tudo sincroniza (`SyncManager.ressincronizar`).

### No servidor
- **Só existe push pelo FCM:** `enviarPush` (`fcm.ts`) manda uma mensagem só de dados com `{titulo, mensagem, conversa}`.
- **Quando manda:** em mensagem nova, para quem tem `token_fcm`, não arquivou a conversa e **não tem nenhum socket aberto** (`usuarioConectado`, S18).
- **Sem FCM configurado** (parâmetros `fcm_*` vazios): o servidor só avisa no log, e o push falha calado.
- **Chamadas não têm push nenhum** (S1). Hoje, o celular só toca se o WebSocket estiver aberto.
- **Não existe outro canal:** nem UnifiedPush/WebPush padrão, nem endpoint de dispositivo além do `token_fcm`.

### No web
- Usa o Firebase no navegador (`services/firebase.ts`) para o push do service worker.
- O projeto Firebase é um só por servidor: a conta de serviço está nos `parametros`.

## O que "funcionar sem FCM" precisa cobrir

| Situação | Hoje sem FCM | Precisa |
|---|---|---|
| App na frente | ✅ funciona | — |
| App em segundo plano (minutos) | ❌ nada chega | mensagens e chamadas |
| App fechado, tela apagada (Doze) | ❌ nada chega | mensagens; chamadas, se for requisito |
| Aparelho sem Google Play (Huawei, sem GApps) | ❌ e o FCM nem existe ali | o mesmo de cima |
| Build sem `google-services.json` | ✅ | continuar assim |

## Opções

### A. Conexão própria em segundo plano (serviço em primeiro plano)
- **Como funciona:**
  - mantém o WebSocket aberto com o app fora da frente, num serviço com **notificação fixa** ("Conectado ao Conversa"), que o Android exige;
  - as mensagens chegam pelo WS 2 e as chamadas pelo WS 51, e o app já trata os dois: a notificação de mensagem e a tela cheia de chamada existem;
  - a presença manda o WS 63 com `ativo:false`, para os outros verem "ausente" (PRE-02).
- **Vantagens:**
  - não depende de Google nem de mudança no servidor;
  - **as chamadas tocam**.
- **Custos e riscos:**
  - **Bateria:** um socket aberto o dia todo. Dá para reduzir o keepalive, mas não sai de graça.
  - **Doze** (tela apagada): o Android corta a rede dos apps que não estão isentos da otimização de bateria. Será preciso pedir a isenção (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), como fazem os apps de mensagem sem Google (Conversations, Molly). **Ainda precisa ser medido no Galaxy A25**, porque cada fabricante fecha mais ou menos.
  - **Tipo do serviço no Android 14+:**
    - `dataSync` não serve: o Android 15 limita a 6 h por dia;
    - `remoteMessaging` não se encaixa exatamente;
    - o caminho é `specialUse`, que **na Play Store** exige justificar, e o Google pode recusar.
  - **Reverte a decisão** "sem serviço permanente" (FC-609, AND-07), mas só quando não houver FCM.

### B. UnifiedPush (push aberto, sem Google)
- **Como funciona:** o aparelho tem um app "distribuidor" (por exemplo o ntfy), que mantém **uma** conexão para todos os apps. O servidor do Conversa faz um POST HTTP no endereço que o aparelho registrou. Pode usar um ntfy próprio no `docker-compose`.
- **Vantagens:**
  - padrão aberto;
  - uma conexão para vários apps;
  - sem Google.
- **Custos:**
  - **mudança no servidor:** registrar o endereço por dispositivo e mandar o POST, nas mensagens e também nas chamadas (S1);
  - quem usa precisa **instalar o app distribuidor**;
  - o distribuidor sofre do mesmo problema de Doze e bateria.
- **Sentido:** vale para quem já usa UnifiedPush. Para uma empresa, é um passo a mais de instalação.

### C. Sincronização periódica (WorkManager)
- **Como funciona:** a cada 15 min, no mínimo, e mais espaçado em Doze, busca mensagens novas e notifica.
- **Vantagens:**
  - sem serviço permanente;
  - sem mudança no servidor;
  - bateria baixa;
  - pouco código, porque o `SyncManager` e o notificador já existem.
- **Custos:**
  - mensagens com **até 15 min ou mais** de atraso;
  - **chamadas impossíveis**.
- **Sentido:** serve como **rede de segurança**, não como solução.

### D. FCM opcional, ligado em tempo de execução pelo servidor
- **Como funciona:** o APK leva a biblioteca do Firebase, mas **não** o `google-services.json`. Depois do login, o app lê do servidor as opções do cliente Firebase (app id, chave, projeto, sender id). Se vierem, chama `FirebaseApp.initializeApp(contexto, opções)` e registra o token (`PATCH /dispositivo {token_fcm}`). Se não vierem, ou se o aparelho não tiver Google Play, segue sem push.
- **Vantagens:**
  - **um único APK** serve qualquer instalação do servidor, cada uma com o seu projeto Firebase ou sem nenhum;
  - casa com o modelo atual, em que a conta de serviço fica nos `parametros` do servidor.
- **Custos:**
  - **mudança no servidor:** novos parâmetros públicos do cliente Android, por exemplo `fcm_android_app_id`, `fcm_api_key` e `fcm_sender_id`, expostos em `GET /recursos` ou numa rota de configuração do cliente;
  - **S1:** o push de chamada continua necessário para tocar com o app fechado;
  - em aparelho sem Google Play, cai em A ou C do mesmo jeito.
- **Variação:** dois sabores de build, `comFirebase` e `livre`, este sem nenhuma biblioteca do Google (para F-Droid ou para quem não quer Google). Só se houver essa exigência.

## Recomendação (para decidir)
1. **D + A + C**, nesta ordem de preferência em tempo de execução:
   - **Com FCM**, quando o servidor configurou e o aparelho tem Google Play: push. Sem serviço permanente e sem custo de bateria.
   - **Sem FCM:** "Manter conectado em segundo plano" (A), uma chave em Configurações › Notificações, ligada por padrão quando não há FCM. Mostra a notificação fixa e pede a isenção de bateria.
   - **Sempre:** a sincronização periódica (C), como rede de segurança, para quando o sistema matar o processo.
2. **UnifiedPush (B) só se houver pedido**, porque exige mudança no servidor e um app a mais no aparelho.

## Perguntas para a decisão
1. **Chamadas com o app fechado e sem FCM** são requisito? Se sim, A é obrigatória: só uma conexão aberta faz tocar sem push.
2. A **notificação fixa** "Conectado ao Conversa" é aceitável quando não houver FCM?
3. **Distribuição:** Play Store, APK direto ou loja interna? O `specialUse` e o pedido de isenção de bateria têm regras na Play.
4. **Firebase:** um projeto por servidor, configurado em tempo de execução (D), ou um projeto fixo dentro do APK?
5. O **servidor** pode receber as mudanças? Seriam:
   - expor a configuração do cliente Firebase;
   - push de chamada (S1);
   - push por aparelho (S2);
   - push mesmo com socket "ausente" (S18), que importa se A ficar ligada junto com o FCM.
6. **UnifiedPush:** entra agora, depois ou nunca?

## Pendências técnicas (independem da escolha)
- [ ] Garantir no CI que o app compila e roda **sem** `google-services.json`, o que já é verdade hoje, e que a falta de Google Play não derruba nada.
- [ ] Uma interface `CanalDePush` no `core:data`, com implementações `SemPush` (hoje), `FcmPush` (D) e `ConexaoEmSegundoPlano` (A). O resto do app só vê "chegou algo, sincronize". O `SyncManager` e o `NotificadorMensagens` já fazem o resto.
- [ ] Mostrar em Configurações › Notificações **como** este aparelho recebe avisos ("Push do Google", "Conexão própria", "Só com o app aberto"), com o que fazer em cada caso. Hoje a pessoa não tem como saber que nada chega com o app fechado.
- [ ] Medir no Galaxy A25, com A ligada: atraso das mensagens com a tela apagada (Doze forçado: `adb shell dumpsys deviceidle force-idle`) e bateria em 8 h.

## Consequências (se a recomendação for aceita)
- O 5.1/5.2 (Firebase) deixa de estar ⛔ "esperando o projeto". Vira "FCM quando o servidor tiver", sem bloquear nada.
- O 5.7 ("sem serviço permanente") ganha uma exceção controlada: a conexão em segundo plano, só sem FCM e com a chave ligada.
- No servidor, entram as pendências novas S23 (configuração do cliente Firebase por servidor) e, se B for escolhida, S24 (UnifiedPush).
