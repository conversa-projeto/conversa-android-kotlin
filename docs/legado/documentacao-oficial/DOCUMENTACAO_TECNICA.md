# Documentação Técnica - Projeto Conversa

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Sistema](#arquitetura-do-sistema)
3. [Servidor](#servidor)
   - [API REST](#api-rest)
   - [WebSocket](#websocket)
   - [Servidor TCP (Chamadas)](#servidor-tcp-chamadas)
4. [Autenticação e Segurança](#autenticação-e-segurança)
5. [Estrutura de Dados](#estrutura-de-dados)
6. [Endpoints da API](#endpoints-da-api)
7. [WebSocket - Mensagens em Tempo Real](#websocket---mensagens-em-tempo-real)
8. [Sistema de Chamadas de Áudio](#sistema-de-chamadas-de-áudio)
9. [Sistema de Anexos](#sistema-de-anexos)
10. [Notificações Push](#notificações-push)
11. [Cliente Windows (Referência)](#cliente-windows-referência)
12. [Guia de Implementação - Cliente Kotlin](#guia-de-implementação---cliente-kotlin)

---

## 🎯 Visão Geral

O **Conversa** é um sistema de mensagens instantâneas open-source desenvolvido em Delphi/Pascal. O sistema é composto por:

- **Servidor Backend**: API REST + WebSocket + TCP para chamadas de áudio
- **Clientes**: Windows (FMX), Android (FMX) e **Android (Kotlin - em desenvolvimento)**
- **Banco de Dados**: PostgreSQL
- **Notificações**: Firebase Cloud Messaging (FCM)

### Funcionalidades Principais

- ✅ Mensagens de texto
- ✅ Envio de imagens
- ✅ Envio de arquivos
- ✅ Mensagens de áudio
- ✅ Conversas 1:1 e grupos
- ✅ Status de mensagens (enviada, recebida, visualizada)
- ✅ Notificações push
- ⚠️ Chamadas de áudio (servidor implementado, cliente pendente)

---

## 🏗️ Arquitetura do Sistema

```
┌────────────────────────────────────────────────────────┐
│                    CLIENTES                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Windows    │  │   Android    │  │    Kotlin    │  │
│  │     FMX      │  │     FMX      │  │  (Novo)      │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────────────────────────────────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────┐
│                    SERVIDOR                            │
│  ┌──────────────────────────────────────────────────┐  │
│  │           API REST (Horse Framework)             │  │
│  │  • Autenticação (JWT)                            │  │
│  │  • CRUD Usuários, Conversas, Mensagens           │  │
│  │  • Upload/Download de Anexos                     │  │
│  │  • Gerenciamento de Chamadas                     │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │         WebSocket (Bird Socket)                  │  │
│  │  • Mensagens em tempo real                       │  │
│  │  • Notificações de eventos                       │  │
│  │  • Status de mensagens                           │  │
│  │  • Eventos de chamadas                           │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │         Servidor TCP (Chamadas)                  │  │
│  │  • Retransmissão de áudio em tempo real          │  │
│  │  • Gerenciamento de participantes                │  │
│  │  • Roteamento de pacotes de áudio                │  │
│  └──────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────┘
           │                  │                  │
           ▼                  ▼                  ▼
┌────────────────────────────────────────────────────────┐
│               INFRAESTRUTURA                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │  PostgreSQL  │  │   Firebase   │  │  Arquivos    │  │
│  │  (Banco)     │  │     FCM      │  │  (Anexos)    │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────────────────────────────────────────┘
```

### Fluxo de Comunicação

1. **Autenticação**: Cliente → API REST (Login) → Recebe JWT Token
2. **Operações CRUD**: Cliente → API REST (com JWT) → PostgreSQL
3. **Mensagens em Tempo Real**: Cliente ↔ WebSocket (autenticado com JWT)
4. **Chamadas de Áudio**: Cliente ↔ Servidor TCP (áudio raw)
5. **Notificações**: Servidor → FCM → Cliente (Push Notifications)

---

## 🖥️ Servidor

### Stack Tecnológica

- **Linguagem**: Delphi/Pascal
- **Framework Web**: Horse (servidor HTTP/REST)
- **WebSocket**: Bird Socket
- **Banco de Dados**: PostgreSQL via FireDAC
- **Autenticação**: JWT (delphi-jose-jwt)

### Estrutura do Servidor

```
conversa-servidores/
└── conversa/
    ├── rest/                    # Servidor REST + WebSocket
    │   └── src/
    │       ├── conversa/
    │       │   ├── conversa.api.pas           # Endpoints da API
    │       │   ├── conversa.comum.pas         # Funções auxiliares
    │       │   ├── conversa.chamada.pas       # Lógica de chamadas
    │       │   ├── conversa.configuracoes.pas # Configurações
    │       │   ├── WebSocket.pas              # Servidor WebSocket
    │       │   ├── FCMNotification.pas        # Notificações FCM
    │       │   └── Postgres.pas               # Pool de conexões
    │       ├── horse/           # Framework HTTP
    │       ├── horse-jwt/       # Middleware JWT
    │       └── tcp/             # Servidor TCP para chamadas
    └── socket/                  # (Servidor WebSocket standalone)
```

---

## 🔐 Autenticação e Segurança

### Fluxo de Autenticação

```
1. Cliente envia credenciais → POST /login
   Body: {
     "login": "usuario",
     "senha": "senha_hash",
     "dispositivo_id": 123  // (opcional)
   }

2. Servidor valida no PostgreSQL

3. Se válido, retorna:
   {
     "id": 1,
     "nome": "Nome do Usuário",
     "email": "email@example.com",
     "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     "dispositivo": {
       "id": 123,
       "nome": "Meu Celular",
       "plataforma": "Android"
     }
   }

4. Cliente armazena o token JWT

5. Todas as requisições seguintes incluem:
   Header: Authorization: Bearer {token}
```

### JWT Token

- **Algoritmo**: HS256 (HMAC SHA-256)
- **Claims**:
  - `sub`: ID do usuário
  - `iat`: Data de emissão
  - `exp`: Data de expiração
- **Validação**: Cada endpoint protegido valida o token via middleware

### WebSocket Authentication

```javascript
// Após conectar ao WebSocket, cliente deve autenticar:
{
  "tipo": 1,  // TSocketMessageType.Login
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}

// Servidor valida JWT e associa conexão ao usuário
```

---

## 📊 Estrutura de Dados

### Entidades Principais

#### Usuario
```json
{
  "id": 1,
  "nome": "João Silva",
  "login": "joao.silva",
  "email": "joao@example.com",
  "telefone": "+5511999999999",
  "senha": "hash_bcrypt",
  "inserido": "2025-01-15T10:30:00Z",
  "alterado": "2025-01-15T10:30:00Z"
}
```

#### Dispositivo
```json
{
  "id": 1,
  "nome": "Meu Android",
  "modelo": "Samsung Galaxy S21",
  "versao_so": "Android 13",
  "plataforma": "Android",
  "token_fcm": "token_firebase_aqui",
  "ativo": true
}
```

#### Conversa
```json
{
  "id": 1,
  "tipo": 1,  // 1=Chat (1:1), 2=Grupo
  "descricao": "Nome do Grupo",  // Vazio para chat 1:1
  "inserida": "2025-01-15T10:30:00Z",
  "mensagem_id": 150,  // ID da última mensagem
  "ultima_mensagem": "Olá!",
  "ultima_mensagem_data": "2025-01-15T15:45:00Z",
  "destinatario_id": 2,  // Apenas para chat 1:1
  "mensagens_sem_visualizar": 3
}
```

#### Mensagem
```json
{
  "id": 1,
  "conversa_id": 1,
  "usuario_id": 1,
  "remetente": "João",
  "inserida": "2025-01-15T15:45:00Z",
  "alterada": "2025-01-15T15:45:00Z",
  "recebida": true,
  "visualizada": false,
  "reproduzida": false,
  "conteudos": [
    {
      "id": 1,
      "tipo": 1,  // 1=Texto, 2=Imagem, 3=Arquivo, 4=MensagemAudio
      "ordem": 1,
      "conteudo": "Olá, tudo bem?",
      "nome": null,  // Para arquivos
      "extensao": null  // Para arquivos
    }
  ]
}
```

#### Conteudo (Mensagem)
- **tipo**: `1` = Texto, `2` = Imagem, `3` = Arquivo, `4` = Mensagem de Áudio
- Para tipo **Texto**: `conteudo` contém o texto
- Para tipo **Imagem/Arquivo/Audio**: `conteudo` contém o identificador (hash SHA256) do anexo

#### Anexo
```json
{
  "id": 1,
  "identificador": "sha256_hash_do_arquivo",
  "tipo": 2,  // 1=?, 2=Imagem, 3=Arquivo, 4=Audio
  "tamanho": 1024000,
  "nome": "foto.jpg",
  "extensao": "jpg"
}
```

### Status de Mensagem

Cada mensagem tem três estados independentes por destinatário:
- **recebida**: Cliente recebeu do servidor
- **visualizada**: Usuário viu a mensagem
- **reproduzida**: Áudio foi reproduzido (apenas para mensagens de áudio)

---

## 📡 Endpoints da API

### Base URL
```
http://servidor:porta/
```

### Autenticação

#### POST /login
Autentica usuário e retorna JWT token.

**Request:**
```json
{
  "login": "usuario",
  "senha": "senha",
  "dispositivo_id": 123  // opcional
}
```

**Response (200):**
```json
{
  "id": 1,
  "nome": "Nome do Usuário",
  "email": "email@example.com",
  "telefone": "+5511999999999",
  "token": "jwt_token_aqui",
  "dispositivo": {
    "id": 123,
    "nome": "Dispositivo",
    "modelo": "Modelo",
    "versao_so": "13",
    "plataforma": "Android",
    "ativo": true
  }
}
```

**Response (401):**
```json
{
  "error": "Acesso negado!"
}
```

### Dispositivos

#### PUT /dispositivo
Registra novo dispositivo.

**Request:**
```json
{
  "nome": "Meu Android",
  "modelo": "Samsung Galaxy S21",
  "versao_so": "13",
  "plataforma": "Android",
  "token_fcm": "token_firebase"  // opcional
}
```

**Response (200):**
```json
{
  "id": 123,
  "nome": "Meu Android",
  "modelo": "Samsung Galaxy S21",
  "versao_so": "13",
  "plataforma": "Android",
  "ativo": true
}
```

#### PATCH /dispositivo
Atualiza dispositivo existente.

**Request:**
```json
{
  "id": 123,
  "token_fcm": "novo_token_firebase"
}
```

### Usuários

#### PUT /usuario
Cria novo usuário.

**Request:**
```json
{
  "nome": "João Silva",
  "login": "joao.silva",
  "email": "joao@example.com",
  "senha": "senha_hash",
  "telefone": "+5511999999999"  // opcional
}
```

#### PATCH /usuario
Atualiza usuário existente (requer autenticação).

**Request:**
```json
{
  "id": 1,
  "nome": "Novo Nome",
  "email": "novo@example.com"
}
```

#### GET /usuario/contatos
Retorna lista de contatos do usuário (requer autenticação).

**Response (200):**
```json
[
  {
    "id": 2,
    "nome": "Maria Silva",
    "login": "maria.silva",
    "email": "maria@example.com",
    "telefone": "+5511888888888"
  }
]
```

### Conversas

#### GET /conversas
Retorna todas as conversas do usuário autenticado.

**Response (200):**
```json
[
  {
    "id": 1,
    "descricao": "Maria Silva",
    "tipo": 1,
    "inserida": "2025-01-15T10:30:00Z",
    "nome": "Maria Silva",
    "destinatario_id": 2,
    "mensagem_id": 150,
    "ultima_mensagem": "2025-01-15T15:45:00Z",
    "ultima_mensagem_texto": "Olá!",
    "mensagens_sem_visualizar": 3
  },
  {
    "id": 2,
    "descricao": "Grupo da Família",
    "tipo": 2,
    "inserida": "2025-01-10T08:00:00Z",
    "mensagem_id": 89,
    "ultima_mensagem": "2025-01-15T12:30:00Z",
    "ultima_mensagem_texto": "Almoço domingo?",
    "mensagens_sem_visualizar": 0
  }
]
```

#### PUT /conversa
Cria nova conversa.

**Request:**
```json
{
  "descricao": "Grupo da Família",
  "tipo": 2,  // 1=Chat, 2=Grupo
  "inserida": "2025-01-15T10:30:00Z"
}
```

**Response (200):**
```json
{
  "id": 2,
  "descricao": "Grupo da Família",
  "tipo": 2,
  "inserida": "2025-01-15T10:30:00Z"
}
```

#### PUT /conversa/usuario
Adiciona usuário a uma conversa.

**Request:**
```json
{
  "conversa_id": 2,
  "usuario_id": 3
}
```

### Mensagens

#### GET /mensagens
Obtém mensagens de uma conversa.

**Query Parameters:**
- `conversa`: ID da conversa (obrigatório)
- `mensagemreferencia`: ID da mensagem de referência (0 para última)
- `mensagensprevias`: Quantidade de mensagens anteriores (máx: 1000)
- `mensagensseguintes`: Quantidade de mensagens seguintes (máx: 1000)

**Exemplo:**
```
GET /mensagens?conversa=1&mensagemreferencia=0&mensagensprevias=50&mensagensseguintes=0
```

**Response (200):**
```json
[
  {
    "id": 150,
    "remetente_id": 2,
    "remetente": "Maria",
    "conversa_id": 1,
    "inserida": "2025-01-15T15:45:00Z",
    "alterada": "2025-01-15T15:45:00Z",
    "recebida": true,
    "visualizada": false,
    "reproduzida": false,
    "conteudos": [
      {
        "id": 1,
        "tipo": 1,
        "ordem": 1,
        "conteudo": "Olá, tudo bem?",
        "nome": null,
        "extensao": null
      }
    ]
  }
]
```

#### PUT /mensagem
Envia nova mensagem.

**Request:**
```json
{
  "conversa_id": 1,
  "conteudos": [
    {
      "tipo": 1,
      "ordem": 1,
      "conteudo": "Olá, tudo bem?"
    }
  ]
}
```

Para mensagens com anexo:
```json
{
  "conversa_id": 1,
  "conteudos": [
    {
      "tipo": 2,  // Imagem
      "ordem": 1,
      "conteudo": "sha256_do_arquivo"  // Identificador retornado pelo /anexo
    }
  ]
}
```

**Response (200):**
```json
{
  "id": 151,
  "usuario_id": 1,
  "conversa_id": 1,
  "inserida": "2025-01-15T15:50:00Z",
  "alterada": "2025-01-15T15:50:00Z"
}
```

#### GET /mensagem/visualizar
Marca mensagem como visualizada.

**Query Parameters:**
- `conversa`: ID da conversa
- `mensagem`: ID da mensagem

**Exemplo:**
```
GET /mensagem/visualizar?conversa=1&mensagem=150
```

#### GET /mensagem/status
Obtém status de mensagens (recebida, visualizada, reproduzida).

**Query Parameters:**
- `conversa`: ID da conversa
- `mensagem`: IDs das mensagens (separados por vírgula)

**Exemplo:**
```
GET /mensagem/status?conversa=1&mensagem=148,149,150
```

**Response (200):**
```json
[
  {
    "conversa_id": 1,
    "mensagem_id": 148,
    "recebida": true,
    "visualizada": true,
    "reproduzida": false
  },
  {
    "conversa_id": 1,
    "mensagem_id": 149,
    "recebida": true,
    "visualizada": false,
    "reproduzida": false
  }
]
```

#### GET /mensagens/novas
Verifica se há novas mensagens desde a última sincronização.

**Query Parameters:**
- `ultima`: ID da última mensagem conhecida

**Response (200):**
```json
[
  {
    "conversa_id": 1,
    "mensagem_id": 151
  },
  {
    "conversa_id": 2,
    "mensagem_id": 90
  }
]
```

### Anexos

#### GET /anexo/existe
Verifica se anexo já existe no servidor (evita reenvio).

**Query Parameters:**
- `identificador`: Hash SHA256 do arquivo

**Response (200):**
```json
{
  "existe": true
}
```

#### PUT /anexo
Faz upload de arquivo.

**Headers:**
```
Content-Type: application/octet-stream
```

**Query Parameters:**
- `tipo`: 2=Imagem, 3=Arquivo, 4=Audio
- `nome`: Nome do arquivo
- `extensao`: Extensão do arquivo

**Body:** Bytes do arquivo

**Response (200):**
```json
{
  "id": 1,
  "identificador": "sha256_hash",
  "tipo": 2,
  "tamanho": 1024000
}
```

#### GET /anexo
Faz download de arquivo.

**Query Parameters:**
- `identificador`: Hash SHA256 do arquivo

**Response (200):** Bytes do arquivo

### Chamadas de Áudio

#### PUT /chamada/iniciar
Inicia nova chamada.

**Request:**
```json
{
  "tipo": 1,  // 1=Simples (1:1), 2=Grupo
  "usuarios": [
    {"id": 1},
    {"id": 2}
  ]
}
```

**Response (200):**
```json
{
  "id": 1,
  "iniciada": null,
  "finalizada": null,
  "tipo": 1,
  "status": 1,  // 1=Pendente
  "criado_em": "2025-01-15T16:00:00Z",
  "criado_por": 1,
  "usuarios": [
    {
      "usuario_id": 1,
      "usuario_nome": "João",
      "status": 3,  // 3=Entrou
      "adicionado_por": 1,
      "adicionado_por_nome": "João",
      "adicionado_em": "2025-01-15T16:00:00Z",
      "entrou_em": "2025-01-15T16:00:00Z"
    },
    {
      "usuario_id": 2,
      "usuario_nome": "Maria",
      "status": 1,  // 1=Pendente
      "adicionado_por": 1,
      "adicionado_por_nome": "João",
      "adicionado_em": "2025-01-15T16:00:00Z"
    }
  ]
}
```

#### POST /chamada/entrar
Aceita e entra na chamada.

**Request:**
```json
{
  "id": 1
}
```

#### POST /chamada/recusar
Recusa chamada.

**Request:**
```json
{
  "id": 1
}
```

#### POST /chamada/sair
Sai da chamada (mas não finaliza).

**Request:**
```json
{
  "id": 1
}
```

#### POST /chamada/cancelar
Cancela chamada (antes de alguém entrar).

**Request:**
```json
{
  "id": 1
}
```

#### POST /chamada/finalizar
Finaliza chamada (forçadamente).

**Request:**
```json
{
  "id": 1
}
```

#### GET /chamada/dados
Obtém dados completos da chamada.

**Query Parameters:**
- `id`: ID da chamada

**Response:** Mesmo formato do `/chamada/iniciar`

---

## 🔌 WebSocket - Mensagens em Tempo Real

### Conexão

```
ws://servidor:porta/
```

### Tipos de Mensagem (TSocketMessageType)

```pascal
TSocketMessageType = (
  Erro = 0,
  Login = 1,
  NovaMensagem = 2,
  AtualizacaoStatusMensagem = 3,
  ChamadaRecebida = 51,
  ChamadaFinalizada = 52,
  UsuarioRecusou = 53,
  UsuarioEntrou = 54,
  UsuarioSaiu = 55
);
```

### Fluxo de Autenticação WebSocket

```javascript
// 1. Cliente conecta ao WebSocket
const ws = new WebSocket('ws://servidor:porta/');

// 2. Após conectar, envia token JWT
ws.onopen = () => {
  ws.send(JSON.stringify({
    tipo: 1,  // Login
    token: "jwt_token_aqui"
  }));
};

// 3. Servidor valida e associa conexão ao usuário
// Se erro, recebe:
{
  tipo: 0,  // Erro
  message: "Token inválido"
}
```

### Mensagens do Servidor → Cliente

#### Nova Mensagem
```json
{
  "tipo": 2,
  "titulo": "Maria Silva",
  "mensagem": "Olá, tudo bem?"
}
```

#### Atualização de Status
Quando outro usuário recebe/visualiza mensagem enviada por você:
```json
{
  "tipo": 3,
  "grupo": 1,  // conversa_id
  "mensagens": "148,149,150"  // IDs das mensagens atualizadas
}
```

#### Eventos de Chamada

**Chamada Recebida:**
```json
{
  "tipo": 51,
  "chamada_id": 1,
  "usuario_id": 2  // Quem está chamando
}
```

**Chamada Finalizada:**
```json
{
  "tipo": 52,
  "chamada_id": 1,
  "usuario_id": 2  // Quem finalizou
}
```

**Usuário Recusou:**
```json
{
  "tipo": 53,
  "chamada_id": 1,
  "usuario_id": 3  // Quem recusou
}
```

**Usuário Entrou:**
```json
{
  "tipo": 54,
  "chamada_id": 1,
  "usuario_id": 3  // Quem entrou
}
```

**Usuário Saiu:**
```json
{
  "tipo": 55,
  "chamada_id": 1,
  "usuario_id": 3  // Quem saiu
}
```

### Tratamento de Eventos

```kotlin
// Exemplo de tratamento no cliente
when (message.tipo) {
    2 -> {
        // Nova mensagem - atualizar UI e/ou mostrar notificação
        showNotification(message.titulo, message.mensagem)
    }
    3 -> {
        // Status atualizado - buscar novos status via API
        atualizarStatusMensagens(message.grupo, message.mensagens)
    }
    51 -> {
        // Chamada recebida - mostrar tela de chamada
        mostrarTelaReceberChamada(message.chamada_id, message.usuario_id)
    }
    // ... outros eventos
}
```

---

## 📞 Sistema de Chamadas de Áudio

### Arquitetura

O sistema de chamadas utiliza **3 camadas**:

1. **API REST**: Gerenciamento de chamadas (iniciar, aceitar, finalizar, etc.)
2. **WebSocket**: Sinalização de eventos (chamada recebida, usuário entrou, etc.)
3. **TCP Server**: Transmissão do áudio em tempo real

### Fluxo de Chamada

```
[João]                    [Servidor]                    [Maria]
  |                            |                            |
  |--PUT /chamada/iniciar----->|                            |
  |<---chamada_id=1------------|                            |
  |                            |                            |
  |                            |---WS: ChamadaRecebida----->|
  |                            |                            |
  |                            |<---POST /chamada/entrar----|
  |<---WS: UsuarioEntrou-------|                            |
  |                            |                            |
  |--Conecta TCP:9090--------->|                            |
  |                            |<---Conecta TCP:9090--------|
  |                            |                            |
  |--[0][ID_4bytes]----------->|  (Registra cliente TCP)    |
  |                            |<--[0][ID_4bytes]-----------|
  |                            |                            |
  |--[1][ChamadaID][Audio]---->|                            |
  |                            |--[ClientID][Audio]-------->|
  |<--[ClientID][Audio]--------|<--[1][ChamadaID][Audio]----|
  |                            |                            |
```

### Servidor TCP (Porta 9090)

**IMPORTANTE:** O servidor TCP **NÃO faz mixing de áudio**. Ele apenas **retransmite** os pacotes de áudio recebidos para os outros participantes da chamada. A **mixagem do áudio acontece no cliente**, que recebe múltiplos streams de áudio e os combina localmente antes de reproduzir.

#### Protocolo de Comunicação

**Pacote de Registro:**
```
[Tipo: 1 byte][ID_Usuario: 4 bytes]
Tipo = 0: Registrar cliente
ID_Usuario: ID do usuário no banco de dados
```

**Pacote de Áudio:**
```
[Tipo: 1 byte][ID_Chamada: 4 bytes][Dados_Audio: N bytes]
Tipo = 1: Enviar áudio
ID_Chamada: ID da chamada no banco
Dados_Audio: PCM 16-bit, 44.1kHz, mono
```

#### Formato de Áudio

- **Sample Rate**: 44.100 Hz
- **Bits per Sample**: 16-bit
- **Channels**: 1 (Mono)
- **Format**: PCM (Raw)
- **Buffer Size**: 2048 bytes (~23ms de áudio)

#### Implementação Cliente

**1. Conectar ao servidor TCP:**
```kotlin
val socket = Socket("servidor", 9090)
val output = DataOutputStream(socket.getOutputStream())
val input = DataInputStream(socket.getInputStream())
```

**2. Registrar cliente:**
```kotlin
// Envia tipo=0 + ID do usuário
val buffer = ByteBuffer.allocate(5)
buffer.put(0.toByte())              // Tipo: Registrar
buffer.putInt(usuarioId)            // ID do usuário
output.write(buffer.array())
```

**3. Capturar e enviar áudio:**
```kotlin
// Configurar AudioRecord
val audioRecord = AudioRecord(
    MediaRecorder.AudioSource.MIC,
    44100,                          // Sample rate
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    AudioRecord.getMinBufferSize(44100, 
        AudioFormat.CHANNEL_IN_MONO, 
        AudioFormat.ENCODING_PCM_16BIT)
)

audioRecord.startRecording()

// Loop de captura
while (emChamada) {
    val audioData = ByteArray(2048)
    val bytesRead = audioRecord.read(audioData, 0, audioData.size)
    
    if (bytesRead > 0) {
        // Monta pacote: [tipo=1][chamada_id][audio]
        val packet = ByteBuffer.allocate(5 + bytesRead)
        packet.put(1.toByte())          // Tipo: Audio
        packet.putInt(chamadaId)        // ID da chamada
        packet.put(audioData, 0, bytesRead)
        
        output.write(packet.array())
    }
}
```

**4. Receber e reproduzir áudio (com mixing no cliente):**
```kotlin
// Thread separada para receber
Thread {
    // Configurar AudioTrack
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
    
    // Buffer para mixing de múltiplos streams
    val mixingBuffers = mutableMapOf<Int, Queue<ByteArray>>()
    val mixedBuffer = ShortArray(2048 / 2) // PCM 16-bit = 2 bytes por sample
    
    while (emChamada) {
        // Recebe: [ClientID: 4 bytes][Audio: N bytes]
        val clientId = input.readInt()
        val audioSize = input.available()
        
        if (audioSize > 0) {
            val audioData = ByteArray(audioSize)
            input.read(audioData)
            
            // Adiciona ao buffer do participante
            if (!mixingBuffers.containsKey(clientId)) {
                mixingBuffers[clientId] = LinkedList()
            }
            mixingBuffers[clientId]?.add(audioData)
            
            // Mixing: soma os samples de todos os participantes
            Arrays.fill(mixedBuffer, 0)
            
            mixingBuffers.forEach { (_, queue) ->
                queue.poll()?.let { buffer ->
                    for (i in 0 until minOf(buffer.size / 2, mixedBuffer.size)) {
                        // Converte bytes para short (PCM 16-bit little-endian)
                        val sample = ((buffer[i * 2 + 1].toInt() shl 8) or 
                                     (buffer[i * 2].toInt() and 0xFF)).toShort()
                        
                        // Soma os samples (com clipping)
                        val mixed = mixedBuffer[i] + sample
                        mixedBuffer[i] = mixed.coerceIn(Short.MIN_VALUE, Short.MAX_VALUE).toShort()
                    }
                }
            }
            
            // Converte de volta para bytes e reproduz
            val outputBuffer = ByteArray(mixedBuffer.size * 2)
            for (i in mixedBuffer.indices) {
                outputBuffer[i * 2] = (mixedBuffer[i].toInt() and 0xFF).toByte()
                outputBuffer[i * 2 + 1] = (mixedBuffer[i].toInt() shr 8).toByte()
            }
            
            audioTrack.write(outputBuffer, 0, outputBuffer.size)
        }
    }
    
    audioTrack.stop()
    audioTrack.release()
}.start()
```

**Nota sobre Mixing:** O exemplo acima mostra uma implementação básica de mixing. Em produção, considere:
- Usar um buffer circular para sincronização adequada
- Implementar controle de ganho para evitar clipping
- Adicionar jitter buffer para compensar variações de rede
- Considerar usar bibliotecas especializadas como WebRTC ou Oboe

### Status de Chamada

#### Status no Servidor (banco de dados)
```
1 = Pendente      (Aguardando resposta)
2 = Recusada      (Alguém recusou)
3 = Em Andamento  (Pelo menos 2 pessoas conectadas)
4 = Encerrada     (Finalizada)
5 = Não Atendida  (Timeout)
6 = Cancelada     (Cancelada antes de alguém entrar)
```

#### Status Local (Cliente)
```
0 = Desconhecido
1 = Iniciando Chamada
2 = Recebendo Chamada
3 = Chamada Em Andamento
4 = Chamada Finalizada
5 = Chamada Perdida
6 = Recusada
```

---

## 📎 Sistema de Anexos

### Fluxo de Upload

```
1. Cliente calcula SHA256 do arquivo
2. Verifica se já existe: GET /anexo/existe?identificador={sha256}
3. Se NÃO existe:
   - Envia arquivo: PUT /anexo?tipo=2&nome=foto.jpg&extensao=jpg
   - Body: bytes do arquivo
   - Header: Content-Type: application/octet-stream
4. Servidor retorna identificador
5. Cliente envia mensagem com identificador no conteúdo
```

### Fluxo de Download

```
1. Cliente recebe mensagem com tipo=2/3/4 (Imagem/Arquivo/Audio)
2. Pega identificador do campo 'conteudo'
3. Baixa: GET /anexo?identificador={sha256}
4. Salva arquivo localmente
5. Exibe/reproduz
```

### Exemplo Kotlin

```kotlin
// Upload
fun uploadAnexo(file: File, tipo: Int): String {
    // 1. Calcula SHA256
    val sha256 = calcularSHA256(file)
    
    // 2. Verifica se existe
    val existe = api.get("/anexo/existe?identificador=$sha256")
        .getBoolean("existe")
    
    if (existe) {
        return sha256  // Já existe, não precisa enviar
    }
    
    // 3. Faz upload
    val response = api.put("/anexo")
        .query("tipo", tipo)
        .query("nome", file.name)
        .query("extensao", file.extension)
        .header("Content-Type", "application/octet-stream")
        .body(file.readBytes())
        .execute()
    
    return response.getString("identificador")
}

// Download
fun downloadAnexo(identificador: String): ByteArray {
    return api.get("/anexo")
        .query("identificador", identificador)
        .execute()
        .bytes()
}

// Calcular SHA256
fun calcularSHA256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = file.readBytes()
    val hash = digest.digest(bytes)
    return hash.joinToString("") { "%02x".format(it) }
}
```

---

## 🔔 Notificações Push

### Firebase Cloud Messaging (FCM)

O servidor utiliza FCM para enviar notificações push quando:
- Nova mensagem chega (e usuário está offline ou app em background)
- Chamada recebida

### Configuração no Cliente

**1. Adicionar Firebase ao projeto:**
```gradle
// build.gradle (app)
implementation 'com.google.firebase:firebase-messaging:23.1.0'
```

**2. Obter token FCM:**
```kotlin
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (task.isSuccessful) {
        val token = task.result
        // Envia ao servidor via PATCH /dispositivo
        atualizarTokenFCM(token)
    }
}
```

**3. Atualizar token no servidor:**
```kotlin
fun atualizarTokenFCM(token: String) {
    api.patch("/dispositivo")
        .body("""
            {
                "id": ${dispositivoId},
                "token_fcm": "$token"
            }
        """)
        .execute()
}
```

**4. Receber notificações:**
```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Processa notificação
        val titulo = remoteMessage.notification?.title
        val corpo = remoteMessage.notification?.body
        
        // Mostra notificação local
        showNotification(titulo, corpo)
        
        // Atualiza dados se app estiver aberto
        if (appEmPrimeiroplano) {
            sincronizarMensagens()
        }
    }
}
```

**Observação**: Atualmente o servidor tem o envio de FCM desabilitado (linha comentada em `conversa.api.pas`). Para habilitar, configure as credenciais do Firebase no banco de dados (tabela `parametros`).

---

## 💻 Cliente Windows (Referência)

### Arquitetura do Cliente FMX

```
src/
├── base/                    # Classes base
│   ├── FormularioBase       # Form base
│   └── FrameBase           # Frame base
│
├── chat/                    # Sistema de chat
│   ├── chat/               # Componentes de mensagem
│   │   ├── frames/         # Frames de exibição
│   │   │   ├── chat.mensagem.pas
│   │   │   ├── chat.conteudo.texto.pas
│   │   │   ├── chat.conteudo.imagem.pas
│   │   │   ├── chat.editor.pas
│   │   │   └── ...
│   ├── Chat.Listagem.pas   # Lista de conversas
│   └── Chat.pas            # Tela principal do chat
│
├── chamada/                 # Sistema de chamadas
│   ├── Chamada.pas         # Lógica de chamada
│   ├── Chamada.view.pas    # UI de chamada
│   ├── Audio.pas           # Interface de áudio
│   └── Audio.Windows.pas   # Implementação Windows
│
├── Proxy.pas               # Camada de comunicação com API
├── Tipos.pas               # Estruturas de dados
├── Eventos.pas             # Sistema de eventos
└── Dados.pas               # Cache local e sincronização
```

### Padrões Utilizados

#### 1. Sistema de Eventos
O cliente usa `TMessageManager` (FMX) para comunicação entre componentes:

```pascal
// Enviar evento
TMessageManager.DefaultManager.SendMessage(
  nil, 
  TEventoNovaMensagem.Create(mensagemId)
);

// Receber evento
TMessageManager.DefaultManager.SubscribeToMessage(
  TEventoNovaMensagem,
  procedure(const Sender: TObject; const M: TMessage)
  begin
    // Processar
  end
);
```

#### 2. Cache Local
- Conversas e mensagens ficam em memória (`Conversa.Dados.pas`)
- Sincronização incremental com servidor
- Anexos salvos localmente

#### 3. Comunicação Assíncrona
- Requisições REST em threads separadas
- Callbacks via eventos
- UI nunca bloqueia

---

## 📱 Guia de Implementação - Cliente Kotlin

### Stack Recomendada

#### Arquitetura
- **Padrão**: MVVM (Model-View-ViewModel)
- **Estrutura**: Clean Architecture com camadas separadas

#### Networking
- **REST API**: Retrofit + OkHttp
- **WebSocket**: OkHttp WebSocket ou Socket.IO
- **JSON**: Moshi ou Gson

#### Persistência Local
- **Banco**: Room (SQLite)
- **Preferências**: DataStore (substituto do SharedPreferences)
- **Arquivos**: File System padrão do Android

#### UI
- **Framework**: Jetpack Compose ou XML tradicional
- **Navigation**: Navigation Component
- **Imagens**: Coil ou Glide

#### Áudio
- **Captura**: AudioRecord
- **Reprodução**: AudioTrack
- **Permissões**: Runtime Permissions

#### Notificações
- **Push**: Firebase Cloud Messaging (FCM)
- **Locais**: NotificationManager

#### Injeção de Dependência
- **Framework**: Hilt (recomendado) ou Koin

---

### Estrutura de Projeto

```
app/src/main/java/com/projeto/conversa/
│
├── data/                           # Camada de Dados
│   ├── remote/                     # API REST
│   │   ├── api/
│   │   │   ├── ConversaApi.kt     # Interface Retrofit
│   │   │   └── WebSocketClient.kt
│   │   ├── dto/                    # Data Transfer Objects
│   │   │   ├── LoginRequest.kt
│   │   │   ├── LoginResponse.kt
│   │   │   ├── MensagemDto.kt
│   │   │   └── ...
│   │   └── interceptor/
│   │       └── AuthInterceptor.kt # Adiciona JWT no header
│   │
│   ├── local/                      # Banco Local
│   │   ├── dao/
│   │   │   ├── ConversaDao.kt
│   │   │   ├── MensagemDao.kt
│   │   │   └── ...
│   │   ├── entity/
│   │   │   ├── ConversaEntity.kt
│   │   │   ├── MensagemEntity.kt
│   │   │   └── ...
│   │   └── AppDatabase.kt
│   │
│   ├── repository/                 # Repositórios
│   │   ├── AuthRepository.kt
│   │   ├── ConversaRepository.kt
│   │   ├── MensagemRepository.kt
│   │   └── ChamadaRepository.kt
│   │
│   └── preferences/                # Preferências
│       └── AppPreferences.kt       # DataStore
│
├── domain/                         # Camada de Domínio
│   ├── model/                      # Modelos de negócio
│   │   ├── Usuario.kt
│   │   ├── Conversa.kt
│   │   ├── Mensagem.kt
│   │   └── Chamada.kt
│   │
│   ├── usecase/                    # Casos de Uso
│   │   ├── auth/
│   │   │   ├── LoginUseCase.kt
│   │   │   └── LogoutUseCase.kt
│   │   ├── conversa/
│   │   │   ├── GetConversasUseCase.kt
│   │   │   └── CreateConversaUseCase.kt
│   │   ├── mensagem/
│   │   │   ├── SendMensagemUseCase.kt
│   │   │   ├── GetMensagensUseCase.kt
│   │   │   └── VisualizarMensagemUseCase.kt
│   │   └── chamada/
│   │       ├── IniciarChamadaUseCase.kt
│   │       └── AceitarChamadaUseCase.kt
│   │
│   └── repository/                 # Interfaces dos repositórios
│       └── (Interfaces)
│
├── presentation/                   # Camada de Apresentação
│   ├── auth/
│   │   ├── LoginViewModel.kt
│   │   └── LoginScreen.kt          # Compose
│   │
│   ├── conversas/
│   │   ├── ConversasViewModel.kt
│   │   └── ConversasScreen.kt
│   │
│   ├── chat/
│   │   ├── ChatViewModel.kt
│   │   └── ChatScreen.kt
│   │
│   ├── chamada/
│   │   ├── ChamadaViewModel.kt
│   │   └── ChamadaScreen.kt
│   │
│   └── components/                 # Componentes reutilizáveis
│       ├── MensagemItem.kt
│       └── ConversaItem.kt
│
├── service/                        # Serviços de Background
│   ├── WebSocketService.kt        # Service para WebSocket
│   ├── ChamadaService.kt          # Service para chamadas
│   └── FirebaseMessagingService.kt
│
├── util/                           # Utilitários
│   ├── NetworkUtil.kt
│   ├── DateUtil.kt
│   ├── FileUtil.kt
│   └── AudioUtil.kt
│
└── di/                             # Injeção de Dependência
    ├── NetworkModule.kt
    ├── DatabaseModule.kt
    ├── RepositoryModule.kt
    └── UseCaseModule.kt
```

---

### Implementação - Passo a Passo

#### 1. Setup do Projeto

**build.gradle (project):**
```gradle
buildscript {
    ext.kotlin_version = "1.9.0"
    ext.hilt_version = "2.48"
    
    dependencies {
        classpath "com.google.dagger:hilt-android-gradle-plugin:$hilt_version"
        classpath 'com.google.gms:google-services:4.3.15'
    }
}
```

**build.gradle (app):**
```gradle
plugins {
    id 'com.android.application'
    id 'kotlin-android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'
    id 'com.google.gms.google-services'
}

android {
    compileSdk 34
    
    defaultConfig {
        applicationId "com.projeto.conversa"
        minSdk 24
        targetSdk 34
        
        buildConfigField "String", "API_URL", "\"http://seu-servidor:porta/\""
        buildConfigField "String", "WS_URL", "\"ws://seu-servidor:porta/\""
        buildConfigField "String", "TCP_HOST", "\"seu-servidor\""
        buildConfigField "int", "TCP_PORT", "9090"
    }
    
    buildFeatures {
        compose true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.0"
    }
}

dependencies {
    // Kotlin
    implementation "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version"
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // AndroidX
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.6.2'
    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2'
    
    // Compose
    def compose_version = '1.5.4'
    implementation "androidx.compose.ui:ui:$compose_version"
    implementation "androidx.compose.material3:material3:1.1.2"
    implementation "androidx.compose.ui:ui-tooling-preview:$compose_version"
    implementation 'androidx.activity:activity-compose:1.8.1'
    implementation 'androidx.navigation:navigation-compose:2.7.5'
    
    // Hilt
    implementation "com.google.dagger:hilt-android:$hilt_version"
    kapt "com.google.dagger:hilt-compiler:$hilt_version"
    implementation 'androidx.hilt:hilt-navigation-compose:1.1.0'
    
    // Networking
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-moshi:2.9.0'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'
    implementation 'com.squareup.moshi:moshi-kotlin:1.15.0'
    kapt 'com.squareup.moshi:moshi-kotlin-codegen:1.15.0'
    
    // Room
    def room_version = "2.6.1"
    implementation "androidx.room:room-runtime:$room_version"
    implementation "androidx.room:room-ktx:$room_version"
    kapt "androidx.room:room-compiler:$room_version"
    
    // DataStore
    implementation 'androidx.datastore:datastore-preferences:1.0.0'
    
    // Firebase
    implementation platform('com.google.firebase:firebase-bom:32.6.0')
    implementation 'com.google.firebase:firebase-messaging-ktx'
    
    // Coil (Imagens)
    implementation 'io.coil-kt:coil-compose:2.5.0'
    
    // Debugging
    debugImplementation "androidx.compose.ui:ui-tooling:$compose_version"
}
```

#### 2. Configuração de Rede

**NetworkModule.kt:**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideConversaApi(retrofit: Retrofit): ConversaApi {
        return retrofit.create(ConversaApi::class.java)
    }
}
```

**AuthInterceptor.kt:**
```kotlin
@Singleton
class AuthInterceptor @Inject constructor(
    private val appPreferences: AppPreferences
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        
        val token = runBlocking {
            appPreferences.getToken()
        }
        
        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        
        return chain.proceed(newRequest)
    }
}
```

**ConversaApi.kt:**
```kotlin
interface ConversaApi {
    
    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
    
    @GET("conversas")
    suspend fun getConversas(): List<ConversaDto>
    
    @GET("mensagens")
    suspend fun getMensagens(
        @Query("conversa") conversaId: Int,
        @Query("mensagemreferencia") referencia: Int = 0,
        @Query("mensagensprevias") previas: Int = 50,
        @Query("mensagensseguintes") seguintes: Int = 0
    ): List<MensagemDto>
    
    @PUT("mensagem")
    suspend fun sendMensagem(@Body request: MensagemRequest): MensagemResponse
    
    @GET("mensagem/visualizar")
    suspend fun visualizarMensagem(
        @Query("conversa") conversaId: Int,
        @Query("mensagem") mensagemId: Int
    ): Response<Unit>
    
    @GET("anexo/existe")
    suspend fun anexoExiste(
        @Query("identificador") identificador: String
    ): AnexoExisteResponse
    
    @PUT("anexo")
    suspend fun uploadAnexo(
        @Query("tipo") tipo: Int,
        @Query("nome") nome: String,
        @Query("extensao") extensao: String,
        @Body arquivo: RequestBody
    ): AnexoResponse
    
    @GET("anexo")
    suspend fun downloadAnexo(
        @Query("identificador") identificador: String
    ): ResponseBody
    
    @PUT("chamada/iniciar")
    suspend fun iniciarChamada(@Body request: ChamadaRequest): ChamadaResponse
    
    @POST("chamada/entrar")
    suspend fun entrarChamada(@Body request: ChamadaIdRequest): Response<Unit>
    
    @POST("chamada/recusar")
    suspend fun recusarChamada(@Body request: ChamadaIdRequest): Response<Unit>
    
    // ... outros endpoints
}
```

#### 3. Banco de Dados Local (Room)

**AppDatabase.kt:**
```kotlin
@Database(
    entities = [
        UsuarioEntity::class,
        ConversaEntity::class,
        MensagemEntity::class,
        ConteudoEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun conversaDao(): ConversaDao
    abstract fun mensagemDao(): MensagemDao
    abstract fun conteudoDao(): ConteudoDao
}
```

**ConversaEntity.kt:**
```kotlin
@Entity(tableName = "conversas")
data class ConversaEntity(
    @PrimaryKey
    val id: Int,
    val tipo: Int,  // 1=Chat, 2=Grupo
    val descricao: String,
    val ultimaMensagem: String?,
    val ultimaMensagemData: Long?,
    val ultimaMensagemId: Int?,
    val mensagensSemVisualizar: Int,
    val criadoEm: Long
)
```

**MensagemEntity.kt:**
```kotlin
@Entity(
    tableName = "mensagens",
    foreignKeys = [
        ForeignKey(
            entity = ConversaEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversaId")]
)
data class MensagemEntity(
    @PrimaryKey
    val id: Int,
    val localId: Int?,  // ID temporário (negativo)
    val conversaId: Int,
    val remetenteId: Int,
    val inserida: Long,
    val alterada: Long,
    val recebida: Boolean,
    val visualizada: Boolean,
    val exibida: Boolean
)
```

**ConteudoEntity.kt:**
```kotlin
@Entity(
    tableName = "conteudos",
    foreignKeys = [
        ForeignKey(
            entity = MensagemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mensagemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mensagemId")]
)
data class ConteudoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val mensagemId: Int,
    val tipo: Int,  // 1=Texto, 2=Imagem, 3=Arquivo, 4=Audio
    val ordem: Int,
    val conteudo: String,
    val nome: String?,
    val extensao: String?
)
```

**ConversaDao.kt:**
```kotlin
@Dao
interface ConversaDao {
    
    @Query("SELECT * FROM conversas ORDER BY ultimaMensagemData DESC")
    fun getAll(): Flow<List<ConversaEntity>>
    
    @Query("SELECT * FROM conversas WHERE id = :id")
    suspend fun getById(id: Int): ConversaEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversa: ConversaEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(conversas: List<ConversaEntity>)
    
    @Update
    suspend fun update(conversa: ConversaEntity)
    
    @Delete
    suspend fun delete(conversa: ConversaEntity)
    
    @Query("DELETE FROM conversas")
    suspend fun deleteAll()
}
```

#### 4. Repositórios

**ConversaRepository.kt:**
```kotlin
@Singleton
class ConversaRepository @Inject constructor(
    private val api: ConversaApi,
    private val conversaDao: ConversaDao,
    private val mensagemDao: MensagemDao
) {
    
    fun getConversas(): Flow<List<Conversa>> {
        return conversaDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    suspend fun sincronizarConversas() {
        try {
            val conversas = api.getConversas()
            conversaDao.insertAll(conversas.map { it.toEntity() })
        } catch (e: Exception) {
            // Tratar erro
        }
    }
    
    suspend fun getConversaById(id: Int): Conversa? {
        return conversaDao.getById(id)?.toDomain()
    }
}
```

**MensagemRepository.kt:**
```kotlin
@Singleton
class MensagemRepository @Inject constructor(
    private val api: ConversaApi,
    private val mensagemDao: MensagemDao,
    private val conteudoDao: ConteudoDao
) {
    
    fun getMensagens(conversaId: Int): Flow<List<Mensagem>> {
        return mensagemDao.getByConversa(conversaId).map { entities ->
            entities.map { entity ->
                val conteudos = conteudoDao.getByMensagem(entity.id)
                entity.toDomain(conteudos.map { it.toDomain() })
            }
        }
    }
    
    suspend fun sincronizarMensagens(
        conversaId: Int,
        referencia: Int = 0,
        previas: Int = 50,
        seguintes: Int = 0
    ) {
        try {
            val mensagens = api.getMensagens(
                conversaId, referencia, previas, seguintes
            )
            
            mensagens.forEach { dto ->
                mensagemDao.insert(dto.toEntity())
                conteudoDao.insertAll(dto.conteudos.map { it.toEntity(dto.id) })
            }
        } catch (e: Exception) {
            // Tratar erro
        }
    }
    
    suspend fun enviarMensagem(
        conversaId: Int,
        conteudos: List<Conteudo>
    ): Result<Mensagem> {
        return try {
            // Cria mensagem local com ID negativo
            val localId = gerarLocalId()
            val mensagemLocal = MensagemEntity(
                id = localId,
                localId = localId,
                conversaId = conversaId,
                remetenteId = getUserId(),
                inserida = System.currentTimeMillis(),
                alterada = System.currentTimeMillis(),
                recebida = false,
                visualizada = false,
                exibida = false
            )
            
            mensagemDao.insert(mensagemLocal)
            
            conteudoDao.insertAll(
                conteudos.mapIndexed { index, conteudo ->
                    ConteudoEntity(
                        mensagemId = localId,
                        tipo = conteudo.tipo,
                        ordem = index + 1,
                        conteudo = conteudo.valor,
                        nome = conteudo.nome,
                        extensao = conteudo.extensao
                    )
                }
            )
            
            // Envia ao servidor
            val request = MensagemRequest(
                conversa_id = conversaId,
                conteudos = conteudos.mapIndexed { index, c ->
                    ConteudoDto(
                        tipo = c.tipo,
                        ordem = index + 1,
                        conteudo = c.valor
                    )
                }
            )
            
            val response = api.sendMensagem(request)
            
            // Atualiza mensagem local com ID do servidor
            val mensagemAtualizada = mensagemLocal.copy(
                id = response.id,
                inserida = response.inserida,
                alterada = response.alterada
            )
            
            mensagemDao.update(mensagemAtualizada)
            
            Result.success(mensagemAtualizada.toDomain(conteudos))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun visualizarMensagem(conversaId: Int, mensagemId: Int) {
        try {
            api.visualizarMensagem(conversaId, mensagemId)
            mensagemDao.marcarComoVisualizada(mensagemId)
        } catch (e: Exception) {
            // Tratar erro
        }
    }
    
    private fun gerarLocalId(): Int {
        return -(System.currentTimeMillis().toInt())
    }
}
```

#### 5. Use Cases

**SendMensagemUseCase.kt:**
```kotlin
@Singleton
class SendMensagemUseCase @Inject constructor(
    private val mensagemRepository: MensagemRepository,
    private val anexoRepository: AnexoRepository
) {
    
    suspend operator fun invoke(
        conversaId: Int,
        texto: String? = null,
        imagens: List<Uri>? = null,
        arquivos: List<Uri>? = null
    ): Result<Mensagem> {
        
        val conteudos = mutableListOf<Conteudo>()
        
        // Adiciona texto
        texto?.let {
            conteudos.add(Conteudo(tipo = 1, valor = it))
        }
        
        // Faz upload de imagens
        imagens?.forEach { uri ->
            val identificador = anexoRepository.uploadAnexo(uri, tipo = 2)
            conteudos.add(Conteudo(tipo = 2, valor = identificador))
        }
        
        // Faz upload de arquivos
        arquivos?.forEach { uri ->
            val identificador = anexoRepository.uploadAnexo(uri, tipo = 3)
            conteudos.add(Conteudo(tipo = 3, valor = identificador))
        }
        
        return mensagemRepository.enviarMensagem(conversaId, conteudos)
    }
}
```

**GetMensagensUseCase.kt:**
```kotlin
@Singleton
class GetMensagensUseCase @Inject constructor(
    private val mensagemRepository: MensagemRepository
) {
    
    operator fun invoke(conversaId: Int): Flow<List<Mensagem>> {
        return mensagemRepository.getMensagens(conversaId)
    }
    
    suspend fun sincronizar(conversaId: Int) {
        mensagemRepository.sincronizarMensagens(conversaId)
    }
}
```

#### 6. ViewModels

**ChatViewModel.kt:**
```kotlin
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMensagensUseCase: GetMensagensUseCase,
    private val sendMensagemUseCase: SendMensagemUseCase,
    private val visualizarMensagemUseCase: VisualizarMensagemUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val conversaId: Int = savedStateHandle["conversaId"] ?: 0
    
    val mensagens: StateFlow<List<Mensagem>> = 
        getMensagensUseCase(conversaId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    init {
        sincronizarMensagens()
    }
    
    fun enviarMensagem(texto: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(enviando = true) }
            
            sendMensagemUseCase(
                conversaId = conversaId,
                texto = texto
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(enviando = false) }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            enviando = false,
                            erro = error.message
                        )
                    }
                }
            )
        }
    }
    
    fun visualizarMensagem(mensagem: Mensagem) {
        if (mensagem.visualizada) return
        
        viewModelScope.launch {
            visualizarMensagemUseCase(conversaId, mensagem.id)
        }
    }
    
    private fun sincronizarMensagens() {
        viewModelScope.launch {
            try {
                getMensagensUseCase.sincronizar(conversaId)
            } catch (e: Exception) {
                // Tratar erro
            }
        }
    }
}

data class ChatUiState(
    val enviando: Boolean = false,
    val erro: String? = null
)
```

#### 7. WebSocket Service

**WebSocketService.kt:**
```kotlin
@AndroidEntryPoint
class WebSocketService : Service() {
    
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var conversaRepository: ConversaRepository
    @Inject lateinit var mensagemRepository: MensagemRepository
    
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        conectar()
    }
    
    private fun conectar() {
        val request = Request.Builder()
            .url(BuildConfig.WS_URL)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Autentica com JWT
                lifecycleScope.launch {
                    val token = appPreferences.getToken()
                    val loginMsg = JSONObject().apply {
                        put("tipo", 1)  // Login
                        put("token", token)
                    }
                    webSocket.send(loginMsg.toString())
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                processarMensagem(text)
            }
            
            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                // Tentar reconectar após delay
                lifecycleScope.launch {
                    delay(5000)
                    conectar()
                }
            }
        })
    }
    
    private fun processarMensagem(json: String) {
        lifecycleScope.launch {
            try {
                val obj = JSONObject(json)
                val tipo = obj.getInt("tipo")
                
                when (tipo) {
                    2 -> {  // Nova Mensagem
                        val titulo = obj.getString("titulo")
                        val mensagem = obj.getString("mensagem")
                        mostrarNotificacao(titulo, mensagem)
                        
                        // Sincronizar conversas
                        conversaRepository.sincronizarConversas()
                    }
                    
                    3 -> {  // Atualização de Status
                        val conversaId = obj.getInt("grupo")
                        val mensagensIds = obj.getString("mensagens")
                            .split(",")
                            .map { it.toInt() }
                        
                        // Atualizar status localmente
                        mensagensIds.forEach { id ->
                            mensagemRepository.atualizarStatus(id)
                        }
                    }
                    
                    51 -> {  // Chamada Recebida
                        val chamadaId = obj.getInt("chamada_id")
                        val usuarioId = obj.getInt("usuario_id")
                        mostrarTelaChamada(chamadaId, usuarioId)
                    }
                    
                    // ... outros tipos
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroy() {
        webSocket?.close(1000, "Service destruído")
        super.onDestroy()
    }
}
```

#### 8. Sistema de Chamadas

**ChamadaManager.kt:**
```kotlin
@Singleton
class ChamadaManager @Inject constructor(
    private val api: ConversaApi,
    private val appPreferences: AppPreferences
) {
    
    private var socket: Socket? = null
    private var audioThread: Thread? = null
    private var isRecording = false
    
    suspend fun iniciarChamada(usuarios: List<Int>): Result<Chamada> {
        return try {
            val request = ChamadaRequest(
                tipo = if (usuarios.size == 2) 1 else 2,
                usuarios = usuarios.map { UsuarioIdDto(it) }
            )
            
            val response = api.iniciarChamada(request)
            
            // Conecta ao servidor TCP
            conectarServidor(response.id)
            
            Result.success(response.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun aceitarChamada(chamadaId: Int) {
        api.entrarChamada(ChamadaIdRequest(chamadaId))
        conectarServidor(chamadaId)
    }
    
    private suspend fun conectarServidor(chamadaId: Int) {
        withContext(Dispatchers.IO) {
            try {
                socket = Socket(BuildConfig.TCP_HOST, BuildConfig.TCP_PORT)
                
                val output = DataOutputStream(socket!!.getOutputStream())
                val input = DataInputStream(socket!!.getInputStream())
                
                // Registra cliente
                val userId = appPreferences.getUserId()
                val registerPacket = ByteBuffer.allocate(5).apply {
                    put(0.toByte())  // Tipo: Registrar
                    putInt(userId)
                }.array()
                output.write(registerPacket)
                
                // Inicia captura e reprodução
                iniciarAudio(chamadaId, output, input)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun iniciarAudio(
        chamadaId: Int,
        output: DataOutputStream,
        input: DataInputStream
    ) {
        isRecording = true
        
        // Thread de captura
        Thread {
            val audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
            )
            
            audioRecord.startRecording()
            
            val buffer = ByteArray(2048)
            
            while (isRecording) {
                val bytesRead = audioRecord.read(buffer, 0, buffer.size)
                
                if (bytesRead > 0) {
                    // Monta pacote: [tipo=1][chamada_id][audio]
                    val packet = ByteBuffer.allocate(5 + bytesRead).apply {
                        put(1.toByte())
                        putInt(chamadaId)
                        put(buffer, 0, bytesRead)
                    }.array()
                    
                    output.write(packet)
                }
            }
            
            audioRecord.stop()
            audioRecord.release()
        }.start()
        
        // Thread de reprodução
        Thread {
            val audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                44100,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                AudioTrack.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ),
                AudioTrack.MODE_STREAM
            )
            
            audioTrack.play()
            
            while (isRecording) {
                try {
                    // Recebe: [ClientID: 4 bytes][Audio: N bytes]
                    val clientId = input.readInt()
                    val audioSize = input.available()
                    
                    if (audioSize > 0) {
                        val audioData = ByteArray(audioSize)
                        input.read(audioData)
                        
                        audioTrack.write(audioData, 0, audioData.size)
                    }
                } catch (e: Exception) {
                    break
                }
            }
            
            audioTrack.stop()
            audioTrack.release()
        }.start()
    }
    
    fun finalizarChamada() {
        isRecording = false
        socket?.close()
        socket = null
    }
}
```

---

### Checklist de Implementação

#### Fase 1 - Setup Básico
- [ ] Criar projeto Android no Android Studio
- [ ] Configurar dependências (Retrofit, Room, Hilt, etc.)
- [ ] Implementar estrutura de pacotes
- [ ] Configurar Hilt (Application class)

#### Fase 2 - Autenticação
- [ ] Criar tela de login
- [ ] Implementar LoginRepository
- [ ] Implementar LoginUseCase
- [ ] Implementar LoginViewModel
- [ ] Salvar token JWT no DataStore
- [ ] Implementar AuthInterceptor

#### Fase 3 - Conversas
- [ ] Criar entidades Room (Conversa, Usuario)
- [ ] Implementar ConversaDao
- [ ] Implementar ConversaRepository
- [ ] Criar tela de lista de conversas
- [ ] Implementar sincronização com servidor
- [ ] Implementar navegação para chat

#### Fase 4 - Mensagens
- [ ] Criar entidades Room (Mensagem, Conteudo)
- [ ] Implementar MensagemDao
- [ ] Implementar MensagemRepository
- [ ] Criar tela de chat
- [ ] Implementar envio de mensagens
- [ ] Implementar visualização de mensagens
- [ ] Implementar status de mensagens

#### Fase 5 - Anexos
- [ ] Implementar upload de imagens
- [ ] Implementar upload de arquivos
- [ ] Implementar download de anexos
- [ ] Implementar cache local
- [ ] Implementar visualização de imagens

#### Fase 6 - WebSocket
- [ ] Implementar WebSocketService
- [ ] Conectar e autenticar
- [ ] Processar eventos em tempo real
- [ ] Atualizar UI em tempo real

#### Fase 7 - Notificações
- [ ] Configurar Firebase
- [ ] Implementar FirebaseMessagingService
- [ ] Enviar token FCM ao servidor
- [ ] Mostrar notificações locais
- [ ] Tratar notificações (abrir chat)

#### Fase 8 - Chamadas de Áudio
- [ ] Implementar ChamadaManager
- [ ] Criar tela de chamada
- [ ] Implementar conexão TCP
- [ ] Implementar captura de áudio (AudioRecord)
- [ ] Implementar reprodução de áudio (AudioTrack)
- [ ] Tratar eventos de chamada via WebSocket
- [ ] Implementar UI de chamada (aceitar/recusar)

#### Fase 9 - Polimento
- [ ] Adicionar animações
- [ ] Melhorar tratamento de erros
- [ ] Adicionar testes unitários
- [ ] Adicionar testes instrumentados
- [ ] Otimizar performance
- [ ] Adicionar logging

---

### Considerações Importantes

#### Permissões (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

#### Segurança
- **NUNCA** armazene senhas em texto plano
- Use HTTPS em produção
- Valide todos os inputs
- Implemente rate limiting no servidor
- Use ProGuard/R8 para ofuscar código

#### Performance
- Implemente paginação ao carregar mensagens
- Use cache de imagens (Coil ou Glide)
- Otimize consultas ao banco Room
- Use WorkManager para sincronização em background
- Implemente lazy loading de conversas

#### UX
- Mostre indicadores de carregamento
- Implemente pull-to-refresh
- Adicione feedback tátil
- Suporte modo escuro
- Trate casos offline graciosamente

---

## 📚 Referências e Recursos

### Documentação Oficial
- [Retrofit](https://square.github.io/retrofit/)
- [Room](https://developer.android.com/training/data-storage/room)
- [Hilt](https://dagger.dev/hilt/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Firebase FCM](https://firebase.google.com/docs/cloud-messaging)

### Boas Práticas
- [Android Architecture Guide](https://developer.android.com/topic/architecture)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Material Design 3](https://m3.material.io/)

---

## 📝 Notas Finais

### Sobre Chamadas de Áudio

A implementação de chamadas de áudio está **parcialmente concluída**:

- ✅ **Servidor**: Totalmente implementado (REST API + TCP + Sinalização)
- ⚠️ **Cliente Windows**: Interface pronta, **falta implementar captura/reprodução**
- ❌ **Cliente Kotlin**: Ainda não implementado

Os arquivos em `D:\Claude\Conversa\Call\audio\` contêm a implementação de referência para Windows:
- `AudioCapture.pas`: Captura de áudio via WASAPI
- `AudioPlayer.pas`: Reprodução de áudio
- `AudioTypes.pas`: Buffer circular de áudio

### Próximos Passos

1. Implementar captura/reprodução de áudio no cliente Kotlin
2. Testar sistema de chamadas end-to-end
3. Otimizar qualidade de áudio (codec, noise reduction)
4. Adicionar suporte a vídeo (futuro)

---

## 🤝 Contribuindo

Para contribuir com o projeto:

1. Fork o repositório
2. Crie uma branch para sua feature
3. Implemente e teste
4. Faça commit com mensagens descritivas
5. Abra um Pull Request

---

**Documentação gerada em:** 21/10/2025  
**Versão:** 1.0  
**Autor:** Claude (Anthropic) com base no código-fonte do projeto Conversa
