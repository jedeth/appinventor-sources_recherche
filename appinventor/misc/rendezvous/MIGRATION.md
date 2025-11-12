# Migration vers Infrastructure Indépendante (Google-free)

## Vue d'ensemble

Ce document décrit les modifications apportées au serveur rendezvous de MIT App Inventor pour permettre un déploiement sur une infrastructure indépendante de Google, tout en maintenant la compatibilité avec la version originale du MIT.

## Objectifs

1. ✅ Remplacer Google App Engine Memcache par Redis standalone
2. ✅ Permettre un basculement facile entre Google et infrastructure indépendante
3. ✅ Maintenir la compatibilité pour les futurs merges avec le dépôt MIT
4. ✅ Conserver le code original commenté pour référence

## Modifications effectuées

### 1. Nouveau fichier : `cache-adapter.js`

**Localisation :** `/appinventor/misc/rendezvous/cache-adapter.js`

**Description :** Couche d'abstraction qui permet de basculer entre deux backends de cache :
- **Memcache** : Google App Engine Memcache (original)
- **Redis** : Redis standalone (déploiement indépendant)

**Fonctionnalités :**
- Interface unifiée pour `get()`, `set()`, `delete()`
- Configuration via variables d'environnement
- Gestion de la reconnexion automatique (Redis)
- Compatibilité totale avec l'API Memcache originale

**Variables d'environnement :**
```bash
CACHE_BACKEND=redis           # 'memcache' ou 'redis'
REDIS_HOST=localhost          # Hôte Redis
REDIS_PORT=6379               # Port Redis
REDIS_DB=0                    # Numéro de base Redis
REDIS_PASSWORD=               # Mot de passe Redis (optionnel)
```

### 2. Modifications : `rendezvous.js`

**Localisation :** `/appinventor/misc/rendezvous/rendezvous.js:10-29`

**Changements :**

**AVANT (code original MIT) :**
```javascript
var memcache = require('memcache');
var mc = new memcache.Client();
mc.connect();
```

**APRÈS (code modifié) :**
```javascript
// Original Google App Engine Memcache code (kept for reference and easy rollback):
// var memcache = require('memcache');
// var mc = new memcache.Client();
// mc.connect();

// New unified cache adapter (supports both Memcache and Redis)
var CacheAdapter = require('./cache-adapter');
var mc = new CacheAdapter();
mc.connect();
```

**Note importante :** Le reste du code de `rendezvous.js` n'a **PAS** été modifié. L'adaptateur utilise la même interface que Memcache, donc tous les appels à `mc.get()` et `mc.set()` fonctionnent sans modification.

### 3. Modifications : `package.json`

**Localisation :** `/appinventor/misc/rendezvous/package.json`

**Ajouts :**
- Dépendance `redis` : `^3.1.2`
- Scripts npm :
  - `npm start` : démarre le serveur (backend par défaut)
  - `npm run start:redis` : démarre avec Redis
  - `npm run start:memcache` : démarre avec Memcache
- Version incrémentée : `2.0.0` → `2.1.0`

### 4. Nouveaux fichiers de configuration

#### `.env.example`
Fichier d'exemple de configuration avec tous les paramètres Redis.

#### `.gitignore`
Garantit que le fichier `.env` (contenant des secrets) n'est jamais commité.

## Installation et Déploiement

### Prérequis

- Node.js >= 10.0.0
- Redis server (uniquement pour déploiement indépendant)

### Option 1 : Déploiement avec Redis (Infrastructure indépendante)

#### Étape 1 : Installer Redis

**Ubuntu/Debian :**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis
sudo systemctl enable redis
```

**macOS :**
```bash
brew install redis
brew services start redis
```

**Docker :**
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

#### Étape 2 : Configurer le serveur

```bash
cd appinventor/misc/rendezvous

# Copier le fichier de configuration exemple
cp .env.example .env

