# Matriz de testes manuais — chamadas (FC-955, TODO 6.14)

Testes de chamada que dependem de aparelho, rede ou situação real. Os testes unitários da máquina de estados (`GerenciadorChamadasTest`) cobrem as regras; aqui fica o que só se vê rodando.

**Legenda:**
- ✅ passou;
- ⏳ falta testar;
- ⛔ bloqueado;
- **Onde:**
  - **E**: emulador contra o servidor de desenvolvimento, com o outro lado no web (Chrome de teste com câmera e microfone falsos; guia em `docs/desenvolvimento/emulador.md`);
  - **A**: aparelho de verdade.

Atualize a linha (data, versão, onde e o que aconteceu) a cada rodada.

## Rede

| Cenário | Onde | Situação | Observação |
|---|---|---|---|
| Wi-Fi ↔ Wi-Fi (mesma rede) | E | ✅ 2026-10-08 | Mídia pelo TURN (TCP 3478) do servidor de desenvolvimento |
| Wi-Fi ↔ 4G | A | ⏳ | Precisa de dois aparelhos e do servidor acessível pela internet (TURN em TLS 443/8443) |
| 4G ↔ 4G | A | ⏳ | Idem |
| Trocar de rede durante a chamada (Wi-Fi → 4G) | A | ⏳ | A conexão que cai é refeita sozinha (6.1); conferir quanto tempo leva |

## Estado do app

| Cenário | Onde | Situação | Observação |
|---|---|---|---|
| App aberto: recebe e atende | E | ✅ 2026-10-08 | A tela de chamada recebida abre sozinha |
| App aberto: recebe e atende arrastando o botão | A | ✅ 2026-10-09 | Galaxy A25 (Android 16) pela depuração por Wi-Fi, contra o web (conta C): tocou, atendeu arrastando o botão verde; o gesto foi ajustado (limiar de 140 dp) e aprovado |
| App em segundo plano (até 10 s, WebSocket ainda ligado) | E | ✅ 2026-10-08 | Notificação CallStyle com Atender/Recusar; "Atender" abre já atendendo |
| App em segundo plano há mais de 10 s / fechado | A | ⛔ | Depende do push de chamada (FCM, 5.1) e do S1 no servidor |
| Tela apagada | E | ✅ 2026-10-08 | A tela acende já na "Chamada recebida" (tela cheia) |
| Tela bloqueada com PIN | A | ⏳ | No emulador não há PIN (não se altera a segurança do aparelho de teste) |
| Ninguém atende em 30 s | E | ✅ 2026-10-08 | Para de tocar, some a notificação, vira "Perdida" no histórico |
| Atender no web com o celular tocando | E | ✅ 2026-10-08 | O A atendeu no web; o celular parou de tocar e a chamada saiu do Telecom |
| Recusar em outro aparelho com o celular tocando | E | ⏳ | Mesma regra do item acima (WS 53 com o meu id); coberto por teste unitário |

## Áudio e sistema

| Cenário | Onde | Situação | Observação |
|---|---|---|---|
| Ligação de celular durante a chamada | E | ✅ 2026-10-08 | Simulada pelo emulador e atendida no discador: a do app ficou em espera; "Retomar" voltou |
| Fone Bluetooth: atender e desligar pelo botão | A | ⏳ | O emulador não tem fone Bluetooth |
| Fone com fio | A | ⏳ | O seletor "Áudio saída" deve listar "Fone com fio" |
| Fone do aparelho ↔ alto-falante | A | ⏳ | O emulador só oferece alto-falante |
| Sensor de proximidade (tela apaga no ouvido) | A | ⏳ | Só com o áudio no fone do aparelho |
| Música tocando quando chega a chamada | A | ⏳ | O Telecom pausa (foco de áudio) |
| TalkBack: atender e desligar | A | ⏳ | Não se liga o TalkBack no emulador de teste (configuração do aparelho) |
| 🆕 Qualidade das chamadas: eco e ruído do aparelho desligados (8.5) | A | ⏳ | Desligar "Cancelamento de eco" e "Redução de ruído" e ligar no viva-voz: o outro lado deve ouvir eco e ruído. O emulador não tem esses efeitos |
| 🆕 Qualidade "Música" (estéreo, 8.5) | E/A | ✅ 2026-10-09 (E) | No emulador, o microfone abre em estéreo; falta ouvir o estéreo do outro lado (web) |

## Tipos de chamada

| Cenário | Onde | Situação | Observação |
|---|---|---|---|
| 1:1 áudio (ligar e receber) | E | ✅ 2026-10-08 | Os dois sentidos; o MediaMTX gravou |
| 1:1 vídeo (ligar e receber) | E | ✅ 2026-10-08 | Gravação VP9 + Opus (o emulador não tem H264 por hardware) |
| Grupo de 3 | E | ✅ 2026-10-08 | A, B e C; grade, destaque e tela única |
| Grupo de 4 ou mais | A/E | ⏳ | Precisa de uma quarta conta de teste |
| Upgrade áudio → vídeo (pelo app e pelo web) | E | ✅ 2026-10-08 | "Apenas assistir" e "Transmitir também" |
| Adicionar participante | E | ✅ 2026-10-08 | O app adicionou o C numa chamada com o B |
| Chat da chamada | E | ✅ 2026-10-08 | A primeira mensagem cria o grupo; a chamada vai para o PiP |
| Tela compartilhada e ponteiro remotos | E | ✅ 2026-10-08 | O B compartilhou a tela pelo web; o ponteiro do C apareceu |
| Somente recepção ("Ativar microfone/câmera") | A | ⏳ | Entrar sem conceder as permissões |
| Picture-in-picture e faixa "voltar à chamada" | E | ✅ 2026-10-08 | |
