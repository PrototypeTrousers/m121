package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

import java.util.Map;

public class PoseHelper {

    private static final Map<net.minecraft.world.level.block.entity.BlockEntityType<?>, PartPoseConfig> POSE_CONFIGS = new java.util.HashMap<>();

    public static void registerPoseConfig(net.minecraft.world.level.block.entity.BlockEntityType<?> type, PartPoseConfig config) {
        if (type != null && config != null) {
            POSE_CONFIGS.put(type, config);
        }
    }

    public static Matrix4f createInitialPose(BlockPos visualPos, BlockState blockState) {
        return createInitialPose(visualPos, blockState, null);
    }

    public static Matrix4f createInitialPose(BlockPos visualPos, BlockState blockState, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        Matrix4f matrix = new Matrix4f().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());

        if (blockState != null) {
            // Detect the facing direction from any Direction-typed block state property
            Direction facing = null;
            for (Property<?> prop : blockState.getProperties()) {
                Object val = blockState.getValue(prop);
                if (val instanceof Direction dir) {
                    facing = dir;
                    break;
                }
            }

            PartPoseConfig config = (blockEntity != null) ? POSE_CONFIGS.get(blockEntity.getType()) : null;

            if (config != null && config.usesFacingRotation) {
                // Generic renderer-driven transform: translate to center, apply facing quaternion from BlockState,
                // then optionally flip vertically and apply post-rotation Y offset.
                // This reconstructs what vanilla does with poseStack.mulPose(direction.getRotation()) + scale(1,-1,-1).
                Direction dir = (facing != null) ? facing : Direction.UP;
                matrix.translate(0.5F, 0.5F + config.yOffset, 0.5F);
                matrix.scale(0.9995F, 0.9995F, 0.9995F);
                matrix.rotate(dir.getRotation());
                if (config.verticallyFlipped) {
                    matrix.scale(1.0F, -1.0F, -1.0F);
                }
                if (config.postRotationYOffset != 0.0F) {
                    matrix.translate(0.0F, config.postRotationYOffset, 0.0F);
                }
                return matrix;
            }

            if (config != null && config.customPose && facing != null) {
                matrix.translate(0.0F, config.yOffset, 0.0F);
                if (config.xRot != 0.0F) {
                    matrix.rotateX(config.xRot * Mth.DEG_TO_RAD);
                }
                matrix.translate(0.5F, 0.5F, 0.5F);
                if (config.zRotOffset != 0.0F) {
                    matrix.rotateZ((config.zRotOffset + facing.toYRot()) * Mth.DEG_TO_RAD);
                } else {
                    matrix.rotateY(-facing.toYRot() * Mth.DEG_TO_RAD);
                }
                matrix.translate(-0.5F, -0.5F, -0.5F);
                return matrix;
            } else if (facing != null && facing.getAxis().isHorizontal()) {
                float horizontalAngle = facing.toYRot();
                matrix.translate(0.5F, 0.5F, 0.5F)
                      .rotateY(-horizontalAngle * Mth.DEG_TO_RAD)
                      .translate(-0.5F, -0.5F, -0.5F);
            }
        }
        return matrix;
    }

    public static void updateVisibility(BlockState blockState, Map<String, InstanceTree> rootTrees) {
        if (rootTrees == null || rootTrees.size() <= 1 || blockState == null) {
            return;
        }

        for (Property<?> prop : blockState.getProperties()) {
            String propName = prop.getName();
            String valName = blockState.getValue(prop).toString().toLowerCase();

            if (propName.equals("type")) { // Chests (single, left, right)
                for (Map.Entry<String, InstanceTree> entry : rootTrees.entrySet()) {
                    String layerName = entry.getKey().toLowerCase();
                    boolean match = false;
                    if (valName.equals("single") && !layerName.contains("double") && !layerName.contains("left") && !layerName.contains("right")) {
                        match = true;
                    } else if (valName.equals("left") && layerName.contains("left")) {
                        match = true;
                    } else if (valName.equals("right") && layerName.contains("right")) {
                        match = true;
                    }
                    if (entry.getValue() != null) {
                        entry.getValue().visible(match);
                    }
                }
                return;
            } else if (propName.equals("part")) { // Beds (head, foot)
                for (Map.Entry<String, InstanceTree> entry : rootTrees.entrySet()) {
                    String layerName = entry.getKey().toLowerCase();
                    boolean match = layerName.contains(valName);
                    if (entry.getValue() != null) {
                        entry.getValue().visible(match);
                    }
                }
                return;
            }
        }

        // If the block state has no "type" property (e.g. Ender Chest using ChestRenderer),
        // but the visual has double-chest layers, default to single chest visibility.
        boolean hasChestLayers = false;
        for (String key : rootTrees.keySet()) {
            String lower = key.toLowerCase();
            if (lower.contains("double") || lower.contains("left") || lower.contains("right")) {
                hasChestLayers = true;
                break;
            }
        }
        if (hasChestLayers) {
            for (Map.Entry<String, InstanceTree> entry : rootTrees.entrySet()) {
                String layerName = entry.getKey().toLowerCase();
                boolean match = !layerName.contains("double") && !layerName.contains("left") && !layerName.contains("right");
                if (entry.getValue() != null) {
                    entry.getValue().visible(match);
                }
            }
        }
    }

    public static Matrix4f createEntityPose(net.minecraft.world.entity.Entity entity, float partialTick, net.minecraft.core.Vec3i renderOrigin) {
        double x = Mth.lerp((double) partialTick, entity.xo, entity.getX()) - (double) renderOrigin.getX();
        double y = Mth.lerp((double) partialTick, entity.yo, entity.getY()) - (double) renderOrigin.getY();
        double z = Mth.lerp((double) partialTick, entity.zo, entity.getZ()) - (double) renderOrigin.getZ();

        float bodyRot = 0.0F;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            bodyRot = Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot);
        } else {
            bodyRot = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
        }

        return new Matrix4f()
                .translate((float) x, (float) y, (float) z)
                .rotateY((180.0F - bodyRot) * Mth.DEG_TO_RAD)
                .scale(-1.0F, -1.0F, 1.0F)
                .translate(0.0F, -1.501F, 0.0F);
    }

    public static class EntityAnimationParams {
        public float limbSwing;
        public float limbSwingAmount;
        public float ageInTicks;
        public float netHeadYaw;
        public float headPitch;
    }

    public static EntityAnimationParams getAnimationParams(net.minecraft.world.entity.Entity entity, float partialTick) {
        EntityAnimationParams params = new EntityAnimationParams();
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            params.ageInTicks = (float) living.tickCount + partialTick;
            float headYaw = Mth.lerp(partialTick, living.yHeadRotO, living.yHeadRot);
            float bodyYaw = Mth.lerp(partialTick, living.yBodyRotO, living.yBodyRot);
            params.netHeadYaw = headYaw - bodyYaw;
            params.headPitch = Mth.lerp(partialTick, living.xRotO, living.getXRot());

            if (!living.isPassenger() && living.isAlive()) {
                params.limbSwingAmount = living.walkAnimation.speed(partialTick);
                params.limbSwing = living.walkAnimation.position(partialTick);
                if (living.isBaby()) {
                    params.limbSwing *= 3.0F;
                }
                if (params.limbSwingAmount > 1.0F) {
                    params.limbSwingAmount = 1.0F;
                }
            }
        }
        return params;
    }

    public static void syncDummyToTree(DummyModelPart dummy, dev.engine_room.flywheel.lib.model.part.InstanceTree tree) {
        if (dummy != null && tree != null) {
            tree.xPos(dummy.x);
            tree.yPos(dummy.y);
            tree.zPos(dummy.z);
            tree.xRot(dummy.xRot);
            tree.yRot(dummy.yRot);
            tree.zRot(dummy.zRot);
            tree.xScale(dummy.xScale);
            tree.yScale(dummy.yScale);
            tree.zScale(dummy.zScale);
            tree.visible(dummy.visible);
            tree.skipDraw(dummy.skipDraw);
        }
    }

    public static class PartPair {
        public final net.minecraft.client.model.geom.ModelPart vanillaPart;
        public final dev.engine_room.flywheel.lib.model.part.InstanceTree flywheelTree;

        public PartPair(net.minecraft.client.model.geom.ModelPart vanillaPart, dev.engine_room.flywheel.lib.model.part.InstanceTree flywheelTree) {
            this.vanillaPart = vanillaPart;
            this.flywheelTree = flywheelTree;
        }

        public void syncToFlywheel() {
            if (vanillaPart == null || flywheelTree == null) return;
            flywheelTree.xPos(vanillaPart.x);
            flywheelTree.yPos(vanillaPart.y);
            flywheelTree.zPos(vanillaPart.z);
            flywheelTree.xRot(vanillaPart.xRot);
            flywheelTree.yRot(vanillaPart.yRot);
            flywheelTree.zRot(vanillaPart.zRot);
            flywheelTree.xScale(vanillaPart.xScale);
            flywheelTree.yScale(vanillaPart.yScale);
            flywheelTree.zScale(vanillaPart.zScale);
            flywheelTree.visible(vanillaPart.visible);
            flywheelTree.skipDraw(vanillaPart.skipDraw);
        }
    }

    public static class EntityVisualState {
        public final net.minecraft.client.model.EntityModel<net.minecraft.world.entity.Entity> model;
        public final java.util.List<PartPair> pairs;

        @SuppressWarnings("unchecked")
        public EntityVisualState(net.minecraft.client.model.EntityModel<?> model, java.util.List<PartPair> pairs) {
            this.model = (net.minecraft.client.model.EntityModel<net.minecraft.world.entity.Entity>) model;
            this.pairs = pairs;
        }
    }

    public static void collectPartPairs(net.minecraft.client.model.geom.ModelPart vanillaPart, dev.engine_room.flywheel.lib.model.part.InstanceTree flywheelTree, java.util.List<PartPair> pairs) {
        if (vanillaPart == null || flywheelTree == null) return;
        pairs.add(new PartPair(vanillaPart, flywheelTree));
        try {
            for (java.lang.reflect.Field f : vanillaPart.getClass().getDeclaredFields()) {
                if (java.util.Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(vanillaPart);
                    if (map != null) {
                        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                            String childName = String.valueOf(entry.getKey());
                            Object childObj = entry.getValue();
                            if (childObj instanceof net.minecraft.client.model.geom.ModelPart childPart) {
                                dev.engine_room.flywheel.lib.model.part.InstanceTree childTree = flywheelTree.child(childName);
                                if (childTree != null) {
                                    collectPartPairs(childPart, childTree, pairs);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static String toSnakeCase(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static Object setupEntityVisual(net.minecraft.world.entity.Entity entity, Map<String, InstanceTree> rootTreesMap) {
        try {
            net.minecraft.client.renderer.entity.EntityRenderer<?> renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> livingRenderer) {
                net.minecraft.client.model.EntityModel<?> model = livingRenderer.getModel();
                java.util.List<PartPair> pairs = new java.util.ArrayList<>();
                
                net.minecraft.client.model.geom.ModelPart rootPart = null;
                if (model instanceof net.minecraft.client.model.HierarchicalModel<?> hm) {
                    rootPart = hm.root();
                } else {
                    for (java.lang.reflect.Field f : model.getClass().getFields()) {
                        if (net.minecraft.client.model.geom.ModelPart.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            try {
                                net.minecraft.client.model.geom.ModelPart p = (net.minecraft.client.model.geom.ModelPart) f.get(model);
                                if (p != null && rootPart == null) {
                                    rootPart = p;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                InstanceTree mainTree = rootTreesMap.isEmpty() ? null : rootTreesMap.values().iterator().next();
                if (rootPart != null && mainTree != null) {
                    collectPartPairs(rootPart, mainTree, pairs);
                }
                
                if (mainTree != null) {
                    java.util.Set<net.minecraft.client.model.geom.ModelPart> alreadyMatched = new java.util.HashSet<>();
                    for (PartPair pair : pairs) alreadyMatched.add(pair.vanillaPart);

                    Class<?> currClass = model.getClass();
                    while (currClass != null && currClass != Object.class) {
                        for (java.lang.reflect.Field f : currClass.getDeclaredFields()) {
                            f.setAccessible(true);
                            try {
                                Object val = f.get(model);
                                if (val instanceof net.minecraft.client.model.geom.ModelPart p && !alreadyMatched.contains(p)) {
                                    String name = f.getName();
                                    InstanceTree t = mainTree.child(name);
                                    if (t == null) t = mainTree.child(toSnakeCase(name));
                                    if (t != null) {
                                        collectPartPairs(p, t, pairs);
                                        for (PartPair pair : pairs) alreadyMatched.add(pair.vanillaPart);
                                    }
                                } else if (val instanceof net.minecraft.client.model.geom.ModelPart[] arr) {
                                    for (int i = 0; i < arr.length; i++) {
                                        if (arr[i] != null && !alreadyMatched.contains(arr[i])) {
                                            String name = f.getName();
                                            InstanceTree t = mainTree.child("part" + i);
                                            if (t == null) t = mainTree.child(name + i);
                                            if (t == null) t = mainTree.child(toSnakeCase(name) + "_" + i);
                                            if (t != null) {
                                                collectPartPairs(arr[i], t, pairs);
                                                for (PartPair pair : pairs) alreadyMatched.add(pair.vanillaPart);
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        currClass = currClass.getSuperclass();
                    }
                }

                return new EntityVisualState(model, pairs);
            }
        } catch (Exception e) {
            System.err.println("[Flywheel Slicer] Failed to setup entity visual state: " + e.getMessage());
        }
        return null;
    }

    public static void animateEntityVisual(Object stateObj, net.minecraft.world.entity.Entity entity, float partialTick) {
        if (stateObj instanceof EntityVisualState state && state.model != null && entity != null) {
            EntityAnimationParams params = getAnimationParams(entity, partialTick);
            synchronized (state.model) {
                for (PartPair pair : state.pairs) {
                    pair.vanillaPart.resetPose();
                }
                state.model.setupAnim(entity, params.limbSwing, params.limbSwingAmount, params.ageInTicks, params.netHeadYaw, params.headPitch);
                for (PartPair pair : state.pairs) {
                    pair.syncToFlywheel();
                }
            }
        }
    }
}
