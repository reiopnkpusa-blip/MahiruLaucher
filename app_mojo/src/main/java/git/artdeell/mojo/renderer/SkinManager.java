package git.artdeell.mojo.renderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.accounts.Account;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SkinManager {
    private SkinManager() {}

    public static File getLocalSkinFile(Context context) {
        File directory = new File(context.getFilesDir(), "skins");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create skin directory");
        }
        return new File(directory, "local-skin.png");
    }

    public static void saveLocalSkin(Context context, Uri uri)
            throws IOException {
        Bitmap bitmap;
        try (InputStream input =
                     context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Cannot open selected image");
            bitmap = BitmapFactory.decodeStream(input);
        }

        if (bitmap == null) throw new IOException("Invalid PNG image");

        try {
            if (bitmap.getWidth() != 64 || bitmap.getHeight() != 64) {
                throw new IOException("Skin must be exactly 64 x 64 pixels");
            }

            File output = getLocalSkinFile(context);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw new IOException("Could not save skin PNG");
                }
            }
        } finally {
            bitmap.recycle();
        }
    }

    public static void uploadLocalSkin(
            Context context, Account account, String variant)
            throws IOException {

        if (account == null || account.isLocal()
                || account.authType != AuthType.MICROSOFT
                || account.accessToken == null
                || account.accessToken.trim().isEmpty()
                || "0".equals(account.accessToken)) {
            throw new IOException("Sign in with a Minecraft Premium account first");
        }

        if (!"classic".equals(variant) && !"slim".equals(variant)) {
            throw new IOException("Invalid skin model");
        }

        File skin = getLocalSkinFile(context);
        if (!skin.isFile()) throw new IOException("Import a local skin first");

        String boundary = "MahiruSkinBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) new URL(
                    "https://api.minecraftservices.com/minecraft/profile/skins"
            ).openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty(
                    "Authorization", "Bearer " + account.accessToken);
            connection.setRequestProperty(
                    "Content-Type", "multipart/form-data; boundary=" + boundary);

            try (OutputStream out = new BufferedOutputStream(
                    connection.getOutputStream())) {

                write(out, "--" + boundary + "\r\n");
                write(out, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
                write(out, variant + "\r\n");

                write(out, "--" + boundary + "\r\n");
                write(out, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
                write(out, "Content-Type: image/png\r\n\r\n");

                try (InputStream in = new BufferedInputStream(
                        new java.io.FileInputStream(skin))) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                    }
                }

                write(out, "\r\n--" + boundary + "--\r\n");
                out.flush();
            }

            int status = connection.getResponseCode();
            if (status != 200 && status != 202 && status != 204) {
                throw new IOException(
                        "Minecraft skin upload failed (HTTP " + status + ")");
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void write(OutputStream out, String value)
            throws IOException {
        out.write(value.getBytes(StandardCharsets.UTF_8));
    }
}
