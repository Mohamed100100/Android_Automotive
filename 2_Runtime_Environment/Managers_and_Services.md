# Android Service Manager vs Framework Managers: Complete Guide

A comprehensive guide to understanding the Service Manager, Framework Managers, System Services, and Native Services in Android — how they differ, how they interact, and when to use each approach.

---

## Table of Contents

1. [The Big Picture: Two Types of "Manager"](#1-the-big-picture-two-types-of-manager)
2. [Framework Managers (ActivityManager, LocationManager, etc.)](#2-framework-managers-activitymanager-locationmanager-etc)
3. [Service Manager (The Registry)](#3-service-manager-the-registry)
4. [How They Work Together](#4-how-they-work-together)
5. [Manager vs Service: The Critical Difference](#5-manager-vs-service-the-critical-difference)
6. [System Services vs Native Services](#6-system-services-vs-native-services)
7. [Direct ServiceManager Access vs Custom Manager Pattern](#7-direct-servicemanager-access-vs-custom-manager-pattern)
8. [When to Use Which Approach](#8-when-to-use-which-approach)
9. [Hidden API Policy (Android 9+)](#9-hidden-api-policy-android-9)
10. [Complete Implementation: Custom Manager + System Service](#10-complete-implementation-custom-manager--system-service)
11. [Architecture Comparison](#11-architecture-comparison)
12. [Key Takeaways](#12-key-takeaways)

---

## 1. The Big Picture: Two Types of "Manager"

In Android, the word "Manager" appears in two completely different contexts. This is the source of much confusion.

### The Two Types

| Type                   | Examples                                                     | What It Actually Is                                          | Where It Lives                                              | Analogy                         |
| ---------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ | ----------------------------------------------------------- | ------------------------------- |
| **Framework Managers** | `ActivityManager`, `LocationManager`, `PackageManager`, `CameraManager`, `VibratorManager`, `WindowManager`, etc. | **Client-side API wrappers** that apps use to talk to System Services | Inside **your app's process**                               | Department receptionist         |
| **Service Manager**    | `ServiceManager`                                             | **Registry/directory service** that keeps track of ALL system and native services | Inside **`system_server`** and **`servicemanager`** process | Hospital phone book / directory |

### Visual Summary

```
┌─────────────────────────────────────────────────────────────┐
│  YOUR APP PROCESS                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  Framework Managers (Java stubs)                        ││
│  │  • ActivityManager     • LocationManager                ││
│  │  • CameraManager       • VibratorManager                  ││
│  │  • PackageManager      • WindowManager                    ││
│  │  • NotificationManager • SensorManager                  ││
│  │  ALL of them internally call:                             ││
│  │  ServiceManager.getService("name") ──────┐              ││
│  └───────────────────────────────────────────┘              ││
└─────────────────────────────────────────────┘               ││
                                              │               ││
                                              ▼               ││
┌─────────────────────────────────────────────────────────────┐│
│  SERVICE MANAGER (The Registry)                               ││
│  "activity"     → ActivityManagerService handle               ││
│  "location"     → LocationManagerService handle               ││
│  "camera"       → CameraService handle                        ││
│  "vibrator"     → VibratorManagerService handle               ││
│  "media.player" → MediaPlayerService handle                   ││
│  "SurfaceFlinger" → SurfaceFlinger handle                     ││
│  ... (100+ services)                                          ││
└─────────────────────────────────────────────────────────────┘│
                                              │               │
                                              ▼               │
┌─────────────────────────────────────────────────────────────┐
│  SYSTEM SERVICES (Real Implementations)                       │
│  ActivityManagerService    LocationManagerService             │
│  CameraService             VibratorManagerService             │
│  PackageManagerService     WindowManagerService               │
│  MediaPlayerService        SurfaceFlinger                     │
│  ... THESE do the real work!                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Framework Managers (ActivityManager, LocationManager, etc.)

### What They Are

Framework Managers are **Java classes** that provide a clean, high-level API for apps to request system operations. They are **client stubs** — thin wrappers that live in your app's process and forward requests to the real System Services via Binder IPC.

### Key Characteristics

| Characteristic      | Description                                                  |
| ------------------- | ------------------------------------------------------------ |
| **Location**        | Lives in **your app's process** (loaded from `/system/framework/framework.jar`) |
| **Language**        | Written in **Java/Kotlin**                                   |
| **Role**            | **Client** — sends requests, receives responses              |
| **Does real work?** | **NO** — only forwards requests via Binder IPC               |
| **How many**        | One per feature (~20+ different managers)                    |
| **Created by**      | Google (part of Android Framework)                           |
| **Used by**         | All apps (system and third-party)                            |

### Example — LocationManager

```java
// This code runs IN YOUR APP PROCESS
// The LocationManager is just a thin wrapper

LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

// This line sends a Binder IPC transaction to LocationManagerService in system_server
// NO actual GPS work happens in your app!
lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, listener);
```

### List of Framework Managers

| Manager               | System Service It Talks To   | Purpose                                      |
| --------------------- | ---------------------------- | -------------------------------------------- |
| `ActivityManager`     | `ActivityManagerService`     | App lifecycle, tasks, back stack, memory     |
| `LocationManager`     | `LocationManagerService`     | GPS, Wi-Fi, cellular positioning             |
| `PackageManager`      | `PackageManagerService`      | App install, uninstall, permissions          |
| `NotificationManager` | `NotificationManagerService` | Status bar alerts, heads-up notifications    |
| `CameraManager`       | `CameraService`              | Camera device enumeration and access         |
| `SensorManager`       | `SensorService`              | Hardware sensors (accelerometer, gyro, etc.) |
| `VibratorManager`     | `VibratorManagerService`     | Haptic feedback, vibration control           |
| `WindowManager`       | `WindowManagerService`       | Window positioning, z-order, animations      |
| `PowerManager`        | `PowerManagerService`        | Wake locks, sleep, doze mode                 |
| `TelephonyManager`    | `TelephonyRegistry`          | SIM, network type, signal strength           |
| `AudioManager`        | `AudioService`               | Volume, audio routing, focus                 |
| `ConnectivityManager` | `ConnectivityService`        | Wi-Fi, mobile data, VPN                      |
| `StorageManager`      | `StorageManagerService`      | File systems, external storage               |
| `BatteryManager`      | `BatteryService`             | Battery level, charging status               |
| `KeyguardManager`     | `KeyguardService`            | Lock screen, device security                 |

### Why Framework Managers Exist

| Benefit               | Explanation                                                  |
| --------------------- | ------------------------------------------------------------ |
| **Abstraction**       | Apps don't need to know how GPS hardware works — just call `LocationManager.requestLocationUpdates()` |
| **Type Safety**       | Provides typed Java APIs instead of raw Binder handles       |
| **Caching**           | Can cache results to reduce IPC overhead                     |
| **Convenience**       | Wraps complex IPC into simple method calls                   |
| **Consistency**       | All apps use the same APIs, ensuring uniform behavior        |
| **Permission Checks** | Validates permissions before sending IPC                     |

---

## 3. Service Manager (The Registry)

### What It Is

The **Service Manager** is a **registry/directory service** that keeps track of ALL system services and native services running on the Android device. It maps **service names** (strings) to **Binder handles** (objects) so that clients can find and connect to services.

Think of it as the **phone book** of the Android system — you look up a name, and it gives you the number to call.

### Key Characteristics

| Characteristic      | Description                                                  |
| ------------------- | ------------------------------------------------------------ |
| **Location**        | Lives in **`system_server`** (Java side) and **`servicemanager`** (native C++ process) |
| **Language**        | Core written in **C++**, with Java wrapper                   |
| **Role**            | **Registry** — stores "service name → Binder object" mappings |
| **Does real work?** | **NO** — only returns service handles, never performs app operations |
| **How many**        | **Only ONE** in the entire system                            |
| **Created by**      | Google (core Android infrastructure)                         |
| **Used by**         | Framework Managers, system services, native daemons          |

### How Service Manager Works

#### Step 1: Service Registration (at boot time)

```cpp
// In system_server or native daemon startup
// Services register themselves with Service Manager

void onFirstRef() {
    // Register this service with Service Manager
    sp<IServiceManager> sm = defaultServiceManager();
    sm->addService(
        String16("media.player"),    // Service name (string key)
        new MediaPlayerService()       // Binder object (the actual service)
    );
}
```

#### Step 2: Client Lookup (when app needs a service)

```java
// Framework Manager internally does this:
IBinder binder = ServiceManager.getService("location");
ILocationManager service = ILocationManager.Stub.asInterface(binder);
```

### Service Manager Registry Contents

| Service Name            | Binder Object            | Type                  |
| ----------------------- | ------------------------ | --------------------- |
| `"activity"`            | `ActivityManagerService` | System Service (Java) |
| `"location"`            | `LocationManagerService` | System Service (Java) |
| `"package"`             | `PackageManagerService`  | System Service (Java) |
| `"camera"`              | `CameraService`          | System Service (Java) |
| `"vibrator"`            | `VibratorManagerService` | System Service (Java) |
| `"media.player"`        | `MediaPlayerService`     | Native Service (C++)  |
| `"media.audio_flinger"` | `AudioFlinger`           | Native Service (C++)  |
| `"SurfaceFlinger"`      | `SurfaceFlinger`         | Native Service (C++)  |
| `"inputflinger"`        | `InputFlinger`           | Native Service (C++)  |
| `"alarm"`               | `AlarmManagerService`    | System Service (Java) |
| `"power"`               | `PowerManagerService`    | System Service (Java) |
| `"window"`              | `WindowManagerService`   | System Service (Java) |

### Service Manager Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  YOUR APP PROCESS                                             │
│  ┌─────────────────────────────────────────────────────────┐│
│  │  LocationManager (Java stub)                            ││
│  │  └─ getSystemService(LOCATION_SERVICE)                  ││
│  │     └─ ServiceManager.getService("location")  ───────────┼─┐
│  └─────────────────────────────────────────────────────────┘│ │
└─────────────────────────────────────────────────────────────┘ │
                                              │                 │
                                              ▼ Binder IPC      │
┌─────────────────────────────────────────────────────────────┐ │
│  servicemanager PROCESS (Native C++ daemon)                   │ │
│  ┌─────────────────────────────────────────────────────────┐│ │
│  │  ServiceManager (Registry)                                ││ │
│  │  ├── Receives: "getService(location)"                   ││ │
│  │  ├── Looks up: "location" → LocationManagerService      ││ │
│  │  └── Returns: Binder proxy ◄────────────────────────────┼─┘ │
│  └─────────────────────────────────────────────────────────┘│   │
└─────────────────────────────────────────────────────────────┘   │
                                              │                     │
                                              ▼ Binder IPC          │
┌─────────────────────────────────────────────────────────────┐     │
│  system_server PROCESS                                        │     │
│  ┌─────────────────────────────────────────────────────────┐│     │
│  │  LocationManagerService (Real Implementation)           ││     │
│  │  └── Receives actual location request ◄─────────────────┼─────┘ │
│  │     └── Does real work: GPS, Wi-Fi, cellular          ││       │
│  └─────────────────────────────────────────────────────────┘│       │
└─────────────────────────────────────────────────────────────┘       │
```

---

## 4. How They Work Together

### The Complete Flow: From App to Hardware

```
┌─────────────────────────────────────────────────────────────────────────┐
│ 1. APPLICATION LAYER (Your App)                                         │
│                                                                         │
│  User taps "Get Location" button                                        │
│  Your app calls:                                                        │
│  LocationManager lm = getSystemService(LOCATION_SERVICE);               │
│  lm.requestLocationUpdates(GPS_PROVIDER, listener);                       │
│                                                                         │
│  [Framework Manager in your app — just a stub]                        │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Internally calls ServiceManager
┌─────────────────────────────────────────────────────────────────────────┐
│ 2. SERVICE MANAGER (Registry)                                           │
│                                                                         │
│  ServiceManager.getService("location")                                  │
│  ├── Looks up registry: "location" → LocationManagerService handle      │
│  └── Returns Binder proxy to LocationManagerService                   │
│                                                                         │
│  [Registry only — no location logic here]                               │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Binder IPC
┌─────────────────────────────────────────────────────────────────────────┐
│ 3. SYSTEM SERVICE (Real Work)                                           │
│                                                                         │
│  LocationManagerService in system_server                                  │
│  ├── Validates permission (ACCESS_FINE_LOCATION)                        │
│  ├── Checks if GPS is enabled                                           │
│  ├── Registers your app as a location listener                            │
│  └── Calls native GPS HAL via JNI                                       │
│                                                                         │
│  [Does REAL work — permissions, state, hardware coordination]           │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ JNI
┌─────────────────────────────────────────────────────────────────────────┐
│ 4. NATIVE SERVICE / HAL (Hardware Access)                              │
│                                                                         │
│  GPS HAL Service (C++)                                                  │
│  ├── Configures GPS chipset                                             │
│  ├── Starts satellite acquisition                                       │
│  └── Returns location data to Java layer                                │
│                                                                         │
│  [Direct hardware access — C++ native code]                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Callback
┌─────────────────────────────────────────────────────────────────────────┐
│ 5. BACK TO YOUR APP (Callback)                                          │
│                                                                         │
│  LocationListener.onLocationChanged(Location location)                  │
│  └── Your app receives latitude, longitude, accuracy                  │
│                                                                         │
│  [Result delivered via Binder IPC callback]                             │
└─────────────────────────────────────────────────────────────────────────┘
```

### Key Insight

**All Framework Managers use ServiceManager internally.** There is no other way for a Manager to find its corresponding System Service. The ServiceManager is the single source of truth for service discovery in Android.

---

## 5. Manager vs Service: The Critical Difference

This is the most important distinction in Android architecture. The words "Manager" and "Service" refer to completely different things that live in different places and do different jobs.

### Quick Comparison

| Aspect                   | **Manager** (e.g., `LocationManager`) | **Service** (e.g., `LocationManagerService`)             |
| ------------------------ | ------------------------------------- | -------------------------------------------------------- |
| **What it is**           | Client-side API wrapper               | Server-side implementation                               |
| **Where it lives**       | **Your app's process**                | **`system_server` process** (or separate native process) |
| **Language**             | Java/Kotlin                           | Java (System Service) or C++ (Native Service)            |
| **Role**                 | Sends requests                        | Receives and executes requests                           |
| **Does real work?**      | **NO** — just forwards                | **YES** — validates, manages, executes                   |
| **Analogy**              | Receptionist                          | Doctor who actually treats you                           |
| **How many instances**   | One per app process                   | One system-wide instance                                 |
| **Created by**           | Google (Framework)                    | Google (System) or Vendor (Native)                       |
| **How apps access it**   | `getSystemService()`                  | Never directly — only via Manager                        |
| **Process crash impact** | Only your app crashes                 | Can crash entire system                                  |

### The Four Types Compared

```
┌─────────────────────────────────────────────────────────────────────────┐
│ YOUR APP PROCESS                                                        │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ MANAGER (Java)                                                      │ │
│ │  • LocationManager, CameraManager, VibratorManager                  │ │
│ │  • Lives in YOUR app process                                        │ │
│ │  • Just a wrapper — sends Binder IPC calls                          │ │
│ │  • Does NOT do real work                                            │ │
│ │  • Analogy: Receptionist                                            │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ APP SERVICE (Java)                                                  │ │
│ │  • MusicPlaybackService, DownloadService                              │ │
│ │  • Lives in YOUR app process                                        │ │
│ │  • Does real work but only for YOUR app                             │ │
│ │  • Analogy: Your own employee                                         │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ Binder IPC
┌─────────────────────────────────────────────────────────────────────────┐
│ SYSTEM_SERVER PROCESS                                                   │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ SYSTEM SERVICE (Java + JNI)                                         │ │
│ │  • LocationManagerService, CameraService, ActivityManagerService    │ │
│ │  • Lives in system_server (shared with ~100 services)               │ │
│ │  • Does REAL work: validates permissions, manages state, delegates  │ │
│ │  • Calls JNI to reach native layer                                  │ │
│ │  • Analogy: Department head / Doctor                                │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼ JNI / Binder IPC
┌─────────────────────────────────────────────────────────────────────────┐
│ SEPARATE NATIVE PROCESS                                                 │
│ ┌─────────────────────────────────────────────────────────────────────┐ │
│ │ NATIVE SERVICE (C++)                                                │ │
│ │  • SurfaceFlinger, MediaServer, CameraServer                        │ │
│ │  • Lives in its own process (crash isolation)                       │ │
│ │  • Does hardware-critical work, direct HAL access                   │ │
│ │  • Analogy: Specialist technician                                   │ │
│ └─────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

### Detailed Breakdown

#### 1. Manager (Client-Side API Wrapper)

**What it is:** A Java class that provides a clean API for apps. It lives in your app's process and sends requests to the real System Service.

**Example — LocationManager:**

```java
// This runs IN YOUR APP PROCESS
// LocationManager is just a thin wrapper

LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

// This sends a Binder IPC call to LocationManagerService in system_server
// NO actual GPS work happens here!
lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, listener);
```

**Key traits:**

- Lives in **your app process**
- Does **NOT** do real work
- Just **forwards** requests via Binder IPC
- Provides **type safety** and **caching**
- **Analogy:** Receptionist — takes your request, passes it to the doctor

#### 2. System Service (Server-Side Implementation)

**What it is:** The actual implementation that receives Binder calls and performs real system-level operations. Lives in `system_server`.

**Example — LocationManagerService:**

```java
// This runs IN THE SYSTEM_SERVER PROCESS
// This is the REAL implementation

public class LocationManagerService extends SystemService {

    @Override
    public void requestLocationUpdates(String provider, LocationRequest request, 
                                       ILocationListener listener) {

        // 1. VALIDATE PERMISSION
        mContext.enforceCallingPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            "requestLocationUpdates"
        );

        // 2. CHECK STATE
        if (!isGPSEnabled()) {
            throw new SecurityException("GPS is disabled");
        }

        // 3. MANAGE LISTENERS
        synchronized (mLock) {
            mListeners.register(listener);
        }

        // 4. CALL NATIVE HAL VIA JNI
        nativeStartGPS(request.getInterval());
    }
}
```

**Key traits:**

- Lives in **`system_server` process**
- Does **REAL work** — permissions, state, hardware coordination
- **Validates** security before executing
- **Manages** system-wide state
- **Calls JNI** to reach native layer
- **Analogy:** Department head / Doctor — receives requests, makes decisions, delegates

#### 3. App Service (Your Background Component)

**What it is:** One of Android's four core components (Activity, Service, BroadcastReceiver, ContentProvider). Runs in your app's process.

**Example — MusicPlaybackService:**

```java
// This runs IN YOUR APP PROCESS
// It performs real work but only for YOUR app

public class MusicPlaybackService extends Service {
    private MediaPlayer mPlayer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mPlayer = new MediaPlayer();
        mPlayer.setDataSource("song.mp3");
        mPlayer.prepare();
        mPlayer.start();  // Real audio playback in YOUR app!

        return START_STICKY;
    }
}
```

**Key traits:**

- Lives in **your app process**
- Does **real work** but only for **your app**
- Can be **started** or **bound**
- **Analogy:** Your own employee — works only for you

#### 4. Native Service (Low-Level Daemon)

**What it is:** A standalone C++ process for performance-critical operations. Runs outside `system_server`.

**Example — SurfaceFlinger:**

```cpp
// This runs IN A SEPARATE PROCESS called "surfaceflinger"
// It is NOT in system_server!

class SurfaceFlinger : public BnSurfaceComposer {
    void doComposition() {
        // Must run at 60fps (16ms per frame)
        // Java GC pauses would cause visible stuttering!

        // 1. Collect all visible layers from all apps
        for (const auto& layer : mLayers) {
            layer->latchBuffer();
        }

        // 2. Composite into final display buffer using GPU
        mRenderEngine->drawLayers(mLayers);

        // 3. Send to display via HAL
        mDisplayHardware->present(buffer);
    }
};
```

**Key traits:**

- Lives in **separate native process**
- Has **direct HAL access** without JNI overhead
- **Crash isolation** — doesn't kill `system_server`
- **Real-time** — can't wait for Java GC
- **Analogy:** Specialist technician — handles complex, time-sensitive tasks

### Manager vs Service: Code Comparison

```java
// MANAGER (in your app process)
public class LocationManager {
    private ILocationManager mService;  // Binder proxy to system service

    public void requestLocationUpdates(String provider, LocationListener listener) {
        // Just forwards to the real service — NO GPS work here!
        mService.requestLocationUpdates(provider, listener);
    }
}

// SYSTEM SERVICE (in system_server process)
public class LocationManagerService extends ILocationManager.Stub {

    public void requestLocationUpdates(String provider, ILocationListener listener) {
        // 1. Validate permission
        enforcePermission(ACCESS_FINE_LOCATION);

        // 2. Check if GPS is enabled
        if (!isGPSEnabled()) throw new SecurityException();

        // 3. Register listener
        mListeners.add(listener);

        // 4. Start GPS hardware via JNI
        nativeStartGPS();
    }
}
```

### Complete Flow: Manager → Service → Hardware

```
Your App calls: lm.requestLocationUpdates()
    │
    ▼
┌─────────────────────────────────────┐
│ LocationManager (MANAGER)           │
│ • In YOUR app process               │
│ • Just forwards request             │
│ • No GPS logic here                 │
└─────────────────────────────────────┘
    │
    ▼ Binder IPC
┌─────────────────────────────────────┐
│ LocationManagerService (SERVICE)    │
│ • In system_server process          │
│ • Validates permission              │
│ • Checks GPS state                  │
│ • Manages listeners                 │
│ • Calls native HAL                  │
│ • DOES REAL WORK                    │
└─────────────────────────────────────┘
    │
    ▼ JNI
┌─────────────────────────────────────┐
│ GPS HAL Service (NATIVE SERVICE)    │
│ • In separate native process        │
│ • Direct hardware control           │
│ • Configures GPS chipset            │
└─────────────────────────────────────┘
    │
    ▼
GPS Hardware → Satellites → Location fix
    │
    ▼ Callback
Back to LocationManagerService
    │
    ▼ Binder IPC
Back to LocationManager (your app)
    │
    ▼
LocationListener.onLocationChanged()
```

---

## 6. System Services vs Native Services

Both types of services register with ServiceManager, but they differ in where they run and what they do.

### System Services (Java, in system_server)

| Characteristic   | Description                                                  |
| ---------------- | ------------------------------------------------------------ |
| **Location**     | `system_server` process (shared with ~100 services)          |
| **Language**     | Java with JNI calls to native code                           |
| **Role**         | Permission validation, state management, policy enforcement  |
| **Started by**   | `SystemServer.java` during boot                              |
| **Crash impact** | Can crash entire system (watchdog restarts)                  |
| **Examples**     | `ActivityManagerService`, `PackageManagerService`, `LocationManagerService` |

### Native Services (C++, separate process)

| Characteristic   | Description                                                  |
| ---------------- | ------------------------------------------------------------ |
| **Location**     | Separate native process (e.g., `mediaserver`, `surfaceflinger`) |
| **Language**     | C/C++                                                        |
| **Role**         | Direct hardware access, real-time operations, performance-critical work |
| **Started by**   | `init.rc` during boot                                        |
| **Crash impact** | Isolated — only that service restarts                        |
| **Examples**     | `MediaPlayerService`, `AudioFlinger`, `SurfaceFlinger`, `CameraServer` |

### Why Two Types?

| System Service Path         | Native Service Path            |
| --------------------------- | ------------------------------ |
| Needs permission checks     | Needs real-time performance    |
| Needs state management      | Needs direct HAL access        |
| Can tolerate GC pauses      | Cannot tolerate GC pauses      |
| Example: Vibrator, Location | Example: Audio, Video, Display |

---

## 7. Direct ServiceManager Access vs Custom Manager Pattern

### Approach 1: Direct ServiceManager Access (Quick but Risky)

```java
// Direct access — possible but NOT recommended
IBinder binder = ServiceManager.getService("my_custom_service");
if (binder != null) {
    IMyCustomService service = IMyCustomService.Stub.asInterface(binder);
    service.doSomething();
}
```

**Characteristics:**

- Uses **magic string** `"my_custom_service"` (fragile)
- No type safety
- No caching
- No error handling
- Breaks if service name changes
- **Blocked for third-party apps** since Android 9

**When acceptable:**

- System apps with platform signature
- Internal OEM apps
- Native daemons (C++)
- Debugging/prototyping

### Approach 2: Custom Manager + System Service (Proper Pattern)

```java
// Step 1: Define AIDL interface
// IMyFeatureService.aidl
interface IMyFeatureService {
    void enableFeature(boolean enabled);
    int getFeatureStatus();
}

// Step 2: Create System Service (in system_server)
public class MyFeatureService extends SystemService {
    private final IMyFeatureService.Stub mBinder = new IMyFeatureService.Stub() {
        @Override
        public void enableFeature(boolean enabled) {
            nativeEnableFeature(enabled);  // JNI → HAL
        }

        @Override
        public int getFeatureStatus() {
            return nativeGetStatus();
        }
    };

    @Override
    public void onStart() {
        publishBinderService(Context.MY_FEATURE_SERVICE, mBinder);
    }
}

// Step 3: Create Manager (client API in app)
public class MyFeatureManager {
    private final IMyFeatureService mService;

    public MyFeatureManager(Context context) {
        // Uses ServiceManager internally, but hidden from app
        IBinder binder = ServiceManager.getService(Context.MY_FEATURE_SERVICE);
        mService = IMyFeatureService.Stub.asInterface(binder);
    }

    public void enableFeature(boolean enabled) {
        try {
            mService.enableFeature(enabled);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isEnabled() {
        try {
            return mService.getFeatureStatus() == 1;
        } catch (RemoteException e) {
            return false;
        }
    }
}

// Step 4: Register in SystemServiceRegistry
registerService(Context.MY_FEATURE_SERVICE, MyFeatureManager.class,
    new CachedServiceFetcher<MyFeatureManager>() {
        @Override
        public MyFeatureManager createService(ContextImpl ctx) {
            return new MyFeatureManager(ctx);
        }
    });

// Step 5: App usage — clean and supported!
MyFeatureManager manager = (MyFeatureManager) getSystemService(Context.MY_FEATURE_SERVICE);
manager.enableFeature(true);
```

**Characteristics:**

- Uses **typed constant** `Context.MY_FEATURE_SERVICE` (stable)
- Full type safety
- Caching via `CachedServiceFetcher`
- Proper error handling
- Documented public API
- **Supported for third-party apps**

---

## 8. When to Use Which Approach

### Decision Matrix

| Scenario                              | Direct ServiceManager? | Custom Manager? | Notes                                      |
| ------------------------------------- | ---------------------- | --------------- | ------------------------------------------ |
| Internal OEM feature (no public API)  | ✅ Yes                  | Optional        | Quick, but fragile                         |
| AOSP system app                       | ✅ Yes                  | Optional        | `getSystemService()` is cleaner            |
| Custom ROM development                | ✅ Yes                  | Optional        | Direct access acceptable                   |
| Public SDK for third-party apps       | ❌ No                   | ✅ Must          | Hidden API policy blocks direct access     |
| Hardware abstraction feature          | ❌ No                   | ✅ Must          | Needs proper HAL integration               |
| Long-term maintainable code           | ❌ No                   | ✅ Must          | Manager pattern is proven                  |
| Google Play Store app                 | ❌ Blocked              | ✅ Must          | `ServiceManager` is hidden API             |
| Native daemon finding another service | ✅ Yes                  | N/A             | C++ services use `defaultServiceManager()` |
| Debugging/prototyping                 | ✅ Yes                  | N/A             | Quick verification                         |

### The Golden Rule

> **If third-party apps need to use it → Create a Manager.**
> **If only system apps use it → Direct ServiceManager is acceptable, but Manager is still better.**

---

## 9. Hidden API Policy (Android 9+)

Since Android 9 (Pie), Google **blocks** direct `ServiceManager` access for third-party apps to prevent reliance on unstable internal APIs.

### What Gets Blocked

```java
// This will throw SecurityException or return null on Android 9+
IBinder binder = ServiceManager.getService("hidden_service");
// ^^^ BLOCKED for third-party apps!
```

### Why Google Did This

| Reason            | Explanation                                                  |
| ----------------- | ------------------------------------------------------------ |
| **Stability**     | Prevents apps from breaking when internal service names change |
| **Security**      | Prevents apps from accessing privileged services             |
| **Compatibility** | Allows Google to refactor internals without breaking apps    |
| **Quality**       | Forces developers to use official, tested, documented APIs   |

### Workarounds (System Apps Only)

| Method             | How                                                          | Limitation                    |
| ------------------ | ------------------------------------------------------------ | ----------------------------- |
| Platform signature | Build as `priv-app` with `android:sharedUserId="android.uid.system"` | Only for OEMs                 |
| `@SystemApi`       | Use system-level APIs                                        | Requires system SDK           |
| Reflection         | Access hidden fields/methods                                 | Fragile, may break on updates |
| Custom ROM         | Modify framework to expose API                               | Only for ROM developers       |

---

## 10. Complete Implementation: Custom Manager + System Service

### Full Example: Custom Temperature Sensor Feature

#### Step 1: AIDL Interface

```aidl
// ITemperatureService.aidl
package com.example.android;

interface ITemperatureService {
    float getCurrentTemperature();
    void setTemperatureUnit(int unit);  // 0=Celsius, 1=Fahrenheit
    void registerCallback(ITemperatureCallback callback);
    void unregisterCallback(ITemperatureCallback callback);
}

// ITemperatureCallback.aidl
interface ITemperatureCallback {
    void onTemperatureChanged(float temperature);
}
```

#### Step 2: System Service (Java, in system_server)

```java
// frameworks/base/services/core/java/...
package com.android.server.temperature;

public class TemperatureService extends SystemService {
    private static final String TAG = "TemperatureService";
    private final ITemperatureService.Stub mBinder;
    private final RemoteCallbackList<ITemperatureCallback> mCallbacks;

    public TemperatureService(Context context) {
        super(context);
        mCallbacks = new RemoteCallbackList<>();
        mBinder = new ITemperatureService.Stub() {
            @Override
            public float getCurrentTemperature() {
                // Permission check
                mContext.enforceCallingPermission(
                    android.Manifest.permission.ACCESS_TEMPERATURE, 
                    "getCurrentTemperature"
                );
                // Read from native HAL via JNI
                return nativeReadTemperature();
            }

            @Override
            public void setTemperatureUnit(int unit) {
                nativeSetUnit(unit);
            }

            @Override
            public void registerCallback(ITemperatureCallback callback) {
                mCallbacks.register(callback);
            }

            @Override
            public void unregisterCallback(ITemperatureCallback callback) {
                mCallbacks.unregister(callback);
            }
        };
    }

    @Override
    public void onStart() {
        // Register with ServiceManager
        publishBinderService(Context.TEMPERATURE_SERVICE, mBinder);
    }

    // JNI methods
    private native float nativeReadTemperature();
    private native void nativeSetUnit(int unit);
}
```

#### Step 3: Manager (Java, client API)

```java
// frameworks/base/core/java/android/hardware/temperature/
package android.hardware.temperature;

public class TemperatureManager {
    private final ITemperatureService mService;

    /** @hide */
    public TemperatureManager(Context context) {
        // ServiceManager is used internally, hidden from app
        IBinder binder = ServiceManager.getService(Context.TEMPERATURE_SERVICE);
        mService = ITemperatureService.Stub.asInterface(binder);
    }

    public float getCurrentTemperature() {
        try {
            return mService.getCurrentTemperature();
        } catch (RemoteException e) {
            throw new RuntimeException("Temperature service died", e);
        }
    }

    public void setCelsius() {
        try {
            mService.setTemperatureUnit(0);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set unit", e);
        }
    }

    public void setFahrenheit() {
        try {
            mService.setTemperatureUnit(1);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set unit", e);
        }
    }

    public void registerListener(TemperatureListener listener) {
        // Wrap Java listener in AIDL callback
        ITemperatureCallback callback = new ITemperatureCallback.Stub() {
            @Override
            public void onTemperatureChanged(float temperature) {
                listener.onTemperatureChanged(temperature);
            }
        };
        try {
            mService.registerCallback(callback);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to register listener", e);
        }
    }

    public interface TemperatureListener {
        void onTemperatureChanged(float temperature);
    }
}
```

#### Step 4: Register in Context and SystemServiceRegistry

```java
// frameworks/base/core/java/android/content/Context.java
public abstract class Context {
    // Add service name constant
    public static final String TEMPERATURE_SERVICE = "temperature";
    ...
}

// frameworks/base/core/java/android/app/SystemServiceRegistry.java
static {
    // Register the service
    registerService(Context.TEMPERATURE_SERVICE, TemperatureManager.class,
        new CachedServiceFetcher<TemperatureManager>() {
            @Override
            public TemperatureManager createService(ContextImpl ctx) {
                return new TemperatureManager(ctx);
            }
        });
}
```

#### Step 5: App Usage

```java
// Third-party app usage — clean and supported!
public class MainActivity extends AppCompatActivity {
    private TemperatureManager mTempManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get manager via standard API
        mTempManager = (TemperatureManager) getSystemService(Context.TEMPERATURE_SERVICE);

        // Read temperature
        float temp = mTempManager.getCurrentTemperature();
        Log.d("Temp", "Current: " + temp + "°C");

        // Listen for changes
        mTempManager.registerListener(new TemperatureManager.TemperatureListener() {
            @Override
            public void onTemperatureChanged(float temperature) {
                runOnUiThread(() -> {
                    TextView tv = findViewById(R.id.temp_display);
                    tv.setText(temperature + "°C");
                });
            }
        });
    }
}
```

---

## 11. Architecture Comparison

### Direct ServiceManager (The "Hacky" Way)

```
Your App
    │
    ▼
ServiceManager.getService("my_custom_service")  ← Magic string! Fragile!
    │
    ▼
Native Service (works, but breaks easily)
```

**Problems:**

- Magic string can change between Android versions
- No type safety — runtime crashes if interface mismatches
- No caching — every call does IPC lookup
- No error handling — raw `RemoteException` everywhere
- Blocked by Google for third-party apps

### Proper Manager Pattern (The "Android" Way)

```
Your App
    │
    ▼
MyFeatureManager (typed, documented, stable API)
    │
    ▼
ServiceManager.getService(Context.MY_FEATURE_SERVICE)  ← Constant! Stable!
    │
    ▼
MyFeatureService (System Service in system_server)
    │
    ▼
JNI → Native Service → HAL → Kernel → Hardware
```

**Benefits:**

- Typed constants — never changes unexpectedly
- Full type safety — compile-time checking
- Caching — `CachedServiceFetcher` reuses instances
- Error handling — wrapped in clean exceptions
- Supported — works for all apps including Play Store

---

## 12. Key Takeaways

### The Core Concepts

| Concept                | One-Sentence Summary                                         |
| ---------------------- | ------------------------------------------------------------ |
| **Framework Manager**  | Client-side API wrapper in your app that talks to a System Service |
| **Service Manager**    | Central registry that maps service names to Binder handles — the "phone book" of Android |
| **System Service**     | Real implementation in `system_server` that does permission checks and state management |
| **Native Service**     | Separate C++ process for performance-critical hardware access |
| **Manager vs Service** | Manager = receptionist (forwards), Service = doctor (does real work) |

### The Relationships

1. **All Framework Managers use ServiceManager internally** — there is no other way to find System Services
2. **ServiceManager is the single source of truth** for service discovery in Android
3. **ServiceManager can reach Native Services directly** — no Manager required for native-to-native communication
4. **Direct ServiceManager access is possible but discouraged** — use the Manager pattern for public APIs
5. **Manager lives in your app, Service lives in system_server** — they are completely different processes

### The Four Types Summarized

| Type               | Lives In             | Does Real Work?          | Analogy                  |
| ------------------ | -------------------- | ------------------------ | ------------------------ |
| **Manager**        | Your app process     | ❌ No — just forwards     | Receptionist             |
| **App Service**    | Your app process     | ✅ Yes, for your app only | Your own employee        |
| **System Service** | `system_server`      | ✅ Yes, system-wide       | Department head / Doctor |
| **Native Service** | Separate C++ process | ✅ Yes, hardware-critical | Specialist technician    |

### The Decision Rules

| If you are building...                | Use this approach                                    |
| ------------------------------------- | ---------------------------------------------------- |
| Public API for third-party apps       | **Custom Manager + System Service**                  |
| Internal OEM feature                  | **Custom Manager** (or direct for quick hacks)       |
| Native daemon finding another service | **Direct ServiceManager** (C++)                      |
| System app with platform signature    | **Direct ServiceManager** acceptable                 |
| Play Store app                        | **Must use public Managers** — direct access blocked |
| Long-term maintainable code           | **Always use Manager pattern**                       |

### The Golden Rule

> **ServiceManager is the permanent, central registry of Android.** It can reach any service — Java or native, system or vendor. But for public APIs, always wrap it in a typed Manager to provide stability, safety, and a clean developer experience.

> **Manager vs Service:** The Manager is the receptionist in your app (forwards requests). The Service is the doctor in system_server (does real work). They are completely different things in completely different processes.
