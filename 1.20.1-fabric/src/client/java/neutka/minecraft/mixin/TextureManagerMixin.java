package neutka.minecraft.mixin;

import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.profiler.Profiler;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
	// Stable lifecycle hook: TextureManager implements ResourceReloader and wraps texture reload cycles.
	@Inject(method = "reload", at = @At("HEAD"))
	private void instantreload$reloadHead(
		ResourceReloader.Synchronizer synchronizer,
		ResourceManager resourceManager,
		Profiler prepareProfiler,
		Profiler applyProfiler,
		Executor prepareExecutor,
		Executor applyExecutor,
		CallbackInfoReturnable<CompletableFuture<Void>> cir
	) {
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		try {
			controller.onTextureManagerReloadStart();
		} catch (Throwable throwable) {
			controller.markInconsistent("failed in TextureManager.reload head hook", throwable);
		}
	}

	@Inject(method = "reload", at = @At("RETURN"))
	private void instantreload$reloadReturn(
		ResourceReloader.Synchronizer synchronizer,
		ResourceManager resourceManager,
		Profiler prepareProfiler,
		Profiler applyProfiler,
		Executor prepareExecutor,
		Executor applyExecutor,
		CallbackInfoReturnable<CompletableFuture<Void>> cir
	) {
		SelectiveAtlasReloadController controller = SelectiveAtlasReloadController.getInstance();
		cir.getReturnValue().whenComplete((unused, throwable) -> {
			try {
				controller.onTextureManagerReloadEnd(throwable);
			} catch (Throwable hookThrowable) {
				controller.markInconsistent("failed in TextureManager.reload return hook", hookThrowable);
			}
		});
	}
}