# Éditer la configuration
nano .env
```

Exemple de configuration `.env` :
```bash
CACHE_BACKEND=redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DB=0
```

#### Étape 3 : Installer les dépendances

```bash
npm install
```

#### Étape 4 : Démarrer le serveur

```bash
# Utilise la configuration du fichier .env
npm start

# Ou directement avec variables d'environnement
CACHE_BACKEND=redis npm start

# Ou avec le script dédié
npm run start:redis
```

#### Vérification

```bash
# Test du serveur
curl http://localhost:3000/

# Test Redis
redis-cli ping  # doit retourner "PONG"
```

### Option 2 : Déploiement avec Memcache (Google App Engine)

**Aucune modification nécessaire !** Le serveur fonctionne exactement comme avant.

```bash
cd appinventor/misc/rendezvous
npm install
CACHE_BACKEND=memcache npm start
```

Ou simplement déployer sur Google App Engine sans configuration supplémentaire.

### Option 3 : Déploiement Docker

#### Créer `docker-compose.yml` :

```yaml
version: '3.8'

services:
  rendezvous:
    build: .
    ports:
      - "3000:3000"
    environment:
      - CACHE_BACKEND=redis
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_DB=0
    depends_on:
      - redis

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes

volumes:
  redis-data:
```

#### Créer `Dockerfile` :

```dockerfile
FROM node:16-alpine

WORKDIR /app

COPY package*.json ./
RUN npm install --production

COPY . .

EXPOSE 3000

CMD ["node", "rendezvous.js"]
```

#### Lancer :

```bash
docker-compose up -d
```

## Maintenir la Compatibilité avec MIT App Inventor

### Stratégie de Versionnement

Pour permettre des merges faciles avec le dépôt MIT :

1. **Branche principale :** `main` ou `master`
   - Suit le dépôt MIT officiel
   - Pas de modifications Google-free

2. **Branche Google-free :** `google-free` ou `independent`
   - Contient les modifications pour Redis
   - Rebase régulièrement sur `main`

### Procédure de Merge

#### Récupérer les mises à jour du MIT :

```bash
# Ajouter le dépôt MIT comme remote (une seule fois)
git remote add mit https://github.com/mit-cml/appinventor-sources.git

# Récupérer les dernières modifications
git fetch mit

# Merger dans votre branche principale
git checkout main
git merge mit/master

# Rebaser la branche Google-free
git checkout google-free
git rebase main
```

#### Résoudre les conflits potentiels :

Les conflits possibles se limiteront à :
- `misc/rendezvous/rendezvous.js` (lignes 10-29)
- `misc/rendezvous/package.json`

**Résolution :**
1. Garder les modifications Google-free (cache-adapter)
2. Intégrer les autres changements du MIT
3. Vérifier que le code original est toujours commenté pour référence

### Différences avec la Version MIT

**Fichiers modifiés :**
- ✏️ `misc/rendezvous/rendezvous.js` (lignes 10-29)
- ✏️ `misc/rendezvous/package.json`

**Fichiers ajoutés :**
- ➕ `misc/rendezvous/cache-adapter.js`
- ➕ `misc/rendezvous/.env.example`
- ➕ `misc/rendezvous/.gitignore`
- ➕ `misc/rendezvous/MIGRATION.md` (ce fichier)

**Fichiers non modifiés :**
- ✅ `misc/rendezvous/logworker.js` (inchangé)
- ✅ Tous les autres fichiers du dépôt

## Tests et Validation

### Test 1 : Vérifier le backend utilisé

```bash
# Dans rendezvous.js, l'adaptateur affiche le backend au démarrage
CACHE_BACKEND=redis npm start
# Devrait afficher : "Initializing Redis cache backend..."
# Suivi de : "✓ Redis connected successfully"

CACHE_BACKEND=memcache npm start
# Devrait afficher : "Initializing Memcache backend (Google App Engine)..."
# Suivi de : "✓ Memcache connected"
```

### Test 2 : Test de connexion

```bash
# Test de base
curl http://localhost:3000/
# Devrait retourner la page HTML de bienvenue

