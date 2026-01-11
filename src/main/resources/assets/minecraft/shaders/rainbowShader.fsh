#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    gl_FragColor = vec4(0.5 + 0.5 * cos(time + uv.xyx + vec3(0, 2, 4)), 1.0);
}
