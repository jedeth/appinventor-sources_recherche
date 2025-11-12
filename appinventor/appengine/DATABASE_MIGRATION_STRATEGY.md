# Stratégie de Migration de Base de Données - Phase 4

## Résumé Exécutif

**Objectif :** Migrer de Google Cloud Datastore vers PostgreSQL pour indépendance complète

**Complexité :** ⚠️ **TRÈS ÉLEVÉE** (6-12 mois de travail)

**Recommandation :** **Approche Progressive (Strangler Pattern)**

## Approche Recommandée : Strangler Pattern

### Principe

Remplacer progressivement Datastore par SQL, table par table, sans downtime.

```
Phase actuelle:  ObjectifyStorageIo → Objectify → Datastore
                          ↓
Phase transition: ObjectifyStorageIo → DatabaseService (interface)
                                              ↓
                                    +---------+---------+
                                    ↓                   ↓
                            DatastoreAdapter      SQLAdapter
                                    ↓                   ↓
                                Datastore          PostgreSQL
                          ↓
Phase finale:    ObjectifyStorageIo → DatabaseService → PostgreSQL
```

### Phases d'Implémentation

#### **Phase 4.1 : Abstraction (1-2 mois)**

**Objectif :** Créer la couche d'abstraction

**Livrables :**
1. Interface `DatabaseService`
2. `DatastoreAdapter` (wrapper Objectify - aucun changement fonctionnel)
3. `DatabaseFactory` (sélection backend)
4. Tests de non-régression

**Code estimé :** ~2000 lignes

**Risque :** ⚠️ Faible

#### **Phase 4.2 : Tables Simples (2-3 mois)**

**Objectif :** Prouver le concept avec tables simples

**Tables à migrer :**
- MOTD (Message of the Day)
- Splash (Splash screen config)
- Whitelist (Email whitelist)
- AllowedTutorialUrls
- AllowedIosExtensions

**Processus :**
1. Créer schéma SQL
2. Implémenter `SQLAdapter` pour ces tables
3. **Dual-write** : écrire dans Datastore ET SQL
4. Valider équivalence des données
5. Basculer lecture depuis SQL
6. Monitoring 2 semaines
7. Arrêter écriture Datastore

**Risque :** ⚠️ Faible (peu de données, peu de queries)

#### **Phase 4.3 : Users (3-4 mois)**

**Objectif :** Migrer la table Users (critique)

**Défis :**
- Volume : millions d'utilisateurs potentiels
- Queries par email (login)
- Sessions actives
- Authentification

**Processus :**
1. Créer schéma SQL users
2. **Migration des données existantes** (batch, hors ligne)
3. Implémenter CRUD SQL
4. Dual-write (Datastore + SQL)
5. Validation extensive (2-4 semaines)
6. Basculer progressivement (par région ou % users)
7. Monitoring intensif
8. Rollback plan prêt

**Risque :** ⚠️⚠️ Moyen-Élevé

#### **Phase 4.4 : Projects et Relations (4-6 mois)**

**Objectif :** Migrer Projects et UserProjects

**Défis :**
- Volume : millions de projets
- Relations many-to-many (UserProjects)
- Queries complexes
- Transactions

**Processus :**
1. Schéma SQL projects + user_projects
2. Migration batch des données
3. Implémenter CRUD + relations SQL
4. Dual-write avec validation
5. Tests de charge
6. Basculement progressif
7. Validation finale

**Risque :** ⚠️⚠️⚠️ Élevé

#### **Phase 4.5 : Files (4-6 mois)**

**Objectif :** Migrer FileData et UserFileData

**Défis :**
- Volume TRÈS élevé (dizaines de millions)
- Métadonnées uniquement (contenu dans GCS/S3)
- Relations avec Projects
- Queries fréquentes

**Processus :**
1. Schéma SQL files + user_files
2. Migration batch (plus longue)
3. Implémenter CRUD SQL
4. Dual-write
5. Tests de performance
6. Basculement
7. Cleanup

**Risque :** ⚠️⚠️⚠️ Élevé

#### **Phase 4.6 : Autres Tables (2-3 mois)**

**Tables restantes :**
- RendezvousData
- FeedbackData
- NonceData
- CorruptionRecord
- PWData
- Backpack

**Processus :** Similaire à Phase 4.2 mais en batch

**Risque :** ⚠️ Faible-Moyen

#### **Phase 4.7 : Désactivation Datastore (1-2 mois)**

**Objectif :** Nettoyage final

**Tâches :**
1. Valider 100% sur SQL
2. Arrêter dual-write complètement
3. Supprimer code Datastore
4. Archiver données Datastore (backup)
5. Documentation finale

**Risque :** ⚠️⚠️ Moyen (point of no return)

## Durée Totale et Ressources

