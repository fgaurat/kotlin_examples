// ==============================================================================
// FICHE FORMATEUR — Fonctions d'extension
// Objectif : ajouter des fonctions/propriétés à un type existant (String, Int, List…) sans héritage.
// À présenter (dans l'ordre) :
//   1. Syntaxe `fun Type.nom()` et `this` = le receveur
//   2. Extension avec paramètres par défaut (truncate)
//   3. Extension générique sur List<T>
//   4. Extension property (calculée, pas de stockage)
//   5. Extension sur type nullable (String?.orDefault)
// Lien Android : Jetpack en est rempli : `Modifier.padding(...)`, `viewModelScope` (extension
//   de ViewModel), `lifecycleScope`, `Flow.collectAsStateWithLifecycle()`, `Context.dataStore`
//   (TP12) ; extensions de mapping `fun TodoDto.toDomain() = Todo(...)` (TP04).
// Pièges / questions fréquentes : résolution STATIQUE (pas de polymorphisme, pas d'accès
//   aux membres private) ; un membre de la classe gagne toujours sur une extension de même signature.
// ==============================================================================
/**
 * Exemple 7 : Extension Functions
 *
 * Démonstration de l'enrichissement de types existants
 * sans héritage ni modification du code source
 */

// Extension sur String
fun String.isPalindrome(): Boolean {
    val normalized = this.lowercase().replace(" ", "")
    return normalized == normalized.reversed()
}

// Extension avec paramètre
fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (this.length <= maxLength) this
    else this.take(maxLength) + suffix
}

// Extension sur Int
fun Int.isEven() = this % 2 == 0
fun Int.isOdd() = this % 2 != 0

// [À DIRE] La stdlib fournit déjà `getOrNull(1)` : bon réflexe de vérifier avant d'écrire sa propre extension.
// Extension sur List
fun <T> List<T>.secondOrNull(): T? = if (this.size >= 2) this[1] else null

// Extension avec type générique
fun <T> List<T>.joinToStringFormatted(
    prefix: String = "[",
    suffix: String = "]",
    separator: String = ", "
): String {
    return prefix + this.joinToString(separator) + suffix
}

// [PIÈGE] Une extension property ne peut pas avoir de backing field : pas d'initialiseur, uniquement get().
// [ANDROID] Exemple réel : `val Context.dataStore by preferencesDataStore(name = "settings")` (TP12, délégué top-level).
// Extension property (calculée)
val String.wordCount: Int
    get() = this.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

// [À DIRE] Appelable sur null sans ?. : c'est ainsi que la stdlib définit `isNullOrEmpty()` / `orEmpty()`.
// Extension sur type nullable
fun String?.orDefault(default: String = "N/A"): String {
    return this ?: default
}

fun main() {
    // Extensions sur String
    println("radar".isPalindrome()) // true
    println("hello".isPalindrome()) // false
    println("A man a plan a canal Panama".isPalindrome()) // true

    val longText = "Ceci est un texte très long qui doit être tronqué"
    println(longText.truncate(20))
    println(longText.truncate(20, "…"))

    // Extensions sur Int
    println("5 est pair: ${5.isEven()}")
    println("5 est impair: ${5.isOdd()}")
    println("10 est pair: ${10.isEven()}")

    // Extensions sur List
    val numbers = listOf(1, 2, 3, 4, 5)
    println("Deuxième élément: ${numbers.secondOrNull()}")

    val emptyList = emptyList<Int>()
    println("Deuxième d'une liste vide: ${emptyList.secondOrNull()}")

    // Extension générique
    val fruits = listOf("Pomme", "Banane", "Orange")
    println(fruits.joinToStringFormatted())
    println(fruits.joinToStringFormatted(prefix = "{", suffix = "}", separator = " | "))

    // Extension property
    val text = "Kotlin est un langage moderne"
    println("Nombre de mots: ${text.wordCount}")

    // Extension sur nullable
    val nullableString: String? = null
    val nonNullString: String? = "Kotlin"
    println(nullableString.orDefault())
    println(nonNullString.orDefault())
    println(nullableString.orDefault("Valeur absente"))

    // [ANDROID] Validation d'un TextField de formulaire Compose : `isError = !email.isValidEmail()` ;
    //   sur Android on peut aussi utiliser `android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()`.
    // [PIÈGE] Le Regex est recompilé à chaque appel : en vrai, le sortir dans une val top-level.
    // Exemple pratique : extension pour validation
    fun String.isValidEmail(): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return this.matches(emailRegex)
    }

    println("test@example.com est valide: ${"test@example.com".isValidEmail()}")
    println("invalid-email est valide: ${"invalid-email".isValidEmail()}")

    // Chaînage d'extensions
    val result = "  Kotlin Programming  "
        .trim()
        .lowercase()
        .truncate(10)
    println("Résultat chaîné: $result")
}
