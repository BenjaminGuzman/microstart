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

import org.apache.commons.cli.*;
import org.everit.json.schema.ValidationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.management.InstanceAlreadyExistsException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Microstart {
	public static final Logger LOGGER = Logger.getLogger(Microstart.class.getName());
	public static final boolean IS_WINDOWS = System.getProperty("os.name").contains("win");

	public static final String DEFAULT_CONFIG_FILE = "microstart.json";

	public static void main(String... args) {
		System.out.println(
			"Micro start version: " + Microstart.class.getPackage().getImplementationVersion() + "\n" +
				"Copyright (c) 2021. Benjamín Antonio Velasco Guzmán\n" +
				"License GPLv3: GNU GPL version 3 <http://gnu.org/licenses/gpl.html>\n" +
				"This is free software: you are free to change and redistribute it.\n"
		);

		CommandLine cli = parseCLIArgs(args);
		if (cli == null)
			return;

		if (cli.hasOption("help")) {
			HelpFormatter helpFormatter = new HelpFormatter();
			helpFormatter.printHelp("java -jar <jar name>", getCLIOptions());
			return;
		}

		String configFile = cli.hasOption("config") ? cli.getOptionValue("config") : DEFAULT_CONFIG_FILE;

		// load configuration
		try {
			new ConfigLoader(configFile);
		} catch (ValidationException e) {
			System.out.println("Configuration file is invalid. Errors are these:");
			e.getCausingExceptions()
				.stream()
				.map(ValidationException::getMessage)
				.forEach(System.out::println);
			return;
		} catch (FileNotFoundException | NoSuchFileException e) {
			System.out.println(
				"Config file " + configFile + " doesn't exist. Absolute path: " +
					new File(configFile).getAbsolutePath()
			);
			return;
		} catch (IOException e) {
			LOGGER.log(
				Level.SEVERE,
				"Exception while reading config file: " + configFile,
				e
			);
			return;
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.severe(e.getMessage());
			return;
		}

		// start command line
		try {
			new CLI(cli.getOptionValue("input")).run();
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Shouldn't instantiate CLI more than once!", e);
		} finally { // the application is exiting due to breakage of the cli loop
			ServiceGroup.getGroups().forEach(ServiceGroup::shutdownNow);
		}
	}

	@NotNull
	private static Options getCLIOptions() {
		Options options = new Options();
		options.addOption(
			"c",
			"config",
			true,
			"Path to the json config file. Default: " + DEFAULT_CONFIG_FILE
		);
		options.addOption(
			"i",
			"input",
			true,
			"Command(s) to be executed by microstart CLI. Example: \"start group <service group name>\""
		);
		options.addOption("h", "help", false, "Print this message");

		return options;
	}

	@Nullable
	private static CommandLine parseCLIArgs(String... args) {
		Options options = getCLIOptions();

		try {
			return new DefaultParser().parse(options, args);
		} catch (ParseException e) {
			if (e instanceof UnrecognizedOptionException)
				System.out.println(e.getMessage());
			else if (e instanceof MissingArgumentException)
				System.out.println(e.getMessage());
			else
				LOGGER.log(Level.SEVERE, "😱 Exception while parsing CLI options", e);
		}
		return null;
	}

	/**
	 * Tries to stop all running process that may have been run before
	 *
	 * @param forcibly if true, {@link Process#destroyForcibly()} will be used to destroy the process, if false,
	 *                 {@link Process#destroy()} will be used
	 */
	public static void destroyProcesses(boolean forcibly) {
		Service.services().forEach(service -> {
			Process proc = service.getProc();
			if (proc != null && proc.isAlive()) // stop all processes that are alive
				if (forcibly)
					proc.destroyForcibly();
				else
					proc.destroy();
		});
	}

	/**
	 * Blocks until all processes have finished.
	 * <p>
	 * This is a complement to {@link #destroyProcesses(boolean)}. You may want to call that first
	 *
	 * @see #destroyProcesses(boolean)
	 */
	public static void waitForProcesses() {
		Service.services().forEach(service -> {
			Process proc = service.getProc();
			if (proc != null) { // stop all processes that are alive
				try {
					proc.waitFor(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					LOGGER.log(
						Level.WARNING,
						"Exception produced while waiting for process in service " + service.getConfig()
							.getColorizedName(),
						e
					);
				}
			}
		});
	}
}
