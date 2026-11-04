// ==============================================================================
// FICHE FORMATEUR — Pair, Triple et déstructuration
// Objectif : savoir écrire `val (a, b) = ...` et choisir entre Pair et data class.
// À présenter (dans l'ordre) :
//   1. `to` crée un Pair (infix) → c'est ce qu'on écrit dans mapOf("a" to 1)
//   2. Déstructuration = appels à component1(), component2()… générés par les data class
//   3. Déstructuration dans for, sur Map et dans les paramètres de lambda
//   4. Règle : Pair pour du local, data class dès que ça sort d'une fonction publique
// Lien Android : `var text by remember { mutableStateOf("") }` vs `val (text, setText) = remember { mutableStateOf("") }`
//   (J2) ; `val (active, done) = tasks.partition { !it.done }` dans un ViewModel (TP07) ;
//   `itemsIndexed(list) { index, item -> }` dans une LazyColumn.
// Pièges / questions fréquentes : la déstructuration est POSITIONNELLE (pas par nom) ; `(a, b)` dans une lambda ≠ `a, b`.
// Si peu de temps : `to`, déstructuration de data class, for sur Map, lambda `{ (k, v) -> }`, bonnes pratiques.
// ==============================================================================
/**
 * Exemple 3 : Tuples et Déstructuration
 *
 * Pair et Triple
 * Opérateur to
 * Déstructuration de data class
 * Déstructuration dans les boucles
 * withIndex()
 * Pair/Triple vs data class
 */

// ===== Pair : tuple de 2 éléments =====

fun demonstratePair() {
    println("=== Pair : tuple de 2 éléments ===")

    val p: Pair<String, Int> = Pair("Ada", 36)
    println("p: $p")
    println("p.first: ${p.first}")
    println("p.second: ${p.second}")

    println("\n✅ Pair<A, B> : tuple de 2 éléments")
}

// ===== Opérateur to =====

fun demonstrateToOperator() {
    println("\n=== Opérateur to ===")

    // [À DIRE] `to` n'est pas un mot-clé : c'est une fonction infix de la stdlib (`infix fun A.to(that: B)`).
    val p = "Ada" to 36
    println("p: $p")
    println("Type: ${p::class}")

    val map = mapOf(
        "Alice" to 30,
        "Bob" to 25,
        "Charlie" to 35
    )
    println("\nmap: $map")

    println("\n✅ 'to' crée un Pair de manière concise")
}

// ===== Triple : tuple de 3 éléments =====
// [SAUTER] Identique à Pair avec un troisième champ : le mentionner suffit.

fun demonstrateTriple() {
    println("\n=== Triple : tuple de 3 éléments ===")

    val t: Triple<String, Int, String> = Triple("Alice", 30, "Paris")
    println("t: $t")
    println("t.first: ${t.first}")
    println("t.second: ${t.second}")
    println("t.third: ${t.third}")

    println("\n✅ Triple<A, B, C> : tuple de 3 éléments")
}

// ===== Déstructuration de Pair =====

fun demonstrateDestructuringPair() {
    println("\n=== Déstructuration de Pair ===")

    val p = "Kotlin" to 10

    // [À DIRE] Traduit par le compilateur en `val name = p.component1(); val version = p.component2()`.
    val (name, version) = p
    println("p: $p")
    println("name: $name")
    println("version: $version")

    println("\n✅ La déstructuration décompose le Pair en variables")
}

// ===== Déstructuration de Triple =====

fun demonstrateDestructuringTriple() {
    println("\n=== Déstructuration de Triple ===")

    val t = Triple("Alice", 30, "Paris")

    val (name, age, city) = t
    println("t: $t")
    println("name: $name, age: $age, city: $city")

    println("\n✅ La déstructuration fonctionne aussi avec Triple")
}

// ===== Ignorer des éléments avec _ =====

fun demonstrateIgnoringComponents() {
    println("\n=== Ignorer des éléments ===")

    val t = Triple("Alice", 30, "Paris")

    // [À DIRE] `_` évite le warning « variable inutilisée » et n'appelle pas le componentN correspondant.
    val (name, _, city) = t
    println("name: $name, city: $city")

    val p = "Kotlin" to 10
    val (lang, _) = p
    println("lang: $lang")

    println("\n💡 _ permet d'ignorer des composants")
}

// ===== Retourner un Pair depuis une fonction =====

fun divide(a: Int, b: Int): Pair<Int, Int> {
    return a / b to a % b
}

