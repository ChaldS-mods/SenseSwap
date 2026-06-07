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

        // ── 2. HUD роли ───────────────────────────────────────────────────────
        SenseSwapMod.Role role = SenseSwapClientMod.currentRole;
        if (role == null) return;

        String roleLabel = Text.translatable(role.getTranslationKey()).getString();
        int roleColor = switch (role) {
            case BLIND -> 0xFFFF5555;
            case DEAF  -> 0xFF5555FF;
            case MUTE  -> 0xFF55FF55;
            case DIZZY -> 0xFFFF55FF;  // NEW purple
        };
        int bgColor = switch (role) {
            case BLIND -> 0xAA550000;
            case DEAF  -> 0xAA000055;
            case MUTE  -> 0xAA005500;
            case DIZZY -> 0xAA550055;  // NEW
        };

        float scale = cfg.hudScale;
        int textW = (int)(mc.textRenderer.getWidth(roleLabel) * scale) + 8;
        int textH = (int)(mc.textRenderer.fontHeight * scale) + 6;

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
                    
                }
            }
            int sel = inv.selectedSlot;
            int selX = hotbarX + sel * (slotSize + padding * 2) + padding - 2;
            int selY = hotbarY + padding - 2;
            context.drawBorder(selX, selY, slotSize + 4, slotSize + 4, 0xFFFFFFFF);
            renderTimerBar(context, mc, screenW);
        }

        // ── NEW 4.0: Эффект DIZZY: пульсирующая рамка по краям экрана ─────────
        if (role == SenseSwapMod.Role.DIZZY) {
            long tick = SenseSwapClientMod.phaseTicksRemaining;
            float pulse = (float)(0.5 + 0.5 * Math.sin(tick * 0.15));
            int alpha = (int)(50 + 100 * pulse);
            int dizzyBorderColor = (alpha << 24) | 0xFF00FF;
            int borderThickness = 6;
            // Left
            context.fill(0, 0, borderThickness, screenH, dizzyBorderColor);
            // Right
            context.fill(screenW - borderThickness, 0, screenW, screenH, dizzyBorderColor);
            // Top
            context.fill(0, 0, screenW, borderThickness, dizzyBorderColor);
            // Bottom
            context.fill(0, screenH - borderThickness, screenW, screenH, dizzyBorderColor);
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

        float progress = Math.max(0f, Math.min(1f, ticksLeft / (float) total));

        float fade = SenseSwapClientMod.fadeFraction;

        int barColor;
        int glowColor;

        if (phase == PhaseManager.Phase.GAME) {
            int r = (int) lerp(0x33, 0xFF, 1f - progress);
            int g = (int) lerp(0xAA, 0x22, 1f - progress);
            int b = (int) lerp(0xFF, 0x22, 1f - progress);
            float pulse = fade > 0 ? (float)(0.5 + 0.5 * Math.sin(ticksLeft * 0.3)) : 0f;
            r = Math.min(255, r + (int)(pulse * 80));
            barColor  = 0xFF000000 | (r << 16) | (g << 8) | b;
            glowColor = 0x44000000 | (r << 16) | (g << 8) | b;
        } else {
            int r = (int) lerp(0x00, 0x44, fade);
            int g = (int) lerp(0xCC, 0xFF, fade);
            int b = (int) lerp(0xCC, 0x88, fade);
            barColor  = 0xFF000000 | (r << 16) | (g << 8) | b;
            glowColor = 0x44000000 | (r << 16) | (g << 8) | b;
        }

        int BAR_H = 4;
        int barW = (int)(screenW * progress);

        context.fill(0, 0, screenW, BAR_H, 0xAA000000);
        context.fill(0, BAR_H, barW, BAR_H + 3, glowColor);
        context.fill(0, 0, barW, BAR_H, barColor);

        long totalSecs = ticksLeft / 20;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        String timeText = String.format("%d:%02d", mins, secs);

        String phaseLabel = phase == PhaseManager.Phase.GAME
            ? Text.translatable("senseswap.phase.label_game").getString()
            : Text.translatable("senseswap.phase.label_rest").getString();

        String label = phaseLabel + "  " + timeText;
        int textColor = phase == PhaseManager.Phase.GAME ? 0xFFAADDFF : 0xFF88FFEE;

        int textX = screenW / 2 - mc.textRenderer.getWidth(label) / 2;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(label), textX, 6, textColor);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * Math.max(0f, Math.min(1f, t));
    }
}
