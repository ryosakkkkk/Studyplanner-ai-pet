#!/bin/bash
bash /tmp/claude-0/sc/buildall.sh > /tmp/claude-0/sc/buildall_p8.log 2>&1
cd /home/claude/w/rig4; for p in zorro gato panda buho pinguino conejo; do grep -E "^FORRO" out/$p.log; done > /tmp/claude-0/sc/forro_p8.txt
rm -f /home/claude/w/v23test/scan/p8.log
/tmp/claude-0/sc/ciclo.sh p8 celebrate,wave 2 > /tmp/claude-0/sc/ciclo_p8.log 2>&1
