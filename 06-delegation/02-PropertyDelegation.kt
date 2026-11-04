// ==============================================================================
// FICHE FORMATEUR — Délégation de propriété (by lazy, observable, vetoable, notNull)
// Objectif : comprendre le mot-clé `by` sur une propriété, pour le reconnaître partout dans Android.
// À présenter (dans l'ordre) :
//   1. `by` = « les get/set sont délégués à un objet » (getValue/setValue) — pas de magie
//   2. lazy : calcul au premier accès, une seule fois (montrer la sortie console eager vs lazy)
//   3. observable / vetoable : callback après / validation avant affectation
//   4. lateinit vs lazy vs notNull (tableau récapitulatif final)
// Lien Android : `by viewModels()`, `by remember { mutableStateOf(...) }` (J2), `by lazy { Retrofit... }`
//   (TP04), `val uiState by viewModel.uiState.collectAsStateWithLifecycle()` (J3), `by preferencesDataStore` (TP12).
// Pièges / questions fréquentes : lazy sur `val` uniquement ; lateinit interdit sur Int/Double (d'où notNull) ;
//   « Pourquoi `by remember` a besoin d'un import getValue/setValue ? » → c'est exactement ce mécanisme.
// Si peu de temps : lazy + comparaison finale + lien `by` Android ; [SAUTER] modes de thread-safety et caching.
// ==============================================================================
/**
 * Exemple 2 : Délégation de propriété
 *
 * lazy, observable, vetoable
 * Delegates standard de la stdlib
 * Syntaxe 'by'
 */

import kotlin.properties.Delegates

// ===== lazy : initialisation paresseuse =====

class ExpensiveResource {
    init {
        println("  ExpensiveResource created (heavy initialization)")
        Thread.sleep(100)
    }

    fun use() {
        println("  Using ExpensiveResource")
    }
}

class ServiceWithEagerInit {
    // ❌ Initialisé immédiatement, même si jamais utilisé
    private val resource = ExpensiveResource()

    fun doWork() {
        println("ServiceWithEagerInit.doWork()")
        resource.use()
    }
}

// [À DIRE] Le bloc lazy est exécuté au premier accès puis la valeur est mémorisée : montrer qu'au
// 2e doWork(), « Initializing lazy resource » ne s'affiche plus.
// [ANDROID] Typique : `private val api: TodoApi by lazy { retrofit.create(TodoApi::class.java) }` (TP04),
// ou `private val viewModel: TodoViewModel by viewModels()` dans une Activity (lazy lui aussi).
class ServiceWithLazy {
    // ✅ Initialisé seulement au premier accès
    private val resource: ExpensiveResource by lazy {
        println("  Initializing lazy resource")
        ExpensiveResource()
    }

    fun doWork() {
        println("ServiceWithLazy.doWork()")
        resource.use()
    }
}

fun demonstrateLazy() {
    println("=== lazy : initialisation paresseuse ===")

    println("\nAvec eager init:")
    val service1 = ServiceWithEagerInit()
    println("Instance créée")
    service1.doWork()

    println("\nAvec lazy:")
    val service2 = ServiceWithLazy()
    println("Instance créée (resource pas encore initialisée)")
    service2.doWork()
    println("Deuxième appel:")
    service2.doWork()

    println("\n✅ lazy : initialisé une seule fois, au premier accès")
}

// ===== lazy : modes de thread-safety =====
// [SAUTER] Détail rarement utile en formation Android : retenir juste que le défaut (SYNCHRONIZED) est sûr.
// [PIÈGE] Thread.sleep ici = simulation JVM ; sur Android, jamais de travail lourd sur le thread principal
// (même dans un lazy) → `withContext(Dispatchers.IO)` dans une suspend fun.

class LazyModes {
    // SYNCHRONIZED (défaut) : thread-safe
    val lazySynchronized: String by lazy {
        println("  Initializing lazySynchronized")
        "Synchronized value"
    }

    // PUBLICATION : plusieurs threads peuvent initialiser, un seul gagne
    val lazyPublication: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        println("  Initializing lazyPublication")
        "Publication value"
    }

    // NONE : pas thread-safe (plus rapide)
    val lazyNone: String by lazy(LazyThreadSafetyMode.NONE) {
        println("  Initializing lazyNone")
        "None value"
    }
}

fun demonstrateLazyModes() {
    println("\n=== lazy : modes de thread-safety ===")

    val modes = LazyModes()
    println("lazySynchronized: ${modes.lazySynchronized}")
    println("lazyPublication: ${modes.lazyPublication}")
    println("lazyNone: ${modes.lazyNone}")

    println("\n💡 SYNCHRONIZED (défaut) : thread-safe")
    println("💡 PUBLICATION : peut initialiser plusieurs fois")
    println("💡 NONE : pas thread-safe, plus rapide")
}

// ===== observable : observer les changements =====

