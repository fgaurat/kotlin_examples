// ==============================================================================
// FICHE FORMATEUR — Dispatchers et withContext
// Objectif : savoir sur quel thread tourne une coroutine et en changer avec withContext.
// À présenter (dans l'ordre) :
//   1. Default = calcul CPU, IO = appels bloquants (demo01, demo04)
//   2. Main = thread UI, absent en console (demo08)
//   3. withContext : changer de dispatcher le temps d'un bloc puis revenir (demo12)
//   4. Fonction « main-safe » : c'est elle qui choisit son dispatcher (readFile, demo09)
//   5. Règle d'or : CPU → Default, bloquant → IO, UI → Main (demo15)
// Lien Android : viewModelScope/lifecycleScope sur Dispatchers.Main.immediate ; withContext(Dispatchers.IO)
//   dans le repository ; Retrofit et Room en suspend sont déjà main-safe (TP04, TP07)
// Pièges / questions fréquentes : withContext(IO) inutile autour d'un appel Retrofit suspend ;
//   calcul CPU sur IO ; Dispatchers.Main en test JVM → Dispatchers.setMain + StandardTestDispatcher (J3, TP08)
// Si peu de temps : demo01, 04, 08, 09, 12, 15. Sauter 02, 03, 05, 07, 10, 11, 14, 16 à 20.
// ==============================================================================
// [ATTENTION FORMATEUR] Le fichier ne compile pas tel quel : voir withAsync (demo13).
/**
 * 03-Dispatchers.kt
 *
 * Exemples sur les Dispatchers (Répartiteurs)
 * - Dispatchers.Default (CPU-intensive)
 * - Dispatchers.IO (I/O operations)
 * - Dispatchers.Main (UI, simulation)
 * - Dispatchers.Unconfined
 * - withContext (changement de dispatcher)
 * - Choisir le bon dispatcher
 */

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// =============================================================================
// 1. DISPATCHERS.DEFAULT (CPU-INTENSIVE)
// =============================================================================

/**
 * Dispatchers.Default est optimisé pour les tâches CPU-intensive
 */
// [À DIRE] Un dispatcher décide sur quel(s) thread(s) la coroutine s'exécute. Faire lire le nom du thread affiché.
// [ANDROID] Trier ou filtrer une grosse liste, traiter une image : `withContext(Dispatchers.Default) { ... }`.
fun demo01_dispatchersDefault() = runBlocking {
    println("\n=== Demo 01: Dispatchers.Default (CPU) ===")

    launch(Dispatchers.Default) {
        println("Thread: ${Thread.currentThread().name}")
        println("Calcul intensif en cours...")

        // Simulation d'un calcul lourd
        var result = 0
        repeat(1_000_000) {
            result += it
        }

        println("Résultat: $result")
    }

    delay(1000)
}

/**
 * Nombre de threads dans Default
 */
// [SAUTER] Il suffit de dire que le pool Default a autant de threads que de cœurs (minimum 2).
fun demo02_defaultThreadPool() = runBlocking {
    println("\n=== Demo 02: Pool de threads - Dispatchers.Default ===")

    val cores = Runtime.getRuntime().availableProcessors()
    println("Nombre de cores CPU: $cores")
    println("Dispatchers.Default utilise max($cores, 2) threads\n")

    // Lancement de plusieurs coroutines
    repeat(10) { i ->
        launch(Dispatchers.Default) {
            println("Coroutine $i sur thread: ${Thread.currentThread().name}")
            delay(100)
        }
    }

    delay(500)
}

/**
 * Calculs parallèles avec Default
 */
// [SAUTER] demo03 : vrai parallélisme multi-cœurs, hors sujet pour une appli mobile classique.
fun heavyCalculation(n: Int): Long {
    var result = 0L
    repeat(n) {
        result += (it * it).toLong()
    }
    return result
}

