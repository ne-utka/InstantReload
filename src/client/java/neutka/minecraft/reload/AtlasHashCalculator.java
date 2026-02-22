package neutka.minecraft.reload;

import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteOpener;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadataSerializer;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AtlasHashCalculator {
	private static final String FORMAT_VERSION = "instantreload-v1";
	public static final ResourceFinder ATLAS_DEFINITION_FINDER = ResourceFinder.json("atlases");

	private AtlasHashCalculator() {
	}

	public static AtlasScanResult scan(
		ResourceManager resourceManager,
		Identifier definitionId,
		Set<ResourceMetadataSerializer<?>> additionalMetadata
	) {
		HashAccumulator accumulator = new HashAccumulator();
		accumulator.updateUtf8(FORMAT_VERSION);
		accumulator.updateIdentifier(definitionId);

		Identifier definitionPath = ATLAS_DEFINITION_FINDER.toResourcePath(definitionId);
		List<Resource> atlasDefinitions = resourceManager.getAllResources(definitionPath);
		accumulator.updateInt(atlasDefinitions.size());
		for (Resource definitionResource : atlasDefinitions) {
			accumulator.updateUtf8(definitionResource.getPackId());
			accumulator.updateUtf8(definitionPath.toString());
			hashResourceBytes(accumulator, definitionResource);
		}

		AtlasLoader loader = AtlasLoader.of(resourceManager, definitionId);
		List<AtlasSource.SpriteSource> sources = loader.loadSources(resourceManager);
		accumulator.updateInt(sources.size());
		Set<Identifier> dependencies = new HashSet<>();
		Map<Identifier, Boolean> dependencyPresenceCache = new HashMap<>();

		SpriteOpener delegate = SpriteOpener.create(additionalMetadata);
		final boolean[] hasUnresolvedDependencies = {false};
		SpriteOpener hashingOpener = (spriteId, resource) -> {
			Identifier dependencyResourceId = AtlasSource.RESOURCE_FINDER.toResourcePath(spriteId);
			dependencies.add(dependencyResourceId);
			boolean dependencyPresent = dependencyPresenceCache.computeIfAbsent(dependencyResourceId, id -> {
				try {
					return resourceManager.getResource(id).isPresent();
				} catch (Throwable throwable) {
					return false;
				}
			});
			if (!dependencyPresent) {
				hasUnresolvedDependencies[0] = true;
			}

			accumulator.updateUtf8("sprite-input");
			accumulator.updateIdentifier(spriteId);
			accumulator.updateUtf8(resource.getPackId());
			hashResourceBytes(accumulator, resource);
			return delegate.loadSprite(spriteId, resource);
		};

		int spriteCount = 0;
		for (int i = 0; i < sources.size(); i++) {
			AtlasSource.SpriteSource source = sources.get(i);
			accumulator.updateInt(i);
			accumulator.updateUtf8(source.getClass().getName());

			SpriteContents contents = source.load(hashingOpener);
			if (contents == null) {
				accumulator.updateUtf8("null-sprite");
				continue;
			}

			try {
				spriteCount++;
				accumulator.updateIdentifier(contents.getId());
				accumulator.updateInt(contents.getWidth());
				accumulator.updateInt(contents.getHeight());
			} finally {
				contents.close();
			}
		}

		return new AtlasScanResult(
			accumulator.finish(spriteCount),
			Set.copyOf(dependencies),
			hasUnresolvedDependencies[0]
		);
	}

	private static void hashResourceBytes(HashAccumulator accumulator, Resource resource) {
		byte[] buffer = new byte[8192];
		try (InputStream stream = resource.getInputStream()) {
			int read;
			while ((read = stream.read(buffer)) >= 0) {
				if (read == 0) {
					continue;
				}
				accumulator.updateBytes(buffer, read);
			}
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to hash resource bytes for " + resource.getPackId(), exception);
		}
	}
}
