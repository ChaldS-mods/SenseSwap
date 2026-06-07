package com.chalds.senseswap;

import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.gui.*;
import com.chalds.senseswap.network.RoleNetworking;
import com.chalds.senseswap.network.SummaryNetworking;
import com.chalds.senseswap.server.PhaseManager;
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

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class SenseSwapClientMod implements ClientModInitializer {

    public static SenseSwapMod.Role currentRole = null;
    public static PhaseManager.Phase currentPhase = null;
    public static long phaseTicksRemaining = 0;
    public static float fadeFraction = 0f;

    private static KeyBinding settingsKey;
    private static KeyBinding duoKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "senseswap.key.open_settings",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "senseswap.key.category"));

        duoKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "senseswap.key.duo_setup",
            InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "senseswap.key.category"));

        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.RolePayload.ID,
            (payload, context) -> {
                SenseSwapMod.Role role = payload.role();
                context.client().execute(() -> {
                    currentRole = role;
                    if (role != null && ModConfig.get().showRolePopup) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (ModConfig.get().useWheelAnimation) {
                            mc.setScreen(new WheelSpinScreen(role, mc.currentScreen));
                        } else {
                            mc.setScreen(new RolePopupScreen(role, mc.currentScreen));
                        }
                    }
                });
            }
        );

        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.RoleClearPayload.ID,
            (payload, context) -> context.client().execute(() -> currentRole = null));

        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.PhasePayload.ID,
            (payload, context) -> context.client().execute(() -> {
                currentPhase = payload.phase();
                phaseTicksRemaining = payload.ticksRemaining();
            }));

        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.PhaseStopPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                currentPhase = null;
                phaseTicksRemaining = 0;
                fadeFraction = 0f;
            }));

        ClientPlayNetworking.registerGlobalReceiver(
            SummaryNetworking.SummaryPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                List<DaySummaryScreen.PlayerEntry> entries = new ArrayList<>();
                for (SummaryNetworking.SummaryEntry e : payload.entries()) {
                    entries.add(new DaySummaryScreen.PlayerEntry(
                        e.name(), e.role(), e.score(), e.delta()));
                }
                mc.setScreen(new DaySummaryScreen(payload.round(), entries, mc.currentScreen));
            }));

        HudRenderCallback.EVENT.register((context, tickDelta) ->
            RoleHudRenderer.render(context, tickDelta));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (currentPhase != null && phaseTicksRemaining > 0) {
                phaseTicksRemaining--;
                long total = currentPhase == PhaseManager.Phase.GAME
                    ? PhaseManager.getGameTicks() : PhaseManager.getRestTicks();
                long fadeTicks = 100L;
                fadeFraction = phaseTicksRemaining < fadeTicks
                    ? 1f - (phaseTicksRemaining / (float) fadeTicks) : 0f;
            }

            while (settingsKey.wasPressed()) {
                client.setScreen(new SettingsScreen(client.currentScreen));
            }
            while (duoKey.wasPressed()) {
                client.setScreen(new DuoSetupScreen(client.currentScreen));
            }
        });

        System.out.println("[SenseSwap] v" + SenseSwapMod.VERSION + " Client v5 initialized.");
    }
}
