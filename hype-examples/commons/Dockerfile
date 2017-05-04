FROM openjdk:8-jre
MAINTAINER Romain Yon <romain@spotify.com>

# Install gcloud
RUN apt-get update && apt-get install apt-transport-https -y
RUN echo "deb https://packages.cloud.google.com/apt cloud-sdk-jessie main" | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list
RUN curl https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -
RUN apt-get update && apt-get install google-cloud-sdk -y

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Install crcmod for gsutil
RUN apt-get update && apt-get install gcc python-dev python-setuptools -y
RUN apt-get purge python-crcmod -y
RUN easy_install -U pip
RUN pip install -U crcmod

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"

ENTRYPOINT ["hype-run"]
