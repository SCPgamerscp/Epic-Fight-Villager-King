package com.scpgamerscp.villagerking.client;

import com.scpgamerscp.villagerking.VillagerKingMod;
import com.scpgamerscp.villagerking.entity.VillagerKingEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public final class VillagerKingRenderer extends MobRenderer<VillagerKingEntity, PlayerModel<VillagerKingEntity>> {
    private static final ResourceLocation SKIN = VillagerKingMod.id("textures/entity/villager_king.png");

    public VillagerKingRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(VillagerKingEntity entity) {
        return SKIN;
    }
}
