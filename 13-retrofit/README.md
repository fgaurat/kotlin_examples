# Exemple — Retrofit en Kotlin pur

Client REST des tâches (`/todos`) **sans Android** : un petit projet Gradle Kotlin/JVM qui montre
comment ajouter les dépendances Retrofit, puis comment lire, créer, modifier et supprimer des tâches.
Il correspond au chapitre **04 « Utiliser des services Web »** ; le TP04 (`examples/tp04-client-todos`)
fait la même chose dans une application Android.

## Lancer

```bash
./gradlew run                                    # JSONPlaceholder, en ligne (écritures simulées)
./gradlew run --args="http://localhost:3000/"    # JSON Server en local (écritures réelles)
./gradlew run --args="http://localhost:3000/ --log"   # + journal des requêtes HTTP
./gradlew test                                   # 5 tests, sans réseau (MockWebServer)
```

Pour JSON Server (Node.js requis) : `npx json-server db.json` dans un dossier contenant un `db.json`
(par exemple celui de `examples/tp04-client-todos/json-server/`).

Sortie attendue :

```text
1. 9 tâche(s) à faire pour l'utilisateur 1, par exemple :
   ☐ #1 delectus aut autem
2. Tâche 4 : « et porro tempora », terminée = true
3. Créée avec l'id 201 : « Réviser Retrofit »        (JSON Server : id du type "okfaMsfe01Y")
4. Tâche 4 après PATCH : terminée = true
5. DELETE /todos/201 → HTTP 200
6. GET /todos/9999 → HTTP 404
7. En parallèle : « delectus aut autem » et « quis ut nam facilis et officia qui »
```

## Ajouter les dépendances : où et comment

Tout se passe dans **Gradle**. Ce projet utilise le **catalogue de versions**, comme les projets
Android Studio récents :

| Fichier | Rôle |
|---|---|
| [`gradle/libs.versions.toml`](gradle/libs.versions.toml) | **Quelles** bibliothèques et plugins, en **quelle** version |
| [`build.gradle.kts`](build.gradle.kts) | **Lesquels** ce projet utilise (`plugins { }`, `dependencies { }`) |
| [`settings.gradle.kts`](settings.gradle.kts) | **Où** les télécharger (Maven Central) |

Le catalogue n'est pas obligatoire. La forme directe, sans catalogue, est équivalente :

```kotlin
plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"     // même version que Kotlin
    application
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
```

| Dépendance | Rôle |
|---|---|
| Plugin `org.jetbrains.kotlin.plugin.serialization` | Génère le code des classes `@Serializable` (même version que Kotlin) |
| `retrofit` | Décrit l'API par une interface annotée, génère l'implémentation |
| `converter-kotlinx-serialization` | Convertit JSON ↔ DTO dans Retrofit |
| `logging-interceptor` (OkHttp) | Affiche les requêtes HTTP (`--log`) |
| `kotlinx-serialization-json` | Bibliothèque JSON de JetBrains |
| `kotlinx-coroutines-core` | `runBlocking`, `async` ; sur Android, les bibliothèques AndroidX l'apportent déjà |

**Où trouver ces coordonnées ?** Dans la documentation de chaque bibliothèque (README GitHub,
section « Setup » ou « Download »), ou sur Maven Central : <https://central.sonatype.com>.
Vérifier la compatibilité avec la version de Kotlin du projet : kotlinx-serialization 1.12 exige
Kotlin 2.4 et ne compile pas avec Kotlin 2.2.10 (« *compiled with an incompatible version of Kotlin* »).

Sources :
<https://developer.android.com/build/dependencies> ·
<https://docs.gradle.org/current/userguide/version_catalogs.html> ·
<https://github.com/Kotlin/kotlinx.serialization>

## Fichiers

1. **[`TodoApi.kt`](src/main/kotlin/retrofit/TodoApi.kt)**
   - DTO `@Serializable` : `TodoDto`, `NouveauTodoDto` (POST, sans id), `MajTodoDto` (PATCH)
   - `IdSerializer` : accepte un `id` numérique (JSONPlaceholder) ou en chaîne (JSON Server 1.x)
   - Interface `TodoApi` : `@GET`, `@POST`, `@PATCH`, `@DELETE`, `@Path`, `@Query`, `@Body`
   - `creerTodoApi()` : `Json { ignoreUnknownKeys; explicitNulls = false }`, OkHttp, Retrofit
2. **[`Main.kt`](src/main/kotlin/retrofit/Main.kt)**
   - GET filtré, GET d'une ressource, POST, PATCH, DELETE
   - Erreur 404 → `HttpException` ; serveur arrêté → `IOException`
   - Deux appels en parallèle avec `async`
3. **[`TodoApiTest.kt`](src/test/kotlin/retrofit/TodoApiTest.kt)**
   - `MockWebServer` remplace le serveur : URL envoyée, corps du PATCH, 404, id numérique ou chaîne

## Différences avec Android

| Ici (Kotlin pur) | Sur Android (TP04) |
|---|---|
| `runBlocking` dans `main()` | `viewModelScope.launch` ou `LaunchedEffect` (jamais `runBlocking`) |
| `http://localhost:3000/` | `http://10.0.2.2:3000/` depuis l'émulateur |
| Rien à déclarer | Permission `INTERNET` + `network_security_config.xml` pour le HTTP en clair |
| Plugin `kotlin("jvm")` | Kotlin intégré à l'Android Gradle Plugin 9 |
