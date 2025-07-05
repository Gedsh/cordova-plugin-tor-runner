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

import androidx.annotation.NonNull;

import java.util.List;

public class CommandResult implements ShellExitCode {

    private static String toString(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        if (lines != null) {
            String emptyOrNewLine = "";
            for (String line : lines) {
                sb.append(emptyOrNewLine).append(line);
                emptyOrNewLine = "\n";
            }
        }
        return sb.toString();
    }

    @NonNull
    public final List<String> stdout;
    @NonNull
    public final List<String> stderr;
    public final int exitCode;

    public CommandResult(@NonNull List<String> stdout, @NonNull List<String> stderr, int exitCode) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitCode = exitCode;
    }

    /**
     * Check if the exit code is 0.
     *
     * @return {@code true} if the {@link #exitCode} is equal to {@link ShellExitCode#SUCCESS}.
     */
    public boolean isSuccessful() {
        return exitCode == SUCCESS;
    }

    /**
     * Get the standard output.
     *
     * @return The standard output as a string.
     */
    public String getStdout() {
        return toString(stdout);
    }

    /**
     * Get the standard error.
     *
     * @return The standard error as a string.
     */
    public String getStderr() {
        return toString(stderr);
    }

    @NonNull
    @Override
    public String toString() {
        return getStdout();
    }

}
