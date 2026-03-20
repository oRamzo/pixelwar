#!/bin/bash
#Mini- Projet
#Systemes 2
#Rami MAHMOUD
#Rebecca FARAH
#L3 IFA 1
#2025/2026

#chemin vers javafx
JAVAFX="$HOME/javafx-sdk-21/lib"

#chemin du dossier des sources
SOURCES="$HOME/pixelwar/src/main/ressources"

#se placer dans le dossier des sources
cd "$SOURCES"

#compiler tous les fichiers java
echo "Compilation en cours..."
javac --module-path "$JAVAFX" --add-modules javafx.controls,javafx.fxml *.java

#lancer l'application
echo "Lancement de l'application..."
java --module-path "$JAVAFX" --add-modules javafx.controls,javafx.fxml App

echo ""
echo "Application terminee."

#pour executer ce script :
#chmod +x pixelwar.sh
#./pixelwar.sh