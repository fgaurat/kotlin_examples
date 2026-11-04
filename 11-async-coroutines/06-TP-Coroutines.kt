// ==============================================================================
// FICHE FORMATEUR — TP de synthèse : dashboard chargé en coroutines
// Objectif : combiner async/await, withTimeoutOrNull, supervisorScope et annulation sur un cas concret.
// À présenter (dans l'ordre) :
//   1. L'énoncé + la version séquentielle (~600 ms) : repérer les dépendances
//   2. Version parallèle : async dans coroutineScope (~400 ms), computeScore sur Dispatchers.Default
//   3. demo04 : timeout global avec withTimeoutOrNull
//   4. demo05 : échec partiel avec supervisorScope
//   5. demo06 : annulation, puis relecture critique de loadDashboardComplete (piège CancellationException)
// Lien Android : loadDashboard = repository/use case appelé depuis viewModelScope.launch, résultat
//   posé dans un StateFlow<UiState> (TP07) ; même bonus async que le TP04 ; testable en temps
//   virtuel avec runTest (TP08).
// Pièges / questions fréquentes : var partagées modifiées depuis plusieurs launch ; catch de
//   CancellationException qui renvoie une valeur ; Thread.sleep dans du code appelé en suspend.
// Si peu de temps : faire coder les étapes a-b-c (~30 min), montrer demo05 et la critique de
//   loadDashboardComplete ; sauter demo03, demo07, sections 8 et 10, le résumé.
// ------------------------------------------------------------------------------
// À FAIRE FAIRE AUX STAGIAIRES (fournir seulement les modèles, fetchUser, fetchStats, computeScore) :
//   a. loadDashboardSequential + mesure du temps (measureTimeMillis)
//   b. loadDashboardParallel : user et stats en parallèle, score ensuite
//   c. timeout global qui renvoie null (tester avec fetchUserSlow et 500 ms)
//   d. tolérer l'échec de fetchUser (fetchUserFailure) sans tout perdre
//   e. bonus : annuler un chargement en cours ; charger 5 dashboards en parallèle
// À VÉRIFIER DANS LEUR SOLUTION :
//   - async lancés dans coroutineScope / supervisorScope, jamais GlobalScope ni CoroutineScope() à la volée
//   - les deux async démarrent AVANT le premier await() (sinon c'est séquentiel)
//   - computeScore dans withContext(Dispatchers.Default), pas de runBlocking dans une fonction suspend
//   - aucun catch (Exception/Throwable) ni runCatching qui avale CancellationException
//   - temps mesuré ~400 ms contre ~600 ms
//   - échec partiel : async + try autour de await() plutôt que des var partagées et un delay() d'attente
// ==============================================================================
/**
 * 06-TP-Coroutines.kt
 *
 * TP (Travaux Pratiques) Complet sur les Coroutines
 * Basé sur l'énoncé des slides
 *
 * Objectif: Charger un dashboard utilisateur avec:
 * - Données utilisateur (fetchUser)
 * - Statistiques (fetchStats)
 * - Score calculé (computeScore)
 *
 * Implémentations:
 * 1. Séquentielle (lente)
 * 2. Parallèle (rapide)
 * 3. Avec timeout
 * 4. Avec supervisorScope
 * 5. Avec annulation
 * 6. Version complète avec toutes les fonctionnalités
 */

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

// =============================================================================
// MODÈLES DE DONNÉES
// =============================================================================

data class User(
    val id: Int,
    val name: String,
    val email: String
)

data class Stats(
    val userId: Int,
    val viewCount: Int,
    val likeCount: Int,
    val shareCount: Int
)

data class Score(
    val userId: Int,
    val totalScore: Int,
    val ranking: String
)

data class Dashboard(
    val user: User,
    val stats: Stats,
    val score: Score,
    val loadTimeMs: Long
)

// =============================================================================
// FONCTIONS DE BASE (SELON L'ÉNONCÉ)
// =============================================================================

/**
 * Récupération des données utilisateur (simulation API)
 * Délai: 200ms
 */
// [ANDROID] dans l'appli, ce seraient des fonctions suspend Retrofit (@GET ... suspend fun getUser()) :
// elles sont main-safe, inutile de les entourer de withContext(Dispatchers.IO).
suspend fun fetchUser(userId: Int): User {
    println("  [fetchUser] Début pour user $userId")
    delay(200) // Simulation appel réseau
    println("  [fetchUser] Terminé pour user $userId")
    return User(
        id = userId,
        name = "User-$userId",
        email = "user$userId@example.com"
    )
}

