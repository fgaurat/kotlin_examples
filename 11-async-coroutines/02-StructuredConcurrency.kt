// ==============================================================================
// FICHE FORMATEUR — Concurrence structurée
// Objectif : toute coroutine a un parent, qui l'attend, peut l'annuler et reçoit ses erreurs.
// À présenter (dans l'ordre) :
//   1. GlobalScope vs launch dans un scope (demo01, demo02) : pourquoi les scopes existent
//   2. Les 3 principes : attente, annulation descendante, erreurs montantes (demo03)
//   3. coroutineScope : parallélisme local dans une suspend fun, « tout ou rien » (demo06, demo07)
//   4. supervisorScope : un échec n'annule pas les frères (demo09, demo10)
//   5. Annulation en cascade et coopérative (demo13)
// Lien Android : viewModelScope (SupervisorJob + Dispatchers.Main.immediate, annulé dans onCleared),
//   lifecycleScope, coroutineScope { async } dans le repository (bonus TP04)
// Pièges / questions fréquentes : GlobalScope ; avaler CancellationException (règle du TP04) ;
//   exception non rattrapée dans un launch de viewModelScope → crash de l'appli
// Si peu de temps : demo01, 02, 06, 07, 09, 13. Sauter 05, 11, 12, 14, 15, 16, 18.
//   Voir [ATTENTION FORMATEUR] dans demo03 : le main s'arrête à cet endroit.
// ==============================================================================
/**
 * 02-StructuredConcurrency.kt
 *
 * Exemples de Structured Concurrency (Concurrence Structurée)
 * - coroutineScope
 * - supervisorScope
 * - Principe de structured concurrency
 * - Relations parent-enfant
 * - Propagation des erreurs
 */

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// =============================================================================
// 1. PRINCIPE DE STRUCTURED CONCURRENCY
// =============================================================================

/**
 * Démonstration du problème sans structured concurrency
 */
// [PIÈGE] GlobalScope : la coroutine n'a pas de parent et survit à l'écran (fuite, travail inutile, mise à jour d'une UI détruite).
// L'API est marquée @DelicateCoroutinesApi, d'où l'avertissement à la compilation.
// [ANDROID] On utilise viewModelScope / lifecycleScope, annulés automatiquement (onCleared / DESTROYED).
fun demo01_withoutStructuredConcurrency() = runBlocking {
    println("\n=== Demo 01: Sans Structured Concurrency (PROBLÉMATIQUE) ===")

    // GlobalScope: les coroutines ne sont pas liées au scope parent
    GlobalScope.launch {
        delay(1000)
        println("Cette coroutine peut survivre au parent!")
    }

    println("Fonction terminée (mais la coroutine peut encore tourner)")
    delay(1500) // On doit attendre manuellement
}

/**
 * Démonstration avec structured concurrency
 */
// [À DIRE] Un scope, c'est la « durée de vie » des coroutines. Quand le scope finit ou est annulé, ses coroutines aussi.
fun demo02_withStructuredConcurrency() = runBlocking {
    println("\n=== Demo 02: Avec Structured Concurrency (BON) ===")

    // Lancement dans le scope du parent
    launch {
        delay(1000)
        println("Cette coroutine est liée au parent")
    }

    println("Fonction terminée automatiquement après les enfants")
    // Pas besoin de delay, runBlocking attend automatiquement
}

/**
 * Les trois principes de structured concurrency
 */
fun demo03_structuredConcurrencyPrinciples() = runBlocking {
    println("\n=== Demo 03: Principes de Structured Concurrency ===")

    println("1. Un parent attend tous ses enfants")
    launch {
        launch {
            delay(1000)
            println("  Petit-enfant terminé")
        }
        println("  Enfant attend son petit-enfant")
    }
    println("  Parent attend son enfant\n")

    delay(1500)

    println("2. L'annulation se propage du parent aux enfants")
    val job = launch {
        launch {
            // [PIÈGE] Attraper CancellationException sans la relancer l'« avale ». Ici c'est sans conséquence,
            // mais la règle du TP04 est de la relancer (`throw e`), ou d'utiliser finally pour le nettoyage.
            try {
                delay(10000)
            } catch (e: CancellationException) {
                println("  Enfant annulé car parent annulé")
            }
        }
    }
    delay(100)
    job.cancel()
    delay(100)

    // [ATTENTION FORMATEUR] Ce try/catch ne capture rien : launch rend la main tout de suite. L'exception remonte
    // au parent runBlocking, qui échoue : main() plante ici et les démos suivantes ne s'exécutent pas.
    // Pour la démontrer, il faut placer `coroutineScope { launch { throw ... } }` dans le try (comme en demo07).
    println("\n3. Les erreurs se propagent des enfants au parent")
    try {
        launch {
            throw RuntimeException("Erreur dans l'enfant")
        }
    } catch (e: Exception) {
        println("  Parent a capturé l'erreur: ${e.message}")
    }
}

