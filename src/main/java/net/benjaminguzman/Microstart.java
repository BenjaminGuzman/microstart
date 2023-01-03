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

import org.everit.json.schema.ValidationException;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import javax.management.InstanceAlreadyExistsException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@CommandLine.Command(
	name = "microstart",
	description = "Start processes groups with dependencies in a single command",
	version = "microstart v0.8.1",
	header = "Copyright (c) 2021. Benjamín Antonio Velasco Guzmán\n" +
		"This program comes with ABSOLUTELY NO WARRANTY.\n" +
		"This is free software, and you are welcome to redistribute it\n" +
		"under certain conditions.\n" +
		"License GPLv3: GNU GPL version 3 <http://gnu.org/licenses/gpl.html>\n",
	mixinStandardHelpOptions = true
)
public class Microstart implements Runnable {
	public static final Logger LOGGER = Logger.getLogger(Microstart.class.getName());
	public static final boolean IS_WINDOWS = System.getProperty("os.name").contains("win");

	@CommandLine.Option(
		names = {"-c", "--config"},
		description = "Path to the configuration file",
		defaultValue = "microstart.yml"
	)
	private String configFile;

	@CommandLine.Option(
		names = {"-i", "--input"},
		description = "Command(s) to be executed by microstart CLI. Example: \"start <service name>\""
	)
	private String initialInput;

	@CommandLine.Option(
		names = {"-e", "--ignore-errors"},
		description = "Tells if execution should be stopped when a service notifies an error has " +
			"happened. Overrides ignoreErrors key in config file"
	)
	private boolean ignoreErrors;

	/**
	 * If true, and an error occurred while running a service or group, the application will continue execution
	 */
	public static boolean IGNORE_ERRORS;

	public static void main(String... args) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-7s] [%1$tF %1$tT] %5$s %n");

		// when jvm is shutting down, kill all its children processes.
		// Recall this processes will be the ones started by running groups or singleton services.
		// Killing all children recursively is good because this way it is ensured there are no remaining
		// orphan/dangling processes that may have been started by other service, e.g.
		// microstart -> process 1 (defined in config) -> process 2 -> process 3
		// with this line of code process 3, process 2 and process 1 will be stopped
		// instead of just stopping the direct child process 1, which would happen normally if jvm exits and no
		// shutdown hook is configured
		Runtime.getRuntime().addShutdownHook(new Thread(
			() -> ProcessHandle.current().children().forEach(Microstart::destroyChildrenProcesses)
		));

		CommandLine commandLine = new CommandLine(new Microstart());
		commandLine.execute(args);
	}

	@Override
	public void run() {
		System.out.println(
			"Copyright (c) 2021. Benjamín Antonio Velasco Guzmán\n" +
				"This program comes with ABSOLUTELY NO WARRANTY.\n" +
				"This is free software, and you are welcome to redistribute it\n" +
				"under certain conditions.\n" +
				"License GPLv3: GNU GPL version 3 <http://gnu.org/licenses/gpl.html>\n"
		);

		// load configuration
		try {
			new ConfigLoader(configFile);
		} catch (ValidationException e) {
			System.out.println("Configuration file contains the following errors:");
			System.out.println(e.getMessage());
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
		IGNORE_ERRORS = ConfigLoader.getInstance().shouldIgnoreErrors();

		if (ignoreErrors) // override value in config
			IGNORE_ERRORS = true;

		// start command line
		try {
			if (initialInput != null)
				new CLI(initialInput).run();
			else
				new CLI().run();
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Shouldn't instantiate CLI more than once!", e);
		} finally { // the application is exiting due to breakage of the cli loop
			// This won't execute if SIGINT is received, therefore it is convenient to ask if
			// this should be inside a shutdown hook?
			// it seems the JVM successfully handles child process destruction when SIGINT is received
			// so let's hope it is true for any architecture and 🤞 there are no dangling process after
			// exit
			Group.getGroups().stream()
				.peek(Group::shutdownNow)
				.forEach(group -> {
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
			Service.getServices().forEach(Service::stop);
		}
	}

	/**
	 * Stop all children processes for the given process recursively
	 * <p>
	 * Once all children have been stopped, parent process is also stopped
	 *
	 * @param parentProc parent process whose children will be tried to be stopped
	 */
	public static void destroyChildrenProcesses(@NotNull ProcessHandle parentProc) {
		// stop all children
		parentProc.children().forEach(Microstart::destroyChildrenProcesses);

		// stop parent process
		parentProc.destroy();
		// should destroyForcibly() be used? let's hope the user knows what he/she is doing and there is no
		// need to use destroyForcibly() which will send SIGKILL or something similar to the process,
		// which is no good because process won't clean resources or handle shutdown hooks
	}
}
