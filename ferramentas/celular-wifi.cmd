@echo off
rem Conecta o adb a um celular pela depuracao por Wi-Fi (Android 11+), sem cabo.
rem Guia: docs\desenvolvimento\emulador.md, "Aparelho de verdade pela depuracao por Wi-Fi".
rem No celular: Opcoes do desenvolvedor > Depuracao por Wi-Fi (ligada), com o celular no mesmo Wi-Fi do PC.
rem   - Primeira vez neste PC: "Parear dispositivo com codigo de pareamento" mostra o endereco:porta e o codigo.
rem   - Sempre: no topo da mesma tela, "Endereco IP e porta" e o endereco para conectar (a porta muda
rem     cada vez que a depuracao por Wi-Fi e ligada, e e diferente da porta do pareamento).
setlocal
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" goto semadb

echo.
set /p JAPAREADO=Este PC ja foi pareado com o celular? (s/n):
if /i "%JAPAREADO%"=="s" goto conectar

echo.
echo No celular: Depuracao por Wi-Fi ^> Parear dispositivo com codigo de pareamento.
set /p ENDPAR=Endereco e porta do PAREAMENTO (ex.: 192.168.2.20:41234):
if "%ENDPAR%"=="" goto fim
set /p CODIGO=Codigo de pareamento (6 digitos):
if "%CODIGO%"=="" goto fim
"%ADB%" pair %ENDPAR% %CODIGO%
if errorlevel 1 goto falhou

:conectar
echo.
echo No topo da tela Depuracao por Wi-Fi: "Endereco IP e porta".
set /p ENDCON=Endereco IP e porta para CONECTAR (ex.: 192.168.2.20:37953):
if "%ENDCON%"=="" goto fim
"%ADB%" connect %ENDCON% | findstr /i /c:"connected to" >nul
if errorlevel 1 goto naoconectou
echo Conectado a %ENDCON%.

echo.
set /p PORTAS=Ligar as portas do servidor de dev no celular (8081 e 3478, para o app usar http://localhost:8081)? (s/n):
if /i not "%PORTAS%"=="s" goto lista
"%ADB%" -s %ENDCON% reverse tcp:8081 tcp:8081
"%ADB%" -s %ENDCON% reverse tcp:3478 tcp:3478
"%ADB%" -s %ENDCON% reverse --list

:lista
echo.
"%ADB%" devices -l
echo Com o emulador tambem ligado, os comandos levam o aparelho: adb -s %ENDCON% ...
goto fim

:semadb
echo adb nao encontrado em "%ADB%". Instale o Android SDK Platform-Tools pelo Android Studio.
goto fim

:falhou
echo O pareamento falhou. Confira o endereco, a porta e o codigo (o codigo vale so enquanto a janela de pareamento esta aberta no celular).
goto fim

:naoconectou
"%ADB%" connect %ENDCON%
echo Nao conectou. Confira se e o "Endereco IP e porta" do topo da tela (nao o do pareamento) e se o celular esta no mesmo Wi-Fi.

:fim
echo.
pause
