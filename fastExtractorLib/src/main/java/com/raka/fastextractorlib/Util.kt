package com.raka.fastextractorlib

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64

object Util {
    fun decode(data: String): String {
        return try {
            return String(Base64.decode(data, Base64.DEFAULT)).trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun getVersionCode(context: Context): Int {
        var versionCode = 1
        val packageInfo: PackageInfo?
        try {
            packageInfo = context.packageManager.getPackageInfo(
                context.packageName, 0
            )
            if (packageInfo == null) {
                return versionCode
            }
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            return versionCode
        }
        return versionCode
    }

    fun getVersionName(context: Context): String {
        try {
            context.packageManager?.getPackageInfo(context.packageName, 0)?.let {
                return it.versionName
            }
        } catch (e: Exception) {
        }
        return ""
    }
}