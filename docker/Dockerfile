FROM registry.spotify.net/spotify/trusty-java:0.22
MAINTAINER dataex <dataex-squad@spotify.net>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Install hype-run command
RUN /bin/sh -c "$(curl -fsSL https://goo.gl/kSogpF)"

ENTRYPOINT ["hype-run"]
