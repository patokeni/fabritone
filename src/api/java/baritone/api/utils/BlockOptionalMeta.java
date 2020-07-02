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

package baritone.api.utils;

import baritone.api.BaritoneAPI;
import baritone.api.utils.accessor.IItemStack;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootManager;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.LootConditionManager;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.resource.*;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BlockOptionalMeta {

    private final Block block;
    private final Set<BlockState> blockstates;
    private final ImmutableSet<Integer> stateHashes;
    private final ImmutableSet<Integer> stackHashes;
    private static final Pattern pattern = Pattern.compile("^(.+?)(?::(\\d+))?$");
    private static LootManager manager;
    private static LootConditionManager predicate = new LootConditionManager();
    private static Map<Block, List<Item>> drops = new HashMap<>();

    public BlockOptionalMeta(Block block) {
        this.block = block;
        this.blockstates = getStates(block);
        this.stateHashes = getStateHashes(blockstates);
        this.stackHashes = getStackHashes(blockstates);
    }

    public BlockOptionalMeta(String selector) {
        Matcher matcher = pattern.matcher(selector);

        if (!matcher.find()) {
            throw new IllegalArgumentException("invalid block selector");
        }

        MatchResult matchResult = matcher.toMatchResult();

        block = BlockUtils.stringToBlockRequired(matchResult.group(1));
        blockstates = getStates(block);
        stateHashes = getStateHashes(blockstates);
        stackHashes = getStackHashes(blockstates);
    }

    private static Set<BlockState> getStates(Block block) {
        return new HashSet<>(block.getStateManager().getStates());
    }

    private static ImmutableSet<Integer> getStateHashes(Set<BlockState> blockstates) {
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .map(BlockState::hashCode)
                        .toArray(Integer[]::new)
        );
    }

    private static ImmutableSet<Integer> getStackHashes(Set<BlockState> blockstates) {
        //noinspection ConstantConditions
        return ImmutableSet.copyOf(
                blockstates.stream()
                        .flatMap(state -> {
                                    List<Item> originDrops = Lists.newArrayList(), dropData = drops(state.getBlock());
                                    originDrops.add(state.getBlock().asItem());
                                    if (dropData != null && !dropData.isEmpty()) {
                                        originDrops.addAll(dropData);
                                    }
                                    return originDrops.stream().map(item -> new ItemStack(item ,1));
                                }
                        )
                        .map(stack -> ((IItemStack) (Object) stack).getBaritoneHash())
                        .toArray(Integer[]::new)
        );
    }

    public Block getBlock() {
        return block;
    }

    public boolean matches(Block block) {
        return block == this.block;
    }

    public boolean matches(BlockState blockstate) {
        // Note: BlockState is somehow null in 1.16+, remedy it by setting to default state
        blockstate = (blockstate == null ? block.getDefaultState() : blockstate);

        Block block = blockstate.getBlock();
        return block == this.block && stateHashes.contains(blockstate.hashCode());
    }

    public boolean matches(ItemStack stack) {
        //noinspection ConstantConditions
        int hash = ((IItemStack) (Object) stack).getBaritoneHash();

        hash -= stack.getDamage();

        return stackHashes.contains(hash);
    }

    @Override
    public String toString() {
        return String.format("BlockOptionalMeta{block=%s}", block);
    }

    public BlockState getAnyBlockState() {
        if (blockstates.size() > 0) {
            return blockstates.iterator().next();
        }

        return null;
    }

    public static LootManager getManager() {
        if (manager == null) {
            ResourcePackManager rpl = new ResourcePackManager(ResourcePackProfile::new, new VanillaDataPackProvider(), new FileResourcePackProvider(Helper.mc.getResourcePackDir(), ResourcePackSource.PACK_SOURCE_WORLD));
            rpl.scanPacks();
            List<ResourcePack> thePacks = new ArrayList<>();

            while (rpl.getEnabledProfiles() != null && rpl.getEnabledProfiles().iterator().hasNext()) {
                ResourcePack thePack = rpl.getEnabledProfiles().iterator().next().createResourcePack();
                thePacks.add(thePack);
            }
            ReloadableResourceManager resourceManager = new ReloadableResourceManagerImpl(ResourceType.SERVER_DATA);
            manager = new LootManager(predicate);
            resourceManager.registerListener(manager);
            try {
                resourceManager.beginReload(new ThreadPerTaskExecutor(Thread::new), new ThreadPerTaskExecutor(Thread::new), thePacks, CompletableFuture.completedFuture(Unit.INSTANCE)).get();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        }
        return manager;
    }

    public static LootConditionManager getPredicateManager() {
        return predicate;
    }

    private static synchronized List<Item> drops(Block block) {
        if (!drops.containsKey(block)) {
            Identifier lootTableLocation = block.getLootTableId();
            if (lootTableLocation == LootTables.EMPTY) {
                return Collections.emptyList();
            } else if (Helper.mc.getServer() != null) {
                IntegratedServer server = Helper.mc.getServer();
                drops.put(block, getManager().getTable(lootTableLocation).generateLoot(
                        new LootContext.Builder(server.getWorld(BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext().world().getRegistryKey()))
                                .random(new Random())
                                .parameter(LootContextParameters.POSITION, BlockPos.ORIGIN)
                                .parameter(LootContextParameters.TOOL, ItemStack.EMPTY)
                                .optionalParameter(LootContextParameters.BLOCK_ENTITY, null)
                                .parameter(LootContextParameters.BLOCK_STATE, block.getDefaultState())
                                .build(LootContextTypes.BLOCK)).stream().map(ItemStack::getItem).collect(Collectors.toList()));
                return drops.get(block);
            } else {
                List<Item> items = new ArrayList<>();

                // the other overload for generate doesnt work in forge because forge adds code that requires a non null world
                getManager().getTable(lootTableLocation).generateLoot(
                    new LootContext.Builder(null)
                        .random(new Random())
                        .parameter(LootContextParameters.POSITION, BlockPos.ORIGIN)
                        .parameter(LootContextParameters.TOOL, ItemStack.EMPTY)
                        .optionalParameter(LootContextParameters.BLOCK_ENTITY, null)
                        .parameter(LootContextParameters.BLOCK_STATE, block.getDefaultState())
                        .build(LootContextTypes.BLOCK),
                    stack -> items.add(stack.getItem())
                );
                return items;
            }
        } else if (drops.get(block) != null && !drops.get(block).isEmpty()) {
            return drops.get(block);
        } else {
            return Lists.newArrayList();
        }
    }
}
