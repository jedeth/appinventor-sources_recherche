# Quick Start - Rendezvous Server avec Redis

Ce guide permet de démarrer le serveur rendezvous en 5 minutes avec Redis (Google-free).

## 🚀 Démarrage Rapide avec Docker Compose (Recommandé)

### 1. Créer les répertoires de données

```bash
cd appinventor/misc/rendezvous
mkdir -p data redis-data
```

### 2. Configurer (optionnel)

```bash
# Copier le fichier d'exemple
cp .env.example .env

# Éditer si besoin (optionnel pour un démarrage simple)
nano .env
```

Configuration minimale (`.env`) :
```bash
CACHE_BACKEND=redis
REDIS_HOST=redis
```

### 3. Démarrer les services

```bash
docker-compose up -d
```

### 4. Vérifier que tout fonctionne

```bash
# Vérifier les services
docker-compose ps

# Tester le serveur
curl http://localhost:3000/
curl http://localhost:3000/rendezvous/test
curl http://localhost:3000/rendezvous2/test

# Voir les logs
docker-compose logs -f
```

✅ **Vous êtes prêt !** Le serveur tourne sur http://localhost:3000

---

## 🐳 Démarrage avec Docker (sans Compose)

### 1. Démarrer Redis

```bash
docker run -d --name redis \
  -v $(pwd)/redis-data:/data \
  redis:7-alpine \
  redis-server --appendonly yes
```

### 2. Construire l'image rendezvous

```bash
docker build -f Dockerfile.new -t appinventor-rendezvous .
```

### 3. Démarrer le serveur rendezvous

```bash
docker run -d --name rendezvous \
  --link redis:redis \
  -e CACHE_BACKEND=redis \
  -e REDIS_HOST=redis \
  -p 3000:3000 \
  -v $(pwd)/data:/data \
  appinventor-rendezvous
```

### 4. Tester

```bash
curl http://localhost:3000/rendezvous/test
```

---

## 💻 Démarrage sans Docker (Node.js local)

### Prérequis

- Node.js >= 10
- Redis installé et démarré

### 1. Installer Redis

**Ubuntu/Debian :**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis
```

**macOS :**
```bash
brew install redis
brew services start redis
```

**Vérifier :**
```bash
redis-cli ping  # doit retourner "PONG"
```

### 2. Installer les dépendances Node.js

```bash
cd appinventor/misc/rendezvous
npm install
```

### 3. Configurer

```bash
cp .env.example .env
```

Éditer `.env` :
```bash
CACHE_BACKEND=redis
REDIS_HOST=localhost
REDIS_PORT=6379
```

### 4. Démarrer le serveur

```bash
npm start
```

Ou directement :
```bash
CACHE_BACKEND=redis npm start
```

### 5. Tester

```bash
curl http://localhost:3000/rendezvous/test
```

---

## 🔧 Commandes Utiles

### Docker Compose

```bash
# Démarrer
docker-compose up -d

# Arrêter
docker-compose down

# Voir les logs en temps réel
docker-compose logs -f

# Redémarrer un service
docker-compose restart rendezvous

# Voir l'état des services
docker-compose ps

# Arrêter et supprimer tout (attention : efface les volumes)
docker-compose down -v
```

### Redis

```bash
# Se connecter à Redis
redis-cli

# Dans redis-cli :
> PING                    # Test de connexion
> KEYS rr2-*              # Voir les clés du rendezvous
> GET rr2-<key>           # Voir une clé spécifique
> TTL rr2-<key>           # Voir le temps restant
> FLUSHALL                # Effacer toutes les clés (attention !)
> INFO stats              # Statistiques Redis
```

### Docker (sans Compose)

```bash
# Voir les logs du rendezvous
docker logs -f rendezvous

# Redémarrer
docker restart rendezvous

# Arrêter
docker stop rendezvous redis

