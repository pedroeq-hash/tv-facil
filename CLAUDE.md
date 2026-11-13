# Projeto: limpeza do TV Stick Pro Eletronic (Android TV 10)

## Quem sou eu e como quero trabalhar
- Sou iniciante nisso. Fale comigo **em português**, de forma simples.
- **Antes de cada comando**, explique em 1 ou 2 frases o que ele faz e por quê. Depois rode (eu aprovo na tela).
- **Uma mudança por vez.** Depois de cada mudança, me peça para conferir a TV antes de seguir.
- Se não tiver certeza sobre um pacote ou comando, **pergunte antes** e não faça.

## O aparelho
- TV Stick Pro Eletronic, Android TV 10 (`ro.build.characteristics=tv`, `leanback_only`). Modelo reportado como "Google" (firmware genérico).
- Conectado por ADB via Wi-Fi, porta 5555. O IP atual fica em `CLAUDE.local.md` (fora do git). O IP pode mudar; se `adb devices` não listar, rode `adb connect <IP>:5555` e, se falhar, me peça o IP em Configurações → Rede → Wi-Fi → rede conectada.
- ADB e scrcpy estão em `C:\scrcpy` (use `C:\scrcpy\adb.exe` se `adb` não estiver no PATH).
- Estou controlando o stick com um mouse USB (o controle remoto não está comigo).

## Regras de segurança (obrigatórias)
- **Sem root, sem gravar firmware, sem desbloquear bootloader, sem `pm uninstall` de app de sistema.** Use só `pm disable-user --user 0 <pacote>` (desfaz com `pm enable <pacote>`).
- **Backup primeiro**: antes de qualquer mudança, confira se a pasta `stick\backup` tem os arquivos abaixo; se faltar algum, gere-o:
  getprop.txt, pacotes_todos.txt (`pm list packages -f`), pacotes_sistema.txt (`-s`), pacotes_instalados.txt (`-3`), pacotes_ja_desativados.txt (`-d`), settings_global/secure/system.txt (`settings list ...`), launcher_original.txt (`cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`).
  No PowerShell, salve em UTF-8: `adb shell ... | Out-File -Encoding utf8 stick\backup\arquivo.txt`.
- **Registre tudo** em `stick\mudancas.txt` (data, comando, motivo, comando para desfazer), uma linha por mudança.
- **Nunca desative**: `com.android.systemui`, `com.android.settings`, `com.android.tv.settings`, qualquer `com.android.providers.*`, teclados (`com.google.android.leanback.ime`, `com.android.inputmethod.*`), `com.android.bluetooth`, apps de controle/pareamento (nomes com `rcu`, `remote`), `com.google.android.gms`, `com.google.android.gsf`, `com.android.vending`, `com.android.packageinstaller`, `com.android.permissioncontroller`, qualquer coisa com `wifi`, `network`, `hdmi`, `tvinput`, `tuner`, `ota` do sistema, e o **launcher original** (deixe como reserva mesmo depois de instalar outro).
- Não reinicie o stick no meio de uma sequência de mudanças. Reinicie uma vez no fim (`adb reboot`) e confirme que ele volta.

## Objetivos, em ordem
1. Conferir conexão e backup.
2. Listar os pacotes de sistema e me mostrar uma tabela: pacote | o que é (em português simples) | sugestão (desativar / deixar / nunca tocar). Esperar minha escolha.
3. Desativar só os que eu aprovar, um por vez, testando.
4. Opcional (se eu pedir): reduzir animações para 0.5 (`settings put global window_animation_scale 0.5`, idem `transition_animation_scale` e `animator_duration_scale`; desfaz com 1).
5. Ajudar a instalar pela Play Store: Projectivy Launcher, TiviMate (ou OTT Navigator), Pluto TV, Plex, Globoplay, YouTube. Para digitar texto no stick use `adb shell input text "..."`.
6. Configurar no player IPTVlistas de canais:
   - `https://iptv-org.github.io/iptv/countries/br.m3u`
   - `https://iptv-org.github.io/iptv/languages/por.m3u`
7. Final: desativar a depuração nas Opções do desenvolvedor, `adb disconnect`, e me dar um resumo do que foi mudado e como desfazer.

## Pastas do projeto
- `stick\` — dados do aparelho, **fora do git**: `mudancas.txt`, `backup\`, `logs\` (cópias dos logs do Termux), `apks\` (APKs baixados), `listas-antigas\`.
- `pipeline\` — scripts que rodam no Termux do stick (teste dos canais a cada 6 h). No stick ficam em `~/iptv/` e `~/.termux/boot/`. Depois de mudar `testa_canais.sh`, rode `bash pipeline/testa_regras.sh` (Git Bash, `LANG=en_US.UTF-8`).
- `tvapp\` — app próprio de TV (Kotlin, libVLC). Plano: `docs\plano-tvapp.md`.
- `CLAUDE.local.md` — dados desta máquina/rede (IP do stick etc.), **fora do git**.
- `ferramentas\env.ps1` — ativa JDK/Android SDK portáteis (`C:\dev\android-tools`) só na sessão atual.

## App próprio (tvapp)
- **TDD**: toda lógica fica no módulo `:core` (Kotlin puro). Escreva o teste antes, veja falhar, implemente. `:app` é camada fina (telas + libVLC).
- Ferramentas são portáteis: nada no registro nem variáveis globais. Desinstalar = apagar `C:\dev\android-tools`, `%USERPROFILE%\.gradle`, `%USERPROFILE%\.android`.
- Chave de assinatura fica em `C:\dev\chaves\`, **nunca** no repositório (o repo é público).
- Atualização no stick: GitHub Releases + Obtainium.
- **Próximo: app par no note** (configura a TV pela rede Wi-Fi: listas, categorias, favoritos, logs). Plano e análise de segurança em `docs\plano-tvapp.md` → Fase 7. **A análise de segurança vem antes de qualquer código do app par.**
- **Avaliar trazer o pipeline do Termux para dentro do app** (Fase 7a), com logs auditáveis pelo note. O Termux continua rodando até o app provar que dá o mesmo resultado.
- Toda lista M3U e todo dado que chega pela rede são **não confiáveis**: validar no `:core`, com teste.
- **Nunca** pôr no repositório (público) URL de lista com usuário/senha/token (`username=`, `password=`…), nem em `pipeline\listas.txt`.

## Se algo der errado
- Desfazer a última mudança com `pm enable <pacote>` (ver `stick\mudancas.txt`).
- Último recurso: restauração de fábrica (Configurações → Sistema/Sobre → Redefinir), que reativa tudo.
