#version 120

uniform vec2 location, rectSize;
uniform vec4 color1, color2, color3, color4;
uniform float radius;

#define NOISE .5/255.0

float roundSDF(vec2 p, vec2 b, float r) {
    return length(max(abs(p) - b , 0.0)) - r;
}

vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
    vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
    //Dithering the color
    // from https://shader-tutorial.dev/advanced/color-banding-dithering/
    color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
    return color;
}

void main() {
    vec2 st = gl_TexCoord[0].st;
    vec2 halfSize = rectSize * .5;
    
   // use the bottom leftColor as the alpha
    float smoothedAlpha =  (1.0-smoothstep(0.0, 2., roundSDF(halfSize - (gl_TexCoord[0].st * rectSize), halfSize - radius - 1., radius)));
    vec4 gradient = createGradient(st, color1, color2, color3, color4);
    gl_FragColor = vec4(gradient.rgb, gradient.a * smoothedAlpha);
}
