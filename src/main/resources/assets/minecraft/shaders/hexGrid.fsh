#version 120
uniform float time;
uniform vec2 resolution;

vec4 hexDist(vec2 p) {
    p = abs(p);
    float c = dot(p, normalize(vec2(1.0, 1.73)));
    c = max(c, p.x);
    return vec4(c, p);
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    uv *= 8.0;
    vec2 r = vec2(1.0, 1.73);
    vec2 h = r * 0.5;
    vec2 a = mod(uv, r) - h;
    vec2 b = mod(uv - h, r) - h;
    vec2 gv = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = uv - gv;
    float d = hexDist(gv).x;
    float pulse = sin(length(id) * 0.5 - time * 2.0);
    float edge = smoothstep(0.5, 0.48, d);
    vec3 col = vec3(0.5 + 0.5 * pulse) * vec3(0.2, 0.6, 1.0);
    col *= edge;
    col += (1.0 - edge) * vec3(0.02, 0.05, 0.1);
    gl_FragColor = vec4(col, 1.0);
}
