package git.artdeell.mojo.prefs.screens;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import git.artdeell.mojo.prefs.screens.LauncherPreferenceFragment;

public class MahiruAppearanceFragment extends LauncherPreferenceFragment {
    private static final String PREFS = "mahiru_appearance";
    private static final String KEY_MODE = "background_mode";
    private static final String KEY_URI = "background_uri";
    private static final String KEY_COLOR = "background_color";
    private static final String KEY_BLUR = "background_blur";

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || !isAdded()) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                openCropEditor(uri);
            });

    private Preference photoPreference;

    private void openCropEditor(Uri uri) {
        Bitmap preview;
        try (java.io.InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
            if (input == null) throw new java.io.IOException("Unable to open image");
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(input, null, bounds);
            int sample = 1;
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (java.io.InputStream imageInput = requireContext().getContentResolver().openInputStream(uri)) {
                preview = BitmapFactory.decodeStream(imageInput, null, options);
            }
        } catch (Exception error) {
            new AlertDialog.Builder(requireContext()).setMessage("Could not load this image.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (preview == null) return;

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        TextView hint = new TextView(requireContext());
        hint.setText("Drag to position the photo. Use the slider to zoom.");
        content.addView(hint);

        ImageView previewView = new ImageView(requireContext());
        previewView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewView.setImageBitmap(preview);
        FrameLayout cropFrame = new FrameLayout(requireContext());
        cropFrame.setClipChildren(true);
        cropFrame.setClipToPadding(true);
        cropFrame.addView(previewView, new FrameLayout.LayoutParams(-1, -1));
        content.addView(cropFrame, new LinearLayout.LayoutParams(-1,
                (int) (250 * getResources().getDisplayMetrics().density)));

        final float[] drag = new float[]{0f, 0f, 0f, 0f};
        previewView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                drag[0] = event.getX(); drag[1] = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                drag[2] += event.getX() - drag[0];
                drag[3] += event.getY() - drag[1];
                drag[0] = event.getX(); drag[1] = event.getY();
                float maxX = previewView.getWidth() * (previewView.getScaleX() - 1f) / 2f;
                float maxY = previewView.getHeight() * (previewView.getScaleY() - 1f) / 2f;
                drag[2] = Math.max(-maxX, Math.min(maxX, drag[2]));
                drag[3] = Math.max(-maxY, Math.min(maxY, drag[3]));
                view.setTranslationX(drag[2]);
                view.setTranslationY(drag[3]);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_UP;
        });

        TextView zoomLabel = new TextView(requireContext());
        zoomLabel.setText("Zoom");
        content.addView(zoomLabel);
        SeekBar zoom = new SeekBar(requireContext());
        zoom.setMax(300);
        content.addView(zoom);
        zoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                float factor = 1f + value / 100f;
                previewView.setScaleX(factor);
                previewView.setScaleY(factor);
                float maxX = previewView.getWidth() * (factor - 1f) / 2f;
                float maxY = previewView.getHeight() * (factor - 1f) / 2f;
                drag[2] = Math.max(-maxX, Math.min(maxX, drag[2]));
                drag[3] = Math.max(-maxY, Math.min(maxY, drag[3]));
                previewView.setTranslationX(drag[2]);
                previewView.setTranslationY(drag[3]);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Launcher background preview")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Use this crop", (d, w) -> {
                    float panX = previewView.getTranslationX() / Math.max(1f, previewView.getWidth());
                    float panY = previewView.getTranslationY() / Math.max(1f, previewView.getHeight());
                    requireContext().getSharedPreferences(PREFS, 0).edit()
                            .putString(KEY_MODE, "photo")
                            .putString(KEY_URI, uri.toString())
                            .putInt("background_crop_zoom", zoom.getProgress())
                            .putFloat("background_crop_x", Math.max(-0.5f, Math.min(0.5f, panX)))
                            .putFloat("background_crop_y", Math.max(-0.5f, Math.min(0.5f, panY)))
                            .apply();
                    updatePhotoSummary();
                }).create();
        dialog.setOnDismissListener(d -> {
            if (!preview.isRecycled()) preview.recycle();
        });
        dialog.show();
    }


    @Override
    public void onCreatePreferences(android.os.Bundle state, String rootKey) {
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle("MAHIRU BACKGROUND");
        screen.addPreference(category);

        photoPreference = new Preference(requireContext());
        photoPreference.setTitle("Choose background photo");
        photoPreference.setSummary("Use an image from your device");
        photoPreference.setOnPreferenceClickListener(preference -> {
            photoPicker.launch(new String[]{"image/*"});
            return true;
        });
        category.addPreference(photoPreference);

        Preference color = new Preference(requireContext());
        color.setTitle("Background color");
        color.setSummary("Choose a solid color or enter a HEX color");
        color.setOnPreferenceClickListener(preference -> {
            showColorChoices();
            return true;
        });
        category.addPreference(color);

        Preference blur = new Preference(requireContext());
        blur.setTitle("Background image blur");
        blur.setSummary("Adjust blur from 0% (sharp) to 100% (maximum)");
        blur.setOnPreferenceClickListener(preference -> {
            showBlurDialog();
            return true;
        });
        category.addPreference(blur);

        Preference reset = new Preference(requireContext());
        reset.setTitle("Reset default anime background");
        reset.setSummary("Restore the default Sakura anime background");
        reset.setOnPreferenceClickListener(preference -> {
            requireContext().getSharedPreferences(PREFS, 0).edit()
                    .putString(KEY_MODE, "default")
                    .remove(KEY_URI)
                    .remove(KEY_COLOR)
                    .remove("background_crop_zoom")
                    .remove("background_crop_x")
                    .remove("background_crop_y")
                    .apply();
            updatePhotoSummary();
            return true;
        });
        category.addPreference(reset);
        setPreferenceScreen(screen);
        updatePhotoSummary();
    }

    private void showBlurDialog() {
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences(PREFS, 0);
        int current = prefs.getInt(KEY_BLUR, 0);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        TextView value = new TextView(requireContext());
        value.setText("Blur: " + current + "%");
        SeekBar slider = new SeekBar(requireContext());
        slider.setMax(100);
        slider.setProgress(current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                value.setText("Blur: " + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        content.addView(value);
        content.addView(slider);
        new AlertDialog.Builder(requireContext())
                .setTitle("Background blur")
                .setView(content)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset 0%", (dialog, which) ->
                        prefs.edit().putInt(KEY_BLUR, 0).apply())
                .setPositiveButton("Apply", (dialog, which) ->
                        prefs.edit().putInt(KEY_BLUR, slider.getProgress()).apply())
                .show();
    }

    private void updatePhotoSummary() {
        if (photoPreference == null || !isAdded()) return;
        String mode = requireContext().getSharedPreferences(PREFS, 0)
                .getString(KEY_MODE, "default");
        if ("photo".equals(mode)) photoPreference.setSummary("Custom photo selected");
        else if ("color".equals(mode)) photoPreference.setSummary("Solid color selected");
        else photoPreference.setSummary("Default Sakura anime background");
    }

    private void showColorChoices() {
        final String[] names = {"Pink", "Rose", "Lavender", "Purple", "Blue", "Mint", "Charcoal", "Custom HEX"};
        final int[] colors = {
                Color.rgb(255, 105, 180), Color.rgb(220, 90, 130),
                Color.rgb(185, 150, 230), Color.rgb(100, 65, 130),
                Color.rgb(65, 120, 180), Color.rgb(90, 160, 145),
                Color.rgb(28, 23, 34)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose background color")
                .setItems(names, (dialog, which) -> {
                    if (which == names.length - 1) showCustomHexDialog();
                    else saveColor(colors[which]);
                }).setNegativeButton("Cancel", null).show();
    }

    private void showCustomHexDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("#FF5C9A");
        new AlertDialog.Builder(requireContext())
                .setTitle("Custom HEX color")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    try {
                        saveColor(Color.parseColor(input.getText().toString().trim()));
                    } catch (IllegalArgumentException error) {
                        new AlertDialog.Builder(requireContext())
                                .setMessage("Invalid color. Example: #FF5C9A")
                                .setPositiveButton("OK", null).show();
                    }
                }).show();
    }

    private void saveColor(int color) {
        requireContext().getSharedPreferences(PREFS, 0).edit()
                .putString(KEY_MODE, "color")
                .putInt(KEY_COLOR, color)
                .remove(KEY_URI)
                .remove("background_crop_zoom")
                .remove("background_crop_x")
                .remove("background_crop_y")
                .apply();
        updatePhotoSummary();
    }
}
