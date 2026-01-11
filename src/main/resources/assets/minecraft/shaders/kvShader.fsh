uniform float time;
uniform vec2 resolution;

#define PI 3.14159265359
#define DEG2RAD (PI / 180.0)

vec3 hsv2rgb2(vec3 c, float k) {
    vec4 K = vec4(3. / 3., 2. / 3., 1. / 3., 3.);
    vec3 p = smoothstep(0. + k, 1. - k,
        .5 + .5 * cos((c.xxx + K.xyz) * 360.0 * DEG2RAD));
    return c.z * mix(K.xxx, p, c.y);
}

vec3 tonemap(vec3 v)
{
    return mix(v, vec3(1.), smoothstep(1., 4., dot(v, vec3(1.))));
}

float f1(float x, float offset, float freq)
{
    return .4 * sin(30.0 * DEG2RAD * x + offset) + .1 * sin(freq * x);
}

void main()
{
    vec2 fragCoord = gl_FragCoord.xy;
    float scale = resolution.y;
    vec2 uv = (2. * fragCoord - resolution.xy) / scale;
    vec3 col = vec3(0);
    
    float offsets[3];
    offsets[0] = 0. * 360.0 * DEG2RAD / 3.;
    offsets[1] = 1. * 360.0 * DEG2RAD / 3.;
    offsets[2] = 2. * 360.0 * DEG2RAD / 3.;
    
    float freqs[3];
    freqs[0] = 160.0 * DEG2RAD;
    freqs[1] = 213.0 * DEG2RAD;
    freqs[2] = 186.0 * DEG2RAD;
    
    float colorfreqs[3];
    colorfreqs[0] = .317;
    colorfreqs[1] = .210;
    colorfreqs[2] = .401;
    
    for (int i = 0; i < 3; ++i) {
        float x = uv.x + 4. * time;
        float y = f1(x, offsets[i], freqs[i]);
        float uv_x = min(uv.x, 1. + .4 * sin(210.0 * DEG2RAD * time + 360.0 * DEG2RAD * float(i) / 3.));
        
        float r = uv.x / 40.;
        //float r = exp(uv.x + 1.) / 100. - .05;
        float d1 = length(vec2(uv_x, y) - uv) - r;
        col += 1. / pow(max(1., d1 * scale), .8 + .1 * sin(245.0 * DEG2RAD * time + 360.0 * DEG2RAD * float(i) / 3.))
            * (vec3(1.) + hsv2rgb2(vec3(colorfreqs[i] * x, 1., 1.), .07));
    }
    
    gl_FragColor = vec4(tonemap(col), 1.);
}
