// ==============================================================================
// FICHE FORMATEUR — Fonctions de portée (let, apply, also, run, with)
// Objectif : savoir lire ces 5 fonctions (omniprésentes dans le code Android) et choisir la bonne.
// À présenter (dans l'ordre) :
//   1. Les 2 critères : référence à l'objet (it / this) et valeur retournée (objet / résultat lambda)
//   2. let : nullable + transformation
//   3. apply : configuration d'objet (retourne l'objet)
//   4. also : effet de bord (log) sans casser une chaîne
//   5. run / with : calcul avec receveur ; puis mise en garde contre les abus
// Lien Android : `Intent(context, DetailActivity::class.java).apply { putExtra("id", id) }` (J5) ;
//   `NotificationCompat.Builder(...).apply { ... }` (TP12) ; `Retrofit.Builder()...build()` ;
//   `savedInstanceState?.let { ... }` ; `.also { Log.d(TAG, "...") }` pour déboguer.
// Pièges / questions fréquentes : imbriquer des let (it ambigu) ; utiliser let là où un simple
//   if suffit ; confondre apply (retourne l'objet) et run (retourne le résultat).
// Si peu de temps : tableau des 2 critères + let et apply ; survoler run/with.
// ==============================================================================
/**
 * Exemple 8 : Scope Functions
 *
 * Démonstration de let, apply, also, run, with
 * Utiles mais à utiliser avec modération
 */

// [ATTENTION FORMATEUR] data class avec des `var` : pratique pour la démo d'apply, mais en Android
// (UiState, modèles) on préfère des `val` + copy() ; le préciser pour ne pas contredire le fichier 05.
data class Person(
    var name: String,
    var age: Int,
    var email: String? = null
)

fun main() {
    // ===== let =====
    // Contexte : transformer/chaîner, gérer un nullable
    // Référence : it
    // Retourne : résultat du lambda

    val name: String? = "Alice"

    // Sans let (verbeux)
    if (name != null) {
        println("Nom en majuscules: ${name.uppercase()}")
    }

    // [À DIRE] Ici `name` est une val locale : le if bénéficie du smart cast, il est donc tout aussi bien.
    //   let devient vraiment utile sur une PROPRIÉTÉ var nullable (pas de smart cast possible).
    // Avec let (concis)
    name?.let {
        println("Nom en majuscules: ${it.uppercase()}")
    }

    // Transformation
    val length = name?.let { it.length } ?: 0
    println("Longueur: $length")

    // ===== apply =====
    // Contexte : configurer un objet (builder-style)
    // Référence : this
    // Retourne : l'objet lui-même

    val person1 = Person("Bob", 30).apply {
        email = "bob@example.com" // 'this' implicite
    }
    println("Person avec apply: $person1")

    // [ANDROID] Même geste : `Intent(this, DetailActivity::class.java).apply { putExtra("id", 42) }`.
    //   Ne pas confondre avec `Modifier.padding().fillMaxWidth()` qui est un chaînage, pas un apply.
    // Cas d'usage typique : configuration d'objets
    val stringBuilder = StringBuilder().apply {
        append("Hello")
        append(" ")
        append("Kotlin")
    }
    println("StringBuilder: ${stringBuilder.toString()}")

    // ===== also =====
    // Contexte : effets de bord (logs, validation) sans casser le flux
    // Référence : it
    // Retourne : l'objet lui-même

    val person2 = Person("Charlie", 25)
        .also { println("Création de: $it") }
        .also { it.email = "charlie@example.com" }
        .also { println("Après config: $it") }

    // [ANDROID] Sur Android : `.also { Log.d("TodoRepo", "reçu ${it.size} todos") }` (Logcat), pas println.
    //   Pour un Flow, l'équivalent est `.onEach { Log.d(...) }`.
    // Chaînage avec also pour debug
    val numbers = listOf(1, 2, 3, 4, 5)
        .also { println("Liste initiale: $it") }
        .filter { it % 2 == 0 }
        .also { println("Après filter: $it") }
        .map { it * 2 }
        .also { println("Après map: $it") }

    // ===== run =====
    // Contexte : exécuter un bloc avec un receiver
    // Référence : this
    // Retourne : résultat du lambda

    val greeting = "Kotlin".run {
        val exclamation = "!"
        uppercase() + exclamation // 'this' implicite
    }
    println("Greeting: $greeting")

    // Utile pour limiter la portée de variables temporaires
    val result = run {
        val a = 10
        val b = 20
        a + b
    }
    println("Résultat: $result")

    // [SAUTER] run sans receveur : rare en pratique, à mentionner seulement.
    // [ANDROID] with se voit en J5 avec le ViewBinding : `with(binding) { titleText.text = todo.title }`.
    // ===== with =====
    // Contexte : appeler plusieurs méthodes sur le même objet
    // Référence : this
    // Retourne : résultat du lambda
    // Note : n'est pas une extension (fonction standalone)

    val person3 = Person("Diana", 28, "diana@example.com")
    val description = with(person3) {
        "Nom: $name, Age: $age, Email: $email"
    }
    println(description)

    // ===== Comparaison et recommandations =====

    println("\n=== Comparaison ===")

    // let : nullable + transformation
    val maybeNumber: Int? = "123".toIntOrNull()
    val doubled = maybeNumber?.let { it * 2 } ?: 0
    println("Doublé: $doubled")

    // apply : configuration objet (retourne l'objet)
    val configured = Person("Test", 0).apply {
        age = 30
        email = "test@test.com"
    }

    // also : side-effect (retourne l'objet)
    val logged = Person("Logger", 0).also {
        println("Log: création de ${it.name}")
    }

    // run : calcul avec contexte (retourne le résultat)
    val calculated = configured.run {
        age * 2
    }

    // with : opérations multiples sans extension
    with(configured) {
        println("Avec with: $name a $age ans")
    }

    // [À DIRE] Règle simple à donner : if lisible > let ; apply pour configurer ; also pour loguer ; le reste avec parcimonie.
    // ⚠️ Attention : éviter les abus
    // Mauvais : trop de nesting
    /*
    person.let {
        it.name.let { name ->
            name.uppercase().let { upper ->
                println(upper)
            }
        }
    }
    */

    // Bon : clarté avant tout
    val upperName = person3.name.uppercase()
    println(upperName)
}
