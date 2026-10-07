# Documentação legada — NÃO usar como referência

> **Desatualizado.** Estes arquivos descrevem o app Android da era do **servidor Delphi** (até abril/2026): áudio de chamada por **TCP 9090/PCM**, `ws://` na porta 9090, endpoints e payloads que **mudaram**. Vários se dizem "COMPLETO E FUNCIONAL" para funcionalidades que têm defeitos (ver `docs/auditoria-2026-10/04-auditoria-android.md`, problema #56).
>
> A referência atual é `docs/auditoria-2026-10/` (contrato do servidor no doc 01).

## Conteúdo

| Caminho | O que é |
|---|---|
| `*.md` (raiz desta pasta) | Notas de implementação e correção das sessões de 2025–2026 (antes em `legado/`) |
| `documentacao-oficial/` | Documentação técnica antiga (antes na raiz do repo) |
| `chamada-tcp/ChamadaManager.kt` | Implementação antiga de áudio de chamada por TCP/PCM (fora do build) |
| `stashes/NN.patch` | Os 11 stashes do Git exportados como patch (sem `.gradle/`, `.idea/` e `build/`). Os originais completos estão nas tags `legado/stash-NN` |
| `commit-md-stash00.md` | Rascunhos de mensagens de commit guardados no `stash@{0}` (abril/2026) |

## Onde está o código legado
- Branch `legado/abril-2026` e tag `legado-v1-final` (commit `bb22aa2`, 2026-10-06), com o app Android completo no estado final antes da nova base.

## Stashes (data e branch de origem)

| Tag | Stash | Origem |
|---|---|---|
| `legado/stash-00` | 20260421 | novo |
| `legado/stash-01` | 20260121_2235 | novo |
| `legado/stash-02` | 20260112_2232 | novo |
| `legado/stash-03` | 20251218_231645 | flamboyant-ritchie |
| `legado/stash-04` | 20251218_2316 | flamboyant-ritchie |
| `legado/stash-05` | 20251123_2117 | novo |
| `legado/stash-06` | 20251123_1936 | novo |
| `legado/stash-07` | 20251108_1909 | novo |
| `legado/stash-08` | 20251024_2038 - old1 | main |
| `legado/stash-09` | 20251024_2011 | novo |
| `legado/stash-10` | 20251022_2139 | novo |

Para recuperar um stash: `git stash apply legado/stash-NN` (ou `git checkout legado/stash-NN -- <arquivo>`).
