#version 120
uniform vec2 resolution;
uniform float time;
uniform vec2 mouse;
uniform sampler2D textureIn;

#define PI 3.14159265359
// ---------------- soft utility ----------------
float smin(float a,float b,float k){
    float h=clamp(.5+.5*(a-b)/k,0.,1.);
    return mix(a,b,h)-k*h*(1.-h);
}
float smax(float a,float b,float k){
    return smin(a,b,-k);
}
float softRelu(float x){
    return log(.8+exp(min(1.,100.*x)))*.01;
}

// ---------------- 2D SDF ----------------
float softLength(vec2 x){
    float len = length(x);
    return len +0.0009/(len+0.03);
}
float sdRoundBox2D(vec2 p,vec2 b,float r){
    vec2 q=abs(p)-b;
    return .3*(softLength(max(q,0.))+min(smax(q.x,q.y,1.),0.)-r);
}
float sdCircle(vec2 p,float r){
    return .3*(softLength(p)-r);
}
float sceneSDF(vec2 p,vec2 mouse,float k){
    float dBox=sdRoundBox2D(p,vec2(0.45,0.45),0.1);
    float dCir=sdCircle(p-mouse,.15);
    float d=softRelu(smin(dBox,dCir,k));
    return d;
}

// -----------refraction ------------------
vec2 refractOffset(vec2 n2,float eta,float thick){
    vec3 N=normalize(vec3(-n2,1.));//
    vec3 I=vec3(0.,0.,-1.);
    vec3 R=refract(I,N,1./eta);
    float z=max(abs(R.z),1e-5);
    return-thick*(R.xy/z);
}

// -----------reflection ------------------

float fresnel_schlick(float cosTheta,float F0){
    return F0+(1.-F0)*pow(1.-cosTheta,5.);
}
float reflectionTerm(float d,vec2 n2,float gain){
    vec3 N=normalize(vec3(-n2,1.));
    vec3 V=vec3(0.,0.,1.);
    float NoV=clamp(dot(N,V),0.,1.);
    float F=fresnel_schlick(NoV,.06);
    float rim=pow(1.-NoV,3.);
    float inside=smoothstep(fwidth(d)*2.,0.,d);
    float inten=(F+.5*rim)*gain*inside;
    return inten;
}

// ----------- light on plane ------------
float jacobian(vec2 uv,vec2 duv){
    vec2 b=uv+duv;
    // detJ(b)
    vec2 bx=dFdx(b),by=dFdy(b);
    float detB=abs(bx.x*by.y-bx.y*by.x);
    // detJ(uv)
    vec2 ux=dFdx(uv),uy=dFdy(uv);
    float detU=abs(ux.x*uy.y-ux.y*by.x)+1e-12;
    return min(5.,clamp(detU/detB,.8,5.));
}


// ------------ constants ----------------
const float SMIN_K=.05;
const vec3 REFRACTIVE=vec3(1.6,1.50,1.4);//vec3(1.514, 1.504, 1.494);
const float THICKNESS=1.2;
const float REFL_GAIN=.75;
const float DISTANCE_TO_NORMAL_FACTOR=28.;

// ------------ main ---------------------

vec4 tex(vec2 uv){
    //return vec4(mod(uv*vec2(5.,2.),vec2(1.)),1.,1.);
    return texture2D(textureIn,uv);
}
void main(){
    vec2 uv = gl_TexCoord[0].st;
    vec2 p = uv - vec2(0.5);
    float d = sceneSDF(p, mouse - vec2(0.5), SMIN_K);
    vec2 n2 = vec2(dFdx(d),dFdy(d)) * resolution.x * DISTANCE_TO_NORMAL_FACTOR;
    float thick= THICKNESS*(clamp(-30.*d,0.,1.));
    float inside=smoothstep(fwidth(d)*2.,0.,d);
    // --------  chromatic aberration --------
    vec2 duvR=refractOffset(n2,REFRACTIVE.r,thick)*inside;
    vec2 duvG=refractOffset(n2,REFRACTIVE.g,thick)*inside;
    vec2 duvB=refractOffset(n2,REFRACTIVE.b,thick)*inside;
    float r=tex(uv+duvR).r*jacobian(uv,duvR);
    float g=tex(uv+duvG).g*jacobian(uv,duvG);
    float b=tex(uv+duvB).b*jacobian(uv,duvB);
    vec3 bg=vec3(r,g,b);
    vec3 refl=vec3(1)*reflectionTerm(d,n2,REFL_GAIN);
    vec3 col=bg+refl;
    col+=inside*.02*pow(abs(dot(n2,normalize(vec2(1.5,1.)))),3.);
    gl_FragColor=vec4(col,1.);
}