### Timeline

| Phase | Durée Optimiste | Durée Réaliste | Durée Pessimiste |
|-------|-----------------|----------------|------------------|
| 4.1 Abstraction | 1 mois | 2 mois | 3 mois |
| 4.2 Tables simples | 2 mois | 3 mois | 4 mois |
| 4.3 Users | 3 mois | 4 mois | 6 mois |
| 4.4 Projects | 4 mois | 5 mois | 8 mois |
| 4.5 Files | 4 mois | 6 mois | 10 mois |
| 4.6 Autres | 2 mois | 3 mois | 4 mois |
| 4.7 Cleanup | 1 mois | 2 mois | 3 mois |
| **TOTAL** | **17 mois** | **25 mois** | **38 mois** |

**Réaliste avec équipe dédiée :** **25 mois (2 ans)**

### Équipe Recommandée

**Minimum viable :**
- 1 Tech Lead (expert Java/SQL)
- 2 Développeurs Senior (Java, SQL, Datastore)
- 1 DBA (PostgreSQL expert)
- 1 DevOps (infrastructure, monitoring)
- 1 QA Engineer (tests, validation)

**Total :** 6 personnes à temps partiel ou 3-4 à temps plein

### Budget Estimé

**Salaires :** 6 personnes × 25 mois × coût moyen
**Infrastructure :** PostgreSQL managed, staging, monitoring
**Outils :** Migration tools, backup, monitoring
**Contingence :** 20-30%

**Ordre de grandeur :** 500K€ - 1M€

## Technologies Recommandées

### Base de Données

**PostgreSQL 15+** (fortement recommandé)

**Avantages :**
- ✅ Open-source mature
- ✅ JSONB pour colonnes flexibles
- ✅ Excellent support JPA/Hibernate
- ✅ Scalable (partitioning, replication)
- ✅ Rich ecosystem
- ✅ ACID transactions robustes
- ✅ Performance éprouvée

**Configuration recommandée :**
- Cloud Managed (AWS RDS, Azure Database, Google Cloud SQL)
- Ou self-hosted avec HA (Patroni, pgpool)

**Alternatives :**
- MySQL 8+ (si préférence)
- MongoDB (si on veut rester NoSQL)

### ORM et Accès Données

**Hibernate 6+ avec JPA 3.0** (recommandé)

**Avantages :**
- ✅ Standard Java
- ✅ Migrations via Flyway/Liquibase
- ✅ Caching (2nd level cache)
- ✅ Lazy loading
- ✅ Transactions déclaratives

**Alternative :**
- jOOQ (type-safe SQL queries)

### Migration de Données

**Outils :**
- Apache Beam / Dataflow (pour batch processing)
- Custom Java jobs
- pgloader (Datastore → PostgreSQL)

### Monitoring

**Stack recommandée :**
- PostgreSQL : pgAdmin, pg_stat_statements
- Application : Prometheus + Grafana
- Logs : ELK stack ou CloudWatch
- Alerting : PagerDuty, Opsgenie

## Risques et Mitigation

### Risque 1 : Perte de Données

**Probabilité :** Faible
**Impact :** Critique

**Mitigation :**
- Dual-write obligatoire
- Validation croisée continue
- Backups quotidiens
- Rollback plan documenté

### Risque 2 : Downtime Prolongé

**Probabilité :** Moyenne
**Impact :** Élevé

**Mitigation :**
- Migration progressive par phase
- Basculement par feature flag
- Blue-green deployment
- Rollback automatisé

### Risque 3 : Performance Dégradée

**Probabilité :** Moyenne
**Impact :** Élevé

**Mitigation :**
- Load testing avant chaque phase
- Index appropriés
- Query optimization (EXPLAIN ANALYZE)
- Caching (Redis)
- Connection pooling

### Risque 4 : Dépassement Budget/Délais

**Probabilité :** Élevée
**Impact :** Élevé

**Mitigation :**
- Planning détaillé par phase
- Revues régulières (bi-weekly)
- Buffer 30% dans timeline
- Priorisation stricte
- Option de pause entre phases

### Risque 5 : Perte d'Expertise

**Probabilité :** Moyenne
**Impact :** Élevé

**Mitigation :**
- Documentation exhaustive
- Knowledge sharing sessions
- Pair programming
- Code reviews systématiques
- Contractors avec transfert de connaissance

## Critères de Succès

### Par Phase

**Chaque phase doit valider :**
- ✅ Pas de régression fonctionnelle
- ✅ Performance équivalente ou meilleure
- ✅ 100% équivalence des données (dual-write)
- ✅ Rollback testé et fonctionnel
- ✅ Documentation à jour
- ✅ Équipe formée sur nouveaux composants

### Global (Phase 4 complète)

