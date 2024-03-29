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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.management.InstanceAlreadyExistsException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTest {
	@Test
	@DisplayName("Testing process successful execution (stdout)")
	void runStdout() throws InterruptedException, InstanceAlreadyExistsException {
		String serviceName = "Test 1";

		assertNull(Service.forName(serviceName));

		List<ServiceStatus> expectedStatuses = List.of(
			ServiceStatus.LOADED,
			ServiceStatus.STARTING,
			ServiceStatus.STARTED,
			ServiceStatus.STOPPING,
			ServiceStatus.STOPPED
		);

		// should contain the same elements in the same order as expectedStatuses after termination
		List<ServiceStatus> statuses = new ArrayList<>(expectedStatuses.size());

		Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks = new HashMap<>();
		for (ServiceStatus status : expectedStatuses)
			hooks.put(status, (service, status1) -> statuses.add(status1));

		hooks.put(ServiceStatus.ERROR, (s, s1) -> fail()); // that status should never be reached

		// create queue to receive status changes
		BlockingQueue<ServiceStatus> queue = new ArrayBlockingQueue<>(expectedStatuses.size(), true);

		Service service = new Service(
			new ServiceConfig()
				.setName(serviceName)
				.setStartedPatterns(List.of(Pattern.compile("done", Pattern.CASE_INSENSITIVE)))
				.setStartCmd("echo -e \"Starting...\nLoading config\nService is up now\nDone.\""),
			hooks,
			(s, e) -> fail(),
			queue
		);
		Thread t = new Thread(service);
		t.start();

		// check all normal statuses have been added to queue
		for (ServiceStatus status : expectedStatuses)
			assertEquals(status, queue.take());

		t.join();

		assertTrue(queue.isEmpty());
		assertEquals(expectedStatuses, statuses);
		assertNotNull(Service.forName(serviceName)); // check the service was added to singleton map
	}

	@Test
	@DisplayName("Testing process successful execution (stderr)")
	void runStderr() throws InterruptedException, InstanceAlreadyExistsException {
		String serviceName = "Test 2";

		assertNull(Service.forName(serviceName));

		List<ServiceStatus> expectedStatuses = List.of(
			ServiceStatus.LOADED,
			ServiceStatus.STARTING,
			ServiceStatus.ERROR,
			ServiceStatus.STOPPING,
			ServiceStatus.STOPPED
		);

		// should contain the same elements in the same order as expectedStatuses after termination
		List<ServiceStatus> statuses = new ArrayList<>(expectedStatuses.size());

		Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks = new HashMap<>();
		for (ServiceStatus status : expectedStatuses)
			hooks.put(status, (service, status1) -> statuses.add(status1));

		hooks.put(ServiceStatus.STARTED, (s, s1) -> fail()); // that status should never be reached

		// create queue to receive status changes
		BlockingQueue<ServiceStatus> queue = new ArrayBlockingQueue<>(expectedStatuses.size(), true);

		Service service = new Service(
			new ServiceConfig()
				.setName(serviceName)
				.setErrorPatterns(List.of(Pattern.compile("error occurred", Pattern.CASE_INSENSITIVE)))
				.setStartCmd("echo -e \"Starting...\nError occurred\nENOENT\" >&2"),
			hooks,
			(s, e) -> fail(),
			queue
		);
		Thread t = new Thread(service);
		t.start();

		// check all expected statuses have been added to queue
		for (ServiceStatus status : expectedStatuses)
			assertEquals(status, queue.take());

		t.join();

		assertTrue(queue.isEmpty());
		assertEquals(expectedStatuses, statuses);
		assertNotNull(Service.forName(serviceName)); // check the service was added to singleton map
	}

	@Test
	@DisplayName("Testing multiple started patterns")
	void runMultipleUp() throws InterruptedException, InstanceAlreadyExistsException {
		String serviceName = "Test 3";

		assertNull(Service.forName(serviceName));

		List<ServiceStatus> expectedStatuses = List.of(
			ServiceStatus.LOADED,
			ServiceStatus.STARTING,
			ServiceStatus.STARTED,
			ServiceStatus.STARTED,
			ServiceStatus.STARTED,
			ServiceStatus.STOPPING,
			ServiceStatus.STOPPED
		);

		// this actually doesn't require synchronization because start hooks are actually executed sequentially
		// because they are read sequentially, and they appear sequentially in the input
		// but it was made atomic just as a quick solution because lambda exp. requires a final variable
		AtomicInteger i = new AtomicInteger();

		Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks = new HashMap<>();
		hooks.put(ServiceStatus.STARTED, (s, s1) -> i.incrementAndGet());

		// create queue to receive status changes
		BlockingQueue<ServiceStatus> queue = new ArrayBlockingQueue<>(expectedStatuses.size(), true);

		Service service = new Service(
			new ServiceConfig()
				.setName(serviceName)
				.setStartedPatterns(List.of(
					Pattern.compile("is (up|running)", Pattern.CASE_INSENSITIVE),
					Pattern.compile("successful test", Pattern.CASE_INSENSITIVE)
				))
				.setStartCmd("echo -e \"Service is up\nService is running\nSuccessful test!!\""),
			hooks,
			(s, e) -> fail(),
			queue
		);
		Thread t = new Thread(service);
		t.start();

		// check all expected statuses have been added to queue
		for (ServiceStatus status : expectedStatuses)
			assertEquals(status, queue.take());

		t.join();

		assertTrue(queue.isEmpty());
		assertEquals(3, i.get());

		assertNotNull(Service.forName(serviceName)); // check the service was added to singleton map
	}

	@Test
	@DisplayName("Testing stdin redirection works")
	void runStdinRedirection() throws InterruptedException, InstanceAlreadyExistsException {
		String serviceName = "Mirror";

		assertNull(Service.forName(serviceName));

		List<ServiceStatus> expectedStatuses = List.of(
			ServiceStatus.LOADED,
			ServiceStatus.STARTING,
			ServiceStatus.STARTED,
			ServiceStatus.STOPPING,
			ServiceStatus.STOPPED
		);

		Map<ServiceStatus, BiConsumer<Service, ServiceStatus>> hooks = new HashMap<>();

		BlockingQueue<ServiceStatus> queue = new ArrayBlockingQueue<>(expectedStatuses.size(), true);

		Service service = new Service(
			new ServiceConfig()
				.setName(serviceName)
				.setStartedPatterns(List.of(
					Pattern.compile("World!", Pattern.CASE_INSENSITIVE)
				))
				.setStdin(new File("src/test/resources/mirror.stdin"))
				.setStartCmd("python3 src/test/resources/mirror.py"),
			hooks,
			(s, e) -> fail(),
			queue
		);
		Thread t = new Thread(service);
		t.start();

		// check all expected statuses have been added to queue
		for (ServiceStatus status : expectedStatuses)
			assertEquals(status, queue.take());

		t.join(); // wait for service to end

		assertTrue(queue.isEmpty()); // verify that no more statuses were added to the queue

		assertNotNull(Service.forName(serviceName)); // check the service was added to singleton map
	}
}
