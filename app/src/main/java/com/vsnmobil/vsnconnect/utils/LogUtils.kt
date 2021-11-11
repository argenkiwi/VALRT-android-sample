/*
 * @author Rajiv M.
 * @copyright Copyright (C) 2014 VSNMobil. All rights reserved.
 * @license http://www.apache.org/licenses/LICENSE-2.0
 */
package com.vsnmobil.vsnconnect.utils

import com.vsnmobil.vsnconnect.utils.LogUtils
import java.lang.Class
import com.vsnmobil.vsnconnect.BuildConfig
import android.util.Log

/**
 * LogUtils.java
 * To get the different types of log in the application we can use this LogUtils class.
 * By default before creating the signed APK the application will be in debug-able mode
 * Once, you created the signed APK, SDK itself change to debug-able false.Since,we have
 * checked the condition BuildConfig.DEBUG and showing log.
 */
object LogUtils {
    private const val LOG_PREFIX = "VSN_"
    private const val LOG_PREFIX_LENGTH = LOG_PREFIX.length
    private const val MAX_LOG_TAG_LENGTH = 23
    private fun makeLogTag(str: String): String {
        return if (str.length > MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH) {
            LOG_PREFIX + str.substring(
                0,
                MAX_LOG_TAG_LENGTH - LOG_PREFIX_LENGTH - 1
            )
        } else LOG_PREFIX + str
    }

    /**
     * Don't use this when obfuscating class names!
     */
    @JvmStatic
    fun makeLogTag(cls: Class<*>): String {
        return makeLogTag(cls.simpleName)
    }

    fun LOGD(tag: String?, message: String?) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message!!)
        }
    }

    fun LOGD(tag: String?, message: String?, cause: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message, cause)
        }
    }

    fun LOGV(tag: String?, message: String?) {
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message!!)
        }
    }

    fun LOGV(tag: String?, message: String?, cause: Throwable?) {
        if (BuildConfig.DEBUG && Log.isLoggable(tag, Log.VERBOSE)) {
            Log.v(tag, message, cause)
        }
    }

    fun LOGI(tag: String?, message: String?) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message!!)
        }
    }

    fun LOGI(tag: String?, message: String?, cause: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message, cause)
        }
    }

    fun LOGW(tag: String?, message: String?) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message!!)
        }
    }

    fun LOGW(tag: String?, message: String?, cause: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, message, cause)
        }
    }

    fun LOGE(tag: String?, message: String?) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message!!)
        }
    }

    fun LOGE(tag: String?, message: String?, cause: Throwable?) {
        if (BuildConfig.DEBUG) {
            Log.e(tag, message, cause)
        }
    }
}