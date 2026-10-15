# 2026-10-15 · 03 · Figurinhas do pacote Noto

- **Fluxo:** Ações sobre mensagens (etapa 7)
- **Tipo:** correção
- **Itens:** `TODO.md` 8.7 (2ª linha); FC-417; ENV-04
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
No web `d202c06`, o seletor passou a ter só o pacote "Noto" (55 animações, `noto/<nome>`), e os pacotes básico, rostos e coisas foram apagados.

O Android desenha as figurinhas a partir dos próprios assets, então elas não se entendiam nos dois sentidos:
- as do web viravam o quadro "Figurinha" no Android;
- as do Android viravam texto no web.

## O que foi feito
- **Assets:** as 55 animações de `conversa-web/public/figurinhas/noto` foram copiadas para `feature/chat/src/main/assets/figurinhas/noto/` (3,7 MB; o APK passa de 141 para 143 MB). Ao lado, o `LICENSE-noto.txt` (Animated Noto Emoji, © Google, CC BY 4.0).
- **`core/model/.../Figurinhas.kt`:**
  - `PACOTE_NOTO`, gerado do `utils/figurinhasNoto.ts` do web, com os mesmos ids e nomes;
  - `PACOTES_FIGURINHAS` (o seletor) passa a ser só o Noto, como no web;
  - os três pacotes antigos foram para `PACOTES_ANTIGOS`. Eles não aparecem no seletor, mas o nome e o caminho continuam valendo. Assim, **as mensagens antigas seguem animadas no Android**, onde o web mostra só "Figurinha";
  - `CreditoFigurinhas` no pacote.
- **Seletor (`feature/chat/.../Figurinhas.kt`):** embaixo das abas, o crédito que a licença pede, "Animated Noto Emoji © Google, CC BY 4.0", sublinhado. O toque abre a licença, como o link do web.
- **`FigurinhasTest`:**
  - o seletor tem só o Noto (55), com o crédito;
  - os pacotes antigos continuam resolvendo;
  - nome e caminho de um `noto/…`;
  - toda figurinha, nova ou antiga, tem a animação nos assets.

## Como foi verificado
- **Verificação completa:** `assembleDebug`, `testDebugUnitTest`, `:core:model:test`, `:core:testing:test`, `:app:lintDebug` e `ktlintCheck`. Passou.
- **No emulador (A), contra o servidor de dev:**
  - o B mandou pela API `noto/festejando`, o mesmo id que o web manda, e a figurinha apareceu animada;
  - o seletor mostra só "Noto" e o crédito;
  - tocar em "Legal" mandou `noto/legal` (conferido no `GET /mensagens`).
- O lado do web não foi olhado na tela. O `noto/legal` que o Android mandou é o mesmo id que o seletor do web usa, e o arquivo existe em `/figurinhas/noto/`.

## Decisões
- **Manter os pacotes antigos no APK** (cerca de 0,3 MB) só para desenhar as mensagens que já os usam. É melhor que o texto "Figurinha" do web e não custa nada à paridade, porque não aparecem no seletor.

## Pendências
- Nenhuma.
