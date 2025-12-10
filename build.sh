#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Directories for minecraft instances (symlinks to instance/<name>/minecraft/)
# This gives access to: mods/, config/, logs/, local/, etc.
FABRIC_DIR="./minecraft-fabric"
NEOFORGE_DIR="./minecraft-neoforge"

# Read from gradle.properties
JAR_NAME=$(grep "archivesBaseName=" gradle.properties | cut -d'=' -f2)
VERSION=$(grep "modVersion=" gradle.properties | cut -d'=' -f2)

if [ -z "$JAR_NAME" ]; then
    echo -e "${RED}Error: Could not find archivesBaseName in gradle.properties${NC}"
    exit 1
fi

if [ -z "$VERSION" ]; then
    echo -e "${RED}Error: Could not find modVersion in gradle.properties${NC}"
    exit 1
fi

echo -e "${GREEN}=== Building $JAR_NAME v$VERSION ===${NC}"
echo ""

# Clean and build
echo -e "${YELLOW}Running Gradle build...${NC}"
./gradlew clean build

# Find the built jars
FABRIC_JAR=$(find . -path "*/fabric/*/build/libs/*-fabric.jar" -not -name "*-dev*" -not -name "*-sources*" | head -1)
NEOFORGE_JAR=$(find . -path "*/neoforge/*/build/libs/*-neoforge.jar" -not -name "*-dev*" -not -name "*-sources*" | head -1)

if [ -z "$FABRIC_JAR" ]; then
    echo -e "${RED}Error: Fabric jar not found${NC}"
    exit 1
fi

if [ -z "$NEOFORGE_JAR" ]; then
    echo -e "${RED}Error: NeoForge jar not found${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Build successful!${NC}"
echo "  Fabric:   $FABRIC_JAR"
echo "  NeoForge: $NEOFORGE_JAR"

# Copy to deployment directories if they exist
echo ""

if [ -d "$FABRIC_DIR/mods" ] || [ -L "$FABRIC_DIR" ]; then
    FABRIC_DEST="$FABRIC_DIR/mods/${JAR_NAME}-fabric-v${VERSION}.jar"
    mkdir -p "$FABRIC_DIR/mods"
    cp "$FABRIC_JAR" "$FABRIC_DEST"
    echo -e "${GREEN}Deployed Fabric jar to:${NC} $FABRIC_DEST"
else
    echo -e "${YELLOW}Skipping Fabric deployment (${FABRIC_DIR} not found)${NC}"
    echo "  Create symlink: ln -s /path/to/instance/minecraft ./minecraft-fabric"
fi

if [ -d "$NEOFORGE_DIR/mods" ] || [ -L "$NEOFORGE_DIR" ]; then
    NEOFORGE_DEST="$NEOFORGE_DIR/mods/${JAR_NAME}-neoforge-v${VERSION}.jar"
    mkdir -p "$NEOFORGE_DIR/mods"
    cp "$NEOFORGE_JAR" "$NEOFORGE_DEST"
    echo -e "${GREEN}Deployed NeoForge jar to:${NC} $NEOFORGE_DEST"
else
    echo -e "${YELLOW}Skipping NeoForge deployment (${NEOFORGE_DIR} not found)${NC}"
    echo "  Create symlink: ln -s /path/to/instance/minecraft ./minecraft-neoforge"
fi

echo ""
echo -e "${GREEN}Done!${NC}"
echo ""
echo "Useful paths (if symlinks exist):"
echo "  Logs:    ./minecraft-fabric/logs/ or ./minecraft-neoforge/logs/"
echo "  Config:  ./minecraft-fabric/config/ or ./minecraft-neoforge/config/"
echo "  Local:   ./minecraft-fabric/local/ or ./minecraft-neoforge/local/"
