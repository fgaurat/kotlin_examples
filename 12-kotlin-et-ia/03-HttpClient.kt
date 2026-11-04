// ==============================================================================
// FICHE FORMATEUR — Client HTTP + JSON (OpenAI via Ktor, simulé)
// Objectif : montrer requête HTTP, DTO JSON et mapping des codes d'erreur, sans réseau réel.
// À présenter (dans l'ordre) :
//   1. DTO @Serializable + @SerialName (snake_case JSON -> camelCase Kotlin)
//   2. Désérialisation avec Json { ignoreUnknownKeys = true }
//   3. Anatomie d'une requête : méthode, URL, headers (Authorization, Content-Type), body
//   4. sealed class HttpResult + mapHttpStatus : 2xx / 4xx / 5xx / réseau / JSON invalide
//   5. Sécurité : jamais de clé d'API dans l'APK
// Lien Android : TP04 (Todos) fait la même chose avec Retrofit + OkHttp + kotlinx.serialization :
//   DTO @Serializable, interface Retrofit en suspend fun, repository qui renvoie un Result.
// Pièges / questions fréquentes : clé d'API embarquée dans l'app ; oubli de ignoreUnknownKeys ;
//   valeurs par défaut non sérialisées (encodeDefaults).
// Si peu de temps : demonstrateDtos + demonstrateResponseDeserialization + mapHttpStatus.
//   Sauter : TimeoutConfig (le dire oralement), HeadersBuilder, demonstrateCompleteExample.
// ==============================================================================
package `12-kotlin-et-ia`

/**
 * 03-HttpClient.kt
 * ================
 * Client HTTP pour l'API OpenAI avec Ktor.
 *
 * Ce fichier couvre :
 * - DTOs pour OpenAI API (ChatMessage, ChatRequest, ChatResponse)
 * - Client HTTP avec Ktor et kotlinx.serialization
 * - Configuration des timeouts
 * - Headers d'autorisation
 * - Gestion basique des erreurs HTTP
 * - Sérialisation/désérialisation JSON
 *
 * Note : Ce code est à but démonstratif. Les appels API réels nécessitent
 * une vraie clé API OpenAI et des dépendances Ktor configurées.
 *
 * Dépendances requises (build.gradle.kts) :
 * ```
 * implementation("io.ktor:ktor-client-core:2.3.7")
 * implementation("io.ktor:ktor-client-cio:2.3.7")
 * implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
 * implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
 * implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
 * ```
 */

// [ATTENTION FORMATEUR] Versions datées dans le KDoc : Ktor 2.3.7 / serialization 1.6.2.
// Ktor est passé en 3.x (fin 2024) ; vérifier les versions actuelles avant de les montrer.
// [ANDROID] Sur Android, moteur Ktor = OkHttp (pas CIO). Dans TP04 : Retrofit + converter kotlinx.serialization.
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json

// ============================================================
// DATA TRANSFER OBJECTS (DTOs)
// ============================================================

// [À DIRE] DTO = forme exacte du JSON échangé, pas le modèle métier.
// [ANDROID] TP04 : DTO @Serializable séparés du modèle, puis mapper DTO -> modèle dans le repository.
/**
 * Représente un message dans une conversation.
 */
// [ATTENTION FORMATEUR] content non nullable + require(isNotBlank) : l'API peut renvoyer
// content = null (appels d'outils) et des rôles "developer"/"tool" -> échec de désérialisation.
// Valider dans un DTO est discutable : mieux vaut valider dans le mapper / le modèle.
@Serializable
data class ChatMessage(
    val role: String, // "system", "user", ou "assistant"
    val content: String
) {
    init {
        require(role in listOf("system", "user", "assistant")) {
            "Role must be 'system', 'user', or 'assistant', got: $role"
        }
        require(content.isNotBlank()) {
            "Content cannot be blank"
        }
    }

    companion object {
        fun system(content: String) = ChatMessage("system", content)
        fun user(content: String) = ChatMessage("user", content)
        fun assistant(content: String) = ChatMessage("assistant", content)
    }
}

/**
 * Requête pour l'API Chat Completion.
 */
