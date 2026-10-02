#version 120
uniform float time;
uniform vec2 resolution;

float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    float cols = 60.0;
    float x = floor(uv.x * cols);
    float speed = 0.5 + hash(vec2(x, 1.0)) * 1.5;
    float y = fract(uv.y + time * speed * 0.15 + hash(vec2(x, 3.0)));
    float ch = hash(vec2(x, floor((uv.y + time * speed * 0.15) * 40.0)));
    float trail = pow(1.0 - y, 3.0);
    float glyph = step(0.5, ch);
    vec3 col = vec3(0.1, 1.0, 0.35) * trail * glyph;
    col += vec3(0.6, 1.0, 0.7) * pow(trail, 8.0) * glyph;
    gl_FragColor = vec4(col, 1.0);
}
