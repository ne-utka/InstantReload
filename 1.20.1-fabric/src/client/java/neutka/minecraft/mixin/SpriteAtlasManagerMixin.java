package neutka.minecraft.mixin;

import net.minecraft.client.render.model.SpriteAtlasManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import neutka.minecraft.reload.AtlasHashCalculator;
import neutka.minecraft.reload.AtlasReuseFactory;
import neutka.minecraft.reload.AtlasScanResult;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteAtlasManager.class)
public abstract class SpriteAtlasManagerMixin {
	@Shadow @Final private Map<Identifier, Object> atlases;

	// Stable entrypoint for 1.21.1: SpriteAtlasManager#reload prepares atlas stitch tasks.
	@Inject(method = "reload", at = @At("HEAD"))
	private void instantreload$reloadHead(
		ResourceManager resourceManager,
		int mipLevel,
		Executor executor,
		CallbackInfoReturnable<Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>>> cir
	) {
		Map<Identifier, Identifier> definitionsByTextureId = this.collectAtlasDefinitions();
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		try {
			controller.beginReload(resourceManager, definitionsByTextureId);
		} catch (Throwable throwable) {
			controller.markInconsistent("failed to build selective reload plan", throwable);
			return;
		}

		Set<Identifier> scanAtlases = controller.getAtlasesToScan();
		for (Identifier atlasTextureId : scanAtlases) {
			Identifier definitionId = definitionsByTextureId.get(atlasTextureId);
			if (definitionId == null) {
				controller.markInconsistent("atlas definition missing for " + atlasTextureId, null);
				break;
			}
			try {
				AtlasScanResult scan = AtlasHashCalculator.scan(
					resourceManager,
					definitionId
				);
				controller.acceptScanResult(resourceManager, atlasTextureId, definitionId, scan);
			} catch (Throwable throwable) {
				controller.markInconsistent("atlas scan failed for " + definitionId, throwable);
				break;
			}
		}
	}

	// Stable return hook: replace unchanged atlas tasks with synthetic completed preparations.
	@Inject(method = "reload", at = @At("RETURN"))
	private void instantreload$reloadReturn(
		ResourceManager resourceManager,
		int mipLevel,
		Executor executor,
		CallbackInfoReturnable<Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>>> cir
	) {
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>> reloadMap = cir.getReturnValue();
		Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>> replacements = new HashMap<>();

		try {
			for (Map.Entry<Identifier, Object> entry : this.atlases.entrySet()) {
				Identifier atlasTextureId = entry.getKey();
				if (!controller.shouldSkip(atlasTextureId)) {
					continue;
				}

				SpriteAtlasManagerAtlasView atlasView = (SpriteAtlasManagerAtlasView)entry.getValue();
				SpriteAtlasTexture atlas = atlasView.instantreload$getAtlas();
				Optional<SpriteLoader.StitchResult> reuse = AtlasReuseFactory.tryCreate(atlas);
				if (reuse.isEmpty()) {
					controller.markInconsistent("unable to create synthetic stitch result for " + atlasTextureId, null);
					return;
				}

				SpriteLoader.StitchResult stitchResult = reuse.get();
				controller.markSyntheticResult(stitchResult, atlasTextureId);
				SpriteAtlasManager.AtlasPreparation preparation = new SpriteAtlasManager.AtlasPreparation(atlas, stitchResult);
				replacements.put(atlasTextureId, CompletableFuture.completedFuture(preparation));
			}
		} catch (Throwable throwable) {
			controller.markInconsistent("failed in SpriteAtlasManager.reload selective path", throwable);
			return;
		}

		reloadMap.putAll(replacements);
	}

	private Map<Identifier, Identifier> collectAtlasDefinitions() {
		Map<Identifier, Identifier> definitionsByTextureId = new HashMap<>();
		for (Map.Entry<Identifier, Object> entry : this.atlases.entrySet()) {
			SpriteAtlasManagerAtlasView atlasView = (SpriteAtlasManagerAtlasView)entry.getValue();
			definitionsByTextureId.put(entry.getKey(), atlasView.instantreload$getAtlasInfoLocation());
		}
		return definitionsByTextureId;
	}
}
