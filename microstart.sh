#!/bin/bash

# Check install.sh before modifying this file
# this is just a bash script to avoid typing java -jar ...

INSTALLATION_DIR="REPLACE ME PLEASE!!"

java -jar "$INSTALLATION_DIR/microstart.jar" "$@"