/**
 * Récupération des statistiques utilisateur (simulation API)
 * Délai: 300ms
 */
suspend fun fetchStats(userId: Int): Stats {
    println("  [fetchStats] Début pour user $userId")
    delay(300) // Simulation appel réseau
    println("  [fetchStats] Terminé pour user $userId")
    return Stats(
        userId = userId,
        viewCount = (100..1000).random(),
        likeCount = (50..500).random(),
        shareCount = (10..100).random()
    )
}

/**
 * Calcul du score utilisateur (CPU intensive)
 * Pas de delay car calcul CPU, mais on simule avec un petit délai
 */
fun computeScore(stats: Stats): Score {
    println("  [computeScore] Calcul pour user ${stats.userId}")

    // Simulation calcul CPU
    val score = stats.viewCount + (stats.likeCount * 2) + (stats.shareCount * 5)

    val ranking = when {
        score > 2000 -> "Gold"
        score > 1000 -> "Silver"
        else -> "Bronze"
    }

    // [À DIRE] fonction NON suspend et bloquante, volontairement : il faudra la déplacer sur
    // Dispatchers.Default. Sur le thread Main, 100 ms bloqués = images perdues ; 5 s = ANR.
    Thread.sleep(100) // Simulation calcul CPU
    println("  [computeScore] Terminé pour user ${stats.userId}")

    return Score(
        userId = stats.userId,
        totalScore = score,
        ranking = ranking
    )
}

// =============================================================================
// 1. VERSION SÉQUENTIELLE (LENTE)
// =============================================================================

/**
 * Chargement séquentiel du dashboard
 * Temps attendu: ~200ms + 300ms + 100ms = ~600ms
 */
suspend fun loadDashboardSequential(userId: Int): Dashboard {
    println("\n[Sequential] Chargement du dashboard pour user $userId")
    // [À DIRE] du code suspend est séquentiel par défaut : écrire d'abord la version simple,
    // paralléliser ensuite seulement ce qui est indépendant.

    val startTime = System.currentTimeMillis()

    // 1. Récupération user
    val user = fetchUser(userId)

    // 2. Récupération stats (doit attendre user)
    val stats = fetchStats(userId)

    // [PIÈGE] appel direct : computeScore bloque le thread de l'appelant (sur Android, le thread
    // Main si on vient de viewModelScope.launch).
    // 3. Calcul score (doit attendre stats)
    val score = computeScore(stats)

    val loadTime = System.currentTimeMillis() - startTime

    println("[Sequential] Chargement terminé en ${loadTime}ms")

    return Dashboard(user, stats, score, loadTime)
}

fun demo01_sequential() = runBlocking {
    println("\n=== Demo 01: Version Séquentielle ===")

    val dashboard = loadDashboardSequential(1)

    println("\nRésultat:")
    println("  User: ${dashboard.user.name}")
    println("  Stats: ${dashboard.stats.viewCount} vues")
    println("  Score: ${dashboard.score.totalScore} (${dashboard.score.ranking})")
    println("  Temps: ${dashboard.loadTimeMs}ms")
}

// =============================================================================
// 2. VERSION PARALLÈLE (RAPIDE)
// =============================================================================

/**
 * Chargement parallèle du dashboard
 * Temps attendu: max(200ms, 300ms) + 100ms = ~400ms
 *
 * User et Stats peuvent être chargés en parallèle
 * Score dépend de Stats donc doit attendre
 */
suspend fun loadDashboardParallel(userId: Int): Dashboard = coroutineScope {
    println("\n[Parallel] Chargement du dashboard pour user $userId")

    val startTime = System.currentTimeMillis()

    // [À DIRE] coroutineScope : si un async échoue, l'autre est annulé et l'exception remonte à
    // l'appelant. Aucune coroutine ne survit à la fonction (concurrence structurée).
    // [PIÈGE] async { fetchUser() }.await() suivi de async { fetchStats() }.await() redevient séquentiel.
    // 1. Lancement parallèle de user et stats
    val userDeferred = async { fetchUser(userId) }
    val statsDeferred = async { fetchStats(userId) }

    // 2. Attente des résultats
    val user = userDeferred.await()
    val stats = statsDeferred.await()

    // [ANDROID] en vrai projet, injecter le dispatcher (paramètre du constructeur) pour le remplacer
    // par un StandardTestDispatcher dans runTest (TP08).
    // 3. Calcul du score (dépend de stats)
    val score = withContext(Dispatchers.Default) {
        computeScore(stats)
    }

    val loadTime = System.currentTimeMillis() - startTime

    println("[Parallel] Chargement terminé en ${loadTime}ms")

    Dashboard(user, stats, score, loadTime)
}

