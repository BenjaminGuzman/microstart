#!/bin/bash

echo "Installing microstart..."

function select_jar() {
    available_jars=(target/microstart*with-dependencies.jar)
    jar="${available_jars[0]}"
    if [[ -f "$jar" ]]; then
      echo "$jar"
    else
      echo "nil"
    fi
}

jar=$(select_jar)
if [[ "$jar" == "nil" ]]; then
  echo -e "\tJar doesn't exist. Building it now..."
  mvn clean package
  jar=$(select_jar)
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
sed -i '' "s|INSTALLATION_DIR=\".*\"|INSTALLATION_DIR=\"$INSTALLATION_DIR\"|" "$INSTALLATION_DIR/microstart"

echo "Done."
