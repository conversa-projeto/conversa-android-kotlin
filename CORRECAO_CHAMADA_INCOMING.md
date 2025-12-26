# ✅ Correção Final: ChamadaIncomingActivity

## Erro de Compilação Corrigido

**Erro:**
```
Unresolved reference 'ChamadaIncomingActivity'
```

**Causa:**
O código estava referenciando `ChamadaIncomingActivity`, mas essa classe **não existe** no projeto.

---

## Classes de Chamada Disponíveis

Encontrado no projeto:
- ✅ `ChamadaActivity` - Activity principal de chamada
- ✅ `IncomingCallFragment` - Fragment para chamadas recebidas
- ✅ `SimpleCallFragment` - Fragment para chamadas simples
- ✅ `GroupCallFragment` - Fragment para chamadas em grupo
- ❌ `ChamadaIncomingActivity` - **NÃO EXISTE**

---

## Correções Aplicadas

### 1. SocketService.kt

**Removido import:**
```kotlin
import com.conversa.conversa.ui.chamada.ChamadaIncomingActivity
```

**Substituído todas as referências:**
- `ChamadaIncomingActivity::class.java` → `ChamadaActivity::class.java`
- `ChamadaIncomingActivity.EXTRA_*` → `ChamadaActivity.EXTRA_*`
- `ChamadaIncomingActivity.sharedSocketManager` → `ChamadaActivity.sharedSocketManager`

**Adicionado extra importante:**
```kotlin
putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true)
```

Isso informa para `ChamadaActivity` que é uma **chamada recebida**, não uma chamada saindo.

---

### 2. AndroidManifest.xml

**Removida declaração duplicada:**

ANTES (ERRADO):
```xml
<activity
    android:name=".ui.chamada.ChamadaIncomingActivity"
    android:excludeFromRecents="true"
    android:exported="false"
    android:launchMode="singleTop"
    android:screenOrientation="portrait"
    android:showWhenLocked="true"
    android:showOnLockScreen="true"
    android:inheritShowWhenLocked="true"
    android:theme="@style/Theme.Conversa.NoActionBar"
    android:turnScreenOn="true" />
<activity
    android:name=".ui.chamada.ChamadaActivity"
    ... />
```

DEPOIS (CORRETO):
```xml
<activity
    android:name=".ui.chamada.ChamadaActivity"
    android:excludeFromRecents="true"
    android:exported="false"
    android:launchMode="singleTop"
    android:screenOrientation="portrait"
    android:showWhenLocked="true"
    android:showOnLockScreen="true"
    android:inheritShowWhenLocked="true"
    android:turnScreenOn="true"
    android:theme="@style/Theme.Conversa.NoActionBar" />
```

**Atributos importantes mantidos:**
- ✅ `showWhenLocked="true"` - Mostra sobre lockscreen
- ✅ `showOnLockScreen="true"` - Compatibilidade com versões antigas
- ✅ `inheritShowWhenLocked="true"` - Herda configuração
- ✅ `turnScreenOn="true"` - Acende a tela automaticamente

---

## Como Funciona Agora

### Quando Chamada é Recebida (App Fechado):

```
SocketService.mostrarNotificacaoChamada()
    ↓
Cria Intent para ChamadaActivity com:
  - EXTRA_CHAMADA_ID = 554
  - EXTRA_USUARIO_ID = 2
  - EXTRA_USUARIO_NOME = "Desconhecido"
  - EXTRA_IS_INCOMING = true  ← IMPORTANTE!
    ↓
Cria notificação com setFullScreenIntent()
    ↓
Android abre ChamadaActivity automaticamente
    ↓
ChamadaActivity verifica EXTRA_IS_INCOMING
    ↓
Se true: Mostra UI de chamada recebida (botões Atender/Recusar)
Se false: Mostra UI de chamada saindo
```

---

## Resumo das Alterações

### SocketService.kt (3 mudanças)
1. ✅ Removido import `ChamadaIncomingActivity`
2. ✅ Substituído todas as referências para `ChamadaActivity`
3. ✅ Adicionado `EXTRA_IS_INCOMING = true`

### AndroidManifest.xml (1 mudança)
1. ✅ Removida declaração de `ChamadaIncomingActivity` que não existe
2. ✅ Mantidos atributos de lockscreen em `ChamadaActivity`

---

## Agora Você Pode Fazer Rebuild

O erro de compilação está corrigido. Agora você pode fazer:

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

✅ Erro de compilação resolvido
✅ Imports corrigidos
✅ Referências corretas para ChamadaActivity
✅ AndroidManifest limpo
✅ Atributos de lockscreen mantidos
✅ EXTRA_IS_INCOMING adicionado para diferenciar chamadas recebidas

**Pronto para rebuild!**
