# MIT App Inventor - Déploiement On-Premise (RGPD)

Ce répertoire contient tout ce dont vous avez besoin pour déployer MIT App Inventor sur votre infrastructure on-premise à Paris DSI, en conformité totale avec le RGPD.

---

## 📚 Documentation Disponible

### Pour Tous

- **[README.md](README.md)** ← Vous êtes ici
  - Vue d'ensemble et guide de démarrage rapide

### Pour Installation Manuelle

- **[INSTALLATION_STEP_BY_STEP.md](INSTALLATION_STEP_BY_STEP.md)** (1067 lignes)
  - Guide détaillé étape par étape pour non-techniciens
  - Toutes les commandes à copier-coller
  - Vérifications après chaque étape
  - Section dépannage complète
  - **Durée**: 4-6 heures

### Pour Installation Automatique

- **[install-appinventor.sh](install-appinventor.sh)** (script bash)
  - Installation 100% automatique
  - Génère des mots de passe sécurisés
  - Crée tous les composants
  - **Durée**: 30-60 minutes

- **[CLAUDE_CODE_PROMPT.md](CLAUDE_CODE_PROMPT.md)**
  - Prompts pour Claude Code
  - Installation automatique avec IA
  - Mode debug intégré
  - **Durée**: 30-60 minutes (supervisée par IA)

### Pour Opérations

- **[DEPLOYMENT_GUIDE_DSI.md](DEPLOYMENT_GUIDE_DSI.md)** (600+ lignes)
  - Guide complet pour équipe DSI
  - Infrastructure, installation, monitoring
  - Procédures opérationnelles
  - Checklist RGPD

### Pour Développement

- **[PHASE4_MIGRATION_PLAN_ONPREMISE.md](PHASE4_MIGRATION_PLAN_ONPREMISE.md)** (1000+ lignes)
  - Plan de développement Phase 4 (base de données)
  - Architecture technique détaillée
  - Exemples de code Java
  - Timeline et budget (3-6 mois, 215K€-305K€)

### Manifestes Kubernetes

- **[kubernetes/](kubernetes/)** - Tous les manifestes YAML
  - `00-namespace.yaml` - Namespace
  - `01-storage-classes.yaml` - Stockage local
  - `02-secrets.yaml` - Templates secrets
  - `postgresql/` - PostgreSQL (3 nodes + Patroni)
  - `redis/` - Redis (3 nodes + Sentinel)
  - `minio/` - MinIO (4 nodes distribués)
  - `appinventor/` - Applications (webapp, buildserver, rendezvous, ingress)

---

## 🚀 Démarrage Rapide

### Méthode 1: Installation Automatique (Recommandée)

**Pour qui**: Tous, même sans expertise technique

```bash
# Se connecter en SSH au serveur
ssh votre-utilisateur@votre-serveur

# Devenir root ou utiliser sudo
sudo su

# Cloner le repository
cd /opt
git clone https://github.com/jedeth/appinventor-sources_recherche.git

# Lancer le script d'installation
cd appinventor-sources_recherche/appinventor/deployment
bash install-appinventor.sh
```

**Résultat**: Infrastructure complète installée en 30-60 minutes.

---

### Méthode 2: Installation avec Claude Code (IA)

**Pour qui**: Utilisateurs de VS Code avec Claude Code installé

1. Connectez-vous en SSH via VS Code
2. Ouvrez Claude Code (Ctrl+Shift+P → "Claude Code")
3. Copiez-collez ce prompt:

```
Installe MIT App Inventor automatiquement avec le script:
sudo bash /opt/appinventor-sources_recherche/appinventor/deployment/install-appinventor.sh

Réponds "oui" aux confirmations.
Ensuite affiche-moi l'état final et l'URL d'accès.
```

**Voir**: [CLAUDE_CODE_PROMPT.md](CLAUDE_CODE_PROMPT.md) pour plus de prompts.

---

### Méthode 3: Installation Manuelle

**Pour qui**: Personnes voulant comprendre chaque étape

Suivez le guide: [INSTALLATION_STEP_BY_STEP.md](INSTALLATION_STEP_BY_STEP.md)

11 étapes détaillées avec explications et vérifications.

---

## 📊 Ce qui Sera Installé

Après l'installation, vous aurez:

### ✅ Infrastructure de Base
- **Kubernetes (RKE2)** - Orchestrateur de containers
- **Docker Registry Local** - Pour stocker les images

### ✅ Bases de Données et Stockage
- **PostgreSQL** (3 serveurs)
  - Haute disponibilité avec Patroni
  - 16 tables créées automatiquement
  - Optimisé pour 512GB RAM
  - Backup automatique

- **Redis** (3 serveurs)
  - Cache et sessions
  - Haute disponibilité avec Sentinel
  - Cleanup automatique RGPD

- **MinIO** (4 serveurs)
  - Stockage objet S3-compatible
  - Mode distribué (erasure coding)
  - 2 buckets créés: projects et builds
  - Lifecycle policy: APKs supprimés après 1 jour

### ✅ Applications
- **Rendezvous Server** (3 réplicas)
  - WebRTC pour AI Companion
  - Fonctionnel immédiatement ✅

