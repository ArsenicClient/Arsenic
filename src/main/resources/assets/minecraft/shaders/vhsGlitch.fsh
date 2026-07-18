#version 120
uniform float time;
uniform vec2 resolution;
uniform vec3 themeColor;

float hash(vec2 p){ return fract(sin(dot(p, vec2(12.9, 78.2))) * 43758.5); }

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    float line = floor(uv.y * 120.0);
    float jitter = (hash(vec2(line, floor(time * 15.0))) - 0.5) * 0.06;
    jitter *= step(0.85, hash(vec2(floor(time * 8.0), line)));
    uv.x += jitter;
    float r = 0.5 + 0.5 * sin(uv.x * 10.0 + time);
    float g = 0.5 + 0.5 * sin(uv.x * 10.0 + time + 2.0);
    float b = 0.5 + 0.5 * sin(uv.x * 10.0 + time + 4.0);
    vec3 col = vec3(r, g, b) * 0.6;
    float scan = 0.85 + 0.15 * sin(uv.y * resolution.y * 1.5);
    col *= scan;
    col += (hash(uv + time) - 0.5) * 0.15;

    // recolour the whole effect to the GUI's theme colour: keep the animated
    // brightness/scanline pattern but drive its hue from themeColor. Falls back
    // to the original look if no theme colour was supplied.
    vec3 tc = themeColor;
    if (max(tc.r, max(tc.g, tc.b)) > 0.001) {
        float lum = dot(col, vec3(0.333));
        col = tc * (0.35 + 1.15 * lum);
    }

    gl_FragColor = vec4(col, 1.0);
}
