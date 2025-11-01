# 📞 Documentação - Fluxo de Chamadas no Projeto Android Kotlin

## 📋 Visão Geral

O sistema de chamadas de áudio do projeto Conversa utiliza uma arquitetura de **3 camadas independentes** para gerenciar o ciclo completo de uma chamada, desde o início até o encerramento.

---

## 🎯 Arquitetura das Chamadas

### Três Camadas Principais

```
┌─────────────────────────────────────────────┐
│          1. API REST (HTTP/HTTPS)          │
│  Gerenciamento do ciclo de vida da chamada │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         2. WebSocket (Tempo Real)           │
│  Sinalização de eventos entre participantes │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│         3. Servidor TCP (Porta 9090)        │
│    Transmissão dos pacotes de áudio raw    │
└─────────────────────────────────────────────┘
```

---

## 🔄 Fluxo Completo de uma Chamada 1:1

### Fase 1: Iniciação

1. **Usuário A clica em "Ligar"**
   - App faz requisição REST para iniciar chamada
   - Endpoint: `PUT /chamada/iniciar`
   - Envia: tipo da chamada + IDs dos usuários

2. **Servidor cria chamada**
   - Registra no banco de dados (PostgreSQL)
   - Estado inicial: **PENDENTE**
   - Gera ID único da chamada

3. **Servidor notifica Usuário B**
   - Via WebSocket: evento `ChamadaRecebida`
   - Contém: ID da chamada + ID do chamador
   - UI do Usuário B mostra tela de chamada recebida

### Fase 2: Aceitação

4. **Usuário B clica em "Aceitar"**
   - App faz requisição REST
   - Endpoint: `POST /chamada/entrar`
   - Envia ID da chamada

5. **Servidor atualiza estado**
   - Estado muda para: **EM ANDAMENTO**
   - Registra horário de entrada do Usuário B

6. **Servidor notifica Usuário A**
   - Via WebSocket: evento `UsuarioEntrou`
   - Contém: ID da chamada + ID de quem entrou
   - UI do Usuário A muda para "Conectado"

### Fase 3: Estabelecimento de Áudio

7. **Ambos conectam ao servidor TCP**
   - Conexão TCP na porta 9090
   - Cada um envia pacote de registro com seu ID
   - Servidor mapeia socket ↔ usuário

8. **Sistema de áudio fica pronto**
   - AudioRecord configurado para captura
   - AudioTrack configurado para reprodução
   - Formato: PCM 16-bit, 44.1kHz, Mono

### Fase 4: Transmissão de Áudio

9. **Loop contínuo de áudio**
   
   **Usuário A:**
   - Captura áudio do microfone (2048 bytes)
   - Monta pacote: [tipo][chamada_id][dados_audio]
   - Envia via TCP ao servidor
   
   **Servidor:**
   - Recebe pacote do Usuário A
   - Identifica a chamada
   - Retransmite para Usuário B
   - Formato: [client_id_origem][dados_audio]
   
   **Usuário B:**
   - Recebe áudio do Usuário A
   - Decodifica pacote
   - Reproduz no speaker

   **O processo se repete no sentido contrário simultaneamente**

### Fase 5: Encerramento

10. **Usuário A clica em "Desligar"**
    - App faz requisição REST
    - Endpoint: `POST /chamada/finalizar`
    - Envia ID da chamada

11. **Servidor encerra chamada**
    - Estado muda para: **ENCERRADA**
    - Registra horário de finalização
    - Remove mapeamentos de conexões TCP

12. **Servidor notifica Usuário B**
    - Via WebSocket: evento `ChamadaFinalizada`
    - UI do Usuário B retorna ao normal

13. **Ambos fecham conexões TCP**
    - Liberam recursos de áudio
    - Fecham sockets TCP

---

## 👥 Chamadas em Grupo (Mais de 2 Participantes)

### Diferenças Principais

1. **Múltiplos convites simultâneos**
   - Servidor envia `ChamadaRecebida` para todos
   - Cada um pode aceitar independentemente

2. **Estado muda com primeiro aceite**
   - Assim que alguém aceita → **EM ANDAMENTO**
   - Outros podem entrar depois

3. **Retransmissão para múltiplos destinos**
   - Servidor retransmite áudio de A para B, C, D...
   - Cada participante recebe áudio de TODOS os outros
   - **Mixing acontece no cliente**

4. **Entrada/saída dinâmica**
   - Participantes podem entrar/sair durante a chamada
   - Evento `UsuarioEntrou` notifica novos membros
   - Evento `UsuarioSaiu` notifica saídas
   - Chamada continua até o último sair ou alguém finalizar

---

## 🔊 Sistema de Áudio

### Captura (AudioRecord)

- **Fonte**: Microfone do dispositivo
- **Formato**: PCM 16-bit
- **Taxa de amostragem**: 44.100 Hz
- **Canais**: 1 (Mono)
- **Tamanho do buffer**: 2048 bytes (~23ms)

