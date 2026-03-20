# Skript Editor - Fabric Mod for Minecraft 1.21.11

An in-game Skript script editor mod for Minecraft, built with Fabric. Provides a full-featured code editor GUI with syntax highlighting, file management, and server communication for editing `.sk` files on your server.

## Architecture

This mod is the **client-side** component of a two-part system:

```
┌─────────────────────┐         Custom Packets          ┌─────────────────────────┐
│   Fabric Client Mod │ ◄──────────────────────────────► │  Server Bridge Plugin   │
│   (This project)    │   skripteditor:file_list_req     │  (You build this)       │
│                     │   skripteditor:file_open_req     │                         │
│  - GUI / Editor     │   skripteditor:file_save_req     │  - File I/O             │
│  - Syntax Highlight │   skripteditor:file_create_req   │  - Path validation      │
│  - Tab management   │   skripteditor:file_rename_req   │  - Permission checks    │
│  - Toast messages   │   skripteditor:file_delete_req   │  - Skript reload API    │
│  - Search/Find      │   skripteditor:reload_req        │  - Audit logging        │
│                     │   + corresponding *_res packets  │                         │
└─────────────────────┘                                  └─────────────────────────┘
```

## Features

- **File Tree Sidebar** - Browse scripts directory with expand/collapse folders
- **Tabbed Editor** - Open multiple files simultaneously
- **Syntax Highlighting** - Skript-aware coloring for commands, events, variables, functions, strings, comments, keywords, and more
- **Line Numbers** - With current-line highlighting
- **Search/Find** - Ctrl+F with match counting, next/previous, case sensitivity toggle
- **Auto-indentation** - Preserves indentation and adds a level after colons (Skript blocks)
- **Undo/Redo** - Full undo history per tab (Ctrl+Z / Ctrl+Shift+Z)
- **Unsaved Indicators** - Yellow dot on modified tabs, status bar warnings
- **Keyboard Shortcuts** - Ctrl+S, Ctrl+F, Ctrl+W, Ctrl+Tab, F5, and more
- **Toast Notifications** - Success/error messages for all operations
- **Reload Console** - View Skript reload output (F6 to toggle)
- **Dark Theme** - Catppuccin Mocha-inspired color scheme
- **Status Bar** - File path, cursor position, save state, server messages

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| F10 | Open/close editor |
| Ctrl+S | Save current file |
| Ctrl+Shift+S | Save all files |
| Ctrl+F | Toggle search bar |
| Ctrl+W | Close current tab |
| Ctrl+Tab | Next tab |
| Ctrl+Shift+Tab | Previous tab |
| Ctrl+N | Create new file |
| F5 | Reload current script |
| Shift+F5 | Reload all scripts |
| F6 | Toggle reload console |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z | Redo |
| Ctrl+D | Duplicate line |
| Ctrl+A | Select all |
| Ctrl+C/X/V | Copy/Cut/Paste |
| Tab / Shift+Tab | Indent / Unindent |

## Building

### Prerequisites

- Java 21 or later (JDK)
- Internet connection (for Gradle to download dependencies)

### Setup

1. If you don't have the Gradle wrapper, generate it:
   ```bash
   gradle wrapper --gradle-version 8.10
   ```

2. Build the mod:
   ```bash
   ./gradlew build
   ```

3. The built mod JAR will be in `build/libs/`.

### Development

Run the Minecraft client with the mod loaded:
```bash
./gradlew runClient
```

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the built `skript-editor-*.jar` in your `.minecraft/mods/` folder
4. Launch Minecraft and press F10 to open the editor

## Server-Side Bridge (Required)

This mod **cannot** access server files directly. You need a companion server-side plugin that handles actual file I/O. Here's what the bridge must implement:

### Plugin Message Channels

Register these channels on your Spigot/Paper plugin:

