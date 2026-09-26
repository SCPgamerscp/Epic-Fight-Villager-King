package com.scpgamerscp.villagerking.entity;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.item.EpicFightItems;

/** The lists below use Epic Fight's player animations, not mob approximations. */
public enum KingWeapon {
    UCHIGATANA, GREATSWORD, SPEAR, TACHI, LONGSWORD, DAGGER, GLOVE, SWORD, AXE, TRIDENT;

    public Item item() {
        return switch (this) {
            case UCHIGATANA -> EpicFightItems.UCHIGATANA.get();
            case GREATSWORD -> EpicFightItems.NETHERITE_GREATSWORD.get();
            case SPEAR -> EpicFightItems.NETHERITE_SPEAR.get();
            case TACHI -> EpicFightItems.NETHERITE_TACHI.get();
            case LONGSWORD -> EpicFightItems.NETHERITE_LONGSWORD.get();
            case DAGGER -> EpicFightItems.NETHERITE_DAGGER.get();
            case GLOVE -> EpicFightItems.GLOVE.get();
            case SWORD -> Items.NETHERITE_SWORD;
            case AXE -> Items.NETHERITE_AXE;
            case TRIDENT -> Items.TRIDENT;
        };
    }

    public List<AssetAccessor<? extends StaticAnimation>> normal(boolean dual) {
        return switch (this) {
            case UCHIGATANA -> List.of(Animations.UCHIGATANA_AUTO1, Animations.UCHIGATANA_AUTO2, Animations.UCHIGATANA_AUTO3);
            case GREATSWORD -> List.of(Animations.GREATSWORD_AUTO1, Animations.GREATSWORD_AUTO2);
            case SPEAR -> List.of(Animations.SPEAR_TWOHAND_AUTO1, Animations.SPEAR_TWOHAND_AUTO2);
            case TACHI -> List.of(Animations.TACHI_AUTO1, Animations.TACHI_AUTO2, Animations.TACHI_AUTO3);
            case LONGSWORD -> List.of(Animations.LONGSWORD_AUTO1, Animations.LONGSWORD_AUTO2, Animations.LONGSWORD_AUTO3);
            case DAGGER -> dual ? List.of(Animations.DAGGER_DUAL_AUTO1, Animations.DAGGER_DUAL_AUTO2, Animations.DAGGER_DUAL_AUTO3, Animations.DAGGER_DUAL_AUTO4)
                    : List.of(Animations.DAGGER_AUTO1, Animations.DAGGER_AUTO2, Animations.DAGGER_AUTO3);
            case GLOVE -> List.of(Animations.FIST_AUTO1, Animations.FIST_AUTO2, Animations.FIST_AUTO3);
            case SWORD -> dual ? List.of(Animations.SWORD_DUAL_AUTO1, Animations.SWORD_DUAL_AUTO2, Animations.SWORD_DUAL_AUTO3)
                    : List.of(Animations.SWORD_AUTO1, Animations.SWORD_AUTO2, Animations.SWORD_AUTO3);
            case AXE -> List.of(Animations.AXE_AUTO1, Animations.AXE_AUTO2);
            case TRIDENT -> List.of(Animations.TRIDENT_AUTO1, Animations.TRIDENT_AUTO2, Animations.TRIDENT_AUTO3);
        };
    }

    public List<AssetAccessor<? extends StaticAnimation>> skills(boolean dual) {
        return switch (this) {
            case UCHIGATANA -> List.of(Animations.BATTOJUTSU);
            case GREATSWORD -> List.of(Animations.STEEL_WHIRLWIND);
            case SPEAR -> List.of(Animations.GRASPING_SPIRAL_FIRST, Animations.GRASPING_SPIRAL_SECOND);
            case TACHI -> List.of(Animations.RUSHING_TEMPO1, Animations.RUSHING_TEMPO2, Animations.RUSHING_TEMPO3);
            case LONGSWORD -> List.of(Animations.SHARP_STAB);
            case DAGGER -> dual ? List.of(Animations.BLADE_RUSH_COMBO1, Animations.BLADE_RUSH_COMBO2, Animations.BLADE_RUSH_COMBO3)
                    : List.of(Animations.EVISCERATE_FIRST, Animations.EVISCERATE_SECOND);
            case GLOVE -> List.of(Animations.RELENTLESS_COMBO);
            case SWORD -> dual ? List.of(Animations.DANCING_EDGE) : List.of(Animations.SWEEPING_EDGE);
            case AXE -> List.of(Animations.THE_GUILLOTINE);
            case TRIDENT -> List.of(Animations.WRATHFUL_LIGHTING);
        };
    }
}
