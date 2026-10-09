# 2026-10-08 · 34 · Etapa 7 — votação: bolha, votar, encerrar, data final e criar

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade
- **Itens:** `TODO.md` 7.12 (o resto do bloco, incluindo as linhas 🆕 `785bdef` e `eaa8bac`); FC-516, FC-517; MSG-16, MSG-20, ENV-22
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A base de dados da votação entrou em [30](2026-10-08-30-etapa-7-enquetes-base.md): DTO, rotas novas, `EnquetesRepositorio` com cache e WS 62. Faltavam:
- a bolha: até aqui, um "📊 Votação / Abra no computador para votar";
- criar a votação;
- o resumo da votação na citação, na oculta e na prévia.

## O que foi feito
- **`core:model` — `Enquetes.kt`:**
  - `idDaEnquete`, `porcentagemDaOpcao` (sobre quem votou, arredondada);
  - `votosAoTocar` (única troca ou tira; múltipla marca ou desmarca);
  - `vencedoras` (só encerrada, empates entram, ninguém votou = nenhuma), `momentoDoEncerramento`;
  - `validarPrazoEnquete` (pelo menos 1 minuto, no máximo 1 ano), `sugestaoPrazoEnquete` (amanhã, na próxima hora cheia);
  - `opcoesPreenchidas`, `podeCriarEnquete` e os limites (12 opções, 300/200 caracteres).
- **`core:model` — `ListaConversas.kt`:** a prévia `"enquete"` vira "📊 Votação" (`PreviaConversa.Votacao`).
- **`feature:chat` — `BolhaEnquete.kt`**, como o `BolhaEnquete.vue`:
  - **Bolha:**
    - "Carregando votação…" e o erro;
    - "📊 pergunta"; "Escolha uma opção" ou "Escolha uma ou mais opções", com "· encerra hoje 18:00" se tem data final;
    - cada opção com círculo ou quadrado e ✓, contagem, barra e quem votou;
    - "N pessoas votaram" e o rodapé.
  - **Votar:** desabilitado durante o voto; o erro do servidor ou "Não foi possível votar".
  - **Encerrada** (à mão ou prazo vencido, inclusive com a bolha aberta, na hora exata): "🔒 Votação encerrada …", sem votar, 🏆 e negrito nas mais votadas.
  - **Quem criou:** "Definir data final" / "Alterar data final" abre "Data final" com "Tirar data" (se tem), "Cancelar" e "Salvar".
  - **Quem criou a votação ou o grupo:** "Encerrar votação", com a confirmação do web.
  - **`ResumoEnquete`** (`EnqueteResumo.vue`): só leitura, na citação e na votação oculta revelada.
  - **`AcoesEnquete`:** a bolha fala com o repositório por essa interface; o `ChatViewModel` a implementa e põe a mensagem amigável do erro.
- **`feature:chat` — `NovaVotacao.kt`**, como o `EnqueteModal.vue`, aberta pelo "+" → "Votação", só em grupo:
  - pergunta, com foco ao abrir;
  - 2 a 12 opções, com "+ Adicionar opção" e "Remover opção N";
  - "Permitir várias escolhas" e "Definir data final" (com os erros do web);
  - "Cancelar" e "Criar votação" ("Criando…"); o erro do servidor aparece na folha.
  - Depois de criar, o `ChatViewModel.criarVotacao` relê as mensagens e a lista e desce ao fim, como o web.
- **`StatusEAgendamento.kt`:** o seletor de data e hora virou `CamposDataHora`, usado pelo agendamento, pela data final na bolha e pela "Nova votação".
- **Testes:**
  - `EnquetesTest` (7 casos);
  - `ListaConversasTest`: "enquete" vira Votação;
  - `ChatViewModelTest`: a falha traz a mensagem do servidor; criar relê mensagens e lista e devolve o erro.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- **Emulador (A no "Grupo criado pelo B").** Pela API: uma votação do B, de escolha única, com data final em 4 minutos; uma do A, múltipla.
  - **Votos:** votei em "Centro"; o voto do B em "Shopping", pela API, chegou sem recarregar (WS 62), com "2 pessoas votaram" e os nomes. Na múltipla, "Segunda" e "Sexta" ficaram marcadas, e a contagem foi "1 pessoa votou".
  - **Data final:** "Definir data final" → "Salvar" mostrou "· encerra amanhã 03:00", e o botão virou "Alterar data final"; "Tirar data" voltou ao texto sem data.
  - **Encerrar:** "Encerrar votação" abriu a confirmação; "Encerrar" mostrou "🔒 Votação encerrada hoje 02:50", com 🏆 em "Segunda" e "Sexta" (empate) e sem os botões.
  - **Prazo vencendo na tela:** a votação do B se encerrou sozinha às 02:52, com 🏆 em "Centro" e "Shopping".
  - **Criar no celular:** "+" → "Votação" → "Cafe" com "Sim" e "Nao" → "Criar votação". A bolha apareceu no fim; o voto do B, pela API, atualizou a barra na hora.
  - **Oculta:** ocultei a "Cafe"; ao tocar em "Mensagem oculta", apareceu o resumo ("📊 Cafe", "Sim 1", "Nao 0").

## Decisões
- **Data final num diálogo**, e não dentro da bolha como no web: mais espaço para o calendário e o relógio no celular.
- **"Votar no web" do teste foi feito pela API como o B:** é a mesma rota (`POST /enquete/votar`) e o mesmo WS 62.
- **A bolha usa as cores das bolhas do FMX:** o círculo, o quadrado e a barra usam a primária (o fundo da barra é a primária com transparência), sem cor nova.

## Pendências
- Atividades (etapa 8): quando a tela existir, a atividade de uma votação usa o mesmo resumo.
- **Etapa 7:** falta só o 7.9, com o Markdown renderizado e o mermaid (P2).
