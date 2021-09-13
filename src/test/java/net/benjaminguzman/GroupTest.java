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
import org.junit.jupiter.api.Test;

import javax.management.InstanceAlreadyExistsException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

class GroupTest {
	@BeforeAll
	static void beforeAll() throws IOException, InstanceAlreadyExistsException {
		if (ConfigLoader.getInstance() == null)
			new ConfigLoader("src/test/test.json");
	}

	@Test
	void start() throws MaxDepthExceededException, ServiceNotFoundException, FileNotFoundException,
		GroupNotFoundException, CircularDependencyException, InstanceAlreadyExistsException {
		GroupConfig config = Objects.requireNonNull(ConfigLoader.getInstance()).loadGroupConfig("good group");
		//ServiceGroup group = new ServiceGroup(config);
		//group.start();
		// uncomment to see results
		// this is actually not a test but an example
		// manually check Test 2 is started before Test 1
	}
}