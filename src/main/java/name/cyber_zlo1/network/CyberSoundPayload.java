package name.cyber_zlo1.network;

import name.cyber_zlo1.Cyber;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server-to-client custom payload triggering a synthesized Cyberpunk 2077 OpenAL sound effect
 * on the receiving client via {@code CyberOpenALSynth}.
 */
public record CyberSoundPayload(String soundId, float volume, float pitch) implements CustomPayload {
	public static final CustomPayload.Id<CyberSoundPayload> ID =
			new CustomPayload.Id<>(Cyber.id("play_cyber_sound"));

	public static final PacketCodec<RegistryByteBuf, CyberSoundPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, CyberSoundPayload::soundId,
			PacketCodecs.FLOAT, CyberSoundPayload::volume,
			PacketCodecs.FLOAT, CyberSoundPayload::pitch,
			CyberSoundPayload::new
	);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
