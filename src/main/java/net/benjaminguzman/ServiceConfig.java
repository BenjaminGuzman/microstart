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
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.awt.*;
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
	 * Multiply a 256 RGB color component by this factor to obtain it in scale from 0 to 5
	 */
	public static float COLOR_NORM_FACTOR = 5f / 256;

	/**
	 * Service name
	 * <p>
	 * It is the name that will be printed to stdout
	 * <p>
	 * It should be unique throughout the whole application
	 * <p>
	 * {@link Service} ensures that uniqueness
	 */
	@NotNull
	private String name = "Unnamed service";

	/**
	 * Working directory for the service.
	 * <p>
	 * Used when starting the service with {@link #startCmd}
	 */
	@NotNull
	private File workingDirectory = Paths.get(".").toFile();

	@NotNull
	private List<Pattern> startedPatterns = Collections.emptyList();

	@NotNull
	private List<Pattern> errorPatterns = Collections.emptyList();

	@NotNull
	private List<String> aliases = Collections.emptyList();

	/**
	 * This color will be used to colorize the service name
	 */
	@NotNull
	private Color color = Color.WHITE;

	@NotNull
	private String[] startCmd = {"npm", "run", "start"};

	@NotNull
	private String[] stopCmd = {"SIGTERM"};

	private int stopTimeout = 5;

	@Nullable
	private File stdin;

	@Nullable
	private File stopStdin;

	@NotNull
	private String colorizedName = CommandLine.Help.Ansi.AUTO.string("@|white " + name + "|@");

	private String colorizedErrorName = CommandLine.Help.Ansi.AUTO.string("@|red,blink,bold " + name + "|@");

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
	 * Get the list of patterns to indicate the service has started (is up)
	 * <p>
	 * If the text inside service (process) stdout matches one of these patterns, service will be considered to
	 * have been started
	 *
	 * @return the list of patterns
	 * @see #setStartedPatterns(List)
	 */
	@NotNull
	public List<Pattern> getStartedPatterns() {
		return startedPatterns;
	}

	/**
	 * @see #getStartedPatterns()
	 */
	public ServiceConfig setStartedPatterns(@NotNull List<Pattern> startedPatterns) {
		this.startedPatterns = startedPatterns;
		return this;
	}

	/**
	 * Get the list of patterns to indicate an error occurred within the service
	 * <p>
	 * If the text inside service (process) stdout matches one of these patterns, an error will be considered to
	 * have happened
	 *
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
	 * <p>
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

	public ServiceConfig setColor(@NotNull Color color) {
		this.color = color;
		this.setColorizedName();
		return this;
	}

	/**
	 * Get the start command (with arguments)
	 * <p>
	 * Some start commands may be dependent on the {@link #workingDirectory}
	 * (for example "npm", "run", "start" won't work if working directory is not right)
	 *
	 * @return the command to execute in order to start the service. It'll be system dependent, for example, in
	 * windows platforms it may return {"cmd", "/c", real command}. You can pass this to
	 * {@link ProcessBuilder#command(String...)}
	 * @see #setStartCmd(String)
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
	 * Get the stop command (with arguments) to be executed on exit or when service is stopped
	 * <p>
	 * Some start commands may be dependent on the {@link #workingDirectory}
	 * (for example "npm", "run", "start" won't work if working directory is not right)
	 * <p>
	 * Command MAY NOT be an actual command but a signal name (SIGHUP, SIGTERM, SIGKILL, SIGQUIT, SIGINT) which
	 * should be sent to the process
	 *
	 * @return the command to execute in order to start the service. It'll be system dependent, for example, in
	 * windows platforms it may return {"cmd", "/c", real command}. You can pass this to
	 * {@link ProcessBuilder#command(String...)}
	 * @see #setStopCmd(String)
	 */
	public String[] getStopCmd() {
		return stopCmd;
	}

	/**
	 * @param stopCmd the command to execute or signal to send to stop a service
	 * @see #getStopCmd()
	 */
	public ServiceConfig setStopCmd(@NotNull String stopCmd) {
		String normalizedStopCmd = stopCmd.strip().toUpperCase();
		switch (normalizedStopCmd) {
			case "SIGINT":
			case "SIGTERM":
			case "SIGHUP":
			case "SIGKILL":
			case "SIGQUIT":
				this.stopCmd[0] = normalizedStopCmd;
				break;
			default:
				this.stopCmd = new String[]{
					Microstart.IS_WINDOWS ? "cmd" : "sh",
					Microstart.IS_WINDOWS ? "/c" : "-c",
					stopCmd
				};
		}
		return this;
	}

	/**
	 * Get the stop timeout (in seconds)
	 * <p>
	 * If service (process) hasn't been terminated after this number of seconds, destroy it
	 *
	 * @return timeout in seconds
	 * @see #setStopTimeout(int)
	 */
	public int getStopTimeout() {
		return stopTimeout;
	}

	/**
	 * @param timeout timeout in seconds
	 * @see #getStopTimeout()
	 */
	public ServiceConfig setStopTimeout(int timeout) {
		if (timeout <= 0)
			throw new IllegalArgumentException("stop timeout should be greater than 0");

		this.stopTimeout = timeout;
		return this;
	}

	/**
	 * @param stdin the file containing the data that will be passed to the service process' stdin start command
	 */
	public ServiceConfig setStdin(@Nullable File stdin) {
		this.stdin = stdin;
		return this;
	}

	@Nullable
	public File getStdin() {
		return stdin;
	}

	/**
	 * @param stdin the file containing the data that will be passed to the service process' stdin stop command
	 */
	public ServiceConfig setStopStdin(@Nullable File stdin) {
		this.stopStdin = stdin;
		return this;
	}

	@Nullable
	public File getStopStdin() {
		return stopStdin;
	}

	/**
	 * Same as {@link #getName()} but with colorized with ANSI scape sequences
	 * <p>
	 * You can safely print this to stdout
	 */
	@NotNull
	public String getColorizedName() {
		return colorizedName;
	}

	/**
	 * Same as {@link #getColorizedName()} but with special format to indicate an error has happened
	 * <p>
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
		// normalized rgb components in the scale 0 - 5
		// https://picocli.info/#_ansi_colors_and_styles
		int[] rgbNorm = {
			Math.round(COLOR_NORM_FACTOR * color.getRed()),
			Math.round(COLOR_NORM_FACTOR * color.getGreen()),
			Math.round(COLOR_NORM_FACTOR * color.getBlue())
		};

		String normalizedColor = "fg(" + rgbNorm[0] + ";" + rgbNorm[1] + ";" + rgbNorm[2] + ")";
		colorizedName = CommandLine.Help.Ansi.AUTO.string("@|" + normalizedColor + " " + name + "|@");
		setColorizedErrorName();
	}

	private void setColorizedErrorName() {
		colorizedErrorName = CommandLine.Help.Ansi.AUTO.string("@|red,blink,bold " + name + "|@");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ServiceConfig that = (ServiceConfig) o;
		return name.equals(that.name); // name should be unique for each service config. If the name are equal,
		// that implies object are also equal
	}

	@Override
	public int hashCode() {
		return name.hashCode(); // the name should be unique for each service config
	}

	@Override
	public String toString() {
		return "ServiceConfig{" +
			"name='" + name + '\'' +
			", workingDirectory=" + workingDirectory +
			", startedPatterns=" + startedPatterns +
			", errorPatterns=" + errorPatterns +
			", aliases=" + aliases +
			", color=" + color +
			", startCmd=" + Arrays.toString(startCmd) +
			", colorizedName='" + colorizedName + '\'' +
			", colorizedErrorName='" + colorizedErrorName + '\'' +
			'}';
	}
}