fun demo02_parallel() = runBlocking {
    println("\n=== Demo 02: Version Parallèle ===")

    val dashboard = loadDashboardParallel(1)

    println("\nRésultat:")
    println("  User: ${dashboard.user.name}")
    println("  Stats: ${dashboard.stats.viewCount} vues")
    println("  Score: ${dashboard.score.totalScore} (${dashboard.score.ranking})")
    println("  Temps: ${dashboard.loadTimeMs}ms")
}

// =============================================================================
// 3. COMPARAISON SÉQUENTIEL VS PARALLÈLE
// =============================================================================

fun demo03_comparison() = runBlocking {
    println("\n=== Demo 03: Comparaison Séquentiel vs Parallèle ===")
    // [SAUTER] demo01 + demo02 suffisent ; les chiffres varient (premier appel, Thread.sleep).

    // Version séquentielle
    val timeSeq = measureTimeMillis {
        val dashboard = loadDashboardSequential(1)
        println("Séquentiel: ${dashboard.loadTimeMs}ms")
    }

    println()

    // Version parallèle
    val timePar = measureTimeMillis {
        val dashboard = loadDashboardParallel(2)
        println("Parallèle: ${dashboard.loadTimeMs}ms")
    }

    println("\n📊 Gain de performance:")
    println("  Séquentiel: ${timeSeq}ms")
    println("  Parallèle: ${timePar}ms")
    println("  Amélioration: ${timeSeq - timePar}ms (~${((timeSeq - timePar) * 100.0 / timeSeq).toInt()}%)")
}

// =============================================================================
// 4. VERSION AVEC TIMEOUT
// =============================================================================

/**
 * Chargement avec timeout global
 */
suspend fun loadDashboardWithTimeout(userId: Int, timeoutMs: Long): Dashboard? {
    println("\n[Timeout] Chargement avec timeout de ${timeoutMs}ms")
    // [À DIRE] à l'échéance, withTimeoutOrNull annule tout le sous-arbre (les deux async) :
    // aucune requête ne continue en arrière-plan.
    // [ANDROID] côté ViewModel, transformer ce null en état « Timeout » ou afficher le cache.

    return withTimeoutOrNull(timeoutMs) {
        loadDashboardParallel(userId)
    }
}

/**
 * Simulation d'un service lent
 */
suspend fun fetchUserSlow(userId: Int): User {
    println("  [fetchUserSlow] Début (très lent)")
    delay(2000) // Très lent
    return User(userId, "Slow-User-$userId", "slow@example.com")
}

suspend fun loadDashboardSlow(userId: Int): Dashboard = coroutineScope {
    val startTime = System.currentTimeMillis()

    val userDeferred = async { fetchUserSlow(userId) }
    val statsDeferred = async { fetchStats(userId) }

    val user = userDeferred.await()
    val stats = statsDeferred.await()
    // [ATTENTION FORMATEUR] ici computeScore est appelé sans withContext(Dispatchers.Default),
    // contrairement à loadDashboardParallel.
    val score = computeScore(stats)

    val loadTime = System.currentTimeMillis() - startTime
    Dashboard(user, stats, score, loadTime)
}

fun demo04_timeout() = runBlocking {
    println("\n=== Demo 04: Version avec Timeout ===")

    // Cas 1: Succès dans les temps
    println("Cas 1: Chargement rapide avec timeout 1000ms")
    val dashboard1 = loadDashboardWithTimeout(1, 1000)
    if (dashboard1 != null) {
        println("✓ Succès: ${dashboard1.user.name}")
    } else {
        println("✗ Timeout atteint")
    }

    // Cas 2: Timeout dépassé
    println("\nCas 2: Chargement lent avec timeout 500ms")
    val dashboard2 = withTimeoutOrNull(500) {
        loadDashboardSlow(2)
    }
    if (dashboard2 != null) {
        println("✓ Succès: ${dashboard2.user.name}")
    } else {
        println("✗ Timeout atteint (normal)")
    }
}