- **App Inventor Webapp** (3-10 réplicas)
  - ⚠️ Image placeholder (Phase 4 requis)

- **Build Server** (5-15 réplicas)
  - ⚠️ Image placeholder (Phase 4 requis)

### ✅ Accès et Sécurité
- **NGINX Ingress** - Routage HTTP/HTTPS
- **NetworkPolicies** - Isolation réseau
- **Secrets Kubernetes** - Mots de passe sécurisés

---

## ⏱️ Durées et Coûts

### Installation Infrastructure

| Méthode | Durée | Complexité |
|---------|-------|------------|
| Script automatique | 30-60 min | Facile |
| Claude Code (IA) | 30-60 min | Très facile |
| Manuelle étape par étape | 4-6h | Moyenne |

**Coût infrastructure**: 0€ (vous avez déjà les serveurs)

### Développement Phase 4 (Pour App Fonctionnel)

| Approche | Durée | Coût |
|----------|-------|------|
| Greenfield (nouveau déploiement) | 3-6 mois | 215K€-305K€ |
| Migration (avec données existantes) | 12-18 mois | 500K€-750K€ |

**Voir**: [PHASE4_MIGRATION_PLAN_ONPREMISE.md](PHASE4_MIGRATION_PLAN_ONPREMISE.md)

---

## 🎯 Après l'Installation

### 1. Vérifier l'État

```bash
# Commande de vérification installée automatiquement
check-appinventor

# Ou manuellement
kubectl get pods -n appinventor
kubectl get svc -n appinventor
kubectl get pvc -n appinventor
```

### 2. Accéder aux Services

**App Inventor (une fois Phase 4 développée)**:
```
http://VOTRE_IP:NODEPORT
```

**MinIO Console**:
```bash
kubectl port-forward svc/minio -n appinventor 9001:9001
# Puis: http://localhost:9001
```

**PostgreSQL** (ligne de commande):
```bash
kubectl exec -it postgresql-0 -n appinventor -- psql -U postgres -d appinventor
```

### 3. Récupérer les Mots de Passe

```bash
cat /root/appinventor-passwords-*.txt
```

**⚠️ IMPORTANT**: Conservez ce fichier en lieu sûr!

### 4. Sauvegardes

**PostgreSQL**:
```bash
kubectl exec postgresql-0 -n appinventor -- pg_dump -U postgres -d appinventor > backup.sql
```

**MinIO**:
```bash
mc mirror minio/appinventor-projects ./backup-projects/
```

---

## 🔄 Prochaines Étapes

### Phase 4: Développement de l'Adapter PostgreSQL

L'infrastructure est prête, mais il manque le code Java pour que App Inventor utilise PostgreSQL au lieu de Google Datastore.

**Options**:

1. **Engager un développeur Java**
   - Timeline: 3-6 mois
   - Budget: 215K€-305K€
   - Voir: [PHASE4_MIGRATION_PLAN_ONPREMISE.md](PHASE4_MIGRATION_PLAN_ONPREMISE.md)

2. **Développer en interne**
   - Nécessite expertise Java, JDBC, Kubernetes
   - Même timeline

3. **Utiliser une version simplifiée**
   - Version minimale avec fonctionnalités réduites
   - Timeline: 1-2 mois

### Composants à Développer

```java
// À implémenter (exemples fournis dans PHASE4)
- DatabaseService.java (interface)
- PostgreSQLAdapter.java (implémentation JDBC)
- 16 classes Entity (User, Project, FileData, etc.)
- Repositories (DAO pattern)
- Modifier le code existant pour utiliser le nouvel adapter
```

### Puis: Rebuild et Redéploiement

```bash
# Compiler le WAR avec Maven
mvn clean package

# Builder l'image Docker
docker build -t localhost:5000/appinventor:v2.0.0 .
docker push localhost:5000/appinventor:v2.0.0

# Redéployer
kubectl set image deployment/appinventor appinventor=localhost:5000/appinventor:v2.0.0 -n appinventor
kubectl rollout status deployment/appinventor -n appinventor
```

---

## 🛠️ Commandes Utiles

### Surveillance

```bash
# Voir tous les pods
kubectl get pods -n appinventor -o wide

# Voir les logs d'un pod
kubectl logs <pod-name> -n appinventor -f

# Voir les logs de plusieurs pods
kubectl logs -l app=postgresql -n appinventor --tail=50

# Voir les événements
kubectl get events -n appinventor --sort-by='.lastTimestamp'

# Voir l'utilisation des ressources
kubectl top nodes
kubectl top pods -n appinventor
```

### Gestion

```bash
# Redémarrer un deployment
kubectl rollout restart deployment/rendezvous -n appinventor

# Scaler un deployment
kubectl scale deployment/rendezvous --replicas=5 -n appinventor

# Voir l'historique des déploiements
kubectl rollout history deployment/appinventor -n appinventor

# Rollback
kubectl rollout undo deployment/appinventor -n appinventor
```

### Debug

