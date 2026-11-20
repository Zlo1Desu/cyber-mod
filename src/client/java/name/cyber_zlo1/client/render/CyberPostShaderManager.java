package name.cyber_zlo1.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import name.cyber_zlo1.Cyber;
import name.cyber_zlo1.client.CyberClient;
import name.cyber_zlo1.cyberware.CyberwareState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public final class CyberPostShaderManager {
    private static final Identifier SANDEVISTAN_POST_ID = Cyber.id("shaders/post/sandevistan.json");
    private static final Identifier CYBERPSYCHOSIS_POST_ID = Cyber.id("shaders/post/cyberpsychosis.json");
    private static final Random RANDOM = new Random();

    private static PostEffectProcessor sandevistanProcessor;
    private static PostEffectProcessor cyberpsychosisProcessor;
    private static boolean sandevistanFailed = false;
    private static boolean cyberpsychosisFailed = false;

    private static int lastFbWidth = -1;
    private static int lastFbHeight = -1;
    private static long lastFrameNanos = 0L;

    private static float sandevistanIntensity = 0.0f;
    private static float shockwaveProgress = 0.0f;
    private static boolean wasSandevistanActive = false;

    private static float cyberpsychosisIntensity = 0.0f;
    private static float cyberpsychosisSpike = 0.0f;
    private static float nextSpikeTimer = 0.65f;
    private static float glitchSeedTimer = 0.0f;
    private static float glitchSeed = 1.0f;

    private static boolean sandevistanActiveThisFrame = false;
    private static boolean cyberpsychosisActiveThisFrame = false;

    private CyberPostShaderManager() {
    }

    public static boolean isShaderActive() {
        return sandevistanActiveThisFrame && sandevistanProcessor != null && !sandevistanFailed;
    }

    public static boolean isCyberpsychosisShaderActive() {
        return cyberpsychosisActiveThisFrame && cyberpsychosisProcessor != null && !cyberpsychosisFailed;
    }

    public static void renderPostEffects(RenderTickCounter tickCounter) {
        sandevistanActiveThisFrame = false;
        cyberpsychosisActiveThisFrame = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.player == null) {
            resetRuntimeState();
            return;
        }

        long now = System.nanoTime();
        float dt;
        if (lastFrameNanos == 0L) {
            dt = 0.016f;
        } else {
            dt = (now - lastFrameNanos) / 1_000_000_000.0f;
            dt = MathHelper.clamp(dt, 0.001f, 0.1f);
        }
        lastFrameNanos = now;

        boolean paused = client.isPaused();
        float simDt = paused ? 0.0f : dt;
        float frameTicks = paused ? 0.0f : MathHelper.clamp(tickCounter.getLastFrameDuration(), 0.0f, 4.0f);

        // 1. Update Sandevistan intensity & activation shockwave ring
        boolean sandyNow = CyberwareState.isActive();
        if (sandyNow && !wasSandevistanActive) {
            shockwaveProgress = 0.001f;
        }
        wasSandevistanActive = sandyNow;

        if (shockwaveProgress > 0.0f && !paused) {
            shockwaveProgress += simDt * 1.45f;
            if (shockwaveProgress >= 1.0f) {
                shockwaveProgress = 0.0f;
            }
        }

        float sandyTarget = sandyNow ? 1.0f : 0.0f;
        if (!paused) {
            float sandyRate = sandyNow ? 9.5f : 5.5f;
            sandevistanIntensity += (sandyTarget - sandevistanIntensity) * (1.0f - (float) Math.exp(-simDt * sandyRate));
            if (sandyTarget == 0.0f && sandevistanIntensity < 0.003f) {
                sandevistanIntensity = 0.0f;
            }
        }

        // 2. Update Cyberpsychosis / Relic Malfunction intensity & burst spikes
        int psychosis = CyberwareState.getCyberpsychosis();
        boolean rampage = CyberwareState.isCyberpsychoRampage();
        boolean cutscene = CyberClient.isInCyberpsychosisCutscene();

        float psychoTarget;
        if (rampage || cutscene) {
            psychoTarget = 1.0f;
        } else if (psychosis >= 450) {
            psychoTarget = MathHelper.clamp((psychosis - 450.0f) / 550.0f, 0.12f, 0.92f);
        } else {
            psychoTarget = 0.0f;
        }

        if (!paused) {
            float psychoRate = (psychoTarget > cyberpsychosisIntensity) ? 6.5f : 4.0f;
            cyberpsychosisIntensity += (psychoTarget - cyberpsychosisIntensity) * (1.0f - (float) Math.exp(-simDt * psychoRate));
            if (psychoTarget == 0.0f && cyberpsychosisIntensity < 0.003f) {
                cyberpsychosisIntensity = 0.0f;
            }

            if (cyberpsychosisIntensity > 0.01f) {
                glitchSeedTimer += simDt;
                if (glitchSeedTimer >= 1.0f) {
                    glitchSeedTimer -= 1.0f;
                    glitchSeed = (glitchSeed + 1.0f) % 997.0f;
                }

                cyberpsychosisSpike *= (float) Math.exp(-simDt * 7.5f);
                nextSpikeTimer -= simDt;
                if (nextSpikeTimer <= 0.0f) {
                    if (cutscene) {
                        cyberpsychosisSpike = 0.78f + RANDOM.nextFloat() * 0.22f;
                        nextSpikeTimer = 0.28f + RANDOM.nextFloat() * 0.34f;
                    } else if (rampage) {
                        cyberpsychosisSpike = 0.65f + RANDOM.nextFloat() * 0.35f;
                        nextSpikeTimer = 0.42f + RANDOM.nextFloat() * 0.68f;
                    } else {
                        cyberpsychosisSpike = (0.35f + 0.55f * cyberpsychosisIntensity) * (0.7f + RANDOM.nextFloat() * 0.3f);
                        nextSpikeTimer = MathHelper.lerp(cyberpsychosisIntensity, 3.2f, 0.85f) + RANDOM.nextFloat() * 1.1f;
                    }
                }
            } else {
                cyberpsychosisSpike = 0.0f;
                nextSpikeTimer = 0.45f;
            }
        }

        boolean runSandy = (sandevistanIntensity > 0.004f || shockwaveProgress > 0.0f) && !sandevistanFailed;
        boolean runPsycho = (cyberpsychosisIntensity > 0.004f) && !cyberpsychosisFailed;
        if (!runSandy && !runPsycho) {
            return;
        }

        Framebuffer fb = client.getFramebuffer();
        if (fb == null || fb.textureWidth <= 0 || fb.textureHeight <= 0) {
            return;
        }

        boolean resized = (fb.textureWidth != lastFbWidth || fb.textureHeight != lastFbHeight);
        if (resized) {
            lastFbWidth = fb.textureWidth;
            lastFbHeight = fb.textureHeight;
            if (sandevistanProcessor != null) {
                sandevistanProcessor.setupDimensions(lastFbWidth, lastFbHeight);
            }
            if (cyberpsychosisProcessor != null) {
                cyberpsychosisProcessor.setupDimensions(lastFbWidth, lastFbHeight);
            }
        }

        if (runSandy && sandevistanProcessor == null) {
            sandevistanProcessor = tryLoadProcessor(client, fb, SANDEVISTAN_POST_ID, "Sandevistan");
            if (sandevistanProcessor == null) {
                sandevistanFailed = true;
                runSandy = false;
            }
        }

        if (runPsycho && cyberpsychosisProcessor == null) {
            cyberpsychosisProcessor = tryLoadProcessor(client, fb, CYBERPSYCHOSIS_POST_ID, "Cyberpsychosis");
            if (cyberpsychosisProcessor == null) {
                cyberpsychosisFailed = true;
                runPsycho = false;
            }
        }

        if (!runSandy && !runPsycho) {
            return;
        }

        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();

        if (runSandy && sandevistanProcessor != null) {
            try {
                sandevistanProcessor.setUniforms("Intensity", sandevistanIntensity);
                sandevistanProcessor.setUniforms("Shockwave", shockwaveProgress);
                sandevistanProcessor.render(frameTicks);
                sandevistanActiveThisFrame = true;
            } catch (Throwable t) {
                Cyber.LOGGER.warn("Disabling Sandevistan post-processing shader after runtime error", t);
                closeProcessor(sandevistanProcessor);
                sandevistanProcessor = null;
                sandevistanFailed = true;
            }
        }

        if (runPsycho && cyberpsychosisProcessor != null) {
            try {
                cyberpsychosisProcessor.setUniforms("Intensity", cyberpsychosisIntensity);
                cyberpsychosisProcessor.setUniforms("Spike", MathHelper.clamp(cyberpsychosisSpike, 0.0f, 1.0f));
                cyberpsychosisProcessor.setUniforms("GlitchSeed", glitchSeed);
                cyberpsychosisProcessor.render(frameTicks);
                cyberpsychosisActiveThisFrame = true;
            } catch (Throwable t) {
                Cyber.LOGGER.warn("Disabling Cyberpsychosis post-processing shader after runtime error", t);
                closeProcessor(cyberpsychosisProcessor);
                cyberpsychosisProcessor = null;
                cyberpsychosisFailed = true;
            }
        }

        fb.beginWrite(false);
        RenderSystem.enableDepthTest();
    }

    public static void onResized(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        lastFbWidth = width;
        lastFbHeight = height;
        if (sandevistanProcessor != null) {
            try {
                sandevistanProcessor.setupDimensions(width, height);
            } catch (Throwable ignored) {
            }
        }
        if (cyberpsychosisProcessor != null) {
            try {
                cyberpsychosisProcessor.setupDimensions(width, height);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void invalidate() {
        close();
        sandevistanFailed = false;
        cyberpsychosisFailed = false;
    }

    public static void close() {
        closeProcessor(sandevistanProcessor);
        closeProcessor(cyberpsychosisProcessor);
        sandevistanProcessor = null;
        cyberpsychosisProcessor = null;
        lastFbWidth = -1;
        lastFbHeight = -1;
        sandevistanActiveThisFrame = false;
        cyberpsychosisActiveThisFrame = false;
    }

    private static PostEffectProcessor tryLoadProcessor(MinecraftClient client, Framebuffer fb, Identifier id, String label) {
        try {
            PostEffectProcessor processor = new PostEffectProcessor(
                    client.getTextureManager(),
                    client.getResourceManager(),
                    fb,
                    id
            );
            processor.setupDimensions(fb.textureWidth, fb.textureHeight);
            return processor;
        } catch (Throwable t) {
            Cyber.LOGGER.warn("Failed to load {} post-processing shader ({})", label, id, t);
            return null;
        }
    }

    private static void closeProcessor(PostEffectProcessor processor) {
        if (processor != null) {
            try {
                processor.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void resetRuntimeState() {
        lastFrameNanos = 0L;
        sandevistanIntensity = 0.0f;
        shockwaveProgress = 0.0f;
        wasSandevistanActive = false;
        cyberpsychosisIntensity = 0.0f;
        cyberpsychosisSpike = 0.0f;
        nextSpikeTimer = 0.65f;
    }
}