fun demonstrateReturningPair() {
    println("\n=== Retourner un Pair ===")

    val (quotient, remainder) = divide(17, 5)
    println("17 / 5 = $quotient reste $remainder")

    println("\n💡 Pair utile pour retourner 2 valeurs")
}

// ===== data class vs Pair =====

// [ANDROID] « Traverser une boundary » = repository → ViewModel → UI : on y met toujours des data class
// (ex. `data class TaskUiState(val tasks: List<Task>, val isLoading: Boolean)`), jamais un Pair.
data class User(val name: String, val age: Int)

fun demonstrateDataClassVsPair() {
    println("\n=== data class vs Pair ===")

    // Avec Pair
    val p: Pair<String, Int> = "Alice" to 30
    println("Pair: ${p.first}, ${p.second}")  // ❌ first/second peu lisible

    // Avec data class
    val u = User("Alice", 30)
    println("User: ${u.name}, ${u.age}")  // ✅ name/age très clair

    println("\n💡 Règle : si le tuple traverse une boundary → data class")
    println("Pair/Triple OK pour du code local/temporaire")
}

// ===== Déstructuration de data class =====

fun demonstrateDestructuringDataClass() {
    println("\n=== Déstructuration de data class ===")

    data class Point(val x: Int, val y: Int)

    val p = Point(10, 20)
    val (x, y) = p

    println("p: $p")
    println("x: $x, y: $y")

    println("\n✅ data class génère automatiquement componentN()")
}

// ===== Déstructuration avec plusieurs propriétés =====

fun demonstrateMultipleComponentDataClass() {
    println("\n=== data class avec plusieurs composants ===")

    // [PIÈGE] Déstructuration positionnelle : si on réordonne les propriétés de la data class (ex. age avant
    // name), `val (name, age) = person` compile toujours mais les valeurs sont inversées (si mêmes types) !
    data class Person(val name: String, val age: Int, val city: String)

    val person = Person("Alice", 30, "Paris")

    val (name, age, city) = person
    println("person: $person")
    println("name: $name, age: $age, city: $city")

    // Ignorer des composants
    val (n, _, c) = person
    println("name: $n, city: $c")

    println("\n✅ componentN() généré pour chaque propriété (dans l'ordre)")
}

// ===== Déstructuration dans les boucles =====

fun demonstrateDestructuringInLoop() {
    println("\n=== Déstructuration dans les boucles ===")

    val pairs = listOf("a" to 1, "b" to 2, "c" to 3)

    for ((k, v) in pairs) {
        println("$k = $v")
    }

    println("\n✅ Déstructuration très utile dans les for")
}

// ===== Déstructuration de Map =====

fun demonstrateDestructuringMap() {
    println("\n=== Déstructuration de Map ===")

    val map = mapOf(
        "Alice" to 30,
        "Bob" to 25,
        "Charlie" to 35
    )

    // [À DIRE] Fonctionne car Map.Entry a component1() (key) et component2() (value) en extension.
    for ((name, age) in map) {
        println("$name : $age ans")
    }

    println("\n✅ Itérer sur une Map avec déstructuration")
}

// ===== withIndex() =====

fun demonstrateWithIndex() {
    println("\n=== withIndex() ===")

    val names = listOf("Alice", "Bob", "Charlie")

    // [ANDROID] Équivalent Compose : `LazyColumn { itemsIndexed(names) { index, name -> Text("$index. $name") } }`.
    for ((index, name) in names.withIndex()) {
        println("$index -> $name")
    }

    println("\n✅ withIndex() retourne IndexedValue (déstructurable)")
}

// ===== Déstructuration dans les lambdas =====

fun demonstrateDestructuringInLambda() {
    println("\n=== Déstructuration dans les lambdas ===")

    val pairs = listOf("a" to 1, "b" to 2, "c" to 3)

    // [PIÈGE] `{ (k, v) -> }` = UN paramètre déstructuré ; `{ k, v -> }` = DEUX paramètres. Avec map sur une
    // liste de Pair, la 2e forme ne compile pas. (Pour mapIndexed, c'est `{ index, item -> }`, sans parenthèses.)
    val result = pairs.map { (k, v) ->
        "$k=$v"
    }

    println("result: $result")

    println("\n✅ Déstructuration fonctionne dans les paramètres de lambda")
}

// ===== Déstructuration de Map dans lambda =====

