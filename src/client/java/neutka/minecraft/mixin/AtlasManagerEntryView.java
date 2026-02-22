package neutka.minecraft.mixin;

import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.texture.AtlasManager$Entry")
public interface AtlasManagerEntryView {
	@Invoker("metadata")
	AtlasManager.Metadata instantreload$getMetadata();

	@Invoker("atlas")
	SpriteAtlasTexture instantreload$getAtlas();
}
