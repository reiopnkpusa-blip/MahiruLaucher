package git.artdeell.mojo;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import git.artdeell.mojo.progresskeeper.ProgressKeeper;
import git.artdeell.mojo.progresskeeper.ProgressListener;

public final class DownloadManagerDialog {

    private static final String[] KEYS = {
            "download_minecraft",
            "unpack_runtime",
            "download_verlist",
            "authenticate",
            "install_modpack",
            "extract_components",
            "extract_single_files",
            "instance_install",
            "data_migration",
            "driver_install"
    };

    private DownloadManagerDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final Map<String, TaskRow> rows = new HashMap<>();
        final List<ProgressListener> listeners = new ArrayList<>();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 18),
                dp(activity, 20), dp(activity, 12));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(23, 18, 27));
        background.setCornerRadius(dp(activity, 18));
        root.setBackground(background);

        TextView title = makeText(activity,
                activity.getString(R.string.download_manager_title),
                19, Color.WHITE, true);
        root.addView(title);

        TextView subtitle = makeText(activity, "", 12,
                Color.rgb(241, 220, 232), false);

        LinearLayout.LayoutParams subtitleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(activity, 5);
        root.addView(subtitle, subtitleParams);

        View divider = new View(activity);
        divider.setBackgroundColor(Color.rgb(73, 48, 68));

        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        dividerParams.topMargin = dp(activity, 14);
        dividerParams.bottomMargin = dp(activity, 12);
        root.addView(divider, dividerParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);

        LinearLayout.LayoutParams scrollParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 300));
        root.addView(scroll, scrollParams);

        LinearLayout taskList = new LinearLayout(activity);
        taskList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(taskList);

        TextView empty = makeText(activity,
                activity.getString(R.string.download_manager_empty),
                14, Color.rgb(190, 174, 190), false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(activity, 35), 0, dp(activity, 35));
        taskList.addView(empty);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setPositiveButton(R.string.download_manager_close, null)
                .create();

        Runnable updateSummary = () -> {
            int count = rows.size();

            if (count == 1) {
                subtitle.setText(R.string.download_manager_active_one);
            } else {
                subtitle.setText(activity.getString(
                        R.string.download_manager_active_many, count));
            }

            empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        };

        dialog.setOnDismissListener(d -> {
            for (int i = 0; i < KEYS.length; i++) {
                ProgressKeeper.removeListener(KEYS[i], listeners.get(i));
            }
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            int screenWidth = activity.getResources()
                    .getDisplayMetrics().widthPixels;
            int width = Math.min(screenWidth - dp(activity, 32), dp(activity, 560));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        for (String key : KEYS) {
            ProgressListener listener = new ProgressListener() {
                @Override
                public void onProgressStarted() {
                    handler.post(() -> {
                        if (!dialog.isShowing()) return;

                        if (!rows.containsKey(key)) {
                            TaskRow row = new TaskRow(activity, titleFor(key));
                            rows.put(key, row);

                            int insertAt = Math.max(0, taskList.getChildCount() - 1);
                            taskList.addView(row.card, insertAt,
                                    new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT));
                        }

                        updateSummary.run();
                    });
                }

                @Override
                public void onProgressUpdated(int progress, int resid, Object... va) {
                    handler.post(() -> {
                        if (!dialog.isShowing()) return;

                        TaskRow row = rows.get(key);
                        if (row == null) {
                            row = new TaskRow(activity, titleFor(key));
                            rows.put(key, row);

                            int insertAt = Math.max(0, taskList.getChildCount() - 1);
                            taskList.addView(row.card, insertAt,
                                    new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT));
                        }

                        row.update(progress, resid, va);
                        updateSummary.run();
                    });
                }

                @Override
                public void onProgressEnded() {
                    handler.post(() -> {
                        TaskRow row = rows.remove(key);
                        if (row != null) {
                            taskList.removeView(row.card);
                        }
                        updateSummary.run();
                    });
                }
            };

            listeners.add(listener);
            ProgressKeeper.addListener(key, listener);
        }

        updateSummary.run();
    }

    private static String titleFor(String key) {
        switch (key) {
            case "download_minecraft": return "Minecraft game files";
            case "unpack_runtime": return "Java runtime";
            case "download_verlist": return "Version list";
            case "authenticate": return "Account authentication";
            case "install_modpack": return "Modpack installation";
            case "extract_components": return "Extracting components";
            case "extract_single_files": return "Extracting files";
            case "instance_install": return "Profile installation";
            case "data_migration": return "Data migration";
            case "driver_install": return "Driver installation";
            default: return "Background task";
        }
    }

    private static TextView makeText(Activity activity, String value,
                                     int size, int color, boolean bold) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);

        if (bold) {
            text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        return text;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value *
                activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class TaskRow {
        final LinearLayout card;
        final TextView detail;
        final TextView percent;
        final ProgressBar progressBar;

        TaskRow(Activity activity, String taskTitle) {
            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(activity, 12), dp(activity, 11),
                    dp(activity, 12), dp(activity, 11));

            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(Color.rgb(42, 31, 47));
            cardBackground.setCornerRadius(dp(activity, 12));
            card.setBackground(cardBackground);

            LinearLayout header = new LinearLayout(activity);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = makeText(activity, taskTitle, 14,
                    Color.WHITE, true);

            LinearLayout.LayoutParams nameParams =
                    new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            header.addView(name, nameParams);

            percent = makeText(activity, "0%", 12,
                    Color.rgb(255, 92, 154), true);
            header.addView(percent);

            card.addView(header);

            detail = makeText(activity,
                    activity.getString(R.string.download_manager_working),
                    11, Color.rgb(215, 200, 213), false);

            LinearLayout.LayoutParams detailParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(activity, 5);
            card.addView(detail, detailParams);

            progressBar = new ProgressBar(activity, null,
                    android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            progressBar.setProgressTintList(
                    android.content.res.ColorStateList.valueOf(
                            Color.rgb(255, 92, 154)));

            LinearLayout.LayoutParams barParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 6));
            barParams.topMargin = dp(activity, 9);
            card.addView(progressBar, barParams);
        }

        void update(int value, int resid, Object... args) {
            int safeProgress = Math.max(0, Math.min(100, value));
            progressBar.setProgress(safeProgress);
            percent.setText(safeProgress + "%");

            if (resid != -1) {
                try {
                    detail.setText(detail.getContext().getString(resid, args));
                    return;
                } catch (Exception ignored) {
                    // Use fallback text below.
                }
            }

            if (args != null && args.length > 0 && args[0] instanceof String) {
                detail.setText((String) args[0]);
            } else {
                detail.setText(R.string.download_manager_working);
            }
        }
    }
}
