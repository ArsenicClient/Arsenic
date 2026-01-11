#version 120

uniform sampler2D textureIn, textureToCheck;
uniform vec2 texelSize, direction;
uniform vec3 color;
uniform bool avoidTexture;
uniform float exposure, radius;
uniform float weights[256];

#define offset direction * texelSize

void main() {
    if (direction.y == 1 && avoidTexture) {
        if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;
    }
    vec4 innerColor = texture2D(textureIn, gl_TexCoord[0].st);
    innerColor.rgb *= innerColor.a;
    innerColor *= weights[0];
    for (float r = 1.0; r <= radius; r++) {
        vec4 colorCurrent1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);
        vec4 colorCurrent2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);

        colorCurrent1.rgb *= colorCurrent1.a;
        colorCurrent2.rgb *= colorCurrent2.a;

        innerColor += (colorCurrent1 + colorCurrent2) * weights[int(r)];
    }

    gl_FragColor = vec4(innerColor.rgb / innerColor.a, mix(innerColor.a, 1.0 - exp(-innerColor.a * exposure), step(0.0, direction.y)));
}
