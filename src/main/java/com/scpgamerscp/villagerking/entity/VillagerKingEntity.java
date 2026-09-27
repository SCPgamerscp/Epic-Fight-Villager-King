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
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

public final class VillagerKingEntity extends PathfinderMob {
    private static final double MELEE_ATTACK_RANGE_SQR = 9.0D;
    private static final double SLAM_MAX_CHASE_SPEED = 1.45D;
    private static final double SLAM_BRAKE_DISTANCE_SQR = 2.25D;
    private static final double SLAM_DIVE_TRIGGER_DISTANCE_SQR = 9.0D;
    private static final double SLAM_IMPACT_RADIUS = 6.0D;
    private static final double SLAM_IMPACT_VERTICAL_RADIUS = 3.0D;
    private static final float SLAM_DAMAGE_MULTIPLIER = 4.0F;
    private final ServerBossEvent bossBar = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    @Nullable private UUID ownerId;
    private KingWeapon weapon = KingWeapon.UCHIGATANA;
    private boolean dual;
    private int weaponTicks;
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
                .add(Attributes.MOVEMENT_SPEED, 0.48D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(EpicFightAttributes.STUN_ARMOR.get(), 20.0D);
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
            slamStage = 0;
        }

        VillagerKingPatch patch = EpicFightCapabilities.getEntityPatch(this, VillagerKingPatch.class);
        if (patch == null) return;
        if (slamStage != 0) {
            tickSlam(target);
            return;
        }
        // The server animator accounts for links, speed modifiers, and spear followups.
        // Start another attack only after the whole animation chain has finished.
        if (!patch.isAnimationIdle()) return;
        if (eviscerateAwaitingResult) {
            eviscerateAwaitingResult = false;
            if (patch.getCurrentlyActuallyHitEntities().stream().anyMatch(net.minecraft.world.entity.LivingEntity::isAlive)) {
                patch.play(Animations.EVISCERATE_SECOND);
                return;
            }
        }
        if (distanceToSqr(target) >= 144.0D && onGround()) {
            beginSlam(target);
            return;
        }
        if (!onGround() || distanceToSqr(target) > MELEE_ATTACK_RANGE_SQR) return;

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
    }

    private void beginSlam(net.minecraft.world.entity.LivingEntity target) {
        eviscerateAwaitingResult = false;
        getNavigation().stop();

        Vec3 horizontal = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        double distance = Math.sqrt(horizontal.lengthSqr());
        Vec3 direction = distance > 1.0E-4D ? horizontal.scale(1.0D / distance) : Vec3.ZERO;
        double speed = Math.min(SLAM_MAX_CHASE_SPEED, Math.max(0.6D, distance * 0.08D));

        // Give the king enough airtime to get above distant targets. Horizontal
        // speed is corrected every tick below, so this no longer overshoots the target.
        setDeltaMovement(direction.x * speed, 1.35D, direction.z * speed);
        hasImpulse = true;
        slamStage = 1;
        slamTicks = 0;
    }

    private void tickSlam(net.minecraft.world.entity.LivingEntity target) {
        getNavigation().stop();
        slamTicks++;

        if (slamStage == 1) {
            Vec3 horizontal = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            double horizontalDistanceSqr = horizontal.lengthSqr();
            Vec3 currentMotion = getDeltaMovement();

            if (horizontalDistanceSqr > SLAM_BRAKE_DISTANCE_SQR) {
                double distance = Math.sqrt(horizontalDistanceSqr);
                Vec3 direction = horizontal.scale(1.0D / distance);
                double speed = Math.min(SLAM_MAX_CHASE_SPEED, Math.max(0.25D, distance * 0.10D));
                setDeltaMovement(direction.x * speed, currentMotion.y, direction.z * speed);
            } else {
                // Brake above the target instead of carrying the old 1.4 block/tick
                // velocity through them and starting the slam far behind.
                setDeltaMovement(0.0D, currentMotion.y, 0.0D);
            }
            hasImpulse = true;

            boolean descending = slamTicks >= 8 && getDeltaMovement().y <= 0.0D;
            boolean linedUp = horizontalDistanceSqr <= SLAM_DIVE_TRIGGER_DISTANCE_SQR;

            if ((descending && linedUp) || (descending && slamTicks >= 28) || (onGround() && slamTicks > 3)) {
                startMeteorDive(target);
            }
            return;
        }

        if (slamStage == 2 && ((onGround() && slamTicks >= 4) || slamTicks > 50)) {
            if (level() instanceof ServerLevel serverLevel) slamImpact(serverLevel);
            slamStage = 0;
            slamTicks = 0;
        }
    }

    private void startMeteorDive(net.minecraft.world.entity.LivingEntity target) {
        VillagerKingPatch patch = EpicFightCapabilities.getEntityPatch(this, VillagerKingPatch.class);
        if (patch == null) {
            slamStage = 0;
            return;
        }

        // METEOR_SLAM traces the entity's view direction to choose its landing
        // coordinate. Aim the king directly at the target instead of using a fixed
        // 60-degree pitch, which could send the animation into the ground elsewhere.
        Vec3 from = getEyePosition();
        Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.35D, 0.0D);
        Vec3 towardTarget = aimPoint.subtract(from);
        double horizontalDistance = Math.sqrt(towardTarget.x * towardTarget.x + towardTarget.z * towardTarget.z);

        float yaw = (float)(Math.atan2(towardTarget.z, towardTarget.x) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float)(-Math.atan2(towardTarget.y, Math.max(horizontalDistance, 1.0E-4D)) * 180.0D / Math.PI);

        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        setXRot(Math.max(-89.0F, Math.min(89.0F, pitch)));

        patch.play(Animations.METEOR_SLAM);
        slamStage = 2;
        slamTicks = 0;
    }

    private void slamImpact(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2D, getZ(), 18, 2.5D, 0.25D, 2.5D, 0.12D);
        level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, getSoundSource(), 1.25F, 0.75F);

        VillagerKingPatch patch = EpicFightCapabilities.getEntityPatch(this, VillagerKingPatch.class);
        AABB area = getBoundingBox().inflate(SLAM_IMPACT_RADIUS, SLAM_IMPACT_VERTICAL_RADIUS, SLAM_IMPACT_RADIUS);
        float fallbackDamage = (float)getAttributeValue(Attributes.ATTACK_DAMAGE) * SLAM_DAMAGE_MULTIPLIER;

        for (Player player : level.getEntitiesOfClass(Player.class, area, Player::isAlive)) {
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            if (dx * dx + dz * dz > SLAM_IMPACT_RADIUS * SLAM_IMPACT_RADIUS) continue;

            // The real METEOR_SLAM AttackAnimation has its own collider. Only use
            // the radial impact as a fallback when that collider did not connect.
            if (patch != null && patch.getCurrentlyActuallyHitEntities().contains(player)) continue;

            // Epic Fight attacks temporarily ignore vanilla hurt invulnerability in
            // the same way. Restore the previous value after this one impact attempt.
            int previousInvulnerability = player.invulnerableTime;
            player.invulnerableTime = 0;
            player.hurt(damageSources().mobAttack(this), fallbackDamage);
            player.invulnerableTime = previousInvulnerability;
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
