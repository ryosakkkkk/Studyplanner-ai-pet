#!/usr/bin/env python3
"""Mapas de calor de las zonas expuestas (frente XZ, lado YZ, arriba XY) sobre el contorno de la malla en reposo.
uso: voxmap.py <pos.json> <glb> <salida.png>"""
import sys, json, numpy as np, struct
from PIL import Image, ImageDraw
d = json.load(open(sys.argv[1]))
# posiciones de la malla principal del GLB (para el contorno)
buf = open(sys.argv[2], 'rb').read()
jl = struct.unpack('<I', buf[12:16])[0]; J = json.loads(buf[20:20 + jl]); bin0 = 20 + jl + 8
best = None
for m in J['meshes']:
    for p in m['primitives']:
        a = J['accessors'][p['attributes']['POSITION']]
        if best is None or a['count'] > best['count']: best = a
bv = J['bufferViews'][best['bufferView']]
off = bin0 + bv.get('byteOffset', 0) + best.get('byteOffset', 0)
st = bv.get('byteStride', 12)
V = np.frombuffer(buf, np.uint8, count=best['count'] * st, offset=off).reshape(-1, st)[:, :12].copy().view(np.float32).reshape(-1, 3)
Vb = np.c_[V[:, 0], -V[:, 2], V[:, 1]]          # Blender: x, y (atrás +), z
vox = np.array(d['vox']); S = 640
def panel(ax, ay, flipx, title):
    im = np.full((S, S, 3), 255, np.uint8)
    def to_px(a, b):
        u = (a + 0.5) if not flipx else (0.5 - a)
        return np.clip((u * S).astype(int), 0, S - 1), np.clip(((1 - b) * S).astype(int), 0, S - 1)
    px, py = to_px(Vb[:, ax], Vb[:, ay] if ay == 2 else Vb[:, ay] + 0.5)
    im[py, px] = (205, 205, 205)
    if len(vox):
        key = vox[:, 0]; r = (key >> 12) & 63; g = (key >> 6) & 63; b = key & 63
        X = np.c_[(r * 4 + 2) / 255 - 0.5, -((b * 4 + 2) / 255 - 0.5), (g * 4 + 2) / 255]
        H = np.zeros((S, S))
        qx, qy = to_px(X[:, ax], X[:, ay] if ay == 2 else X[:, ay] + 0.5)
        np.add.at(H, (qy, qx), vox[:, 1])
        from scipy import ndimage
        H = ndimage.maximum_filter(H, size=7)
        m = H > 0
        v = np.clip(np.log10(H[m] + 1) / 4.0, 0, 1)
        col = np.stack([255 * np.ones_like(v), 220 * (1 - v), 40 * (1 - v)], 1).astype(np.uint8)
        im[m] = col
    I = Image.fromarray(im); ImageDraw.Draw(I).text((6, 4), title, fill=(0, 0, 0))
    return I
W = Image.new('RGB', (S * 3, S), (255, 255, 255))
W.paste(panel(0, 2, False, f"{d['pet']} FRENTE (x, z)  amarillo→rojo = más píxeles (log)"), (0, 0))
W.paste(panel(1, 2, False, 'LADO (y atrás → derecha, z)'), (S, 0))
W.paste(panel(0, 1, False, 'ARRIBA (x, y atrás ↑)'), (S * 2, 0))
W.save(sys.argv[3]); print('mapa', sys.argv[3])
