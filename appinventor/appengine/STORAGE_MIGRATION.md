# Migration du Stockage Cloud - Phase 3

## Vue d'ensemble

Cette documentation décrit la **Phase 3** de la migration de MIT App Inventor vers une infrastructure indépendante de Google : le remplacement de Google Cloud Storage (GCS) par une couche d'abstraction supportant GCS et S3.

**Objectifs :**
- ✅ Permettre le déploiement sur AWS S3, MinIO, ou tout service S3-compatible
- ✅ Maintenir 100% de compatibilité avec GCS (implémentation MIT originale)
- ✅ Faciliter les futurs merges avec le dépôt MIT officiel
- ✅ Basculement instantané entre backends via configuration

**Status :** ✅ Implémentation terminée (code + documentation)

## Architecture

### Avant (Google-dépendant)

```
ObjectifyStorageIo -----> GcsService -----> Google Cloud Storage
                             |
                             +-----> GcsFilename, GcsOutputChannel, etc.
```

### Après (Flexible)

```
ObjectifyStorageIo -----> CloudStorageService (interface)
                                    |
                                    +-----> GcsStorageAdapter -----> Google Cloud Storage
                                    |
                                    +-----> S3StorageAdapter  -----> AWS S3 / MinIO / S3-compatible
```

**CloudStorageFactory** : Sélectionne automatiquement le backend selon la configuration

## Fichiers Créés

### 1. Interface et Adaptateurs

| Fichier | Description | Lignes | Status |
|---------|-------------|---------|--------|
| `CloudStorageService.java` | Interface unifiée pour le stockage cloud | 150 | ✅ Complet |
| `GcsStorageAdapter.java` | Adaptateur pour Google Cloud Storage | 225 | ✅ Complet |
| `S3StorageAdapter.java` | Adaptateur pour S3 (AWS, MinIO, etc.) | 450 | ✅ Complet |
| `CloudStorageFactory.java` | Factory pour sélection automatique du backend | 180 | ✅ Complet |

**Total :** ~1000 lignes de nouveau code

### 2. Documentation et Configuration

| Fichier | Description | Status |
|---------|-------------|--------|
| `STORAGE_MIGRATION_GUIDE.md` | Guide détaillé de modification d'ObjectifyStorageIo.java | ✅ Complet |
| `appengine-web.xml.storage-example` | Exemples de configuration pour tous les backends | ✅ Complet |
| `STORAGE_MIGRATION.md` | Documentation complète (ce fichier) | ✅ Complet |

### 3. Modifications Nécessaires

| Fichier | Modifications | Status |
|---------|---------------|--------|
| `ObjectifyStorageIo.java` | ~20 modifications sur 2848 lignes (~0.7%) | ⏳ À effectuer |
| `lib/` ou build dependencies | Ajouter AWS SDK S3 | ⏳ À effectuer |

## Fonctionnalités

### CloudStorageService Interface

```java
public interface CloudStorageService {
  // Création/remplacement de fichier
  OutputChannel createOrReplace(String bucket, String fileName) throws IOException;

  // Lecture de fichier
  InputChannel openReadChannel(String bucket, String fileName, long offset) throws IOException;

  // Métadonnées
  FileMetadata getMetadata(String bucket, String fileName) throws IOException;

  // Suppression
  void delete(String bucket, String fileName) throws IOException;

  // Vérification d'existence
  boolean exists(String bucket, String fileName);

  // Lecture simplifiée
  InputStream getInputStream(String bucket, String fileName) throws IOException;

  // Information sur le backend
  String getBackendInfo();
}
```

### Backends Supportés

#### 1. Google Cloud Storage (GcsStorageAdapter)

```xml
<property name="storage.backend" value="gcs" />
<property name="gcs.bucket" value="your-bucket" />
```

**Caractéristiques :**
- Implémentation MIT originale (100% compatible)
- Utilise `GcsService` de Google App Engine
- Backend par défaut si `storage.backend` non spécifié

#### 2. Amazon S3 (S3StorageAdapter)

```xml
<property name="storage.backend" value="s3" />
<property name="s3.endpoint" value="" />  <!-- Empty for AWS -->
<property name="s3.region" value="us-east-1" />
<property name="s3.access.key" value="YOUR_KEY" />
<property name="s3.secret.key" value="YOUR_SECRET" />
<property name="s3.path.style" value="false" />
```

**Caractéristiques :**
- AWS SDK S3 officiel
- Support des régions AWS
- Virtual-hosted style URLs
- IAM credentials

#### 3. MinIO (S3StorageAdapter avec endpoint custom)

```xml
<property name="storage.backend" value="s3" />
<property name="s3.endpoint" value="https://minio.example.com:9000" />
<property name="s3.region" value="us-east-1" />
<property name="s3.access.key" value="minioadmin" />
<property name="s3.secret.key" value="minioadmin" />
<property name="s3.path.style" value="true" />  <!-- MinIO uses path-style -->
```

