# 2026-10-19 · 01 · Sincronização: ramal SIP pelo administrador, TURN por UDP e sugestões de pasta pela IA

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** linhas 🆕 no `TODO.md` (4.5, 6.1, 6.5, 6.11, 7.14, 8.5 e etapa 9); FC-225, FC-425, FC-729, FC-730 e FC-817, e notas na FC-702, FC-900 e FC-901; CON-18, ANX-19, CHA-27, SIP-06; S40–S42
- **Branch:** `reescrita`
- **Commits:** `540e79b`
- **Commits de origem** (pull `--ff-only` em 2026-10-19; três em cada repositório):
  - **Servidor (`ac710eb` → `b104db8`):**
    - `be17b8e` "Ramal SIP de cada pessoa configurado só pelo administrador (migração 057)";
    - `383eaaa` "TURN sobre UDP na 3478, com o TCP de reserva";
    - `b104db8` "Sugestão de pasta pela IA para as mensagens que ainda não estão em pasta".
  - **Web (`f351190` → `57bd9f0`):**
    - `8385b22` "Telefonia SIP configurada pelo administrador e chamadas funcionando como no telefone de teste";
    - `d244fc8` "Chamada já conectada enquanto toca, áudios em sequência e clique direito na seleção";
    - `57bd9f0` "Sugestões da IA no painel de pastas: confirmar ou ignorar cada uma".

## Contexto
- **O pedido** foi "faça pull e atualize a documentação e o TODO".
- **O repositório do Android** já estava igual ao `origin/reescrita` (o push dos 20 commits e a junção da apresentação já tinham sido feitos).
- **O Docker Desktop estava desligado:** a API não recarregou com o código novo, e a migração 057 não foi aplicada no `conversa_dev`. Não o liguei, para não mexer no banco de dev sem pedido; por isso o OpenAPI não foi baixado.
- **O `yarn.lock` não versionado do `conversa-web`** continua lá; o pull não mexeu nele.

## O que mudou (resumo)
- **Servidor: 119 → 122 rotas.**
  - **Ramal SIP pelo administrador** (migração 057):
    - permissão de administração nova `sip`, "Configurar a telefonia (SIP) das pessoas" (quem tinha `permissoes` ganhou); agora são **33 códigos**, 6 de administração e 27 de uso;
    - `GET`, `PUT` e `DELETE /sip/usuario`; as senhas nunca voltam (só `tem_senha` e `tem_senha_turn`), e vazio mantém a salva;
    - **saíram `PUT` e `PATCH /sip`**, em que cada um cadastrava o seu (hoje dão 404);
    - o ramal pode ter o TURN do próprio PABX (`turn_server`, `turn_user`, `turn_password`), que o `GET /sip` devolve ao dono.
  - **TURN por UDP:**
    - o coturn aceita UDP e TCP na 3478, e o `docker-compose` publica a porta UDP;
    - no `GET /ice`, o **`urls` de cada servidor virou lista**: o `turn:…:3478?transport=udp` primeiro e o TCP (ou o `turns:` da borda) de reserva.
  - **Sugestão de pasta pela IA:**
    - `POST /pastas/sugerir` (`{conversa_id, ignorar?}`) e `GET /pastas/sugestao?id=`, em segundo plano como o resumo, guardadas na memória por 30 min;
    - a IA olha as 80 mensagens mais recentes que não estão em pasta nenhuma e sugere no máximo uma pasta por mensagem;
    - nada entra na pasta sozinho: aceitar é o `PUT /pasta/mensagens` de sempre;
    - não há evento WebSocket: é consulta.
- **Web: 172 → 176 funcionalidades.**
  - **SIP-06:** a aba "Telefonia (SIP)" no painel de cada pessoa em Pessoas › Usuários. A aba Voip ficou só leitura (SIP-01), e a mídia do SIP vai pelo STUN e pelo TURN do PABX (SIP-05).
  - **CHA-27:** enquanto toca, quem recebe já prepara mídia, publicação (com as trilhas desligadas) e assinaturas; atender só liga as trilhas. Se o primeiro áudio do outro lado não chegou, toca `aguardando.mp3` (até 10 s).
  - **ANX-19:** o áudio que termina toca o próximo.
  - **CON-18:** "Sugerir pastas" no painel de pastas, com ✓/✕ por sugestão e "Aceitar todas"; as ignoradas ficam no navegador.
  - **Sem ID novo:** o vídeo da chamada passa a aparecer inteiro (`object-contain`, CHA-11), e o clique direito na seleção abre o menu (ENV-29, MSG-24, só computador).

## Achados no Android
- **Nada quebrou:**
  - o `ServidorIceDto.urls` já era `JsonElement` e o mapeador aceita texto ou lista (`DesserializacaoTest`, "ice com urls em texto ou lista");
  - o `sip` (`uso: false`) cai no grupo Administração dos Acessos com a descrição do servidor;
  - os campos novos do `GET /sip` são ignorados (`ignoreUnknownKeys`).
- **Código morto:** `incluirSip`/`alterarSip` do `ConversaApi` apontam para rotas que não existem mais (sem uso; anotado na FC-900 e na etapa 9).
- **Pré-conexão do web (CHA-27):**
  - no app, nada é obrigatório: o 54 continua sendo o único sinal de que a pessoa atendeu;
  - quando o web liga e o Android atende, o web toca o som de espera até o primeiro áudio do Android, porque o app faz `entrar` e só depois publica. Publicar antes do `entrar` é a melhoria opcional (FC-729);
  - fica um teste manual: a mesma conta no web e no celular, atendendo no celular no primeiro toque (o MediaMTX fica com a publicação mais nova).
