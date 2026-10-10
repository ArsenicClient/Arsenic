#version 120

uniform float uTime;
// 0 = ring band (uv.y runs inner to outer), 1 = curtain (uv.y runs ground to top), 2 = fin (uv.y runs base to tip)
uniform float uMode;

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec3 col = gl_Color.rgb;
    float a = 1.0;

    if (uMode < 0.5) {
        // Soft-edged solid body with a hot centre, and a pulse of light travelling round the ring
        float d = abs(uv.y - 0.5) * 2.0;
        float body = 1.0 - smoothstep(0.55, 1.0, d);
        float hot = 1.0 - smoothstep(0.0, 0.45, d);
        float travel = 0.7 + 0.3 * sin(uv.x * 25.1327 - uTime * 2.5);
        float sparkle = 0.92 + 0.08 * sin(uv.x * 160.0 + uTime * 9.0);
        a = body * travel * sparkle;
        col = mix(col, vec3(1.0), hot * 0.55);
    } else if (uMode < 1.5) {
        // Bright at the top, fading toward the ground, with streaks that drift down the curtain
        float streak = 0.5 + 0.5 * sin(uv.x * 90.0 + sin(uv.y * 10.0 - uTime * 3.0) * 2.5);
        float rise = pow(uv.y, 1.4);
        a = rise * (0.45 + 0.55 * streak);
        col = mix(col, vec3(1.0), rise * streak * 0.25);
    } else {
        // The tip burns white-hot and flickers
        float tip = smoothstep(0.35, 1.0, uv.y);
        float flick = 0.9 + 0.1 * sin(uTime * 22.0 + uv.x * 30.0);
        col = mix(col, vec3(1.0), tip * tip * 0.7);
        a = flick;
    }

    gl_FragColor = vec4(col, gl_Color.a * a);
}
