// ==============================================================================
// FICHE FORMATEUR — Coroutines : les bases
// Objectif : comprendre suspendre vs bloquer, et savoir lancer une coroutine (launch / async).
// À présenter (dans l'ordre) :
//   1. Thread.sleep vs delay (demo01 à 03) : suspendre libère le thread
//   2. suspend fun, appel séquentiel lisible comme du code synchrone (demo04)
//   3. launch → Job (effet de bord), async → Deferred<T> + await (demo06, demo07)
//   4. Parallélisme avec async : ~1 s au lieu de ~3 s (demo09)
//   5. runBlocking = uniquement main() et vieux tests, jamais sur le thread UI (demo10)
//   6. Job : join / cancel / parent qui attend ses enfants (demo13, demo18)
// Lien Android : viewModelScope.launch (J3, TP07), suspend fun Retrofit (TP04), async en parallèle (bonus TP04)
// Pièges / questions fréquentes : Thread.sleep ou runBlocking sur le thread UI → ANR ;
//   async { }.await() immédiat = séquentiel ; « suspend = nouveau thread ? » → non
// Si peu de temps : demo02, 03, 04, 06, 07, 09, 10, 18. Sauter 05, 08, 11, 12, 14, 16, 17.
//   Exécution complète du main ≈ 35 s : lancer seulement les démos choisies.
// ==============================================================================
/**
 * 01-Basics.kt
 *
 * Exemples de base sur les coroutines Kotlin
 * - Bloquer vs suspendre
 * - Fonctions suspend
 * - launch vs async
 * - runBlocking
 * - delay
 * - Job et Deferred
 */

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// =============================================================================
// 1. BLOQUER VS SUSPENDRE
// =============================================================================

/**
 * Démonstration de Thread.sleep (bloque le thread)
 */
// [À DIRE] Thread.sleep immobilise le thread : il ne peut rien faire d'autre pendant 1 s.
// [ANDROID] Sur le thread principal, plus de dessin ni de clics : UI gelée, puis ANR au bout d'environ 5 s.
fun demo01_threadSleep() {
    println("\n=== Demo 01: Thread.sleep (BLOQUANT) ===")
    println("Début - Thread: ${Thread.currentThread().name}")

    println("Je vais bloquer le thread pendant 1 seconde...")
    Thread.sleep(1000) // BLOQUE le thread entier

    println("Terminé - Le thread était bloqué")
}

/**
 * Démonstration de delay (suspend, ne bloque pas le thread)
 */
// [À DIRE] delay met la coroutine de côté : le thread reste libre et peut exécuter d'autres coroutines.
// [PIÈGE] Thread.sleep dans une coroutine : ça compile mais ça bloque le thread. Toujours delay.
fun demo02_delay() = runBlocking {
    println("\n=== Demo 02: delay (SUSPEND) ===")
    println("Début - Thread: ${Thread.currentThread().name}")

    println("Je vais suspendre pendant 1 seconde...")
    delay(1000) // SUSPEND la coroutine, ne bloque pas le thread

    println("Terminé - La coroutine était suspendue, pas le thread")
}

/**
 * Comparaison directe: plusieurs tâches avec Thread.sleep vs delay
 */
fun demo03_blockingVsSuspending() {
    println("\n=== Demo 03: Bloquer vs Suspendre - Comparaison ===")

    // Version bloquante
    val timeBlocking = measureTimeMillis {
        println("Version BLOQUANTE avec Thread.sleep:")
        repeat(3) { i ->
            println("  Tâche $i commence")
            Thread.sleep(500)
            println("  Tâche $i terminée")
        }
    }
    println("Temps total bloquant: ${timeBlocking}ms")

    // [À DIRE] Comparer ~1500 ms et ~500 ms : les 3 tâches tournent sur UN seul thread (main).
    // Concurrence n'est pas parallélisme : on n'a pas créé de threads.
    // Version suspendante
    val timeSuspending = measureTimeMillis {
        runBlocking {
            println("\nVersion SUSPENDANTE avec delay:")
            repeat(3) { i ->
                launch {
                    println("  Tâche $i commence")
                    delay(500)
                    println("  Tâche $i terminée")
                }
            }
        }
    }
    println("Temps total suspendant: ${timeSuspending}ms")
}

