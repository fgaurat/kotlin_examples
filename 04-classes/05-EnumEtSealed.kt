// ==============================================================================
// FICHE FORMATEUR — Enum et sealed classes
// Objectif : choisir entre enum (valeurs fixes) et sealed (variantes portant des données différentes).
// À présenter (dans l'ordre) :
//   1. enum simple, puis avec propriétés/méthodes ; `entries` (et non plus values())
//   2. sealed interface Result / sealed class UiState
//   3. when exhaustif sans else (renderUiState)
//   4. Règle de choix enum vs sealed (DayOfWeek vs NetworkResponse)
//   5. LoginState : exemple d'états d'écran complet
// Lien Android : enum pour un filtre d'écran (`enum class TaskFilter { ALL, ACTIVE, DONE }` dans un
//   `FilterChip`/onglets) ou un flavor/BuildConfig (J4) ; sealed pour l'UiState du ViewModel (TP07),
//   les erreurs réseau du repository (TP04) et les routes de navigation (J4).
// Pièges / questions fréquentes : ordinal fragile (ne pas le persister en base/DataStore : stocker name) ;
//   `else` dans un when sur sealed → perte de la vérification ; conflits de noms avec Android (voir marqueurs).
// Si peu de temps : Enum de base + avec propriétés, UiState + when exhaustif, Enum vs Sealed ;
//   sauter Operation (comportements par constante), Command et Expression.
// ==============================================================================
/**
 * Exemple 5 : Enum et Sealed classes
 *
 * enum class : ensemble fermé simple
 * sealed class/interface : ensemble fermé avec variantes
 * When exhaustif
 */

// ===== Enum de base =====

enum class Status {
    NEW,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

// Enum avec propriétés
enum class Role(val level: Int, val displayName: String) {
    USER(1, "Utilisateur"),
    MODERATOR(5, "Modérateur"),
    ADMIN(10, "Administrateur");

    fun canAccess(requiredLevel: Int) = level >= requiredLevel
}

// [PIÈGE] Nom `Color` en conflit avec `androidx.compose.ui.graphics.Color` dans un projet Compose
//   (imports ambigus) : en vrai, nommer autrement (ex. `BrandColor`).
// Enum avec méthodes
enum class Color(val rgb: Int) {
    RED(0xFF0000),
    GREEN(0x00FF00),
    BLUE(0x0000FF);

    fun toHex() = "#${rgb.toString(16).padStart(6, '0').uppercase()}"
}

// [SAUTER] Méthodes abstraites par constante : rarement utile en Android, à montrer seulement si question.
// [PIÈGE] DIVIDE.apply(x, 0) lève ArithmeticException (division entière par zéro) : non géré ici.
// Enum avec comportements spécifiques par cas
enum class Operation {
    ADD {
        override fun apply(a: Int, b: Int) = a + b
    },
    SUBTRACT {
        override fun apply(a: Int, b: Int) = a - b
    },
    MULTIPLY {
        override fun apply(a: Int, b: Int) = a * b
    },
    DIVIDE {
        override fun apply(a: Int, b: Int) = a / b
    };

    abstract fun apply(a: Int, b: Int): Int
}

// ===== Sealed class : variantes riches =====
// [ATTENTION FORMATEUR] Ce `Result` maison masque `kotlin.Result` (utilisé par le repository du TP04 :
//   runCatching, getOrElse, onFailure). Le signaler ; en projet, préférer un autre nom.
// [À DIRE] `out T` + `Nothing` : Error et Loading sont des sous-types de Result<String>, Result<User>…

sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

// Sealed class pour états d'UI
sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Success(val data: List<String>) : UiState()
    data class Error(val message: String) : UiState()
}

// Sealed interface pour événements
sealed interface PaymentEvent {
    data class PaymentInitiated(val amount: Double, val currency: String) : PaymentEvent
    data class PaymentSucceeded(val transactionId: String, val amount: Double) : PaymentEvent
    data class PaymentFailed(val reason: String, val isRetryable: Boolean) : PaymentEvent
    data class PaymentCancelled(val userId: String, val reason: String) : PaymentEvent
}

