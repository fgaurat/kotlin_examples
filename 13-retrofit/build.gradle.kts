// Projet Kotlin/JVM pur (sans Android) : client REST avec Retrofit.
// Lancer : ./gradlew run                                   (JSONPlaceholder en ligne)
//          ./gradlew run --args="http://localhost:3000/"   (JSON Server en local)

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)   // indispensable pour @Serializable
    application                                 // ajoute la tâche « run »
}

dependencies {
    // Chaque ligne fait référence au catalogue gradle/libs.versions.toml.
    // Forme directe équivalente, sans catalogue (voir README) :
    //   implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)   // converter JSON ↔ DTO
    implementation(libs.okhttp.logging)                   // journal des requêtes
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)          // runBlocking, async

    testImplementation(kotlin("test"))                    // JUnit 5 via kotlin-test
    testImplementation(libs.okhttp.mockwebserver)         // faux serveur HTTP pour les tests
    testImplementation(libs.kotlinx.coroutines.test)
}

application {
    mainClass.set("retrofit.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
