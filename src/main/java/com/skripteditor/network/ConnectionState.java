package com.skripteditor.network;

import com.skripteditor.SkriptEditorClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks the connection state between the client mod and the server bridge plugin.
 * Singleton — shared across the editor screen, packet handler, and status bar.
 *
 * Populated when the server responds to a HandshakeRequest.
 * Reset when the player disconnects or when the handshake times out.
 */
public final class ConnectionState {

    // Default safety limits (used when no handshake has been received)
    private static final int DEFAULT_MAX_FILE_SIZE = 262144; // 256KB
    private static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of(
            ".sk", ".yml", ".yaml", ".txt", ".csv", ".json", ".log"
    );
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            ".jar", ".exe", ".bat", ".sh", ".cmd", ".ps1", ".dll", ".so",
            ".class", ".py", ".rb", ".php", ".jsp", ".war", ".zip", ".tar",
            ".gz", ".rar", ".7z", ".msi", ".deb", ".rpm"
    );

    // INSTANCE must be declared AFTER the static constants it depends on
    private static final ConnectionState INSTANCE = new ConnectionState();

    private boolean connected = false;
    private boolean handshakeComplete = false;
    private long handshakeSentAt = 0;
    private String serverPluginVersion = "";
    private int serverProtocolVersion = 0;
    private int maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private Set<String> allowedExtensions = new HashSet<>(DEFAULT_ALLOWED_EXTENSIONS);

    private ConnectionState() {}

    public static ConnectionState getInstance() {
        return INSTANCE;
    }

    /** Called when the editor opens to initiate a handshake. */
    public void beginHandshake() {
        handshakeSentAt = System.currentTimeMillis();
        handshakeComplete = false;
    }

    /** Called when the server responds to the handshake. */
    public void onHandshakeReceived(int protocolVersion, String pluginVersion,
                                     String extensions, int maxSize) {
        this.connected = true;
        this.handshakeComplete = true;
        this.serverProtocolVersion = protocolVersion;
        this.serverPluginVersion = pluginVersion;
        this.maxFileSize = maxSize > 0 ? maxSize : DEFAULT_MAX_FILE_SIZE;

        // Parse allowed extensions from server
        if (extensions != null && !extensions.isEmpty()) {
            allowedExtensions.clear();
            for (String ext : extensions.split(",")) {
                String trimmed = ext.trim().toLowerCase();
                if (!trimmed.startsWith(".")) trimmed = "." + trimmed;
                // Never allow blocked extensions even if server sends them
                if (!BLOCKED_EXTENSIONS.contains(trimmed)) {
                    allowedExtensions.add(trimmed);
                }
            }
        }

        if (protocolVersion != SkriptPackets.PROTOCOL_VERSION) {
            SkriptEditorClient.LOGGER.warn(
                    "Server bridge protocol version {} does not match client version {}. Some features may not work.",
                    protocolVersion, SkriptPackets.PROTOCOL_VERSION);
        }

        SkriptEditorClient.LOGGER.info("Connected to server bridge v{} (protocol {})",
                pluginVersion, protocolVersion);
    }

    /** Called when the first file list response arrives (fallback for servers without handshake). */
    public void markConnectedFallback() {
        if (!handshakeComplete) {
            connected = true;
            SkriptEditorClient.LOGGER.info("Server bridge responded (no handshake support — using defaults)");
        }
    }

    /** Called when the player disconnects. */
    public void reset() {
        connected = false;
        handshakeComplete = false;
        handshakeSentAt = 0;
        serverPluginVersion = "";
        serverProtocolVersion = 0;
        maxFileSize = DEFAULT_MAX_FILE_SIZE;
        allowedExtensions = new HashSet<>(DEFAULT_ALLOWED_EXTENSIONS);
    }

    // --- Queries ---

    public boolean isConnected() { return connected; }
    public boolean isHandshakeComplete() { return handshakeComplete; }
    public String getServerPluginVersion() { return serverPluginVersion; }
    public int getServerProtocolVersion() { return serverProtocolVersion; }
    public int getMaxFileSize() { return maxFileSize; }
    public Set<String> getAllowedExtensions() { return Collections.unmodifiableSet(allowedExtensions); }

    /** Returns true if the protocol versions match. */
    public boolean isProtocolCompatible() {
        return !handshakeComplete || serverProtocolVersion == SkriptPackets.PROTOCOL_VERSION;
    }

    /**
     * Returns true if the given file path has an allowed extension.
     * Always blocks known dangerous extensions regardless of server config.
     */
    public boolean isExtensionAllowed(String path) {
        if (path == null || path.isEmpty()) return false;
        String lower = path.toLowerCase();
        int lastDot = lower.lastIndexOf('.');
        if (lastDot < 0) return false;
        String ext = lower.substring(lastDot);

        // Always block dangerous extensions
        if (BLOCKED_EXTENSIONS.contains(ext)) return false;

        return allowedExtensions.contains(ext);
    }

    /**
     * Returns true if the given content size is within the allowed limit.
     */
    public boolean isFileSizeAllowed(int sizeBytes) {
        return sizeBytes <= maxFileSize;
    }

    /** Returns a human-readable file size limit string (e.g. "256 KB"). */
    public String getMaxFileSizeDisplay() {
        if (maxFileSize >= 1048576) {
            return String.format("%.1f MB", maxFileSize / 1048576.0);
        }
        return (maxFileSize / 1024) + " KB";
    }

    /** Returns a status string for the UI. */
    public String getStatusText() {
        if (!connected) return "Disconnected";
        if (!handshakeComplete) return "Connected (legacy)";
        if (!isProtocolCompatible()) return "Connected (version mismatch!)";
        return "Connected v" + serverPluginVersion;
    }
}
