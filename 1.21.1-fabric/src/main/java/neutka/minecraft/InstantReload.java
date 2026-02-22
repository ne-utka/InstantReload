package neutka.minecraft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InstantReload {
	public static final String MOD_ID = "instantreload";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final boolean DEBUG_LOGGING = Boolean.getBoolean("instantreload.debug");

	private InstantReload() {
	}
}
