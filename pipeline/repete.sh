#!/data/data/com.termux/files/usr/bin/bash
# Roda o teste dos canais 2 min depois de ligar e repete a cada 6 horas.
# Iniciado pelo Termux:Boot (~/.termux/boot/). Duas copias nao se atropelam: o testa_canais.sh tem trava.
LOG=$HOME/iptv/repete.log
sleep 120   # deixa o stick terminar de ligar
while true; do
  echo "$(date '+%F %T') iniciando teste" >> "$LOG"
  PARALELO=4 bash "$HOME/iptv/testa_canais.sh" >> "$LOG" 2>&1   # 4 por vez: nao atrapalha quem assiste
  sleep 21600
done
