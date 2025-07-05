/*
    This file is part of Cordova Plugin Tor Runner.

    Cordova Plugin Tor Runner is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Cordova Plugin Tor Runner is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Cordova Plugin Tor Runner.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2025 by Garmatin Oleksandr invizible.soft@gmail.com
 */

package pan.alexander.cordova.torrunner.domain.core;

public interface ShellExitCode {

    int SUCCESS = 0;

    int WATCHDOG_EXIT = -1;

    int SHELL_DIED = -2;

    int SHELL_EXEC_FAILED = -3;

    int SHELL_WRONG_UID = -4;

    int SHELL_NOT_FOUND = -5;

    int TERMINATED = 130;

    int COMMAND_NOT_EXECUTABLE = 126;

    int COMMAND_NOT_FOUND = 127;

}
