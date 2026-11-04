// ==============================================================================
// FICHE FORMATEUR — Flow et StateFlow
// Objectif : savoir produire et transformer un Flow, et exposer un état observable avec StateFlow,
//   c'est-à-dire tout ce qu'un ViewModel utilise au J3 (chapitre 07 MVVM, TP07).
// À présenter (dans l'ordre) :
//   1. Flow froid : flow { }, rien ne se passe sans collect, rejoué pour chaque collecteur (demo01 à 03)
//   2. Opérateurs : map / filter / take / onEach, opérateurs terminaux (demo04, demo05)
//   3. Opérateurs « UI » : debounce, combine, flatMapLatest (demo06 à 08)
//   4. Erreurs et contexte : catch, onCompletion, flowOn (demo09, demo10)
//   5. StateFlow : toujours une valeur, .value, conflation, dédoublonnage par equals (demo11 à 13)
//   6. update { } atomique ; MutableStateFlow privé / StateFlow public (demo14, demo15)
//   7. SharedFlow pour les événements ; stateIn pour rendre chaud un flow froid (demo16, demo17)
//   8. Synthèse : un « ViewModel » en Kotlin pur, état d'écran sealed + recherche (demo18)
// Lien Android : _uiState / uiState dans tous les ViewModels (J3) ; collectAsStateWithLifecycle()
//   côté Compose ; Room renvoie un Flow<List<…>> (J4) ; stateIn(viewModelScope, WhileSubscribed(5_000), …)
// Pièges / questions fréquentes : « mon flow ne fait rien » → personne ne le collecte ;
//   MutableList dans un StateFlow → aucune émission ; value++ depuis plusieurs threads → valeurs perdues ;
//   collect sur un StateFlow ne se termine jamais → il faut annuler la coroutine ;
//   un événement (Snackbar, navigation) dans un StateFlow → rejoué à la rotation.
// Si peu de temps : demo01, 02, 04, 06, 07, 11, 13, 14, 15, 17, 18. Sauter 03, 05, 08, 09, 10, 12, 16.
//   Exécution complète du main ≈ 10 s.
// Dépendance : org.jetbrains.kotlinx:kotlinx-coroutines-core (comme les fichiers 01 à 06).
// ==============================================================================

// debounce est encore marqué « preview », flatMapLatest « expérimental » : stables en pratique,
// utilisés partout sur Android. L'annotation évite d'écrire @OptIn sur chaque fonction.
@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

/**
 * 07-Flow-StateFlow.kt
 *
 * Flux de données asynchrones avec Kotlin Flow
 * - Flow froid : flow { }, collect, flowOf, asFlow
 * - Opérateurs intermédiaires et terminaux
 * - debounce, combine, flatMapLatest
 * - catch, onCompletion, flowOn
 * - StateFlow et MutableStateFlow, update { }
 * - SharedFlow, stateIn, SharingStarted
 * - Un ViewModel en Kotlin pur
 */

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.measureTimeMillis

// Le modèle utilisé dans tout le fichier : une liste de tâches, comme dans les TPs Android
data class Tache(val id: Int, val titre: String, val faite: Boolean = false)

// =============================================================================
// 1. FLOW FROID
// =============================================================================

/**
 * Un Flow est une suite de valeurs produites dans le temps.
 * Une fonction suspend renvoie UNE valeur, un Flow en renvoie PLUSIEURS.
 */
// [À DIRE] flow { } décrit COMMENT produire les valeurs ; emit() envoie une valeur au collecteur.
//   Le bloc peut appeler des fonctions suspend (delay, appel réseau…).
// [ANDROID] Room renvoie Flow<List<Tache>> : une nouvelle liste à chaque modification de la table (J4).
fun compteARebours(depart: Int): Flow<Int> = flow {
    println("  [flow] démarrage du compte à rebours")
    for (i in depart downTo 1) {
        delay(200)
        emit(i)
    }
}

// [À DIRE] Appeler compteARebours() ne lance RIEN : on obtient juste une description du flux.
//   C'est collect { } (une fonction suspend) qui déclenche la production des valeurs.
// [PIÈGE] « Mon flow ne fait rien » : personne ne l'a collecté.
fun demo01_flowBuilder() = runBlocking {
    println("\n=== Demo 01: flow { } et collect ===")

    val flux = compteARebours(3)
    println("Flow créé, mais rien n'est encore produit (pas de « démarrage » affiché)")

    flux.collect { valeur -> println("  reçu : $valeur") }
    println("collect rend la main quand le flow est terminé")
}

