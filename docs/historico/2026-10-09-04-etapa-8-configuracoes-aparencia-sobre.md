# 2026-10-09 · 04 · Etapa 8 — Configurações: a tela, a aparência e o sobre

- **Fluxo:** Atividades, pesquisa, perfil e configurações (etapa 8)
- **Tipo:** funcionalidade / design
- **Itens:** `TODO.md` 8.5 (Aparência, Sobre e o começo da tela com seções); FC-804, FC-805; CFG-01, CFG-02
- **Branch:** `reescrita`
- **Commits:** (preencher)

## Contexto
A aba Configurações era provisória, no `app`: o usuário, o servidor e "Sair". O 8.5 pede uma tela com seções, como as abas do `ProfileSettingsModal.vue` do web, que no celular viram uma lista em que cada item abre uma sub-tela.

O 8.5 foi dividido em blocos. Este é o primeiro: a tela, Aparência e Sobre. Depois vêm Notificações e Permissões, Chamadas e, por último, Sistema e Acessos.

**Tema escuro:** o web segue o `prefers-color-scheme`. Aqui, o esquema escuro do `docs/design/cores.md` §6.2 é **proposta**: o FMX não tem modo escuro, e a pergunta 9 continua aberta. O próprio código dizia "Só ligar com aprovação".

## O que foi feito
- **`core:model` — `PreferenciaTema`:** Sistema, Claro e Escuro.
  - O **padrão é Claro**: sem escolha, nada muda para ninguém. "Seguir o sistema" e "Escuro" só valem por escolha da pessoa.
  - Quando o escuro for aprovado, `PADRAO` passa a `SISTEMA`, como no web.
- **Preferências:**
  - `PreferenciasStore`: chave `tema`;
  - `core:data` — `PreferenciasRepositorio`: guarda o tema, que sobrevive ao logout.
- **O tema nas telas:**
  - `MainActivity` aplica o tema escolhido. A splash espera a preferência ser lida, para a tela não piscar;
  - `BarrasDoSistema` (`core:ui`): com o escuro escolhido num aparelho claro, os ícones das barras de status e de navegação acompanham o tema do app;
  - `ChamadaActivity` também segue o tema. A tela da chamada já é escura; o tema vale para o que abre por cima.
- **Esquema escuro:** faltavam `surfaceContainerLowest`/`Low`/`surfaceContainer`. A barra inferior caía no cinza arroxeado padrão do Material.
  - Agora eles espelham o claro, só com as cores do `escuro.pss`: `#3C3C3C`, `#323232` e `#282828`.
  - Registrado em `cores.md` §6.2 (🆕 8.5).
- **`:feature:config` — Configurações** (substitui a `ConfiguracoesProvisorias` do `app`, que foi apagada com os seus textos):
  - a linha do usuário (foto, nome, e-mail, "›") abre o Perfil;
  - **Aparência:** diálogo com "Seguir o sistema" / "Claro" / "Escuro", que vale na hora;
  - **Servidor:** abre a troca de servidor, como antes;
  - **Sobre;**
  - **Sair**, com a confirmação de antes.
  - A tela fica na superfície branca, como a lista de conversas: no fundo `#F5F5F5`, o círculo da inicial sumia.
- **`FotoDaSessao`:** a URL da foto do usuário, que acompanha a sessão e é renovada quando vence (no máximo a cada 30 s), saiu do `PerfilViewModel`. Agora o Perfil e a linha do usuário em Configurações usam a mesma.
  - Isso resolve a pendência do 8.3: a linha de Configurações não renovava a URL vencida.
- **Sobre:**
  - "Conversa", a versão instalada e o servidor conectado;
  - "Licenças de código aberto": as bibliotecas que vão no APK, com autor e licença (Apache 2.0, BSD 3-Clause do WebRTC e MIT do Mermaid).
- **Testes:**
  - `AparenciaTest`;
  - `ConfiguracoesViewModelTest`: estado com foto, servidor e tema; "Sair" uma vez só;
  - os do Perfil continuam passando com a `FotoDaSessao`.

## Como foi verificado
- `./gradlew :app:assembleDebug testDebugUnitTest :core:model:test :core:testing:test :app:lintDebug ktlintCheck` passou.
- `node ferramentas/textos-repetidos.mjs` não acha nenhuma colisão.
- **Emulador (conta A).** O tema foi trocado pelo app, sem mexer no aparelho.
  - **Diálogo e troca:** "Aparência" → "Escuro" trocou o tema na hora, com os ícones da barra de status claros.
  - **Reinício:** o tema continuou depois de reabrir o app.
  - **Telas no escuro:** lista, conversa, Configurações, Sobre e Perfil ficaram legíveis, e a barra inferior ficou em `#282828`.
  - **No fim:** voltei para "Claro".

## Decisões
- **Material You:** fica de fora. As cores vêm só do `cores.md`.
- **Licenças:** a lista é escrita à mão, a partir do `libs.versions.toml`. Um gerador (AboutLibraries) pode entrar na etapa de publicação.

## Pendências
- **Você precisa decidir:** aprovar o esquema escuro (`cores.md` pergunta 9). Se for aprovado, o padrão vira "Seguir o sistema".
- **O resto do 8.5:** Notificações, Permissões, Chamadas, Sistema e Acessos. O Ramal SIP fica com o SIP (etapa 9).
