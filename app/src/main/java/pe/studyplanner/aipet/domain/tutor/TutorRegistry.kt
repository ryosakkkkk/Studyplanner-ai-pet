package pe.studyplanner.aipet.domain.tutor

/**
 * Registro único de temas. Para agregar un tema: escribir su generador (extender `BaseGenerator`) y añadir UN
 * [TopicSpec] a [CATALOG] al final de este archivo. Nada más hay que tocar.
 *
 * Convenciones:
 * - El id del tema es el `topicId` de su generador.
 * - La semilla [WORKED_EXAMPLE_SEED] (0) está reservada para el ejemplo resuelto del tema; la sesión de práctica
 *   usa siempre semillas mayores que 0, así que el ejemplo nunca se repite como ejercicio.
 */
object TutorRegistry {

    /** Semilla reservada para el ejemplo resuelto de cada tema. */
    const val WORKED_EXAMPLE_SEED = 0L

    /** Dificultad del ejemplo resuelto: el nivel básico. */
    const val WORKED_EXAMPLE_DIFFICULTY = 1

    private val ID_PATTERN = Regex("""^(.+)-d([1-3])-s(-?\d+)$""")

    private val specs: List<TopicSpec> = CATALOG.also { validate(it) }

    /** Todos los temas, en el orden del catálogo. */
    val topics: List<Topic> = specs.map { it.toTopic() }

    /** Los generadores, en el mismo orden que [topics]. */
    val generators: List<ExerciseGenerator> = specs.map { it.generator }

    /** El tema con ese id, o `null` si no existe. */
    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    /** El tema con ese id; lanza [IllegalArgumentException] si no existe. */
    fun requireTopic(id: String): Topic = requireNotNull(topic(id)) { "Tema desconocido: '$id'" }

    /** El generador del tema [id], o `null` si no existe. */
    fun generator(id: String): ExerciseGenerator? = generators.firstOrNull { it.topicId == id }

    /** Los temas de un área. */
    fun byArea(area: Area): List<Topic> = topics.filter { it.area == area }

    /** Genera un ejercicio del tema [topicId]; lanza [IllegalArgumentException] si el tema no existe. */
    fun generate(topicId: String, seed: Long, difficulty: Int): Exercise {
        val g = requireNotNull(generator(topicId)) { "Tema desconocido: '$topicId'" }
        return g.generate(seed, difficulty)
    }

    /**
     * Vuelve a generar un ejercicio a partir de su id (por ejemplo, el que figura en el CSV de intentos).
     * Devuelve `null` si el id no tiene el formato esperado o el tema ya no existe.
     */
    fun regenerate(exerciseId: String): Exercise? {
        val m = ID_PATTERN.matchEntire(exerciseId) ?: return null
        val seed = m.groupValues[3].toLongOrNull() ?: return null
        val g = generator(m.groupValues[1]) ?: return null
        return g.generate(seed, m.groupValues[2].toInt())
    }

    private fun validate(list: List<TopicSpec>) {
        val ids = list.map { it.generator.topicId }
        require(ids.size == ids.toSet().size) { "Hay ids de tema repetidos: $ids" }
    }
}

/** Datos editables de un tema; el ejemplo resuelto se calcula con el generador. */
internal class TopicSpec(
    val generator: ExerciseGenerator,
    val area: Area,
    val title: String,
    val temario: List<TemarioRef>,
    val objective: String,
    val explanation: List<String>,
) {
    fun toTopic(): Topic = Topic(
        id = generator.topicId,
        area = area,
        title = title,
        temario = temario,
        objective = objective,
        explanation = explanation,
        workedExample = WorkedExample.from(
            generator.generate(TutorRegistry.WORKED_EXAMPLE_SEED, TutorRegistry.WORKED_EXAMPLE_DIFFICULTY),
        ),
    )
}

/**
 * Catálogo de temas. Orden sugerido de estudio: de lo básico a lo más abstracto.
 * Redacción: frases cortas, sin tecnicismos innecesarios y aptas para lectura en voz alta (TTS).
 */
