# 2026-10-08 · 30 · Etapa 7 — votação: contrato novo, repositório e WS 62

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** funcionalidade (camada de dados)
- **Itens:** `TODO.md` 7.12, 3 linhas (repositório, WS 62 e o DTO 🆕 `5cad911`); FC-516
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
O 7.12 (votação em grupo) é o maior bloco da etapa 7. Esta é a primeira parte: a base que a bolha e o "Nova votação" vão usar.
- O `:core:network` já tinha `GET /enquete`, `PUT /enquete` e `POST /enquete/votar`.
- Faltavam:
  - os campos da data final e do encerramento (servidor `5cad911`);
  - as duas rotas novas, `POST /enquete/encerrar` e `PATCH /enquete`;
  - um lugar que guardasse as enquetes para as bolhas.

## O que foi feito
- **`core:network`:**
  - **`EnqueteDto`:** ganhou `encerra_em`, `encerrada_em`, `encerrada`, `pode_encerrar` e `pode_alterar_prazo`, com padrões para servidor antigo (aberta e sem permissões).
  - **`CriarEnqueteRequisicao`:** ganhou `encerra_em`.
  - **Requisições novas:** `EncerrarEnqueteRequisicao` e `PrazoEnqueteRequisicao`. A segunda leva `encerra_em` como `JsonElement`: para tirar a data vai `null` explícito (`nuloExplicito`), porque o `ConversaJson` omite os nulos.
  - **`ConversaApi`:** `encerrarEnquete` e `alterarPrazoEnquete`.
- **`core:model` — `Enquete`:**
  - os mesmos campos;
  - `fechada(agora)`: encerrada, ou `encerra_em <= agora`. O servidor não avisa quando o prazo passa.
- **`core:data` — `EnquetesRepositorio`:**
  - cache em memória por id, com `observar(id)`;
  - `carregar(id)` deduplica leituras simultâneas no escopo da aplicação;
  - `votar`, `encerrar` e `alterarPrazo` substituem a enquete do cache;
  - `criar` manda as opções com trim e sem as vazias;
  - `aoAtualizar(id)` relê a enquete só se ela já está em cache.
- **`SyncManager`:** o WS 62 chama `enquetes.aoAtualizar`.
- **Testes:**
  - `DesserializacaoTest`: os campos novos da fixture, o servidor antigo e o `encerra_em: null` explícito;
  - `EnquetesRepositorioTest`: dedup, votar e WS 62, e tirar a data.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- Sem teste no emulador: ainda não há tela que use o repositório.

## Pendências
- **7.12:** a bolha (ler, votar, encerrada, 🏆), criar ("Nova votação" com data final), data final e encerrar pela bolha, o resumo "Votação" e a votação oculta revelada.
