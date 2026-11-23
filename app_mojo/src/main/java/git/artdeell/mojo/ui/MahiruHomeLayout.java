package git.artdeell.mojo.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Home-screen background that follows Mahiru appearance preferences. */
public class MahiruHomeLayout extends ConstraintLayout
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREFS = "mahiru_appearance";
    private static final String KEY_MODE = "background_mode";
    private static final String KEY_URI = "background_uri";
    private static final String KEY_COLOR = "background_color";
    private static final String MODE_DEFAULT = "default";
    private static final String MODE_PHOTO = "photo";
    private static final String MODE_COLOR = "color";
    private static final String DEFAULT_IMAGE =
            "https://cdn.pixabay.com/photo/2025/09/20/07/10/ai-generated-9844362_1280.png";

    private final SharedPreferences preferences;
    private ImageView backgroundImage;
    private int loadToken;

    public MahiruHomeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        setClipToPadding(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (backgroundImage == null) {
            backgroundImage = new ImageView(getContext());
            backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
            backgroundImage.setAlpha(0.82f);
            backgroundImage.setColorFilter(
                    Color.argb(38, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
            addView(backgroundImage, 0, new ConstraintLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        applyAppearance();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        preferences.registerOnSharedPreferenceChangeListener(this);
        applyAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        loadToken++;
        super.onDetachedFromWindow();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (KEY_MODE.equals(key) || KEY_URI.equals(key) || KEY_COLOR.equals(key)) {
            applyAppearance();
        }
    }

    private void applyAppearance() {
        if (backgroundImage == null) return;
        int token = ++loadToken;
        String mode = preferences.getString(KEY_MODE, MODE_DEFAULT);

        if (MODE_COLOR.equals(mode)) {
            backgroundImage.setImageDrawable(null);
            setBackgroundColor(preferences.getInt(KEY_COLOR, Color.rgb(23, 18, 27)));
            return;
        }

        setBackgroundColor(Color.rgb(23, 18, 27));
        if (MODE_PHOTO.equals(mode)) {
            String saved = preferences.getString(KEY_URI, null);
            if (saved == null) {
                backgroundImage.setImageDrawable(null);
                return;
            }
            try {
                backgroundImage.setImageURI(Uri.parse(saved));
            } catch (Exception e) {
                backgroundImage.setImageDrawable(null);
            }
            return;
        }

        backgroundImage.setImageDrawable(null);
        loadDefaultImage(token);
    }

    private void loadDefaultImage(int token) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            Bitmap bitmap = null;
            try {
                connection = (HttpURLConnection) new URL(DEFAULT_IMAGE).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream input = connection.getInputStream()) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    byte[] bytes = readAll(input);
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                }
            } catch (Exception ignored) {
                // Keep the solid dark-pink fallback if the device is offline.
            } finally {
                if (connection != null) connection.disconnect();
            }

            final Bitmap result = bitmap;
            post(() -> {
                if (token != loadToken || backgroundImage == null) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                if (result != null) backgroundImage.setImageBitmap(result);
            });
        }, "Mahiru-Default-Background").start();
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private static int sampleSize(int width, int height) {
        int sample = 1;
        while (width / sample > 1440 || height / sample > 1440) sample *= 2;
        return Math.max(1, sample);
    }
}
