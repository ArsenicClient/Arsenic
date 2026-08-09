#version 120

// Flattens a rendered entity into a uniform silhouette for the glow pass.
//
// This exists instead of reusing chams.fsh because that one carries the source alpha through
// (color.a * alpha). Player skins with a semi-transparent second layer render at alpha < 1, so their
// silhouette came out weaker and blurred into a noticeably dimmer halo than a player whose skin is
// fully opaque. Thresholding makes every player contribute the same coverage, so the glow is only a
// function of shape - not of whatever the skin's alpha channel happens to be.

uniform sampler2D textureIn;
uniform vec4 color;
uniform float threshold;

void main() {
    float alpha = texture2D(textureIn, gl_TexCoord[0].st).a;

    gl_FragColor = vec4(color.rgb, color.a * step(threshold, alpha));
}
