#!/usr/bin/env python3
"""Informe del barrido por posición: agrupa los vóxeles expuestos en zonas del modelo (coordenadas de Blender:
x derecha de la imagen de frente, y hacia atrás, z altura). uso: voxrep.py <scan/tag/pet_pos.json> [n zonas]"""
import sys, json, numpy as np
from scipy import ndimage
d = json.load(open(sys.argv[1])); nshow = int(sys.argv[2]) if len(sys.argv) > 2 else 14
vox = np.array(d['vox'])            # clave, total, máx, clip, cuadro, vista
if len(vox) == 0: print('sin caras traseras visibles'); sys.exit(0)
key = vox[:, 0]; r = (key >> 12) & 63; g = (key >> 6) & 63; b = key & 63
G = np.zeros((64, 64, 64), np.int64); G[r, g, b] = vox[:, 1]
lab, n = ndimage.label(G > 0, structure=np.ones((3, 3, 3)))
tot = ndimage.sum(G, lab, index=np.arange(1, n + 1))
order = np.argsort(-tot)
nviews = len(d['views']); nfr = sum(len(c['rows']) for c in d['clips'].values())
print(f"{d['pet']}: {int(G.sum())} px de cara trasera en {nfr} cuadros x {nviews} vistas; {n} zonas")
L = lab[r, g, b]
for k in order[:nshow]:
    m = L == k + 1
    w = vox[m, 1].astype(float)
    # centro de vóxel -> coordenadas de reposo (glTF: x, altura, z) -> Blender (x, y = -z, altura)
    xs = (r[m] * 4 + 2) / 255 - 0.5; hs = (g[m] * 4 + 2) / 255; zs = (b[m] * 4 + 2) / 255 - 0.5
    X = np.c_[xs, -zs, hs]
    c = (X * w[:, None]).sum(0) / w.sum()
    i = int(np.argmax(vox[m, 2])); e = vox[m][i]
    print(f"  zona {int(tot[k]):7d} px | centro x {c[0]:+.2f} y {c[1]:+.2f} z {c[2]:.2f} | caja x[{X[:,0].min():+.2f},{X[:,0].max():+.2f}] y[{X[:,1].min():+.2f},{X[:,1].max():+.2f}] z[{X[:,2].min():.2f},{X[:,2].max():.2f}]"
          f" | peor: {d['clipNames'][e[3]]} cuadro {e[4]} vista {d['views'][e[5]]} ({e[2]} px)")
