#version 150
uniform sampler2D DiffuseSampler;
in vec2 texCoord0;
out vec4 fragColor;
uniform vec4 Color;

void main() {
    vec4 c = texture(DiffuseSampler, texCoord0);
    fragColor = vec4(c.rgb * Color.rgb, c.a);
}
