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
    private static final String KEY_BLUR = "background_blur";
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
        if (KEY_MODE.equals(key) || KEY_URI.equals(key) || KEY_COLOR.equals(key) || KEY_BLUR.equals(key)) {
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
            new Thread(() -> {
                Bitmap photo = null;
                Uri photoUri = Uri.parse(saved);
                try (InputStream boundsInput = getContext().getContentResolver()
                        .openInputStream(photoUri)) {
                    if (boundsInput != null) {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeStream(boundsInput, null, bounds);
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight);
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        try (InputStream imageInput = getContext().getContentResolver()
                                .openInputStream(photoUri)) {
                            if (imageInput != null) {
                                photo = BitmapFactory.decodeStream(imageInput, null, options);
                            }
                        }
                    }
                } catch (Exception ignored) { }
                final Bitmap result = applyConfiguredBlur(photo);
                post(() -> {
                    if (token != loadToken || backgroundImage == null) {
                        if (result != null && !result.isRecycled()) result.recycle();
                        return;
                    }
                    if (result != null) backgroundImage.setImageBitmap(result);
                    else backgroundImage.setImageDrawable(null);
                });
            }, "Mahiru-Custom-Background").start();
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

            final Bitmap result = applyConfiguredBlur(bitmap);
            post(() -> {
                if (token != loadToken || backgroundImage == null) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                if (result != null) backgroundImage.setImageBitmap(result);
            });
        }, "Mahiru-Default-Background").start();
    }

    private Bitmap applyConfiguredBlur(Bitmap source) {
        if (source == null) return null;
        int percent = preferences.getInt(KEY_BLUR, 0);
        if (percent <= 0) return source;
        int maxSide = 512;
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, Math.round(source.getWidth() * scale));
        int height = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap working = Bitmap.createScaledBitmap(source, width, height, true);
        if (working != source && !source.isRecycled()) source.recycle();
        int radius = Math.max(1, Math.round(percent * 0.16f));
        int[] pixels = new int[width * height];
        int[] temp = new int[width * height];
        working.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long a=0, r=0, g=0, b=0; int n=0;
                for (int k=Math.max(0,x-radius); k<=Math.min(width-1,x+radius); k++) {
                    int color=pixels[y*width+k]; a+=(color>>>24)&255;
                    r+=(color>>>16)&255; g+=(color>>>8)&255; b+=color&255; n++;
                }
                temp[y*width+x]=((int)(a/n)<<24)|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long a=0, r=0, g=0, b=0; int n=0;
                for (int k=Math.max(0,y-radius); k<=Math.min(height-1,y+radius); k++) {
                    int color=temp[k*width+x]; a+=(color>>>24)&255;
                    r+=(color>>>16)&255; g+=(color>>>8)&255; b+=color&255; n++;
                }
                pixels[y*width+x]=((int)(a/n)<<24)|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);
            }
        }
        Bitmap blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        blurred.setPixels(pixels, 0, width, 0, 0, width, height);
        if (working != blurred && !working.isRecycled()) working.recycle();
        return blurred;
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