# Supprimer
docker rm rendezvous redis
```

---

## 🧪 Tests Rapides

### Test 1 : Connexion de base

```bash
curl http://localhost:3000/
```

Devrait retourner la page HTML de bienvenue.

### Test 2 : Endpoint rendezvous legacy

```bash
curl http://localhost:3000/rendezvous/test
```

Devrait retourner : `Connection OK`

### Test 3 : Endpoint rendezvous2 (WebRTC)

```bash
curl http://localhost:3000/rendezvous2/test
```

Devrait retourner : `Connection OK`

### Test 4 : POST + GET (simulation complète)

```bash
# POST des données
curl -X POST http://localhost:3000/rendezvous2/ \
  -H "Content-Type: application/json" \
  -d '{"key":"test123","webrtc":true,"first":true,"data":"hello"}'

# GET des données (dans les 120 secondes)
curl http://localhost:3000/rendezvous2/test123
```

Devrait retourner les données JSON postées.

### Test 5 : Vérifier Redis

```bash
# Voir toutes les clés
redis-cli KEYS '*'

# Voir une clé spécifique
redis-cli GET rr2-test123

# Vérifier le TTL (temps avant expiration)
redis-cli TTL rr2-test123
```

---

## 🔄 Basculer entre Memcache et Redis

### Option 1 : Via variable d'environnement

```bash
# Avec Redis
CACHE_BACKEND=redis npm start

# Avec Memcache (Google App Engine)
CACHE_BACKEND=memcache npm start
```

### Option 2 : Via fichier .env

Éditer `.env` :
```bash
# Pour Redis
CACHE_BACKEND=redis

# Pour Memcache
CACHE_BACKEND=memcache
```

Puis :
```bash
npm start
```

### Option 3 : Via Docker Compose

Éditer `docker-compose.yml`, changer :
```yaml
environment:
  - CACHE_BACKEND=redis  # ou memcache
```

Puis :
```bash
docker-compose down
docker-compose up -d
```

---

## 📊 Monitoring

### Voir les logs en temps réel

**Docker Compose :**
```bash
docker-compose logs -f rendezvous
```

**Docker :**
```bash
docker logs -f rendezvous
```

**Node.js local :**
Les logs s'affichent dans le terminal.

### Statistiques Redis

```bash
redis-cli INFO stats
```

### Base de données SQLite (statistiques)

```bash
sqlite3 data/rendezvous.db "SELECT COUNT(*) FROM logs;"
```

---

## 🛑 Arrêter les Services

### Docker Compose

```bash
docker-compose down
```

### Docker (sans Compose)

```bash
docker stop rendezvous redis
docker rm rendezvous redis
```

### Node.js local

```bash
# Ctrl+C dans le terminal

# Arrêter Redis
brew services stop redis  # macOS
sudo systemctl stop redis # Linux
```

---

## ❓ Dépannage

### Problème : "Redis connection refused"

**Solution :**
```bash
# Vérifier que Redis tourne
docker ps | grep redis
# ou
sudo systemctl status redis

# Redémarrer Redis
docker restart redis
# ou
sudo systemctl restart redis
```

### Problème : "Cannot find module 'redis'"

**Solution :**
```bash
rm -rf node_modules
npm install
```

### Problème : Port 3000 déjà utilisé

**Solution :**
```bash
# Voir ce qui utilise le port 3000
sudo lsof -i :3000

# Changer le port (dans docker-compose.yml ou au lancement)
PORT=8080 npm start
```

### Problème : "NOAUTH Authentication required"

**Solution :**
Ajouter le mot de passe dans `.env` :
```bash
REDIS_PASSWORD=votre_mot_de_passe
```

---

## 📚 Documentation Complète

- **README.md** : Vue d'ensemble et documentation générale
- **MIGRATION.md** : Guide complet de migration et compatibilité avec MIT
- **.env.example** : Exemple de configuration complète

---

## ✅ Checklist de Démarrage

- [ ] Redis installé et démarré
- [ ] Dépendances Node.js installées (`npm install`)
- [ ] Fichier `.env` configuré (optionnel)
- [ ] Services démarrés (`docker-compose up -d` ou `npm start`)
- [ ] Tests réussis (`curl http://localhost:3000/rendezvous/test`)
- [ ] Vérification Redis (`redis-cli ping`)

---

**Vous êtes prêt à utiliser le serveur rendezvous avec Redis ! 🎉**

Pour toute question, consultez [MIGRATION.md](MIGRATION.md) pour plus de détails.
