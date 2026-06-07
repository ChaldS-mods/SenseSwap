package com.chalds.senseswap.mixin;

import com.chalds.senseswap.gui.WheelSpinScreen;
import com.chalds.senseswap.gui.DaySummaryScreen;
import com.chalds.senseswap.gui.RolePopupScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class NoBlurMixin {
    @Inject(method = "method_57734", at = @At("HEAD"), cancellable = true)
    private void cancelBlur(float blurRadius, CallbackInfo ci) {
        if ((Object)this instanceof WheelSpinScreen ||
            (Object)this instanceof DaySummaryScreen ||
            (Object)this instanceof RolePopupScreen) {
            ci.cancel();
        }
    }
}
