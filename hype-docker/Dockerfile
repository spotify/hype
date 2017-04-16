FROM openjdk:8-jre
MAINTAINER Rouzbeh Delavari <rouz@spotify.com>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json
ENV HYPE_ENV testing

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]
