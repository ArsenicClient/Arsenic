#version 120
uniform float time;
uniform vec2 resolution;

float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec3 col = vec3(0.0);
    for (float l = 0.0; l < 4.0; l += 1.0) {
        float depth = fract(l * 0.31 + time * 0.15 * (l + 1.0));
        float scale = mix(30.0, 2.0, depth);
        vec2 g = uv * scale + l * 25.0;
        vec2 id = floor(g);
        vec2 f = fract(g) - 0.5;
        float star = hash(id);
        if (star > 0.94) {
            float d = length(f);
            float bright = smoothstep(0.5, 0.0, d) * depth;
            float tw = 0.6 + 0.4 * sin(time * 3.0 + star * 40.0);
            col += vec3(bright * tw) * vec3(0.8, 0.9, 1.2);
        }
    }
    gl_FragColor = vec4(col, 1.0);
}