/**
 * Un Flow froid est rejoué depuis le début pour chaque collecteur.
 */
// [À DIRE] Froid = chaque collecteur a SA propre exécution du bloc flow { }, depuis le début.
//   Deux collecteurs → deux comptes à rebours, deux « démarrage ».
// [ANDROID] Un flow froid qui fait un appel réseau, collecté par deux écrans, fait deux appels :
//   d'où stateIn (demo17) pour partager une seule exécution.

// launch est utilisé pour lancer deux collecteurs en parallèle, mais on pourrait aussi les lancer séquentiellement.
//plus généralement le bloc lanch { } est utilisé pour lancer une coroutine dans un scope (ici runBlocking) et exécuter du code de manière concurrente.
fun demo02_coldReplayed() = runBlocking {
    println("\n=== Demo 02: Flow froid, rejoué pour chaque collecteur ===")

    val flux = compteARebours(3)

    launch { flux.collect { println("  collecteur A : $it") } }
    delay(300)   // le collecteur B arrive en retard…
    launch { flux.collect { println("  collecteur B : $it") } }
    // … mais il reçoit quand même 3, 2, 1 : le flow repart du début pour lui
}

/**
 * Autres façons de créer un Flow.
 */
fun demo03_flowOfAsFlow() = runBlocking {
    println("\n=== Demo 03: flowOf, asFlow ===")

    flowOf("Acheter du pain", "Réviser Kotlin").collect { println("  flowOf : $it") }

    listOf(1, 2, 3).asFlow().collect { println("  asFlow : $it") }

    // [À DIRE] Utile surtout dans les tests et les exemples : un faux repository qui renvoie flowOf(…).
}

// =============================================================================
// 2. OPÉRATEURS
// =============================================================================

fun observerTaches(): Flow<Tache> = flow {
    val taches = listOf(
        Tache(1, "Acheter du pain", faite = true),
        Tache(2, "Réviser Kotlin"),
        Tache(3, "Ranger le bureau"),
        Tache(4, "Appeler le plombier"),
        Tache(5, "Préparer le TP"),
    )
    for (tache in taches) {
        delay(100)
        emit(tache)
    }
}

/**
 * Opérateurs intermédiaires : ils renvoient un nouveau Flow, sans rien exécuter.
 */
// [À DIRE] Mêmes noms que sur les listes (map, filter, take…), mais appliqués valeur par valeur,
//   au fil de l'eau. On construit une chaîne ; seul collect la met en route.
// [À DIRE] onEach : un effet de bord (log) sans modifier la valeur.
fun demo04_intermediateOperators() = runBlocking {
    println("\n=== Demo 04: map, filter, take, onEach ===")

    observerTaches()
        .onEach { println("  [source] ${it.titre}") }
        .filter { !it.faite }                       // garde les tâches à faire
        .map { it.titre.uppercase() }               // transforme Tache → String
        .take(2)                                    // s'arrête après 2 valeurs (et arrête la source)
        .collect { println("  → $it") }

    // [PIÈGE] take(2) annule la source : « Appeler le plombier » n'est jamais produit.
}

/**
 * Opérateurs terminaux : ils sont suspend et déclenchent la collecte.
 */
fun demo05_terminalOperators() = runBlocking {
    println("\n=== Demo 05: Opérateurs terminaux ===")

    val liste: List<Tache> = observerTaches().toList()
    println("  toList : ${liste.size} tâches")

    val premiere: Tache = observerTaches().first { !it.faite }
    println("  first { !faite } : ${premiere.titre}")

    val restantes: Int = observerTaches().fold(0) { acc, tache -> if (tache.faite) acc else acc + 1 }
    println("  fold : $restantes tâches restantes")

    // [À DIRE] first() s'arrête dès la première valeur : très utilisé dans les tests
    //   (« lire l'état actuel »), et pour lire une seule fois un Flow (DataStore, J4).
}

// =============================================================================
// 3. OPÉRATEURS « UI » : DEBOUNCE, COMBINE, FLATMAPLATEST
// =============================================================================

