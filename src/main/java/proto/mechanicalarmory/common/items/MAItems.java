package proto.mechanicalarmory.common.items;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.common.blocks.MABlocks;
import proto.mechanicalarmory.common.items.armor.ArmorMaterials;
import proto.mechanicalarmory.common.items.armor.OctoSuit;

public class MAItems {

    // Create a Deferred Register to hold Items which will all be registered under the "mechanicalarmory" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MechanicalArmory.MODID);
    public static final DeferredItem<BlockItem> ARM_ITEM = ITEMS.registerSimpleBlockItem("arm", MABlocks.ARM);

    public static final DeferredItem<ArmorItem> MY_CHESTPLATE = ITEMS.registerItem("my_chestplate",
            properties -> new OctoSuit(ArmorMaterials.MY_MATERIAL, ArmorItem.Type.CHESTPLATE, properties));

}
