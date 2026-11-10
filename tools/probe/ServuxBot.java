import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;

/**
 * Minimal Servux protocol probe: connects like a Fabric client, registers the servux channels,
 * asks every data provider for its metadata and prints (and validates) the replies.
 */
public final class ServuxBot
{
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 25566;
    private static final String USER = "ServuxProbe";

    private static final List<String> channels = List.of(
            "servux:structures", "servux:hud_metadata", "servux:entity_data",
            "servux:litematics", "servux:tweaks", "servux:main");

    private static final List<String> log = new ArrayList<>();
    private static final Map<String, Integer> counts = new LinkedHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean loggedIn = false;
    private static int tpX = Integer.MIN_VALUE;
    private static int tpY = 100;
    private static int tpZ;
    private static int splitterExpected = -1;
    private static final ByteArrayOutputStream splitterData = new ByteArrayOutputStream();

    public static void main(String[] args) throws Exception
    {
        if (args.length >= 3)
        {
            tpX = Integer.parseInt(args[0]);
            tpY = Integer.parseInt(args[1]);
            tpZ = Integer.parseInt(args[2]);
        }

        final MinecraftProtocol protocol = new MinecraftProtocol(USER);
        final ClientSession client = ClientNetworkSessionFactory.factory()
                .setRemoteSocketAddress(new InetSocketAddress(HOST, PORT))
                .setProtocol(protocol)
                .create();
        client.setFlag(org.geysermc.mcprotocollib.protocol.MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, true);
        client.setFlag(org.geysermc.mcprotocollib.protocol.MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, true);

        client.addListener(new SessionAdapter()
        {
            @Override
            public void connected(org.geysermc.mcprotocollib.network.event.session.ConnectedEvent event)
            {
                log.add("CONNECTED (transport)");
            }

            @Override
            public void packetError(org.geysermc.mcprotocollib.network.event.session.PacketErrorEvent event)
            {
                log.add("PACKET ERROR: " + event.getPacketClass() + " -> " + event.getCause());
            }

            @Override
            public void packetReceived(Session session, Packet packet)
            {
                if (packet instanceof ClientboundLoginPacket)
                {
                    onLogin(session);
                }
                else if (packet instanceof ClientboundCustomPayloadPacket payload)
                {
                    onPayload(payload);
                }
            }

            @Override
            public void disconnected(org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent event)
            {
                log.add("DISCONNECTED: " + event.getReason() + " cause=" + event.getCause());
            }
        });

        log.add("connecting to " + HOST + ":" + PORT + " as " + USER);
        client.connect();

        final long deadline = System.currentTimeMillis() + 16000L;

        while (System.currentTimeMillis() < deadline)
        {
            Thread.sleep(100);
        }

        report();
        client.disconnect(net.kyori.adventure.text.Component.text("probe done"));
        scheduler.shutdownNow();
        Thread.sleep(500);
        System.exit(0);
    }

    private static void onLogin(Session session)
    {
        if (loggedIn)
        {
            return;
        }

        loggedIn = true;
        log.add("LOGIN: joined, registering " + channels.size() + " servux channels");

        // Fabric clients announce the channels they understand via minecraft:register.
        final String list = String.join(",", channels);
        session.send(new ServerboundCustomPayloadPacket(Key.key("minecraft:register"),
                list.getBytes(StandardCharsets.UTF_8)));

        final boolean navigate = tpX != Integer.MIN_VALUE;

        if (navigate)
        {
            scheduler.schedule(() -> session.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket(
                    "tp " + tpX + " " + tpY + " " + tpZ)), 300, TimeUnit.MILLISECONDS);
        }

        final long structuresAt = navigate ? 2600L : 600L;