/**
 * Simule un utilisateur qui tape « kotlin » dans un champ de recherche.
 */
fun saisieUtilisateur(): Flow<String> = flow {
    for (texte in listOf("k", "ko", "kot", "kotl", "kotli", "kotlin")) {
        emit(texte)
        delay(80)        // l'utilisateur tape vite : une lettre toutes les 80 ms
    }
    delay(500)           // pause
    emit("kotlin co")    // reprend la saisie
}

/**
 * debounce : attend que la valeur soit stable pendant un délai avant de la laisser passer.
 */
// [À DIRE] Sans debounce : 7 requêtes de recherche. Avec debounce(300) : 2 requêtes,
//   une seule par « pause » de l'utilisateur.
// [ANDROID] Recherche réactive dans le ViewModel : _recherche.debounce(300) (tp07-mvvm-sujets, sujet 6).
fun demo06_debounce() = runBlocking {
    println("\n=== Demo 06: debounce ===")

    saisieUtilisateur()
        .debounce(300)
        .collect { println("  recherche lancée pour « $it »") }
}

/**
 * combine : recalcule un résultat dès que l'UN des flows émet, avec la dernière valeur de chacun.
 */
// [À DIRE] Typique d'un écran : liste + filtre → liste affichée. Changer le filtre OU la liste
//   recalcule l'affichage. combine attend que chaque flow ait émis au moins une fois.
fun demo07_combine() = runBlocking {
    println("\n=== Demo 07: combine ===")

    val taches = flow {
        emit(listOf(Tache(1, "Pain", faite = true), Tache(2, "Kotlin")))
        delay(300)
        emit(listOf(Tache(1, "Pain", faite = true), Tache(2, "Kotlin"), Tache(3, "Bureau")))
    }
    val masquerFaites = flow {
        emit(false)
        delay(150)
        emit(true)
    }

    combine(taches, masquerFaites) { liste, masquer ->
        if (masquer) liste.filter { !it.faite } else liste
    }.collect { affichees -> println("  affichées : ${affichees.map { it.titre }}") }
}

/**
 * flatMapLatest : pour chaque valeur, lance un nouveau flow et ANNULE le précédent.
 */
// [À DIRE] Une recherche lente pour « ko » est annulée dès que l'utilisateur tape « kot » :
//   on n'affiche jamais un résultat périmé.
// [À DIRE] Variantes : flatMapConcat (attend la fin du précédent), flatMapMerge (tous en parallèle).
fun demo08_flatMapLatest() = runBlocking {
    println("\n=== Demo 08: flatMapLatest ===")

    fun rechercher(texte: String): Flow<String> = flow {
        println("  [recherche] début « $texte »")
        delay(200)                    // requête lente
        emit("résultats pour « $texte »")
    }

    flowOf("k", "ko", "kot")
        .onEach { delay(100) }        // une lettre toutes les 100 ms : plus rapide que la recherche
        .flatMapLatest { rechercher(it) }
        .collect { println("  → $it") }
    // Seul « kot » va au bout : « k » et « ko » sont annulées
}

// =============================================================================
// 4. ERREURS ET CONTEXTE : CATCH, ONCOMPLETION, FLOWON
// =============================================================================

/**
 * catch intercepte les exceptions EN AMONT ; onCompletion s'exécute à la fin, dans tous les cas.
 */
// [À DIRE] catch peut émettre une valeur de remplacement : ici un état d'erreur pour l'écran.
// [PIÈGE] catch ne protège que ce qui est AU-DESSUS de lui : une exception dans collect { }
//   n'est pas interceptée.
fun demo09_catchOnCompletion() = runBlocking {
    println("\n=== Demo 09: catch et onCompletion ===")

    flow {
        emit("Acheter du pain")
        emit("Réviser Kotlin")
        throw IllegalStateException("serveur injoignable")
    }
        .catch { e -> emit("⚠ erreur : ${e.message}") }
        .onCompletion { println("  [onCompletion] flow terminé") }
        .collect { println("  $it") }
}

/**
 * flowOn change le dispatcher de la partie EN AMONT du flow.
 */