fun demo03_parallelCalculations() = runBlocking {
    println("\n=== Demo 03: Calculs parallèles avec Default ===")

    val time = measureTimeMillis {
        val results = (1..4).map { i ->
            async(Dispatchers.Default) {
                println("Calcul $i sur: ${Thread.currentThread().name}")
                heavyCalculation(10_000_000)
            }
        }

        val finalResults = results.awaitAll()
        println("Résultats: ${finalResults.size} calculs terminés")
    }
    println("Temps total: ${time}ms")
}

// =============================================================================
// 2. DISPATCHERS.IO (I/O OPERATIONS)
// =============================================================================

/**
 * Dispatchers.IO pour les opérations I/O
 */
// [À DIRE] IO sert au code BLOQUANT (fichier, socket, JDBC, SDK Java synchrone). delay ne bloque pas, c'est ici une simple simulation.
// [ANDROID] Les DAO Room et les interfaces Retrofit en suspend sont déjà main-safe : on n'ajoute pas de withContext(IO) autour.
fun demo04_dispatchersIO() = runBlocking {
    println("\n=== Demo 04: Dispatchers.IO (I/O) ===")

    launch(Dispatchers.IO) {
        println("Thread: ${Thread.currentThread().name}")
        println("Simulation d'opération I/O (lecture fichier, réseau, DB)...")

        delay(1000) // Simulation I/O
        println("I/O terminé")
    }

    delay(1500)
}

/**
 * Le pool IO est plus grand que Default
 */
// [SAUTER] Taille du pool IO : une phrase suffit (64 threads, ou plus s'il y a plus de cœurs).
fun demo05_ioThreadPool() = runBlocking {
    println("\n=== Demo 05: Pool de threads - Dispatchers.IO ===")

    println("Dispatchers.IO peut avoir jusqu'à 64 threads (ou max(64, nombre de cores))")
    println("Adapté pour les opérations bloquantes\n")

    // Lancement de nombreuses coroutines I/O
    repeat(20) { i ->
        launch(Dispatchers.IO) {
            println("IO Coroutine $i sur: ${Thread.currentThread().name}")
            delay(100)
        }
    }

    delay(500)
}

/**
 * Opérations I/O parallèles
 */
// [À DIRE] Fonction « main-safe » : elle choisit elle-même son dispatcher. On peut l'appeler depuis le thread UI sans risque.
// [ANDROID] C'est la recommandation officielle : le withContext(Dispatchers.IO) se met dans le repository, pas dans le ViewModel.
suspend fun readFile(filename: String): String {
    return withContext(Dispatchers.IO) {
        println("  Lecture de $filename sur: ${Thread.currentThread().name}")
        delay(500) // Simulation lecture fichier
        "Content of $filename"
    }
}

// [À DIRE] async(Dispatchers.IO) est redondant ici, puisque readFile change déjà de dispatcher : `async { readFile(it) }` suffit.
fun demo06_parallelIO() = runBlocking {
    println("\n=== Demo 06: Opérations I/O parallèles ===")

    val time = measureTimeMillis {
        val files = listOf("file1.txt", "file2.txt", "file3.txt", "file4.txt")

        val contents = files.map { filename ->
            async(Dispatchers.IO) {
                readFile(filename)
            }
        }.awaitAll()

        println("Fichiers lus: ${contents.size}")
    }
    println("Temps total: ${time}ms (~500ms grâce au parallélisme)")
}

/**
 * Différence Default vs IO
 */
// [SAUTER] Une boucle vide `repeat(...) { }` peut être optimisée par la JIT, donc le temps n'est pas représentatif.
fun demo07_defaultVsIO() = runBlocking {
    println("\n=== Demo 07: Default vs IO ===")

    // CPU-intensive sur Default
    launch(Dispatchers.Default) {
        println("CPU sur: ${Thread.currentThread().name}")
        repeat(100_000_000) { /* calcul */ }
        println("CPU terminé")
    }

    // I/O-bound sur IO
    launch(Dispatchers.IO) {
        println("I/O sur: ${Thread.currentThread().name}")
        delay(1000) // Simulation I/O
        println("I/O terminé")
    }

    delay(2000)
}

