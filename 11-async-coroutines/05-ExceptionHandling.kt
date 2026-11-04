// ==============================================================================
// FICHE FORMATEUR — Exceptions dans les coroutines
// Objectif : savoir par où remonte une exception (launch, async, scopes) et où l'attraper.
// À présenter (dans l'ordre) :
//   1. demo10 (partie coroutineScope) : un enfant qui échoue annule parent et frères
//   2. demo14 : try/catch DANS la coroutine, autour de l'appel suspend
//   3. demo09 : supervisorScope, échecs indépendants
//   4. demo11 : CoroutineExceptionHandler = filet de sécurité sur une coroutine racine
//   5. demo22 + demo23 : pattern Result et piège runCatching -> repository du TP04
// Lien Android : viewModelScope repose sur un SupervisorJob ; try/catch dans
//   viewModelScope.launch pour alimenter l'état d'erreur du StateFlow (TP07) ; repository
//   qui renvoie un Result (TP04) ; une exception non attrapée dans viewModelScope fait planter l'appli.
// Pièges / questions fréquentes : « async garde l'exception jusqu'à await() » n'est vrai que
//   pour une coroutine racine ; handler ignoré sur un enfant ; runCatching avale CancellationException.
// Si peu de temps : demo10, demo14, demo11, demo22-23 (~15 min). Sauter demo03-08, demo12-13,
//   demo15-18, demo20-21, demo24-25. ATTENTION : main() plante dès demo01 (voir marqueurs),
//   lancer les démos une par une.
// ==============================================================================
/**
 * 05-ExceptionHandling.kt
 *
 * Exemples sur la gestion des exceptions dans les coroutines
 * - Exceptions dans launch vs async
 * - CoroutineExceptionHandler
 * - supervisorScope pour échecs indépendants
 * - try/catch dans les coroutines
 * - Propagation des exceptions
 * - Exception aggregation
 */

import kotlinx.coroutines.*

// =============================================================================
// 1. EXCEPTIONS DANS LAUNCH VS ASYNC
// =============================================================================

/**
 * Exception dans launch: propage immédiatement
 */
fun demo01_exceptionInLaunch() = runBlocking {
    println("\n=== Demo 01: Exception dans launch ===")
    // [À DIRE] launch rend la main tout de suite : l'exception remonte par la hiérarchie des Job,
    // pas par la pile d'appels. Le try/catch autour ne peut pas la voir.
    // [ATTENTION FORMATEUR] l'enfant qui échoue annule runBlocking : delay(1000) lève alors une
    // CancellationException, attrapée par ce catch (le message s'affiche donc !), puis runBlocking
    // relance la RuntimeException et main() s'arrête : aucune autre démo ne s'exécute.

    try {
        launch {
            println("Launch: début")
            delay(500)
            throw RuntimeException("Erreur dans launch!")
        }
        delay(1000)
    } catch (e: Exception) {
        // Cette exception ne sera PAS capturée ici!
        println("Exception capturée? Non!")
    }

    delay(1500)
    println("Note: L'exception dans launch se propage au parent")
}

/**
 * Exception dans async: encapsulée jusqu'à await()
 */
fun demo02_exceptionInAsync() = runBlocking {
    println("\n=== Demo 02: Exception dans async ===")
    // [ATTENTION FORMATEUR] faux ici : cet async est enfant de runBlocking, son échec annule le
    // parent dès 500 ms, sans attendre await(), et l'exception remonte jusqu'à main(). L'exception
    // n'attend await() que pour une coroutine racine (scope à SupervisorJob, enfant de supervisorScope).
    // [ANDROID] en pratique, async s'écrit dans coroutineScope { } (bonus TP04) : un échec annule
    // l'autre appel et coroutineScope relance l'exception ; on attrape autour du coroutineScope.

    val deferred = async {
        println("Async: début")
        delay(500)
        throw RuntimeException("Erreur dans async!")
    }

    delay(1000)
    println("L'exception n'est pas encore levée...")

    try {
        deferred.await() // Exception levée ICI
    } catch (e: Exception) {
        println("Exception capturée: ${e.message}")
    }
}

/**
 * Comparaison launch vs async
 */
