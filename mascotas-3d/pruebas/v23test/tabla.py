#!/usr/bin/env python3
"""Tabla antes/después a partir de dos registros del barrido. uso: tabla.py <antes.log> <despues.log> <salida.csv>"""
import re, sys, csv
pat = re.compile(r'^\S+ (\w+) (\w+) cuadros (\d+) x vistas (\d+) \| caras traseras: máx (\d+) px, vistas con alguna \d+ de \d+ suma (\d+) \| piezas sueltas: máx (\d+) px')
def lee(f):
    d = {}
    for l in open(f, encoding='utf-8'):
        m = pat.match(l)
        if m: d[(m[1], m[2])] = (int(m[3]), int(m[4]), int(m[5]), int(m[6]), int(m[7]))
    return d
a, b = lee(sys.argv[1]), lee(sys.argv[2])
rows = []
for k in b:
    if k not in a: continue
    x, y = a[k], b[k]
    red = round(100 * (1 - y[3] / x[3])) if x[3] else 0
    rows.append([k[0], k[1], y[0], y[1], x[2], y[2], x[3], y[3], red, x[4], y[4]])
with open(sys.argv[3], 'w', newline='', encoding='utf-8') as f:
    w = csv.writer(f); w.writerow('mascota,animacion,cuadros_medidos,vistas,caras_traseras_max_px_0222,caras_traseras_max_px_023,suma_px_0222,suma_px_023,reduccion_%,piezas_sueltas_max_px_0222,piezas_sueltas_max_px_023'.split(','))
    w.writerows(rows)
peor = [r for r in rows if r[7] > r[6]]
print('filas', len(rows), '| peor que antes:', [(r[0], r[1], r[6], r[7]) for r in peor])
for r in rows: print(f'{r[0]:9s} {r[1]:10s} suma {r[6]:7d} -> {r[7]:6d} ({r[8]:3d} %)  máx {r[4]:4d} -> {r[5]:3d}  sueltas {r[9]} -> {r[10]}')
