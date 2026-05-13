package com.chalds.senseswap.network;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.server.PhaseManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class RoleNetworking {

    // ── Пакет 1: Server→Client — назначить роль ───────────────────────────────
    public record RolePayload(SenseSwapMod.Role role) implements CustomPayload {
        public static final CustomPayload.Id<RolePayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "role"));
        public static final PacketCodec<PacketByteBuf, RolePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeEnumConstant(value.role),
            buf -> new RolePayload(buf.readEnumConstant(SenseSwapMod.Role.class))
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Пакет 2: Server→Client — убрать роль ─────────────────────────────────
    public record RoleClearPayload() implements CustomPayload {
        public static final CustomPayload.Id<RoleClearPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "role_clear"));
        public static final PacketCodec<PacketByteBuf, RoleClearPayload> CODEC = PacketCodec.of(
            (value, buf) -> {},
            buf -> new RoleClearPayload()
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Пакет 3: Server→Client — фаза и таймер (для HUD) ─────────────────────
    public record PhasePayload(PhaseManager.Phase phase, long ticksRemaining) implements CustomPayload {
        public static final CustomPayload.Id<PhasePayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "phase"));
        public static final PacketCodec<PacketByteBuf, PhasePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeEnumConstant(value.phase);
                buf.writeLong(value.ticksRemaining);
            },
            buf -> new PhasePayload(
                buf.readEnumConstant(PhaseManager.Phase.class),
                buf.readLong()
            )
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Пакет 4: Server→Client — игра остановлена, убрать таймер ─────────────
    public record PhaseStopPayload() implements CustomPayload {
        public static final CustomPayload.Id<PhaseStopPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "phase_stop"));
        public static final PacketCodec<PacketByteBuf, PhaseStopPayload> CODEC = PacketCodec.of(
            (value, buf) -> {},
            buf -> new PhaseStopPayload()
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoleClearPayload.ID, RoleClearPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhasePayload.ID, PhasePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PhaseStopPayload.ID, PhaseStopPayload.CODEC);
    }

    public static void sendRoleToClient(ServerPlayerEntity player, SenseSwapMod.Role role) {
        ServerPlayNetworking.send(player, new RolePayload(role));
    }

    public static void sendRoleClearToClient(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new RoleClearPayload());
    }

    public static void sendPhaseToClient(ServerPlayerEntity player,
                                          PhaseManager.Phase phase, long ticksRemaining) {
        ServerPlayNetworking.send(player, new PhasePayload(phase, ticksRemaining));
    }

    public static void sendPhaseStopToClient(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new PhaseStopPayload());
    }
}