fun demo03_launchVsAsyncException() = runBlocking {
    println("\n=== Demo 03: launch vs async - Exceptions ===")
    // [ATTENTION FORMATEUR] même défaut que demo02 pour async ; et launch(handler) est un enfant
    // de runBlocking, donc le handler est ignoré et l'exception remonte quand même.
    // [SAUTER]

    // async: contrôle sur l'exception
    println("Avec async:")
    val deferred = async {
        delay(500)
        throw IllegalStateException("Async error")
    }

    try {
        deferred.await()
        println("  Succès")
    } catch (e: Exception) {
        println("  Exception gérée: ${e.message}")
    }

    // launch: exception se propage
    println("\nAvec launch:")
    val handler = CoroutineExceptionHandler { _, exception ->
        println("  Handler capturé: ${exception.message}")
    }

    launch(handler) {
        delay(500)
        throw IllegalStateException("Launch error")
    }

    delay(1000)
}

/**
 * Plusieurs async avec exceptions
 */
fun demo04_multipleAsyncExceptions() = runBlocking {
    println("\n=== Demo 04: Plusieurs async avec exceptions ===")
    // [ATTENTION FORMATEUR] deferred2 échoue à 300 ms et annule runBlocking avec les autres async :
    // les try/catch individuels ne suffisent pas. Le comportement voulu exige supervisorScope { }.
    // [SAUTER]

    val deferred1 = async {
        delay(500)
        42
    }

    val deferred2 = async {
        delay(300)
        throw RuntimeException("Erreur 2")
    }

    val deferred3 = async {
        delay(700)
        100
    }

    // Récupération individuelle des résultats
    val result1 = try {
        deferred1.await()
    } catch (e: Exception) {
        println("Erreur 1: ${e.message}"); null
    }

    val result2 = try {
        deferred2.await()
    } catch (e: Exception) {
        println("Erreur 2: ${e.message}"); null
    }

    val result3 = try {
        deferred3.await()
    } catch (e: Exception) {
        println("Erreur 3: ${e.message}"); null
    }

    println("Résultats: $result1, $result2, $result3")
}

// =============================================================================
// 2. COROUTINEEXCEPTIONHANDLER
// =============================================================================

/**
 * Utilisation basique de CoroutineExceptionHandler
 */
fun demo05_coroutineExceptionHandler() = runBlocking {
    println("\n=== Demo 05: CoroutineExceptionHandler ===")
    // [ATTENTION FORMATEUR] launch(handler) sous runBlocking n'est pas une racine : handler ignoré,
    // plantage. Pour le montrer vraiment : CoroutineScope(SupervisorJob()).launch(handler) { ... }
    // ou un enfant direct de supervisorScope (demo11).
    // [ANDROID] CoroutineExceptionHandler sert à journaliser (Crashlytics…), pas à reprendre :
    // la coroutine est déjà finie. Pour l'UI : try/catch dans viewModelScope.launch.

    val handler = CoroutineExceptionHandler { context, exception ->
        println("Exception capturée par handler: ${exception.message}")
        println("Context: $context")
    }

    val job = launch(handler) {
        println("Coroutine commence")
        delay(500)
        throw IllegalArgumentException("Quelque chose s'est mal passé")
    }

    job.join()
}

/**
 * Handler doit être au niveau racine
 */
fun demo06_handlerScope() = runBlocking {
    println("\n=== Demo 06: Scope du handler ===")
    // [ATTENTION FORMATEUR] le premier launch(handler) n'est pas racine non plus (enfant de
    // runBlocking) : le « fonctionne » est faux ici et la démo plante. La règle énoncée reste juste.
    // [SAUTER] demo06 à demo08 : mêmes limites ; demo08 = handler2 remplace handler1 (même clé).

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Root handler: ${exception.message}")
    }

    // Handler au niveau racine (fonctionne)
    launch(handler) {
        println("Root coroutine")
        throw RuntimeException("Erreur root")
    }

    delay(1000)

    // Handler dans une coroutine enfant (NE fonctionne PAS)
    launch(handler) {
        println("Parent coroutine")
        launch {
            println("Child coroutine")
            throw RuntimeException("Erreur child")
        }
    }

    delay(1000)
    println("Note: Le handler enfant n'a pas capturé l'exception")
}

/**
 * Handler avec informations détaillées
 */