// [À DIRE] @SerialName fait le pont entre max_tokens (JSON) et maxTokens (Kotlin).
// [ATTENTION FORMATEUR] max_tokens est déprécié chez OpenAI au profit de max_completion_tokens
// (refusé par les modèles de raisonnement). OpenAI pousse aussi la Responses API pour les nouveaux projets.
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens") val maxTokens: Int = 1000,
    @SerialName("top_p") val topP: Double = 1.0,
    @SerialName("frequency_penalty") val frequencyPenalty: Double = 0.0,
    @SerialName("presence_penalty") val presencePenalty: Double = 0.0
) {
    init {
        require(messages.isNotEmpty()) { "Messages cannot be empty" }
        require(temperature in 0.0..2.0) { "Temperature must be between 0.0 and 2.0" }
        require(maxTokens > 0) { "Max tokens must be positive" }
    }
}

/**
 * Choix de réponse de l'API.
 */
@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

/**
 * Informations sur l'utilisation de tokens.
 */
@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

/**
 * Réponse de l'API Chat Completion.
 */
@Serializable
data class ChatResponse(
    val id: String,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage,
    val created: Long,
    @SerialName("object") val objectType: String = "chat.completion"
) {
    /**
     * Retourne le contenu de la première réponse (cas d'usage le plus courant).
     */
    fun firstContent(): String? = choices.firstOrNull()?.message?.content
}

/**
 * Réponse d'erreur de l'API OpenAI.
 */
// [À DIRE] Le corps d'une réponse d'erreur a aussi un format JSON : on le modélise pour afficher un vrai message.
// [ANDROID] Retrofit : en cas de 4xx/5xx, lire response.errorBody() (Response<T>) ou HttpException.response().
@Serializable
data class OpenAiError(
    val message: String,
    val type: String,
    val param: String? = null,
    val code: String? = null
)

@Serializable
data class OpenAiErrorResponse(
    val error: OpenAiError
)

/**
 * Démontre la création et sérialisation des DTOs.
 */
fun demonstrateDtos() {
    println("===== DATA TRANSFER OBJECTS (DTOs) =====\n")

    // Création de messages
    val systemMsg = ChatMessage.system("You are a helpful assistant.")
    val userMsg = ChatMessage.user("What is Kotlin?")

    println("Messages créés :")
    println("  System: ${systemMsg.content}")
    println("  User  : ${userMsg.content}")
    println()

    // Création d'une requête
    // [ATTENTION FORMATEUR] "gpt-4" est un modèle ancien ; prendre un modèle actuel de la doc OpenAI.
    val request = ChatRequest(
        model = "gpt-4",
        messages = listOf(systemMsg, userMsg),
        temperature = 0.7,
        maxTokens = 500
    )

    println("Requête créée :")
    println("  Model      : ${request.model}")
    println("  Messages   : ${request.messages.size}")
    println("  Temperature: ${request.temperature}")
    println("  Max tokens : ${request.maxTokens}")
    println()

    // [PIÈGE] Par défaut, Json n'écrit pas les propriétés égales à leur valeur par défaut :
    // ici temperature = 0.7, top_p, etc. disparaissent du JSON. Faire constater à l'exécution.
    // Solution : encodeDefaults = true (utilisé plus bas dans prepareChatRequest).
    // Sérialisation JSON
    val json = Json { prettyPrint = true }
    val requestJson = json.encodeToString(ChatRequest.serializer(), request)

    println("Sérialisation JSON :")
    println(requestJson)
    println()
}

/**
 * Démontre la désérialisation d'une réponse.
 */
fun demonstrateResponseDeserialization() {
    println("===== DÉSÉRIALISATION DE RÉPONSE =====\n")

    // Exemple de réponse JSON de l'API OpenAI
    val responseJson = """
        {
          "id": "chatcmpl-123",
          "object": "chat.completion",
          "created": 1677652288,
          "model": "gpt-4",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "Kotlin is a modern programming language that runs on the JVM."
            },
            "finish_reason": "stop"
          }],
          "usage": {
            "prompt_tokens": 15,
            "completion_tokens": 20,
            "total_tokens": 35
          }
        }
    """.trimIndent()

    println("JSON reçu :")
    println(responseJson)
    println()

    // [À DIRE] Sans ignoreUnknownKeys, le moindre champ ajouté côté serveur fait planter le parsing.
    // [ANDROID] Même config dans TP04 : Json { ignoreUnknownKeys = true } passé à
    // json.asConverterFactory("application/json".toMediaType()) dans le Retrofit.Builder.
    // Désérialisation
    val json = Json { ignoreUnknownKeys = true }
    val response = json.decodeFromString<ChatResponse>(responseJson)

    println("Réponse désérialisée :")
    println("  ID         : ${response.id}")
    println("  Model      : ${response.model}")
    println("  Content    : ${response.firstContent()}")
    println("  Tokens used: ${response.usage.totalTokens}")
    println("    - Prompt    : ${response.usage.promptTokens}")
    println("    - Completion: ${response.usage.completionTokens}")
    println()
}

