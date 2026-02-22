package neutka.minecraft.mixin;

import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import neutka.minecraft.reload.SelectiveAtlasReloadController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AtlasLoader.class)
public abstract class AtlasLoaderMixin {
	// Stable factory hook: AtlasLoader#of is the atlas-definition entrypoint used by SpriteLoader.
	@Inject(method = "of", at = @At("HEAD"))
	private static void instantreload$of(
		ResourceManager resourceManager,
		Identifier id,
		CallbackInfoReturnable<AtlasLoader> cir
	) {
		SelectiveAtlasReloadController.getInstance().onAtlasLoaderRequested(id);
	}
}
