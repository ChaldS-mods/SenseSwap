package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WheelSpinScreen extends Screen {

    // One wheel texture per language
    private static final Identifier WHEEL_EN =
        Identifier.of("senseswap", "textures/wheel_en_us.png");
    private static final Identifier WHEEL_RU =
        Identifier.of("senseswap", "textures/wheel_ru_ru.png");
    private static final Identifier WHEEL_KK =
        Identifier.of("senseswap", "textures/wheel_kk_kz.png");

    private static final Identifier POINTER_TEX =
        Identifier.of("senseswap", "textures/pointer.png");

    // Stop angles matching wheel layout: BLIND=180, DEAF=270, MUTE=0, DIZZY=90
    private static final float[] ROLE_STOP_ANGLES = {180f, 270f, 0f, 90f};

    private final SenseSwapMod.Role targetRole;
    private final Screen parent;

    // Spin state
    private float spinAngle  = 0f;
    private float spinSpeed  = 0f;
    private int   phase      = 0;   // 0=accel 1=decel 2=slide 3=reveal
    private int   phaseTick  = 0;

    // Phase 2: role icon slides to center
    private float slideProgress = 0f;  // 0..1
    private float revealAlpha   = 0f;  // 0..1 for the reveal panel

    // Sparks
    private final List<Spark> sparks = new ArrayList<>();
    private final Random rng = new Random();

    private static final int[] SPARK_COLORS = {
        0xFFFFD700, 0xFFFFAA00, 0xFFFF8800, 0xFFFFEE44, 0xFFFFFFAA
    };

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
            vy += 0.18f; vx *= 0.93f;
            life -= 1f;
        }
    }

    public WheelSpinScreen(SenseSwapMod.Role role, Screen parent) {
        super(Text.empty());
        this.targetRole = role;
        this.parent     = parent;
    }

    /** Pick wheel texture based on current game language. */
    private Identifier getWheelTexture() {
        String lang = MinecraftClient.getInstance().getLanguageManager()
            .getLanguage();
        if (lang == null) return WHEEL_EN;
        if (lang.startsWith("ru")) return WHEEL_RU;
        if (lang.startsWith("kk")) return WHEEL_KK;
        return WHEEL_EN;
    }

    @Override
    public void tick() {
        phaseTick++;

        // Phase 0: accelerate
        if (phase == 0) {
            spinSpeed = Math.min(spinSpeed + 3.2f, 58f);
            if (phaseTick > 35) { phase = 1; phaseTick = 0; }

        // Phase 1: decelerate and snap
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

        // Phase 2: role icon slides from sector to center (60 ticks)
        } else if (phase == 2) {
            slideProgress = Math.min(slideProgress + 1f / 50f, 1f);
            if (phaseTick > 55) {
                phase     = 3;
                phaseTick = 0;
            }

        // Phase 3: reveal panel fades in, then close
        } else if (phase == 3) {
            revealAlpha = Math.min(revealAlpha + 0.04f, 1f);
            if (phaseTick > 110 && this.client != null) {
                this.client.setScreen(parent);
            }
        }

        spinAngle = (spinAngle + spinSpeed) % 3600f;

        if (spinSpeed > 4f) spawnSparks();
        for (int i = sparks.size() - 1; i >= 0; i--) {
            sparks.get(i).tick();
            if (sparks.get(i).dead()) sparks.remove(i);
        }
    }

    private void spawnSparks() {
        if (width == 0) return;
        int size   = Math.min(Math.min(width, height) - 100, 380);
        float cx   = width  / 2f;
        float cy   = height / 2f - size * 0.08f;
        float ptrY = cy - size / 2f - 10;
        int count  = (int)(spinSpeed / 12f) + 1;
        for (int i = 0; i < count; i++) {
            float ang = (float)(-Math.PI / 2 + (rng.nextFloat() - 0.5f) * 0.9f);
            float spd = 1.5f + rng.nextFloat() * 3.5f;
            float vx  = (float)Math.cos(ang) * spd;
            float vy  = (float)Math.sin(ang) * spd - 1.5f;
            int col   = SPARK_COLORS[rng.nextInt(SPARK_COLORS.length)];
            sparks.add(new Spark(
                cx + (rng.nextFloat() - 0.5f) * 18f,
                ptrY + 14f, vx, vy,
                6f + rng.nextFloat() * 10f, col));
        }
    }

    // ── smooth easing ──────────────────────────────────────────────────────
    private float easeOutCubic(float t) {
        float f = 1f - t;
        return 1f - f * f * f;
    }

    // ── render ─────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xDD000000);

        int size = Math.min(Math.min(width, height) - 100, 380);
        int cx   = width  / 2;
        int cy   = height / 2 - size / 8;

        // Spinning wheel
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 500);
        ctx.getMatrices().multiply(
            net.minecraft.util.math.RotationAxis.POSITIVE_Z
                .rotationDegrees(spinAngle * 0.1f));
        ctx.drawTexture(getWheelTexture(),
            -size/2, -size/2, 0, 0, size, size, size, size);
        ctx.getMatrices().pop();
        }

        // Pointer
        int pW = 36, pH = 48;
        ctx.drawTexture(POINTER_TEX,
            cx - pW/2, cy - size/2 - pH + 6, 0, 0, pW, pH, pW, pH);

        renderSparks(ctx);

        // Phase 2+: sliding circle
        if (phase >= 2) renderSlide(ctx, cx, cy, size);

        // Phase 3: reveal panel
        if (phase >= 3 && revealAlpha > 0.01f) renderReveal(ctx, cx, cy, size);

        super.render(ctx, mx, my, delta);
    }

    /** Colored circle slides from role sector position toward center. */
    private void renderSlide(DrawContext ctx, int cx, int cy, int size) {
        float t = easeOutCubic(slideProgress);
        int roleRgb = getRoleRgb(targetRole);

        // Sector center position (where the circle was on the wheel)
        double sectorRad = Math.toRadians(ROLE_STOP_ANGLES[targetRole.ordinal() % 4]);
        float dist = size * 0.55f / 2f;
        float startX = cx + (float)(dist * Math.cos(sectorRad));
        float startY = cy + (float)(dist * Math.sin(sectorRad));

        // Interpolate to wheel center
        float posX = startX + (cx - startX) * t;
        float posY = startY + (cy - startY) * t;

        // Grow from small to 40px radius
        int r = (int)(12 + 28 * t);
        int alpha = Math.min((int)(t * 255), 255);

        // Glow ring
        int glowAlpha = (int)(alpha * 0.45f);
        ctx.fill((int)(posX - r - 6), (int)(posY - r - 6),
                 (int)(posX + r + 6), (int)(posY + r + 6),
                 (glowAlpha << 24) | (roleRgb & 0x00FFFFFF));

        // Dark border
        ctx.fill((int)(posX - r - 2), (int)(posY - r - 2),
                 (int)(posX + r + 2), (int)(posY + r + 2),
                 (alpha << 24) | 0x000000);

        // Colored fill
        ctx.fill((int)(posX - r), (int)(posY - r),
                 (int)(posX + r), (int)(posY + r),
                 (alpha << 24) | (roleRgb & 0x00FFFFFF));

        // White inner shine
        int shineAlpha = (int)(alpha * 0.55f);
        int sr = r / 3;
        ctx.fill((int)(posX - sr + sr/2), (int)(posY - r + 4),
                 (int)(posX + sr/2),       (int)(posY - r + 4 + sr),
                 (shineAlpha << 24) | 0xFFFFFF);
    }

    /** Reveal panel: "Вам выпала роль: X / Дебафы: Y" */
    private void renderReveal(DrawContext ctx, int cx, int cy, int size) {
        int alpha  = (int)(revealAlpha * 230f);
        int panelW = 320;
        int panelH = 110;
        int px     = cx - panelW / 2;
        int py     = cy + size / 2 + 14;

        // Background
        ctx.fill(px, py, px + panelW, py + panelH,
            (alpha << 24) | 0x0A0705);
        ctx.drawBorder(px, py, panelW, panelH,
            (alpha << 24) | 0xD4AF37);
        ctx.drawBorder(px + 3, py + 3, panelW - 6, panelH - 6,
            ((alpha / 3) << 24) | 0xD4AF37);

        int roleRgb = getRoleRgb(targetRole);
        String roleName = Text.translatable(targetRole.getTranslationKey())
            .getString().toUpperCase();

        // Line 1: "Вам выпала роль:" label
        int labelAlpha = Math.min(alpha, 255);
        String assignedLabel = Text.translatable("senseswap.wheel.role_assigned")
            .getString();
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(assignedLabel),
            cx, py + 12,
            (labelAlpha << 24) | 0xCCCCCC);

        // Line 2: role name — larger, colored
        float scale = 1.0f + revealAlpha * 0.35f;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, py + 34, 0);
        ctx.getMatrices().scale(scale * 1.5f, scale * 1.5f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(roleName), 0, 0,
            (labelAlpha << 24) | roleRgb);
        ctx.getMatrices().pop();

        // Line 3: "Дебафы:" label
        int descAlpha = Math.min((int)(revealAlpha * 180f), 255);
        String debuffsLabel = Text.translatable("senseswap.wheel.debuffs")
            .getString();
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(debuffsLabel),
            cx, py + 72,
            (descAlpha << 24) | 0xCCCCCC);

        // Line 4: debuff description
        String debuffKey = "senseswap.wheel.debuff." + targetRole.name().toLowerCase();
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.translatable(debuffKey),
            cx, py + 86,
            (descAlpha << 24) | 0xAAAAAA);
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

    private int getRoleRgb(SenseSwapMod.Role r) {
        return switch (r) {
            case BLIND -> 0xFF5555;
            case DEAF  -> 0xFFBB22;
            case MUTE  -> 0x44DD66;
            case DIZZY -> 0xCC55FF;
        };
    }

    @Override public boolean shouldPause()      { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override protected void applyBlur(float blurRadius) {}
}
