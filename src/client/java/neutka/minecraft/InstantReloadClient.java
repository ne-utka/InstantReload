package neutka.minecraft;

import net.fabricmc.api.ClientModInitializer;
import neutka.minecraft.reload.SelectiveAtlasReloadController;

public class InstantReloadClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SelectiveAtlasReloadController.getInstance().setDebug(InstantReload.DEBUG_LOGGING);
		InstantReload.LOGGER.info(
			"[{}] selective atlas reload enabled (debug={})",
			InstantReload.MOD_ID,
			InstantReload.DEBUG_LOGGING
		);
	}
}
