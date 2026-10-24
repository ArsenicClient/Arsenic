#version 120

uniform vec2 quadSize, rectSize;
uniform vec4 color;
uniform vec4 highlight;
uniform float radius;
uniform float time;

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 p = (uv - 0.5) * quadSize;
    vec2 hs = rectSize * 0.5;
    float r = clamp(radius, 0.0, min(hs.x, hs.y));

    float d = sdRoundBox(p, hs, r);
    float aa = max(fwidth(d), 0.75);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage <= 0.001) {
        discard;
    }

    float hs2 = highlight.a;
    vec3 col = color.rgb;
    float alpha = color.a;

    float vgrad = 1.0 - uv.y;
    col += highlight.rgb * (vgrad * vgrad) * 0.12 * hs2;
    alpha += vgrad * vgrad * 0.03 * hs2;

    float sweep = sin((uv.x + uv.y) * 3.14159 + time * 0.7);
    float spec = smoothstep(0.75, 1.0, sweep);
    col += highlight.rgb * spec * 0.09 * hs2;
    alpha += spec * 0.04 * hs2;

    float rim = smoothstep(2.5, 0.0, abs(d));
    col += highlight.rgb * rim * 0.45 * hs2;
    alpha += rim * 0.30 * hs2;

    float sheen = smoothstep(0.14, 0.0, uv.y) * (1.0 - smoothstep(0.4, 1.0, abs(uv.x - 0.5) * 2.0));
    col += highlight.rgb * sheen * 0.16 * hs2;
    alpha += sheen * 0.07 * hs2;

    alpha = clamp(alpha, 0.0, 1.0) * coverage;
    gl_FragColor = vec4(col, alpha);
}
