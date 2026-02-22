package neutka.minecraft.mixin;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import neutka.minecraft.reload.AtlasHashCalculator;
import neutka.minecraft.reload.AtlasScanResult;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(AtlasManager.class)
public abstract class AtlasManagerMixin {
	@Shadow @Final private Map<Identifier, Object> entriesByDefinitionId;

	// Stable entrypoint: AtlasManager is the concrete ResourceReloader that owns atlas prepare state.
	@Inject(method = "prepareSharedState", at = @At("HEAD"))
	private void instantreload$prepareSharedState(ResourceReloader.Store store, CallbackInfo ci) {
		ResourceManager resourceManager = store.getResourceManager();
		Map<Identifier, AtlasManager.Metadata> metadataByTextureId = this.collectAtlasMetadata();
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		try {
			controller.beginReload(resourceManager, metadataByTextureId);
		} catch (Throwable throwable) {
			controller.markInconsistent("failed to build selective reload plan", throwable);
			return;
		}

		Set<Identifier> scanAtlases = controller.getAtlasesToScan();
		for (Identifier atlasTextureId : scanAtlases) {
			AtlasManager.Metadata metadata = metadataByTextureId.get(atlasTextureId);
			if (metadata == null) {
				controller.markInconsistent("atlas metadata missing for " + atlasTextureId, null);
				break;
			}
			try {
				AtlasScanResult scan = AtlasHashCalculator.scan(
					resourceManager,
					metadata.definitionId(),
					metadata.additionalMetadata()
				);
				controller.acceptScanResult(resourceManager, atlasTextureId, metadata, scan);
			} catch (Throwable throwable) {
				controller.markInconsistent("atlas scan failed for " + metadata.definitionId(), throwable);
				break;
			}
		}
	}

	// Stable exitpoint: this is the Future returned by AtlasManager's reload contract.
	@Inject(method = "reload", at = @At("RETURN"))
	private void instantreload$reloadReturn(
		ResourceReloader.Store store,
		Executor prepareExecutor,
		ResourceReloader.Synchronizer synchronizer,
		Executor applyExecutor,
		CallbackInfoReturnable<CompletableFuture<Void>> cir
	) {
		cir.getReturnValue().whenComplete((unused, throwable) ->
			SelectiveAtlasReloadController.getInstance().finishReload(throwable == null)
		);
	}

	private Map<Identifier, AtlasManager.Metadata> collectAtlasMetadata() {
		Map<Identifier, AtlasManager.Metadata> metadataByTextureId = new HashMap<>();
		for (Object entryObject : this.entriesByDefinitionId.values()) {
			AtlasManagerEntryView entryView = (AtlasManagerEntryView)entryObject;
			AtlasManager.Metadata metadata = entryView.instantreload$getMetadata();
			metadataByTextureId.put(metadata.textureId(), metadata);
		}
		return metadataByTextureId;
	}
}
