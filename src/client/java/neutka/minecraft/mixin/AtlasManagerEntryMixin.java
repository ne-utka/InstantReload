package neutka.minecraft.mixin;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import neutka.minecraft.reload.AtlasReuseFactory;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(targets = "net.minecraft.client.texture.AtlasManager$Entry")
public abstract class AtlasManagerEntryMixin {
	// Stable per-atlas hook: AtlasManager always calls Entry#load for each atlas during prepare.
	@Inject(method = "load", at = @At("HEAD"), cancellable = true)
	private void instantreload$load(
		ResourceManager manager,
		Executor executor,
		int mipLevel,
		CallbackInfoReturnable<CompletableFuture<SpriteLoader.StitchResult>> cir
	) {
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		try {
			AtlasManagerEntryView entryView = (AtlasManagerEntryView)this;
			AtlasManager.Metadata metadata = entryView.instantreload$getMetadata();
			Identifier atlasTextureId = metadata.textureId();
			if (!controller.shouldSkip(atlasTextureId)) {
				return;
			}

			SpriteAtlasTexture atlas = entryView.instantreload$getAtlas();
			Optional<SpriteLoader.StitchResult> reuse = AtlasReuseFactory.tryCreate(atlas);
			if (reuse.isEmpty()) {
				controller.markInconsistent("unable to create synthetic stitch result for " + atlasTextureId, null);
				return;
			}

			SpriteLoader.StitchResult stitchResult = reuse.get();
			controller.markSyntheticResult(stitchResult, atlasTextureId);
			cir.setReturnValue(CompletableFuture.completedFuture(stitchResult));
		} catch (Throwable throwable) {
			controller.markInconsistent("failed in AtlasManager$Entry.load selective path", throwable);
			return;
		}
	}
}
