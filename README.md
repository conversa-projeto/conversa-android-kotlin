# Conversa - Cliente Android Kotlin

## 📱 Sobre o Projeto

Cliente Android em Kotlin para o sistema de mensagens Conversa. Este aplicativo permite aos usuários fazer login, autenticar-se no servidor e visualizar uma tela inicial após login bem-sucedido.

## ✨ Funcionalidades Implementadas

### Fase 1 - Autenticação (✅ Concluída)

- ✅ **Tela Splash**: Tela inicial em branco que verifica autenticação prévia
- ✅ **Verificação de Conexão**: Verifica se há token JWT salvo localmente
- ✅ **Tela de Login**: Interface moderna com validação de campos
- ✅ **Integração com API**: Comunicação com servidor via Retrofit
- ✅ **Armazenamento Seguro**: Token JWT salvo com DataStore
- ✅ **Mensagem de Sucesso**: Feedback visual após login bem-sucedido
- ✅ **Tela Principal**: Exibe mensagem de boas-vindas ao usuário
- ✅ **Logout**: Opção para sair e limpar dados salvos

## 🛠️ Tecnologias Utilizadas

- **Kotlin**: Linguagem principal
- **Retrofit**: Cliente HTTP para comunicação com API REST
- **OkHttp**: Cliente HTTP robusto com logging
- **DataStore**: Armazenamento de preferências (token, usuário)
- **ViewBinding**: Binding de views type-safe
- **Coroutines**: Programação assíncrona
- **Material Design 3**: Interface moderna e responsiva

## 📋 Configuração

### 1. Configurar URL do Servidor

Edite o arquivo `app/build.gradle.kts` e altere a URL do servidor:

```kotlin
buildConfigField("String", "API_URL", "\"http://SEU_SERVIDOR:PORTA/\"")
```

**Exemplo:**
```kotlin
buildConfigField("String", "API_URL", "\"http://192.168.1.100:9000/\"")
```

⚠️ **Importante**: 
- Use o IP da sua máquina na rede local (não use `localhost` ou `127.0.0.1`)
- Certifique-se de que o servidor Conversa está rodando
- A porta padrão do servidor é `9000`

### 2. Compilar o Projeto

```bash
./gradlew build
```

### 3. Executar no Emulador ou Dispositivo

No Android Studio:
1. Conecte um dispositivo ou inicie um emulador
2. Clique em "Run" (▶️)

## 🔐 Fluxo de Autenticação

```
┌─────────────────┐
│  SplashActivity │
│  (Tela Inicial) │
└────────┬────────┘
         │
         ├─ Verifica token salvo?
         │
    ┌────▼────┐
    │ Token?  │
    └─┬────┬──┘
      │    │
   Não│    │Sim
      │    │
      │    └──> Verifica conexão servidor
      │              │
      │              ├─ OK: MainActivity
      │              └─ Erro: LoginActivity
      │
      ▼
┌──────────────┐
│LoginActivity │
│              │
│ - Usuário    │
│ - Senha      │
│ [Entrar]     │
└──────┬───────┘
       │
       ├─ POST /login
       │
   ┌───▼────┐
   │Servidor│
   └───┬────┘
       │
       ├─ 200: Sucesso
       │   └─> Salva token + dados
       │       └─> MainActivity
       │
       └─ 401: Erro
           └─> Mostra mensagem
```

## 📁 Estrutura do Projeto

```
app/src/main/java/com/conversa/conversa/
├── data/
│   ├── api/
│   │   ├── ConversaApi.kt         # Interface Retrofit
│   │   └── RetrofitClient.kt      # Cliente HTTP configurado
│   ├── model/
│   │   ├── LoginRequest.kt        # DTO de requisição
│   │   └── LoginResponse.kt       # DTO de resposta
│   └── preferences/
│       └── UserPreferences.kt     # Gerenciamento de preferências
├── SplashActivity.kt              # Tela inicial/splash
├── LoginActivity.kt               # Tela de login
└── MainActivity.kt                # Tela principal (pós-login)
```

## 🔄 Próximos Passos

As seguintes funcionalidades serão implementadas nas próximas fases:

### Fase 2 - Conversas
- [ ] Listar conversas do usuário
- [ ] Sincronização com servidor
- [ ] Exibir última mensagem de cada conversa
- [ ] Contador de mensagens não lidas

### Fase 3 - Mensagens
- [ ] Tela de chat
- [ ] Envio de mensagens de texto
- [ ] Recebimento em tempo real (WebSocket)
- [ ] Status de mensagens (enviada, recebida, visualizada)
- [ ] Banco de dados local (Room)

### Fase 4 - Anexos
- [ ] Envio de imagens
- [ ] Envio de arquivos
- [ ] Mensagens de áudio
- [ ] Download e cache de anexos

### Fase 5 - Chamadas de Áudio
- [ ] Iniciar chamada
- [ ] Receber chamada
- [ ] Transmissão de áudio (TCP)
- [ ] Interface de chamada

### Fase 6 - Notificações
- [ ] Firebase Cloud Messaging (FCM)
- [ ] Notificações push
- [ ] Notificações locais

## 🐛 Troubleshooting

### Erro de Conexão

Se você receber erro de conexão com o servidor:

1. **Verifique se o servidor está rodando**
   ```bash
   # No servidor Delphi, verifique se está escutando na porta 9000
   ```

2. **Verifique o IP no build.gradle.kts**
   - Deve ser o IP da máquina na rede local
   - Para descobrir o IP:
     - Windows: `ipconfig`
     - Linux/Mac: `ifconfig` ou `ip addr`

3. **Firewall**
   - Certifique-se de que a porta 9000 está aberta no firewall
   - Permita conexões de entrada na porta 9000

4. **Cleartext Traffic**
   - O projeto já está configurado com `android:usesCleartextTraffic="true"`
   - Isso permite HTTP (não-HTTPS) em desenvolvimento

### Erro 401 - Usuário/Senha Incorretos

- Verifique se o usuário existe no banco de dados PostgreSQL
- Confirme que a senha está correta
- O servidor retorna 401 para credenciais inválidas

## 📝 Notas de Desenvolvimento

- O projeto usa **DataStore** ao invés de SharedPreferences (mais moderno)
- O token JWT é armazenado localmente para autenticação automática
- **Coroutines** são usadas para operações assíncronas
- O projeto segue a arquitetura **MVVM** parcialmente
- ViewBinding está ativado para evitar findViewById()

## 📄 Licença

Este projeto faz parte do sistema Conversa open-source.

## 👨‍💻 Desenvolvimento

Desenvolvido seguindo a documentação técnica oficial do projeto Conversa.

Para mais informações sobre a API e o servidor, consulte:
- `documentacao-oficial/DOCUMENTACAO_TECNICA.md`
- `documentacao-oficial/DOCUMENTACAO_MENSAGENS_VISUAL.md`
