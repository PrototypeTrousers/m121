package proto.mechanicalarmory.common.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import proto.mechanicalarmory.MechanicalArmory;

public class BlockArm extends Block {
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<BlockArm>> COMPLEX_CODEC = MechanicalArmory.REGISTRAR.register("simple", () -> simpleCodec(BlockArm::new));
    int value;

    public BlockArm(Properties properties) {
        super(properties);
    }

    @Override
    protected @NotNull MapCodec<BlockArm> codec() {
        return COMPLEX_CODEC.value();
    }

    public int getValue() {
        return this.value;
    }
}