        scheduler.schedule(() -> request(session, "servux:structures", payload(3, nbtInts(Map.of("version", 3)))), structuresAt, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> request(session, "servux:hud_metadata", payload(2, nbtInts(Map.of("version", 3)))), 1400, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> request(session, "servux:entity_data", payload(2, nbtInts(Map.of("version", 3)))), 2200, TimeUnit.MILLISECONDS);
        // HUD spawn-data request omitted: that packet uses the compressed DataTag format.
        scheduler.schedule(() -> request(session, "servux:tweaks", payload(2, nbtInts(Map.of("version", 3)))), 3000, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> request(session, "servux:litematics", payload(2, nbtInts(Map.of("version", 2)))), 3800, TimeUnit.MILLISECONDS);
        // The probe is opped by the test harness; place a block entity so the NBT request
        // has something to return.
        scheduler.schedule(() -> session.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket(
                "setblock 30 78 -195 minecraft:chest")), 4400, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> requestBlockEntity(session, 30, 78, -195), 5200, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> requestBlockEntity(session, 30, 78, -195), 5800, TimeUnit.MILLISECONDS);
    }

    private static void request(Session session, String channel, byte[] data)
    {
        log.add("SEND " + channel + " type=" + data[0] + " len=" + data.length);
        session.send(new ServerboundCustomPayloadPacket(Key.key(channel), data));
    }

    /** entity_data block entity request: varint type 3 + packed BlockPos. */
    private static void requestBlockEntity(Session session, int x, int y, int z)
    {
        final long packed = ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(3);
        for (int i = 7; i >= 0; i--)
        {
            out.write((int) (packed >> (i * 8)) & 0xFF);
        }
        request(session, "servux:entity_data", out.toByteArray());
    }

    private static void onPayload(ClientboundCustomPayloadPacket packet)
    {
        final String channel = packet.getChannel().asString();
        final byte[] data = packet.getData();
        counts.merge(channel, 1, Integer::sum);

        final int type = data.length > 0 ? (data[0] & 0xFF) : -1;
        final StringBuilder line = new StringBuilder("RECV ")
                .append(channel).append(" type=").append(type)
                .append(" len=").append(data.length);

        if (channel.equals("servux:structures") && type == 2 && data.length > 1)
        {
            int offset = 1;

            if (splitterExpected < 0)
            {
                final int[] result = readVarInt(data, offset);
                splitterExpected = result[0];
                offset = result[1];
            }

            splitterData.write(data, offset, data.length - offset);

            if (splitterData.size() >= splitterExpected)
            {
                line.append(" splitterComplete=").append(splitterData.size()).append('/').append(splitterExpected);
                splitterExpected = -1;
                splitterData.reset();
            }
            else
            {
                line.append(" splitterPart=").append(splitterData.size()).append('/').append(splitterExpected);
            }
        }
        else if (channel.equals("servux:structures") && type == 1 && data.length > 2)
        {
            line.append(" nbt=").append(nbtKeys(data, 1));
        }
        else if (channel.contains("hud_metadata") && type == 1)
        {
            line.append(" nbt=").append(nbtKeys(data, 1));
        }
        else if (channel.contains("entity_data") && type == 1)
        {
            line.append(" nbt=").append(nbtKeys(data, 1));
        }
        else if (channel.contains("entity_data") && type == 5)
        {
            line.append(" nbt=").append(nbtKeys(data, 9));
        }

        log.add(line.toString());
    }

    private static int[] readVarInt(byte[] data, int offset)
    {
        int value = 0;
        int position = 0;

        while (true)
        {
            final byte current = data[offset++];
            value |= (current & 0x7F) << position;

            if ((current & 0x80) == 0)
            {
                return new int[]{value, offset};
            }

            position += 7;
        }
    }

    private static void report()
    {
        System.out.println("===== SERVUX PROBE LOG =====");
        log.forEach(System.out::println);
        System.out.println("===== PAYLOAD COUNTS =====");
        counts.forEach((k, v) -> System.out.println(k + " = " + v));
        System.out.println("RESULT loggedIn=" + loggedIn + " channels=" + counts.size());
    }

    // ---- tiny helpers: Servux payloads are varint type + vanilla (nameless) NBT ----

    private static byte[] payload(int type, byte[] nbt)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(type);
        out.writeBytes(nbt);
        return out.toByteArray();
    }

    /** Writes a nameless root compound containing int values. */
    private static byte[] nbtInts(Map<String, Integer> values)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(10);
        values.forEach((name, value) ->
        {
            try
            {
                out.write(3);
                writeUtf(out, name);
                out.write((value >>> 24) & 0xFF);
                out.write((value >>> 16) & 0xFF);
                out.write((value >>> 8) & 0xFF);
                out.write(value & 0xFF);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        });
        out.write(0);
        return out.toByteArray();
    }

    private static void writeUtf(ByteArrayOutputStream out, String s)
    {
        final byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.write((b.length >>> 8) & 0xFF);
        out.write(b.length & 0xFF);
        out.writeBytes(b);
    }

    /** Reads the top-level keys of a nameless compound starting at {@code offset}. */
    private static String nbtKeys(byte[] data, int offset)
    {
        try
        {
            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, offset, data.length - offset));
            final byte rootType = in.readByte();

            if (rootType != 10)
            {
                return "rootType=" + rootType;
            }

            final List<String> keys = new ArrayList<>();

            while (true)
            {
                final byte type = in.readByte();

                if (type == 0)
                {
                    break;
                }

                final int len = in.readUnsignedShort();
                final byte[] nameBytes = new byte[len];
                in.readFully(nameBytes);
                keys.add(new String(nameBytes, StandardCharsets.UTF_8) + ":" + type);
                skipPayload(in, type);
            }

            return keys.toString();
        }
        catch (Exception e)
        {
            return "unparsed(" + e.getMessage() + ")";
        }
    }

    private static void skipPayload(DataInputStream in, int type) throws Exception
    {
        switch (type)
        {
            case 1 -> in.skipBytes(1);
            case 2 -> in.skipBytes(2);
            case 3, 5 -> in.skipBytes(4);
            case 4, 6 -> in.skipBytes(8);
            case 7 -> in.skipBytes(4 * in.readInt());
            case 8 -> in.skipBytes(in.readUnsignedShort());
            case 9 -> { int t = in.readByte(); int n = in.readInt(); for (int i = 0; i < n; i++) skipPayload(in, t); }
            case 10 -> { while (true) { int t = in.readByte(); if (t == 0) break; in.skipBytes(in.readUnsignedShort()); skipPayload(in, t); } }
            case 11 -> in.skipBytes(4 * in.readInt());
            case 12 -> in.skipBytes(8 * in.readInt());
            default -> throw new IllegalStateException("nbt type " + type);
        }
    }
}
