FROM registry.spotify.net/spotify/trusty-java:0.22
MAINTAINER dataex <dataex-squad@spotify.net>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json

# Download and install runner capsule
RUN ["bash", "-c", "bash <(curl -fsSL https://raw.githubusercontent.com/spotify/hype/master/bin/install-runner.sh?token=AAoAXDBMDot0-Ccbn_aTHzRT2CnDEkFIks5Y2ylJwA%3D%3D)"]

ENTRYPOINT hype-run
