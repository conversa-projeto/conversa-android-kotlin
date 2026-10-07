# Histórico de alterações — índice por fluxo

> Uma linha por alteração, na seção do fluxo, em ordem cronológica (mais recente embaixo). O detalhe de cada uma está no arquivo do link.
> Regras de registro: `CLAUDE.md`, seção "Regra obrigatória: histórico de alterações". Modelo: [`_modelo.md`](_modelo.md).
>
> Formato: `- AAAA-MM-DD · [Título](arquivo.md) — resumo`

---

## Planejamento e documentação
- 2026-10-06 · [Auditoria inicial do Android contra o servidor e o web](2026-10-06-01-auditoria-inicial.md) — docs 00–08; decisão de recomeçar; 132 funcionalidades, 57 problemas, Q1–Q12, S1–S14
- 2026-10-06 · [TODO literal, cronograma removido e correção sobre o OpenAPI](2026-10-06-02-todo-detalhado.md) — `TODO.md` com 586 passos; sem datas; o OpenAPI já existe (faltam as respostas)
- 2026-10-06 · [Criação do histórico de alterações e do CLAUDE.md](2026-10-06-04-historico-e-claude-md.md) — `docs/historico/` por fluxo + regra obrigatória no `CLAUDE.md`
- 2026-10-07 · [Cores do app vêm do conversa-windows-fmx](2026-10-07-01-cores-do-fmx.md) — `docs/design/cores.md`; primária `#007DFF`; escuro é proposta e fica desligado

## Sincronização com servidor e web
- 2026-10-06 · [Commits da noite: votação, campo rico, chat completo na chamada](2026-10-06-03-sincronizacao-votacao-campo-rico.md) — servidor `8031fa5` (enquete, WS 62, tipo 8) e web `39d06f9`; 136 funcionalidades; novos FC-315/415/416/516–518/723

## Repositório e build
- 2026-10-06 · [Etapa 0 — preservação, limpeza e organização do repositório](2026-10-06-05-etapa-0-repositorio.md) — branch/tag legado, 11 stashes em tags e patches, limpeza, wrapper versionado, docs em `docs/legado/`, ADR 0001, README; push pendente

## Fundação (etapa 1)
- 2026-10-07 · [Etapa 1 — fundação do app novo](2026-10-07-02-etapa-1-fundacao.md) — 8 módulos, rede (67 rotas), WebSocket, Room, sessão cifrada, sync, design system, tela Servidor; 65 testes passando

## Sessão, conversas, contatos e presença (etapa 2)
- 2026-10-07 · [Etapa 2 — camada de dados e OpenAPI](2026-10-07-03-etapa-2-camada-de-dados.md) — login/cadastro/dispositivo/sair, conversas (fixar, arquivar, direta, grupo), contatos, presença, início resiliente, limpeza; OpenAPI = 67 rotas
- 2026-10-07 · [Etapa 2 — login, cadastro, navegação e saída](2026-10-07-04-etapa-2-login-cadastro-navegacao.md) — telas de login e cadastro, rotas tipadas, barra inferior, links `conversa://chat`, sair com limpeza; 124 testes
- 2026-10-07 · [Etapa 2 — conversas, grupos e teste de ponta a ponta no emulador](2026-10-07-05-etapa-2-conversas-e-teste-no-emulador.md) — `:feature:conversas` (lista, nova conversa, grupos, membros), 6 correções achadas no emulador, S15/S16; 137 testes

## Mensagens (etapa 3)
- 2026-10-07 · [Etapa 3 — regras do chat, carregar, ler e enviar](2026-10-07-06-etapa-3-regras-e-dados.md) — classificação e links do web, paginação, fila de leitura, envio com WorkManager; status do WS 3 só nas minhas; 159 testes

## Anexos e mídia (etapa 4)
_(nada ainda)_

## Notificações e push (etapa 5)
_(nada ainda)_

## Chamadas (etapa 6)
_(nada ainda)_

## Ações sobre mensagens (etapa 7)
_(nada ainda)_

## Atividades, pesquisa, perfil e configurações (etapa 8)
_(nada ainda)_

## Extras — SIP, compartilhar tela (etapa 9)
_(nada ainda)_

## Qualidade, segurança e publicação
_(nada ainda)_

## App legado (hotfixes opcionais)
_(nada ainda)_
