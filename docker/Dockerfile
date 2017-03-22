FROM openjdk:8-jre
MAINTAINER Rouzbeh Delavari <rouz@spotify.com>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]
