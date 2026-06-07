package com.chalds.senseswap.server;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.network.RoleNetworking;
import com.chalds.senseswap.network.SummaryNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

public class PhaseManager {

    public enum Phase { GAME, REST }

    private Phase currentPhase = Phase.GAME;
    private long ticksRemaining;
    private int roundNumber = 1;

    private final Map<UUID, Integer> prevScores = new HashMap<>();
    private final Map<UUID, SenseSwapMod.Role> prevRoles = new HashMap<>();

    private static PhaseManager instance = null;
    public static PhaseManager getInstance() { return instance; }
    public static PhaseManager start() { instance = new PhaseManager(); return instance; }
    public static void stop() { instance = null; }

    public PhaseManager() { this.ticksRemaining = getGameTicks(); }

    public Phase getCurrentPhase() { return currentPhase; }
    public long getTicksRemaining() { return ticksRemaining; }
    public int getRoundNumber() { return roundNumber; }

    public static long getGameTicks() { return (long) ModConfig.get().gamePhaseDurationMinutes * 60 * 20; }
    public static long getRestTicks()  { return (long) ModConfig.get().restPhaseDurationMinutes  * 60 * 20; }

    public void tick(MinecraftServer server, Map<UUID, SenseSwapMod.Role> playerRoles) {
        if (server.getPlayerManager().getCurrentPlayerCount() == 0) return;
        ticksRemaining--;
        if (ticksRemaining % 20 == 0) broadcastPhase(server);
        if (ticksRemaining <= 0) {
            if (currentPhase == Phase.GAME) enterRest(server, playerRoles);
            else enterGame(server, playerRoles);
        }
    }

    private void enterRest(MinecraftServer server, Map<UUID, SenseSwapMod.Role> playerRoles) {
        currentPhase = Phase.REST;
        ticksRemaining = getRestTicks();

        Map<UUID, Integer> before = new HashMap<>(ScoreManager.getAllScores());
        ScoreManager.awardRoundPoints(server, playerRoles);
        Map<UUID, Integer> after  = ScoreManager.getAllScores();

        List<SummaryNetworking.SummaryEntry> entries = new ArrayList<>();
        List<Map.Entry<UUID, Integer>> board = ScoreManager.getLeaderboard();
        for (Map.Entry<UUID, Integer> e : board) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            String name = p != null ? p.getName().getString() : ScoreManager.getPlayerName(e.getKey());
            SenseSwapMod.Role role = playerRoles.getOrDefault(e.getKey(), SenseSwapMod.Role.BLIND);
            int delta = e.getValue() - before.getOrDefault(e.getKey(), 0);
            entries.add(new SummaryNetworking.SummaryEntry(name, role, e.getValue(), delta));
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            RoleNetworking.sendRoleClearToClient(p);
            SummaryNetworking.sendSummary(p, roundNumber, entries);
            p.sendMessage(Text.translatable("senseswap.phase.rest_start").formatted(Formatting.AQUA), false);
        }

        prevRoles.clear();
        prevRoles.putAll(playerRoles);
        playerRoles.clear();
        RoleManager.saveRoles(server, playerRoles);
        broadcastPhase(server);
    }

    private void enterGame(MinecraftServer server, Map<UUID, SenseSwapMod.Role> playerRoles) {
        roundNumber++;
        currentPhase = Phase.GAME;
        ticksRemaining = getGameTicks();

        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        Collections.shuffle(players);

        boolean useDizzy = ModConfig.get().enableDizzyRole && players.size() >= 4;

        if (ModConfig.get().duoMode) {
            assignDuoRoles(server, playerRoles, players);
        } else {
            List<SenseSwapMod.Role> rolePool = new ArrayList<>(useDizzy
                ? List.of(SenseSwapMod.Role.BLIND, SenseSwapMod.Role.DEAF,
                          SenseSwapMod.Role.MUTE,  SenseSwapMod.Role.DIZZY)
                : List.of(SenseSwapMod.Role.BLIND, SenseSwapMod.Role.DEAF,
                          SenseSwapMod.Role.MUTE));
            // Shuffle until no player gets the same role as last round
            for (int attempt = 0; attempt < 20; attempt++) {
                Collections.shuffle(rolePool);
                boolean anyRepeat = false;
                for (int i = 0; i < players.size() && i < rolePool.size(); i++) {
                    SenseSwapMod.Role prev = prevRoles.get(players.get(i).getUuid());
                    if (prev != null && prev == rolePool.get(i)) {
                        anyRepeat = true;
                        break;
                    }
                }
                if (!anyRepeat) break;
            }
            prevRoles.clear();
            for (int i = 0; i < rolePool.size() && i < players.size(); i++) {
                playerRoles.put(players.get(i).getUuid(), rolePool.get(i));
                prevRoles.put(players.get(i).getUuid(), rolePool.get(i));
            }
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID uuid = p.getUuid();
            if (playerRoles.containsKey(uuid)) {
                RoleNetworking.sendRoleToClient(p, playerRoles.get(uuid));
            }
            p.sendMessage(Text.translatable("senseswap.phase.round_start", roundNumber)
                .formatted(Formatting.GREEN), false);
        }

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            RoleNetworking.sendPhaseToClient(p, currentPhase, ticksRemaining);
        }

        RoleManager.saveRoles(server, playerRoles);
    }

    private void assignDuoRoles(MinecraftServer server, Map<UUID, SenseSwapMod.Role> roles,
                                 List<ServerPlayerEntity> players) {
        String p1name = ModConfig.get().duoPlayer1;
        String p2name = ModConfig.get().duoPlayer2;

        SenseSwapMod.Role[] pool = {SenseSwapMod.Role.BLIND, SenseSwapMod.Role.DEAF,
                                     SenseSwapMod.Role.MUTE};
        List<SenseSwapMod.Role> shuffled = new ArrayList<>(List.of(pool));
        Collections.shuffle(shuffled);

        for (ServerPlayerEntity p : players) {
            String name = p.getName().getString();
            if (name.equalsIgnoreCase(p1name)) roles.put(p.getUuid(), shuffled.get(0));
            else if (name.equalsIgnoreCase(p2name)) roles.put(p.getUuid(), shuffled.get(1));
        }
    }

    private void broadcastPhase(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            RoleNetworking.sendPhaseToClient(p, currentPhase, ticksRemaining);
        }
    }
}
