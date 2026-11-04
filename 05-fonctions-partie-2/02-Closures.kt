// ==============================================================================
// FICHE FORMATEUR — Closures (capture de variables)
// Objectif : comprendre qu'une lambda capture les variables de son contexte (y compris des var et `this`), avec ses avantages et ses risques.
// À présenter (dans l'ordre) :
//   1. makeCounter : la variable survit à l'appel de la fonction
//   2. Callbacks (Button.onClick) : capture d'un compteur local
//   3. Piège de la boucle while (var partagée) vs for (nouvelle val à chaque tour)
//   4. Fuite mémoire : capture implicite de `this`
//   5. Closure vs classe : quand préférer une classe
// Lien Android : chaque `onClick = { ... }` en Compose est une closure (capture de l'état, du ViewModel,
//   du NavController) ; `viewModelScope.launch { ... }` capture le ViewModel ; une lambda qui capture
//   une Activity/Context et qui survit à sa destruction = fuite mémoire (d'où ViewModel sans Context,
//   lifecycleScope / collectAsStateWithLifecycle qui s'annulent automatiquement).
// Pièges / questions fréquentes : capturer une Activity dans un callback long ; état mutable capturé
//   et accédé depuis plusieurs threads/coroutines (Dispatchers.Default) ; en Compose, une var locale
//   capturée n'est PAS un état : il faut `remember { mutableStateOf(...) }`.
// Si peu de temps : 1, 3 et 4 ; sauter accumulateur, concurrence et Timer.
// ==============================================================================
/**
 * Exemple 2 : Closures
 *
 * Capture de variables, état caché
 * Implications et risques
 */

// ===== Closure de base =====

// [À DIRE] ≠ Java : Kotlin capture des `var` modifiables (pas besoin d'« effectively final ») ;
//   le compilateur les enveloppe dans un objet Ref partagé.
fun makeCounter(): () -> Int {
    var n = 0
    return { ++n }
}

fun demonstrateBasicClosure() {
    println("=== Closure : compteur ===")

    val counter1 = makeCounter()
    println("counter1(): ${counter1()}")  // 1
    println("counter1(): ${counter1()}")  // 2
    println("counter1(): ${counter1()}")  // 3

    // counter2 a son propre état
    val counter2 = makeCounter()
    println("counter2(): ${counter2()}")  // 1
    println("counter2(): ${counter2()}")  // 2
}

// ===== Closure capturant plusieurs variables =====

fun makeAdder(initialValue: Int): (Int) -> Int {
    var current = initialValue
    return { x ->
        current += x
        current
    }
}

fun demonstrateMultipleCaptures() {
    println("\n=== Capture multiple ===")

    val adder = makeAdder(10)
    println("add(5): ${adder(5)}")   // 15
    println("add(3): ${adder(3)}")   // 18
    println("add(10): ${adder(10)}") // 28
}

// ===== Closure pour callbacks =====
// [À DIRE] Ce `Button` maison n'a rien à voir avec `androidx.compose.material3.Button` (attention aux imports en TP).
// [ANDROID] Ancien style View (J5) : `button.setOnClickListener { clickCount++ }` = exactement ce patron.
//   En Compose : `Button(onClick = { count++ }) { Text("OK") }` avec `var count by remember { mutableStateOf(0) }`.

class Button(private val label: String) {
    private val clickListeners = mutableListOf<() -> Unit>()

    fun onClick(listener: () -> Unit) {
        clickListeners.add(listener)
    }

    fun click() {
        println("Button '$label' clicked")
        clickListeners.forEach { it() }
    }
}

fun demonstrateClosureCallbacks() {
    println("\n=== Closure pour callbacks ===")

    val button = Button("Submit")

    // [PIÈGE] Dans un @Composable, une simple `var clickCount = 0` capturée serait remise à 0 à chaque
    //   recomposition et ne redessinerait rien : il faut `remember { mutableStateOf(0) }`.
    var clickCount = 0
    button.onClick {
        clickCount++
        println("  Click count: $clickCount")
    }

    button.onClick {
        println("  Another handler")
    }

    button.click()
    button.click()
    button.click()
}

// ===== Closure pour accumuler des résultats =====
// [SAUTER] Variante de makeCounter ; à passer si le temps manque.

fun createLogger(): (String) -> List<String> {
    val logs = mutableListOf<String>()
    return { message ->
        logs.add(message)
        logs.toList()  // Retourne une copie
    }
}

fun demonstrateAccumulator() {
    println("\n=== Closure accumulateur ===")

    val logger = createLogger()

    println("Log 1: ${logger("Started")}")
    println("Log 2: ${logger("Processing")}")
    println("Log 3: ${logger("Completed")}")
}

// ===== Closure avec boucle : le piège classique =====

// [À DIRE] La lambda capture la VARIABLE i, pas sa valeur au moment de la création : elle lit i à l'exécution (= 4).
// [À DIRE] Faire deviner la sortie aux apprenants avant d'exécuter : bon moment d'interaction.
fun buildFiltersWrong(): List<(Int) -> Boolean> {
    val filters = mutableListOf<(Int) -> Boolean>()
    var i = 1
    while (i <= 3) {
        filters.add { x -> x % i == 0 }  // Capture i mutable
        i++
    }
    return filters
}

fun buildFiltersCorrect(): List<(Int) -> Boolean> {
    val filters = mutableListOf<(Int) -> Boolean>()
    for (i in 1..3) {
        // En Kotlin, i de la boucle for est un nouveau val à chaque itération
        filters.add { x -> x % i == 0 }
    }
    return filters
}

