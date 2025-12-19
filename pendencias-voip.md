# Pendências - Sistema de Chamadas VoIP

## 1. AndroidManifest.xml

### 1.1 Permissões faltando
- `FOREGROUND_SERVICE_PHONE_CALL` - obrigatória para foreground service de chamada
- `FOREGROUND_SERVICE_MICROPHONE` - obrigatória para captura de áudio em foreground
- `BLUETOOTH_CONNECT` - necessária para roteamento de áudio via Bluetooth

### 1.2 SocketService - foregroundServiceType incorreto
- **Atual:** `dataSync`
- **Correto:** `dataSync|phoneCall` ou apenas `phoneCall`
- Sem isso, Android pode matar o service durante chamadas

### 1.3 ChamadaActivity - atributos faltando
- `android:showOnLockScreen="true"` (complementar ao showWhenLocked)
- `android:inheritShowWhenLocked="true"` (para fragments/dialogs)

---

## 2. SocketService - Binder/ServiceConnection

### 2.1 Problema atual
- `onBind()` retorna `null`
- App não consegue se vincular ao service
- Comunicação apenas via LocalBroadcast (ineficiente)

### 2.2 O que implementar
- Criar classe `LocalBinder` que expõe o service
- Implementar `onBind()` retornando o binder
- Adicionar interface `CallListener` para callbacks diretos
- Controlar estado `isAppBound` para saber se app está vinculado
- Quando vinculado: enviar eventos via callback direto
- Quando não vinculado: enviar via Broadcast (atual)

### 2.3 Onde vincular
- `MainActivity.onCreate()` → `bindService()`
- `MainActivity.onDestroy()` → `unbindService()`
- Registrar callback para receber chamadas quando app aberto

---

## 3. Tela Bloqueada - Camada de Pré-Notificação

### 3.1 Problema atual
- Notificação fullscreen depende do `ChamadaNotificationManager`
- Não há garantia de exibição imediata no Android 12+
- Possível ANR se processamento demorar

### 3.2 O que implementar
- Criar `ChamadaPreNotificationManager` (ou adaptar o existente)
- Mostrar notificação IMEDIATAMENTE ao receber chamada
- Iniciar ringtone/vibração de forma assíncrona
- Buscar dados do chamador em background (não bloquear)

### 3.3 Sequência correta
1. Socket recebe chamada
2. Mostra notificação básica imediatamente (nome pode estar vazio)
3. Inicia ringtone/vibração
4. Busca dados do chamador via API
5. Atualiza notificação com nome correto

---

## 4. ChamadaNotificationManager - Melhorias

### 4.1 Problema atual
- `buscarDadosChamada()` é chamado ANTES de mostrar notificação
- Se API demorar, notificação demora a aparecer
- `setOngoing(false)` permite dispensar a notificação (incorreto)

### 4.2 Correções necessárias
- Mostrar notificação primeiro, buscar dados depois
- `setOngoing(true)` para notificação de chamada
- Remover `setTimeoutAfter(60000)` ou aumentar para 120000ms
- Adicionar `setVisibility(NotificationCompat.VISIBILITY_PUBLIC)` para tela bloqueada

---

## 5. RingtoneManager Dedicado

### 5.1 Problema atual
- Ringtone gerenciado apenas pelo canal de notificação
- Sem controle programático para parar/pausar
- Vibração definida no builder da notificação (não controlável)

### 5.2 O que implementar
- Criar objeto `RingtoneManager` singleton
- Métodos: `iniciar()`, `parar()`, `pausar()`
- Usar `android.media.Ringtone` para tocar som
- Usar `Vibrator` para vibração controlada
- Respeitar modo silencioso/DND
- Parar automaticamente ao aceitar/recusar

---

## 6. ChamadaBroadcastReceiver - Problema de registro

### 6.1 Problema atual
- Registrado no Manifest com intent-filter
- Usa `LocalBroadcastManager` para enviar (incompatível)
- `LocalBroadcast` não funciona com receivers declarados no Manifest

### 6.2 Correções necessárias
- **Opção A:** Registrar receiver dinamicamente no SocketService
- **Opção B:** Usar broadcast global (não LocalBroadcast) - menos seguro
- **Opção C:** Chamar `ChamadaNotificationManager` diretamente do SocketService

---

## 7. Fluxo de Chamada - Inconsistências

### 7.1 App aberto (vinculado)
- **Esperado:** SocketService → Callback direto → UI de chamada
- **Atual:** SocketService → LocalBroadcast → ? (receiver não recebe)

### 7.2 App fechado (não vinculado)
- **Esperado:** SocketService → Broadcast global → Receiver → Notificação
- **Atual:** SocketService → LocalBroadcast → Receiver não recebe

### 7.3 Tela bloqueada
- **Esperado:** Notificação fullscreen imediata → ChamadaActivity
- **Atual:** Depende de buscar dados antes (pode demorar)

---

## 8. Ordem de Implementação Sugerida

### Fase 1 - Crítico (funcionalidade básica)
1. Corrigir Manifest (permissões + foregroundServiceType)
2. Corrigir fluxo de Broadcast (LocalBroadcast vs Global)
3. Inverter ordem: notificação primeiro, dados depois

### Fase 2 - ServiceConnection
4. Implementar Binder no SocketService
5. Criar interface CallListener
6. Vincular MainActivity ao service
7. Lógica de bound/unbound para decidir callback vs broadcast

### Fase 3 - Tela Bloqueada
8. Criar RingtoneManager dedicado
9. Ajustar notificação (ongoing, visibility, etc)
10. Testar em diferentes estados (tela ligada/desligada/bloqueada)

### Fase 4 - Polimento
11. Tratamento de erros robusto
12. Logs para debug
13. Testes de cenários edge-case

---

## 9. Checklist de Testes

- [ ] App aberto, tela ligada → chamada chega
- [ ] App em background, tela ligada → chamada chega
- [ ] App fechado (processo vivo), tela ligada → chamada chega
- [ ] App fechado, tela desligada → chamada chega
- [ ] App fechado, tela bloqueada → chamada chega com fullscreen
- [ ] Atender pela notificação → abre ChamadaActivity
- [ ] Recusar pela notificação → notificação some, chamada recusada
- [ ] Perda de conexão socket → reconecta automaticamente
- [ ] Chamada durante modo silencioso → respeita configuração