// =============================================================================
// 2. FONCTIONS SUSPEND
// =============================================================================

/**
 * Fonction suspend simple
 */
// [À DIRE] suspend = « peut se mettre en pause ». On ne peut l'appeler que depuis une coroutine ou une autre suspend fun.
// [ANDROID] TP04 : dans l'interface Retrofit, `suspend fun getTodos(): List<Todo>`. Retrofit est déjà main-safe.
suspend fun fetchUserData(userId: Int): String {
    println("Récupération des données utilisateur $userId...")
    delay(1000) // Simulation d'un appel réseau
    return "User-$userId"
}

/**
 * Fonction suspend qui appelle d'autres fonctions suspend
 */
suspend fun fetchUserProfile(userId: Int): Map<String, Any> {
    println("Récupération du profil complet...")
    val userName = fetchUserData(userId)
    delay(500) // Autre appel réseau
    return mapOf(
        "id" to userId,
        "name" to userName,
        "email" to "$userName@example.com"
    )
}

/**
 * Démonstration des fonctions suspend
 */
// [À DIRE] Par défaut, c'est séquentiel (~2500 ms) : le code se lit de haut en bas, sans callback.
fun demo04_suspendFunctions() = runBlocking {
    println("\n=== Demo 04: Fonctions suspend ===")

    val time = measureTimeMillis {
        val userData = fetchUserData(42)
        println("Données reçues: $userData")

        val userProfile = fetchUserProfile(42)
        println("Profil reçu: $userProfile")
    }
    println("Temps total: ${time}ms (séquentiel)")
}

/**
 * Suspension conditionnelle
 */
// [SAUTER] Suspension conditionnelle : n'apporte rien de nouveau.
suspend fun processData(data: String, shouldDelay: Boolean = true): String {
    if (shouldDelay) {
        delay(500)
    }
    return data.uppercase()
}

fun demo05_conditionalSuspension() = runBlocking {
    println("\n=== Demo 05: Suspension conditionnelle ===")

    println("Avec délai:")
    val result1 = processData("hello", shouldDelay = true)
    println("Résultat: $result1")

    println("Sans délai:")
    val result2 = processData("world", shouldDelay = false)
    println("Résultat: $result2")
}

// =============================================================================
// 3. LAUNCH VS ASYNC
// =============================================================================

/**
 * Démonstration de launch (fire-and-forget)
 */
// [À DIRE] launch lance une coroutine sans attendre de résultat et renvoie un Job. On ne peut le lancer que dans un scope.
// [ANDROID] Dans un ViewModel, c'est `viewModelScope.launch { ... }` (J3, TP07). Dans Compose : LaunchedEffect / rememberCoroutineScope().
fun demo06_launch() = runBlocking {
    println("\n=== Demo 06: launch (fire-and-forget) ===")

    println("Lancement de plusieurs coroutines...")
    launch {
        delay(1000)
        println("Coroutine 1 terminée")
    }

    launch {
        delay(500)
        println("Coroutine 2 terminée")
    }

    launch {
        delay(1500)
        println("Coroutine 3 terminée")
    }

    println("Toutes les coroutines sont lancées")
    // [À DIRE] Ce delay n'est pas nécessaire : runBlocking attend ses enfants (structured concurrency, fichier 02).
    delay(2000) // Attendre que toutes se terminent
}

/**
 * Démonstration de async (retourne un résultat)
 */
// [À DIRE] async renvoie un Deferred<T>, et await() suspend jusqu'au résultat.
// Les deux calculs démarrent tout de suite : le total est ~1000 ms, pas 1500.
fun demo07_async() = runBlocking {
    println("\n=== Demo 07: async (retourne un résultat) ===")

    println("Lancement de calculs asynchrones...")
    val deferred1 = async {
        delay(1000)
        println("Calcul 1 en cours...")
        42
    }

    val deferred2 = async {
        delay(500)
        println("Calcul 2 en cours...")
        "Résultat"
    }

    println("En attente des résultats...")
    val result1 = deferred1.await()
    val result2 = deferred2.await()

    println("Résultats: $result1, $result2")
}

