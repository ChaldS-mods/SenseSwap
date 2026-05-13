package com.chalds.senseswap.mixin;
import com.chalds.senseswap.SenseSwapClientMod;
import com.chalds.senseswap.SenseSwapMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderBossBar(DrawContext context, CallbackInfo ci) {
        if (SenseSwapClientMod.currentRole == SenseSwapMod.Role.BLIND) {
            ci.cancel();
        }
    }
}
