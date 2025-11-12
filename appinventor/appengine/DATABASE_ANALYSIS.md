# Analyse de la Base de Données - Phase 4

## Vue d'ensemble

Cette analyse examine l'architecture actuelle de la base de données de MIT App Inventor basée sur Google Cloud Datastore avec Objectify, en vue de migrer vers une solution indépendante (PostgreSQL, MongoDB, ou MySQL).

**Complexité :** ⚠️ **ÉLEVÉE** - Cette phase est significativement plus complexe que les Phases 1-3

**Raisons :**
- 16 entités de données différentes
- Relations hiérarchiques parent-enfant
- Entity groups pour transactions ACID
- ~100 méthodes dans ObjectifyStorageIo.java (2848 lignes)
- Transactions, index, queries complexes

## Architecture Actuelle

### Stack Technologique

```
ObjectifyStorageIo (2848 lignes)
         ↓
    Objectify 3.1 (ORM)
         ↓
Google Cloud Datastore (NoSQL)
         ↓
    Memcache (cache)
```

**Objectify :** ORM (Object-Relational Mapping) simplifiant l'accès à Datastore
**Datastore :** Base de données NoSQL de Google (clé-valeur hiérarchique)

### Entités de Données (16 entités)

#### 1. **UserData** (Root Entity)
```java
@Unindexed @Cached
class UserData {
  @Id String id;                    // User ID (primary key)
  @Indexed String email;            // Email
  @Indexed String emaillower;       // Email lowercase (for search)
  String settings;                  // User settings (JSON)
  boolean tosAccepted;              // Terms of Service accepted
  boolean isAdmin;                  // Admin flag
  @Indexed Date visited;            // Last activity
  String name, link;                // Profile info
  int emailFrequency, type;         // Preferences
  String sessionid;                 // Active session UUID
  String password;                  // Hashed password (PBKDF2)
  String templatePath;              // Template project path
  boolean upgradedGCS;              // GCS migration flag
}
```

**Relations :**
- **Parent de** : UserProjectData, UserFileData
- **Volume** : ~millions d'utilisateurs potentiels
- **Queries** : Par email, emaillower, visited

#### 2. **ProjectData** (Root Entity)
```java
@Cached @Unindexed
class ProjectData {
  @Id Long id;                      // Auto-generated project ID
  String name;                      // Project name
  String link;                      // Introduction link
  String type;                      // "YoungAndroid", "Simple"
  String settings;                  // Project settings (JSON)
  long dateCreated;                 // Creation timestamp
  long dateModified;                // Last modification
  long dateBuilt;                   // Last build
  String history;                   // Project history (special format)
  boolean projectMovedToTrashFlag;  // Soft delete flag
}
```

**Relations :**
- **Parent de** : FileData
- **Volume** : ~millions de projets potentiels
- **Queries** : Par ID principalement

#### 3. **UserProjectData** (Child of UserData)
```java
@Unindexed
class UserProjectData {
  @Id long projectId;               // Project ID (primary key)
  @Parent Key<UserData> userKey;    // Parent user
  StateEnum state;                  // CLOSED, OPEN, DELETED
  String settings;                  // User-specific settings
}
```

**Relations :**
- **Parent** : UserData
- **Purpose** : Many-to-many entre users et projects
- **Volume** : ~dizaines de millions d'associations
- **Transactions** : Grouped with parent UserData

#### 4. **FileData** (Child of ProjectData)
```java
@Cached @Unindexed
class FileData implements Serializable {
  @Id String fileName;              // File name (primary key)
  @Parent Key<ProjectData> projectKey; // Parent project
  RoleEnum role;                    // SOURCE, TARGET, TEMPORARY
  byte[] content;                   // File content (small files)
  boolean isBlob;                   // Stored in Blobstore?
  String blobstorePath;             // Blobstore path
  String blobKey;                   // Blobstore key
  Boolean isGCS;                    // Stored in GCS?
  String gcsName;                   // GCS file name
  String settings;                  // File settings
  long lastBackup;                  // Last backup time
  String userId;                    // Owner user ID
}
```

