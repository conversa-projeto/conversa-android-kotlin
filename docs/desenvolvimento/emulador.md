# Testar no emulador contra o servidor de desenvolvimento

Como o app novo é testado de ponta a ponta sem mexer nas configurações de segurança do aparelho.

## Por que HTTP na porta 8080

O servidor de dev atende em HTTPS (nginx, porta 443) com certificado do **mkcert**. Para o emulador confiar nele seria preciso instalar a CA do mkcert no aparelho (Configurações > Segurança > Credenciais), o que altera a segurança do dispositivo. Para testes automatizados usamos a API **direto, sem o nginx**:

- a API escuta em `127.0.0.1:8080` na máquina (container `api`), atendendo `/api/` e `/ws/` (o nginx só repassa);
- só o **build de debug** aceita HTTP, e **só para `localhost`/`127.0.0.1`** (`app/src/debug/res/xml/network_security_config.xml`). O release continua só com HTTPS.

Limitação: `/storage/` (anexos no MinIO) e `/webrtc/` passam pelo nginx; para testar anexos e chamadas use HTTPS com a CA do mkcert instalada (pela pessoa, num aparelho de teste).

## Passo a passo

1. Servidor de dev no ar (Docker: `api`, `postgres`, `minio`, `nginx`…). Conferir: `curl http://localhost:8080/api/usuario/permissoes` → 401 com `{"error":"Token não informado"}`.
2. Emulador: `emulator -avd Medium_Phone_API_36.1` (com `-no-window` roda sem janela).
3. Ligar a porta do emulador à da máquina: `adb reverse tcp:8080 tcp:8080` (refazer a cada boot do emulador).
4. `./gradlew :app:installDebug` e abrir o app.
5. Tela "Servidor": `http://localhost:8080` → "Testar conexão" → "Salvar".

## Contas de teste (banco `conversa_dev`, só no servidor local)

Criadas pelo próprio cadastro do app em 2026-10-07:

| Usuário | Nome | E-mail | Senha |
|---|---|---|---|
| `teste.android.a` | Teste Android A | teste.android.a@exemplo.test | `teste-49b1fffb` |
| `teste.android.b` | Teste Android B | teste.android.b@exemplo.test | `teste-49b1fffb` |

São contas descartáveis do banco local de desenvolvimento; não existem em nenhum outro servidor.
