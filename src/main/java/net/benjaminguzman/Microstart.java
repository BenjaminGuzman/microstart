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

	/**
	 * If true, and an error occurred while running a service or group
	 * <p>
	 * The application should continue execution
	 */
	public static boolean CONTINUE_AFTER_ERROR = true;

	public static final String DEFAULT_CONFIG_FILE = "microstart.json";

	public static void main(String... args) {
		System.out.println(
			"Micro start version: " + Microstart.class.getPackage().getImplementationVersion() + "\n" +
				"Copyright (c) 2021. Benjamín Antonio Velasco Guzmán\n" +
				"This program comes with ABSOLUTELY NO WARRANTY.\n" +
				"This is free software, and you are welcome to redistribute it\n" +
				"under certain conditions.\n" +
				"License GPLv3: GNU GPL version 3 <http://gnu.org/licenses/gpl.html>\n"
		);
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-7s] [%1$tF %1$tT] %5$s %n");

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
		assert ConfigLoader.getInstance() != null;
		CONTINUE_AFTER_ERROR = ConfigLoader.getInstance().shouldContinueAfterError();

		// start command line
		try {
			String inputLine = cli.getOptionValue("input");
			if (inputLine != null)
				new CLI(inputLine).run();
			else
				new CLI().run();
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Shouldn't instantiate CLI more than once!", e);
		} finally { // the application is exiting due to breakage of the cli loop
			// This won't execute if SIGINT is received, therefore it is convenient to ask
			// this should be inside a shutdown hook?
			// it seems the JVM successfully handles child process destruction when SIGINT is received
			// so let's hope it is true for any architecture and 🤞 there are no dangling process after
			// exit
			Group.getGroups().forEach(Group::shutdownNow);
			Group.getGroups().forEach(group -> {
				try {
					group.awaitTermination(5, TimeUnit.SECONDS);
				} catch (InterruptedException e) {
					LOGGER.warning(
						"Group "
							+ group.getConfig().getName()
							+ " couldn't be gracefully shut down"
					);
				}
			});

			// also stop any singleton services
			Service.getServices().forEach(service -> {
				if (service.getProc() == null)
					return;
				service.getProc().destroy();
				boolean graceful_shutdown = false;
				try {
					graceful_shutdown = service.getProc().waitFor(500, TimeUnit.MILLISECONDS);
				} catch (InterruptedException ignored) {
					service.getProc().destroyForcibly();
				}

				if (!graceful_shutdown) {
					System.out.println(
						"Process " + service.getConfig().getColorizedName()
							+ " didn't shutdown gracefully, trying to destroy it forcibly"
					);
					service.getProc().destroyForcibly();
				}
			});
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
		Service.getServices().forEach(service -> {
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
		Service.getServices().forEach(service -> {
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
