# Exemples - Introduction à Kotlin

Ce dossier contient des exemples pratiques correspondant au slide **01-introduction.marp.md**.

## Liste des exemples

### Concepts de base

1. **[01-HelloWorld.kt](01-HelloWorld.kt)**
   - Point d'entrée `main()`
   - Fonctions top-level
   - Arguments de ligne de commande

2. **[02-ValVsVar.kt](02-ValVsVar.kt)**
   - Immutabilité avec `val`
   - Mutabilité avec `var`
   - Bonnes pratiques

3. **[03-NullSafety.kt](03-NullSafety.kt)**
   - Types nullable (`?`)
   - Safe call (`?.`)
   - Elvis operator (`?:`)
   - `let` pour gérer les nulls
   - Pourquoi éviter `!!`

### Style Kotlin

4. **[04-Expressions.kt](04-Expressions.kt)**
   - `if` comme expression
   - `when` (équivalent puissant de `switch`)
   - `try-catch` comme expression
   - Fonctions à expression unique

5. **[05-DataClass.kt](05-DataClass.kt)**
   - Élimination du boilerplate
   - `equals`, `hashCode`, `toString` automatiques
   - `copy` pour copie avec modification
   - Destructuration

6. **[06-Collections.kt](06-Collections.kt)**
   - Collections immutables vs mutables
   - API fonctionnelle (`filter`, `map`, `reduce`)
   - Opérations courantes
   - Chaînage d'opérations

### Fonctionnalités avancées

7. **[07-ExtensionFunctions.kt](07-ExtensionFunctions.kt)**
   - Enrichir des types existants
   - Extensions sur types standards
   - Extensions génériques
   - Extension properties

8. **[08-ScopeFunctions.kt](08-ScopeFunctions.kt)**
   - `let` : transformation et nullable
   - `apply` : configuration objet
   - `also` : effets de bord
   - `run` / `with` : exécution dans contexte
   - Comparaison et recommandations

9. **[09-SealedClasses.kt](09-SealedClasses.kt)**
   - Modélisation d'états (ADT)
   - Pattern matching exhaustif
   - Gestion d'erreurs type-safe
   - États d'UI

10. **[10-SmartCasts.kt](10-SmartCasts.kt)**
    - Cast automatique après vérification
    - Smart cast avec nullable
    - Smart cast dans `when`
    - `filterIsInstance` pour collections

## Comment exécuter les exemples

### Option 1 : IntelliJ IDEA (recommandé)

1. Ouvrir le projet dans IntelliJ
2. Naviguer vers le fichier exemple
3. Cliquer sur le bouton ▶️ vert à côté de `fun main()`

### Option 2 : Ligne de commande

Si vous avez Kotlin installé :

```bash
# Compiler et exécuter
kotlinc 01-HelloWorld.kt -include-runtime -d hello.jar
java -jar hello.jar

# Ou directement avec kotlin
kotlin 01-HelloWorld.kt
```

### Option 3 : Kotlin Scratch

1. Dans IntelliJ : File → New → Scratch File → Kotlin
2. Copier le contenu d'un exemple
3. Exécuter avec Ctrl+Shift+F10 (ou Cmd+Shift+R sur Mac)

## Ordre d'apprentissage recommandé

Pour une progression logique, suivez cet ordre :

1. **Bases essentielles** : 01, 02, 03
2. **Style Kotlin** : 04, 05, 06
3. **Fonctionnalités avancées** : 07, 08, 09, 10

## Correspondance avec le slide

| Slide | Exemples |
|-------|----------|
| Pourquoi Kotlin ? | 02, 03, 04, 05 |
| JVM - notions | 01 (bytecode implicite) |
| Structure Kotlin | 01 (main, packages) |
| Conventions | 02, 03, 04 (val, null-safety, expressions) |
| Data classes | 05 |
| Collections | 06 |
| Extension functions | 07 |
| Scope functions | 08 |
| Sealed classes | 09 |
| Smart casts | 10 |

## Points clés à retenir

### Null-safety
- Types non-nullable par défaut
- `?` pour nullable
- `?.`, `?:`, `let` pour gérer les nulls
- Éviter `!!`

### Immutabilité
- Préférer `val` à `var`
- Collections immutables par défaut

### Expressivité
- `if`, `when`, `try` sont des expressions
- API de collections riche et fonctionnelle
- Data classes éliminent le boilerplate

### Extensibilité
- Extension functions sans héritage
- Scope functions pour du code plus fluide
- Sealed classes pour modélisation type-safe

## Exercices suggérés

1. Modifier `03-NullSafety.kt` pour ajouter votre propre fonction de validation
2. Dans `06-Collections.kt`, ajouter des opérations sur vos propres données
3. Créer des extensions personnalisées dans `07-ExtensionFunctions.kt`
4. Modéliser un cas métier avec sealed classes dans `09-SealedClasses.kt`

## Ressources complémentaires

- [Kotlin Official Docs](https://kotlinlang.org/docs/home.html)
- [Kotlin Koans](https://play.kotlinlang.org/koans)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
