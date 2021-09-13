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

import java.util.Collections;
import java.util.List;

public class GroupConfig {
	@NotNull
	private String name = "Unnamed group";

	@NotNull
	private List<String> aliases = Collections.emptyList();

	@NotNull
	private List<GroupConfig> dependenciesConfigs = Collections.emptyList();

	@NotNull
	private List<ServiceConfig> servicesConfigs = Collections.emptyList();

	@NotNull
	public String getName() {
		return name;
	}

	public GroupConfig setName(@NotNull String name) {
		this.name = name;
		return this;
	}

	@NotNull
	public List<String> getAliases() {
		return aliases;
	}

	public GroupConfig setAliases(@NotNull List<String> aliases) {
		this.aliases = aliases;
		return this;
	}

	@NotNull
	public List<GroupConfig> getDependenciesConfigs() {
		return dependenciesConfigs;
	}

	public GroupConfig setDependenciesConfigs(@NotNull List<GroupConfig> dependenciesConfigs) {
		this.dependenciesConfigs = dependenciesConfigs;
		return this;
	}

	@NotNull
	public List<ServiceConfig> getServicesConfigs() {
		return servicesConfigs;
	}

	public GroupConfig setServicesConfigs(@NotNull List<ServiceConfig> servicesConfigs) {
		this.servicesConfigs = servicesConfigs;
		return this;
	}

	@Override
	public String toString() {
		return "ServiceGroupConfig{" +
			"name='" + name + '\'' +
			", aliases=" + aliases +
			", dependencies=" + dependenciesConfigs +
			", servicesConfigs=" + servicesConfigs +
			'}';
	}
}
