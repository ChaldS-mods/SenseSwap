package com.chalds.senseswap;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.UUID;

/**
 * Handles Simple Voice Chat integration.
 *
 * Version 3.0 changes:
 *   - MUTE check on the microphone event now uses the SERVER-SIDE role map
 *     (SenseSwapMod.getRoleForPlayer) rather than the client field.
 *     This means the server is the authority — a modified client cannot bypass muting.
 *   - DEAF still uses the client field (ClientReceiveSoundEvent fires client-side only).
 */
@ForgeVoicechatPlugin
public class SenseSwapVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return SenseSwapMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        // SERVER-SIDE: Block outgoing audio for MUTE players
        // The event fires on the server when it receives a mic packet from a client.
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);

        // CLIENT-SIDE: Block incoming audio for DEAF players
        registration.registerEvent(ClientReceiveSoundEvent.class, this::onClientReceiveSound);
    }

    /**
     * Server-side microphone check.
     * Role is fetched from the authoritative server map, so clients cannot cheat.
     */
    private void onMicrophonePacket(MicrophonePacketEvent event) {
        if (event.getSenderConnection() == null) return;

        UUID senderUuid = event.getSenderConnection().getPlayer().getUuid();
        SenseSwapMod.Role role = SenseSwapMod.getRoleForPlayer(senderUuid);

        if (role == SenseSwapMod.Role.MUTE) {
            event.cancel();
        }
    }

    /**
     * Client-side receive sound check.
     * DEAF players don't hear voice chat.
     */
    @Environment(EnvType.CLIENT)
    private void onClientReceiveSound(ClientReceiveSoundEvent event) {
        SenseSwapMod.Role role = SenseSwapClientMod.currentRole;
        if (role == SenseSwapMod.Role.DEAF) {
            event.cancel();
        }
    }
}
