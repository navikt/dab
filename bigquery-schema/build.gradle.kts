plugins {
    id("dab.kotlin-libary")
    `java-test-fixtures`
}

dependencies {
    // google-cloud-bigquery eksponeres som del av det offentlige API-et slik at
    // konsumenter ikke trenger å legge den til separat.
    api(platform(libs.google.cloud.bom))
    api(libs.google.cloud.bigquery)

    implementation(libs.slf4j)

    // testFixtures: basisklasse for Datastream-kontrakttester.
    // Konsumenter legger til testImplementation(testFixtures("...")) for å bruke denne.
    testFixturesApi(libs.junit.engine)
    testFixturesApi(libs.assertj.core)
    testFixturesImplementation(platform(libs.google.cloud.bom))
    testFixturesImplementation(libs.google.cloud.bigquery)
    testImplementation(libs.junit.kotlin)
    testImplementation(libs.junit.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.assertj.core)
}
