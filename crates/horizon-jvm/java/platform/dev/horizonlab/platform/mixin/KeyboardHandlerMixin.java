package dev.horizonlab.platform.mixin;

import dev.horizonlab.platform.HorizonClient;
import dev.horizonlab.platform.HorizonTitle;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Synthetic title input uses the mirror's zero handle and the complete mod hook chain. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @ModifyConstant(method = {"keyPress", "charTyped"}, constant = @Constant(longValue = 0L, ordinal = 0))
    private long horizon$syntheticWindow(long invalidHandle) {
        return HorizonClient.headless() && HorizonTitle.active() ? Long.MIN_VALUE : invalidHandle;
    }
}
