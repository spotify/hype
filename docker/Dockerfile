FROM registry.spotify.net/spotify/trusty-java:0.22
MAINTAINER dataex <dataex-squad@spotify.net>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Download and install runner capsule
RUN ["bash", "-c", "bash <(curl -fsSL https://raw.githubusercontent.com/spotify/hype/master/bin/install-runner?token=AAoAXJUmfmJ9dttUgI_9E8vbJOOspn7Yks5Y2zW0wA%3D%3D)"]

ENTRYPOINT hype-run
