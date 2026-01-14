#version 120

uniform sampler2D textureIn;
uniform vec4 color;
void main() {
    float alpha = texture2D(textureIn, gl_TexCoord[0].st).a;

    gl_FragColor = vec4(color.rgb, color.a * mix(0.0, alpha, step(0.0, alpha)));
}
