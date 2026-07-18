#version 120
uniform sampler2D gui;     // the captured ClickGUI (whole screen), premultiplied alpha
uniform float time;
uniform vec2 resolution;
uniform float progress;    // 0 = fully gone, 1 = fully present
uniform vec3 themeColor;    // ember / burn colour
uniform vec2 boxMin;        // main GUI box, top-down pixels
uniform vec2 boxMax;
uniform float boxRadius;
uniform int style;          // 0 = paper burn, 1 = dissolve, 2 = glitch, 3 = fade

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

// Shared with burnMaskFade.fsh - keep the two in sync so the post-processing
// blur/bloom masks vanish in exactly the same pattern as the GUI itself.
float burnField(vec2 uv){
    float aspect = resolution.x / resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);
    float field = fbm(p * 3.0 + vec2(0.0, time * 0.05));
    field += 0.18 * fbm(p * 12.0 - time * 0.1);
    return clamp(field, 0.0, 1.0);
}

// Output is composited with premultiplied-alpha blending: glBlendFunc(GL_ONE,
// GL_ONE_MINUS_SRC_ALPHA). The captured GUI is premultiplied and (via
// RenderUtils.captureCoverage) its alpha is true coverage, so scaling the
// whole vec4 is a correct premultiplied fade.
void main() {
    vec2 fragB = gl_FragCoord.xy;                       // bottom-up (matches FBO)
    vec2 uv = fragB / resolution.xy;

    if (style == 2) {
        // glitch out: horizontal strips tear sideways with RGB split and drop
        // out at random; plays in reverse on open (GUI assembles from static)
        float amt = 1.0 - progress;
        float seed = floor(time * 20.0);
        float strip = floor(fragB.y / max(4.0, resolution.y * 0.012));
        float j = hash(vec2(strip, seed)) - 0.5;
        float disp = j * amt * amt * 0.4;
        vec4 gr = texture2D(gui, uv + vec2(disp, 0.0));
        vec4 gg = texture2D(gui, uv + vec2(disp * 0.35, 0.0));
        vec4 gb = texture2D(gui, uv - vec2(disp * 0.6, 0.0));
        float drop = step(amt * 0.7, hash(vec2(strip, seed + 31.0)));
        float fade = smoothstep(0.0, 0.9, progress);
        gl_FragColor = vec4(gr.r, gg.g, gb.b, (gr.a + gg.a + gb.a) / 3.0) * (fade * drop);
        return;
    }

    vec4 g = texture2D(gui, uv);                        // premultiplied GUI pixel

    if (style == 3) {
        // plain fade
        gl_FragColor = g * smoothstep(0.0, 1.0, progress);
        return;
    }

    if (style == 1) {
        // noise dissolve: the whole GUI (box, backdrop, search) melts to
        // transparency along the fbm field - like the burn, minus the ember
        float field = burnField(uv);
        float edge = 0.28;
        float f = progress * (1.0 + edge * 2.0) - edge;
        float d = clamp((f + edge - field) / edge, 0.0, 1.0);
        gl_FragColor = g * (d * d * (3.0 - 2.0 * d));   // smoothstepped ramp
        return;
    }

    // ---- style 0: paper burn ----
    vec2 fc = vec2(fragB.x, resolution.y - fragB.y);    // top-down pixels for the mask

    vec2 center = (boxMin + boxMax) * 0.5;
    vec2 halfS  = (boxMax - boxMin) * 0.5;
    vec2 q = abs(fc - center) - (halfS - vec2(boxRadius));
    float sdf = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - boxRadius;

    if (sdf <= 0.0) {
        // inside the box: dissolve to full transparency with a theme-coloured edge
        float field = burnField(uv);

        float f = progress * 1.25 - 0.12;
        float edge = 0.11;

        if (field >= f + edge)
            discard;                                    // burnt away -> transparent (world)

        float d = clamp((f + edge - field) / edge, 0.0, 1.0);  // 1 intact .. 0 at burning edge
        float emberEdge = 1.0 - smoothstep(0.0, 0.6, d);       // glow only near the edge
        float flick = 1.25 + 0.5 * sin(time * 18.0 + field * 60.0);
        vec3 ember = themeColor * flick;

        // the capture uses coverage-correct alpha blending (see
        // RenderUtils.captureCoverage), so g.a IS the true coverage and the
        // intact box matches the finished GUI exactly - no opacity pop
        float cov = clamp(g.a, 0.0, 1.0);

        // premultiplied: intact GUI where d=1, opaque ember where burning
        vec3 rgb = g.rgb * d + ember * emberEdge;
        float a  = cov * d + emberEdge;
        gl_FragColor = vec4(rgb, clamp(a, 0.0, 1.0));
    } else {
        // outside the box (background shader, search bar): fade to transparent
        float fade = smoothstep(0.0, 0.85, progress);
        gl_FragColor = vec4(g.rgb * fade, g.a * fade);
    }
}
