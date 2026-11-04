// ==============================================================================
// FICHE FORMATEUR — Transformations de collections (map, filter, flatMap, mapNotNull…)
// Objectif : écrire des pipelines de collections lisibles, sans boucle ni liste mutable.
// À présenter (dans l'ordre) :
//   1. map / filter : renvoient une NOUVELLE liste, l'originale est intacte
//   2. mapNotNull (avec toIntOrNull) et filterNotNull : List<T?> → List<T>
//   3. flatMap vs flatten
//   4. Chaînage, puis partition et associateBy/associateWith
// Lien Android : mapping DTO → modèle dans le repository (`dtos.map { it.toDomain() }`, TP04) ; filtrage d'état
//   dans un ViewModel (`tasks.filter { !it.done }`, TP07) ; `Flow.map { }` / `filter { }` ont les mêmes noms (TP07, TP10).
// Pièges / questions fréquentes : chaque opérateur crée une liste intermédiaire ; map sur une Map renvoie une List
//   (d'où mapValues/mapKeys) ; « it » ambigu dans les lambdas imbriquées → nommer le paramètre.
// Si peu de temps : map, filter, mapNotNull, chaînage, partition ; [SAUTER] flatten, mapKeys, performance.
// ==============================================================================
/**
 * Exemple 2 : Transformations
 *
 * map : transformer chaque élément
 * filter : filtrer selon un prédicat
 * flatMap : aplatir et transformer
 * mapNotNull : transformer en ignorant les nulls
 * filterNotNull : filtrer les nulls
 */

// ===== map : transformer chaque élément =====

fun demonstrateMap() {
    println("=== map : transformer chaque élément ===")

    val numbers = listOf(1, 2, 3, 4, 5)
    // [À DIRE] map ne modifie pas `numbers` : elle renvoie une nouvelle List (immuable en lecture).
    // [ANDROID] Usage n°1 en Android : convertir les DTO réseau en modèle d'UI, ex. `todoDtos.map { it.toTodo() }`.
    val doubled = numbers.map { it * 2 }
    println("numbers: $numbers")
    println("doubled: $doubled")

    val names = listOf("alice", "bob", "charlie")
    val upperCase = names.map { it.uppercase() }
    println("\nnames: $names")
    println("upperCase: $upperCase")

    // map sur Map
    val ages = mapOf("Alice" to 30, "Bob" to 25)
    // [PIÈGE] `ages.map { ... }` renverrait une List, pas une Map : pour garder les clés, mapValues.
    val nextYear = ages.mapValues { (_, age) -> age + 1 }
    println("\nages: $ages")
    println("nextYear: $nextYear")

    println("\n✅ map : applique une fonction à chaque élément")
}

// ===== mapIndexed : avec l'index =====

fun demonstrateMapIndexed() {
    println("\n=== mapIndexed : avec l'index ===")

    val names = listOf("Alice", "Bob", "Charlie")
    val numbered = names.mapIndexed { index, name ->
        "${index + 1}. $name"
    }
    println("names: $names")
    println("numbered: $numbered")

    println("\n✅ mapIndexed : accès à l'index pendant la transformation")
}

// ===== filter : filtrer selon un prédicat =====

fun demonstrateFilter() {
    println("\n=== filter : filtrer selon un prédicat ===")

    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    // [ANDROID] Ex. recherche dans le TP05 : `recipes.filter { it.title.contains(query, ignoreCase = true) }`
    // calculé à partir de l'état `var query by remember { mutableStateOf("") }`.
    val evens = numbers.filter { it % 2 == 0 }
    println("numbers: $numbers")
    println("evens: $evens")

    val names = listOf("Alice", "Bob", "Charlie", "David")
    val longNames = names.filter { it.length > 4 }
    println("\nnames: $names")
    println("longNames: $longNames")

    println("\n✅ filter : garde les éléments qui satisfont le prédicat")
}

// ===== filter sur Map =====

fun demonstrateFilterOnMap() {
    println("\n=== filter sur Map ===")

    // [SAUTER] Section Map à survoler : citer filterKeys/filterValues, montrer seulement le filter déstructuré.
    val ages = mapOf("Alice" to 30, "Bob" to 25, "Charlie" to 35, "David" to 22)

    // Filtrer sur les clés
    val startsWithA = ages.filterKeys { it.startsWith("A") }
    println("ages: $ages")
    println("startsWithA: $startsWithA")

    // Filtrer sur les valeurs
    val over30 = ages.filterValues { it >= 30 }
    println("over30: $over30")

    // Filtrer sur les paires
    val filtered = ages.filter { (name, age) -> age > 25 && name.length > 3 }
    println("filtered (age>25 && name.length>3): $filtered")

    println("\n✅ filterKeys, filterValues pour Map")
}

// ===== flatMap : aplatir et transformer =====

