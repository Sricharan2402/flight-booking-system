plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "9.8.1"
    id("nu.studer.jooq") version "9.0"
    id("org.openapi.generator") version "7.0.1"
}

group = "com.flightbooking"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.3")

    // Jooq
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq:3.19.23")
    implementation("org.jooq:jooq-kotlin:3.19.23")
    jooqGenerator("org.postgresql:postgresql:42.7.3")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("redis.clients:jedis:5.2.0") // explicitly add Jedis
    implementation("org.apache.commons:commons-pool2:2.11.1")


    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.8")

    // Kafka testing
    testImplementation("org.springframework.kafka:spring-kafka-test")

    // Async testing utilities
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // Additional Spring test utilities
    testImplementation("org.springframework.boot:spring-boot-testcontainers")

    // JSON testing
    testImplementation("com.jayway.jsonpath:json-path")
}


configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "redis.clients" && requested.name == "jedis") {
            useVersion("5.2.0")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "21"
    }
}

// Flyway Configuration
flyway {
    driver = "org.postgresql.Driver"
    url = "jdbc:postgresql://localhost:5433/flight_booking"
    user = "flight_user"
    password = "flight_password"
    schemas = arrayOf("public")
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    cleanOnValidationError = true
    // allow clean for local dev
    cleanDisabled = false
}

// Jooq Configuration
jooq {
    version.set("3.19.23")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5433/flight_booking"
                    user = "flight_user"
                    password = "flight_password"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                    }
                    target.apply {
                        packageName = "com.flightbooking.generated.jooq"
                        directory = "${layout.buildDirectory.get()}/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}

// Make sure generated sources are included in compilation
sourceSets {
    main {
        java {
            srcDirs("build/generated-src/jooq/main")
        }
    }
}

// Ensure Jooq generation runs before Kotlin compilation
tasks.named("compileKotlin") {
    dependsOn(tasks.named("generateJooq"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// OpenAPI Generator Configuration
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateServerApi") {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/src/main/resources/openapi/server-api.yaml")
    outputDir.set("${layout.buildDirectory.get()}/generated/server")
    modelPackage.set("${project.group}.generated.server.model")
    apiPackage.set("${project.group}.generated.server.api")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "exceptionHandler" to "false"
        )
    )

    typeMappings.set(
        mapOf(
            "DateTime" to "java.time.ZonedDateTime"
        )
    )
}

// Add generated sources to the main source set
sourceSets.main {
    java.srcDirs(
        "${layout.buildDirectory.get()}/generated/server/src/main/kotlin"
    )
}

// Ensure the generateAllApis task runs before compilation
tasks.compileKotlin {
    dependsOn("generateServerApi")
}