// ==============================================================================
// FICHE FORMATEUR — Collections et opérations fonctionnelles
// Objectif : manipuler listes/sets/maps avec filter, map, groupBy… au lieu de boucles for.
// À présenter (dans l'ordre) :
//   1. listOf / setOf / mapOf (lecture seule) vs mutableListOf
//   2. filter, map, chaînage (lambdas avec `it`)
//   3. find / any / all / none
//   4. groupBy, partition, fold
//   5. Exemple « Person » : requête lisible type SQL
// Lien Android : préparer les données affichées par `LazyColumn { items(todos) { TodoRow(it) } }` ;
//   filtrer/trier dans le ViewModel (`todos.filter { !it.done }.sortedBy { it.title }`) ;
//   transformer un Flow avec `.map { list -> list.filter { ... } }` (TP07) ; mapper DTO → domaine (TP04).
// Pièges / questions fréquentes : List n'est pas immuable mais « en lecture seule » ;
//   reduce plante sur liste vide (préférer fold/sum) ; find renvoie un nullable.
// Si peu de temps : 1, 2, groupBy et l'exemple pratique ; sauter take/drop et reduce.
// ==============================================================================
/**
 * Exemple 6 : Collections et API riche
 *
 * Démonstration de l'API de collections Kotlin
 * (plus expressive et fonctionnelle que Java)
 */

fun main() {
    // Collections immutables (lecture seule)
    val readOnlyList = listOf("Kotlin", "Java", "Scala")
    val readOnlySet = setOf(1, 2, 3, 2, 1) // Dédupliqué automatiquement
    val readOnlyMap = mapOf(
        "France" to "Paris",
        "Italie" to "Rome",
        "Espagne" to "Madrid"
    )

    println("Liste: $readOnlyList")
    println("Set: $readOnlySet")
    println("Map: $readOnlyMap")

    // [À DIRE] `"France" to "Paris"` crée une Pair : `to` est une fonction infix, pas un mot-clé.
    // [ANDROID] Exposer `List<Todo>` hors du ViewModel, garder le mutable privé : jamais de MutableList dans un UiState.
    // Collections mutables
    val mutableList = mutableListOf("A", "B", "C")
    mutableList.add("D")
    mutableList.removeAt(0)
    println("Liste mutable: $mutableList")

    // Opérations fonctionnelles
    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    // [À DIRE] Chaque opération crée une NOUVELLE liste (l'originale ne bouge pas) ; `it` = paramètre implicite de la lambda.
    // filter
    val evens = numbers.filter { it % 2 == 0 }
    println("Nombres pairs: $evens")

    // map
    val doubled = numbers.map { it * 2 }
    println("Doublés: $doubled")

    // filter + map (chaînage)
    val oddSquares = numbers
        .filter { it % 2 != 0 }
        .map { it * it }
    println("Carrés des impairs: $oddSquares")

    // [PIÈGE] find / firstOrNull renvoient Int? ; first { } lève NoSuchElementException si rien ne correspond.
    // find
    val firstEven = numbers.find { it % 2 == 0 }
    println("Premier pair: $firstEven")

    // any, all, none
    println("Contient un nombre > 5: ${numbers.any { it > 5 }}")
    println("Tous > 0: ${numbers.all { it > 0 }}")
    println("Aucun négatif: ${numbers.none { it < 0 }}")

    // groupBy
    val grouped = numbers.groupBy { if (it % 2 == 0) "pair" else "impair" }
    println("Groupés: $grouped")

    // partition
    val (pairs, impairs) = numbers.partition { it % 2 == 0 }
    println("Pairs: $pairs, Impairs: $impairs")

    // [PIÈGE] reduce sur une liste vide lève une exception ; ici `numbers.sum()` suffirait.
    // reduce / fold
    val sum = numbers.reduce { acc, n -> acc + n }
    val sumWithInitial = numbers.fold(100) { acc, n -> acc + n }
    println("Somme: $sum")
    println("Somme avec initial 100: $sumWithInitial")

    // take / drop
    val firstThree = numbers.take(3)
    val withoutFirstThree = numbers.drop(3)
    println("3 premiers: $firstThree")
    println("Sans les 3 premiers: $withoutFirstThree")

    // Exemple pratique : traitement de données
    data class Person(val name: String, val age: Int, val city: String)

    val people = listOf(
        Person("Alice", 28, "Paris"),
        Person("Bob", 35, "Lyon"),
        Person("Charlie", 22, "Paris"),
        Person("Diana", 31, "Lyon")
    )

    // Trouver les personnes de Paris de plus de 25 ans
    val result = people
        .filter { it.city == "Paris" }
        .filter { it.age > 25 }
        .map { it.name }

    println("Parisiens > 25 ans: $result")

    // [À DIRE] `{ (_, persons) -> }` : déstructuration d'une Map.Entry, `_` = composant ignoré.
    // [ANDROID] groupBy est typique pour une liste à sections : `LazyColumn { grouped.forEach { (ville, gens) -> stickyHeader { ... }; items(gens) { ... } } }`.
    // Moyenne d'âge par ville
    val avgAgeByCity = people
        .groupBy { it.city }
        .mapValues { (_, persons) ->
            persons.map { it.age }.average()
        }

    println("Âge moyen par ville: $avgAgeByCity")
}
