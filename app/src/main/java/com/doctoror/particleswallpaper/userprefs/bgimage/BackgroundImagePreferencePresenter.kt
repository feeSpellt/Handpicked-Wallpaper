/*
 * Copyright (C) 2017 Yaroslav Mytkalyk
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
package com.doctoror.particleswallpaper.userprefs.bgimage

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.Fragment
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.bumptech.glide.Glide
import com.doctoror.particleswallpaper.R
import com.doctoror.particleswallpaper.app.REQUEST_CODE_GET_CONTENT
import com.doctoror.particleswallpaper.app.REQUEST_CODE_OPEN_DOCUMENT
import com.doctoror.particleswallpaper.framework.app.ApiLevelProvider
import com.doctoror.particleswallpaper.framework.app.actions.FragmentStartActivityForResultAction
import com.doctoror.particleswallpaper.framework.execution.SchedulersProvider
import com.doctoror.particleswallpaper.framework.file.BackgroundImageManager
import com.doctoror.particleswallpaper.framework.lifecycle.OnActivityResultCallback
import com.doctoror.particleswallpaper.framework.lifecycle.OnActivityResultCallbackHost
import com.doctoror.particleswallpaper.userprefs.data.DefaultSceneSettings
import com.doctoror.particleswallpaper.userprefs.data.NO_URI
import com.doctoror.particleswallpaper.userprefs.data.SceneSettings
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable

class BackgroundImagePreferencePresenter(
    apiLevelProvider: ApiLevelProvider,
    private val backgroundImageManager: BackgroundImageManager,
    private val context: Context,
    private val defaults: DefaultSceneSettings,
    private val glide: Glide,
    private val pickImageGetContentUseCase: PickImageGetContentUseCase,
    private val pickImageDocumentUseCase: PickImageDocumentUseCase,
    private val schedulers: SchedulersProvider,
    private val settings: SceneSettings,
    private val view: BackgroundImagePreferenceView
) {

    private val disposables = CompositeDisposable()

    private val tag = "BgImagePrefPresenter"

    private val imageHandler: BackgroundImageHandler

    init {
        @SuppressLint("NewApi")
        imageHandler = if (apiLevelProvider.provideSdkInt() >= Build.VERSION_CODES.KITKAT) {
            BackgroundImageHandlerKitKat()
        } else {
            BackgroundImageHandlerLegacy()
        }
    }

    var host: Fragment? = null
        set(f) {
            val prevHost = host
            if (prevHost !== f) {
                if (prevHost is OnActivityResultCallbackHost) {
                    prevHost.unregsiterCallback(onActivityResultCallback)
                }
                if (f is OnActivityResultCallbackHost) {
                    f.registerCallback(onActivityResultCallback)
                }
                field = f
            }
        }

    fun onClick() {
        view.showActionDialog()
    }

    fun clearBackground() {
        imageHandler.clearBackground()
        glide.clearMemory()
    }

    fun pickBackground() {
        imageHandler.pickBackground()
    }

    fun onStop() {
        disposables.clear()
    }

    private val onActivityResultCallback = object : OnActivityResultCallback() {

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                when (requestCode) {
                    REQUEST_CODE_OPEN_DOCUMENT,
                    REQUEST_CODE_GET_CONTENT -> {
                        val uri = data.data
                        if (uri == null) {
                            Log.w(tag, "onActivityResult(), data uri is null")
                        } else {
                            glide.clearMemory()
                            imageHandler.onActivityResultAvailable(requestCode, uri)
                        }
                    }
                }
            }
        }
    }

    private interface BackgroundImageHandler {
        fun pickBackground()
        fun clearBackground()
        fun onActivityResultAvailable(requestCode: Int, uri: Uri)
    }

    private open inner class BackgroundImageHandlerLegacy : BackgroundImageHandler {

        override fun pickBackground() {
            host?.let {
                try {
                    pickImageGetContentUseCase.invoke(FragmentStartActivityForResultAction(it))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.Failed_to_open_image_picker, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        override fun clearBackground() {
            settings.backgroundUri = defaults.backgroundUri
            clearBackgroundFile()
        }

        private fun clearBackgroundFile() {
            disposables.add(Observable
                .fromCallable { backgroundImageManager.clearBackgroundImage() }
                .subscribeOn(schedulers.io())
                .subscribe())
        }

        override fun onActivityResultAvailable(requestCode: Int, uri: Uri) {
            if (requestCode == REQUEST_CODE_GET_CONTENT) {
                handleGetContentUriResult(uri)
            } else {
                handleDefaultUriResult(uri)
            }
        }

        private fun handleGetContentUriResult(uri: Uri) {
            disposables.add(Observable
                .fromCallable { backgroundImageManager.copyBackgroundToFile(uri) }
                .subscribeOn(schedulers.io())
                .subscribe(
                    { settings.backgroundUri = it.toString() },
                    {
                        Log.w(tag, "Failed copying to private file", it)
                        handleDefaultUriResult(uri)
                    })
            )
        }

        private fun handleDefaultUriResult(uri: Uri) {
            settings.backgroundUri = uri.toString()
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private inner class BackgroundImageHandlerKitKat : BackgroundImageHandlerLegacy() {

        override fun pickBackground() {
            host?.let {
                try {
                    pickImageDocumentUseCase.invoke(FragmentStartActivityForResultAction(it))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.Failed_to_open_image_picker, Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        override fun clearBackground() {
            val uriString = settings.backgroundUri
            if (uriString != NO_URI) {
                val contentResolver = context.contentResolver
                if (contentResolver != null) {
                    val uri = Uri.parse(uriString)
                    val permissions = contentResolver.persistedUriPermissions
                    permissions
                        .filter { uri == it.uri }
                        .forEach {
                            contentResolver.releasePersistableUriPermission(
                                it.uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                }
            }
            super.clearBackground()
        }

        override fun onActivityResultAvailable(requestCode: Int, uri: Uri) {
            if (requestCode == REQUEST_CODE_OPEN_DOCUMENT) {
                try {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    // This might happen if openByGetContent() was called within this handler
                    Log.w(tag, "Failed to take persistable Uri permission", e)
                }
            }
            super.onActivityResultAvailable(requestCode, uri)
        }
    }
}
