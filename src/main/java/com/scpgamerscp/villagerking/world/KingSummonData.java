package com.scpgamerscp.villagerking.world;

import com.scpgamerscp.villagerking.VillagerKingMod;
import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

public final class KingSummonData extends SavedData {
    private static final String FILE_NAME = "villagerking_summons";
    private final Map<UUID, Progress> progressByPlayer = new HashMap<>();
    private UUID activeKing;

    public static KingSummonData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(KingSummonData::load, KingSummonData::new, FILE_NAME);
    }

    public static KingSummonData load(CompoundTag tag) {
        KingSummonData data = new KingSummonData();
        if (tag.hasUUID("ActiveKing")) data.activeKing = tag.getUUID("ActiveKing");
        ListTag entries = tag.getList("Players", 10);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("Player")) {
                Progress p = new Progress();
                p.hits = Math.max(0, Math.min(19, entry.getInt("Hits")));
                data.progressByPlayer.put(entry.getUUID("Player"), p);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (activeKing != null) tag.putUUID("ActiveKing", activeKing);
        ListTag entries = new ListTag();
        progressByPlayer.forEach((player, progress) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putInt("Hits", progress.hits);
            entries.add(entry);
        });
        tag.put("Players", entries);
        return tag;
    }

    public void recordHit(ServerPlayer player, ServerLevel level) {
        if (activeKing != null) return;
        Progress progress = progressByPlayer.computeIfAbsent(player.getUUID(), unused -> new Progress());
        if (progress.hits < 19) {
            progress.hits++;
            setDirty();
            return;
        }

        VillagerKingEntity king = VillagerKingMod.KING.get().create(level);
        if (king == null) return;
        if (!placeNear(king, player, level)) return;
        king.setOwnerId(player.getUUID());
        king.setTarget(player);
        king.setPersistenceRequired();
        if (level.addFreshEntity(king)) {
            progress.hits = 0;
            activeKing = king.getUUID();
            setDirty();
        }
    }

    private boolean placeNear(VillagerKingEntity king, ServerPlayer player, ServerLevel level) {
        BlockPos origin = player.blockPosition();
        int[][] offsets = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] offset : offsets) {
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos pos = origin.offset(offset[0], dy, offset[1]);
                king.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
                if (level.noCollision(king) && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void onKingDeath(UUID kingId) {
        if (kingId.equals(activeKing)) {
            activeKing = null;
            progressByPlayer.values().forEach(progress -> progress.hits = 0);
            setDirty();
        }
    }

    private static final class Progress {
        private int hits;
    }
}
