package com.chalds.senseswap.mixin;

import com.chalds.senseswap.gui.WheelSpinScreen;
import com.chalds.senseswap.gui.DaySummaryScreen;
import com.chalds.senseswap.gui.RolePopupScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class NoBlurMixin {
    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void noBlur(CallbackInfoReturnable<Boolean> cir) {}

    @Inject(method = "isBlurEnabled", at = @At("HEAD"), cancellable = true)
    private void disableBlur(CallbackInfoReturnable<Boolean> cir) {
        Screen screen = ((MinecraftClient)(Object)this).currentScreen;
        if (screen instanceof WheelSpinScreen ||
            screen instanceof DaySummaryScreen ||
            screen instanceof RolePopupScreen) {
            cir.setReturnValue(false);
        }
    }
}
