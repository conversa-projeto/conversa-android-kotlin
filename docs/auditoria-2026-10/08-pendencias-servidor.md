# 08 — Pendências no servidor (`conversa`) que afetam o Android

**Data:** 2026-10-06 · **Origem:** leitura de `conversa/src/*.ts` durante a auditoria (detalhes e linhas no doc 01).

> **Nenhuma alteração foi feita no servidor.** Esta lista serve para planejar o trabalho no repositório `conversa`. Itens marcados como **bloqueante** impedem uma funcionalidade P0 do Android.

---

### S1 · **Bloqueante** · Não existe push para chamada

- **Hoje:** `chamadas.ts` notifica só por WebSocket (evento 51). O FCM (`fcm.ts`) só é usado em `notificarNovaMensagemConversa` (`notificacoes.ts`).
- **Efeito:** com o app Android fechado, em segundo plano sem WS ou com o aparelho em Doze, **o telefone não toca**. O web contorna isso com a aba aberta; o Android não pode manter um socket permanente (política do Play, bateria).
- **Proposta:** ao iniciar a chamada (e ao adicionar um usuário), enviar um push **data-only, `android.priority: "high"`, TTL curto (~30 s)** para cada participante sem WS ativo. Payload sugerido:

  ```json
  {"tipo":"chamada","chamada_id":"123","usuario_id":"7","usuario_nome":"Ana","video":"1","criada_em":"<ISO>"}
  ```

  Também é preciso enviar um push de "chamada encerrada/cancelada", para parar o toque em quem recebeu só por push.
- **Compatibilidade com o web:** o service worker hoje lê `{titulo, mensagem, conversa}`. Incluir o campo `tipo` e fazer o SW ignorar o que não reconhece.

### S2 · **Bloqueante** · Entrega do push de mensagem

1. **Sem prioridade Android:** `enviarPush` só define `webpush.headers.Urgency`. É preciso `android: { priority: 'high' }`, porque sem isso o FCM pode atrasar a entrega em Doze.
2. **"Conectado" é por usuário, não por aparelho:** `usuarioConectado(usuario_id)` faz com que, se o usuário tiver **qualquer** WS aberto (por exemplo, o web no PC), o celular **não receba push**.
   - Proposta: enviar push para os dispositivos que não estão com socket aberto. Isso exige associar a conexão WS ao `dispositivo_id`, enviado no login do WS (`{tipo:1, token, dispositivo_id}`).
   - Alternativa: sempre enviar push e deixar o cliente decidir se exibe.
3. **Um só dispositivo por usuário?** A consulta faz `left join dispositivo … d.ativo = true`. Confirmar que o modelo suporta vários aparelhos ativos por usuário (web + celular) e que o login não desativa os outros.

### S3 · Eventos e push sem contexto suficiente

- **WS 2 (nova mensagem):** envia só `{titulo, mensagem}` (`notificarNovaMensagem(usuario_id, titulo, texto)`), **sem `conversa_id` nem `mensagem_id`**. O cliente precisa fazer `GET /mensagens/novas` para descobrir de onde veio.
- **Push:** `{titulo, mensagem, conversa}`, sem `mensagem_id`, `remetente_id`, tipo de conversa ou nome do grupo.
- **Proposta:** incluir `conversa_id`, `mensagem_id`, `remetente_id`, `conversa_tipo` e `conversa_descricao` nos dois. Isso permite MessagingStyle com o nome do grupo, deduplicação e deep link direto. O web pode continuar ignorando os campos extras.
- **Duplicidade:** o doc 01 registra que o WS 2 pode chegar duplicado. Incluir `mensagem_id` permite deduplicar.

### S4 · Segurança

1. **`GET /api/anexo?identificador=`** devolve a URL assinada **sem conferir se o usuário participa de alguma conversa que contém o anexo**. Quem souber ou adivinhar o SHA-256 baixa o arquivo.
2. **MediaMTX (`/webrtc/call-<id>-u-<uid>/whep`)** não exige autenticação nem participação. Os caminhos são previsíveis (ids sequenciais), então é possível **ouvir chamadas alheias**.
   - Proposta: autenticação HTTP do MediaMTX (`authMethod: http` apontando para a API, ou JWT) com um token de chamada emitido em `/chamada/entrar`/`/chamada/iniciar`. Os clientes mandam `Authorization: Bearer` no WHIP/WHEP.
3. **`mensagem_referencia`** não checa o acesso à mensagem referenciada (doc 01 §10.3). Isso permite citar ou encaminhar o conteúdo de conversas de que o usuário não participa.

### S5 · Figurinhas não são servidas pelo servidor de produção

- As animações ficam em `conversa-web/public/figurinhas/**`, mas o `bin/web` (build publicado) não contém a pasta. Ou seja, nem o web de produção as teria se o build não fosse refeito.
- **Para o Android:** embutir no APK (FC-509).
- **Para o servidor:** garantir que o build do web publique `/figurinhas/`. Se o catálogo for crescer, considerar servi-lo pela API.

### S6 · `DELETE /api/conversa` não funciona

- Ele faz `delete from conversa` sem cascade, e `conversa_usuario`/`mensagem` têm FK, o que gera **500**.
- **Proposta:** remover a rota, ou implementar exclusão lógica ou "sair da conversa" (que já existe via `DELETE /conversa/usuario`).

### S7 · Erros 500 com mensagem crua do Postgres

