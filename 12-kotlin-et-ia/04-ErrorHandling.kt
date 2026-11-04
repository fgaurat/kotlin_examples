// ==============================================================================
// FICHE FORMATEUR — Gestion d'erreurs d'une API distante (exemple IA)
// Objectif : typer les erreurs réseau/HTTP et décider lesquelles réessayer.
// À présenter (dans l'ordre) :
//   1. sealed class AiError : une erreur = un type, when exhaustif
//   2. AiResult<T> (Success/Failure + map/onSuccess/onFailure) ≈ kotlin.Result
//   3. mapHttpStatusToError : 401/403 vs 429 vs 5xx
//   4. Retriable vs non retriable + retry avec backoff (delay = suspension, pas de blocage)
// Lien Android : repository de TP04 qui renvoie un Result et traduit IOException/HttpException
//   en erreurs du domaine, puis UiState sealed au J3 ; le backoff existe tout fait dans WorkManager (J5).
// Pièges / questions fréquentes : avaler CancellationException dans un catch ; réessayer une 401 ;
//   withTimeout lève TimeoutCancellationException (sous-classe de CancellationException).
// Si peu de temps : AiError + mapHttpStatusToError + isRetriableError + retryWithBackoff.
//   Sauter : rate limiting (token bucket, adaptatif), validation des réponses, fallback.
// ==============================================================================
package `12-kotlin-et-ia`

/**
 * 04-ErrorHandling.kt
 * ===================
 * Stratégies de gestion d'erreurs pour l'intégration avec des APIs IA.
 *
 * Ce fichier couvre :
 * - Codes HTTP spécifiques : 401/403, 429, 5xx, timeouts
 * - Sealed class pour résultats typés
 * - Retry avec backoff exponentiel
 * - Rate limiting côté client
 * - Validation des réponses
 * - Stratégies de fallback
 *
 * Principe clé : Anticiper les échecs et gérer gracieusement les erreurs.
 */

import kotlin.math.pow
import kotlin.random.Random
import kotlinx.coroutines.*

// ============================================================
// CODES HTTP ET ERREURS SPÉCIFIQUES
// ============================================================

// [À DIRE] Erreurs du domaine : l'UI ne voit jamais une IOException brute, mais NetworkError, Unauthorized…
// [ANDROID] TP04 : catch (e: IOException) -> erreur réseau ; catch (e: HttpException) -> e.code() -> erreur métier.
// Le ViewModel (J3) transforme ensuite ces erreurs en UiState.Error avec un message lisible.
/**
 * Erreurs spécifiques aux APIs IA (notamment OpenAI).
 */
sealed class AiError {
    // Erreurs d'authentification/autorisation
    data class Unauthorized(val message: String = "Invalid API key") : AiError()
    data class Forbidden(val message: String = "Access forbidden") : AiError()

    // Erreurs de rate limiting
    data class RateLimited(
        val retryAfterSeconds: Int?,
        val message: String = "Rate limit exceeded"
    ) : AiError()

    // Erreurs serveur
    data class ServerError(val statusCode: Int, val message: String) : AiError()

    // Erreurs réseau
    data class NetworkError(val cause: Throwable) : AiError()
    data class Timeout(val durationMs: Long) : AiError()

    // Erreurs de validation
    data class ValidationError(val message: String) : AiError()
    data class InvalidResponse(val message: String) : AiError()

    // Erreurs métier
    data class ContentFiltered(val message: String = "Content was filtered") : AiError()
    data class QuotaExceeded(val message: String = "Quota exceeded") : AiError()

    fun toReadableMessage(): String = when (this) {
        is Unauthorized -> "Authentication failed: $message"
        is Forbidden -> "Access denied: $message"
        is RateLimited -> "Rate limit exceeded. ${retryAfterSeconds?.let { "Retry after $it seconds." } ?: ""}"
        is ServerError -> "Server error ($statusCode): $message"
        is NetworkError -> "Network error: ${cause.message}"
        is Timeout -> "Request timeout after ${durationMs}ms"
        is ValidationError -> "Validation error: $message"
        is InvalidResponse -> "Invalid response: $message"
        is ContentFiltered -> message
        is QuotaExceeded -> message
    }
}

