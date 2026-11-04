// ==============================================================================
// FICHE FORMATEUR — Hello World et point d'entrée
// Objectif : montrer qu'un programme Kotlin démarre sur une simple fonction top-level main().
// À présenter (dans l'ordre) :
//   1. fun main() sans classe englobante (≠ Java : pas de public static void main)
//   2. println et les templates de chaîne "${...}" / "$x"
//   3. Exécution : bouton ▶ dans la marge d'Android Studio / IntelliJ (fichier .kt avec main)
// Lien Android : une appli Android n'a PAS de main() : le point d'entrée est l'Activity
//   (MainActivity.onCreate { setContent { ... } }) déclarée dans AndroidManifest.xml.
//   main() ne sert ici qu'à tester du Kotlin pur (et dans les tests JUnit, J3).
// Pièges / questions fréquentes : « où est la classe ? » → fonctions top-level compilées
//   dans une classe HelloWorldKt ; pas de point-virgule obligatoire.
// ==============================================================================
/**
 * Exemple 1 : Hello World et point d'entrée
 *
 * Démontration du point d'entrée minimal d'une application Kotlin
 * Note : pas besoin de classe, les fonctions peuvent être top-level
 */

fun main() {
    println("Hello Kotlin")
}

// [ATTENTION FORMATEUR] mainWithArgs n'est PAS un point d'entrée : seul `fun main(args: Array<String>)`
// l'est. Le préciser si un apprenant cherche à la lancer.
// [SAUTER] Arguments de ligne de commande : inexistants côté Android (on reçoit plutôt des extras d'Intent).
// Version avec arguments de ligne de commande
fun mainWithArgs(args: Array<String>) {
    if (args.isNotEmpty()) {
        println("Hello, ${args[0]}")
    } else {
        println("Hello, World")
    }
}