// ============================================================
// CLIENT HTTP AVEC KTOR (VERSION SIMULÉE)
// ============================================================

/**
 * Client pour l'API OpenAI.
 *
 * Note : Cette version est simplifiée pour la démonstration.
 * Une implémentation réelle nécessiterait les dépendances Ktor.
 */
// [À DIRE] SÉCURITÉ : ne jamais embarquer une clé d'API dans l'APK, elle est extractible (apktool, strings).
// [ANDROID] En prod : l'app appelle VOTRE backend/proxy, qui détient la clé. En dev seulement :
// local.properties (hors Git) -> buildConfigField -> BuildConfig.API_KEY (vu au J4). Même idée pour BASE_URL dans TP04.
class OpenAiClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val timeoutMillis: Long = 30000
) {
    init {
        require(apiKey.isNotBlank()) { "API key cannot be blank" }
        require(apiKey.startsWith("sk-")) { "Invalid API key format" }
        // [ANDROID] Android bloque aussi le HTTP en clair par défaut (API 28+). TP04 vise http://10.0.2.2
        // (le localhost du PC vu de l'émulateur) : d'où le network_security_config, + permission INTERNET.
        require(baseUrl.startsWith("https://")) { "Base URL must use HTTPS" }
        require(timeoutMillis > 0) { "Timeout must be positive" }
    }

    /**
     * Structure de la requête HTTP pour démonstration.
     */
    data class HttpRequestInfo(
        val method: String,
        val url: String,
        val headers: Map<String, String>,
        val bodyJson: String
    )

    /**
     * Prépare une requête chat (sans l'exécuter réellement).
     * Dans une vraie implémentation, cette méthode serait suspend et utiliserait Ktor.
     */
    // [ANDROID] Avec Retrofit, tout ceci devient une déclaration :
    // @POST("chat/completions") suspend fun chat(@Body request: ChatRequest): ChatResponse
    // Le header Authorization s'ajoute une fois pour toutes via un Interceptor OkHttp.
    fun prepareChatRequest(request: ChatRequest): HttpRequestInfo {
        val json = Json { encodeDefaults = true }
        val bodyJson = json.encodeToString(ChatRequest.serializer(), request)

        return HttpRequestInfo(
            method = "POST",
            url = "$baseUrl/chat/completions",
            headers = mapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer ${apiKey.take(7)}..." // Masqué
            ),
            bodyJson = bodyJson
        )
    }

    /**
     * Affiche des informations sur la configuration du client.
     */
    fun info(): String {
        return """
            OpenAiClient:
              API Key    : ${apiKey.take(7)}...
              Base URL   : $baseUrl
              Timeout    : ${timeoutMillis}ms
        """.trimIndent()
    }
}

// [À DIRE] Rien n'est envoyé : on montre seulement la requête construite (méthode, URL, headers, body).
fun demonstrateOpenAiClient() {
    println("===== CLIENT OPENAI =====\n")

    // Créer le client (avec une clé de test)
    val client = OpenAiClient(
        apiKey = "sk-test-key-1234567890",
        baseUrl = "https://api.openai.com/v1",
        timeoutMillis = 30000
    )

    println(client.info())
    println()

    // Préparer une requête
    val request = ChatRequest(
        model = "gpt-4",
        messages = listOf(
            ChatMessage.system("You are a helpful assistant."),
            ChatMessage.user("Explain Kotlin coroutines in one sentence.")
        ),
        temperature = 0.5,
        maxTokens = 100
    )

    val httpRequest = client.prepareChatRequest(request)

    println("Requête HTTP préparée :")
    println("  Method : ${httpRequest.method}")
    println("  URL    : ${httpRequest.url}")
    println("  Headers:")
    httpRequest.headers.forEach { (key, value) ->
        println("    $key: $value")
    }
    println("  Body   :")
    println(httpRequest.bodyJson.lines().joinToString("\n") { "    $it" })
    println()

    println("⚠️  Cette requête n'est pas réellement envoyée (code de démonstration)")
}

