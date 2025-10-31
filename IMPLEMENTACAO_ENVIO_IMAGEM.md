# Implementação de Envio de Imagem - Conversa Android

## 📱 Resumo da Implementação

Foi adicionada a funcionalidade completa de **envio de imagens** no chat do aplicativo Conversa Android (Kotlin).

---

## 🎯 Funcionalidades Adicionadas

### 1. **Seleção de Imagem da Galeria**
- Botão de anexar imagem na interface do chat
- Seletor de imagem nativo do Android
- Suporte para múltiplos formatos: JPG, PNG, GIF, WEBP

### 2. **Upload Inteligente**
- Cálculo de hash SHA256 do arquivo
- Verificação se arquivo já existe no servidor (evita reenvio)
- Upload apenas se necessário
- Indicador de progresso durante o envio

### 3. **Envio de Mensagem com Imagem**
- Suporte a mensagem apenas com imagem
- Suporte a mensagem com imagem + texto
- Integração perfeita com o sistema de mensagens existente

### 4. **Gerenciamento de Permissões**
- Suporte completo para Android 10+ (READ_EXTERNAL_STORAGE)
- Suporte para Android 13+ (READ_MEDIA_IMAGES)
- Solicitação de permissão em runtime
- Tratamento de permissão negada

---

## 📁 Arquivos Criados

### 1. **UploadHelper.kt**
**Localização:** `app/src/main/java/com/conversa/conversa/data/api/UploadHelper.kt`

Helper responsável pelo upload de imagens:
- `uploadImagem()`: Faz upload completo da imagem
- `calcularSHA256()`: Calcula hash do arquivo
- `obterNomeArquivo()`: Extrai nome do arquivo da URI
- `obterExtensao()`: Detecta extensão da imagem
- `formatarTamanhoArquivo()`: Formata tamanho para exibição

### 2. **ic_attach.xml**
**Localização:** `app/src/main/res/drawable/ic_attach.xml`

Ícone de anexar (clipe de papel) em formato vetorial.

---

## 🔧 Arquivos Modificados

### 1. **activity_chat.xml**
**Alteração:** Adicionado botão de anexar imagem

```xml
<ImageButton
    android:id="@+id/btnAnexar"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_gravity="bottom"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="Anexar imagem"
    android:src="@drawable/ic_attach"
    android:tint="@color/corPrimaria" />
```

### 2. **ChatActivity.kt**
**Alterações principais:**

**a) Imports adicionados:**
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.conversa.conversa.data.api.UploadHelper
```

**b) Propriedades adicionadas:**
```kotlin
private lateinit var uploadHelper: UploadHelper
private val selecionarImagemLauncher = registerForActivityResult(...)
private val solicitarPermissaoGaleriaLauncher = registerForActivityResult(...)
```

**c) Métodos adicionados:**
- `verificarPermissaoEAbrirGaleria()`: Verifica e solicita permissão
- `abrirSeletorImagem()`: Abre o seletor de imagem
- `enviarImagemSelecionada()`: Processa e envia a imagem selecionada

### 3. **ConversaApi.kt**
**Alterações:** Adicionados endpoints de upload

```kotlin
@GET("anexo/existe")
suspend fun verificarAnexoExiste(...)

@PUT("anexo")
suspend fun uploadAnexo(...)

