package com.zetaplugins.cookieclickerz.util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import com.zetaplugins.cookieclickerz.CookieClickerZ;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class VersionChecker {
    public final String MODRINTH_SLUG = "cookieclickerz";
    public final String MODRINTH_ID = "YE4jVOVg";

    private final CookieClickerZ plugin;
    public final String MODRINTH_PROJECT_URL = "https://api.modrinth.com/v2/project/" + MODRINTH_ID;
    public boolean NEW_VERSION_AVAILABLE = false;

    public VersionChecker(CookieClickerZ plugin) {
        this.plugin = plugin;
        String latestVersion = getLatestVersionFromModrinth();
        if (latestVersion != null) {
            String currentVersion = plugin.getDescription().getVersion();
            if (!latestVersion.trim().equals(currentVersion.trim())) {
                NEW_VERSION_AVAILABLE = true;
                plugin.getLogger().info("A new version of CookieCLickerZ is available! Version: " + latestVersion + "\nDownload the latest version here: https://modrinth.com/plugin/" + MODRINTH_SLUG + "/versions");
            }
        }
    }

    /**
     * Gets the latest version of the plugin from Modrinth
     *
     * @return The latest version of the plugin
     */
    public String getLatestVersionFromModrinth() {
        try {
            URL projectUrl = new URL(MODRINTH_PROJECT_URL);
            HttpURLConnection projectConnection = (HttpURLConnection) projectUrl.openConnection();
            projectConnection.setRequestMethod("GET");

            if (projectConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader projectReader = new BufferedReader(new InputStreamReader(projectConnection.getInputStream()));
                StringBuilder projectResponse = new StringBuilder();
                String projectInputLine;
                while ((projectInputLine = projectReader.readLine()) != null) {
                    projectResponse.append(projectInputLine);
                }
                projectReader.close();

                JSONParser parser = new JSONParser();
                JSONObject projectJson = (JSONObject) parser.parse(projectResponse.toString());
                JSONArray versionArray = (JSONArray) projectJson.get("versions");
                String latestVersionId = (String) versionArray.get(versionArray.size() - 1);

                URL versionUrl = new URL(MODRINTH_PROJECT_URL + "/version/" + latestVersionId);
                HttpURLConnection versionConnection = (HttpURLConnection) versionUrl.openConnection();
                versionConnection.setRequestMethod("GET");

                if (versionConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    BufferedReader versionReader = new BufferedReader(new InputStreamReader(versionConnection.getInputStream()));
                    StringBuilder versionResponse = new StringBuilder();
                    String versionInputLine;
                    while ((versionInputLine = versionReader.readLine()) != null) {
                        versionResponse.append(versionInputLine);
                    }
                    versionReader.close();

                    JSONObject versionJson = (JSONObject) parser.parse(versionResponse.toString());
                    return (String) versionJson.get("version_number");
                } else {
                    plugin.getLogger().warning("Failed to retrieve version details from Modrinth. Response code: " + versionConnection.getResponseCode());
                }
            } else {
                plugin.getLogger().warning("Failed to retrieve project information from Modrinth. Response code: " + projectConnection.getResponseCode());
            }
        } catch (IOException | org.json.simple.parser.ParseException e) {
            plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
        }
        return null;
    }
}