private val CATALOG: List<TopicSpec> = listOf(
    TopicSpec(
        generator = LinearEquationGenerator(),
        area = Area.ALGEBRA,
        title = "Ecuaciones de primer grado",
        temario = listOf(TemarioRef(Area.ALGEBRA, 7)),
        objective = "Despejar la incógnita x en ecuaciones de primer grado, incluso con x en ambos lados y con paréntesis.",
        explanation = listOf(
            "Una ecuación de primer grado es una igualdad con una incógnita, por ejemplo x, que aparece sin exponentes. Resolverla es encontrar el valor de x que hace verdadera la igualdad.",
            "Piensa en una balanza en equilibrio. Todo lo que hagas de un lado, hazlo también del otro. Si restas 5 a la izquierda, resta 5 a la derecha.",
            "Para despejar x, primero deshaz las sumas y las restas, y después las multiplicaciones y las divisiones. Si hay paréntesis, quítalos multiplicando cada término de adentro. Si hay x en los dos lados, junta todas las x en un solo lado.",
            "Comprueba siempre. Reemplaza el valor que encontraste en la ecuación original. Si los dos lados dan el mismo número, tu respuesta es correcta.",
        ),
    ),
    TopicSpec(
        generator = LinearSystemGenerator(),
        area = Area.ALGEBRA,
        title = "Sistemas de ecuaciones lineales 2×2",
        temario = listOf(TemarioRef(Area.ALGEBRA, 8)),
        objective = "Hallar x e y en un sistema de dos ecuaciones con dos incógnitas, por sustitución o por eliminación.",
        explanation = listOf(
            "Un sistema de dos ecuaciones con dos incógnitas pide dos números, x e y, que cumplan las dos ecuaciones a la vez.",
            "En el método de sustitución despejas una incógnita en una ecuación y la reemplazas en la otra. Así te queda una ecuación con una sola incógnita, que ya sabes resolver.",
            "En el método de eliminación multiplicas las ecuaciones por números adecuados para que una incógnita tenga coeficientes opuestos. Al sumar las dos ecuaciones, esa incógnita desaparece.",
            "Cuando ya tienes una incógnita, vuelve a una ecuación para hallar la otra. Al final comprueba que el par de valores cumple las dos ecuaciones.",
        ),
    ),
    TopicSpec(
        generator = PercentageGenerator(),
        area = Area.ARITMETICA,
        title = "Porcentajes: aumentos y descuentos sucesivos",
        temario = listOf(TemarioRef(Area.ARITMETICA, 11, "tanto por ciento, aumentos y descuentos sucesivos")),
        objective = "Calcular aumentos y descuentos, y la variación total cuando se aplican uno tras otro.",
        explanation = listOf(
            "Un porcentaje es una fracción de 100. El 20% de una cantidad es 20 sobre 100 de esa cantidad, es decir, 0,20 veces la cantidad.",
            "Un aumento de 20% deja el 120% del valor, así que multiplicas por 1,20. Un descuento de 20% deja el 80% del valor, así que multiplicas por 0,80.",
            "Cuando los cambios son sucesivos, el segundo porcentaje se aplica sobre el resultado del primero, no sobre el valor original. Por eso un aumento de 20% seguido de un descuento de 20% no te devuelve al valor inicial.",
            "Para la variación total, multiplica los factores de cada cambio. Si el resultado es 1,08, hubo un aumento total de 8%. Si es 0,92, hubo un descuento total de 8%.",
        ),
    ),
    TopicSpec(
        generator = RuleOfThreeGenerator(),
        area = Area.ARITMETICA,
        title = "Regla de tres simple, directa e inversa",
        temario = listOf(
            TemarioRef(Area.ARITMETICA, 10, "directa e inversamente proporcionales"),
            TemarioRef(Area.ARITMETICA, 11),
        ),
        objective = "Reconocer si dos magnitudes son directa o inversamente proporcionales y calcular el valor pedido.",
        explanation = listOf(
            "La regla de tres compara dos magnitudes que se relacionan. Antes de calcular, decide si la relación es directa o inversa.",
            "Es directa si, al aumentar una magnitud, la otra aumenta en la misma proporción. Más cuadernos cuestan más dinero. Se resuelve multiplicando en aspa, o hallando el valor de una unidad y multiplicando.",
            "Es inversa si, al aumentar una magnitud, la otra disminuye. Más obreros terminan la obra en menos días. En este caso se mantiene constante el producto de las dos magnitudes.",
            "Para decidir, hazte esta pregunta: si duplico la primera cantidad, ¿la segunda se duplica o se reduce a la mitad? Esa pregunta evita el error más común.",
        ),
    ),
    TopicSpec(
        generator = McdMcmGenerator(),
        area = Area.ARITMETICA,
        title = "MCD y MCM",
        temario = listOf(TemarioRef(Area.ARITMETICA, 8)),
        objective = "Calcular el máximo común divisor y el mínimo común múltiplo por descomposición en factores primos, y reconocer cuál de los dos pide un problema.",
        explanation = listOf(
            "El máximo común divisor, o MCD, de dos o más números es el mayor número que los divide a todos de forma exacta. El mínimo común múltiplo, o MCM, es el menor número que es múltiplo de todos ellos.",
            "Para hallarlos, descompón cada número en factores primos. Por ejemplo, 12 = 2^2 · 3 y 18 = 2 · 3^2. Escribe cada factor con su exponente.",
            "Para el MCD se multiplican solo los factores comunes, cada uno con su menor exponente. En el ejemplo, los comunes son 2 y 3, los dos con exponente 1, así que el MCD es 2 · 3 = 6.",
            "Para el MCM se multiplican todos los factores, comunes y no comunes, cada uno con su mayor exponente. En el ejemplo, el MCM es 2^2 · 3^2 = 36.",
            "Para decidir qué pide un problema: si hay que cortar o repartir en partes iguales, lo más grande posible, es el MCD. Si hay que saber cuándo coinciden hechos que se repiten cada cierto tiempo, es el MCM. Con dos números, su producto es igual al MCD por el MCM.",
        ),
    ),
    TopicSpec(
        generator = SimpleInterestGenerator(),
        area = Area.ARITMETICA,
        title = "Interés simple",
        temario = listOf(TemarioRef(Area.ARITMETICA, 12, "interés simple")),
        objective = "Calcular el interés, el monto, el capital, la tasa o el tiempo en problemas de interés simple.",
        explanation = listOf(
            "El interés es el pago por usar un dinero durante un tiempo. El dinero que se deposita o se presta se llama capital, y la tasa dice qué porcentaje del capital se paga por cada año.",
            "En el interés simple, el interés de cada año es el mismo, porque siempre se calcula sobre el capital inicial. La fórmula es I = C · r · t ÷ 100, con la tasa r en porcentaje anual y el tiempo t en años.",
            "Ejemplo: 1000 soles al 6% anual durante 2 años dan 1000 · 6 · 2 ÷ 100 = 120 soles de interés.",
            "El monto es todo el dinero al final del plazo: el capital más el interés. En el ejemplo, el monto es 1000 + 120 = 1120 soles.",
            "Si el tiempo está en meses, divídelo entre 12 para pasarlo a años. Si te piden el capital, la tasa o el tiempo, despeja en la fórmula: son solo multiplicaciones y divisiones, como en una regla de tres.",
        ),
    ),
    TopicSpec(
        generator = ExponentLawsGenerator(),
        area = Area.ALGEBRA,
        title = "Leyes de exponentes",
        temario = listOf(TemarioRef(Area.ALGEBRA, 1, "teoría de exponentes")),
        objective = "Aplicar las leyes de exponentes para calcular el valor exacto de potencias, incluso con exponentes negativos y cero.",
        explanation = listOf(
            "Un exponente indica cuántas veces se multiplica la base por sí misma. Por ejemplo, 2^3 significa 2 por 2 por 2, que es 8.",
            "Al multiplicar potencias de la misma base, se suman los exponentes. Al dividirlas, se restan. Por ejemplo, 3^5 por 3^2 es 3^7.",
            "Una potencia elevada a otra potencia multiplica los exponentes: (2^3)^2 es 2^6. La potencia de un producto se reparte entre los factores: (2·3)^2 es 2^2 por 3^2.",
            "Cualquier número distinto de cero elevado a 0 vale 1. Un exponente negativo invierte la base: 2^(-3) es 1 sobre 2^3, o sea 1/8.",
        ),
    ),
    TopicSpec(
        generator = QuadraticGenerator(),
        area = Area.ALGEBRA,
        title = "Ecuaciones cuadráticas con raíces enteras",
        temario = listOf(TemarioRef(Area.ALGEBRA, 7)),
        objective = "Resolver ecuaciones de segundo grado por factorización y hallar sus raíces enteras.",
        explanation = listOf(
            "Una ecuación cuadrática tiene la forma a x² + b x + c = 0. Puede tener hasta dos soluciones, que se llaman raíces.",
            "Antes de empezar, pasa todo a un lado para que el otro lado quede en cero. Si todos los coeficientes tienen un factor común, divide la ecuación entre ese número.",
            "Para factorizar, busca dos números que multiplicados den c y sumados den b. Con ellos escribes la ecuación como (x - r1)(x - r2) = 0. Si el coeficiente de x² no es 1, usa el aspa simple.",
            "Un producto vale cero solo si algún factor vale cero. Por eso x - r1 = 0 o x - r2 = 0. Cuidado con el signo: el factor (x - 3) da la raíz positiva 3, y el factor (x + 3) da la raíz negativa -3.",
            "Comprueba reemplazando cada raíz en la ecuación original. Las dos deben cumplirla.",
        ),
    ),
    TopicSpec(
        generator = LogarithmsGenerator(),
        area = Area.ALGEBRA,
        title = "Logaritmos: definición y propiedades",
        temario = listOf(TemarioRef(Area.ALGEBRA, 11, "definición y propiedades")),
        objective = "Calcular logaritmos con su definición, aplicar las propiedades del producto, el cociente y la potencia, y resolver ecuaciones logarítmicas sencillas.",
        explanation = listOf(
            "El logaritmo de un número, en una base, es el exponente al que hay que elevar esa base para obtener el número. Como 2^3 = 8, entonces log_2(8) = 3. Se lee: logaritmo de 8 en base 2 es 3.",
            "Para calcular un logaritmo, pregúntate: ¿a qué exponente elevo la base para llegar al número? Por ejemplo, log_3(81) = 4, porque 3^4 = 81. Escribir las potencias de la base te ayuda a encontrarlo.",
            "El logaritmo de un producto es la suma de los logaritmos: log_b(x · y) = log_b(x) + log_b(y). El logaritmo de un cociente es la resta: log_b(x ÷ y) = log_b(x) - log_b(y).",
            "El logaritmo de una potencia se calcula sacando el exponente: log_b(x^k) = k · log_b(x). Además, log_b(b) = 1 y log_b(1) = 0 en cualquier base.",
            "Para resolver una ecuación logarítmica, vuelve a la definición. Si log_2(x) = 5, entonces x = 2^5 = 32. Si la incógnita está en la base, como log_x(81) = 4, busca el número que elevado a 4 da 81.",
        ),
    ),
    TopicSpec(
        generator = TriangleAnglesGenerator(),
        area = Area.GEOMETRIA,
        title = "Ángulos en el triángulo",
        temario = listOf(
            TemarioRef(Area.GEOMETRIA, 2, "ángulos suplementarios"),
            TemarioRef(Area.GEOMETRIA, 3, "propiedades generales del triángulo"),
        ),
        objective = "Usar la suma de los ángulos interiores (180°) y la propiedad del ángulo exterior para hallar ángulos desconocidos.",
        explanation = listOf(
            "Los tres ángulos interiores de cualquier triángulo suman 180 grados. Si conoces dos, el tercero es 180 menos la suma de los dos.",
            "Un ángulo exterior se forma al prolongar un lado del triángulo. Junto con el ángulo interior vecino suman 180 grados.",
            "Una propiedad muy útil: el ángulo exterior es igual a la suma de los dos ángulos interiores que no son vecinos suyos.",
            "Cuando los ángulos vienen con x, plantea una ecuación: la suma de las expresiones debe ser 180. Resuelve x y reemplaza para hallar cada ángulo.",
        ),
    ),
    TopicSpec(
        generator = PythagorasGenerator(),
        area = Area.GEOMETRIA,
        title = "Teorema de Pitágoras",
        temario = listOf(
            TemarioRef(Area.GEOMETRIA, 3, "triángulos rectángulos"),
            TemarioRef(Area.GEOMETRIA, 10, "relaciones métricas en triángulos rectángulos"),
        ),
        objective = "Calcular un lado de un triángulo rectángulo y resolver problemas de escaleras, diagonales y distancias.",
        explanation = listOf(
            "En un triángulo rectángulo, los lados que forman el ángulo recto se llaman catetos. El lado opuesto al ángulo recto, que es el más largo, se llama hipotenusa.",
            "El teorema de Pitágoras dice que el cuadrado de la hipotenusa es igual a la suma de los cuadrados de los catetos: c² = a² + b².",
            "Para hallar la hipotenusa, suma los cuadrados de los catetos y saca la raíz cuadrada. Para hallar un cateto, resta: al cuadrado de la hipotenusa quítale el cuadrado del otro cateto.",
            "Algunas ternas se repiten mucho: 3, 4 y 5; 5, 12 y 13; 8, 15 y 17; 7, 24 y 25. Sus múltiplos también sirven, como 6, 8 y 10. Si las reconoces, ahorras cálculos.",
            "La diagonal de un cuadrado de lado a mide a por raíz de 2. Siempre dibuja el triángulo rectángulo antes de calcular.",
        ),
    ),
    TopicSpec(
        generator = TrigRatiosGenerator(),
        area = Area.TRIGONOMETRIA,
        title = "Razones trigonométricas en triángulos notables",
        temario = listOf(
            TemarioRef(Area.TRIGONOMETRIA, 2, "ángulos notables"),
            TemarioRef(Area.GEOMETRIA, 3, "triángulos rectángulos notables de 30°, 37°, 45°, 53° y 60°"),
        ),
        objective = "Calcular razones y lados usando los triángulos notables de 30° y 60°, de 45° y de 37° y 53°.",
        explanation = listOf(
            "En un triángulo rectángulo, el seno de un ángulo agudo es cateto opuesto sobre hipotenusa. El coseno es cateto adyacente sobre hipotenusa. La tangente es cateto opuesto sobre cateto adyacente.",
            "El triángulo de 45 grados tiene lados proporcionales a 1, 1 y raíz de 2. El de 30 y 60 grados tiene lados proporcionales a 1, raíz de 3 y 2: el lado 1 está frente a 30 grados y la hipotenusa mide 2.",
            "El triángulo de 37 y 53 grados es una aproximación del triángulo de lados 3, 4 y 5. El lado 3 está frente a 37 grados, el lado 4 frente a 53 grados y la hipotenusa mide 5. En el examen se trabaja así.",
            "Para evaluar una expresión, reemplaza cada razón por su valor y opera con cuidado las fracciones y las raíces. Dibuja el triángulo y marca el ángulo para no confundir opuesto con adyacente.",
        ),
    ),
    TopicSpec(
        generator = ArithmeticSequenceGenerator(),
        area = Area.RAZONAMIENTO,
        title = "Sucesiones aritméticas",
        temario = listOf(
            TemarioRef(Area.RAZONAMIENTO, 5),
            TemarioRef(Area.ALGEBRA, 12, "progresiones aritméticas"),
        ),
        objective = "Hallar la razón, un término cualquiera y el número de términos de una sucesión aritmética.",
        explanation = listOf(
            "Una sucesión aritmética es una lista de números en la que, para pasar de un término al siguiente, siempre se suma la misma cantidad. Esa cantidad se llama razón.",
            "La razón se halla restando dos términos consecutivos: el segundo menos el primero. Si es positiva, la sucesión crece. Si es negativa, decrece.",
            "Para llegar al término de lugar n desde el primero hay que dar n - 1 saltos, y cada salto vale la razón. Entonces, al primer término le sumas la razón multiplicada por n - 1.",
            "Si conoces el último término, puedes hallar cuántos términos hay: divide la diferencia entre el último y el primero entre la razón, y suma 1. El resultado debe ser un número entero positivo.",
        ),
    ),
    TopicSpec(
        generator = AveragesGenerator(),
        area = Area.RAZONAMIENTO,
        title = "Promedios: aritmético y ponderado",
        temario = listOf(TemarioRef(Area.RAZONAMIENTO, 7, "promedios aritmético y ponderado")),
        objective = "Calcular el promedio de varios datos, hallar un dato que falta y resolver problemas de promedio ponderado.",
        explanation = listOf(
            "El promedio aritmético de varios números es lo que le tocaría a cada uno si el total se repartiera en partes iguales. Se calcula sumando todos los números y dividiendo entre la cantidad de números.",
            "Por ejemplo, el promedio de 12, 15 y 18 es la suma 45 dividida entre 3, que da 15.",
            "Una idea muy útil: la suma total es igual al promedio por la cantidad de datos. Si conoces el promedio y cuántos datos hay, conoces la suma, y con la suma puedes hallar un dato que falta.",
            "En el promedio ponderado, cada dato tiene un peso que indica cuánto vale. Se multiplica cada dato por su peso, se suman los productos y se divide entre la suma de los pesos, no entre la cantidad de datos.",
            "Si juntas dos grupos, no promedies los promedios sin pensar. Calcula la suma de cada grupo, que es su promedio por su cantidad, suma ambas y divide entre el total de datos.",
        ),
    ),
)
