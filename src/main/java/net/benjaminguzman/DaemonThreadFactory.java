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

import java.util.concurrent.ThreadFactory;

/**
 * Factory for daemon threads
 * @see Thread#setDaemon(boolean)
 */
public class DaemonThreadFactory implements ThreadFactory {
	@NotNull
	private final String name;

	private final int priority;

	/**
	 * Create a thread factory with "Service-Thread" as thread name
	 * and default priority
	 * @see DaemonThreadFactory(String)
	 */
	public DaemonThreadFactory() {
		this("Service-Thread");
	}

	/**
	 * Create a thread factory with thread having the given name and {@link Thread#NORM_PRIORITY}
	 * @param threadName thread name
	 */
	public DaemonThreadFactory(@NotNull String threadName) {
		this(threadName, Thread.NORM_PRIORITY);
	}

	/**
	 * Create a thread factory with thread having the given name and priority
	 * @param threadName thread name
	 * @param priority thread priority
	 */
	public DaemonThreadFactory(@NotNull String threadName, int priority) {
		this.name = threadName;
		this.priority = priority;
	}

	/**
	 * Constructs a new {@code Thread}.  Implementations may also initialize
	 * priority, name, daemon status, {@code ThreadGroup}, etc.
	 *
	 * @param r a runnable to be executed by new thread instance
	 * @return constructed thread, or {@code null} if the request to
	 * create a thread is rejected
	 */
	@Override
	public Thread newThread(@NotNull Runnable r) {
		Thread t = new Thread(r, name);
		t.setDaemon(true);
		t.setPriority(priority);
		return t;
	}
}
