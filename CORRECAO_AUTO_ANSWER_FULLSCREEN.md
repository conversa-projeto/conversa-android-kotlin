# Correção: Auto-Answer em Fullscreen Intent

## Problema Identificado

**Sintoma**: Quando o dispositivo estava bloqueado, a chamada abria a tela automaticamente E já atendia a chamada, sem dar chance do usuário decidir.

**Comportamento Esperado**:
- Dispositivo bloqueado → Abre tela automático (fullscreen intent)
- Usuário deve clicar no botão "Atender" para aceitar a chamada

## Causa Raiz

### Conflito de PendingIntents

No código original, dois `PendingIntent` diferentes estavam usando o **mesmo requestCode**:

```kotlin
// PendingIntent para fullscreen (linha 499)
val pendingIntent = PendingIntent.getActivity(
    this,
    chamadaId,  // ← MESMO requestCode
    intent,     // SEM auto_answer
    ...
)

// PendingIntent para botão "Atender" (linha 517)
val answerPendingIntent = PendingIntent.getActivity(
    this,
    chamadaId,  // ← MESMO requestCode
    answerIntent,  // COM auto_answer = true
    ...
)
```

### O que Acontecia

Quando você usa `PendingIntent.getActivity()` com o mesmo `requestCode` e a flag `FLAG_UPDATE_CURRENT`, o Android **REUTILIZA** o `PendingIntent` existente e atualiza apenas os extras.

**Sequência do problema:**
```
1. Cria pendingIntent (fullscreen) com requestCode=568
   Extras: { chamadaId=568, isIncoming=true }
   (SEM auto_answer)

2. Cria answerPendingIntent com requestCode=568
   Android vê: "Já existe PendingIntent com requestCode=568!"
   Atualiza extras: { chamadaId=568, isIncoming=true, auto_answer=true }

3. setFullScreenIntent(pendingIntent, true)
   Mas pendingIntent AGORA TEM auto_answer=true! ❌

4. Tela abre com auto_answer=true
   ChamadaActivity atende automaticamente ❌
```

## Solução Implementada

### Usar RequestCodes Diferentes

Cada `PendingIntent` precisa ter um **requestCode único** para não conflitar:

```kotlin
// PendingIntent para fullscreen - SEM auto_answer
val pendingIntent = PendingIntent.getActivity(
    this,
    chamadaId + 200000,  // ← RequestCode ÚNICO
    intent,              // SEM auto_answer
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

// PendingIntent para botão "Atender" - COM auto_answer
val answerPendingIntent = PendingIntent.getActivity(
    this,
    chamadaId + 300000,  // ← RequestCode DIFERENTE
    answerIntent,        // COM auto_answer = true
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

// PendingIntent para botão "Recusar"
val declinePendingIntent = PendingIntent.getBroadcast(
    this,
    chamadaId + 100000,  // ← RequestCode DIFERENTE (já estava assim)
    declineIntent,
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)
```

### Estratégia de RequestCodes

Para garantir que os requestCodes nunca colidam:

| PendingIntent | RequestCode | Uso |
|---------------|-------------|-----|
| **Fullscreen** | `chamadaId + 200000` | Abre tela quando dispositivo bloqueado (SEM auto_answer) |
| **Botão Atender** | `chamadaId + 300000` | Quando usuário clica no botão Atender (COM auto_answer) |
| **Botão Recusar** | `chamadaId + 100000` | Quando usuário clica no botão Recusar |

Mesmo se `chamadaId` for 999999, os requestCodes serão:
- 999999 + 200000 = 1199999 (fullscreen)
- 999999 + 300000 = 1299999 (atender)
- 999999 + 100000 = 1099999 (recusar)

Todos diferentes, sem colisão! ✅

## Comportamento Após a Correção

### Dispositivo Desbloqueado
```
Chamada recebida
    ↓
Notificação com botões aparece
    ↓
Usuário clica "Atender"
    ↓
answerPendingIntent dispara
    ↓
ChamadaActivity abre com auto_answer=true ✅
    ↓
Chamada é atendida automaticamente ✅
```

### Dispositivo Bloqueado
```
Chamada recebida
    ↓
Fullscreen intent dispara
    ↓
pendingIntent (SEM auto_answer) dispara
    ↓
ChamadaActivity abre SEM auto_answer ✅
    ↓
Tela mostra botões "Atender" e "Recusar"
    ↓
Usuário DEVE clicar no botão para atender ✅
```

## Arquivos Modificados

### SocketService.kt - mostrarNotificacaoComFullscreen()
**Linhas 487-522**: Modificado requestCodes dos PendingIntents

**ANTES**:
```kotlin
val pendingIntent = PendingIntent.getActivity(this, chamadaId, intent, ...)
val answerPendingIntent = PendingIntent.getActivity(this, chamadaId, answerIntent, ...)
```

**DEPOIS**:
```kotlin
val pendingIntent = PendingIntent.getActivity(this, chamadaId + 200000, intent, ...)
val answerPendingIntent = PendingIntent.getActivity(this, chamadaId + 300000, answerIntent, ...)
```

## Teste Recomendado

### Teste 1: Dispositivo Desbloqueado
1. Mantenha o dispositivo desbloqueado
2. Receba uma chamada
3. ✅ Deve aparecer notificação com botões
4. Clique no botão "Atender"
5. ✅ Deve atender automaticamente (este é o comportamento correto aqui)

### Teste 2: Dispositivo Bloqueado (cenário que estava errado)
1. **Bloqueie o dispositivo** (pressione botão power)
2. Receba uma chamada
3. ✅ Tela de chamada deve abrir automaticamente
4. ✅ **IMPORTANTE**: Chamada NÃO deve ser atendida automaticamente
5. ✅ Deve mostrar tela com botões "Atender" e "Recusar"
6. Clique em "Atender"
7. ✅ Agora sim, chamada deve ser atendida

### Teste 3: Clicar na Notificação (não nos botões)
1. Dispositivo desbloqueado
2. Receba uma chamada
3. Notificação aparece
4. **Clique na área da notificação** (não nos botões)
5. ✅ Deve abrir a tela MAS NÃO atender automaticamente
6. Usuário deve clicar no botão "Atender"

## Conceito Técnico: PendingIntent Caching

### O que é PendingIntent?

`PendingIntent` é como um "token" que você dá para outro componente (sistema Android, notificações, etc.) executar uma ação no futuro em nome do seu app.

### Como o Android Gerencia PendingIntents

O Android mantém um **cache global** de PendingIntents identificados por:
- `requestCode` (int)
- `Intent` action/component
- Flags

Quando você cria um PendingIntent com `FLAG_UPDATE_CURRENT`:
```kotlin
PendingIntent.getActivity(context, requestCode, intent, FLAG_UPDATE_CURRENT)
```

O Android verifica:
1. Já existe PendingIntent com esse `requestCode`?
2. SIM → **Reutiliza e atualiza os extras do Intent**
3. NÃO → Cria novo

### Por que RequestCodes Diferentes?

Se dois PendingIntents têm:
- **Mesmo requestCode** → Android REUTILIZA
- **RequestCodes diferentes** → Android cria SEPARADOS

No nosso caso:
- Fullscreen precisa de SEM auto_answer
- Botão Atender precisa de COM auto_answer
- **Solução**: RequestCodes diferentes garantem PendingIntents separados

## Data da Correção
2025-12-26
