package lytblu7.autonexus.common;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class PacketIO {
    
    public static byte[] encode(NexusPacket packet) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(packet.getType());
        out.writeUTF(packet.getPayload());
        return out.toByteArray();
    }

    public static NexusPacket decode(byte[] data) {
        if (data == null) return null;
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(data);
            String type = in.readUTF();
            String payload = in.readUTF();
            return new NexusPacket(type, payload);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
