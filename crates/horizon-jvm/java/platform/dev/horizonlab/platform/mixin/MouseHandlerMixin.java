package dev.horizonlab.platform.mixin;

import dev.horizonlab.platform.HorizonClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mirror has no SDL window; its zero handle still receives synthetic GUI input. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = {"resyncMousePosition", "onDrop"}, at = @At("HEAD"), cancellable = true)
    private void horizon$syntheticMousePosition(CallbackInfo ci) {
        if (HorizonClient.headless()) ci.cancel();
    }
    @Inject(method = {"onButton", "onScroll", "onMove"}, at = @At("HEAD"), cancellable = true)
    private void horizon$playerInputIsBridged(CallbackInfo ci) {
        if (HorizonClient.headless() && !dev.horizonlab.platform.HorizonTitle.active()) ci.cancel();
    }
    @ModifyConstant(method = {"onButton", "onScroll", "onMove"}, constant = @Constant(longValue = 0L, ordinal = 0))
    private long horizon$headlessWindow(long invalidHandle) {
        return HorizonClient.headless() ? Long.MIN_VALUE : invalidHandle;
    }
}
