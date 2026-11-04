// ==============================================================================
// FICHE FORMATEUR — Annulation des coroutines (fichier OPTIONNEL)
// Objectif : l'annulation est coopérative ; on ne doit jamais avaler CancellationException.
// À présenter (dans l'ordre) :
//   1. demo01 + demo07 : cancel()/join() ; annuler le parent annule tous les enfants
//   2. demo09 : boucle CPU sur Dispatchers.Default arrêtée grâce à isActive
//   3. demo15 : catch (e: Exception) qui avale l'annulation -> « toujours relancer »
//   4. demo18 : withTimeoutOrNull comme fallback réseau
//   5. demo22 : finally + withContext(NonCancellable) pour un nettoyage suspendu
// Lien Android : viewModelScope annulé dans onCleared() (TP07) ; LaunchedEffect(key)
//   annulé quand la clé change ou qu'il quitte la composition ; CoroutineWorker arrêté
//   par WorkManager (TP12) ; repository du TP04 qui relance CancellationException.
// Pièges / questions fréquentes : Thread.sleep ou boucle CPU sans point de suspension
//   = non annulable ; runCatching et catch (e: Exception) avalent CancellationException ;
//   appel suspend dans finally sans NonCancellable.
// Si peu de temps : demo07, demo15, demo18, demo22 (~10 min). Sauter demo02, demo08,
//   demo10, demo13 (bloquées par runBlocking, voir ATTENTION), demo04-06, demo11-12,
//   demo19-21, demo23-26 et le résumé.
// ==============================================================================
/**
 * 04-Cancellation.kt
 *
 * Exemples sur l'annulation de coroutines
 * - Annulation coopérative
 * - job.cancel()
 * - isActive
 * - yield()
 * - ensureActive()
 * - CancellationException
 * - withTimeout et withTimeoutOrNull
 * - Nettoyage des ressources
 */

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// =============================================================================
// 1. ANNULATION COOPÉRATIVE
// =============================================================================

/**
 * Démonstration de l'annulation basique
 */
fun demo01_basicCancellation() = runBlocking {
    println("\n=== Demo 01: Annulation basique ===")
    // [À DIRE] cancel() ne tue pas la coroutine : il la marque annulée, et c'est le
    // prochain point de suspension (ici delay) qui lève CancellationException.
    // [PIÈGE] le catch ne relance pas : sans effet ici (fin du launch), mais c'est le
    // contre-exemple de demo15. Le faire remarquer.

    val job = launch {
        try {
            repeat(10) { i ->
                println("Itération $i")
                delay(500)
            }
        } catch (e: CancellationException) {
            println("Coroutine annulée!")
        }
    }

    delay(1500)
    println("Annulation de la coroutine...")
    job.cancel()
    job.join()
    println("Coroutine terminée")
}

/**
 * Problème: boucle non-coopérative (ne répond pas à l'annulation)
 */
fun demo02_nonCooperativeCancellation() = runBlocking {
    println("\n=== Demo 02: Boucle non-coopérative (PROBLÈME) ===")
    // [ATTENTION FORMATEUR] sous runBlocking (un seul thread), ce launch ne suspend jamais :
    // le parent ne reprend la main qu'après la boucle, donc cancel() arrive trop tard. Le
    // message final est vrai, mais pas pour la raison annoncée. Démo honnête : launch(Dispatchers.Default).
    // [SAUTER] le dire à l'oral suffit : « Thread.sleep n'est pas un point de suspension ».

    val job = launch {
        var i = 0
        val startTime = System.currentTimeMillis()

        // Boucle qui ne vérifie pas l'annulation
        while (i < 5) {
            val elapsedTime = System.currentTimeMillis() - startTime
            if (elapsedTime >= 1000) {
                println("Itération $i")
                i++
                Thread.sleep(500) // PROBLÈME: Thread.sleep ne répond pas à cancel()
            }
        }
    }

    delay(1500)
    println("Tentative d'annulation...")
    job.cancel()
    job.join()
    println("La coroutine a continué malgré cancel()!")
}

