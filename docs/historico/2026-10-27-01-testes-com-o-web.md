# 2026-10-27 · 01 · Testes com o web do outro lado (feitos em 2026-10-23): pastas, localização e o atender da FC-729

- **Fluxo:** Ações sobre mensagens (etapa 7). Toca também Anexos e mídia (etapa 4) e Chamadas (etapa 6)
- **Tipo:** teste e documentação
- **Itens:** `TODO.md` 7.14 e 4.12 (as linhas **Teste:** "com o web"); CON-17, ENV-30; FC-729 (CHA-27)
- **Branch:** `reescrita`
- **Commits:** `HASH_X`

## Contexto
Os testes rodaram em 2026-10-23, no fim da sessão. O registro ficou para hoje porque o computador ia desligar.

Três testes esperavam o web, que estava fora do ar:
- as pastas com o web do outro lado (WS 67 nos dois sentidos);
- a localização e o trajeto que o app manda, abertos no web;
- o atender da FC-729 com o web ligando.

## Ambiente
- O contêiner `dev` (Vite do web) estava parado havia 6 dias. Ele subiu com `docker start dev` e o Vite como no `bin/iniciar-desenvolvimento.bat` do servidor.
- **Faltava o `leaflet`** no `node_modules` do contêiner: o web passou a usar a biblioteca depois da última instalação, e o Vite mostrava "Failed to resolve import "leaflet"". O `bun install --frozen-lockfile` no contêiner (o passo "Conferindo dependências" do mesmo script) resolveu. O `git status` do web não mudou (só o `yarn.lock` solto de sempre).
- **Chrome de teste:** perfil descartável no scratchpad, câmera e microfone falsos e a porta de depuração 9333. A sessão foi posta pelo login na API, com o token direto no `localStorage` e nada impresso; os testes foram comandados pelas stores Pinia pelo DevTools.
- A direta A–B não aparece na lista do A, então os testes usaram a direta A–C (conversa 5), com o C no web.

## Pastas (7.14, CON-17)
- **web → app:** o C criou pelo web a pasta compartilhada "Do web" e pôs a mensagem 1209. Na folha de pastas do A (aberta), "Do web" apareceu sozinha com 1, sem menu (é do C).
- **app → web:** o A tocou em "Compartilhar com Teste Android C" na "Notas" e ela entrou na lista de pastas do web (`compartilhada: true`).
- **pôr mensagem, app → web:** com a conversa aberta no web, o A pôs o vídeo (1205) na "Do web" pelo app ("Selecionar" › "Pôr na pasta"). Na bolha do web, as pastas passaram de `[3]` para `[3, 4]` na hora (WS 67 `incluir`). No banco: `pasta_mensagem` com as duas.

## Localização (4.12, ENV-30, MSG-25)
- **No web, as mensagens que o app mandou:**
  - o trajeto 1197 aparece com o mapa, a rota, "Praça da Sé… → Capela de Sant…", "Trajeto · 1,7 km · 4 min" e "Abrir no Google Maps";
  - o local 1198 aparece com o mapa e "Museu de Arte de São Paulo…".
- **Visualizador:** o clique no mapa do trajeto abriu o visualizador do web, com os endereços completos de A e B, "Trajeto · 1,7 km · 4 min de carro", a rota e o botão Satélite.
- **Link:** `https://www.google.com/maps/dir/?api=1&origin=…&destination=…`.
- **web → app:** as mensagens 1193/1194, com o JSON do web, já tinham sido mostradas no app (2026-10-17).
- **Presença:** no cabeçalho do web, o A aparecia como "nesta conversa · visto por último hoje às 00:52", com a bolinha amarela, porque o emulador estava sem toque havia mais de 30 s (FC-226).

## O atender da FC-729 com o web ligando (web `17c49a4`)
O C ligou pelo web (`iniciarChamada`) e o A atendeu no emulador. Um registro na página anotou `estado` e `aguardandoConexao` a cada 50 ms.
- **Atendendo uns 16 s depois de tocar (chamada 76):** a pré-assinatura do web desistiu de tentar aos ~10 s ("no stream is available" até 04:55:51), antes de o app publicar (04:55:58). Depois do 54, o web assinou de novo e tocou o som de espera por ~5 s.
- **Atendendo uns 4 s depois (chamada 77):**
  - o app publicou (04:57:31) antes do `entrar`;
  - a tentativa seguinte do web pegou a publicação (04:57:32), mas a conexão dela não fechou ("deadline exceeded while waiting connection" 10 s depois);
  - o web assinou de novo depois do 54 (04:57:36) e tocou o som de espera por ~5 s.
- **Conclusão:** o app faz a parte dele (a publicação está no MediaMTX antes do `entrar`). Naquela versão do web, o som de espera vinha da pré-assinatura dele, que desistia ou não conectava. O web `6117545` (2026-10-25) mudou exatamente isso: o atender passou a esperar a pré-assinatura que ainda está conectando. O teste é repetido depois da sincronização de hoje.

## Documentos atualizados
- **`TODO.md`:** as linhas **Teste:** de 7.14 e 4.12 marcadas.
- **`05`:** CON-17 e ENV-30 sem o "falta conferir com o web".
- **`emulador.md`:** depois de um pull do web, o `bun install` no contêiner `dev`.

## Pendências
- Repetir o atender da FC-729 com o web `6117545` (na sincronização de hoje).
- O teste da FC-729 com a mesma conta no web e no celular (linha **Teste:** do 6.x) continua aberto.
