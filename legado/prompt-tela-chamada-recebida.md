# Prompt: Tela de Recebimento de Chamadas em Kotlin (Jetpack Compose)

Crie uma tela de recebimento de chamadas em **Kotlin com Jetpack Compose** com as seguintes especificações:

---

## Estrutura da Tela

Layout vertical centralizado com fundo gradiente escuro (slate-900 → slate-800 → slate-900).

### 1. Seção Superior - Informações do Chamador

- **Avatar**: Círculo de 112dp com gradiente violeta→fúcsia, contendo um emoji ou imagem
  - Indicador de status online: pequeno círculo verde (24dp) posicionado no canto inferior direito, com borda e animação de pulse
  - Animação de flutuação suave (sobe e desce 5dp) em loop infinito
- **Nome**: Texto branco, 24sp, semi-bold, abaixo do avatar
- **Descrição**: Texto cinza claro, 18sp, abaixo do nome (ex: "Chamada em Grupo")
- **Indicador de chamada**: Pequeno círculo verde pulsante + texto "Chamando..."

### 2. Seção Inferior - Botões de Ação

Dois botões circulares lado a lado com espaçamento de 80dp entre eles:

#### Botão Recusar (Esquerda)
- Círculo de 80dp com gradiente vermelho (red-500 → red-600)
- Ícone de telefone branco rotacionado 135°
- Sombra vermelha
- Label "Recusar" abaixo

#### Botão Atender (Direita)
- Círculo de 80dp com gradiente verde (green-500 → green-600)
- Ícone de telefone branco
- Sombra verde
- Label "Atender" abaixo

---

## Efeito de Ondas Animadas (IMPORTANTE)

Cada botão deve ter **4 ondas concêntricas** animadas nas bordas:

- As ondas são círculos com apenas borda (sem preenchimento)
- Animação: escala de 1.0 → 2.5 enquanto opacidade vai de 0.6 → 0.0
- Duração: 2 segundos, loop infinito
- Cada onda tem um delay diferente: 0s, 0.5s, 1.0s, 1.5s
- Cor das ondas: mesma cor do botão (vermelho ou verde) com transparência

---

## Interação de Deslizar (Gesture)

Implementar **drag gesture** horizontal em ambos os botões:

### Comportamento:
1. Ao arrastar o botão (qualquer direção, esquerda ou direita), ele deve **crescer mantendo o centro fixo**
2. O crescimento é proporcional à distância arrastada
3. Threshold para ação: **150dp** de distância
4. Ao atingir o threshold:
   - Botão Recusar → executa ação de recusar (qualquer direção)
   - Botão Atender → executa ação de atender (qualquer direção)
5. O botão oposto deve ficar com opacidade reduzida (0.3) durante o arraste
6. Ao soltar antes do threshold, o botão volta ao tamanho original com animação suave

### Escala do botão durante arraste:
```
scale = 1.0 + (distância / threshold) * 0.8
```
Ou seja, o botão pode crescer até 1.8x seu tamanho original.

---

## Telas de Estado

### Estado: Chamada Aceita
- Ícone 📞 grande verde
- Texto "Conectando..."
- Botão "Reiniciar Demo"

### Estado: Chamada Recusada
- Ícone 📵 grande vermelho
- Texto "Chamada Recusada"
- Botão "Reiniciar Demo"

---

## Requisitos Técnicos

- Usar `Modifier.pointerInput` para gestos de drag
- Usar `Animatable` ou `InfiniteTransition` para as ondas
- Usar `animateFloatAsState` para transições suaves de escala/opacidade
- Garantir que funcione bem em diferentes tamanhos de tela
- Cores e dimensões podem usar Material Theme ou valores hardcoded

---

## Cores de Referência

- Fundo: #0f172a → #1e293b → #0f172a
- Violeta avatar: #8b5cf6 → #d946ef
- Verde: #22c55e → #16a34a
- Vermelho: #ef4444 → #dc2626
- Texto principal: #ffffff
- Texto secundário: #94a3b8
