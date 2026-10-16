# 2026-10-15 · 19 · Etapas ao ligar o vídeo e troca sem intervalo

- **Fluxo:** Chamadas (etapa 6)
- **Tipo:** código
- **Itens:** `TODO.md` 6.11 (linha 🆕 `769bd7d` das etapas do vídeo); FC-728; CHA-16
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
- **O web** (`769bd7d`, `call.ts`): ao ligar o vídeo numa chamada de áudio, a janela mostra a etapa: "Abrindo a câmera...", "Enviando o vídeo..." e "Recebendo o vídeo dos participantes...". E troca sem cortar:
  - publica a transmissão nova no mesmo caminho antes de fechar a antiga (o MediaMTX troca o publicador, `overridePublisher`, padrão ligado no dev);
  - troca cada assinatura só quando a nova está pronta.
- **O app,** antes: fechava a publicação e as assinaturas e só depois abria as novas.

## O que foi feito
- **`MidiaChamada.ativarVideo(transmitir, aoEtapa)`** e o enum `EtapaVideo` (CAMERA, ENVIANDO, RECEBENDO).
- **`MidiaWebRtc`:**
  - **`trocarPublicacao`:** a publicação antiga fica em `publicacaoAnterior` (os avisos dela são ignorados) até a nova entrar. Se a troca for recusada, descarta a antiga e publica.
  - **`assinar(…, trocar = true)`:** a assinatura antiga, se conectada, continua tocando em `substituidas` até a nova conectar ou falhar. Enquanto isso, a tela mostra a antiga.
  - **O `ativarVideo`:**
    - avisa as etapas;
    - só republica com câmera (como o web);
    - espera as assinaturas novas até 10 s.
  - **`reassinar` (WS 56)** e o `sincronizar` também trocam sem intervalo.
- **Dois defeitos achados no teste:**
  - **Uma corrida:** o `sincronizar` da cada 4 s, pedido ainda em áudio no meio da ligação do vídeo, desfazia o `comVideo`. As assinaturas novas podiam sair só com áudio. Agora a mídia não volta de vídeo para áudio.
  - **O quadro preto:** o `sdpEnviaVideo` dizia que vinha vídeo de quem publica só áudio, e o quadro ficava preto em vez do avatar. O MediaMTX responde `m=video … a=sendonly` mesmo sem vídeo no caminho; a diferença é a trilha (`a=msid`/`a=ssrc`), que agora é exigida.
- **`GerenciadorChamadas`:** `EstadoChamada.etapaVideo`. Começa em "Abrindo a câmera" no toque, segue as etapas da mídia (passadas para a vez do gerenciador) e some no fim ou ao encerrar.
- **Tela:** no topo da área do vídeo, a pílula com o indicador girando e o texto (os do web), com aviso de acessibilidade.
- **Testes:**
  - as etapas aparecem e somem;
  - desligar no meio não deixa etapa para a próxima chamada;
  - `sdpEnviaVideo` com a resposta sem trilha.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test` (511 testes), `:app:lintDebug` (só os 3 avisos de sempre), `ktlintCheck` e `textos-repetidos.mjs`. Passou.
- **No emulador (A),** contra o web (B, no Chrome de teste com câmera e microfone falsos), em chamadas de áudio 64–67 que o A passou para vídeo:
  - **As etapas:** capturas a cada ~0,4 s mostraram "Abrindo a câmera…", "Enviando o vídeo…" e "Recebendo o vídeo dos participantes…". No fim, "Vídeo", com a miniatura "Você".
  - **A troca no MediaMTX** (chamada 65):
    - a publicação nova do A (`9332ccb0`) foi criada antes de a antiga (`aa39c74a`) sair, por troca de publicador;
    - a assinatura nova do A ao B (`33b54b8f`) entrou antes de a antiga (`bcaa6eda`) fechar;
    - nenhuma "troca recusada" no log do app.
  - **O quadro do B:** antes da correção, ficava preto enquanto o B só tinha áudio. Depois (chamada 67), aparece o avatar com o anel de fala até o B publicar o vídeo.
  - **A resposta do MediaMTX** foi conferida com uma oferta WHEP de teste ao MediaMTX local: num caminho só com áudio vem `a=sendonly` sem `a=msid`/`a=ssrc`; com vídeo, vêm as duas.
- **O ambiente:**
  - o contêiner `dev` (Vite) estava parado e foi ligado com o Vite, como o `iniciar-desenvolvimento.bat`;
  - o B entrou no web pela sessão de um login na API, posta no `localStorage` do Chrome de teste (`docs/desenvolvimento/emulador.md`).

## Decisões
- **As etapas chegam da mídia por uma função.** Ela pode rodar fora da vez do gerenciador, então cada etapa é passada para a vez dele e só vale enquanto o vídeo está ligando, na mesma chamada.
- **A espera das assinaturas novas tem limite de 10 s.** Sem ela, a etapa "Recebendo" ficaria até o fim das tentativas (40 s) quando alguém não publica.

## Pendências
- **Quem republica sem avisar derruba as leituras dos outros por uns 9 s.** O MediaMTX fecha os leitores quando o publicador muda, e quem lia só percebe pela queda da conexão. Aconteceu com o web respondendo "Apenas assistir" ao 56, que republica sem mandar outro 56. Não é desta mudança; não foi conferido como fica no web.
