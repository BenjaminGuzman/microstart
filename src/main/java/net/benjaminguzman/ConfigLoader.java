/*
 * Copyright (c) 2021. Benjamín Antonio Velasco Guzmán
 * Author: Benjamín Antonio Velasco Guzmán <bg@benjaminguzman.dev>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.benjaminguzman;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.diogonunes.jcolor.Attribute.TEXT_COLOR;

public class ConfigLoader {
	private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());

	private static ConfigLoader instance;
	private final String configData;

	/**
	 *
	 * @param file configuration will be read from this file
	 * @throws IOException If there is an error while reading file contents (e.g. file doesn't exist)
	 */
	public ConfigLoader(@NotNull File file) throws IOException {
		this.configData = Files.readString(file.toPath());
	}

	public static ConfigLoader getInstance() {
		return instance;
	}

	/**
	 * Loads a {@link ServiceConfig} for the service with the given name or alias
	 *
	 * @param name name or alias to be looked in the array of services defined in configuration file
	 * @return the corresponding {@link ServiceConfig} or null if not found
	 * @throws JSONException if the configuration has invalid format
	 * @throws FileNotFoundException If the specified working directory for a service is not found
	 */
	@Nullable
	public ServiceConfig loadConfig(@NotNull String name) throws FileNotFoundException, JSONException {
		return loadConfigJSON(name);
	}

	/**
	 * Loads a {@link ServiceConfig} for the service with the given name or alias assuming the contents in
	 * {@link #configData} have json format
	 *
	 * @param name name or alias to be looked in the array of services defined in configuration file
	 * @return the corresponding {@link ServiceConfig} or null if not found
	 * @throws JSONException if the configuration has invalid format
	 * @throws FileNotFoundException If the specified working directory for a service is not found
	 */
	@Nullable
	private ServiceConfig loadConfigJSON(@NotNull String name) throws FileNotFoundException, JSONException {
		JSONObject rootNode = new JSONObject(configData);
		JSONArray servicesNode = rootNode.getJSONArray("services");

		for (Object value : servicesNode) {
			JSONObject serviceNode = (JSONObject) value;

			String serviceName = serviceNode.getString("name");

			List<String> aliases = Collections.emptyList();
			if (serviceNode.has("aliases"))
				aliases = serviceNode.getJSONArray("aliases").toList()
					.stream()
					.filter(o -> o instanceof String)
					.map(o -> (String) o)
					.collect(Collectors.toList());

			if (!serviceName.equals(name) || !aliases.contains(name))
				continue;

			// by now we now the current json node is the one the user wants
			// load all its configuration
			ServiceConfig config = new ServiceConfig();

			// set required fields
			config.setName(serviceName);
			config.setStartCmd(serviceNode.getString("start"));

			// set optional fields
			config.setAliases(aliases);

			if (serviceNode.has("color")) {
				Color color = Color.decode(serviceNode.getString("color"));
				config.setAsciiColor(
					TEXT_COLOR(color.getRed(), color.getGreen(), color.getBlue()).toString()
				);
			}

			if (serviceNode.has("workDir")) {
				File workDir = new File(serviceNode.getString("workDir"));
				if (!workDir.exists() || !workDir.isDirectory() || !workDir.canRead())
					throw new FileNotFoundException(
						"\"" + workDir.getAbsolutePath() + "\" either doesn't exists," +
							" isn't a directory, or you can't read from it"
					);

				config.setWorkingDirectory(workDir);
			}

			if (serviceNode.has("upPatterns")) {
				List<Pattern> patterns = serviceNode.getJSONArray("upPatterns").toList()
					.stream()
					.filter(o -> o instanceof String)
					.map(obj -> {
						String patternStr = (String) obj;
						return Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
					})
					.collect(Collectors.toList());

				config.setUpPatterns(patterns);
			} else
				LOGGER.warning(
					"⚠ Not configuring up patterns for " +
						config.getColorizedName() +
						" may result on the application hanging up indefinitely"
				);

			if (serviceNode.has("errorPatterns")) {
				List<Pattern> patterns = serviceNode.getJSONArray("errorPatterns").toList()
					.stream()
					.filter(o -> o instanceof String)
					.map(obj -> {
						String patternStr = (String) obj;
						return Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
					})
					.collect(Collectors.toList());

				config.setErrorPatterns(patterns);
			} else
				LOGGER.warning(
					"⚠ Not configuring error patterns for " +
						config.getColorizedName() +
						" may result on the application hanging up indefinitely if an error" +
						" occurs"
				);

			return config;
		}

		return null;
	}
}
