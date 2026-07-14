#version 120
uniform float time;
uniform vec2 resolution;

float hash(vec2 p){ return fract(sin(dot(p, vec2(27.6, 91.3))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0; float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 6; i++){ v += a * noise(p); p = m * p; a *= 0.5; }
    return v;
}

void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / resolution.y;
    vec2 q = vec2(fbm(uv * 3.0 + time * 0.1), fbm(uv * 3.0 - time * 0.12));
    float f = fbm(uv * 3.0 + q * 2.0);
    vec3 col = mix(vec3(0.05, 0.0, 0.15), vec3(0.9, 0.2, 0.6), f);
    col = mix(col, vec3(0.1, 0.4, 0.9), clamp(q.x * q.x, 0.0, 1.0));
    col += vec3(hash(uv * 800.0)) * step(0.995, hash(floor(uv * 400.0)));
    gl_FragColor = vec4(col * 1.2, 1.0);
}
