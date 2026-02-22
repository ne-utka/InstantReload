package neutka.minecraft.reload;

import net.minecraft.util.Identifier;

import java.util.Set;

public record AtlasScanResult(AtlasFingerprint fingerprint, Set<Identifier> dependencies, boolean hasUnresolvedDependencies) {
}
