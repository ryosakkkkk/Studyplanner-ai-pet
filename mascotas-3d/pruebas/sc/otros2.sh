#!/bin/bash
# continúa el barrido de todos los clips (8 vistas, cada 4 cuadros) desde el panda, las dos versiones a la vez
cd /home/claude/w/v23test
uno() { port=$1; tag=$2; for p in panda buho pinguino conejo; do node scan.js $port $tag $p all 320 az8 4 >> scan/$tag.log 2>&1; done; echo OTROS_LISTO_$tag >> scan/$tag.log; }
uno 8770 o0222 & uno 8768 o023 & wait
grep -c cuadros scan/o023.log scan/o0222.log
