#version 120
uniform sampler2D gui;     // the captured ClickGUI (whole screen), premultiplied alpha
uniform float time;
uniform vec2 resolution;
uniform float progress;    // 0 = fully burnt/gone, 1 = fully present
uniform vec3 themeColor;    // ember / burn colour
uniform vec2 boxMin;        // main GUI box, top-down pixels
uniform vec2 boxMax;
uniform float boxRadius;

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

// Output is composited with premultiplied-alpha blending: glBlendFunc(GL_ONE,
// GL_ONE_MINUS_SRC_ALPHA). The captured GUI is already premultiplied (it was
// drawn over a transparent buffer), so intact pixels reproduce the GUI exactly
// - fully opaque, no wash.
void main() {
    vec2 fragB = gl_FragCoord.xy;                       // bottom-up (matches FBO)
    vec2 uv = fragB / resolution.xy;
    vec4 g = texture2D(gui, uv);                        // premultiplied GUI pixel

    vec2 fc = vec2(fragB.x, resolution.y - fragB.y);    // top-down pixels for the mask

    vec2 center = (boxMin + boxMax) * 0.5;
    vec2 halfS  = (boxMax - boxMin) * 0.5;
    vec2 q = abs(fc - center) - (halfS - vec2(boxRadius));
    float sdf = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - boxRadius;

    if (sdf <= 0.0) {
        // inside the box: dissolve to full transparency with a theme-coloured edge
        float aspect = resolution.x / resolution.y;
        vec2 p = vec2(uv.x * aspect, uv.y);
        float field = fbm(p * 3.0 + vec2(0.0, time * 0.05));
        field += 0.18 * fbm(p * 12.0 - time * 0.1);
        field = clamp(field, 0.0, 1.0);

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
