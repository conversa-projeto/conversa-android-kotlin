# ✅ Correção Final: Permissões do Foreground Service

## Erro Encontrado

```
java.lang.SecurityException: Starting FGS with type phoneCall
requires permissions: all of the permissions allOf=true
[android.permission.FOREGROUND_SERVICE_PHONE_CALL]
any of the permissions allOf=false
[android.permission.MANAGE_OWN_CALLS, android.app.role.DIALER]
```

## O Que Isso Significa

O Android 14 exige que apps com `foregroundServiceType="phoneCall"` sejam:
- **Apps discadores do sistema** (como o app Telefone nativo)
- **OU** tenham a permissão especial `MANAGE_OWN_CALLS`

Como seu app **não é um discador do sistema**, ele não pode usar o tipo `phoneCall`.

---

## Solução Aplicada

Removido o tipo `phoneCall` do foreground service, mantendo apenas os tipos necessários.

### AndroidManifest.xml - Permissões

**ANTES:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

**DEPOIS:**
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
```

**Mudanças:**
- ❌ Removido `FOREGROUND_SERVICE_PHONE_CALL` (não permitido)
- ✅ Mantido `FOREGROUND_SERVICE_DATA_SYNC` (sincronização de dados via socket)
- ✅ Mantido `FOREGROUND_SERVICE_MICROPHONE` (chamadas VoIP)
- ✅ Adicionado `USE_FULL_SCREEN_INTENT` (notificações fullscreen)

---

### AndroidManifest.xml - Service

**ANTES:**
```xml
<service
    android:name=".service.SocketService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync|phoneCall|microphone"></service>
```

**DEPOIS:**
```xml
<service
    android:name=".service.SocketService"
    android:enabled="true"
    android:exported="false"
    android:foregroundServiceType="dataSync|microphone"></service>
```

**Mudanças:**
- ❌ Removido `phoneCall` do foregroundServiceType
- ✅ Mantido `dataSync` (para conexão socket)
- ✅ Mantido `microphone` (para chamadas de voz)

---

## Por Que Isso Funciona

### `dataSync` é suficiente para notificações de chamadas

O tipo `dataSync` permite:
- ✅ Manter conexão socket ativa em background
- ✅ Receber notificações de chamadas via socket
- ✅ Mostrar notificações de chamadas recebidas
- ✅ Tocar ringtone quando chamada chega

### `microphone` é para áudio das chamadas

O tipo `microphone` permite:
- ✅ Acessar microfone durante chamadas VoIP
- ✅ Processar áudio em tempo real

### `phoneCall` NÃO É NECESSÁRIO

O tipo `phoneCall` é **APENAS** para apps que:
- Substituem o discador do sistema (como Google Phone)
- Gerenciam chamadas do sistema operacional

**Seu app não precisa disso!** Ele só precisa:
1. Receber notificação via socket (`dataSync`)
2. Mostrar notificação ao usuário
3. Processar áudio da chamada (`microphone`)

---

## Permissões Finais (Resumo)

### Permissões de Foreground Service
✅ `FOREGROUND_SERVICE` - Básica
✅ `FOREGROUND_SERVICE_DATA_SYNC` - Socket/dados
✅ `FOREGROUND_SERVICE_MICROPHONE` - Áudio VoIP

### Permissões de Notificações
✅ `POST_NOTIFICATIONS` - Mostrar notificações
✅ `USE_FULL_SCREEN_INTENT` - Fullscreen em tela bloqueada

### Permissões de Áudio/Conectividade
✅ `RECORD_AUDIO` - Gravar áudio (já estava)
✅ `MODIFY_AUDIO_SETTINGS` - Ajustar volume (já estava)
✅ `BLUETOOTH_CONNECT` - Headsets Bluetooth

### Permissões Gerais
✅ `INTERNET` - Conexão de rede
✅ `ACCESS_NETWORK_STATE` - Estado da rede
✅ `WAKE_LOCK` - Manter CPU ativa

---

## Agora Deve Funcionar

Após rebuild, o service deve iniciar **SEM ERRO**:

**Logs esperados:**
```
SocketService: Service iniciado
SocketService: Service criado
SocketService: ✅ Conectado ao socket: host:8090
MainActivity: ✅ SocketService iniciado: host:8090
```

**NÃO deve aparecer:**
```
java.lang.SecurityException: Starting FGS with type phoneCall
```

---

## Rebuild Necessário

```bash
./gradlew clean
./gradlew assembleDebug
```

Ou no Android Studio:
1. Build → Clean Project
2. Build → Rebuild Project
3. Run → Run 'app'

---

## Status Final

✅ Removido tipo `phoneCall` (não autorizado)
✅ Mantido `dataSync` (suficiente para socket/notificações)
✅ Mantido `microphone` (necessário para áudio)
✅ Adicionado `USE_FULL_SCREEN_INTENT` (fullscreen)
✅ Permissões corretas para VoIP sem ser discador do sistema

**PRONTO PARA REBUILD!**
