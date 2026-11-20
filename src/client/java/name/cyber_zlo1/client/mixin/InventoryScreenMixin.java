package name.cyber_zlo1.client.mixin;

import name.cyber_zlo1.client.audio.CyberOpenALSynth;
import name.cyber_zlo1.client.ui.CyberUiRenderer;
import name.cyber_zlo1.network.CyberwareNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.SoundManager;
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
                64,
                14,
                Text.translatable("cyber.ui.button"),
                button -> ClientPlayNetworking.send(CyberwareNetworking.OpenCyberwarePayload.INSTANCE),
                textSupplier -> textSupplier.get()
        ) {
            private boolean wasHovered = false;

            @Override
            public void playDownSound(SoundManager soundManager) {
                CyberOpenALSynth.play("ui_click", 0.8f, 1.0f);
            }

            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                boolean hovered = this.isHovered();
                if (hovered && !this.wasHovered) {
                    CyberOpenALSynth.play("ui_hover", 0.7f, 1.0f);
                }
                this.wasHovered = hovered;

                int x = this.getX();
                int y = this.getY();
                int w = this.getWidth();
                int h = this.getHeight();

                int bg = hovered ? 0xEE2B0D16 : CyberUiRenderer.CP_CRIMSON_BG;
                int border = hovered ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CRIMSON;
                int accent = hovered ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_YELLOW;
                int textColor = hovered ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;

                CyberUiRenderer.drawChamferedPanel(context, x, y, w, h, 4, bg, border, accent);

                // Subtle animated Cyberpunk 2077 top-edge scan shimmer when hovered
                if (hovered) {
                    int shimmerSpan = Math.max(1, w - 14);
                    int shimmerX = x + 3 + (int) ((System.currentTimeMillis() / 22L) % shimmerSpan);
                    context.fill(shimmerX, y, Math.min(x + w - 2, shimmerX + 9), y + 1, CyberUiRenderer.CP_CYAN);
                }

                CyberUiRenderer.drawClampedTextCentered(
                        context,
                        textRenderer,
                        this.getMessage(),
                        x + 2 + (w - 6) * 0.5f,
                        y + 4.0f,
                        0.68f,
                        w - 10.0f,
                        textColor,
                        false
                );
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
