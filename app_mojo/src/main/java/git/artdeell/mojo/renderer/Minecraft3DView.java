package git.artdeell.mojo.renderer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import androidx.preference.PreferenceManager;

import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Minecraft3DView extends GLSurfaceView
        implements GLSurfaceView.Renderer {

    private static final String TAG = "Minecraft3DView";
    private static final float UNIT = 0.075f;

    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] model = new float[16];
    private final float[] temp = new float[16];
    private final float[] mvp = new float[16];

    private int program;
    private int positionHandle;
    private int uvHandle;
    private int matrixHandle;
    private int textureHandle;
    private int textureId;

    private float rotation;
    private float lastTouchX;
    private float armAnimation;
    private volatile boolean slimArms;
    private volatile Bitmap skinBitmap;
    private volatile boolean skinLoading;

    private final List<Box> boxes = new ArrayList<>();

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec3 aPosition;" +
            "attribute vec2 aUV;" +
            "varying vec2 vUV;" +
            "void main() {" +
            " vUV = aUV;" +
            " gl_Position = uMVPMatrix * vec4(aPosition, 1.0);" +
            "}";

    private static final String FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform sampler2D uTexture;" +
            "varying vec2 vUV;" +
            "void main() {" +
            " vec4 texColor = texture2D(uTexture, vUV);" +
            " if (texColor.a < 0.1) discard;" +
            " gl_FragColor = texColor;" +
            "}";

    public Minecraft3DView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        slimArms = "slim".equals(SkinManager.getLocalSkinVariant(context));
        loadSelectedSkin();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .registerOnSharedPreferenceChangeListener(modelPreferenceListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .unregisterOnSharedPreferenceChangeListener(modelPreferenceListener);
        super.onDetachedFromWindow();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener modelPreferenceListener =
            (preferences, key) -> {
                if ("local_skin_model".equals(key)) {
                    queueEvent(() -> {
                        slimArms = "slim".equals(SkinManager.getLocalSkinVariant(
                                getContext().getApplicationContext()));
                        buildPlayerModel();
                    });
                    requestRender();
                }
            };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                return true;
            case MotionEvent.ACTION_MOVE:
                float currentX = event.getX();
                float delta = currentX - lastTouchX;
                lastTouchX = currentX;
                rotation += delta * 0.65f;
                requestRender();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void loadSelectedSkin() {
        if (skinLoading || skinBitmap != null) return;
        skinLoading = true;

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                Account account = Accounts.getCurrent();

                // A local skin takes priority for every account type.
                java.io.File localSkin = SkinManager.getLocalSkinFile(
                        getContext().getApplicationContext());

                if (localSkin.isFile()) {
                    Bitmap local = BitmapFactory.decodeFile(
                            localSkin.getAbsolutePath());
                    if (local != null && local.getWidth() == 64
                            && local.getHeight() == 64) {
                        slimArms = "slim".equals(SkinManager.getLocalSkinVariant(
                                getContext().getApplicationContext()));
                        skinBitmap = local;
                        return;
                    }
                    if (local != null) local.recycle();
                }

                // Only official Microsoft/Minecraft accounts are supported here.
                if (account == null || account.isLocal()
                        || account.authType !=
                        git.artdeell.mojo.authenticator.AuthType.MICROSOFT
                        || account.accessToken == null
                        || "0".equals(account.accessToken)) {
                    Log.i(TAG, "No local or Microsoft skin available");
                    return;
                }

                connection = (HttpURLConnection) new URL(
                        "https://api.minecraftservices.com/minecraft/profile"
                ).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                        "Authorization", "Bearer " + account.accessToken);

                if (connection.getResponseCode() != 200) {
                    Log.w(TAG, "Minecraft profile request failed");
                    return;
                }

                org.json.JSONObject profile;
                try (InputStream stream = connection.getInputStream()) {
                    byte[] bytes = new byte[0];
                    java.io.ByteArrayOutputStream output =
                            new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = stream.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    profile = new org.json.JSONObject(
                            output.toString("UTF-8"));
                }

                org.json.JSONArray skins = profile.optJSONArray("skins");
                if (skins == null || skins.length() == 0) return;

                org.json.JSONObject selectedSkin = null;
                org.json.JSONObject fallbackSkin = null;
                for (int i = 0; i < skins.length(); i++) {
                    org.json.JSONObject item = skins.optJSONObject(i);
                    if (item == null || item.optString("url", "").isEmpty()) continue;
                    if (fallbackSkin == null) fallbackSkin = item;
                    if ("ACTIVE".equalsIgnoreCase(item.optString("state", ""))) {
                        selectedSkin = item;
                        break;
                    }
                }
                if (selectedSkin == null) selectedSkin = fallbackSkin;
                if (selectedSkin == null) return;
                String skinUrl = selectedSkin.optString("url", "");
                String variant = selectedSkin.optString("variant", "CLASSIC");
                slimArms = "SLIM".equalsIgnoreCase(variant);
                queueEvent(this::buildPlayerModel);
                requestRender();

                connection.disconnect();
                connection = (HttpURLConnection) new URL(skinUrl).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);

                if (connection.getResponseCode() != 200) return;

                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null && bitmap.getWidth() == 64
                            && bitmap.getHeight() == 64) {
                        skinBitmap = bitmap;
                    } else if (bitmap != null) {
                        bitmap.recycle();
                        Log.w(TAG, "Unsupported Minecraft skin dimensions");
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not load Minecraft skin");
            } finally {
                if (connection != null) connection.disconnect();
                skinLoading = false;
            }
        }, "Mahiru-Skin-Loader").start();
    }

    public void reloadSkin() {
        queueEvent(() -> {
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                textureId = 0;
            }
            Bitmap oldBitmap = skinBitmap;
            skinBitmap = null;
            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                oldBitmap.recycle();
            }
            slimArms = "slim".equals(SkinManager.getLocalSkinVariant(
                    getContext().getApplicationContext()));
            buildPlayerModel();
            skinLoading = false;
            post(this::loadSelectedSkin);
        });
        requestRender();
    }

    @Override
    public void onSurfaceCreated(
            javax.microedition.khronos.opengles.GL10 gl,
            javax.microedition.khronos.egl.EGLConfig config) {

        GLES20.glClearColor(0.09f, 0.055f, 0.10f, 1f);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        int vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vs == 0 || fs == 0) return;

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);

        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, "Shader link failed: "
                    + GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
            return;
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        uvHandle = GLES20.glGetAttribLocation(program, "aUV");
        matrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        textureHandle = GLES20.glGetUniformLocation(program, "uTexture");

        buildPlayerModel();

        if (skinBitmap == null) loadSelectedSkin();
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);

        if (status[0] == 0) {
            Log.e(TAG, "Shader error: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private void buildPlayerModel() {
        boxes.clear();

        float armWidth = slimArms ? 3f : 4f;
        float armCenter = slimArms ? 5.5f : 6f;

        // Base and outer head layers. Outer geometry is larger, UV size is unchanged.
        addBox(0, 12, 0, 8, 8, 8, 0, 0);
        addBox(0, 12, 0, 8.5f, 8.5f, 8.5f, 32, 0, 8, 8, 8, false);

        // Torso and jacket.
        addBox(0, 2, 0, 8, 12, 4, 16, 16);
        addBox(0, 2, 0, 8.5f, 12.5f, 4.5f, 16, 32, 8, 12, 4, false);

        // Right arm: 4 px Classic or 3 px Slim. The two arm layers swing together.
        addBox(-armCenter, 2, 0, armWidth, 12, 4,
                40, 16, armWidth, 12, 4, true);
        addBox(-armCenter, 2, 0, armWidth + 0.5f, 12.5f, 4.5f,
                40, 32, armWidth, 12, 4, true);

        // Left arm.
        addBox(armCenter, 2, 0, armWidth, 12, 4,
                32, 48, armWidth, 12, 4, false);
        addBox(armCenter, 2, 0, armWidth + 0.5f, 12.5f, 4.5f,
                48, 48, armWidth, 12, 4, false);

        // Legs and outer trouser layers.
        addBox(-2, -10, 0, 4, 12, 4, 0, 16);
        addBox(-2, -10, 0, 4.5f, 12.5f, 4.5f,
                0, 32, 4, 12, 4, false);
        addBox(2, -10, 0, 4, 12, 4, 16, 48);
        addBox(2, -10, 0, 4.5f, 12.5f, 4.5f,
                0, 48, 4, 12, 4, false);
    }

    private void addBox(float x, float y, float z,
                        float w, float h, float d, int u, int v) {
        addBox(x, y, z, w, h, d, u, v, w, h, d, false);
    }

    private void addBox(float x, float y, float z,
                        float w, float h, float d,
                        int u, int v, float uvW, float uvH, float uvD,
                        boolean animatedArm) {
        boxes.add(new Box(x, y, z, w, h, d,
                u, v, uvW, uvH, uvD, animatedArm));
    }

    private static float texU(float pixel) {
        return pixel / 64f;
    }

    private static float texV(float pixel) {
        return 1f - pixel / 64f;
    }

    private static class Box {
        final float x, y, z;
        final FloatBuffer buffer;

        final boolean animatedArm;

        Box(float x, float y, float z,
            float w, float h, float d, int u, int v,
            float uvW, float uvH, float uvD, boolean animatedArm) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.animatedArm = animatedArm;

            float[] data = new float[36 * 5];
            int[] index = {0};

            float x0 = -w / 2f, x1 = w / 2f;
            float y0 = -h / 2f, y1 = h / 2f;
            float z0 = -d / 2f, z1 = d / 2f;

            // Standard Minecraft skin atlas cuboid layout
            // UV offsets must use the original skin dimensions (uvD), not the
            // expanded outer-layer geometry depth (d). Using d here shifts every
            // face of the hat/jacket/sleeves/trousers into neighboring atlas pixels.
            face(data, index,
                    new float[]{x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1},
                    u+uvD, v+uvD, uvW, uvH);

            face(data, index,
                    new float[]{x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0},
                    u+uvD+uvW+uvD, v+uvD, uvW, uvH);

            face(data, index,
                    new float[]{x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1},
                    u, v+uvD, uvD, uvH);

            face(data, index,
                    new float[]{x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0},
                    u+uvD+uvW, v+uvD, uvD, uvH);

            face(data, index,
                    new float[]{x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0},
                    u+uvD, v, uvW, uvD);

            face(data, index,
                    new float[]{x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1},
                    u+uvD+uvW, v, uvW, uvD);

            ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
            bb.order(ByteOrder.nativeOrder());
            buffer = bb.asFloatBuffer();
            buffer.put(data);
            buffer.position(0);
        }

        private static void face(float[] out, int[] index,
                                 float[] p, float u, float v,
                                 float width, float height) {
            float u0 = texU(u), u1 = texU(u + width);
            float v0 = texV(v), v1 = texV(v + height);

            vertex(out,index,p,0,u0,v0);
            vertex(out,index,p,1,u1,v0);
            vertex(out,index,p,2,u1,v1);
            vertex(out,index,p,0,u0,v0);
            vertex(out,index,p,2,u1,v1);
            vertex(out,index,p,3,u0,v1);
        }

        private static void vertex(float[] out, int[] index,
                                   float[] p, int corner,
                                   float u, float v) {
            int pi = corner * 3;
            int i = index[0];
            out[i++] = p[pi];
            out[i++] = p[pi + 1];
            out[i++] = p[pi + 2];
            out[i++] = u;
            out[i++] = v;
            index[0] = i;
        }
    }

    private void uploadSkinIfNeeded() {
        if (textureId != 0 || skinBitmap == null) return;

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, skinBitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    @Override
    public void onSurfaceChanged(
            javax.microedition.khronos.opengles.GL10 gl,
            int width, int height) {
        if (width <= 0 || height <= 0) return;

        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        Matrix.frustumM(projection, 0,
                -ratio, ratio, -1, 1, 2, 30);

        Matrix.setLookAtM(view, 0,
                0, 0, 6.4f,
                0, 0, 0,
                0, 1, 0);
    }

    @Override
    public void onDrawFrame(
            javax.microedition.khronos.opengles.GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT
                | GLES20.GL_DEPTH_BUFFER_BIT);

        if (program == 0) return;

        uploadSkinIfNeeded();
        if (textureId == 0) return;

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glUniform1i(textureHandle, 0);

        Matrix.setIdentityM(model, 0);
        Matrix.rotateM(model, 0, rotation, 0, 1, 0);

        for (Box box : boxes) {
            Matrix.setIdentityM(temp, 0);
            if (box.animatedArm) {
                float swing = (float) Math.sin(armAnimation) * 18f;
                Matrix.translateM(temp, 0,
                        box.x * UNIT, 8f * UNIT, box.z * UNIT);
                Matrix.rotateM(temp, 0, swing, 1f, 0f, 0f);
                Matrix.translateM(temp, 0, 0f,
                        (box.y - 8f) * UNIT, 0f);
            } else {
                Matrix.translateM(temp, 0,
                        box.x * UNIT, box.y * UNIT, box.z * UNIT);
            }
            Matrix.scaleM(temp, 0, UNIT, UNIT, UNIT);

            float[] part = new float[16];
            Matrix.multiplyMM(part, 0, model, 0, temp, 0);
            Matrix.multiplyMM(mvp, 0, view, 0, part, 0);
            Matrix.multiplyMM(part, 0, projection, 0, mvp, 0);

            GLES20.glUniformMatrix4fv(matrixHandle, 1, false, part, 0);

            box.buffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(uvHandle);

            box.buffer.position(0);
            GLES20.glVertexAttribPointer(positionHandle, 3,
                    GLES20.GL_FLOAT, false, 20, box.buffer);

            box.buffer.position(3);
            GLES20.glVertexAttribPointer(uvHandle, 2,
                    GLES20.GL_FLOAT, false, 20, box.buffer);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
        }

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(uvHandle);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        rotation += 0.28f;
        armAnimation += 0.045f;
    }
}