// [À DIRE] Avantage sur kotlin.Result : l'erreur est typée (AiError), pas un Throwable quelconque.
// [ANDROID] TP04 utilise le Result<T> standard (Result.success / Result.failure, fold, onFailure).
// [PIÈGE] runCatching { } attrape aussi CancellationException : dans une coroutine, la relancer.
/**
 * Résultat d'une opération avec l'API IA.
 */
sealed class AiResult<out T> {
    data class Success<T>(val value: T) : AiResult<T>()
    data class Failure(val error: AiError) : AiResult<Nothing>()

    fun isSuccess(): Boolean = this is Success
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException(error.toReadableMessage())
    }

    fun <R> map(transform: (T) -> R): AiResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun onSuccess(action: (T) -> Unit): AiResult<T> {
        if (this is Success) action(value)
        return this
    }

    fun onFailure(action: (AiError) -> Unit): AiResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

fun demonstrateAiErrors() {
    println("===== ERREURS SPÉCIFIQUES AUX APIs IA =====\n")

    val errors = listOf(
        AiError.Unauthorized("API key 'sk-test...' is invalid"),
        AiError.Forbidden("Organization access required"),
        AiError.RateLimited(retryAfterSeconds = 60),
        AiError.ServerError(500, "Internal server error"),
        AiError.NetworkError(Exception("Connection timeout")),
        AiError.Timeout(30000),
        AiError.ValidationError("Messages list cannot be empty"),
        AiError.InvalidResponse("Missing 'choices' field in response"),
        AiError.ContentFiltered("Input violated content policy"),
        AiError.QuotaExceeded("Monthly quota of 100,000 tokens exceeded")
    )

    errors.forEach { error ->
        println("${error::class.simpleName}:")
        println("  ${error.toReadableMessage()}")
        println()
    }
}

/**
 * Mapping du code HTTP vers AiError.
 */
// [À DIRE] 401 = qui es-tu ? (clé absente/invalide) ; 403 = je sais qui tu es, mais c'est interdit.
// [ATTENTION FORMATEUR] Chez OpenAI, un 429 peut aussi signifier quota épuisé (code "insufficient_quota") :
// non retriable, alors qu'ici tout 429 devient RateLimited (retriable). Retry-After peut aussi être une date HTTP.
// [ANDROID] Map sensible à la casse ; OkHttp : response.headers["Retry-After"] est insensible à la casse.
fun mapHttpStatusToError(statusCode: Int, responseBody: String, headers: Map<String, String> = emptyMap()): AiError {
    return when (statusCode) {
        401 -> AiError.Unauthorized(responseBody.take(100))
        403 -> AiError.Forbidden(responseBody.take(100))
        429 -> {
            val retryAfter = headers["Retry-After"]?.toIntOrNull()
            AiError.RateLimited(retryAfter, responseBody.take(100))
        }
        in 500..599 -> AiError.ServerError(statusCode, responseBody.take(100))
        else -> AiError.InvalidResponse("Unexpected status code: $statusCode")
    }
}

// [ATTENTION FORMATEUR] Même nom que demonstrateHttpStatusMapping() de 03-HttpClient.kt, même package :
// conflit de compilation, alors que ce fichier dépend des DTO de 03 (ChatResponse…). Exécuter séparément ou renommer.
fun demonstrateHttpStatusMapping() {
    println("===== MAPPING DES STATUS HTTP VERS ERREURS =====\n")

    val testCases = listOf(
        Triple(401, "Invalid API key", emptyMap()),
        Triple(403, "Organization access required", emptyMap()),
        Triple(429, "Rate limit exceeded", mapOf("Retry-After" to "60")),
        Triple(500, "Internal server error", emptyMap()),
        Triple(503, "Service unavailable", emptyMap())
    )

    testCases.forEach { (statusCode, body, headers) ->
        val error = mapHttpStatusToError(statusCode, body, headers)
        println("HTTP $statusCode → ${error::class.simpleName}")
        println("  ${error.toReadableMessage()}")
        println()
    }
}

