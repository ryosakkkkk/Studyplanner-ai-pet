// Ajustes y temas nuevos a partir de los exámenes 2025 y 2026 (CPU y ordinario):
// 1. Distribución de cursos de los grupos B y C como en esos exámenes (60 preguntas, 10 bloques).
// 2. Temas que esos exámenes preguntan y la app no tenía, con preguntas propias (ninguna copiada de un examen).
// Se carga después de datos.js y antes de plan.js.
(function (root) {
  'use strict';
  const D = root.DATOS;

  // ---------------------------------------------------------------- cursos y grupos
  D.cursos.ZOO = { n: 'Zoología', k: 'cie' };
  D.cursos.HIS.n = 'Historia del Perú y del mundo';   // así se llama el bloque en el examen: incluye historia universal
  const FUENTE = 'los exámenes CPU y ordinario de 2025 y 2026';
  D.grupos.A.fuente = FUENTE;
  D.grupos.B.cursos = { RM: 10, RV: 10, LEN: 5, ARI: 5, ALG: 5, FIS: 5, QUI: 5, BIO: 5, ZOO: 5, ECO: 5 };
  D.grupos.B.d = 'Biología, zoología, química y física pesan más.';
  D.grupos.B.fuente = FUENTE;
  D.grupos.C.cursos = { RM: 10, RV: 10, LEN: 5, ARI: 5, ALG: 5, ECO: 5, ECN: 5, HIS: 5, CIV: 5, GEG: 5 };
  D.grupos.C.d = 'Economía, historia, cívica, geografía y ecología pesan más.';
  D.grupos.C.fuente = FUENTE;
  // Los temas de zoología que ya trae la app pasan a su propio curso.
  D.lista.forEach(t => { if (t[0].indexOf('zoologia-') === 0) t[1] = 'ZOO'; });

  // ---------------------------------------------------------------- temas nuevos
  // tema(id, curso, peso, título, de dónde sale el tipo de pregunta, idea clave, preguntas)
  // cada pregunta: [enunciado, correcta, cuatro incorrectas…, explicación]
  function tema(id, curso, f, titulo, ref, idea, pregs) {
    D.lista.push([id, curso, f, titulo]);
    D.idea[id] = idea;
    D.pregs[id] = pregs.map(p => ({ e: p[0], o: p.slice(1, 6), c: 0, x: p[6], ref }));
  }
  const R = c => 'Exámenes CPU y ordinario 2025 y 2026 (' + c + ')';

  // ------------------------------------------------ Ecología (5 preguntas en los tres grupos)
  tema('eco-niveles', 'ECO', 1.3, 'Población, comunidad y relaciones entre especies', R('Ecología'), [
    'Los niveles van de menor a mayor: individuo, población (una sola especie), comunidad o biocenosis (varias poblaciones), ecosistema (biocenosis más biotopo) y biosfera.',
    'El biotopo es el lugar con sus factores sin vida. El hábitat es donde vive una especie y el nicho es la función que cumple allí.',
    'En una relación entre dos especies fíjate en quién gana y quién pierde: mutualismo (ganan las dos), comensalismo (gana una y a la otra le da igual), parasitismo y depredación (gana una y pierde la otra), competencia (pierden las dos).',
  ], [
    ['Un grupo de vicuñas que vive en la misma pampa altoandina y se reproduce entre sí forma:', 'una población', 'una comunidad', 'un ecosistema', 'un biotopo', 'una biosfera', 'Son individuos de una sola especie en un mismo lugar y tiempo: eso es una población. La comunidad reúne poblaciones de varias especies.'],
    ['El conjunto de todas las poblaciones de plantas, animales y microorganismos que conviven en una laguna es:', 'la comunidad o biocenosis', 'el biotopo', 'una población', 'el nicho ecológico', 'el bioma', 'Varias poblaciones de especies distintas que conviven forman la comunidad, también llamada biocenosis.'],
    ['El biotopo de un ecosistema está formado por:', 'el espacio físico y sus factores sin vida: suelo, agua, luz y temperatura', 'los productores y los consumidores', 'las poblaciones de una misma especie', 'los descomponedores del suelo', 'las relaciones entre las especies', 'Biotopo es el lugar y sus factores abióticos. Los seres vivos que lo ocupan forman la biocenosis; juntos son el ecosistema.'],
    ['La rama de la ecología que estudia las comunidades, es decir, el conjunto de poblaciones que conviven en un lugar, es la:', 'sinecología', 'autoecología', 'edafología', 'etología', 'taxonomía', 'La sinecología estudia comunidades. La autoecología estudia una sola especie frente a su ambiente.'],
    ['Las micorrizas son hongos unidos a las raíces: el hongo recibe azúcares y la planta absorbe mejor el agua y los minerales. Esa relación es de:', 'mutualismo', 'comensalismo', 'parasitismo', 'competencia', 'depredación', 'Las dos especies ganan, así que es mutualismo.'],
    ['La garrapata que se alimenta de la sangre de una alpaca y la debilita mantiene con ella una relación de:', 'parasitismo', 'mutualismo', 'comensalismo', 'amensalismo', 'competencia', 'La garrapata gana y la alpaca pierde, sin que la mate de inmediato: es parasitismo.'],
    ['El nicho ecológico de una especie es:', 'la función que cumple en el ecosistema: qué come, quién se la come y cómo usa los recursos', 'el lugar físico donde vive', 'el número de individuos que tiene', 'la zona de transición entre dos ecosistemas', 'el conjunto de factores sin vida del lugar', 'El lugar donde vive es el hábitat. El nicho es su «oficio» dentro del ecosistema.'],
    ['La zona de transición entre dos ecosistemas vecinos, como el borde entre un bosque y un pajonal, se llama:', 'ecotono', 'biotopo', 'bioma', 'nicho', 'hábitat', 'En el ecotono se mezclan especies de los dos ecosistemas.'],
  ]);
  tema('eco-energia', 'ECO', 1.2, 'Cadenas alimenticias y ciclos de la materia', R('Ecología'), [
    'La energía entra por los productores, que hacen fotosíntesis, y pasa a los consumidores. En cada paso se pierde la mayor parte como calor.',
    'La materia sí se recicla: los descomponedores (hongos y bacterias) devuelven los nutrientes al suelo.',
    'Ciclo del nitrógeno: unas bacterias lo fijan desde el aire. Ciclo del carbono: entra a los seres vivos por la fotosíntesis y vuelve al aire por la respiración y la combustión.',
  ], [
    ['En una pirámide alimenticia, la base o primer nivel trófico la forman:', 'los productores, como plantas y algas', 'los herbívoros', 'los carnívoros', 'los descomponedores', 'los carroñeros', 'Los productores fabrican su alimento con la luz del sol y sostienen a todos los demás niveles.'],
    ['En la cadena ichu → vicuña → puma, la vicuña es:', 'consumidor primario', 'productor', 'consumidor secundario', 'descomponedor', 'consumidor terciario', 'La vicuña come al productor (el ichu), por eso es consumidor primario. El puma, que se la come, es consumidor secundario.'],
    ['Los hongos y las bacterias que transforman los restos de seres vivos en sustancias minerales son:', 'descomponedores', 'productores', 'consumidores primarios', 'consumidores secundarios', 'depredadores', 'Los descomponedores cierran el ciclo de la materia: devuelven los nutrientes al suelo.'],
    ['Cuando la energía pasa de un nivel trófico al siguiente, la cantidad disponible:', 'disminuye, porque gran parte se pierde como calor', 'aumenta, porque se acumula', 'se mantiene igual', 'se duplica en cada nivel', 'desaparece por completo', 'Solo pasa cerca de la décima parte al nivel siguiente. Por eso hay menos carnívoros que herbívoros.'],
    ['La fijación del nitrógeno del aire en el suelo la realizan principalmente:', 'bacterias, como Rhizobium en las raíces de las leguminosas', 'las lombrices de tierra', 'las algas pardas', 'los virus del suelo', 'los hongos de sombrero', 'Las bacterias fijadoras convierten el nitrógeno del aire en compuestos que las plantas sí pueden usar.'],
    ['El proceso por el que el carbono del aire (CO₂) pasa a formar parte de la materia orgánica de las plantas es la:', 'fotosíntesis', 'respiración', 'combustión', 'descomposición', 'transpiración', 'En la fotosíntesis la planta toma CO₂ y agua y fabrica glucosa. La respiración y la combustión hacen lo contrario: devuelven CO₂ al aire.'],
    ['En el ciclo del agua, el paso del vapor de agua a gotitas líquidas que forman las nubes se llama:', 'condensación', 'evaporación', 'precipitación', 'infiltración', 'transpiración', 'Condensar es pasar de gas a líquido. La evaporación es el paso contrario.'],
    ['Para la fotosíntesis, las plantas usan sobre todo la radiación solar:', 'visible', 'ultravioleta', 'infrarroja', 'de rayos X', 'de microondas', 'La clorofila absorbe luz visible, sobre todo la azul y la roja.'],
  ]);
  tema('eco-global', 'ECO', 1.2, 'Suelo, cambio climático y capa de ozono', R('Ecología'), [
    'Efecto invernadero: gases como el CO₂ y el metano retienen el calor (rayos infrarrojos). Si aumentan demasiado, el planeta se calienta.',
    'La capa de ozono está en la estratósfera y filtra los rayos ultravioleta. Los CFC o freones la destruyen.',
    'Acuerdos: Protocolo de Montreal (1987, capa de ozono), Protocolo de Kioto (1997, gases de efecto invernadero) y Acuerdo de París (2015, limitar el calentamiento).',
  ], [
    ['La capa de ozono, que filtra la radiación ultravioleta, se encuentra en la:', 'estratósfera', 'tropósfera', 'mesósfera', 'termósfera', 'exósfera', 'La mayor parte del ozono está en la estratósfera, entre unos 15 y 35 km de altura.'],
    ['El gas que más ha aumentado por la quema de combustibles fósiles y que refuerza el efecto invernadero es el:', 'dióxido de carbono (CO₂)', 'oxígeno (O₂)', 'nitrógeno (N₂)', 'argón (Ar)', 'hidrógeno (H₂)', 'El CO₂ absorbe los rayos infrarrojos que la Tierra devuelve y así retiene calor.'],
    ['El Protocolo de Montreal (1987) es un acuerdo internacional para:', 'reducir las sustancias que destruyen la capa de ozono, como los CFC', 'reducir los gases de efecto invernadero', 'proteger los humedales', 'controlar el comercio de especies amenazadas', 'regular el traslado de desechos peligrosos', 'Montreal trata del ozono. Los gases de efecto invernadero son tema de Kioto y de París.'],
    ['Los clorofluorocarbonos (CFC o freones) de las refrigeradoras y los aerosoles antiguos dañan el ambiente porque:', 'liberan cloro, que destruye las moléculas de ozono', 'producen lluvia ácida', 'aumentan el oxígeno del aire', 'vuelven ácidos los suelos', 'enfrían los océanos', 'En la estratósfera, la luz ultravioleta rompe los CFC y el cloro que sueltan destruye el ozono.'],
    ['El desgaste del suelo por acción del agua o del viento, que le hace perder su capa fértil, se llama:', 'erosión', 'sedimentación', 'salinización', 'eutrofización', 'compactación', 'La erosión arrastra la capa superior del suelo, que es la más fértil.'],
    ['Las partículas minerales del suelo, ordenadas de mayor a menor tamaño, son:', 'grava, arena, limo y arcilla', 'arcilla, limo, arena y grava', 'arena, grava, arcilla y limo', 'limo, arcilla, grava y arena', 'grava, limo, arena y arcilla', 'La grava es la más gruesa y la arcilla la más fina; la arena y el limo quedan en medio.'],
    ['El enriquecimiento excesivo de un lago con nitratos y fosfatos, que hace crecer muchas algas y agota el oxígeno, se llama:', 'eutrofización', 'bioacumulación', 'erosión', 'desertificación', 'salinización', 'Con tantos nutrientes las algas se multiplican; al morir se descomponen y ese proceso consume el oxígeno del agua.'],
    ['Es una fuente de energía renovable:', 'la energía solar', 'el petróleo', 'el gas natural', 'el carbón mineral', 'el uranio', 'La energía del Sol no se agota con el uso. Los otros cuatro son recursos que se acaban.'],
    ['El agua líquida alcanza su mayor densidad (1 g/cm³) a una temperatura de:', '4 °C', '0 °C', '25 °C', '100 °C', '−4 °C', 'A 4 °C el agua es más densa. Por eso el hielo flota y el fondo de los lagos fríos no se congela.'],
  ]);

  // ------------------------------------------------ Lenguaje (5 preguntas en los tres grupos, en el bloque «Comunicación»)
  tema('len-lengua', 'LEN', 1.2, 'Lenguaje, lengua, habla y variedades', R('Comunicación'), [
    'Lenguaje: capacidad de todos los seres humanos para comunicarse (es universal). Lengua: sistema de signos de una comunidad (es social). Habla: uso que hace cada persona (es individual).',
    'Dialecto: variedad de una lengua según la región. Sociolecto: según el grupo social. Idiolecto: la manera de hablar de una sola persona.',
    'El Perú es multilingüe: tiene 48 lenguas originarias (44 amazónicas y 4 andinas), agrupadas en 19 familias lingüísticas.',
  ], [
    ['La capacidad que tienen todos los seres humanos para comunicarse mediante signos es:', 'el lenguaje', 'la lengua', 'el habla', 'el dialecto', 'el idiolecto', 'El lenguaje es la facultad, común a toda la humanidad. La lengua es el sistema concreto de una comunidad.'],
    ['El uso individual y concreto que cada persona hace de su lengua en un momento dado es:', 'el habla', 'el lenguaje', 'la lengua', 'la norma', 'el dialecto', 'El habla es individual y momentánea; la lengua es de toda la comunidad.'],
    ['El castellano, el quechua y el aimara son ejemplos de:', 'lenguas', 'lenguajes', 'hablas', 'dialectos de una misma lengua', 'idiolectos', 'Cada uno es un sistema de signos propio de una comunidad, es decir, una lengua o idioma.'],
    ['La variedad de una lengua que depende de la región donde se habla, como el castellano andino o el amazónico, se llama:', 'dialecto', 'idiolecto', 'sociolecto', 'jerga', 'lenguaje', 'La variación por región es el dialecto. Ningún dialecto es mejor que otro.'],
    ['La manera particular de hablar de una sola persona, con sus muletillas y su entonación, es su:', 'idiolecto', 'dialecto', 'sociolecto', 'lengua', 'replana', 'Idio- significa «propio»: el idiolecto es el habla propia de cada individuo.'],
    ['Según el Ministerio de Cultura, las lenguas originarias del Perú son:', '48: 44 amazónicas y 4 andinas', '24: 12 amazónicas y 12 andinas', '19: 17 amazónicas y 2 andinas', '84: 80 amazónicas y 4 andinas', '48: 4 amazónicas y 44 andinas', 'Son 48 lenguas originarias. El número 19 corresponde a las familias lingüísticas que las agrupan.'],
    ['Que el Perú sea un país multilingüe quiere decir que:', 'en su territorio se hablan muchas lenguas', 'todos sus habitantes hablan dos lenguas', 'tiene una sola lengua con muchos dialectos', 'el castellano es la única lengua que se usa', 'todas sus lenguas vienen de una misma familia', 'Multilingüe: muchas lenguas en un mismo territorio.'],
    ['Un conjunto de lenguas que provienen de una misma lengua antigua forma:', 'una familia lingüística', 'un dialecto', 'un sociolecto', 'un alfabeto', 'una jerga', 'Por ejemplo, las lenguas quechuas forman una familia y las lenguas aru (aimara, jaqaru, cauqui) otra.'],
  ]);
  tema('len-fonetica', 'LEN', 1.0, 'Fonemas: vocales y consonantes', R('Comunicación'), [
    'El fonema es la unidad mínima de sonido que distingue significados: /p/ y /b/ distinguen «pata» de «bata».',
    'Por el punto donde se articulan, las consonantes son bilabiales (p, b, m), labiodental (f), dentales (t, d), alveolares (s, n, l, r), palatales (ch, ñ, y, ll) y velares (k, g, j).',
    'Por el modo: oclusivas (el aire se detiene y sale de golpe: p, t, k, b, d, g), fricativas (el aire sale rozando: f, s, j) y nasales (sale por la nariz: m, n, ñ).',
  ], [
    ['Los sonidos de p, b y m se articulan juntando los dos labios; por eso son:', 'bilabiales', 'dentales', 'alveolares', 'palatales', 'velares', 'Bi-labial: intervienen los dos labios.'],
    ['Son consonantes alveolares (la lengua toca los alvéolos, detrás de los dientes de arriba):', 's, n, l, r', 'p, b, m', 'k, g, j', 'ch, ñ, y', 'f, t, d', 'Al decir s, n, l y r la punta de la lengua se acerca a los alvéolos.'],
    ['Los sonidos de «ch» y «ñ», como en «chullo» y «ñaño», son:', 'palatales', 'bilabiales', 'labiodentales', 'alveolares', 'velares', 'Se articulan con la lengua contra el paladar duro.'],
    ['En las consonantes fricativas, como f y s, el aire:', 'sale rozando por un paso estrecho, sin cortarse', 'se detiene por completo y luego sale de golpe', 'sale solo por la nariz', 'hace vibrar la punta de la lengua', 'sale por los costados de la lengua', 'Fricativa viene de fricción: el aire roza. Si se detiene y sale de golpe, la consonante es oclusiva.'],
    ['La unidad mínima de sonido de una lengua, que no tiene significado pero sirve para distinguir palabras, es el:', 'fonema', 'morfema', 'lexema', 'grafema', 'sema', 'El morfema y el lexema ya tienen significado; el grafema es la letra escrita.'],
    ['Las palabras «peso» y «beso» se distinguen por un solo:', 'fonema', 'morfema', 'acento', 'sufijo', 'lexema', 'Solo cambia /p/ por /b/, y con eso cambia el significado.'],
    ['En castellano, las vocales cerradas son:', 'i, u', 'a, e, o', 'a, i', 'e, o', 'a, u', 'Al decir i y u la boca se cierra más. Las vocales a, e y o son abiertas.'],
    ['La disciplina que estudia los fonemas, es decir, los sonidos en cuanto distinguen significados, es la:', 'fonología', 'semántica', 'morfología', 'sintaxis', 'lexicología', 'La fonología estudia los fonemas. La semántica estudia los significados.'],
  ]);

  // ------------------------------------------------ Razonamiento verbal
  tema('rv-semantica', 'RV', 1.0, 'Semántica y etimología', R('Razonamiento verbal'), [
    'La semántica estudia el significado de las palabras; la etimología, su origen.',
    'El sema es cada rasgo mínimo de significado. Las palabras que comparten un sema forman un campo semántico.',
    'Denotación: el significado literal. Connotación: el significado figurado o que depende del contexto.',
  ], [
    ['La disciplina que estudia el origen de las palabras es la:', 'etimología', 'semántica', 'fonología', 'sintaxis', 'ortografía', 'Etimología: estudio del origen de las palabras. La semántica estudia su significado.'],
    ['El sema es:', 'cada rasgo mínimo de significado de una palabra', 'la unidad mínima de sonido', 'la sílaba con mayor fuerza de voz', 'la raíz de una palabra', 'el conjunto de palabras de un idioma', 'Por ejemplo, «silla» tiene los semas «mueble», «para sentarse» y «con respaldo».'],
    ['En «Pedro es un zorro para los negocios», la palabra «zorro» se usa con significado:', 'connotativo (figurado)', 'denotativo (literal)', 'etimológico', 'técnico', 'científico', 'No se habla del animal: se dice que Pedro es astuto. Eso es connotación.'],
    ['La palabra «flebitis» significa «inflamación de las venas». El sufijo griego -itis significa:', 'inflamación', 'dolor', 'estudio', 'miedo', 'tumor', 'Como en apendicitis, bronquitis u otitis. «Dolor» es -algia y «miedo» es -fobia.'],
    ['Por sus raíces griegas, «democracia» significa:', 'gobierno del pueblo', 'gobierno de los ricos', 'poder de los dioses', 'estudio del pueblo', 'amor al pueblo', 'Demos es «pueblo» y kratos es «poder o gobierno».'],
    ['La raíz griega «hidro», en hidrografía e hidratar, significa:', 'agua', 'tierra', 'fuego', 'aire', 'vida', '«Tierra» es geo y «vida» es bio.'],
    ['Las palabras mesa, silla, ropero y cama forman un campo semántico porque:', 'comparten un rasgo de significado: todas son muebles', 'suenan parecido', 'tienen el mismo número de sílabas', 'vienen del griego', 'son sinónimas entre sí', 'Un campo semántico reúne palabras con un sema en común.'],
    ['Por su raíz latina, «omnívoro» es el animal que:', 'come de todo', 'come solo carne', 'come solo plantas', 'come insectos', 'no necesita comer', 'Omni- significa «todo» y -voro, «que come».'],
  ]);

  // ------------------------------------------------ Educación cívica (5 preguntas en el grupo C)
  tema('civ-familia', 'CIV', 1.2, 'Familia, parentesco y sucesión', R('Educación cívica'), [
    'Parentesco: por consanguinidad (misma sangre: padres, hijos, hermanos), por afinidad (nace del matrimonio: suegros, cuñados) y por adopción.',
    'La patria potestad es el deber y el derecho de los padres de cuidar a sus hijos menores y los bienes de ellos.',
    'Sucesión: con testamento es testamentaria; sin testamento es intestada o legal, y la ley señala a los herederos.',
  ], [
    ['El parentesco que une a una persona con sus padres, hijos y hermanos es por:', 'consanguinidad', 'afinidad', 'adopción', 'vecindad', 'alianza', 'Consanguinidad: descienden unos de otros o de un tronco común.'],
    ['El parentesco que nace del matrimonio y une a cada cónyuge con los parientes del otro, como suegros y cuñados, es por:', 'afinidad', 'consanguinidad', 'adopción', 'tutela', 'línea recta', 'La afinidad no viene de la sangre: viene del matrimonio.'],
    ['El deber y el derecho de los padres de cuidar a sus hijos menores de edad y los bienes de ellos se llama:', 'patria potestad', 'tutela', 'curatela', 'adopción', 'emancipación', 'La tutela es para el menor que no tiene padres que lo cuiden; la curatela, para mayores que no pueden valerse.'],
    ['Según el Código Civil, el vínculo del matrimonio se disuelve con:', 'el divorcio', 'la separación de cuerpos', 'la separación de bienes', 'el cambio de domicilio', 'la pérdida de la patria potestad', 'El divorcio disuelve el vínculo. La separación de cuerpos solo suspende la vida en común: el vínculo sigue.'],
    ['Cuando una persona muere sin dejar testamento y sus bienes se reparten como manda la ley, la sucesión es:', 'intestada o legal', 'testamentaria', 'contractual', 'anticipada', 'voluntaria', 'Sin testamento, la ley decide quiénes heredan y en qué orden.'],
    ['La familia formada solo por los padres y sus hijos se llama:', 'nuclear', 'extensa', 'monoparental', 'ensamblada', 'comunal', 'La extensa suma a abuelos, tíos y primos; la monoparental tiene un solo progenitor.'],
    ['En línea recta, el parentesco entre un abuelo y su nieto es de:', 'segundo grado', 'primer grado', 'tercer grado', 'cuarto grado', 'quinto grado', 'Se cuenta una generación por grado: abuelo → padre (1) → nieto (2).'],
    ['La función de la familia que consiste en transmitir a los hijos normas, valores y costumbres es la:', 'socializadora', 'reproductora', 'económica', 'recreativa', 'protectora', 'Por la socialización el niño aprende a vivir en su sociedad.'],
  ]);
  tema('civ-ddhh', 'CIV', 1.1, 'Derechos humanos, nación y defensa civil', R('Educación cívica'), [
    'Generaciones de derechos: primera, civiles y políticos (vida, libertad, voto); segunda, económicos, sociales y culturales (trabajo, salud, educación); tercera, de solidaridad (paz, ambiente sano, desarrollo).',
    'La Declaración Universal de los Derechos Humanos fue aprobada por la ONU el 10 de diciembre de 1948.',
    'Según la Constitución, los símbolos de la patria son la bandera, el escudo y el himno nacional.',
  ], [
    ['El derecho a la vida, a la libertad y al voto son derechos de:', 'primera generación (civiles y políticos)', 'segunda generación (económicos, sociales y culturales)', 'tercera generación (de solidaridad)', 'cuarta generación (digitales)', 'ninguna generación: son deberes', 'Los civiles y políticos fueron los primeros en reconocerse: protegen a la persona frente al poder.'],
    ['El derecho al trabajo, a la salud y a la educación son derechos de:', 'segunda generación (económicos, sociales y culturales)', 'primera generación (civiles y políticos)', 'tercera generación (de solidaridad)', 'cuarta generación (digitales)', 'los pueblos originarios solamente', 'Los de segunda generación piden al Estado que asegure condiciones de vida dignas.'],
    ['Los derechos de tercera generación, llamados de solidaridad o de los pueblos, incluyen:', 'el derecho a la paz y a un ambiente sano', 'el derecho al voto y a la libertad de opinión', 'el derecho al trabajo y a la seguridad social', 'el derecho a la vida y a la integridad', 'el derecho a la propiedad y a la herencia', 'Son derechos colectivos: paz, ambiente sano y desarrollo.'],
    ['La Declaración Universal de los Derechos Humanos fue aprobada por la ONU el:', '10 de diciembre de 1948', '26 de junio de 1945', '28 de julio de 1821', '4 de julio de 1776', '14 de julio de 1789', 'Por eso el 10 de diciembre es el Día de los Derechos Humanos.'],
    ['Según la Constitución, los símbolos de la patria son:', 'la bandera, el escudo y el himno nacional', 'la bandera, la escarapela y el himno nacional', 'el escudo, la escarapela y la marcha de banderas', 'la bandera, el himno y la flor de la cantuta', 'el escudo, el himno y el gallito de las rocas', 'La Constitución nombra tres: bandera, escudo e himno. La escarapela no figura en ese artículo.'],
    ['Una empresa, una asociación o una comunidad campesina, con derechos y obligaciones distintos de los de sus miembros, son:', 'personas jurídicas', 'personas naturales', 'órganos del Estado', 'bienes colectivos', 'sociedades conyugales', 'La persona natural es cada ser humano. La persona jurídica es una organización reconocida por la ley.'],
    ['La forma de gobierno en la que el poder viene del pueblo, que elige a sus autoridades, es la:', 'democracia', 'monarquía absoluta', 'aristocracia', 'teocracia', 'oligarquía', 'Democracia: gobierno del pueblo.'],
    ['En el Perú, el organismo que conduce la preparación, la respuesta y la rehabilitación ante desastres es el:', 'INDECI', 'INEI', 'INDECOPI', 'SENAMHI', 'INGEMMET', 'INDECI es el Instituto Nacional de Defensa Civil. El SENAMHI estudia el clima y el INEI hace las estadísticas.'],
  ]);

  // ------------------------------------------------ Historia (5 preguntas en el grupo C)
  tema('his-fuentes', 'HIS', 1.1, 'Fuentes de la historia, prehistoria y poblamiento de América', R('Historia'), [
    'Fuentes: escritas (crónicas, documentos), materiales (restos, cerámica, edificios), orales (mitos, testimonios) y audiovisuales.',
    'La Edad de Piedra se divide en Paleolítico (piedra tallada, cazadores nómadas), Mesolítico (transición) y Neolítico (piedra pulida, agricultura y aldeas).',
    'Poblamiento de América: Alex Hrdlička (llegaron de Asia por Bering), Paul Rivet (varias rutas) y Florentino Ameghino (origen americano, teoría descartada).',
  ], [
    ['La «Crónica del Perú» de Pedro Cieza de León es para el historiador una fuente:', 'escrita', 'oral', 'monumental', 'audiovisual', 'etnográfica', 'Las crónicas son textos: fuentes escritas.'],
    ['Una vasija de cerámica mochica encontrada en una tumba es una fuente:', 'material o arqueológica', 'escrita', 'oral', 'audiovisual', 'lingüística', 'Los objetos y restos que dejaron los pueblos son fuentes materiales.'],
    ['La ciencia que estudia los restos fósiles de animales y plantas, y que ayuda a la historia, es la:', 'paleontología', 'paleografía', 'numismática', 'epigrafía', 'heráldica', 'La paleografía estudia escrituras antiguas; la numismática, monedas; la epigrafía, inscripciones.'],
    ['La Edad de Piedra se divide, de la etapa más antigua a la más reciente, en:', 'Paleolítico, Mesolítico y Neolítico', 'Neolítico, Mesolítico y Paleolítico', 'Paleolítico, Neolítico y Mesolítico', 'Mesolítico, Paleolítico y Neolítico', 'Paleolítico, Megalítico y Neolítico', 'Paleo es «antiguo», meso es «medio» y neo es «nuevo».'],
    ['El gran cambio del Neolítico fue:', 'el inicio de la agricultura y la ganadería, con aldeas estables', 'el dominio del fuego', 'la invención de la escritura', 'el uso del hierro', 'la aparición de los primeros homínidos', 'Con la agricultura los grupos dejaron de ser nómadas. La escritura llegó después y marca el fin de la prehistoria.'],
    ['La teoría de que los primeros pobladores de América llegaron desde Asia cruzando el estrecho de Bering fue defendida por:', 'Alex Hrdlička', 'Paul Rivet', 'Florentino Ameghino', 'António Mendes Correia', 'Thor Heyerdahl', 'Hrdlička propuso una sola ruta, la asiática. Rivet aceptó esa ruta y añadió otras.'],
    ['Paul Rivet sostuvo que América fue poblada por:', 'varias corrientes: asiática, melanésica, polinésica y australiana', 'una sola corriente llegada de Asia', 'hombres originarios de la pampa argentina', 'navegantes llegados de Europa', 'migrantes llegados de África', 'Por eso su teoría se llama oceánica o de origen múltiple.'],
    ['El Paleolítico coincide con la época geológica llamada:', 'Pleistoceno', 'Holoceno', 'Jurásico', 'Cretácico', 'Cámbrico', 'El Pleistoceno es la época de las glaciaciones. El Holoceno, la actual, empieza cuando terminan.'],
  ]);
  tema('his-origen', 'HIS', 1.1, 'Origen de la cultura andina y sus periodos', R('Historia'), [
    'Teorías sobre el origen de la alta cultura: Max Uhle (inmigracionista: vino de Centroamérica), Julio C. Tello (autoctonista: nació en los Andes, con raíz en la selva), Federico Kauffmann Doig (aloctonista: desde Valdivia, Ecuador) y Luis G. Lumbreras (hologenista: factores internos más aportes de fuera).',
    'Periodos: Horizonte Temprano (Chavín), Intermedio Temprano (Moche, Nazca), Horizonte Medio (Wari, Tiahuanaco), Intermedio Tardío (Chimú, Chanca) y Horizonte Tardío (Incas).',
    'En un horizonte una cultura se expande y da unidad; en un intermedio florecen culturas regionales.',
  ], [
    ['Julio C. Tello sostuvo que la alta cultura peruana:', 'nació en el propio territorio andino, con origen en la selva', 'llegó ya formada desde Centroamérica', 'llegó desde la costa de Ecuador', 'fue traída por navegantes de Polinesia', 'surgió por influencia de Egipto', 'Es la teoría autoctonista: la cultura es propia y Chavín sería su cultura matriz.'],
    ['La teoría inmigracionista, según la cual la cultura peruana vino de Centroamérica, fue planteada por:', 'Max Uhle', 'Julio C. Tello', 'Federico Kauffmann Doig', 'Luis G. Lumbreras', 'Rafael Larco Hoyle', 'Uhle pensaba que venía de los mayas y que primero se asentó en la costa.'],
    ['La teoría hologenista, que explica la cultura andina por factores internos junto con aportes externos, es de:', 'Luis Guillermo Lumbreras', 'Max Uhle', 'Julio C. Tello', 'Federico Kauffmann Doig', 'John Rowe', 'Holo- significa «todo»: Lumbreras toma en cuenta todos los factores, los de dentro y los de fuera.'],
    ['En la periodificación por horizontes e intermedios, la cultura Chavín corresponde al:', 'Horizonte Temprano', 'Intermedio Temprano', 'Horizonte Medio', 'Intermedio Tardío', 'Horizonte Tardío', 'Chavín fue la primera cultura que se extendió por gran parte de los Andes.'],
    ['La cultura Wari, con su capital cerca de Ayacucho, se ubica en el:', 'Horizonte Medio', 'Horizonte Temprano', 'Intermedio Temprano', 'Intermedio Tardío', 'Horizonte Tardío', 'Wari y Tiahuanaco son del Horizonte Medio. Wari es considerado el primer imperio andino.'],
    ['Las culturas Moche y Nazca se desarrollaron en el:', 'Intermedio Temprano', 'Horizonte Temprano', 'Horizonte Medio', 'Intermedio Tardío', 'Horizonte Tardío', 'Después de Chavín vino una etapa de culturas regionales: el Intermedio Temprano.'],
    ['En esta periodificación, un «horizonte» es una etapa en la que:', 'una cultura se expande y da unidad a gran parte de los Andes', 'cada región desarrolla su cultura por separado', 'todavía no existe la agricultura', 'los pueblos son cazadores nómadas', 'llegan los españoles', 'Si cada región va por su lado, la etapa es un intermedio.'],
    ['Federico Kauffmann Doig propuso la teoría aloctonista, que ubica el origen de la cultura peruana en:', 'Valdivia, en la costa de Ecuador', 'Chavín de Huántar', 'la selva amazónica', 'el altiplano del Titicaca', 'la península de Yucatán', 'Alo- significa «otro lugar»: para Kauffmann el foco estuvo en Valdivia.'],
  ]);
  tema('his-antiguo', 'HIS', 1.0, 'Mundo antiguo y feudalismo', R('Historia'), [
    'Egipto creció junto al Nilo y Mesopotamia entre el Tigris y el Éufrates. Los sumerios crearon la escritura cuneiforme.',
    'En Atenas nació la democracia. La Edad Antigua termina con la caída del Imperio romano de Occidente (476).',
    'En el feudalismo la riqueza era la tierra: el señor la poseía y los siervos la trabajaban.',
  ], [
    ['En el antiguo Egipto, el dios de los muertos y de la resurrección era:', 'Osiris', 'Anubis', 'Ra', 'Horus', 'Seth', 'Osiris reinaba en el más allá. Anubis se encargaba del embalsamamiento.'],
    ['El dios egipcio del embalsamamiento, representado con cabeza de chacal, era:', 'Anubis', 'Osiris', 'Ra', 'Thot', 'Horus', 'Anubis guiaba a los muertos y protegía la momificación.'],
    ['La civilización que se desarrolló entre los ríos Tigris y Éufrates fue:', 'Mesopotamia', 'Egipto', 'India', 'China', 'Fenicia', 'Mesopotamia significa «tierra entre ríos».'],
    ['La democracia nació en la Grecia clásica, en la ciudad de:', 'Atenas', 'Esparta', 'Troya', 'Corinto', 'Tebas', 'En Atenas los ciudadanos votaban las leyes en la asamblea. Esparta era una ciudad militar gobernada por pocos.'],
    ['En el feudalismo europeo, la principal actividad económica fue:', 'la agricultura', 'el comercio marítimo', 'la industria textil', 'la minería de oro', 'la banca', 'La economía feudal era rural y casi de autoconsumo: giraba en torno a la tierra.'],
    ['En el feudalismo, el campesino que trabajaba la tierra del señor y no podía abandonarla era el:', 'siervo', 'esclavo', 'burgués', 'caballero', 'artesano libre', 'El siervo no era propiedad del señor, como un esclavo, pero estaba atado a la tierra.'],
    ['Según la división tradicional, la Edad Antigua termina con:', 'la caída del Imperio romano de Occidente (476)', 'la invención de la escritura', 'la caída de Constantinopla (1453)', 'la llegada de Colón a América (1492)', 'la Revolución francesa (1789)', 'La escritura marca su inicio y la caída de Roma su final. Con Constantinopla termina la Edad Media.'],
    ['La escritura cuneiforme, hecha con marcas en forma de cuña sobre tablillas de arcilla, fue creada por:', 'los sumerios', 'los egipcios', 'los fenicios', 'los griegos', 'los chinos', 'Los sumerios vivían en el sur de Mesopotamia. Los egipcios usaban jeroglíficos y los fenicios crearon un alfabeto.'],
  ]);
  tema('his-republica', 'HIS', 1.0, 'Virreinato y República: hechos clave', R('Historia'), [
    'El virreinato del Perú se creó en 1542. En el siglo XVIII las reformas borbónicas le quitaron territorio para crear Nueva Granada y Río de la Plata.',
    'Primeros años de la República: caudillos militares, la Confederación Perú-Boliviana (1836–1839) y Ramón Castilla, que abolió la esclavitud en 1854.',
    'Siglo XX: el Oncenio de Leguía (1919–1930) y el golpe de Sánchez Cerro en 1930.',
  ], [
    ['El inca que en 1536 inició la resistencia contra los españoles, cercó el Cusco y se retiró a Vilcabamba fue:', 'Manco Inca', 'Atahualpa', 'Huáscar', 'Túpac Amaru I', 'Sayri Túpac', 'Manco Inca fundó la resistencia de Vilcabamba. Túpac Amaru I fue el último de esos incas, ejecutado en 1572.'],
    ['El virreinato del Perú fue creado en 1542 mediante:', 'las Leyes Nuevas, dictadas por el rey Carlos I', 'la Capitulación de Toledo', 'el Tratado de Tordesillas', 'las reformas borbónicas', 'la Constitución de Cádiz', 'Las Leyes Nuevas crearon el virreinato y la Audiencia de Lima, y limitaron las encomiendas.'],
    ['En el siglo XVIII, las reformas borbónicas separaron territorios del virreinato del Perú para crear los virreinatos de:', 'Nueva Granada y Río de la Plata', 'Nueva España y Nueva Granada', 'Nueva España y Río de la Plata', 'Chile y Quito', 'Charcas y Venezuela', 'Nueva España (México) existía desde 1535 y no salió del Perú.'],
    ['La actividad económica que sostuvo el comercio del virreinato del Perú con España fue:', 'la minería de plata, sobre todo en Potosí', 'la agricultura de exportación', 'la pesca', 'la industria del hierro', 'la ganadería de vacunos', 'La plata de Potosí, sacada con la mita minera, fue el eje de la economía colonial.'],
    ['El presidente más joven de la historia del Perú, fusilado en 1836 por oponerse a la Confederación Perú-Boliviana, fue:', 'Felipe Santiago Salaverry', 'Agustín Gamarra', 'Luis José de Orbegoso', 'Andrés de Santa Cruz', 'Ramón Castilla', 'Salaverry tomó el poder a los 28 años y fue fusilado en Arequipa tras ser vencido por Santa Cruz.'],
    ['La Confederación Perú-Boliviana (1836–1839) fue dirigida por:', 'Andrés de Santa Cruz', 'Simón Bolívar', 'Agustín Gamarra', 'Felipe Santiago Salaverry', 'José de La Mar', 'Santa Cruz fue su Protector. La Confederación terminó con la batalla de Yungay, en 1839.'],
    ['El presidente que en 1854 decretó la abolición de la esclavitud y del tributo indígena fue:', 'Ramón Castilla', 'José Rufino Echenique', 'Manuel Pardo', 'José Balta', 'Nicolás de Piérola', 'Castilla dio esos decretos durante su levantamiento contra Echenique.'],
    ['El «Oncenio» (1919–1930), gobierno que se llamó a sí mismo «Patria Nueva», fue el de:', 'Augusto B. Leguía', 'Luis M. Sánchez Cerro', 'Óscar R. Benavides', 'José Pardo', 'Manuel Prado', 'Leguía gobernó once años seguidos, de ahí el nombre.'],
    ['El comandante que derrocó a Leguía en 1930, desde Arequipa, y fue presidente hasta su asesinato en 1933 fue:', 'Luis M. Sánchez Cerro', 'Óscar R. Benavides', 'Víctor Raúl Haya de la Torre', 'Manuel A. Odría', 'David Samanez Ocampo', 'Tras su muerte, el Congreso entregó el mando a Benavides.'],
  ]);

  // ------------------------------------------------ Geografía (5 preguntas en el grupo C)
  tema('geg-tierra', 'GEG', 1.1, 'El universo, la Tierra y los principios geográficos', R('Geografía'), [
    'Rotación: la Tierra gira sobre su eje en cerca de 24 horas y produce el día y la noche. Traslación: da una vuelta al Sol en 365 días y unas 6 horas; con la inclinación del eje produce las estaciones.',
    'Principios geográficos: localización (Ratzel), causalidad (Humboldt), comparación (Ritter y Vidal de la Blache) y actividad o evolución (Brunhes).',
    'La latitud se mide desde la línea ecuatorial y la longitud desde el meridiano de Greenwich.',
  ], [
    ['El Sol está formado sobre todo por:', 'hidrógeno y helio', 'oxígeno y nitrógeno', 'hierro y níquel', 'carbono y oxígeno', 'helio y metano', 'Cerca de tres cuartas partes de su masa es hidrógeno y casi todo el resto es helio.'],
    ['La rotación de la Tierra sobre su eje dura cerca de 24 horas y su principal consecuencia es:', 'la sucesión del día y la noche', 'las estaciones del año', 'los años bisiestos', 'las fases de la Luna', 'los eclipses', 'Las estaciones y los años bisiestos se deben a la traslación, no a la rotación.'],
    ['Las estaciones del año se deben al movimiento de traslación y a:', 'la inclinación del eje de la Tierra', 'la distancia entre la Tierra y la Luna', 'la rotación del Sol', 'la forma redonda de la Tierra', 'las mareas', 'Por la inclinación del eje, cada hemisferio recibe más luz en una parte del año y menos en otra.'],
    ['El principio geográfico de causalidad, que pide explicar el porqué de cada hecho, fue propuesto por:', 'Alexander von Humboldt', 'Friedrich Ratzel', 'Karl Ritter', 'Jean Brunhes', 'Paul Vidal de la Blache', 'Humboldt buscaba las causas de los hechos geográficos. Ratzel planteó el principio de localización.'],
    ['El principio de localización, que pide ubicar dónde ocurre un hecho geográfico, fue planteado por:', 'Friedrich Ratzel', 'Alexander von Humboldt', 'Karl Ritter', 'Jean Brunhes', 'Paul Vidal de la Blache', 'Para Ratzel, lo primero es ubicar el hecho: lugar, extensión y límites.'],
    ['La teoría más aceptada sobre el origen del universo, que plantea una gran expansión a partir de un punto muy denso y caliente, es la del:', 'Big Bang', 'estado estacionario', 'universo oscilante', 'geocentrismo', 'heliocentrismo', 'El geocentrismo y el heliocentrismo no hablan del origen: dicen qué astro está en el centro.'],
    ['La línea imaginaria que divide la Tierra en los hemisferios norte y sur es:', 'la línea ecuatorial', 'el meridiano de Greenwich', 'el trópico de Cáncer', 'el trópico de Capricornio', 'el círculo polar ártico', 'El meridiano de Greenwich divide la Tierra en este y oeste.'],
    ['Las coordenadas que permiten ubicar cualquier punto de la Tierra son:', 'la latitud y la longitud', 'la altitud y la presión', 'el relieve y el clima', 'los husos horarios', 'el ecuador y los polos', 'La latitud dice cuánto al norte o al sur; la longitud, cuánto al este o al oeste.'],
  ]);
  tema('geg-peru', 'GEG', 1.2, 'El Perú: ubicación, relieve y recursos', R('Geografía'), [
    'El Perú está en la parte central y occidental de América del Sur y mide cerca de 1 285 216 km².',
    'Relieve: cañones (valles muy profundos de los Andes), pongos (pasos estrechos de los ríos hacia la selva) y llano amazónico u Omagua (selva baja).',
    'Mar peruano: frío y de color verdoso por el fitoplancton. El gas de Camisea está en Cusco.',
  ], [
    ['El territorio del Perú tiene una superficie aproximada de:', '1 285 216 km²', '1 098 581 km²', '756 102 km²', '2 780 400 km²', '283 561 km²', 'Es el tercer país más grande de América del Sur, después de Brasil y Argentina.'],
    ['En América del Sur, el Perú se ubica en la parte:', 'central y occidental', 'norte y oriental', 'sur y oriental', 'central y oriental', 'sur y occidental', 'Está en el centro del subcontinente, frente al océano Pacífico, es decir, al oeste.'],
    ['El relieve de la selva baja, plano y cubierto de bosque, también se conoce como:', 'llano amazónico u Omagua', 'Rupa Rupa', 'ceja de selva', 'meseta del Collao', 'Janca', 'Omagua es el nombre que Pulgar Vidal dio a la selva baja. Rupa Rupa es la selva alta.'],
    ['Los valles muy profundos y de paredes empinadas que los ríos han excavado en los Andes, como el del Colca o el del Apurímac, se llaman:', 'cañones', 'mesetas', 'tablazos', 'abras', 'pampas', 'Un cañón es obra de la erosión del río durante miles de años.'],
    ['El color verdoso del mar peruano se debe a:', 'la gran cantidad de fitoplancton', 'la poca profundidad de sus aguas', 'las arenas del fondo', 'su salinidad muy alta', 'el reflejo de los cerros de la costa', 'El fitoplancton abunda porque las aguas frías que afloran traen nutrientes.'],
    ['Camisea, el yacimiento de gas natural más importante del Perú, está en la región:', 'Cusco', 'Loreto', 'Piura', 'Ucayali', 'Madre de Dios', 'Está en la provincia de La Convención, en la selva del Cusco.'],
    ['La explotación minera que retira el mineral desde la superficie, formando grandes terrazas, se llama:', 'a tajo abierto', 'de socavón', 'por galerías', 'submarina', 'de pozo', 'También se dice «a cielo abierto». La de socavón avanza por túneles bajo tierra.'],
    ['Un río que entrega sus aguas a otro río más grande es un:', 'afluente', 'efluente', 'delta', 'estuario', 'meandro', 'El Pachachaca, por ejemplo, es afluente del Apurímac.'],
    ['El Fenómeno de El Niño se reconoce porque, frente a la costa norte del Perú:', 'la temperatura del mar sube más de lo normal', 'el mar se enfría más de lo normal', 'dejan de caer lluvias en Piura y Tumbes', 'aumenta la pesca de anchoveta', 'el mar se retira de la costa', 'El agua caliente trae lluvias fuertes al norte y aleja a la anchoveta, que prefiere el agua fría.'],
  ]);

  // ------------------------------------------------ Economía (5 preguntas en el grupo C)
  tema('ecn-necesidades', 'ECN', 1.2, 'Necesidades, bienes y problema económico', R('Economía'), [
    'Pirámide de Maslow, de la base a la cima: necesidades fisiológicas, de seguridad, sociales, de estima y de autorrealización.',
    'Las necesidades son ilimitadas en número pero limitadas en capacidad: cada una se calma al satisfacerla.',
    'Bienes libres (abundan y no tienen precio, como el aire) y bienes económicos (escasos y con precio). Los económicos pueden ser de consumo o de capital.',
  ], [
    ['Según Maslow, las necesidades que están en la base de la pirámide y se atienden primero son las:', 'fisiológicas', 'de seguridad', 'sociales', 'de estima', 'de autorrealización', 'Comer, beber y dormir van primero: sin eso no se puede pensar en lo demás.'],
    ['El orden de las necesidades en la pirámide de Maslow, de la base a la cima, es:', 'fisiológicas, seguridad, sociales, estima y autorrealización', 'seguridad, fisiológicas, sociales, estima y autorrealización', 'fisiológicas, sociales, seguridad, autorrealización y estima', 'sociales, estima, seguridad, fisiológicas y autorrealización', 'autorrealización, estima, sociales, seguridad y fisiológicas', 'Se sube de lo más urgente (el cuerpo) a lo más elevado (realizarse como persona).'],
    ['Después del tercer vaso de agua ya no hay sed. Esto muestra que las necesidades son:', 'limitadas en capacidad (se sacian)', 'ilimitadas en número', 'complementarias', 'sustituibles', 'concurrentes', 'Cada necesidad tiene un límite: al satisfacerla, el deseo disminuye.'],
    ['Alimentarse, vestirse y tener un techo son necesidades:', 'primarias o vitales', 'secundarias o generales', 'suntuarias o de lujo', 'colectivas', 'espirituales', 'De ellas depende conservar la vida.'],
    ['El aire que respiramos es un bien libre porque:', 'abunda en la naturaleza y no tiene precio', 'lo produce el Estado', 'se vende en el mercado', 'es un bien de capital', 'es escaso', 'Los bienes libres no son escasos ni tienen dueño; los económicos sí.'],
    ['El tractor que usa un agricultor para producir es un bien:', 'de capital', 'de consumo', 'libre', 'inmaterial', 'sustituto', 'Un bien de capital sirve para producir otros bienes.'],
    ['El café y el azúcar, que suelen consumirse juntos, son bienes:', 'complementarios', 'sustitutos', 'libres', 'de capital', 'inferiores', 'Los complementarios se usan juntos. Los sustitutos se reemplazan entre sí, como el té y el café.'],
    ['El problema económico fundamental es que:', 'los recursos son escasos y las necesidades son ilimitadas', 'los precios siempre suben', 'el dinero pierde valor', 'las empresas buscan ganancias', 'el Estado cobra impuestos', 'Como no alcanza para todo, hay que elegir. De eso trata la economía.'],
    ['Rosa puede usar su tarde para estudiar o para trabajar. Si elige trabajar, la tarde de estudio a la que renuncia es su:', 'costo de oportunidad', 'costo fijo', 'costo marginal', 'utilidad total', 'ingreso real', 'El costo de oportunidad es lo mejor que se deja de lado al elegir.'],
  ]);
  tema('ecn-dinero', 'ECN', 1.0, 'Dinero, tipo de cambio, PBI y ciclo económico', R('Economía'), [
    'Funciones del dinero: medio de cambio, unidad de cuenta y depósito de valor.',
    'Tipo de cambio: precio de una moneda extranjera en moneda nacional. Si el dólar sube, el sol se deprecia.',
    'PBI nominal: a precios del año en curso. PBI real: a precios de un año base. Fases del ciclo económico: recuperación, auge, recesión y depresión.',
  ], [
    ['Que el dinero sirva para comprar y vender sin recurrir al trueque es su función de:', 'medio de cambio', 'unidad de cuenta', 'depósito de valor', 'medio de pago diferido', 'reserva internacional', 'Como todos lo aceptan, ya no hace falta cambiar un producto por otro.'],
    ['La característica del dinero por la que debe conservar su poder de compra a lo largo del tiempo es la:', 'estabilidad', 'divisibilidad', 'durabilidad', 'portabilidad', 'homogeneidad', 'La durabilidad es otra cosa: que el billete o la moneda resista el uso.'],
    ['El tipo de cambio es:', 'el precio de una moneda extranjera expresado en moneda nacional', 'la tasa de interés de los bancos', 'el precio de los productos importados', 'el impuesto que se paga al importar', 'la cantidad de billetes que circula', 'Por ejemplo, cuántos soles cuesta un dólar.'],
    ['Si el dólar pasa de S/ 3,70 a S/ 3,90, el sol:', 'se deprecia: se necesitan más soles por cada dólar', 'se aprecia: vale más que antes', 'no cambia de valor', 'se convierte en moneda fuerte', 'deja de circular', 'Cuando la moneda extranjera sube de precio, la nacional pierde valor frente a ella.'],
    ['El PBI real se diferencia del PBI nominal en que se calcula:', 'con los precios de un año base, para descontar la inflación', 'con los precios del año en curso', 'solo con la producción del Estado', 'sin contar los servicios', 'en dólares', 'Así se ve si de verdad se produjo más, y no solo si subieron los precios.'],
    ['El Producto Bruto Interno (PBI) mide:', 'el valor de los bienes y servicios finales producidos dentro del país en un periodo', 'la cantidad de dinero que circula', 'las exportaciones menos las importaciones', 'los impuestos que recauda el Estado', 'el ingreso de los peruanos que viven en el extranjero', '«Interno» quiere decir dentro del territorio, sin importar si la empresa es nacional o extranjera.'],
    ['La fase del ciclo económico en la que la producción y el empleo llegan a su punto más alto es:', 'el auge', 'la recesión', 'la depresión', 'la recuperación', 'la crisis', 'Después del auge la economía empieza a caer: viene la recesión.'],
    ['El aumento general y sostenido de los precios se llama:', 'inflación', 'deflación', 'devaluación', 'recesión', 'especulación', 'La deflación es lo contrario: una baja general de los precios.'],
  ]);

  // ------------------------------------------------ Zoología (5 preguntas en el grupo B)
  tema('zoo-invertebrados', 'ZOO', 1.1, 'Protozoos e invertebrados', R('Zoología'), [
    'Los protozoos se agrupan por su forma de moverse: con seudópodos (ameba), con cilios (paramecio) o con flagelos (tripanosoma).',
    'Los artrópodos tienen exoesqueleto de quitina y patas articuladas. Insectos: 3 pares de patas. Arácnidos: 4 pares. Crustáceos: 5 pares o más.',
    'Otros grupos: poríferos (esponjas), cnidarios (medusas y corales), anélidos (lombriz), moluscos (caracol, pulpo) y equinodermos (estrella de mar).',
  ], [
    ['Las amebas se desplazan y capturan su alimento mediante:', 'seudópodos', 'cilios', 'flagelos', 'patas articuladas', 'tentáculos', 'Los seudópodos o «falsos pies» son prolongaciones del citoplasma.'],
    ['El paramecio se mueve gracias a:', 'cilios', 'seudópodos', 'un flagelo', 'tentáculos', 'pies ambulacrales', 'Los cilios son como pelitos cortos que cubren todo su cuerpo y baten a la vez.'],
    ['La malaria o paludismo la causa un protozoo del género Plasmodium, que se transmite por la picadura de:', 'la hembra del mosquito Anopheles', 'la chirimacha', 'la mosca tse-tse', 'el mosquito Aedes aegypti', 'la pulga de la rata', 'La chirimacha transmite el mal de Chagas y el Aedes, el dengue.'],
    ['Los artrópodos (insectos, arañas, cangrejos) tienen en común:', 'un exoesqueleto de quitina y patas articuladas', 'un esqueleto interno de hueso', 'el cuerpo blando cubierto por un manto', 'células urticantes en los tentáculos', 'pies ambulacrales y simetría radial', 'Artrópodo significa «patas articuladas».'],
    ['Los insectos se reconocen porque tienen:', 'tres pares de patas y el cuerpo dividido en cabeza, tórax y abdomen', 'cuatro pares de patas y cefalotórax', 'cinco pares de patas y dos pares de antenas', 'un par de patas en cada segmento del cuerpo', 'ocho brazos con ventosas', 'Seis patas: por eso también se les llama hexápodos.'],
    ['Las arañas y los escorpiones son arácnidos. Tienen:', 'cuatro pares de patas', 'tres pares de patas', 'cinco pares de patas', 'un par de antenas', 'dos pares de alas', 'Ocho patas y ninguna antena: así se distinguen de los insectos.'],
    ['La lombriz de tierra, con el cuerpo formado por anillos, pertenece a los:', 'anélidos', 'nematodos', 'platelmintos', 'moluscos', 'artrópodos', 'Anélido viene de «anillo». Los nematodos son gusanos cilíndricos sin anillos y los platelmintos son planos.'],
    ['El pulpo y el calamar son moluscos del grupo de los:', 'cefalópodos', 'gasterópodos', 'bivalvos', 'crustáceos', 'equinodermos', 'Cefalópodo: «pies en la cabeza». Los gasterópodos son los caracoles y los bivalvos, los choros y las conchas.'],
    ['Las medusas, los corales y las anémonas tienen células urticantes llamadas cnidocitos. Pertenecen a los:', 'cnidarios', 'poríferos', 'equinodermos', 'moluscos', 'anélidos', 'El nombre del grupo viene de esas células.'],
  ]);
  tema('zoo-vertebrados', 'ZOO', 1.1, 'Vertebrados: peces, anfibios, reptiles y aves', R('Zoología'), [
    'Peces, anfibios y reptiles son de temperatura variable (poiquilotermos). Aves y mamíferos la mantienen constante (homeotermos).',
    'Peces: branquias y aletas. Anfibios: metamorfosis y respiración por la piel. Reptiles: piel con escamas y huevo con cáscara. Aves: plumas y huesos huecos.',
    'Corazón: dos cavidades en los peces, tres en los anfibios y cuatro en las aves y los mamíferos.',
  ], [
    ['Los peces toman el oxígeno disuelto en el agua mediante:', 'branquias', 'pulmones', 'tráqueas', 'sacos aéreos', 'espiráculos', 'El agua entra por la boca, pasa por las branquias y allí la sangre recoge el oxígeno.'],
    ['Los tiburones y las rayas se diferencian de la mayoría de los peces porque su esqueleto es de:', 'cartílago', 'hueso', 'quitina', 'queratina', 'sílice', 'Son los peces cartilaginosos o condrictios. Los demás son peces óseos.'],
    ['Los anfibios, como las ranas, pasan de renacuajo a adulto mediante:', 'metamorfosis', 'muda del exoesqueleto', 'gemación', 'regeneración', 'partenogénesis', 'El renacuajo pierde la cola y las branquias, y desarrolla patas y pulmones.'],
    ['Una rana adulta respira por los pulmones y también por:', 'la piel, que debe mantenerse húmeda', 'branquias externas', 'tráqueas', 'sacos aéreos', 'espiráculos', 'Su respiración por la piel es tan importante que, si la piel se seca, el animal puede morir.'],
    ['Los reptiles pudieron reproducirse lejos del agua gracias a:', 'el huevo con cáscara y la piel con escamas', 'la metamorfosis', 'las glándulas mamarias', 'las plumas', 'la respiración por la piel', 'La cáscara evita que el huevo se seque y las escamas evitan que el cuerpo pierda agua.'],
    ['Son animales de temperatura corporal variable (poiquilotermos):', 'peces, anfibios y reptiles', 'aves y mamíferos', 'solo los mamíferos', 'aves y reptiles', 'anfibios y aves', 'Su temperatura depende del ambiente. Las aves y los mamíferos la regulan por sí mismos.'],
    ['Las aves mantienen constante su temperatura y tienen el corazón con:', 'cuatro cavidades: dos aurículas y dos ventrículos', 'dos cavidades', 'tres cavidades', 'una sola cavidad', 'cinco cavidades', 'Igual que los mamíferos. Así la sangre con oxígeno no se mezcla con la que no lo tiene.'],
    ['Para volar, las aves cuentan con huesos:', 'huecos y livianos', 'macizos y pesados', 'de cartílago', 'sin articulaciones', 'cubiertos de escamas', 'Se llaman huesos neumáticos: tienen aire por dentro y pesan poco.'],
    ['El ave nacional del Perú es:', 'el gallito de las rocas', 'el cóndor andino', 'la pava aliblanca', 'el colibrí maravilloso', 'el suri', 'El gallito de las rocas o tunki (Rupicola peruvianus) vive en los bosques de la selva alta.'],
  ]);
})(typeof window !== 'undefined' ? window : globalThis);
