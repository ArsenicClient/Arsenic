#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv * 6.0;
    float c = 0.0;
    float amp = 1.0;
    for (int i = 0; i < 5; i++) {
        p += time * 0.15 * (mod(float(i), 2.0) == 0.0 ? 1.0 : -1.0);
        c += amp * abs(sin(p.x + sin(p.y + time)) * cos(p.y - cos(p.x - time)));
        p *= 1.6;
        amp *= 0.6;
    }
    c = pow(c * 0.4, 2.0);
    vec3 deep = vec3(0.0, 0.15, 0.35);
    vec3 bright = vec3(0.3, 0.8, 0.9);
    vec3 col = mix(deep, bright, clamp(c, 0.0, 1.0));
    gl_FragColor = vec4(col, 1.0);
}