// ============================================================
// RETRY AVEC BACKOFF EXPONENTIEL
// ============================================================

// [À DIRE] Backoff exponentiel : on espace les essais pour ne pas surcharger un serveur déjà en difficulté.
// Le jitter évite que tous les clients réessaient au même instant.
// [ANDROID] WorkManager (J5) le fait nativement : setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ...) + Result.retry().
/**
 * Configuration pour la stratégie de retry.
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000,
    val maxDelayMs: Long = 32000,
    val multiplier: Double = 2.0,
    val jitterFactor: Double = 0.1
) {
    init {
        require(maxAttempts > 0) { "Max attempts must be positive" }
        require(initialDelayMs > 0) { "Initial delay must be positive" }
        require(maxDelayMs >= initialDelayMs) { "Max delay must be >= initial delay" }
        require(multiplier >= 1.0) { "Multiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "Jitter factor must be between 0.0 and 1.0" }
    }

    /**
     * Calcule le délai pour une tentative donnée.
     */
    fun delayForAttempt(attempt: Int): Long {
        require(attempt >= 0) { "Attempt must be non-negative" }

        val exponentialDelay = (initialDelayMs * multiplier.pow(attempt.toDouble())).toLong()
        val cappedDelay = exponentialDelay.coerceAtMost(maxDelayMs)

        // Ajouter du jitter pour éviter les thundering herds
        val jitter = cappedDelay * jitterFactor * (Random.nextDouble() - 0.5)

        return (cappedDelay + jitter.toLong()).coerceAtLeast(0)
    }
}

fun demonstrateRetryDelays() {
    println("===== CALCUL DES DÉLAIS DE RETRY =====\n")

    val config = RetryConfig(
        maxAttempts = 5,
        initialDelayMs = 1000,
        maxDelayMs = 32000,
        multiplier = 2.0,
        jitterFactor = 0.1
    )

    println("Configuration :")
    println("  Max attempts  : ${config.maxAttempts}")
    println("  Initial delay : ${config.initialDelayMs}ms")
    println("  Max delay     : ${config.maxDelayMs}ms")
    println("  Multiplier    : ${config.multiplier}")
    println("  Jitter factor : ${config.jitterFactor}")
    println()

    println("Délais par tentative (avec jitter) :")
    repeat(config.maxAttempts) { attempt ->
        val delay = config.delayForAttempt(attempt)
        println("  Tentative ${attempt + 1}: ~${delay}ms")
    }
    println()

    println("Progression théorique (sans jitter) :")
    println("  1s → 2s → 4s → 8s → 16s → 32s (max)")
}

// [À DIRE] Question à poser au groupe avant de dérouler : « Faut-il réessayer une 401 ? »
// Transitoire -> on réessaie ; permanent -> on échoue tout de suite.
/**
 * Détermine si une erreur est retriable.
 */
fun isRetriableError(error: AiError): Boolean {
    return when (error) {
        // Retriable : erreurs transitoires
        is AiError.RateLimited -> true
        is AiError.ServerError -> true // 5xx
        is AiError.NetworkError -> true
        is AiError.Timeout -> true

        // Non retriable : erreurs permanentes
        is AiError.Unauthorized -> false // 401
        is AiError.Forbidden -> false // 403
        is AiError.ValidationError -> false
        is AiError.InvalidResponse -> false
        is AiError.ContentFiltered -> false
        is AiError.QuotaExceeded -> false
    }
}

fun demonstrateRetriableErrors() {
    println("===== ERREURS RETRIABLES =====\n")

    val errors = listOf(
        AiError.Unauthorized() to false,
        AiError.Forbidden() to false,
        AiError.RateLimited(60) to true,
        AiError.ServerError(500, "Internal error") to true,
        AiError.NetworkError(Exception("Connection failed")) to true,
        AiError.Timeout(30000) to true,
        AiError.ValidationError("Invalid input") to false,
        AiError.InvalidResponse("Malformed JSON") to false,
        AiError.ContentFiltered() to false,
        AiError.QuotaExceeded() to false
    )

    errors.forEach { (error, expectedRetriable) ->
        val retriable = isRetriableError(error)
        val symbol = if (retriable) "↻" else "✗"
        println("$symbol ${error::class.simpleName}: ${if (retriable) "RETRIABLE" else "NON RETRIABLE"}")
    }
    println()

    println("Stratégie :")
    println("  ↻ Retriable    : Réessayer avec backoff exponentiel")
    println("  ✗ Non retriable: Échouer immédiatement, ne pas gaspiller de ressources")
}

