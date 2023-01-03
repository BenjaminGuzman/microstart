/*
 * Copyright (c) 2021. Benjamín Antonio Velasco Guzmán
 * Author: Benjamín Antonio Velasco Guzmán <9benjaminguzman@gmail.com>
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
	@NotNull
	private static final Config config = new Config();

	@NotNull
	public final Map<String, ServiceConfig> services = new HashMap<>();

	@NotNull
	public final Map<String, GroupConfig> groups = new HashMap<>();

	public int maxDepth = ConfigDefaults.MAX_DEPTH;
	public boolean continueAfterError = ConfigDefaults.IGNORE_ERRORS;

	public static Config getInstance() {
		return config;
	}

	/**
	 * Clear (reset) all configuration values
	 */
	public void clear() {
		services.clear();
		groups.clear();
		maxDepth = ConfigDefaults.MAX_DEPTH;
		continueAfterError = ConfigDefaults.IGNORE_ERRORS;
	}

	/**
	 * Adds a single service configuration to the services array (hashmap)
	 *
	 * @param config the config for the service to be added
	 * @return this
	 */
	public Config addService(@NotNull ServiceConfig config) {
		services.putIfAbsent(config.getName(), config);
		return this;
	}

	/**
	 * Adds multiple services configuration to the services array (hashmap)
	 *
	 * @param configs list of services to be added
	 * @return this
	 */
	public Config addAllServices(@NotNull List<ServiceConfig> configs) {
		configs.forEach(this::addService);
		return this;
	}

	/**
	 * Adds a single group configuration to the service groups array (hashmap)
	 *
	 * @param config the service group to be added
	 * @return this
	 */
	public Config addGroup(@NotNull GroupConfig config) {
		groups.putIfAbsent(config.getName(), config);
		return this;
	}

	/**
	 * Adds multiple groups configuration to the service groups array (hashmap)
	 *
	 * @param configs list of service groups to be added
	 * @return this
	 */
	public Config addAllGroups(@NotNull List<GroupConfig> configs) {
		configs.forEach(this::addGroup);
		return this;
	}

	/**
	 * Set the max depth of the dependency graph
	 *
	 * @param maxDepth max depth
	 * @return this
	 */
	public Config setMaxDepth(int maxDepth) {
		this.maxDepth = maxDepth;
		return this;
	}

	/**
	 * Set the "continue after error" flag
	 *
	 * @param continueAfterError value of the flag
	 * @return this
	 */
	public Config setContinueAfterError(boolean continueAfterError) {
		this.continueAfterError = continueAfterError;
		return this;
	}

	@NotNull
	public Map<String, ServiceConfig> getServices() {
		return services;
	}

	@NotNull
	public Map<String, GroupConfig> getGroups() {
		return groups;
	}

	public int getMaxDepth() {
		return maxDepth;
	}

	public boolean isContinueAfterError() {
		return continueAfterError;
	}
}
