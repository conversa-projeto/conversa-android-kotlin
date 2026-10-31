# 2026-10-31 · 06 · O Sigilo no RegexNoAndroidTest, rodado no emulador

- **Fluxo:** Qualidade, segurança e publicação; também Planejamento e documentação (guia do emulador)
- **Tipo:** documentação
- **Itens:** `TODO.md`, Qualidade (a linha de teste do registro do app); pendência do histórico `2026-10-31-04`
- **Branch:** `reescrita`
- **Commits:** `2aa5387`

## Contexto
O `Sigilo` do registro do app (histórico `2026-10-31-04`) esconde segredos com regex e `IGNORE_CASE`. No Android, o regex é o do ICU, e um padrão aceito pela JVM dos testes unitários pode fechar o app. Por isso, as verificações dele entraram no `RegexNoAndroidTest`. No commit do registro, o teste foi só compilado, porque o emulador estava desligado. A pessoa pediu para rodar.

## O que foi feito
- **O teste:** `RegexNoAndroidTest` rodou no emulador `Medium_Phone_API_36.1` (Android 16, API 36): `OK (1 test)`, em 0,09 s.
  - O teste confere o Bearer escondido, `senha` no JSON e a assinatura da URL, e confirma que "Token não informado" fica como está.
  - O celular estava ligado ao adb na mesma hora. O `connectedDebugAndroidTest` rodaria nele também e desinstalaria o app de lá. Por isso os dois APKs foram instalados só no emulador (`adb -s emulator-5554`), e o teste rodou com `am instrument`.
- **O espaço do emulador:**
  - O `install -r` do app falhou por espaço: `INSTALL_FAILED_INSUFFICIENT_STORAGE`, com 7% livre.
  - O `pm trim-caches` liberou só ~8 MB, e o `uninstall -k` não liberou nada (o Android 16 guarda o código junto com os dados).
  - Desinstalar e instalar resolveu. O app do emulador perdeu o endereço do servidor e o login, como já acontece no fim de todo `connectedDebugAndroidTest`.
- **`docs/desenvolvimento/emulador.md`:**
  - como rodar uma classe só no emulador com o celular ligado;
  - o que funcionou e o que não funcionou para instalar com pouco espaço.
- **`TODO.md`:** a linha de teste se dividiu: o `RegexNoAndroidTest` está feito; o zip no servidor do trabalho continua aberto.
- **Histórico `2026-10-31-04`:** a verificação foi atualizada, e a pendência saiu.

## Como foi verificado
- A saída do `am instrument`: `INSTRUMENTATION_STATUS: test=regrasDeTextoFuncionamNoIcu`, status 0, `OK (1 test)`.

## Pendências / próximos passos
- No emulador, configurar de novo o servidor e o login quando for testar nele.
- O armazenamento do AVD continua apertado (7% livre de 6 GB). Aumentar o tamanho no Device Manager fica a critério de quem usa o emulador.
