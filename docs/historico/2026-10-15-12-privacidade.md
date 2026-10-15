# 2026-10-15 · 12 · Configurações › Privacidade

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** código
- **Itens:** `TODO.md` 8.5 (linha 🆕 `769bd7d` da Privacidade); FC-810; CFG-09
- **Branch:** `reescrita`
- **Commits:** `ab62486`

## Contexto
A presença nova (bloco 11) tem três chaves no servidor (`769bd7d`): aparecer offline, mostrar o visto por último e mostrar quando estou na conversa. O web as mostra em Configurações › Privacidade (`ConfiguracaoPrivacidade.vue`); o Android não tinha.

## O que foi feito
- **Rede:**
  - `GET /usuario/privacidade` (`PrivacidadeDto`);
  - `mostrar_visto_em`, `mostrar_na_conversa` e `aparecer_offline` no `AlterarUsuarioRequisicao` (`PATCH /usuario`). Vai só a chave que mudou; as nulas não entram no JSON.
- **Dados:** `Privacidade` (`core:model`), `PerfilRepositorio.privacidade` e `alterarPrivacidade`.
- **Tela** (`feature/config/.../PrivacidadeTela.kt`, nova):
  - **Conteúdo:** a seção "Status" com o texto do web e as três chaves com título e descrição, os mesmos textos do web.
  - **Ao mudar:** a chave muda na hora; se o servidor recusar, volta e mostra o erro.
  - **"Aparecer offline":** com ela ligada, as outras duas ficam desabilitadas.
  - **Onde fica:** um item novo nas Configurações, "Privacidade · Status, visto por último e aparecer offline", com a rota `RotaPrivacidade`.
- **Testes:**
  - JSON: o `PATCH` só com a chave; a leitura da privacidade.
  - ViewModel: carrega, muda só a chave tocada, e volta mostrando o erro quando o servidor recusa.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (493 testes), `:app:lintDebug`, `ktlintCheck` e `textos-repetidos.mjs`. Passou.
  - O lint pediu "…" em vez de "..." em "Carregando" (`TypographyEllipsis`); trocado, e o lint voltou aos 3 avisos de sempre.
- **No emulador (A), com o C ouvindo pelo WebSocket:**
  - a tela abriu com as três chaves do servidor;
  - "Aparecer offline" ligada desabilitou as outras duas, e o C recebeu o WS 60 do A como offline;
  - desligada, o C recebeu o A ativo de novo.

## Decisões
- **O texto da seção "Status" é o do web**, inclusive "parado há 5 minutos". Ele descreve como o servidor junta os aparelhos; no celular, o app fica ausente ao sair da tela.

## Pendências
- Nenhuma.