- ✅ 0% dépendance à Google Cloud Datastore
- ✅ Performance maintenue ou améliorée
- ✅ Coûts infrastructure réduits ou équivalents
- ✅ Maintenance simplifiée (SQL standard)
- ✅ Scalabilité prouvée
- ✅ Équipe autonome sur la stack

## Alternatives à Considérer

### Alternative 1 : Garder Datastore

**Option :** Ne pas migrer Datastore, se concentrer sur phases 1-3-5

**Avantages :**
- Économise 2 ans de développement
- Pas de risque de migration
- Datastore fonctionne bien

**Inconvénients :**
- Dépendance Google reste
- Coûts Google Cloud
- Émulateur Datastore pour local seulement

**Verdict :** ⭐ **À considérer sérieusement**

### Alternative 2 : Datastore Emulator

**Option :** Utiliser l'émulateur Datastore pour déploiement local

**Avantages :**
- Code inchangé
- Pas de migration
- Local/on-premise possible

**Inconvénients :**
- Émulateur pas production-ready
- Performance limitée
- Pas de support officiel

**Verdict :** ⚠️ Risqué pour production

### Alternative 3 : Cloud Spanner

**Option :** Migrer vers Cloud Spanner (au lieu de SQL)

**Avantages :**
- Google Cloud (si acceptable)
- Scalabilité infinie
- ACID + NoSQL

**Inconvénients :**
- Toujours Google-dépendant
- Coûts très élevés
- Migration quand même nécessaire

**Verdict :** ❌ Ne résout pas l'objectif d'indépendance

### Alternative 4 : MongoDB

**Option :** NoSQL → NoSQL (Datastore → MongoDB)

**Avantages :**
- Concepts similaires
- Migration potentiellement plus simple
- Open-source

**Inconvénients :**
- Pas de relations SQL natives
- Objectify n'existe pas pour MongoDB
- Réécriture queries quand même

**Verdict :** 🤔 Option viable mais travail similaire

## Recommandation Finale

### Si Objectif = Indépendance Totale

**Procéder avec Phase 4** (PostgreSQL) selon planning Strangler Pattern

**Prérequis :**
- ✅ Budget disponible (~500K€-1M€)
- ✅ Équipe expérimentée (ou contractors)
- ✅ Timeline réaliste (2-3 ans)
- ✅ Engagement management
- ✅ Phases 1-3 terminées

### Si Objectif = Indépendance Pragmatique

**Ne PAS faire Phase 4**, se concentrer sur :

1. ✅ **Phase 1** : Rendezvous (Redis) - FAIT
2. ✅ **Phase 3** : Storage (S3/MinIO) - FAIT
3. ⏳ **Phase 5** : Application Server (Docker/K8s)
4. ⏳ **Services** : Task Queue, Cron, Mail

**Garder Datastore** car :
- Fonctionne bien
- Pas critique pour indépendance
- Économise 2 ans + 500K€-1M€
- Peut être fait plus tard si vraiment nécessaire

**Verdict Recommandé :** ⭐ **Approche Pragmatique**

## Prochaines Actions

### Si Décision = Procéder avec Phase 4

1. **Proof of Concept (2 semaines)**
   - Migrer table MOTD vers PostgreSQL
   - Valider l'approche DatabaseService
   - Tester dual-write et basculement

2. **Planning détaillé (1 mois)**
   - Breakdown détaillé de toutes les phases
   - Estimation précise par composant
   - Identification équipe et ressources

3. **Phase 4.1 : Abstraction (2 mois)**
   - Implémenter couche d'abstraction
   - Tests exhaustifs
   - Revue de code

### Si Décision = Approche Pragmatique

1. **Terminer Phase 3** (si pas encore fait)
   - Modifier ObjectifyStorageIo.java
   - Tests complets
   - Déploiement

2. **Phase 5 : Application Server**
   - Containerisation (Docker)
   - Orchestration (Kubernetes)
   - Services (Queue, Cron, Mail)

3. **Documentation finale**
   - Architecture complète
   - Deployment guides
   - Runbooks

## Conclusion

La **Phase 4 est la plus complexe** de toutes les phases de migration. Elle nécessite un investissement significatif en temps, ressources, et expertise.

**Question clé :** **Est-ce vraiment nécessaire ?**

Dans de nombreux cas, l'**approche pragmatique** (garder Datastore, migrer le reste) offre un **meilleur ROI** :
- 90% d'indépendance Google
- 10% du coût et du temps
- Risque minimal

La décision doit être prise en fonction de :
- Contraintes budgétaires
- Timeline acceptable
- Expertise disponible
- Criticité de l'indépendance totale

---

**Auteur :** MIT App Inventor Migration Team
**Date :** 2024
**Version :** 1.0 - Phase 4 Strategy
