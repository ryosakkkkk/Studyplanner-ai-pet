#!/usr/bin/env python3
"""Genera app/src/main/assets/web/camino/nativo.js con los temas que ya trae la app (títulos) y una copia de sus preguntas de banco para el
diagnóstico. Las preguntas son las propias del banco de la app (assets/tutor/banco_preguntas_*.json)."""
import json, sys, os
# uso (desde la raíz del repositorio): python herramientas/camino/nativo.py
RAIZ = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
APP = sys.argv[1] if len(sys.argv) > 1 else os.path.join(RAIZ, 'app', 'src', 'main', 'assets', 'tutor')
MATE = {  # temas de matemática de la app (ejercicios generados en Kotlin): id -> título
 'ecuaciones-lineales': 'Ecuaciones de primer grado', 'sistemas-2x2': 'Sistemas de ecuaciones lineales 2×2',
 'porcentajes': 'Porcentajes: aumentos y descuentos sucesivos', 'regla-de-tres': 'Regla de tres simple, directa e inversa',
 'mcd-mcm': 'MCD y MCM', 'interes-simple': 'Interés simple', 'leyes-de-exponentes': 'Leyes de exponentes',
 'ecuaciones-cuadraticas': 'Ecuaciones cuadráticas con raíces enteras', 'logaritmos': 'Logaritmos: definición y propiedades',
 'angulos-triangulo': 'Ángulos en el triángulo', 'pitagoras': 'Teorema de Pitágoras',
 'razones-trigonometricas': 'Razones trigonométricas en triángulos notables', 'sucesiones-aritmeticas': 'Sucesiones aritméticas',
 'promedios': 'Promedios: aritmético y ponderado'}
temas = {k: {'t': v} for k, v in MATE.items()}; banco = {}
n = 0
for f in ('banco_preguntas_v1.json', 'banco_preguntas_v1b.json', 'banco_preguntas_v2.json'):
    d = json.load(open(f'{APP}/{f}', encoding='utf-8'))
    for t in d['topics']:
        temas[t['id']] = {'t': t['title']}
        banco[t['id']] = [{'e': q['statement'], 'o': q['options'], 'c': q['correctIndex'], 'x': q['explanation'], 'd': q['difficulty']} for q in t['questions']]
        n += len(t['questions'])
out = 'window.NATIVO = ' + json.dumps({'temas': temas, 'banco': banco}, ensure_ascii=False, separators=(',', ':')) + ';\n'
open(os.path.join(RAIZ, 'app', 'src', 'main', 'assets', 'web', 'camino', 'nativo.js'), 'w', encoding='utf-8').write('// Generado por tools/nativo.py desde el banco de la app. No editar a mano.\n' + out)
print('temas', len(temas), 'preguntas', n, 'bytes', len(out))
