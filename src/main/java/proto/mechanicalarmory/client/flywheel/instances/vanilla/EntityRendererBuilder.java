package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ObjectBuilder;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import proto.mechanicalarmory.client.mixin.EntityRenderersAccessor;

public class EntityRendererBuilder<T> implements ObjectBuilder<EntityRenderer<Entity>> {
    EntityRendererProvider.Context context;
    EntityType<?> entityType;

    EntityRendererBuilder(EntityType<?> entityType, EntityRendererProvider.Context context){
        this.context = context;
        this.entityType = entityType;
    }

    @Override
    public EntityRenderer<Entity> newInstance() {
        return (EntityRenderer<Entity>) EntityRenderersAccessor.getProviders().get(this.entityType).create(context);
    }
}
