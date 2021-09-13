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

import java.io.File;

public class ConfigToDot {
	@NotNull
	private Config config;

	@NotNull
	private File file;

	public ConfigToDot(@NotNull Config config, @NotNull File file) {
		this.config = config;
		this.file = file;
	}

	public ConfigToDot(@NotNull Config config) {
		this(config, new File("microstart.dot"));
	}

	public String convert(@NotNull Config config, @NotNull File file) {
		StringBuilder builder = new StringBuilder();

		//config.getGroups().values().;

		return builder.toString();
	}
}
