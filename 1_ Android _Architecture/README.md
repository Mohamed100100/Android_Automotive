# Android Architecture: Complete Deep Dive

A comprehensive technical guide covering Android's layered architecture, service types, IPC mechanisms, runtime environments, and a complete real-world trace from the Application Layer down to the Linux Kernel using the Vibrator as a practical example.

---

## 1. Architecture Overview

Android's architecture is organized into **abstract layers** — conceptual boundaries that help developers understand how different components interact. While these layers don't exist as physical separations in code, they represent clear responsibility boundaries between processes and subsystems.

> **Important Note:** These are *abstract layers* — conceptual boundaries based on responsibilities, not strict physical separations. Processes and components may cross these boundaries in practice.

### Two Common Layer Representations

**Google's Standard 5-Layer Model:**

```
┌─────────────────────────────────────┐
│ 1. Application Layer                │
├─────────────────────────────────────┤
│ 2. Application Framework Layer      │
├─────────────────────────────────────┤
│ 3. System Runtime Layer             │
│    (Native Libraries + ART)         │
├─────────────────────────────────────┤
│ 4. Hardware Abstraction Layer (HAL) │
├─────────────────────────────────────┤
│ 5. Linux Kernel Layer               │
└─────────────────────────────────────┘
```

**Extended 7-Layer Model (More Granular):**

```
┌─────────────────────────────────────┐
│ 1. Application Layer                │
├─────────────────────────────────────┤
│ 2. Android Framework Layer          │
├─────────────────────────────────────┤
│ 3. System Services Layer            │
├─────────────────────────────────────┤
│ 4. Android Runtime (ART)            │
├─────────────────────────────────────┤
│ 5. HAL Layer                        │
├─────────────────────────────────────┤
│ 6. Native Daemons & Services        │
├─────────────────────────────────────┤
│ 7. Linux Kernel Layer               │
└─────────────────────────────────────┘
```

---

## 2. The Five/Seven Abstract Layers

### Layer Comparison Table

| Layer | Name                      | Primary Language | Key Components                                    | Process                      |
| ----- | ------------------------- | ---------------- | ------------------------------------------------- | ---------------------------- |
| 1     | **Application Layer**     | Java / Kotlin    | Apps, System UI, OEM apps                         | Per-app process              |
| 2     | **Application Framework** | Java / Kotlin    | Managers, APIs, View System                       | App process + system_server  |
| 3     | **System Services**       | Java + JNI       | AMS, WMS, PMS, VibratorManagerService             | system_server                |
| 4     | **Android Runtime**       | Java / C++       | ART, Core Libraries, GC                           | Shared across Java processes |
| 5     | **HAL**                   | C / C++          | Vendor HAL implementations (.so)                  | Vendor processes             |
| 6     | **Native Daemons**        | C / C++          | SurfaceFlinger, MediaServer, Vibrator HAL service | Separate native processes    |
| 7     | **Linux Kernel**          | C                | Drivers, Binder IPC, Scheduler                    | Kernel space                 |

---

## 3. Application Layer

The topmost layer where users interact directly with the system.

### Three Types of Applications

| Type                | Description                                       | Examples                          | Permissions                                           |
| ------------------- | ------------------------------------------------- | --------------------------------- | ----------------------------------------------------- |
| **Normal Apps**     | Standard Play Store apps                          | Instagram, Spotify, Chrome        | Standard Android permissions                          |
| **Privileged Apps** | Pre-installed vendor apps with system permissions | Samsung One UI apps, carrier apps | `priv-app` directory, signed with OEM key             |
| **System Apps**     | AOSP built-in apps                                | Phone, Contacts, Settings, Camera | `system/app` or `system/priv-app`, platform signature |

### Key Characteristics

- Written in **Java** or **Kotlin**
- Each app runs in its own **sandboxed Linux process** with unique UID
- No direct hardware access — must use Framework APIs
- APK/AAB format packaged with resources and code

### Application Categories

```
/system/app/          → System apps (AOSP)
/system/priv-app/     → Privileged system apps (higher permissions)
/vendor/app/          → OEM/vendor pre-installed apps
/data/app/            → User-installed third-party apps
```

---

## 4. Application Framework Layer

Also known as the **Java API Framework**, this layer provides the APIs developers use to build applications. It contains **Managers** — client-side wrappers that communicate with System Services via IPC.

### Key Managers & Services

| Manager               | System Service It Talks To   | Description                                     |
| --------------------- | ---------------------------- | ----------------------------------------------- |
| `ActivityManager`     | `ActivityManagerService`     | App lifecycle, tasks, back stack, memory        |
| `WindowManager`       | `WindowManagerService`       | Window composition, z-order, input dispatch     |
| `PackageManager`      | `PackageManagerService`      | APK install, uninstall, permissions, signatures |
| `NotificationManager` | `NotificationManagerService` | Status bar alerts, heads-up notifications       |
| `LocationManager`     | `LocationManagerService`     | GPS, Wi-Fi, cellular positioning                |
| `CameraManager`       | `CameraService`              | Camera device enumeration and access            |
| `SensorManager`       | `SensorService`              | Accelerometer, gyroscope, light, proximity      |
| `VibratorManager`     | `VibratorManagerService`     | Haptic feedback, vibration control              |
| `PowerManager`        | `PowerManagerService`        | Wake locks, sleep, doze mode                    |
| `TelephonyManager`    | `TelephonyRegistry`          | SIM, network type, signal strength              |
| `ContentResolver`     | Content Providers            | Cross-app data sharing via URIs                 |
| `ViewSystem`          | `WindowManager`              | Layout inflation, rendering, touch events       |

### Framework Characteristics

