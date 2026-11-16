package dev.horizonlab.platform.mixin;

import dev.horizonlab.platform.HorizonTitle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void horizon$skipNativeWidget(GuiGraphicsExtractor graphics, int x, int y, float delta, CallbackInfo ci) {
        if (HorizonTitle.drawsNativeWidget((AbstractWidget) (Object) this)) ci.cancel();
    }
}
