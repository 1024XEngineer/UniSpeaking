FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

ARG MAVEN_REPO_URL=https://maven.aliyun.com/repository/public
ENV MAVEN_REPO_URL=${MAVEN_REPO_URL}
ENV MVNW_REPOURL=${MAVEN_REPO_URL}

COPY . .
RUN ./mvnw --settings docker/maven/settings.xml \
    --batch-mode \
    --no-transfer-progress \
    -DskipUnitTests \
    -DskipIntegrationTests \
    package

FROM eclipse-temurin:21-jre AS java-runtime

FROM python:3.11-slim-bookworm AS ocr-base
WORKDIR /app

ARG PYPI_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/
ARG PADDLE_PDX_MODEL_SOURCE=bos

RUN sed -i \
        -e 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' \
        -e 's|http://deb.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=3 update \
    && apt-get install -y --no-install-recommends \
        libgomp1 \
        libgl1 \
        libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

RUN python --version | grep -E '^Python 3\.11\.'

RUN python -m venv /opt/ocr-venv
ENV PATH="/opt/ocr-venv/bin:${PATH}"
ENV PADDLE_PDX_CACHE_HOME=/app/ocr/models
ENV PADDLE_PDX_MODEL_SOURCE=${PADDLE_PDX_MODEL_SOURCE}
ENV PIP_INDEX_URL=${PYPI_INDEX_URL}
ENV PIP_DEFAULT_TIMEOUT=120

COPY docker/ocr/requirements.txt /tmp/ocr-requirements.txt
RUN python -m pip install \
    --no-cache-dir \
    --retries 5 \
    -r /tmp/ocr-requirements.txt

RUN mkdir -p /app/ocr/models
COPY src/main/resources/ocr/paddle_ocr_runner.py /app/ocr/paddle_ocr_runner.py
RUN python /app/ocr/paddle_ocr_runner.py \
    --download-models \
    --device cpu \
    --text-detection-model-name PP-OCRv5_mobile_det \
    --text-recognition-model-name PP-OCRv5_mobile_rec \
    --disable-doc-orientation \
    --disable-doc-unwarping \
    --disable-textline-orientation

FROM python:3.11-slim-bookworm
WORKDIR /app

RUN sed -i \
        -e 's|http://deb.debian.org/debian|https://mirrors.aliyun.com/debian|g' \
        -e 's|http://deb.debian.org/debian-security|https://mirrors.aliyun.com/debian-security|g' \
        /etc/apt/sources.list.d/debian.sources \
    && apt-get -o Acquire::Retries=3 update \
    && apt-get install -y --no-install-recommends \
        libgomp1 \
        libgl1 \
        libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk
COPY --from=ocr-base /opt/ocr-venv /opt/ocr-venv
COPY --from=ocr-base /app/ocr /app/ocr

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:/opt/ocr-venv/bin:${PATH}"
ENV PADDLE_PDX_CACHE_HOME=/app/ocr/models
ENV PADDLE_PDX_MODEL_SOURCE=bos

COPY --from=build /workspace/target/unispeaking_server-0.0.1-SNAPSHOT.jar /app/unispeaking-server.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/unispeaking-server.jar"]
