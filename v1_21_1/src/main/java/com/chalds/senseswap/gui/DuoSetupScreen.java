package com.chalds.senseswap.gui;

import com.chalds.senseswap.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class DuoSetupScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget player1Field;
    private TextFieldWidget player2Field;
    private boolean duoEnabled;
    private String statusMsg = "";

    public DuoSetupScreen(Screen parent) {
        super(Text.literal("Режим 2 игроков"));
        this.parent     = parent;
        this.duoEnabled = ModConfig.get().duoMode;
    }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2;

        player1Field = new TextFieldWidget(textRenderer, cx - 100, cy - 40, 200, 20,
            Text.literal("Игрок 1"));
        player1Field.setPlaceholder(Text.literal("Ник игрока 1"));
        player1Field.setText(ModConfig.get().duoPlayer1);

        player2Field = new TextFieldWidget(textRenderer, cx - 100, cy, 200, 20,
            Text.literal("Игрок 2"));
        player2Field.setPlaceholder(Text.literal("Ник игрока 2"));
        player2Field.setText(ModConfig.get().duoPlayer2);

        addDrawableChild(player1Field);
        addDrawableChild(player2Field);

        addDrawableChild(ButtonWidget.builder(
            Text.literal(duoEnabled ? "§a✔ Дуэт вкл" : "§c✘ Дуэт выкл"),
            btn -> {
                duoEnabled = !duoEnabled;
                btn.setMessage(Text.literal(duoEnabled ? "§a✔ Дуэт вкл" : "§c✘ Дуэт выкл"));
            }
        ).dimensions(cx - 60, cy + 35, 120, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Сохранить"),
            btn -> save()
        ).dimensions(cx - 60, cy + 65, 120, 20).build());

        addDrawableChild(ButtonWidget.builder(
            Text.translatable("senseswap.gui.cancel"),
            btn -> close()
        ).dimensions(cx - 60, cy + 92, 120, 20).build());
    }

    private void save() {
        ModConfig cfg = ModConfig.get();
        cfg.duoMode    = duoEnabled;
        cfg.duoPlayer1 = player1Field.getText().trim();
        cfg.duoPlayer2 = player2Field.getText().trim();
        cfg.save();
        statusMsg = "§aSохранено!";
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        int cx = width / 2, cy = height / 2;
        ctx.fill(cx - 140, cy - 80, cx + 140, cy + 120, 0xDD0D0A06);
        ctx.drawBorder(cx - 140, cy - 80, 280, 200, 0xFFD4AF37);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy - 65, 0);
        ctx.getMatrices().scale(1.3f, 1.3f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Дуэт-режим"), 0, 0, 0xFFD700);
        ctx.getMatrices().pop();

        ctx.drawTextWithShadow(textRenderer, Text.literal("Игрок 1:"), cx - 100, cy - 52, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Игрок 2:"), cx - 100, cy - 12, 0xAAAAAA);

        if (!statusMsg.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(statusMsg), cx, cy + 115, 0xFFFFFF);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override public boolean shouldPause() { return false; }
}
