// ==============================================================================
// FICHE FORMATEUR — Lambdas avancées et fonctions d'ordre supérieur
// Objectif : lire les signatures à base de types fonction, dont les lambdas AVEC RECEVEUR (T.() -> R) qui fondent Compose et les DSL Gradle.
// À présenter (dans l'ordre) :
//   1. Types fonction (rappel rapide) et bonne pratique : nommer le paramètre si la lambda est longue
//   2. Trailing lambda + fonction maison qui prend une lambda (repeat)
//   3. Références : ::fonction, objet::méthode, ::Constructeur
//   4. Lambda avec receveur `StringBuilder.() -> Unit` vs `(StringBuilder) -> Unit`
//   5. Stdlib (map/filter/groupBy/flatMap…) : rappel, pas de nouveauté
// Lien Android : `@Composable () -> Unit` (paramètre content de tout layout), `ColumnScope.() -> Unit`
//   dans `Column { ... }` (d'où `Modifier.weight()` dispo seulement dedans) ; DSL Gradle Kotlin
//   (`android { defaultConfig { ... } }`, flavors J4) ; `NavHost(...) { composable<Route> { ... } }` (J4) ;
//   références `onClick = viewModel::onRefresh`.
// Pièges / questions fréquentes : différence `this` (receveur) / `it` (paramètre) ; références à une
//   fonction surchargée ambiguës ; « pourquoi weight() n'existe pas ici ? » → hors du scope receveur.
// Si peu de temps : section « Lambda avec receiver » en priorité (clé pour Compose/Gradle), puis
//   Trailing lambda et Références ; sauter Standard library (déjà vu en 01-introduction/06).
// ==============================================================================
/**
 * Exemple 1 : Lambdas avancées
 *
 * Types de fonctions, it, trailing lambda
 * Références de fonctions, receiver
 */

// ===== Types de fonctions =====

fun demonstrateFunctionTypes() {
    println("=== Types de fonctions ===")

    // [ANDROID] `onClick: () -> Unit` est le type le plus fréquent en Compose ; `onValueChange: (String) -> Unit` pour un TextField.
    // () -> Unit : pas de paramètre, retourne Unit
    val action: () -> Unit = { println("Action executed") }
    action()

    // (String) -> Int : un String, retourne Int
    val length: (String) -> Int = { it.length }
    println("Length of 'Kotlin': ${length("Kotlin")}")

    // (Int, Int) -> Int : deux Int, retourne Int
    val add: (Int, Int) -> Int = { a, b -> a + b }
    println("5 + 3 = ${add(5, 3)}")

    // (List<String>) -> Boolean : liste, retourne Boolean
    val isEmpty: (List<String>) -> Boolean = { it.isEmpty() }
    println("Liste vide: ${isEmpty(emptyList())}")
}

// ===== Lambda avec it =====

