package com.chalds.senseswap.server;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.config.ModConfig;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ScoreManager — NEW in 4.0.0
 *
 * Tracks per-player scores across rounds.
 * Scores are saved to <world>/senseswap_scores.json and persist between sessions.
 *
 * Scoring rules:
 *  +scorePerRound  — for each completed game round (alive at the end)
 *  +5              — bonus for completing a round as BLIND (hardest role)
 *  +3              — bonus for completing a round as DEAF
 *  +2              — bonus for completing a round as MUTE
 *  +1              — bonus for completing a round as DIZZY
 */
public class ScoreManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String SCORES_FILE = "senseswap_scores.json";

    /** uuid → total score */
    private static final Map<UUID, Integer> scores = new LinkedHashMap<>();

    /** uuid → display name (last seen, for offline display) */
    private static final Map<UUID, String> playerNames = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Award points at the end of a game round to all players who were online
     * and had a role assigned.
     */
    public static void awardRoundPoints(MinecraftServer server,
                                         Map<UUID, SenseSwapMod.Role> playerRoles) {
        if (!ModConfig.get().scoreEnabled) return;

        int base = ModConfig.get().scorePerRound;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            playerNames.put(uuid, player.getName().getString());

            SenseSwapMod.Role role = playerRoles.get(uuid);
            int bonus = role == null ? 0 : switch (role) {
                case BLIND -> 5;
                case DEAF  -> 3;
                case MUTE  -> 2;
                case DIZZY -> 1;
            };

            scores.merge(uuid, base + bonus, Integer::sum);
        }

        saveScores(server);
    }

    /** Add or subtract points for a specific player (e.g. from command). */
    public static void addPoints(UUID uuid, String name, int delta) {
        playerNames.put(uuid, name);
        scores.merge(uuid, delta, Integer::sum);
    }

    /** Reset all scores. */
    public static void resetScores(MinecraftServer server) {
        scores.clear();
        saveScores(server);
    }

    /** Returns sorted leaderboard (highest first). */
    public static List<Map.Entry<UUID, Integer>> getLeaderboard() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    public static String getPlayerName(UUID uuid) {
        return playerNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public static int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static Path getPath(MinecraftServer server) {
        return server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve(SCORES_FILE);
    }

    public static void loadScores(MinecraftServer server) {
        Path path = getPath(server);
        if (!Files.exists(path)) return;

        try (Reader reader = new FileReader(path.toFile())) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;

            if (root.has("scores")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("scores").entrySet()) {
                    try {
                        scores.put(UUID.fromString(e.getKey()), e.getValue().getAsInt());
                    } catch (Exception ignored) {}
                }
            }
            if (root.has("names")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("names").entrySet()) {
                    try {
                        playerNames.put(UUID.fromString(e.getKey()), e.getValue().getAsString());
                    } catch (Exception ignored) {}
                }
            }
            System.out.println("[SenseSwap] Loaded scores for " + scores.size() + " player(s).");
        } catch (IOException e) {
            System.err.println("[SenseSwap] Failed to load scores: " + e.getMessage());
        }
    }

    public static void saveScores(MinecraftServer server) {
        Path path = getPath(server);
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            System.err.println("[SenseSwap] Could not create world directory: " + e.getMessage());
            return;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", SenseSwapMod.VERSION);
        root.addProperty("saved_at", java.time.Instant.now().toString());

        JsonObject scoresObj = new JsonObject();
        scores.forEach((uuid, score) -> scoresObj.addProperty(uuid.toString(), score));
        root.add("scores", scoresObj);

        JsonObject namesObj = new JsonObject();
        playerNames.forEach((uuid, name) -> namesObj.addProperty(uuid.toString(), name));
        root.add("names", namesObj);

        try (Writer writer = new FileWriter(path.toFile())) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            System.err.println("[SenseSwap] Failed to save scores: " + e.getMessage());
        }
    }

    public static Map<UUID, Integer> getAllScores() {
        return Collections.unmodifiableMap(scores);
    }
}
