package neutka.minecraft.reload;

import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import neutka.minecraft.InstantReload;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SelectiveAtlasReloadController {
	private static final SelectiveAtlasReloadController INSTANCE = new SelectiveAtlasReloadController();
	private static final String STACK_SIG_VERSION = "instantreload-stack-v1";

	private final Set<SpriteLoader.StitchResult> syntheticResults = ConcurrentHashMap.newKeySet();
	private final AtomicInteger textureReloadDepth = new AtomicInteger();

	private final Map<Identifier, AtlasState> previousStates = new HashMap<>();
	private final Map<Identifier, Set<Identifier>> previousResourceToAtlases = new HashMap<>();
	private final Map<Identifier, String> previousResourceSignatures = new HashMap<>();

	private final Map<Identifier, AtlasState> nextStates = new HashMap<>();
	private final Map<Identifier, String> nextResourceSignatures = new HashMap<>();
	private final Map<Identifier, Boolean> rebuildPlan = new HashMap<>();

	private final Set<Identifier> activeAtlases = new HashSet<>();
	private final Set<Identifier> atlasesToScan = new HashSet<>();

	private volatile boolean fallbackToFullReload;
	private volatile boolean debug;
	private volatile int expectedAtlasCount;
	private final boolean strictResourceSignatures =
		Boolean.parseBoolean(System.getProperty("instantreload.strictSignatures", "true"));

	private SelectiveAtlasReloadController() {
	}

	public static SelectiveAtlasReloadController getInstance() {
		return INSTANCE;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public synchronized void beginReload(ResourceManager resourceManager, Map<Identifier, Identifier> definitionsByTextureId) {
		this.expectedAtlasCount = definitionsByTextureId.size();
		this.fallbackToFullReload = false;
		this.syntheticResults.clear();
		this.nextStates.clear();
		this.nextResourceSignatures.clear();
		this.rebuildPlan.clear();
		this.activeAtlases.clear();
		this.atlasesToScan.clear();
		this.activeAtlases.addAll(definitionsByTextureId.keySet());

		if (this.previousStates.isEmpty()) {
			for (Identifier atlasTextureId : definitionsByTextureId.keySet()) {
				this.rebuildPlan.put(atlasTextureId, true);
				this.atlasesToScan.add(atlasTextureId);
			}
			this.debug("begin reload: cold start, scheduling all {} atlases", definitionsByTextureId.size());
			return;
		}

		Set<Identifier> changedResources = this.detectChangedDependencyResources(resourceManager);
		Set<Identifier> candidates = new HashSet<>();
		for (Identifier changedResource : changedResources) {
			Set<Identifier> atlases = this.previousResourceToAtlases.get(changedResource);
			if (atlases != null) {
				candidates.addAll(atlases);
			}
		}

		for (Map.Entry<Identifier, Identifier> entry : definitionsByTextureId.entrySet()) {
			Identifier atlasTextureId = entry.getKey();
			Identifier definitionId = entry.getValue();
			AtlasState previousState = this.previousStates.get(atlasTextureId);

			Identifier currentDefinitionPath = this.toDefinitionPath(definitionId);
			String currentDefinitionSignature = this.getOrComputeResourceStackSignature(resourceManager, currentDefinitionPath);

			if (previousState == null || !Objects.equals(previousState.definitionId(), definitionId)) {
				candidates.add(atlasTextureId);
				continue;
			}
			if (previousState.hasUnresolvedDependencies()) {
				candidates.add(atlasTextureId);
				continue;
			}

			Identifier previousDefinitionPath = this.toDefinitionPath(previousState.definitionId());
			String previousDefinitionSignature = this.previousResourceSignatures.get(previousDefinitionPath);
			if (!Objects.equals(previousDefinitionSignature, currentDefinitionSignature)) {
				candidates.add(atlasTextureId);
				continue;
			}

			this.nextStates.put(atlasTextureId, previousState);
			this.rebuildPlan.put(atlasTextureId, false);
		}

		for (Identifier atlasTextureId : candidates) {
			this.rebuildPlan.put(atlasTextureId, true);
			this.atlasesToScan.add(atlasTextureId);
		}

		this.debug(
			"begin reload: candidates={} reused={}",
			this.atlasesToScan.size(),
			definitionsByTextureId.size() - this.atlasesToScan.size()
		);
	}

	public synchronized Set<Identifier> getAtlasesToScan() {
		return Set.copyOf(this.atlasesToScan);
	}

	public synchronized void acceptScanResult(
		ResourceManager resourceManager,
		Identifier atlasTextureId,
		Identifier definitionId,
		AtlasScanResult result
	) {
		AtlasState previous = this.previousStates.get(atlasTextureId);
		AtlasState current = new AtlasState(
			definitionId,
			result.fingerprint(),
			result.dependencies(),
			result.hasUnresolvedDependencies()
		);
		this.nextStates.put(atlasTextureId, current);

		for (Identifier dependency : result.dependencies()) {
			this.getOrComputeResourceStackSignature(resourceManager, dependency);
		}

		boolean rebuild = previous == null
			|| !Objects.equals(previous.definitionId(), current.definitionId())
			|| !Objects.equals(previous.fingerprint(), current.fingerprint());
		this.rebuildPlan.put(atlasTextureId, rebuild);
	}

	public synchronized boolean shouldSkip(Identifier atlasTextureId) {
		if (this.fallbackToFullReload) {
			return false;
		}
		return Boolean.FALSE.equals(this.rebuildPlan.get(atlasTextureId));
	}

	public synchronized void markInconsistent(String reason, Throwable throwable) {
		this.fallbackToFullReload = true;
		for (Identifier atlasTextureId : this.activeAtlases) {
			this.rebuildPlan.put(atlasTextureId, true);
		}
		if (throwable == null) {
			InstantReload.LOGGER.warn("[{}] disabling selective atlas reload this cycle: {}", InstantReload.MOD_ID, reason);
		} else {
			InstantReload.LOGGER.warn(
				"[{}] disabling selective atlas reload this cycle: {}",
				InstantReload.MOD_ID,
				reason,
				throwable
			);
		}
	}

	public void markSyntheticResult(SpriteLoader.StitchResult result, Identifier atlasTextureId) {
		this.syntheticResults.add(result);
		this.debug("marked synthetic stitch result for {}", atlasTextureId);
	}

	public boolean consumeSyntheticResult(SpriteLoader.StitchResult result) {
		return this.syntheticResults.remove(result);
	}

	public synchronized void finishReload(boolean success) {
		boolean committed = false;
		if (success && !this.fallbackToFullReload && this.nextStates.size() == this.expectedAtlasCount) {
			this.commitNextState();
			committed = true;
		}

		this.nextStates.clear();
		this.nextResourceSignatures.clear();
		this.rebuildPlan.clear();
		this.activeAtlases.clear();
		this.atlasesToScan.clear();
		this.syntheticResults.clear();
		this.expectedAtlasCount = 0;
		this.fallbackToFullReload = false;
		this.debug("finish reload (success={}, committed={})", success, committed);
	}

	public void onAtlasLoaderRequested(Identifier definitionId) {
		this.debug("atlas loader requested for {}", definitionId);
	}

	public void onTextureManagerReloadStart() {
		int depth = this.textureReloadDepth.incrementAndGet();
		this.debug("texture manager reload start (depth={})", depth);
	}

	public void onTextureManagerReloadEnd(Throwable throwable) {
		int depth = this.textureReloadDepth.decrementAndGet();
		this.debug("texture manager reload end (depth={}, ok={})", depth, throwable == null);
	}

	private Set<Identifier> detectChangedDependencyResources(ResourceManager resourceManager) {
		Set<Identifier> changed = new HashSet<>();
		for (Identifier resourceId : this.previousResourceToAtlases.keySet()) {
			String current = this.getOrComputeResourceStackSignature(resourceManager, resourceId);
			String previous = this.previousResourceSignatures.get(resourceId);
			if (!Objects.equals(previous, current)) {
				changed.add(resourceId);
			}
		}
		return changed;
	}

	private void commitNextState() {
		this.previousStates.clear();
		this.previousStates.putAll(this.nextStates);

		this.previousResourceToAtlases.clear();
		this.previousResourceSignatures.clear();
		for (Map.Entry<Identifier, AtlasState> entry : this.previousStates.entrySet()) {
			Identifier atlasTextureId = entry.getKey();
			AtlasState state = entry.getValue();

			Identifier definitionPath = this.toDefinitionPath(state.definitionId());
			this.copySignatureIfPresent(definitionPath);

			for (Identifier dependency : state.dependencies()) {
				this.previousResourceToAtlases.computeIfAbsent(dependency, id -> new HashSet<>()).add(atlasTextureId);
				this.copySignatureIfPresent(dependency);
			}
		}
	}

	private void copySignatureIfPresent(Identifier resourceId) {
		String signature = this.nextResourceSignatures.get(resourceId);
		if (signature != null) {
			this.previousResourceSignatures.put(resourceId, signature);
		}
	}

	private Identifier toDefinitionPath(Identifier definitionId) {
		return AtlasHashCalculator.ATLAS_DEFINITION_FINDER.toResourcePath(definitionId);
	}

	private String getOrComputeResourceStackSignature(ResourceManager resourceManager, Identifier resourceId) {
		String cached = this.nextResourceSignatures.get(resourceId);
		if (cached != null) {
			return cached;
		}
		String signature = this.computeResourceStackSignature(resourceManager, resourceId);
		this.nextResourceSignatures.put(resourceId, signature);
		return signature;
	}

	private String computeResourceStackSignature(ResourceManager resourceManager, Identifier resourceId) {
		HashAccumulator accumulator = new HashAccumulator();
		accumulator.updateUtf8(STACK_SIG_VERSION);
		accumulator.updateIdentifier(resourceId);

		List<Resource> stack = resourceManager.getAllResources(resourceId);
		accumulator.updateInt(stack.size());
		for (Resource resource : stack) {
			accumulator.updateUtf8(resource.getResourcePackName());
			if (this.strictResourceSignatures) {
				this.hashResourceBytes(accumulator, resource);
			}
		}
		return accumulator.finish(stack.size()).sha1();
	}

	private void hashResourceBytes(HashAccumulator accumulator, Resource resource) {
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
			throw new UncheckedIOException("Failed to build stack signature for " + resource.getResourcePackName(), exception);
		}
	}

	private void debug(String template, Object... args) {
		if (!this.debug) {
			return;
		}
		InstantReload.LOGGER.info("[{}] " + template, prependModId(args));
	}

	private static Object[] prependModId(Object[] args) {
		Object[] merged = new Object[args.length + 1];
		merged[0] = InstantReload.MOD_ID;
		System.arraycopy(args, 0, merged, 1, args.length);
		return merged;
	}

	private record AtlasState(
		Identifier definitionId,
		AtlasFingerprint fingerprint,
		Set<Identifier> dependencies,
		boolean hasUnresolvedDependencies
	) {
		private AtlasState {
			dependencies = Set.copyOf(dependencies);
		}
	}
}
