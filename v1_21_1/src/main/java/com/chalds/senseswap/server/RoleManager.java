package com.chalds.senseswap.server;

import com.chalds.senseswap.SenseSwapMod;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Handles server-side persistence of player roles.
 * Roles are saved to: <world_folder>/senseswap_roles.json
 *
 * This means each world has its own role assignments — perfect for multi-world servers
 * or when you want roles tied to a specific game session/save.
 */
public class RoleManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ROLES_FILE = "senseswap_roles.json";

    /**
     * Returns the path to the roles file inside the world/save directory.
     * On a dedicated server: <server_dir>/<world_name>/senseswap_roles.json
     * In singleplayer: <saves>/<world_name>/senseswap_roles.json
     */
    private static Path getRolesPath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve(ROLES_FILE);
    }

    /**
     * Loads roles from the world directory into the provided map.
     * If the file doesn't exist, the map is left unchanged (empty).
     */
    public static void loadRoles(MinecraftServer server, Map<UUID, SenseSwapMod.Role> roles) {
        Path path = getRolesPath(server);
        if (!Files.exists(path)) {
            System.out.println("[SenseSwap] No saved roles found in world directory (fresh start).");
            return;
        }

        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null || !root.has("roles")) return;

            JsonObject rolesObj = root.getAsJsonObject("roles");
            int loaded = 0;

            for (Map.Entry<String, JsonElement> entry : rolesObj.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    SenseSwapMod.Role role = SenseSwapMod.Role.valueOf(entry.getValue().getAsString());
                    roles.put(uuid, role);
                    loaded++;
                } catch (IllegalArgumentException e) {
                    System.err.println("[SenseSwap] Skipping invalid role entry: " + entry.getKey() + " → " + entry.getValue());
                }
            }

            System.out.println("[SenseSwap] Loaded " + loaded + " role(s) from: " + path);

        } catch (IOException e) {
            System.err.println("[SenseSwap] Failed to load roles: " + e.getMessage());
        }
    }

    /**
     * Saves the current role map to the world directory.
     * Overwrites any existing file.
     */
    public static void saveRoles(MinecraftServer server, Map<UUID, SenseSwapMod.Role> roles) {
        Path path = getRolesPath(server);

        // Ensure parent directory exists (it should, but just in case)
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            System.err.println("[SenseSwap] Could not create world directory: " + e.getMessage());
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", SenseSwapMod.VERSION);
        root.addProperty("saved_at", java.time.Instant.now().toString());

        JsonObject rolesObj = new JsonObject();
        for (Map.Entry<UUID, SenseSwapMod.Role> entry : roles.entrySet()) {
            rolesObj.addProperty(entry.getKey().toString(), entry.getValue().name());
        }
        root.add("roles", rolesObj);

        try (Writer writer = new FileWriter(path.toFile())) {
            GSON.toJson(root, writer);
            System.out.println("[SenseSwap] Saved " + roles.size() + " role(s) to: " + path);
        } catch (IOException e) {
            System.err.println("[SenseSwap] Failed to save roles: " + e.getMessage());
        }
    }
}