// [À DIRE] observable : le callback reçoit (propriété, ancienne valeur, nouvelle valeur) APRÈS l'affectation.
// [ANDROID] En pratique, pour « observer » un état on n'utilise pas Delegates.observable mais un StateFlow
// (`_uiState.update { it.copy(...) }`) ou `mutableStateOf` en Compose, qui déclenche la recomposition.
class User {
    var name: String by Delegates.observable("Unknown") { prop, old, new ->
        println("  ${prop.name} changed: '$old' -> '$new'")
    }

    var age: Int by Delegates.observable(0) { prop, old, new ->
        println("  ${prop.name} changed: $old -> $new")
    }
}

fun demonstrateObservable() {
    println("\n=== observable : observer les changements ===")

    val user = User()
    println("\nSetting name:")
    user.name = "Alice"
    println("\nSetting name again:")
    user.name = "Bob"

    println("\nSetting age:")
    user.age = 30
    println("\nSetting age again:")
    user.age = 31

    println("\n✅ observable : callback appelé après chaque modification")
}

// ===== vetoable : valider avant modification =====

// [À DIRE] vetoable : le lambda est appelé AVANT l'affectation et renvoie un Boolean (dernière expression).
// [PIÈGE] Le refus est silencieux pour l'appelant : pas d'exception, la valeur reste l'ancienne (ligne « Toujours 50.0 »).
class BankAccount {
    var balance: Double by Delegates.vetoable(0.0) { prop, old, new ->
        val isValid = new >= 0.0
        if (!isValid) {
            println("  ❌ Rejected: ${prop.name} cannot be negative (tried $old -> $new)")
        } else {
            println("  ✅ Accepted: ${prop.name} changed $old -> $new")
        }
        isValid  // true = accepter, false = rejeter
    }
}

fun demonstrateVetoable() {
    println("\n=== vetoable : valider avant modification ===")

    val account = BankAccount()
    println("balance = ${account.balance}")

    println("\nDeposit 100:")
    account.balance = 100.0
    println("balance = ${account.balance}")

    println("\nWithdraw 50:")
    account.balance = 50.0
    println("balance = ${account.balance}")

    println("\nTry to set negative:")
    account.balance = -10.0
    println("balance = ${account.balance}")  // Toujours 50.0

    println("\n✅ vetoable : modification acceptée seulement si callback retourne true")
}

// ===== Cas d'usage : configuration avec validation =====

// [SAUTER] Variante du vetoable précédent ; à montrer seulement si question sur `_` pour les paramètres inutilisés.
class AppConfig {
    var port: Int by Delegates.vetoable(8080) { _, _, new ->
        (new in 1024..65535).also { isValid ->
            if (!isValid) println("  ❌ Port must be in range 1024..65535")
        }
    }

    var maxConnections: Int by Delegates.vetoable(100) { _, _, new ->
        (new > 0).also { isValid ->
            if (!isValid) println("  ❌ maxConnections must be positive")
        }
    }

    var logLevel: String by Delegates.observable("INFO") { prop, old, new ->
        println("  Log level changed: $old -> $new")
    }
}

fun demonstrateConfigValidation() {
    println("\n=== Cas d'usage : configuration avec validation ===")

    val config = AppConfig()
    println("Initial config: port=${config.port}, max=${config.maxConnections}, log=${config.logLevel}")

    println("\nSet valid port:")
    config.port = 3000
    println("port = ${config.port}")

    println("\nTry invalid port:")
    config.port = 80  // < 1024
    println("port = ${config.port}")  // Toujours 3000

    println("\nSet valid maxConnections:")
    config.maxConnections = 200
    println("maxConnections = ${config.maxConnections}")

    println("\nTry invalid maxConnections:")
    config.maxConnections = -10
    println("maxConnections = ${config.maxConnections}")  // Toujours 200

    println("\nChange log level:")
    config.logLevel = "DEBUG"
}

// ===== notNull : initialisation différée sans null =====

// [À DIRE] La vraie différence avec lateinit : lateinit est interdit sur les types primitifs (Int, Double, Boolean…),
// Delegates.notNull() fonctionne avec tous les types.
// [ANDROID] Côté Android, on croise surtout `lateinit var binding: ActivityMainBinding` (J5, View Binding) ;
// accès avant init → UninitializedPropertyAccessException.
class Service {
    // Alternative à lateinit pour types non-nullable
    var connection: String by Delegates.notNull()

    fun connect(url: String) {
        connection = url
        println("Connected to $connection")
    }

    fun disconnect() {
        println("Disconnecting from $connection")
    }
}

fun demonstrateNotNull() {
    println("\n=== notNull : initialisation différée ===")

    val service = Service()

    // Pas encore initialisé
    try {
        println(service.connection)  // Lève IllegalStateException
    } catch (e: IllegalStateException) {
        println("❌ Error: ${e.message}")
    }

    // Initialisation
    service.connect("https://api.example.com")

    // Maintenant accessible
    service.disconnect()

    println("\n💡 notNull : comme lateinit, mais avec delegate")
}