# Test de l'endpoint rendezvous
curl http://localhost:3000/rendezvous/test
# Devrait retourner : "Connection OK"

# Test de l'endpoint rendezvous2
curl http://localhost:3000/rendezvous2/test
# Devrait retourner : "Connection OK"
```

### Test 3 : Test de stockage/récupération

```bash
# POST avec une clé
curl -X POST http://localhost:3000/rendezvous2/ \
  -H "Content-Type: application/json" \
  -d '{"key":"test123","webrtc":true,"first":true,"apiversion":1}'

# GET pour récupérer la clé (dans les 120 secondes)
curl http://localhost:3000/rendezvous2/test123

# Devrait retourner les données JSON stockées
```

### Test 4 : Vérifier Redis directement

```bash
# Se connecter à Redis
redis-cli

# Lister toutes les clés
KEYS rr2-*

# Voir une clé spécifique (remplacer test123 par votre clé)
GET rr2-test123

# Vérifier le TTL (temps restant avant expiration)
TTL rr2-test123  # devrait être <= 120 secondes
```

### Test 5 : Test de charge (optionnel)

```bash
# Installer Apache Bench
sudo apt install apache2-utils

# Test avec 1000 requêtes, 10 concurrentes
ab -n 1000 -c 10 http://localhost:3000/rendezvous/test

# Comparer les performances Redis vs Memcache
```

## Performances et Optimisation

### Comparaison Redis vs Memcache

| Critère | Redis | Memcache (Google) |
|---------|-------|-------------------|
| **Latence** | ~1ms (local) | ~5-10ms (réseau) |
| **Débit** | >100k ops/sec | Variable |
| **Persistence** | Optionnelle (RDB/AOF) | Non |
| **HA** | Sentinel/Cluster | Automatique (Google) |
| **Coût** | Infrastructure propre | Inclus dans App Engine |

### Recommandations Redis

**Pour la production :**

1. **Persistence** : Activer AOF (Append-Only File)
   ```bash
   # Dans redis.conf
   appendonly yes
   appendfsync everysec
   ```

2. **Haute disponibilité** : Utiliser Redis Sentinel
   ```bash
   # Minimum : 1 master + 2 replicas + 3 sentinels
   ```

3. **Monitoring** : Utiliser Redis INFO
   ```bash
   redis-cli INFO stats
   ```

4. **Limites mémoire** : Configurer maxmemory
   ```bash
   # Dans redis.conf
   maxmemory 256mb
   maxmemory-policy allkeys-lru
   ```

### Configuration Redis Optimale

```bash
# redis.conf pour rendezvous server

# Réseau
bind 0.0.0.0
port 6379
tcp-backlog 511

# Mémoire (ajuster selon vos besoins)
maxmemory 512mb
maxmemory-policy allkeys-lru

# Persistence (optionnelle, rendezvous n'a pas besoin)
save ""
appendonly no

# Performance
tcp-keepalive 300
timeout 0

# Sécurité
requirepass votre_mot_de_passe_fort
```

## Dépannage

### Problème : "Redis connection refused"

**Solution :**
```bash
# Vérifier que Redis est démarré
sudo systemctl status redis

# Vérifier le port
sudo netstat -tlnp | grep 6379

