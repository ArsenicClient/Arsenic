#version 120

uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv * 2.0 - 1.0;
    p.x *= resolution.x / resolution.y;

    float t = time * 0.5;

    float wave = sin(p.x * 5.0 + t) * 0.3 + sin(p.x * 3.0 - t * 0.7) * 0.2;
    float dist = abs(p.y - wave);

    float intensity = 1.0 - smoothstep(0.0, 0.4, dist);
    intensity *= 1.0 - abs(p.y) * 0.8;

    vec3 col1 = vec3(0.129, 0.588, 1.0);
    vec3 col2 = vec3(0.667, 0.282, 1.0);
    vec3 col = mix(col1, col2, sin(p.x * 2.0 + t) * 0.5 + 0.5);

    col *= intensity * 1.5;
    col += vec3(0.02);

    gl_FragColor = vec4(col, 1.0);
}