/**
 * Comparaison launch vs async
 */
// [SAUTER] Redit demo06 et demo07 : se contenter de la règle « launch = action, async = valeur ».
fun demo08_launchVsAsync() = runBlocking {
    println("\n=== Demo 08: launch vs async - Comparaison ===")

    // launch: pour les effets de bord (pas de retour)
    println("launch: pour les effets de bord")
    val job = launch {
        delay(1000)
        println("  Envoi d'un email...")
    }
    job.join() // Attendre la fin

    // async: pour récupérer un résultat
    println("\nasync: pour récupérer un résultat")
    val deferred = async {
        delay(1000)
        println("  Calcul en cours...")
        42 + 58
    }
    val result = deferred.await()
    println("  Résultat calculé: $result")
}

/**
 * Exécution parallèle avec async
 */
suspend fun downloadFile(name: String): String {
    delay(1000)
    return "Content of $name"
}

// [ANDROID] C'est le bonus du TP04 : deux appels Retrofit en parallèle avec async, dans un coroutineScope { } (fichier 02).
// [PIÈGE] `async { ... }.await()` écrit d'une traite redevient séquentiel : lancer tous les async d'abord, puis await.
fun demo09_parallelAsync() = runBlocking {
    println("\n=== Demo 09: Exécution parallèle avec async ===")

    val time = measureTimeMillis {
        // Exécution parallèle
        val file1 = async { downloadFile("file1.txt") }
        val file2 = async { downloadFile("file2.txt") }
        val file3 = async { downloadFile("file3.txt") }

        // Récupération des résultats
        val results = listOf(file1.await(), file2.await(), file3.await())
        println("Fichiers téléchargés: ${results.size}")
    }
    println("Temps total: ${time}ms (parallèle, ~1000ms)")

    // Comparaison avec exécution séquentielle
    val timeSeq = measureTimeMillis {
        val results = listOf(
            downloadFile("file1.txt"),
            downloadFile("file2.txt"),
            downloadFile("file3.txt")
        )
        println("Fichiers téléchargés: ${results.size}")
    }
    println("Temps séquentiel: ${timeSeq}ms (~3000ms)")
}

// =============================================================================
// 4. RUNBLOCKING
// =============================================================================

/**
 * Démonstration de runBlocking (point d'entrée)
 */
// [À DIRE] runBlocking fait le pont entre le monde bloquant et les coroutines. On l'utilise ici seulement parce qu'on est dans main().
// [PIÈGE] runBlocking sur le thread UI Android bloque l'UI → ANR. En Android, on utilise viewModelScope / lifecycleScope,
// et en test runTest (J3).
fun demo10_runBlocking() {
    println("\n=== Demo 10: runBlocking ===")

    println("Avant runBlocking - Thread: ${Thread.currentThread().name}")

    runBlocking {
        println("Dans runBlocking - Thread: ${Thread.currentThread().name}")
        delay(1000)
        println("Après delay")
    }

    println("Après runBlocking - Thread: ${Thread.currentThread().name}")
}

/**
 * runBlocking bloque le thread appelant
 */
// [SAUTER] Déjà dit en demo10.
fun demo11_runBlockingBlocks() {
    println("\n=== Demo 11: runBlocking BLOQUE le thread ===")

    val time = measureTimeMillis {
        runBlocking {
            println("Coroutine commence...")
            delay(2000)
            println("Coroutine terminée")
        }
        println("runBlocking a bloqué le thread pendant 2 secondes")
    }
    println("Temps total: ${time}ms")
}

/**
 * Plusieurs runBlocking (déconseillé)
 */
// [SAUTER] Cas qui ne se présente pas en Android.
fun demo12_multipleRunBlocking() {
    println("\n=== Demo 12: Plusieurs runBlocking (DÉCONSEILLÉ) ===")

    val time = measureTimeMillis {
        runBlocking {
            delay(1000)
            println("Premier runBlocking")
        }

        runBlocking {
            delay(1000)
            println("Deuxième runBlocking")
        }
    }
    println("Temps total: ${time}ms (séquentiel, ~2000ms)")
    println("Note: Évitez d'utiliser plusieurs runBlocking")
}