- Written in **Java** and increasingly **Kotlin**
- Provides **Public APIs** (for all apps) and **System APIs** (require system signature)
- **AIDL interfaces** define the contract between Managers and System Services
- Lives in both app processes (as client stubs) and `system_server` (as service implementations)

### API Categories

| API Type        | Accessibility               | Build Requirement                     |
| --------------- | --------------------------- | ------------------------------------- |
| **Public APIs** | All apps                    | Standard SDK                          |
| **System APIs** | System/privileged apps only | Build with AOSP or platform signature |
| **OEM APIs**    | Vendor-specific             | Vendor SDK                            |

---

## 5. System Services Layer

System Services are the **actual implementations** that run inside the **`system_server`** process. They receive requests from Managers via **Binder IPC** and perform real system-level operations.

### System Server Architecture

```
system_server Process (Single process hosting ~100+ services)
├─ ActivityManagerService
├─ WindowManagerService
├─ PackageManagerService
├─ VibratorManagerService
├─ NotificationManagerService
├─ PowerManagerService
├─ CameraService
├─ SensorService
├─ ... (many more)
```

### System Service Lifecycle

```java
// SystemServer.java - Boot sequence
public static void main(String[] args) {
    startBootstrapServices();   // AMS, PMS, etc.
    startCoreServices();        // Battery, UsageStats
    startOtherServices();       // VibratorManagerService, etc.
}
```

### Key System Services

| Service                  | Manager               | Function                                      |
| ------------------------ | --------------------- | --------------------------------------------- |
| `ActivityManagerService` | `ActivityManager`     | App lifecycle, ANR detection, task management |
| `WindowManagerService`   | `WindowManager`       | Display composition, window hierarchy         |
| `PackageManagerService`  | `PackageManager`      | APK parsing, install, permissions database    |
| `VibratorManagerService` | `VibratorManager`     | Vibration control, HAL coordination           |
| `CameraService`          | `CameraManager`       | Camera HAL management, device policy          |
| `MediaSessionService`    | `MediaSessionManager` | Media playback, remote control                |
| `InputManagerService`    | `InputManager`        | Touch/key event routing                       |

---

## 6. Android Runtime (ART) & JVM

### What is a Runtime Environment?

A **Runtime Environment** is the software infrastructure that executes application code. It provides:

- **Memory management** (allocation, garbage collection)
- **Thread management** (creation, scheduling, synchronization)
- **Bytecode execution** (interpreting or compiling to machine code)
- **Security** (sandboxing, permission enforcement)
- **Standard libraries** (core APIs available to apps)

### JVM (Java Virtual Machine) vs ART (Android Runtime)

| Aspect                    | JVM (Standard Java)                  | ART (Android Runtime)                                      |
| ------------------------- | ------------------------------------ | ---------------------------------------------------------- |
| **Platform**              | Desktop/Server Java                  | Android only                                               |
| **Bytecode Format**       | `.class` files                       | `.dex` (Dalvik Executable)                                 |
| **Compilation**           | JIT (Just-In-Time) at runtime        | AOT (Ahead-Of-Time) at install + hybrid JIT                |
| **Memory Model**          | Heap-based with GC                   | Heap-based with concurrent GC                              |
| **Register Architecture** | Stack-based VM                       | Register-based VM (Dalvik heritage)                        |
| **Process Model**         | Single JVM per app, multiple threads | Multiple ART instances, each app is separate Linux process |
| **File Format**           | `.jar` (ZIP of `.class`)             | `.apk` containing `.dex`                                   |
| **JNI**                   | Standard JNI                         | Android JNI with Binder extensions                         |

### Dalvik VM → ART Evolution

#### Dalvik VM (Android 1.0 – 4.4 KitKat)

```
App Launch
    ↓
Load .dex file
    ↓
JIT Compiler converts bytecode to machine code
    ↓
Execute machine code
    ↓
[Next launch] → Repeat JIT compilation (slow!)
```

**Characteristics:**

- **JIT Compilation**: Compiled at runtime, every time app starts
- **Register-based**: Uses registers instead of stack (optimized for mobile)
- **Memory Efficient**: Lower memory footprint
- **Slower Startup**: Compilation happens at launch
- **Battery Drain**: Higher CPU usage during compilation
- **Process Isolation**: Each app runs as independent Linux process — if VM crashes, other apps survive

#### ART (Android 5.0 Lollipop – Present)

```
App Install
    ↓
dex2oat AOT Compiler converts .dex → .oat (native ELF binary)
    ↓
Store .oat file on disk
    ↓
App Launch
    ↓
Load pre-compiled .oat file directly
    ↓
Execute native machine code immediately (fast!)
```

**Characteristics:**

- **AOT Compilation**: Pre-compiled at install time to native machine code
- **Faster Execution**: No runtime compilation overhead
- **Better Battery**: Less CPU usage during app execution
- **Larger Storage**: `.oat` files take extra space
- **Slower Install**: Installation takes longer due to compilation
- **Hybrid Mode (Android 7.0+)**: Profile-guided compilation — AOT for hot paths, JIT for rare code

### ART Architecture

```
┌─────────────────────────────────────────┐
│           Application Code                │
│         (Java / Kotlin)                   │
├─────────────────────────────────────────┤
│         Core Libraries (Java)             │
│   java.lang, java.util, java.io, etc.     │
├─────────────────────────────────────────┤
│         ART Virtual Machine               │
│  ┌─────────────────────────────────────┐  │
│  │  Compiler (dex2oat / JIT)           │  │
│  │  ├─ AOT: Install-time compilation   │  │
│  │  └─ JIT: Runtime fallback           │  │
│  ├─────────────────────────────────────┤  │
│  │  Garbage Collector                  │  │
│  │  ├─ Concurrent GC                  │  │
│  │  ├─ Compacting GC                  │  │
│  │  └─ Generational GC                │  │
│  ├─────────────────────────────────────┤  │
│  │  Debugger (JDWP)                    │  │
│  └─────────────────────────────────────┘  │
├─────────────────────────────────────────┤
│         JNI & Native Bridge               │
├─────────────────────────────────────────┤
│         Native Libraries (.so)            │
│   libc, OpenGL, SQLite, etc.              │
└─────────────────────────────────────────┘
```

