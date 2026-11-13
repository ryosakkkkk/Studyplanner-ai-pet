#!/bin/bash
cd /home/claude/w/rig4
(./build22.sh zorro & ./build22.sh gato & wait); (./build22.sh panda & ./build22.sh buho & wait); ./build22.sh pinguino
for p in zorro gato panda buho pinguino conejo; do grep -E "^FORRO" out/$p.log; done > /tmp/claude-0/sc/forro_p9.txt
rm -f /home/claude/w/v23test/scan/p9.log
/tmp/claude-0/sc/ciclo.sh p9 celebrate,wave 2 > /tmp/claude-0/sc/ciclo_p9.log 2>&1
cd /home/claude/w/qa; for p in zorro gato panda buho pinguino conejo; do python3 compat23.py /home/claude/w/v222web/pets/$p.glb /home/claude/w/rig4/glb/$p.glb > compat_${p}_p9.log 2>&1; echo "$p compat $?" ; done > /tmp/claude-0/sc/compat_p9.txt
echo FINAL5_LISTO >> /tmp/claude-0/sc/ciclo_p9.log
