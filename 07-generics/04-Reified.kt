// ==============================================================================
// FICHE FORMATEUR — reified (types réifiés)
// Objectif : savoir lire `inline fun <reified T>` et comprendre pourquoi des API Android l'utilisent.
// À présenter (dans l'ordre) :
//   1. Type erasure : à l'exécution, List<String> et List<Int> sont la même classe
//   2. inline + reified : le compilateur recopie la fonction à l'appel avec le vrai type → `is T`, `T::class` possibles
//   3. filterIsInstance<T>() (stdlib) et safeCast
//   4. Avant/après : parse(json, String::class.java) vs parse<String>(json)
// Lien Android : `Json.decodeFromString<Todo>(body)` (kotlinx.serialization, TP04), `by viewModels<MyVm>()`,
//   `composable<TodoDetail> { ... }` et `backStackEntry.toRoute<TodoDetail>()` (Navigation type-safe, TP09),
//   `startActivity(Intent(this, DetailActivity::class.java))` (J5, ici sans reified).
// Pièges / questions fréquentes : reified exige inline ; le type d'une classe générique (Container<T>) ne peut
//   pas être réifié ; « pourquoi pas reified partout ? » → bytecode dupliqué à chaque appel.
// Si peu de temps : sections type erasure, inline + reified, comparaison avec/sans ; [SAUTER] le reste.
// ==============================================================================
/**
 * Exemple 4 : reified (types réifiés)
 *
 * Type erasure (effacement de type)
 * inline + reified
 * Cas d'usage : casting, reflection
 * Limites de reified
 */

// ===== Le problème : type erasure =====

fun demonstrateTypeErasure() {
    println("=== Type erasure (effacement de type) ===")

    val list1: List<String> = listOf("a", "b")
    val list2: List<Int> = listOf(1, 2)

    println("list1: $list1")
    println("list2: $list2")

    // [À DIRE] Le compilateur connaît <String>, la JVM (et ART sur Android) non : l'info est effacée au bytecode.
    // À runtime, les deux sont juste "List"
    println("\nlist1::class: ${list1::class}")
    println("list2::class: ${list2::class}")

    // Impossible de vérifier le type générique à runtime
    // fun <T> isType(x: Any): Boolean = x is T  // ❌ Erreur

    println("\n❌ Sur la JVM, les types génériques sont effacés à runtime")
    println("List<String> et List<Int> deviennent juste List")
}

// ===== Solution : inline + reified =====

// [À DIRE] inline = le corps est copié à chaque appel ; donc à l'endroit de l'appel, T est connu (String, Int…).
// [PIÈGE] `reified` sans `inline` ne compile pas.
inline fun <reified T> Any?.isA(): Boolean = this is T

inline fun <reified T> Any?.asA(): T? = this as? T

fun demonstrateReified() {
    println("\n=== inline + reified ===")

    println("\"kotlin\".isA<String>(): ${"kotlin".isA<String>()}")
    println("42.isA<String>(): ${42.isA<String>()}")
    println("42.isA<Int>(): ${42.isA<Int>()}")

    println()

    val str: String? = "kotlin".asA<String>()
    val num: Int? = "kotlin".asA<Int>()

    println("\"kotlin\".asA<String>(): $str")
    println("\"kotlin\".asA<Int>(): $num")

    println("\n✅ reified permet d'accéder au type T à runtime")
}

// ===== Cas d'usage : filterIsInstance =====

inline fun <reified T> List<*>.filterIsInstanceCustom(): List<T> {
    val result = mutableListOf<T>()
    for (item in this) {
        if (item is T) {
            result.add(item)
        }
    }
    return result
}

fun demonstrateFilterIsInstance() {
    println("\n=== filterIsInstance avec reified ===")

    val mixed: List<Any> = listOf(1, "hello", 2, "world", 3.14, true)
    println("mixed: $mixed")

    // [ANDROID] Utile pour trier une liste hétérogène, ex. `items.filterIsInstance<ListItem.Header>()`
    // quand une LazyColumn affiche plusieurs types d'éléments (sealed).
    val strings = mixed.filterIsInstance<String>()
    val ints = mixed.filterIsInstance<Int>()
    val doubles = mixed.filterIsInstance<Double>()

    println("\nStrings: $strings")
    println("Ints: $ints")
    println("Doubles: $doubles")

    println("\nAvec custom filterIsInstanceCustom:")
    val customStrings = mixed.filterIsInstanceCustom<String>()
    println("Strings: $customStrings")

    println("\n✅ filterIsInstance utilise reified pour vérifier le type")
}

// ===== Cas d'usage : casting sécurisé =====

// [À DIRE] `as?` renvoie null au lieu de lever ClassCastException ; avec un T non réifié, le cast ne serait
// pas vérifié (warning « unchecked cast »).
inline fun <reified T> safeCast(value: Any?): T? {
    return value as? T
}

inline fun <reified T> requireCast(value: Any?): T {
    return value as? T ?: throw ClassCastException("Cannot cast $value to ${T::class}")
}

