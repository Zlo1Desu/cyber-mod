package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.network.CyberwareNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractInventoryScreen<PlayerScreenHandler> {
    @Unique
    private ButtonWidget cyber$cyberwareButton;

    protected InventoryScreenMixin(PlayerScreenHandler screenHandler, PlayerInventory playerInventory, Text text) {
        super(screenHandler, playerInventory, text);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cyber$addCyberwareButton(CallbackInfo ci) {
        int btnX = this.x + this.backgroundWidth - 64;
        int btnY = this.y - 15;
        this.cyber$cyberwareButton = new ButtonWidget(
                btnX,
                btnY,
                62,
                14,
                Text.translatable("cyber.ui.button"),
                button -> ClientPlayNetworking.send(CyberwareNetworking.OpenCyberwarePayload.INSTANCE),
                textSupplier -> textSupplier.get()
        ) {
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                boolean hovered = this.isHovered();
                int bg = hovered ? 0xEE18263A : 0xEE090E18;
                int border = hovered ? 0xFFFCEE09 : 0xFF00F0FF;
                int textColor = hovered ? 0xFFFCEE09 : 0xFF00F0FF;

                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                context.fill(x, y, x + w, y + h, bg);
                context.fill(x, y, x + w, y + 1, border);
                context.fill(x, y + h - 1, x + w, y + h, border);
                context.fill(x, y, x + 1, y + h, border);
                context.fill(x + w - 1, y, x + w, y + h, border);
                context.fill(x, y, x + 3, y + h, hovered ? 0xFFFF003C : 0xFFFCEE09);

                context.getMatrices().push();
                float scale = Math.min(0.70f, 52.0f / Math.max(1, textRenderer.getWidth(this.getMessage())));
                int textW = (int) (textRenderer.getWidth(this.getMessage()) * scale);
                context.getMatrices().translate(x + 3 + (w - 4 - textW) / 2.0f, y + 4, 0);
                context.getMatrices().scale(scale, scale, 1.0f);
                context.drawText(textRenderer, this.getMessage(), 0, 0, textColor, false);
                context.getMatrices().pop();
            }
        };
        this.addDrawableChild(this.cyber$cyberwareButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cyber$syncButtonPosition(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.cyber$cyberwareButton != null) {
            this.cyber$cyberwareButton.setPosition(this.x + this.backgroundWidth - 64, this.y - 15);
        }
    }
}
