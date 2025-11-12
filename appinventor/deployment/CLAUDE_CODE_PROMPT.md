# Prompt pour Claude Code - Installation Automatique

Ce fichier contient un prompt que vous pouvez donner à Claude Code pour qu'il exécute automatiquement l'installation complète de MIT App Inventor.

---

## Option 1: Installation Automatique Complète (Recommandée)

Copiez-collez ce prompt dans Claude Code:

```
Je suis connecté en SSH avec sudo à mon serveur Linux.
Je veux installer MIT App Inventor de façon automatique.

Voici ce que tu dois faire:

1. D'abord, vérifie que nous sommes dans le bon répertoire et que le script existe:
   - Aller dans /home/user/appinventor-sources_recherche ou cloner le repo si nécessaire
   - Vérifier que le fichier install-appinventor.sh existe dans appinventor/deployment/

2. Exécute le script d'installation automatique:
   sudo bash appinventor/deployment/install-appinventor.sh

3. Surveille l'exécution et réponds aux questions si nécessaire:
   - Confirmer l'installation (taper "oui")
   - Si le script demande des confirmations pour RAM/CPU/Disk insuffisant, confirmer avec "oui"

4. Une fois terminé, vérifie que tout fonctionne:
   - Exécute: check-appinventor
   - Vérifie que tous les pods sont Running
   - Affiche le fichier des mots de passe (dans /root/appinventor-passwords-*.txt)

5. Crée un résumé pour moi avec:
   - L'adresse d'accès web
   - L'emplacement du fichier des mots de passe
   - L'état de chaque composant (PostgreSQL, Redis, MinIO, etc.)
   - Les prochaines étapes à suivre

IMPORTANT: Si tu rencontres une erreur:
- Affiche les logs: cat /var/log/appinventor-install-*.log
- Identifie le problème
- Propose une solution

Commence maintenant!
```

---

## Option 2: Installation Étape par Étape (Pour Suivi Détaillé)

Si vous préférez un contrôle plus fin, copiez ce prompt:

```
Je veux installer MIT App Inventor étape par étape sur mon serveur Linux avec sudo.

Voici le plan:

ÉTAPE 0-1: Vérifications et Préparation
- Vérifie que je suis bien en SSH avec sudo (whoami, sudo echo "test")
- Vérifie la RAM, CPU, espace disque (free -h, nproc, df -h)
- Met à jour le système (apt update ou dnf update selon l'OS)
- Désactive le swap: sudo swapoff -a
- Configure le firewall pour les ports nécessaires

ÉTAPE 2: Installation Kubernetes
- Installe RKE2 avec: curl -sfL https://get.rke2.io | sudo sh -
- Démarre RKE2: sudo systemctl enable rke2-server && sudo systemctl start rke2-server
- Configure kubectl
- Vérifie que le node est Ready: kubectl get nodes

ÉTAPE 3: Stockage
- Crée les répertoires: /mnt/appinventor-storage/postgresql-{0,1,2}, minio-{0,1,2,3}, redis-{0,1,2}
- Clone le repo si nécessaire
- Applique les manifestes: kubectl apply -f 00-namespace.yaml && kubectl apply -f 01-storage-classes.yaml
  (en remplaçant "your-node-1" par le vrai nom du node)

ÉTAPE 4: Secrets
- Génère des mots de passe sécurisés avec openssl rand -base64
- Crée les secrets Kubernetes pour PostgreSQL, Redis, MinIO, etc.
- Sauvegarde les mots de passe dans un fichier sécurisé

ÉTAPE 5-7: Déploiement Base de Données et Stockage
- Déploie PostgreSQL: kubectl apply -f postgresql/postgresql-statefulset.yaml
- Attend que les 3 pods soient Running
- Déploie Redis: kubectl apply -f redis/redis-statefulset.yaml
- Déploie MinIO: kubectl apply -f minio/minio-statefulset.yaml
- Crée les buckets MinIO

ÉTAPE 8-9: Images Docker et App
- Installe Docker si nécessaire
- Build l'image Rendezvous
- Déploie les applications: kubectl apply -f appinventor/*.yaml

ÉTAPE 10-11: Accès Web et Vérifications
- Installe NGINX Ingress Controller
- Configure l'Ingress
- Vérifie que tout fonctionne

À chaque étape:
- Exécute les commandes nécessaires
- Vérifie que ça a fonctionné
- Affiche le résultat
- Attends ma confirmation avant de passer à l'étape suivante

Commence par l'ÉTAPE 0-1!
```

