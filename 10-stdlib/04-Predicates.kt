// ==============================================================================
// FICHE FORMATEUR — Prédicats (any, all, none, find, first/single et variantes OrNull)
// Objectif : interroger une collection sans boucle, et choisir entre version « OrNull » et version qui lève.
// À présenter (dans l'ordre) :
//   1. any / all / none → Boolean, avec arrêt dès que le résultat est connu (short-circuit)
//   2. find == firstOrNull { } → T? à combiner avec ?. / ?: / let
//   3. first / single lèvent une exception ; *OrNull renvoient null → règle : *OrNull par défaut
//   4. Cas limite : liste vide (all = true, any = false)
// Lien Android : `enabled = fields.all { it.isNotBlank() }` sur un Button Compose (J2) ; retrouver l'élément sélectionné
//   `tasks.find { it.id == taskId }` dans un ViewModel après navigation (TP09) ; `tasks.any { !it.done }` pour un badge.
// Pièges / questions fréquentes : `emptyList().all { ... }` vaut true ; first() sur liste vide = crash de l'app ;
//   single() lève aussi si PLUSIEURS éléments (IllegalArgumentException).
// Si peu de temps : any/all/none, find/firstOrNull, first vs firstOrNull, short-circuit ; [SAUTER] lastOrNull, contains.
// ==============================================================================
/**
 * Exemple 4 : Prédicats
 *
 * any : au moins un élément satisfait
 * all : tous les éléments satisfont
 * none : aucun élément ne satisfait
 * find : premier élément qui satisfait
 * firstOrNull : premier élément ou null
 * singleOrNull : un seul élément ou null
 */

// ===== any : au moins un élément satisfait =====

