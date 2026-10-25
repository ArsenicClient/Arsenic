#version 120

uniform vec2 quadSize, rectSize;
uniform vec4 color1, color2, color3, color4;
uniform float radius;

#define NOISE .5/255.0

float sdRoundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

void main() {
    vec2 st = gl_TexCoord[0].st;
    vec2 p = (st - 0.5) * quadSize;
    vec2 hs = rectSize * 0.5;
    float r = clamp(radius, 0.0, min(hs.x, hs.y));

    float d = sdRoundBox(p, hs, r);
    float aa = max(fwidth(d), 0.75);
    float cov = 1.0 - smoothstep(-aa, aa, d);
    if (cov <= 0.001)
        discard;

    vec4 gradient = createGradient(st, color1, color2, color3, color4);
    gl_FragColor = vec4(gradient.rgb, gradient.a * cov);
}