### ART File Locations

```
/data/dalvik-cache/          → Compiled .oat and .vdex files
/system/framework/           → Boot classpath .oat files
/data/app/.../oat/           → Per-app compiled code
```

### Why Android Doesn't Use Standard JVM

| Reason            | Explanation                                                  |
| ----------------- | ------------------------------------------------------------ |
| **Memory**        | Mobile devices have limited RAM — ART is optimized for low memory |
| **Battery**       | JIT compilation drains battery — AOT is more efficient       |
| **Process Model** | Android needs each app as separate process for security — standard JVM doesn't support this well |
| **File Format**   | `.dex` is optimized for mobile (shared constants, smaller size) |
| **Boot Time**     | ART pre-compilation reduces app startup time significantly   |
| **Binder IPC**    | Android needs deep integration with Binder — standard JVM doesn't support this |

---

## 7. Native Daemons & System Services

Native services are **standalone C++ processes** that handle performance-critical or hardware-specific operations outside the Java `system_server`.

### Why Native Services Exist

| Reason              | Explanation                                   |
| ------------------- | --------------------------------------------- |
| **Performance**     | No Java GC overhead, direct memory access     |
| **Stability**       | Crash doesn't kill `system_server`            |
| **Hardware Access** | Direct HAL/driver access without JNI overhead |
| **Real-time**       | Audio/video need low latency                  |

### Key Native Services

| Service                | Process           | Role                                     |
| ---------------------- | ----------------- | ---------------------------------------- |
| `SurfaceFlinger`       | `surfaceflinger`  | Display compositing — builds every frame |
| `MediaServer`          | `mediaserver`     | Audio/video playback, codecs             |
| `CameraServer`         | `cameraserver`    | Camera HAL coordination (Android 7.0+)   |
| `Vibrator HAL Service` | `vendor.vibrator` | Haptic feedback HAL interface            |
| `DrmServer`            | `drmserver`       | DRM content protection                   |
| `Netd`                 | `netd`            | Network interfaces, firewall, tethering  |

### Startup (init.rc)

```bash
service surfaceflinger /system/bin/surfaceflinger
    class core
    user system
    group graphics drmrpc
    onrestart restart zygote

service cameraserver /system/bin/cameraserver
    class main
    user cameraserver
    group camera audio drmrpc
```

---

## 8. Hardware Abstraction Layer (HAL)

The HAL is the **interface between the Android system and hardware vendors**. It protects vendor IP while providing a standardized contract.

### HAL Architecture

```
┌─────────────────────────────────────────┐
│ Android Framework / Native Service        │
│     (Java / C++ code)                     │
├─────────────────────────────────────────┤
│ HAL Interface (Google-defined headers)    │
│   e.g., IVibrator.aidl, vibrator.h        │
├─────────────────────────────────────────┤
│ HAL Implementation (Vendor .so)           │
│   (Proprietary vendor code)               │
├─────────────────────────────────────────┤
│ Linux Kernel Driver                         │
│   (Open source or binary blob)            │
├─────────────────────────────────────────┤
│ Physical Hardware                           │
└─────────────────────────────────────────┘
```

### HAL Modules

| HAL Module        | Hardware Controlled              | Interface        |
| ----------------- | -------------------------------- | ---------------- |
| **Audio HAL**     | Speakers, mic, headphone         | `audio.h`        |
| **Bluetooth HAL** | BT chip, BLE, protocols          | `bluetooth.h`    |
| **Camera HAL**    | Sensor, ISP, flash, AF           | `camera3.h`      |
| **Vibrator HAL**  | Haptic motor, vibration patterns | `IVibrator.aidl` |
| **Sensor HAL**    | Accelerometer, gyro, compass     | `sensors.h`      |
| **Display HAL**   | Screen panel, GPU composer       | `composer.h`     |
| **GPS HAL**       | GNSS hardware                    | `gps.h`          |
| **Wi-Fi HAL**     | WLAN chip                        | `wifi.h`         |
| **NFC HAL**       | Near-field communication         | `nfc.h`          |
| **Power HAL**     | CPU frequency, thermal           | `power.h`        |

### HAL File Locations

```
/hardware/interfaces/          → AIDL/HIDL interface definitions (Google)
/vendor/lib/hw/                → Vendor HAL implementations (.so files)
/system/lib/hw/                → Google reference HALs
```

### Project Treble (Android 8.0+)

HALs are **modularized** so Android framework updates don't require vendor recompilation. The framework and vendor code are separated into different partitions (`/system` vs `/vendor`).

---

## 9. Linux Kernel Layer

The foundation of Android. A modified Linux kernel with Android-specific drivers and subsystems.

### Android-Specific Drivers

| Driver                | Function                        | Why Critical                               |
| --------------------- | ------------------------------- | ------------------------------------------ |
| **Binder IPC**        | Inter-process communication     | Every Framework API call uses Binder       |
| **Ashmem**            | Anonymous shared memory         | Efficient bitmap sharing between processes |
| **Logger**            | Ring-buffer logging (`logcat`)  | `adb logcat` reads from here               |
| **Low Memory Killer** | Process killing before OOM      | Prevents system freezes                    |
| **Alarm**             | Wake device for scheduled tasks | Push notifications, background sync        |
| **Paranoid Network**  | Per-UID network restrictions    | Security — apps can't sniff traffic        |
| **RAM Console**       | Crash logs in RAM               | Post-reboot debugging                      |