/**
 * Fonction de retry générique (version simplifiée pour démonstration).
 */
// [À DIRE] delay() suspend la coroutine sans bloquer le thread : sur Android, l'UI reste fluide.
// Et delay() est annulable : si l'écran est quitté (viewModelScope annulé), les retries s'arrêtent.
// [PIÈGE] Si on ajoute un try/catch(Exception) autour de operation(), relancer CancellationException.
// [ATTENTION FORMATEUR] « delay(delay) » : variable locale homonyme de la fonction, ça compile mais trouble
// les débutants. Et Retry-After (RateLimited.retryAfterSeconds) n'est pas pris en compte pour le délai.
suspend fun <T> retryWithBackoff(
    config: RetryConfig = RetryConfig(),
    operation: suspend (attempt: Int) -> AiResult<T>
): AiResult<T> {
    var lastError: AiError? = null

    repeat(config.maxAttempts) { attempt ->
        val result = operation(attempt)

        when (result) {
            is AiResult.Success -> return result
            is AiResult.Failure -> {
                lastError = result.error

                if (!isRetriableError(result.error)) {
                    // Erreur non retriable, échouer immédiatement
                    return result
                }

                if (attempt < config.maxAttempts - 1) {
                    // Attendre avant la prochaine tentative
                    val delay = config.delayForAttempt(attempt)
                    println("  Tentative ${attempt + 1} échouée: ${result.error.toReadableMessage()}")
                    println("  Attente de ${delay}ms avant retry...")
                    delay(delay)
                }
            }
        }
    }

    return AiResult.Failure(lastError ?: AiError.ServerError(0, "Unknown error"))
}

suspend fun demonstrateRetryWithBackoff() {
    println("===== RETRY AVEC BACKOFF EXPONENTIEL =====\n")

    println("Simulation : Appel qui échoue 2 fois puis réussit\n")

    var attemptCount = 0

    val result = retryWithBackoff(
        config = RetryConfig(maxAttempts = 3, initialDelayMs = 100)
    ) { attempt ->
        attemptCount++
        println("→ Tentative ${attempt + 1}...")

        when (attemptCount) {
            1 -> AiResult.Failure(AiError.ServerError(503, "Service unavailable"))
            2 -> AiResult.Failure(AiError.Timeout(30000))
            else -> AiResult.Success("Success after retries!")
        }
    }

    println()
    when (result) {
        is AiResult.Success -> println("✓ Succès final: ${result.value}")
        is AiResult.Failure -> println("✗ Échec final: ${result.error.toReadableMessage()}")
    }
    println()
}

// ============================================================
// RATE LIMITING CÔTÉ CLIENT
// ============================================================

// [SAUTER] Rate limiting côté client : hors périmètre de la formation (et les deux classes de cette section).
// [ATTENTION FORMATEUR] @Synchronized sur une suspend fun ne protège pas à travers la suspension (delay) :
// le verrou est relâché quand la fonction suspend. Utiliser kotlinx.coroutines.sync.Mutex (withLock).
// System.currentTimeMillis() n'est pas monotone : préférer System.nanoTime() / SystemClock.elapsedRealtime().
/**
 * Rate limiter simple basé sur le token bucket algorithm.
 */
