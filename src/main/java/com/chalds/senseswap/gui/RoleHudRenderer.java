package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapClientMod;
import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.config.ModConfig;
import com.chalds.senseswap.server.PhaseManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

public class RoleHudRenderer {

    public static void render(DrawContext context, RenderTickCounter tickDelta) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.hudEnabled) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // ── 1. Полоска таймера сверху ─────────────────────────────────────────
        renderTimerBar(context, mc, screenW);

        // ── 2. HUD роли (существующая логика) ────────────────────────────────
        SenseSwapMod.Role role = SenseSwapClientMod.currentRole;
        if (role == null) return;

        String roleLabel = Text.translatable(role.getTranslationKey()).getString();
        int roleColor = switch (role) {
            case BLIND -> 0xFFFF5555;
            case DEAF  -> 0xFF5555FF;
            case MUTE  -> 0xFF55FF55;
        };
        int bgColor = switch (role) {
            case BLIND -> 0xAA550000;
            case DEAF  -> 0xAA000055;
            case MUTE  -> 0xAA005500;
        };

        float scale = cfg.hudScale;
        int textW = (int)(mc.textRenderer.getWidth(roleLabel) * scale) + 8;
        int textH = (int)(mc.textRenderer.fontHeight * scale) + 6;

        // Смещаем вниз если есть полоска таймера (высота полоски = 6px + отступ)
        int barOffset = SenseSwapClientMod.currentPhase != null ? 10 : 0;

        int x, y;
        switch (cfg.hudPosition) {
            case TOP_LEFT     -> { x = 4;                   y = 4 + barOffset; }
            case TOP_RIGHT    -> { x = screenW - textW - 4; y = 4 + barOffset; }
            case BOTTOM_LEFT  -> { x = 4;                   y = screenH - textH - 4; }
            case BOTTOM_RIGHT -> { x = screenW - textW - 4; y = screenH - textH - 4; }
            default           -> { x = 4;                   y = 4 + barOffset; }
        }

        context.fill(x, y, x + textW, y + textH, bgColor);
        context.drawBorder(x, y, textW, textH, roleColor);

        context.getMatrices().push();
        context.getMatrices().translate(x + 4, y + 3, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(roleLabel), 0, 0, roleColor);
        context.getMatrices().pop();

        // ── Эффект BLIND: чёрный экран + только хотбар ───────────────────────
        if (role == SenseSwapMod.Role.BLIND) {
            context.fill(0, 0, screenW, screenH, 0xFF000000);
            int slotSize = 16, padding = 3, slots = 9;
            int hotbarW = slots * (slotSize + padding * 2) + 2;
            int hotbarH = slotSize + padding * 2 + 2;
            int hotbarX = (screenW - hotbarW) / 2;
            int hotbarY = screenH - hotbarH - 2;
            context.fill(hotbarX - 1, hotbarY - 1, hotbarX + hotbarW + 1, hotbarY + hotbarH + 1, 0xFF222222);
            net.minecraft.entity.player.PlayerInventory inv = mc.player.getInventory();
            for (int i = 0; i < slots; i++) {
                int slotX = hotbarX + i * (slotSize + padding * 2) + padding;
                int slotY = hotbarY + padding;
                context.fill(slotX - 1, slotY - 1, slotX + slotSize + 1, slotY + slotSize + 1, 0xFF333333);
                net.minecraft.item.ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, slotX, slotY);
                    context.drawItemInSlot(mc.textRenderer, stack, slotX, slotY);
                }
            }
            int sel = inv.selectedSlot;
            int selX = hotbarX + sel * (slotSize + padding * 2) + padding - 2;
            int selY = hotbarY + padding - 2;
            context.drawBorder(selX, selY, slotSize + 4, slotSize + 4, 0xFFFFFFFF);

            // Перерисовать полоску поверх чёрного экрана (BLIND её не скрывает)
            renderTimerBar(context, mc, screenW);
        }
    }

    // ── Полоска таймера ───────────────────────────────────────────────────────
    private static void renderTimerBar(DrawContext context, MinecraftClient mc, int screenW) {
        PhaseManager.Phase phase = SenseSwapClientMod.currentPhase;
        if (phase == null) return;

        long ticksLeft = SenseSwapClientMod.phaseTicksRemaining;
        long total = phase == PhaseManager.Phase.GAME
            ? PhaseManager.getGameTicks()
            : PhaseManager.getRestTicks();

        // Прогресс: 1.0 = полная, 0.0 = пустая
        float progress = Math.max(0f, Math.min(1f, ticksLeft / (float) total));

        // ── Цвета полоски ─────────────────────────────────────────────────────
        // GAME: синий → жёлтый (последние 5 сек → красный)
        // REST: бирюзовый → зелёный
        float fade = SenseSwapClientMod.fadeFraction;  // 0..1 в конце фазы

        int barColor;
        int glowColor;

        if (phase == PhaseManager.Phase.GAME) {
            // Синий → красный по мере уменьшения времени
            // Сначала чисто синий, в конце — краснеет
            int r = (int) lerp(0x33, 0xFF, 1f - progress);
            int g = (int) lerp(0xAA, 0x22, 1f - progress);
            int b = (int) lerp(0xFF, 0x22, 1f - progress);
            // В fade (последние 5 сек) — мигание: добавляем яркость
            float pulse = fade > 0 ? (float)(0.5 + 0.5 * Math.sin(ticksLeft * 0.3)) : 0f;
            r = Math.min(255, r + (int)(pulse * 80));
            barColor  = 0xFF000000 | (r << 16) | (g << 8) | b;
            glowColor = 0x44000000 | (r << 16) | (g << 8) | b;
        } else {
            // REST: бирюзовый, спокойный
            int r = (int) lerp(0x00, 0x44, fade);
            int g = (int) lerp(0xCC, 0xFF, fade);
            int b = (int) lerp(0xCC, 0x88, fade);
            barColor  = 0xFF000000 | (r << 16) | (g << 8) | b;
            glowColor = 0x44000000 | (r << 16) | (g << 8) | b;
        }

        int BAR_H = 4;   // высота полоски в пикселях
        int barW = (int)(screenW * progress);

        // Тёмный фон под полоской
        context.fill(0, 0, screenW, BAR_H, 0xAA000000);

        // Свечение снизу (широкая полупрозрачная полоса)
        context.fill(0, BAR_H, barW, BAR_H + 3, glowColor);

        // Сама полоска
        context.fill(0, 0, barW, BAR_H, barColor);

        // Текст с временем: MM:SS справа от полоски (или у правого края)
        long totalSecs = ticksLeft / 20;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        String timeText = String.format("%d:%02d", mins, secs);

        String phaseLabel = phase == PhaseManager.Phase.GAME
            ? Text.translatable("senseswap.phase.label_game").getString()
            : Text.translatable("senseswap.phase.label_rest").getString();

        String label = phaseLabel + "  " + timeText;
        int textColor = phase == PhaseManager.Phase.GAME ? 0xFFAADDFF : 0xFF88FFEE;

        // Рисуем текст по центру сверху
        int textX = screenW / 2 - mc.textRenderer.getWidth(label) / 2;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(label), textX, 6, textColor);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
