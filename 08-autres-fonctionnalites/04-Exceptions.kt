// ==============================================================================
// FICHE FORMATEUR — Exceptions, Result, require/check/error
// Objectif : gérer les erreurs « à la Kotlin » pour préparer le repository qui renvoie un Result (TP04).
// À présenter (dans l'ordre) :
//   1. Pas d'exceptions vérifiées en Kotlin (pas de `throws`) + try/catch est une expression
//   2. use { } pour fermer une ressource (équivalent try-with-resources)
//   3. runCatching / Result : getOrElse, map, onSuccess/onFailure
//   4. require / check / error : préconditions et invariants
//   5. Erreurs métier : Result ou sealed class + when exhaustif (prépare UiState du TP07)
// Lien Android : `suspend fun getTodos(): Result<List<Todo>>` dans le repository (TP04) ;
//   `when (uiState) { is UiState.Error -> ... }` (TP07) ; IOException / HttpException de Retrofit.
// Pièges / questions fréquentes : runCatching attrape aussi CancellationException → l'annulation d'une
//   coroutine (viewModelScope) est avalée ; règle du TP04 : la relancer. Ne jamais faire catch (e: Exception) { }.
// Si peu de temps : try expression, runCatching + Result, sealed class ; [SAUTER] finally, multi-catch, custom exception.
// ==============================================================================
/**
 * Exemple 4 : Gestion des exceptions
 *
 * try/catch comme expression
 * finally
 * use (ressources)
 * runCatching
 * Exceptions vs erreurs métier
 * require, check, error
 */

// ===== try/catch classique =====

// [À DIRE] Différence avec Java : aucune exception n'est « checked », le compilateur n'oblige jamais à catcher.
// [ANDROID] Pour ce cas précis, `s.toIntOrNull() ?: 0` est plus idiomatique (ex. saisie d'un TextField).
fun parseIntClassic(s: String): Int {
    try {
        return s.toInt()
    } catch (e: NumberFormatException) {
        println("Error parsing: ${e.message}")
        return 0
    }
}

fun demonstrateTryCatch() {
    println("=== try/catch classique ===")

    println("parseIntClassic(\"123\"): ${parseIntClassic("123")}")
    println("parseIntClassic(\"abc\"): ${parseIntClassic("abc")}")

    println("\n✅ try/catch capture les exceptions")
}

// ===== try/catch comme expression =====

fun parseIntExpression(s: String): Int {
    return try {
        s.toInt()
    } catch (e: NumberFormatException) {
        0
    }
}

fun demonstrateTryCatchExpression() {
    println("\n=== try/catch comme expression ===")

    // [À DIRE] La valeur du try (ou du catch) est sa dernière expression : on peut écrire `val x = try {...}`.
    val value: Int = try {
        "123".toInt()
    } catch (e: NumberFormatException) {
        0
    }

    println("value: $value")

    println("parseIntExpression(\"123\"): ${parseIntExpression("123")}")
    println("parseIntExpression(\"abc\"): ${parseIntExpression("abc")}")

    println("\n✅ try/catch est une expression")
    println("Évite des variables mutables")
}

// ===== finally =====

// [ANDROID] En coroutines, finally sert à nettoyer quand une coroutine est annulée (ex. à la sortie de l'écran,
// annulation de viewModelScope) ; c'est aussi ce qui s'exécute à la fin d'un `callbackFlow` via `awaitClose { }` (TP10).
fun readWithFinally(): String {
    try {
        println("  Opening resource")
        return "ok"
    } finally {
        println("  Cleanup in finally")
    }
}

fun demonstrateFinally() {
    println("\n=== finally ===")

    val result = readWithFinally()
    println("Result: $result")

    println("\n✅ finally s'exécute toujours")
    println("Même en cas de return ou exception")
}

// ===== Multiple catch =====

// [SAUTER] Multi-catch : identique à Java (pas de syntaxe `catch (A | B)` en Kotlin, on utilise plusieurs catch).
// [ANDROID] Côté Retrofit on distingue typiquement `catch (e: IOException)` (réseau) et `catch (e: HttpException)` (code HTTP).
fun processValue(s: String): Int {
    return try {
        val num = s.toInt()
        100 / num
    } catch (e: NumberFormatException) {
        println("  Not a number: $s")
        0
    } catch (e: ArithmeticException) {
        println("  Division by zero")
        0
    }
}

