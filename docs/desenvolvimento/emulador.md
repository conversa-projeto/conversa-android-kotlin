# Testar no emulador contra o servidor de desenvolvimento

Como o app novo é testado de ponta a ponta sem mexer nas configurações de segurança do aparelho.

## Por que HTTP (e o proxy de desenvolvimento)

O servidor de dev atende em HTTPS (nginx, porta 443) com certificado do **mkcert**. Para o emulador confiar nele seria preciso instalar a CA do mkcert no aparelho (Configurações > Segurança > Credenciais). Isso altera a segurança do dispositivo, então para os testes automatizados usamos HTTP:

- **`ferramentas/proxy-dev.mjs`** (Node, sem dependências) faz o papel do nginx sem TLS, em `127.0.0.1:8081`:
  - `/storage/` vai para o MinIO (`127.0.0.1:9000`), **mantendo o `Host`**, porque o MinIO valida a assinatura da URL com ele;
  - `/webrtc/` vai para o MediaMTX (`127.0.0.1:8889`), tirando o `/webrtc`, como o nginx (WHIP/WHEP das chamadas);
  - o resto vai para a API (`127.0.0.1:8080`), inclusive o WebSocket `/ws/`, com `X-Forwarded-Proto: http`.
- O servidor monta as URLs de anexo com o `Host` e o `X-Forwarded-Proto` da requisição (`conversa/src/contexto.ts`). Por isso, pelo proxy, elas voltam apontando para `http://localhost:8081/storage/...`, e upload e download funcionam no emulador.
- Só o **build de debug** aceita HTTP, e **só para `localhost`/`127.0.0.1`** (`app/src/debug/res/xml/network_security_config.xml`). O release continua só com HTTPS.

> Dá para usar a API direto (`http://localhost:8080`, sem o proxy) para tudo menos anexos: sem o nginx/proxy, `/storage/` não existe.

A mídia das chamadas passa pelo TURN do coturn (só TCP, porta 3478): `GET /api/ice` responde `turn:localhost:3478?transport=tcp`, então o emulador precisa também de `adb reverse tcp:3478 tcp:3478`.

## Passo a passo

1. Servidor de dev no ar (Docker: `api`, `postgres`, `minio`, `nginx`…). Conferir: `curl http://localhost:8080/api/usuario/permissoes` → 401 com `{"error":"Token não informado"}`.
2. Proxy: `node ferramentas/proxy-dev.mjs` (deixar rodando).
3. Emulador: `emulator -avd Medium_Phone_API_36.1` (com `-no-window` roda sem janela).
4. Ligar a porta do emulador à da máquina: `adb reverse tcp:8081 tcp:8081` (refazer a cada boot do emulador). Para chamadas, também `adb reverse tcp:3478 tcp:3478`.
5. `./gradlew :app:installDebug` e abrir o app.
6. Tela "Servidor": `http://localhost:8081` → "Testar conexão" → "Salvar".

Atenção: `./gradlew :app:connectedDebugAndroidTest` **desinstala o app no fim**. Depois dele, o servidor e a sessão precisam ser configurados de novo.

## Contas de teste (banco `conversa_dev`, só no servidor local)

Criadas pelo próprio cadastro do app em 2026-10-07:

| Usuário | Nome | E-mail | Senha |
|---|---|---|---|
| `teste.android.a` | Teste Android A | teste.android.a@exemplo.test | `teste-49b1fffb` |
| `teste.android.b` | Teste Android B | teste.android.b@exemplo.test | `teste-49b1fffb` |

São contas descartáveis do banco local de desenvolvimento; não existem em nenhum outro servidor. O segundo usuário (B) é usado por scripts Node para conversar com o app (online, digitando, ler, responder).

## Chamadas (etapa 6): o outro lado pelo web

Para testar chamada, o usuário B fica no cliente web, num Chrome à parte com câmera e microfone falsos:

1. O web de desenvolvimento no ar: `https://localhost` (o nginx repassa a página para o Vite do container `dev`; `conversa/bin/iniciar-desenvolvimento.bat` sobe tudo).
2. Um Chrome com perfil descartável (fora do perfil da pessoa), com dispositivos falsos e a porta de depuração local:
   `chrome.exe --user-data-dir=<pasta temporária> --remote-debugging-port=9333 --use-fake-device-for-media-stream --use-fake-ui-for-media-stream https://localhost/`
   - a câmera falsa mostra um círculo verde girando; o microfone falso, um bipe;
   - entrar como `teste.android.b` e comandar a chamada pelo DevTools Protocol (a store `call` do Pinia: `iniciarChamada`, `aceitarChamada`, `upgradeParaVideo`, `sairDaChamada`…).
3. Gravações: o MediaMTX grava cada participante em `call-<chamada>-u-<usuário>` no volume `conversa-gravacoes`. O contêiner não tem shell; para listar, um contêiner temporário com o volume só de leitura (`docker run --rm -v conversa-gravacoes:/g:ro alpine ls /g`).

Limitação conhecida: a câmera falsa do Chrome não abre de novo na mesma sessão ("Could not start video source"). Para testar o vídeo do web outra vez, feche e abra o Chrome de teste.
