#!/bin/bash
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Minecraft Mod Template Setup ===${NC}"
echo ""

# Current template values (will be replaced)
TEMPLATE_MOD_ID="cobbledollarsmarket"
TEMPLATE_MOD_NAME="CobbleDollars Market"
TEMPLATE_MOD_CLASS="CobbleDollarsMarket"
TEMPLATE_PACKAGE="com.rhett.cobbledollarsmarket"
TEMPLATE_PACKAGE_PATH="com/rhett/cobbledollarsmarket"
TEMPLATE_JAR_NAME="cobbledollars-market"
TEMPLATE_AUTHOR="Rhett"
TEMPLATE_DESCRIPTION="A market system for CobbleDollars economy."

# Prompt for new values
read -p "Mod ID (lowercase, no spaces, e.g., myawesomemod): " NEW_MOD_ID
read -p "Mod Name (display name, e.g., My Awesome Mod): " NEW_MOD_NAME
read -p "Mod Class Name (PascalCase, e.g., MyAwesomeMod): " NEW_MOD_CLASS
read -p "Package (e.g., com.yourname.modid): " NEW_PACKAGE
read -p "Jar Name (for output files, e.g., my-awesome-mod): " NEW_JAR_NAME
read -p "Author: " NEW_AUTHOR
read -p "Description: " NEW_DESCRIPTION

# Convert package to path
NEW_PACKAGE_PATH=$(echo "$NEW_PACKAGE" | tr '.' '/')

echo ""
echo -e "${YELLOW}You entered:${NC}"
echo "  Mod ID:      $NEW_MOD_ID"
echo "  Mod Name:    $NEW_MOD_NAME"
echo "  Mod Class:   $NEW_MOD_CLASS"
echo "  Package:     $NEW_PACKAGE"
echo "  Jar Name:    $NEW_JAR_NAME"
echo "  Author:      $NEW_AUTHOR"
echo "  Description: $NEW_DESCRIPTION"
echo ""
read -p "Continue? (y/n): " CONFIRM

if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
    echo "Aborted."
    exit 1
fi

echo ""
echo -e "${GREEN}Renaming directories...${NC}"

# Rename Kotlin source directories
for dir in common fabric neoforge; do
    if [ -d "$dir/src/main/kotlin/$TEMPLATE_PACKAGE_PATH" ]; then
        mkdir -p "$dir/src/main/kotlin/$NEW_PACKAGE_PATH"
        mv "$dir/src/main/kotlin/$TEMPLATE_PACKAGE_PATH"/* "$dir/src/main/kotlin/$NEW_PACKAGE_PATH/" 2>/dev/null || true
        rm -rf "$dir/src/main/kotlin/$(echo $TEMPLATE_PACKAGE | cut -d. -f1)"
    fi
done

# Rename test directory
if [ -d "common/src/test/kotlin/$TEMPLATE_PACKAGE_PATH" ]; then
    mkdir -p "common/src/test/kotlin/$NEW_PACKAGE_PATH"
    mv "common/src/test/kotlin/$TEMPLATE_PACKAGE_PATH"/* "common/src/test/kotlin/$NEW_PACKAGE_PATH/" 2>/dev/null || true
    rm -rf "common/src/test/kotlin/$(echo $TEMPLATE_PACKAGE | cut -d. -f1)"
fi

echo -e "${GREEN}Updating file contents...${NC}"

# Find and replace in all relevant files
find . -type f \( -name "*.kt" -o -name "*.kts" -o -name "*.json" -o -name "*.toml" -o -name "*.properties" -o -name "*.md" \) \
    -not -path "./.git/*" \
    -not -path "./build/*" \
    -not -path "./.gradle/*" \
    -exec sed -i '' "s|$TEMPLATE_PACKAGE_PATH|$NEW_PACKAGE_PATH|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_PACKAGE|$NEW_PACKAGE|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_MOD_CLASS|$NEW_MOD_CLASS|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_MOD_ID|$NEW_MOD_ID|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_MOD_NAME|$NEW_MOD_NAME|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_JAR_NAME|$NEW_JAR_NAME|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_AUTHOR|$NEW_AUTHOR|g" {} \; \
    -exec sed -i '' "s|$TEMPLATE_DESCRIPTION|$NEW_DESCRIPTION|g" {} \;

# Rename Kotlin files
echo -e "${GREEN}Renaming Kotlin files...${NC}"

for dir in common fabric neoforge; do
    if [ -d "$dir/src/main/kotlin/$NEW_PACKAGE_PATH" ]; then
        for file in "$dir/src/main/kotlin/$NEW_PACKAGE_PATH"/*"$TEMPLATE_MOD_CLASS"*.kt; do
            if [ -f "$file" ]; then
                newfile=$(echo "$file" | sed "s|$TEMPLATE_MOD_CLASS|$NEW_MOD_CLASS|g")
                mv "$file" "$newfile"
            fi
        done
    fi
done

# Rename test file
if [ -f "common/src/test/kotlin/$NEW_PACKAGE_PATH/${TEMPLATE_MOD_CLASS}CommonTest.kt" ]; then
    mv "common/src/test/kotlin/$NEW_PACKAGE_PATH/${TEMPLATE_MOD_CLASS}CommonTest.kt" \
       "common/src/test/kotlin/$NEW_PACKAGE_PATH/${NEW_MOD_CLASS}CommonTest.kt"
fi

# Update mixin config filenames and references
echo -e "${GREEN}Updating mixin configs...${NC}"

# Rename mixin JSON files
if [ -f "common/src/main/resources/cobbledollars_market.mixins.json" ]; then
    mv "common/src/main/resources/cobbledollars_market.mixins.json" \
       "common/src/main/resources/${NEW_MOD_ID}.mixins.json"
fi

if [ -f "neoforge/src/main/resources/cobbledollars_market-neoforge.mixins.json" ]; then
    mv "neoforge/src/main/resources/cobbledollars_market-neoforge.mixins.json" \
       "neoforge/src/main/resources/${NEW_MOD_ID}-neoforge.mixins.json"
fi

# Update references to mixin files
find . -type f \( -name "*.json" -o -name "*.kts" \) \
    -not -path "./.git/*" \
    -exec sed -i '' "s|cobbledollars_market.mixins.json|${NEW_MOD_ID}.mixins.json|g" {} \; \
    -exec sed -i '' "s|cobbledollars_market-neoforge.mixins.json|${NEW_MOD_ID}-neoforge.mixins.json|g" {} \; \
    -exec sed -i '' "s|cobbledollars-market-common-refmap.json|${NEW_JAR_NAME}-common-refmap.json|g" {} \;

# Update project name in settings.gradle.kts
sed -i '' "s|rootProject.name = \"cobbledollars-market\"|rootProject.name = \"$NEW_JAR_NAME\"|g" settings.gradle.kts

echo ""
echo -e "${GREEN}Setup complete!${NC}"
echo ""
echo "Next steps:"
echo "  1. Review the changes"
echo "  2. Run './gradlew build' to verify"
echo "  3. Delete this setup.sh file"
echo "  4. Update README.md with your mod info"
echo ""