/**
 * Solution: rendre la boucle coopérative
 */
fun demo03_cooperativeCancellation() = runBlocking {
    println("\n=== Demo 03: Boucle coopérative (SOLUTION) ===")
    // [À DIRE] c'est delay() qui rend cette boucle annulable ; le test isActive sert
    // seulement à sortir proprement, sans exception.

    val job = launch {
        repeat(10) { i ->
            if (!isActive) {
                println("Détection d'annulation, arrêt propre")
                return@launch
            }
            println("Itération $i")
            delay(500) // delay est coopératif
        }
    }

    delay(1500)
    println("Annulation de la coroutine...")
    job.cancel()
    job.join()
    println("Coroutine annulée proprement")
}

// =============================================================================
// 2. JOB.CANCEL()
// =============================================================================

/**
 * Différentes façons d'annuler
 */
fun demo04_jobCancel() = runBlocking {
    println("\n=== Demo 04: job.cancel() ===")
    // [SAUTER] demo04 à demo06 : variantes de cancel()/join() ; citer cancelAndJoin() à l'oral.

    val job = launch {
        try {
            repeat(10) { i ->
                println("Travail: $i")
                delay(300)
            }
        } catch (e: CancellationException) {
            println("Annulé avec raison: ${e.message}")
        }
    }

    delay(1000)
    job.cancel(CancellationException("Annulation demandée par l'utilisateur"))
    job.join()
}

/**
 * cancelAndJoin() - combinaison de cancel() et join()
 */
fun demo05_cancelAndJoin() = runBlocking {
    println("\n=== Demo 05: cancelAndJoin() ===")

    val job = launch {
        try {
            repeat(10) { i ->
                println("Étape $i")
                delay(300)
            }
        } catch (e: CancellationException) {
            println("Nettoyage en cours...")
            // [ATTENTION FORMATEUR] la coroutine est déjà annulée : ce delay relance aussitôt
            // CancellationException, « Nettoyage terminé » ne s'affiche jamais. Il faudrait
            // withContext(NonCancellable) { delay(100) } (cf. demo22).
            delay(100)
            println("Nettoyage terminé")
        }
    }

    delay(1000)
    println("Annulation et attente...")
    job.cancelAndJoin() // Équivalent à: job.cancel(); job.join()
    println("Tout est nettoyé")
}

/**
 * Annuler plusieurs coroutines
 */
fun demo06_cancelMultiple() = runBlocking {
    println("\n=== Demo 06: Annuler plusieurs coroutines ===")

    val jobs = List(5) { i ->
        launch {
            try {
                repeat(10) {
                    println("Job $i: étape $it")
                    delay(300)
                }
            } catch (e: CancellationException) {
                println("Job $i annulé")
            }
        }
    }

    delay(1000)
    println("Annulation de tous les jobs...")
    jobs.forEach { it.cancel() }
    jobs.forEach { it.join() }
    println("Tous les jobs annulés")
}

/**
 * Annulation du parent annule les enfants
 */
fun demo07_parentCancellation() = runBlocking {
    println("\n=== Demo 07: Annulation du parent annule les enfants ===")
    // [ANDROID] c'est le modèle de viewModelScope : onCleared() annule le scope et toutes
    // les coroutines du ViewModel s'arrêtent (TP07). Idem lifecycleScope à ON_DESTROY.

    val parentJob = launch {
        val child1 = launch {
            try {
                repeat(10) {
                    println("  Enfant 1: $it")
                    delay(300)
                }
            } catch (e: CancellationException) {
                println("  Enfant 1 annulé")
            }
        }

        val child2 = launch {
            try {
                repeat(10) {
                    println("  Enfant 2: $it")
                    delay(300)
                }
            } catch (e: CancellationException) {
                println("  Enfant 2 annulé")
            }
        }

        delay(5000)
    }

    delay(1000)
    println("Annulation du parent...")
    parentJob.cancel()
    parentJob.join()
    println("Parent et enfants annulés")
}

