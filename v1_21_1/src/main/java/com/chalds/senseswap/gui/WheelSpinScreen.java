package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WheelSpinScreen extends Screen {

    private static final Identifier WHEEL_TEX =
        Identifier.of("senseswap", "textures/wheel.png");
    private static final Identifier POINTER_TEX =
        Identifier.of("senseswap", "textures/pointer.png");

    private static final float[] ROLE_STOP_ANGLES = {270f, 0f, 90f, 180f};

    private final SenseSwapMod.Role targetRole;
    private final Screen parent;

    private float spinAngle   = 0f;
    private float spinSpeed   = 0f;
    private int   phase       = 0;
    private int   phaseTick   = 0;
    private float revealAlpha = 0f;

    private final List<Spark> sparks = new ArrayList<>();
    private final Random rng = new Random();

    private static class Spark {
        float x, y, vx, vy, life, maxLife;
        int color;
        Spark(float x, float y, float vx, float vy, float life, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = this.maxLife = life; this.color = color;
        }
        boolean dead() { return life <= 0; }
        void tick() {
            x += vx; y += vy;
            vy += 0.18f;
            vx *= 0.93f;
            life -= 1f;
        }
    }

    private static final int[] SPARK_COLORS = {
        0xFFFFD700, 0xFFFFAA00, 0xFFFF8800, 0xFFFFEE44, 0xFFFFFFAA
    };

    public WheelSpinScreen(SenseSwapMod.Role role, Screen parent) {
        super(Text.empty());
        this.targetRole = role;
        this.parent     = parent;
    }

    @Override
    public void tick() {
        phaseTick++;

        if (phase == 0) {
            spinSpeed = Math.min(spinSpeed + 3.2f, 58f);
            if (phaseTick > 35) { phase = 1; phaseTick = 0; }
        } else if (phase == 1) {
            float decel = phaseTick > 60 ? 0.88f : 0.975f;
            spinSpeed *= decel;

            if (phaseTick > 80 && spinSpeed < 0.8f) {
                float target = ROLE_STOP_ANGLES[targetRole.ordinal() % 4];
                float cur    = spinAngle % 360f;
                float diff   = ((target - cur) + 360f) % 360f;
                spinAngle   += diff;
                spinSpeed    = 0f;
                phase        = 2;
                phaseTick    = 0;
            }
        } else if (phase == 2) {
            revealAlpha = Math.min(revealAlpha + 0.035f, 1f);
        }

        spinAngle = (spinAngle + spinSpeed) % 3600f;

        if (spinSpeed > 4f) {
            spawnSparks();
        }

        for (int i = sparks.size() - 1; i >= 0; i--) {
            sparks.get(i).tick();
            if (sparks.get(i).dead()) sparks.remove(i);
        }

        if (phase == 2 && phaseTick > 90 && this.client != null) {
            this.client.setScreen(parent);
        }
    }

    private void spawnSparks() {
        if (width == 0) return;
        int size    = Math.min(Math.min(width, height) - 100, 380);
        float cx    = width  / 2f;
        float cy    = height / 2f - size * 0.08f;
        float ptrY  = cy - size / 2f - 10;

        int count = (int)(spinSpeed / 12f) + 1;
        for (int i = 0; i < count; i++) {
            float spread = (float)(Math.PI * 2);
            float ang    = (float)(-Math.PI / 2 + (rng.nextFloat() - 0.5f) * 0.9f);
            float spd    = 1.5f + rng.nextFloat() * 3.5f;
            float vx     = (float)Math.cos(ang) * spd;
            float vy     = (float)Math.sin(ang) * spd - 1.5f;
            int col      = SPARK_COLORS[rng.nextInt(SPARK_COLORS.length)];
            sparks.add(new Spark(cx + (rng.nextFloat() - 0.5f) * 18f,
                                 ptrY + 14f, vx, vy,
                                 6f + rng.nextFloat() * 10f, col));
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xDD000000);

        int size = Math.min(Math.min(width, height) - 100, 380);
        int cx   = width  / 2;
        int cy   = height / 2 - size / 8;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().multiply(
            net.minecraft.util.math.RotationAxis.POSITIVE_Z
                .rotationDegrees(spinAngle * 0.1f));
        ctx.drawTexture(WHEEL_TEX,
            -size/2, -size/2, 0, 0, size, size, size, size);
        ctx.getMatrices().pop();

        int pW = 36, pH = 48;
        ctx.drawTexture(POINTER_TEX,
            cx - pW/2, cy - size/2 - pH + 6, 0, 0, pW, pH, pW, pH);

        renderSparks(ctx);

        if (phase >= 2 && revealAlpha > 0.01f) {
            renderReveal(ctx, cx, cy, size);
        }

        super.render(ctx, mx, my, delta);
    }

    private void renderSparks(DrawContext ctx) {
        for (Spark s : sparks) {
            float t     = s.life / s.maxLife;
            int   alpha = (int)(t * 230f);
            int   argb  = (alpha << 24) | (s.color & 0x00FFFFFF);
            int   sx    = (int)s.x;
            int   sy    = (int)s.y;
            int   sz    = Math.max(1, (int)(t * 4f));
            ctx.fill(sx - sz/2, sy - sz/2, sx + sz/2, sy + sz/2, argb);
            if (sz > 2) {
                int dimmer = ((alpha / 2) << 24) | (s.color & 0x00FFFFFF);
                ctx.fill(sx - sz, sy - 1, sx + sz, sy + 1, dimmer);
            }
        }
    }

    private void renderReveal(DrawContext ctx, int cx, int cy, int size) {
        int alpha  = (int)(revealAlpha * 230f);
        int panelW = 280, panelH = 98;
        int px     = cx - panelW/2;
        int py     = cy + size/2 + 18;

        ctx.fill(px, py, px + panelW, py + panelH,
            (alpha << 24) | 0x0E0A05);
        ctx.drawBorder(px, py, panelW, panelH,
            (alpha << 24) | 0xD4AF37);
        ctx.drawBorder(px + 3, py + 3, panelW - 6, panelH - 6,
            ((alpha / 3) << 24) | 0xD4AF37);

        int roleRgb = getRoleRgb(targetRole);
        String name = Text.translatable(targetRole.getTranslationKey())
            .getString().toUpperCase();

        float scale     = 0.8f + revealAlpha * 0.5f;
        int   scaledAlpha = Math.min(alpha, 255);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, py + 30, 0);
        ctx.getMatrices().scale(scale * 1.8f, scale * 1.8f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(name), 0, 0,
            (scaledAlpha << 24) | roleRgb);
        ctx.getMatrices().pop();

        String descKey = "senseswap.popup.desc." + targetRole.name().toLowerCase();
        int ta = Math.min((int)(revealAlpha * 180f), 255);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(descKey), cx, py + 72,
            (ta << 24) | 0xAAAAAA);
    }

    private int getRoleRgb(SenseSwapMod.Role r) {
        return switch (r) {
            case BLIND -> 0xFF5555;
            case DEAF  -> 0x55AAFF;
            case MUTE  -> 0x55FF88;
            case DIZZY -> 0xCC55FF;
        };
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
