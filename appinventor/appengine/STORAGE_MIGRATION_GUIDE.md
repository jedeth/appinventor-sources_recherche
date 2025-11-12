# Guide de Migration du Stockage - ObjectifyStorageIo.java

Ce guide explique comment modifier `ObjectifyStorageIo.java` pour utiliser la nouvelle couche d'abstraction de stockage cloud, permettant un déploiement sur une infrastructure indépendante de Google.

## Vue d'ensemble

**Fichier à modifier :** `src/com/google/appinventor/server/storage/ObjectifyStorageIo.java` (2848 lignes)

**Objectif :** Remplacer l'utilisation directe de `GcsService` par `CloudStorageService` pour permettre le basculement entre GCS et S3.

**Principe :** Les modifications sont **minimales** et **localisées** pour faciliter les futurs merges avec le dépôt MIT.

## Modifications à Effectuer

### 1. Imports à Modifier

**Avant (lignes 77-84) :**
```java
// GCS imports
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
```

**Après :**
```java
// GCS imports (KEPT FOR REFERENCE AND BACKWARD COMPATIBILITY)
// Original imports preserved in comments for easy rollback:
// import com.google.appengine.tools.cloudstorage.GcsFileOptions;
// import com.google.appengine.tools.cloudstorage.GcsFilename;
// import com.google.appengine.tools.cloudstorage.GcsInputChannel;
// import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
// import com.google.appengine.tools.cloudstorage.GcsService;
// import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
// import com.google.appengine.tools.cloudstorage.RetryParams;

// New Cloud Storage Abstraction imports
import com.google.appinventor.server.storage.CloudStorageService;
import com.google.appinventor.server.storage.CloudStorageService.FileMetadata;
import com.google.appinventor.server.storage.CloudStorageService.InputChannel;
import com.google.appinventor.server.storage.CloudStorageService.OutputChannel;
import com.google.appinventor.server.storage.CloudStorageFactory;
```

### 2. Déclaration du Service (ligne ~134)

**Avant :**
```java
private final GcsService gcsService;
```

**Après :**
```java
// Original GCS service declaration (kept for reference):
// private final GcsService gcsService;

// New unified cloud storage service
private final CloudStorageService cloudStorageService;
```

### 3. Initialisation dans le Constructeur (lignes 242-254)

**Avant :**
```java
ObjectifyStorageIo() {
  RetryParams retryParams = new RetryParams.Builder().initialRetryDelayMillis(100)
    .retryMaxAttempts(10)
    .totalRetryPeriodMillis(10000).build();
  if (DEBUG) {
    LOG.log(Level.INFO, "RetryParams: getInitialRetryDelayMillis() = " + retryParams.getInitialRetryDelayMillis());
    LOG.log(Level.INFO, "RetryParams: getRequestTimeoutMillis() = " + retryParams.getRequestTimeoutMillis());
    LOG.log(Level.INFO, "RetryParams: getRetryDelayBackoffFactor() = " + retryParams.getRetryDelayBackoffFactor());
    LOG.log(Level.INFO, "RetryParams: getRetryMaxAttempts() = " + retryParams.getRetryMaxAttempts());
    LOG.log(Level.INFO, "RetryParams: getRetryMinAttempts() = " + retryParams.getRetryMinAttempts());
    LOG.log(Level.INFO, "RetryParams: getTotalRetryPeriodMillis() = " + retryParams.getTotalRetryPeriodMillis());
  }
  gcsService = GcsServiceFactory.createGcsService(retryParams);
  memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
  initAllowedTutorialUrls();
}
```

**Après :**
```java
ObjectifyStorageIo() {
  // Original GCS initialization (kept for reference):
  // RetryParams retryParams = new RetryParams.Builder().initialRetryDelayMillis(100)
  //   .retryMaxAttempts(10)
  //   .totalRetryPeriodMillis(10000).build();
  // if (DEBUG) {
  //   LOG.log(Level.INFO, "RetryParams: getInitialRetryDelayMillis() = " + retryParams.getInitialRetryDelayMillis());
  //   ... (other debug logs)
  // }
  // gcsService = GcsServiceFactory.createGcsService(retryParams);

  // New unified cloud storage service initialization
  cloudStorageService = CloudStorageFactory.getInstance();
  if (DEBUG) {
    LOG.log(Level.INFO, "Cloud Storage Backend: " + cloudStorageService.getBackendInfo());
    LOG.log(Level.INFO, CloudStorageFactory.getConfigurationInfo());
  }

  memcache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
  initAllowedTutorialUrls();
}
```

### 4. Modification des Appels `gcsService.*`

Il y a **environ 20 occurrences** de `gcsService.` dans le fichier. Voici les patterns de remplacement :

#### Pattern 1 : `gcsService.createOrReplace()`