// ============================================================
// CONFIGURATION DES TIMEOUTS
// ============================================================

// [SAUTER] Section démonstrative : se contenter de dire « toujours des timeouts ».
// [ANDROID] OkHttpClient.Builder().connectTimeout(10, SECONDS).readTimeout(30, SECONDS).callTimeout(...)
// (défauts OkHttp : 10 s connect/read/write, pas de callTimeout). Ktor : plugin HttpTimeout.
/**
 * Configuration des timeouts pour Ktor.
 */
data class TimeoutConfig(
    val connectTimeoutMillis: Long = 10000,  // 10s pour établir la connexion
    val requestTimeoutMillis: Long = 30000,  // 30s pour la requête complète
    val socketTimeoutMillis: Long = 30000    // 30s pour lire la réponse
) {
    init {
        require(connectTimeoutMillis > 0) { "Connect timeout must be positive" }
        require(requestTimeoutMillis > 0) { "Request timeout must be positive" }
        require(socketTimeoutMillis > 0) { "Socket timeout must be positive" }
    }
}

fun demonstrateTimeoutConfiguration() {
    println("===== CONFIGURATION DES TIMEOUTS =====\n")

    val timeouts = listOf(
        TimeoutConfig() to "Par défaut",
        TimeoutConfig(
            connectTimeoutMillis = 5000,
            requestTimeoutMillis = 60000,
            socketTimeoutMillis = 60000
        ) to "Requêtes longues",
        TimeoutConfig(
            connectTimeoutMillis = 3000,
            requestTimeoutMillis = 10000,
            socketTimeoutMillis = 10000
        ) to "Requêtes rapides"
    )

    timeouts.forEach { (config, description) ->
        println("$description :")
        println("  Connect : ${config.connectTimeoutMillis}ms")
        println("  Request : ${config.requestTimeoutMillis}ms")
        println("  Socket  : ${config.socketTimeoutMillis}ms")
        println()
    }

    println("Recommandations :")
    println("  ✓ Connect timeout : 5-10s (établir connexion)")
    println("  ✓ Request timeout : 30-60s (OpenAI peut être lent)")
    println("  ✓ Socket timeout  : Identique au request timeout")
    println("  ✓ Toujours configurer des timeouts pour éviter les hangs")
}

// ============================================================
// HEADERS HTTP
// ============================================================

// [SAUTER] Builder maison, inutile sur Android.
// [ANDROID] Équivalent : un Interceptor OkHttp qui fait chain.request().newBuilder().header(...).
// Pour les logs : HttpLoggingInterceptor + redactHeader("Authorization"), uniquement en debug.
/**
 * Builder pour les headers HTTP.
 */
class HeadersBuilder {
    private val headers = mutableMapOf<String, String>()

    fun authorization(token: String): HeadersBuilder {
        require(token.isNotBlank()) { "Token cannot be blank" }
        headers["Authorization"] = "Bearer $token"
        return this
    }

    fun contentType(value: String = "application/json"): HeadersBuilder {
        headers["Content-Type"] = value
        return this
    }

    fun userAgent(value: String): HeadersBuilder {
        headers["User-Agent"] = value
        return this
    }

    fun custom(key: String, value: String): HeadersBuilder {
        headers[key] = value
        return this
    }

    fun build(): Map<String, String> = headers.toMap()

    /**
     * Version sécurisée pour le logging (masque les tokens).
     */
    fun buildSafe(): Map<String, String> = headers.mapValues { (key, value) ->
        if (key.equals("Authorization", ignoreCase = true)) {
            val parts = value.split(" ")
            if (parts.size == 2) {
                "${parts[0]} ${parts[1].take(7)}..."
            } else {
                "***"
            }
        } else {
            value
        }
    }
}

