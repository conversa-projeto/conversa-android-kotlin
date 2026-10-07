# Conversa — cliente Android

Cliente Android (Kotlin) do **Conversa**: mensagens, anexos, chamadas de áudio e vídeo e notificações. Fala com o servidor [`conversa`](https://github.com/conversa-projeto) (Bun + Elysia) e busca paridade com o cliente web `conversa-web`.

## Estado atual: em reescrita

Em 2026-10-06 uma auditoria concluiu que o app (escrito contra o antigo servidor Delphi) está defasado demais. A decisão foi **construir uma nova base** (ADR `docs/adr/0001-nova-base.md`).

- **Comece por:** [`docs/auditoria-2026-10/00-LEIAME.md`](docs/auditoria-2026-10/00-LEIAME.md)
- **O que fazer, passo a passo:** [`TODO.md`](TODO.md)
- **O que já foi feito:** [`docs/historico/indice.md`](docs/historico/indice.md)
- **Contrato do servidor (fonte da verdade):** [`docs/auditoria-2026-10/01-contrato-servidor-atual.md`](docs/auditoria-2026-10/01-contrato-servidor-atual.md)
- **App legado:** branch `legado/abril-2026` / tag `legado-v1-final`; a documentação antiga está em `docs/legado/` (desatualizada).

## Como compilar

Requisitos:
- **JDK 17 ou mais novo.** O `java` do PATH pode ser 1.8; use o JDK que vem com o Android Studio.
- Android SDK instalado; o `local.properties` com `sdk.dir` é criado pelo Android Studio e não é versionado.

No Git Bash:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug
```

No PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

O APK de debug sai em `app/build/outputs/apk/debug/`.

## Servidor de desenvolvimento

- O servidor de dev escuta em **HTTPS na porta 443** (nginx), com certificado **mkcert**. Os caminhos relevantes ficam todos no mesmo endereço:

  | Caminho | Uso |
  |---|---|
  | `/api/` | REST |
  | `/ws/` | WebSocket (a barra final é obrigatória) |
  | `/webrtc/` | MediaMTX: WHIP/WHEP |
  | `/storage/` | Anexos (URLs assinadas) |

- No celular use o IP da máquina na rede, por exemplo `https://192.168.x.x/`, nunca `localhost`.
- Para o app confiar no certificado de dev, a CA do mkcert (`mkcert -CAROOT` → `rootCA.pem`) entra no `network_security_config` só do build de debug (etapa 0.5/1.7 do `TODO.md`).

## Documentação

| Pasta | Conteúdo |
|---|---|
| `docs/auditoria-2026-10/` | Auditoria, contrato do servidor, inventário do web, matriz de paridade, fila de correções, plano da nova base, pendências do servidor |
| `docs/historico/` | Registro de **toda** alteração, separado por fluxo |
| `docs/adr/` | Decisões de arquitetura |
| `docs/legado/` | Documentação e stashes da versão antiga (só referência histórica) |
