package com.chalds.senseswap.gui;

import com.chalds.senseswap.SenseSwapMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Random;

public class DaySummaryScreen extends Screen {

    private static final Identifier BG_TEX =
        Identifier.of("senseswap", "textures/summary_bg.png");
    private static final Identifier[] BADGE_TEX = {
        Identifier.of("senseswap", "textures/role_badge_blind.png"),
        Identifier.of("senseswap", "textures/role_badge_deaf.png"),
        Identifier.of("senseswap", "textures/role_badge_mute.png"),
        Identifier.of("senseswap", "textures/role_badge_dizzy.png"),
    };

    public record PlayerEntry(String name, SenseSwapMod.Role role, int score, int delta) {}

    private final int roundNumber;
    private final List<PlayerEntry> entries;
    private final Screen parent;

    private int   tick         = 0;
    private float scrollOpen   = 0f;
    private float[] rowReveal;
    private Particle[] particles;
    private final Random rng = new Random();

    private static class Particle {
        float x, y, vx, vy, life, maxLife;
        int color;
        Particle(float x, float y, float vx, float vy, float life, int col) {
            this.x=x; this.y=y; this.vx=vx; this.vy=vy;
            this.life=this.maxLife=life; this.color=col;
        }
        void tick() { x+=vx; y+=vy; vy+=0.12f; vx*=0.96f; life-=1f; }
        boolean dead() { return life<=0; }
    }

    private static final int[] CONFETTI = {
        0xFFD700, 0xFF5555, 0x55AAFF, 0x55FF88, 0xCC55FF, 0xFF8844
    };

    public DaySummaryScreen(int round, List<PlayerEntry> entries, Screen parent) {
        super(Text.empty());
        this.roundNumber = round;
        this.entries     = entries;
        this.parent      = parent;
        this.rowReveal   = new float[Math.max(entries.size(), 1)];
        this.particles   = new Particle[0];
    }

    @Override
    protected void init() {
        int bw = 120, bh = 22;
        addDrawableChild(ButtonWidget.builder(
            Text.translatable("senseswap.gui.done"),
            b -> close()
        ).dimensions(width/2 - bw/2, height/2 + 210, bw, bh).build());
    }

    @Override
    public void tick() {
        tick++;

        // Phase 1: scroll unfurl (0-30 ticks)
        scrollOpen = Math.min(scrollOpen + 0.045f, 1f);

        // Phase 2: rows cascade in after scroll is ~60% open
        if (scrollOpen > 0.6f) {
            float rowBase = (scrollOpen - 0.6f) / 0.4f;
            for (int i = 0; i < rowReveal.length; i++) {
                float target = Math.max(0f, rowBase - i * 0.22f);
                rowReveal[i] = Math.min(rowReveal[i] + 0.055f, Math.min(target, 1f));
            }
        }

        // Phase 3: confetti burst once fully open
        if (scrollOpen >= 1f && tick == 30) {
            spawnConfetti();
        }

        java.util.ArrayList<Particle> alive = new java.util.ArrayList<>();
        for (Particle p : particles) {
            p.tick();
            if (!p.dead()) alive.add(p);
        }
        particles = alive.toArray(new Particle[0]);
    }

    private void spawnConfetti() {
        int cx = width / 2;
        int cy = height / 2 - 180;
        java.util.ArrayList<Particle> list = new java.util.ArrayList<>();
        for (int i = 0; i < 80; i++) {
            float ang = (float)(Math.PI * 2 * rng.nextFloat());
            float spd = 1.5f + rng.nextFloat() * 5f;
            int col   = CONFETTI[rng.nextInt(CONFETTI.length)];
            list.add(new Particle(
                cx + (rng.nextFloat()-0.5f)*60,
                cy + (rng.nextFloat()-0.5f)*20,
                (float)Math.cos(ang)*spd,
                (float)Math.sin(ang)*spd - 2.5f,
                25f + rng.nextFloat()*35f, col));
        }
        particles = list.toArray(new Particle[0]);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0xBB000000);

        int bgW = 700, bgH = 520;
        int bgX = width/2  - bgW/2;
        int bgY = height/2 - bgH/2;

        // scroll unfurl: clip height
        float unroll  = easeOutBack(scrollOpen);
        int   clipH   = (int)(bgH * unroll);
        int   clipY   = bgY + bgH/2 - clipH/2;

        if (clipH < 4) {
            super.render(ctx, mx, my, delta);
            return;
        }

