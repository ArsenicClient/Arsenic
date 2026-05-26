#version 120

uniform float time;
uniform vec2 resolution;

float tanh_f(float x) {
    float e = exp(2.0 * clamp(x, -10.0, 10.0));
    return (e - 1.0) / (e + 1.0);
}

vec2 tanh_v(vec2 x) {
    return vec2(tanh_f(x.x), tanh_f(x.y));
}

void main()
{
    vec2 u = gl_FragCoord.xy;
    vec2 v = resolution.xy;
    u = 0.2 * (u + u - v) / v.y;

    vec4 z = vec4(1.0, 2.0, 3.0, 0.0);
    vec4 o = z;

    float a = 0.5;
    float t = time;

    for (float i = 0.0; i < 19.0; i += 1.0)
    {
        o += (1.0 + cos(z + t))
        / length((1.0 + (i + 1.0) * dot(v, v))
        * sin(1.5 * u / (0.5 - dot(u, u)) - 9.0 * u.yx + t));

        t += 1.0;
        a += 0.03;
        v = cos(t - 7.0 * u * pow(a, i + 1.0)) - 5.0 * u;

        float angle = i + 1.0 + 0.02 * t;
        float c = cos(angle);
        float s = sin(angle);
        mat2 rotMat = mat2(c, -s, s, c);
        u *= rotMat;

        u += tanh_v(40.0 * dot(u, u) * cos(100.0 * u.yx + t)) / 200.0
        + 0.2 * a * u
        + cos(4.0 / exp(dot(o, o) / 100.0) + t) / 300.0;
    }

    o = 25.6 / (min(o, vec4(13.0)) + 164.0 / o)
    - dot(u, u) / 250.0;

    // Target: #DD425E = vec3(0.867, 0.259, 0.369)
    // Remap each channel toward the target hue
    float lum = dot(o.rgb, vec3(0.299, 0.587, 0.114));
    vec3 target = vec3(0.867, 0.259, 0.369);
    vec3 tinted = mix(o.rgb, lum * target * 2.8, 0.72);

    gl_FragColor = vec4(tinted, o.a);
}