// Data classes para respostas
data class AnexoExisteResponse(val existe: Boolean)
data class AnexoUploadResponse(...)
```

---

## 🔐 Permissões Necessárias

Já estavam configuradas no **AndroidManifest.xml**:

```xml
<!-- Android 10-12 -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
```

---

## 🚀 Fluxo de Funcionamento

### 1. **Usuário Clica no Botão de Anexar**
```
Clique no botão → Verificação de permissão → Seletor de imagem
```

### 2. **Upload da Imagem**
```
Seleção → Leitura do arquivo → Cálculo SHA256 → 
Verificação no servidor → Upload (se necessário) → Retorna identificador
```

### 3. **Envio da Mensagem**
```
Identificador da imagem → Criação de ConteudoRequest(tipo=2) →
Opcional: ConteudoRequest(tipo=1) para texto →
Envio via API → Reload das mensagens
```

---

## 📊 Tipos de Conteúdo

Conforme a documentação técnica:

- **tipo = 1**: Texto
- **tipo = 2**: Imagem ✅ (implementado)
- **tipo = 3**: Arquivo
- **tipo = 4**: Áudio

---

## ✅ Testes Recomendados

### 1. **Teste de Permissões**
- [ ] Testar em Android 13+ (READ_MEDIA_IMAGES)
- [ ] Testar em Android 10-12 (READ_EXTERNAL_STORAGE)
- [ ] Testar negação de permissão
- [ ] Testar concessão de permissão

### 2. **Teste de Upload**
- [ ] Enviar imagem JPG
- [ ] Enviar imagem PNG
- [ ] Enviar imagem grande (> 5MB)
- [ ] Enviar mesma imagem duas vezes (verificar cache)

### 3. **Teste de Mensagens**
- [ ] Enviar apenas imagem
- [ ] Enviar imagem + texto
- [ ] Enviar em chat 1:1
- [ ] Enviar em grupo

### 4. **Teste de UI**
- [ ] Verificar indicador de loading
- [ ] Verificar desabilitação de botões durante upload
- [ ] Verificar exibição da imagem enviada
- [ ] Verificar scroll automático após envio

---

## 🐛 Tratamento de Erros

### Erros Capturados:

1. **Arquivo não pode ser lido**: Toast de erro
2. **Falha no upload**: Toast com mensagem de erro
3. **Falha ao enviar mensagem**: Toast com código HTTP
4. **Exceções gerais**: Toast com mensagem da exceção

### Logging:
```kotlin
catch (e: Exception) {
    e.printStackTrace()
    Toast.makeText(...)
}
```

---

## 📝 Próximos Passos Sugeridos

### 1. **Melhorias de UX**
- [ ] Adicionar preview da imagem antes de enviar
- [ ] Adicionar indicador de progresso de upload
- [ ] Permitir cancelamento do upload
- [ ] Adicionar suporte a múltiplas imagens

### 2. **Funcionalidades Adicionais**
- [ ] Envio de arquivos (PDF, DOC, etc.)
- [ ] Gravação e envio de áudio
- [ ] Captura de foto pela câmera
- [ ] Compressão de imagens antes do envio

### 3. **Otimizações**
- [ ] Cache de thumbnails
- [ ] Lazy loading de imagens
- [ ] Compression de imagens grandes
- [ ] Retry automático em caso de falha

---

## 🎨 Interface do Usuário

### Antes:
```
[___Campo_de_texto___] [Enviar]
```

### Depois:
```
[Anexar] [___Campo_de_texto___] [Enviar]
```

### Comportamento:
1. **Anexar**: Abre galeria de imagens
2. **Campo de texto**: Permite adicionar legenda opcional
3. **Enviar**: Envia imagem + texto (se houver)

---

## 📖 Referências

- **Documentação Técnica**: `DOCUMENTACAO_TECNICA.md`
- **Endpoint de Upload**: `PUT /anexo`
- **Endpoint de Verificação**: `GET /anexo/existe`
- **Formato de Mensagem**: `PUT /mensagem`

---

## ⚙️ Configuração do Servidor

O servidor já suporta upload de imagens conforme documentação:

```
PUT /anexo?tipo=2&nome=imagem&extensao=jpg
Header: Authorization: Bearer {token}
Body: bytes da imagem
```

Resposta:
```json
{
  "id": 1,
  "identificador": "sha256_hash",
  "tipo": 2,
  "tamanho": 1024000
}
```

---

## ✨ Conclusão

A funcionalidade de **envio de imagem** foi implementada com sucesso, seguindo:

✅ Arquitetura MVVM
✅ Padrões do projeto existente
✅ Boas práticas Android
✅ Gerenciamento adequado de permissões
✅ Tratamento de erros completo
✅ Integração com API existente

O aplicativo agora suporta o envio de imagens da galeria, com verificação de duplicatas e upload otimizado!

---

**Data da Implementação:** 30 de outubro de 2025
**Versão:** 1.0
**Status:** ✅ Implementado e pronto para testes