// =============================================================================
// 3. DISPATCHERS.MAIN (SIMULATION)
// =============================================================================

/**
 * Simulation de Dispatchers.Main (pas disponible en CLI)
 */
// [À DIRE] En console JVM, utiliser Dispatchers.Main lève IllegalStateException (« Module with the Main dispatcher is missing »).
// [ANDROID] viewModelScope et lifecycleScope tournent sur Dispatchers.Main.immediate, comme LaunchedEffect et rememberCoroutineScope()
// dans Compose. On ne lance donc presque jamais Dispatchers.Main à la main.
fun demo08_dispatchersMainSimulation() = runBlocking {
    println("\n=== Demo 08: Dispatchers.Main (simulation) ===")

    println("Note: Dispatchers.Main nécessite kotlinx-coroutines-android")
    println("Il est utilisé pour mettre à jour l'UI dans les apps Android/Desktop\n")

    // Simulation du pattern avec Main dispatcher
    println("Pattern typique:")
    println("1. Lancement sur Main")
    println("2. Calcul/IO sur Default/IO avec withContext")
    println("3. Retour sur Main pour update UI")

    // Simulation
    println("\n[Main] Lancement de la tâche")

    val result = withContext(Dispatchers.Default) {
        println("[Background] Calcul en cours...")
        delay(1000)
        42
    }

    println("[Main] Mise à jour UI avec résultat: $result")
}

/**
 * Pattern typique avec Main dispatcher (simulé)
 */
// [ANDROID] Le pattern du TP07 : `viewModelScope.launch { _uiState.value = Loading; val d = repository.load(); _uiState.value = Success(d) }`.
// Le withContext(IO) se trouve idéalement dans repository.load(), pas dans le ViewModel.
suspend fun loadDataAndUpdateUI() {
    // Sur le thread Main (simulé avec le thread actuel)
    println("  [Main] Début du chargement...")

    // Passer sur IO pour charger les données
    val data = withContext(Dispatchers.IO) {
        println("  [IO] Chargement des données...")
        delay(1000)
        "Données chargées"
    }

    // Retour automatique sur Main
    println("  [Main] Mise à jour de l'UI avec: $data")
}

fun demo09_mainDispatcherPattern() = runBlocking {
    println("\n=== Demo 09: Pattern avec Main dispatcher ===")
    loadDataAndUpdateUI()
}

// =============================================================================
// 4. DISPATCHERS.UNCONFINED
// =============================================================================

/**
 * Dispatchers.Unconfined (non recommandé en général)
 */
// [SAUTER] Unconfined ne s'utilise pas dans une appli.
// [ANDROID] En test, on utilise plutôt StandardTestDispatcher / UnconfinedTestDispatcher de kotlinx-coroutines-test (J3, TP08).
fun demo10_dispatchersUnconfined() = runBlocking {
    println("\n=== Demo 10: Dispatchers.Unconfined ===")

    launch(Dispatchers.Unconfined) {
        println("Avant delay: ${Thread.currentThread().name}")
        delay(500)
        println("Après delay: ${Thread.currentThread().name}")
        println("Note: Le thread peut changer après une suspension!")
    }

    delay(1000)
}

/**
 * Comparaison des comportements de dispatchers
 */
// [SAUTER] Retenir seulement que, sans dispatcher, launch hérite de celui du parent.
fun demo11_dispatcherComparison() = runBlocking {
    println("\n=== Demo 11: Comparaison des dispatchers ===")

    println("Dispatchers.Default:")
    launch(Dispatchers.Default) {
        println("  Thread: ${Thread.currentThread().name}")
    }

    delay(100)

    println("\nDispatchers.IO:")
    launch(Dispatchers.IO) {
        println("  Thread: ${Thread.currentThread().name}")
    }

    delay(100)

    println("\nDispatchers.Unconfined:")
    launch(Dispatchers.Unconfined) {
        println("  Thread: ${Thread.currentThread().name}")
    }

    delay(100)

    println("\nPas de dispatcher (hérite du parent):")
    launch {
        println("  Thread: ${Thread.currentThread().name}")
    }

    delay(500)
}

