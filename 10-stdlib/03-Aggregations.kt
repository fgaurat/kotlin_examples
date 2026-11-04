// ==============================================================================
// FICHE FORMATEUR — Agrégations (groupBy, associateBy, fold/reduce, tri, distinct)
// Objectif : regrouper, résumer et trier une collection en une expression.
// À présenter (dans l'ordre) :
//   1. groupBy → Map<K, List<V>> vs associateBy → Map<K, V> (clé unique, le dernier gagne)
//   2. sumOf / count / maxByOrNull avant fold/reduce (plus lisibles)
//   3. fold (valeur initiale, type libre) vs reduce (même type, exception si vide)
//   4. sortedBy / sortedWith(compareBy { }.thenBy { }) — tri sans modifier l'original
// Lien Android : en-têtes de section d'une LazyColumn (`grouped.forEach { (cat, items) -> stickyHeader { }; items(items) { } }`),
//   tri dans un ViewModel avant d'exposer le StateFlow (TP07), `associateBy { it.id }` pour un lookup rapide.
// Pièges / questions fréquentes : sorted() ≠ sort() (sort() modifie une MutableList en place) ; reduce sur liste vide
//   → exception ; min()/max() lèvent une exception sur liste vide (préférer minOrNull()).
// Si peu de temps : groupBy, associateBy, sumOf, sortedBy/sortedWith ; [SAUTER] reduce, distinctBy, groupBy avec valueTransform.
// ==============================================================================
/**
 * Exemple 3 : Agrégations
 *
 * groupBy : grouper par clé
 * associateBy : associer par clé unique
 * fold : accumuler avec valeur initiale
 * reduce : accumuler sans valeur initiale
 * sorted : trier
 * distinct : éliminer doublons
 */

// ===== groupBy : grouper par clé =====

fun demonstrateGroupBy() {
    println("=== groupBy : grouper par clé ===")

    val names = listOf("Alice", "Bob", "Charlie", "Anna", "David", "Alex")
    val byFirstLetter = names.groupBy { it.first() }
    println("names: $names")
    println("byFirstLetter: $byFirstLetter")

    // Exemple pratique
    data class Person(val name: String, val age: Int, val city: String)
    val people = listOf(
        Person("Alice", 30, "Paris"),
        Person("Bob", 25, "Lyon"),
        Person("Charlie", 35, "Paris"),
        Person("David", 30, "Lyon")
    )

    // [À DIRE] La Map résultante conserve l'ordre de première apparition des clés (LinkedHashMap).
    // [ANDROID] Ex. TP05 : `recipes.groupBy { it.category }` pour afficher une liste par catégorie,
    // avec `stickyHeader { Text(category) }` dans une LazyColumn.
    val byCity = people.groupBy { it.city }
    println("\npeople: $people")
    println("byCity: $byCity")

    val byAge = people.groupBy { it.age }
    println("byAge: $byAge")

    println("\n✅ groupBy : Map<K, List<V>>")
}

// ===== groupBy avec transformation =====
// [SAUTER] Variante avec valueTransform ; la section « statistiques » plus bas montre l'enchaînement plus utile.

fun demonstrateGroupByWithTransform() {
    println("\n=== groupBy avec transformation ===")

    data class Order(val id: Int, val customer: String, val amount: Double)
    val orders = listOf(
        Order(1, "Alice", 100.0),
        Order(2, "Bob", 200.0),
        Order(3, "Alice", 150.0),
        Order(4, "Charlie", 300.0),
        Order(5, "Bob", 250.0)
    )

    // Grouper par client, ne garder que les montants
    val amountsByCustomer = orders.groupBy(
        keySelector = { it.customer },
        valueTransform = { it.amount }
    )
    println("orders: $orders")
    println("amountsByCustomer: $amountsByCustomer")

    println("\n✅ groupBy avec keySelector et valueTransform")
}

// ===== associateBy : clé unique =====