**Caractéristiques :**
- Open-source, self-hosted
- 100% compatible S3 API
- Path-style URLs
- Pas de coûts cloud
- Idéal pour déploiement on-premise

#### 4. Autres Services S3-compatibles

Le `S3StorageAdapter` fonctionne avec tout service compatible S3 :
- **Wasabi** : Storage low-cost (80% moins cher que S3)
- **Backblaze B2** : Storage économique avec API S3
- **DigitalOcean Spaces** : S3-compatible storage
- **Ceph** : Solution open-source pour datacenters
- **Cloudflare R2** : Zero egress fees

## Installation et Configuration

### Étape 1 : Ajouter les Dépendances

#### Option A : Fichiers JAR (build.xml / Ant)

Télécharger et ajouter dans `appinventor/lib/aws-sdk-s3/` :

```
aws-sdk-s3-2.20.0.jar
aws-sdk-core-2.20.0.jar
aws-core-2.20.0.jar
aws-auth-2.20.0.jar
aws-regions-2.20.0.jar
aws-http-client-spi-2.20.0.jar
aws-apache-client-2.20.0.jar
...
```

Puis modifier `appengine/build.xml` :

```xml
<path id="libsForBuild">
  <!-- Existing libraries -->
  <fileset dir="../lib/aws-sdk-s3" includes="*.jar"/>
</path>
```

#### Option B : Maven/Gradle (si migration vers Maven)

```xml
<!-- pom.xml -->
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
  <version>2.20.0</version>
</dependency>
```

### Étape 2 : Modifier ObjectifyStorageIo.java

Suivre le guide détaillé : **`STORAGE_MIGRATION_GUIDE.md`**

**Résumé des modifications :**
- Imports : Commenter les imports GCS, ajouter les imports CloudStorage
- Service : Remplacer `GcsService gcsService` par `CloudStorageService cloudStorageService`
- Initialisation : Utiliser `CloudStorageFactory.getInstance()`
- Appels : Remplacer `gcsService.*` par `cloudStorageService.*` (~20 occurrences)

### Étape 3 : Configurer le Backend

Copier et adapter `appengine-web.xml.storage-example` :

```bash
cp war/WEB-INF/appengine-web.xml.storage-example war/WEB-INF/appengine-web.xml
# Éditer appengine-web.xml et décommenter la section pour votre backend
```

**Pour MinIO (exemple) :**

```xml
<system-properties>
  <property name="storage.backend" value="s3" />
  <property name="s3.endpoint" value="https://minio.local:9000" />
  <property name="s3.region" value="us-east-1" />
  <property name="s3.access.key" value="minioadmin" />
  <property name="s3.secret.key" value="minioadmin" />
  <property name="s3.path.style" value="true" />
  <property name="gcs.bucket" value="appinventor-projects" />
  <property name="gcs.apkbucket" value="appinventor-builds" />
</system-properties>
```

### Étape 4 : Préparer le Stockage

#### Pour MinIO :

```bash
# Installation via Docker
docker run -d \
  -p 9000:9000 -p 9001:9001 \
  --name minio \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  -v /data/minio:/data \
  quay.io/minio/minio server /data --console-address ":9001"

# Créer les buckets
docker exec minio mc alias set local http://localhost:9000 minioadmin minioadmin
docker exec minio mc mb local/appinventor-projects
docker exec minio mc mb local/appinventor-builds
```

#### Pour AWS S3 :

```bash
# Créer les buckets via AWS CLI
aws s3 mb s3://appinventor-projects --region us-east-1
aws s3 mb s3://appinventor-builds --region us-east-1

# Configurer lifecycle pour apk bucket (suppression après 1 jour)
aws s3api put-bucket-lifecycle-configuration \
  --bucket appinventor-builds \
  --lifecycle-configuration file://lifecycle.json
```

`lifecycle.json` :
```json
{
  "Rules": [
    {
      "Id": "DeleteOldBuilds",
      "Status": "Enabled",
      "Expiration": { "Days": 1 },
      "Filter": {}
    }
  ]
}
```

### Étape 5 : Compiler et Tester

```bash
# Compiler
cd appinventor
ant compile

# Exécuter les tests
ant tests

# Démarrer le serveur de développement
ant devmode
```

**Vérifier les logs au démarrage :**

```
INFO: ======================================================================
INFO: Initializing Cloud Storage Service
INFO: Backend configuration: s3
INFO: Selected backend: S3-compatible Storage (AWS S3, MinIO, etc.)
INFO: Creating S3StorageAdapter...
INFO: S3 Storage Configuration:
INFO:   Region: us-east-1
INFO:   Endpoint: https://minio.local:9000
INFO:   Path-style access: true
INFO:   Access key: mini...dmin
INFO: ✓ S3StorageAdapter initialized successfully
INFO:   Backend info: S3-compatible (https://minio.local:9000)
INFO: Cloud Storage Service ready: S3-compatible (https://minio.local:9000)
INFO: ======================================================================
```

