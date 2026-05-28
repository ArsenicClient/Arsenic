#version 120

uniform vec2 location, rectSize;
uniform sampler2D u_texture, u_texture2;
void main() {
    vec2 coords = (gl_FragCoord.xy - location) / rectSize;
    float texColorAlpha = texture2D(u_texture, gl_TexCoord[0].st).a;
    vec3 tex2Color = texture2D(u_texture2, gl_TexCoord[0].st).rgb;
    gl_FragColor = vec4(tex2Color, texColorAlpha);
}