// =============================================================================
// 2. COROUTINESCOPE
// =============================================================================

/**
 * Utilisation basique de coroutineScope
 */
// [À DIRE] coroutineScope est une suspend fun : elle crée un scope local et ne rend la main qu'une fois tous ses enfants terminés.
// [ANDROID] C'est ainsi qu'un repository lance des coroutines sans recevoir de scope de l'extérieur.
suspend fun performTask(): String = coroutineScope {
    println("Début de la tâche")

    launch {
        delay(1000)
        println("  Sous-tâche 1 terminée")
    }

    launch {
        delay(500)
        println("  Sous-tâche 2 terminée")
    }

    delay(1500) // Attendre un peu plus
    println("Tâche principale terminée")
    "Résultat"
}

fun demo04_coroutineScope() = runBlocking {
    println("\n=== Demo 04: coroutineScope ===")

    println("Avant coroutineScope")
    val result = performTask()
    println("Après coroutineScope: $result")
    println("Note: coroutineScope attend TOUS ses enfants")
}

/**
 * coroutineScope crée un nouveau scope mais hérite du contexte
 */
// [SAUTER] Affichage de Job internes, trop technique pour la demi-journée.
fun demo05_coroutineScopeInheritsContext() = runBlocking {
    println("\n=== Demo 05: coroutineScope hérite du contexte ===")

    println("Parent context: ${coroutineContext[Job]}")

    coroutineScope {
        println("coroutineScope context: ${coroutineContext[Job]}")
        println("Note: Nouveau Job mais même contexte")

        launch {
            println("  Enfant context: ${coroutineContext[Job]}")
        }
    }
}

/**
 * coroutineScope attend tous les enfants
 */
// [ANDROID] Le modèle du bonus TP04 : `coroutineScope { val a = async { api.x() }; val b = async { api.y() }; a.await() to b.await() }`.
suspend fun processMultipleItems(): List<Int> = coroutineScope {
    println("Traitement de plusieurs items en parallèle")

    val results = (1..5).map { id ->
        async {
            delay(1000)
            println("  Item $id traité")
            id * 10
        }
    }

    results.awaitAll()
}

fun demo06_coroutineScopeWaitsForAll() = runBlocking {
    println("\n=== Demo 06: coroutineScope attend tous les enfants ===")

    val time = measureTimeMillis {
        val results = processMultipleItems()
        println("Tous les résultats: $results")
    }
    println("Temps total: ${time}ms (~1000ms, exécution parallèle)")
}

/**
 * Propagation d'erreur avec coroutineScope
 */
// [À DIRE] « Tout ou rien » : la tâche 2 échoue à 300 ms, les tâches 1 et 3 sont annulées, puis coroutineScope relance l'exception.
// Ici le try/catch de l'appelant fonctionne.
suspend fun taskWithError() = coroutineScope {
    launch {
        delay(500)
        println("Tâche 1 réussie")
    }

    launch {
        delay(300)
        throw RuntimeException("Erreur dans tâche 2")
    }

    launch {
        delay(1000)
        println("Tâche 3 (n'arrivera jamais)")
    }
}

fun demo07_coroutineScopeErrorPropagation() = runBlocking {
    println("\n=== Demo 07: coroutineScope - Propagation d'erreur ===")

    try {
        taskWithError()
    } catch (e: Exception) {
        println("Erreur capturée: ${e.message}")
        println("Note: Toutes les coroutines sont annulées en cas d'erreur")
    }
}

// =============================================================================
// 3. SUPERVISORSCOPE
// =============================================================================

/**
 * Comportement de supervisorScope face aux erreurs
 */
suspend fun taskWithErrorSupervised() = supervisorScope {
    launch {
        delay(500)
        println("  Tâche 1 réussie")
    }

    launch {
        delay(300)
        throw RuntimeException("Erreur dans tâche 2")
    }

    launch {
        delay(1000)
        println("  Tâche 3 réussie (supervisorScope permet sa continuation)")
    }
}

