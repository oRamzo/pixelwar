#!/bin/bash
echo "==============================="
echo "   Lancement de Pixel War"
echo "==============================="
echo

# Chemins
JAVAFX="$HOME/javafx-sdk-21/lib"
SOURCES="$HOME/pixelwar/src/main/ressources"

cd "$SOURCES"

# Compilation
echo "Compilation en cours..."
javac --module-path "$JAVAFX" --add-modules javafx.controls,javafx.fxml *.java

if [ $? -ne 0 ]; then
    echo ""
    echo "ERREUR : La compilation a echoue."
    read -p "Appuyez sur Entree pour quitter..."
    exit 1
fi

echo "Compilation reussie."
echo

# Lancement
echo "Lancement de l'application..."
java --module-path "$JAVAFX" --add-modules javafx.controls,javafx.fxml App

echo ""
echo "Application terminee."
read -p "Appuyez sur Entree pour quitter..."