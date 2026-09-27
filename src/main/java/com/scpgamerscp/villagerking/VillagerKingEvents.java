package com.scpgamerscp.villagerking;

import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import com.scpgamerscp.villagerking.world.KingSummonData;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
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

    @SubscribeEvent
    public void onKingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof VillagerKingEntity king) ||
                !(king.level() instanceof ServerLevel level) ||
                !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;

        ItemStack reward = new ItemStack(Items.POTION);
        PotionUtils.setCustomEffects(reward, List.of(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 12000, 4)));
        reward.setHoverName(Component.translatable("item.villagerking.kings_strength_potion"));
        for (int i = 0; i < 10; i++) {
            event.getDrops().add(new ItemEntity(level, king.getX(), king.getY(), king.getZ(), reward.copy()));
        }
    }
}
