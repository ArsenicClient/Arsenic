#version 120

// How the incoming vertices are laid out. 0: an ordinary mesh with real normals. 1: a quad around a point, with the
// corner in texcoord -1..1, opened out to face the camera to uWidth either side.
uniform int uGeom;
uniform float uWidth;

varying vec2 vUv;
varying vec3 vPos;
varying vec3 vNormal;
// The normal and the direction to the camera in world axes, for effects that need to know which way is up
varying vec3 vWorldNormal;
varying vec3 vWorldView;

void main() {
    vUv = gl_MultiTexCoord0.st;
    vWorldNormal = gl_Normal;
    vWorldView = (gl_ModelViewMatrixInverse * vec4(0.0, 0.0, 0.0, 1.0)).xyz - gl_Vertex.xyz;
    vec4 eye = gl_ModelViewMatrix * gl_Vertex;
    vec3 n = gl_NormalMatrix * gl_Normal;

    if (uGeom == 1) {
        eye.xy += vUv * uWidth;
        n = vec3(0.0, 0.0, 1.0);
    }

    vPos = eye.xyz;
    vNormal = n;
    gl_Position = gl_ProjectionMatrix * eye;
}
