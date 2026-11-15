# Plano: app próprio de TV para o stick (interface simples + libVLC)

## Contexto
O stick já recebe, a cada 6 h, uma lista testada e agrupada (`/sdcard/iptv/canais_ok.m3u`, gerada pelo Termux). O problema agora é a **usabilidade**: o Kodi tem menus demais para a usuária final. Decidimos criar um **app próprio**, com interface mínima pensada para o controle Pro Eletronic (sem teclas numéricas; tem setas, OK, voltar, CH ▲▼, ≡, ⌫ e microfone). A reprodução usa a **libVLC** (motor open source do VLC, baseado em FFmpeg, como o Kodi), para tocar praticamente o que o Kodi toca. Desenvolvimento com **TDD**. Atualização via **GitHub Releases (repo público) + Obtainium** no stick.

As regras do `CLAUDE.md` continuam valendo: explicar cada comando antes, uma mudança por vez no stick, registrar tudo em `mudancas.txt`, conferir a TV entre as mudanças.

## Fase 0 — Reorganizar a pasta do projeto
Estrutura nova (move, sem apagar nada):
```
tv-facil/
  CLAUDE.md            ← atualizar caminhos + seção do app (build, TDD)
  README.md            ← mapa das pastas (novo, curto)
  stick/               ← dados do aparelho (FORA do git)
    mudancas.txt       (era ./mudancas.txt)
    backup/            (era backup-stick/)
    logs/              (era logs-stick/)
    apks/              (era downloads/)
    listas-antigas/    (era listas/fontes/)
  pipeline/            (era termux/) testa_canais.sh, testa_regras.sh, repete.sh, inicia.sh, listas.txt
  tvapp/               ← projeto Android (novo)
  ferramentas/env.ps1  ← ativa JDK/SDK só na sessão atual
```
- `git init` na raiz; `.gitignore` exclui `stick/`, `local.properties`, `build/`, `.gradle/`, chaves (`*.jks`).
- Atualizar no `CLAUDE.md` as referências a `backup-stick` e `mudancas.txt`.

## Fase 1 — Ferramentas portáteis em `C:\dev\android-tools`
- Baixar e **conferir SHA-256** (publicados pelos sites oficiais): JDK 17 Temurin (zip), Android command-line tools (zip), Gradle 8.x (zip).
- `sdkmanager`: só `platforms/android-36` e `build-tools/36.0.0` (instalados em 10/11/2026; o `adb` continua o de `C:\scrcpy`). Versões baixadas: JDK Temurin 17.0.21, Gradle 9.9.1, cmdline-tools 23.0 — todas com checksum oficial conferido.
- `ferramentas/env.ps1` define `JAVA_HOME`, `ANDROID_HOME` e `PATH` **apenas na sessão** — nada no registro nem variáveis globais.
- Desinstalar = apagar `C:\dev\android-tools`, `%USERPROFILE%\.gradle` e `%USERPROFILE%\.android` (documentar no README).

## Fase 2 — Arquitetura (pensada para TDD)
Projeto Gradle `tvapp/` com 2 módulos:

**`:core` — Kotlin puro (JVM), sem Android. Toda a lógica, 100% TDD (JUnit 5 + kotlin.test):**
- `M3uParser`: lê `#EXTINF` (nome, `tvg-logo`, `group-title`) + `#EXTVLCOPT` (user-agent, referrer) + URL; tolera `\r`, linhas vazias, blocos quebrados.
- `ChannelCatalog`: ordem dos grupos (Favoritos → Sergipe → Notícias → Emissoras → Filmes → Esportes → Outros), busca **sem acento e sem maiúsculas** ("camara" acha "Câmara", "glo" acha "GloboNews").
- `Zapper`: próximo/anterior com volta ao início, dentro do grupo atual.
- `TvStateMachine`: estados *Assistindo / Lista sobre o vídeo / Navegando / Buscando / Erro*; recebe ações (`Ok, Voltar, CanalMais, CanalMenos, Favoritar, Apagar, Buscar…`) e devolve o novo estado. A interface só desenha esse estado → o comportamento do controle fica testável sem TV.
- `KeyMap`: código de tecla Android → ação (função pura, testada).
- Interfaces `Player`, `FavoritesStore`, `LastChannelStore` (com versões falsas nos testes).

