package name.cyber_zlo1.client.screen;

import name.cyber_zlo1.client.audio.CyberOpenALSynth;
import name.cyber_zlo1.client.ui.CyberUiRenderer;
import name.cyber_zlo1.cyberware.CyberwareItem;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.CyberwareState;
import name.cyber_zlo1.screen.CyberwareScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CyberwareScreen extends HandledScreen<CyberwareScreenHandler> {
    private static final int SHOCKWAVE_DURATION = 15;

    private float mouseX;
    private float mouseY;
    private Slot lastHoveredSlot;
    private final ItemStack[] lastCyberwareStacks = new ItemStack[10];
    private final int[] slotShockwaveTicks = new int[10];
    private int screenTicks = 0;
    private int errorFlashTicks = 0;

    public CyberwareScreen(CyberwareScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = CyberwareScreenHandler.GUI_WIDTH;
        this.backgroundHeight = CyberwareScreenHandler.GUI_HEIGHT;
        Arrays.fill(this.lastCyberwareStacks, ItemStack.EMPTY);
    }

    @Override
    protected void init() {
        super.init();
        this.lastHoveredSlot = null;
        this.screenTicks = 0;
        this.errorFlashTicks = 0;
        Arrays.fill(this.slotShockwaveTicks, 0);
        for (int i = 0; i < 10; i++) {
            this.lastCyberwareStacks[i] = this.handler.getCyberwareInventory().getStack(i).copy();
        }
        int btnX = this.x + 8;
        int btnY = this.y + 4;
        this.addDrawableChild(new CyberButton(btnX, btnY, 36, 13, Text.literal("< INV"), button -> {
            if (this.client != null && this.client.player != null) {
                this.client.setScreen(new InventoryScreen(this.client.player));
            }
        }));
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        this.screenTicks++;
        if (this.errorFlashTicks > 0) {
            this.errorFlashTicks--;
        }

        for (int i = 0; i < 10; i++) {
            if (this.slotShockwaveTicks[i] > 0) {
                this.slotShockwaveTicks[i]--;
            }
            ItemStack current = this.handler.getCyberwareInventory().getStack(i);
            if (this.screenTicks <= 3) {
                this.lastCyberwareStacks[i] = current.copy();
                continue;
            }
            if (!ItemStack.areEqual(this.lastCyberwareStacks[i], current)) {
                boolean installed = !current.isEmpty();
                this.lastCyberwareStacks[i] = current.copy();
                this.slotShockwaveTicks[i] = SHOCKWAVE_DURATION;
                playSlotChangeSound(installed);
            }
        }
    }

    private void playSlotChangeSound(boolean installed) {
        if (installed) {
            CyberOpenALSynth.play("ui_install", 0.9f, 1.0f);
        } else {
            CyberOpenALSynth.play("ui_click", 0.8f, 0.85f);
        }
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slot != null) {
            ItemStack cursor = this.handler.getCursorStack();
            boolean isCyberSlot = slot.inventory == this.handler.getCyberwareInventory();
            boolean rejected = false;

            if (isCyberSlot && !cursor.isEmpty() && !slot.canInsert(cursor)) {
                rejected = true;
            } else if (!isCyberSlot
                    && slot.hasStack()
                    && slot.getStack().getItem() instanceof CyberwareItem hoveredCi
                    && !this.handler.canFitImplant(hoveredCi.getSlotType().getIndex(), hoveredCi)
                    && (actionType == SlotActionType.QUICK_MOVE || actionType == SlotActionType.PICKUP)) {
                rejected = true;
            }

            if (rejected) {
                this.errorFlashTicks = 14;
                CyberOpenALSynth.play("ui_error", 0.9f, 1.0f);
            } else if (slot.hasStack() || !cursor.isEmpty()) {
                CyberOpenALSynth.play("ui_click", 0.7f, isCyberSlot ? 1.08f : 1.0f);
            }
        }
        super.onMouseClick(slot, slotId, button, actionType);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.render(context, mouseX, mouseY, delta);
        if (this.focusedSlot != this.lastHoveredSlot) {
            this.lastHoveredSlot = this.focusedSlot;
            if (this.focusedSlot != null) {
                float pitch = (this.focusedSlot.inventory == this.handler.getCyberwareInventory()) ? 1.08f : 0.98f;
                CyberOpenALSynth.play("ui_hover", 0.7f, pitch);
            }
        }
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Suppress vanilla grey titles; REDengine header and labels render in drawBackground.
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;
        int w = this.backgroundWidth;
        int h = this.backgroundHeight;

        // 1. Deep red-obsidian Cyberpunk 2077 double-chamfered main frame
        CyberUiRenderer.drawDoubleChamferedPanel(
                context,
                x,
                y,
                w,
                h,
                8,
                CyberUiRenderer.CP_CRIMSON_BG,
                CyberUiRenderer.CP_CRIMSON,
                CyberUiRenderer.CP_YELLOW
        );

        // Secondary inner Kiroshi ice-cyan hairline framing
        CyberUiRenderer.drawBorder(context, x + 3, y + 21, w - 6, h - 24, CyberUiRenderer.CP_CYAN_DIM);

        // Corner micro-telemetry strictly in outer empty inventory margins (zero overlap with slots)
        CyberUiRenderer.drawClampedText(
                context, this.textRenderer, "// V2.077",
                x + 5, y + 227, 0.45f, 23.0f, CyberUiRenderer.CP_CRIMSON, false
        );
        CyberUiRenderer.drawClampedText(
                context, this.textRenderer, "OS.CP77",
                x + 195, y + 159, 0.45f, 21.0f, CyberUiRenderer.CP_CYAN_DIM, false
        );

        // 2. Top Header Bar — signature Cyberpunk 2077 Ripperdoc Crimson & Yellow
        context.fill(x + 6, y + 2, x + w - 2, y + 20, CyberUiRenderer.CP_CRIMSON_PANEL);
        context.fill(x + 4, y + 20, x + w - 4, y + 21, CyberUiRenderer.CP_CRIMSON);
        context.fill(x + 46, y + 20, x + 142, y + 21, CyberUiRenderer.CP_YELLOW);

        // Auto-clamped title between < INV (ends x+44) and Capacity Meter (starts x+146)
        Text titleText = Text.translatable("cyber.ui.title");
        CyberUiRenderer.drawClampedText(
                context,
                this.textRenderer,
                titleText,
                x + 48,
                y + 7,
                0.60f,
                94.0f,
                CyberUiRenderer.CP_YELLOW,
                true
        );

        // Segmented Cyberware Capacity gauge in top-right (x+146..x+216, y+3..y+19)
        drawCapacityMeter(context, x + 146, y + 3);

        // 3. Central Ripperdoc Scanner Chamber (x+62..x+160, y+23..y+141)
        int chX1 = x + 62;
        int chY1 = y + 23;
        int chX2 = x + 160;
        int chY2 = y + 141;
        int chW = chX2 - chX1;
        int chH = chY2 - chY1;
        CyberUiRenderer.drawChamferedPanel(
                context,
                chX1,
                chY1,
                chW,
                chH,
                6,
                CyberUiRenderer.CP_CHAMBER,
                CyberUiRenderer.CP_CRIMSON_DARK,
                0
        );
        drawTargetingReticle(context, chX1, chY1, chX2, chY2);

        // Animated telemetry margins along outer left/right edges (x+5..x+22 and x+199..x+216)
        drawDataRainMargins(context, x, y);

        // Dark-red / ice-cyan holographic scanner disc under player's feet
        drawHolographicPlatform(context, (chX1 + chX2) / 2, chY2 - 14, chY1);

        // Animated ECG biomonitor waveform in top-left of chamber
        drawEcgBiomonitor(context, chX1 + 3, chY1 + 3);

        // Clean 2077 schematic trace lines connecting all 10 slots to body zones
        drawCircuitTraces(context, x, y, delta);

        // Live 3D player model inside central chamber
        if (this.client != null && this.client.player != null) {
            InventoryScreen.drawEntity(
                    context,
                    chX1 + 2,
                    chY1 + 2,
                    chX2 - 2,
                    chY2 - 4,
                    48,
                    0.0625F,
                    this.mouseX,
                    this.mouseY,
                    this.client.player
            );
        }

        // 4. All 10 chamfered cyberware implant sockets + player inventory slot frames
        drawSlotFrames(context, x, y, delta);

        // 5. Inventory Divider & Compact Cyberpsychosis Module (clean 14px band y+143..y+156)
        context.fill(x + 6, y + 143, x + w - 6, y + 144, CyberUiRenderer.CP_CRIMSON);
        context.fill(x + 6, y + 144, x + 76, y + 145, CyberUiRenderer.CP_CYAN);

        CyberUiRenderer.drawClampedText(
                context,
                this.textRenderer,
                "// INVENTORY",
                x + 8,
                y + 148,
                0.60f,
                92.0f,
                CyberUiRenderer.CP_CYAN,
                false
        );

        drawCyberpsychosisBar(context, x + 108, y + 145);
    }

    private void drawCapacityMeter(DrawContext context, int mx, int my) {
        int boxW = 70;
        int boxH = 16;
        int cap = this.handler.getTotalEquippedCapacity();
        int maxCap = CyberwareState.MAX_CYBERWARE_CAPACITY;
        long now = System.currentTimeMillis();
        boolean flashTick = ((now / 160L) % 2L == 0L);

        boolean limitBlocked = this.errorFlashTicks > 0;
        if (!limitBlocked
                && this.focusedSlot != null
                && this.focusedSlot.hasStack()
                && this.focusedSlot.inventory != this.handler.getCyberwareInventory()
                && this.focusedSlot.getStack().getItem() instanceof CyberwareItem hoveredCi) {
            limitBlocked = !this.handler.canFitImplant(hoveredCi.getSlotType().getIndex(), hoveredCi);
        }
        if (!limitBlocked && !this.handler.getCursorStack().isEmpty()
                && this.handler.getCursorStack().getItem() instanceof CyberwareItem cursorCi) {
            limitBlocked = !this.handler.canFitImplant(cursorCi.getSlotType().getIndex(), cursorCi);
        }

        int gaugeColor;
        if (cap > 90) {
            gaugeColor = flashTick ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_DARK;
        } else if (cap > 75) {
            gaugeColor = CyberUiRenderer.CP_YELLOW;
        } else {
            gaugeColor = CyberUiRenderer.CP_CYAN;
        }

        int borderColor = (limitBlocked || cap > 90)
                ? (flashTick ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_DARK)
                : CyberUiRenderer.CP_CYAN_DIM;
        int accentColor = limitBlocked ? CyberUiRenderer.CP_CRIMSON : (cap > 75 ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN);

        CyberUiRenderer.drawChamferedPanel(
                context,
                mx,
                my,
                boxW,
                boxH,
                4,
                CyberUiRenderer.CP_DARK,
                borderColor,
                accentColor
        );

        if (limitBlocked) {
            int warnColor = flashTick ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW;
            CyberUiRenderer.drawClampedTextCentered(
                    context,
                    this.textRenderer,
                    "MAX 100!",
                    mx + boxW * 0.5f,
                    my + 2.5f,
                    0.62f,
                    boxW - 8.0f,
                    warnColor,
                    false
            );
        } else {
            CyberUiRenderer.drawClampedText(
                    context,
                    this.textRenderer,
                    "CAP",
                    mx + 4,
                    my + 2.5f,
                    0.52f,
                    18.0f,
                    CyberUiRenderer.CP_TEXT_MUTED,
                    false
            );
            String capStr = cap + "/" + maxCap;
            int numColor = cap > 90 ? CyberUiRenderer.CP_CRIMSON
                    : (cap > 75 ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN);
            CyberUiRenderer.drawClampedTextRight(
                    context,
                    this.textRenderer,
                    capStr,
                    mx + boxW - 4,
                    my + 2.5f,
                    0.58f,
                    40.0f,
                    numColor,
                    false
            );
        }

        float ratio = Math.min(1.0f, Math.max(0.0f, cap / (float) maxCap));
        int fill = limitBlocked ? (flashTick ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW) : gaugeColor;
        CyberUiRenderer.drawSegmentedGauge(
                context,
                mx + 4,
                my + 10,
                boxW - 9,
                4,
                ratio,
                10,
                fill,
                0x337A1924,
                0x66060408
        );
    }

    private void drawCyberpsychosisBar(DrawContext context, int bx, int by) {
        int barW = 106;
        int barH = 11;
        int psychosis = CyberwareState.getCyberpsychosis();
        int pct = Math.min(100, Math.max(0, psychosis / 10));
        int barColor = pct >= 70 ? CyberUiRenderer.CP_CRIMSON
                : (pct >= 40 ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN);

        CyberUiRenderer.drawChamferedPanel(
                context,
                bx,
                by,
                barW,
                barH,
                3,
                CyberUiRenderer.CP_DARK,
                pct >= 70 ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_DARK,
                barColor
        );

        Text label = Text.translatable("cyber.ui.psychosis");
        CyberUiRenderer.drawClampedText(
                context,
                this.textRenderer,
                label,
                bx + 4,
                by + 1.5f,
                0.50f,
                66.0f,
                barColor,
                false
        );

        CyberUiRenderer.drawClampedTextRight(
                context,
                this.textRenderer,
                pct + "%",
                bx + barW - 4,
                by + 1.5f,
                0.52f,
                26.0f,
                pct >= 70 ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW,
                false
        );

        CyberUiRenderer.drawSegmentedGauge(
                context,
                bx + 4,
                by + 7,
                barW - 9,
                2,
                pct / 100.0f,
                12,
                barColor,
                0x28FF4D5A,
                0x55080306
        );
    }

    private void drawTargetingReticle(DrawContext context, int x1, int y1, int x2, int y2) {
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int w = x2 - x1;
        int h = y2 - y1;
        int bracketColor = CyberwareState.getCyberpsychosis() >= 700
                ? CyberUiRenderer.CP_CRIMSON
                : CyberUiRenderer.CP_CYAN;

        // Subtle crimson/cyan anatomical spine & horizon grid
        context.fill(cx, y1 + 8, cx + 1, y2 - 8, 0x245CEBF5);
        context.fill(x1 + 8, cy, x2 - 8, cy + 1, 0x24FF4D5A);

        // Crisp 1px Kiroshi corner brackets
        CyberUiRenderer.drawCornerBrackets(context, x1 + 2, y1 + 2, w - 4, h - 4, 8, bracketColor);

        // Subtle sweeping holographic scan beam across chamber
        int scanRange = Math.max(1, h - 14);
        int scanY = y1 + 7 + (int) ((System.currentTimeMillis() / 22L) % scanRange);
        context.fill(x1 + 2, scanY - 2, x2 - 2, scanY + 3, 0x1E5CEBF5);
        context.fill(x1 + 2, scanY, x2 - 2, scanY + 1, 0x99FF4D5A);
    }

    private void drawDataRainMargins(DrawContext context, int x, int y) {
        long now = System.currentTimeMillis();
        int topY = y + 32;
        int botY = y + 137;
        int spanY = botY - topY;

        int[] leftCols = {x + 6, x + 12, x + 18};
        int[] rightCols = {x + 201, x + 207, x + 213};

        for (int c = 0; c < 3; c++) {
            drawRainColumn(context, leftCols[c], topY, spanY, now, c * 37 + 11, c % 2 == 0);
            drawRainColumn(context, rightCols[c], topY, spanY, now, c * 53 + 29, c % 2 != 0);
        }

        String[] hexCodes = {"A7", "F0", "3C", "9E", "1B", "D4", "88", "77"};
        int idxL = (int) ((now / 420L) % hexCodes.length);
        int idxR = (int) ((now / 360L + 3) % hexCodes.length);

        CyberUiRenderer.drawClampedText(
                context, this.textRenderer, "0x" + hexCodes[idxL],
                x + 5, y + 24, 0.45f, 18.0f, 0xAAFF4D5A, false
        );
        CyberUiRenderer.drawClampedText(
                context, this.textRenderer, "0x" + hexCodes[idxR],
                x + 198, y + 24, 0.45f, 18.0f, 0xAA5CEBF5, false
        );
    }

    private static void drawRainColumn(
            DrawContext context,
            int colX,
            int topY,
            int spanY,
            long now,
            int seed,
            boolean crimson
    ) {
        int speed = 42 + (seed % 26);
        int headOffset = (int) (((now / speed) + seed * 7L) % spanY);
        int tailLen = 12 + (seed % 8);
        int rgb = crimson ? 0xFF4D5A : 0x5CEBF5;
        for (int t = 0; t < tailLen; t++) {
            int dy = headOffset - t * 2;
            if (dy < 0 || dy >= spanY - 1) {
                continue;
            }
            int py = topY + dy;
            int color;
            if (t == 0) {
                color = crimson ? 0xAAFF4D5A : 0xAA5CEBF5;
            } else {
                int alpha = Math.max(0x14, 0x4C - t * 4);
                color = (alpha << 24) | rgb;
            }
            context.fill(colX, py, colX + 1, py + 1, color);
        }
    }

    private void drawHolographicPlatform(DrawContext context, int cx, int cy, int chamberTopY) {
        long now = System.currentTimeMillis();
        double spin = (now % 4200L) / 4200.0 * Math.PI * 2.0;

        // Vertical holographic containment columns
        context.fillGradient(cx - 28, chamberTopY + 34, cx - 27, cy, 0x00FF4D5A, 0x44FF4D5A);
        context.fillGradient(cx + 27, chamberTopY + 34, cx + 28, cy, 0x005CEBF5, 0x445CEBF5);

        // Dark-red / ice-cyan concentric holographic floor disc
        drawHoloEllipse(context, cx, cy, 28.0f, 7.5f, 40, 0x88FF4D5A);
        drawHoloEllipse(context, cx, cy, 20.0f, 5.2f, 30, 0x995CEBF5);
        drawHoloEllipse(context, cx, cy, 11.0f, 3.0f, 20, 0xAAFCEE09);

        // Rotating calibration ticks on outer ring
        for (int i = 0; i < 8; i++) {
            double ang = spin + i * (Math.PI / 4.0);
            int tx = cx + (int) Math.round(Math.cos(ang) * 28.0);
            int ty = cy + (int) Math.round(Math.sin(ang) * 7.5);
            int tickColor = (i % 2 == 0) ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;
            context.fill(tx - 1, ty, tx + 2, ty + 1, tickColor);
            context.fill(tx, ty - 1, tx + 1, ty + 2, tickColor);
        }
    }

    private static void drawHoloEllipse(DrawContext context, int cx, int cy, float rx, float ry, int steps, int color) {
        for (int i = 0; i < steps; i++) {
            double ang = (i / (double) steps) * Math.PI * 2.0;
            int px = cx + (int) Math.round(Math.cos(ang) * rx);
            int py = cy + (int) Math.round(Math.sin(ang) * ry);
            context.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void drawEcgBiomonitor(DrawContext context, int bx, int by) {
        int bw = 38;
        int bh = 18;
        int cap = this.handler.getTotalEquippedCapacity();
        int psychosis = CyberwareState.getCyberpsychosis();
        float stress = Math.max(cap / 100.0f, psychosis / 1000.0f);

        int waveColor;
        if (psychosis >= 700 || cap > 90) {
            waveColor = CyberUiRenderer.CP_CRIMSON;
        } else if (psychosis >= 400 || cap > 75) {
            waveColor = CyberUiRenderer.CP_YELLOW;
        } else {
            waveColor = CyberUiRenderer.CP_CYAN;
        }

        int bpm = Math.round(72 + stress * 96.0f);
        CyberUiRenderer.drawChamferedPanel(
                context, bx, by, bw, bh, 3, 0xCC0C0508, CyberUiRenderer.CP_CRIMSON_DARK, waveColor
        );

        CyberUiRenderer.drawClampedText(
                context, this.textRenderer, "ECG " + bpm,
                bx + 4, by + 2, 0.46f, bw - 7.0f, waveColor, false
        );

        int waveLeft = bx + 3;
        int waveW = bw - 6;
        int baselineY = by + 13;
        double beatPeriodMs = 60000.0 / Math.max(60, bpm);
        long now = System.currentTimeMillis();
        int headCol = (int) ((now / 36L) % waveW);

        int prevPy = baselineY;
        for (int i = 0; i < waveW; i++) {
            int ageFromHead = (headCol - i + waveW) % waveW;
            if (ageFromHead > waveW - 4) {
                continue;
            }
            long sampleTime = now - ageFromHead * 36L;
            double phase = (sampleTime % (long) beatPeriodMs) / beatPeriodMs;
            int offset = getEcgWaveOffset(phase);
            int py = Math.max(by + 8, Math.min(by + 16, baselineY + offset));

            int alpha = Math.max(0x30, 0xFF - ageFromHead * 7);
            int segColor = (ageFromHead == 0) ? 0xFFFFFFFF : ((alpha << 24) | (waveColor & 0x00FFFFFF));
            if (i > 0 && ageFromHead > 0) {
                CyberUiRenderer.drawVLine(context, waveLeft + i, prevPy, py, segColor);
            } else {
                context.fill(waveLeft + i, py, waveLeft + i + 1, py + 1, segColor);
            }
            prevPy = py;
        }
    }

    private static int getEcgWaveOffset(double phase) {
        if (phase < 0.12) {
            return 0;
        } else if (phase < 0.19) {
            return -1;
        } else if (phase < 0.24) {
            return 0;
        } else if (phase < 0.28) {
            return 1;
        } else if (phase < 0.34) {
            return -4;
        } else if (phase < 0.39) {
            return 2;
        } else if (phase < 0.48) {
            return 0;
        } else if (phase < 0.58) {
            return -1;
        }
        return 0;
    }

    private void drawCircuitTraces(DrawContext context, int x, int y, float delta) {
        long now = System.currentTimeMillis();
        float pulse = (now % 1300L) / 1300.0f;
        float nodePulse = 0.5f + 0.5f * (float) Math.sin(now / 190.0);

        for (CyberwareSlotType type : CyberwareSlotType.values()) {
            int idx = type.getIndex();
            ItemStack stack = this.handler.getCyberwareInventory().getStack(idx);
            boolean equipped = !stack.isEmpty();
            int shockTicks = this.slotShockwaveTicks[idx];
            boolean shocking = shockTicks > 0;

            int traceColor = shocking
                    ? CyberUiRenderer.CP_YELLOW
                    : (equipped ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CYAN_DIM);
            int nodeColor = equipped
                    ? (nodePulse > 0.5f ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CRIMSON)
                    : 0xAA5CEBF5;

            int sx = x + type.getSlotX();
            int sy = y + type.getSlotY() + 8;
            boolean isLeft = type.getSlotX() < 100;

            int startX = isLeft ? sx + 18 : sx - 2;
            int targetX = getTargetBodyX(x, type);
            int targetY = getTargetBodyY(y, type);
            int elbowX = isLeft ? x + 55 : x + 167;

            CyberUiRenderer.drawHLine(context, startX, elbowX, sy, traceColor);
            CyberUiRenderer.drawVLine(context, elbowX, sy, targetY, traceColor);
            CyberUiRenderer.drawHLine(context, elbowX, targetX, targetY, traceColor);

            // Crisp glowing diamond node at body attachment zone
            if (equipped) {
                int haloAlpha = (int) (0x44 + nodePulse * 0x55);
                int haloColor = (haloAlpha << 24) | 0xFF4D5A;
                CyberUiRenderer.drawDiamondNode(context, targetX, targetY, 2, haloColor);
            }
            CyberUiRenderer.drawDiamondNode(context, targetX, targetY, 1, nodeColor);
            context.fill(targetX, targetY, targetX + 1, targetY + 1, equipped ? 0xFFFFFFFF : CyberUiRenderer.CP_CYAN);

            // Traveling energy pulse along schematic trace
            drawTracePulse(context, startX, sy, elbowX, targetX, targetY, pulse, equipped);

            // 15-tick install/remove shockwave surge
            if (shocking) {
                float surgeProgress = Math.max(0.0f, Math.min(1.0f,
                        (SHOCKWAVE_DURATION - shockTicks + (1.0f - delta)) / (float) SHOCKWAVE_DURATION));
                int surgeAlpha = (int) ((1.0f - surgeProgress) * 230);
                int surgeColor = (surgeAlpha << 24) | 0xFCEE09;
                CyberUiRenderer.drawHLine(context, startX, elbowX, sy - 1, surgeColor);
                CyberUiRenderer.drawHLine(context, elbowX, targetX, targetY - 1, surgeColor);
                drawTracePulse(context, startX, sy, elbowX, targetX, targetY, surgeProgress, true);
                int burstR = 2 + Math.round(surgeProgress * 3.0f);
                CyberUiRenderer.drawDiamondNode(context, targetX, targetY, burstR, (surgeAlpha << 24) | 0x5CEBF5);
            }
        }
    }

    private static void drawTracePulse(
            DrawContext context,
            int startX,
            int sy,
            int elbowX,
            int targetX,
            int targetY,
            float pulse,
            boolean equipped
    ) {
        int d1 = Math.abs(elbowX - startX);
        int d2 = Math.abs(targetY - sy);
        int d3 = Math.abs(targetX - elbowX);
        int total = d1 + d2 + d3;
        if (total <= 0) {
            return;
        }

        float dist = pulse * total;
        int px;
        int py;
        if (dist <= d1) {
            float t = d1 == 0 ? 0.0f : dist / d1;
            px = Math.round(startX + (elbowX - startX) * t);
            py = sy;
        } else if (dist <= d1 + d2) {
            float t = d2 == 0 ? 0.0f : (dist - d1) / d2;
            px = elbowX;
            py = Math.round(sy + (targetY - sy) * t);
        } else {
            float t = d3 == 0 ? 0.0f : (dist - d1 - d2) / d3;
            px = Math.round(elbowX + (targetX - elbowX) * t);
            py = targetY;
        }

        int glowColor = equipped ? 0x66FF4D5A : 0x555CEBF5;
        int coreColor = equipped ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;
        context.fill(px - 1, py - 1, px + 2, py + 2, glowColor);
        context.fill(px, py, px + 1, py + 1, coreColor);
    }

    private int getTargetBodyX(int x, CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX, OCULAR -> x + 106;
            case CIRCULATORY -> x + 105;
            case IMMUNE -> x + 107;
            case NERVOUS -> x + 108;
            case OPERATING_SYSTEM, SKELETON -> x + 116;
            case HANDS -> x + 128;
            case ARMS -> x + 125;
            case LEGS -> x + 116;
        };
    }

    private int getTargetBodyY(int y, CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> y + 34;
            case OCULAR -> y + 44;
            case CIRCULATORY -> y + 66;
            case IMMUNE -> y + 80;
            case NERVOUS -> y + 96;
            case OPERATING_SYSTEM -> y + 42;
            case SKELETON -> y + 64;
            case HANDS -> y + 86;
            case ARMS -> y + 72;
            case LEGS -> y + 116;
        };
    }

    private void drawSlotFrames(DrawContext context, int x, int y, float delta) {
        ItemStack cursor = this.handler.getCursorStack();
        CyberwareItem cursorCyber = (!cursor.isEmpty() && cursor.getItem() instanceof CyberwareItem ci) ? ci : null;
        boolean pulseFast = ((System.currentTimeMillis() / 180L) % 2L == 0L);

        for (Slot slot : this.handler.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            boolean hovered = (slot == this.focusedSlot);

            if (slot.inventory == this.handler.getCyberwareInventory()) {
                int idx = slot.getIndex();
                CyberwareSlotType type = CyberwareSlotType.values()[idx];
                boolean equipped = slot.hasStack();

                boolean cursorTargetsThis = cursorCyber != null && cursorCyber.getSlotType() == type;
                boolean cursorBlocked = cursorTargetsThis && !this.handler.canFitImplant(idx, cursorCyber);

                int fillColor = equipped ? 0xDD1E080F : 0xCC0D070C;
                int borderColor;
                int tierBarColor;

                if (cursorBlocked) {
                    borderColor = pulseFast ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_DARK;
                    tierBarColor = CyberUiRenderer.CP_CRIMSON;
                } else if (cursorTargetsThis) {
                    borderColor = pulseFast ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;
                    tierBarColor = CyberUiRenderer.CP_YELLOW;
                } else if (equipped) {
                    boolean iconicTier = slot.getStack().getItem() instanceof CyberwareItem ci
                            && ci.getCyberCapacityCost() >= 25;
                    borderColor = hovered ? CyberUiRenderer.CP_YELLOW
                            : (iconicTier ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW);
                    tierBarColor = iconicTier ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW;
                } else {
                    borderColor = hovered ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;
                    tierBarColor = hovered ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_CYAN_DIM;
                }

                // 20x20 chamfered Cyberpunk 2077 implant socket
                CyberUiRenderer.drawChamferedSlot(context, sx - 2, sy - 2, 20, 4, fillColor, borderColor, tierBarColor);

                // Installed top-right active status pip
                if (equipped) {
                    context.fill(sx + 14, sy - 1, sx + 17, sy + 1, tierBarColor);
                }

                // 15-tick install/remove expanding shockwave
                int shockTicks = this.slotShockwaveTicks[idx];
                if (shockTicks > 0) {
                    float progress = Math.max(0.0f, Math.min(1.0f,
                            (SHOCKWAVE_DURATION - shockTicks + (1.0f - delta)) / (float) SHOCKWAVE_DURATION));
                    int expand = 1 + Math.round(progress * 5.0f);
                    int alpha = (int) ((1.0f - progress) * 235);
                    int waveColor = (alpha << 24) | (equipped ? 0xFCEE09 : 0x5CEBF5);
                    int innerFlash = ((int) ((1.0f - progress) * 130) << 24) | (equipped ? 0xFF4D5A : 0x5CEBF5);
                    context.fill(sx - 1, sy - 1, sx + 17, sy + 17, innerFlash);
                    CyberUiRenderer.drawBorder(context, sx - 2 - expand, sy - 2 - expand, 20 + expand * 2, 20 + expand * 2, waveColor);
                }

                if (!equipped) {
                    drawHologramIcon(context, sx + 2, sy + 2, type);

                    String code = getSlotCode(type);
                    int codeColor = type == CyberwareSlotType.OPERATING_SYSTEM
                            ? 0xDDFCEE09
                            : 0xCC5CEBF5;
                    CyberUiRenderer.drawClampedTextRight(
                            context,
                            this.textRenderer,
                            code,
                            sx + 15.5f,
                            sy + 11.0f,
                            0.48f,
                            12.0f,
                            codeColor,
                            false
                    );
                }
            } else {
                // Player inventory / hotbar chamfered socket frame
                boolean overLimitHover = hovered
                        && slot.hasStack()
                        && slot.getStack().getItem() instanceof CyberwareItem hoveredCi
                        && !this.handler.canFitImplant(hoveredCi.getSlotType().getIndex(), hoveredCi);

                int invBorder = overLimitHover
                        ? (pulseFast ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_YELLOW)
                        : (hovered ? CyberUiRenderer.CP_CRIMSON : CyberUiRenderer.CP_CRIMSON_DARK);
                int invFill = hovered ? 0xCC210B12 : 0xB80E060A;
                CyberUiRenderer.drawChamferedSlot(context, sx - 1, sy - 1, 18, 2, invFill, invBorder, 0);
            }
        }
    }

    private static void drawHologramIcon(DrawContext context, int ix, int iy, CyberwareSlotType type) {
        String[] pattern = getIconPattern(type);
        boolean isOs = type == CyberwareSlotType.OPERATING_SYSTEM;
        int outerColor = isOs ? 0x99FCEE09 : 0x665CEBF5;
        int coreColor = isOs ? 0xCCFF4D5A : 0xBB5CEBF5;

        for (int row = 0; row < pattern.length; row++) {
            String line = pattern[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '.') {
                    continue;
                }
                int color = (c == '#') ? coreColor : outerColor;
                context.fill(ix + col, iy + row, ix + col + 1, iy + row + 1, color);
            }
        }
    }

    private static String[] getIconPattern(CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> new String[]{
                    "..O..O..O...",
                    ".OOOOOOOOO..",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    "OO#O###O#OO.",
                    ".O#######O..",
                    ".OOOOOOOOO..",
                    "..O..O..O...",
                    "............"
            };
            case OCULAR -> new String[]{
                    "....OOOO....",
                    "..OO....OO..",
                    ".O...##...O.",
                    "O....##....O",
                    "O..######..O",
                    "O..##OO##..O",
                    "O..######..O",
                    "O....##....O",
                    ".O...##...O.",
                    "..OO....OO..",
                    "....OOOO....",
                    "............"
            };
            case CIRCULATORY -> new String[]{
                    "..OO....OO..",
                    ".O##O..O##O.",
                    "O####OO####O",
                    "O##########O",
                    "O##O####O##O",
                    ".O########O.",
                    "..O######O..",
                    "...O####O...",
                    "....O##O....",
                    ".....OO.....",
                    "............",
                    "............"
            };
            case IMMUNE -> new String[]{
                    "....OOOO....",
                    "..OO####OO..",
                    ".O###OO###O.",
                    ".O###OO###O.",
                    ".O#OOOOOO#O.",
                    ".O#OOOOOO#O.",
                    ".O###OO###O.",
                    "..O##OO##O..",
                    "...O####O...",
                    "....O##O....",
                    ".....OO.....",
                    "............"
            };
            case NERVOUS -> new String[]{
                    "......OOO...",
                    ".....O##O...",
                    "....O##O....",
                    "...O##O.....",
                    "..O#####OO..",
                    "....OOO##O..",
                    "......O##O..",
                    ".....O##O...",
                    "....O##O....",
                    "...O##O.....",
                    "...OO.......",
                    "............"
            };
            case OPERATING_SYSTEM -> new String[]{
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "...O####O...",
                    "....OOOO....",
                    "............"
            };
            case SKELETON -> new String[]{
                    "...OOOOOO...",
                    "..O######O..",
                    ".O##O##O##O.",
                    ".O##O##O##O.",
                    ".O########O.",
                    "..O##OO##O..",
                    "...OOOOOO...",
                    "...O#OO#O...",
                    "....OOOO....",
                    "..OO....OO..",
                    ".O........O.",
                    "............"
            };
            case HANDS -> new String[]{
                    "..O.O.O.O...",
                    "..#.#.#.#...",
                    "..#.#.#.#...",
                    "..O#O#O#O.O.",
                    "..O######O#.",
                    "..O#######O.",
                    "..O######O..",
                    "...O####O...",
                    "...O####O...",
                    "....OOOO....",
                    "............",
                    "............"
            };
            case ARMS -> new String[]{
                    ".OO......OO.",
                    ".O#O....O#O.",
                    "..O#O..O#O..",
                    "...O#OO#O...",
                    "....O##O....",
                    "...O####O...",
                    "..O#O..O#O..",
                    ".O#O....O#O.",
                    ".OO......OO.",
                    "............",
                    "............",
                    "............"
            };
            case LEGS -> new String[]{
                    "...OOOOO....",
                    "...O###O....",
                    "...O###O....",
                    "....O#O.....",
                    "...O###O....",
                    "...O###O....",
                    "....O#O.....",
                    "...O####OO..",
                    "..O#######O.",
                    "..OOOOOOOOO.",
                    "............",
                    "............"
            };
        };
    }

    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> tooltip = super.getTooltipFromItem(stack);
        if (this.focusedSlot != null
                && this.focusedSlot.hasStack()
                && this.focusedSlot.getStack() == stack
                && this.focusedSlot.inventory != this.handler.getCyberwareInventory()
                && stack.getItem() instanceof CyberwareItem hoveredCi
                && !this.handler.canFitImplant(hoveredCi.getSlotType().getIndex(), hoveredCi)) {
            List<Text> extended = new ArrayList<>(tooltip);
            extended.add(Text.translatable("cyber.ui.limit_exceeded").formatted(Formatting.RED, Formatting.BOLD));
            return extended;
        }
        return tooltip;
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        super.drawMouseoverTooltip(context, x, y);
        if (this.handler.getCursorStack().isEmpty()
                && this.focusedSlot != null
                && !this.focusedSlot.hasStack()
                && this.focusedSlot.inventory == this.handler.getCyberwareInventory()) {
            CyberwareSlotType type = CyberwareSlotType.values()[this.focusedSlot.getIndex()];
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable(type.getTranslationKey()).formatted(Formatting.AQUA, Formatting.BOLD));
            if (type == CyberwareSlotType.OPERATING_SYSTEM) {
                lines.add(Text.literal("Установите Сандевистан или ОС").formatted(Formatting.YELLOW));
            }
            lines.add(Text.literal("SLOT // " + getSlotCode(type)).formatted(Formatting.DARK_GRAY));
            context.drawTooltip(this.textRenderer, lines, x, y);
        }
    }

    private static String getSlotCode(CyberwareSlotType type) {
        return switch (type) {
            case FRONTAL_CORTEX -> "FC";
            case OCULAR -> "OC";
            case CIRCULATORY -> "CR";
            case IMMUNE -> "IM";
            case NERVOUS -> "NV";
            case OPERATING_SYSTEM -> "OS";
            case SKELETON -> "SK";
            case HANDS -> "HD";
            case ARMS -> "AR";
            case LEGS -> "LG";
        };
    }

    private class CyberButton extends ButtonWidget {
        private boolean wasHovered = false;

        protected CyberButton(int x, int y, int width, int height, Text message, PressAction onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        }

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

            int bg = hovered ? 0xEE2C0C15 : 0xE616080D;
            int border = hovered ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CRIMSON;
            int accent = hovered ? CyberUiRenderer.CP_CYAN : CyberUiRenderer.CP_YELLOW;
            int textColor = hovered ? CyberUiRenderer.CP_YELLOW : CyberUiRenderer.CP_CYAN;

            CyberUiRenderer.drawChamferedPanel(
                    context,
                    this.getX(),
                    this.getY(),
                    this.getWidth(),
                    this.getHeight(),
                    4,
                    bg,
                    border,
                    accent
            );

            CyberUiRenderer.drawClampedTextCentered(
                    context,
                    textRenderer,
                    this.getMessage(),
                    this.getX() + this.getWidth() * 0.5f + 1.0f,
                    this.getY() + 3.5f,
                    0.70f,
                    this.getWidth() - 8.0f,
                    textColor,
                    false
            );
        }
    }
}
