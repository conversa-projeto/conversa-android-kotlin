# 2026-10-06 · 02 · TODO literal, cronograma removido e correção sobre o OpenAPI

- **Fluxo:** Planejamento e documentação
- **Tipo:** documentação
- **Referências:** `TODO.md`, `docs/auditoria-2026-10/07`, `docs/auditoria-2026-10/08`

## O que foi feito
- Criado `09-cronograma.md` com datas e, a pedido, **apagado**: o projeto não vai se prender a datas. A referência no `00-LEIAME.md` também foi removida.
- Criado o `TODO.md` na raiz: lista literal de pendências, de 5 min a 2 h por linha, com comandos, endpoints e textos exatos.
  - Organizado em etapas 0–9, mais "Qualidade", "Opcional — hotfixes do app antigo" e "Revisão periódica".
  - Linhas **Teste:** fecham cada seção.
  - Total na criação: 586 itens em 90 seções.
- **Correção:** o servidor **já publica OpenAPI** (`/api/docs`, JSON em `/api/docs/json`, `src/app.ts:49-55`). O que falta são os esquemas de **resposta**. Atualizados:
  - `08` (S12);
  - `07` (D8 e riscos);
  - o `TODO` (passo para baixar o OpenAPI na 1.9).

## Decisões
- Sem cronograma com datas; o andamento é acompanhado pelo `TODO.md` e por este histórico.
