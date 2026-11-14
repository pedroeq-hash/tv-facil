#!/data/data/com.termux/files/usr/bin/bash
# Testa os canais das listas IPTV e gera canais_ok.m3u so com os que respondem.
# Uso no Termux: bash ~/iptv/testa_canais.sh
set -u

MAX_CANAIS=${MAX_CANAIS:-1000}   # limite combinado para nao pesar no stick
PARALELO=${PARALELO:-8}          # quantos canais testa ao mesmo tempo
TEMPO=${TEMPO:-10}               # segundos maximos de espera por canal
DIR=${DIR:-$HOME/iptv}
SAIDA=$DIR/canais_ok.m3u
REL=$DIR/relatorio.txt
COPIA=${COPIA:-/sdcard/iptv/canais_ok.m3u}   # copia que o Kodi le (memoria comum do stick)
TMP=$DIR/tmp

mkdir -p "$DIR"
# Enderecos das listas: um por linha em listas.txt (linhas com # sao ignoradas)
LISTAS=()
if [ -f "$DIR/listas.txt" ]; then
  while IFS= read -r l || [ -n "$l" ]; do
    l=${l%$'\r'}; l=${l#"${l%%[![:space:]]*}"}; l=${l%"${l##*[![:space:]]}"}
    [[ -z $l || $l == '#'* ]] || LISTAS+=("$l")
  done < "$DIR/listas.txt"
fi
if [ ${#LISTAS[@]} -eq 0 ]; then
  echo "Nenhuma lista em $DIR/listas.txt"
  exit 1
fi
# canais adicionados a mao (ex.: transmissoes oficiais de Sergipe) entram primeiro
[ -f "$DIR/extras.m3u" ] && LISTAS=("file://$DIR/extras.m3u" "${LISTAS[@]}")
# trava simples: impede dois testes rodando ao mesmo tempo
if ! mkdir "$DIR/.rodando" 2>/dev/null; then
  echo "Ja existe um teste rodando (apague $DIR/.rodando se tiver certeza que nao)."
  exit 1
fi
trap 'rm -rf "$DIR/.rodando" "$TMP"' EXIT
rm -rf "$TMP"; mkdir -p "$TMP/ok"
inicio=$(date +%s)

# 1) Baixa as listas, junta, tira repetidos (pela URL) e separa cada canal num arquivo
declare -A visto
n=0
cabecalho=""
for url in "${LISTAS[@]}"; do
  # So http/https (tambem nos redirecionamentos); file:// so para o extras.m3u local
  proto='=http,https'
  [ "$url" = "file://$DIR/extras.m3u" ] && proto='=file'
  if ! curl -sSL -m 60 --proto "$proto" --proto-redir =http,https -o "$TMP/lista.m3u" "$url"; then
    echo "Falhou ao baixar $url"
    continue
  fi
  bloco=""
  while IFS= read -r linha || [ -n "$linha" ]; do
    linha=${linha%$'\r'}
    case "$linha" in
      '#EXTM3U'*) [ -z "$cabecalho" ] && cabecalho=$linha ;;
      '#EXTINF'*) bloco=$linha$'\n' ;;
      '#'*)       [ -n "$bloco" ] && bloco+=$linha$'\n' ;;   # ex.: #EXTVLCOPT (user-agent, referrer)
      '')         ;;
      *)
        if [ -n "$bloco" ] && [ -z "${visto[$linha]:-}" ] && [ "$n" -lt "$MAX_CANAIS" ]; then
          visto[$linha]=1
          n=$((n + 1))
          printf '%s%s\n' "$bloco" "$linha" > "$TMP/$(printf %04d "$n").m3u"
        fi
        bloco=""
        ;;
    esac
  done < "$TMP/lista.m3u"
done

# 2) Testa um canal: responde com sucesso e o comeco nao e uma pagina de erro?
# shortcut: nao toca o video; um .m3u8 que responde passa mesmo se os pedacos do video falharem.
# shortcut: so testa http/https; rtmp/udp sao descartados. Melhorar se muitos canais bons sumirem.
testa() {
  local arq=$1 url ua ref cod
  url=$(tail -n 1 "$arq")
  ua=$(sed -n 's/^#EXTVLCOPT:http-user-agent=//p' "$arq" | head -n 1)
  ref=$(sed -n 's/^#EXTVLCOPT:http-referrer=//p' "$arq" | head -n 1)
  local args=(-sL -m "$TEMPO" --proto =http,https --proto-redir =http,https -D "$arq.cab")
  [ -n "$ua" ] && args+=(-A "$ua")
  [ -n "$ref" ] && args+=(-e "$ref")
  # head corta a transmissao depois de 4 KB, para nao baixar video a toa
  curl "${args[@]}" "$url" 2>/dev/null | head -c 4096 > "$arq.dados"
  cod=$(grep -E '^HTTP/' "$arq.cab" 2>/dev/null | tail -n 1)
  cod=${cod#* }; cod=${cod%% *}
  case "$cod" in 2*) ;; *) return ;; esac
  [ -s "$arq.dados" ] || return
  head -c 512 "$arq.dados" | grep -qiE '<html|<!doctype' && return
  mv "$arq" "$TMP/ok/"
}

