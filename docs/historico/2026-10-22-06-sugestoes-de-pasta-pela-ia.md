# 2026-10-22 · 06 · Sugestões de pasta pela IA

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.14 (a linha 🆕 `b104db8`/`57bd9f0`); FC-225; CON-18
- **Branch:** `reescrita`
- **Commits:** `b8695b5`

## Contexto
No web (`57bd9f0`, servidor `b104db8`), o painel de pastas tem "Sugestões da IA":
- com a IA ligada e alguma pasta, "Sugerir pastas" pede ao servidor que a IA olhe as mensagens recentes que ainda não estão em pasta e sugira a pasta de cada uma;
- a análise roda no servidor (`POST /pastas/sugerir`) e é consultada a cada 2 s (`GET /pastas/sugestao`) até sair de "processando";
- cada sugestão mostra quem escreveu, a prévia e a pasta; ✓ põe na pasta, ✕ ignora, e as ignoradas (guardadas no navegador por pessoa e conversa) vão em `ignorar` no pedido seguinte;
- "Aceitar todas" faz uma chamada por pasta. Nada entra na pasta sem a pessoa confirmar.

## O que foi feito
- **Modelo (`core/model/.../Pastas.kt`):** `SugestaoPasta`, `StatusSugestoes` (processando, concluído, erro), `SugestoesPastas` e `juntarIgnoradas` (sem repetir, as últimas 2000, o limite do servidor).
- **Rede:** `pedirSugestoesPastas` e `sugestoesPastas` na `ConversaApi`; `PedirSugestoesPastasRequisicao`, `SugestaoPastaDto` e `SugestoesPastasDto` com o mapeador (status desconhecido conta como erro).
- **Dados:**
  - `PastasRepositorio.pedirSugestoes` e `consultarSugestoes`;
  - as ignoradas ficam no `PreferenciasStore`, por pessoa e conversa (`sugestoes_ignoradas_<usuário>_<conversa>`), lidas e somadas pelo `PreferenciasRepositorio`.
- **`PastasViewModel`:**
  - `iaLigada` (dos recursos do servidor) e `sugestoes`, que esconde as sugestões de pastas que não existem mais (como o web);
  - `sugerirPastas` consulta a cada 2 s até sair de "processando"; o erro do servidor aparece na folha;
  - `aceitarSugestao`, `aceitarTodasSugestoes` (uma chamada por pasta; para na primeira falha, e as que faltam ficam na lista) e `ignorarSugestao`.
- **Folha de pastas (`Pastas.kt`):** no pé, "Sugestões da IA":
  - "Sugerir pastas" ("Analisando…" enquanto a IA trabalha) e a linha de estado: analisando, erro, "Nenhuma sugestão pendente." ou "As mensagens recentes já estão em pastas.";
  - cada sugestão num cartão: quem escreveu (em negrito) e a prévia em até 3 linhas, com as quebras e tabulações viradas em espaço, como o HTML do web mostra; a pasta com a cor; ✓ e ✕;
  - "Aceitar todas (N)" com mais de uma.
- **`juntarEspacos`** (`Texto.kt`), que já existia para os resumos, ficou pública para a prévia.
- **Textos** do web em `strings.xml` (`pasta_sugestoes_*`, `pasta_sugerir*`, `pasta_sugestao_*`).
- **Testes:**
  - `PastasTest`: `juntarIgnoradas`;
  - `TextoTest`: `juntarEspacos`;
  - `PastasRepositorioTest`: o pedido com as ignoradas e a leitura do resultado;
  - `PastasViewModelTest`: a consulta até ficar pronta, o filtro das pastas que sumiram, aceitar, ignorar, aceitar todas e o erro do servidor.

## Como foi verificado
- `:core:model:test`, `:core:data:testDebugUnitTest`, `:feature:chat:testDebugUnitTest` e `assembleDebug`.
- **Servidor de dev (IA desligada):** `POST /pastas/sugerir` responde 400 "IA não configurada…", e o app não mostra a seção, nem com pasta.
- **No emulador (A, na direta com o C), com a resposta do servidor simulada:**
  - um proxy de teste local, na frente do proxy de dev, respondeu `ia: true` nos recursos e simulou o pedido e a consulta, com mensagens e pastas reais da conversa; os parâmetros do servidor não mudaram;
  - "Sugerir pastas" mostrou "Analisando…", e a sugestão apareceu depois da segunda consulta;
  - ✓ pôs a mensagem na pasta "Notas" (de 0 para 1);
  - ✕ tirou a sugestão, e o pedido seguinte levou `ignorar: [1205]`;
  - com 3 sugestões, "Aceitar todas (3)" pôs as três (de 1 para 4).

## Decisões
- **A prévia vai numa linha corrida,** como o web: o servidor manda o texto da mensagem com as quebras.
- **As ignoradas ficam no DataStore,** como no `localStorage` do web: não vão para o servidor e não passam para outro aparelho.

## Pendências
- Falta conferir com um servidor de IA de verdade (o de dev está com a IA desligada).