fun demonstrateFlatMap() {
    println("\n=== flatMap : aplatir et transformer ===")

    val lists = listOf(
        listOf(1, 2, 3),
        listOf(4, 5),
        listOf(6, 7, 8)
    )
    // [À DIRE] flatMap = map dont le lambda renvoie une collection, puis aplatissement. `flatMap { it }` == flatten().
    // [ANDROID] Ex. : toutes les étapes de toutes les recettes, `recipes.flatMap { it.steps }` (TP05).
    val flattened = lists.flatMap { it }
    println("lists: $lists")
    println("flattened: $flattened")

    // Exemple pratique : obtenir toutes les lettres
    val words = listOf("hello", "world")
    val letters = words.flatMap { it.toList() }
    println("\nwords: $words")
    println("letters: $letters")

    // flatMap avec transformation
    val numbers = listOf(1, 2, 3)
    val pairs = numbers.flatMap { n -> listOf(n, n * 10) }
    println("\nnumbers: $numbers")
    println("pairs: $pairs")

    println("\n✅ flatMap : transforme + aplatit en une seule opération")
}

// ===== flatten : juste aplatir =====

fun demonstrateFlatten() {
    println("\n=== flatten : juste aplatir ===")

    val lists = listOf(
        listOf(1, 2, 3),
        listOf(4, 5),
        listOf(6, 7, 8)
    )
    val flattened = lists.flatten()
    println("lists: $lists")
    println("flattened: $flattened")

    println("\n✅ flatten : aplatit une collection de collections")
}

// ===== mapNotNull : transformer en ignorant les nulls =====

fun demonstrateMapNotNull() {
    println("\n=== mapNotNull : transformer en ignorant les nulls ===")

    val strings = listOf("1", "2", "abc", "3", "def", "4")
    // [À DIRE] Combo très idiomatique : `toIntOrNull()` + mapNotNull = parser en ignorant les valeurs invalides,
    // sans try/catch. Le type résultat est List<Int>, pas List<Int?>.
    val numbers = strings.mapNotNull { it.toIntOrNull() }
    println("strings: $strings")
    println("numbers: $numbers")

    // Exemple avec objets
    data class Person(val name: String, val email: String?)
    val people = listOf(
        Person("Alice", "alice@example.com"),
        Person("Bob", null),
        Person("Charlie", "charlie@example.com")
    )
    val emails = people.mapNotNull { it.email }
    println("\npeople: $people")
    println("emails: $emails")

    println("\n✅ mapNotNull : map + filtre automatique des nulls")
}

// ===== filterNotNull : filtrer les nulls =====

fun demonstrateFilterNotNull() {
    println("\n=== filterNotNull : filtrer les nulls ===")

    val mixed: List<String?> = listOf("Alice", null, "Bob", null, "Charlie")
    val nonNull = mixed.filterNotNull()
    println("mixed: $mixed")
    println("nonNull: $nonNull")
    // [ATTENTION FORMATEUR] Cette ligne affiche la classe d'exécution (« ArrayList »), pas « List<String> » :
    // le paramètre de type est effacé (cf. 07-generics/04-Reified.kt). Le gain est sur le type STATIQUE
    // (List<String> au lieu de List<String?>) : le montrer en survolant `nonNull` dans l'IDE. Ce n'est pas un smart cast.
    println("type: ${nonNull::class.simpleName}")  // List<String> (non-nullable)

    println("\n✅ filterNotNull : élimine les nulls + smart cast")
}

// ===== mapKeys : transformer les clés d'un Map =====
// [SAUTER] Symétrique de mapValues. [PIÈGE] si deux clés deviennent identiques, la dernière écrase l'autre.

fun demonstrateMapKeys() {
    println("\n=== mapKeys : transformer les clés ===")

    val ages = mapOf("alice" to 30, "bob" to 25, "charlie" to 35)
    val upperKeys = ages.mapKeys { (key, _) -> key.uppercase() }
    println("ages: $ages")
    println("upperKeys: $upperKeys")

    println("\n✅ mapKeys : transforme les clés d'un Map")
}

// ===== Chaînage de transformations =====

fun demonstrateChaining() {
    println("\n=== Chaînage de transformations ===")

    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    val result = numbers
        .filter { it % 2 == 0 }      // [2, 4, 6, 8, 10]
        .map { it * it }             // [4, 16, 36, 64, 100]
        .filter { it < 50 }          // [4, 16, 36]

    println("numbers: $numbers")
    println("result: $result")

    // Exemple plus complexe
    data class User(val name: String, val age: Int, val city: String?)
    val users = listOf(
        User("Alice", 30, "Paris"),
        User("Bob", 25, null),
        User("Charlie", 35, "Lyon"),
        User("David", 22, null)
    )

    // [À DIRE] Ordre conseillé : filtrer d'abord (moins d'éléments à transformer), une opération par ligne.
    // [ANDROID] Même style dans un ViewModel : `repository.tasks.map { list -> list.filter { !it.done } }`
    // — le map externe est celui de Flow, le filter interne celui de List.
    val cities = users
        .filter { it.age >= 25 }
        .mapNotNull { it.city }
        .map { it.uppercase() }

    println("\nusers: $users")
    println("cities (age>=25): $cities")

    println("\n✅ Chaîner les opérations pour des transformations complexes")
}

