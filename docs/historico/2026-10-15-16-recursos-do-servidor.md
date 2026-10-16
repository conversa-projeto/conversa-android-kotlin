# 2026-10-15 · 16 · `GET /recursos`: "Transcrever" só com transcritor

- **Fluxo:** Anexos e mídia (etapa 4); toca também em Atividades, pesquisa, perfil e configurações (8.8)
- **Tipo:** código
- **Itens:** `TODO.md` 4.8 (linha 🆕 `769bd7d` do "Transcrever") e 8.8 (1ª linha, a parte do `/recursos`); FC-419; ANX-12, GER-08
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`769bd7d`): diz o que tem ligado em `GET /recursos` → `{transcricao, ia}`.
- **O web:** lê os recursos uma vez por sessão (`useRecursos`) e esconde "Transcrever" quando não há transcritor.
- **O Android:** só descobria isso depois de um pedido recusado (400). Até lá, o botão aparecia e falhava.
- **O servidor de dev:** responde `{transcricao:false, ia:false}`.

## O que foi feito
- **Modelo e rede:** `Recursos(transcricao, ia)` (`core/model/.../Sistema.kt`), `RecursosDto` e `ConversaApi.recursos()`.
- **`RecursosRepositorio`** (`core/data/sistema/`, novo):
  - a cada sessão, lê os recursos (tenta de novo a cada 30 s se a API não responder) e limpa no fim da sessão;
  - nulo enquanto não se sabe: a tela segue como antes;
  - começa no `ConversaApplication`, junto do início da sessão;
  - deixa um log de depuração só com os dois valores.
- **`TranscricoesRepositorio.desligada`:** passou a juntar o 400 de antes com `recursos.transcricao == false`. Sem transcritor, o botão "Transcrever" some desde a abertura. A transcrição já feita, ou em andamento, continua aparecendo, porque a tela só esconde o botão.
- **Testes:** o `/recursos` sem transcritor desliga o botão e, com ele, liga de novo. O teste do 400 ganhou um `runCurrent()`, porque agora o valor sai de um fluxo combinado.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (502 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev** (sem transcritor; os parâmetros dele não foram mexidos):
  - o logcat mostrou `GET /api/recursos` → 200 e "transcricao=false ia=false";
  - aberta pelo link na gravação 1094 do grupo 3, a bolha não mostrou "Transcrever";
  - duas aberturas do zero (app parado) deram o mesmo resultado.
  - Na primeira tentativa o botão apareceu. O APK instalado ainda era o anterior: aquela verificação tinha parado num teste antes de gerar o APK novo.

## Pendências
- 8.8: "Resumir conversa" e a sugestão ao digitar usam o `ia` destes recursos. O teste deles precisa de um servidor de IA no Sistema do servidor de dev, o que mexe nos parâmetros: pedir antes.