// =============================================================================
// 5. WITHCONTEXT (CHANGEMENT DE DISPATCHER)
// =============================================================================

/**
 * Utilisation basique de withContext
 */
// [À DIRE] withContext est L'outil à retenir. Il ne crée pas de coroutine en parallèle : il suspend, exécute le bloc ailleurs,
// puis revient sur le dispatcher d'origine avec la valeur.
// [ANDROID] Dans un repository : `suspend fun lireCache(): String = withContext(Dispatchers.IO) { file.readText() }`.
suspend fun performTaskWithContextSwitch(): String {
    // Calcul sur Default
    val result1 = withContext(Dispatchers.Default) {
        println("  Calcul sur: ${Thread.currentThread().name}")
        delay(500)
        42
    }

    // I/O sur IO
    val result2 = withContext(Dispatchers.IO) {
        println("  I/O sur: ${Thread.currentThread().name}")
        delay(500)
        "data"
    }

    return "Results: $result1, $result2"
}

fun demo12_withContext() = runBlocking {
    println("\n=== Demo 12: withContext ===")

    println("Début sur: ${Thread.currentThread().name}")
    val result = performTaskWithContextSwitch()
    println("Fin sur: ${Thread.currentThread().name}")
    println("Résultat: $result")
}

/**
 * withContext vs async-await
 */
// [ATTENTION FORMATEUR] NE COMPILE PAS : async est une extension de CoroutineScope, indisponible dans une simple suspend fun
// (« Unresolved reference: async »). Tout le fichier échoue. Il faudrait écrire `coroutineScope { async(...) { ... }.await() }`.
// [À DIRE] async immédiatement suivi de await, c'est withContext en plus lourd.
suspend fun withAsync(): String {
    val deferred = async(Dispatchers.Default) {
        delay(1000)
        "Result"
    }
    return deferred.await()
}

suspend fun withWithContext(): String {
    return withContext(Dispatchers.Default) {
        delay(1000)
        "Result"
    }
}

fun demo13_withContextVsAsync() = runBlocking {
    println("\n=== Demo 13: withContext vs async-await ===")

    println("Avec async-await:")
    val time1 = measureTimeMillis {
        val result = withAsync()
        println("  Résultat: $result")
    }
    println("  Temps: ${time1}ms")

    println("\nAvec withContext:")
    val time2 = measureTimeMillis {
        val result = withWithContext()
        println("  Résultat: $result")
    }
    println("  Temps: ${time2}ms")

    println("\nNote: withContext est plus simple et efficace pour changer de dispatcher")
}

/**
 * Changements multiples de contexte
 */
// [SAUTER] Si on la montre : Default et IO partagent les mêmes threads (DefaultDispatcher-worker-N), d'où des noms
// parfois identiques. Le passage de Default à IO peut se faire sans changer de thread.
suspend fun complexOperation(): String {
    println("1. Sur: ${Thread.currentThread().name}")

    val data = withContext(Dispatchers.IO) {
        println("2. Lecture I/O sur: ${Thread.currentThread().name}")
        delay(500)
        "raw_data"
    }

    val processed = withContext(Dispatchers.Default) {
        println("3. Traitement CPU sur: ${Thread.currentThread().name}")
        delay(500)
        data.uppercase()
    }

    val saved = withContext(Dispatchers.IO) {
        println("4. Écriture I/O sur: ${Thread.currentThread().name}")
        delay(500)
        "saved_$processed"
    }

    println("5. Retour sur: ${Thread.currentThread().name}")
    return saved
}

fun demo14_multipleContextSwitches() = runBlocking {
    println("\n=== Demo 14: Changements multiples de contexte ===")
    val result = complexOperation()
    println("Résultat final: $result")
}

