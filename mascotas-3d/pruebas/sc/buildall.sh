#!/bin/bash
cd /home/claude/w/rig4
(./build22.sh zorro & ./build22.sh gato & wait)
(./build22.sh panda & ./build22.sh buho & wait)
(./build22.sh pinguino & ./build22.sh conejo & wait)
echo BUILD_LISTO
for p in zorro gato panda buho pinguino conejo; do grep -c Traceback out/$p.log; done
