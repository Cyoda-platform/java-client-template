ARG GRAALVM_VERSION=21.0.2-ol8-20240116
#our scripts should not be modified at this stage, saas will have its own charts in the future
#FROM container-registry.oracle.com/graalvm/jdk:${JAVA_VERSION}@sha256:5d3e7ad9dc8a645dac080603597935b5d7afdc18e254f9471c4d3de2efc81e65
FROM ghcr.io/graalvm/graalvm-community:${GRAALVM_VERSION}@sha256:202c80bd7a34c0e2ed742ee34bc404d1bf9880b5fad3a5105e18c65a483f660b
#RUN gu --verbose --jvm install js
RUN mkdir -p /app
ARG DOCKER_USER=cyoda
ARG USER_UID=9000
RUN useradd -m -s /bin/bash -u ${USER_UID} ${DOCKER_USER}
RUN chown ${DOCKER_USER} /app
USER ${DOCKER_USER}
COPY target/app-*.jar /app/client.jar
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /app/client.jar" ]