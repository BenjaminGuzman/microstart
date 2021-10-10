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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class ConfigToDot {
	@NotNull
	private final Builder opts;

	/**
	 * Indentation string of level 1
	 */
	private final String indentStr;

	/**
	 * Indentation string of level 2
	 */
	private final String indentStr2;

	public ConfigToDot(@NotNull Builder options) {
		opts = options;
		indentStr = String.valueOf(opts.indentChar).repeat(opts.indentSize);
		indentStr2 = String.valueOf(opts.indentChar).repeat(2 * opts.indentSize);
	}

	/**
	 * Convert the JSON configuration to dot code
	 *
	 * @return the dot code
	 */
	public String convert() {
		StringBuilder builder = new StringBuilder();

		// open a directed graph
		builder.append("digraph {").append(System.lineSeparator());

		// compound true is important to connect subgraphs
		builder.append(indentStr).append("compound=true;").append(System.lineSeparator());

		// add separation between subgraphs
		builder.append(indentStr).append("rank=same;").append(System.lineSeparator());
		builder.append(indentStr).append("ranksep=1;").append(System.lineSeparator());

		// print all the subgraphs
		Collection<GroupConfig> groups = opts.config.getGroups().values();
		for (GroupConfig group : groups)
			printSubgraph(group, builder);

		// connect all the subgraphs
		for (GroupConfig group : groups)
			connectSubgraphs(group, builder);

		// close the directed graph
		builder.append("}").append(System.lineSeparator());

		return builder.toString();
	}

	/**
	 * Convert and save the JSON configuration into dot code and save it to the specified location
	 *
	 * @param outPath output path where the dot code will be written
	 * @return the dot code
	 * @throws IOException any exception thrown by {@link Files#writeString(Path, CharSequence, OpenOption...)}
	 */
	public String convertAndSave(@NotNull Path outPath) throws IOException {
		String dot = this.convert();
		Files.writeString(outPath, dot);
		return dot;
	}

	/**
	 * "Prints" a single group config as a subgraph
	 *
	 * @param groupConfig the group configuration to be converted into dot
	 * @param builder     the string builder in which the dot code will be added
	 */
	private void printSubgraph(@NotNull GroupConfig groupConfig, @NotNull StringBuilder builder) {
		int group_hash_code = groupConfig.getName().hashCode() & 0x7fff_ffff; // remove sign bit

		// add subgraph
		builder.append(indentStr)
			.append("subgraph cluster_")
			.append(group_hash_code)
			.append(" {")
			.append(System.lineSeparator());

		// add label to subgraph
		builder.append(indentStr2).append("label=\"")
			.append(groupConfig.getName())
			.append("\";")
			.append(System.lineSeparator())
			.append(indentStr2).append("color=blue;") // add border color to subgraph
			.append(System.lineSeparator());

		// add group's services
		groupConfig.getServicesConfigs().forEach(serviceConfig -> builder.append(indentStr2)
			.append('"')
			.append(serviceConfig.getName()).append(group_hash_code)
			.append("\"")
			.append(" [label=<")
			.append(serviceConfig.getName())
			.append("<br/><font point-size=\"10\">")
			.append(String.join(", ", serviceConfig.getAliases()))
			.append("</font>>];")
			.append(System.lineSeparator())
		);

		// close the subgraph
		builder.append(indentStr).append("}").append(System.lineSeparator());
	}

	/**
	 * Connects all subgraph. This method should be called after {@link #printSubgraph(GroupConfig, StringBuilder)}
	 *
	 * @param groupConfig the group configuration
	 * @param builder     the string builder in which the dot code will be added
	 */
	private void connectSubgraphs(@NotNull GroupConfig groupConfig, @NotNull StringBuilder builder) {
		int group_hash_code = groupConfig.getName().hashCode() & 0x7fff_ffff;

		List<GroupConfig> deps = groupConfig.getDependenciesConfigs();
		if (deps.isEmpty()) // this group doesn't have dependencies
			return;

		// connection must be made between nodes in cluster, not between clusters
		// that's how dot works 😕
		String serviceName = groupConfig.getServicesConfigs().get(0).getName();

		// add all dependencies
		int dep_hash_code;
		for (GroupConfig dep : deps) {
			dep_hash_code = dep.getName().hashCode() & 0x7fff_ffff;
			builder.append(indentStr)
				.append('"')
				.append(dep.getServicesConfigs().get(0).getName())
				.append(dep_hash_code)
				.append('"')
				.append(" -> ")
				.append('"')
				.append(serviceName)
				.append(group_hash_code)
				.append('"')
				.append(" [lhead=cluster_")
				.append(group_hash_code) // remove sign bit
				.append(", ltail=cluster_")
				.append(dep_hash_code) // remove sign bit
				.append("];")
				.append(System.lineSeparator());
		}
	}

	public static class Builder {
		@NotNull
		private Config config;

		private char indentChar = ' ';
		private int indentSize = 4;

		public Builder(@NotNull Config config) {
			this.config = config;
		}

		/**
		 * @see #setConfig(Config)
		 */
		@NotNull
		public Config getConfig() {
			return config;
		}

		/**
		 * Set the configuration for which the dot file will be generated
		 *
		 * @param config the configuration
		 */
		public Builder setConfig(Config config) {
			this.config = config;
			return this;
		}

		/**
		 * @see #setIndentChar(char)
		 */
		public char getIndentChar() {
			return indentChar;
		}

		/**
		 * Set the indentation character
		 * <p>
		 * Default: ' '
		 *
		 * @param indentChar indentation character
		 */
		public Builder setIndentChar(char indentChar) {
			this.indentChar = indentChar;
			return this;
		}

		/**
		 * @see #setIndentSize(int)
		 */
		public int getIndentSize() {
			return indentSize;
		}

		/**
		 * Set the indent size, i.e. how many {@link #indentChar} will be printed per indent
		 * <p>
		 * Default: 4
		 *
		 * @param indentSize number, must be non-negative
		 * @throws IllegalArgumentException if the given indent size is negative
		 */
		public Builder setIndentSize(int indentSize) {
			if (indentSize < 0)
				throw new IllegalArgumentException("Can't have an indentation size of " + indentSize);

			this.indentSize = indentSize;
			return this;
		}
	}
}
