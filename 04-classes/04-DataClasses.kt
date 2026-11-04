// ==============================================================================
// FICHE FORMATEUR — Data classes (approfondissement)
// Objectif : maîtriser ce que génère une data class, ses limites (copie superficielle) et ses usages.
// À présenter (dans l'ordre) :
//   1. equals/hashCode cohérents → fonctionnement dans Set/Map
//   2. copy() = modifier sans muter (base de l'état immuable)
//   3. Déstructuration (y compris dans les lambdas et sur Map)
//   4. Limite : copy superficielle avec une MutableList
//   5. Bonnes pratiques : val, peu de logique, DTO / événements / value objects
// Lien Android : DTO Retrofit `@Serializable data class TodoDto(val id: Int, val title: String, val completed: Boolean)` (TP04) ;
//   `data class TasksUiState(...)` + `_uiState.update { it.copy(...) }` (TP07) ; Compose compare
//   les paramètres via equals pour sauter des recompositions (classes stables = propriétés val).
// Pièges / questions fréquentes : MutableList dans un état → mutation invisible pour Compose/StateFlow
//   (StateFlow ignore une valeur égale à la précédente) ; toString affiche TOUT (mots de passe, tokens).
// Si peu de temps : Génération automatique, copy, Limitation copy ; survoler Cas d'usage.
// ==============================================================================
/**
 * Exemple 4 : Data classes
 *
 * equals, hashCode, toString, copy, destructuration
 * Bonnes pratiques et limitations
 */

// ===== Data class de base =====

data class User(
    val id: Long,
    val name: String,
    val email: String
)

// Avec valeurs par défaut
data class Config(
    val host: String = "localhost",
    val port: Int = 8080,
    val ssl: Boolean = false
)

// ===== Génération automatique =====

fun demonstrateGeneratedMethods() {
    val user1 = User(1, "Alice", "alice@example.com")
    val user2 = User(1, "Alice", "alice@example.com")
    val user3 = User(2, "Bob", "bob@example.com")

    println("=== toString ===")
    println(user1)  // User(id=1, name=Alice, email=alice@example.com)

    println("\n=== equals ===")
    println("user1 == user2: ${user1 == user2}")  // true (mêmes valeurs)
    println("user1 == user3: ${user1 == user3}")  // false
    println("user1 === user2: ${user1 === user2}") // false (références différentes)

    println("\n=== hashCode ===")
    println("user1.hashCode(): ${user1.hashCode()}")
    println("user2.hashCode(): ${user2.hashCode()}")  // Même hashCode
    println("user3.hashCode(): ${user3.hashCode()}")  // Différent

    // [ANDROID] StateFlow utilise equals : émettre une valeur égale à la précédente ne déclenche rien ;
    //   c'est aussi ce qui permet à Compose de « sauter » un Composable dont les paramètres n'ont pas changé.
    // Utilisation dans Set/Map
    val userSet = setOf(user1, user2, user3)
    println("Set size: ${userSet.size}")  // 2 (user1 == user2)
}

// ===== copy : immutabilité =====

fun demonstrateCopy() {
    println("\n=== copy ===")

    val config1 = Config()
    println("Config original: $config1")

    // [ANDROID] Dans un ViewModel : `_uiState.update { it.copy(isLoading = false, tasks = result) }` (TP07).
    // Copie avec modification partielle
    val config2 = config1.copy(port = 9090)
    println("Config copié: $config2")

    val config3 = config1.copy(host = "prod-server", ssl = true)
    println("Config prod: $config3")

    println("Original inchangé: $config1")
}

// ===== Destructuration =====

fun demonstrateDestructuring() {
    println("\n=== Destructuration ===")

    val user = User(1, "Charlie", "charlie@example.com")

    // [PIÈGE] La déstructuration est POSITIONNELLE (component1, 2, 3), pas par nom : réordonner les
    //   propriétés de la data class change silencieusement le sens de `val (id, name, email)`.
    // Déstructuration en variables séparées
    val (id, name, email) = user
    println("ID: $id, Name: $name, Email: $email")

    // Dans une boucle
    val users = listOf(
        User(1, "Alice", "alice@example.com"),
        User(2, "Bob", "bob@example.com")
    )

    users.forEach { (id, name, _) ->  // _ pour ignorer email
        println("User #$id: $name")
    }

    // Avec Map
    val userMap = users.associateBy { it.id }
    userMap.forEach { (id, user) ->
        println("ID $id -> ${user.name}")
    }
}

// ===== Data class en collections =====

data class Product(val id: String, val name: String, val price: Double)

