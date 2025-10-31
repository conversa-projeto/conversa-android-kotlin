@echo off
echo ========================================
echo  REBUILD DO PROJETO CONVERSA - KOTLIN
echo ========================================
echo.

echo [1/5] Limpando projeto...
call gradlew clean
echo.

echo [2/5] Sincronizando dependencias...
call gradlew dependencies
echo.

echo [3/5] Compilando codigo Kotlin...
call gradlew compileDebugKotlin
echo.

echo [4/5] Construindo projeto...
call gradlew assembleDebug
echo.

echo [5/5] Instalando no dispositivo...
call gradlew installDebug
echo.

echo ========================================
echo  BUILD CONCLUIDO COM SUCESSO!
echo ========================================
echo.
echo Proximos passos:
echo 1. Abra o app no dispositivo
echo 2. Teste a funcionalidade de audio
echo 3. Verifique os logs caso necessario
echo.
pause