// =============================================================================
// 3. ISACTIVE
// =============================================================================

/**
 * Vérification avec isActive
 */
fun demo08_isActive() = runBlocking {
    println("\n=== Demo 08: isActive ===")
    // [ATTENTION FORMATEUR] boucle infinie : Thread.sleep occupe le thread de runBlocking,
    // le parent ne reprend jamais la main pour appeler cancel(), isActive reste true et le
    // programme ne se termine pas. Ne pas exécuter tel quel (launch(Dispatchers.Default) corrige).
    // [SAUTER] demo09 montre la même idée et fonctionne.

    val job = launch {
        var counter = 0
        while (isActive) {
            counter++
            println("Compteur: $counter")
            Thread.sleep(300) // Pas coopératif, mais on vérifie isActive
        }
        println("Boucle terminée, isActive=false")
    }

    delay(1500)
    job.cancel()
    job.join()
    println("Job annulé")
}

/**
 * Boucle infinie avec isActive
 */
fun demo09_infiniteLoopWithIsActive() = runBlocking {
    println("\n=== Demo 09: Boucle infinie avec isActive ===")
    // [À DIRE] Dispatchers.Default = un autre thread, donc le parent peut annuler ; sans le
    // test isActive, cette boucle ne s'arrêterait jamais malgré cancel().
    // [ANDROID] calcul lourd dans withContext(Dispatchers.Default) avec ensureActive() dans la
    // boucle (traitement d'image, gros parsing) : il s'arrête quand l'écran est quitté.

    val job = launch(Dispatchers.Default) {
        var sum = 0L
        var i = 0L

        while (isActive) {
            sum += i++
            if (i % 100_000_000L == 0L) {
                println("Calcul en cours: i=$i, sum=$sum")
            }
        }

        println("Arrêt du calcul à i=$i")
    }

    delay(2000)
    println("Annulation du calcul...")
    job.cancelAndJoin()
}

/**
 * isActive vs try-catch
 */
fun demo10_isActiveVsTryCatch() = runBlocking {
    println("\n=== Demo 10: isActive vs try-catch ===")

    // [ATTENTION FORMATEUR] job1 a le même défaut que demo08 (Thread.sleep sur le thread de
    // runBlocking) : le programme reste bloqué ici.
    // [SAUTER]
    // Avec isActive (recommandé pour les boucles)
    println("Version isActive:")
    val job1 = launch {
        while (isActive) {
            println("  Travail...")
            Thread.sleep(300)
        }
        println("  Arrêt propre avec isActive")
    }
    delay(1000)
    job1.cancelAndJoin()

    // Avec try-catch (recommandé pour les fonctions suspend)
    println("\nVersion try-catch:")
    val job2 = launch {
        try {
            repeat(10) {
                println("  Travail...")
                delay(300)
            }
        } catch (e: CancellationException) {
            println("  Arrêt propre avec CancellationException")
            throw e // Important: relancer l'exception
        }
    }
    delay(1000)
    job2.cancelAndJoin()
}

// =============================================================================
// 4. YIELD()
// =============================================================================

/**
 * yield() pour rendre le code coopératif
 */
fun demo11_yield() = runBlocking {
    println("\n=== Demo 11: yield() ===")
    // [SAUTER] demo11-12 : yield() est rare en code Android ; ensureActive() est plus explicite
    // pour rendre un calcul annulable.

    val job = launch {
        try {
            repeat(10) { i ->
                println("Étape $i")
                // Pas de delay, mais yield() rend la coroutine coopérative
                yield()

                // Simulation de travail CPU
                repeat(100_000_000) { /* calcul */ }
            }
        } catch (e: CancellationException) {
            println("Annulé grâce à yield()")
        }
    }

    delay(100)
    job.cancel()
    job.join()
}

