# 2026-10-27 · 02 · Sincronização: atender sem atraso, conectando o vídeo, escolhendo e vendo a tela

- **Fluxo:** Sincronização com servidor e web
- **Tipo:** documentação
- **Itens:** FC-731 (novo); nota na FC-729; CHA-04, CHA-11, CHA-17 e CHA-27; linhas 🆕 no `TODO.md` (6.9, 6.13 e 6.x)
- **Branch:** `reescrita`
- **Commits:** `87c9753`
- **Commits de origem** (pull `--ff-only` em 2026-10-27; o servidor não mudou desde `331083b`):
  - **Web (`17c49a4` → `1dab7dd`):**
    - `6117545` (2026-10-25) "Chamada sem atraso ao atender: a voz sai no clique, dos dois lados";
    - `c5487a9` (2026-10-26) "Atender entra na chamada e troca a tela no clique, sem esperar o preparo";
    - `edb1785` (2026-10-26) "Vídeo na chamada: conectando no tile, quem está escolhendo a tela e quem já vê";
    - `1dab7dd` (2026-10-27) "Textos legíveis nos tiles da chamada no tema escuro".

## Contexto
- **O pedido** foi "faça pull, atualize a documentação e faça as atualizações do Kotlin".
- O repositório do Android estava em dia com o remoto (os 31 commits da semana passada já tinham sido enviados).
- O `yarn.lock` não versionado do `conversa-web` continua lá; o pull não mexeu nele.

## O que mudou (resumo)
- **Atender (CHA-04, CHA-27):**
  - quem atende pelo web entra na chamada no clique (`entrar` e `ativa` na hora) e prepara depois; o clique já tira do mudo quem ligou e liga o microfone;
  - quem liga toca a pré-assinatura de quem não atendeu **sem mudo** (só silêncio até atender);
  - o atender espera a pré-assinatura que ainda está conectando (até 15 s) em vez de começar outra.
- **"Conectando o vídeo..." (CHA-11):** no tile, até o primeiro quadro e quando a trilha para.
- **Tela compartilhada (CHA-17):**
  - sinal novo `{acao:'escolhendo', ativa}`: os outros veem "Ana está escolhendo a tela...";
  - sinal novo `{acao:'vendo', alvo}`: quem assiste avisa quando a tela aparece, e quem compartilha vê "Vendo sua tela:" com ✓ ou "(conectando...)".
- **Escuro (`1dab7dd`):** os textos dos tiles com `text-chamada-300`; é cor do web, não se aplica ao app.
- **Sem rota nova nem funcionalidade nova:** continuam 178. O servidor só repassa o sinal 57.

## Achados no Android
- **Nada quebra:** o `GerenciadorChamadas.sinal` ignora as ações que não conhece.
- **O app nunca manda sinal 57:** o `RealtimeClient.enviarSinal` existe, mas ninguém chama. Sem o `vendo`, o web de quem compartilha mostra o celular como "(conectando...)" o tempo todo.
- **Tile:** o `VideoDaTrilha` tem o `onFirstFrameRendered` vazio, e o `Participante` só mostra "Conectando" quando ainda não há trilha de vídeo. Com a trilha e sem quadro, fica a área escura.
- **FC-729:** o web `6117545` mexeu exatamente na pré-assinatura de quem liga, que em 2026-10-23 desistia ou não conectava. O teste será repetido.

## Documentos atualizados
- **`01`** (§9.11): as ações `escolhendo` e `vendo` do sinal 57.
- **`03`:**
  - o cabeçalho e o sumário;
  - CHA-04 (entrar no clique), CHA-27 (sem mudo do lado de quem liga e esperar a pré-assinatura), CHA-11 ("Conectando o vídeo..." e as cores no escuro) e CHA-17 (escolhendo e vendo);
  - a **§20 nova**, com o que muda para o Android.
- **`05`:**
  - o aviso da atualização;
  - CHA-11 e CHA-17 voltam a 🟡 (FC-731);
  - nota em CHA-27;
  - o placar: **149 ✅, 6 🟡, 3 ⛔, 14 ⬜, 6 ➖ de 178**, recontado das linhas por script;
  - a leitura.
- **`06`:** FC-731 (tile, escolhendo e vendo; o lado de quem compartilha fica para a etapa 9); nota na FC-729.
- **`00`:** o aviso.
- **`TODO.md`:** linhas 🆕 em 6.9 (tile), 6.13 (escolhendo e vendo) e 6.x (repetir o teste da FC-729).
- **`08`:** nada (o servidor não mudou).

## Como foi verificado
- `git show` dos quatro commits; pull `--ff-only`.
- O código do app conferido: `GerenciadorChamadas.sinal`, `RealtimeClient.enviarSinal` (sem uso), `VideoDaTrilha` e `Participante`.
- O placar do doc 05 recontado das 178 linhas da tabela por script.

## Pendências
- Implementar a FC-731 (a seguir) e repetir o teste da FC-729 com o web.