// =============================================================================
// 6. CHOISIR LE BON DISPATCHER
// =============================================================================

/**
 * Exemple: API call + processing + cache
 */
// [ANDROID] Avec Retrofit + kotlinx.serialization (TP04), l'appel ET le parsing JSON sont faits dans une suspend fun main-safe :
// pas de withContext, ni pour l'un ni pour l'autre. Default ne sert au parsing qu'en cas de très gros volume traité à la main.
suspend fun fetchAndProcessData(id: Int): String {
    // 1. Appel réseau sur IO
    val rawData = withContext(Dispatchers.IO) {
        println("  [IO] Appel API pour id=$id")
        delay(500)
        "{\"id\": $id, \"value\": 42}"
    }

    // 2. Parsing et calcul sur Default
    val processed = withContext(Dispatchers.Default) {
        println("  [CPU] Parsing et calcul")
        delay(300)
        rawData.uppercase()
    }

    // 3. Sauvegarde cache sur IO
    withContext(Dispatchers.IO) {
        println("  [IO] Sauvegarde en cache")
        delay(200)
    }

    return processed
}

fun demo15_choosingRightDispatcher() = runBlocking {
    println("\n=== Demo 15: Choisir le bon dispatcher ===")

    val result = fetchAndProcessData(123)
    println("Résultat: $result")

    println("\nRègles:")
    println("- Dispatchers.IO: réseau, fichiers, DB")
    println("- Dispatchers.Default: calculs, parsing, traitement")
    println("- Dispatchers.Main: mise à jour UI (Android/Desktop)")
}

/**
 * Traitement par lots avec bon dispatcher
 */
// [SAUTER] demo16 : rien de nouveau.
suspend fun processItem(item: Int): String {
    // Lecture (I/O)
    val data = withContext(Dispatchers.IO) {
        delay(100)
        "data_$item"
    }

    // Traitement (CPU)
    val result = withContext(Dispatchers.Default) {
        delay(50)
        data.reversed()
    }

    return result
}

fun demo16_batchProcessing() = runBlocking {
    println("\n=== Demo 16: Traitement par lots ===")

    val time = measureTimeMillis {
        val items = (1..10).toList()

        val results = items.map { item ->
            async {
                processItem(item)
            }
        }.awaitAll()

        println("Items traités: ${results.size}")
    }
    println("Temps total: ${time}ms")
}

/**
 * Mauvais choix de dispatcher
 */
// [ATTENTION FORMATEUR] Une seule tâche CPU s'exécute de la même façon sur IO ou Default (mêmes threads) : les temps
// seront quasi identiques, la démo ne prouve rien. Le vrai problème : IO autorise 64 threads, donc bien plus de tâches CPU
// concurrentes que de cœurs. [SAUTER]
fun demo17_wrongDispatcherChoice() = runBlocking {
    println("\n=== Demo 17: Mauvais choix de dispatcher ===")

    println("MAUVAIS: Calcul CPU sur IO dispatcher")
    val time1 = measureTimeMillis {
        withContext(Dispatchers.IO) {
            repeat(100_000_000) { /* calcul lourd */ }
        }
    }
    println("  Temps: ${time1}ms")

    println("\nBON: Calcul CPU sur Default dispatcher")
    val time2 = measureTimeMillis {
        withContext(Dispatchers.Default) {
            repeat(100_000_000) { /* calcul lourd */ }
        }
    }
    println("  Temps: ${time2}ms")

    println("\nNote: Utiliser le bon dispatcher améliore les performances")
}

// =============================================================================
// 7. DISPATCHERS PERSONNALISÉS
// =============================================================================

/**
 * Créer un dispatcher personnalisé
 */
