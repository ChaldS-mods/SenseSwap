package com.chalds.senseswap.server;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.network.RoleNetworking;
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

    private final Map<UUID, Integer> previousRoleIndices = new HashMap<>();

    private static PhaseManager instance = null;
    public static PhaseManager getInstance() { return instance; }
    public static PhaseManager start() { instance = new PhaseManager(); return instance; }
    public static void stop() { instance = null; }

    public PhaseManager() {
        this.ticksRemaining = getGameTicks();
    }

    public Phase getCurrentPhase() { return currentPhase; }
    public long getTicksRemaining() { return ticksRemaining; }
    public int getRoundNumber() { return roundNumber; }

    // Читаем из конфига каждый раз — чтобы изменения настроек применялись
    public static long getGameTicks() {
        return (long) ModConfig.get().gamePhaseDurationMinutes * 60 * 20;
    }
    public static long getRestTicks() {
        return (long) ModConfig.get().restPhaseDurationMinutes * 60 * 20;
    }

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

        playerRoles.forEach((uuid, role) -> previousRoleIndices.put(uuid, role.ordinal()));
        playerRoles.clear();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            RoleNetworking.sendRoleClearToClient(player);
            player.sendMessage(
                Text.translatable("senseswap.phase.rest_start").formatted(Formatting.AQUA), false);
        }

        RoleManager.saveRoles(server, playerRoles);
        broadcastPhase(server);
    }

    private void enterGame(MinecraftServer server, Map<UUID, SenseSwapMod.Role> playerRoles) {
        roundNumber++;
        currentPhase = Phase.GAME;
        ticksRemaining = getGameTicks();

        assignRolesAntiRepeat(server, playerRoles);
        RoleManager.saveRoles(server, playerRoles);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (playerRoles.containsKey(uuid)) {
                SenseSwapMod.Role role = playerRoles.get(uuid);
                RoleNetworking.sendRoleToClient(player, role);
                player.sendMessage(
                    Text.translatable("senseswap.phase.round_start", roundNumber)
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(" → "))
                        .append(Text.translatable(role.getTranslationKey())
                            .formatted(SenseSwapMod.getRoleFormatting(role))), false);
            } else {
                RoleNetworking.sendRoleClearToClient(player);
                player.sendMessage(
                    Text.translatable("senseswap.phase.round_start", roundNumber)
                        .formatted(Formatting.GREEN), false);
            }
        }

        broadcastPhase(server);
    }

    private void assignRolesAntiRepeat(MinecraftServer server,
                                       Map<UUID, SenseSwapMod.Role> playerRoles) {
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList());
        playerRoles.clear();
        if (players.size() < 3) return;

        SenseSwapMod.Role[] roles = SenseSwapMod.Role.values();
        List<ServerPlayerEntity> best = null;
        List<ServerPlayerEntity> shuffled = new ArrayList<>(players);

        for (int attempt = 0; attempt < 30; attempt++) {
            Collections.shuffle(shuffled);
            boolean noRepeat = true;
            for (int i = 0; i < roles.length && i < shuffled.size(); i++) {
                Integer prev = previousRoleIndices.get(shuffled.get(i).getUuid());
                if (prev != null && prev == i) { noRepeat = false; break; }
            }
            if (noRepeat) { best = new ArrayList<>(shuffled); break; }
            if (best == null) best = new ArrayList<>(shuffled);
        }

        for (int i = 0; i < roles.length && i < best.size(); i++) {
            playerRoles.put(best.get(i).getUuid(), roles[i]);
        }
    }

    private void broadcastPhase(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            RoleNetworking.sendPhaseToClient(player, currentPhase, ticksRemaining);
        }
    }
}
