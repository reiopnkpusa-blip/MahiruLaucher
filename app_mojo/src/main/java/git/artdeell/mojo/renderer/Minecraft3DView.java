package git.artdeell.mojo.renderer;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Minecraft3DView extends GLSurfaceView
        implements GLSurfaceView.Renderer {

    private static final String TAG = "Minecraft3DView";

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];
    private final float[] finalMatrix = new float[16];

    private int program;
    private int positionHandle;
    private int matrixHandle;
    private int colorHandle;

    private FloatBuffer cubeBuffer;
    private float rotation = 0f;

    private final String vertexShader =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            "gl_Position = uMVPMatrix * vPosition;" +
            "}";

    private final String fragmentShader =
            "precision mediump float;" +
            "uniform vec4 uColor;" +
            "void main() {" +
            "gl_FragColor = uColor;" +
            "}";

    public Minecraft3DView(Context context) {
        super(context);

        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    @Override
    public void onSurfaceCreated(
            javax.microedition.khronos.opengles.GL10 gl,
            javax.microedition.khronos.egl.EGLConfig config) {

        GLES20.glClearColor(0.12f, 0.07f, 0.13f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        int vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader);

        if (vs == 0 || fs == 0) {
            program = 0;
            return;
        }

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(
                program,
                GLES20.GL_LINK_STATUS,
                linkStatus,
                0
        );

        if (linkStatus[0] == 0) {
            Log.e(TAG, "Shader program link failed: "
                    + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
            return;
        }

        positionHandle =
                GLES20.glGetAttribLocation(program, "vPosition");
        matrixHandle =
                GLES20.glGetUniformLocation(program, "uMVPMatrix");
        colorHandle =
                GLES20.glGetUniformLocation(program, "uColor");

        float[] cube = {
                -0.5f, -0.5f,  0.5f,
                 0.5f, -0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,

                -0.5f, -0.5f, -0.5f,
                -0.5f,  0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f,

                -0.5f,  0.5f, -0.5f,
                -0.5f,  0.5f,  0.5f,
                 0.5f,  0.5f,  0.5f,
                 0.5f,  0.5f, -0.5f,

                -0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f, -0.5f,
                 0.5f, -0.5f,  0.5f,
                -0.5f, -0.5f,  0.5f,

                 0.5f, -0.5f, -0.5f,
                 0.5f,  0.5f, -0.5f,
                 0.5f,  0.5f,  0.5f,
                 0.5f, -0.5f,  0.5f,

                -0.5f, -0.5f, -0.5f,
                -0.5f, -0.5f,  0.5f,
                -0.5f,  0.5f,  0.5f,
                -0.5f,  0.5f, -0.5f
        };

        ByteBuffer bb = ByteBuffer.allocateDirect(cube.length * 4);
        bb.order(ByteOrder.nativeOrder());

        cubeBuffer = bb.asFloatBuffer();
        cubeBuffer.put(cube);
        cubeBuffer.position(0);
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(
                shader,
                GLES20.GL_COMPILE_STATUS,
                status,
                0
        );

        if (status[0] == 0) {
            Log.e(TAG, "Shader compile failed: "
                    + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    @Override
    public void onSurfaceChanged(
            javax.microedition.khronos.opengles.GL10 gl,
            int width,
            int height) {

        if (height <= 0 || width <= 0) {
            return;
        }

        GLES20.glViewport(0, 0, width, height);

        float ratio = (float) width / (float) height;

        Matrix.frustumM(
                projection, 0,
                -ratio, ratio,
                -1, 1,
                2, 20
        );

        Matrix.setLookAtM(
                view, 0,
                0, 0, 7,
                0, 0, 0,
                0, 1, 0
        );
    }

    @Override
    public void onDrawFrame(
            javax.microedition.khronos.opengles.GL10 gl) {

        GLES20.glClear(
                GLES20.GL_COLOR_BUFFER_BIT |
                GLES20.GL_DEPTH_BUFFER_BIT
        );

        if (program == 0 || cubeBuffer == null) {
            return;
        }

        GLES20.glUseProgram(program);

        Matrix.setIdentityM(model, 0);
        Matrix.rotateM(model, 0, rotation, 0, 1, 0);

        drawPart(0f, 0.65f, 0f,
                0.48f, 0.48f, 0.48f,
                0.95f, 0.72f, 0.58f);

        drawPart(0f, -0.05f, 0f,
                0.55f, 0.72f, 0.30f,
                1f, 0.35f, 0.62f);

        drawPart(-0.40f, -0.05f, 0f,
                0.22f, 0.72f, 0.25f,
                0.95f, 0.72f, 0.58f);

        drawPart(0.40f, -0.05f, 0f,
                0.22f, 0.72f, 0.25f,
                0.95f, 0.72f, 0.58f);

        drawPart(-0.16f, -0.72f, 0f,
                0.25f, 0.62f, 0.28f,
                0.35f, 0.55f, 0.95f);

        drawPart(0.16f, -0.72f, 0f,
                0.25f, 0.62f, 0.28f,
                0.35f, 0.55f, 0.95f);

        rotation += 0.35f;
    }

    private void drawPart(
            float x, float y, float z,
            float sx, float sy, float sz,
            float r, float g, float b) {

        float[] part = new float[16];
        float[] temp = new float[16];

        Matrix.setIdentityM(part, 0);
        Matrix.translateM(part, 0, x, y, z);
        Matrix.scaleM(part, 0, sx, sy, sz);

        Matrix.multiplyMM(temp, 0, model, 0, part, 0);
        Matrix.multiplyMM(mvp, 0, view, 0, temp, 0);
        Matrix.multiplyMM(finalMatrix, 0, projection, 0, mvp, 0);

        GLES20.glUniformMatrix4fv(
                matrixHandle, 1, false, finalMatrix, 0
        );

        GLES20.glUniform4f(colorHandle, r, g, b, 1f);

        GLES20.glEnableVertexAttribArray(positionHandle);

        cubeBuffer.position(0);

        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                cubeBuffer
        );

        for (int i = 0; i < 6; i++) {
            GLES20.glDrawArrays(
                    GLES20.GL_TRIANGLE_FAN,
                    i * 4,
                    4
            );
        }

        GLES20.glDisableVertexAttribArray(positionHandle);
    }
}
