# Versões e release: a versão de teste no GitHub

**Só existe a versão de TESTE.** Ela sai com um comando, o [`publicar.ps1`](../../publicar.ps1), no mesmo esquema do Tabernáculo (`tabernaculo/documentacao/20-versoes-e-release.md`):
- compila uma cópia limpa da `reescrita` que está no GitHub;
- roda os testes unitários nela;
- publica um release **Pre-release** com dois APKs, o de 64 bits e o de 32.

Para soltar uma, veja o §4, ou peça "solta uma versão de teste".

Criado em 2026-11-02 (histórico `2026-11-02-02`).

## 1. O que vai no release
- **O APK de depuração** (`assembleDebug`, pacote `com.conversa.conversa.dev`), e não o de release:
  - o release ainda não tem `signingConfig` nem o R8 provado (`TODO.md`, Qualidade). Sem assinatura, ele nem instala;
  - o release só aceita HTTPS com as CAs do sistema. O servidor interno, com HTTP ou certificado próprio, só funciona no debug (`apresentacao.md` §1);
  - o celular de teste já tem o debug instalado. O APK sai com a mesma chave de depuração desta máquina (`~/.android/debug.keystore`), então instala por cima e mantém o servidor e o login.
- **Um APK por ABI:**
  - `Conversa-<versão>-64bits.apk` (`arm64-v8a`), o de quase todo celular desde 2017;
  - `Conversa-<versão>-32bits.apk` (`armeabi-v7a`), para celular antigo ou com Android de 32 bits.
  - Cada um leva só a sua parte do libwebrtc (é o que pesa).
- **A versão de teste diz isso em todo lugar:**
  - no número (`2.0.0-teste.1`) e no nome dos arquivos;
  - na marca **Pre-release** do GitHub (nunca *Latest*) e no título do release;
  - no app, em Configurações (`2.0.0-teste.1-debug`) e na primeira linha do registro do app.

## 2. O número
```
2.0.0-dev       a BASE, no versionName do app/build.gradle.kts (o build de sempre)
2.0.0-teste.1   a 1ª versão de teste dessa base
2.0.0-teste.2   a 2ª ...
```
- **A base mora no `versionName`** do [`app/build.gradle.kts`](../../app/build.gradle.kts), como `X.Y.Z-dev`. Ela sobe à mão, num commit, quando se quer começar outra série.
- **O `-teste.N` não vai para o git.** O script o passa ao Gradle por `-Pconversa.versao=2.0.0-teste.1`, que troca o `versionName` só nessa compilação. Soltar uma versão não pede commit.
- **O N se conta sozinho:** é o maior `v<base>-teste.N` que já existe no GitHub, mais um. Apagar um release não reaproveita o número.
- **O `versionCode` continua 2.** O Android instala por cima com o mesmo código. Com códigos crescentes, o `adb install -r` do build de sempre passaria a ser recusado como rebaixamento depois de uma versão de teste.

### 2.1 Os APKs por ABI
- O `-Pconversa.abis=armeabi-v7a,arm64-v8a` liga o `splits.abi` do `app/build.gradle.kts`, só nessa compilação.
- Sem a propriedade, o `assembleDebug` faz o `app-debug.apk` de sempre, com todas as ABIs, e o emulador x86_64 continua instalando como antes.

## 3. Onde é montado: nesta máquina, numa cópia limpa
O `publicar.ps1`, na ordem:
1. **As ferramentas:** `git`, o `gh` **logado** (§4.1), o JDK do Android Studio (o `java` do PATH é o 1.8) e o SDK (do `local.properties`, com as build-tools mais novas).
2. **O commit:** a `reescrita` que está **no GitHub** (`origin/reescrita`). Se a `reescrita` daqui tiver commit que o GitHub não tem, ele **para**: o que não subiu ficaria de fora sem ninguém perceber. `-Ramo` troca o ramo.
3. **A versão:** a base desse commit, mais `-teste.N` (§2).
4. **A cópia limpa:** um `git worktree` desse commit em `build\publicar`, com o `local.properties` copiado.
   - O caminho é curto de propósito: o build do Android vai fundo nas pastas, e o Windows ainda tropeça em mais de 260 letras.
5. **Os testes e o APK, na cópia, numa compilação só:**
   - as tarefas `testDebugUnitTest :core:model:test :core:testing:test :app:assembleDebug`;
   - com `--no-daemon` e o Kotlin no mesmo processo, para nada ficar vivo segurando arquivo da cópia;
   - o cache de build do Gradle é o da máquina e vale para a cópia;
   - **teste vermelho, ele para e nada é publicado.** O registro fica em `dist\publicar\<versão>\gradle.txt`.
