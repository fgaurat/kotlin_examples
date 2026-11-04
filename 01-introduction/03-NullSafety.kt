// ==============================================================================
// FICHE FORMATEUR — Null-safety
// Objectif : comprendre que le compilateur interdit les NPE en distinguant String et String?.
// À présenter (dans l'ordre) :
//   1. Types non-nullables par défaut, String? pour autoriser null
//   2. Safe call ?. (renvoie null au lieu de planter)
//   3. Elvis ?: (valeur par défaut, ou `?: return` / `?: throw`)
//   4. ?.let { } pour exécuter un bloc seulement si non-null
//   5. !! = « je prends le risque » → à bannir sauf cas prouvé
// Lien Android : les API Java/Android renvoient des types plateforme (String!) : on les traite
//   comme nullables ; `savedInstanceState: Bundle?` dans onCreate ; JSON avec champs
//   optionnels → `val description: String? = null` dans les DTO kotlinx.serialization (TP04) ;
//   dernière position GPS possiblement null (TP10).
// Pièges / questions fréquentes : abus de !! pour « faire compiler » ; ?.let enchaîné partout
//   au lieu d'un simple `if (x != null)` (smart cast) ; lateinit vs nullable.
// ==============================================================================
/**
 * Exemple 3 : Null-safety
 *
 * Démonstration des mécanismes de null-safety en Kotlin
 * Objectif : éliminer les NullPointerException
 */


 // Que'est ce que la null-safety et nullable?
 // En Kotlin, les types sont non-nullable par défaut. 
 // Cela signifie que vous ne pouvez pas assigner null à une variable de type String, Int, etc. 
 // Si vous voulez qu'une variable puisse contenir null, vous devez utiliser le type nullable en ajoutant un point d'interrogation (?) après le type (ex: String?).
fun main() {
    // Type non-nullable par défaut
    val nonNull: String = "Hello"
    // val error: String = null // ❌ Erreur de compilation

    // Type nullable avec ?
    val nullable: String? = null
    val maybeValue: String? = "Kotlin"

    // 1. Safe call operator (?.)
    println("Longueur (safe call): ${nullable?.length}") // null
    println("Longueur (safe call): ${maybeValue?.length}") // 6

    // [À DIRE] ?. propage le null : le résultat de `nullable?.length` est un Int? (pas un Int).
    // 2. Elvis operator (?:)
    //L'opérateur Elvis permet de fournir une valeur par défaut si l'expression à gauche est null.
    val length = nullable?.length ?: 0
    println("Longueur avec défaut: $length")

    // [ANDROID] Idiome très courant : `val id = arguments?.getString("id") ?: return` ou
    //   `?: error("id manquant")` pour sortir tôt d'une fonction.
    // 3. let pour exécuter un bloc si non-null
    maybeValue?.let { value ->
        println("Valeur présente: $value")
        println("En majuscules: ${value.uppercase()}")
    }

    nullable?.let {
        println("Ce bloc ne s'exécutera pas")
    }

    // [ATTENTION FORMATEUR] Depuis Kotlin 1.4, !! lève une java.lang.NullPointerException
    //   (KotlinNullPointerException est dépréciée) : corriger oralement le commentaire ci-dessous.
    // [PIÈGE] Le « quick fix » d'Android Studio propose souvent d'ajouter !! : ne pas l'accepter par réflexe.
    // 4. !! (assertion non-null) - À ÉVITER
    // val forced = nullable!! // ❌ KotlinNullPointerException à l'exécution

    // Exemple pratique : traitement d'une chaîne potentiellement nulle
    //pourquoi utiliser !! est dangereux car si la variable est null, cela provoquera une NullPointerException à l'exécution. 
    // Il est préférable d'utiliser des mécanismes de null-safety comme le safe call operator (?.) et l'Elvis operator (?:) pour gérer les valeurs nulles de manière sûre.
    fun processUserInput(input: String?) {
        // Mauvais : utilisation de !!
        // println(input!!.trim())

        // Bon : gestion explicite du null
        val trimmed = input?.trim() ?: "valeur par défaut"
        println("Input traité: $trimmed")
    }

    processUserInput("  Kotlin  ")
    processUserInput(null)

    // [ANDROID] Chaînage typique sur une réponse REST imbriquée : `response.body()?.user?.address?.city`.
    // Chaînage de safe calls
    data class Address(val street: String?)
    data class User(val address: Address?)

    val user: User? = User(Address("Rue de la Paix"))
    val street = user?.address?.street?.uppercase() ?: "Adresse inconnue"
    println("Rue: $street")
}
