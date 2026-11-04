// ==============================================================================
// FICHE FORMATEUR — Style « expression » (if, when, try)
// Objectif : montrer que if / when / try renvoient une valeur, ce qui évite les var temporaires.
// À présenter (dans l'ordre) :
//   1. if expression (pas d'opérateur ternaire ? : en Kotlin)
//   2. when sans argument (conditions) et when(valeur) (remplace switch, pas de break)
//   3. try/catch comme expression
//   4. Fonction à expression unique `fun f() = ...`
// Lien Android : `when (uiState) { is UiState.Loading -> CircularProgressIndicator() ... }`
//   dans un @Composable (J2/J3, TP07) ; `Text(if (done) "Terminé" else "À faire")` ;
//   fonctions à expression unique très utilisées pour les mappers DTO → modèle (TP04).
// Pièges / questions fréquentes : « où est le ternaire ? » ; when exhaustif obligatoire
//   quand il est utilisé comme expression (else requis sauf sealed/enum).
// ==============================================================================
/**
 * Exemple 4 : Style "expression"
 *
 * Kotlin encourage l'écriture orientée expressions
 * (presque tout retourne une valeur)
 */

fun main() {
    // if est une expression (retourne une valeur)
    val age = 25
    val label = if (age >= 18) "adult" else "minor"
    println("Catégorie: $label")

    // [À DIRE] Les branches sont testées dans l'ordre, la première vraie gagne : l'ordre des seuils compte.
    // when est une expression (équivalent puissant de switch)
    val grade = 85
    val mention = when {
        grade >= 90 -> "Excellent"
        grade >= 80 -> "Bien"
        grade >= 70 -> "Assez bien"
        grade >= 60 -> "Passable"
        else -> "Insuffisant"
    }
    println("Mention: $mention")

    // when avec valeur
    val dayOfWeek = 3
    val dayName = when (dayOfWeek) {
        1 -> "Lundi"
        2 -> "Mardi"
        3 -> "Mercredi"
        4 -> "Jeudi"
        5 -> "Vendredi"
        6, 7 -> "Week-end"
        else -> "Jour invalide"
    }
    println("Jour: $dayName")

    // [À DIRE] Kotlin n'a pas d'exceptions vérifiées (pas de `throws`) : rien n'oblige à catcher.
    // [ANDROID] Ici on aurait aussi `"123".toIntOrNull() ?: 0` (plus idiomatique pour un TextField).
    // try-catch est aussi une expression
    val number = try {
        "123".toInt()
    } catch (e: NumberFormatException) {
        0
    }
    println("Nombre parsé: $number")

    // Fonction à expression unique
    fun max(a: Int, b: Int) = if (a > b) a else b

    // [À DIRE] `is` fait un test de type ET un smart cast dans la branche ; `obj: Any` = Object en Java.
    // Fonction avec when
    fun describe(obj: Any) = when (obj) {
        1 -> "Un"
        "Hello" -> "Salutation"
        is Long -> "Long number"
        !is String -> "Pas une string"
        else -> "Inconnu"
    }

    println("max(10, 20) = ${max(10, 20)}")
    println("describe(1) = ${describe(1)}")
    println("describe(\"Hello\") = ${describe("Hello")}")
    println("describe(100L) = ${describe(100L)}")
}