```bash
# Décrire un pod (voir les erreurs)
kubectl describe pod <pod-name> -n appinventor

# Shell dans un pod
kubectl exec -it <pod-name> -n appinventor -- /bin/bash

# Voir les logs d'installation
cat /var/log/appinventor-install-*.log | less

# Voir la configuration d'un secret
kubectl get secret postgresql-credentials -n appinventor -o yaml
```

---

## 🔐 Conformité RGPD

### Ce qui est Conforme

✅ **Toutes les données à Paris DSI**
- PostgreSQL: stockage local
- MinIO: stockage local
- Redis: données en mémoire locale
- Pas de transfert hors UE

✅ **Droit à l'oubli**
- Fonctions SQL de suppression implémentées
- Cleanup automatique (CronJobs)

✅ **Droit d'accès**
- Fonctions d'export de données
- Logs d'audit

✅ **Sécurité**
- Chiffrement des données (MinIO KMS)
- Secrets Kubernetes sécurisés
- NetworkPolicies (isolation)
- TLS supporté (à configurer)

✅ **Rétention**
- APKs: 1 jour (lifecycle policy)
- Nonces: 24h (cleanup automatique)
- Données utilisateur: configurable

---

## ❓ FAQ

### Q: Puis-je installer sur Ubuntu ET Rocky Linux?
**R**: Oui, le script supporte les deux.

### Q: Combien de temps pour avoir App Inventor fonctionnel?
**R**:
- Infrastructure: 1h (automatique)
- Phase 4 développement: 3-6 mois
- **Total**: 3-6 mois

### Q: Est-ce que ça marche sur un seul serveur?
**R**: Oui, mais les pods de réplication seront sur le même serveur (moins de HA).

### Q: Puis-je ajouter des serveurs plus tard?
**R**: Oui, Kubernetes supporte le scale-out.

### Q: Les mots de passe sont-ils sécurisés?
**R**: Oui, générés avec `openssl rand -base64` (24-32 caractères).

### Q: Puis-je changer les mots de passe après installation?
**R**: Oui, modifiez les secrets Kubernetes et redémarrez les pods.

### Q: Que faire si un pod crash?
**R**:
1. `kubectl logs <pod> -n appinventor`
2. `kubectl describe pod <pod> -n appinventor`
3. Voir section Dépannage dans INSTALLATION_STEP_BY_STEP.md

### Q: Comment sauvegarder?
**R**: Voir section "Après l'Installation → Sauvegardes" ci-dessus.

### Q: Comment restaurer?
**R**:
```bash
# PostgreSQL
kubectl exec -i postgresql-0 -n appinventor -- psql -U postgres -d appinventor < backup.sql

# MinIO
mc mirror ./backup-projects/ minio/appinventor-projects
```

---

## 🆘 Support et Aide

### Logs

- **Installation**: `/var/log/appinventor-install-*.log`
- **Mots de passe**: `/root/appinventor-passwords-*.txt`
- **Kubernetes**: `kubectl logs <pod> -n appinventor`

### Vérification Santé

```bash
# Script de vérification complet
check-appinventor

# Ou vérification manuelle
kubectl get all -n appinventor
```

### En Cas de Problème

1. **Consultez**: [INSTALLATION_STEP_BY_STEP.md](INSTALLATION_STEP_BY_STEP.md) section Dépannage
2. **Utilisez Claude Code**: Prompt "Mode Debug" dans [CLAUDE_CODE_PROMPT.md](CLAUDE_CODE_PROMPT.md)
3. **Vérifiez les logs**:
   ```bash
   kubectl get events -n appinventor --sort-by='.lastTimestamp'
   ```

---

## 📁 Structure du Répertoire

```
deployment/
├── README.md                          ← Vous êtes ici
├── INSTALLATION_STEP_BY_STEP.md       ← Guide manuel détaillé
├── DEPLOYMENT_GUIDE_DSI.md            ← Guide complet DSI
├── PHASE4_MIGRATION_PLAN_ONPREMISE.md ← Plan de développement
├── CLAUDE_CODE_PROMPT.md              ← Prompts pour IA
├── install-appinventor.sh             ← Script d'installation auto
└── kubernetes/
    ├── 00-namespace.yaml
    ├── 01-storage-classes.yaml
    ├── 02-secrets.yaml
    ├── postgresql/
    │   └── postgresql-statefulset.yaml
    ├── redis/
    │   └── redis-statefulset.yaml
    ├── minio/
    │   └── minio-statefulset.yaml
    └── appinventor/
        ├── appinventor-deployment.yaml
        ├── buildserver-deployment.yaml
        ├── rendezvous-deployment.yaml
        └── ingress.yaml
```

---

## 🎉 Conclusion

Vous avez maintenant tout ce qu'il faut pour:

1. ✅ **Installer l'infrastructure** (1h automatique)
2. 📋 **Planifier Phase 4** (guide complet fourni)
3. 🔧 **Opérer le système** (guides et commandes)
4. 🛡️ **Rester conforme RGPD** (tout configuré)

**Commencez maintenant**:
```bash
sudo bash install-appinventor.sh
```

Bonne installation! 🚀

---

*Documentation créée le 2025-11-12 pour DSI Paris*
*MIT App Inventor - RGPD Compliant On-Premise Deployment*
*Version 1.0*