**Relations :**
- **Parent** : ProjectData
- **Volume** : ~millions de fichiers
- **Storage** : Content peut être dans Datastore, Blobstore, ou GCS
- **Transactions** : Grouped with parent ProjectData

#### 5. **UserFileData** (Child of UserData)
```java
@Unindexed
class UserFileData {
  @Id String fileName;              // File name (primary key)
  @Parent Key<UserData> userKey;    // Parent user
  byte[] content;                   // File content
  String settings;                  // File settings
}
```

**Relations :**
- **Parent** : UserData
- **Purpose** : User-specific files (backpack, keystore)
- **Volume** : ~quelques fichiers par utilisateur

#### 6-16. Autres Entités

| Entité | Type | Purpose | Volume | Index |
|--------|------|---------|--------|-------|
| **MotdData** | Global | Message of the day | 1 | None |
| **RendezvousData** | Global | Rendezvous fallback (when memcache down) | ~milliers | key, used |
| **WhiteListData** | Global | Email whitelist | ~milliers | emailLower |
| **FeedbackData** | Global | User feedback | ~dizaines de milliers | None |
| **NonceData** | Global | APK download nonces | ~dizaines de milliers | nonce, timestamp |
| **CorruptionRecord** | Global | Corruption tracking | ~centaines | timestamp |
| **SplashData** | Global | Splash screen config | 1 | None |
| **PWData** | Global | Password reset tokens | ~actifs | timestamp |
| **Backpack** | Global | Shared backpacks | ~milliers | None |
| **AllowedTutorialUrls** | Global | Allowed tutorial URLs | 1 | None |
| **AllowedIosExtensions** | Global | Allowed iOS extensions | 1 | None |

### Hiérarchie et Entity Groups

**Datastore utilise des Entity Groups pour les transactions ACID :**

```
Entity Group 1:
  UserData (user1)
    ├── UserProjectData (project1)
    ├── UserProjectData (project2)
    └── UserFileData (backpack)

Entity Group 2:
  ProjectData (project1)
    ├── FileData (Screen1.bky)
    ├── FileData (Screen1.scm)
    └── FileData (assets/image.png)

Entity Group 3:
  UserData (user2)
    └── ...
```

**Implications :**
- Transactions limitées à un entity group
- Pas de transaction cross-entity-group
- Performance optimisée pour queries dans un group

### Patterns d'Accès

#### Pattern 1 : User CRUD
```java
// Get user
UserData userData = datastore.find(userKey(userId));

// Update user
userData.settings = newSettings;
datastore.put(userData);
```

#### Pattern 2 : Project CRUD avec Relations
```java
// Create project with user relationship
runJobWithRetries(new JobRetryHelper() {
  public void run(Objectify datastore) {
    // Create ProjectData
    ProjectData pd = new ProjectData();
    pd.name = projectName;
    datastore.put(pd);

    // Create UserProjectData (links user to project)
    UserProjectData upd = new UserProjectData();
    upd.projectId = pd.id;
    upd.userKey = userKey(userId);
    datastore.put(upd);
  }
}, true); // Transaction = true
```

#### Pattern 3 : Query avec Filtres
```java
// Find user by email
UserData user = datastore.query(UserData.class)
    .filter("email", email)
    .get();

// Find nonces to cleanup (older than threshold)
Query<NonceData> query = datastore.query(NonceData.class)
    .filter("timestamp <", threshold);
```

#### Pattern 4 : Batch Operations
```java
// Get all projects for a user
List<Long> projectIds = getProjects(userId);
List<UserProject> projects = getUserProjects(userId, projectIds);
```

### Transactions et Concurrence

**Objectify JobRetryHelper Pattern :**

```java
abstract class JobRetryHelper {
  public abstract void run(Objectify datastore) throws ObjectifyException, IOException;
  public void onNonFatalError() { }
  public void onIOException(IOException error) { }
}

// Usage avec retry automatique (MAX_JOB_RETRIES = 10)
runJobWithRetries(new JobRetryHelper() {
  public void run(Objectify datastore) {
    // Database operations
  }
}, true); // useTransaction
```

