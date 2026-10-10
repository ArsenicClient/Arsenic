#version 120

// Which effect the current geometry carries; the numbers match the FX_ constants in TargetFx
uniform int uFx;
uniform float uTime;       // seconds
uniform vec3 uColor;       // theme colour
uniform vec3 uColor2;      // health colour, red when low to green when full
uniform float uAlpha;
uniform float uHit;        // 1 the moment a hit lands, falling to 0
uniform float uParam;      // classic trail: vertical speed of the ring, -1..1.
                           // crystal: a phase 0..1 so neighbouring crystals glint at different times
uniform float uGain;       // strength of the current piece, for fading parts in and out
uniform vec4 uHealth;      // health arc: health shown, health a moment ago, absorption (all 0..1), number of hearts
uniform float uFacing;     // health arc: angle round the ring of the side facing the viewer

varying vec2 vUv;
varying vec3 vPos;
varying vec3 vNormal;
varying vec3 vWorldNormal;
varying vec3 vWorldView;

const float PI = 3.14159265;
const float TAU = 6.28318531;
// How much of the colour behind the effect it covers. 0 would be purely additive, which washes out against snow and
// sky; a little coverage keeps the glow readable on bright backgrounds.
const float COVER = 0.45;

// Colour and coverage summed over every layer of the effect, premultiplied
vec3 acc;
float accA;
// How much of the background the effect hides; COVER unless the effect is solid
float coverage;

void add(vec3 c, float a) {
    acc += c * a;
    accA += a;
}

// 1 - |N.V|: 0 where the surface faces the camera, 1 along its silhouette
float fresnel() {
    return 1.0 - abs(dot(normalize(vNormal), normalize(-vPos)));
}

// CLASSIC: the ring itself, a tube taller than it is wide. White-hot where it faces you, theme colour round its edges,
// with two highlights chasing round it.
void tube() {
    float f = fresnel();
    float face = pow(1.0 - f, 1.5);
    float chase = pow(0.5 + 0.5 * sin(vUv.x * TAU * 2.0 - uTime * 3.0), 4.0);
    vec3 c = mix(uColor, vec3(1.0), face * 0.3 + chase * 0.2 + uHit * 0.4);
    add(c, 0.4 + 0.45 * f + chase * 0.3 + uHit * 0.6);
}

// CLASSIC: a wall through the tube. It glows just above and below the tube and leaves a streaked trail behind it, longer
// the faster the ring is moving. vUv.y is the height above the tube's middle in blocks.
void classicTrail() {
    const float HALF = 0.1;
    float h = vUv.y;
    float outside = max(abs(h) - HALF, 0.0);
    float halo = exp(-outside * 20.0);

    float behind = (uParam >= 0.0 ? -h : h) - HALF;
    float len = 0.06 + 0.55 * abs(uParam);
    float trail = behind > 0.0 ? exp(-behind / len * 2.2) : 0.0;

    // whole-number frequencies around the ring so the streaks meet up where the wall starts and ends
    float u = vUv.x * TAU;
    float streak = 0.5 + 0.5 * sin(u * 29.0 + 2.5 * sin(u * 5.0 + uTime * 1.3));
    streak = mix(streak, 1.0, exp(-max(behind, 0.0) * 12.0));
    float flow = 0.8 + 0.2 * sin(behind * 40.0 - uTime * 10.0);

    float ends = 1.0 - smoothstep(0.55, 0.8, abs(h));
    float a = (halo * 0.6 + trail * 1.1 * streak * flow) * ends * (1.0 + uHit * 0.5);
    add(uColor, a);
}

// CROWN: the glow round a gem, on a camera-facing quad with vUv -1..1
void orb() {
    float d = length(vUv);
    float core = exp(-d * d * 30.0);
    float glow = exp(-d * 4.5) * 0.6;
    float fade = 1.0 - smoothstep(0.75, 1.0, d);
    add(mix(uColor, vec3(1.0), core), (core * 1.3 + glow) * fade);
}

