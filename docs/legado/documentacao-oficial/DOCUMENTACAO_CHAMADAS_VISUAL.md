# 📞 Documentação Visual - Sistema de Chamadas de Áudio

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Sistema](#arquitetura-do-sistema)
3. [Chamada 1:1 (Simples)](#chamada-11-simples)
4. [Chamada em Grupo](#chamada-em-grupo)
5. [Protocolo TCP Detalhado](#protocolo-tcp-detalhado)
6. [Estados da Chamada](#estados-da-chamada)
7. [Mixing de Áudio no Cliente](#mixing-de-áudio-no-cliente)
8. [Casos de Erro e Recuperação](#casos-de-erro-e-recuperação)

---

## 🎯 Visão Geral

O sistema de chamadas de áudio utiliza **3 camadas independentes**:

```
┌────────────────────────────────────────────────────────────┐
│                     SISTEMA DE CHAMADAS                    │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  [1] API REST          → Gerenciamento de chamadas         │
│      /chamada/iniciar  → Criar nova chamada                │
│      /chamada/entrar   → Aceitar chamada                   │
│      /chamada/recusar  → Recusar chamada                   │
│      /chamada/sair     → Sair da chamada                   │
│      /chamada/cancelar → Cancelar chamada                  │
│      /chamada/finalizar→ Encerrar chamada                  │
│                                                            │
│  [2] WebSocket         → Sinalização em tempo real         │
│      ChamadaRecebida   → Notifica novo convite             │
│      UsuarioEntrou     → Alguém aceitou                    │
│      UsuarioRecusou    → Alguém recusou                    │
│      UsuarioSaiu       → Alguém saiu                       │
│      ChamadaFinalizada → Chamada encerrada                 │
│                                                            │
│  [3] TCP Server        → Transmissão de áudio              │
│      Porta 9090        → Streaming PCM 16-bit 44.1kHz      │
│      Retransmissão     → Servidor NÃO faz mixing           │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

**IMPORTANTE:** 
- O servidor TCP **APENAS RETRANSMITE** os pacotes de áudio
- A **MIXAGEM acontece no CLIENTE** ao receber múltiplos streams
- Cada cliente recebe áudio de TODOS os outros participantes

---

## 🏗️ Arquitetura do Sistema

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   Cliente A  │         │   SERVIDOR   │         │   Cliente B  │
│              │         │              │         │              │
│ ┌──────────┐ │         │ ┌──────────┐ │         │ ┌──────────┐ │
│ │   API    │◄├─────────┤►│   REST   │◄├─────────┤►│   API    │ │
│ │  Client  │ │  HTTPS  │ │   API    │ │  HTTPS  │ │  Client  │ │
│ └──────────┘ │         │ └──────────┘ │         │ └──────────┘ │
│              │         │      ▲       │         │              │
│ ┌──────────┐ │         │      │       │         │ ┌──────────┐ │
│ │WebSocket │◄├─────────┤►┌────┴─────┐◄├─────────┤►│WebSocket │ │
│ │  Client  │ │   WSS   │ │WebSocket │ │   WSS   │ │  Client  │ │
│ └──────────┘ │         │ │  Server  │ │         │ └──────────┘ │
│              │         │ └──────────┘ │         │              │
│ ┌──────────┐ │         │              │         │ ┌──────────┐ │
│ │   TCP    │◄├─────────┤►┌──────────┐◄├─────────┤►│   TCP    │ │
│ │  Audio   │ │   TCP   │ │   TCP    │ │   TCP   │ │  Audio   │ │
│ │  Stream  │ │  :9090  │ │  Server  │ │  :9090  │ │  Stream  │ │
│ └──────────┘ │         │ │  :9090   │ │         │ └──────────┘ │
│      ▲       │         │ └────┬─────┘ │         │      ▲       │
│      │       │         │      │       │         │      │       │
│ ┌────┴─────┐ │         │      │       │         │ ┌────┴─────┐ │
│ │AudioMixer│ │         │      │       │         │ │AudioMixer│ │
│ │  Client  │ │         │      ▼       │         │ │  Client  │ │
│ └──────────┘ │         │ ┌──────────┐ │         │ └──────────┘ │
│  Capture +   │         │ │PostgreSQL│ │         │  Capture +   │
│  Playback    │         │ └──────────┘ │         │  Playback    │
└──────────────┘         └──────────────┘         └──────────────┘
```

---

## 🔹 Chamada 1:1 (Simples)

### Fluxo Completo - Cenário de Sucesso

```
┌──────────┐                   ┌──────────┐                  ┌──────────┐
│ João (A) │                   │ SERVIDOR │                  │ Maria (B)│
└────┬─────┘                   └────┬─────┘                  └────┬─────┘
     │                              │                             │
     │ 1. João inicia chamada       │                             │
     ├─PUT /chamada/iniciar────────>│                             │
     │ Body: {                      │                             │
     │   tipo: 1,                   │                             │
     │   usuarios: [                │                             │
     │     {id: 1}, // João         │                             │
     │     {id: 2}  // Maria        │                             │
     │   ]                          │                             │
     │ }                            │                             │
     │                              │                             │
     │ 2. Servidor cria chamada     │                             │
     │    no banco de dados         │                             │
     │    Status: Pendente          │                             │
     │<─────Response(chamada_id=1)──┤                             │
     │ {                            │                             │
     │   id: 1,                     │                             │
     │   status: 1, // Pendente     │                             │
     │   usuarios: [...]            │                             │
     │ }                            │                             │
     │                              │                             │
     │                              │ 3. Notifica Maria           │
     │                              ├─WS: ChamadaRecebida────────>│
     │                              │ {                           │
     │                              │   tipo: 51,                 │
     │                              │   chamada_id: 1,            │
     │                              │   usuario_id: 1 // João     │
     │                              │ }                           │
     │                              │                             │
     │                              │                             │
     │ 4. UI mostra "Chamando..."   │  5. UI mostra "Recebendo"   │
     │    [Status: Iniciando]       │     [Tela de chamada]       │
     │    [Aguardando resposta]     │     [Aceitar] [Recusar]     │
     │                              │                             │
     │                              │                             │
     │                              │ 6. Maria aceita             │
     │                              │<─POST /chamada/entrar───────┤
     │                              │ Body: {id: 1}               │
     │                              │                             │
     │                              │ 7. Atualiza status no BD    │
     │                              │    Status: Em Andamento     │
     │                              │                             │
     │ 8. Notifica João             │                             │
     │<─WS: UsuarioEntrou───────────┤                             │
     │ {                            │                             │
     │   tipo: 54,                  │                             │
     │   chamada_id: 1,             │                             │
     │   usuario_id: 2 // Maria     │                             │
     │ }                            │                             │
     │                              │                             │
     │                              │                             │
     │ 9. João conecta TCP          │                             │
     ├─TCP Connect─────────────────>│                             │
     │    servidor:9090             │                             │
     │                              │                             │
     │ 10. Registra no servidor     │                             │
     ├─[0][UserID=1]───────────────>│                             │
     │                              │                             │
     │                              │ 11. Maria conecta TCP       │
     │                              │<─TCP Connect────────────────┤
     │                              │                             │
     │                              │ 12. Registra no servidor    │
     │                              │<─[0][UserID=2]──────────────┤
     │                              │                             │
     │                              │                             │
     │ ╔═══════════════════════════════════════════════════════╗  │
     │ ║       CHAMADA EM ANDAMENTO - TROCA DE ÁUDIO           ║  │
     │ ╚═══════════════════════════════════════════════════════╝  │
     │                              │                             │
     │ 13. João captura áudio       │                             │
     │     [Microfone] → [Buffer]   │                             │
     │                              │                             │
     │ 14. Envia ao servidor        │                             │
     ├─[1][ChamadaID=1][Audio]─────>│                             │
     │                              │                             │
     │                              │ 15. Retransmite para Maria  │
     │                              ├─[ClientID=1][Audio]────────>│
     │                              │                             │
     │                              │ 16. Maria reproduz          │
     │                              │     [Buffer] → [Speaker]    │
     │                              │                             │
     │                              │                             │
     │                              │ 17. Maria captura áudio     │
     │                              │     [Microfone] → [Buffer]  │
     │                              │                             │
     │                              │ 18. Envia ao servidor       │
     │                              │<─[1][ChamadaID=1][Audio]────┤
     │                              │                             │
     │ 19. Retransmite para João    │                             │
     │<─[ClientID=2][Audio]─────────┤                             │
     │                              │                             │
     │ 20. João reproduz            │                             │
     │     [Buffer] → [Speaker]     │                             │
     │                              │                             │
     │ ↕                            ↕                             ↕
     │ (Ciclo contínuo de áudio)    │                             │
     │                              │                             │
     │                              │                             │
     │ 21. João encerra chamada     │                             │
     ├─POST /chamada/finalizar─────>│                             │
     │ Body: {id: 1}                │                             │
     │                              │                             │
     │ 22. Fecha conexão TCP        │                             │
     ├─TCP Disconnect──────────────>│                             │
     │                              │                             │
     │                              │ 23. Notifica Maria          │
     │                              ├─WS: ChamadaFinalizada──────>│
     │                              │ {                           │
     │                              │   tipo: 52,                 │
     │                              │   chamada_id: 1,            │
     │                              │   usuario_id: 1             │
     │                              │ }                           │
     │                              │                             │
     │                              │ 24. Fecha conexão TCP       │
     │                              │<─TCP Disconnect─────────────┤
     │                              │                             │
     │ 25. UI volta ao normal       │ 26. Atualiza BD             │ 27. UI volta ao normal
     │                              │     Status: Encerrada       │
     └──────────────────────────────┴─────────────────────────────┘
```

### Cenário: Maria Recusa a Chamada

```
┌──────────┐                   ┌──────────┐                  ┌──────────┐
│ João (A) │                   │ SERVIDOR │                  │ Maria (B)│
└────┬─────┘                   └────┬─────┘                  └────┬─────┘
     │                              │                             │
     │ 1. João inicia chamada       │                             │
     ├─PUT /chamada/iniciar────────>│                             │
     │                              │                             │
     │<─────Response(chamada_id=1)──┤                             │
     │                              │                             │
     │                              │ 2. Notifica Maria           │
     │                              ├─WS: ChamadaRecebida────────>│
     │                              │                             │
     │ UI: "Chamando Maria..."      │    UI: "João está chamando" │
     │     [Aguardando...]          │        [Aceitar] [Recusar]  │
     │                              │                             │
     │                              │ 3. Maria recusa             │
     │                              │<─POST /chamada/recusar──────┤
     │                              │ Body: {id: 1}               │
     │                              │                             │
     │                              │ 4. Atualiza status          │
     │                              │    Status: Recusada         │
     │                              │                             │
     │ 5. Notifica João             │                             │
     │<─WS: UsuarioRecusou──────────┤                             │
     │ {                            │                             │
     │   tipo: 53,                  │                             │
     │   chamada_id: 1,             │                             │
     │   usuario_id: 2              │                             │
     │ }                            │                             │
     │                              │                             │
     │ 6. UI: "Maria recusou"       │                             │
     │     [Status: Recusada]       │                             │
     │     [OK]                     │                             │
     └──────────────────────────────┴─────────────────────────────┘
```

### Cenário: Chamada Não Atendida (Timeout)

```
┌──────────┐                   ┌──────────┐                  ┌──────────┐
│ João (A) │                   │ SERVIDOR │                  │ Maria (B)│
└────┬─────┘                   └────┬─────┘                  └────┬─────┘
     │                              │                             │
     │ 1. João inicia chamada       │                             │
     ├─PUT /chamada/iniciar────────>│                             │
     │                              │                             │
     │<─────Response(chamada_id=1)──┤                             │
     │                              │                             │
     │                              │ 2. Notifica Maria           │
     │                              ├─WS: ChamadaRecebida────────>│
     │                              │                             │
     │ UI: "Chamando Maria..."      │    UI: "João está chamando" │
     │     [Aguardando...]          │        [Aceitar] [Recusar]  │
     │                              │                             │
     │                              │                             │
     │     ... 60 segundos ...      │     Maria não responde      │
     │                              │                             │
     │                              │                             │
     │                              │ 3. Timeout no servidor      │
     │                              │    (Lógica de negócio)      │
     │                              │    Status: Não Atendida     │
     │                              │                             │
     │ 4. Notifica João             │                             │
     │<─WS: ChamadaFinalizada───────┤                             │
     │ {                            │                             │
     │   tipo: 52,                  │                             │
     │   chamada_id: 1              │                             │
     │ }                            │                             │
     │                              │                             │
     │ 5. UI: "Não atendida"        │                             │
     │     [Status: Não Atendida]   │                             │
     │     [OK]                     │                             │
     └──────────────────────────────┴─────────────────────────────┘
```

---

## 🔸 Chamada em Grupo

### Fluxo Completo - 3 Participantes

```
┌──────────┐             ┌──────────┐            ┌──────────┐            ┌──────────┐
│ João (1) │             │ SERVIDOR │            │ Maria (2)│            │ Pedro (3)│
└────┬─────┘             └────┬─────┘            └────┬─────┘            └────┬─────┘
     │                        │                       │                       │
     │ 1. João inicia grupo   │                       │                       │
     ├─PUT /chamada/iniciar──>│                       │                       │
     │ Body: {                │                       │                       │
     │   tipo: 2, // Grupo    │                       │                       │
     │   usuarios: [          │                       │                       │
     │     {id: 1}, // João   │                       │                       │
     │     {id: 2}, // Maria  │                       │                       │
     │     {id: 3}  // Pedro  │                       │                       │
     │   ]                    │                       │                       │
     │ }                      │                       │                       │
     │                        │                       │                       │
     │<──Response(id=1)───────┤                       │                       │
     │                        │                       │                       │
     │                        │ 2. Notifica Maria     │                       │
     │                        ├─WS: ChamadaRecebida──>│                       │
     │                        │                       │                       │
     │                        │ 3. Notifica Pedro     │                       │
     │                        ├─WS: ChamadaRecebida──────────────────────────>│
     │                        │                       │                       │
     │                        │                       │                       │
     │ UI: "Chamando..."      │   UI: "João chamando" │   UI: "João chamando" │
     │ - Maria: Aguardando    │   [Aceitar] [Recusar] │   [Aceitar] [Recusar] │
     │ - Pedro: Aguardando    │                       │                       │
     │                        │                       │                       │
     │                        │ 4. Maria aceita       │                       │
     │                        │<─POST /chamada/entrar─┤                       │
     │                        │                       │                       │
     │ 5. Notifica todos      │                       │                       │
     │<─WS: UsuarioEntrou─────┤                       │                       │
     │                        ├──────────────────────────────────────────────>│
     │                        │                       │                       │
     │                        │                       │ 6. Pedro aceita       │
     │                        │<─POST /chamada/entrar─────────────────────────┤
     │                        │                       │                       │
     │ 7. Notifica todos      │                       │                       │
     │<─WS: UsuarioEntrou─────┤                       │                       │
     │                        ├─WS: UsuarioEntrou────>│                       │
     │                        │                       │                       │
     │                        │                       │                       │
     │ 8. João conecta TCP    │                       │                       │
     ├─TCP Connect───────────>│                       │                       │
     ├─[0][UserID=1]─────────>│                       │                       │
     │                        │                       │                       │
     │                        │ 9. Maria conecta TCP  │                       │
     │                        │<─TCP Connect──────────┤                       │
     │                        │<─[0][UserID=2]────────┤                       │
     │                        │                       │                       │
     │                        │ 10. Pedro conecta TCP │                       │
     │                        │<─TCP Connect──────────────────────────────────┤
     │                        │<─[0][UserID=3]────────────────────────────────┤
     │                        │                       │                       │
     │                        │                       │                       │
     │ ╔═══════════════════════════════════════════════════════════════════╗  │
     │ ║         CHAMADA EM GRUPO - CADA UM FALA, TODOS OUVEM              ║  │
     │ ╚═══════════════════════════════════════════════════════════════════╝  │
     │                        │                       │                       │
     │ 11. João fala          │                       │                       │
     ├─[1][ID=1][Audio]──────>│                       │                       │
     │                        │                       │                       │
     │                        │ 12. Retransmite       │                       │
     │                        ├─[ClientID=1][Audio]──>│ Maria ouve João       │
     │                        ├─[ClientID=1][Audio]──────────────────────────>│ Pedro ouve João
     │                        │                       │                       │
     │                        │                       │                       │
     │                        │ 13. Maria fala        │                       │
     │                        │<─[1][ID=1][Audio]─────┤                       │
     │                        │                       │                       │
     │                        │ 14. Retransmite       │                       │
     │<─[ClientID=2][Audio]───┤                       │ João ouve Maria       │
     │                        ├─[ClientID=2][Audio]──────────────────────────>│ Pedro ouve Maria
     │                        │                       │                       │
     │                        │                       │                       │
     │                        │ 15. Pedro fala        │                       │
     │                        │<─[1][ID=1][Audio]─────────────────────────────┤
     │                        │                       │                       │
     │                        │ 16. Retransmite       │                       │
     │<─[ClientID=3][Audio]───┤                       │ João ouve Pedro       │
     │                        ├─[ClientID=3][Audio]───>│ Maria ouve Pedro     │
     │                        │                       │                       │
     │                        │                       │                       │
     │ MIXING NO CLIENTE:     │                       │ MIXING NO CLIENTE:    │
     │ João ouve:             │                       │ Maria ouve:           │
     │ - Maria (ID=2)         │                       │ - João (ID=1)         │
     │ - Pedro (ID=3)         │                       │ - Pedro (ID=3)        │
     │ [Mix e Reproduz]       │                       │ [Mix e Reproduz]      │
     │                        │                       │                       │
     └────────────────────────┴───────────────────────┴───────────────────────┘
```

### Cenário: Pedro Sai da Chamada (outros continuam)

```
┌──────────┐            ┌──────────┐            ┌──────────┐            ┌──────────┐
│ João (1) │            │ SERVIDOR │            │ Maria (2)│            │ Pedro (3)│
└────┬─────┘            └────┬─────┘            └────┬─────┘            └────┬─────┘
     │                       │                       │                       │
     │ [Chamada em andamento com 3 pessoas]          │                       │
     │                       │                       │                       │
     │                       │                       │ Pedro sai             │
     │                       │<─POST /chamada/sair───────────────────────────┤
     │                       │ Body: {id: 1}         │                       │
     │                       │                       │                       │
     │                       │<─TCP Disconnect───────────────────────────────┤
     │                       │                       │                       │
     │                       │ Atualiza status:      │                       │
     │                       │ Pedro = Saiu          │                       │
     │                       │                       │                       │
     │ Notifica              │                       │ Notifica              │
     │<─WS: UsuarioSaiu──────┤                       │                       │
     │ {                     ├─WS: UsuarioSaiu──────>│                       │
     │   tipo: 55,           │ {                     │                       │
     │   chamada_id: 1,      │   tipo: 55,           │                       │
     │   usuario_id: 3       │   chamada_id: 1,      │                       │
     │ }                     │   usuario_id: 3       │                       │
     │                       │ }                     │                       │
     │                       │                       │                       │
     │ UI: "Pedro saiu"      │                       │ UI: "Pedro saiu"      │
     │ [João e Maria]        │                       │ [João e Maria]        │
     │                       │                       │                       │
     │ ↕ Continua chamada    │                       │ ↕ Continua chamada    │
     │   apenas com Maria    │                       │   apenas com João     │
     │                       │                       │                       │
     │ João fala             │                       │                       │
     ├─[1][ID=1][Audio]─────>│                       │                       │
     │                       ├─[ClientID=1][Audio]──>│ Maria ouve            │
     │                       │   (Pedro não recebe)  │                       │
     │                       │                       │                       │
     │                       │ Maria fala            │                       │
     │                       │<─[1][ID=1][Audio]─────┤                       │
     │<─[ClientID=2][Audio]──┤                       │                       │
     │ João ouve             │   (Pedro não recebe)  │                       │
     └───────────────────────┴───────────────────────┴───────────────────────┘
```

---

## 🔧 Protocolo TCP Detalhado

### Estrutura dos Pacotes

```
┌─────────────────────────────────────────────────────────────┐
│                  PACOTE DE REGISTRO                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Byte 0          │  Bytes 1-4                               │
│  ┌─────┐         │  ┌──────────────────────┐                │
│  │  0  │         │  │    ID do Usuário     │                │
│  └─────┘         │  │      (Int32)         │                │
│   Tipo           │  └──────────────────────┘                │
│  Registrar       │      Little Endian                       │
│                                                             │
│  Total: 5 bytes                                             │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   PACOTE DE ÁUDIO                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Byte 0   │  Bytes 1-4      │  Bytes 5-N                    │
│  ┌─────┐  │  ┌────────────┐ │  ┌─────────────────────┐      │
│  │  1  │  │  │  Chamada   │ │  │   Dados de Áudio    │      │
│  └─────┘  │  │     ID     │ │  │   PCM 16-bit        │      │
│   Tipo    │  │  (Int32)   │ │  │   44.1kHz, Mono     │      │
│   Áudio   │  └────────────┘ │  └─────────────────────┘      │
│           │   Little Endian │    (Tamanho variável)         │
│                                                             │
│  Tamanho típico: 2053 bytes (5 header + 2048 audio)         │
│  Duração: ~23ms de áudio                                    │
│                                                             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              RETRANSMISSÃO DO SERVIDOR                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Bytes 0-3        │  Bytes 4-N                              │
│  ┌─────────────┐  │  ┌─────────────────────┐                │
│  │  Cliente ID │  │  │   Dados de Áudio    │                │
│  │   (Int32)   │  │  │   (mesmo recebido)  │                │
│  └─────────────┘  │  └─────────────────────┘                │
│   Identificação  │    PCM 16-bit, 44.1kHz                   │
│   do remetente   │                                          │
│                                                             │
│  Tamanho: 4 + tamanho_audio                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Fluxo de Registro TCP

```
Cliente                                             Servidor
  │                                                    │
  │ 1. Conecta socket TCP                              │
  ├─────────── TCP SYN ───────────────────────────────>│
  │<────────── TCP SYN-ACK ────────────────────────────┤
  ├─────────── TCP ACK ───────────────────────────────>│
  │                                                    │
  │ 2. Envia pacote de registro                        │
  ├── [0x00][0x01][0x00][0x00][0x00] ─────────────────>│
  │     │      └────────┬────────────┘                 │
  │     │               │                              │
  │   Tipo=0          ID=1 (Little Endian)             │
  │   Registrar                                        │
  │                                                    │
  │                                    3. Servidor     │
  │                                       - Armazena   │
  │                                         socket     │
  │                                       - Associa    │
  │                                         UserID=1   │
  │                                       - Mapeia     │
  │                                         chamadas   │
  │                                                    │
  │ 4. Conexão estabelecida e registrada               │
  │<══════════════════════════════════════════════════>│
  │           Pronto para streaming                    │
  │                                                    │
  └────────────────────────────────────────────────────┘
```

### Fluxo de Transmissão de Áudio

```
Cliente A                        Servidor                      Cliente B
   │                                │                              │
   │ Captura áudio do microfone     │                              │
   │ [PCM 16-bit, 44.1kHz]          │                              │
   │ Buffer: 2048 bytes (~23ms)     │                              │
   │                                │                              │
   │ Monta pacote:                  │                              │
   │ [1][ChamadaID][AudioData]      │                              │
   ├───────────────────────────────>│                              │
   │                                │                              │
   │                                │ Recebe pacote                │
   │                                │ - Valida tipo = 1            │
   │                                │ - Lê chamada_id              │
   │                                │ - Extrai audio_data          │
   │                                │                              │
   │                                │ Busca participantes          │
   │                                │ da chamada_id no mapa        │
   │                                │ {                            │
   │                                │   1: [socketA, socketB],     │
   │                                │   2: [socketC, socketD]      │
   │                                │ }                            │
   │                                │                              │
   │                                │ Para cada participante       │
   │                                │ (exceto remetente):          │
   │                                │                              │
   │                                │ Monta pacote retransmissão:  │
   │                                │ [ClientID][AudioData]        │
   │                                │                              │
   │                                ├─────────────────────────────>│
   │                                │  [ClientID=A][Audio]         │
   │                                │                              │
   │                                │                  Recebe:     │
   │                                │                  - ClientID  │
   │                                │                  - AudioData │
   │                                │                              │
   │                                │                  Armazena em │
   │                                │                  buffer do   │
   │                                │                  ClientID    │
   │                                │                              │
   │                                │                  Mixing:     │
   │                                │                  Mix todos   │
   │                                │                  buffers     │
   │                                │                              │
   │                                │                  Reproduz!   │
   └────────────────────────────────┴──────────────────────────────┘
```

### Exemplo de Código - Envio de Áudio

```kotlin
// Cliente - Envio de áudio
fun enviarAudioParaServidor(
    output: DataOutputStream,
    chamadaId: Int,
    audioData: ByteArray,
    bytesLidos: Int
) {
    // Aloca buffer do pacote: 1 byte (tipo) + 4 bytes (id) + audio
    val packet = ByteBuffer.allocate(5 + bytesLidos)
        .order(ByteOrder.LITTLE_ENDIAN)
    
    packet.put(1.toByte())           // Tipo: Áudio
    packet.putInt(chamadaId)         // ID da chamada
    packet.put(audioData, 0, bytesLidos)  // Dados de áudio
    
    // Envia para o servidor
    output.write(packet.array())
    output.flush()
}
```

### Exemplo de Código - Recepção de Áudio

```kotlin
// Cliente - Recepção de áudio
fun receberAudioDoServidor(input: DataInputStream): AudioPacket {
    // Lê ID do cliente remetente (4 bytes)
    val clientId = input.readInt()
    
    // Lê tamanho disponível
    val audioSize = input.available()
    
    // Lê dados de áudio
    val audioData = ByteArray(audioSize)
    val bytesLidos = input.read(audioData)
    
    return AudioPacket(
        clientId = clientId,
        data = audioData,
        size = bytesLidos
    )
}

data class AudioPacket(
    val clientId: Int,
    val data: ByteArray,
    val size: Int
)
```

---

## 📊 Estados da Chamada

### Máquina de Estados no Servidor

```
                  ┌──────────────────────────────────┐
                  │        ESTADOS NO BANCO          │
                  │         (PostgreSQL)             │
                  └──────────────────────────────────┘

     ┌─────────────────────────────────────────────────────────┐
     │                                                         │
     ▼                                                         │
┌─────────┐  Alguém aceita  ┌────────────┐  Todos saem  ┌──────────┐
│Pendente │────────────────>│    Em      │─────────────>│Encerrada │
│  (1)    │                 │ Andamento  │              │   (4)    │
└────┬────┘                 │    (3)     │              └──────────┘
     │                      └──────┬─────┘                    ▲
     │                             │                          │
     │                             │ Forçar encerramento      │
     │                             └──────────────────────────┘
     │
     │ Todos recusam
     ├──────────────────────>┌──────────┐
     │                       │Recusada  │
     │                       │   (2)    │
     │                       └──────────┘
     │
     │ Timeout (60s)
     ├──────────────────────>┌──────────┐
     │                       │   Não    │
     │                       │ Atendida │
     │                       │   (5)    │
     │                       └──────────┘
     │
     │ Cancelar antes aceitar
     └──────────────────────>┌──────────┐
                             │Cancelada │
                             │   (6)    │
                             └──────────┘
```

### Estados Locais no Cliente

```
┌─────────────────────────────────────────────────────────────┐
│               ESTADOS NO CLIENTE (APP)                      │
└─────────────────────────────────────────────────────────────┘

      Usuário inicia
           │
           ▼
    ┌─────────────┐
    │  Iniciando  │ UI: "Chamando..."
    │     (1)     │ [Aguardando resposta]
    └──────┬──────┘
           │
           │ Recebe UsuarioEntrou
           ▼
    ┌─────────────┐
    │Recebendo    │ UI: "João está chamando"
    │     (2)     │ [Aceitar] [Recusar]
    └──────┬──────┘
           │
           │ Usuário aceita / Entrar
           ▼
    ┌─────────────┐
    │     Em      │ UI: Tela de chamada ativa
    │ Andamento   │ [Microfone] [Alto-falante]
    │     (3)     │ [Encerrar]
    └──────┬──────┘
           │
           │ Finalizar / ChamadaFinalizada
           ▼
    ┌─────────────┐
    │ Finalizada  │ UI: "Chamada encerrada"
    │     (4)     │ [OK]
    └─────────────┘

           │ UsuarioRecusou
           ▼
    ┌─────────────┐
    │  Perdida    │ UI: "Não atendida"
    │     (5)     │ [OK]
    └─────────────┘

           │ Usuário recusa
           ▼
    ┌─────────────┐
    │  Recusada   │ UI: "Você recusou"
    │     (6)     │ [OK]
    └─────────────┘
```

### Transições de Estado - Tabela Completa

| Estado Atual | Ação/Evento | Novo Estado | Quem Muda | Notificação WebSocket |
|--------------|-------------|-------------|-----------|----------------------|
| - | PUT /chamada/iniciar | Pendente (1) | Servidor | ChamadaRecebida → Convidados |
| Pendente | POST /chamada/entrar | Em Andamento (3) | Servidor | UsuarioEntrou → Todos |
| Pendente | POST /chamada/recusar | Recusada (2)* | Servidor | UsuarioRecusou → Todos |
| Pendente | POST /chamada/cancelar | Cancelada (6) | Servidor | ChamadaFinalizada → Todos |
| Pendente | Timeout (60s) | Não Atendida (5) | Servidor | ChamadaFinalizada → Todos |
| Em Andamento | POST /chamada/sair | Em Andamento (3) | Servidor | UsuarioSaiu → Outros |
| Em Andamento | POST /chamada/finalizar | Encerrada (4) | Servidor | ChamadaFinalizada → Todos |
| Em Andamento | Último sai | Encerrada (4) | Servidor | ChamadaFinalizada → Todos |

*Se todos recusam, estado = Recusada. Se pelo menos um aceita, estado = Em Andamento.

---

## 🎵 Mixing de Áudio no Cliente

### Por Que no Cliente?

```
┌─────────────────────────────────────────────────────────────┐
│           VANTAGENS DO MIXING NO CLIENTE                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ✅ Menor Latência                                          │
│     - Servidor apenas retransmite (rápido)                  │
│     - Não processa áudio                                    │
│                                                             │
│  ✅ Menor Carga no Servidor                                │
│     - Servidor escala melhor                                │
│     - Suporta mais chamadas simultâneas                     │
│                                                             │
│  ✅ Controle Individual de Volume                           │
│     - Cliente pode ajustar volume de cada participante      │
│     - Implementar recursos como "mute" de usuário específico│
│                                                             │
│  ✅ Qualidade Personalizada                                 │
│     - Cliente pode aplicar filtros (noise reduction)        │
│     - Echo cancellation local                               │
│                                                             │
│  ✅ Sincronização Melhor                                    │
│     - Cada cliente gerencia seu próprio jitter buffer       │
│     - Compensa latência individual                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Algoritmo de Mixing

```
┌─────────────────────────────────────────────────────────────┐
│              ALGORITMO DE MIXING BÁSICO                     │
└─────────────────────────────────────────────────────────────┘

Para cada frame de áudio (~23ms = 2048 bytes):

1. RECEBER de todos os participantes
   ┌────────────────────────────────────┐
   │ Buffer_UserA: [s1, s2, s3, ...]    │
   │ Buffer_UserB: [s1, s2, s3, ...]    │
   │ Buffer_UserC: [s1, s2, s3, ...]    │
   └────────────────────────────────────┘

2. INICIALIZAR buffer de saída
   MixedBuffer: [0, 0, 0, ...]

3. SOMAR samples de cada usuário
   Para cada sample i:
       mixed[i] = userA[i] + userB[i] + userC[i]

4. APLICAR CLIPPING (evitar distorção)
   Para cada sample i:
       if mixed[i] > 32767:  // Max do Int16
           mixed[i] = 32767
       if mixed[i] < -32768:  // Min do Int16
           mixed[i] = -32768

5. REPRODUZIR
   AudioTrack.write(MixedBuffer)
```

### Implementação Detalhada - Kotlin

```kotlin
class AudioMixer {
    // Buffers por participante
    private val participantBuffers = ConcurrentHashMap<Int, CircularBuffer>()
    
    // Buffer de saída (mixed)
    private val mixedBuffer = ShortArray(SAMPLES_PER_FRAME)
    
    companion object {
        const val SAMPLE_RATE = 44100
        const val SAMPLES_PER_FRAME = 1024  // ~23ms
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * 2  // 16-bit
    }
    
    /**
     * Adiciona áudio recebido de um participante
     */
    fun addAudioFromParticipant(participantId: Int, audioData: ByteArray) {
        // Garante que existe buffer para o participante
        val buffer = participantBuffers.getOrPut(participantId) {
            CircularBuffer(capacity = SAMPLES_PER_FRAME * 10)  // ~230ms buffer
        }
        
        // Converte bytes para samples (PCM 16-bit Little Endian)
        val samples = bytesToSamples(audioData)
        
        // Adiciona ao buffer circular
        buffer.write(samples)
    }
    
    /**
     * Faz mixing de todos os participantes e retorna áudio pronto
     */
    fun mixAndGetAudio(): ByteArray {
        // Limpa buffer de saída
        Arrays.fill(mixedBuffer, 0)
        
        var activeParticipants = 0
        
        // Para cada participante ativo
        participantBuffers.forEach { (participantId, buffer) ->
            // Pega próximo frame do buffer
            val samples = buffer.read(SAMPLES_PER_FRAME)
            
            if (samples != null && samples.size == SAMPLES_PER_FRAME) {
                activeParticipants++
                
                // Soma samples
                for (i in mixedBuffer.indices) {
                    // Acumula sem clipping ainda
                    val sum = mixedBuffer[i] + samples[i]
                    mixedBuffer[i] = sum.toShort()
                }
            }
        }
        
        // Se não há participantes ativos, retorna silêncio
        if (activeParticipants == 0) {
            return ByteArray(BYTES_PER_FRAME)
        }
        
        // Aplica normalização e clipping
        applyNormalizationAndClipping(mixedBuffer, activeParticipants)
        
        // Converte de volta para bytes
        return samplesToBytes(mixedBuffer)
    }
    
    /**
     * Aplica normalização para evitar distorção
     */
    private fun applyNormalizationAndClipping(
        buffer: ShortArray,
        participantCount: Int
    ) {
        // Fator de normalização (volume reduz conforme mais pessoas falam)
        val normalizationFactor = if (participantCount > 1) {
            1.0f / sqrt(participantCount.toFloat())
        } else {
            1.0f
        }
        
        for (i in buffer.indices) {
            // Aplica normalização
            var normalized = (buffer[i] * normalizationFactor).toInt()
            
            // Aplica clipping (hard limiting)
            normalized = normalized.coerceIn(Short.MIN_VALUE.toInt(), 
                                              Short.MAX_VALUE.toInt())
            
            buffer[i] = normalized.toShort()
        }
    }
    
    /**
     * Converte bytes (PCM 16-bit LE) para array de shorts
     */
    private fun bytesToSamples(bytes: ByteArray): ShortArray {
        val samples = ShortArray(bytes.size / 2)
        
        for (i in samples.indices) {
            val low = bytes[i * 2].toInt() and 0xFF
            val high = bytes[i * 2 + 1].toInt() shl 8
            samples[i] = (high or low).toShort()
        }
        
        return samples
    }
    
    /**
     * Converte array de shorts para bytes (PCM 16-bit LE)
     */
    private fun samplesToBytes(samples: ShortArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        
        for (i in samples.indices) {
            bytes[i * 2] = (samples[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = (samples[i].toInt() shr 8).toByte()
        }
        
        return bytes
    }
    
    /**
     * Remove participante (quando sai da chamada)
     */
    fun removeParticipant(participantId: Int) {
        participantBuffers.remove(participantId)
    }
    
    /**
     * Limpa todos os buffers
     */
    fun clear() {
        participantBuffers.clear()
        Arrays.fill(mixedBuffer, 0)
    }
}

/**
 * Buffer circular para armazenar samples
 */
class CircularBuffer(private val capacity: Int) {
    private val buffer = ShortArray(capacity)
    private var writeIndex = 0
    private var readIndex = 0
    private var available = 0
    
    @Synchronized
    fun write(samples: ShortArray) {
        for (sample in samples) {
            buffer[writeIndex] = sample
            writeIndex = (writeIndex + 1) % capacity
            
            if (available < capacity) {
                available++
            } else {
                // Buffer cheio, sobrescreve mais antigo
                readIndex = (readIndex + 1) % capacity
            }
        }
    }
    
    @Synchronized
    fun read(count: Int): ShortArray? {
        if (available < count) {
            return null  // Não há samples suficientes
        }
        
        val result = ShortArray(count)
        for (i in 0 until count) {
            result[i] = buffer[readIndex]
            readIndex = (readIndex + 1) % capacity
            available--
        }
        
        return result
    }
    
    @Synchronized
    fun availableCount(): Int = available
}
```

### Uso do AudioMixer

```kotlin
// Thread de reprodução
Thread {
    val audioTrack = AudioTrack(
        AudioManager.STREAM_VOICE_CALL,
        44100,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        AudioTrack.getMinBufferSize(44100,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT),
        AudioTrack.MODE_STREAM
    )
    
    audioTrack.play()
    
    val mixer = AudioMixer()
    
    // Thread que recebe áudio
    Thread {
        while (chamadaAtiva) {
            val packet = receberAudioDoServidor(input)
            
            // Adiciona ao mixer
            mixer.addAudioFromParticipant(
                participantId = packet.clientId,
                audioData = packet.data
            )
        }
    }.start()
    
    // Loop de reprodução
    while (chamadaAtiva) {
        // Faz mixing e pega áudio pronto
        val mixedAudio = mixer.mixAndGetAudio()
        
        // Reproduz
        audioTrack.write(mixedAudio, 0, mixedAudio.size)
        
        // ~23ms de áudio, então aguarda um pouco
        Thread.sleep(20)
    }
    
    audioTrack.stop()
    audioTrack.release()
}.start()
```

---

## ⚠️ Casos de Erro e Recuperação

### Erro de Conexão TCP

```
Cliente                              Servidor
  │                                     │
  │ Durante chamada ativa               │
  │<═══════ Audio streaming ═══════════>│
  │                                     │
  │  ❌ Conexão TCP perdida             │
  │<════════ DISCONNECT ════════════════│
  │                                     │
  │ 1. Detecta desconexão               │
  │    - onError() no Socket            │
  │    - IOException                    │
  │                                     │
  │ 2. Tenta reconectar                 │
  │    - Máximo 3 tentativas            │
  │    - Delay: 1s, 2s, 4s              │
  │                                     │
  ├────── TCP Connect (retry 1) ───────>│
  │<────── TCP Connected ───────────────┤
  │                                     │
  │ 3. Re-registra no servidor          │
  ├─[0][UserID]────────────────────────>│
  │                                     │
  │ 4. Retoma streaming                 │
  │<═══════ Audio streaming ═══════════>│
  │                                     │
  │ Se 3 tentativas falharem:           │
  │ - Mostra erro ao usuário            │
  │ - Encerra chamada localmente        │
  │ - POST /chamada/sair                │
  └─────────────────────────────────────┘
```

### Timeout de Áudio (Jitter Buffer Vazio)

```
┌─────────────────────────────────────────────────────────────┐
│         TRATAMENTO DE JITTER BUFFER VAZIO                   │
└─────────────────────────────────────────────────────────────┘

Situação: Cliente não recebe áudio por X frames

┌─────────────────────────────────────┐
│ Loop de Reprodução                  │
│                                     │
│  while (chamadaAtiva) {             │
│      audioData = mixer.mix()        │
│                                     │
│      if (audioData == silence) {    │
│          silentFrames++             │
│                                     │
│          if (silentFrames > 50) {   │ // ~1 segundo
│              // Possível problema   │
│              showWarning()          │
│          }                          │
│                                     │
│          if (silentFrames > 150) {  │ // ~3 segundos
│              // Definitivamente     │
│              // desconectado        │
│              handleDisconnect()     │
│          }                          │
│      } else {                       │
│          silentFrames = 0           │
│      }                              │
│                                     │
│      audioTrack.write(audioData)    │
│  }                                  │
└─────────────────────────────────────┘
```

### Perda de Pacotes (Packet Loss)

```
┌─────────────────────────────────────────────────────────────┐
│              ESTRATÉGIAS PARA PACKET LOSS                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. JITTER BUFFER                                           │
│     ┌───────────────────────────────────┐                  │
│     │  [Frame N-3] [Frame N-2] [...]    │                  │
│     └───────────────────────────────────┘                  │
│     Buffer de 3-5 frames (~60-115ms)                        │
│     Absorve variação na rede                                │
│                                                             │
│  2. PACKET LOSS CONCEALMENT (PLC)                           │
│     Se frame perdido:                                       │
│     - Repete último frame válido                            │
│     - Ou faz interpolação                                   │
│                                                             │
│  3. ADAPTIVE JITTER BUFFER                                  │
│     Ajusta tamanho baseado em:                              │
│     - Taxa de perda observada                               │
│     - Variação de latência                                  │
│                                                             │
│  4. FORWARD ERROR CORRECTION (FEC)                          │
│     (Opcional, mais avançado)                               │
│     Envia dados redundantes                                 │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Sincronização de Múltiplos Streams

```
┌─────────────────────────────────────────────────────────────┐
│         PROBLEMA: STREAMS DESSINCRONIZADOS                  │
└─────────────────────────────────────────────────────────────┘

User A Stream:  ████████████████▌
User B Stream:  ██████████▌
User C Stream:  ███████████████████▌
                └─────┬──────┘
                      │
                 Diferentes latências

SOLUÇÃO: Jitter Buffer Individual

┌──────────────────────────────────────────────┐
│ class SyncedAudioMixer {                     │
│                                              │
│   data class ParticipantState(              │
│       val buffer: CircularBuffer,           │
│       val jitterBuffer: Int,                │
│       val lastPacketTime: Long              │
│   )                                          │
│                                              │
│   fun addAudio(id: Int, data: ByteArray) {  │
│       val state = participants[id]          │
│                                              │
│       // Calcula jitter                     │
│       val now = System.currentTimeMillis()  │
│       val timeSinceLastPacket =             │
│           now - state.lastPacketTime        │
│                                              │
│       // Ajusta buffer size                 │
│       if (timeSinceLastPacket > 100) {      │
│           state.jitterBuffer++              │
│       }                                      │
│                                              │
│       state.buffer.write(data)              │
│       state.lastPacketTime = now            │
│   }                                          │
│                                              │
│   fun mix(): ByteArray {                    │
│       // Só faz mix quando todos            │
│       // têm dados suficientes              │
│       val allReady = participants.all {     │
│           it.buffer.available >=            │
│               it.jitterBuffer               │
│       }                                      │
│                                              │
│       if (allReady) {                       │
│           return mixBuffers()               │
│       } else {                               │
│           return silence()                  │
│       }                                      │
│   }                                          │
│ }                                            │
└──────────────────────────────────────────────┘
```

---

## 📝 Checklist de Implementação

### Cliente - Parte 1: Sinalização

- [ ] Implementar API calls para chamadas
  - [ ] PUT /chamada/iniciar
  - [ ] POST /chamada/entrar
  - [ ] POST /chamada/recusar
  - [ ] POST /chamada/sair
  - [ ] POST /chamada/cancelar
  - [ ] POST /chamada/finalizar
  - [ ] GET /chamada/dados

- [ ] Implementar handlers WebSocket
  - [ ] ChamadaRecebida (tipo=51)
  - [ ] UsuarioEntrou (tipo=54)
  - [ ] UsuarioRecusou (tipo=53)
  - [ ] UsuarioSaiu (tipo=55)
  - [ ] ChamadaFinalizada (tipo=52)

- [ ] Implementar UI de chamada
  - [ ] Tela "Iniciando chamada"
  - [ ] Tela "Recebendo chamada" com [Aceitar][Recusar]
  - [ ] Tela "Chamada em andamento"
  - [ ] Indicadores visuais (quem está falando)

### Cliente - Parte 2: Áudio TCP

- [ ] Implementar conexão TCP
  - [ ] Conectar ao servidor:9090
  - [ ] Enviar pacote de registro
  - [ ] Tratamento de erro/reconexão

- [ ] Implementar captura de áudio
  - [ ] Configurar AudioRecord
  - [ ] Sample rate: 44.1kHz
  - [ ] Format: PCM 16-bit mono
  - [ ] Buffer size: 2048 bytes
  - [ ] Thread de captura
  - [ ] Envio via TCP

- [ ] Implementar reprodução de áudio
  - [ ] Configurar AudioTrack
  - [ ] Thread de recepção TCP
  - [ ] Thread de reprodução
  - [ ] Tratamento de buffer vazio

### Cliente - Parte 3: Mixing de Áudio

- [ ] Implementar AudioMixer
  - [ ] Buffers por participante
  - [ ] Conversão bytes ↔ samples
  - [ ] Algoritmo de soma
  - [ ] Normalização e clipping
  - [ ] Gerenciamento de participantes

- [ ] Implementar CircularBuffer
  - [ ] Write com sobrescrita
  - [ ] Read thread-safe
  - [ ] Controle de disponibilidade

- [ ] Implementar Jitter Buffer
  - [ ] Buffer adaptativo
  - [ ] Detecção de packet loss
  - [ ] Packet loss concealment

### Cliente - Parte 4: Melhorias

- [ ] Controles de UI
  - [ ] Mute/Unmute microfone
  - [ ] Ativar/Desativar alto-falante
  - [ ] Indicador de volume
  - [ ] Lista de participantes

- [ ] Otimizações
  - [ ] Noise suppression
  - [ ] Echo cancellation
  - [ ] Automatic gain control (AGC)

- [ ] Qualidade de vida
  - [ ] Vibração ao receber chamada
  - [ ] Toque de chamada
  - [ ] Notificação durante chamada
  - [ ] Estatísticas (latência, packet loss)

---

## 🚀 Próximos Passos

### Melhorias Futuras

1. **Codecs de Áudio**
   - Atualmente: PCM bruto (não comprimido)
   - Futuro: Opus codec (melhor qualidade, menor banda)

2. **Segurança**
   - Atualmente: TCP sem criptografia
   - Futuro: TLS/SSL ou DTLS

3. **Vídeo**
   - Adicionar streaming de vídeo
   - WebRTC completo

4. **Gravação**
   - Gravar chamadas
   - Salvar localmente ou servidor

5. **Analytics**
   - Métricas de qualidade
   - Logs de diagnóstico
   - Telemetria

---

**Documentação gerada em:** 21/10/2025  
**Versão:** 1.0  
**Foco:** Sistema de Chamadas de Áudio
