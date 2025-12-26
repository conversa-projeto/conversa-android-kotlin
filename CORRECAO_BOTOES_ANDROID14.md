# ✅ Correção: Botões Funcionando no Android 14+

## Problemas Identificados

### 1. Background Activity Launch Blocked
```
ActivityTaskManager: Background activity launch blocked!
```
**Causa:** Android 14+ bloqueia abertura de Activities a partir de BroadcastReceivers.

### 2. Permissão VIBRATE Faltando
```
java.lang.SecurityException: cancelVibrate: Neither user 10442 nor current process has android.permission.VIBRATE
```
**Causa:** App não tinha permissão para vibrar.

---

## Soluções Aplicadas

### 1. AndroidManifest.xml

#### Adicionada Permissão VIBRATE (linha 9)

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

**Por que é necessário:**
- CallRingtoneManager usa vibração quando o ringtone toca
- Sem essa permissão, o app crasha ao tentar parar a vibração

---

### 2. SocketService.kt

#### Mudança no Botão "Atender" (linhas 244-259)

**ANTES (errado - usava BroadcastReceiver):**
```kotlin
// Botão Atender
val answerIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
    action = ChamadaActionReceiver.ACTION_ANSWER
    putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
    putExtra(ChamadaActionReceiver.EXTRA_USUARIO_ID, usuarioId)
}
val answerPendingIntent = PendingIntent.getBroadcast(
    this,
    chamadaId,
    answerIntent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)
```

**DEPOIS (correto - abre Activity diretamente):**
```kotlin
// Botão Atender - Abre ChamadaActivity diretamente
val answerIntent = Intent(this, ChamadaActivity::class.java).apply {
    putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
    putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
    putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
    putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
    putExtra("auto_answer", true)
    putExtra("from_notification_action", true) // Para parar ringtone
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val answerPendingIntent = PendingIntent.getActivity(
    this,
    chamadaId,
    answerIntent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)
```

**Mudanças importantes:**
1. ❌ Removido uso de `ChamadaActionReceiver` (BroadcastReceiver)
2. ✅ Intent aponta direto para `ChamadaActivity`
3. ✅ Usa `PendingIntent.getActivity()` em vez de `.getBroadcast()`
4. ✅ Adiciona extra `from_notification_action=true` para sinalizar que veio de notificação

**Botão "Recusar" permanece usando BroadcastReceiver:**
- Não precisa abrir activity, apenas fazer API call
- BroadcastReceiver funciona normalmente para isso

---

### 3. ChamadaActivity.kt

#### Adicionado Tratamento em onCreate() (linhas 92-96)

```kotlin
// Para ringtone e remove notificação se veio de ação de notificação
if (intent.getBooleanExtra("from_notification_action", false)) {
    Log.d(TAG, "📞 Aberto via botão de notificação - parando ringtone")
    pararRingtoneERemoverNotificacao()
}
```

**Por que é necessário:**
- Quando usuário clica em "Atender", a activity abre diretamente
- Precisamos parar o ringtone e remover a notificação
- Antes, isso era feito pelo `ChamadaActionReceiver`

#### Novo Método: pararRingtoneERemoverNotificacao() (linhas 595-615)

```kotlin
/**
 * Para o ringtone e remove a notificação de chamada
 * Chamado quando a activity é aberta via botão da notificação
 */
private fun pararRingtoneERemoverNotificacao() {
    try {
        // Para ringtone via SocketService
        val serviceIntent = Intent(this, com.conversa.conversa.service.SocketService::class.java).apply {
            action = "STOP_RINGTONE"
        }
        startService(serviceIntent)

        // Remove notificação
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(1002) // NOTIFICATION_ID_CHAMADA

        Log.d(TAG, "Ringtone parado e notificação removida")
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao parar ringtone/remover notificação", e)
    }
}
```

**O que faz:**
1. Envia comando `STOP_RINGTONE` para SocketService
2. Remove a notificação de chamada
3. Logs para debug

---

## Por Que Isso Funciona no Android 14+

### Problema: Background Activity Start (BAS) Restrictions

