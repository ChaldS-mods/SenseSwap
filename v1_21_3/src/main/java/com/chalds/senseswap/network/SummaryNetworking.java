package com.chalds.senseswap.network;

import com.chalds.senseswap.SenseSwapMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class SummaryNetworking {

    public record SummaryEntry(String name, SenseSwapMod.Role role, int score, int delta) {}

    public record SummaryPayload(int round, List<SummaryEntry> entries) implements CustomPayload {
        public static final CustomPayload.Id<SummaryPayload> ID =
            new CustomPayload.Id<>(Identifier.of("senseswap", "summary"));
        public static final PacketCodec<PacketByteBuf, SummaryPayload> CODEC = PacketCodec.of(
            (val, buf) -> {
                buf.writeInt(val.round());
                buf.writeInt(val.entries().size());
                for (SummaryEntry e : val.entries()) {
                    buf.writeString(e.name());
                    buf.writeEnumConstant(e.role());
                    buf.writeInt(e.score());
                    buf.writeInt(e.delta());
                }
            },
            buf -> {
                int round = buf.readInt();
                int sz = buf.readInt();
                List<SummaryEntry> list = new ArrayList<>(sz);
                for (int i = 0; i < sz; i++) {
                    list.add(new SummaryEntry(
                        buf.readString(),
                        buf.readEnumConstant(SenseSwapMod.Role.class),
                        buf.readInt(),
                        buf.readInt()
                    ));
                }
                return new SummaryPayload(round, list);
            }
        );
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(SummaryPayload.ID, SummaryPayload.CODEC);
    }

    public static void sendSummary(ServerPlayerEntity player, int round, List<SummaryEntry> entries) {
        ServerPlayNetworking.send(player, new SummaryPayload(round, entries));
    }
}
