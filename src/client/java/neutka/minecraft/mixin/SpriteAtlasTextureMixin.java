package neutka.minecraft.mixin;

import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixin {
	// Stable upload gate: AtlasManager apply path funnels atlas GPU rebuild through create(...).
	@Inject(method = "create", at = @At("HEAD"), cancellable = true)
	private void instantreload$create(SpriteLoader.StitchResult stitchResult, CallbackInfo ci) {
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		try {
			if (controller.consumeSyntheticResult(stitchResult)) {
				ci.cancel();
			}
		} catch (Throwable throwable) {
			controller.markInconsistent("failed in SpriteAtlasTexture.create selective gate", throwable);
		}
	}
}
