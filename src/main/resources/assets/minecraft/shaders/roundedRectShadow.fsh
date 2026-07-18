#version 120

// Soft elevation shadow for a rounded rect, drawn in a single pass.
// The quad is the element expanded by `spread` on every side; alpha falls off
// smoothly (gaussian-ish) with SDF distance from the element's edge, so the
// element reads as hovering above the layer behind it - no layer banding.

uniform vec2 quadSize;   // full quad incl. spread margin (real pixels)
uniform vec2 rectSize;   // the element casting the shadow (real pixels)
uniform float radius;    // element corner radius (same convention as roundedRect.fsh)
uniform float spread;    // feather reach beyond the element edge (real pixels)
uniform vec4 color;      // shadow colour; a = core opacity

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b, 0.0)) - r;
}

void main() {
    vec2 p = (gl_TexCoord[0].st - 0.5) * quadSize;
    float sdf = roundSDF(p, rectSize * 0.5 - radius, radius);
    float t = clamp(sdf / spread, 0.0, 1.0);          // 0 at edge -> 1 at reach
    float a = color.a * exp(-t * t * 4.0) * (1.0 - t); // smooth, dies to exactly 0
    gl_FragColor = vec4(color.rgb, a);
}
