# 2026-10-09 · 10 · Matriz de paridade com a nova base

- **Fluxo:** Planejamento e documentação
- **Tipo:** documentação
- **Itens:** `TODO.md` "Revisão periódica" (atualizar o doc 05 e marcar o que foi feito); doc 05
- **Branch:** `reescrita`
- **Commits:** `3babed2`

## Contexto
A matriz de paridade web × Android (`docs/auditoria-2026-10/05-matriz-paridade.md`) tinha só a situação do app **legado**, da auditoria de 2026-10-06. O andamento da nova base, com as etapas 1 a 8 prontas, só aparecia no `TODO.md`. Por isso a matriz ainda dizia, por exemplo, ⬜ para o perfil, a pesquisa e as configurações.

## O que foi feito
- **Coluna nova "Nova base"** em todas as tabelas, com 137 funcionalidades, a linha "Gravação" e os 16 requisitos AND. A coluna "Android" virou "Legado" e foi mantida como estava, como histórico.
  - Cada célula traz o símbolo, a seção do TODO e, quando há, o commit.
  - Uma nota "falta…" indica teste que o emulador não permite: TalkBack, Bluetooth, CA do mkcert.
- **Legenda da nova base:** ✅ pronto, 🟡 em parte, ⛔ bloqueado fora do app, ⬜ por fazer, ➖ não se aplica.
- **Placar da nova base:**
  - 137 funcionalidades: **121 ✅, 2 🟡, 2 ⛔, 9 ⬜, 3 ➖**;
  - AND: 13 ✅, 2 🟡, 1 ⛔;
  - uma "leitura" do que bloqueia e do que falta.
- **Placar do legado corrigido:** ainda não contava o ENV-23, que entrou em 2026-10-08 (⬜ no legado). Passou de 136 para 137, e ⬜ de 78 para 79.
- **`TODO.md`:** 16 linhas antigas das etapas 2 a 6 diziam "entra na etapa 4/5/6/7/8" e ficaram abertas, mas as etapas seguintes as cumpriram. Cada uma foi conferida no código e fechada com o commit em que ficou pronta:
  - **logout:** encerra a chamada (`ceeec72`) e limpa o cache de áudio (`c5cefbb`);
  - **navegação:** a rota do perfil (`143d8d6`); a lista atualiza ao enviar (`0bdfe08`);
  - **notificação:** some ao arquivar ou ao zerar as não lidas (`199ce1b`); arquivada não notifica (`199ce1b`);
  - **cabeçalho e campo do chat:** avatar → perfil (`4d53ba8`); voz e vídeo (`cf43b1f`) e pesquisa (`28cef66`); anexo (`c5cefbb`) e microfone (`399bae5`);
  - **mensagens:** oculta sem menu e fora da galeria (`52d6d03`); ligar de novo pela bolha (`cf43b1f`); ordem encaminhados → texto → figurinha → arquivos (`61d6a2c`);
  - **chamada e mídia:** o áudio para ao começar a chamada (`5837aec`); Opus 64/128 kbps (`705ee2e`);
  - **"carregando seguintes":** não se aplica, porque o salto traz o caminho todo (`2a4cc59`).
  - Na "Revisão periódica", as duas linhas recorrentes ganharam a data da última vez.

## Como foi verificado
- **Cruzamento automático:** um script (no scratchpad, fora do repositório) cruzou cada ID da matriz com as seções do TODO. A ligação vem pelo ID no título ou na linha, ou pela FC do doc 06.
  - Saiu ✅ só quando todas as linhas ligadas estavam marcadas.
  - O commit só entrou quando a maior parte das linhas apontava para ele. Nas etapas 1 a 3, as linhas não têm commit, e a célula cita só a seção.
- **Casos à mão:** os 45 casos restantes foram decididos um a um (parciais, bloqueados, sem linha no TODO, AND). Os commits escolhidos foram conferidos pelo assunto (`git log`).
- **Código:** as 16 linhas antigas foram conferidas no código antes de fechar, e o commit de cada uma saiu do `git log -S`.
- **Tabelas:** todas as linhas ficaram com o mesmo número de colunas: 8 nos grupos e 6 nos AND.

## Decisões
- **✅ com nota:** se só falta um teste que o emulador não permite (aparelho real, TalkBack, Bluetooth, mkcert), a célula fica ✅ com a nota, e não 🟡. A funcionalidade está feita.
- **Placar da nova base:** usa ⛔ no lugar do 🔴 do legado. Na nova base nada "quebra contra o servidor"; o que não anda está bloqueado de fora.

## Pendências
- **Atualizar a matriz a cada etapa:** fica na "Revisão periódica" do TODO.
- **O que segue aberto** (está na própria matriz):
  - o push (Firebase, 5.1) e, com ele, segundo plano, app fechado e retomada;
  - a decisão do campo rico (FC-416);
  - o SIP e o compartilhar tela (etapa 9);
  - o avatar na notificação;
  - remover outro membro do grupo, que o servidor não permite;
  - o teste de migração do Room;
  - os testes em aparelho real.
