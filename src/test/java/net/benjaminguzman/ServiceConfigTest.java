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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceConfigTest {
	ServiceConfig config;
	@BeforeEach
	void beforeEach() {
		config = new ServiceConfig();
	}

	@Test
	@DisplayName("Name getter and setter")
	void name() {
		assertEquals("Test name", config.setName("Test name").getName());
	}

	@Test
	@DisplayName("Working dir getter and setter")
	void workingDirectory() {
		File f = new File("Working/directory");
		assertEquals(f, config.setWorkingDirectory(f).getWorkingDirectory());
	}

	@Test
	@DisplayName("started patterns getter and setter")
	void startedPatterns() {
		List<Pattern> patterns = List.of(
			Pattern.compile("Regex"),
			Pattern.compile("Service is (up|running)")
		);
		assertEquals(patterns, config.setStartedPatterns(patterns).getStartedPatterns());
	}

	@Test
	@DisplayName("Error patterns getter and setter")
	void errorPatterns() {
		List<Pattern> patterns = List.of(
			Pattern.compile("Service is down"),
			Pattern.compile("Service had error")
		);
		assertEquals(patterns, config.setErrorPatterns(patterns).getErrorPatterns());
	}

	@Test
	@DisplayName("Aliases getter and setter")
	void aliases() {
		List<String> aliases = List.of("crypto", "cryptographic", "microcrypto");
		assertEquals(aliases, config.setAliases(aliases).getAliases());
	}

	@Test
	@DisplayName("Start cmd getter and setter")
	void startCmd() {
		config.setStartCmd("npm run start");
		String[] cmd;

		if (Microstart.IS_WINDOWS)
			cmd = new String[]{"cmd", "/c", "npm run start"};
		else
			cmd = new String[]{"sh", "-c", "npm run start"};

		assertArrayEquals(cmd, config.getStartCmd());
	}
}