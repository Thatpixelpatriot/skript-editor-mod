package com.skripteditor;

import com.skripteditor.config.EditorConfig;
import com.skripteditor.gui.SkriptEditorScreen;
import com.skripteditor.network.ConnectionState;
import com.skripteditor.network.PacketHandler;
import com.skripteditor.network.SkriptPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side entrypoint for the Skript Editor mod.
 *
 * Registers:
 * - Custom network packets for server communication
 * - Client-side packet response handlers
 * - Keybinding (F10) to open the editor GUI
 *
 * IMPORTANT: This mod is the CLIENT component only.
 * It requires a server-side bridge plugin (Spigot/Paper) that:
 *   1. Registers the same plugin message channels (see SkriptPackets)
 *   2. Handles file I/O requests within plugins/Skript/scripts/
 *   3. Validates permissions and sanitizes paths
 *   4. Sends responses back to the client
 *
 * Without the server bridge, the editor will open but file operations
 * will fail with "Not connected" errors.
 */
public class SkriptEditorClient implements ClientModInitializer {

    public static final String MOD_ID = "skripteditor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openEditorKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Skript Editor client initializing...");

        // Load client configuration
        EditorConfig.getInstance().load();

        // Register all custom network packet types
        SkriptPackets.registerAll();

        // Register client-side response handlers
        PacketHandler.registerReceivers();

        // Register keybinding: F10 to open editor
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skripteditor.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KeyBinding.Category.MISC
        ));

        // Reset connection state when leaving a server
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ConnectionState.getInstance().reset();
            LOGGER.info("Disconnected from server — connection state reset.");
        });

        // Listen for keybinding press each tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey.wasPressed()) {
                openEditor(client);
            }
        });

        LOGGER.info("Skript Editor client initialized. Press F10 to open.");
    }

    /** Opens the Skript Editor screen. */
    private void openEditor(MinecraftClient client) {
        if (client.currentScreen instanceof SkriptEditorScreen) {
            return; // Already open
        }
        client.setScreen(new SkriptEditorScreen());
    }
}
