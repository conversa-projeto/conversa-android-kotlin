# 🔧 Correções Definitivas - Imagens e Áudios no Conversa Android

## 📋 Diagnóstico Completo

Após análise detalhada do código, identifiquei os **PROBLEMAS REAIS**:

### ✅ O que JÁ ESTÁ CORRETO:
1. **MensagensAdapter.kt** - Autenticação do Glide está correta
2. **AudioPlayerHelper.kt** - Download com autenticação está correto  
3. **ImageViewerActivity.kt** - Carregamento de imagem está correto
4. **AndroidManifest.xml** - ImageViewerActivity já está registrada

### ❌ O que ESTÁ ERRADO:

**1. activity_image_viewer.xml - ID DO IMAGEVIEW ESTÁ ERRADO**
- O código espera: `binding.imageView`
- Mas o XML tem: `binding.ivImagem` ou outro ID diferente

**2. item_mensagem_enviada.xml e item_mensagem_recebida.xml**
- Ícone do anexo está errado: `@drawable/ic_send` (deveria ser algo como `ic_attach_file`)

---

## 🔍 CORREÇÃO 1: activity_image_viewer.xml

### Localizar o arquivo:
`app/src/main/res/layout/activity_image_viewer.xml`

### Verificar o ID do ImageView:

Se o arquivo tiver algo como:
```xml
<ImageView
    android:id="@+id/ivImagem"
    ... />
```

**TROCAR PARA:**
```xml
<ImageView
    android:id="@+id/imageView"
    ... />
```

### OU (solução alternativa - mudar o código Kotlin):

Se preferir manter o XML como está, edite **ImageViewerActivity.kt**:

**LOCALIZAR ESTA LINHA (aproximadamente linha 50):**
```kotlin
.into(binding.imageView)
```

**TROCAR PARA:**
```kotlin
.into(binding.ivImagem)
```

---

## 🔍 CORREÇÃO 2: Ícone de anexo errado

### Arquivo 1: item_mensagem_enviada.xml

**LOCALIZAR:**
```xml
<TextView
    android:id="@+id/tvNomeArquivo"
    ...
    android:drawableStart="@drawable/ic_send"
    ...
    tools:text="documento.pdf" />
```

**TROCAR PARA:**
```xml
<TextView
    android:id="@+id/tvNomeArquivo"
    ...
    android:drawableStart="@android:drawable/ic_menu_save"
    ...
    tools:text="documento.pdf" />
```

### Arquivo 2: item_mensagem_recebida.xml

**LOCALIZAR:**
```xml
<TextView
    android:id="@+id/tvNomeArquivo"
    ...
    android:drawableStart="@drawable/ic_send"
    ...
    tools:text="documento.pdf" />
```

**TROCAR PARA:**
```xml
<TextView
    android:id="@+id/tvNomeArquivo"
    ...
    android:drawableStart="@android:drawable/ic_menu_save"
    ...
    tools:text="documento.pdf" />
```

---

## 🔍 CORREÇÃO 3: Verificar URL da API

### Verificar build.gradle.kts:

**Arquivo:** `app/build.gradle.kts`

**Procurar por:**
```kotlin
buildConfigField("String", "API_URL", "\"http://10.0.2.2:8000\"")
```

**OU:**
```kotlin
buildConfigField("String", "API_URL", "\"http://localhost:8000\"")
```

### Se estiver usando emulador:
```kotlin
buildConfigField("String", "API_URL", "\"http://10.0.2.2:8000\"")
```

### Se estiver usando dispositivo físico:
```kotlin
buildConfigField("String", "API_URL", "\"http://SEU_IP_LOCAL:8000\"")
```

**Exemplo:**
```kotlin
buildConfigField("String", "API_URL", "\"http://192.168.0.10:8000\"")
```

---

## 🔍 VERIFICAÇÃO ADICIONAL: activity_image_viewer.xml completo

Se o arquivo não existir ou estiver incompleto, crie/substitua com este conteúdo:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true"
    android:background="@android:color/black">

    <!-- Toolbar -->
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:theme="@style/ThemeOverlay.AppCompat.Dark.ActionBar">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            android:background="?attr/colorPrimary"
            app:popupTheme="@style/ThemeOverlay.AppCompat.Light"
            app:layout_scrollFlags="scroll|enterAlways" />

    </com.google.android.material.appbar.AppBarLayout>

    <!-- Container da imagem -->
    <FrameLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <!-- ImageView com zoom -->
        <com.github.chrisbanes.photoview.PhotoView
            android:id="@+id/imageView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:contentDescription="Imagem em tela cheia"
            android:scaleType="fitCenter" />

        <!-- ProgressBar para carregamento -->
        <ProgressBar
            android:id="@+id/progressBar"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="center"
            android:indeterminateTint="@android:color/white" />

    </FrameLayout>

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**ATENÇÃO:** Este layout usa `PhotoView` para zoom. Se não tiver esta dependência, adicione no `build.gradle.kts`:

