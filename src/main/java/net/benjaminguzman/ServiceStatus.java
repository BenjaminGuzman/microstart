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

public enum ServiceStatus {
	/**
	 * Initial status for the service
	 */
	LOADED,

	/**
	 * Service start command is being executed
	 */
	STARTING,

	/**
	 * Service has notified a successful start up
	 */
	STARTED,

	/**
	 * Service has notified an error occurred
	 */
	ERROR,

	/**
	 * Service has been requested to be stopped
	 */
	STOPPING,

	/**
	 * Service has stopped and it is down
	 */
	STOPPED;

	/**
	 * Tells if the service can be started depending on its current status
	 * (you can't start a service that has already been started)
	 *
	 * @param status current service status
	 * @return true if the service can be started given its status, false otherwise
	 */
	public static boolean canServiceBeStarted(@NotNull ServiceStatus status) {
		return status == LOADED || status == STOPPED;
	}

	/**
	 * Tells if the service is currently running or is about to be running (is starting)
	 * <p>
	 * A service is running if its status is one of {@link ServiceStatus#STARTING},
	 * {@link ServiceStatus#STARTED}, {@link ServiceStatus#ERROR}, {@link ServiceStatus#STOPPING}.
	 * <p>
	 * In other words, a service is running if it has been loaded and hasn't been
	 *
	 * @return true if the service is running, false otherwise
	 */
	public boolean isRunning() {
		switch (this) {
			case STARTING:
			case STARTED:
			case ERROR:
			case STOPPING:
				return true;
			default:
				return false;
		}
	}


	/**
	 * Returns the name of this enum constant, as contained in the
	 * declaration.  This method may be overridden, though it typically
	 * isn't necessary or desirable.  An enum type should override this
	 * method when a more "programmer-friendly" string form exists.
	 *
	 * @return the name of this enum constant
	 */
	@Override
	public String toString() {
		return this.name().charAt(0) + this.name().substring(1).toLowerCase();
	}
}
