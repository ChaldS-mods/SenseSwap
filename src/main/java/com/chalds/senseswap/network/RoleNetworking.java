package com.chalds.senseswap.network;

import com.chalds.senseswap.SenseSwapMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

public class RoleNetworking {

    // Packet 1: Server → Client: assign a role
    public record RolePayload(SenseSwapMod.Role role) implements CustomPayload {

        public static final CustomPayload.Id<RolePayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "role"));

        public static final PacketCodec<PacketByteBuf, RolePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeEnumConstant(value.role),
            buf -> new RolePayload(buf.readEnumConstant(SenseSwapMod.Role.class))
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // Packet 2: Server → Client: clear the player's role (no data needed)
    public record RoleClearPayload() implements CustomPayload {

        public static final CustomPayload.Id<RoleClearPayload> ID =
            new CustomPayload.Id<>(Identifier.of(SenseSwapMod.MOD_ID, "role_clear"));

        public static final PacketCodec<PacketByteBuf, RoleClearPayload> CODEC = PacketCodec.of(
            (value, buf) -> { /* nothing to write */ },
            buf -> new RoleClearPayload()
        );

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(RolePayload.ID, RolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RoleClearPayload.ID, RoleClearPayload.CODEC);
    }

    /** Send a role assignment to the client. */
    public static void sendRoleToClient(ServerPlayerEntity player, SenseSwapMod.Role role) {
        ServerPlayNetworking.send(player, new RolePayload(role));
    }

    /** Tell the client to remove their current role (used when /ss stop or clearrole). */
    public static void sendRoleClearToClient(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new RoleClearPayload());
    }
}
