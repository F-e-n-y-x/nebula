package io.github.fenyx.nebula.engine.upscale

/**
 * OpenGL ES 3.0 shaders for the decoder-output upscaler.
 *
 * - EASU and RCAS are hand-ported to GLSL ES from AMD FidelityFX Super Resolution 1.0
 *   (framegen/src/main/cpp/shaders/amd_fsr/ffx_fsr1.h, the FP32 paths). Copyright (c) 2021
 *   Advanced Micro Devices, Inc. MIT licence (framegen/src/main/cpp/shaders/amd_fsr/LICENSE.txt).
 * - SGSR is Snapdragon Game Super Resolution v1 (sgsr1_shader_mobile.frag, RGBA mode) from
 *   https://github.com/SnapdragonStudios/snapdragon-gsr at d926f074. Copyright (c) 2025 Qualcomm
 *   Innovation Center, Inc. BSD-3-Clause (see engine/src/main/assets/licenses/snapdragon-gsr.txt).
 *
 * textureGather is GLES 3.1; these shaders emulate it with four texelFetch calls and the same
 * texel order (x = (i0,j1), y = (i1,j1), z = (i1,j0), w = (i0,j0)), so they run on ES 3.0.
 */
internal object UpscalerShaders {
    /** Full-screen triangle; vUv is 0..1 with (0,0) at the bottom left. */
    const val VERTEX = """#version 300 es
out highp vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

    /** Samples the decoder's external (OES) texture with the SurfaceTexture transform. */
    const val COPY_OES = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES uSrc;
uniform mat4 uTexMatrix;
in highp vec2 vUv;
out vec4 outColor;
void main() {
    vec2 uv = (uTexMatrix * vec4(vUv, 0.0, 1.0)).xy;
    outColor = vec4(texture(uSrc, uv).rgb, 1.0);
}
"""

    private const val COMMON = """
precision highp float;
precision highp int;
vec4 fetchC(sampler2D t, ivec2 p) {
    ivec2 sz = textureSize(t, 0);
    return texelFetch(t, clamp(p, ivec2(0), sz - ivec2(1)), 0);
}
// textureGather emulation: the 2x2 footprint a bilinear sample at uv p would use.
void gather4(sampler2D t, vec2 p, out vec4 x, out vec4 y, out vec4 z, out vec4 w) {
    vec2 q = p * vec2(textureSize(t, 0)) - 0.5;
    ivec2 i0 = ivec2(floor(q));
    ivec2 i1 = i0 + ivec2(1);
    x = fetchC(t, ivec2(i0.x, i1.y));
    y = fetchC(t, ivec2(i1.x, i1.y));
    z = fetchC(t, ivec2(i1.x, i0.y));
    w = fetchC(t, ivec2(i0.x, i0.y));
}
float aRcpLo(float a) { return uintBitsToFloat(uint(0x7ef07ebb) - floatBitsToUint(a)); }
float aRsqLo(float a) { return uintBitsToFloat(uint(0x5f347d74) - (floatBitsToUint(a) >> uint(1))); }
float aRcpMed(float a) { float b = uintBitsToFloat(uint(0x7ef19fff) - floatBitsToUint(a)); return b * (-b * a + 2.0); }
float sat(float a) { return clamp(a, 0.0, 1.0); }
float min3(float a, float b, float c) { return min(a, min(b, c)); }
float max3(float a, float b, float c) { return max(a, max(b, c)); }
vec3 min3v(vec3 a, vec3 b, vec3 c) { return min(a, min(b, c)); }
vec3 max3v(vec3 a, vec3 b, vec3 c) { return max(a, max(b, c)); }
"""

    /** Plain bilinear resample (the first pass of "sharpen only"). */
    const val BILINEAR_OES = COPY_OES

