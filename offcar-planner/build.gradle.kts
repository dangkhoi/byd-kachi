plugins {
    id("java-library")
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass.set("com.byd.clusternav.offcar.OffCarPlannerMain")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":vehicle-contracts"))
    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("clusternav.root", rootProject.projectDir.absolutePath)
}


tasks.register<JavaExec>("renderExpansionPack") {
    group = "verification"
    description = "Render the fixed canonical expansion pack with no arguments"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.byd.clusternav.offcar.ExpansionMain")
    workingDir = rootProject.projectDir
}