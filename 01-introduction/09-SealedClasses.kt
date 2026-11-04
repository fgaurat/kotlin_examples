// ==============================================================================
// FICHE FORMATEUR — Sealed classes / interfaces
// Objectif : modéliser un ensemble FERMÉ d'états et laisser le compilateur vérifier le when.
// À présenter (dans l'ordre) :
//   1. sealed = toutes les sous-classes connues à la compilation (même module/package)
//   2. data class (avec données) vs data object (sans données)
//   3. when exhaustif SANS else → ajouter un cas = erreurs de compilation partout
//   4. UiState : LE patron utilisé dans tous les écrans de la formation
//   5. `out T` + Nothing : Error/Loading compatibles avec n'importe quel Result<T>
// Lien Android : TP07 `sealed interface TasksUiState { data object Loading; data class Success(...); data class Error(...) }`
//   exposé en `StateFlow<TasksUiState>` puis, dans le Composable :
//   `when (val s = uiState) { is TasksUiState.Loading -> CircularProgressIndicator() ... }` ;
//   événements UI (`sealed interface TaskEvent`) ; routes de navigation (J4).
// Pièges / questions fréquentes : ajouter un `else` tue l'intérêt de l'exhaustivité ;
//   sealed vs enum (enum = instances uniques sans données variables).
// Si peu de temps : Exemple 1 (Result) et Exemple 2 (UiState) ; sauter Operation et PaymentEvent.
// ==============================================================================
/**
 * Exemple 9 : Sealed Classes & Interfaces
 *
 * Modélisation d'états et d'ADT (Algebraic Data Types)
 * Pattern matching exhaustif avec when
 */

// Une interface sealed est une interface dont toutes les implémentations sont connues à la compilation, 
// ce qui permet au compilateur de vérifier l'exhaustivité des when.

// [ATTENTION FORMATEUR] Ce `Result` maison masque `kotlin.Result` de la stdlib : en TP04 le repository
// renvoie le `kotlin.Result` (runCatching / getOrElse / fold). Le dire pour éviter la confusion,
// et conseiller un autre nom en vrai projet (ex. `NetworkResult`).
// [À DIRE] Mettre Loading dans un Result est discutable : le chargement est un état d'ÉCRAN (UiState), pas un résultat.
// Sealed interface pour un résultat d'opération
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Error(val message: String, val code: Int? = null) : Result<Nothing>
    data object Loading : Result<Nothing>
}

// [ANDROID] Côté ViewModel : `private val _uiState = MutableStateFlow<UiState>(UiState.Idle)` ;
//   côté écran : `val uiState by viewModel.uiState.collectAsStateWithLifecycle()` (TP07).
// [À DIRE] Alternative fréquente : une seule `data class UiState(isLoading, items, error)` + copy() ;
//   les deux approches existent, choisir selon que les états sont exclusifs ou cumulables.
// Sealed class pour des états d'écran
sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Success(val message: String) : UiState()
    data class Error(val error: String) : UiState()
}

// Sealed class pour modéliser des opérations
sealed class Operation {
    data class Add(val value: Int) : Operation()
    data class Subtract(val value: Int) : Operation()
    data class Multiply(val value: Int) : Operation()
    data class Divide(val value: Int) : Operation()
}

// Sealed interface pour des événements
sealed interface PaymentEvent {
    data class PaymentReceived(val amount: Double, val currency: String) : PaymentEvent
    data class PaymentFailed(val reason: String) : PaymentEvent
    data class PaymentCancelled(val userId: String) : PaymentEvent
}

