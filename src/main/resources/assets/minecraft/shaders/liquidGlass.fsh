#version 120

// Liquid Glass surface treatment for rounded rects.
// Drawn as a translucent overlay ON TOP of a panel's base fill: adds a faint
// coloured film, a vertical gloss gradient, a slow moving specular sweep (the
// "liquid" shimmer), a bright Fresnel edge rim, and a soft top sheen so the
// panel reads like a pane of frosted glass floating over the backdrop.

uniform vec2 location, rectSize;
uniform vec4 color;      // base glass film: rgb tint, a = overall film opacity
uniform vec4 highlight;  // rgb = rim/spec colour, a = highlight strength (0..1)
uniform float radius;
uniform float time;

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 uv = gl_TexCoord[0].st;          // 0..1 across the quad
    vec2 halfSize = rectSize * 0.5;
    float d = roundSDF(halfSize - (uv * rectSize), halfSize - radius - 1.0, radius);
    float coverage = 1.0 - smoothstep(0.0, 1.5, d);   // ~1 inside, AA at edge
    if (coverage <= 0.001) {
        gl_FragColor = vec4(0.0);
        return;
    }

    float hs = highlight.a;
    vec3 col = color.rgb;
    float alpha = color.a;

    // vertical gloss - brighter toward the top edge
    float vgrad = 1.0 - uv.y;
    col += highlight.rgb * (vgrad * vgrad) * 0.15 * hs;
    alpha += vgrad * vgrad * 0.04 * hs;

    // slow diagonal specular sweep across the surface
    float sweep = sin((uv.x + uv.y) * 3.14159 + time * 0.7);
    float spec = smoothstep(0.72, 1.0, sweep);
    col += highlight.rgb * spec * 0.12 * hs;
    alpha += spec * 0.06 * hs;

    // Fresnel edge rim - a bright thin line hugging the rounded border
    float rim = smoothstep(3.0, 0.0, abs(d));
    col += highlight.rgb * rim * 0.60 * hs;
    alpha += rim * 0.40 * hs;

    // top sheen band - a soft reflection near the upper edge, centred
    float sheen = smoothstep(0.16, 0.0, uv.y) * (1.0 - smoothstep(0.4, 1.0, abs(uv.x - 0.5) * 2.0));
    col += highlight.rgb * sheen * 0.22 * hs;
    alpha += sheen * 0.10 * hs;

    alpha = clamp(alpha, 0.0, 1.0) * coverage;
    gl_FragColor = vec4(col, alpha);
}
