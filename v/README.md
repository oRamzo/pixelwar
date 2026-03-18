# Pixel War — Mini Projet RIP-IHM 2025-2026

Application de coloriage de pixels en temps réel, multijoueur, en Java + JavaFX.

---

## Structure du projet

```
pixel-war/
├── pom.xml
└── src/main/
    ├── java/com/pixelwar/
    │   ├── Main.java                        ← Point d'entrée client JavaFX
    │   ├── model/
    │   │   └── Message.java                 ← Objet sérialisable client↔serveur
    │   ├── server/
    │   │   ├── Serveur.java                 ← Serveur TCP (main)
    │   │   └── ClientHandler.java           ← Gestion d'un client côté serveur
    │   └── client/
    │       ├── NetworkClient.java           ← Connexion TCP côté client
    │       ├── ConnexionController.java     ← Contrôleur écran de login
    │       └── GrilleController.java        ← Contrôleur écran de jeu
    └── resources/com/pixelwar/
        ├── connexion.fxml                   ← Vue login
        └── grille.fxml                      ← Vue jeu
```

---

## Prérequis

- Java 17+
- Maven 3.8+
- JavaFX 21 (géré automatiquement par Maven)

---

## Lancement

### 1. Compiler le projet

```bash
mvn compile
```

### 2. Lancer le SERVEUR

```bash
mvn exec:java -Dexec.args="12345"
```

Ou directement avec Java (après `mvn package`) :
```bash
java -cp target/pixel-war-1.0-SNAPSHOT.jar com.pixelwar.server.Serveur 12345
```

### 3. Lancer le CLIENT (autant de fois que de joueurs)

```bash
mvn javafx:run
```

Puis dans l'interface :
- Entrer un **pseudo** unique
- Adresse : `localhost` (ou l'IP du serveur)
- Port : `12345`
- Cliquer **Se connecter**

---

## Règles du jeu

| Règle | Durée |
|-------|-------|
| Embargo par pixel | 60 secondes |
| Cooldown utilisateur après un coloriage | 30 secondes |

- Un pixel colorié est **entouré en rouge** pendant son embargo.
- Pendant le cooldown, **toute la grille est désactivée**.
- Si un autre joueur essaie de cliquer un pixel sous embargo, le serveur lui envoie une notification.

---

## Protocole réseau

Communication TCP avec objets Java sérialisés (`ObjectOutputStream` / `ObjectInputStream`).

| Type de message | Direction | Description |
|-----------------|-----------|-------------|
| `CONNECT` | C → S | Demande de connexion avec pseudo |
| `CONNECT_OK` | S → C | Connexion acceptée |
| `CONNECT_ERROR` | S → C | Refus (pseudo pris, etc.) |
| `PIXEL_COLOR` | C → S | Demande de coloriage d'un pixel |
| `PIXEL_UPDATE` | S → tous | Un pixel a été colorié (broadcast) |
| `PIXEL_EMBARGO` | S → C | Pixel refusé car sous embargo |
| `DISCONNECT` | C → S | Déconnexion propre |
| `SERVER_SHUTDOWN` | S → tous | Serveur arrêté |

---

## Gestion des erreurs

- **Client qui plante** : le serveur détecte l'EOF et supprime le client de la liste.
- **Serveur qui plante** : le client détecte la perte de connexion via `SocketException`/`EOFException`, affiche un message d'erreur et revient à l'écran de connexion.
- Le serveur continue de tourner tant qu'il y a au moins un client actif.