**Caractéristiques :**
- Retry automatique en cas d'erreur
- Transactions optionnelles
- ConcurrentModificationException handling

### Caching Strategy

**Memcache :**
- User data cached (60 seconds)
- Build status cached
- Project owner cached
- File data cached (@Cached annotation)

```java
String cachekey = User.usercachekey + "|" + userId;
User user = (User) memcache.get(cachekey);
if (user != null) {
  return user;
}
// ... fetch from datastore ...
memcache.put(cachekey, user, Expiration.byDeltaSeconds(60));
```

### Index Strategy

**Indexed Fields :**
- `UserData.email`, `UserData.emaillower`, `UserData.visited`
- `RendezvousData.key`, `RendezvousData.used`
- `WhiteListData.emailLower`
- `NonceData.nonce`, `NonceData.timestamp`
- `CorruptionRecord.timestamp`
- `PWData.timestamp`

**Non-indexed par défaut** : `@Unindexed` sur la plupart des classes

## Défis de Migration

### 1. Hiérarchie Parent-Child

**Datastore :**
- Relations parent-child natives
- Entity groups pour transactions
- Clés composites automatiques

**SQL (PostgreSQL/MySQL) :**
- Foreign keys classiques
- Pas de notion d'entity group
- Transactions ACID plus flexibles

**Solution :**
- Mapper parent-child vers foreign keys
- Utiliser des tables de jonction
- Gérer les transactions différemment

### 2. Schéma Flexible (NoSQL → SQL)

**Datastore :**
- Schéma flexible
- Champs peuvent être ajoutés/supprimés
- Pas de schéma strict

**SQL :**
- Schéma strict
- Migrations nécessaires pour changements
- Types définis

**Solution :**
- Définir un schéma SQL complet
- Utiliser JSONB pour champs flexibles
- Planifier les migrations de schéma

### 3. Transactions

**Datastore :**
- Transactions limitées à un entity group
- Cross-group queries non transactionnelles
- Optimistic locking

**SQL :**
- Transactions ACID complètes
- Multi-table transactions
- Isolation levels configurables

**Solution :**
- Profiter des transactions SQL plus puissantes
- Adapter le code pour cross-table operations
- Revoir le pattern JobRetryHelper

### 4. Queries et Index

**Datastore :**
- Index explicites nécessaires
- Queries sur propriétés indexées
- Pas de JOIN natif

**SQL :**
- Index standard (B-tree, Hash)
- JOINs natifs
- Queries complexes

**Solution :**
- Créer index appropriés en SQL
- Utiliser JOINs pour remplacer queries multiples
- Optimiser avec EXPLAIN ANALYZE

### 5. Auto-increment IDs

**Datastore :**
- IDs auto-générés (Long)
- Garantis uniques globalement

**SQL :**
- SERIAL / AUTO_INCREMENT
- Uniques par table

**Solution :**
- Utiliser BIGSERIAL (PostgreSQL)
- Ou UUID pour clés distribuées
- Mapper les IDs existants lors de migration

### 6. Volume de Données

**Estimation :**
- Users : millions
- Projects : millions
- Files : dizaines de millions
- UserProjectData : dizaines de millions

**Solution :**
- Migration par batch
- Downtime planifié
- Ou dual-write temporaire

## Stratégies de Migration

### Option A : Big Bang Migration

**Approche :**
1. Créer schéma SQL complet
2. Export massif de Datastore
3. Import dans SQL
4. Switch du code
5. Déploiement

**Avantages :**
- Migration complète en une fois
- Code simplifié après migration

**Inconvénients :**
- Downtime important (heures/jours)
- Risque élevé
- Difficile à tester

### Option B : Strangler Pattern (Recommandé)

**Approche :**
1. Créer abstraction DatabaseService
2. Implémenter DatastoreAdapter (wrapper Objectify)
3. Implémenter SQLAdapter (nouvelle implémentation)
4. Migrer table par table
5. Dual-write pendant transition
6. Valider et switch progressif

