package com.chalds.senseswap.mixin;

import com.chalds.senseswap.SenseSwapClientMod;
import com.chalds.senseswap.SenseSwapMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class HotbarHudMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderHotbar(DrawContext context, RenderTickCounter counter, CallbackInfo ci) {
        if (SenseSwapClientMod.currentRole == SenseSwapMod.Role.BLIND) {
            ci.cancel();
        }
    }
}
