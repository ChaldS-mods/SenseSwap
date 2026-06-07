package com.chalds.senseswap;

import com.chalds.senseswap.network.RoleNetworking;
import com.chalds.senseswap.server.PhaseManager;
import com.chalds.senseswap.server.RoleManager;
import com.chalds.senseswap.server.ScoreManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;

public class SenseSwapMod implements ModInitializer {

    public static final String MOD_ID = "senseswap";
    public static final String VERSION = "5.0.0";

    private static final Map<UUID, Role> playerRoles = new HashMap<>();
    private static boolean gameRunning = false;
    private static MinecraftServer server;
    private static long sessionStartTime = 0;

    public enum Role {
        BLIND("senseswap.role.blind"),
        DEAF("senseswap.role.deaf"),
        MUTE("senseswap.role.mute"),
        DIZZY("senseswap.role.dizzy");  // NEW in 4.0

        private final String translationKey;
        Role(String translationKey) { this.translationKey = translationKey; }
        public String getTranslationKey() { return translationKey; }
    }

    @Override
    public void onInitialize() {
        RoleNetworking.registerServer();
        com.chalds.senseswap.network.SummaryNetworking.registerServer();

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            RoleManager.loadRoles(srv, playerRoles);
            ScoreManager.loadScores(srv);
            System.out.println("[SenseSwap] v" + VERSION + " Server initialized.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            RoleManager.saveRoles(srv, playerRoles);
            ScoreManager.saveScores(srv);
            PhaseManager.stop();
        });

        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            PhaseManager pm = PhaseManager.getInstance();
            if (pm != null) {
                pm.tick(srv, playerRoles);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity player = handler.player;
            UUID uuid = player.getUuid();

            if (playerRoles.containsKey(uuid)) {
                Role role = playerRoles.get(uuid);
                srv.execute(() -> {
                    RoleNetworking.sendRoleToClient(player, role);
                    player.sendMessage(
                        Text.translatable("senseswap.message.role_restored",
                            Text.translatable(role.getTranslationKey()).formatted(getRoleFormatting(role)))
                            .formatted(Formatting.YELLOW),
                        false
                    );
                });
            }

            PhaseManager pm = PhaseManager.getInstance();
            if (pm != null) {
                srv.execute(() -> RoleNetworking.sendPhaseToClient(
                    player, pm.getCurrentPhase(), pm.getTicksRemaining()));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) ->
            RoleManager.saveRoles(srv, playerRoles));

        // ── Команды ───────────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("ss")
                    .requires(source -> source.hasPermissionLevel(2))

                    .then(literal("start")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            List<ServerPlayerEntity> players = new ArrayList<>(s.getPlayerManager().getPlayerList());

                            int minP = com.chalds.senseswap.config.ModConfig.get().duoMode ? 2 : com.chalds.senseswap.config.ModConfig.get().minPlayers;
                            if (players.size() < minP) {
                                ctx.getSource().sendError(Text.translatable("senseswap.command.need_players", minP));
                                return 0;
                            }

                            Collections.shuffle(players);
                            playerRoles.clear();

                            // 4.0: use DIZZY role if enabled and there are 4+ players
                            boolean useDizzy = com.chalds.senseswap.config.ModConfig.get().enableDizzyRole
                                && players.size() >= 4;
                            Role[] roles = useDizzy
                                ? new Role[]{Role.BLIND, Role.DEAF, Role.MUTE, Role.DIZZY}
                                : new Role[]{Role.BLIND, Role.DEAF, Role.MUTE};

                            for (int i = 0; i < roles.length && i < players.size(); i++) {
                                playerRoles.put(players.get(i).getUuid(), roles[i]);
                            }

                            gameRunning = true;
                            sessionStartTime = System.currentTimeMillis();

                            PhaseManager pm = null;
                            if (com.chalds.senseswap.config.ModConfig.get().phaseCycleEnabled) {
                                pm = PhaseManager.start();
                            }

                            broadcastRoles(s);
                            if (pm != null) {
                                final PhaseManager pmFinal = pm;
                                for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                                    RoleNetworking.sendPhaseToClient(p, pmFinal.getCurrentPhase(), pmFinal.getTicksRemaining());
                                }
                            }