/**
 * yield() permet à d'autres coroutines de s'exécuter
 */
fun demo12_yieldForFairness() = runBlocking {
    println("\n=== Demo 12: yield() pour l'équité ===")

    launch {
        repeat(5) { i ->
            println("Coroutine A: $i")
            yield() // Donne la main aux autres
        }
    }

    launch {
        repeat(5) { i ->
            println("Coroutine B: $i")
            yield() // Donne la main aux autres
        }
    }

    delay(1000)
}

/**
 * ensureActive() - alternative à yield()
 */
fun demo13_ensureActive() = runBlocking {
    println("\n=== Demo 13: ensureActive() ===")
    // [ATTENTION FORMATEUR] même défaut que demo08 : avec Thread.sleep sur le thread de
    // runBlocking, cancel() n'est appelé qu'après les 10 itérations ; ensureActive() ne se
    // déclenche jamais. Pour le montrer vraiment : launch(Dispatchers.Default).
    // [ANDROID] ensureActive() dans une boucle de CoroutineWorker.doWork() : WorkManager annule
    // le worker si ses contraintes ne sont plus remplies (TP12).

    val job = launch {
        try {
            repeat(10) { i ->
                ensureActive() // Lance CancellationException si annulé
                println("Étape $i")
                Thread.sleep(300)
            }
        } catch (e: CancellationException) {
            println("Annulé via ensureActive()")
            throw e
        }
    }

    delay(1000)
    job.cancel()
    job.join()
}

// =============================================================================
// 5. CANCELLATIONEXCEPTION
// =============================================================================

/**
 * Capturer CancellationException
 */
fun demo14_cancellationException() = runBlocking {
    println("\n=== Demo 14: CancellationException ===")
    // [À DIRE] le finally s'exécute toujours ; la cause passée à cancel() se lit dans e.message.

    val job = launch {
        try {
            println("Début du travail")
            delay(2000)
            println("Travail terminé")
        } catch (e: CancellationException) {
            println("CancellationException capturée: ${e.message}")
            println("Nettoyage...")
            // Important: relancer l'exception
            throw e
        } finally {
            println("Bloc finally exécuté")
        }
    }

    delay(1000)
    job.cancel(CancellationException("Timeout atteint"))
    job.join()
}

/**
 * Ne pas avaler CancellationException
 */
fun demo15_dontSwallowCancellation() = runBlocking {
    println("\n=== Demo 15: Ne PAS avaler CancellationException ===")

    // [À DIRE] message clé du fichier : catch (e: Exception) attrape aussi CancellationException.
    // On peut la capturer pour nettoyer, mais on la relance.
    // [ANDROID] même piège avec runCatching { api.getTodos() } dans un repository. Au TP04 :
    // catch (e: CancellationException) { throw e } avant de traduire les autres erreurs.
    // MAUVAIS: avale l'exception
    println("MAUVAIS exemple:")
    val job1 = launch {
        try {
            delay(2000)
        } catch (e: Exception) { // Trop large, capture aussi CancellationException
            println("  Exception capturée (MAUVAIS)")
            // [ATTENTION FORMATEUR] ici le dégât est invisible (le bloc se termine juste après) ;
            // le vrai danger est le code qui continue après le catch : boucle, mise à jour
            // d'état alors que l'écran est fermé.
            // N'est pas relancée - la coroutine ne se termine pas correctement
        }
    }
    delay(500)
    job1.cancel()
    job1.join()

    // BON: gère spécifiquement CancellationException
    println("\nBON exemple:")
    val job2 = launch {
        try {
            delay(2000)
        } catch (e: CancellationException) {
            println("  Annulation détectée (BON)")
            throw e // Relance l'exception
        } catch (e: Exception) {
            println("  Autre exception: ${e.message}")
        }
    }
    delay(500)
    job2.cancel()
    job2.join()
}