fun demonstrateMultipleCatch() {
    println("\n=== Multiple catch ===")

    println("processValue(\"10\"): ${processValue("10")}")
    println("processValue(\"abc\"): ${processValue("abc")}")
    println("processValue(\"0\"): ${processValue("0")}")

    println("\n✅ Plusieurs catch pour différents types d'exceptions")
}

// ===== use : ressources auto-closeable =====

fun demonstrateUse() {
    println("\n=== use : ressources ===")

    // [À DIRE] use { } = try-with-resources de Java : close() est appelé même en cas d'exception.
    // [ANDROID] Ex. : `contentResolver.openInputStream(uri)?.use { it.readBytes() }`, ou un Cursor (`cursor.use { }`).

    val lines = """
        Line 1
        Line 2
        Line 3
    """.trimIndent().byteInputStream().use { stream ->
        stream.bufferedReader().readLines()
    }

    println("lines: $lines")

    println("\n✅ use garantit la fermeture de la ressource")
    println("Même en cas d'exception")
}

// ===== runCatching : Result<T> =====

fun demonstrateRunCatching() {
    println("\n=== runCatching ===")

    // [PIÈGE] runCatching attrape TOUT Throwable, y compris CancellationException. Dans une suspend fun,
    // ça casse l'annulation des coroutines. Au TP04 : `catch (e: CancellationException) { throw e }`
    // avant le catch général, ou `.onFailure { if (it is CancellationException) throw it }`.
    val r1: Result<Int> = runCatching { "123".toInt() }
    println("runCatching { \"123\".toInt() }: $r1")

    val r2: Result<Int> = runCatching { "abc".toInt() }
    println("runCatching { \"abc\".toInt() }: $r2")

    println("\n✅ runCatching capture l'exception dans un Result")
}

// ===== Result : getOrElse, getOrNull =====

fun safeParseInt(s: String): Int {
    return runCatching { s.toInt() }
        .getOrElse { 0 }
}

fun safeParseIntOrNull(s: String): Int? {
    return runCatching { s.toInt() }
        .getOrNull()
}

fun demonstrateResultMethods() {
    println("\n=== Result : méthodes ===")

    println("safeParseInt(\"123\"): ${safeParseInt("123")}")
    println("safeParseInt(\"abc\"): ${safeParseInt("abc")}")

    println("\nsafeParseIntOrNull(\"123\"): ${safeParseIntOrNull("123")}")
    println("safeParseIntOrNull(\"abc\"): ${safeParseIntOrNull("abc")}")

    println("\n✅ getOrElse : valeur par défaut")
    println("✅ getOrNull : null si erreur")
}

// ===== Result : map/mapCatching =====

// [À DIRE] map ne protège pas le lambda : si `it * 2` levait une exception, elle remonterait.
// C'est le rôle de mapCatching (annoncé en titre mais non montré ici).
fun demonstrateResultMap() {
    println("\n=== Result : map ===")

    val result = runCatching { "10".toInt() }
        .map { it * 2 }

    println("result: $result")

    val error = runCatching { "abc".toInt() }
        .map { it * 2 }

    println("error: $error")

    println("\n✅ map transforme le succès, propage l'erreur")
}

// ===== Result : recover =====

fun demonstrateRecover() {
    println("\n=== Result : recover ===")

    val result = runCatching { "abc".toInt() }
        .recover { 0 }

    println("result: $result")

    val value = runCatching { "abc".toInt() }
        .recover { 0 }
        .getOrThrow()

    println("value: $value")

    println("\n✅ recover permet de récupérer d'une erreur")
}

// ===== Result : onSuccess/onFailure =====

fun demonstrateResultCallbacks() {
    println("\n=== Result : onSuccess/onFailure ===")

    // [ANDROID] Forme typique dans un ViewModel (TP04/TP07) :
    // `repository.getTodos().onSuccess { todos -> _uiState.update { UiState.Success(todos) } }
    //     .onFailure { e -> _uiState.update { UiState.Error(e.message) } }` dans un `viewModelScope.launch`.
    runCatching { "123".toInt() }
        .onSuccess { println("  Success: $it") }
        .onFailure { println("  Failure: ${it.message}") }

    runCatching { "abc".toInt() }
        .onSuccess { println("  Success: $it") }
        .onFailure { println("  Failure: ${it.message}") }

    println("\n💡 onSuccess/onFailure pour side-effects")
}