**`:app` — Android TV, camada fina:**
- Compose + `androidx.tv:tv-material`; `minSdk 29`, só `armeabi-v7a` (stick 32 bits, APK menor).
- `VlcPlayer` implementa `Player` com `org.videolan.android:libvlc-all` (passa user-agent/referrer como opções de mídia). Erro ou 10 s sem imagem → "Canal indisponível", oferecendo o próximo.
- Lê `/sdcard/iptv/canais_ok.m3u` (`READ_EXTERNAL_STORAGE` + `requestLegacyExternalStorage`, válido no Android 10); recarrega quando o arquivo muda.
- Favoritos e último canal em `SharedPreferences`.
- Telas: **Assistindo** (abre no último canal) · **Lista sobre o vídeo** (OK) · **Grade** com grupos à esquerda e cartões grandes · **Busca** (teclado do sistema com microfone; botão de voz via `RecognizerIntent`, se o stick tiver).
- Controle: CH ▲▼ = trocar canal · OK = lista · ≡ = favoritar · ⌫ = apagar na busca · Voltar = sair da camada atual.

Nome e pacote do app: definir antes do primeiro release (o pacote não muda depois sem reinstalar). Sugestão provisória: "TV Fácil", `br.tvfacil`.

## Fase 3 — Protótipo (ponto de decisão)
1. TDD do `M3uParser` + uma tela simples com a lista + `VlcPlayer` tocando.
2. Instalar via ADB (registrar em `mudancas.txt`).
3. Medir no stick:
   - **Cobertura:** modo de diagnóstico que tenta cada canal por 8 s e registra quantos tocam (comparar com os ~340 que passam no teste do curl).
   - **Desempenho:** rolagem fluida, memória, tempo para trocar de canal.
   - **Teclas reais do controle** (`adb shell getevent -lt` / logcat): confirmar os códigos de CH ▲▼, ≡, ⌫ e microfone (o botão do Assistente pode ser capturado pelo sistema).
     **Feito em 11/11/2026** (controle Bluetooth, `Vendor_2b54_Product_1600.kl`): CH ▲▼ = `KEYCODE_CHANNEL_UP/DOWN` · ≡ = `KEYCODE_MENU` · ⌫ = `KEYCODE_DEL` · microfone = `KEYCODE_SEARCH` (o sistema não usa; fica livre para o app) · Voltar = `KEYCODE_BACK`. Há ainda um botão que envia `F3` (não identificado). **Testado em 12/11/2026: o controle não tem microfone**; a busca é só pelo teclado (≡ → Buscar canal).
   - **Resultado do diagnóstico (11/11/2026):** ~236 de 242 canais tocaram (97,5%), abertura média ~4 s (máx. 12,6 s), memória estável 85–105 MB. 3 quedas nativas intermitentes da libVLC (HLS com propaganda) → canal fica bloqueado por 6 h (`Bloqueio` no `:core`). Decisão: **seguir** com o app.
4. **Seguir ou não:** se muitos canais falharem ou ficar lento, reavaliar (plano B: skin simplificada no Kodi).

## Fase 4 — Versão 1
Completar, sempre em TDD no `:core`: favoritos, grupos, busca, troca por CH ▲▼, tela de erro, abrir no último canal. Rodadas de "instala → testa na TV com o controle → ajusta".

