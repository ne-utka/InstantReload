package neutka.minecraft.mixin;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SpriteAtlasTexture.class)
public interface SpriteAtlasTextureAccessor {
	@Accessor("width")
	int instantreload$getWidth();

	@Accessor("height")
	int instantreload$getHeight();

	@Accessor("mipLevel")
	int instantreload$getMipLevel();

	@Accessor("sprites")
	Map<Identifier, Sprite> instantreload$getSprites();
}
