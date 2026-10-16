# 2026-10-16 · 08 · Sugestão de texto da IA

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8); toca o campo de mensagem (etapa 3)
- **Tipo:** código
- **Itens:** `TODO.md` 8.8 (linhas dos recursos e da sugestão); FC-523; ENV-25; GER-08
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`ea02f57`, contrato §13.6) completa o que a pessoa digita: `POST /ia/sugestao {conversa_id, texto}` → `{sugestao}`, a continuação (pode começar com espaço). Falha não é erro (vem vazia), e cancelar a requisição cancela o pedido à IA.
- **O web** (`f798e9c`, `useSugestaoIa.ts`):
  - pede depois de 600 ms parado, com o campo em foco, sem a lista de menção, sem seleção, com o cursor no fim e pelo menos 3 caracteres;
  - mostra em cinza depois do cursor (Tab aceita, Esc descarta);
  - cada navegador liga ou desliga em Configurações › Privacidade ("Inteligência artificial", só com a IA ligada).
- **No app** não existia. Com isso, os recursos do servidor (GER-08) ficam completos: o Transcrever, o Resumir e a sugestão só aparecem quando o servidor tem.

## O que foi feito
- **`core:model` (`Sugestao.kt`):** `podePedirSugestao` (cursor no fim, sem seleção, 3 caracteres) e `textoParaSugestao` (o texto até o cursor, no limite de 4000 do servidor).
- **Rede:** `ConversaApi.sugestao`, `PedirSugestaoRequisicao` e `SugestaoDto`.
- **Preferência do aparelho:** `sugestoes_ia` no `PreferenciasStore` (padrão ligada, como o web) e no `PreferenciasRepositorio`.
- **`SugestoesRepositorio`** (`core:data/ia`): `disponivel` (a IA do servidor), `ligadas` (servidor e preferência) e `sugerir` (falha vira vazio; o cancelamento passa adiante e cancela a chamada).
- **Campo de mensagem (`SugestaoDaIa`, `SugestaoViewModel`):**
  - observa o texto, o cursor e o foco; cada mudança apaga a sugestão e cancela o pedido em andamento (`collectLatest`);
  - pede depois de 600 ms nas mesmas condições do web;
  - no celular não há Tab: a continuação aparece num chip acima do campo, com o ícone de "faísca". Tocar insere no cursor; o "×" descarta. O leitor de tela diz "Sugestão da IA: … Toque para usar.";
  - resposta que chega depois de o campo mudar é jogada fora.
- **Privacidade:** a seção "Inteligência artificial" com "Sugerir a continuação ao digitar", só com a IA do servidor. O texto foi adaptado do web: "num balão acima do campo: toque para usar" no lugar do Tab. A chave fica no `SugestoesIaViewModel`, sem mexer no ViewModel da privacidade do servidor.
- **Testes:** `SugestaoTest` (as condições e o texto enviado) e `SugestoesRepositorioTest` (liga com o servidor e a preferência; a continuação vem como está; a falha vira vazio).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (551 testes), `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A), contra o servidor de dev, sem IA configurada:** a Privacidade ficou como antes, sem a seção "Inteligência artificial", e o campo de mensagem sem o chip.
- **Não testado de ponta a ponta:** precisa de um servidor de IA no Sistema do servidor de dev, e os parâmetros de IA só mudam com autorização (TODO 8.8, linha do teste).

## Decisões
- **Chip acima do campo, e não texto cinza no próprio campo:** o `TextField` não desenha texto que não faz parte dele, e no celular não há Tab. O chip é tocável e visível acima do teclado.
- **A resposta só vale para o texto exato em que foi pedida:** se a pessoa digitou enquanto a IA pensava, a sugestão some sem aparecer.

## Pendências
- Conferir com um servidor de IA (pedir antes de mexer nos parâmetros do servidor de dev).