// [ATTENTION FORMATEUR] Le try/catch ne capture rien : dans supervisorScope, l'exception d'un launch enfant n'est pas relancée.
// Elle part au CoroutineExceptionHandler (ici le handler par défaut, qui imprime une stack trace : prévenir la salle).
// Le delay(1500) est inutile, supervisorScope attend déjà ses enfants.
fun demo08_supervisorScope() = runBlocking {
    println("\n=== Demo 08: supervisorScope ===")

    try {
        taskWithErrorSupervised()
    } catch (e: Exception) {
        println("Erreur: ${e.message}")
    }

    delay(1500)
    println("Note: Les autres tâches continuent malgré l'erreur")
}

/**
 * Comparaison coroutineScope vs supervisorScope
 */
// [À DIRE] La démo à garder si on ne doit en montrer qu'une sur les deux scopes.
// [ATTENTION FORMATEUR] Dans la partie supervisorScope, B rattrape sa propre erreur : avec un coroutineScope,
// A et C auraient aussi continué. La démo ne prouve donc pas l'effet du superviseur (voir demo08 ou demo10).
fun demo09_coroutineScopeVsSupervisorScope() = runBlocking {
    println("\n=== Demo 09: coroutineScope vs supervisorScope ===")

    // Avec coroutineScope: une erreur annule tout
    println("Avec coroutineScope:")
    try {
        coroutineScope {
            launch {
                delay(500)
                println("  Task A done")
            }
            launch {
                delay(100)
                throw RuntimeException("Error in B")
            }
            launch {
                delay(1000)
                println("  Task C done (never printed)")
            }
        }
    } catch (e: Exception) {
        println("  Exception: ${e.message}")
    }

    delay(1500)

    // Avec supervisorScope: les autres tâches continuent
    println("\nAvec supervisorScope:")
    supervisorScope {
        launch {
            delay(500)
            println("  Task A done")
        }
        launch {
            try {
                delay(100)
                throw RuntimeException("Error in B")
            } catch (e: Exception) {
                println("  Task B failed: ${e.message}")
            }
        }
        launch {
            delay(1000)
            println("  Task C done (printed!)")
        }
    }
}

/**
 * supervisorScope pour tâches indépendantes
 */
// [À DIRE] Avec async dans supervisorScope, l'erreur est conservée dans le Deferred et relancée à await() : un try/catch par await.
// [ANDROID] viewModelScope repose sur un SupervisorJob : l'échec d'un launch n'annule pas les autres coroutines du ViewModel.
// [PIÈGE] catch (e: Exception) attrape aussi CancellationException : la relancer avant de convertir en null.
suspend fun downloadFiles(urls: List<String>): List<String?> = supervisorScope {
    urls.map { url ->
        async {
            delay(500)
            if (url.contains("fail")) {
                throw RuntimeException("Failed to download $url")
            }
            "Content of $url"
        }
    }.map {
        try {
            it.await()
        } catch (e: Exception) {
            println("  Erreur: ${e.message}")
            null
        }
    }
}

fun demo10_supervisorScopeIndependentTasks() = runBlocking {
    println("\n=== Demo 10: supervisorScope pour tâches indépendantes ===")

    val urls = listOf(
        "http://example.com/file1.txt",
        "http://example.com/fail_file2.txt",
        "http://example.com/file3.txt"
    )

    val results = downloadFiles(urls)
    println("Résultats: $results")
    println("Note: Les téléchargements réussis sont retournés malgré les échecs")
}

// =============================================================================
// 4. RELATIONS PARENT-ENFANT
// =============================================================================

/**
 * Hiérarchie de coroutines
 */
// [SAUTER] Redit demo18 du fichier 01.
fun demo11_parentChildHierarchy() = runBlocking {
    println("\n=== Demo 11: Hiérarchie Parent-Enfant ===")

    val parent = launch {
        println("Parent commence")

        val child1 = launch {
            println("  Enfant 1 commence")
            delay(1000)
            println("  Enfant 1 terminé")
        }

        val child2 = launch {
            println("  Enfant 2 commence")

            val grandChild = launch {
                println("    Petit-enfant commence")
                delay(1500)
                println("    Petit-enfant terminé")
            }

            delay(500)
            println("  Enfant 2 terminé (mais attend petit-enfant)")
        }

        println("Parent a lancé ses enfants")
    }

    parent.join()
    println("Parent et toute sa descendance terminés")
}

/**
 * Le parent attend tous ses enfants
 */
