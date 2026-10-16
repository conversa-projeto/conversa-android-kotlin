# 2026-10-16 · 02 · Volumes do Docker renomeados e as variáveis antigas do shell

- **Fluxo:** Repositório e build
- **Tipo:** ambiente / documentação
- **Itens:** servidor `ef13c22` (volumes `conversa-postgres`/`conversa-minio`); `docs/desenvolvimento/emulador.md`
- **Branch:** `reescrita`
- **Commits:** `fc2c74a`

## Contexto
- **O servidor** (`ef13c22`) renomeou os volumes: `pgdata` → `conversa-postgres` e `minio` → `conversa-minio`. O `bin\renomear-volumes.bat` copia, confere e apaga os antigos.
- **A pessoa pediu para rodar o script** (2026-10-16).

## O que foi feito
- **Cópia de segurança:** com o backend parado, os dois volumes antigos foram copiados para arquivos `.tgz` (14 MB e 214 MB) no scratchpad da sessão, antes do script.
- **O `renomear-volumes.bat`:** `pgdata`: 1887 arquivos e 79.734.791 bytes nos dois lados; `minio`: 42 arquivos e 214.888.833 bytes. Os antigos foram apagados pelo script, depois da conferência.
- **O problema ao subir:** um `docker compose up -d --build` pelo shell subiu a API na base **`conversa`** (antiga, de abril, versão 35), e não na `conversa_dev`, e o nginx na porta 90.
  - **A causa:** variáveis de ambiente antigas nesta máquina (`CONVERSA_DATABASE=conversa`, `CONVERSA_PORTA=90`) passam por cima do `.env` do servidor.
  - **O efeito:** a API rodou as migrações até a 52 na base `conversa`, e o login das contas de teste deu 401.
- **Desfeito:**
  - a API e o nginx foram parados;
  - o volume `conversa-postgres` foi restaurado da cópia de segurança (1887 arquivos e 79.734.791 bytes, idêntico);
  - o compose subiu de novo com `env -u CONVERSA_DATABASE -u CONVERSA_PORTA`.
- **O guia `emulador.md`:** o comando para subir pelo shell, o aviso das variáveis e os nomes novos dos volumes.

## Como foi verificado
- **A API:** `docker inspect api` → `CONVERSA_DATABASE=conversa_dev`, os mesmos avisos de antes (FCM, transcritor, IA) e "Servidor iniciado".
- **O resto do ambiente:**
  - o nginx na 443 e o `https://localhost` com 200;
  - o login das contas de teste funciona;
  - a base `conversa` voltou à versão 35, como estava antes, e a `conversa_dev` está na 52;
  - o Qdrant subiu (serviço novo).

## Pendências
- **As cópias `.tgz`** ficam no scratchpad da sessão (temporário), até a pessoa conferir o ambiente.
