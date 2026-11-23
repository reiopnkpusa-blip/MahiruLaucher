package git.artdeell.mojo.prefs.screens;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;

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

    private final ActivityResultLauncher<String[]> photoPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || !isAdded()) return;
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) { }
                requireContext().getSharedPreferences(PREFS, 0).edit()
                        .putString(KEY_MODE, "photo")
                        .putString(KEY_URI, uri.toString())
                        .apply();
                updatePhotoSummary();
            });

    private Preference photoPreference;

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

        Preference reset = new Preference(requireContext());
        reset.setTitle("Reset default anime background");
        reset.setSummary("Restore the default Sakura anime background");
        reset.setOnPreferenceClickListener(preference -> {
            requireContext().getSharedPreferences(PREFS, 0).edit()
                    .putString(KEY_MODE, "default")
                    .remove(KEY_URI)
                    .remove(KEY_COLOR)
                    .apply();
            updatePhotoSummary();
            return true;
        });
        category.addPreference(reset);
        setPreferenceScreen(screen);
        updatePhotoSummary();
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
                .apply();
        updatePhotoSummary();
    }
}
