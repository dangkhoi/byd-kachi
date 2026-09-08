// ClusterNav root build — v1.03 remediation (T1 toolchain migration)
// AGP 9.3.1 built-in Kotlin for :app; Kotlin JVM 2.4.10 for :core/:car-integration.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.4.10" apply false
}
