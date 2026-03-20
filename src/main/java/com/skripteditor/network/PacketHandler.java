package com.skripteditor.network;

import com.skripteditor.SkriptEditorClient;
import com.skripteditor.gui.DependencyGraphScreen;
import com.skripteditor.gui.SkriptEditorScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/**
 * Registers client-side handlers for all S2C (server-to-client) packets.
 * Each handler forwards the response data to the currently open editor screen.
 *
 * SERVER-SIDE BRIDGE NOTE:
 * ========================
 * These handlers ONLY run on the client. They expect properly formatted
 * responses from your server-side bridge plugin. The bridge must:
 *
 * 1. Receive C2S packets on the matching channels
 * 2. Validate the requesting player has permission (e.g., op or custom perm)
 * 3. Perform the file operation WITHIN the scripts directory only
 * 4. Send back the appropriate S2C response with results
 *
 * Security checklist for the server bridge:
 * - Normalize all paths (resolve "..", ".", symlinks)
 * - Verify resolved path is within plugins/Skript/scripts/
 * - Check player permissions before every operation
 * - Limit file sizes to prevent abuse
 * - Rate-limit requests to prevent spam
 * - Log all file operations for audit
 */
public final class PacketHandler {

    private PacketHandler() {}

    /** Registers all client-side S2C packet receivers. Call once during client init. */
    public static void registerReceivers() {

        // --- Handshake Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.HandshakeResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        ConnectionState.getInstance().onHandshakeReceived(
                                payload.protocolVersion(), payload.pluginVersion(),
                                payload.allowedExtensions(), payload.maxFileSize());
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            screen.onHandshakeComplete();
                        }
                    });
                });

        // --- File Listing Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileListResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        // If we got a file list, the server bridge is alive (fallback for no-handshake servers)
                        ConnectionState.getInstance().markConnectedFallback();
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            screen.onFileListReceived(payload.path(), payload.entries());
                        }
                    });
                });

        // --- File Open Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileOpenResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            if (payload.success()) {
                                screen.onFileContentReceived(payload.path(), payload.content());
                            } else {
                                screen.onOperationError("Open failed: " + payload.error());
                            }
                        }
                    });
                });

        // --- File Save Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileSaveResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            if (payload.success()) {
                                screen.onFileSaved(payload.path());
                            } else {
                                screen.onOperationError("Save failed: " + payload.error());
                            }
                        }
                    });
                });

        // --- File Create Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileCreateResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            if (payload.success()) {
                                screen.onFileCreated(payload.path());
                            } else {
                                screen.onOperationError("Create failed: " + payload.error());
                            }
                        }
                    });
                });

        // --- File Rename Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileRenameResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            if (payload.success()) {
                                screen.onFileRenamed(payload.oldPath(), payload.newPath());
                            } else {
                                screen.onOperationError("Rename failed: " + payload.error());
                            }
                        }
                    });
                });

        // --- File Delete Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.FileDeleteResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            if (payload.success()) {
                                screen.onFileDeleted(payload.path());
                            } else {
                                screen.onOperationError("Delete failed: " + payload.error());
                            }
                        }
                    });
                });

        // --- Script Reload Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.ReloadResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        SkriptEditorScreen screen = getEditorScreen();
                        if (screen != null) {
                            screen.onReloadResponse(payload.path(), payload.success(),
                                    payload.message(), payload.logs());
                        }
                    });
                });

        // --- Dependency Graph Response ---
        ClientPlayNetworking.registerGlobalReceiver(
                SkriptPackets.DependencyGraphResponse.ID,
                (payload, context) -> {
                    context.client().execute(() -> {
                        // Route to graph screen if open, otherwise to editor screen
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.currentScreen instanceof DependencyGraphScreen graphScreen) {
                            graphScreen.onGraphReceived(payload);
                        } else {
                            SkriptEditorScreen editor = getEditorScreen();
                            if (editor != null) {
                                editor.onGraphReceived(payload);
                            }
                        }
                    });
                });
    }

    /** Safely gets the editor screen if it's currently open. */
    private static SkriptEditorScreen getEditorScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof SkriptEditorScreen editor) {
            return editor;
        }
        return null;
    }
}
