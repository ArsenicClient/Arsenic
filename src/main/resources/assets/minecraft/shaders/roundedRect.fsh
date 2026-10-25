#version 120

uniform vec2 quadSize, rectSize;
uniform vec4 color;
uniform float radius;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 p = (gl_TexCoord[0].st - 0.5) * quadSize;
    vec2 hs = rectSize * 0.5;
    float r = clamp(radius, 0.0, min(hs.x, hs.y));

    float d = sdRoundBox(p, hs, r);
    float aa = max(fwidth(d), 0.75);
    float cov = 1.0 - smoothstep(-aa, aa, d);
    if (cov <= 0.001)
        discard;

    gl_FragColor = vec4(color.rgb, color.a * cov);
}
