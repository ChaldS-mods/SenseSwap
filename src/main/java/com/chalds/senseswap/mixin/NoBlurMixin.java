package com.chalds.senseswap.mixin;

import com.chalds.senseswap.gui.WheelSpinScreen;
import com.chalds.senseswap.gui.DaySummaryScreen;
import com.chalds.senseswap.gui.RolePopupScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class NoBlurMixin {
    @Shadow private MinecraftClient client;

    @Inject(method = "method_57796", at = @At("HEAD"), cancellable = true)
    private void cancelRenderBlur(float delta, CallbackInfo ci) {
        if (client.currentScreen instanceof WheelSpinScreen ||
            client.currentScreen instanceof DaySummaryScreen ||
            client.currentScreen instanceof RolePopupScreen) {
            ci.cancel();
        }
    }
}
