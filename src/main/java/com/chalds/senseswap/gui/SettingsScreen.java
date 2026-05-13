package com.chalds.senseswap.gui;

import com.chalds.senseswap.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {

    private final Screen parent;
    private ModConfig config;

    private float tempHudScale;
    private int tempBlindStrength;
    private float tempFogIntensity;
    private int tempPopupDuration;
    private ModConfig.HudPosition tempPosition;
    private boolean tempHudEnabled;
    private boolean tempAutoAssign;
    private boolean tempShowPopup;

    public SettingsScreen(Screen parent) {
        super(Text.translatable("senseswap.gui.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        config = ModConfig.get();
        tempHudScale = config.hudScale;
        tempBlindStrength = config.blindEffectStrength;
        tempFogIntensity = config.fogIntensity;
        tempPopupDuration = config.rolePopupDuration;
        tempPosition = config.hudPosition;
        tempHudEnabled = config.hudEnabled;
        tempAutoAssign = config.autoAssignRoles;
        tempShowPopup = config.showRolePopup;

        int centerX = this.width / 2;
        int btnW = 310;
        int btnH = 20;
        int gap = 26;
        int x = centerX - btnW / 2;

        int y = 42;

        addDrawableChild(CyclingButtonWidget.onOffBuilder(tempHudEnabled)
            .build(x, y, btnW, btnH,
                Text.translatable("senseswap.gui.hud_enabled"),
                (btn, val) -> tempHudEnabled = val));

        addDrawableChild(CyclingButtonWidget.<ModConfig.HudPosition>builder(pos ->
                    Text.translatable("senseswap.gui.hud_pos_" + pos.name().toLowerCase()))
                .values(ModConfig.HudPosition.values())
                .initially(tempPosition)
                .build(x, y + gap, btnW, btnH,
                    Text.translatable("senseswap.gui.hud_position"),
                    (btn, val) -> tempPosition = val));

        addDrawableChild(new SliderWidget(x, y + gap * 2, btnW, btnH,
                Text.literal(""), (tempHudScale - 0.5f) / 1.5f) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("senseswap.gui.hud_scale")
                    .append(Text.literal(": " + String.format("%.1f", tempHudScale))));
            }
            @Override
            protected void applyValue() {
                tempHudScale = (float)(value * 1.5f + 0.5f);
            }
        });

        addDrawableChild(new SliderWidget(x, y + gap * 3, btnW, btnH,
                Text.literal(""), tempFogIntensity) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("senseswap.gui.fog_intensity")
                    .append(Text.literal(": " + String.format("%.0f%%", tempFogIntensity * 100))));
            }
            @Override
            protected void applyValue() {
                tempFogIntensity = (float) value;
            }
        });

        int gameY = y + gap * 4 + 16;

        addDrawableChild(new SliderWidget(x, gameY, btnW, btnH,
                Text.literal(""), (tempBlindStrength - 1) / 9.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("senseswap.gui.blind_effect")
                    .append(Text.literal(": " + tempBlindStrength)));
            }
            @Override
            protected void applyValue() {
                tempBlindStrength = (int)(value * 9) + 1;
            }
        });

        addDrawableChild(CyclingButtonWidget.onOffBuilder(tempAutoAssign)
            .build(x, gameY + gap, btnW, btnH,
                Text.translatable("senseswap.gui.auto_assign"),
                (btn, val) -> tempAutoAssign = val));

        addDrawableChild(CyclingButtonWidget.onOffBuilder(tempShowPopup)
            .build(x, gameY + gap * 2, btnW, btnH,
                Text.translatable("senseswap.gui.show_role_popup"),
                (btn, val) -> tempShowPopup = val));

        addDrawableChild(new SliderWidget(x, gameY + gap * 3, btnW, btnH,
                Text.literal(""), (tempPopupDuration - 1) / 14.0) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("senseswap.gui.role_popup_duration")
                    .append(Text.literal(": " + tempPopupDuration + "s")));
            }
            @Override
            protected void applyValue() {
                tempPopupDuration = (int)(value * 14) + 1;
            }
        });

        int bottomY = this.height - 30;

        addDrawableChild(ButtonWidget.builder(Text.translatable("senseswap.gui.done"), btn -> {
            applyAndSave();
            this.client.setScreen(parent);
        }).dimensions(centerX - 155, bottomY, 100, btnH).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("senseswap.gui.reset"), btn -> {
            config.resetToDefault();
            this.client.setScreen(new SettingsScreen(parent));
        }).dimensions(centerX - 50, bottomY, 100, btnH).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("senseswap.gui.cancel"), btn -> {
            this.client.setScreen(parent);
        }).dimensions(centerX + 55, bottomY, 100, btnH).build());
    }

    private void applyAndSave() {
        config.hudEnabled = tempHudEnabled;
        config.hudScale = tempHudScale;
        config.hudPosition = tempPosition;
        config.blindEffectStrength = tempBlindStrength;
        config.fogIntensity = tempFogIntensity;
        config.autoAssignRoles = tempAutoAssign;
        config.showRolePopup = tempShowPopup;
        config.rolePopupDuration = tempPopupDuration;
        config.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int gap = 26;
        int sectionGameY = 42 + gap * 4 + 16;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
            centerX, 10, 0xFFFFFF);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.translatable("senseswap.gui.section_visual"),
            centerX, 30, 0xFFD700);

        context.drawCenteredTextWithShadow(this.textRenderer,
            Text.translatable("senseswap.gui.section_game"),
            centerX, sectionGameY - 12, 0xFFD700);
    }
}
