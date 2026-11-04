// ==============================================================================
// FICHE FORMATEUR — Retrofit en Kotlin pur (sans Android)
// Objectif : montrer un client REST complet hors d'Android, avec ses dépendances Gradle.
// À présenter (dans l'ordre) :
//   1. build.gradle.kts + gradle/libs.versions.toml : plugins kotlin.jvm + serialization,
//      dépendances Retrofit, converter, OkHttp logging, kotlinx-serialization, coroutines
//   2. DTO @Serializable (TodoDto, NouveauTodoDto, MajTodoDto) et Json { … }
//   3. TodoApi : une interface annotée, Retrofit génère l'implémentation
//   4. Main.kt : GET / POST / PATCH / DELETE, erreur 404, appels parallèles avec async
// Lien Android : chapitre 04 et TP04 (tp04-client-todos), mêmes DTO et même interface ;
//   sur Android, seuls changent l'URL (10.0.2.2), la permission INTERNET et le scope (viewModelScope).
// Pièges / questions fréquentes : id en chaîne avec JSON Server 1.x mais numérique avec
//   JSONPlaceholder ; plugin serialization oublié ; version de kotlinx-serialization trop récente
//   pour la version de Kotlin ; baseUrl sans « / » final.
// Si peu de temps : TodoApi.kt + Main.kt (étapes 1, 4 et 6).
// ==============================================================================
package retrofit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------------------
// DTO : collent exactement au JSON de /todos
// ---------------------------------------------------------------------------

/** GET : réponse. */
@Serializable
data class TodoDto(
    val userId: Int,
    @Serializable(with = IdSerializer::class)
    val id: String,                     // "1" avec JSON Server 1.x, 1 avec JSONPlaceholder
    val title: String,
    val completed: Boolean = false,
)

/** POST : pas d'id, c'est le serveur qui l'attribue. */
@Serializable
data class NouveauTodoDto(
    val userId: Int,
    val title: String,
    val completed: Boolean,
)

/** PATCH : seulement les champs modifiés (avec explicitNulls = false). */
@Serializable
data class MajTodoDto(
    val title: String? = null,
    val completed: Boolean? = null,
)

/**
 * Accepte un id numérique (JSONPlaceholder) ou en chaîne (JSON Server 1.x),
 * et le garde toujours en String côté Kotlin.
 */
object IdSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Id", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String =
        if (decoder is JsonDecoder) decoder.decodeJsonElement().jsonPrimitive.content
        else decoder.decodeString()

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

// ---------------------------------------------------------------------------
// L'API : une interface annotée, Retrofit génère l'implémentation
// ---------------------------------------------------------------------------

interface TodoApi {

    @GET("todos")
    suspend fun lister(
        @Query("userId") utilisateurId: Int? = null,      // null → paramètre absent de l'URL
        @Query("completed") terminee: Boolean? = null,
    ): List<TodoDto>

    @GET("todos/{id}")
    suspend fun lire(@Path("id") id: String): TodoDto

    @POST("todos")
    suspend fun creer(@Body todo: NouveauTodoDto): TodoDto

    @PATCH("todos/{id}")
    suspend fun modifier(@Path("id") id: String, @Body maj: MajTodoDto): TodoDto

    @DELETE("todos/{id}")
    suspend fun supprimer(@Path("id") id: String): Response<Unit>   // code HTTP sans corps
}

// ---------------------------------------------------------------------------
// Construction du client
// ---------------------------------------------------------------------------

val json = Json {
    ignoreUnknownKeys = true      // un champ ajouté côté serveur ne fait pas planter
    explicitNulls = false         // un PATCH n'envoie pas "title": null
}

fun creerTodoApi(baseUrl: String, journaliser: Boolean = false): TodoApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (journaliser) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        })
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)                                  // doit finir par « / »
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TodoApi::class.java)
}