// [ATTENTION FORMATEUR] measureTimeMillis ne mesure que le launch (≈ 0 ms), qui rend la main immédiatement :
// le « ~5000ms » affiché est faux et s'imprime avant les enfants.
// [SAUTER]
fun demo12_parentWaitsForChildren() = runBlocking {
    println("\n=== Demo 12: Le parent attend tous ses enfants ===")

    val time = measureTimeMillis {
        launch {
            println("Parent commence")

            repeat(5) { i ->
                launch {
                    delay(1000L * (i + 1))
                    println("  Enfant $i terminé après ${i + 1}s")
                }
            }

            println("Parent a lancé tous ses enfants")
            // Pas besoin de join(), le parent attend automatiquement
        }
    }
    println("Temps total: ${time}ms (~5000ms)")
}

/**
 * Annulation en cascade
 */
// [À DIRE] L'annulation est coopérative : delay (et toute suspend fun de kotlinx) vérifie l'annulation.
// Une boucle de calcul pur doit appeler ensureActive() ou isActive.
// [ANDROID] On quitte l'écran → viewModelScope est annulé → Retrofit annule la requête HTTP en cours.
// [PIÈGE] Les catch ci-dessous avalent CancellationException (même remarque qu'en demo03).
fun demo13_cancellationCascade() = runBlocking {
    println("\n=== Demo 13: Annulation en cascade ===")

    val parent = launch {
        val child1 = launch {
            try {
                repeat(10) {
                    delay(500)
                    println("  Enfant 1: itération $it")
                }
            } catch (e: CancellationException) {
                println("  Enfant 1 annulé")
            }
        }

        val child2 = launch {
            try {
                repeat(10) {
                    delay(500)
                    println("  Enfant 2: itération $it")
                }
            } catch (e: CancellationException) {
                println("  Enfant 2 annulé")
            }
        }
    }

    delay(1500)
    println("Annulation du parent...")
    parent.cancel()
    delay(500)
    println("Tous les enfants ont été annulés avec le parent")
}

/**
 * Job indépendant avec Job()
 */
// [PIÈGE] Passer Job() à launch casse le lien parent-enfant : runBlocking ne l'attend plus et ne l'annule plus.
// Sur Android, `viewModelScope.launch(Job())` n'est plus annulé dans onCleared().
// [SAUTER] Se contenter de citer le piège.
fun demo14_independentJob() = runBlocking {
    println("\n=== Demo 14: Job indépendant ===")

    val independentJob = Job()

    launch(independentJob) {
        try {
            delay(5000)
            println("Job indépendant terminé")
        } catch (e: CancellationException) {
            println("Job indépendant annulé")
        }
    }

    println("Job indépendant lancé")
    delay(1000)

    println("Annulation du job indépendant...")
    independentJob.cancel()
    println("Note: Ce job n'est pas lié au scope parent")
}

// =============================================================================
// 5. PROPAGATION DES ERREURS
// =============================================================================

/**
 * Propagation d'erreur dans coroutineScope
 */
// [SAUTER] Doublon de demo07.
fun demo15_errorPropagationCoroutineScope() = runBlocking {
    println("\n=== Demo 15: Propagation d'erreur - coroutineScope ===")

    try {
        coroutineScope {
            launch {
                delay(500)
                println("  Tâche rapide terminée")
            }

            launch {
                delay(100)
                println("  Tâche avec erreur...")
                throw IllegalStateException("Erreur critique!")
            }

            launch {
                delay(1000)
                println("  Tâche lente (annulée)")
            }
        }
    } catch (e: Exception) {
        println("Erreur propagée au parent: ${e.message}")
    }
}

/**
 * Gestion d'erreur dans supervisorScope
 */
// [SAUTER] Doublon de demo09 (même remarque : l'erreur est gérée localement, le superviseur ne sert pas ici).
fun demo16_errorHandlingSupervisorScope() = runBlocking {
    println("\n=== Demo 16: Gestion d'erreur - supervisorScope ===")

    supervisorScope {
        launch {
            delay(500)
            println("  Tâche rapide terminée")
        }

        launch {
            try {
                delay(100)
                println("  Tâche avec erreur...")
                throw IllegalStateException("Erreur non-critique")
            } catch (e: Exception) {
                println("  Erreur gérée localement: ${e.message}")
            }
        }

        launch {
            delay(1000)
            println("  Tâche lente terminée (continue normalement)")
        }
    }

    println("supervisorScope terminé normalement")
}

/**
 * Erreur dans async avec coroutineScope
 */