    /** AMD FSR 1 EASU (edge-adaptive spatial upsampling), FP32 path. */
    const val EASU = """#version 300 es
$COMMON
uniform sampler2D uSrc;
uniform vec4 uCon0;
uniform vec4 uCon1;
uniform vec4 uCon2;
uniform vec4 uCon3;
out vec4 outColor;

void tap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c) {
    vec2 v;
    v.x = (off.x * dir.x) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len;
    float d2 = min(v.x * v.x + v.y * v.y, clp);
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void setDir(inout vec2 dir, inout float len, vec2 pp, int corner, float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (corner == 0) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (corner == 1) w = pp.x * (1.0 - pp.y);
    if (corner == 2) w = (1.0 - pp.x) * pp.y;
    if (corner == 3) w = pp.x * pp.y;
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = aRcpLo(max(abs(dc), abs(cb)));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = sat(abs(dirX) * lenX);
    len += lenX * lenX * w;
    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = aRcpLo(max(abs(ec), abs(ca)));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = sat(abs(dirY) * lenY);
    len += lenY * lenY * w;
}

void main() {
    vec2 ip = floor(gl_FragCoord.xy);
    vec2 pp = ip * uCon0.xy + uCon0.zw;
    vec2 fp = floor(pp);
    pp -= fp;
    vec2 p0 = fp * uCon1.xy + uCon1.zw;
    vec2 p1 = p0 + uCon2.xy;
    vec2 p2 = p0 + uCon2.zw;
    vec2 p3 = p0 + uCon3.xy;
    vec4 b0, b1, b2, b3; gather4(uSrc, p0, b0, b1, b2, b3); // b c z z  (x y z w)
    vec4 i0, i1, i2, i3; gather4(uSrc, p1, i0, i1, i2, i3); // i j f e
    vec4 k0, k1, k2, k3; gather4(uSrc, p2, k0, k1, k2, k3); // k l h g
    vec4 z0, z1, z2, z3; gather4(uSrc, p3, z0, z1, z2, z3); // z z o n
    vec3 b = b0.rgb, c = b1.rgb;
    vec3 i = i0.rgb, j = i1.rgb, f = i2.rgb, e = i3.rgb;
    vec3 k = k0.rgb, l = k1.rgb, h = k2.rgb, g = k3.rgb;
    vec3 o = z2.rgb, n = z3.rgb;
    // Luma times 2.
    #define L(v) (v.b * 0.5 + (v.r * 0.5 + v.g))
    float bL = L(b), cL = L(c), iL = L(i), jL = L(j), fL = L(f), eL = L(e);
    float kL = L(k), lL = L(l), hL = L(h), gL = L(g), oL = L(o), nL = L(n);
    vec2 dir = vec2(0.0);
    float len = 0.0;
    setDir(dir, len, pp, 0, bL, eL, fL, gL, jL);
    setDir(dir, len, pp, 1, cL, fL, gL, hL, kL);
    setDir(dir, len, pp, 2, fL, iL, jL, kL, nL);
    setDir(dir, len, pp, 3, gL, jL, kL, lL, oL);
    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = aRsqLo(dirR);
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= vec2(dirR);
    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) * aRcpLo(max(abs(dir.x), abs(dir.y)));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = aRcpLo(lob);
    vec3 min4 = min(min3v(f, g, j), k);
    vec3 max4 = max(max3v(f, g, j), k);
    vec3 aC = vec3(0.0);
    float aW = 0.0;
    tap(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, b);
    tap(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, c);
    tap(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, i);
    tap(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, j);
    tap(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, f);
    tap(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, e);
    tap(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, k);
    tap(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, l);
    tap(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, h);
    tap(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, g);
    tap(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, o);
    tap(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, n);
    vec3 pix = min(max4, max(min4, aC * (1.0 / aW)));
    outColor = vec4(max(pix, vec3(0.0)), 1.0);
}
"""

    /** AMD FSR 1 RCAS (robust contrast-adaptive sharpening) at the output resolution. */
    const val RCAS = """#version 300 es
$COMMON
uniform sampler2D uSrc;
uniform float uSharpness;
out vec4 outColor;
#define FSR_RCAS_LIMIT (0.25 - (1.0 / 16.0))
void main() {
    ivec2 sp = ivec2(floor(gl_FragCoord.xy));
    vec3 b = fetchC(uSrc, sp + ivec2(0, -1)).rgb;
    vec3 d = fetchC(uSrc, sp + ivec2(-1, 0)).rgb;
    vec3 e = fetchC(uSrc, sp).rgb;
    vec3 f = fetchC(uSrc, sp + ivec2(1, 0)).rgb;
    vec3 h = fetchC(uSrc, sp + ivec2(0, 1)).rgb;
    vec3 mn4 = min(min3v(b, d, f), h);
    vec3 mx4 = max(max3v(b, d, f), h);
    vec2 peakC = vec2(1.0, -4.0);
    vec3 hitMin = min(mn4, e) / (4.0 * mx4);
    vec3 hitMax = (peakC.x - max(mx4, e)) / (4.0 * mn4 + peakC.y);
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-FSR_RCAS_LIMIT, min(max3(lobeRGB.r, lobeRGB.g, lobeRGB.b), 0.0)) * uSharpness;
    float rcpL = aRcpMed(4.0 * lobe + 1.0);
    vec3 pix = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
    outColor = vec4(clamp(pix, 0.0, 1.0), 1.0);
}
"""