Desde o Android 10, mas especialmente no Android 14+, o sistema bloqueia:
- Apps em background abrindo Activities diretamente
- BroadcastReceivers abrindo Activities (considerado "trampoline")

### Nossa Solução: Usar PendingIntent.getActivity()

Quando usamos `PendingIntent.getActivity()` em uma **notificação**:
- ✅ O PendingIntent é executado com o contexto do **sistema** (NotificationManager)
- ✅ O sistema tem permissão para abrir Activities a qualquer momento
- ✅ Não é considerado "trampoline" porque vai direto da notificação → activity

### Por Que BroadcastReceiver Não Funciona:

```
Notificação → BroadcastReceiver → Activity
              ↑                    ↑
         App em background    BLOQUEADO!
```

### Por Que PendingIntent.getActivity() Funciona:

```
Notificação → Activity
       ↑
   Sistema (permitido sempre)
```

---

## Fluxo Completo Agora

### Quando Usuário Clica em "ATENDER":

```
1. Usuário clica no botão "Atender" na notificação
   ↓
2. NotificationManager executa PendingIntent.getActivity()
   ↓
3. Sistema abre ChamadaActivity (permitido)
   ↓
4. ChamadaActivity.onCreate() verifica extra "from_notification_action"
   ↓
5. ChamadaActivity.pararRingtoneERemoverNotificacao() executado
   ↓
6. Envia comando STOP_RINGTONE para SocketService
   ↓
7. Remove notificação
   ↓
8. ChamadaActivity mostra interface de chamada
   ↓
9. auto_answer=true faz atender automaticamente
```

### Quando Usuário Clica em "RECUSAR":

```
1. Usuário clica no botão "Recusar" na notificação
   ↓
2. NotificationManager executa PendingIntent.getBroadcast()
   ↓
3. ChamadaActionReceiver.recusarChamada() executado
   ↓
4. Remove notificação
   ↓
5. Para ringtone
   ↓
6. Envia recusa para API
```

---

## Logs Esperados Após Correção

### Ao clicar em ATENDER:

```
ChamadaActivity: 📞 Aberto via botão de notificação - parando ringtone
ChamadaActivity: Ringtone parado e notificação removida
SocketService: Recebido comando STOP_RINGTONE
SocketService: Ringtone parado
ChamadaActivity: Chamada iniciada com auto_answer=true
```

**NÃO deve aparecer:**
```
ActivityTaskManager: Background activity launch blocked!
```

### Ao clicar em RECUSAR:

```
ChamadaActionReceiver: Recusando chamada 560
ChamadaActionReceiver: Comando para parar ringtone enviado
SocketService: Recebido comando STOP_RINGTONE
SocketService: Ringtone parado
ChamadaActionReceiver: Chamada recusada com sucesso
```

---

## Resumo das Mudanças

### AndroidManifest.xml
1. ✅ Adicionada permissão `VIBRATE`

### SocketService.kt
1. ✅ Botão "Atender" agora usa `PendingIntent.getActivity()` direto para `ChamadaActivity`
2. ✅ Adiciona extra `from_notification_action=true`
3. ✅ Botão "Recusar" continua usando BroadcastReceiver (funciona para API call)

### ChamadaActivity.kt
1. ✅ Verifica extra `from_notification_action` em `onCreate()`
2. ✅ Novo método `pararRingtoneERemoverNotificacao()`
3. ✅ Para ringtone e remove notificação quando aberto via botão

---

## Benefícios

✅ **Compatível com Android 14+:** Não é bloqueado por Background Activity Start
✅ **Sem trampolines:** Intent vai direto de notificação → activity
✅ **Ringtone para corretamente:** Sem erro de permissão VIBRATE
✅ **Notificação removida:** Desaparece ao atender
✅ **Código mais simples:** Menos intermediários (sem BroadcastReceiver desnecessário)

---

## Próximo Passo: Rebuild

```bash
./gradlew clean
./gradlew assembleDebug
```

Ou no Android Studio:
1. Build → Clean Project
2. Build → Rebuild Project
3. Run → Run 'app'

**AGORA OS BOTÕES DEVEM FUNCIONAR CORRETAMENTE NO ANDROID 14+!**
