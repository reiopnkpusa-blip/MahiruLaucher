package git.artdeell.mojo.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modpacks.api.ModrinthApi;
import git.artdeell.mojo.utils.DownloadUtils;
import git.artdeell.mojo.utils.FileUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class MahiruStoreFragment extends Fragment {
    public static final String TAG = "MahiruStoreFragment";

    private static final String API = "https://api.modrinth.com/v2";
    private final String[] categories = {"Mods", "Modpacks", "Datapacks", "Shaders", "Resource Packs"};
    private final String[] loaders = {"Auto", "Fabric", "Forge", "NeoForge", "Quilt", "Vanilla"};
    private final ArrayList<JSONObject> results = new ArrayList<>();

    private EditText searchInput;
    private Spinner categorySpinner, loaderSpinner;
    private TextView versionLabel, status;
    private TextView progressLabel;
    private ProgressBar progress;
    private ListView resultList;
    private ArrayAdapter<String> resultAdapter;
    private final ArrayList<String> resultTitles = new ArrayList<>();
    private String gameVersion = "";
    private int requestGeneration = 0;

    public MahiruStoreFragment() {
        super();
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        Instance selected = Instances.loadSelectedInstance();
        if (selected != null && selected.versionId != null) {
            String id = selected.versionId;
            String[] parts = id.split("-");
            gameVersion = parts[parts.length - 1];
        }
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle state) {
        int pad = dp(14);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(0xFF171019);

        TextView title = new TextView(requireContext());
        title.setText("MAHIRU STORE");
        title.setTextColor(0xFFFF5C9B);
        title.setTextSize(22);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("Discover compatible Minecraft content from Modrinth");
        subtitle.setTextColor(0xFFD9CED8);
        subtitle.setTextSize(12);
        root.addView(subtitle);

        searchInput = new EditText(requireContext());
        searchInput.setSingleLine(true);
        searchInput.setHint("Search mods, packs and more...");
        searchInput.setTextColor(0xFFFFFFFF);
        searchInput.setHintTextColor(0xFFAA9EAA);
        root.addView(searchInput, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout filters = new LinearLayout(requireContext());
        filters.setOrientation(LinearLayout.HORIZONTAL);
        categorySpinner = new Spinner(requireContext());
        loaderSpinner = new Spinner(requireContext());
        categorySpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, categories));
        loaderSpinner.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, loaders));
        filters.addView(categorySpinner, new LinearLayout.LayoutParams(0, dp(46), 1));
        filters.addView(loaderSpinner, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(filters);

        versionLabel = new TextView(requireContext());
        versionLabel.setTextColor(0xFFFF8AB5);
        versionLabel.setTextSize(13);
        versionLabel.setPadding(0, dp(6), 0, dp(6));
        root.addView(versionLabel);
        updateVersionLabel();

        Button search = new Button(requireContext());
        search.setText("SEARCH");
        search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE84E91));
        root.addView(search, new LinearLayout.LayoutParams(-1, dp(44)));

        progress = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(5)));
        progressLabel = new TextView(requireContext());
        progressLabel.setTextColor(0xFFFF8AB5);
        progressLabel.setTextSize(11);
        progressLabel.setVisibility(View.GONE);
        root.addView(progressLabel);

        status = new TextView(requireContext());
        status.setTextColor(0xFFD9CED8);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(10), 0, dp(10));
        root.addView(status);

        resultList = new ListView(requireContext());
        resultList.setDividerHeight(dp(1));
        resultAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_2, android.R.id.text1, resultTitles) {
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                TextView name = new TextView(requireContext());
                name.setText(resultTitles.get(position));
                name.setTextColor(0xFFFFFFFF);
                name.setTextSize(15);
                name.setTypeface(null, 1);
                TextView description = new TextView(requireContext());
                JSONObject item = results.get(position);
                description.setText(item.optString("description", ""));
                description.setTextColor(0xFFBFB3C0);
                description.setTextSize(12);
                row.addView(name);
                row.addView(description);
                return row;
            }
        };
        resultList.setAdapter(resultAdapter);
        root.addView(resultList, new LinearLayout.LayoutParams(-1, 0, 1));

        search.setOnClickListener(v -> searchProjects());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            searchProjects();
            return true;
        });
        categorySpinner.setOnItemSelectedListener(new SimpleSelectionListener(() -> searchProjects()));
        loaderSpinner.setOnItemSelectedListener(new SimpleSelectionListener(() -> searchProjects()));
        resultList.setOnItemClickListener((parent, view, position, id) -> showProjectVersions(position));

        searchProjects();
        return root;
    }

    private void updateVersionLabel() {
        versionLabel.setText("Minecraft version: " +
                (gameVersion.isEmpty() ? "Select a profile first" : gameVersion));
    }

    private void searchProjects() {
        if (!isAdded() || categorySpinner == null) return;
        final int generation = ++requestGeneration;
        progress.setVisibility(View.VISIBLE);
        status.setText("Searching Modrinth...");
        results.clear();
        resultTitles.clear();
        resultAdapter.notifyDataSetChanged();

        final String query = searchInput.getText().toString().trim();
        if (gameVersion == null || gameVersion.trim().isEmpty()) {
            progress.setVisibility(View.GONE);
            status.setText("Select a Minecraft profile with a known game version first.");
            return;
        }
        final String type = getProjectType();
        final String loader = getLoader();
        final String version = gameVersion;

        new Thread(() -> {
            try {
                JSONArray facets = new JSONArray();
                facets.put(new JSONArray().put("project_type:" + type));
                if (!version.isEmpty()) facets.put(new JSONArray().put("versions:" + version));
                if (!loader.isEmpty() && ("mod".equals(type) || "modpack".equals(type))) facets.put(new JSONArray().put("categories:" + loader));

                String url = API + "/search?query=" + enc(query)
                        + "&facets=" + enc(facets.toString()) + "&limit=40&index=relevance";
                JSONObject response = new JSONObject(DownloadUtils.downloadString(url));
                JSONArray hits = response.optJSONArray("hits");
                ArrayList<JSONObject> found = new ArrayList<>();
                if (hits != null) for (int i = 0; i < hits.length(); i++) {
                    JSONObject hit = hits.optJSONObject(i);
                    if (hit != null) found.add(hit);
                }
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (generation != requestGeneration) return;
                    results.clear();
                    results.addAll(found);
                    resultTitles.clear();
                    for (JSONObject item : found) resultTitles.add(item.optString("title", "Untitled"));
                    resultAdapter.notifyDataSetChanged();
                    progress.setVisibility(View.GONE);
                    status.setText(found.isEmpty() ? "No compatible projects found." : found.size() + " results • Tap an item to install");
                });
            } catch (Exception error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (generation != requestGeneration) return;
                    progress.setVisibility(View.GONE);
                    status.setText("Search failed: " + error.getMessage());
                });
            }
        }, "Mahiru-Store-Search").start();
    }

    private void showProjectVersions(int position) {
        if (position < 0 || position >= results.size()) return;
        JSONObject project = results.get(position);
        String projectId = project.optString("project_id", "");
        String title = project.optString("title", "Minecraft content");
        String type = getProjectType();
        String loader = getLoader();
        String version = gameVersion;
        if (projectId.isEmpty()) return;

        progress.setVisibility(View.VISIBLE);
        status.setText("Checking compatible versions...");
        new Thread(() -> {
            try {
                String url = API + "/project/" + enc(projectId) + "/version";
                if (!version.isEmpty()) url += "?game_versions=" + enc(new JSONArray().put(version).toString());
                if (!loader.isEmpty() && ("mod".equals(type) || "modpack".equals(type))) {
                    url += (url.contains("?") ? "&" : "?") + "loaders=" + enc(new JSONArray().put(loader).toString());
                }
                JSONArray versions = new JSONArray(DownloadUtils.downloadString(url));
                if (versions.length() == 0) throw new IllegalStateException("No compatible versions for this profile.");
                JSONObject selected = versions.getJSONObject(0);
                JSONArray files = selected.optJSONArray("files");
                if (files == null || files.length() == 0) throw new IllegalStateException("This version has no downloadable file.");
                JSONObject file = files.optJSONObject(0);
                for (int i = 0; i < files.length(); i++) {
                    JSONObject candidate = files.optJSONObject(i);
                    if (candidate != null && candidate.optBoolean("primary", false)) {
                        file = candidate;
                        break;
                    }
                }
                JSONObject finalFile = file;
                String fileUrl = finalFile.optString("url", "");
                String filename = finalFile.optString("filename", title.replaceAll("[^A-Za-z0-9._-]", "_") + ".jar");
                if (fileUrl.isEmpty()) throw new IllegalStateException("Download link is missing.");
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Install " + title)
                            .setMessage("Compatible version: " + selected.optString("name", selected.optString("version_number", "Latest"))
                                    + "\nMinecraft: " + version + "\nLoader: " + (loader.isEmpty() ? "Any / Vanilla" : loader))
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("INSTALL", (d, w) -> downloadAndInstall(project, selected, finalFile, type))
                            .show();
                });
            } catch (Exception error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Not available")
                            .setMessage(error.getMessage())
                            .setPositiveButton(android.R.string.ok, null).show();
                });
            }
        }, "Mahiru-Store-Versions").start();
    }

    private void downloadAndInstall(JSONObject project, JSONObject version, JSONObject file, String type) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(requireContext(), "Select a Minecraft profile first.", Toast.LENGTH_LONG).show();
            return;
        }
        File datapackFolder = null;
        if ("datapack".equals(type)) {
            File saves = new File(instance.getGameDirectory(), "saves");
            File[] worlds = saves.listFiles(File::isDirectory);
            if (worlds == null || worlds.length == 0) {
                Toast.makeText(requireContext(), "Create a world before installing datapacks.", Toast.LENGTH_LONG).show();
                return;
            }
            String[] names = new String[worlds.length];
            for (int i = 0; i < worlds.length; i++) names[i] = worlds[i].getName();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Choose world for datapack")
                    .setItems(names, (dialog, which) ->
                            beginStoreDownload(project, version, file, type, instance,
                                    new File(worlds[which], "datapacks")))
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        beginStoreDownload(project, version, file, type, instance, null);
    }

    private void beginStoreDownload(JSONObject project, JSONObject version, JSONObject file,
                                     String type, Instance instance, File datapackFolder) {
        String url = file.optString("url", "");
        String filename = FileUtils.escapeFileName(
                new File(file.optString("filename", "download.bin")).getName());
        if (url.isEmpty()) return;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(false);
        progress.setProgress(0);
        progressLabel.setVisibility(View.VISIBLE);
        status.setText("Preparing " + filename + "...");
        new Thread(() -> {
            File temp = new File(Tools.DIR_CACHE,
                    "mahiru-store-" + System.currentTimeMillis() + "-" + filename);
            try {
                downloadStoreFile(url, temp);
                if ("modpack".equals(type)) {
                    new ModrinthApi().installLocalModpack(
                            project.optString("title", "Modpack"), temp, null);
                } else {
                    File destination;
                    if ("datapack".equals(type)) destination = datapackFolder;
                    else if ("mod".equals(type)) destination = new File(instance.getGameDirectory(), "mods");
                    else if ("shader".equals(type)) destination = new File(instance.getGameDirectory(), "shaderpacks");
                    else if ("resourcepack".equals(type)) destination = new File(instance.getGameDirectory(), "resourcepacks");
                    else throw new IllegalStateException("Unsupported content type.");
                    if (destination == null) throw new IllegalStateException("Install destination is unavailable.");
                    FileUtils.ensureDirectory(destination);
                    File output = new File(destination, filename);
                    String base = destination.getCanonicalPath() + File.separator;
                    if (!output.getCanonicalPath().startsWith(base)) {
                        throw new SecurityException("Invalid install path.");
                    }
                    if (output.exists()) throw new IllegalStateException("This file is already installed.");
                    if (!temp.renameTo(output)) {
                        try (FileInputStream input = new FileInputStream(temp);
                             FileOutputStream outputStream = new FileOutputStream(output)) {
                            byte[] buffer = new byte[8192];
                            int count;
                            while ((count = input.read(buffer)) != -1) outputStream.write(buffer, 0, count);
                        }
                        if (!temp.delete()) temp.deleteOnExit();
                    }
                }
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    progressLabel.setVisibility(View.GONE);
                    status.setText("Installed successfully: " + project.optString("title", filename));
                    Toast.makeText(requireContext(), "Installed successfully", Toast.LENGTH_LONG).show();
                });
            } catch (Exception error) {
                temp.delete();
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    progressLabel.setVisibility(View.GONE);
                    status.setText("Install failed: " + error.getMessage());
                    Toast.makeText(requireContext(), "Install failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "Mahiru-Store-Install").start();
    }

    private void downloadStoreFile(String url, File output) throws Exception {
        FileUtils.ensureParentDirectory(output);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "MahiruLauncher/1.0");
        try {
            int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                throw new IllegalStateException("Download server returned HTTP " + response);
            }
            long total = connection.getContentLengthLong();
            long received = 0;
            try (InputStream input = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(output)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    received += count;
                    outputStream.write(buffer, 0, count);
                    if (isAdded()) {
                        final int percent = total > 0
                                ? (int) Math.min(100, received * 100L / total) : 0;
                        final String label = total > 0
                                ? android.text.format.Formatter.formatFileSize(requireContext(), received)
                                  + " / " + android.text.format.Formatter.formatFileSize(requireContext(), total)
                                : android.text.format.Formatter.formatFileSize(requireContext(), received);
                        requireActivity().runOnUiThread(() -> {
                            if (progress != null) progress.setProgress(percent);
                            if (progressLabel != null) progressLabel.setText(label);
                        });
                    }
                }
                outputStream.getFD().sync();
            }
        } finally {
            connection.disconnect();
        }
    }

    private String getProjectType() {
        switch (categorySpinner.getSelectedItemPosition()) {
            case 1: return "modpack";
            case 2: return "datapack";
            case 3: return "shader";
            case 4: return "resourcepack";
            default: return "mod";
        }
    }

    private String getLoader() {
        if (loaderSpinner == null) return "";
        int selected = loaderSpinner.getSelectedItemPosition();
        if (selected == 5) return ""; // Vanilla uses projects without a mod-loader restriction.
        if (selected == 0) {
            Instance active = Instances.loadSelectedInstance();
            String id = active == null || active.versionId == null
                    ? "" : active.versionId.toLowerCase(Locale.ROOT);
            if (id.contains("fabric")) return "fabric";
            if (id.contains("neoforge")) return "neoforge";
            if (id.contains("forge")) return "forge";
            if (id.contains("quilt")) return "quilt";
            return "";
        }
        switch (selected) {
            case 1: return "fabric";
            case 2: return "forge";
            case 3: return "neoforge";
            case 4: return "quilt";
            default: return "";
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSelectionListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable callback;
        private boolean first = true;
        SimpleSelectionListener(Runnable callback) { this.callback = callback; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
            if (first) { first = false; return; }
            callback.run();
        }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
    }
}