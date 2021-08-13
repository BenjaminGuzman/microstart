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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
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
	@Nullable
	private final BlockingQueue<ServiceStatus> statusesQueue;

	/**
	 * Callback to be executed when an exception occurs.
	 * First argument is {@code this} service
	 * Second argument is the exception produced
	 */
	@NotNull
	private final BiConsumer<Service, ? super Exception> onException;

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
	 * instantiated previously
	 */
	public Service(
		@NotNull ServiceConfig config,
		@NotNull Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks,
		@NotNull BiConsumer<Service, ? super Exception> onException
	) throws InstanceAlreadyExistsException {
		this(config, hooks, onException, null);
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
	 * instantiated previously
	 */
	public Service(
		@NotNull ServiceConfig config,
		@NotNull Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks,
		@NotNull BiConsumer<Service, ? super Exception> onException,
		@Nullable BlockingQueue<ServiceStatus> statusesQueue
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
		ProcessBuilder processBuilder = new ProcessBuilder(config.getStartCmd())
			.directory(config.getWorkingDirectory());

		Process proc;
		try {
			proc = processBuilder.start();
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
		if (hooks.get(ServiceStatus.STARTED) != null && !config.getStartedPatterns().isEmpty())
			config.getStartedPatterns().forEach(pattern -> {
				startUpHooks.put(pattern, s -> changeStatusSync(ServiceStatus.STARTED));
			});

		// set the hooks to be executed when service notifies an error has happened
		Map<Pattern, Consumer<String>> errorHooks = new HashMap<>();
		if (hooks.get(ServiceStatus.ERROR) != null && !config.getErrorPatterns().isEmpty())
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
		} catch (InterruptedException ignored) {
			// thread interruption is expected, e.g. when you request service stop
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
			exit_code = Integer.MIN_VALUE;
			// thread interruption is expected, e.g. when you request service stop
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
		if (exit_code == Integer.MIN_VALUE) // if thread has been interrupted this may never be executed anyway
			exitMessage += " because thread was interrupted";
		else
			exitMessage += " with status code " + exit_code + " " +
				(exit_code == 0 ? "(Good)" : "(Bad?)");

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

		if (statusesQueue != null)
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
}
