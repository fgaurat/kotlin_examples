// ==============================================================================
// FICHE FORMATEUR — Lambdas et fonctions anonymes
// Objectif : lire et écrire des lambdas, comprendre les types fonction et la trailing lambda.
// À présenter (dans l'ordre) :
//   1. Syntaxe { params -> corps }, `it` implicite, dernière expression = valeur retournée
//   2. Types fonction : (Int, Int) -> Int, () -> Unit
//   3. Fonction qui reçoit une fonction (ordre supérieur)
//   4. Trailing lambda (la syntaxe de TOUT Compose)
//   5. Références ::fonction ; return local avec label
// Lien Android : Compose = trailing lambdas partout : `Column { ... }`, `Button(onClick = { vm.onAdd() }) { Text("Ajouter") }` ;
//   callbacks remontés d'un Composable (`onTaskClick: (Task) -> Unit`, state hoisting J2/TP05) ;
//   `viewModelScope.launch { ... }` est aussi une trailing lambda ; références `onClick = viewModel::refresh`.
// Pièges / questions fréquentes : `return` nu dans une lambda = sort de la fonction englobante
//   (si inline) ; `() -> Unit` ≠ `Unit` ; oublier que la dernière ligne est la valeur de retour.
// Si peu de temps : sections Lambdas, Types de fonctions, Trailing lambda ; survoler fonctions anonymes et return.
// ==============================================================================
/**
 * Exemple 5 : Lambdas et fonctions anonymes
 *
 * Différences entre lambda et fonction anonyme
 * Types de fonctions, syntaxe
 */

// ===== Lambda : syntaxe de base =====

fun demonstrateLambdas() {
    println("=== Lambdas ===")

    // Lambda avec un paramètre (it implicite)
    val numbers = listOf(1, 2, 3, 4, 5)
    val doubled = numbers.map { it * 2 }
    println("Doublés: $doubled")

    // Lambda avec paramètre nommé
    val tripled = numbers.map { n -> n * 3 }
    println("Triplés: $tripled")

    // [PIÈGE] `{ (num, letter) -> }` = UN paramètre Pair déstructuré, pas deux paramètres `{ num, letter -> }`.
    // Lambda avec plusieurs paramètres
    val pairs = listOf(1 to "a", 2 to "b", 3 to "c")
    val formatted = pairs.map { (num, letter) -> "$num:$letter" }
    println("Formatés: $formatted")

    // Lambda sur plusieurs lignes
    val processed = numbers.map { value ->
        val square = value * value
        val result = square + value
        result  // Dernière expression = valeur retournée
    }
    println("Processés: $processed")
}

// ===== Fonction anonyme : syntaxe =====
// [SAUTER] Les fonctions anonymes sont rares dans le code Android : les mentionner pour savoir les lire.

