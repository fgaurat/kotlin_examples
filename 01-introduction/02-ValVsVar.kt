// ==============================================================================
// FICHE FORMATEUR — val vs var
// Objectif : faire adopter le réflexe « val par défaut, var seulement si nécessaire ».
// À présenter (dans l'ordre) :
//   1. val = référence non réassignable ; var = réassignable
//   2. Inférence de type (pas besoin d'écrire : String / : Int)
//   3. val ≠ immuable : une val peut pointer vers une MutableList modifiable
//   4. Préférer les collections en lecture seule (List) aux MutableList
// Lien Android : l'état Compose (J2) s'écrit `var count by remember { mutableStateOf(0) }` ;
//   dans le ViewModel (J3) : `private val _uiState = MutableStateFlow(UiState())` + `val uiState = _uiState.asStateFlow()`.
// Pièges / questions fréquentes : confondre val et immutabilité profonde ; muter une liste
//   dans un état Compose ne déclenche pas de recomposition (il faut une nouvelle liste).
// ==============================================================================
/**
 * Exemple 2 : val vs var
 *
 * Démonstration de l'immutabilité (val) vs mutabilité (var)
 * Convention : préférer val par défaut
 */

fun main() {
    // val : valeur immutable (équivalent à 'final' en Java)
    val name = "Kotlin"
    // name = "Java" // ❌ Erreur de compilation
    println("Language: $name")

    // [À DIRE] Le type est inféré (Int) mais fixé : `counter = "abc"` ne compile pas. Kotlin est statiquement typé.
    // var : valeur mutable
    var counter = 0
    println("Counter initial: $counter")
    counter = 1
    counter += 10
    println("Counter après modifications: $counter")

    // Bon usage : val pour des valeurs qui ne changent pas
    val maxRetries = 3
    val apiUrl = "https://api.example.com"

    // var pour des états qui évoluent
    var currentAttempt = 0
    var isConnected = false

    // [À DIRE] Avertissement IDE « variable never used / could be val » : le montrer en live (maxRetries, isConnected...).
    // [PIÈGE] val ne rend pas l'objet immuable : seule la RÉFÉRENCE est figée.
    // [ANDROID] En Compose, `items.add(...)` sur une liste d'état ne recompose pas : on fait
    //   `_uiState.update { it.copy(items = it.items + nouvel) }` (nouvelle liste, TP07).
    // Démonstration : val n'empêche pas la modification du contenu
    val items = mutableListOf("A", "B")
    items.add("C") // ✅ OK : la liste est mutable
    // items = mutableListOf("X") // ❌ Erreur : on ne peut pas réassigner

    println("Items: $items")
}
