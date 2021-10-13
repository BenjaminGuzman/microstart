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

import net.benjaminguzman.exceptions.CircularDependencyException;
import net.benjaminguzman.exceptions.GroupNotFoundException;
import net.benjaminguzman.exceptions.MaxDepthExceededException;
import net.benjaminguzman.exceptions.ServiceNotFoundException;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.yaml.snakeyaml.Yaml;

import javax.management.InstanceAlreadyExistsException;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigLoader {
	private static volatile ConfigLoader instance = null;

	private static final Logger LOGGER = Logger.getLogger(ConfigLoader.class.getName());
	private final JSONObject rootNode;

	/**
	 * @param file configuration will be read from this file
	 * @throws IOException if there is an error while reading file contents (e.g. file doesn't exist)
	 * @throws ValidationException if the given config file doesn't comply with the schema specification
	 * @throws InstanceAlreadyExistsException if there already exists an instance of this class. Use
	 * {@link #getInstance()} to use it instead of creating a new one
	 */
	public ConfigLoader(@NotNull File file) throws IOException, ValidationException, InstanceAlreadyExistsException {
		if (instance != null)
			throw new InstanceAlreadyExistsException(
				"Cannot instantiate " + ConfigLoader.class.getName() + " more than once"
			);

		if (file.exists() && file.length() < 10) // if file doesn't exist exception will be thrown later
			LOGGER.warning(file.getName() + " has less than 10 bytes, that doesn't seem good");

		if (file.getName().endsWith("json"))
			this.rootNode = new JSONObject(Files.readString(file.toPath()));
		else if (file.getName().endsWith("yaml") || file.getName().endsWith("yml")) {
			// read is as yaml
			Yaml yaml = new Yaml();
			Map<String, Object> map = yaml.load(Files.readString(file.toPath()));

			// convert it to json
			this.rootNode = new JSONObject(map);
		} else {
			LOGGER.warning(
				file.getName() + " doesn't have any of the following extensions: yaml, yml, json.\n" +
					" I'll try to read it as JSON"
			);
			this.rootNode = new JSONObject(Files.readString(file.toPath()));
		}

		// validate the config file complies with the specification in schema.json
		try (InputStream inputStream = getClass().getResourceAsStream("/resources/schema.json")) {
			assert inputStream != null;
			JSONObject rawSchema = new JSONObject(new JSONTokener(inputStream));
			Schema schema = SchemaLoader.load(rawSchema);
			schema.validate(this.rootNode);
		}
		instance = this;
	}

	/**
	 * Delegator constructor
	 * <p>
	 * Calling this constructor is equivalent to {@code new ConfigLoader(new File(filePathStr))}
	 *
	 * @param filePathStr path to the configuration file
	 */
	public ConfigLoader(@NotNull String filePathStr) throws IOException, InstanceAlreadyExistsException {
		this(new File(filePathStr));
	}

	@Nullable
	public static ConfigLoader getInstance() {
		return instance;
	}

	/**
	 * Loads ALL the configuration in the configuration file
	 *
	 * @return the loaded configuration
	 */
	public Config load() throws ServiceNotFoundException, FileNotFoundException, MaxDepthExceededException,
		GroupNotFoundException, CircularDependencyException {
		Config conf = Config.getInstance();

		// load single values
		conf.setContinueAfterError(shouldIgnoreErrors())
			.setMaxDepth(maxDepth());

		// load all services
		JSONArray servicesArr = rootNode.getJSONArray("services");
		for (Object service : servicesArr)
			conf.addService(loadServiceConfig(((JSONObject) service).getString("name")));

		// load all groups
		JSONArray groupsArr = rootNode.getJSONArray("groups");
		for (Object group : groupsArr)
			conf.addGroup(loadGroupConfig(((JSONObject) group).getString("name")));

		return conf;
	}

	/**
	 * @return the value of the key continueAfterError in the JSON file or
	 * {@link ConfigDefaults#IGNORE_ERRORS} if not defined
	 */
	public boolean shouldIgnoreErrors() {
		return rootNode.optBoolean("ignoreErrors", ConfigDefaults.IGNORE_ERRORS);
	}

	/**
	 * @return the value of the key maxDepth in the JSON file or {@link ConfigDefaults#MAX_DEPTH} if not defined
	 */
	public int maxDepth() {
		return rootNode.optInt("maxDepth", ConfigDefaults.MAX_DEPTH);
	}

	/**
	 * Loads a {@link ServiceConfig} for the service with the given name or alias
	 *
	 * @param name name or alias to be searched in the array of services defined in configuration file
	 * @return the corresponding {@link ServiceConfig} or null if not found
	 * @throws JSONException            if the configuration has invalid format
	 * @throws FileNotFoundException    If the specified working directory for a service is not found
	 * @throws ServiceNotFoundException if the service configuration was not found
	 */
	@NotNull
	public ServiceConfig loadServiceConfig(@NotNull String name) throws FileNotFoundException, JSONException,
		ServiceNotFoundException {
		return loadServiceConfigJSON(name);
	}

	/**
	 * Loads a {@link Group} for the service group with the given name or alias
	 *
	 * @param name name or alias to be searched in the array of group services defined in configuration file
	 * @return the corresponding {@link Group} or null if not found
	 * @throws JSONException            if the configuration has invalid format
	 * @throws FileNotFoundException    if the specified working directory for a service is not found
	 * @throws ServiceNotFoundException if the service configuration was not found
	 */
	@NotNull
	public GroupConfig loadGroupConfig(@NotNull String name) throws JSONException,
		MaxDepthExceededException, GroupNotFoundException, CircularDependencyException, FileNotFoundException,
		ServiceNotFoundException {
		return loadGroupConfigJSON(name);
	}

	/**
	 * Search and get the corresponding {@link JSONObject} within the array whose name or alias is the specified
	 * one.
	 * <p>
	 * This is done by checking if any object inside the array has the key named "name" equal to the given name, or
	 * the key "alias" (which should be an array if present) contains the given name
	 *
	 * @param name      name or alias to search within the given json array
	 * @param jsonArray json array that is supposed to contain a json object with the given name or alias
	 * @return the corresponding json object or null if it was not found
	 */
	@Nullable
	private static JSONObject getItemByNameOrAlias(@NotNull String name, @NotNull JSONArray jsonArray) {
		for (Object value : jsonArray) {
			JSONObject obj = (JSONObject) value;

			String currentName = obj.getString("name");

			List<String> aliases = Collections.emptyList();
			if (obj.has("aliases"))
				aliases = list2StringList(obj.getJSONArray("aliases").toList());

			if (currentName.equals(name) || aliases.contains(name))
				return obj;
		}

		return null;
	}

	/**
	 * From the given list, filters those elements that are {@link String} and returns them in a list
	 *
	 * @param list list to be converted
	 * @return the list with all elements being strings
	 */
	@NotNull
	private static List<String> list2StringList(@NotNull List<?> list) {
		return list
			.stream()
			.filter(o -> o instanceof String)
			.map(o -> (String) o)
			.collect(Collectors.toList());
	}

	/**
	 * Loads a {@link ServiceConfig} for the service with the given name or alias assuming the contents in
	 * config file have json format
	 *
	 * @param name name or alias to be looked in the array of services defined in configuration file
	 * @return the corresponding {@link ServiceConfig} or null if not found
	 * @throws JSONException            if the configuration has invalid format
	 * @throws FileNotFoundException    if the specified working directory for a service is not found
	 * @throws ServiceNotFoundException if the service configuration was not found
	 */
	@NotNull
	private ServiceConfig loadServiceConfigJSON(@NotNull String name) throws FileNotFoundException, JSONException, ServiceNotFoundException {
		JSONObject serviceJSONConfig = getItemByNameOrAlias(name, rootNode.getJSONArray("services"));
		if (serviceJSONConfig == null)
			throw new ServiceNotFoundException("\"" + name + "\"" + " service was not found");

		// by now we know the current json node is the one the user wants
		// load all its configuration
		ServiceConfig config = new ServiceConfig();

		// set required fields
		config.setName(serviceJSONConfig.getString("name"));
		config.setStartCmd(serviceJSONConfig.getString("start"));

		// set optional fields
		if (serviceJSONConfig.has("aliases"))
			config.setAliases(list2StringList(serviceJSONConfig.getJSONArray("aliases").toList()));

		if (serviceJSONConfig.has("color")) {
			Color color;
			if (serviceJSONConfig.get("color") instanceof String) // color is given as string
				color = Color.decode(serviceJSONConfig.getString("color"));
			else // color is given as an integer
				color = new Color(serviceJSONConfig.getInt("color"));

			config.setColor(color);
		}

		if (serviceJSONConfig.has("workDir")) {
			File workDir = new File(serviceJSONConfig.getString("workDir"));
			if (!workDir.exists() || !workDir.isDirectory() || !workDir.canRead())
				throw new FileNotFoundException(
					"\"" + workDir.getAbsolutePath() + "\" either doesn't exists," +
						" isn't a directory, or you can't read from it"
				);

			config.setWorkingDirectory(workDir);
		}

		if (serviceJSONConfig.has("startedPatterns"))
			config.setStartedPatterns(
				list2StringList(serviceJSONConfig.getJSONArray("startedPatterns").toList())
					.stream()
					.map(patternStr -> Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE))
					.collect(Collectors.toList())
			);
		else
			LOGGER.warning(
				"⚠ Not configuring started patterns for " +
					config.getColorizedName() +
					" may result on the application hanging up indefinitely"
			);

		if (serviceJSONConfig.has("errorPatterns"))
			config.setErrorPatterns(
				list2StringList(serviceJSONConfig.getJSONArray("errorPatterns").toList())
					.stream()
					.map(patternStr -> Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE))
					.collect(Collectors.toList())
			);
		else
			LOGGER.warning(
				"⚠ Not configuring error patterns for " +
					config.getColorizedName() +
					" may result on the application hanging up indefinitely if an error" +
					" occurs"
			);

		return config;
	}

	/**
	 * Loads a {@link GroupConfig} for the service group with the given name or alias assuming the contents
	 * in config file have json format
	 *
	 * @param name name or alias to be looked in the array of groups defined in configuration file
	 * @return the corresponding {@link GroupConfig} or null if not found
	 * @throws JSONException               if the configuration has invalid format
	 * @throws CircularDependencyException if it is not a DAG, and therefore it has circular dependencies
	 * @throws GroupNotFoundException      if a group name is not found in the configuration
	 * @throws MaxDepthExceededException   if the graph depth is greater than the maximum depth allowed
	 */
	@NotNull
	private GroupConfig loadGroupConfigJSON(@NotNull String name) throws JSONException, MaxDepthExceededException,
		GroupNotFoundException, CircularDependencyException, FileNotFoundException, ServiceNotFoundException {
		JSONObject groupJSONConfig = getItemByNameOrAlias(name, rootNode.getJSONArray("groups"));
		if (groupJSONConfig == null)
			throw new GroupNotFoundException("\"" + name + "\" was not found in the groups array");

		// check dependencies are ok for this group
		// this check is important because dependencies will be loaded recursively, so, this avoids infinite
		// recursion
		int max_depth = rootNode.optInt("maxDepth", ConfigDefaults.MAX_DEPTH);
		checkGroupDependencies(
			groupJSONConfig.getString("name"),
			rootNode.getJSONArray("groups"),
			max_depth
		);

		return loadGroupDependencyConfigJSON(name);
	}

	@NotNull
	private GroupConfig loadGroupDependencyConfigJSON(@NotNull String name) throws JSONException,
		GroupNotFoundException, FileNotFoundException, ServiceNotFoundException {
		JSONObject groupJSONConfig = getItemByNameOrAlias(name, rootNode.getJSONArray("groups"));
		if (groupJSONConfig == null) // if checkGroupDependencies is called before this, this will never happen
			throw new GroupNotFoundException(
				"Dependency \"" + name + "\" was not found in the groups array"
			);

		// by now we know the current json node is the one the user wants
		// load all its configuration
		GroupConfig config = new GroupConfig();
		config.setName(groupJSONConfig.getString("name"));

		// set other config properties
		if (groupJSONConfig.has("aliases"))
			config.setAliases(list2StringList(groupJSONConfig.getJSONArray("aliases").toList()));

		// load services
		List<String> services = list2StringList(groupJSONConfig.getJSONArray("services").toList());
		List<ServiceConfig> servicesConfigs = new ArrayList<>(services.size());
		for (String serviceName : services)
			servicesConfigs.add(loadServiceConfigJSON(serviceName));

		config.setServicesConfigs(servicesConfigs);

		// load other group dependencies
		if (groupJSONConfig.has("dependencies") && !groupJSONConfig.getJSONArray("dependencies").isEmpty()) {
			List<String> dependencies = list2StringList(
				groupJSONConfig.getJSONArray("dependencies").toList()
			);
			List<GroupConfig> groupsConfigs = new ArrayList<>(dependencies.size());
			for (String dependencyName : dependencies)
				groupsConfigs.add(loadGroupDependencyConfigJSON(dependencyName));

			config.setDependenciesConfigs(groupsConfigs);
		}

		return config;
	}

	/**
	 * Ensures dependency graph for the given service group is a DAG (Directed Acyclic Graph) and has valid
	 * references and depth.
	 * <p>
	 * In other words, ensures there are no circular dependencies, max depth is not exceeded and depends on
	 * services
	 * that are actually described in configuration
	 * <p>
	 * Formally, this only checks the graph has no cycles (check if graph is AC Acyclic Graph), but
	 * edges in the graph have only 1 direction (because the nature of dependencies) so it is implicitly a
	 * DAG
	 *
	 * @param rootGroupName name or alias for the service group to be checked
	 * @param groupsArray   array containing all the groups configuration
	 * @param max_depth     maximum depth allowed
	 * @throws CircularDependencyException if it is not a DAG, and therefore it has circular dependencies
	 * @throws GroupNotFoundException      if a group name is not found in the configuration
	 * @throws MaxDepthExceededException   if the graph depth is greater than the maximum depth allowed
	 */
	public void checkGroupDependencies(
		@NotNull String rootGroupName,
		@NotNull JSONArray groupsArray,
		int max_depth
	) throws CircularDependencyException, GroupNotFoundException, MaxDepthExceededException {
		Map<String, Boolean> visitedNodes = new HashMap<>(); // contains the names of the visited nodes

		// use a stack to traverse the graph using DFS
		Stack<String> pendingNodes = new Stack<>(); // contains the names of the unvisited nodes

		pendingNodes.push(rootGroupName);

		int depth = 0;

		// traverse the graph to detect cycles
		while (!pendingNodes.isEmpty()) {
			String current = pendingNodes.pop();

			// if current node has been visited, a cycle has been found
			if (visitedNodes.containsKey(current))
				throw new CircularDependencyException(
					"Group \"" + rootGroupName
						+ "\" depends on itself (circular dependency was found)"
				);

			visitedNodes.put(current, true); // mark the current node as visited

			// obtain dependencies for current group
			JSONObject groupConfig = getItemByNameOrAlias(current, groupsArray);
			if (groupConfig == null)
				throw new GroupNotFoundException(
					"Group dependency \"" + current + "\" for group \"" + rootGroupName +
						"\" was not found in configuration"
				);

			List<String> dependencies = Collections.emptyList();
			if (groupConfig.has("dependencies"))
				dependencies = list2StringList(
					groupConfig.getJSONArray("dependencies").toList()
				);

			// add dependencies to pending nodes
			dependencies.forEach(pendingNodes::push); // replace pendingNodes.addAll(dependencies);?

			if (++depth > max_depth)
				throw new MaxDepthExceededException(
					"Group \"" + rootGroupName + "\" has a dependency graph which exceeds depth" +
						" limit " + max_depth
				);

			// if the node has no dependencies, the next iteration the current item will be one
			// level up
			if (dependencies.isEmpty())
				depth -= 2; // decrement by 2 to undo the first increment and to "go back" in the graph
		}
	}

	@TestOnly
	public static void deleteInstance() {
		instance = null;
	}

	@TestOnly
	public JSONObject getRoot() {
		return rootNode;
	}
}