class RateLimiter(
    private val tokensPerSecond: Double,
    private val bucketSize: Int = tokensPerSecond.toInt()
) {
    private var availableTokens = bucketSize.toDouble()
    private var lastRefillTime = System.currentTimeMillis()

    @Synchronized
    suspend fun acquire(tokens: Int = 1): Boolean {
        refillTokens()

        if (availableTokens >= tokens) {
            availableTokens -= tokens
            return true
        }

        // Calculer le temps d'attente nécessaire
        val tokensNeeded = tokens - availableTokens
        val waitTimeMs = (tokensNeeded / tokensPerSecond * 1000).toLong()

        if (waitTimeMs > 0) {
            delay(waitTimeMs)
            refillTokens()

            if (availableTokens >= tokens) {
                availableTokens -= tokens
                return true
            }
        }

        return false
    }

    private fun refillTokens() {
        val now = System.currentTimeMillis()
        val elapsedMs = now - lastRefillTime
        val tokensToAdd = (elapsedMs / 1000.0) * tokensPerSecond

        availableTokens = (availableTokens + tokensToAdd).coerceAtMost(bucketSize.toDouble())
        lastRefillTime = now
    }

    fun availableTokens(): Int = availableTokens.toInt()
}

suspend fun demonstrateRateLimiting() {
    println("===== RATE LIMITING CÔTÉ CLIENT =====\n")

    val rateLimiter = RateLimiter(
        tokensPerSecond = 2.0, // 2 requêtes par seconde
        bucketSize = 5          // Burst de 5 max
    )

    println("Configuration :")
    println("  Tokens/seconde : 2")
    println("  Bucket size    : 5")
    println()

    println("Simulation de 8 requêtes :")
    repeat(8) { i ->
        val startTime = System.currentTimeMillis()
        rateLimiter.acquire()
        val waitTime = System.currentTimeMillis() - startTime

        println("  Requête ${i + 1}: acquise après ${waitTime}ms (tokens disponibles: ${rateLimiter.availableTokens()})")
    }
    println()
}

/**
 * Rate limiter adaptatif basé sur les headers Retry-After.
 */
class AdaptiveRateLimiter {
    private var minDelayMs: Long = 0
    private var lastRequestTime: Long = 0

    @Synchronized
    suspend fun acquire() {
        val now = System.currentTimeMillis()
        val timeSinceLastRequest = now - lastRequestTime

        if (timeSinceLastRequest < minDelayMs) {
            val waitTime = minDelayMs - timeSinceLastRequest
            println("  Rate limiter: Attente de ${waitTime}ms")
            delay(waitTime)
        }

        lastRequestTime = System.currentTimeMillis()
    }

    fun onRateLimited(retryAfterSeconds: Int) {
        minDelayMs = (retryAfterSeconds * 1000L).coerceAtMost(60000) // Max 60s
        println("  Rate limiter: Délai minimum ajusté à ${minDelayMs}ms")
    }

    fun onSuccess() {
        // Réduire progressivement le délai après succès
        minDelayMs = (minDelayMs * 0.9).toLong()
    }
}

suspend fun demonstrateAdaptiveRateLimiting() {
    println("===== RATE LIMITING ADAPTATIF =====\n")

    val rateLimiter = AdaptiveRateLimiter()

    println("Simulation :")
    println("1. Requête initiale")
    rateLimiter.acquire()
    println("  ✓ Requête envoyée\n")

    println("2. Réponse 429 avec Retry-After: 5")
    rateLimiter.onRateLimited(5)
    println()

    println("3. Retry après ajustement")
    rateLimiter.acquire()
    println("  ✓ Requête envoyée\n")

    println("4. Succès")
    rateLimiter.onSuccess()
    println("  Délai minimum réduit progressivement")
}

// ============================================================
// VALIDATION DES RÉPONSES
// ============================================================

// [SAUTER] Validation métier des réponses : à évoquer seulement (« un 200 ne garantit pas une réponse exploitable »).
// [ANDROID] Ce contrôle a sa place dans le mapper DTO -> modèle du repository (TP04).
/**
 * Validation d'une réponse de l'API.
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Invalid(val reasons: List<String>) : ValidationResult()
}

/**
 * Validateur de réponse ChatResponse.
 */
