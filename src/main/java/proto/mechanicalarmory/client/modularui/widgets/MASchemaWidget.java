package proto.mechanicalarmory.client.modularui.widgets;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.Interactable;
import brachy.modularui.drawable.schema.ISchema;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.utils.math.MathUtils;
import brachy.modularui.widget.Widget;
import proto.mechanicalarmory.client.modularui.renderer.MABaseSchemaRenderer;
import proto.mechanicalarmory.client.modularui.renderer.MASchemaRenderer;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.platform.InputConstants;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Mechanical Armory's own copy of the upstream {@code SchemaWidget}.
 * Extends {@link Widget} directly (no upstream SchemaWidget dependency) and holds
 * an {@link MABaseSchemaRenderer} instead of the upstream {@code BaseSchemaRenderer}.
 */
public class MASchemaWidget extends Widget<MASchemaWidget> implements Interactable {

    @Getter private final MABaseSchemaRenderer schemaRenderer;
    @Getter private boolean enableRotation = true;
    @Getter private boolean enableTranslation = true;
    @Getter private boolean enableScaling = true;
    @Getter private float scale = 10f;
    @Getter private float pitch = MathUtils.PI_QUART;
    @Getter private float yaw = 0;
    @Getter private final Vector3f offset = new Vector3f();

    public MASchemaWidget(ISchema schema) {
        this(new MASchemaRenderer(schema));
    }

    public MASchemaWidget(MABaseSchemaRenderer schemaRenderer) {
        this.schemaRenderer = schemaRenderer;
    }

    @Override
    public void dispose() {
        super.dispose();
        this.schemaRenderer.dispose();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        Vector3fc f = this.schemaRenderer.schema().getFocus();
        this.schemaRenderer.camera().setLookAtAndAngle(
                f.x() + this.offset.x, f.y() + this.offset.y, f.z() + this.offset.z,
                scale, yaw, pitch);
        this.schemaRenderer.drawAtZeroPadded(context, getArea(), widgetTheme.theme());
    }

    @Override
    public boolean onMouseScrolled(double scrollX, double scrollY) {
        if (this.enableScaling) {
            incrementScale((float) (-scrollY / 12.0f));
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Result onMousePressed(int button) {
        return Result.SUCCESS;
    }

    @Override
    public void onMouseDrag(int button, double dragX, double dragY) {
        float dx = (float) dragX;
        float dy = (float) dragY;
        if (button == InputConstants.MOUSE_BUTTON_LEFT && this.enableRotation) {
            float moveScale = 0.03f;
            yaw(this.yaw + dx * moveScale);
            pitch(this.pitch + dy * moveScale);
        } else if (button == InputConstants.MOUSE_BUTTON_MIDDLE && this.enableTranslation) {
            float moveScale = 0.09f;
            Vector3f look = this.schemaRenderer.camera().getLookVec().normalize();
            Vector3f right = look.cross(MathUtils.UNIT_Y, new Vector3f()).normalize();
            Vector3f up = right.cross(look, new Vector3f());
            this.offset.sub(right.mul(dx * moveScale)).add(up.mul(dy * moveScale));
        }
    }

    public void incrementScale(float amount) {
        this.scale += amount;
        this.scale = Math.max(this.scale, 0.001f);
    }

    public MASchemaWidget scale(float scale) {
        this.scale = scale;
        return this;
    }

    public MASchemaWidget pitch(float pitch) {
        this.pitch = Mth.clamp(pitch, -Mth.HALF_PI + 0.001f, Mth.HALF_PI - 0.001f);
        return this;
    }

    public MASchemaWidget yaw(float yaw) {
        this.yaw = (yaw + Mth.TWO_PI) % Mth.TWO_PI;
        return this;
    }

    public MASchemaWidget offset(float x, float y, float z) {
        this.offset.set(x, y, z);
        return this;
    }

    public MASchemaWidget enableDragRotation(boolean enable) {
        this.enableRotation = enable;
        return this;
    }

    public MASchemaWidget enableDragTranslation(boolean enable) {
        this.enableTranslation = enable;
        return this;
    }

    public MASchemaWidget enableScrollScaling(boolean enable) {
        this.enableScaling = enable;
        return this;
    }

    public MASchemaWidget enableInteraction(boolean rotation, boolean translation, boolean scaling) {
        return enableDragRotation(rotation)
                .enableDragTranslation(translation)
                .enableScrollScaling(scaling);
    }

    public MASchemaWidget enableAllInteraction(boolean enable) {
        return enableInteraction(enable, enable, enable);
    }

    /** Layer visibility button — mirrors upstream {@code SchemaWidget.LayerButton}. */
    public static class LayerButton extends brachy.modularui.widgets.ButtonWidget<LayerButton> {

        private final int minLayer;
        private final int maxLayer;
        private int currentLayer = Integer.MIN_VALUE;

        public LayerButton(MABaseSchemaRenderer schema, int minLayer, int maxLayer) {
            this.minLayer = minLayer;
            this.maxLayer = maxLayer;
            overlay(Text.dynamic(() -> currentLayer > Integer.MIN_VALUE ?
                    Component.literal(Integer.toString(currentLayer)) : Component.literal("ALL")).scale(0.5f));

            onMousePressed((context, button) -> {
                if (button == 0 || button == 1) {
                    if (button == 0) {
                        if (currentLayer == Integer.MIN_VALUE) currentLayer = minLayer;
                        else currentLayer++;
                    } else {
                        if (currentLayer == Integer.MIN_VALUE) currentLayer = maxLayer;
                        else currentLayer--;
                    }
                    if (currentLayer > maxLayer || currentLayer < minLayer) {
                        currentLayer = Integer.MIN_VALUE;
                    }
                    schema.notifyRecompile();
                    return true;
                }
                return false;
            });
            schema.updateRenderFilter((blockPos, blockInfo) ->
                    currentLayer == Integer.MIN_VALUE || currentLayer >= blockPos.getY());
        }

        public LayerButton startLayer(int start) {
            this.currentLayer = start;
            return this;
        }
    }
}