// [À DIRE] Le producteur tourne sur Dispatchers.IO, le collecteur reste sur son thread.
//   On n'utilise PAS withContext dans flow { } : c'est interdit (IllegalStateException).
// [ANDROID] Le repository fait .flowOn(Dispatchers.IO) ; le ViewModel collecte sur le thread principal.
fun demo10_flowOn() = runBlocking {
    println("\n=== Demo 10: flowOn ===")

    flow {
        println("  [producteur] thread : ${Thread.currentThread().name}")
        emit("données lues sur le disque")
    }
        .flowOn(Dispatchers.IO)
        .collect { println("  [collecteur] thread : ${Thread.currentThread().name} → $it") }
}

// =============================================================================
// 5. STATEFLOW
// =============================================================================

/**
 * StateFlow : un flow CHAUD qui contient toujours UNE valeur, l'état courant.
 */
// [À DIRE] Trois différences avec un Flow froid :
//   1. il a toujours une valeur (valeur initiale obligatoire), lisible avec .value, sans collecter ;
//   2. il existe indépendamment des collecteurs (chaud) ;
//   3. un nouveau collecteur reçoit immédiatement la valeur courante, puis les changements.
// [PIÈGE] collect sur un StateFlow ne se termine JAMAIS : on annule la coroutine (ici job.cancel()).
//   Sur Android, c'est le cycle de vie qui s'en charge (collectAsStateWithLifecycle).
fun demo11_stateFlowBasics() = runBlocking {
    println("\n=== Demo 11: StateFlow, valeur courante ===")

    val compteur = MutableStateFlow(0)
    println("  .value sans collecter : ${compteur.value}")

    val job = launch {
        compteur.collect { println("  collecteur : $it") }   // reçoit 0 tout de suite
    }
    delay(100)
    compteur.value = 1
    delay(100)
    compteur.value = 2
    delay(100)

    job.cancel()
    println("  collecteur annulé ; valeur toujours lisible : ${compteur.value}")
}

/**
 * Conflation : un collecteur lent ne reçoit que la DERNIÈRE valeur, pas toutes les intermédiaires.
 */
// [À DIRE] Un StateFlow représente un ÉTAT, pas une suite d'événements : si l'état change
//   5 fois pendant que l'écran dessine, seul le dernier compte. C'est voulu.
// [PIÈGE] Ne pas compter sur un StateFlow pour recevoir chaque valeur (événements → demo16).
fun demo12_conflation() = runBlocking {
    println("\n=== Demo 12: Conflation ===")

    val etat = MutableStateFlow(0)
    val job = launch {
        etat.collect {
            println("  collecteur lent reçoit : $it")
            delay(200)                         // traitement lent
        }
    }
    delay(10)
    for (i in 1..5) {
        etat.value = i
        delay(10)
    }
    delay(300)
    job.cancel()
    // Affiche 0 puis 5 : les valeurs 1 à 4 ont été remplacées avant d'être lues
}

/**
 * StateFlow n'émet que si la nouvelle valeur est DIFFÉRENTE (equals) de l'actuelle.
 */
// [À DIRE] Assigner une valeur égale ne déclenche rien : pas de recomposition inutile.
//   Les data class comparent leur contenu : c'est pour ça qu'on les utilise pour l'état.
// [PIÈGE] Une MutableList modifiée en place puis réassignée : c'est le MÊME objet,
//   donc égal à lui-même → aucune émission, l'écran ne bouge pas.
//   Toujours une List immuable, et une nouvelle liste (liste + element, copy()).
fun demo13_distinctEquals() = runBlocking {
    println("\n=== Demo 13: Dédoublonnage par equals ===")

    val etat = MutableStateFlow(Tache(1, "Acheter du pain"))
    val job = launch { etat.collect { println("  émis : $it") } }
    delay(50)

    etat.value = Tache(1, "Acheter du pain")              // égale (data class) → rien
    delay(50)
    etat.value = etat.value.copy(faite = true)            // différente → émise
    delay(50)

    println("  --- piège de la liste mutable ---")
    val listeMutable = mutableListOf("Pain")
    val etatListe = MutableStateFlow<List<String>>(listeMutable)
    val job2 = launch { etatListe.collect { println("  liste émise : $it") } }
    delay(50)

    listeMutable.add("Kotlin")
    etatListe.value = listeMutable                        // même objet → AUCUNE émission
    delay(50)
    println("  ❌ contenu réel ${etatListe.value}, mais rien n'a été émis")

    etatListe.value = etatListe.value + "Bureau"          // nouvelle liste → émise
    delay(50)

    job.cancel()
    job2.cancel()
}