// ===== Combinaison : lazy + observable =====

// [PIÈGE] Un statut en String ("loading", "success"…) est fragile (fautes de frappe, when non exhaustif) :
// en MVVM on utilise une sealed interface UiState (TP07) et `when (uiState) { is UiState.Loading -> ... }`.
class DataManager {
    // Lazy + transformation
    val processedData: List<String> by lazy {
        println("  Loading and processing data...")
        Thread.sleep(50)
        listOf("data1", "data2", "data3").map { it.uppercase() }
    }

    // Observable avec logique métier
    var status: String by Delegates.observable("idle") { _, old, new ->
        println("  Status transition: $old -> $new")
        when (new) {
            "loading" -> println("    🔄 Starting operation...")
            "success" -> println("    ✅ Operation completed")
            "error" -> println("    ❌ Operation failed")
        }
    }
}

fun demonstrateCombination() {
    println("\n=== Combinaison : lazy + observable ===")

    val manager = DataManager()

    manager.status = "loading"
    val data = manager.processedData
    println("Data: $data")
    manager.status = "success"

    println("\n💡 lazy et observable sont composables")
}

// ===== Cas d'usage : caching avec lazy =====
// [SAUTER] Redite du premier exemple lazy.
// [ATTENTION FORMATEUR] fibonacci(100) dépasse Long.MAX_VALUE (max représentable : fib(92)) : le résultat
// affiché est faux (dépassement silencieux). Ne pas annoncer la valeur, ou en profiter pour parler d'overflow.

class ExpensiveCalculator {
    // Cache avec lazy
    val fibonacci100: Long by lazy {
        println("  Computing fibonacci(100)...")
        fibonacci(100)
    }

    val factorial20: Long by lazy {
        println("  Computing factorial(20)...")
        factorial(20)
    }

    private fun fibonacci(n: Int): Long {
        return if (n <= 1) n.toLong()
        else {
            var a = 0L
            var b = 1L
            repeat(n - 1) {
                val temp = a + b
                a = b
                b = temp
            }
            b
        }
    }

    private fun factorial(n: Int): Long {
        return (1..n).fold(1L) { acc, i -> acc * i }
    }
}

fun demonstrateCaching() {
    println("\n=== Cas d'usage : caching avec lazy ===")

    val calc = ExpensiveCalculator()

    println("First access to fibonacci100:")
    println("Result: ${calc.fibonacci100}")

    println("\nSecond access (cached):")
    println("Result: ${calc.fibonacci100}")

    println("\nFirst access to factorial20:")
    println("Result: ${calc.factorial20}")

    println("\n✅ lazy = cache automatique thread-safe")
}

// ===== Comparaison : lateinit vs lazy vs notNull =====

// [À DIRE] Récap à faire au tableau : lateinit (var, init externe, pas de primitif), lazy (val, init calculée
// au 1er accès), notNull (var, tous types). Règle Android : préférer val + lazy, ou un état nullable explicite.
class ComparisonExample {
    // lateinit : var, types non-nullable, pas de custom initializer
    lateinit var lateinitProp: String

    // lazy : val, initialisation personnalisée, thread-safe
    val lazyProp: String by lazy {
        "Lazy initialized"
    }

    // notNull : var, initialisation différée, lance exception si pas initialisé
    var notNullProp: String by Delegates.notNull()
}

fun demonstrateComparison() {
    println("\n=== Comparaison : lateinit vs lazy vs notNull ===")

    val example = ComparisonExample()

    // lateinit
    example.lateinitProp = "Late init"
    println("lateinit: ${example.lateinitProp}")

    // lazy
    println("lazy: ${example.lazyProp}")

    // notNull
    example.notNullProp = "Not null"
    println("notNull: ${example.notNullProp}")

    println("\n💡 lateinit: var, exception si pas init")
    println("💡 lazy: val, init au 1er accès, thread-safe")
    println("💡 notNull: var, comme lateinit avec delegate")
}

fun main() {
    demonstrateLazy()
    demonstrateLazyModes()
    demonstrateObservable()
    demonstrateVetoable()
    demonstrateConfigValidation()
    demonstrateNotNull()
    demonstrateCombination()
    demonstrateCaching()
    demonstrateComparison()

    println("\n=== Résumé ===")
    println("1. lazy : initialisation paresseuse (val)")
    println("   - Thread-safe par défaut (SYNCHRONIZED)")
    println("   - Calculé une seule fois")
    println("2. observable : observer les modifications (var)")
    println("   - Callback appelé après modification")
    println("3. vetoable : valider avant modification (var)")
    println("   - Retourne true pour accepter, false pour rejeter")
    println("4. notNull : initialisation différée sans null")
    println("5. Cas d'usage : config, cache, validation, logging")
}
