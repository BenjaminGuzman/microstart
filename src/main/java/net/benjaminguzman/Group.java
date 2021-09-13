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

import javax.management.InstanceAlreadyExistsException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class containing a group of services to run
 * <p>
 * This class can also start the list group and execute it
 */
public class Group {
	private static final Logger LOGGER = Logger.getLogger(Group.class.getName());

	/**
	 * Map of names and service groups
	 * <p>
	 * Names include also aliases, so many keys (names) can point to the same value (service group)
	 * <p>
	 * This contains all the available service groups throughout the application
	 */
	@NotNull
	private final static Map<String, Group> serviceGroups = new HashMap<>();

	/**
	 * Configuration for this service group
	 */
	@NotNull
	private final GroupConfig config;

	/**
	 * Latch to be counted down each time a service in the group has started
	 * <p>
	 * If count is 0, all services should have started
	 */
	@NotNull
	private final CountDownLatch servicesLatch;

	@NotNull
	private final Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> defaultServiceHooks = new HashMap<>();

	/**
	 * Keeps track of the number of times the {@link #servicesLatch} have been counted down because a
	 * "started pattern" appeared
	 */
	@NotNull
	private final Map<Service, Integer> countDownTimes = new HashMap<>();

	/**
	 * Executor used to run services
	 */
	@NotNull
	private final ExecutorService executorService;

	/**
	 * Construct a service group with the given configuration
	 *
	 * @param config configuration for the service group
	 * @throws InstanceAlreadyExistsException if a service group with the same name or alias has already been
	 *                                        instantiated previously
	 */
	public Group(@NotNull GroupConfig config) throws InstanceAlreadyExistsException {
		// ensure there is no other service loaded with the same name or alias
		if (forName(config.getName()) != null // using stream is probably not efficient, but it is easy
			|| config.getAliases().stream().anyMatch(alias -> forName(alias) != null))
			throw new InstanceAlreadyExistsException();

		this.config = config;
		this.servicesLatch = new CountDownLatch(this.config.getServicesConfigs().size());
		this.executorService = Executors.newFixedThreadPool(
			this.config.getServicesConfigs().size(),
			new ServiceThreadFactory()
		);

		defaultServiceHooks.put(ServiceStatus.ERROR, this::onServiceError);
		defaultServiceHooks.put(ServiceStatus.STARTED, this::onServiceStarted);

		// register service in singleton map
		serviceGroups.put(config.getName(), this);
		for (String alias : config.getAliases())
			serviceGroups.put(alias, this);
	}

	/**
	 * Get a {@link Group} by its name or alias
	 *
	 * @param name the name or alias of the service
	 * @return the service with the given name or null if not found (maybe it has not been loaded)
	 */
	@Nullable
	public static Group forName(@NotNull String name) {
		return serviceGroups.get(name);
	}

	/**
	 * @return list of loaded service groups
	 */
	public static Collection<Group> getGroups() {
		return serviceGroups.values();
	}

	/**
	 * Starts a service group.
	 * <p>
	 * This will manage the execution of group services and singleton services with threads, so you don't have to
	 * worry about it
	 *
	 * @throws InstanceAlreadyExistsException if a service group with the same name or alias has already
	 *                                        been instantiated
	 */
	public void start() throws InstanceAlreadyExistsException {
		if (this.isUp())
			return;

		for (GroupConfig groupConfig : config.getDependenciesConfigs()) {
			Group dependency;
			if ((dependency = forName(groupConfig.getName())) == null) // dependency has not been loaded
				dependency = new Group(groupConfig);

			if (!dependency.isUp())
				dependency.start(); // start dependency services (and dependencies if they exist)
		}

		for (ServiceConfig serviceConfig : config.getServicesConfigs()) {
			boolean submit2Executor = true;

			Service service;
			if ((service = Service.forName(serviceConfig.getName())) == null)
				service = new Service(serviceConfig, defaultServiceHooks, this::onException);
			else // service has already been loaded, and probably is running
				if (service.getStatus().isRunning()) {
					System.out.println(service.getConfig()
						.getColorizedName() + " has already started");

					// countdown the latch and don't submit the service to execution
					// because it is already running
					submit2Executor = false;
					servicesLatch.countDown();
				}

			if (submit2Executor)
				executorService.submit(service);
		}

		// wait until all services are up
		try {
			servicesLatch.await();
		} catch (InterruptedException e) {
			LOGGER.warning("Services couldn't be started because thread was interrupted");
		}
	}

	/**
	 * @return true if both {@link #servicesLatch} count is 0. It'll be 0 if all dependencies are up and all
	 * services are up too
	 */
	public boolean isUp() {
		return servicesLatch.getCount() == 0;
	}

	/**
	 * @return {@link GroupConfig} object used to configure this service group
	 */
	@NotNull
	public GroupConfig getConfig() {
		return config;
	}

	/**
	 * Calls {@link ExecutorService#shutdownNow()} on the executor service used to run services inside this group
	 *
	 * @return same as {@link ExecutorService#shutdownNow()}
	 */
	public List<Runnable> shutdownNow() {
		return executorService.shutdownNow();
	}

	/**
	 * Calls {@link ExecutorService#awaitTermination(long, TimeUnit)} on the executor service used to run services
	 * inside this group
	 *
	 * @return same as {@link ExecutorService#awaitTermination(long, TimeUnit)}
	 */
	public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
		return executorService.awaitTermination(timeout, unit);
	}

	private void onServiceStarted(Service service, ServiceStatus started) {
		// check this method has not been invoked earlier for the same service
		if (countDownTimes.getOrDefault(service, 0) > 0) {
			LOGGER.info(
				"Service " + service.getConfig().getColorizedName() +
					" has notified again it has started. Ignoring that notification 🤷.\n" +
					"Total times it has notified this (excluding this occasion): " +
					countDownTimes.get(service)
			);
			countDownTimes.put(service, countDownTimes.get(service) + 1);
			return;
		}

		servicesLatch.countDown();
		countDownTimes.put(service, 1);
	}

	private void onServiceError(Service service, ServiceStatus error) {
		LOGGER.severe(
			"🔥 Error has been produced inside " + service.getConfig().getColorizedName() + " service 🔥\n"
				+ (Microstart.CONTINUE_AFTER_ERROR
				? "💥 Services will continue execution 💥"
				: "Next service group in the graph will not be executed")
		);

		if (Microstart.CONTINUE_AFTER_ERROR) {
			servicesLatch.countDown();
			countDownTimes.put(service, 1);
		}
	}

	private void onException(Service service, Exception e) {
		LOGGER.log(
			Level.SEVERE,
			"Exception produced while starting service " + service.getConfig().getColorizedName(),
			e
		);
	}
}