### Standard Linux Subsystems

| Subsystem                   | Role in Android                           |
| --------------------------- | ----------------------------------------- |
| **Process Scheduler (CFS)** | CPU time allocation, priorities           |
| **Memory Management**       | Virtual memory, swapping, page allocation |
| **Security (SELinux)**      | Mandatory access control, sandboxing      |
| **Network Stack**           | TCP/IP, Wi-Fi, mobile data, Bluetooth     |
| **File Systems**            | ext4 (system), F2FS (data), VFAT (SD)     |
| **Power Management**        | CPU governors, suspend/resume, wake locks |
| **Device Drivers**          | Display, touchscreen, camera, USB, audio  |

### Kernel Configuration

```
CONFIG_ANDROID=y              → Enable all Android drivers
CONFIG_BINDER_IPC=y           → Binder IPC
CONFIG_ASHMEM=y               → Shared memory
CONFIG_LOW_MEMORY_KILLER=y    → OOM prevention
CONFIG_SECCOMP=y              → Syscall sandboxing
```

### Kernel File Locations

```
/boot/kernel               → Kernel image
/boot/initramfs            → Initial RAM filesystem
/dev/                      → Device nodes (binder, camera, etc.)
/proc/                     → Process information
/sys/                      → System and driver info
```

---

## 10. Service vs Manager vs System Service vs Native Service

### Quick Comparison

| Term               | What It Is                       | Where It Lives          | Language    | Does Real Work?                 | Example                                            |
| ------------------ | -------------------------------- | ----------------------- | ----------- | ------------------------------- | -------------------------------------------------- |
| **Manager**        | Java API wrapper that apps call  | App process (Framework) | Java/Kotlin | ❌ No — just forwards requests   | `VibratorManager`, `CameraManager`                 |
| **Service** (App)  | Background component in your app | Your app's process      | Java/Kotlin | ✅ Yes, but only for your app    | `MusicPlaybackService`                             |
| **System Service** | Core OS service in system_server | `system_server` process | Java + JNI  | ✅ Yes, system-wide coordination | `VibratorManagerService`, `ActivityManagerService` |
| **Native Service** | Low-level C++ daemon             | Separate native process | C/C++       | ✅ Yes, hardware-critical work   | `SurfaceFlinger`, `Vibrator HAL Service`           |
| **JNI**            | Java ↔ C++ bridge                | Shared library (.so)    | C++         | ✅ Yes, translates calls         | `libandroid_runtime.so`                            |
| **HAL**            | Hardware interface               | Vendor .so file         | C/C++       | ✅ Yes, talks to drivers         | `vibrator.default.so`                              |
| **Kernel Driver**  | Hardware controller              | Kernel space            | C           | ✅ Yes, controls physical HW     | `timed_output.ko`                                  |

### Detailed Explanations

#### Manager (Client-Side API)

- **What:** A Java class that provides a clean API for apps
- **Where:** Inside your app's process (loaded from `framework.jar`)
- **Role:** Sends Binder IPC transactions to the real System Service
- **Analogy:** Receptionist — takes your request and passes it to the right department

**Example — `VibratorManager`:**

```java
// In YOUR app process
VibratorManager vm = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
vm.vibrate(effect);  // This just sends a Binder IPC call — no actual vibration happens here!
```

#### System Service (Server-Side Implementation)

- **What:** The actual implementation that receives Binder calls and does the work
- **Where:** `system_server` process (shared with ~100 other services)
- **Role:** Validates permissions, manages state, calls native code via JNI
- **Analogy:** Department head — receives requests, makes decisions, delegates work

**Example — `VibratorManagerService`:**

```java
// In system_server process
public class VibratorManagerService extends SystemService {
    public void vibrate(...) {
        enforcePermission(VIBRATE);  // Check permission
        // ... manage state ...
        nativeVibrate(duration);  // Call JNI to reach native layer
    }
}
```

#### Native Service (Specialized Daemon)

- **What:** Standalone C++ process for performance-critical operations
- **Where:** Separate process (e.g., `surfaceflinger`, `cameraserver`)
- **Role:** Direct hardware access, real-time processing, crash isolation
- **Analogy:** Specialist technician — handles complex, time-sensitive tasks

**Example — `SurfaceFlinger`:**

```cpp
// In surfaceflinger process (separate from system_server!)
class SurfaceFlinger : public BnSurfaceComposer {
    void doComposition() {
        // Directly composites all app windows into final display buffer
        // This must be fast and can't wait for Java GC
        mRenderEngine->drawLayers(layers);
    }
};
```

#### App Service (Your Background Component)

- **What:** One of Android's 4 core components (Activity, Service, BroadcastReceiver, ContentProvider)
- **Where:** Your app's process
- **Role:** Long-running background operations without UI
- **Analogy:** Your own employee — works for your app only

**Example — `MusicPlaybackService`:**

```java
// In YOUR app process
public class MusicPlaybackService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaPlayer player = new MediaPlayer();
        player.setDataSource("song.mp3");
        player.prepare();
        player.start();  // This runs in YOUR app, not system_server!
        return START_STICKY;
    }
}
```

### Complete Flow Comparison

#### Flow 1: Manager → System Service (Vibrator)

```
Your App Process                    system_server Process
├─ VibratorManager (Java)         ├─ VibratorManagerService (Java)
│  └─ vibrate() ──Binder IPC──>     │  ├─ enforcePermission()
│                                    │  └─ nativeVibrate() ──JNI──>
```

