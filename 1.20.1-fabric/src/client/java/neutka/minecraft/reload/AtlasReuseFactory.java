package neutka.minecraft.reload;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.util.Identifier;
import neutka.minecraft.mixin.SpriteAtlasTextureAccessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class AtlasReuseFactory {
	private AtlasReuseFactory() {
	}

	public static Optional<SpriteLoader.StitchResult> tryCreate(SpriteAtlasTexture atlas) {
		SpriteAtlasTextureAccessor accessor = (SpriteAtlasTextureAccessor)atlas;
		Map<Identifier, Sprite> sprites = accessor.instantreload$getSprites();
		if (sprites == null || sprites.isEmpty()) {
			return Optional.empty();
		}

		Sprite missing = atlas.getSprite(MissingSprite.getMissingSpriteId());
		if (missing == null) {
			return Optional.empty();
		}

		Map<Identifier, Sprite> snapshot = Map.copyOf(new HashMap<>(sprites));
		SpriteLoader.StitchResult result = new SpriteLoader.StitchResult(
			accessor.instantreload$getWidth(),
			accessor.instantreload$getHeight(),
			accessor.instantreload$getMipLevel(),
			missing,
			snapshot,
			CompletableFuture.completedFuture(null)
		);
		return Optional.of(result);
	}
}
