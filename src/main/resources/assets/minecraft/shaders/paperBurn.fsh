#version 120
uniform float time;
uniform vec2 resolution;
uniform float progress;   // 0 = sheet fully covers the GUI, 1 = fully burnt away
uniform vec3 themeColor;

float hash(vec2 p){ return fract(sin(dot(p, vec2(41.3, 289.1))) * 43758.5453); }
float noise(vec2 p){
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), u.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), u.x), u.y);
}
float fbm(vec2 p){
    float v = 0.0; float a = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 5; i++){ v += a * noise(p); p = m * p; a *= 0.5; }
    return v;
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    float aspect = resolution.x / resolution.y;
    vec2 p = vec2(uv.x * aspect, uv.y);

    // burn field - large soft blobs plus fine crinkle so the edge is ragged
    float field = fbm(p * 3.0 + vec2(0.0, time * 0.05));
    field += 0.18 * fbm(p * 12.0 - time * 0.1);
    field = clamp(field, 0.0, 1.0);

    // widen the effective range slightly so progress 0..1 fully covers/clears
    float burn = progress * 1.25 - 0.12;
    float edge = 0.10;             // width of the glowing ember band

    if (field < burn - edge)
        discard;                    // burnt through -> reveal the GUI beneath

    // d: 0 at the inner (just-burning) side, 1 out in intact paper
    float d = clamp((field - (burn - edge)) / edge, 0.0, 1.0);

    float flick = 0.85 + 0.25 * sin(time * 18.0 + field * 60.0);
    vec3 charCol  = vec3(0.03, 0.02, 0.02);
    vec3 emberHot = vec3(1.0, 0.75, 0.20) * flick;
    vec3 emberLow = vec3(0.95, 0.30, 0.02) * flick;
    // paper takes a faint tint from the theme so it feels part of the GUI
    vec3 paperCol = mix(vec3(0.07, 0.055, 0.05), themeColor * 0.25, 0.25);

    vec3 col;
    if (d < 0.30)
        col = mix(charCol, emberHot, d / 0.30);        // charred rim -> hot ember
    else if (d < 0.55)
        col = mix(emberHot, emberLow, (d - 0.30) / 0.25); // ember cooling
    else
        col = mix(emberLow, paperCol, (d - 0.55) / 0.45); // ember -> paper

    gl_FragColor = vec4(col, 1.0);
}
