FROM registry.spotify.net/spotify/trusty-java:0.22
MAINTAINER dataex <dataex-squad@spotify.net>

ENV GOOGLE_APPLICATION_CREDENTIALS /etc/gcloud/key.json
COPY hype-run/target/*-capsule.jar /usr/share/hype-run/capsule.jar

RUN /usr/bin/java -Dcapsule.modes -jar /usr/share/hype-run/capsule.jar

ENTRYPOINT ["/bin/bash", "-c", "exec /usr/bin/java $JVM_ARGS -jar /usr/share/hype-run/capsule.jar \"$@\"", "bash"]
