# Phase 4 - Exemples de Migration de Code

Ce document montre comment migrer le code existant d'Objectify/Datastore vers le nouveau DatabaseService (PostgreSQL).

---

## Vue d'Ensemble

**Avant (Objectify)**:
```java
import com.googlecode.objectify.ObjectifyService;

// Query
UserData user = ObjectifyService.ofy().load().type(UserData.class).filter("email", email).first().now();

// Save
ObjectifyService.ofy().save().entity(user).now();

// Delete
ObjectifyService.ofy().delete().entity(user).now();
```

**Après (DatabaseService)**:
```java
import com.google.appinventor.server.storage.database.DatabaseFactory;
import com.google.appinventor.server.storage.database.DatabaseService;

DatabaseService db = DatabaseFactory.getInstance();

// Query
Optional<User> user = db.getUserByEmail(email);

// Save
db.saveUser(user);

// Delete
db.deleteUser(userId);
```

---

## Exemple 1: User Operations (UserInfoService.java)

### AVANT (Objectify)

```java
package com.google.appinventor.server;

import com.googlecode.objectify.ObjectifyService;
import com.google.appinventor.shared.rpc.user.UserInfoProvider;
import com.google.appinventor.server.storage.UserData;

public class UserInfoServiceImpl implements UserInfoProvider {

    public User getUser(String userId) {
        UserData userData = ObjectifyService.ofy()
            .load()
            .type(UserData.class)
            .id(userId)
            .now();

        if (userData == null) {
            return null;
        }

        return new User(
            userData.getId(),
            userData.getEmail(),
            userData.getIsAdmin()
        );
    }

    public User createUser(String email) {
        UserData userData = new UserData();
        userData.setId(generateUserId());
        userData.setEmail(email);
        userData.setTosAccepted(false);

        ObjectifyService.ofy().save().entity(userData).now();

        return new User(userData.getId(), userData.getEmail(), false);
    }

    public void updateUserSettings(String userId, String settings) {
        UserData userData = ObjectifyService.ofy()
            .load()
            .type(UserData.class)
            .id(userId)
            .now();

        if (userData != null) {
            userData.setSettings(settings);
            ObjectifyService.ofy().save().entity(userData).now();
        }
    }

    public void deleteUser(String userId) {
        UserData userData = ObjectifyService.ofy()
            .load()
            .type(UserData.class)
            .id(userId)
            .now();

        if (userData != null) {
            ObjectifyService.ofy().delete().entity(userData).now();
        }
    }
}
```

### APRÈS (DatabaseService)

```java
package com.google.appinventor.server;

import com.google.appinventor.server.storage.database.DatabaseFactory;
import com.google.appinventor.server.storage.database.DatabaseService;
import com.google.appinventor.server.storage.database.entities.User;
import com.google.appinventor.shared.rpc.user.UserInfoProvider;

public class UserInfoServiceImpl implements UserInfoProvider {

    private final DatabaseService db = DatabaseFactory.getInstance();

    public com.google.appinventor.shared.rpc.user.User getUser(String userId) {
        Optional<User> userOpt = db.getUserById(userId);

        if (!userOpt.isPresent()) {
            return null;
        }

        User user = userOpt.get();
        return new com.google.appinventor.shared.rpc.user.User(
            user.getId(),
            user.getEmail(),
            user.isAdmin()
        );
    }

    public com.google.appinventor.shared.rpc.user.User createUser(String email) {
        User user = new User(generateUserId(), email);
        user.setTosAccepted(false);

        user = db.saveUser(user);

        return new com.google.appinventor.shared.rpc.user.User(
            user.getId(),
            user.getEmail(),
            false
        );
    }

    public void updateUserSettings(String userId, String settings) {
        Optional<User> userOpt = db.getUserById(userId);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setSettingsJson(settings);
            db.saveUser(user);
        }
    }

    public void deleteUser(String userId) {
        // RGPD Compliance - delete all user data
        db.deleteAllUserData(userId);
    }
}
```

**Changements clés**:
- ✅ `ObjectifyService.ofy()` → `DatabaseFactory.getInstance()`
- ✅ `.load().type()` → `.getUserById()` / `.getUserByEmail()`
- ✅ `.save().entity()` → `.saveUser()`
- ✅ `.delete().entity()` → `.deleteUser()`
- ✅ Retour `null` → `Optional<User>`

---

## Exemple 2: Project Operations (ProjectService.java)

### AVANT

