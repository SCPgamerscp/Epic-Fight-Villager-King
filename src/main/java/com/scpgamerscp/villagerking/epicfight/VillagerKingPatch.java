package com.scpgamerscp.villagerking.epicfight;

import com.scpgamerscp.villagerking.entity.KingWeapon;
import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.property.AnimationProperty.PlaybackSpeedModifier;
import yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.entitypatch.Factions;
import yesman.epicfight.world.capabilities.entitypatch.HumanoidMobPatch;
import yesman.epicfight.world.entity.ai.goal.AnimatedAttackGoal;

public final class VillagerKingPatch extends HumanoidMobPatch<VillagerKingEntity> {
    private static final float ATTACK_PLAYBACK_SPEED = 2.25F;
    private static boolean attackSpeedConfigured;

    public VillagerKingPatch() {
        super(Factions.VILLAGER);
    }

    @Override
    public void onJoinWorld(VillagerKingEntity entity, EntityJoinLevelEvent event) {
        super.onJoinWorld(entity, event);
        configureAttackAnimationSpeed();

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

    /**
     * Epic Fight's AttackAnimation#getPlaySpeed uses the attack-speed stat only for
     * PlayerPatch. Mob patches otherwise play these shared player animations at 1.0x.
     *
     * The king intentionally reuses Epic Fight's player animations, so install a
     * conditional playback modifier on those shared animation assets. The modifier
     * preserves any speed rule Epic Fight already attached to an animation and only
     * adds the 2.25x multiplier when the current entity patch is VillagerKingPatch.
     */
    private static synchronized void configureAttackAnimationSpeed() {
        if (attackSpeedConfigured) return;

        Map<StaticAnimation, PlaybackSpeedModifier> originalModifiers = new IdentityHashMap<>();
        Map<StaticAnimation, Integer> depths = new IdentityHashMap<>();

        for (KingWeapon weapon : KingWeapon.values()) {
            collectAnimations(weapon.normal(false), originalModifiers, depths);
            collectAnimations(weapon.normal(true), originalModifiers, depths);
            collectAnimations(weapon.skills(false), originalModifiers, depths);
            collectAnimations(weapon.skills(true), originalModifiers, depths);
        }
        collectAnimation(Animations.EVISCERATE_SECOND.get().getRealAnimation().get(), 0, originalModifiers, depths);
        collectAnimation(Animations.METEOR_SLAM.get().getRealAnimation().get(), 0, originalModifiers, depths);

        // addProperty propagates to sub-animations. Apply parents before children so
        // each child can finally restore and wrap its own original speed modifier.
        List<StaticAnimation> animations = new ArrayList<>(depths.keySet());
        animations.sort(Comparator.comparingInt(depths::get));

        for (StaticAnimation animation : animations) {
            PlaybackSpeedModifier originalModifier = originalModifiers.get(animation);
            animation.addProperty(StaticAnimationProperty.PLAY_SPEED_MODIFIER,
                    (self, entityPatch, speed, prevElapsedTime, elapsedTime) -> {
                        float resolvedSpeed = originalModifier == null
                                ? speed
                                : originalModifier.modify(self, entityPatch, speed, prevElapsedTime, elapsedTime);
                        return entityPatch instanceof VillagerKingPatch
                                ? resolvedSpeed * ATTACK_PLAYBACK_SPEED
                                : resolvedSpeed;
                    });
        }

        attackSpeedConfigured = true;
    }

    private static void collectAnimations(
            List<AssetAccessor<? extends StaticAnimation>> accessors,
            Map<StaticAnimation, PlaybackSpeedModifier> originalModifiers,
            Map<StaticAnimation, Integer> depths) {
        for (AssetAccessor<? extends StaticAnimation> accessor : accessors) {
            collectAnimation(accessor.get().getRealAnimation().get(), 0, originalModifiers, depths);
        }
    }

    private static void collectAnimation(
            StaticAnimation animation,
            int depth,
            Map<StaticAnimation, PlaybackSpeedModifier> originalModifiers,
            Map<StaticAnimation, Integer> depths) {
        if (!originalModifiers.containsKey(animation)) {
            originalModifiers.put(
                    animation,
                    animation.getProperty(StaticAnimationProperty.PLAY_SPEED_MODIFIER).orElse(null));
        }

        Integer previousDepth = depths.get(animation);
        if (previousDepth != null && previousDepth >= depth) return;
        depths.put(animation, depth);

        for (AssetAccessor<? extends StaticAnimation> subAnimation : animation.getSubAnimations()) {
            collectAnimation(
                    subAnimation.get().getRealAnimation().get(),
                    depth + 1,
                    originalModifiers,
                    depths);
        }
    }
}
