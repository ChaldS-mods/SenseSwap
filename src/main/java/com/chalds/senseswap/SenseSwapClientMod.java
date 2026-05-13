package com.chalds.senseswap;

import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.gui.RoleHudRenderer;
import com.chalds.senseswap.gui.RolePopupScreen;
import com.chalds.senseswap.gui.SettingsScreen;
import com.chalds.senseswap.network.RoleNetworking;
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

@Environment(EnvType.CLIENT)
public class SenseSwapClientMod implements ClientModInitializer {

    /** Текущая роль клиента. null = нет роли. Всегда приходит с сервера. */
    public static SenseSwapMod.Role currentRole = null;

    /** Текущая фаза (GAME / REST). null = игра не идёт. */
    public static PhaseManager.Phase currentPhase = null;

    /** Оставшиеся тики текущей фазы — для рисования полоски. */
    public static long phaseTicksRemaining = 0;

    /**
     * Прогресс плавного перехода цвета полоски (0.0 → 1.0 за последние 5 сек фазы).
     * Обновляется каждый клиентский тик на основе phaseTicksRemaining.
     */
    public static float fadeFraction = 0f;

    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        ModConfig.load();

        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "senseswap.key.open_settings",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "senseswap.key.category"
        ));

        // Пакет: назначить роль
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

        // Пакет: убрать роль
        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.RoleClearPayload.ID,
            (payload, context) -> context.client().execute(() -> currentRole = null)
        );

        // Пакет: обновление фазы и таймера
        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.PhasePayload.ID,
            (payload, context) -> context.client().execute(() -> {
                currentPhase = payload.phase();
                phaseTicksRemaining = payload.ticksRemaining();
            })
        );

        // Пакет: игра остановлена — убрать таймер
        ClientPlayNetworking.registerGlobalReceiver(
            RoleNetworking.PhaseStopPayload.ID,
            (payload, context) -> context.client().execute(() -> {
                currentPhase = null;
                phaseTicksRemaining = 0;
                fadeFraction = 0f;
            })
        );

        // HUD
        HudRenderCallback.EVENT.register((context, tickDelta) ->
            RoleHudRenderer.render(context, tickDelta));

        // Тик: декрементируем phaseTicksRemaining на клиенте (плавность между пакетами)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (currentPhase != null && phaseTicksRemaining > 0) {
                phaseTicksRemaining--;

                // Считаем fade: последние 100 тиков (5 сек) → плавное изменение
                long total = currentPhase == PhaseManager.Phase.GAME
                    ? PhaseManager.getGameTicks()
                    : PhaseManager.getRestTicks();
                long fadeTicks = 100L;
                if (phaseTicksRemaining < fadeTicks) {
                    fadeFraction = 1f - (phaseTicksRemaining / (float) fadeTicks);
                } else {
                    fadeFraction = 0f;
                }
            }

            while (settingsKey.wasPressed()) {
                client.setScreen(new SettingsScreen(client.currentScreen));
            }
        });

        System.out.println("[SenseSwap] v" + SenseSwapMod.VERSION + " Client initialized.");
    }
}