fun demonstrateHeaders() {
    println("===== HEADERS HTTP =====\n")

    val headers = HeadersBuilder()
        .authorization("sk-proj-abc123def456ghi789")
        .contentType()
        .userAgent("MyKotlinApp/1.0")
        .custom("X-Request-ID", "req-12345")
        .build()

    println("Headers construits :")
    headers.forEach { (key, value) ->
        println("  $key: $value")
    }
    println()

    println("Headers pour logging (sécurisés) :")
    val safeHeaders = HeadersBuilder()
        .authorization("sk-proj-abc123def456ghi789")
        .contentType()
        .userAgent("MyKotlinApp/1.0")
        .custom("X-Request-ID", "req-12345")
        .buildSafe()

    safeHeaders.forEach { (key, value) ->
        println("  $key: $value")
    }
    println()
}

// ============================================================
// GESTION DES ERREURS HTTP
// ============================================================

// [À DIRE] Cœur du sujet « gestion des erreurs » : un type fermé, et le when est exhaustif sans else.
// [ANDROID] TP04 utilise le Result<T> standard + erreurs du domaine. Même sealed class au J3 pour UiState.
// IOException -> erreur réseau (ici NetworkError/TimeoutError), HttpException -> code HTTP (Client/ServerError).
/**
 * Résultat d'un appel HTTP.
 */
sealed class HttpResult<out T> {
    data class Success<T>(val data: T, val statusCode: Int = 200) : HttpResult<T>()

    sealed class Error : HttpResult<Nothing>() {
        data class ClientError(val statusCode: Int, val message: String) : Error()
        data class ServerError(val statusCode: Int, val message: String) : Error()
        data class NetworkError(val cause: Throwable) : Error()
        data class TimeoutError(val timeoutMillis: Long) : Error()
        data class DeserializationError(val cause: Throwable) : Error()
    }

    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error.ClientError -> throw IllegalStateException("Client error $statusCode: $message")
        is Error.ServerError -> throw IllegalStateException("Server error $statusCode: $message")
        is Error.NetworkError -> throw cause
        is Error.TimeoutError -> throw IllegalStateException("Timeout after ${timeoutMillis}ms")
        is Error.DeserializationError -> throw cause
    }
}

/**
 * Démontre la gestion des erreurs HTTP.
 */
fun demonstrateHttpErrors() {
    println("===== GESTION DES ERREURS HTTP =====\n")

    val results = listOf(
        HttpResult.Success(ChatResponse(
            id = "chatcmpl-123",
            model = "gpt-4",
            choices = listOf(ChatChoice(0, ChatMessage.assistant("Hello!"), "stop")),
            usage = Usage(10, 5, 15),
            created = System.currentTimeMillis() / 1000
        )),
        HttpResult.Error.ClientError(401, "Invalid API key"),
        HttpResult.Error.ClientError(429, "Rate limit exceeded"),
        HttpResult.Error.ServerError(500, "Internal server error"),
        HttpResult.Error.NetworkError(Exception("Connection refused")),
        HttpResult.Error.TimeoutError(30000),
        HttpResult.Error.DeserializationError(Exception("Invalid JSON"))
    )

    // [À DIRE] Chaque type d'erreur appelle une action différente : c'est pour ça qu'on les distingue.
    results.forEach { result ->
        when (result) {
            is HttpResult.Success -> {
                println("✓ SUCCESS (${result.statusCode})")
                println("  Response ID: ${result.data.id}")
                println("  Content: ${result.data.firstContent()}")
            }
            is HttpResult.Error.ClientError -> {
                println("✗ CLIENT ERROR (${result.statusCode})")
                println("  Message: ${result.message}")
                when (result.statusCode) {
                    401 -> println("  Action: Vérifier l'API key")
                    429 -> println("  Action: Attendre avant de réessayer")
                }
            }
            is HttpResult.Error.ServerError -> {
                println("✗ SERVER ERROR (${result.statusCode})")
                println("  Message: ${result.message}")
                println("  Action: Réessayer avec backoff")
            }
            is HttpResult.Error.NetworkError -> {
                println("✗ NETWORK ERROR")
                println("  Cause: ${result.cause.message}")
                println("  Action: Vérifier la connexion")
            }
            is HttpResult.Error.TimeoutError -> {
                println("✗ TIMEOUT")
                println("  Durée: ${result.timeoutMillis}ms")
                println("  Action: Augmenter le timeout ou réessayer")
            }
            is HttpResult.Error.DeserializationError -> {
                println("✗ DESERIALIZATION ERROR")
                println("  Cause: ${result.cause.message}")
                println("  Action: Vérifier le format de réponse")
            }
        }
        println()
    }
}

