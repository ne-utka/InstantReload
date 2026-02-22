package neutka.minecraft.mixin;

import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.render.model.SpriteAtlasManager$Atlas")
public interface SpriteAtlasManagerAtlasView {
	@Accessor("atlas")
	SpriteAtlasTexture instantreload$getAtlas();

	@Accessor("atlasInfoLocation")
	Identifier instantreload$getAtlasInfoLocation();
}
