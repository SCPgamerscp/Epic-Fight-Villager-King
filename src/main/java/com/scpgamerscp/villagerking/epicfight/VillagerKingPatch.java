package com.scpgamerscp.villagerking.epicfight;

import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;

public final class VillagerKingPatch extends HumanoidMobPatch<VillagerKingEntity> {
    public VillagerKingPatch() {
        super(Factions.VILLAGER);
    }

    @Override
    public void onJoinWorld(VillagerKingEntity entity, EntityJoinLevelEvent event) {
        super.onJoinWorld(entity, event);
        // Epic Fight normally builds this value on equipment changes. A newly
        // spawned or reloaded boss needs its base stun armor before the first hit.
        float armor = getStunArmor();
        stunTimeReductionDefault = armor / (armor + 7.5F);
        stunTimeReduction = stunTimeReductionDefault;
    }

    @Override
    protected void initAI() {
        super.initAI();
        // The boss owns the exact 3-combo / 6-skill schedule. Keep Epic Fight chasing AI only.
        List<WrappedGoal> attacks = new ArrayList<>();
        for (WrappedGoal goal : original.goalSelector.getAvailableGoals()) {
            if (goal.getGoal() instanceof AnimatedAttackGoal<?>) attacks.add(goal);
        }
        attacks.forEach(goal -> original.goalSelector.removeGoal(goal.getGoal()));
    }

    @Override
    public void initAnimator(Animator animator) {
        super.initAnimator(animator);
        animator.addLivingAnimation(LivingMotions.IDLE, Animations.BIPED_IDLE);
        animator.addLivingAnimation(LivingMotions.WALK, Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.CHASE, Animations.BIPED_WALK);
        animator.addLivingAnimation(LivingMotions.FALL, Animations.BIPED_FALL);
        animator.addLivingAnimation(LivingMotions.DEATH, Animations.BIPED_DEATH);
    }

    @Override
    public void updateMotion(boolean considerInaction) {
        commonAggressiveMobUpdateMotion(considerInaction);
    }

    public void play(AssetAccessor<? extends StaticAnimation> animation) {
        playAnimationSynchronized(animation, 0.0F);
    }

    public boolean isAnimationIdle() {
        return getServerAnimator().getPlayerFor(null).isEmpty();
    }
}
