package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.prefs.LauncherPreferences;
import git.artdeell.mojo.renderer.Minecraft3DView;
import git.artdeell.mojo.renderer.SkinManager;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private Minecraft3DView mLobby3DView;
    private String mPendingSkinVariant = "classic";

    private final ActivityResultLauncher<String> mSkinPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.GetContent(),
                    uri -> {
                        if (uri == null || !isAdded()) return;
                        try {
                            SkinManager.saveLocalSkin(requireContext(), uri, mPendingSkinVariant);
                            if (mLobby3DView != null) mLobby3DView.reloadSkin();
                            Toast.makeText(requireContext(),
                                    "Local skin imported successfully",
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(requireContext(),
                                    "Skin import failed: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    });


    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(
                    new OpenDocumentWithExtension("jar"),
                    (data) -> {
                        if (data != null) {
                            Tools.launchModInstaller(requireContext(), data);
                        }
                    }
            );

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton =
                view.findViewById(R.id.edit_profile_button);

        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        styleMahiruButton(mNewsButton, false);
        styleMahiruButton(mDiscordButton, false);
        styleMahiruButton(mCustomControlButton, false);
        styleMahiruButton(mInstallJarButton, false);
        styleMahiruButton(mShareLogsButton, false);
        styleMahiruButton(mOpenDirectoryButton, false);
        styleMahiruButton(mPlayButton, true);

        FrameLayout lobbyContainer =
                view.findViewById(R.id.lobby_3d_container);

        if (lobbyContainer != null) {
            lobbyContainer.setBackgroundColor(Color.TRANSPARENT);
            lobbyContainer.setClipToPadding(false);
            mLobby3DView = new Minecraft3DView(requireContext());
            mLobby3DView.setBackgroundColor(Color.TRANSPARENT);

            lobbyContainer.addView(
                    mLobby3DView,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                    )
            );
        }

        mNewsButton.setOnClickListener(
                v -> Tools.openURL(requireActivity(), Tools.URL_HOME)
        );

        mDiscordButton.setOnClickListener(
                v -> Tools.openURL(
                        requireActivity(),
                        getString(R.string.social_media_invite)
                )
        );

        mCustomControlButton.setOnClickListener(
                v -> startActivity(
                        new Intent(requireContext(), CustomControlsActivity.class)
                )
        );

        mInstallJarButton.setOnClickListener(
                v -> runInstallerWithConfirmation()
        );

        mEditProfileButton.setOnClickListener(
                v -> mVersionSpinner.openProfileEditor(requireActivity())
        );

        mPlayButton.setOnClickListener(
                v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true)
        );

        mShareLogsButton.setOnClickListener(
                v -> shareLog(requireContext())
        );

        mOpenDirectoryButton.setOnClickListener(
                v -> openGameDirectory(v.getContext())
        );


        View moreButton = view.findViewById(R.id.more_button);
        moreButton.setOnClickListener(v -> {
            String[] items = {
                    "Guide", "Discord", "Controller",
                    "Extract JAR", "Share Logs", "Game Files",
                    "Skin Manager"
            };
            new AlertDialog.Builder(requireContext())
                    .setTitle("Mahiru Launcher")
                    .setItems(items, (dialog, which) -> {
                        switch (which) {
                            case 0: mNewsButton.performClick(); break;
                            case 1: mDiscordButton.performClick(); break;
                            case 2: mCustomControlButton.performClick(); break;
                            case 3: mInstallJarButton.performClick(); break;
                            case 4: mShareLogsButton.performClick(); break;
                            case 5: mOpenDirectoryButton.performClick(); break;
                            case 6: showSkinManager(); break;
                        }
                    })
                    .show();
        });

        mNewsButton.setOnLongClickListener(v -> {
            Tools.swapFragment(
                    requireActivity(),
                    GamepadMapperFragment.class,
                    GamepadMapperFragment.TAG,
                    null
            );
            return true;
        });

        if (LauncherPreferences.PREF_STARTUP_ANIMATION) {
            if (lobbyContainer != null) {
                animateEntrance(lobbyContainer, 0);
            }
            animateEntrance(mNewsButton, 40);
            animateEntrance(mDiscordButton, 80);
            animateEntrance(mCustomControlButton, 120);
            animateEntrance(mInstallJarButton, 160);
            animateEntrance(mShareLogsButton, 200);
            animateEntrance(mOpenDirectoryButton, 240);
            animateEntrance(mVersionSpinner, 180);
            animateEntrance(mEditProfileButton, 220);
            animateEntrance(mPlayButton, 260);
        }
    }

    private void showSkinManager() {
        String[] choices = {
                "Import Local PNG Skin",
                "Upload Local Skin to Premium Account",
                "Refresh 3D Preview"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Skin Manager")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        final int[] selectedModel = {0};
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Choose Skin Model")
                                .setSingleChoiceItems(
                                        new String[]{"Classic (4-pixel arms)", "Slim / Alex (3-pixel arms)"},
                                        0,
                                        (d, index) -> selectedModel[0] = index)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Choose PNG", (d, index) -> {
                                    mPendingSkinVariant = selectedModel[0] == 0
                                            ? "classic" : "slim";
                                    mSkinPickerLauncher.launch("image/png");
                                })
                                .show();
                    } else if (which == 1) {
                        Account account = Accounts.getCurrent();
                        if (account == null || account.isLocal()
                                || account.authType != AuthType.MICROSOFT) {
                            Toast.makeText(requireContext(),
                                    "Please select a Minecraft Premium account first",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }

                        final int[] selectedModel = {0};
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Choose Skin Model")
                                .setSingleChoiceItems(
                                        new String[]{"Classic", "Slim"},
                                        0,
                                        (d, index) -> selectedModel[0] = index)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Upload", (d, index) -> {
                                    String variant = selectedModel[0] == 0
                                            ? "classic" : "slim";

                                    Context context = requireContext();
                                    new Thread(() -> {
                                        String message;
                                        boolean uploaded = false;
                                        try {
                                            SkinManager.setLocalSkinVariant(context, variant);
                                            SkinManager.uploadLocalSkin(context, account, variant);
                                            uploaded = true;
                                            message = "Skin uploaded successfully";
                                        } catch (Exception e) {
                                            message = "Upload failed: " + e.getMessage();
                                        }

                                        final String result = message;
                                        final boolean refreshPreview = uploaded;
                                        if (isAdded()) {
                                            requireActivity().runOnUiThread(() -> {
                                                if (refreshPreview && mLobby3DView != null) {
                                                    mLobby3DView.reloadSkin();
                                                }
                                                Toast.makeText(requireContext(),
                                                        result,
                                                        Toast.LENGTH_LONG).show();
                                            });
                                        }
                                    }, "Mahiru-Skin-Upload").start();
                                })
                                .show();
                    } else if (which == 2) {
                        if (mLobby3DView != null) mLobby3DView.reloadSkin();
                    }
                })
                .show();
    }

    private void styleMahiruButton(@NonNull Button button, boolean primary) {
        button.setAllCaps(false);
        button.setTextSize(13f);
        button.setMinHeight(Math.round(44 * getResources().getDisplayMetrics().density));
        button.setPadding(
                Math.round(14 * getResources().getDisplayMetrics().density),
                Math.round(8 * getResources().getDisplayMetrics().density),
                Math.round(14 * getResources().getDisplayMetrics().density),
                Math.round(8 * getResources().getDisplayMetrics().density));
        button.setTextColor(Color.WHITE);
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                primary
                        ? new int[]{0xFFFF79AA, 0xFFE83F88}
                        : new int[]{0xCC382638, 0xCC241B2A});
        shape.setCornerRadius(14 * getResources().getDisplayMetrics().density);
        shape.setStroke(
                Math.round(getResources().getDisplayMetrics().density),
                primary ? 0xFFFFB1CE : 0x77FF8AB5);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(0x55FFFFFF), shape, null);
        button.setBackground(ripple);
        button.setElevation(3 * getResources().getDisplayMetrics().density);
    }

    private void animateEntrance(@NonNull View target, long delay) {
        float offset = 14f * getResources().getDisplayMetrics().density;
        target.setAlpha(0f);
        target.setTranslationY(offset);
        target.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(360)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();

        if (instance == null) {
            Toast.makeText(
                    context,
                    R.string.no_instance,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        File gameDirectory = instance.getGameDirectory();

        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(
                    context,
                    R.string.gamedir_open_failed,
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    public void onPause() {
        if (mLobby3DView != null) {
            mLobby3DView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mLobby3DView != null) {
            mLobby3DView.onResume();
        }

        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if (!ProgressLayout.ensureNoOngoingTasks(requireContext())) {
            return;
        }

        mModInstallerLauncher.launch(null);
    }
}
