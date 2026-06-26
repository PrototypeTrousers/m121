package proto.mechanicalarmory.common.recipes.shredder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import proto.mechanicalarmory.common.recipes.Recipe;
import proto.mechanicalarmory.common.recipes.RecipeRegistry;

public class ShredderRecipes {
    static final Recipe COBBLESTONE = new Recipe(new ItemStack(Items.COBBLESTONE), new  ItemStack(Items.STONE));

    public static void init() {
        var l = RecipeRegistry.INSTANCE.register("shredder");
        l.add(COBBLESTONE);
    }
}