fun demo07_detailedHandler() = runBlocking {
    println("\n=== Demo 07: Handler détaillé ===")

    val handler = CoroutineExceptionHandler { context, exception ->
        println("=== Exception capturée ===")
        println("Type: ${exception::class.simpleName}")
        println("Message: ${exception.message}")
        println("CoroutineName: ${context[CoroutineName]}")
        println("Job: ${context[Job]}")
        println("Stack trace: ${exception.stackTraceToString().take(200)}...")
    }

    launch(handler + CoroutineName("MyCoroutine")) {
        delay(500)
        throw IllegalStateException("État invalide")
    }

    delay(1000)
}

/**
 * Multiples handlers
 */
fun demo08_multipleHandlers() = runBlocking {
    println("\n=== Demo 08: Plusieurs handlers ===")

    val handler1 = CoroutineExceptionHandler { _, exception ->
        println("Handler 1: ${exception.message}")
    }

    val handler2 = CoroutineExceptionHandler { _, exception ->
        println("Handler 2: ${exception.message}")
    }

    // Seul le dernier handler est utilisé
    launch(handler1 + handler2) {
        throw RuntimeException("Test")
    }

    delay(1000)
    println("Note: Seul le dernier handler (2) est exécuté")
}

// =============================================================================
// 3. SUPERVISORSCOPE POUR ÉCHECS INDÉPENDANTS
// =============================================================================

/**
 * supervisorScope: les enfants échouent indépendamment
 */
fun demo09_supervisorScopeBasic() = runBlocking {
    println("\n=== Demo 09: supervisorScope basique ===")
    // [À DIRE] dans supervisorScope, chaque enfant gère ses erreurs : une exception non attrapée
    // va au CoroutineExceptionHandler, sinon au handler par défaut (trace imprimée sur la JVM).
    // [ANDROID] viewModelScope = SupervisorJob + Dispatchers.Main.immediate : deux chargements du
    // même ViewModel ne s'annulent pas entre eux. Mais une exception non attrapée y fait planter l'appli.

    supervisorScope {
        launch {
            delay(500)
            println("Tâche 1: succès")
        }

        launch {
            delay(300)
            println("Tâche 2: échec")
            throw RuntimeException("Erreur tâche 2")
        }

        launch {
            delay(700)
            println("Tâche 3: succès (continue malgré erreur tâche 2)")
        }
    }

    println("supervisorScope terminé")
}

/**
 * Comparaison coroutineScope vs supervisorScope
 */
fun demo10_coroutineScopeVsSupervisorScope() = runBlocking {
    println("\n=== Demo 10: coroutineScope vs supervisorScope ===")

    // [À DIRE] démo centrale : l'échec de B annule A et C, puis coroutineScope relance l'erreur
    // -> un seul try/catch autour. C'est la forme à utiliser avec async (bonus TP04).
    // coroutineScope: une erreur annule tout
    println("Avec coroutineScope:")
    try {
        coroutineScope {
            launch {
                delay(500)
                println("  Task A")
            }

            launch {
                delay(100)
                throw RuntimeException("Error in B")
            }

            launch {
                delay(1000)
                println("  Task C (jamais affiché)")
            }
        }
    } catch (e: Exception) {
        println("  Exception: ${e.message}")
    }

    delay(500)

    // [PIÈGE] ici B attrape sa propre erreur : la démo marcherait aussi avec coroutineScope.
    // Pour montrer la vraie différence, retirer le try/catch de B (A et C continuent quand même).
    // supervisorScope: les autres continuent
    println("\nAvec supervisorScope:")
    supervisorScope {
        launch {
            delay(500)
            println("  Task A")
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
            println("  Task C (affiché!)")
        }
    }
}

/**
 * supervisorScope avec handler
 */
fun demo11_supervisorScopeWithHandler() = runBlocking {
    println("\n=== Demo 11: supervisorScope avec handler ===")
    // [À DIRE] ici le handler fonctionne : les enfants directs d'un supervisorScope sont traités
    // comme des racines pour les exceptions.

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Handler: ${exception.message}")
    }

    supervisorScope {
        launch(handler) {
            delay(300)
            throw IllegalArgumentException("Erreur 1")
        }

        launch(handler) {
            delay(500)
            println("Tâche 2 réussie")
        }

        launch(handler) {
            delay(600)
            throw IllegalStateException("Erreur 3")
        }

        launch(handler) {
            delay(900)
            println("Tâche 4 réussie")
        }
    }

    println("Toutes les tâches terminées (succès ou échec)")
}

