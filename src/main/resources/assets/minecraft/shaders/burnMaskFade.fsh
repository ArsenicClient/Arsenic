#version 120
// Fades the post-processing blur/bloom masks in step with the ClickGUI
// open/close transition, so blur and bloom vanish exactly where (and when)
// the GUI has burnt / dissolved / glitched away. Drawn over the bound mask
// FBO with glBlendFunc(GL_ZERO, GL_SRC_ALPHA): dst *= keep.
//
// The coverage logic MUST mirror paperBurn.fsh per style - keep them in sync.

uniform float time;
uniform vec2 resolution;
uniform float progress;    // 0 = fully gone, 1 = fully present
uniform vec2 boxMin;       // main GUI box, top-down pixels
uniform vec2 boxMax;
uniform float boxRadius;
uniform int style;         // 0 = paper burn, 1 = dissolve, 2 = glitch, 3 = fade

float hash(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0; float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 5; i++){ v += a * noise(p); p = m * p; a *= 0.5; }
    return v;
}
float burnField(vec2 uv){
    float aspect = resolution.x / resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);
    float field = fbm(p * 3.0 + vec2(0.0, time * 0.05));
    field += 0.18 * fbm(p * 12.0 - time * 0.1);
    return clamp(field, 0.0, 1.0);
}

void main() {
    vec2 fragB = gl_FragCoord.xy;
    vec2 uv = fragB / resolution.xy;
    float keep;

    if (style == 2) {
        keep = smoothstep(0.0, 0.9, progress);          // glitch: global fade
    } else if (style == 3) {
        keep = smoothstep(0.0, 1.0, progress);          // plain fade
    } else if (style == 1) {
        // dissolve: same field/ramp as the composite
        float field = burnField(uv);
        float edge = 0.28;
        float f = progress * (1.0 + edge * 2.0) - edge;
        float d = clamp((f + edge - field) / edge, 0.0, 1.0);
        keep = d * d * (3.0 - 2.0 * d);
    } else {
        // paper burn: burnt-through inside the box, smooth fade outside
        vec2 fc = vec2(fragB.x, resolution.y - fragB.y);
        vec2 center = (boxMin + boxMax) * 0.5;
        vec2 halfS  = (boxMax - boxMin) * 0.5;
        vec2 q = abs(fc - center) - (halfS - vec2(boxRadius));
        float sdf = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - boxRadius;
        if (sdf <= 0.0) {
            float field = burnField(uv);
            float f = progress * 1.25 - 0.12;
            float edge = 0.11;
            keep = field >= f + edge ? 0.0 : clamp((f + edge - field) / edge, 0.0, 1.0);
        } else {
            keep = smoothstep(0.0, 0.85, progress);
        }
    }

    gl_FragColor = vec4(0.0, 0.0, 0.0, keep);
}
