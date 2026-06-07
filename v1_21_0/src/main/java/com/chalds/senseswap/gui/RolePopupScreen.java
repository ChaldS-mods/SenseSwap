package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapMod;
import com.chalds.senseswap.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class RolePopupScreen extends Screen {

    private final SenseSwapMod.Role role;
    private final Screen parent;
    private int ticksRemaining;

    public RolePopupScreen(SenseSwapMod.Role role, Screen parent) {
        super(Text.translatable("senseswap.popup.title"));
        this.role = role;
        this.parent = parent;
        this.ticksRemaining = ModConfig.get().rolePopupDuration * 20;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("senseswap.gui.done"),
            btn -> this.client.setScreen(parent)
        ).dimensions(centerX - 50, centerY + 60, 100, 20).build());
    }

    @Override
    public void tick() {
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int boxW = 300;
        int boxH = 120;
        int boxX = centerX - boxW / 2;
        int boxY = centerY - boxH / 2;

        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xDD1A1A2E);
        context.drawBorder(boxX, boxY, boxW, boxH, 0xFFFFD700);

        int roleColor = switch (role) {
            case BLIND -> 0xFF5555; case DIZZY -> 0xCC55FF;
            case DEAF -> 0x5555FF;
            case MUTE -> 0x55FF55;
        };

        String roleStr = Text.translatable(role.getTranslationKey()).getString().toUpperCase();

        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("senseswap.popup.title").formatted(Formatting.WHITE),
            centerX, boxY + 15, 0xFFFFFF);

        context.getMatrices().push();
        context.getMatrices().translate(centerX, boxY + 45, 0);
        context.getMatrices().scale(2.5f, 2.5f, 1.0f);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(roleStr), 0, 0, roleColor);
        context.getMatrices().pop();

        String descKey = switch (role) {
            case BLIND -> "senseswap.popup.desc.blind";
            case DEAF  -> "senseswap.popup.desc.deaf";
            case MUTE  -> "senseswap.popup.desc.mute";
            case DIZZY -> "senseswap.popup.desc.dizzy";
        };
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(descKey).formatted(Formatting.GRAY),
            centerX, boxY + 90, 0xAAAAAA);

        int secs = ticksRemaining / 20 + 1;
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("senseswap.popup.closes", secs).formatted(Formatting.DARK_GRAY),
            centerX, centerY + 70, 0x888888);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
