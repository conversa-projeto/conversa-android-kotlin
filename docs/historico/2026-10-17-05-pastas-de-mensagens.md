# 2026-10-17 · 05 · Pastas de mensagens na conversa direta

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** código
- **Itens:** `TODO.md` 7.14 (as cinco primeiras linhas); FC-223, FC-318, FC-527; CON-17, MSG-23, MSG-24, ENV-29
- **Branch:** `reescrita`
- **Commits:** `2a58133`

## Contexto
- **O servidor** (`ac710eb`, contrato §10.17) tem pastas na conversa direta. Cada pessoa cria as suas (até 50 por conversa) e pode compartilhar com a outra, que põe e tira mensagens mas não muda a pasta.
- **A pasta só marca:** a mensagem continua no chat, pode estar em várias, e vem com `pastas` (ids) quando está em alguma.
- **O aviso** é o WS 67.
- **O web** (`f351190`) tem:
  - o painel de pastas;
  - as bolinhas nas bolhas;
  - a seleção (arrastar ao lado das bolhas, Ctrl + clique);
  - a pasta aberta por cima do chat.

## O que foi feito
- **Dados:**
  - `core:model` `Pastas.kt`: `Pasta`, os limites, a paleta do web (`CORES_PASTA`), `argbDaPasta` (só `#rrggbb`; o resto usa a cor do tema), a ordem do servidor (nome sem acento nem maiúsculas, depois id) e as marcas da bolha (até três);
  - `pastas` no `MensagemDto`, no modelo e no Room. Na entidade é uma coluna com os ids separados por vírgula; Room v13 com `AutoMigration(12, 13)` e o `13.json`;
  - `core:network`: as 7 rotas no `ConversaApi`. O DELETE de `/pasta/mensagens` leva corpo JSON (`@HTTP(hasBody = true)`, o primeiro do app). DTOs em `PastasDtos.kt`;
  - **WS 67:** `EventoSocket.PastasAtualizadas` (conversa, pasta e, quando há, as mensagens e a ação "incluir"/"tirar"). O `SyncManager` repassa ao repositório e, na reconexão, relê as pastas das conversas já abertas;
  - **`PastasRepositorio`:**
    - as pastas de cada conversa aberta ficam na memória, como no web;
    - pôr, tirar, excluir e o WS 67 mudam as marcas no Room na hora, de 500 em 500 (o SQLite do Android 9 aceita até 999 variáveis);
    - a pasta que sumiu sai das marcas.
- **Tela (`PastasViewModel`, `Pastas.kt`):**
  - **"⋮" › "Pastas de mensagens"** (só na direta) abre a folha:
    - a lista com a pasta na cor dela, o ícone de compartilhada ("Compartilhada com/por …") e a contagem;
    - "Nova pasta" com o campo já em foco (até 60);
    - para o dono, o "⋮" com "Renomear", "Compartilhar com …"/"Deixar de compartilhar", as 8 cores e "Excluir", com a confirmação do web;
  - **seleção:** toque longo › "Selecionar" (só mensagens salvas). Com a seleção ativa, tocar numa mensagem soma ou tira. A barra "N selecionadas" tem "Pôr na pasta" (a lista das pastas) e "Limpar seleção"; voltar também limpa;
  - **marcas na bolha** (`Rodape`): na minha, até três bolinhas atrás do ✓, sem mudar a altura; na recebida, antes da hora, e tocar abre a pasta;
  - **pasta aberta** em tela cheia por cima do chat:
    - "Voltar ao chat", o nome e "N mensagens · compartilhada";
    - só as mensagens dela, com os dias, 50 por vez (rolar para cima traz as anteriores);
    - o toque longo abre "Ver na conversa", "Responder", "Selecionar" e "Tirar da pasta", e as selecionadas saem de uma vez;
    - relê quando a pasta ganha ou perde mensagens e fecha se a pasta sumir;
  - **falhas:** os títulos do web com o motivo do servidor ("Não foi possível criar a pasta: …").
- **Cores:** a cor da pasta é dado do servidor. O app oferece a paleta do web para a pasta ficar igual nos dois clientes, registrada como PROPOSTA em `docs/design/cores.md` §6.5, com a pergunta 14.
- **Testes:**
  - `PastasTest`: cor, ordem, marcas e pôr/tirar;
  - `PastasRepositorioTest`: carregar e limpar marcas, criar/alterar/excluir, pôr de 500 em 500, tirar e o aviso 67;
  - `PastasViewModelTest`: só a direta, seleção, falha com motivo, pasta aberta com páginas, tirar, reler e fechar;
  - também o parser do WS 67, o `SyncManagerTest` e a `MigracoesTest` (v13).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só os avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`.
- **No emulador (A, na direta com o C), contra o servidor de dev:**
  - **criar e mudar:** criada a pasta "Contratos", depois a cor âmbar e o compartilhamento com o C. A API confirma `cor: "#f59e0b"` e `compartilhada: true`;
  - **pôr na pasta:** toque longo › "Selecionar" em duas mensagens, a barra "2 selecionadas" e "Pôr na pasta" › "Contratos". As bolinhas âmbar apareceram (antes da hora na recebida, atrás do ✓✓ na minha), e a seleção acabou;
  - **WS 67:** pela API, o C pôs a mensagem 1198 na pasta compartilhada, e a bolinha apareceu na hora no app;
  - **pasta aberta:** tocar na bolinha da recebida abriu "Contratos · 3 mensagens · compartilhada". O toque longo › "Tirar da pasta" deixou 2 (conferido pela API), e "Ver na conversa" fechou a pasta e destacou a mensagem no chat;
  - **excluir:** a confirmação "A pasta “Contratos” sai daqui e de Teste Android C. …" e as marcas sumiram;
  - a pasta "Viagem", criada para conferir o foco automático, foi apagada pela API.
- **Corrigido no teste:**
  - as aspas retas dos textos sumiam (o Android as tira do `strings.xml`): viraram “ ”;
  - o campo do nome não pegava o foco;
  - o vigia "a pasta sumiu" fechava a pasta aberta antes de a lista carregar.
- **Não deu para conferir no web,** que estava fora do ar: fica a linha **Teste** do 7.14. O WS 67 foi conferido nos dois sentidos pela API, com o app de um lado.

## Decisões
- **Seleção pelo toque longo › "Selecionar":** no celular não há arraste ao lado das bolhas nem Ctrl + clique.
- **Pastas na memória e marcas no Room:** as pastas são relidas ao abrir a conversa, no WS 67 e na reconexão. As marcas acompanham as mensagens (o servidor manda as dele a cada leitura).
- **Paleta do web para escolher a cor** (proposta, pergunta 14 do `cores.md`).
- **Menu da pasta aberta reduzido** ("Ver na conversa", "Responder", "Selecionar", "Tirar da pasta"). O web mantém ali também reagir, encaminhar e copiar.

## Pendências
- A linha **Teste** do 7.14, com o web no ar.
