#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    for (float i = 1.0; i < 8.0; i += 1.0) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + time * 0.7 + i);
        uv.y += 0.6 / i * cos(i * 1.5 * uv.x + time * 0.6 + i);
    }
    float v = 0.5 + 0.5 * sin(time - uv.x - uv.y);
    vec3 col = vec3(v);
    col *= vec3(0.6, 0.7, 0.9);
    col += 0.15 * vec3(sin(uv.x * 3.0), sin(uv.y * 3.0), sin((uv.x + uv.y) * 3.0));
    gl_FragColor = vec4(col, 1.0);
}
