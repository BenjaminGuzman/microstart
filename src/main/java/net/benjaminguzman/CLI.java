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

import net.benjaminguzman.exceptions.CircularDependencyException;
import net.benjaminguzman.exceptions.GroupNotFoundException;
import net.benjaminguzman.exceptions.MaxDepthExceededException;
import net.benjaminguzman.exceptions.ServiceNotFoundException;
import org.everit.json.schema.ValidationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import javax.management.InstanceAlreadyExistsException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLI implements Runnable {
	private static final Logger LOGGER = Logger.getLogger(CLI.class.getName());

	private static volatile boolean instantiated = false;

	/**
	 * Stores separately an inline command the user submitted for execution
	 * <p>
	 * For example if the user entered: {@code start admin & start editor}
	 * <p>
	 * This queue will have 2 nodes:
	 * <p>
	 * {@code start admin}
	 * <p>
	 * and
	 * <p>
	 * {@code start editor}
	 */
	private final Queue<String> cmdsQueue = new LinkedList<>();
	private final String waitingSymbol = "⏳";
	private final String readySymbol = "✔";
	private final String byeSymbol = "👋";
	private final String cmdSeparator = "&";
	private final PromptOutputStream customStdout = new PromptOutputStream(System.out)
		.setPrompt(">>> ")
		.setStatusIcon(waitingSymbol);
	/**
	 * Line with the initial commands. This is, the commands to be executed first, before reading directly from
	 * stdin
	 */
	@Nullable
	private String initialLineInput = null;

	public CLI() throws InstanceAlreadyExistsException {
		if (instantiated)
			throw new InstanceAlreadyExistsException(
				"There must exist a single instance of "
					+ this.getClass().getName() + " per application!"
			);

		instantiated = true;

		// change default stderr and stdout
		PrintStream customOutput = new PrintStream(customStdout, true);
		System.setOut(customOutput);
		System.setErr(customOutput);

		printHelp();

		setPromptWaiting(false);
	}

	public CLI(@NotNull String initialLineInput) throws InstanceAlreadyExistsException {
		this();
		if (!initialLineInput.isBlank())
			this.initialLineInput = initialLineInput;
	}

	@Override
	public void run() {
		BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));

		String input;
		boolean should_quit = false;
		try {
			if (initialLineInput != null) // process the initial line
				should_quit = processInputLine(initialLineInput);

			while (!should_quit && (input = stdinReader.readLine()) != null)
				should_quit = processInputLine(input);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "😱 Some really weird exception happened while reading from stdin",
				e);
		} finally {
			customStdout.printPrompt(byeSymbol);
		}
	}

	/**
	 * @param is_waiting if true, waiting symbol will be shown in prompt, if false, ready symbol will be shown
	 */
	private void setPromptWaiting(boolean is_waiting) {
		customStdout.printPrompt(is_waiting ? waitingSymbol : readySymbol);
	}

	/**
	 * Process a single input line
	 *
	 * @param line the line to be processed
	 * @return true if the line contained the "exit" command, false otherwise
	 */
	private boolean processInputLine(@NotNull String line) {
		setPromptWaiting(true);

		// separate multiple commands
		String[] commands = line.trim().split(cmdSeparator);
		cmdsQueue.addAll(Arrays.asList(commands));

		// execute each command, until the queue is empty or the user asks to quit
		boolean should_quit = false;
		while (!cmdsQueue.isEmpty()) {
			should_quit = processSingleCommand(cmdsQueue.poll());
			if (should_quit)
				break;
		}
		setPromptWaiting(false);

		return should_quit;
	}

	private boolean processSingleCommand(@NotNull String cmd) {
		cmd = cmd.toLowerCase().strip();

		switch (cmd) {
			case "q":
			case "exit":
			case "quit":
				return true;
			case "h":
			case "help":
				printHelp();
				return false;
			case "reload":
				reload();
				return false;
			case "load":
				loadAllServices();
				return false;
			case "":
				return false;
		}

		// deal with group commands
		if (cmd.startsWith("start group") || cmd.startsWith("group start")) {
			// notice how removing "group start" or "start group" gives the same result because both string
			// lengths are equal
			String groupName = cmd.substring("group start".length()).stripLeading();
			startGroupByName(groupName);
			return false;
		}

		if (cmd.startsWith("stop group") || cmd.startsWith("group stop")) {
			String groupName = cmd.substring("group stop".length()).stripLeading();
			stopGroupByName(groupName);
			return false;
		}

		// deal with singleton service commands
		if (cmd.startsWith("start")) {
			String serviceName = cmd.substring("start".length()).stripLeading();
			startServiceByName(serviceName);
			return false;
		}

		if (cmd.startsWith("stop")) {
			String serviceName = cmd.substring("stop".length()).stripLeading();
			stopServiceByName(serviceName);
			return false;
		}

		if (cmd.startsWith("status")) {
			String serviceName = cmd.substring("status".length()).stripLeading();
			serviceStatusByName(serviceName);
			return false;
		}

		// process print command
		if (cmd.startsWith("print")) {
			String filename = cmd.substring("print".length()).stripLeading();
			try {
				assert ConfigLoader.getInstance() != null;
				printDot(filename, ConfigLoader.getInstance().load());
			} catch (ServiceNotFoundException
			         | FileNotFoundException
			         | MaxDepthExceededException
			         | GroupNotFoundException
			         | CircularDependencyException e) {
				LOGGER.log(Level.SEVERE, "Config file is invalid", e);
			}
			return false;
		}

		System.out.println("Command \"" + cmd + "\" was not understood. Type \"help\" or \"h\" to print help");
		System.out.println("Forwarding command \"" + cmd + "\" to OS...");
		try {
			// this is the same code used by OpenJDK implementation for Runtime.exec(String)
			StringTokenizer st = new StringTokenizer(cmd);
			String[] cmdarray = new String[st.countTokens()];
			for (int i = 0; st.hasMoreTokens(); i++)
				cmdarray[i] = st.nextToken();

			System.out.println("Control will also be forwarded to command.\n" +
				"When the process is dead control will return to microstart");
			new ProcessBuilder()
				.command(cmdarray)
				.inheritIO()
				.start()
				.waitFor();
		} catch (InterruptedException | IOException e) {
			LOGGER.log(Level.WARNING, "Exception encountered while executing: " + cmd, e);
		}

		return false;
	}

	private void loadAllServices() {
		try {
			ConfigLoader configLoader = ConfigLoader.getInstance();
			if (configLoader == null)
				return;

			configLoader.load()
				.getServices()
				.keySet()
				.stream()
				.filter(serviceName -> Service.forName(serviceName) == null) // only load not loaded services, otherwise an exception will be produced
				.forEach(this::loadServiceByName);
		} catch (ServiceNotFoundException | FileNotFoundException | MaxDepthExceededException |
		         GroupNotFoundException | CircularDependencyException e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Reload configuration
	 */
	private void reload() {
		try {
			ConfigLoader configLoader = ConfigLoader.getInstance();
			if (configLoader == null)
				return;

			configLoader.refresh();
			Group.clear(); // remove loaded groups
			Service.clear(); // remove loaded services

			System.out.println("Configuration successfully reloaded");
		} catch (FileNotFoundException | NoSuchFileException e) {
			System.out.println("Config file was deleted and doesn't exist anymore");
		} catch (ValidationException e) {
			ConfigLoader configLoader = ConfigLoader.getInstance();
			if (configLoader == null)
				System.out.println("Configuration file contains the following errors:");
			else
				System.out.println("Configuration file "
					+ configLoader.getConfigFile().getAbsolutePath()
					+ " contains the following errors:");

			System.out.println(e.getMessage());
			e.getCausingExceptions()
				.stream()
				.map(ValidationException::getMessage)
				.forEach(System.out::println);
		} catch (MaxDepthExceededException | GroupNotFoundException | IOException | CircularDependencyException |
			 InstanceAlreadyExistsException | ServiceNotFoundException e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * Loads a NOT loaded yet group by its name.
	 * Call this method only if {@link Group#forName(String)} returned null
	 *
	 * @param groupName group name
	 * @return the group or null in case an exception was encountered
	 */
	@Nullable
	private Group loadGroupByName(@NotNull String groupName) {
		Group group = null;
		try {
			assert ConfigLoader.getInstance() != null;
			group = new Group(
				ConfigLoader.getInstance().loadGroupConfig(groupName)
			);
		} catch (MaxDepthExceededException | GroupNotFoundException | CircularDependencyException |
		         FileNotFoundException | ServiceNotFoundException e) {
			System.out.println("Couldn't load group \"" + groupName + "\"");
			System.out.println(e.getMessage());
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Programming error❗", e);
		}
		return group;
	}

	private void startGroupByName(@NotNull String groupName) {
		Group group = Group.forName(groupName);

		if (group == null) {// group has not been loaded. Load it
			group = loadGroupByName(groupName);
			if (group == null) // group couldn't be successfully loaded
				return;
		}

		// by now, the group has been loaded
		if (!group.isUp()) // if the group is not up, try to start it
			try {
				group.start(); // start and block until it has started
			} catch (InstanceAlreadyExistsException e) {
				LOGGER.log(Level.SEVERE, "Programming error❗", e);
			}
		else
			System.out.println("Group \"" + groupName + "\" is already running");
	}

	private void stopGroupByName(@NotNull String groupName) {
		Group group = Group.forName(groupName);

		if (group == null) { // group has not been loaded, there is nothing to do
			System.out.println("Group " + groupName + " has not been loaded");
			return;
		}

		// the group has been loaded
		if (group.isUp()) // if the group is up, stop it
			group.shutdownNow();
		else
			System.out.println("Group \"" + groupName + "\" has been loaded but it is not running");
	}

	/**
	 * Loads a NOT loaded yet service by its name.
	 * Call this method only if {@link Service#forName(String)} returned null
	 *
	 * @param serviceName service name
	 * @return the service or null in case an exception was encountered
	 */
	@Nullable
	private Service loadServiceByName(@NotNull String serviceName) {
		Service service = null;
		try {
			assert ConfigLoader.getInstance() != null;
			ServiceConfig serviceConfig =
				ConfigLoader.getInstance().loadServiceConfig(serviceName);
			service = new Service(serviceConfig, new HashMap<>(), (s, e) -> {});
		} catch (FileNotFoundException | ServiceNotFoundException e) {
			System.out.println(e.getMessage());
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Programming error❗", e);
		}
		return service;
	}

	private void startServiceByName(@NotNull String serviceName) {
		Service service = Service.forName(serviceName);

		if (service == null) { // service has not been loaded. Load it
			service = loadServiceByName(serviceName);
			if (service == null) // service couldn't be successfully loaded
				return;
			System.out.println("Service " + service.getConfig().getColorizedName() + " loaded");
		}

		// by now, the service has been loaded
		if (service.getStatus().isRunning()) {
			System.out.println(
				"Service " + service.getConfig().getColorizedName() + " can't be run now." +
					" Current status: " + service.getStatus()
			);
			return;
		}

		System.out.println("Starting " + service.getConfig().getColorizedName() + " asynchronously...");
		new ServiceThreadFactory().newThread(service).start();
	}

	private void stopServiceByName(@NotNull String serviceName) {
		if (serviceName.isBlank()) {
			System.out.println("No service name was provided");
			System.out.println("To see a list of available services use the status command");
			return;
		}

		Service service = Service.forName(serviceName);

		if (service == null) { // service has not been loaded, therefore it is not running
			System.out.println("Service " + serviceName + " hasn't been loaded and it can't be stopped");
			return;
		}

		if (service.getStatus() == ServiceStatus.STARTED) {
			service.destroyProc();
			return;
		}

		System.out.println(
			"Service " + service.getConfig().getColorizedName() + " can't be requested to be stopped" +
				" because it hasn't been started. Current status: " + service.getStatus()
		);
	}

	private void serviceStatusByName(@NotNull String serviceName) {
		if (serviceName.isBlank()) { // show status for all services
			if (Service.getServices().isEmpty()) {
				System.out.println("No service has been loaded. Try the load command");
				return;
			}

			// maximum width for all service names
			int max_width = Service.getServices().stream()
				.map(service -> service.getConfig().getName())
				.mapToInt(String::length)
				.max()
				.orElse(30);

			// pretty print all service names and their status
			StringBuilder strBuilder = new StringBuilder();
			for (Service service : Service.getServices()) {
				ServiceStatus serviceStatus = service.getStatus();
				int serviceNameLength = service.getConfig().getName().length();
				String spaces = " ".repeat(max_width - serviceNameLength);
				strBuilder.append(service.getConfig().getColorizedName())
					.append(spaces)
					.append("  ")
					.append(serviceStatus);

				if (serviceStatus == ServiceStatus.STARTED) {
					assert service.getProc() != null;
					strBuilder.append(" (pid: ")
						.append(service.getProc().pid())
						.append(")");
				}

				strBuilder.append('\n');
			}
			strBuilder.deleteCharAt(strBuilder.length() - 1); // prompt output library will add the linefeed
			System.out.println(strBuilder);

			// %-15s -> left aligned 15 char-width string
			/*String format = "%-" + max_width + "s - %S%n";

			Service.getServices().forEach(service -> System.out.printf(
				format,
				service.getConfig().getColorizedName(),
				service.getStatus()
			));
			// This actually doesn't work. Probably because printf has problems handling the ascii escape codes
			 */

			return;
		}

		Service service = Service.forName(serviceName);

		if (service == null) { // service has not been loaded, therefore it is not running
			System.out.println("Service " + serviceName + " hasn't been loaded");
			return;
		}

		System.out.println(service.getConfig().getColorizedName() + "  " + service.getStatus());
	}

	private void printDot(@NotNull String filename, @NotNull Config config) {
		if (filename.startsWith("\"")) // remove "" from the filename if they exist
			filename = filename.substring(1, filename.length() - 1);

		String dotCode = "";
		try {
			dotCode = new ConfigToDot(new ConfigToDot.Builder(config)).convert();
			Files.writeString(Path.of(filename), dotCode);
			String cmd = CommandLine.Help.Ansi.ON.string("@|bold dot -Tsvg " + filename + "|@");
			System.out.println(
				"Dot code has been written to " + filename
					+ "\nRun " + cmd + " to obtain a nice svg image"
			);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Couldn't save generated dot code into " + filename, e);
			System.out.println("Generated dot code is:\n" + dotCode);
		}
	}

	private void printHelp() {
		String help = "CLI prompt statuses:\n" +
			" - " + readySymbol + ": Service has started and can read commands\n" +
			" - " + waitingSymbol + ": Waiting a service or group to be started. Can't execute " +
			"commands\n" +
			" - " + byeSymbol + ": Exiting the application. Bye bye\n" +
			'\n' +
			"Available commands:\n" +
			" - (group start|start group) <group name>. Start a group service.\n" +
			"   The group name is the one you defined in the config file\n" +
			" - (start|stop) <service name>. Start or stop a singleton service\n" +
			" - status [<service name>]. Query the status of a particular service\n" +
			"   or all services if service name is not provided\n" +
			" - load. Load all services which haven't been loaded yet.\n" +
			"   Useful to validate config. It may produce duplicated output\n" +
			" - reload. Reload configuration from configuration file.\n" +
			" - print <filename> Convert the configuration to dot (graphviz) code and write it into the " +
			"specified filename\n" +
			"   Useful to obtain an overview of microservices dependency graph\n" +
			" - (quit|exit|q). Exit the application (all started processes will be stopped)\n" +
			" - (help|h). Print this help\n" +
			'\n' +
			"You can input multiple commands if you separate them by '&'.\n" +
			"Example: \"group start <group name> & status <group name>\"\n" +
			"They'll execute sequentially";
		System.out.println(help);
	}
}
