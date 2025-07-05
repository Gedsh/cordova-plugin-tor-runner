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

import android.content.Context
import androidx.annotation.Keep
import dagger.BindsInstance
import dagger.Component
import pan.alexander.cordova.torrunner.framework.CoreService
import pan.alexander.cordova.torrunner.plugin.TorPluginManager
import pan.alexander.cordova.torrunner.plugin.Plugin
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        RepositoryModule::class,
        CoroutinesModule::class,
        SharedPreferencesModule::class
    ]
)
@Keep
interface AppComponent {
    @Component.Builder
    interface Builder {
        @BindsInstance
        fun appContext(context: Context): Builder
        fun build(): AppComponent
    }

    fun inject(plugin: Plugin)
    fun inject(torPluginManager: TorPluginManager)
    fun inject(service: CoreService)
}