#### Flow 2: System Service → Native Service (Camera)

```
system_server Process               cameraserver Process (Native)
├─ CameraService (Java + JNI)       ├─ Camera HAL Service (C++)
│  └─ nativeConnect() ──JNI──>     │  ├─ openCameraDevice()
│  └─ Binder IPC ─────────────>     │  └─ HAL::open()
```

#### Flow 3: Your App Service (Music Playback)

```
Your App Process
├─ MusicPlaybackService (Java)
│  └─ MediaPlayer.start()
│     └─ MediaPlayer (Java) → MediaPlayer JNI → Native Media Server
│        [No system_server involved for playback!]
```

---

## 11. JNI: The Java-Native Bridge

JNI (Java Native Interface) is the **glue** that enables Java code to call C/C++ code and vice versa. It is the critical bridge between the Java Framework layer and the Native layer.

### Where JNI Lives in the Architecture

```
┌─────────────────────────────────────────────┐
│  JAVA SIDE (Application Framework /          │
│  System Service / App Code)                  │
│                                              │
│  VibratorManagerService.java                 │
│  └─ nativeVibrate(long duration)             │
│     [declared as 'native']                   │
│                                              │
│  ↓ JNI CALL                                  │
├─────────────────────────────────────────────┤
│  JNI BRIDGE (C++ Glue Layer)                 │
│                                              │
│  android_os_Vibrator.cpp                     │
│  ├─ JNIEnv* env                              │
│  ├─ Convert Java long → C++ int64_t          │
│  ├─ Call Native HAL / Native Service         │
│  └─ Convert result back to Java              │
│                                              │
│  [libandroid_runtime.so]                     │
│  [libandroid_servers.so]                     │
│                                              │
│  ↓ NATIVE CALL                               │
├─────────────────────────────────────────────┤
│  NATIVE SIDE (C++ Service / HAL / Driver)    │
│                                              │
│  Vibrator HAL Service (C++)                  │
│  ├─ IVibrator::on(milliseconds)              │
│  └─ sysfs_write("/sys/class/.../enable")     │
│                                              │
│  [vendor.vibrator process]                   │
│  [vibrator.default.so]                       │
└─────────────────────────────────────────────┘
```

### JNI Registration Types

| Type                     | How It Works                                            | Example                                  |
| ------------------------ | ------------------------------------------------------- | ---------------------------------------- |
| **Static Registration**  | Method name follows `Java_package_Class_method` pattern | `Java_android_os_Vibrator_nativeVibrate` |
| **Dynamic Registration** | Explicitly register methods via `RegisterNatives()`     | Used in `libandroid_runtime.so`          |

### JNI Example: Vibrator

**Java Side (Framework):**

```java
// frameworks/base/core/java/android/os/Vibrator.java
public abstract class Vibrator {
    // Load native library at class load time
    static {
        System.loadLibrary("vibrator_jni");
    }

    // Declare native method — implementation is in C++
    private static native long nativeInit();
    private static native void nativeVibrate(long duration);

    // Public API that apps call
    public void vibrate(long milliseconds) {
        // This calls into C++ via JNI
        nativeVibrate(milliseconds);
    }
}
```

**C++ Side (JNI Implementation):**

```cpp
// frameworks/base/core/jni/android_os_Vibrator.cpp
#include <jni.h>
#include <hardware/vibrator.h>  // HAL header

// This function is called when Java calls nativeVibrate()
static void android_os_Vibrator_nativeVibrate(JNIEnv* env, jobject clazz, jlong milliseconds) {

    // 1. Get the native vibrator service via ServiceManager / Binder
    sp<IVibrator> vibrator = IVibrator::getService();

    // 2. Call the native HAL method
    // This sends a Binder IPC to the native Vibrator HAL Service
    vibrator->on(static_cast<int32_t>(milliseconds));

    // Note: JNIEnv* is the thread-local JNI environment pointer
    // It provides all JNI functions (FindClass, GetMethodID, etc.)
}

// Register the native method so Java can find it
static JNINativeMethod gVibratorMethods[] = {
    // Java method name        Java signature          C++ function pointer
    {"nativeVibrate",         "(J)V",                 (void*)android_os_Vibrator_nativeVibrate},
    {"nativeInit",            "()J",                  (void*)android_os_Vibrator_nativeInit},
    {"nativeCancel",          "()V",                  (void*)android_os_Vibrator_nativeCancel},
};

// Called when the library is loaded
int register_android_os_Vibrator(JNIEnv* env) {
    jclass clazz = env->FindClass("android/os/Vibrator");
    return env->RegisterNatives(clazz, gVibratorMethods, 
                                sizeof(gVibratorMethods)/sizeof(gVibratorMethods[0]));
}
```

### JNI Data Type Mapping

| Java Type | JNI Type   | C/C++ Type                      |
| --------- | ---------- | ------------------------------- |
| `boolean` | `jboolean` | `uint8_t`                       |
| `byte`    | `jbyte`    | `int8_t`                        |
| `char`    | `jchar`    | `uint16_t`                      |
| `short`   | `jshort`   | `int16_t`                       |
| `int`     | `jint`     | `int32_t`                       |
| `long`    | `jlong`    | `int64_t`                       |
| `float`   | `jfloat`   | `float`                         |
| `double`  | `jdouble`  | `double`                        |
| `Object`  | `jobject`  | `void*`                         |
| `String`  | `jstring`  | `char*` (via GetStringUTFChars) |
| `Array`   | `jarray`   | `void*`                         |

### JNI File Locations in AOSP

