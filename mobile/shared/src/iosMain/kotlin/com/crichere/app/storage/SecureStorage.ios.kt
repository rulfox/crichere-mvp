package com.crichere.app.storage

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * iOS `actual`: native Keychain Services (`kSecClassGenericPassword`), scoped under one
 * `kSecAttrService` string so this app's entries stay isolated from anything else on-device.
 * Keychain is the standard, Apple-recommended place for credential-shaped secrets -- there's no
 * DataStore/Tink equivalent question to resolve on this side, unlike Android's.
 *
 * Built directly on the CoreFoundation `CFDictionary`/`CFString`/`CFData` APIs (not
 * `NSMutableDictionary`/`NSString`/`NSData`) because the `Security` framework's C functions
 * (`SecItemAdd` et al.) are declared against `CFDictionaryRef`, and Kotlin/Native's interop does
 * NOT toll-free-bridge `NSDictionary` to `CFDictionaryRef` automatically the way Swift/ObjC do --
 * that mismatch (verified for real: `:shared:compileKotlinIosSimulatorArm64` runs on this
 * Windows machine's bundled Kotlin/Native cross-compiler and rejected an earlier
 * `NSMutableDictionary`-based draft of this file with exactly this error) is why this version
 * stays in pure-CoreFoundation types throughout. This file **does** compile against the
 * `iosSimulatorArm64`/`iosArm64`/`iosX64` klib targets on this machine; what's still unverified
 * is the actual framework link + on-device/simulator run, which needs Xcode's Apple SDKs (no
 * Mac available here) -- see task-5-report.md.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage : SecureStore {

    actual override suspend fun get(key: String): String? = memScoped {
        val query = newQuery(key)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)

        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return null

        val dataRef = result.value?.asCFData() ?: return null
        val plaintext = dataRef.toByteArray()
        CFRelease(dataRef)
        plaintext.decodeToString()
    }

    actual override suspend fun set(key: String, value: String) {
        val dataRef = value.encodeToByteArray().toCFData()

        val updateAttributes = newMutableDictionary()
        CFDictionarySetValue(updateAttributes, kSecValueData, dataRef)
        val updateStatus = SecItemUpdate(newQuery(key), updateAttributes)
        CFRelease(updateAttributes)

        if (updateStatus == errSecItemNotFound) {
            val insertQuery = newQuery(key)
            CFDictionarySetValue(insertQuery, kSecValueData, dataRef)
            SecItemAdd(insertQuery, null)
            CFRelease(insertQuery)
        }

        CFRelease(dataRef)
    }

    actual override suspend fun remove(key: String) {
        val query = newQuery(key)
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun newMutableDictionary(): CFMutableDictionaryRef? =
        CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)

    /** A fresh `[kSecClass: genericPassword, kSecAttrService: SERVICE, kSecAttrAccount: key]` query. */
    private fun newQuery(key: String): CFMutableDictionaryRef? {
        val query = newMutableDictionary()
        CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionarySetValue(query, kSecAttrService, SERVICE.toCFString())
        CFDictionarySetValue(query, kSecAttrAccount, key.toCFString())
        return query
    }

    private fun String.toCFString(): CFStringRef? =
        CFStringCreateWithCString(null, this, kCFStringEncodingUTF8)

    private fun ByteArray.toCFData(): CFDataRef? {
        if (isEmpty()) return CFDataCreate(null, null, 0)
        return usePinned { pinned ->
            CFDataCreate(null, pinned.addressOf(0).reinterpret(), size.toLong())
        }
    }

    private fun CFDataRef.toByteArray(): ByteArray {
        val length = CFDataGetLength(this).toInt()
        val bytePtr = CFDataGetBytePtr(this) ?: return ByteArray(0)
        return ByteArray(length) { index -> bytePtr[index].toByte() }
    }

    private fun COpaquePointer.asCFData(): CFDataRef = reinterpret()

    private companion object {
        const val SERVICE = "com.crichere.app.secure_storage"
    }
}
