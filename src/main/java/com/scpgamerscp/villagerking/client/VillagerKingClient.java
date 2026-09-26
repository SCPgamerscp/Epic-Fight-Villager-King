package com.scpgamerscp.villagerking.client;

import com.scpgamerscp.villagerking.VillagerKingMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.client.renderer.patched.entity.PHumanoidRenderer;

@Mod.EventBusSubscriber(modid = VillagerKingMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VillagerKingClient {
    @SubscribeEvent
    public static void registerVanillaRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VillagerKingMod.KING.get(), VillagerKingRenderer::new);
    }

    @SubscribeEvent
    public static void registerEpicFightRenderer(PatchedRenderersEvent.Add event) {
        event.addPatchedEntityRenderer(VillagerKingMod.KING.get(), type ->
                new PHumanoidRenderer<>(Meshes.BIPED, event.getContext(), type));
    }
}
