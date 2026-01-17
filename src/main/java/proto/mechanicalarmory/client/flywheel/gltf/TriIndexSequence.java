package proto.mechanicalarmory.client.flywheel.gltf;

import dev.engine_room.flywheel.api.model.IndexSequence;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public final class TriIndexSequence implements IndexSequence {
	private final ByteBuffer binary;

	private final int indexCount;

	public TriIndexSequence(ByteBuffer binary, int indexCount) {
		this.binary = binary;
		this.indexCount = indexCount;
	}

	@Override
	public void fill(long ptr, int count) {
		ShortBuffer sb = binary.asShortBuffer();
		for (int i = 0; i < indexCount; i++) {
			MemoryUtil.memPutInt(ptr + i * 4L, sb.get(i));
		}
	}

	public ByteBuffer getBinary() {
		return binary;
	}
}