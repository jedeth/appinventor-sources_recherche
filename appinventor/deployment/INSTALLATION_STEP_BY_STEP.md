# Installation Complète MIT App Inventor - Guide Pas-à-Pas
## Pour Non-Techniciens avec Accès Sudo

**Version**: 1.0
**Date**: 2025-11-12
**Public**: Utilisateurs ayant accès SSH sudo, sans expertise technique
**Durée estimée**: 4-6 heures pour une première installation

---

## ⚠️ IMPORTANT - À Lire Avant de Commencer

Ce guide suppose que vous êtes connecté en SSH à votre serveur via VS Code avec les droits `sudo`.

### Ce que vous allez installer

1. **Kubernetes (RKE2)** - Plateforme pour orchestrer les containers
2. **PostgreSQL** - Base de données (3 serveurs pour haute disponibilité)
3. **Redis** - Cache et sessions (3 serveurs)
4. **MinIO** - Stockage de fichiers (4 serveurs)
5. **App Inventor** - L'application web elle-même

### Prérequis Matériel

- ✅ Serveur Linux (Ubuntu 20.04+ ou Rocky Linux 8+)
- ✅ Minimum 32 GB RAM (idéal: 64+ GB)
- ✅ Minimum 8 CPU cores (idéal: 16+ cores)
- ✅ Minimum 500 GB disque (idéal: 1+ TB)
- ✅ Connexion Internet

---

## Table des Matières