// =============================================================================
// 5. VERSION AVEC SUPERVISORSCOPE
// =============================================================================

/**
 * Chargement avec supervisorScope
 * Permet de continuer même si une partie échoue
 */
suspend fun loadDashboardWithSupervisor(userId: Int): Dashboard? = supervisorScope {
    println("\n[Supervisor] Chargement avec gestion d'erreur indépendante")

    val startTime = System.currentTimeMillis()

    var user: User? = null
    var stats: Stats? = null

    // [ATTENTION FORMATEUR] var partagées modifiées depuis des launch : ça marche ici (un seul thread),
    // mais c'est une course de données sur Dispatchers.Default et ça impose les !!. Forme idiomatique :
    // val u = async { fetchUser(id) } dans supervisorScope, puis try { u.await() } catch (...) { null }.
    // [PIÈGE] catch (e: Exception) dans chaque launch avale aussi CancellationException.
    // Lancement des tâches avec gestion d'erreur individuelle
    val userJob = launch {
        try {
            user = fetchUser(userId)
        } catch (e: Exception) {
            println("  Erreur user: ${e.message}")
        }
    }

    val statsJob = launch {
        try {
            stats = fetchStats(userId)
        } catch (e: Exception) {
            println("  Erreur stats: ${e.message}")
        }
    }

    // Attendre les deux
    userJob.join()
    statsJob.join()

    // Vérifier si on a les données nécessaires
    if (user == null || stats == null) {
        println("[Supervisor] Données incomplètes")
        return@supervisorScope null
    }

    val score = computeScore(stats!!)
    val loadTime = System.currentTimeMillis() - startTime

    println("[Supervisor] Chargement terminé en ${loadTime}ms")

    Dashboard(user!!, stats!!, score, loadTime)
}

/**
 * Simulation d'échecs
 */
suspend fun fetchUserFailure(userId: Int): User {
    delay(200)
    throw RuntimeException("API User indisponible")
}

suspend fun loadDashboardWithFailure(userId: Int): Dashboard? = supervisorScope {
    val startTime = System.currentTimeMillis()

    var user: User? = null
    var stats: Stats? = null

    launch {
        try {
            user = fetchUserFailure(userId)
        } catch (e: Exception) {
            println("  ✗ Erreur user: ${e.message}")
        }
    }

    launch {
        try {
            stats = fetchStats(userId)
        } catch (e: Exception) {
            println("  ✗ Erreur stats: ${e.message}")
        }
    }

    // [ATTENTION FORMATEUR] delay(500) au lieu de join() : on parie sur la durée des appels. Si
    // fetchStats dépassait 500 ms, le dashboard serait déclaré incomplet à tort. Garder les Job et join().
    delay(500)

    if (user == null || stats == null) {
        println("  Impossible de charger le dashboard complet")
        return@supervisorScope null
    }

    val score = computeScore(stats!!)
    val loadTime = System.currentTimeMillis() - startTime
    Dashboard(user!!, stats!!, score, loadTime)
}

fun demo05_supervisor() = runBlocking {
    println("\n=== Demo 05: Version avec supervisorScope ===")
    // [ANDROID] échec partiel = afficher l'écran avec une carte « stats indisponibles » plutôt
    // qu'une erreur globale : un UiState avec des champs nullables ou un état par bloc.

    // Cas 1: Succès
    println("Cas 1: Chargement normal")
    val dashboard1 = loadDashboardWithSupervisor(1)
    if (dashboard1 != null) {
        println("✓ Dashboard chargé: ${dashboard1.user.name}")
    } else {
        println("✗ Échec du chargement")
    }

    // Cas 2: Avec échec
    println("\nCas 2: Chargement avec échec")
    val dashboard2 = loadDashboardWithFailure(2)
    if (dashboard2 != null) {
        println("✓ Dashboard chargé: ${dashboard2.user.name}")
    } else {
        println("✗ Échec du chargement (normal)")
    }
}

// =============================================================================
// 6. VERSION AVEC ANNULATION
// =============================================================================

/**
 * Chargement annulable
 */
