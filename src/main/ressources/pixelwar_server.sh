#!/bin/bash
#Mini- Projet
#Systemes 2
#Rami MAHMOUD
#Rebecca FARAH
#L3 IFA 1
#2025/2026

#chemin du dossier des sources
SOURCES="$HOME/pixelwar/src/main/ressources"

#se placer dans le dossier des sources
cd "$SOURCES"

#port par defaut 7777 si pas de parametre
PORT=${1:-7777}

#compiler le serveur
echo "Compilation en cours..."
javac Server.java

#lancer le serveur
echo "Lancement du serveur sur le port $PORT..."
java Server "$PORT"

echo ""
echo "Serveur arrete."

#pour executer ce script :
#chmod +x pixelwar_server.sh
#./pixelwar_server.sh