---

## Option 3: Mode Debug (Pour Diagnostiquer des Problèmes)

Si l'installation a échoué et vous voulez diagnostiquer:

```
L'installation de MIT App Inventor a rencontré un problème.

Aide-moi à diagnostiquer:

1. Vérifie l'état général:
   - kubectl get nodes
   - kubectl get pods -n appinventor
   - kubectl get pvc -n appinventor
   - kubectl get events -n appinventor --sort-by='.lastTimestamp' | tail -20

2. Pour chaque pod qui n'est pas Running:
   - kubectl describe pod <pod-name> -n appinventor
   - kubectl logs <pod-name> -n appinventor

3. Vérifie les logs d'installation:
   - ls -la /var/log/appinventor-install-*.log
   - tail -100 /var/log/appinventor-install-*.log

4. Vérifie les ressources système:
   - free -h
   - df -h
   - kubectl top nodes (si disponible)

5. Identifie le problème et propose une solution:
   - Quelle étape a échoué?
   - Quel est le message d'erreur exact?
   - Quelle est la cause probable?
   - Quelle commande dois-je exécuter pour corriger?

Commence l'analyse maintenant!
```

---

## Option 4: Exécution Directe du Script (Plus Rapide)

Le prompt le plus simple si vous voulez juste lancer le script:

```
Exécute le script d'installation automatique de MIT App Inventor:

sudo bash /home/user/appinventor-sources_recherche/appinventor/deployment/install-appinventor.sh

Réponds "oui" à toutes les confirmations.

Ensuite affiche-moi:
1. Le contenu du fichier des mots de passe (dans /root/)
2. L'état final: check-appinventor
3. L'URL d'accès web

Vas-y maintenant!
```

---

## Conseils d'Utilisation avec Claude Code

### 1. Prérequis
- Être connecté en SSH à votre serveur via VS Code
- Avoir les droits sudo
- Terminal VS Code ouvert

### 2. Workflow Recommandé

**A. Avant de lancer Claude Code:**
```bash
# Se connecter en SSH
ssh votre-utilisateur@votre-serveur

# Ouvrir VS Code Remote
code .

# Ouvrir un terminal dans VS Code
# (Terminal > New Terminal)
```

**B. Lancer Claude Code:**
- Ouvrir la palette de commandes (Ctrl+Shift+P ou Cmd+Shift+P)
- Taper "Claude Code"
- Coller un des prompts ci-dessus

**C. Pendant l'exécution:**
- Claude Code va exécuter les commandes une par une
- Surveillez l'output dans le terminal
- Si Claude demande une confirmation, répondez dans le chat
- Si une erreur survient, Claude va la diagnostiquer

### 3. Que Fait Claude Code Automatiquement?

✅ Exécute toutes les commandes bash nécessaires
✅ Vérifie le résultat de chaque commande
✅ Attend que les pods soient prêts (avec timeout)
✅ Génère des mots de passe sécurisés
✅ Configure automatiquement les manifestes Kubernetes
✅ Crée les secrets
✅ Déploie tous les composants
✅ Vérifie que tout fonctionne
✅ Vous donne un résumé final

### 4. Durée Estimée

- **Installation automatique complète**: 30-60 minutes
- **Installation étape par étape**: 1-2 heures (avec validation manuelle)
- **Mode debug**: Variable selon le problème

### 5. Après l'Installation

Claude Code vous donnera:
- ✅ L'adresse d'accès web
- ✅ Le fichier avec tous les mots de passe
- ✅ L'état de tous les composants
- ✅ Les prochaines étapes

### 6. Si Quelque Chose ne Fonctionne Pas

1. **Utilisez le prompt "Option 3: Mode Debug"**
2. Claude Code va diagnostiquer automatiquement
3. Il vous proposera des solutions

Ou demandez directement:
```
Un composant ne fonctionne pas correctement:
[décrivez le problème]

Aide-moi à le corriger.
```

---

## Limitations et Notes Importantes

### ⚠️ Ce que le Script NE Fait PAS

Le script installe l'infrastructure complète, mais:

1. **Phase 4 non développée**: L'adapter PostgreSQL n'est pas encore codé
   - Les images App Inventor et Build Server sont des placeholders
   - Le Rendezvous Server fonctionne ✅
   - L'infrastructure (PostgreSQL, Redis, MinIO) fonctionne ✅

