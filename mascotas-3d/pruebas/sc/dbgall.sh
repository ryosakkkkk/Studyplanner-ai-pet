#!/bin/bash
cd /home/claude/w/rig4
run() { KEEPPART=1 timeout 1500 python3 rig4.py -- $1 out/$1_dbg.blend > out/$1_dbg.log 2>&1; echo "dbg $1 traceback=$(grep -c Traceback out/$1_dbg.log)"; }
run zorro & run panda & wait
run buho & run pinguino & wait
run conejo
echo DBG_LISTO
