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

import net.benjaminguzman.exceptions.CircularDependencyException;
import net.benjaminguzman.exceptions.GroupNotFoundException;
import net.benjaminguzman.exceptions.MaxDepthExceededException;
import net.benjaminguzman.exceptions.ServiceNotFoundException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.management.InstanceAlreadyExistsException;
import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
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

	/**
	 * Line with the initial commands. This is, the commands to be executed first, before reading directly from
	 * stdin
	 */
	@Nullable
	private String initialLineInput = null;

	private final String waitingSymbol = "⏳";
	private final String readySymbol = "✔";
	private final String byeSymbol = "👋";
	private final String cmdSeparator = "&";

	private final PromptOutputStream customStdout = new PromptOutputStream(System.out)
		.setPrompt(">>> ")
		.setStatusIcon(waitingSymbol);

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
		}
		System.out.println("Processing command: \"" + cmd + "\"");

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

		// process print command
		if (cmd.startsWith("graph")) {
			String filename = cmd.substring("graph".length()).stripLeading();
			//filename
		}

		System.out.println("Command \"" + cmd + "\" was not understood. Type \"help\" or \"h\" to print help");

		return false;
	}

	private void startGroupByName(@NotNull String groupName) {
		Group group = Group.forName(groupName);

		if (group == null) // group has not been loaded. Load it
			try {
				assert ConfigLoader.getInstance() != null;
				group = new Group(
					ConfigLoader.getInstance().loadGroupConfig(groupName)
				);
			} catch (MaxDepthExceededException | GroupNotFoundException | CircularDependencyException | FileNotFoundException | ServiceNotFoundException e) {
				System.out.println(e.getMessage());
				return;
			} catch (InstanceAlreadyExistsException e) {
				LOGGER.log(Level.SEVERE, "Programming error❗", e);
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

	private void startServiceByName(@NotNull String serviceName) {
		Service service = Service.forName(serviceName);
		//throw new UnsupportedOperationException("You can't start singleton services with this version");

		/*if (service == null) // service has not been loaded. Load it
			try {
				assert ConfigLoader.getInstance() != null;
				ServiceConfig serviceConfig = ConfigLoader.getInstance().loadServiceConfig
				(serviceName);
				service = new Service(
					serviceConfig,

				);
			} catch (MaxDepthExceededException | GroupNotFoundException | CircularDependencyException |
			FileNotFoundException | ServiceNotFoundException e) {
				System.out.println(e.getMessage());
				return;
			} catch (InstanceAlreadyExistsException e) {
				LOGGER.log(Level.SEVERE, "Programming error❗", e);
				return;
			}

		// by now, the service has been loaded
		try {
			service.start(); // start and block until it has started
		} catch (InstanceAlreadyExistsException e) {
			LOGGER.log(Level.SEVERE, "Programming error❗", e);
		}*/

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
			"   The group name is the one you defined in config file\n" +
			" - (start|stop|restart) <service name>. Start, stop or restart a singleton service\n" +
			" - status [<service name>]. Query the status of a particular service\n" +
			"   or all services if service name is not provided\n" +
			" - (quit|exit|q). Exit the application (all started processes will be stopped)\n" +
			" - (help|h). Print this help\n" +
			"You can input multiple commands if you separate them by '&'.\n" +
			"Example: \"group start <group name> & status <group name>\"\n" +
			"They'll execute sequentially";
		System.out.println(help);
	}
}
