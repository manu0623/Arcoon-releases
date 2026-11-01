#if defined(VERTEX)
attribute vec4 VertexCoord;
attribute vec2 TexCoord;
varying vec2 uv;

void main() {
    gl_Position = VertexCoord;
    uv = TexCoord;
}

#elif defined(FRAGMENT)
precision highp float;
varying vec2 uv;
uniform sampler2D Texture;

#ifdef _HAS_SENSOR_UNIFORMS
uniform vec3 Gyroscope;
uniform vec3 Accelerometer;
uniform vec3 AccelerometerRest;
#endif

void main() {
    vec3 color = texture2D(Texture, uv).rgb * 0.45;
#ifdef _HAS_SENSOR_UNIFORMS
    bool calibrated = length(AccelerometerRest) > 0.5;
    vec3 tilt = Accelerometer - AccelerometerRest;
    vec2 center = vec2(0.5) + clamp(tilt.xy * 0.4, vec2(-0.35), vec2(0.35));
    float dotMask = 1.0 - smoothstep(0.02, 0.03, distance(uv, center));
    color = mix(color, vec3(0.0, 1.0, 1.0), dotMask);
    if (uv.y > 0.92) {
        color = clamp(abs(Gyroscope) * 0.7 + vec3(0.03), 0.0, 1.0);
    }
    if (uv.y < 0.06) {
        color = calibrated ? vec3(0.0, 0.8, 0.0) : vec3(1.0, 0.5, 0.0);
    }
#else
    if (uv.y < 0.06 || uv.y > 0.92) {
        color = vec3(1.0, 0.0, 0.0);
    }
#endif
    gl_FragColor = vec4(color, 1.0);
}
#endif
