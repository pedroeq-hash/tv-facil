# TV do stick Pro Eletronic

Configuração de um TV stick Android TV 10 e um app de TV simples para a usuária final.

| Pasta | O que tem |
|---|---|
| `pipeline/` | Scripts que rodam no Termux do stick: testam os canais das listas a cada 6 h e geram `/sdcard/iptv/canais_ok.m3u`. Endereços das listas em `pipeline/listas.txt`. |
| `tvapp/` | App de TV (Kotlin + libVLC). Lógica em `:core` (testes primeiro), telas em `:app`. |
| `ferramentas/` | `env.ps1`: ativa JDK e Android SDK portáteis só na sessão do PowerShell. |
| `stick/` | Dados do aparelho (backup, logs, APKs, registro de mudanças). Não vai para o git. |

## Ferramentas (portáteis)
JDK, Android SDK e Gradle ficam em `C:\dev\android-tools`. Para usar: `. .\ferramentas\env.ps1`.
Para desinstalar tudo: apagar `C:\dev\android-tools`, `%USERPROFILE%\.gradle` e `%USERPROFILE%\.android`.

## Instalar e atualizar o app (TV Fácil)
O APK assinado de cada versão fica em [Releases](https://github.com/pedroeq-hash/tv-facil/releases) (só `armeabi-v7a`, para sticks Android TV de 32 bits).
No stick, o [Obtainium](https://github.com/ImranR98/Obtainium) aponta para este repositório e instala as versões novas.

O app lê os canais de `/sdcard/iptv/canais_ok.m3u`, gerado pelo `pipeline/` no Termux.

## Licenças
- Código deste repositório: [MIT](LICENSE).
- [libVLC](https://code.videolan.org/videolan/vlc-android) (reprodução de vídeo): LGPL-2.1 ou posterior.
- Fonte Atkinson Hyperlegible: SIL Open Font License 1.1 (`tvapp/app/FONTE-LICENCA-OFL.txt`).
