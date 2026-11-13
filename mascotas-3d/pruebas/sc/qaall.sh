#!/bin/bash
# QA de exportación: las seis nuevas (tag $1) y, si faltan, las seis de la 0.22.2
tag=${1:-p6}
cd /home/claude/w/qa
run() { timeout 1500 python3 qa23.py -- "$1" "$2" > "${2%.json}.log" 2>&1; }
for pair in "zorro gato" "panda buho" "pinguino conejo"; do
  set -- $pair
  run /home/claude/w/rig4/glb/$1.glb qa23_${1}_$tag.json &
  run /home/claude/w/rig4/glb/$2.glb qa23_${2}_$tag.json &
  wait
done
echo QA_NUEVAS_LISTO
for pair in "gato panda" "buho pinguino" "conejo zorro"; do
  set -- $pair
  for p in $1 $2; do
    if [ ! -s qa23_${p}_0222.json ] || [ ! -s qa23_${p}_0222.log ]; then run /home/claude/w/v222web/pets/$p.glb qa23_${p}_0222.json & fi
  done
  wait
done
echo QA_LISTO