/**
 * SupervisorJob pour une hiérarchie supervised
 */
fun demo12_supervisorJob() = runBlocking {
    println("\n=== Demo 12: SupervisorJob ===")
    // [ANDROID] CoroutineScope(SupervisorJob() + Dispatchers.Default) = scope « applicatif » qu'on
    // injecte (Hilt) pour du travail qui dépasse un écran, à la place de GlobalScope. Préférer
    // viewModelScope / lifecycleScope quand ils suffisent ; ce scope-là, il faut l'annuler soi-même.
    // [SAUTER]

    val supervisor = SupervisorJob()
    val scope = CoroutineScope(Dispatchers.Default + supervisor)

    scope.launch {
        delay(300)
        println("Job 1: succès")
    }

    scope.launch {
        delay(200)
        throw RuntimeException("Job 2: erreur")
    }

    scope.launch {
        delay(500)
        println("Job 3: succès (continue)")
    }

    delay(1000)
    supervisor.cancel()
}

// =============================================================================
// 4. TRY/CATCH DANS LES COROUTINES
// =============================================================================

/**
 * try/catch basique
 */
fun demo13_basicTryCatch() = runBlocking {
    println("\n=== Demo 13: try/catch basique ===")
    // [SAUTER] try/catch classique, rien de propre aux coroutines.

    launch {
        try {
            println("Tentative d'opération...")
            delay(500)
            throw IllegalStateException("Opération échouée")
        } catch (e: IllegalStateException) {
            println("Exception capturée: ${e.message}")
        } catch (e: Exception) {
            println("Autre exception: ${e.message}")
        } finally {
            println("Nettoyage")
        }
    }

    delay(1000)
}

/**
 * try/catch avec fonctions suspend
 */
suspend fun riskyOperation(): String {
    delay(500)
    throw RuntimeException("Opération risquée a échoué")
}

fun demo14_tryCatchSuspend() = runBlocking {
    println("\n=== Demo 14: try/catch avec suspend ===")
    // [À DIRE] un appel suspend se protège comme un appel normal : le try/catch est autour de
    // l'appel, à l'intérieur de la coroutine.
    // [ANDROID] forme type du ViewModel (TP07) : viewModelScope.launch { _uiState.value = try {
    // UiState.Success(repo.load()) } catch (e: IOException) { UiState.Error(...) } }.
    // Attraper des exceptions précises plutôt qu'Exception.

    launch {
        val result = try {
            riskyOperation()
        } catch (e: Exception) {
            println("Erreur gérée: ${e.message}")
            "Valeur par défaut"
        }

        println("Résultat: $result")
    }

    delay(1000)
}

/**
 * Placement du try/catch
 */
fun demo15_tryCatchPlacement() = runBlocking {
    println("\n=== Demo 15: Placement du try/catch ===")

    // [ATTENTION FORMATEUR] cette première partie plante runBlocking (l'enfant non attrapé annule le
    // parent) et main() s'arrête là. Montrer le code sans l'exécuter, ou n'exécuter que la 2e partie.
    // [SAUTER] déjà vu en demo01.
    // try/catch AUTOUR de launch: NE fonctionne PAS
    println("try/catch autour de launch (ne fonctionne PAS):")
    try {
        launch {
            delay(300)
            throw RuntimeException("Erreur launch")
        }
    } catch (e: Exception) {
        println("  Pas capturé ici!")
    }

    delay(500)

    // try/catch DANS launch: fonctionne
    println("\ntry/catch dans launch (fonctionne):")
    launch {
        try {
            delay(300)
            throw RuntimeException("Erreur launch")
        } catch (e: Exception) {
            println("  Capturé: ${e.message}")
        }
    }

    delay(500)
}

/**
 * try/catch avec async
 */
