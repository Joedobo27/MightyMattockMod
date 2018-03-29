package com.joedobo27.mmm;

import org.jetbrains.annotations.Nullable;

import javax.json.*;
import java.io.*;
import java.util.Properties;

class ConfigureOptions {

    private final ConfigureActionOptions chopActionOptions;
    private final ConfigureActionOptions collectResourceActionOptions;
    private final ConfigureActionOptions digActionOptions;
    private final ConfigureActionOptions mineActionOptions;
    private final ConfigureActionOptions packActionOptions;
    private final ConfigureActionOptions raiseDirtActionOptions;
    private final ConfigureActionOptions raiseRockActionOptions;

    private static ConfigureOptions instance = null;
    private static final String DEFAULT_ACTION_OPTION = "" +
            "{\"minSkill\":10 ,\"maxSkill\":95 , \"longestTime\":100 , \"shortestTime\":10 , \"minimumStamina\":6000}";

    private ConfigureOptions(ConfigureActionOptions chopActionOptions,
                             ConfigureActionOptions collectResourceActionOptions,
                             ConfigureActionOptions digActionOptions, ConfigureActionOptions mineActionOptions,
                             ConfigureActionOptions packActionOptions, ConfigureActionOptions raiseDirtActionOptions,
                             ConfigureActionOptions raiseRockActionOptions) {

        this.chopActionOptions = chopActionOptions;
        this.collectResourceActionOptions = collectResourceActionOptions;
        this.digActionOptions = digActionOptions;
        this.mineActionOptions = mineActionOptions;
        this.packActionOptions = packActionOptions;
        this.raiseDirtActionOptions = raiseDirtActionOptions;
        this.raiseRockActionOptions = raiseRockActionOptions;
        instance = this;
    }

    synchronized static void setOptions(@Nullable Properties properties) {
        if (instance == null) {
            if (properties == null) {
                properties = getProperties();
            }
            if (properties == null)
                throw new RuntimeException("properties can't be null here.");

            ConfigureOptions configureOptions = new ConfigureOptions(
                    doJsonToPOJO(properties.getProperty("chopAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("collectResourceAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("digAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("mineAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("packAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("raiseDirtAction", DEFAULT_ACTION_OPTION)),
                    doJsonToPOJO(properties.getProperty("raiseRockAction", DEFAULT_ACTION_OPTION))
            );
            instance = configureOptions;
        }
    }

    synchronized static void resetOptions() {
        instance = null;
        Properties properties = getProperties();
        if (properties == null)
            throw new RuntimeException("properties can't be null here.");
        ConfigureOptions configureOptions = new ConfigureOptions(
                doJsonToPOJO(properties.getProperty("chopAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("collectResourceAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("digAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("mineAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("packAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("raiseDirtAction", DEFAULT_ACTION_OPTION)),
                doJsonToPOJO(properties.getProperty("raiseRockAction", DEFAULT_ACTION_OPTION))
        );
        instance = configureOptions;
    }

    private static ConfigureActionOptions doJsonToPOJO(String jsonString) {
        Reader reader = new StringReader(jsonString);
        JsonReader jsonReader = Json.createReader(reader);
        JsonObject jsonValues = jsonReader.readObject();
        int minSkill = jsonValues.getInt("minSkill", 10);
        int maxSkill = jsonValues.getInt("maxSkill", 95);
        int longestTime = jsonValues.getInt("longestTime", 100);
        int shortestTime = jsonValues.getInt("shortestTime", 10);
        int minimumStamina = jsonValues.getInt("minimumStamina", 6000);
        if (jsonReader != null) {
            jsonReader.close();
        }
        return new ConfigureActionOptions(minSkill, maxSkill, longestTime, shortestTime, minimumStamina);
    }

    private static Properties getProperties() {
        try {
            File configureFile = new File("mods/MightyMattockMod.properties");
            FileInputStream configureStream = new FileInputStream(configureFile);
            Properties configureProperties = new Properties();
            configureProperties.load(configureStream);
            return configureProperties;
        }catch (IOException e) {
            MightyMattockMod.logger.warning(e.getMessage());
            return null;
        }
    }

    static ConfigureOptions getInstance() {
        return instance;
    }

    ConfigureActionOptions getChopActionOptions() {
        return chopActionOptions;
    }

    ConfigureActionOptions getCollectResourceActionOptions() {
        return collectResourceActionOptions;
    }

    ConfigureActionOptions getDigActionOptions() {
        return digActionOptions;
    }

    ConfigureActionOptions getMineActionOptions() {
        return mineActionOptions;
    }

    ConfigureActionOptions getPackActionOptions() {
        return packActionOptions;
    }

    ConfigureActionOptions getRaiseDirtActionOptions() {
        return raiseDirtActionOptions;
    }

    ConfigureActionOptions getRaiseRockActionOptions() {
        return raiseRockActionOptions;
    }
}
