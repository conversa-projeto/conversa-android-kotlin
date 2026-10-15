# 2026-10-15 · 02 · "Chamar de novo" volta a tocar no Android

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** correção
- **Itens:** `TODO.md` 8.7 (3ª linha); FC-724; CHA-25; contrato §9.14
- **Branch:** `reescrita`
- **Commits:** `936c3ee`

## Contexto
O servidor `b8ea0c2` criou o `POST /chamada/chamar-novamente`. Ele faz o convidado que recusou, ou que deixou passar, voltar a pendente, e manda de novo o WS 51 para ele.

O `GerenciadorChamadas` ignorava qualquer 51 de uma chamada já "encerrada" no aparelho (`if (id in encerradas) return`). Isso protege contra o 51 duplicado ou atrasado, mas também calava o "Chamar de novo" do web.

## O que foi feito
- **`ParticipanteChamada.recusouEm`** (`core:model`): o DTO já lia o `recusou_em`, e o mapeador agora o repassa.
- **`GerenciadorChamadas.recebida`:** um 51 de uma chamada encerrada aqui consulta `GET /chamada/dados` (`chamadoDeNovo`). O app só volta a tocar quando as três condições valem:
  - o meu status voltou a pendente;
  - o `recusou_em` está preenchido, o que mostra que fui chamado de novo depois de recusar;
  - a chamada não terminou.
- **O que continua sem tocar:**
  - o 51 repetido da primeira vez;
  - o 51 que chega antes de o servidor processar a minha recusa (nos dois, o `recusou_em` ainda está vazio).
- **`finalizadas`:** as chamadas encerradas pelo servidor (WS 52) ficam guardadas à parte. Para elas, o 51 atrasado nem consulta o servidor, porque o "Chamar de novo" não reabre uma chamada encerrada.
- **Testes (`GerenciadorChamadasTest`, +3):**
  - recusar e depois "Chamar de novo" toca de novo;
  - o 51 repetido sem recusa registrada não toca;
  - "Chamar de novo" numa chamada que já acabou não toca.
  - Os 68 testes da classe passam. O antigo "51 depois do 52 não toca" continua sem nenhuma consulta ao servidor.

## Como foi verificado
- Verificação completa: `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` e `ktlintCheck`. O lint só mostra avisos que já existiam.
- O teste no emulador contra o web (grupo A, B e C: o A recusa e o B chama de novo) fica na linha **Teste** do 8.7.

## Decisões
- **Usar o `recusou_em` como sinal**, e não o tempo desde a recusa. É o servidor que diz se houve um "Chamar de novo", e isso não depende do relógio do aparelho.

## Pendências
- Pelas pendentes (`/chamadas/pendentes`, ao reconectar), uma chamada chamada de novo continua sendo pulada. A pendente traz o `criado_em` original e cairia na regra dos 25 s como "perdida". O caminho do WS 51 é o que importa com o app aberto.
