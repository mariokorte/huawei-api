FROM eclipse-temurin:17

ARG TARGETPLATFORM

RUN mkdir /huawei-api-service; \
    mkdir /huawei-api-service/dependency

COPY ./aggregate-huawei-api/target/huawei-api-service-all-jar.jar /huawei-api-service/huawei-api-service-all-jar.jar
COPY ./aggregate-huawei-api/target/dependency/ /huawei-api-service/dependency/

WORKDIR /huawei-api-service
CMD "java" "-jar" "huawei-api-service-all-jar.jar"