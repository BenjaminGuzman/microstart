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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.management.InstanceAlreadyExistsException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {
	@BeforeAll
	static void beforeAll() throws IOException, InstanceAlreadyExistsException {
		if (ConfigLoader.getInstance() == null)
			new ConfigLoader("src/test/test.json");
	}

	@Test
	@DisplayName("Testing config loader for service Test 1")
	void loadConfig1() throws FileNotFoundException, ServiceNotFoundException {
		ServiceConfig test1Config = Objects.requireNonNull(ConfigLoader.getInstance()).loadServiceConfig("Test 1");
		assertNotNull(test1Config);
		assertEquals("Test 1", test1Config.getName());
		assertEquals("echo -e \"Testing config loader...\nIt works!\"", test1Config.getStartCmd()[2]);
		assertEquals("/tmp", test1Config.getWorkingDirectory().toString());
		assertEquals(Pattern.compile("Works", Pattern.CASE_INSENSITIVE).toString(), test1Config.getStartedPatterns().get(0).toString());
		assertEquals(Pattern.compile("errno", Pattern.CASE_INSENSITIVE).toString(), test1Config.getErrorPatterns().get(1).toString());

		// test config can also be loaded by the service aliases
		assertEquals(test1Config, ConfigLoader.getInstance().loadServiceConfig("test1"));
		assertEquals(test1Config, ConfigLoader.getInstance().loadServiceConfig("first"));
	}

	@Test
	@DisplayName("Testing config loader for service Test 2")
	void loadConfig2() throws FileNotFoundException, ServiceNotFoundException {
		ServiceConfig test2Config = Objects.requireNonNull(ConfigLoader.getInstance()).loadServiceConfig("Test 2");
		assertNotNull(test2Config);
		assertEquals("Test 2", test2Config.getName());
		assertEquals("echo -e \"Testing config loader 2...\nIt works!\"", test2Config.getStartCmd()[2]);
		assertEquals("/tmp", test2Config.getWorkingDirectory().toString());
		assertEquals(Pattern.compile("Works", Pattern.CASE_INSENSITIVE).toString(), test2Config.getStartedPatterns().get(0).toString());
		assertEquals(Pattern.compile("errno", Pattern.CASE_INSENSITIVE).toString(), test2Config.getErrorPatterns().get(1).toString());

		// test config can also be loaded by the service aliases
		assertEquals(test2Config, ConfigLoader.getInstance().loadServiceConfig("test2"));
		assertEquals(test2Config, ConfigLoader.getInstance().loadServiceConfig("second"));
	}

	@Test
	@DisplayName("Testing config loader for group service with circular dependencies")
	void loadGroupWCyclicDeps() {
		assertThrows(CircularDependencyException.class, () -> Objects.requireNonNull(ConfigLoader.getInstance())
			.loadGroupConfig("circular dependencies"));
	}

	@Test
	@DisplayName("Testing config loader for group service with max depth exceeded")
	void loadGroupWMaxDepth() {
		assertThrows(MaxDepthExceededException.class, () -> Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("max depth"));
	}

	@Test
	@DisplayName("Testing config loader for group service with wrong dependency name")
	void loadGroupWWrongDepName() {
		assertThrows(GroupNotFoundException.class, () -> Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("wrong dependency"));
	}

	@Test
	@DisplayName("Testing config loader for group service with wrong name")
	void loadGroupWWrongName() {
		assertThrows(GroupNotFoundException.class, () -> Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("non existent group"));
	}

	@Test
	@DisplayName("Testing config loader for group service with wrong service name")
	void loadGroupWWrongServiceName() {
		assertThrows(ServiceNotFoundException.class, () -> Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("wrong service"));
	}

	@Test
	@DisplayName("Testing config loader for good service group configuration")
	void loadGroupWGoodConfig() throws MaxDepthExceededException, ServiceNotFoundException, FileNotFoundException, GroupNotFoundException, CircularDependencyException {
		GroupConfig config = Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("good group");
		assertEquals("good group", config.getName());
		assertEquals(List.of("pass", "good"), config.getAliases());

		// check dependencies have been loaded correctly
		GroupConfig deps = config.getDependenciesConfigs().get(0);
		assertEquals(1, config.getDependenciesConfigs().size());
		assertEquals("good group 2", deps.getName());
		assertEquals("Test 2", deps.getServicesConfigs().get(0).getName());
		assertEquals("/tmp", deps.getServicesConfigs().get(0).getWorkingDirectory().toString());
	}

	@Test
	@DisplayName("Testing it loads ALL configuration")
	void load() {
		assertThrows(
			MaxDepthExceededException.class,
			() -> Objects.requireNonNull(ConfigLoader.getInstance()).load()
		);
	}
}