        // scale Y so it looks like unrolling from center
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width/2f, height/2f, 0);
        ctx.getMatrices().scale(1f, unroll, 1f);
        ctx.getMatrices().translate(-width/2f, -height/2f, 0);

        ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, BG_TEX, bgX, bgY, 0f, 0f, bgW, bgH, bgW, bgH);
        ctx.getMatrices().pop();

        // contents only when sufficiently open
        if (scrollOpen > 0.55f) {
            float contentAlpha = (scrollOpen - 0.55f) / 0.45f;
            int   cAlpha       = (int)(contentAlpha * 255f);

            renderTitle(ctx, bgX, bgY, bgW, cAlpha);
            renderRows(ctx, bgX, bgY, bgW, cAlpha);
            renderFooter(ctx, bgX, bgY, bgW, bgH, cAlpha);
        }

        renderParticles(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void renderTitle(DrawContext ctx, int bgX, int bgY, int bgW, int alpha) {
        String title = "ИТОГИ РАУНДА " + roundNumber;
        int a = Math.min(alpha, 255);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(bgX + bgW/2f, bgY + 56, 0);
        ctx.getMatrices().scale(1.55f, 1.55f, 1f);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal(title), 0, 0, (a << 24) | 0xFFD700);
        ctx.getMatrices().pop();
    }

    private void renderRows(DrawContext ctx, int bgX, int bgY, int bgW, int baseAlpha) {
        int rowY0 = bgY + 108;
        int rowH  = 88;
        int badgeS = 52;

        for (int i = 0; i < Math.min(entries.size(), 4); i++) {
            float rev = rowReveal[i];
            if (rev <= 0.01f) continue;

            int   alpha = (int)(rev * Math.min(baseAlpha, 255));
            PlayerEntry e = entries.get(i);
            int ey = rowY0 + i * rowH;

            // row slide-in from right
            int slideX = (int)((1f - easeOut(rev)) * 80f);

            ctx.getMatrices().push();
            ctx.getMatrices().translate(slideX, 0, 0);

            // badge
            int bx = bgX + 22;
            int by = ey + (rowH - badgeS) / 2 - 2;
            ctx.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, BADGE_TEX[e.role().ordinal() % 4], bx, by, 0f, 0f, badgeS, badgeS, badgeS, badgeS);

            // medal
            String medal = switch (i) {
                case 0 -> "1.";
                case 1 -> "2.";
                case 2 -> "3.";
                default -> "4.";
            };
            int medalColor = switch (i) {
                case 0 -> 0xFFD700;
                case 1 -> 0xCCCCCC;
                case 2 -> 0xCD7F32;
                default -> 0x888888;
            };

            // player name
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(medal + " " + e.name()),
                bgX + 82, ey + 18,
                (alpha << 24) | medalColor);

            // role name
            String roleName = Text.translatable(e.role().getTranslationKey()).getString();
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(roleName),
                bgX + 82, ey + 34,
                (alpha << 24) | (getRoleRgb(e.role()) & 0xFFFFFF));

            // score
            String scoreStr = e.score() + " pts";
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(scoreStr),
                bgX + bgW - 120, ey + 22,
                (alpha << 24) | 0xFFD700);

            // delta
            if (e.delta() > 0) {
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("+" + e.delta()),
                    bgX + bgW - 120, ey + 38,
                    (alpha << 24) | 0x55FF88);
            }

            // progress bar
            int barX = bgX + 82, barY = ey + 54;
            int barW = bgW - 220;
            int maxScore = entries.stream().mapToInt(PlayerEntry::score).max().orElse(1);
            int filled = (int)(barW * rev * (e.score() / (float) Math.max(maxScore, 1)));
            int roleRgb = getRoleRgb(e.role());

            ctx.fill(barX, barY, barX + barW, barY + 7,
                ((alpha / 3) << 24) | 0x333333);
            ctx.fill(barX, barY, barX + filled, barY + 7,
                (alpha << 24) | (roleRgb & 0xFFFFFF));
            // shine
            ctx.fill(barX, barY, barX + filled, barY + 3,
                ((alpha / 2) << 24) | 0xFFFFFF);

            ctx.getMatrices().pop();
        }
    }

    private void renderFooter(DrawContext ctx, int bgX, int bgY, int bgW, int bgH, int alpha) {
        int a = Math.min(alpha, 255);
        ctx.drawCenteredTextWithShadow(textRenderer,
            Text.literal("SenseSwap  ·  конец раунда"),
            bgX + bgW/2, bgY + bgH - 34,
            (a << 24) | 0xC8A850);
    }

    private void renderParticles(DrawContext ctx) {
        for (Particle p : particles) {
            float t   = p.life / p.maxLife;
            int alpha = (int)(t * 220f);
            int argb  = (alpha << 24) | (p.color & 0xFFFFFF);
            int sz    = Math.max(2, (int)(t * 5f));
            int sx    = (int)p.x, sy = (int)p.y;
            ctx.fill(sx - sz/2, sy - sz/2, sx + sz/2, sy + sz/2, argb);
        }
    }

    private float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float v = 1f + c3 * (float)Math.pow(t - 1, 3) + c1 * (float)Math.pow(t - 1, 2);
        return Math.max(0f, Math.min(1f, v));
    }

    private float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private int getRoleRgb(SenseSwapMod.Role r) {
        return switch (r) {
            case BLIND -> 0xFF5555;
            case DEAF  -> 0x55AAFF;
            case MUTE  -> 0x55FF88;
            case DIZZY -> 0xCC55FF;
        };
    }

    @Override public boolean shouldPause() { return false; }
}
