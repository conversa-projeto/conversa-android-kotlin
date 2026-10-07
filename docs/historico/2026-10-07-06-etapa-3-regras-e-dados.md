# 2026-10-07 · 06 · Etapa 3 — regras do chat, carregar, ler e enviar (camada de dados)

- **Fluxo:** Mensagens (etapa 3)
- **Tipo:** código / testes
- **Itens:** `TODO.md` 3.2, 3.3, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10 (só as partes de regra e de dados); MSG-01…14, ENV-01, ENV-15
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
Primeiro bloco da etapa 3: tudo o que a tela de chat vai usar, testado sem tela. A tela vem no próximo bloco.

## O que foi feito

### Regras puras (`core/model/Chat.kt`)
- **`classificarMensagem`**, porte do web com a mesma prioridade: oculta > chamada > imagem > enquete > figurinha > código > emoji > com referência > texto curto (≤ 60) > padrão.
- **`ehSoEmoji`**: por faixas de código, porque o regex de propriedades Unicode do Android (ICU) e o da JVM dos testes têm sintaxes diferentes.
- **`separarLinks` / `separarTexto`**: links `https?://` e `www.`, com as regras do web (pontuação final sai; `)` só sai se não fechar um `(` do link), e menções `@[Nome](id)`.
- **`statusEntrega`**: enviando, falhou, ✓, ✓✓ e lida.
- **`atividadeDaConversa`**: digitando ou gravando; até 3 nomes e "outras N"; "Usuário #id" para quem não é contato; ignora o próprio usuário.
- **`ordenarMensagens` e `montarItensChat`**:
  - separador de dia pela data efetiva (o web usa `inserida`);
  - linha "Últimas";
  - nome do remetente em grupo quando ele muda;
  - otimistas no fim.
- **`rotuloDia`** (Hoje/Ontem/data), **`resumoCitacao`** (escolha do web) e **`formatarDuracao`** (`mm:ss`).
- `Mensagem` ganhou `enviando` e `falhou`.

### Rede
- `ChamadaConteudoDto` + `lerChamadaDaMensagem` para o conteúdo tipo 6. As datas vêm sem fuso; JSON inválido devolve nulo.

### Dados (`core/data/mensagens/`)
- **`MensagensRepositorio`**:
  - abrir traz as 80 mais recentes; cada página traz 60 para trás ou para frente;
  - a mensagem de referência vem repetida na resposta e é descartada; resultado 0 = chegou à ponta;
  - **`marcarLida`**: otimista no Room e no contador da conversa; fila serial com `POST /mensagem/visualizar`, uma vez por mensagem; se falhar, pode pedir de novo.
- **`EnvioMensagens`**:
  - mensagem otimista com id negativo, mais a linha em `envio_pendente`;
  - **`EnvioWorker`** (`@HiltWorker`, só com rede, `APPEND_OR_REPLACE`) manda na ordem;
  - ao dar certo, troca a otimista pela real. Se não conseguir buscar a real, grava a otimista com o id real;
  - recusa 4xx ou 5 erros do servidor marcam `falhou`; sem rede não conta tentativa;
  - `reenviar`, `descartar` e `retomar` (chamado no início da sessão).
- **Logout:** cancela a fila de envio e limpa a fila de leitura.
- **`:app`:** `ConversaApplication` implementa `Configuration.Provider` com o `HiltWorkerFactory`; o manifest tira o inicializador automático do WorkManager.

### Correção de um erro achado lendo o contrato (§10.5)
- `GET /mensagem/status` devolve o status **agregado de todos os destinatários**, mesmo para quem não é o autor.
- O `SyncManager` gravava isso em todas as mensagens do WS 3. Nas mensagens dos outros, isso sobrescrevia o **meu** status com o do grupo.
- Agora o status só vai para as minhas mensagens (`atualizarStatusDaMinha`); "oculta" continua valendo para todas.

## Como foi verificado
- `gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` → BUILD SUCCESSFUL.
- **159 testes, 0 falhas.** Lint: 0 apontamentos.
- Novos:
  - `ChatTest` (14, inclusive todos os casos do `classificarMensagem.test.ts` do web);
  - `MensagensTest` (6, Room real com Robolectric): paginação sem repetir, marcar lida uma vez, envio e troca pela real, sem rede, recusa/reenviar/apagar, 5 tentativas;
  - teste do conteúdo tipo 6 no `DesserializacaoTest`;
  - teste do WS 3 no `SyncManagerTest`;
  - "só as minhas" no `BancoTest`.

## Decisões
- **Dia do separador pela data efetiva**, a mesma da ordenação. No web é pela `inserida`.
- **Envio em ordem e parando no primeiro erro sem rede:** uma mensagem nunca passa à frente da outra.
- **Risco aceito:** se a resposta do `PUT /mensagem` se perder depois de gravada no servidor, a nova tentativa pode duplicar a mensagem (o web tem o mesmo risco). A solução definitiva é uma chave de idempotência no servidor; fica como sugestão junto com a S9.

## Pendências
- A tela (bloco seguinte): bolhas, separadores, "Últimas", botões flutuantes, links, campo, digitando, status, "Reenviar"/"Apagar".
- Testes no emulador: envio sem rede, ✓✓ azul.