fun demo16_tryCatchAsync() = runBlocking {
    println("\n=== Demo 16: try/catch avec async ===")

    // [ATTENTION FORMATEUR] option 1 : même défaut que demo02 (async enfant de runBlocking, son
    // échec annule le parent avant même await()). L'option 2 est correcte.
    // [SAUTER]
    // Option 1: try/catch autour de await
    val deferred1 = async {
        delay(300)
        throw RuntimeException("Erreur async")
    }

    try {
        deferred1.await()
    } catch (e: Exception) {
        println("Option 1: ${e.message}")
    }

    // Option 2: try/catch dans async
    val deferred2 = async {
        try {
            delay(300)
            throw RuntimeException("Erreur async")
        } catch (e: Exception) {
            println("Option 2: ${e.message}")
            null
        }
    }

    val result = deferred2.await()
    println("Résultat: $result")
}

// =============================================================================
// 5. PROPAGATION DES EXCEPTIONS
// =============================================================================

/**
 * Propagation parent-enfant
 */
fun demo17_exceptionPropagation() = runBlocking {
    println("\n=== Demo 17: Propagation des exceptions ===")
    // [ATTENTION FORMATEUR] handler non racine (enfant de runBlocking) : ignoré, plantage. Le
    // comportement des frères (enfants 1 et 3 annulés) est juste.
    // [SAUTER] déjà vu avec demo10.

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Handler capturé: ${exception.message}")
    }

    launch(handler) {
        println("Parent")

        launch {
            println("  Enfant 1")
            delay(500)
            println("  Enfant 1 terminé")
        }

        launch {
            println("  Enfant 2")
            delay(300)
            throw RuntimeException("Erreur enfant 2")
        }

        launch {
            println("  Enfant 3")
            delay(700)
            println("  Enfant 3 (annulé car frère a échoué)")
        }
    }

    delay(1500)
}

/**
 * Exception ne propage pas avec SupervisorJob
 */
fun demo18_supervisorPropagation() = runBlocking {
    println("\n=== Demo 18: Pas de propagation avec Supervisor ===")
    // [ATTENTION FORMATEUR] coroutineContext + supervisor remplace le Job de runBlocking : ces
    // coroutines ne sont plus ses enfants (concurrence structurée cassée, d'où delay + cancel
    // manuels). supervisorScope { } ou SupervisorJob(coroutineContext[Job]) serait correct.
    // [SAUTER]

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Handler: ${exception.message}")
    }

    val supervisor = SupervisorJob()

    with(CoroutineScope(coroutineContext + supervisor)) {
        launch(handler) {
            println("Job 1")
            delay(300)
            throw RuntimeException("Erreur Job 1")
        }

        launch {
            println("Job 2")
            delay(500)
            println("Job 2 terminé (continue)")
        }

        launch {
            println("Job 3")
            delay(700)
            println("Job 3 terminé (continue)")
        }
    }

    delay(1000)
    supervisor.cancel()
}

/**
 * CancellationException ne propage pas
 */
fun demo19_cancellationDoesNotPropagate() = runBlocking {
    println("\n=== Demo 19: CancellationException ne propage pas ===")
    // [À DIRE] annuler un enfant ne fait pas échouer le parent : CancellationException est le seul
    // « échec » qui ne remonte pas.
    // [ANDROID] c'est ce qui permet d'annuler une recherche en cours (searchJob?.cancel()) sans
    // casser le reste du ViewModel.

    val parent = launch {
        val child = launch {
            try {
                delay(1000)
            } catch (e: CancellationException) {
                println("Enfant annulé")
                throw e
            }
        }

        delay(500)
        child.cancel()
        delay(500)
        println("Parent continue après annulation enfant")
    }

    parent.join()
}

// =============================================================================
// 6. EXCEPTION AGGREGATION
// =============================================================================

/**
 * Plusieurs exceptions en même temps
 */
fun demo20_multipleExceptions() = runBlocking {
    println("\n=== Demo 20: Plusieurs exceptions simultanées ===")
    // [SAUTER] demo20-21 : exceptions supprimées et cause, culture générale. Même limite : le
    // handler n'est pas racine, la démo plante.

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Exception principale: ${exception.message}")

        val suppressed = exception.suppressed
        if (suppressed.isNotEmpty()) {
            println("Exceptions supprimées:")
            suppressed.forEach {
                println("  - ${it.message}")
            }
        }
    }

    launch(handler) {
        launch {
            delay(100)
            throw RuntimeException("Erreur 1")
        }

        launch {
            delay(100)
            throw IllegalStateException("Erreur 2")
        }

        launch {
            delay(100)
            throw IllegalArgumentException("Erreur 3")
        }
    }

    delay(1000)
}