// [ATTENTION FORMATEUR] Le commentaire « se propage au premier await » est trompeur. deferred2 échoue à 100 ms
// et annule tout le coroutineScope : deferred1.await() est interrompu et « Résultat 1 » ne s'affiche jamais.
// Dans coroutineScope, l'erreur passe par le scope, pas par await.
fun demo17_errorInAsyncCoroutineScope() = runBlocking {
    println("\n=== Demo 17: Erreur dans async - coroutineScope ===")

    try {
        coroutineScope {
            val deferred1 = async {
                delay(500)
                42
            }

            val deferred2 = async {
                delay(100)
                throw RuntimeException("Erreur dans async")
            }

            val deferred3 = async {
                delay(1000)
                100
            }

            // L'erreur se propage au premier await
            println("Résultat 1: ${deferred1.await()}")
            println("Résultat 2: ${deferred2.await()}") // Exception ici
        }
    } catch (e: Exception) {
        println("Exception capturée: ${e.message}")
    }
}

/**
 * Erreur dans async avec supervisorScope
 */
// [SAUTER] Même idée que demo10.
fun demo18_errorInAsyncSupervisorScope() = runBlocking {
    println("\n=== Demo 18: Erreur dans async - supervisorScope ===")

    supervisorScope {
        val deferred1 = async {
            delay(500)
            42
        }

        val deferred2 = async {
            delay(100)
            throw RuntimeException("Erreur dans async")
        }

        val deferred3 = async {
            delay(1000)
            100
        }

        // Gestion individuelle des erreurs
        val result1 = try {
            deferred1.await()
        } catch (e: Exception) {
            println("Erreur deferred1: ${e.message}"); null
        }

        val result2 = try {
            deferred2.await()
        } catch (e: Exception) {
            println("Erreur deferred2: ${e.message}"); null
        }

        val result3 = try {
            deferred3.await()
        } catch (e: Exception) {
            println("Erreur deferred3: ${e.message}"); null
        }

        println("Résultats: $result1, $result2, $result3")
    }
}

// =============================================================================
// 6. EXEMPLES PRATIQUES
// =============================================================================

/**
 * Traitement parallèle avec gestion d'erreur
 */
// [À DIRE] Cas concret : un écran « tableau de bord » qui agrège plusieurs appels réseau.
// [ATTENTION FORMATEUR] User, fetchUser, printSummary et main sont au niveau racine sans package : conflit possible
// avec d'autres fichiers du même module. Exécuter le fichier seul.
data class User(val id: Int, val name: String)
data class Order(val userId: Int, val amount: Double)

suspend fun fetchUser(id: Int): User {
    delay(500)
    if (id == 3) throw RuntimeException("User $id not found")
    return User(id, "User-$id")
}

suspend fun fetchOrders(userId: Int): List<Order> {
    delay(300)
    return listOf(Order(userId, 100.0), Order(userId, 200.0))
}

suspend fun loadDashboardWithCoroutineScope(userIds: List<Int>) = coroutineScope {
    userIds.map { id ->
        async {
            val user = fetchUser(id)
            val orders = fetchOrders(id)
            user to orders
        }
    }.awaitAll()
}

// [ANDROID] Ce code vit dans le repository. Le ViewModel transforme l'échec en état d'erreur dans son StateFlow (TP07),
// ou le repository renvoie un Result (TP04).
fun demo19_practicalCoroutineScope() = runBlocking {
    println("\n=== Demo 19: Exemple pratique - coroutineScope ===")

    try {
        val results = loadDashboardWithCoroutineScope(listOf(1, 2, 3, 4))
        println("Résultats: $results")
    } catch (e: Exception) {
        println("Erreur: ${e.message}")
        println("Note: Tout le chargement échoue si un user échoue")
    }
}

// [PIÈGE] catch (e: Exception) attrape aussi CancellationException : si l'écran se ferme, l'annulation devient null
// au lieu d'arrêter le travail. Règle du TP04 : `catch (e: CancellationException) { throw e }` en premier.
// [ATTENTION FORMATEUR] Chaque async rattrape sa propre erreur : un simple coroutineScope suffirait ici.
suspend fun loadDashboardWithSupervisorScope(userIds: List<Int>) = supervisorScope {
    userIds.map { id ->
        async {
            try {
                val user = fetchUser(id)
                val orders = fetchOrders(id)
                user to orders
            } catch (e: Exception) {
                println("  Erreur pour user $id: ${e.message}")
                null
            }
        }
    }.awaitAll().filterNotNull()
}

