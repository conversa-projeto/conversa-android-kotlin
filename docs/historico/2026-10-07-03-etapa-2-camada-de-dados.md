# 2026-10-07 · 03 · Etapa 2 — camada de dados (sessão, conversas, contatos, presença) e OpenAPI

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2); também Fundação (1.9, OpenAPI)
- **Tipo:** código / testes / documentação
- **Itens:** `TODO.md` 1.9 (OpenAPI), base de 2.1–2.4 e 2.6–2.11 (FC-200…FC-214); AUT-01…05, CON-01…13, PRE-01, GER-02
- **Branch:** `reescrita`
- **Commits:** _(preencher)_

## Contexto
Primeiro bloco da etapa 2: tudo o que as telas vão usar, sem as telas ainda. O servidor de dev foi ligado pelo usuário nesta sessão, o que destravou o download do OpenAPI.

## O que foi feito

### OpenAPI (1.9)
- `docs/contrato/openapi.json` baixado de `https://localhost/api/docs/json` (servidor `8031fa5`).
- Conferência: 68 operações = as **67 rotas REST da `ConversaApi`** (método e caminho iguais, uma a uma) + `/ws/`. Nenhuma rota faltando ou sobrando.

### Regras puras (`:core:model`)
- `Texto.kt`: porte de `resumirTexto` / `parseCodeBlocks` do web (menção `@[Nome](id)` → `@Nome`; bloco de código → "Código (linguagem)", com bloco interno e regra de Markdown).
- `ListaConversas.kt`: `previaDaConversa`, `rotuloData` (hoje HH:mm / Ontem / dd/MM/aa), `ordenarConversas` (fixadas por `fixada_ordem`, depois `mensagem_id` desc), filtros sem acento (`conversaCombina`, `contatoCombina`), `diretaCom`, `avatarDoContato`, cálculo da nova ordem das fixadas (fixar no fim, desafixar, mover).

### Camada de dados (`:core:data`)
- `autenticacao/AutenticacaoRepositorio`:
  - `entrar` (login aparado, reenvia o `dispositivo_id` salvo, salva sessão e id do dispositivo, guarda o último login digitado; sem token → `RespostaLoginInvalidaException`);
  - `cadastrar` (e-mail repetido, que hoje é 500 do Postgres, vira `EmailJaCadastradoException`);
  - `registrarDispositivo` (`PATCH /dispositivo` com textos cortados em 50/50/15 e `plataforma = "android"`);
  - `sair` (limpa o `token_fcm` com 3 s de limite e encerra a sessão mesmo sem rede).
  - `InfoDispositivoAndroid`: fabricante + modelo (não usa o nome que a pessoa deu ao aparelho, que pode ter o nome dela).
- `conversas/ConversasRepositorio`: Room como fonte; atualizar; fixar/desafixar/mover e arquivar **otimistas** (erro → recarrega do servidor); obter ou criar direta; criar grupo (inclui o criador, sem duplicar, em paralelo); membros, renomear, adicionar, sair.
- `contatos/ContatosRepositorio`: `GET /usuario/contatos` → Room; observa todos menos o próprio usuário.
- `presenca/PresencaRepositorio`: online (`/contatos/online` + WS 60) e digitando/gravando (WS 4/5, expira em 4 s como o web, some quando chega mensagem). Saiu do `SyncManager`.
- `sessao/CicloSessao.kt`:
  - `IniciadorSessao` (AUT-03): ao entrar, registra o dispositivo e carrega conversas e contatos; erro que não é 401 mantém a sessão, aparece em `falha` e tenta de novo a cada 5 s.
  - `LimpezaSessao`: no fim da sessão limpa o Room, a presença e os contadores.
- `ConexaoTempoReal`: sem sessão desliga o socket **na hora** (antes esperava 10 s); `ligada`, `semTempoReal` (faixa depois de 5 s, GER-02), `tentarAgora()` e atualização periódica de 8 s enquanto o socket está fora (como o web).
- `SyncManager`: usa o `PresencaRepositorio`; `atualizacaoPeriodica()` e `limpar()`.
- DAOs: `ConversaDao.aplicarFixadas`, `marcarArquivada` (arquivar desfixa e reordena), `remover`, `todas`; `ContatoDao.limpar`.
- DTO: `LoginResposta.token` passou a ter padrão `""`, para detectar a resposta sem token.
- `PreferenciasStore.ultimoLogin`; `Clock` e `InfoDispositivo` injetáveis (`DadosModulo`).
- `:core:testing`: `escopoDoTeste()` compartilhado.

## Como foi verificado
- `gradlew :core:model:test :core:data:testDebugUnitTest :core:network:testDebugUnitTest` → tudo passando.
  - Novos: `TextoTest` (12), `ListaConversasTest` (10), `AutenticacaoRepositorioTest` (9), `ConversasRepositorioTest` (9, Room real em memória com Robolectric), `PresencaRepositorioTest` (3), `IniciadorSessaoTest` (3).
  - `SyncManagerTest` ajustado para a presença separada.

## Decisões
- **Remover outra pessoa do grupo não será oferecido:** o servidor só aceita remover o próprio vínculo (`validarRemocaoConversaUsuario`, contrato §11.4); o web mostra o botão, mas ele sempre dá 403. No Android fica só "Sair do grupo". Volta quando o servidor tiver administrador de grupo.
- **Prévia vazia com mensagem** (arquivo, áudio, chamada): o web mostra "Sem mensagens"; aqui vira um texto próprio (`PreviaConversa.SemTexto`).
- **Logout e 401 limpam o Room inteiro**: outro usuário pode entrar no mesmo aparelho.
- **Dispositivo registrado a cada início de sessão** (não só no login): mantém a versão do Android atualizada e cobre um `PATCH` que falhou.

## Pendências
- As telas (login, cadastro, lista, contatos, grupos, membros, navegação) vêm nos próximos blocos.
- Encerrar a chamada ativa no logout: etapa 6. Cancelar notificações e limpar o cache de imagens no logout: no `:app`, no bloco das telas.