/**
 * Mapping du code HTTP vers HttpResult.
 */
// [ANDROID] Retrofit fait ce tri pour vous : suspend fun qui renvoie T -> HttpException si non-2xx ;
// qui renvoie Response<T> -> tester response.isSuccessful / response.code() soi-même.
fun mapHttpStatus(statusCode: Int, body: String): HttpResult<ChatResponse> {
    return when (statusCode) {
        in 200..299 -> {
            // [PIÈGE] catch (e: Exception) attrape tout. Pas grave ici (fonction non suspend), mais dans une
            // suspend fun il avalerait CancellationException : la relancer, ou cibler SerializationException.
            // [ATTENTION FORMATEUR] Un Json { } recréé à chaque appel : en pratique, une seule instance partagée.
            try {
                val json = Json { ignoreUnknownKeys = true }
                val response = json.decodeFromString<ChatResponse>(body)
                HttpResult.Success(response, statusCode)
            } catch (e: Exception) {
                HttpResult.Error.DeserializationError(e)
            }
        }
        in 400..499 -> {
            val errorMsg = try {
                val json = Json { ignoreUnknownKeys = true }
                val errorResponse = json.decodeFromString<OpenAiErrorResponse>(body)
                errorResponse.error.message
            } catch (e: Exception) {
                body
            }
            HttpResult.Error.ClientError(statusCode, errorMsg)
        }
        in 500..599 -> {
            HttpResult.Error.ServerError(statusCode, "Server error: $body")
        }
        // [ATTENTION FORMATEUR] 1xx/3xx classés en ClientError : approximation discutable.
        else -> {
            HttpResult.Error.ClientError(statusCode, "Unexpected status code")
        }
    }
}

// [ATTENTION FORMATEUR] 04-ErrorHandling.kt déclare aussi demonstrateHttpStatusMapping() dans le même package :
// « Conflicting overloads » si les deux fichiers sont compilés ensemble (or 04 a besoin des DTO de 03).
fun demonstrateHttpStatusMapping() {
    println("===== MAPPING DES STATUS HTTP =====\n")

    val testCases = listOf(
        200 to """{"id":"cmpl-123","object":"chat.completion","created":1677652288,"model":"gpt-4","choices":[{"index":0,"message":{"role":"assistant","content":"Test"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""",
        401 to """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error","code":"invalid_api_key"}}""",
        429 to """{"error":{"message":"Rate limit exceeded","type":"rate_limit_error"}}""",
        500 to """Internal Server Error"""
    )

    testCases.forEach { (statusCode, body) ->
        println("Status $statusCode :")
        val result = mapHttpStatus(statusCode, body)

        when (result) {
            is HttpResult.Success -> println("  ✓ Succès")
            is HttpResult.Error.ClientError -> println("  ✗ Erreur client: ${result.message}")
            is HttpResult.Error.ServerError -> println("  ✗ Erreur serveur: ${result.message}")
            else -> println("  ✗ Autre erreur")
        }
        println()
    }
}

// ============================================================
// EXEMPLE COMPLET
// ============================================================

// [SAUTER] Récapitulatif des sections précédentes, rien de nouveau.
/**
 * Exemple complet de préparation d'une requête OpenAI.
 */