    /** Snapdragon GSR 1, RGBA operation mode (OperationMode 1, gathers the green channel). */
    const val SGSR = """#version 300 es
$COMMON
uniform sampler2D uSrc;
uniform highp vec4 uViewportInfo;
uniform float uEdgeSharpness;
in highp vec2 vUv;
out vec4 outColor;

float fastLanczos2(float x) {
    float wA = x - 4.0;
    float wB = x * wA - wA;
    wA *= wA;
    return wB * wA;
}
vec2 weightY(float dx, float dy, float c, float stdv) {
    float x = ((dx * dx) + (dy * dy)) * 0.55 + clamp(abs(c) * stdv, 0.0, 1.0);
    float w = fastLanczos2(x);
    return vec2(w, w * c);
}
vec4 gatherG(vec2 p) {
    vec4 x, y, z, w;
    gather4(uSrc, p, x, y, z, w);
    return vec4(x.g, y.g, z.g, w.g);
}
void main() {
    const float edgeThreshold = 8.0 / 255.0;
    vec4 color;
    color.xyz = textureLod(uSrc, vUv, 0.0).xyz;
    highp vec2 imgCoord = (vUv * uViewportInfo.zw) + vec2(-0.5, 0.5);
    highp vec2 imgCoordPixel = floor(imgCoord);
    highp vec2 coord = imgCoordPixel * uViewportInfo.xy;
    vec2 pl = imgCoord - imgCoordPixel;
    vec4 left = gatherG(coord);
    float edgeVote = abs(left.z - left.y) + abs(color.g - left.y) + abs(color.g - left.z);
    if (edgeVote > edgeThreshold) {
        coord.x += uViewportInfo.x;
        vec4 right = gatherG(coord + vec2(uViewportInfo.x, 0.0));
        vec4 upDown;
        upDown.xy = gatherG(coord + vec2(0.0, -uViewportInfo.y)).wz;
        upDown.zw = gatherG(coord + vec2(0.0, uViewportInfo.y)).yx;
        float mean = (left.y + left.z + right.x + right.w) * 0.25;
        left -= vec4(mean);
        right -= vec4(mean);
        upDown -= vec4(mean);
        color.w = color.g - mean;
        float sum = abs(left.x) + abs(left.y) + abs(left.z) + abs(left.w)
                  + abs(right.x) + abs(right.y) + abs(right.z) + abs(right.w)
                  + abs(upDown.x) + abs(upDown.y) + abs(upDown.z) + abs(upDown.w);
        float stdv = 2.181818 / sum;
        vec2 aWY = weightY(pl.x, pl.y + 1.0, upDown.x, stdv);
        aWY += weightY(pl.x - 1.0, pl.y + 1.0, upDown.y, stdv);
        aWY += weightY(pl.x - 1.0, pl.y - 2.0, upDown.z, stdv);
        aWY += weightY(pl.x, pl.y - 2.0, upDown.w, stdv);
        aWY += weightY(pl.x + 1.0, pl.y - 1.0, left.x, stdv);
        aWY += weightY(pl.x, pl.y - 1.0, left.y, stdv);
        aWY += weightY(pl.x, pl.y, left.z, stdv);
        aWY += weightY(pl.x + 1.0, pl.y, left.w, stdv);
        aWY += weightY(pl.x - 1.0, pl.y - 1.0, right.x, stdv);
        aWY += weightY(pl.x - 2.0, pl.y - 1.0, right.y, stdv);
        aWY += weightY(pl.x - 2.0, pl.y, right.z, stdv);
        aWY += weightY(pl.x - 1.0, pl.y, right.w, stdv);
        float finalY = aWY.y / aWY.x;
        float maxY = max(max(left.y, left.z), max(right.x, right.w));
        float minY = min(min(left.y, left.z), min(right.x, right.w));
        finalY = clamp(uEdgeSharpness * finalY, minY, maxY);
        float deltaY = clamp(finalY - color.w, -23.0 / 255.0, 23.0 / 255.0);
        color.xyz = clamp(color.xyz + deltaY, 0.0, 1.0);
    }
    outColor = vec4(color.xyz, 1.0);
}
"""
}
