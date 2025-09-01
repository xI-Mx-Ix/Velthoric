package net.xmx.velthoric.ship.plot;

import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;

public class VxTicketManager {

    public static final TicketType<ChunkPos> PLOT_TICKET =
            TicketType.create("velthoric_plot", Comparator.comparingLong(ChunkPos::toLong), 0);

}