6. **A conferência dos APKs:**
   - a versão (`aapt2 dump badging`);
   - que cada um leva só a sua ABI;
   - a assinatura (`apksigner verify`).
7. **O release:** `gh release create`, **Pre-release**, com a tag criada no GitHub **no commit exato** que foi compilado.
8. **A conferência do GitHub:** os dois arquivos estão lá, com o mesmo tamanho dos daqui, e o release está marcado Pre-release. Depois a cópia limpa é apagada.

Os APKs e as notas ficam também em `dist\publicar\<versão>\` (o `dist\` não vai para o git).

**Por que a cópia limpa, e não a pasta de trabalho:** a versão de teste tem de ser **um commit**, que se reencontra depois pela tag, e não o que estava na pasta naquele minuto. Aqui também já trabalhou mais de uma sessão ao mesmo tempo (a apresentação de 2026-10-17, histórico `2026-10-17-20`).

### 3.1 O ensaio
- `-Ensaio` faz os passos 1 a 6 e para: compila, testa e gera os APKs e as notas em `dist\publicar\<versão>\`, mas **não publica**.
- Ele compila o commit **daqui** (`HEAD`), e não o do GitHub, para provar uma mudança antes de enviá-la.
- Só entra o que está commitado; mudança na pasta sem commit não entra.

### 3.2 As notas
Saem sozinhas, do `git log`:
- os commits desde a versão de teste anterior, sem os de merge e sem os `docs: hash …`. Na primeira, os 25 mais recentes;
- em cima, **"Versão de TESTE — não é a oficial"** e o commit de onde saiu;
- embaixo, **Antes de instalar**, os dois arquivos e a linha dos testes.

`-Notas arquivo.md` troca o meio pelas notas escritas à mão.

## 4. Soltar uma versão de teste
```
powershell -NoProfile -ExecutionPolicy Bypass -File publicar.ps1
```
- Antes, a `reescrita` tem de estar no GitHub (`git push`).
- `-Ensaio` prova sem publicar, `-SemTestes` pula os testes (e as notas dizem isso).
- Leva alguns minutos: a cópia é compilada do zero, com o que estiver no cache de build.

### 4.1 Uma vez por máquina
| Ferramenta | Onde está nesta máquina |
|---|---|
| **GitHub CLI** (`gh`) | `%LOCALAPPDATA%\Programs\gh\bin`, logado como `DanielAraujo-dac` (o mesmo do Tabernáculo) |
| **JDK 17+** | o do Android Studio, `C:\Program Files\Android\Android Studio\jbr` |
| **SDK do Android** | o do `local.properties`, com as build-tools (`aapt2`, `apksigner`) |

O login do `gh`, se cair: `gh auth login --web`, e o código em **github.com/login/device**.

## 5. Instalar no celular
- **Pelo navegador do celular:** abra o release no GitHub, baixe o `-64bits.apk` e abra o arquivo.
- O Android pede para liberar *Instalar apps desconhecidos* para o navegador ou o app de arquivos. Quem libera é a pessoa, no aparelho.
- O Play Protect pode avisar que o app é desconhecido: é **Mais detalhes → Instalar mesmo assim**.
- **Por cima** de outra versão de teste, ou do debug instalado por esta máquina, ficam o servidor e o login.
  - Se o Android disser que o pacote "entra em conflito", o instalado foi assinado em outro computador (o APK do CI, por exemplo): desinstale antes.
- **Uma versão mais velha por cima de uma mais nova** pode fechar ao abrir: o Room não tem volta de versão, e o cache local é da mais nova. Aí é *Limpar armazenamento* do app (servidor e login somem).
- O resto (servidor interno, permissões, bateria) é o roteiro de [`apresentacao.md`](apresentacao.md).

## 6. A oficial: o que ela vai pedir
O `publicar.ps1` não faz a oficial, de propósito. Quando ela vier:
- o APK de **release** (`com.conversa.conversa`, o mesmo do app legado, para atualizar por cima, ADR 0001);
- o `signingConfig` com a keystore fora do Git;
- o R8 provado (as regras do kotlinx.serialization, do Hilt e do WebRTC);
- e a decisão sobre HTTPS: hoje o release recusa servidor sem certificado de CA do sistema.

O repositório é **público**: os releases, de teste ou não, ficam abertos para quem tiver o endereço.
