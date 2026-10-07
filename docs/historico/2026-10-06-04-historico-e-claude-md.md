# 2026-10-06 · 04 · Criação do histórico de alterações e do CLAUDE.md

- **Fluxo:** Planejamento e documentação
- **Tipo:** documentação / processo
- **Commits:** `87ba4b5` (registrado retroativamente)

## O que foi feito
- Criada a pasta `docs/historico/`:
  - `indice.md`: índice **separado por fluxo**, com uma linha por alteração apontando para o detalhe;
  - `AAAA-MM-DD-NN-assunto.md`: o detalhamento de cada alteração;
  - `_modelo.md`: modelo para novas entradas.
- Registradas retroativamente as alterações do dia (01–03).
- Criado o `CLAUDE.md` na raiz do projeto: o que é o projeto, onde está a documentação, como trabalhar com o `TODO.md` e a **regra obrigatória** de registrar toda alteração no histórico.

## Decisões
- Toda alteração (código, documentação, configuração, decisão) gera uma entrada no histórico **no mesmo commit** da alteração.
