# Roteiro: app no celular contra um servidor interno

Para apresentar ou testar o app num **celular real**, contra um servidor Conversa que roda **na rede local**, sem FCM. Escrito para a apresentação de 2026-10-17 (branch `apresentacao`, histórico `2026-10-17-20`).

## 1. Instalar
- Use o APK de **debug**. Só ele aceita HTTP e certificado próprio.
- Gerar: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
- No celular: copie o APK e abra. O Android pede para permitir "instalar apps desconhecidos" para o app que abriu o arquivo (Arquivos, Drive…).
- Com cabo e depuração USB: `adb install -r app-debug.apk`.
- O pacote do debug é `com.conversa.conversa.dev`. Convive com outra instalação de release.

## 2. Primeira inicialização
1. **Servidor:** digite o endereço que você usa no navegador para abrir o web de lá.
   - Pode ser só o IP ou o nome (ex.: `192.168.0.10`). O app tenta `https://` e, se não houver HTTPS, `http://` sozinho.
   - Se o web abre com `http://` e uma porta (ex.: `192.168.0.10:8080`), pode digitar assim mesmo.
   - **"Certificado não reconhecido"** aparece quando o servidor usa certificado próprio (mkcert, autoassinado, CA da empresa). Confira a impressão digital, se puder, e toque em **"Confiar e salvar"**.
     - Quem administra o servidor vê a impressão com `openssl x509 -in cert.pem -noout -fingerprint -sha256`.
     - No navegador, ela aparece no cadeado › certificado › SHA-256.
2. **Entrar** com o usuário e a senha do Conversa.
3. Aceite os três pedidos, nesta ordem:
   - **Notificações** › "Permitir" e, no Android, "Permitir";
   - **Chamadas em tela cheia** (Android 14+): só aparece se estiver negado; leva às configurações;
   - **Receber com o app fechado** › "Permitir" e, no Android, "Permitir" (o app sai da otimização de bateria).
4. Puxe a barra de notificações: deve haver **"Conectado ao Conversa"**. Esse aviso fixo é o que faz mensagens e chamadas chegarem com o app fechado, sem o Google.

## 3. Conferir antes de apresentar
- **Configurações › Notificações:**
  - "Receber com o app fechado" **ligado**;
  - "Uso de bateria" em **verde** ("Sem restrição");
  - as notificações e os canais "Ativadas".
- **Samsung (One UI):** Configurações › Aplicativos › Conversa (o ícone do debug) › Bateria › **Sem restrições**. Em Configurações › Manutenção do aparelho › Bateria › Limites de uso em segundo plano, o Conversa **não** pode estar em "Apps em suspensão" nem em "Apps em suspensão profunda".
- **Xiaomi, Motorola e outros:** procure "Inicialização automática" e "Sem restrições de bateria" para o Conversa.
- **Toque:** o app usa o toque e o som de notificação **padrões do aparelho**. Com o celular no silencioso ou no vibrar, a chamada só vibra (ou nada).

## 4. O que mostrar, e como
| Pedido | Como mostrar |
|---|---|
| Notificação com o app fechado | Tire o Conversa dos recentes, apague a tela e mande uma mensagem pelo web → notificação com o som padrão |
| Atender com a tela bloqueada | Celular bloqueado; ligue pelo web → a tela acende com "Chamada recebida" → arraste "Atender" |
| Continuar após fechar o app | Em chamada, abra os recentes e tire o Conversa → a chamada segue (cronômetro na barra de status) |
| Reabrir com a chamada em segundo plano | Abra o Conversa → faixa verde "Toque para voltar à chamada" no topo; navegue entre os chats |
| Sair da chamada sem encerrar | Na chamada, o botão do canto superior esquerdo (ou o Voltar) → volta ao app com a faixa; toque nela para voltar |

## 5. Se algo não funcionar
- **"Não foi possível conectar":**
  - o celular está no mesmo Wi-Fi do servidor?
  - o endereço abre no **navegador do celular**?
- **Mensagens não chegam com a tela apagada:** confira o "Uso de bateria" (seção 3) e se o aviso "Conectado ao Conversa" está na barra.
- **Chamada conecta, mas sem áudio ou vídeo:**
  - teste a mesma chamada no web, no navegador do celular;
  - se também falhar, é a configuração de mídia do servidor (MediaMTX e TURN), não o app;
  - se o servidor usar `turns:` (TURN com TLS, a variável `CONVERSA_TURN_PORTA`) com certificado próprio, a chamada pelo relay pode falhar (histórico `2026-10-17-20`, pendências).
- **Trocar de servidor:** na tela de login, toque no "Servidor: …"; logado, Configurações › Servidor.
