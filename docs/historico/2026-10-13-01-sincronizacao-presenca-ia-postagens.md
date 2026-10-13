# 2026-10-13 · 01 · Sincronização: presença, confirmação de leitura, IA, chat da chamada e postagens

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** `TODO.md` 8.7 e 8.8 (novas), 7.13 (nova) e linhas 🆕 em 2.6, 2.10, 2.11, 3.5, 3.8, 4.8, 5.4, 6.10, 6.11, 6.13, 7.2, 7.5, 8.2 e 8.5; FC-215–218, 316–317, 417–419, 521–524, 610, 724–728, 810–812; S17–S22
- **Branch:** `reescrita`
- **Commits:** `70d3385`
- **Commits de origem** (pull feito em 2026-10-13, `--ff-only`; as cópias locais estavam 5 commits atrás em cada repositório):
  - **Servidor (`779c8ed` → `12cf0cf`):**
    - `b8ea0c2` "Presença, confirmação de leitura, resumo por IA e chamar de novo" (migrações 037–041);
    - `ea7e974` "IA: resumo pede JSON por json_schema" (interno);
    - `ea02f57` "Adiciona sugestão de texto";
    - `a40fd47` "Chat da chamada reaproveita a conversa e vai junto da mensagem da ligação";
    - `12cf0cf` "Postagens em grupo com respostas embaixo da postagem" (migração 042).
  - **Web (`e8d82cb` → `d202c06`):** `769bd7d`, `05507a0`, `f798e9c`, `964ed72` e `d202c06`.
  - Também atualizado, sem efeito no Android: o `conversa-android-fmx` (app antigo em FMX), que estava 11 commits atrás.
  - Não foram tocados:
    - o `conversa-windows-fmx`, que não tem branch remota e tem trabalho de outra sessão;
    - as pastas "- Copia";
    - o `docs`, cujo remoto não dá acesso.

## Contexto
O pedido foi fazer os pulls e atualizar o projeto. Além dos docs, o ambiente de desenvolvimento foi atualizado:
- **API:** já rodava o código novo. Depois do reinício do Docker, aplicou as migrações até a 42.
- **Container `dev`:** foi religado, recebeu o `bun install --frozen-lockfile` (entrou o `emojibase-data`) e o Vite foi reiniciado. `https://localhost` volta a responder 200.

A análise dos diffs foi feita em paralelo por dois agentes, um para o servidor e um para o web. Cada detalhe dos docs 01 e 03 foi conferido no código.

## O que mudou (resumo)
- **Servidor:** 69 → **81 rotas**. As 12 novas:
  - `/contatos/presenca`, `/usuario/privacidade` e `/conversa/presentes`;
  - `/mensagem/confirmar`;
  - `/chamada/chamar-novamente`;
  - `/recursos`;
  - `/parametros/ia/testar`;
  - `POST` e `GET /conversa/resumo`;
  - `/ia/sugestao`;
  - `/postagem/respostas` e `/postagem/visualizar`.
- **WebSocket do servidor:**
  - tipos novos 8 (confirmação), 63 (presença na conversa) e 64 (postagem);
  - o 60 passa a ter `estado` (ativo/ausente/offline) e `visto_em`, e chega também com o próprio usuário;
  - o cliente pode mandar o 63 `{ativo, conversa_id}`.
- **Outras mudanças no servidor:**
  - grupos com imagem ou emoji;
  - `chamada` em `/conversas`;
  - o chat da chamada reaproveita a conversa de origem;
  - as respostas de postagem ficam fora de `/mensagens`, mas contam nas não lidas.
- **Web:**
  - os mesmos recursos;
  - a chamada de vídeo em aba própria;
  - o chat da ligação dentro da bolha e do histórico;
  - o seletor com todos os emojis;
  - **só o pacote de figurinhas Noto** (os antigos foram apagados);
  - a pesquisa na conversa nova e o destaque em faixa.
- **Funcionalidades: 137 → 150**, 13 novas:
  - presença: PRE-02, PRE-03;
  - conversas: CON-14, CON-15;
  - mensagens: MSG-21, MSG-22;
  - envio: ENV-24, ENV-25, ENV-26;
  - chamadas: CHA-25, CHA-26;
  - configurações e geral: CFG-09, GER-08.

## O que quebra no app novo (conferido no emulador e no código)
1. **Respostas de postagem prendem o contador** (FC-316, P0).
   - No emulador, o B fez uma postagem com resposta pela API.
   - A lista do A mostrou a resposta como prévia, que não aparece dentro da conversa, e 2 não lidas.
   - Depois de abrir o grupo, ficou **1 não lida para sempre**.
2. **Figurinhas** (FC-417, P0). O Android desenha as figurinhas a partir dos próprios assets (básico, rostos, coisas). O web agora só tem o pacote Noto (`noto/<nome>`, 55 animações, 3,7 MB). Nos dois sentidos, a figurinha vira o quadro "Figurinha".
3. **"Chamar de novo" não toca** (FC-724, P0). O servidor manda outro WS 51 para uma chamada já recusada, e o `GerenciadorChamadas` ignora ids "encerrados" (`if (id in encerradas) return`).
4. **Chat da chamada reaproveitado** (FC-725, P1). Pode ser a própria conversa direta. Quem não é membro dela recebe 403 ao abrir; isso é a S20 no servidor.

O que continua funcionando com o servidor novo foi conferido no emulador, conta A:
- o login, depois que o token de 12 h venceu ("Sessão expirada", tratada certo);
- a lista;
- receber mensagem;
- enviar mensagem (`PUT /mensagem` 200);
- marcar como lida.

O JSON ignora as chaves novas, então os campos novos não quebram nada.

