# 2026-10-15 · 22 · Rótulos das setas do mermaid sem a caixa preta

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** correção
- **Itens:** `TODO.md` 7.9 (linha nova abaixo da do mermaid); MSG-15
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O defeito:** num fluxograma, os rótulos das setas ("Sim", "Não") apareciam numa caixa preta, quase ilegíveis.
- **A causa:** as cores vão ao mermaid como `#RRGGBB` (`coresDoMermaid`), e a conversão descartava a transparência. O `divisorLista`, que é o `secondaryColor` do diagrama, é preto translúcido no tema claro (`0x32000000`) e virava `#000000`. O tema `base` do mermaid usa o `secondaryColor` como fundo dos rótulos das setas.
- **No tema escuro,** o `0x33FFFFFF` virava branco puro, com o texto claro em cima.

## O que foi feito
- **`corHex(cor, fundo)`** (`DiagramaMermaid.kt`): a cor como aparece sobre o fundo do diagrama (`compositeOver` do `surface`) antes de virar hex. Todas as cores do `themeVariables` passam por ela.
- **Teste** (`DiagramaMermaidTest`): o `divisorLista` claro sobre branco vira `#CDCDCD`, o escuro sobre preto vira `#333333`, e a cor opaca fica igual.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (517 testes), `:app:lintDebug` (só os 3 avisos de sempre) e `ktlintCheck`. Passou.
- **No emulador (A), tema claro:** o fluxograma do "Grupo criado pelo B" (mensagem 1148) mostrou "Sim" e "Não" em cinza claro, legíveis. Antes, na mesma mensagem, estavam em caixas pretas.
- **O tema escuro** não foi visto no emulador (seria preciso mudar a configuração do sistema). O caso está no teste.

## Decisões
- **Nenhuma cor nova:** continua o `divisorLista`, agora como ele aparece na tela.
