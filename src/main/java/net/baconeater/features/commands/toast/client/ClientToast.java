package net.baconeater.features.commands.toast.client;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.Toast;
import net.minecraft.client.toast.ToastManager;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public final class ClientToast implements Toast {
    private static final Identifier TEXTURE = Identifier.ofVanilla("toast/advancement");
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final int TEXT_WIDTH = 125;
    private static final long DISPLAY_TIME_MS = 5000L;

    private final ItemStack icon;
    private final Text title;
    private final Text subtitle;
    private Visibility visibility = Visibility.SHOW;

    public ClientToast(ItemStack icon, Text title, Text subtitle) {
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public void draw(DrawContext context, TextRenderer textRenderer, long startTime) {
        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, WIDTH, HEIGHT);
        context.drawItem(icon, 8, 8);

        List<OrderedText> titleLines = textRenderer.wrapLines(title, TEXT_WIDTH);
        if (!titleLines.isEmpty()) {
            context.drawText(textRenderer, titleLines.get(0), 30, 7, 0xFFFFFF00, false);
        }

        List<OrderedText> subtitleLines = textRenderer.wrapLines(subtitle, TEXT_WIDTH);
        if (!subtitleLines.isEmpty()) {
            context.drawText(textRenderer, subtitleLines.get(0), 30, 18, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void update(ToastManager manager, long time) {
        if (time >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()) {
            visibility = Visibility.HIDE;
        }
    }

    @Override
    public Visibility getVisibility() {
        return visibility;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }
}
