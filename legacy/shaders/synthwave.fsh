#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec3 col;
    if (uv.y > 0.0) {
        float sun = smoothstep(0.35, 0.34, length(uv - vec2(0.0, 0.25)));
        float bands = step(0.0, sin((uv.y - 0.25) * 60.0 + 1.0));
        vec3 sky = mix(vec3(0.1, 0.02, 0.2), vec3(0.9, 0.2, 0.5), uv.y * 2.0);
        col = sky + sun * mix(vec3(1.0, 0.9, 0.2), vec3(1.0, 0.2, 0.6), (uv.y - 0.25) * 3.0) * bands;
    } else {
        float persp = 1.0 / (-uv.y + 0.001);
        float gx = abs(fract(uv.x * persp * 0.5) - 0.5);
        float gz = abs(fract((persp * 0.5) + time * 1.5) - 0.5);
        float line = smoothstep(0.05, 0.0, gx) + smoothstep(0.05, 0.0, gz);
        vec3 floorc = vec3(0.05, 0.0, 0.1) + vec3(0.9, 0.1, 0.8) * line;
        col = floorc;
    }
    gl_FragColor = vec4(col, 1.0);
}
