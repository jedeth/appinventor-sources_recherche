# 🚀 Prompt Claude Code - Installation Automatique App Inventor DSI Paris

**Usage**: Copiez ce prompt dans Claude Code sur votre serveur DSI Paris pour automatiser toute l'installation.

---

## PROMPT À COPIER DANS CLAUDE CODE

```
Tu es un expert DevOps spécialisé dans les déploiements Kubernetes et Java.

CONTEXTE:
Je suis connecté en SSH sur mon serveur Ubuntu/Rocky Linux avec accès sudo.
Je veux installer App Inventor en version on-premise (DSI Paris) avec:
- PostgreSQL 15 (base de données)
- Redis 7.2 (cache)
- MinIO (stockage fichiers)
- App Inventor compilé avec Phase 4 (nouveau layer database PostgreSQL)

OBJECTIF:
Installer et déployer COMPLÈTEMENT le système de façon automatisée.

CONTRAINTES IMPORTANTES:
1. Je ne suis ni développeur ni admin sys
2. Je veux une installation 100% automatisée
3. Tu dois TOUJOURS me montrer les commandes avant de les exécuter
4. Tu dois VÉRIFIER chaque étape avant de passer à la suivante
5. En cas d'erreur, tu dois diagnostiquer et corriger automatiquement
6. Tu dois tester la connexion à la fin

ÉTAPES À SUIVRE:

## Phase 1: Préparation Environnement (15 min)

1. **Détecter l'OS** et installer les dépendances de base:
   - Ubuntu: apt-get
   - Rocky Linux: dnf
   - Packages: git, curl, wget, openssl, jq, vim

2. **Vérifier les prérequis**:
   - Minimum 16GB RAM disponible
   - Minimum 100GB disque disponible
   - Accès sudo fonctionnel
   - Connexion internet active

3. **Installer Java Development Kit**:
   - OpenJDK 8 (pour App Inventor build)
   - OpenJDK 17 (pour runtime moderne)
   - Ant 1.10+ (build tool)
   - Maven 3.8+ (dependency management)
   - Vérifier JAVA_HOME et PATH

## Phase 2: Installation Kubernetes (20 min)

4. **Installer RKE2** (Rancher Kubernetes):
   ```bash
   curl -sfL https://get.rke2.io | sudo sh -
   sudo systemctl enable rke2-server.service
   sudo systemctl start rke2-server.service
   ```

5. **Configurer kubectl**:
   ```bash
   sudo cp /var/lib/rancher/rke2/bin/kubectl /usr/local/bin/
   mkdir -p ~/.kube
   sudo cp /etc/rancher/rke2/rke2.yaml ~/.kube/config
   sudo chown $(id -u):$(id -g) ~/.kube/config
   ```

6. **Vérifier cluster**:
   ```bash
   kubectl get nodes
   kubectl get pods -A
   ```

## Phase 3: Déploiement Bases de Données (30 min)

7. **Créer namespace**:
   ```bash
   kubectl create namespace appinventor
   ```

8. **Générer secrets sécurisés**:
   - PostgreSQL superuser password
   - PostgreSQL app user password
   - Redis password
   - MinIO root user/password
   - Utiliser `openssl rand -base64 24`

9. **Déployer PostgreSQL avec Patroni**:
   - Appliquer `postgresql-statefulset.yaml`
   - Attendre que les 3 pods soient Ready
   - Vérifier connexion avec psql

10. **Déployer Redis avec Sentinel**:
    - Appliquer `redis-statefulset.yaml`
    - Vérifier cluster avec redis-cli

11. **Déployer MinIO**:
    - Appliquer `minio-statefulset.yaml`
    - Créer bucket "appinventor-files"

## Phase 4: BUILD App Inventor (45 min) ⚠️ CRUCIAL

12. **Cloner repository** (si pas déjà fait):
    ```bash
    cd /opt
    git clone https://github.com/jedeth/appinventor-sources_recherche.git
    cd appinventor-sources_recherche
    git checkout claude/analyze-repository-011CV3oQ12QxRTtV5uUmfkmr
    ```

13. **Installer dépendances Phase 4**:
    ```bash
    cd appinventor/appengine
    # Ajouter dépendances PostgreSQL/HikariCP au pom.xml
    # (déjà dans pom-phase4-dependencies.xml)
    ```

14. **Configuration build.properties**:
    Créer/modifier `appinventor/buildserver/build.properties`:
    ```properties
    # Database Configuration (Phase 4)
    db.type=postgresql
    db.host=postgresql-0.postgresql-headless.appinventor.svc.cluster.local
    db.port=5432
    db.name=appinventor
    db.user=appinventor_user
    db.password=<GENERATED_PASSWORD>

    # Redis Configuration
    redis.host=redis-0.redis-headless.appinventor.svc.cluster.local
    redis.port=6379
    redis.password=<GENERATED_PASSWORD>

    # MinIO Configuration
    minio.endpoint=http://minio.appinventor.svc.cluster.local:9000
    minio.access.key=<GENERATED_ACCESS_KEY>
    minio.secret.key=<GENERATED_SECRET_KEY>
    minio.bucket=appinventor-files
    ```

15. **Compiler App Inventor**:
    ```bash
    cd /opt/appinventor-sources_recherche/appinventor

    # Build full
    ant clean
    ant MakeAuthKey
    ant

    # Vérifier build success
    ls -lh build/war/
    ```

16. **Créer image Docker**:
    Créer `Dockerfile` dans `/opt/appinventor-sources_recherche/appinventor/`:
    ```dockerfile
    FROM openjdk:8-jre-alpine

    # Install runtime deps
    RUN apk add --no-cache bash curl

    # Copy WAR file
    COPY build/war /app/war

    # Copy configuration
    COPY appengine/build/war/WEB-INF /app/war/WEB-INF

    # Expose port
    EXPOSE 8080

    # Run with Jetty or Tomcat
    CMD ["java", "-jar", "/app/war/appengine-web.jar"]
    ```

17. **Build image Docker**:
    ```bash
    cd /opt/appinventor-sources_recherche/appinventor
    docker build -t appinventor-server:phase4-latest .

    # Importer dans containerd (RKE2)
    docker save appinventor-server:phase4-latest | \
      sudo ctr -n k8s.io images import -
    ```

## Phase 5: Déploiement App Inventor (20 min)

18. **Créer ConfigMap avec configuration**:
    ```bash
    kubectl create configmap appinventor-config \
      --from-file=build.properties \
      -n appinventor
    ```

19. **Déployer App Inventor**:
    Créer `appinventor-deployment.yaml`:
    ```yaml
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: appinventor-server
      namespace: appinventor
    spec:
      replicas: 2
      selector:
        matchLabels:
          app: appinventor-server
      template:
        metadata:
          labels:
            app: appinventor-server
        spec:
          containers:
          - name: appinventor
            image: appinventor-server:phase4-latest
            imagePullPolicy: Never
            ports:
            - containerPort: 8080
            env:
            - name: DB_HOST
              value: postgresql-0.postgresql-headless.appinventor.svc.cluster.local
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: postgresql-credentials
                  key: app-password
            volumeMounts:
            - name: config
              mountPath: /app/config
          volumes:
          - name: config
            configMap:
              name: appinventor-config
    ---
    apiVersion: v1
    kind: Service
    metadata:
      name: appinventor-service
      namespace: appinventor
    spec:
      type: NodePort
      ports:
      - port: 8080
        targetPort: 8080
        nodePort: 30080
      selector:
        app: appinventor-server
    ```

20. **Appliquer déploiement**:
    ```bash
    kubectl apply -f appinventor-deployment.yaml
    kubectl wait --for=condition=ready pod \
      -l app=appinventor-server \
      -n appinventor \
      --timeout=300s
    ```

## Phase 6: Tests & Validation (15 min)

21. **Vérifier tous les pods**:
    ```bash
    kubectl get pods -n appinventor
    # Tous doivent être Running/Ready
    ```

22. **Tester connexion PostgreSQL**:
    ```bash
    kubectl exec -it postgresql-0 -n appinventor -- \
      psql -U appinventor_user -d appinventor -c '\dt'
    # Doit montrer toutes les tables
    ```

23. **Tester connexion Redis**:
    ```bash
    kubectl exec -it redis-0 -n appinventor -- \
      redis-cli -a <PASSWORD> PING
    # Doit retourner PONG
    ```

24. **Tester MinIO**:
    ```bash
    kubectl port-forward svc/minio -n appinventor 9000:9000
    # Ouvrir http://localhost:9000
    ```

25. **Tester App Inventor**:
    ```bash
    # Obtenir IP du node
    kubectl get nodes -o wide

    # Tester endpoint
    curl http://<NODE_IP>:30080/health
    curl http://<NODE_IP>:30080/
    ```

26. **Vérifier logs**:
    ```bash
    kubectl logs -l app=appinventor-server -n appinventor --tail=50
    # Chercher "Phase 4 database initialized"
    # Chercher "Server started successfully"
    ```

## Phase 7: Finalisation (10 min)

27. **Créer utilisateur admin**:
    Se connecter à l'interface web et créer premier utilisateur

28. **Tester création projet**:
    Créer un projet simple pour vérifier:
    - Connexion PostgreSQL
    - Sauvegarde fichiers
    - Cache Redis

29. **Documenter l'installation**:
    Créer fichier `/opt/appinventor-installation-info.txt` avec:
    - Toutes les URLs
    - Tous les mots de passe générés
    - NodePort mappings
    - Commandes de base

30. **Backup initial**:
    ```bash
    # Backup PostgreSQL
    kubectl exec postgresql-0 -n appinventor -- \
      pg_dump -U postgres appinventor > \
      /opt/backup-initial.sql
    ```

## RÉSUMÉ FINAL

À la fin, tu dois me fournir:

1. ✅ **Status Report** complet:
   - Version Kubernetes installée
   - PostgreSQL version et status
   - Redis version et status
   - MinIO version et status
   - App Inventor build version
   - Tous les pods Running

2. ✅ **Fichier credentials.txt** avec:
   - PostgreSQL superuser password
   - PostgreSQL app password
   - Redis password
   - MinIO access/secret keys
   - URLs d'accès (NodePort)

3. ✅ **Commandes de test** que je peux copier/coller:
   ```bash
   # Tester App Inventor
   curl http://<IP>:30080/

   # Voir logs
   kubectl logs -l app=appinventor-server -n appinventor -f

   # Restart si besoin
   kubectl rollout restart deployment/appinventor-server -n appinventor
   ```

4. ✅ **Next steps** pour moi:
   - Comment accéder à l'interface web
   - Comment créer mon premier utilisateur
   - Comment créer mon premier projet
   - Comment vérifier que Phase 4 fonctionne

## EN CAS D'ERREUR

Si une étape échoue:
1. Diagnostique l'erreur
2. Affiche les logs pertinents
3. Propose 2-3 solutions
4. Demande-moi laquelle choisir
5. Applique la solution
6. Vérifie que c'est corrigé
7. Continue

## CONFIRMATION AVANT DE COMMENCER

Avant de démarrer, tu dois:
1. Vérifier que tu as bien compris toutes les étapes
2. Me demander confirmation pour démarrer
3. M'avertir du temps total estimé (environ 2h30)
4. Me dire que je peux te laisser travailler pendant ce temps

COMMENCE MAINTENANT!
```