## Migration de Données

### Migrer de GCS vers S3

Si vous migrez une instance existante de GCS vers S3, vous devez copier les données :

#### Option 1 : Outil gsutil + aws CLI

```bash
#!/bin/bash
# migrate-gcs-to-s3.sh

GCS_BUCKET="gs://your-gcs-bucket"
S3_BUCKET="s3://your-s3-bucket"

# Télécharger depuis GCS
gsutil -m cp -r "$GCS_BUCKET/*" ./temp_migration/

# Uploader vers S3
aws s3 sync ./temp_migration/ "$S3_BUCKET/"

# Nettoyer
rm -rf ./temp_migration/
```

#### Option 2 : rclone (plus rapide)

```bash
# Configurer rclone
rclone config

# Copier directement GCS → S3
rclone copy gcs:your-gcs-bucket s3:your-s3-bucket --progress
```

#### Option 3 : Google Transfer Service

Pour de gros volumes, utiliser Google Cloud Transfer Service :
https://cloud.google.com/storage-transfer/docs/create-transfers

### Migrer de S3 vers GCS

```bash
# rclone
rclone copy s3:your-s3-bucket gcs:your-gcs-bucket --progress
```

## Performance et Optimisation

### Comparaison des Backends

| Backend | Latence lecture | Latence écriture | Débit | Coût ($/GB/mois) |
|---------|-----------------|------------------|-------|-------------------|
| **GCS** | ~50-100ms | ~100-150ms | Élevé | $0.020 |
| **AWS S3** | ~50-100ms | ~100-150ms | Élevé | $0.023 |
| **MinIO** | ~5-20ms (local) | ~10-30ms (local) | Très élevé | $0 (self-hosted) |
| **Wasabi** | ~50-100ms | ~100-150ms | Élevé | $0.0059 |

### Optimisations MinIO

Pour de meilleures performances avec MinIO :

```bash
# Déployer avec plusieurs disques (Erasure Coding)
minio server /data{1...4}

# Configurer avec SSD
# Utiliser un réseau 10Gbit/s si possible

# Activer la compression
mc admin config set local compression extensions=".log,.csv,.txt"
```

### Optimisations S3

```xml
<!-- Augmenter le buffer size -->
<property name="s3.upload.buffer.size" value="10485760" />  <!-- 10MB -->

<!-- Utiliser des threads multiples pour upload/download -->
<property name="s3.transfer.threads" value="10" />
```

## Sécurité

### Bonnes Pratiques

#### 1. Gestion des Secrets

**❌ MAL (secrets en clair dans appengine-web.xml) :**
```xml
<property name="s3.access.key" value="AKIAIOSFODNN7EXAMPLE" />
<property name="s3.secret.key" value="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY" />
```

