@echo off
echo ========================================
echo    REBUILD COMPLETO DO PROJETO
echo ========================================
echo.
echo Arquivos criados/atualizados:
echo - activity_image_viewer.xml
echo - item_mensagem_enviada.xml (atualizado)
echo - item_mensagem_recebida.xml (atualizado)
echo - ic_play.xml (novo icone)
echo - ic_pause.xml (novo icone)
echo.
pause

cd /d "C:\Users\danie\Desktop\GIT\conversa-projeto\conversa-android-kotlin"

echo.
echo [1/4] Limpando cache e builds antigos...
call gradlew.bat clean
if errorlevel 1 (
    echo ERRO ao limpar o projeto!
    pause
    exit /b 1
)

echo.
echo [2/4] Removendo pasta .gradle...
rmdir /s /q .gradle 2>nul
rmdir /s /q app\.gradle 2>nul

echo.
echo [3/4] Fazendo build do projeto...
call gradlew.bat build --no-daemon
if errorlevel 1 (
    echo ERRO ao fazer build!
    echo Tente executar no Android Studio:
    echo 1. File ^> Invalidate Caches ^> Invalidate and Restart
    echo 2. Build ^> Clean Project
    echo 3. Build ^> Rebuild Project
    pause
    exit /b 1
)

echo.
echo [4/4] Gerando bindings...
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo ERRO ao gerar bindings!
    pause
    exit /b 1
)

echo.
echo ========================================
echo    BUILD CONCLUIDO COM SUCESSO!
echo ========================================
echo.
echo PROXIMOS PASSOS:
echo 1. Abra o Android Studio
echo 2. File ^> Sync Project with Gradle Files
echo 3. Build ^> Rebuild Project
echo.
echo Os bindings devem estar disponiveis agora!
echo.
pause
