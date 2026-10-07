# Testar no emulador contra o servidor de desenvolvimento

Como o app novo é testado de ponta a ponta sem mexer nas configurações de segurança do aparelho.

## Por que HTTP (e o proxy de desenvolvimento)

O servidor de dev atende em HTTPS (nginx, porta 443) com certificado do **mkcert**. Para o emulador confiar nele seria preciso instalar a CA do mkcert no aparelho (Configurações > Segurança > Credenciais). Isso altera a segurança do dispositivo, então para os testes automatizados usamos HTTP:

- **`ferramentas/proxy-dev.mjs`** (Node, sem dependências) faz o papel do nginx sem TLS, em `127.0.0.1:8081`:
  - `/storage/` vai para o MinIO (`127.0.0.1:9000`), **mantendo o `Host`**, porque o MinIO valida a assinatura da URL com ele;
  - o resto vai para a API (`127.0.0.1:8080`), inclusive o WebSocket `/ws/`, com `X-Forwarded-Proto: http`.
- O servidor monta as URLs de anexo com o `Host` e o `X-Forwarded-Proto` da requisição (`conversa/src/contexto.ts`). Por isso, pelo proxy, elas voltam apontando para `http://localhost:8081/storage/...`, e upload e download funcionam no emulador.
- Só o **build de debug** aceita HTTP, e **só para `localhost`/`127.0.0.1`** (`app/src/debug/res/xml/network_security_config.xml`). O release continua só com HTTPS.

> Dá para usar a API direto (`http://localhost:8080`, sem o proxy) para tudo menos anexos: sem o nginx/proxy, `/storage/` não existe.

Ainda passam só pelo nginx real: o `/webrtc/` (chamadas, etapa 6). Para testar chamadas, use HTTPS com a CA do mkcert instalada pela pessoa num aparelho de teste.

## Passo a passo

1. Servidor de dev no ar (Docker: `api`, `postgres`, `minio`, `nginx`…). Conferir: `curl http://localhost:8080/api/usuario/permissoes` → 401 com `{"error":"Token não informado"}`.
2. Proxy: `node ferramentas/proxy-dev.mjs` (deixar rodando).
3. Emulador: `emulator -avd Medium_Phone_API_36.1` (com `-no-window` roda sem janela).
4. Ligar a porta do emulador à da máquina: `adb reverse tcp:8081 tcp:8081` (refazer a cada boot do emulador).
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
