#!/bin/bash
# ciclo completo de medición: QA de las seis (tag), publicar, visor de diagnóstico, barrido celebrate+wave
tag=${1:-p7}; clips=${2:-celebrate,wave}; step=${3:-2}
cd /home/claude/w/qa
for pair in "zorro gato" "panda buho" "pinguino conejo"; do
  set -- $pair
  for p in $1 $2; do (timeout 1500 python3 qa23.py -- /home/claude/w/rig4/glb/$p.glb qa23_${p}_$tag.json > qa23_${p}_$tag.log 2>&1 &) ; done
  sleep 1; while pgrep -f "qa23.py" >/dev/null; do sleep 2; done
done
echo QA_LISTO
cd /home/claude/w/rig4 && bash publish22.sh > /tmp/claude-0/sc/publish_$tag.log 2>&1
cd /home/claude/w && python3 v22dbg/make_dbg.py v22web/pet-viewer.html v22dbg/pet-viewer.html > /dev/null 2>&1
bash /tmp/claude-0/sc/servers.sh > /dev/null 2>&1
echo PUBLICADO
cd /home/claude/w/v23test
for p in zorro gato panda buho pinguino conejo; do
  node scan.js 8768 $tag $p $clips 320 full $step >> scan/$tag.log 2>&1
done
echo BARRIDO_LISTO
