// ==============================================================================
// FICHE FORMATEUR — Data classes
// Objectif : montrer qu'une data class remplace un POJO Java en une ligne (equals/hashCode/toString/copy/componentN).
// À présenter (dans l'ordre) :
//   1. Comparaison avec la classe « style Java »
//   2. toString et == (égalité structurelle) vs === (référence)
//   3. copy(...) pour « modifier » un objet immuable
//   4. Paramètres par défaut + arguments nommés
//   5. Déstructuration (val (id, name, email) = user)
// Lien Android : modèles et DTO réseau (`@Serializable data class TodoDto(...)`, TP04) ;
//   état d'écran `data class UiState(val isLoading: Boolean = false, ...)` mis à jour via
//   `_uiState.update { it.copy(isLoading = true) }` (TP07) ; Compose s'appuie sur equals
//   pour éviter des recompositions inutiles.
// Pièges / questions fréquentes : seules les propriétés du constructeur primaire comptent
//   dans equals/copy ; copy est une copie superficielle ; préférer val dans une data class.
// ==============================================================================
/**
 * Exemple 5 : Data classes
 *
 * Démonstration de l'élimination du boilerplate avec data class
 * Génération automatique : equals, hashCode, toString, copy
 */

// Classe Java traditionnelle (pour comparaison conceptuelle)
class UserJavaStyle(
    private val id: Long,
    private val name: String,
    private val email: String
) {
    // Il faudrait implémenter : getters, equals, hashCode, toString, copy...
}

// Équivalent Kotlin : data class
data class User(
    val id: Long,
    val name: String,
    val email: String
)

// Data class avec valeurs par défaut
data class Product(
    val id: Long,
    val name: String,
    val price: Double,
    val inStock: Boolean = true
)

fun main() {
    // Création simple
    val user1 = User(1, "Alice", "alice@example.com")
    val user2 = User(1, "Alice", "alice@example.com")
    val user3 = User(2, "Bob", "bob@example.com")

    // toString automatique
    println(user1) // User(id=1, name=Alice, email=alice@example.com)

    // equals et hashCode automatiques
    println("user1 == user2: ${user1 == user2}") // true (même valeurs)
    println("user1 == user3: ${user1 == user3}") // false
    println("user1 === user2: ${user1 === user2}") // false (références différentes)

    // Destructuration
    val (id, name, email) = user1
    println("ID: $id, Name: $name, Email: $email")

    // [ANDROID] C'est LE geste du ViewModel : `_uiState.update { it.copy(todos = newList) }` (TP07).
    // copy avec modification partielle
    val updatedUser = user1.copy(email = "alice.new@example.com")
    println("Original: $user1")
    println("Copie modifiée: $updatedUser")

    // [ANDROID] Arguments nommés + valeurs par défaut = style de toutes les API Compose :
    //   `Text(text = "...", modifier = Modifier.padding(8.dp), color = ...)`.
    // [PIÈGE] Price en Double pour de l'argent : OK pour la démo, mais en vrai on évite (arrondis) → Long en centimes ou BigDecimal.
    // Named arguments + valeurs par défaut
    val product1 = Product(
        id = 1,
        name = "Laptop",
        price = 999.99
    )

    val product2 = Product(
        id = 2,
        name = "Mouse",
        price = 29.99,
        inStock = false
    )

    println(product1)
    println(product2)

    // [ANDROID] equals/hashCode servent aussi aux clés de `LazyColumn { items(todos, key = { it.id }) { ... } }`
    //   et à distinctUntilChanged sur les Flow/StateFlow.
    // Utilisation dans des collections
    val users = listOf(user1, user2, user3)
    val uniqueUsers = users.toSet() // equals/hashCode utilisés
    println("Utilisateurs uniques: ${uniqueUsers.size}") // 2

    // [À DIRE] Ce n'est pas du vrai « pattern matching » sur la structure : juste des conditions ; le vrai cas utile = when sur sealed (fichier 09).
    // Pattern matching avec when
    fun processUser(user: User) = when {
        user.name.startsWith("A") -> "Nom commence par A"
        user.id > 5 -> "ID élevé"
        else -> "Utilisateur standard"
    }

    println(processUser(user1))
}
