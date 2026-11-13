#!/bin/bash
# barrido de TODOS los clips (8 vistas, cada 4 cuadros): 0.22.2 (puerto 8770) y 0.23 (puerto 8768)
cd /home/claude/w/v23test
port=$1; tag=$2
for p in zorro gato panda buho pinguino conejo; do
  node scan.js $port $tag $p all 320 az8 4 >> scan/$tag.log 2>&1
done
echo OTROS_LISTO_$tag >> scan/$tag.log