fun demonstrateCompleteExample() {
    println("===== EXEMPLE COMPLET =====\n")

    println("1. Configuration")
    val client = OpenAiClient(
        apiKey = "sk-test-1234567890abcdef",
        baseUrl = "https://api.openai.com/v1",
        timeoutMillis = 30000
    )
    println(client.info())
    println()

    println("2. Création de la requête")
    val messages = listOf(
        ChatMessage.system("You are a Kotlin expert."),
        ChatMessage.user("What are sealed classes in Kotlin?")
    )

    val request = ChatRequest(
        model = "gpt-4",
        messages = messages,
        temperature = 0.5,
        maxTokens = 200
    )

    println("  Messages: ${messages.size}")
    println("  Model: ${request.model}")
    println()

    println("3. Préparation de la requête HTTP")
    val httpRequest = client.prepareChatRequest(request)
    println("  ${httpRequest.method} ${httpRequest.url}")
    println("  Headers: ${httpRequest.headers.keys}")
    println()

    println("4. Simulation de la réponse")
    val mockResponseJson = """
        {
          "id": "chatcmpl-789",
          "object": "chat.completion",
          "created": 1234567890,
          "model": "gpt-4",
          "choices": [{
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "Sealed classes in Kotlin restrict inheritance to a known set of subclasses."
            },
            "finish_reason": "stop"
          }],
          "usage": {
            "prompt_tokens": 25,
            "completion_tokens": 18,
            "total_tokens": 43
          }
        }
    """.trimIndent()

    val result = mapHttpStatus(200, mockResponseJson)

    when (result) {
        is HttpResult.Success -> {
            println("  ✓ Réponse reçue")
            println("  Content: ${result.data.firstContent()}")
            println("  Tokens: ${result.data.usage.totalTokens}")
        }
        is HttpResult.Error -> {
            println("  ✗ Erreur")
        }
    }
    println()

    println("⚠️  Rappel : Ce code est à but démonstratif")
    println("   Pour l'utiliser réellement :")
    println("   1. Ajouter les dépendances Ktor au build.gradle.kts")
    println("   2. Obtenir une vraie clé API OpenAI")
    println("   3. Implémenter les appels HTTP réels avec suspend functions")
}

// ============================================================
// MAIN ET RÉSUMÉ
// ============================================================

fun main() {
    println("="
.repeat(60))
    println("MODULE 12 - KOTLIN ET IA : CLIENT HTTP")
    println("="
.repeat(60))
    println()

    demonstrateDtos()
    demonstrateResponseDeserialization()
    demonstrateOpenAiClient()
    demonstrateTimeoutConfiguration()
    demonstrateHeaders()
    demonstrateHttpErrors()
    demonstrateHttpStatusMapping()
    demonstrateCompleteExample()

    println("\n" + "=".repeat(60))
    println("RÉSUMÉ - CLIENT HTTP POUR API OPENAI")
    println("=".repeat(60))
    // [ATTENTION FORMATEUR] Dans le résumé : « toSafeString() » n'existe pas (c'est buildSafe()).
    // Point 10 : avec Ktor ou Retrofit en suspend, withContext(Dispatchers.IO) est inutile (déjà main-safe).
    println("""
        1. DTOs AVEC @Serializable : ChatMessage, ChatRequest, ChatResponse avec
           kotlinx.serialization. @SerialName pour mapping JSON (max_tokens → maxTokens).

        2. VALIDATION DANS INIT : require() pour vérifier role, messages non vide,
           temperature 0..2, maxTokens > 0. Fail-fast dès la construction.

        3. KTOR CLIENT : Configuration avec ContentNegotiation, JsonPlugin, timeouts.
           HttpClient(CIO) recommandé pour backend, OkHttp pour Android.

        4. TIMEOUTS OBLIGATOIRES : connect (10s), request (30-60s pour OpenAI), socket.
           Sans timeout, risque de hang infini sur requêtes lentes/bloquées.

        5. HEADERS : Authorization Bearer avec API key, Content-Type application/json,
           User-Agent custom. Masquer token dans logs (take(7)...).

        6. GESTION D'ERREURS : Sealed class HttpResult<T> avec Success et erreurs typées
           (ClientError 4xx, ServerError 5xx, NetworkError, TimeoutError, DeserializationError).

        7. STATUS HTTP : 200-299 succès, 401 API key invalide, 429 rate limit, 5xx erreur
           serveur. Parser OpenAiErrorResponse pour message d'erreur détaillé.

        8. SÉRIALISATION JSON : Json { ignoreUnknownKeys = true } pour tolérer champs
           supplémentaires de l'API. encodeDefaults = true si nécessaire.

        9. HELPER FUNCTIONS : ChatMessage.system/user/assistant(), firstContent() sur
           ChatResponse, toSafeString() pour logging sans exposer secrets.

        10. SUSPEND FUNCTIONS : Appels HTTP avec suspend fun, utiliser withContext(Dispatchers.IO)
            si nécessaire, withTimeout() pour contrôle supplémentaire. Pas de runBlocking.
    """.trimIndent())
}
