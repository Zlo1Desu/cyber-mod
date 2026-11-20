#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Intensity;
uniform float Spike;
uniform float GlitchSeed;

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

const float TWO_PI = 6.28318530718;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    float intensity = clamp(Intensity, 0.0, 1.0);
    float spike = clamp(Spike, 0.0, 1.0);

    vec3 orig = texture(DiffuseSampler, texCoord).rgb;
    if (intensity <= 0.001 && spike <= 0.001) {
        fragColor = vec4(orig, 1.0);
        return;
    }

    // 1. Real GPU Relic Malfunction Horizontal Pixel Tearing
    float bandY = floor(texCoord.y * 28.0);
    float timeStep = floor(Time * 14.0) + GlitchSeed * 17.0;

    float bandRand = hash12(vec2(bandY, timeStep));
    float bandDir = hash12(vec2(bandY + 19.37, timeStep + 7.13)) * 2.0 - 1.0;

    float tearPower = clamp(intensity * 0.62 + spike * 0.68, 0.0, 1.0);
    float tearThreshold = mix(0.93, 0.50, tearPower);
    float bandActive = step(tearThreshold, bandRand);
    float tearShift = bandActive * bandDir * (0.010 + 0.052 * spike) * (0.35 + 0.65 * intensity);

    // Secondary high-frequency razor scan-slice jitter during spikes
    float fineY = floor(texCoord.y * 76.0);
    float fineRand = hash12(vec2(fineY, timeStep * 2.0 + 3.71));
    float fineActive = step(mix(0.97, 0.78, spike), fineRand);
    float fineShift = fineActive * (hash12(vec2(fineY + 5.13, timeStep)) - 0.5) * 0.036 * intensity;

    vec2 tornUv = vec2(
        clamp(texCoord.x + tearShift + fineShift, 0.001, 0.999),
        clamp(texCoord.y, 0.001, 0.999)
    );

    // 2. Real GPU RGB Chromatic Split (Crimson R vs Cyan G/B across glitch bands)
    vec2 centered = texCoord - vec2(0.5);
    float distFromCenter = length(centered) * 1.41421356;

    float splitAmt = (0.0032 * intensity * (0.35 + 0.95 * distFromCenter))
                   + (0.0155 * spike * (0.35 + bandActive * 1.45))
                   + abs(tearShift) * 0.48;

    float r = texture(DiffuseSampler, clamp(tornUv + vec2(splitAmt, 0.0), vec2(0.001), vec2(0.999))).r;
    float g = texture(DiffuseSampler, clamp(tornUv - vec2(splitAmt * 0.42, 0.0), vec2(0.001), vec2(0.999))).g;
    float b = texture(DiffuseSampler, clamp(tornUv - vec2(splitAmt * 0.95, 0.0), vec2(0.001), vec2(0.999))).b;
    vec3 color = vec3(r, g, b);

    // Relic digital cyan/crimson glitch-band bleed during burst spikes
    vec3 relicTint = (bandDir > 0.0) ? vec3(1.00, 0.12, 0.24) : vec3(0.08, 0.94, 1.00);
    color = mix(color, color * 0.68 + relicTint * 0.42, bandActive * spike * 0.30);

    // 3. Neural Stress Color Contrast Grade
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    color = mix(color, vec3(luma) * vec3(1.18, 0.82, 0.86), 0.26 * intensity);

    // 4. Subtle CRT Scanlines
    float scanWave = sin(texCoord.y * max(InSize.y, 1.0) * 1.4 + Time * TWO_PI * 2.0);
    color *= 1.0 - (0.055 + 0.045 * spike) * intensity * (0.5 + 0.5 * scanWave);

    // 5. Optical Sensor Noise Grain
    vec2 grainCell = floor(texCoord * max(InSize, vec2(1.0)) * 0.5);
    float grain = hash12(grainCell + vec2(Time * 197.0, GlitchSeed * 41.0)) - 0.5;
    color += vec3(grain) * (0.070 * intensity + 0.090 * spike);

    // 6. Pulsing Crimson Vignette
    float heartbeat = 0.78 + 0.22 * sin(Time * TWO_PI * 2.0);
    float vig = smoothstep(0.36, 1.06, distFromCenter);
    vec3 crimsonEdge = vec3(0.58, 0.03, 0.09);
    color = mix(
        color,
        color * vec3(0.46, 0.18, 0.22) + crimsonEdge * (0.36 * heartbeat),
        vig * (0.56 * intensity + 0.24 * spike)
    );

    float finalBlend = clamp(intensity + spike * 0.45, 0.0, 1.0);
    vec3 outColor = mix(orig, color, finalBlend);

    fragColor = vec4(clamp(outColor, 0.0, 1.0), 1.0);
}