- **TURN por UDP:** onde o UDP não tem caminho (emulador, Tailscale Funnel), a coleta sem trickle pode esperar os 5 s inteiros a cada WHIP/WHEP. Fica para medir (FC-702, TODO 6.1, S41).

## Documentos atualizados
- **`01`, `02` e `08`** (por um agente, conferindo no código do servidor):
  - **No `01`:**
    - as rotas 120–124 (`/sip/usuario` ×3, `/pastas/sugerir`, `/pastas/sugestao`) e as 63–64 riscadas;
    - o total do `rotas.ts` recontado por `grep`: 122 (119 em `ac710eb`, 120 depois dos dois primeiros commits);
    - seções novas §10.17.1 (sugestões) e §14.1 (ramal pelo administrador);
    - atualizadas §1.1–1.3 (porta UDP), §6.4, §9.5 (ICE), §13.1 (33 códigos), §13.2 (migração 057), §13.4, §13.5, §16, §18 e §20;
    - pegadinhas 106–118.
  - **No `02`:** a linha do tempo, as rotas que saíram e as novas, e o total.
  - **No `08`:** notas em S4, S7, S12, S21 e S28, e as **pendências novas S40–S42**:
    - **S40:** ramal pelo administrador — o dono não é avisado; a senha salva continua valendo quando o administrador troca domínio, WebSocket ou TURN (poderia apontar o app do dono para outro servidor); senhas em texto puro na auditoria; arestas de validação;
    - **S41:** o UDP do TURN é anunciado sempre, sem como desligar, e o Funnel só repassa TCP;
    - **S42:** sugestões — o "ignorar" fica só no cliente (o que foi ignorado no web volta no Android), sem limite nem cache de pedidos à IA, e mensagens de erro e conferências de membro.
- **`03`** (por um agente, conferindo os textos no web): os IDs novos SIP-06, CHA-27, ANX-19 e CON-18; os alterados (SIP-01…05, AUT-09, GER-09, CFG-01, CFG-08, CFG-11, CHA-01/03/04/05/07/10/11/12/25, CON-17, GER-08, ENV-29, MSG-24, ANX-10); a §5.10 nova (o protocolo da pré-conexão, com as linhas do `call.ts`); a §17 nova; e o total de 176.
- **`05`:**
  - as 4 linhas novas (⬜);
  - notas em SIP-01, SIP-05, CHA-11, CFG-08 e ENV-29;
  - o placar da nova base: **147 ✅, 3 🟡, 3 ⛔, 17 ⬜, 6 ➖ de 176**, recontado das linhas por script.
- **`06`:**
  - FC-225 (sugestões de pasta), FC-425 (áudios em sequência), FC-729 (chamada já conectada enquanto toca, com a nota de interoperabilidade), FC-730 (vídeo inteiro, avaliar) e FC-817 (telefonia de cada pessoa pelo administrador);
  - notas na FC-702 (`urls` em lista e o tempo com o UDP), FC-900 (sem autocadastro; tirar `incluirSip`/`alterarSip`) e FC-901 (TURN do PABX).
- **`00`:** o aviso, os HEADs e os totais.
- **`docs/desenvolvimento/emulador.md`:** o TURN aceita UDP e TCP; no emulador só o TCP passa pelo `adb reverse`; para o UDP valer no Docker local, recriar o coturn.
- **`TODO.md`:** linhas 🆕 em:
  - 4.5 (áudios em sequência);
  - 6.1 (medir o tempo com o UDP);
  - 6.5 (publicar antes do `entrar`, opcional, e o teste com a mesma conta);
  - 6.11 (vídeo inteiro);
  - 7.14 (sugestões de pasta);
  - 8.5 (telefonia de cada pessoa);
  - etapa 9 (SIP só leitura e o TURN do PABX).

## Como foi verificado
- `git log --stat` dos seis commits; pull `--ff-only` nos dois repositórios.
- O `urls` em lista conferido contra o `Mapeadores.kt` e o `DesserializacaoTest` do app; o agrupamento das permissões de administração, no `Sistema.kt`.
- O placar do doc 05 recontado das linhas da tabela por script (176 linhas, mais os 16 requisitos AND fora da contagem).

## Decisões
- **Não ligar o Docker** só para baixar o OpenAPI: aplicaria a migração 057 no banco de dev sem pedido.
- **Pré-conexão (CHA-27):** não copiar o preparo inteiro do web no app (câmera e microfone abertos enquanto toca); só a melhoria opcional de publicar antes do `entrar`.

## Pendências
- **`docs/contrato/openapi.json`:** baixar de novo de `https://localhost/api/docs/json` quando o Docker estiver no ar (122 rotas).
- **No ambiente de dev, quando o Docker voltar:**
  - a API aplica a migração 057 sozinha;
  - para o TURN por UDP, recriar o coturn: `env -u CONVERSA_DATABASE -u CONVERSA_PORTA docker compose up -d coturn`.
- **As FC novas:** FC-225, FC-425, FC-729 e FC-730 o app pode fazer; FC-817 depende da decisão sobre a administração no app.
