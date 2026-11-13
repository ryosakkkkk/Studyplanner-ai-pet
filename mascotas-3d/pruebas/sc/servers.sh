#!/bin/bash
# servidores de prueba: 8766 actual, 8767 0.22.1, 8768 diagnóstico actual, 8769 0.22.2, 8770 diagnóstico 0.22.2
start() { (cd "$1" && nohup python3 -m http.server "$2" --bind 127.0.0.1 > /tmp/claude-0/http_$2.log 2>&1 &) ; }
for pair in "/home/claude/w/v22web 8766" "/home/claude/w/v221web 8767" "/home/claude/w/v22dbg 8768" "/home/claude/w/v222web 8769" "/home/claude/w/v222dbg 8770"; do
  set -- $pair
  if ! (ss -ltn 2>/dev/null | grep -q ":$2 "); then start "$1" "$2"; fi
done
sleep 1
ss -ltn 2>/dev/null | grep -E ":87(6[6-9]|70) " | wc -l