1. [Étape 0: Vérifications Préliminaires](#étape-0-vérifications-préliminaires)
2. [Étape 1: Préparation du Système](#étape-1-préparation-du-système)
3. [Étape 2: Installation de Kubernetes (RKE2)](#étape-2-installation-de-kubernetes-rke2)
4. [Étape 3: Configuration du Stockage](#étape-3-configuration-du-stockage)
5. [Étape 4: Création des Secrets](#étape-4-création-des-secrets)
6. [Étape 5: Déploiement de la Base de Données](#étape-5-déploiement-de-la-base-de-données)
7. [Étape 6: Déploiement de Redis](#étape-6-déploiement-de-redis)
8. [Étape 7: Déploiement de MinIO](#étape-7-déploiement-de-minio)
9. [Étape 8: Construction des Images Docker](#étape-8-construction-des-images-docker)
10. [Étape 9: Déploiement d'App Inventor](#étape-9-déploiement-dapp-inventor)
11. [Étape 10: Configuration de l'Accès Web](#étape-10-configuration-de-laccès-web)
12. [Étape 11: Vérifications Finales](#étape-11-vérifications-finales)
13. [Dépannage](#dépannage)

---

## Étape 0: Vérifications Préliminaires

### 0.1 - Vérifier que vous êtes bien connecté en SSH

Dans le terminal VS Code, tapez:

```bash
whoami
```

**Résultat attendu**: Vous devriez voir votre nom d'utilisateur s'afficher.

### 0.2 - Vérifier que vous avez les droits sudo

```bash
sudo echo "J'ai les droits sudo!"
```

**Résultat attendu**: Le message "J'ai les droits sudo!" s'affiche. Si on vous demande un mot de passe, entrez-le.

### 0.3 - Vérifier le système d'exploitation

```bash
cat /etc/os-release
```

**Résultat attendu**: Vous devriez voir des informations sur votre OS (Ubuntu, Rocky Linux, etc.)

### 0.4 - Vérifier les ressources disponibles

```bash
# Mémoire RAM
free -h

# CPU
nproc

# Espace disque
df -h
```

**Vérifiez**:
- RAM: Au moins 30-40 GB disponibles (ligne "available")
- CPU: Au moins 8 cores
- Disque: Au moins 400 GB disponibles

### 0.5 - Vérifier la connexion Internet

```bash
ping -c 4 google.com
```

**Résultat attendu**: Vous devriez voir des réponses (64 bytes from...)

---

## Étape 1: Préparation du Système

### 1.1 - Mettre à jour le système

```bash
# Pour Ubuntu/Debian
sudo apt update && sudo apt upgrade -y

# OU pour Rocky Linux/CentOS
sudo dnf update -y
```

**Temps**: 5-10 minutes
**Résultat attendu**: Le système se met à jour sans erreur.

### 1.2 - Installer les outils nécessaires

```bash
# Pour Ubuntu/Debian
sudo apt install -y curl wget git vim

# OU pour Rocky Linux/CentOS
sudo dnf install -y curl wget git vim
```

### 1.3 - Désactiver le swap (requis par Kubernetes)

```bash
# Désactiver temporairement
sudo swapoff -a

# Désactiver de façon permanente
sudo sed -i '/ swap / s/^/#/' /etc/fstab

# Vérifier
free -h
```

**Vérifiez**: La ligne "Swap" doit afficher "0B" partout.

### 1.4 - Configurer le firewall

```bash
# Pour Ubuntu avec ufw
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 6443/tcp  # Kubernetes API
sudo ufw allow 9090/tcp  # Prometheus (monitoring)
sudo ufw allow 3000/tcp  # Grafana (monitoring)
sudo ufw allow 80/tcp    # HTTP
sudo ufw allow 443/tcp   # HTTPS
sudo ufw allow 9000/tcp  # MinIO API
sudo ufw allow 9001/tcp  # MinIO Console
sudo ufw --force enable

# OU pour Rocky Linux avec firewalld
sudo firewall-cmd --permanent --add-port=22/tcp
sudo firewall-cmd --permanent --add-port=6443/tcp
sudo firewall-cmd --permanent --add-port=9090/tcp
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --permanent --add-port=9000/tcp
sudo firewall-cmd --permanent --add-port=9001/tcp
sudo firewall-cmd --reload
```

### 1.5 - Configurer SELinux (si Rocky Linux/CentOS)

```bash
# Mettre en mode permissif (pour éviter les problèmes)
sudo setenforce 0
sudo sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config
```

---

## Étape 2: Installation de Kubernetes (RKE2)

### 2.1 - Télécharger et installer RKE2

```bash
# Télécharger le script d'installation
curl -sfL https://get.rke2.io | sudo sh -

# Activer et démarrer RKE2
sudo systemctl enable rke2-server.service
sudo systemctl start rke2-server.service
```

**Temps**: 2-5 minutes
**Résultat attendu**: Le service démarre sans erreur.

### 2.2 - Vérifier que RKE2 fonctionne

```bash
# Attendre que RKE2 soit prêt (peut prendre 2-3 minutes)
sudo systemctl status rke2-server.service

# Vérifier (appuyez sur 'q' pour quitter)
```

**Résultat attendu**: Vous devriez voir "active (running)" en vert.

### 2.3 - Configurer kubectl

```bash
# Créer le dossier de configuration
mkdir -p ~/.kube

# Copier la configuration Kubernetes
sudo cp /etc/rancher/rke2/rke2.yaml ~/.kube/config
sudo chown $(whoami):$(whoami) ~/.kube/config

# Ajouter kubectl au PATH
echo 'export PATH=$PATH:/var/lib/rancher/rke2/bin' >> ~/.bashrc
source ~/.bashrc
```

### 2.4 - Tester kubectl

```bash
kubectl get nodes
```

**Résultat attendu**: Vous devriez voir votre serveur listé avec STATUS "Ready".

Exemple:
```
NAME          STATUS   ROLES                       AGE   VERSION
your-server   Ready    control-plane,etcd,master   5m    v1.28.x+rke2r1
```

**SI "NotReady"**: Attendez 2-3 minutes et réessayez.

### 2.5 - Vérifier les pods système

```bash
kubectl get pods -n kube-system
```

**Résultat attendu**: Tous les pods doivent être "Running" ou "Completed".

**SI certains pods sont en erreur**: Attendez 5 minutes, ils devraient se stabiliser.

---

## Étape 3: Configuration du Stockage

### 3.1 - Créer les répertoires de stockage

```bash
# Créer les répertoires pour PostgreSQL (SSD si disponible)
sudo mkdir -p /mnt/appinventor-storage/postgresql-0
sudo mkdir -p /mnt/appinventor-storage/postgresql-1
sudo mkdir -p /mnt/appinventor-storage/postgresql-2

# Créer les répertoires pour MinIO (peut être sur disque HDD)
sudo mkdir -p /mnt/appinventor-storage/minio-0
sudo mkdir -p /mnt/appinventor-storage/minio-1
sudo mkdir -p /mnt/appinventor-storage/minio-2
sudo mkdir -p /mnt/appinventor-storage/minio-3

# Créer les répertoires pour Redis
sudo mkdir -p /mnt/appinventor-storage/redis-0
sudo mkdir -p /mnt/appinventor-storage/redis-1
sudo mkdir -p /mnt/appinventor-storage/redis-2

# Définir les permissions
sudo chmod -R 777 /mnt/appinventor-storage/
```

### 3.2 - Vérifier l'espace disponible

```bash
df -h /mnt/appinventor-storage/
```

**Vérifiez**: Au moins 400 GB disponibles.

### 3.3 - Cloner le repository (si pas déjà fait)

```bash
# Aller dans votre répertoire de travail
cd ~

# Cloner le repository
git clone https://github.com/jedeth/appinventor-sources_recherche.git

# Aller dans le répertoire des manifestes Kubernetes
cd appinventor-sources_recherche/appinventor/deployment/kubernetes
```

### 3.4 - Modifier les manifestes de stockage

Ouvrez le fichier avec VS Code ou vim:

```bash
vim 01-storage-classes.yaml
```

**Remplacez** dans CHAQUE PersistentVolume la ligne:
```yaml
              values:
                - your-node-1  # À REMPLACER
```

Par le nom de votre serveur (celui obtenu avec `kubectl get nodes`).

**Exemple**: Si votre serveur s'appelle "server-paris-01", remplacez par:
```yaml
              values:
                - server-paris-01
```

**À faire pour**: postgresql-pv-0, postgresql-pv-1, postgresql-pv-2, minio-pv-0, minio-pv-1, minio-pv-2, minio-pv-3, redis-pv-0, redis-pv-1, redis-pv-2

**Sauvegarder**:
- Dans vim: Appuyez sur `Esc`, puis tapez `:wq` et `Enter`
- Dans VS Code: Ctrl+S

### 3.5 - Appliquer la configuration de stockage

```bash
# Créer le namespace
kubectl apply -f 00-namespace.yaml

# Appliquer les storage classes et PersistentVolumes
kubectl apply -f 01-storage-classes.yaml
```

### 3.6 - Vérifier

```bash
kubectl get pv
```

**Résultat attendu**: Vous devriez voir 10 PersistentVolumes avec STATUS "Available".

---

## Étape 4: Création des Secrets

Les secrets contiennent les mots de passe. **IMPORTANT**: Choisissez des mots de passe forts!

### 4.1 - Créer les secrets PostgreSQL

```bash
# Remplacez les valeurs entre guillemets par vos propres mots de passe forts
kubectl create secret generic postgresql-credentials \
  --from-literal=superuser-name=postgres \
  --from-literal=superuser-password='VotreMotDePasseSuperUser123!' \
  --from-literal=appinventor-user=appinventor_user \
  --from-literal=appinventor-password='VotreMotDePasseAppInventor456!' \
  -n appinventor
```

**Vérifier**:
```bash
kubectl get secret postgresql-credentials -n appinventor
```

### 4.2 - Créer les secrets Redis

```bash
kubectl create secret generic redis-credentials \
  --from-literal=password='VotreMotDePasseRedis789!' \
  -n appinventor
```

### 4.3 - Créer les secrets MinIO

```bash
kubectl create secret generic minio-credentials \
  --from-literal=root-user=minioadmin \
  --from-literal=root-password='VotreMotDePasseMinIORoot012!' \
  --from-literal=access-key=appinventor \
  --from-literal=secret-key='VotreMotDePasseMinIOAccess345!' \
  -n appinventor
```

### 4.4 - Créer les secrets Build Server

```bash
kubectl create secret generic buildserver-credentials \
  --from-literal=password='VotreMotDePasseBuildServer678!' \
  -n appinventor
```

### 4.5 - Créer les secrets App Inventor

```bash
# Générer des secrets aléatoires sécurisés
SESSION_SECRET=$(openssl rand -base64 32)
CLOUDDB_SECRET=$(openssl rand -base64 32)

kubectl create secret generic appinventor-secrets \
  --from-literal=session-secret="$SESSION_SECRET" \
  --from-literal=clouddb-uuid-secret="$CLOUDDB_SECRET" \
  -n appinventor
```

### 4.6 - Vérifier tous les secrets

```bash
kubectl get secrets -n appinventor
```

**Résultat attendu**: Vous devriez voir 5 secrets créés.

---

## Étape 5: Déploiement de la Base de Données

### 5.1 - Déployer PostgreSQL

```bash
cd ~/appinventor-sources_recherche/appinventor/deployment/kubernetes

kubectl apply -f postgresql/postgresql-statefulset.yaml
```

### 5.2 - Attendre que PostgreSQL démarre

```bash
# Surveiller le démarrage (Ctrl+C pour arrêter)
kubectl get pods -n appinventor -w
```

**Attendez** que vous voyiez 3 pods `postgresql-0`, `postgresql-1`, `postgresql-2` avec STATUS "Running" et READY "1/1".

**Temps d'attente**: 5-10 minutes (le premier démarrage est long).

**Exemple de résultat final**:
```
NAME           READY   STATUS    RESTARTS   AGE
postgresql-0   1/1     Running   0          5m
postgresql-1   1/1     Running   0          4m
postgresql-2   1/1     Running   0          3m
```

### 5.3 - Vérifier les logs PostgreSQL

```bash
# Voir les logs du premier pod
kubectl logs postgresql-0 -n appinventor | tail -20
```

**Vous devriez voir**: Des messages comme "database system is ready to accept connections".

### 5.4 - Vérifier que la base de données est créée

```bash
# Se connecter à PostgreSQL
kubectl exec -it postgresql-0 -n appinventor -- psql -U postgres -d appinventor -c "\dt"
```

**Résultat attendu**: Vous devriez voir la liste des 16 tables (users, projects, file_data, etc.).

**SI "database does not exist"**: La base n'a pas été initialisée. Vérifiez les logs:
```bash
kubectl logs postgresql-0 -n appinventor | grep -i error
```

---

## Étape 6: Déploiement de Redis

### 6.1 - Déployer Redis

```bash
kubectl apply -f redis/redis-statefulset.yaml
```

### 6.2 - Attendre que Redis démarre

```bash
kubectl get pods -n appinventor -w
```

**Attendez**: 3 pods `redis-0`, `redis-1`, `redis-2` en "Running" (2-3 minutes).

### 6.3 - Vérifier Redis

```bash
# Tester la connexion
kubectl exec -it redis-0 -n appinventor -- redis-cli -a 'VotreMotDePasseRedis789!' ping
```

**Résultat attendu**: "PONG"

**SI "NOAUTH"**: Le mot de passe est incorrect. Vérifiez le secret créé à l'étape 4.2.

---

## Étape 7: Déploiement de MinIO

### 7.1 - Déployer MinIO

```bash
kubectl apply -f minio/minio-statefulset.yaml
```

### 7.2 - Attendre que MinIO démarre

```bash
kubectl get pods -n appinventor -w
```

**Attendez**: 4 pods `minio-0`, `minio-1`, `minio-2`, `minio-3` en "Running" (3-5 minutes).

### 7.3 - Créer les buckets

```bash
# Forward le port MinIO localement (laissez tourner dans un terminal)
kubectl port-forward svc/minio -n appinventor 9000:9000 &

# Attendre 5 secondes
sleep 5

# Installer le client MinIO
wget https://dl.min.io/client/mc/release/linux-amd64/mc
chmod +x mc
sudo mv mc /usr/local/bin/

# Configurer le client
mc alias set minio http://localhost:9000 appinventor 'VotreMotDePasseMinIOAccess345!'

# Créer les buckets
mc mb minio/appinventor-projects
mc mb minio/appinventor-builds

# Vérifier
mc ls minio/
```

**Résultat attendu**: Vous devriez voir les 2 buckets listés.

### 7.4 - Arrêter le port-forward

```bash
# Trouver le processus
ps aux | grep "port-forward"

# Tuer le processus (remplacez XXXX par le PID)
kill XXXX
```

---

## Étape 8: Construction des Images Docker

**ATTENTION**: Cette étape nécessite que le code source soit modifié pour utiliser PostgreSQL (Phase 4). Pour l'instant, nous allons créer des Dockerfiles de base.

### 8.1 - Installer Docker (si pas déjà installé)

```bash
# Pour Ubuntu
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER

# Se reconnecter pour que les permissions prennent effet
newgrp docker

# Vérifier
docker --version
```

### 8.2 - Créer un registry local

```bash
# Démarrer un registry Docker local
docker run -d -p 5000:5000 --restart always --name registry registry:2
```

### 8.3 - Créer le Dockerfile pour le Rendezvous Server

```bash
cd ~/appinventor-sources_recherche/appinventor/misc/rendezvous

cat > Dockerfile << 'EOF'
FROM node:18-alpine

WORKDIR /app

# Copier les fichiers
COPY package*.json ./
COPY rendezvous.js ./
COPY cache-adapter.js ./

# Installer les dépendances
RUN npm install --production

# Utilisateur non-root
USER node

# Port
EXPOSE 8888

# Démarrer
CMD ["node", "rendezvous.js"]
EOF
```

### 8.4 - Builder l'image Rendezvous

```bash
docker build -t localhost:5000/appinventor-rendezvous:latest .
docker push localhost:5000/appinventor-rendezvous:latest
```

### 8.5 - NOTE IMPORTANTE pour App Inventor et Build Server

⚠️ **Les images pour App Inventor (application principale) et Build Server nécessitent**:
1. Le code Java compilé avec les modifications Phase 4 (PostgreSQLAdapter)
2. Le Android SDK pour le Build Server

**Pour l'instant**, nous allons créer des images "placeholder" que vous devrez remplacer plus tard.

```bash
# Image placeholder App Inventor
cat > /tmp/Dockerfile.appinventor << 'EOF'
FROM tomcat:10-jdk17-temurin

# TODO: Copier le WAR compilé ici
# COPY appinventor.war /usr/local/tomcat/webapps/ROOT.war

RUN echo "Cette image doit être remplacée après Phase 4" > /usr/local/tomcat/webapps/README.txt

EXPOSE 8080
CMD ["catalina.sh", "run"]
EOF

docker build -t localhost:5000/appinventor:latest -f /tmp/Dockerfile.appinventor /tmp/
docker push localhost:5000/appinventor:latest
```

```bash
# Image placeholder Build Server
cat > /tmp/Dockerfile.buildserver << 'EOF'
FROM openjdk:17-jdk-slim

# TODO: Installer Android SDK et copier le code compilé
RUN echo "Cette image doit être remplacée après développement Phase 4" > /README.txt

EXPOSE 9990
CMD ["java", "-version"]
EOF

docker build -t localhost:5000/appinventor-buildserver:latest -f /tmp/Dockerfile.buildserver /tmp/
docker push localhost:5000/appinventor-buildserver:latest
```

---

## Étape 9: Déploiement d'App Inventor

### 9.1 - NOTE IMPORTANTE

⚠️ **ATTENTION**: Les images placeholder ne contiennent pas encore le code fonctionnel. Cette étape va créer les pods, mais ils ne fonctionneront pas correctement tant que la Phase 4 n'est pas développée.

**Vous pouvez**:
- **Option A**: Sauter cette étape et attendre que Phase 4 soit développée
- **Option B**: Déployer maintenant pour tester l'infrastructure (pods vont démarrer mais ne seront pas fonctionnels)

### 9.2 - Déployer le Rendezvous Server (fonctionnel)

```bash
cd ~/appinventor-sources_recherche/appinventor/deployment/kubernetes

kubectl apply -f appinventor/rendezvous-deployment.yaml
```

### 9.3 - Vérifier Rendezvous

```bash
kubectl get pods -n appinventor | grep rendezvous
```

**Résultat attendu**: 3 pods "rendezvous-xxx" en "Running".

```bash
# Tester
kubectl port-forward svc/rendezvous -n appinventor 8888:8888 &
sleep 3
curl http://localhost:8888/health
```

**Résultat attendu**: Une réponse (même si c'est une erreur, ça prouve que le serveur répond).

### 9.4 - Déployer App Inventor (optionnel)

```bash
# ⚠️ NE PAS FAIRE si vous n'avez pas encore développé Phase 4
kubectl apply -f appinventor/appinventor-deployment.yaml
kubectl apply -f appinventor/buildserver-deployment.yaml
```

---

## Étape 10: Configuration de l'Accès Web

### 10.1 - Installer NGINX Ingress Controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.8.1/deploy/static/provider/baremetal/deploy.yaml
```

### 10.2 - Attendre que l'Ingress soit prêt

```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s
```

### 10.3 - Modifier le fichier Ingress

Vous devez remplacer les noms de domaine par votre propre domaine ou l'IP du serveur.

```bash
cd ~/appinventor-sources_recherche/appinventor/deployment/kubernetes

# Faire une copie
cp appinventor/ingress.yaml appinventor/ingress-custom.yaml

# Éditer le fichier
vim appinventor/ingress-custom.yaml
```

**Remplacez**:
```yaml
- host: appinventor.dsi.paris.fr
```

Par:
```yaml
- host: appinventor.votre-domaine.fr
```

OU si vous n'avez pas de domaine, utilisez l'IP:
```yaml
- host: 192.168.1.100  # Remplacez par l'IP de votre serveur
```

**Faites de même pour** `rendezvous.dsi.paris.fr`.

**COMMENTEZ** la section TLS pour l'instant (mettez # devant):
```yaml
# tls:
#   - hosts:
#       - appinventor.dsi.paris.fr
#       - rendezvous.dsi.paris.fr
#     secretName: appinventor-tls-cert
```

### 10.4 - Appliquer la configuration Ingress

```bash
kubectl apply -f appinventor/ingress-custom.yaml
```

### 10.5 - Trouver le port d'accès

```bash
kubectl get svc -n ingress-nginx
```

Cherchez la ligne `ingress-nginx-controller` et notez le port NodePort (exemple: 80:**30080**/TCP).

### 10.6 - Tester l'accès

```bash
# Remplacez 30080 par le port NodePort trouvé ci-dessus
curl http://localhost:30080
```

---

## Étape 11: Vérifications Finales

### 11.1 - Vérifier tous les pods

```bash
kubectl get pods -n appinventor
```

**Vérifiez** que tous les pods sont "Running" (sauf appinventor et buildserver qui peuvent ne pas fonctionner si Phase 4 n'est pas développée).

### 11.2 - Vérifier les services

```bash
kubectl get svc -n appinventor
```

**Vous devriez voir**:
- postgresql
- redis
- minio
- rendezvous
- (appinventor)
- (buildserver)

### 11.3 - Vérifier le stockage

```bash
kubectl get pvc -n appinventor
```

**Vérifiez** que tous les PersistentVolumeClaims sont "Bound".

### 11.4 - Créer un fichier de vérification

```bash
cat > ~/verification-appinventor.sh << 'EOF'
#!/bin/bash

echo "==================================="
echo "Vérification Installation App Inventor"
echo "==================================="

echo ""
echo "1. NODES KUBERNETES:"
kubectl get nodes

echo ""
echo "2. PODS APP INVENTOR:"
kubectl get pods -n appinventor

echo ""
echo "3. SERVICES:"
kubectl get svc -n appinventor

echo ""
echo "4. PERSISTENTVOLUMES:"
kubectl get pvc -n appinventor

echo ""
echo "5. SECRETS:"
kubectl get secrets -n appinventor

echo ""
echo "6. ESPACE DISQUE:"
df -h /mnt/appinventor-storage/

echo ""
echo "==================================="
echo "FIN DE LA VÉRIFICATION"
echo "==================================="
EOF

chmod +x ~/verification-appinventor.sh
```

### 11.5 - Lancer la vérification

```bash
~/verification-appinventor.sh
```

---

## Dépannage

### Problème: Les pods ne démarrent pas

```bash
# Voir les détails du pod
kubectl describe pod <nom-du-pod> -n appinventor

# Voir les logs
kubectl logs <nom-du-pod> -n appinventor
```

**Solutions courantes**:
- **ImagePullBackOff**: L'image Docker n'existe pas → Vérifier l'étape 8
- **CrashLoopBackOff**: Le conteneur crash au démarrage → Vérifier les logs
- **Pending**: Pas assez de ressources → Vérifier avec `kubectl describe pod`

### Problème: "No space left on device"

```bash
# Nettoyer Docker
docker system prune -a --volumes

# Vérifier l'espace
df -h
```

### Problème: PostgreSQL ne démarre pas

```bash
# Vérifier les logs
kubectl logs postgresql-0 -n appinventor

# Vérifier les permissions du volume
ls -la /mnt/appinventor-storage/postgresql-0/

# Si nécessaire, recréer le volume
kubectl delete pvc data-postgresql-0 -n appinventor
kubectl delete pod postgresql-0 -n appinventor
# Le pod va se recréer automatiquement
```

### Problème: "Unable to connect to the server"

```bash
# Redémarrer RKE2
sudo systemctl restart rke2-server

# Attendre 2 minutes puis vérifier
kubectl get nodes
```

### Problème: Mot de passe oublié

```bash
# Pour PostgreSQL
kubectl get secret postgresql-credentials -n appinventor -o jsonpath='{.data.superuser-password}' | base64 -d
echo ""

# Pour Redis
kubectl get secret redis-credentials -n appinventor -o jsonpath='{.data.password}' | base64 -d
echo ""

# Pour MinIO
kubectl get secret minio-credentials -n appinventor -o jsonpath='{.data.secret-key}' | base64 -d
echo ""
```

---

## Prochaines Étapes

### Phase 4 - Développement de l'Adapter PostgreSQL

Pour que App Inventor fonctionne complètement, il faut:

1. **Développer le code Phase 4**:
   - Implémenter `PostgreSQLAdapter.java`
   - Modifier le code existant pour utiliser le nouveau adapter
   - Compiler le tout en fichier WAR

2. **Builder les vraies images Docker**:
   - Image App Inventor avec le WAR compilé
   - Image Build Server avec Android SDK

3. **Redéployer**:
   - Pousser les nouvelles images
   - Mettre à jour les deployments

**Voir**: `PHASE4_MIGRATION_PLAN_ONPREMISE.md` pour tous les détails.

---

## Sauvegardes

### Sauvegarder PostgreSQL

```bash
# Créer un backup
kubectl exec postgresql-0 -n appinventor -- pg_dump -U postgres -d appinventor > backup-$(date +%Y%m%d).sql

# Restaurer un backup
kubectl exec -i postgresql-0 -n appinventor -- psql -U postgres -d appinventor < backup-20251112.sql
```

### Sauvegarder MinIO

```bash
# Installer mc si pas déjà fait
mc alias set minio-backup http://localhost:9000 appinventor 'VotreMotDePasseMinIOAccess345!'

# Sauvegarder
mc mirror minio-backup/appinventor-projects ./backup-minio-projects-$(date +%Y%m%d)/
```

---

## Désinstallation Complète

**⚠️ ATTENTION: Ceci supprimera TOUTES les données!**

```bash
# Supprimer tous les pods, services, etc.
kubectl delete namespace appinventor

# Supprimer les PersistentVolumes
kubectl delete pv --all

# Supprimer les données
sudo rm -rf /mnt/appinventor-storage/

# Désinstaller Kubernetes (si nécessaire)
sudo systemctl stop rke2-server
sudo /usr/local/bin/rke2-uninstall.sh
```

---

## Support et Aide

### Commandes Utiles

```bash
# Voir tous les pods
kubectl get pods -A

# Voir les événements (pour diagnostiquer)
kubectl get events -n appinventor --sort-by='.lastTimestamp'

# Redémarrer un deployment
kubectl rollout restart deployment/rendezvous -n appinventor

# Voir l'utilisation des ressources
kubectl top nodes
kubectl top pods -n appinventor
```

### Logs

```bash
# Logs en temps réel
kubectl logs -f <nom-du-pod> -n appinventor

# Logs des 100 dernières lignes
kubectl logs --tail=100 <nom-du-pod> -n appinventor
```

---

## Checklist Finale

Après installation, vérifiez:

- [ ] Kubernetes fonctionne (`kubectl get nodes` → Ready)
- [ ] Tous les PersistentVolumes sont "Bound"
- [ ] PostgreSQL: 3 pods Running
- [ ] Redis: 3 pods Running
- [ ] MinIO: 4 pods Running
- [ ] Rendezvous: 3 pods Running
- [ ] Les buckets MinIO existent
- [ ] Les tables PostgreSQL existent (16 tables)
- [ ] Vous pouvez vous connecter à Redis
- [ ] Vous avez sauvegardé tous vos mots de passe
- [ ] L'espace disque est suffisant (>100GB libre)

---

**Installation terminée! 🎉**

L'infrastructure est prête. Il reste maintenant à développer la Phase 4 (code Java PostgreSQL) pour avoir une application App Inventor complètement fonctionnelle.

**Questions?** Relisez les sections pertinentes ou consultez les logs avec `kubectl logs`.

---

*Guide créé le 2025-11-12 pour DSI Paris*
*Installation simplifiée pour non-techniciens*
*Version 1.0*
