package com.chalds.senseswap;

import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.gui.RoleHudRenderer;
import com.chalds.senseswap.gui.RolePopupScreen;
import com.chalds.senseswap.gui.SettingsScreen;
import com.chalds.senseswap.network.RoleNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class SenseSwapClientMod implements ClientModInitializer {

    /**
     * The client's current role. Null = no role assigned.
     * This value is ALWAYS set by the server via network packet — never locally.
     * On disconnect it is reset to null automatically.
     */
    public static SenseSwapMod.Role currentRole = null;

    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        // Register settings keybinding (K by default)
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "senseswap.key.open_settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "senseswap.key.category"
        ));

        // === Packet: server assigned a role ===
        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.RolePayload.ID,
            (payload, context) -> {
                SenseSwapMod.Role role = payload.role();
                context.client().execute(() -> {
                    currentRole = role;
                    if (role != null && ModConfig.get().showRolePopup) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        mc.setScreen(new RolePopupScreen(role, mc.currentScreen));
                    }
                });
            }
        );

        // === Packet: server cleared this player's role ===
        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.RoleClearPayload.ID,
            (payload, context) -> {
                context.client().execute(() -> {
                    currentRole = null;
                });
            }
        );

        // HUD renderer
        HudRenderCallback.EVENT.register((context, tickDelta) ->
            RoleHudRenderer.render(context, tickDelta));

        // Key tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.wasPressed()) {
                client.setScreen(new SettingsScreen(client.currentScreen));
            }
        });

        System.out.println("[SenseSwap] v" + SenseSwapMod.VERSION + " Client initialized.");
    }
}