/**
 * Nettoyage dans finally
 */
fun demo16_cleanupInFinally() = runBlocking {
    println("\n=== Demo 16: Nettoyage dans finally ===")
    // [À DIRE] finally convient au nettoyage non suspendu (close(), remettre un indicateur
    // isLoading à false) ; un appel suspend y lèverait CancellationException aussitôt.

    val job = launch {
        try {
            println("Ouverture de ressources...")
            val resource = "FILE_HANDLE"
            println("Ressource ouverte: $resource")

            delay(2000)

            println("Fermeture de la ressource")
        } finally {
            println("Bloc finally: nettoyage garanti")
            // Attention: les fonctions suspend ne fonctionnent pas ici
            // pendant l'annulation
        }
    }

    delay(1000)
    job.cancel()
    job.join()
}

// =============================================================================
// 6. WITHTIMEOUT ET WITHTIMEOUTORNULL
// =============================================================================

/**
 * withTimeout lance une exception
 */
fun demo17_withTimeout() = runBlocking {
    println("\n=== Demo 17: withTimeout ===")
    // [PIÈGE] TimeoutCancellationException hérite de CancellationException : un withTimeout
    // non rattrapé dans un launch ressemble à une annulation silencieuse, pas à un crash.

    try {
        withTimeout(1500) {
            repeat(5) { i ->
                println("Étape $i")
                delay(500)
            }
        }
    } catch (e: TimeoutCancellationException) {
        println("Timeout! ${e.message}")
    }
}

/**
 * withTimeoutOrNull retourne null
 */
fun demo18_withTimeoutOrNull() = runBlocking {
    println("\n=== Demo 18: withTimeoutOrNull ===")
    // [ANDROID] withTimeoutOrNull(5_000) { api.fetch() } ?: cache : fallback simple dans un
    // repository. Avec Retrofit, les timeouts OkHttp (connectTimeout, readTimeout) restent la base.

    val result = withTimeoutOrNull(1500) {
        repeat(5) { i ->
            println("Étape $i")
            delay(500)
        }
        "Succès"
    }

    if (result == null) {
        println("Timeout atteint, résultat: null")
    } else {
        println("Résultat: $result")
    }
}

/**
 * Comparaison withTimeout vs withTimeoutOrNull
 */
suspend fun longRunningTask(): String {
    delay(2000)
    return "Résultat"
}

fun demo19_timeoutComparison() = runBlocking {
    println("\n=== Demo 19: withTimeout vs withTimeoutOrNull ===")
    // [SAUTER] reprend demo17 + demo18.

    // withTimeout: lance une exception
    println("Avec withTimeout:")
    try {
        val result = withTimeout(1000) {
            longRunningTask()
        }
        println("  Résultat: $result")
    } catch (e: TimeoutCancellationException) {
        println("  Exception: ${e.message}")
    }

    // withTimeoutOrNull: retourne null
    println("\nAvec withTimeoutOrNull:")
    val result = withTimeoutOrNull(1000) {
        longRunningTask()
    }
    println("  Résultat: ${result ?: "null (timeout)"}")
}

/**
 * Timeout sur plusieurs opérations
 */
suspend fun fetchData(id: Int): String {
    delay(500)
    return "Data-$id"
}

fun demo20_timeoutMultipleOperations() = runBlocking {
    println("\n=== Demo 20: Timeout sur plusieurs opérations ===")
    // [SAUTER] à retenir seulement : le timeout couvre tout le bloc, pas chaque appel ;
    // et ces appels sont séquentiels (async les paralléliserait, cf. 06-TP).

    val result = withTimeoutOrNull(2000) {
        val data1 = fetchData(1)
        val data2 = fetchData(2)
        val data3 = fetchData(3)
        val data4 = fetchData(4)
        val data5 = fetchData(5) // Total: 2500ms, dépassera le timeout

        listOf(data1, data2, data3, data4, data5)
    }

    if (result == null) {
        println("Timeout: toutes les opérations annulées")
    } else {
        println("Résultats: $result")
    }
}