// ===== require : préconditions =====

// [À DIRE] require → IllegalArgumentException (faute de l'appelant), check → IllegalStateException
// (objet dans un mauvais état), error() → IllegalStateException + type Nothing (utilisable dans un when).
fun divide(a: Int, b: Int): Int {
    require(b != 0) { "Divisor must not be zero" }
    return a / b
}

fun demonstrateRequire() {
    println("\n=== require : préconditions ===")

    println("divide(10, 2): ${divide(10, 2)}")

    try {
        divide(10, 0)
    } catch (e: IllegalArgumentException) {
        println("Error: ${e.message}")
    }

    println("\n✅ require vérifie les préconditions")
    println("Jette IllegalArgumentException si false")
}

// ===== check : invariants =====

// [PIÈGE] Double pour de l'argent : erreurs d'arrondi (0.1 + 0.2). En vrai : centimes en Long ou BigDecimal.
// Par ailleurs, solde insuffisant est plutôt un cas métier attendu (→ Result/sealed) qu'un invariant.
class BankAccount(private var balance: Double) {
    fun withdraw(amount: Double) {
        check(balance >= amount) { "Insufficient balance" }
        balance -= amount
    }

    fun getBalance() = balance
}

fun demonstrateCheck() {
    println("\n=== check : invariants ===")

    val account = BankAccount(100.0)

    account.withdraw(50.0)
    println("Balance after withdraw: ${account.getBalance()}")

    try {
        account.withdraw(100.0)
    } catch (e: IllegalStateException) {
        println("Error: ${e.message}")
    }

    println("\n✅ check vérifie les invariants internes")
    println("Jette IllegalStateException si false")
}

// ===== error : signaler une erreur =====

fun processStatus(status: String): String {
    return when (status) {
        "OK" -> "Success"
        "ERROR" -> "Failed"
        else -> error("Unknown status: $status")
    }
}

fun demonstrateError() {
    println("\n=== error : signaler une erreur ===")

    println("processStatus(\"OK\"): ${processStatus("OK")}")

    try {
        processStatus("UNKNOWN")
    } catch (e: IllegalStateException) {
        println("Error: ${e.message}")
    }

    println("\n✅ error jette IllegalStateException")
    println("Utile dans les else/when pour les cas impossibles")
}

// ===== Exceptions vs erreurs métier =====

// [À DIRE] Message clé de la section : un échec ATTENDU (réseau coupé, utilisateur absent) fait partie du type
// de retour ; l'UI doit pouvoir l'afficher sans try/catch.
// ❌ Mauvais : exception pour flux normal
fun findUserBad(id: String): String {
    if (id == "1") return "Alice"
    throw Exception("User not found")
}

// ✅ Bon : Result pour flux normal
fun findUserGood(id: String): Result<String> {
    return if (id == "1") {
        Result.success("Alice")
    } else {
        Result.failure(Exception("User not found"))
    }
}

fun demonstrateExceptionsVsErrors() {
    println("\n=== Exceptions vs erreurs métier ===")

    println("Approche 1 : exception")
    try {
        println("User: ${findUserBad("1")}")
        println("User: ${findUserBad("2")}")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }

    println("\nApproche 2 : Result")
    findUserGood("1")
        .onSuccess { println("User: $it") }
        .onFailure { println("Error: ${it.message}") }

    findUserGood("2")
        .onSuccess { println("User: $it") }
        .onFailure { println("Error: ${it.message}") }

    println("\n💡 Règle :")
    println("   - Exceptions : cas exceptionnels, bugs, violations")
    println("   - Result : flux normal avec échec attendu")
}

// ===== sealed class pour erreurs métier =====

