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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

		// printHelp();
		System.out.println("For help, type \"help\"");

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
			LOGGER.log(
				Level.SEVERE,
				"😱 Some really weird exception happened while reading from stdin",
				e
			);
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
		cmd = cmd.strip();
		String cmdLower = cmd.toLowerCase();

		switch (cmdLower) {
			case "q", "exit", "quit" -> {
				return true;
			}
			case "h", "help" -> {
				printHelp();
				return false;
			}
			case "reload" -> {
				reload();
				return false;
			}
			case "load" -> {
				loadAllServices();
				return false;
			}
			case "" -> {
				return false;
			}
		}

		// store the keys in a tree map sorting the keys by its length (if equal, use lexicographical order)
		// this way longer command names will be processed before shorter command names
		// e.g. "stop group" will be processed before "stop"
		Map<String, Consumer<String>> cmdsWithArg = new TreeMap<>((String a, String b) -> {
			if (a.length() == b.length())
				return a.compareTo(b);
			return b.length() - a.length();
		});

		// group commands
		cmdsWithArg.put("start group", this::startGroupByName);
		cmdsWithArg.put("group start", this::startGroupByName);
		cmdsWithArg.put("stop group", this::stopGroupByName);
		cmdsWithArg.put("group stop", this::stopGroupByName);
		cmdsWithArg.put("group status", this::groupStatusByName);
		cmdsWithArg.put("status group", this::groupStatusByName);

		// singleton service commands
		cmdsWithArg.put("start", this::startServiceByName);
		cmdsWithArg.put("stop", this::stopServiceByName);
		cmdsWithArg.put("status", this::serviceStatusByName);

		// print command
		cmdsWithArg.put("print", (String filename) -> {
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
		});

		// Process a single command that requires an argument
		// (or not, in which case the consumer should handle empty strings)
		Optional<String> cmdNameOptional = cmdsWithArg.keySet()
			.stream()
			.filter(cmdLower::startsWith)
			.findFirst();
		if (cmdNameOptional.isPresent()) { // means cmdNameOptional is actually a key of the map (therefore, valid)
			String cmdName = cmdNameOptional.get();
			String cmdArg = cmd.substring(cmdName.length()).stripLeading();
			Consumer<String> cmdConsumer = cmdsWithArg.get(cmdName);

			if (cmdArg.isBlank()) // process command that accepts empty argument
				cmdConsumer.accept(cmdArg);
			else // process each command argument
				splitBySpaces(cmdArg).forEach(cmdConsumer);
			return false;
		}

		System.out.println("Forwarding command \"" + cmd + "\" to OS...");
		try {
			// this is the same code used by OpenJDK implementation for Runtime.exec(String)
			StringTokenizer st = new StringTokenizer(cmd);
			String[] cmdarray = new String[st.countTokens()];
			for (int i = 0; st.hasMoreTokens(); i++)
				cmdarray[i] = st.nextToken();

			new ProcessBuilder()
				.command(cmdarray)
				.inheritIO()
				.start()
				.waitFor();
		} catch (InterruptedException | IOException e) {
			LOGGER.log(Level.WARNING, "Exception encountered while executing: " + cmdLower, e);
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
			printError(e.getMessage());
		}
	}

	/**
	 * Reload configuration
	 */
	private void reload() {
		boolean notSafe = Service.getServices()
			.stream()
			.anyMatch(service -> service.getStatus().isRunning());

		if (notSafe) {
			printError("It is not safe to reload since there are services running");
			return;
		}

		try {
			ConfigLoader configLoader = ConfigLoader.getInstance();
			if (configLoader == null)
				return;

			configLoader.refresh();
			Group.clear(); // remove loaded groups
			Service.clear(); // remove loaded services

			printSuccess("Configuration successfully reloaded");
		} catch (FileNotFoundException | NoSuchFileException e) {
			printError("Configuration file was deleted");
		} catch (ValidationException e) {
			ConfigLoader configLoader = ConfigLoader.getInstance();
			if (configLoader == null)
				printError("Configuration file contains the following errors:");
			else
				printError("Configuration file " +
					configLoader.getConfigFile().getAbsolutePath() +
					" contains the following errors:");

			printError(e.getMessage());
			e.getCausingExceptions()
				.stream()
				.map(ValidationException::getMessage)
				.forEach(CLI::printError);
		} catch (MaxDepthExceededException | GroupNotFoundException | IOException |
			 CircularDependencyException |
			 InstanceAlreadyExistsException | ServiceNotFoundException e) {
			printError(e.getMessage());
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
		if (isGroupNameBlank(groupName))
			return null;

		Group group = null;
		try {
			assert ConfigLoader.getInstance() != null;
			group = new Group(
				ConfigLoader.getInstance().loadGroupConfig(groupName)
			);
		} catch (MaxDepthExceededException | GroupNotFoundException | CircularDependencyException |
			 FileNotFoundException | ServiceNotFoundException e) {
			printError("Couldn't load group \"" + groupName + "\"");
			printError(e.getMessage());
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Programming error❗", e);
		}
		return group;
	}

	private void startGroupByName(@NotNull String groupName) {
		if (isGroupNameBlank(groupName))
			return;

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
			printWarning("Group \"" + groupName + "\" is already running");
	}

	private void stopGroupByName(@NotNull String groupName) {
		if (isGroupNameBlank(groupName))
			return;

		Group group = Group.forName(groupName);

		if (group == null) { // group has not been loaded, there is nothing to do
			printError("Group " + groupName + " has not been loaded");
			return;
		}

		// the group has been loaded
		if (group.isUp()) // if the group is up, stop it
			group.stop();
		else
			printWarning("Group \"" + groupName + "\" has been loaded but it is not running");
	}

	private void groupStatusByName(@NotNull String groupName) {
		if (isGroupNameBlank(groupName))
			return;

		Group group = getGroupByName(groupName);

		if (group == null) { // group has not been loaded, there is nothing to do
			printError("Group \"" + groupName + "\" has not been loaded");
			return;
		}

		GroupConfig groupConfig = group.getConfig();

		// print status for services directly on this group
		System.out.println("Group \"" + groupName + "\" status:");
		group.getConfig()
			.getServicesConfigs()
			.stream()
			.map(ServiceConfig::getName)
			.forEach(this::serviceStatusByName);

		// print status for this group dependencies
		String dependenciesStr = groupConfig.getDependenciesConfigs()
			.stream()
			.map(GroupConfig::getName)
			.collect(Collectors.joining(", "));
		if (dependenciesStr.isEmpty())
			return;

		System.out.println("Group \"" + groupName + "\" depends on: " + dependenciesStr);
		groupConfig.getDependenciesConfigs().forEach(g -> groupStatusByName(g.getName()));
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
		if (isServiceNameBlank(serviceName))
			return null;

		Service service = null;
		try {
			assert ConfigLoader.getInstance() != null;
			ServiceConfig serviceConfig =
				ConfigLoader.getInstance().loadServiceConfig(serviceName);
			service = new Service(serviceConfig, new HashMap<>(), (s, e) -> {
			});
		} catch (FileNotFoundException | ServiceNotFoundException e) {
			printError(e.getMessage());
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Programming error❗", e);
		}
		return service;
	}

	private void startServiceByName(@NotNull String serviceName) {
		if (isServiceNameBlank(serviceName))
			return;

		Service service = Service.forName(serviceName);

		if (service == null) { // service has not been loaded. Load it
			service = loadServiceByName(serviceName);
			if (service == null) // service couldn't be successfully loaded
				return;
			System.out.println("Service " + service.getConfig().getColorizedName() + " successfully loaded");
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
		new DaemonThreadFactory().newThread(service).start();
	}

	private void stopServiceByName(@NotNull String serviceName) {
		if (isServiceNameBlank(serviceName))
			return;

		Service service = Service.forName(serviceName);

		if (service == null) { // service has not been loaded, therefore it is not running
			printError("Service \"" + serviceName + "\" hasn't been loaded");
			return;
		}

		if (service.getStatus() == ServiceStatus.STARTED) {
			service.stop();
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
				printError("No service has been loaded");
				System.out.println("Try the load command");
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
			printError("Service \"" + serviceName + "\" hasn't been loaded");
			return;
		}

		System.out.println(service.getConfig().getColorizedName() + "  " + service.getStatus());
	}

	/**
	 * Check the given service name is blank and print error message if needed
	 *
	 * @param serviceName string to be validated
	 * @return true if service name is blank, false otherwise
	 */
	private boolean isServiceNameBlank(@NotNull String serviceName) {
		if (serviceName.isBlank()) {
			printError("You must provide a service name or alias");
			System.out.println("To see a list of available services use the status command");
			return true;
		}
		return false;
	}

	/**
	 * Check the given group name is blank and print error message if needed
	 *
	 * @param groupName string to be validated
	 * @return true if group name is blank, false otherwise
	 */
	private boolean isGroupNameBlank(@NotNull String groupName) {
		if (groupName.isBlank()) {
			printError("You must provide a group name or alias");
			return true;
		}
		return false;
	}

	/**
	 * Check if group exists and return it if it does exist
	 * If it doesn't exist, show error
	 * @param groupName group name
	 * @return null if group doesn't exist or, if it does exist, the actual group related to that group name
	 */
	private Group getGroupByName(@NotNull String groupName) {
		Group group = Group.forName(groupName);

		if (group == null) {
			printError("Group \"" + groupName + "\" has not been loaded");
			return null;
		}
		return group;
	}

	private void printDot(@NotNull String filename, @NotNull Config config) {
		if (filename.startsWith("\"")) // remove "" from the filename if they exist
			filename = filename.substring(1, filename.length() - 1);

		String dotCode = "";
		try {
			dotCode = new ConfigToDot(new ConfigToDot.Builder(config)).convert();
			if (filename.equals("-")) // write to stdout
				System.out.println(dotCode);
			else { // write to file
				Files.writeString(Path.of(filename), dotCode);
				String cmd = CommandLine.Help.Ansi.AUTO.string("@|white,bold dot -Tsvg " + filename + "|@");
				System.out.println(
					"Dot code has been written to " + filename
						+ "\nRun " + cmd + " to obtain a nice svg image"
				);
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Couldn't save generated dot code into " + filename, e);
			System.out.println("Generated dot code is:\n" + dotCode);
		}
	}

	private void printHelp() {
		String promptStatuses = CommandLine.Help.Ansi.AUTO.string(
			String.format(
				"""
					@|white,bold CLI prompt statuses:|@
					  • %s: Ready to read commands
					  • %s: Waiting a service or group to start. Can't read commands
					  • %s: Exiting the application. Bye, bye
					""", readySymbol, waitingSymbol, byeSymbol
			)
		);
		String availableCommands = CommandLine.Help.Ansi.AUTO.string(
			"""
				@|white,bold Available commands:|@
				  @|white,underline Group commands:|@
				    • @|blue,bold group|@ (@|blue,bold start|@ | @|blue,bold stop|@) | (@|blue,bold start|@ | @|blue,bold stop|@) @|blue,bold group|@ @|cyan,underline <group name or alias>|@ @|cyan ...|@
				        Start or stop a group service.
				        Group's dependencies will be started and stopped first.
				    • @|blue,bold group status|@ | @|blue,bold status group|@ @|cyan,underline <group name or alias>|@ @|cyan ...|@
				        Show the status of a group service.
				        Status will also be shown for group's dependencies

				  @|white,underline Singleton service commands:|@
				    • @|blue,bold start|@ | @|blue,bold stop|@ @|cyan,underline <service name or alias>|@ @|cyan ...|@
				        Start or stop a singleton service.
				    • @|blue,bold status|@ @|cyan,underline [<service name or alias>]|@ @|cyan ...|@
				        Show the status of a service.
				        If service name or alias is not provided, all services' status will be shown.

				  @|white,underline Configuration commands:|@
				    • @|blue,bold load|@
				        Load all services. Useful to validate config.
				        It may produce duplicated output, but that's normal.
				    • @|blue,bold reload|@
				        Reload configuration from configuration file.
				    • @|blue,bold print|@ @|cyan,underline <filename>|@
				        Convert configuration to dot (graphviz) code and write it to the specified file.
				        If "-" is used as file, output will be printed to standard output.
				        Useful to obtain an overview of the microservices dependency graph.

				  @|white,underline Miscellaneous commands:|@
				    • @|blue,bold quit|@ | @|blue,bold exit|@ | @|blue,bold q|@
				        Exit the application (all started processes will be stopped).
				    • @|blue,bold help|@ | @|blue,bold h |@
				        Print this help.

				  If service name or alias contains a space, enclose it in double-quotes
				"""
		);
		String extraInfo = String.format(
			"""
				You can input multiple commands if you separate them by '%s'.
				Example: "start <service name> %s status <service name>"
				They'll execute sequentially.
							
				You can also execute any command your OS is capable to handle.
				Example: "bash"
				Control will be forwarded to that command.""",
			cmdSeparator,
			cmdSeparator
		);
		System.out.println(promptStatuses + "\n" + availableCommands + "\n" + extraInfo);
	}

	/**
	 * Split a string by space but not splitting if substring is double-quoted
	 * <p>
	 * Example:
	 * 'hello world "hola mundo" ...' will be split into:
	 * <ol>
	 *         <li>hello</li>
	 *         <li>world</li>
	 *         <li>hola mundo</li>
	 *         <li>...</li>
	 * </ol>
	 * @param str string to be split
	 * @return list of tokens extracted
	 */
	@NotNull
	public static List<String> splitBySpaces(String str) {
		if (str.isBlank())
			return Collections.emptyList();

		List<String> tokens = new ArrayList<>();
		int tokenStart = 0;
		int tokenEnd = 0;
		boolean insideQuotes = false; // tells if index i is inside double quotes
		for (int i = 0; i < str.length(); ++i) { // O(n)
			if (insideQuotes) {
				tokenStart = i;

				// advance i until we see the final '"'
				// also ignore any escaped '"', i.e. \"
				while (i < str.length() && (str.charAt(i) != '"' && str.charAt(i - 1) != '\\'))
					++i;

				if (i == str.length()) {
					printError("Malformed string. Missing end double quote '\"'");
					return Collections.emptyList();
				}

				tokenEnd = i;
				insideQuotes = false;

				tokens.add(str.substring(tokenStart, tokenEnd));
				continue;
			}

			// outside quotes
			switch (str.charAt(i)) {
				case '"' -> insideQuotes = true;
				case ' ' -> {
					tokenEnd = i;
					tokens.add(str.substring(tokenStart, tokenEnd));
					tokenStart = tokenEnd + 1;
				}
			}
		}

		// add substring at the end
		if (str.charAt(str.length() - 1) != '"') // if last token is double-quoted, then it is already in list
			tokens.add(str.substring(tokenStart));

		return tokens;
	}

	/**
	 * Same as println method from {@link System#out} but using green coloured output
	 * @param msg message to be printed
	 */
	public static void printSuccess(@NotNull String msg) {
		String out = CommandLine.Help.Ansi.AUTO.string("@|green,bold " + msg + "|@");
		System.out.println(out);
	}

	/**
	 * Same as println method from {@link System#out} but using red coloured output
	 * @param msg message to be printed
	 */
	public static void printError(@NotNull String msg) {
		String out = CommandLine.Help.Ansi.AUTO.string("@|red " + msg + "|@");
		System.out.println(out);
	}

	/**
	 * Same as println method from {@link System#out} but using yellow coloured output
	 * @param msg message to be printed
	 */
	public static void printWarning(@NotNull String msg) {
		String out = CommandLine.Help.Ansi.AUTO.string("@|yellow " + msg + "|@");
		System.out.println(out);
	}
}