```java
import com.googlecode.objectify.ObjectifyService;
import com.google.appinventor.server.storage.ProjectData;

public class ProjectServiceImpl {

    public List<Project> getUserProjects(String userId) {
        List<ProjectData> projectDataList = ObjectifyService.ofy()
            .load()
            .type(ProjectData.class)
            .filter("ownerId", userId)
            .list();

        return projectDataList.stream()
            .map(this::convertToProject)
            .collect(Collectors.toList());
    }

    public void createProject(String userId, String projectName) {
        ProjectData projectData = new ProjectData();
        projectData.setId(generateProjectId());
        projectData.setProjectName(projectName);
        projectData.setOwnerId(userId);
        projectData.setDateCreated(System.currentTimeMillis());
        projectData.setDateModified(System.currentTimeMillis());

        ObjectifyService.ofy().save().entity(projectData).now();
    }

    public void updateProject(long projectId, String newName) {
        ProjectData projectData = ObjectifyService.ofy()
            .load()
            .type(ProjectData.class)
            .id(projectId)
            .now();

        if (projectData != null) {
            projectData.setProjectName(newName);
            projectData.setDateModified(System.currentTimeMillis());
            ObjectifyService.ofy().save().entity(projectData).now();
        }
    }

    public void deleteProject(long projectId) {
        // Delete files first
        List<FileData> files = ObjectifyService.ofy()
            .load()
            .type(FileData.class)
            .filter("projectId", projectId)
            .list();

        ObjectifyService.ofy().delete().entities(files).now();

        // Then delete project
        ProjectData projectData = ObjectifyService.ofy()
            .load()
            .type(ProjectData.class)
            .id(projectId)
            .now();

        if (projectData != null) {
            ObjectifyService.ofy().delete().entity(projectData).now();
        }
    }
}
```

### APRÈS

```java
import com.google.appinventor.server.storage.database.DatabaseFactory;
import com.google.appinventor.server.storage.database.DatabaseService;
import com.google.appinventor.server.storage.database.entities.Project;

public class ProjectServiceImpl {

    private final DatabaseService db = DatabaseFactory.getInstance();

    public List<com.google.appinventor.shared.rpc.project.Project> getUserProjects(String userId) {
        List<Project> projects = db.getProjectsByUserId(userId);

        return projects.stream()
            .map(this::convertToProject)
            .collect(Collectors.toList());
    }

    public void createProject(String userId, String projectName) {
        Project project = new Project();
        project.setProjectId(generateProjectId());
        project.setProjectName(projectName);
        project.setOwnerId(userId);
        long now = System.currentTimeMillis();
        project.setDateCreated(now);
        project.setDateModified(now);

        db.saveProject(project);
    }

    public void updateProject(long projectId, String newName) {
        Optional<Project> projectOpt = db.getProjectById(projectId);

        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            project.setProjectName(newName);
            project.setDateModified(System.currentTimeMillis());
            db.saveProject(project);
        }
    }

    public void deleteProject(long projectId) {
        // Files will be cascade deleted by database foreign key
        db.deleteProject(projectId);
    }
}
```

**Simplifications**:
- ✅ Pas besoin de delete files manuellement (cascade automatique)
- ✅ `.filter()` → méthode dédiée `.getProjectsByUserId()`
- ✅ Code plus simple et lisible

---

## Exemple 3: Session Management (LocalUser.java)

### AVANT

```java
import com.googlecode.objectify.ObjectifyService;

public class LocalUser {

    public static String getCurrentUserId() {
        HttpSession session = getRequest().getSession();
        String sessionId = session.getId();

        UserData userData = ObjectifyService.ofy()
            .load()
            .type(UserData.class)
            .filter("sessionId", sessionId)
            .first()
            .now();

        return (userData != null) ? userData.getId() : null;
    }

    public static void setCurrentUser(String userId) {
        HttpSession session = getRequest().getSession();
        String sessionId = session.getId();

        UserData userData = ObjectifyService.ofy()
            .load()
            .type(UserData.class)
            .id(userId)
            .now();

        if (userData != null) {
            userData.setSessionId(sessionId);
            userData.setSessionStartDate(System.currentTimeMillis());
            ObjectifyService.ofy().save().entity(userData).now();
        }
    }

    public static void logout() {
        String userId = getCurrentUserId();
        if (userId != null) {
            UserData userData = ObjectifyService.ofy()
                .load()
                .type(UserData.class)
                .id(userId)
                .now();

            if (userData != null) {
                userData.setSessionId(null);
                userData.setSessionStartDate(null);
                ObjectifyService.ofy().save().entity(userData).now();
            }
        }

        getRequest().getSession().invalidate();
    }
}
```

