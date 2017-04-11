FROM openjdk:8-jre
MAINTAINER Rafal Wojdyla <rav@spotify.com>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

COPY kisssplit.py /

# install python
RUN apt-get update && apt-get install -y python

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"
ENTRYPOINT ["hype-run"]
