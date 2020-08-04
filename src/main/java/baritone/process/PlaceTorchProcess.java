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

package baritone.process;

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlaceTorchProcess implements IBaritoneProcess {
    public static final int PLACE_ON_RIGHT = 0;
    public static final int PLACE_ON_LEFT = 1;
    public static final int PLACE_ON_FEET = 2;

    private final Baritone baritone;
    private final IPlayerContext ctx;
    private final AtomicBoolean shouldPlaceTorch = new AtomicBoolean(false);

    private boolean hasWatched = true;

    public PlaceTorchProcess(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.baritone.getPathingControlManager().registerProcess(this);
    }

    @Override
    public boolean isActive() {
        return this.shouldPlaceTorch.get();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        int torch = findTorch();
        if (torch != -1) {
            baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
            baritone.getInputOverrideHandler().clearAllKeys();
            if (hasWatched) {
                ctx.player().inventory.selectedSlot = torch;
                ctx.playerController().syncHeldItem();
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                onLostControl();
                hasWatched = false;
            } else {
                switch (BaritoneAPI.getSettings().placeTorchMode.value) {
                    case 2:
                        baritone.getLookBehavior().updateTarget(new Rotation(0, 90), true);
                    case 1:
                        baritone.getLookBehavior().updateTarget(new Rotation(Rotation.normalizeYaw(ctx.player().yaw - 90), 0), true);
                        // fallback
                        if (!ctx.getSelectedBlock().isPresent()) {
                            baritone.getLookBehavior().updateTarget(new Rotation(0, 90), true);
                        }
                        break;
                    case 0:
                    default:
                        baritone.getLookBehavior().updateTarget(new Rotation(Rotation.normalizeYaw(ctx.player().yaw + 90), 0), true);
                        // fallback
                        if (!ctx.getSelectedBlock().isPresent()) {
                            baritone.getLookBehavior().updateTarget(new Rotation(0, 90), true);
                        }
                        break;
                }
                hasWatched = true;
            }
        }
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public void onLostControl() {
        this.shouldPlaceTorch.set(false);
    }

    @Override
    public String displayName0() {
        return "Place Torch";
    }

    public int findTorch() {
        List<ItemStack> inventory = ctx.player().inventory.main;
        for (int i = 0;i < 9;i++) {
            if (inventory.get(i).getItem().equals(Items.TORCH)) {
                return i;
            }
        }
        return -1;
    }

    public void requestPlaceTorch() {
        this.shouldPlaceTorch.set(true);
    }
}
