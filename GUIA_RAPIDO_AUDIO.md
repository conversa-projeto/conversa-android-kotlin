# 🎤 Guia Rápido: Envio de Áudio

## Como Usar

### 1️⃣ Iniciar Gravação
- Clique no **botão de microfone** 🎤 (ao lado do campo de texto)
- Conceda permissão se solicitado
- A UI mudará para o modo de gravação

### 2️⃣ Durante a Gravação
```
┌─────────────────────────────────┐
│ ❌  Gravando áudio...       ✉️  │
│     00:42                        │
└─────────────────────────────────┘
```
- **Timer** mostra a duração em tempo real
- **Máximo:** 5 minutos
- **Mínimo:** 1 segundo

### 3️⃣ Finalizar
**Opção A - Enviar:**
- Clique no botão verde ✉️
- Áudio será enviado automaticamente

**Opção B - Cancelar:**
- Clique no botão vermelho ❌
- Áudio será descartado

---

## Fluxo Visual

```
┌───────────────────────────────────────┐
│  1. ESTADO NORMAL                     │
│  ┌──────────────────────────────┐    │
│  │ [📎] [Campo de Texto...] [🎤] │    │
│  └──────────────────────────────┘    │
└───────────────────────────────────────┘
              │
              │ (Clica em 🎤)
              ↓
┌───────────────────────────────────────┐
│  2. GRAVANDO                          │
│  ┌──────────────────────────────┐    │
│  │ [❌] Gravando áudio...  [✉️] │    │
│  │      00:15                     │    │
│  └──────────────────────────────┘    │
└───────────────────────────────────────┘
              │
         ┌────┴────┐
         │         │
    (❌)  │         │  (✉️)
         │         │
         ↓         ↓
┌────────────┐  ┌────────────┐
│  CANCELADO │  │  ENVIADO   │
│  Volta ao  │  │  Mensagem  │
│  normal    │  │  aparece   │
└────────────┘  └────────────┘
```

---

## Validações Automáticas

### ✅ Duração Mínima: 1 segundo
Se tentar enviar antes de 1 segundo:
```
❌ "Áudio muito curto. Grave por pelo menos 1 segundo."
```

### ✅ Duração Máxima: 5 minutos
Ao atingir 5 minutos:
```
⚠️ "Limite de 5 minutos atingido"
```
→ Áudio é enviado automaticamente

### ✅ Permissão de Microfone
Primeira vez:
```
📱 "Conversa precisa de acesso ao microfone"
    [Permitir] [Negar]
```

Se negar:
```
❌ "Permissão de microfone negada. Não é possível gravar áudio."
```

---

## Formato do Áudio

**Especificações Técnicas:**
- 📦 Formato: M4A (MPEG-4 Audio)
- 🎵 Codec: AAC
- 📊 Taxa: 44.1 kHz
- 💾 Bitrate: 128 kbps
- 🔊 Canais: Mono

---

## Dicas de Uso

### ✨ Boas Práticas
- 🎤 Fale próximo ao microfone
- 🔇 Grave em ambiente silencioso
- ⏱️ Seja conciso (ideal: 5-30 segundos)
- 🔊 Verifique o volume do dispositivo

### ⚠️ Evite
- ❌ Gravar em locais com muito barulho
- ❌ Enviar áudios muito longos
- ❌ Falar longe do microfone
- ❌ Cancelar sem querer

---

## Solução de Problemas

### 🔴 Problema: Botão de microfone não aparece
**Solução:** 
- Limpe o campo de texto
- O botão 🎤 só aparece quando o campo está vazio

### 🔴 Problema: Permissão negada
**Solução:**
1. Vá em Configurações → Apps → Conversa
2. Permissões → Microfone
3. Ative "Permitir"

### 🔴 Problema: Áudio não envia
**Solução:**
- Verifique sua conexão com internet
- Tente gravar novamente
- Reinicie o app se necessário

### 🔴 Problema: Qualidade ruim
**Solução:**
- Verifique se está usando fone com microfone
- Desconecte Bluetooth se não for usar
- Grave em ambiente mais silencioso

---

## Interface Detalhada

### Botões Principais

#### 🎤 Botão Microfone (Normal)
```
Função: Iniciar gravação
Localização: Canto inferior direito
Visível quando: Campo de texto vazio
Cor: Primária (azul)
```

#### ❌ Botão Cancelar (Gravando)
```
Função: Cancelar e descartar gravação
Localização: Canto inferior esquerdo
Visível quando: Gravando
Cor: Vermelho (#F44336)
```

#### ✉️ Botão Enviar (Gravando)
```
Função: Parar gravação e enviar
Localização: Canto inferior direito
Visível quando: Gravando
Cor: Primária (azul/verde)
```

### Indicadores Visuais

#### Timer de Duração
```
Formato: MM:SS
Atualização: A cada 100ms
Exemplo: 00:00 → 00:15 → 01:30
```

#### Texto de Status
```
"Gravando áudio..."
Cor: Vermelho
Efeito: Pulsante (opcional)
```

---

## Checklist para o Usuário

Antes de enviar um áudio, verifique:
- [ ] Ambiente está silencioso
- [ ] Microfone está funcionando
- [ ] Mensagem é clara e objetiva
- [ ] Duração é adequada (não muito longa)
- [ ] Conexão está estável

---

## Atalhos e Dicas

### 💡 Dica 1: Alternativa ao Áudio
Se preferir, pode:
- Digitar a mensagem (mais rápido para o destinatário ler)
- Enviar imagem com texto

### 💡 Dica 2: Combinação
Pode enviar:
- Texto + Áudio
- Imagem + Áudio
- Apenas Áudio

### 💡 Dica 3: Privacidade
- Áudios ficam salvos no servidor
- Podem ser reproduzidos novamente
- Não há auto-destruição

---

## Exemplos de Uso

### ✅ Bom Uso
```
🎤 "Oi! Tudo bem? Vou chegar às 15h, ok?"
   Duração: 5 segundos
   ✓ Claro e objetivo
```

### ❌ Uso Inadequado
```
🎤 "Então... é... tipo assim... sabe... 
    eu tava pensando... hmm... que..."
   Duração: 2 minutos
   ✗ Muito longo e confuso
```

---

**Pronto para usar! 🚀**

Qualquer dúvida, consulte a documentação completa em:
`IMPLEMENTACAO_ENVIO_AUDIO_FINALIZADO.md`
