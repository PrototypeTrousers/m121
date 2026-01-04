package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.LightShader;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.internal.FlwLibLink;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import proto.mechanicalarmory.client.mixin.CompositeRenderTypeAccessor;
import proto.mechanicalarmory.client.mixin.RenderTypeAccessor;
import proto.mechanicalarmory.client.mixin.TextureStateShardAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VanillaModel implements Model {

    private static final RendererReloadCache<VanillaKey, VanillaModel> CACHE = new RendererReloadCache<>(key -> new VanillaModel(key.modelPart, key.instanceMaterialKey));
    private static final ThreadLocal<ThreadLocalObjects> THREAD_LOCAL_OBJECTS = ThreadLocal.withInitial(ThreadLocalObjects::new);
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    List<ConfiguredMesh> meshes = new ArrayList<>();
    ModelPart modelPart;
    SinkBufferSourceVisual.InstanceMaterialKey material;
    public VanillaModel(ModelPart part, SinkBufferSourceVisual.InstanceMaterialKey instanceMaterialKey) {
        this.modelPart = part;
        this.material = instanceMaterialKey;
        Mesh m = fromPart(part, instanceMaterialKey);
        if (m != null) {
            meshes.add(new ConfiguredMesh(instanceMaterialKey.material(), m));
        }
    }

    public static VanillaModel cachedOf(ModelPart modelPart, SinkBufferSourceVisual.InstanceMaterialKey materialKey) {
        return CACHE.get(new VanillaKey(modelPart, materialKey));
    }

    SimpleQuadMesh fromPart(ModelPart part, SinkBufferSourceVisual.InstanceMaterialKey material) {
        if (part.isEmpty()) {
            return null;
        }

        VanillaVertexWriter vertexWriter = THREAD_LOCAL_OBJECTS.get().vertexWriter;
        VertexConsumer v;
        if (material.sprite() != null) {
            v = new SpriteCoordinateExpander(vertexWriter, material.sprite());
        } else {
            v = vertexWriter;
        }
        FlwLibLink.INSTANCE.compileModelPart(part, IDENTITY_POSE, v, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        MemoryBlock data = vertexWriter.copyDataAndReset();

        VertexView vertexView = new PosTexNormalVertexView();
        vertexView.load(data);
        return new SimpleQuadMesh(vertexView, "source=VanillaModel");
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1, 1, 1, 1);
    }

    public static dev.engine_room.flywheel.api.material.Material makeFlywheelMaterial(RenderType renderType) {

        CompositeRenderTypeAccessor c = ((CompositeRenderTypeAccessor) renderType);
        RenderStateShard.EmptyTextureStateShard r = ((RenderTypeAccessor) (Object) c.getState()).getTextureState();
        ResourceLocation atlas = ((TextureStateShardAccessor) r).getTexture().get();

        SimpleMaterial.Builder materialBuilder = SimpleMaterial.builder();
        if (renderType.name.contains("entity")) {
            materialBuilder.cardinalLightingMode(CardinalLightingMode.ENTITY);
        } else {
            materialBuilder.cardinalLightingMode(CardinalLightingMode.CHUNK);
        } if (renderType.name.contains("cutout")) {
            materialBuilder.cutout(CutoutShaders.EPSILON);
        } else {
            materialBuilder.cutout(CutoutShaders.OFF);
        }

        if (renderType.name.contains("translucent")) {
            materialBuilder.transparency(Transparency.ORDER_INDEPENDENT);
        }
        materialBuilder.light(LightShaders.FLAT);
        materialBuilder.texture(atlas);
        return materialBuilder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        VanillaModel that = (VanillaModel) o;
        if (this.modelPart != that.modelPart) return false;
        return material.equals(that.material);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material);
    }

    private record VanillaKey(ModelPart modelPart, SinkBufferSourceVisual.InstanceMaterialKey instanceMaterialKey) {
        @Override
        public boolean equals(Object o) {
            if (o == null) return false;
            if (o.getClass() != VanillaKey.class) {
                return false;
            }
            if (this.instanceMaterialKey.equals(((VanillaKey) o).instanceMaterialKey)) {
                return DeepModelPartHashStrategy.INSTANCE.equals(this.modelPart, ((VanillaKey) o).modelPart);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = this.instanceMaterialKey.hashCode();
            hash += DeepModelPartHashStrategy.INSTANCE.hashCode();
            return hash;
        }
    }

    private static class ThreadLocalObjects {
        public final VanillaVertexWriter vertexWriter = new VanillaVertexWriter();
    }
}
