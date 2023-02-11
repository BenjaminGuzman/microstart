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
import org.jetbrains.annotations.Nullable;

import javax.management.InstanceAlreadyExistsException;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class Service implements Runnable {
	private final static Logger LOGGER = Logger.getLogger(Service.class.getName());

	/**
	 * Map of names and services
	 * <p>
	 * Names include also aliases, so many keys (names) can point to the same value (service)
	 * <p>
	 * This contains all the available services throughout the application
	 */
	@NotNull
	private final static Map<String, Service> services = new HashMap<>();

	/**
	 * Contains the same values of {@link #services} but without any duplicates
	 */
	private final static HashSet<Service> uniqueServices = new HashSet<>();

	/**
	 * Service configuration
	 */
	@NotNull
	private final ServiceConfig config;

	/**
	 * Map of service status and consumers (hooks)
	 * <p>
	 * When the service reaches a new status, the specified consumer is run.
	 * First argument is {@code this} service
	 * Second argument is the status that provoked its execution
	 * <p>
	 * These consumers should be completed in short time (or spawn a thread to avoid blocking execution)
	 *
	 * @see ServiceStatus
	 */
	@NotNull
	private final Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks;
	private final Object statusLock = new Object();

	/**
	 * Queue to store a history of the statuses this service has had
	 */
	@NotNull
	private final BlockingQueue<ServiceStatus> statusesQueue;

	/**
	 * Callback to be executed when an exception occurs.
	 * First argument is {@code this} service
	 * Second argument is the exception produced
	 */
	@NotNull
	private final BiConsumer<Service, ? super Exception> onException;

	/**
	 * The process that runs this service
	 */
	@Nullable
	private Process proc;

	/**
	 * Indicates the service status
	 */
	private ServiceStatus status;

	/**
	 * @param config      configuration used to run this service process
	 * @param hooks       hooks to be executed when the service enters a particular {@link ServiceStatus}
	 * @param onException callback to be executed whenever an exception is produced. It'll be called only when java
	 *                    exceptions are produced
	 *                    (e.g. reading from the process stdout, permission error to execute),
	 *                    not when the service process reports an error, see
	 *                    {@link ServiceStatus#ERROR} and {@link ServiceConfig#setErrorPatterns(List)}
	 * @throws InstanceAlreadyExistsException if a service with the same name or alias has already been
	 *                                        instantiated previously
	 */
	public Service(
		@NotNull ServiceConfig config,
		@NotNull Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks,
		@NotNull BiConsumer<Service, ? super Exception> onException
	) throws InstanceAlreadyExistsException {
		this(config, hooks, onException, new LinkedBlockingQueue<>());
	}

	/**
	 * @param config        configuration used to run this service process
	 * @param hooks         hooks to be executed when the service enters a particular {@link ServiceStatus}
	 * @param onException   callback to be executed whenever an exception is produced. It'll be called only when
	 *                      java exceptions are produced
	 *                      (e.g. reading from the process stdout, permission error to execute),
	 *                      not when the service process reports an error, see
	 *                      {@link ServiceStatus#ERROR} and {@link ServiceConfig#setErrorPatterns(List)}
	 * @param statusesQueue each time the service status changes, the new status will be added to the queue.
	 *                      You can use this to check if {@link ServiceStatus#ERROR} was produced after
	 *                      {@link ServiceStatus#STARTED}. Adding elements to the queue doesn't affect this class
	 *                      behaviour, this class is a producer, not a consumer
	 * @throws InstanceAlreadyExistsException if a service with the same name or alias has already been
	 *                                        instantiated previously
	 */
	public Service(
		@NotNull ServiceConfig config,
		@NotNull Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks,
		@NotNull BiConsumer<Service, ? super Exception> onException,
		@NotNull BlockingQueue<ServiceStatus> statusesQueue
	) throws InstanceAlreadyExistsException {
		// ensure there is no other service loaded with the same name or alias
		if (forName(config.getName()) != null // using stream is probably not efficient, but it is easy
			|| config.getAliases().stream().anyMatch(alias -> forName(alias) != null))
			throw new InstanceAlreadyExistsException();

		this.config = config;
		this.hooks = hooks;
		this.onException = onException;
		this.statusesQueue = statusesQueue;

		// register service in singleton map
		services.put(config.getName(), this);
		uniqueServices.add(this);
		for (String alias : config.getAliases())
			services.put(alias, this);

		changeStatus(ServiceStatus.LOADED);
	}

	/**
	 * Get a {@link Service} by its name or alias
	 *
	 * @param name the name or alias of the service
	 * @return the service with the given name or null if not found (maybe it has not been loaded)
	 */
	@Nullable
	public static Service forName(@NotNull String name) {
		return services.get(name);
	}

	/**
	 * Remove all loaded services
	 */
	public static void clear() {
		services.clear();
		uniqueServices.clear();
	}

	/**
	 * @return a list of all loaded services
	 */
	public static Collection<Service> getServices() {
		return uniqueServices;
	}

	/**
	 * When an object implementing interface <code>Runnable</code> is used
	 * to create a thread, starting the thread causes the object's
	 * <code>run</code> method to be called in that separately executing
	 * thread.
	 * <p>
	 * The general contract of the method <code>run</code> is that it may
	 * take any action whatsoever.
	 *
	 * @see Thread#run()
	 */
	@Override
	public void run() {
		synchronized (statusLock) {
			if (!ServiceStatus.canServiceBeStarted(status))
				return; // don't run the service again if it is running

			changeStatus(ServiceStatus.STARTING);
		}

		// start the service process
		ProcessBuilder startProcBuilder = new ProcessBuilder(config.getStartCmd())
			.directory(config.getWorkingDirectory());

		// if provided, redirect file contents to stdin
		if (config.getStdin() != null) {
			startProcBuilder = startProcBuilder.redirectInput(config.getStdin());
			LOGGER.config(config.getStdin().getAbsolutePath() + " will serve as stdin for " + config.getColorizedName());
		}

		try {
			proc = startProcBuilder.start();
			LOGGER.info(() -> config.getColorizedName() + " PID: " + proc.pid());
		} catch (IOException e) {
			LOGGER.log(
				Level.SEVERE,
				"😱 Error starting service with this configuration: " + config,
				e
			);
			return;
		}

		// pipe streams
		// don't use try-with-resources here
		// since the streams will be closed before the pipe could start reading from them
		// see below to see when streams are being closed
		InputStream procStdout = proc.getInputStream();
		InputStream procStderr = proc.getErrorStream();

		// set the hooks to be executed when service notifies it has successfully started
		Map<Pattern, Consumer<String>> startUpHooks = new HashMap<>();
		if (!config.getStartedPatterns().isEmpty())
			config.getStartedPatterns().forEach(pattern -> {
				startUpHooks.put(pattern, s -> changeStatusSync(ServiceStatus.STARTED));
			});

		// set the hooks to be executed when service notifies an error has happened
		Map<Pattern, Consumer<String>> errorHooks = new HashMap<>();
		if (!config.getErrorPatterns().isEmpty())
			config.getErrorPatterns().forEach(pattern -> {
				errorHooks.put(pattern, s -> changeStatusSync(ServiceStatus.ERROR));
			});

		// set up pipes
		Pipe stdoutPipe = new Pipe(
			new Pipe.Builder(procStdout, System.out)
				.setPrefix("[" + config.getColorizedName() + "]: ")
				.setHooks(startUpHooks)
				.setOnException(e -> this.onException.accept(this, e))
				.setCloseOutStream(false)
		);

		Pipe stderrPipe = new Pipe(
			new Pipe.Builder(procStderr, System.out)
				.setPrefix("[" + config.getColorizedErrorName() + "]: ")
				.setHooks(errorHooks)
				.setOnException(e -> this.onException.accept(this, e))
				.setCloseOutStream(false)
		);

		// start pipe threads
		Thread stdoutThread = stdoutPipe.initThread(config.getName() + " stdout pipe thread");
		Thread stderrThread = stderrPipe.initThread(config.getName() + " stderr pipe thread");
		stdoutThread.setPriority(Thread.NORM_PRIORITY);
		stderrThread.setPriority(Thread.MIN_PRIORITY);
		stdoutThread.start();
		stderrThread.start();

		// wait until streams are closed (they're close when process closes them)
		try (procStdout; procStderr) {
			stdoutThread.join();
			stderrThread.join();
		} catch (InterruptedException e) {
			// thread interruption is expected, e.g. when you request service stop
			proc.destroy();

			// status is changed just for safety,
			// in case the thread won't continue (because it has been requested to stop)
			changeStatusSync(ServiceStatus.STOPPED);
		} catch (IOException e) {
			this.onException.accept(this, e);
		}

		// wait for application to finish
		// it is not common, but perfectly possible that an application has closed its stdout/stderr streams and
		// keep running
		int exit_code;
		try {
			exit_code = proc.waitFor(); // wait until the process finishes
		} catch (InterruptedException ignored) {
			// thread interruption is expected, e.g. when you request service stop
			exit_code = Integer.MIN_VALUE;

			// status is changed just for safety,
			// in case the thread won't continue (because it has been requested to stop)
			changeStatusSync(ServiceStatus.STOPPED);
		}

		changeStatusSync(ServiceStatus.STOPPING);

		proc.destroy();
		try {
			proc.waitFor(3, TimeUnit.SECONDS);
		} catch (InterruptedException ignored) {
			proc.destroyForcibly(); // ensure the process is terminated
		} finally {
			changeStatusSync(ServiceStatus.STOPPED);
		}

		String exitMessage = "Service " + config.getColorizedName() + " exited";
		if (exit_code == Integer.MIN_VALUE) // if thread has been interrupted this may not be executed anyway
			exitMessage += " because thread was interrupted";
		else
			exitMessage += " with status code " + exit_code + " " +
				(exit_code == 143 ? "(SIGTERM)" : (exit_code == 0 ? "(Good 👍)" : "(Bad 🥴?)"));

		System.out.println(exitMessage);
	}

	/**
	 * @return the current status of the service. If you require a "history" of statuses use a queue as described in
	 * {@link #Service(ServiceConfig, Map, BiConsumer, BlockingQueue)} constructor documentation
	 * @see #Service(ServiceConfig, Map, BiConsumer, BlockingQueue)
	 */
	@NotNull
	public ServiceStatus getStatus() {
		return status;
	}

	@NotNull
	public ServiceConfig getConfig() {
		return config;
	}

	/**
	 * @return the process that is running by this service
	 */
	@Nullable
	public Process getProc() {
		return proc;
	}

	/**
	 * Run the stop cmd (see {@link ServiceConfig})
	 */
	public void stop() {
		if (proc == null || !proc.isAlive())
			return;

		ServiceConfig config = getConfig();
		String[] stopCmd = config.getStopCmd();
		int stopTimeout = getConfig().getStopTimeout();

		if (stopCmd[0].startsWith("SIG")) { // cmd is actually not a command but a signal name
			String signalName = stopCmd[0];
			if (Microstart.IS_WINDOWS) {
				LOGGER.finer("Windows 😠...");
			} else {
				LOGGER.info("Sending " + signalName + " to "
					+ getConfig().getColorizedName() + " (pid: " + proc.pid() + ") "
					+ "and all subprocesses");
				sendSignal(signalName);
			}

			// destroy the process anyway, if the signal was processed correctly,
			// then this should be a no-op
			destroyProc();
		} else { // cmd is really a command
			LOGGER.info("Executing stop command for " + getConfig().getColorizedName() + " (pid: " + proc.pid() + ")");
			ProcessBuilder stopProcBuilder = new ProcessBuilder(config.getStopCmd())
				.directory(config.getWorkingDirectory())
				.redirectOutput(ProcessBuilder.Redirect.INHERIT)
				.redirectError(ProcessBuilder.Redirect.INHERIT);

			if (config.getStopStdin() != null) {
				stopProcBuilder.redirectInput(config.getStopStdin());
				//LOGGER.info(config.getStopStdin().getAbsolutePath() + " will serve as stdin for " + config.getColorizedName());
			}

			/*Thread t = new Thread(() -> waitForStoppedStatus(stopTimeout));
			t.start();*/
			var waiterForStopped = Executors.newSingleThreadExecutor(
				new DaemonThreadFactory("Waiter-For-Stopped-Status")
			)
				.submit(() -> waitForStoppedStatus(stopTimeout));
			try {
				stopProcBuilder
					.start()
					.waitFor(stopTimeout, TimeUnit.SECONDS);
			} catch (InterruptedException | IOException e) {
				LOGGER.log(
					Level.SEVERE,
					"Error while executing stop command \""
						+ Arrays.toString(config.getStopCmd()) + "\"",
					e
				);
			} finally {
				// cancel the thread. stop waiting
				//t.interrupt();
				waiterForStopped.cancel(true);

				// at the end we need to be sure the process is destroyed
				// if we call destroyProc method and STOPPED status was seen,
				// then destroyProc is a no-op because the process doesn't exist anymore
				destroyProc();
			}
		}
	}

	/**
	 * Wait for {@link ServiceStatus#STOPPED} status to appear on {@link #statusesQueue}
	 * or destroy the process if it is not seen before the timeout
	 * <p>
	 * It is recommended to clear the queue before calling this method
	 * <p>
	 * WARNING: This will block the thread
	 *
	 * @param timeout number of seconds to wait before destroying the process
	 */
	private void waitForStoppedStatus(int timeout) {
		try {
			long startWaitingAt = System.currentTimeMillis();

			ServiceStatus currStatus;
			while ((currStatus = statusesQueue.poll(timeout, TimeUnit.SECONDS)) != null) {
				if (currStatus == ServiceStatus.STOPPED) // stopped status was seen in time
					return;

				// received a status but not the STOPPED status
				// the timeout should be updated
				// it may not be guaranteed the timeout is precise because all these computations
				// take time
				long endWaitingAt = System.currentTimeMillis();
				long elapsedSeconds = (endWaitingAt - startWaitingAt) / 1_000;
				timeout -= elapsedSeconds;

				startWaitingAt = endWaitingAt;
			}
		} catch (InterruptedException ignored) {
		}
	}

	/**
	 * @return the pids for all the subprocesses of {@link #proc} and itself (pid of {@link #proc} is also present
	 * in the returned list). List order is postorder
	 * (try to visualize the process hierarchy as a complete binary tree.
	 * Example: subsubproc subsubproc subproc subproc proc)
	 */
	private List<Long> pids(@NotNull ProcessHandle p, @NotNull List<Long> pidsList) {
		p.children().forEach(childP -> pids(childP, pidsList));
		pidsList.add(p.pid());
		return pidsList;
	}

	/**
	 * Send signal to {@link #proc} and all child processes
	 * @param signalName signal name to send
	 */
	private void sendSignal(@NotNull String signalName) {
		if (proc == null || !proc.isAlive())
			return;

		// get a list of all process ids that should receive the signal
		List<Long> allPids = pids(proc.toHandle(), new ArrayList<>());
		LOGGER.fine("Sending " + signalName + " to: " + allPids);

		// create kill command: kill --signal SIGNAME pid1 pid2 pid3...
		String[] killCmd = new String[3 + allPids.size()];
		killCmd[0] = "kill";
		killCmd[1] = "--signal";
		killCmd[2] = signalName;
		for (int i = 0; i < allPids.size(); ++i)
			killCmd[i + 3] = String.valueOf(allPids.get(i));

		try {
			Runtime.getRuntime().exec(killCmd).waitFor(2, TimeUnit.SECONDS);
		} catch (IOException | InterruptedException e) {
			LOGGER.log(
				Level.SEVERE,
				"Error while sending " + signalName + " to " + allPids,
				e
			);
		}
	}

	/**
	 * Tries to stop the current running process (if any) and all its children processes
	 */
	private void destroyProc() {
		if (proc == null || !proc.isAlive())
			return;

		Microstart.destroyChildrenProcesses(proc.toHandle());
	}

	/**
	 * Runs the hook (if any) configured to run at the given service status
	 *
	 * @param status the service status
	 */
	private void runHook(@NotNull ServiceStatus status) {
		if (hooks.containsKey(status))
			hooks.get(status).accept(this, status);
	}

	/**
	 * Util method to change the status and run {@link #runHook(ServiceStatus)} at the same time
	 * <p>
	 * This method has no synchronization, you'll acquire lock on {@link #statusLock} first
	 *
	 * @param newStatus the new status to be set
	 * @see #changeStatusSync(ServiceStatus)
	 */
	private void changeStatus(@NotNull ServiceStatus newStatus) {
		status = newStatus;

		statusesQueue.offer(newStatus); // use put instead?

		runHook(newStatus); // hooks shouldn't take too much time to finish
	}

	/**
	 * Util method to change the status and run {@link #runHook(ServiceStatus)} at the same time
	 * <p>
	 * This method has synchronization
	 *
	 * @param newStatus the new status to be set
	 * @see #changeStatus(ServiceStatus)
	 */
	private void changeStatusSync(@NotNull ServiceStatus newStatus) {
		synchronized (statusLock) {
			changeStatus(newStatus);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Service other = (Service) o;
		return config.equals(other.config); // this will actually just check equality for service names
	}

	@Override
	public int hashCode() {
		// this will actually return the hashcode for the service name, which should be unique throughout
		// the application
		return config.hashCode();
	}
}
