package dev.horizonlab.platform.mixin;

import com.mojang.authlib.GameProfile;
import dev.horizonlab.platform.HorizonClient;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Resolve the launcher player's skin for mod screens. Other profiles use their default
 * skin in the Java mirror. Horizon downloads world skins for its own renderer.
 */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void horizon$skin(GameProfile profile, CallbackInfoReturnable<CompletableFuture<Optional<PlayerSkin>>> cir) {
        if (!HorizonClient.headless()) return;
        var resolved = HorizonClient.launcherSkinProfile(profile);
        if (resolved != null && dev.horizonlab.platform.HorizonGpu.installed()) {
            // The second call carries Mojang's texture property and uses vanilla's cache.
            if (resolved.getNow(null) == profile) return;
            cir.setReturnValue(resolved.thenCompose(result -> result == null
                    ? CompletableFuture.completedFuture(Optional.of(DefaultPlayerSkin.get(profile)))
                    : ((SkinManager) (Object) this).get(result)));
        } else {
            cir.setReturnValue(CompletableFuture.completedFuture(Optional.of(DefaultPlayerSkin.get(profile))));
        }
    }
}
