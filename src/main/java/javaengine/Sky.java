package javaengine;

import org.joml.Matrix4f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

final class Sky implements AutoCloseable {
    private static final String VERTEX = """
            #version 330 core
            out vec2 screen;
            void main() {
                screen = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);
                gl_Position = vec4(screen, 0.9999, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 330 core
            in vec2 screen;
            uniform mat4 inverseView;
            uniform vec2 lens;
            uniform float seconds;
            out vec4 fragment;

            float hash(vec2 p) {
                p = fract(p * vec2(0.1037, 0.1191));
                p += dot(p, p.yx + 29.72);
                return fract((p.x + p.y) * p.x * 19.19);
            }

            float noise(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(cell), hash(cell + vec2(1, 0)), f.x),
                           mix(hash(cell + vec2(0, 1)), hash(cell + 1.0), f.x), f.y);
            }

            void main() {
                vec3 ray = normalize(mat3(inverseView) * vec3(screen * lens, -1.0));
                vec3 sky = mix(vec3(0.55, 0.69, 0.70), vec3(0.25, 0.47, 0.62),
                               smoothstep(0.0, 0.86, max(0.0, ray.y)));
                if (ray.y > 0.025) {
                    vec2 drift = vec2(0.866, 0.5) * seconds * 0.025;
                    vec2 p = ray.xz / (ray.y + 0.38) * 2.8 - drift + vec2(5.0, 2.9);
                    float shape = noise(p) * 0.57 + noise(p * 2.15 + 7.4) * 0.29
                                  + noise(p * 4.50 + 21.7) * 0.14;
                    float thickness = smoothstep(0.47, 0.66, shape);
                    float cloud = thickness * smoothstep(0.025, 0.22, ray.y);
                    vec3 cloudColor = mix(vec3(0.66, 0.75, 0.76), vec3(0.91, 0.89, 0.80), thickness);
                    sky = mix(sky, cloudColor, cloud * 0.72);
                }
                fragment = vec4(sky, 1.0);
            }
            """;

    static final float FOV = (float) Math.toRadians(57.0);
    private final int program = SceneShader.link(VERTEX, FRAGMENT);
    private final int vao = glGenVertexArrays();
    private final int inverseLocation = glGetUniformLocation(program, "inverseView");
    private final int lens = glGetUniformLocation(program, "lens");
    private final int seconds = glGetUniformLocation(program, "seconds");
    private final Matrix4f inverse = new Matrix4f();

    void draw(Matrix4f view, float aspect, float time, FloatBuffer buffer) {
        draw(view, aspect, time, FOV, buffer);
    }

    void draw(Matrix4f view, float aspect, float time, float fov, FloatBuffer buffer) {
        float scale = (float) Math.tan(fov * 0.5f);
        glUseProgram(program);
        glUniformMatrix4fv(inverseLocation, false, view.invertAffine(inverse).get(buffer));
        glUniform2f(lens, aspect * scale, scale);
        glUniform1f(seconds, time);
        glBindVertexArray(vao);
        glDepthMask(false);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glDepthMask(true);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