### APRÈS

```java
import com.google.appinventor.server.storage.database.DatabaseFactory;
import com.google.appinventor.server.storage.database.DatabaseService;
import com.google.appinventor.server.storage.database.entities.User;

public class LocalUser {

    private static final DatabaseService db = DatabaseFactory.getInstance();

    public static String getCurrentUserId() {
        HttpSession session = getRequest().getSession();
        String sessionId = session.getId();

        Optional<User> userOpt = db.getUserBySessionId(sessionId);
        return userOpt.map(User::getId).orElse(null);
    }

    public static void setCurrentUser(String userId) {
        HttpSession session = getRequest().getSession();
        String sessionId = session.getId();
        long timestamp = System.currentTimeMillis();

        db.updateUserSession(userId, sessionId, timestamp);
    }

    public static void logout() {
        String userId = getCurrentUserId();
        if (userId != null) {
            db.clearUserSession(userId);
        }

        getRequest().getSession().invalidate();
    }
}
```

**Avantages**:
- ✅ Code beaucoup plus court
- ✅ Méthodes dédiées pour session management
- ✅ Pas besoin de charger/sauver l'entité complète

---

## Exemple 4: Queries Complexes

### AVANT - Query avec filtres multiples

```java
List<ProjectData> recentProjects = ObjectifyService.ofy()
    .load()
    .type(ProjectData.class)
    .filter("ownerId", userId)
    .filter("projectType", "Project")
    .order("-dateModified")
    .limit(10)
    .list();
```

### APRÈS - Utiliser méthode dédiée ou custom query

```java
// Option 1: Méthode dédiée (si elle existe)
List<Project> recentProjects = db.getProjectsByUserId(userId)
    .stream()
    .filter(p -> "Project".equals(p.getProjectType()))
    .sorted((a, b) -> Long.compare(b.getDateModified(), a.getDateModified()))
    .limit(10)
    .collect(Collectors.toList());

// Option 2: Ajouter méthode custom à DatabaseService
// Dans DatabaseService.java:
//   List<Project> getRecentProjects(String userId, String type, int limit);

// Dans PostgreSQLAdapter.java:
public List<Project> getRecentProjects(String userId, String type, int limit) {
    String sql = "SELECT * FROM projects WHERE owner_id = ? AND project_type = ? " +
                "ORDER BY date_modified DESC LIMIT ?";

    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setString(1, userId);
        stmt.setString(2, type);
        stmt.setInt(3, limit);

        ResultSet rs = stmt.executeQuery();
        List<Project> projects = new ArrayList<>();

        while (rs.next()) {
            projects.add(mapProjectFromResultSet(rs));
        }

        return projects;

    } catch (SQLException e) {
        throw new DatabaseException("Failed to get recent projects", e);
    }
}
```

---

## Exemple 5: Transactions

### AVANT

```java
import com.googlecode.objectify.Work;

public void transferProject(String fromUserId, String toUserId, long projectId) {
    ObjectifyService.ofy().transact(new Work<Void>() {
        public Void run() {
            ProjectData project = ObjectifyService.ofy()
                .load()
                .type(ProjectData.class)
                .id(projectId)
                .now();

            if (project != null && project.getOwnerId().equals(fromUserId)) {
                project.setOwnerId(toUserId);
                ObjectifyService.ofy().save().entity(project).now();
            }

            return null;
        }
    });
}
```

### APRÈS

```java
public void transferProject(String fromUserId, String toUserId, long projectId) {
    try {
        db.executeInTransactionVoid(() -> {
            Optional<Project> projectOpt = db.getProjectById(projectId);

            if (projectOpt.isPresent()) {
                Project project = projectOpt.get();

                if (project.getOwnerId().equals(fromUserId)) {
                    project.setOwnerId(toUserId);
                    db.saveProject(project);
                }
            }
        });
    } catch (Exception e) {
        throw new RuntimeException("Failed to transfer project", e);
    }
}
```

**Avec retour de valeur**:
```java
public boolean transferProject(String fromUserId, String toUserId, long projectId) {
    try {
        return db.executeInTransaction(() -> {
            Optional<Project> projectOpt = db.getProjectById(projectId);

            if (projectOpt.isPresent()) {
                Project project = projectOpt.get();

                if (project.getOwnerId().equals(fromUserId)) {
                    project.setOwnerId(toUserId);
                    db.saveProject(project);
                    return true;
                }
            }

            return false;
        });
    } catch (Exception e) {
        throw new RuntimeException("Failed to transfer project", e);
    }
}
```