---

## 📝 Instructions d'Utilisation

### Sur votre serveur DSI Paris:

1. **Connectez-vous en SSH** avec VS Code:
   ```bash
   ssh user@votre-serveur-dsi.fr
   ```

2. **Ouvrez Claude Code** dans VS Code

3. **Copiez-collez le prompt ci-dessus** dans Claude Code

4. **Laissez Claude travailler** pendant environ 2h30

5. **Claude va**:
   - ✅ Installer toutes les dépendances
   - ✅ Configurer Kubernetes
   - ✅ Déployer PostgreSQL, Redis, MinIO
   - ✅ **COMPILER App Inventor avec Phase 4**
   - ✅ **Créer l'image Docker**
   - ✅ Déployer sur Kubernetes
   - ✅ Tester toutes les connexions
   - ✅ Vous fournir un rapport complet + credentials

6. **À la fin**, vous recevrez:
   - ✅ Fichier avec tous les mots de passe
   - ✅ URL d'accès à App Inventor
   - ✅ Commandes de test
   - ✅ Instructions pour premier usage

### Après l'installation:

Vous pourrez tester immédiatement:
```bash
# Accéder à l'interface web
http://votre-serveur:30080

# Créer votre premier utilisateur
# Créer votre premier projet
# Vérifier que tout fonctionne avec Phase 4 PostgreSQL
```

