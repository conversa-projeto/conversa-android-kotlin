# Soluções para Posicionamento de Mensagens

## SOLUÇÃO RECOMENDADA: LinearLayout com Gravity

Esta é a forma mais simples e confiável.

### Mensagem ENVIADA (Direita)

```xml
<LinearLayout 
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="end"
    android:padding="4dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="48dp"
        android:maxWidth="280dp"
        android:background="@drawable/bg_mensagem_enviada"
        ...>
```

**Key:** `android:gravity="end"` empurra para direita

### Mensagem RECEBIDA (Esquerda)

```xml
<LinearLayout 
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="start"
    android:padding="4dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginEnd="48dp"
        android:maxWidth="280dp"
        android:background="@drawable/bg_mensagem_recebida"
        ...>
```

**Key:** `android:gravity="start"` empurra para esquerda

## Alternativa: RelativeLayout

### Enviada:
```xml
<RelativeLayout>
    <LinearLayout
        android:layout_alignParentEnd="true"
        ...>
```

### Recebida:
```xml
<RelativeLayout>
    <LinearLayout
        android:layout_alignParentStart="true"
        ...>
```

## Por que LinearLayout é melhor?

1. Mais simples
2. Mais confiável
3. Melhor performance
4. Funciona 100% das vezes
