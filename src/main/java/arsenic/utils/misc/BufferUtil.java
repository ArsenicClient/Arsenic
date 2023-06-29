package arsenic.utils.misc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import static org.lwjgl.BufferUtils.createByteBuffer;
import static org.lwjgl.system.MemoryUtil.memSlice;

public class BufferUtil {
    public static ByteBuffer getResourceBytes(String resource, int bufferSize) throws IOException {
        ByteBuffer buffer;
        InputStream source = BufferUtil.class.getResourceAsStream("/assets/arsenic/" + resource);
        ReadableByteChannel rbc;
        try {
            rbc = Channels.newChannel(source);
        } catch (Exception e) {
            return null;
        }

        buffer = createByteBuffer(bufferSize);

        while (true) {
            int bytes = rbc.read(buffer);

            if (bytes == -1) {
                break;
            }

            if (buffer.remaining() == 0) {
                ByteBuffer newBuffer = createByteBuffer(buffer.capacity() * 3 / 2);
                buffer.flip();
                newBuffer.put(buffer);
                buffer = newBuffer;
            }
        }

        buffer.flip();
        return memSlice(buffer);
    }
}