fun demonstrateMapInLambda() {
    println("\n=== Map dans lambda ===")

    val map = mapOf("x" to 10, "y" to 20)

    // [À DIRE] `key` n'est pas utilisé : l'IDE propose `(_, value)` — bonne occasion de rappeler `_`.
    val doubled = map.mapValues { (key, value) ->
        value * 2
    }

    println("map: $map")
    println("doubled: $doubled")

    println("\n💡 mapValues déstructure automatiquement les entries")
}

// ===== Cas d'usage : swap =====
// [SAUTER] Swap et min/max : redites de « retourner un Pair ».

fun <T> swap(p: Pair<T, T>): Pair<T, T> {
    val (a, b) = p
    return b to a
}

fun demonstrateSwap() {
    println("\n=== Swap avec Pair ===")

    val p = 1 to 2
    val swapped = swap(p)

    println("p: $p")
    println("swapped: $swapped")

    println("\n💡 Pair utile pour des opérations temporaires")
}

// ===== Cas d'usage : retourner min/max =====

fun minMax(numbers: List<Int>): Pair<Int, Int>? {
    if (numbers.isEmpty()) return null
    // [PIÈGE] `!!` : acceptable ici car la liste est testée juste avant, mais à éviter en Android (crash = NPE).
    // Plus simple : `numbers.min()` (Kotlin 1.7+) ou `minOrNull() ?: return null`.
    val min = numbers.minOrNull()!!
    val max = numbers.maxOrNull()!!
    return min to max
}

fun demonstrateMinMax() {
    println("\n=== Min/Max avec Pair ===")

    val numbers = listOf(5, 2, 8, 1, 9, 3)
    val (min, max) = minMax(numbers)!!

    println("numbers: $numbers")
    println("min: $min, max: $max")

    println("\n💡 Pair pour retourner 2 valeurs liées")
}

// ===== Limites de Pair/Triple =====

fun demonstrateLimitations() {
    println("\n=== Limites de Pair/Triple ===")

    val person: Pair<String, Int> = "Alice" to 30

    // ❌ Pas très lisible
    println("Name: ${person.first}")
    println("Age: ${person.second}")

    // [À DIRE] Faire deviner aux stagiaires ce que contient `nested.second.second` : argument imparable pour la data class.
    // Avec plusieurs Pair imbriqués, ça devient vite illisible
    val nested: Pair<String, Pair<Int, String>> = "Alice" to (30 to "Paris")
    println("City: ${nested.second.second}")  // 😱

    println("\n❌ first/second/third peu expressifs")
    println("❌ Pair/Triple imbriqués → illisibles")
    println("✅ Préférer data class pour des structures complexes")
}

// ===== Bonnes pratiques =====

fun demonstrateBestPractices() {
    println("\n=== Bonnes pratiques ===")

    println("✅ Pair/Triple pour :")
    println("   - retours temporaires")
    println("   - code local (pas d'API publique)")
    println("   - prototypes")

    println("\n✅ data class pour :")
    println("   - API publique")
    println("   - structures métier")
    println("   - code qui traverse des modules")

    println("\n✅ Déstructuration pour :")
    println("   - améliorer la lisibilité (ex: (k,v))")
    println("   - boucles for")
    println("   - lambdas courtes")

    println("\n❌ Éviter :")
    println("   - Pair/Triple dans les signatures publiques")
    println("   - Pair/Triple imbriqués")
    println("   - Déstructuration qui perd la signification")
}

fun main() {
    demonstratePair()
    demonstrateToOperator()
    demonstrateTriple()
    demonstrateDestructuringPair()
    demonstrateDestructuringTriple()
    demonstrateIgnoringComponents()
    demonstrateReturningPair()
    demonstrateDataClassVsPair()
    demonstrateDestructuringDataClass()
    demonstrateMultipleComponentDataClass()
    demonstrateDestructuringInLoop()
    demonstrateDestructuringMap()
    demonstrateWithIndex()
    demonstrateDestructuringInLambda()
    demonstrateMapInLambda()
    demonstrateSwap()
    demonstrateMinMax()
    demonstrateLimitations()
    demonstrateBestPractices()

    println("\n=== Résumé ===")
    println("1. Pair<A, B> : tuple de 2 éléments")
    println("2. Triple<A, B, C> : tuple de 3 éléments")
    println("3. 'to' : opérateur pour créer un Pair")
    println("4. Déstructuration : val (a, b) = pair")
    println("5. data class génère componentN() automatiquement")
    println("6. Déstructuration très utile dans les for")
    println("7. withIndex() pour (index, value)")
    println("8. Pair/Triple pour code local, data class pour API")
    println("9. _ pour ignorer des composants")
}
