#version 120
uniform float time;
uniform vec2 resolution;

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv * 8.0 - 4.0;
    float t = time * 1.2;
    float v = sin(p.x + t);
    v += sin(p.y * 0.5 + t);
    v += sin((p.x + p.y + t) * 0.5);
    float cx = p.x + 0.5 * sin(t * 0.33);
    float cy = p.y + 0.5 * cos(t * 0.5);
    v += sin(sqrt(cx * cx + cy * cy + 1.0) + t);
    v *= 0.5;
    vec3 col = vec3(sin(v * 3.14159), sin(v * 3.14159 + 2.0), sin(v * 3.14159 + 4.0));
    col = 0.5 + 0.5 * col;
    gl_FragColor = vec4(col, 1.0);
}