fun demonstrateLoopClosure() {
    println("\n=== Closure dans boucle ===")

    val wrong = buildFiltersWrong()
    println("Filtres (mauvais):")
    wrong.forEachIndexed { index, filter ->
        println("  Filter $index teste 6: ${filter(6)}")  // Tous testent % 4
    }

    val correct = buildFiltersCorrect()
    println("\nFiltres (correct):")
    correct.forEachIndexed { index, filter ->
        println("  Filter $index teste 6: ${filter(6)}")
    }
}

// ===== Risque : fuite mémoire =====
// [ANDROID] Version Android du problème : une lambda qui référence une Activity (ou son Context, une View)
//   et qui est stockée plus longtemps qu'elle (singleton, callback réseau, ViewModel) empêche le GC
//   après une rotation d'écran. Règle : jamais de Context d'Activity dans un ViewModel ; utiliser
//   `applicationContext` si besoin ; LeakCanary pour détecter.

class HeavyObject(val name: String) {
    private val data = ByteArray(10_000_000)  // 10 MB

    fun createCallback(): () -> Unit {
        // ⚠️ La lambda capture 'this' (HeavyObject)
        return {
            println("Callback from $name")
        }
    }

    fun createBetterCallback(): () -> Unit {
        // ✅ Capture seulement ce qui est nécessaire
        val capturedName = name
        return {
            println("Callback from $capturedName")
        }
    }
}

fun demonstrateMemoryLeak() {
    println("\n=== Risque de fuite mémoire ===")

    // ❌ La callback garde une référence à HeavyObject
    val callback1 = HeavyObject("Heavy1").createCallback()

    // ✅ La callback ne capture que le nom
    val callback2 = HeavyObject("Heavy2").createBetterCallback()

    callback1()
    callback2()

    println("⚠️ callback1 garde une référence à tout HeavyObject")
    println("✅ callback2 ne capture que le String")
}

// ===== Closure et concurrence =====
// [À DIRE] Avec les coroutines (fin du J1), ce risque revient si plusieurs `launch(Dispatchers.Default)` modifient
//   la même var ; solutions : AtomicInteger, Mutex, ou mieux un `MutableStateFlow.update { it + 1 }` (atomique).
// [SAUTER] Si peu de temps : le dire en une phrase sans dérouler l'exemple.

fun createUnsafeCounter(): () -> Int {
    var count = 0
    return {
        count++  // ⚠️ Pas thread-safe
    }
}

fun demonstrateConcurrency() {
    println("\n=== Closure et concurrence ===")

    val counter = createUnsafeCounter()

    println("⚠️ Closure avec état mutable + threads = danger")
    println("Solution : synchronisation ou types atomiques")

    // Exemple d'utilisation simple (pas multithread ici)
    repeat(5) {
        println("Count: ${counter()}")
    }
}

// ===== Cas d'usage légitime : factory avec configuration =====
// [ANDROID] Utile pour la validation de formulaire Compose : `isError = !validateUsername(username)` dans un OutlinedTextField.

fun createValidator(minLength: Int, maxLength: Int): (String) -> Boolean {
    require(minLength >= 0)
    require(maxLength >= minLength)

    return { input ->
        input.length in minLength..maxLength
    }
}

fun demonstrateLegitimateUse() {
    println("\n=== Usage légitime : factory ===")

    val validateUsername = createValidator(3, 20)
    val validatePassword = createValidator(8, 128)

    println("'ab' username valide: ${validateUsername("ab")}")
    println("'alice' username valide: ${validateUsername("alice")}")
    println("'pass' password valide: ${validatePassword("pass")}")
    println("'secure123' password valide: ${validatePassword("secure123")}")
}

// ===== Closure vs classe =====

// [PIÈGE] `System.currentTimeMillis()` peut sauter (changement d'heure système) : pour mesurer une durée,
//   préférer `System.nanoTime()` / `kotlin.time.TimeSource.Monotonic`, et sur Android `SystemClock.elapsedRealtime()`.
// [ATTENTION FORMATEUR] `Thread.sleep` plus bas : acceptable dans un main() de démo, mais à ne JAMAIS faire
//   sur le thread principal Android (ANR) ; en coroutine on utilise `delay()`.
// Avec closure
fun makeTimerClosure(): () -> Long {
    val startTime = System.currentTimeMillis()
    return {
        System.currentTimeMillis() - startTime
    }
}

// Avec classe (plus explicite)
class Timer {
    private val startTime = System.currentTimeMillis()

    fun elapsed(): Long {
        return System.currentTimeMillis() - startTime
    }
}

fun demonstrateClosureVsClass() {
    println("\n=== Closure vs Classe ===")

    val timerClosure = makeTimerClosure()
    val timerClass = Timer()

    Thread.sleep(100)

    println("Closure elapsed: ${timerClosure()}ms")
    println("Class elapsed: ${timerClass.elapsed()}ms")

    println("\nClosure : concise, état caché")
    println("Classe : explicite, testable, extensible")
}

fun main() {
    demonstrateBasicClosure()
    demonstrateMultipleCaptures()
    demonstrateClosureCallbacks()
    demonstrateAccumulator()
    demonstrateLoopClosure()
    demonstrateMemoryLeak()
    demonstrateConcurrency()
    demonstrateLegitimateUse()
    demonstrateClosureVsClass()

    println("\n=== Résumé ===")
    println("1. Closure : lambda qui capture des variables externes")
    println("2. Utile pour : callbacks, accumulateurs, factories")
    println("3. Risques :")
    println("   - État caché (moins prévisible)")
    println("   - Fuites mémoire (capture d'objets lourds)")
    println("   - Concurrence (état mutable partagé)")
    println("4. Préférer classe explicite si logique complexe")
}