fun demo06_cancellation() = runBlocking {
    println("\n=== Demo 06: Annulation ===")
    // [ANDROID] équivalent Android : l'utilisateur quitte l'écran, onCleared() annule viewModelScope
    // et les deux appels Retrofit sont annulés (OkHttp annule la requête HTTP).

    val job = launch {
        try {
            val dashboard = loadDashboardParallel(1)
            println("Dashboard chargé: ${dashboard.user.name}")
        } catch (e: CancellationException) {
            println("✗ Chargement annulé")
            throw e
        }
    }

    delay(250) // Annuler avant la fin
    println("Annulation du chargement...")
    job.cancel()
    job.join()
}

/**
 * Chargement avec vérification de cancellation
 */
suspend fun loadDashboardCancellable(userId: Int): Dashboard = coroutineScope {
    val startTime = System.currentTimeMillis()

    // [ATTENTION FORMATEUR] ces ensureActive() en début d'async n'apportent presque rien :
    // fetchUser/fetchStats appellent delay(), déjà annulable. ensureActive() sert dans une boucle CPU,
    // et computeScore (Thread.sleep) reste, lui, non annulable.
    // [SAUTER] demo07 n'apporte pas grand-chose de plus que demo06.
    val userDeferred = async {
        ensureActive() // Vérifier l'annulation
        fetchUser(userId)
    }

    val statsDeferred = async {
        ensureActive()
        fetchStats(userId)
    }

    val user = userDeferred.await()
    val stats = statsDeferred.await()

    ensureActive()
    val score = computeScore(stats)

    val loadTime = System.currentTimeMillis() - startTime
    Dashboard(user, stats, score, loadTime)
}

fun demo07_cancellableWithCheck() = runBlocking {
    println("\n=== Demo 07: Annulation avec vérification ===")

    val job = launch {
        try {
            val dashboard = loadDashboardCancellable(1)
            println("✓ Dashboard chargé: ${dashboard.user.name}")
        } catch (e: CancellationException) {
            // [PIÈGE] pas de throw e ici, contrairement à demo06 : à faire remarquer.
            println("✗ Chargement annulé (vérifié avec ensureActive)")
        }
    }

    delay(150)
    job.cancel()
    job.join()
}

// =============================================================================
// 7. VERSION COMPLÈTE (AVEC TOUTES LES FONCTIONNALITÉS)
// =============================================================================

/**
 * Version complète avec:
 * - Chargement parallèle
 * - Timeout
 * - Gestion d'erreur avec supervisorScope
 * - Annulation
 * - Logging détaillé
 */
// [À DIRE] présenter cette section comme du « code à relire » : laisser les stagiaires chercher
// ce qui cloche avant de commenter (voir les marqueurs ci-dessous).
// [ANDROID] DashboardResult joue le rôle du UiState : préférer une sealed interface
// (Loading / Success / Error) à des booléens success / cancelled qui peuvent se contredire.
data class DashboardResult(
    val dashboard: Dashboard?,
    val success: Boolean,
    val error: String? = null,
    val cancelled: Boolean = false
)

