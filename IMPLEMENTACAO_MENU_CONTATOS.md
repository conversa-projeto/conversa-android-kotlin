# Documentação - Menu de Contatos e Criação de Grupos

## 📋 Objetivo

Implementar um menu na MainActivity que permita ao usuário:
1. Visualizar seus contatos e iniciar/abrir conversas 1:1
2. Criar grupos com múltiplos contatos

---

## 🎯 Funcionalidades

### Fluxo 1: Contatos (Conversa 1:1)

1. Usuário clica no ícone "Contatos" no menu da MainActivity
2. Abre dialog com lista de contatos do usuário
3. Usuário pode pesquisar contatos pelo nome ou login
4. Ao clicar em um contato:
   - **Se já existe conversa:** Abre a conversa existente
   - **Se não existe:** Cria nova conversa e abre

### Fluxo 2: Criar Grupo

1. Usuário clica no ícone "Novo Grupo" no menu da MainActivity
2. Abre dialog para seleção de múltiplos contatos
3. Usuário marca os contatos que deseja adicionar ao grupo
4. Usuário clica em "Avançar"
5. Abre segunda tela para definir nome e descrição do grupo
6. Usuário confirma e o grupo é criado
7. Abre automaticamente o chat do novo grupo

---

## 🏗️ Estrutura de Arquivos Necessários

### Layouts
```
app/src/main/res/layout/
├── dialog_contatos.xml                    # Dialog com lista de contatos (1:1)
├── dialog_criar_grupo.xml                 # Dialog para seleção de membros
├── dialog_detalhes_grupo.xml              # Dialog para nome/descrição do grupo
├── item_contato.xml                       # Item individual da lista (simples)
└── item_contato_selecionavel.xml          # Item com checkbox para seleção
```

### Menu
```
app/src/main/res/menu/
└── menu_main.xml                          # Adicionar itens:
                                             - "Contatos"
                                             - "Novo Grupo"
```

### Models
```
app/src/main/java/.../data/model/
├── Contato.kt                             # Modelo de dados do contato
└── GrupoRequest.kt                        # Request para criar grupo
```

### API
```
app/src/main/java/.../data/api/
└── ConversaApi.kt                         # Adicionar endpoints:
                                             - listarContatos()
                                             - criarConversa()
                                             - adicionarUsuarioConversa()
```

### Adapters
```
app/src/main/java/.../adapter/
├── ContatosAdapter.kt                     # Adapter para lista simples
└── ContatosSelecionaveisAdapter.kt        # Adapter com seleção múltipla
```

### Activity
```
app/src/main/java/.../
└── MainActivity.kt                        # Adicionar métodos:
                                             - mostrarDialogContatos()
                                             - mostrarDialogCriarGrupo()
                                             - mostrarDialogDetalhesGrupo()
                                             - carregarContatos()
                                             - iniciarOuAbrirConversa()
                                             - criarNovaConversa()
                                             - criarGrupo()
```

---

## 📝 Componentes Principais

### 1. Model - Contato

**Arquivo:** `Contato.kt`

```kotlin
data class Contato(
    val id: Int,
    val nome: String,
    val login: String,
    val email: String?,
    val telefone: String?
)
```

### 2. Model - GrupoRequest

**Arquivo:** `GrupoRequest.kt`

```kotlin
data class GrupoRequest(
    val descricao: String,        // Nome do grupo
    val tipo: Int,                // 2 = Grupo
    val inserida: String,         // Data ISO 8601
    val membros: List<Int>        // IDs dos usuários
)
```

---

## 🔧 API Endpoints

**Arquivo:** `ConversaApi.kt`

