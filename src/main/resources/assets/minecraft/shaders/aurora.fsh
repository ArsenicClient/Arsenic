#version 120

uniform float time;
uniform vec2 resolution;

vec3 palette(float t) {
    vec3 a = vec3(0.5, 0.5, 0.5);
    vec3 b = vec3(0.5, 0.5, 0.5);
    vec3 c = vec3(1.0, 1.0, 1.0);
    vec3 d = vec3(0.263, 0.416, 0.557);
    return a + b * cos(6.28318 * (c * t + d));
}

void main() {
    vec2 uv = (2.0 * gl_FragCoord.xy - resolution.xy) / resolution.y;
    float t = time * 0.1;

    vec2 uv0 = uv;
    vec3 finalCol = vec3(0.0);

    for (float i = 0.0; i < 4.0; i++) {
        uv = uv0;
        uv.x += sin(t + i) * 0.5;
        uv.y += cos(t * 0.8 + i * 1.2) * 0.3;

        float d = sin(length(uv) + t * 0.5 + i * 1.5) * 0.5 + 0.5;
        d = sin(d * 8.0 + t * 2.0) * 0.5 + 0.5;
        d = pow(d, 3.0);

        vec3 col = palette(i * 0.4 + t * 0.3 + d * 0.5);
        col = col * d * 0.6;

        float glow = 1.0 / (1.0 + length(uv) * 2.0);
        col += palette(i * 0.3 + t * 0.2) * glow * 0.15;

        finalCol += col;
    }

    finalCol = pow(finalCol, vec3(0.8));
    finalCol *= 1.0 - length(uv0) * 0.3;

    gl_FragColor = vec4(finalCol, 1.0);
}