| Channel | Direction | Purpose |
|---|---|---|
| `skripteditor:file_list_req` | C→S | Client requests directory listing |
| `skripteditor:file_list_res` | S→C | Server responds with file entries |
| `skripteditor:file_open_req` | C→S | Client requests file contents |
| `skripteditor:file_open_res` | S→C | Server responds with file content |
| `skripteditor:file_save_req` | C→S | Client sends file content to save |
| `skripteditor:file_save_res` | S→C | Server confirms save result |
| `skripteditor:file_create_req` | C→S | Client requests new file creation |
| `skripteditor:file_create_res` | S→C | Server confirms creation |
| `skripteditor:file_rename_req` | C→S | Client requests file rename |
| `skripteditor:file_rename_res` | S→C | Server confirms rename |
| `skripteditor:file_delete_req` | C→S | Client requests file deletion |
| `skripteditor:file_delete_res` | S→C | Server confirms deletion |
| `skripteditor:reload_req` | C→S | Client requests script reload |
| `skripteditor:reload_res` | S→C | Server responds with reload result |

### Security Requirements for the Bridge

1. **Path validation** - Normalize all paths, resolve `..` and `.`, verify the resolved path is within `plugins/Skript/scripts/`
2. **Permission checks** - Verify the requesting player has appropriate permissions (e.g., `skripteditor.use`, `skripteditor.write`, `skripteditor.delete`)
3. **File size limits** - Reject requests for files larger than a reasonable limit (e.g., 256KB)
4. **Rate limiting** - Prevent request spam from clients
5. **Audit logging** - Log all file operations with player UUID and timestamp
6. **Allowed extensions** - Only allow `.sk`, `.txt`, `.yml` file operations

### Packet Format

See `SkriptPackets.java` for the exact binary format of each packet. Each payload uses Minecraft's `RegistryByteBuf` encoding:
- Strings are length-prefixed (VarInt length + UTF-8 bytes)
- Booleans are single bytes
- Lists use VarInt count prefix

### Minimal Bridge Example (Pseudocode)

```java
// In your Spigot/Paper plugin:
Messenger messenger = Bukkit.getMessenger();
messenger.registerIncomingPluginChannel(plugin, "skripteditor:file_list_req", (channel, player, data) -> {
    if (!player.hasPermission("skripteditor.use")) return;

    String path = readString(data);
    path = normalizePath(path); // SECURITY: prevent traversal

    File dir = new File(scriptsDir, path);
    if (!dir.getCanonicalPath().startsWith(scriptsDir.getCanonicalPath())) return; // Traversal check

    File[] files = dir.listFiles();
    byte[] response = encodeFileList(path, files);
    player.sendPluginMessage(plugin, "skripteditor:file_list_res", response);
});
```

## Project Structure

```
src/main/java/com/skripteditor/
├── SkriptEditorClient.java          # Fabric client entrypoint
├── config/
│   └── EditorConfig.java            # Client-side configuration (.properties)
├── network/
│   ├── SkriptPackets.java           # All packet type definitions + registration
│   ├── PacketHandler.java           # Client-side S2C response handlers
│   └── ConnectionState.java         # Connection & handshake state tracking
├── editor/
│   ├── EditorTab.java               # Per-tab state (content, cursor, undo)
│   ├── DraftManager.java            # Auto-save draft recovery
│   ├── SkriptSyntaxHighlighter.java # Skript syntax coloring engine
│   └── UndoManager.java             # Undo/redo stack
└── gui/
    ├── SkriptEditorScreen.java      # Main screen orchestrator
    ├── ConfirmDialog.java           # Modal yes/no dialog
    ├── DependencyGraphScreen.java   # Script dependency graph view
    └── widget/
        ├── CodeEditorWidget.java    # Text editor widget
        ├── FileTreeWidget.java      # Sidebar file browser
        ├── TabBarWidget.java        # Tabbed file switcher
        ├── StatusBarWidget.java     # Bottom info bar
        ├── SearchBarWidget.java     # Find/search bar
        ├── ToastManager.java        # Notification toasts
        └── DependencyGraphWidget.java # Force-directed graph layout
```

## Version Note

This mod targets Minecraft 1.21.11 with Fabric. If you're targeting a different version, update the version numbers in `gradle.properties`.

## License

MIT
