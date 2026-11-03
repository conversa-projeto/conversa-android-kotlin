# 2026-11-02 · 01 · O rascunho aparece na lista de conversas

- **Fluxo:** Ações sobre mensagens (etapa 7); também Sessão, conversas, contatos e presença (etapa 2), pela lista
- **Tipo:** funcionalidade (diferença do web, a pedido)
- **Itens:** `TODO.md` 7.11 (linha nova, depois do rascunho por conversa); ENV-23, FC-519; CON-01
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A pessoa pediu uma revisão do rascunho: o que se digita fica guardado, e aparece como rascunho na lista de conversas?
- **O rascunho por conversa** (histórico `2026-10-08-33`) funcionava: texto, menções, anexos e resposta pendente, guardados meio segundo depois da última mudança e na saída da conversa.
- **A lista não mostrava nada:** a segunda linha continuava com a última mensagem.

O web, que é a referência, também não mostra o rascunho na lista; lá ele só volta ao abrir a conversa. A pessoa aprovou mostrar no Android, como no WhatsApp.

## O que foi feito
- **`core/model/.../ListaConversas.kt`:**
  - `RascunhoNaLista(texto, anexos)` e `rascunhoNaLista(texto, anexos)`;
  - o texto vai para uma linha, como a prévia: menção vira `@Nome` e código vira "Código (js)";
  - sem texto nem anexos, a função devolve nulo. Uma resposta pendente sozinha não aparece na lista.
- **`core/database/.../Daos.kt`:** `RascunhoDao.observarTodos()` (um `Flow`). Não é migração: a tabela é a mesma.
- **`core/data/.../RascunhosRepositorio.kt`:** `observarNaLista()` devolve um mapa por conversa, com o texto e quantos anexos tem o rascunho.
- **`feature/conversas/.../ConversasViewModel.kt`:** o `ItemConversa` ganha `rascunho`, que vem do Room junto com as conversas e a presença.
- **`feature/conversas/.../ConversasTela.kt` (`LinhaRascunho`):**
  - na segunda linha, "**Rascunho:**" na cor principal e depois o texto, em cinza como a prévia;
  - sem texto, mostra "Rascunho: 2 anexos";
  - "Gravando áudio…" e o "digitando" continuam por cima, e a hora e o contador não mudam;
  - a posição da conversa na lista também não muda.
- **`strings.xml` (`feature/conversas`):** `lista_rascunho` ("Rascunho:") e o plural `lista_rascunho_anexos` ("%d anexo"/"%d anexos").
- **`docs/design/cores.md` §6.4:** o papel "Rascunho:" reaproveita o `primary`, sem cor nova.
- **`docs/auditoria-2026-10/05-matriz-paridade.md`:** na linha do ENV-23, a diferença do web.
- **Testes:**
  - `ListaConversasTest`: o texto em uma linha (menção e código), só anexos, e o vazio que não aparece;
  - `RascunhosRepositorioTest` (Room em memória): texto e anexos aparecem, a resposta sozinha não aparece, e o rascunho sai ao esvaziar;
  - `ConversasViewModelTest`: o rascunho vai para o item certo, também nas arquivadas, e some quando o rascunho é apagado.

## Como foi verificado
- `:app:assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de antes), `ktlintCheck`, `:app:compileDebugAndroidTestKotlin` e `textos-repetidos`.
- **No emulador** (Android 16, conta A no servidor de dev):
  - na conversa com o "Teste Android C", digitado "vou conferir amanha", voltando pelo "Voltar": a lista mostrou "Rascunho: vou conferir amanha", com "Rascunho:" em azul, no mesmo lugar da lista;
  - reaberta a conversa, o campo voltou com o texto;
  - apagado o texto e de volta à lista, voltou a prévia "Anexo ou chamada".
- **O app do emulador:**
  - o `install -r` falhou de novo por espaço, e foi desinstalar e instalar (histórico `2026-10-31-06`);
  - o servidor e o login da conta A foram configurados de novo.

## Decisões
- **A cor principal**, e não o verde do WhatsApp: o verde da paleta (`chamadaAtender`) é o de atender chamada, e o azul já marca o que pede atenção na lista (a hora com não lidas).
- **A resposta pendente sozinha não aparece:** não há texto para mostrar, e "Rascunho:" sem nada confundiria.
- **A ordem da lista não muda** com o rascunho, como no WhatsApp: a lista continua pela última mensagem.
- **A busca da lista não procura no rascunho:** continua pelo título e pela prévia.