---

## 🎯 Avantages de ce Prompt

1. **100% automatisé** - Vous n'avez rien à faire
2. **Intelligent** - Claude diagnostique et corrige les erreurs
3. **Sécurisé** - Génération automatique de mots de passe forts
4. **Complet** - Installation + Build + Déploiement + Tests
5. **Documenté** - Vous recevez toute la documentation à la fin
6. **Production-ready** - Configuration optimale pour DSI Paris

---

## ⏱️ Temps Estimé

- Phase 1 (Préparation): 15 min
- Phase 2 (Kubernetes): 20 min
- Phase 3 (Databases): 30 min
- **Phase 4 (BUILD App Inventor): 45 min** ⚠️
- Phase 5 (Déploiement): 20 min
- Phase 6 (Tests): 15 min
- Phase 7 (Finalisation): 10 min

**TOTAL: ~2h30** (non-supervisé)

---

## 🔒 Sécurité

Claude va générer automatiquement:
- ✅ Mots de passe PostgreSQL (24 caractères random)
- ✅ Mot de passe Redis (24 caractères random)
- ✅ Access keys MinIO (32 caractères random)
- ✅ Certificates SSL (auto-signés)

Tous sauvegardés dans `/opt/appinventor-credentials.txt`

---

## 📞 Support

Si problème pendant l'installation:
- Claude diagnostiquera automatiquement
- Proposera des solutions
- Corrigera et continuera
- Vous aurez les logs complets

**Vous n'avez qu'à surveiller et valider!**