/**
 * Exception avec cause
 */
fun demo21_exceptionWithCause() = runBlocking {
    println("\n=== Demo 21: Exception avec cause ===")

    val handler = CoroutineExceptionHandler { _, exception ->
        println("Exception: ${exception.message}")
        println("Cause: ${exception.cause?.message}")
    }

    launch(handler) {
        try {
            throw IllegalStateException("Erreur bas niveau")
        } catch (e: IllegalStateException) {
            throw RuntimeException("Erreur haut niveau", e)
        }
    }

    delay(1000)
}

// =============================================================================
// 7. PATTERNS DE GESTION D'ERREUR
// =============================================================================

/**
 * Pattern: Result wrapper
 */
// [ATTENTION FORMATEUR] cette classe masque kotlin.Result dans tout le fichier (même nom), ce qui
// prête à confusion avec runCatching juste en dessous. Au TP04, on utilise kotlin.Result.
sealed class Result<out T> {
    data class Success<T>(val value: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}

suspend fun safeApiCall(shouldFail: Boolean): Result<String> {
    return try {
        delay(500)
        if (shouldFail) {
            throw RuntimeException("API call failed")
        }
        Result.Success("Data")
    // [ATTENTION FORMATEUR] catch (e: Exception) avale CancellationException : un écran fermé pendant
    // l'appel donne un Result.Error au lieu d'annuler. Ajouter avant :
    // catch (e: CancellationException) { throw e } — c'est l'exigence du TP04.
    } catch (e: Exception) {
        Result.Error(e)
    }
}

fun demo22_resultPattern() = runBlocking {
    println("\n=== Demo 22: Pattern Result ===")
    // [ANDROID] c'est la forme du repository du TP04 : suspend fun getTodos(): Result<List<Todo>>,
    // les exceptions techniques (IOException, HttpException) traduites en erreurs du domaine ;
    // le ViewModel fait fold / when et met à jour son StateFlow.

    launch {
        val result1 = safeApiCall(false)
        when (result1) {
            is Result.Success -> println("Succès: ${result1.value}")
            is Result.Error -> println("Erreur: ${result1.exception.message}")
        }

        val result2 = safeApiCall(true)
        when (result2) {
            is Result.Success -> println("Succès: ${result2.value}")
            is Result.Error -> println("Erreur: ${result2.exception.message}")
        }
    }

    delay(2000)
}

/**
 * Pattern: runCatching
 */
suspend fun riskyTask(): String {
    delay(500)
    if (Math.random() > 0.5) {
        throw RuntimeException("Random failure")
    }
    return "Success"
}

fun demo23_runCatchingPattern() = runBlocking {
    println("\n=== Demo 23: Pattern runCatching ===")
    // [PIÈGE] runCatching attrape Throwable, donc CancellationException aussi : à éviter tel quel
    // autour d'un appel suspend (repository TP04). Relancer : .onFailure { if (it is
    // CancellationException) throw it }, ou écrire un try/catch explicite.

    launch {
        val result = runCatching {
            riskyTask()
        }

        result
            .onSuccess { println("Succès: $it") }
            .onFailure { println("Échec: ${it.message}") }

        val valueOrDefault = result.getOrDefault("Valeur par défaut")
        println("Valeur: $valueOrDefault")
    }

    delay(1000)
}

/**
 * Pattern: Retry avec exception
 */
suspend fun retryableOperation(attempt: Int): String {
    delay(300)
    if (attempt < 3) {
        throw RuntimeException("Tentative $attempt échouée")
    }
    return "Succès"
}

suspend fun <T> retry(
    times: Int = 3,
    delayMs: Long = 1000,
    block: suspend (Int) -> T
): T {
    repeat(times - 1) { attempt ->
        try {
            return block(attempt + 1)
        // [ATTENTION FORMATEUR] même piège : l'annulation est comptée comme un échec et seul le
        // delay() suivant la relance. Il vaut mieux relancer CancellationException explicitement.
        } catch (e: Exception) {
            println("Tentative ${attempt + 1} échouée: ${e.message}")
            delay(delayMs)
        }
    }
    return block(times) // Dernière tentative
}

fun demo24_retryPattern() = runBlocking {
    println("\n=== Demo 24: Pattern Retry ===")
    // [SAUTER] demo24-25 : patterns avancés. Pour un Flow, retry / retryWhen existent déjà.

    try {
        val result = retry(times = 5, delayMs = 500) { attempt ->
            retryableOperation(attempt)
        }
        println("Résultat: $result")
    } catch (e: Exception) {
        println("Échec final: ${e.message}")
    }
}

/**
 * Pattern: Circuit breaker (simplifié)
 */
// [ATTENTION FORMATEUR] version jouet : compteurs non protégés (ni Mutex ni atomic) si appelée
// depuis plusieurs threads, pas d'état « semi-ouvert », et une annulation compte comme un échec.
class CircuitBreaker(private val threshold: Int = 3) {
    private var failureCount = 0
    private var isOpen = false

