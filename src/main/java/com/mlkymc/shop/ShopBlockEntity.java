package com.mlkymc.shop;

import com.mlkymc.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Block entity for shop stalls. Stores the shop ID linking to a Shopkeeper in ShopManager.
 * Persists automatically with chunk save/load via getPersistentData().
 */
public class ShopBlockEntity extends BlockEntity {

    private static final String TAG_SHOP_ID = "mlkymc_shop_id";

    public ShopBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STALL_DEED.get(), pos, state);
    }

    public String getShopId() {
        return getPersistentData().getStringOr(TAG_SHOP_ID, "");
    }

    public void setShopId(String shopId) {
        getPersistentData().putString(TAG_SHOP_ID, shopId != null ? shopId : "");
        setChanged();
    }
}