fun demonstrateIt() {
    println("\n=== it : paramètre implicite ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // ✅ BON : it pour lambda courte
    val doubled = numbers.map { it * 2 }
    println("Doublés: $doubled")

    // ❌ ÉVITER : it dans lambda longue
    val processed = numbers.map {
        val squared = it * it
        val incremented = squared + 1
        incremented * 2
        // Difficile de savoir ce qu'est 'it'
    }

    // [PIÈGE] `it` imbriqués (un `it` dans une lambda dans une autre) : l'IDE avertit du shadowing ; toujours nommer dans ce cas.
    // ✅ BON : nommer le paramètre
    val betterProcessed = numbers.map { n ->
        val squared = n * n
        val incremented = squared + 1
        incremented * 2
    }
    println("Processés: $betterProcessed")
}

// ===== Lambdas avec plusieurs paramètres =====

fun demonstrateMultipleParameters() {
    println("\n=== Plusieurs paramètres ===")

    val pairs = listOf("a" to 1, "b" to 2, "c" to 3)

    // Avec déstructuration
    val formatted = pairs.map { (key, value) -> "$key=$value" }
    println("Formatés: $formatted")

    // Map avec clé et valeur
    val map = mapOf("France" to "Paris", "Italy" to "Rome")
    map.forEach { (country, capital) ->
        println("$country: $capital")
    }

    // Lambda avec deux paramètres nommés
    val combine: (Int, Int) -> String = { a, b -> "($a, $b)" }
    println(combine(10, 20))
}

// ===== Trailing lambda =====

fun demonstrateTrailingLambda() {
    println("\n=== Trailing lambda ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // Standard (entre parenthèses)
    val evens1 = numbers.filter({ it % 2 == 0 })

    // Trailing lambda (hors parenthèses)
    val evens2 = numbers.filter { it % 2 == 0 }

    println("Pairs: $evens2")

    // [ATTENTION FORMATEUR] Les fonctions locales `repeat` et `buildString` ci-dessous MASQUENT celles de la
    //   stdlib (`kotlin.repeat`, `kotlin.text.buildString`) : le préciser, sinon confusion quand on clique dessus dans l'IDE.
    // Plusieurs paramètres + trailing lambda
    fun repeat(times: Int, action: () -> Unit) {
        for (i in 1..times) action()
    }

    repeat(3) {
        println("  Répété")
    }

    // DSL-style
    fun buildString(builder: StringBuilder.() -> Unit): String {
        return StringBuilder().apply(builder).toString()
    }

    val result = buildString {
        append("Hello")
        append(" ")
        append("Kotlin")
    }
    println("Résultat: $result")
}

// ===== Références de fonctions =====

fun square(x: Int) = x * x
fun isEven(x: Int) = x % 2 == 0
fun isPositive(x: Int) = x > 0

fun demonstrateFunctionReferences() {
    println("\n=== Références de fonctions ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // Référence à fonction top-level
    val squares = numbers.map(::square)
    println("Carrés: $squares")

    val evens = numbers.filter(::isEven)
    println("Pairs: $evens")

    // Chaînage avec références
    val processed = numbers
        .filter(::isPositive)
        .map(::square)
    println("Carrés des positifs: $processed")

    // Référence à méthode de String
    val strings = listOf("kotlin", "java", "scala")
    val uppercased = strings.map(String::uppercase)
    println("Majuscules: $uppercased")

    // [ANDROID] Référence de constructeur utile pour les mappers : `dtos.map(::Todo)` si les paramètres correspondent.
    // Référence à constructeur
    data class Person(val name: String)
    val names = listOf("Alice", "Bob", "Charlie")
    val people = names.map(::Person)
    println("Personnes: $people")
}

// ===== Références de méthodes =====

class Formatter {
    fun format(x: Int) = "[$x]"
    fun formatPair(a: Int, b: Int) = "($a, $b)"
}

fun demonstrateMethodReferences() {
    println("\n=== Références de méthodes ===")

    val formatter = Formatter()
    val numbers = listOf(1, 2, 3)

    // [ANDROID] Référence liée (bound reference) : `Button(onClick = viewModel::refresh)` ; le ViewModel est capturé.
    // Référence à méthode d'instance
    val formatted = numbers.map(formatter::format)
    println("Formatés: $formatted")

    // Comparaison avec lambda
    val withLambda = numbers.map { formatter.format(it) }
    println("Avec lambda: $withLambda")
}

// ===== Lambda avec receiver =====

fun demonstrateReceiver() {
    println("\n=== Lambda avec receiver ===")

    // (T) -> R : paramètre classique
    val withParam: (StringBuilder) -> String = { sb ->
        sb.append("Hello")
        sb.toString()
    }

    // [À DIRE] Point clé du fichier : dans un `T.() -> R`, le corps s'écrit COMME une méthode de T (this implicite).
    // [ANDROID] `fun Column(..., content: @Composable ColumnScope.() -> Unit)` : c'est pourquoi `Modifier.weight(1f)`
    //   ou `Modifier.align(Alignment.CenterHorizontally)` ne sont accessibles QUE dans le bloc de Column/Row.
    // T.() -> R : receiver (this implicite)
    val withReceiver: StringBuilder.() -> String = {
        append("Hello")  // this.append implicite
        append(" Kotlin")
        toString()
    }

    val result = StringBuilder().withReceiver()
    println("Résultat: $result")

    // Usage avec apply
    val text = StringBuilder().apply {
        append("Line 1")
        append("\n")
        append("Line 2")
    }.toString()
    println("Texte:\n$text")

    // [ANDROID] Même mécanique pour le DSL Gradle : `android { defaultConfig { minSdk = 24 } }` (J4, build.gradle.kts).
    // buildString : receiver-based DSL
    val html = buildString {
        append("<html>")
        append("<body>")
        append("Content")
        append("</body>")
        append("</html>")
    }
    println("HTML: $html")
}

// ===== Fonctions standard library =====
// [SAUTER] Révision de 01-introduction/06-Collections ; ne montrer que flatMap si pas encore vu.

data class User(val name: String, val age: Int, val active: Boolean)

fun demonstrateStandardLibrary() {
    println("\n=== Standard library ===")

    val users = listOf(
        User("Alice", 28, true),
        User("Bob", 15, true),
        User("Charlie", 35, false),
        User("Diana", 22, true)
    )

    // map : transformation
    val names = users.map { it.name }
    println("Noms: $names")

    // filter : sélection
    val adults = users.filter { it.age >= 18 }
    println("Adultes: ${adults.map { it.name }}")

    // any, all, none
    println("A un actif: ${users.any { it.active }}")
    println("Tous actifs: ${users.all { it.active }}")
    println("Aucun mineur: ${users.none { it.age < 18 }}")

    // first, firstOrNull
    val firstAdult = users.firstOrNull { it.age >= 18 }
    println("Premier adulte: ${firstAdult?.name}")

    // groupBy
    val byAge = users.groupBy { if (it.age >= 18) "adult" else "minor" }
    println("Groupés: ${byAge.mapValues { it.value.map { u -> u.name } }}")

    // associateBy
    val byName = users.associateBy { it.name }
    println("Alice: ${byName["Alice"]}")

    // flatMap
    val allWords = listOf("Hello World", "Kotlin Java")
        .flatMap { it.split(" ") }
    println("Tous les mots: $allWords")

    // fold, reduce
    val ages = users.map { it.age }
    val totalAge = ages.fold(0) { acc, age -> acc + age }
    val avgAge = ages.average()
    println("Total âges: $totalAge, Moyenne: $avgAge")
}

fun main() {
    demonstrateFunctionTypes()
    demonstrateIt()
    demonstrateMultipleParameters()
    demonstrateTrailingLambda()
    demonstrateFunctionReferences()
    demonstrateMethodReferences()
    demonstrateReceiver()
    demonstrateStandardLibrary()

    println("\n=== Résumé ===")
    println("1. Type (A, B) -> R : fonction prenant A et B, retournant R")
    println("2. it : paramètre implicite (lambda courte)")
    println("3. Trailing lambda : { } hors parenthèses si dernier param")
    println("4. ::function : référence de fonction (réutilisable)")
    println("5. T.() -> R : receiver (this implicite)")
    println("6. Standard lib : map, filter, any, groupBy, fold...")
}