    suspend fun <T> execute(block: suspend () -> T): T {
        if (isOpen) {
            throw RuntimeException("Circuit breaker is OPEN")
        }

        return try {
            val result = block()
            failureCount = 0 // Reset on success
            result
        } catch (e: Exception) {
            failureCount++
            if (failureCount >= threshold) {
                isOpen = true
                println("Circuit breaker OPENED after $failureCount failures")
            }
            throw e
        }
    }

    fun reset() {
        isOpen = false
        failureCount = 0
    }
}

fun demo25_circuitBreakerPattern() = runBlocking {
    println("\n=== Demo 25: Pattern Circuit Breaker ===")

    val circuitBreaker = CircuitBreaker(threshold = 3)
    var attemptNumber = 0

    repeat(5) {
        try {
            val result = circuitBreaker.execute {
                attemptNumber++
                delay(100)
                if (attemptNumber < 4) {
                    throw RuntimeException("Service failure $attemptNumber")
                }
                "Success"
            }
            println("Résultat: $result")
        } catch (e: Exception) {
            println("Erreur: ${e.message}")
        }
    }
}

// =============================================================================
// RÉSUMÉ ET BONNES PRATIQUES
// =============================================================================

// [ATTENTION FORMATEUR] dans ce résumé, « async : exception encapsulée jusqu'à await() » n'est vrai
// que pour une coroutine racine, et « runCatching pour simplifier » contredit le piège
// CancellationException. Nuancer à l'oral.
// [SAUTER] à distribuer ; projeter seulement le tableau final.
fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Gestion des Exceptions")
    println("=".repeat(80))

