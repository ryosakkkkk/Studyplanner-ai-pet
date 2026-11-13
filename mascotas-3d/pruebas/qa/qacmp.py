#!/usr/bin/env python3
# compara QA nuevo contra 0.22.2: python3 qacmp.py <tag> [pets]
import json, sys
tag = sys.argv[1]; pets = sys.argv[2].split(',') if len(sys.argv) > 2 else ['zorro','gato','panda','buho','pinguino','conejo']
for p in pets:
    a = json.load(open(f'qa23_{p}_0222.json')); b = json.load(open(f'qa23_{p}_{tag}.json'))
    ia, ib = a['info'], b['info']
    print(f"== {p}: vértices {ia['vertices']}→{ib['vertices']} caras {ia['caras']}→{ib['caras']} sin_peso {ib['sin_peso']} infl {ib['influencias_max']}")
    worst = []
    for c in sorted(b['clips']):
        ra, rb = a['clips'].get(c), b['clips'][c]
        if ra is None: print('   clip nuevo', c); continue
        fl = []
        if rb['alarga_max_mm'] > ra['alarga_max_mm'] + 3: fl.append('ALARGA+')
        if rb['pico_max_mm'] > ra['pico_max_mm'] + 3: fl.append('PICO+')
        if abs(rb['dur'] - ra['dur']) > 1e-3: fl.append('DUR!')
        if rb['ciclo'] and rb['cierre_mm'] > 0.5: fl.append('CIERRE!')
        vmin = min(rb.get('vol_brazoL_min', 1), rb.get('vol_brazoR_min', 1)); vmax = max(rb.get('vol_brazoL_max', 1), rb.get('vol_brazoR_max', 1))
        if vmin < 0.8 or vmax > 1.3: fl.append('VOLBRAZO')
        pa = rb.get('peor_arista') or {}
        print(f"   {c:10s} alarga {ra['alarga_max_mm']:5.1f}→{rb['alarga_max_mm']:5.1f} (p99.95 {ra['alarga_p9995_mm']:4.1f}→{rb['alarga_p9995_mm']:4.1f}) pico {ra['pico_max_mm']:4.1f}→{rb['pico_max_mm']:4.1f} brazos {vmin:.2f}-{vmax:.2f} paso {ra['paso_max_mm']:4.1f}→{rb['paso_max_mm']:4.1f} {' '.join(fl)}" + (f"  | peor {pa.get('pos')} {[w[:2] for w in pa.get('pesos', [])]}" if fl and pa else ''))
