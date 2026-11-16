package dev.horizonlab.platform.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.horizonlab.platform.HorizonClient;
import dev.horizonlab.platform.HorizonGpu;
import dev.horizonlab.platform.HorizonTextures;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mod's dynamic texture in the headless client (an image it draws in memory: Chunk Loaders'
 * chunk maps): no GPU texture; a named view whose pixels go to Horizon where a frame draws it
 * (HorizonTextures.imageOp).
 */
@Mixin(DynamicTexture.class)
public abstract class DynamicTextureMixin extends AbstractTexture {
    @Inject(method = "createTexture(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void horizon$create(String label, CallbackInfo ci) {
        if (!HorizonClient.headless()) return;
        ci.cancel();
        horizon$createView();
    }

    @Inject(method = "createTexture(Ljava/util/function/Supplier;)V", at = @At("HEAD"), cancellable = true)
    private void horizon$createNamed(java.util.function.Supplier<String> label, CallbackInfo ci) {
        if (!HorizonClient.headless()) return;
        ci.cancel();
        horizon$createView();
    }

    @Unique
    private void horizon$createView() {
        this.textureView = HorizonTextures.dynamic((DynamicTexture) (Object) this);
        this.texture = new HorizonTextures.Texture((HorizonTextures.View) this.textureView);
        // Vanilla's DynamicTexture selects nearest magnification, including downloaded skins.
        if (HorizonGpu.installed()) this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void horizon$upload(CallbackInfo ci) {
        if (!HorizonClient.headless()) return;
        ci.cancel();
        HorizonTextures.uploaded((DynamicTexture) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void horizon$close(CallbackInfo ci) {
        if (HorizonClient.headless()) HorizonTextures.closed((DynamicTexture) (Object) this);
    }
}