### Endpoints Existentes
```kotlin
// 1. Listar contatos do usuário
@GET("usuario/contatos")
suspend fun listarContatos(@Header("Authorization") token: String): Response<List<Contato>>

// 2. Criar nova conversa (1:1 ou Grupo)
@PUT("conversa")
suspend fun criarConversa(@Header("Authorization") token, @Body request): Response<Conversa>

// 3. Adicionar usuário à conversa
@PUT("conversa/usuario")
suspend fun adicionarUsuarioConversa(@Header("Authorization") token, @Body request): Response<Unit>
```

### Requests Necessários
```kotlin
data class CriarConversaRequest(
    val descricao: String,     // Vazio para 1:1, Nome para grupo
    val tipo: Int,             // 1 = Chat 1:1, 2 = Grupo
    val inserida: String       // Data ISO 8601
)

data class AdicionarUsuarioRequest(
    val conversaId: Int,
    val usuarioId: Int
)
```

---

## 🎨 Layouts

### 1. dialog_contatos.xml (Conversa 1:1)

Componentes:
- **Título:** "Selecione um contato"
- **Campo de pesquisa:** TextInputEditText com ícone de busca
- **RecyclerView:** Lista de contatos (item_contato.xml)
- **ProgressBar:** Loading
- **TextViews:** Mensagens de erro e lista vazia
- **Botão:** Cancelar

---

### 2. dialog_criar_grupo.xml (Seleção de Membros)

Componentes:
- **Título:** "Novo Grupo"
- **Subtítulo:** "Selecione os membros"
- **Campo de pesquisa:** Filtrar contatos
- **RecyclerView:** Lista com checkboxes (item_contato_selecionavel.xml)
- **TextView:** Contador "X selecionados"
- **ProgressBar:** Loading
- **TextViews:** Erro e lista vazia
- **Botões:** 
  - Cancelar
  - Avançar (habilitado apenas com 2+ membros selecionados)

---

### 3. dialog_detalhes_grupo.xml (Nome e Descrição)

Componentes:
- **Título:** "Detalhes do Grupo"
- **ImageView:** Ícone do grupo (opcional foto)
- **TextInputEditText:** Nome do grupo (obrigatório)
- **TextInputEditText:** Descrição do grupo (opcional)
- **TextView:** Lista de membros selecionados
- **Botões:**
  - Voltar
  - Criar Grupo

---

### 4. item_contato.xml (Item Simples)

Componentes:
- **CardView** com:
  - **ImageView:** Ícone do contato
  - **TextView:** Nome (negrito)
  - **TextView:** @login

---

### 5. item_contato_selecionavel.xml (Item com Checkbox)

Componentes:
- **CardView** com:
  - **CheckBox:** Seleção
  - **ImageView:** Ícone do contato
  - **TextView:** Nome (negrito)
  - **TextView:** @login

---

## 🔄 Adapters

### 1. ContatosAdapter (Lista Simples)

**Arquivo:** `ContatosAdapter.kt`

Características:
- Estende `ListAdapter<Contato, ViewHolder>`
- Recebe callback `onContatoClick: (Contato) -> Unit`
- Implementa `filtrar(query: String, listaCompleta: List<Contato>)`
- Usa DiffUtil para performance

---

### 2. ContatosSelecionaveisAdapter (Seleção Múltipla)

**Arquivo:** `ContatosSelecionaveisAdapter.kt`

Características:
- Estende `ListAdapter<Contato, ViewHolder>`
- Mantém `Set<Int>` com IDs dos contatos selecionados
- Métodos:
  - `getContatosSelecionados(): List<Contato>`
  - `limparSelecao()`
  - `filtrar(query: String, listaCompleta: List<Contato>)`
- Atualiza checkbox baseado no estado de seleção
- Callback `onSelecaoChanged: (Int) -> Unit` para atualizar contador

---

## 🔧 Alterações na MainActivity

### Menu (menu_main.xml)

