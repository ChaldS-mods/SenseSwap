package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.SenseSwapClientMod;
import com.chalds.senseswap.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RoleHudRenderer {

    public static void render(DrawContext context, RenderTickCounter tickDelta) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.hudEnabled) return;

        SenseSwapMod.Role role = SenseSwapClientMod.currentRole;
        if (role == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        String roleLabel = Text.translatable(role.getTranslationKey()).getString();
        int roleColor = switch (role) {
            case BLIND -> 0xFF5555FF; // red-ish
            case DEAF  -> 0xFF5555FF; // blue
            case MUTE  -> 0xFF55FF55; // green
        };
        int bgColor = switch (role) {
            case BLIND -> 0xAA550000;
            case DEAF  -> 0xAA000055;
            case MUTE  -> 0xAA005500;
        };

        float scale = cfg.hudScale;
        int textW = (int)(mc.textRenderer.getWidth(roleLabel) * scale) + 8;
        int textH = (int)(mc.textRenderer.fontHeight * scale) + 6;

        int x, y;
        switch (cfg.hudPosition) {
            case TOP_LEFT     -> { x = 4;                    y = 4; }
            case TOP_RIGHT    -> { x = screenW - textW - 4;  y = 4; }
            case BOTTOM_LEFT  -> { x = 4;                    y = screenH - textH - 4; }
            case BOTTOM_RIGHT -> { x = screenW - textW - 4;  y = screenH - textH - 4; }
            default           -> { x = 4;                    y = 4; }
        }

        // Background box
        context.fill(x, y, x + textW, y + textH, bgColor);
        context.drawBorder(x, y, textW, textH, roleColor);

        // Text with scale
        context.getMatrices().push();
        context.getMatrices().translate(x + 4, y + 3, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTextWithShadow(mc.textRenderer, Text.literal(roleLabel), 0, 0, roleColor);
        context.getMatrices().pop();

        // Blind effect: dark overlay
        if (role == SenseSwapMod.Role.BLIND) {
            context.fill(0, 0, screenW, screenH, 0xFF000000);
            // Draw hotbar background
            int slotSize = 16;
            int padding = 3;
            int slots = 9;
            int hotbarW = slots * (slotSize + padding * 2) + 2;
            int hotbarH = slotSize + padding * 2 + 2;
            int hotbarX = (screenW - hotbarW) / 2;
            int hotbarY = screenH - hotbarH - 2;
            context.fill(hotbarX - 1, hotbarY - 1, hotbarX + hotbarW + 1, hotbarY + hotbarH + 1, 0xFF222222);
            // Draw items in hotbar slots
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
            // Highlight selected slot
            int sel = inv.selectedSlot;
            int selX = hotbarX + sel * (slotSize + padding * 2) + padding - 2;
            int selY = hotbarY + padding - 2;
            context.drawBorder(selX, selY, slotSize + 4, slotSize + 4, 0xFFFFFFFF);
        }
    }
}
