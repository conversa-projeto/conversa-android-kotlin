# Correção: Detecção de App em Foreground

## Problema Identificado

**Sintoma**: Apenas a primeira chamada funcionava. Chamadas subsequentes (2ª, 3ª, etc.) não exibiam notificação quando o app estava minimizado.

**Causa Raiz**: A lógica de decisão entre "callback" vs "notificação" verificava apenas se o app estava **vinculado** (`isAppBound`), mas não se estava **visível** (foreground).

### Por que isso acontecia?

```
Cenário:
1. Usuário abre o app → MainActivity vincula ao SocketService
2. Chega chamada #1 → App está em FOREGROUND → Callback funciona ✅
3. Usuário minimiza o app (vai pro WhatsApp)
4. MainActivity continua vinculada (isAppBound = true)
5. Chega chamada #2 → Sistema envia callback
6. MAS MainActivity está em onPause() → Não consegue mostrar notificação ❌
7. Chamada perdida
```

## Solução Implementada

Adicionada verificação de **foreground** usando `AppLifecycleManager.isAppInForeground`:

### Arquivo: SocketService.kt (linha 401)

**ANTES**:
```kotlin
if (isAppBound && callListener != null) {
    // Envia callback
    callListener?.onChamadaRecebida(...)
} else {
    // Mostra notificação
    mostrarNotificacaoChamada(...)
}
```

**DEPOIS**:
```kotlin
if (isAppBound && callListener != null && AppLifecycleManager.isAppInForeground) {
    // App VISÍVEL em foreground: callback
    callListener?.onChamadaRecebida(...)
} else {
    // App minimizado/background/fechado: notificação
    mostrarNotificacaoChamada(...)
}
```

### Como Funciona o AppLifecycleManager

```kotlin
object AppLifecycleManager : Application.ActivityLifecycleCallbacks {
    var isAppInForeground = false
        private set

    private var activityReferences = 0

    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1) {
            isAppInForeground = true  // ✅ Primeira activity visível
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (--activityReferences == 0) {
            isAppInForeground = false  // ❌ Última activity sumiu
        }
    }
}
```

**Analogia**: Funciona como um porteiro contando quantas pessoas tem na sala:
- Primeira pessoa entra → acende a luz
- Última pessoa sai → apaga a luz

## Comportamento Agora

| Estado do App | isAppBound | isAppInForeground | Resultado |
|---------------|------------|-------------------|-----------|
| **Aberto e visível** | ✅ true | ✅ true | Callback → MainActivity mostra notificação sem fullscreen |
| **Minimizado** | ✅ true | ❌ false | Notificação com fullscreen (abre automático) |
| **Fechado** | ❌ false | ❌ false | Notificação com fullscreen (abre automático) |

## Arquivos Modificados

### 1. SocketService.kt
- **Linha 20**: Adicionado import `com.conversa.conversa.utils.AppLifecycleManager`
- **Linha 398**: Adicionado log do estado `isAppInForeground`
- **Linha 401**: Adicionada verificação `&& AppLifecycleManager.isAppInForeground`
- **Linhas 402-411**: Atualizados comentários e logs para refletir nova lógica

## Como Testar

1. **Teste 1 - App em Foreground**:
   - Abra o app e deixe na tela principal
   - Faça uma chamada
   - ✅ Deve aparecer notificação SEM abrir a tela automaticamente
   - ✅ Botões "Atender" e "Recusar" devem funcionar

2. **Teste 2 - App Minimizado** (cenário que estava quebrado):
   - Abra o app
   - Minimize (pressione HOME ou vá pra outro app)
   - Faça uma chamada
   - ✅ Deve abrir a tela de chamada automaticamente (fullscreen intent)

3. **Teste 3 - App Fechado**:
   - Force stop no app
   - Faça uma chamada
   - ✅ Deve abrir a tela de chamada automaticamente

4. **Teste 4 - Múltiplas Chamadas Sequenciais**:
   - Abra o app
   - Receba chamada #1 → minimize → receba chamada #2 → receba chamada #3
   - ✅ Todas devem funcionar

## Logs para Debugging

Com a correção, os logs agora mostram:

```
🔔 Estado: isAppBound=true, callListener=NOT NULL, isAppInForeground=true
✅ FLUXO: App VISÍVEL - Enviando via CALLBACK DIRETO para MainActivity
```

ou

```
🔔 Estado: isAppBound=true, callListener=NOT NULL, isAppInForeground=false
📱 FLUXO: App minimizado/background/fechado - Enviando via NOTIFICAÇÃO
```

## Data da Correção
2025-12-26