fun validateChatResponse(response: ChatResponse): ValidationResult {
    val errors = mutableListOf<String>()

    if (response.choices.isEmpty()) {
        errors.add("Choices list is empty")
    }

    response.choices.forEach { choice ->
        if (choice.message.content.isBlank()) {
            errors.add("Message content is blank at index ${choice.index}")
        }

        if (choice.finishReason == "content_filter") {
            errors.add("Content was filtered")
        }
    }

    if (response.usage.totalTokens <= 0) {
        errors.add("Total tokens is not positive")
    }

    if (response.model.isBlank()) {
        errors.add("Model field is blank")
    }

    return if (errors.isEmpty()) {
        ValidationResult.Valid
    } else {
        ValidationResult.Invalid(errors)
    }
}

fun demonstrateResponseValidation() {
    println("===== VALIDATION DES RÉPONSES =====\n")

    val testCases = listOf(
        ChatResponse(
            id = "cmpl-1",
            model = "gpt-4",
            choices = listOf(
                ChatChoice(0, ChatMessage.assistant("Valid response"), "stop")
            ),
            usage = Usage(10, 20, 30),
            created = 1234567890
        ) to "Réponse valide",

        ChatResponse(
            id = "cmpl-2",
            model = "gpt-4",
            choices = emptyList(),
            usage = Usage(10, 0, 10),
            created = 1234567890
        ) to "Choices vide",

        // [ATTENTION FORMATEUR] Bug : ChatMessage (03) fait require(content.isNotBlank()) dans son init,
        // donc ChatMessage.assistant("") lève IllegalArgumentException et la démo plante ici.
        ChatResponse(
            id = "cmpl-3",
            model = "gpt-4",
            choices = listOf(
                ChatChoice(0, ChatMessage.assistant(""), "stop")
            ),
            usage = Usage(10, 0, 10),
            created = 1234567890
        ) to "Content vide",

        ChatResponse(
            id = "cmpl-4",
            model = "gpt-4",
            choices = listOf(
                ChatChoice(0, ChatMessage.assistant("Filtered"), "content_filter")
            ),
            usage = Usage(10, 5, 15),
            created = 1234567890
        ) to "Content filtré"
    )

    testCases.forEach { (response, description) ->
        println("Test : $description")
        val result = validateChatResponse(response)

        when (result) {
            is ValidationResult.Valid -> {
                println("  ✓ Valide\n")
            }
            is ValidationResult.Invalid -> {
                println("  ✗ Invalide :")
                result.reasons.forEach { reason ->
                    println("    - $reason")
                }
                println()
            }
        }
    }
}

// ============================================================
// STRATÉGIES DE FALLBACK
// ============================================================

// [SAUTER] Fallback : dire seulement qu'on peut afficher un cache (Room) ou un état dégradé plutôt qu'une erreur.
// [ATTENTION FORMATEUR] Génériques posés sur les sous-classes + casts non vérifiés (as T) : pas type-safe,
// un ClassCastException peut survenir à l'usage. Ne pas le montrer comme modèle.
/**
 * Stratégies de fallback en cas d'échec.
 */
sealed class FallbackStrategy {
    data object FailImmediately : FallbackStrategy()
    data class ReturnDefault<T>(val value: T) : FallbackStrategy()
    data class UseCache<T>(val cachedValue: T) : FallbackStrategy()
    data class DegradeGracefully(val message: String) : FallbackStrategy()
}

suspend fun <T> executeWithFallback(
    operation: suspend () -> AiResult<T>,
    fallback: FallbackStrategy
): AiResult<T> {
    val result = operation()

    return when (result) {
        is AiResult.Success -> result
        is AiResult.Failure -> {
            when (fallback) {
                is FallbackStrategy.FailImmediately -> result
                is FallbackStrategy.ReturnDefault<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    AiResult.Success(fallback.value as T)
                }
                is FallbackStrategy.UseCache<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    AiResult.Success(fallback.cachedValue as T)
                }
                is FallbackStrategy.DegradeGracefully -> {
                    println("  Degraded mode: ${fallback.message}")
                    result
                }
            }
        }
    }
}

