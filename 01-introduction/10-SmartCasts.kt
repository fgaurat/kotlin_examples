// ==============================================================================
// FICHE FORMATEUR — Smart casts
// Objectif : montrer qu'après un `is` ou un `!= null`, le compilateur type automatiquement la variable.
// À présenter (dans l'ordre) :
//   1. is dans un when → plus de cast explicite (≠ Java instanceof + cast)
//   2. Smart cast après null check
//   3. filterIsInstance<T>() sur une collection
//   4. Limites : propriétés var de classe → copie locale (ou ?.let)
// Lien Android : `when (val state = uiState) { is UiState.Success -> TaskList(state.tasks) }` (TP07) ;
//   propriété `var` nullable d'une Activity/Fragment (J5) → `val b = _binding ?: return` ;
//   `as?` pour un cast sûr (`context as? Activity`).
// Pièges / questions fréquentes : « Smart cast to 'String' is impossible, because 'x' is a mutable
//   property » → copie locale ; `as` lève ClassCastException, `as?` renvoie null.
// Si peu de temps : sections 1, 2 et « Limites » ; les autres sont des variantes.
// ==============================================================================
/**
 * Exemple 10 : Smart Casts
 *
 * Le compilateur Kotlin effectue automatiquement des casts
 * après vérification de type
 */

// Interface et implémentations pour la démo
interface Shape {
    fun area(): Double
}

data class Circle(val radius: Double) : Shape {
    override fun area() = Math.PI * radius * radius
}

data class Rectangle(val width: Double, val height: Double) : Shape {
    override fun area() = width * height
}

data class Triangle(val base: Double, val height: Double) : Shape {
    override fun area() = 0.5 * base * height
}

fun main() {
    // ===== Smart cast après vérification de type =====

    fun describe(obj: Any): String {
        // Après 'is', le compilateur sait que obj est du bon type
        return when (obj) {
            is String -> "String de longueur ${obj.length}" // obj castée en String
            is Int -> "Entier valant ${obj + 1}"            // obj castée en Int
            is List<*> -> "Liste de ${obj.size} éléments"  // obj castée en List
            is Circle -> "Cercle de rayon ${obj.radius}"   // obj castée en Circle
            else -> "Type inconnu"
        }
    }

    println("=== Smart Cast avec Any ===")
    println(describe("Kotlin"))
    println(describe(42))
    println(describe(listOf(1, 2, 3)))
    println(describe(Circle(5.0)))

    // ===== Smart cast avec nullable =====

    fun processString(text: String?) {
        if (text != null) {
            // Après le check, text est smartcast en String (non-null)
            println("Longueur: ${text.length}")
            println("Majuscules: ${text.uppercase()}")
        } else {
            println("Texte null")
        }
    }

    println("\n=== Smart Cast nullable ===")
    processString("Hello")
    processString(null)

    // ===== Smart cast avec when =====
    // [À DIRE] Shape est une interface « ouverte » : le `else` est obligatoire. Avec `sealed interface Shape`,
    //   il disparaîtrait (renvoi au fichier 09).

    fun calculateArea(shape: Shape): String {
        return when (shape) {
            is Circle -> {
                // shape est automatiquement castée en Circle
                "Cercle: rayon=${shape.radius}, aire=${shape.area()}"
            }
            is Rectangle -> {
                "Rectangle: ${shape.width}x${shape.height}, aire=${shape.area()}"
            }
            is Triangle -> {
                "Triangle: base=${shape.base}, hauteur=${shape.height}, aire=${shape.area()}"
            }
            else -> "Shape inconnue"
        }
    }

    println("\n=== Smart Cast avec when ===")
    val shapes: List<Shape> = listOf(
        Circle(3.0),
        Rectangle(4.0, 5.0),
        Triangle(6.0, 4.0)
    )

    shapes.forEach { shape ->
        println(calculateArea(shape))
    }

    // ===== Smart cast dans les expressions =====

    fun getLength(obj: Any): Int {
        // Smart cast dans une expression if
        return if (obj is String) obj.length else 0
    }

    println("\n=== Smart Cast dans expression ===")
    println("Longueur de 'Kotlin': ${getLength("Kotlin")}")
    println("Longueur de 123: ${getLength(123)}")

    // ===== Smart cast avec && (and) =====

    fun processShape(shape: Shape?) {
        // [ATTENTION FORMATEUR] `shape != null &&` est redondant : `shape is Circle` est déjà faux pour null
        //   (l'IDE le signale). Le présenter comme tel, ou s'en servir pour le faire remarquer aux apprenants.
        // Cast combiné : null check + type check
        if (shape != null && shape is Circle) {
            println("Cercle non-null de rayon ${shape.radius}")
        }
    }

    println("\n=== Smart Cast avec && ===")
    processShape(Circle(7.0))
    processShape(null)

    // ===== Smart cast dans les lambda =====

    val items: List<Any> = listOf("Hello", 42, Circle(2.0), "World", 100)

    // [À DIRE] Survoler `strings` et `stringsTyped` dans l'IDE pour montrer les types inférés différents.
    val strings = items.filter { it is String } // List<Any>
    val stringsTyped = items.filterIsInstance<String>() // List<String> !

    println("\n=== FilterIsInstance (smart cast collection) ===")
    stringsTyped.forEach { str ->
        // str est de type String, pas Any
        println("String: ${str.uppercase()}")
    }

    val circles = items.filterIsInstance<Circle>()
    circles.forEach { circle ->
        println("Circle: rayon=${circle.radius}")
    }

    // ===== Limites du smart cast =====

    // Smart cast ne fonctionne pas si la valeur peut changer
    var mutableValue: Any = "Test"

    if (mutableValue is String) {
        // Smart cast fonctionne ici (scope local)
        println("Longueur: ${mutableValue.length}")
    }

    // [À DIRE] La vraie raison : une propriété var de classe peut être modifiée entre le test et l'usage
    //   (autre thread, getter personnalisé, autre module) ; le compilateur ne peut pas le garantir.
    // [ANDROID] Cas typique J5 : `private var _binding: FragmentXBinding? = null` → `_binding?.let { ... }`
    //   ou copie locale ; ce message d'erreur, les apprenants le verront.
    // Mais attention avec les propriétés mutables de classe
    class Container {
        var value: Any = "Initial"

        fun test() {
            if (value is String) {
                // ⚠️ Smart cast impossible (value peut changer entre threads)
                // println(value.length) // Erreur

                // Solution : copie locale
                val localValue = value
                if (localValue is String) {
                    println(localValue.length)
                }
            }
        }
    }

    println("\n=== Points clés ===")
    println("1. Smart cast après 'is' dans when/if")
    println("2. Smart cast après null check")
    println("3. filterIsInstance pour smart cast sur collections")
    println("4. Attention aux propriétés mutables (var)")
}
