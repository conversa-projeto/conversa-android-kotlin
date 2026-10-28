# 2026-10-27 · 03 · Chamada: "Conectando o vídeo…", quem está escolhendo a tela e o aviso de quem já vê

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.9 e 6.13 (as linhas 🆕 `edb1785`) e o teste 🆕 `6117545`/`c5487a9` do 6.x; FC-731; FC-729; CHA-11, CHA-17 e CHA-27
- **Branch:** `reescrita`
- **Commits:** `5b4032f`

## Contexto
O web `edb1785` (sincronização 2026-10-27-02) trouxe três coisas que o app precisa acompanhar:
- "Conectando o vídeo..." no tile até o primeiro quadro;
- o sinal 57 `{acao:'escolhendo', ativa}`, com "Ana está escolhendo a tela...";
- o sinal 57 `{acao:'vendo', alvo}`, que quem assiste manda quando a tela aparece.

Sem o `vendo`, o web de quem compartilha mostrava o celular como "(conectando...)" o tempo todo.

## O que foi feito
- **`GerenciadorChamadas`:**
  - `EstadoChamada.escolhendoTela`: entra com `escolhendo` `ativa:true`; sai com `ativa:false`, com o aviso `tela` (que o substitui) ou quando a pessoa sai da chamada;
  - `avisarTelaVista(alvo)` manda pelo `RealtimeClient.enviarSinal` (que existia e nunca era usado) `{acao:"vendo", alvo}`. Só manda com a tela de `alvo` ativa, uma vez por compartilhamento, e de novo depois que ela para e volta; com o socket fechado, tenta na próxima vez.
- **`TelaChamada`:**
  - **"Conectando o vídeo…"** por cima do vídeo até o primeiro quadro (o `onFirstFrameRendered` do `SurfaceViewRenderer`, que estava vazio no `VideoDaTrilha`), com as cores do "Conectando…" do quadro sem vídeo;
  - **"Fulano está escolhendo a tela…"** ("… estão …" com mais de um; sem nome, "Participante") no topo, na mesma pílula com o círculo girando do aviso da etapa do vídeo (FC-728), um embaixo do outro, como o web;
  - **tela vista:** com a pessoa compartilhando, quando o quadro muda de tamanho depois do aviso `tela` (era a câmera, ou nada) ou ~1 s depois com quadros, o app chama `avisarTelaVista` (a mesma regra do `VideoRemoto.vue`);
  - com a miniatura local na tela, os avisos do topo desviam dela. O vídeo local é desenhado numa camada própria, por cima de tudo, e cobria o fim do texto.
- **Textos** (`feature/chamada`): `conectando_video`, `escolhendo_tela_um`, `escolhendo_tela_varios` e `participante_sem_nome`. As reticências usam "…", como os outros textos do app que vêm do web.
- **Testes (`GerenciadorChamadasTest`):**
  - o "escolhendo" entra, sai com o aviso da tela, com `ativa:false` e quando a pessoa sai;
  - o `vendo` não vai sem a tela, vai uma vez por compartilhamento e de novo depois de parar e voltar.

## Como foi verificado
- `:feature:chamada:testDebugUnitTest` (78 testes), `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` (só `AndroidGradlePluginVersion` e `UseKtx`), `ktlintCheck` e `:app:compileDebugAndroidTestKotlin`.
- **No emulador, com o C no web** (Chrome de teste com câmera falsa e a escolha de tela automática):
  - **escolhendo:** com o web mandando `{acao:'escolhendo', ativa:true}`, o app mostrou "Teste Android C está escolhendo a tela…" no topo; depois do ajuste, ao lado da miniatura e em duas linhas;
  - **vendo:** o C compartilhou a tela pelo web (`compartilharTela`). O aviso sumiu com o `tela`, e o web passou a ter o A em "Vendo sua tela" (`espectadoresTela`: `{usuarioId: 1, vendo: true}`);
  - **conectando:** logo depois de atender uma chamada de vídeo, o tile do C mostrou "Conectando o vídeo..." antes do primeiro quadro. O branco sumia no cinza do tema claro, e a cor foi trocada pela do "Conectando…" do mesmo tile.
- **FC-729 de novo, com o web `6117545`** (chamada 80, de vídeo):
  - o app publicou às 03:42:01 e o web já lia a publicação às 03:42:02, antes do 54;
  - o som de espera do web tocou **1,24 s** (eram ~5,3 s com o web anterior, histórico 2026-10-27-01). O que sobra é o primeiro áudio de verdade levar esse tempo para chegar (microfone do emulador e o TURN por TCP).

## Ambiente (o emulador sem espaço)
- O emulador estava com 5% livres (317 MB de 6 GB), e a instalação falhava com "not enough space" / `INSTALL_FAILED_INSUFFICIENT_STORAGE`. A sessão do app tinha caído (tela de login).
- Para liberar espaço:
  - o app de debug foi desinstalado e instalado de novo (perde-se só o endereço do servidor e o cache local);
  - o `video-teste-200mb.mp4` de teste (pasta Download, de 2026-10-07) foi copiado para o scratchpad do PC antes de sair do emulador;
  - saiu também um `app-debug.apk` de 112 MB que uma instalação do Gradle que falhou deixou em `/data/local/tmp`.
- Instalei um APK só com a biblioteca nativa x86_64 (112 MB em vez de 144 MB), montado à parte e assinado com a chave de debug, sem mudar o projeto. O servidor e o login foram refeitos, e as permissões do próprio app foram aceitas (notificações, câmera e microfone). A isenção de bateria ficou recusada.
- A nota "Emulador sem espaço" entrou no `emulador.md`.

## Decisões
- **Sem o "parou de chegar":** o web mostra "Conectando o vídeo..." de novo quando a trilha fica muda (`mute`), mas o libwebrtc do Android não avisa isso para a trilha remota. Fica só até o primeiro quadro.
- **O lado de quem compartilha** (mandar `escolhendo` e mostrar "Vendo sua tela:") fica para a etapa 9: o app ainda não compartilha a tela.

## Pendências
- Aumentar o armazenamento do emulador (ou limpar o que os apps do Google ocupam): com ~500 MB livres, reinstalar por cima volta a falhar. Fica a critério da pessoa.
