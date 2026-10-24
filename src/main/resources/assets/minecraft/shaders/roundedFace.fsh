#version 120

uniform vec2 rectSize;
uniform vec2 uvMin, uvMax;
uniform sampler2D textureIn;
uniform float radius, alpha;

float sdRoundBox(vec2 point, vec2 halfSize, float cornerRadius) {
    vec2 q = abs(point) - halfSize + cornerRadius;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - cornerRadius;
}

void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 halfSize = rectSize * 0.5;
    float cornerRadius = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    float distance = sdRoundBox((uv - 0.5) * rectSize, halfSize, cornerRadius);
    float antialias = max(fwidth(distance), 0.75);
    float coverage = 1.0 - smoothstep(-antialias, antialias, distance);
    vec4 skin = texture2D(textureIn, mix(uvMin, uvMax, uv));

    if (coverage <= 0.001 || skin.a <= 0.001)
        discard;
    gl_FragColor = vec4(skin.rgb, skin.a * alpha * coverage);
}
