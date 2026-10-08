# 2026-10-08 · 29 · Etapa 7 — atalhos de emoji, colar texto longo e "Inserir código"

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.11; FC-515
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O web tem três recursos no campo de mensagem:
- atalhos de texto que viram emoji (`emojiAtalhos.ts`);
- a janela "Inserir código" (`CodigoModal.vue`), aberta pelo "+" → "Código";
- a sugestão dessa janela ao colar um texto com mais de 10 linhas (`textoLongo`). Ali, "Cancelar" cola o texto como estava.

O Android não tinha nenhum dos três.

## O que foi feito
- **`core:model` — `Atalhos.kt`:** porte do `emojiAtalhos.ts` sem regex. Também traz:
  - a mesma tabela de atalhos, com o mais longo vencendo;
  - só palavra solta e fora de código (`` ` `` e ```` ``` ````);
  - `substituirAtalhoAntesDoCursor` e `substituirAtalhoNoFim`;
  - `textoLongo`, `cercaCodigo` (cerca maior que as crases do código), `blocoDeCodigo` e `LINGUAGENS_CODIGO` (as do web).
- **`feature:chat` — `InserirCodigo.kt`:**
  - **diálogo "Inserir código":** menu de linguagem ("texto" padrão; "markdown" se o colado tem ```` ``` ````), o código em monoespaçado com o placeholder do web, e "Cancelar"/"Enviar". "Enviar" fica desabilitado sem código;
  - **`transformacaoDoCampo` (`InputTransformation` do campo):**
    - uma inserção com mais de 10 linhas (colar) é desfeita e abre o diálogo preenchido;
    - espaço ou quebra logo depois de um atalho troca o atalho pelo emoji.
- **`CampoMensagem.kt`:**
  - "Código" no menu do "+";
  - "Cancelar" de um colado devolve o texto ao cursor;
  - no envio, `substituirAtalhoNoFim` (sem os espaços do fim).
- **Testes:** `AtalhosTest` (6 casos, os do `utilsTexto.test.ts` do web e os da cerca).

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A na direta com o C e no "Grupo criado pelo B"):**
  - "oi :)" + espaço virou "oi 🙂 " no campo;
  - "a <3" enviado chegou ao servidor como "a ❤️";
  - "+" → "Código": escolhi python, digitei e enviei. O servidor recebeu ```` ```python\nx= 1\n``` ```` e a bolha mostrou o bloco com "python" e "Copiar";
  - "Copiar" no JS de 24 linhas, depois colar no campo: abriu "Inserir código" preenchido, com "texto";
  - "Cancelar" colou as 24 linhas no campo.

## Decisões
- **Sem detecção automática de linguagem ao colar** (o web usa a do highlight.js): vem "texto", ou "markdown" com ```` ``` ```` dentro. A pessoa escolhe no menu.
- **O "colar como código" automático do web (`pareceCodigo`, para comandos de terminal e desenhos ASCII) não foi portado:** o TODO pede só a sugestão para mais de 10 linhas.
- **"Enviar" do "Inserir código" não apaga o que estava digitado no campo** (o web limpa o campo): nada digitado se perde.

## Pendências
- Nenhuma do 7.11.
