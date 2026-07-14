#version 120
uniform float time;
uniform vec2 resolution;

vec2 hash2(vec2 p){
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.y;
    uv *= 6.0;
    vec2 g = floor(uv);
    vec2 f = fract(uv);
    float md = 8.0;
    vec2 mp;
    for (int j = -1; j <= 1; j++)
    for (int i = -1; i <= 1; i++) {
        vec2 o = vec2(float(i), float(j));
        vec2 pt = hash2(g + o);
        pt = 0.5 + 0.5 * sin(time * 0.9 + 6.2831 * pt);
        vec2 r = o + pt - f;
        float d = dot(r, r);
        if (d < md) { md = d; mp = pt; }
    }
    vec3 col = 0.5 + 0.5 * sin(vec3(0.0, 2.0, 4.0) + mp.x * 6.0 + time);
    col *= 0.6 + 0.4 * exp(-8.0 * md);
    col += 0.05 / (md + 0.05);
    gl_FragColor = vec4(col, 1.0);
}