fun demo20_practicalSupervisorScope() = runBlocking {
    println("\n=== Demo 20: Exemple pratique - supervisorScope ===")

    val results = loadDashboardWithSupervisorScope(listOf(1, 2, 3, 4))
    println("Résultats réussis: ${results.size}")
    println("Note: Les users valides sont chargés malgré les erreurs")
}

// =============================================================================
// RÉSUMÉ ET BONNES PRATIQUES
// =============================================================================

// [ANDROID] Nuance au résumé « pas de propagation automatique » : une exception non rattrapée dans un launch
// part au CoroutineExceptionHandler. Sur Android, sans handler, l'appli plante.
fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Structured Concurrency")
    println("=".repeat(80))

    println("""
    1. STRUCTURED CONCURRENCY - 3 PRINCIPES:
       a) Un scope attend automatiquement tous ses enfants
       b) L'annulation se propage du parent aux enfants
       c) Les erreurs se propagent des enfants au parent

    2. COROUTINESCOPE:
       - Crée un nouveau scope qui hérite du contexte parent
       - Attend automatiquement tous ses enfants
       - Si un enfant échoue, TOUS les enfants sont annulés
       - L'exception est propagée au parent
       - Utiliser pour: tâches interdépendantes

    3. SUPERVISORSCOPE:
       - Comme coroutineScope mais gestion d'erreur différente
       - Si un enfant échoue, les AUTRES continuent
       - L'exception n'est PAS propagée automatiquement
       - Utiliser pour: tâches indépendantes

    4. RELATIONS PARENT-ENFANT:
       - Chaque coroutine a un parent (sauf les root coroutines)
       - Le parent attend automatiquement ses enfants (join implicite)
       - L'annulation du parent annule tous les enfants
       - Les enfants peuvent échouer sans affecter les frères (supervisorScope)

    5. PROPAGATION DES ERREURS:

       coroutineScope:
       - Exception dans enfant → annule tous les enfants → propage au parent
       - Comportement "tout ou rien"

       supervisorScope:
       - Exception dans enfant → enfant échoue
       - Les autres enfants continuent normalement
       - Pas de propagation automatique au parent

    QUAND UTILISER QUOI?

    coroutineScope:
    ✓ Tâches interdépendantes (si une échoue, tout doit échouer)
    ✓ Transactions (atomicité requise)
    ✓ Workflow séquentiel avec parallélisme interne

    supervisorScope:
    ✓ Tâches indépendantes (échec isolé)
    ✓ Téléchargements multiples
    ✓ Appels API parallèles non-critiques
    ✓ Traitement par lots

    BONNES PRATIQUES:
    ✓ Toujours utiliser structured concurrency (éviter GlobalScope)
    ✓ Utiliser coroutineScope pour créer un scope local
    ✓ Utiliser supervisorScope quand les tâches sont indépendantes
    ✓ Ne pas bloquer dans un scope (pas de runBlocking)
    ✓ Laisser le parent attendre ses enfants automatiquement

    ANTI-PATTERNS:
    ✗ GlobalScope.launch (perte du contrôle)
    ✗ Oublier de gérer les erreurs dans supervisorScope
    ✗ Utiliser coroutineScope quand les tâches sont indépendantes
    ✗ join() manuel sur tous les enfants (automatique avec scope)
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(18) + "KOTLIN COROUTINES - STRUCTURED CONCURRENCY")
    println("=" + "=".repeat(78) + "=")

    // 1. Principe de Structured Concurrency
    demo01_withoutStructuredConcurrency()
    demo02_withStructuredConcurrency()
    demo03_structuredConcurrencyPrinciples()

    // 2. coroutineScope
    demo04_coroutineScope()
    demo05_coroutineScopeInheritsContext()
    demo06_coroutineScopeWaitsForAll()
    demo07_coroutineScopeErrorPropagation()

    // 3. supervisorScope
    demo08_supervisorScope()
    demo09_coroutineScopeVsSupervisorScope()
    demo10_supervisorScopeIndependentTasks()

    // 4. Relations Parent-Enfant
    demo11_parentChildHierarchy()
    demo12_parentWaitsForChildren()
    demo13_cancellationCascade()
    demo14_independentJob()

    // 5. Propagation des erreurs
    demo15_errorPropagationCoroutineScope()
    demo16_errorHandlingSupervisorScope()
    demo17_errorInAsyncCoroutineScope()
    demo18_errorInAsyncSupervisorScope()

    // 6. Exemples pratiques
    demo19_practicalCoroutineScope()
    demo20_practicalSupervisorScope()

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations")
    println("=".repeat(80))
}