2. **Certificats TLS**: Pas de HTTPS configuré automatiquement
   - Accès en HTTP seulement
   - Vous devrez configurer Let's Encrypt manuellement

3. **DNS**: Pas de noms de domaine configurés
   - Accès par IP:PORT uniquement

### ✅ Ce qui Fonctionne Après le Script

- ✅ Kubernetes (RKE2) installé et opérationnel
- ✅ PostgreSQL: 3 serveurs avec haute disponibilité + 16 tables créées
- ✅ Redis: 3 serveurs avec Sentinel
- ✅ MinIO: 4 serveurs en mode distribué + buckets créés
- ✅ Rendezvous Server: Fonctionnel pour WebRTC
- ✅ Infrastructure de monitoring de base
- ✅ Tous les secrets et mots de passe configurés

### 📋 Prochaines Étapes Après Installation

1. **Développer Phase 4** (ou engager un développeur):
   - Implémenter PostgreSQLAdapter.java
   - Modifier le code App Inventor
   - Compiler le WAR

2. **Builder les vraies images Docker**:
   - Image App Inventor avec code compilé
   - Image Build Server avec Android SDK

3. **Redéployer**:
   - Pusher les nouvelles images
   - kubectl set image deployment/...

4. **Configurer HTTPS** (optionnel):
   - Installer cert-manager
   - Obtenir certificat Let's Encrypt
   - Mettre à jour Ingress

5. **Tests utilisateurs**:
   - Créer des comptes
   - Tester la création de projets
   - Tester la compilation APK

---

## Support et Dépannage

### Commandes Utiles Après Installation

```bash
# Vérifier l'état général
check-appinventor

# Voir tous les pods
kubectl get pods -n appinventor -o wide

# Voir les logs d'un pod
kubectl logs <pod-name> -n appinventor

# Voir les événements récents
kubectl get events -n appinventor --sort-by='.lastTimestamp'

# Redémarrer un deployment
kubectl rollout restart deployment/<name> -n appinventor

# Accéder à MinIO Console
kubectl port-forward svc/minio -n appinventor 9001:9001
# Puis: http://localhost:9001

# Accéder à PostgreSQL
kubectl exec -it postgresql-0 -n appinventor -- psql -U postgres -d appinventor

# Voir les mots de passe
cat /root/appinventor-passwords-*.txt

# Voir les logs d'installation
cat /var/log/appinventor-install-*.log
```

### Problèmes Courants et Solutions

**Problème**: Pods en CrashLoopBackOff
```
Solution: kubectl logs <pod-name> -n appinventor
Regarder l'erreur et corriger la configuration
```

**Problème**: Pas assez d'espace disque
```
Solution:
docker system prune -a --volumes
kubectl delete pvc <pvc-name> -n appinventor
```

**Problème**: Le script s'est arrêté
```
Solution:
# Relancer le script, il va détecter ce qui est déjà installé
sudo bash appinventor/deployment/install-appinventor.sh
```

---

## Exemples de Prompts Avancés

### Installer + Configurer HTTPS
```
Installe MIT App Inventor avec le script automatique, puis configure HTTPS:

1. Exécute: sudo bash appinventor/deployment/install-appinventor.sh
2. Attends que ce soit terminé
3. Installe cert-manager:
   kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.0/cert-manager.yaml
4. Configure Let's Encrypt
5. Mets à jour l'Ingress pour utiliser TLS

Donne-moi l'URL HTTPS finale.
```

### Installer + Monitoring Grafana
```
Installe MIT App Inventor puis configure le monitoring:

1. Lance l'installation automatique
2. Installe Prometheus + Grafana
3. Configure les dashboards pour:
   - PostgreSQL
   - Redis
   - MinIO
   - Kubernetes
4. Donne-moi l'URL Grafana et les credentials par défaut
```

### Installer + Backup Automatique
```
Installe MIT App Inventor puis configure les backups:

1. Lance l'installation
2. Crée un CronJob Kubernetes pour backup PostgreSQL (tous les jours à 2h)
3. Crée un CronJob pour backup MinIO
4. Configure la rétention: 7 jours
5. Teste le backup manuellement
6. Montre-moi comment restaurer un backup
```

---

**Note Finale**: Ce script est testé mais chaque serveur est différent. Si vous rencontrez un problème, utilisez le mode Debug (Option 3) et Claude Code vous aidera à le résoudre.

Bonne installation! 🚀
