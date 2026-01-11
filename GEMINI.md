# Arsenic

## Project Overview

Arsenic is a Forge-based Minecraft cheat client for version 1.8.9. It is designed to bypass the Grim anticheat system, particularly on the Hypixel server. The client is built with a modular architecture, allowing for the easy addition and removal of features.

**Key Technologies:**

*   **Java 8:** The core programming language.
*   **Minecraft Forge:** The modding framework used to load and run the client.
*   **Gradle:** The build automation system used for compiling the project and managing dependencies.
*   **Mixins:** A bytecode manipulation framework used to modify Minecraft's source code at runtime. This allows for more advanced modifications than what Forge's event system provides.
*   **Reflections:** A library used to dynamically discover and instantiate modules at runtime.

**Architecture:**

The client is built around a central `Arsenic` class, which manages various singleton instances of key components:

*   **`ModuleManager`:** Responsible for discovering, registering, and managing all the modules (cheats) in the client.
*   **`CommandManager`:** Handles in-game commands.
*   **`EventManager`:** Manages the event bus, allowing modules to subscribe to and listen for game events.
*   **`ConfigManager`:** Handles the saving and loading of module configurations.
*   **`ThemeManager`:** Manages the visual themes for the client's GUI.

Modules are organized into categories such as `blatant`, `ghost`, `movement`, `visual`, etc. Each module is a self-contained class that extends the `Module` class and uses an `@ModuleInfo` annotation to define its metadata.

## Building and Running

To set up the development environment and build the client, follow these steps:

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/your-username/Arsenic.git
    ```
2.  **Run the setup script:**
    ```bash
    ./setup.bat
    ```
    This will download the necessary dependencies, set up the Minecraft Forge environment, and generate the necessary project files for IntelliJ IDEA.
3.  **Open the project in IntelliJ IDEA:**
    Open the `Arsenic.ipr` file in IntelliJ IDEA.
4.  **Build the project:**
    The `setup.bat` script already builds the project. To rebuild the project, you can run the following Gradle command:
    ```bash
    ./gradlew.bat build
    ```
5.  **Run the client:**
    The `setup.bat` script also generates run configurations for IntelliJ IDEA. You can run the client by selecting the "Minecraft Client" run configuration and clicking the "Run" button.

## Development Conventions

*   **Modules:** All new modules should extend the `Module` class and be placed in the `arsenic.module.impl` package, under the appropriate category sub-package.
*   **Events:** The client uses a custom event bus. To listen for events, use the `@EventLink` annotation on a `Listener` field.
*   **Properties:** Module settings should be exposed as `Property` objects. This allows them to be automatically saved and loaded by the `ConfigManager` and displayed in the GUI.
*   **Commands:** In-game commands should extend the `Command` class and be registered with the `CommandManager`.
*   **Mixins:** Mixins are used for more advanced modifications. All mixins should be placed in the `arsenic.injection.mixin` package and registered in the `mixins.arsenic.json` file.

## Features

### Custom Main Menu

The client features a custom main menu with several enhancements over the default Minecraft menu:

*   **Animated Background:** The background is rendered with a cool animated shader. You can cycle through different shaders by clicking the "Next Shader" button in the top-left corner.
*   **Liquid Buttons:** The menu buttons have a liquid glass effect that interacts with the mouse cursor, creating a metaball-like effect.
*   **Player Model:** Your player model is displayed in the bottom-right corner and will look towards the mouse cursor.
*   **Information Display:**
    *   The top-right corner shows the number of loaded modules and settings.
    *   The bottom-left corner shows the number of loaded mods.
*   **Custom Title:** A large "Arsenic" title is displayed above the buttons.
*   **Standard Buttons:** The menu includes buttons for Singleplayer, Multiplayer, Mods, Options, and Quit Game.
