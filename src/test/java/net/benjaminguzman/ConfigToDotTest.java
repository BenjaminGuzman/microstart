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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ConfigToDotTest {
	Config config = new Config();
	File tmpOut = File.createTempFile("microstart", ".dot");

	public ConfigToDotTest() throws IOException {
		config.addAllServices(List.of(
			new ServiceConfig().setName("Test 1").setStartCmd("hola").setAliases(List.of("alias 1", "ali"
			)),
			new ServiceConfig().setName("Test 2").setStartCmd("hola").setAliases(List.of("alias 2")),
			new ServiceConfig().setName("Test 3").setStartCmd("hola").setAliases(List.of("alias 3")),
			new ServiceConfig().setName("Test 4").setStartCmd("hola").setAliases(List.of("alias 4")),
			new ServiceConfig().setName("Test 5").setStartCmd("hola").setAliases(List.of("alias 5"))
		));
		GroupConfig group1 = new GroupConfig().setName("Group 1").setServicesConfigs(List.of(
			config.getServices().get("Test 1"),
			config.getServices().get("Test 2")
		));
		GroupConfig group2 = new GroupConfig().setName("Group 2").setServicesConfigs(List.of(
			config.getServices().get("Test 3")
		));
		GroupConfig group3 = new GroupConfig().setName("Group 3").setServicesConfigs(List.of(
			config.getServices().get("Test 4"),
			config.getServices().get("Test 5")
		));
		config.addAllGroups(List.of(
			group1,
			group2.setDependenciesConfigs(List.of(group1)),
			group3.setDependenciesConfigs(List.of(group1, group2))
		));
	}

	@Test
	@DisplayName("Test conversion with indentChar=' ' and indentSize=4")
	void convert4space() throws IOException {
		System.out.println(new ConfigToDot(
			new ConfigToDot.Builder(config)
				.setIndentChar(' ')
				.setIndentSize(4)
		).convert());
		new ConfigToDot(
			new ConfigToDot.Builder(config)
				.setIndentChar(' ')
				.setIndentSize(4)
		).convertAndSave(tmpOut.toPath());

		// check contents are equal
		BufferedInputStream actual = new BufferedInputStream(new FileInputStream(tmpOut));
		BufferedInputStream expected = new BufferedInputStream(
			Objects.requireNonNull(this.getClass().getResourceAsStream("/4spaces.dot"))
		);

		byte[] actualBuff = new byte[2048];
		byte[] expectedBuff = new byte[2048];
		try (actual; expected) {
			while (actual.read(actualBuff) != -1 && expected.read(expectedBuff) != -1)
				assertArrayEquals(expectedBuff, actualBuff);
		}
	}

	@Test
	@DisplayName("Test conversion with indentChar='\\t' and indentSize=2")
	void convert2tabs() throws IOException {
		new ConfigToDot(
			new ConfigToDot.Builder(config)
				.setIndentChar('\t')
				.setIndentSize(2)
		).convertAndSave(tmpOut.toPath());

		// check contents are equal
		BufferedInputStream actual = new BufferedInputStream(new FileInputStream(tmpOut));
		BufferedInputStream expected = new BufferedInputStream(
			Objects.requireNonNull(this.getClass().getResourceAsStream("/2tab.dot"))
		);

		byte[] actualBuff = new byte[2048];
		byte[] expectedBuff = new byte[2048];
		try (actual; expected) {
			while (actual.read(actualBuff) != -1 && expected.read(expectedBuff) != -1)
				assertArrayEquals(expectedBuff, actualBuff);
		}
	}
}