// ===== partition : diviser en deux =====

fun demonstratePartition() {
    println("\n=== partition : diviser en deux ===")

    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    // [À DIRE] partition renvoie un Pair<List, List> → déstructuration directe (cf. 08/03-Tuples).
    // [ANDROID] Ex. TP07 : `val (done, todo) = tasks.partition { it.done }` pour deux sections d'une LazyColumn.
    val (evens, odds) = numbers.partition { it % 2 == 0 }
    println("numbers: $numbers")
    println("evens: $evens")
    println("odds: $odds")

    data class User(val name: String, val active: Boolean)
    val users = listOf(
        User("Alice", true),
        User("Bob", false),
        User("Charlie", true),
        User("David", false)
    )
    val (active, inactive) = users.partition { it.active }
    println("\nusers: $users")
    println("active: $active")
    println("inactive: $inactive")

    println("\n✅ partition : divise selon un prédicat en deux listes")
}

// ===== associate : créer un Map =====

fun demonstrateAssociate() {
    println("\n=== associate : créer un Map ===")

    val names = listOf("Alice", "Bob", "Charlie")

    // associateWith : valeur calculée pour chaque élément
    val lengths = names.associateWith { it.length }
    println("names: $names")
    println("lengths: $lengths")

    // associateBy : clé calculée pour chaque élément
    // [PIÈGE] associateBy : si deux éléments ont la même clé, le dernier gagne (ici pas de doublon de longueur,
    // mais tester avec "Alice" et "Chloé"). Pour garder tous les éléments : groupBy (fichier 03).
    val byLength = names.associateBy { it.length }
    println("byLength: $byLength")

    // associate : clé et valeur calculées
    val pairs = names.associate { it to it.length }
    println("pairs: $pairs")

    println("\n✅ associateWith, associateBy, associate pour créer Map")
}

// ===== Performance : éviter les copies inutiles =====

fun demonstratePerformance() {
    println("\n=== Performance : éviter les copies inutiles ===")

    val numbers = (1..1_000_000).toList()

    // ❌ Crée une copie intermédiaire à chaque étape
    val result1 = numbers
        .map { it * 2 }     // Copie 1
        .filter { it > 10 } // Copie 2

    // [ATTENTION FORMATEUR] Le fichier 05-Sequences.kt référencé ici n'existe pas dans 10-stdlib/.
    // [À DIRE] Sequence = évaluation paresseuse élément par élément ; utile pour de gros volumes ou beaucoup d'étapes,
    // pas systématiquement plus rapide sur de petites listes. Sur des listes d'UI de quelques centaines d'items : List.
    // ✅ Utiliser des sequences pour lazy evaluation (voir 05-Sequences.kt)
    val result2 = numbers
        .asSequence()
        .map { it * 2 }
        .filter { it > 10 }
        .toList()

    println("result1.size: ${result1.size}")
    println("result2.size: ${result2.size}")

    println("\n💡 Pour grandes collections : utiliser sequences")
}

// ===== Bonnes pratiques =====

fun demonstrateBestPractices() {
    println("\n=== Bonnes pratiques ===")

    println("✅ map : transformer chaque élément")
    println("✅ filter : sélectionner selon prédicat")
    println("✅ flatMap : transformer + aplatir")
    println("✅ mapNotNull : ignorer les nulls automatiquement")
    println("✅ filterNotNull : éliminer les nulls avec smart cast")
    println("✅ Chaîner les opérations pour lisibilité")
    println("✅ partition : diviser en deux listes")
    println("✅ associate : créer des Maps")

    println("\n💡 Principe :")
    println("  Préférer les transformations fonctionnelles")
    println("  Éviter les mutations")
    println("  Chaîner pour plus de lisibilité")
}

fun main() {
    demonstrateMap()
    demonstrateMapIndexed()
    demonstrateFilter()
    demonstrateFilterOnMap()
    demonstrateFlatMap()
    demonstrateFlatten()
    demonstrateMapNotNull()
    demonstrateFilterNotNull()
    demonstrateMapKeys()
    demonstrateChaining()
    demonstratePartition()
    demonstrateAssociate()
    demonstratePerformance()
    demonstrateBestPractices()

    println("\n=== Résumé ===")
    println("1. map : transforme chaque élément (it * 2)")
    println("2. mapIndexed : transformation avec index")
    println("3. filter : garde éléments selon prédicat")
    println("4. flatMap : transforme + aplatit")
    println("5. flatten : aplatit collection de collections")
    println("6. mapNotNull : map + filtre nulls")
    println("7. filterNotNull : élimine nulls + smart cast")
    println("8. partition : divise en (matching, non-matching)")
    println("9. associate : crée Map depuis List")
    println("10. Chaîner pour transformations complexes")
}
