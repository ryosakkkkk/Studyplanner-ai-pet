// Guía socrática: las preguntas que el tutor hace ANTES de mostrar cada paso de la solución.
// La pregunta k va antes del paso k: el estudiante la piensa y recién entonces ve el paso.
// Cada tema tiene reglas [condición sobre el enunciado, preguntas]; la primera que calza se usa. La última regla de
// cada tema no tiene condición. En los temas de preguntas fijas (una sola explicación) las preguntas van saliendo con
// cada intento. Uso: Guia.de(temaId, pregunta) -> lista de preguntas.
(function (root) {
  'use strict';
  const T = String.raw;
  const R = {};

  // ------------------------------------------------------------------------------------------ aritmética
  R['ari-logica'] = [[null, [
    T`¿En qué único caso es falsa una condicional $A \rightarrow B$? ¿Y una disyunción $A \vee B$?`,
    'La proposición completa es falsa. ¿Qué te dice eso de cada una de sus dos partes?',
    T`Con esas partes ya fijadas, ¿qué valor le toca a cada variable: $p$, $q$, $r$ y $s$?`,
    'Ahora reemplaza esos valores en I, II y III. ¿Cuál sale verdadera y cuál falsa?']]];
  R['ari-conjuntos'] = [
    [/las dos cosas\?/, [
      'Algunas personas no están en ningún grupo. ¿Cuántas quedan en al menos uno?',
      'Si sumas los dos grupos, te pasas de ese número. ¿Qué representa lo que sobra?',
      '¿Cuánto sobra exactamente?']],
    [null, [
      'Si sumas los dos grupos tal cual, ¿a quiénes estás contando dos veces?',
      'Dibuja dos círculos que se cruzan. ¿Qué número va en el medio y cuánto queda a cada lado?',
      'Ya sabes cuántos están en al menos un grupo. ¿Cómo sacas los que quedan fuera?']]];
  R['ari-numeracion'] = [
    [/la base/, [
      T`Escribe el número con potencias de $n$, cifra por cifra. ¿Qué ecuación te queda?`,
      'Es una ecuación de segundo grado. ¿Cómo la ordenas para resolverla?',
      T`Salen dos valores de $n$. ¿Cuál sirve como base y por qué?`]],
    [null, [
      'En base 10, el 345 es 3 centenas, 4 decenas y 5 unidades. ¿Qué valen las posiciones cuando la base es otra?',
      '¿Por qué potencia de la base se multiplica cada cifra de este número?',
      'Calcula cada producto y suma. ¿Cuánto da?']]];
  R['ari-divisores'] = [[null, [
    '¿Cómo se escribe este número como producto de factores primos?',
    'Con los exponentes de esa descomposición, ¿cómo se cuenta cuántos divisores hay en total?',
    'De todos esos divisores, ¿cuáles no cumplen lo que piden y hay que quitar? Ojo con el 1.']]];
  R['ari-proporciones'] = [
    [/mezcla|litros/, [
      'La relación dice cuántas partes hay de cada líquido. ¿Cuántos litros de cada uno hay al inicio?',
      'Al agregar agua, ¿qué cantidad cambia y cuál se queda igual? Escribe la nueva relación.',
      T`¿Cómo despejas $x$ de esa igualdad de fracciones?`]],
    [null, [
      T`Si las partes son proporcionales a esos números, ¿cómo escribes cada parte con una misma constante $k$?`,
      T`¿Qué ecuación sale al sumar las partes? ¿Cuánto vale $k$?`,
      T`Con $k$, ¿cuánto recibe cada uno? ¿Cuál es la parte que piden?`]]];

  // ------------------------------------------------------------------------------------------ álgebra
  R['alg-polinomios'] = [
    [/suma de coeficientes/, [
      T`¿Qué valor de $x$ hace que $P(x)$ sea justo la suma de sus coeficientes?`,
      T`¿Y qué valor de $x$ deja solo el término independiente?`]],
    [null, [
      T`En $P(P(a))$, ¿qué hay que calcular primero?`,
      T`Ese resultado es ahora la nueva entrada. ¿Qué da al reemplazarlo en $P$?`]]];
  R['alg-notables'] = [
    [/\\dfrac\{1\}\{x\}/, [
      '¿Qué pasa si elevas al cuadrado los dos lados de la igualdad que te dan?',
      T`En el desarrollo, ¿cuánto vale el término del medio, $2 \cdot x \cdot \dfrac{1}{x}$? ¿Qué queda al despejar?`]],
    [null, [
      T`¿Qué producto notable contiene a la vez $a + b$, $ab$ y $a^{2} + b^{2}$?`,
      'En esa identidad, ¿qué despejas y qué valores reemplazas?']]];
  R['alg-resto'] = [
    [/sea exacta/, [
      'Si la división es exacta, ¿cuánto vale el resto? ¿Qué dice el teorema del resto?',
      'Reemplaza el valor que anula al divisor e iguala a cero. ¿Qué ecuación queda?']],
    [null, [
      T`¿Qué dice el teorema del resto? ¿Con qué valor de $x$ se anula el divisor?`,
      T`Reemplaza ese valor en $P(x)$. ¿Qué operación te queda?`,
      'Calcula con cuidado los signos. ¿Cuánto da?']]];
  R['alg-factorizacion'] = [[null, [
    T`En $x^{2} + bx + c$, ¿qué deben cumplir los dos números del aspa simple?`,
    '¿Qué par de números cumple las dos condiciones? Fíjate en los signos.',
    'Con esos números, ¿cómo quedan los dos factores? ¿Y su suma?']]];
  R['alg-inecuaciones'] = [
    [/x\^\{2\}/, [
      '¿Cómo se factoriza el trinomio? ¿Cuáles son sus raíces?',
      'El trinomio es una parábola que abre hacia arriba. ¿En qué tramo queda por debajo del eje?',
      '¿Qué enteros hay dentro de ese tramo, sin contar los extremos?']],
    [/\\geq|\\ge /, [
      'Si un valor absoluto es mayor o igual que un número, ¿en qué dos zonas de la recta puede estar lo de adentro?',
      'Resuelve cada desigualdad por separado. ¿Qué dos tramos salen?']],
    [null, [
      '¿Qué significa que un valor absoluto sea menor que un número? Escríbelo sin las barras.',
      T`¿Qué haces en los tres lados para dejar sola la $x$?`,
      'Los extremos, ¿entran o no? Cuenta los enteros que quedan dentro.']]];
  R['alg-funciones'] = [[null, [
    T`¿En qué punto de una parábola está su valor más bajo o más alto? ¿Con qué fórmula se halla su $x$?`,
    T`Ya tienes la $x$ del vértice. ¿Cómo sacas su $y$? Con el vértice, ¿cómo respondes lo que piden?`]]];
  R['alg-factorial'] = [
    [/\+ n!/, [
      'En el numerador hay dos factoriales. ¿Cuál puedes escribir usando el otro, para sacar factor común?',
      T`Al simplificar $n!$, ¿qué ecuación sencilla queda?`]],
    [null, [
      '¿Cómo escribes el factorial mayor para que aparezca dentro el factorial menor?',
      '¿Qué se simplifica y qué producto queda?',
      '¿Qué números consecutivos multiplicados dan ese resultado?']]];

  // ------------------------------------------------------------------------------------------ geometría
  R['geo-segmentos'] = [
    [/punto medio/, [
      T`Ubica $M$ y $N$ en el dibujo. ¿Entre qué puntos queda cada uno?`,
      '¿Cuánto mide la mitad de cada segmento?',
      T`¿Qué tramos hay que sumar para ir de $M$ a $N$?`]],
    [null, [
      T`Dibuja la recta con los cuatro puntos en orden. ¿Dónde caen $AC$ y $BD$?`,
      T`Si sumas $AC + BD$, ¿qué tramo estás contando dos veces?`,
      'Escribe esa relación con los datos. ¿Cuánto mide el tramo que piden?']]];
  R['geo-paralelas'] = [
    [/marcado con/, [
      '¿Qué trazo auxiliar conviene hacer por el vértice que está entre las dos rectas?',
      'El ángulo del vértice queda partido en dos. ¿Qué ecuación une sus dos partes con el total?']],
    [null, [
      T`El vértice de $x$ está entre las dos paralelas. ¿Qué trazo auxiliar por ese punto te ayudaría?`,
      T`Esa nueva paralela parte a $x$ en dos ángulos. ¿Con cuál de los ángulos dados es igual cada parte, y por qué?`,
      T`Entonces, ¿cómo se obtiene $x$?`]]];
  R['geo-metricas'] = [
    [/altura relativa/, [
      '¿Qué necesitas conocer primero del triángulo? ¿Cómo lo calculas?',
      'El área se puede calcular con los catetos, o con la hipotenusa y su altura. ¿Qué igualdad sale de ahí?']],
    [/divide a la hipotenusa/, [
      'Dibuja el triángulo y marca las dos partes de la hipotenusa. ¿Qué te piden?',
      '¿Qué relación métrica une la altura con las dos proyecciones?',
      'Reemplaza y saca la raíz. ¿Cuánto mide?']],
    [null, [
      'Dibuja el triángulo con su altura. ¿Qué segmentos conoces y cuál te piden?',
      '¿Qué relación métrica une un cateto con la hipotenusa y con su proyección?',
      T`Con eso sale la hipotenusa completa. ¿Cómo llegas a $HC$?`]]];
  R['geo-poligonos'] = [
    [/diagonales/, [
      'Desde un vértice, ¿a cuántos vértices no puedes trazar diagonal? ¿Cuántas diagonales salen entonces de cada uno?',
      T`Si multiplicas por los $n$ vértices, cada diagonal queda contada dos veces. ¿Cómo lo corriges?`]],
    [/exterior/, [
      '¿Cuánto suman todos los ángulos exteriores de un polígono convexo, tenga los lados que tenga?',
      'Si es regular, todos los exteriores son iguales. ¿Cómo sacas el número de lados?']],
    [/cada ángulo interior/, [
      '¿Cuánto suman los ángulos interiores de ese polígono?',
      'Si es regular, ¿cómo repartes esa suma entre sus ángulos?']],
    [null, [
      T`Desde un vértice, ¿en cuántos triángulos queda dividido un polígono de $n$ lados?`,
      T`Cada triángulo suma $180^{\circ}$. ¿Cuánto suman entonces todos los ángulos interiores?`]]];
  R['geo-areas'] = [
    [/rombo/, [
      '¿Cómo se cortan las diagonales de un rombo? ¿En qué figuras lo dividen?',
      '¿Qué fórmula da el área con las dos diagonales?']],
    [/sector/, [
      '¿Qué fracción del círculo completo es ese sector?',
      '¿Cuál es el área del círculo entero? ¿Qué parte le toca al sector?']],
    [/trapecio/, [
      '¿Qué datos del trapecio entran en la fórmula de su área?',
      'Reemplaza en la semisuma de las bases por la altura. ¿Cuánto da?']],
    [null, [
      'Para el área necesitas base y altura. ¿Qué lado te falta y cómo lo hallas?',
      'En un triángulo rectángulo, ¿qué lados hacen de base y de altura?']]];
  R['geo-solidos'] = [
    [/cubo/, [
      '¿Cuántas caras tiene un cubo y cómo es cada una? ¿Cómo sacas la arista a partir del área total?',
      'Con la arista, ¿cómo se calcula el volumen?']],
    [/cilindro/, [
      'El volumen de un cilindro es el área de su base por la altura. ¿Qué forma tiene la base y cuál es su área?',
      'Reemplaza el radio y la altura. ¿Cuánto da?']],
    [/esfera/, [
      '¿Qué fórmula da el volumen de una esfera a partir de su radio?',
      'Eleva el radio al cubo y simplifica. ¿Cuánto queda?']],
    [null, [
      '¿Qué parte ocupa un cono del cilindro que tiene la misma base y la misma altura?',
      'Reemplaza el radio y la altura en la fórmula. ¿Cuánto da?']]];

  // ------------------------------------------------------------------------------------------ trigonometría
  R['tri-sistemas'] = [
    [/tercer ángulo/, [
      T`Los ángulos están en radianes. ¿A cuántos grados equivale $\pi$ rad? Pasa cada uno a grados.`,
      '¿Cuánto suman los tres ángulos de un triángulo? ¿Cuánto queda para el tercero?']],
    [/a radianes/, [
      'Para pasar de grados a radianes, ¿por qué fracción se multiplica?',
      'Simplifica la fracción. ¿Cuál es el mayor divisor común?']],
    [null, [
      '¿Cuál es la equivalencia básica entre radianes y grados?',
      T`Reemplaza $\pi$ por su valor en grados. ¿Cuánto da?`]]];
  R['tri-identidades'] = [
    [/\\sec\\theta = /, [
      T`Eleva el dato al cuadrado. ¿Cuánto valen $\sec^{2}\theta$ y $\cos^{2}\theta$?`,
      T`¿Qué identidad pitagórica une $\tan^{2}\theta$ con $\sec^{2}\theta$?`,
      T`¿Y cómo sacas $\text{sen}^{2}\theta$ a partir de $\cos^{2}\theta$?`]],
    [/agudo/, [
      'Con una razón de un ángulo agudo puedes armar un triángulo rectángulo. ¿Qué lados te dan y cuál falta?',
      T`Con los tres lados, ¿cómo se escribe cada razón que aparece en lo que piden?`]],
    [null, [
      '¿Qué identidad conoces que tenga los términos que aparecen: pitagórica, recíproca o por cociente?',
      'Después de reemplazar, ¿qué se simplifica o se cancela?']]];
  R['tri-cuadrantes'] = [[null, [
    '¿En qué cuadrante cae ese ángulo?',
    '¿Cuál es su ángulo de referencia, el que forma con el eje horizontal?',
    'En ese cuadrante, ¿la razón pedida es positiva o negativa? ¿Por qué?']]];
  R['tri-senos'] = [[null, [
    '¿Qué lado está frente a cada ángulo que te dan?',
    '¿Qué ley relaciona cada lado con el seno del ángulo que tiene al frente?',
    'Despeja el lado pedido y reemplaza los senos de los ángulos notables. ¿Cuánto da?']]];

  // ------------------------------------------------------------------------------------------ física y química
  R['fis-dimensional'] = [
    [/dimensionalmente correcta/, [
      'Si la ecuación es correcta, ¿qué debe pasar con la dimensión de sus dos lados?',
      T`¿Cuál es la dimensión del lado izquierdo? ¿Y la de todo lo que acompaña a $k$?`,
      T`¿Cómo despejas la dimensión de $k$? ¿Qué se simplifica?`]],
    [null, [
    '¿Con qué fórmula se define esa magnitud?',
    T`Reemplaza cada magnitud de la fórmula por su dimensión ($M$, $L$, $T$). ¿Qué queda?`]]];
  R['fis-vectores'] = [
    [/diferencia/, [
      T`Restar un vector es sumar su opuesto. ¿Cómo queda el dibujo con $-\vec{B}$?`,
      T`$\vec{A}$ y $-\vec{B}$ siguen siendo perpendiculares. ¿Qué teorema usas?`]],
    [/90°|perpendiculares/, [
      'Dibuja una fuerza a continuación de la otra. ¿Qué figura forman con la resultante?',
      T`Como forman $90^{\circ}$, ¿qué teorema te da la resultante?`]],
    [null, [
      'Cuando el ángulo no es recto, ¿qué ley da el módulo de la resultante de dos vectores?',
      '¿Cuánto vale el coseno de ese ángulo? Reemplaza y simplifica.']]];
  R['fis-vt'] = [
    [/aceleración/, [
      'En una gráfica velocidad–tiempo, ¿qué representa la inclinación de la línea?',
      '¿Cuánto cambia la velocidad en ese tramo y en cuánto tiempo?']],
    [null, [
      'En una gráfica velocidad–tiempo, ¿qué representa el área bajo la línea?',
      '¿En qué figuras simples puedes dividir esa área? Calcula cada una.']]];
  R['fis-vmedia'] = [[null, [
    '¿Cómo se define la rapidez media? ¿Es lo mismo que promediar las dos rapideces?',
    T`Si cada mitad mide $d$, ¿cuánto tiempo demora en cada una?`,
    T`Divide la distancia total entre el tiempo total. ¿Qué queda al simplificar $d$?`]]];
  R['qui-gases'] = [
    [/temperatura no cambia/, [
      '¿Qué se mantiene constante? ¿Qué ley une entonces la presión con el volumen?',
      T`Reemplaza los datos en $P_1 V_1 = P_2 V_2$. ¿Cuánto sale?`,
      'Si la presión sube, ¿el volumen sube o baja? ¿Tu resultado va en ese sentido?']],
    [/atm y/, [
      'Aquí cambian presión, volumen y temperatura. ¿Qué ley une a las tres?',
      '¿En qué unidad debe ir la temperatura? Conviértela.',
      'Despeja el volumen final y reemplaza. ¿Cuánto da?']],
    [null, [
      '¿Qué se mantiene constante? ¿Qué ley une entonces el volumen con la temperatura?',
      '¿En qué unidad debe ir la temperatura? Conviértela.',
      'Despeja el volumen final y reemplaza. ¿Cuánto da?']]];

  // ------------------------------------------------------------------------------------------ razonamiento matemático
  R['rm-planteo'] = [
    [/patas/, [
      '¿A qué cantidad le llamas incógnita? ¿Cómo escribes la otra usando el total de cabezas?',
      '¿Cuántas patas aporta cada animal? Escribe la ecuación del total de patas.',
      'Otra mirada: si todos fueran gallinas, ¿cuántas patas habría y cuántas sobrarían?']],
    [/billetes/, [
      T`¿A qué cantidad le llamas $x$? ¿Cómo escribes la cantidad del otro billete?`,
      '¿Cómo se escribe el dinero total con esas dos cantidades?',
      T`Resuelve la ecuación. ¿Cuánto vale $x$?`]],
    [null, [
      T`¿Qué conviene llamar $x$: lo gastado o lo que no se gastó? ¿Cómo se escribe lo otro?`,
      'Lo gastado más lo no gastado da el total. ¿Qué ecuación queda?']]];
  R['rm-edades'] = [
    [/Hace/, [
      T`Parte del pasado: si entonces uno tenía $y$, ¿cuánto tenía el otro? ¿Y cuánto tienen hoy?`,
      'Hoy sus edades suman lo que dice el problema. ¿Qué ecuación queda?']],
    [null, [
      'Escribe las edades de hoy con una sola incógnita. ¿Cuánto vale?',
      T`Dentro de $t$ años los dos habrán sumado $t$ a su edad. ¿Qué condición debe cumplirse?`]]];
  R['rm-operadores'] = [
    [null, [
      'El operador es una regla. ¿Qué lugar ocupa cada número en ella? ¿Qué se calcula primero?',
      'Aplica la regla a esa primera operación. ¿Cuánto da?',
      'Ese resultado entra ahora en la regla. Aplícala otra vez.'], 3],
    [null, [
      'Aplica la regla del operador dejando la incógnita en su lugar. ¿Qué expresión queda?',
      'Iguala al valor que te dan. ¿Cuánto vale la incógnita?']]];
  R['rm-fracciones'] = [[null, [
    '¿Qué fracción del total ya se repartió, sumando todo lo anterior?',
    '¿Qué fracción queda? ¿A cuántas unidades equivale?',
    'Si esa fracción del total vale eso, ¿cuánto es el total?']]];
  R['rm-trabajo'] = [
    [/solo\?/, [
      '¿Qué parte de la obra hacen juntos en un día? ¿Y el albañil solo?',
      '¿Qué parte le toca al ayudante en un día? Entonces, ¿cuántos días necesita?']],
    [null, [
      '¿Qué parte de la obra hace cada uno en un día?',
      '¿Qué parte hacen juntos en un día? Entonces, ¿cuántos días necesitan?']]];
  R['rm-moviles'] = [
    [/alcanza/, [
      'Van en el mismo sentido. ¿Con qué rapidez se recorta la ventaja?',
      '¿Cómo sacas el tiempo con esa rapidez y la ventaja inicial?',
      'En una gráfica de posición contra tiempo, ¿cuál recta es más inclinada y dónde lo alcanza?']],
    [null, [
      'Van uno hacia el otro. ¿Con qué rapidez se acorta la distancia entre ellos?',
      '¿Cómo sacas el tiempo con esa rapidez y la distancia que los separa?',
      'En una gráfica de posición contra tiempo, ¿cómo se vería el momento del encuentro?']]];
  R['rm-calendario'] = [[null, [
    '¿Cuántos días mueve cada palabra: ayer, anteayer, mañana y pasado mañana?',
    'Suma los movimientos. ¿Cuántos días avanzas o retrocedes en total?',
    'Cuenta esos días desde hoy. ¿A qué día llegas?']]];

  // ------------------------------------------------------------------------------------------ biología y economía
  R['bio-genetica'] = [[null, [
    '¿Qué alelos puede entregar cada progenitor?',
    'Arma el cuadro de Punnett. ¿Qué combinación queda en cada casilla?',
    '¿Qué genotipos muestran el rasgo que piden? ¿Cuántas casillas de las cuatro son?']]];
  R['ecn-mercado'] = [
    [/se fija/, [
      'Reemplaza el precio fijado. ¿Cuánto se quiere comprar y cuánto se ofrece?',
      '¿Cuál de los dos es mayor? ¿Cómo se llama esa situación?']],
    [null, [
      '¿Qué condición se cumple en el equilibrio de un mercado?',
      'Iguala la demanda con la oferta. ¿Qué precio sale?',
      'Con ese precio, ¿qué cantidad se compra y se vende?']]];

  // ------------------------------------------------------------------------------------------ temas de preguntas fijas
  // Dos preguntas por tema: la primera sale con el primer error y la segunda con el siguiente.
  const F = {
    'rv-relaciones': ['Compara las palabras: ¿suenan igual?, ¿se escriben igual?, ¿solo se parecen?, ¿o una nombra al grupo de la otra?', 'Con lo que notaste, ¿qué alternativas ya no pueden ser?'],
    'rv-excluido': ['¿Qué tienen en común casi todos los términos? Ponle nombre a ese grupo.', '¿Cuál es el único que no entra en ese grupo?'],
    'len-signo': ['¿De qué parte del signo o de la comunicación te hablan? Dilo con tus palabras antes de mirar las alternativas.', '¿Te hablan de la forma (lo que se oye o se ve) o del contenido (lo que se entiende)? ¿De quién envía o de quién recibe?'],
    'len-silaba': ['Di la palabra despacio. ¿Dónde hay dos vocales juntas?', '¿Son abiertas (a, e, o) o cerradas (i, u)? ¿Alguna cerrada lleva tilde? Eso decide si van juntas o separadas.'],
    'lit-generos': ['¿Te preguntan por un género o por una figura literaria? ¿Qué rasgo lo distingue de los demás?', 'Busca la pista en la frase: ¿hay una comparación con «como», una exageración, o una cosa que actúa como persona?'],
    'lit-peruana': ['¿De qué época o movimiento es la obra o el autor? ¿Qué más recuerdas de él?', '¿Qué alternativas son de otra época o de otro autor y puedes descartar?'],
    'lit-universal': ['¿De qué época y de qué país es la obra o el autor? ¿Qué más recuerdas de él?', '¿Qué alternativas son de otra época o de otra obra y puedes descartar?'],
    'psf-psicologia': ['¿Qué proceso o qué tipo de aprendizaje describe la situación? Dilo con tus palabras antes de mirar las alternativas.', '¿En qué se diferencia de los otros que aparecen en las alternativas?'],
    'psf-filosofia': ['¿Qué estudia cada disciplina o qué sostuvo cada filósofo de las alternativas? Di una frase por cada uno.', '¿Cuál de ellos calza con lo que describe la pregunta?'],
    // temas agregados con los exámenes de 2025 y 2026 (datos2.js)
    'eco-niveles': ['¿La pregunta habla de una sola especie, de varias especies juntas o del lugar donde viven?', 'Si es una relación entre dos especies, ¿quién gana y quién pierde? Si es un nivel, ¿qué tiene que no tenga el nivel anterior?'],
    'eco-energia': ['¿De dónde saca su alimento o su energía cada ser vivo que se menciona?', '¿En qué paso del recorrido de la energía o de la materia (aire, planta, animal, suelo) ocurre lo que describe la pregunta?'],
    'eco-global': ['¿Qué problema ambiental o qué parte del ambiente describe la pregunta: el aire, el agua, el suelo o el clima?', '¿Qué lo causa? Con esa causa, descarta las alternativas que hablan de otro problema.'],
    'len-lengua': ['¿Lo que describe la pregunta es de toda la humanidad, de una comunidad, de una región o de una sola persona?', '¿Qué nombre recibe cada uno de esos cuatro casos?'],
    'len-fonetica': ['Pronuncia despacio los sonidos de la pregunta. ¿Qué partes de la boca se tocan o se acercan?', '¿El aire se detiene, sale rozando o sale por la nariz? ¿Qué nombre tiene ese caso?'],
    'rv-semantica': ['Separa la palabra en sus partes o mira cómo se usa en la oración. ¿Qué significa cada parte?', '¿La pregunta pide el origen de la palabra, su significado literal o su significado figurado?'],
    'civ-familia': ['¿De dónde nace el vínculo que describe la pregunta: de la sangre, del matrimonio o de una decisión legal?', '¿Qué nombre le da la ley a ese caso? Descarta las alternativas que nombran otra figura.'],
    'civ-ddhh': ['¿El derecho o la institución de la pregunta protege a la persona sola, pide algo al Estado o es de todo un pueblo?', '¿En qué grupo o en qué norma se reconoce eso? Recuerda un ejemplo de cada alternativa.'],
    'his-fuentes': ['¿De qué época habla la pregunta y con qué tipo de huella se la conoce: un texto, un objeto o un relato?', '¿Qué caracteriza a cada alternativa? Di una frase por cada una y quédate con la que calza.'],
    'his-origen': ['¿La pregunta trata de una teoría (quién dijo de dónde vino la cultura) o de un periodo (cuándo se desarrolló una cultura)?', 'Ordena lo que recuerdas: los autores con su idea, o las culturas de la más antigua a la más reciente. ¿Dónde cae lo que piden?'],
    'his-antiguo': ['¿A qué civilización o a qué época pertenece lo que describe la pregunta?', '¿Qué dato conoces de cada alternativa: dónde quedaba o a qué se dedicaba? ¿Cuál calza?'],
    'his-republica': ['¿En qué siglo ocurre lo que describe la pregunta: en el virreinato, en el siglo XIX o en el XX?', '¿Qué hizo cada personaje o qué decía cada norma de las alternativas? Ubícalos en el tiempo.'],
    'geg-tierra': ['¿La pregunta trata de un movimiento de la Tierra, de un autor o de una línea imaginaria?', '¿Qué produce o qué plantea cada alternativa? Descarta las que hablan de otra cosa.'],
    'geg-peru': ['¿En qué región del Perú (costa, sierra, selva o mar) está lo que describe la pregunta?', '¿Qué rasgo lo distingue de las otras alternativas?'],
    'ecn-necesidades': ['¿La pregunta habla de una necesidad o de un bien? ¿Qué rasgo suyo se describe?', '¿Cómo se clasifican las necesidades y los bienes? Ubica el caso en su grupo.'],
    'ecn-dinero': ['¿Qué mide o para qué sirve lo que describe la pregunta: el dinero, el dólar o la producción del país?', '¿Qué pasaría con un ejemplo concreto, con números pequeños? ¿Qué alternativa lo describe?'],
    'zoo-invertebrados': ['¿Qué rasgo del animal menciona la pregunta: cómo se mueve, cuántas patas tiene o cómo es su cuerpo?', '¿Qué grupo de animales tiene justamente ese rasgo?'],
    'zoo-vertebrados': ['¿Dónde vive el animal de la pregunta y qué necesita su cuerpo para respirar, moverse o reproducirse allí?', '¿Qué rasgo distingue a ese grupo de los otros vertebrados?'],
    'eco-ecosistemas': ['¿Qué papel cumple ese ser vivo o ese elemento? ¿De qué se alimenta o qué función tiene?', '¿Qué alternativas describen otro papel u otra capa y puedes descartar?'],
    'geg-hidrografia': ['¿Hacia dónde va el agua de ese río: al Pacífico, al Amazonas o al Titicaca?', '¿Qué características tienen los ríos de esa vertiente? Descarta lo que no encaja.'],
    'qui-materia': ['¿La propiedad depende de cuánta materia hay? Si es un cambio, ¿se forma otra sustancia o solo cambia la forma o el estado?', 'Con eso, ¿qué alternativas puedes descartar?'],
  };
  const GEN = ['¿Qué datos te dan y qué te piden exactamente?', '¿Qué relación o fórmula une esos datos con lo que piden?', '¿Qué operación falta para llegar a la respuesta?'];
  const UNA = ['¿Qué te pide exactamente la pregunta? Dilo con tus palabras antes de volver a mirar las alternativas.', '¿Qué alternativas puedes descartar ya, y por qué?'];

  /** Preguntas de guía para una pregunta: una por cada paso (sin contar el de la respuesta), o dos si solo hay una explicación. */
  function de(id, q) {
    const n = Math.max(0, (q.pasos || []).length - 1);
    if (n === 0) return (F[id] || UNA).slice();
    let g = null;
    for (const r of R[id] || []) { if (r[2] && r[2] !== n) continue; if (!r[0] || r[0].test(q.e)) { g = r[1]; break; } }
    g = (g || []).slice(0, n);
    for (let k = g.length; k < n; k++) g.push(k === 0 ? GEN[0] : k === n - 1 ? GEN[2] : GEN[1]);
    return g;
  }
  root.Guia = { de, temas: R, fijas: F };
})(typeof window !== 'undefined' ? window : globalThis);