## Fase 5 — Atualização
- Repositório **público** no GitHub (sem senhas nem listas de canais; `stick/` fora do git).
- **Chave de assinatura** criada com `keytool`, guardada **fora do repo** (`C:\dev\chaves\`) + cópia de segurança num gerenciador de senhas. Sem ela, não dá para atualizar sem perder os favoritos.
- Cada versão: aumentar `versionCode`, gerar o APK assinado, publicar com `gh release create vX.Y --attach app-release.apk`.
- No stick: instalar o **Obtainium** (GitHub, `armeabi-v7a`), conferir permissões, apontar para o repo e permitir "instalar apps desconhecidos" **só para ele** (uma vez, com mouse).
- A lista de canais continua sendo atualizada pelo pipeline do Termux (sem mudança).
- **Verificação de desenvolvedor do Google (conferido em 15/11/2026):** a regra vale no Brasil desde 30/09/2026, por enquanto só para apps vindos de 7 lojas, e em 2027 passa a valer para todos os apps em aparelhos **certificados**. O stick **não é certificado**: o firmware é o genérico `google/walley/eros-p1`, a atualização de segurança é de 2019-12-05, a Play Store da TV não mostra certificação e a Proeletronic não está na lista de parceiros do Google. Por isso o Obtainium segue servindo. Rever quando o Google anunciar o mês de 2027. Se um dia o aparelho for certificado: criar uma conta gratuita de "distribuição limitada" (até 20 aparelhos) e registrar `br.tvfacil` com a chave atual. A reserva é atualizar por ADB, que continua liberado. Fonte: [guia do Google](https://developer.android.com/developer-verification/guides).

## Fase 6 — Encerramento (objetivo 7 do CLAUDE.md)
Deixar o app na tela inicial (Arc Launcher) e manter o launcher original como reserva. Desligar a depuração, `adb disconnect`, resumo final do que mudou e como desfazer.

## Fase 7 — App par no note + segurança
**Ideia:** a TV é só para assistir. Um segundo app, no computador, conversa com o TV Fácil pela rede Wi-Fi de casa. Nele, o filho da usuária troca, adiciona ou remove listas, organiza os canais em categorias, marca favoritos, reordena e **vê os logs**. A TV aplica as mudanças sem a usuária precisar mexer em nada. **A análise de segurança abaixo vem antes de qualquer código do app par.**

### 7a — Trazer o pipeline do Termux para dentro do app (se for viável)
- **Por que vale a pena:** hoje o stick depende de várias peças: o Termux, o Termux:Boot, os scripts em `~/iptv` e o `/sdcard/iptv`, por onde a lista chega ao app. Dentro do app sobra uma peça só. O app par passa a mexer nas listas direto, sem editar `listas.txt` por ADB. E a lista sai do `/sdcard`, o que resolve boa parte do risco 1 da tabela abaixo.
- **Como:** o `WorkManager` (Jetpack) roda a cada 6 h e também depois de religar o stick, sem precisar do Termux:Boot. Os passos do `testa_canais.sh` viram código:
  - **`:core` (TDD):** juntar as listas e tirar os canais repetidos (pela URL); respeitar o limite `MAX_CANAIS`; decidir se a resposta é "ok" (código 2xx, não vazia, não é página HTML); classificar nos grupos e remover o que não serve (regras do `classifica`); montar a lista final; manter a lista antiga se nada passar. Os casos do `pipeline/testa_regras.sh` viram testes JUnit, para o resultado ser o mesmo.
  - **`:app`:** a busca HTTP com `HttpURLConnection` (sem biblioteca nova), 8 canais testados ao mesmo tempo, 10 s no máximo por canal, lendo só os primeiros 4 KB (como o `head -c 4096`). O `extras.m3u` (canais de Sergipe adicionados à mão) vira uma lista local que o note edita.
- **Logs auditáveis:** um arquivo no armazenamento privado do app, com rodízio (fica só o que cabe num limite de tamanho). Ele registra:
  - (a) cada rodada: início, duração, canais lidos, funcionando, removidos e quantos por grupo (como o `relatorio.txt` de hoje);
  - (b) cada canal que falhou: o motivo (tempo esgotado, código HTTP ou página HTML) e o domínio, **com a senha mascarada**;
  - (c) cada mudança feita pelo note: quando, o quê e qual note pareado fez.
  O note lê o log pela API pareada. A TV não mostra o log.
- **Ponto de decisão:** rodar o app e o Termux **juntos** por alguns dias e comparar os números (canais ok, no total e por grupo). Se o app ficar igual ou melhor e não pesar no stick, parar o agendamento do Termux (e registrar em `mudancas.txt`). Se não ficar, o Termux continua e o app par só edita o `listas.txt`. Riscos: o Android matar a tarefa longa (resolve com `setForeground` do WorkManager) e o consumo de memória durante o teste (medir).

### Análise de segurança: o que se aplica aqui
| # | Ataque | Se aplica? | Situação hoje | O que fazer | Prioridade |
|---|---|---|---|---|---|
| 1 | Roubo da URL com senha (lista paga) | **Sim**, quando entrar uma lista paga | As listas atuais são públicas (iptv-org). Mas a URL do canal vai para `SharedPreferences`, para o relatório de diagnóstico (`getExternalFilesDir`) e para `/sdcard/iptv/`, que no Android 10 outros apps com permissão de armazenamento conseguem ler | Mostrar só o domínio na tela. Mascarar `username`, `password` e `token` nos logs e relatórios (função no `:core`, com teste). Guardar a lista com senha no armazenamento privado do app. Usar `android:allowBackup="false"`. Nunca colocar no git | Alta |
| 2 | Conteúdo malicioso nos nomes | **Pouco na TV**; **sim no note**, se ele for uma página web | O `Text` do Compose mostra texto puro, sem executar HTML ou JS. Os logos não são carregados (usamos siglas) | No `:core`: limitar o tamanho do nome e do grupo, tirar caracteres de controle e de inversão de texto (bidi), limitar o tamanho do arquivo e o número de canais. No note: sempre escapar o texto (nunca `innerHTML`) | Alta |
| 3 | SSRF | **Na forma local**: não há servidor na nuvem, mas a TV e o curl buscam URLs que vêm de fora | `Uri.parse(canal.url)` aceita qualquer esquema (`file://`, `smb://`, `mms://`, `rtsp://`…), e o `curl` do pipeline também | Aceitar só `http` e `https` nas listas e nos canais (medir quantos canais isso derruba). No pipeline: `curl --proto =http,https --proto-redir =http,https`. Na URL da **lista**, recusar `localhost`, IPs privados e link-local | Alta |
| 4 | Falha no player | **Sim**: a libVLC é código nativo e processa vídeo de terceiros (já vimos 3 quedas nativas) | O parser **já** aceita só dois `#EXTVLCOPT` (user-agent e referrer). Esse era o ataque do VLC de 2007 (SA-0804), então essa parte está certa. Usamos `libvlc-all:3.7.7` | Conferir se a 3.7.7 já traz as correções do último boletim da VideoLAN (VLC 3.0.22) e atualizar quando sair versão nova. Manter a lista branca de opções. Limitar o tamanho do user-agent e do referrer. A lista branca de esquemas (item 3) também bloqueia `mms://` (CVE-2024-46461) e `realrtsp://` | Alta |
| 5 | Phishing e links | **Baixo** | O app não abre navegador nem links | Nunca abrir URL externa. Se um dia carregar logos: só `https`, com limite de tamanho. As configurações ficam só no note, nunca na tela da TV | Média |
| 6 | **Qualquer aparelho do Wi-Fi mexe na TV** | **Sim**: é o risco principal do app par | Ainda não existe | **Parear com um PIN** que aparece na TV (como no Chromecast); o note guarda um token. Recusar qualquer pedido sem token. Servidor só na rede local | Alta |
| 7 | **Site aberto no note ataca a TV** (CSRF / DNS rebinding) | Sim, se a TV abrir um servidor HTTP | Ainda não existe | Token em cabeçalho (não em cookie), sem CORS, conferir os cabeçalhos `Host` e `Origin`, aceitar só JSON | Alta |
| 8 | **Atualização falsa** | Sim (Obtainium + GitHub) | A chave de assinatura já fica fora do repo | Ativar 2FA no GitHub. O Android só aceita atualização assinada com a mesma chave | Média |
| 9 | **O log vaza dados** | Sim, quando existir o log auditável | Ainda não existe | Mascarar as credenciais **antes** de gravar (mesma função do item 1). Guardar no armazenamento privado do app. O note só lê o log com o token | Alta |

**Feito na 0.19 (14/11/2026):**
- **Item 2 (nomes):** o `M3uParser` tira caracteres de controle e invisíveis (`\p{Cc}`, `\p{Cf}`, inclusive os de inversão de texto). Ele também corta nome em 80, grupo em 40 e user-agent/referrer em 256 caracteres, e aceita no máximo 5000 canais. O app não lê lista acima de 5 MB.
- **Item 3 (esquemas):** o app só aceita canal `http`/`https`, com URL de até 2048 caracteres e sem espaço. Na lista daquele dia, os 237 canais já eram assim. O `curl` do pipeline usa `--proto =http,https --proto-redir =http,https`; só o `extras.m3u` local entra como `file://`. Ainda falta recusar `localhost` e IP privado na URL da lista, o que entra junto com o app par.
- **Item 4 (player):** a `libvlc-all:3.7.7` é a mais nova da série 3. Ela traz o **VLC 3.0.25** (conferido no `libvlc.so`), posterior ao 3.0.24 do boletim VideoLAN-SB-VLC-3024 (set/2026). Por isso não precisa atualizar agora. O bloqueio de esquemas também barra `mms://` e `rtsp://`.
- **Item 1 (senhas), parcial:** `android:allowBackup="false"`. Nenhum log grava URL hoje. A função de mascarar `username`/`password`/`token` entra junto com o log da 7a.

**0.21 (14/11/2026), `usesCleartextTraffic="true"`:** na 1ª rodada do teste dentro do app (0.20), todos os canais `http://` falharam com `IOException`. O motivo é que o Android barra `http` sem criptografia no `HttpURLConnection` de apps com targetSdk ≥ 28. A libVLC tem conexão própria e já tocava esses canais, então liberar `http` não abre nada novo; a lista branca `http`/`https` da 0.19 continua valendo. Quando existir o app par, avaliar uma `network_security_config` que libere `http` só para o teste dos canais.

No pipeline de hoje, o `extras.m3u` entra como `file://`. Por isso o `--proto =http,https` vale só para as listas baixadas e os canais testados; o arquivo local continua sendo lido direto.

### App par como página servida pela própria TV (decidido em 14/11/2026, 0.23)
O stick vai para outra casa. Quem configura é o filho da usuária, e ele quer pôr listas M3U próprias (geralmente pagas, com usuário e senha na URL). Em vez de um app no notebook, a TV serve uma página simples, aberta no navegador do celular ou do notebook na mesma rede Wi-Fi. As regras abaixo valem antes de qualquer código.

- **Item 6 (qualquer aparelho do Wi-Fi):** o servidor (porta 8765) só existe enquanto a tela "Configurar pelo celular" está aberta na TV. No dia a dia, nada fica escutando.
  - O pareamento usa um PIN de 6 dígitos (`SecureRandom`) mostrado na TV. Com 5 PINs errados, o servidor fecha.
  - O token tem 32 bytes aleatórios, fica só na memória e vale só naquela sessão.
- **Item 7 (site malicioso / DNS rebinding):** a TV recusa qualquer pedido fora destas regras:
  - o `Host` tem que ser exatamente `<IP-da-TV>:8765`;
  - se vier `Origin`, ele tem que ser `http://<IP-da-TV>:8765`;
  - toda rota, menos a página e o `/parear`, exige o cabeçalho `X-Token`. Um formulário de outro site não consegue mandar cabeçalho próprio.
  - Não há CORS. As respostas levam `Content-Security-Policy` (nada externo, `frame-ancestors 'none'`) e `Cache-Control: no-store`.
- **Item 3 (URL de lista):** só `http`/`https`, até 2048 caracteres. São recusados `localhost` e IPs de loopback, rede privada ou link-local escritos direto na URL.
  - Limite conhecido: um nome que o DNS resolve para um IP privado passa. Isso deve ser conferido no DNS se a página ganhar mais poder.
- **Itens 1 e 9 (senha da lista):**
  - as URLs ficam só no armazenamento privado do app (`filesDir`, com `allowBackup=false`) e nunca vão para o `/sdcard`;
  - a página e o log mostram só o domínio;
  - as listas próprias não passam pelo teste de canais, porque listas pagas limitam conexões e testar centenas de canais pode bloquear a conta.
- **Pedido HTTP (não confiável):**
  - linha e cabeçalhos até 8 KB, corpo até 4 KB, 10 s de tempo;
  - uma conexão por vez;
  - o código que lê o pedido fica no `:core`, com testes.
- **Item 2 (nomes na página):** a página só usa `textContent`, nunca `innerHTML`.

### Canais avulsos: enviar um arquivo .m3u pela página (0.24)
**Por quê:** algumas listas existem só como arquivo, com usuário e senha nos links dos canais, e não podem virar um link público. O `extras.m3u` do Termux não serviria: desde a 0.22 o app usa a própria lista, e o `TesteJob` só lê links. Em 14/11/2026, esse arquivo nem existia no stick. Com esta função, o arquivo vai do note ou do celular direto para a TV.

**Como funciona:** a página ganha as rotas `GET`, `POST` e `DELETE` em `/avulsos`. Cada envio **substitui** o anterior. Os canais entram na frente das listas próprias e, como elas, **sem teste**.

**Regras de segurança:**
- **Itens 6 e 7:** as três rotas exigem `X-Token`. Valem as mesmas regras de `Host`, `Origin`, PIN e token das outras rotas.
- **Tamanho do pedido:** o corpo passa de 4 KB para **64 KB**, porque uma lista com cerca de 300 canais não cabe em 4 KB.
  - O corpo é lido antes de conferir o token. Isso é aceitável porque o servidor atende uma conexão por vez, desiste em 10 s e só existe com a tela aberta. Além disso, 64 KB não pesa na memória.
- **Item 2 (conteúdo não confiável):** o texto passa pelo `M3uParser`, que aceita só `http`/`https`, URL de até 2048 caracteres, nomes sem caracteres de controle ou de inversão de texto e tamanhos cortados. O limite é de **300 canais** avulsos.
  - Se o arquivo não tiver nenhum canal válido ou tiver canais demais, a TV responde 400 e **nada muda**: a lista anterior continua.
  - A TV guarda **a lista já filtrada e reescrita** (`TesteCanais.m3u`), nunca o texto recebido. Na leitura, a lista passa de novo pelo `M3uParser`.
- **Itens 1 e 9 (senha nos links):** o arquivo fica só em `filesDir/extras.m3u`, que é privado e tem `allowBackup=false`. Ele nunca vai para o `/sdcard`. A página e o log mostram **só a quantidade** de canais, nunca um link.
- **Limite conhecido:** um canal pode apontar para um IP da rede de casa, como já acontece nas listas próprias.
  - Só o VLC abre esse link, e só quem tem o PIN consegue enviá-lo.
  - Se a página ganhar mais poder, recusar IPs privados também na URL dos canais.

### Filmes e séries de lista própria (0.25)
**Por quê:** a lista do colega tem 85 MB e 316 mil itens (30 mil filmes e 286 mil episódios de 8,4 mil séries). Até a 0.23 o app baixava só 15 MB e guardava 5000 itens, todos como canais. O app tem 128 MB de memória, então a lista inteira não cabe na memória de uma vez.

**Como funciona:** um job do Android, 1 vez por dia, baixa a lista própria direto para um arquivo e a lê aos pedaços. A leitura separa:
- canais ao vivo → `proprias_aovivo.m3u`;
- filmes → `filmes.m3u`;
- episódios → um arquivo por série, mais o índice `series.tsv`.

A tela carrega só os filmes e o índice das séries. Os episódios de uma série são lidos do disco quando ela é aberta.

**Regras de segurança:**
- **Item 2 (conteúdo não confiável):** todo item passa pelo `M3uParser`: só `http`/`https`, URL de até 2048 caracteres, sem caracteres invisíveis, nomes cortados. As listas públicas continuam sendo tratadas como canais, como antes.
- **Disco e memória:**
  - o download é gravado aos pedaços, até **200 MB**, contados já descompactados (vale para resposta gzip). Se passar disso ou falhar, a biblioteca anterior continua inteira;
  - limites: 60 mil filmes, 20 mil séries, 5 mil episódios por série, 5 mil canais ao vivo;
  - a lista inteira nunca fica na memória.
- **Nomes de arquivo:** o arquivo de cada série é `series/<número>.m3u`. O número é gerado pelo app e nunca vem do texto da lista, então `../` não tem efeito.
- **Índice `series.tsv`:** os nomes chegam sem tab e sem quebra de linha, porque o `M3uParser` tira `\p{Cc}`. Linha estragada é ignorada. Há teste com nome malicioso.
- **Itens 1 e 9 (senha nos links):** tudo fica em `filesDir`, que é privado e tem `allowBackup=false`. A posição de "continuar de onde parou" vai para `SharedPreferences` privado. O log mostra só o domínio e as contagens.
- **Troca atômica:** a biblioteca nova é montada em `biblioteca.novo/` e só substitui a antiga no fim. Quem lê nunca pega uma biblioteca pela metade.

### Ajustes depois do teste na TV (0.26)
- **Registro simples na página:** `Resumo.simples` (`:core`, com testes) transforma o `registro.txt` em frases com horário de início e fim, além de uma previsão do próximo teste. A rota é `GET /resumo` e exige o token, como `/registro`.
  - O registro completo continua em "Ver detalhes (avançado)".
  - Nenhuma das duas mostra URL; só domínio e contagens.
  - A página agora lê os últimos 64 KB do registro. Com 6 KB, só apareciam as linhas "falhou".
- **Busca:** a do menu continua única, com até 30 canais, 30 séries e 30 filmes (`Busca.global`). Filmes e Séries ganham uma barra no topo que busca só naquele tipo.
- **Segurar ◀▶ no filme:** a barra mostra uma prévia (`Pulo.alvo`, velocidade crescente), e o vídeo pula uma vez só, ao soltar. Cada pulo recarrega o vídeo, e o stick é fraco.
- **Nome das listas (0.27):** cada lista tem um nome opcional, dado pelo usuário: 2ª linha do `POST /listas` ou `PUT /listas/<i>`.
  - O nome não é confiável: `Fontes.nome` tira caracteres de controle e invisíveis (inclusive tab e quebra de linha) e corta em 40 letras.
  - Ele é gravado depois de um tab em `listas.txt` e aparece na página só por `textContent`.
  - Trocar só o nome não baixa nada de novo.
  - Os canais avulsos continuam **sem teste** (decisão do usuário), e a página avisa isso.
- **Job diário (ID 3):** não baixa a lista de novo se a biblioteca tem menos de 20 h. Antes, ele rodava logo depois do ID 4. Quando o Android interrompe um job, o registro passa a dizer "interrompida pelo Android"; antes aparecia o nome embaralhado pelo R8.

**Ordem sugerida:**
1. Proteger o que já existe: itens 1, 3 e 4, em TDD no `:core`, mais o `curl --proto`.
2. Fazer a 7a (pipeline dentro do app, com o log).
3. Desenhar o protocolo do app par (HTTP, WebSocket, descoberta na rede) já com os itens 6, 7 e 9.

**Fontes:** [VideoLAN SA-0804](https://www.videolan.org/sa0804.html) (EXTVLCOPT) · [CVE-2024-46461](https://nvd.nist.gov/vuln/detail/CVE-2024-46461) (MMS, corrigida no VLC 3.0.21) · [boletins da VideoLAN](https://images.videolan.org/security) (último: 3.0.22) · [OWASP SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) · proteção contra DNS rebinding: validar `Host` e `Origin` ([middleware do SDK MCP](https://ts.sdk.modelcontextprotocol.io/v2/api/@modelcontextprotocol/express/middleware/hostHeaderValidation.html)).

## Verificação
- `gradlew :core:test` verde a cada passo (escrever o teste antes, ver falhar, implementar).
- `gradlew :app:assembleDebug` compila; `adb install` no stick.
- Checklist na TV com o controle real: abre tocando o último canal · CH ▲▼ troca · OK abre a lista · ≡ favorita e o canal aparece em "Favoritos" · busca por voz "globo news" encontra · canal quebrado mostra a mensagem e oferece o próximo · reiniciar o stick e abrir o app de novo · atualizar de v0.1 para v0.2 pelo Obtainium **mantendo os favoritos**.

## Riscos
- Cobertura da libVLC no armv7 e desempenho do Compose num stick fraco → medidos no protótipo antes de investir na v1.
- O botão do microfone pode abrir o Assistente do Google em vez do app → nesse caso, a voz fica só na tela de busca.
- Obtainium tem interface de celular: a primeira configuração precisa do mouse; depois, só confirmar as atualizações.
- Perder a chave de assinatura = reinstalar e perder os favoritos → backup obrigatório.
- App par na rede Wi-Fi: qualquer aparelho da casa (ou um site aberto no note) poderia mexer na TV → pareamento com PIN e token. Detalhes e demais riscos na Fase 7.
