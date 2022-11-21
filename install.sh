#!/bin/bash

# another way to check if file exist
#if ! compgen -G "target/microstart*with-dependencies.jar" > /dev/null; then
#	echo "Jar doesn't exist. Building it now..."
#	mvn clean package
#fi

echo "Installing microstart..."

jar=(target/microstart*with-dependencies.jar)
jar="${jar[0]}"

if [[ ! -f "$jar" ]]; then
	echo -e "\tJar doesn't exist. Building it now..."
	mvn clean package
fi

# TODO: let the user decide installation directory
# TODO: let the user decide if copying whole jar or simply creating a symlink
INSTALLATION_DIR="$HOME/bin"
echo "Copying jar to $INSTALLATION_DIR..."

if [[ ! -d "$INSTALLATION_DIR" ]]; then
	echo -e "\t$INSTALLATION_DIR doesnt' exist. Creating directory..."
	mkdir "$INSTALLATION_DIR"
fi

cp "$jar" "$INSTALLATION_DIR/microstart.jar"	# Copy jar file

echo "Copying microstart bash script..."
cp microstart.sh "$INSTALLATION_DIR/microstart"	# Copy bash script to avoid typing java -jar ...
chmod u+x "$INSTALLATION_DIR/microstart"	# Grant execute privileges on bash script

# replace INSTALLATION_DIR variable from the microstart bash script with the actual value from this script
# | as separator is needed because $INSTALLATION_DIR could potentially contain forward slashes (/)
sed -i "s|INSTALLATION_DIR=\".*\"|INSTALLATION_DIR=\"$INSTALLATION_DIR\"|" "$INSTALLATION_DIR/microstart"

echo "Done."
