package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import org.joml.*;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GeckoModel implements Model {

    private static final RendererReloadCache<GeckoKey, GeckoModel> CACHE = new RendererReloadCache<>(key -> new GeckoModel(key.geoBone, key.material));
    private static final ThreadLocal<ThreadLocalObjects> THREAD_LOCAL_OBJECTS = ThreadLocal.withInitial(ThreadLocalObjects::new);
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    List<ConfiguredMesh> meshes = new ArrayList<>();
    GeoBone geoBone;
    Material material;
    public GeckoModel(GeoBone geoBone, Material material) {
        this.geoBone = geoBone;
        this.material = material;
        Mesh m = fromBone(geoBone, material);
        if (m != null) {
            meshes.add(new ConfiguredMesh(makeFlywheelMaterial(material), m));
        }
    }

    public static GeckoModel cachedOf(GeoBone geoBone, Material material) {
        return CACHE.get(new GeckoKey(geoBone, material));
    }

    SimpleQuadMesh fromBone(GeoBone geoBone, Material material) {
        if (geoBone.getCubes().isEmpty()){
            return null;
        }

        VanillaVertexWriter vertexWriter = THREAD_LOCAL_OBJECTS.get().vertexWriter;
        VertexConsumer v;
        if (!material.texture().equals(material.atlasLocation())) {
            v = new SpriteCoordinateExpander(vertexWriter, material.sprite());
        } else {
            v = vertexWriter;
        }

        PoseStack poseStack = new PoseStack();

        for (GeoCube cube : geoBone.getCubes()) {
            poseStack.pushPose();
            renderCube(poseStack, cube, v, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            poseStack.popPose();
        }

        MemoryBlock data = vertexWriter.copyDataAndReset();

        VertexView vertexView = new PosTexNormalVertexView();
        vertexView.load(data);
        return new SimpleQuadMesh(vertexView, "source=VanillaModel");

    }

    void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight,
                    int packedOverlay, int colour) {
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalisedPoseState = poseStack.last().normal();
        Matrix4f poseState = new Matrix4f(poseStack.last().pose());

        for (GeoQuad quad : cube.quads()) {
            if (quad == null)
                continue;

            Vector3f normal = normalisedPoseState.transform(new Vector3f(quad.normal()));

            RenderUtil.fixInvertedFlatCube(cube, normal);
            createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, colour);
        }
    }

    void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer,
                              int packedLight, int packedOverlay, int colour) {
        for (GeoVertex vertex : quad.vertices()) {
            Vector3f position = vertex.position();
            Vector4f vector4f = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0f));

            buffer.addVertex(vector4f.x(), vector4f.y(), vector4f.z(), colour, vertex.texU(),
                    vertex.texV(), packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
        }
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1, 1, 1, 1);
    }

    public dev.engine_room.flywheel.api.material.Material makeFlywheelMaterial(Material material) {
        return SimpleMaterial.builder()
                .texture(material.atlasLocation())
                .cutout(CutoutShaders.EPSILON)
                .light(LightShaders.SMOOTH_WHEN_EMBEDDED)
                .cardinalLightingMode(CardinalLightingMode.OFF)
                .ambientOcclusion(false)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GeckoModel that = (GeckoModel) o;
        if (this.geoBone != that.geoBone) return false;
        return material.equals(that.material);
    }

    @Override
    public int hashCode() {
        return Objects.hash(material);
    }

    private record GeckoKey(GeoBone geoBone, Material material) {
//        @Override
//        public boolean equals(Object o) {
//            if (o == null) return false;
//            if (o.getClass() != VanillaKey.class) {
//                return false;
//            }
//            if (this.material.equals(((VanillaKey) o).material)) {
//                return DeepModelPartHashStrategy.INSTANCE.equals(this.modelPart, ((VanillaKey) o).modelPart);
//            }
//            return false;
//        }
//
//        @Override
//        public int hashCode() {
//            int hash = this.material.hashCode();
//            hash += DeepModelPartHashStrategy.INSTANCE.hashCode();
//            return hash;
//        }
    }

    private static class ThreadLocalObjects {
        public final VanillaVertexWriter vertexWriter = new VanillaVertexWriter();
    }
}
