# 2026-10-23 · 05 · Ao atender, publicar antes do `entrar`

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.x (a linha 🆕 `d244fc8` "(opcional) Publicar antes do `POST /chamada/entrar`"); FC-729; CHA-27
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
No web (`d244fc8`), quem liga pré-assina quem foi chamado. Quando alguém atende (o 54) e ainda não chegou áudio dele, o web toca um som de espera por até 10 s. O app fazia `POST /chamada/entrar` e só depois publicava, então o web que ligava para o celular sempre tocava esse som até a publicação do app aparecer. A FC-729 marcava como melhoria opcional publicar antes do `entrar`, sem copiar o preparo inteiro durante o toque (abrir câmera e microfone enquanto toca).

## O que foi feito
- **`GerenciadorChamadas.atender`:**
  - abre a mídia, começa a publicar e espera a publicação por até 2 s (`ESPERA_PUBLICAR_ANTES_MS`); só então faz o `entrar`;
  - depois do `entrar`, espera a publicação terminar e fica ativa;
  - o `entrar` continua sendo o sinal de que a pessoa atendeu (o 54). Se ele falhar, ou se chegar o 52, o `encerrar()` desfaz a publicação, como já fazia com as outras.
- **Testes (`GerenciadorChamadasTest`):**
  - o atender publica antes de o `entrar` terminar;
  - com a publicação lenta, o `entrar` sai depois de 2 s e a chamada só fica ativa quando a publicação termina;
  - o 52 durante o atender encerra e desfaz a publicação.

## Como foi verificado
- `:feature:chamada:testDebugUnitTest` (76 testes).
- **No emulador, com o proxy de medição:** o C ligou para o A pela API (chamada 75, contas de teste) e o A atendeu arrastando o botão.
  - `GET /ice` → **`POST …/call-75-u-1/whip` em 90,782 s** → **`POST /chamada/entrar` em 90,807 s**;
  - o MediaMTX mostrou a publicação conectada ("is publishing to path 'call-75-u-1'");
  - depois, o A saiu pelo app e o C pela API (status 4).
- **Sem o web** (não estava rodando), não deu para ver o som de espera sumir do lado dele. A ordem acima é o que o protocolo do web pede (doc 03 §5.10).

## Decisões
- **2 s de espera no máximo:** com o UDP sem caminho, a coleta do ICE leva ~1 s (2026-10-22-07). Mais que isso deixaria o `entrar` (e o fim do toque nos outros aparelhos da pessoa) esperando demais.
- **CHA-27 fica 🟡:** o app atende já publicando, mas não prepara a chamada durante o toque como o web. Isso abriria câmera e microfone com o telefone tocando, o que exige o serviço em primeiro plano do Android 14+.

## Pendências
- O teste com o web: a mesma conta no web e no celular, e o web ligando para o celular sem o som de espera.