fun demonstrateAnonymousFunctions() {
    println("\n=== Fonctions anonymes ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // Fonction anonyme (type explicite)
    val doubled = numbers.map(fun(x: Int): Int {
        return x * 2
    })
    println("Doublés: $doubled")

    // Fonction anonyme avec expression body
    val tripled = numbers.map(fun(x: Int) = x * 3)
    println("Triplés: $tripled")

    // Fonction anonyme sur plusieurs lignes
    val processed = numbers.map(fun(value: Int): Int {
        val square = value * value
        return square + value
    })
    println("Processés: $processed")
}

// ===== Types de fonctions =====

fun demonstrateFunctionTypes() {
    println("\n=== Types de fonctions ===")

    // [ANDROID] Ces types apparaissent dans les signatures de Composables :
    //   `fun TaskItem(task: Task, onToggle: (Task) -> Unit, onDelete: () -> Unit)` (state hoisting, TP05/TP07).
    // Type : (Int, Int) -> Int
    val add: (Int, Int) -> Int = { a, b -> a + b }
    println("add(3, 4) = ${add(3, 4)}")

    // Type : (String) -> Boolean
    val isLong: (String) -> Boolean = { it.length > 5 }
    println("isLong(\"Kotlin\") = ${isLong("Kotlin")}")

    // Type : () -> Unit
    val greet: () -> Unit = { println("Hello!") }
    greet()

    // Fonction qui prend une fonction en paramètre
    fun applyOperation(a: Int, b: Int, op: (Int, Int) -> Int): Int {
        return op(a, b)
    }

    println("applyOperation(10, 5, add) = ${applyOperation(10, 5, add)}")
    println("applyOperation(10, 5, { x, y -> x - y }) = ${applyOperation(10, 5, { x, y -> x - y })}")
}

// ===== Trailing lambda : syntaxe spéciale =====

fun demonstrateTrailingLambda() {
    println("\n=== Trailing lambda ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // [À DIRE] L'IDE signale `filter({ ... })` : « Lambda argument should be moved out of parentheses ».
    // Lambda entre parenthèses (standard)
    val evens1 = numbers.filter({ it % 2 == 0 })

    // Trailing lambda : sortir la lambda des parenthèses
    val evens2 = numbers.filter { it % 2 == 0 }

    println("Pairs: $evens2")

    // Si la lambda est le seul argument, les parenthèses sont optionnelles
    numbers.forEach { println(it) }

    // Avec plusieurs paramètres
    fun customOperation(a: Int, b: Int, operation: (Int, Int) -> Int): Int {
        return operation(a, b)
    }

    // [ANDROID] Exactement la forme de `Button(onClick = { ... }) { Text("OK") }` : le DERNIER paramètre
    //   lambda (content) sort des parenthèses ; `Scaffold(topBar = { ... }) { padding -> ... }` idem.
    val result = customOperation(10, 5) { x, y -> x * y }
    println("Résultat: $result")
}

// ===== Références de fonctions =====

fun square(x: Int) = x * x

fun isEven(x: Int) = x % 2 == 0

fun demonstrateFunctionReferences() {
    println("\n=== Références de fonctions ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // [ANDROID] `onClick = viewModel::refresh`, `items.map(TodoDto::toDomain)` : références très courantes.
    // Référence à une fonction top-level
    val squares = numbers.map(::square)
    println("Carrés: $squares")

    val evens = numbers.filter(::isEven)
    println("Pairs: $evens")

    // Référence à une fonction membre
    val strings = listOf("kotlin", "java", "scala")
    val uppercased = strings.map(String::uppercase)
    println("Majuscules: $uppercased")
}

// ===== Lambda vs Fonction anonyme : return =====

fun demonstrateReturnBehavior() {
    println("\n=== Comportement de return ===")

    // [ATTENTION FORMATEUR] Le commentaire ci-dessous annonce un return non-local mais l'exemple utilise
    //   `return@forEach` (return LOCAL avec label) : aucun return non-local n'est montré. Écrire `return`
    //   au tableau pour montrer qu'il sortirait de withLambda() (possible car forEach est inline).
    // [PIÈGE] return@forEach ne « break » pas la boucle : il passe à l'élément suivant (comme continue).
    // Lambda : return peut être non-local (voir exemple suivant)
    fun withLambda() {
        val numbers = listOf(1, 2, 3)
        numbers.forEach {
            if (it == 2) return@forEach  // Return local (de la lambda)
            println("Lambda: $it")
        }
        println("Fin de withLambda")
    }

    // Fonction anonyme : return est toujours local
    fun withAnonymous() {
        val numbers = listOf(1, 2, 3)
        numbers.forEach(fun(it: Int) {
            if (it == 2) return  // Return de la fonction anonyme
            println("Anonyme: $it")
        })
        println("Fin de withAnonymous")
    }

    withLambda()
    println()
    withAnonymous()
}

// ===== Quand utiliser quoi ? =====

fun demonstrateUseCases() {
    println("\n=== Quand utiliser quoi ? ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // ✅ Lambda : transformations simples
    val doubled = numbers.map { it * 2 }
    println("Lambda simple: $doubled")

    // [À DIRE] En pratique on écrirait `numbers.map { x -> if (x % 2 == 0) x else 0 }` : la fonction anonyme
    //   n'est utile que pour des return multiples locaux.
    // ✅ Fonction anonyme : besoin d'annoter le type de retour
    val processed = numbers.map(fun(x: Int): Int {
        return if (x % 2 == 0) x else 0
    })
    println("Fonction anonyme avec type: $processed")

    // ✅ Fonction nommée : logique réutilisée ou complexe
    fun complexTransform(x: Int): Int {
        val step1 = x * 2
        val step2 = step1 + 10
        val step3 = if (step2 > 20) step2 else 0
        return step3
    }

    val transformed = numbers.map(::complexTransform)
    println("Fonction nommée: $transformed")
}

fun main() {
    demonstrateLambdas()
    demonstrateAnonymousFunctions()
    demonstrateFunctionTypes()
    demonstrateTrailingLambda()
    demonstrateFunctionReferences()
    demonstrateReturnBehavior()
    demonstrateUseCases()

    println("\n=== Résumé ===")
    println("Lambda: { x -> x * 2 } - concise, it implicite")
    println("Fonction anonyme: fun(x: Int) = x * 2 - type explicite")
    println("Lambda: pour transformations simples")
    println("Fonction anonyme: pour return local explicite")
    println("Fonction nommée: pour logique complexe ou réutilisée")
}
