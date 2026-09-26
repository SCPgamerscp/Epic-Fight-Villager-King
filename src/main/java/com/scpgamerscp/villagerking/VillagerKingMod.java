package com.scpgamerscp.villagerking;

import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import com.scpgamerscp.villagerking.epicfight.VillagerKingPatch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;

@Mod(VillagerKingMod.MOD_ID)
public final class VillagerKingMod {
    public static final String MOD_ID = "villagerking";
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);
    public static final RegistryObject<EntityType<VillagerKingEntity>> KING = ENTITIES.register("villager_king", () ->
            EntityType.Builder.of(VillagerKingEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build("villagerking:villager_king"));

    public VillagerKingMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(this::registerPatch);
        modBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(new VillagerKingEvents());
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(KING.get(), VillagerKingEntity.createAttributes().build());
    }

    private void registerPatch(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(KING.get(), entity -> VillagerKingPatch::new);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> Armatures.registerEntityTypeArmature(KING.get(), Armatures.BIPED));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
