package proto.mechanicalarmory.client.ui.owo.component;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.ButtonComponent;

public class KnobButton {
    public static ButtonComponent.Renderer knob(int trackColor, int knobColor, int activeTrackColor) {
        return (context, button, delta) -> {
            RenderSystem.enableDepthTest();

            // 1. Determine Toggle State
            // Note: For a true toggle, you'd usually have a boolean field 'isOn' in your ButtonComponent subclass
            // For this example, we will assume 'button.active' or a custom state determines position.
            boolean isOn = button.active; // Replace with your custom toggle state if needed

            // 2. Draw Track (The background rectangle)
            int trackHeight = 4;
            int trackY = button.getY() + (button.getHeight() / 4);
            int currentTrackColor = isOn ? activeTrackColor : trackColor;

            // Draw track background
            context.fill(button.getX(), trackY, button.getX() + button.getWidth(), trackY + trackHeight, 0xFFAAAAAA); // Border
            context.fill(button.getX() + 1, trackY + 1, button.getX() + button.getWidth() - 1, trackY + trackHeight - 1, currentTrackColor);

            // 3. Draw Knob (The sliding square)
            int knobSize = 8;
            // Calculate X: Left if off, Right if on
            int knobX = isOn ? (button.getX() + button.getWidth() - knobSize) : button.getX();

            context.fill(
                    knobX,
                    button.getY() + button.getHeight() / 4 - knobSize / 2 + trackHeight / 2,
                    knobX + knobSize,
                    button.getY() + button.getHeight() / 4 + knobSize / 2 + trackHeight / 2,
                    0xFF000000);

            context.fill(
                    knobX + 1,
                    button.getY() + button.getHeight() / 4 - knobSize / 2 + trackHeight / 2 + 1,
                    knobX + knobSize -1 ,
                    button.getY() + button.getHeight() / 4 + knobSize / 2 + trackHeight / 2 - 1,
                    0xFFEEEEEE);

            // Optional: Highlight if hovered
            if (button.isHovered()) {
                context.fill(knobX + 1, button.getY() + 1, knobX + knobSize - 1, button.getY() + button.getHeight() - 1, 0x40FFFFFF);
            }
        };
    }
}