```xml
<!-- Item 1: Contatos (Conversa 1:1) -->
<item
    android:id="@+id/action_contatos"
    android:icon="@drawable/ic_person"
    android:title="Contatos"
    app:showAsAction="ifRoom" />

<!-- Item 2: Novo Grupo -->
<item
    android:id="@+id/action_novo_grupo"
    android:icon="@drawable/ic_group_add"
    android:title="Novo Grupo"
    app:showAsAction="ifRoom" />
```

### Variáveis de Classe

```kotlin
private var listaContatos: List<Contato> = emptyList()
private var contatosSelecionados: List<Contato> = emptyList()
```

### FAB (Floating Action Button)

**Opção 1:** FAB abre menu com duas opções
```kotlin
binding.fab.setOnClickListener { 
    // Mostrar PopupMenu com:
    // - Nova Conversa (abre dialog de contatos)
    // - Novo Grupo (abre dialog de criar grupo)
}
```

**Opção 2:** FAB abre contatos, grupo só pelo menu
```kotlin
binding.fab.setOnClickListener { 
    mostrarDialogContatos() 
}
```

### onOptionsItemSelected

```kotlin
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.action_contatos -> {
            mostrarDialogContatos()
            true
        }
        R.id.action_novo_grupo -> {
            mostrarDialogCriarGrupo()
            true
        }
        R.id.action_settings -> { ... }
        R.id.action_logout -> { ... }
        else -> super.onOptionsItemSelected(item)
    }
}
```

---

## 🔄 Fluxo de Implementação - Conversas 1:1

### 1. mostrarDialogContatos()
```
1. Criar Dialog
2. Inflar DialogContatosBinding
3. Configurar tamanho (90% largura, 70% altura)
4. Criar ContatosAdapter com callback simples
5. Configurar RecyclerView
6. Chamar carregarContatos()
7. Configurar pesquisa (TextWatcher)
8. Configurar botão cancelar
9. Mostrar dialog
```

### 2. carregarContatos()
```
1. Mostrar ProgressBar
2. Obter token do UserPreferences
3. Chamar api.listarContatos(token)
4. Se sucesso e não vazio:
   - Guardar em listaContatos
   - Mostrar RecyclerView
   - Submeter lista ao adapter
5. Se vazio: Mostrar tvVazioContatos
6. Se erro: Mostrar tvErroContatos
```

### 3. iniciarOuAbrirConversa(contato)
```
1. Obter token e userId
2. Buscar conversa existente:
   - Filtrar conversasAdapter.currentList
   - tipo == 1 && destinatarioId == contato.id
3. Se encontrou: abrirConversa(conversaExistente)
4. Se não encontrou: criarNovaConversa(contato, token, userId)
```

### 4. criarNovaConversa(contato, token, userId)
```
1. Toast "Criando conversa..."
2. Criar CriarConversaRequest:
   - descricao = ""
   - tipo = 1
   - inserida = data atual ISO
3. Chamar api.criarConversa()
4. Adicionar userId à conversa
5. Adicionar contato.id à conversa
6. Atualizar conversa com nome e destinatarioId
7. abrirConversa(conversaAtualizada)
8. carregarConversas()
```

---

## 🔄 Fluxo de Implementação - Grupos

### 1. mostrarDialogCriarGrupo()
```
1. Criar Dialog
2. Inflar DialogCriarGrupoBinding
3. Configurar tamanho (90% largura, 75% altura)
4. Criar ContatosSelecionaveisAdapter com callback de seleção
5. Configurar RecyclerView
6. Configurar contador "X selecionados"
7. Chamar carregarContatos() (usa mesma API)
8. Configurar pesquisa
9. Configurar botão Cancelar
10. Configurar botão Avançar:
    - Desabilitado se < 2 selecionados
    - Ao clicar: guardar selecionados e chamar mostrarDialogDetalhesGrupo()
11. Mostrar dialog
```

