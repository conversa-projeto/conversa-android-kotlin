# 2026-10-31 · 03 · Lista de conversas e de chamadas: a hora sempre alinhada à direita

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2); também Chamadas (etapa 6), pelo histórico de chamadas
- **Tipo:** correção
- **Itens:** `TODO.md` 2.6 (lista de conversas, CON-01) e o histórico de chamadas (6.x); linha nova da correção
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
A pessoa notou no celular (Galaxy A25) que, na lista de conversas, algumas datas e horas ficavam desalinhadas. No print, o "15:48" do "Teste Android C" e o "15/10/26" do "Aniversario" terminavam mais à esquerda que as outras datas. Os nomes longos também cortavam cedo, com espaço sobrando ("Grupo de te…", "Grupo criad…").

**Causa:** na linha do título, o nome tinha `weight(1f, fill = false)` e, depois dos ícones, um espaçador `Box(Modifier.weight(1f))`. Com dois filhos de peso igual, o `Row` dá a cada um metade do espaço que sobra.
- Nome curto: ele não usa toda a metade dele, e a sobra não passa para o espaçador. A hora para antes da borda, e quanto antes depende do tamanho do nome.
- Nome longo: ele fica limitado à metade e corta, mesmo com espaço livre.

O histórico de chamadas (`HistoricoChamadas.kt`) tinha o mesmo arranjo (nome com `fill = false` e `Spacer(Modifier.weight(1f))`).

## O que foi feito
- **`feature/conversas/.../lista/ConversasTela.kt` (`LinhaConversa`):**
  - o nome e os ícones (grupo, "Arquivada", alfinete) ficam num `Row` interno com `weight(1f)`, que ocupa todo o espaço até a hora;
  - dentro dele, o nome continua com `weight(1f, fill = false)`: encolhe ao tamanho do texto e os ícones ficam colados nele;
  - a hora sai do `Row` interno, com 8 dp de folga à esquerda, e fica sempre na borda direita.
- **`feature/chamada/.../HistoricoChamadas.kt`:** o nome com `weight(1f)`, sem o espaçador; a hora com 8 dp de folga, sempre na borda.

## Como foi verificado
- `:feature:conversas:testDebugUnitTest`, `:feature:chamada:testDebugUnitTest` e `ktlintCheck` dos dois módulos.
- **No celular** (`adb install -r`, conta B no servidor de dev):
  - lista de conversas: as cinco datas e horas terminam na mesma coluna;
  - os nomes aparecem inteiros ("Grupo de teste 2", "Grupo criado pelo B"), com o ícone de grupo logo depois;
  - histórico de chamadas: as horas na mesma coluna, antes do botão de ligar.

## Decisões
- O espaçador saiu em vez de virar `fill = true` no nome: com o nome preenchendo o espaço, os ícones de grupo e alfinete iriam para perto da hora, longe do nome (o web e o FMX deixam o ícone colado no nome).
