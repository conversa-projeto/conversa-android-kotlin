# 2026-11-02 · 02 · A versão de teste no GitHub (publicar.ps1)

- **Fluxo:** Qualidade, segurança e publicação; também Repositório e build (o `app/build.gradle.kts`)
- **Tipo:** build
- **Itens:** `TODO.md` Qualidade (linha nova, depois do `signingConfig`); ADR 0002 ("APK direto por enquanto")
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A pessoa pediu para lançar a versão no GitHub "assim como é feito no Tabernáculo", como versão de teste, para baixar no celular, com um APK de 32 bits e outro de 64.

No Tabernáculo, o `publicar.ps1` faz isso desde 2026-10-22:
- compila uma cópia limpa do commit que está no GitHub e roda a bancada nela;
- publica um **Pre-release** `vX.Y.Z-teste.N`;
- confere o que chegou lá.

## O que foi feito
- **`publicar.ps1`** (raiz), o mesmo esquema do Tabernáculo para o Android:
  - ferramentas: o `gh` logado, o JDK do Android Studio e as build-tools do SDK;
  - o commit é a `origin/reescrita`, e ele para se a daqui estiver na frente;
  - a versão é a base do `versionName` sem o `-dev`, mais `-teste.N` contado pelas tags do GitHub;
  - a cópia limpa fica em `build\publicar` (`git worktree`);
  - testes unitários e `:app:assembleDebug` numa compilação só (`--no-daemon`, Kotlin no mesmo processo);
  - confere a versão, a ABI e a assinatura de cada APK;
  - as notas saem do `git log`;
  - `gh release create --prerelease --target <commit>` e a conferência dos tamanhos;
  - `-Ensaio` compila o `HEAD` e não publica;
  - o arquivo tem BOM, porque o PowerShell 5.1 lê sem BOM como ANSI e os acentos das notas sairiam trocados.
- **`app/build.gradle.kts`**, duas propriedades do Gradle que só valem quando passadas:
  - `conversa.versao` troca o `versionName` (o `-debug` do debug continua no fim);
  - `conversa.abis` liga o `splits.abi` só com as ABIs pedidas, sem o universal.
  - Sem elas, o build de sempre não muda: o `app-debug.apk` com todas as ABIs, e o emulador x86_64 instalando.
- **`.gitignore`:** `dist/`, onde ficam os APKs e as notas de cada versão.
- **`docs/desenvolvimento/versoes-e-release.md`** (novo): o que vai no release e por que é o APK de debug, o número, os passos, o ensaio, as notas, como soltar, como instalar no celular e o que a oficial vai pedir.
- **`CLAUDE.md`:** a linha do guia na tabela e o comando na seção Build.

## Como foi verificado
- **O primeiro ensaio parou no Gradle:** o `cmd` não achou o `gradlew.bat` na cópia limpa. Neste ambiente a variável `NoDefaultCurrentDirectoryInExePath` está ligada, e o `cmd` não procura programas na pasta atual. O script passou a chamar o `.\gradlew.bat`.
- **O segundo ensaio (`-Ensaio`, do `e9bd5db`):**
  - `BUILD SUCCESSFUL in 1m 56s`, com 297 das 646 tarefas vindas do cache de build;
  - 654 testes unitários verdes;
  - `Conversa-2.0.0-teste.1-64bits.apk` (103,6 MB, só `arm64-v8a`) e `-32bits.apk` (98,6 MB, só `armeabi-v7a`);
  - os dois com `versionName='2.0.0-teste.1-debug'` e `versionCode='2'`;
  - a assinatura é a da chave de depuração desta máquina: o SHA-256 do `apksigner verify --print-certs` é o mesmo do `keytool` no `~/.android/debug.keystore` (`bc6da118…8c3126`).
- **A limpeza deixou 4.640 arquivos na cópia:** não estavam presos. Os caminhos passavam de 260 letras (as classes do Hilt e das lambdas), e nem o `git worktree remove` nem o `Remove-Item` do PowerShell 5.1 os apagam.
  - Agora vão o `git -c core.longpaths=true worktree remove` e, se sobrar, o `rd /s /q "\\?\<pasta>"`.
  - Provado com uma cópia e um arquivo num caminho de 328 letras: nada sobrou.
- (preencher: a publicação)

## Decisões
- **O APK de debug, e não o de release:**
  - o release não tem assinatura nem o R8 provado;
  - o release só aceita HTTPS com as CAs do sistema, e o servidor interno precisa do debug;
  - o debug sai com a chave de depuração desta máquina, a mesma do que já está no celular, e instala por cima com o servidor e o login.
- **O ramo é a `reescrita`**, e não o `main`: o `main` do GitHub ainda é o app antigo.
- **O `versionCode` fica em 2:** com códigos crescentes, o `adb install -r` do build de sempre seria recusado como rebaixamento depois de instalar uma versão de teste.
- **Os nomes dos arquivos dizem 64bits e 32bits**, como a pessoa pediu; as notas dizem a ABI de cada um.
- **Sem os commits `docs: hash …` nas notas:** só preenchem o histórico.
- **Lint e ktlint não entram no script:** já rodam antes de cada commit e no CI. Os testes entram, como a bancada do Tabernáculo.

## Pendências / próximos passos
- A versão oficial (§6 do guia): `signingConfig`, R8 e a decisão sobre HTTPS no release.