---

## Checklist de Migration

Pour migrer un fichier Java:

1. **Imports**
   - [ ] Supprimer `import com.googlecode.objectify.*;`
   - [ ] Ajouter `import com.google.appinventor.server.storage.database.*;`

2. **Initialisation**
   - [ ] Remplacer `ObjectifyService.ofy()` par `DatabaseFactory.getInstance()`
   - [ ] Stocker dans une variable: `DatabaseService db = DatabaseFactory.getInstance();`

3. **Load/Get Operations**
   - [ ] `.load().type(X.class).id()` → `.getXById()`
   - [ ] `.load().type(X.class).filter()` → méthode dédiée
   - [ ] Gérer `Optional<>` au lieu de `null`

4. **Save Operations**
   - [ ] `.save().entity()` → `.saveX()`

5. **Delete Operations**
   - [ ] `.delete().entity()` → `.deleteX()`

6. **Queries**
   - [ ] Vérifier si méthode dédiée existe
   - [ ] Sinon, ajouter méthode custom à DatabaseService

7. **Transactions**
   - [ ] `.transact()` → `.executeInTransaction()` ou `.executeInTransactionVoid()`

8. **Tests**
   - [ ] Mettre à jour les tests unitaires
   - [ ] Tester avec vraie base PostgreSQL

---

## Configuration

### appengine-web.xml

```xml
<appengine-web-app>
    <!-- Existing config... -->

    <system-properties>
        <!-- Phase 4: Database Configuration -->
        <property name="db.backend" value="postgresql"/>
        <property name="db.host" value="postgresql.appinventor.svc.cluster.local"/>
        <property name="db.port" value="5432"/>
        <property name="db.name" value="appinventor"/>
        <property name="db.user" value="appinventor_user"/>
        <property name="db.password" value="${DB_PASSWORD}"/>  <!-- From environment -->
        <property name="db.pool.min" value="10"/>
        <property name="db.pool.max" value="50"/>

        <!-- Or use old backend temporarily -->
        <!-- <property name="db.backend" value="datastore"/> -->
    </system-properties>
</appengine-web-app>
```

### Environment Variables (Kubernetes)

Déjà configuré dans `appinventor-deployment.yaml`:
```yaml
env:
  - name: DB_HOST
    value: "postgresql.appinventor.svc.cluster.local"
  - name: DB_USER
    valueFrom:
      secretKeyRef:
        name: postgresql-credentials
        key: appinventor-user
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: postgresql-credentials
        key: appinventor-password
```

---

## Stratégie de Migration Graduelle

### Phase 1: Ajouter le nouveau code (sans casser l'ancien)

```java
// Garder l'ancien code commenté
// UserData userData = ObjectifyService.ofy().load().type(UserData.class).id(userId).now();

// Nouveau code
DatabaseService db = DatabaseFactory.getInstance();
Optional<User> userOpt = db.getUserById(userId);
User user = userOpt.orElse(null);
```

### Phase 2: Tester en parallèle

```java
// Test: comparer les deux backends
UserData oldUser = ObjectifyService.ofy().load().type(UserData.class).id(userId).now();
Optional<User> newUser = db.getUserById(userId);

if (!usersAreEqual(oldUser, newUser.orElse(null))) {
    LOG.warning("Mismatch between Datastore and PostgreSQL for user: " + userId);
}
```

### Phase 3: Basculer complètement

```java
// Supprimer l'ancien code
DatabaseService db = DatabaseFactory.getInstance();
Optional<User> userOpt = db.getUserById(userId);
```

---

## Fichiers Prioritaires à Migrer

1. **UserInfoServiceImpl.java** - Operations utilisateur
2. **ProjectServiceImpl.java** - Operations projet
3. **StorageIo.java** - Operations fichiers
4. **LocalUser.java** - Session management
5. **GalleryServiceImpl.java** - Gallery operations

---

## Support

Si vous rencontrez un pattern Objectify non couvert:
1. Vérifiez si une méthode équivalente existe dans `DatabaseService`
2. Sinon, ajoutez-la à `DatabaseService.java` et `PostgreSQLAdapter.java`
3. Ou utilisez une approche fonctionnelle (stream, filter, etc.)

**Questions?** Consultez `PHASE4_MIGRATION_PLAN_ONPREMISE.md` pour plus de détails.