// [SAUTER] Section 7 entière, hors périmètre.
// [ATTENTION FORMATEUR] newSingleThreadContext et newFixedThreadPoolContext sont @DelicateCoroutinesApi (avertissement).
// À préférer : `Dispatchers.Default.limitedParallelism(1)` ou `Executors.newFixedThreadPool(4).asCoroutineDispatcher()`.
fun demo18_customDispatcher() = runBlocking {
    println("\n=== Demo 18: Dispatcher personnalisé ===")

    // Dispatcher à thread unique
    val singleThreadDispatcher = newSingleThreadContext("MyThread")

    try {
        launch(singleThreadDispatcher) {
            println("Thread: ${Thread.currentThread().name}")
            delay(500)
            println("Toujours sur: ${Thread.currentThread().name}")
        }

        delay(1000)
    } finally {
        singleThreadDispatcher.close()
    }
}

/**
 * Dispatcher avec pool de threads fixe
 */
fun demo19_fixedThreadPoolDispatcher() = runBlocking {
    println("\n=== Demo 19: Dispatcher avec pool fixe ===")

    val fixedDispatcher = newFixedThreadPoolContext(4, "FixedPool")

    try {
        repeat(10) { i ->
            launch(fixedDispatcher) {
                println("Task $i sur: ${Thread.currentThread().name}")
                delay(500)
            }
        }

        delay(1000)
    } finally {
        fixedDispatcher.close()
    }

    println("\nNote: Pensez à fermer les dispatchers personnalisés!")
}

/**
 * limitedParallelism pour limiter le parallélisme
 */
// [ATTENTION FORMATEUR] (1) measureTimeMillis ne mesure que les launch (≈ 0 ms). (2) limitedParallelism limite les threads
// utilisés en même temps, pas les coroutines : delay libère le thread, donc les 10 tâches démarrent presque ensemble.
// « Seulement 2 en parallèle » n'est vrai que pour du code bloquant. Pour limiter les coroutines : Semaphore(2).withPermit { }.
fun demo20_limitedParallelism() = runBlocking {
    println("\n=== Demo 20: limitedParallelism ===")

    // Limiter à 2 coroutines parallèles sur IO
    val limitedDispatcher = Dispatchers.IO.limitedParallelism(2)

    val time = measureTimeMillis {
        repeat(10) { i ->
            launch(limitedDispatcher) {
                println("Task $i commence sur: ${Thread.currentThread().name}")
                delay(500)
                println("Task $i terminé")
            }
        }
    }

    delay(3000)
    println("Temps total: ${time}ms")
    println("Note: Seulement 2 tâches s'exécutent en parallèle")
}

// =============================================================================
// RÉSUMÉ ET BONNES PRATIQUES
// =============================================================================

