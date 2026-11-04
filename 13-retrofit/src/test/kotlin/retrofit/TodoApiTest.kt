package retrofit

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.HttpException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Tests sans réseau : MockWebServer joue le rôle de JSON Server. */
class TodoApiTest {

    private val serveur = MockWebServer()
    private lateinit var api: TodoApi

    @BeforeTest
    fun demarrer() {
        serveur.start()
        api = creerTodoApi(serveur.url("/").toString())
    }

    @AfterTest
    fun arreter() = serveur.shutdown()

    @Test
    fun `un id numérique (JSONPlaceholder) devient une chaîne`() {
        val dto = json.decodeFromString<TodoDto>("""{"userId":1,"id":1,"title":"a","completed":false}""")
        assertEquals("1", dto.id)
    }

    @Test
    fun `un id en chaîne (JSON Server 1·x) est conservé`() {
        val dto = json.decodeFromString<TodoDto>("""{"userId":1,"id":"yWeeNCMABkQ","title":"b"}""")
        assertEquals("yWeeNCMABkQ", dto.id)
        assertEquals(false, dto.completed)              // champ absent → valeur par défaut
    }

    @Test
    fun `lister envoie les filtres dans l'URL`() = runTest {
        serveur.enqueue(MockResponse().setBody("""[{"userId":1,"id":"1","title":"a","completed":false}]"""))

        val todos = api.lister(utilisateurId = 1, terminee = false)

        assertEquals(1, todos.size)
        assertEquals("/todos?userId=1&completed=false", serveur.takeRequest().path)
    }

    @Test
    fun `un PATCH n'envoie que le champ modifié`() = runTest {
        serveur.enqueue(MockResponse().setBody("""{"userId":1,"id":"4","title":"t","completed":true}"""))

        api.modifier("4", MajTodoDto(completed = true))

        val requete = serveur.takeRequest()
        assertEquals("PATCH", requete.method)
        assertEquals("""{"completed":true}""", requete.body.readUtf8())
    }

    @Test
    fun `une ressource absente lève HttpException 404`() = runTest {
        serveur.enqueue(MockResponse().setResponseCode(404).setBody("{}"))

        val e = assertFailsWith<HttpException> { api.lire("9999") }
        assertEquals(404, e.code())
    }
}