### Processamento no Cliente

1. Captura chunk de áudio do microfone
2. Verifica se há dados válidos
3. Empacota: tipo + ID chamada + dados
4. Envia via TCP ao servidor

### Reprodução (AudioTrack)

1. Recebe pacotes do servidor TCP
2. Extrai ID do remetente e dados de áudio
3. **Mixing**: Se múltiplas fontes, combina os áudios
4. Reproduz no alto-falante

### Mixing de Áudio (Apenas em Grupos)

**Conceito**: Combinar múltiplos streams de áudio em um único output

**Processo**:
1. Cliente mantém buffer separado por participante
2. Quando chega áudio: armazena no buffer do remetente
3. A cada frame de reprodução:
   - Soma todos os samples dos buffers ativos
   - Aplica normalização (evitar distorção)
   - Aplica clipping (limitar amplitude)
4. Reproduz áudio mixado

**Importante**: O servidor **NÃO faz mixing**, apenas retransmite os pacotes raw. A mixagem é responsabilidade do cliente.

---

## 📊 Estados da Chamada

### Estados no Servidor (Banco de Dados)

| Estado | Valor | Descrição |
|--------|-------|-----------|
| Pendente | 1 | Aguardando aceitação |
| Recusada | 2 | Alguém recusou |
| Em Andamento | 3 | Chamada ativa |
| Encerrada | 4 | Finalizada normalmente |
| Não Atendida | 5 | Timeout sem resposta |
| Cancelada | 6 | Cancelada antes de aceitar |

### Estados Locais (App Android)

| Estado | Valor | Tela Correspondente |
|--------|-------|---------------------|
| Desconhecido | 0 | - |
| Iniciando | 1 | "Chamando..." |
| Recebendo | 2 | "Recebendo chamada" |
| Em Andamento | 3 | Tela de chamada ativa |
| Finalizada | 4 | "Chamada encerrada" |
| Perdida | 5 | "Chamada não atendida" |
| Recusada | 6 | "Chamada recusada" |

---

## 🔔 Eventos WebSocket

### Eventos Recebidos pelo Cliente

| Tipo | Nome | Quando Acontece |
|------|------|-----------------|
| 51 | ChamadaRecebida | Alguém está ligando para você |
| 52 | ChamadaFinalizada | Chamada foi encerrada |
| 53 | UsuarioRecusou | Alguém recusou a chamada |
| 54 | UsuarioEntrou | Alguém entrou na chamada |
| 55 | UsuarioSaiu | Alguém saiu da chamada |

### Estrutura dos Eventos

```json
{
  "tipo": 51,
  "chamada_id": 1,
  "usuario_id": 2
}
```

---

## 🌐 Endpoints REST Utilizados

### Gerenciamento de Chamadas

| Método | Endpoint | Propósito |
|--------|----------|-----------|
| PUT | /chamada/iniciar | Criar nova chamada |
| POST | /chamada/entrar | Aceitar e entrar |
| POST | /chamada/recusar | Recusar convite |
| POST | /chamada/sair | Sair mantendo chamada |
| POST | /chamada/cancelar | Cancelar antes de aceitar |
| POST | /chamada/finalizar | Encerrar forçadamente |
| GET | /chamada/dados | Obter informações |

---

## 🔐 Protocolo TCP (Porta 9090)

### Tipos de Pacote

**1. Registro do Cliente**
```
[1 byte: tipo = 0] + [4 bytes: user_id]
Total: 5 bytes
```

**2. Dados de Áudio**
```
[1 byte: tipo = 1] + [4 bytes: chamada_id] + [N bytes: audio_data]
Total: 5 + N bytes (tipicamente 5 + 2048)
```

### Fluxo TCP

1. Cliente conecta TCP:9090
2. Cliente envia pacote de registro
3. Servidor mapeia: socket ↔ usuário
4. Durante chamada: troca contínua de pacotes de áudio
5. Ao encerrar: cliente fecha socket

---

## ⚠️ Tratamento de Erros

### Cenários Comuns

**1. Perda de Conexão TCP**
- Detecta desconexão
- Tenta reconectar (3 tentativas)
- Se falhar: encerra chamada localmente
- Notifica servidor via REST

**2. Timeout de Aceitação**
- Após 60s sem resposta
- Servidor muda estado para NÃO ATENDIDA
- Notifica iniciador via WebSocket

**3. Participante Sai Durante Grupo**
- Servidor notifica outros via `UsuarioSaiu`
- Áudio desse participante para de chegar
- Chamada continua com os restantes

**4. Buffer de Áudio Vazio**
- Se não recebe áudio por muito tempo
- Contadores incrementam
- Após threshold: considera desconectado
- Pode tentar reconectar ou encerrar

---