// =============================================================================
// 7. NETTOYAGE DES RESSOURCES
// =============================================================================

/**
 * Nettoyage avec use (AutoCloseable)
 */
class Resource(val name: String) : AutoCloseable {
    init {
        println("  Ouverture de $name")
    }

    fun doWork() {
        println("  $name travaille...")
    }

    override fun close() {
        println("  Fermeture de $name")
    }
}

fun demo21_cleanupWithUse() = runBlocking {
    println("\n=== Demo 21: Nettoyage avec use() ===")
    // [SAUTER] use() est du Kotlin standard : il ferme la ressource aussi en cas d'annulation.

    val job = launch {
        try {
            Resource("MyResource").use { resource ->
                repeat(5) { i ->
                    resource.doWork()
                    delay(500)
                }
            }
        } catch (e: CancellationException) {
            println("  Annulé (use() garantit close())")
            throw e
        }
    }

    delay(1500)
    job.cancel()
    job.join()
}

/**
 * NonCancellable pour opérations critiques
 */
fun demo22_nonCancellable() = runBlocking {
    println("\n=== Demo 22: NonCancellable ===")
    // [À DIRE] seul usage légitime de NonCancellable : un nettoyage suspendu court dans finally.
    // Pas pour lancer du travail « qui ne doit pas être annulé ».
    // [ANDROID] ex. : écrire un état final dans Room ou DataStore dans finally. Un travail qui
    // doit survivre à l'écran relève de WorkManager (TP12), pas de NonCancellable.

    val job = launch {
        try {
            println("Travail en cours...")
            delay(2000)
        } finally {
            // Ce bloc peut utiliser des fonctions suspend même après annulation
            withContext(NonCancellable) {
                println("Sauvegarde critique en cours...")
                delay(500) // Fonctionne même si annulé
                println("Sauvegarde terminée")
            }
        }
    }

    delay(1000)
    println("Annulation...")
    job.cancel()
    job.join()
    println("Nettoyage critique effectué")
}

/**
 * Timeout avec nettoyage
 */
