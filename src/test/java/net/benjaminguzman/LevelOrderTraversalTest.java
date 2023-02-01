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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class LevelOrderTraversalTest {
	@BeforeAll
	static void beforeAll() throws IOException, InstanceAlreadyExistsException {
		if (ConfigLoader.getInstance() != null)
			ConfigLoader.deleteInstance();
		new ConfigLoader("src/test/resources/tree.yml");
	}

	@Test
	@DisplayName("Testing level order works")
	void loadConfig1() throws FileNotFoundException, ServiceNotFoundException, MaxDepthExceededException, GroupNotFoundException, CircularDependencyException {
		Config conf = Objects.requireNonNull(ConfigLoader.getInstance()).load();
		// load all groups
		conf.getGroups()
			.values()
			.stream()
			.map(GroupConfig::getName)
			.map(name -> {
				try {
					return ConfigLoader.getInstance().loadGroupConfig(name);
				} catch (MaxDepthExceededException | GroupNotFoundException |
					 CircularDependencyException | FileNotFoundException |
					 ServiceNotFoundException e) {
					fail(e);
					return null;
				}
			}).forEach(groupConfig -> {
				try {
					new Group(groupConfig);
				} catch (InstanceAlreadyExistsException e) {
					fail(e);
				}
			});

		// get actual group level ordering
		List<String> groupNames = Group.getRoots()
			.stream()
			.map(Microstart::levelOrderTraversal)
			// .peek(System.out::println) // check traversal is indeed working
			.flatMap(Collection::stream)
			.flatMap(Collection::stream)
			//.distinct() // distinct is used in Microstart because we call shutdownNow only once
			.map(Group::getConfig)
			.map(GroupConfig::getName)
			.toList();

		List<String> expected = Stream.of(
			List.of(
				List.of("Root 2"),
				List.of("Deeper 1"),
				List.of("Deepest 1")
			),
			List.of(
				List.of("Root 1"),
				List.of("Deep 1"),
				List.of("Deeper 1", "Deeper 2"),
				List.of("Deepest 1")
			)
		).flatMap(Collection::stream).flatMap(Collection::stream).toList();

		assertEquals(expected, groupNames);
	}
}
