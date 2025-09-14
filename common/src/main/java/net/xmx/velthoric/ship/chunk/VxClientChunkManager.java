package net.xmx.velthoric.ship.chunk;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.xmx.velthoric.ship.plot.ShipPlotInfo;
import net.xmx.velthoric.ship.plot.VxClientPlotManager;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VxClientChunkManager {

    private final Map<Long, Queue<Packet<?>>> queuedPackets = new ConcurrentHashMap<>();
    private final ClientPacketListener packetListener;

    public VxClientChunkManager(ClientPacketListener listener) {
        this.packetListener = listener;
    }

    public boolean queueIfNecessary(int chunkX, int chunkZ, Packet<?> packet) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (VxClientPlotManager.getInstance().isShipChunk(chunkX, chunkZ) &&
                VxClientPlotManager.getInstance().getShipInfoForChunk(pos) == null) {
            queuedPackets.computeIfAbsent(pos.toLong(), k -> new ConcurrentLinkedQueue<>()).add(packet);
            return true;
        }
        return false;
    }

    public void onShipDataReceived(UUID shipId) {
        ShipPlotInfo plotInfo = VxClientPlotManager.getInstance().getShipInfoForShip(shipId);
        if (plotInfo == null) return;

        int radius = VxClientPlotManager.getInstance().getPlotRadius(shipId);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                long pos = new ChunkPos(plotInfo.plotCenter().x + x, plotInfo.plotCenter().z + z).toLong();
                Queue<Packet<?>> packets = queuedPackets.remove(pos);
                if (packets != null) {
                    packets.forEach(this::dispatchPacket);
                }
            }
        }
    }

    private void dispatchPacket(Packet<?> packet) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket p) {
            packetListener.handleLevelChunkWithLight(p);
        } else if (packet instanceof ClientboundBlockUpdatePacket p) {
            packetListener.handleBlockUpdate(p);
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket p) {
            packetListener.handleChunkBlocksUpdate(p);
        }
    }
}