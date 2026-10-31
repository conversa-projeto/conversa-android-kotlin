# 2026-10-31 · 01 · Script para conectar o celular pela depuração por Wi-Fi

- **Fluxo:** Repositório e build
- **Tipo:** ferramenta de desenvolvimento e documentação
- **Itens:** `docs/desenvolvimento/emulador.md` ("Aparelho de verdade pela depuração por Wi-Fi")
- **Branch:** `reescrita`
- **Commits:** `1bc15e0`

## Contexto
A pessoa pediu "o script pra fazer conexão adb via Wi-Fi". Em 2026-10-22 o pareamento foi feito por um `.cmd` na pasta temporária da sessão, e a conexão pedia o "Endereço IP e porta" do celular: a descoberta pela rede (`adb mdns services`) não achou o aparelho, com o PC no cabo e o celular no Wi-Fi.

## O que foi feito
- **`ferramentas/celular-wifi.cmd`** (para o `cmd` ou dois cliques; só ASCII, CRLF):
  - acha o adb em `%LOCALAPPDATA%\Android\Sdk\platform-tools`;
  - pergunta se o PC já foi pareado; se não, pede o endereço e a porta do pareamento e o código e roda `adb pair`;
  - pede o "Endereço IP e porta" do topo da tela de Depuração por Wi-Fi e roda `adb connect`;
  - se quiser, liga as portas do servidor de dev no celular (`adb -s <endereço> reverse tcp:8081` e `tcp:3478`) e lista os aparelhos;
  - nos erros, diz o que conferir: o código só vale com a janela de pareamento aberta; a porta de conectar não é a do pareamento.
  - Quem digita o código e escolhe o aparelho é a pessoa; o script não guarda nada.
- **`.gitattributes`:** `*.cmd text eol=crlf`. O `*.bat` já tinha; sem isso, o Git poderia gravar o `.cmd` só com LF, e o `cmd` lida mal com rótulos nesse caso.
- **`emulador.md`:**
  - o atalho;
  - o passo 2 com o `adb connect` e a porta do topo da tela;
  - a descoberta pela rede, que não funcionou neste PC (o texto dizia que o adb achava o aparelho sozinho).

## Como foi verificado
- No `cmd`, com as respostas vindas de um arquivo:
  - **já pareado:** um endereço que não existe (`127.0.0.1:5`) mostrou o erro do adb e a orientação de conferir o endereço do topo da tela;
  - **pareamento:** `127.0.0.1:1` com um código falso mostrou "O pareamento falhou" e a orientação.
- A conexão com o celular de verdade não foi repetida agora (o celular não estava ligado). O mesmo `adb pair` e `adb connect` funcionaram em 2026-10-22 com o Galaxy A25 (histórico 2026-10-22-09).

## Pendências
- Nenhuma.