### 2. mostrarDialogDetalhesGrupo()
```
1. Criar Dialog
2. Inflar DialogDetalhesGrupoBinding
3. Configurar tamanho (80% largura, wrap height)
4. Mostrar lista de membros selecionados
5. Configurar validação do nome (obrigatório)
6. Configurar botão Voltar:
   - Fecha dialog atual
   - Retorna para mostrarDialogCriarGrupo()
7. Configurar botão Criar Grupo:
   - Validar nome não vazio
   - Chamar criarGrupo()
8. Mostrar dialog
```

### 3. criarGrupo(nome, descricao, membros)
```
1. Toast "Criando grupo..."
2. Obter token e userId
3. Criar CriarConversaRequest:
   - descricao = nome
   - tipo = 2
   - inserida = data atual ISO
4. Chamar api.criarConversa()
5. Para cada membro (incluindo userId):
   - AdicionarUsuarioRequest(conversaId, membroId)
   - Chamar api.adicionarUsuarioConversa()
6. Criar objeto Conversa completo
7. abrirConversa(novoGrupo)
8. carregarConversas()
```

---

## ✅ Checklist de Implementação

### Fase 1 - Estrutura Básica
- [ ] Criar `Contato.kt`
- [ ] Criar `GrupoRequest.kt`
- [ ] Criar `dialog_contatos.xml`
- [ ] Criar `dialog_criar_grupo.xml`
- [ ] Criar `dialog_detalhes_grupo.xml`
- [ ] Criar `item_contato.xml`
- [ ] Criar `item_contato_selecionavel.xml`
- [ ] Adicionar ícone `ic_group_add` (se não existir)
- [ ] Atualizar `menu_main.xml`

### Fase 2 - API
- [ ] Adicionar `listarContatos()` em ConversaApi
- [ ] Adicionar `criarConversa()` em ConversaApi
- [ ] Adicionar `adicionarUsuarioConversa()` em ConversaApi
- [ ] Criar `CriarConversaRequest`
- [ ] Criar `AdicionarUsuarioRequest`

### Fase 3 - Adapters
- [ ] Criar `ContatosAdapter.kt`
- [ ] Criar `ContatosSelecionaveisAdapter.kt`
- [ ] Implementar ViewHolders
- [ ] Implementar DiffCallbacks
- [ ] Implementar métodos de filtro
- [ ] Implementar lógica de seleção múltipla

### Fase 4 - MainActivity (Conversas 1:1)
- [ ] Adicionar variável `listaContatos`
- [ ] Adicionar case `R.id.action_contatos` no menu
- [ ] Implementar `mostrarDialogContatos()`
- [ ] Implementar `carregarContatos()`
- [ ] Implementar `iniciarOuAbrirConversa()`
- [ ] Implementar `criarNovaConversa()`

### Fase 5 - MainActivity (Grupos)
- [ ] Adicionar variável `contatosSelecionados`
- [ ] Adicionar case `R.id.action_novo_grupo` no menu
- [ ] Implementar `mostrarDialogCriarGrupo()`
- [ ] Implementar `mostrarDialogDetalhesGrupo()`
- [ ] Implementar `criarGrupo()`
- [ ] Atualizar FAB (se necessário)

### Fase 6 - Testes
- [ ] Testar carregamento de contatos
- [ ] Testar pesquisa de contatos
- [ ] Testar criação de conversa 1:1
- [ ] Testar abertura de conversa existente
- [ ] Testar seleção de múltiplos contatos
- [ ] Testar validação de mínimo 2 membros
- [ ] Testar criação de grupo
- [ ] Testar validação de nome do grupo
- [ ] Testar navegação entre dialogs
- [ ] Testar tratamento de erros

---

## 🔍 Pontos de Atenção

### 1. Diferença entre Chat e Grupo
```kotlin
// Chat 1:1
tipo = 1
descricao = ""
membros = 2 (usuário atual + contato)

// Grupo
tipo = 2
descricao = "Nome do Grupo"
membros = 2+ (usuário atual + contatos selecionados)
```

### 2. Validações