fun demonstrateAny() {
    println("=== any : au moins un élément satisfait ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    val hasEven = numbers.any { it % 2 == 0 }
    println("numbers: $numbers")
    println("hasEven: $hasEven")

    val hasNegative = numbers.any { it < 0 }
    println("hasNegative: $hasNegative")

    // Sans prédicat : vérifie si non vide
    val empty = emptyList<Int>()
    println("\nempty.any(): ${empty.any()}")
    println("numbers.any(): ${numbers.any()}")

    data class User(val name: String, val active: Boolean)
    val users = listOf(
        User("Alice", true),
        User("Bob", false),
        User("Charlie", false)
    )
    // [ANDROID] Ex. : afficher un bouton « Tout supprimer » seulement si `tasks.any { it.done }` (TP07) ;
    // `isNotEmpty()` reste plus lisible que `any()` sans prédicat.
    val hasActive = users.any { it.active }
    println("\nusers: $users")
    println("hasActive: $hasActive")

    println("\n✅ any : true si au moins un élément satisfait")
}

// ===== all : tous les éléments satisfont =====

fun demonstrateAll() {
    println("\n=== all : tous les éléments satisfont ===")

    val numbers = listOf(2, 4, 6, 8, 10)

    val allEven = numbers.all { it % 2 == 0 }
    println("numbers: $numbers")
    println("allEven: $allEven")

    val allPositive = numbers.all { it > 0 }
    println("allPositive: $allPositive")

    val allNegative = numbers.all { it < 0 }
    println("allNegative: $allNegative")

    // [PIÈGE] Cas qui surprend toujours : sur une liste vide, all { } vaut true. Dans un formulaire, valider
    // `fields.all { it.isValid }` sans vérifier que la liste n'est pas vide peut laisser passer un formulaire vide.
    // Empty list : all retourne true (vacuous truth)
    val empty = emptyList<Int>()
    println("\nempty.all { it > 0 }: ${empty.all { it > 0 }}")

    data class User(val name: String, val age: Int)
    val users = listOf(
        User("Alice", 30),
        User("Bob", 25),
        User("Charlie", 35)
    )
    val allAdults = users.all { it.age >= 18 }
    println("\nusers: $users")
    println("allAdults: $allAdults")

    println("\n✅ all : true si tous les éléments satisfont")
}

// ===== none : aucun élément ne satisfait =====

fun demonstrateNone() {
    println("\n=== none : aucun élément ne satisfait ===")

    val numbers = listOf(1, 3, 5, 7, 9)

    // [À DIRE] none { p } == !any { p } : choisir la forme qui se lit le plus naturellement.
    val noEven = numbers.none { it % 2 == 0 }
    println("numbers: $numbers")
    println("noEven: $noEven")

    val noNegative = numbers.none { it < 0 }
    println("noNegative: $noNegative")

    // Sans prédicat : vérifie si vide
    val empty = emptyList<Int>()
    println("\nempty.none(): ${empty.none()}")
    println("numbers.none(): ${numbers.none()}")

    // none { } == !any { }
    println("\nnone { it % 2 == 0 } == !any { it % 2 == 0 }")
    println("none: $noEven")
    println("!any: ${!numbers.any { it % 2 == 0 }}")

    println("\n✅ none : true si aucun élément ne satisfait")
}

// ===== find : premier élément qui satisfait =====

fun demonstrateFind() {
    println("\n=== find : premier élément qui satisfait ===")

    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    val firstEven = numbers.find { it % 2 == 0 }
    println("numbers: $numbers")
    println("firstEven: $firstEven")

    val firstNegative = numbers.find { it < 0 }
    println("firstNegative: $firstNegative")

    data class User(val name: String, val email: String)
    val users = listOf(
        User("Alice", "alice@example.com"),
        User("Bob", "bob@example.com"),
        User("Charlie", "charlie@example.com")
    )
    // [ANDROID] Écran détail (TP09) : `val task = tasks.find { it.id == route.taskId }` puis
    // `task?.let { TaskDetail(it) } ?: Text("Tâche introuvable")` dans le composable.
    val userBob = users.find { it.name == "Bob" }
    println("\nusers: $users")
    println("userBob: $userBob")

    val userDavid = users.find { it.name == "David" }
    println("userDavid: $userDavid")

    println("\n✅ find : premier élément ou null (alias de firstOrNull)")
}

// ===== firstOrNull : premier élément ou null =====

fun demonstrateFirstOrNull() {
    println("\n=== firstOrNull : premier élément ou null ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    // [À DIRE] Réflexe à donner : dès que la liste vient du réseau ou d'une base, utiliser la variante OrNull
    // et traiter le null explicitement (elvis `?:`).
    // Sans prédicat : premier élément
    val first = numbers.firstOrNull()
    println("numbers: $numbers")
    println("first: $first")

    val empty = emptyList<Int>()
    val emptyFirst = empty.firstOrNull()
    println("\nempty: $empty")
    println("emptyFirst: $emptyFirst")

    // Avec prédicat
    val firstEven = numbers.firstOrNull { it % 2 == 0 }
    println("\nfirstEven: $firstEven")

    val firstNegative = numbers.firstOrNull { it < 0 }
    println("firstNegative: $firstNegative")

    println("\n✅ firstOrNull : premier élément ou null (safe)")
}

// ===== first : premier élément (exception si vide) =====

fun demonstrateFirst() {
    println("\n=== first : premier élément (exception si vide) ===")

    val numbers = listOf(1, 2, 3, 4, 5)
    val first = numbers.first()
    println("numbers: $numbers")
    println("first: $first")

    val firstEven = numbers.first { it % 2 == 0 }
    println("firstEven: $firstEven")

    // [PIÈGE] Sur Android, une NoSuchElementException non attrapée = l'application se ferme.
    // first() n'est acceptable que si l'absence est un bug (invariant), jamais pour des données externes.
    // ⚠️ Exception si vide ou aucun match
    val empty = emptyList<Int>()
    try {
        empty.first()
    } catch (e: NoSuchElementException) {
        println("\nempty.first(): ${e.message}")
    }

    try {
        numbers.first { it > 100 }
    } catch (e: NoSuchElementException) {
        println("first { it > 100 }: ${e.message}")
    }

    println("\n✅ first : comme firstOrNull mais avec exception")
}

// ===== lastOrNull : dernier élément ou null =====
// [SAUTER] Symétrique de firstOrNull.

fun demonstrateLastOrNull() {
    println("\n=== lastOrNull : dernier élément ou null ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    val last = numbers.lastOrNull()
    println("numbers: $numbers")
    println("last: $last")

    val lastEven = numbers.lastOrNull { it % 2 == 0 }
    println("lastEven: $lastEven")

    val empty = emptyList<Int>()
    println("\nempty.lastOrNull(): ${empty.lastOrNull()}")

    println("\n✅ lastOrNull : dernier élément ou null")
}

// ===== singleOrNull : un seul élément ou null =====

fun demonstrateSingleOrNull() {
    println("\n=== singleOrNull : un seul élément ou null ===")

    // [À DIRE] singleOrNull exprime « exactement un » : null si 0 OU plusieurs. Ne pas l'utiliser comme firstOrNull.
    // Un seul élément : OK
    val single = listOf(42)
    println("single: $single")
    println("singleOrNull: ${single.singleOrNull()}")

    // Vide : null
    val empty = emptyList<Int>()
    println("\nempty: $empty")
    println("singleOrNull: ${empty.singleOrNull()}")

    // Multiple : null
    val multiple = listOf(1, 2, 3)
    println("\nmultiple: $multiple")
    println("singleOrNull: ${multiple.singleOrNull()}")

    // Avec prédicat
    val numbers = listOf(1, 2, 3, 4, 5)
    val singleEven = numbers.singleOrNull { it == 2 }
    println("\nnumbers: $numbers")
    println("singleOrNull { it == 2 }: $singleEven")

    val multipleEven = numbers.singleOrNull { it % 2 == 0 }
    println("singleOrNull { it % 2 == 0 }: $multipleEven")  // null (plusieurs pairs)

    println("\n✅ singleOrNull : null si 0 ou plusieurs éléments")
}

// ===== single : un seul élément (exception sinon) =====

fun demonstrateSingle() {
    println("\n=== single : un seul élément (exception sinon) ===")

    val single = listOf(42)
    println("single: $single")
    println("single(): ${single.single()}")

    // ⚠️ Exception si vide
    val empty = emptyList<Int>()
    try {
        empty.single()
    } catch (e: NoSuchElementException) {
        println("\nempty.single(): ${e.message}")
    }

    // [PIÈGE] Deux types d'exception différents : NoSuchElementException (vide) vs IllegalArgumentException (plusieurs).
    // ⚠️ Exception si multiple
    val multiple = listOf(1, 2, 3)
    try {
        multiple.single()
    } catch (e: IllegalArgumentException) {
        println("multiple.single(): ${e.message}")
    }

    println("\n✅ single : comme singleOrNull mais avec exception")
}

// ===== contains et containsAll =====

fun demonstrateContains() {
    println("\n=== contains et containsAll ===")

    val numbers = listOf(1, 2, 3, 4, 5)

    println("numbers: $numbers")
    println("contains(3): ${numbers.contains(3)}")
    println("contains(10): ${numbers.contains(10)}")

    // [À DIRE] `x in list` est traduit en `list.contains(x)` (convention d'opérateur) ; même `in` que dans `for` et `when`.
    // [ANDROID] Ex. TP10 : `if (Manifest.permission.ACCESS_FINE_LOCATION in grantedPermissions) { ... }`.
    // Opérateur in
    println("\n3 in numbers: ${3 in numbers}")
    println("10 in numbers: ${10 in numbers}")

    // containsAll
    println("\ncontainsAll([2, 4]): ${numbers.containsAll(listOf(2, 4))}")
    println("containsAll([2, 10]): ${numbers.containsAll(listOf(2, 10))}")

    println("\n✅ contains, containsAll, opérateur in")
}

// ===== Exemple pratique : validation =====

fun demonstrateValidation() {
    println("\n=== Exemple pratique : validation ===")

    data class User(val name: String, val age: Int, val email: String)
    val users = listOf(
        User("Alice", 30, "alice@example.com"),
        User("Bob", 17, "bob@example.com"),
        User("Charlie", 35, "charlie@invalid")
    )

    // Tous adultes ?
    val allAdults = users.all { it.age >= 18 }
    println("users: $users")
    println("allAdults: $allAdults")

    // [ANDROID] Même logique pour valider un formulaire Compose : calculer `isFormValid` dans le ViewModel
    // et le passer en `enabled = uiState.isFormValid` au Button. (Validation d'email simpliste ici : `contains("@")`.)
    // Au moins un email invalide ?
    val hasInvalidEmail = users.any { !it.email.contains("@") }
    println("hasInvalidEmail: $hasInvalidEmail")

    // Aucun email vide ?
    val noEmptyEmail = users.none { it.email.isEmpty() }
    println("noEmptyEmail: $noEmptyEmail")

    // Trouver le premier mineur
    val firstMinor = users.firstOrNull { it.age < 18 }
    println("firstMinor: $firstMinor")

    // Trouver l'email invalide
    val invalidEmail = users.find { !it.email.contains("@") }
    println("invalidEmail: $invalidEmail")

    println("\n✅ Combinaison de prédicats pour validation")
}

// ===== Performance : short-circuit =====

fun demonstrateShortCircuit() {
    println("\n=== Performance : short-circuit ===")

    var counter = 0
    val numbers = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)

    // [À DIRE] Lancer le main et montrer la sortie : 2 vérifications pour any, 5 pour all au lieu de 10.
    // [PIÈGE] Mettre des effets de bord (counter++, println) dans un prédicat est à éviter hors démo.
    // any s'arrête dès le premier match
    val hasEven = numbers.any {
        counter++
        println("Checking $it (counter=$counter)")
        it % 2 == 0
    }
    println("hasEven: $hasEven")
    println("Checks: $counter")  // S'arrête à 2

    counter = 0
    println("\n--- all s'arrête dès le premier non-match ---")
    val allSmall = numbers.all {
        counter++
        println("Checking $it (counter=$counter)")
        it < 5
    }
    println("allSmall: $allSmall")
    println("Checks: $counter")  // S'arrête à 5

    println("\n✅ any, all, find utilisent short-circuit evaluation")
}

// ===== Bonnes pratiques =====

fun demonstrateBestPractices() {
    println("\n=== Bonnes pratiques ===")

    println("✅ any : au moins un (short-circuit)")
    println("✅ all : tous (short-circuit)")
    println("✅ none : aucun (short-circuit)")
    println("✅ find/firstOrNull : premier match ou null")
    println("✅ first : premier match ou exception")
    println("✅ singleOrNull : exactement un ou null")
    println("✅ single : exactement un ou exception")
    println("✅ Préférer *OrNull pour éviter exceptions")

    println("\n💡 Principe :")
    println("  *OrNull : safe (null si pas trouvé)")
    println("  * : unsafe (exception si pas trouvé)")
    println("  any/all/none : validation rapide")
    println("  find : recherche")
}

fun main() {
    demonstrateAny()
    demonstrateAll()
    demonstrateNone()
    demonstrateFind()
    demonstrateFirstOrNull()
    demonstrateFirst()
    demonstrateLastOrNull()
    demonstrateSingleOrNull()
    demonstrateSingle()
    demonstrateContains()
    demonstrateValidation()
    demonstrateShortCircuit()
    demonstrateBestPractices()

    println("\n=== Résumé ===")
    println("1. any : true si au moins un satisfait")
    println("2. all : true si tous satisfont")
    println("3. none : true si aucun ne satisfait")
    println("4. find : premier match ou null")
    println("5. firstOrNull : premier ou null (safe)")
    println("6. first : premier ou exception")
    println("7. lastOrNull : dernier ou null")
    println("8. singleOrNull : exactement un ou null")
    println("9. single : exactement un ou exception")
    println("10. Short-circuit : optimisation automatique")
}