## Documentos atualizados (marcados com 🆕 e o commit)
- `01-contrato-servidor-atual.md`:
  - cabeçalho;
  - §4 com 81 rotas e as linhas 70–81;
  - §5.1;
  - §6.2, §6.4–§6.8 (WebSocket);
  - §7.1;
  - §9.2, §9.3, §9.7, §9.8, §9.11, §9.12 e a nova §9.14;
  - §10.1–§10.4, §10.6, §10.10, §10.11 e as novas §10.14 (confirmação) e §10.15 (postagens);
  - §11 com a nova §11.9;
  - §12;
  - §13.2 e as novas §13.3–§13.6 (recursos, IA, resumo, sugestão);
  - §15, reescrita;
  - §16;
  - checklist 18–24;
  - pegadinhas 13, 30, 44 e 46 revistas e 51–65 novas;
  - §20.
- `02-mudancas-servidor-desde-abril.md`:
  - linhas 26–31 da linha do tempo;
  - as 12 rotas novas;
  - WS 8, 63, 64 e 60.
- `03-inventario-web.md`:
  - cabeçalho e contagem (150);
  - os 13 IDs novos;
  - os IDs alterados: PRE-01, CON-01/02/08/09, MSG-06/07/13, ENV-01/03/04/12/17, ANX-12, PES-01, CHA-02/11/13/15/16/17/18/22, NOT-02, CFG-01/07 e GER-07;
  - a ordem do PES-01 corrigida ("do mais antigo ao mais novo");
  - §1.3 com as datas que faltavam;
  - §4.1, §4.2, §6 e §7;
  - a nova §13 com o resumo e o que muda para o Android.
- `05-matriz-paridade.md`:
  - as 13 linhas novas (⬜ no legado e na nova base);
  - Nova base de CON-01, PRE-01, MSG-06, ENV-04, ENV-17, ANX-12, PES-01, CHA-18 e CFG-07 rebaixada de ✅ para 🟡;
  - notas em CON-08, MSG-13, CHA-16, CHA-22 e NOT-02;
  - os dois placares **recalculados a partir das tabelas**:
    - nova base: 112 ✅, 11 🟡, 2 ⛔, 22 ⬜, 3 ➖, de 150;
    - legado: 2 ✅, 26 🟡, 16 🔴, 92 ⬜, 14 ➖.
- `06-fila-de-correcoes.md`:
  - FC-215–218 (presença, grupo, seção "Chamadas");
  - FC-316–317 (postagens);
  - FC-417–419 (Noto, emojis, recursos);
  - FC-521–524 (confirmação, criar postagem, sugestão, pesquisa);
  - FC-610 (notificação de resposta);
  - FC-724–728 (chamar de novo, chat reaproveitado, convidados, chat da ligação, etapas do vídeo);
  - FC-810–812 (privacidade, resumo, IA no Sistema).
- `08-pendencias-servidor.md`, conferidas no código:
  - S17: respostas fora do fluxo, mas em `/conversas` e `/mensagens/novas`;
  - S18: push bloqueado por qualquer socket aberto, mesmo ausente;
  - S19: `avatar_anexo_id` sem validação dá 500;
  - S20: quem não é membro fica de fora do chat reaproveitado;
  - S21: resumo só na memória;
  - S22: o 63 aceita a conversa de outros;
  - notas em S5 e S10.
- `00-LEIAME.md`: nota de atualização, HEADs e totais (81 rotas, 150 funcionalidades).
- `docs/contrato/openapi.json`: baixado de novo de `https://localhost/api/docs/json` (`12cf0cf`).
  - 70 → 82 entradas (contando as 2 públicas): as 12 novas;
  - alteradas: `PATCH /usuario`, `PATCH /conversa`, `PUT /mensagem` e `PATCH /parametros`;
  - nenhuma removida.
- `TODO.md`:
  - **8.7 nova: "O que o update de 2026-10-13 quebrou — fazer primeiro"** (os 4 itens acima e o teste);
  - 7.13 nova (postagens);
  - 8.8 nova (IA; o teste depende de configurar a IA no servidor de dev, **o que pede autorização**);
  - linhas 🆕 nas seções de cada funcionalidade.

## Como foi verificado
- `git log`/`git diff` dos dois repositórios antes do pull; pull `--ff-only` sem conflito.
- O OpenAPI novo foi comparado com o antigo por script: rotas novas, alteradas e removidas.
- Os placares do doc 05 foram recalculados por script a partir das próprias tabelas.
- No emulador, com o APK atual contra o servidor novo: o básico funciona; a postagem prende o contador (reproduzido).
- No código: figurinhas por assets e WS 51 ignorado para chamadas encerradas.

## Decisões
- **Os IDs novos** seguem a sugestão da análise do web, por grupo. A IA ficou em CON-14, ENV-25 e GER-08, sem um grupo próprio.
- **O que quebra vem antes** (TODO 8.7, P0). As funcionalidades novas entram nas seções de cada área, como nas sincronizações anteriores.
- **Figurinhas antigas:** manter os assets antigos no Android só para desenhar mensagens antigas. O web as mostra como texto; o Android pode fazer melhor sem custo.
- **Sistema › IA (CFG-07, FC-812):** fica marcado "avaliar se fica só no web", por ser uso de administrador.

## Pendências
- Fazer o 8.7 (P0) antes de qualquer outra coisa.
- **Testes da IA:** pedir antes de configurar o servidor de IA no dev, porque mexe nos parâmetros do servidor.
- Uma mensagem de teste digitada pelo adb saiu embaralhada no "Grupo criado pelo B" ("Cmaiiiae: nid eoap"), por causa do teclado do emulador. Não é defeito do app.
