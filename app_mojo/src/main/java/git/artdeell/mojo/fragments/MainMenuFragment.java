package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
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
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private Minecraft3DView mLobby3DView;

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

        FrameLayout lobbyContainer =
                view.findViewById(R.id.lobby_3d_container);

        mLobby3DView = new Minecraft3DView(requireContext());

        lobbyContainer.addView(
                mLobby3DView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                )
        );

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
            animateEntrance(lobbyContainer, 0);
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