## 📱 Componentes no App Android

### Estrutura Atual do Projeto

O projeto está organizado seguindo Clean Architecture:

```
app/src/main/java/com/conversa/conversa/
├── ui/chat/              # Telas de chat e componentes visuais
├── adapter/              # Adaptadores RecyclerView
├── data/
│   ├── api/             # Cliente REST e helpers
│   ├── model/           # Modelos de dados
│   └── preferences/     # Gerenciamento de preferências
└── (outros pacotes conforme necessário)
```

### Componentes Necessários para Chamadas

**Ainda não implementados**, mas seguirão esta estrutura:

```
app/src/main/java/com/conversa/conversa/
├── ui/
│   └── chamada/
│       ├── ChamadaActivity.kt           # Tela principal de chamada
│       ├── ChamadaIncomingActivity.kt   # Tela de chamada recebida
│       └── ChamadaGroupActivity.kt      # Tela para chamadas em grupo
├── data/
│   ├── audio/
│   │   ├── AudioCaptureManager.kt       # Gerencia captura (AudioRecord)
│   │   ├── AudioPlaybackManager.kt      # Gerencia reprodução (AudioTrack)
│   │   └── AudioMixer.kt                # Mixing de múltiplos streams
│   └── network/
│       ├── TcpAudioClient.kt            # Cliente TCP para áudio
│       └── ChamadaRepository.kt         # Gerencia APIs de chamada
└── service/
    └── ChamadaService.kt                 # Service de background para chamadas
```

---

## ✅ Checklist de Implementação

### Fase 1: Infraestrutura Base
- [ ] Implementar cliente TCP para áudio
- [ ] Criar gerenciador de captura (AudioRecord)
- [ ] Criar gerenciador de reprodução (AudioTrack)
- [ ] Implementar protocolo de pacotes

### Fase 2: Interface de Chamada
- [ ] Tela de chamada 1:1 (iniciando)
- [ ] Tela de chamada recebida
- [ ] Tela de chamada ativa
- [ ] Indicadores visuais (status, timer, ícones)

### Fase 3: Integração com API
- [ ] Implementar endpoints REST de chamada
- [ ] Conectar eventos WebSocket
- [ ] Gerenciar ciclo de vida (iniciar/aceitar/finalizar)
- [ ] Tratamento de erros e timeouts

### Fase 4: Áudio Avançado
- [ ] Implementar mixer de áudio
- [ ] Adicionar controles (mute, volume)
- [ ] Implementar jitter buffer
- [ ] Tratamento de packet loss

### Fase 5: Chamadas em Grupo
- [ ] Adaptar interface para múltiplos participantes
- [ ] Implementar mixing no cliente
- [ ] Gerenciar entrada/saída dinâmica
- [ ] Lista de participantes ativos

### Fase 6: Polimento
- [ ] Notificações de chamada
- [ ] Som de toque
- [ ] Tela de bloqueio durante chamada
- [ ] Histórico de chamadas
- [ ] Testes end-to-end

---

## 🎓 Conceitos Importantes

### 1. Por Que Três Camadas?

- **REST**: Gerenciamento estruturado e rastreável
- **WebSocket**: Notificações instantâneas sem polling
- **TCP**: Baixa latência para streaming de áudio

### 2. Por Que Mixing no Cliente?

- **Menor latência**: Servidor só retransmite
- **Escalabilidade**: Servidor suporta mais chamadas
- **Controle individual**: Cliente pode ajustar volume por participante

### 3. Por Que PCM Raw?

- **Simplicidade**: Sem overhead de codecs
- **Baixa latência**: Sem tempo de encode/decode
- **Desenvolvimento rápido**: Foco em funcionalidade

### 4. Formato dos Dados

- **PCM**: Pulse Code Modulation (áudio não comprimido)
- **16-bit**: 2 bytes por sample
- **44.1kHz**: 44.100 samples por segundo
- **Mono**: 1 canal de áudio
- **Taxa**: ~88KB/segundo de transmissão

---

## 📝 Observações Finais

### Estado Atual do Projeto

- ✅ Sistema de mensagens funcionando
- ✅ Envio de imagens e áudios gravados
- ✅ API REST implementada no servidor
- ✅ WebSocket funcionando
- ✅ Servidor TCP de áudio operacional
- ❌ **Cliente de chamadas não implementado**

### Próximos Passos

1. Começar pela **Fase 1** do checklist
2. Implementar chamada 1:1 simples primeiro
3. Testar extensivamente antes de grupos
4. Adicionar recursos avançados gradualmente

### Recursos de Referência

- Documentação oficial no diretório `documentacao-oficial/`
- Servidor de referência no repositório principal
- Cliente Windows FMX como exemplo de implementação

---

**Documentação criada em:** 31/10/2025  
**Versão:** 1.0  
**Status:** Cliente não implementado - Documentação para implementação futura
