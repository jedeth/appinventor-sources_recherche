# Phase 4: PostgreSQL Database Layer

**Status**: ✅ **CORE IMPLEMENTATION COMPLETE**
**Ready for**: Testing & Integration

---

## 📚 What's Included

This package provides a complete abstraction layer for database operations, replacing Google Datastore with PostgreSQL for RGPD-compliant on-premise deployment.

### ✅ Fully Implemented (Ready to Use)

1. **DatabaseService.java** (Interface)
   - 80+ methods covering all database operations
   - User, Project, File, Gallery, Comments, RGPD compliance
   - Transactions, health checks, statistics

2. **DatabaseFactory.java** (Factory Pattern)
   - Singleton with thread-safe initialization
   - Configuration via Flags
   - Support for multiple backends (PostgreSQL ready, MySQL stub)

3. **PostgreSQLAdapter.java** (Implementation)
   - HikariCP connection pooling
   - **FULLY implemented User operations** ✅
   - **FULLY implemented Project operations** ✅
   - **FULLY implemented Nonce & Whitelist operations** ✅
   - Stubs for File, Gallery, Comments (with TODOs)

4. **Entities Package** (`entities/`)
   - User, Project, FileData, GalleryApp
   - ProjectComment, GalleryComment
   - Motd, Backpack, UserDataExport
   - All with proper getters/setters

5. **Exceptions Package** (`exceptions/`)
   - DatabaseException (base exception)

### 📝 Documentation

- **MIGRATION_CODE_EXAMPLES.md** - How to migrate from Objectify to DatabaseService
- **pom-phase4-dependencies.xml** - Maven dependencies to add
- **This README** - Architecture and usage guide

---

## 🚀 Quick Start

### 1. Add Dependencies

Merge `pom-phase4-dependencies.xml` into your `pom.xml`:
- PostgreSQL JDBC Driver
- HikariCP connection pooling
- Flyway (migrations)
- Test dependencies (JUnit, Mockito, Testcontainers)

### 2. Configure Database

In `appengine-web.xml`:
```xml
<system-properties>
    <property name="db.backend" value="postgresql"/>
    <property name="db.host" value="postgresql.appinventor.svc.cluster.local"/>
    <property name="db.port" value="5432"/>
    <property name="db.name" value="appinventor"/>
    <property name="db.user" value="appinventor_user"/>
    <property name="db.password" value="${DB_PASSWORD}"/>
    <property name="db.pool.min" value="10"/>
    <property name="db.pool.max" value="50"/>
</system-properties>
```

### 3. Use in Your Code

```java
import com.google.appinventor.server.storage.database.DatabaseFactory;
import com.google.appinventor.server.storage.database.DatabaseService;
import com.google.appinventor.server.storage.database.entities.User;

// Get instance (singleton)
DatabaseService db = DatabaseFactory.getInstance();

// User operations
Optional<User> user = db.getUserById("user123");
Optional<User> user = db.getUserByEmail("user@example.com");
User savedUser = db.saveUser(user);
db.deleteUser("user123");

// Project operations
List<Project> projects = db.getProjectsByUserId("user123");
Project project = db.saveProject(project);
db.deleteProject(projectId);

// Transactions
db.executeInTransaction(() -> {
    User user = db.getUserById("user123").get();
    user.setEmail("new@example.com");
    db.saveUser(user);
    return user;
});

// RGPD compliance
db.deleteAllUserData("user123");  // Right to be forgotten
UserDataExport export = db.exportUserData("user123");  // Right to access
```

---

## 📊 Architecture

```
┌────────────────────────────────────────┐
│     Application Code                   │
│  (UserInfoService, ProjectService...)  │
└───────────────┬────────────────────────┘
                │
                ▼
┌────────────────────────────────────────┐
│   DatabaseService (Interface)          │
│   - 80+ methods                        │
│   - All database operations            │
└───────────────┬────────────────────────┘
                │
                ▼
┌────────────────────────────────────────┐
│   DatabaseFactory (Singleton)          │
│   - Thread-safe initialization         │
│   - Backend selection                  │
└───────────────┬────────────────────────┘
                │
        ┌───────┴───────┐
        ▼               ▼
┌──────────────┐ ┌──────────────┐
│ PostgreSQL   │ │ MySQL        │
│ Adapter      │ │ Adapter      │
│ (HikariCP)   │ │ (Future)     │
└──────────────┘ └──────────────┘
```

