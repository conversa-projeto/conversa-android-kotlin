# 2026-10-08 · 33 · Rascunho por conversa

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade (acompanha o web) / banco de dados
- **Itens:** `TODO.md` 7.11 (linha 🆕 `7322e83`); FC-519; ENV-23
- **Branch:** `reescrita`
- **Commits:** `217be62`

## Contexto
O web `7322e83` passou a guardar um rascunho por usuário e conversa (`services/rascunhos.ts`, IndexedDB). O rascunho tem o campo, os arquivos e o "respondendo a…", e volta ao reabrir a conversa, mesmo depois de fechar o navegador. No Android, sair da conversa perdia o texto, a fila de anexos e a resposta pendente.

## O que foi feito
- **`core:database`:**
  - tabela `rascunho` (`RascunhoEntidade`: `conversaId`, texto, anexos em JSON, tipo e id da referência, `atualizadoEm`) e `RascunhoDao`;
  - **versão 3 do banco** com `AutoMigration(2, 3)`, que só cria a tabela; o esquema `3.json` está versionado;
  - o banco já é limpo ao sair da conta, então o rascunho é por usuário.
- **`core:data` — `RascunhosRepositorio`:**
  - `ler(conversaId)` → `Rascunho(texto, anexos, referencia)`;
  - `guardar` grava no escopo do app; vazio apaga;
  - gravações seguidas da mesma conversa valem na ordem: a mais antiga que chegue atrasada é ignorada.
- **`feature:chat`:**
  - **`ChatViewModel`:**
    - ao abrir, restaura o rascunho: os anexos entram na fila, o texto vai para o campo e a resposta volta se a mensagem está no aparelho;
    - um texto compartilhado por outro app vem depois do texto do rascunho;
    - depois, cada mudança de texto, fila ou resposta é gravada 500 ms após a última;
    - ao sair (`onCleared`), grava o último estado na hora.
  - **`CampoMensagem.kt`:**
    - informa o texto com as menções cruas (`@[Nome](id)`) a cada mudança;
    - o texto posto pelo app (rascunho ou compartilhado) não manda mais "digitando".
- **Testes:**
  - `RascunhosRepositorioTest` (Room em memória): guardar, ler e apagar; gravações na ordem;
  - `ChatViewModelTest`: o rascunho volta ao abrir; as mudanças viram rascunho depois do atraso.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A na direta com o C):**
  - O app atualizado abriu sem erro: a migração 2 → 3 rodou sobre o banco existente.
  - "Responder" em uma mensagem e "rascunho" digitado: ao sair e voltar, a barra da resposta e o texto estavam lá; depois de fechar o app à força (`am force-stop`) e reabrir, também.
  - Sem a resposta e com o campo apagado, ao fechar e reabrir o app o campo veio vazio.

## Decisões
- **Room, e não DataStore:** o rascunho é por conversa e já existe o banco do usuário, limpo no logout.
- **Os anexos guardam o URI:** os escolhidos já têm a permissão persistente (`manterAcesso`), e os da câmera e os colados ficam no cache do app.

## Pendências
- Nenhuma do FC-519.