// CROWN: a faceted gem. vUv.y runs from its bottom point (0) to its top point (1). The flat
// normals give every facet its own shade and highlight; a glint climbs it and the top point burns white.
void crystal() {
    vec3 n = normalize(vNormal);
    vec3 v = normalize(-vPos);
    float f = 1.0 - abs(dot(n, v));
    vec3 l = normalize(vec3(-0.35, 0.8, 0.5));
    float diff = max(dot(n, l), 0.0);
    float spec = pow(max(dot(reflect(-l, n), v), 0.0), 18.0);

    float y = vUv.y;
    float g = fract(uTime * 0.6 + uParam) * 1.6 - 0.3;
    float glint = exp(-abs(y - g) * 12.0);
    float tip = smoothstep(0.55, 1.0, y);
    tip *= tip;

    vec3 body = uColor * (0.55 + 0.6 * diff);
    vec3 c = mix(body, vec3(1.0), clamp(tip * 0.8 + spec + glint * 0.35 + uHit * 0.25, 0.0, 1.0));
    add(c, (0.3 + 0.3 * diff + 0.55 * f + spec * 0.9 + glint * 0.35 + tip * 0.5) * (1.0 + uHit * 0.5));
}

// CROWN: polished gold. A reflection of a bright sky over dark ground, a sharp highlight from a fixed sun, a shine
// sweeping round the band, and a rim of theme colour. Solid, unlike everything else.
void metal() {
    vec3 n = normalize(vWorldNormal);
    vec3 v = normalize(vWorldView);
    // the inside of the band seen from above
    if (dot(n, v) < 0.0)
        n = -n;
    vec3 r = reflect(-v, n);
    vec3 gold = vec3(1.0, 0.74, 0.26);
    float sky = smoothstep(-0.35, 0.65, r.y);
    vec3 col = mix(gold * 0.2, gold * 1.05, sky);
    // a darker band where the horizon would be, which is what makes it read as metal
    col *= 1.0 - 0.35 * exp(-abs(r.y - 0.05) * 9.0);

    vec3 sun = normalize(vec3(-0.45, 0.75, 0.35));
    float spec = pow(max(dot(r, sun), 0.0), 48.0);
    float sweep = pow(0.5 + 0.5 * sin(vUv.x * TAU * 3.0 + vUv.y * 3.0 - uTime * 2.2), 24.0);
    float rim = pow(1.0 - abs(dot(n, v)), 3.0);

    col += vec3(1.0, 0.95, 0.8) * (spec * 1.3 + sweep * 0.45);
    col += uColor * rim * 0.45;
    col = mix(col, vec3(1.0, 0.96, 0.85), uHit * 0.3);
    add(col, 1.0);
    coverage = 1.0;
}

// HEALTH_ARC: how far round the ring this point is from the side facing the viewer, 0 in front to 1 at the back.
// The bar is full out to the health fraction.
float healthPos() {
    return abs(mod(vUv.x * TAU - uFacing + PI, TAU) - PI) / PI;
}

// 1 while health is low, pulsing like a heartbeat: two quick beats then a rest
float heartbeat() {
    float low = 1.0 - smoothstep(0.15, 0.35, uHealth.x);
    float t = fract(uTime * 1.1);
    float beat = exp(-t * 30.0) + 0.6 * exp(-max(t - 0.18, 0.0) * 30.0) * step(0.18, t);
    return low * beat;
}