| Path                                          | Contents                                                |
| --------------------------------------------- | ------------------------------------------------------- |
| `frameworks/base/core/jni/`                   | Core framework JNI (graphics, input, vibrator, sensors) |
| `frameworks/base/services/core/jni/`          | System service JNI                                      |
| `frameworks/base/media/jni/`                  | Media framework JNI                                     |
| `libnativehelper/`                            | JNI helper utilities                                    |
| `frameworks/base/core/jni/AndroidRuntime.cpp` | ART startup and JNI bridge initialization               |

### JNI in the Compilation Pipeline

```
Java Source (.java)
    ↓
Javac → .class (bytecode)
    ↓
D8 / dx → .dex
    ↓
dex2oat (AOT) → .oat (ELF with native code)
    ↓
Runtime: Java method call → JNI lookup → C++ function execution
```

---

## 12. IPC Mechanisms: AIDL & HIDL

Android uses **Inter-Process Communication (IPC)** extensively because components run in different processes with different security contexts.

### AIDL (Android Interface Definition Language)

**Used for:** Java-to-Java or Java-to-C++ communication between processes.

**Where it's used:**

- App → System Service (e.g., `VibratorManager` → `VibratorManagerService`)
- System Service → Native Service (e.g., `VibratorManagerService` → Vibrator HAL Service)

**Example AIDL Interface:**

```aidl
// IVibratorManagerService.aidl
interface IVibratorManagerService {
    boolean isVibrating();
    void vibrate(in VibrationEffect effect, in IBinder token);
    void cancelVibrate(in IBinder token);
}
```

### HIDL (HAL Interface Definition Language) — Legacy

**Used for:** C++-to-C++ communication between System Services and HAL (Android 8.0–11.0).

**Replaced by:** AIDL for HAL interfaces (Android 12.0+).

### Binder IPC Flow

```
Process A (App)
    └── VibratorManager (Java stub)
            └── Binder Driver (kernel)
                    └── Process B (system_server)
                            └── VibratorManagerService (Java implementation)
                                    └── JNI
                                            └── Process C (native daemon)
                                                    └── Vibrator HAL Service (C++)
                                                            └── HAL Interface
                                                                    └── Kernel Driver
```

### Two AIDL Interfaces in Vibrator Example

1. **Framework → System Service:** `IVibratorManagerService.aidl`
2. **System Service → Native HAL Service:** `IVibrator.aidl` (in `hardware/interfaces/`)

---

## 13. Real-World Example: Vibrator Flow (App → HAL → Kernel)

This traces a complete `vibrate()` call from your app button click to the physical haptic motor.

### Step-by-Step Flow

#### Step 1: Application Layer (Your App)

```java
// Your app's Activity
public class MainActivity extends AppCompatActivity {

    public void onVibrateButtonClick(View view) {
        // Get the VibratorManager (API 31+)
        VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);

        // Create vibration effect
        VibrationEffect effect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE);

        // This calls the Manager (Framework API)
        vibratorManager.vibrate(effect);
    }
}
```

**Location:** Your app's process (`com.example.myapp`)
**Permission required:** `android.permission.VIBRATE` in AndroidManifest.xml

---

#### Step 2: Application Framework (VibratorManager)

```java
// frameworks/base/core/java/android/os/VibratorManager.java
public abstract class VibratorManager {

    public void vibrate(VibrationEffect effect) {
        // This is an abstract method — implemented by SystemVibratorManager
        // It sends a Binder IPC call to VibratorManagerService
        vibrate(CombinedVibration.createParallel(effect));
    }
}
```

**Location:** `frameworks/base/core/java/android/os/VibratorManager.java`
**Role:** Client-side API wrapper — forwards to System Service via AIDL

---

#### Step 3: System Service (VibratorManagerService)

```java
// frameworks/base/services/core/java/com/android/server/vibrator/VibratorManagerService.java
public class VibratorManagerService extends SystemService {

    private final IVibratorManagerServiceStub mService = new IVibratorManagerServiceStub() {

        @Override
        public void vibrate(int uid, String opPkg, CombinedVibration vibration, 
                           IBinder token, String reason) {
            // Validate permission
            enforcePermission(android.Manifest.permission.VIBRATE);

            // Coordinate with native vibrator HAL service via JNI/AIDL
            for (VibratorController controller : mVibrators) {
                controller.vibrate(vibration);  // → JNI → Native
            }
        }
    };
}
```

**Location:** `frameworks/base/services/core/java/com/android/server/vibrator/`
**Process:** `system_server`
**Role:** Receives Binder IPC, validates permissions, manages state, delegates to native layer

---

#### Step 4: JNI Bridge

```cpp
// frameworks/base/core/jni/android_os_Vibrator.cpp
static void android_os_Vibrator_on(JNIEnv* env, jobject clazz, jlong milliseconds) {
    // Convert Java call to native Binder call
    sp<IVibrator> vibrator = IVibrator::getService();

    // Call native HAL service via AIDL/Binder
    vibrator->on(milliseconds);
}
```

**Location:** `frameworks/base/core/jni/`
**Role:** Translates Java objects to C++ types, makes native Binder calls

---

#### Step 5: Native HAL Service (C++ Daemon)

```cpp
// hardware/interfaces/vibrator/aidl/default/Vibrator.cpp
// OR vendor-specific implementation

class Vibrator : public BnVibrator {
public:
    ndk::ScopedAStatus on(int32_t milliseconds) override {
        // Call HAL interface to control hardware
        return mHwApi->on(milliseconds);  // → Kernel driver
    }

    ndk::ScopedAStatus off() override {
        return mHwApi->off();
    }
};
```

**Location:** `hardware/interfaces/vibrator/aidl/default/` (AOSP reference) or vendor-specific path
**Process:** `vendor.vibrator` (or similar vendor process)
**Role:** Receives native AIDL calls, controls hardware via HAL APIs