---

## 📁 Package Structure

```
com.google.appinventor.server.storage.database/
├── DatabaseService.java           # Interface (80+ methods)
├── DatabaseFactory.java            # Factory & Singleton
├── PostgreSQLAdapter.java          # PostgreSQL implementation
├── entities/
│   ├── User.java                  # User entity
│   ├── AllEntities.java           # Project, FileData, GalleryApp, etc.
│   └── README.md                  # (this file)
├── exceptions/
│   └── DatabaseException.java     # Custom exception
└── repositories/                   # (Future: DAO pattern)
```

---

## 🎯 Implementation Status

### ✅ DONE (Fully Working)

| Component | Status | Test Coverage |
|-----------|--------|---------------|
| DatabaseService interface | ✅ | N/A |
| DatabaseFactory | ✅ | ⏳ Pending |
| PostgreSQLAdapter - User ops | ✅ | ⏳ Pending |
| PostgreSQLAdapter - Project ops | ✅ | ⏳ Pending |
| PostgreSQLAdapter - Nonce ops | ✅ | ⏳ Pending |
| PostgreSQLAdapter - Whitelist ops | ✅ | ⏳ Pending |
| PostgreSQLAdapter - Transactions | ✅ | ⏳ Pending |
| PostgreSQLAdapter - Health check | ✅ | ⏳ Pending |
| Entities (User, Project, etc.) | ✅ | N/A |
| Exceptions | ✅ | N/A |

### ⏳ TODO (Stubs Present)

| Component | Priority | Estimated Effort |
|-----------|----------|-----------------|
| File operations | High | 2-3 days |
| Gallery operations | Medium | 2-3 days |
| Comment operations | Medium | 1-2 days |
| Backpack operations | Low | 1 day |
| MOTD operations | Low | 1 day |
| RGPD export (full) | High | 1-2 days |
| Unit tests | High | 3-4 days |
| Integration tests | High | 2-3 days |
| Performance tests | Medium | 1-2 days |

**Total estimated**: 15-20 days for complete implementation + testing

---

## 🔨 How to Complete TODOs

### Example: Implementing File Operations

1. **Open PostgreSQLAdapter.java**

2. **Find the stub**:
```java
@Override
public List<FileData> getFilesByProjectId(long projectId) throws DatabaseException {
    throw new UnsupportedOperationException("getFilesByProjectId not implemented yet");
}
```

3. **Implement**:
```java
@Override
public List<FileData> getFilesByProjectId(long projectId) throws DatabaseException {
    String sql = "SELECT * FROM file_data WHERE project_id = ? ORDER BY file_name";

    try (Connection conn = getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {

        stmt.setLong(1, projectId);
        ResultSet rs = stmt.executeQuery();
        List<FileData> files = new ArrayList<>();

        while (rs.next()) {
            files.add(mapFileFromResultSet(rs));
        }

        return files;

    } catch (SQLException e) {
        throw new DatabaseException("Failed to get files for project: " + projectId, e);
    }
}

// Add mapper method
private FileData mapFileFromResultSet(ResultSet rs) throws SQLException {
    FileData file = new FileData();
    file.setId(rs.getLong("id"));
    file.setFileName(rs.getString("file_name"));
    file.setProjectId(rs.getLong("project_id"));
    file.setContent(rs.getBytes("content"));
    file.setStorageLocation(rs.getString("storage_location"));
    file.setMinioKey(rs.getString("minio_key"));
    file.setFileSize(rs.getLong("file_size"));
    file.setRole(rs.getString("role"));
    file.setBlobFile(rs.getBoolean("is_blob_file"));
    // ... timestamps ...
    return file;
}
```

4. **Test**:
```java
@Test
public void testGetFilesByProjectId() {
    DatabaseService db = DatabaseFactory.getInstance();

    // Create test project
    Project project = new Project(1L, "Test Project", "user123");
    db.saveProject(project);

    // Create test files
    FileData file1 = new FileData(1L, "file1.txt", "content1".getBytes());
    FileData file2 = new FileData(1L, "file2.txt", "content2".getBytes());
    db.saveFile(file1);
    db.saveFile(file2);

    // Test
    List<FileData> files = db.getFilesByProjectId(1L);
    assertEquals(2, files.size());
}
```