// =============================================================================
// 6. MODIFIER ET EXPOSER UN STATEFLOW
// =============================================================================

/**
 * update { } : modification atomique, sûre même depuis plusieurs threads.
 */
// [À DIRE] value++ c'est « lire, ajouter 1, écrire » : deux threads peuvent lire la même valeur
//   et une incrémentation est perdue. update { } recommence si la valeur a changé entre-temps.
// [ANDROID] Dans un ViewModel : _uiState.update { it.copy(chargement = true) }.
//   Toujours update dès que la nouvelle valeur dépend de l'ancienne.
fun demo14_update() = runBlocking {
    println("\n=== Demo 14: value++ contre update { } ===")

    val avecValue = MutableStateFlow(0)
    val avecUpdate = MutableStateFlow(0)

    // 1 000 coroutines × 100 incrémentations, réparties sur plusieurs threads
    withContext(Dispatchers.Default) {
        repeat(1_000) {
            launch {
                repeat(100) {
                    avecValue.value++                  // ❌ non atomique
                    avecUpdate.update { it + 1 }       // ✅ atomique
                }
            }
        }
    }
    println("  attendu      : 100000")
    println("  value++      : ${avecValue.value}   (souvent moins : incrémentations perdues)")
    println("  update { }   : ${avecUpdate.value}")
}

/**
 * MutableStateFlow privé, StateFlow public : seul le propriétaire peut modifier l'état.
 */
// [À DIRE] C'est LE motif de tous les ViewModels : _etat (modifiable, privé) et etat (lecture seule).
//   L'écran lit l'état et appelle des fonctions (onAjouter…) : il ne modifie jamais l'état lui-même.
// [À DIRE] asStateFlow() empêche un appelant de faire « etat as MutableStateFlow ».
class ListeDeTaches {
    private val _taches = MutableStateFlow<List<Tache>>(emptyList())
    val taches: StateFlow<List<Tache>> = _taches.asStateFlow()

    // Valeur dérivée : calculée à partir de l'état, jamais stockée à part
    val nombreRestantes: Int get() = _taches.value.count { !it.faite }

    fun ajouter(titre: String) {
        _taches.update { liste -> liste + Tache(id = liste.size + 1, titre = titre) }
    }

    fun basculer(id: Int) {
        _taches.update { liste -> liste.map { if (it.id == id) it.copy(faite = !it.faite) else it } }
    }
}

fun demo15_readOnlyExposure() = runBlocking {
    println("\n=== Demo 15: _etat privé, etat public ===")

    val liste = ListeDeTaches()
    val job = launch {
        liste.taches.collect { taches -> println("  écran : ${taches.map { "${it.titre}${if (it.faite) " ✓" else ""}" }}") }
    }
    delay(50)

    liste.ajouter("Acheter du pain"); delay(50)
    liste.ajouter("Réviser Kotlin"); delay(50)
    liste.basculer(1); delay(50)
    println("  restantes : ${liste.nombreRestantes}")

    // liste.taches.value = emptyList()   // ❌ ne compile pas : StateFlow est en lecture seule
    job.cancel()
}

// =============================================================================
// 7. SHAREDFLOW ET STATEIN
// =============================================================================

/**
 * SharedFlow : un flow chaud SANS valeur courante, pour diffuser des événements.
 */
// [À DIRE] StateFlow = un état (il y en a toujours un, le dernier compte).
//   SharedFlow = des événements (« tâche supprimée », « naviguer vers… ») : chacun compte, aucun n'est rejoué.
// [PIÈGE] Avec replay = 0 (défaut), un événement émis sans collecteur est PERDU.
// [ANDROID] Un événement mis dans un StateFlow est rejoué après une rotation (Snackbar affichée 2 fois).
//   Aujourd'hui, Google conseille plutôt de garder l'événement dans l'état et de le « consommer ».
fun demo16_sharedFlowEvents() = runBlocking {
    println("\n=== Demo 16: SharedFlow pour les événements ===")

    val evenements = MutableSharedFlow<String>()

    evenements.emit("Tâche ajoutée")              // personne n'écoute → perdu
    println("  « Tâche ajoutée » émis sans collecteur")

    val job = launch { evenements.collect { println("  Snackbar : $it") } }
    delay(50)

    evenements.emit("Tâche supprimée")            // reçu
    evenements.emit("Tâche supprimée")            // reçu aussi : pas de dédoublonnage, contrairement à StateFlow
    delay(50)
    job.cancel()
}

