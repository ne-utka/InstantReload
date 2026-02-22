package neutka.minecraft.reload;

import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.CRC32;

final class HashAccumulator {
	private final MessageDigest sha1;
	private final CRC32 crc32 = new CRC32();

	HashAccumulator() {
		try {
			this.sha1 = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("Missing SHA-1 digest implementation", exception);
		}
	}

	void updateIdentifier(Identifier identifier) {
		this.updateUtf8(identifier.toString());
	}

	void updateInt(int value) {
		byte[] buffer = new byte[] {
			(byte)((value >>> 24) & 0xFF),
			(byte)((value >>> 16) & 0xFF),
			(byte)((value >>> 8) & 0xFF),
			(byte)(value & 0xFF)
		};
		this.updateBytes(buffer);
	}

	void updateUtf8(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		this.updateInt(bytes.length);
		this.updateBytes(bytes);
	}

	void updateBytes(byte[] bytes) {
		this.sha1.update(bytes);
		this.crc32.update(bytes, 0, bytes.length);
	}

	void updateBytes(byte[] bytes, int length) {
		this.sha1.update(bytes, 0, length);
		this.crc32.update(bytes, 0, length);
	}

	AtlasFingerprint finish(int spriteCount) {
		return new AtlasFingerprint(this.crc32.getValue(), HexFormat.of().formatHex(this.sha1.digest()), spriteCount);
	}
}
