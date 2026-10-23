# 2026-10-22 · 09 · Campo de mensagem religava o teclado a cada letra

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código (correção)
- **Itens:** `TODO.md` 7.7 (menções; linha nova da correção); FC-508
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
No celular, ao digitar no campo de mensagem do chat, o teclado abria e fechava sem parar, a cada toque. A pessoa notou com um servidor de IA ligada, mas a IA não era a causa.

## Causa
- O destaque das menções (`destaqueDasMencoes`, um `OutputTransformation`, desde `76a7357`) era criado de novo a cada recomposição da linha do campo.
- A linha se recompõe a cada letra: ela lê o texto para escolher entre o Enviar e o microfone.
- O `TextField` trata uma transformação nova como um campo novo e refaz a ligação com o teclado (`restartInput`).
- **Efeito:** cada letra religava o teclado. No Gboard do emulador, letras se perdiam ("Bom dia" virava "Bo i"); em outros teclados, como o do celular, o teclado fecha e abre a cada toque.

## Como foi encontrado
- **No emulador, com o `ImeTracker` do sistema** (registra cada pedido de abrir o teclado e o motivo): "Bom dia" digitado pelo adb gerou 4 `ATTACH_NEW_INPUT` (teclado religado ao campo) e ficou "Bo i".
  - Igual com a IA simulada (proxy de teste local respondendo `ia: true` e a sugestão) e com a IA desligada, o que descartou a sugestão da IA.
  - No campo "Nome da pasta" (sem `OutputTransformation`), o mesmo texto entrou inteiro.
- Também foram descartados, lendo o código: o rascunho (FC-519, só entra uma vez), a sugestão da IA (não pega o foco) e a barra de pesquisa da lista.

## O que foi feito
- `CampoMensagem.kt` (`LinhaDoCampo`): `remember(mencoes, cor) { destaqueDasMencoes(mencoes, cor) }`.
  - A lista de menções é a mesma instância (`SnapshotStateList`), lida dentro da transformação, então o destaque continua acompanhando as menções novas.
- **`CLAUDE.md`, "Campos de texto":** as transformações e os outros objetos passados ao `TextField` vão com `remember`.

## Como foi verificado
- **No emulador, com a correção:**
  - "Bom dia" e "Bom dia para voce": 0 religações, texto inteiro, teclado sempre aberto (amostrado a cada ~0,2 s);
  - com a IA simulada: a sugestão "e mais um pouco" apareceu, sem nenhuma religação;
  - num grupo: "Oi @Teste Android C tudo bem", com a menção em azul e negrito, sem nenhuma religação ao continuar digitando.
- `:feature:chat:testDebugUnitTest`, `ktlintCheck`, `:app:lintDebug` e `assembleDebug`.
- Os outros campos do app não têm o padrão: os de senha usam a API antiga (`PasswordVisualTransformation`, comparada pelo valor), e os `maxLength` também são comparados pelo valor.

## Decisões
- **Sem teste automático:** seria preciso um teste de interface com o teclado de verdade, que o projeto ainda não tem (etapa 10). A regra no `CLAUDE.md` e o roteiro acima (digitar pelo adb e contar os `ATTACH_NEW_INPUT` do `ImeTracker`) ficam como proteção.

## Pendências
- Conferir no celular da pessoa, com o teclado dela.