**Avant :**
```java
GcsOutputChannel outputChannel =
  gcsService.createOrReplace(new GcsFilename(bucketName, fileName), GcsFileOptions.getDefaultInstance());
outputChannel.write(ByteBuffer.wrap(content));
outputChannel.close();
```

**Après :**
```java
OutputChannel outputChannel =
  cloudStorageService.createOrReplace(bucketName, fileName);
outputChannel.write(ByteBuffer.wrap(content));
outputChannel.close();
```

**Lignes concernées :** 617, 1469, 1504, 2481

#### Pattern 2 : `gcsService.delete()`

**Avant :**
```java
gcsService.delete(new GcsFilename(bucketName, fileName));
```

**Après :**
```java
cloudStorageService.delete(bucketName, fileName);
```

**Lignes concernées :** 584, 669, 1483, 1606, 2443

#### Pattern 3 : `gcsService.getMetadata()` + `gcsService.openReadChannel()`

**Avant :**
```java
GcsFilename gcsFileName = new GcsFilename(bucketName, fileName);
int fileSize = (int) gcsService.getMetadata(gcsFileName).getLength();
ByteBuffer resultBuffer = ByteBuffer.allocate(fileSize);
GcsInputChannel readChannel = gcsService.openReadChannel(gcsFileName, 0);
int bytesRead = 0;
try {
  while (bytesRead < fileSize) {
    bytesRead += readChannel.read(resultBuffer);
    if (bytesRead < fileSize) {
      if (DEBUG) {
        LOG.log(Level.INFO, "Read " + bytesRead + " of " + fileSize + " bytes");
      }
    }
  }
} finally {
  readChannel.close();
}
return resultBuffer.array();
```

**Après :**
```java
FileMetadata metadata = cloudStorageService.getMetadata(bucketName, fileName);
int fileSize = (int) metadata.getLength();
ByteBuffer resultBuffer = ByteBuffer.allocate(fileSize);
InputChannel readChannel = cloudStorageService.openReadChannel(bucketName, fileName, 0);
int bytesRead = 0;
try {
  while (bytesRead < fileSize) {
    int read = readChannel.read(resultBuffer);
    if (read == -1) break;  // End of stream
    bytesRead += read;
    if (bytesRead < fileSize) {
      if (DEBUG) {
        LOG.log(Level.INFO, "Read " + bytesRead + " of " + fileSize + " bytes");
      }
    }
  }
} finally {
  readChannel.close();
}
return resultBuffer.array();
```

**Lignes concernées :** 1688-1700, 1915-1927, 2424-2431

### 5. Méthode `getGcsBucketToUse()` - Pas de Modification

La méthode privée `getGcsBucketToUse(FileData.RoleEnum role)` (lignes 2840-2846) **n'a PAS besoin d'être modifiée**.

Elle retourne simplement le nom du bucket à utiliser (GCS_BUCKET_NAME ou APK_BUCKET_NAME) selon le rôle du fichier. Cette logique reste valide pour S3 également.

```java
// This method works for both GCS and S3 - no changes needed
private static final String getGcsBucketToUse(FileData.RoleEnum role) {
  if (role == FileData.RoleEnum.TARGET) {
    return APK_BUCKET_NAME;  // This is now a S3 bucket name when using S3
  } else {
    return GCS_BUCKET_NAME;  // This is now a S3 bucket name when using S3
  }
}
```

**Note :** Les variables `GCS_BUCKET_NAME` et `APK_BUCKET_NAME` sont des noms logiques. En mode S3, elles contiendront les noms des buckets S3.

### 6. Méthode de Test `setGcsFileContent()` (ligne 2480)

**Avant :**
```java
@VisibleForTesting
void setGcsFileContent(String gcsPath, byte[] content) throws IOException {
  GcsOutputChannel outputChannel = gcsService.createOrReplace(
    new GcsFilename(getGcsBucketToUse(FileData.RoleEnum.TARGET), gcsPath),
      GcsFileOptions.getDefaultInstance());
  outputChannel.write(ByteBuffer.wrap(content));
  outputChannel.close();
}
```

**Après :**
```java
@VisibleForTesting
void setGcsFileContent(String gcsPath, byte[] content) throws IOException {
  // Original GCS version (kept for reference):
  // GcsOutputChannel outputChannel = gcsService.createOrReplace(
  //   new GcsFilename(getGcsBucketToUse(FileData.RoleEnum.TARGET), gcsPath),
  //     GcsFileOptions.getDefaultInstance());

  // New cloud storage version (works for both GCS and S3):
  OutputChannel outputChannel = cloudStorageService.createOrReplace(
      getGcsBucketToUse(FileData.RoleEnum.TARGET), gcsPath);
  outputChannel.write(ByteBuffer.wrap(content));
  outputChannel.close();
}
```

## Résumé des Modifications