/**
 * stateIn : transforme un flow froid en StateFlow partagé.
 */
// [À DIRE] Le flow froid (repository) n'est exécuté qu'UNE fois, quel que soit le nombre de collecteurs,
//   et on obtient un StateFlow avec une valeur initiale.
// [À DIRE] SharingStarted :
//   - Eagerly : démarre tout de suite, ne s'arrête jamais (jusqu'à la fin du scope) ;
//   - Lazily : démarre au premier collecteur, ne s'arrête jamais ;
//   - WhileSubscribed(t) : démarre au premier collecteur, s'arrête t ms après le départ du dernier.
// [ANDROID] stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), valeurInitiale) :
//   5 s couvrent une rotation (pas de redémarrage), mais l'appli en arrière-plan arrête le flux.
//   Ici 500 ms pour ne pas attendre.
fun demo17_stateIn() = runBlocking {
    println("\n=== Demo 17: stateIn et SharingStarted ===")

    val depuisLaBase: Flow<List<String>> = flow {
        println("  [base] démarrage de la requête")
        emit(listOf("Pain", "Kotlin"))
        delay(200)
        emit(listOf("Pain", "Kotlin", "Bureau"))
        awaitCancellation()                       // comme Room : le flow reste ouvert
    }.onCompletion { println("  [base] requête arrêtée") }

    // Le scope joue le rôle de viewModelScope ; on l'annule à la fin (comme onCleared)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val taches: StateFlow<List<String>> = depuisLaBase.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(500),
        initialValue = emptyList(),
    )
    println("  avant tout collecteur : ${taches.value}")

    val a = launch { taches.collect { println("  écran A : $it") } }
    val b = launch { taches.collect { println("  écran B : $it") } }
    delay(400)
    println("  → une seule requête pour deux écrans")

    a.cancel(); b.cancel()
    println("  plus de collecteur : arrêt dans 500 ms…")
    delay(700)

    scope.cancel()
}

// =============================================================================
// 8. SYNTHÈSE : UN VIEWMODEL EN KOTLIN PUR
// =============================================================================

// État d'écran : une sealed interface, un seul état à la fois
sealed interface TachesUiState {
    data object Chargement : TachesUiState
    data class Succes(val taches: List<Tache>, val recherche: String) : TachesUiState
    data class Erreur(val message: String) : TachesUiState
}

// Le repository expose un Flow froid (Room, Retrofit…)
class TacheRepository {
    fun observerTaches(): Flow<List<Tache>> = flow {
        delay(300)                                // chargement
        emit(listOf(Tache(1, "Acheter du pain"), Tache(2, "Réviser Kotlin"), Tache(3, "Kotlin Flow")))
    }
}

/**
 * Même code qu'un ViewModel Android, sans la classe ViewModel :
 * le scope reçu en paramètre remplace viewModelScope.
 */
// [À DIRE] Tout est là : MutableStateFlow privé pour l'entrée de l'utilisateur, combine + debounce,
//   catch pour l'erreur, stateIn pour exposer UN StateFlow<TachesUiState> à l'écran.
// [ANDROID] Côté Compose : val etat by viewModel.etat.collectAsStateWithLifecycle(), puis when (etat).
class TachesViewModelPur(scope: CoroutineScope, repository: TacheRepository) {

    private val _recherche = MutableStateFlow("")

    val etat: StateFlow<TachesUiState> =
        combine(repository.observerTaches(), _recherche.debounce(300)) { taches, recherche ->
            val filtrees = taches.filter { it.titre.contains(recherche, ignoreCase = true) }
            TachesUiState.Succes(filtrees, recherche) as TachesUiState
        }
            .catch { e -> emit(TachesUiState.Erreur(e.message ?: "erreur inconnue")) }
            .stateIn(scope, SharingStarted.Eagerly, TachesUiState.Chargement)

    // Événement venant de l'écran (onValueChange du TextField)
    fun onRechercheChange(texte: String) {
        _recherche.value = texte
    }
}