fun main() {
    // ===== Exemple 1 : Result (gestion d'erreurs type-safe) =====

    fun fetchUser(id: Int): Result<String> {
        return when {
            id < 0 -> Result.Error("ID invalide", 400)
            id == 0 -> Result.Loading
            id == 1 -> Result.Success("Alice")
            else -> Result.Error("Utilisateur non trouvé", 404)
        }
    }

    // [À DIRE] Démo live : ajouter un cas dans la sealed interface et montrer l'erreur de compilation ici.
    // [À DIRE] `is` est inutile devant un data object (`Result.Loading ->` suffit), mais reste correct.
    // Pattern matching exhaustif
    fun handleResult(result: Result<String>) {
        when (result) {
            is Result.Success -> println("✅ Succès: ${result.data}")
            is Result.Error -> println("❌ Erreur (${result.code}): ${result.message}")
            is Result.Loading -> println("⏳ Chargement...")
        }
        // Le compilateur garantit que tous les cas sont couverts
    }

    println("=== Test Result ===")
    handleResult(fetchUser(1))
    handleResult(fetchUser(-1))
    handleResult(fetchUser(0))
    handleResult(fetchUser(99))

    // ===== Exemple 2 : UiState =====
    // [ANDROID] Ce renderUi est l'ancêtre exact d'un @Composable :
    //   `when (state) { is UiState.Loading -> CircularProgressIndicator(); is UiState.Error -> Text(state.error) ... }`.

    fun renderUi(state: UiState): String = when (state) {
        is UiState.Idle -> "En attente d'action"
        is UiState.Loading -> "Chargement en cours..."
        is UiState.Success -> "✅ ${state.message}"
        is UiState.Error -> "❌ ${state.error}"
    }

    println("\n=== Test UiState ===")
    println(renderUi(UiState.Idle))
    println(renderUi(UiState.Loading))
    println(renderUi(UiState.Success("Données chargées")))
    println(renderUi(UiState.Error("Connexion échouée")))

    // ===== Exemple 3 : Operation (calculatrice) =====
    // [SAUTER] Exemples 3 et 4 : mêmes principes ; à garder pour un exercice si le temps le permet.
    // [PIÈGE] La division par zéro est ignorée silencieusement (renvoie acc) : en vrai, on signalerait l'erreur.

    fun calculate(initial: Int, operations: List<Operation>): Int {
        return operations.fold(initial) { acc, op ->
            when (op) {
                is Operation.Add -> acc + op.value
                is Operation.Subtract -> acc - op.value
                is Operation.Multiply -> acc * op.value
                is Operation.Divide -> if (op.value != 0) acc / op.value else acc
            }
        }
    }

    println("\n=== Test Operations ===")
    val ops = listOf(
        Operation.Add(10),
        Operation.Multiply(2),
        Operation.Subtract(5)
    )
    val result = calculate(0, ops)
    println("Résultat: $result") // 0 + 10 = 10, 10 * 2 = 20, 20 - 5 = 15

    // ===== Exemple 4 : PaymentEvent =====

    fun processPayment(event: PaymentEvent) {
        when (event) {
            is PaymentEvent.PaymentReceived -> {
                println("Paiement reçu: ${event.amount} ${event.currency}")
                // Logique de traitement...
            }
            is PaymentEvent.PaymentFailed -> {
                println("Échec: ${event.reason}")
                // Logique de retry...
            }
            is PaymentEvent.PaymentCancelled -> {
                println("Annulé par: ${event.userId}")
                // Logique de remboursement...
            }
        }
    }

    println("\n=== Test Paiement ===")
    processPayment(PaymentEvent.PaymentReceived(99.99, "EUR"))
    processPayment(PaymentEvent.PaymentFailed("Carte expirée"))
    processPayment(PaymentEvent.PaymentCancelled("user123"))

    // ===== Avantages des Sealed Classes =====

    println("\n=== Avantages ===")
    println("1. Pattern matching exhaustif (compilateur vérifie tous les cas)")
    println("2. Modélisation claire d'états mutuellement exclusifs")
    println("3. Type-safety (pas de cast, pas de null check)")
    println("4. Refactoring sûr (ajout d'un cas = erreurs de compilation)")

    // [À DIRE] filterIsInstance<T>() filtre ET type la liste (List<Result.Error>) : lien avec le fichier 10.
    // Exemple : utilisation avec collections
    val results = listOf(
        Result.Success("User1"),
        Result.Error("Timeout", 408),
        Result.Success("User2"),
        Result.Loading
    )

    val successCount = results.count { it is Result.Success }
    val errors = results.filterIsInstance<Result.Error>()

    println("\nSur ${results.size} résultats:")
    println("- Succès: $successCount")
    println("- Erreurs: ${errors.size}")
}