fun demonstrateAssociateBy() {
    println("\n=== associateBy : clé unique ===")

    data class User(val id: Int, val name: String, val email: String)
    val users = listOf(
        User(1, "Alice", "alice@example.com"),
        User(2, "Bob", "bob@example.com"),
        User(3, "Charlie", "charlie@example.com")
    )

    // Associer par ID
    // [ANDROID] Ex. : retrouver une tâche par id après navigation vers l'écran détail (TP09) sans parcourir
    // la liste à chaque fois : `val tasksById = tasks.associateBy { it.id }` puis `tasksById[taskId]` (nullable).
    // [PIÈGE] Clés en doublon : pas d'erreur, la dernière valeur écrase silencieusement les précédentes.
    val byId = users.associateBy { it.id }
    println("users: $users")
    println("byId: $byId")

    // Lookup rapide
    println("User with id=2: ${byId[2]}")

    // Associer par email, ne garder que le nom
    val nameByEmail = users.associateBy(
        keySelector = { it.email },
        valueTransform = { it.name }
    )
    println("nameByEmail: $nameByEmail")

    println("\n✅ associateBy : Map<K, V> avec clé unique")
}

// ===== fold : accumuler avec valeur initiale =====

fun demonstrateFold() {
    println("\n=== fold : accumuler avec valeur initiale ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // Somme
    // [À DIRE] Dérouler à voix haute : acc=0 → 0+1=1 → 1+2=3 → ... → 15. Le type de acc est celui de la valeur initiale.
    val sum = numbers.fold(0) { acc, n -> acc + n }
    println("numbers: $numbers")
    println("sum: $sum")

    // Produit
    val product = numbers.fold(1) { acc, n -> acc * n }
    println("product: $product")

    // Concaténation
    val names = listOf("Alice", "Bob", "Charlie")
    // [PIÈGE] Résultat avec une virgule finale en trop ; en vrai : `names.joinToString(prefix = "Names: ")`.
    val concatenated = names.fold("Names: ") { acc, name -> "$acc$name, " }
    println("\nnames: $names")
    println("concatenated: $concatenated")

    // Exemple complexe : compter les lettres
    val words = listOf("hello", "world")
    // [À DIRE] Ce fold s'écrit plus lisiblement `words.sumOf { it.length }` : fold quand aucune fonction dédiée n'existe.
    val letterCount = words.fold(0) { acc, word -> acc + word.length }
    println("\nwords: $words")
    println("letterCount: $letterCount")

    println("\n✅ fold : (acc, element) -> acc avec valeur initiale")
}

// ===== reduce : accumuler sans valeur initiale =====
// [PIÈGE] reduce sur une liste vide lève UnsupportedOperationException : en Android, les listes viennent souvent
// du réseau et peuvent être vides → reduceOrNull, ou fold. [SAUTER] si le temps manque (fold suffit).

fun demonstrateReduce() {
    println("\n=== reduce : accumuler sans valeur initiale ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // Somme
    val sum = numbers.reduce { acc, n -> acc + n }
    println("numbers: $numbers")
    println("sum: $sum")

    // Max manuel
    val max = numbers.reduce { acc, n -> if (n > acc) n else acc }
    println("max: $max")

    // ⚠️ Exception si vide
    val empty = emptyList<Int>()
    try {
        empty.reduce { acc, n -> acc + n }
    } catch (e: Exception) {
        println("\nempty.reduce: ${e.message}")
    }

    // Solution : reduceOrNull
    val safeResult = empty.reduceOrNull { acc, n -> acc + n }
    println("empty.reduceOrNull: $safeResult")

    println("\n✅ reduce : comme fold mais sans valeur initiale")
}

// ===== Fonctions d'agrégation prédéfinies =====

fun demonstratePredefinedAggregations() {
    println("\n=== Fonctions d'agrégation prédéfinies ===")

    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    println("numbers: $numbers")
    println("sum: ${numbers.sum()}")
    println("average: ${numbers.average()}")
    // [PIÈGE] min()/max() lèvent NoSuchElementException sur liste vide (depuis Kotlin 1.7 ; avant, elles renvoyaient
    // un nullable) → minOrNull()/maxOrNull() pour des données réseau. average() d'une liste vide = NaN.
    println("min: ${numbers.min()}")
    println("max: ${numbers.max()}")
    println("count: ${numbers.count()}")

    // sumOf : calculer puis sommer
    data class Order(val amount: Double)
    val orders = listOf(Order(100.0), Order(200.0), Order(150.0))
    // [ANDROID] Ex. : total d'un panier affiché dans un Text, `cart.sumOf { it.price * it.quantity }`, calculé dans
    // le ViewModel ou via `val total by remember(cart) { derivedStateOf { ... } }` côté Compose.
    val total = orders.sumOf { it.amount }
    println("\norders: $orders")
    println("total: $total")

    println("\n✅ sum, average, min, max, count, sumOf")
}

// ===== sorted : trier =====

fun demonstrateSorted() {
    println("\n=== sorted : trier ===")

    val numbers = listOf(5, 2, 8, 1, 9, 3)
    println("numbers: $numbers")
    // [PIÈGE] sorted() renvoie une copie triée ; sort() trie une MutableList en place et renvoie Unit.
    // `val x = list.sort()` est une erreur classique (x vaut Unit).
    println("sorted: ${numbers.sorted()}")
    println("sortedDescending: ${numbers.sortedDescending()}")

    val names = listOf("Charlie", "Alice", "Bob", "David")
    println("\nnames: $names")
    println("sorted: ${names.sorted()}")

    println("\n✅ sorted : tri croissant (original non modifié)")
}

// ===== sortedBy : trier avec sélecteur =====

fun demonstrateSortedBy() {
    println("\n=== sortedBy : trier avec sélecteur ===")

    data class Person(val name: String, val age: Int)
    val people = listOf(
        Person("Charlie", 35),
        Person("Alice", 30),
        Person("Bob", 25),
        Person("David", 30)
    )

    println("people: $people")
    // [À DIRE] Tri stable : à âge égal (Alice et David, 30), l'ordre d'origine est conservé.
    println("sortedBy age: ${people.sortedBy { it.age }}")
    println("sortedByDescending age: ${people.sortedByDescending { it.age }}")
    println("sortedBy name: ${people.sortedBy { it.name }}")

    // Tri sur longueur de chaîne
    val names = listOf("Charlie", "Al", "Bob", "Alexander")
    println("\nnames: $names")
    println("sortedBy length: ${names.sortedBy { it.length }}")

    println("\n✅ sortedBy : tri selon une propriété")
}

// ===== sortedWith : tri personnalisé =====

fun demonstrateSortedWith() {
    println("\n=== sortedWith : tri personnalisé ===")

    data class Person(val name: String, val age: Int)
    val people = listOf(
        Person("Charlie", 35),
        Person("Alice", 30),
        Person("Bob", 25),
        Person("David", 30)
    )

    // Trier par age puis par name
    // [ANDROID] Ex. TP07 : `tasks.sortedWith(compareBy<Task> { it.done }.thenByDescending { it.createdAt })`
    // → tâches non faites en premier, les plus récentes en haut. `<Person>` est nécessaire pour l'inférence.
    val sorted = people.sortedWith(
        compareBy<Person> { it.age }
            .thenBy { it.name }
    )
    println("people: $people")
    println("sortedWith (age, name): $sorted")

    println("\n✅ sortedWith + compareBy pour tri multi-critères")
}

// ===== distinct : éliminer doublons =====

fun demonstrateDistinct() {
    println("\n=== distinct : éliminer doublons ===")

    val numbers = listOf(1, 2, 2, 3, 3, 3, 4, 5, 5)
    // [À DIRE] distinct s'appuie sur equals/hashCode : marche avec des data class, pas avec des classes classiques
    // (comparaison par référence) — rappel du chapitre data class.
    println("numbers: $numbers")
    println("distinct: ${numbers.distinct()}")

    val names = listOf("Alice", "Bob", "Alice", "Charlie", "Bob")
    println("\nnames: $names")
    println("distinct: ${names.distinct()}")

    println("\n✅ distinct : élimine les doublons")
}

// ===== distinctBy : éliminer doublons selon clé =====
// [SAUTER] Variante de distinct. [ANDROID] Utile pour dédoublonner par id avant `items(list, key = { it.id })`
// dans une LazyColumn : des clés dupliquées y provoquent un crash (IllegalArgumentException).

fun demonstrateDistinctBy() {
    println("\n=== distinctBy : éliminer doublons selon clé ===")

    data class Person(val name: String, val age: Int)
    val people = listOf(
        Person("Alice", 30),
        Person("Bob", 25),
        Person("Charlie", 30),
        Person("David", 25)
    )

    println("people: $people")
    val uniqueAges = people.distinctBy { it.age }
    println("distinctBy age: $uniqueAges")

    // Premier prénom de chaque longueur
    val names = listOf("Al", "Bob", "Charlie", "Dan", "Eve", "Alexander")
    val uniqueLengths = names.distinctBy { it.length }
    println("\nnames: $names")
    println("distinctBy length: $uniqueLengths")

    println("\n✅ distinctBy : garde le premier de chaque groupe")
}

// ===== Exemple pratique : statistiques =====

fun demonstrateStatistics() {
    println("\n=== Exemple pratique : statistiques ===")

    data class Sale(val product: String, val amount: Double, val region: String)
    val sales = listOf(
        Sale("Laptop", 1000.0, "North"),
        Sale("Phone", 500.0, "South"),
        Sale("Laptop", 1100.0, "North"),
        Sale("Tablet", 300.0, "East"),
        Sale("Phone", 550.0, "North"),
        Sale("Laptop", 1050.0, "South")
    )

    // [À DIRE] Pattern à retenir : groupBy + mapValues { sumOf } = un « GROUP BY ... SUM » SQL en une expression.
    // [ANDROID] Même calcul possible côté base avec Room (`@Query("SELECT region, SUM(amount) ...")`) si les données sont locales.
    // Total par région
    val byRegion = sales.groupBy { it.region }
        .mapValues { (_, regionSales) ->
            regionSales.sumOf { it.amount }
        }
    println("Total by region: $byRegion")

    // Total par produit
    val byProduct = sales.groupBy { it.product }
        .mapValues { (_, productSales) ->
            productSales.sumOf { it.amount }
        }
    println("Total by product: $byProduct")

    // Région avec plus de ventes
    // [À DIRE] Sur une Map, maxByOrNull renvoie une Map.Entry (affichée « North=2650.0 »), pas juste la clé.
    val topRegion = byRegion.maxByOrNull { it.value }
    println("Top region: $topRegion")

    println("\n✅ Combinaison de groupBy, sumOf, maxByOrNull")
}

// ===== Bonnes pratiques =====

fun demonstrateBestPractices() {
    println("\n=== Bonnes pratiques ===")

    println("✅ groupBy : grouper en Map<K, List<V>>")
    println("✅ associateBy : créer Map<K, V> avec clé unique")
    println("✅ fold : accumulation avec valeur initiale")
    println("✅ reduce : accumulation sans valeur initiale")
    println("✅ sorted : tri (crée nouvelle liste)")
    println("✅ distinct : éliminer doublons")
    println("✅ Utiliser sumOf, maxByOrNull pour lisibilité")

    println("\n💡 Principe :")
    println("  fold pour valeur initiale différente")
    println("  reduce pour même type")
    println("  groupBy pour groupements")
    println("  associateBy pour lookups rapides")
}

fun main() {
    demonstrateGroupBy()
    demonstrateGroupByWithTransform()
    demonstrateAssociateBy()
    demonstrateFold()
    demonstrateReduce()
    demonstratePredefinedAggregations()
    demonstrateSorted()
    demonstrateSortedBy()
    demonstrateSortedWith()
    demonstrateDistinct()
    demonstrateDistinctBy()
    demonstrateStatistics()
    demonstrateBestPractices()

    println("\n=== Résumé ===")
    println("1. groupBy : Map<K, List<V>> pour grouper")
    println("2. associateBy : Map<K, V> avec clé unique")
    println("3. fold : accumulation avec valeur initiale")
    println("4. reduce : accumulation sans valeur initiale")
    println("5. sum, average, min, max : agrégations prédéfinies")
    println("6. sorted, sortedBy : tri (immutable)")
    println("7. sortedWith : tri multi-critères")
    println("8. distinct, distinctBy : éliminer doublons")
    println("9. sumOf, maxByOrNull : agrégations sur propriétés")
    println("10. Combiner pour analyses complexes")
}
