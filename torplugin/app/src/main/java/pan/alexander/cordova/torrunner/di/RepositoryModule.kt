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

package pan.alexander.cordova.torrunner.di

import dagger.Binds
import dagger.Module
import pan.alexander.cordova.torrunner.data.configuration.ConfigurationRepositoryImpl
import pan.alexander.cordova.torrunner.data.preferences.PreferenceRepositoryImpl
import pan.alexander.cordova.torrunner.domain.configuration.ConfigurationRepository
import pan.alexander.cordova.torrunner.domain.preferences.PreferenceRepository

@Module
abstract class RepositoryModule {

    @Binds
    abstract fun bindConfigurationRepository(
        repository: ConfigurationRepositoryImpl
    ): ConfigurationRepository

    @Binds
    abstract fun bindPreferenceRepository(
        repository: PreferenceRepositoryImpl
    ): PreferenceRepository
}