**Phases :**
- **Phase 4.1** : Abstraction + Datastore adapter
- **Phase 4.2** : SQL adapter + tables simples (MOTD, Splash, etc.)
- **Phase 4.3** : Tables principales (User, Project)
- **Phase 4.4** : Relations et fichiers
- **Phase 4.5** : Migration des données existantes
- **Phase 4.6** : Désactivation Datastore

**Avantages :**
- Migration progressive
- Rollback facile à chaque étape
- Downtime minimal
- Testable en production

**Inconvénients :**
- Plus long
- Code de transition temporaire
- Dual-write = complexité

### Option C : Nouveau Système Parallèle

**Approche :**
1. Déployer nouvelle instance avec SQL
2. Rediriger nouveaux utilisateurs vers SQL
3. Garder Datastore pour utilisateurs existants
4. Migrer progressivement

**Avantages :**
- Zéro downtime
- Facile à tester
- Rollback simple

**Inconvénients :**
- Infrastructure double
- Coûts plus élevés
- Synchronisation complexe

## Schéma SQL Proposé (PostgreSQL)

### Tables Principales

```sql
-- Users
CREATE TABLE users (
  id VARCHAR(255) PRIMARY KEY,
  email VARCHAR(255) NOT NULL,
  email_lower VARCHAR(255) NOT NULL,
  settings TEXT,
  tos_accepted BOOLEAN DEFAULT FALSE,
  is_admin BOOLEAN DEFAULT FALSE,
  visited TIMESTAMP,
  name VARCHAR(255),
  link VARCHAR(512),
  email_frequency INTEGER DEFAULT 0,
  type INTEGER DEFAULT 0,
  session_id VARCHAR(36),
  password_hash VARCHAR(255),
  template_path VARCHAR(512),
  upgraded_gcs BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_email_lower ON users(email_lower);
CREATE INDEX idx_users_visited ON users(visited);

-- Projects
CREATE TABLE projects (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  link VARCHAR(512),
  type VARCHAR(50) NOT NULL,
  settings TEXT,
  date_created BIGINT NOT NULL,
  date_modified BIGINT NOT NULL,
  date_built BIGINT DEFAULT 0,
  history TEXT,
  moved_to_trash BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_projects_date_modified ON projects(date_modified);

-- User-Project relationship (many-to-many)
CREATE TABLE user_projects (
  user_id VARCHAR(255) REFERENCES users(id) ON DELETE CASCADE,
  project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  state VARCHAR(20) DEFAULT 'OPEN',  -- OPEN, CLOSED, DELETED
  settings TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, project_id)
);

CREATE INDEX idx_user_projects_user ON user_projects(user_id);
CREATE INDEX idx_user_projects_project ON user_projects(project_id);

-- Project Files
CREATE TABLE files (
  project_id BIGINT REFERENCES projects(id) ON DELETE CASCADE,
  file_name VARCHAR(512) NOT NULL,
  role VARCHAR(20) NOT NULL,  -- SOURCE, TARGET, TEMPORARY
  content BYTEA,  -- For small files
  is_blob BOOLEAN DEFAULT FALSE,
  blob_store_path VARCHAR(512),
  blob_key VARCHAR(512),
  is_gcs BOOLEAN DEFAULT FALSE,
  gcs_name VARCHAR(512),
  settings TEXT,
  last_backup BIGINT DEFAULT 0,
  user_id VARCHAR(255) REFERENCES users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (project_id, file_name)
);

CREATE INDEX idx_files_project ON files(project_id);
CREATE INDEX idx_files_user ON files(user_id);

-- User Files (backpack, keystore, etc.)
CREATE TABLE user_files (
  user_id VARCHAR(255) REFERENCES users(id) ON DELETE CASCADE,
  file_name VARCHAR(255) NOT NULL,
  content BYTEA,
  settings TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, file_name)
);
```

### Tables Auxiliaires