- E-mail duplicado no cadastro, segundo `PUT /sip`, strings acima do limite da coluna (nome 100, login 50, dispositivo 50/15, extensão 10, emoji 10…) e FKs inválidas viram **500** com o texto do Postgres (doc 01 §3, pegadinha 42).
- **Proposta:** validar o tamanho no `esquemas.ts` (`maxLength`) e mapear `unique_violation` → **409** e `foreign_key_violation` → **400/404**, com mensagens em português. O Android passa a mostrar mensagens corretas sem precisar adivinhar.

### S8 · Criação de conversa não atômica

- Criar uma conversa direta ou um grupo exige `PUT /conversa` + N× `PUT /conversa/usuario`. Uma falha no meio deixa uma conversa órfã ou sem participantes (o web e o Android sofrem igual).
- **Proposta:** `PUT /conversa {descricao, tipo, usuarios:[ids]}` transacional; para a direta, `PUT /conversa/direta {usuario_id}`, que devolve a existente ou cria.

### S9 · Marcar como visualizada é uma chamada por mensagem

- `POST /mensagem/visualizar {conversa, mensagem}`. Abrir uma conversa com 200 não lidas gera 200 requisições (no web e no Android).
- **Proposta:** `POST /mensagem/visualizar {conversa, ate_mensagem}` (marca tudo até o id) ou `{conversa, mensagens:[ids]}`.

### S10 · Descobrir recursos do servidor

- O cliente não sabe se a transcrição está configurada (`transcritor_url`), e só administradores leem `/parametros`.
- **Proposta:** `GET /api/recursos` (para qualquer usuário logado) → `{transcricao:boolean, sip:boolean, gravacao:boolean, versao_api:"…"}`.

### S11 · Ambiguidade do `tipo` da chamada

- Para o cliente, `tipo` é áudio/vídeo; na regra automática de status do servidor (`chamadas.ts`, doc 01 §9.4), ele é tratado como 1:1/grupo.
- Além disso, quem liga precisa se incluir em `usuarios`, e iniciar uma chamada só consigo mesmo dá **500 "Chamada não encontrada!"** (`notificacoes.ts:33-35`).
- Ao terminar porque o último participante saiu, **não sai o evento 52**.
- **Proposta:** separar `midia` (1 áudio / 2 vídeo) de `modo` (direta/grupo), incluir o criador automaticamente e emitir o 52 em todo encerramento.

### S12 · Contrato publicado

- **Já existe** OpenAPI: `@elysiajs/openapi` publica a documentação em `/api/docs` e o JSON em `/api/docs/json` (`src/app.ts:49-55`). O Android pode usar esse JSON para validar ou gerar os DTOs.
- **Lacunas:**
  - As **respostas** não têm esquema declarado (só corpo e query têm), então o OpenAPI não descreve o que volta.
  - O WebSocket e o push não estão documentados.
  - Não há `CHANGELOG-API.md` nem versão da API.
- **Proposta:**
  - Declarar `response` nos esquemas das rotas.
  - Um `CHANGELOG-API.md` no servidor.
  - Versão da API no login ou em `/recursos`.

### S13 · JWT sem renovação

- O token vale 12 h e não há refresh nem logout no servidor (doc 01 §2.2). No celular, isso obriga a pedir a senha a cada 12 h, ou a guardá-la (o que o app atual faz, de forma insegura).
- **Proposta:** refresh token (rotativo, por dispositivo, revogável no logout) via `POST /api/token/renovar`. Até lá, o Android só pede login de novo ao receber 401, e **não guarda a senha**.

### S14 · Rate limit do login

- O nginx limita o login a 10 por minuto. Uma rede com NAT compartilhado (empresa) pode bloquear vários usuários juntos.
- Avaliar o limite por login, e não só por IP.

### S15 · Remover participante do grupo 🆕 (2026-10-07)

- `DELETE /api/conversa/usuario?id=` só aceita o **próprio** vínculo (`validarRemocaoConversaUsuario`, `src/autorizacao.ts`: "Regra atual: só auto-remoção. Admin de grupo via conversa.criado_por fica para depois"). O web mostra "Remover" para os outros membros, e o clique sempre dá 403.
- No Android a opção não é oferecida; a tela de membros explica que cada pessoa só pode sair por conta própria.
- **Proposta:** administrador de grupo (ao menos `conversa.criado_por`) pode remover; ou o web esconde o botão até lá.

### S16 · Prévia da conversa sem o tipo do conteúdo 🆕 (2026-10-07)

- Em `GET /conversas`, `ultima_mensagem_texto` vem `""` para arquivo, áudio, gravação e chamada (doc 01 §11.2). O cliente não sabe o que foi: o web mostra "Sem mensagens"; o Android mostra "Anexo ou chamada".
- **Proposta:** incluir `ultima_mensagem_tipo` (tipo do primeiro conteúdo) na lista, para a prévia dizer "Áudio", "Arquivo", "Chamada perdida"…

---

## Prioridade sugerida no servidor

| Ordem | Itens | Por quê |
|---|---|---|
| 1 | S1, S2 | Sem eles, o Android não toca nem notifica de forma confiável |
| 2 | S4 | Segurança (ouvir chamadas e baixar anexos alheios) |
| 3 | S3, S13 | Qualidade das notificações e da sessão no celular |
| 4 | S7, S8, S9, S11 | Robustez para todos os clientes |
| 5 | S5, S6, S10, S12, S14, S15, S16 | Higiene e evolução |