fun demo23_timeoutWithCleanup() = runBlocking {
    println("\n=== Demo 23: Timeout avec nettoyage ===")
    // [SAUTER] combinaison de demo21 et demo22.

    try {
        withTimeout(1500) {
            try {
                println("Début opération longue...")
                Resource("DBConnection").use { db ->
                    repeat(10) { i ->
                        db.doWork()
                        delay(500)
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    println("Nettoyage garanti")
                    delay(200)
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        println("Timeout, mais nettoyage effectué")
    }
}

// =============================================================================
// 8. PATTERNS D'ANNULATION
// =============================================================================

/**
 * Pattern: annulation manuelle avec bouton
 */
fun demo24_manualCancellation() = runBlocking {
    println("\n=== Demo 24: Annulation manuelle (simulation UI) ===")
    // [ANDROID] pattern « bouton Annuler » ou recherche : garder le Job dans le ViewModel,
    // job?.cancel() puis job = viewModelScope.launch { ... }. Le catch ne relance pas : à corriger à l'oral.

    var job: Job? = null

    // Simulation bouton "Start"
    fun startWork() {
        job = launch {
            try {
                repeat(10) { i ->
                    println("  Travail en cours: $i")
                    delay(500)
                }
                println("  Travail terminé")
            } catch (e: CancellationException) {
                println("  Travail annulé par l'utilisateur")
            }
        }
    }

    // Simulation bouton "Cancel"
    fun cancelWork() {
        job?.cancel()
    }

    startWork()
    delay(2000)
    println("Utilisateur clique sur Cancel")
    cancelWork()
    job?.join()
}

/**
 * Pattern: retry avec timeout
 */
suspend fun unreliableOperation(attempt: Int): String {
    delay(500)
    if (attempt < 3) {
        throw RuntimeException("Échec tentative $attempt")
    }
    return "Succès"
}

suspend fun retryWithTimeout(maxAttempts: Int = 5): String? {
    repeat(maxAttempts) { attempt ->
        try {
            return withTimeout(1000) {
                println("  Tentative ${attempt + 1}")
                unreliableOperation(attempt + 1)
            }
        } catch (e: TimeoutCancellationException) {
            println("  Timeout tentative ${attempt + 1}")
        // [ATTENTION FORMATEUR] ce catch (e: Exception) attrape aussi l'annulation du parent.
        // Le delay() suivant la relance, sauf à la dernière tentative : la fonction renvoie
        // alors null au lieu de propager l'annulation. Contredit la règle de demo15.
        } catch (e: Exception) {
            println("  Erreur tentative ${attempt + 1}: ${e.message}")
        }

        if (attempt < maxAttempts - 1) {
            delay(500) // Attendre avant de réessayer
        }
    }
    return null
}

fun demo25_retryPattern() = runBlocking {
    println("\n=== Demo 25: Pattern retry avec timeout ===")
    // [SAUTER] demo25-26 : patterns avancés. Pour un Flow, retry / retryWhen existent déjà.

    val result = retryWithTimeout()
    println("Résultat final: ${result ?: "Échec après toutes les tentatives"}")
}

/**
 * Pattern: timeout progressif
 */
fun demo26_progressiveTimeout() = runBlocking {
    println("\n=== Demo 26: Timeout progressif ===")

    val tasks = listOf(
        "Rapide" to 500L,
        "Moyen" to 1500L,
        "Lent" to 3000L
    )

    val results = tasks.map { (name, duration) ->
        async {
            val result = withTimeoutOrNull(2000) {
                println("  $name commence")
                delay(duration)
                name
            }
            name to (result != null)
        }
    }.awaitAll()

    println("\nRésultats:")
    results.forEach { (name, success) ->
        println("  $name: ${if (success) "✓" else "✗ timeout"}")
    }
}

// =============================================================================
// RÉSUMÉ ET BONNES PRATIQUES
// =============================================================================

// [SAUTER] résumé à projeter ou distribuer plutôt qu'à lire ; les « RÈGLES D'OR » suffisent.
fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Annulation de Coroutines")
    println("=".repeat(80))

    println("""
    1. ANNULATION COOPÉRATIVE:
    - Les coroutines ne peuvent être annulées que si elles coopèrent
    - Fonctions suspend des coroutines sont coopératives (delay, yield, etc.)
    - Code CPU-intensif doit vérifier isActive ou appeler yield()

    2. MÉTHODES D'ANNULATION:

    job.cancel():
    - Demande l'annulation de la coroutine
    - La coroutine doit coopérer pour s'arrêter
    - Peut passer un CancellationException personnalisé

    job.join():
    - Attend la fin de la coroutine (même si annulée)

    job.cancelAndJoin():
    - Combine cancel() et join()
    - Équivalent à: job.cancel(); job.join()

    3. VÉRIFIER L'ANNULATION:

    isActive:
    - Propriété booléenne du CoroutineScope
    - false quand la coroutine est annulée
    - Utiliser dans les boucles

    yield():
    - Suspend la coroutine et vérifie l'annulation
    - Lance CancellationException si annulée
    - Donne la main aux autres coroutines

    ensureActive():
    - Lance CancellationException immédiatement si annulée
    - Plus direct que isActive

    4. CANCELLATIONEXCEPTION:
    - Exception spéciale pour l'annulation
    - TOUJOURS la relancer après l'avoir capturée!
    - Ne pas utiliser catch (e: Exception) qui l'avalerait

    Bon pattern:
        try {
            // code
        } catch (e: CancellationException) {
            // nettoyage
            throw e // IMPORTANT!
        } finally {
            // nettoyage garanti
        }

    5. TIMEOUT:

    withTimeout(millis):
    - Lance TimeoutCancellationException si dépassé
    - Annule automatiquement la coroutine

    withTimeoutOrNull(millis):
    - Retourne null si timeout dépassé
    - Plus simple si null est une valeur acceptable
    - Pas besoin de try-catch

    6. NETTOYAGE:

    finally:
    - Toujours exécuté, même en cas d'annulation
    - Les suspend functions peuvent échouer si annulé

    NonCancellable:
    - Context spécial pour opérations critiques
    - Les suspend functions fonctionnent même après annulation
        withContext(NonCancellable) {
            // sauvegarde critique
        }

    use():
    - Pour AutoCloseable
    - Garantit close() même en cas d'annulation

    7. PATTERNS COURANTS:

    Boucle avec isActive:
        while (isActive) {
            // travail
        }

    Calcul CPU avec yield:
        repeat(n) {
            yield() // ou ensureActive()
            // calcul intensif
        }

    Timeout avec fallback:
        val result = withTimeoutOrNull(1000) {
            longOperation()
        } ?: defaultValue

    Nettoyage garanti:
        try {
            // travail
        } finally {
            withContext(NonCancellable) {
                // nettoyage critique
            }
        }

    BONNES PRATIQUES:
    ✓ Toujours rendre le code coopératif (isActive, yield, ensureActive)
    ✓ Utiliser delay() au lieu de Thread.sleep()
    ✓ Relancer CancellationException après capture
    ✓ Utiliser finally pour le nettoyage
    ✓ Utiliser withTimeoutOrNull pour simplicité
    ✓ NonCancellable pour opérations critiques en finally
    ✓ use() pour AutoCloseable

    ANTI-PATTERNS:
    ✗ Thread.sleep() dans une coroutine (non coopératif)
    ✗ Boucle infinie sans isActive/yield
    ✗ Avaler CancellationException (catch Exception)
    ✗ Suspend functions dans finally sans NonCancellable
    ✗ Ignorer l'état d'annulation
    ✗ Oublier de relancer CancellationException

    RÈGLES D'OR:
    - "Une coroutine annulée doit coopérer pour s'arrêter"
    - "Toujours relancer CancellationException"
    - "Nettoyage critique dans finally avec NonCancellable"
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

// [ATTENTION FORMATEUR] exécuté en entier, main() reste bloqué dans demo08 (boucle infinie).
// Lancer les démos une par une en commentant les autres appels.
fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(22) + "KOTLIN COROUTINES - CANCELLATION")
    println("=" + "=".repeat(78) + "=")

    // 1. Annulation coopérative
    demo01_basicCancellation()
    demo02_nonCooperativeCancellation()
    demo03_cooperativeCancellation()

    // 2. job.cancel()
    demo04_jobCancel()
    demo05_cancelAndJoin()
    demo06_cancelMultiple()
    demo07_parentCancellation()

    // 3. isActive
    demo08_isActive()
    demo09_infiniteLoopWithIsActive()
    demo10_isActiveVsTryCatch()

    // 4. yield()
    demo11_yield()
    demo12_yieldForFairness()
    demo13_ensureActive()

    // 5. CancellationException
    demo14_cancellationException()
    demo15_dontSwallowCancellation()
    demo16_cleanupInFinally()

    // 6. withTimeout et withTimeoutOrNull
    demo17_withTimeout()
    demo18_withTimeoutOrNull()
    demo19_timeoutComparison()
    demo20_timeoutMultipleOperations()

    // 7. Nettoyage des ressources
    demo21_cleanupWithUse()
    demo22_nonCancellable()
    demo23_timeoutWithCleanup()

    // 8. Patterns
    demo24_manualCancellation()
    demo25_retryPattern()
    demo26_progressiveTimeout()

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations")
    println("=".repeat(80))
}
