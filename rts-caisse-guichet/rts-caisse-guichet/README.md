# RTS Caisse — Client Guichet JavaFX

Client lourd de saisie des opérations de caisse pour les **guichets physiques**
de la Radiodiffusion Télévision Sénégalaise. Installé sur chaque poste de
caissier, il dialogue avec l'API REST Spring Boot et imprime les reçus
sur l'imprimante locale.

## Stack technique

| Composant       | Version |
|-----------------|---------|
| Java            | 17+     |
| JavaFX          | 21.0.4  |
| Jackson         | 2.17.2  |
| Maven           | 3.8+    |

Architecture MVC : FXML pour les vues, contrôleurs JavaFX, couche API
isolée, session en mémoire, impression native.

## Prérequis

1. **JDK 17+** (`java -version` doit retourner 17 ou plus). JDK 21 également
   pris en charge, comme l'indique ton environnement actuel.
2. **Maven 3.8+**
3. Le **backend RTS Caisse** doit tourner (par défaut sur `http://localhost:9090`).
4. Une **imprimante** physique ou virtuelle (PDF, p. ex. Microsoft Print to PDF
   sur Windows) pour tester l'impression des reçus.

## Démarrage rapide

```bash
cd rts-caisse-guichet
mvn clean javafx:run
```

Configuration de l'URL backend si celui-ci ne tourne pas sur `localhost:9090` :

```bash
# Linux / macOS
mvn clean javafx:run -Drts.api.url=http://10.0.0.12:9090/api

# Windows PowerShell
mvn clean javafx:run "-Drts.api.url=http://10.0.0.12:9090/api"
```

On peut aussi passer par une variable d'environnement :

```bash
export RTS_API_URL=http://rts-caisse-serveur.local:9090/api
mvn javafx:run
```

## Règles d'accès

Le guichet est **réservé au rôle CAISSIER**. Les ADMIN et SUPERVISEUR se
voient refuser l'accès avec un message explicite leur rappelant d'utiliser
l'application web Angular. C'est cohérent avec la répartition métier :
- le **guichet** sert à saisir et imprimer des opérations à haute cadence,
- le **web** sert à administrer et superviser.

## Workflow du caissier

### 1. Connexion
Écran de login : saisie matricule/login + mot de passe. Le JWT retourné par
le backend est stocké en mémoire dans la classe `Session`.

### 2. Sélection de la caisse
La liste affiche toutes les caisses. Les caisses affectées au caissier
connecté sont marquées « ★ affectée à vous » et remontent en haut. Un badge
coloré indique le statut (OUVERTE / FERMÉE / SUSPENDUE).

### 3. Ouverture de la caisse
Si la caisse est fermée, cliquer sur **« Ouvrir la caisse »** ouvre une boîte
de dialogue demandant le **fond d'ouverture** (billets/pièces présents dans la
caisse en début de journée). Validation → création du journal côté serveur,
la caisse passe en OUVERTE.

### 4. Saisie d'opération
Une fois la caisse ouverte, le formulaire est actif :

- **Type** (boutons Encaissement vert / Décaissement rouge) — filtre dynamiquement la liste des catégories.
- **Catégorie** (Recette publicitaire, Cession de droits, Frais de mission…)
- **Mode de paiement** (Espèces, Chèque, Virement, Carte, Wave, Orange Money, Free Money)
- **Montant** en FCFA (parser tolérant : `150 000` ou `150000` ou `150000,00`)
- **Référence** (n° chèque, TX Wave/OM, bordereau) — optionnelle
- **Client** — optionnel, autocomplete
- **Motif** — obligatoire

Clic sur **« Enregistrer & Imprimer »** :
1. L'opération est envoyée à l'API, qui retourne un **numéro de reçu unique** (`RTS-2026-CAI-01-00042`)
2. Le solde courant de la caisse est mis à jour transactionnellement côté serveur
3. Le tableau du jour se rafraîchit
4. Une boîte propose l'**impression du reçu** : cliquer OK ouvre le dialogue natif de sélection d'imprimante

Le reçu imprimé contient : en-tête RTS, numéro de reçu, date/heure, caisse, agent, catégorie, mode de paiement, client éventuel, référence éventuelle, motif, **MONTANT en gras**, pied de page.

