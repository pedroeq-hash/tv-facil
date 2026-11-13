#!/data/data/com.termux/files/usr/bin/sh
# Executado pelo Termux:Boot quando o stick liga (fica em ~/.termux/boot/).
termux-wake-lock   # impede o Android de fechar o Termux
nohup bash "$HOME/iptv/repete.sh" > /dev/null 2>&1 &