// ===== When exhaustif =====
// [ANDROID] Version Compose (TP07) :
//   `when (val s = uiState) { UiState.Loading -> CircularProgressIndicator(); is UiState.Success -> LazyColumn { items(s.data) { Text(it) } } ... }`.
// [À DIRE] Démo : supprimer une branche → erreur « 'when' expression must be exhaustive ».

fun renderUiState(state: UiState): String = when (state) {
    is UiState.Idle -> "Idle: Waiting for action"
    is UiState.Loading -> "Loading: Please wait..."
    is UiState.Success -> "Success: ${state.data.size} items loaded"
    is UiState.Error -> "Error: ${state.message}"
    // Pas besoin de else : tous les cas sont couverts
}

fun <T> handleResult(result: Result<T>): String = when (result) {
    is Result.Success -> "✅ Data: ${result.data}"
    is Result.Error -> "❌ Error (${result.code}): ${result.message}"
    is Result.Loading -> "⏳ Loading..."
}

fun processPayment(event: PaymentEvent) {
    when (event) {
        is PaymentEvent.PaymentInitiated -> {
            println("Payment initiated: ${event.amount} ${event.currency}")
        }
        is PaymentEvent.PaymentSucceeded -> {
            println("Payment succeeded: ${event.transactionId}")
        }
        is PaymentEvent.PaymentFailed -> {
            println("Payment failed: ${event.reason}")
            if (event.isRetryable) {
                println("  → Retryable")
            }
        }
        is PaymentEvent.PaymentCancelled -> {
            println("Payment cancelled by ${event.userId}: ${event.reason}")
        }
    }
}

// ===== Enum vs Sealed : quand utiliser quoi ? =====

// [À DIRE] Règle de choix : « toutes les valeurs ont la même forme » → enum ; « chaque cas a ses propres données » → sealed.
// [PIÈGE] `java.time.DayOfWeek` existe déjà (dispo sur Android API 26+, ou via desugaring) : ne pas le redéfinir en vrai.
// ✅ Enum : ensemble fermé simple, valeurs sans données associées
enum class DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

fun isWeekend(day: DayOfWeek) = day in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

// [ANDROID] Correspond aux 2 familles d'erreurs Retrofit du TP04 : HttpException (code 4xx/5xx)
//   et IOException (pas de réseau) — à distinguer pour le message affiché à l'utilisateur.
// ✅ Sealed : variantes avec données différentes
sealed class NetworkResponse {
    data class Success(val body: String, val headers: Map<String, String>) : NetworkResponse()
    data class HttpError(val code: Int, val message: String) : NetworkResponse()
    data class NetworkError(val exception: Throwable) : NetworkResponse()
}

// ===== Sealed class pour modélisation métier =====
// [ANDROID] Même patron pour les actions utilisateur envoyées au ViewModel :
//   `sealed interface TaskAction { data class Toggle(val id: Int) : TaskAction; data object Refresh : TaskAction }` + `fun onAction(a: TaskAction)`.

sealed class Command {
    data class CreateUser(val username: String, val email: String) : Command()
    data class UpdateUser(val userId: Long, val newEmail: String) : Command()
    data class DeleteUser(val userId: Long) : Command()
}

fun executeCommand(command: Command): String = when (command) {
    is Command.CreateUser -> "Creating user: ${command.username}"
    is Command.UpdateUser -> "Updating user ${command.userId}"
    is Command.DeleteUser -> "Deleting user ${command.userId}"
}

// Sealed class pour états d'écran
sealed class LoginState {
    data object Initial : LoginState()
    data class LoggingIn(val username: String) : LoginState()
    data class LoggedIn(val user: User, val token: String) : LoginState()
    data class LoginFailed(val error: String, val canRetry: Boolean) : LoginState()
}

data class User(val id: Long, val username: String)

// ===== Hiérarchie sealed plus complexe =====
// [SAUTER] Arbre d'expressions récursif : joli mais hors sujet Android.