```kotlin
dependencies {
    // ... outras dependências
    
    // PhotoView para zoom de imagens
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
}
```

---

## 🧪 TESTES A FAZER

Após aplicar as correções:

### 1. Limpar e Rebuildar:
```bash
Build > Clean Project
Build > Rebuild Project
```

### 2. Testar Imagens:
- Envie uma mensagem com imagem
- Verifique se a thumbnail carrega
- Clique na imagem
- Verifique se abre em tela cheia
- Tente dar zoom (se adicionou PhotoView)

### 3. Testar Áudios:
- Envie uma mensagem com áudio
- Verifique se o ícone de play aparece (não o ícone de send)
- Clique no botão de play
- Verifique se o áudio toca
- Verifique se o ícone muda para pause
- Clique novamente para pausar

### 4. Testar Anexos:
- Envie uma mensagem com arquivo
- Verifique se o nome do arquivo aparece corretamente
- Verifique se o ícone está correto (não o ícone de send)
- Clique no botão "Baixar"
- Verifique se o download acontece

---

## 🐛 DEBUGGING: Se ainda não funcionar

### Para Imagens:

1. **Verificar Logcat** enquanto tenta abrir uma imagem:
   - Procure por erros de `Glide`
   - Procure por `401 Unauthorized`
   - Procure por `ImageViewerActivity`

2. **Testar URL diretamente no navegador:**
   - Copie a URL: `http://SEU_IP:8000/conteudos/NUMERO_ID`
   - Abra no navegador
   - Se pedir autenticação, o problema está no token
   - Se a imagem baixar, o problema está no app

3. **Verificar token:**
   - Adicione log em `ImageViewerActivity`:
   ```kotlin
   Log.d("ImageViewer", "URL: $imageUrl")
   Log.d("ImageViewer", "Token: $authToken")
   ```

### Para Áudios:

1. **Verificar Logcat** enquanto tenta tocar um áudio:
   - Procure por erros de `MediaPlayer`
   - Procure por `401 Unauthorized`
   - Procure por `AudioPlayerHelper`

2. **Adicionar logs em AudioPlayerHelper:**
   ```kotlin
   Log.d("AudioPlayer", "Downloading: $audioUrl")
   Log.d("AudioPlayer", "Token: $authToken")
   Log.d("AudioPlayer", "File saved: ${audioFile.absolutePath}")
   ```

---

## 📝 RESUMO DAS MUDANÇAS

### Arquivos Modificados:
1. ✅ `activity_image_viewer.xml` - Corrigir ID do ImageView
2. ✅ `item_mensagem_enviada.xml` - Corrigir ícone do anexo
3. ✅ `item_mensagem_recebida.xml` - Corrigir ícone do anexo
4. ✅ `build.gradle.kts` - Verificar/corrigir URL da API

### Arquivos que JÁ ESTÃO CORRETOS (não mexer):
- ✅ `MensagensAdapter.kt`
- ✅ `AudioPlayerHelper.kt`
- ✅ `ImageViewerActivity.kt`
- ✅ `AndroidManifest.xml`

---

## ❓ DÚVIDAS COMUNS

**Q: As imagens ainda não carregam!**
A: Verifique se o servidor da API está rodando e se a URL está correta no `build.gradle.kts`

**Q: O app fecha quando clico na imagem!**
A: Verifique se o ID do ImageView em `activity_image_viewer.xml` está como `imageView`

**Q: O áudio não toca!**
A: Verifique as permissões de armazenamento e se o servidor está servindo o arquivo de áudio corretamente

**Q: Como sei se o token está sendo enviado?**
A: Adicione logs conforme mostrado na seção de debugging e verifique no Logcat

---

## 🎯 PRÓXIMOS PASSOS

Após aplicar todas as correções e testar:

1. Se imagens funcionarem ✅
2. Se áudios funcionarem ✅
3. Se anexos funcionarem ✅

Então o app está OK! 🎉

Se algum problema persistir, me envie:
- Screenshot do erro
- Logs do Logcat
- Qual funcionalidade não funciona (imagem/áudio/anexo)
