package com.chalds.senseswap;

import com.chalds.senseswap.network.RoleNetworking;
import com.chalds.senseswap.server.PhaseManager;
import com.chalds.senseswap.server.RoleManager;
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

public class SenseSwapMod implements ModInitializer {

    public static final String MOD_ID = "senseswap";
    public static final String VERSION = "3.5.0-beta";

    private static final Map<UUID, Role> playerRoles = new HashMap<>();
    private static boolean gameRunning = false;
    private static MinecraftServer server;
    private static long sessionStartTime = 0;

    public enum Role {
        BLIND("senseswap.role.blind"),
        DEAF("senseswap.role.deaf"),
        MUTE("senseswap.role.mute");

        private final String translationKey;
        Role(String translationKey) { this.translationKey = translationKey; }
        public String getTranslationKey() { return translationKey; }
    }

    @Override
    public void onInitialize() {
        RoleNetworking.registerServer();

        ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
            server = srv;
            RoleManager.loadRoles(srv, playerRoles);
            System.out.println("[SenseSwap] v" + VERSION + " Server initialized.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            RoleManager.saveRoles(srv, playerRoles);
            PhaseManager.stop();
        });

        // ── Серверный тик: передаём PhaseManager ──────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(srv -> {
            PhaseManager pm = PhaseManager.getInstance();
            if (pm != null) {
                pm.tick(srv, playerRoles);
            }
        });

        // При входе игрока — восстановить роль и отправить текущую фазу
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

            // Отправить текущую фазу если игра идёт
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

                            if (players.size() < 3) {
                                ctx.getSource().sendError(Text.translatable("senseswap.command.need_players"));
                                return 0;
                            }

                            Collections.shuffle(players);
                            playerRoles.clear();
                            Role[] roles = Role.values();
                            for (int i = 0; i < roles.length && i < players.size(); i++) {
                                playerRoles.put(players.get(i).getUuid(), roles[i]);
                            }

                            gameRunning = true;
                            sessionStartTime = System.currentTimeMillis();

                            // Запускаем цикл фаз если включён в настройках
                            PhaseManager pm = null;
                            if (com.chalds.senseswap.config.ModConfig.get().phaseCycleEnabled) {
                                pm = PhaseManager.start();
                            }

                            broadcastRoles(s);
                            // Сразу отправить фазу всем (если цикл включён)
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

                            // Показать фазу и время
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
                        // Алиас для status
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
            );
        });

        System.out.println("[SenseSwap] v" + VERSION + " initialized (server-authoritative, phase cycle).");
    }

    public static Role getRoleForPlayer(UUID uuid) { return playerRoles.get(uuid); }
    public static boolean isGameRunning() { return gameRunning; }

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