**✅ BIEN (via variables d'environnement) :**
```xml
<env-variables>
  <env-var name="S3_ACCESS_KEY" value="${S3_ACCESS_KEY}" />
  <env-var name="S3_SECRET_KEY" value="${S3_SECRET_KEY}" />
</env-variables>
```

Et modifier `S3StorageAdapter.java` pour lire depuis les variables d'environnement.

**✅ MIEUX (via gestionnaire de secrets) :**
- AWS Secrets Manager
- HashiCorp Vault
- Google Secret Manager (si vous restez sur GCP pour l'app server)

#### 2. Permissions IAM (AWS S3)

Créer une policy IAM minimale :

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::appinventor-projects/*",
        "arn:aws:s3:::appinventor-builds/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::appinventor-projects",
        "arn:aws:s3:::appinventor-builds"
      ]
    }
  ]
}
```

#### 3. Chiffrement

**Pour S3 :**
```bash
# Activer le chiffrement côté serveur (SSE-S3)
aws s3api put-bucket-encryption \
  --bucket appinventor-projects \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'
```

**Pour MinIO :**
```bash
# Activer le chiffrement avec KMS
mc encrypt set sse-kms myminio/appinventor-projects
```

#### 4. Versioning (pour backup)

```bash
# AWS S3
aws s3api put-bucket-versioning \
  --bucket appinventor-projects \
  --versioning-configuration Status=Enabled

# MinIO
mc version enable myminio/appinventor-projects
```

## Dépannage

### Problème : "S3 credentials not configured"

**Cause :** Les clés S3 ne sont pas définies dans la configuration.

**Solution :**
```xml
<property name="s3.access.key" value="YOUR_ACCESS_KEY" />
<property name="s3.secret.key" value="YOUR_SECRET_KEY" />
```

### Problème : "Failed to connect to S3 endpoint"

**Cause :** Endpoint MinIO inaccessible ou mauvaise URL.

**Solution :**
```bash
# Vérifier que MinIO est accessible
curl https://minio.example.com:9000/minio/health/live

# Vérifier le certificat SSL
openssl s_client -connect minio.example.com:9000

# Si certificat auto-signé, désactiver vérification SSL (dev seulement !)
# Modifier S3StorageAdapter pour accepter certificats non vérifiés
```

### Problème : "Access Denied" (S3)

**Cause :** Permissions IAM insuffisantes.

**Solution :**
```bash
# Vérifier les permissions
aws iam get-user-policy --user-name appinventor-user --policy-name S3Access

# Tester l'accès
aws s3 ls s3://appinventor-projects/
```

### Problème : "Path-style access not working"

**Cause :** Configuration `s3.path.style` incorrecte.

**Solution :**
- AWS S3 : `s3.path.style=false` (virtual-hosted style)
- MinIO : `s3.path.style=true` (path style)

### Problème : Performance lente (MinIO)

**Solutions :**
1. Vérifier la latence réseau : `ping minio.example.com`
2. Utiliser un réseau local (pas internet)
3. Configurer MinIO avec plusieurs disques
4. Activer la compression
5. Augmenter les workers MinIO

## Compatibilité avec MIT App Inventor

### Stratégie de Merge

Pour faciliter les futurs merges avec le dépôt MIT :

1. **Code original préservé :**
   ```java
   // Original GCS imports (kept for reference):
   // import com.google.appengine.tools.cloudstorage.GcsService;
   ```

2. **Modifications minimales :**
   - Seulement ~20 changements dans ObjectifyStorageIo.java
   - Nouveau code isolé dans des fichiers séparés

3. **Backend par défaut = GCS :**
   ```java
   String backendConfig = Flag.createFlag("storage.backend", "gcs").get();
   ```

4. **Documentation des changements :**
   - Chaque modification est documentée
   - Références aux lignes d'origine

### Procédure de Merge

```bash
# Ajouter le dépôt MIT comme remote
git remote add mit https://github.com/mit-cml/appinventor-sources.git

# Récupérer les mises à jour
git fetch mit

# Merger dans votre branche principale
git checkout main
git merge mit/master

# Rebaser votre branche Google-free
git checkout google-free
git rebase main

# Résoudre les conflits (uniquement dans ObjectifyStorageIo.java probablement)
# Les nouveaux fichiers (CloudStorage*.java) n'auront pas de conflits
```

## Roadmap et Prochaines Étapes

### ✅ Phase 1 : Rendezvous Server (Complète)
- Migration Memcache → Redis

### ✅ Phase 2 : Build Server (Déjà indépendant)
- Aucune modification nécessaire

### ✅ Phase 3 : Stockage de Fichiers (Complète - code)
- Migration GCS → S3/MinIO
- **Reste à faire :** Modifier ObjectifyStorageIo.java

### 🔜 Phase 4 : Base de Données
- Migration Datastore → PostgreSQL/MongoDB
- Remplacer Objectify ORM

### 🔜 Phase 5 : Serveur Applicatif
- Migration App Engine → Kubernetes/Docker
- Remplacer services App Engine (Memcache, Task Queue, etc.)

## Ressources

### Documentation

- [AWS S3 Developer Guide](https://docs.aws.amazon.com/s3/)
- [MinIO Documentation](https://min.io/docs/)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)

### Outils

- **MinIO Client (mc)** : CLI pour gérer MinIO
- **rclone** : Sync entre différents storage providers
- **s3cmd** : CLI alternatif pour S3
- **aws-cli** : CLI officiel AWS

### Support

- **MIT App Inventor** : https://github.com/mit-cml/appinventor-sources
- **MinIO Community** : https://slack.min.io/
- **AWS Forums** : https://forums.aws.amazon.com/

## Conclusion

La Phase 3 fournit une couche d'abstraction complète pour le stockage cloud, permettant à MIT App Inventor de fonctionner sur n'importe quelle infrastructure de stockage objet.

**Avantages :**
- ✅ Indépendance de Google Cloud Storage
- ✅ Support de MinIO (open-source, self-hosted)
- ✅ Support d'AWS S3 et services compatibles
- ✅ Compatibilité totale avec l'implémentation MIT originale
- ✅ Migration facile et réversible
- ✅ Pas de vendor lock-in

**Prochaine étape :** Modifier `ObjectifyStorageIo.java` selon le guide `STORAGE_MIGRATION_GUIDE.md`.

---

**Version :** Phase 3 - Complète (code + documentation)
**Date :** 2024
**Auteur :** MIT App Inventor Team (Google-free migration)
