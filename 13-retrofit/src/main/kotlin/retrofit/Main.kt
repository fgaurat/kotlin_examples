package retrofit

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException
import java.io.IOException

/**
 * Main.kt — client REST des tâches, en Kotlin pur.
 *
 *   ./gradlew run                                   → JSONPlaceholder (en ligne, écritures simulées)
 *   ./gradlew run --args="http://localhost:3000/"   → JSON Server local (écritures réelles dans db.json)
 */
fun main(args: Array<String>) = runBlocking {          // runBlocking : seulement dans main() ou les tests
    val baseUrl = args.firstOrNull() ?: "https://jsonplaceholder.typicode.com/"
    val api = creerTodoApi(baseUrl, journaliser = "--log" in args)
    println("API : $baseUrl\n")

    try {
        // 1. GET avec filtres : /todos?userId=1&completed=false
        val aFaire = api.lister(utilisateurId = 1, terminee = false)
        println("1. ${aFaire.size} tâche(s) à faire pour l'utilisateur 1, par exemple :")
        aFaire.take(3).forEach { println("   ☐ #${it.id} ${it.title}") }

        // 2. GET d'une ressource : /todos/4
        val todo = api.lire("4")
        println("\n2. Tâche 4 : « ${todo.title} », terminée = ${todo.completed}")

        // 3. POST : le serveur attribue l'id et renvoie l'objet créé (201)
        val cree = api.creer(NouveauTodoDto(userId = 1, title = "Réviser Retrofit", completed = false))
        println("\n3. Créée avec l'id ${cree.id} : « ${cree.title} »")

        // 4. PATCH : on n'envoie que le champ modifié → {"completed":true}
        val maj = api.modifier("4", MajTodoDto(completed = true))
        println("\n4. Tâche 4 après PATCH : terminée = ${maj.completed}")

        // 5. DELETE : Response<Unit> donne le code HTTP sans exception
        val reponse = api.supprimer(cree.id)
        println("\n5. DELETE /todos/${cree.id} → HTTP ${reponse.code()}")

        // 6. Une erreur HTTP lève HttpException : ici, une tâche inexistante
        val erreur = try {
            api.lire("9999")
            "aucune"
        } catch (e: HttpException) {
            "HTTP ${e.code()}"                         // 404
        }
        println("\n6. GET /todos/9999 → $erreur")

        // 7. Deux appels EN PARALLÈLE avec async
        val (t1, t2) = coroutineScope {
            val a = async { api.lire("1") }
            val b = async { api.lire("2") }
            a.await() to b.await()
        }
        println("\n7. En parallèle : « ${t1.title} » et « ${t2.title} »")
    } catch (e: IOException) {
        // Pas de réseau, serveur arrêté (ConnectException), délai dépassé…
        println("Erreur réseau : ${e.message}")
        println("JSON Server est-il lancé ? (npx json-server db.json)")
    }
}
