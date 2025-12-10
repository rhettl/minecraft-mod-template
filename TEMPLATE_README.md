# Minecraft Mod Template

A multi-loader Minecraft mod template with:
- **Kotlin** for mod code
- **Kotlin DSL** for build scripts
- **Architectury** for multi-loader support (Fabric + NeoForge)
- **Stonecutter** for multi-version support

## Quick Start

### Using this template

1. Click "Use this template" on GitHub (or clone/copy this repo)

2. Run the setup script:
   ```bash
   chmod +x setup.sh
   ./setup.sh
   ```

3. Follow the prompts to configure your mod:
   - **Mod ID**: lowercase, no spaces (e.g., `myawesomemod`)
   - **Mod Name**: display name (e.g., `My Awesome Mod`)
   - **Mod Class**: PascalCase (e.g., `MyAwesomeMod`)
   - **Package**: Java package (e.g., `com.yourname.mymod`)
   - **Jar Name**: output file base name (e.g., `my-awesome-mod`)
   - **Author**: your name
   - **Description**: short description

4. Build your mod:
   ```bash
   ./gradlew build
   ```

5. After setup:
   - Delete `setup.sh` and `TEMPLATE_README.md`
   - Update `CLAUDE.md` with your mod-specific details (keep the coding guidelines)
   - Create a `README.md` for your mod

## Project Structure

```
├── common/                 # Shared code (both loaders)
├── fabric/                 # Fabric-specific code
├── neoforge/              # NeoForge-specific code
├── CLAUDE.md              # Instructions for Claude Code AI assistant
├── build.gradle.kts       # Root build config
├── settings.gradle.kts    # Stonecutter + project setup
├── stonecutter.gradle.kts # Version management
├── build.sh               # Build & deploy script
├── tag-release.sh         # Version tagging script
└── setup.sh               # Template setup script (delete after use)
```

## Claude Code Integration

This template includes `CLAUDE.md` with:
- **Coding guidelines**: Pure functions, file organization, test discipline, layered architecture
- **Tech stack reference**: Kotlin, Architectury, Stonecutter
- **Common tasks**: How to add dependencies, create releases, etc.
- **Gotchas**: Platform-specific issues and solutions

Claude Code automatically reads `CLAUDE.md` for project context. Keep this file updated with your mod's specifics.

## Building

### Standard build
```bash
./gradlew build
```

### Build and deploy to Minecraft
```bash
# First, create symlinks to your Minecraft instance directories
# This gives you access to mods/, config/, logs/, local/, etc.
ln -s "/path/to/instances/my-fabric-instance/minecraft" ./minecraft-fabric
ln -s "/path/to/instances/my-neoforge-instance/minecraft" ./minecraft-neoforge

# Example for PrismLauncher:
# ln -s "$HOME/Library/Application Support/PrismLauncher/instances/MyInstance/minecraft" ./minecraft-fabric

# Then build and deploy
./build.sh
```

Output: `./minecraft-{loader}/mods/{jarname}-{loader}-v{version}.jar`

This also gives you easy access to:
- `./minecraft-fabric/logs/` - Game logs
- `./minecraft-fabric/config/` - Mod configs
- `./minecraft-fabric/local/` - Local data

## Multi-Version Support

This template uses Stonecutter for multi-version support.

### Adding a new Minecraft version

1. Edit `settings.gradle.kts`:
   ```kotlin
   versions("1.21.1", "1.21")  // Add new version
   ```

2. Create version-specific properties if needed in `versions/{version}/gradle.properties`

3. Use version comments in code:
   ```kotlin
   //? if >=1.21.1
   newApiCall()
   //?else
   /*oldApiCall()*/
   //?endif
   ```

## Tech Stack

| Component | Version |
|-----------|---------|
| Kotlin | 2.0.21 |
| Architectury Loom | 1.10-SNAPSHOT |
| Architectury Plugin | 3.4-SNAPSHOT |
| Stonecutter | 0.7.11 |
| Fabric Language Kotlin | 1.12.3 |

## License

This template is provided as-is. Use it for your mods however you like.
