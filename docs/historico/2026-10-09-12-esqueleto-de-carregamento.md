# 2026-10-09 · 12 · Esqueleto de carregamento no Markdown e no diagrama

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.9 (linha nova no fim da seção); MSG-15; `docs/design/cores.md` §6.4
- **Branch:** `reescrita`
- **Commits:** `ec69ba8`

## Contexto
O Markdown (` ```md `) e o diagrama (` ```mermaid `) demoram um instante para aparecer, e até lá o bloco ficava vazio. Foi pedido o efeito de carregamento conhecido como "shimmer": formas cinza que pulsam na opacidade, com um brilho passando.

## O que foi feito
- **`core/ui/.../componentes/Esqueleto.kt` (novo): `EsqueletoCarregando`.**
  - Desenha formas arredondadas no lugar do conteúdo: linhas de larguras variadas, como um parágrafo, ou um bloco só.
  - O conjunto pulsa entre opacidade 1 e 0,55 (700 ms).
  - Uma faixa de brilho inclinada atravessa da esquerda para a direita (1,2 s). Ela pinta só por cima das formas: camada fora da tela com `BlendMode.SrcAtop`.
  - As cores são só tokens (`cores.md` §6.4, linhas novas): formas em `divisorLista`; o brilho é `surface` no tema claro e `onSurface` fraco no escuro.
  - Para o TalkBack, o esqueleto é "Carregando…" (texto novo no `core:ui`, igual ao das Atividades).
- **`feature/chat/.../BlocoCodigo.kt`:** o slot `loading` do `Markdown` mostra as linhas do esqueleto enquanto a biblioteca interpreta o texto, o que acontece fora da thread principal.
- **`feature/chat/.../DiagramaMermaid.kt`:** um bloco de 120 dp com o brilho fica no lugar do diagrama até a página avisar a altura (`Conversa.altura`). Isso vale também para os diagramas dentro de um ` ```md `. O WebView continua carregando por baixo, com 1 dp de altura.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Tudo passou, e o lint só mostra avisos que já existiam.
- **No emulador:** a conta A abriu o "Grupo criado pelo B" com um ` ```md ` que traz um diagrama de sequência.
  - Logo após abrir, o bloco cinza aparecia com o brilho na diagonal.
  - O diagrama levou cerca de 4 s e entrou no lugar do bloco.
  - O ` ```mermaid ` inválido continuou virando código.
- **No aparelho:** instalado no Galaxy A25 para a pessoa ver. Não foi testado com toques, porque o aparelho estava em uso.

## Decisões
- **Um componente no `core:ui`**, e não um modificador em cada tela, para servir a outros carregamentos depois (lista, imagens).
- **Bloco de 120 dp no diagrama:** é uma altura média de um fluxograma pequeno, para o salto ao desenhar ser menor. A altura real só se conhece depois de desenhar.

## Pendências
- Se a pessoa quiser, usar o mesmo esqueleto em outros carregamentos (lista de conversas, Atividades, PDF).