### 5. Consultation et annulation
Le panneau de droite liste les opérations du jour. Deux boutons par ligne :
- **🖨** — réimprimer le reçu à tout moment
- **✖** — annuler l'opération (demande un motif, contre-passation automatique du solde côté serveur, opération marquée barrée dans le tableau mais conservée pour la traçabilité)

### 6. Clôture de la caisse
En fin de journée : **« Clôturer la caisse »** ouvre deux dialogues successifs :

1. **Solde réel** compté dans la caisse (billets + pièces)
2. **Commentaire** facultatif (pour justifier un écart par exemple)

Le backend calcule l'écart = `soldeReel - soldeTheorique` et le renvoie.
Une boîte d'information finale résume toute la journée :

```
Fond d'ouverture : 50 000 FCFA
Total entrées    : 425 000 FCFA
Total sorties    :  80 000 FCFA
Solde théorique  : 395 000 FCFA
Solde réel       : 395 000 FCFA
─────────────────────────
ÉCART            : 0 FCFA

✓ Aucun écart — clôture parfaite.

La clôture reste à valider par un superviseur.
```

La caisse repasse en FERMÉE et le caissier peut se déconnecter.
La **validation finale** est faite par un ADMIN/SUPERVISEUR depuis l'application web.

## Architecture du projet

```
sn.rts.caisse.guichet
├── app/           GuichetApplication (point d'entrée + navigation goTo)
├── api/           ApiClient (HttpClient + Jackson + JWT), CaisseApi (façade), ApiException
├── controller/    LoginController, SelectionCaisseController, CaissierController
├── model/         Enums (Role, TypeOperation, ModePaiement, StatutCaisse) + Dto
├── print/         PrintRecu (JavaFX PrinterJob → ticket reçu)
└── util/          Session, Config, Ui (formatters, dialogs), AsyncRunner

src/main/resources
├── fxml/          login.fxml, selection-caisse.fxml, caissier.fxml
└── css/           style.css (thème RTS)
```

## Points techniques

### Appels API non bloquants
Chaque appel HTTP passe par `AsyncRunner.run(supplier, onSuccess, onError)` :
exécution sur un pool de 4 threads daemon, callbacks dans `Platform.runLater`.
L'UI ne se gèle jamais, même sur un réseau lent.

### Injection automatique du JWT
`ApiClient` lit le token depuis `Session.getInstance()` avant chaque requête
et l'ajoute en `Authorization: Bearer ...`. Rien à faire dans les contrôleurs.

### Gestion des erreurs
Les erreurs backend (formatées par `GlobalExceptionHandler` Spring) sont
désérialisées, le champ `message` est extrait et affiché dans une boîte
d'erreur. Les erreurs 0 (serveur injoignable), 401 (token invalide),
403 (accès refusé) ont des messages spécifiques.

### Impression
`PrintRecu.imprimer(op)` utilise `javafx.print.PrinterJob` — standard, aucune
dépendance externe. Le reçu est construit comme un `VBox` en police monospace
de 11 pt (cohérent avec les imprimantes thermique 80 mm) et la sélection
d'imprimante est faite par l'utilisateur via le dialogue natif.

## Packaging pour déploiement

Pour créer un exécutable distributable (via `jpackage` JDK 17+) :

```bash
mvn clean package
mvn javafx:jlink  # génère un runtime modulaire
# puis jpackage pour générer un .msi (Windows), .deb (Linux), .dmg (macOS)
```

## Compte de test

Après démarrage du backend avec son `DataInitializer`, un ADMIN par défaut
existe (`admin` / `Admin@2026`). Cet utilisateur étant ADMIN, il **ne peut
pas se connecter au guichet** (rejeté avec message explicatif).

Pour tester le guichet, créer un utilisateur CAISSIER depuis l'application
web (écran Utilisateurs) ou via `curl` :

```bash
curl -X POST http://localhost:9090/api/utilisateurs \
  -H "Authorization: Bearer <TOKEN_ADMIN>" \
  -H "Content-Type: application/json" \
  -d '{
    "matricule": "RTS-C-0045",
    "login": "adiop",
    "motDePasse": "Caisse@2026",
    "prenom": "Aïssatou",
    "nom": "Diop",
    "email": "a.diop@rts.sn",
    "telephone": "+221771234567",
    "role": "CAISSIER"
  }'
```

Puis se connecter au guichet avec `adiop` / `Caisse@2026`.

---

© RTS - Direction des Systèmes d'Information — 2026