fun demonstrateInCollections() {
    println("\n=== Data class dans collections ===")

    val products = listOf(
        Product("P001", "Laptop", 999.99),
        Product("P002", "Mouse", 29.99),
        Product("P001", "Laptop", 999.99)  // Doublon
    )

    // Déduplication avec toSet()
    val uniqueProducts = products.toSet()
    println("Produits uniques: ${uniqueProducts.size}")

    // [À DIRE] associateBy : en cas de clé en double (P001), le DERNIER élément gagne.
    // [ANDROID] Clé stable pour les listes : `LazyColumn { items(products, key = { it.id }) { ... } }`.
    // Utilisation comme clé de Map
    val productMap = products.associateBy { it.id }
    println("Map: $productMap")

    // Tri
    val sortedByPrice = products.sortedBy { it.price }
    println("Triés par prix: ${sortedByPrice.map { it.name }}")
}

// ===== Limitations : copy n'est pas profonde =====

// [À DIRE] La vraie solution n'est pas la copie profonde manuelle mais `val members: List<String>` + `copy(members = members + "Charlie")`.
// [ANDROID] Bug classique en Compose : `state.items.add(x)` sur une MutableList → l'écran ne se met pas à jour.
data class Team(val name: String, val members: MutableList<String>)

fun demonstrateCopyLimitation() {
    println("\n=== Limitation : copy superficielle ===")

    val team1 = Team("Dev", mutableListOf("Alice", "Bob"))
    val team2 = team1.copy()

    println("Team1: $team1")
    println("Team2: $team2")

    // Modification de la liste
    team2.members.add("Charlie")

    println("\nAprès ajout dans team2:")
    println("Team1: $team1")  // ⚠️ Modifié aussi !
    println("Team2: $team2")

    // Solution : copie manuelle
    val team3 = team1.copy(members = team1.members.toMutableList())
    team3.members.add("Diana")

    println("\nAprès copie profonde:")
    println("Team1: $team1")  // ✅ Inchangé
    println("Team3: $team3")
}

// ===== Bonnes pratiques =====

// ✅ BON : val dans data class
data class ImmutableUser(
    val id: Long,
    val name: String
)

// ❌ ÉVITER : var dans data class (mutation)
data class MutableUser(
    val id: Long,
    var name: String
)

// ✅ BON : data class pour DTO, événements, value objects
data class UserCreatedEvent(
    val userId: Long,
    val timestamp: Long
)

data class Address(
    val street: String,
    val city: String,
    val zipCode: String
)

// ❌ ÉVITER : data class avec beaucoup de logique métier
// data class Order(...) {
//     fun calculate() { /* logique complexe */ }
//     fun validate() { /* ... */ }
//     // Trop de comportement : ce n'est plus "juste des données"
// }

// ===== Cas d'usage typiques =====

// [ANDROID] Sur Android, un DTO réseau porte `@Serializable` (kotlinx.serialization) et éventuellement
//   `@SerialName("user_name")` si le JSON diffère ; on le convertit en modèle domaine via `fun UserDto.toDomain()` (TP04).
// DTO (Data Transfer Object)
data class UserDto(
    val id: Long,
    val username: String,
    val email: String
)

// [PIÈGE] toString() généré affiche le password en clair (logs, Logcat, crash reports) :
//   surcharger toString ou ne pas mettre de secret dans une data class.
// Configuration
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
    val maxConnections: Int = 10
)

// Événement
data class OrderPlacedEvent(
    val orderId: String,
    val userId: Long,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
)

// [ATTENTION FORMATEUR] Montant en Double : imprécis pour de l'argent (0.1 + 0.2 ≠ 0.3) → Long en centimes ou BigDecimal.
// [À DIRE] `operator fun plus` = surcharge d'opérateur : permet `price1 + price2` ; `require` lève IllegalArgumentException.
// [SAUTER] value class (`@JvmInline value class`) pour les value objects : hors périmètre ici.
// Value Object
data class Money(
    val amount: Double,
    val currency: String = "EUR"
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) { "Currency mismatch" }
        return Money(amount + other.amount, currency)
    }
}

fun main() {
    demonstrateGeneratedMethods()
    demonstrateCopy()
    demonstrateDestructuring()
    demonstrateInCollections()
    demonstrateCopyLimitation()

    println("\n=== Value Object ===")
    val price1 = Money(10.0)
    val price2 = Money(20.0)
    val total = price1 + price2
    println("Total: $total")

    println("\n=== Résumé ===")
    println("1. Data class génère : equals, hashCode, toString, copy, componentN")
    println("2. Préférer val dans data class")
    println("3. copy permet l'immutabilité (attention : copie superficielle)")
    println("4. Destructuration avec componentN")
    println("5. Cas d'usage : DTO, configuration, événements, value objects")
    println("6. Éviter data class avec beaucoup de logique métier")
}
