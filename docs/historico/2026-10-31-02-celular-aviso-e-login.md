# 2026-10-31 · 02 · Celular: aviso por cima das abas e o teclado do campo de usuário

- **Fluxo:** Sessão, conversas, contatos e presença (etapa 2)
- **Tipo:** código (correções)
- **Itens:** `TODO.md` 1.x (`SnackbarHost` global, GER-01), 2.2 (tela de login) e 2.3 (erros na inicialização, AUT-03); linhas novas das correções
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
A pessoa pediu para abrir o Conversa no celular (Galaxy A25, Android 16, teclado SwiftKey, depuração por Wi-Fi), apontar para o servidor de desenvolvimento e testar o envio de mensagens. Ela tinha tido problemas no teste da manhã, no servidor do trabalho.

**O que o celular tinha:**
- **Logcat:** só guardava os últimos ~20 minutos. O app estava em `https://10.0.5.78` (servidor do trabalho), que daqui não responde: todo pedido esgotava o tempo em 15 s. A última sincronização boa foi às 08:57. Havia um envio pendente na fila (`EnvioWorker`). Os registros do teste da manhã já não estavam lá.
- **Primeiro defeito:** sem servidor, o aviso fixo "Sem conexão com o servidor… (tentando de novo…)" ficava **por cima da barra de abas** e bloqueava o toque. Não dava para chegar às Configurações nem para sair da conta. Para seguir, toquei na sobra da aba à direita do aviso.
- **Saída da conta:** a pessoa autorizou sair do servidor do trabalho, sabendo que a mensagem pendente e o cache local seriam descartados. O servidor foi trocado para `http://localhost:8081`, com o proxy de dev e o `adb reverse` das portas 8081 e 3478.
- **Segundo defeito:** no login, o usuário "teste.android.b" virou **"teste. Android. b"**. O SwiftKey pôs espaço e maiúscula depois de cada ponto, e o servidor respondeu "Usuário não encontrado!". O campo usava `KeyboardType.Ascii` com a correção automática ligada.

## O que foi feito
- **`AreaDeAvisos`** (`core/ui/.../AvisosGlobais.kt`):
  - o Snackbar global fica acima do que estiver mais alto embaixo: o teclado, a barra do sistema ou a barra fixa da tela (`LocalBarraDeBaixo`);
  - antes, só subia acima da barra do sistema e do teclado.
- **`PrincipalTela`:** a `NavigationBar` informa a altura dela em `LocalBarraDeBaixo` (`onSizeChanged`) e volta a zero ao sair da tela.
- **Campo de usuário no login e no cadastro** (`TECLADO_DO_USUARIO`, em `LoginTela.kt`): teclado de e-mail, sem maiúscula e sem correção automática. No Android, é o teclado que não mexe no que se digita num login.

## Como foi verificado
- `:app:assembleDebug`, `:feature:auth:testDebugUnitTest`, `:core:ui:testDebugUnitTest` e `ktlintCheck`; depois a verificação completa (abaixo).
- **No celular, com a versão nova instalada por cima** (`adb install -r`; ela também trouxe o que entrou desde 22/10):
  - **login:** o usuário digitado ficou exatamente "teste.android.b", e a conta B entrou;
  - **enviar:** na direta com o C (conversa 8), "Teste do celular no servidor de dev" chegou ao servidor (mensagem 1247, conferida pela API como o C), e o celular marcou "Entregue";
  - **receber:** a resposta do C pela API (1248) apareceu no celular em menos de 2,5 s, pelo tempo real;
  - **aviso:** com o proxy de dev parado e o app reaberto, o aviso ficou em y=1854, acima das abas (y=2137). O toque em "Configurações" abriu a aba. Com o proxy de volta, o app reconectou e o aviso sumiu em ~15 s.

## Decisões
- **A margem fica na área de avisos,** em vez de um `SnackbarHost` próprio na tela principal: todo aviso global (erros de qualquer tela) fica acima das abas, não só o "tentando de novo".
- **Teclado de e-mail no campo de usuário:** a conta pode ser um nome ou um e-mail, e esse teclado não muda nenhum dos dois.

## Pendências
- O problema do teste da manhã no servidor do trabalho não pôde ser visto: os registros já não estavam no logcat, e a mensagem pendente foi descartada ao sair da conta. Se acontecer de novo, ler o logcat logo depois (`adb logcat -d`).
