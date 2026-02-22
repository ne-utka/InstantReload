# InstantReload

`InstantReload` is a client-only Fabric mod for **Minecraft Java 1.21.11** that replaces all-at-once atlas rebuild behavior with a **selective atlas reload pipeline**.

When resource packs are toggled, vanilla typically rebuilds all known atlases.  
This mod detects what actually changed and attempts to rebuild only affected atlases.

## Target Stack

- Minecraft: `1.21.11`
- Loader: `Fabric Loader 0.18.4`
- Mappings: `Yarn 1.21.11+build.4`
- Java: `21+` (tested with newer JDK runtimes too)
- Branch: `fabric-1.21.11`

## What It Does

- Builds an incremental reload plan during `AtlasManager.prepareSharedState(...)`.
- Tracks previous successful atlas state:
  - atlas definition id
  - atlas fingerprint (CRC32 + SHA1 + sprite count)
  - dependency set (`Identifier` resources used by atlas sprite sources)
- Detects changed resources using per-resource stack signatures.
- Rebuilds only candidate atlases.
- For unchanged atlases:
  - returns synthetic `SpriteLoader.StitchResult`
  - skips `SpriteAtlasTexture.create(...)` upload/recreate path
- Preserves vanilla resource reload lifecycle and async model.

## High-Level Architecture

Core packages:

- `neutka.minecraft.reload`
  - `SelectiveAtlasReloadController`  
    Orchestrates plan/commit, fallback behavior, synthetic stitch tracking.
  - `AtlasHashCalculator`  
    Performs deep scan for candidate atlases (definition + sprite inputs).
  - `AtlasScanResult`, `AtlasFingerprint`, `HashAccumulator`, `AtlasReuseFactory`

- `neutka.minecraft.mixin`
  - `AtlasManagerMixin`  
    Prepare/finish orchestration and per-cycle scan flow.
  - `AtlasManagerEntryMixin`  
    Per-atlas skip decision at `Entry.load(...)`.
  - `SpriteAtlasTextureMixin`  
    Cancels GPU recreation for synthetic stitch results.
  - `TextureManagerMixin`  
    Lifecycle tracking hooks for reload cycle boundaries.
  - `AtlasLoaderMixin`  
    Diagnostic entrypoint hook.
  - Accessors:
    - `AtlasManagerEntryView`
    - `SpriteAtlasTextureAccessor`

## Reload Lifecycle

### Prepare Phase

1. Collect atlas metadata (`textureId`, `definitionId`, metadata serializers).
2. Compare previous dependency resource signatures against current signatures.
3. Build candidate set: changed resource -> affected atlas.
4. For candidate atlases only:
   - run deep scan (`AtlasHashCalculator.scan(...)`)
   - compute fingerprint
   - capture new dependency set
5. Produce `rebuildPlan[atlasTextureId] = true/false`.

### Apply Phase

- `AtlasManager$Entry.load(...)`:
  - if `rebuildPlan` says unchanged, return synthetic stitch result from current atlas snapshot
  - else run vanilla load+stitch path
- `SpriteAtlasTexture.create(...)`:
  - canceled only for synthetic stitch results
  - vanilla behavior for changed atlases remains untouched

### Commit

On successful reload:

- commit next atlas states as previous baseline
- rebuild dependency reverse index (`resource -> atlases`)
- persist resource signatures for next cycle

On inconsistency/error:

- mark cycle as fallback
- force rebuild for active atlases
- skip incremental commit

## Safety Guarantees

- No global cancel of `TextureManager.reload()`.
- No breakage of `ResourceReloader` contract.
- Fallback-to-full behavior on any internal inconsistency.
- Mixin hooks are defensive (`try/catch`) around critical selective logic.
- Existing vanilla async prepare/apply flow is preserved.

## Performance Notes

Expected behavior after warm baseline:

- Significant win when few resources change (especially pack toggles touching a small subset).
- Lower win when many atlases are affected.
- First reload after startup behaves close to full reload (baseline creation).

Complexity trend:

- Previous brute-force selective approach: close to full `all-atlas` scan every cycle.
- Current approach: `changed resources -> affected atlases`, with deep hashing only on candidates.

## Runtime Flags

JVM flags:

- `-Dinstantreload.debug=true`  
  Enables internal debug logging.

- `-Dinstantreload.strictSignatures=true`  
  Uses stricter dependency signature mode (reads resource bytes for stack signatures).  
  More robust, but slower.

## Development

Build:

```bash
./gradlew build
```

Run client:

```bash
./gradlew runClient
```

## Current Scope / Limitations

- Client-side only.
- Targets the 1.21.11 Yarn surface used in this branch.
- Atlas dependency extraction relies on sprite source load path behavior.
- Dynamic runtime-generated textures outside atlas dependency graph are intentionally out of scope.

## License

MIT (see `fabric.mod.json` and project license files).

