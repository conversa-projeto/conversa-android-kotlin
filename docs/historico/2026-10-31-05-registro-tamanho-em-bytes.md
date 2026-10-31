# 2026-10-31 · 05 · Registro do app: o tamanho em bytes abaixo de 1 kB

- **Fluxo:** Qualidade, segurança e publicação
- **Tipo:** correção
- **Itens:** `TODO.md`, Qualidade (tela "Registro do app"); continua o histórico `2026-10-31-04`
- **Branch:** `reescrita`
- **Commits:** `88a2327`

## Contexto
No teste do Apagar no celular (histórico `2026-10-31-04`), o arquivo ficou com uma linha (121 bytes), mas a tela dizia "0 kB": o `tamanhoLegivel` arredondava tudo abaixo de 1 MB para kB sem casas. Parecia que o registro tinha parado de gravar. A pessoa pediu para mostrar em bytes abaixo de 1 kB.

## O que foi feito
- **`feature/config/.../RegistroTela.kt`:**
  - `tamanhoLegivel` mostra "121 B" abaixo de 1024 bytes, como o `tamanho` do `RegistroHttp`;
  - de 1 kB a 1 MB continua "55 kB", e acima "4,1 MB";
  - a função passou de `private` para `internal`, para o teste.
- **`feature/config/src/test/.../RegistroTelaTest.kt` (novo):** os limites do `tamanhoLegivel` (0, 121, 1023 e 1024 bytes, kB e MB) e o `momentoCurto`.

## Como foi verificado
- `:feature:config:testDebugUnitTest`, `ktlintCheck`, `:app:assembleDebug` e `:app:lintDebug`.
- **No celular** (Galaxy A25, `adb install -r`, conta B no servidor de dev; a depuração por Wi-Fi tinha caído e foi reconectada pela pessoa):
  - antes do Apagar, "Desde 31/10/2026 · 110 kB" (112 607 bytes);
  - depois do Apagar, "Desde 31/10/2026 · 121 B", e o arquivo no aparelho tinha mesmo 121 bytes, com a linha "Registro apagado pela pessoa, na tela do registro".

## Decisões
- A faixa de kB continua sem casas ("55 kB"): na tela, o tamanho é só uma noção de quanto o registro guarda. O pedido era só o caso abaixo de 1 kB.
