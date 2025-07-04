package net.xmx.xbullet.physics.object.riding;

import com.github.stephengold.joltjni.Vec3;
import net.minecraft.world.entity.player.Player;
import net.xmx.xbullet.physics.object.riding.seat.Seat;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Interface für Physikobjekte, die Sitze haben und von Spielern geritten werden können.
 */
public interface IRideable {
    
    Collection<Seat> getSeats();

    @Nullable
    Seat getSeat(String seatId);
    
    void addSeat(Seat seat);
    
    void removeSeat(String seatId);

    void tryStartRiding(Player player, Vec3 hitPoint, Vec3 hitNormal);
}