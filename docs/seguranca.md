# Segurança

O app recebe dados de fora o tempo todo: listas M3U, nomes, links de vídeo, capas e pedidos da página de configuração. Tudo isso é tratado como **não confiável**. A validação fica no `:core`, e cada regra tem teste.

## Ameaças e o que foi feito

| # | Ameaça | O que o app faz |
|---|---|---|
| 1 | **Credenciais em links** (listas privadas costumam ter usuário e senha na URL) | As listas e os links ficam só no armazenamento privado do app (`filesDir`), nunca no `/sdcard`, e o app tem `allowBackup="false"`. O log e a página mostram só o **domínio** (`TesteCanais.dominio`, com teste). A URL completa só aparece na página, depois do PIN, para quem já pode trocá-la. |
| 2 | **Conteúdo malicioso nos nomes** | O `M3uParser` tira caracteres de controle e de formato (`\p{Cc}`, `\p{Cf}`, inclusive os de inversão de texto como U+202E) e corta tamanhos: nome 80, grupo 40, user-agent e referrer 256. A página usa só `textContent`, nunca `innerHTML`. |
| 3 | **SSRF / esquemas perigosos** | Só `http` e `https`, URL de até 2048 caracteres e sem espaço; `file://`, `mms://`, `rtsp://`, `javascript:` e outros são ignorados. Na URL de **lista**, também são recusados `localhost` e IPs de loopback, rede privada ou link-local. O `curl` da 1ª versão usava `--proto =http,https --proto-redir =http,https`. |
| 4 | **Falha no player** (libVLC é código nativo e processa vídeo de terceiros) | Só dois `#EXTVLCOPT` são aceitos (user-agent e referrer), o que evita o ataque clássico do VLC (SA-0804). A `libvlc-all:3.7.7` traz o VLC 3.0.25, posterior ao último boletim de segurança. Um canal que derruba o player fica bloqueado por 6 h (`Bloqueio`). |
| 5 | **Phishing e links** | O app nunca abre navegador nem link externo. As capas passam por `Capa.endereco`, com as mesmas regras do item 3, até 1 MB por imagem e decodificação reduzida. |
| 6 | **Qualquer aparelho do Wi-Fi mexendo na TV** | O servidor (porta 8765) só existe enquanto a tela "Configurar" está aberta. Pareamento com PIN de 6 dígitos (`SecureRandom`) mostrado na TV; 5 erros fecham o servidor. O token tem 32 bytes, fica só na memória e vale só naquela sessão. |
| 7 | **Site malicioso no navegador atacando a TV** (CSRF, DNS rebinding) | `Host` tem que ser exatamente `<IP-da-TV>:8765`; se vier `Origin`, tem que ser o da própria TV. Toda rota, menos a página e o pareamento, exige o cabeçalho `X-Token`, que um formulário de outro site não consegue mandar. Sem CORS. Respostas com `Content-Security-Policy` (nada externo, `frame-ancestors 'none'`) e `Cache-Control: no-store`. |
| 8 | **Atualização falsa** | O APK é assinado com uma chave que nunca entra no repositório; o Android só aceita atualização com a mesma assinatura. Conta do GitHub com 2FA. |
| 9 | **Log vazando dados** | O log grava só domínio, contagens e o tipo do erro, nunca a mensagem da exceção, que pode trazer a URL. Fica no armazenamento do app, com rodízio por tamanho. |

## Entrada não confiável: limites

- **Pedido HTTP**: linha e cabeçalhos até 8 KB, corpo até 64 KB, 10 s de tempo, uma conexão por vez. `Http.ler` devolve `null` para pedido malformado.
- **Listas**: download gravado aos pedaços, até 200 MB contados já descompactados. Se passar ou falhar, fica a versão anterior inteira.
- **Biblioteca**: até 60 mil filmes, 20 mil séries, 5 mil episódios por série e 5 mil canais ao vivo. O arquivo de cada série é `series/<número>.m3u`, com número gerado pelo app e nunca tirado do texto da lista, então `../` não tem efeito. Linha estragada no índice é ignorada (há teste com nome malicioso).
- **Avulsos pela página**: o formulário vira uma entrada M3U e passa pelo mesmo `M3uParser`; no máximo 300; a TV guarda a lista já reescrita, nunca o texto recebido.
- **Troca atômica**: listas e biblioteca são gravadas com nome provisório e trocadas no fim, então quem lê nunca pega um arquivo pela metade.

## Limites conhecidos

- Um nome de domínio que o DNS resolve para um IP privado passa pela regra do item 3. Conferir no DNS se a página ganhar mais poder.
- Canais e filmes avulsos podem apontar para um IP da rede de casa. Só o player abre esse link, e só quem tem o PIN consegue cadastrá-lo.
- A página é servida em `http://` na rede local. O PIN, o token de sessão e o fato de o servidor só existir com a tela aberta reduzem o risco, mas não há TLS.

## Fontes

[VideoLAN SA-0804](https://www.videolan.org/sa0804.html) · [CVE-2024-46461](https://nvd.nist.gov/vuln/detail/CVE-2024-46461) · [boletins da VideoLAN](https://images.videolan.org/security) · [OWASP SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
