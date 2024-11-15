package me.adarsh.autoupdate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateChecker implements Runnable {

    /** The frequency in seconds to check for updates */
    public static final long CHECK_UPDATE_FREQUENCY = 10*60; // 10 minutes

    /** The url to the jenkins last build information */
    public static final String LAST_BUILD_URL_VV = "https://ci.viaversion.com/job/ViaVersion/lastBuild/api/json?random=%f";

    /** The url to the jenkins last build information */
    public static final String LAST_BUILD_URL_VB = "https://ci.viaversion.com/job/ViaBackwards/lastBuild/api/json?random=%f";

    /** The url to the jenkins last build information */
    public static final String LAST_BUILD_URL_VR = "https://ci.viaversion.com/job/ViaRewind/lastBuild/api/json?random=%f";


    /** The url to download the viaversion jar from */
    public static final String DOWNLOAD_URL_VV = "https://ci.viaversion.com/job/ViaVersion/lastBuild/artifact/%s";

    /** The url to download the viabackwards jar from */
    public static final String DOWNLOAD_URL_VB = "https://ci.viaversion.com/job/ViaBackwards/lastBuild/artifact/%s";

    /** The url to download the viarewind jar from */
    public static final String DOWNLOAD_URL_VR = "https://ci.viaversion.com/job/ViaRewind/lastBuild/artifact/%s";

    /** The url to the jeskins last build information */
    private final String LAST_BUILD_URL;

    /** The url to the target download */
    private final String DOWNLOAD_URL;

    private ViaVersionAutoUpdate viaVersionAutoUpdate;

    public UpdateChecker(ViaVersionAutoUpdate viaVersionAutoUpdate, String type) {
        this.viaVersionAutoUpdate = viaVersionAutoUpdate;
        switch (type) {
            case "VV": {
                DOWNLOAD_URL = DOWNLOAD_URL_VV;
                LAST_BUILD_URL = LAST_BUILD_URL_VV;
                break;
            }
            case "VB": {
                DOWNLOAD_URL = DOWNLOAD_URL_VB;
                LAST_BUILD_URL = LAST_BUILD_URL_VB;
                break;
            }
            case "VR": {
                DOWNLOAD_URL = DOWNLOAD_URL_VR;
                LAST_BUILD_URL = LAST_BUILD_URL_VR;
                break;
            }
            default:
                throw new IllegalArgumentException("Invalid type: " + type);
        }
    }

    @Override
    public void run() {
        try {
            // Download the json
            String lastBuild = downloadLastBuildInfo();

            // Parse the json
            JsonObject json = new JsonParser().parse(lastBuild).getAsJsonObject();

            // Get the file name (json.artifacts[0].filename)
            String fileName = json.getAsJsonArray("artifacts")
                    .get(0).getAsJsonObject()
                    .get("fileName").getAsString();

            String relativePath = json.getAsJsonArray("artifacts")
                    .get(0).getAsJsonObject()
                    .get("relativePath").getAsString();

            // Check if it's a new update
            File oldJar = viaVersionAutoUpdate.getPlugin().getViaVersionJar();
            if (oldJar == null || !oldJar.getName().equalsIgnoreCase(fileName)) {

                // New update!
                installUpdate(oldJar, fileName, relativePath);

                // Restart the server to apply the update
                viaVersionAutoUpdate.startRestartCountdown();
            }


        } catch (Exception e) {
            System.err.println("[ViaVersionAutoUpdate] An error occured while checking for updates");
            e.printStackTrace();
        }

        viaVersionAutoUpdate.getPlugin().runTaskLaterAsync(this, CHECK_UPDATE_FREQUENCY);
    }

    /**
     * Install an update
     * @param oldJar The old jar to be deleted
     * @param fileName The new file to be downloaded
     */
    private void installUpdate(File oldJar, String fileName, String relativePath) throws IOException {
        File newJar = new File(viaVersionAutoUpdate.getPlugin().getPluginsDirectory(), fileName);

        URL url = new URL(String.format(DOWNLOAD_URL, relativePath));
        URLConnection connection = url.openConnection();
        // Spoof a user-agent, jenkins doesn't like the java default
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36");
        try (InputStream in = connection.getInputStream()) {
            Files.copy(in, newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Try to delete old jar now, if not, delete it on exit
            if (oldJar != null && !oldJar.delete()) {
                oldJar.deleteOnExit();
            }
        }
    }

    /**
     * Download the last build info from LAST_BUILD_URL
     *
     * @return A json string of the last build info
     */
    private String downloadLastBuildInfo() throws IOException {
        URL url = new URL(String.format(LAST_BUILD_URL, Math.random()));
        URLConnection connection = url.openConnection();
        // Spoof a user-agent, jenkins doesn't like the java default
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.79 Safari/537.36");
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int i;
            while ((i = in.read(b)) >= 0) {
                buffer.write(b, 0, i);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}