---

#### Step 6: HAL Interface

```cpp
// hardware/interfaces/vibrator/aidl/android/hardware/vibrator/IVibrator.aidl
interface IVibrator {
    void on(int32_t milliseconds);
    void off();
    void setAmplitude(float amplitude);
}
```

**Location:** `hardware/interfaces/vibrator/aidl/`
**Role:** Defines the contract between System Service and vendor implementation

---

#### Step 7: Kernel Driver

```c
// kernel/drivers/misc/vibrator.c (example)
static ssize_t vibrator_enable_store(struct device *dev, 
                                      struct device_attribute *attr,
                                      const char *buf, size_t size) {
    // Parse duration from userspace
    int value;
    sscanf(buf, "%d", &value);

    // Control GPIO or PWM to activate haptic motor
    if (value > 0) {
        gpio_set_value(VIBRATOR_GPIO, 1);  // Turn ON
        msleep(value);                       // Wait for duration
        gpio_set_value(VIBRATOR_GPIO, 0);    // Turn OFF
    }

    return size;
}
```

**Location:** Kernel source tree (`kernel/drivers/`)
**Space:** Kernel space
**Role:** Direct hardware control via GPIO/PWM/registers

---

#### Step 8: Physical Hardware

```
Haptic Motor (ERM or LRA)
    ↓
Receives electrical signal from GPIO/PWM
    ↓
Vibrates for specified duration
```

---

### Complete Vibrator Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. APPLICATION LAYER (Your App Process)                                 │
│                                                                         │
│  User taps "Vibrate" button                                             │
│  MainActivity.onClick()                                                 │
│  └── getSystemService(VIBRATOR_MANAGER_SERVICE)                         │
│      └── Returns VibratorManager (Java stub in your process)            │
│          └── vibratorManager.vibrate(effect)                            │
│                                                                         │
│  [Requires: android.permission.VIBRATE]                                   │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Binder IPC (AIDL)
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. APPLICATION FRAMEWORK (App + System Server)                          │
│                                                                         │
│  VibratorManager (Java abstract class)                                  │
│  └── SystemVibratorManager (implementation)                               │
│      └── Sends Binder transaction via IVibratorManagerService.aidl        │
│                                                                         │
│  [AIDL Interface: IVibratorManagerService]                                │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Binder IPC
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. SYSTEM SERVICES (system_server Process)                             │
│                                                                         │
│  VibratorManagerService extends SystemService                             │
│  ├── Receives Binder call from app                                      │
│  ├── enforcePermission(VIBRATE)                                         │
│  ├── Checks if already vibrating                                          │
│  └── For each vibrator: controller.vibrate()                            │
│      └── JNI call to native layer                                         │
│                                                                         │
│  [Java + JNI, Runs in system_server]                                    │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ JNI
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. JNI BRIDGE (libandroid_runtime.so)                                   │
│                                                                         │
│  android_os_Vibrator.cpp                                                │
│  ├── JNIEnv* env conversion                                             │
│  ├── Get native IVibrator service via ServiceManager                    │
│  └── vibrator->on(milliseconds)  // Native AIDL call                    │
│                                                                         │
│  [C++ Glue Code]                                                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Binder IPC (Native AIDL)
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. NATIVE HAL SERVICE (Vendor Process)                                  │
│                                                                         │
│  Vibrator HAL Service (C++)                                             │
│  ├── BnVibrator implementation                                          │
│  ├── Receives native Binder call                                        │
│  └── mHwApi->on(milliseconds)  // HAL interface call                     │
│                                                                         │
│  [C++ Daemon, Vendor-specific]                                          │
│  [Process: vendor.vibrator or similar]                                  │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ HAL Interface
┌─────────────────────────────────────────────────────────────────────────┐
│ 6. HAL IMPLEMENTATION (Vendor .so)                                      │
│                                                                         │
│  vibrator.default.so (or vendor-specific)                                 │
│  ├── Implements IVibrator AIDL interface                                │
│  ├── May use sysfs: /sys/class/timed_output/vibrator/enable             │
│  └── Writes to kernel driver                                            │
│                                                                         │
│  [Vendor Proprietary Code]                                              │
│  [Location: /vendor/lib/hw/ or /vendor/lib64/hw/]                       │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Sysfs / IOCTL
┌─────────────────────────────────────────────────────────────────────────┐
│ 7. LINUX KERNEL (Kernel Space)                                          │
│                                                                         │
│  Vibrator Driver (e.g., timed_output.ko)                                  │
│  ├── Receives enable command via sysfs node                             │
│  ├── Sets GPIO or PWM pin HIGH                                          │
│  ├── Starts timer for duration                                          │
│  └── Sets GPIO LOW when timer expires                                   │
│                                                                         │
│  [Kernel Module, Direct Hardware Access]                                │
│  [Location: kernel/drivers/misc/ or kernel/drivers/input/]              │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Electrical Signal
┌─────────────────────────────────────────────────────────────────────────┐
│ 8. PHYSICAL HARDWARE                                                    │
│                                                                         │
│  Haptic Motor (ERM or LRA)                                              │
│  ├── Receives voltage from GPIO/PWM                                     │
│  ├── Generates vibration                                                │
│  └── Stops when signal removed                                          │
│                                                                         │
│  [Physical Component on PCB]                                            │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 14. Complete Data Flow Architecture

### Cross-Layer Communication Summary