    println("""
    1. EXCEPTIONS DANS LAUNCH VS ASYNC:

    launch:
    - Exception se propage immédiatement au parent
    - Annule le parent et les frères (sauf supervisorScope)
    - Utiliser CoroutineExceptionHandler pour capturer

    async:
    - Exception encapsulée dans le Deferred
    - Exception levée lors de l'appel à await()
    - Utiliser try/catch autour de await()

    2. COROUTINEEXCEPTIONHANDLER:

    Création:
        val handler = CoroutineExceptionHandler { context, exception ->
            // Gestion de l'exception
        }

    Utilisation:
        launch(handler) { ... }

    Important:
    - Doit être installé au niveau ROOT de la coroutine
    - Ne fonctionne PAS sur les coroutines enfants
    - Ne capture PAS CancellationException

    3. SUPERVISORSCOPE:

    - Les enfants échouent indépendamment
    - L'échec d'un enfant n'annule pas les autres
    - L'échec d'un enfant ne propage pas au parent

    Utilisation:
        supervisorScope {
            launch { /* peut échouer */ }
            launch { /* continue malgré échec du frère */ }
        }

    SupervisorJob:
        val supervisor = SupervisorJob()
        val scope = CoroutineScope(context + supervisor)

    4. TRY/CATCH:

    Placement correct:
        launch {
            try {
                // code qui peut échouer
            } catch (e: Exception) {
                // gestion d'erreur
            }
        }

    Placement incorrect:
        try {
            launch {
                // exception non capturée!
            }
        } catch (e: Exception) { }

    Avec async:
        try {
            val result = deferred.await()
        } catch (e: Exception) {
            // gestion
        }

    5. PROPAGATION DES EXCEPTIONS:

    Règles:
    - Exception d'un enfant → annule parent et frères (coroutineScope)
    - Exception d'un enfant → n'affecte pas les frères (supervisorScope)
    - CancellationException → ne propage JAMAIS au parent
    - Exception capturée → ne propage pas

    6. PATTERNS COURANTS:

    Result wrapper:
        sealed class Result<out T> {
            data class Success<T>(val value: T)
            data class Error(val exception: Exception)
        }

    runCatching:
        val result = runCatching { riskyOperation() }
        result.onSuccess { }.onFailure { }

    Retry:
        suspend fun <T> retry(times: Int, block: suspend () -> T): T

    Circuit Breaker:
        class CircuitBreaker(threshold: Int)

    7. BONNES PRATIQUES:

    ✓ Utiliser CoroutineExceptionHandler au niveau root
    ✓ Utiliser supervisorScope pour tâches indépendantes
    ✓ Placer try/catch DANS la coroutine, pas autour
    ✓ Toujours relancer CancellationException
    ✓ Utiliser runCatching pour simplifier
    ✓ Logger les exceptions non gérées
    ✓ Nettoyer les ressources dans finally

    8. ANTI-PATTERNS:

    ✗ try/catch autour de launch (ne capture rien)
    ✗ Avaler les exceptions sans log
    ✗ Oublier de relancer CancellationException
    ✗ Handler sur coroutine enfant (ne fonctionne pas)
    ✗ catch (e: Exception) qui capture CancellationException
    ✗ Ignorer les exceptions dans supervisorScope

    9. TABLEAU RÉCAPITULATIF:

    ╔═══════════════════════╦═══════════════════════╦═══════════════════════╗
    ║ Situation             ║ Comment capturer      ║ Comportement          ║
    ╠═══════════════════════╬═══════════════════════╬═══════════════════════╣
    ║ Exception dans launch ║ CoroutineException    ║ Propage au parent     ║
    ║                       ║ Handler               ║                       ║
    ╠═══════════════════════╬═══════════════════════╬═══════════════════════╣
    ║ Exception dans async  ║ try/catch autour      ║ Encapsulée jusqu'à    ║
    ║                       ║ await()               ║ await()               ║
    ╠═══════════════════════╬═══════════════════════╬═══════════════════════╣
    ║ coroutineScope        ║ try/catch autour      ║ Annule tout           ║
    ╠═══════════════════════╬═══════════════════════╬═══════════════════════╣
    ║ supervisorScope       ║ try/catch dans        ║ Échecs indépendants   ║
    ║                       ║ chaque enfant         ║                       ║
    ╚═══════════════════════╩═══════════════════════╩═══════════════════════╝

    RÈGLE D'OR:
    "launch propage, async encapsule, supervisor isole"
    """.trimIndent())
}

// =============================================================================
// FONCTION MAIN
// =============================================================================

// [ATTENTION FORMATEUR] exécuté tel quel, main() s'arrête sur l'exception de demo01. Lancer les
// démos une par une (ou entourer chaque appel d'un try/catch).
fun main() {
    println("=" + "=".repeat(78) + "=")
    println(" ".repeat(18) + "KOTLIN COROUTINES - EXCEPTION HANDLING")
    println("=" + "=".repeat(78) + "=")

    // 1. Exceptions dans launch vs async
    demo01_exceptionInLaunch()
    demo02_exceptionInAsync()
    demo03_launchVsAsyncException()
    demo04_multipleAsyncExceptions()

    // 2. CoroutineExceptionHandler
    demo05_coroutineExceptionHandler()
    demo06_handlerScope()
    demo07_detailedHandler()
    demo08_multipleHandlers()

    // 3. supervisorScope
    demo09_supervisorScopeBasic()
    demo10_coroutineScopeVsSupervisorScope()
    demo11_supervisorScopeWithHandler()
    demo12_supervisorJob()

    // 4. try/catch
    demo13_basicTryCatch()
    demo14_tryCatchSuspend()
    demo15_tryCatchPlacement()
    demo16_tryCatchAsync()

    // 5. Propagation
    demo17_exceptionPropagation()
    demo18_supervisorPropagation()
    demo19_cancellationDoesNotPropagate()

    // 6. Exception aggregation
    demo20_multipleExceptions()
    demo21_exceptionWithCause()

    // 7. Patterns
    demo22_resultPattern()
    demo23_runCatchingPattern()
    demo24_retryPattern()
    demo25_circuitBreakerPattern()

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations")
    println("=".repeat(80))
}
