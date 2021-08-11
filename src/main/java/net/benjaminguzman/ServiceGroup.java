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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class containing a group of services to run
 *
 * This class can also start the list group and execute it
 */
public class ServiceGroup {
	/**
	 * Map of names and services
	 * <p>
	 * Names include also aliases, so many keys (names) can point to the same value (service)
	 * <p>
	 * This contains all the available services throughout the application
	 */
	@NotNull
	private final static Map<String, Service> services = new HashMap<>();

	//@NotNull
	private List<ServiceConfig> group;
}