suspend fun demonstrateFallbackStrategies() {
    println("===== STRATÉGIES DE FALLBACK =====\n")

    val failingOperation: suspend () -> AiResult<String> = {
        AiResult.Failure(AiError.ServerError(503, "Service unavailable"))
    }

    println("1. FailImmediately")
    val result1 = executeWithFallback(failingOperation, FallbackStrategy.FailImmediately)
    println("  Result: ${if (result1.isFailure()) "Échec" else "Succès"}\n")

    println("2. ReturnDefault")
    val result2 = executeWithFallback(failingOperation, FallbackStrategy.ReturnDefault("Default response"))
    println("  Result: ${result2.getOrNull()}\n")

    println("3. UseCache")
    val result3 = executeWithFallback(failingOperation, FallbackStrategy.UseCache("Cached response from yesterday"))
    println("  Result: ${result3.getOrNull()}\n")

    println("4. DegradeGracefully")
    val result4 = executeWithFallback(failingOperation, FallbackStrategy.DegradeGracefully("Fonctionnalité IA temporairement indisponible"))
    println("  Result: ${if (result4.isFailure()) "Échec" else "Succès"}\n")
}

// ============================================================
// MAIN ET RÉSUMÉ
// ============================================================

// [PIÈGE] runBlocking : acceptable dans un main JVM de démo, jamais sur Android (bloque le thread UI -> ANR).
// Sur Android : viewModelScope.launch { } (J3).
fun main() = runBlocking {
    println("="
.repeat(60))
    println("MODULE 12 - KOTLIN ET IA : GESTION D'ERREURS")
    println("="
.repeat(60))
    println()

    demonstrateAiErrors()
    demonstrateHttpStatusMapping()
    demonstrateRetryDelays()
    demonstrateRetriableErrors()
    demonstrateRetryWithBackoff()
    demonstrateRateLimiting()
    demonstrateAdaptiveRateLimiting()
    demonstrateResponseValidation()
    demonstrateFallbackStrategies()

    println("\n" + "=".repeat(60))
    println("RÉSUMÉ - GESTION D'ERREURS POUR APIs IA")
    println("=".repeat(60))
    // [PIÈGE] Point 9 du résumé : withTimeout lève TimeoutCancellationException, sous-classe de
    // CancellationException ; un catch générique peut l'avaler. withTimeoutOrNull renvoie null à la place.
    println("""
        1. SEALED CLASS AiError : Types d'erreurs spécifiques (Unauthorized 401, Forbidden 403,
           RateLimited 429, ServerError 5xx, NetworkError, Timeout, ValidationError, etc.).

        2. RETRIABLE vs NON-RETRIABLE : 5xx, 429, timeout, network → retriable. 401, 403,
           validation, quota → non-retriable. Ne jamais retry les erreurs permanentes.

        3. BACKOFF EXPONENTIEL : Délais 1s → 2s → 4s → 8s... avec jitter (±10%) pour éviter
           thundering herd. MaxDelay pour plafonner (32s). 3-5 tentatives max.

        4. RATE LIMITING CLIENT : Token bucket (tokensPerSecond, bucketSize) pour respecter
           les quotas API. Attend automatiquement si tokens insuffisants.

        5. ADAPTIVE RATE LIMITER : Ajuste minDelay basé sur Retry-After header des réponses 429.
           Réduit progressivement après succès. Évite de surcharger l'API.

        6. VALIDATION RÉPONSES : Vérifier choices non vide, content non blank, finishReason
           pas content_filter, usage.totalTokens > 0. Fail-fast si réponse invalide.

        7. STRATÉGIES FALLBACK : FailImmediately (prod critique), ReturnDefault (valeur safe),
           UseCache (données anciennes OK), DegradeGracefully (UI dégradée mais fonctionnelle).

        8. AiResult<T> : Sealed class Success/Failure avec map(), onSuccess(), onFailure(),
           getOrNull(), getOrThrow(). Pattern Result pour chaînage et composition.

        9. TIMEOUT : withTimeout() coroutine pour limite stricte. Si dépassé, AiError.Timeout
           avec durée. Toujours configurer timeout, jamais d'attente infinie.

        10. OBSERVABILITÉ : Logger tentatives, délais, erreurs avec contexte (request ID).
            Métriques : taux d'erreur par type, latence retry, taux succès après retry.
    """.trimIndent())
}
