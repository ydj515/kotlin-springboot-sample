# kotlin-springboot-sample

## Environment
- Java 17
- springboot 3.3.5
- jpa
- gradle

## Docker build
1. build
    ```shell
    docker build --no-cache -t {app-name} .
    ```
2. run
   ```shell
   docker run -p 8080:8080 {app-name}
    ```