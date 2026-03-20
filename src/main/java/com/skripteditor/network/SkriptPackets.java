package com.skripteditor.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines all custom network payloads for client <-> server communication.
 *
 * ARCHITECTURE NOTE FOR SERVER-SIDE BRIDGE:
 * ==========================================
 * This mod is the CLIENT side only. Your server-side Spigot/Paper plugin must:
 * 1. Register matching packet channels (same Identifiers defined here)
 * 2. Listen for C2S (client-to-server) payloads
 * 3. Perform the actual file I/O on the server filesystem
 * 4. Validate permissions and paths server-side
 * 5. Send back the corresponding S2C (server-to-client) response payloads
 *
 * All channel identifiers use the namespace "skripteditor".
 * The server bridge should register plugin message channels with these exact names.
 */
public final class SkriptPackets {

    private SkriptPackets() {}

    // =========================================================================
    // FILE LISTING
    // =========================================================================

    /** C2S: Request a directory listing within the scripts folder. */
    public record FileListRequest(String path) implements CustomPayload {
        public static final Id<FileListRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_list_req"));
        public static final PacketCodec<RegistryByteBuf, FileListRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileListRequest decode(RegistryByteBuf buf) {
                        return new FileListRequest(buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileListRequest val) {
                        buf.writeString(val.path);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** A single entry in a file listing. */
    public record FileEntry(String name, boolean isDirectory) {}

    /** S2C: Response containing directory contents. */
    public record FileListResponse(String path, List<FileEntry> entries) implements CustomPayload {
        public static final Id<FileListResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_list_res"));
        public static final PacketCodec<RegistryByteBuf, FileListResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileListResponse decode(RegistryByteBuf buf) {
                        String path = buf.readString(1024);
                        int count = buf.readVarInt();
                        List<FileEntry> entries = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) {
                            entries.add(new FileEntry(buf.readString(256), buf.readBoolean()));
                        }
                        return new FileListResponse(path, entries);
                    }
                    @Override public void encode(RegistryByteBuf buf, FileListResponse val) {
                        buf.writeString(val.path);
                        buf.writeVarInt(val.entries.size());
                        for (FileEntry e : val.entries) {
                            buf.writeString(e.name());
                            buf.writeBoolean(e.isDirectory());
                        }
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // FILE OPEN (read contents)
    // =========================================================================

    /** C2S: Request to read a file's contents. */
    public record FileOpenRequest(String path) implements CustomPayload {
        public static final Id<FileOpenRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_open_req"));
        public static final PacketCodec<RegistryByteBuf, FileOpenRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileOpenRequest decode(RegistryByteBuf buf) {
                        return new FileOpenRequest(buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileOpenRequest val) {
                        buf.writeString(val.path);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S2C: Response with file contents.
     * Content is sent as a single string. Large files may need chunking in your
     * server bridge implementation (Minecraft packet size limit is ~32KB for plugin messages).
     */
    public record FileOpenResponse(String path, String content, boolean success, String error) implements CustomPayload {
        public static final Id<FileOpenResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_open_res"));
        public static final PacketCodec<RegistryByteBuf, FileOpenResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileOpenResponse decode(RegistryByteBuf buf) {
                        return new FileOpenResponse(
                                buf.readString(1024),
                                buf.readString(262144), // Up to 256KB of content
                                buf.readBoolean(),
                                buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileOpenResponse val) {
                        buf.writeString(val.path);
                        buf.writeString(val.content);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // FILE SAVE
    // =========================================================================

    /** C2S: Request to save/write file contents. */
    public record FileSaveRequest(String path, String content) implements CustomPayload {
        public static final Id<FileSaveRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_save_req"));
        public static final PacketCodec<RegistryByteBuf, FileSaveRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileSaveRequest decode(RegistryByteBuf buf) {
                        return new FileSaveRequest(buf.readString(1024), buf.readString(262144));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileSaveRequest val) {
                        buf.writeString(val.path);
                        buf.writeString(val.content);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C: Response confirming save result. */
    public record FileSaveResponse(String path, boolean success, String error) implements CustomPayload {
        public static final Id<FileSaveResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_save_res"));
        public static final PacketCodec<RegistryByteBuf, FileSaveResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileSaveResponse decode(RegistryByteBuf buf) {
                        return new FileSaveResponse(buf.readString(1024), buf.readBoolean(), buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileSaveResponse val) {
                        buf.writeString(val.path);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // FILE CREATE
    // =========================================================================

    /** C2S: Request to create a new file or directory. */
    public record FileCreateRequest(String path, boolean isDirectory) implements CustomPayload {
        public static final Id<FileCreateRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_create_req"));
        public static final PacketCodec<RegistryByteBuf, FileCreateRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileCreateRequest decode(RegistryByteBuf buf) {
                        return new FileCreateRequest(buf.readString(1024), buf.readBoolean());
                    }
                    @Override public void encode(RegistryByteBuf buf, FileCreateRequest val) {
                        buf.writeString(val.path);
                        buf.writeBoolean(val.isDirectory);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C: Response confirming file/directory creation. */
    public record FileCreateResponse(String path, boolean success, String error) implements CustomPayload {
        public static final Id<FileCreateResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_create_res"));
        public static final PacketCodec<RegistryByteBuf, FileCreateResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileCreateResponse decode(RegistryByteBuf buf) {
                        return new FileCreateResponse(buf.readString(1024), buf.readBoolean(), buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileCreateResponse val) {
                        buf.writeString(val.path);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // FILE RENAME
    // =========================================================================

    /** C2S: Request to rename/move a file. */
    public record FileRenameRequest(String oldPath, String newPath) implements CustomPayload {
        public static final Id<FileRenameRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_rename_req"));
        public static final PacketCodec<RegistryByteBuf, FileRenameRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileRenameRequest decode(RegistryByteBuf buf) {
                        return new FileRenameRequest(buf.readString(1024), buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileRenameRequest val) {
                        buf.writeString(val.oldPath);
                        buf.writeString(val.newPath);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C: Response confirming rename result. */
    public record FileRenameResponse(String oldPath, String newPath, boolean success, String error)
            implements CustomPayload {
        public static final Id<FileRenameResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_rename_res"));
        public static final PacketCodec<RegistryByteBuf, FileRenameResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileRenameResponse decode(RegistryByteBuf buf) {
                        return new FileRenameResponse(
                                buf.readString(1024), buf.readString(1024),
                                buf.readBoolean(), buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileRenameResponse val) {
                        buf.writeString(val.oldPath);
                        buf.writeString(val.newPath);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // FILE DELETE
    // =========================================================================

    /** C2S: Request to delete a file or empty directory. */
    public record FileDeleteRequest(String path) implements CustomPayload {
        public static final Id<FileDeleteRequest> ID =
                new Id<>(Identifier.of("skripteditor", "file_delete_req"));
        public static final PacketCodec<RegistryByteBuf, FileDeleteRequest> CODEC =
                new PacketCodec<>() {
                    @Override public FileDeleteRequest decode(RegistryByteBuf buf) {
                        return new FileDeleteRequest(buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileDeleteRequest val) {
                        buf.writeString(val.path);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S2C: Response confirming deletion result. */
    public record FileDeleteResponse(String path, boolean success, String error) implements CustomPayload {
        public static final Id<FileDeleteResponse> ID =
                new Id<>(Identifier.of("skripteditor", "file_delete_res"));
        public static final PacketCodec<RegistryByteBuf, FileDeleteResponse> CODEC =
                new PacketCodec<>() {
                    @Override public FileDeleteResponse decode(RegistryByteBuf buf) {
                        return new FileDeleteResponse(buf.readString(1024), buf.readBoolean(), buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, FileDeleteResponse val) {
                        buf.writeString(val.path);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // SCRIPT RELOAD
    // =========================================================================

    /** C2S: Request to reload a script (empty path = reload all). */
    public record ReloadRequest(String path) implements CustomPayload {
        public static final Id<ReloadRequest> ID =
                new Id<>(Identifier.of("skripteditor", "reload_req"));
        public static final PacketCodec<RegistryByteBuf, ReloadRequest> CODEC =
                new PacketCodec<>() {
                    @Override public ReloadRequest decode(RegistryByteBuf buf) {
                        return new ReloadRequest(buf.readString(1024));
                    }
                    @Override public void encode(RegistryByteBuf buf, ReloadRequest val) {
                        buf.writeString(val.path);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S2C: Response with reload results and any log/error messages.
     * The logs list contains individual lines of output from the Skript reload process.
     */
    public record ReloadResponse(String path, boolean success, String message, List<String> logs)
            implements CustomPayload {
        public static final Id<ReloadResponse> ID =
                new Id<>(Identifier.of("skripteditor", "reload_res"));
        public static final PacketCodec<RegistryByteBuf, ReloadResponse> CODEC =
                new PacketCodec<>() {
                    @Override public ReloadResponse decode(RegistryByteBuf buf) {
                        String path = buf.readString(1024);
                        boolean success = buf.readBoolean();
                        String message = buf.readString(4096);
                        int logCount = buf.readVarInt();
                        List<String> logs = new ArrayList<>(logCount);
                        for (int i = 0; i < logCount; i++) {
                            logs.add(buf.readString(4096));
                        }
                        return new ReloadResponse(path, success, message, logs);
                    }
                    @Override public void encode(RegistryByteBuf buf, ReloadResponse val) {
                        buf.writeString(val.path);
                        buf.writeBoolean(val.success);
                        buf.writeString(val.message);
                        buf.writeVarInt(val.logs.size());
                        for (String log : val.logs) {
                            buf.writeString(log);
                        }
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // DEPENDENCY GRAPH
    // =========================================================================

    /** C2S: Request the server to analyze all scripts and return a dependency graph. */
    public record DependencyGraphRequest() implements CustomPayload {
        public static final Id<DependencyGraphRequest> ID =
                new Id<>(Identifier.of("skripteditor", "dep_graph_req"));
        public static final PacketCodec<RegistryByteBuf, DependencyGraphRequest> CODEC =
                new PacketCodec<>() {
                    @Override public DependencyGraphRequest decode(RegistryByteBuf buf) {
                        return new DependencyGraphRequest();
                    }
                    @Override public void encode(RegistryByteBuf buf, DependencyGraphRequest val) {
                        // No fields
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** A node in the dependency graph representing a single .sk script file. */
    public record GraphNode(String scriptPath, List<String> definedFunctions) {}

    /** A directed edge: the script at fromIndex calls a function defined in the script at toIndex. */
    public record GraphEdge(int fromIndex, int toIndex, String functionName) {}

    /**
     * S2C: Response containing the full dependency graph.
     *
     * SERVER-SIDE BRIDGE NOTE:
     * To build this response, the server should:
     * 1. Scan all .sk files in the scripts directory
     * 2. Parse each file for "function <name>(...)" definitions
     * 3. Parse each file for function calls (e.g., "myFunc(...)")
     * 4. Match calls to definitions across files
     * 5. Build edges: fromIndex = calling script, toIndex = defining script
     */
    public record DependencyGraphResponse(boolean success, String error,
            List<GraphNode> nodes, List<GraphEdge> edges) implements CustomPayload {
        public static final Id<DependencyGraphResponse> ID =
                new Id<>(Identifier.of("skripteditor", "dep_graph_res"));
        public static final PacketCodec<RegistryByteBuf, DependencyGraphResponse> CODEC =
                new PacketCodec<>() {
                    @Override public DependencyGraphResponse decode(RegistryByteBuf buf) {
                        boolean success = buf.readBoolean();
                        String error = buf.readString(1024);
                        int nodeCount = buf.readVarInt();
                        List<GraphNode> nodes = new ArrayList<>(nodeCount);
                        for (int i = 0; i < nodeCount; i++) {
                            String path = buf.readString(1024);
                            int funcCount = buf.readVarInt();
                            List<String> funcs = new ArrayList<>(funcCount);
                            for (int j = 0; j < funcCount; j++) {
                                funcs.add(buf.readString(256));
                            }
                            nodes.add(new GraphNode(path, funcs));
                        }
                        int edgeCount = buf.readVarInt();
                        List<GraphEdge> edges = new ArrayList<>(edgeCount);
                        for (int i = 0; i < edgeCount; i++) {
                            edges.add(new GraphEdge(buf.readVarInt(), buf.readVarInt(), buf.readString(256)));
                        }
                        return new DependencyGraphResponse(success, error, nodes, edges);
                    }
                    @Override public void encode(RegistryByteBuf buf, DependencyGraphResponse val) {
                        buf.writeBoolean(val.success);
                        buf.writeString(val.error);
                        buf.writeVarInt(val.nodes.size());
                        for (GraphNode node : val.nodes) {
                            buf.writeString(node.scriptPath());
                            buf.writeVarInt(node.definedFunctions().size());
                            for (String func : node.definedFunctions()) {
                                buf.writeString(func);
                            }
                        }
                        buf.writeVarInt(val.edges.size());
                        for (GraphEdge edge : val.edges) {
                            buf.writeVarInt(edge.fromIndex());
                            buf.writeVarInt(edge.toIndex());
                            buf.writeString(edge.functionName());
                        }
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // VERSION HANDSHAKE
    // =========================================================================

    /** The current protocol version. Increment when packet formats change. */
    public static final int PROTOCOL_VERSION = 1;

    /** C2S: Sent when the editor opens to verify server bridge compatibility. */
    public record HandshakeRequest(int protocolVersion, String modVersion) implements CustomPayload {
        public static final Id<HandshakeRequest> ID =
                new Id<>(Identifier.of("skripteditor", "handshake_req"));
        public static final PacketCodec<RegistryByteBuf, HandshakeRequest> CODEC =
                new PacketCodec<>() {
                    @Override public HandshakeRequest decode(RegistryByteBuf buf) {
                        return new HandshakeRequest(buf.readVarInt(), buf.readString(64));
                    }
                    @Override public void encode(RegistryByteBuf buf, HandshakeRequest val) {
                        buf.writeVarInt(val.protocolVersion);
                        buf.writeString(val.modVersion);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S2C: Server responds with its version info, supported features, and allowed extensions.
     *
     * SERVER-SIDE BRIDGE NOTE:
     * When receiving a HandshakeRequest, respond with:
     * - Your plugin's protocol version (must match client's PROTOCOL_VERSION)
     * - Your plugin version string
     * - A comma-separated list of allowed file extensions (e.g. ".sk,.yml,.txt")
     * - Maximum file size in bytes your plugin accepts (e.g. 262144 for 256KB)
     */
    public record HandshakeResponse(int protocolVersion, String pluginVersion,
                                     String allowedExtensions, int maxFileSize) implements CustomPayload {
        public static final Id<HandshakeResponse> ID =
                new Id<>(Identifier.of("skripteditor", "handshake_res"));
        public static final PacketCodec<RegistryByteBuf, HandshakeResponse> CODEC =
                new PacketCodec<>() {
                    @Override public HandshakeResponse decode(RegistryByteBuf buf) {
                        return new HandshakeResponse(
                                buf.readVarInt(), buf.readString(64),
                                buf.readString(512), buf.readVarInt());
                    }
                    @Override public void encode(RegistryByteBuf buf, HandshakeResponse val) {
                        buf.writeVarInt(val.protocolVersion);
                        buf.writeString(val.pluginVersion);
                        buf.writeString(val.allowedExtensions);
                        buf.writeVarInt(val.maxFileSize);
                    }
                };
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // =========================================================================
    // REGISTRATION
    // =========================================================================

    /**
     * Registers all packet types with Fabric's networking system.
     * Call this from your client mod initializer.
     *
     * SERVER-SIDE BRIDGE NOTE:
     * Your server plugin must register the SAME channels. For Spigot/Paper,
     * register plugin message channels with names like:
     *   "skripteditor:file_list_req"
     *   "skripteditor:file_list_res"
     *   etc.
     *
     * Use the Minecraft plugin messaging API:
     *   Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, "skripteditor:file_list_res");
     *   Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "skripteditor:file_list_req", listener);
     */
    public static void registerAll() {
        // Client-to-Server packets
        PayloadTypeRegistry.playC2S().register(HandshakeRequest.ID, HandshakeRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileListRequest.ID, FileListRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileOpenRequest.ID, FileOpenRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileSaveRequest.ID, FileSaveRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileCreateRequest.ID, FileCreateRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileRenameRequest.ID, FileRenameRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(FileDeleteRequest.ID, FileDeleteRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(ReloadRequest.ID, ReloadRequest.CODEC);
        PayloadTypeRegistry.playC2S().register(DependencyGraphRequest.ID, DependencyGraphRequest.CODEC);

        // Server-to-Client packets
        PayloadTypeRegistry.playS2C().register(HandshakeResponse.ID, HandshakeResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileListResponse.ID, FileListResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileOpenResponse.ID, FileOpenResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileSaveResponse.ID, FileSaveResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileCreateResponse.ID, FileCreateResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileRenameResponse.ID, FileRenameResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(FileDeleteResponse.ID, FileDeleteResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(ReloadResponse.ID, ReloadResponse.CODEC);
        PayloadTypeRegistry.playS2C().register(DependencyGraphResponse.ID, DependencyGraphResponse.CODEC);
    }
}