| From           | To             | Mechanism               | Example                      |
| -------------- | -------------- | ----------------------- | ---------------------------- |
| App            | Framework      | Direct Java call        | `getSystemService()`         |
| Framework      | System Service | **AIDL / Binder IPC**   | `IVibratorManagerService`    |
| System Service | JNI            | Native method call      | `nativeVibrate()`            |
| JNI            | Native Service | **AIDL / Binder IPC**   | `IVibrator`                  |
| Native Service | HAL            | Function call / AIDL    | `mHwApi->on()`               |
| HAL            | Kernel         | Sysfs / IOCTL / Netlink | `echo 500 > /sys/.../enable` |
| Kernel         | Hardware       | GPIO / PWM / I2C / SPI  | Electrical signal            |

### Two IPC Boundaries in Vibrator Example

1. **First IPC (Java → Java):** `VibratorManager` (app) → `VibratorManagerService` (system_server) via `IVibratorManagerService.aidl`
2. **Second IPC (Java/C++ → C++):** `VibratorManagerService` (system_server) → `Vibrator HAL Service` (native daemon) via `IVibrator.aidl`

---

## 15. Key Takeaways

1. **Abstract Layers:** Android's layers are conceptual boundaries, not physical walls. Components communicate across layers via well-defined interfaces.

2. **Manager ≠ Service:** A Manager is a client-side wrapper in your app. The System Service is the real implementation in `system_server`.

3. **Two IPC Hops:** Most hardware operations require two Binder IPC calls: (1) App → System Service, (2) System Service → Native HAL Service.

4. **JNI is the Bridge:** JNI connects Java Framework code to C++ native code. It lives in shared libraries (`libandroid_runtime.so`, etc.).

5. **HAL Protects IP:** The Hardware Abstraction Layer lets vendors write proprietary code without exposing it, while maintaining a standard interface.

6. **Binder is the Backbone:** Every cross-process communication in Android uses Binder IPC — it's the most important Android-specific kernel addition.

7. **ART vs JVM vs Dalvik:**
   - **JVM**: Standard Java runtime, stack-based, JIT only, desktop/server
   - **Dalvik**: Android's old runtime, register-based, JIT, mobile-optimized
   - **ART**: Android's current runtime, register-based, AOT + hybrid JIT, fastest

8. **Native Services for Stability:** Critical services (SurfaceFlinger, CameraServer) run in separate processes so crashes don't bring down `system_server`.

9. **Permission Enforcement:** System Services enforce permissions at the Framework layer before allowing hardware access.

10. **Project Treble Separation:** Android 8.0+ separates `/system` (Google) and `/vendor` (OEM) partitions, allowing independent updates.

11. **JNI Location:** JNI code lives in `frameworks/base/core/jni/` and is compiled into `libandroid_runtime.so` and `libandroid_servers.so`.

12. **Runtime Environment:** ART provides the complete execution environment — memory management, thread scheduling, bytecode compilation, and garbage collection — optimized specifically for mobile resource constraints.

---

## 16. File Locations in AOSP

### Vibrator Example File Paths (Android 15)

| Component                                | AOSP Path                                                    |
| ---------------------------------------- | ------------------------------------------------------------ |
| **Framework API**                        | `frameworks/base/core/java/android/os/Vibrator.java`         |
| **VibratorManager**                      | `frameworks/base/core/java/android/os/VibratorManager.java`  |
| **System Service**                       | `frameworks/base/services/core/java/com/android/server/vibrator/VibratorManagerService.java` |
| **AIDL Interface (Framework → Service)** | `frameworks/base/core/java/android/os/IVibratorManagerService.aidl` |
| **AIDL Interface (Service → HAL)**       | `hardware/interfaces/vibrator/aidl/android/hardware/vibrator/IVibrator.aidl` |
| **JNI Implementation**                   | `frameworks/base/core/jni/android_os_Vibrator.cpp`           |
| **Native HAL Service**                   | `hardware/interfaces/vibrator/aidl/default/Vibrator.cpp`     |
| **HAL Interface Definition**             | `hardware/interfaces/vibrator/aidl/`                         |
| **Vendor Implementation**                | `vendor/.../vibrator/` (vendor-specific)                     |
| **Kernel Driver**                        | `kernel/drivers/misc/vibrator.c` or `kernel/drivers/input/misc/vibrator.c` |
| **Init Config (Service Startup)**        | `device/.../init.rc` or `system/core/rootdir/init.rc`        |

### General AOSP Directory Structure

```
frameworks/base/
├── core/java/android/os/           → Framework APIs (Vibrator, VibratorManager)
├── core/jni/                        → JNI implementations
├── services/core/java/.../vibrator/ → System Services (VibratorManagerService)
└── services/core/jni/               → Service JNI code

hardware/interfaces/
├── vibrator/aidl/                   → Vibrator HAL AIDL interfaces
├── camera/aidl/                     → Camera HAL interfaces
├── sensors/aidl/                    → Sensor HAL interfaces
└── ...

system/
├── core/rootdir/init.rc             → Native service startup
└── ...

kernel/
├── drivers/misc/vibrator.c          → Vibrator kernel driver
├── drivers/android/binder.c         → Binder IPC driver
└── ...
```

---

## 17. References

- Android Open Source Project (AOSP) — https://source.android.com/
- Android Developers Documentation — https://developer.android.com/
- "Android Architecture Explained in Detail" — YouTube Educational Content
- "Android Architecture: A Real-World Example (Vibrator from App Layer to HAL)" — YouTube Educational Content
- Linux Kernel Documentation — https://www.kernel.org/doc/html/latest/
- Android Runtime (ART) Documentation — https://source.android.com/docs/core/runtime
- Project Treble Documentation — https://source.android.com/docs/core/architecture/treble
- HAL Interface Definition (AIDL) — https://source.android.com/docs/core/architecture/hidl
- JNI Specification — https://docs.oracle.com/javase/8/docs/technotes/guides/jni/

---