fun demo18_miniViewModel() = runBlocking {
    println("\n=== Demo 18: Un ViewModel en Kotlin pur ===")

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val viewModel = TachesViewModelPur(scope, TacheRepository())

    // « L'écran » : affiche chaque état
    val ecran = launch {
        viewModel.etat.collect { etat ->
            when (etat) {
                TachesUiState.Chargement -> println("  écran : ⏳ chargement…")
                is TachesUiState.Succes -> println("  écran : « ${etat.recherche} » → ${etat.taches.map { it.titre }}")
                is TachesUiState.Erreur -> println("  écran : ⚠ ${etat.message}")
            }
        }
    }
    delay(500)

    // L'utilisateur tape « kot » : grâce à debounce, un seul filtrage
    for (texte in listOf("k", "ko", "kot")) {
        viewModel.onRechercheChange(texte)
        delay(50)
    }
    delay(500)

    ecran.cancel()
    scope.cancel()          // équivalent de onCleared() : arrête tout ce que le ViewModel a lancé
}

// =============================================================================
// RÉSUMÉ
// =============================================================================

fun printSummary() {
    println("\n" + "=".repeat(80))
    println("RÉSUMÉ: Flow et StateFlow")
    println("=".repeat(80))

    println("""
    1. FLOW (FROID):
    - flow { emit(…) } décrit une suite de valeurs produites dans le temps
    - Rien n'est exécuté avant collect { } ; rejoué pour chaque collecteur
    - flowOf(…), liste.asFlow() pour créer rapidement un flow

    2. OPÉRATEURS:
    - Intermédiaires (renvoient un Flow) : map, filter, take, onEach
    - Terminaux (suspend) : collect, toList, first, fold
    - UI : debounce (saisie), combine (plusieurs sources), flatMapLatest (annule le précédent)
    - catch (erreurs en amont), onCompletion (fin), flowOn (dispatcher en amont)

    3. STATEFLOW (CHAUD):
    - Toujours une valeur : MutableStateFlow(valeurInitiale), lisible avec .value
    - Un nouveau collecteur reçoit la valeur courante, puis les changements
    - Conflation : un collecteur lent ne voit que la dernière valeur
    - N'émet que si la nouvelle valeur est différente (equals) → data class + List immuable
    - update { } pour modifier à partir de l'ancienne valeur (atomique)
    - private val _etat = MutableStateFlow(…) ; val etat = _etat.asStateFlow()
    - collect ne se termine jamais : annuler la coroutine (le cycle de vie sur Android)

    4. SHAREDFLOW ET STATEIN:
    - SharedFlow : événements, pas de valeur courante, pas de dédoublonnage
    - stateIn(scope, started, valeurInitiale) : flow froid → StateFlow partagé
    - Android : stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), …)

    5. CÔTÉ ANDROID:
    - ViewModel : expose un StateFlow<UiState>
    - Compose : val etat by viewModel.etat.collectAsStateWithLifecycle()
    """.trimIndent())
}

// =============================================================================
// MAIN
// =============================================================================

fun main() {
    println("=".repeat(80))
    println("Flow et StateFlow")
    println("=".repeat(80))

    val duree = measureTimeMillis {
        // 1. Flow froid
        demo01_flowBuilder()
        demo02_coldReplayed()
        demo03_flowOfAsFlow()

        // 2. Opérateurs
        demo04_intermediateOperators()
        demo05_terminalOperators()

        // 3. Opérateurs « UI »
        demo06_debounce()
        demo07_combine()
        demo08_flatMapLatest()

        // 4. Erreurs et contexte
        demo09_catchOnCompletion()
        demo10_flowOn()

        // 5. StateFlow
        demo11_stateFlowBasics()
        demo12_conflation()
        demo13_distinctEquals()

        // 6. Modifier et exposer un StateFlow
        demo14_update()
        demo15_readOnlyExposure()

        // 7. SharedFlow et stateIn
        demo16_sharedFlowEvents()
        demo17_stateIn()

        // 8. Synthèse
        demo18_miniViewModel()
    }

    // Résumé
    printSummary()

    println("\n" + "=".repeat(80))
    println("Fin des démonstrations (${duree / 1000.0} s)")
    println("=".repeat(80))
}
