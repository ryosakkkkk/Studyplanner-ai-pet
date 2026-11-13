#!/bin/bash
# v0.22: brazos originales recortados + parche del costado. Dos pasadas para las mascotas con corte en el hombro.
cd /home/claude/w/rig4
p=$1
if python3 -c "import json,sys; sys.exit(0 if json.load(open('cfg4.json'))['$p'].get('cut_t') is not None else 1)"; then
  PASS1=1 timeout 600 python3 rig4.py -- $p x.blend > out/pass1_$p.log 2>&1 || { echo "FALLO pasada1 $p"; exit 1; }
fi
timeout 1200 python3 rig4.py -- $p out/${p}_rig4.blend > out/$p.log 2>&1 || { echo "FALLO rig $p"; exit 1; }
timeout 1200 python3 /home/claude/w/rig4/anim22.py -- out/${p}_rig4.blend glb/${p}.glb out/${p}_anim.blend > out/${p}_anim.log 2>&1 || { echo "FALLO anim $p"; exit 1; }
echo "LISTO $p"
