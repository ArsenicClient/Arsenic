#version 120
uniform float time;
uniform vec2 resolution;

float hash(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0; float a = 0.5;
    for (int i = 0; i < 5; i++){ v += a * noise(p); p *= 2.0; a *= 0.5; }
    return v;
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv * vec2(3.0, 2.0);
    p.y -= time * 1.2;
    float n = fbm(p + fbm(p * 0.5));
    float flame = n * (1.0 - uv.y) * 1.8;
    vec3 col = vec3(1.5, 0.5, 0.1) * flame;
    col += vec3(1.0, 0.8, 0.2) * pow(flame, 3.0);
    col = pow(col, vec3(0.9));
    gl_FragColor = vec4(col, 1.0);
}