// [ANDROID] Exactement le modèle du UiState du TP07 :
// `sealed interface UiState { data object Loading : UiState; data class Success(val tasks: List<Task>) : UiState;
//   data class Error(val message: String) : UiState }`, consommé en Compose par `when (uiState) { ... }`.
// [ATTENTION FORMATEUR] Depuis Kotlin 1.9, on écrit `data object Unauthorized` (toString lisible) plutôt que
// `object` ; dans le when, `UserResult.Unauthorized ->` suffit (le `is` est inutile pour un object).
sealed class UserResult {
    data class Success(val name: String) : UserResult()
    data class NotFound(val id: String) : UserResult()
    object Unauthorized : UserResult()
}

fun findUser(id: String, authorized: Boolean): UserResult {
    if (!authorized) return UserResult.Unauthorized
    return if (id == "1") {
        UserResult.Success("Alice")
    } else {
        UserResult.NotFound(id)
    }
}

fun demonstrateSealedResult() {
    println("\n=== sealed class pour erreurs métier ===")

    // [À DIRE] Pas de branche else : si on ajoute un cas à la sealed class, ce when ne compile plus → c'est voulu.
    // Smart cast : dans la branche Success, `result1.name` est accessible sans cast.
    val result1 = findUser("1", true)
    when (result1) {
        is UserResult.Success -> println("User: ${result1.name}")
        is UserResult.NotFound -> println("Not found: ${result1.id}")
        is UserResult.Unauthorized -> println("Unauthorized")
    }

    val result2 = findUser("2", true)
    when (result2) {
        is UserResult.Success -> println("User: ${result2.name}")
        is UserResult.NotFound -> println("Not found: ${result2.id}")
        is UserResult.Unauthorized -> println("Unauthorized")
    }

    println("\n✅ sealed class : erreurs métier explicites et type-safe")
    println("when garantit l'exhaustivité")
}

// ===== Custom exception =====
// [SAUTER] Simple héritage d'Exception ; à mentionner seulement si question.

class ValidationException(message: String) : Exception(message)

fun validateAge(age: Int) {
    if (age < 0) throw ValidationException("Age cannot be negative")
    if (age > 150) throw ValidationException("Age too high")
}

fun demonstrateCustomException() {
    println("\n=== Custom exception ===")

    validateAge(30)
    println("validateAge(30): OK")

    try {
        validateAge(-5)
    } catch (e: ValidationException) {
        println("Error: ${e.message}")
    }

    try {
        validateAge(200)
    } catch (e: ValidationException) {
        println("Error: ${e.message}")
    }

    println("\n💡 Exceptions custom pour des erreurs spécifiques")
}

// ===== Bonnes pratiques =====

fun demonstrateBestPractices() {
    println("\n=== Bonnes pratiques ===")

    println("✅ Utiliser try/catch comme expression")
    println("✅ finally pour cleanup (ou use)")
    println("✅ runCatching pour pont exception → Result")

    println("\n✅ require : préconditions sur arguments")
    println("✅ check : invariants internes")
    println("✅ error : cas impossibles")

    println("\n✅ Exceptions pour :")
    println("   - erreurs exceptionnelles (I/O, bug)")
    println("   - violations de contrat")

    println("\n✅ Result/sealed class pour :")
    println("   - flux normal avec échec attendu")
    println("   - erreurs métier")

    println("\n❌ Éviter :")
    println("   - exceptions pour contrôle de flux")
    println("   - avaler des exceptions sans log/traitement")
}

fun main() {
    demonstrateTryCatch()
    demonstrateTryCatchExpression()
    demonstrateFinally()
    demonstrateMultipleCatch()
    demonstrateUse()
    demonstrateRunCatching()
    demonstrateResultMethods()
    demonstrateResultMap()
    demonstrateRecover()
    demonstrateResultCallbacks()
    demonstrateRequire()
    demonstrateCheck()
    demonstrateError()
    demonstrateExceptionsVsErrors()
    demonstrateSealedResult()
    demonstrateCustomException()
    demonstrateBestPractices()

    println("\n=== Résumé ===")
    println("1. try/catch est une expression")
    println("2. finally s'exécute toujours")
    println("3. use pour ressources Closeable")
    println("4. runCatching : exception → Result<T>")
    println("5. require : préconditions (IllegalArgumentException)")
    println("6. check : invariants (IllegalStateException)")
    println("7. error : cas impossibles (IllegalStateException)")
    println("8. Exceptions pour cas exceptionnels")
    println("9. Result/sealed class pour erreurs métier")
}
