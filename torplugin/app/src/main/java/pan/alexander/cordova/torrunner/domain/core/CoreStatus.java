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

import static pan.alexander.cordova.torrunner.domain.core.CoreState.RESTARTING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.RUNNING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STARTING;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.STOPPED;
import static pan.alexander.cordova.torrunner.domain.core.CoreState.UNDEFINED;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.loge;
import static pan.alexander.cordova.torrunner.utils.logger.Logger.logi;

import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;

import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository;

@Singleton
public final class CoreStatus {

    private final ConfigurationRepository configuration;

    private volatile CoreState torState = STOPPED;
    private volatile boolean torReady;

    @Inject
    CoreStatus(ConfigurationRepository configuration) {
        this.configuration = configuration;
    }

    public CoreState getTorState() {
        return torState;
    }

    public void setTorState(CoreState torState) {
        logi("Tor State " + torState);
        this.torState = torState;
        updateCordovaConfiguration();
    }

    public boolean isTorReady() {
        return torReady;
    }

    public void setTorReady(boolean torReady) {
        this.torReady = torReady;
    }

    private void updateCordovaConfiguration() {
        try {
            if (torState == STOPPED || torState == STARTING || torState == RUNNING) {
                setTorStatus(torState);
            } else if (torState == RESTARTING) {
                setTorStatus(STARTING);
            } else {
                setTorStatus(STOPPED);
            }
        } catch (Exception e) {
            loge("CoreStatus updateCordovaConfiguration", e);
        }
    }

    private void setTorStatus(CoreState state) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("torState", state.name());
        configuration.updateCordovaConfiguration(json);
    }

}