```sql
-- Message of the Day
CREATE TABLE motd (
  id BIGSERIAL PRIMARY KEY,
  caption VARCHAR(512),
  content TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Rendezvous Data
CREATE TABLE rendezvous (
  id BIGSERIAL PRIMARY KEY,
  key VARCHAR(6) NOT NULL UNIQUE,
  ip_address VARCHAR(45),
  used TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rendezvous_key ON rendezvous(key);
CREATE INDEX idx_rendezvous_used ON rendezvous(used);

-- Whitelist
CREATE TABLE whitelist (
  id BIGSERIAL PRIMARY KEY,
  email_lower VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_whitelist_email ON whitelist(email_lower);

-- Feedback
CREATE TABLE feedback (
  id BIGSERIAL PRIMARY KEY,
  notes TEXT,
  found_in VARCHAR(255),
  fault_data TEXT,
  comments TEXT,
  datestamp VARCHAR(255),
  email VARCHAR(255),
  project_id VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Nonces (APK downloads)
CREATE TABLE nonces (
  id BIGSERIAL PRIMARY KEY,
  nonce VARCHAR(255) NOT NULL UNIQUE,
  user_id VARCHAR(255),
  project_id BIGINT,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nonces_nonce ON nonces(nonce);
CREATE INDEX idx_nonces_timestamp ON nonces(timestamp);

-- Corruption Records
CREATE TABLE corruption_records (
  id BIGSERIAL PRIMARY KEY,
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  user_id VARCHAR(255),
  project_id BIGINT,
  file_id VARCHAR(512),
  message TEXT
);

CREATE INDEX idx_corruption_timestamp ON corruption_records(timestamp);

-- Splash Screen Configuration
CREATE TABLE splash (
  id BIGSERIAL PRIMARY KEY,
  version INTEGER NOT NULL,
  content TEXT,
  height INTEGER,
  width INTEGER,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Password Reset
CREATE TABLE password_reset (
  id VARCHAR(36) PRIMARY KEY,  -- UUID
  timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  email VARCHAR(255) NOT NULL
);

CREATE INDEX idx_pwreset_timestamp ON password_reset(timestamp);
CREATE INDEX idx_pwreset_email ON password_reset(email);

-- Backpacks
CREATE TABLE backpacks (
  id VARCHAR(36) PRIMARY KEY,  -- UUID
  content TEXT,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Allowed Tutorial URLs
CREATE TABLE allowed_tutorial_urls (
  id BIGSERIAL PRIMARY KEY,
  allowed_urls TEXT,  -- JSON array
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Allowed iOS Extensions
CREATE TABLE allowed_ios_extensions (
  id BIGSERIAL PRIMARY KEY,
  allowed_extensions TEXT,  -- JSON array
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Plan d'Implémentation

### Phase 4.1 : Préparation et Abstraction

**Objectif :** Créer la couche d'abstraction sans impacter le fonctionnement actuel

**Tâches :**
1. Créer `DatabaseService` interface
2. Créer `DatastoreAdapter` (wrapper autour d'Objectify)
3. Créer `DatabaseFactory`
4. Tester que DatastoreAdapter fonctionne identiquement à l'original
5. Pas de changements dans ObjectifyStorageIo pour l'instant

**Durée estimée :** 1-2 semaines

**Risque :** Faible (wrapper uniquement)

### Phase 4.2 : SQL Adapter - Tables Simples

**Objectif :** Implémenter et tester SQL pour tables simples

**Tables :**
- MOTD
- Splash
- Whitelist
- AllowedTutorialUrls
- AllowedIosExtensions

**Tâches :**
1. Créer schéma SQL pour ces tables
2. Implémenter `SQLAdapter` pour ces tables
3. Dual-write : écrire dans Datastore ET SQL
4. Valider que les données sont identiques
5. Basculer en lecture depuis SQL

**Durée estimée :** 2-3 semaines

**Risque :** Faible (peu de données, peu de queries)

### Phase 4.3 : SQL Adapter - Users

**Objectif :** Migrer la table Users

**Tâches :**
1. Créer schéma SQL pour users
2. Migrer les données existantes (batch)
3. Implémenter CRUD pour users en SQL
4. Dual-write pendant 1-2 semaines
5. Valider et basculer

**Durée estimée :** 3-4 semaines

**Risque :** Moyen (table critique)

### Phase 4.4 : SQL Adapter - Projects et Relations

**Objectif :** Migrer Projects et UserProjects

**Tâches :**
1. Créer schéma pour projects et user_projects
2. Migrer données existantes
3. Implémenter CRUD et relations
4. Dual-write
5. Basculer

**Durée estimée :** 4-6 semaines

**Risque :** Élevé (beaucoup de données, queries complexes)

### Phase 4.5 : SQL Adapter - Files

**Objectif :** Migrer FileData et UserFileData

**Tâches :**
1. Créer schéma pour files
2. Migrer métadonnées (pas le contenu - déjà dans GCS/S3)
3. Implémenter CRUD pour files
4. Dual-write
5. Basculer

**Durée estimée :** 4-6 semaines

**Risque :** Élevé (volume très important)

### Phase 4.6 : Désactivation Datastore

**Objectif :** Désactiver complètement Datastore

**Tâches :**
1. Valider que tout fonctionne en SQL uniquement
2. Arrêter dual-write
3. Supprimer code Datastore
4. Archiver données Datastore

**Durée estimée :** 2-3 semaines

**Risque :** Moyen (point of no return)

## Durée Totale Estimée

**Optimiste :** 16-24 semaines (4-6 mois)
**Réaliste :** 24-36 semaines (6-9 mois)
**Pessimiste :** 36-52 semaines (9-12 mois)

## Ressources Nécessaires

**Compétences requises :**
- Java enterprise (JPA, Hibernate)
- PostgreSQL / SQL avancé
- Google Cloud Datastore / Objectify
- Migration de données à grande échelle
- Testing et validation

**Infrastructure :**
- Serveur PostgreSQL (ou cloud managed)
- Serveur de staging pour tests
- Outils de migration (batch jobs)
- Monitoring et alerting

## Recommandations

### Stratégie Recommandée

**Strangler Pattern (Option B)** est fortement recommandé pour :
- Réduire les risques
- Permettre rollback à chaque étape
- Maintenir le service en ligne
- Valider progressivement

### Technologies Recommandées

**Base de données :**
- **PostgreSQL** (recommandé)
  - Open-source mature
  - JSONB pour flexibilité
  - Excellent support JPA
  - Scalabilité prouvée

- MySQL (alternative)
- MongoDB (si on veut rester NoSQL)

**ORM :**
- **Hibernate** avec JPA (standard Java)
- Ou **jOOQ** pour queries type-safe

### Points d'Attention

1. **Testez, testez, testez**
   - Tests unitaires pour chaque entité
   - Tests d'intégration
   - Tests de charge
   - Tests de rollback

2. **Monitoring**
   - Logs détaillés pendant dual-write
   - Alertes sur discrepancies
   - Métriques de performance

3. **Documentation**
   - Documenter chaque étape
   - Procédures de rollback
   - Runbooks pour incidents

4. **Communication**
   - Informer les utilisateurs
   - Maintenance windows planifiés
   - Status page

## Conclusion

La Phase 4 est **la plus complexe et la plus longue** de toutes les phases de migration. Elle nécessite :

- **Planning rigoureux**
- **Équipe expérimentée**
- **Temps significatif** (6-12 mois)
- **Budget approprié**
- **Testing exhaustif**

Il est recommandé de :
1. Commencer par une **proof of concept** sur une table simple
2. Valider l'approche avant de se lancer
3. Considérer d'engager des **consultants** si manque d'expertise
4. Prévoir du **temps de buffer** (toujours plus long que prévu)

**Question importante :** Avez-vous vraiment besoin de migrer Datastore ?

Si l'objectif principal est de réduire la dépendance à Google, considérez :
- Garder Datastore (disponible en mode standalone via émulateur)
- Se concentrer sur les autres phases (1-3, 5)
- Datastore n'est pas le plus critique pour l'indépendance

---

**Prochaine étape recommandée :** Créer un **proof of concept** avec une table simple (MOTD) pour valider l'approche avant de s'engager sur la migration complète.

