# kotlin-springboot-sample

## Environment
- Java 21.0.2
- springboot 3.5.14
- Kotlin 1.9.25
- Gradle 8.14.4
- jpa

## Docker build
1. build
    ```shell
    docker build --no-cache -t {app-name} .
    ```
2. run
   ```shell
   docker run -p 8080:8080 {app-name}
    ```
