package com.scpgamerscp.villagerking.entity;

import com.scpgamerscp.villagerking.epicfight.VillagerKingPatch;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.animation.SynchedAnimationVariableKeys;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class VillagerKingEntity extends PathfinderMob {
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    @Nullable private UUID ownerId;
    private KingWeapon weapon = KingWeapon.UCHIGATANA;
    private boolean dual;
    private int weaponTicks;
    private int busyTicks;
    private int comboIndex;
    private int comboRounds;
    private int skillsRemaining;
    private int skillIndex;
    private int slamStage;
    private int slamTicks;
    private int landingProtectionTicks;
    private boolean combatStarted;
    private boolean eviscerateAwaitingResult;

    public VillagerKingEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 100;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0D)
                .add(Attributes.ARMOR, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isAlive()) return;
        bossBar.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 100 == 0 || !hasEffect(MobEffects.REGENERATION)) {
            addEffect(new MobEffectInstance(MobEffects.REGENERATION, 220, 2, true, false));
        }
        if (getMainHandItem().isEmpty()) equipWeapon(weapon);
        if (landingProtectionTicks > 0) landingProtectionTicks--;

        var target = getTarget();
        if (target == null || !target.isAlive()) {
            if (onGround()) slamStage = 0;
            eviscerateAwaitingResult = false;
            return;
        }
        if (!combatStarted) {
            combatStarted = true;
            weaponTicks = 0;
        }

        if (++weaponTicks >= 600) {
            if (slamStage != 0) landingProtectionTicks = 80;
            KingWeapon[] choices = KingWeapon.values();
            equipWeapon(choices[(weapon.ordinal() + 1 + getRandom().nextInt(choices.length - 1)) % choices.length]);
            weaponTicks = 0;
            busyTicks = 0;
            slamStage = 0;
        }

        if (slamStage != 0) {
            tickSlam(target);
            return;
        }
        if (distanceToSqr(target) >= 144.0D && onGround()) {
            beginSlam(target);
            return;
        }
        if (busyTicks > 0) {
            busyTicks--;
            return;
        }
        VillagerKingPatch patch = EpicFightCapabilities.getEntityPatch(this, VillagerKingPatch.class);
        if (patch == null) return;
        if (eviscerateAwaitingResult) {
            eviscerateAwaitingResult = false;
            if (patch.getCurrentlyActuallyHitEntities().stream().anyMatch(net.minecraft.world.entity.LivingEntity::isAlive)) {
                patch.play(Animations.EVISCERATE_SECOND);
                busyTicks = Math.max(6, (int)Math.ceil(Animations.EVISCERATE_SECOND.get().getTotalTime() * 20.0F) + 4);
                return;
            }
        }
        if (!onGround() || distanceToSqr(target) > 25.0D) return;

        getNavigation().stop();
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        AssetAccessor<? extends StaticAnimation> animation;
        boolean skillAttack = comboRounds >= 3;
        if (comboRounds < 3) {
            List<AssetAccessor<? extends StaticAnimation>> combo = weapon.normal(dual);
            animation = combo.get(comboIndex++);
            if (comboIndex == combo.size()) {
                comboIndex = 0;
                comboRounds++;
            }
        } else {
            List<AssetAccessor<? extends StaticAnimation>> skills = weapon.skills(dual);
            animation = skills.get(skillIndex++ % skills.size());
            if (--skillsRemaining <= 0) {
                comboRounds = 0;
                comboIndex = 0;
                skillIndex = 0;
                skillsRemaining = 6;
            }
        }
        if (weapon == KingWeapon.DAGGER && dual && skillAttack) {
            patch.getAnimator().getVariables().put(SynchedAnimationVariableKeys.TARGET_ENTITY.get(), animation, target.getId());
        }
        eviscerateAwaitingResult = weapon == KingWeapon.DAGGER && !dual && skillAttack;
        patch.play(animation);
        float animationTime = animation.get().getTotalTime();
        if (weapon == KingWeapon.SPEAR && skillAttack) {
            animationTime += Animations.GRASPING_SPIRAL_SECOND.get().getTotalTime();
        }
        busyTicks = Math.max(6, (int)Math.ceil(animationTime * 20.0F) + 4);
    }

    private void beginSlam(net.minecraft.world.entity.LivingEntity target) {
        eviscerateAwaitingResult = false;
        Vec3 direction = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        getNavigation().stop();
        setDeltaMovement(direction.x * 1.4D, 1.05D, direction.z * 1.4D);
        hasImpulse = true;
        slamStage = 1;
        slamTicks = 0;
    }

    private void tickSlam(net.minecraft.world.entity.LivingEntity target) {
        getNavigation().stop();
        slamTicks++;
        if (slamStage == 1) {
            Vec3 towardTarget = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            if (towardTarget.lengthSqr() > 4.0D) {
                Vec3 direction = towardTarget.normalize();
                setDeltaMovement(direction.x * 1.4D, getDeltaMovement().y, direction.z * 1.4D);
                hasImpulse = true;
            }
        }
        if (slamStage == 1 && (slamTicks >= 8 && getDeltaMovement().y <= 0.0D || onGround() && slamTicks > 3)) {
            VillagerKingPatch patch = EpicFightCapabilities.getEntityPatch(this, VillagerKingPatch.class);
            if (patch != null) {
                getLookControl().setLookAt(target, 30.0F, 90.0F);
                Vec3 towardTarget = target.position().subtract(position());
                setYRot((float)(Math.atan2(towardTarget.z, towardTarget.x) * 180.0D / Math.PI) - 90.0F);
                setYHeadRot(getYRot());
                setXRot(60.0F);
                patch.play(Animations.METEOR_SLAM);
            }
            slamStage = 2;
            slamTicks = 0;
        } else if (slamStage == 2 && (onGround() && slamTicks > 3 || slamTicks > 60)) {
            if (level() instanceof ServerLevel serverLevel) slamImpact(serverLevel);
            slamStage = 0;
            busyTicks = 4;
        }
    }

    private void slamImpact(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(), 12, 2.0D, 0.1D, 2.0D, 0.1D);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.0F, 0.8F);
        AABB area = getBoundingBox().inflate(4.0D, 1.0D, 4.0D);
        for (Player player : level.getEntitiesOfClass(Player.class, area)) {
            if (hasLineOfSight(player)) player.hurt(damageSources().mobAttack(this), (float)getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }

    private void equipWeapon(KingWeapon next) {
        weapon = next;
        dual = (next == KingWeapon.SWORD || next == KingWeapon.DAGGER) && getRandom().nextBoolean();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(next.item()));
        setItemSlot(EquipmentSlot.OFFHAND, next == KingWeapon.GLOVE || dual ? new ItemStack(next.item()) : ItemStack.EMPTY);
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        setDropChance(EquipmentSlot.OFFHAND, 0.0F);
        comboIndex = 0;
        comboRounds = 0;
        skillIndex = 0;
        skillsRemaining = 6;
        eviscerateAwaitingResult = false;
    }

    @Nullable
    public UUID getOwnerId() { return ownerId; }

    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("Summoner", ownerId);
        tag.putString("Weapon", weapon.name());
        tag.putBoolean("Dual", dual);
        tag.putInt("WeaponTicks", weaponTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Summoner")) ownerId = tag.getUUID("Summoner");
        try { weapon = KingWeapon.valueOf(tag.getString("Weapon")); }
        catch (IllegalArgumentException ignored) { weapon = KingWeapon.UCHIGATANA; }
        dual = tag.getBoolean("Dual");
        weaponTicks = Math.max(0, Math.min(599, tag.getInt("WeaponTicks")));
        skillsRemaining = 6;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return slamStage == 0 && landingProtectionTicks == 0 && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