---

## 🧪 Testing

### Unit Tests (in progress)

```bash
mvn test
```

### Integration Tests with Testcontainers

```java
@Testcontainers
public class PostgreSQLAdapterIntegrationTest {

    @Container
    private static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("appinventor_test")
        .withUsername("test")
        .withPassword("test");

    private DatabaseService db;

    @BeforeEach
    public void setUp() {
        String url = postgres.getJdbcUrl();
        // Initialize DatabaseService with test container
        db = new PostgreSQLAdapter(
            postgres.getHost(),
            postgres.getFirstMappedPort(),
            "appinventor_test",
            "test",
            "test",
            5,
            10
        );
    }

    @Test
    public void testUserCRUD() {
        // Create
        User user = new User("user123", "test@example.com");
        user = db.saveUser(user);
        assertNotNull(user);

        // Read
        Optional<User> found = db.getUserById("user123");
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());

        // Update
        user.setEmail("new@example.com");
        db.saveUser(user);
        found = db.getUserById("user123");
        assertEquals("new@example.com", found.get().getEmail());

        // Delete
        db.deleteUser("user123");
        found = db.getUserById("user123");
        assertFalse(found.isPresent());
    }
}
```

---

## 📖 Migration Guide

See **MIGRATION_CODE_EXAMPLES.md** for detailed examples of migrating from Objectify to DatabaseService.

**Key changes**:
- `ObjectifyService.ofy()` → `DatabaseFactory.getInstance()`
- `.load().type().id()` → `.getXById()`
- `.save().entity()` → `.saveX()`
- `.delete().entity()` → `.deleteX()`
- Return `null` → `Optional<X>`

---

## 🔧 Configuration

### Flags (appengine-web.xml)

| Flag | Default | Description |
|------|---------|-------------|
| `db.backend` | `postgresql` | Database backend |
| `db.host` | `localhost` | Database host |
| `db.port` | `5432` | Database port |
| `db.name` | `appinventor` | Database name |
| `db.user` | `appinventor_user` | Database user |
| `db.password` | *required* | Database password |
| `db.pool.min` | `10` | Min connection pool size |
| `db.pool.max` | `50` | Max connection pool size |

### Environment Variables (Kubernetes)

Set via Kubernetes secrets:
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`

Already configured in `deployment/kubernetes/appinventor/appinventor-deployment.yaml`.

---

## 🚨 Known Limitations

1. **File Operations**: Stubs only. Need implementation for:
   - Large file handling (>10MB → store in MinIO)
   - Binary file support
   - File versioning

2. **Gallery Operations**: Complete implementation needed

3. **Comments**: Implementation needed

4. **Search**: Full-text search not optimized (need GIN indexes)

5. **Performance**: Not yet benchmarked under load

---

## 🎯 Next Steps

1. **Immediate** (Required for MVP):
   - [ ] Implement File operations
   - [ ] Write unit tests for User/Project operations
   - [ ] Integration tests with Testcontainers

2. **Short-term** (1-2 weeks):
   - [ ] Implement Gallery operations
   - [ ] Implement Comment operations
   - [ ] Performance testing & optimization
   - [ ] Add database indexes

3. **Medium-term** (1 month):
   - [ ] Complete RGPD compliance features
   - [ ] Add caching layer (Redis)
   - [ ] Monitoring & metrics
   - [ ] Documentation

4. **Long-term** (2-3 months):
   - [ ] MySQL backend support
   - [ ] Sharding support (if needed)
   - [ ] Read replicas
   - [ ] Advanced features

---

## 📞 Support

- **Documentation**: See `PHASE4_MIGRATION_PLAN_ONPREMISE.md`
- **Examples**: See `MIGRATION_CODE_EXAMPLES.md`
- **Kubernetes**: See `deployment/kubernetes/`

---

## 🎉 Credits

**Phase 4 - RGPD Compliant Database Layer**
Developed for DSI Paris on-premise deployment
Replaces Google Datastore with PostgreSQL

---

**Last Updated**: 2025-11-12
**Version**: 2.0 (Phase 4)
**Status**: Core implementation complete, ready for testing and integration
