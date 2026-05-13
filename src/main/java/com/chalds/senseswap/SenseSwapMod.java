package com.chalds.senseswap;

import com.chalds.senseswap.network.RoleNetworking;
import com.chalds.senseswap.server.RoleManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import com.mojang.brigadier.arguments.StringArgumentType;

public class SenseSwapMod implements ModInitializer {

    public static final String MOD_ID = "senseswap";
    public static final String VERSION = "3.0.0";

    // Server-side: UUID -> Role  (single source of truth)
    private static final Map<UUID, Role> playerRoles = new HashMap<>();
    private static boolean gameRunning = false;
    private static MinecraftServer server;

    // Session timer (bonus feature)
    private static long sessionStartTime = 0;

    public enum Role {
        BLIND("senseswap.role.blind"),
        DEAF("senseswap.role.deaf"),
        MUTE("senseswap.role.mute");

        private final String translationKey;

        Role(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getTranslationKey() {
            return translationKey;
        }
    }

    @Override
    public void onInitialize() {
        // Register network packets
        RoleNetworking.registerServer();

        // Save/load roles when server starts/stops
        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            RoleManager.loadRoles(srv, playerRoles);
            System.out.println("[SenseSwap] v" + VERSION + " Server initialized. Loaded roles from world save.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            RoleManager.saveRoles(srv, playerRoles);
            System.out.println("[SenseSwap] Roles saved to world directory.");
        });

        // === PLAYER JOIN: restore their role ===
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {
            ServerPlayerEntity player = handler.player;
            UUID uuid = player.getUuid();

            if (playerRoles.containsKey(uuid)) {
                Role role = playerRoles.get(uuid);
                // Send their restored role after a short delay (ensure client is ready)
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
        });

        // === PLAYER LEAVE: save roles to disk ===
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            RoleManager.saveRoles(srv, playerRoles);
        });

        // Register commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                literal("ss")
                    .requires(source -> source.hasPermissionLevel(2))

                    // /ss start — assign roles, save, broadcast
                    .then(literal("start")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            List<ServerPlayerEntity> players = new ArrayList<>(s.getPlayerManager().getPlayerList());

                            if (players.size() < 3) {
                                ctx.getSource().sendError(Text.translatable("senseswap.command.need_players"));
                                return 0;
                            }

                            // Shuffle and assign one of each role (remaining players get no role)
                            Collections.shuffle(players);
                            playerRoles.clear();

                            Role[] roles = Role.values();
                            for (int i = 0; i < roles.length && i < players.size(); i++) {
                                UUID uuid = players.get(i).getUuid();
                                playerRoles.put(uuid, roles[i]);
                            }

                            // Clear roles for players not assigned
                            for (int i = roles.length; i < players.size(); i++) {
                                playerRoles.remove(players.get(i).getUuid());
                            }

                            gameRunning = true;
                            sessionStartTime = System.currentTimeMillis();

                            // Send roles to all players
                            broadcastRoles(s);

                            // Save to world
                            RoleManager.saveRoles(s, playerRoles);

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.started").formatted(Formatting.GREEN), true);
                            return 1;
                        })
                    )

                    // /ss stop — clear all roles
                    .then(literal("stop")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            playerRoles.clear();
                            gameRunning = false;
                            sessionStartTime = 0;

                            // Notify all clients to clear their role
                            for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList()) {
                                RoleNetworking.sendRoleClearToClient(player);
                            }

                            RoleManager.saveRoles(s, playerRoles);

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.stopped").formatted(Formatting.RED), true);
                            return 1;
                        })
                    )

                    // /ss setrole <player> <BLIND|DEAF|MUTE>
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
                                    try {
                                        role = Role.valueOf(roleName);
                                    } catch (IllegalArgumentException e) {
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

                    // /ss clearrole <player>
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

                    // /ss status — show all current roles
                    .then(literal("status")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();

                            if (playerRoles.isEmpty()) {
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.no_roles").formatted(Formatting.GRAY), false);
                                return 1;
                            }

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.status_header").formatted(Formatting.GOLD), false);

                            for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
                                ServerPlayerEntity player = s.getPlayerManager().getPlayer(entry.getKey());
                                String name = player != null ? player.getName().getString() : entry.getKey().toString();
                                Role role = entry.getValue();
                                ctx.getSource().sendFeedback(() ->
                                    Text.literal("  " + name + " → ")
                                        .append(Text.translatable(role.getTranslationKey()).formatted(getRoleFormatting(role))),
                                    false);
                            }

                            // Show session duration (bonus)
                            if (gameRunning && sessionStartTime > 0) {
                                long elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000;
                                long minutes = elapsed / 60;
                                long seconds = elapsed % 60;
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.session_time", minutes, seconds)
                                        .formatted(Formatting.AQUA), false);
                            }

                            return 1;
                        })
                    )

                    // /ss list — alias for status (bonus)
                    .then(literal("list")
                        .executes(ctx -> {
                            // Reuse status logic
                            MinecraftServer s = ctx.getSource().getServer();
                            if (playerRoles.isEmpty()) {
                                ctx.getSource().sendFeedback(() ->
                                    Text.translatable("senseswap.command.no_roles").formatted(Formatting.GRAY), false);
                                return 1;
                            }
                            for (Map.Entry<UUID, Role> entry : playerRoles.entrySet()) {
                                ServerPlayerEntity player = s.getPlayerManager().getPlayer(entry.getKey());
                                String name = player != null ? player.getName().getString() : entry.getKey().toString();
                                Role role = entry.getValue();
                                ctx.getSource().sendFeedback(() ->
                                    Text.literal("  " + name + " → ")
                                        .append(Text.translatable(role.getTranslationKey()).formatted(getRoleFormatting(role))),
                                    false);
                            }
                            return 1;
                        })
                    )

                    // /ss reload — reload roles from world save (bonus)
                    .then(literal("reload")
                        .executes(ctx -> {
                            MinecraftServer s = ctx.getSource().getServer();
                            playerRoles.clear();
                            RoleManager.loadRoles(s, playerRoles);

                            // Re-send roles to all online players
                            broadcastRoles(s);

                            ctx.getSource().sendFeedback(() ->
                                Text.translatable("senseswap.command.reloaded").formatted(Formatting.GREEN), true);
                            return 1;
                        })
                    )
            );
        });

        System.out.println("[SenseSwap] v" + VERSION + " initialized (server-authoritative mode).");
    }

    // ===== Helpers =====

    public static Role getRoleForPlayer(UUID uuid) {
        return playerRoles.get(uuid);
    }

    public static boolean isGameRunning() {
        return gameRunning;
    }

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
        };
    }
}
