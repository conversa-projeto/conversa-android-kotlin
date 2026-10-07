# 💬 Documentação Visual - Sistema de Mensagens

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Sistema](#arquitetura-do-sistema)
3. [Envio de Mensagem de Texto](#envio-de-mensagem-de-texto)
4. [Envio de Mensagem com Anexo](#envio-de-mensagem-com-anexo)
5. [Recebimento de Mensagens](#recebimento-de-mensagens)
6. [Sistema de Status](#sistema-de-status)
7. [Sincronização de Mensagens](#sincronização-de-mensagens)
8. [Tipos de Conteúdo](#tipos-de-conteúdo)
9. [Sistema de Anexos](#sistema-de-anexos)
10. [WebSocket - Eventos em Tempo Real](#websocket---eventos-em-tempo-real)
11. [Casos de Erro e Recuperação](#casos-de-erro-e-recuperação)

---

## 🎯 Visão Geral

O sistema de mensagens utiliza **3 camadas principais**:

```
┌─────────────────────────────────────────────────────────────┐
│                  SISTEMA DE MENSAGENS                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  [1] API REST          → CRUD de mensagens                  │
│      PUT /mensagem     → Enviar mensagem                    │
│      GET /mensagens    → Buscar mensagens                   │
│      GET /mensagem/    → Marcar como visualizada            │
│          visualizar                                         │
│      GET /mensagem/    → Status de mensagens                │
│          status                                             │
│      GET /mensagens/   → Verificar novas mensagens          │
│          novas                                              │
│                                                             │
│  [2] WebSocket         → Notificações em tempo real         │
│      NovaMensagem      → Notifica nova mensagem             │
│      AtualizacaoStatus → Status mudou (recebida/vista)      │
│                                                             │
│  [3] Cache Local       → Banco de dados Room                │
│      - Mensagens       → Armazenadas localmente             │
│      - Conteúdos       → Texto, imagens, arquivos           │
│      - Status          → Enviada, recebida, visualizada     │
│      - Sincronização   → Incremental com servidor           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**IMPORTANTE:** 
- Mensagens são armazenadas **localmente** primeiro (ID negativo)
- Envio ao servidor é **assíncrono**
- UI atualiza **imediatamente** (otimistic update)
- WebSocket notifica **apenas outros participantes**

---

## 🏗️ Arquitetura do Sistema

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Cliente A  │         │   SERVIDOR   │         │   Cliente B  │
│   (Emissor)  │         │              │         │ (Destinatário│
│              │         │              │         │              │
│ ┌──────────┐ │         │ ┌──────────┐ │         │ ┌──────────┐ │
│ │    UI    │ │         │ │   REST   │ │         │ │    UI    │ │
│ │          │ │         │ │   API    │ │         │ │          │ │
│ └────┬─────┘ │         │ └────┬─────┘ │         │ └────▲─────┘ │
│      │       │         │      │       │         │      │       │
│      ▼       │         │      ▼       │         │      │       │
│ ┌──────────┐ │  HTTPS  │ ┌──────────┐ │         │ ┌────┴─────┐ │
│ │ViewModel │◄├─────────┤►│  Horse   │◄├─────────┤►│ViewModel │ │
│ └────┬─────┘ │         │ │Framework │ │         │ └────▲─────┘ │
│      │       │         │ └────┬─────┘ │         │      │       │
│      ▼       │         │      │       │         │      │       │
│ ┌──────────┐ │         │      ▼       │         │ ┌────┴─────┐ │
│ │Repository│ │         │ ┌──────────┐ │         │ │Repository│ │
│ └────┬─────┘ │         │ │PostgreSQL│ │         │ └────▲─────┘ │
│      │       │         │ └──────────┘ │         │      │       │
│      ▼       │         │      ▲       │         │      │       │
│ ┌──────────┐ │         │      │       │         │ ┌────┴─────┐ │
│ │   Room   │ │         │ ┌────┴─────┐ │   WSS   │ │WebSocket │ │
│ │(SQLite)  │ │         │ │WebSocket │◄├─────────┤►│  Client  │ │
│ └──────────┘ │         │ │  Server  │ │         │ └──────────┘ │
│              │         │ └──────────┘ │         │              │
└──────────────┘         └──────────────┘         └──────────────┘
     │                                                    ▲
     │                                                    │
     └────────── Notificação em tempo real ──────────────┘
                    (Apenas para outros)
```

---

## 📤 Envio de Mensagem de Texto

### Fluxo Completo - Cenário de Sucesso

```
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  João (A)    │              │   SERVIDOR   │              │  Maria (B)   │
│  Cliente     │              │              │              │  Cliente     │
└──────┬───────┘              └──────┬───────┘              └──────┬───────┘
       │                             │                             │
       │ 1. Usuário digita mensagem  │                             │
       │    "Olá, tudo bem?"         │                             │
       │    [Pressiona Enviar]       │                             │
       │                             │                             │
       │ 2. ViewModel captura        │                             │
       │    sendMessage()            │                             │
       │    ├─ Valida texto          │                             │
       │    └─ Chama Repository      │                             │
       │                             │                             │
       │ 3. Repository processa      │                             │
       │    ├─ Gera ID local (-1)    │                             │
       │    ├─ Timestamp atual       │                             │
       │    ├─ Status: enviando      │                             │
       │    └─ Salva no Room         │                             │
       │       (banco local)         │                             │
       │                             │                             │
       │ 4. UI atualiza IMEDIATAMENTE│                             │
       │    ┌─────────────────────┐  │                             │
       │    │ Olá, tudo bem?      │  │                             │
       │    │ [🕐 Enviando...]    │  │                             │
       │    └─────────────────────┘  │                             │
       │                             │                             │
       │ 5. Repository envia ao      │                             │
       │    servidor (background)    │                             │
       ├─PUT /mensagem───────────────>│                             │
       │ Body: {                     │                             │
       │   conversa_id: 1,           │                             │
       │   conteudos: [              │                             │
       │     {                       │                             │
       │       tipo: 1,  // Texto    │                             │
       │       ordem: 1,             │                             │
       │       conteudo: "Olá..."   │                             │
       │     }                       │                             │
       │   ]                         │                             │
       │ }                           │                             │
       │                             │                             │
       │                             │ 6. Servidor valida          │
       │                             │    - JWT token              │
       │                             │    - Permissões             │
       │                             │    - Dados                  │
       │                             │                             │
       │                             │ 7. Salva no PostgreSQL      │
       │                             │    - Gera ID real: 150      │
       │                             │    - Timestamp servidor     │
       │                             │    - conversa_id            │
       │                             │    - usuario_id             │
       │                             │                             │
       │ 8. Resposta com ID real     │                             │
       │<─────Response(200)───────────┤                             │
       │ {                           │                             │
       │   id: 150,                  │                             │
       │   inserida: "2025-10-21...", │                             │
       │   alterada: "2025-10-21..."  │                             │
       │ }                           │                             │
       │                             │                             │
       │ 9. Repository atualiza Room │                             │
       │    - ID: -1 → 150           │                             │
       │    - Status: enviada ✓      │                             │
       │                             │                             │
       │ 10. UI atualiza status      │                             │
       │    ┌─────────────────────┐  │                             │
       │    │ Olá, tudo bem?      │  │                             │
       │    │ [✓ Enviada]         │  │                             │
       │    └─────────────────────┘  │                             │
       │                             │                             │
       │                             │ 11. Busca destinatários     │
       │                             │     da conversa             │
       │                             │     - Maria (ID=2)          │
       │                             │                             │
       │                             │ 12. Envia via WebSocket     │
       │                             ├─WS: NovaMensagem───────────>│
       │                             │ {                           │
       │                             │   tipo: 2,                  │
       │                             │   titulo: "João",           │
       │                             │   mensagem: "Olá, tudo bem?"│
       │                             │ }                           │
       │                             │                             │
       │                             │                      13. Maria recebe
       │                             │                          via WebSocket
       │                             │                          ├─ Event handler
       │                             │                          └─ Tipo = 2
       │                             │                             │
       │                             │                      14. Sincroniza
       │                             │<─GET /mensagens?────────────┤
       │                             │  conversa=1&                │
       │                             │  mensagemreferencia=0&      │
       │                             │  mensagensprevias=50        │
       │                             │                             │
       │                             ├─Response(200)───────────────>│
       │                             │ [                           │
       │                             │   {                         │
       │                             │     id: 150,                │
       │                             │     remetente: "João",      │
       │                             │     conversa_id: 1,         │
       │                             │     inserida: "...",        │
       │                             │     conteudos: [...]        │
       │                             │   }                         │
       │                             │ ]                           │
       │                             │                             │
       │                             │                      15. Salva no Room
       │                             │                          (banco local)
       │                             │                             │
       │                             │                      16. UI atualiza
       │                             │                          ┌──────────────┐
       │                             │                          │ João:        │
       │                             │                          │ Olá, tudo... │
       │                             │                          └──────────────┘
       │                             │                             │
       │                             │                      17. Marca recebida
       │                             │<─GET /mensagem/─────────────┤
       │                             │  visualizar?                │
       │                             │  conversa=1&                │
       │                             │  mensagem=150               │
       │                             │                             │
       │                             │ 18. Atualiza status         │
       │                             │     no PostgreSQL           │
       │                             │     recebida = true         │
       │                             │                             │
       │                             ├─Response(200)───────────────>│
       │                             │                             │
       │                             │ 19. Notifica João           │
       │<─WS: AtualizacaoStatus──────┤                             │
       │ {                           │                             │
       │   tipo: 3,                  │                             │
       │   grupo: 1,                 │                             │
       │   mensagens: "150"          │                             │
       │ }                           │                             │
       │                             │                             │
       │ 20. João busca status       │                             │
       ├─GET /mensagem/status────────>│                             │
       │  ?conversa=1&               │                             │
       │   mensagem=150              │                             │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │ [                           │                             │
       │   {                         │                             │
       │     mensagem_id: 150,       │                             │
       │     recebida: true,  ✓      │                             │
       │     visualizada: false      │                             │
       │   }                         │                             │
       │ ]                           │                             │
       │                             │                             │
       │ 21. UI atualiza indicador   │                             │
       │    ┌─────────────────────┐  │                             │
       │    │ Olá, tudo bem?      │  │                             │
       │    │ [✓✓ Recebida]       │  │                             │
       │    └─────────────────────┘  │                             │
       └─────────────────────────────┴─────────────────────────────┘
```

### Timeline Visual

```
JOÃO (Emissor)                    SERVIDOR                      MARIA (Destinatário)
═══════════════════════════════════════════════════════════════════════════════════

t=0ms    ┌───────────┐
         │UI: Digite │
         │"Olá..."   │
         │[Enviar] ◄─┘
         └───────────┘

t=1ms    ┌───────────┐
         │UI: Mostra │
         │Enviando..│
         └───────────┘
            ↓
t=5ms    [Room DB]
         Salva local
         ID = -1
            ↓
t=10ms   ──────────────────────────→  [Recebe PUT]
                                      [Valida JWT]
                                      
t=50ms                                [PostgreSQL]
                                      Salva ID=150
                                      
t=60ms   ←──────────────────────────  [Response]
                                      {id: 150}
         
t=61ms   [Room DB]
         Atualiza
         -1 → 150
         
t=62ms   ┌───────────┐
         │UI: ✓      │
         │Enviada    │
         └───────────┘
         
t=70ms                                [WebSocket]                     
                                      ───────────→   [Event] NovaMensagem
                                                     
t=75ms                                               ┌───────────┐
                                                     │Notificação│
                                                     │"João: Olá"│
                                                     └───────────┘
                                                     
t=80ms                                [GET]  ←────────────────────
                                      /mensagens
                                      
t=100ms                               [Response] ──→  [Room DB]
                                      [...msgs...]    Salva local
                                      
t=105ms                                                ┌───────────┐
                                                       │UI: Mostra │
                                                       │Mensagem   │
                                                       └───────────┘
                                                       
t=110ms                               [GET]  ←────────────────────
                                      /visualizar
                                      
t=120ms                               [Update DB]
                                      recebida=true
                                      
t=125ms  [WS Event] ←────────────────
         AtualizacaoStatus
         
t=130ms  ┌───────────┐
         │UI: ✓✓     │
         │Recebida   │
         └───────────┘
```

---

## 📸 Envio de Mensagem com Anexo

### Fluxo Completo - Imagem

```
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  João (A)    │              │   SERVIDOR   │              │  Maria (B)   │
└──────┬───────┘              └──────┬───────┘              └──────┬───────┘
       │                             │                             │
       │ 1. Usuário seleciona imagem │                             │
       │    [📷 Galeria]             │                             │
       │    foto.jpg (1.5 MB)        │                             │
       │                             │                             │
       │ 2. ViewModel prepara upload │                             │
       │    ├─ Lê arquivo            │                             │
       │    ├─ Calcula SHA256        │                             │
       │    │  hash = "a3f2e8..."    │                             │
       │    └─ Chama Repository      │                             │
       │                             │                             │
       │ 3. Verifica se já existe    │                             │
       ├─GET /anexo/existe───────────>│                             │
       │  ?identificador=a3f2e8...   │                             │
       │                             │                             │
       │                             │ 4. Busca no banco           │
       │                             │    SELECT * FROM anexos     │
       │                             │    WHERE identificador=...  │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │ {                           │                             │
       │   existe: false             │                             │
       │ }                           │                             │
       │                             │                             │
       │ 5. Faz upload do arquivo    │                             │
       ├─PUT /anexo──────────────────>│                             │
       │ Query:                      │                             │
       │   tipo=2  (imagem)          │                             │
       │   nome=foto.jpg             │                             │
       │   extensao=jpg              │                             │
       │ Header:                     │                             │
       │   Content-Type:             │                             │
       │   application/octet-stream  │                             │
       │ Body:                       │                             │
       │   [bytes da imagem]         │                             │
       │   [1.5 MB]                  │                             │
       │                             │                             │
       │                             │ 6. Recebe arquivo           │
       │                             │    ├─ Calcula SHA256        │
       │                             │    ├─ Valida hash           │
       │                             │    ├─ Salva no disco        │
       │                             │    │  /anexos/a3/f2/e8...   │
       │                             │    └─ Insere no banco       │
       │                             │       INSERT INTO anexos    │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │ {                           │                             │
       │   id: 42,                   │                             │
       │   identificador: "a3f2e8...",│                             │
       │   tipo: 2,                  │                             │
       │   tamanho: 1572864          │                             │
       │ }                           │                             │
       │                             │                             │
       │ 7. Envia mensagem com       │                             │
       │    identificador do anexo   │                             │
       ├─PUT /mensagem───────────────>│                             │
       │ Body: {                     │                             │
       │   conversa_id: 1,           │                             │
       │   conteudos: [              │                             │
       │     {                       │                             │
       │       tipo: 2,  // Imagem   │                             │
       │       ordem: 1,             │                             │
       │       conteudo: "a3f2e8..." │ ← Hash SHA256               │
       │     }                       │                             │
       │   ]                         │                             │
       │ }                           │                             │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │ {                           │                             │
       │   id: 151,                  │                             │
       │   inserida: "...",          │                             │
       │   alterada: "..."           │                             │
       │ }                           │                             │
       │                             │                             │
       │                             │ 8. WebSocket → Maria        │
       │                             ├─WS: NovaMensagem───────────>│
       │                             │ {                           │
       │                             │   tipo: 2,                  │
       │                             │   titulo: "João",           │
       │                             │   mensagem: "📷 Imagem"     │
       │                             │ }                           │
       │                             │                             │
       │                             │                      9. Maria sincroniza
       │                             │<─GET /mensagens─────────────┤
       │                             │                             │
       │                             ├─Response(200)───────────────>│
       │                             │ [                           │
       │                             │   {                         │
       │                             │     id: 151,                │
       │                             │     conteudos: [            │
       │                             │       {                     │
       │                             │         tipo: 2,            │
       │                             │         conteudo: "a3f2e8.."│ ← Hash
       │                             │       }                     │
       │                             │     ]                       │
       │                             │   }                         │
       │                             │ ]                           │
       │                             │                             │
       │                             │                      10. Download anexo
       │                             │<─GET /anexo─────────────────┤
       │                             │  ?identificador=a3f2e8...   │
       │                             │                             │
       │                             │ 11. Lê do disco             │
       │                             │     /anexos/a3/f2/e8...     │
       │                             │                             │
       │                             ├─Response(200)───────────────>│
       │                             │ Body: [bytes da imagem]     │
       │                             │                             │
       │                             │                      12. Salva local
       │                             │                          /cache/images/
       │                             │                          a3f2e8.jpg
       │                             │                             │
       │                             │                      13. UI exibe
       │                             │                          ┌──────────┐
       │                             │                          │ João:    │
       │                             │                          │ [🖼️ IMG] │
       │                             │                          └──────────┘
       └─────────────────────────────┴─────────────────────────────────────┘
```

### Otimização - Evitar Reenvio

```
┌──────────────────────────────────────────────────────────────┐
│        OTIMIZAÇÃO: ARQUIVO JÁ EXISTE NO SERVIDOR             │
└──────────────────────────────────────────────────────────────┘

Cliente A enviou foto.jpg (hash: a3f2e8...)
Cliente B quer enviar a MESMA foto.jpg

Cliente B                               Servidor
   │                                       │
   │ 1. Seleciona foto.jpg                 │
   │    Calcula hash = a3f2e8...           │
   │                                       │
   │ 2. Verifica se existe                 │
   ├─GET /anexo/existe───────────────────>│
   │  ?identificador=a3f2e8...             │
   │                                       │
   │                     3. Busca no banco │
   │                        SELECT ... ✓   │
   │                        EXISTS!        │
   │                                       │
   │<─Response(200)────────────────────────┤
   │ {                                     │
   │   existe: true  ✓✓✓                   │
   │ }                                     │
   │                                       │
   │ 4. NÃO faz upload!                    │
   │    Pula para enviar mensagem          │
   │                                       │
   ├─PUT /mensagem────────────────────────>│
   │ Body: {                               │
   │   conteudos: [{                       │
   │     tipo: 2,                          │
   │     conteudo: "a3f2e8..."  ← Já existe│
   │   }]                                  │
   │ }                                     │
   │                                       │
   │ ✅ Economizou 1.5 MB de upload!       │
   └───────────────────────────────────────┘
```

---

## 📥 Recebimento de Mensagens

### Fluxo com WebSocket + Sincronização

```
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  Maria (B)   │              │   SERVIDOR   │              │  João (A)    │
│ (App aberto) │              │              │              │  (Enviou)    │
└──────┬───────┘              └──────┬───────┘              └──────┬───────┘
       │                             │                             │
       │ [WebSocket conectado]       │                             │
       │<═══════════════════════════>│                             │
       │                             │                             │
       │                             │   João envia mensagem       │
       │                             │<─PUT /mensagem──────────────┤
       │                             │                             │
       │                             │ Salva no banco              │
       │                             │ ID = 152                    │
       │                             │                             │
       │ 1. Recebe via WebSocket     │                             │
       │<─WS: NovaMensagem───────────┤                             │
       │ {                           │                             │
       │   tipo: 2,                  │                             │
       │   titulo: "João",           │                             │
       │   mensagem: "Olá Maria!"    │                             │
       │ }                           │                             │
       │                             │                             │
       │ 2. Event handler processa   │                             │
       │    when (tipo) {            │                             │
       │      2 -> {                 │                             │
       │        // Nova mensagem     │                             │
       │        sincronizar()        │                             │
       │      }                      │                             │
       │    }                        │                             │
       │                             │                             │
       │ 3. ViewModel chama          │                             │
       │    sincronizarMensagens()   │                             │
       │                             │                             │
       │ 4. Busca última msg local   │                             │
       │    SELECT MAX(id)           │                             │
       │    FROM mensagens           │                             │
       │    WHERE conversa_id = 1    │                             │
       │    = 145                    │                             │
       │                             │                             │
       │ 5. Requisita novas mensagens│                             │
       ├─GET /mensagens──────────────>│                             │
       │  ?conversa=1&               │                             │
       │   mensagemreferencia=145&   │ ← Última que tem            │
       │   mensagensprevias=0&       │ ← Não quer antigas          │
       │   mensagensseguintes=50     │ ← Quer novas                │
       │                             │                             │
       │                             │ 6. Busca no PostgreSQL      │
       │                             │    SELECT * FROM mensagens  │
       │                             │    WHERE conversa_id = 1    │
       │                             │    AND id > 145             │
       │                             │    LIMIT 50                 │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │ [                           │                             │
       │   {                         │                             │
       │     id: 152,                │                             │
       │     remetente_id: 1,        │                             │
       │     remetente: "João",      │                             │
       │     conversa_id: 1,         │                             │
       │     inserida: "2025-10...", │                             │
       │     recebida: false,        │                             │
       │     visualizada: false,     │                             │
       │     conteudos: [            │                             │
       │       {                     │                             │
       │         tipo: 1,            │                             │
       │         conteudo: "Olá..."  │                             │
       │       }                     │                             │
       │     ]                       │                             │
       │   }                         │                             │
       │ ]                           │                             │
       │                             │                             │
       │ 7. Salva no Room (local)    │                             │
       │    INSERT INTO mensagens    │                             │
       │    INSERT INTO conteudos    │                             │
       │                             │                             │
       │ 8. LiveData/Flow notifica UI│                             │
       │    ┌─────────────────────┐  │                             │
       │    │ [Conversa atualiza] │  │                             │
       │    │ João:               │  │                             │
       │    │ Olá Maria!          │  │                             │
       │    └─────────────────────┘  │                             │
       │                             │                             │
       │ 9. Marca como recebida      │                             │
       ├─GET /mensagem/visualizar────>│                             │
       │  ?conversa=1&               │                             │
       │   mensagem=152              │                             │
       │                             │                             │
       │                             │ 10. Atualiza status         │
       │                             │     UPDATE mensagem_usuario │
       │                             │     SET recebida = true     │
       │                             │     WHERE mensagem_id = 152 │
       │                             │     AND usuario_id = 2      │
       │                             │                             │
       │<─Response(200)───────────────┤                             │
       │                             │                             │
       │                             │ 11. Notifica João           │
       │                             ├─WS: AtualizacaoStatus──────>│
       │                             │ {                           │
       │                             │   tipo: 3,                  │
       │                             │   grupo: 1,                 │
       │                             │   mensagens: "152"          │
       │                             │ }                           │
       └─────────────────────────────┴─────────────────────────────┘
```

### Recebimento - App em Background

```
┌──────────────┐              ┌──────────────┐              ┌──────────────┐
│  Maria (B)   │              │   SERVIDOR   │              │  João (A)    │
│(App fechado) │              │              │              │              │
└──────┬───────┘              └──────┬───────┘              └──────┬───────┘
       │                             │                             │
       │ [WebSocket desconectado] ✗  │                             │
       │                             │                             │
       │                             │   João envia mensagem       │
       │                             │<─PUT /mensagem──────────────┤
       │                             │                             │
       │                             │ 1. Salva no banco           │
       │                             │                             │
       │                             │ 2. Tenta WebSocket          │
       │                             │    Maria offline ✗          │
       │                             │                             │
       │                             │ 3. Busca token FCM          │
       │                             │    SELECT token_fcm         │
       │                             │    FROM dispositivos        │
       │                             │    WHERE usuario_id = 2     │
       │                             │                             │
       │                             │ 4. Envia FCM Push           │
       │                             ├─[Firebase]──────────────────┐
       │                             │                             │
       │                             │                             │
       │ 5. Recebe notificação push  │                             │
       │    do Firebase              │                             │
       │<────────────────────────────┴─────────────────────────────┘
       │                             
       │ ┌─────────────────────────┐ 
       │ │ 🔔 João                 │ 
       │ │ Olá Maria!              │ 
       │ └─────────────────────────┘ 
       │                             
       │ 6. Usuário toca notificação 
       │    App abre                 
       │                             
       │ 7. onResume()               
       │    ├─ Conecta WebSocket     
       │    └─ Sincroniza mensagens  
       │                             
       ├─GET /mensagens──────────────>│
       │                             │
       │<─Response(200)───────────────┤
       │ [... mensagens novas ...]   │
       │                             │
       │ 8. UI mostra mensagens      │
       │    ┌─────────────────────┐  │
       │    │ João:               │  │
       │    │ Olá Maria!          │  │
       │    └─────────────────────┘  │
       └─────────────────────────────┘
```

---

## 📊 Sistema de Status

### Estados de Uma Mensagem

```
┌─────────────────────────────────────────────────────────────┐
│                 ESTADOS DE UMA MENSAGEM                     │
└─────────────────────────────────────────────────────────────┘

Para CADA destinatário, uma mensagem tem 3 estados independentes:

1. RECEBIDA    (recebida: boolean)
2. VISUALIZADA (visualizada: boolean)
3. REPRODUZIDA (reproduzida: boolean) ← Apenas para áudio

┌─────────────────────────────────────────────────────────────┐
│                     FLUXO DE ESTADOS                        │
└─────────────────────────────────────────────────────────────┘

Emissor         ┌──────────┐
   ├──────────→ │ Enviando │ Local (ID negativo)
   │            └────┬─────┘
   │                 │
   │                 ▼
   │            ┌──────────┐
   ├──────────→ │ Enviada  │ Servidor confirmou (ID real)
   │            └────┬─────┘ [✓]
   │                 │
   │                 ▼
   │            ┌──────────┐
   ├──────────→ │ Recebida │ Destinatário recebeu
   │            └────┬─────┘ [✓✓]
   │                 │
   │                 ▼
   │            ┌────────────┐
   └──────────→ │Visualizada │ Destinatário viu
                └────────────┘ [✓✓ Azul]
                
                     │ (Se mensagem de áudio)
                     ▼
                ┌────────────┐
                │Reproduzida │ Áudio foi tocado
                └────────────┘
```

### Indicadores Visuais

```
┌─────────────────────────────────────────────────────────────┐
│              INDICADORES NO CHAT (WhatsApp-like)            │
└─────────────────────────────────────────────────────────────┘

Remetente vê:

┌─────────────────────────────┐
│ Olá, tudo bem?              │
│ 🕐 Enviando...              │ ← Ainda não enviou
└─────────────────────────────┘

┌─────────────────────────────┐
│ Olá, tudo bem?              │
│ ✓ 10:30                     │ ← Enviada, não recebida
└─────────────────────────────┘

┌─────────────────────────────┐
│ Olá, tudo bem?              │
│ ✓✓ 10:30                    │ ← Recebida, não visualizada
└─────────────────────────────┘

┌─────────────────────────────┐
│ Olá, tudo bem?              │
│ ✓✓ 10:30                    │ ← Visualizada (checks azuis)
└─────────────────────────────┘
  └─ Azul

Destinatário vê:

┌─────────────────────────────┐
│        Olá, tudo bem?       │
│               10:30         │ ← Sem checks (não é minha msg)
└─────────────────────────────┘
```

### Atualização de Status - Tempo Real

```
[João]                        [Servidor]                      [Maria]
  │                               │                              │
  │ Envia msg (id=150)            │                              │
  ├─────────────────────────────→ │                              │
  │                               │                              │
  │ Status local: Enviada ✓       │                              │
  │                               │                              │
  │                               ├──────────────────────────→   │
  │                               │  WS: NovaMensagem            │
  │                               │                              │
  │                               │                              │
  │                               │  ← GET /mensagens            │
  │                               │  ← GET /visualizar           │
  │                               │                              │
  │                               │  UPDATE recebida=true        │
  │                               │                              │
  │  ← WS: AtualizacaoStatus      │                              │
  │                               │                              │
  │  GET /mensagem/status →       │                              │
  │                               │                              │
  │  ← Response({recebida:true})  │                              │
  │                               │                              │
  │ Status local: Recebida ✓✓     │                              │
  │                               │                              │
  │                               │  (Maria abre conversa)       │
  │                               │                              │
  │                               │  ← GET /visualizar           │
  │                               │                              │
  │                               │  UPDATE visualizada=true     │
  │                               │                              │
  │  ← WS: AtualizacaoStatus      │                              │
  │                               │                              │
  │  GET /mensagem/status →       │                              │
  │                               │                              │
  │  ← Response({visualizada:true})│                             │
  │                               │                              │
  │ Status local: Visualizada     │                              │
  │ (Checks azuis) ✓✓             │                              │
  └───────────────────────────────┴──────────────────────────────┘
```

### Batch Update - Otimização

```
Problema: Muitas mensagens = muitas requisições

┌─────────────────────────────────────────────────────────────┐
│            OTIMIZAÇÃO: BATCH STATUS UPDATE                  │
└─────────────────────────────────────────────────────────────┘

Sem otimização (10 mensagens novas):
  GET /mensagem/visualizar?mensagem=141
  GET /mensagem/visualizar?mensagem=142
  GET /mensagem/visualizar?mensagem=143
  ...
  GET /mensagem/visualizar?mensagem=150
  
  = 10 requisições HTTP 😞

Com otimização (batch):
  GET /mensagem/visualizar?mensagem=141,142,143,...,150
  
  = 1 requisição HTTP 😊

Implementação:

// Cliente acumula IDs
val mensagensParaMarcar = mutableListOf<Int>()

mensagens.forEach { msg ->
    if (!msg.visualizada && msg.isVisible()) {
        mensagensParaMarcar.add(msg.id)
    }
}

// Envia em batch
if (mensagensParaMarcar.isNotEmpty()) {
    val ids = mensagensParaMarcar.joinToString(",")
    api.visualizarMensagens(
        conversa = conversaId,
        mensagens = ids
    )
}
```

---

## 🔄 Sincronização de Mensagens

### Sincronização Incremental

```
┌─────────────────────────────────────────────────────────────┐
│          SINCRONIZAÇÃO INCREMENTAL (EFICIENTE)              │
└─────────────────────────────────────────────────────────────┘

Cliente tem localmente:
┌────────────────────────────────────┐
│ Room Database                      │
├────────────────────────────────────┤
│ Mensagem ID: 100                   │
│ Mensagem ID: 101                   │
│ ...                                │
│ Mensagem ID: 145  ← Última         │
└────────────────────────────────────┘

Servidor tem:
┌────────────────────────────────────┐
│ PostgreSQL                         │
├────────────────────────────────────┤
│ Mensagem ID: 100                   │
│ Mensagem ID: 101                   │
│ ...                                │
│ Mensagem ID: 145                   │
│ Mensagem ID: 146  ← Nova           │
│ Mensagem ID: 147  ← Nova           │
│ Mensagem ID: 148  ← Nova           │
└────────────────────────────────────┘

Cliente requisita:
GET /mensagens?conversa=1
    &mensagemreferencia=145      ← Última que tenho
    &mensagensprevias=0          ← Não quero antigas
    &mensagensseguintes=50       ← Quero até 50 novas

Servidor responde apenas:
[
  { id: 146, ... },
  { id: 147, ... },
  { id: 148, ... }
]

✅ Apenas 3 mensagens transmitidas
✅ Banda economizada
✅ Sincronização rápida
```

### Paginação - Histórico Antigo

```
┌─────────────────────────────────────────────────────────────┐
│           CARREGAR MENSAGENS ANTIGAS (SCROLL UP)            │
└─────────────────────────────────────────────────────────────┘

Usuário abre conversa:

1. Cliente busca localmente
   SELECT * FROM mensagens 
   WHERE conversa_id = 1
   ORDER BY id DESC
   LIMIT 50
   
   ┌─────────────────┐
   │ Msg 145         │
   │ Msg 144         │
   │ ...             │
   │ Msg 96          │ ← 50 mensagens
   └─────────────────┘

2. Usuário faz scroll para cima (quer mais antigas)
   
   ┌─────────────────┐
   │ [Loading...]    │ ← Topo da lista
   │ ─────────────── │
   │ Msg 96          │
   │ Msg 95          │
   └─────────────────┘

3. Cliente verifica localmente
   Tem mensagens < 96? 
   - SIM: Carrega do Room
   - NÃO: Busca do servidor

4. Se buscar do servidor:
   GET /mensagens?conversa=1
       &mensagemreferencia=96   ← A partir desta
       &mensagensprevias=50     ← Quero 50 anteriores
       &mensagensseguintes=0    ← Não quero seguintes

5. Servidor responde:
   [
     { id: 95, ... },
     { id: 94, ... },
     ...
     { id: 46, ... }   ← 50 mensagens anteriores
   ]

6. Cliente salva no Room e exibe
   
   ┌─────────────────┐
   │ Msg 95          │
   │ Msg 94          │
   │ ...             │
   │ Msg 46          │
   │ ─────────────── │
   │ Msg 96          │ ← Mensagens que já tinha
   │ Msg 95          │
   └─────────────────┘
```

### Sincronização Bidirecional

```
┌─────────────────────────────────────────────────────────────┐
│        CONTEXTO: CARREGAR MENSAGENS AO REDOR DE UMA         │
└─────────────────────────────────────────────────────────────┘

Caso de uso: Usuário clica em notificação que leva 
             para mensagem específica (ex: reply)

Mensagem de destino: ID = 120

GET /mensagens?conversa=1
    &mensagemreferencia=120      ← Esta mensagem
    &mensagensprevias=25         ← 25 anteriores
    &mensagensseguintes=25       ← 25 posteriores

Servidor responde:
[
  { id: 95 },   ─┐
  { id: 96 },    │
  ...            ├─ 25 anteriores
  { id: 119 },  ─┘
  { id: 120 },  ← Mensagem de referência
  { id: 121 },  ─┐
  { id: 122 },   │
  ...            ├─ 25 posteriores
  { id: 145 }   ─┘
]

UI exibe com scroll na mensagem 120:
┌─────────────────┐
│ Msg 119         │
│ Msg 120  ◄───   │ ← Scroll aqui (highlight)
│ Msg 121         │
└─────────────────┘
```

---

## 📎 Tipos de Conteúdo

### Estrutura de Conteúdo

```
┌─────────────────────────────────────────────────────────────┐
│                  TIPOS DE CONTEÚDO                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  tipo: 1 - TEXTO                                            │
│  ┌────────────────────────────────────┐                    │
│  │ {                                  │                    │
│  │   tipo: 1,                         │                    │
│  │   ordem: 1,                        │                    │
│  │   conteudo: "Olá, tudo bem?"       │ ← Texto plano      │
│  │ }                                  │                    │
│  └────────────────────────────────────┘                    │
│                                                             │
│  tipo: 2 - IMAGEM                                           │
│  ┌────────────────────────────────────┐                    │
│  │ {                                  │                    │
│  │   tipo: 2,                         │                    │
│  │   ordem: 1,                        │                    │
│  │   conteudo: "a3f2e8b1c...",        │ ← SHA256 hash      │
│  │   nome: "foto.jpg",                │                    │
│  │   extensao: "jpg"                  │                    │
│  │ }                                  │                    │
│  └────────────────────────────────────┘                    │
│                                                             │
│  tipo: 3 - ARQUIVO                                          │
│  ┌────────────────────────────────────┐                    │
│  │ {                                  │                    │
│  │   tipo: 3,                         │                    │
│  │   ordem: 1,                        │                    │
│  │   conteudo: "b4d7e2a9c...",        │ ← SHA256 hash      │
│  │   nome: "documento.pdf",           │                    │
│  │   extensao: "pdf"                  │                    │
│  │ }                                  │                    │
│  └────────────────────────────────────┘                    │
│                                                             │
│  tipo: 4 - ÁUDIO (Mensagem de voz)                          │
│  ┌────────────────────────────────────┐                    │
│  │ {                                  │                    │
│  │   tipo: 4,                         │                    │
│  │   ordem: 1,                        │                    │
│  │   conteudo: "c8e3a7f5d...",        │ ← SHA256 hash      │
│  │   nome: "audio.m4a",               │                    │
│  │   extensao: "m4a"                  │                    │
│  │ }                                  │                    │
│  └────────────────────────────────────┘                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Mensagem com Múltiplos Conteúdos

```
┌─────────────────────────────────────────────────────────────┐
│          UMA MENSAGEM PODE TER VÁRIOS CONTEÚDOS             │
└─────────────────────────────────────────────────────────────┘

Exemplo: Texto + 2 Imagens

{
  "conversa_id": 1,
  "conteudos": [
    {
      "tipo": 1,                      ┐
      "ordem": 1,                     │ Conteúdo 1: Texto
      "conteudo": "Olha essas fotos!" │
    },                                ┘
    {
      "tipo": 2,                      ┐
      "ordem": 2,                     │ Conteúdo 2: Imagem 1
      "conteudo": "hash_foto1...",    │
      "nome": "praia.jpg",            │
      "extensao": "jpg"               │
    },                                ┘
    {
      "tipo": 2,                      ┐
      "ordem": 3,                     │ Conteúdo 3: Imagem 2
      "conteudo": "hash_foto2...",    │
      "nome": "por_do_sol.jpg",       │
      "extensao": "jpg"               │
    }                                 ┘
  ]
}

UI renderiza:

┌─────────────────────────────┐
│ João - 10:30                │
│ Olha essas fotos!           │ ← Texto (ordem: 1)
│ ┌──────────┐ ┌──────────┐  │
│ │[🖼️ Praia]│ │[🖼️ Sol  ]│  │ ← Imagens (ordem: 2,3)
│ └──────────┘ └──────────┘  │
└─────────────────────────────┘
```

---

## 📁 Sistema de Anexos

### Fluxo Completo de Anexos

```
┌─────────────────────────────────────────────────────────────┐
│                   UPLOAD DE ANEXO                           │
└─────────────────────────────────────────────────────────────┘

1. PREPARAÇÃO
   ┌────────────────────────────┐
   │ Cliente seleciona arquivo  │
   │ - Lê bytes do arquivo      │
   │ - Calcula SHA256           │
   │ - hash = identificador     │
   └────────────────────────────┘
           │
           ▼
2. VERIFICAÇÃO
   ┌────────────────────────────┐
   │ GET /anexo/existe          │
   │ ?identificador={hash}      │
   │                            │
   │ Se existe: PULA UPLOAD ✓   │
   │ Se não existe: continua    │
   └────────────────────────────┘
           │
           ▼
3. UPLOAD
   ┌────────────────────────────┐
   │ PUT /anexo                 │
   │ Query:                     │
   │   tipo=2                   │
   │   nome=arquivo.jpg         │
   │   extensao=jpg             │
   │ Header:                    │
   │   Content-Type:            │
   │   application/octet-stream │
   │ Body:                      │
   │   [bytes do arquivo]       │
   └────────────────────────────┘
           │
           ▼
4. SERVIDOR PROCESSA
   ┌────────────────────────────┐
   │ - Recebe bytes             │
   │ - Valida tipo/tamanho      │
   │ - Calcula SHA256           │
   │ - Compara com informado    │
   │ - Salva no disco:          │
   │   /anexos/a3/f2/e8b1...    │
   │ - Insere no banco          │
   └────────────────────────────┘
           │
           ▼
5. RESPOSTA
   ┌────────────────────────────┐
   │ {                          │
   │   id: 42,                  │
   │   identificador: "a3f2...",│
   │   tipo: 2,                 │
   │   tamanho: 1048576         │
   │ }                          │
   └────────────────────────────┘
           │
           ▼
6. CLIENTE USA IDENTIFICADOR
   ┌────────────────────────────┐
   │ PUT /mensagem              │
   │ {                          │
   │   conteudos: [{            │
   │     tipo: 2,               │
   │     conteudo: "a3f2..."    │ ← hash
   │   }]                       │
   │ }                          │
   └────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   DOWNLOAD DE ANEXO                         │
└─────────────────────────────────────────────────────────────┘

1. CLIENTE RECEBE MENSAGEM
   ┌────────────────────────────┐
   │ {                          │
   │   conteudos: [{            │
   │     tipo: 2,               │
   │     conteudo: "a3f2..."    │ ← hash do anexo
   │   }]                       │
   │ }                          │
   └────────────────────────────┘
           │
           ▼
2. VERIFICA CACHE LOCAL
   ┌────────────────────────────┐
   │ Existe em:                 │
   │ /cache/images/a3f2.jpg?    │
   │                            │
   │ SIM: Usa arquivo local ✓   │
   │ NÃO: Faz download          │
   └────────────────────────────┘
           │
           ▼
3. DOWNLOAD
   ┌────────────────────────────┐
   │ GET /anexo                 │
   │ ?identificador=a3f2...     │
   │                            │
   │ Response:                  │
   │ Content-Type: image/jpeg   │
   │ Body: [bytes]              │
   └────────────────────────────┘
           │
           ▼
4. SALVA CACHE LOCAL
   ┌────────────────────────────┐
   │ Salva em:                  │
   │ /cache/images/a3f2.jpg     │
   │                            │
   │ Próxima vez: lê do cache   │
   └────────────────────────────┘
           │
           ▼
5. EXIBE NA UI
   ┌────────────────────────────┐
   │ ┌─────────────────────┐    │
   │ │ [🖼️ Imagem carregada]│    │
   │ └─────────────────────┘    │
   └────────────────────────────┘
```

### Gerenciamento de Cache

```
┌─────────────────────────────────────────────────────────────┐
│              ESTRATÉGIA DE CACHE DE ANEXOS                  │
└─────────────────────────────────────────────────────────────┘

ESTRUTURA DE DIRETÓRIOS:
/data/data/com.app/cache/
├── images/
│   ├── a3f2e8b1c...jpg
│   ├── b4d7e2a9c...png
│   └── ...
├── files/
│   ├── c8e3a7f5d...pdf
│   └── ...
└── audio/
    ├── d9f4b8e6a...m4a
    └── ...

POLÍTICA DE LIMPEZA:
┌────────────────────────────────────┐
│ 1. LRU (Least Recently Used)      │
│    Remove arquivos antigos         │
│                                    │
│ 2. Limite de tamanho               │
│    Max: 500 MB                     │
│    Se > 500MB: limpa até 400MB     │
│                                    │
│ 3. Tempo de vida                   │
│    Arquivos > 30 dias: candidatos  │
│    a remoção                       │
│                                    │
│ 4. Manual                          │
│    Usuário pode limpar em          │
│    Configurações > Armazenamento   │
└────────────────────────────────────┘

PRIORIDADE DE DOWNLOAD:
┌────────────────────────────────────┐
│ 1. ALTA: Mensagens visíveis        │
│    Download imediato               │
│                                    │
│ 2. MÉDIA: Próximas mensagens       │
│    Pré-carrega (prefetch)          │
│                                    │
│ 3. BAIXA: Mensagens antigas        │
│    Download sob demanda            │
└────────────────────────────────────┘
```

---

## 🔌 WebSocket - Eventos em Tempo Real

### Tipos de Eventos

```
┌─────────────────────────────────────────────────────────────┐
│             TIPOS DE MENSAGEM WEBSOCKET                     │
├──────────┬──────────────────────────────────────────────────┤
│ Tipo     │ Descrição                                        │
├──────────┼──────────────────────────────────────────────────┤
│ 0 - Erro │ Erro de autenticação ou processamento           │
│ 1 - Login│ Autenticação com token JWT                       │
│ 2 - Nova │ Nova mensagem recebida                           │
│ 3 - Status│ Status de mensagem atualizado                   │
│ 51-55    │ Eventos de chamada (ver doc de chamadas)        │
└──────────┴──────────────────────────────────────────────────┘
```

### Fluxo de Autenticação WebSocket

```
Cliente                                        Servidor
  │                                               │
  │ 1. Conecta WebSocket                          │
  ├──── ws://servidor:porta/ ───────────────────→│
  │                                               │
  │ 2. onOpen() callback                          │
  │    Envia autenticação                         │
  ├─── Message ──────────────────────────────────→│
  │  {                                            │
  │    "tipo": 1,                                 │
  │    "token": "eyJhbGc..."                      │
  │  }                                            │
  │                                               │
  │                            3. Valida JWT      │
  │                               - Decodifica    │
  │                               - Verifica exp  │
  │                               - Extrai userId │
  │                                               │
  │                            4. Se válido:      │
  │                               Associa conexão │
  │                               userId → socket │
  │                               {               │
  │                                 1: socket1,   │
  │                                 2: socket2    │
  │                               }               │
  │                                               │
  │                            5. Se inválido:    │
  │<─── Message ──────────────────────────────────┤
  │  {                                            │
  │    "tipo": 0,                                 │
  │    "message": "Token inválido"                │
  │  }                                            │
  │                                               │
  │<──── Close connection ────────────────────────┤
  │                                               │
  │                                               │
  │ 6. Autenticado! ✓                             │
  │    Aguarda eventos                            │
  │<══════════════════════════════════════════════>│
  │            Conexão persistente                │
  └───────────────────────────────────────────────┘
```

### Event Handlers no Cliente

```kotlin
class WebSocketHandler {
    private var webSocket: WebSocket? = null
    
    fun connect(token: String) {
        val request = Request.Builder()
            .url("ws://servidor:porta/")
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Autentica
                val loginMsg = JSONObject().apply {
                    put("tipo", 1)
                    put("token", token)
                }
                webSocket.send(loginMsg.toString())
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                val tipo = json.getInt("tipo")
                
                when (tipo) {
                    0 -> handleError(json)
                    2 -> handleNovaMensagem(json)
                    3 -> handleAtualizacaoStatus(json)
                    // 51-55: chamadas
                }
            }
            
            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                // Tentar reconectar
                reconnect()
            }
        })
    }
    
    private fun handleNovaMensagem(json: JSONObject) {
        val titulo = json.getString("titulo")
        val mensagem = json.getString("mensagem")
        
        // Notifica UI
        eventBus.post(NovaMensagemEvent(titulo, mensagem))
        
        // Sincroniza mensagens
        sincronizarMensagens()
        
        // Mostra notificação se app em background
        if (!isAppInForeground()) {
            showNotification(titulo, mensagem)
        }
    }
    
    private fun handleAtualizacaoStatus(json: JSONObject) {
        val conversaId = json.getInt("grupo")
        val mensagensIds = json.getString("mensagens")
            .split(",")
            .map { it.toInt() }
        
        // Busca novos status
        buscarStatus(conversaId, mensagensIds)
    }
}
```

---

## ⚠️ Casos de Erro e Recuperação

### Falha no Envio de Mensagem

```
┌─────────────────────────────────────────────────────────────┐
│              CENÁRIO: ERRO AO ENVIAR MENSAGEM               │
└─────────────────────────────────────────────────────────────┘

Cliente                                Servidor
  │                                       │
  │ 1. Usuário envia mensagem             │
  │    "Olá!"                             │
  │                                       │
  │ 2. Salva localmente                   │
  │    ID = -1                            │
  │    Status: Enviando 🕐                │
  │                                       │
  │ 3. UI mostra imediatamente            │
  │    ┌─────────────────┐                │
  │    │ Olá!            │                │
  │    │ 🕐 Enviando...  │                │
  │    └─────────────────┘                │
  │                                       │
  │ 4. Tenta enviar                       │
  ├─PUT /mensagem───────────────────────→ │
  │                                       │
  │                                ✗ Erro │
  │                              (timeout,│
  │                               500, etc│
  │                                       │
  │<──────── Error ────────────────────────┤
  │                                       │
  │ 5. Atualiza status local              │
  │    Status: Erro ⚠️                     │
  │                                       │
  │ 6. UI mostra erro                     │
  │    ┌─────────────────┐                │
  │    │ Olá!            │                │
  │    │ ⚠️ Não enviada  │                │
  │    │ [Tentar novam.] │                │
  │    └─────────────────┘                │
  │                                       │
  │ 7. Usuário toca "Tentar novamente"    │
  │    ou                                 │
  │    Sistema tenta automaticamente      │
  │    após 5 segundos                    │
  │                                       │
  │ 8. Retry                              │
  ├─PUT /mensagem───────────────────────→ │
  │                                       │
  │                                 ✓ OK  │
  │<──────── Response ────────────────────┤
  │ {id: 150}                             │
  │                                       │
  │ 9. Atualiza                           │
  │    -1 → 150                           │
  │    Status: Enviada ✓                  │
  │                                       │
  │ 10. UI atualiza                       │
  │    ┌─────────────────┐                │
  │    │ Olá!            │                │
  │    │ ✓ Enviada       │                │
  │    └─────────────────┘                │
  └───────────────────────────────────────┘
```

### Perda de Conexão WebSocket

```
┌─────────────────────────────────────────────────────────────┐
│          CENÁRIO: WEBSOCKET DESCONECTADO                    │
└─────────────────────────────────────────────────────────────┘

  │ [WebSocket conectado] ✓
  │<══════════════════════════>│
  │                            │
  │                            │
  │     ✗ Conexão perdida      │
  │<════════ DISCONNECT ═══════│
  │                            │
  │ 1. onFailure() detecta     │
  │    - IOException           │
  │    - Timeout               │
  │                            │
  │ 2. Marca como desconectado │
  │    isConnected = false     │
  │                            │
  │ 3. UI mostra indicador     │
  │    "Reconectando..."       │
  │                            │
  │ 4. Exponential backoff     │
  │    Tentativa 1: 1s         │
  │    Tentativa 2: 2s         │
  │    Tentativa 3: 4s         │
  │    Tentativa 4: 8s         │
  │    Tentativa 5: 16s        │
  │    Max: 30s                │
  │                            │
  │ 5. Tenta reconectar        │
  ├────── Connect ────────────→│
  │                            │
  │                      ✓ OK  │
  │<══════ Connected ══════════│
  │                            │
  │ 6. Re-autentica            │
  ├─ Login (JWT) ─────────────→│
  │                            │
  │ 7. Sincroniza mensagens    │
  │    (pode ter perdido       │
  │    notificações)           │
  │                            │
  ├─GET /mensagens/novas──────→│
  │  ?ultima=150               │
  │                            │
  │<─Response───────────────────┤
  │  [msg 151, 152, ...]       │
  │                            │
  │ 8. UI volta ao normal      │
  │    Indicador some          │
  │                            │
  │ [Conectado novamente] ✓    │
  │<══════════════════════════>│
  └────────────────────────────┘
```

### Inconsistência de Dados

```
┌─────────────────────────────────────────────────────────────┐
│     CENÁRIO: BANCO LOCAL FORA DE SINCRONIA                  │
└─────────────────────────────────────────────────────────────┘

Situação: Cliente perdeu várias mensagens

Causa:
- App ficou muito tempo fechado
- WebSocket falhou várias vezes
- Servidor estava indisponível

Solução: Sincronização completa

Cliente                                Servidor
  │                                       │
  │ 1. Detecta inconsistência             │
  │    - Muitas mensagens perdidas        │
  │    - Ou timeout muito longo           │
  │                                       │
  │ 2. Pega última mensagem local         │
  │    SELECT MAX(id)                     │
  │    FROM mensagens                     │
  │    WHERE conversa_id = 1              │
  │    = 100                              │
  │                                       │
  │ 3. Requisita TODAS após 100           │
  ├─GET /mensagens───────────────────────→│
  │  ?conversa=1&                         │
  │   mensagemreferencia=100&             │
  │   mensagensprevias=0&                 │
  │   mensagensseguintes=1000  ← Max      │
  │                                       │
  │                                       │
  │<─Response──────────────────────────────┤
  │  [                                    │
  │    {id: 101}, {id: 102}, ...          │
  │    ... (todas até 250)                │
  │  ]                                    │
  │                                       │
  │ 4. Salva todas no Room                │
  │    INSERT INTO mensagens              │
  │    ... (150 mensagens)                │
  │                                       │
  │ 5. UI atualiza                        │
  │    ┌───────────────────────┐          │
  │    │ [Todas msg exibidas]  │          │
  │    │ Msg 101               │          │
  │    │ Msg 102               │          │
  │    │ ...                   │          │
  │    │ Msg 250               │          │
  │    └───────────────────────┘          │
  │                                       │
  │ 6. Sincronizado! ✓                    │
  └───────────────────────────────────────┘
```

---

## 📝 Checklist de Implementação

### Fase 1: API e Repositório

- [ ] Implementar interface da API (Retrofit)
  - [ ] PUT /mensagem
  - [ ] GET /mensagens
  - [ ] GET /mensagem/visualizar
  - [ ] GET /mensagem/status
  - [ ] GET /mensagens/novas
  - [ ] PUT /anexo
  - [ ] GET /anexo
  - [ ] GET /anexo/existe

- [ ] Criar entidades Room
  - [ ] MensagemEntity
  - [ ] ConteudoEntity
  - [ ] Relacionamentos

- [ ] Implementar DAOs
  - [ ] MensagemDao
  - [ ] ConteudoDao
  - [ ] Queries otimizadas

- [ ] Implementar Repository
  - [ ] MensagemRepository
  - [ ] Sincronização
  - [ ] Cache

### Fase 2: Envio de Mensagens

- [ ] Implementar UI de chat
  - [ ] Input de texto
  - [ ] Botão enviar
  - [ ] Lista de mensagens

- [ ] Implementar envio de texto
  - [ ] Otimistic update
  - [ ] ID negativo local
  - [ ] Envio assíncrono
  - [ ] Atualização de ID

- [ ] Tratamento de erros
  - [ ] Retry automático
  - [ ] Indicador visual
  - [ ] Botão "Tentar novamente"

### Fase 3: Recebimento

- [ ] Implementar WebSocket
  - [ ] Conexão
  - [ ] Autenticação
  - [ ] Event handlers
  - [ ] Reconexão

- [ ] Sincronização
  - [ ] Incremental
  - [ ] Completa (fallback)
  - [ ] Background

- [ ] Notificações
  - [ ] FCM setup
  - [ ] Notificações locais
  - [ ] Deep links

### Fase 4: Anexos

- [ ] Implementar upload
  - [ ] Seleção de arquivo
  - [ ] Cálculo SHA256
  - [ ] Verificação existe
  - [ ] Progress indicator
  - [ ] Envio

- [ ] Implementar download
  - [ ] Cache local
  - [ ] Progress indicator
  - [ ] Retry

- [ ] Gerenciamento de cache
  - [ ] LRU
  - [ ] Limite de tamanho
  - [ ] Limpeza

### Fase 5: Status

- [ ] Implementar indicadores
  - [ ] Enviando
  - [ ] Enviada (✓)
  - [ ] Recebida (✓✓)
  - [ ] Visualizada (✓✓ azul)

- [ ] Atualização em tempo real
  - [ ] WebSocket events
  - [ ] Batch updates
  - [ ] UI updates

### Fase 6: Melhorias

- [ ] Paginação de mensagens
- [ ] Pull to refresh
- [ ] Busca de mensagens
- [ ] Reply/Forward
- [ ] Edição/Deleção
- [ ] Reações (emojis)

---

**Documentação gerada em:** 21/10/2025  
**Versão:** 1.0  
**Foco:** Sistema de Mensagens
