/*
 * Copyright (C) 2018 Yaroslav Mytkalyk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.doctoror.particleswallpaper.userprefs.multisampling

import com.doctoror.particleswallpaper.framework.execution.SchedulersProvider
import com.doctoror.particleswallpaper.userprefs.data.DeviceSettings
import com.doctoror.particleswallpaper.userprefs.data.OpenGlSettings
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction

class MultisamplingPreferencePresenter(
    private val schedulers: SchedulersProvider,
    private val settings: OpenGlSettings,
    private val settingsDevice: DeviceSettings,
    private val valueMapper: MultisamplingPreferenceValueMapper,
    private val view: MultisamplingPreferenceView,
    private val wallpaperChecker: WallpaperCheckerUseCase
) {

    private val disposables = CompositeDisposable()

    fun onPreferenceChange(v: Int) {
        settings.numSamples = v

        disposables.add(wallpaperChecker
            .wallpaperInstalledSource()
            .subscribeOn(schedulers.io())
            .observeOn(schedulers.mainThread())
            .subscribe { isInstalled ->
                if (isInstalled) {
                    view.showRestartDialog()
                }
            })
    }

    fun onStart() {
        disposables.add(
            settings
                .observeNumSamples()
                .subscribeOn(schedulers.io())
                .observeOn(schedulers.mainThread())
                .subscribe(view::setValue)
        )

        disposables.add(
            Observable
                .combineLatest(
                    settingsDevice
                        .observeMultisamplingSupportedValues()
                        .subscribeOn(schedulers.io()),
                    settingsDevice
                        .observeOpenglEnabled()
                        .subscribeOn(schedulers.io()),
                    BiFunction<
                            Set<String>,
                            Boolean,
                            Triple<Array<CharSequence>, Array<CharSequence>, Boolean>>
                    { supportedValues, openglEnabled ->
                        Triple(
                            valueMapper.toEntries(supportedValues),
                            valueMapper.toEntryValues(supportedValues),
                            openglEnabled
                        )
                    }
                )
                .observeOn(schedulers.mainThread())
                .subscribe { (entries, entryValues, openglEnabled) ->
                    if (entryValues.isEmpty()) {
                        throw IllegalArgumentException("Empty values are not expected")
                    }
                    view.setEntries(entries)
                    view.setEntryValues(entryValues)
                    // 1 means only Disabled item is there
                    view.setPreferenceSupported(openglEnabled && entryValues.size > 1)
                }
        )
    }

    fun onStop() {
        disposables.clear()
    }
}