// =============================================================================
// 5. JOB ET DEFERRED
// =============================================================================

/**
 * Manipulation de Job
 */
// [À DIRE] Le Job est une poignée sur la coroutine : état, join(), cancel().
// [ANDROID] Recherche à la frappe : `searchJob?.cancel(); searchJob = viewModelScope.launch { ... }`.
fun demo13_job() = runBlocking {
    println("\n=== Demo 13: Job ===")

    val job = launch {
        println("Job commence...")
        delay(2000)
        println("Job terminé")
    }

    println("Job lancé. État: isActive=${job.isActive}, isCompleted=${job.isCompleted}")

    delay(1000)
    println("Après 1s. État: isActive=${job.isActive}, isCompleted=${job.isCompleted}")

    job.join() // Attendre la fin
    println("Après join(). État: isActive=${job.isActive}, isCompleted=${job.isCompleted}")
}

/**
 * Manipulation de Deferred
 */
// [SAUTER] Même chose que demo13 avec un résultat.
fun demo14_deferred() = runBlocking {
    println("\n=== Demo 14: Deferred ===")

    val deferred = async {
        println("Deferred commence...")
        delay(2000)
        println("Deferred calcule...")
        42 * 2
    }

    println("Deferred lancé. État: isActive=${deferred.isActive}, isCompleted=${deferred.isCompleted}")

    delay(1000)
    println("Après 1s. État: isActive=${deferred.isActive}, isCompleted=${deferred.isCompleted}")

    val result = deferred.await() // Attendre le résultat
    println("Résultat: $result")
    println("État final: isActive=${deferred.isActive}, isCompleted=${deferred.isCompleted}")
}

/**
 * Différence entre Job et Deferred
 */
// [À DIRE] Une phrase suffit : Deferred<T> est un Job qui porte une valeur.
fun demo15_jobVsDeferred() = runBlocking {
    println("\n=== Demo 15: Job vs Deferred ===")

    // Job: pas de valeur de retour
    val job: Job = launch {
        delay(1000)
        println("Job: exécution d'une tâche")
        // Pas de return
    }

    // Deferred: avec valeur de retour
    val deferred: Deferred<Int> = async {
        delay(1000)
        println("Deferred: calcul d'une valeur")
        100 // Retourne une valeur
    }

    job.join() // Attendre la fin
    val value = deferred.await() // Récupérer le résultat
    println("Valeur du Deferred: $value")

    println("\nNote: Deferred hérite de Job")
    println("Deferred est un Job: ${deferred is Job}")
}

/**
 * Chaînage de Jobs
 */
// [ATTENTION FORMATEUR] Ce n'est pas un « chaînage » : les 5 jobs tournent en concurrence (~5 s au total, pas 15 s).
// Il existe aussi `jobs.joinAll()`.
// [SAUTER] Démo de 5 s.
fun demo16_jobChaining() = runBlocking {
    println("\n=== Demo 16: Chaînage de Jobs ===")

    val jobs = List(5) { index ->
        launch {
            delay(1000L * (index + 1))
            println("Job $index terminé après ${index + 1} seconde(s)")
        }
    }

    println("Attente de tous les jobs...")
    jobs.forEach { it.join() }
    println("Tous les jobs sont terminés")
}

/**
 * Chaînage de Deferred
 */
// [SAUTER] Même remarque que demo16. Si on la montre, citer `deferreds.awaitAll()`.
fun demo17_deferredChaining() = runBlocking {
    println("\n=== Demo 17: Chaînage de Deferred ===")

    val deferreds = List(5) { index ->
        async {
            delay(500)
            index * index
        }
    }

    println("Récupération des résultats...")
    val results = deferreds.map { it.await() }
    println("Résultats: $results")
}

/**
 * Job avec enfants
 */
