<#
  Publica uma VERSÃO DE TESTE do Conversa (Android) no GitHub, como Pre-release,
  com o APK de 64 bits e o de 32 — docs/desenvolvimento/versoes-e-release.md.

    powershell -NoProfile -ExecutionPolicy Bypass -File publicar.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File publicar.ps1 -Ensaio
    ... -SemTestes         pula os testes unitários (e as notas dizem que pulou)
    ... -Notas notas.md    as notas do release saem deste arquivo, e não do git log
    ... -Ramo reescrita    o ramo do GitHub de onde a versão sai (o padrão)

  O que ele faz, na ordem (§3):
   1. confere as ferramentas: git, o gh logado, o JDK do Android Studio e o SDK;
   2. escolhe o commit: o ramo que está NO GITHUB (origin/reescrita). O ensaio
      usa o commit daqui (HEAD), para provar a mudança antes de enviá-la;
   3. a versão: a do versionName desse commit, sem o "-dev", + "-teste.N",
      com o N seguinte ao maior que já existe no GitHub — 2.0.0-teste.1, .2, ...;
   4. uma CÓPIA LIMPA desse commit em build\publicar. Mudança sem commit
      nesta pasta, de quem for, não entra no APK;
   5. os testes unitários e o APK, nessa cópia, numa compilação só. Teste
      vermelho, ele para e nada é publicado;
   6. confere os dois APKs (arm64-v8a e armeabi-v7a): a versão, a ABI e a
      assinatura;
   7. o release no GitHub, Pre-release, apontando para o commit;
   8. confere o que chegou lá, e apaga a cópia limpa.

  O APK é o de DEBUG, de propósito (§1): o release ainda não tem assinatura
  nem o R8 provado (TODO, Qualidade), e só aceita HTTPS com as CAs do sistema.
#>
param(
    [switch]$Ensaio,
    [switch]$SemTestes,
    [string]$Notas,
    [string]$Ramo = 'reescrita'
)

# Continue, e não Stop: no PowerShell 5.1 o que o git escreve no stderr
# ("Preparing worktree...") vira erro e pararia o script com Stop. Cada
# comando de fora tem o código de saída conferido à mão.
$ErrorActionPreference = 'Continue'
# git e gh falam UTF-8; sem isto o PowerShell 5.1 lê os acentos dos
# commits na página de código do console, e as notas saem trocadas.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$utf8 = New-Object Text.UTF8Encoding $false

$raiz = $PSScriptRoot
# Curto de propósito: o build do Android vai fundo nas pastas, e o Windows
# ainda tropeça em caminho de mais de 260 letras.
$fonte = Join-Path $raiz 'build\publicar'
$inicio = Get-Date
$abis = @(
    @{ Abi = 'arm64-v8a'; Rotulo = '64bits' },
    @{ Abi = 'armeabi-v7a'; Rotulo = '32bits' }
)

function Passo([string]$texto) {
    $t = [int]((Get-Date) - $inicio).TotalSeconds
    Write-Host ""
    Write-Host ("== " + $texto + "  (" + $t + " s)") -ForegroundColor Cyan
}
function Aviso([string]$texto) { Write-Host ("AVISO: " + $texto) -ForegroundColor Yellow }
function Parar([string]$texto) { throw $texto }

