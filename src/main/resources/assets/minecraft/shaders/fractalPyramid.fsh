#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec3 col = vec3(0.0);
    vec2 p = uv;
    float scale = 1.0;
    for (int i = 0; i < 8; i++) {
        p = abs(p) / dot(p, p) - 0.9;
        float rot = time * 0.2 + float(i) * 0.3;
        float c = cos(rot), s = sin(rot);
        p *= mat2(c, -s, s, c);
        scale *= 0.85;
        col += 0.5 + 0.5 * cos(vec3(0.0, 1.5, 3.0) + length(p) * 2.0 + time + float(i));
    }
    col /= 8.0;
    col = pow(col, vec3(1.5));
    gl_FragColor = vec4(col, 1.0);
}
