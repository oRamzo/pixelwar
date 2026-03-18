#!/bin/bash
echo "==============================="
echo "   Lancement du Serveur Pixel War"
echo "==============================="
echo

SOURCES="$HOME/pixelwar/src/main/ressources"

cd "$SOURCES"

# Port par defaut 7777 si pas de parametre
PORT=${1:-7777}

# Compilation
echo "Compilation en cours..."
javac Server.java

if [ $? -ne 0 ]; then
    echo ""
    echo "ERREUR : La compilation a echoue."
    read -p "Appuyez sur Entree pour quitter..."
    exit 1
fi

echo "Compilation reussie."
echo

# Lancement
echo "Lancement du serveur sur le port $PORT..."
java Server "$PORT"

echo ""
echo "Serveur arrete."
read -p "Appuyez sur Entree pour quitter..."    