function Tirar-Fonte {
    if (Test-Path $fonte) {
        git -C $raiz -c core.longpaths=true worktree remove --force $fonte 2>&1 | Out-Null
        # O build do Android passa das 260 letras em alguns arquivos (as
        # classes do Hilt e das lambdas), e o Remove-Item do PowerShell 5.1
        # não os apaga. O rd com o \\?\ na frente apaga.
        if (Test-Path $fonte) { cmd.exe /c "rd /s /q `"\\?\$fonte`"" 2>&1 | Out-Null }
        if (Test-Path $fonte) { Aviso "não consegui apagar $fonte inteira (arquivo preso?); apague à mão." }
    }
    git -C $raiz worktree prune 2>&1 | Out-Null
}

$codigo = 0
try {
    # ── 1. As ferramentas ───────────────────────────────────────────
    Passo 'As ferramentas'
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Parar 'git não está no PATH.' }
    $gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
    if (-not $gh) {
        $c = Join-Path $env:LOCALAPPDATA 'Programs\gh\bin\gh.exe'
        if (Test-Path $c) { $gh = $c }
    }
    if (-not $gh) { Parar 'o GitHub CLI (gh) não está instalado (§4.1).' }
    & $gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { Parar 'o gh não está logado: rode  gh auth login --web  (§4.1).' }
    # O java do PATH desta máquina é o 1.8; o Gradle e o apksigner pedem o 17+.
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (-not (Test-Path (Join-Path $jbr 'bin\java.exe'))) { Parar "sem o JDK do Android Studio em $jbr." }
    $env:JAVA_HOME = $jbr
    $env:Path = (Join-Path $jbr 'bin') + ';' + $env:Path
    # O SDK: o local.properties (que não vai no git) ou o ANDROID_HOME.
    $locais = Join-Path $raiz 'local.properties'
    $sdk = $null
    if (Test-Path $locais) {
        $linha = Select-String -Path $locais -Pattern '^sdk\.dir=(.+)$' | Select-Object -First 1
        if ($linha) { $sdk = $linha.Matches[0].Groups[1].Value -replace '\\:', ':' -replace '\\\\', '\' }
    }
    if (-not $sdk) { $sdk = $env:ANDROID_HOME }
    if (-not $sdk -or -not (Test-Path $sdk)) { Parar 'não achei o SDK do Android (local.properties ou ANDROID_HOME).' }
    $ferramentas = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { [version]($_.Name -replace '[^\d.].*$', '') } | Select-Object -Last 1
    if (-not $ferramentas) { Parar "sem as build-tools em $sdk." }
    $aapt2 = Join-Path $ferramentas.FullName 'aapt2.exe'
    $apksigner = Join-Path $ferramentas.FullName 'apksigner.bat'
    $url = (git -C $raiz remote get-url origin).Trim()
    if ($url -notmatch 'github\.com[:/](.+?)(\.git)?$') { Parar "o origin não é do GitHub: $url" }
    $repo = $Matches[1]
    Write-Host "  gh: $gh"
    Write-Host "  JDK: $jbr"
    Write-Host "  build-tools: $($ferramentas.FullName)"
    Write-Host "  repositório: $repo"

    # ── 2. O commit ─────────────────────────────────────────────────
    Passo 'O commit'
    git -C $raiz fetch --quiet --tags origin 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Parar 'o git fetch falhou: sem rede, ou sem acesso ao GitHub.' }
    if ($Ensaio) {
        $commit = (git -C $raiz rev-parse HEAD).Trim()
        Write-Host '  ENSAIO: o commit daqui (HEAD), e nada vai para o GitHub.'
    } else {
        $commit = (git -C $raiz rev-parse "origin/$Ramo" 2>$null)
        if ($LASTEXITCODE -ne 0 -or -not $commit) { Parar "o GitHub não tem o ramo $Ramo." }
        $commit = $commit.Trim()
        # O release sai do que está no GitHub. Commit daqui que não subiu
        # ficaria de fora sem ninguém perceber — então para.
        $frente = [int](git -C $raiz rev-list --count "origin/$Ramo..$Ramo" 2>$null)
        if ($frente -gt 0) {
            Parar "a $Ramo daqui tem $frente commit(s) que o GitHub não tem. Envie antes (git push) — o release sai da $Ramo de lá."
        }
    }
    $curto = $commit.Substring(0, 7)
    $assunto = (git -C $raiz log -1 --format=%s $commit) -join ' '
    Write-Host "  $curto  $assunto"
    $sujo = git -C $raiz status --porcelain
    if ($sujo) { Aviso 'há mudança sem commit nesta pasta. Ela NÃO entra no APK — só o commit acima entra.' }

    # ── 3. A versão ─────────────────────────────────────────────────
    Passo 'A versão'
    $gradleApp = git -C $raiz show "${commit}:app/build.gradle.kts"
    $nome = $null
    foreach ($l in $gradleApp) { if ($l -match '^\s*versionName = "([^"]+)"') { $nome = $Matches[1]; break } }
    if (-not $nome) { Parar 'não achei a linha versionName do app/build.gradle.kts.' }
    if ($nome -notmatch '^(\d+\.\d+\.\d+)(-dev)?$') { Parar "o versionName diz $nome; ele guarda X.Y.Z-dev, e o -teste.N quem põe é este script (§2)." }
    $base = $Matches[1]
    $n = 0
    $padrao = 'refs/tags/v' + [regex]::Escape($base) + '-teste\.(\d+)$'
    foreach ($t in (git -C $raiz ls-remote --tags origin)) {
        if ($t -match $padrao) { $n = [Math]::Max($n, [int]$Matches[1]) }
    }
    $numero = $n + 1
    $versao = "$base-teste.$numero"
    $tag = "v$versao"
    Write-Host "  $tag"
    $saida = Join-Path $raiz "dist\publicar\$versao"
    New-Item -ItemType Directory -Force $saida | Out-Null

    # ── 4. A cópia limpa ────────────────────────────────────────────
    Passo 'A cópia limpa'
    Tirar-Fonte
    git -C $raiz worktree add --detach $fonte $commit 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Parar 'não consegui criar a cópia limpa (git worktree add).' }
    if (Test-Path $locais) { Copy-Item $locais $fonte -ErrorAction Stop }
    Write-Host "  $fonte"

    # ── 5. Os testes e o APK ────────────────────────────────────────
    $linhaTestes = ''
    $tarefas = @(':app:assembleDebug')
    if ($SemTestes) {
        Aviso 'os testes foram PULADOS (-SemTestes), e as notas vão dizer isso.'
        $linhaTestes = '⚠️ Publicada **sem os testes unitários** (-SemTestes).'
    } else {
        $tarefas = @('testDebugUnitTest', ':core:model:test', ':core:testing:test') + $tarefas
    }
    Passo ('O Gradle, na cópia limpa: ' + ($tarefas -join ' '))
    # --no-daemon e o Kotlin no mesmo processo: nada fica vivo segurando
    # arquivo da cópia, e o Windows deixa apagá-la no fim. O cache de build
    # do Gradle (org.gradle.caching) é o da máquina e vale para a cópia.
    $props = "-Pconversa.versao=$versao -Pconversa.abis=" + (($abis | ForEach-Object { $_.Abi }) -join ',') +
        ' -Pkotlin.compiler.execution.strategy=in-process'
    $log = Join-Path $saida 'gradle.txt'
    Push-Location $fonte
    # O .\ na frente: com NoDefaultCurrentDirectoryInExePath ligado, o cmd
    # não procura o programa na pasta atual.
    cmd.exe /c (".\gradlew.bat --no-daemon --console=plain $props " + ($tarefas -join ' ') + ' 2>&1') |
        Tee-Object -FilePath $log
    $resGradle = $LASTEXITCODE
    Pop-Location
    if ($resGradle -ne 0) { Parar "o Gradle FALHOU na cópia limpa (teste ou compilação) — nada foi publicado. O registro está em $log" }
    if (-not $SemTestes) {
        $total = 0; $falhas = 0; $pulados = 0
        Get-ChildItem $fonte -Recurse -Filter 'TEST-*.xml' |
            Where-Object { $_.FullName -match '\\build\\test-results\\' } |
            ForEach-Object {
                $s = ([xml](Get-Content $_.FullName -Raw -Encoding UTF8)).testsuite
                $total += [int]$s.tests; $falhas += [int]$s.failures + [int]$s.errors; $pulados += [int]$s.skipped
            }
        if ($falhas -gt 0) { Parar "$falhas teste(s) falharam e o Gradle não parou — nada foi publicado." }
        $linhaTestes = "Testes unitários verdes nesta cópia: $total testes ($pulados ignorados)."
        Write-Host "  $linhaTestes"
    }

    # ── 6. Os APKs ──────────────────────────────────────────────────
    Passo 'Os APKs'
    $arquivos = @()
    foreach ($a in $abis) {
        $apk = Join-Path $fonte ("app\build\outputs\apk\debug\app-" + $a.Abi + "-debug.apk")
        if (-not (Test-Path $apk)) { Parar "o APK não saiu: $apk" }
        $badging = & $aapt2 dump badging $apk 2>$null
        $pacote = $badging | Where-Object { $_ -like 'package:*' } | Select-Object -First 1
        $nativo = $badging | Where-Object { $_ -like 'native-code:*' } | Select-Object -First 1
        # O debug põe o "-debug" no fim (versionNameSuffix).
        if ($pacote -notmatch "versionName='$([regex]::Escape($versao))-debug'") { Parar "o APK $($a.Abi) diz: $pacote" }
        if ($nativo -ne "native-code: '$($a.Abi)'") { Parar "o APK $($a.Abi) leva outras ABIs: $nativo" }
        & $apksigner verify $apk *> $null
        if ($LASTEXITCODE -ne 0) { Parar "a assinatura do APK $($a.Abi) não confere (apksigner verify)." }
        $destino = Join-Path $saida ("Conversa-$versao-" + $a.Rotulo + '.apk')
        Copy-Item $apk $destino -Force -ErrorAction Stop
        $arquivos += $destino
        Write-Host ("  ok  " + (Split-Path $destino -Leaf) + "  " + $a.Abi + "  " + [Math]::Round((Get-Item $destino).Length / 1MB, 1) + " MB")
    }

    # ── As notas ────────────────────────────────────────────────────
    $arqNotas = Join-Path $saida 'notas.md'
    $l = New-Object System.Collections.Generic.List[string]
    $l.Add("**Versão de TESTE** — não é a oficial. Saiu do commit ``$curto`` da ``$Ramo``: *$assunto*")
    $l.Add('')
    if ($Notas) {
        $l.Add([IO.File]::ReadAllText((Resolve-Path $Notas).Path).TrimEnd())
    } else {
        # Sem os "docs: hash ...", que só preenchem o histórico.
        $filtro = @('--no-merges', '--invert-grep', '--grep=^docs: hash', '--format=- %s')
        $anterior = git -C $raiz describe --tags --abbrev=0 --match 'v*-teste.*' $commit 2>$null
        if ($LASTEXITCODE -eq 0 -and $anterior) {
            $l.Add("## O que mudou desde $anterior")
            $mudancas = git -C $raiz log @filtro "$anterior..$commit"
        } else {
            $l.Add('## Os últimos commits')
            $l.Add('')
            $l.Add('A primeira versão de teste publicada: estes são os 25 commits mais recentes.')
            $mudancas = git -C $raiz log @filtro -n 25 $commit
        }
        $l.Add('')
        foreach ($m in $mudancas) { $l.Add($m) }
    }
    $l.Add('')
    $l.Add('## Antes de instalar')
    $l.Add('')
    $l.Add('- É o **APK de depuração** (`com.conversa.conversa.dev`): instala **ao lado** do app antigo, sem trocá-lo. Aceita servidor interno com HTTP ou com certificado próprio ("Confiar neste certificado").')
    $l.Add('- O Android pede para liberar a instalação pelo navegador ou pelo app de arquivos (*Instalar apps desconhecidos*). O Play Protect pode avisar que o app é desconhecido: é **Mais detalhes → Instalar mesmo assim**.')
    $l.Add('- Instalar por cima de outra versão de teste **mantém o servidor e o login**. Se o Android recusar ("o pacote entra em conflito"), o instalado foi assinado em outro computador: desinstale antes.')
    $l.Add('- Uma versão de teste **mais velha** por cima de uma mais nova pode não abrir o cache local; aí é *Limpar armazenamento* do app.')
    $l.Add('')
    $l.Add('## Arquivos')
    $l.Add('')
    $l.Add("- ``Conversa-$versao-64bits.apk`` — **arm64-v8a**, o de quase todo celular desde 2017. Na dúvida, é este.")
    $l.Add("- ``Conversa-$versao-32bits.apk`` — **armeabi-v7a**, para celular antigo ou com Android de 32 bits (quando o de 64 diz que o app não é compatível).")
    $l.Add('')
    $l.Add("O app mostra a versão em Configurações: ``$versao-debug``.")
    if ($linhaTestes) { $l.Add(''); $l.Add($linhaTestes) }
    [IO.File]::WriteAllText($arqNotas, ($l -join "`n") + "`n", $utf8)

    if ($Ensaio) {
        Passo 'ENSAIO pronto — nada foi para o GitHub'
        Write-Host "  seria publicado como $tag, do commit $curto"
        Write-Host "  arquivos e notas em $saida"
    } else {
        # ── 7. O release ────────────────────────────────────────────
        Passo "O release $tag no GitHub"
        # --target com o commit: o gh cria a tag lá, no commit exato que
        # foi compilado, mesmo que o ramo ande enquanto isto roda.
        & $gh release create $tag @arquivos --repo $repo --target $commit `
            --title "Conversa Android $base — teste $numero" --notes-file $arqNotas --prerelease
        if ($LASTEXITCODE -ne 0) { Parar 'o gh release create falhou.' }

        # ── 8. A conferência ────────────────────────────────────────
        Passo 'A conferência'
        $r = (& $gh release view $tag --repo $repo --json url,isPrerelease,assets) -join "`n" | ConvertFrom-Json
        foreach ($arq in $arquivos) {
            $nomeArq = Split-Path $arq -Leaf
            $tamanho = (Get-Item $arq).Length
            $a = $r.assets | Where-Object { $_.name -eq $nomeArq }
            if (-not $a) { Parar "o arquivo $nomeArq não está no release." }
            if ($a.size -ne $tamanho) { Parar "o $nomeArq chegou com $($a.size) bytes, e o daqui tem $tamanho." }
            Write-Host ("  ok  " + $nomeArq + "  " + [Math]::Round($a.size / 1MB, 1) + " MB")
        }
        if (-not $r.isPrerelease) { Parar 'o release não saiu marcado como Pre-release.' }
        git -C $raiz fetch --quiet --tags origin 2>&1 | Out-Null
        Write-Host ""
        Write-Host "PUBLICADO: $($r.url)" -ForegroundColor Green
    }
} catch {
    Write-Host ""
    Write-Host ("PAROU: " + $_.Exception.Message) -ForegroundColor Red
    $codigo = 1
} finally {
    Tirar-Fonte
}
exit $codigo
