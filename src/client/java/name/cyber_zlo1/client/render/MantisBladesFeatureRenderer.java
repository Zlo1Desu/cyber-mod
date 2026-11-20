package name.cyber_zlo1.client.render;

import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.client.screen.CyberwareScreen;
import name.cyber_zlo1.cyberware.CyberwareSlotType;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MantisBladesFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final float DEPLOY_STEP = 0.125f; // ~8 ticks smooth mechanical deploy/retract
    private static final Map<UUID, DeployAnimState> DEPLOY_STATES = new HashMap<>();

    // Arasaka Cyberpunk 2077 palette (normalized RGB)
    private static final float CARBON_R = 0.078f, CARBON_G = 0.090f, CARBON_B = 0.122f; // #14171F matte carbon
    private static final float STEEL_R  = 0.155f, STEEL_G  = 0.180f, STEEL_B  = 0.235f; // brushed dark steel
    private static final float TITAN_R  = 0.420f, TITAN_G  = 0.465f, TITAN_B  = 0.540f; // machined titanium
    private static final float CHROME_R = 0.790f, CHROME_G = 0.840f, CHROME_B = 0.890f; // polished chrome hinge
    private static final float CORAL_R  = 1.000f, CORAL_G  = 0.302f, CORAL_B  = 0.353f; // #FF4D5A Arasaka coral-red
    private static final float CYAN_R   = 0.361f, CYAN_G   = 0.922f, CYAN_B   = 0.961f; // #5CEBF5 ice-cyan LED

    public MantisBladesFeatureRenderer(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context
    ) {
        super(context);
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.world == null) {
            DEPLOY_STATES.clear();
            return;
        }
        if (client.isPaused()) {
            return;
        }

        if (DEPLOY_STATES.size() > 64) {
            DEPLOY_STATES.keySet().removeIf(uuid -> client.world.getPlayerByUuid(uuid) == null);
        }

        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            UUID uuid = player.getUuid();
            boolean hasBlades = shouldRenderBlades(player);
            boolean targetDeployed = hasBlades && player.getMainHandStack().isEmpty();
            float target = targetDeployed ? 1.0f : 0.0f;

            DeployAnimState state = DEPLOY_STATES.get(uuid);
            if (state == null) {
                state = new DeployAnimState(target);
                DEPLOY_STATES.put(uuid, state);
                continue;
            }

            state.prevProgress = state.progress;
            if (state.progress < target) {
                state.progress = Math.min(target, state.progress + DEPLOY_STEP);
            } else if (state.progress > target) {
                state.progress = Math.max(target, state.progress - DEPLOY_STEP);
            }
        }
    }

    public static float getDeployProgress(AbstractClientPlayerEntity player, float tickDelta) {
        if (player == null) {
            return 0.0f;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.currentScreen instanceof CyberwareScreen
                && player == client.player) {
            return player.getMainHandStack().isEmpty() ? 1.0f : 0.0f;
        }
        DeployAnimState state = DEPLOY_STATES.get(player.getUuid());
        if (state == null) {
            return player.getMainHandStack().isEmpty() ? 1.0f : 0.0f;
        }
        float linear = MathHelper.clamp(MathHelper.lerp(tickDelta, state.prevProgress, state.progress), 0.0f, 1.0f);
        return linear * linear * (3.0f - 2.0f * linear);
    }

    public static boolean shouldRenderBlades(AbstractClientPlayerEntity player) {
        if (player == null) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null
                && client.currentScreen instanceof CyberwareScreen cs
                && player == client.player) {
            return cs.getScreenHandler()
                    .getCyberwareInventory()
                    .getStack(CyberwareSlotType.ARMS.getIndex())
                    .isOf(Cyber.MANTIS_BLADES);
        }
        return CyberwareState.hasPlayerMantisBlades(player);
    }

    @Override
    public void render(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            AbstractClientPlayerEntity player,
            float limbAngle,
            float limbDistance,
            float tickDelta,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        if (!shouldRenderBlades(player)) {
            return;
        }
        float deployProgress = getDeployProgress(player, tickDelta);

        matrices.push();
        this.getContextModel().rightArm.rotate(matrices);
        renderArmMantisBlade(matrices, vertexConsumers, true, deployProgress, player.age, tickDelta);
        matrices.pop();

        matrices.push();
        this.getContextModel().leftArm.rotate(matrices);
        renderArmMantisBlade(matrices, vertexConsumers, false, deployProgress, player.age, tickDelta);
        matrices.pop();
    }

    public static void renderArmMantisBlade(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            boolean isRight,
            boolean deployed,
            int age
    ) {
        renderArmMantisBlade(matrices, vertexConsumers, isRight, deployed ? 1.0f : 0.0f, age, 0.0f);
    }

    public static void renderArmMantisBlade(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            boolean isRight,
            float deployProgress,
            int age,
            float tickDelta
    ) {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getDebugFilledBox());
        double side = isRight ? -1.0 : 1.0;
        float deploy = MathHelper.clamp(deployProgress, 0.0f, 1.0f);
        float animAge = age + tickDelta;
        float ledPulse = 0.80f + 0.20f * (float) Math.sin(animAge * 0.36);
        float thermalPulse = 0.50f + 0.50f * (float) Math.sin(animAge * 0.52);

        // ------------------------------------------------------------------
        // 1) Sleek Contoured Matte-Carbon Arasaka Forearm Shell (flush fit)
        // ------------------------------------------------------------------
        // Slim inner sub-frame flush against outer arm skin
        box(matrices, buffer, side * 0.138, 0.155, -0.118, side * 0.162, 0.545, 0.118,
                STEEL_R, STEEL_G, STEEL_B, 0.98f);

        // Split matte-carbon cyber-shell panels (#14171F) that open slightly when deployed
        double shellSpread = 0.008 * deploy;
        // Front shell half (-Z side)
        box(matrices, buffer, side * 0.158, 0.160, -0.124 - shellSpread, side * 0.178, 0.540, -0.010 - shellSpread,
                CARBON_R, CARBON_G, CARBON_B, 0.99f);
        // Rear shell half (+Z side)
        box(matrices, buffer, side * 0.158, 0.160, 0.010 + shellSpread, side * 0.178, 0.540, 0.124 + shellSpread,
                CARBON_R, CARBON_G, CARBON_B, 0.99f);

        // Low-profile wrist & elbow brushed-steel retention bands
        box(matrices, buffer, side * 0.090, 0.152, -0.128, side * 0.181, 0.174, 0.128,
                STEEL_R, STEEL_G, STEEL_B, 0.98f);
        box(matrices, buffer, side * 0.090, 0.526, -0.128, side * 0.181, 0.548, 0.128,
                STEEL_R, STEEL_G, STEEL_B, 0.98f);

        // Razor-thin 0.008 Arasaka coral-red (#FF4D5A) LED seam along the forearm split
        box(matrices, buffer, side * 0.174, 0.176, -0.004, side * 0.182, 0.524, 0.004,
                CORAL_R, CORAL_G, CORAL_B, (0.65f + 0.35f * deploy) * ledPulse);
        // Thin 0.008 coral-red wrist cuff LED accent
        box(matrices, buffer, side * 0.156, 0.518, -0.128, side * 0.182, 0.526, 0.128,
                CORAL_R, CORAL_G, CORAL_B, 0.92f * ledPulse);
        // Thin 0.008 ice-cyan (#5CEBF5) neural telemetry trace along upper rear seam
        box(matrices, buffer, side * 0.174, 0.184, 0.078, side * 0.182, 0.496, 0.086,
                CYAN_R, CYAN_G, CYAN_B, 0.92f * ledPulse);

        if (deploy <= 0.02f) {
            // Sleek retracted blade cover flush against forearm
            box(matrices, buffer, side * 0.176, 0.165, -0.048, side * 0.190, 0.535, 0.048,
                    CARBON_R, CARBON_G, CARBON_B, 0.99f);
            box(matrices, buffer, side * 0.184, 0.180, -0.004, side * 0.192, 0.520, 0.004,
                    CORAL_R, CORAL_G, CORAL_B, 0.85f * ledPulse);
            box(matrices, buffer, side * 0.184, 0.195, 0.036, side * 0.192, 0.360, 0.044,
                    CYAN_R, CYAN_G, CYAN_B, 0.85f * ledPulse);
            return;
        }

        // ------------------------------------------------------------------
        // 2) Thin Titanium Hydraulic Scissor-Struts & Chrome Hinges
        // ------------------------------------------------------------------
        float lowerAngle = -36.0f * deploy;
        float upperAngle = 24.0f * deploy;
        double lowerLen = 0.06 + 0.185 * deploy;
        double upperLen = 0.06 + 0.165 * deploy;

        // Lower scissor strut (titanium sleeve + slender chrome hydraulic rod)
        matrices.push();
        matrices.translate(0.0, 0.445, -0.085);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(lowerAngle));
        box(matrices, buffer, side * 0.168, -0.008, -lowerLen * 0.56, side * 0.186, 0.012, 0.0,
                TITAN_R, TITAN_G, TITAN_B, 0.99f);
        box(matrices, buffer, side * 0.172, -0.004, -lowerLen, side * 0.182, 0.008, -lowerLen * 0.42,
                CHROME_R, CHROME_G, CHROME_B, 0.99f);
        matrices.pop();

        // Upper scissor strut (titanium sleeve + slender chrome hydraulic rod)
        matrices.push();
        matrices.translate(0.0, 0.215, -0.085);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(upperAngle));
        box(matrices, buffer, side * 0.168, -0.008, -upperLen * 0.56, side * 0.186, 0.010, 0.0,
                TITAN_R, TITAN_G, TITAN_B, 0.99f);
        box(matrices, buffer, side * 0.172, -0.004, -upperLen, side * 0.182, 0.006, -upperLen * 0.42,
                CHROME_R, CHROME_G, CHROME_B, 0.99f);
        matrices.pop();

        // Animated mechanical pivot position
        double hingeY = MathHelper.lerp(deploy, 0.34, 0.29);
        double hingeZ = MathHelper.lerp(deploy, -0.11, -0.275);

        matrices.push();
        matrices.translate(0.0, hingeY, hingeZ);

        // Compact machined-chrome & Arasaka coral-red hinge knuckle
        box(matrices, buffer, side * 0.164, -0.020, -0.020, side * 0.194, 0.026, 0.022,
                TITAN_R, TITAN_G, TITAN_B, 0.99f);
        box(matrices, buffer, side * 0.160, -0.011, -0.011, side * 0.198, 0.015, 0.013,
                CHROME_R, CHROME_G, CHROME_B, 0.99f);
        box(matrices, buffer, side * 0.171, -0.016, 0.018, side * 0.187, 0.020, 0.026,
                CORAL_R, CORAL_G, CORAL_B, 0.96f * ledPulse);

        // ------------------------------------------------------------------
        // 3) Razor-Thin (0.014 width!) Tapering Curved Arasaka Cyber-Blade
        // ------------------------------------------------------------------
        float bladeAngle = -20.0f * deploy - 10.0f * (float) Math.sin(deploy * Math.PI);
        float bladeScaleY = 0.34f + 0.66f * deploy;
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bladeAngle));
        matrices.scale(1.0f, bladeScaleY, 1.0f);

        // Blade X-center and razor-thin half-width (total blade width = 0.014!)
        double bx = side * 0.179;
        double halfW = 0.007;      // 0.014 total width
        double edgeHalfW = 0.0045; // 0.009 razor cutting edge
        double circuitHalfW = 0.008; // hairline ice-cyan circuit inset slightly proud of spine

        float heatG = 0.42f + 0.24f * thermalPulse;
        float hotYelR = 1.0f, hotYelG = 0.88f + 0.10f * thermalPulse, hotYelB = 0.16f + 0.18f * thermalPulse;

        // Segment 1 (Heel -> Lower Mid): full blade depth with dark-steel spine, thermal vents & cyan circuit
        box(matrices, buffer, bx - halfW, 0.00, -0.032, bx + halfW, 0.25, 0.034,
                CARBON_R, CARBON_G, CARBON_B, 0.99f);
        box(matrices, buffer, bx - halfW * 0.85, 0.01, 0.010, bx + halfW * 0.85, 0.25, 0.038,
                STEEL_R, STEEL_G, STEEL_B, 0.99f);
        // Fine ice-cyan circuit trace along Segment 1
        box(matrices, buffer, bx - circuitHalfW, 0.03, 0.014, bx + circuitHalfW, 0.245, 0.022,
                CYAN_R, CYAN_G, CYAN_B, 0.95f * ledPulse);
        // Micro thermal radiator slits near heel (Arasaka coral-red)
        box(matrices, buffer, bx - circuitHalfW, 0.055, -0.010, bx + circuitHalfW, 0.075, 0.006,
                CORAL_R, CORAL_G, CORAL_B, 0.90f * ledPulse);
        box(matrices, buffer, bx - circuitHalfW, 0.095, -0.010, bx + circuitHalfW, 0.115, 0.006,
                CORAL_R, CORAL_G, CORAL_B, 0.90f * ledPulse);
        // Thermal orange-yellow glowing cutting edge (-Z)
        box(matrices, buffer, bx - edgeHalfW, 0.01, -0.048, bx + edgeHalfW, 0.255, -0.030,
                1.0f, heatG, 0.06f, 0.98f);
        box(matrices, buffer, bx - 0.0028, 0.015, -0.056, bx + 0.0028, 0.255, -0.045,
                hotYelR, hotYelG, hotYelB, 0.96f);

        // Segment 2 (Mid Blade — gentle -5.5 deg forward scythe curve, tapering chord)
        matrices.push();
        matrices.translate(0.0, 0.245, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-5.5f));
        box(matrices, buffer, bx - halfW, 0.00, -0.032, bx + halfW, 0.235, 0.024,
                CARBON_R, CARBON_G, CARBON_B, 0.99f);
        box(matrices, buffer, bx - halfW * 0.85, 0.00, 0.006, bx + halfW * 0.85, 0.235, 0.028,
                STEEL_R, STEEL_G, STEEL_B, 0.99f);
        // Fine ice-cyan circuit line continuing smoothly
        box(matrices, buffer, bx - circuitHalfW, 0.00, 0.009, bx + circuitHalfW, 0.228, 0.016,
                CYAN_R, CYAN_G, CYAN_B, 0.95f * ledPulse);
        // Thermal cutting edge
        box(matrices, buffer, bx - edgeHalfW, 0.00, -0.048, bx + edgeHalfW, 0.240, -0.030,
                1.0f, heatG, 0.06f, 0.98f);
        box(matrices, buffer, bx - 0.0028, 0.00, -0.056, bx + 0.0028, 0.240, -0.045,
                hotYelR, hotYelG, hotYelB, 0.97f);

        // Segment 3 (Upper Taper — -7.0 deg curve, sleek narrowing silhouette)
        matrices.push();
        matrices.translate(0.0, 0.230, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-7.0f));
        double upperHalfW = 0.006; // 0.012 width tapering cleanly
        box(matrices, buffer, bx - upperHalfW, 0.00, -0.030, bx + upperHalfW, 0.195, 0.011,
                STEEL_R, STEEL_G, STEEL_B, 0.99f);
        box(matrices, buffer, bx - (upperHalfW + 0.001), 0.00, 0.001, bx + (upperHalfW + 0.001), 0.150, 0.007,
                CYAN_R, CYAN_G, CYAN_B, 0.92f * ledPulse);
        // Thermal cutting edge
        box(matrices, buffer, bx - 0.0040, 0.00, -0.045, bx + 0.0040, 0.200, -0.028,
                1.0f, heatG + 0.06f, 0.08f, 0.98f);
        box(matrices, buffer, bx - 0.0024, 0.00, -0.053, bx + 0.0024, 0.205, -0.042,
                hotYelR, hotYelG, hotYelB, 0.98f);

        // Segment 4 (Razor Needle Apex — -8.5 deg predatory mantis hook point)
        matrices.push();
        matrices.translate(0.0, 0.192, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-8.5f));
        box(matrices, buffer, bx - 0.0045, 0.00, -0.036, bx + 0.0045, 0.095, -0.006,
                1.0f, heatG + 0.12f, 0.10f, 0.99f);
        box(matrices, buffer, bx - 0.0025, 0.00, -0.046, bx + 0.0025, 0.135, -0.022,
                hotYelR, hotYelG, hotYelB, 0.99f);
        box(matrices, buffer, bx - 0.0018, 0.090, -0.040, bx + 0.0018, 0.165, -0.028,
                1.0f, 0.98f, 0.84f, 0.99f);
        matrices.pop();

        matrices.pop();
        matrices.pop();
        matrices.pop();
    }

    private static void box(
            MatrixStack matrices,
            VertexConsumer buffer,
            double x1,
            double y1,
            double z1,
            double x2,
            double y2,
            double z2,
            float r,
            float g,
            float b,
            float a
    ) {
        WorldRenderer.renderFilledBox(
                matrices,
                buffer,
                Math.min(x1, x2),
                Math.min(y1, y2),
                Math.min(z1, z2),
                Math.max(x1, x2),
                Math.max(y1, y2),
                Math.max(z1, z2),
                r,
                g,
                b,
                a
        );
    }

    private static final class DeployAnimState {
        float prevProgress;
        float progress;

        DeployAnimState(float initialProgress) {
            this.prevProgress = initialProgress;
            this.progress = initialProgress;
        }
    }
}