for arq in "$TMP"/[0-9]*.m3u; do
  [ -e "$arq" ] || break
  while [ "$(jobs -rp | wc -l)" -ge "$PARALELO" ]; do wait -n; done
  testa "$arq" &
done
wait

# 3) Organiza nos grupos combinados. Imprime "N Grupo" (N = ordem na lista) ou REMOVER.
GRUPOS=("" "Sergipe" "Noticias Brasil" "Emissoras abertas" "Filmes" "Esportes" "Outros")
ESPORTE_NOME='espn|sportv|esporte|fifa\+|(^|[^a-z])sports?([^a-z]|$)'
EMISSORAS='^(sbt|record( tv)?|band|redetv|tv cultura|(canal )?futura|rede brasil|tv brasil|tv senado|tv gazeta|tve |istv|com brasil|globo)'
classifica() {
  local l=$1 nome=${1##*,} id="" grupo=""
  [[ $l =~ tvg-id=\"([^\"]*)\" ]] && id=${BASH_REMATCH[1]}
  [[ $l =~ group-title=\"([^\"]*)\" ]] && grupo=${BASH_REMATCH[1]}
  if   [[ "$nome $id" =~ sergipe|aracaju|atalaia|aperip ]]; then echo 1
  elif [[ $id =~ \.pt($|@) || $nome =~ portugal|^rtp ]]; then echo REMOVER
  elif [[ $nome =~ caz[eé]\ ?tv ]]; then echo REMOVER # 1080p60: trava no stick (12/11/2026)
  elif [[ $nome =~ c[aâ]mara ]]; then echo 3
  elif [[ $grupo =~ sports || $nome =~ $ESPORTE_NOME ]]; then echo 5
  elif [[ $grupo =~ religious|shop|legislative ]]; then echo REMOVER
  elif [[ -z $grupo || $grupo =~ ^undefined$ ]]; then echo REMOVER
  elif [[ $grupo =~ news || $nome =~ (^|[^a-z])news ]]; then echo 2
  elif [[ $nome =~ $EMISSORAS ]]; then echo 3
  elif [[ $grupo =~ movies ]]; then echo 4
  else echo 6
  fi
}
shopt -s nocasematch
mkdir -p "$TMP/final"
removidos=0
for arq in "$TMP"/ok/*.m3u; do
  [ -e "$arq" ] || break
  inf=$(head -n 1 "$arq")
  g=$(classifica "$inf")
  if [ "$g" = REMOVER ]; then removidos=$((removidos + 1)); continue; fi
  [[ $inf =~ ^(#EXTINF:[^\ ,]*)(.*)$ ]]
  base=${BASH_REMATCH[1]}
  resto=$(sed 's/ group-title="[^"]*"//' <<< "${BASH_REMATCH[2]}")
  { echo "$base group-title=\"${GRUPOS[$g]}\"$resto"; tail -n +2 "$arq"; } >> "$TMP/final/$g.m3u"
done
shopt -u nocasematch

# 4) Monta a lista final. Se nada passou (ex.: sem internet), mantem a lista antiga.
ok=$(find "$TMP/ok" -name '*.m3u' | wc -l)
final=$(cat "$TMP"/final/*.m3u 2>/dev/null | grep -c '^#EXTINF')
agora=$(date '+%F %T')
if [ "$n" -eq 0 ] || [ "$final" -eq 0 ]; then
  echo "$agora ERRO: $n canais lidos, $ok funcionando, $final na lista - lista antiga mantida" | tee -a "$REL"
  exit 1
fi
{ echo "${cabecalho:-#EXTM3U}"; cat "$TMP"/final/*.m3u; } > "$SAIDA.novo" && mv "$SAIDA.novo" "$SAIDA"
dur=$(( $(date +%s) - inicio ))
resumo=""
for g in 1 2 3 4 5 6; do
  resumo+="${GRUPOS[$g]}=$(grep -c '^#EXTINF' "$TMP/final/$g.m3u" 2>/dev/null || echo 0); "
done
echo "$agora $ok de $n canais funcionando, $removidos removidos pelos filtros, $final na lista ($((dur / 60))min $((dur % 60))s)" | tee -a "$REL"
echo "$agora grupos: $resumo" | tee -a "$REL"

# 5) Copia para o Kodi: grava com nome provisorio e troca no fim, para o Kodi nunca ler pela metade
if mkdir -p "$(dirname "$COPIA")" && cp "$SAIDA" "$COPIA.novo" && mv "$COPIA.novo" "$COPIA"; then
  echo "$agora copia atualizada em $COPIA" | tee -a "$REL"
else
  echo "$agora ERRO: nao consegui copiar para $COPIA (permissao de armazenamento?)" | tee -a "$REL"
  exit 2
fi