fun demonstrateSafeCast() {
    println("\n=== Casting sécurisé avec reified ===")

    val value1: Any = "Kotlin"
    val value2: Any = 42

    val str1 = safeCast<String>(value1)
    val str2 = safeCast<String>(value2)

    println("safeCast<String>(\"Kotlin\"): $str1")
    println("safeCast<String>(42): $str2")

    println()

    val int1 = requireCast<Int>(value2)
    println("requireCast<Int>(42): $int1")

    try {
        requireCast<Int>(value1)
    } catch (e: ClassCastException) {
        println("requireCast<Int>(\"Kotlin\"): ${e.message}")
    }

    println("\n✅ reified simplifie le casting avec type safety")
}

// ===== Cas d'usage : création d'instances =====

// [SAUTER] Réflexion : peu utile en Android (coûteuse, et R8 peut supprimer les constructeurs non référencés).
inline fun <reified T> create(): T {
    return T::class.java.getDeclaredConstructor().newInstance()
}

data class Person(val name: String = "Unknown", val age: Int = 0)

fun demonstrateCreate() {
    println("\n=== Création d'instances avec reified ===")

    // [ATTENTION FORMATEUR] Person a des valeurs par défaut pour TOUS ses paramètres : Kotlin/JVM génère alors
    // un constructeur sans argument, donc create<Person>() fonctionnerait. La note ci-dessous est trompeuse.
    // Note: nécessite un constructeur sans paramètres
    // val person = create<Person>()
    // println("Created: $person")

    println("💡 reified permet d'accéder à T::class")
    println("Mais création d'instance nécessite réflexion et constructeur approprié")
}

// ===== Cas d'usage : JSON parsing =====

// [ANDROID] C'est LE cas d'usage à retenir : `Json.decodeFromString<List<Todo>>(body)` en kotlinx.serialization.
// Avec Retrofit (TP04), c'est le converter qui désérialise : `suspend fun getTodos(): List<Todo>` dans l'interface.
// [PIÈGE] Ce parseur est un jouet : parse<String>("\"Kotlin\"") renvoie la chaîne AVEC les guillemets,
// et parse<Int>("abc") lève une exception (null casté en Int). Ne pas le présenter comme un vrai parseur JSON.
// Simulé (sans dépendances externes)
object JsonParser {
    inline fun <reified T> parse(json: String): T {
        println("  Parsing JSON to ${T::class.simpleName}")
        // Implémentation simplifiée
        return when (T::class) {
            String::class -> json as T
            Int::class -> json.toIntOrNull() as T
            else -> throw IllegalArgumentException("Unsupported type")
        }
    }
}

fun demonstrateJsonParsing() {
    println("\n=== JSON parsing avec reified ===")

    val str = JsonParser.parse<String>("\"Kotlin\"")
    val num = JsonParser.parse<Int>("42")

    println("Parsed String: $str")
    println("Parsed Int: $num")

    println("\n✅ reified évite de passer Class<T> explicitement")
    println("JsonParser.parse<String>(json) au lieu de parse(json, String::class)")
}

// ===== Cas d'usage : validation de type =====
// [SAUTER] Variante de safeCast/requireCast.

inline fun <reified T> validateType(value: Any?, fieldName: String): T {
    return when {
        value == null -> throw IllegalArgumentException("$fieldName cannot be null")
        value !is T -> throw IllegalArgumentException("$fieldName must be ${T::class.simpleName}")
        else -> value
    }
}

fun demonstrateValidation() {
    println("\n=== Validation de type avec reified ===")

    val name = validateType<String>("Alice", "name")
    println("Validated name: $name")

    val age = validateType<Int>(30, "age")
    println("Validated age: $age")

    try {
        validateType<String>(42, "name")
    } catch (e: IllegalArgumentException) {
        println("Error: ${e.message}")
    }

    println("\n✅ reified pour validation de type avec messages d'erreur clairs")
}

// ===== Cas d'usage : genericArray =====
// [SAUTER] Array<T> est rare en Android applicatif : on manipule des List.

inline fun <reified T> genericArray(size: Int, init: (Int) -> T): Array<T> {
    return Array(size, init)
}

fun demonstrateGenericArray() {
    println("\n=== genericArray avec reified ===")

    val strings = genericArray(5) { "Item $it" }
    val ints = genericArray(5) { it * 2 }

    println("strings: ${strings.joinToString()}")
    println("ints: ${ints.joinToString()}")

    println("\n✅ reified permet de créer des Array<T>")
    println("Sinon, on doit utiliser Array<Any?> et caster")
}

// ===== Cas d'usage : type-safe builders =====

class ListBuilder<T> {
    private val items = mutableListOf<T>()

    fun add(item: T) {
        items.add(item)
    }

    fun build(): List<T> = items.toList()
}

// [ATTENTION FORMATEUR] Ce `buildList` masque la fonction standard `buildList { add(...) }` (stdlib, stable
// depuis Kotlin 1.6) : risque de confusion si un stagiaire la connaît ou copie le code. Le préciser à l'oral.
// [SAUTER] Section optionnelle (lambda avec receveur + reified).
inline fun <reified T> buildList(block: ListBuilder<T>.() -> Unit): List<T> {
    println("  Building List<${T::class.simpleName}>")
    val builder = ListBuilder<T>()
    builder.block()
    return builder.build()
}

