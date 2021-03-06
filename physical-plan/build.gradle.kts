plugins {
    kotlin("plugin.serialization") version "1.3.61"
}


dependencies {

    implementation(project(":datasource"))
    implementation(project(":logical-plan"))

    implementation("org.apache.arrow:arrow-memory:0.16.0")
    implementation("org.apache.arrow:arrow-vector:0.16.0")
}
