/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.event;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import baritone.api.event.ILightWatchdog;
import baritone.api.event.events.*;
import baritone.api.event.listener.IGameEventListener;
import net.minecraft.block.BlockState;

import java.util.concurrent.atomic.AtomicBoolean;

public class LightWatchdogListener implements IGameEventListener, ILightWatchdog {

    private final Baritone baritone;
    private AtomicBoolean watching = new AtomicBoolean(true);
    private int ticks;

    public LightWatchdogListener(Baritone baritone) {
        this.baritone = baritone;
    }

    @Override
    public void onTick(TickEvent event) {
        if (watching.get() && BaritoneAPI.getSettings().placeTorchWhileMining.value) {
            ticks++;
            if (ticks % BaritoneAPI.getSettings().checkLightIntervalInTicks.value != 0)
                return;
            int lightLevel = baritone.getPlayerContext().world().getLightLevel(baritone.getPlayerContext().playerFeet());
            if (lightLevel <= BaritoneAPI.getSettings().lightThreshold.value) {
                baritone.getPlaceTorchProcess().requestPlaceTorch();
            }
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {

    }

    @Override
    public void onSendChatMessage(ChatEvent event) {

    }

    @Override
    public void onPreTabComplete(TabCompleteEvent event) {

    }

    @Override
    public void onChunkEvent(ChunkEvent event) {

    }

    @Override
    public void onRenderPass(RenderEvent event) {

    }

    @Override
    public void onWorldEvent(WorldEvent event) {

    }

    @Override
    public void onSendPacket(PacketEvent event) {

    }

    @Override
    public void onReceivePacket(PacketEvent event) {

    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {

    }

    @Override
    public void onPlayerSprintState(SprintStateEvent event) {

    }

    @Override
    public void onBlockInteract(BlockInteractEvent event) {

    }

    @Override
    public void onPlayerDeath() {
        baritone.getPathingBehavior().cancelEverything();
    }

    @Override
    public void onPathEvent(PathEvent event) {
        if (event.equals(PathEvent.CANCELED))
            stopWatching();
    }

    public void startWatching() {
        this.watching.set(true);
    }

    public void stopWatching() {
        this.watching.set(false);
    }
}
