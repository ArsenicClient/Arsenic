#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    float a = atan(uv.y, uv.x);
    float r = length(uv);
    float u = a / 3.14159;
    float v = 0.3 / r + time * 0.8;
    float rings = 0.5 + 0.5 * sin(v * 20.0);
    float spokes = 0.5 + 0.5 * sin(u * 30.0);
    vec3 col = vec3(0.5 + 0.5 * sin(v * 3.0 + vec3(0.0, 2.0, 4.0)));
    col *= mix(0.4, 1.0, rings * spokes);
    col *= smoothstep(0.0, 0.4, r);
    gl_FragColor = vec4(col, 1.0);
}
