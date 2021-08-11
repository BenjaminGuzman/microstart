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

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wrapper class for all the configurations needed to run a microservice
 */
public class ServiceConfig {
	/**
	 * Service name
	 *
	 * It is the name that will be printed to stdout
	 */
	@NotNull
	private String name = "Unnamed service";

	/**
	 * Working directory for the service.
	 *
	 * Used when starting the service with {@link #startCmd}
	 */
	@NotNull
	private File workingDirectory = Paths.get(".").toFile();

	@NotNull
	private List<Pattern> upPatterns = Collections.emptyList();

	@NotNull
	private List<Pattern> errorPatterns = Collections.emptyList();

	@NotNull
	private List<String> aliases = Collections.emptyList();

	/**
	 * ASCII color
	 */
	@NotNull
	private String asciiColor = "";

	@NotNull
	private String[] startCmd = {"npm", "run", "start"};

	@NotNull
	private String colorizedName = asciiColor + name + "\033[0m";

	private String colorizedErrorName = "\033[41mERROR " + colorizedName;

	/**
	 * @return service name used to identify unequivocally the service within the application
	 */
	@NotNull
	public String getName() {
		return name;
	}

	public ServiceConfig setName(@NotNull String name) {
		this.name = name;
		this.setColorizedName();
		return this;
	}

	/**
	 * Working directory
	 *
	 * @see #getStartCmd()
	 */
	@NotNull
	public File getWorkingDirectory() {
		return workingDirectory;
	}

	/**
	 * @see #getWorkingDirectory()
	 */
	public ServiceConfig setWorkingDirectory(@NotNull File workingDirectory) {
		this.workingDirectory = workingDirectory;
		return this;
	}

	/**
	 * Get the list of patterns to indicate the service is up
	 *
	 * If the text inside service (process) stdout matches one of these patterns, service will be considered to be
	 * up
	 * @return the list of patterns
	 * @see #setUpPatterns(List)
	 */
	@NotNull
	public List<Pattern> getUpPatterns() {
		return upPatterns;
	}

	/**
	 * @see #getUpPatterns()
	 */
	public ServiceConfig setUpPatterns(@NotNull List<Pattern> upPatterns) {
		this.upPatterns = upPatterns;
		return this;
	}

	/**
	 * Get the list of patterns to indicate an error occurred within the service
	 *
	 * If the text inside service (process) stdout matches one of these patterns, an error will be considered to
	 * have happened
	 * @return the list of patterns
	 * @see #setErrorPatterns(List)
	 */
	@NotNull
	public List<Pattern> getErrorPatterns() {
		return errorPatterns;
	}

	/**
	 * @see #getErrorPatterns()
	 */
	public ServiceConfig setErrorPatterns(@NotNull List<Pattern> errorPatterns) {
		this.errorPatterns = errorPatterns;
		return this;
	}

	/**
	 * Get the aliases for this service name
	 *
	 * Unlike service name, these aliases may not be unique throughout the application
	 *
	 * @see #getName()
	 */
	@NotNull
	public List<String> getAliases() {
		return aliases;
	}

	/**
	 * @see #getAliases()
	 */
	public ServiceConfig setAliases(@NotNull List<String> aliases) {
		this.aliases = aliases;
		return this;
	}

	@NotNull
	public String getAsciiColor() {
		return asciiColor;
	}

	public ServiceConfig setAsciiColor(String asciiColor) {
		this.asciiColor = asciiColor;
		this.setColorizedName();
		return this;
	}

	/**
	 * Get the start command (with arguments)
	 *
	 * Some start commands may be dependent on the {@link #workingDirectory}
	 * (for example "npm", "run", "start" won't work if working directory is not right)
	 *
	 * @see #setStartCmd(String)
	 * @return the command to execute in order to start the service. It'll be system dependent, for example, in
	 * windows platforms it may return {"cmd", "/c", real command}. You can pass this to
	 * {@link ProcessBuilder#command(String...)}
	 */
	public String[] getStartCmd() {
		return startCmd;
	}

	/**
	 * @param startCmd the command to execute in order to start the service
	 * @see #getStartCmd()
	 */
	public ServiceConfig setStartCmd(@NotNull String startCmd) {
		this.startCmd = new String[]{
			Microstart.IS_WINDOWS ? "cmd" : "sh",
			Microstart.IS_WINDOWS ? "/c" : "-c",
			startCmd
		};
		return this;
	}

	/**
	 * Same as {@link #getName()} but with {@link #getAsciiColor()} at the beginning and
	 * "\033[0m" (reset ASCII sequence) at the end
	 *
	 * You can safely print this to stdout
	 */
	@NotNull
	public String getColorizedName() {
		return colorizedName;
	}

	/**
	 * Same as {@link #getColorizedName()} but with {@link #getAsciiColor()} in red background to indicate an error
	 * has happened
	 *
	 * You can safely print this to stdout
	 */
	@NotNull
	public String getColorizedErrorName() {
		return colorizedErrorName;
	}

	/**
	 * Set the colorized name
	 */
	private void setColorizedName() {
		colorizedName = asciiColor + name + "\033[0m";
		setColorizedErrorName();
	}

	private void setColorizedErrorName() {
		colorizedErrorName = "\033[41mERROR " + colorizedName;
	}

	@Override
	public String toString() {
		return "ServiceConfig{" +
			"name='" + name + '\'' +
			", workingDirectory=" + workingDirectory +
			", upPatterns=" + upPatterns +
			", errorPatterns=" + errorPatterns +
			", aliases=" + aliases +
			", asciiColor='" + asciiColor + '\'' +
			", startCmd=" + Arrays.toString(startCmd) +
			", colorizedName='" + colorizedName + '\'' +
			'}';
	}
}