suspend fun loadDashboardComplete(
    userId: Int,
    timeoutMs: Long = 5000
): DashboardResult = supervisorScope {
    println("\n[Complete] Chargement complet pour user $userId")

    try {
        val dashboard = withTimeoutOrNull(timeoutMs) {
            val startTime = System.currentTimeMillis()

            // [À DIRE] ici les catch relancent bien CancellationException avant catch (e: Exception) :
            // c'est la bonne forme, à comparer avec la section 5.
            // Chargement parallèle avec gestion d'erreur
            var user: User? = null
            var stats: Stats? = null
            var userError: String? = null
            var statsError: String? = null

            val userJob = launch {
                try {
                    ensureActive()
                    user = fetchUser(userId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    userError = e.message
                    println("  ✗ Erreur user: ${e.message}")
                }
            }

            val statsJob = launch {
                try {
                    ensureActive()
                    stats = fetchStats(userId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    statsError = e.message
                    println("  ✗ Erreur stats: ${e.message}")
                }
            }

            userJob.join()
            statsJob.join()

            if (user == null || stats == null) {
                val errors = listOfNotNull(userError, statsError)
                // [À DIRE] cette exception traverse withTimeoutOrNull et finit dans le
                // catch (e: Exception) du bas -> DashboardResult avec l'erreur.
                throw RuntimeException("Données incomplètes: ${errors.joinToString(", ")}")
            }

            ensureActive()
            val score = withContext(Dispatchers.Default) {
                computeScore(stats!!)
            }

            val loadTime = System.currentTimeMillis() - startTime
            Dashboard(user!!, stats!!, score, loadTime)
        }

        if (dashboard == null) {
            println("[Complete] ✗ Timeout atteint")
            DashboardResult(null, false, "Timeout de ${timeoutMs}ms dépassé")
        } else {
            println("[Complete] ✓ Succès en ${dashboard.loadTimeMs}ms")
            DashboardResult(dashboard, true)
        }

    // [ATTENTION FORMATEUR] ce catch avale l'annulation et renvoie une valeur, contre la règle
    // « toujours relancer ». En pratique la valeur est perdue : le supervisorScope annulé relance
    // CancellationException, donc « Détecté comme annulé » (Cas 3) ne s'affiche pas. Le champ
    // cancelled est inutile et le « Production-ready » du résumé est faux.
    } catch (e: CancellationException) {
        println("[Complete] ✗ Annulé")
        DashboardResult(null, false, cancelled = true)
    } catch (e: Exception) {
        println("[Complete] ✗ Erreur: ${e.message}")
        DashboardResult(null, false, e.message)
    }
}

fun demo08_complete() = runBlocking {
    println("\n=== Demo 08: Version Complète ===")
    // [ANDROID] cas 3 = l'écran fermé pendant le chargement : il n'y a plus d'UI à mettre à jour,
    // donc aucun état « annulé » à produire ; laisser l'annulation se propager.

    // Cas 1: Succès
    println("\n--- Cas 1: Chargement réussi ---")
    val result1 = loadDashboardComplete(1, timeoutMs = 2000)
    if (result1.success && result1.dashboard != null) {
        println("✓ Succès:")
        println("  User: ${result1.dashboard.user.name}")
        println("  Score: ${result1.dashboard.score.totalScore}")
        println("  Temps: ${result1.dashboard.loadTimeMs}ms")
    }

    // Cas 2: Timeout
    println("\n--- Cas 2: Timeout ---")
    val result2 = loadDashboardComplete(2, timeoutMs = 100)
    if (!result2.success) {
        println("✗ Échec: ${result2.error}")
    }

    // Cas 3: Annulation
    println("\n--- Cas 3: Annulation ---")
    val job = launch {
        val result = loadDashboardComplete(3, timeoutMs = 5000)
        if (result.cancelled) {
            println("Détecté comme annulé")
        }
    }
    delay(150)
    job.cancel()
    job.join()
}

// =============================================================================
// 8. CHARGEMENT MULTIPLE (BONUS)
// =============================================================================

/**
 * Charger plusieurs dashboards en parallèle
 */
suspend fun loadMultipleDashboards(userIds: List<Int>): List<DashboardResult> = coroutineScope {
    println("\n[Multiple] Chargement de ${userIds.size} dashboards")
    // [SAUTER] bonus pour les plus rapides : awaitAll() fonctionne ici sans annuler les autres
    // parce que loadDashboardComplete ne lève jamais (les erreurs sont dans DashboardResult).

    userIds.map { userId ->
        async {
            loadDashboardComplete(userId, timeoutMs = 2000)
        }
    }.awaitAll()
}

fun demo09_multiple() = runBlocking {
    println("\n=== Demo 09: Chargement Multiple ===")

    val userIds = listOf(1, 2, 3, 4, 5)

    val time = measureTimeMillis {
        val results = loadMultipleDashboards(userIds)

        println("\n📊 Résultats:")
        results.forEachIndexed { index, result ->
            val status = if (result.success) "✓" else "✗"
            val info = if (result.success) {
                result.dashboard?.user?.name ?: "?"
            } else {
                result.error ?: "Erreur"
            }
            println("  $status User ${userIds[index]}: $info")
        }

        val successCount = results.count { it.success }
        println("\nTaux de succès: $successCount/${results.size}")
    }

    println("Temps total: ${time}ms")
}

// =============================================================================
// 10. TESTS DE PERFORMANCE
// =============================================================================

fun demo10_performanceTest() = runBlocking {
    println("\n=== Demo 10: Tests de Performance ===")
    // [SAUTER] répète demo03.
    // [ANDROID] mesure fiable au TP08 : runTest + currentTime (temps virtuel, ~300 ms pour la version
    // parallèle sans calcul) à condition de retirer Thread.sleep et d'injecter le dispatcher.

    val iterations = 3

    println("\n1. Version Séquentielle:")
    val avgSeq = (1..iterations).map {
        measureTimeMillis {
            loadDashboardSequential(it)
        }
    }.average()
    println("   Moyenne: ${avgSeq.toInt()}ms")

    println("\n2. Version Parallèle:")
    val avgPar = (1..iterations).map {
        measureTimeMillis {
            loadDashboardParallel(it)
        }
    }.average()
    println("   Moyenne: ${avgPar.toInt()}ms")

    println("\n3. Version Complète:")
    val avgComplete = (1..iterations).map {
        measureTimeMillis {
            loadDashboardComplete(it)
        }
    }.average()
    println("   Moyenne: ${avgComplete.toInt()}ms")

    println("\n📊 Comparaison:")
    println("   Séquentiel: ${avgSeq.toInt()}ms (baseline)")
    println("   Parallèle: ${avgPar.toInt()}ms (${((avgSeq - avgPar) / avgSeq * 100).toInt()}% plus rapide)")
    println("   Complet: ${avgComplete.toInt()}ms")
}

// =============================================================================
// RÉSUMÉ DU TP
// =============================================================================

// [ATTENTION FORMATEUR] le résumé annonce « IO pour réseau » alors que le code n'utilise jamais
// Dispatchers.IO (inutile avec Retrofit/Room en suspend), et « Production-ready » pour une version
// qui avale l'annulation. Nuancer à l'oral.
// [SAUTER] à distribuer.
fun printTPSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ DU TP: Chargement de Dashboard")
    println("=".repeat(80))

    println("""
    ÉNONCÉ:
    Charger un dashboard utilisateur avec 3 opérations:
    1. fetchUser(userId) - 200ms (I/O)
    2. fetchStats(userId) - 300ms (I/O)
    3. computeScore(stats) - 100ms (CPU)

    SOLUTIONS IMPLÉMENTÉES:

    1. Version Séquentielle (~600ms):
       - Appels successifs
       - Simple mais lent
       - user → stats → score

    2. Version Parallèle (~400ms):
       - user et stats en parallèle avec async
       - score après (dépend de stats)
       - 33% plus rapide!

    3. Version avec Timeout:
       - withTimeoutOrNull pour limite de temps
       - Retourne null si timeout dépassé
       - Annule automatiquement les opérations

    4. Version avec supervisorScope:
       - Gestion d'erreur indépendante
       - Continue même si une partie échoue
       - Retourne null si données incomplètes

    5. Version avec Annulation:
       - Utilise ensureActive()
       - Répond rapidement à cancel()
       - Nettoyage propre

    6. Version Complète:
       - Combine toutes les fonctionnalités
       - Parallèle + Timeout + Erreur + Annulation
       - Production-ready

    POINTS CLÉS:

    ✓ Identifier les dépendances entre opérations
    ✓ Paralléliser les opérations indépendantes
    ✓ Utiliser le bon dispatcher (IO pour réseau, Default pour CPU)
    ✓ Gérer les timeouts
    ✓ Gérer les erreurs avec supervisorScope
    ✓ Rendre le code annulable

    GAIN DE PERFORMANCE:
    Séquentiel: 600ms
    Parallèle: 400ms
    Gain: 200ms (33%)

    ARCHITECTURE:
        ┌─────────────┐
        │  Dashboard  │
        └──────┬──────┘
               │
        ┌──────┴──────┐
        │ Parallel    │
        │ Loading     │
        └──────┬──────┘
               │
        ┌──────┼──────┐
        │      │      │
     fetchUser │  fetchStats
               │
               └─ computeScore

    LEÇONS APPRISES:
    1. Le parallélisme améliore significativement les performances
    2. withTimeoutOrNull est plus simple que withTimeout + try/catch
    3. supervisorScope permet de continuer malgré des échecs partiels
    4. ensureActive() rend le code coopératif
    5. async/await pour les résultats, launch pour les effets de bord
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(18) + "TP COROUTINES - CHARGEMENT DE DASHBOARD")
    println("=" + "=".repeat(78) + "=")

    // Démonstrations
    demo01_sequential()
    demo02_parallel()
    demo03_comparison()
    demo04_timeout()
    demo05_supervisor()
    demo06_cancellation()
    demo07_cancellableWithCheck()
    demo08_complete()
    demo09_multiple()
    demo10_performanceTest()

    // Résumé
    printTPSummary()

    println("\n" + "=".repeat(80))
    println("Fin du TP")
    println("=".repeat(80))
}
