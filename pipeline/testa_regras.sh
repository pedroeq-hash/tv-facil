#!/usr/bin/env bash
# Confere as regras de grupo do testa_canais.sh com canais de exemplo (roda no PC, sem internet).
# Uso: LANG=en_US.UTF-8 bash pipeline/testa_regras.sh
cd "$(dirname "$0")" || exit 1
source <(sed -n "/^GRUPOS=/,/^}/p" testa_canais.sh)
shopt -s nocasematch
falhas=0
confere() {
  local r; r=$(classifica "$1")
  if [ "$r" = "$2" ]; then echo "ok   $r ${1##*,}"; else echo "ERRO esperado=$2 veio=$r ${1##*,}"; falhas=1; fi
}
confere '#EXTINF:-1 tvg-id="ESPNBrazil.br" group-title="Sports",ESPN (Brazil) (1080p)' 5
confere '#EXTINF:-1 tvg-id="CazeTV.br" group-title="Entertainment",CazeTV (1080p)' REMOVER
confere '#EXTINF:-1 group-title="Undefined",Cazé TV' REMOVER
confere '#EXTINF:-1 tvg-id="FIFAPlus.int@Portuguese" group-title="General",FIFA+ Portuguese (720p)' 5
confere '#EXTINF:-1 tvg-id="CNNBrasil.br" group-title="News",CNN Brasil (1080p)' 2
confere '#EXTINF:-1 group-title="Brazil",Record News' 2
confere '#EXTINF:-1 group-title="Brazil",TV Cultura' 3
confere '#EXTINF:-1 tvg-id="CanalFutura.br" group-title="Education",Canal Futura (720p)' 3
confere '#EXTINF:-1 group-title="Movies",Pluto TV Cine Sucessos' 4
confere '#EXTINF:-1 group-title="Religious",TV Aparecida' REMOVER
confere '#EXTINF:-1 tvg-id="RTP1.pt" group-title="General",RTP 1' REMOVER
confere '#EXTINF:-1 group-title="Undefined",TV Qualquer' REMOVER
confere '#EXTINF:-1 group-title="Kids",Box Kids TV (720p)' 6
confere '#EXTINF:-1 group-title="Entertainment",Adult Swim Latin America Brazil' 6
exit $falhas
