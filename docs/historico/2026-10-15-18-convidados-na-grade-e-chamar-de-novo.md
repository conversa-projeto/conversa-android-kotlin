# 2026-10-15 · 18 · Convidados fora da chamada na grade, com "Chamar de novo"

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.13 (linha 🆕 `769bd7d` dos convidados fora da chamada); FC-726; CHA-25
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O servidor** (`b8ea0c2`, contrato §9.14): `POST /chamada/chamar-novamente {chamada_id, usuario_id}` toca de novo para quem recusou ou deixou tocar sem atender, com a chamada em andamento, e devolve os dados da chamada.
  - O alvo recebe o WS 51. O lado do alvo já tocava de novo desde o histórico 2026-10-15-02.
  - Os outros participantes recebem o WS 57 `{acao:"participantes"}`.
- **O web** (`769bd7d`): na grade, depois de quem está na chamada, os convidados fora dela (`participantesAguardando`):
  - quem está tocando: "Chamando...";
  - quem recusou sem nunca ter entrado: "Não atendeu" e o botão "Chamar de novo", desligado até o servidor responder.

## O que foi feito
- **Rede e dados:**
  - `ConversaApi.chamarNovamente` (`POST chamada/chamar-novamente`, que devolve `DadosChamadaDto`);
  - `ChamadasRemotas.chamarNovamente(chamadaId, usuarioId): Chamada`.
- **`GerenciadorChamadas`:**
  - `chamarNovamente(usuario)`: vale chamando, conectando ou na chamada. Usa os dados da resposta. Se falhar, relê `GET /chamada/dados` e devolve o erro para a tela;
  - WS 57 `{acao:"participantes"}` relê os dados.
- **`convidadosAguardando`** (`EstadoChamada.kt`), como o `participantesAguardando` do web. Ficam fora:
  - eu;
  - quem já está conectado;
  - quem já entrou alguma vez.
- **Tela da chamada** (`TelaChamada.kt`):
  - **A grade:** depois dos participantes, o quadro `ConvidadoAguardando`. Ele mostra o avatar e o nome. Tocando: "Chamando…" piscando devagar. Senão: "Não atendeu" e o botão verde (`chamadaAtender`) "Chamar de novo", desligado até a resposta. A falha aparece no lugar do "Não atendeu", com o aviso de acessibilidade.
  - **Ninguém conectado ainda:** a grade aparece quando há mais de um convidado ou alguém que não atendeu. Numa ligação direta continua o avatar grande.
  - **O seletor "Grade / Destaque / Tela única"** passa a contar os convidados aguardando: aparece com 2 quadros ou mais na grade.
- **Textos** (do web): "Não atendeu", "Chamar de novo" e "Erro ao chamar de novo".
- **Testes:**
  - chamar de novo usa os dados devolvidos;
  - se falhar, relê os dados e devolve o erro;
  - sem chamada, não chama o servidor;
  - o WS 57 `participantes` relê os dados;
  - `convidadosAguardando` (quem entra e quem fica fora).

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (509 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A),** com o B e o C pela API (chamadas de vídeo 62 e 63, contas de teste, servidor de dev):
  - **A recusa do C:** o B ligou para A e C, o A atendeu "só assistindo" e o C recusou. A grade mostrou o B e o C com "Não atendeu" e "Chamar de novo".
  - **"Chamar de novo":** o toque fez o servidor pôr o C de volta em 1 (tocando), e o quadro passou a "Chamando…".
  - **Os avisos:** o C recusou de novo e o quadro voltou a "Não atendeu" (WS 53). Depois o B chamou o C de novo pela API, e o quadro voltou a "Chamando…" (WS 57 `participantes`).
  - **O seletor:** quem atende "só assistindo" abre em tela única. No primeiro teste o seletor não aparecia (só 1 participante conectado) e só um toque no quadro levava à grade. Depois da mudança, o seletor apareceu e "Grade" mostrou o C com "Chamar de novo".
  - **O fim:** o A desligou e o B saiu. A chamada ficou encerrada (status 4).

## Decisões
- **Os convidados aguardando só aparecem na grade, como no web.** No destaque e na tela única ficam só os conectados. Por isso o seletor passou a contar os convidados.
- **A falha vai para o quadro, não para um aviso geral.** Os avisos do gerenciador só aparecem nas telas de ligar, não na tela da chamada. O texto é o do servidor; sem ele, "Erro ao chamar de novo".

## Pendências
- **O caminho de falha** (o servidor recusar o "Chamar de novo") só foi coberto por teste unitário. Pelo app, a recusa é difícil de provocar, porque os avisos chegam antes do toque.