// [ATTENTION FORMATEUR] Points datés dans le texte du résumé : « SharedPreferences » → DataStore est recommandé (TP12),
// et il est déjà suspend/Flow. Dans « Pattern API call », le withContext(IO) est inutile avec Retrofit suspend.
// Dans « Pattern UI update », le withContext(IO) est placé dans le ViewModel : la recommandation est un repository main-safe.
fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Dispatchers")
    println("=".repeat(80))

    println("""
    1. DISPATCHERS PRINCIPAUX:

    Dispatchers.Default:
    - Pool de threads partagé
    - Taille: max(nombre de cores, 2)
    - Utilisation: Calculs CPU-intensive
    - Exemples: parsing JSON, tri, calculs mathématiques

    Dispatchers.IO:
    - Pool de threads partagé (plus grand que Default)
    - Taille: max(64, nombre de cores)
    - Utilisation: Opérations I/O bloquantes
    - Exemples: lecture/écriture fichiers, appels réseau, requêtes DB

    Dispatchers.Main:
    - Thread UI (Android/Desktop uniquement)
    - Utilisation: Mise à jour de l'interface utilisateur
    - Nécessite: kotlinx-coroutines-android ou kotlinx-coroutines-javafx

    Dispatchers.Unconfined:
    - Démarre sur le thread appelant, reprend sur thread arbitraire
    - Utilisation: Cas spéciaux, généralement à éviter
    - Attention: Comportement imprévisible

    2. WITHCONTEXT:
    - Change le dispatcher pour un bloc de code
    - Suspend jusqu'à la fin du bloc
    - Retourne la valeur du bloc
    - Plus simple que async { }.await()

    Syntaxe:
        val result = withContext(Dispatchers.IO) {
            // Code exécuté sur IO dispatcher
            "résultat"
        }

    3. CHOISIR LE BON DISPATCHER:

    Utilisez Default pour:
    ✓ Calculs mathématiques
    ✓ Parsing de données
    ✓ Tri et filtrage de listes
    ✓ Traitement d'images
    ✓ Compression/décompression

    Utilisez IO pour:
    ✓ Lecture/écriture de fichiers
    ✓ Appels réseau (HTTP, sockets)
    ✓ Requêtes base de données
    ✓ SharedPreferences (Android)
    ✓ Tout ce qui bloque en attendant I/O

    Utilisez Main pour:
    ✓ Mise à jour de l'UI
    ✓ Appels aux APIs UI
    ✓ Modification de vues
    ✓ Affichage de dialogs

    4. PATTERNS COURANTS:

    Pattern API call:
        suspend fun loadUser(id: Int): User {
            // Appel réseau sur IO
            val json = withContext(Dispatchers.IO) {
                api.fetchUser(id)
            }

            // Parsing sur Default
            return withContext(Dispatchers.Default) {
                parseUser(json)
            }
        }

    Pattern UI update (Android):
        fun loadData() {
            viewModelScope.launch {
                // Lancement sur Main
                showLoading()

                // Chargement sur IO
                val data = withContext(Dispatchers.IO) {
                    repository.fetchData()
                }

                // Retour automatique sur Main
                updateUI(data)
            }
        }

    5. DISPATCHERS PERSONNALISÉS:
    - newSingleThreadContext(): thread unique
    - newFixedThreadPoolContext(n): pool de n threads
    - limitedParallelism(n): limite le parallélisme
    - Attention: fermer avec close() après usage!

    BONNES PRATIQUES:
    ✓ Utiliser withContext pour changer de dispatcher
    ✓ Ne pas bloquer Dispatchers.Default (Thread.sleep, etc.)
    ✓ Limiter le parallélisme sur IO si nécessaire
    ✓ Toujours fermer les dispatchers personnalisés
    ✓ Utiliser Default pour CPU, IO pour I/O, Main pour UI

    ANTI-PATTERNS:
    ✗ Calculs lourds sur Dispatchers.IO
    ✗ Opérations I/O sur Dispatchers.Default
    ✗ Bloquer le thread avec Thread.sleep
    ✗ Oublier de fermer les dispatchers personnalisés
    ✗ Utiliser Dispatchers.Unconfined par défaut
    ✗ Créer trop de dispatchers personnalisés

    RÈGLE D'OR:
    "CPU sur Default, I/O sur IO, UI sur Main"
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(22) + "KOTLIN COROUTINES - DISPATCHERS")
    println("=" + "=".repeat(78) + "=")

    // 1. Dispatchers.Default
    demo01_dispatchersDefault()
    demo02_defaultThreadPool()
    demo03_parallelCalculations()

    // 2. Dispatchers.IO
    demo04_dispatchersIO()
    demo05_ioThreadPool()
    demo06_parallelIO()
    demo07_defaultVsIO()

    // 3. Dispatchers.Main (simulation)
    demo08_dispatchersMainSimulation()
    demo09_mainDispatcherPattern()

    // 4. Dispatchers.Unconfined
    demo10_dispatchersUnconfined()
    demo11_dispatcherComparison()

    // 5. withContext
    demo12_withContext()
    demo13_withContextVsAsync()
    demo14_multipleContextSwitches()

    // 6. Choisir le bon dispatcher
    demo15_choosingRightDispatcher()
    demo16_batchProcessing()
    demo17_wrongDispatcherChoice()

    // 7. Dispatchers personnalisés
    demo18_customDispatcher()
    demo19_fixedThreadPoolDispatcher()
    demo20_limitedParallelism()

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations")
    println("=".repeat(80))
}