                            RoleManager.saveRoles(s, playerRoles);

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.started").formatted(Formatting.GREEN), true);
                            return 1;
                        })
                    )

                    .then(literal("stop")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            playerRoles.clear();
                            gameRunning = false;
                            sessionStartTime = 0;
                            PhaseManager.stop();

                            for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) {
                                RoleNetworking.sendRoleClearToClient(player);
                                RoleNetworking.sendPhaseStopToClient(player);
                            }

                            RoleManager.saveRoles(s, playerRoles);
                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.stopped").formatted(Formatting.RED), true);
                            return 1;
                        })
                    )

                    // ── NEW 4.0: /ss swap — randomly shuffle roles between players ──
                    .then(literal("swap")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            if (playerRoles.isEmpty()) {
                                ctx.getSource().sendError(Text.translatable("senseswap.command.no_roles"));
                                return 0;
                            }

                            List<UUID> uuids = new ArrayList<>(playerRoles.keySet());
                            List<Role> roles = new ArrayList<>(playerRoles.values());
                            Collections.shuffle(roles);
                            for (int i = 0; i < uuids.size(); i++) {
                                playerRoles.put(uuids.get(i), roles.get(i));
                            }

                            broadcastRoles(s);
                            RoleManager.saveRoles(s, playerRoles);

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.swapped").formatted(Formatting.LIGHT_PURPLE), true);
                            return 1;
                        })
                    )

                    .then(literal("setrole")
                        .then(argument("player", StringArgumentType.word())
                            .then(argument("role", StringArgumentType.word())
                                .executes(ctx -> {
                                    MinecraftServer s = ctx.getSource().getServer();
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    String roleName = StringArgumentType.getString(ctx, "role").toUpperCase();
                                    ServerPlayerEntity target = s.getPlayerManager().getPlayer(playerName);
                                    if (target == null) {
                                        ctx.getSource().sendError(Text.translatable("senseswap.command.player_not_found", playerName));
                                        return 0;
                                    }
                                    Role role;
                                    try { role = Role.valueOf(roleName); }
                                    catch (IllegalArgumentException e) {
                                        ctx.getSource().sendError(Text.translatable("senseswap.command.invalid_role", roleName));
                                        return 0;
                                    }
                                    playerRoles.put(target.getUuid(), role);
                                    RoleNetworking.sendRoleToClient(target, role);
                                    RoleManager.saveRoles(s, playerRoles);
                                    ctx.getSource().sendFeedback(() ->
                                        Text.translatable("senseswap.command.role_set",
                                            target.getName(),
                                            Text.translatable(role.getTranslationKey()).formatted(getRoleFormatting(role)))
                                            .formatted(Formatting.GREEN), true);
                                    return 1;
                                })
                            )
                        )
                    )

                    .then(literal("clearrole")
                        .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                MinecraftServer s = ctx.getSource().getServer();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = s.getPlayerManager().getPlayer(playerName);
                                if (target == null) {
                                    ctx.getSource().sendError(Text.translatable("senseswap.command.player_not_found", playerName));
                                    return 0;
                                }
                                playerRoles.remove(target.getUuid());
                                RoleNetworking.sendRoleClearToClient(target);
                                RoleManager.saveRoles(s, playerRoles);
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.role_cleared", target.getName())
                                        .formatted(Formatting.YELLOW), true);
                                return 1;
                            })
                        )
                    )

                    .then(literal("status")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.status_header").formatted(Formatting.GOLD), false);

                            if (playerRoles.isEmpty()) {
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.no_roles").formatted(Formatting.GRAY), false);
                            } else {
                                for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
                                    ServerPlayerEntity p = s.getPlayerManager().getPlayer(entry.getKey());
                                    String name = p != null ? p.getName().getString() : entry.getKey().toString();
                                    Role role = entry.getValue();
                                    ctx.getSource().sendFeedback(() ->
                                        Text.literal("  " + name + " → ")
                                            .append(Text.translatable(role.getTranslationKey())
                                                .formatted(getRoleFormatting(role))), false);
                                }
                            }

                            PhaseManager pm = PhaseManager.getInstance();
                            if (pm != null) {
                                long ticks = pm.getTicksRemaining();
                                long mins = (ticks / 20) / 60;
                                long secs = (ticks / 20) % 60;
                                String phaseName = pm.getCurrentPhase() == PhaseManager.Phase.GAME
                                    ? "GAME" : "REST";
                                ctx.getSource().sendFeedback(() ->
                                    Text.literal("  Фаза: " + phaseName + " | Осталось: " + mins + ":" + String.format("%02d", secs)
                                        + " | Раунд: " + pm.getRoundNumber())
                                        .formatted(Formatting.AQUA), false);
                            } else if (gameRunning && sessionStartTime > 0) {
                                long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
                                long minutes = elapsed / 60, seconds = elapsed % 60;
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.session_time", minutes, seconds)
                                        .formatted(Formatting.AQUA), false);
                            }
                            return 1;
                        })
                    )

                    .then(literal("list").executes(ctx -> {
                        MinecraftServer s = ctx.getSource().getServer();
                        if (playerRoles.isEmpty()) {
                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.no_roles").formatted(Formatting.GRAY), false);
                            return 1;
                        }
                        for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
                            ServerPlayerEntity p = s.getPlayerManager().getPlayer(entry.getKey());
                            String name = p != null ? p.getName().getString() : entry.getKey().toString();
                            Role role = entry.getValue();
                            ctx.getSource().sendFeedback(() ->
                                Text.literal("  " + name + " → ")
                                    .append(Text.translatable(role.getTranslationKey())
                                        .formatted(getRoleFormatting(role))), false);
                        }
                        return 1;
                    }))

                    .then(literal("reload").executes(ctx -> {
                        MinecraftServer s = ctx.getSource().getServer();
                        playerRoles.clear();
                        RoleManager.loadRoles(s, playerRoles);
                        broadcastRoles(s);
                        ctx.getSource().sendFeedback(() ->
                            Text.translatable("senseswap.command.reloaded").formatted(Formatting.GREEN), true);
                        return 1;
                    }))

                    // ── NEW 4.0: /ss score — show leaderboard ─────────────────────────
                    .then(literal("score")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.score_header").formatted(Formatting.GOLD), false);

                            List<Map.Entry<UUID, Integer>> board = ScoreManager.getLeaderboard();
                            if (board.isEmpty()) {
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.score_empty").formatted(Formatting.GRAY), false);
                            } else {
                                int[] rank = {1};
                                for (Map.Entry<UUID, Integer> entry : board) {
                                    String name = ScoreManager.getPlayerName(entry.getKey());
                                    int pts = entry.getValue();
                                    String medal = rank[0] == 1 ? "🥇" : rank[0] == 2 ? "🥈" : rank[0] == 3 ? "🥉" : "  ";
                                    ctx.getSource().sendFeedback(() ->
                                        Text.literal(medal + " " + rank[0] + ". " + name + " — " + pts + " pts")
                                            .formatted(rank[0] == 1 ? Formatting.YELLOW : Formatting.WHITE), false);
                                    rank[0]++;
                                }
                            }
                            return 1;
                        })
                    )

                    // ── NEW 4.0: /ss score reset ──────────────────────────────────────
                    .then(literal("score")
                        .then(literal("reset")
                            .executes(ctx -> {
                                MinecraftServer s = ctx.getSource().getServer();
                                ScoreManager.resetScores(s);
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.score_reset").formatted(Formatting.RED), true);
                                return 1;
                            })
                        )
                    )

                    // ── NEW 4.0: /ss score add <player> <points> ──────────────────────
                    .then(literal("score")
                        .then(literal("add")
                            .then(argument("player", StringArgumentType.word())
                                .then(argument("points", IntegerArgumentType.integer())
                                    .executes(ctx -> {
                                        MinecraftServer s = ctx.getSource().getServer();
                                        String playerName = StringArgumentType.getString(ctx, "player");
                                        int pts = IntegerArgumentType.getInteger(ctx, "points");
                                        ServerPlayerEntity target = s.getPlayerManager().getPlayer(playerName);
                                        if (target == null) {
                                            ctx.getSource().sendError(Text.translatable("senseswap.command.player_not_found", playerName));
                                            return 0;
                                        }
                                        ScoreManager.addPoints(target.getUuid(), target.getName().getString(), pts);
                                        ScoreManager.saveScores(s);
                                        ctx.getSource().sendFeedback(() ->
                                            Text.translatable("senseswap.command.score_added", target.getName(), pts)
                                                .formatted(Formatting.GREEN), true);
                                        return 1;
                                    })
                                )
                            )
                        )
                    )
            );
        });

        System.out.println("[SenseSwap] v" + VERSION + " initialized (server-authoritative, phase cycle, scores, dizzy role).");
    }

    public static Role getRoleForPlayer(UUID uuid) { return playerRoles.get(uuid); }
    public static boolean isGameRunning() { return gameRunning; }
    public static Map<UUID, Role> getPlayerRoles() { return playerRoles; }

    private static void broadcastRoles(MinecraftServer s) {
        for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            if (playerRoles.containsKey(uuid)) {
                RoleNetworking.sendRoleToClient(player, playerRoles.get(uuid));
            } else {
                RoleNetworking.sendRoleClearToClient(player);
            }
        }
    }

    public static Formatting getRoleFormatting(Role role) {
        return switch (role) {
            case BLIND -> Formatting.RED;
            case DEAF  -> Formatting.BLUE;
            case MUTE  -> Formatting.GREEN;
            case DIZZY -> Formatting.LIGHT_PURPLE;  // NEW
        };
    }
}