# Vérifier la configuration
redis-cli ping
```

### Problème : "Error: Cannot find module 'redis'"

**Solution :**
```bash
cd appinventor/misc/rendezvous
npm install
```

### Problème : "NOAUTH Authentication required"

**Solution :**
Ajouter le mot de passe dans `.env` :
```bash
REDIS_PASSWORD=votre_mot_de_passe
```

### Problème : Performances lentes avec Redis

**Solutions :**
1. Vérifier la latence réseau : `redis-cli --latency`
2. Vérifier la mémoire disponible : `redis-cli INFO memory`
3. Activer les logs : `redis-cli CONFIG SET loglevel debug`
4. Utiliser Redis sur la même machine que rendezvous

## Migration Complète de l'Infrastructure App Inventor

Ce serveur rendezvous n'est qu'une partie de la migration complète. Pour un déploiement totalement indépendant de Google :

### Composants à Migrer

1. ✅ **Serveur Rendezvous (WebRTC signaling)** - FAIT
   - ✅ Memcache → Redis

2. ⏳ **Serveur App Engine (appengine/)**
   - ⏳ Cloud Datastore → PostgreSQL/MongoDB
   - ⏳ Google Cloud Storage → MinIO/S3
   - ⏳ Blobstore → S3-compatible storage
   - ⏳ App Identity → OAuth2 custom

3. ⏳ **Build Server (buildserver/)**
   - ✅ Déjà indépendant (juste besoin d'un serveur)

4. ⏳ **Stockage Temps Réel**
   - ✅ CloudDB (déjà Redis) - aucune modification
   - ⏳ FirebaseDB → Alternative (Socket.io + Redis / Supabase)

### Ordre de Migration Recommandé

1. ✅ **Phase 1 : Rendezvous Server** (actuel)
2. **Phase 2 : Build Server** (simple, déjà indépendant)
3. **Phase 3 : Stockage de fichiers** (GCS → MinIO/S3)
4. **Phase 4 : Base de données** (Datastore → PostgreSQL/MongoDB)
5. **Phase 5 : Serveur applicatif** (App Engine → Tomcat/Docker)

## Sécurité

### Points d'Attention

1. **Fichier `.env`** : Ne JAMAIS commiter dans Git
2. **Mot de passe Redis** : Utiliser un mot de passe fort en production
3. **Firewall** : Restreindre l'accès au port Redis (6379)
4. **TLS/SSL** : Utiliser Redis avec TLS en production
5. **Réseau** : Isoler Redis dans un réseau privé

### Configuration Redis Sécurisée

```bash
# redis.conf - Production

# Authentification
requirepass VotreMOTdePASSEtresFORTici123!

# Désactiver les commandes dangereux
rename-command FLUSHDB ""
rename-command FLUSHALL ""
rename-command CONFIG ""
rename-command SHUTDOWN ""

# Bind sur interface locale uniquement (si possible)
bind 127.0.0.1

# Ou bind sur IP privée seulement
bind 10.0.0.10
```

## Licence et Crédits

- **Code original** : MIT App Inventor (MIT License)
- **Modifications** : Cache adapter pour Redis (Apache 2.0)
- **Auteur original** : Jeffrey I. Schiller <jis@mit.edu>
- **Migration Google-free** : 2024

## Support et Contribution

Pour des questions ou contributions :
1. Ouvrir une issue sur votre dépôt Git
2. Consulter la documentation MIT App Inventor
3. Vérifier ce document MIGRATION.md

## Changelog

### Version 2.1.0 (2024)
- ✅ Ajout de cache-adapter.js pour abstraction cache
- ✅ Support de Redis standalone
- ✅ Compatibilité maintenue avec Memcache Google
- ✅ Configuration via variables d'environnement
- ✅ Documentation complète de migration

### Version 2.0.0 (Original MIT)
- Version originale avec Google App Engine Memcache

## Ressources Utiles

- [Redis Documentation](https://redis.io/documentation)
- [MIT App Inventor Sources](https://github.com/mit-cml/appinventor-sources)
- [Node.js Redis Client](https://github.com/NodeRedis/node-redis)
- [WebRTC Documentation](https://webrtc.org/)

---

**Note finale :** Ces modifications sont conçues pour être **minimales** et **réversibles**, afin de faciliter les futurs merges avec le dépôt MIT officiel. Le code original est conservé en commentaires et peut être restauré en quelques minutes si nécessaire.