**Conversa 1:1:**
- Verificar se conversa já existe antes de criar

**Grupo:**
- Mínimo 2 membros além do criador (total 3)
- Nome do grupo obrigatório
- Descrição opcional

### 3. Ordem de Criação do Grupo
```
1. Criar conversa (tipo=2, descricao=nome)
2. Adicionar criador (userId)
3. Adicionar cada membro selecionado
4. Atualizar lista de conversas
5. Abrir chat do grupo
```

### 4. Estados do Botão "Avançar"
```kotlin
// Habilitar apenas quando >= 2 contatos selecionados
btnAvancar.isEnabled = contatosSelecionados.size >= 2
```

### 5. Navegação Entre Dialogs
```kotlin
// Dialog Criar Grupo → Dialog Detalhes
dialog1.dismiss()
mostrarDialogDetalhesGrupo()

// Dialog Detalhes → Voltar para Criar Grupo
dialog2.dismiss()
mostrarDialogCriarGrupo() // Manter seleções anteriores
```

### 6. Persistência de Seleção
Ao voltar do dialog de detalhes para seleção, manter os contatos marcados:
```kotlin
// Guardar seleção em variável de classe
contatosSelecionados = adapter.getContatosSelecionados()
```

### 7. Contador de Selecionados
Atualizar em tempo real:
```kotlin
adapter.onSelecaoChanged = { quantidade ->
    tvContador.text = "$quantidade selecionados"
    btnAvancar.isEnabled = quantidade >= 2
}
```

---

## 🎨 Interface do Usuário

### Estados do Dialog de Contatos (1:1)
1. **Loading:** ProgressBar visível
2. **Sucesso:** RecyclerView com lista
3. **Vazio:** "Nenhum contato encontrado"
4. **Erro:** Mensagem de erro

### Estados do Dialog Criar Grupo
1. **Loading:** ProgressBar visível
2. **Sucesso:** RecyclerView com checkboxes
3. **Selecionando:** Contador atualiza, botão Avançar habilita/desabilita
4. **Vazio:** "Nenhum contato encontrado"
5. **Erro:** Mensagem de erro

### Estados do Dialog Detalhes Grupo
1. **Inicial:** Campos vazios, botão Criar desabilitado
2. **Preenchendo:** Validando nome em tempo real
3. **Válido:** Nome preenchido, botão Criar habilitado
4. **Criando:** Loading, botão desabilitado

---

## 📚 Exemplo de Uso da API

### Criar Grupo Completo

```kotlin
// 1. Criar conversa tipo 2
POST /conversa
{
  "descricao": "Família",
  "tipo": 2,
  "inserida": "2025-10-31T14:30:00"
}
Response: { "id": 5, ... }

// 2. Adicionar criador
PUT /conversa/usuario
{ "conversa_id": 5, "usuario_id": 1 }

// 3. Adicionar membro 1
PUT /conversa/usuario
{ "conversa_id": 5, "usuario_id": 2 }

// 4. Adicionar membro 2
PUT /conversa/usuario
{ "conversa_id": 5, "usuario_id": 3 }

// Resultado: Grupo "Família" com 3 membros
```

---

## 🎯 Resultado Final

### Funcionalidades Implementadas

**Menu de Contatos:**
1. ✅ Visualizar todos os contatos
2. ✅ Pesquisar contatos
3. ✅ Iniciar conversa 1:1
4. ✅ Abrir conversa existente automaticamente

**Criação de Grupos:**
1. ✅ Selecionar múltiplos contatos
2. ✅ Pesquisar durante seleção
3. ✅ Contador de selecionados
4. ✅ Validação de mínimo 2 membros
5. ✅ Definir nome e descrição do grupo
6. ✅ Criar grupo e adicionar todos os membros
7. ✅ Abrir chat do grupo automaticamente

---

**Documentação criada em:** 31/10/2025  
**Atualização:** Incluída funcionalidade de criação de grupos
