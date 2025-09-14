package net.xmx.velthoric.mixin.util.ship;

import net.xmx.velthoric.ship.chunk.VxClientChunkManager;

public interface IVxClientPacketListener {
    VxClientChunkManager velthoric$getSeamlessManager();
}