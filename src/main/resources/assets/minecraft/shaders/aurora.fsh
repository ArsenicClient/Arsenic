#version 120
uniform float time;
uniform vec2 resolution;

float hash(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec3 col = vec3(0.02, 0.03, 0.08);
    for (float i = 0.0; i < 5.0; i += 1.0) {
        float y = 0.5 + 0.18 * sin(uv.x * 3.0 + time * (0.3 + i * 0.2) + i);
        y += 0.08 * noise(vec2(uv.x * 4.0 + time * 0.4, i));
        float d = abs(uv.y - y);
        float glow = 0.02 / (d + 0.02);
        vec3 band = vec3(0.1 + 0.3 * sin(i + time * 0.5), 0.8, 0.5 + 0.4 * cos(i));
        col += band * glow * 0.12;
    }
    gl_FragColor = vec4(col, 1.0);
}
