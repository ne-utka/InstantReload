package neutka.minecraft.reload;

import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.atlas.AtlasLoader;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class AtlasHashCalculator {
	private static final String FORMAT_VERSION = "instantreload-v1";
	public static final ResourceFinder ATLAS_DEFINITION_FINDER = ResourceFinder.json("atlases");

	private AtlasHashCalculator() {
	}

	public static AtlasScanResult scan(
		ResourceManager resourceManager,
		Identifier definitionId
	) {
		HashAccumulator accumulator = new HashAccumulator();
		accumulator.updateUtf8(FORMAT_VERSION);
		accumulator.updateIdentifier(definitionId);

		Identifier definitionPath = ATLAS_DEFINITION_FINDER.toResourcePath(definitionId);
		List<Resource> atlasDefinitions = resourceManager.getAllResources(definitionPath);
		accumulator.updateInt(atlasDefinitions.size());
		for (Resource definitionResource : atlasDefinitions) {
			accumulator.updateUtf8(definitionResource.getResourcePackName());
			accumulator.updateUtf8(definitionPath.toString());
			hashResourceBytes(accumulator, definitionResource);
		}

		AtlasLoader loader = AtlasLoader.of(resourceManager, definitionId);
		List<Supplier<SpriteContents>> sources = loader.loadSources(resourceManager);
		accumulator.updateInt(sources.size());
		Set<Identifier> dependencies = new HashSet<>();
		Map<Identifier, Boolean> dependencyPresenceCache = new HashMap<>();
		final boolean[] hasUnresolvedDependencies = {false};

		int spriteCount = 0;
		for (int i = 0; i < sources.size(); i++) {
			Supplier<SpriteContents> source = sources.get(i);
			accumulator.updateInt(i);
			accumulator.updateUtf8(source.getClass().getName());

			SpriteContents contents = source.get();
			if (contents == null) {
				accumulator.updateUtf8("null-sprite");
				continue;
			}

			try {
				spriteCount++;
				Identifier spriteId = contents.getId();
				accumulator.updateIdentifier(spriteId);

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
				accumulator.updateIdentifier(dependencyResourceId);
				List<Resource> resourceStack = resourceManager.getAllResources(dependencyResourceId);
				accumulator.updateInt(resourceStack.size());
				for (Resource dependency : resourceStack) {
					accumulator.updateUtf8(dependency.getResourcePackName());
					hashResourceBytes(accumulator, dependency);
				}

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
			throw new UncheckedIOException("Failed to hash resource bytes for " + resource.getResourcePackName(), exception);
		}
	}
}