// HEALTH_ARC: the bar itself, flat on the ground. vUv.y is the distance out from the bar's middle in blocks.
void healthBar() {
    float pos = healthPos();
    float d = vUv.y;
    float aa = 0.004;
    float hearts = uHealth.w;

    const float HALF = 0.06;
    float bar = smoothstep(HALF, HALF - 0.008, abs(d));
    float fill = smoothstep(uHealth.x + aa, uHealth.x - aa, pos);
    float lag = smoothstep(uHealth.y + aa, uHealth.y - aa, pos) * (1.0 - fill);
    // a small gap between hearts
    float gapDist = abs(fract(pos * hearts + 0.5) - 0.5) / hearts;
    float gaps = smoothstep(0.003, 0.007, gapDist);
    float beat = heartbeat();

    // the empty track and a thin frame either side of it, in the theme colour
    add(uColor, bar * (1.0 - fill - lag) * 0.12 * gaps);
    float frame = smoothstep(0.006, 0.002, abs(abs(d) - 0.085));
    float sweep = 0.6 + 0.4 * cos(vUv.x * TAU * 2.0 - uTime * 1.5);
    add(uColor, frame * 0.4 * sweep);

    // filled hearts: brightest down the middle of the bar, a glow round them, and a glint on the leading edge
    float shade = 0.7 + 0.3 * (1.0 - abs(d) / HALF);
    vec3 hp = mix(uColor2, vec3(1.0, 0.15, 0.1), beat * 0.6);
    add(mix(hp, vec3(1.0), (1.0 - abs(d) / HALF) * 0.25), bar * fill * gaps * shade * (0.9 + beat * 0.6));
    add(hp, exp(-max(abs(d) - HALF, 0.0) * 28.0) * fill * (0.35 + beat * 0.5) * (1.0 - bar));
    float edge = exp(-abs(pos - uHealth.x) * 90.0) * step(0.001, uHealth.x);
    add(vec3(1.0), edge * (bar + exp(-abs(d) * 20.0) * 0.5) * 0.9);

    // health just lost, pale and fading out as it drains
    add(mix(vec3(1.0), vec3(1.0, 0.3, 0.25), 0.35), bar * lag * gaps * 0.85);

    // absorption hearts, a thin gold arc outside the frame
    float absorb = smoothstep(uHealth.z + aa, uHealth.z - aa, pos) * step(0.001, uHealth.z);
    add(vec3(1.0, 0.8, 0.2), absorb * (smoothstep(0.014, 0.008, abs(d - 0.115)) + 0.3 * exp(-abs(d - 0.115) * 40.0)));

    // a hit flashes the whole ring
    add(vec3(1.0), uHit * uHit * bar * 0.5);
}

// HEALTH_ARC: a curtain of light rising from the filled part of the bar. vUv.y is the height above the ground.
void healthWall() {
    float pos = healthPos();
    float h = vUv.y / 0.45;
    float aa = 0.004;
    float fill = smoothstep(uHealth.x + aa, uHealth.x - aa, pos);
    float lag = smoothstep(uHealth.y + aa, uHealth.y - aa, pos) * (1.0 - fill);

    float rise = exp(-h * 3.2) * (1.0 - smoothstep(0.8, 1.0, h));
    float streaks = 0.55 + 0.45 * sin(vUv.x * TAU * 40.0 + sin(vUv.x * TAU * 7.0 + uTime) * 2.0);
    float flow = 0.75 + 0.25 * sin(h * 18.0 - uTime * 5.0);
    float beat = heartbeat();
    vec3 hp = mix(uColor2, vec3(1.0, 0.15, 0.1), beat * 0.6);

    add(hp, fill * rise * streaks * flow * (0.55 + beat * 0.6));
    add(vec3(1.0, 0.75, 0.7), lag * rise * 0.35);
}

void main() {
    acc = vec3(0.0);
    accA = 0.0;
    coverage = COVER;

    if (uFx == 0) tube();
    else if (uFx == 1) classicTrail();
    else if (uFx == 2) orb();
    else if (uFx == 3) crystal();
    else if (uFx == 4) metal();
    else if (uFx == 5) healthBar();
    else healthWall();

    acc *= uGain;
    accA *= uGain;

    // where layers stack past full, bleed toward white rather than clipping flat
    vec3 col = acc + vec3(max(accA - 1.0, 0.0) * 0.3);
    float cover = min(accA, 1.0) * coverage;
    gl_FragColor = vec4(col, cover) * uAlpha;
}