// [À DIRE] Transition vers le fichier 02 : le parent n'est terminé que quand tous ses enfants le sont.
fun demo18_jobWithChildren() = runBlocking {
    println("\n=== Demo 18: Job avec enfants ===")

    val parentJob = launch {
        println("Parent commence")

        launch {
            delay(1000)
            println("Enfant 1 terminé")
        }

        launch {
            delay(1500)
            println("Enfant 2 terminé")
        }

        println("Parent a lancé ses enfants")
    }

    println("Parent job lancé")
    parentJob.join() // Attend le parent ET ses enfants
    println("Parent et tous ses enfants sont terminés")
}

// =============================================================================
// RÉSUMÉ ET BONNES PRATIQUES
// =============================================================================

// [ATTENTION FORMATEUR] printSummary() et main() sont déclarés au niveau racine, sans package, dans 01, 02 et 03 :
// il y a conflit si les fichiers sont compilés ensemble dans un même module. Exécuter chaque fichier séparément.
fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Bases des Coroutines Kotlin")
    println("=".repeat(80))

    println("""
    1. BLOQUER vs SUSPENDRE:
       - Thread.sleep(): BLOQUE le thread (mauvais)
       - delay(): SUSPEND la coroutine (bon)
       - Les fonctions suspend libèrent le thread pendant l'attente

    2. FONCTIONS SUSPEND:
       - Marquées avec le mot-clé 'suspend'
       - Peuvent être suspendues et reprises
       - Doivent être appelées depuis une coroutine ou une autre fonction suspend
       - Ne bloquent pas le thread

    3. LAUNCH vs ASYNC:
       - launch: Fire-and-forget, retourne Job
         * Pour les effets de bord (logging, updates, etc.)
         * Pas de valeur de retour
       - async: Calcul concurrent, retourne Deferred<T>
         * Pour récupérer un résultat
         * Utiliser await() pour obtenir la valeur

    4. RUNBLOCKING:
       - Point d'entrée pour passer du code bloquant au code suspend
       - BLOQUE le thread appelant
       - À utiliser principalement dans main() et les tests
       - Ne pas utiliser dans du code de production (sauf main)

    5. JOB:
       - Représente une coroutine lancée avec launch
       - Propriétés: isActive, isCompleted, isCancelled
       - Méthodes: join() (attendre), cancel() (annuler)
       - Peut avoir des enfants (structured concurrency)

    6. DEFERRED:
       - Hérite de Job
       - Représente une coroutine lancée avec async
       - Contient une valeur de retour de type T
       - Utiliser await() pour récupérer la valeur

    BONNES PRATIQUES:
    ✓ Utiliser delay() au lieu de Thread.sleep()
    ✓ Utiliser launch pour les effets de bord
    ✓ Utiliser async pour les calculs parallèles
    ✓ Limiter l'usage de runBlocking à main() et aux tests
    ✓ Préférer les fonctions suspend aux callbacks
    ✓ Utiliser join() ou await() pour attendre les résultats

    ANTI-PATTERNS:
    ✗ Thread.sleep() dans une coroutine
    ✗ Multiples runBlocking en cascade
    ✗ async sans await (utiliser launch à la place)
    ✗ Bloquer un thread dans du code suspend
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(20) + "KOTLIN COROUTINES - BASICS")
    println("=" + "=".repeat(78) + "=")

    // 1. Bloquer vs Suspendre
    demo01_threadSleep()
    demo02_delay()
    demo03_blockingVsSuspending()

    // 2. Fonctions suspend
    demo04_suspendFunctions()
    demo05_conditionalSuspension()

    // 3. Launch vs Async
    demo06_launch()
    demo07_async()
    demo08_launchVsAsync()
    demo09_parallelAsync()

    // 4. runBlocking
    demo10_runBlocking()
    demo11_runBlockingBlocks()
    demo12_multipleRunBlocking()

    // 5. Job et Deferred
    demo13_job()
    demo14_deferred()
    demo15_jobVsDeferred()
    demo16_jobChaining()
    demo17_deferredChaining()
    demo18_jobWithChildren()

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations")
    println("=".repeat(80))
}
