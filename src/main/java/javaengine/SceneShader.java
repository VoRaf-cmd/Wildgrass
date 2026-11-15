package javaengine;

import org.joml.Matrix4f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

final class SceneShader implements AutoCloseable {
    private static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 normal;
            layout(location = 2) in vec3 color;
            layout(location = 3) in float growth;
            layout(location = 4) in vec4 joints;
            layout(location = 5) in vec4 weights;
            uniform mat4 viewProjection;
            uniform mat4 model;
            uniform bool skinned;
            uniform mat4 bones[10];
            uniform vec3 clayTint;
            uniform int vegetation;
            uniform float plantHeight;
            uniform float windTime;
            uniform float windStrength;
            uniform vec2 windDirection;
            uniform vec2 playerPosition;
            out vec3 worldPosition;
            out vec3 worldNormal;
            out vec3 surfaceColor;
            out float viewDepth;
            out float bladeGrowth;
            out vec2 patchPosition;
            void main() {
                vec3 plant = position;
                vec3 surfaceNormal = normal;
                if (skinned) {
                    mat4 skin = bones[int(joints.x)] * weights.x + bones[int(joints.y)] * weights.y
                              + bones[int(joints.z)] * weights.z + bones[int(joints.w)] * weights.w;
                    plant = (skin * vec4(plant, 1.0)).xyz;
                    surfaceNormal = mat3(skin) * surfaceNormal;
                }
                if (vegetation == 1 && growth >= 0.0) {
                    plant.y -= growth * (1.0 - plantHeight);
                }
                if (vegetation == 3) {
                    float limb = min(abs(plant.y) / 0.415, 1.0);
                    plant.z += pow(limb, 1.65) * 0.065 * plantHeight;
                    plant.y *= 1.0 - plantHeight * 0.046;
                }
                vec4 world = model * vec4(plant, 1.0);
                if (vegetation != 0) {
                    float phase = dot(world.xz, windDirection);
                    float gust = 0.60 + 0.36 * sin(windTime * 2.9 - phase * 0.64)
                                 + 0.22 * sin(windTime * 5.5 - phase * 1.8);
                    if (vegetation == 1 && growth >= 0.0) {
                        float blade = growth * plantHeight;
                        float flex = blade * blade / (1.0 + max(blade - 1.15, 0.0) * 2.6);
                        world.xz += windDirection * (flex * gust * windStrength * 0.46);
                        vec2 away = world.xz - playerPosition;
                        float nearPlayer = 1.0 - smoothstep(0.14, 0.77, length(away));
                        world.xz += away * nearPlayer * blade * 0.58;
                        world.y -= nearPlayer * blade * 0.40;
                    } else if (vegetation == 2) {
                        float canopy = smoothstep(1.8, 4.4, plant.y);
                        bool leafy = color.g > color.r * 1.12 && color.g > color.b * 1.16;
                        world.xz += windDirection * gust * canopy * windStrength * (leafy ? 0.17 : 0.08);
                        world.y += canopy * sin(windTime * 6.0 + phase * 4.0 + plant.y * 9.0)
                                   * (leafy ? 0.043 : 0.012) * windStrength;
                    }
                }
                gl_Position = viewProjection * world;
                worldPosition = world.xyz;
                worldNormal = transpose(inverse(mat3(model))) * surfaceNormal;
                surfaceColor = skinned ? clayTint * color : color;
                viewDepth = gl_Position.w;
                bladeGrowth = growth;
                patchPosition = position.xz;
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec3 worldPosition;
            in vec3 worldNormal;
            in vec3 surfaceColor;
            in float viewDepth;
            in float bladeGrowth;
            in vec2 patchPosition;
            uniform bool displaySurface;
            uniform int vegetation;
            uniform vec2 treeFallDirection;
            uniform float treeGroundContact;
            uniform vec2 shadowOrigin;
            uniform float charAmount;
            uniform bool emissive;
            uniform vec4 fireLights[5];
            uniform int fireLightCount;
            uniform float opacity;
            uniform vec4 blastMarks[8];
            uniform int blastMarkCount;
            out vec4 fragment;
            void main() {
                vec3 color = surfaceColor;
                if (vegetation == 1 && bladeGrowth < 0.0) {
                    vec2 grain = floor(worldPosition.xz * 17.0);
                    float scatter = fract(sin(dot(grain, vec2(43.71, 79.07))) * 4816.13);
                    float edge = max(abs(patchPosition.x), abs(patchPosition.y)) / 0.825;
                    if (charAmount < 0.23 || edge + scatter * 0.24 > 0.20 + charAmount * 0.97) discard;
                    color *= 0.80 + scatter * 0.26;
                }
                if (displaySurface) {
                    vec2 xz = worldPosition.xz;
                    float meadow = sin(xz.x * 0.64 + xz.y * 0.36) * sin(xz.y * 0.47 - xz.x * 0.4);
                    vec2 grain = floor(xz * 13.0);
                    float mottling = fract(sin(dot(grain, vec2(32.47, 87.23))) * 4374.43);
                    color *= 0.87 + meadow * 0.14 + mottling * 0.16;
                    float trail = (1.0 - smoothstep(0.16, 1.33, abs(xz.x - sin(xz.y * 0.22) * 1.8)))
                                  * (1.0 - smoothstep(4.0, 13.5, abs(xz.y)));
                    color = mix(color, vec3(0.51, 0.43, 0.27) * (0.8 + 0.22 * mottling), trail * 0.52);
                    color *= mix(0.62, 1.0, smoothstep(0.36, 1.35, length(worldPosition.xz)));
                    vec2 fromTrunk = xz - shadowOrigin;
                    float alongTrunk = clamp(dot(fromTrunk, treeFallDirection), 0.62, 3.65);
                    float distanceToTrunk = length(fromTrunk - alongTrunk * treeFallDirection);
                    float shade = 1.0 - smoothstep(0.20, 1.00, distanceToTrunk);
                    color *= 1.0 - 0.39 * shade * treeGroundContact;
                    for (int i = 0; i < blastMarkCount; i++) {
                        vec2 offset = xz - blastMarks[i].xy;
                        float edge = length(offset) / blastMarks[i].z;
                        float ragged = sin(offset.x * 11.0 + blastMarks[i].w)
                                     * cos(offset.y * 9.0 - blastMarks[i].w) * 0.105;
                        float scorch = 1.0 - smoothstep(0.63, 1.06, edge + ragged);
                        vec3 ash = mix(vec3(0.15, 0.11, 0.081), vec3(0.32, 0.23, 0.15), clamp(edge, 0.0, 1.0));
                        color = mix(color, ash * (0.84 + mottling * 0.23), scorch);
                    }
                }
                float diffuse = max(dot(normalize(worldNormal), normalize(vec3(-0.4, 0.8, 0.65))), 0.0);
                color = mix(color, color * 0.19 + vec3(0.025, 0.019, 0.014), charAmount);
                vec3 lit = emissive ? color : color * (0.40 + diffuse * 0.60);
                if (!emissive) {
                    for (int i = 0; i < fireLightCount; i++) {
                        vec3 offset = fireLights[i].xyz - worldPosition;
                        float distanceSquared = dot(offset, offset);
                        float attenuation = fireLights[i].w / (1.0 + distanceSquared * 3.5);
                        lit += color * vec3(1.0, 0.50, 0.19) * attenuation;
                    }
                }
                float fog = smoothstep(12.0, 44.0, viewDepth);
                fragment = vec4(mix(lit, vec3(0.55, 0.69, 0.70), fog), opacity);
            }
            """;

    private final int program;
    private final int viewProjection;
    private final int model;
    private final int displaySurface;
    private final int treeFallDirection;
    private final int treeGroundContact;
    private final int shadowOrigin;
    private final int charAmount;
    private final int emissive;
    private final int fireLights;
    private final int fireLightCount;
    private final int vegetation;
    private final int plantHeight;
    private final int windTime;
    private final int windStrength;
    private final int windDirection;
    private final int playerPosition;
    private final int opacity;
    private final int blastMarks;
    private final int blastMarkCount;
    private final int skinned, bones, clayTint;

    SceneShader() {
        program = link(VERTEX, FRAGMENT);
        viewProjection = glGetUniformLocation(program, "viewProjection");
        model = glGetUniformLocation(program, "model");
        displaySurface = glGetUniformLocation(program, "displaySurface");
        treeFallDirection = glGetUniformLocation(program, "treeFallDirection");
        treeGroundContact = glGetUniformLocation(program, "treeGroundContact");
        shadowOrigin = glGetUniformLocation(program, "shadowOrigin");
        charAmount = glGetUniformLocation(program, "charAmount");
        emissive = glGetUniformLocation(program, "emissive");
        fireLights = glGetUniformLocation(program, "fireLights[0]");
        fireLightCount = glGetUniformLocation(program, "fireLightCount");
        vegetation = glGetUniformLocation(program, "vegetation");
        plantHeight = glGetUniformLocation(program, "plantHeight");
        windTime = glGetUniformLocation(program, "windTime");
        windStrength = glGetUniformLocation(program, "windStrength");
        windDirection = glGetUniformLocation(program, "windDirection");
        playerPosition = glGetUniformLocation(program, "playerPosition");
        opacity = glGetUniformLocation(program, "opacity");
        blastMarks = glGetUniformLocation(program, "blastMarks[0]");
        blastMarkCount = glGetUniformLocation(program, "blastMarkCount");
        skinned = glGetUniformLocation(program, "skinned");
        bones = glGetUniformLocation(program, "bones[0]");
        clayTint = glGetUniformLocation(program, "clayTint");
    }

    static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        try {
            int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
            try {
                int program = glCreateProgram();
                glAttachShader(program, vertex);
                glAttachShader(program, fragment);
                glLinkProgram(program);
                if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                    String message = glGetProgramInfoLog(program);
                    glDeleteProgram(program);
                    throw new IllegalStateException(message);
                }
                return program;
            } finally {
                glDeleteShader(fragment);
            }
        } finally {
            glDeleteShader(vertex);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String message = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(message);
        }
        return shader;
    }

    void begin(Matrix4f matrix, FloatBuffer buffer) {
        glUseProgram(program);
        glUniformMatrix4fv(viewProjection, false, matrix.get(buffer));
    }

    void setTreeShadow(float directionX, float directionZ, float contact) {
        glUniform2f(treeFallDirection, directionX, directionZ);
        glUniform1f(treeGroundContact, contact);
    }

    void setShadowOrigin(float x, float z) {
        glUniform2f(shadowOrigin, x, z);
    }

    void setWind(float time, float strength, float playerX, float playerZ) {
        glUniform1f(windTime, time);
        glUniform1f(windStrength, strength);
        glUniform2f(windDirection, Wind.X, Wind.Z);
        glUniform2f(playerPosition, playerX, playerZ);
    }

    void setFireLights(float[] lights, int count) {
        glUniform4fv(fireLights, lights);
        glUniform1i(fireLightCount, count);
    }

    void clearFireLights() {
        glUniform1i(fireLightCount, 0);
    }

    void setBlastMarks(float[] marks, int count) {
        if (count > 0) glUniform4fv(blastMarks, marks);
        glUniform1i(blastMarkCount, count);
    }

    void draw(ObjModel mesh, Matrix4f matrix, boolean isDisplay, FloatBuffer buffer) {
        draw(mesh, matrix, isDisplay, 0.0f, false, 0, 1.0f, 1.0f, buffer);
    }

    void drawClay(ObjModel mesh, Matrix4f matrix, float[] skeleton, int tint, FloatBuffer buffer) {
        glUniform1i(skinned, 1);
        glUniformMatrix4fv(bones, false, skeleton);
        glUniform3f(clayTint, ((tint >> 16) & 255) / 255f, ((tint >> 8) & 255) / 255f, (tint & 255) / 255f);
        draw(mesh, matrix, false, 0, false, 0, 1, 1, buffer);
        glUniform1i(skinned, 0);
    }

    void drawCharred(ObjModel mesh, Matrix4f matrix, float burn, FloatBuffer buffer) {
        draw(mesh, matrix, false, burn, false, 0, 1.0f, 1.0f, buffer);
    }

    void drawEmissive(ObjModel mesh, Matrix4f matrix, FloatBuffer buffer) {
        draw(mesh, matrix, false, 0.0f, true, 0, 1.0f, 1.0f, buffer);
    }

    void drawGrass(ObjModel mesh, Matrix4f matrix, float height, float burn, FloatBuffer buffer) {
        draw(mesh, matrix, false, burn, false, 1, height, 1.0f, buffer);
    }

    void drawTree(ObjModel mesh, Matrix4f matrix, float burn, FloatBuffer buffer) {
        draw(mesh, matrix, false, burn, false, 2, 1.0f, 1.0f, buffer);
    }

    void drawBow(ObjModel mesh, Matrix4f matrix, float draw, FloatBuffer buffer) {
        draw(mesh, matrix, false, 0.0f, false, 3, draw, 1.0f, buffer);
    }

    void drawTranslucent(ObjModel mesh, Matrix4f matrix, float alpha, boolean emitsLight, FloatBuffer buffer) {
        draw(mesh, matrix, false, 0.0f, emitsLight, 0, 1.0f, alpha, buffer);
    }

    private void draw(ObjModel mesh, Matrix4f matrix, boolean isDisplay, float burn, boolean emitsLight,
                      int plants, float height, float alpha, FloatBuffer buffer) {
        glUniformMatrix4fv(model, false, matrix.get(buffer));
        glUniform1i(displaySurface, isDisplay ? 1 : 0);
        glUniform1f(charAmount, burn);
        glUniform1i(emissive, emitsLight ? 1 : 0);
        glUniform1i(vegetation, plants);
        glUniform1f(plantHeight, height);
        glUniform1f(opacity, alpha);
        mesh.draw();
    }

    @Override
    public void close() {
        glDeleteProgram(program);
    }
}
