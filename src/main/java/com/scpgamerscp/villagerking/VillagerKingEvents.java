package com.scpgamerscp.villagerking;

import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import com.scpgamerscp.villagerking.world.KingSummonData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.damagesource.EpicFightDamageSource;

public final class VillagerKingEvents {
    @SubscribeEvent
    public void onVillagerHurt(LivingHurtEvent event) {
        if (event.getSource() instanceof EpicFightDamageSource source &&
                source.getEntity() instanceof VillagerKingEntity king &&
                source.getAnimation().equals(Animations.THE_GUILLOTINE) &&
                event.getEntity().getHealth() < king.getAttributeValue(Attributes.ATTACK_DAMAGE) * 2.0D) {
            source.setExecute();
        }
        if (!(event.getEntity() instanceof Villager) || event.getAmount() <= 0 ||
                !(event.getSource().getEntity() instanceof ServerPlayer player) ||
                event.getSource().getDirectEntity() != player || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        KingSummonData.get(level).recordHit(player, level);
    }

    @SubscribeEvent
    public void onKingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof VillagerKingEntity king && king.level() instanceof ServerLevel level) {
            KingSummonData.get(level).onKingDeath(king.getUUID());
        }
    }
}