| Type de Modification | Nombre d'Occurrences | Difficulté |
|---------------------|---------------------|------------|
| Imports | ~8 lignes | Facile |
| Déclaration du service | 1 ligne | Facile |
| Initialisation | ~13 lignes | Facile |
| `createOrReplace()` | 4 occurrences | Facile |
| `delete()` | 5 occurrences | Facile |
| `getMetadata()` + `openReadChannel()` | 3 occurrences | Moyen |
| Méthode de test | 1 occurrence | Facile |

**Total : ~20 modifications** dans un fichier de 2848 lignes (~0.7% du fichier)

## Script de Modification Automatique

Pour faciliter la migration, un script sed peut être utilisé :

```bash
#!/bin/bash
# migration_script.sh - Automated migration of ObjectifyStorageIo.java

FILE="src/com/google/appinventor/server/storage/ObjectifyStorageIo.java"
BACKUP="${FILE}.backup"

# Create backup
cp "$FILE" "$BACKUP"
echo "Backup created: $BACKUP"

# 1. Comment out original GCS imports
sed -i 's/^import com\.google\.appengine\.tools\.cloudstorage\./\/\/ import com.google.appengine.tools.cloudstorage./g' "$FILE"

# 2. Add new imports after the commented GCS imports
sed -i '/\/\/ import com\.google\.appengine\.tools\.cloudstorage\.RetryParams;/a\\nimport com.google.appinventor.server.storage.CloudStorageService;\nimport com.google.appinventor.server.storage.CloudStorageService.FileMetadata;\nimport com.google.appinventor.server.storage.CloudStorageService.InputChannel;\nimport com.google.appinventor.server.storage.CloudStorageService.OutputChannel;\nimport com.google.appinventor.server.storage.CloudStorageFactory;' "$FILE"

# 3. Replace service declaration
sed -i 's/private final GcsService gcsService;/\/\/ private final GcsService gcsService;\n  private final CloudStorageService cloudStorageService;/g' "$FILE"

# 4. Replace service initialization in constructor
# This is complex and may require manual editing

echo "Automatic modifications applied."
echo "IMPORTANT: Manual review required for constructor and method calls."
echo "See STORAGE_MIGRATION_GUIDE.md for detailed instructions."
```

## Vérification Après Migration

Après avoir effectué les modifications, vérifiez :

1. **Compilation :** Le projet doit compiler sans erreurs
   ```bash
   ant compile
   ```

2. **Tests :** Exécutez les tests unitaires
   ```bash
   ant tests
   ```

3. **Logs :** Au démarrage, vérifiez les logs pour confirmer le backend utilisé
   ```
   INFO: Initializing Cloud Storage Service
   INFO: Backend configuration: s3
   INFO: Selected backend: S3-compatible Storage (AWS S3, MinIO, etc.)
   INFO: ✓ S3StorageAdapter initialized successfully
   INFO: Cloud Storage Service ready: S3-compatible (https://minio.example.com:9000)
   ```

## Retour Arrière (Rollback)

Si vous devez revenir à l'implémentation GCS originale :

1. **Restaurer le backup :**
   ```bash
   cp src/com/google/appinventor/server/storage/ObjectifyStorageIo.java.backup \
      src/com/google/appinventor/server/storage/ObjectifyStorageIo.java
   ```

2. **Ou changer la configuration :**
   ```xml
   <!-- Dans appengine-web.xml -->
   <property name="storage.backend" value="gcs" />
   ```

Le code original GCS est préservé en commentaires pour référence et rollback facile.

## Compatibilité avec MIT App Inventor

Ces modifications sont conçues pour :

- ✅ **Minimiser les changements** : Seulement ~20 modifications dans un fichier de 2848 lignes
- ✅ **Préserver le code original** : Imports et code originaux conservés en commentaires
- ✅ **Faciliter les merges** : Modifications localisées et bien documentées
- ✅ **Permettre le rollback** : Configuration simple pour revenir à GCS
- ✅ **Maintenir la compatibilité** : GCS reste le backend par défaut

## Support

Pour toute question ou problème lors de la migration :

1. Consultez la documentation complète : `STORAGE_MIGRATION.md`
2. Vérifiez les logs de démarrage de l'application
3. Testez d'abord avec le backend GCS (original) pour vérifier que vos modifications n'ont pas cassé la compatibilité
4. Testez ensuite avec le backend S3

## Prochaines Étapes

Après avoir modifié `ObjectifyStorageIo.java` :

1. ✅ Ajouter les dépendances AWS SDK dans `lib/` ou via Maven/Gradle
2. ✅ Configurer `appengine-web.xml` avec les paramètres S3
3. ✅ Tester le déploiement avec MinIO localement
4. ✅ Déployer sur infrastructure de production

---

**Version :** Phase 3, Part 2
**Auteur :** MIT App Inventor Team (Google-free migration)
**Date :** 2024