sealed interface Expression {
    data class Const(val value: Int) : Expression
    data class Add(val left: Expression, val right: Expression) : Expression
    data class Multiply(val left: Expression, val right: Expression) : Expression
}

fun evaluate(expr: Expression): Int = when (expr) {
    is Expression.Const -> expr.value
    is Expression.Add -> evaluate(expr.left) + evaluate(expr.right)
    is Expression.Multiply -> evaluate(expr.left) * evaluate(expr.right)
}

fun main() {
    println("=== Enum de base ===")
    val status = Status.IN_PROGRESS
    println("Status: $status")
    // [PIÈGE] Ne jamais persister `ordinal` (DataStore, Room) : réordonner l'enum corrompt les données. Stocker `name` + `valueOf()`.
    println("Ordinal: ${status.ordinal}")
    println("Name: ${status.name}")

    println("\nTous les status:")
    // [À DIRE] `entries` (Kotlin 1.9+) remplace `values()` : on le trouvera encore dans du vieux code / tutos.
    // [ANDROID] `Status.entries.forEach { FilterChip(selected = it == filter, onClick = { onFilter(it) }, label = { Text(it.name) }) }`.
    Status.entries.forEach { println("  $it") }

    println("\n=== Enum avec propriétés ===")
    val role = Role.MODERATOR
    println("Role: ${role.displayName} (level ${role.level})")
    println("Can access level 3: ${role.canAccess(3)}")
    println("Can access level 7: ${role.canAccess(7)}")

    println("\n=== Enum avec méthodes ===")
    Color.entries.forEach { color ->
        println("${color.name}: ${color.toHex()}")
    }

    println("\n=== Enum avec comportements ===")
    println("5 + 3 = ${Operation.ADD.apply(5, 3)}")
    println("5 * 3 = ${Operation.MULTIPLY.apply(5, 3)}")

    println("\n=== Sealed class : Result ===")
    val results: List<Result<String>> = listOf(
        Result.Success("Data loaded"),
        Result.Error("Network timeout", 408),
        Result.Loading,
        Result.Success("More data")
    )

    results.forEach { result ->
        println(handleResult(result))
    }

    println("\n=== Sealed class : UiState ===")
    val states = listOf(
        UiState.Idle,
        UiState.Loading,
        UiState.Success(listOf("Item1", "Item2", "Item3")),
        UiState.Error("Connection failed")
    )

    states.forEach { state ->
        println(renderUiState(state))
    }

    println("\n=== Sealed interface : PaymentEvent ===")
    val events: List<PaymentEvent> = listOf(
        PaymentEvent.PaymentInitiated(99.99, "EUR"),
        PaymentEvent.PaymentSucceeded("TXN-123", 99.99),
        PaymentEvent.PaymentFailed("Insufficient funds", true),
        PaymentEvent.PaymentCancelled("user@example.com", "User cancelled")
    )

    events.forEach { event ->
        processPayment(event)
        println()
    }

    println("=== Command ===")
    val commands = listOf(
        Command.CreateUser("alice", "alice@example.com"),
        Command.UpdateUser(1, "newemail@example.com"),
        Command.DeleteUser(2)
    )

    commands.forEach { cmd ->
        println(executeCommand(cmd))
    }

    println("\n=== Expression evaluation ===")
    // (2 + 3) * 5
    val expr = Expression.Multiply(
        Expression.Add(Expression.Const(2), Expression.Const(3)),
        Expression.Const(5)
    )
    println("Result: ${evaluate(expr)}")

    println("\n=== Enum vs Sealed ===")
    println("Enum : ensemble fermé simple (NEW, PAID, SHIPPED)")
    println("Sealed : variantes avec données différentes")
    println("Enum : quand tous les cas sont similaires")
    println("Sealed : quand chaque cas a sa structure propre")

    println("\n=== Résumé ===")
    println("1. Enum : valeurs constantes, propriétés communes")
    println("2. Sealed : variantes avec données différentes")
    println("3. When exhaustif : compilateur vérifie tous les cas")
    println("4. Sealed = modélisation sûre d'états/événements")
    println("5. Ajout de cas : erreurs de compilation (refactoring sûr)")
}
