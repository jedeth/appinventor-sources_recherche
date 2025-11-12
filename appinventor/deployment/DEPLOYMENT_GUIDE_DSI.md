# Guide de Déploiement MIT App Inventor - DSI Paris
## Infrastructure On-Premise 100% RGPD Compliant

**Version :** 1.0
**Date :** 2024
**Destination :** DSI Paris (milieu scolaire français)
**Conformité :** RGPD, données en France uniquement

---

## 📋 Table des Matières

1. [Vue d'Ensemble](#vue-densemble)
2. [Prérequis Infrastructure](#prérequis-infrastructure)
3. [Préparation des Serveurs](#préparation-des-serveurs)
4. [Installation Kubernetes](#installation-kubernetes)
5. [Déploiement des Composants](#déploiement-des-composants)
6. [Configuration App Inventor](#configuration-app-inventor)
7. [Tests et Validation](#tests-et-validation)
8. [Monitoring et Maintenance](#monitoring-et-maintenance)
9. [Procédures Opérationnelles](#procédures-opérationnelles)
10. [Troubleshooting](#troubleshooting)

---

## 1. Vue d'Ensemble

### Architecture Déployée

```
┌─────────────────────────────────────────────────────────────────┐
│ Utilisateurs (Élèves, Enseignants)                              │
│   ↓                                                              │
│ HTTPS (TLS) - appinventor.dsi.paris.fr                          │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────────┐
│ Load Balancer / Ingress Controller (HAProxy / Nginx)           │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────┴────────────────────────────────────┐
│ Kubernetes Cluster (RKE2)                                       │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │ App Inventor │  │ Build Server │  │ Rendezvous   │         │
│  │ (5-10 pods)  │  │ (3-5 pods)   │  │ (3 pods)     │         │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘         │
│         │                  │                  │                  │
│  ┌──────┴──────────────────┴──────────────────┴───────┐        │
│  │ Services Backend                                    │        │
│  │  ┌─────────────┐  ┌──────────┐  ┌───────────┐    │        │
│  │  │ PostgreSQL  │  │  MinIO   │  │   Redis   │    │        │
│  │  │ (HA 3 nodes)│  │(4 nodes) │  │(3 nodes)  │    │        │
│  │  └─────────────┘  └──────────┘  └───────────┘    │        │
│  └─────────────────────────────────────────────────────┘        │
│                                                                  │
│  Stockage:                                                       │
│  - PostgreSQL: 500GB SSD NVMe (x3)                              │
│  - MinIO: 2TB HDD RAID (x4)                                     │
│  - Redis: 16GB SSD (x3)                                         │
└──────────────────────────────────────────────────────────────────┘
```

### Composants

| Composant | Technologie | Version | Rôle | RGPD |
|-----------|-------------|---------|------|------|
| **Orchestration** | Kubernetes (RKE2) | 1.28+ | Gestion containers | ✅ |
| **Base de données** | PostgreSQL | 15 | Utilisateurs, projets | ✅ Paris |
| **Stockage objet** | MinIO | Latest | Fichiers projets | ✅ Paris |
| **Cache** | Redis | 7 | Sessions, cache | ✅ Paris |
| **App Server** | Tomcat / Jetty | 9+ | App Inventor web | ✅ Paris |
| **Build Server** | Custom Java | - | Compilation APK | ✅ Paris |
| **Load Balancer** | HAProxy / Nginx | Latest | Répartition charge | ✅ Paris |
| **Monitoring** | Prometheus+Grafana | Latest | Supervision | ✅ Paris |

**Toutes les données restent en France (Paris DSI) - Conformité RGPD garantie**

---

## 2. Prérequis Infrastructure

### 2.1 Serveurs Disponibles

**Configuration minimale :**
- CPU : 72 cores (Xeon)
- RAM : 512 GB
- Stockage SSD : 2 TB (NVMe pour PostgreSQL)
- Stockage HDD : 8-12 TB (RAID pour MinIO)
- Réseau : 10 Gbit/s (recommandé)
- Localisation : **Paris DSI uniquement**

**Nombre de serveurs :**
- Minimum : 3 serveurs (pour haute disponibilité)
- Recommandé : 4-5 serveurs
- Configuration : Identiques si possible

### 2.2 Réseau

**Requirements :**
- VLAN dédié pour le cluster Kubernetes
- Adresses IP statiques (au moins 10-20 IPs)
- Nom de domaine : `appinventor.dsi.paris.fr` (exemple)
- Certificat TLS (Let's Encrypt ou interne)
- Firewall configuré :
  - Port 443 (HTTPS) : Internet → Load Balancer
  - Port 80 (HTTP) : Redirect vers 443
  - Port 6443 (K8s API) : Admin uniquement
  - Ports internes : Communication inter-pods

### 2.3 Stockage

**Organisation recommandée sur chaque serveur :**

```
/mnt/
├── ssd-nvme/              # SSD NVMe (raid 1 ou 10)
│   ├── postgresql-0/      # 500 GB (node 1)
│   ├── postgresql-1/      # 500 GB (node 2)
│   ├── postgresql-2/      # 500 GB (node 3)
│   └── redis/             # 16 GB par node
│
└── hdd-raid/              # HDD RAID 10 ou 6
    ├── minio-0/           # 2 TB (node 1)
    ├── minio-1/           # 2 TB (node 2)
    ├── minio-2/           # 2 TB (node 3)
    └── minio-3/           # 2 TB (node 4)
```

**Commandes de préparation :**

```bash
# Sur chaque serveur
sudo mkdir -p /mnt/ssd-nvme/{postgresql-{0..2},redis}
sudo mkdir -p /mnt/hdd-raid/minio-{0..3}

# Permissions
sudo chown -R 1000:1000 /mnt/ssd-nvme
sudo chown -R 1000:1000 /mnt/hdd-raid

# Vérifier les montages
df -h | grep mnt
```

### 2.4 Système d'Exploitation

**OS recommandé :**
- **Ubuntu Server 22.04 LTS** (recommandé)
- Ou Rocky Linux 9 (CentOS successor)
- Ou Debian 12

**Configuration minimale :**

```bash
# Désactiver swap (requis par Kubernetes)
sudo swapoff -a
sudo sed -i '/ swap / s/^/#/' /etc/fstab

# Modules kernel requis
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter

# Paramètres sysctl
cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sudo sysctl --system

# Installer les paquets de base
sudo apt update
sudo apt install -y curl wget git vim htop iotop net-tools
```

---

## 3. Préparation des Serveurs

### 3.1 Inventaire des Serveurs

**Créer un fichier `inventory.txt` :**

```
# Format: hostname,ip,role
node1,192.168.1.101,control-plane+worker
node2,192.168.1.102,worker
node3,192.168.1.103,worker
node4,192.168.1.104,worker
```

**À adapter à votre infrastructure !**

### 3.2 Configuration DNS

**Ajouter dans votre DNS interne :**

```
# Forward
appinventor.dsi.paris.fr.        IN A    192.168.1.100  # Load Balancer VIP
minio-console.dsi.paris.fr.      IN A    192.168.1.100
grafana.dsi.paris.fr.            IN A    192.168.1.100

# Nodes
node1.cluster.local.             IN A    192.168.1.101
node2.cluster.local.             IN A    192.168.1.102
node3.cluster.local.             IN A    192.168.1.103
node4.cluster.local.             IN A    192.168.1.104
```

### 3.3 SSH et Accès

**Configurer l'accès SSH sans mot de passe depuis un jump host :**

```bash
# Sur votre poste d'administration
ssh-keygen -t ed25519 -C "dsi-admin@paris"

# Copier la clé sur tous les nodes
for i in {101..104}; do
  ssh-copy-id root@192.168.1.$i
done

# Tester
for i in {101..104}; do
  ssh root@192.168.1.$i 'hostname'
done
```

### 3.4 Synchronisation Temps (NTP)

**Essentiel pour PostgreSQL et Kubernetes :**

```bash
# Sur tous les nodes
sudo apt install -y chrony

# Configurer NTP
sudo cat <<EOF > /etc/chrony/chrony.conf
server ntp.paris.dsi.fr iburst  # Votre serveur NTP interne
driftfile /var/lib/chrony/drift
makestep 1.0 3
rtcsync
EOF

sudo systemctl restart chrony
sudo systemctl enable chrony

# Vérifier
chronyc tracking
```

---

## 4. Installation Kubernetes

### 4.1 Choix de la Distribution

**RKE2 (Rancher Kubernetes Engine 2)** - Recommandé pour votre cas

**Avantages :**
- ✅ RGPD compliant
- ✅ Support SUSE (entreprise européenne)
- ✅ Hardened security (CIS benchmarks)
- ✅ Installation air-gap possible
- ✅ Intégration Rancher UI (optionnelle)

**Alternative :** K3s (plus léger) ou kubeadm (vanilla)

### 4.2 Installation RKE2

**Sur le premier node (control-plane) :**

```bash
# Télécharger le script d'installation
curl -sfL https://get.rke2.io | sh -

# Configuration
sudo mkdir -p /etc/rancher/rke2

cat <<EOF | sudo tee /etc/rancher/rke2/config.yaml
# Configuration RKE2
write-kubeconfig-mode: "0644"
tls-san:
  - appinventor.dsi.paris.fr
  - 192.168.1.100
cluster-cidr: 10.42.0.0/16
service-cidr: 10.43.0.0/16
cluster-dns: 10.43.0.10
cni:
  - calico  # ou cilium
EOF

# Démarrer RKE2
sudo systemctl enable rke2-server.service
sudo systemctl start rke2-server.service

# Attendre que RKE2 soit prêt
sudo journalctl -u rke2-server -f

# Récupérer le token pour les workers
sudo cat /var/lib/rancher/rke2/server/node-token
# Sauvegarder ce token !

# Configurer kubectl
mkdir -p ~/.kube
sudo cp /etc/rancher/rke2/rke2.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config

# Ajouter au PATH
echo 'export PATH=$PATH:/var/lib/rancher/rke2/bin' >> ~/.bashrc
echo 'export KUBECONFIG=~/.kube/config' >> ~/.bashrc
source ~/.bashrc

# Vérifier
kubectl get nodes
```

**Sur les nodes workers (node2, node3, node4) :**

```bash
# Télécharger le script
curl -sfL https://get.rke2.io | INSTALL_RKE2_TYPE="agent" sh -

# Configuration
sudo mkdir -p /etc/rancher/rke2

cat <<EOF | sudo tee /etc/rancher/rke2/config.yaml
server: https://192.168.1.101:9345  # IP du control-plane
token: <TOKEN_FROM_CONTROL_PLANE>   # Token récupéré plus haut
EOF

# Démarrer
sudo systemctl enable rke2-agent.service
sudo systemctl start rke2-agent.service

# Vérifier les logs
sudo journalctl -u rke2-agent -f
```

**Vérification du cluster :**

```bash
# Sur le control-plane
kubectl get nodes

# Devrait afficher :
# NAME    STATUS   ROLES                       AGE   VERSION
# node1   Ready    control-plane,etcd,master   5m    v1.28.x+rke2r1
# node2   Ready    <none>                      3m    v1.28.x+rke2r1
# node3   Ready    <none>                      3m    v1.28.x+rke2r1
# node4   Ready    <none>                      3m    v1.28.x+rke2r1
```

### 4.3 Installation kubectl et Helm

**kubectl (déjà installé avec RKE2) :**

```bash
kubectl version
```

**Helm (gestionnaire de packages K8s) :**

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Vérifier
helm version
```

### 4.4 Labels et Taints (optionnel)

**Labeler les nodes pour contraintes de déploiement :**

```bash
# Label pour identifier les nodes avec SSD
kubectl label nodes node1 storage=ssd
kubectl label nodes node2 storage=ssd
kubectl label nodes node3 storage=ssd

# Label pour identifier les nodes avec HDD
kubectl label nodes node1 storage=hdd
kubectl label nodes node2 storage=hdd
kubectl label nodes node3 storage=hdd
kubectl label nodes node4 storage=hdd

# Vérifier
kubectl get nodes --show-labels
```

---

## 5. Déploiement des Composants

### 5.1 Ordre de Déploiement

**Suivre cet ordre strictement :**

1. ✅ Namespace
2. ✅ Storage Classes et PersistentVolumes
3. ✅ Secrets
4. ✅ PostgreSQL (base de données)
5. ✅ Redis (cache)
6. ✅ MinIO (stockage objet)
7. ✅ App Inventor (application)
8. ✅ Build Server
9. ✅ Rendezvous Server
10. ✅ Ingress Controller (load balancer)
11. ✅ Monitoring (Prometheus + Grafana)

### 5.2 Déploiement Étape par Étape

**Préparation :**

```bash
# Cloner ou copier les manifestes K8s
cd /opt
git clone <votre-repo>/appinventor-deployment
cd appinventor-deployment/kubernetes

# OU copier les fichiers manuellement
```

**Étape 1 : Namespace**

```bash
kubectl apply -f 00-namespace.yaml

# Vérifier
kubectl get namespaces
```

**Étape 2 : Storage Classes**

**Important :** AVANT d'appliquer `01-storage-classes.yaml`, **éditer le fichier** :

```bash
# Éditer pour remplacer les noms de nodes
nano 01-storage-classes.yaml

# Remplacer :
# - your-node-1 → nom réel (kubectl get nodes)
# - your-node-2 → nom réel
# - your-node-3 → nom réel
# - your-node-4 → nom réel

# Appliquer
kubectl apply -f 01-storage-classes.yaml

# Vérifier
kubectl get pv
kubectl get storageclass
```

**Étape 3 : Secrets**

**⚠️ SÉCURITÉ : NE PAS utiliser les valeurs d'exemple en production !**

```bash
# Méthode 1 : Éditer 02-secrets.yaml avec vos vraies valeurs
# puis kubectl apply -f 02-secrets.yaml

# Méthode 2 (RECOMMANDÉ) : Créer les secrets en ligne de commande
# MinIO
kubectl create secret generic minio-credentials \
  --from-literal=root-user=appinventor-admin \
  --from-literal=root-password=$(openssl rand -base64 32) \
  -n appinventor

# PostgreSQL
kubectl create secret generic postgresql-credentials \
  --from-literal=postgres-password=$(openssl rand -base64 32) \
  --from-literal=appinventor-password=$(openssl rand -base64 32) \
  -n appinventor

# Redis
kubectl create secret generic redis-credentials \
  --from-literal=redis-password=$(openssl rand -base64 32) \
  -n appinventor

# App Inventor
kubectl create secret generic appinventor-secrets \
  --from-literal=build-server-password=$(openssl rand -base64 32) \
  --from-literal=clouddb-uuid-secret=$(openssl rand -base64 32) \
  --from-literal=session-secret=$(openssl rand -base64 32) \
  -n appinventor

# TLS (certificat auto-signé pour tests)
openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /tmp/tls.key -out /tmp/tls.crt \
  -subj "/CN=appinventor.dsi.paris.fr/O=DSI Paris"

kubectl create secret tls appinventor-tls \
  --cert=/tmp/tls.crt \
  --key=/tmp/tls.key \
  -n appinventor

# Nettoyer les fichiers temporaires
rm /tmp/tls.{key,crt}

# Vérifier
kubectl get secrets -n appinventor
```

**Sauvegarder les mots de passe :**

```bash
# Exporter dans un fichier sécurisé (À PROTÉGER !)
kubectl get secrets -n appinventor -o yaml > /root/appinventor-secrets-backup.yaml
chmod 600 /root/appinventor-secrets-backup.yaml
```

**Étape 4 : MinIO**

```bash
kubectl apply -f minio/minio-statefulset.yaml

# Attendre que les pods soient prêts (peut prendre 5-10 min)
kubectl get pods -n appinventor -l app=minio -w

# Vérifier les logs
kubectl logs -n appinventor minio-0 -f

# Vérifier le statut
kubectl get statefulset -n appinventor
kubectl get pvc -n appinventor

# Une fois tous les pods Running (4/4), créer les buckets
kubectl exec -n appinventor minio-0 -- mc alias set local http://localhost:9000 \
  $(kubectl get secret minio-credentials -n appinventor -o jsonpath='{.data.root-user}' | base64 -d) \
  $(kubectl get secret minio-credentials -n appinventor -o jsonpath='{.data.root-password}' | base64 -d)

kubectl exec -n appinventor minio-0 -- mc mb local/appinventor-projects
kubectl exec -n appinventor minio-0 -- mc mb local/appinventor-builds

# Configurer lifecycle pour builds (suppression après 1 jour)
kubectl exec -n appinventor minio-0 -- mc ilm import local/appinventor-builds <<EOF
{
  "Rules": [
    {
      "ID": "DeleteOldBuilds",
      "Status": "Enabled",
      "Expiration": {
        "Days": 1
      }
    }
  ]
}
EOF

# Activer le chiffrement (RGPD)
kubectl exec -n appinventor minio-0 -- mc encrypt set sse-s3 local/appinventor-projects
kubectl exec -n appinventor minio-0 -- mc encrypt set sse-s3 local/appinventor-builds
```

**Étape 5 : PostgreSQL**

(Les manifestes PostgreSQL seront créés dans la prochaine partie car c'est assez volumineux)

**Pour l'instant, voici les commandes une fois les manifestes appliqués :**

```bash
kubectl apply -f postgresql/postgresql-statefulset.yaml

# Attendre (3-5 min)
kubectl get pods -n appinventor -l app=postgresql -w

# Vérifier
kubectl logs -n appinventor postgresql-0 -f

# Créer la base de données
kubectl exec -n appinventor postgresql-0 -- psql -U postgres <<EOF
CREATE DATABASE appinventor;
CREATE USER appinventor WITH ENCRYPTED PASSWORD 'GET_FROM_SECRET';
GRANT ALL PRIVILEGES ON DATABASE appinventor TO appinventor;
\c appinventor
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- Pour chiffrement
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- Pour UUIDs
\q
EOF

# Appliquer le schéma SQL (voir PHASE4_MIGRATION_PLAN.md)
kubectl exec -i -n appinventor postgresql-0 -- psql -U appinventor -d appinventor < schema.sql
```

**Étape 6 : Redis**

```bash
kubectl apply -f redis/redis-statefulset.yaml

# Vérifier
kubectl get pods -n appinventor -l app=redis -w
kubectl logs -n appinventor redis-0 -f
```

**Étape 7-10 : App Inventor, Build Server, Rendezvous, Ingress**

(Manifestes à créer - voir sections suivantes)

---

## 6. Configuration App Inventor

### 6.1 Préparer l'Image Docker

**Option A : Utiliser une image pré-built (si disponible)**

```bash
docker pull ghcr.io/mit-cml/appinventor:latest
```

**Option B : Build votre propre image**

```dockerfile
# Dockerfile pour App Inventor
FROM tomcat:9-jdk17

# Copier le WAR
COPY appengine/build/war/ /usr/local/tomcat/webapps/ROOT/

# Configuration
ENV CATALINA_OPTS="-Xms4G -Xmx8G"
ENV JAVA_OPTS="-Dstorage.backend=s3 -Ds3.endpoint=http://minio:9000"

EXPOSE 8080
CMD ["catalina.sh", "run"]
```

```bash
# Build
cd /path/to/appinventor-sources
docker build -t appinventor:local -f Dockerfile .

# Tag et push vers un registry local (optionnel)
docker tag appinventor:local registry.dsi.paris.fr/appinventor:latest
docker push registry.dsi.paris.fr/appinventor:latest
```

### 6.2 ConfigMap pour Configuration

**Créer `appinventor/appinventor-configmap.yaml` :**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: appinventor-config
  namespace: appinventor
data:
  # Configuration générale
  app-name: "MIT App Inventor - DSI Paris"

  # Storage backend (Phase 3)
  storage.backend: "s3"
  s3.endpoint: "http://minio:9000"
  s3.region: "eu-west-3"
  s3.path.style: "true"
  gcs.bucket: "appinventor-projects"
  gcs.apkbucket: "appinventor-builds"

  # Database backend (Phase 4)
  database.backend: "postgresql"
  db.host: "postgresql"
  db.port: "5432"
  db.name: "appinventor"

  # Redis (Phase 1)
  cache.backend: "redis"
  redis.host: "redis"
  redis.port: "6379"

  # Rendezvous
  use.rendezvousserver: "http://rendezvous:3000"

  # Build server
  build.server.host: "buildserver:9990"

  # Sessions
  session.idletimeout: "120"
  session.renew: "30"
```

### 6.3 Deployment App Inventor

(Manifest complet dans la section suivante)

---

## 7. Tests et Validation

### 7.1 Tests Unitaires par Composant

**MinIO :**

```bash
# Test connectivity
kubectl run -it --rm debug --image=minio/mc --restart=Never -n appinventor -- \
  mc alias set minio http://minio:9000 ACCESS_KEY SECRET_KEY

# Test upload/download
echo "test" > /tmp/test.txt
kubectl exec -n appinventor minio-0 -- mc cp /tmp/test.txt minio/appinventor-projects/
kubectl exec -n appinventor minio-0 -- mc cat minio/appinventor-projects/test.txt
```

**PostgreSQL :**

```bash
# Test connexion
kubectl run -it --rm psql --image=postgres:15 --restart=Never -n appinventor -- \
  psql -h postgresql -U appinventor -d appinventor -c "SELECT version();"

# Test tables
kubectl exec -n appinventor postgresql-0 -- psql -U appinventor -d appinventor -c "\dt"
```

**Redis :**

```bash
# Test connexion
kubectl run -it --rm redis-cli --image=redis:7 --restart=Never -n appinventor -- \
  redis-cli -h redis -a PASSWORD ping

# Test set/get
kubectl exec -n appinventor redis-0 -- redis-cli -a PASSWORD SET test "hello"
kubectl exec -n appinventor redis-0 -- redis-cli -a PASSWORD GET test
```

### 7.2 Tests d'Intégration

**Test App Inventor complet :**

```bash
# Port-forward pour accès local
kubectl port-forward -n appinventor svc/appinventor 8080:8080

# Ouvrir dans le navigateur
http://localhost:8080
```

**Checklist de validation :**

- [ ] Page d'accueil s'affiche
- [ ] Login fonctionne
- [ ] Créer un projet
- [ ] Ouvrir l'éditeur de blocs
- [ ] Ajouter un composant
- [ ] Sauvegarder le projet
- [ ] Build APK
- [ ] Télécharger l'APK
- [ ] Companion app se connecte

### 7.3 Tests de Charge

**Utiliser k6 ou Apache Bench :**

```bash
# Installer k6
wget https://github.com/grafana/k6/releases/download/v0.47.0/k6-v0.47.0-linux-amd64.tar.gz
tar -xzf k6-v0.47.0-linux-amd64.tar.gz
sudo mv k6-v0.47.0-linux-amd64/k6 /usr/local/bin/

# Script de test
cat <<EOF > load-test.js
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 },  // Monter à 100 users
    { duration: '5m', target: 100 },  // Rester à 100 users
    { duration: '2m', target: 200 },  // Pic à 200 users
    { duration: '5m', target: 200 },
    { duration: '2m', target: 0 },    // Descente
  ],
};

export default function () {
  let res = http.get('https://appinventor.dsi.paris.fr');
  check(res, { 'status was 200': (r) => r.status == 200 });
  sleep(1);
}
EOF

# Lancer le test
k6 run load-test.js
```

---

## 8. Monitoring et Maintenance

### 8.1 Prometheus + Grafana

**Installation via Helm :**

```bash
# Ajouter le repo Prometheus
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Installer kube-prometheus-stack
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace appinventor \
  --set prometheus.prometheusSpec.retention=30d \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi

# Port-forward Grafana
kubectl port-forward -n appinventor svc/prometheus-grafana 3000:80

# Login: admin / prom-operator (changer !)
```

**Dashboards à importer :**
- Kubernetes Cluster Monitoring (ID: 7249)
- PostgreSQL Database (ID: 9628)
- Redis (ID: 11835)
- MinIO (ID: 13502)

### 8.2 Logs Centralisés (ELK - optionnel)

**Si besoin de logs centralisés :**

```bash
helm repo add elastic https://helm.elastic.co
helm install elasticsearch elastic/elasticsearch -n appinventor
helm install kibana elastic/kibana -n appinventor
helm install filebeat elastic/filebeat -n appinventor
```

### 8.3 Backups

**PostgreSQL Backups :**

```bash
# Backup manuel
kubectl exec -n appinventor postgresql-0 -- pg_dump -U postgres appinventor | gzip > appinventor-backup-$(date +%Y%m%d).sql.gz

# Backup automatique (CronJob K8s)
kubectl apply -f - <<EOF
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgresql-backup
  namespace: appinventor
spec:
  schedule: "0 2 * * *"  # Tous les jours à 2h
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/sh
            - -c
            - pg_dump -h postgresql -U postgres appinventor | gzip > /backup/appinventor-\$(date +\%Y\%m\%d).sql.gz
            volumeMounts:
            - name: backup
              mountPath: /backup
          volumes:
          - name: backup
            persistentVolumeClaim:
              claimName: postgresql-backup-pvc
          restartPolicy: OnFailure
EOF
```

**MinIO Backups :**

```bash
# Backup via mc mirror
kubectl exec -n appinventor minio-0 -- mc mirror local/appinventor-projects /backup/minio/
```

### 8.4 Mises à Jour

**Stratégie Rolling Update :**

```bash
# Update de l'image App Inventor
kubectl set image deployment/appinventor \
  appinventor=appinventor:new-version \
  -n appinventor

# Suivre le rollout
kubectl rollout status deployment/appinventor -n appinventor

# Rollback si problème
kubectl rollout undo deployment/appinventor -n appinventor
```

---

## 9. Procédures Opérationnelles

### 9.1 Démarrage à Froid du Cluster

```bash
# 1. Vérifier que tous les serveurs sont up
ansible all -m ping

# 2. Démarrer RKE2 sur control-plane
ssh node1 'sudo systemctl start rke2-server'

# 3. Attendre que le control-plane soit ready
kubectl get nodes

# 4. Démarrer les workers
for i in {2..4}; do
  ssh node$i 'sudo systemctl start rke2-agent'
done

# 5. Vérifier le cluster
kubectl get nodes
kubectl get pods -A

# 6. Si des pods sont Pending, vérifier les PVs
kubectl get pv
```

### 9.2 Arrêt Propre du Cluster

```bash
# 1. Drain les workers (migrer les pods)
for i in {2..4}; do
  kubectl drain node$i --ignore-daemonsets --delete-emptydir-data
done

# 2. Arrêter les workers
for i in {2..4}; do
  ssh node$i 'sudo systemctl stop rke2-agent'
done

# 3. Arrêter le control-plane
kubectl drain node1 --ignore-daemonsets --delete-emptydir-data
ssh node1 'sudo systemctl stop rke2-server'
```

### 9.3 Ajout d'un Node

```bash
# Sur le nouveau node (node5)
curl -sfL https://get.rke2.io | INSTALL_RKE2_TYPE="agent" sh -

cat <<EOF | sudo tee /etc/rancher/rke2/config.yaml
server: https://192.168.1.101:9345
token: <TOKEN_FROM_CONTROL_PLANE>
EOF

sudo systemctl enable rke2-agent.service
sudo systemctl start rke2-agent.service

# Vérifier sur le control-plane
kubectl get nodes
```

### 9.4 Suppression d'un Node

```bash
# Drain le node
kubectl drain node4 --ignore-daemonsets --delete-emptydir-data --force

# Supprimer du cluster
kubectl delete node node4

# Sur le node physique
ssh node4 'sudo /usr/local/bin/rke2-uninstall.sh'
```

---

## 10. Troubleshooting

### 10.1 Problèmes Courants

**Pod en CrashLoopBackOff :**

```bash
# Vérifier les logs
kubectl logs -n appinventor <pod-name>
kubectl describe pod -n appinventor <pod-name>

# Vérifier les events
kubectl get events -n appinventor --sort-by='.lastTimestamp'
```

**PVC en Pending :**

```bash
# Vérifier les PV disponibles
kubectl get pv

# Vérifier les events du PVC
kubectl describe pvc -n appinventor <pvc-name>

# Problème fréquent : node affinity non respectée
# Solution : vérifier les labels des nodes et PV
```

**PostgreSQL ne démarre pas :**

```bash
# Vérifier les permissions du volume
kubectl exec -n appinventor postgresql-0 -- ls -la /var/lib/postgresql/data

# Vérifier le mot de passe
kubectl get secret postgresql-credentials -n appinventor -o jsonpath='{.data.postgres-password}' | base64 -d

# Vérifier les logs
kubectl logs -n appinventor postgresql-0 -f
```

**MinIO ne forme pas de cluster :**

```bash
# Vérifier la résolution DNS
kubectl exec -n appinventor minio-0 -- nslookup minio-1.minio-headless.appinventor.svc.cluster.local

# Vérifier les endpoints
kubectl get endpoints -n appinventor minio-headless

# Vérifier les logs de tous les pods
for i in {0..3}; do
  echo "=== minio-$i ==="
  kubectl logs -n appinventor minio-$i --tail=50
done
```

**App Inventor ne se connecte pas à PostgreSQL :**

```bash
# Test depuis un pod de debug
kubectl run -it --rm psql-test --image=postgres:15 --restart=Never -n appinventor -- \
  psql -h postgresql -U appinventor -d appinventor -c "SELECT 1;"

# Vérifier la config
kubectl get configmap appinventor-config -n appinventor -o yaml

# Vérifier les variables d'environnement du pod
kubectl exec -n appinventor <appinventor-pod> -- env | grep -i db
```

### 10.2 Commandes Utiles

```bash
# Voir tous les pods
kubectl get pods -A

# Voir tous les services
kubectl get svc -A

# Voir toutes les PVC
kubectl get pvc -A

# Top nodes (CPU/RAM)
kubectl top nodes

# Top pods
kubectl top pods -n appinventor

# Describe d'un pod
kubectl describe pod -n appinventor <pod-name>

# Logs avec suivi
kubectl logs -f -n appinventor <pod-name>

# Exec dans un pod
kubectl exec -it -n appinventor <pod-name> -- /bin/bash

# Port-forward
kubectl port-forward -n appinventor svc/appinventor 8080:8080

# Get events
kubectl get events -n appinventor --sort-by='.lastTimestamp'

# Restart un deployment
kubectl rollout restart deployment/appinventor -n appinventor

# Scale un deployment
kubectl scale deployment/appinventor --replicas=10 -n appinventor
```

### 10.3 Contacts et Escalade

**Support Interne :**
- Équipe DSI : support-dsi@paris.fr
- Administrateur Kubernetes : admin-k8s@paris.fr
- DBA PostgreSQL : dba@paris.fr

**Support Externe (si contrat) :**
- SUSE (RKE2) : support.suse.com
- PostgreSQL : support@postgresql.org
- MIT App Inventor : appinventor@mit.edu

---

## 11. Conformité RGPD

### 11.1 Checklist de Conformité

- [x] Données stockées en France (Paris DSI)
- [x] Pas de transfert vers USA (Patriot Act)
- [x] Chiffrement au repos (MinIO SSE, PostgreSQL TDE possible)
- [x] Chiffrement en transit (TLS everywhere)
- [x] Audit logs (kubectl logs, ELK)
- [x] Droit à l'oubli (DELETE CASCADE SQL)
- [x] Portabilité (export SQL + MinIO)
- [x] Consentement (users.tos_accepted)
- [ ] Procédure mineurs (à implémenter selon besoins)
- [ ] DPO informé et documentation

### 11.2 Registre de Traitement

**À documenter pour le DPO :**

| Donnée | Finalité | Base légale | Durée conservation |
|--------|----------|-------------|-------------------|
| Email élève | Authentification | Consentement | Scolarité + 1 an |
| Projets | Pédagogie | Mission éducation | Scolarité + 1 an |
| Logs accès | Sécurité | Intérêt légitime | 1 an |
| IP | Anti-abus | Intérêt légitime | 6 mois |

### 11.3 Procédures RGPD

**Droit d'accès :**

```sql
-- Exporter toutes les données d'un utilisateur
SELECT * FROM users WHERE id = 'user123';
SELECT * FROM projects WHERE id IN (
  SELECT project_id FROM user_projects WHERE user_id = 'user123'
);
-- etc.
```

**Droit à l'oubli :**

```sql
-- Supprimer un utilisateur et toutes ses données
DELETE FROM users WHERE id = 'user123';
-- CASCADE supprime automatiquement user_projects, user_files, etc.
```

**Portabilité :**

```bash
# Export SQL
kubectl exec -n appinventor postgresql-0 -- pg_dump -U appinventor --data-only \
  -t users -t projects -t files \
  --where="user_id='user123'" appinventor > user123-export.sql

# Export fichiers MinIO
kubectl exec -n appinventor minio-0 -- mc mirror local/appinventor-projects/user123/ /export/
```

---

## 12. Annexes

### 12.1 Checklist de Déploiement

**Phase Préparation :**
- [ ] Serveurs provisionnés (3-4 machines, Xeon, 512 GB RAM)
- [ ] OS installé et configuré (Ubuntu 22.04 LTS)
- [ ] Réseau configuré (VLAN, IPs statiques)
- [ ] DNS configuré (appinventor.dsi.paris.fr)
- [ ] Stockage préparé (/mnt/ssd-nvme, /mnt/hdd-raid)
- [ ] NTP synchronisé
- [ ] SSH configuré

**Phase Kubernetes :**
- [ ] RKE2 installé (control-plane + workers)
- [ ] kubectl et helm installés
- [ ] Cluster accessible (kubectl get nodes)
- [ ] Nodes labelés

**Phase Déploiement :**
- [ ] Namespace créé
- [ ] Storage classes créés
- [ ] PersistentVolumes créés
- [ ] Secrets créés (avec mots de passe forts !)
- [ ] PostgreSQL déployé et initialisé
- [ ] Redis déployé
- [ ] MinIO déployé et buckets créés
- [ ] App Inventor déployé
- [ ] Build Server déployé
- [ ] Rendezvous déployé
- [ ] Ingress Controller déployé
- [ ] TLS configuré

**Phase Validation :**
- [ ] Tests unitaires passés
- [ ] Tests d'intégration passés
- [ ] Test de bout en bout passé (créer un projet, build APK)
- [ ] Tests de charge OK
- [ ] Monitoring configuré
- [ ] Backups configurés
- [ ] Documentation à jour

**Phase Production :**
- [ ] Formation équipe DSI
- [ ] Runbooks opérationnels écrits
- [ ] Procédures de backup testées
- [ ] Plan de disaster recovery
- [ ] DPO informé
- [ ] Registre RGPD à jour
- [ ] Communication aux utilisateurs

### 12.2 Ressources Utiles

**Documentation :**
- RKE2 : https://docs.rke2.io/
- Kubernetes : https://kubernetes.io/docs/
- PostgreSQL : https://www.postgresql.org/docs/
- MinIO : https://min.io/docs/
- Redis : https://redis.io/documentation

**Communautés :**
- Kubernetes Slack : kubernetes.slack.com
- PostgreSQL ML : pgsql-general@lists.postgresql.org
- MinIO Slack : slack.min.io

**Support :**
- SUSE (RKE2) : support.suse.com
- Rancher Forums : forums.rancher.com

### 12.3 Glossaire

- **K8s** : Kubernetes
- **PV** : PersistentVolume
- **PVC** : PersistentVolumeClaim
- **SVC** : Service
- **DS** : DaemonSet
- **SS** : StatefulSet
- **RS** : ReplicaSet
- **CM** : ConfigMap
- **HA** : High Availability
- **RBAC** : Role-Based Access Control
- **CRD** : Custom Resource Definition
- **CNI** : Container Network Interface
- **CSI** : Container Storage Interface

---

## 13. Conclusion

Ce guide couvre le déploiement complet de MIT App Inventor sur infrastructure on-premise 100% RGPD compliant.

**Prochaines étapes :**
1. Valider ce guide avec votre équipe DSI
2. Ajuster les configurations selon votre infrastructure exacte
3. Planifier une fenêtre de déploiement (2-3 jours)
4. Former l'équipe
5. Déployer en environnement de test d'abord
6. Valider et passer en production

**Support :**
Pour toute question ou problème, référez-vous aux sections Troubleshooting ou contactez l'équipe de migration App Inventor.

**Version du document :** 1.0
**Dernière mise à jour :** 2024
**Auteur :** Équipe Migration App Inventor (Google-free)
**Contact :** appinventor-migration@dsi.paris.fr

---

**🇫🇷 Ce déploiement garantit une conformité RGPD totale avec données hébergées exclusivement à Paris. 🇫🇷**
