pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ClusterNav"
include(":app")
include(":core")
include(":car-integration")
include(":vehicle-contracts")
include(":offcar-planner")

// AGP disables host unit tests for release-derived build types by default. T8 requires a real
// vehicleTest host-test variant (not a debug-task alias). The Android plugin API is unavailable on
// the settings script compile classpath, so this hook calls the public selector/builder API by
// reflection as soon as the app plugin is applied and before variants are created.
gradle.beforeProject {
    if (path == ":app") {
        pluginManager.withPlugin("com.android.application") {
            val components = extensions.getByName("androidComponents")
            val selector = components.javaClass.getMethod("selector").invoke(components)
            val vehicleTest = selector.javaClass
                .getMethod("withBuildType", String::class.java)
                .invoke(selector, "vehicleTest")
            val action = object : org.gradle.api.Action<Any> {
                override fun execute(builder: Any) {
                    builder.javaClass
                        .getMethod("setEnableUnitTest", Boolean::class.javaPrimitiveType)
                        .invoke(builder, true)
                }
            }
            val beforeVariants = components.javaClass.methods.single { method ->
                method.name == "beforeVariants" &&
                    method.parameterCount == 2 &&
                    method.parameterTypes[1] == org.gradle.api.Action::class.java
            }
            beforeVariants.invoke(components, vehicleTest, action)
        }
    }
}
