import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import net.minecraft.SharedConstants;

public class ProtoCheck
{
    public static void main(String[] args)
    {
        System.out.println("MCPL version: " + MinecraftCodec.CODEC.getMinecraftVersion());
        System.out.println("MCPL protocol: " + MinecraftCodec.CODEC.getProtocolVersion());
        System.out.println("Server version: " + SharedConstants.getCurrentVersion().name());
        System.out.println("Server protocol: " + SharedConstants.getProtocolVersion());
        System.out.println("MCPL MinecraftProtocol default: " + new MinecraftProtocol("x").getCodec().getProtocolVersion());
    }
}