fun demonstrateTypeBuilder() {
    println("\n=== Type-safe builders avec reified ===")

    val strings = buildList<String> {
        add("Kotlin")
        add("Java")
        add("Scala")
    }

    println("strings: $strings")

    val numbers = buildList<Int> {
        add(1)
        add(2)
        add(3)
    }

    println("numbers: $numbers")

    println("\n✅ reified pour builders type-safe avec logging du type")
}

// ===== Limites de reified =====

// [ATTENTION FORMATEUR] Affirmation inexacte : une méthode membre `inline fun <reified R> get(): R` est
// autorisée dans une classe. Ce qui est impossible, c'est de réifier le paramètre de la CLASSE (ici T).
// Même correction pour le message « reified impossible dans les classes » de demonstrateLimitations().
class Container<T> {
    // ❌ reified impossible dans une classe
    // inline fun <reified T> get(): T = ...

    // ✅ Possible dans une fonction inline au niveau package
}

// ❌ reified impossible sans inline
// fun <reified T> notInline() = Unit

// ✅ reified OK avec inline
inline fun <reified T> withInline() = T::class

fun demonstrateLimitations() {
    println("\n=== Limites de reified ===")

    println("❌ reified nécessite inline")
    println("❌ reified impossible dans les classes/méthodes non-inline")
    println("❌ inline augmente la taille du bytecode")
    println("✅ reified parfait pour les helpers de haut niveau")
    println("✅ Ne pas abuser : réserver aux cas où c'est vraiment utile")
}

// ===== Comparaison : avec et sans reified =====

// [À DIRE] Le style « sans reified » est celui des API Java : `retrofit.create(TodoApi::class.java)`,
// `Intent(this, DetailActivity::class.java)`. Le style reified est celui des API Kotlin récentes (`toRoute<T>()`).
// Sans reified : doit passer le Class
fun <T> createWithClass(clazz: Class<T>): T {
    return clazz.getDeclaredConstructor().newInstance()
}

// Avec reified : pas besoin de passer Class
inline fun <reified T> createWithReified(): T {
    return T::class.java.getDeclaredConstructor().newInstance()
}

fun demonstrateComparison() {
    println("\n=== Comparaison : avec et sans reified ===")

    println("Sans reified :")
    println("  fun <T> parse(json: String, clazz: Class<T>): T")
    println("  parse(json, String::class.java)")
    println("  → Verbeux, doit passer Class explicitement")

    println("\nAvec reified :")
    println("  inline fun <reified T> parse(json: String): T")
    println("  parse<String>(json)")
    println("  → Concis, type inféré")

    println("\n✅ reified améliore l'ergonomie de l'API")
}

// ===== Cas d'usage stdlib : lazy avec reified =====

fun demonstrateStdlibUsage() {
    println("\n=== Cas d'usage stdlib ===")

    println("filterIsInstance<T>() : utilise reified")
    println("Array<T>(size, init) : nécessite reified internement")
    println("typeOf<T>() : utilise reified (Kotlin 1.6+)")

    val mixed = listOf(1, "a", 2, "b")
    val ints = mixed.filterIsInstance<Int>()

    println("\nmixed.filterIsInstance<Int>(): $ints")

    println("\n💡 Beaucoup d'APIs Kotlin utilisent reified en interne")
}

// ===== Pattern : reified + variance =====
// [SAUTER] Pas de variance (in/out) réellement illustrée ici : seulement le fait que `is Number` capture Int et Double.

inline fun <reified T> List<*>.filterByType(): List<T> {
    return filterIsInstance<T>()
}

fun demonstrateReifiedVariance() {
    println("\n=== reified + variance ===")

    val mixed: List<Any> = listOf(1, "hello", 2.5, "world")

    val strings = mixed.filterByType<String>()
    val numbers = mixed.filterByType<Number>()

    println("mixed: $mixed")
    println("strings: $strings")
    println("numbers: $numbers")

    println("\n✅ reified fonctionne bien avec la variance")
}

fun main() {
    demonstrateTypeErasure()
    demonstrateReified()
    demonstrateFilterIsInstance()
    demonstrateSafeCast()
    demonstrateCreate()
    demonstrateJsonParsing()
    demonstrateValidation()
    demonstrateGenericArray()
    demonstrateTypeBuilder()
    demonstrateLimitations()
    demonstrateComparison()
    demonstrateStdlibUsage()
    demonstrateReifiedVariance()

    println("\n=== Résumé ===")
    println("1. Type erasure : les types génériques sont effacés à runtime")
    println("2. reified : permet d'accéder au type T à runtime")
    println("3. Nécessite inline : inline fun <reified T>")
    println("4. Cas d'usage : filterIsInstance, casting, validation")
    println("5. Limites : uniquement dans fonctions inline")
    println("6. Avantage : APIs plus ergonomiques (pas de Class<T>)")
    println("7. Ne pas abuser : réserver aux helpers utilitaires")
}
